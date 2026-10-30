#include "chemistry/chemistry_service.h"
#include <algorithm>
#include <cmath>
#include <unordered_set>

namespace astratto {
namespace {
constexpr uint32_t FLAMMABLE = 1, CONDUCTIVE = 2, FREEZABLE = 4, WIND_SENSITIVE = 8;
constexpr uint32_t WET = 1, BURNING = 2, FROZEN = 4, CHARGED = 8, BURNT = 16;
constexpr uint32_t FIRE = 1, WATER = 2, ICE = 3, WIND = 4, ELECTRIC = 5;
constexpr size_t MAX_SENSORS = 4096, MAX_VOLUMES = 256, BUDGET = 96;
float square(float x) { return x * x; }
}
int64_t ChemistryService::bucket(float x, float y, float z) {
    const auto cell = [](float f) { return static_cast<uint64_t>(static_cast<int64_t>(std::floor(f / 4)) + 1048576) & 0x1fffffULL; };
    return static_cast<int64_t>((cell(x) << 42) | (cell(y) << 21) | cell(z));
}
void ChemistryService::index(Sensor& s, bool add) {
    const int64_t key = bucket(s.x, s.y, s.z);
    if (add) buckets_[key].push_back(s.id);
    else {
        auto it = buckets_.find(key); if (it == buckets_.end()) return;
        auto& v = it->second; v.erase(std::remove(v.begin(), v.end(), s.id), v.end());
        if (v.empty()) buckets_.erase(it);
    }
}
void ChemistryService::wake(Sensor& s) { if (!s.queued) { s.queued = true; active_.push_back(s.id); } }
void ChemistryService::emit(Volume v) {
    if (pending_.size() + volumes_.size() >= MAX_VOLUMES) return;
    if (v.serial == 0) v.serial = ++serial_;
    pending_.push_back(v);
}
void ChemistryService::worldChanged(Engine&) { sensors_.clear(); buckets_.clear(); active_.clear(); pending_.clear(); volumes_.clear(); accumulator_ = 0; }
int ChemistryService::command(Engine& e, uint32_t type, const uint8_t* data, uint32_t bytes) {
    switch (type) {
        case ASTRATTO_CMD_CHEM_MATERIAL: {
            astratto_cmd_chem_material c; if (!readRecord(data, bytes, c)) return ASTRATTO_ERR_STREAM;
            if (c.block > 255 || c.burnt > 255 || !std::isfinite(c.fuel) || c.fuel < 0) return ASTRATTO_ERR_ARGUMENT;
            materials_[c.block] = {c.tags & 15u, c.burnt, c.fuel}; return ASTRATTO_OK;
        }
        case ASTRATTO_CMD_CHEM_SENSOR: {
            astratto_cmd_chem_sensor c; if (!readRecord(data, bytes, c)) return ASTRATTO_ERR_STREAM;
            if (!c.id || !std::isfinite(c.x + c.y + c.z + c.radius + c.fuel + c.wet + c.heat) || std::abs(c.x)>1048576 || std::abs(c.y)>1048576 || std::abs(c.z)>1048576 || c.radius < 0 || c.radius > 6) return ASTRATTO_ERR_ARGUMENT;
            auto found = sensors_.find(c.id);
            if (found == sensors_.end() && sensors_.size() >= MAX_SENSORS) {
                auto discard=std::find_if(sensors_.begin(),sensors_.end(),[](const auto& entry){return entry.second.cell && (entry.second.state==0 || (entry.second.state&BURNT));});
                if(discard==sensors_.end())return ASTRATTO_OK;
                index(discard->second,false);sensors_.erase(discard);
            }
            if (found != sensors_.end()) index(found->second, false);
            Sensor& s = sensors_[c.id]; const bool fresh = s.id == 0;
            s.id = c.id; s.cell = (c.id >> 63) != 0; if (s.cell && e.active()) s.block = static_cast<uint32_t>(e.active()->get(static_cast<int>(c.x), static_cast<int>(c.y), static_cast<int>(c.z))); s.x = c.x; s.y = c.y; s.z = c.z; s.radius = c.radius; s.tags = c.tags & 15u;
            if (fresh || c.restore) { s.owner = c.owner; s.state = c.state & 31u; s.fuel = std::max(0.0f, c.fuel); s.wet = std::clamp(c.wet, 0.0f, 1.0f); s.heat = std::clamp(c.heat, -1.0f, 3.0f); s.last = time_; s.originalBlock=s.block; if (s.state & CHARGED) s.chargedUntil = time_ + 0.65f; }
            index(s, true); if (s.state != 0 || std::fabs(s.heat) > 0.01f) wake(s); return ASTRATTO_OK;
        }
        case ASTRATTO_CMD_CHEM_EMIT: {
            astratto_cmd_chem_emit c; if (!readRecord(data, bytes, c)) return ASTRATTO_ERR_STREAM;
            if (c.element < FIRE || c.element > ELECTRIC || !std::isfinite(c.x + c.y + c.z + c.radius + c.power + c.life) || std::abs(c.x)>1048576 || std::abs(c.y)>1048576 || std::abs(c.z)>1048576 || c.radius <= 0 || c.radius > 6 || c.power <= 0 || c.power > 10 || c.life <= 0 || c.life > 30) return ASTRATTO_ERR_ARGUMENT;
            Volume v; v.owner = c.owner; v.type = c.element; v.x = c.x; v.y = c.y; v.z = c.z; v.radius = c.radius; v.power = c.power; v.life = c.life; emit(v); return ASTRATTO_OK;
        }
        case ASTRATTO_CMD_CHEM_DISOWN: {
            astratto_cmd_handle c; if (!readRecord(data, bytes, c)) return ASTRATTO_ERR_STREAM;
            if (!c.handle) return ASTRATTO_ERR_ARGUMENT;
            for (auto& entry : sensors_) if (entry.second.owner == c.handle) entry.second.owner = 0;
            for (auto& volume : volumes_) if (volume.owner == c.handle) volume.owner = 0;
            for (auto& volume : pending_) if (volume.owner == c.handle) volume.owner = 0;
            return ASTRATTO_OK;
        }
        case ASTRATTO_CMD_CHEM_REMOVE: {
            astratto_cmd_handle c; if (!readRecord(data, bytes, c)) return ASTRATTO_ERR_STREAM;
            auto found = sensors_.find(c.handle); if (found != sensors_.end()) { index(found->second, false); sensors_.erase(found); }
            // A removed receiver cannot leave behind its own secondary flame volumes and reignite its replacement.
            volumes_.erase(std::remove_if(volumes_.begin(), volumes_.end(), [&](const Volume& v){return v.source == c.handle;}), volumes_.end());
            pending_.erase(std::remove_if(pending_.begin(), pending_.end(), [&](const Volume& v){return v.source == c.handle;}), pending_.end());
            return ASTRATTO_OK;
        }
        case ASTRATTO_CMD_CHEM_WIND: {
            astratto_cmd_chem_wind c; if (!readRecord(data, bytes, c)) return ASTRATTO_ERR_STREAM;
            if (!std::isfinite(c.x + c.z)) return ASTRATTO_ERR_ARGUMENT;
            windX_ = std::clamp(c.x, -20.0f, 20.0f); windZ_ = std::clamp(c.z, -20.0f, 20.0f); return ASTRATTO_OK;
        }
        case ASTRATTO_CMD_CHEM_WEATHER: {
            astratto_cmd_chem_weather c; if(!readRecord(data,bytes,c))return ASTRATTO_ERR_STREAM;
            if(!std::isfinite(c.rain+c.temperature)||c.rain<0||c.rain>1)return ASTRATTO_ERR_ARGUMENT;
            rain_=c.rain;return ASTRATTO_OK;
        }
        case ASTRATTO_CMD_CHEM_RESET: worldChanged(e); return ASTRATTO_OK;
        default: return -1;
    }
}
void ChemistryService::discover(Engine& e, const Volume& v) {
    VoxelWorld* w = e.active(); if (!w) return;
    const int reach = static_cast<int>(std::ceil(v.radius));
    for (int x = static_cast<int>(std::floor(v.x)) - reach; x <= static_cast<int>(std::floor(v.x)) + reach; ++x)
        for (int y = static_cast<int>(std::floor(v.y)) - reach; y <= static_cast<int>(std::floor(v.y)) + reach; ++y)
            for (int z = static_cast<int>(std::floor(v.z)) - reach; z <= static_cast<int>(std::floor(v.z)) + reach; ++z) {
                if (!w->inBounds(x, y, z) || sensors_.size() >= MAX_SENSORS) continue;
                const uint32_t block = static_cast<uint32_t>(w->get(x, y, z)); const Material& mat = materials_[block]; if (!mat.tags) continue;
                const uint64_t id = (1ULL << 63) | (static_cast<uint64_t>(x) << 40) | (static_cast<uint64_t>(y) << 20) | static_cast<uint64_t>(z);
                auto old = sensors_.find(id);
                if (old != sensors_.end()) { if (old->second.block == block) continue; index(old->second, false); sensors_.erase(old); }
                Sensor s; s.id = id; s.x = static_cast<float>(x) + 0.5f; s.y = static_cast<float>(y) + 0.5f; s.z = static_cast<float>(z) + 0.5f; s.radius = 0.6f; s.tags = mat.tags; s.fuel = mat.fuel; s.block = s.originalBlock = block; s.cell = true; s.last = time_;
                auto inserted = sensors_.emplace(id, s); index(inserted.first->second, true);
            }
}
void ChemistryService::publish(Engine& e, Sensor& s, uint32_t previous) {
    if (previous == s.state) return;
    astratto_evt_chem_state ev{}; ev.id = s.id; ev.owner = s.owner; ev.state = s.state; ev.previous = previous; ev.x = s.x; ev.y = s.y; ev.z = s.z; e.emit(ev, ASTRATTO_EVT_CHEM_STATE);
}
void ChemistryService::apply(Engine& e, Sensor& s, const Volume& v) {
    const float d2 = square(s.x - v.x) + square(s.y - v.y) + square(s.z - v.z); if (d2 > square(v.radius + s.radius) || (s.state & BURNT)) return;
    const uint32_t previous = s.state;
    if (v.type == WATER) { s.wet = std::min(1.0f, s.wet + v.power * 0.35f); s.heat = std::max(0.0f, s.heat - v.power); s.state = (s.state | WET) & ~BURNING; }
    if (v.type == FIRE) {
        if (s.state & FROZEN) { s.state &= ~FROZEN; Volume water = v; water.serial = 0; water.type = WATER; water.x = s.x; water.y = s.y; water.z = s.z; water.radius = s.radius; water.power = 0.5f; water.life = 0.25f; emit(water); }
        // (0.2.1, gamefixes: fire raced through a whole wooden building in seconds) the flame a burning cell gives off warms
        // its neighbours less the farther they are - a face neighbour most, a corner barely - so heat builds up over
        // several seconds before the next plank catches; a spell's or a torch's own fire (no source cell) is unchanged
        float reach = 1.0f;
        if (v.source != 0) reach = std::clamp(1.0f - std::sqrt(d2) / (v.radius + s.radius), 0.15f, 1.0f) * 1.6f;
        s.heat = std::min(3.0f, s.heat + v.power * reach * (1 - s.wet) * 0.3f);
        if ((s.tags & FLAMMABLE) && s.fuel > 0 && s.wet < 0.15f && s.heat > 0.35f) {
            s.state |= BURNING;
            // Passive wind/water and an older unowned flame must not erase a fresh attack's source.
            if (v.owner != 0 || (previous & BURNING) == 0) s.owner = v.owner;
        }
    }
    if (v.type == ICE && ((s.tags & FREEZABLE) || s.wet > 0.1f)) { s.heat = -1; s.state = (s.state | FROZEN) & ~BURNING; }
    if (v.type == ELECTRIC && ((s.tags & CONDUCTIVE) || s.wet > 0.15f) && s.shock != v.serial) {
        s.owner = v.owner; s.shock = v.serial; s.chargedUntil = time_ + 0.65f; s.state |= CHARGED;
        if (v.hops < 8) { Volume next = v; next.x = s.x; next.y = s.y; next.z = s.z; next.radius = s.radius + 0.7f; next.power *= 0.8f; next.life = 0.15f; next.hops++; emit(next); }
    }
    if (v.type == WIND && (s.tags & WIND_SENSITIVE)) s.heat = std::max(-1.0f, s.heat - v.power * 0.03f);
    if (s.state || std::fabs(s.heat) > 0.01f) wake(s);
    publish(e, s, previous);
}
void ChemistryService::step(Engine& e, float dt) {
    time_ += dt; accumulator_ += dt; if (accumulator_ < 0.1f) return;
    const float elapsed = accumulator_; accumulator_ = 0;
    // New volumes are queued: material -> element -> material, never recursive receiver-to-receiver calls.
    const size_t incoming = pending_.size();
    for (size_t i = 0; i < incoming; ++i) { volumes_.push_back(pending_.front()); pending_.pop_front(); discover(e, volumes_.back()); }
    // Element/element conversion is bounded by the small active-volume cap, not total world size.
    for (Volume& fire : volumes_) if (fire.type == FIRE) for (const Volume& water : volumes_) if (water.type == WATER && square(fire.x-water.x)+square(fire.y-water.y)+square(fire.z-water.z) < square(fire.radius+water.radius)) fire.life = 0;
    for (Volume& v : volumes_) {
        if (v.life <= 0) continue;
        v.life -= elapsed;
        std::unordered_set<uint64_t> touched;
        const float extent = v.radius + 1;
        for (float x = std::floor((v.x-extent)/4)*4; x <= v.x+extent; x += 4)
            for (float y = std::floor((v.y-extent)/4)*4; y <= v.y+extent; y += 4)
                for (float z = std::floor((v.z-extent)/4)*4; z <= v.z+extent; z += 4) {
                    auto b = buckets_.find(bucket(x,y,z)); if (b == buckets_.end()) continue;
                    for (uint64_t id : b->second) if (touched.insert(id).second) { auto s = sensors_.find(id); if (s != sensors_.end()) apply(e,s->second,v); }
                }
        if(v.type==FIRE)for(const Volume& wind:volumes_)if(wind.type==WIND && square(v.x-wind.x)+square(v.y-wind.y)+square(v.z-wind.z)<square(v.radius+wind.radius))v.y+=elapsed*wind.power*0.35f;
        if (v.type == FIRE || v.type == WIND) { v.x += windX_*elapsed*0.2f; v.z += windZ_*elapsed*0.2f; if(v.type==FIRE) v.y+=elapsed*0.25f; }
    }
    volumes_.erase(std::remove_if(volumes_.begin(),volumes_.end(),[](const Volume& v){return v.life<=0;}),volumes_.end());
    const size_t count = std::min(BUDGET, active_.size());
    for (size_t i=0;i<count;++i) {
        const uint64_t id=active_.front(); active_.pop_front(); auto found=sensors_.find(id); if(found==sensors_.end())continue;
        Sensor& s=found->second; s.queued=false; const uint32_t previous=s.state; const float h=std::min(0.5f,time_-s.last); s.last=time_;
        if(rain_>0.05f && e.active() && e.active()->sky(static_cast<int>(s.x),static_cast<int>(s.y+1),static_cast<int>(s.z))>10 && (s.state&BURNING)) {
            Volume water;water.type=WATER;water.x=s.x;water.y=s.y;water.z=s.z;water.radius=s.radius;water.power=rain_*3;water.life=0.12f;emit(water);
        }
        s.wet=std::max(0.0f,s.wet-h*0.025f); if(s.wet<0.02f)s.state&=~WET;
        if(time_>s.chargedUntil)s.state&=~CHARGED;
        if(s.state&BURNING) {
            s.fuel=std::max(0.0f,s.fuel-h); if(s.wet>0.15f)s.state&=~BURNING;
            if(s.fuel<=0) { s.state=(s.state&~BURNING)|BURNT; if(s.cell&&e.active())e.setCell(e.activeHandle(),static_cast<int>(s.x),static_cast<int>(s.y),static_cast<int>(s.z),materials_[s.block].burnt); s.block = materials_[s.block].burnt; }
            else if(time_>=s.emitAt) { s.emitAt=time_+0.45f; Volume fire; fire.source=s.id; fire.owner=s.owner; fire.type=FIRE; fire.x=s.x+windX_*0.08f; fire.y=s.y+0.25f; fire.z=s.z+windZ_*0.08f; fire.radius=s.radius+0.6f; fire.power=0.05f; fire.life=0.25f; emit(fire); Volume up=fire; up.type=WIND; up.power=0.4f; emit(up); }
        } else s.heat += (0-s.heat)*std::min(1.0f,h*0.15f);
        if((s.state&FROZEN)&&s.heat>-0.15f)s.state&=~FROZEN;
        publish(e,s,previous);
        if(!(s.state&BURNT)&&(s.state||std::fabs(s.heat)>0.02f))wake(s);
    }
}
bool ChemistryService::snapshot(Engine&, uint32_t kind, std::vector<uint8_t>& out) {
    if(kind!=ASTRATTO_SNAPSHOT_CHEMISTRY)return false;
    for(const auto& pair:sensors_) { const Sensor& s=pair.second; if(!s.state&&std::fabs(s.heat)<0.02f)continue;
        astratto_snap_chem_sensor r{}; r.id=s.id;r.owner=s.owner;r.x=s.x;r.y=s.y;r.z=s.z;r.radius=s.radius;r.tags=s.tags;r.state=s.state;r.fuel=s.fuel;r.wet=s.wet;r.heat=s.heat;r.block=s.block;r.original_block=s.originalBlock;
        appendRecord(out,r,ASTRATTO_SNAP_CHEM_SENSOR);
    }
    return true;
}
}
