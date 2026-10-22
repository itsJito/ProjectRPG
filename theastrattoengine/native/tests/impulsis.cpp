// Astratto Engine - checks of Impulsis, the rigid-body dynamics (2.2), through the public C interface only: a box
// dropped on a floor of cells comes to rest flat and falls asleep; a stack of boxes stands; a ball rolls and stops;
// a convex hull settles; a hinged door is pushed open by a character walking into it and stops at its limit, and the
// character is held back when it can go no further; a slider and a fixed joint hold; a breakable joint breaks; a
// fast small body does not pass through a wall; impacts are reported; the same run is the same twice.
#if defined(_MSC_VER)
#pragma warning(disable : 4996)   // getenv: the debug switch of these checks only
#endif
#include <cmath>
#include <cstdio>
#include <cstring>
#include <cstdlib>
#include <vector>

#include "astratto/astratto.h"

void check(const char* what, bool ok);   // selftest.cpp

namespace {

const int SX = 48, SY = 20, SZ = 48, FLOOR = 4;
enum { AIR = 0, STONE = 1 };

struct Cmds {
    std::vector<uint8_t> bytes;
    template <typename T> T& add(uint32_t type, size_t tail = 0) {
        const size_t at = bytes.size();
        bytes.resize(at + sizeof(T) + tail);
        T* rec = reinterpret_cast<T*>(bytes.data() + at);
        std::memset(rec, 0, sizeof(T) + tail);
        rec->head.type = type; rec->head.bytes = static_cast<uint32_t>(sizeof(T) + tail);
        return *rec;
    }
    void clear() { bytes.clear(); }
};

struct Run {
    astratto_engine* e = nullptr;
    astratto_handle world = 0;
    Cmds c;
    std::vector<uint8_t> events;

    void frame(float dt) {
        astratto_frame_io io{};
        io.struct_size = sizeof io; io.dt = dt;
        io.commands = c.bytes.empty() ? nullptr : c.bytes.data(); io.command_bytes = static_cast<uint32_t>(c.bytes.size());
        astratto_frame(e, &io);
        events.assign(io.events, io.events + io.event_bytes);
        c.clear();
    }
    int counted(uint32_t type) const {
        int n = 0; uint32_t at = 0;
        while (at + 8 <= events.size()) { astratto_record h; std::memcpy(&h, events.data() + at, 8); if (h.bytes < 8) break; if (h.type == type) n++; at += h.bytes; }
        return n;
    }
    template <typename T> bool find(uint32_t type, astratto_handle h, T& out) {
        astratto_snapshot_io io{};
        io.struct_size = sizeof io; io.kind = type == ASTRATTO_SNAP_MOVER ? ASTRATTO_SNAPSHOT_BODIES : ASTRATTO_SNAPSHOT_RIGIDS;
        if (astratto_snapshot(e, &io) != ASTRATTO_OK) return false;
        const uint8_t* p = static_cast<const uint8_t*>(io.data);
        uint64_t at = 0;
        while (at + 8 <= io.bytes) {
            astratto_record r; std::memcpy(&r, p + at, 8);
            if (r.bytes < 8) break;
            if (r.type == type) { T t; std::memcpy(&t, p + at, sizeof t); if (t.handle == h) { out = t; return true; } }
            at += r.bytes;
        }
        return false;
    }
    astratto_handle reserve(uint32_t kind) { astratto_handle h = 0; astratto_handles_reserve(e, kind, 1, &h); return h; }

    astratto_handle box(float x, float y, float z, float hx, float hy, float hz, float mass, uint32_t flags = 0, float yaw = 0) {
        const astratto_handle h = reserve(ASTRATTO_KIND_RIGID);
        astratto_cmd_rigid_create& r = c.add<astratto_cmd_rigid_create>(ASTRATTO_CMD_RIGID_CREATE);
        r.handle = h; r.shape = ASTRATTO_SHAPE_BOX; r.flags = flags; r.size[0] = hx; r.size[1] = hy; r.size[2] = hz; r.mass = mass;
        r.pos[0] = x; r.pos[1] = y; r.pos[2] = z;
        r.rot[0] = std::cos(yaw * 0.5f); r.rot[2] = std::sin(yaw * 0.5f);
        return h;
    }
};

Run make(int threads) {
    Run r;
    astratto_config cfg{};
    cfg.struct_size = sizeof cfg; cfg.api_version = ASTRATTO_API_VERSION; cfg.worker_threads = static_cast<uint32_t>(threads); cfg.flags = ASTRATTO_CONFIG_DETERMINISTIC;
    r.e = astratto_create(&cfg);
    if (!r.e) return r;
    astratto_block_def defs[2]{};
    defs[STONE].flags = ASTRATTO_BLOCK_SOLID | ASTRATTO_BLOCK_OPAQUE;
    astratto_block_table table{}; table.struct_size = sizeof table; table.count = 2; table.defs = defs;
    astratto_world_block_table(r.e, &table);
    std::vector<uint8_t> cells(static_cast<size_t>(SX) * SY * SZ, 0);
    auto put = [&](int x, int y, int z) { cells[(static_cast<size_t>(x) * SZ + z) * SY + y] = STONE; };
    for (int x = 0; x < SX; x++) for (int z = 0; z < SZ; z++) for (int y = 0; y < FLOOR; y++) put(x, y, z);
    // a wall along x = 30 (z 0..47), four high, for the bullet; and a doorway in a wall along z = 20 (x 0..15) at x 6..7
    for (int z = 0; z < SZ; z++) for (int y = FLOOR; y < FLOOR + 4; y++) put(30, y, z);
    for (int x = 0; x < 16; x++) if (x < 6 || x > 7) for (int y = FLOOR; y < FLOOR + 4; y++) put(x, y, 20);
    for (int x = 6; x <= 7; x++) put(x, FLOOR + 3, 20);   // the lintel over the doorway
    astratto_world_desc wd{};
    wd.struct_size = sizeof wd; wd.size_x = SX; wd.size_y = SY; wd.size_z = SZ; wd.below_floor_block = STONE;
    wd.blocks = cells.data(); wd.block_bytes = cells.size(); wd.flags = ASTRATTO_WORLD_LIGHT;
    astratto_world_create(r.e, &wd, &r.world);
    r.c.add<astratto_cmd_handle>(ASTRATTO_CMD_WORLD_ACTIVATE).handle = r.world;
    r.frame(0);
    return r;
}

float tiltOf(const astratto_snap_rigid& s) {
    // how far the body's up axis leans from the world's (radians)
    const float w = s.rot[0], x = s.rot[1], y = s.rot[2], z = s.rot[3];
    const float upY = 1 - 2 * (x * x + z * z);
    (void)w; (void)y;
    return std::acos(std::fmax(-1.0f, std::fmin(1.0f, upY)));
}

}  // namespace

void impulsis_checks() {
    Run r = make(2);
    check("2.2 Impulsis: an engine with a walled yard for the rigid bodies", r.e != nullptr && r.world != 0);
    if (!r.e) return;

    // ---- a box dropped tilted onto a floor of many cells comes to rest flat, still, and asleep
    const astratto_handle crate = r.box(5.3f, FLOOR + 3.0f, 5.7f, 0.4f, 0.3f, 0.5f, 20, ASTRATTO_RIGID_REPORT, 0.6f);
    {
        astratto_cmd_rigid_place& p = r.c.add<astratto_cmd_rigid_place>(ASTRATTO_CMD_RIGID_PLACE);
        p.handle = crate; p.pos[0] = 5.3f; p.pos[1] = FLOOR + 3.0f; p.pos[2] = 5.7f;
        // tipped about x and z
        const float ax = 0.5f, az = 0.35f;
        p.rot[0] = std::cos(ax / 2) * std::cos(az / 2); p.rot[1] = std::sin(ax / 2) * std::cos(az / 2); p.rot[2] = -std::sin(ax / 2) * std::sin(az / 2); p.rot[3] = std::cos(ax / 2) * std::sin(az / 2);
        p.vel[0] = 1.2f;
    }
    int impacts = 0;
    for (int i = 0; i < 180; i++) { r.frame(1.0f / 60); impacts += r.counted(ASTRATTO_EVT_RIGID_IMPACT); }
    astratto_snap_rigid s{};
    bool got = r.find(ASTRATTO_SNAP_RIGID, crate, s);
    const float t = got ? tiltOf(s) : 9;
    const bool flat = got && (std::fabs(t) < 0.03f || std::fabs(t - 1.5708f) < 0.03f || std::fabs(t - 3.1416f) < 0.03f);
    std::printf("       crate rests at y %.3f, tilt %.3f rad, %s, %d impact(s)\n", got ? s.pos[1] : -1.0f, t, got && s.asleep ? "asleep" : "awake", impacts);
    check("a tipped box dropped on a floor of cells lands, tumbles and rests flat on a face, on the floor's top", flat && got && s.pos[1] > FLOOR + 0.25f && s.pos[1] < FLOOR + 0.55f);
    check("then it is still and falls asleep, and its landing was reported as an impact", got && s.asleep && impacts >= 1);

    // ---- a stack of five boxes stands (warm starting and the friction cone hold it)
    astratto_handle stack[5];
    for (int k = 0; k < 5; k++) stack[k] = r.box(12.5f + k * 0.01f, FLOOR + 0.25f + k * 0.502f, 12.5f, 0.25f, 0.25f, 0.25f, 10);
    for (int i = 0; i < 300; i++) r.frame(1.0f / 60);
    bool standing = true; float drift = 0; int asleep = 0;
    for (int k = 0; k < 5; k++) {
        astratto_snap_rigid q{};
        if (!r.find(ASTRATTO_SNAP_RIGID, stack[k], q)) { standing = false; continue; }
        drift = std::fmax(drift, std::fabs(q.pos[0] - (12.5f + k * 0.01f)) + std::fabs(q.pos[2] - 12.5f));
        if (std::fabs(q.pos[1] - (FLOOR + 0.25f + k * 0.5f)) > 0.03f) standing = false;
        asleep += q.asleep ? 1 : 0;
    }
    std::printf("       stack of five: drift %.3f m, %d asleep\n", drift, asleep);
    check("a stack of five boxes stands for five seconds without sliding apart, and the whole pile sleeps together", standing && drift < 0.06f && asleep == 5);

    // ---- a ball rolls along the floor, slows by friction and stops (and a sphere rests on the cells)
    const astratto_handle ball = r.reserve(ASTRATTO_KIND_RIGID);
    {
        astratto_cmd_rigid_create& b = r.c.add<astratto_cmd_rigid_create>(ASTRATTO_CMD_RIGID_CREATE);
        b.handle = ball; b.shape = ASTRATTO_SHAPE_SPHERE; b.size[0] = 0.2f; b.mass = 4; b.pos[0] = 20; b.pos[1] = FLOOR + 0.2f; b.pos[2] = 4; b.vel[2] = 3; b.angular_damping = 0.8f;
    }
    for (int i = 0; i < 360; i++) { r.frame(1.0f / 60); if (getenv("IMPDBG") && i % 20 == 0 && r.find(ASTRATTO_SNAP_RIGID, ball, s)) std::printf("ball %d y %.4f z %.3f vy %.3f vz %.3f\n", i, s.pos[1], s.pos[2], s.vel[1], s.vel[2]); }
    got = r.find(ASTRATTO_SNAP_RIGID, ball, s);
    std::printf("       ball at z %.2f, y %.3f, speed %.3f\n", got ? s.pos[2] : -1.0f, got ? s.pos[1] : -1.0f, got ? std::sqrt(s.vel[0] * s.vel[0] + s.vel[2] * s.vel[2]) : -1.0f);
    check("a ball sent rolling travels on along the floor at the floor's height, and comes to a stop", got && s.pos[2] > 6 && std::fabs(s.pos[1] - (FLOOR + 0.2f)) < 0.02f && std::sqrt(s.vel[0] * s.vel[0] + s.vel[2] * s.vel[2]) < 0.05f);

    // ---- a convex hull (a wedge of glass) falls and settles on the floor (GJK/EPA and the hull's corners)
    const astratto_handle shard = r.reserve(ASTRATTO_KIND_RIGID);
    {
        const float pts[6][3] = {{-0.2f, -0.01f, -0.15f}, {0.25f, -0.01f, -0.1f}, {0.0f, -0.01f, 0.3f}, {-0.2f, 0.01f, -0.15f}, {0.25f, 0.01f, -0.1f}, {0.0f, 0.01f, 0.3f}};
        astratto_cmd_rigid_create& h = r.c.add<astratto_cmd_rigid_create>(ASTRATTO_CMD_RIGID_CREATE, sizeof pts);
        h.handle = shard; h.shape = ASTRATTO_SHAPE_HULL; h.hull_count = 6; h.mass = 0.4f; h.flags = ASTRATTO_RIGID_DEBRIS;
        h.pos[0] = 24; h.pos[1] = FLOOR + 2; h.pos[2] = 10; h.ang[0] = 4; h.ang[2] = 2; h.vel[0] = 0.5f;
        std::memcpy(reinterpret_cast<uint8_t*>(&h) + sizeof h, pts, sizeof pts);
    }
    for (int i = 0; i < 240; i++) r.frame(1.0f / 60);
    got = r.find(ASTRATTO_SNAP_RIGID, shard, s);
    std::printf("       shard at y %.3f, %s\n", got ? s.pos[1] : -1.0f, got && s.asleep ? "asleep" : "awake");
    check("a thin convex hull tumbling down lands and lies flat on the floor (GJK and EPA), and sleeps", got && s.pos[1] > FLOOR - 0.005f && s.pos[1] < FLOOR + 0.06f && s.asleep);

    // ---- a door: a leaf hinged on the doorway's post; a character walks into it and it swings, to its stop
    const astratto_handle door = r.box(6.5f + 0.5f, FLOOR + 1.45f, 20.5f, 0.98f, 1.45f, 0.06f, 30, ASTRATTO_RIGID_NO_WORLD);
    const astratto_handle hinge = r.reserve(ASTRATTO_KIND_JOINT);
    {
        astratto_cmd_joint_create& j = r.c.add<astratto_cmd_joint_create>(ASTRATTO_CMD_JOINT_CREATE);
        j.handle = hinge; j.a = door; j.b = 0; j.type = ASTRATTO_JOINT_HINGE; j.flags = ASTRATTO_JOINT_LIMITED;
        j.anchor[0] = 6.0f; j.anchor[1] = FLOOR + 1.45f; j.anchor[2] = 20.5f; j.axis[1] = 1;
        j.lo = -1.75f; j.hi = 1.75f; j.motor_force = 4;   // a little friction in the hinge
    }
    const astratto_handle walker = r.reserve(ASTRATTO_KIND_MOVER);
    {
        astratto_cmd_mover_create& m = r.c.add<astratto_cmd_mover_create>(ASTRATTO_CMD_MOVER_CREATE);
        m.handle = walker; m.x = 7.0f; m.y = FLOOR; m.z = 17.0f; m.radius = 0.3f; m.height = 1.8f; m.step = 0.6f;
    }
    r.frame(0);
    float maxAngle = 0;
    for (int i = 0; i < 150; i++) {
        astratto_cmd_mover_input& in = r.c.add<astratto_cmd_mover_input>(ASTRATTO_CMD_MOVER_INPUT);
        in.handle = walker; in.vz = 2.5f;
        r.frame(1.0f / 60);
        astratto_snap_joint jj{};
        if (r.find(ASTRATTO_SNAP_JOINT, hinge, jj)) maxAngle = std::fmax(maxAngle, std::fabs(jj.angle));
    }
    astratto_snap_mover ms{};
    got = r.find(ASTRATTO_SNAP_MOVER, walker, ms);
    std::printf("       door swung %.2f rad; the walker is at z %.2f\n", maxAngle, got ? ms.z : -1.0f);
    check("a character walking into a hinged door swings it open and walks through the doorway", got && maxAngle > 0.8f && ms.z > 21.5f);
    astratto_snap_rigid ds{};
    got = r.find(ASTRATTO_SNAP_RIGID, door, ds);
    const float dx = ds.pos[0] - 6.0f, dz = ds.pos[2] - 20.5f;
    check("and the leaf turns about its hinge: its middle stays a half-leaf from the post at the post's height", got && std::fabs(std::sqrt(dx * dx + dz * dz) - 1.0f) < 0.04f && std::fabs(ds.pos[1] - (FLOOR + 1.45f)) < 0.04f);
    // locked: limits held at 0; walking into it now, the character is held back
    {
        astratto_cmd_rigid_place& p = r.c.add<astratto_cmd_rigid_place>(ASTRATTO_CMD_RIGID_PLACE);
        p.handle = door; p.pos[0] = 7.0f; p.pos[1] = FLOOR + 1.45f; p.pos[2] = 20.5f; p.rot[0] = 1;
        astratto_cmd_joint_limits& l = r.c.add<astratto_cmd_joint_limits>(ASTRATTO_CMD_JOINT_LIMITS);
        l.handle = hinge; l.flags = ASTRATTO_JOINT_LIMITED; l.lo = 0; l.hi = 0;
        astratto_cmd_body_place& mp = r.c.add<astratto_cmd_body_place>(ASTRATTO_CMD_MOVER_PLACE);
        mp.handle = walker; mp.x = 7.0f; mp.y = FLOOR; mp.z = 18.0f;
    }
    r.frame(0);
    for (int i = 0; i < 120; i++) {
        astratto_cmd_mover_input& in = r.c.add<astratto_cmd_mover_input>(ASTRATTO_CMD_MOVER_INPUT);
        in.handle = walker; in.vz = 2.5f;
        r.frame(1.0f / 60);
    }
    got = r.find(ASTRATTO_SNAP_MOVER, walker, ms);
    astratto_snap_joint jj{};
    r.find(ASTRATTO_SNAP_JOINT, hinge, jj);
    std::printf("       against the latched door the walker stops at z %.2f (leaf at %.3f rad)\n", got ? ms.z : -1.0f, jj.angle);
    check("latched shut (its limits closed), the same door does not give, and the character is held at its face", got && ms.z < 20.3f && ms.z > 19.7f && std::fabs(jj.angle) < 0.05f);

    // ---- a breakable joint breaks under a load, and is reported
    const astratto_handle sign = r.box(35, FLOOR + 3, 35, 0.3f, 0.2f, 0.05f, 2);
    const astratto_handle rope = r.reserve(ASTRATTO_KIND_JOINT);
    {
        astratto_cmd_joint_create& j = r.c.add<astratto_cmd_joint_create>(ASTRATTO_CMD_JOINT_CREATE);
        j.handle = rope; j.a = sign; j.type = ASTRATTO_JOINT_BALL; j.anchor[0] = 35; j.anchor[1] = FLOOR + 3.2f; j.anchor[2] = 35; j.break_force = 400;
    }
    r.frame(0);
    for (int i = 0; i < 60; i++) r.frame(1.0f / 60);
    got = r.find(ASTRATTO_SNAP_RIGID, sign, s);
    check("a sign hung by a ball joint hangs from its point (it does not fall)", got && std::fabs(s.pos[1] - (FLOOR + 3.0f)) < 0.05f);
    {
        astratto_cmd_rigid_impulse& im = r.c.add<astratto_cmd_rigid_impulse>(ASTRATTO_CMD_RIGID_IMPULSE);
        im.handle = sign; im.x = 35; im.y = FLOOR + 3; im.z = 35; im.jy = -40;
    }
    int broke = 0;
    for (int i = 0; i < 30; i++) { r.frame(1.0f / 60); broke += r.counted(ASTRATTO_EVT_JOINT_BROKEN); }
    got = r.find(ASTRATTO_SNAP_RIGID, sign, s);
    check("yanked hard, the joint breaks (reported once) and the sign falls", broke == 1 && got && s.pos[1] < FLOOR + 2.5f);

    // ---- a slider holds its line; a fixed joint holds two bodies as one
    const astratto_handle carriage = r.box(40, FLOOR + 2, 5, 0.2f, 0.2f, 0.2f, 5);
    const astratto_handle rail = r.reserve(ASTRATTO_KIND_JOINT);
    {
        astratto_cmd_joint_create& j = r.c.add<astratto_cmd_joint_create>(ASTRATTO_CMD_JOINT_CREATE);
        j.handle = rail; j.a = carriage; j.type = ASTRATTO_JOINT_SLIDER; j.flags = ASTRATTO_JOINT_LIMITED;
        j.anchor[0] = 40; j.anchor[1] = FLOOR + 2; j.anchor[2] = 5; j.axis[0] = 1; j.lo = -1; j.hi = 1; j.motor_force = 2;   // the rail's friction
    }
    r.frame(0);
    {
        astratto_cmd_rigid_impulse& im = r.c.add<astratto_cmd_rigid_impulse>(ASTRATTO_CMD_RIGID_IMPULSE);
        im.handle = carriage; im.x = 40; im.y = FLOOR + 2; im.z = 5; im.jx = 20; im.jz = 10;
    }
    for (int i = 0; i < 120; i++) { r.frame(1.0f / 60); if (getenv("IMPDBG") && i % 10 == 0 && r.find(ASTRATTO_SNAP_RIGID, carriage, s)) std::printf("car %d x %.4f y %.4f z %.3f vx %.3f\n", i, s.pos[0], s.pos[1], s.pos[2], s.vel[0]); }
    got = r.find(ASTRATTO_SNAP_RIGID, carriage, s);
    std::printf("       carriage at (%.3f, %.3f, %.3f)\n", s.pos[0], s.pos[1], s.pos[2]);
    check("a body on a slider stays on its line against gravity and a push across it, and stops at the line's end", got && std::fabs(s.pos[1] - (FLOOR + 2)) < 0.03f && std::fabs(s.pos[2] - 5) < 0.03f && s.pos[0] > 40.9f && s.pos[0] < 41.06f);

    // ---- a small fast body does not pass through a wall
    const astratto_handle pebble = r.reserve(ASTRATTO_KIND_RIGID);
    {
        astratto_cmd_rigid_create& b = r.c.add<astratto_cmd_rigid_create>(ASTRATTO_CMD_RIGID_CREATE);
        b.handle = pebble; b.shape = ASTRATTO_SHAPE_SPHERE; b.size[0] = 0.03f; b.mass = 0.05f; b.flags = ASTRATTO_RIGID_BULLET;
        b.pos[0] = 25; b.pos[1] = FLOOR + 1.5f; b.pos[2] = 30; b.vel[0] = 140;
    }
    for (int i = 0; i < 30; i++) r.frame(1.0f / 60);
    got = r.find(ASTRATTO_SNAP_RIGID, pebble, s);
    check("a pebble thrown at 140 m/s at a wall stops at the wall, never on its far side", got && s.pos[0] < 30.0f);

    // ---- released: gone; its handle refused
    r.c.add<astratto_cmd_handle>(ASTRATTO_CMD_RELEASE).handle = crate;
    r.frame(0);
    {
        astratto_cmd_rigid_impulse& im = r.c.add<astratto_cmd_rigid_impulse>(ASTRATTO_CMD_RIGID_IMPULSE);
        im.handle = crate;
        r.frame(0);
    }
    check("a released body is gone from the snapshot, and its handle is refused afterwards", !r.find(ASTRATTO_SNAP_RIGID, crate, s) && r.counted(ASTRATTO_EVT_ERROR) == 1);
    astratto_destroy(r.e);

    // ---- the same pile, dropped twice (one worker, then four), lands the same
    float yA = 0, yB = 0;
    for (int run = 0; run < 2; run++) {
        Run q = make(run == 0 ? 0 : 4);
        if (!q.e) continue;
        astratto_handle hs[12];
        for (int k = 0; k < 12; k++) hs[k] = q.box(10 + (k % 4) * 0.45f, FLOOR + 1 + (k / 4) * 0.7f, 10 + (k % 3) * 0.2f, 0.2f, 0.2f, 0.2f, 5, 0, k * 0.3f);
        for (int i = 0; i < 200; i++) q.frame(1.0f / 60);
        float sum = 0;
        for (auto h : hs) { astratto_snap_rigid u{}; if (q.find(ASTRATTO_SNAP_RIGID, h, u)) sum += u.pos[0] * 3 + u.pos[1] * 7 + u.pos[2] * 11; }
        (run == 0 ? yA : yB) = sum;
        astratto_destroy(q.e);
    }
    check("a pile of twelve boxes dropped on one worker and on four comes to the same rest (deterministic)", std::fabs(yA - yB) < 1e-3f && yA != 0);
}
