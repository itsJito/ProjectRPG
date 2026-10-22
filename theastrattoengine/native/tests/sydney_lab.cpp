// Astratto Engine - the Sydney lab: Sydney's body and nervous system run on their own in a small world of stone, through
// the scenarios its behaviour is tuned against, with the numbers that say how it went and (with --dump) every frame's
// pose for a contact sheet. Built from the sources directly (not through the C interface), for development.
//
//   astratto_sydney_lab stand|push|die|drop|getup|ledge|walk|sweep [--dump file] [--dir x z] [--impulse n] [--at point]
#define _CRT_SECURE_NO_WARNINGS
#include <cmath>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <string>
#include <vector>

#include "physics/ragdoll.h"

using namespace astratto;
using namespace impulsis;
using namespace syd;

namespace {

const int SX = 48, SY = 24, SZ = 48, FLOOR = 4;
BlockTable table;
VoxelWorld* world = nullptr;
FILE* dump = nullptr;
float frameT = 0;
const char* STATES[] = {"STAND", "STEP", "FALL", "DOWN", "GETUP", "DEAD"};

void makeWorld() {
    astratto_block_def defs[3]{};
    defs[1].flags = ASTRATTO_BLOCK_SOLID | ASTRATTO_BLOCK_OPAQUE;
    table.set(defs, 3);
    world = new VoxelWorld(&table);
    std::vector<uint8_t> cells(static_cast<size_t>(SX) * SY * SZ, 0);
    for (int x = 0; x < SX; x++) for (int z = 0; z < SZ; z++) for (int y = 0; y < SY; y++) {
        bool solid = y < FLOOR;
        // a platform 3 m high, its edge at x = 32 (for ledges and drops)
        if (x >= 32 && x < 44 && z >= 10 && z < 40 && y < FLOOR + 3) solid = true;
        cells[(static_cast<size_t>(x) * SZ + z) * SY + y] = solid ? 1 : 0;
    }
    world->create(SX, SY, SZ, 1, cells.data(), cells.size(), nullptr, 0);
}

/** LAB_CFG="name=value,name=value": the profile's numbers overridden, for tuning runs. */
void tune(SydneyConfig& c) {
    const char* e = std::getenv("LAB_CFG");
    if (!e) return;
    struct F { const char* n; float* p; } fields[] = {
        {"stepTime", &c.stepTime}, {"strength", &c.strength}, {"stepReach", &c.stepReach}, {"stepLift", &c.stepLift}, {"stepOvershoot", &c.stepOvershoot}, {"stepThreshold", &c.stepThreshold},
        {"stepWidth", &c.stepWidth}, {"shiftTime", &c.shiftTime}, {"balanceGain", &c.balanceGain}, {"stanceGain", &c.stanceGain}, {"stanceHip", &c.stanceHip},
        {"standPosture", &c.standPosture}, {"lostReach", &c.lostReach}, {"fallTilt", &c.fallTilt}, {"landBrake", &c.landBrake}, {"hipStrategy", &c.hipStrategy}, {"pelvisGain", &c.pelvisGain}, {"rootMode", &c.rootMode}, {"spineStiff", &c.spineStiff}, {"hipRollKeep", &c.hipRollKeep}, {"rollTwist", &c.rollTwist},
        {"freqSpine", &c.freq[0]}, {"freqHip", &c.freq[1]}, {"freqKnee", &c.freq[2]}, {"zetaHip", &c.zeta[1]}, {"zetaSpine", &c.zeta[0]}};
    std::string all = e;
    size_t at = 0;
    while (at < all.size()) {
        size_t end = all.find(',', at);
        if (end == std::string::npos) end = all.size();
        const std::string kv = all.substr(at, end - at);
        const size_t eq = kv.find('=');
        if (eq != std::string::npos) {
            const std::string name = kv.substr(0, eq);
            const float v = static_cast<float>(std::atof(kv.c_str() + eq + 1));
            if (name == "maxSteps") c.maxSteps = static_cast<int>(v);
            for (const F& f : fields) if (name == f.n) *f.p = v;
        }
        at = end + 1;
    }
}

struct Sim {
    Ragdoll body{1.0f};
    SydneyCharacter* sy = nullptr;
    int lastState = -1, landSide = 0;
    float lastPhase = -1;
    V3 landTo;
    float t = 0;
    Sim(float x, float y, float z, float yaw, bool active) {
        body.gravity = 22.0f;
        body.standAt(x, y, z, yaw);
        if (active) { SydneyConfig c = SydneyConfig::human(); tune(c); sy = new SydneyCharacter(&body, c); }
    }
    ~Sim() { delete sy; }
    void frame(float dt) {
        body.update(world, dt);
        t += dt;
        if (sy && std::getenv("LAB_LAND") && lastPhase >= 0 && (sy->swingSide() < 0 || sy->stepPhase() < lastPhase)) {
            const int f = landSide;
            const V3 fp = body.body(f == 0 ? L_FOOT : R_FOOT).p;
            std::printf("    landed %s foot at (%.3f %.3f), aimed (%.3f %.3f), capture now (%.3f %.3f), com (%.3f %.3f) tilt %.0f\n", f == 0 ? "left" : "right", fp.x, fp.z, landTo.x, landTo.z,
                        sy->s.capture.x, sy->s.capture.z, sy->s.com.x, sy->s.com.z, sy->s.tilt * 57.3f);
        }
        if (sy && sy->swingSide() >= 0) { landSide = sy->swingSide(); landTo = sy->stepTarget(); lastPhase = sy->stepPhase(); } else lastPhase = -1;
        if (sy && sy->state != lastState) {
            std::printf("    %6.2fs  %s%s  (h %.2f, tilt %.0f, capture %+.3f, feet %d, steps %d)\n", t, STATES[sy->state], sy->outcome == SydneyCharacter::RECOVERED ? " [recovered]" : "",
                        sy->s.height, sy->s.tilt * 57.3f, sy->s.captureOut, sy->s.feet, sy->steps);
            lastState = sy->state;
        }
        if (std::getenv("LAB_TRACE") && sy && t < std::atof(std::getenv("LAB_TRACE"))) {
            const SydneySenses& q = sy->s;
            std::printf("  t %.3f com (%.3f %.3f %.3f) v (%+.2f %+.2f %+.2f) cop (%.3f %.3f) want (%.3f %.3f) sup L %.0f R %.0f  K %.2f %.2f\n", t, q.com.x, q.com.y, q.com.z, q.comV.x, q.comV.y, q.comV.z,
                        q.cop.x, q.cop.z, sy->copWanted.x, sy->copWanted.z, q.support[0], q.support[1], sy->lqrGain(0), sy->lqrGain(1));
            std::printf("           knee L want %+.0f got %+.0f  R want %+.0f got %+.0f | hip L x %+.0f/%+.0f  ankle L %+.0f/%+.0f  spine x %+.0f/%+.0f\n",
                        sy->wantTorque[L_KNEE][0], sy->gotTorque[L_KNEE][0], sy->wantTorque[R_KNEE][0], sy->gotTorque[R_KNEE][0], sy->wantTorque[L_HIP][0], sy->gotTorque[L_HIP][0],
                        sy->wantTorque[L_ANKLE][0], sy->gotTorque[L_ANKLE][0], sy->wantTorque[SPINE][0], sy->gotTorque[SPINE][0]);
        }
        if (std::getenv("LAB_GETUP") && sy && sy->state == 4 && ((int)(t * 60) % 6) == 0)
            std::printf("  t %.2f phase %d fwd.y %+.2f up (%+.2f %+.2f %+.2f) h %.2f supine %d prone %d hips L(%+.2f %+.2f %+.2f) spine (%+.2f %+.2f %+.2f)\n", t, sy->getUpPhase(), sy->s.fwd.y, sy->s.up.x, sy->s.up.y, sy->s.up.z, sy->s.height, sy->s.supine, sy->s.prone,
                        sy->jointAngles(L_HIP).x, sy->jointAngles(L_HIP).y, sy->jointAngles(L_HIP).z, sy->jointAngles(SPINE).x, sy->jointAngles(SPINE).y, sy->jointAngles(SPINE).z);
        if (std::getenv("LAB_LEAN") && sy && t > (float)std::atof(std::getenv("LAB_LEAN"))) {
            const V3 tu = body.body(TORSO).R.col(1), pu = body.body(PELVIS).R.col(1);
            std::printf("  t %.2f %-5s torso up (%+.2f %+.2f) pelvis up (%+.2f %+.2f) | spine z %+5.0f/%+5.0f x %+5.0f/%+5.0f | hipL x %+5.0f/%+5.0f hipR x %+5.0f/%+5.0f | sup %4.0f %4.0f\n", t, STATES[sy->state],
                        tu.x, tu.z, pu.x, pu.z, sy->wantTorque[SPINE][2], sy->gotTorque[SPINE][2], sy->wantTorque[SPINE][0], sy->gotTorque[SPINE][0],
                        sy->wantTorque[L_HIP][0], sy->gotTorque[L_HIP][0], sy->wantTorque[R_HIP][0], sy->gotTorque[R_HIP][0], sy->s.support[0], sy->s.support[1]);
        }
        if (std::getenv("LAB_REACH") && sy && sy->state == 2) {
            const V3 sh = body.jointPoint(L_SHOULDER);
            std::printf("  t %.2f com (%.2f %.2f %.2f) v.y %+.2f shoulder (%.2f %.2f) hands sent L %d (%.2f %.2f %.2f) R %d grips %d%d\n", t, sy->s.com.x, sy->s.com.y, sy->s.com.z, sy->s.comV.y,
                        sh.x, sh.y, sy->reaching[0], sy->reachAt[0].x, sy->reachAt[0].y, sy->reachAt[0].z, sy->reaching[1], sy->gripping(0), sy->gripping(1));
        }
        if (std::getenv("LAB_FD") && sy) {
            static float last[12];
            for (int j = 0; j < 12; j++) {
                const float a = sy->jointAngles(j).x, w = -sy->muscle[j][0][0].v / sy->muscle[j][0][0].k;
                if (j == L_KNEE || j == L_ELBOW || j == L_ANKLE) std::printf("  t %.3f joint %d angle %+.3f  d/dt %+.2f  measured %+.2f\n", t, j, a, (a - last[j]) * 60, w);
                last[j] = a;
            }
        }
        if (std::getenv("LAB_OMEGA") && sy) {
            std::printf("  t %.3f knee w L %+.2f R %+.2f  hip w L %+.2f R %+.2f  ankle L %+.2f  spine %+.2f  elbow L %+.2f\n", t, -sy->muscle[L_KNEE][0][0].v / sy->muscle[L_KNEE][0][0].k,
                        -sy->muscle[R_KNEE][0][0].v / sy->muscle[R_KNEE][0][0].k, -sy->muscle[L_HIP][0][0].v / sy->muscle[L_HIP][0][0].k, -sy->muscle[R_HIP][0][0].v / sy->muscle[R_HIP][0][0].k,
                        -sy->muscle[L_ANKLE][0][0].v / sy->muscle[L_ANKLE][0][0].k, -sy->muscle[SPINE][0][0].v / sy->muscle[SPINE][0][0].k, -sy->muscle[L_ELBOW][0][0].v / sy->muscle[L_ELBOW][0][0].k);
        }
        if (std::getenv("LAB_ANGLES") && sy && sy->state == 1) {
            const int sw = sy->swingSide() < 0 ? 0 : sy->swingSide();
            const int hp = sw == 0 ? L_HIP : R_HIP, kn = sw == 0 ? L_KNEE : R_KNEE;
            const V3 a = sy->jointAngles(hp), ta = sy->targetAngles(hp), b = sy->jointAngles(kn), tb = sy->targetAngles(kn);
            std::printf("  t %.3f k %.2f swing hip x %+.2f (want %+.2f, gain %.1f) z %+.2f (%+.2f) knee %+.2f (want %+.2f) | torque hip %+5.0f/%+5.0f knee %+5.0f/%+5.0f\n", t, sy->stepPhase(),
                        a.x, ta.x, sy->servoGain(hp), a.z, ta.z, b.x, tb.x, sy->wantTorque[hp][0], sy->gotTorque[hp][0], sy->wantTorque[kn][0], sy->gotTorque[kn][0]);
            const Muscle& ke = sy->muscle[kn][0][0];
            const Muscle& kf = sy->muscle[kn][0][1];
            std::printf("           knee ext a %.2f u %.2f l %.2f fl %.2f fv %.2f pas %+.0f | flex a %.2f u %.2f l %.2f fl %.2f fv %.2f pas %+.0f\n", ke.a, ke.u, ke.l, ke.fl, ke.fv, ke.passive(), kf.a, kf.u, kf.l, kf.fl, kf.fv, kf.passive());
        }
        if (std::getenv("LAB_KNEE") && sy && sy->state == 1) {
            std::printf("  t %.3f knees L want %+5.0f got %+5.0f len %.2f sup %4.0f | R want %+5.0f got %+5.0f len %.2f sup %4.0f | hips L %+5.0f/%+5.0f R %+5.0f/%+5.0f\n", t,
                        sy->wantTorque[L_KNEE][0], sy->gotTorque[L_KNEE][0], sy->muscle[L_KNEE][0][0].l, sy->s.support[0],
                        sy->wantTorque[R_KNEE][0], sy->gotTorque[R_KNEE][0], sy->muscle[R_KNEE][0][0].l, sy->s.support[1],
                        sy->wantTorque[L_HIP][0], sy->gotTorque[L_HIP][0], sy->wantTorque[R_HIP][0], sy->gotTorque[R_HIP][0]);
        }
        if (std::getenv("LAB_STEP") && sy && sy->swingSide() >= 0) {
            const int sw = sy->swingSide();
            const Body& f = body.body(sw == 0 ? L_FOOT : R_FOOT);
            const V3 to = sy->stepTarget();
            std::printf("  t %.3f k %.2f swing %d foot (%.3f %.3f %.3f) to (%.3f %.3f) capture (%.3f %.3f) com (%.3f %.3f) tilt %.0f sup %.0f/%.0f\n", t, sy->stepPhase(), sw, f.p.x, f.p.y, f.p.z, to.x, to.z,
                        sy->s.capture.x, sy->s.capture.z, sy->s.com.x, sy->s.com.z, sy->s.tilt * 57.3f, sy->s.support[0], sy->s.support[1]);
        }
        if (dump) {
            std::fprintf(dump, "%.4f %d %d", t, sy ? sy->state : 5, sy ? sy->activity : 0);
            for (int i = 0; i < rd::N; i++) std::fprintf(dump, " %.4f %.4f %.4f", body.x[i], body.y[i], body.z[i]);
            std::fprintf(dump, "\n");
        }
        body.impacts.clear();
    }
    void run(float seconds, float dt = 1.0f / 60) { for (float k = 0; k < seconds; k += dt) frame(dt); }
    float headY() const { return body.y[rd::HEAD_P]; }
};

void report(const char* what, Sim& s) {
    const SydneyCharacter* y = s.sy;
    std::printf("  %-34s state %-5s outcome %d  head %.2f m  com h %.2f  tilt %4.0f  capture %+.3f  feet %d  steps %d falls %d  effort %.2f%s\n", what,
                y ? STATES[y->state] : "-", y ? y->outcome : 0, s.headY() - FLOOR, y ? y->s.height : 0, y ? y->s.tilt * 57.3f : 0, y ? y->s.captureOut : 0, y ? y->s.feet : 0,
                y ? y->steps : 0, y ? y->falls : 0, y ? y->effort : 0, y && y->invalid ? "  INVALID" : "");
}

int pointByName(const char* n) {
    if (!std::strcmp(n, "neck")) return rd::NECK;
    if (!std::strcmp(n, "head")) return rd::HEAD_P;
    if (!std::strcmp(n, "pelvis")) return rd::PELVIS;
    if (!std::strcmp(n, "shoulder")) return rd::RS;
    if (!std::strcmp(n, "knee")) return rd::LK;
    return atoi(n);
}

}  // namespace

int main(int argc, char** argv) {
    makeWorld();
    const char* what = argc > 1 ? argv[1] : "stand";
    float dirX = 1, dirZ = 0, impulse = 120, extra = 0;
    int at = rd::NECK;
    for (int i = 2; i < argc; i++) {
        if (!std::strcmp(argv[i], "--dump") && i + 1 < argc) dump = std::fopen(argv[++i], "w");
        else if (!std::strcmp(argv[i], "--dir") && i + 2 < argc) { dirX = static_cast<float>(atof(argv[++i])); dirZ = static_cast<float>(atof(argv[++i])); }
        else if (!std::strcmp(argv[i], "--impulse") && i + 1 < argc) impulse = static_cast<float>(atof(argv[++i]));
        else if (!std::strcmp(argv[i], "--at") && i + 1 < argc) at = pointByName(argv[++i]);
        else if (!std::strcmp(argv[i], "--extra") && i + 1 < argc) extra = static_cast<float>(atof(argv[++i]));
    }
    const std::string w = what;
    if (w == "stand") {
        Sim s(20.5f, FLOOR, 20.5f, 0, true);
        float minHead = 1e9f, maxHead = -1e9f, drift = 0;
        const float x0 = s.body.com().x, z0 = s.body.com().z;
        for (int k = 0; k < 600; k++) {
            s.frame(1.0f / 60);
            if (k > 60) { minHead = std::min(minHead, s.headY()); maxHead = std::max(maxHead, s.headY()); }
            drift = std::max(drift, std::hypot(s.body.com().x - x0, s.body.com().z - z0));
        }
        report("stood ten seconds", s);
        std::printf("    head %.3f..%.3f m over the floor, centre of mass drifted at most %.3f m\n", minHead - FLOOR, maxHead - FLOOR, drift);
        std::printf("    LQR gains on the pendulum: %.3f, %.3f\n", s.sy->lqrGain(0), s.sy->lqrGain(1));
    } else if (w == "push") {
        Sim s(20.5f, FLOOR, 20.5f, 0, true);
        s.run(1.0f);
        const float l = std::hypot(dirX, dirZ);
        s.sy->hit(at, dirX / l, 0, dirZ / l, impulse, impulse * 0.25f);
        s.run(extra > 0 ? extra : 6.0f);
        char buf[96];
        std::snprintf(buf, sizeof buf, "push %.0f N s at %d toward (%.1f, %.1f)", impulse, at, dirX, dirZ);
        report(buf, s);
    } else if (w == "sweep") {
        // pushes of growing size from the four sides: what it takes to make it step, and to put it down
        const float dirs[4][2] = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        float score = 0;
        const char* names[4] = {"from its left (toward +x)", "from its right (toward -x)", "from the front (toward +z, back)", "from behind (toward -z, forward)"};
        for (int d = 0; d < 4; d++) {
            std::printf("  %s\n", names[d]);
            for (float J = std::getenv("LAB_J0") ? (float)std::atof(std::getenv("LAB_J0")) : 20.0f; J <= (std::getenv("LAB_J1") ? (float)std::atof(std::getenv("LAB_J1")) : 260.0f); J += (std::getenv("LAB_JS") ? (float)std::atof(std::getenv("LAB_JS")) : 40.0f)) {
                Sim s(20.5f, FLOOR, 20.5f, 0, true);
                s.run(1.0f);
                s.sy->hit(std::getenv("LAB_AT") ? pointByName(std::getenv("LAB_AT")) : rd::NECK, dirs[d][0], 0, dirs[d][1], J, J * 0.25f);
                int maxSteps = 0, fell = 0;
                for (int k = 0; k < 360; k++) { s.frame(1.0f / 60); maxSteps = std::max(maxSteps, s.sy->steps); if (s.sy->state == SydneyCharacter::FALL) fell = 1; }
                std::printf("    %4.0f N s: %-5s steps %d%s  head %.2f\n", J, STATES[s.sy->state], maxSteps, fell ? "  fell" : "", s.headY() - FLOOR);
                if (!fell && s.sy->state == SydneyCharacter::STAND) score += J;
            }
        }
        std::printf("  score %.0f\n", score);
    } else if (w == "die") {
        Sim s(20.5f, FLOOR, 20.5f, 0, true);
        s.run(1.0f);
        s.sy->hit(rd::NECK, 1, 0, 0, 120, 30);
        s.sy->die();
        float settledAt = -1;
        for (int k = 0; k < 900 && settledAt < 0; k++) { s.frame(1.0f / 60); if (s.body.settled) settledAt = s.t; }
        report("died", s);
        float lo = 1e9f, hi = -1e9f;
        for (int i = 0; i < rd::N; i++) { lo = std::min(lo, s.body.y[i]); hi = std::max(hi, s.body.y[i]); }
        std::printf("    settled at %.2f s; points between %.3f and %.3f m over the floor\n", settledAt, lo - FLOOR, hi - FLOOR);
    } else if (w == "drop") {
        // walked off the platform's edge
        Sim s(31.4f, FLOOR + 3, 25.5f, 1.5707963f, true);
        s.run(0.5f);
        s.sy->hit(rd::NECK, -1, 0, 0, 90, 10);
        s.run(extra > 0 ? extra : 8.0f);
        report("pushed off a 3 m ledge", s);
    } else if (w == "ledge") {
        // standing at the edge with its back to the drop, shoved over it: the hands should find the edge
        Sim s(32.35f, FLOOR + 3, 25.5f, -1.5707963f, true);
        s.run(0.5f);
        s.sy->hit(rd::NECK, -1, 0, 0, impulse, 10);
        float hung = 0;
        for (int k = 0; k < 480; k++) { s.frame(1.0f / 60); if (s.sy->gripping(0) || s.sy->gripping(1)) hung += 1.0f / 60; }
        report("shoved backward off a ledge", s);
        std::printf("    hung on for %.2f s\n", hung);
    } else if (w == "getup") {
        Sim s(20.5f, FLOOR, 20.5f, 0, true);
        s.run(0.5f);
        s.sy->hit(rd::NECK, 0, 0, dirZ >= 0 ? 1.0f : -1.0f, 320, 60);
        float upAt = -1;
        for (int k = 0; k < 60 * 20 && upAt < 0; k++) { s.frame(1.0f / 60); if (s.sy->state == SydneyCharacter::STAND && s.sy->falls > 0) upAt = s.t; }
        if (upAt > 0) s.run(3.0f);
        report("knocked down, getting up", s);
        std::printf("    back on its feet at %.2f s (tries %d)\n", upAt, s.sy->tries);
    } else if (w == "tilt") {
        // the trunk set turning (2 rad/s about the given axis), nothing else: how the torso is brought back
        Sim s(20.5f, FLOOR, 20.5f, 0, true);
        s.run(1.0f);
        const float l = std::hypot(dirX, dirZ);
        s.body.body(TORSO).w += V3(dirX / l, 0, dirZ / l) * (extra > 0 ? extra : 2.0f);
        for (int k = 0; k < 120; k++) {
            s.frame(1.0f / 60);
            if (k % 6 == 0) std::printf("    %.2fs tilt %4.1f  pelvis tilt %4.1f  com (%.3f %.3f)  capture out %+.3f  feet %d\n", s.t, s.sy->s.tilt * 57.3f,
                                        std::acos(std::max(-1.0f, std::min(1.0f, s.body.body(PELVIS).R.col(1).y))) * 57.3f, s.sy->s.com.x, s.sy->s.com.z, s.sy->s.captureOut, s.sy->s.feet);
        }
        report("trunk set turning", s);
    } else if (w == "lift") {
        // a deliberate step forward with the left foot, 30 cm, from quiet standing
        Sim s(20.5f, FLOOR, 20.5f, 0, true);
        s.run(1.0f);
        const int side = std::getenv("LAB_SIDE") ? std::atoi(std::getenv("LAB_SIDE")) : 0;
        const V3 f = s.body.body(side == 0 ? L_FOOT : R_FOOT).p;
        const float dx = std::getenv("LAB_DX") ? (float)std::atof(std::getenv("LAB_DX")) : 0.0f, dz = std::getenv("LAB_DZ") ? (float)std::atof(std::getenv("LAB_DZ")) : -0.3f;
        s.sy->forceStep(side, V3(f.x + dx, FLOOR, f.z + dz));
        s.run(extra > 0 ? extra : 2.0f);
        report("a deliberate step", s);
        const V3 g = s.body.body(side == 0 ? L_FOOT : R_FOOT).p;
        std::printf("    the stepping foot moved (%.3f, %.3f)\n", g.x - f.x, g.z - f.z);
    } else if (w == "walk") {
        Sim s(20.5f, FLOOR, 10.5f, 3.1415927f, true);
        s.run(0.5f);
        // LAB_WALK=<speed> (negative: backward), the way it faces
        const char* ws = std::getenv("LAB_WALK");
        const float sp = ws ? static_cast<float>(std::atof(ws)) : 1.1f;
        const impulsis::V3 f = impulsis::rotate(s.body.body(syd::TORSO).q, impulsis::V3(0, 0, -1));
        const float fl = std::sqrt(f.x * f.x + f.z * f.z);
        SydneyIntent i; i.kind = ASTRATTO_INTENT_WALK; i.x = f.x / fl * sp; i.z = f.z / fl * sp;
        s.sy->setIntent(i);
        const impulsis::V3 c0 = s.body.com();
        s.run(extra > 0 ? extra : 8.0f);
        report("walking ahead", s);
        const impulsis::V3 c1 = s.body.com();
        std::printf("    went %.2f m along the way it faced (%.2f m across)\n", ((c1.x - c0.x) * f.x + (c1.z - c0.z) * f.z) / fl, ((c1.x - c0.x) * f.z - (c1.z - c0.z) * f.x) / fl);
    }
    if (dump) std::fclose(dump);
    return 0;
}
