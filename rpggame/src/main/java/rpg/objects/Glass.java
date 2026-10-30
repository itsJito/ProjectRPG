package rpg.objects;

import astratto.bridge.AstrattoNative;
import java.util.ArrayDeque;
import java.util.Random;
import rpg.Rpg;
import rpg.actor.Actor;
import rpg.world.Blocks;
import rpg.world.DevMap;

/**
 * Glass that breaks (gamefixes: glass breaks into shards that are physical objects with physics). A pane struck hard
 * enough - by a blade, an arrow or a spell, or by a body thrown into it - is gone from the world, and in its place a
 * scatter of shards: thin convex hulls (Impulsis), each its own irregular shape, flung along the blow, tumbling, clinking
 * as they land (their impacts are heard) and coming to rest on the floor. The noise carries; breaking a pane that
 * belongs to someone is a crime for any witness to report. Shards fade away after a while, the oldest first.
 */
public final class Glass {
    /** One shard: its body, its own mesh (a triangle prism), when it was made. */
    public static final class Shard {
        public long body;
        public float[] mesh;
        public int gpu;
        public double born;
    }

    public static final int MATERIAL_GLASS = 3, MAX_SHARDS = 140;
    public static final float LIFETIME = 60;
    private final Rpg g;
    public final ArrayDeque<Shard> shards = new ArrayDeque<>();
    private final Random rnd = new Random(77);
    public int broken;

    public Glass(Rpg g) { this.g = g; }

    /** Whether a cell is glass that can break. */
    public boolean isGlass(int x, int y, int z) { return Blocks.glass(g.vox.block(x, y, z)); }

    /**
     * Breaks the pane at a cell: struck at (px, py, pz) along (dx, dy, dz) with a force (about 1 for a sword's blow).
     * by: who broke it (0-able); for the law.
     */
    public void shatter(int bx, int by, int bz, float px, float py, float pz, float dx, float dy, float dz, float force, Actor who) {
        if (!isGlass(bx, by, bz)) return;
        int pane = g.vox.block(bx, by, bz);
        g.setBlock(bx, by, bz, Blocks.AIR);
        if (g.repairs != null) g.repairs.broken(bx, by, bz, pane, "glass");
        broken++;
        float dl = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (dl < 1e-4f) { dx = 0; dy = 0; dz = 1; dl = 1; }
        dx /= dl; dy /= dl; dz /= dl;
        // the pane's plane: across the axis the blow mostly travels along
        int axis = Math.abs(dx) > Math.abs(dz) ? 0 : 2;
        int n = 10 + rnd.nextInt(6);
        for (int i = 0; i < n; i++) {
            float u = rnd.nextFloat(), v = rnd.nextFloat();
            float sx = axis == 0 ? bx + 0.5f : bx + 0.08f + u * 0.84f, sz = axis == 0 ? bz + 0.08f + u * 0.84f : bz + 0.5f, sy = by + 0.08f + v * 0.84f;
            // nearer the point struck, the faster a shard leaves
            float near = 1f / (1f + 3f * (float) Math.hypot(sx - px, sy - py) + 3f * (float) Math.abs((axis == 0 ? sz - pz : sx - px)));
            float speed = (0.6f + 3.2f * near) * Math.max(0.4f, force) + rnd.nextFloat() * 0.6f;
            float vx = dx * speed + (rnd.nextFloat() - 0.5f) * 1.2f, vy = dy * speed + rnd.nextFloat() * 1.0f, vz = dz * speed + (rnd.nextFloat() - 0.5f) * 1.2f;
            spawn(sx, sy, sz, vx, vy, vz, 0.07f + rnd.nextFloat() * 0.14f);
        }
        while (shards.size() > MAX_SHARDS) remove(shards.pollFirst());
        if (g.sfx != null) g.sfx.play("glass.break", px, py, pz, 0.9f, 0.95f + rnd.nextFloat() * 0.1f, 40);
        g.noise(who, px, py, pz, 18, 0.9f);
        if (g.fx != null) g.fx.sparks(px, py, pz, 6);
        var ev = g.state.commit("GlassBroken", who == null ? 0 : who.id, 0, DevMap.jurisdiction(px, pz), 0, String.format("pane at (%d, %d, %d)", bx, by, bz));
        // a pane in a building in a jurisdiction belongs to someone: breaking it is a crime
        if (who != null && !DevMap.WILDS.equals(DevMap.jurisdiction(px, pz))) g.law.crime(who, "VANDALISM", 0, 6, null, ev.id());
    }

    /** One shard: a triangle of glass (three random corners round a centre), 5 mm thick, as a convex hull. */
    private void spawn(float x, float y, float z, float vx, float vy, float vz, float size) {
        float[] hull = new float[18];
        float t = 0.006f;
        double a0 = rnd.nextDouble() * Math.PI * 2;
        for (int k = 0; k < 3; k++) {
            double a = a0 + k * (2 * Math.PI / 3) + (rnd.nextDouble() - 0.5) * 1.1;
            float r = size * (0.55f + 0.45f * rnd.nextFloat());
            float cx = (float) Math.cos(a) * r, cz = (float) Math.sin(a) * r;
            hull[k * 3] = cx; hull[k * 3 + 1] = -t; hull[k * 3 + 2] = cz;
            hull[9 + k * 3] = cx; hull[9 + k * 3 + 1] = t; hull[9 + k * 3 + 2] = cz;
        }
        Shard s = new Shard();
        s.body = g.game.reserve(AstrattoNative.KIND_RIGID);
        s.born = g.time;
        s.mesh = prism(hull);
        // a random orientation: shards leave a pane edge-on and tumbling
        float ax = rnd.nextFloat() * 2 - 1, ay = rnd.nextFloat() * 2 - 1, az = rnd.nextFloat() * 2 - 1, al = (float) Math.sqrt(ax * ax + ay * ay + az * az) + 1e-6f;
        float ang = rnd.nextFloat() * 3.1f, sn = (float) Math.sin(ang / 2) / al;
        g.game.commands().rigidCreate(s.body, AstrattoNative.SHAPE_HULL, AstrattoNative.RIGID_DEBRIS | AstrattoNative.RIGID_REPORT | AstrattoNative.RIGID_NO_MOVERS,
                0, 0, 0, 0.03f + size * 0.3f, x, y, z, (float) Math.cos(ang / 2), ax * sn, ay * sn, az * sn, vx, vy, vz,
                (rnd.nextFloat() - 0.5f) * 18, (rnd.nextFloat() - 0.5f) * 18, (rnd.nextFloat() - 0.5f) * 18, 0.35f, 0.15f, MATERIAL_GLASS, hull);
        shards.addLast(s);
    }

    /** A hull of two triangles (bottom then top) as 10-float vertices: the faces of a prism, with normals. */
    private static float[] prism(float[] h) {
        float[] v = new float[8 * 3 * 10];
        int k = 0;
        int[][] tris = {{0, 2, 1}, {3, 4, 5}, {0, 1, 4}, {0, 4, 3}, {1, 2, 5}, {1, 5, 4}, {2, 0, 3}, {2, 3, 5}};
        for (int[] t : tris) {
            float[] a = {h[t[0] * 3], h[t[0] * 3 + 1], h[t[0] * 3 + 2]}, b = {h[t[1] * 3], h[t[1] * 3 + 1], h[t[1] * 3 + 2]}, c = {h[t[2] * 3], h[t[2] * 3 + 1], h[t[2] * 3 + 2]};
            float ux = b[0] - a[0], uy = b[1] - a[1], uz = b[2] - a[2], wx = c[0] - a[0], wy = c[1] - a[1], wz = c[2] - a[2];
            float nx = uy * wz - uz * wy, ny = uz * wx - ux * wz, nz = ux * wy - uy * wx, nl = (float) Math.sqrt(nx * nx + ny * ny + nz * nz) + 1e-9f;
            // outward: away from the prism's middle
            float mx = (a[0] + b[0] + c[0]) / 3, my = (a[1] + b[1] + c[1]) / 3, mz = (a[2] + b[2] + c[2]) / 3;
            float cx = (h[0] + h[3] + h[6]) / 3, cz = (h[2] + h[5] + h[8]) / 3;
            boolean flip = (mx - cx) * nx + my * ny + (mz - cz) * nz < 0;
            float[][] order = flip ? new float[][]{a, c, b} : new float[][]{a, b, c};
            float s = flip ? -1 : 1;
            for (float[] p : order) {
                v[k++] = p[0]; v[k++] = p[1]; v[k++] = p[2]; v[k++] = 1; v[k++] = 1; v[k++] = 1; v[k++] = 1;
                v[k++] = s * nx / nl; v[k++] = s * ny / nl; v[k++] = s * nz / nl;
            }
        }
        return v;
    }

    /** Old shards go (they have lain long enough), and shards that fell out of the world. */
    public void update() {
        while (!shards.isEmpty() && g.time - shards.peekFirst().born > LIFETIME) remove(shards.pollFirst());
        for (var it = shards.iterator(); it.hasNext(); ) {
            Shard s = it.next();
            var r = g.lastRigids.body(s.body);
            if (r != null && r.y < 0) { remove(s); it.remove(); }
        }
    }

    private void remove(Shard s) { if (s != null) { g.game.commands().release(s.body); s.body = 0; } }

    /** A shard struck something: a clink, quieter as it slows. */
    public void clink(float x, float y, float z, float speed) {
        if (g.sfx != null && speed > 0.8f) g.sfx.play("glass.light", x, y, z, Math.min(0.5f, speed * 0.08f), 1.1f + rnd.nextFloat() * 0.3f, 14);
    }
}
