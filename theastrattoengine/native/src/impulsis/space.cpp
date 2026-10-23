// Astratto Engine - Impulsis: the fixed step. Broadphase (sweep and prune), narrowphase (collide.cpp), islands, the
// sequential-impulse solver for contacts and joints, semi-implicit Euler integration, bullets and sleep.
#include <algorithm>
#include <cmath>
#include <cstring>
#include <thread>

#include "core/job_pool.h"
#include "impulsis/space.h"

namespace astratto {
namespace impulsis {

namespace {

uint64_t mix(uint64_t a, uint64_t b) {
    uint64_t z = a * 0x9E3779B97F4A7C15ull ^ (b + 0x632BE59BD9B4E019ull + (a << 6) + (a >> 2));
    z = (z ^ (z >> 30)) * 0xBF58476D1CE4E5B9ull;
    z = (z ^ (z >> 27)) * 0x94D049BB133111EBull;
    return z ^ (z >> 31);
}
uint64_t pairKey(astratto_handle a, astratto_handle b) { return a < b ? mix(a, b) : mix(b, a); }
uint64_t cellKey(astratto_handle a, int x, int y, int z) {
    return mix(a, (static_cast<uint64_t>(static_cast<uint32_t>(x)) << 42) ^ (static_cast<uint64_t>(static_cast<uint32_t>(y)) << 21) ^ static_cast<uint32_t>(z) ^ 0x5DEECE66Dull);
}

float effMass(const Body& a, const Body* b, V3 rA, V3 rB, V3 n) {
    float k = a.invMass + dot(cross(a.invI * cross(rA, n), rA), n);
    if (b) k += b->invMass + dot(cross(b->invI * cross(rB, n), rB), n);
    return k > 1e-12f ? 1.0f / k : 0.0f;
}

void applyImpulse(Body& a, Body* b, V3 rA, V3 rB, V3 P) {
    if (a.invMass > 0) { a.v += P * a.invMass; a.w += a.invI * cross(rA, P); }
    if (b && b->invMass > 0) { b->v -= P * b->invMass; b->w -= b->invI * cross(rB, P); }
}

void applyAngular(Body& a, Body* b, V3 L) {
    if (a.invMass > 0) a.w -= a.invI * L;
    if (b && b->invMass > 0) b->w += b->invI * L;
}

}  // namespace

// ------------------------------------------------------------------ bodies and joints
int Space::add(const Body& in) {
    Body b = in;
    b.shape.finish();
    b.q = qnorm(b.q);
    const bool fixed = (b.flags & (ASTRATTO_RIGID_STATIC | ASTRATTO_RIGID_KINEMATIC)) != 0 || !(b.mass > 0);
    if (fixed) { b.invMass = 0; b.invInertiaLocal = V3(); if (!(b.flags & ASTRATTO_RIGID_KINEMATIC)) b.flags |= ASTRATTO_RIGID_STATIC; }
    else {
        b.invMass = 1.0f / b.mass;
        const V3 I = b.shape.inertia(b.mass);
        b.invInertiaLocal = V3(I.x > 0 ? 1 / I.x : 0, I.y > 0 ? 1 / I.y : 0, I.z > 0 ? 1 / I.z : 0);
    }
    if (b.proxy) b.invInertiaLocal = V3();
    b.update();
    b.prevP = b.p; b.prevQ = b.q;
    b.awake = true;
    const int i = static_cast<int>(bodies_.size());
    bodies_.push_back(b);
    index_[b.handle] = i;
    return i;
}

void Space::remove(int i) {
    if (i < 0 || i >= static_cast<int>(bodies_.size())) return;
    const astratto_handle h = bodies_[static_cast<size_t>(i)].handle;
    // joints on it go; the bodies it touched wake (they may have been resting on it)
    for (auto& j : joints_) if (j.a == i || j.b == i) { j.broken = true; forget(j); }
    joints_.erase(std::remove_if(joints_.begin(), joints_.end(), [&](const Joint& j) { return j.a == i || j.b == i; }), joints_.end());
    for (const Manifold& m : manifolds_) if (m.count > 0 && (m.a == i || m.b == i)) { if (m.a >= 0) bodies_[static_cast<size_t>(m.a)].wake(); if (m.b >= 0) bodies_[static_cast<size_t>(m.b)].wake(); }
    manifolds_.clear();
    const int last = static_cast<int>(bodies_.size()) - 1;
    if (i != last) {
        bodies_[static_cast<size_t>(i)] = bodies_[static_cast<size_t>(last)];
        index_[bodies_[static_cast<size_t>(i)].handle] = i;
        for (auto& j : joints_) { if (j.a == last) j.a = i; if (j.b == last) j.b = i; }
    }
    bodies_.pop_back();
    index_.erase(h);
}

int Space::addJoint(const Joint& in) {
    Joint j = in;
    Body& A = bodies_[static_cast<size_t>(j.a)];
    const Body* B = j.b >= 0 ? &bodies_[static_cast<size_t>(j.b)] : nullptr;
    const V3 anchor = j.localA, axis = norm(j.axisA);   // given in world terms; turned into each body's frame here
    j.localA = A.toLocal(anchor);
    j.localB = B ? B->toLocal(anchor) : anchor;
    j.axisA = transpose(A.R) * axis;
    j.axisB = B ? transpose(B->R) * axis : axis;
    V3 t1, t2;
    basis(axis, t1, t2);
    j.perpA = transpose(A.R) * t1;
    j.perpB = B ? transpose(B->R) * t1 : t1;
    j.rel0 = B ? conj(A.q) * B->q : conj(A.q);
    A.wake();
    if (B) bodies_[static_cast<size_t>(j.b)].wake();
    // the two bodies a joint couples do not collide with each other (unless it says they do)
    if (B && !(j.flags & ASTRATTO_JOINT_COLLIDE)) jointed_.insert(pairKey(A.handle, B->handle));
    joints_.push_back(j);
    return static_cast<int>(joints_.size()) - 1;
}

void Space::forget(const Joint& j) {
    if (j.b < 0 || (j.flags & ASTRATTO_JOINT_COLLIDE)) return;
    auto it = jointed_.find(pairKey(bodies_[static_cast<size_t>(j.a)].handle, bodies_[static_cast<size_t>(j.b)].handle));
    if (it != jointed_.end()) jointed_.erase(it);
}

void Space::removeJoint(astratto_handle h) {
    for (auto& j : joints_) if (j.handle == h) { forget(j); if (j.a >= 0) bodies_[static_cast<size_t>(j.a)].wake(); if (j.b >= 0) bodies_[static_cast<size_t>(j.b)].wake(); }
    joints_.erase(std::remove_if(joints_.begin(), joints_.end(), [&](const Joint& j) { return j.handle == h; }), joints_.end());
}

Joint* Space::joint(astratto_handle h) { for (auto& j : joints_) if (j.handle == h) return &j; return nullptr; }

void Space::impulseAt(int i, V3 point, V3 j) {
    Body& b = bodies_[static_cast<size_t>(i)];
    if (!b.dynamic()) return;
    b.wake();
    b.v += j * b.invMass;
    b.w += b.invI * cross(point - b.p, j);
}

void Space::explode(V3 c, float radius, float impulse) {
    for (size_t i = 0; i < bodies_.size(); i++) {
        Body& b = bodies_[i];
        if (!b.dynamic() || b.proxy) continue;
        const V3 d = b.p - c;
        const float l = len(d);
        if (l > radius) continue;
        const float k = impulse * (1 - l / radius);
        impulseAt(static_cast<int>(i), b.p - norm(d) * (b.shape.boundRadius * 0.3f), norm(d + V3(0, 0.3f, 0)) * k);
    }
}

void Space::wakeNear(V3 lo, V3 hi) {
    const Aabb box{lo, hi};
    for (Body& b : bodies_) if (b.dynamic() && !b.awake && b.box.overlaps(box)) b.wake();
}

int Space::raycast(V3 o, V3 d, float maxDist, float& t, V3& n) const {
    const V3 dir = norm(d);
    int best = -1;
    t = maxDist;
    for (size_t i = 0; i < bodies_.size(); i++) {
        const Body& b = bodies_[i];
        if (b.proxy) continue;
        // against the body's bounding sphere, then its box in its own frame (spheres and capsules as their boxes)
        const V3 oc = o - b.p;
        const float bb = dot(oc, dir), cc = len2(oc) - b.shape.boundRadius * b.shape.boundRadius;
        if (cc > 0 && bb > 0) continue;
        if (bb * bb - cc < 0) continue;
        V3 half = b.shape.half;
        if (b.shape.type == ASTRATTO_SHAPE_SPHERE) half = V3(b.shape.radius, b.shape.radius, b.shape.radius);
        else if (b.shape.type == ASTRATTO_SHAPE_CAPSULE) half = V3(b.shape.radius, b.shape.radius + b.shape.halfHeight, b.shape.radius);
        else if (b.shape.type == ASTRATTO_SHAPE_HULL) { half = V3(); for (const V3& p : b.shape.hull) half = vmax(half, vabs(p)); }
        const V3 lo = transpose(b.R) * oc, ld = transpose(b.R) * dir;
        float tmin = 0, tmax = t; int axis = -1; float sgn = 1;
        bool miss = false;
        for (int k = 0; k < 3 && !miss; k++) {
            if (std::fabs(ld[k]) < 1e-9f) { if (std::fabs(lo[k]) > half[k]) miss = true; continue; }
            float t1 = (-half[k] - lo[k]) / ld[k], t2 = (half[k] - lo[k]) / ld[k];
            float s = -1;
            if (t1 > t2) { std::swap(t1, t2); s = 1; }
            if (t1 > tmin) { tmin = t1; axis = k; sgn = s; }
            tmax = std::min(tmax, t2);
            if (tmin > tmax) miss = true;
        }
        if (miss || tmin >= t) continue;
        t = tmin; best = static_cast<int>(i);
        V3 ln; if (axis >= 0) ln[axis] = sgn; else ln = V3(0, 1, 0);
        n = b.R * ln;
    }
    return best;
}

// ------------------------------------------------------------------ the step
int Space::advance(const VoxelWorld* w, float dt, float gravity) {
    acc_ += std::min(dt, 0.25f);
    int steps = 0;
    while (acc_ >= step_ && steps < 8) {
        tick(w, gravity);
        acc_ -= step_;
        steps++;
    }
    if (steps == 8) acc_ = std::min(acc_, step_);
    return steps;
}

void Space::tick(const VoxelWorld* w, float gravity) {
    for (Body& b : bodies_) { b.prevP = b.p; b.prevQ = b.q; }
    step(w, gravity);
}

bool Space::touches(const Body& a, const Body& b) const {
    if (!a.moves() && !b.moves()) return false;
    if (a.proxy && b.proxy) return false;
    if ((a.proxy && (b.flags & ASTRATTO_RIGID_NO_MOVERS)) || (b.proxy && (a.flags & ASTRATTO_RIGID_NO_MOVERS))) return false;
    if ((a.flags & ASTRATTO_RIGID_DEBRIS) && (b.flags & ASTRATTO_RIGID_DEBRIS)) return false;
    if (!a.dynamic() && !b.dynamic()) return false;
    if (!a.awake && !b.awake) return false;
    const uint32_t am = a.mask ? a.mask : ~0u, bm = b.mask ? b.mask : ~0u, ag = a.group ? a.group : ~0u, bg = b.group ? b.group : ~0u;
    if (!((ag & bm) && (bg & am))) return false;
    return jointed_.empty() || jointed_.find(pairKey(a.handle, b.handle)) == jointed_.end();
}

void Space::broadphase() {
    pairs_.clear();
    std::vector<int> order(bodies_.size());
    for (size_t i = 0; i < bodies_.size(); i++) {
        Body& b = bodies_[i];
        const V3 r(b.shape.boundRadius, b.shape.boundRadius, b.shape.boundRadius);
        // a body's box is its bounding sphere's, swept by its motion over the step, and the contact margin
        const V3 sweep = vabs(b.v) * step_ + V3(SPECULATIVE, SPECULATIVE, SPECULATIVE);
        if (b.shape.type == ASTRATTO_SHAPE_BOX) {
            V3 e;
            for (int k = 0; k < 3; k++) e[k] = std::fabs(b.R.r[k].x) * b.shape.half.x + std::fabs(b.R.r[k].y) * b.shape.half.y + std::fabs(b.R.r[k].z) * b.shape.half.z;
            b.box = Aabb{b.p - e - sweep, b.p + e + sweep};
        } else b.box = Aabb{b.p - r - sweep, b.p + r + sweep};
        order[i] = static_cast<int>(i);
    }
    std::sort(order.begin(), order.end(), [&](int x, int y) { return bodies_[static_cast<size_t>(x)].box.lo.x < bodies_[static_cast<size_t>(y)].box.lo.x; });
    for (size_t i = 0; i < order.size(); i++) {
        const Body& a = bodies_[static_cast<size_t>(order[i])];
        for (size_t k = i + 1; k < order.size(); k++) {
            const Body& b = bodies_[static_cast<size_t>(order[k])];
            if (b.box.lo.x > a.box.hi.x) break;
            if (!a.box.overlaps(b.box) || !touches(a, b)) continue;
            // the dynamic one first, so a contact always has a body that moves
            if (a.dynamic()) pairs_.emplace_back(order[i], order[k]); else pairs_.emplace_back(order[k], order[i]);
        }
    }
}

void Space::worldContacts(const VoxelWorld* w, int i, std::vector<Manifold>& out) {
    const Body& b = bodies_[static_cast<size_t>(i)];
    if (!w || (b.flags & ASTRATTO_RIGID_NO_WORLD) || b.proxy || !b.dynamic() || !b.awake) return;
    const int x0 = static_cast<int>(std::floor(b.box.lo.x)), y0 = static_cast<int>(std::floor(b.box.lo.y)), z0 = static_cast<int>(std::floor(b.box.lo.z));
    const int x1 = static_cast<int>(std::floor(b.box.hi.x)), y1 = static_cast<int>(std::floor(b.box.hi.y)), z1 = static_cast<int>(std::floor(b.box.hi.z));
    if ((x1 - x0 + 1) * (y1 - y0 + 1) * (z1 - z0 + 1) > 512) return;
    for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) for (int z = z0; z <= z1; z++) {
        if (!w->solid(x, y, z)) continue;
        // a face with a solid cell beside it is inside the world's surface: never a contact
        uint32_t open = 0;
        if (!w->solid(x - 1, y, z)) open |= 1;
        if (!w->solid(x + 1, y, z)) open |= 2;
        if (!w->solid(x, y - 1, z)) open |= 4;
        if (!w->solid(x, y + 1, z)) open |= 8;
        if (!w->solid(x, y, z - 1)) open |= 16;
        if (!w->solid(x, y, z + 1)) open |= 32;
        if (!open) continue;
        Manifold m;
        m.a = i; m.b = -1; m.cx = x; m.cy = y; m.cz = z; m.block = w->get(x, y, z);
        collideCell(b, x, y, z, open, m);
        if (m.count == 0) continue;
        m.key = cellKey(b.handle, x, y, z);
        m.friction = b.friction; m.restitution = b.restitution; m.rolling = b.rolling();
        out.push_back(m);
    }
}

void Space::narrowphase(const VoxelWorld* w) {
    const size_t workers = pool_ ? std::max<size_t>(1, pool_->workers()) : 1;
    if (scratch_.size() < workers) scratch_.resize(workers);
    for (auto& s : scratch_) s.clear();
    const size_t nPairs = pairs_.size(), nBodies = bodies_.size();
    auto job = [&](size_t k, unsigned worker) {
        std::vector<Manifold>& out = scratch_[std::min<size_t>(worker, scratch_.size() - 1)];
        if (k < nPairs) {
            const int ia = pairs_[k].first, ib = pairs_[k].second;
            const Body& a = bodies_[static_cast<size_t>(ia)];
            const Body& b = bodies_[static_cast<size_t>(ib)];
            Manifold m;
            m.a = ia; m.b = ib;
            collide(a, b, m);
            if (m.count == 0) return;
            m.key = pairKey(a.handle, b.handle);
            m.friction = std::sqrt(a.friction * b.friction);
            m.restitution = std::max(a.restitution, b.restitution);
            m.rolling = std::max(a.rolling(), b.rolling());
            out.push_back(m);
        } else worldContacts(w, static_cast<int>(k - nPairs), out);
    };
    if (pool_ && nPairs + nBodies > 32) pool_->parallelFor(nPairs + nBodies, job);
    else for (size_t k = 0; k < nPairs + nBodies; k++) job(k, 0);
    manifolds_.clear();
    for (auto& s : scratch_) manifolds_.insert(manifolds_.end(), s.begin(), s.end());
    // the same order whatever the threads did (a run is repeatable)
    std::sort(manifolds_.begin(), manifolds_.end(), [](const Manifold& x, const Manifold& y) { return x.key < y.key; });
    // warm starting: impulses of the contacts that were there last step, matched by where they are on the body
    for (Manifold& m : manifolds_) {
        auto it = previous_.find(m.key);
        if (it == previous_.end()) continue;
        Manifold& old = it->second;
        m.fresh = false;
        const bool hullPair = bodies_[static_cast<size_t>(m.a)].shape.type == ASTRATTO_SHAPE_HULL || (m.b >= 0 && bodies_[static_cast<size_t>(m.b)].shape.type == ASTRATTO_SHAPE_HULL);
        for (int i = 0; i < m.count; i++) {
            Contact& c = m.pts[i];
            for (int k = 0; k < old.count; k++) {
                if (len2(old.pts[k].localA - c.localA) < 0.0004f) { c.Pn = old.pts[k].Pn; c.Pt1 = old.pts[k].Pt1; c.Pt2 = old.pts[k].Pt2; break; }
            }
        }
        // GJK gives one point a step: the old points that still hold are kept, to make a stable manifold of four
        if (hullPair && m.count < 4) {
            const Body& A = bodies_[static_cast<size_t>(m.a)];
            const Body* B = m.b >= 0 ? &bodies_[static_cast<size_t>(m.b)] : nullptr;
            for (int k = 0; k < old.count && m.count < 4; k++) {
                const Contact& oc = old.pts[k];
                const V3 pa = A.toWorld(oc.localA), pb = B ? B->toWorld(oc.localB) : oc.localB;
                const V3 n = m.pts[0].n;
                const float sep = dot(pa - pb, n);
                if (sep > SPECULATIVE || len2((pa - pb) - n * sep) > 0.0004f) continue;
                bool near = false;
                for (int i = 0; i < m.count; i++) if (len2(m.pts[i].localA - oc.localA) < 0.0004f) near = true;
                if (near) continue;
                Contact c = oc;
                c.n = n; c.pen = -sep; c.rA = pa - A.p; c.rB = B ? pa - B->p : V3();
                m.pts[m.count++] = c;
            }
        }
    }
    previous_.clear();
}

void Space::islands() {
    const size_t n = bodies_.size();
    parent_.resize(n);
    for (size_t i = 0; i < n; i++) parent_[i] = static_cast<int>(i);
    auto find = [&](int x) { while (parent_[static_cast<size_t>(x)] != x) { parent_[static_cast<size_t>(x)] = parent_[static_cast<size_t>(parent_[static_cast<size_t>(x)])]; x = parent_[static_cast<size_t>(x)]; } return x; };
    auto unite = [&](int a, int b) {
        if (a < 0 || b < 0) return;
        if (!bodies_[static_cast<size_t>(a)].dynamic() || !bodies_[static_cast<size_t>(b)].dynamic()) return;   // a static floor does not join two piles
        if (bodies_[static_cast<size_t>(a)].proxy || bodies_[static_cast<size_t>(b)].proxy) return;
        a = find(a); b = find(b); if (a != b) parent_[static_cast<size_t>(a)] = b;
    };
    for (const Manifold& m : manifolds_) unite(m.a, m.b);
    for (const Joint& j : joints_) unite(j.a, j.b);
    for (size_t i = 0; i < n; i++) bodies_[i].island = find(static_cast<int>(i));
    // an island is awake if any of it is (something resting on a sleeping pile wakes it all)
    std::vector<uint8_t> awake(n, 0);
    for (size_t i = 0; i < n; i++) if (bodies_[i].dynamic() && bodies_[i].awake) awake[static_cast<size_t>(bodies_[i].island)] = 1;
    // a proxy or a moving kinematic body touching a sleeper wakes it
    for (const Manifold& m : manifolds_) {
        if (m.b < 0) continue;
        const Body& a = bodies_[static_cast<size_t>(m.a)];
        const Body& b = bodies_[static_cast<size_t>(m.b)];
        if ((b.proxy || (b.flags & ASTRATTO_RIGID_KINEMATIC)) && len2(b.v) > 1e-4f) awake[static_cast<size_t>(a.island)] = 1;
        if ((a.proxy || (a.flags & ASTRATTO_RIGID_KINEMATIC)) && len2(a.v) > 1e-4f) awake[static_cast<size_t>(b.island)] = 1;
    }
    for (size_t i = 0; i < n; i++) if (bodies_[i].dynamic() && awake[static_cast<size_t>(bodies_[i].island)] && !bodies_[i].awake) bodies_[i].wake();
}

void Space::prepare(float dt) {
    for (Body& b : bodies_) b.contacts = 0;
    for (Manifold& m : manifolds_) {
        Body& A = bodies_[static_cast<size_t>(m.a)];
        Body* B = m.b >= 0 ? &bodies_[static_cast<size_t>(m.b)] : nullptr;
        A.contacts++;
        if (B) B->contacts++;
        for (int i = 0; i < m.count; i++) {
            Contact& c = m.pts[i];
            basis(c.n, c.t1, c.t2);
            c.mN = effMass(A, B, c.rA, c.rB, c.n);
            c.mT1 = effMass(A, B, c.rA, c.rB, c.t1);
            c.mT2 = effMass(A, B, c.rA, c.rB, c.t2);
            const V3 vr = A.velocityAt(c.rA) - (B ? B->velocityAt(c.rB) : V3());
            const float vn = dot(vr, c.n);
            // Baumgarte: a share of the overlap is put right each step; a gap may be closed, no more (speculative)
            c.bias = c.pen > 0 ? BAUMGARTE / dt * std::max(0.0f, c.pen - SLOP) : c.pen / dt;
            c.bounce = vn < -RESTITUTION_THRESHOLD ? -m.restitution * vn : 0.0f;
            c.approach = -vn;
        }
    }
}

void Space::warmStart() {
    for (Manifold& m : manifolds_) {
        Body& A = bodies_[static_cast<size_t>(m.a)];
        Body* B = m.b >= 0 ? &bodies_[static_cast<size_t>(m.b)] : nullptr;
        for (int i = 0; i < m.count; i++) {
            Contact& c = m.pts[i];
            const V3 P = c.n * c.Pn + c.t1 * c.Pt1 + c.t2 * c.Pt2;
            applyImpulse(A, B, c.rA, c.rB, P);
        }
    }
}

void Space::solveVelocities() {
    for (Manifold& m : manifolds_) {
        Body& A = bodies_[static_cast<size_t>(m.a)];
        Body* B = m.b >= 0 ? &bodies_[static_cast<size_t>(m.b)] : nullptr;
        if (!A.awake && (!B || !B->awake)) continue;
        for (int i = 0; i < m.count; i++) {
            Contact& c = m.pts[i];
            // the normal: never pulls (the accumulated impulse stays at or above zero)
            V3 vr = A.velocityAt(c.rA) - (B ? B->velocityAt(c.rB) : V3());
            const float vn = dot(vr, c.n);
            // a gap may be closed at most (bias < 0); an overlap is pushed apart; a fast approach bounces
            const float target = c.bounce > 0 ? std::max(c.bias, c.bounce) : c.bias;
            float dP = c.mN * (target - vn);
            const float P0 = c.Pn;
            c.Pn = std::max(0.0f, P0 + dP);
            dP = c.Pn - P0;
            applyImpulse(A, B, c.rA, c.rB, c.n * dP);
            // friction: both directions together, kept inside the Coulomb cone of the normal impulse
            vr = A.velocityAt(c.rA) - (B ? B->velocityAt(c.rB) : V3());
            const float d1 = -c.mT1 * dot(vr, c.t1), d2 = -c.mT2 * dot(vr, c.t2);
            float p1 = c.Pt1 + d1, p2 = c.Pt2 + d2;
            const float maxF = m.friction * c.Pn, mag = std::sqrt(p1 * p1 + p2 * p2);
            if (mag > maxF && mag > 1e-12f) { p1 *= maxF / mag; p2 *= maxF / mag; }
            const V3 P = c.t1 * (p1 - c.Pt1) + c.t2 * (p2 - c.Pt2);
            c.Pt1 = p1; c.Pt2 = p2;
            applyImpulse(A, B, c.rA, c.rB, P);
        }
        // rolling resistance: a ball or a capsule rolling on something slows, as a real one does (the contact patch)
        if (m.rolling > 0) {
            float pn = 0;
            for (int i = 0; i < m.count; i++) pn += m.pts[i].Pn;
            const V3 wr = A.w - (B ? B->w : V3());
            const float wl = len(wr);
            if (wl > 1e-6f && pn > 0) {
                const V3 dir = wr / wl;
                const float k = dot(dir, A.invI * dir) + (B ? dot(dir, B->invI * dir) : 0);
                if (k > 1e-12f) {
                    V3 Pr = m.Proll - dir * (wl / k);
                    const float maxR = m.rolling * pn, pl = len(Pr);
                    if (pl > maxR) Pr = Pr * (maxR / pl);
                    const V3 d = Pr - m.Proll;
                    m.Proll = Pr;
                    A.w += A.invI * d;
                    if (B) B->w -= B->invI * d;
                }
            }
        }
    }
}

// ------------------------------------------------------------------ joints
void Space::prepareJoint(Joint& j, float dt) {
    Body& A = bodies_[static_cast<size_t>(j.a)];
    Body* B = j.b >= 0 ? &bodies_[static_cast<size_t>(j.b)] : nullptr;
    j.rA = A.R * j.localA;
    j.rB = B ? B->R * j.localB : V3();
    const V3 pA = A.p + j.rA, pB = B ? B->p + j.rB : j.localB;
    // the point constraint's 3 x 3 effective mass: K = (mA + mB) I - [rA]x IA [rA]x - [rB]x IB [rB]x
    const float ms = A.invMass + (B ? B->invMass : 0);
    M3 K = M3::diag(V3(ms, ms, ms));
    const M3 sA = skew(j.rA);
    {
        const M3 t = sA * A.invI * sA;
        for (int r = 0; r < 3; r++) K.r[r] -= t.r[r];
        if (B) { const M3 sB = skew(j.rB); const M3 u = sB * B->invI * sB; for (int r = 0; r < 3; r++) K.r[r] -= u.r[r]; }
    }
    j.kPoint = inverse(K);
    // the axis and the angle about it
    const V3 axA = A.R * j.axisA;
    j.ax = axA;
    const V3 perA = A.R * j.perpA, perB = B ? B->R * j.perpB : j.perpB;
    // project B's reference onto the plane across the axis, then measure
    const V3 pb = norm(perB - axA * dot(perB, axA));
    j.angle = std::atan2(dot(cross(perA, pb), axA), dot(perA, pb));
    const V3 wrel = (B ? B->w : V3()) - A.w;
    if (j.type == ASTRATTO_JOINT_HINGE) j.speed = dot(wrel, axA);
    const float kAx = dot(axA, A.invI * axA) + (B ? dot(axA, B->invI * axA) : 0);
    j.motorMass = j.limitMass = kAx > 1e-12f ? 1 / kAx : 0;
    if (j.type == ASTRATTO_JOINT_SLIDER) {
        // the slide along the axis, and the two directions across it
        const V3 d = pB - pA;
        j.angle = dot(d, axA);
        j.speed = dot(((B ? B->velocityAt(j.rB) : V3()) - A.velocityAt(j.rA)), axA);
        basis(axA, j.lin1, j.lin2);
        j.slideMass[0] = effMass(A, B, j.rA, j.rB, j.lin1);
        j.slideMass[1] = effMass(A, B, j.rA, j.rB, j.lin2);
        j.motorMass = j.limitMass = effMass(A, B, j.rA, j.rB, axA);
    }
    // limits, speculative: a limit within reach this step is already in force, and lets the joint come up to it and
    // no further (no overshoot to be pushed back out of, so a stop does not bounce)
    j.limitState = 0;
    if ((j.flags & ASTRATTO_JOINT_LIMITED) && (j.type == ASTRATTO_JOINT_HINGE || j.type == ASTRATTO_JOINT_SLIDER)) {
        const float margin = j.type == ASTRATTO_JOINT_HINGE ? 0.15f : 0.08f;
        if (std::fabs(j.hi - j.lo) < 1e-4f) { j.limitState = 3; j.limitBias = BAUMGARTE / dt * (j.angle - j.lo); }
        else if (j.angle < j.lo + margin && (j.angle - j.lo) <= (j.hi - j.angle)) { j.limitState = 1; j.limitBias = j.angle >= j.lo ? (j.angle - j.lo) / dt : BAUMGARTE / dt * (j.angle - j.lo); }
        else if (j.angle > j.hi - margin) { j.limitState = 2; j.limitBias = j.angle <= j.hi ? (j.angle - j.hi) / dt : BAUMGARTE / dt * (j.angle - j.hi); }
        if (j.limitState == 0) j.Plimit = 0;
    }
    // the cone of a cone-twist: how far B's twist axis has swung from A's
    j.swingActive = false;
    if (j.type == ASTRATTO_JOINT_CONE_TWIST && B) {
        const V3 tB = B->R * j.axisB;
        const float c = std::clamp(dot(axA, tB), -1.0f, 1.0f), swing = std::acos(c);
        if ((j.flags & ASTRATTO_JOINT_LIMITED) && swing > j.lo - 0.1f) {
            j.swingActive = true;
            j.swingAxis = norm(cross(axA, tB));
            if (len2(j.swingAxis) < 0.5f) j.swingAxis = j.perpA;
            const float k = dot(j.swingAxis, A.invI * j.swingAxis) + dot(j.swingAxis, B->invI * j.swingAxis);
            j.swingMass = k > 1e-12f ? 1 / k : 0;
            j.swingBias = swing <= j.lo ? (swing - j.lo) / dt : BAUMGARTE / dt * (swing - j.lo);
        } else j.Pswing = 0;
        // twist: measured about B's own twist axis
        const V3 pA2 = norm(perA - tB * dot(perA, tB)), pB2 = B->R * j.perpB;
        j.angle = std::atan2(dot(cross(pA2, pB2), tB), dot(pA2, pB2));
        if ((j.flags & ASTRATTO_JOINT_LIMITED) && std::fabs(j.angle) > j.hi - 0.1f) {
            j.limitState = j.angle > 0 ? 2 : 1;
            const float edge = j.angle > 0 ? j.hi : -j.hi, over = j.angle > 0 ? j.angle - j.hi : -j.hi - j.angle;
            j.limitBias = over <= 0 ? (j.angle - edge) / dt : BAUMGARTE / dt * (j.angle - edge);
            j.ax = tB;
        }
        else j.Plimit = 0;
    }
    // the dampers: each axis of A's frame, with its effective inertia
    for (int k = 0; k < 3; k++) {
        if (j.damp[k] <= 0) { j.Pdamp[k] = 0; continue; }
        const V3 a = A.R.col(k);
        j.dampAxis[k] = a;
        const float kk = dot(a, A.invI * a) + (B ? dot(a, B->invI * a) : 0);
        j.dampMass[k] = kk;
        j.Pdamp[k] = std::clamp(j.Pdamp[k] * 0.8f, std::min(0.0f, j.dampLo[k]) * step_, std::max(0.0f, j.dampHi[k]) * step_);   // warm start
        applyAngular(A, B, a * j.Pdamp[k]);
    }
    // warm start: last step's impulses, applied again
    auto pushLinear = [&](V3 P) {
        if (B) { B->v += P * B->invMass; B->w += B->invI * cross(j.rB, P); }
        A.v -= P * A.invMass; A.w -= A.invI * cross(j.rA, P);
    };
    if (j.type == ASTRATTO_JOINT_SLIDER) {
        applyAngular(A, B, j.Pang);
        pushLinear(j.lin1 * j.Pslide[0] + j.lin2 * j.Pslide[1] + j.ax * (j.Plimit + j.Pmotor));
    } else {
        pushLinear(j.Pp);
        applyAngular(A, B, j.Pang + j.ax * (j.Plimit + j.Pmotor) + (j.swingActive ? j.swingAxis * j.Pswing : V3()));
    }
}

void Space::solveJoint(Joint& j) {
    Body& A = bodies_[static_cast<size_t>(j.a)];
    Body* B = j.b >= 0 ? &bodies_[static_cast<size_t>(j.b)] : nullptr;
    const float inv = 1.0f / step_;
    auto relVel = [&]() { return (B ? B->velocityAt(j.rB) : V3()) - A.velocityAt(j.rA); };
    auto pushLinear = [&](V3 P) {   // + on B, - on A
        if (B) { B->v += P * B->invMass; B->w += B->invI * cross(j.rB, P); }
        A.v -= P * A.invMass; A.w -= A.invI * cross(j.rA, P);
    };
    // ---- the dampers: C' + gamma P = 0, gamma = 1 / (c h): an implicit viscous coupling
    for (int k = 0; k < 3; k++) {
        if (j.damp[k] <= 0) continue;
        const V3 a = j.dampAxis[k];
        const float wr = dot((B ? B->w : V3()) - A.w, a) - j.dampTarget[k];
        const float gamma = 1.0f / (j.damp[k] * step_);
        const float dP = -(wr + gamma * j.Pdamp[k]) / (j.dampMass[k] + gamma);
        const float P0 = j.Pdamp[k];
        j.Pdamp[k] = std::clamp(P0 + dP, std::min(0.0f, j.dampLo[k]) * step_, std::max(0.0f, j.dampHi[k]) * step_);
        applyAngular(A, B, a * (j.Pdamp[k] - P0));
    }
    // ---- angular rows first (motor, limits, locks), then the point
    if (j.type == ASTRATTO_JOINT_HINGE || j.type == ASTRATTO_JOINT_CONE_TWIST) {
        const V3 axis = j.ax;
        // motor or friction about the axis
        if (j.type == ASTRATTO_JOINT_HINGE && (j.motorForce > 0)) {
            const float target = (j.flags & ASTRATTO_JOINT_MOTOR) ? j.motorSpeed : 0.0f;
            const float wr = dot((B ? B->w : V3()) - A.w, axis);
            float dP = -j.motorMass * (wr - target);
            const float P0 = j.Pmotor, maxP = j.motorForce * step_;
            j.Pmotor = std::clamp(P0 + dP, -maxP, maxP);
            applyAngular(A, B, axis * (j.Pmotor - P0));
        }
        if (j.limitState) {
            const float wr = dot((B ? B->w : V3()) - A.w, axis);
            float dP = -j.limitMass * (wr + j.limitBias);
            const float P0 = j.Plimit;
            if (j.limitState == 1) j.Plimit = std::max(0.0f, P0 + dP);
            else if (j.limitState == 2) j.Plimit = std::min(0.0f, P0 + dP);
            else j.Plimit = P0 + dP;
            applyAngular(A, B, axis * (j.Plimit - P0));
        }
        if (j.swingActive && B) {
            const float wr = dot(B->w - A.w, j.swingAxis);
            float dP = -j.swingMass * (wr + j.swingBias);
            const float P0 = j.Pswing;
            j.Pswing = std::min(0.0f, P0 + dP);
            applyAngular(A, B, j.swingAxis * (j.Pswing - P0));
        }
    }
    if (j.type == ASTRATTO_JOINT_HINGE) {
        // the two directions across the axis are held: B's axis stays A's
        const V3 axA = j.ax, axB = B ? B->R * j.axisB : j.axisB;
        V3 t1, t2;
        basis(axA, t1, t2);
        const V3 err = cross(axA, axB);
        for (const V3& t : {t1, t2}) {
            const float k = dot(t, A.invI * t) + (B ? dot(t, B->invI * t) : 0);
            if (k < 1e-12f) continue;
            const float wr = dot((B ? B->w : V3()) - A.w, t);
            const float dP = -(wr + BAUMGARTE * inv * dot(err, t)) / k;
            j.Pang += t * dP;
            applyAngular(A, B, t * dP);
        }
    } else if (j.type == ASTRATTO_JOINT_FIXED || j.type == ASTRATTO_JOINT_SLIDER) {
        // the whole relative orientation is held at what it was
        const Q want = A.q * j.rel0;                 // where B should be turned to (the world's frame is no turn at all)
        const V3 err = rotationVector(want, B ? B->q : Q());
        M3 K = A.invI;
        if (B) K = K + B->invI;
        const M3 Ki = inverse(K);
        const V3 wr = (B ? B->w : V3()) - A.w;
        const V3 dP = Ki * -(wr + err * (BAUMGARTE * inv));
        j.Pang += dP;
        applyAngular(A, B, dP);
    }
    if (j.type == ASTRATTO_JOINT_SLIDER) {
        const V3 axis = j.ax;
        if (j.motorForce > 0) {
            const float target = (j.flags & ASTRATTO_JOINT_MOTOR) ? j.motorSpeed : 0.0f;
            const float vr = dot(relVel(), axis);
            const float P0 = j.Pmotor, maxP = j.motorForce * step_;
            j.Pmotor = std::clamp(P0 - j.motorMass * (vr - target), -maxP, maxP);
            pushLinear(axis * (j.Pmotor - P0));
        }
        if (j.limitState) {
            const float vr = dot(relVel(), axis);
            const float dP = -j.limitMass * (vr + j.limitBias), P0 = j.Plimit;
            if (j.limitState == 1) j.Plimit = std::max(0.0f, P0 + dP);
            else if (j.limitState == 2) j.Plimit = std::min(0.0f, P0 + dP);
            else j.Plimit = P0 + dP;
            pushLinear(axis * (j.Plimit - P0));
        }
        // across the line
        const V3 pA = A.p + j.rA, pB = B ? B->p + j.rB : j.localB;
        const V3 d = pB - pA;
        const V3 lins[2] = {j.lin1, j.lin2};
        for (int k = 0; k < 2; k++) {
            const float vr = dot(relVel(), lins[k]);
            const float dP = -j.slideMass[k] * (vr + BAUMGARTE * inv * dot(d, lins[k]));
            j.Pslide[k] += dP;
            pushLinear(lins[k] * dP);
        }
        return;
    }
    // ---- the point: the two anchors stay together
    const V3 pA = A.p + j.rA, pB = B ? B->p + j.rB : j.localB;
    const V3 C = pB - pA;
    const V3 cdot = relVel();
    const V3 P = j.kPoint * -(cdot + C * (BAUMGARTE * inv));
    j.Pp += P;
    pushLinear(P);
}

// ------------------------------------------------------------------ integration, bullets, sleep
void Space::integrate(float dt, float gravity) {
    for (Body& b : bodies_) {
        if (b.proxy) continue;
        if (b.flags & ASTRATTO_RIGID_KINEMATIC) { b.p += b.v * dt; b.q = impulsis::integrate(b.q, b.w, dt); b.update(); continue; }
        if (!b.dynamic() || !b.awake) continue;
        b.p += b.v * dt;
        b.q = impulsis::integrate(b.q, b.w, dt);
        b.update();
    }
    (void)gravity;
}

void Space::sweepBullets(const VoxelWorld* w, float dt) {
    if (!w) return;
    for (Body& b : bodies_) {
        if (!(b.flags & ASTRATTO_RIGID_BULLET) || !b.dynamic() || !b.awake) continue;
        // a straight cell walk from the last pose to this one: the first solid cell stops it at its face
        const V3 from = b.prevP, to = b.p, d = to - from;
        const float dist = len(d);
        if (dist < 0.25f) continue;
        const V3 dir = d / dist;
        int x = static_cast<int>(std::floor(from.x)), y = static_cast<int>(std::floor(from.y)), z = static_cast<int>(std::floor(from.z));
        const int sx = dir.x > 0 ? 1 : -1, sy = dir.y > 0 ? 1 : -1, sz = dir.z > 0 ? 1 : -1;
        const float tdx = std::fabs(dir.x) < 1e-9f ? 1e30f : std::fabs(1 / dir.x), tdy = std::fabs(dir.y) < 1e-9f ? 1e30f : std::fabs(1 / dir.y), tdz = std::fabs(dir.z) < 1e-9f ? 1e30f : std::fabs(1 / dir.z);
        float tmx = std::fabs(dir.x) < 1e-9f ? 1e30f : (dir.x > 0 ? (x + 1 - from.x) : (from.x - x)) * tdx;
        float tmy = std::fabs(dir.y) < 1e-9f ? 1e30f : (dir.y > 0 ? (y + 1 - from.y) : (from.y - y)) * tdy;
        float tmz = std::fabs(dir.z) < 1e-9f ? 1e30f : (dir.z > 0 ? (z + 1 - from.z) : (from.z - z)) * tdz;
        float t = 0; int axis = -1;
        for (int i = 0; i < 64 && t <= dist; i++) {
            if (i > 0 && w->solid(x, y, z)) {
                const float back = std::max(0.0f, t - b.shape.boundRadius - 0.01f);
                b.p = from + dir * back;
                V3 n; n[axis] = axis == 0 ? -static_cast<float>(sx) : axis == 1 ? -static_cast<float>(sy) : -static_cast<float>(sz);
                const float vn = dot(b.v, n);
                if (vn < 0) b.v -= n * (vn * (1 + b.restitution));
                b.v *= 0.6f;
                b.update();
                break;
            }
            if (tmx < tmy && tmx < tmz) { t = tmx; tmx += tdx; x += sx; axis = 0; }
            else if (tmy < tmz) { t = tmy; tmy += tdy; y += sy; axis = 1; }
            else { t = tmz; tmz += tdz; z += sz; axis = 2; }
        }
    }
    (void)dt;
}

void Space::sleep(float dt) {
    // an island sleeps only when every body in it has been still long enough
    std::unordered_map<int, float> least;
    for (Body& b : bodies_) {
        if (!b.dynamic() || b.proxy) continue;
        if ((b.flags & ASTRATTO_RIGID_NO_SLEEP) || len2(b.v) > SLEEP_LINEAR * SLEEP_LINEAR || len2(b.w) > SLEEP_ANGULAR * SLEEP_ANGULAR) b.sleepTime = 0;
        else b.sleepTime += dt;
        auto it = least.find(b.island);
        if (it == least.end()) least[b.island] = b.sleepTime; else it->second = std::min(it->second, b.sleepTime);
    }
    for (Body& b : bodies_) {
        if (!b.dynamic() || b.proxy || !b.awake) continue;
        if (least[b.island] >= SLEEP_TIME) { b.awake = false; b.v = V3(); b.w = V3(); slept_.push_back(b.handle); }
    }
}

void Space::step(const VoxelWorld* w, float gravity) {
    const float dt = step_;
    // forces: gravity and damping (velocities first, positions after: semi-implicit Euler)
    for (Body& b : bodies_) {
        if (!b.dynamic() || !b.awake || b.proxy) continue;
        if (b.gravityScale >= 0) b.v.y -= gravity * b.gravityScale * dt;
        b.v *= 1.0f / (1.0f + dt * b.linDamp);
        b.w *= 1.0f / (1.0f + dt * b.angDamp);
        // a spin no body could have (a solver blow-up) is cut back, rather than let it run away
        const float w2 = len2(b.w);
        if (w2 > 3600.0f) b.w *= 60.0f / std::sqrt(w2);
    }
    for (Body& b : bodies_) if (b.proxy) b.v = b.drive;
    broadphase();
    narrowphase(w);
    islands();
    prepare(dt);
    for (Joint& j : joints_) if (!j.broken) prepareJoint(j, dt);
    warmStart();
    for (int it = 0; it < iterations_; it++) {
        for (Joint& j : joints_) if (!j.broken) solveJoint(j);
        solveVelocities();
    }
    for (Body& b : bodies_) if (b.proxy) { b.push += b.v - b.drive; b.pushSteps++; }
    // what the world's cells hold each body up with, and where
    for (Body& b : bodies_) { b.support = 0; b.cop = V3(); b.groundPoints = 0; }
    for (const Manifold& m : manifolds_) {
        if (m.b >= 0) continue;
        Body& A = bodies_[static_cast<size_t>(m.a)];
        for (int i = 0; i < m.count; i++) {
            const Contact& c = m.pts[i];
            if (c.n.y < 0.5f || c.Pn <= 0) continue;
            const float f = c.Pn * c.n.y / dt;
            A.support += f; A.cop += (A.p + c.rA) * f; A.groundPoints++;
        }
    }
    for (Body& b : bodies_) if (b.support > 0) b.cop = b.cop / b.support;
    // joints that carried more than they could
    for (Joint& j : joints_) {
        if (j.broken) continue;
        j.force = (len(j.Pp) + std::fabs(j.Plimit) + len(j.Pang)) / dt;
        if (j.breakForce > 0 && j.force > j.breakForce) { j.broken = true; forget(j); broken_.push_back(j.handle); if (j.a >= 0) bodies_[static_cast<size_t>(j.a)].wake(); if (j.b >= 0) bodies_[static_cast<size_t>(j.b)].wake(); }
    }
    integrate(dt, gravity);
    sweepBullets(w, dt);
    sleep(dt);
    // impacts: the first step two things touch, at speed; and the manifolds kept for warm starting
    for (Manifold& m : manifolds_) {
        float speed = 0, pn = 0; int bi = -1;
        for (int i = 0; i < m.count; i++) { pn += m.pts[i].Pn; if (bi < 0 || m.pts[i].approach > speed) { speed = std::max(speed, m.pts[i].approach); bi = i; } }
        contactTotal_ += static_cast<uint64_t>(m.count);
        const Body& A = bodies_[static_cast<size_t>(m.a)];
        const Body* B = m.b >= 0 ? &bodies_[static_cast<size_t>(m.b)] : nullptr;
        const bool report = (A.flags & ASTRATTO_RIGID_REPORT) || (B && (B->flags & ASTRATTO_RIGID_REPORT));
        if (bi >= 0 && m.fresh && report && speed > 0.6f && impacts_.size() < 256) {
            Impact im;
            im.a = A.proxy ? (B ? B->handle : 0) : A.handle;
            im.b = A.proxy ? 0 : (B && !B->proxy ? B->handle : 0);
            im.other = A.proxy ? A.mover : (B && B->proxy ? B->mover : 0);
            im.p = A.p + m.pts[bi].rA; im.n = m.pts[bi].n; im.speed = speed; im.impulse = pn;
            im.ma = A.material; im.mb = B ? B->material : static_cast<uint32_t>(m.block);
            impacts_.push_back(im);
        }
        previous_[m.key] = m;
    }
}

}  // namespace impulsis
}  // namespace astratto
