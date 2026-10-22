// Astratto Engine - Sydney 3's body: thirteen rigid segments and twelve joints in an Impulsis space of their own, made
// in the rest pose (standing, facing -z, feet on the floor), placed from an animated pose, stepped, and read back as the
// fifteen joint points of the API and the segments' own orientations.
#include "physics/ragdoll.h"

#include <algorithm>
#include <cmath>

namespace astratto {

using namespace impulsis;
using namespace syd;

namespace rd {
// the API's points weighed as the segments that carry them (the share of a 78 kg body each, as it has always been)
const float MASS[N] = {5, 18, 14, 3, 2, 1.2f, 3, 2, 1.2f, 7, 4.5f, 3, 7, 4.5f, 3};
}

namespace {

// ---- the rest pose, metres at scale 1 (1.8 m tall): standing, facing -z, its left at -x, its feet on y = 0
constexpr float ANKLE_Y = 0.075f, KNEE_Y = 0.4125f, HIP_Y = 0.75f, SPINE_Y = 0.86f, NECK_Y = 1.425f, SHOULDER_Y = 1.369f, ELBOW_Y = 1.088f, WRIST_Y = 0.806f;
constexpr float HIP_X = 0.1125f, SHOULDER_X = 0.3375f;

struct PartDef { uint32_t shape; V3 c; V3 half; float r, hh, mass, friction; };
const PartDef PART[PARTS] = {
    {ASTRATTO_SHAPE_BOX, {0, 0.80f, 0}, {0.16f, 0.09f, 0.10f}, 0, 0, 11.0f, 0.6f},                // pelvis
    {ASTRATTO_SHAPE_BOX, {0, 1.14f, 0}, {0.17f, 0.27f, 0.10f}, 0, 0, 26.0f, 0.6f},                // torso
    {ASTRATTO_SHAPE_CAPSULE, {0, 1.62f, 0}, {}, 0.11f, 0.06f, 5.0f, 0.6f},                        // head
    {ASTRATTO_SHAPE_CAPSULE, {-SHOULDER_X, 1.2285f, 0}, {}, 0.05f, 0.09f, 2.0f, 0.6f},            // left upper arm
    {ASTRATTO_SHAPE_CAPSULE, {-SHOULDER_X, 0.897f, 0}, {}, 0.045f, 0.146f, 1.6f, 0.8f},           // left forearm and hand
    {ASTRATTO_SHAPE_CAPSULE, {SHOULDER_X, 1.2285f, 0}, {}, 0.05f, 0.09f, 2.0f, 0.6f},
    {ASTRATTO_SHAPE_CAPSULE, {SHOULDER_X, 0.897f, 0}, {}, 0.045f, 0.146f, 1.6f, 0.8f},
    {ASTRATTO_SHAPE_CAPSULE, {-HIP_X, 0.58125f, 0}, {}, 0.075f, 0.094f, 8.5f, 0.6f},              // left thigh
    {ASTRATTO_SHAPE_CAPSULE, {-HIP_X, 0.24375f, 0}, {}, 0.055f, 0.114f, 3.6f, 0.6f},              // left shin
    {ASTRATTO_SHAPE_BOX, {-HIP_X, 0.035f, -0.045f}, {0.045f, 0.035f, 0.125f}, 0, 0, 1.1f, 1.0f},  // left foot
    {ASTRATTO_SHAPE_CAPSULE, {HIP_X, 0.58125f, 0}, {}, 0.075f, 0.094f, 8.5f, 0.6f},
    {ASTRATTO_SHAPE_CAPSULE, {HIP_X, 0.24375f, 0}, {}, 0.055f, 0.114f, 3.6f, 0.6f},
    {ASTRATTO_SHAPE_BOX, {HIP_X, 0.035f, -0.045f}, {0.045f, 0.035f, 0.125f}, 0, 0, 1.1f, 1.0f}};

struct JointDef { int parent, child; V3 at; uint32_t type; V3 axis, twist; float swing, twistLimit, lo, hi; };
// cone-twist joints: their cone about axis (the parent's), their twist about the child's long axis; hinges about +x
// (+ turns the child's lower end forward: an elbow bending, a knee straightening, toes rising)
const JointDef JOINT[JOINTS] = {
    {PELVIS, TORSO, {0, SPINE_Y, 0}, ASTRATTO_JOINT_CONE_TWIST, {0, 1, 0}, {0, 1, 0}, 0.75f, 0.6f, 0, 0},
    {TORSO, HEAD, {0, NECK_Y, 0}, ASTRATTO_JOINT_CONE_TWIST, {0, 1, 0}, {0, 1, 0}, 0.9f, 1.2f, 0, 0},
    {TORSO, L_UPPER, {-SHOULDER_X, SHOULDER_Y, 0}, ASTRATTO_JOINT_CONE_TWIST, {-0.5f, -0.486f, -0.717f}, {0, -1, 0}, 2.1f, 1.3f, 0, 0},
    {L_UPPER, L_FORE, {-SHOULDER_X, ELBOW_Y, 0}, ASTRATTO_JOINT_HINGE, {1, 0, 0}, {}, 0, 0, -0.05f, 2.5f},
    {TORSO, R_UPPER, {SHOULDER_X, SHOULDER_Y, 0}, ASTRATTO_JOINT_CONE_TWIST, {0.5f, -0.486f, -0.717f}, {0, -1, 0}, 2.1f, 1.3f, 0, 0},
    {R_UPPER, R_FORE, {SHOULDER_X, ELBOW_Y, 0}, ASTRATTO_JOINT_HINGE, {1, 0, 0}, {}, 0, 0, -0.05f, 2.5f},
    {PELVIS, L_THIGH, {-HIP_X, HIP_Y, 0}, ASTRATTO_JOINT_CONE_TWIST, {-0.114f, -0.757f, -0.644f}, {0, -1, 0}, 1.35f, 0.7f, 0, 0},
    {L_THIGH, L_SHIN, {-HIP_X, KNEE_Y, 0}, ASTRATTO_JOINT_HINGE, {1, 0, 0}, {}, 0, 0, -2.4f, 0.03f},
    {L_SHIN, L_FOOT, {-HIP_X, ANKLE_Y, 0}, ASTRATTO_JOINT_CONE_TWIST, {0, -1, 0}, {0, -1, 0}, 0.8f, 0.3f, 0, 0},   // the ankle and the subtalar joint together
    {PELVIS, R_THIGH, {HIP_X, HIP_Y, 0}, ASTRATTO_JOINT_CONE_TWIST, {0.114f, -0.757f, -0.644f}, {0, -1, 0}, 1.35f, 0.7f, 0, 0},
    {R_THIGH, R_SHIN, {HIP_X, KNEE_Y, 0}, ASTRATTO_JOINT_HINGE, {1, 0, 0}, {}, 0, 0, -2.4f, 0.03f},
    {R_SHIN, R_FOOT, {HIP_X, ANKLE_Y, 0}, ASTRATTO_JOINT_CONE_TWIST, {0, -1, 0}, {0, -1, 0}, 0.8f, 0.3f, 0, 0}};

// the API's fifteen points, each on a segment (rest positions, world, scale 1)
const int POINT_PART[rd::N] = {HEAD, TORSO, PELVIS, L_UPPER, L_FORE, L_FORE, R_UPPER, R_FORE, R_FORE, L_THIGH, L_SHIN, L_FOOT, R_THIGH, R_SHIN, R_FOOT};
const V3 POINT_REST[rd::N] = {
    {0, 1.70f, 0}, {0, NECK_Y, 0}, {0, HIP_Y, 0},
    {-SHOULDER_X, SHOULDER_Y, 0}, {-SHOULDER_X, ELBOW_Y, 0}, {-SHOULDER_X, WRIST_Y, 0},
    {SHOULDER_X, SHOULDER_Y, 0}, {SHOULDER_X, ELBOW_Y, 0}, {SHOULDER_X, WRIST_Y, 0},
    {-HIP_X, HIP_Y, 0}, {-HIP_X, KNEE_Y, 0}, {-HIP_X, ANKLE_Y, 0},
    {HIP_X, HIP_Y, 0}, {HIP_X, KNEE_Y, 0}, {HIP_X, ANKLE_Y, 0}};

inline int fl(float v) { return static_cast<int>(std::floor(v)); }

/** The rotation whose columns are x, y, z (orthonormal, right-handed). */
Q fromBasis(V3 x, V3 y, V3 z) {
    const float m00 = x.x, m11 = y.y, m22 = z.z, tr = m00 + m11 + m22;
    Q q;
    if (tr > 0) {
        const float s = std::sqrt(tr + 1) * 2;
        q = Q(0.25f * s, (y.z - z.y) / s, (z.x - x.z) / s, (x.y - y.x) / s);
    } else if (m00 > m11 && m00 > m22) {
        const float s = std::sqrt(1 + m00 - m11 - m22) * 2;
        q = Q((y.z - z.y) / s, 0.25f * s, (y.x + x.y) / s, (z.x + x.z) / s);
    } else if (m11 > m22) {
        const float s = std::sqrt(1 + m11 - m00 - m22) * 2;
        q = Q((z.x - x.z) / s, (y.x + x.y) / s, 0.25f * s, (z.y + y.z) / s);
    } else {
        const float s = std::sqrt(1 + m22 - m00 - m11) * 2;
        q = Q((x.y - y.x) / s, (z.x + x.z) / s, (z.y + y.z) / s, 0.25f * s);
    }
    return qnorm(q);
}

/** A frame whose y is up and whose x is as near right as it can be. */
Q frameUpRight(V3 up, V3 right) {
    V3 y = norm(up);
    V3 x = right - y * dot(right, y);
    if (len2(x) < 1e-6f) { x = cross(y, V3(0, 0, 1)); if (len2(x) < 1e-6f) x = V3(1, 0, 0); }
    x = norm(x);
    return fromBasis(x, y, cross(x, y));
}

}  // namespace

// ------------------------------------------------------------------ the skeleton
int Ragdoll::jointParent(int j) { return JOINT[j].parent; }
int Ragdoll::jointChild(int j) { return JOINT[j].child; }
int Ragdoll::jointDof(int j) { return JOINT[j].type == ASTRATTO_JOINT_HINGE ? 1 : 3; }
int Ragdoll::pointPart(int p) { return POINT_PART[p]; }

Ragdoll::Ragdoll(float s) : scale(s) {
    unit = rd::VOX * s;
    space.setRate(syd::STEP, ITERATIONS);
    build();
}

void Ragdoll::build() {
    const float s = scale, m3 = s * s * s;
    totalMass = 0;
    for (int p = 0; p < PARTS; p++) {
        const PartDef& d = PART[p];
        Body b;
        b.handle = static_cast<astratto_handle>(p + 1);
        b.shape.type = d.shape;
        b.shape.half = d.half * s; b.shape.radius = d.r * s; b.shape.halfHeight = d.hh * s;
        b.mass = d.mass * m3;
        b.p = d.c * s;
        b.friction = d.friction;
        b.restitution = 0;
        b.linDamp = 0.02f; b.angDamp = 0.25f;
        b.flags = ASTRATTO_RIGID_REPORT;
        part[p] = space.add(b);
        partMass[p] = b.mass;
        totalMass += b.mass;
    }
    for (int j = 0; j < JOINTS; j++) {
        const JointDef& d = JOINT[j];
        impulsis::Joint jt;
        jt.handle = static_cast<astratto_handle>(100 + j);
        jt.type = d.type;
        jt.flags = ASTRATTO_JOINT_LIMITED;
        jt.a = part[d.parent]; jt.b = part[d.child];
        jt.localA = d.at * s;
        jt.axisA = norm(d.axis);
        if (d.type == ASTRATTO_JOINT_HINGE) { jt.lo = d.lo; jt.hi = d.hi; }
        else { jt.lo = d.swing; jt.hi = d.twistLimit; }
        space.addJoint(jt);
        jointHandle[j] = jt.handle;
        if (d.type == ASTRATTO_JOINT_CONE_TWIST) {
            // the twist is about the child's own long axis, whatever way the cone of its swing is turned
            impulsis::Joint* made = space.joint(jt.handle);
            const V3 tw = norm(d.twist);
            const V3 perA = made->perpA;                   // the bodies are unturned: their frames are the world's
            made->axisB = tw;
            made->perpB = norm(perA - tw * dot(perA, tw));
        }
    }
}

V3 Ragdoll::jointPoint(int j) const {
    const Body& c = body(JOINT[j].child);
    return c.toWorld((JOINT[j].at - PART[JOINT[j].child].c) * scale);
}

V3 Ragdoll::pointWorld(int i) const {
    const int p = POINT_PART[i];
    return body(p).toWorld((POINT_REST[i] - PART[p].c) * scale);
}

void Ragdoll::place(const V3* pos, const Q* rot, V3 v) {
    for (int p = 0; p < PARTS; p++) {
        Body& b = body(p);
        b.p = pos[p]; b.q = qnorm(rot[p]); b.v = v; b.w = V3();
        b.update();
        b.prevP = b.p; b.prevQ = b.q;
        b.wake();
    }
    age = 0; quiet = 0; settled = false; acc_ = 0;
    publish(1);
}

void Ragdoll::fromPose(const float* pt, float vx, float vy, float vz) {
    auto P = [&](int i) { return V3(pt[i * 3], pt[i * 3 + 1], pt[i * 3 + 2]); };
    const V3 right = P(rd::RS) - P(rd::LS), hips = P(rd::RH) - P(rd::LH);
    Q rot[PARTS];
    rot[TORSO] = frameUpRight(P(rd::NECK) - P(rd::PELVIS), right);
    rot[PELVIS] = frameUpRight(P(rd::NECK) - P(rd::PELVIS), len2(hips) > 1e-6f ? hips : right);
    rot[HEAD] = frameUpRight(P(rd::HEAD_P) - P(rd::NECK), right);
    rot[L_UPPER] = frameUpRight(P(rd::LS) - P(rd::LE), right);
    rot[L_FORE] = frameUpRight(P(rd::LE) - P(rd::LW), right);
    rot[R_UPPER] = frameUpRight(P(rd::RS) - P(rd::RE), right);
    rot[R_FORE] = frameUpRight(P(rd::RE) - P(rd::RW), right);
    rot[L_THIGH] = frameUpRight(P(rd::LH) - P(rd::LK), hips);
    rot[L_SHIN] = frameUpRight(P(rd::LK) - P(rd::LA), hips);
    rot[R_THIGH] = frameUpRight(P(rd::RH) - P(rd::RK), hips);
    rot[R_SHIN] = frameUpRight(P(rd::RK) - P(rd::RA), hips);
    // the feet lie flat, pointing the way the body faces
    const V3 tz = toM3(rot[TORSO]).col(2);
    V3 back(tz.x, 0, tz.z);
    if (len2(back) < 1e-6f) back = V3(0, 0, 1);
    back = norm(back);
    rot[L_FOOT] = rot[R_FOOT] = fromBasis(cross(V3(0, 1, 0), back), V3(0, 1, 0), back);
    // the segments laid down the tree from the pelvis, each hung from its parent's joint: every joint starts whole
    V3 pos[PARTS];
    const float s = scale;
    pos[PELVIS] = P(rd::PELVIS) - rotate(rot[PELVIS], (POINT_REST[rd::PELVIS] - PART[PELVIS].c) * s);
    for (int j = 0; j < JOINTS; j++) {
        const JointDef& d = JOINT[j];
        const V3 at = pos[d.parent] + rotate(rot[d.parent], (d.at - PART[d.parent].c) * s);
        pos[d.child] = at - rotate(rot[d.child], (d.at - PART[d.child].c) * s);
    }
    place(pos, rot, V3(vx, vy, vz));
}

void Ragdoll::standAt(float wx, float wy, float wz, float yaw) {
    const Q turn = axisAngle(V3(0, 1, 0), yaw);
    V3 pos[PARTS];
    Q rot[PARTS];
    for (int p = 0; p < PARTS; p++) { pos[p] = V3(wx, wy + 0.004f, wz) + rotate(turn, PART[p].c * scale); rot[p] = turn; }
    place(pos, rot, V3());
    age = 0.5f;
}

void Ragdoll::shift(float dx, float dy, float dz) {
    const V3 d(dx, dy, dz);
    for (int p = 0; p < PARTS; p++) { Body& b = body(p); b.p += d; b.prevP += d; b.update(); }
    publish(1);
}

float Ragdoll::unbury(const VoxelWorld* w) {
    if (w == nullptr) return 0;
    float lift = 0;
    for (int p = 0; p < PARTS; p++) {
        const Body& b = body(p);
        const float r = b.shape.type == ASTRATTO_SHAPE_BOX ? b.shape.half.y : b.shape.radius;
        const int bx = fl(b.p.x), by = fl(b.p.y - r * 0.6f), bz = fl(b.p.z);
        if (!w->solid(bx, by, bz)) continue;
        int top = by;
        if (w->solid(bx, top + 1, bz)) top++;
        if (w->solid(bx, top + 1, bz)) continue;
        lift = std::max(lift, static_cast<float>(top + 1) + r + 0.01f - b.p.y);
    }
    lift = std::min(lift, 1.35f);
    if (lift > 0) shift(0, lift, 0);
    return lift;
}

void Ragdoll::kickAll(float vx, float vy, float vz) {
    const V3 d(vx, vy, vz);
    for (int p = 0; p < PARTS; p++) { Body& b = body(p); b.v += d; b.wake(); }
    if (settled) wake();
}

void Ragdoll::strike(int seg, V3 point, V3 J, float maxDv) {
    Body& b = body(seg);
    const float dv = len(J) / b.mass;
    V3 here = J, rest;
    if (dv > maxDv) { here = J * (maxDv / dv); rest = J - here; }
    space.impulseAt(part[seg], point, here);
    if (len2(rest) > 0) {
        const float mt = partMass[TORSO] + partMass[PELVIS];
        body(TORSO).v += rest * (1.0f / mt);
        body(PELVIS).v += rest * (1.0f / mt);
    }
    for (int p = 0; p < PARTS; p++) body(p).wake();
    if (settled) wake();
}

void Ragdoll::impulse(int region, float vx, float vy, float vz) {
    static const int head[] = {HEAD, TORSO}, arms[] = {L_UPPER, R_UPPER, L_FORE, R_FORE}, legs[] = {L_THIGH, R_THIGH, L_SHIN, R_SHIN}, torso[] = {TORSO, PELVIS};
    const int* hit = torso;
    int n = 2;
    if (region == ASTRATTO_REGION_HEAD) { hit = head; n = 2; }
    else if (region == ASTRATTO_REGION_ARMS) { hit = arms; n = 4; }
    else if (region == ASTRATTO_REGION_LEGS) { hit = legs; n = 4; }
    const V3 v(vx, vy, vz);
    for (int p = 0; p < PARTS; p++) body(p).v += v * 0.35f;
    for (int k = 0; k < n; k++) body(hit[k]).v += v * 0.65f;
    for (int p = 0; p < PARTS; p++) body(p).wake();
    if (settled) wake();
}

void Ragdoll::carry(float vx, float vy, float vz, float share) {
    for (int p = 0; p < PARTS; p++) {
        Body& b = body(p);
        b.v.x += (vx - b.v.x) * share;
        b.v.y += std::max(0.0f, vy - b.v.y) * share;
        b.v.z += (vz - b.v.z) * share;
        b.wake();
    }
    age = std::min(age, 0.6f); quiet = 0; settled = false;
}

void Ragdoll::wake() {
    settled = false; quiet = 0; age = std::min(age, 1.2f);
    for (int p = 0; p < PARTS; p++) body(p).wake();
}

int Ragdoll::disturb(float wx, float wy, float wz, float radius, float vx, float vy, float vz) {
    int n = 0;
    const float mean = totalMass / PARTS;
    for (int p = 0; p < PARTS; p++) {
        Body& b = body(p);
        const float dd = len(b.p - V3(wx, wy, wz));
        if (dd > radius) continue;
        const float k = (1 - dd / radius) * std::max(0.6f, std::min(2.2f, mean / b.mass));
        b.v += V3(vx, vy, vz) * k;
        n++;
    }
    if (n > 0) wake();
    return n;
}

int Ragdoll::update(const VoxelWorld* w, float dt) {
    if (settled) return 0;
    acc_ += std::min(dt, 0.1f);
    int n = 0;
    const float h = space.stepLength();
    while (acc_ >= h && !settled) {
        acc_ -= h;
        if (driver != nullptr) driver->drive(w, h);
        if (live) for (int p = 0; p < PARTS; p++) { Body& b = body(p); b.awake = true; b.sleepTime = 0; }
        space.tick(w, gravity);
        age += h;
        n++;
        // the parts that struck the world hard, for the caller's sounds and harm
        for (const Impact& im : space.impacts()) {
            if (im.speed < 2.2f || im.b != 0) continue;
            int seg = -1;
            for (int p = 0; p < PARTS; p++) if (body(p).handle == im.a) seg = p;
            if (seg < 0) continue;
            astratto_evt_ragdoll_impact ev{};
            int best = rd::PELVIS; float bd = 1e30f;
            for (int i = 0; i < rd::N; i++) { if (POINT_PART[i] != seg) continue; const float d = len2(pointWorld(i) - im.p); if (d < bd) { bd = d; best = i; } }
            ev.point = static_cast<uint32_t>(best); ev.speed = im.speed; ev.impulse = im.impulse; ev.x = im.p.x; ev.y = im.p.y; ev.z = im.p.z; ev.block = im.mb;
            if (impacts.size() < 32) impacts.push_back(ev);
        }
        space.impacts().clear();
        space.fellAsleep().clear();
        space.brokenJoints().clear();
        // a limp body comes to rest when every segment has gone to sleep
        float moved = 0;
        bool asleep = true;
        for (int p = 0; p < PARTS; p++) { const Body& b = body(p); moved = std::max(moved, len(b.v)); if (b.awake) asleep = false; }
        quiet = moved < 0.22f ? quiet + h : 0;
        if (!live && (asleep || age >= rd::ACTIVE_TIME * 3 || (quiet > 0.9f && age > 0.6f))) {
            settled = true;
            for (int p = 0; p < PARTS; p++) { Body& b = body(p); b.v = V3(); b.w = V3(); b.awake = false; b.prevP = b.p; b.prevQ = b.q; }
        }
    }
    publish(settled ? 1.0f : acc_ / h);
    return n;
}

void Ragdoll::publish(float alpha) {
    alpha = std::max(0.0f, std::min(1.0f, alpha));
    Q q[PARTS];
    V3 c[PARTS];
    for (int p = 0; p < PARTS; p++) {
        const Body& b = body(p);
        q[p] = slerp(b.prevQ, b.q, alpha);
        c[p] = b.prevP + (b.p - b.prevP) * alpha;
        segQ[p * 4] = q[p].w; segQ[p * 4 + 1] = q[p].x; segQ[p * 4 + 2] = q[p].y; segQ[p * 4 + 3] = q[p].z;
        segC[p * 3] = c[p].x; segC[p * 3 + 1] = c[p].y; segC[p * 3 + 2] = c[p].z;
    }
    for (int i = 0; i < rd::N; i++) {
        const int p = POINT_PART[i];
        const V3 w = c[p] + rotate(q[p], (POINT_REST[i] - PART[p].c) * scale);
        x[i] = w.x; y[i] = w.y; z[i] = w.z;
        grounded[i] = body(p).support > 0 || body(p).groundPoints > 0;
    }
    forward();
}

void Ragdoll::segment(int p, float* q, float* c) const {
    for (int k = 0; k < 4; k++) q[k] = segQ[p * 4 + k];
    for (int k = 0; k < 3; k++) c[k] = segC[p * 3 + k];
}

V3 Ragdoll::com() const {
    V3 m;
    for (int p = 0; p < PARTS; p++) m += body(p).p * partMass[p];
    return m / totalMass;
}

V3 Ragdoll::comVelocity() const {
    V3 m;
    for (int p = 0; p < PARTS; p++) m += body(p).v * partMass[p];
    return m / totalMass;
}

void Ragdoll::forward() {
    const float rx = x[rd::RS] - x[rd::LS], ry = y[rd::RS] - y[rd::LS], rz = z[rd::RS] - z[rd::LS];
    const float ux = x[rd::NECK] - x[rd::PELVIS], uy = y[rd::NECK] - y[rd::PELVIS], uz = z[rd::NECK] - z[rd::PELVIS];
    fwd[0] = uy * rz - uz * ry; fwd[1] = uz * rx - ux * rz; fwd[2] = ux * ry - uy * rx;
    const float l = std::sqrt(fwd[0] * fwd[0] + fwd[1] * fwd[1] + fwd[2] * fwd[2]);
    if (l < 1e-6f) { fwd[0] = 0; fwd[1] = 0; fwd[2] = -1; return; }
    fwd[0] /= l; fwd[1] /= l; fwd[2] /= l;
}

float Ragdoll::yaw() { forward(); return std::atan2(-fwd[0], -fwd[2]); }

float Ragdoll::lowestY() const {
    float m = 3.4e38f;
    for (int p = 0; p < PARTS; p++) {
        const Body& b = body(p);
        const float r = b.shape.type == ASTRATTO_SHAPE_BOX ? len(b.shape.half) : b.shape.radius + b.shape.halfHeight;
        m = std::min(m, b.p.y - r);
    }
    return m;
}

}  // namespace astratto
