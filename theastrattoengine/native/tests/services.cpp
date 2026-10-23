// Astratto Engine - checks of the 2.0 simulation services, through the public C interface only: handles and their
// lifetimes, physics (movers, Sydney's ragdolls, soft bodies, cars), AI (policies, perception, routes followed),
// audio (the mixer rendered without a device), world views and edits, and a C++ gameplay module loaded from a library.
// Like selftest.cpp it knows nothing of any game: it builds a small walled yard of its own.
#include <algorithm>
#include <cmath>
#include <cstdio>
#include <cstring>
#include <string>
#include <vector>

#include "astratto/astratto.h"
#include "astratto/astratto_module.h"

void check(const char* what, bool ok);   // selftest.cpp

namespace {

const int SX = 64, SY = 24, SZ = 64, FLOOR = 4;
enum { AIR = 0, STONE = 1, GLASS = 2 };

struct Cmds {
    std::vector<uint8_t> bytes;
    template <typename T> T& add(uint32_t type) {
        const size_t at = bytes.size();
        // Return naturally aligned C records; the wire format itself only promises four-byte alignment.
        const size_t size = (sizeof(T) + 7) & ~size_t(7);
        bytes.resize(at + size);
        T* rec = reinterpret_cast<T*>(bytes.data() + at);
        std::memset(rec, 0, sizeof(T));
        rec->head.type = type; rec->head.bytes = static_cast<uint32_t>(size);
        return *rec;
    }
    void bare(uint32_t type) { astratto_record r{type, sizeof(astratto_record)}; raw(&r, sizeof r); }
    void raw(const void* p, size_t n) { const size_t at = bytes.size(); bytes.resize(at + n); std::memcpy(bytes.data() + at, p, n); }
    void clear() { bytes.clear(); }
};

struct Events {
    std::vector<uint8_t> copy;
    uint32_t count = 0;
    int32_t status = 0;
    template <typename F> void each(uint32_t type, F f) const {
        uint32_t at = 0;
        while (at + sizeof(astratto_record) <= copy.size()) {
            astratto_record h;
            std::memcpy(&h, copy.data() + at, sizeof h);
            if (h.bytes < sizeof h || h.bytes > copy.size() - at) break;
            if (h.type == type) f(copy.data() + at, h.bytes);
            at += h.bytes;
        }
    }
    int counted(uint32_t type) const { int n = 0; each(type, [&](const uint8_t*, uint32_t) { n++; }); return n; }
};

Events frame(astratto_engine* e, Cmds& c, float dt) {
    astratto_frame_io io{};
    io.struct_size = sizeof io; io.dt = dt;
    io.commands = c.bytes.empty() ? nullptr : c.bytes.data(); io.command_bytes = static_cast<uint32_t>(c.bytes.size());
    Events ev;
    ev.status = astratto_frame(e, &io);
    ev.count = io.event_count;
    if (io.event_bytes) ev.copy.assign(io.events, io.events + io.event_bytes);
    c.clear();
    return ev;
}

template <typename T> const T* findSnap(const astratto_snapshot_io& io, uint32_t type, astratto_handle h) {
    const uint8_t* p = static_cast<const uint8_t*>(io.data);
    uint64_t at = 0;
    while (at + sizeof(astratto_record) <= io.bytes) {
        astratto_record r;
        std::memcpy(&r, p + at, sizeof r);
        if (r.bytes < sizeof r) break;
        if (r.type == type) { const T* t = reinterpret_cast<const T*>(p + at); if (t->handle == h) return t; }
        at += r.bytes;
    }
    return nullptr;
}

astratto_snapshot_io snap(astratto_engine* e, uint32_t kind) {
    astratto_snapshot_io io{};
    io.struct_size = sizeof io; io.kind = kind;
    if (astratto_snapshot(e, &io) != ASTRATTO_OK) { io.data = nullptr; io.bytes = 0; }
    return io;
}

astratto_handle reserve(astratto_engine* e, uint32_t kind) { astratto_handle h = 0; astratto_handles_reserve(e, kind, 1, &h); return h; }

}  // namespace

void services_checks(const char* moduleDir) {
    astratto_config cfg{};
    cfg.struct_size = sizeof cfg; cfg.api_version = ASTRATTO_API_VERSION; cfg.worker_threads = 2; cfg.flags = ASTRATTO_CONFIG_DETERMINISTIC;
    astratto_engine* e = astratto_create(&cfg);
    check("2.0: an engine for the services is made, without an audio device", e != nullptr);
    if (!e) return;

    // ------------------------------------------------------------ the world is the engine's
    astratto_block_def defs[3]{};
    defs[STONE].flags = ASTRATTO_BLOCK_SOLID | ASTRATTO_BLOCK_OPAQUE;
    defs[GLASS].flags = ASTRATTO_BLOCK_SOLID; defs[GLASS].material = ASTRATTO_MATERIAL_GLASS;
    astratto_block_table table{};
    table.struct_size = sizeof table; table.count = 3; table.defs = defs;
    astratto_world_block_table(e, &table);
    astratto_world_desc wd{};
    wd.struct_size = sizeof wd; wd.size_x = SX; wd.size_y = SY; wd.size_z = SZ; wd.below_floor_block = STONE; wd.flags = ASTRATTO_WORLD_LIGHT;
    astratto_handle world = 0;
    check("a world is made empty, by the engine, and lit", astratto_world_create(e, &wd, &world) == ASTRATTO_OK && ASTRATTO_HANDLE_KIND(world) == ASTRATTO_KIND_WORLD);
    {
        // the floor, then a wall across the yard at z = 40 with a gap at x 30..33
        Cmds b;
        astratto_edit_fill& f = b.add<astratto_edit_fill>(ASTRATTO_EDIT_FILL);
        f.x0 = 0; f.y0 = 0; f.z0 = 0; f.x1 = SX - 1; f.y1 = FLOOR - 1; f.z1 = SZ - 1; f.block = STONE | ASTRATTO_SET_RAW;
        astratto_edit_fill& w1 = b.add<astratto_edit_fill>(ASTRATTO_EDIT_FILL);
        w1.x0 = 0; w1.y0 = FLOOR; w1.z0 = 40; w1.x1 = 29; w1.y1 = FLOOR + 5; w1.z1 = 40; w1.block = STONE | ASTRATTO_SET_RAW;
        astratto_edit_fill& w2 = b.add<astratto_edit_fill>(ASTRATTO_EDIT_FILL);
        w2.x0 = 34; w2.y0 = FLOOR; w2.z0 = 40; w2.x1 = SX - 1; w2.y1 = FLOOR + 5; w2.z1 = 40; w2.block = STONE | ASTRATTO_SET_RAW;
        b.bare(ASTRATTO_EDIT_LIGHT_ALL);
        check("a batch of edits builds it", astratto_world_edit(e, world, b.bytes.data(), static_cast<uint32_t>(b.bytes.size())) == ASTRATTO_OK);
    }
    astratto_world_view v{};
    v.struct_size = sizeof v;
    const bool viewed = astratto_world_get_view(e, world, &v) == ASTRATTO_OK;
    const uint64_t edits0 = viewed ? v.edits : 0;
    check("its view is the engine's own memory: the edits are there, read straight back, and it is lit from the sky",
          viewed && v.blocks[(10 * SZ + 10) * SY + 0] == STONE && v.blocks[(10 * SZ + 40) * SY + FLOOR + 2] == STONE && v.blocks[(31 * SZ + 40) * SY + FLOOR] == AIR
          && (v.light[(10 * SZ + 10) * SY + FLOOR + 1] >> 4) == 15);
    astratto_world_set(e, world, 31, FLOOR, 40, STONE);
    check("a single set changes the same memory the view shows, and the edit count moves",
          v.blocks[(31 * SZ + 40) * SY + FLOOR] == STONE && astratto_world_get_view(e, world, &v) == ASTRATTO_OK && v.edits > edits0);
    astratto_world_set(e, world, 31, FLOOR, 40, AIR);

    Cmds c;
    c.add<astratto_cmd_handle>(ASTRATTO_CMD_WORLD_ACTIVATE).handle = world;

    // ------------------------------------------------------------ handles
    astratto_handle mover = reserve(e, ASTRATTO_KIND_MOVER);
    {
        astratto_handle bogus = mover ^ (static_cast<astratto_handle>(1) << 32);   // another generation of the same slot
        astratto_cmd_mover_input& in = c.add<astratto_cmd_mover_input>(ASTRATTO_CMD_MOVER_INPUT);
        in.handle = bogus;
        Events ev = frame(e, c, 0);
        int errs = 0;
        ev.each(ASTRATTO_EVT_ERROR, [&](const uint8_t* p, uint32_t) { astratto_evt_error er; std::memcpy(&er, p, sizeof er); if (er.status == ASTRATTO_ERR_HANDLE && er.handle == bogus) errs++; });
        check("a handle of the wrong generation is refused with an error event, and the frame goes on", ev.status == ASTRATTO_OK && errs == 1);
    }

    // ------------------------------------------------------------ physics: a mover walks, falls and is stopped by walls
    {
        astratto_cmd_mover_create& m = c.add<astratto_cmd_mover_create>(ASTRATTO_CMD_MOVER_CREATE);
        m.handle = mover; m.x = 10.5f; m.y = FLOOR + 3.0f; m.z = 10.5f; m.radius = 0.3f; m.height = 1.8f; m.step = 0.6f;
        frame(e, c, 0);
        for (int i = 0; i < 60; i++) frame(e, c, 1.0f / 60);
        astratto_snapshot_io io = snap(e, ASTRATTO_SNAPSHOT_BODIES);
        const astratto_snap_mover* s = findSnap<astratto_snap_mover>(io, ASTRATTO_SNAP_MOVER, mover);
        check("a mover made in the air falls onto the floor and stands on it", s && s->grounded && std::fabs(s->y - FLOOR) < 0.05f);
        // 2.3: standing, it is on the ground in every frame, whatever the frames' lengths (it used to read airborne on
        // every other physics step, which uneven frames turned into flicker)
        {
            int off = 0;
            float lo = 1e9f, hi = -1e9f;
            uint32_t seed = 12345;
            for (int i = 0; i < 300; i++) {
                seed = seed * 1664525u + 1013904223u;
                const float dt = 0.006f + static_cast<float>(seed >> 8 & 0xFFFF) / 65535.0f * 0.03f;
                frame(e, c, dt);
                astratto_snapshot_io sio = snap(e, ASTRATTO_SNAPSHOT_BODIES);
                const astratto_snap_mover* ss = findSnap<astratto_snap_mover>(sio, ASTRATTO_SNAP_MOVER, mover);
                if (!ss || !ss->grounded) off++;
                if (ss) { lo = std::min(lo, ss->y); hi = std::max(hi, ss->y); }
            }
            check("2.3: a mover standing still is reported on the ground in every one of 300 uneven frames, and does not bob", off == 0 && hi - lo < 0.005f);
        }
        float y0 = s ? s->z : 0;
        for (int i = 0; i < 60; i++) { astratto_cmd_mover_input& in = c.add<astratto_cmd_mover_input>(ASTRATTO_CMD_MOVER_INPUT); in.handle = mover; in.vz = 4; frame(e, c, 1.0f / 60); }
        io = snap(e, ASTRATTO_SNAPSHOT_BODIES);
        s = findSnap<astratto_snap_mover>(io, ASTRATTO_SNAP_MOVER, mover);
        check("asked to walk at 4 m/s for a second, it goes about four metres", s && s->z - y0 > 3.3f && s->z - y0 < 4.3f);
        for (int i = 0; i < 400; i++) { astratto_cmd_mover_input& in = c.add<astratto_cmd_mover_input>(ASTRATTO_CMD_MOVER_INPUT); in.handle = mover; in.vz = 6; frame(e, c, 1.0f / 60); }
        io = snap(e, ASTRATTO_SNAPSHOT_BODIES);
        s = findSnap<astratto_snap_mover>(io, ASTRATTO_SNAP_MOVER, mover);
        check("walking on into the wall, it is stopped at the wall's face", s && s->z < 40.0f && s->z > 39.0f);
    }
    {
        // the same run cut into different frame lengths ends in the same place: fixed steps
        astratto_handle a = reserve(e, ASTRATTO_KIND_MOVER), b = reserve(e, ASTRATTO_KIND_MOVER);
        astratto_cmd_mover_create& ma = c.add<astratto_cmd_mover_create>(ASTRATTO_CMD_MOVER_CREATE);
        ma.handle = a; ma.x = 50.5f; ma.y = FLOOR + 6.0f; ma.z = 5.5f; ma.radius = 0.3f; ma.height = 1.8f; ma.step = 0.6f; ma.flags = ASTRATTO_MOVER_AGENT;
        frame(e, c, 0);
        for (int i = 0; i < 120; i++) frame(e, c, 1.0f / 120);
        astratto_cmd_mover_create& mb = c.add<astratto_cmd_mover_create>(ASTRATTO_CMD_MOVER_CREATE);
        mb.handle = b; mb.x = 54.5f; mb.y = FLOOR + 6.0f; mb.z = 5.5f; mb.radius = 0.3f; mb.height = 1.8f; mb.step = 0.6f; mb.flags = ASTRATTO_MOVER_AGENT;
        frame(e, c, 0);
        for (int i = 0; i < 24; i++) frame(e, c, 1.0f / 24);
        astratto_snapshot_io io = snap(e, ASTRATTO_SNAPSHOT_BODIES);
        const astratto_snap_mover* sa = findSnap<astratto_snap_mover>(io, ASTRATTO_SNAP_MOVER, a);
        const astratto_snap_mover* sb = findSnap<astratto_snap_mover>(io, ASTRATTO_SNAP_MOVER, b);
        check("a body's fall is the same whether its second is cut into 120 frames or 24 (fixed-rate steps)", sa && sb && std::fabs(sa->y - sb->y) < 1e-3f && std::fabs(sa->vy - sb->vy) < 1e-3f);
        c.add<astratto_cmd_handle>(ASTRATTO_CMD_RELEASE).handle = a;
        c.add<astratto_cmd_handle>(ASTRATTO_CMD_RELEASE).handle = b;
        frame(e, c, 0);
        io = snap(e, ASTRATTO_SNAPSHOT_BODIES);
        check("released by command, they are gone from the snapshot", findSnap<astratto_snap_mover>(io, ASTRATTO_SNAP_MOVER, a) == nullptr);
        astratto_cmd_mover_input& in = c.add<astratto_cmd_mover_input>(ASTRATTO_CMD_MOVER_INPUT); in.handle = a;
        Events ev = frame(e, c, 0);
        check("and a released handle used again is refused, never reaching freed memory", ev.counted(ASTRATTO_EVT_ERROR) == 1);
    }

    // ------------------------------------------------------------ Sydney: death is terminal even across controller release
    {
        for (int order = 0; order < 2; order++) {
            const astratto_handle h = reserve(e, ASTRATTO_KIND_RAGDOLL);
            auto& r = c.add<astratto_cmd_ragdoll_create>(ASTRATTO_CMD_RAGDOLL_CREATE);
            r.handle = h; r.scale = 1; r.profile = ASTRATTO_SYDNEY_HUMAN;
            r.flags = ASTRATTO_RAGDOLL_ACTIVE | ASTRATTO_RAGDOLL_STAND;
            r.x = 35.5f + order * 4; r.y = FLOOR; r.z = 20.5f;
            frame(e, c, 0.25f);
            const uint32_t controls[] = {static_cast<uint32_t>(order == 0 ? ASTRATTO_CONTROL_RELEASE : ASTRATTO_CONTROL_DIE),
                                         static_cast<uint32_t>(order == 0 ? ASTRATTO_CONTROL_DIE : ASTRATTO_CONTROL_RELEASE)};
            for (uint32_t control : controls) {
                auto& cmd = c.add<astratto_cmd_ragdoll_control>(ASTRATTO_CMD_RAGDOLL_CONTROL);
                cmd.handle = h; cmd.control = control;
            }
            frame(e, c, 0);
            auto io = snap(e, ASTRATTO_SNAPSHOT_BODIES);
            const auto* s = findSnap<astratto_snap_ragdoll>(io, ASTRATTO_SNAP_RAGDOLL, h);
            bool terminal = s && s->state == ASTRATTO_SYDNEY_DEAD && s->outcome == ASTRATTO_OUTCOME_DEAD && !s->active;
            check(order == 0 ? "death after controller release still records a terminal dead body"
                             : "controller release during dying finishes death instead of freezing its transition", terminal);
            // Queued living intentions and later impacts may move the corpse, but may never restore balance control.
            auto& intent = c.add<astratto_cmd_ragdoll_intent>(ASTRATTO_CMD_RAGDOLL_INTENT);
            intent.handle = h; intent.intent = ASTRATTO_INTENT_WALK; intent.x = 1; intent.time = 4;
            auto& hit = c.add<astratto_cmd_ragdoll_hit>(ASTRATTO_CMD_RAGDOLL_HIT);
            hit.handle = h; hit.particle = ASTRATTO_RD_NECK; hit.dx = 1; hit.impulse = 80; hit.force = 200;
            for (int tick = 0; tick < 180; tick++) {
                if (tick % 30 == 0) {
                    auto& die = c.add<astratto_cmd_ragdoll_control>(ASTRATTO_CMD_RAGDOLL_CONTROL);
                    die.handle = h; die.control = ASTRATTO_CONTROL_DIE;
                }
                frame(e, c, 1.0f / 60);
                io = snap(e, ASTRATTO_SNAPSHOT_BODIES);
                s = findSnap<astratto_snap_ragdoll>(io, ASTRATTO_SNAP_RAGDOLL, h);
                terminal &= s && s->state == ASTRATTO_SYDNEY_DEAD && s->outcome == ASTRATTO_OUTCOME_DEAD && !s->active;
            }
            check("a released dead body stays terminal through repeated DIE, walking intent and a hit", terminal);
            c.add<astratto_cmd_handle>(ASTRATTO_CMD_RELEASE).handle = h;
            frame(e, c, 0);
        }
    }

    // ------------------------------------------------------------ Sydney: a ragdoll stands, is struck, falls, gets up or dies
    astratto_handle rd = reserve(e, ASTRATTO_KIND_RAGDOLL);
    {
        astratto_cmd_ragdoll_create& r = c.add<astratto_cmd_ragdoll_create>(ASTRATTO_CMD_RAGDOLL_CREATE);
        r.handle = rd; r.scale = 1; r.profile = ASTRATTO_SYDNEY_HUMAN; r.flags = ASTRATTO_RAGDOLL_ACTIVE | ASTRATTO_RAGDOLL_STAND;
        r.x = 20.5f; r.y = FLOOR; r.z = 20.5f;
        frame(e, c, 0);
        for (int i = 0; i < 90; i++) frame(e, c, 1.0f / 60);
        astratto_snapshot_io io = snap(e, ASTRATTO_SNAPSHOT_BODIES);
        const astratto_snap_ragdoll* s = findSnap<astratto_snap_ragdoll>(io, ASTRATTO_SNAP_RAGDOLL, rd);
        const float headY = s ? s->p[ASTRATTO_RD_HEAD * 3 + 1] : 0;
        check("a ragdoll made standing is held up by Sydney's controllers: after a second and a half its head is still well over the floor",
              s && s->active && headY > FLOOR + 1.3f);
        int states = 0, fell = 0;
        astratto_cmd_ragdoll_hit& h = c.add<astratto_cmd_ragdoll_hit>(ASTRATTO_CMD_RAGDOLL_HIT);
        h.handle = rd; h.particle = ASTRATTO_RD_NECK; h.dx = 1; h.impulse = 400; h.force = 80;
        for (int i = 0; i < 240; i++) {
            Events ev = frame(e, c, 1.0f / 60);
            ev.each(ASTRATTO_EVT_RAGDOLL_STATE, [&](const uint8_t* p, uint32_t) {
                astratto_evt_ragdoll_state st; std::memcpy(&st, p, sizeof st);
                if (st.handle == rd) { states++; if (st.state == ASTRATTO_SYDNEY_FALL || st.state == ASTRATTO_SYDNEY_DOWN) fell++; }
            });
        }
        check("struck hard at the neck it is knocked down, and its state machine says so in events", states > 0 && fell > 0);
        c.add<astratto_cmd_ragdoll_control>(ASTRATTO_CMD_RAGDOLL_CONTROL) = astratto_cmd_ragdoll_control{{ASTRATTO_CMD_RAGDOLL_CONTROL, sizeof(astratto_cmd_ragdoll_control)}, rd, ASTRATTO_CONTROL_DIE, 0};
        bool dead = false, settled = false;
        for (int i = 0; i < 900 && !settled; i++) {
            Events ev = frame(e, c, 1.0f / 60);
            ev.each(ASTRATTO_EVT_RAGDOLL_STATE, [&](const uint8_t* p, uint32_t) { astratto_evt_ragdoll_state st; std::memcpy(&st, p, sizeof st); if (st.handle == rd && st.state == ASTRATTO_SYDNEY_DEAD) dead = true; });
            ev.each(ASTRATTO_EVT_SETTLED, [&](const uint8_t* p, uint32_t) { astratto_evt_body b; std::memcpy(&b, p, sizeof b); if (b.handle == rd) settled = true; });
        }
        io = snap(e, ASTRATTO_SNAPSHOT_BODIES);
        s = findSnap<astratto_snap_ragdoll>(io, ASTRATTO_SNAP_RAGDOLL, rd);
        bool onFloor = s != nullptr;
        for (int i = 0; s && i < ASTRATTO_RD_PARTICLES; i++) if (s->p[i * 3 + 1] < FLOOR - 0.05f || s->p[i * 3 + 1] > FLOOR + 0.9f) onFloor = false;
        check("told to die it goes limp, lies on the floor (no particle through it) and comes to rest", dead && settled && onFloor);
    }
    {
        // ------------------------------------------------------------ Sydney 3: muscles, balance, steps, getting up
        auto make = [&](float x, float z, float strength) {
            astratto_handle h = reserve(e, ASTRATTO_KIND_RAGDOLL);
            astratto_cmd_ragdoll_create& r = c.add<astratto_cmd_ragdoll_create>(ASTRATTO_CMD_RAGDOLL_CREATE);
            r.handle = h; r.scale = 1; r.profile = ASTRATTO_SYDNEY_HUMAN; r.flags = ASTRATTO_RAGDOLL_ACTIVE | ASTRATTO_RAGDOLL_STAND;
            r.x = x; r.y = FLOOR; r.z = z;
            astratto_cmd_ragdoll_traits& t = c.add<astratto_cmd_ragdoll_traits>(ASTRATTO_CMD_RAGDOLL_TRAITS);
            t.handle = h; t.strength = strength; t.agility = 1; t.toughness = 1; t.endurance = 1;
            frame(e, c, 0);
            return h;
        };
        auto hit = [&](astratto_handle h, uint32_t point, float dx, float dz, float impulse) {
            astratto_cmd_ragdoll_hit& k = c.add<astratto_cmd_ragdoll_hit>(ASTRATTO_CMD_RAGDOLL_HIT);
            k.handle = h; k.particle = point; k.dx = dx; k.dz = dz; k.impulse = impulse; k.force = impulse * 0.25f;
        };
        auto snapOf = [&](astratto_handle h) { astratto_snapshot_io io = snap(e, ASTRATTO_SNAPSHOT_BODIES); const astratto_snap_ragdoll* s = findSnap<astratto_snap_ragdoll>(io, ASTRATTO_SNAP_RAGDOLL, h); return s ? *s : astratto_snap_ragdoll{}; };
        const astratto_handle a = make(12.5f, 12.5f, 1), b = make(30.5f, 12.5f, 1), d = make(48.5f, 12.5f, 1);
        float lo = 9, hi = -9;
        for (int i = 0; i < 300; i++) {
            frame(e, c, 1.0f / 60);
            if (i > 60) { const astratto_snap_ragdoll s = snapOf(a); lo = std::min(lo, s.p[ASTRATTO_RD_HEAD * 3 + 1]); hi = std::max(hi, s.p[ASTRATTO_RD_HEAD * 3 + 1]); }
        }
        check("Sydney 3: a body stands on its own muscles for five seconds, its head steady to a centimetre", hi - lo < 0.01f && lo > FLOOR + 1.6f);
        astratto_snap_ragdoll s0 = snapOf(a);
        bool turned = false;
        for (int i = 0; i < 13; i++) if (std::fabs(s0.seg_q[i * 4] - 1) > 1e-4f || std::fabs(s0.seg_c[i * 3 + 1]) > 0) turned = true;
        check("the snapshot carries every simulated segment's orientation and centre", turned && s0.seg_c[1 * 3 + 1] > FLOOR + 0.9f);
        // a firm shove at the hips: absorbed in place by ankle and hip; a harder one: a few steps, then standing again
        hit(a, ASTRATTO_RD_PELVIS, 1, 0, 40); hit(b, ASTRATTO_RD_PELVIS, 0, 1, 60);
        int stepsB = 0; bool fellA = false, fellB = false;
        for (int i = 0; i < 360; i++) {
            Events ev = frame(e, c, 1.0f / 60);
            ev.each(ASTRATTO_EVT_RAGDOLL_STATE, [&](const uint8_t* p, uint32_t) {
                astratto_evt_ragdoll_state st; std::memcpy(&st, p, sizeof st);
                if (st.handle == a && st.state == ASTRATTO_SYDNEY_FALL) fellA = true;
                if (st.handle == b && st.state == ASTRATTO_SYDNEY_FALL) fellB = true;
                if (st.handle == b && st.state == ASTRATTO_SYDNEY_STEP) stepsB++;
            });
        }
        const astratto_snap_ragdoll sa = snapOf(a), sb = snapOf(b);
        check("a shove of 40 N s at the hips is taken where it stands (ankle and hip strategy), without falling", !fellA && sa.state == ASTRATTO_SYDNEY_STAND && sa.outcome == ASTRATTO_OUTCOME_RECOVERED);
        check("one of 60 N s is caught by stepping: steps are taken and it ends standing", !fellB && stepsB > 0 && sb.state == ASTRATTO_SYDNEY_STAND && sb.outcome == ASTRATTO_OUTCOME_RECOVERED);
        // knocked flat: it falls, the parts that strike the ground say so, it lies a moment and gets up by itself
        hit(d, ASTRATTO_RD_NECK, 0, 1, 320);
        int impacts = 0; bool down = false, up = false;
        for (int i = 0; i < 60 * 14 && !up; i++) {
            Events ev = frame(e, c, 1.0f / 60);
            impacts += ev.counted(ASTRATTO_EVT_RAGDOLL_IMPACT);
            ev.each(ASTRATTO_EVT_RAGDOLL_STATE, [&](const uint8_t* p, uint32_t) {
                astratto_evt_ragdoll_state st; std::memcpy(&st, p, sizeof st);
                if (st.handle == d && st.state == ASTRATTO_SYDNEY_DOWN) down = true;
                if (st.handle == d && down && st.state == ASTRATTO_SYDNEY_STAND) up = true;
            });
        }
        check("knocked flat, its fall is reported as impacts on the ground", impacts > 0);
        check("and it gets back up on its own within fourteen seconds", down && up);
        // the weak are put down by less: a shove the strong take standing knocks a body with a third of the strength off its feet
        for (astratto_handle h : {a, b}) { astratto_cmd_handle& rr = c.add<astratto_cmd_handle>(ASTRATTO_CMD_RELEASE); rr.handle = h; }
        frame(e, c, 0);
        const astratto_handle w = make(12.5f, 12.5f, 0.35f);
        for (int i = 0; i < 60; i++) frame(e, c, 1.0f / 60);
        hit(w, ASTRATTO_RD_PELVIS, 1, 0, 40);
        bool fellW = false;
        for (int i = 0; i < 240; i++) {
            Events ev = frame(e, c, 1.0f / 60);
            ev.each(ASTRATTO_EVT_RAGDOLL_STATE, [&](const uint8_t* p, uint32_t) { astratto_evt_ragdoll_state st; std::memcpy(&st, p, sizeof st); if (st.handle == w && (st.state == ASTRATTO_SYDNEY_FALL || st.state == ASTRATTO_SYDNEY_STEP)) fellW = true; });
        }
        check("traits matter: the 40 N s shove the strong took standing puts a body with a third of the strength off balance", fellW);
        for (astratto_handle h : {d, w}) { astratto_cmd_handle& rr = c.add<astratto_cmd_handle>(ASTRATTO_CMD_RELEASE); rr.handle = h; }
        frame(e, c, 0);
    }
    {
        // a soft body of two particles and a stick: dropped, it lands and keeps its length
        astratto_handle sb = reserve(e, ASTRATTO_KIND_SOFTBODY);
        std::vector<uint8_t> rec(sizeof(astratto_cmd_softbody_create) + 2 * sizeof(astratto_softbody_particle) + sizeof(astratto_softbody_stick));
        astratto_cmd_softbody_create sc{};
        sc.head.type = ASTRATTO_CMD_SOFTBODY_CREATE; sc.head.bytes = static_cast<uint32_t>(rec.size());
        sc.handle = sb; sc.particles = 2; sc.sticks = 1;
        astratto_softbody_particle ps[2] = {{40.5f, FLOOR + 3.0f, 20.5f, 0.1f, 5}, {41.5f, FLOOR + 3.0f, 20.5f, 0.1f, 5}};
        astratto_softbody_stick st{0, 1};
        std::memcpy(rec.data(), &sc, sizeof sc);
        std::memcpy(rec.data() + sizeof sc, ps, sizeof ps);
        std::memcpy(rec.data() + sizeof sc + sizeof ps, &st, sizeof st);
        c.raw(rec.data(), rec.size());
        frame(e, c, 0);
        for (int i = 0; i < 180; i++) frame(e, c, 1.0f / 60);
        astratto_snapshot_io io = snap(e, ASTRATTO_SNAPSHOT_BODIES);
        const astratto_snap_softbody* s = findSnap<astratto_snap_softbody>(io, ASTRATTO_SNAP_SOFTBODY, sb);
        bool ok = s && s->particles == 2;
        if (ok) {
            float p[6];
            std::memcpy(p, reinterpret_cast<const uint8_t*>(s) + sizeof *s, sizeof p);
            const float len = std::sqrt((p[3] - p[0]) * (p[3] - p[0]) + (p[4] - p[1]) * (p[4] - p[1]) + (p[5] - p[2]) * (p[5] - p[2]));
            ok = std::fabs(len - 1.0f) < 0.05f && p[1] < FLOOR + 0.3f && p[1] > FLOOR - 0.05f;
        }
        check("a soft body dropped lands on the floor with its stick still its length", ok);
    }

    // ------------------------------------------------------------ a car
    astratto_handle car = reserve(e, ASTRATTO_KIND_VEHICLE);
    {
        astratto_cmd_vehicle_create& vc = c.add<astratto_cmd_vehicle_create>(ASTRATTO_CMD_VEHICLE_CREATE);
        astratto_vehicle_desc& d = vc.desc;
        vc.handle = car;
        d.mass = 1400; d.width = 1.8f; d.length = 4.4f; d.height = 1.45f; d.com_y = 0.5f;
        d.engine_accel = 4; d.max_velocity = 33; d.max_reverse = 8; d.first_gear_boost = 1.5f; d.second_gear_boost = 1.2f;
        d.traction = 1; d.traction_loss = 0.82f; d.traction_bias = 0.5f; d.steering_lock = 34; d.brake_decel = 9; d.brake_bias = 0.62f;
        d.susp_force = 1.4f; d.susp_damping = 0.28f; d.susp_upper = 0.28f; d.susp_lower = -0.16f; d.susp_bias = 0.5f; d.anti_dive = 0.3f;
        d.wheel_radius = 0.34f; d.handbrake_tyre = 0.55f;
        const float wh[12] = {-0.8f, 0.34f, 1.35f, 0.8f, 0.34f, 1.35f, -0.8f, 0.34f, -1.35f, 0.8f, 0.34f, -1.35f};
        std::memcpy(d.wheels, wh, sizeof wh);
        d.gears = 4; d.drive = ASTRATTO_DRIVE_REAR;
        vc.x = 8.5f; vc.y = FLOOR + 1.0f; vc.z = 6.5f; vc.yaw = 0;
        frame(e, c, 0);
        for (int i = 0; i < 120; i++) frame(e, c, 1.0f / 60);
        astratto_snapshot_io io = snap(e, ASTRATTO_SNAPSHOT_BODIES);
        const astratto_snap_vehicle* s = findSnap<astratto_snap_vehicle>(io, ASTRATTO_SNAP_VEHICLE, car);
        const float z0 = s ? s->matrix[14] : 0;
        check("a car dropped onto the floor comes to rest on its four wheels", s && s->grounded_wheels == 4 && s->speed < 0.3f);
        int gearEvents = 0;
        for (int i = 0; i < 180; i++) {
            astratto_cmd_vehicle_input& in = c.add<astratto_cmd_vehicle_input>(ASTRATTO_CMD_VEHICLE_INPUT);
            in.handle = car; in.throttle = 1; in.engine_on = 1;
            Events ev = frame(e, c, 1.0f / 60);
            gearEvents += ev.counted(ASTRATTO_EVT_GEAR);
        }
        io = snap(e, ASTRATTO_SNAPSHOT_BODIES);
        s = findSnap<astratto_snap_vehicle>(io, ASTRATTO_SNAP_VEHICLE, car);
        check("full throttle for three seconds drives it forward (+z), changing up through the gears", s && s->matrix[14] - z0 > 8 && s->forward_speed > 4 && s->gear >= 2 && gearEvents >= 1);
        if (s) std::printf("       car: %.1f m in 3 s, %.1f m/s, gear %u\n", s->matrix[14] - z0, s->forward_speed, s->gear);
        int impacts = 0;
        for (int i = 0; i < 600 && impacts == 0; i++) {
            astratto_cmd_vehicle_input& in = c.add<astratto_cmd_vehicle_input>(ASTRATTO_CMD_VEHICLE_INPUT);
            in.handle = car; in.throttle = 1; in.engine_on = 1;
            Events ev = frame(e, c, 1.0f / 60);
            impacts += ev.counted(ASTRATTO_EVT_BLOCK_HIT) + ev.counted(ASTRATTO_EVT_VEHICLE_IMPACT);
        }
        io = snap(e, ASTRATTO_SNAPSHOT_BODIES);
        s = findSnap<astratto_snap_vehicle>(io, ASTRATTO_SNAP_VEHICLE, car);
        check("driven on into the wall it strikes it (an impact event) and does not pass through", impacts > 0 && s && s->matrix[14] < 40.0f);
    }

    // ------------------------------------------------------------ AI: a policy, perception through the engine's sight lines, routes
    astratto_handle policy = reserve(e, ASTRATTO_KIND_POLICY), agent = reserve(e, ASTRATTO_KIND_AGENT), body = reserve(e, ASTRATTO_KIND_MOVER), quarry = reserve(e, ASTRATTO_KIND_MOVER);
    enum { IDLE = 10, CHASE = 11, GOTO = 12 };
    {
        // idle unless the target is seen (then approach to 2 m) or fact 0 is set (then go to the goal)
        astratto_cmd_policy_create pc{};
        astratto_action acts[3]{};
        astratto_consideration cons[2]{};
        acts[0] = astratto_action{IDLE, ASTRATTO_ACT_IDLE, 0.2f, 0, 0, 0, 0};
        acts[1] = astratto_action{CHASE, ASTRATTO_ACT_APPROACH, 1.0f, 2.0f, 4.0f, 0, 1};
        acts[2] = astratto_action{GOTO, ASTRATTO_ACT_MOVE_TO_GOAL, 0.9f, 0, 3.0f, 1, 1};
        cons[0] = astratto_consideration{ASTRATTO_IN_TARGET_SEEN, ASTRATTO_CURVE_STEP, 0, 1, 1, 0};
        cons[1] = astratto_consideration{ASTRATTO_IN_FACT0, ASTRATTO_CURVE_STEP, 0, 1, 1, 0};
        const size_t policyBytes = sizeof pc + sizeof acts + sizeof cons;
        pc.head.type = ASTRATTO_CMD_POLICY_CREATE; pc.head.bytes = static_cast<uint32_t>((policyBytes + 7) & ~size_t(7));
        pc.handle = policy; pc.actions = 3; pc.considerations = 2; pc.inertia = 0.1f; pc.think = 0.1f;
        c.raw(&pc, sizeof pc); c.raw(acts, sizeof acts); c.raw(cons, sizeof cons);
        c.bytes.resize(c.bytes.size() + pc.head.bytes - policyBytes, 0);
        astratto_cmd_mover_create& mb = c.add<astratto_cmd_mover_create>(ASTRATTO_CMD_MOVER_CREATE);
        mb.handle = body; mb.x = 31.5f; mb.y = FLOOR; mb.z = 50.5f; mb.radius = 0.3f; mb.height = 1.8f; mb.step = 0.6f; mb.flags = ASTRATTO_MOVER_AGENT;
        astratto_cmd_agent_create& ac = c.add<astratto_cmd_agent_create>(ASTRATTO_CMD_AGENT_CREATE);
        ac.handle = agent; ac.policy = policy; ac.body = body; ac.eye = 1.6f; ac.view_range = 60; ac.view_cos = -1; ac.hear_range = 30; ac.team = 1; ac.seed = 7;
        // the quarry stands behind the wall, out of sight
        astratto_cmd_mover_create& mq = c.add<astratto_cmd_mover_create>(ASTRATTO_CMD_MOVER_CREATE);
        mq.handle = quarry; mq.x = 10.5f; mq.y = FLOOR; mq.z = 30.5f; mq.radius = 0.3f; mq.height = 1.8f; mq.step = 0.6f;
        astratto_cmd_agent_target& t = c.add<astratto_cmd_agent_target>(ASTRATTO_CMD_AGENT_TARGET);
        t.handle = agent; t.target = quarry;
        frame(e, c, 0);
        int chose = -1, sees = 0;
        for (int i = 0; i < 30; i++) {
            Events ev = frame(e, c, 1.0f / 30);
            ev.each(ASTRATTO_EVT_AGENT_ACTION, [&](const uint8_t* p, uint32_t) { astratto_evt_agent a; std::memcpy(&a, p, sizeof a); if (a.handle == agent) chose = a.value; });
            sees += ev.counted(ASTRATTO_EVT_AGENT_SEES);
        }
        check("an agent whose target is behind a wall does not see it, and its policy chooses to idle", sees == 0 && chose == IDLE);
        // the game sets a fact: go to the goal, through the gap, to the quarry's side of the wall
        astratto_cmd_agent_goal& g = c.add<astratto_cmd_agent_goal>(ASTRATTO_CMD_AGENT_GOAL);
        g.handle = agent; g.x = 31.5f; g.y = FLOOR; g.z = 30.5f;
        astratto_cmd_agent_fact& f = c.add<astratto_cmd_agent_fact>(ASTRATTO_CMD_AGENT_FACT);
        f.handle = agent; f.index = 0; f.value = 1;
        bool wentToGoal = false, sawIt = false, chased = false, arrived = false;
        for (int i = 0; i < 900 && !arrived; i++) {
            Events ev = frame(e, c, 1.0f / 30);
            ev.each(ASTRATTO_EVT_AGENT_ACTION, [&](const uint8_t* p, uint32_t) { astratto_evt_agent a; std::memcpy(&a, p, sizeof a); if (a.handle == agent) { if (a.value == GOTO) wentToGoal = true; if (a.value == CHASE) chased = true; } });
            ev.each(ASTRATTO_EVT_AGENT_SEES, [&](const uint8_t* p, uint32_t) { astratto_evt_agent a; std::memcpy(&a, p, sizeof a); if (a.handle == agent && a.other == quarry) sawIt = true; });
            if (chased) ev.each(ASTRATTO_EVT_AGENT_ARRIVED, [&](const uint8_t* p, uint32_t) { astratto_evt_agent a; std::memcpy(&a, p, sizeof a); if (a.handle == agent) arrived = true; });
        }
        astratto_snapshot_io io = snap(e, ASTRATTO_SNAPSHOT_BODIES);
        const astratto_snap_mover* sb = findSnap<astratto_snap_mover>(io, ASTRATTO_SNAP_MOVER, body);
        const float d = sb ? std::sqrt((sb->x - 10.5f) * (sb->x - 10.5f) + (sb->z - 30.5f) * (sb->z - 30.5f)) : 1e9f;
        check("told by a fact to go, it routes through the gap in the wall, sees the quarry with the engine's sight lines, chooses to approach and arrives by it, walking its mover",
              wentToGoal && sawIt && chased && arrived && d < 3.0f && sb->z < 40.0f);
        astratto_snapshot_io ai = snap(e, ASTRATTO_SNAPSHOT_AGENTS);
        const astratto_snap_agent* sa = findSnap<astratto_snap_agent>(ai, ASTRATTO_SNAP_AGENT, agent);
        check("its snapshot says what it does and what it sees", sa && sa->action == CHASE && sa->sees == 1 && sa->target == quarry);
        // heard: a noise near it is reported
        astratto_cmd_noise& n = c.add<astratto_cmd_noise>(ASTRATTO_CMD_NOISE);
        n.x = sb ? sb->x + 3 : 0; n.y = FLOOR; n.z = sb ? sb->z : 0; n.radius = 20; n.loudness = 1; n.team = 0;
        Events ev = frame(e, c, 1.0f / 30);
        check("a noise in its hearing comes back as heard", ev.counted(ASTRATTO_EVT_AGENT_HEARD) >= 1);
    }

    // An agent without a mover isolates the route follower from acceleration and contacts.
    // Crossing intermediate waypoints must not insert stationary frames into an otherwise valid walk.
    {
        Cmds edits;
        auto& platform = edits.add<astratto_edit_fill>(ASTRATTO_EDIT_FILL);
        platform.x0 = 49; platform.x1 = 59; platform.y0 = platform.y1 = FLOOR; platform.z0 = 4; platform.z1 = 12;
        platform.block = STONE | ASTRATTO_SET_RAW;
        astratto_world_edit(e, world, edits.bytes.data(), static_cast<uint32_t>(edits.bytes.size()));
        astratto_handle walker = reserve(e, ASTRATTO_KIND_AGENT);
        astratto_cmd_agent_create& ac = c.add<astratto_cmd_agent_create>(ASTRATTO_CMD_AGENT_CREATE);
        ac.handle = walker; ac.policy = policy; ac.x = 43.5f; ac.y = FLOOR; ac.z = 8.5f; ac.eye = 1.6f; ac.seed = 17;
        astratto_cmd_agent_goal& goal = c.add<astratto_cmd_agent_goal>(ASTRATTO_CMD_AGENT_GOAL);
        goal.handle = walker; goal.x = 56.5f; goal.y = FLOOR + 1; goal.z = 8.5f;
        astratto_cmd_agent_fact& fact = c.add<astratto_cmd_agent_fact>(ASTRATTO_CMD_AGENT_FACT);
        fact.handle = walker; fact.index = 0; fact.value = 1;
        frame(e, c, 0);
        float previousX = 43.5f, previousZ = 8.5f;
        int walkingFrames = 0, pauses = 0;
        bool arrived = false;
        for (int i = 0; i < 900 && !arrived; i++) {
            Events ev = frame(e, c, 1.0f / 30);
            ev.each(ASTRATTO_EVT_AGENT_ARRIVED, [&](const uint8_t* p, uint32_t) {
                astratto_evt_agent event; std::memcpy(&event, p, sizeof event);
                if (event.handle == walker) arrived = true;
            });
            auto state = snap(e, ASTRATTO_SNAPSHOT_AGENTS);
            const auto* agentState = findSnap<astratto_snap_agent>(state, ASTRATTO_SNAP_AGENT, walker);
            if (agentState) {
                const float dx = agentState->x - previousX, dz = agentState->z - previousZ;
                if (i > 3 && agentState->moving && agentState->route_cells > 1) {
                    walkingFrames++;
                    if (dx * dx + dz * dz < 1e-10f) pauses++;
                }
                previousX = agentState->x; previousZ = agentState->z;
            }
        }
        if (!arrived || pauses != 0) std::printf("      waypoint continuity: arrived %d, walking %d, pauses %d\n", arrived, walkingFrames, pauses);
        check("route following reaches a raised goal without stationary intermediate waypoint frames", arrived && walkingFrames > 20 && pauses == 0);
        c.add<astratto_cmd_handle>(ASTRATTO_CMD_RELEASE).handle = walker;
        frame(e, c, 0);
        platform.block = AIR | ASTRATTO_SET_RAW;
        astratto_world_edit(e, world, edits.bytes.data(), static_cast<uint32_t>(edits.bytes.size()));
    }

    // ------------------------------------------------------------ audio: the mixer, rendered on request
    {
        const int rate = 44100, n = rate / 2;
        std::vector<float> tone(n);
        for (int i = 0; i < n; i++) tone[i] = 0.5f * std::sin(2 * 3.14159265f * 440.0f * i / rate);
        astratto_sound_desc sd{};
        sd.struct_size = sizeof sd; sd.channels = 1; sd.rate = rate; sd.frames = n; sd.samples = tone.data();
        astratto_handle sound = 0;
        check("a sound is handed over (copied) and named by a handle", astratto_sound_create(e, &sd, &sound) == ASTRATTO_OK && ASTRATTO_HANDLE_KIND(sound) == ASTRATTO_KIND_SOUND);
        std::vector<float> out(2048 * 2);
        astratto_audio_render(e, out.data(), 2048);
        float silent = 0;
        for (float s : out) silent = std::max(silent, std::fabs(s));
        astratto_handle left = reserve(e, ASTRATTO_KIND_VOICE), near = reserve(e, ASTRATTO_KIND_VOICE);
        astratto_cmd_listener& l = c.add<astratto_cmd_listener>(ASTRATTO_CMD_LISTENER);
        l.x = 20; l.y = FLOOR + 1.6f; l.z = 10; l.yaw = 0;
        astratto_cmd_voice_play& p = c.add<astratto_cmd_voice_play>(ASTRATTO_CMD_VOICE_PLAY);
        p.voice = left; p.sound = sound; p.bus = ASTRATTO_BUS_SFX; p.flags = ASTRATTO_VOICE_POSITIONAL; p.gain = 1; p.pitch = 1;
        p.x = 12; p.y = FLOOR + 1.6f; p.z = 10; p.range = 40;     // to the listener's left (it faces -z, so -x is its left)
        frame(e, c, 1.0f / 60);
        astratto_audio_render(e, out.data(), 2048);
        double lsum = 0, rsum = 0;
        for (int i = 0; i < 2048; i++) { lsum += std::fabs(out[i * 2]); rsum += std::fabs(out[i * 2 + 1]); }
        check("silence before anything plays; a positional voice to the listener's left is heard, louder in the left channel", silent < 1e-6f && lsum > 1.0 && lsum > rsum * 1.3);
        // the same sound far behind a wall is quieter than near and in the open
        astratto_cmd_voice_stop& st = c.add<astratto_cmd_voice_stop>(ASTRATTO_CMD_VOICE_STOP); st.voice = left;
        frame(e, c, 1.0f / 60);
        for (int i = 0; i < 4; i++) astratto_audio_render(e, out.data(), 2048);
        astratto_cmd_voice_play& q = c.add<astratto_cmd_voice_play>(ASTRATTO_CMD_VOICE_PLAY);
        q.voice = near; q.sound = sound; q.bus = ASTRATTO_BUS_SFX; q.flags = ASTRATTO_VOICE_POSITIONAL | ASTRATTO_VOICE_OCCLUDE; q.gain = 1; q.pitch = 1;
        q.x = 20; q.y = FLOOR + 1.6f; q.z = 50; q.range = 60;   // the far side of the wall at z = 40
        frame(e, c, 1.0f / 60);
        astratto_audio_render(e, out.data(), 2048);
        double far = 0;
        for (float s : out) far += std::fabs(s);
        check("a voice beyond a wall and further off is quieter than the near one in the open", far > 0.01 && far < (lsum + rsum) * 0.5);
        bool ended = false;
        for (int i = 0; i < 60 && !ended; i++) {
            astratto_audio_render(e, out.data(), 2048);
            Events ev = frame(e, c, 1.0f / 60);
            ev.each(ASTRATTO_EVT_VOICE_ENDED, [&](const uint8_t* pp, uint32_t) { astratto_evt_body b; std::memcpy(&b, pp, sizeof b); if (b.handle == near) ended = true; });
        }
        check("a voice that has played its sound to the end says so in an event", ended);
        astratto_snapshot_io io = snap(e, ASTRATTO_SNAPSHOT_AUDIO);
        const astratto_audio_stats* as = static_cast<const astratto_audio_stats*>(io.data);
        check("the audio snapshot says there is no device and that frames were mixed", as && as->device == 0 && as->frames_mixed >= 2048 * 6);

        // 2.3: a streamed sound - written while it plays, silent (not ended) when dry, ended once drained after its last write
        astratto_sound_desc rdesc{};
        rdesc.struct_size = sizeof rdesc; rdesc.channels = 2; rdesc.frames = 8192;
        astratto_handle stream = 0;
        check("2.3: a stream is made with a ring at the mixer's rate", astratto_stream_create(e, &rdesc, &stream) == ASTRATTO_OK && ASTRATTO_HANDLE_KIND(stream) == ASTRATTO_KIND_SOUND);
        std::vector<float> st2(10000 * 2);
        for (int i = 0; i < 10000; i++) st2[i * 2] = st2[i * 2 + 1] = 0.4f * std::sin(2 * 3.14159265f * 220.0f * i / rate);
        astratto_stream_io w{};
        w.struct_size = sizeof w; w.stream = stream; w.samples = st2.data(); w.frames = 10000;
        astratto_stream_write(e, &w);
        check("the ring takes only what fits (8192 of 10000 frames)", w.accepted == 8192);
        astratto_handle music = reserve(e, ASTRATTO_KIND_VOICE);
        astratto_cmd_voice_play& mp = c.add<astratto_cmd_voice_play>(ASTRATTO_CMD_VOICE_PLAY);
        mp.voice = music; mp.sound = stream; mp.bus = ASTRATTO_BUS_MUSIC; mp.gain = 1; mp.pitch = 1;
        frame(e, c, 1.0f / 60);
        astratto_audio_render(e, out.data(), 2048);
        double heard = 0;
        for (float s : out) heard += std::fabs(s);
        // the 2048 frames played freed that much room: the rest of the tone goes in (240 frames of room left)
        w.samples = st2.data() + 8192 * 2; w.frames = 1808;
        astratto_stream_write(e, &w);
        const uint32_t topped = w.accepted;
        check("a playing stream is heard, and room frees as it is drained", heard > 1.0 && topped == 1808);
        // pause the music bus: held silent and undrained; resumed, it carries on
        astratto_cmd_audio_pause& pz = c.add<astratto_cmd_audio_pause>(ASTRATTO_CMD_AUDIO_PAUSE); pz.bus_paused = 1u << ASTRATTO_BUS_MUSIC;
        frame(e, c, 1.0f / 60);
        astratto_audio_render(e, out.data(), 2048);
        double held = 0;
        for (float s : out) held += std::fabs(s);
        w.samples = st2.data(); w.frames = 1000;
        astratto_stream_write(e, &w);
        check("a paused bus is silent (the earlier sounds' reverb tail aside) and its stream is not drained (no room freed while held)", held < heard * 0.01 && w.accepted == 240);
        astratto_cmd_audio_pause& rz = c.add<astratto_cmd_audio_pause>(ASTRATTO_CMD_AUDIO_PAUSE); rz.bus_paused = 0;
        frame(e, c, 1.0f / 60);
        astratto_audio_render(e, out.data(), 2048);
        double resumed = 0;
        for (float s : out) resumed += std::fabs(s);
        w.frames = 0; w.flags = ASTRATTO_STREAM_END;
        astratto_stream_write(e, &w);
        check("resumed, the stream plays on", resumed > 1.0);
        bool streamEnded = false;
        for (int i = 0; i < 30 && !streamEnded; i++) {
            astratto_audio_render(e, out.data(), 2048);
            Events ev = frame(e, c, 1.0f / 60);
            ev.each(ASTRATTO_EVT_VOICE_ENDED, [&](const uint8_t* pp, uint32_t) { astratto_evt_body b; std::memcpy(&b, pp, sizeof b); if (b.handle == music) streamEnded = true; });
        }
        check("after its last write is drained, the stream's voice ends", streamEnded);
    }

    // ------------------------------------------------------------ a C++ gameplay module
    if (moduleDir) {
        std::string path = std::string(moduleDir) +
#if defined(_WIN32)
            "/astratto_example_module.dll";
#elif defined(__APPLE__)
            "/libastratto_example_module.dylib";
#else
            "/libastratto_example_module.so";
#endif
        astratto_handle mod = 0;
        const int32_t st = astratto_module_load(e, path.c_str(), "radio 31.5 4 45.5 0.5 1", &mod);
        check("a C++ gameplay module is loaded from its library and named by a handle", st == ASTRATTO_OK && ASTRATTO_HANDLE_KIND(mod) == ASTRATTO_KIND_MODULE);
        int logs = 0, heard = 0;
        for (int i = 0; i < 60; i++) {
            Events ev = frame(e, c, 1.0f / 30);
            logs += ev.counted(ASTRATTO_EVT_MODULE_LOG);
            heard += ev.counted(ASTRATTO_EVT_AGENT_HEARD);
        }
        check("run inside each frame, its radio's noises (its own commands) are heard by the agent, and its log lines come back as events", logs >= 1 && heard >= 2);
        check("released, it is shut down and unloaded", astratto_release(e, mod) == ASTRATTO_OK && astratto_release(e, mod) == ASTRATTO_ERR_HANDLE);
        astratto_handle bad = 0;
        check("a library that is not there is refused, not a crash", astratto_module_load(e, "no_such_module_library", "", &bad) != ASTRATTO_OK && bad == 0);
    }

    // ------------------------------------------------------------ releasing the world takes what lives in it
    {
        astratto_snapshot_io io = snap(e, ASTRATTO_SNAPSHOT_STATS);
        const astratto_stats* s = static_cast<const astratto_stats*>(io.data);
        const bool before = s && s->live[ASTRATTO_KIND_WORLD] == 1 && s->sim_steps > 0 && s->sim_time > 10;
        check("the stats count the world, the fixed steps run and the time simulated (the frame's dt is used)", before);
        check("the world is released by its handle; a view of it is refused afterwards",
              astratto_release(e, world) == ASTRATTO_OK && astratto_world_get_view(e, world, &v) == ASTRATTO_ERR_HANDLE);
    }
    astratto_destroy(e);
}
