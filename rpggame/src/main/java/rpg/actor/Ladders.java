package rpg.actor;

import rpg.Rpg;
import rpg.skill.Skill;
import rpg.world.Blocks;
import static astratto.bridge.AstrattoNative.*;

/**
 * (0.3.0, gamefixes: ladders were broken - one climbed them whichever way one faced) a ladder is climbed facing it. A
 * ladder hangs on the face of the block behind it (the same rule the mesher draws it by), so it has a front: the body
 * comes onto it only walking at it from that front - or, from the top, walking off the edge it hangs from - and is drawn
 * onto the rungs (snapped, eased in over a moment: a magnet, not a teleport). On it, forward climbs and back goes down,
 * hand over hand and rung by rung (the figure's climb is driven by how far up it has gone, so the hands and feet meet the
 * rungs); at the top it climbs out over the edge onto whatever it leads to, at the bottom it steps off onto the ground,
 * and a jump pushes off backward. A blow or a stagger knocks one off.
 */
public final class Ladders {
    public enum Phase { MOUNT, CLIMB, TOP_OUT, TOP_IN, STEP_OFF }
    /** How far apart the rungs are (m, the mesher's boxes at 2, 7 and 12 of 16 a block), and climbing speed (m/s). */
    public static final float RUNG = 5f / 16f, SPEED = 1.9f;

    public static final class Climb {
        /** The ladder's cell column, its front (unit, pointing away from the wall), and its lowest and highest blocks. */
        public int cx, cz, bottom, top;
        public float nx, nz;
        public Phase phase = Phase.MOUNT;
        /** Through the present phase (seconds), and its length. */
        public float t, duration;
        /** Where the phase starts and ends (feet, world). */
        public float fx, fy, fz, tx, ty, tz;
        /** The way up or down being climbed now (-1, 0, +1) and the speed carried (m/s). */
        public float dir, speed;
        /** Where the top-out lands (feet, world). */
        public float ox, oy, oz;
        /** The way the body faced as it took hold (it turns to the ladder from there). */
        public float startYaw;
    }

    private final Rpg g;
    public Ladders(Rpg g) { this.g = g; }

    public static boolean ladder(Rpg g, int x, int y, int z) { return Blocks.ladder(g.vox.block(x, y, z)); }

    /** A ladder block's front (away from the wall it hangs on), as the mesher orients it: {nx, nz}. */
    public static float[] front(Rpg g, int x, int y, int z) {
        if (g.vox.solid(x, y, z - 1)) return new float[]{0, 1};
        if (g.vox.solid(x + 1, y, z)) return new float[]{-1, 0};
        if (g.vox.solid(x, y, z + 1)) return new float[]{0, -1};
        if (g.vox.solid(x - 1, y, z)) return new float[]{1, 0};
        return switch ((x * 31 + z * 17) & 3) { case 0 -> new float[]{0, 1}; case 1 -> new float[]{-1, 0}; case 2 -> new float[]{0, -1}; default -> new float[]{1, 0}; };
    }

    /** The front of a ladder the move goes into: its own front, or, in a corner, another wall it is set against; null if none. */
    static float[] facing(Rpg g, int x, int y, int z, float mx, float mz) {
        float[] own = front(g, x, y, z);
        if (mx * -own[0] + mz * -own[1] >= 0.6f) return own;
        int[][] sides = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
        for (int[] d : sides) if (g.vox.solid(x + d[0], y, z + d[1]) && mx * d[0] + mz * d[1] >= 0.6f) return new float[]{-d[0], -d[1]};
        return null;
    }

    /** Where a body clings on a ladder's column: centred on it, its back to the open, its front a hand's breadth off the rungs. */
    public static float[] cling(Actor a, int cx, int cz, float nx, float nz) {
        // from the cell's middle toward the front: the wall is half a block behind the middle, the rungs' face 0.12 m off it
        float d = -0.5f + 0.12f + a.radius * 0.95f;
        return new float[]{cx + 0.5f + nx * d, cz + 0.5f + nz * d};
    }

    private int[] span(int x, int y, int z) {
        int b = y, t = y;
        while (ladder(g, x, b - 1, z)) b--;
        while (ladder(g, x, t + 1, z)) t++;
        return new int[]{b, t};
    }

    public boolean on(Actor a) { return a.climb != null; }

    /** Whether the body may take hold of a ladder now. */
    private boolean free(Actor a) {
        return a.alive && a.traversal == null && a.carryBody == 0 && !a.reacting && a.cs.staggered <= 0 && a.morphT <= 0 && !g.law.inCustody(a) && g.time >= a.ladderAgain;
    }

    /**
     * Takes hold of a ladder if the body walks at one from its front (or off the top edge it hangs from): the move's
     * direction, world, unit. True when the body is now on it.
     */
    public boolean mount(Actor a, float mx, float mz) {
        if (a.climb != null || !free(a)) return false;
        int fy = (int) Math.floor(a.y + 0.05f);
        // from below or level: a ladder in the cell ahead (or this one), at the feet or the knees
        for (float reach : new float[]{0f, 0.45f, 0.8f}) {
            int x = (int) Math.floor(a.x + mx * reach), z = (int) Math.floor(a.z + mz * reach);
            for (int y = fy; y <= fy + 1; y++) {
                if (!ladder(g, x, y, z)) continue;
                // facing it: walking into its front (a ladder in a corner, against two walls, is taken from either)
                float[] n = facing(g, x, y, z, mx, mz);
                if (n == null) continue;
                float side = (a.x - (x + 0.5f)) * n[0] + (a.z - (z + 0.5f)) * n[1];
                if (side < -0.3f) continue;
                int[] s = span(x, y, z);
                begin(a, x, z, n, s, Phase.MOUNT, Math.max(a.y, s[0]));
                return true;
            }
        }
        // from the top: standing on the ledge a ladder hangs from, walking off it over the ladder's top
        if (a.grounded) {
            int x = (int) Math.floor(a.x + mx * 0.7f), z = (int) Math.floor(a.z + mz * 0.7f);
            int y = fy - 1;
            if (ladder(g, x, y, z) && !ladder(g, x, fy, z) && !g.vox.solid(x, fy, z) && !g.vox.solid(x, fy + 1, z)) {
                float[] n = front(g, x, y, z);
                // the ledge is the block it hangs from: walking from over that toward the ladder's front
                if (mx * n[0] + mz * n[1] > 0.6f) {
                    int[] s = span(x, y, z);
                    begin(a, x, z, n, s, Phase.TOP_IN, s[1] + 1 - a.height * 0.72f);
                    return true;
                }
            }
        }
        return false;
    }

    private void begin(Actor a, int x, int z, float[] n, int[] s, Phase phase, float y) {
        Climb c = new Climb();
        c.cx = x; c.cz = z; c.nx = n[0]; c.nz = n[1]; c.bottom = s[0]; c.top = s[1];
        float[] at = cling(a, x, z, c.nx, c.nz);
        c.fx = a.x; c.fy = a.y; c.fz = a.z; c.tx = at[0]; c.ty = y; c.tz = at[1];
        c.phase = phase; c.t = 0; c.startYaw = a.yaw;
        c.duration = phase == Phase.TOP_IN ? 0.85f : 0.28f;
        a.climb = c; a.onLadder = true; a.climbPhase = (y - s[0]) / RUNG;
        a.ladderNX = c.nx; a.ladderNZ = c.nz;
        a.cs.cancel(); a.cs.blocking = false; a.cs.draw = 0;
        g.interact.cancel(a, "climbing");
        g.state.commit("LadderMounted", a.id, 0, null, 0, phase == Phase.TOP_IN ? "from the top" : "from below");
    }

    /** Lets go: dropped off the ladder (a blow, a jump: with a push away from it), the mover handed back. */
    public void off(Actor a, float push, String why) {
        Climb c = a.climb; if (c == null) return;
        a.climb = null; a.onLadder = false; a.ladderAgain = g.time + 0.45;
        a.grounded = false;
        float vx = c.nx * push, vz = c.nz * push, vy = push > 0 ? g.cfg.jumpSpeed * 0.45f : 0;
        if (a.body != 0) { g.game.commands().moverPlace(a.body, a.x, a.y, a.z, vx, vy, vz); g.game.commands().moverInput(a.body, vx, vz, 0, 0, 80, 0); }
        a.vx = vx; a.vz = vz;
        g.state.commit("LadderLeft", a.id, 0, null, 0, why);
    }

    /** Cancelled outright (a teleport, a load): nothing more than forgetting it. */
    public void cancel(Actor a) { if (a.climb == null) return; a.climb = null; a.onLadder = false; if (a.body != 0) g.game.commands().moverInput(a.body, 0, 0, 0, 0, 80, 0); }

    /** One frame on the ladder, by the keys: forward up, back down, jump off. True while it owns the body. */
    public boolean update(Actor a, float forward, boolean jump, float dt) {
        Climb c = a.climb; if (c == null) return false;
        if (!a.alive || a.reacting || a.cs.staggered > 0 || g.time - a.cs.lastHurt < 0.05) { off(a, 1.2f, "knocked off"); return false; }
        c.t += dt;
        float k = Math.min(1, c.t / Math.max(0.01f, c.duration)), e = k * k * (3 - 2 * k);
        float x = a.x, y = a.y, z = a.z;
        switch (c.phase) {
            case MOUNT -> {
                x = mix(c.fx, c.tx, e); y = mix(c.fy, c.ty, e); z = mix(c.fz, c.tz, e);
                if (k >= 1) { c.phase = Phase.CLIMB; c.t = 0; }
            }
            case TOP_IN -> {
                // turned about at the edge, a foot found on the top rung, then lowered onto it: across first, then down
                float h = smooth(Math.min(1, k / 0.45f)), v = smooth(Math.max(0, (k - 0.45f) / 0.55f));
                x = mix(c.fx, c.tx, h); z = mix(c.fz, c.tz, h); y = mix(c.fy, c.ty, v);
                if (k >= 1) { c.phase = Phase.CLIMB; c.t = 0; }
            }
            case CLIMB -> {
                if (jump) { off(a, 2.6f, "jumped off"); return true; }
                float want = Math.abs(forward) > 0.2f ? Math.signum(forward) : 0;
                float top = SPEED * (1 + Math.min(40, a.skills.level(Skill.AGILITY)) * 0.006f) * a.actionRate();
                c.speed += Math.max(-dt * 9, Math.min(dt * 9, want * top - c.speed));
                c.dir = Math.abs(c.speed) > 0.05f ? Math.signum(c.speed) : 0;
                x = c.tx; z = c.tz; y = a.y + c.speed * dt;
                // the bottom: the feet reach the ground beneath the ladder - going on down steps off backward onto it
                float floor = c.bottom;
                if (g.vox.solid(c.cx, c.bottom - 1, c.cz) && y <= floor) {
                    y = floor;
                    if (want < 0) { c.phase = Phase.STEP_OFF; c.t = 0; c.duration = 0.3f; c.fx = x; c.fy = y; c.fz = z; c.tx = x + c.nx * 0.35f; c.ty = y; c.tz = z + c.nz * 0.35f; }
                }
                if (y < floor - 0.02f && !g.vox.solid(c.cx, c.bottom - 1, c.cz)) { a.y = y; off(a, 0, "ran out of ladder"); return false; }
                // the top: the hands on the last rung, the body climbs out over the edge onto what it leads to
                float lastHold = c.top + 1 - a.height * 0.72f;
                if (y >= lastHold) {
                    y = lastHold;
                    float[] out = want > 0 ? exit(a, c) : null;
                    if (out != null) { c.phase = Phase.TOP_OUT; c.t = 0; c.duration = 0.8f; c.fx = x; c.fy = y; c.fz = z; c.ox = out[0]; c.oy = out[1]; c.oz = out[2]; }
                    else if (c.speed > 0) c.speed = 0;
                }
                a.climbPhase += (y - a.y) / RUNG;
                // the climb is work: a little stamina for every rung gone up
                if (y > a.y) a.stamina = Math.max(0, a.stamina - (y - a.y) * 1.2f);
            }
            case TOP_OUT -> {
                // up the last rungs until the hips are over the edge, then a knee on the top and the body over it
                float up = smooth(Math.min(1, k / 0.55f)), over = smooth(Math.max(0, (k - 0.35f) / 0.65f));
                x = mix(c.fx, c.ox, over); z = mix(c.fz, c.oz, over);
                y = mix(c.fy, c.oy + 0.1f, up) + (c.oy - (c.oy + 0.1f)) * over;
                a.climbPhase += dt * 3.2f * (1 - over);
                if (k >= 1) { finish(a, c.ox, c.oy, c.oz, "climbed out at the top"); return true; }
            }
            case STEP_OFF -> {
                x = mix(c.fx, c.tx, e); y = c.fy; z = mix(c.fz, c.tz, e);
                if (k >= 1) { finish(a, x, y, z, "stepped off at the bottom"); return true; }
            }
        }
        if (c.phase == Phase.CLIMB && !room(a, x, y, z)) { off(a, 0.8f, "no room"); return false; }
        a.x = x; a.y = y; a.z = z; a.vx = a.vz = 0; a.grounded = false; a.onLadder = true;
        a.climbVelocity = c.speed;
        // facing the wall: turned to it while taking hold (about, from the top), then kept to it
        float wallYaw = (float) Math.atan2(c.nx, c.nz);
        if (c.phase == Phase.MOUNT || c.phase == Phase.TOP_IN) {
            float turn = smooth(Math.min(1, c.t / (c.duration * (c.phase == Phase.TOP_IN ? 0.5f : 1f))));
            a.yaw = c.startYaw + Actor.wrap(wallYaw - c.startYaw) * turn;
        } else a.yaw = wallYaw;
        a.ladderNX = c.nx; a.ladderNZ = c.nz;
        if (a.body != 0) { g.game.commands().moverPlace(a.body, x, y, z, 0, 0, 0); g.game.commands().moverInput(a.body, 0, 0, 0, 0, 80, MOVER_POSED); }
        return true;
    }

    private void finish(Actor a, float x, float y, float z, String why) {
        a.climb = null; a.onLadder = false; a.ladderAgain = g.time + 0.35;
        a.x = x; a.y = y; a.z = z; a.grounded = true;
        if (a.body != 0) { g.game.commands().moverPlace(a.body, x, y + 0.02f, z, 0, 0, 0); g.game.commands().moverInput(a.body, 0, 0, 0, 0, 80, 0); }
        var e = g.state.commit("LadderLeft", a.id, 0, null, 0, why);
        if (why.contains("top")) g.prog.award(a, Skill.AGILITY, 3, "ladder climb", e.id());
    }

    /** Where the top of a ladder lets out: the ledge it hangs from first, else either side, else straight out in front. */
    private float[] exit(Actor a, Climb c) {
        int y = c.top + 1;
        int[][] cells = {{c.cx - Math.round(c.nx), c.cz - Math.round(c.nz)}, {c.cx + Math.round(c.nz), c.cz - Math.round(c.nx)}, {c.cx - Math.round(c.nz), c.cz + Math.round(c.nx)}, {c.cx, c.cz}};
        for (int[] cell : cells) {
            if (!g.vox.solid(cell[0], y - 1, cell[1]) && !ladder(g, cell[0], y - 1, cell[1])) continue;
            if (ladder(g, cell[0], y - 1, cell[1]) && (cell[0] != c.cx || cell[1] != c.cz)) continue;
            if (g.vox.solid(cell[0], y, cell[1]) || g.vox.solid(cell[0], y + 1, cell[1])) continue;
            // the column itself only when something stands beside it to step on (a hatch in a floor)
            if (cell[0] == c.cx && cell[1] == c.cz) continue;
            float px = cell[0] + 0.5f, pz = cell[1] + 0.5f;
            if (!room(a, px, y, pz)) continue;
            return new float[]{px, y, pz};
        }
        return null;
    }

    private boolean room(Actor a, float x, float y, float z) {
        float r = a.radius * 0.9f;
        for (int bx = (int) Math.floor(x - r); bx <= (int) Math.floor(x + r); bx++)
            for (int bz = (int) Math.floor(z - r); bz <= (int) Math.floor(z + r); bz++)
                for (int by = (int) Math.floor(y + 0.05f); by <= (int) Math.floor(y + a.height - 0.05f); by++) if (g.vox.solid(bx, by, bz)) return false;
        return true;
    }

    private static float mix(float a, float b, float t) { return a + (b - a) * t; }
    private static float smooth(float t) { t = Math.max(0, Math.min(1, t)); return t * t * (3 - 2 * t); }
}
