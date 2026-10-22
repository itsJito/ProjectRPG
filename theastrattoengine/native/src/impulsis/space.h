// Astratto Engine - Impulsis: the rigid-body world. Bodies with shapes, the contacts between them and with the voxel
// world, the joints that couple them, and the fixed step that moves them: broadphase, narrowphase, islands, sequential
// impulses, integration, sleep.
#pragma once

#include <algorithm>
#include <cstdint>
#include <unordered_map>
#include <unordered_set>
#include <vector>

#include "astratto/astratto.h"
#include "impulsis/imath.h"
#include "world/voxel_world.h"

namespace astratto {

class JobPool;

namespace impulsis {

constexpr float STEP = 1.0f / 120.0f;
constexpr int VELOCITY_ITERATIONS = 10;
constexpr float BAUMGARTE = 0.2f;              // the share of penetration put right each step
constexpr float SLOP = 0.005f;                 // penetration let be (m), so resting contacts do not chatter
constexpr float RESTITUTION_THRESHOLD = 1.0f;  // slower than this (m/s) nothing bounces
constexpr float SPECULATIVE = 0.04f;           // contacts are made this far apart already (m), so fast bodies do not pass
constexpr float SLEEP_LINEAR = 0.05f, SLEEP_ANGULAR = 0.08f, SLEEP_TIME = 0.6f;

struct Shape {
    uint32_t type = ASTRATTO_SHAPE_BOX;
    V3 half{0.5f, 0.5f, 0.5f};                 // BOX
    float radius = 0.5f, halfHeight = 0;       // SPHERE, CAPSULE (the segment runs +-halfHeight along local y)
    std::vector<V3> hull;                      // HULL
    float boundRadius = 0;                     // of the whole shape about the body's origin

    /** The point of the shape furthest along a direction, in the body's frame (support mapping for GJK). */
    V3 support(V3 d) const;
    /** Mass properties for a mass: the diagonal of the inertia tensor in the body's frame. */
    V3 inertia(float mass) const;
    void finish();
};

struct Body {
    astratto_handle handle = 0;
    Shape shape;
    uint32_t flags = 0, group = 0, mask = 0, material = 0;
    V3 p, v, w;
    Q q;
    float invMass = 0, mass = 0;
    V3 invInertiaLocal;
    M3 R, invI;                                // world rotation and world inverse inertia
    float friction = 0.6f, restitution = 0, linDamp = 0.02f, angDamp = 0.06f, gravityScale = 1;
    V3 prevP;
    Q prevQ;
    bool awake = true;
    float sleepTime = 0;
    int island = -1, contacts = 0;
    Aabb box;
    bool hasTarget = false;
    V3 targetP;
    // a character's capsule stand-in (a mover): solved with a mass, but placed and moved by the character controller
    bool proxy = false;
    astratto_handle mover = 0;
    V3 drive;                                  // the velocity its character moves at this frame
    V3 push;                                   // how much the solver changed that velocity, summed over the frame's steps
    int pushSteps = 0;
    // what holds it up, measured each step: the force of the world's cells pressing on it from below (N), where that
    // force acts (the centre of pressure, world), and on how many points
    float support = 0;
    V3 cop;
    int groundPoints = 0;

    /** Rolling resistance (m): the reach of a round body's contact patch. Boxes and hulls have none. */
    float rolling() const { return shape.type == ASTRATTO_SHAPE_SPHERE ? shape.radius * 0.04f : shape.type == ASTRATTO_SHAPE_CAPSULE ? shape.radius * 0.06f : 0.0f; }
    bool dynamic() const { return invMass > 0 && !(flags & (ASTRATTO_RIGID_KINEMATIC | ASTRATTO_RIGID_STATIC)); }
    bool moves() const { return !(flags & ASTRATTO_RIGID_STATIC); }
    void update() { R = toM3(q); invI = R * M3::diag(invInertiaLocal) * transpose(R); if (!dynamic()) invI = M3::zero(); }
    V3 toWorld(V3 local) const { return p + R * local; }
    V3 toLocal(V3 world) const { return transpose(R) * (world - p); }
    V3 velocityAt(V3 r) const { return v + cross(w, r); }
    void wake() { awake = true; sleepTime = 0; }
};

/** A point of contact, solved as one normal row and two friction rows. */
struct Contact {
    V3 localA, localB;                         // the point on each body, in its own frame (for warm starting)
    V3 rA, rB, n, t1, t2;
    float pen = 0;                             // > 0: overlapping
    float Pn = 0, Pt1 = 0, Pt2 = 0;            // accumulated impulses
    float mN = 0, mT1 = 0, mT2 = 0, bias = 0, bounce = 0, approach = 0;
    uint32_t feature = 0;
};

/** The contacts between two bodies (b < 0: a cell of the world, at cell). */
struct Manifold {
    int a = -1, b = -1;
    int cx = 0, cy = 0, cz = 0, block = 0;
    int count = 0;
    Contact pts[4];
    float friction = 0.6f, restitution = 0, rolling = 0;
    V3 Proll;                                  // accumulated rolling-resistance impulse (angular)
    bool fresh = true;                         // first step it exists: impacts are reported once
    uint64_t key = 0;
};

struct Joint {
    astratto_handle handle = 0;
    uint32_t type = ASTRATTO_JOINT_BALL, flags = 0;
    int a = -1, b = -1;                        // body indices; b < 0: the world
    astratto_handle ha = 0, hb = 0;
    V3 localA, localB;                         // anchors in each body's frame (the world frame for b < 0)
    V3 axisA, axisB;                           // the joint axis in each frame
    V3 perpA, perpB;                           // a reference perpendicular in each frame (angles are measured from it)
    Q rel0;                                    // relative orientation when it was made (fixed, cone-twist)
    float lo = 0, hi = 0, motorSpeed = 0, motorForce = 0, breakForce = 0;
    bool broken = false;
    // solver state
    V3 rA, rB, Pp, Pang, ax;
    M3 kPoint;
    float Plimit = 0, Pmotor = 0, Pslide[2] = {0, 0}, angle = 0, speed = 0, force = 0;
    float limitMass = 0, motorMass = 0;
    V3 lin1, lin2;                             // slider: the two directions across the line
    float slideMass[2] = {0, 0};
    int limitState = 0;                        // 0 free, 1 at lo, 2 at hi, 3 locked
    float limitBias = 0;
    // cone-twist
    V3 swingAxis; float swingMass = 0, Pswing = 0, swingBias = 0; bool swingActive = false;
    // a drive on the relative turn about each of A's own axes: a viscous coupling (N m s / rad) toward a relative
    // speed, solved implicitly (a soft constraint) within [dampLo, dampHi] N m - a character's muscles: a joint servo
    // that is stable however stiff, never stronger than the muscles crossing it
    float damp[3] = {0, 0, 0}, dampTarget[3] = {0, 0, 0}, dampLo[3] = {0, 0, 0}, dampHi[3] = {0, 0, 0};
    float Pdamp[3] = {0, 0, 0}, dampMass[3] = {0, 0, 0};
    V3 dampAxis[3];
};

/** An impact to report after the step. */
struct Impact {
    astratto_handle a = 0, b = 0, other = 0;
    V3 p, n;
    float speed = 0, impulse = 0;
    uint32_t ma = 0, mb = 0;
};

class Space {
public:
    explicit Space(JobPool* pool = nullptr) : pool_(pool) {}

    int add(const Body& b);                    // returns its index
    void remove(int index);                    // swap-removes; joints on it are dropped
    int find(astratto_handle h) const { auto it = index_.find(h); return it == index_.end() ? -1 : it->second; }
    Body& body(int i) { return bodies_[static_cast<size_t>(i)]; }
    const Body& body(int i) const { return bodies_[static_cast<size_t>(i)]; }
    size_t count() const { return bodies_.size(); }

    int addJoint(const Joint& j);
    void removeJoint(astratto_handle h);
    Joint* joint(astratto_handle h);
    const std::vector<Joint>& joints() const { return joints_; }

    /** Advances by dt in fixed steps (the remainder carried over). Returns the steps run. */
    int advance(const VoxelWorld* w, float dt, float gravity);
    /** One fixed step, now: for a caller that keeps its own clock and acts between steps (Sydney's muscles). */
    void tick(const VoxelWorld* w, float gravity);
    /** How far into the next step the frame is (0..1), for blending poses. */
    float alpha() const { return acc_ / step_; }
    /** The step's length and the solver's passes over the constraints (a jointed body wants a finer, deeper solve). */
    void setRate(float step, int iterations) { step_ = std::max(1e-4f, step); iterations_ = std::max(1, iterations); }
    float stepLength() const { return step_; }

    void impulseAt(int i, V3 point, V3 j);
    void explode(V3 c, float radius, float impulse);
    void wakeNear(V3 lo, V3 hi);
    /** Wakes every body touching a block that changed (a wall knocked out from under a pile). */
    void worldChanged(int x, int y, int z) { wakeNear(V3(x - 1.5f, y - 1.5f, z - 1.5f), V3(x + 2.5f, y + 2.5f, z + 2.5f)); }

    std::vector<Impact>& impacts() { return impacts_; }
    std::vector<astratto_handle>& brokenJoints() { return broken_; }
    std::vector<astratto_handle>& fellAsleep() { return slept_; }
    uint64_t contactCount() const { return contactTotal_; }

    /** Where a ray first strikes a body (not proxies); -1 when none. */
    int raycast(V3 o, V3 d, float maxDist, float& t, V3& n) const;

private:
    void step(const VoxelWorld* w, float gravity);
    void broadphase();
    void narrowphase(const VoxelWorld* w);
    void worldContacts(const VoxelWorld* w, int i, std::vector<Manifold>& out);
    void islands();
    void prepare(float dt);
    void warmStart();
    void solveVelocities();
    void integrate(float dt, float gravity);
    void sleep(float dt);
    void sweepBullets(const VoxelWorld* w, float dt);
    bool touches(const Body& a, const Body& b) const;
    void prepareJoint(Joint& j, float dt);
    void forget(const Joint& j);
    void solveJoint(Joint& j);

    JobPool* pool_;
    std::vector<Body> bodies_;
    std::unordered_map<astratto_handle, int> index_;
    std::vector<Joint> joints_;
    std::unordered_multiset<uint64_t> jointed_;     // pairs of bodies a joint couples (and keeps apart from each other)
    std::vector<std::pair<int, int>> pairs_;
    std::vector<Manifold> manifolds_;
    std::unordered_map<uint64_t, Manifold> previous_;
    std::vector<std::vector<Manifold>> scratch_;
    std::vector<Impact> impacts_;
    std::vector<astratto_handle> broken_, slept_;
    std::vector<int> parent_;
    float acc_ = 0;
    float step_ = STEP;
    int iterations_ = VELOCITY_ITERATIONS;
    uint64_t contactTotal_ = 0;
};

// ------------------------------------------------------------------ narrowphase (collide.cpp)
/** Contacts between two bodies' shapes, as a manifold (count 0: apart). Normal points from b to a. */
void collide(const Body& a, const Body& b, Manifold& m);
/** Contacts between a body and one solid cell of the world (open: which of its six faces have no solid neighbour). */
void collideCell(const Body& a, int cx, int cy, int cz, uint32_t open, Manifold& m);
/** GJK distance between two convex shapes in place (support functions); true when they overlap (then EPA fills depth). */
bool gjkEpa(const Body& a, const Body& b, V3& pa, V3& pb, V3& n, float& depth);

}  // namespace impulsis
}  // namespace astratto
