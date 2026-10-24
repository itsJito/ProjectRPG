package rpg.world;

import astratto.bridge.WorldView;

/**
 * Questions about the engine's world asked directly of its memory (the read-only view - no copy): what block is where,
 * whether a line is clear, where a ray strikes. Used for the interaction raycast, melee and projectile contact, the
 * third-person camera and NPC perception, so all of them agree with what the engine simulates and draws.
 */
public final class Voxels {
    private final WorldView v;
    private final boolean[] solid = new boolean[256], opaque = new boolean[256], leafy = new boolean[256];

    public Voxels(WorldView view) {
        this.v = view;
        for (int i = 1; i < Blocks.COUNT; i++) { solid[i] = true; opaque[i] = true; }
        solid[Blocks.WATER] = false; opaque[Blocks.WATER] = false;
        opaque[Blocks.GLASS] = false; opaque[Blocks.ICE] = false;
        for (int i : new int[]{Blocks.LEAVES, Blocks.OAK_LEAVES, Blocks.WILLOW_LEAVES, Blocks.FENCE, Blocks.BERRY_BUSH}) opaque[i] = false;
        for (int i : new int[]{Blocks.LEAVES, Blocks.OAK_LEAVES, Blocks.WILLOW_LEAVES, Blocks.BERRY_BUSH}) leafy[i] = true;
        // objects stand on invisible cells: low furniture is seen over, tall pieces (a bookcase, the furnace's stack) still hide
        for (int i = 1; i < Blocks.COUNT; i++) if (Blocks.objectCell(i)) opaque[i] = i == Blocks.BOOKCASE || i == Blocks.FURNACE;
        for (int i = 1; i < Blocks.COUNT; i++) if (Blocks.plant(i)) { solid[i] = false; opaque[i] = false; }
    }

    public int block(int x, int y, int z) { return v.block(x, y, z); }
    public boolean solid(int x, int y, int z) { return solid[v.block(x, y, z)]; }
    public boolean opaque(int x, int y, int z) { return opaque[v.block(x, y, z)]; }

    /** Where a ray first enters a solid cell. */
    public static final class Hit {
        public float dist, x, y, z;
        public int bx, by, bz, nx, ny, nz, block;
        public boolean hit;
    }

    /** Casts a ray (direction need not be unit) up to max metres; solidOnly false also stops at liquids. */
    public Hit ray(float ox, float oy, float oz, float dx, float dy, float dz, float max, boolean stopAtWater) {
        Hit h = new Hit();
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1e-6f) return h;
        dx /= len; dy /= len; dz /= len;
        int x = (int) Math.floor(ox), y = (int) Math.floor(oy), z = (int) Math.floor(oz);
        int sx = dx > 0 ? 1 : -1, sy = dy > 0 ? 1 : -1, sz = dz > 0 ? 1 : -1;
        float tdx = dx == 0 ? Float.MAX_VALUE : Math.abs(1 / dx), tdy = dy == 0 ? Float.MAX_VALUE : Math.abs(1 / dy), tdz = dz == 0 ? Float.MAX_VALUE : Math.abs(1 / dz);
        float tmx = dx == 0 ? Float.MAX_VALUE : ((dx > 0 ? (x + 1 - ox) : (ox - x)) * tdx);
        float tmy = dy == 0 ? Float.MAX_VALUE : ((dy > 0 ? (y + 1 - oy) : (oy - y)) * tdy);
        float tmz = dz == 0 ? Float.MAX_VALUE : ((dz > 0 ? (z + 1 - oz) : (oz - z)) * tdz);
        float t = 0;
        int nx = 0, ny = 0, nz = 0;
        for (int i = 0; i < 512 && t <= max; i++) {
            int b = v.block(x, y, z);
            if (b != 0 && (solid[b] || (stopAtWater && b == Blocks.WATER))) {
                h.hit = true; h.dist = t; h.bx = x; h.by = y; h.bz = z; h.nx = nx; h.ny = ny; h.nz = nz; h.block = b;
                h.x = ox + dx * t; h.y = oy + dy * t; h.z = oz + dz * t;
                return h;
            }
            if (tmx < tmy && tmx < tmz) { t = tmx; tmx += tdx; x += sx; nx = -sx; ny = 0; nz = 0; }
            else if (tmy < tmz) { t = tmy; tmy += tdy; y += sy; nx = 0; ny = -sy; nz = 0; }
            else { t = tmz; tmz += tdz; z += sz; nx = 0; ny = 0; nz = -sz; }
        }
        return h;
    }

    /** Whether a segment crosses no solid cell (contact through walls is impossible). */
    public boolean clear(float ax, float ay, float az, float bx, float by, float bz) {
        float dx = bx - ax, dy = by - ay, dz = bz - az, d = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (d < 1e-4f) return !solid((int) Math.floor(ax), (int) Math.floor(ay), (int) Math.floor(az));
        Hit h = ray(ax, ay, az, dx, dy, dz, d, false);
        return !h.hit || h.dist >= d;
    }

    /** Whether one could see along a segment: opaque cells block, glass, leaves and fences do not. */
    public boolean sight(float ax, float ay, float az, float bx, float by, float bz) {
        float dx = bx - ax, dy = by - ay, dz = bz - az, d = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        int steps = (int) Math.ceil(d / 0.25f);
        for (int i = 1; i < steps; i++) {
            float t = i / (float) steps;
            if (opaque((int) Math.floor(ax + dx * t), (int) Math.floor(ay + dy * t), (int) Math.floor(az + dz * t))) return false;
        }
        return true;
    }

    /** The y of the ground under a point (the top of the highest solid cell at or below y). */
    /**
     * How much of a sight line gets through, 0 .. 1: an opaque cell stops it; each leafy cell it crosses (leaves, a berry
     * bush) lets some through - foliage hides without walling off. Glass and fences cost nothing.
     */
    public float seeThrough(float ax, float ay, float az, float bx, float by, float bz) {
        float dx = bx - ax, dy = by - ay, dz = bz - az, d = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        int steps = (int) Math.ceil(d / 0.25f);
        float t = 1;
        int lx = Integer.MIN_VALUE, ly = 0, lz = 0;
        for (int i = 1; i < steps; i++) {
            float f = i / (float) steps;
            int x = (int) Math.floor(ax + dx * f), y = (int) Math.floor(ay + dy * f), z = (int) Math.floor(az + dz * f);
            if (x == lx && y == ly && z == lz) continue;
            lx = x; ly = y; lz = z;
            int b = v.block(x, y, z);
            if (opaque[b]) return 0;
            if (leafy[b]) t *= 0.55f;
        }
        return t;
    }

    /** The light in a cell, 0 .. 1 each: what the sky gives it (before the time of day) and what lamps and glowing blocks do. */
    public float skyLight(int x, int y, int z) { return ((v.light(x, y, z) >> 4) & 15) / 15f; }
    public float blockLight(int x, int y, int z) { return (v.light(x, y, z) & 15) / 15f; }

    public float groundY(float x, float y, float z) {
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        for (int yy = (int) Math.floor(y); yy >= 0; yy--) if (solid(bx, yy, bz)) return yy + 1;
        return 0;
    }

    public WorldView view() { return v; }
}
