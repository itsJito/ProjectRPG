package rpg.world;

import astratto.bridge.AstrattoNative;
import astratto.bridge.NativeEngine;
import astratto.bridge.NativeMesher;
import java.nio.ByteBuffer;
import java.util.Random;
import org.lwjgl.system.MemoryUtil;

/**
 * The devmap's blocks: what each id means to the engine (collision, sight, light, doors, water), how it looks (64 x 64
 * texture layers painted here from code), and the colours the minimap and the probes use. A block is one cubic metre.
 *
 * Furniture and workstations are objects, not blocks: their cells (tables, counters, crates, bookcases, beds, targets,
 * the furnace, anvil, range, crafting table and sawbench) are invisible collision the engine still walks, routes and
 * casts rays against, while the game draws each one as its own model. Doors are drawn by the game too, as leaves that
 * swing; their cells only say where a closed door stands.
 */
public final class Blocks {
    private Blocks() {}

    public static final int AIR = 0, GRASS = 1, DIRT = 2, STONE = 3, BRICK = 4, GLASS = 5, LAMP = 6, PLANKS = 7, LOG = 8, LEAVES = 9,
            OAK_LOG = 10, OAK_LEAVES = 11, WILLOW_LOG = 12, WILLOW_LEAVES = 13, COPPER_ROCK = 14, TIN_ROCK = 15, IRON_ROCK = 16,
            COAL_ROCK = 17, SPENT_ROCK = 18, WATER = 19, SAND = 20, COBBLE = 21, ROOF = 22, DOOR = 23, LOCKED_DOOR = 24,
            FURNACE = 25, ANVIL = 26, RANGE = 27, CRAFT_TABLE = 28, SAWBENCH = 29, TARGET = 30, FENCE = 31, STUMP = 32,
            HERB_PATCH = 33, FLAX_PATCH = 34, BERRY_BUSH = 35, CRATE = 36, BOOKCASE = 37, MARK_RED = 38, MARK_BLUE = 39,
            MARK_YELLOW = 40, DEBUG_FLOOR = 41, PLASTER = 42, BEAM = 43, BARE_PATCH = 44, MARK_GREEN = 45, TABLE = 46, BED = 47, HEADSTONE = 48, CHARRED = 49, ICE = 50, COUNT = 51;

    /** The cells of objects drawn by the game rather than meshed. */
    public static boolean objectCell(int id) {
        return id == FURNACE || id == ANVIL || id == RANGE || id == CRAFT_TABLE || id == SAWBENCH || id == TARGET || id == CRATE || id == BOOKCASE || id == TABLE || id == BED || id == HEADSTONE;
    }
    /** Full structural cells form the traversal graph; glass, gates, plants and thin props never do. */
    public static boolean traversable(int id) {
        return switch (id) { case GRASS, DIRT, STONE, BRICK, PLANKS, LOG, OAK_LOG, WILLOW_LOG, SAND, COBBLE, ROOF, CRATE, PLASTER, BEAM, CHARRED -> true; default -> false; };
    }
    /** Plants drawn as crossed planes that bodies walk through. */
    public static boolean plant(int id) { return id == HERB_PATCH || id == FLAX_PATCH; }

    // texture layers
    private static final int L_GRASS = 0, L_GRASS_SIDE = 1, L_DIRT = 2, L_STONE = 3, L_BRICK = 4, L_GLASS = 5, L_LAMP = 6, L_PLANKS = 7,
            L_BARK = 8, L_RINGS = 9, L_LEAVES = 10, L_OAK_BARK = 11, L_OAK_LEAVES = 12, L_WILLOW_BARK = 13, L_WILLOW_LEAVES = 14,
            L_COPPER = 15, L_TIN = 16, L_IRON = 17, L_COAL = 18, L_SPENT = 19, L_WATER = 20, L_SAND = 21, L_COBBLE = 22, L_ROOF = 23,
            L_DOOR = 24, L_LOCKED = 25, L_FURNACE = 26, L_METAL = 27, L_RANGE = 28, L_TABLE_TOP = 29, L_SAW = 30, L_TARGET = 31,
            L_FENCE = 32, L_HERB = 33, L_FLAX = 34, L_BERRY = 35, L_CRATE = 36, L_BOOKS = 37, L_RED = 38, L_BLUE = 39, L_YELLOW = 40,
            L_DEBUG = 41, L_PLASTER = 42, L_BEAM = 43, L_BARE = 44, L_GREEN = 45, L_TABLE_SIDE = 46;
    /** Texture layers, each TEX x TEX texels: 64 to the metre. */
    public static final int LAYERS = 47, TEX = 64;

    /** Per block id: top, side, bottom layer. */
    private static final int[][] FACES = new int[COUNT][];
    static {
        f(GRASS, L_GRASS, L_GRASS_SIDE, L_DIRT); f(DIRT, L_DIRT); f(STONE, L_STONE); f(BRICK, L_BRICK); f(GLASS, L_GLASS); f(LAMP, L_LAMP);
        f(PLANKS, L_PLANKS); f(LOG, L_RINGS, L_BARK, L_RINGS); f(LEAVES, L_LEAVES); f(OAK_LOG, L_RINGS, L_OAK_BARK, L_RINGS); f(OAK_LEAVES, L_OAK_LEAVES);
        f(WILLOW_LOG, L_RINGS, L_WILLOW_BARK, L_RINGS); f(WILLOW_LEAVES, L_WILLOW_LEAVES); f(COPPER_ROCK, L_COPPER); f(TIN_ROCK, L_TIN); f(IRON_ROCK, L_IRON);
        f(COAL_ROCK, L_COAL); f(SPENT_ROCK, L_SPENT); f(WATER, L_WATER); f(SAND, L_SAND); f(COBBLE, L_COBBLE); f(ROOF, L_ROOF); f(DOOR, L_DOOR);
        f(LOCKED_DOOR, L_LOCKED); f(FURNACE, L_STONE, L_FURNACE, L_STONE); f(ANVIL, L_METAL); f(RANGE, L_RANGE, L_BRICK, L_BRICK);
        f(CRAFT_TABLE, L_TABLE_TOP, L_TABLE_SIDE, L_PLANKS); f(SAWBENCH, L_SAW, L_TABLE_SIDE, L_PLANKS); f(TARGET, L_TARGET); f(FENCE, L_FENCE);
        f(STUMP, L_RINGS, L_BARK, L_RINGS); f(HERB_PATCH, L_HERB); f(FLAX_PATCH, L_FLAX); f(BERRY_BUSH, L_BERRY);
        f(CRATE, L_CRATE); f(BOOKCASE, L_PLANKS, L_BOOKS, L_PLANKS); f(MARK_RED, L_RED); f(MARK_BLUE, L_BLUE); f(MARK_YELLOW, L_YELLOW);
        f(DEBUG_FLOOR, L_DEBUG); f(PLASTER, L_PLASTER); f(BEAM, L_BEAM); f(BARE_PATCH, L_BARE, L_DIRT, L_DIRT); f(MARK_GREEN, L_GREEN);
        f(TABLE, L_TABLE_TOP, L_TABLE_SIDE, L_PLANKS); f(BED, L_PLANKS); f(HEADSTONE, L_STONE); f(CHARRED, L_COAL); f(ICE,L_WATER);
    }
    private static void f(int id, int top, int side, int bottom) { FACES[id] = new int[]{top, side, bottom}; }
    private static void f(int id, int all) { f(id, all, all, all); }

    /** The block colours for the minimap (RGB). */
    public static int mapColor(int id) {
        int[] c = BASE[FACES[Math.max(1, Math.min(COUNT - 1, id))][0]];
        return c[0] << 16 | c[1] << 8 | c[2];
    }

    public static boolean isTreeLog(int id) { return id == LOG || id == OAK_LOG || id == WILLOW_LOG; }
    public static boolean isLeaves(int id) { return id == LEAVES || id == OAK_LEAVES || id == WILLOW_LEAVES; }

    /** What each block means to the engine. */
    public static NativeEngine.BlockDef[] engineDefs() {
        NativeEngine.BlockDef[] d = new NativeEngine.BlockDef[COUNT];
        int solid = AstrattoNative.BLOCK_SOLID | AstrattoNative.BLOCK_OPAQUE;
        for (int i = 0; i < COUNT; i++) d[i] = new NativeEngine.BlockDef(solid);
        d[AIR] = new NativeEngine.BlockDef(0);
        d[GRASS].surface = 3; d[DIRT].surface = 3; d[SAND].surface = 3; d[STONE].surface = 1; d[COBBLE].surface = 1;
        d[GLASS] = new NativeEngine.BlockDef(AstrattoNative.BLOCK_SOLID | AstrattoNative.BLOCK_LIGHT_PASS); d[GLASS].material = AstrattoNative.MATERIAL_GLASS;
        d[LAMP].emission = 15;
        for (int leaves : new int[]{LEAVES, OAK_LEAVES, WILLOW_LEAVES, BERRY_BUSH}) {
            d[leaves] = new NativeEngine.BlockDef(AstrattoNative.BLOCK_SOLID | AstrattoNative.BLOCK_CUTOUT | AstrattoNative.BLOCK_SKY_DIM);
            d[leaves].material = AstrattoNative.MATERIAL_FOLIAGE;
        }
        d[WATER] = new NativeEngine.BlockDef(AstrattoNative.BLOCK_LIQUID | AstrattoNative.BLOCK_SKY_DIM | AstrattoNative.BLOCK_LIGHT_PASS | AstrattoNative.BLOCK_HAZARD);
        d[ICE] = new NativeEngine.BlockDef(AstrattoNative.BLOCK_SOLID | AstrattoNative.BLOCK_LIGHT_PASS);
        d[FENCE] = new NativeEngine.BlockDef(AstrattoNative.BLOCK_SOLID | AstrattoNative.BLOCK_CUTOUT);
        // a shut door's cells: not solid (the leaf itself, a rigid body on its hinge, is what stops a body), but a door to
        // routes (only those who open doors pass) and opaque to eyes and light
        d[DOOR] = new NativeEngine.BlockDef(AstrattoNative.BLOCK_OPAQUE | AstrattoNative.BLOCK_DOOR);
        d[LOCKED_DOOR] = new NativeEngine.BlockDef(solid | AstrattoNative.BLOCK_DOOR);
        // object cells: solid to bodies and rays, but light and sight pass round the object's real shape
        for (int i = 0; i < COUNT; i++) if (objectCell(i)) d[i] = new NativeEngine.BlockDef(AstrattoNative.BLOCK_SOLID | AstrattoNative.BLOCK_CUTOUT | AstrattoNative.BLOCK_LIGHT_PASS);
        d[FURNACE].emission = 9;
        d[BED] = new NativeEngine.BlockDef(AstrattoNative.BLOCK_SOLID | AstrattoNative.BLOCK_CUTOUT | AstrattoNative.BLOCK_LIGHT_PASS | AstrattoNative.BLOCK_NO_COLLIDE_AI);
        for (int i = 0; i < COUNT; i++) if (plant(i)) { d[i] = new NativeEngine.BlockDef(AstrattoNative.BLOCK_LIGHT_PASS); d[i].material = AstrattoNative.MATERIAL_FOLIAGE; }
        return d;
    }

    /** How each block is meshed. */
    public static NativeMesher.BlockDef[] renderDefs() {
        NativeMesher.BlockDef[] r = new NativeMesher.BlockDef[COUNT];
        int cube = AstrattoNative.RB_SOLID | AstrattoNative.RB_OCCLUDES;
        r[AIR] = new NativeMesher.BlockDef();
        for (int i = 1; i < COUNT; i++) r[i] = def(FACES[i], cube);
        r[GLASS] = def(FACES[GLASS], AstrattoNative.RB_SOLID | AstrattoNative.RB_TRANSLUCENT);
        r[ICE] = def(FACES[ICE],AstrattoNative.RB_SOLID|AstrattoNative.RB_TRANSLUCENT);
        r[WATER] = def(FACES[WATER], AstrattoNative.RB_LIQUID | AstrattoNative.RB_TRANSLUCENT);
        r[LAMP] = def(FACES[LAMP], cube | AstrattoNative.RB_EMISSIVE);
        for (int leaves : new int[]{LEAVES, OAK_LEAVES, WILLOW_LEAVES, BERRY_BUSH}) r[leaves] = def(FACES[leaves], AstrattoNative.RB_SOLID | AstrattoNative.RB_CUTOUT | AstrattoNative.RB_LEAF);
        r[FENCE] = def(FACES[FENCE], AstrattoNative.RB_SOLID | AstrattoNative.RB_CUTOUT);
        r[DOOR] = def(FACES[DOOR], AstrattoNative.RB_DOOR | AstrattoNative.RB_SOLID);
        r[DOOR].shape = SHAPE_DOOR;
        r[LOCKED_DOOR] = def(FACES[LOCKED_DOOR], AstrattoNative.RB_DOOR | AstrattoNative.RB_SOLID);
        r[LOCKED_DOOR].shape = SHAPE_DOOR;
        for (int i = 0; i < COUNT; i++) if (objectCell(i)) { r[i] = def(FACES[i], AstrattoNative.RB_SOLID); r[i].shape = SHAPE_HIDDEN; }   // the game draws these
        for (int i = 0; i < COUNT; i++) if (plant(i)) r[i] = def(FACES[i], AstrattoNative.RB_CROSS | AstrattoNative.RB_CUTOUT | AstrattoNative.RB_SWAY);
        return r;
    }

    private static NativeMesher.BlockDef def(int[] faces, int flags) {
        NativeMesher.BlockDef d = new NativeMesher.BlockDef();
        d.layerTop = faces[0]; d.layerSide = faces[1]; d.layerBottom = faces[2]; d.flags = flags;
        return d;
    }

    /** Render shapes: 0 is the plain cube; object cells are never meshed; door cells are leaves the game hangs and swings. */
    private static final int SHAPE_HIDDEN = 1, SHAPE_DOOR = 2;

    public static NativeMesher.ShapeDef[] renderShapes() {
        NativeMesher.ShapeDef cube = new NativeMesher.ShapeDef(), hidden = new NativeMesher.ShapeDef(), door = new NativeMesher.ShapeDef();
        hidden.invisible = true;
        hidden.boxes = new int[][]{{0, 0, 0, 16, 16, 16, 0}};
        door.orient = AstrattoNative.ORIENT_DOOR;
        door.boxes = new int[][]{{0, 0, 7, 16, 16, 9, 0}};
        return new NativeMesher.ShapeDef[]{cube, hidden, door};
    }

    // ------------------------------------------------------------------ painting the texture array
    private static final int[][] BASE = {
            {92, 146, 64}, {104, 96, 60}, {116, 88, 60}, {128, 128, 132}, {156, 72, 56}, {196, 224, 236}, {255, 236, 190}, {170, 128, 80},
            {96, 70, 44}, {176, 140, 92}, {60, 120, 48}, {110, 84, 56}, {74, 110, 44}, {104, 100, 66}, {120, 150, 72},
            {120, 116, 112}, {120, 120, 118}, {112, 104, 100}, {70, 70, 74}, {96, 96, 98}, {60, 110, 170}, {214, 196, 140}, {122, 120, 116}, {150, 60, 44},
            {130, 92, 52}, {96, 74, 50}, {90, 84, 80}, {58, 60, 66}, {70, 66, 64}, {160, 120, 76}, {150, 112, 70}, {214, 190, 120},
            {130, 100, 60}, {70, 140, 60}, {150, 160, 90}, {54, 110, 50}, {150, 110, 60}, {120, 80, 50}, {190, 50, 46}, {50, 90, 190}, {230, 196, 50},
            {66, 70, 80}, {226, 220, 204}, {92, 66, 42}, {124, 96, 64}, {60, 170, 80}, {140, 100, 62}};

    /** The texture array: LAYERS layers of TEX x TEX RGBA (native memory; the caller frees it). */
    public static ByteBuffer atlasPixels() {
        ByteBuffer px = MemoryUtil.memAlloc(TEX * TEX * 4 * LAYERS);
        for (int l = 0; l < LAYERS; l++) {
            Painter p = new Painter(l);
            for (int y = 0; y < TEX; y++) for (int x = 0; x < TEX; x++) {
                int[] c = p.paint(x, y);
                px.put((byte) c[0]).put((byte) c[1]).put((byte) c[2]).put((byte) c[3]);
            }
        }
        px.flip();
        return px;
    }

    /** Paints one 64 x 64 layer: tileable noise, then the material's own structure (boards, courses, stones, rings...). */
    private static final class Painter {
        final int l;
        final Random rnd;
        final float[][] lattices = new float[6][];
        final int[] periods = {4, 8, 16, 32, 64, 2};
        float[][] cells;   // Voronoi sites for stones

        Painter(int l) {
            this.l = l;
            this.rnd = new Random(7 + l * 101L);
            for (int i = 0; i < lattices.length; i++) { int p = periods[i]; lattices[i] = new float[p * p]; for (int k = 0; k < p * p; k++) lattices[i][k] = rnd.nextFloat(); }
            if (l == L_COBBLE || l == L_STONE || l == L_SPENT || l == L_COPPER || l == L_TIN || l == L_IRON || l == L_COAL) {
                int n = l == L_COBBLE ? 11 : 7;
                cells = new float[n][];
                for (int i = 0; i < n; i++) cells[i] = new float[]{rnd.nextFloat() * TEX, rnd.nextFloat() * TEX, 0.85f + rnd.nextFloat() * 0.25f};
            }
        }

        /** Smooth tileable value noise at one of the lattice periods (0..1). */
        float noise(int oct, float x, float y) {
            int p = periods[oct];
            float fx = x * p / TEX, fy = y * p / TEX;
            int x0 = (int) Math.floor(fx), y0 = (int) Math.floor(fy);
            float tx = fx - x0, ty = fy - y0;
            tx = tx * tx * (3 - 2 * tx); ty = ty * ty * (3 - 2 * ty);
            float[] g = lattices[oct];
            float a = g[Math.floorMod(y0, p) * p + Math.floorMod(x0, p)], b = g[Math.floorMod(y0, p) * p + Math.floorMod(x0 + 1, p)];
            float c = g[Math.floorMod(y0 + 1, p) * p + Math.floorMod(x0, p)], d = g[Math.floorMod(y0 + 1, p) * p + Math.floorMod(x0 + 1, p)];
            return (a + (b - a) * tx) * (1 - ty) + (c + (d - c) * tx) * ty;
        }

        /** Fractal noise from coarse to fine (0..1). */
        float fbm(float x, float y) { return noise(0, x, y) * 0.35f + noise(1, x, y) * 0.3f + noise(2, x, y) * 0.2f + noise(3, x, y) * 0.15f; }

        /** Nearest and second-nearest Voronoi site distances (wrapping), and the nearest site's index. */
        float[] voronoi(float x, float y) {
            float d1 = 1e9f, d2 = 1e9f;
            int best = 0;
            for (int i = 0; i < cells.length; i++) {
                float dx = Math.abs(x - cells[i][0]), dy = Math.abs(y - cells[i][1]);
                dx = Math.min(dx, TEX - dx); dy = Math.min(dy, TEX - dy);
                float d = (float) Math.sqrt(dx * dx + dy * dy);
                if (d < d1) { d2 = d1; d1 = d; best = i; } else if (d < d2) d2 = d;
            }
            return new float[]{d1, d2, best};
        }

        int[] paint(int x, int y) {
            int[] c = BASE[l];
            float r = c[0], g = c[1], b = c[2];
            int a = 255;
            float n = 0.88f + fbm(x, y) * 0.24f;
            switch (l) {
                case L_GRASS -> {
                    n = 0.8f + fbm(x, y) * 0.3f;
                    float blade = noise(4, x, y * 0.5f);
                    if (blade > 0.72f) n *= 1.18f; else if (blade < 0.22f) n *= 0.84f;
                    if (rnd.nextFloat() < 0.012f) { r = 220; g = 210; b = 120; n = 1; }       // seed heads
                }
                case L_GRASS_SIDE -> {
                    float edge = 14 + noise(3, x, 0) * 6 + ((x * 7) % 5 == 0 ? 5 : 0);
                    if (y > edge) { r = BASE[L_DIRT][0]; g = BASE[L_DIRT][1]; b = BASE[L_DIRT][2]; n = pebbles(x, y, n); }
                    else { r = BASE[L_GRASS][0]; g = BASE[L_GRASS][1]; b = BASE[L_GRASS][2]; n = 0.8f + fbm(x, y) * 0.3f; if (y > edge - 2) n *= 0.82f; }
                }
                case L_DIRT, L_BARE -> {
                    n = pebbles(x, y, 0.84f + fbm(x, y) * 0.3f);
                    if (l == L_BARE) { int row = (y + 4) % 16; if (row < 3) n *= 0.7f; else if (row < 6) n *= 1.12f; }   // furrows
                }
                case L_SAND -> { n = 0.9f + fbm(x, y) * 0.16f; if (rnd.nextFloat() < 0.05f) n *= rnd.nextBoolean() ? 0.82f : 1.12f; }
                case L_STONE, L_SPENT -> {
                    float[] v = voronoi(x, y);
                    n = (0.84f + fbm(x, y) * 0.24f) * cells[(int) v[2]][2];
                    if (v[1] - v[0] < 1.6f) n *= 0.68f;                                       // cracks between slabs
                    else if (v[1] - v[0] < 3f) n *= 0.9f;
                    if (l == L_SPENT && noise(4, x, y) > 0.7f) n *= 0.75f;
                }
                case L_COPPER, L_TIN, L_IRON, L_COAL -> {
                    float[] v = voronoi(x, y);
                    r = 116; g = 112; b = 108;
                    n = (0.84f + fbm(x, y) * 0.24f) * cells[(int) v[2]][2];
                    if (v[1] - v[0] < 1.6f) n *= 0.7f;
                    int[] ore = l == L_COPPER ? new int[]{206, 116, 62} : l == L_TIN ? new int[]{224, 224, 218} : l == L_IRON ? new int[]{156, 92, 66} : new int[]{24, 24, 26};
                    float blob = noise(3, x, y) * 0.6f + noise(4, x, y) * 0.4f;
                    if (blob > 0.66f) { r = ore[0]; g = ore[1]; b = ore[2]; n = blob > 0.74f ? 1.12f : 0.92f; }
                }
                case L_BRICK -> {
                    int row = y / 8, bx = (x + (row % 2) * 8) % 16, by = y % 8;
                    if (by < 2 || bx < 2) { r = 196; g = 190; b = 178; n = 0.9f + fbm(x, y) * 0.15f; }
                    else {
                        Random br = new Random(row * 31 + (x + (row % 2) * 8) / 16);
                        n = (0.82f + br.nextFloat() * 0.24f) * (0.92f + fbm(x, y) * 0.14f);
                        if (by == 2 || bx == 2) n *= 1.08f;                                     // the lit top-left edge
                        if (by == 7 || bx == 15) n *= 0.86f;
                    }
                }
                case L_COBBLE -> {
                    float[] v = voronoi(x, y);
                    n = (0.82f + fbm(x, y) * 0.22f) * cells[(int) v[2]][2];
                    if (v[1] - v[0] < 2.4f) { n = 0.55f; }
                    else if (v[0] < 3) n *= 1.06f;
                }
                case L_GLASS -> {
                    boolean frame = x < 3 || y < 3 || x > TEX - 4 || y > TEX - 4 || Math.abs(x - TEX / 2) < 1 || Math.abs(y - TEX / 2) < 1;
                    if (frame) { r = 120; g = 96; b = 64; n = 0.9f + fbm(x, y) * 0.1f; }
                    else { a = 60; n = 1; if ((x + y) % 23 < 2 && (x + y) % 46 < 23) { a = 110; n = 1.15f; } }
                }
                case L_LAMP -> {
                    boolean frame = x < 4 || y < 4 || x > TEX - 5 || y > TEX - 5 || Math.abs(x - TEX / 2) < 2 || Math.abs(y - TEX / 2) < 2;
                    if (frame) { r = 60; g = 52; b = 44; n = 1; } else { float d = (float) Math.hypot((x % 32) - 16, (y % 32) - 16) / 16f; n = 1.05f - d * 0.15f; }
                }
                case L_PLANKS, L_TABLE_SIDE -> {
                    int board = y / 16, by = y % 16;
                    Random br = new Random(board * 977 + l);
                    float tint = 0.88f + br.nextFloat() * 0.2f;
                    float grain = noise(5, x * 0.25f + board * 13, y * 4f) * 0.6f + noise(4, x * 0.5f, y * 2f) * 0.4f;
                    n = tint * (0.86f + grain * 0.24f);
                    if (by == 0) n *= 0.6f; else if (by == 15) n *= 0.78f; else if (by == 1) n *= 1.08f;
                    int seam = (board * 23) % 64;
                    if (Math.abs(x - seam) < 1) n *= 0.65f;                                       // butt joint
                    int fromSeam = Math.floorMod(x - seam, TEX);
                    if ((fromSeam == 4 || fromSeam == TEX - 4) && (by == 4 || by == 11)) { r = 60; g = 56; b = 52; n = 1; }   // nails either side of the joint
                }
                case L_TABLE_TOP -> {
                    float grain = noise(5, x * 0.25f, y * 4f) * 0.6f + noise(4, x * 0.5f, y * 2f) * 0.4f;
                    n = 0.88f + grain * 0.22f;
                    if (x < 3 || y < 3 || x > TEX - 4 || y > TEX - 4) n *= 0.72f;
                }
                case L_BARK, L_OAK_BARK, L_WILLOW_BARK -> {
                    float ridge = noise(5, x * 2f, y * 0.25f) * 0.6f + noise(3, x, y * 0.5f) * 0.4f;
                    n = 0.7f + ridge * 0.45f;
                    if (ridge < 0.32f) n *= 0.7f;                                                 // fissures
                    if (l == L_WILLOW_BARK && (y + x / 3) % 19 < 2) n *= 0.8f;
                }
                case L_RINGS -> {
                    float d = (float) Math.hypot(x - 31.5, y - 31.5) + noise(2, x, y) * 3;
                    n = 0.9f + 0.1f * (float) Math.sin(d * 1.3f);
                    if (((int) d) % 6 == 0) n *= 0.82f;
                    if (d > 28) { r = 96; g = 70; b = 44; n = 0.8f + fbm(x, y) * 0.3f; }
                    float crack = Math.abs((float) Math.atan2(y - 31.5, x - 31.5) - 0.6f);
                    if (crack < 0.04f && d < 26 && d > 4) n *= 0.6f;
                }
                case L_LEAVES, L_OAK_LEAVES, L_WILLOW_LEAVES -> {
                    float cl = noise(3, x, y) * 0.5f + noise(4, x, y) * 0.5f;
                    if (cl < 0.33f) a = 0;
                    n = 0.62f + cl * 0.6f;
                    if (noise(5, x, y) > 0.8f) n *= 1.15f;
                }
                case L_BERRY -> {
                    float cl = noise(3, x, y) * 0.5f + noise(4, x, y) * 0.5f;
                    if (cl < 0.3f) a = 0;
                    n = 0.62f + cl * 0.55f;
                    float bx = (x % 13) - 6, by = (y % 11) - 5;
                    if (bx * bx + by * by < 5 && ((x / 13 + y / 11) % 3 == 0)) { r = 200; g = 28; b = 40; n = bx + by < -1 ? 1.25f : 1; a = 255; }
                }
                case L_HERB, L_FLAX -> {   // crossed plants: stems and leaves on nothing
                    a = 0;
                    int stems = l == L_HERB ? 6 : 9;
                    for (int s = 0; s < stems; s++) {
                        float sx = 6 + s * (52f / (stems - 1)) + (float) Math.sin(s * 3.1 + y * 0.08) * 3;
                        float top = 10 + ((s * 37) % 17);
                        if (y >= top && Math.abs(x - sx) < (l == L_HERB ? 1.5f : 1f)) { a = 255; n = 0.8f + 0.25f * (y - top) / (TEX - top); }
                        if (l == L_HERB && y >= top && y < top + 30 && ((y - (int) top) % 9) < 5 && Math.abs(x - sx - ((y / 9) % 2 == 0 ? 3 : -3)) < 3) { a = 255; n = 1.05f; }
                        if (l == L_FLAX && Math.abs(y - top) < 3 && Math.abs(x - sx) < 3) { r = 110; g = 130; b = 220; a = 255; n = 1; }
                    }
                }
                case L_WATER -> { a = 170; n = 0.88f + 0.12f * (float) Math.sin((x + y * 0.5f) * 0.3f + noise(2, x, y) * 6); }
                case L_ROOF -> {
                    int row = y / 10, ry = y % 10, sx = (x + (row % 2) * 8) % 16;
                    Random br = new Random(row * 53 + (x + (row % 2) * 8) / 16);
                    n = (0.84f + br.nextFloat() * 0.2f) * (0.9f + fbm(x, y) * 0.15f);
                    if (ry == 9 || sx == 0) n *= 0.6f;
                    else if (ry > 6) n *= 0.85f;
                }
                case L_DOOR, L_LOCKED -> {
                    int bx = x % 16;
                    n = (0.86f + noise(5, x * 4f, y * 0.25f) * 0.2f) * (bx == 0 ? 0.65f : 1);
                    boolean band = Math.abs(y - 12) < 3 || Math.abs(y - 52) < 3;
                    if (l == L_LOCKED && band) { r = 70; g = 70; b = 76; n = 1; }
                    if (x > 48 && x < 54 && y > 28 && y < 36) { r = l == L_LOCKED ? 200 : 40; g = l == L_LOCKED ? 170 : 40; b = l == L_LOCKED ? 60 : 40; n = 1; }
                }
                case L_FURNACE -> {
                    int row = y / 8, bx = (x + (row % 2) * 8) % 16;
                    if (y % 8 < 1 || bx < 1) n *= 0.6f;
                    if (x > 16 && x < 48 && y > 28 && y < 58) { r = 255; g = 140 + (int) (noise(4, x, y) * 60); b = 40; n = 1; }
                }
                case L_METAL -> { n = 0.9f + noise(5, x * 0.2f, y * 2) * 0.12f; if (x < 2 || y < 2) n *= 0.7f; if (y < 6) n *= 1.25f; }
                case L_RANGE -> { float d1 = (float) Math.hypot(x - 18, y - 18), d2 = (float) Math.hypot(x - 44, y - 44); if (Math.abs(d1 - 10) < 2 || Math.abs(d2 - 10) < 2) { r = 30; g = 30; b = 30; n = 1; } }
                case L_SAW -> { if (Math.abs(x - y) < 3) { r = 190; g = 190; b = 196; n = 1; } }
                case L_TARGET -> {
                    float d = (float) Math.hypot(x - 31.5, y - 31.5);
                    int ring = (int) (d / 6.4f);
                    if (ring == 0) { r = 230; g = 200; b = 40; } else if (ring == 1 || ring == 3) { r = 200; g = 40; b = 40; } else if (ring == 2 || ring == 4) { r = 240; g = 236; b = 226; }
                    n = 0.92f + fbm(x, y) * 0.12f;
                }
                case L_FENCE -> {
                    boolean post = x < 8 || x > 55, rail = (y > 12 && y < 22) || (y > 40 && y < 50);
                    if (!post && !rail) a = 0;
                    n = 0.82f + noise(5, x * (post ? 4 : 0.25f), y * (post ? 0.25f : 4)) * 0.26f;
                    if (post && (x == 0 || x == 7 || x == 56 || x == 63)) n *= 0.75f;
                    if (rail && !post && (y == 13 || y == 41)) n *= 1.12f;
                }
                case L_CRATE -> {
                    boolean frame = x < 6 || y < 6 || x > TEX - 7 || y > TEX - 7 || Math.abs(x - y) < 4;
                    n = (0.86f + noise(5, x * 0.25f, y * 4f) * 0.2f) * (frame ? 0.78f : 1f);
                    if (!frame && y % 16 == 0) n *= 0.7f;
                }
                case L_BOOKS -> {
                    int shelf = y / 21, sy = y % 21;
                    if (sy < 3) { r = 90; g = 60; b = 36; n = 0.9f + fbm(x, y) * 0.15f; }
                    else {
                        Random br = new Random(shelf * 97 + x / 5);
                        int[][] cols = {{150, 40, 40}, {40, 80, 140}, {50, 120, 60}, {160, 130, 50}, {90, 50, 110}, {120, 90, 60}};
                        int[] k = cols[br.nextInt(cols.length)];
                        r = k[0]; g = k[1]; b = k[2];
                        int height = 12 + br.nextInt(6);
                        if (21 - sy > height) { r = 50; g = 36; b = 24; }
                        n = x % 5 == 0 ? 0.7f : 0.95f;
                        if (sy == 21 - height + 3) { r = 220; g = 200; b = 120; n = 1; }
                    }
                }
                case L_RED, L_BLUE, L_YELLOW, L_GREEN -> { n = 0.9f + fbm(x, y) * 0.12f; if ((x + y) % 32 < 4) n *= 0.8f; if (x < 2 || y < 2 || x > TEX - 3 || y > TEX - 3) n *= 0.7f; }
                case L_DEBUG -> {
                    boolean chk = ((x / 32) + (y / 32)) % 2 == 0;
                    n = (chk ? 1f : 0.8f) * (0.96f + fbm(x, y) * 0.06f);
                    if (x % 32 == 0 || y % 32 == 0) { r = 230; g = 170; b = 40; n = 1; }
                    else if (x % 8 == 0 || y % 8 == 0) n *= 0.9f;
                }
                case L_PLASTER -> { n = 0.92f + fbm(x, y) * 0.1f; if (noise(4, x, y) > 0.82f && noise(5, x, y) > 0.6f) n *= 0.88f; }
                case L_BEAM -> {
                    n = 0.8f + noise(5, x * 4f, y * 0.25f) * 0.3f;
                    if (x < 2 || x > TEX - 3) n *= 0.75f;
                    if (noise(3, x * 2f, y * 0.5f) < 0.18f) n *= 0.7f;                            // checks in the timber
                }
                default -> { }
            }
            return new int[]{clamp(r * n), clamp(g * n), clamp(b * n), a};
        }

        /** Earth with small stones in it. */
        float pebbles(int x, int y, float n) {
            float p = noise(4, x, y);
            if (p > 0.8f) return n * 1.2f;
            if (p < 0.12f) return n * 0.78f;
            return n;
        }
    }

    /** A tileable noise texture for the world shader's wear (256 x 256 RGBA; native memory, the caller frees it). */
    public static ByteBuffer noisePixels() {
        ByteBuffer px = MemoryUtil.memAlloc(256 * 256 * 4);
        Random rnd = new Random(11);
        for (int i = 0; i < 256 * 256 * 4; i++) px.put((byte) (96 + rnd.nextInt(64)));
        px.flip();
        return px;
    }

    /** Per block id: its albedo and emitted light for the probes (256 x 8 floats). */
    public static float[] palette() {
        float[] p = new float[256 * 8];
        for (int id = 1; id < COUNT; id++) {
            int[] c = BASE[FACES[id][1]];
            for (int k = 0; k < 3; k++) p[id * 8 + k] = (float) Math.pow(c[k] / 255.0, 2.2);
            p[id * 8 + 3] = 1;
        }
        p[LAMP * 8 + 4] = 6f; p[LAMP * 8 + 5] = 5.2f; p[LAMP * 8 + 6] = 3.6f;
        p[FURNACE * 8 + 4] = 3f; p[FURNACE * 8 + 5] = 1.4f; p[FURNACE * 8 + 6] = 0.4f;
        return p;
    }

    /**
     * Per texture layer: roughness, metalness, emission, 255. Each surface answers light as its material does: water and
     * glass mirror-smooth, metal metallic (its reflection tinted by its own colour), worn and glazed stone and planed wood
     * with a soft sheen, earth, plaster and cloth-dull leaves and grass rough.
     */
    public static byte[] layerMaterials() {
        byte[] m = new byte[LAYERS * 4];
        for (int l = 0; l < LAYERS; l++) { m[l * 4] = (byte) rough(0.86f); m[l * 4 + 3] = (byte) 255; }
        Object[][] table = {
                {L_GRASS, 0.92f, 0f}, {L_GRASS_SIDE, 0.92f, 0f}, {L_DIRT, 0.96f, 0f}, {L_SAND, 0.94f, 0f}, {L_BARE, 0.95f, 0f},
                {L_STONE, 0.78f, 0f}, {L_COBBLE, 0.70f, 0f}, {L_BRICK, 0.84f, 0f}, {L_PLASTER, 0.90f, 0f}, {L_ROOF, 0.55f, 0f},     // roof tiles are glazed
                {L_PLANKS, 0.62f, 0f}, {L_TABLE_TOP, 0.45f, 0f}, {L_TABLE_SIDE, 0.55f, 0f}, {L_DOOR, 0.6f, 0f}, {L_LOCKED, 0.5f, 0.35f},
                {L_BEAM, 0.7f, 0f}, {L_CRATE, 0.75f, 0f}, {L_FENCE, 0.78f, 0f}, {L_BOOKS, 0.7f, 0f}, {L_SAW, 0.5f, 0.4f},
                {L_BARK, 0.9f, 0f}, {L_OAK_BARK, 0.9f, 0f}, {L_WILLOW_BARK, 0.9f, 0f}, {L_RINGS, 0.7f, 0f},
                {L_LEAVES, 0.62f, 0f}, {L_OAK_LEAVES, 0.62f, 0f}, {L_WILLOW_LEAVES, 0.6f, 0f}, {L_HERB, 0.7f, 0f}, {L_FLAX, 0.75f, 0f}, {L_BERRY, 0.6f, 0f},
                {L_COPPER, 0.5f, 0.45f}, {L_TIN, 0.5f, 0.45f}, {L_IRON, 0.55f, 0.4f}, {L_COAL, 0.6f, 0f}, {L_SPENT, 0.82f, 0f},  // ore glints in the rock
                {L_METAL, 0.32f, 1f}, {L_FURNACE, 0.8f, 0f}, {L_RANGE, 0.5f, 0.7f}, {L_TARGET, 0.85f, 0f},
                {L_GLASS, 0.06f, 0f}, {L_WATER, 0.04f, 0f}, {L_DEBUG, 0.6f, 0f}};
        for (Object[] t : table) { int l = (Integer) t[0]; m[l * 4] = (byte) rough((Float) t[1]); m[l * 4 + 1] = (byte) clamp((Float) t[2] * 255); }
        m[L_LAMP * 4 + 2] = (byte) 255;
        m[L_FURNACE * 4 + 2] = (byte) 120;
        return m;
    }

    private static int rough(float r) { return clamp(r * 255); }

    private static int clamp(float v) { return Math.max(0, Math.min(255, Math.round(v))); }
}
