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
            MARK_YELLOW = 40, DEBUG_FLOOR = 41, PLASTER = 42, BEAM = 43, BARE_PATCH = 44, MARK_GREEN = 45, TABLE = 46, BED = 47, HEADSTONE = 48, CHARRED = 49, ICE = 50,
            // the building-block set (gamefixes): whole cubes, each with its own 64 x 64 texture and its material
            SPRUCE_LOG = 51, JUNGLE_LOG = 52, BIRCH_LOG = 53, GRANITE = 54, STONE_BRICKS = 55, MARBLE = 56, CLAY = 57, MUD = 58,
            SANDSTONE = 59, RED_SANDSTONE = 60, WHITE_GLASS = 61, LIGHT_BLUE_GLASS = 62, WHITE_CARPET = 63, BLACK_CARPET = 64,
            BROWN_CARPET = 65, RED_CARPET = 66, LIMESTONE_BRICKS = 67, IRON_BLOCK = 68, GOLD_BLOCK = 69, COPPER_BLOCK = 70,
            COAL_BLOCK = 71, HAY_BALE = 72,
            // the cell a lit fire stands in: nothing to see or bump into, but a hazard routes go round (people do not
            // walk through a campfire)
            EMBERS = 73, COUNT = 74;
    /** Cobblestone is the cobble block (one block, redesigned), as Dirt, Sand and Glass are. */
    public static final int COBBLESTONE = COBBLE;

    /**
     * What a block is made of (gamefixes: the material engine). One answer drives everything that cares: the footstep,
     * whether and how it burns, whether it carries a charge, freezes or shatters, and how it takes the light.
     */
    public enum Material { NONE, EARTH, SAND, STONE, WOOD, FOLIAGE, CLOTH, STRAW, METAL, COAL, GLASS, WATER, ICE }

    public static Material material(int id) {
        return switch (id) {
            case AIR, EMBERS -> Material.NONE;
            case GRASS, DIRT, BARE_PATCH, CLAY, MUD -> Material.EARTH;
            case SAND -> Material.SAND;
            case PLANKS, LOG, OAK_LOG, WILLOW_LOG, SPRUCE_LOG, JUNGLE_LOG, BIRCH_LOG, BEAM, ROOF, FENCE, STUMP, CRATE, BOOKCASE, TABLE,
                 CRAFT_TABLE, SAWBENCH, TARGET, DOOR, LOCKED_DOOR, CHARRED -> Material.WOOD;
            case LEAVES, OAK_LEAVES, WILLOW_LEAVES, HERB_PATCH, FLAX_PATCH, BERRY_BUSH -> Material.FOLIAGE;
            case WHITE_CARPET, BLACK_CARPET, BROWN_CARPET, RED_CARPET, BED -> Material.CLOTH;
            case HAY_BALE -> Material.STRAW;
            case IRON_BLOCK, GOLD_BLOCK, COPPER_BLOCK, ANVIL, LAMP -> Material.METAL;
            case COAL_BLOCK -> Material.COAL;
            case GLASS, WHITE_GLASS, LIGHT_BLUE_GLASS -> Material.GLASS;
            case WATER -> Material.WATER;
            case ICE -> Material.ICE;
            default -> Material.STONE;
        };
    }
    /** Glass of any tint: it lets light through and shatters. */
    public static boolean glass(int id) { return material(id) == Material.GLASS; }

    /** The cells of objects drawn by the game rather than meshed. */
    public static boolean objectCell(int id) {
        return id == FURNACE || id == ANVIL || id == RANGE || id == CRAFT_TABLE || id == SAWBENCH || id == TARGET || id == CRATE || id == BOOKCASE || id == TABLE || id == BED || id == HEADSTONE;
    }
    /** Full structural cells form the traversal graph; glass, gates, plants and thin props never do. */
    public static boolean traversable(int id) {
        return switch (id) { case GRASS, DIRT, STONE, BRICK, PLANKS, LOG, OAK_LOG, WILLOW_LOG, SAND, COBBLE, ROOF, CRATE, PLASTER, BEAM, CHARRED,
                SPRUCE_LOG, JUNGLE_LOG, BIRCH_LOG, GRANITE, STONE_BRICKS, MARBLE, CLAY, MUD, SANDSTONE, RED_SANDSTONE, WHITE_CARPET, BLACK_CARPET,
                BROWN_CARPET, RED_CARPET, LIMESTONE_BRICKS, IRON_BLOCK, GOLD_BLOCK, COPPER_BLOCK, COAL_BLOCK, HAY_BALE -> true; default -> false; };
    }
    /** Plants drawn as crossed planes that bodies walk through. */
    public static boolean plant(int id) { return id == HERB_PATCH || id == FLAX_PATCH; }

    // texture layers
    private static final int L_GRASS = 0, L_GRASS_SIDE = 1, L_DIRT = 2, L_STONE = 3, L_BRICK = 4, L_GLASS = 5, L_LAMP = 6, L_PLANKS = 7,
            L_BARK = 8, L_RINGS = 9, L_LEAVES = 10, L_OAK_BARK = 11, L_OAK_LEAVES = 12, L_WILLOW_BARK = 13, L_WILLOW_LEAVES = 14,
            L_COPPER = 15, L_TIN = 16, L_IRON = 17, L_COAL = 18, L_SPENT = 19, L_WATER = 20, L_SAND = 21, L_COBBLE = 22, L_ROOF = 23,
            L_DOOR = 24, L_LOCKED = 25, L_FURNACE = 26, L_METAL = 27, L_RANGE = 28, L_TABLE_TOP = 29, L_SAW = 30, L_TARGET = 31,
            L_FENCE = 32, L_HERB = 33, L_FLAX = 34, L_BERRY = 35, L_CRATE = 36, L_BOOKS = 37, L_RED = 38, L_BLUE = 39, L_YELLOW = 40,
            L_DEBUG = 41, L_PLASTER = 42, L_BEAM = 43, L_BARE = 44, L_GREEN = 45, L_TABLE_SIDE = 46,
            L_SPRUCE_BARK = 47, L_SPRUCE_TOP = 48, L_JUNGLE_BARK = 49, L_JUNGLE_TOP = 50, L_BIRCH_BARK = 51, L_BIRCH_TOP = 52,
            L_GRANITE = 53, L_STONE_BRICKS = 54, L_MARBLE = 55, L_CLAY = 56, L_MUD = 57, L_SANDSTONE_TOP = 58, L_SANDSTONE = 59,
            L_RED_SANDSTONE_TOP = 60, L_RED_SANDSTONE = 61, L_WHITE_GLASS = 62, L_BLUE_GLASS = 63, L_WHITE_CARPET = 64,
            L_BLACK_CARPET = 65, L_BROWN_CARPET = 66, L_RED_CARPET = 67, L_LIMESTONE = 68, L_IRON_BLOCK = 69, L_GOLD_BLOCK = 70,
            L_COPPER_BLOCK = 71, L_COAL_BLOCK = 72, L_HAY_TOP = 73, L_HAY_SIDE = 74;
    /** Texture layers, each TEX x TEX texels: 64 to the metre. */
    public static final int LAYERS = 75, TEX = 64;

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
        f(SPRUCE_LOG, L_SPRUCE_TOP, L_SPRUCE_BARK, L_SPRUCE_TOP); f(JUNGLE_LOG, L_JUNGLE_TOP, L_JUNGLE_BARK, L_JUNGLE_TOP); f(BIRCH_LOG, L_BIRCH_TOP, L_BIRCH_BARK, L_BIRCH_TOP);
        f(GRANITE, L_GRANITE); f(STONE_BRICKS, L_STONE_BRICKS); f(MARBLE, L_MARBLE); f(CLAY, L_CLAY); f(MUD, L_MUD);
        f(SANDSTONE, L_SANDSTONE_TOP, L_SANDSTONE, L_SANDSTONE_TOP); f(RED_SANDSTONE, L_RED_SANDSTONE_TOP, L_RED_SANDSTONE, L_RED_SANDSTONE_TOP);
        f(WHITE_GLASS, L_WHITE_GLASS); f(LIGHT_BLUE_GLASS, L_BLUE_GLASS);
        f(WHITE_CARPET, L_WHITE_CARPET); f(BLACK_CARPET, L_BLACK_CARPET); f(BROWN_CARPET, L_BROWN_CARPET); f(RED_CARPET, L_RED_CARPET);
        f(LIMESTONE_BRICKS, L_LIMESTONE); f(IRON_BLOCK, L_IRON_BLOCK); f(GOLD_BLOCK, L_GOLD_BLOCK); f(COPPER_BLOCK, L_COPPER_BLOCK); f(COAL_BLOCK, L_COAL_BLOCK);
        f(HAY_BALE, L_HAY_TOP, L_HAY_SIDE, L_HAY_TOP);
        f(EMBERS, L_COAL);
    }
    private static void f(int id, int top, int side, int bottom) { FACES[id] = new int[]{top, side, bottom}; }
    private static void f(int id, int all) { f(id, all, all, all); }

    /** The block colours for the minimap (RGB). */
    public static int mapColor(int id) {
        int[] c = BASE[FACES[Math.max(1, Math.min(COUNT - 1, id))][0]];
        return c[0] << 16 | c[1] << 8 | c[2];
    }

    public static boolean isTreeLog(int id) { return id == LOG || id == OAK_LOG || id == WILLOW_LOG || id == SPRUCE_LOG || id == JUNGLE_LOG || id == BIRCH_LOG; }
    public static boolean isLeaves(int id) { return id == LEAVES || id == OAK_LEAVES || id == WILLOW_LEAVES; }

    /** What each block means to the engine. */
    public static NativeEngine.BlockDef[] engineDefs() {
        NativeEngine.BlockDef[] d = new NativeEngine.BlockDef[COUNT];
        int solid = AstrattoNative.BLOCK_SOLID | AstrattoNative.BLOCK_OPAQUE;
        for (int i = 0; i < COUNT; i++) d[i] = new NativeEngine.BlockDef(solid);
        d[AIR] = new NativeEngine.BlockDef(0);
        for (int i = 1; i < COUNT; i++) switch (material(i)) {
            case EARTH, SAND, STRAW -> d[i].surface = 3;
            case STONE, METAL, COAL -> d[i].surface = 1;
            default -> { }
        }
        for (int i = 1; i < COUNT; i++) if (glass(i)) { d[i] = new NativeEngine.BlockDef(AstrattoNative.BLOCK_SOLID | AstrattoNative.BLOCK_LIGHT_PASS); d[i].material = AstrattoNative.MATERIAL_GLASS; }
        d[LAMP].emission = 15;
        for (int leaves : new int[]{LEAVES, OAK_LEAVES, WILLOW_LEAVES, BERRY_BUSH}) {
            d[leaves] = new NativeEngine.BlockDef(AstrattoNative.BLOCK_SOLID | AstrattoNative.BLOCK_CUTOUT | AstrattoNative.BLOCK_SKY_DIM);
            d[leaves].material = AstrattoNative.MATERIAL_FOLIAGE;
        }
        d[EMBERS] = new NativeEngine.BlockDef(AstrattoNative.BLOCK_HAZARD | AstrattoNative.BLOCK_LIGHT_PASS);
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
        for (int i = 1; i < COUNT; i++) if (glass(i)) r[i] = def(FACES[i], AstrattoNative.RB_SOLID | AstrattoNative.RB_TRANSLUCENT);
        r[ICE] = def(FACES[ICE],AstrattoNative.RB_SOLID|AstrattoNative.RB_TRANSLUCENT);
        r[WATER] = def(FACES[WATER], AstrattoNative.RB_LIQUID | AstrattoNative.RB_TRANSLUCENT);
        r[LAMP] = def(FACES[LAMP], cube | AstrattoNative.RB_EMISSIVE);
        for (int leaves : new int[]{LEAVES, OAK_LEAVES, WILLOW_LEAVES, BERRY_BUSH}) r[leaves] = def(FACES[leaves], AstrattoNative.RB_SOLID | AstrattoNative.RB_CUTOUT | AstrattoNative.RB_LEAF);
        r[FENCE] = def(FACES[FENCE], AstrattoNative.RB_SOLID | AstrattoNative.RB_CUTOUT);
        r[DOOR] = def(FACES[DOOR], AstrattoNative.RB_DOOR | AstrattoNative.RB_SOLID);
        r[DOOR].shape = SHAPE_HIDDEN;
        r[LOCKED_DOOR] = def(FACES[LOCKED_DOOR], AstrattoNative.RB_DOOR | AstrattoNative.RB_SOLID);
        r[LOCKED_DOOR].shape = SHAPE_HIDDEN;
        r[EMBERS] = def(FACES[EMBERS], 0); r[EMBERS].shape = SHAPE_HIDDEN;
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
    private static final int SHAPE_HIDDEN = 1;

    public static NativeMesher.ShapeDef[] renderShapes() {
        NativeMesher.ShapeDef cube = new NativeMesher.ShapeDef(), hidden = new NativeMesher.ShapeDef();
        hidden.invisible = true;
        hidden.boxes = new int[][]{{0, 0, 0, 16, 16, 16, 0}};
        return new NativeMesher.ShapeDef[]{cube, hidden};
    }

    // ------------------------------------------------------------------ painting the texture array
    private static final int[][] BASE = {
            {92, 146, 64}, {104, 96, 60}, {116, 88, 60}, {128, 128, 132}, {156, 72, 56}, {196, 224, 236}, {255, 236, 190}, {170, 128, 80},
            {96, 70, 44}, {176, 140, 92}, {60, 120, 48}, {110, 84, 56}, {74, 110, 44}, {104, 100, 66}, {120, 150, 72},
            {120, 116, 112}, {120, 120, 118}, {112, 104, 100}, {70, 70, 74}, {96, 96, 98}, {60, 110, 170}, {214, 196, 140}, {122, 120, 116}, {150, 60, 44},
            {130, 92, 52}, {96, 74, 50}, {90, 84, 80}, {58, 60, 66}, {70, 66, 64}, {160, 120, 76}, {150, 112, 70}, {214, 190, 120},
            {130, 100, 60}, {70, 140, 60}, {150, 160, 90}, {54, 110, 50}, {150, 110, 60}, {120, 80, 50}, {190, 50, 46}, {50, 90, 190}, {230, 196, 50},
            {66, 70, 80}, {226, 220, 204}, {92, 66, 42}, {124, 96, 64}, {60, 170, 80}, {140, 100, 62},
            // spruce, jungle, birch (bark, end grain)
            {74, 54, 40}, {168, 124, 78}, {118, 90, 56}, {176, 120, 74}, {222, 218, 206}, {214, 190, 142},
            // granite, stone bricks, marble, clay, mud, sandstone (top, side), red sandstone (top, side)
            {152, 108, 92}, {126, 126, 124}, {232, 230, 224}, {158, 150, 146}, {84, 64, 46}, {218, 204, 154}, {212, 196, 144}, {186, 104, 54}, {178, 98, 50},
            // white and light blue stained glass; white, black, brown and red carpet
            {238, 238, 236}, {120, 178, 230}, {234, 230, 222}, {44, 42, 48}, {112, 76, 48}, {164, 42, 38},
            // limestone bricks, iron, gold, copper, coal, hay (top, side)
            {208, 198, 172}, {206, 208, 212}, {238, 198, 70}, {196, 112, 72}, {38, 38, 42}, {206, 172, 84}, {214, 180, 88}};

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
            if (l == L_COBBLE || l == L_STONE || l == L_SPENT || l == L_COPPER || l == L_TIN || l == L_IRON || l == L_COAL || l == L_COAL_BLOCK) {
                int n = l == L_COBBLE ? 15 : l == L_COAL_BLOCK ? 18 : 7;
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
                case L_DIRT -> {
                    // loam in clods: a darker and a warmer earth mottled together, small stones and the odd root thread
                    float clod = noise(1, x, y) * 0.5f + noise(2, x, y) * 0.35f + noise(3, x, y) * 0.15f;
                    n = 0.8f + clod * 0.32f;
                    if (clod < 0.4f) { r = 98; g = 72; b = 50; }
                    float stone = noise(3, x, y) * 0.7f + noise(4, x, y) * 0.3f;
                    if (stone > 0.8f) { r = 142; g = 132; b = 120; n = 0.92f + noise(4, x, y) * 0.16f; }
                    else if (stone > 0.74f) n *= 0.8f;                                             // a stone's shadow
                }
                case L_BARE -> {
                    n = pebbles(x, y, 0.84f + fbm(x, y) * 0.3f);
                    int row = (y + 4) % 16; if (row < 3) n *= 0.7f; else if (row < 6) n *= 1.12f;   // furrows
                }
                case L_SAND -> {
                    // fine grains in three tones, wind ripples across, a few darker and shell-pale grains
                    float ripple = (float) Math.sin((y + noise(1, x, y) * 10) * (Math.PI * 2 / 10.667));
                    n = 0.92f + ripple * 0.055f + noise(2, x, y) * 0.06f;
                    float grain = noise(4, x, y);
                    if (grain > 0.84f) n *= 1.08f; else if (grain < 0.14f) n *= 0.92f;
                    if (rnd.nextFloat() < 0.02f) { r = 236; g = 226; b = 196; n = 1; }
                    else if (rnd.nextFloat() < 0.015f) { r = 150; g = 128; b = 96; n = 1; }
                }
                case L_MUD -> {
                    // wet earth: dark and churned, with glossy puddled hollows and the odd straw-pale fleck
                    float churn = noise(0, x, y) * 0.45f + noise(1, x, y) * 0.35f + noise(2, x, y) * 0.2f;
                    n = 0.78f + churn * 0.3f + noise(3, x, y) * 0.05f;
                    if (churn > 0.62f) {                                                          // the wet sheen of a puddled hollow
                        float wet = Math.min(1, (churn - 0.62f) * 6);
                        r = 84 - 14 * wet; g = 70 - 6 * wet; b = 56; n = 0.95f + wet * 0.2f;
                        if (churn > 0.7f && noise(2, x, y) > 0.6f) n *= 1.12f;
                    } else if (churn < 0.36f) n *= 0.86f;                                          // the ridges between, drying
                }
                case L_CLAY -> {
                    // smooth, dense and faintly bedded: soft horizontal bands and fine drying cracks
                    float bed = (float) Math.sin((y + noise(1, x, y) * 6) * (Math.PI * 2 / 12.8));
                    n = 0.93f + bed * 0.035f + noise(0, x, y) * 0.07f + noise(3, x, y) * 0.025f;
                    if (noise(0, x, y) > 0.7f) { r = 150; g = 146; b = 148; }                       // a cooler, damper patch
                    float crack = Math.abs(noise(2, x, y) - 0.5f);
                    if (crack < 0.018f) n *= 0.84f;
                }
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
                    // rounded stones of a few greys, lit from the top left, bedded in dark mortar
                    float[] v = voronoi(x, y);
                    int site = (int) v[2];
                    float edge = v[1] - v[0];
                    float tone = cells[site][2];
                    if (site % 4 == 1) { r = 132; g = 126; b = 118; } else if (site % 4 == 3) { r = 112; g = 112; b = 116; }
                    float dx = x - cells[site][0], dy = y - cells[site][1];
                    if (Math.abs(dx) > TEX / 2f) dx -= Math.signum(dx) * TEX;
                    if (Math.abs(dy) > TEX / 2f) dy -= Math.signum(dy) * TEX;
                    float lit = Math.max(-1, Math.min(1, -(dx + dy) / Math.max(4, v[1])));      // the sunny shoulder of each stone
                    n = (0.84f + fbm(x, y) * 0.16f) * tone * (1 + lit * 0.12f);
                    if (edge < 2.2f) { r = 58; g = 56; b = 54; n = 0.9f + noise(5, x, y) * 0.2f; }
                    else if (edge < 3.6f) n *= 0.8f;                                               // shadow where a stone dips into the mortar
                }
                case L_GLASS, L_WHITE_GLASS, L_BLUE_GLASS -> {
                    // a pane: a thin pale rim, clear body, two soft diagonal glints; stained panes carry their tint
                    boolean rim = x < 2 || y < 2 || x > TEX - 3 || y > TEX - 3;
                    boolean inner = !rim && (x < 4 || y < 4 || x > TEX - 5 || y > TEX - 5);
                    int clear = l == L_GLASS ? 34 : 120;
                    if (rim) { a = 235; n = l == L_GLASS ? 1.05f : 0.82f; if (l == L_GLASS) { r = 210; g = 228; b = 232; } }
                    else if (inner) { a = clear + 40; n = 0.92f; }
                    else {
                        a = clear; n = 1;
                        int d = x + y;
                        if (d > 14 && d < 22 || d > 26 && d < 29) { a = clear + 90; n = 1.18f; }          // the glints, upper left
                        if (x - y > 30 && x - y < 34) { a = clear + 50; n = 1.1f; }
                    }
                }
                case L_GRANITE -> {
                    // coarse-grained: pink feldspar, grey quartz and black mica in interlocking crystals
                    // fine grains (two texels) over a soft pink-grey ground: feldspar, quartz and the odd black mica
                    n = 0.92f + noise(1, x, y) * 0.1f;
                    float grain = noise(3, x, y) * 0.75f + noise(4, x, y) * 0.25f;
                    if (grain > 0.8f) { r = 214; g = 190; b = 180; n = 0.96f + noise(4, x, y) * 0.06f; }
                    else if (grain > 0.68f) { r = 182; g = 128; b = 110; }
                    else if (grain < 0.12f) { r = 58; g = 52; b = 52; n = 1; }
                    else if (grain < 0.24f) { r = 124; g = 106; b = 100; }
                }
                case L_STONE_BRICKS -> {
                    // dressed grey blocks, two courses to a metre, staggered; bevelled, mortared, the odd chip
                    int row = y / 16, by = y % 16, bx = Math.floorMod(x + (row % 2) * 16, 32);
                    if (by < 2 || bx < 2) { r = 88; g = 86; b = 84; n = 0.9f + noise(5, x, y) * 0.15f; }
                    else {
                        Random br = new Random(row * 41 + Math.floorMod(x + (row % 2) * 16, 64) / 32 * 7);
                        n = (0.88f + br.nextFloat() * 0.14f) * (0.9f + fbm(x, y) * 0.16f);
                        if (by == 2 || bx == 2) n *= 1.12f;
                        if (by == 15 || bx == 31) n *= 0.8f;
                        if (noise(4, x, y) > 0.82f && noise(5, x, y) > 0.6f) n *= 0.84f;          // chips and pits
                    }
                }
                case L_LIMESTONE -> {
                    // pale ashlar: long and short blocks in three courses, soft fossil pits, warm cream mortar
                    int[] courses = {0, 22, 43, 64};
                    int row = y < 22 ? 0 : y < 43 ? 1 : 2, by = y - courses[row], hgt = courses[row + 1] - courses[row];
                    int[][] joints = {{0, 38}, {14, 50}, {0, 26}};
                    int bx = x, block = 0;
                    for (int j : joints[row]) if (x >= j) { bx = x - j; block++; }
                    boolean mortar = by < 2 || bx < 2;
                    if (mortar) { r = 226; g = 218; b = 196; n = 0.92f + noise(5, x, y) * 0.08f; }
                    else {
                        Random br = new Random(row * 13 + block * 5 + 3);
                        n = (0.92f + br.nextFloat() * 0.1f) * (0.92f + fbm(x, y) * 0.12f);
                        if (by == 2 || bx == 2) n *= 1.06f;
                        if (by == hgt - 1) n *= 0.86f;
                        if (noise(3, x, y) > 0.86f && noise(2, x, y) > 0.55f) n *= 0.92f;          // small fossil pits
                    }
                }
                case L_MARBLE -> {
                    // white stone with grey veins wandering diagonally through it, and fainter hairline ones
                    n = 0.96f + noise(0, x, y) * 0.05f + noise(3, x, y) * 0.015f;
                    float flow = (x + y) / 64f * 2 + noise(0, x, y) * 1.1f + noise(1, x, y) * 0.45f;
                    float vein = Math.abs(flow - Math.round(flow));                                  // distance to the nearest vein
                    float main = Math.max(0, 1 - vein / 0.045f);
                    float soft = Math.max(0, 1 - vein / 0.11f);
                    if (main > 0) { r = 232 - 92 * main; g = 230 - 88 * main; b = 224 - 76 * main; }
                    n *= 1 - soft * 0.08f;
                    float flow2 = (x - 2 * y) / 64f * 3 + noise(1, x, y) * 0.8f;
                    float hair = Math.abs(flow2 - Math.round(flow2));
                    if (hair < 0.02f) n *= 0.9f;
                }
                case L_SANDSTONE_TOP, L_RED_SANDSTONE_TOP -> {
                    // the smooth bedding face: fine-grained, softly mottled, a dressed border
                    n = 0.95f + noise(1, x, y) * 0.06f + noise(3, x, y) * 0.03f + noise(4, x, y) * 0.02f;
                    if (noise(2, x, y) > 0.78f) n *= 0.96f;
                    int edge = Math.min(Math.min(x, y), Math.min(TEX - 1 - x, TEX - 1 - y));
                    if (edge < 2) n *= 0.84f; else if (edge < 4) n *= x < 4 || y < 4 ? 1.05f : 0.93f;
                }
                case L_SANDSTONE, L_RED_SANDSTONE -> {
                    // strata: a smooth cap course, then bands of finer and coarser stone, wavering a little
                    float wave = noise(2, x, 0) * 3;
                    float yy = y + wave;
                    if (y < 10) n = 0.98f + fbm(x, y) * 0.06f;
                    else {
                        int band = (int) (yy / 7);
                        n = (band % 3 == 0 ? 1.04f : band % 3 == 1 ? 0.95f : 0.9f) + fbm(x, y) * 0.08f;
                        if (yy % 7 < 1) n *= 0.88f;
                        if (noise(5, x, y) > 0.85f) n *= 0.94f;
                    }
                    if (y == 10 || y == 11) n *= 0.8f;
                }
                case L_WHITE_CARPET, L_BLACK_CARPET, L_BROWN_CARPET, L_RED_CARPET -> {
                    // woven wool: a twill of soft fibres, a patterned border and a fringe of darker weft
                    float fibre = noise(5, x, y) * 0.5f + noise(4, x * 2f, y) * 0.3f + noise(3, x, y) * 0.2f;
                    n = 0.9f + fibre * 0.14f;
                    if (Math.floorMod(x + y, 4) < 2) n *= 1.03f; else n *= 0.97f;                     // the twill
                    int edge = Math.min(Math.min(x, y), Math.min(TEX - 1 - x, TEX - 1 - y));
                    boolean dark = l == L_BLACK_CARPET;
                    if (edge < 3) n *= dark ? 1.35f : 0.8f;
                    else if (edge >= 6 && edge < 8) n *= dark ? 1.5f : 0.86f;                       // the border's inlaid line
                    else if (edge >= 8 && edge < 10 && Math.floorMod(x - y, 6) < 3) n *= dark ? 1.25f : 0.92f;
                }
                case L_IRON_BLOCK, L_GOLD_BLOCK, L_COPPER_BLOCK -> {
                    // a cast block: bevelled edges lit top-left, a brushed face, a rivet at each corner
                    int edge = Math.min(Math.min(x, y), Math.min(TEX - 1 - x, TEX - 1 - y));
                    n = 0.94f + noise(5, x * 0.15f, y * 3f) * 0.08f;                                // brushed across
                    if (edge < 3) n = (x < 3 || y < 3) && !(x > TEX - 4 || y > TEX - 4) ? 1.22f : 0.7f;
                    else if (edge < 5) n *= x < 5 || y < 5 ? 1.08f : 0.86f;
                    int cx = x < TEX / 2 ? 9 : TEX - 10, cy = y < TEX / 2 ? 9 : TEX - 10;
                    float rd = (float) Math.hypot(x - cx, y - cy);
                    if (rd < 2.6f) n = x - cx + y - cy < 0 ? 1.3f : 0.82f;
                    if (Math.abs(y - TEX / 2) < 1 && edge > 5) n *= 0.88f;                               // the seam of the casting
                    if (l == L_COPPER_BLOCK && edge > 3 && noise(1, x, y) * 0.7f + noise(3, x, y) * 0.3f > 0.7f) { r = 92; g = 158; b = 128; n = 0.92f + noise(3, x, y) * 0.1f; }   // verdigris where it weathers
                }
                case L_COAL_BLOCK -> {
                    // pressed coal: dark faceted lumps with a dull sheen and faint glints
                    float[] v = voronoi(x, y);
                    float tone = cells[(int) v[2]][2];
                    n = 0.8f + tone * 0.2f + noise(5, x, y) * 0.1f;
                    if (v[1] - v[0] < 1.4f) n *= 0.55f;
                    else if (v[0] < 2.5f) n *= 1.25f;
                    if (rnd.nextFloat() < 0.012f) { r = 160; g = 160; b = 170; n = 1; }
                }
                case L_HAY_SIDE -> {
                    // straw stalks running across, gold and pale, bound by two bands of twine
                    float stalk = noise(5, x * 0.18f, y * 3.5f) * 0.6f + noise(4, x * 0.3f, y * 2f) * 0.4f;
                    n = 0.8f + stalk * 0.38f;
                    if (stalk < 0.3f) { r = 160; g = 120; b = 52; }
                    if (Math.floorMod(y * 7 + (int) (noise(3, x, y) * 9), 11) == 0) n *= 1.12f;
                    if (Math.abs(y - 16) < 3 || Math.abs(y - 47) < 3) {
                        r = 120; g = 70; b = 40; n = 0.86f + (Math.floorMod(x + y, 4) < 2 ? 0.14f : 0);   // twisted twine
                        if (Math.abs(y - 16) == 2 || Math.abs(y - 47) == 2) n *= 0.8f;
                    }
                }
                case L_HAY_TOP -> {
                    // the cut face: short stalk ends at every angle, the twine coming over the edge
                    // the cut face: short stalk ends crossing every way, shadowed between, the twine running over the edge
                    int cell = (x / 4) + (y / 4) * 16;
                    boolean across = ((cell * 2654435761L) >>> 13 & 1) == 0;
                    int u = across ? x % 4 : y % 4;
                    n = 0.86f + noise(2, x, y) * 0.16f + (u == 1 || u == 2 ? 0.1f : -0.08f);
                    if (noise(3, x, y) < 0.2f) { n *= 0.8f; r = 170; g = 134; b = 62; }
                    int edge = Math.min(Math.min(x, y), Math.min(TEX - 1 - x, TEX - 1 - y));
                    if (edge < 2) n *= 0.85f;
                    if ((Math.abs(x - 16) < 2 || Math.abs(x - 47) < 2) && edge < 6) { r = 120; g = 70; b = 40; n = 0.9f; }
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
                case L_SPRUCE_BARK -> {
                    // dark, scaly: small plates in columns, each broken off at its own height, with resin-lit edges
                    int col = x / 8;
                    int plate = Math.floorMod(y + col * 5 + (int) (noise(2, x, y) * 4), 9);
                    n = 0.78f + noise(5, x * 1.5f, y * 0.5f) * 0.3f;
                    if (plate == 0) n *= 0.55f; else if (plate == 1) n *= 1.15f;
                    if (x % 8 == 0) n *= 0.7f;
                }
                case L_JUNGLE_BARK -> {
                    // warm and fibrous: long vertical strands, deep grooves, moss in the damp hollows
                    float strand = noise(5, x * 2.5f, y * 0.2f) * 0.7f + noise(4, x * 1.5f, y * 0.3f) * 0.3f;
                    n = 0.74f + strand * 0.42f;
                    if (strand < 0.28f) n *= 0.62f;
                    float moss = noise(1, x, y) * 0.65f + noise(2, x, y) * 0.35f;
                    if (moss > 0.64f) { float m = Math.min(1, (moss - 0.64f) * 8); r = 118 - 34 * m; g = 90 + 24 * m; b = 56 - 4 * m; n = 0.86f + strand * 0.2f; }
                }
                case L_BIRCH_BARK -> {
                    // chalk-white, papery, banded with black lenticels and the odd dark knot
                    n = 0.95f + noise(1, x, y) * 0.05f + noise(3, x, y) * 0.02f;
                    // lenticels: dark horizontal dashes in bands, each dash a few texels to a finger long
                    int band = y / 7, inBand = y % 7;
                    float dash = noise(2, x, band * 7 + 3);
                    if (dash > 0.6f && (inBand == 3 || inBand == 4 && dash > 0.7f)) { r = 46; g = 42; b = 40; n = 1; }
                    else if (dash > 0.6f && inBand == 2) n *= 0.9f;
                    // two dark knots where branches were, eye-shaped
                    for (int[] k : new int[][]{{18, 44}, {48, 14}}) {
                        float kx = x - k[0], ky = y - k[1];
                        float e = kx * kx / 30f + ky * ky / 4f;
                        if (e < 1) { r = 40; g = 34; b = 32; n = 0.9f + (ky < 0 ? 0.1f : 0); }
                        else if (e < 1.8f) n *= 0.86f;
                    }
                    if (noise(0, x, y) > 0.72f) { r = 214; g = 206; b = 190; }                      // the cream under peeled paper
                }
                case L_RINGS, L_SPRUCE_TOP, L_JUNGLE_TOP, L_BIRCH_TOP -> {
                    // end grain: growth rings round the pith, a radial check, and the bark of its own tree at the rim
                    float d = (float) Math.hypot(x - 31.5, y - 31.5) + noise(2, x, y) * 3;
                    float spacing = l == L_SPRUCE_TOP ? 1.8f : l == L_JUNGLE_TOP ? 1.0f : 1.3f;
                    n = 0.9f + 0.1f * (float) Math.sin(d * spacing);
                    if (((int) (d * spacing / 1.3f)) % 6 == 0) n *= 0.84f;
                    int[] bark = l == L_SPRUCE_TOP ? BASE[L_SPRUCE_BARK] : l == L_JUNGLE_TOP ? BASE[L_JUNGLE_BARK] : l == L_BIRCH_TOP ? BASE[L_BIRCH_BARK] : new int[]{96, 70, 44};
                    if (d > 28) { r = bark[0]; g = bark[1]; b = bark[2]; n = 0.8f + fbm(x, y) * 0.3f; if (l == L_BIRCH_TOP && noise(5, x, y) > 0.7f) n *= 0.5f; }
                    else if (d > 26.5f) n *= 0.78f;                                               // the cambium line
                    float crack = Math.abs((float) Math.atan2(y - 31.5, x - 31.5) - 0.6f);
                    if (crack < 0.04f && d < 26 && d > 4) n *= 0.6f;
                    if (d < 2) n *= 0.8f;                                                         // the pith
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
     * Per texture layer: roughness, metalness, emission, minimum wet roughness (255 keeps the engine default). Each surface answers light as its material does: water and
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
                {L_GLASS, 0.06f, 0f}, {L_WATER, 0.04f, 0f}, {L_DEBUG, 0.6f, 0f},
                {L_SPRUCE_BARK, 0.92f, 0f}, {L_JUNGLE_BARK, 0.9f, 0f}, {L_BIRCH_BARK, 0.8f, 0f}, {L_SPRUCE_TOP, 0.7f, 0f}, {L_JUNGLE_TOP, 0.7f, 0f}, {L_BIRCH_TOP, 0.7f, 0f},
                {L_GRANITE, 0.5f, 0f}, {L_STONE_BRICKS, 0.8f, 0f}, {L_MARBLE, 0.3f, 0f}, {L_LIMESTONE, 0.86f, 0f},                 // polished marble and granite
                {L_CLAY, 0.7f, 0f}, {L_MUD, 0.45f, 0f}, {L_SANDSTONE_TOP, 0.9f, 0f}, {L_SANDSTONE, 0.92f, 0f}, {L_RED_SANDSTONE_TOP, 0.9f, 0f}, {L_RED_SANDSTONE, 0.92f, 0f},
                {L_WHITE_GLASS, 0.06f, 0f}, {L_BLUE_GLASS, 0.06f, 0f},
                {L_WHITE_CARPET, 0.98f, 0f}, {L_BLACK_CARPET, 0.98f, 0f}, {L_BROWN_CARPET, 0.98f, 0f}, {L_RED_CARPET, 0.98f, 0f}, {L_HAY_TOP, 0.95f, 0f}, {L_HAY_SIDE, 0.95f, 0f},
                {L_IRON_BLOCK, 0.34f, 1f}, {L_GOLD_BLOCK, 0.26f, 1f}, {L_COPPER_BLOCK, 0.36f, 1f}, {L_COAL_BLOCK, 0.55f, 0f}};
        for (Object[] t : table) { int l = (Integer) t[0]; m[l * 4] = (byte) rough((Float) t[1]); m[l * 4 + 1] = (byte) clamp((Float) t[2] * 255); }
        m[L_LAMP * 4 + 2] = (byte) 255;
        m[L_FURNACE * 4 + 2] = (byte) 120;
        for (int l = 0; l < LAYERS; l++) m[l * 4 + 3] = (byte) rough((m[l * 4] & 255) / 255f * 0.55f);
        for (int l : new int[]{L_GRASS, L_GRASS_SIDE, L_DIRT, L_SAND, L_BARE, L_LEAVES, L_OAK_LEAVES, L_WILLOW_LEAVES, L_HAY_TOP, L_HAY_SIDE, L_WHITE_CARPET, L_BLACK_CARPET, L_BROWN_CARPET, L_RED_CARPET}) m[l * 4 + 3] = (byte) rough(0.8f);
        return m;
    }

    private static int rough(float r) { return clamp(r * 255); }

    private static int clamp(float v) { return Math.max(0, Math.min(255, Math.round(v))); }
}
