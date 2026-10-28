// Astratto Engine - Sydney 3's nervous system: the muscles (Hill-type), the central pattern generator (Matsuoka
// oscillators), the senses, the spinal layer (joint servos, gravity compensation, the stretch reflex, the muscle
// solver), the supraspinal layer (LQR balance on the linear inverted pendulum, virtual-model control of the stance
// legs, torso stabilisation, end-effector control) and the behaviours with the state machine that chooses between them.
#include <algorithm>
#include <cmath>
#include <cstring>

#include "physics/ragdoll.h"

namespace astratto {

using namespace impulsis;
using namespace syd;

namespace {
constexpr float PI = 3.14159265358979f;
constexpr float G0 = 9.81f;
inline float clampf(float v, float a, float b) { return v < a ? a : v > b ? b : v; }
inline float lerp(float a, float b, float t) { return a + (b - a) * t; }
inline float smooth(float t) { t = clampf(t, 0, 1); return t * t * (3 - 2 * t); }
inline float rad(float deg) { return deg * PI / 180.0f; }
inline V3 flat(V3 v) { return V3(v.x, 0, v.z); }
inline float hlen(V3 v) { return std::sqrt(v.x * v.x + v.z * v.z); }

// ---- the muscles on each axis of each joint: the strongest torque each way (N m, at 9.81 m/s^2), and the joint's
// range on that axis (radians, the parent's frame; + about x turns a hanging segment's lower end forward and an
// upright segment's top backward; + about z turns a hanging segment's lower end toward +x and an upright one's top
// toward -x). Left and right are mirrored about x.
struct AxisDef { float pos, neg, lo, hi; };
struct JointMuscles { AxisDef ax[3]; int cls; };
const JointMuscles MUSCLES[JOINTS] = {
    {{{250, 180, -0.8f, 0.45f}, {100, 100, -0.6f, 0.6f}, {150, 150, -0.5f, 0.5f}}, SydneyConfig::SPINE},       // spine
    {{{35, 35, -0.7f, 0.6f}, {25, 25, -1.1f, 1.1f}, {30, 30, -0.6f, 0.6f}}, SydneyConfig::NECK},               // neck
    {{{80, 80, -0.8f, 2.6f}, {40, 40, -1.1f, 1.1f}, {70, 70, -2.2f, 0.3f}}, SydneyConfig::SHOULDER},           // left shoulder (abduction is -z)
    {{{70, 60, 0.0f, 2.5f}, {}, {}}, SydneyConfig::ELBOW},                                                    // left elbow
    {{{80, 80, -0.8f, 2.6f}, {40, 40, -1.1f, 1.1f}, {70, 70, -0.3f, 2.2f}}, SydneyConfig::SHOULDER},
    {{{70, 60, 0.0f, 2.5f}, {}, {}}, SydneyConfig::ELBOW},
    {{{210, 260, -0.45f, 2.0f}, {60, 60, -0.7f, 0.7f}, {150, 170, -0.9f, 0.35f}}, SydneyConfig::HIP},          // left hip
    {{{250, 130, -2.4f, 0.0f}, {}, {}}, SydneyConfig::KNEE},                                                  // left knee: + straightens
    {{{50, 160, -0.8f, 0.5f}, {20, 20, -0.3f, 0.3f}, {60, 60, -0.35f, 0.35f}}, SydneyConfig::KNEE},           // left ankle: + lifts the toes; z inverts and everts
    {{{210, 260, -0.45f, 2.0f}, {60, 60, -0.7f, 0.7f}, {170, 150, -0.35f, 0.9f}}, SydneyConfig::HIP},
    {{{250, 130, -2.4f, 0.0f}, {}, {}}, SydneyConfig::KNEE},
    {{{50, 160, -0.8f, 0.5f}, {20, 20, -0.3f, 0.3f}, {60, 60, -0.35f, 0.35f}}, SydneyConfig::KNEE}};
const float DEFAULT_TORQUE[6] = {260, 260, 260, 85, 72, 36};

// what hangs from each joint (its child's subtree)
const int DISTAL[JOINTS][7] = {
    {TORSO, HEAD, L_UPPER, L_FORE, R_UPPER, R_FORE, -1}, {HEAD, -1}, {L_UPPER, L_FORE, -1}, {L_FORE, -1}, {R_UPPER, R_FORE, -1}, {R_FORE, -1},
    {L_THIGH, L_SHIN, L_FOOT, -1}, {L_SHIN, L_FOOT, -1}, {L_FOOT, -1}, {R_THIGH, R_SHIN, R_FOOT, -1}, {R_SHIN, R_FOOT, -1}, {R_FOOT, -1}};
const int LEG[2][3] = {{L_HIP, L_KNEE, L_ANKLE}, {R_HIP, R_KNEE, R_ANKLE}};
const int FOOT[2] = {L_FOOT, R_FOOT}, SHIN[2] = {L_SHIN, R_SHIN}, FORE[2] = {L_FORE, R_FORE};
const int ARM[2][2] = {{L_SHOULDER, L_ELBOW}, {R_SHOULDER, R_ELBOW}};
const float MIRROR[2] = {-1, 1};               // left, right: the sign of an outward turn about z and y

// ---- the muscle's curves (Geyer and Herr 2010; Thelen 2003)
inline float forceLength(float l) { const float x = std::fabs((l - 1) / 0.56f); return std::exp(-2.9957f * x * x * x); }
inline float forceVelocity(float vn) {
    if (vn < 0) return std::max(0.0f, (1 + std::max(vn, -1.0f)) / (1 - 4 * std::max(vn, -1.0f)));   // shortening: Hill's hyperbola
    return 1.5f - 0.5f * std::exp(-10 * vn);                                                     // lengthening: up to 1.5 x isometric
}
constexpr float VMAX = 10;                       // optimal fibre lengths a second
constexpr float TAU_ACT = 0.006f, TAU_DEACT = 0.014f;   // fast-twitch fibre (activation 5-10 ms, deactivation 10-20 ms)

Q expMap(V3 r) { const float a = len(r); return a < 1e-7f ? Q() : axisAngle(r / a, a); }

/** The quaternion whose columns are the given axes. */
Q basisQ(V3 x, V3 y, V3 z) {
    const float tr = x.x + y.y + z.z;
    Q q;
    if (tr > 0) { const float s = std::sqrt(tr + 1) * 2; q = Q(0.25f * s, (y.z - z.y) / s, (z.x - x.z) / s, (x.y - y.x) / s); }
    else if (x.x > y.y && x.x > z.z) { const float s = std::sqrt(1 + x.x - y.y - z.z) * 2; q = Q((y.z - z.y) / s, 0.25f * s, (y.x + x.y) / s, (z.x + x.z) / s); }
    else if (y.y > z.z) { const float s = std::sqrt(1 + y.y - x.x - z.z) * 2; q = Q((z.x - x.z) / s, (y.x + x.y) / s, 0.25f * s, (z.y + y.z) / s); }
    else { const float s = std::sqrt(1 + z.z - x.x - y.y) * 2; q = Q((x.y - y.x) / s, (z.x + x.z) / s, (z.y + y.z) / s, 0.25f * s); }
    return qnorm(q);
}
/** Upright, facing a horizontal heading (forward = -z of the frame). */
Q heading(V3 fwd) {
    V3 f = flat(fwd);
    if (len2(f) < 1e-8f) f = V3(0, 0, -1);
    f = norm(f);
    const V3 z = -f, y(0, 1, 0), x = cross(y, z);
    return basisQ(x, y, z);
}

// ---- the support: the soles of the planted feet as a convex polygon in the ground plane
struct Poly {
    float px[8], pz[8];
    int n = 0;
    void hull(const float* xs, const float* zs, int k) {
        int idx[8];
        for (int i = 0; i < k; i++) idx[i] = i;
        // At most eight sole corners: insertion sort avoids std::sort's larger
        // internal insertion-sort window (and GCC's spurious bounds warning).
        auto before = [&](int a, int b) { return xs[a] < xs[b] || (xs[a] == xs[b] && zs[a] < zs[b]); };
        for (int i = 1; i < k; i++) {
            const int value = idx[i];
            int j = i;
            while (j > 0 && before(value, idx[j - 1])) { idx[j] = idx[j - 1]; j--; }
            idx[j] = value;
        }
        int h[17], m = 0;
        auto crossz = [&](int o, int a, int b) { return (xs[a] - xs[o]) * (zs[b] - zs[o]) - (zs[a] - zs[o]) * (xs[b] - xs[o]); };
        for (int i = 0; i < k; i++) { while (m >= 2 && crossz(h[m - 2], h[m - 1], idx[i]) <= 0) m--; h[m++] = idx[i]; }
        for (int i = k - 2, lo = m + 1; i >= 0; i--) { while (m >= lo && crossz(h[m - 2], h[m - 1], idx[i]) <= 0) m--; h[m++] = idx[i]; }
        n = std::min(8, std::max(0, m - 1));
        for (int i = 0; i < n; i++) { px[i] = xs[h[i]]; pz[i] = zs[h[i]]; }
    }
    /** Distance outside (> 0) or inside (< 0), and the nearest point of the polygon. */
    float outside(float x, float z, float& nx, float& nz) const {
        if (n == 0) { nx = x; nz = z; return 1e3f; }
        if (n == 1) { nx = px[0]; nz = pz[0]; return std::hypot(x - nx, z - nz); }
        bool in = n >= 3;
        float best = 1e30f;
        for (int i = 0; i < n; i++) {
            const int k = (i + 1) % n;
            const float ex = px[k] - px[i], ez = pz[k] - pz[i], l2 = ex * ex + ez * ez;
            const float t = l2 > 1e-12f ? clampf(((x - px[i]) * ex + (z - pz[i]) * ez) / l2, 0, 1) : 0;
            const float cx = px[i] + ex * t, cz = pz[i] + ez * t, d = std::hypot(x - cx, z - cz);
            if (d < best) { best = d; nx = cx; nz = cz; }
            if (ex * (z - pz[i]) - ez * (x - px[i]) < 0) in = false;   // counter-clockwise hull: inside is to the left
        }
        if (in) { nx = x; nz = z; return -best; }
        return best;
    }
};

}  // namespace

// ------------------------------------------------------------------ muscle
void Muscle::sense(float theta, float omega) {
    l = 1 - sign * k * (theta - thetaOpt);
    v = -sign * k * omega;
    const float vn = v / VMAX;
    fl = forceLength(l);
    fv = forceVelocity(vn);
    vAt = (vAt + 1) & 7;
    vHist[vAt] = vn;
}

float Muscle::passive() const {
    if (l <= slack) return 0;
    const float e = (l - slack) / 0.15f;
    return sign * Fr * std::min(e * e, 4.0f);
}

void Muscle::activate(float h, float tauAct, float tauDeact) {
    const float tau = u > a ? tauAct * (0.5f + 1.5f * a) : tauDeact / (0.5f + 1.5f * a);
    a = u + (a - u) * std::exp(-h / tau);
    a = clampf(a, 0.0f, 1.0f);
}

// ------------------------------------------------------------------ the oscillator
void Oscillator::step(float h, float fb0, float fb1, float c0, float c1) {
    // tauR x' = -x - beta v - w y_other + drive + feedback - coupling;  tauA v' = -v + y
    const float y0 = y(0), y1 = y(1);
    const float dx0 = (-x[0] - beta * v[0] - w * y1 + drive + fb0 - c0) / tauR;
    const float dx1 = (-x[1] - beta * v[1] - w * y0 + drive + fb1 - c1) / tauR;
    x[0] += dx0 * h; x[1] += dx1 * h;
    v[0] += (-v[0] + y0) / tauA * h; v[1] += (-v[1] + y1) / tauA * h;
}

// ------------------------------------------------------------------ profiles
SydneyConfig SydneyConfig::human() { return SydneyConfig(); }

SydneyConfig SydneyConfig::infected() {
    SydneyConfig c;
    c.maxSteps = 7; c.balanceGain = 2.2f; c.brace = 0.15f; c.holdWound = false; c.downMin = 0.4f; c.downMax = 1.6f;
    c.strength = 1.15f; c.fallTilt = 62; c.deathFade = 0.55f;
    c.style = INFECTED;
    c.getUpTime = 2.2f; c.getUpTimeout = 2.6f;
    return c;
}

SydneyConfig SydneyConfig::crawler() {
    SydneyConfig c = infected();
    c.getUpTime = 1.4f; c.getUpTimeout = 2.0f;
    c.style = CRAWLER; c.downMin = 0.2f; c.downMax = 0.8f; c.getUpTries = 8; c.getUpStrength = 1.4f;
    return c;
}

SydneyConfig SydneyConfig::giant() {
    SydneyConfig c;
    c.maxSteps = 8; c.brace = 0.3f; c.holdWound = false; c.strength = 1.6f; c.downMin = 0.6f; c.downMax = 2;
    c.style = INFECTED;
    return c;
}

void SydneyConfig::from(const astratto_sydney_profile& p) {
    for (int i = 0; i < 6; i++) { freq[i] = p.freq[i]; zeta[i] = p.zeta[i]; torque[i] = p.torque[i]; }
    strength = p.strength; stanceGain = p.stance_gain; standPosture = p.stand_posture; stanceHip = p.stance_hip; carryHip = p.carry_hip; carryPosture = p.carry_posture;
    ankleLever = p.ankle_lever; balanceGain = p.balance_gain; stepThreshold = p.step_threshold; stepTime = p.step_time; stepReach = p.step_reach; stepLift = p.step_lift;
    stepOvershoot = p.step_overshoot; stepLead = p.step_lead; stepForce = p.step_force;
    fallTilt = p.fall_tilt; fallHeight = p.fall_height; lostReach = p.lost_reach; recoverSettle = p.recover_settle;
    fallStrength = p.fall_strength; brace = p.brace; downStrength = p.down_strength; downMin = p.down_min; downPerForce = p.down_per_force; downMax = p.down_max; holdForce = p.hold_force;
    getUpTime = std::max(0.05f, p.get_up_time); getUpStrength = p.get_up_strength; getUpTimeout = p.get_up_timeout;
    deathFade = p.death_fade; deathPosture = p.death_posture; crawlHeight = p.crawl_height;
    maxSteps = static_cast<int>(p.max_steps); getUpTries = static_cast<int>(p.get_up_tries); holdWound = p.hold_wound != 0; style = static_cast<int>(p.style);
}

// ------------------------------------------------------------------ the character
void SydneyCharacter::Goals::clear() {
    for (int j = 0; j < JOINTS; j++) { target[j] = Q(); gain[j] = 0; damp[j] = 0; cocon[j] = 0; world[j] = false; worldTarget[j] = Q(); }
    balance = false; stance[0] = stance[1] = false; torso = false; gravityComp = true;
    torsoTarget = Q(); torsoGain = 1; heightWant = 0; heightGain = 0; balanceGain = 1; hasCopRef = false; hasVelRef = false; hasWalkAxis = false;
    for (int e = 0; e < 4; e++) { eff[e] = false; effHz[e] = 0; effMax[e] = 0; effVel[e] = V3(); }
    lift = 0; liftHeight = 0; liftDir = V3(0, 1, 0);
    assist = false; assistMax = 0;
    tone = 1; reflex = 1;
}

SydneyCharacter::SydneyCharacter(Ragdoll* r, const SydneyConfig& c) : body(r), cfg(c) {
    r->driver = this;
    r->live = true;
    // the muscles: an antagonistic pair on each axis a joint turns about
    const float gscale = std::max(0.3f, r->gravity / G0), m = r->scale * r->scale * r->scale * r->scale;   // torque goes as mass x length
    for (int j = 0; j < JOINTS; j++) {
        const JointMuscles& jm = MUSCLES[j];
        const int dof = Ragdoll::jointDof(j);
        const float cls = cfg.torque[jm.cls] / DEFAULT_TORQUE[jm.cls];
        for (int a = 0; a < AXES; a++) {
            const AxisDef& d = jm.ax[a];
            for (int k = 0; k < 2; k++) {
                Muscle& mu = muscle[j][a][k];
                mu.sign = k == 0 ? 1.0f : -1.0f;
                if (a >= dof) { mu.Fr = 0; continue; }
                const float c0 = (d.lo + d.hi) * 0.5f, half = std::max(0.2f, (d.hi - d.lo) * 0.5f);
                mu.Fr = (k == 0 ? d.pos : d.neg) * gscale * m * cls;
                mu.k = 0.35f / half;
                mu.thetaOpt = c0;
                mu.slack = 1 + 0.35f * 0.76f;
            }
        }
    }
    // the rest height of the centre of mass over the feet
    s.standHeight = 0.8735f * r->scale;
    cpg[0].x[0] = 0.12f; cpg[1].x[1] = 0.12f;
    g_.clear();
    sense(nullptr);
}

void SydneyCharacter::setBody(uint32_t broken) {
    armL = (broken & 1u) == 0; armR = (broken & 2u) == 0; legL = (broken & 4u) == 0; legR = (broken & 8u) == 0; head = (broken & 16u) == 0;
}

void SydneyCharacter::setIntent(const SydneyIntent& i) { intent = i; }

float SydneyCharacter::rand01() { rng_ = rng_ * 6364136223846793005ull + 1442695040888963407ull; return static_cast<float>((rng_ >> 40) & 0xFFFFFF) / 16777216.0f; }

// ------------------------------------------------------------------ the senses
void SydneyCharacter::sense(const VoxelWorld* w) {
    Ragdoll& r = *body;
    s.mass = r.totalMass;
    s.weight = r.totalMass * r.gravity;
    const V3 com = r.com();
    V3 comV;
    for (int p = 0; p < PARTS; p++) comV += partV_[p] * r.partMass[p];
    comV = comV / r.totalMass;
    if (primed) s.jolt = std::max(s.jolt * 0.9f, len(comV - s.comV) / syd::STEP * 0.002f);
    s.com = com; s.comV = comV;
    s.speed = len(comV);
    const M3 T = r.body(TORSO).R;
    s.right = T.col(0); s.up = T.col(1); s.fwd = -T.col(2);
    s.headUp = r.body(HEAD).R.col(1);
    s.tilt = std::acos(clampf(s.up.y, -1, 1));
    s.supine = s.fwd.y > 0.55f; s.prone = s.fwd.y < -0.55f;
    // the feet: which carry weight, and the support they make
    s.feet = 0;
    float xs[8], zs[8];
    int k = 0;
    V3 cop; float sup = 0;
    for (int f = 0; f < 2; f++) {
        const Body& b = r.body(FOOT[f]);
        const bool works = f == 0 ? legL : legR;
        s.support[f] = b.support;
        s.foot[f] = works && (b.support > s.weight * 0.03f || (b.groundPoints > 0 && b.support > 0));
        s.footFlat[f] = b.R.col(1).y > 0.8f;
        if (!s.foot[f]) continue;
        s.feet++;
        cop += b.cop * b.support; sup += b.support;
        const V3 hx = b.R.col(0) * b.shape.half.x, hz = b.R.col(2) * b.shape.half.z;
        const V3 corners[4] = {b.p + hx + hz, b.p + hx - hz, b.p - hx + hz, b.p - hx - hz};
        for (const V3& c : corners) { xs[k] = c.x; zs[k] = c.z; k++; }
    }
    s.cop = sup > 0 ? cop / sup : com;
    s.otherContacts = 0; s.handsDown = 0;
    for (int p = 0; p < PARTS; p++) {
        if (p == L_FOOT || p == R_FOOT) continue;
        if (r.body(p).groundPoints > 0) { s.otherContacts++; if (p == L_FORE || p == R_FORE) s.handsDown++; }
    }
    // the ground under the body
    float gy = r.lowestY();
    if (w != nullptr) {
        const int ix = static_cast<int>(std::floor(com.x)), iz = static_cast<int>(std::floor(com.z)), top = static_cast<int>(std::floor(com.y));
        for (int iy = top; iy >= std::max(0, top - 6); iy--) if (w->solid(ix, iy, iz)) { gy = static_cast<float>(iy + 1); break; }
    }
    if (s.feet > 0) { float fy = -1e30f; for (int f = 0; f < 2; f++) if (s.foot[f]) fy = std::max(fy, r.body(FOOT[f]).p.y - r.body(FOOT[f]).shape.half.y); gy = fy; }
    s.groundY = gy;
    s.height = com.y - gy;
    s.omega = std::sqrt(r.gravity / std::max(0.3f * r.scale, s.height));
    s.capture = V3(com.x + comV.x / s.omega, gy, com.z + comV.z / s.omega);
    Poly poly;
    if (k > 0) poly.hull(xs, zs, k);
    float nx, nz;
    s.captureOut = k > 0 ? poly.outside(s.capture.x, s.capture.z, nx, nz) : 1.0f;
    float cx = 0, cz = 0;
    for (int i = 0; i < k; i++) { cx += xs[i]; cz += zs[i]; }
    s.supportCentre = k > 0 ? V3(cx / k, gy, cz / k) : V3(com.x, gy, com.z);
    balanceErr = s.captureOut;
}

// ------------------------------------------------------------------ joints, torques
void SydneyCharacter::jointState(int j, Q& rel, V3& theta, V3& omega) const { rel = rel_[j]; theta = theta_[j]; omega = omega_[j]; }

void SydneyCharacter::proprioception(float h) {
    // each joint's rotation, and how fast it is changing: read as the spindles read it, from the change in the joint
    // since the last step (the bodies' own angular velocities carry the solver's drift correction, which is no motion)
    const Ragdoll& r = *body;
    for (int j = 0; j < JOINTS; j++) {
        const Body& P = r.body(Ragdoll::jointParent(j));
        const Body& C = r.body(Ragdoll::jointChild(j));
        Q rel = conj(P.q) * C.q;
        if (rel.w < 0) rel = Q(-rel.w, -rel.x, -rel.y, -rel.z);
        const V3 w = haveRel_ && h > 0 ? rotationVector(rel_[j], rel) * (1.0f / h) : V3();
        // a light filter on the rate (the spindles' own dynamics): the step-to-step reading is noisy at contact
        omega_[j] = haveRel_ ? omega_[j] + (w - omega_[j]) * 0.6f : w;
        rel_[j] = rel;
        theta_[j] = rotationVector(Q(), rel);
    }
    // and each segment's motion, the same way (the vestibular sense of the head and trunk, the limbs' kinaesthesia)
    for (int p = 0; p < PARTS; p++) {
        const Body& b = r.body(p);
        const V3 v = haveRel_ && h > 0 ? (b.p - lastP_[p]) * (1.0f / h) : b.v;
        const V3 w = haveRel_ && h > 0 ? rotationVector(lastQ_[p], b.q) * (1.0f / h) : b.w;
        partV_[p] = haveRel_ ? partV_[p] + (v - partV_[p]) * 0.6f : v;
        partW_[p] = haveRel_ ? partW_[p] + (w - partW_[p]) * 0.6f : w;
        lastP_[p] = b.p; lastQ_[p] = b.q;
    }
    haveRel_ = true;
}

void SydneyCharacter::applyTorque(int j, V3 tau, float h) {
    Ragdoll& r = *body;
    Body& P = r.body(Ragdoll::jointParent(j));
    Body& C = r.body(Ragdoll::jointChild(j));
    const V3 tw = P.R * tau;
    C.w += C.invI * tw * h;
    P.w -= P.invI * tw * h;
}

void SydneyCharacter::jointTorqueWorld(int j, V3 tauWorld, V3* acc) { acc[j] += tauWorld; }

V3 SydneyCharacter::effector(int e) const {
    const Ragdoll& r = *body;
    if (e < 2) return r.body(FORE[e]).toWorld(V3(0, -0.15f * r.scale, 0));
    const Body& f = r.body(FOOT[e - 2]);
    return f.p - f.R.col(1) * f.shape.half.y;
}

V3 SydneyCharacter::effectorVel(int e) const {
    const Ragdoll& r = *body;
    const Body& b = e < 2 ? r.body(FORE[e]) : r.body(FOOT[e - 2]);
    const int p = e < 2 ? FORE[e] : FOOT[e - 2];
    return partV_[p] + cross(partW_[p], effector(e) - b.p);
}

void SydneyCharacter::effectorForce(int e, V3 F, V3* acc) {
    // Jacobian transpose: each joint between the trunk and the hand (or foot) turns by the moment of the force about it
    const Ragdoll& r = *body;
    const V3 x = effector(e);
    const int* chain = e < 2 ? ARM[e] : LEG[e - 2];
    const int n = 2;
    for (int i = 0; i < n; i++) acc[chain[i]] += cross(x - r.jointPoint(chain[i]), F);
}

// ------------------------------------------------------------------ the LQR on the inverted pendulum
void SydneyCharacter::solveLqr() {
    // x = [offset of the centre of mass, its speed]; u = the centre of pressure's offset. x'' = w^2 (x - u).
    const float w2 = s.omega * s.omega, h = syd::STEP;
    const float A[2][2] = {{1, h}, {w2 * h, 1}}, B[2] = {0, -w2 * h};
    const float bg = std::max(0.2f, cfg.balanceGain * traits.agility);
    const float Q0 = 1.0f, Q1 = 0.05f, R = 2.0f / bg;   // (gives about u = 2.4 x + 0.5 x' for an adult: a firm capture-point law)
    float P[2][2] = {{Q0, 0}, {0, Q1}};
    float K0 = 0, K1 = 0;
    for (int it = 0; it < 4000; it++) {
        // K = (R + B'PB)^-1 B'PA
        const float PB0 = P[0][0] * B[0] + P[0][1] * B[1], PB1 = P[1][0] * B[0] + P[1][1] * B[1];
        const float BPB = B[0] * PB0 + B[1] * PB1;
        const float BPA0 = PB0 * A[0][0] + PB1 * A[1][0], BPA1 = PB0 * A[0][1] + PB1 * A[1][1];
        const float nK0 = BPA0 / (R + BPB), nK1 = BPA1 / (R + BPB);
        // P = Q + A'PA - A'PB K
        float AP[2][2];
        for (int i = 0; i < 2; i++) for (int k = 0; k < 2; k++) AP[i][k] = A[0][i] * P[0][k] + A[1][i] * P[1][k];
        float APA[2][2];
        for (int i = 0; i < 2; i++) for (int k = 0; k < 2; k++) APA[i][k] = AP[i][0] * A[0][k] + AP[i][1] * A[1][k];
        const float APB0 = AP[0][0] * B[0] + AP[0][1] * B[1], APB1 = AP[1][0] * B[0] + AP[1][1] * B[1];
        float Pn[2][2] = {{Q0 + APA[0][0] - APB0 * nK0, APA[0][1] - APB0 * nK1}, {APA[1][0] - APB1 * nK0, Q1 + APA[1][1] - APB1 * nK1}};
        const float d = std::fabs(nK0 - K0) + std::fabs(nK1 - K1);
        std::memcpy(P, Pn, sizeof P);
        K0 = nK0; K1 = nK1;
        if (it > 50 && d < 1e-7f) break;
    }
    lqrK_[0] = K0; lqrK_[1] = K1;
    lqrOmega_ = s.omega;
}

// ------------------------------------------------------------------ the step
void SydneyCharacter::drive(const VoxelWorld* w, float h) {
    if (!primed) haveRel_ = false;
    proprioception(h);
    sense(w);
    primed = true;
    coordinate(w, h);
    supraspinal(h);
    spinal(h);
    assist(h);
    Ragdoll& r = *body;
    for (int p = 0; p < PARTS; p++) {
        Body& b = r.body(p);
        if (!std::isfinite(b.w.x) || !std::isfinite(b.w.y) || !std::isfinite(b.w.z) || !std::isfinite(b.v.x) || !std::isfinite(b.p.y)) { invalid = true; b.w = V3(); b.v = V3(); }
    }
    // death: when the last of the tone has drained the body is an ordinary ragdoll again and may come to rest
    if (outcome == DEAD_OUTCOME) release();
}

// ---- supraspinal: balance, the stance legs, the torso, the hands and feet
void SydneyCharacter::supraspinal(float h) {
    (void)h;
    Ragdoll& r = *body;
    for (int j = 0; j < JOINTS; j++) wantTorque[j][0] = wantTorque[j][1] = wantTorque[j][2] = 0;
    V3 acc[JOINTS];
    const float gy = r.gravity;
    const float tone = g_.tone;
    // gravity compensation: every limb that hangs free is held up against its own weight
    if (g_.gravityComp && tone > 0) {
        for (int j = 0; j < JOINTS; j++) {
            const bool stanceLeg = (j == L_HIP || j == L_KNEE || j == L_ANKLE) ? g_.stance[0] : (j == R_HIP || j == R_KNEE || j == R_ANKLE) ? g_.stance[1] : false;
            if (stanceLeg || g_.gain[j] <= 0) continue;
            const V3 pj = r.jointPoint(j);
            V3 tq;
            for (int k = 0; DISTAL[j][k] >= 0; k++) { const int p = DISTAL[j][k]; tq += cross(r.body(p).p - pj, V3(0, r.partMass[p] * gy, 0)); }
            acc[j] += tq * std::min(1.0f, g_.gain[j] * 2.5f) * tone;
        }
    }
    // the joint servos: a damped spring toward each wanted rotation, as stiff as the behaviour asks, scaled by what
    // the joint carries. (Held upright, the spine and the stance hips answer to the torso instead.) They are not
    // torques here: the spinal layer hands them to Impulsis as each joint's drive, solved implicitly within what the
    // muscles can give this step.
    for (int j = 0; j < JOINTS; j++) {
        damping_[j] = 0; servoKp_[j] = 0; servoE_[j] = V3();
        const float gain = g_.gain[j];
        if (tone <= 0) continue;
        const bool stanceLeg = ((j == L_HIP || j == L_KNEE || j == L_ANKLE) && g_.stance[0] && s.foot[0]) || ((j == R_HIP || j == R_KNEE || j == R_ANKLE) && g_.stance[1] && s.foot[1]);
        const bool byTorso = g_.torso && ((j == SPINE && cfg.rootMode == 0) || ((j == L_HIP || j == R_HIP) && stanceLeg));
        const int cls = MUSCLES[j].cls;
        const V3 pj = r.jointPoint(j);
        float I = 0;
        for (int k = 0; DISTAL[j][k] >= 0; k++) { const int p = DISTAL[j][k]; I += r.partMass[p] * (len2(r.body(p).p - pj) + 0.01f * r.scale * r.scale); }
        const float f = cfg.freq[cls] * pace() * std::sqrt(std::max(0.25f, traits.agility)), wv = 2 * PI * f;
        const float gd = byTorso ? 0.35f : gain;
        const float gainUsed = (j == SPINE && g_.torso && cfg.rootMode == 1) ? std::max(gain, cfg.spineStiff) : gain;
        damping_[j] = 2 * cfg.zeta[cls] * I * wv * std::sqrt(gd) * (1 + g_.damp[j]) * (stanceLeg ? 1.2f : 1.0f) * tone;
        if (gainUsed <= 0 || byTorso) continue;
        Q rel; V3 th, om;
        jointState(j, rel, th, om);
        servoKp_[j] = I * wv * wv * gainUsed * (stanceLeg ? cfg.stanceGain : 1.0f) * tone;
        if (g_.world[j]) {
            const Body& P = r.body(Ragdoll::jointParent(j));
            const Body& C = r.body(Ragdoll::jointChild(j));
            servoE_[j] = transpose(P.R) * rotationVector(C.q, g_.worldTarget[j]);
        } else servoE_[j] = rotationVector(rel, g_.target[j]);
    }
    // balance: the LQR picks the centre of pressure for the centre of mass's state; virtual-model control turns the
    // ground force that gives into torques at each stance leg's ankle, knee and hip
    const int stanceN = (g_.stance[0] && s.foot[0] ? 1 : 0) + (g_.stance[1] && s.foot[1] ? 1 : 0);
    V3 copWant = s.cop;
    if (g_.balance && stanceN > 0 && tone > 0) {
        if (lqrOmega_ < 0 || std::fabs(lqrOmega_ - s.omega) > 0.15f) solveLqr();
        const V3 ref = g_.hasVelRef ? s.com : g_.hasCopRef ? g_.copRef : s.supportCentre;
        const float ex = s.com.x - ref.x, ez = s.com.z - ref.z;
        // walking: the velocity wanted is never far ahead of the one there is (the body speeds up over a few strides
        // instead of being thrown forward by its ankles)
        V3 vRef = g_.velRef;
        if (g_.hasVelRef) {
            const V3 dv = flat(g_.velRef - s.comV);
            const float dl = hlen(dv), lim = 0.3f * pace();
            vRef = dl > lim ? flat(s.comV) + dv * (lim / dl) : g_.velRef;
        }
        const V3 vErr = g_.hasVelRef ? s.comV - vRef : s.comV;
        // u = -K x: the centre of pressure's offset from the reference (the behaviour may soften it toward the centre
        // of mass itself, which corrects nothing)
        const float bg = g_.balanceGain;
        float ux = -(lqrK_[0] * ex + lqrK_[1] * vErr.x), uz = -(lqrK_[0] * ez + lqrK_[1] * vErr.z);
        ux = ex + (ux - ex) * bg; uz = ez + (uz - ez) * bg;
        if (g_.hasWalkAxis) {
            // walking: the centre of pressure stays at the stance foot - the pendulum the steps are planned on (the
            // capture point is predicted about a fixed foot); the feet, not the ankle, keep the balance
            ux = 0; uz = 0;
        }
        copWant = V3(ref.x + ux, s.groundY, ref.z + uz);
        copWanted = copWant;
        // the force on the body that centre of pressure gives (the pendulum's), and the height held
        const float w2 = s.omega * s.omega;
        const float M = s.mass;
        V3 G(M * w2 * (s.com.x - copWant.x), 0, M * w2 * (s.com.z - copWant.z));
        const float hk = g_.heightGain;
        G.y = M * gy + (hk > 0 ? M * (40.0f * hk * (g_.heightWant - s.height) - 9.0f * std::sqrt(hk) * s.comV.y) : 0);
        G.y = std::max(0.0f, G.y);
        // horizontal force is limited by friction and by what the feet can push
        const float hl = hlen(G), hmax = G.y * 0.9f;
        if (hl > hmax && hl > 1e-6f) { G.x *= hmax / hl; G.z *= hmax / hl; }
        // shared between the stance feet by where the centre of pressure falls between them
        float share[2] = {0, 0};
        if (stanceN == 2) {
            const V3 a = r.body(L_FOOT).p, b = r.body(R_FOOT).p, ab = flat(b - a);
            const float tt = len2(ab) > 1e-6f ? clampf(dot(flat(copWant - a), ab) / len2(ab), 0.05f, 0.95f) : 0.5f;
            share[0] = 1 - tt; share[1] = tt;
        } else { share[g_.stance[0] && s.foot[0] ? 0 : 1] = 1; }
        for (int f = 0; f < 2; f++) {
            if (share[f] <= 0) continue;
            const Body& foot = r.body(FOOT[f]);
            // the centre of pressure under this foot: the wanted one, held within the sole
            V3 lp = transpose(foot.R) * (copWant - foot.p);
            lp.x = clampf(lp.x, -foot.shape.half.x * 0.9f, foot.shape.half.x * 0.9f);
            lp.z = clampf(lp.z, -foot.shape.half.z * 0.92f, foot.shape.half.z * 0.92f);
            lp.y = -foot.shape.half.y;
            const V3 c = foot.toWorld(lp);
            const V3 Gf = G * share[f];
            for (int k = 0; k < 3; k++) {
                const int j = LEG[f][k];
                const V3 pj = r.jointPoint(j);
                V3 tq = -cross(c - pj, Gf);
                // and the weight of the leg below the joint, which the ground holds too
                for (int q = 0; DISTAL[j][q] >= 0; q++) { const int p = DISTAL[j][q]; tq += cross(r.body(p).p - pj, V3(0, r.partMass[p] * gy, 0)); }
                acc[j] += tq * tone;
            }
        }
    }
    // the hands and feet that are sent somewhere: a damped spring on each, through its limb (Jacobian transpose)
    for (int e = 0; e < 4; e++) {
        if (!g_.eff[e] || tone <= 0) continue;
        if ((e == 0 && !armL) || (e == 1 && !armR) || (e == 2 && !legL) || (e == 3 && !legR)) continue;
        const float wv = 2 * PI * g_.effHz[e];
        const float m = e < 2 ? (r.partMass[L_UPPER] + r.partMass[L_FORE]) : (r.partMass[L_THIGH] + r.partMass[L_SHIN] + r.partMass[L_FOOT]);
        V3 F = (g_.effAt[e] - effector(e)) * (m * wv * wv) - (effectorVel(e) - g_.effVel[e]) * (m * 2 * 0.9f * wv);
        const float fl = len(F), fmax = g_.effMax[e] * r.scale * r.scale * r.scale;
        if (fl > fmax && fl > 1e-6f) F = F * (fmax / fl);
        effectorForce(e, F * tone, acc);
    }
    // a lift through whatever touches the ground (getting up): the trunk pushed toward a height by the limbs braced
    // on the ground, each limb turning its joints by the moment of its share about them
    if (g_.lift > 0 && tone > 0) {
        static const int limbs[4][3] = {{L_SHOULDER, L_ELBOW, L_FORE}, {R_SHOULDER, R_ELBOW, R_FORE}, {L_HIP, L_KNEE, L_SHIN}, {R_HIP, R_KNEE, R_SHIN}};
        const bool works[4] = {armL, armR, legL, legR};
        int n = 0;
        bool down[4] = {};
        for (int l = 0; l < 4; l++) {
            const int seg = limbs[l][2];
            const bool d = works[l] && (r.body(seg).groundPoints > 0 || (l >= 2 && r.body(FOOT[l - 2]).groundPoints > 0));
            down[l] = d; if (d) n++;
        }
        if (n > 0) {
            const float M = s.mass, hk = 1.0f;
            float Fy = M * gy + M * (36.0f * hk * (g_.liftHeight - s.height) - 10.0f * s.comV.y);
            Fy = clampf(Fy, 0, M * gy * cfg.getUpStrength * cfg.strength * traits.strength * g_.lift);
            const V3 F = g_.liftDir * Fy;
            for (int l = 0; l < 4; l++) {
                if (!down[l]) continue;
                const int seg = limbs[l][2];
                const Body& b = r.body(l >= 2 && r.body(FOOT[l - 2]).groundPoints > 0 ? FOOT[l - 2] : seg);
                const V3 c = b.groundPoints > 0 ? b.cop : b.p;
                for (int k = 0; k < 2; k++) {
                    const int j = limbs[l][k];
                    acc[j] += -cross(c - r.jointPoint(j), F * (1.0f / n)) * tone;
                }
            }
        }
    }
    // the torso held upright in the world by the spine, and the pelvis by the stance hips (the stance hips also take
    // what the spine and the swing hip put into the pelvis, so that only the ground's force moves the body)
    if (g_.torso && tone > 0 && cfg.rootMode == 1) {
        // the root (pelvis and torso, held together by the spine's servo) turned in the world by the stance hips alone:
        // tau_stance = -tau_root - tau_swing (Yin et al. 2007; Coros et al. 2010). Their own virtual-model share is
        // replaced: it would only turn the root, which is not fixed.
        const Body& T = r.body(TORSO);
        const float Ir = (r.partMass[TORSO] + r.partMass[HEAD] + r.partMass[PELVIS] + 2 * (r.partMass[L_UPPER] + r.partMass[L_FORE])) * 0.28f * 0.28f * r.scale * r.scale;
        const float wf = 2 * PI * cfg.freq[SydneyConfig::SPINE] * pace() * std::sqrt(g_.torsoGain), zt = cfg.zeta[SydneyConfig::SPINE];
        const V3 e = rotationVector(T.q, g_.torsoTarget);
        const V3 tR = e * (Ir * wf * wf) - partW_[TORSO] * (2 * zt * Ir * wf);
        V3 swing;
        for (int f = 0; f < 2; f++) if (!(g_.stance[f] && s.foot[f])) swing += acc[LEG[f][0]];
        const V3 need = -(tR * tone + swing);
        float share[2] = {s.support[0], s.support[1]};
        for (int f = 0; f < 2; f++) if (!(g_.stance[f] && s.foot[f])) share[f] = 0;
        const float tot = share[0] + share[1];
        // (the roll - about the pelvis's own fore-and-aft axis - keeps the leg's virtual-model share: it is what moves
        // the weight from one foot to the other)
        const V3 roll = r.body(PELVIS).R.col(2);
        if (tot > 1e-3f) for (int f = 0; f < 2; f++) if (share[f] > 0) {
            const V3 keep = roll * (dot(acc[LEG[f][0]], roll) * cfg.hipRollKeep);
            acc[LEG[f][0]] = need * (share[f] / tot) + keep;
        }
    } else if (g_.torso && tone > 0) {
        const Body& T = r.body(TORSO);
        const Body& Pv = r.body(PELVIS);
        const float Iu = (r.partMass[TORSO] + r.partMass[HEAD] + 2 * (r.partMass[L_UPPER] + r.partMass[L_FORE])) * 0.30f * 0.30f * r.scale * r.scale;
        const float wf = 2 * PI * cfg.freq[SydneyConfig::SPINE] * pace() * std::sqrt(g_.torsoGain), zt = cfg.zeta[SydneyConfig::SPINE];
        const V3 eT = rotationVector(T.q, g_.torsoTarget);
        const V3 tT = eT * (Iu * wf * wf) - partW_[TORSO] * (2 * zt * Iu * wf);
        acc[SPINE] += tT * tone;
        const float Ip = (r.partMass[PELVIS] * 0.2f * 0.2f * r.scale * r.scale + Iu * 0.5f) * cfg.pelvisGain;
        const Q pelvisWant = g_.torsoTarget;
        const V3 eP = rotationVector(Pv.q, pelvisWant);
        const V3 tP = eP * (Ip * wf * wf) - partW_[PELVIS] * (2 * zt * Ip * wf);
        // what the spine and the swing hip do to the pelvis (each pushes back on it as it turns its child)
        V3 swing;
        for (int f = 0; f < 2; f++) if (!(g_.stance[f] && s.foot[f])) swing += acc[LEG[f][0]];
        const V3 need = -(tP * tone + acc[SPINE] + swing);
        float share[2] = {s.support[0], s.support[1]};
        for (int f = 0; f < 2; f++) if (!(g_.stance[f] && s.foot[f])) share[f] = 0;
        const float tot = share[0] + share[1];
        if (tot > 1e-3f) for (int f = 0; f < 2; f++) acc[LEG[f][0]] += need * (share[f] / tot);
    }
    // into each joint's own frame, for the spinal layer
    for (int j = 0; j < JOINTS; j++) {
        const V3 tq = transpose(r.body(Ragdoll::jointParent(j)).R) * acc[j];
        wantTorque[j][0] = tq.x; wantTorque[j][1] = tq.y; wantTorque[j][2] = tq.z;
    }
}

// ---- spinal: the muscles. Each axis of each joint has the explicit torque the controllers asked for (balance, gravity,
// reaching - its feed-forward) and a servo. The muscles give the feed-forward; the servo becomes the joint's drive in
// Impulsis, a damped pull toward the servo's speed solved implicitly, bounded by what the muscles have left to give
// this step - their Hill curves at this length and speed, and the activation they can reach (activation dynamics rate
// the force). The activations are then set (static optimisation: least squared activation, with the co-contraction
// asked for) for the torque the joint is giving, feed-forward and drive together, so that they drive the next bound.
void SydneyCharacter::spinal(float h) {
    float sumA = 0;
    int nA = 0;
    const float strength = cfg.strength * traits.strength;
    const float rise = 1 - std::exp(-h / TAU_ACT);
    for (int j = 0; j < JOINTS; j++) {
        Q rel; V3 th, om;
        jointState(j, rel, th, om);
        const int dof = Ragdoll::jointDof(j);
        bool works = true;
        if (j == L_SHOULDER || j == L_ELBOW) works = armL;
        else if (j == R_SHOULDER || j == R_ELBOW) works = armR;
        else if (j == L_HIP || j == L_KNEE || j == L_ANKLE) works = legL;
        else if (j == R_HIP || j == R_KNEE || j == R_ANKLE) works = legR;
        else if (j == NECK) works = head;
        impulsis::Joint* jt = body->space.joint(body->jointHandle[j]);
        const V3 want(wantTorque[j][0], wantTorque[j][1], wantTorque[j][2]);
        float got[3] = {0, 0, 0};
        for (int a = 0; a < 3; a++) {
            if (a >= dof) { if (jt) { jt->damp[a] = 0; jt->Pdamp[a] = 0; } continue; }
            Muscle& mp = muscle[j][a][0];
            Muscle& mn = muscle[j][a][1];
            const float theta = a == 0 ? th.x : a == 1 ? th.y : th.z, omega = a == 0 ? om.x : a == 1 ? om.y : om.z;
            mp.sense(theta, omega); mn.sense(theta, omega);
            const float cap = std::max(1e-3f, mp.capacity() * strength), can = std::max(1e-3f, mn.capacity() * strength);
            const float pas = mp.passive() + mn.passive();
            const float tone = works ? g_.tone : 0;
            // what each side can reach this step
            const float reachP = tone > 0 ? std::min(1.0f, mp.a + (1 - mp.a) * rise) : mp.a * 0.5f;
            const float reachN = tone > 0 ? std::min(1.0f, mn.a + (1 - mn.a) * rise) : mn.a * 0.5f;
            const float hi = reachP * cap * tone + pas, lo = -reachN * can * tone + pas;
            // the stretch reflex: a muscle stretched fast a moment ago (its spindles, 21 ms back) is excited
            const float rg = g_.reflex * 0.6f * traits.agility * tone;
            const float reflex = rg * (std::max(0.0f, mp.delayedV(5) - 0.08f) * cap - std::max(0.0f, mn.delayedV(5) - 0.08f) * can);
            // the feed-forward, as far as the muscles reach
            const float ff = clampf((a == 0 ? want.x : a == 1 ? want.y : want.z) + reflex, lo, hi);
            got[a] = ff;
            // the servo, as the joint's drive
            float drive = 0;
            if (jt) {
                const float kd = damping_[j] * (works ? 1.0f : 0.0f) + 0.004f * (mp.Fr + mn.Fr) * 0.5f;
                const float e = a == 0 ? servoE_[j].x : a == 1 ? servoE_[j].y : servoE_[j].z;
                jt->damp[a] = kd;
                jt->dampTarget[a] = servoKp_[j] > 0 && kd > 1e-4f ? clampf(servoKp_[j] / kd * e, -14.0f, 14.0f) : 0.0f;
                jt->dampHi[a] = std::max(0.0f, hi - ff) + 0.004f * cap;
                jt->dampLo[a] = std::min(0.0f, lo - ff) - 0.004f * can;
                drive = jt->Pdamp[a] / h;      // last step's: what the joint gave beyond the feed-forward
            }
            // the activations for the torque the joint is giving (feed-forward and drive), with the co-contraction
            const float co = clampf(g_.cocon[j], 0, 1) * 0.6f;
            const float need = ff + drive - pas;
            float ap, an;
            if (need >= 0) { an = co; ap = (need + co * can) / cap; } else { ap = co; an = (-need + co * cap) / can; }
            const float floorA = 0.01f * tone;
            ap = clampf(ap * tone, floorA, 1); an = clampf(an * tone, floorA, 1);
            for (int k = 0; k < 2; k++) {
                Muscle& m = muscle[j][a][k];
                const float aw = k == 0 ? ap : an;
                const float tau = aw > m.a ? TAU_ACT * (0.5f + 1.5f * m.a) : TAU_DEACT / (0.5f + 1.5f * m.a);
                m.u = clampf(m.a + (aw - m.a) * (tau / h) * 0.85f, 0, 1);
                m.activate(h, TAU_ACT, TAU_DEACT);
                sumA += m.a; nA++;
            }
        }
        gotTorque[j][0] = got[0]; gotTorque[j][1] = got[1]; gotTorque[j][2] = got[2];
        applyTorque(j, V3(got[0], got[1], got[2]), h);
        if (jt) for (int a = 0; a < dof; a++) gotTorque[j][a] += jt->Pdamp[a] / h;
    }
    effort = nA > 0 ? sumA / nA : 0;
}

// ---- the get-up's root support: a bounded pull of the pelvis and trunk toward where the get-up's reference motion
// has them (height, and the trunk's turn), applied from outside the body. What the simplified skeleton's limbs cannot
// push - a person rolls and presses up with hands, forearms, knees and toes that these thirteen segments do not have -
// this supplies, up to a share of the body's weight; the muscles do the rest, and the body stays a body: it collides,
// it can be struck down again, and once upright it balances itself.
void SydneyCharacter::assist(float h) {
    if (!g_.assist || (state != GETUP && state != STAND && state != STEP)) return;
    Ragdoll& r = *body;
    Body& P = r.body(PELVIS);
    Body& T = r.body(TORSO);
    const float M = s.mass, w0 = 2 * PI * 2.2f;
    const V3 pv = partV_[PELVIS];
    V3 F = (g_.assistAt - P.p) * (M * w0 * w0) - pv * (2 * 1.0f * M * w0);
    F.y += M * r.gravity * std::min(0.5f, g_.assistMax);   // half the weight is expected of the support at most
    const float fmax = M * r.gravity * g_.assistMax, fl = len(F);
    if (fl > fmax && fl > 1e-6f) F = F * (fmax / fl);
    const float mp = r.partMass[PELVIS], mt = r.partMass[TORSO];
    P.v += F * (h * 0.55f / mp);
    T.v += F * (h * 0.45f / mt);
    // the trunk turned toward the reference's turn
    const float It = (mt + r.partMass[HEAD]) * 0.3f * 0.3f * r.scale * r.scale, wr = 2 * PI * 1.6f;
    V3 tq = rotationVector(T.q, g_.assistQ) * (It * wr * wr) - partW_[TORSO] * (2 * It * wr);
    const float tmax = 260.0f * r.scale * r.scale * r.scale * r.scale * g_.assistMax / 0.7f, tl = len(tq);
    if (tl > tmax && tl > 1e-6f) tq = tq * (tmax / tl);
    T.w += T.invI * tq * h;
    P.w += P.invI * tq * (h * 0.5f);
    assistUsed = fl / std::max(1.0f, M * r.gravity);
}

// ------------------------------------------------------------------ the coordinator
void SydneyCharacter::hit(int point, float dx, float dy, float dz, float impulse, float force) {
    Ragdoll& r = *body;
    const float l = std::sqrt(dx * dx + dy * dy + dz * dz);
    if (l < 1e-5f || point < 0 || point >= rd::N) return;
    const V3 d(dx / l, dy / l, dz / l);
    const int seg = Ragdoll::pointPart(point);
    r.strike(seg, r.pointWorld(point), d * impulse, 6.0f);
    wound = point;
    settled = 0;
    const float tough = std::max(0.2f, traits.toughness);
    stun = std::max(stun, std::min(1.2f, force / 110.0f / tough));
    downFor = std::max(downFor, std::min(cfg.downMax, cfg.downMin + force * cfg.downPerForce / tough));
    // the startle: the nervous system answers a moment later (quicker for the agile)
    hitDelay_ = 0.12f / std::max(0.3f, traits.agility);
    hitDir_ = d;
    pending_ = std::min(1.0f, force / 60.0f);
    if (outcome == RECOVERED) outcome = NONE;
}

void SydneyCharacter::die() {
    alive = false;
    if (state != DEAD) { releaseGrips(); go(DEAD); limpT_ = 0; outcome = NONE; }
    // Release may already have detached the driver; no future drive step can finish the death fade then.
    if (body->driver != this) release();
}

void SydneyCharacter::release() {
    releaseGrips();
    intent = SydneyIntent();
    activity = A_NONE;
    effort = 0; assistUsed = 0;
    // Impulsis retains these servo settings and warm-start impulses between steps. Dropping the driver alone
    // leaves the last balance/get-up drive running without any controller to update or turn it off.
    for (int j = 0; j < JOINTS; j++) {
        impulsis::Joint* joint = body->space.joint(body->jointHandle[j]);
        if (!joint) continue;
        for (int axis = 0; axis < AXES; axis++) {
            joint->damp[axis] = 0; joint->dampTarget[axis] = 0;
            joint->dampLo[axis] = 0; joint->dampHi[axis] = 0; joint->Pdamp[axis] = 0;
        }
    }
    if (!alive) outcome = DEAD_OUTCOME;
    body->live = false;
    body->driver = nullptr;
}

void SydneyCharacter::collapse() { if (state != DEAD && state != FALL && state != DOWN) fallNow(); }

void SydneyCharacter::coordinate(const VoxelWorld* w, float h) {
    t += h;
    stun = std::max(0.0f, stun - h);
    steady_ = state == STAND || state == STEP ? std::max(0.0f, steady_ - h) : 0.0f;
    hitDelay_ = std::max(0.0f, hitDelay_ - h);
    if (intent.time > 0) { intent.time -= h; if (intent.time <= 0) intent = SydneyIntent(); }
    airborne = s.feet == 0 && s.otherContacts == 0 ? airborne + h : 0;
    if (airborne > longestAir) longestAir = airborne;
    if (airborne <= 0) flailT_ = 0;
    const bool legs = legL && legR;
    const bool toppled = s.tilt > rad(cfg.fallTilt) || s.height < s.standHeight * cfg.fallHeight;
    if (!alive && state != DEAD) die();
    // the oscillators run all the time; their drive comes from the behaviour (none at rest: they fall silent)
    float drive = 0;
    if (state == STEP || (state == STAND && s.captureOut > 0)) drive = clampf(0.6f + s.captureOut * 6.0f, 0, 2.2f);
    if (activity == A_WALK) drive = 1.0f + clampf(hlen(V3(intent.x, 0, intent.z)) * 0.35f, 0, 1.2f);
    if (state == FALL && airborne > 0.22f) drive = 2.5f;
    if (activity == A_WRITHE) drive = 0.55f;
    for (int f = 0; f < 2; f++) {
        Oscillator& o = cpg[f];
        o.drive = drive;
        const float speedUp = 1.0f / std::max(0.4f, std::sqrt(traits.agility) * (state == FALL ? 2.0f : 1.0f));
        o.tauR = 0.07f * speedUp; o.tauA = 0.14f * speedUp;
        // the feet tell the rhythm where they are: a loaded foot holds its leg in stance (extensor), and the other leg
        // is inhibited from flexing while this one swings
        const float load = s.weight > 0 ? s.support[f] / s.weight : 0;
        const Oscillator& other = cpg[1 - f];
        o.step(h, 0, load * 0.8f, other.y(0) * 1.4f, 0);
    }

    switch (state) {
        case STAND: {
            if (!legs || toppled || airborne > 0.25f) { steady_ = 0; fallNow(); break; }
            const float thr = 0.035f * cfg.stepThreshold * body->scale;
            over = s.captureOut > thr && s.feet > 0 ? over + h : 0;
            // back inside the feet for a moment: the stagger is over, and the steps it took are forgotten
            inside_ = s.captureOut < 0 && s.feet == 2 ? inside_ + h : 0;
            if (inside_ > 0.25f) steps = 0;
            if (over > 0.03f) {
                over = 0;
                if (steps >= cfg.maxSteps || s.captureOut > cfg.lostReach * body->scale || !stepBegin(w)) fallNow();
                else { steps++; go(STEP); }
                break;
            }
            if (activity == A_WALK && s.feet == 2 && t > 0.05f) {
                // a walk starts with a step: the leg the oscillator favours (the other is in stance), then stride after
                // stride with no stand between
                stepSide_ = cpg[0].out() > cpg[1].out() ? 0 : 1;
                if (stepBegin(w)) { go(STEP); break; }
            }
            settled = std::fabs(s.captureOut) < 1e30f && s.captureOut < -0.02f * body->scale && s.speed < 0.35f && s.feet == 2 ? settled + h : 0;
            if (settled >= cfg.recoverSettle && stun <= 0 && hitDelay_ <= 0) { if (outcome != RECOVERED) recoveries++; outcome = RECOVERED; steps = 0; }
            break;
        }
        case STEP:
            if (toppled || !legs) { fallNow(); break; }
            if (stepLanded()) {
                // the struck heel resets the rhythm: this leg into stance, the other free to swing next
                Oscillator& o = cpg[stepSide_];
                o.x[0] = 0; o.x[1] = 0.6f; o.v[0] = 0.5f;
                if (activity == A_WALK && legs && s.feet > 0) {
                    // walking: the other foot goes as this one lands - no stand between strides
                    steps = 0;
                    stepSide_ = 1 - stepSide_;
                    if (stepBegin(w)) { shiftT_ = 0; t = 0; forced_ = false; break; }   // (double support: the weight passes to the landed foot first)
                }
                go(STAND);
                forced_ = false;
                over = s.captureOut > 0 ? 0.03f : 0;     // still falling: the other leg goes at once
            }
            break;
        case FALL: {
            quiet = s.speed < 0.7f ? quiet + h : 0;
            if (activity == A_HANG) { if (!gripping(0) && !gripping(1)) activity = A_NONE; break; }
            if (((s.height < s.standHeight * 0.55f || t > 0.7f) && quiet > 0.3f) || t > 4.0f) { go(DOWN); quiet = 0; }
            break;
        }
        case DOWN:
            if (t < downFor || stayDown) break;
            if (!legs && cfg.style != SydneyConfig::CRAWLER) { outcome = CRAWL; break; }
            if (tries >= cfg.getUpTries + static_cast<int>((traits.endurance - 1) * 2)) { outcome = STUCK; break; }
            tries++;
            getUpT_ = 0; getUpPhase_ = s.supine ? 0 : 1; getUpPhaseT_ = 0;
            rollSide_ = rand01() < 0.5f ? -1.0f : 1.0f;
            go(GETUP);
            break;
        case GETUP: {
            if (!legs && cfg.style != SydneyConfig::CRAWLER) { go(DOWN); downFor = 0.3f; break; }
            const bool upright = s.height > s.standHeight * 0.88f && s.tilt < rad(18) && s.feet > 0;
            if (cfg.style == SydneyConfig::CRAWLER) {
                const bool four = s.height > s.standHeight * cfg.crawlHeight * 0.72f && std::fabs(s.up.y) < 0.62f && s.speed < 1.4f;
                if ((four && getUpT_ > cfg.getUpTime * 0.75f) || getUpT_ > cfg.getUpTime * 1.6f) { outcome = RECOVERED; recoveries++; }
                break;
            }
            if (upright && getUpPhase_ >= 3) { steps = 0; settled = 0; steady_ = 1.2f; go(STAND); break; }
            if (t > cfg.getUpTime + cfg.getUpTimeout) { go(DOWN); downFor = 0.8f; }
            break;
        }
        case DEAD:
            if (limpT_ >= cfg.deathFade) outcome = DEAD_OUTCOME;
            break;
    }

    // the behaviours set the goals for this step
    g_.clear();
    if (activity != A_HANG) activity = A_NONE;
    const float stunned = stun > 0 && state != DEAD ? 0.55f : 1.0f;
    switch (state) {
        case STAND:
            if (intent.kind == ASTRATTO_INTENT_WALK && hlen(V3(intent.x, 0, intent.z)) > 0.05f) { activity = A_WALK; walkGoals(h); }
            else standGoals(h);
            if (outcome == RECOVERED && s.captureOut < -0.05f * body->scale) holdGoals();   // the wound is seen to once the feet are safe
            break;
        case STEP:
            if (intent.kind == ASTRATTO_INTENT_WALK && hlen(V3(intent.x, 0, intent.z)) > 0.05f && !forced_) activity = A_WALK;
            stepGoals(h);
            break;
        case FALL: if (activity == A_HANG) hangGoals(w, h); else fallGoals(w, h); break;
        case DOWN: lieGoals(h); holdGoals(); break;
        case GETUP: getUpGoals(w, h); break;
        case DEAD: limpGoals(h); break;
    }
    if (steady_ > 0 && (state == STAND || state == STEP)) {
        // just risen: the get-up's support eases off over a moment instead of ending the instant the body is upright -
        // the hips held over the feet and the trunk upright while the legs take the whole weight, through the step or
        // two that catches what momentum the rise left (let go at the top, a body staggered and went straight back down)
        const V3 pp = body->body(PELVIS).p;
        g_.assist = true; g_.assistMax = 0.5f * smooth(steady_ / 1.2f);
        g_.assistAt = V3(s.supportCentre.x, pp.y, s.supportCentre.z);
        g_.assistQ = heading(-(body->body(L_FOOT).R.col(2) + body->body(R_FOOT).R.col(2)));
    }
    for (int e = 0; e < 4; e++) { reaching[e] = g_.eff[e]; reachAt[e] = g_.effAt[e]; }
    // stunned, the muscles answer weakly; a blow is answered by a flinch - a brief stiffening - before the response
    if (state != DEAD) {
        g_.tone *= stunned;
        if (hitDelay_ > 0) for (int j = 0; j < JOINTS; j++) g_.cocon[j] = std::max(g_.cocon[j], 0.5f * pending_);
        if (intent.kind == ASTRATTO_INTENT_RELAX) { for (int j = 0; j < JOINTS; j++) { g_.gain[j] *= 0.35f; g_.cocon[j] = 0; } g_.reflex *= 0.4f; }
    }
}

// ------------------------------------------------------------------ behaviours
void SydneyCharacter::setJoint(int j, float ax, float ay, float az, float gain, float damp, float cocon) {
    g_.target[j] = expMap(V3(ax, ay, az));
    g_.gain[j] = gain; g_.damp[j] = damp; g_.cocon[j] = std::max(g_.cocon[j], cocon);
    g_.world[j] = false;
}

void SydneyCharacter::setReach(int e, V3 at, float hz, float maxForce) { g_.eff[e] = true; g_.effAt[e] = at; g_.effHz[e] = hz; g_.effMax[e] = maxForce; g_.effVel[e] = V3(); }

void SydneyCharacter::relaxArms(float gain) {
    for (int side = 0; side < 2; side++) {
        const float m = MIRROR[side];
        setJoint(ARM[side][0], 0.05f, 0, 0.12f * m, gain);
        setJoint(ARM[side][1], 0.25f, 0, 0, gain * 0.8f);
    }
}

void SydneyCharacter::standGoals(float h) {
    (void)h;
    const float scale = body->scale;
    g_.balance = true;
    g_.stance[0] = s.foot[0]; g_.stance[1] = s.foot[1];
    g_.torso = true;
    // upright, facing the way the feet face
    const V3 ff = body->body(L_FOOT).R.col(2) + body->body(R_FOOT).R.col(2);
    g_.torsoTarget = heading(-ff);
    g_.torsoGain = cfg.standPosture;
    // the hip strategy: as the capture point nears the edge of the feet the trunk is pitched toward the fall - turning
    // the upper body quickly that way pushes the hips (and the centre of mass) back over the feet
    if (cfg.hipStrategy > 0 && s.captureOut > -0.04f * body->scale) {
        const V3 d = flat(s.capture - s.supportCentre);
        if (hlen(d) > 1e-3f) {
            const float ang = clampf(cfg.hipStrategy * (s.captureOut + 0.04f * body->scale) * 4.0f, 0.0f, 0.6f);
            g_.torsoTarget = axisAngle(norm(cross(V3(0, 1, 0), d)), ang) * g_.torsoTarget;
        }
    }
    g_.heightWant = s.standHeight * 0.985f; g_.heightGain = 1;
    g_.balanceGain = 1;
    // the legs: softly bent, the feet flat on the ground in the world
    for (int f = 0; f < 2; f++) {
        setJoint(LEG[f][0], 0.06f, 0, 0, cfg.stanceHip * 0.5f);
        setJoint(LEG[f][1], -0.1f, 0, 0, 0.6f);
        setJoint(LEG[f][2], 0.04f, 0, 0, 0.5f);
        if (!s.foot[f]) {
            // a foot off the ground is put back down under the hip
            const V3 hip = body->jointPoint(LEG[f][0]);
            setReach(2 + f, V3(hip.x, s.groundY + 0.01f * scale, hip.z), 5, cfg.stepForce * 0.5f);
        }
    }
    setJoint(SPINE, 0, 0, 0, 0.5f);
    // the head kept level (the vestibular righting reflex)
    g_.world[NECK] = true; g_.worldTarget[NECK] = g_.torsoTarget; g_.gain[NECK] = 0.8f;
    relaxArms(0.45f);
    if (intent.kind == ASTRATTO_INTENT_BRACE) {
        const float k = clampf(intent.value, 0, 1);
        for (int j = 0; j < JOINTS; j++) g_.cocon[j] = std::max(g_.cocon[j], 0.5f * k);
        g_.heightWant = s.standHeight * (0.985f - 0.08f * k);
        V3 d = norm(V3(intent.x, 0, intent.z));
        protectGoals(-d, 0.6f * k);
        activity = A_BRACE;
    } else if (intent.kind == ASTRATTO_INTENT_PROTECT) { protectGoals(s.fwd, 0.8f); activity = A_PROTECT; }
    else if (intent.kind == ASTRATTO_INTENT_REACH) {
        const int which = static_cast<int>(intent.value + 0.5f);
        const V3 at(intent.x, intent.y, intent.z);
        if (which == 0 || which == 2) setReach(0, at, 3.5f, 160);
        if (which == 1 || which == 2) setReach(1, at, 3.5f, 160);
        activity = A_REACH;
    }
    // just struck: the arms come up toward the blow's side
    if (hitDelay_ <= 0 && pending_ > 0.3f && stun > 0) protectGoals(-hitDir_, 0.4f * pending_);
}

bool SydneyCharacter::stepBegin(const VoxelWorld* w) {
    (void)w;
    const Ragdoll& r = *body;
    const float scale = r.scale;
    const V3 dir = flat(s.capture - s.supportCentre);
    const float dl = hlen(dir);
    const V3 d = dl > 1e-4f ? dir / dl : flat(s.fwd);
    // the trailing foot (along the way the body is going) steps; a foot that cannot, cannot
    const V3 fl = r.body(L_FOOT).p, fr = r.body(R_FOOT).p;
    int side = stepSide_ >= 0 && activity == A_WALK ? stepSide_ : (dot(flat(fl - s.supportCentre), d) <= dot(flat(fr - s.supportCentre), d) ? 0 : 1);
    if (activity != A_WALK && state == STAND && steps > 0 && s.feet == 2) {
        // in a stagger the legs take turns where they can
        const int last = stepSide_;
        if (last >= 0) side = 1 - last;
    }
    // but a foot carrying the body cannot be lifted: the one that carries clearly less steps
    noShift_ = false;
    if (activity != A_WALK && steps == 0) {
        const float a = s.support[0], b = s.support[1], tot = a + b;
        if (tot > s.weight * 0.3f && std::fabs(a - b) > tot * 0.3f) side = a < b ? 0 : 1;
        // falling mostly sideways: the leg on that side steps out (its knee drops to free it) rather than the other
        // crossing over it
        const float lat = dot(d, norm(flat(s.right)));
        if (std::fabs(lat) > 0.7f) { side = lat > 0 ? 1 : 0; noShift_ = true; }
    }
    if ((side == 0 && !legL) || (side == 1 && !legR)) side = 1 - side;
    if ((side == 0 && !legL) || (side == 1 && !legR)) return false;
    const int other = 1 - side;
    const V3 stance = r.body(FOOT[other]).p;
    // where it goes: the capture point, a little past, out to its own side of the stance foot
    const V3 rightFlat = norm(flat(s.right));
    const float lat = (side == 0 ? -1.0f : 1.0f) * cfg.stepWidth * scale;
    V3 to = predictCapture(r.body(FOOT[other]).p, stepTime()) + d * (cfg.stepOvershoot * scale) + rightFlat * lat;
    if (activity == A_WALK) to = walkTarget(side, stepTime());
    V3 off = flat(to - stance);
    const float ol = hlen(off), max = cfg.stepReach * scale;
    if (ol > max) off = off * (max / ol);
    // never across the stance foot
    const float across = dot(off, rightFlat) * (side == 0 ? -1.0f : 1.0f);
    if (across < 0.12f * scale) off = off + rightFlat * ((0.12f * scale - across) * (side == 0 ? -1.0f : 1.0f));
    to = stance + off;
    to.y = s.groundY;
    stepSide_ = side;
    stepFrom_ = r.body(FOOT[side]).p;
    stepTo_ = to;
    stepT_ = 0;
    lifted_ = false;
    shiftT_ = 0;
    // the more urgent, the quicker the swing
    stepDur_ = stepTime();
    return true;
}

V3 SydneyCharacter::walkTarget(int side, float left) const {
    // a walking step: the divergent component of motion. The foot lands where the capture point will be when it lands,
    // short of it by the offset a steady gait at that speed keeps (b = v T / (e^(wT) - 1)), so the next stride starts as
    // this one did. The speed aimed at is a little above the one there is: the walk gathers pace stride by stride.
    const V3 vw(intent.x, 0, intent.z), v = flat(s.comV);
    const float T = stepTime();
    const V3 dv = vw - v;
    const float dvl = hlen(dv), lim = 0.3f * pace();
    const V3 vt = dvl > lim ? v + dv * (lim / dvl) : vw;
    const float eT = std::exp(s.omega * T) - 1;
    const float lat = (side == 0 ? -1.0f : 1.0f) * cfg.stepWidth * body->scale * 2.2f;
    return predictCapture(body->body(FOOT[1 - side]).p, left) - vt * (T / std::max(0.2f, eT)) + norm(flat(s.right)) * lat;
}

V3 SydneyCharacter::predictCapture(V3 pivot, float ahead) const {
    // the linear inverted pendulum about a fixed centre of pressure: the capture point runs away from it exponentially,
    // xi(t + T) = p + (xi - p) e^(w T)
    const float k = std::exp(s.omega * std::min(0.5f, ahead));
    return V3(pivot.x + (s.capture.x - pivot.x) * k, s.groundY, pivot.z + (s.capture.z - pivot.z) * k);
}

float SydneyCharacter::stepTime() const {
    const float urgency = clampf(s.captureOut / (0.3f * body->scale), 0, 1);
    return cfg.stepTime / pace() / std::max(0.5f, std::sqrt(traits.agility)) * (activity == A_WALK ? 1.25f : 1.0f - 0.25f * urgency);
}

bool SydneyCharacter::stepLanded() const {
    const int f = stepSide_;
    // down where it was going (or late in the swing, wherever it is): a brush of the floor on the way is not a landing
    const float off = hlen(body->body(FOOT[f]).p - stepTo_);
    return (lifted_ && s.foot[f] && (stepT_ > stepDur_ * 0.8f || (stepT_ > stepDur_ * 0.45f && off < 0.07f * body->scale))) || stepT_ > stepDur_ * 1.8f;
}

void SydneyCharacter::stepGoals(float h) {
    const Ragdoll& r = *body;
    const float scale = r.scale;
    const int sw = stepSide_, st = 1 - sw;
    // first the weight goes onto the stance foot (an anticipatory postural adjustment): both feet still down, the
    // balance referred to the stance foot across the body, so the centre of pressure runs briefly toward the swing
    // foot and sends the body over the other. The more urgent the step, the shorter this is.
    if (shiftT_ >= 0 && !noShift_ && s.foot[sw] && s.foot[st]) {
        const V3 stance = r.body(FOOT[st]).p, rightFlat = norm(flat(s.right));
        const float across = dot(flat(s.capture - stance), rightFlat) * (st == 0 ? 1.0f : -1.0f);   // > 0: toward the swing side
        const float maxShift = forced_ || activity == A_WALK ? 0.4f : clampf(cfg.shiftTime - s.captureOut * cfg.shiftTime * 5, 0.0f, cfg.shiftTime);
        shiftT_ += h;
        const float comAcross = dot(flat(s.com - stance), rightFlat) * (st == 0 ? 1.0f : -1.0f);
        if ((across > 0.03f * scale || comAcross > 0.035f * scale) && shiftT_ < maxShift) {
            standGoals(h);
            const V3 c = s.supportCentre;
            const float ac = dot(flat(stance - c), rightFlat);
            g_.copRef = c + rightFlat * (ac * 1.1f); g_.hasCopRef = true;
            g_.balanceGain = 1.25f;
            return;
        }
    }
    shiftT_ = -1;
    stepT_ += h;
    // while the foot is in the air its target follows the capture point (the step is steered, not thrown)
    if (stepT_ < stepDur_ * 0.6f && !forced_) {
        const V3 stance = r.body(FOOT[st]).p;
        const V3 rightFlat = norm(flat(s.right));
        const V3 ahead = predictCapture(stance, std::max(0.0f, stepDur_ - stepT_));
        const V3 dir = flat(ahead - stance);
        V3 to = activity == A_WALK ? walkTarget(sw, std::max(0.0f, stepDur_ - stepT_))
              : ahead + (hlen(dir) > 1e-4f ? norm(dir) : V3()) * (cfg.stepOvershoot * scale) + rightFlat * ((sw == 0 ? -1.0f : 1.0f) * cfg.stepWidth * scale);
        V3 off = flat(to - stance);
        const float ol = hlen(off), max = cfg.stepReach * scale;
        if (ol > max) off = off * (max / ol);
        // never across the stance foot: the inside foot closes up beside it at most
        const float sgn = sw == 0 ? -1.0f : 1.0f, across = dot(off, rightFlat) * sgn;
        if (across < 0.12f * scale) off = off + rightFlat * ((0.12f * scale - across) * sgn);
        stepTo_ = stepTo_ +(V3(stance.x + off.x, s.groundY, stance.z + off.z) - stepTo_) * std::min(1.0f, h * 30);
    }
    if (s.support[sw] < s.weight * 0.08f) lifted_ = true;
    // the foot comes up at once (the knee folds and drops the weight off it), travels, and is set down
    const float k = clampf(stepT_ / stepDur_, 0, 1), e = smooth((k - 0.08f) / 0.8f);
    const float up = k < 0.3f ? smooth(k / 0.3f) : smooth((1 - k) / 0.55f);
    V3 at(lerp(stepFrom_.x, stepTo_.x, e), s.groundY + up * cfg.stepLift * scale + 0.004f * scale, lerp(stepFrom_.z, stepTo_.z, e));
    // stance on the other leg: balance over it
    g_.balance = s.foot[st];
    g_.stance[st] = s.foot[st];
    g_.torso = true;
    g_.torsoTarget = heading(s.fwd);
    g_.torsoGain = cfg.standPosture;
    g_.heightWant = s.standHeight * 0.97f; g_.heightGain = 0.8f;
    g_.copRef = r.body(FOOT[st]).p; g_.hasCopRef = true;
    g_.balanceGain = 1.0f;
    if (activity == A_WALK) {
        const V3 vw(intent.x, 0, intent.z);
        const float sp = hlen(vw);
        // the body rolls over the stance foot as an inverted pendulum (no push from the ankle): the pace is set by where
        // the feet land, not by the stance leg
        if (sp > 0.05f) { g_.walkAxis = vw / sp; g_.hasWalkAxis = true; }
        if (sp > 0.05f) g_.torsoTarget = heading(vw / sp) * expMap(V3(-0.05f * std::min(1.5f, sp), 0, 0));
    }
    setJoint(LEG[st][0], 0.05f, 0, 0, cfg.stanceHip * 0.5f);
    setJoint(LEG[st][1], -0.12f, 0, 0, 0.7f);
    setJoint(LEG[st][2], 0, 0, 0, 0.5f);
    // the swing leg: lifted and carried by its foot to where it lands, the sole kept level
    // the ankle's path, solved for the hip and the knee (two-bone IK) and followed by their servos; the knee folds
    // first whatever the path says, to take the weight off the foot
    legIk(sw, at + V3(0, 0.075f * scale, 0), 1.6f);
    if (k < 0.25f) setJoint(LEG[sw][1], -1.1f, 0, 0, 1.6f);
    // late in the swing the foot is also pulled to its mark (the knee is bent by then: the pull has purchase)
    if (k > 0.35f) setReach(2 + sw, at, 6, 350);
    // the toes kept up off the floor through the swing (they drag, and anchor the foot, if they are let drop), level to land
    g_.world[LEG[sw][2]] = true; g_.worldTarget[LEG[sw][2]] = heading(s.fwd) * expMap(V3(k < 0.75f ? 0.25f : 0.05f, 0, 0)); g_.gain[LEG[sw][2]] = 6.0f;
    // the arms swing out for balance against the step
    const float m = MIRROR[sw];
    for (int side = 0; side < 2; side++) {
        const float mm = MIRROR[side];
        setJoint(ARM[side][0], (side == sw ? -0.25f : 0.3f), 0, 0.35f * mm, 0.4f);
        setJoint(ARM[side][1], 0.4f, 0, 0, 0.35f);
    }
    (void)m;
    setJoint(SPINE, 0, 0, 0, 0.5f);
    g_.world[NECK] = true; g_.worldTarget[NECK] = heading(s.fwd); g_.gain[NECK] = 0.7f;
}

void SydneyCharacter::legIk(int side, V3 ankle, float gain) {
    const Ragdoll& r = *body;
    const Body& P = r.body(PELVIS);
    const V3 hip = r.jointPoint(LEG[side][0]);
    const V3 d = transpose(P.R) * (ankle - hip);         // in the pelvis's frame: forward is -z
    const float L1 = 0.3375f * r.scale, L2 = 0.3375f * r.scale;
    const float dist = clampf(len(d), 0.15f * r.scale, (L1 + L2) * 0.995f);
    const float knee = PI - std::acos(clampf((L1 * L1 + L2 * L2 - dist * dist) / (2 * L1 * L2), -1, 1));
    const float pitch = std::atan2(-d.z, -d.y), lean = std::acos(clampf((L1 * L1 + dist * dist - L2 * L2) / (2 * L1 * dist), -1, 1));
    const float roll = std::atan2(d.x, std::max(0.05f, -d.y));
    const AxisDef& hx = MUSCLES[LEG[side][0]].ax[0];
    const AxisDef& hz = MUSCLES[LEG[side][0]].ax[2];
    setJoint(LEG[side][0], clampf(pitch + lean, hx.lo, hx.hi), 0, clampf(roll, hz.lo, hz.hi), gain);
    setJoint(LEG[side][1], -clampf(knee, 0, 2.3f), 0, 0, gain);
}

void SydneyCharacter::walkGoals(float h) {
    standGoals(h);
    // walking: the body leans a little into its going and is held over a point ahead of the feet
    const V3 vw(intent.x, 0, intent.z);
    const float sp = hlen(vw);
    if (sp > 0.05f) {
        const V3 d = vw / sp;
        g_.torsoTarget = heading(d) * expMap(V3(-0.06f * std::min(1.5f, sp), 0, 0));
        // (standing still it only leans: the going itself is the steps', which start at once)
        g_.balanceGain = 0.8f;
    }
}

void SydneyCharacter::protectGoals(V3 dir, float k) {
    // the forearms between the head and the danger, elbows bent, hands up
    if (k <= 0) return;
    const float ahead = dot(flat(dir), flat(s.fwd));
    for (int side = 0; side < 2; side++) {
        const float m = MIRROR[side];
        if (ahead > -0.3f) { setJoint(ARM[side][0], 1.3f, 0, -0.25f * m, 0.6f * k + 0.2f, 0, 0.3f * k); setJoint(ARM[side][1], 1.9f, 0, 0, 0.6f * k + 0.2f, 0, 0.3f * k); }
        else { setJoint(ARM[side][0], 0.6f, 0, 0.9f * m, 0.5f * k + 0.2f); setJoint(ARM[side][1], 1.7f, 0, 0, 0.5f * k + 0.2f); }
    }
    g_.cocon[NECK] = std::max(g_.cocon[NECK], 0.4f * k);
}

void SydneyCharacter::fallGoals(const VoxelWorld* w, float h) {
    const Ragdoll& r = *body;
    const float scale = r.scale;
    // where the body is going down: along its velocity, or the way it leans
    V3 dir = flat(s.comV);
    if (hlen(dir) < 0.25f) dir = flat(s.up);
    if (hlen(dir) < 1e-3f) dir = flat(s.fwd);
    dir = norm(dir);
    const float ahead = dot(dir, norm(flat(s.fwd)));
    // how long until it is on the ground (the centre of mass falling to a third of its height)
    const float drop = std::max(0.0f, s.height - s.standHeight * 0.35f), vy = -s.comV.y;
    const float ttc = drop > 0 ? (-vy + std::sqrt(std::max(0.0f, vy * vy + 2 * r.gravity * drop))) / r.gravity : 0;
    braceT_ += h;
    // a ledge within reach: grab it
    bool toLedge[2] = {false, false};
    if (w != nullptr && alive && (armL || armR) && s.comV.y < -0.5f) {
        for (int side = 0; side < 2; side++) {
            if ((side == 0 && !armL) || (side == 1 && !armR)) continue;
            // an edge once gone for is kept going for, while the hand can still get there (half a second, a long reach)
            V3 at;
            const V3 sh = body->jointPoint(ARM[side][0]);
            if (ledgeT_[side] > 0 && len(ledgeAt_[side] - sh) < 1.0f * scale) { at = ledgeAt_[side]; ledgeT_[side] -= h; }
            else if (findLedge(w, side, at)) { ledgeAt_[side] = at; ledgeT_[side] = 0.5f; }
            else { ledgeT_[side] = 0; continue; }
            toLedge[side] = true;
            setReach(side, at, 10, 520 * cfg.strength * traits.strength);
            setJoint(ARM[side][1], 0.2f, 0, 0, 0.8f);
            if (len(effector(side) - at) < 0.15f * scale && grip_[side] == 0) takeHold(side, at);
        }
        if (activity == A_HANG) return;
    }
    // the protective response: hands out toward where the body will land, braced (stiff) until they touch, then
    // yielding; the head turned away from the ground; the knees giving
    const float b = cfg.brace * (alive ? 1.0f : 0.0f);
    if (b > 0.01f && hitDelay_ <= 0) {
        const V3 land = V3(s.com.x, s.groundY + 0.04f * scale, s.com.z) + dir * (0.55f * scale);
        const float stiff = smooth(1 - ttc / 0.4f) * b;
        for (int side = 0; side < 2; side++) {
            const float m = MIRROR[side];
            const bool armOk = (side == 0 ? armL : armR) && !toLedge[side];     // (a hand going for an edge keeps going for it)
            if (!armOk) continue;
            const V3 handAt = land + norm(flat(s.right)) * (m * 0.22f * scale);
            setReach(side, handAt, 4.5f, 260 * b);
            const bool touching = r.body(FORE[side]).groundPoints > 0;
            setJoint(ARM[side][0], ahead > -0.2f ? 1.2f : -0.6f, 0, 0.35f * m, 0.3f * b, 0, touching ? 0.1f : 0.6f * stiff);
            setJoint(ARM[side][1], 0.35f, 0, 0, 0.35f * b, touching ? 1.0f : 0.0f, touching ? 0.05f : 0.5f * stiff);
        }
        activity = A_BRACE;
    } else relaxArms(0.2f * cfg.fallStrength);
    // head: chin tucked falling back, lifted falling forward
    setJoint(NECK, ahead > 0 ? 0.35f : -0.55f, 0, 0, 0.7f * cfg.fallStrength + 0.2f);
    const float k = cfg.fallStrength;
    for (int f = 0; f < 2; f++) { setJoint(LEG[f][0], 0.55f, 0, 0, k); setJoint(LEG[f][1], -0.9f, 0, 0, k); setJoint(LEG[f][2], 0.1f, 0, 0, k * 0.6f); }
    setJoint(SPINE, ahead > 0 ? -0.25f : -0.15f, 0, 0, k);
    // in the air for a while: arms windmill and legs pedal, driven by the oscillators
    if (airborne > 0.22f && alive && cfg.style == SydneyConfig::HUMAN) {
        flailT_ += h;
        const float o0 = cpg[0].out(), o1 = cpg[1].out();
        for (int side = 0; side < 2; side++) {
            const float m = MIRROR[side], o = side == 0 ? o0 : o1;
            setJoint(ARM[side][0], 1.2f + 1.0f * o, 0, (0.9f + 0.4f * o) * m, 0.45f);
            setJoint(ARM[side][1], 0.6f + 0.5f * std::fabs(o), 0, 0, 0.4f);
            setJoint(LEG[side][0], 0.6f + 0.7f * o, 0, 0, 0.45f);
            setJoint(LEG[side][1], -0.4f - 0.9f * std::max(0.0f, -o), 0, 0, 0.45f);
        }
    }
}

bool SydneyCharacter::findLedge(const VoxelWorld* w, int side, V3& at) const {
    // a cell whose top is open (two cells of air over it), within the arm's reach of the shoulder and above the waist
    const Ragdoll& r = *body;
    const float scale = r.scale;
    const V3 sh = r.jointPoint(ARM[side][0]);
    const float reach = 0.8f * scale;
    const int x0 = static_cast<int>(std::floor(sh.x - reach)), x1 = static_cast<int>(std::floor(sh.x + reach));
    const int z0 = static_cast<int>(std::floor(sh.z - reach)), z1 = static_cast<int>(std::floor(sh.z + reach));
    const int y0 = static_cast<int>(std::floor(s.com.y - 0.6f * scale)) - 1, y1 = static_cast<int>(std::floor(sh.y + reach));
    float best = 1e30f;
    bool found = false;
    for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) for (int y = y0; y <= y1; y++) {
        if (!w->solid(x, y, z) || w->solid(x, y + 1, z) || w->solid(x, y + 2, z)) continue;
        // the edge of that top nearest the shoulder, which must face open air (an edge, not the middle of a floor)
        const float ex = clampf(sh.x, static_cast<float>(x) + 0.02f, static_cast<float>(x) + 0.98f), ez = clampf(sh.z, static_cast<float>(z) + 0.02f, static_cast<float>(z) + 0.98f);
        const int nx = ex < x + 0.1f ? x - 1 : ex > x + 0.9f ? x + 1 : x, nz = ez < z + 0.1f ? z - 1 : ez > z + 0.9f ? z + 1 : z;
        if ((nx == x && nz == z) || w->solid(nx, y, nz)) continue;
        const V3 p(ex, static_cast<float>(y + 1) + 0.02f, ez);
        const float d = len(p - sh);
        if (d > reach || p.y < s.com.y - 0.6f * scale) continue;
        if (d < best) { best = d; at = p; found = true; }
    }
    return found;
}

void SydneyCharacter::hangGoals(const VoxelWorld* w, float h) {
    (void)w;
    hangT_ += h;
    gripTired_ += h / (6.0f * std::max(0.3f, traits.endurance));
    const Ragdoll& r = *body;
    activity = A_HANG;
    // hanging: arms overhead and holding, the body let hang, the legs quiet (the trunk damps the swing); tiring, the grip goes
    for (int side = 0; side < 2; side++) {
        const float m = MIRROR[side];
        if (grip_[side] != 0 && body->space.joint(grip_[side]) && body->space.joint(grip_[side])->broken) { body->space.removeJoint(grip_[side]); grip_[side] = 0; }
        setJoint(ARM[side][0], 2.4f, 0, 0.2f * m, 0.6f, 0, 0.3f);
        setJoint(ARM[side][1], 0.3f, 0, 0, 0.6f, 0, 0.3f);
        if (grip_[side] == 0 && grip_[1 - side] != 0 && (side == 0 ? armL : armR)) {
            // the free hand goes up beside the other and takes hold too
            const V3 at = gripAt_[1 - side] + norm(flat(s.right)) * (m * 0.3f * r.scale);
            setReach(side, at, 6, 320);
            if (len(effector(side) - at) < 0.12f * r.scale) takeHold(side, at);
        }
    }
    for (int f = 0; f < 2; f++) { setJoint(LEG[f][0], 0.15f, 0, 0, 0.5f, 1.5f); setJoint(LEG[f][1], -0.3f, 0, 0, 0.4f, 1.0f); setJoint(LEG[f][2], 0, 0, 0, 0.2f); }
    setJoint(SPINE, 0, 0, 0, 0.6f, 1.5f);
    setJoint(NECK, 0.2f, 0, 0, 0.5f);
    if (gripTired_ > 1 || intent.kind == ASTRATTO_INTENT_RELAX || !alive) { releaseGrips(); activity = A_NONE; gripTired_ = 0; }
}

void SydneyCharacter::takeHold(int side, V3 at) {
    // the hand closes on the edge: a ball joint between the hand and the world, as strong as the catch can be; how long
    // it holds is the hang's business (the grip tires)
    impulsis::Joint jt;
    jt.handle = static_cast<astratto_handle>(500 + side);
    jt.type = ASTRATTO_JOINT_BALL;
    jt.a = body->part[FORE[side]]; jt.b = -1;
    jt.localA = effector(side);
    jt.axisA = V3(0, 1, 0);
    jt.breakForce = s.weight * 9.0f * cfg.strength * traits.strength;
    body->space.addJoint(jt);
    grip_[side] = jt.handle; gripAt_[side] = effector(side);
    if (activity != A_HANG) { activity = A_HANG; hangT_ = 0; }
    astratto_evt_ragdoll_impact ev{};
    ev.point = static_cast<uint32_t>(side == 0 ? rd::LW : rd::RW); ev.speed = len(effectorVel(side)); ev.x = at.x; ev.y = at.y; ev.z = at.z;
    if (body->impacts.size() < 32) body->impacts.push_back(ev);
}

void SydneyCharacter::releaseGrips() {
    for (int side = 0; side < 2; side++) if (grip_[side] != 0) { body->space.removeJoint(grip_[side]); grip_[side] = 0; }
}

void SydneyCharacter::lieGoals(float h) {
    // lying: little tone, the joints let settle toward a rest; the hurt writhe a little, and breathe
    writheT_ += h; breathT_ += h;
    const float k = cfg.downStrength;
    for (int f = 0; f < 2; f++) { setJoint(LEG[f][0], 0.2f, 0, 0, k); setJoint(LEG[f][1], -0.35f, 0, 0, k); setJoint(LEG[f][2], 0, 0, 0, k); }
    for (int side = 0; side < 2; side++) { const float m = MIRROR[side]; setJoint(ARM[side][0], 0.2f, 0, 0.3f * m, k); setJoint(ARM[side][1], 0.5f, 0, 0, k); }
    setJoint(SPINE, -0.1f + 0.03f * std::sin(breathT_ * 2 * PI * 0.3f), 0, 0, k);
    setJoint(NECK, -0.1f, 0, 0, k * 1.5f);
    g_.gravityComp = false;
    if (alive && (stun > 0.2f || wound >= 0) && t < downFor + 1.5f) {
        activity = A_WRITHE;
        const float o0 = cpg[0].out(), o1 = cpg[1].out(), amp = 0.45f;
        setJoint(LEG[0][0], 0.5f + amp * o0, 0, 0, k * 2.5f); setJoint(LEG[0][1], -0.9f - amp * o0, 0, 0, k * 2.5f);
        setJoint(LEG[1][0], 0.5f + amp * o1, 0, 0, k * 2.5f); setJoint(LEG[1][1], -0.9f - amp * o1, 0, 0, k * 2.5f);
        setJoint(SPINE, -0.25f - 0.1f * o0, 0.08f * o1, 0, k * 3);
    }
}

void SydneyCharacter::holdGoals() {
    // a hand goes to the wound and holds it (the other arm, when the wound is on an arm)
    if (!cfg.holdWound || !alive || wound < 0) return;
    const int wp = wound;
    const bool onLeftArm = wp == rd::LS || wp == rd::LE || wp == rd::LW, onRightArm = wp == rd::RS || wp == rd::RE || wp == rd::RW;
    const bool leftSide = onLeftArm || wp == rd::LH || wp == rd::LK || wp == rd::LA;
    int arm = onLeftArm ? 1 : onRightArm ? 0 : leftSide ? 0 : 1;
    if ((arm == 0 && !armL) || (arm == 1 && !armR)) arm = 1 - arm;
    if ((arm == 0 && (!armL || onLeftArm)) || (arm == 1 && (!armR || onRightArm))) return;
    if (g_.eff[arm]) return;
    const V3 at = body->pointWorld(wp) + s.fwd * (0.06f * body->scale);
    setReach(arm, at, 3, cfg.holdForce * cfg.strength);
    setJoint(ARM[arm][0], 0.6f, 0, 0, 0.1f);
    setJoint(ARM[arm][1], 1.6f, 0, 0, 0.15f);
    if (activity == A_NONE) activity = A_HOLD;
}

void SydneyCharacter::getUpGoals(const VoxelWorld* w, float h) {
    (void)w;
    const Ragdoll& r = *body;
    const float scale = r.scale;
    getUpT_ += h; getUpPhaseT_ += h;
    const float str = cfg.getUpStrength;
    if (cfg.style == SydneyConfig::CRAWLER) {
        // up onto all fours, and stay there
        for (int side = 0; side < 2; side++) { setJoint(ARM[side][0], 1.4f, 0, 0, 0.8f); setJoint(ARM[side][1], 0.15f, 0, 0, 0.8f); }
        for (int f = 0; f < 2; f++) { setJoint(LEG[f][0], 1.5f, 0, 0, 0.8f); setJoint(LEG[f][1], -1.9f, 0, 0, 0.8f); setJoint(LEG[f][2], -0.4f, 0, 0, 0.4f); }
        setJoint(SPINE, -0.2f, 0, 0, 0.7f);
        setJoint(NECK, 0.6f, 0, 0, 0.6f);
        g_.lift = std::min(1.0f, getUpT_ / 0.4f); g_.liftHeight = s.standHeight * cfg.crawlHeight;
        return;
    }
    switch (getUpPhase_) {
        case 0: {
            // on the back: roll over onto the front, as people do. The knees are drawn up (the feet planted), then both
            // are let fall to one side - which turns the pelvis - and the trunk follows: the spine twists after them, the
            // far arm reaches across the body to the ground, the near arm is put out of the way above the head, and the
            // head turns to look where the body is going.
            const float sd = rollSide_;                    // +1: over to its right, -1: over to its left
            const int nearArm = sd > 0 ? 1 : 0, farArm = 1 - nearArm;
            const float k = smooth((getUpPhaseT_ - 0.15f) / 0.4f);  // 0 knees drawn up, 1 fallen to the side
            for (int f = 0; f < 2; f++) {
                setJoint(LEG[f][0], 1.25f, -0.85f * sd * k * cfg.rollTwist, 0.25f * sd * k, 1.2f * str);
                setJoint(LEG[f][1], -1.9f, 0, 0, 1.0f * str);
                setJoint(LEG[f][2], -0.3f, 0, 0, 0.4f);
            }
            setJoint(SPINE, -0.25f * k, -0.6f * sd * k * cfg.rollTwist, 0.2f * sd * k, 1.0f * str);
            setJoint(ARM[farArm][0], 1.6f, 0, 0.9f * sd * k, 1.0f * str);       // across the chest toward the side rolled to
            setJoint(ARM[farArm][1], 0.3f, 0, 0, 0.7f);
            setJoint(ARM[nearArm][0], 2.3f * k, 0, -0.6f * sd, 0.7f);          // up past the head, out of the way
            setJoint(ARM[nearArm][1], 0.2f, 0, 0, 0.5f);
            setJoint(NECK, -0.3f, -0.7f * sd * k * cfg.rollTwist, 0, 0.7f);
            // the drive: the far foot, planted, pushes down into the floor and toward the roll (its hip rises and turns
            // the pelvis over), and the far hand reaches for the floor beyond the body
            if (k > 0.2f) {
                const int farLeg = sd > 0 ? 0 : 1;
                const V3 side = norm(flat(s.right)) * sd;
                const V3 fp = r.body(FOOT[farLeg]).p;
                setReach(2 + farLeg, fp + V3(0, -0.25f * scale, 0) + side * (0.2f * scale), 6, 900 * str * k);
                const V3 sh = r.jointPoint(ARM[farArm][0]);
                setReach(farArm, V3(sh.x, s.groundY, sh.z) + side * (0.55f * scale), 4, 300 * str * k);
            }
            // the reference: the trunk turned face down about its own length, where it lies
            {
                V3 yl = flat(s.up);
                if (hlen(yl) < 1e-3f) yl = flat(s.fwd);
                yl = norm(yl);
                const V3 zb(0, 1, 0), xr = cross(yl, zb);
                g_.assist = true; g_.assistMax = 0.6f * k + 0.05f;
                g_.assistAt = V3(r.body(PELVIS).p.x, s.groundY + 0.14f * scale, r.body(PELVIS).p.z);
                g_.assistQ = basisQ(xr, yl, zb);
            }
            if (s.prone || (s.fwd.y < -0.5f && getUpPhaseT_ > 0.4f)) { getUpPhase_ = 1; getUpPhaseT_ = 0; }
            else if (getUpPhaseT_ > 2.2f) { rollSide_ = -rollSide_; getUpPhaseT_ = 0; }
            break;
        }
        case 1: {
            // face down: hands under the shoulders and knees under the hips, then the trunk pushed up onto them
            const float e = smooth(getUpPhaseT_ / 0.5f);
            for (int side = 0; side < 2; side++) { const float m = MIRROR[side]; setJoint(ARM[side][0], lerp(0.4f, 1.45f, e), 0, 0.12f * m, 0.9f * str); setJoint(ARM[side][1], lerp(1.2f, 0.15f, e), 0, 0, 0.9f * str); }
            for (int f = 0; f < 2; f++) { setJoint(LEG[f][0], lerp(0.3f, 1.55f, e), 0, 0, 0.9f * str); setJoint(LEG[f][1], lerp(-0.6f, -2.05f, e), 0, 0, 0.9f * str); setJoint(LEG[f][2], -0.6f, 0, 0, 0.4f); }
            setJoint(SPINE, -0.15f, 0, 0, 0.8f);
            setJoint(NECK, 0.55f, 0, 0, 0.6f);
            g_.lift = e; g_.liftHeight = s.standHeight * 0.55f;
            g_.liftDir = V3(0, 1, 0);
            // the reference: on all fours - hips over the knees, the trunk level, face down
            {
                V3 yl = flat(s.up);
                if (hlen(yl) < 1e-3f) yl = flat(-s.fwd);
                yl = norm(yl);
                const V3 zb(0, 1, 0), xr = cross(yl, zb);
                const V3 pp = r.body(PELVIS).p;
                g_.assist = true; g_.assistMax = 0.7f * e;
                g_.assistAt = V3(pp.x, s.groundY + 0.5f * scale, pp.z);
                g_.assistQ = basisQ(xr, norm(yl + V3(0, 0.25f, 0)), norm(zb - yl * 0.25f));
            }
            // the knees under the hips (the hips up off the ground) is enough: kneeling up raises the chest
            const float hipsUp = r.body(PELVIS).p.y - s.groundY;
            const bool four = (s.height > s.standHeight * 0.42f || hipsUp > 0.42f * r.scale) && s.speed < 1.2f && !s.supine;
            if (four && getUpPhaseT_ > 0.55f) { getUpPhase_ = 2; getUpPhaseT_ = 0; stepSide_ = rand01() < 0.5f ? 0 : 1; }
            else if (getUpPhaseT_ > 2.0f) { getUpPhaseT_ = 0.2f; }
            if (s.supine && getUpPhaseT_ > 0.6f) { getUpPhase_ = 0; getUpPhaseT_ = 0; }
            break;
        }
        case 2: {
            // kneeling up: the trunk comes upright over the knees, one foot brought forward and planted
            const int front = stepSide_ < 0 ? 0 : stepSide_, back = 1 - front;
            const float e = smooth(getUpPhaseT_ / 0.7f);
            g_.torso = true;
            g_.torsoTarget = heading(s.fwd) * expMap(V3(lerp(-1.1f, -0.35f, e), 0, 0));
            g_.torsoGain = 0.8f * str;
            setJoint(LEG[front][0], lerp(1.55f, 1.45f, e), 0, 0, 0.9f * str);
            setJoint(LEG[front][1], lerp(-2.05f, -1.55f, e), 0, 0, 0.9f * str);
            g_.world[LEG[front][2]] = true; g_.worldTarget[LEG[front][2]] = heading(s.fwd); g_.gain[LEG[front][2]] = 0.6f;
            setJoint(LEG[back][0], lerp(1.55f, 0.5f, e), 0, 0, 0.8f * str);
            setJoint(LEG[back][1], lerp(-2.05f, -1.7f, e), 0, 0, 0.8f * str);
            setJoint(LEG[back][2], -0.7f, 0, 0, 0.3f);
            for (int side = 0; side < 2; side++) { const float m = MIRROR[side]; setJoint(ARM[side][0], lerp(1.45f, 0.4f, e), 0, 0.15f * m, 0.5f); setJoint(ARM[side][1], lerp(0.15f, 0.6f, e), 0, 0, 0.5f); }
            setJoint(NECK, 0.3f, 0, 0, 0.6f);
            g_.lift = 1; g_.liftHeight = s.standHeight * lerp(0.5f, 0.62f, e);
            // the reference: kneeling up on the back knee, the front foot planted, the trunk upright over them
            {
                const V3 pp = r.body(PELVIS).p, fp = r.body(FOOT[front]).p;
                g_.assist = true; g_.assistMax = 0.7f;
                g_.assistAt = V3(lerp(pp.x, fp.x, 0.3f * e), s.groundY + lerp(0.5f, 0.62f, e) * scale, lerp(pp.z, fp.z, 0.3f * e));
                g_.assistQ = g_.torsoTarget;
            }
            if (getUpPhaseT_ > 0.8f && s.tilt < rad(45) && (s.foot[front] || getUpPhaseT_ > 1.4f)) { getUpPhase_ = 3; getUpPhaseT_ = 0; }
            else if (getUpPhaseT_ > 2.2f) { getUpPhase_ = 1; getUpPhaseT_ = 0.3f; }
            break;
        }
        default: {
            // standing up over the front foot; the back leg follows it in
            const int front = stepSide_ < 0 ? 0 : stepSide_, back = 1 - front;
            const float e = smooth(getUpPhaseT_ / 0.9f);
            g_.torso = true;
            g_.torsoTarget = heading(s.fwd) * expMap(V3(lerp(-0.35f, 0, e), 0, 0));
            g_.torsoGain = str;
            g_.stance[front] = s.foot[front];
            g_.stance[back] = s.foot[back] && e > 0.6f;
            g_.balance = true;
            g_.copRef = r.body(FOOT[front]).p; g_.hasCopRef = !s.foot[back] || e < 0.6f;
            g_.heightWant = lerp(s.standHeight * 0.62f, s.standHeight * 0.97f, e); g_.heightGain = 1.5f * str;
            // the reference: rising over the front foot, the support easing off as the legs take the weight
            {
                const V3 fp = r.body(FOOT[front]).p, pp = r.body(PELVIS).p;
                g_.assist = true; g_.assistMax = 0.7f * (1 - smooth((getUpPhaseT_ - 0.6f) / 0.6f));
                g_.assistAt = V3(lerp(pp.x, fp.x, 0.5f), s.groundY + lerp(0.62f, 0.80f, e) * scale, lerp(pp.z, fp.z, 0.5f));
                g_.assistQ = g_.torsoTarget;
            }
            setJoint(LEG[front][0], lerp(1.4f, 0.05f, e), 0, 0, 0.6f);
            setJoint(LEG[front][1], lerp(-1.5f, -0.1f, e), 0, 0, 0.6f);
            setJoint(LEG[front][2], 0, 0, 0, 0.5f);
            if (!s.foot[back] || e < 0.6f) {
                const V3 hip = r.jointPoint(LEG[back][0]);
                setReach(2 + back, V3(hip.x, s.groundY + 0.01f * scale, hip.z), 4, cfg.stepForce * 0.6f);
                setJoint(LEG[back][0], 0.6f, 0, 0, 0.4f);
                setJoint(LEG[back][1], -0.9f, 0, 0, 0.4f);
                g_.world[LEG[back][2]] = true; g_.worldTarget[LEG[back][2]] = heading(s.fwd); g_.gain[LEG[back][2]] = 0.5f;
            } else { setJoint(LEG[back][0], 0.05f, 0, 0, 0.5f); setJoint(LEG[back][1], -0.1f, 0, 0, 0.6f); setJoint(LEG[back][2], 0, 0, 0, 0.5f); }
            relaxArms(0.5f);
            setJoint(NECK, 0, 0, 0, 0.7f);
            if (getUpPhaseT_ > 2.4f || s.height < s.standHeight * 0.4f) { getUpPhase_ = 1; getUpPhaseT_ = 0.3f; }
            break;
        }
    }
    // trying harder: each attempt after the first, and a phase that has stalled, is given more of the support - a weak
    // or hurt body is slower to rise, but it rises, rather than failing the same way until it gives up
    if (g_.assist) {
        const float stall = smooth((getUpPhaseT_ - 1.0f) / 1.2f);
        g_.assistMax = std::min(1.1f, g_.assistMax * (1.0f + 0.45f * static_cast<float>(std::max(0, tries - 1)) + 0.6f * stall));
    }
}

void SydneyCharacter::limpGoals(float h) {
    // dying is the loss of postural control, not a slow letting-go: the legs give at once (no stance, no balance, no
    // gravity compensation, no stretch reflex - those are what read as a dead body still trying to catch itself), and
    // only the trunk and neck keep a little tone for a moment, so the body folds at the knees and goes down rather than
    // falling stiff like a plank
    limpT_ += h;
    const float fade = std::max(0.05f, cfg.deathFade), u = limpT_ / fade;
    const float trunk = std::max(0.0f, 1 - u) * 0.3f;
    for (int f = 0; f < 2; f++) { setJoint(LEG[f][0], 0, 0, 0, 0, 0); setJoint(LEG[f][1], 0, 0, 0, 0, 0); setJoint(LEG[f][2], 0, 0, 0, 0, 0); }
    setJoint(SPINE, 0, 0, 0, trunk * cfg.deathPosture * 2);
    setJoint(NECK, 0, 0, 0, trunk);
    relaxArms(trunk * 0.3f);
    g_.tone = std::max(0.0f, 1 - u) * 0.6f;
    g_.reflex = 0;
    g_.gravityComp = false;
}

}  // namespace astratto
