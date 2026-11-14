package rpg.actor;

import static astratto.bridge.AstrattoNative.*;

import rpg.Rpg;
import rpg.skill.Skill;

/**
 * (0.3.4, gamefixes: swimming and a new water system) Movement in water, run on its own fixed 20 Hz step (0.05 s) apart
 * from the frame rate and from the walking physics. While a body is in water this controller owns it: the walking
 * mover is posed and given the positions found here, and handed back - with the velocity it has - when the body leaves
 * the water.
 *
 * <p>The state is kept apart in {@link State}: position, velocity (blocks per tick), the camera's yaw and pitch, the
 * movement input, the sprint, whether swimming, the water contact, whether the eyes are under and the collision flags.
 * Each tick, in this order: the input is read and normalised (no diagonal advantage) and turned by the camera's yaw;
 * the water is sampled (the body's box against the water's partial heights, the eyes on their own); the swimming state
 * and the box's size are settled (a swimmer is a 0.6 m cube, and stands up again only where there is room); the push of
 * the strokes is added (0.02 a tick); the vertical controls - pitch steering while swimming (a pull toward where the
 * camera looks, never up out through the surface), the jump key rising, the crouch key diving, the buoyancy of a body
 * in water two blocks deep; the movement is swept against the solid world axis by axis, keeping what is unobstructed and
 * stopping what is not; then the drag (80% of the horizontal kept, 90% in a sprint, 80% of the vertical) and gravity
 * (0.02 down). Running water adds its own push. A swimmer pressed against a bank with open air above it is helped up
 * and out (0.3 upward) - never up an underwater wall - and a strong one (Agility) holding the sprint hauls out onto a
 * higher ledge.
 *
 * <p>Swimming trains Agility, and Agility makes it easier: stronger strokes, a better float, a longer breath. Under
 * water the breath runs out; after that, drowning.
 *
 * <p>Animation is never consulted here: the rig reads {@link Actor#swimMode} and the eased pose that this leaves on the
 * actor, and nothing the rig does feeds back.
 */
public final class Swimming {
    public static final float TICK = 0.05f;
    /** The constants, per tick: stroke push, drag (kept fraction), gravity, the jump's rise and the crouch's dive, the bank's lift. */
    public static final float ACCEL = 0.02f, DRAG = 0.8f, DRAG_SPRINT = 0.9f, DRAG_UP = 0.8f, GRAVITY = 0.02f, RISE = 0.04f, DIVE = 0.04f, EXIT_LIFT = 0.3f;
    /** A current's push: m/s of current to blocks per tick per tick. */
    // (0.4.5, gamefixes: rivers pulled the player too hard - a swimmer was carried at nearly twice the water's own pace)
    // a body afloat drifts at about two thirds of the water's speed, and a strong swimmer makes headway against it
    public static final float FLOW_K = rpg.Tuning.f("water.swimPush", 0.0065f);
    /** (0.3.6) Water this many blocks deep or more floats a body; shallower, one standing on the bottom wades. */
    public static final int FLOAT_DEPTH = 2;
    /** (0.3.6) How much of its pace a body wading keeps, at the deepest wading (the water to the chest) and at the shin. */
    public static final float WADE_DEEP = 0.5f, WADE_SHALLOW = 0.85f;
    /** How much of the current a wading body takes (at the deepest wading; scaled by how deep it stands). */
    public static final float WADE_FLOW = rpg.Tuning.f("water.wadePush", 0.35f);
    /** The share of a walking body's pace the water it stands in leaves it (1 on dry ground). */
    public float wadePace(Actor a) {
        if (a.swimMode != WADE) return 1;
        return WADE_SHALLOW + (WADE_DEEP - WADE_SHALLOW) * wadeShare(a);
    }
    /** How deep a wading body stands, 0 (the ankles) .. 1 (the water to the chest). */
    public float wadeShare(Actor a) {
        float r = Math.max(0.2f, a.radius * 0.8f);
        float sub = g.fluids.submerged(a.x - r, a.y, a.z - r, a.x + r, a.y + a.height, a.z + r);
        return Math.max(0, Math.min(1, sub / Math.max(0.5f, a.height * 0.6f)));
    }
    /** A swimmer's box (a cube), the depth a body must stand in for the water to take it, and the depth it lets go at. */
    public static final float SWIM_BOX = 0.6f, ENGAGE = 0.45f, LET_GO = 0.2f;
    /** The jump key lifts only a body this deep in (so a swimmer bobs at the surface rather than leaving the water). */
    public static final float JUMP_DEPTH = 0.4f;
    /**
     * Where a floating body rides: this much of its height under. (0.3.7, gamefixes: idle in the water the player should
     * keep their head above it) the water at the collarbone, the chin and the eyes well clear - at 0.84 it lapped at the
     * eyes, a hand's breadth from going under, and the view sat in the surface.
     */
    public static final float FLOAT_LINE = 0.70f;
    /** The Agility at which a swimmer can haul out onto a ledge higher than a step (holding the sprint key). */
    public static final int HAUL_LEVEL = 10;
    /** Animation modes (Actor.swimMode): none, wading, treading water (afloat, upright), swimming (flat out), hauling out. */
    public static final int NONE = 0, WADE = 1, TREAD = 2, SWIM = 3, HAUL = 4;

    /** The physics state, kept apart from the actor's own fields. */
    public static final class State {
        public double x, y, z, vx, vy, vz;
        double px, py, pz;
        public float yaw, pitch, forward, strafe;
        public boolean sprint, jump, sneak;
        public boolean active, swimming, inWater, eyeUnder, hCollide, vCollide, onGround;
        public float width = 0.6f, height = 1.8f, submerged;
        public int depth;
        float acc;
        double haulUntil;
        float stroked;
    }

    private final Rpg g;
    public final State player = new State();
    public Swimming(Rpg g) { this.g = g; }

    public boolean active(Actor a) { return a == g.player && player.active; }
    /** Dropped where it is (a teleport): the next frame finds the water afresh, if there is any. */
    public void cancel(Actor a) { if (a == g.player) { player.active = false; player.swimming = false; player.acc = 0; } a.swimMode = NONE; }

    /**
     * The player's frame: takes the body if it stands in water, runs as many fixed ticks as the time allows, and puts
     * the drawn body where the last two ticks say (interpolated). Returns whether it moved the body this frame.
     */
    public boolean update(Player p, float forward, float strafe, boolean sprint, boolean jump, boolean sneak, float yaw, float pitch, float dt) {
        State s = player;
        if (!p.alive || p.reacting || p.riding != 0 || g.traversal.busy(p) || p.climb != null || g.ui != null && g.ui.console.noclip) {
            if (s.active) release(p, s); settle(p, dt);
            // (0.3.6) knocked down or climbing, the head can still be under: the breath runs out all the same
            if (p.alive && p.riding == 0 && !(g.ui != null && g.ui.console.noclip)) breathe(p, headUnder(p, p.x, p.y, p.z), dt);
            return false;
        }
        if (!s.active) {
            float h = p.height, r = 0.3f;
            float sub = g.fluids.submerged(p.x - r, p.y, p.z - r, p.x + r, p.y + h, p.z + r);
            // (0.3.6, gamefixes: climbing out of a well or the fountain glitched the player) water too shallow to float
            // in is waded, on the walking body - which steps and jumps out over a rim as it would anywhere - not swum:
            // the swimmer's lift only cleared 0.6 m, so a basin's rim could only be hauled over, and the haul handed the
            // body back pressed into the stone
            boolean standing = p.grounded && g.fluids.depth(p.x, p.y + h, p.z) < FLOAT_DEPTH;
            if (sub < ENGAGE || standing) {
                p.swimMode = sub > 0.1f ? WADE : NONE; settle(p, dt); breathe(p, headUnder(p, p.x, p.y, p.z), dt); return false;
            }
            engage(p, s);
        }
        s.forward = forward; s.strafe = strafe; s.sprint = sprint; s.jump = jump; s.sneak = sneak; s.yaw = yaw; s.pitch = pitch;
        s.acc += Math.min(dt, 0.25f);
        while (s.acc >= TICK && s.active) { s.acc -= TICK; tick(p, s); }
        if (!s.active) { release(p, s); settle(p, dt); return false; }
        float k = s.acc / TICK;
        p.x = (float) (s.px + (s.x - s.px) * k); p.y = (float) (s.py + (s.y - s.py) * k); p.z = (float) (s.pz + (s.z - s.pz) * k);
        p.vx = (float) (s.vx / TICK); p.vz = (float) (s.vz / TICK); p.climbVelocity = (float) (s.vy / TICK);
        p.grounded = s.onGround; p.crouched = false; p.sneaking = false; p.sprinting = s.swimming;
        if (p.body != 0) { g.game.commands().moverPlace(p.body, p.x, p.y, p.z, 0, 0, 0); g.game.commands().moverInput(p.body, 0, 0, 0, 0, 80, MOVER_POSED); }
        animate(p, s, dt);
        breathe(p, s.eyeUnder, dt);
        return true;
    }

    private void engage(Player p, State s) {
        s.active = true; s.swimming = false;
        s.x = s.px = p.x; s.y = s.py = p.y; s.z = s.pz = p.z;
        s.vx = p.vx * TICK; s.vz = p.vz * TICK; s.vy = Math.max(-1.2, p.climbVelocity * TICK);
        s.width = 0.6f; s.height = p.height; s.acc = 0;
        if (g.falling != null) g.falling.reset(p);   // water breaks a fall
        g.state.commit("EnteredWater", p.id, 0, rpg.world.DevMap.jurisdiction(p.x, p.z), 0, String.format("%.1f m/s down", -p.climbVelocity));
    }

    /** Back to the walking mover, at the swimmer's place and with its velocity (a hop out of the water carries on). */
    private void release(Player p, State s) {
        s.active = false; s.swimming = false;
        p.swimMode = NONE;
        p.x = (float) s.x; p.y = (float) s.y; p.z = (float) s.z;
        float vx = (float) (s.vx / TICK), vy = (float) (s.vy / TICK), vz = (float) (s.vz / TICK);
        if (p.body != 0) { g.game.commands().moverPlace(p.body, p.x, p.y + 0.02f, p.z, vx, vy, vz); g.game.commands().moverInput(p.body, vx, vz, 0, 0, 80, 0); }
        p.vx = vx; p.vz = vz; p.climbVelocity = vy; p.grounded = s.onGround;
        p.poseReset = s.height < p.height * 0.9f;
    }

    // ------------------------------------------------------------------ the tick
    void tick(Player p, State s) {
        s.px = s.x; s.py = s.y; s.pz = s.z;
        int agility = p.skills.level(Skill.AGILITY);
        float skill = Math.min(1, agility / 99f);
        // 1. input: normalised, turned by the camera's yaw
        float fwd = s.forward, side = s.strafe, l = (float) Math.hypot(fwd, side);
        if (l > 1) { fwd /= l; side /= l; }
        float fx = (float) -Math.sin(s.yaw), fz = (float) -Math.cos(s.yaw), rx = (float) Math.cos(s.yaw), rz = (float) -Math.sin(s.yaw);
        float ix = fx * fwd + rx * side, iz = fz * fwd + rz * side;
        // 2. the water: the box's depth in it (partial heights), the eyes apart
        sample(p, s);
        // 3. the swimming state and the box: swimming starts on a sprint well under (the chest down) and lasts while the
        // sprint and the water do; the box stands up again only where a standing body fits
        // (0.3.7) judged against where a body floats now (the float line came down from 0.84 to 0.70): a body afloat at the
        // surface is deep enough to strike out and dive, as it was before
        boolean deepEnough = s.submerged >= (FLOAT_LINE - 0.12f) * s.height || s.eyeUnder;
        if (s.swimming) s.swimming = s.sprint && s.inWater && fwd > 0.1f;
        else s.swimming = s.sprint && s.inWater && fwd > 0.1f && deepEnough;
        // the flat swimming box only where it fits (pressed to a wall it stays as it is), and a body found inside rock is eased
        // up out of it, never down
        if (s.swimming && (s.width == SWIM_BOX && s.height == SWIM_BOX || free(s.x, s.y, s.z, SWIM_BOX, SWIM_BOX, false))) setBox(s, SWIM_BOX, SWIM_BOX);
        if (!free(s.x, s.y, s.z, s.width, s.height, false)) for (double up = 0.05; up <= 1.2; up += 0.05) if (free(s.x, s.y + up, s.z, s.width, s.height, false)) { s.y += up; s.py = s.y; break; }
        else if (s.height < p.height && free(s.x, s.y, s.z, 0.6f, p.height, false)) { setBox(s, 0.6f, p.height); sample(p, s); }
        boolean cramped = !s.swimming && s.height < p.height;
        // 4. the strokes' push (stronger with Agility)
        float push = ACCEL * (1 + 0.6f * skill) * (s.inWater ? 1 : 0.2f);
        if (cramped) push *= 0.6f;
        s.vx += ix * push; s.vz += iz * push;
        // 5. the vertical: pitch steering while swimming, never up through the surface; the jump key, the crouch; floating
        if (s.swimming) {
            double look = Math.sin(s.pitch);
            double e = look < -0.2 ? 0.085 : 0.06;
            boolean waterOver = g.fluids.in((float) s.x, (float) (s.y + s.height + 0.15f), (float) s.z);
            if (look <= 0 || s.jump || waterOver) s.vy += (look - s.vy) * e;
        }
        if (s.jump && s.inWater && s.submerged > JUMP_DEPTH) s.vy += RISE;
        if (s.sneak && s.inWater) s.vy -= DIVE;
        if (s.inWater && s.depth >= 2 && !s.sneak) {
            // afloat: buoyancy grows with how much of the body is under, holding it with the head out; a swimmer flat out
            // is held level (neutral: the pitch alone takes it up or down), a body let go under rises back up
            float frac = s.submerged / Math.max(0.3f, s.height);
            float line = s.swimming ? 1 : FLOAT_LINE - 0.06f * skill;
            s.vy += GRAVITY * Math.min(s.swimming ? 1f : 1.3f, frac / line);
        }
        // the current's own push
        float[] flow = g.fluids.flow((float) s.x, (float) (s.y + Math.min(0.5f, s.submerged * 0.5f)), (float) s.z);
        s.vx += flow[0] * FLOW_K; s.vz += flow[1] * FLOW_K;
        // 6. the movement, swept against the solid world axis by axis
        double startY = s.y;
        move(s);
        // 7. drag, then gravity
        double keep = s.inWater ? (s.sprint && (s.swimming || fwd > 0.1f) ? DRAG_SPRINT : DRAG) : 0.91;
        s.vx *= keep; s.vz *= keep;
        s.vy *= s.inWater ? DRAG_UP : 0.98;
        s.vy -= s.inWater ? GRAVITY : 0.08;
        // 8. a bank to climb out on: pressed against it, open air above it, the water's edge right there
        if (s.hCollide && s.inWater) exit(p, s, ix, iz, agility, startY);
        // 9. out of the water: hand the body back - or to the walking body standing in water too shallow to float in
        sample(p, s);
        boolean wading = s.onGround && s.depth < FLOAT_DEPTH && !s.swimming && s.vy <= 0.01;
        if (!s.inWater || wading || s.submerged < LET_GO && (s.onGround || s.vy > 0)) { if (s.height >= p.height * 0.9f || free(s.x, s.y, s.z, 0.6f, p.height, false)) s.active = false; }
        practise(p, s);
    }

    private void sample(Player p, State s) {
        double r = s.width / 2;
        s.submerged = g.fluids.submerged(s.x - r, s.y, s.z - r, s.x + r, s.y + s.height, s.z + r);
        s.inWater = s.submerged > 0;
        s.eyeUnder = headUnder(p, (float) s.x, (float) s.y, (float) s.z);
        s.depth = g.fluids.depth((float) s.x, (float) (s.y + s.height), (float) s.z);
    }

    private static void setBox(State s, float w, float h) { s.width = w; s.height = h; }

    /**
     * Swept box against the voxels: y first, then x and z, each clipped to the nearest solid face on its way; a blocked
     * axis stops (its velocity is zeroed), the others keep theirs.
     */
    private void move(State s) {
        double r = s.width / 2;
        double dy = clip(s, 1, s.vy, r);
        s.vCollide = dy != s.vy;
        s.onGround = s.vCollide && s.vy < 0;
        s.y += dy; if (s.vCollide) s.vy = 0;
        double dx = clip(s, 0, s.vx, r);
        boolean bx = dx != s.vx;
        s.x += dx; if (bx) s.vx = 0;
        double dz = clip(s, 2, s.vz, r);
        boolean bz = dz != s.vz;
        s.z += dz; if (bz) s.vz = 0;
        s.hCollide = bx || bz;
    }

    /** How far the box may move along one axis (0 x, 1 y, 2 z) before meeting a solid cell. */
    private double clip(State s, int axis, double d, double r) {
        if (d == 0) return 0;
        double minX = s.x - r, maxX = s.x + r, minY = s.y, maxY = s.y + s.height, minZ = s.z - r, maxZ = s.z + r;
        final double eps = 1e-4;
        if (axis == 0) { if (d > 0) maxX += d; else minX += d; }
        else if (axis == 1) { if (d > 0) maxY += d; else minY += d; }
        else { if (d > 0) maxZ += d; else minZ += d; }
        int x0 = (int) Math.floor(minX + eps), x1 = (int) Math.floor(maxX - eps), y0 = (int) Math.floor(minY + eps), y1 = (int) Math.floor(maxY - eps), z0 = (int) Math.floor(minZ + eps), z1 = (int) Math.floor(maxZ - eps);
        // (0.4.4, gamefixes: stopping a sprint swim against a wall pushed the player under the map) only the cells ahead of
        // the moving face stop it: a cell the box already overlaps (a wall it was widened into beside it) used to count as a
        // face behind it, and turned a small rise into a drop of the body's whole height - through the bed of the lake
        double sign = Math.signum(d);
        for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) for (int z = z0; z <= z1; z++) {
            if (!g.vox.solid(x, y, z)) continue;
            if (axis == 0) { if (d > 0 ? x + eps < s.x + r : x + 1 - eps > s.x - r) continue; d = d > 0 ? Math.min(d, x - (s.x + r)) : Math.max(d, x + 1 - (s.x - r)); }
            else if (axis == 1) { if (d > 0 ? y + eps < s.y + s.height : y + 1 - eps > s.y) continue; d = d > 0 ? Math.min(d, y - (s.y + s.height)) : Math.max(d, y + 1 - s.y); }
            else { if (d > 0 ? z + eps < s.z + r : z + 1 - eps > s.z - r) continue; d = d > 0 ? Math.min(d, z - (s.z + r)) : Math.max(d, z + 1 - (s.z - r)); }
        }
        // never carried backwards: a move is shortened, never reversed
        if (Math.signum(d) != sign) d = 0;
        return Math.abs(d) < eps ? 0 : d;
    }

    /** Whether a box of this size fits at a place (no solid cell; with water too, when asked, none of that either). */
    private boolean free(double x, double y, double z, float w, float h, boolean dry) {
        double r = w / 2;
        int x0 = (int) Math.floor(x - r + 1e-3), x1 = (int) Math.floor(x + r - 1e-3), y0 = (int) Math.floor(y + 1e-3), y1 = (int) Math.floor(y + h - 1e-3), z0 = (int) Math.floor(z - r + 1e-3), z1 = (int) Math.floor(z + r - 1e-3);
        for (int bx = x0; bx <= x1; bx++) for (int by = y0; by <= y1; by++) for (int bz = z0; bz <= z1; bz++) if (g.vox.solid(bx, by, bz)) return false;
        return !dry || !g.fluids.contact(x - r, y, z - r, x + r, y + h, z + r);
    }

    /**
     * Out over a bank: a body pressed against it whose box, raised 0.6 and carried on its way, would be clear of rock
     * and of water gets the lift up (the edge is the water's edge, so an underwater wall never gives it). Failing that, a
     * strong swimmer holding the sprint and pushing at it hauls out onto a higher ledge - as high as Agility allows -
     * with the spring a hop to that height takes.
     */
    private void exit(Player p, State s, float ix, float iz, int agility, double startY) {
        if (free(s.x + s.vx, s.y + s.vy + 0.6 - (s.y - startY), s.z + s.vz, s.width, s.height, true)) {
            s.vy = EXIT_LIFT;
            return;
        }
        float il = (float) Math.hypot(ix, iz);
        if (!s.sprint || il < 0.3f || agility < HAUL_LEVEL || g.time < s.haulUntil) return;
        float reach = 1.05f + 0.6f * Math.min(1, agility / 99f);
        float ax = ix / il * 0.45f, az = iz / il * 0.45f;
        for (float up = 0.65f; up <= reach + 0.01f; up += 0.1f) {
            double ny = s.y + up;
            if (!free(s.x, ny, s.z, s.width, s.height, false)) return;          // the head would meet something first
            if (free(s.x + ax, ny, s.z + az, s.width, s.height, true) && !free(s.x + ax, ny - 0.15, s.z + az, s.width, s.height, false)) {
                // found the lip: spring to it (22 m/s2 is the walking world's gravity), carried on over it
                double v = Math.sqrt(2 * 22 * (up + 0.2)) * TICK;
                s.vy = v; s.vx = ax * 0.25; s.vz = az * 0.25;
                s.haulUntil = g.time + 0.8;
                p.swimMode = HAUL; p.swimStroke = 0;
                var e = g.state.commit("HauledOut", p.id, 0, rpg.world.DevMap.jurisdiction(p.x, p.z), 0, String.format("a %.1f m ledge", up));
                g.prog.award(p, Skill.AGILITY, 3, "hauled out of the water", e.id());
                return;
            }
        }
    }

    /** Agility for distance actually swum (not for treading water against a wall). */
    private void practise(Player p, State s) {
        if (!s.inWater || s.depth < 2) return;
        s.stroked += (float) Math.hypot(s.x - s.px, s.z - s.pz) + (float) Math.abs(s.y - s.py) * 0.5f;
        if (s.stroked >= 25) { s.stroked -= 25; g.prog.award(p, Skill.AGILITY, s.swimming ? 3 : 2, "swimming", 0); }
    }

    // ------------------------------------------------------------------ breath
    /** Seconds of breath a body holds (longer with Agility). */
    public static float lungs(Actor a) { return lungs(a.skills.level(Skill.AGILITY)); }
    /** How long a breath held lasts at an Agility level (seconds). */
    public static float lungs(int agility) { return 15 + 0.3f * agility; }

    /**
     * (0.3.6, gamefixes: a head under water was not taken for under water) whether a body's head is in the water - the
     * head itself, where it is: the ragdoll's head for a body off its feet; else the head of the figure as it was last
     * drawn (it moves with the stroke, the tread and the dive), carried along with the body (x, y, z) since; else the
     * eyes' height. It used to be one point at a fixed height in the swimmer's box, which a swimmer's head is often not.
     */
    public boolean headUnder(Actor a, float x, float y, float z) {
        float[] h = headPoint(a, x, y, z);
        return g.fluids.in(h[0], h[1], h[2]);
    }
    private final float[] head = new float[3];
    public float[] headPoint(Actor a, float x, float y, float z) {
        if (a.reacting && a.ragdoll != 0 && g.lastBodies != null && g.lastBodies.ragdoll(a.ragdoll) instanceof astratto.bridge.Snapshots.Ragdoll rd) {
            int n = RD_NECK * 3, t = RD_HEAD * 3;
            head[0] = (rd.p[n] + rd.p[t]) / 2; head[1] = (rd.p[n + 1] + rd.p[t + 1]) / 2; head[2] = (rd.p[n + 2] + rd.p[t + 2]) / 2;
            return head;
        }
        if (a.posePts != null && g.time - a.poseAt < 0.3 && Float.isFinite(a.poseX)) {
            // the middle of the head: halfway from the neck to the crown
            int n = RD_NECK * 3, t = RD_HEAD * 3;
            head[0] = (a.posePts[n] + a.posePts[t]) / 2 + (x - a.poseX);
            head[1] = (a.posePts[n + 1] + a.posePts[t + 1]) / 2 + (y - a.poseY);
            head[2] = (a.posePts[n + 2] + a.posePts[t + 2]) / 2 + (z - a.poseZ);
            return head;
        }
        head[0] = x; head[1] = y + (a.eyeY() - a.y); head[2] = z;
        return head;
    }

    /**
     * (0.3.6, gamefixes: fish had no collision) a body in the water pushed aside by another (a fish shouldering past): the
     * player's swimming body as far as there is room, anyone else's body likewise.
     */
    public void shove(Actor a, float dx, float dz) {
        if (a == g.player && player.active) {
            if (free(player.x + dx, player.y, player.z + dz, player.width, player.height, false)) { player.x += dx; player.z += dz; player.px += dx; player.pz += dz; }
            return;
        }
        if (a.body == 0 || a.riding != 0 || a.reacting) return;
        if (!free(a.x + dx, a.y + 0.05f, a.z + dz, Math.max(0.4f, a.radius * 2), Math.min(1.6f, a.height), false)) return;
        a.x += dx; a.z += dz;
        g.game.commands().moverPlace(a.body, a.x, a.y, a.z, a.vx, 0, a.vz);
    }

    /**
     * The breath: spent while the head is under, got back quickly in the air. With none left, the water takes a point
     * of health a second until the body surfaces - or dies.
     */
    public void breathe(Actor a, boolean under, float dt) {
        float max = lungs(a);
        if (a.oxygen < 0 || a.oxygen > max) a.oxygen = max;
        if (!under) { a.oxygen = Math.min(max, a.oxygen + dt * max / 3f); a.drownAt = 0; return; }
        a.oxygen = Math.max(0, a.oxygen - dt);
        if (a.oxygen > 0) return;
        if (a.drownAt == 0) a.drownAt = g.time + 0.4;
        if (g.time >= a.drownAt) {
            a.drownAt = g.time + 1;
            g.combat.environmentalDamage(a, 1, "drowning");
            if (a == g.player && a.alive) g.chat.add("You are drowning!", rpg.Chat.Kind.WARN);
        }
    }

    // ------------------------------------------------------------------ what the rig reads (never the other way)
    private void animate(Player p, State s, float dt) {
        int mode = p.swimMode == HAUL && g.time < s.haulUntil ? HAUL : s.swimming ? SWIM : s.depth >= 2 && s.inWater && !s.onGround ? TREAD : s.inWater ? WADE : NONE;
        p.swimMode = mode;
        float speed = (float) Math.hypot(s.vx, s.vz) / TICK;
        float rate = mode == SWIM ? 1.1f + speed * 0.35f : mode == TREAD ? 0.9f + speed * 0.4f : 0;
        p.swimStroke += rate * dt;
        float wantTilt = mode == SWIM ? (float) Math.PI / 2 - (float) Math.max(-1.1, Math.min(1.1, s.pitch)) : mode == TREAD ? 0.18f + 0.3f * Math.min(1, speed / 2f) : 0;
        p.swimTilt += (wantTilt - p.swimTilt) * (1 - (float) Math.exp(-dt / 0.18f));
        p.swimPose += ((mode == SWIM ? 1 : 0) - p.swimPose) * (1 - (float) Math.exp(-dt / 0.2f));
    }

    /** Out of the water, the drawn body eases back upright. */
    private static void settle(Actor a, float dt) {
        if (a.swimTilt == 0 && a.swimPose == 0) return;
        float k = 1 - (float) Math.exp(-dt / 0.15f);
        a.swimTilt -= a.swimTilt * k; a.swimPose -= a.swimPose * k;
        if (a.swimTilt < 0.005f) a.swimTilt = 0;
        if (a.swimPose < 0.005f) a.swimPose = 0;
    }

    // ------------------------------------------------------------------ people and animals in water (not the player)
    /**
     * Everyone else in water, after the physics has moved them: a swimmer in deep water (or a duck on any water) is held
     * up at its floating line - the walking world would sink it to the bed - and carried along by the current; anyone
     * whose eyes are under spends breath as the player does.
     */
    public void others(float dt) {
        for (Actor a : g.actors()) {
            if (a == g.player || !a.alive || a.dummy || a.body == 0 || a.riding != 0) continue;
            // (0.3.6) a body knocked off its feet is not afloat, but its head may well be in the water
            if (a.reacting) { if (a.ragdoll != 0 && Math.abs(a.x - g.player.x) < 80 && Math.abs(a.z - g.player.z) < 80) breathe(a, headUnder(a, a.x, a.y, a.z), dt); continue; }
            if (a instanceof rpg.ai.Npc n && !n.loaded || a instanceof rpg.animal.Animal an && (!an.loaded || an.ragdoll != null || an.rider != 0)) continue;
            if (Math.abs(a.x - g.player.x) > 80 || Math.abs(a.z - g.player.z) > 80) { a.swimMode = NONE; continue; }
            // most bodies are nowhere near water: one cell look at the feet before any search
            int fx = (int) Math.floor(a.x), fy = (int) Math.floor(a.y + 0.05f), fz = (int) Math.floor(a.z);
            if (!g.fluids.water(fx, fy, fz) && !g.fluids.water(fx, fy - 1, fz) && !g.fluids.water(fx, fy + 1, fz)) { if (a.swimMode != NONE) a.swimMode = NONE; if (a.oxygen >= 0 && a.oxygen < lungs(a)) breathe(a, false, dt); continue; }
            float surface = g.fluids.surface(a.x, a.y + a.height, a.z);
            if (Float.isNaN(surface) || surface <= a.y + 0.05f) { if (a.swimMode != NONE) a.swimMode = NONE; continue; }
            boolean floats = a instanceof rpg.animal.Animal an ? an.species.swims && (an.species == rpg.animal.Species.DUCK || g.fluids.depth(a.x, surface, a.z) >= 2)
                    : g.fluids.depth(a.x, surface, a.z) >= 2;
            float line = a instanceof rpg.animal.Animal an && an.species == rpg.animal.Species.DUCK ? 0.32f : a instanceof rpg.animal.Animal ? 0.62f : FLOAT_LINE;
            float want = surface - a.height * line;
            if (floats && a.y < want + 0.02f) {
                float bob = 0.025f * (float) Math.sin(g.time * 2.2 + a.id);
                float ny = a.y + (want + bob - a.y) * Math.min(1, dt * 6);
                float[] flow = g.fluids.flow(a.x, ny + 0.3f, a.z);
                a.y = ny;
                g.game.commands().moverPlace(a.body, a.x + flow[0] * dt, ny, a.z + flow[1] * dt, a.vx + flow[0], 0, a.vz + flow[1]);
                a.swimMode = a instanceof rpg.animal.Animal ? SWIM : TREAD;
                a.swimStroke += (0.9f + (float) Math.hypot(a.vx, a.vz) * 0.5f) * dt;
            } else a.swimMode = surface - a.y > 0.35f ? WADE : NONE;
            breathe(a, headUnder(a, a.x, a.y, a.z), dt);
        }
    }
}
