package rpg.world;

import astratto.bridge.WorldView;

/**
 * Questions about the engine's world asked directly of its memory (the read-only view - no copy): what block is where,
 * whether a line is clear, where a ray strikes. Used for the interaction raycast, melee and projectile contact, the
 * third-person camera and NPC perception, so all of them agree with what the engine simulates and draws.
 */
public final class Voxels {
    private final WorldView v;
    private final boolean[] solid = new boolean[256], opaque = new boolean[256], leafy = new boolean[256], glassy = new boolean[256];

    public Voxels(WorldView view) {
        this.v = view;
        for (int i = 1; i < Blocks.COUNT; i++) { solid[i] = true; opaque[i] = true; }
        solid[Blocks.WATER] = false; opaque[Blocks.WATER] = false;
        solid[Blocks.EMBERS] = false; opaque[Blocks.EMBERS] = false;
        for (int i = 1; i < Blocks.COUNT; i++) if (Blocks.glass(i)) { opaque[i] = false; glassy[i] = true; }
        opaque[Blocks.ICE] = false;
        for (int i : new int[]{Blocks.LEAVES, Blocks.OAK_LEAVES, Blocks.WILLOW_LEAVES, Blocks.FENCE, Blocks.BERRY_BUSH}) opaque[i] = false;
        for (int i : new int[]{Blocks.LEAVES, Blocks.OAK_LEAVES, Blocks.WILLOW_LEAVES, Blocks.BERRY_BUSH}) leafy[i] = true;
        // objects stand on invisible cells: low furniture is seen over, tall pieces (a bookcase, the furnace's stack) still hide
        for (int i = 1; i < Blocks.COUNT; i++) if (Blocks.objectCell(i)) opaque[i] = i == Blocks.BOOKCASE || i == Blocks.FURNACE;
        for (int i = 1; i < Blocks.COUNT; i++) if (Blocks.plant(i)) { solid[i] = false; opaque[i] = false; }
    }

    public long navigationRevision;
    private boolean navigationPending;
    public void edited(int before,int after) {
        if(solid[before]!=solid[after] || door(before)!=door(after) || before!=after && (door(before)||door(after))) {navigationRevision++;navigationPending=true;}
    }
    public void afterCommands(){if(navigationPending){navigationRevision++;navigationPending=false;}}

    private java.util.List<rpg.objects.Door> doors = java.util.List.of();
    public void doors(java.util.List<rpg.objects.Door> doors) { this.doors = doors; }
    private static boolean door(int block) { return block == Blocks.DOOR || block == Blocks.LOCKED_DOOR; }

    public int block(int x, int y, int z) { return v.block(x, y, z); }
    public boolean solid(int x, int y, int z) { return solid[v.block(x, y, z)]; }
    public boolean opaque(int x, int y, int z) { return opaque[v.block(x, y, z)]; }

    /** Where a ray first enters a solid cell. */
    public static final class Hit {
        public float dist, x, y, z;
        public int bx, by, bz, nx, ny, nz, block;
        public boolean hit;
        /** The door whose swinging leaf was struck (null: a cell). */
        public rpg.objects.Door door;
    }

    /** Casts a ray (direction need not be unit) up to max metres; solidOnly false also stops at liquids. */
    public Hit ray(float ox, float oy, float oz, float dx, float dy, float dz, float max, boolean stopAtWater) { return ray(ox, oy, oz, dx, dy, dz, max, stopAtWater, true); }

    /** The same, optionally past open doors' leaves (an open leaf swings out of the way of a blade or a reaching hand; a shut one is still a door). */
    public Hit ray(float ox, float oy, float oz, float dx, float dy, float dz, float max, boolean stopAtWater, boolean leaves) {
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
            if (b != 0 && (!door(b) && solid[b] || stopAtWater && b == Blocks.WATER)) {
                h.hit = true; h.dist = t; h.bx = x; h.by = y; h.bz = z; h.nx = nx; h.ny = ny; h.nz = nz; h.block = b;
                h.x = ox + dx * t; h.y = oy + dy * t; h.z = oz + dz * t;
                for (var panel : doors) if (leaves || !panel.open) panel.ray(h, ox, oy, oz, dx, dy, dz, t);
                return h;
            }
            if (tmx < tmy && tmx < tmz) { t = tmx; tmx += tdx; x += sx; nx = -sx; ny = 0; nz = 0; }
            else if (tmy < tmz) { t = tmy; tmy += tdy; y += sy; nx = 0; ny = -sy; nz = 0; }
            else { t = tmz; tmz += tdz; z += sz; nx = 0; ny = 0; nz = -sz; }
        }
        for (var panel : doors) if (leaves || !panel.open) panel.ray(h, ox, oy, oz, dx, dy, dz, max);
        return h;
    }

    /** Whether a segment crosses no solid cell (contact through walls is impossible). */
    public boolean clear(float ax, float ay, float az, float bx, float by, float bz) { return clear(ax, ay, az, bx, by, bz, true); }
    /** The same, optionally past open door leaves (a swung leaf does not shield what stands beside it from a blade). */
    public boolean clear(float ax, float ay, float az, float bx, float by, float bz, boolean leaves) {
        float dx = bx - ax, dy = by - ay, dz = bz - az, d = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (d < 1e-4f) return !solid((int) Math.floor(ax), (int) Math.floor(ay), (int) Math.floor(az));
        Hit h = ray(ax, ay, az, dx, dy, dz, d, false, leaves);
        return !h.hit || h.dist >= d;
    }

    /** Whether one could see along a segment: opaque cells block, glass, leaves and fences do not. */
    /** Look at a cell's near surface, rather than letting its own solid interior occlude it. */
    public boolean sightCell(float ax,float ay,float az,int x,int y,int z) {
        float cx=x+0.5f,cy=y+0.5f,cz=z+0.5f,dx=ax-cx,dy=ay-cy,dz=az-cz;
        float largest=Math.max(Math.abs(dx),Math.max(Math.abs(dy),Math.abs(dz)));
        if(largest<0.52f)return true;
        float scale=0.52f/largest;
        return sight(ax,ay,az,cx+dx*scale,cy+dy*scale,cz+dz*scale);
    }

    public boolean sight(float ax, float ay, float az, float bx, float by, float bz) {
        float dx = bx - ax, dy = by - ay, dz = bz - az, d = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        int steps = (int) Math.ceil(d / 0.25f);
        for (int i = 1; i < steps; i++) {
            float t = i / (float) steps;
            int block = block((int)Math.floor(ax+dx*t), (int)Math.floor(ay+dy*t), (int)Math.floor(az+dz*t));
            if (!door(block) && opaque[block]) return false;
        }
        return !panelBlocks(ax, ay, az, dx, dy, dz, d);
    }

    private boolean panelBlocks(float x,float y,float z,float dx,float dy,float dz,float length) {
        if (length < 1e-5f) return false;
        Hit hit = new Hit();
        for (var panel : doors) panel.ray(hit, x,y,z,dx/length,dy/length,dz/length,length);
        return hit.hit && hit.dist < length - 0.02f;
    }

    /** The y of the ground under a point (the top of the highest solid cell at or below y). */
    /**
     * How much of a sight line gets through, 0 .. 1: an opaque cell stops it; each leafy cell it crosses (leaves, a berry
     * bush) lets some through - foliage hides without walling off. Glass and fences cost nothing.
     */
    public float seeThrough(float ax, float ay, float az, float bx, float by, float bz) { return seeThrough(ax, ay, az, bx, by, bz, Float.MAX_VALUE); }

    /**
     * The same for eyes that must make out a figure (gamefixes: NPCs see through glass, but recognise nothing behind it
     * past five blocks): a line that crosses glass and is longer than {@code glassReach} gets nothing through, and each
     * pane it does cross dims it a little.
     */
    public float seeThrough(float ax, float ay, float az, float bx, float by, float bz, float glassReach) {
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
            if (!door(b) && opaque[b]) return 0;
            if (leafy[b]) t *= 0.55f;
            if (glassy[b]) { if (d > glassReach) return 0; t *= 0.85f; }
        }
        return panelBlocks(ax,ay,az,dx,dy,dz,d) ? 0 : t;
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
