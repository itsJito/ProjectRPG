package rpg.world;

import astratto.bridge.WorldView;

/**
 * Questions about the engine's world asked directly of its memory (the read-only view - no copy): what block is where,
 * whether a line is clear, where a ray strikes. Used for the interaction raycast, melee and projectile contact, the
 * third-person camera and NPC perception, so all of them agree with what the engine simulates and draws.
 */
public final class Voxels {
    private final WorldView v;
    public final rpg.ai.RuntimeNavigation navigation = new rpg.ai.RuntimeNavigation(this);
    public int sizeX(){return v.sizeX;} public int sizeY(){return v.sizeY;} public int sizeZ(){return v.sizeZ;}
    private final boolean[] solid = new boolean[256], opaque = new boolean[256], leafy = new boolean[256], glassy = new boolean[256];

    private final int[] navigationType=new int[256];

    public Voxels(WorldView view) {
        this.v = view;
        var physical=Blocks.engineDefs();
        int navMask=astratto.bridge.AstrattoNative.BLOCK_SOLID | astratto.bridge.AstrattoNative.BLOCK_DOOR
            | astratto.bridge.AstrattoNative.BLOCK_HAZARD | astratto.bridge.AstrattoNative.BLOCK_LIQUID
            | astratto.bridge.AstrattoNative.BLOCK_CLIMB | astratto.bridge.AstrattoNative.BLOCK_STEP;
        for(int i=0;i<physical.length;i++)navigationType[i]=physical[i].flags & navMask;
        for (int i = 1; i < Blocks.COUNT; i++) { solid[i] = true; opaque[i] = true; }
        solid[Blocks.LADDER]=false; opaque[Blocks.LADDER]=false;
        solid[Blocks.WATER] = false; opaque[Blocks.WATER] = false;
        solid[Blocks.EMBERS] = false; opaque[Blocks.EMBERS] = false;
        for (int i = 1; i < Blocks.COUNT; i++) if (Blocks.glass(i)) { opaque[i] = false; glassy[i] = true; }
        opaque[Blocks.ICE] = false;
        for (int i : new int[]{Blocks.LEAVES, Blocks.OAK_LEAVES, Blocks.WILLOW_LEAVES, Blocks.PINE_LEAVES, Blocks.FENCE, Blocks.BERRY_BUSH}) opaque[i] = false;
        for (int i : new int[]{Blocks.LEAVES, Blocks.OAK_LEAVES, Blocks.WILLOW_LEAVES, Blocks.PINE_LEAVES, Blocks.BERRY_BUSH}) leafy[i] = true;
        // objects stand on invisible cells: low furniture is seen over, tall pieces (a bookcase, the furnace's stack) still hide
        for (int i = 1; i < Blocks.COUNT; i++) if (Blocks.objectCell(i)) opaque[i] = i == Blocks.BOOKCASE || i == Blocks.FURNACE;
        for (int i = 1; i < Blocks.COUNT; i++) if (Blocks.plant(i)) { solid[i] = false; opaque[i] = false; }
    }

    public long navigationRevision;
    private boolean navigationPending;
    // (0.4.0, optimisation: ShadowVeil's 4,489 chunks) each chunk's version in an array by its index: the map keyed by
    // (cx << 32) ^ cz hashed every chunk of a column of chunks to a handful of buckets (a long's hash folds its halves)
    private long[] chunkVersions;
    private final java.util.BitSet pendingChunks=new java.util.BitSet();
    private int chunkIndex(float x,float z){
        int cx=Math.floorDiv((int)Math.floor(x),16),cz=Math.floorDiv((int)Math.floor(z),16),nx=(v.sizeX+15)>>4,nz=(v.sizeZ+15)>>4;
        if(chunkVersions==null)chunkVersions=new long[nx*nz];
        return cx<0 || cz<0 || cx>=nx || cz>=nz ? -1 : cx*nz+cz;
    }
    public long chunkVersion(float x,float z){int i=chunkIndex(x,z);return i<0?0:chunkVersions[i];}
    public void edited(int x,int y,int z,int before,int after){
        long revision=navigationRevision;edited(before,after);
        if(revision!=navigationRevision){int i=chunkIndex(x,z);if(i>=0){chunkVersions[i]++;pendingChunks.set(i);}}
    }
    /** Closest supported, two-cell-high surface; preserves separate floors in stacked interiors. */
    public float standY(float x,float near,float z){
        int bx=(int)Math.floor(x),bz=(int)Math.floor(z),mid=(int)Math.floor(near);
        for(int d=0;d<=12;d++)for(int y:new int[]{mid-d,mid+d})
            if(y>0 && y+1<v.sizeY && solid(bx,y-1,bz) && !solid(bx,y,bz) && !solid(bx,y+1,bz))return y;
        return Float.NaN;
    }
    public void edited(int before,int after) {
        if(navigationType[before]!=navigationType[after] || before!=after && (door(before)||door(after))) {navigationRevision++;navigationPending=true;}
    }
    public void afterCommands(){if(navigationPending){navigationRevision++;navigationPending=false;for(int i=pendingChunks.nextSetBit(0);i>=0;i=pendingChunks.nextSetBit(i+1))chunkVersions[i]++;pendingChunks.clear();}}

    private java.util.List<rpg.objects.Door> doors = java.util.List.of();
    public void doors(java.util.List<rpg.objects.Door> doors) { this.doors = doors; navigation.doors(doors); doorGridOf = -1; }

    // (0.4.7, gamefixes: aggressive optimization) the doors by the sixteen-cell square they hang in: every ray and line of
    // sight in the game tried every door in the world (over a thousand on ShadowVeil), thousands of times a frame in a fight
    private final java.util.Map<Long, java.util.List<rpg.objects.Door>> doorGrid = new java.util.HashMap<>();
    private int doorGridOf = -1;
    private final java.util.ArrayList<rpg.objects.Door> nearDoors = new java.util.ArrayList<>();
    private final Thread doorThread = Thread.currentThread();
    private static final int DOOR_SQUARE = 16;
    private static final float DOOR_PAD = 4;
    /** The doors whose leaves could cross a segment (x, z to ex, ez): those hung within a few metres of its box. */
    private java.util.List<rpg.objects.Door> doorsNear(float ox, float oz, float ex, float ez) {
        if (Thread.currentThread() != doorThread || doors.size() < 32) return doors;
        if (doorGridOf != doors.size()) {
            doorGrid.clear(); doorGridOf = doors.size();
            for (var d : doors) doorGrid.computeIfAbsent(squareKey((int) Math.floor(d.x / DOOR_SQUARE), (int) Math.floor(d.z / DOOR_SQUARE)), k -> new java.util.ArrayList<>(2)).add(d);
        }
        int x0 = (int) Math.floor((Math.min(ox, ex) - DOOR_PAD) / DOOR_SQUARE), x1 = (int) Math.floor((Math.max(ox, ex) + DOOR_PAD) / DOOR_SQUARE);
        int z0 = (int) Math.floor((Math.min(oz, ez) - DOOR_PAD) / DOOR_SQUARE), z1 = (int) Math.floor((Math.max(oz, ez) + DOOR_PAD) / DOOR_SQUARE);
        if ((long) (x1 - x0 + 1) * (z1 - z0 + 1) > 64) return doors;   // a very long ray: the whole list is as quick
        nearDoors.clear();
        for (int cx = x0; cx <= x1; cx++) for (int cz = z0; cz <= z1; cz++) { var l = doorGrid.get(squareKey(cx, cz)); if (l != null) nearDoors.addAll(l); }
        return nearDoors;
    }
    private static long squareKey(int x, int z) { return ((long) x << 32) ^ (z & 0xFFFFFFFFL); }
    private static boolean door(int block) { return block == Blocks.DOOR || block == Blocks.LOCKED_DOOR; }

    public int block(int x, int y, int z) { return v.block(x, y, z); }
    public boolean solid(int x, int y, int z) { return solid[v.block(x, y, z)]; }
    public boolean opaque(int x, int y, int z) { return opaque[v.block(x, y, z)]; }
    /** (0.3.6) A cell of leaves or a berry bush: solid to a walker, brush that a mount's weight pushes through. */
    public boolean leafy(int x, int y, int z) { return leafy[v.block(x, y, z)]; }
    /** Solid, and not brush (what stops a ridden mount). */
    public boolean hard(int x, int y, int z) { int b = v.block(x, y, z); return solid[b] && !leafy[b]; }

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
    public Hit ray(float ox, float oy, float oz, float dx, float dy, float dz, float max, boolean stopAtWater, boolean leaves) { return ray(ox, oy, oz, dx, dy, dz, max, stopAtWater, leaves, false); }

    /**
     * (0.4.5, gamefixes: none of the furniture could be broken) The cells a piece of furniture stands on (its invisible
     * collision): a blade, an arrow or a spell passes into them to meet the piece's own parts (rpg.objects.Wreckage) -
     * the cell's face is not the piece. Null: none.
     */
    public java.util.function.LongPredicate furnitureCells;
    public static long cellKey(int x, int y, int z) { return ((long) x & 0xfffff) << 40 | ((long) y & 0xfffff) << 20 | (long) z & 0xfffff; }

    /** The same, passing through furniture's own cells when asked (a blow or a shot: the piece's parts take it, not its cell). */
    public Hit ray(float ox, float oy, float oz, float dx, float dy, float dz, float max, boolean stopAtWater, boolean leaves, boolean throughFurniture) {
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
            if (b != 0 && (!door(b) && solid[b] || stopAtWater && b == Blocks.WATER) && !(throughFurniture && furnitureCells != null && furnitureCells.test(cellKey(x, y, z)))) {
                h.hit = true; h.dist = t; h.bx = x; h.by = y; h.bz = z; h.nx = nx; h.ny = ny; h.nz = nz; h.block = b;
                h.x = ox + dx * t; h.y = oy + dy * t; h.z = oz + dz * t;
                for (var panel : doorsNear(ox, oz, ox + dx * t, oz + dz * t)) if (leaves || !panel.open) panel.ray(h, ox, oy, oz, dx, dy, dz, t);
                return h;
            }
            if (tmx < tmy && tmx < tmz) { t = tmx; tmx += tdx; x += sx; nx = -sx; ny = 0; nz = 0; }
            else if (tmy < tmz) { t = tmy; tmy += tdy; y += sy; nx = 0; ny = -sy; nz = 0; }
            else { t = tmz; tmz += tdz; z += sz; nx = 0; ny = 0; nz = -sz; }
        }
        for (var panel : doorsNear(ox, oz, ox + dx * max, oz + dz * max)) if (leaves || !panel.open) panel.ray(h, ox, oy, oz, dx, dy, dz, max);
        return h;
    }

    /** Whether a segment crosses no solid cell (contact through walls is impossible). */
    public boolean clear(float ax, float ay, float az, float bx, float by, float bz) { return clear(ax, ay, az, bx, by, bz, true); }
    /** The same, optionally past open door leaves (a swung leaf does not shield what stands beside it from a blade). */
    public boolean clear(float ax, float ay, float az, float bx, float by, float bz, boolean leaves) { return clear(ax, ay, az, bx, by, bz, leaves, false); }
    /** The same, the furniture's own cells passed through when asked (a blade's length reaching into a table). */
    public boolean clear(float ax, float ay, float az, float bx, float by, float bz, boolean leaves, boolean throughFurniture) {
        float dx = bx - ax, dy = by - ay, dz = bz - az, d = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (d < 1e-4f) { int x = (int) Math.floor(ax), y = (int) Math.floor(ay), z = (int) Math.floor(az); return !solid(x, y, z) || throughFurniture && furnitureCells != null && furnitureCells.test(cellKey(x, y, z)); }
        Hit h = ray(ax, ay, az, dx, dy, dz, d, false, leaves, throughFurniture);
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
        for (var panel : doorsNear(x, z, x + dx, z + dz)) panel.ray(hit, x,y,z,dx/length,dy/length,dz/length,length);
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
