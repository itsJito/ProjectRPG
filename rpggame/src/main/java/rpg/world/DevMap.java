package rpg.world;

import astratto.bridge.AstrattoNative;
import astratto.bridge.CommandBuffer;
import astratto.bridge.NativeEngine;
import static rpg.world.Blocks.*;

/**
 * The developer test map (GDD 4.2): one small, deliberately artificial, continuously walkable map of greybox areas -
 * the movement/combat lane (west), the skill laboratory and interaction shed (north), the NPC/social yard inside the
 * Alpha jurisdiction (east), the state/economy bay inside the Beta jurisdiction (south), the quest-state kiosk, and the
 * debug/control zone in the middle where the player starts. Fixture positions are public so the systems that own the
 * fixtures place them where the map built them.
 */
public final class DevMap {
    private DevMap() {}

    public static final int SX = 128, SY = 40, SZ = 128, FLOOR = 4;
    /** Doors and doorways are two blocks (2 m) tall. */
    public static final int DOOR_HEIGHT = 2;

    /** The two synthetic jurisdictions and the unpoliced wilds (GDD 4.2: two or more jurisdiction ids). */
    public static final String ALPHA = "DevCity", BETA = "DevTown", WILDS = "WILDS";

    public static String jurisdiction(double x, double z) {
        if (x >= 82 && z >= 46) return ALPHA;
        if (z >= 92 && x < 82) return BETA;
        return WILDS;
    }

    // ------------------------------------------------------------------ where things are
    public static final float SPAWN_X = 64.5f, SPAWN_Z = 68.5f;
    // debug zone
    public static final int[] DEBUG_TERMINAL = {64, 58};
    /** The altar (prayer, spellbooks), on the debug square. */
    public static final int[] ALTAR = {60, 62};
    // combat lane
    public static final int[][] DUMMIES = {{12, 46}, {16, 46}, {20, 46}};
    public static final int[] CABINET = {40, 46};
    public static final int TARGET_X = 7;
    public static final int[][] ARCHERY_TARGETS = {{7, 56}, {7, 61}};
    public static final int[][] BANDIT_SPAWNS = {{14, 80}, {28, 80}, {21, 85}};
    // skill lab
    public static final int SHED_X0 = 48, SHED_Z0 = 8, SHED_X1 = 62, SHED_Z1 = 22;
    public static final int[][] SHED_DOOR = {{54, 22}, {55, 22}};
    public static final int[][] STORE_DOOR = {{57, 14}, {57, 15}};
    public static final int[] SHED_TABLE = {50, 12};
    public static final int[] SHED_CHEST = {55, 10};
    public static final int[] STORE_CHEST = {60, 10};
    public static final int[] FURNACE_AT = {64, 26}, ANVIL_AT = {66, 26}, RANGE_AT = {68, 26}, CRAFT_AT = {70, 26}, SAW_AT = {72, 26};
    public static final int BUILD_X0 = 75, BUILD_Z0 = 24, BUILD_X1 = 79, BUILD_Z1 = 28;
    public static final int[][] TREES = {{86, 8}, {92, 11}, {87, 16}};
    public static final int[][] OAKS = {{99, 8}, {105, 13}};
    public static final int[][] WILLOWS = {{97, 24}, {108, 25}};
    public static final int[][] COPPER = {{48, 32}, {50, 32}}, TIN = {{48, 35}, {50, 35}}, IRON = {{54, 32}, {56, 32}}, COALS = {{60, 32}, {60, 35}};
    public static final int POND_X0 = 86, POND_Z0 = 29, POND_X1 = 106, POND_Z1 = 40;
    public static final float[][] FISH_SPOTS = {{89.5f, 30.5f}, {101.5f, 37.5f}, {95.5f, 39.5f}};
    public static final int[][] HERBS = {{66, 33}, {68, 33}}, FLAXES = {{71, 33}, {73, 33}}, BERRIES = {{76, 33}, {78, 33}};
    // agility course: start, log beam over a pit, climbing wall, stepping stones, finish
    public static final int AG_WALL_Z = 27;
    public static final int[] SHORTCUT = {118, 46};
    // NPC/social yard (Alpha)
    public static final int[] WELL = {100, 72};
    public static final int SHOP_A_X0 = 86, SHOP_A_Z0 = 52, SHOP_A_X1 = 96, SHOP_A_Z1 = 62;
    public static final float[] SHOP_A_COUNTER = {91.5f, 56.5f};
    public static final int[] STALL = {92, 67};
    public static final int CH_A_X0 = 108, CH_A_Z0 = 52, CH_A_X1 = 120, CH_A_Z1 = 62;
    public static final float[] LAWMAN_A = {114.5f, 56.5f};
    public static final float[][] PATROL = {{98.5f, 66.5f}, {113.5f, 69.5f}, {113.5f, 82.5f}, {96.5f, 82.5f}, {88.5f, 72.5f}};
    public static final int HOUSE1_X0 = 86, HOUSE1_Z0 = 86, HOUSE1_X1 = 94, HOUSE1_Z1 = 94;
    public static final int HOUSE2_X0 = 100, HOUSE2_Z0 = 86, HOUSE2_X1 = 108, HOUSE2_Z1 = 94;
    // state/economy bay (Beta)
    public static final int SHOP_B_X0 = 16, SHOP_B_Z0 = 104, SHOP_B_X1 = 26, SHOP_B_Z1 = 114;
    public static final float[] SHOP_B_COUNTER = {20.5f, 109.5f};
    public static final int CH_B_X0 = 30, CH_B_Z0 = 112, CH_B_X1 = 40, CH_B_Z1 = 122;
    public static final float[] LAWMAN_B = {35.5f, 117.5f};
    public static final int CONTACT_X0 = 44, CONTACT_Z0 = 108, CONTACT_X1 = 52, CONTACT_Z1 = 116;
    public static final int KIOSK_X0 = 58, KIOSK_Z0 = 100, KIOSK_X1 = 64, KIOSK_Z1 = 106;
    public static final float[] CARAVAN_A = {91.5f, 64.5f}, CARAVAN_B = {27.5f, 108.5f};
    // animals (GDD 3.9): a wild wood in the north-west and a farm south of the houses
    public static final int WILD_X0 = 3, WILD_Z0 = 3, WILD_X1 = 44, WILD_Z1 = 40;
    public static final int WILD_POND_X0 = 22, WILD_POND_Z0 = 17, WILD_POND_X1 = 28, WILD_POND_Z1 = 22;
    public static final int[][] WILD_TREES = {{8, 26}, {14, 34}, {34, 8}, {38, 30}, {30, 36}, {18, 8}, {40, 18}};
    public static final int[][] WILD_BUSHES = {{12, 18}, {13, 19}, {32, 26}, {33, 26}, {20, 30}, {36, 14}, {6, 14}, {27, 9}};
    /** The den: a rock shelter in the wood's corner (the bear's and the lion's lie-up). */
    public static final int DEN_X0 = 5, DEN_Z0 = 4, DEN_X1 = 10, DEN_Z1 = 8;
    public static final int FARM_X0 = 86, FARM_Z0 = 102, FARM_X1 = 122, FARM_Z1 = 124;
    /** The paddock's gate (a door in the fence) and the shelter (a closed barn with a two-leaf entrance). */
    public static final int[][] FARM_GATE = {{100, 102}, {101, 102}};
    public static final int BARN_X0 = 110, BARN_Z0 = 116, BARN_X1 = 121, BARN_Z1 = 123;
    public static final int DUCK_POND_X0 = 89, DUCK_POND_Z0 = 115, DUCK_POND_X1 = 95, DUCK_POND_Z1 = 120;
    /**
     * The graveyard (GDD 3.12): inside Alpha's boundary, south-east of the square - where the dead wake, and where the
     * undertaker keeps what the guards bring in. A fenced plot entered by a gate in its north fence, the undertaker's
     * hut in its south-east corner.
     */
    public static final int GRAVE_X0 = 111, GRAVE_Z0 = 86, GRAVE_X1 = 125, GRAVE_Z1 = 100;
    public static final int[] GRAVE_GATE = {116, 86};
    public static final int UNDERTAKER_X0 = 119, UNDERTAKER_Z0 = 94, UNDERTAKER_X1 = 125, UNDERTAKER_Z1 = 100;
    /** Where the dead wake (inside the gate), where the undertaker stands (by the hut door), and the custody chest inside. */
    public static final float[] GRAVEYARD_SPAWN = {116.5f, 88.6f}, UNDERTAKER_POST = {117.6f, 96.5f}, CUSTODY_CHEST = {123.5f, 98.5f};
    public static final int[][] HEADSTONES = {{112, 89}, {114, 89}, {112, 92}, {114, 92}, {112, 95}, {114, 95}, {112, 98}, {114, 98},
            {119, 89}, {121, 89}, {123, 89}, {119, 91}, {121, 91}, {123, 91}};
    /** The fence's corner (GDD 3.3 Fence): a shaded nook behind Store B in Beta, out of the guards' sight, among crates. */
    public static final float[] FENCE_POST = {11.5f, 117.5f};
    public static final float[] TROUGH = {104.5f, 109.5f}, FARM_CHEST = {119.5f, 121.5f};
    public static final float[][] NEST_BOXES = {{112.5f, 122.2f}, {114.5f, 122.2f}, {116.5f, 122.2f}};

    /**
     * Furniture: objects, not blocks - each stands on invisible collision cells and is drawn as its own model. Kind; the
     * cells x0, z0 to x1, z1, h cells tall from the floor; facing in quarter turns (0: its front toward -z; 1: -x...
     * turning anticlockwise seen from above); its owner (0: nobody's).
     */
    public record Piece(String kind, int x0, int z0, int x1, int z1, int h, int facing, long owner) {}
    public static final Piece[] FURNITURE = {
            new Piece("TABLE", 50, 12, 51, 13, 1, 0, 0),                     // the shed's table, the loose test items on it
            new Piece("COUNTER", 88, 57, 94, 57, 1, 2, rpg.Ids.MERCHANT_A),  // store A
            new Piece("COUNTER", 112, 57, 116, 57, 1, 2, 0),                 // chancery A
            new Piece("COUNTER", 91, 67, 93, 67, 1, 2, rpg.Ids.MERCHANT_A),  // the market stall
            new Piece("COUNTER", 19, 106, 19, 112, 1, 3, rpg.Ids.MERCHANT_B), // store B
            new Piece("COUNTER", 33, 118, 37, 118, 1, 0, 0),                 // chancery B
            new Piece("TABLE", 60, 104, 62, 104, 1, 0, 0),                   // the quest kiosk
            new Piece("CRATE", 24, 62, 24, 62, 1, 0, 0), new Piece("CRATE", 25, 62, 25, 62, 1, 0, 0), new Piece("CRATE", 26, 62, 26, 62, 1, 1, 0),   // cover on the lane
            new Piece("TARGET", 7, 56, 7, 57, 2, 3, 0), new Piece("TARGET", 7, 61, 7, 62, 2, 3, 0),          // the archery range, facing the shooters
            new Piece("BED", 91, 91, 91, 92, 1, 0, rpg.Ids.MERCHANT_A), new Piece("BED", 92, 91, 92, 92, 1, 0, rpg.Ids.WORKER),
            new Piece("BED", 93, 91, 93, 92, 1, 0, rpg.Ids.CARTER),          // house 1
            new Piece("BED", 105, 91, 105, 92, 1, 0, rpg.Ids.VILLAGER), new Piece("BED", 106, 91, 106, 92, 1, 0, 0),
            new Piece("BED", 107, 91, 107, 92, 1, 0, 0),                     // house 2 (two spare beds anyone may use)
            new Piece("BED", 110, 53, 110, 54, 1, 0, rpg.Ids.LAWMAN_A)};     // the lawman's cot in chancery A
    /** The graveyard's pieces (built after every older fixture, so their ids hold): the undertaker's counter and cot, the stones. */
    public static final Piece[] GRAVEYARD_PIECES = {
            new Piece("COUNTER", 121, 96, 121, 98, 1, 1, rpg.Ids.UNDERTAKER), new Piece("BED", 124, 95, 124, 96, 1, 0, rpg.Ids.UNDERTAKER),
            stone(0), stone(1), stone(2), stone(3), stone(4), stone(5), stone(6), stone(7), stone(8), stone(9), stone(10), stone(11), stone(12), stone(13),
            // the fence's nook behind Store B: crates to sit his goods on and to hide him from the road
            new Piece("CRATE", 10, 115, 10, 115, 1, 0, rpg.Ids.FENCE), new Piece("CRATE", 13, 115, 13, 115, 1, 1, rpg.Ids.FENCE),
            new Piece("CRATE", 10, 119, 10, 119, 1, 0, rpg.Ids.FENCE), new Piece("CRATE", 13, 119, 13, 119, 1, 1, 0)};

    private static Piece stone(int i) { return new Piece("HEADSTONE", HEADSTONES[i][0], HEADSTONES[i][1], HEADSTONES[i][0], HEADSTONES[i][1], 1, 0, 0); }

    /**
     * The coarse travel graph (gates, roads, doors): long trips are walked leg by leg between these so each route the
     * engine searches stays short. Points {x, z}; links are pairs of indices that can be walked straight between.
     */
    public static final float[][] WAYPOINTS = {
            {64.5f, 64.5f}, {64.5f, 44f}, {64.5f, 29.5f}, {84f, 24f}, {84f, 42.5f}, {98.5f, 43f}, {98.5f, 49f}, {100.5f, 68f},   // 0-7: debug, north walk, workstations, grove, pond west, gate out, gate in, yard
            {90.5f, 64f}, {113.5f, 64f}, {97f, 84f}, {85f, 68f}, {79f, 64.5f}, {78f, 96f}, {86f, 96f}, {60f, 96f},              // 8-15: store A door, chancery A door, houses, yard west gate in/out, south gate out/in, bay road
            {61.5f, 98.5f}, {28.5f, 96.5f}, {35.5f, 110.5f}, {48.5f, 106.5f}, {46f, 64f}, {21f, 68.5f}, {64.5f, 82f}, {97f, 96.5f},  // 16-23: kiosk, store B road, chancery B door, contact door, combat lane, pen gate, south walk, south of the houses
            {27.5f, 107f}, {100.5f, 99.5f}, {100.5f, 105.5f},                                                                  // 24: store B door; 25-26 the farm gate, out and in
            {116.5f, 84.6f}, {116.5f, 88.6f}};                                                                                  // 27-28: the graveyard gate, out and in
    public static final int[][] LINKS = {
            {0, 1}, {1, 2}, {2, 3}, {3, 4}, {4, 5}, {5, 6}, {6, 7}, {7, 8}, {7, 9}, {7, 10}, {7, 11}, {11, 12}, {12, 0}, {8, 11},
            {10, 23}, {23, 14}, {14, 13}, {13, 15}, {15, 16}, {15, 22}, {22, 0}, {15, 17}, {17, 24}, {17, 18}, {15, 19}, {0, 20}, {20, 21}, {2, 1}, {23, 25}, {25, 26}, {7, 27}, {10, 27}, {27, 28}};

    /** Where a piece's owner sleeps: the middle of their bed (NPC homes come from here). */
    private static Piece[] concat(Piece[] a, Piece[] b) { Piece[] r = java.util.Arrays.copyOf(a, a.length + b.length); System.arraycopy(b, 0, r, a.length, b.length); return r; }

    public static float[] bedOf(long owner) {
        for (Piece p : concat(FURNITURE, GRAVEYARD_PIECES)) if (p.kind.equals("BED") && p.owner == owner) return new float[]{(p.x0 + p.x1 + 1) / 2f, (p.z0 + p.z1 + 1) / 2f};
        return null;
    }

    /** Area signs: label, x, z. */
    public static final Object[][] SIGNS = {
            {"DEBUG / CONTROL ZONE", 64.5f, 62.5f}, {"MOVEMENT / COMBAT LANE", 30.5f, 44.5f}, {"HOSTILE PEN", 21.5f, 69.5f},
            {"ARCHERY RANGE", 14.5f, 59.5f}, {"INTERACTION SHED", 55.5f, 24f}, {"WORKSTATIONS", 68.5f, 28.5f}, {"BUILD SPOT", 77.5f, 29.5f},
            {"MINING ROCKS", 54.5f, 38.5f}, {"TREE GROVE", 96.5f, 18.5f}, {"FISHING POND", 96.5f, 42f}, {"PATCHES", 72.5f, 36.5f},
            {"AGILITY COURSE", 117.5f, 5.5f}, {"NPC / SOCIAL YARD  (DevCity)", 100.5f, 64.5f}, {"STORE A", 91.5f, 63.5f},
            {"CHANCERY A", 114.5f, 63.5f}, {"STATE / ECONOMY BAY  (DevTown)", 40.5f, 98.5f}, {"STORE B", 27.5f, 106.5f},
            {"CHANCERY B", 35.5f, 111.5f}, {"QUEST KIOSK", 61.5f, 99f}};

    /** Builds the devmap into a new engine world (lit) and returns its handle. */
    public static long build(NativeEngine e) {
        if (!e.blockTable(Blocks.engineDefs())) throw new IllegalStateException("block table refused: " + e.lastError());
        long world = e.createWorld(SX, SY, SZ, STONE, null, null, 0);
        if (world == 0) throw new IllegalStateException("world refused: " + e.lastError());
        CommandBuffer b = new CommandBuffer(1 << 16);
        new Builder(b).all();
        b.lightAll();
        b.pristineSave();
        if (!e.edit(world, b)) throw new IllegalStateException("map edits refused: " + e.lastError());
        b.free();
        return world;
    }

    private static final class Builder {
        final CommandBuffer b;
        Builder(CommandBuffer b) { this.b = b; }
        void fill(int x0, int y0, int z0, int x1, int y1, int z1, int id) { b.fill(Math.min(x0, x1), Math.min(y0, y1), Math.min(z0, z1), Math.max(x0, x1), Math.max(y0, y1), Math.max(z0, z1), id | AstrattoNative.SET_RAW); }
        void set(int x, int y, int z, int id) { b.blockSetRaw(x, y, z, id); }
        void ground(int x0, int z0, int x1, int z1, int id) { fill(x0, FLOOR - 1, z0, x1, FLOOR - 1, z1, id); }

        void all() {
            fill(0, 0, 0, SX - 1, FLOOR - 3, SZ - 1, STONE);
            fill(0, FLOOR - 2, 0, SX - 1, FLOOR - 2, SZ - 1, DIRT);
            fill(0, FLOOR - 1, 0, SX - 1, FLOOR - 1, SZ - 1, GRASS);
            // a low boundary wall round the whole map
            for (int[] w : new int[][]{{0, 0, SX - 1, 0}, {0, SZ - 1, SX - 1, SZ - 1}, {0, 0, 0, SZ - 1}, {SX - 1, 0, SX - 1, SZ - 1}})
                fill(w[0], FLOOR, w[1], w[2], FLOOR + 1, w[3], STONE);
            debugZone();
            combatLane();
            skillLab();
            agility();
            yard();
            economyBay();
            roads();
            wilds();
            farm();
            graveyard();
            for (Jails.Cell cell : Jails.CELLS) {
                fill(cell.wallX(), FLOOR, cell.frontZ(), cell.wallX(), FLOOR + 2, cell.backZ(), BRICK);
                fill(cell.wallX(), FLOOR, cell.frontZ(), cell.endX(), FLOOR + 2, cell.frontZ(), FENCE);
                fill(cell.gateX(), FLOOR, cell.frontZ(), cell.gateX(), FLOOR + DOOR_HEIGHT - 1, cell.frontZ(), LOCKED_DOOR);
                Piece slab = cell.slab();
                fill(slab.x0(), FLOOR, slab.z0(), slab.x1(), FLOOR, slab.z1(), BED);
            }
            // furniture: the collision cells of the pieces (the game draws them)
            for (Piece p : concat(FURNITURE, GRAVEYARD_PIECES)) {
                int id = switch (p.kind) { case "CRATE" -> CRATE; case "BOOKCASE" -> BOOKCASE; case "BED" -> BED; case "TARGET" -> TARGET; case "HEADSTONE" -> HEADSTONE; default -> TABLE; };
                fill(p.x0, FLOOR, p.z0, p.x1, FLOOR + p.h - 1, p.z1, id);
            }
        }

        void debugZone() {
            ground(54, 54, 74, 74, DEBUG_FLOOR);
            // the terminal plinth and four lamp posts
            fill(DEBUG_TERMINAL[0] - 1, FLOOR, DEBUG_TERMINAL[1] - 1, DEBUG_TERMINAL[0] + 1, FLOOR, DEBUG_TERMINAL[1], MARK_YELLOW);
            for (int[] p : new int[][]{{55, 55}, {73, 55}, {55, 73}, {73, 73}}) lampPost(p[0], p[1]);
        }

        void combatLane() {
            ground(6, 42, 44, 66, SAND);
            // simple cover
            fill(26, FLOOR, 50, 29, FLOOR + 1, 50, STONE);
            fill(33, FLOOR, 56, 33, FLOOR + 1, 60, STONE);
            // archery: two targets (furniture) on a backstop, distance stripes at 10, 20 and 30 m
            fill(TARGET_X - 1, FLOOR, 53, TARGET_X - 1, FLOOR + 3, 64, BRICK);
            for (int d : new int[]{10, 20, 30}) fill(TARGET_X + d, FLOOR - 1, 54, TARGET_X + d, FLOOR - 1, 64, MARK_YELLOW);
            // dummies stand on stone pads; the equipment cabinet against a wall
            for (int[] d : DUMMIES) set(d[0], FLOOR - 1, d[1], STONE);
            fill(CABINET[0] - 1, FLOOR, CABINET[1] - 1, CABINET[0] + 1, FLOOR + 2, CABINET[1] - 1, PLANKS);
            // the hostile pen: a fence with a gate on its north side
            int x0 = 8, z0 = 70, x1 = 34, z1 = 88;
            fill(x0, FLOOR, z0, x1, FLOOR, z0, FENCE); fill(x0, FLOOR, z1, x1, FLOOR, z1, FENCE);
            fill(x0, FLOOR, z0, x0, FLOOR, z1, FENCE); fill(x1, FLOOR, z0, x1, FLOOR, z1, FENCE);
            fill(20, FLOOR, z0, 22, FLOOR, z0, AIR);
            ground(x0 + 1, z0 + 1, x1 - 1, z1 - 1, DIRT);
            fill(16, FLOOR, 76, 17, FLOOR + 1, 76, STONE);
            fill(26, FLOOR, 82, 26, FLOOR + 1, 84, STONE);
        }

        void skillLab() {
            fill(ALTAR[0], FLOOR, ALTAR[1], ALTAR[0], FLOOR, ALTAR[1], TABLE);
            // the interaction shed: plank walls on beams, a door, a locked store room, a roof
            int x0 = SHED_X0, z0 = SHED_Z0, x1 = SHED_X1, z1 = SHED_Z1, top = FLOOR + 3;
            ground(x0, z0, x1, z1, PLANKS);
            walls(x0, z0, x1, z1, FLOOR, top, PLANKS);
            for (int[] c : new int[][]{{x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}}) fill(c[0], FLOOR, c[1], c[0], top, c[1], BEAM);
            fill(x0, top + 1, z0, x1, top + 1, z1, ROOF);
            // doors are two blocks tall (a 1.8 m figure passes under), the wall closes over them
            for (int[] d : SHED_DOOR) fill(d[0], FLOOR, d[1], d[0], FLOOR + DOOR_HEIGHT - 1, d[1], DOOR);
            fill(57, FLOOR, z0, 57, top, z1, PLANKS);
            for (int[] d : STORE_DOOR) fill(d[0], FLOOR, d[1], d[0], FLOOR + DOOR_HEIGHT - 1, d[1], LOCKED_DOOR);
            fill(51, FLOOR + 1, z0, 53, FLOOR + 2, z0, GLASS);
            fill(x0, FLOOR + 1, 15, x0, FLOOR + 2, 17, GLASS);
            set(53, top, 15, LAMP);
            set(60, top, 17, LAMP);
            // workstations in a row: each an object on its own cell (the furnace's model carries its own chimney)
            set(FURNACE_AT[0], FLOOR, FURNACE_AT[1], FURNACE);
            set(ANVIL_AT[0], FLOOR, ANVIL_AT[1], ANVIL);
            set(RANGE_AT[0], FLOOR, RANGE_AT[1], RANGE);
            set(CRAFT_AT[0], FLOOR, CRAFT_AT[1], CRAFT_TABLE);
            set(SAW_AT[0], FLOOR, SAW_AT[1], SAWBENCH);
            ground(63, 25, 73, 27, COBBLE);
            ground(BUILD_X0, BUILD_Z0, BUILD_X1, BUILD_Z1, BARE_PATCH);
            for (int[] c : new int[][]{{BUILD_X0 - 1, BUILD_Z0 - 1}, {BUILD_X1 + 1, BUILD_Z0 - 1}, {BUILD_X0 - 1, BUILD_Z1 + 1}, {BUILD_X1 + 1, BUILD_Z1 + 1}}) set(c[0], FLOOR, c[1], MARK_GREEN);
            // gathering nodes
            for (int[] t : TREES) tree(t[0], t[1], LOG, LEAVES, 4);
            for (int[] t : OAKS) tree(t[0], t[1], OAK_LOG, OAK_LEAVES, 5);
            for (int[] t : WILLOWS) tree(t[0], t[1], WILLOW_LOG, WILLOW_LEAVES, 4);
            for (int[] r : COPPER) set(r[0], FLOOR, r[1], COPPER_ROCK);
            for (int[] r : TIN) set(r[0], FLOOR, r[1], TIN_ROCK);
            for (int[] r : IRON) set(r[0], FLOOR, r[1], IRON_ROCK);
            for (int[] r : COALS) set(r[0], FLOOR, r[1], COAL_ROCK);
            ground(46, 30, 62, 37, DIRT);
            for (int[] r : COPPER) set(r[0], FLOOR, r[1], COPPER_ROCK);
            pond(POND_X0, POND_Z0, POND_X1, POND_Z1);
            ground(65, 32, 79, 34, DIRT);
            for (int[] p : HERBS) set(p[0], FLOOR, p[1], HERB_PATCH);
            for (int[] p : FLAXES) set(p[0], FLOOR, p[1], FLAX_PATCH);
            for (int[] p : BERRIES) set(p[0], FLOOR, p[1], BERRY_BUSH);
        }

        void agility() {
            // start platform
            ground(114, 6, 121, 10, COBBLE);
            // a pit of water with a log beam across it
            fill(113, 2, 12, 121, FLOOR - 1, 22, AIR);
            fill(113, 2, 12, 121, 2, 22, SAND);
            fill(113, 3, 12, 121, 3, 22, WATER);
            fill(117, FLOOR - 1, 12, 117, FLOOR - 1, 22, LOG);
            // A two-block practice ledge: the same geometry-derived traversal used throughout the world.
            fill(113, FLOOR, AG_WALL_Z, 121, FLOOR + 1, AG_WALL_Z, BRICK);
            fill(112, FLOOR, AG_WALL_Z - 1, 112, FLOOR + 2, AG_WALL_Z + 1, BRICK);
            fill(122, FLOOR, AG_WALL_Z - 1, 122, FLOOR + 2, AG_WALL_Z + 1, BRICK);
            // stepping stones over water
            fill(113, 2, 33, 121, FLOOR - 1, 43, AIR);
            fill(113, 2, 33, 121, 2, 43, SAND);
            fill(113, 3, 33, 121, 3, 43, WATER);
            for (int z = 34; z <= 42; z += 2) set(117 + ((z / 2) % 2 == 0 ? 0 : 1), FLOOR - 1, z, STONE);
            ground(114, 44, 121, 46, COBBLE);
            // course fences so it is walked in order
            fill(112, FLOOR, 6, 112, FLOOR, 46, FENCE);
            fill(122, FLOOR, 6, 122, FLOOR, 46, FENCE);
            fill(112, FLOOR, AG_WALL_Z - 1, 112, FLOOR + 2, AG_WALL_Z + 1, BRICK);
            fill(122, FLOOR, AG_WALL_Z - 1, 122, FLOOR + 2, AG_WALL_Z + 1, BRICK);
            fill(113, FLOOR, 4, 121, FLOOR, 4, AIR);
        }

        void yard() {
            // the town wall along z 46 with a gate and a climbable section (the Agility shortcut)
            fill(82, FLOOR, 46, 126, FLOOR + 2, 46, STONE);
            fill(97, FLOOR, 46, 100, FLOOR + 2, 46, AIR);
            set(SHORTCUT[0], FLOOR + 2, SHORTCUT[1], MARK_GREEN);
            fill(82, FLOOR, 46, 82, FLOOR + 2, 100, STONE);
            fill(82, FLOOR, 66, 82, FLOOR + 2, 70, AIR);
            fill(82, FLOOR, 94, 82, FLOOR + 2, 98, AIR);
            // the square and its well
            ground(88, 64, 120, 84, COBBLE);
            fill(WELL[0] - 1, FLOOR, WELL[1] - 1, WELL[0] + 1, FLOOR, WELL[1] + 1, STONE);
            set(WELL[0], FLOOR, WELL[1], WATER);
            // store A (brick), Chancery A (brick), two houses (plaster on beams)
            building(SHOP_A_X0, SHOP_A_Z0, SHOP_A_X1, SHOP_A_Z1, BRICK, new int[]{90, SHOP_A_Z1}, 'z');
            building(CH_A_X0, CH_A_Z0, CH_A_X1, CH_A_Z1, BRICK, new int[]{113, CH_A_Z1}, 'z');
            building(HOUSE1_X0, HOUSE1_Z0, HOUSE1_X1, HOUSE1_Z1, PLASTER, new int[]{89, HOUSE1_Z0}, 'z');
            building(HOUSE2_X0, HOUSE2_Z0, HOUSE2_X1, HOUSE2_Z1, PLASTER, new int[]{103, HOUSE2_Z0}, 'z');
            // the market stall (its counter is furniture)
            for (int dx : new int[]{-1, 1}) fill(STALL[0] + dx, FLOOR + 1, STALL[1] + 1, STALL[0] + dx, FLOOR + 2, STALL[1] + 1, BEAM);
            fill(STALL[0] - 1, FLOOR + 3, STALL[1], STALL[0] + 1, FLOOR + 3, STALL[1] + 1, ROOF);
            // occluders for perception tests
            fill(108, FLOOR, 76, 112, FLOOR + 2, 76, BRICK);
            fill(88, FLOOR, 76, 88, FLOOR + 2, 80, BRICK);
            for (float[] p : PATROL) set((int) p[0], FLOOR - 1, (int) p[1], MARK_RED);
            lampPost(95, 70); lampPost(115, 78);
            // the jurisdiction markers: red posts on Alpha's edge
            for (int z = 50; z < 100; z += 8) { fill(83, FLOOR, z, 83, FLOOR + 2, z, BEAM); set(83, FLOOR + 3, z, MARK_RED); }
        }

        void economyBay() {
            ground(14, 96, 80, 124, GRASS);
            building(SHOP_B_X0, SHOP_B_Z0, SHOP_B_X1, SHOP_B_Z1, PLASTER, new int[]{SHOP_B_X1, 108}, 'x');
            building(CH_B_X0, CH_B_Z0, CH_B_X1, CH_B_Z1, BRICK, new int[]{35, CH_B_Z0}, 'z');
            building(CONTACT_X0, CONTACT_Z0, CONTACT_X1, CONTACT_Z1, PLASTER, new int[]{48, CONTACT_Z0}, 'z');
            // the quest kiosk: an open hut on four posts (its table is furniture)
            ground(KIOSK_X0, KIOSK_Z0, KIOSK_X1, KIOSK_Z1, PLANKS);
            for (int[] c : new int[][]{{KIOSK_X0, KIOSK_Z0}, {KIOSK_X1, KIOSK_Z0}, {KIOSK_X0, KIOSK_Z1}, {KIOSK_X1, KIOSK_Z1}}) fill(c[0], FLOOR, c[1], c[0], FLOOR + 2, c[1], BEAM);
            fill(KIOSK_X0, FLOOR + 3, KIOSK_Z0, KIOSK_X1, FLOOR + 3, KIOSK_Z1, ROOF);
            // blue posts on Beta's edge
            for (int x = 6; x < 82; x += 8) { if (x >= 26 && x <= 30) continue; fill(x, FLOOR, 93, x, FLOOR + 2, 93, BEAM); set(x, FLOOR + 3, 93, MARK_BLUE); }
            lampPost(30, 100); lampPost(55, 100);
        }

        void roads() {
            // the caravan road: store A south to the bay, west to store B
            ground(90, 63, 92, 96, COBBLE);
            ground(28, 95, 92, 97, COBBLE);
            ground(27, 97, 29, 109, COBBLE);
            // walks from the debug zone to each area
            ground(63, 28, 65, 54, COBBLE);
            ground(44, 63, 54, 65, COBBLE);
            ground(74, 63, 82, 65, COBBLE);
            ground(63, 74, 65, 95, COBBLE);
            ground(98, 40, 99, 46, COBBLE);
        }

        /** The wild wood: trees, leafy cover, a pond to drink at, a rock den. */
        void wilds() {
            for (int[] t : WILD_TREES) tree(t[0], t[1], (t[0] + t[1]) % 2 == 0 ? OAK_LOG : LOG, (t[0] + t[1]) % 2 == 0 ? OAK_LEAVES : LEAVES, 4 + (t[0] * 7 + t[1]) % 3);
            for (int[] b : WILD_BUSHES) fill(b[0], FLOOR, b[1], b[0] + 1, FLOOR, b[1], LEAVES);
            pond(WILD_POND_X0, WILD_POND_Z0, WILD_POND_X1, WILD_POND_Z1);
            // the den: a rock shelter open to the south-east
            fill(DEN_X0, FLOOR, DEN_Z0, DEN_X1, FLOOR + 2, DEN_Z0, STONE);
            fill(DEN_X0, FLOOR, DEN_Z0, DEN_X0, FLOOR + 2, DEN_Z1, STONE);
            fill(DEN_X0, FLOOR + 3, DEN_Z0, DEN_X1, FLOOR + 3, DEN_Z1, STONE);
            fill(DEN_X1, FLOOR + 2, DEN_Z0, DEN_X1, FLOOR + 2, DEN_Z1, STONE);
            ground(DEN_X0 + 1, DEN_Z0 + 1, DEN_X1, DEN_Z1, DIRT);
        }

        /** The farm: a fenced paddock with a gate, a barn with nest boxes, a duck pond, a trough. */
        void farm() {
            ground(FARM_X0 + 1, FARM_Z0 + 1, FARM_X1 - 1, FARM_Z1 - 1, GRASS);
            walls(FARM_X0, FARM_Z0, FARM_X1, FARM_Z1, FLOOR, FLOOR, FENCE);
            for (int[] d : FARM_GATE) fill(d[0], FLOOR, d[1], d[0], FLOOR + DOOR_HEIGHT - 1, d[1], DOOR);
            ground(FARM_GATE[0][0], 97, FARM_GATE[1][0], FARM_Z0 - 1, COBBLE);
            // Barn entrance is two blocks wide; animal and human movers share its physical door.
            int top = FLOOR + 3;
            fill(BARN_X0, FLOOR, BARN_Z0, BARN_X1, top, BARN_Z0, PLANKS);
            fill((BARN_X0+BARN_X1)/2,FLOOR,BARN_Z0,(BARN_X0+BARN_X1)/2+1,FLOOR+1,BARN_Z0,AIR);
            fill(BARN_X0, FLOOR, BARN_Z1, BARN_X1, top, BARN_Z1, PLANKS);
            fill(BARN_X0, FLOOR, BARN_Z0, BARN_X0, top, BARN_Z1, PLANKS);
            fill(BARN_X1, FLOOR, BARN_Z0, BARN_X1, top, BARN_Z1, PLANKS);
            for (int[] c : new int[][]{{BARN_X0, BARN_Z0}, {BARN_X1, BARN_Z0}, {BARN_X0, BARN_Z1}, {BARN_X1, BARN_Z1}}) fill(c[0], FLOOR, c[1], c[0], top, c[1], BEAM);
            fill(BARN_X0, top + 1, BARN_Z0, BARN_X1, top + 1, BARN_Z1, ROOF);
            ground(BARN_X0 + 1, BARN_Z0, BARN_X1 - 1, BARN_Z1 - 1, DIRT);
            pond(DUCK_POND_X0, DUCK_POND_Z0, DUCK_POND_X1, DUCK_POND_Z1);
            lampPost(FARM_GATE[0][0] - 2, FARM_Z0 - 1);
        }

        void walls(int x0, int z0, int x1, int z1, int y0, int y1, int id) {
            fill(x0, y0, z0, x1, y1, z0, id); fill(x0, y0, z1, x1, y1, z1, id);
            fill(x0, y0, z0, x0, y1, z1, id); fill(x1, y0, z0, x1, y1, z1, id);
        }

        /** A box building with a floor, walls, corner beams, a roof, a lamp, and a doorway (2 wide, 2 tall) along an axis. */
        /** The graveyard: a low fence with a gate, a dirt path to the undertaker's hut (the headstones are furniture). */
        void graveyard() {
            int x0 = GRAVE_X0, z0 = GRAVE_Z0, x1 = GRAVE_X1, z1 = GRAVE_Z1;
            fill(x0, FLOOR, z0, x1, FLOOR, z0, FENCE); fill(x0, FLOOR, z1, x1, FLOOR, z1, FENCE);
            fill(x0, FLOOR, z0, x0, FLOOR, z1, FENCE); fill(x1, FLOOR, z0, x1, FLOOR, z1, FENCE);
            fill(GRAVE_GATE[0], FLOOR, z0, GRAVE_GATE[0] + 1, FLOOR, z0, AIR);
            ground(GRAVE_GATE[0], z0, GRAVE_GATE[0] + 1, 97, DIRT);
            ground(GRAVE_GATE[0] + 2, 96, UNDERTAKER_X0 - 1, 97, DIRT);
            // a bare grave's mound in front of each stone
            for (int[] h : HEADSTONES) ground(h[0], h[1] - 1, h[0], h[1] - 1, BARE_PATCH);
            building(UNDERTAKER_X0, UNDERTAKER_Z0, UNDERTAKER_X1, UNDERTAKER_Z1, PLASTER, new int[]{UNDERTAKER_X0, 96}, 'x');
            lampPost(118, 94);
        }

        void building(int x0, int z0, int x1, int z1, int wall, int[] door, char axis) {
            int top = FLOOR + 3;
            ground(x0, z0, x1, z1, PLANKS);
            walls(x0, z0, x1, z1, FLOOR, top, wall);
            for (int[] c : new int[][]{{x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}}) fill(c[0], FLOOR, c[1], c[0], top, c[1], BEAM);
            fill(x0, top + 1, z0, x1, top + 1, z1, ROOF);
            if (axis == 'z') { fill(door[0], FLOOR, door[1], door[0] + 1, FLOOR + 1, door[1], AIR); fill(door[0], FLOOR + 2, door[1], door[0] + 1, FLOOR + 2, door[1], BEAM); }
            else { fill(door[0], FLOOR, door[1], door[0], FLOOR + 1, door[1] + 1, AIR); fill(door[0], FLOOR + 2, door[1], door[0], FLOOR + 2, door[1] + 1, BEAM); }
            // windows on the long walls
            int mx = (x0 + x1) / 2, mz = (z0 + z1) / 2;
            if (axis == 'z') { fill(x0, FLOOR + 1, mz - 1, x0, FLOOR + 2, mz, GLASS); fill(x1, FLOOR + 1, mz - 1, x1, FLOOR + 2, mz, GLASS); }
            else { fill(mx - 1, FLOOR + 1, z0, mx, FLOOR + 2, z0, GLASS); fill(mx - 1, FLOOR + 1, z1, mx, FLOOR + 2, z1, GLASS); }
            set(mx, top, mz, LAMP);
        }

        void tree(int x, int z, int log, int leaves, int height) {
            fill(x - 2, FLOOR + height - 1, z - 2, x + 2, FLOOR + height + 1, z + 2, leaves);
            fill(x - 1, FLOOR + height + 2, z - 1, x + 1, FLOOR + height + 2, z + 1, leaves);
            for (int[] c : new int[][]{{-2, -2}, {2, -2}, {-2, 2}, {2, 2}}) set(x + c[0], FLOOR + height + 1, z + c[1], AIR);
            fill(x, FLOOR, z, x, FLOOR + height, z, log);
        }

        void pond(int x0, int z0, int x1, int z1) {
            fill(x0, 2, z0, x1, FLOOR - 1, z1, AIR);
            fill(x0, 2, z0, x1, 2, z1, SAND);
            fill(x0, 3, z0, x1, 3, z1, WATER);
            fill(x0 - 1, FLOOR - 1, z0 - 1, x1 + 1, FLOOR - 1, z0 - 1, SAND);
            fill(x0 - 1, FLOOR - 1, z1 + 1, x1 + 1, FLOOR - 1, z1 + 1, SAND);
        }

        void lampPost(int x, int z) {
            fill(x, FLOOR, z, x, FLOOR + 2, z, BEAM);
            set(x, FLOOR + 3, z, LAMP);
        }
    }

    /** The cells a tree occupies above its stump (for felling and regrowing): its log and leaf blocks. */
    public static int treeHeight(int logBlock) { return logBlock == OAK_LOG ? 5 : 4; }
}
