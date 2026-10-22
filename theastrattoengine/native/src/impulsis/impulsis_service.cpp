#include "impulsis/impulsis_service.h"

#include <algorithm>
#include <cmath>
#include <cstring>

#include "physics/physics_service.h"

namespace astratto {

using namespace impulsis;

namespace {
constexpr float MOVER_MASS = 75.0f;   // what a character weighs to the bodies it walks into (kg)

Q quatOf(const float* r) {
    Q q(r[0], r[1], r[2], r[3]);
    if (q.w == 0 && q.x == 0 && q.y == 0 && q.z == 0) q = Q();
    return qnorm(q);
}
}  // namespace

void ImpulsisService::ensure(Engine& e) {
    if (!space_) space_.reset(new Space(&e.pool()));
}

astratto_handle ImpulsisService::reserve(uint32_t kind) {
    if (kind == ASTRATTO_KIND_RIGID) return rigids_.reserve();
    if (kind == ASTRATTO_KIND_JOINT) return joints_.reserve();
    return ASTRATTO_NO_HANDLE;
}

bool ImpulsisService::release(Engine& e, astratto_handle h) {
    ensure(e);
    if (handleKind(h) == ASTRATTO_KIND_RIGID) {
        if (!rigids_.valid(h)) return false;
        const int i = space_->find(h);
        if (i >= 0) space_->remove(i);
        return rigids_.release(h);
    }
    if (handleKind(h) == ASTRATTO_KIND_JOINT) {
        if (!joints_.valid(h)) return false;
        space_->removeJoint(h);
        return joints_.release(h);
    }
    return false;
}

uint32_t ImpulsisService::live(uint32_t kind) const {
    if (kind == ASTRATTO_KIND_RIGID) return rigids_.live();
    if (kind == ASTRATTO_KIND_JOINT) return joints_.live();
    return 0;
}

void ImpulsisService::worldChanged(Engine& e) {
    ensure(e);
    for (size_t i = 0; i < space_->count(); i++) space_->body(static_cast<int>(i)).wake();
    worldEdits_ = 0;
}

int ImpulsisService::command(Engine& e, uint32_t type, const uint8_t* rec, uint32_t bytes) {
    if (type < 160 || type > 191) return -1;
    ensure(e);
    switch (type) {
        case ASTRATTO_CMD_RIGID_CREATE: {
            astratto_cmd_rigid_create c;
            if (!readRecord(rec, bytes, c)) return ASTRATTO_ERR_STREAM;
            if (c.shape == ASTRATTO_SHAPE_HULL && (c.hull_count < 4 || c.hull_count > 64 || bytes < sizeof(c) + c.hull_count * 12)) return ASTRATTO_ERR_ARGUMENT;
            if (c.shape > ASTRATTO_SHAPE_HULL) return ASTRATTO_ERR_ARGUMENT;
            if (!rigids_.valid(c.handle) || rigids_.get(c.handle)) { e.error(type, ASTRATTO_ERR_HANDLE, c.handle); return ASTRATTO_OK; }
            Body b;
            b.handle = c.handle;
            b.shape.type = c.shape;
            switch (c.shape) {
                case ASTRATTO_SHAPE_SPHERE: b.shape.radius = std::max(0.005f, c.size[0]); break;
                case ASTRATTO_SHAPE_CAPSULE: b.shape.radius = std::max(0.005f, c.size[0]); b.shape.halfHeight = std::max(0.0f, c.size[1]); break;
                case ASTRATTO_SHAPE_HULL: {
                    const float* pts = reinterpret_cast<const float*>(rec + sizeof(c));
                    for (uint32_t k = 0; k < c.hull_count; k++) { float p[3]; std::memcpy(p, pts + k * 3, sizeof(p)); b.shape.hull.emplace_back(p[0], p[1], p[2]); }
                    break;
                }
                default: b.shape.half = V3(std::max(0.005f, c.size[0]), std::max(0.005f, c.size[1]), std::max(0.005f, c.size[2])); break;
            }
            b.flags = c.flags; b.group = c.group; b.mask = c.mask; b.material = c.material;
            b.mass = c.mass;
            b.p = V3(c.pos[0], c.pos[1], c.pos[2]);
            b.q = quatOf(c.rot);
            b.v = V3(c.vel[0], c.vel[1], c.vel[2]);
            b.w = V3(c.ang[0], c.ang[1], c.ang[2]);
            b.friction = c.friction > 0 ? c.friction : 0.6f;
            b.restitution = std::clamp(c.restitution, 0.0f, 1.0f);
            if (c.linear_damping > 0) b.linDamp = c.linear_damping;
            if (c.angular_damping > 0) b.angDamp = c.angular_damping;
            b.gravityScale = c.gravity_scale == 0 ? 1.0f : c.gravity_scale;
            space_->add(b);
            rigids_.put(c.handle, std::unique_ptr<Slot>(new Slot()));
            return ASTRATTO_OK;
        }
        case ASTRATTO_CMD_RIGID_PLACE: {
            astratto_cmd_rigid_place c;
            if (!readRecord(rec, bytes, c)) return ASTRATTO_ERR_STREAM;
            const int i = space_->find(c.handle);
            if (i < 0) { e.error(type, ASTRATTO_ERR_HANDLE, c.handle); return ASTRATTO_OK; }
            Body& b = space_->body(i);
            b.p = V3(c.pos[0], c.pos[1], c.pos[2]); b.q = quatOf(c.rot);
            b.v = V3(c.vel[0], c.vel[1], c.vel[2]); b.w = V3(c.ang[0], c.ang[1], c.ang[2]);
            b.prevP = b.p; b.prevQ = b.q;
            b.update(); b.wake();
            return ASTRATTO_OK;
        }
        case ASTRATTO_CMD_RIGID_IMPULSE: {
            astratto_cmd_rigid_impulse c;
            if (!readRecord(rec, bytes, c)) return ASTRATTO_ERR_STREAM;
            const int i = space_->find(c.handle);
            if (i < 0) { e.error(type, ASTRATTO_ERR_HANDLE, c.handle); return ASTRATTO_OK; }
            space_->impulseAt(i, V3(c.x, c.y, c.z), V3(c.jx, c.jy, c.jz));
            return ASTRATTO_OK;
        }
        case ASTRATTO_CMD_RIGID_SET: {
            astratto_cmd_rigid_set c;
            if (!readRecord(rec, bytes, c)) return ASTRATTO_ERR_STREAM;
            const int i = space_->find(c.handle);
            if (i < 0) { e.error(type, ASTRATTO_ERR_HANDLE, c.handle); return ASTRATTO_OK; }
            Body& b = space_->body(i);
            const bool wasFixed = !b.dynamic();
            b.flags = c.flags;
            if (c.friction > 0) b.friction = c.friction;
            b.restitution = std::clamp(c.restitution, 0.0f, 1.0f);
            if (c.gravity_scale != 0) b.gravityScale = c.gravity_scale;
            if (wasFixed != !b.dynamic() || (b.flags & (ASTRATTO_RIGID_KINEMATIC | ASTRATTO_RIGID_STATIC)) == 0) {
                // a change between fixed and free: its mass properties again
                if (b.dynamic() || (!(b.flags & (ASTRATTO_RIGID_KINEMATIC | ASTRATTO_RIGID_STATIC)) && b.mass > 0)) {
                    b.invMass = 1.0f / b.mass;
                    const V3 I = b.shape.inertia(b.mass);
                    b.invInertiaLocal = V3(I.x > 0 ? 1 / I.x : 0, I.y > 0 ? 1 / I.y : 0, I.z > 0 ? 1 / I.z : 0);
                } else { b.invMass = 0; b.invInertiaLocal = V3(); }
            }
            if (!std::isnan(c.target[0]) && (b.flags & ASTRATTO_RIGID_KINEMATIC)) { b.hasTarget = true; b.targetP = V3(c.target[0], c.target[1], c.target[2]); }
            b.update(); b.wake();
            return ASTRATTO_OK;
        }
        case ASTRATTO_CMD_RIGID_EXPLODE: {
            astratto_cmd_rigid_explode c;
            if (!readRecord(rec, bytes, c)) return ASTRATTO_ERR_STREAM;
            space_->explode(V3(c.x, c.y, c.z), c.radius, c.impulse);
            return ASTRATTO_OK;
        }
        case ASTRATTO_CMD_JOINT_CREATE: {
            astratto_cmd_joint_create c;
            if (!readRecord(rec, bytes, c)) return ASTRATTO_ERR_STREAM;
            if (!joints_.valid(c.handle) || joints_.get(c.handle)) { e.error(type, ASTRATTO_ERR_HANDLE, c.handle); return ASTRATTO_OK; }
            const int a = space_->find(c.a), b = c.b ? space_->find(c.b) : -1;
            if (a < 0 || (c.b && b < 0) || c.type > ASTRATTO_JOINT_FIXED) { e.error(type, ASTRATTO_ERR_ARGUMENT, c.handle); return ASTRATTO_OK; }
            Joint j;
            j.handle = c.handle; j.type = c.type; j.flags = c.flags;
            j.a = a; j.b = b; j.ha = c.a; j.hb = c.b;
            j.localA = V3(c.anchor[0], c.anchor[1], c.anchor[2]);
            j.axisA = V3(c.axis[0], c.axis[1], c.axis[2]);
            if (len2(j.axisA) < 1e-8f) j.axisA = V3(0, 1, 0);
            j.lo = std::min(c.lo, c.hi); j.hi = std::max(c.lo, c.hi);
            j.motorSpeed = c.motor_speed; j.motorForce = std::max(0.0f, c.motor_force); j.breakForce = std::max(0.0f, c.break_force);
            space_->addJoint(j);
            joints_.put(c.handle, std::unique_ptr<Slot>(new Slot{true}));
            return ASTRATTO_OK;
        }
        case ASTRATTO_CMD_JOINT_MOTOR: {
            astratto_cmd_joint_motor c;
            if (!readRecord(rec, bytes, c)) return ASTRATTO_ERR_STREAM;
            Joint* j = space_->joint(c.handle);
            if (!j) { e.error(type, ASTRATTO_ERR_HANDLE, c.handle); return ASTRATTO_OK; }
            j->motorSpeed = c.speed; j->motorForce = std::max(0.0f, c.force);
            if (c.force > 0) j->flags |= ASTRATTO_JOINT_MOTOR; else j->flags &= ~static_cast<uint32_t>(ASTRATTO_JOINT_MOTOR);
            if (j->a >= 0) space_->body(j->a).wake();
            if (j->b >= 0) space_->body(j->b).wake();
            return ASTRATTO_OK;
        }
        case ASTRATTO_CMD_JOINT_LIMITS: {
            astratto_cmd_joint_limits c;
            if (!readRecord(rec, bytes, c)) return ASTRATTO_ERR_STREAM;
            Joint* j = space_->joint(c.handle);
            if (!j) { e.error(type, ASTRATTO_ERR_HANDLE, c.handle); return ASTRATTO_OK; }
            j->flags = (j->flags & ~static_cast<uint32_t>(ASTRATTO_JOINT_LIMITED)) | (c.flags & ASTRATTO_JOINT_LIMITED);
            j->lo = std::min(c.lo, c.hi); j->hi = std::max(c.lo, c.hi);
            if (j->a >= 0) space_->body(j->a).wake();
            if (j->b >= 0) space_->body(j->b).wake();
            return ASTRATTO_OK;
        }
        default: return ASTRATTO_ERR_ARGUMENT;
    }
}

// ------------------------------------------------------------------ characters among the bodies
void ImpulsisService::syncMovers(Engine& e, float dt) {
    PhysicsService* ps = e.service<PhysicsService>();
    if (!ps) return;
    seen_.clear();
    ps->eachMover([&](astratto_handle h, const Mover& m) {
        seen_.push_back(h);
        int i = space_->find(h);
        if (i < 0) {
            Body b;
            b.handle = h; b.proxy = true; b.mover = h;
            b.shape.type = ASTRATTO_SHAPE_CAPSULE;
            b.shape.radius = m.radius;
            b.shape.halfHeight = std::max(0.0f, m.h() * 0.5f - m.radius);
            b.mass = MOVER_MASS; b.friction = 0.3f;
            b.p = V3(m.x, m.y + m.h() * 0.5f, m.z);
            i = space_->add(b);
        }
        Body& b = space_->body(i);
        b.shape.halfHeight = std::max(0.0f, m.h() * 0.5f - m.radius);
        b.shape.finish();
        b.p = V3(m.x, m.y + m.h() * 0.5f, m.z);
        b.q = Q(); b.w = V3();
        b.drive = V3(m.vx, m.vy, m.vz);
        b.v = b.drive;
        b.push = V3(); b.pushSteps = 0;
        b.update();
    });
    // movers that are gone
    for (size_t i = 0; i < space_->count();) {
        Body& b = space_->body(static_cast<int>(i));
        if (b.proxy && std::find(seen_.begin(), seen_.end(), b.handle) == seen_.end()) { space_->remove(static_cast<int>(i)); continue; }
        i++;
    }
    (void)dt;
}

void ImpulsisService::pushMovers(Engine& e, float dt) {
    PhysicsService* ps = e.service<PhysicsService>();
    if (!ps) return;
    const VoxelWorld* w = e.active();
    for (astratto_handle h : seen_) {
        const int i = space_->find(h);
        if (i < 0) continue;
        Body& b = space_->body(i);
        if (b.pushSteps == 0 || len2(b.push) < 1e-10f) continue;
        Mover* m = ps->mover(h);
        if (!m) continue;
        // what pushed the character back: the change of velocity the solver gave it, over the steps it ran
        const V3 avg = b.push / static_cast<float>(b.pushSteps);
        V3 d = b.push * STEP;
        const float l = len(d);
        if (l > 0.3f) d = d * (0.3f / l);
        auto free = [&](float x, float y, float z) { return !w || Mover::bodyFree(w, x, y, z, m->radius, m->h()); };
        if (free(m->x + d.x, m->y, m->z)) { m->x += d.x; m->vx += avg.x; }
        if (free(m->x, m->y, m->z + d.z)) { m->z += d.z; m->vz += avg.z; }
        if (d.y > 0 && free(m->x, m->y + d.y, m->z)) {
            // something under it holds it up: it stands on it
            m->y += d.y;
            if (m->vy < 0) m->vy = 0;
            m->onGround = true;
        }
    }
    (void)dt;
}

void ImpulsisService::step(Engine& e, float dt) {
    ensure(e);
    if (dt <= 0) return;
    const VoxelWorld* w = e.active();
    if (w != world_) { world_ = w; for (size_t i = 0; i < space_->count(); i++) space_->body(static_cast<int>(i)).wake(); }
    if (w && w->edits() != worldEdits_) {
        // the world changed under the bodies: whatever rests on it looks again
        worldEdits_ = w->edits();
        for (size_t i = 0; i < space_->count(); i++) { Body& b = space_->body(static_cast<int>(i)); if (b.dynamic() && !b.proxy) b.wake(); }
    }
    // kinematic bodies with a place to be by the end of the frame move there at an even speed
    for (size_t i = 0; i < space_->count(); i++) {
        Body& b = space_->body(static_cast<int>(i));
        if (b.hasTarget && (b.flags & ASTRATTO_RIGID_KINEMATIC)) { b.v = (b.targetP - b.p) / dt; b.hasTarget = false; }
    }
    syncMovers(e, dt);
    space_->impacts().clear();
    space_->brokenJoints().clear();
    space_->fellAsleep().clear();
    const int steps = space_->advance(w, dt, 22.0f);
    e.counters().sim_steps += static_cast<uint64_t>(steps);
    pushMovers(e, dt);
    for (const Impact& im : space_->impacts()) {
        astratto_evt_rigid_impact ev{};
        ev.a = im.a; ev.b = im.b; ev.other = im.other;
        ev.x = im.p.x; ev.y = im.p.y; ev.z = im.p.z; ev.nx = im.n.x; ev.ny = im.n.y; ev.nz = im.n.z;
        ev.speed = im.speed; ev.impulse = im.impulse; ev.material_a = im.ma; ev.material_b = im.mb;
        e.emit(ev, ASTRATTO_EVT_RIGID_IMPACT);
    }
    for (astratto_handle h : space_->brokenJoints()) {
        astratto_evt_body ev{};
        ev.handle = h;
        if (Joint* j = space_->joint(h)) ev.value = j->force;
        e.emit(ev, ASTRATTO_EVT_JOINT_BROKEN);
    }
    for (astratto_handle h : space_->fellAsleep()) { astratto_evt_body ev{}; ev.handle = h; e.emit(ev, ASTRATTO_EVT_RIGID_SLEEP); }
}

bool ImpulsisService::snapshot(Engine& e, uint32_t kind, std::vector<uint8_t>& out) {
    if (kind != ASTRATTO_SNAPSHOT_RIGIDS) return false;
    ensure(e);
    const float a = std::clamp(space_->alpha(), 0.0f, 1.0f);
    for (size_t i = 0; i < space_->count(); i++) {
        const Body& b = space_->body(static_cast<int>(i));
        if (b.proxy) continue;
        astratto_snap_rigid s{};
        s.handle = b.handle;
        // the pose between the last two steps, by how far the frame got into the next
        const V3 p = b.awake ? b.prevP + (b.p - b.prevP) * a : b.p;
        const Q q = b.awake ? slerp(b.prevQ, b.q, a) : b.q;
        s.pos[0] = p.x; s.pos[1] = p.y; s.pos[2] = p.z;
        s.rot[0] = q.w; s.rot[1] = q.x; s.rot[2] = q.y; s.rot[3] = q.z;
        s.vel[0] = b.v.x; s.vel[1] = b.v.y; s.vel[2] = b.v.z;
        s.ang[0] = b.w.x; s.ang[1] = b.w.y; s.ang[2] = b.w.z;
        s.asleep = b.awake ? 0u : 1u;
        s.contacts = static_cast<uint32_t>(b.contacts);
        s.shape = b.shape.type; s.material = b.material;
        appendRecord(out, s, ASTRATTO_SNAP_RIGID);
    }
    for (const Joint& j : space_->joints()) {
        astratto_snap_joint s{};
        s.handle = j.handle; s.angle = j.angle; s.speed = j.speed; s.force = j.force; s.broken = j.broken ? 1u : 0u;
        appendRecord(out, s, ASTRATTO_SNAP_JOINT);
    }
    return true;
}

}  // namespace astratto
