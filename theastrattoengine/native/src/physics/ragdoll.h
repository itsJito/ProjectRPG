// Astratto Engine - Sydney 3: a human body that moves itself.
//
// Sydney synthesises a character's motion by simulating it. The body is a simplified skeleton of thirteen rigid bodies
// (pelvis, torso, head, the two segments of each arm, thigh, shin and foot of each leg) joined by twelve joints, and it
// lives in its own Impulsis space: Impulsis supplies gravity, contact and friction with the voxel world, the joints and
// their hard limits, and every blow from outside. Sydney supplies the muscles. Each joint is crossed by antagonistic
// pairs of Hill-type muscles, one pair an axis, whose force depends on their activation, their length and how fast
// they are changing length; their activation follows a neural excitation through first-order activation dynamics.
//
// The excitations come from a simulated nervous system in three layers:
//   - behaviour (the top): what the body is trying to do - stay balanced, take a step, brace for a fall, protect the
//     head, reach for a ledge and hang from it, lie hurt, get up, walk - set as goals: target joint rotations,
//     a torso orientation, a centre-of-mass target, end-effector targets for hands and feet, a rhythm.
//   - supraspinal control: balance by an LQR on the linear inverted pendulum (the optimal centre of pressure for the
//     centre of mass's state), realised through the stance legs by virtual-model control (Jacobian-transpose torques),
//     torso stabilisation through the stance hip, capture-point foot placement, and Matsuoka neural oscillators (a
//     central pattern generator) that time the steps of a stagger or a walk and are reset by the feet striking.
//   - spinal control: the joint servos, gravity compensation, a delayed stretch reflex, and the muscle solver - the
//     activations that produce the wanted torques at least effort (static optimisation, sum of squared activations,
//     with the co-contraction the behaviour asks for), turned into excitations by inverse activation dynamics.
//
// What comes out is mapped back onto the full-detail skeleton the game draws: the fifteen joint points the engine has
// always reported, and now each simulated segment's orientation as well.
#pragma once

#include <algorithm>
#include <cmath>
#include <cstdint>
#include <vector>

#include "astratto/astratto.h"
#include "impulsis/space.h"
#include "world/voxel_world.h"

namespace astratto {

/** The world, as much as a falling body needs of it (null = no world: nothing to land on). */
using SolidFn = bool (*)(const VoxelWorld* w, int x, int y, int z);

namespace rd {
// the fifteen joint points of the API (ASTRATTO_RD_*)
constexpr int HEAD_P = 0, NECK = 1, PELVIS = 2, LS = 3, LE = 4, LW = 5, RS = 6, RE = 7, RW = 8, LH = 9, LK = 10, LA = 11, RH = 12, RK = 13, RA = 14, N = 15;
constexpr float VOX = 1.8f / 32.0f;           // metres per rig voxel at scale 1
constexpr float ACTIVE_TIME = 8;
/** The points' masses as the API has always given them (a share of the body each), for whoever weighs by point. */
extern const float MASS[N];
}  // namespace rd

namespace syd {
// the simulated segments
enum Part { PELVIS = 0, TORSO, HEAD, L_UPPER, L_FORE, R_UPPER, R_FORE, L_THIGH, L_SHIN, L_FOOT, R_THIGH, R_SHIN, R_FOOT, PARTS };
// the joints: each between a parent (nearer the pelvis) and a child
enum Joint { SPINE = 0, NECK, L_SHOULDER, L_ELBOW, R_SHOULDER, R_ELBOW, L_HIP, L_KNEE, L_ANKLE, R_HIP, R_KNEE, R_ANKLE, JOINTS };
// the axes muscles turn a joint about, in the parent's frame: x pitch (flexion), y twist, z roll (ab/adduction)
constexpr int AXES = 3;
constexpr float STEP = 1.0f / 240.0f;
constexpr int ITERATIONS = 14;
}  // namespace syd

class SydneyCharacter;

/** The body: thirteen rigid segments in their own Impulsis space. */
class Ragdoll {
public:
    explicit Ragdoll(float scale);

    float gravity = 22.0f;                     // m/s^2: the physics service's ragdoll gravity
    float unit;                                // metres per rig voxel for this body
    float scale;
    float age = 0, quiet = 0;
    bool settled = false, live = false;
    SydneyCharacter* driver = nullptr;

    // the fifteen points, as last stepped and blended to the frame (world)
    float x[rd::N] = {}, y[rd::N] = {}, z[rd::N] = {};
    bool grounded[rd::N] = {};
    float fwd[3] = {0, 0, -1};

    impulsis::Space space;
    int part[syd::PARTS] = {};                 // each segment's index in the space
    astratto_handle jointHandle[syd::JOINTS] = {};
    float partMass[syd::PARTS] = {};
    float totalMass = 0;

    impulsis::Body& body(int p) { return space.body(part[p]); }
    const impulsis::Body& body(int p) const { return space.body(part[p]); }

    /** Seeds the segments from an animated pose (the fifteen points, world) and a base velocity. */
    void fromPose(const float* p, float vx, float vy, float vz);
    void standAt(float wx, float wy, float wz, float yaw);
    void shift(float dx, float dy, float dz);
    float unbury(const VoxelWorld* w);
    /** A change of velocity on a region of the body (no controller): ASTRATTO_REGION_*. */
    void impulse(int region, float vx, float vy, float vz);
    /** Something has hold of it and moves it toward a velocity. */
    void carry(float vx, float vy, float vz, float share);
    /** An impulse (N s) on the segment nearest a point, there. Each segment takes at most maxDv of change of speed;
     *  what it cannot take goes into the trunk. */
    void strike(int seg, impulsis::V3 point, impulsis::V3 J, float maxDv);
    /** The same velocity change for every segment. */
    void kickAll(float vx, float vy, float vz);
    void wake();
    int disturb(float wx, float wy, float wz, float radius, float vx, float vy, float vz);
    /** Advances by a frame's worth of fixed steps. Returns the steps run. */
    int update(const VoxelWorld* w, float dt);
    float lowestY() const;
    /** The way the trunk faces, as an actor's yaw (actor forward is (-sin yaw, -cos yaw)). */
    float yaw();
    void forward();

    float segQ[syd::PARTS * 4] = {}, segC[syd::PARTS * 3] = {};
    /** The parts that struck the world hard since the caller last took them. */
    std::vector<astratto_evt_ragdoll_impact> impacts;

    // ---- what the game draws (blended between the last two steps)
    /** Each segment's orientation (w x y z) and centre, blended to the frame. */
    void segment(int p, float* q, float* c) const;
    impulsis::V3 com() const;
    impulsis::V3 comVelocity() const;

    // ---- the skeleton's geometry
    /** The joint's point and its axis frame (the parent's), world. */
    impulsis::V3 jointPoint(int j) const;
    static int jointParent(int j);
    static int jointChild(int j);
    static int jointDof(int j);                // 1 for the hinges (knees, elbows, ankles), 3 for the rest
    static int pointPart(int point);           // which segment carries an API point
    impulsis::V3 pointWorld(int point) const;  // an API point, from its segment (unblended)

private:
    void build();
    void place(const impulsis::V3* pos, const impulsis::Q* rot, impulsis::V3 v);
    void publish(float alpha);
    float acc_ = 0;
};

// ------------------------------------------------------------------ the muscles
/** A Hill-type muscle-tendon unit on one axis of a joint (rigid tendon). Lengths in optimal fibre lengths, speeds in
 *  optimal fibre lengths a second. */
struct Muscle {
    float sign = 1;                            // +1: it turns the joint the + way about its axis
    float Fr = 0;                              // moment arm x maximum isometric force: its torque at full activation (N m)
    float k = 0.3f;                            // moment arm / optimal fibre length: fibre strain per radian
    float thetaOpt = 0;                        // the joint angle at which its fibres sit at optimal length
    float slack = 1.25f;                       // the fibre length past which the parallel elastic element takes up
    float a = 0.01f, u = 0.01f;                // activation and excitation, 0..1
    float l = 1, v = 0, fl = 1, fv = 1;        // fibre length and velocity; the force-length and force-velocity factors
    float vHist[8] = {};                       // the spindles' recent readings (for the delayed stretch reflex)
    int vAt = 0;

    void sense(float theta, float omega);
    /** Torque it could add at full activation, now (signed by its action, >= 0 along it). */
    float capacity() const { return Fr * fl * fv; }
    /** Torque the passive tissue gives now (signed: + about the axis). */
    float passive() const;
    float torque() const { return sign * (a * capacity()) + passive(); }
    void activate(float h, float tauAct, float tauDeact);
    float delayedV(int steps) const { return vHist[(vAt - steps + 64) & 7]; }
};

// ------------------------------------------------------------------ the central pattern generator
/** A Matsuoka half-centre oscillator: two neurons that inhibit each other and tire (adapt). Its output swings
 *  between the two (flexor / extensor bursts) at a rate set by its time constants, as long as it has tonic drive. */
struct Oscillator {
    float x[2] = {0.1f, 0}, v[2] = {0, 0};     // membrane states, fatigue
    float tauR = 0.08f, tauA = 0.16f, beta = 2.5f, w = 2.2f, drive = 0;
    float y(int i) const { return x[i] > 0 ? x[i] : 0; }
    float out() const { return y(0) - y(1); }
    void step(float h, float feedback0, float feedback1, float coupling0, float coupling1);
};

// ------------------------------------------------------------------ the profile (API compatible)
struct SydneyConfig {
    enum { SPINE = 0, HIP = 1, KNEE = 2, SHOULDER = 3, ELBOW = 4, NECK = 5, CLASSES = 6 };
    enum { HUMAN = 0, INFECTED = 1, CRAWLER = 2 };
    // joint servos: natural frequency (Hz) and damping ratio by joint class; the muscles' strength by class (N m, the
    // torque the strongest muscle on that class of joint gives at 9.81 m/s^2 - scaled by the world's gravity)
    float freq[6] = {3.75f, 4.75f, 3.2f, 2.6f, 2.8f, 2.4f}, zeta[6] = {1.0f, 0.95f, 0.95f, 0.9f, 0.9f, 1.0f}, torque[6] = {260, 260, 260, 85, 72, 36};
    float strength = 1, stanceGain = 2.97f, standPosture = 1.68f, stanceHip = 0.6f, carryHip = 1.0f, carryPosture = 1;
    float ankleLever = 0.13f, balanceGain = 0.8f, stepThreshold = 0.52f, stepTime = 0.2f, stepReach = 0.6f, stepLift = 0.105f, stepOvershoot = 0.065f, stepLead = 0.15f, stepForce = 900;
    int maxSteps = 8;
    float stepWidth = 0.05f, shiftTime = 0.03f, landBrake = 1.0f, hipStrategy = 0.3f, pelvisGain = 1.85f, rootMode = 0, spineStiff = 1.5f, hipRollKeep = 0.72f, rollTwist = 1.0f;   // (not in the API's profile)
    float fallTilt = 55, fallHeight = 0.55f, lostReach = 1.1f, recoverSettle = 0.45f;
    float fallStrength = 0.35f, brace = 0.85f, downStrength = 0.1f, downMin = 0.9f, downPerForce = 0.02f, downMax = 3.5f;
    bool holdWound = true;
    float holdForce = 60;
    float getUpTime = 2.6f, getUpStrength = 1.35f, getUpTimeout = 2.4f;
    int getUpTries = 3;
    float deathFade = 0.45f, deathPosture = 0.25f;
    int style = HUMAN;
    float crawlHeight = 0.36f;

    static SydneyConfig human();
    static SydneyConfig infected();
    static SydneyConfig crawler();
    static SydneyConfig giant();
    void from(const astratto_sydney_profile& p);
};

/** One character's traits (from its skills), and what it has been asked to do. */
struct SydneyTraits { float strength = 1, agility = 1, toughness = 1, endurance = 1; };
struct SydneyIntent { uint32_t kind = ASTRATTO_INTENT_BALANCE; float x = 0, y = 0, z = 0, value = 0, time = 0; };

/** What the senses report, each step. */
struct SydneySenses {
    impulsis::V3 com, comV, up{0, 1, 0}, right{1, 0, 0}, fwd{0, 0, -1}, headUp{0, 1, 0};
    float mass = 0, height = 0, standHeight = 1, groundY = 0, tilt = 0, speed = 0, omega = 3;
    float support[2] = {}, weight = 0;         // the force under each foot (N) and the body's weight
    bool foot[2] = {}, footFlat[2] = {};
    int feet = 0, otherContacts = 0, handsDown = 0;
    impulsis::V3 cop, capture;                 // centre of pressure (when on its feet) and the capture point
    float captureOut = 0;                      // how far the capture point is outside the support (m; < 0 inside)
    impulsis::V3 supportCentre;
    float jolt = 0;
    bool supine = false, prone = false;
};

class SydneyCharacter {
public:
    SydneyCharacter(Ragdoll* r, const SydneyConfig& cfg);

    enum State { STAND = 0, STEP = 1, FALL = 2, DOWN = 3, GETUP = 4, DEAD = 5 };
    enum Outcome { NONE = 0, RECOVERED = 1, CRAWL = 2, STUCK = 3, DEAD_OUTCOME = 4 };
    enum Activity { A_NONE = 0, A_BRACE = 1, A_HANG = 2, A_WALK = 3, A_REACH = 4, A_PROTECT = 5, A_WRITHE = 6, A_HOLD = 7 };

    Ragdoll* body;
    SydneyConfig cfg;
    SydneyTraits traits;
    SydneyIntent intent;
    SydneySenses s;
    bool alive = true, primed = false, invalid = false;
    bool armL = true, armR = true, legL = true, legR = true, head = true;
    int wound = -1;                            // the API point last struck (held, when it can be)

    // the coordinator
    State state = STAND;
    Outcome outcome = NONE;
    Activity activity = A_NONE;
    float t = 0, settled = 0, airborne = 0, quiet = 0, downFor = 1.2f, stun = 0, react = 0, over = 0;
    int steps = 0, tries = 0, falls = 0, recoveries = 0;
    bool stayDown = false;
    float longestAir = 0;
    float effort = 0, balanceErr = 0, assistUsed = 0;

    /** Called before each fixed step: sense, decide, drive the muscles. */
    void drive(const VoxelWorld* w, float h);
    void hit(int point, float dx, float dy, float dz, float impulse, float force);
    void die();
    void collapse();
    void setBody(uint32_t brokenLimbs);
    void setIntent(const SydneyIntent& i);
    /** The hands' grips on the world (a ledge): true while that hand holds. */
    bool gripping(int side) const { return grip_[side] != 0; }

    // ---- the muscles, for whoever looks (a test, a debugger)
    Muscle muscle[syd::JOINTS][syd::AXES][2];
    float wantTorque[syd::JOINTS][3] = {};     // what the controllers asked of each joint (parent frame)
    float gotTorque[syd::JOINTS][3] = {};      // what the muscles gave
    Oscillator cpg[2];
    impulsis::V3 copWanted;
    impulsis::V3 reachAt[4];                   // where each hand and foot was sent (last step), when it was
    bool reaching[4] = {};                    // the centre of pressure the balance asked for (last step)
    float lqrGain(int i) const { return lqrK_[i]; }
    impulsis::V3 jointAngles(int j) const { impulsis::Q q; impulsis::V3 th, om; jointState(j, q, th, om); return th; }
    impulsis::V3 targetAngles(int j) const { return impulsis::rotationVector(impulsis::Q(), g_.target[j]); }
    float servoGain(int j) const { return g_.gain[j]; }
    int getUpPhase() const { return getUpPhase_; }
    int swingSide() const { return state == STEP ? stepSide_ : -1; }
    impulsis::V3 stepTarget() const { return stepTo_; }
    float stepPhase() const { return stepT_ / stepDur_; }
    /** A step on command (the lab, and anything that wants a deliberate step): side 0 left, 1 right, to a ground point. */
    void forceStep(int side, impulsis::V3 to) { stepSide_ = side; stepFrom_ = body->body(side == 0 ? syd::L_FOOT : syd::R_FOOT).p; stepTo_ = to; stepT_ = 0; lifted_ = false; shiftT_ = 0; stepDur_ = cfg.stepTime * 1.4f / pace(); forced_ = true; go(STEP); }

private:
    // goals the behaviours set each step
    struct Goals {
        impulsis::Q target[syd::JOINTS];       // relative rotations wanted (parent frame)
        float gain[syd::JOINTS] = {};          // servo stiffness, 0 = limp
        float damp[syd::JOINTS] = {};          // extra damping share
        float cocon[syd::JOINTS] = {};         // co-contraction (stiffness of a brace), 0..1
        bool world[syd::JOINTS] = {};          // target is the child's world orientation instead (head, feet)
        impulsis::Q worldTarget[syd::JOINTS];
        bool balance = false, stance[2] = {false, false}, torso = false, gravityComp = true;
        impulsis::Q torsoTarget;
        float torsoGain = 1, heightWant = 0, heightGain = 0, balanceGain = 1;
        impulsis::V3 copRef;                   // where the centre of mass should be held over
        bool hasCopRef = false;
        impulsis::V3 velRef;                   // walking: the velocity the centre of mass should keep (no place held)
        bool hasVelRef = false;
        impulsis::V3 walkAxis;                 // walking: the way it goes (balance only across it)
        bool hasWalkAxis = false;
        // end effectors: hands 0/1, feet 2/3
        bool eff[4] = {};
        impulsis::V3 effAt[4], effVel[4];
        float effHz[4] = {}, effMax[4] = {};
        // pushes through limbs braced on the ground (getting up): the trunk lifted by what touches
        float lift = 0, liftHeight = 0;
        impulsis::V3 liftDir{0, 1, 0};
        float tone = 1;                        // the whole body's muscle tone (death drains it)
        bool assist = false;                   // the get-up's bounded root support (see SydneyCharacter::assist)
        impulsis::V3 assistAt;
        impulsis::Q assistQ;
        float assistMax = 0;
        float reflex = 1;                      // stretch reflex gain
        void clear();
    } g_;

    // the behaviours' own state
    int stepSide_ = -1;
    float stepT_ = 0, stepDur_ = 0.28f;
    bool lifted_ = false, forced_ = false, noShift_ = false;   // the swing foot has left the ground (a step lands only after); a step on command; no weight shift first
    float shiftT_ = 0;                         // >= 0 while the weight is being moved onto the stance foot
    float inside_ = 0;                         // how long the capture point has been back inside the feet
    impulsis::V3 stepFrom_, stepTo_;
    float getUpT_ = 0, getUpPhaseT_ = 0, flailT_ = 0, limpT_ = 0, hangT_ = 0, writheT_ = 0, breathT_ = 0, braceT_ = 0;
    int getUpPhase_ = 0;
    float rollSide_ = 1;
    astratto_handle grip_[2] = {0, 0};
    impulsis::V3 gripAt_[2], ledgeAt_[2];
    float ledgeT_[2] = {0, 0};             // how much longer each hand keeps going for the edge it found
    float gripTired_ = 0;
    float lastSupport_[2] = {};
    float hitDelay_ = 0;
    impulsis::V3 hitDir_;
    float pending_ = 0;
    uint64_t rng_ = 0x9E3779B97F4A7C15ull;
    float lqrK_[2] = {1.6f, 0.45f};
    float damping_[syd::JOINTS] = {}, servoKp_[syd::JOINTS] = {};
    impulsis::V3 servoE_[syd::JOINTS];
    impulsis::Q rel_[syd::JOINTS];
    impulsis::V3 theta_[syd::JOINTS], omega_[syd::JOINTS];
    bool haveRel_ = false;
    impulsis::V3 lastP_[syd::PARTS], partV_[syd::PARTS], partW_[syd::PARTS];
    impulsis::Q lastQ_[syd::PARTS];          // each joint's viscous damping this step (N m s / rad)
    float lqrOmega_ = -1;
    float strideClock_ = 0;

    /** How much quicker everything happens than on Earth: a body falls in sqrt(9.81 / g) of the time, and a nervous
     *  system that keeps it up must act as much sooner (its servos and its steps are paced by this). */
    float pace() const { return std::sqrt(std::max(1.0f, body->gravity) / 9.81f); }
    void go(State st) { state = st; t = 0; }
    void fallNow() { falls++; quiet = 0; releaseGrips(); go(FALL); }
    void sense(const VoxelWorld* w);
    void proprioception(float h);
    void assist(float h);
    void coordinate(const VoxelWorld* w, float h);
    void spinal(float h);
    void supraspinal(float h);
    void jointState(int j, impulsis::Q& rel, impulsis::V3& theta, impulsis::V3& omega) const;
    void applyTorque(int j, impulsis::V3 tauParentFrame, float h);
    void jointTorqueWorld(int j, impulsis::V3 tauWorld, impulsis::V3* acc);
    void solveLqr();
    float rand01();

    // behaviours
    void standGoals(float h);
    bool stepBegin(const VoxelWorld* w);
    void stepGoals(float h);
    /** Two-bone IK: the hip (pitch, roll) and knee angles that put the ankle at a world point. */
    void legIk(int side, impulsis::V3 ankle, float gain);
    bool stepLanded() const;
    impulsis::V3 predictCapture(impulsis::V3 pivot, float ahead) const;
    float stepTime() const;
    void walkGoals(float h);
    void fallGoals(const VoxelWorld* w, float h);
    void protectGoals(impulsis::V3 dir, float k);
    bool findLedge(const VoxelWorld* w, int side, impulsis::V3& at) const;
    void hangGoals(const VoxelWorld* w, float h);
    void releaseGrips();
    void takeHold(int side, impulsis::V3 at);
    impulsis::V3 walkTarget(int side, float left) const;
    void lieGoals(float h);
    void holdGoals();
    void getUpGoals(const VoxelWorld* w, float h);
    void limpGoals(float h);
    void relaxArms(float gain);
    void setJoint(int j, float ax, float ay, float az, float gain, float damp = 0, float cocon = 0);
    void setReach(int e, impulsis::V3 at, float hz, float maxForce);
    impulsis::V3 effector(int e) const;
    impulsis::V3 effectorVel(int e) const;
    void effectorForce(int e, impulsis::V3 F, impulsis::V3* acc);
};

}  // namespace astratto
