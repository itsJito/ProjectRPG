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
            EMBERS = 73,
            // 0.1.8: what burnt wood becomes - each block charred from its own grain (gamefixes: fire charred everything to
            // one premade coal block), in the order of CHAR_SOURCES
            CHARRED_PLANKS = 74, CHARRED_LOG = 75, CHARRED_OAK_LOG = 76, CHARRED_WILLOW_LOG = 77, CHARRED_SPRUCE_LOG = 78,
            CHARRED_JUNGLE_LOG = 79, CHARRED_BIRCH_LOG = 80, CHARRED_BEAM = 81, CHARRED_ROOF = 82, CHARRED_STUMP = 83,
            // 0.2.1 (gamefixes: dual blocks): two-faced wall blocks - one material outside, another inside, in one cell -
            // DUAL0 onward, four to a pair (the inner face toward +x, -x, +z, -z), the pairs in DUAL_PAIRS
            DUAL0 = 84,
            // 0.2.2 (gamefixes): a clipped hedge - small dense leaves, squared off; DevPalace's greenery
            HEDGE = 108,
            STAIRS = 109, LADDER = 110, COUNT = 111;
    public static boolean stairs(int id) { return id == STAIRS; }
    public static boolean ladder(int id) { return id == LADDER; }
    /** The two-faced blocks' pairs: {outside, inside}. */
    public static final int[][] DUAL_PAIRS = {{BRICK, PLASTER}, {STONE_BRICKS, PLASTER}, {PLASTER, PLANKS}, {STONE_BRICKS, PLANKS}, {LIMESTONE_BRICKS, MARBLE}, {IRON_BLOCK, PLANKS}};
    /** Inner-face directions of a two-faced block: toward +x, -x, +z, -z. */
    public static final int IN_PX = 0, IN_NX = 1, IN_PZ = 2, IN_NZ = 3;
    public static boolean dual(int id) { return id >= DUAL0 && id < DUAL0 + DUAL_PAIRS.length * 4; }
    /** The outside half of a two-faced block (the block itself for any other). */
    public static int outerOf(int id) { return dual(id) ? DUAL_PAIRS[(id - DUAL0) / 4][0] : id; }
    /** The inside half of a two-faced block (the block itself for any other). */
    public static int innerOf(int id) { return dual(id) ? DUAL_PAIRS[(id - DUAL0) / 4][1] : id; }
    /** Which way a two-faced block's inside faces (IN_*), or -1. */
    public static int innerDir(int id) { return dual(id) ? (id - DUAL0) % 4 : -1; }
    /** The two-faced block of an outside and an inside material facing the given way; the outside alone if no such pair. */
    public static int dualOf(int outer, int inner, int dir) {
        for (int i = 0; i < DUAL_PAIRS.length; i++) if (DUAL_PAIRS[i][0] == outer && DUAL_PAIRS[i][1] == inner) return DUAL0 + i * 4 + dir;
        return outer;
    }
    /** What is left of a two-faced block when one half burns away: the half that does not burn. */
    public static int survivorOf(int id) {
        if (!dual(id)) return id;
        int o = outerOf(id), in = innerOf(id);
        boolean ob = material(o) == Material.WOOD, ib = material(in) == Material.WOOD;
        return ob && !ib ? in : !ob && ib ? o : charredOf(o);
    }
    /** The wooden cubes that char into a twin of their own (CHARRED_PLANKS onward); anything else that burns to char is CHARRED. */
    public static final int[] CHAR_SOURCES = {PLANKS, LOG, OAK_LOG, WILLOW_LOG, SPRUCE_LOG, JUNGLE_LOG, BIRCH_LOG, BEAM, ROOF, STUMP};
    /** What a block chars into when fire is done with it. */
    public static int charredOf(int id) {
        for (int i = 0; i < CHAR_SOURCES.length; i++) if (CHAR_SOURCES[i] == id) return CHARRED_PLANKS + i;
        return CHARRED;
    }
    /** Burnt through already: char of any kind (it does not catch again). */
    public static boolean charred(int id) { return id == CHARRED || id >= CHARRED_PLANKS && id <= CHARRED_STUMP; }
    /**
     * The invisible collision of a prop the game draws as its own model (a dungeon's rock mound, a bounty board): object
     * cells that sound and burn like what the prop is made of - stone, or wood.
     */
    public static final int PROP_STONE = HEADSTONE, PROP_WOOD = TABLE;   // (both seen through: what stands in them stays in focus)
    /** Cobblestone is the cobble block (one block, redesigned), as Dirt, Sand and Glass are. */
    public static final int COBBLESTONE = COBBLE;

    /**
     * What a block is made of (gamefixes: the material engine). One answer drives everything that cares: the footstep,
     * whether and how it burns, whether it carries a charge, freezes or shatters, and how it takes the light.
     */
    public enum Material { NONE, EARTH, SAND, STONE, WOOD, FOLIAGE, CLOTH, STRAW, METAL, COAL, GLASS, WATER, ICE }

    public static Material material(int id) {
        if (dual(id)) return material(outerOf(id));   // what is struck and walked against is the outside
        return switch (id) {
            case AIR, EMBERS -> Material.NONE;
            case GRASS, DIRT, BARE_PATCH, CLAY, MUD -> Material.EARTH;
            case SAND -> Material.SAND;
            case LADDER, PLANKS, LOG, OAK_LOG, WILLOW_LOG, SPRUCE_LOG, JUNGLE_LOG, BIRCH_LOG, BEAM, ROOF, FENCE, STUMP, CRATE, BOOKCASE, TABLE,
                 CRAFT_TABLE, SAWBENCH, TARGET, DOOR, LOCKED_DOOR, CHARRED, CHARRED_PLANKS, CHARRED_LOG, CHARRED_OAK_LOG, CHARRED_WILLOW_LOG,
                 CHARRED_SPRUCE_LOG, CHARRED_JUNGLE_LOG, CHARRED_BIRCH_LOG, CHARRED_BEAM, CHARRED_ROOF, CHARRED_STUMP -> Material.WOOD;
            case LEAVES, OAK_LEAVES, WILLOW_LEAVES, HEDGE, HERB_PATCH, FLAX_PATCH, BERRY_BUSH -> Material.FOLIAGE;
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
        if (dual(id)) return true;
        return switch (id) { case GRASS, DIRT, STONE, BRICK, PLANKS, LOG, OAK_LOG, WILLOW_LOG, SAND, COBBLE, ROOF, CRATE, PLASTER, BEAM, CHARRED,
                SPRUCE_LOG, JUNGLE_LOG, BIRCH_LOG, GRANITE, STONE_BRICKS, MARBLE, CLAY, MUD, SANDSTONE, RED_SANDSTONE, WHITE_CARPET, BLACK_CARPET,
                BROWN_CARPET, RED_CARPET, LIMESTONE_BRICKS, IRON_BLOCK, GOLD_BLOCK, COPPER_BLOCK, COAL_BLOCK, HAY_BALE,
                CHARRED_PLANKS, CHARRED_LOG, CHARRED_OAK_LOG, CHARRED_WILLOW_LOG, CHARRED_SPRUCE_LOG, CHARRED_JUNGLE_LOG, CHARRED_BIRCH_LOG, CHARRED_BEAM, CHARRED_ROOF -> true; default -> false; };
    }
    /** Plants drawn as crossed planes that bodies walk through (gamefixes: no foliage is ever solid - the berry bush neither). */
    public static boolean plant(int id) { return id == HERB_PATCH || id == FLAX_PATCH || id == BERRY_BUSH; }

    // texture layers
    static final int L_GRASS = 0, L_GRASS_SIDE = 1, L_DIRT = 2, L_STONE = 3, L_BRICK = 4, L_GLASS = 5, L_LAMP = 6, L_PLANKS = 7,
            L_BARK = 8, L_RINGS = 9, L_LEAVES = 10, L_OAK_BARK = 11, L_OAK_LEAVES = 12, L_WILLOW_BARK = 13, L_WILLOW_LEAVES = 14,
            L_COPPER = 15, L_TIN = 16, L_IRON = 17, L_COAL = 18, L_SPENT = 19, L_WATER = 20, L_SAND = 21, L_COBBLE = 22, L_ROOF = 23,
            L_DOOR = 24, L_LOCKED = 25, L_FURNACE = 26, L_METAL = 27, L_RANGE = 28, L_TABLE_TOP = 29, L_SAW = 30, L_TARGET = 31,
            L_FENCE = 32, L_HERB = 33, L_FLAX = 34, L_BERRY = 35, L_CRATE = 36, L_BOOKS = 37, L_RED = 38, L_BLUE = 39, L_YELLOW = 40,
            L_DEBUG = 41, L_PLASTER = 42, L_BEAM = 43, L_BARE = 44, L_GREEN = 45, L_TABLE_SIDE = 46,
            L_SPRUCE_BARK = 47, L_SPRUCE_TOP = 48, L_JUNGLE_BARK = 49, L_JUNGLE_TOP = 50, L_BIRCH_BARK = 51, L_BIRCH_TOP = 52,
            L_GRANITE = 53, L_STONE_BRICKS = 54, L_MARBLE = 55, L_CLAY = 56, L_MUD = 57, L_SANDSTONE_TOP = 58, L_SANDSTONE = 59,
            L_RED_SANDSTONE_TOP = 60, L_RED_SANDSTONE = 61, L_WHITE_GLASS = 62, L_BLUE_GLASS = 63, L_WHITE_CARPET = 64,
            L_BLACK_CARPET = 65, L_BROWN_CARPET = 66, L_RED_CARPET = 67, L_LIMESTONE = 68, L_IRON_BLOCK = 69, L_GOLD_BLOCK = 70,
            L_COPPER_BLOCK = 71, L_COAL_BLOCK = 72, L_HAY_TOP = 73, L_HAY_SIDE = 74, L_HEDGE = 75;
    /** Texture layers, each TEX x TEX texels: 64 to the metre; from CHAR_LAYER0 on, the charred twins of the wood layers. */
    public static final int CHAR_LAYER0 = 76, LAYERS = 89, TEX = 64;
    /** Per charred layer, the layer it was charred from (filled with FACES). */
    private static final int[] CHAR_FROM = new int[LAYERS - CHAR_LAYER0];

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
        f(HAY_BALE, L_HAY_TOP, L_HAY_SIDE, L_HAY_TOP); f(HEDGE, L_HEDGE);
        f(EMBERS, L_COAL); f(STAIRS,L_STONE_BRICKS); f(LADDER,L_PLANKS);
        // each charrable wood cube's twin: its own faces, each charred (one charred layer per wood layer)
        int next = CHAR_LAYER0;
        int[] charOf = new int[CHAR_LAYER0];
        java.util.Arrays.fill(charOf, -1);
        for (int i = 0; i < CHAR_SOURCES.length; i++) {
            int[] src = FACES[CHAR_SOURCES[i]], twin = new int[3];
            for (int k = 0; k < 3; k++) {
                if (charOf[src[k]] < 0) { charOf[src[k]] = next; CHAR_FROM[next - CHAR_LAYER0] = src[k]; next++; }
                twin[k] = charOf[src[k]];
            }
            FACES[CHARRED_PLANKS + i] = twin;
        }
        // two-faced blocks: the outside's top and side, the inside's side as the inner face (the mesher's bottom layer)
        for (int id = DUAL0; id < DUAL0 + DUAL_PAIRS.length * 4; id++) FACES[id] = new int[]{FACES[outerOf(id)][0], FACES[outerOf(id)][1], FACES[innerOf(id)][1]};
        if (next != LAYERS) throw new IllegalStateException("charred layers: " + (next - CHAR_LAYER0) + ", LAYERS expects " + (LAYERS - CHAR_LAYER0));
    }
    private static void f(int id, int top, int side, int bottom) { FACES[id] = new int[]{top, side, bottom}; }
    private static void f(int id, int all) { f(id, all, all, all); }

    /** The block colours for the minimap (RGB). */
    public static int mapColor(int id) {
        int top = FACES[Math.max(1, Math.min(COUNT - 1, id))][0];
        int[] c = average(top);
        return c[0] << 16 | c[1] << 8 | c[2];
    }

    public static boolean isTreeLog(int id) { return id == LOG || id == OAK_LOG || id == WILLOW_LOG || id == SPRUCE_LOG || id == JUNGLE_LOG || id == BIRCH_LOG; }
    public static boolean isLeaves(int id) { return id == LEAVES || id == OAK_LEAVES || id == WILLOW_LEAVES || id == HEDGE; }

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
        for (int leaves : new int[]{LEAVES, OAK_LEAVES, WILLOW_LEAVES, HEDGE, BERRY_BUSH}) {
            d[leaves] = new NativeEngine.BlockDef(AstrattoNative.BLOCK_CUTOUT | AstrattoNative.BLOCK_SKY_DIM);
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
        d[STAIRS] = new NativeEngine.BlockDef(solid | AstrattoNative.BLOCK_STEP); d[STAIRS].shape=2;
        d[LADDER] = new NativeEngine.BlockDef(AstrattoNative.BLOCK_CLIMB | AstrattoNative.BLOCK_LIGHT_PASS);
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
        r[HEDGE]=def(FACES[HEDGE],AstrattoNative.RB_SOLID | AstrattoNative.RB_CUTOUT | AstrattoNative.RB_SWAY);
        r[FENCE] = def(FACES[FENCE], AstrattoNative.RB_SOLID); r[FENCE].shape = SHAPE_HIDDEN;   // the game draws fences as models (Props.fences)
        r[DOOR] = def(FACES[DOOR], AstrattoNative.RB_DOOR | AstrattoNative.RB_SOLID);
        r[DOOR].shape = SHAPE_HIDDEN;
        r[LOCKED_DOOR] = def(FACES[LOCKED_DOOR], AstrattoNative.RB_DOOR | AstrattoNative.RB_SOLID);
        r[LOCKED_DOOR].shape = SHAPE_HIDDEN;
        r[EMBERS] = def(FACES[EMBERS], 0); r[EMBERS].shape = SHAPE_HIDDEN;
        for (int i = 0; i < COUNT; i++) if (objectCell(i)) { r[i] = def(FACES[i], AstrattoNative.RB_SOLID); r[i].shape = SHAPE_HIDDEN; }   // the game draws these
        for (int i = 0; i < COUNT; i++) if (plant(i)) r[i] = def(FACES[i], AstrattoNative.RB_CROSS | AstrattoNative.RB_CUTOUT | AstrattoNative.RB_SWAY);
        for (int i = DUAL0; i < COUNT; i++) if (dual(i)) r[i] = def(FACES[i], cube | (innerDir(i) + 1) << AstrattoNative.RB_INNER_SHIFT);
        r[STAIRS]=def(FACES[STAIRS],AstrattoNative.RB_SOLID | AstrattoNative.RB_STEP); r[STAIRS].shape=2;
        r[LADDER]=def(FACES[LADDER],AstrattoNative.RB_SOLID); r[LADDER].shape=3;
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
        NativeMesher.ShapeDef stairs=new NativeMesher.ShapeDef(), ladder=new NativeMesher.ShapeDef();
        stairs.orient=7; stairs.boxes=new int[][]{{0,0,0,16,8,16,0},{0,8,8,16,16,16,0}};
        ladder.orient=1; ladder.boxes=new int[][]{{2,0,1,4,16,3,0},{12,0,1,14,16,3,0},{3,2,1,13,4,3,0},{3,7,1,13,9,3,0},{3,12,1,13,14,3,0}};
        return new NativeMesher.ShapeDef[]{cube, hidden, stairs, ladder};
    }

    // ------------------------------------------------------------------ painting the texture array
    /** The texture array: LAYERS layers of TEX x TEX RGBA (native memory; the caller frees it). */
    public static ByteBuffer atlasPixels() {
        ByteBuffer px = MemoryUtil.memAlloc(TEX * TEX * 4 * LAYERS);
        for (int l = 0; l < LAYERS; l++) for (int[] c : layer(l)) px.put((byte) c[0]).put((byte) c[1]).put((byte) c[2]).put((byte) c[3]);
        px.flip();
        return px;
    }

    /** One layer's texels: the material's own painting (Textures); a charred layer is its wood painted, then burnt over. */
    static int[][] layer(int l) {
        if (l < CHAR_LAYER0) return Textures.layer(l);
        int[][] wood = Textures.layer(CHAR_FROM[l - CHAR_LAYER0]);
        Painter fire = new Painter(l);
        for (int y = 0; y < TEX; y++) for (int x = 0; x < TEX; x++) wood[y * TEX + x] = fire.charred(wood[y * TEX + x], x, y);
        return wood;
    }

    /** Each layer's average colour (the minimap, the light bounced off it), from its own painting. */
    private static int[][] averages;
    private static synchronized int[] average(int l) {
        if (averages == null) {
            averages = new int[LAYERS][];
            for (int k = 0; k < LAYERS; k++) {
                long r = 0, g = 0, b = 0, n = 0;
                for (int[] c : layer(k)) if (c[3] > 100) { r += c[0]; g += c[1]; b += c[2]; n++; }
                averages[k] = n == 0 ? new int[]{128, 128, 128} : new int[]{(int) (r / n), (int) (g / n), (int) (b / n)};
            }
        }
        return averages[l];
    }

    /** What fire does to a painted wood layer (its charred twin): tileable noise to break the char up. */
    private static final class Painter {
        final int l;
        final Random rnd;
        final float[][] lattices = new float[6][];
        final int[] periods = {4, 8, 16, 32, 64, 2};

        Painter(int l) {
            this.l = l;
            this.rnd = new Random(7 + l * 101L);
            for (int i = 0; i < lattices.length; i++) { int p = periods[i]; lattices[i] = new float[p * p]; for (int k = 0; k < p * p; k++) lattices[i][k] = rnd.nextFloat(); }
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

        /**
         * Wood as fire leaves it, from what it was (a colour of the unburnt layer): darkened to soot by how light it was, so
         * the grain and the joints still read; split into the blocky "alligator" checks charred timber cracks into, dark in
         * the cracks; grey ash bloomed over the ridges; and here and there a crack still holding a dull ember.
         */
        int[] charred(int[] c, int x, int y) {
            float lum = (0.3f * c[0] + 0.59f * c[1] + 0.11f * c[2]) / 255f;
            // soot over the wood: its light and dark kept (the grain and joints read through), warm brown-black
            float soot = 20 + lum * 78;
            float r = soot * 1.1f, gg = soot * 0.9f, b = soot * 0.78f;
            // the checks: uneven blocks, longer along the grain (rows) than across, wavering as real char cracks do
            float u = x / 12.5f + noise(1, x, y) * 1.1f, v = y / 7.0f + noise(2, x, y) * 0.7f;
            float fu = u - (float) Math.floor(u), fv = v - (float) Math.floor(v);
            float edge = Math.min(Math.min(fu, 1 - fu) * 12.5f, Math.min(fv, 1 - fv) * 7.0f);
            float ash = fbm(x * 1.3f, y * 1.3f);
            if (edge < 0.75f) {
                // a crack: near black - and, deep in a few, a dull ember still glowing
                r = 12; gg = 9; b = 8;
                if (noise(3, x, y) > 0.8f && noise(0, x, y) > 0.62f) { r = 150; gg = 52; b = 16; }
            } else {
                if (edge < 1.8f) { r *= 0.62f; gg *= 0.62f; b *= 0.62f; }   // each block's edge rounding down into its crack
                else if (edge > 3.2f) { r *= 1.12f; gg *= 1.1f; b *= 1.08f; } // and its face standing proud
                if (ash > 0.58f) {
                    float k = Math.min(1, (ash - 0.58f) * 3.2f) * 0.78f;
                    r += (118 - r) * k; gg += (112 - gg) * k; b += (106 - b) * k;
                }
            }
            return new int[]{clamp(r), clamp(gg), clamp(b), c[3]};
        }

        /** Fractal noise from coarse to fine (0..1). */
        float fbm(float x, float y) { return noise(0, x, y) * 0.35f + noise(1, x, y) * 0.3f + noise(2, x, y) * 0.2f + noise(3, x, y) * 0.15f; }

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
            int side = FACES[id][1];
            int[] c = average(side);
            for (int k = 0; k < 3; k++) p[id * 8 + k] = (float) Math.pow(c[k] / 255.0, 2.2);
            p[id * 8 + 3] = 1;
        }
        // (0.1.9: a lantern's warm flame, and its bounce no longer floods the whole room evenly - the pool of light round
        // the lamp is its own direct light, Gfx.lampLights)
        p[LAMP * 8 + 4] = 2.6f; p[LAMP * 8 + 5] = 1.75f; p[LAMP * 8 + 6] = 0.85f;
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
                {L_WHITE_CARPET, 0.98f, 0f}, {L_BLACK_CARPET, 0.98f, 0f}, {L_BROWN_CARPET, 0.98f, 0f}, {L_RED_CARPET, 0.98f, 0f}, {L_HAY_TOP, 0.95f, 0f}, {L_HAY_SIDE, 0.95f, 0f}, {L_HEDGE, 0.66f, 0f},
                {L_IRON_BLOCK, 0.34f, 1f}, {L_GOLD_BLOCK, 0.26f, 1f}, {L_COPPER_BLOCK, 0.36f, 1f}, {L_COAL_BLOCK, 0.55f, 0f}};
        for (Object[] t : table) { int l = (Integer) t[0]; m[l * 4] = (byte) rough((Float) t[1]); m[l * 4 + 1] = (byte) clamp((Float) t[2] * 255); }
        for (int l = CHAR_LAYER0; l < LAYERS; l++) m[l * 4] = (byte) rough(0.97f);   // char is dry and dull
        m[L_LAMP * 4 + 2] = (byte) 255;
        m[L_FURNACE * 4 + 2] = (byte) 120;
        for (int l = 0; l < LAYERS; l++) m[l * 4 + 3] = (byte) rough((m[l * 4] & 255) / 255f * 0.55f);
        for (int l : new int[]{L_GRASS, L_GRASS_SIDE, L_DIRT, L_SAND, L_BARE, L_LEAVES, L_OAK_LEAVES, L_WILLOW_LEAVES, L_HEDGE, L_HAY_TOP, L_HAY_SIDE, L_WHITE_CARPET, L_BLACK_CARPET, L_BROWN_CARPET, L_RED_CARPET}) m[l * 4 + 3] = (byte) rough(0.8f);
        return m;
    }

    private static int rough(float r) { return clamp(r * 255); }

    private static int clamp(float v) { return Math.max(0, Math.min(255, Math.round(v))); }
}
