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

    /**
     * The devmap's size (0.2.0, gamefixes: the size the real map will be): 192 x 192 blocks of overworld - 12 x 12 chunks
     * of 16, 36,864 blocks of ground - and 100 high. The dungeons are dug in the rock beyond its east edge.
     */
    public static final int SX = 192, SY = 100, SZ = 192, FLOOR = 4;
    /** Doors and doorways are two blocks (2 m) tall. */
    public static final int DOOR_HEIGHT = 2;

    /** The two synthetic jurisdictions and the unpoliced wilds (GDD 4.2: two or more jurisdiction ids). */
    public static final String ALPHA = "DevCity", BETA = "DevTown", WILDS = "WILDS";
    /** The kingdom of 0.2.0 (gamefixes): DevPalace, the Roman city in the east of the map. */
    public static final String PALACE = "DevPalace";
    /** Whether a point is in the dungeons (beyond the map's edge, where they are dug). */
    public static boolean inDungeon(double x, double z) { return x >= SX; }

    public static String jurisdiction(double x, double z) {
        if (x >= SX) return WILDS;   // the dungeons are nobody's to police
        String palace = Expanse.jurisdiction(x, z);
        if (palace != null) return palace;
        if (x >= 82 && x < 128 && z >= 46 && z < 128) return ALPHA;
        if (z >= 92 && z < 128 && x < 82) return BETA;
        return WILDS;
    }
    /** The watch's round in each kingdom (DevTown keeps none: its lawman holds his chancery). */
    public static float[][] patrol(String jurisdiction) { return PALACE.equals(jurisdiction) ? Expanse.PATROL : PATROL; }

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
    /** The Bank of ASHENWRATH (gamefixes), DevTown: its walls, its doorway in the north wall, the counter across the hall, where the teller stands. */
    public static final int BANK_X0 = 66, BANK_Z0 = 104, BANK_X1 = 78, BANK_Z1 = 116, BANK_COUNTER_Z = 110;
    public static final int[][] BANK_DOOR = {{71, BANK_Z0}, {72, BANK_Z0}};
    public static final float[] BANK_TELLER = {72.0f, 111.4f};
    // animals (GDD 3.9): a wild wood in the north-west and a farm south of the houses
    public static final int WILD_X0 = 3, WILD_Z0 = 3, WILD_X1 = 44, WILD_Z1 = 40;
    public static final int WILD_POND_X0 = 22, WILD_POND_Z0 = 17, WILD_POND_X1 = 28, WILD_POND_Z1 = 22;
    /** The brook running south out of the wild pond (0.1.8: a river for a river fisherman), and its two rod spots. */
    public static final int STREAM_X0 = 25, STREAM_X1 = 26, STREAM_Z0 = 23, STREAM_Z1 = 44;
    public static final float[][] STREAM_SPOTS = {{25.5f, 29.5f}, {26.5f, 39.5f}};
    /**
     * 0.1.9 (gamefixes: the devmap's missing objects, its people's needs first): what the new residents' work wanted
     * and did not have - two more cage spots in the pond's shore shallows for the shellfish gatherer (one spot, half
     * the day under the tide, was a day's idling), and a second supported face at the quarry beside the first.
     */
    public static final float[][] MORE_CRAB_SPOTS = {{87.5f, 38.5f}, {104.5f, 31.5f}};
    public static final int[] QUARRY_FACE_2 = {95, 19};
    public static final int[][] WILD_TREES = {{8, 26}, {14, 34}, {34, 8}, {38, 30}, {30, 36}, {18, 8}, {40, 18}};
    public static final int[][] WILD_BUSHES = {{12, 18}, {13, 19}, {32, 26}, {33, 26}, {20, 30}, {36, 14}, {6, 14}, {27, 9}};
    /** The den: a rock shelter in the wood's corner (the bear's and the lion's lie-up). */
    public static final int DEN_X0 = 5, DEN_Z0 = 4, DEN_X1 = 10, DEN_Z1 = 8;
    /** Monster lairs (gamefixes): the goblins' camp in the wood's north-east corner; the imps nest on the interaction shed's roof (the bats roost in the den). */
    public static final float[] GOBLIN_CAMP = {38.5f, 10.5f}, IMP_NEST = {55.5f, 15.5f}, BAT_ROOST = {38.5f, 31.5f}, SPIDER_WEB = {14.5f, 40.5f};
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
    /** (0.2.2) The unfinished casket the dead buy their things back from: beside the path's end, before the hut. */
    public static final float[] CASKET = {117.0f, 98.9f};
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
            new Piece("BED", 91, 92, 91, 93, 1, 0, rpg.Ids.MERCHANT_A), new Piece("BED", 92, 92, 92, 93, 1, 0, rpg.Ids.WORKER),
            new Piece("BED", 93, 92, 93, 93, 1, 0, rpg.Ids.CARTER),          // house 1 (heads to the back wall)
            new Piece("BED", 105, 92, 105, 93, 1, 0, rpg.Ids.VILLAGER), new Piece("BED", 106, 92, 106, 93, 1, 0, 0),
            new Piece("BED", 107, 92, 107, 93, 1, 0, 0),                     // house 2 (two spare beds anyone may use)
            new Piece("BED", 110, 53, 110, 54, 1, 2, rpg.Ids.LAWMAN_A)};     // the lawman's cot in chancery A (head to its north wall)
    /** The graveyard's pieces (built after every older fixture, so their ids hold): the undertaker's counter and cot, the stones. */
    public static final Piece[] GRAVEYARD_PIECES = {
            // the counter stands along the south wall, clear of the doorway (gamefixes: it used to fill the space just
            // inside the door, so whoever came in was pinned between it and the swung leaves)
            new Piece("COUNTER", 120, 99, 122, 99, 1, 0, rpg.Ids.UNDERTAKER), new Piece("BED", 124, 95, 124, 96, 1, 2, rpg.Ids.UNDERTAKER),
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

    public static final Piece[] SUPPORT_BEDS = {
        new Piece("BED",106,92,106,93,1,0,rpg.Ids.CARPENTER_CITY), new Piece("BED",107,92,107,93,1,0,rpg.Ids.CARPENTER_TOWN),
        new Piece("BED",86,133,86,134,1,2,rpg.Ids.CARPENTER_WOOD), new Piece("BED",179,43,179,44,1,2,rpg.Ids.CARPENTER_PALACE),
        new Piece("BED",88,133,88,134,1,2,rpg.Ids.WOODCUTTER_CITY), new Piece("BED",90,133,90,134,1,2,rpg.Ids.WOODCUTTER_TOWN),
        new Piece("BED",92,133,92,134,1,2,rpg.Ids.MINER_CITY), new Piece("BED",94,133,94,134,1,2,rpg.Ids.MINER_PALACE),
        new Piece("BED",85,137,86,137,1,3,rpg.Ids.TRADER_CITY), new Piece("BED",182,43,182,44,1,2,rpg.Ids.TRADER_PALACE)
    };

    public static float[] bedOf(long owner) {
        for (Piece p : concat(SUPPORT_BEDS, concat(concat(FURNITURE, GRAVEYARD_PIECES), Expanse.PIECES))) if (p.kind.equals("BED") && p.owner == owner) return new float[]{(p.x0 + p.x1 + 1) / 2f, (p.z0 + p.z1 + 1) / 2f};
        return null;
    }

    /** Area signs: label, x, z. */
    public static final Object[][] SIGNS = {
            {"DEBUG / CONTROL ZONE", 64.5f, 62.5f}, {"MOVEMENT / COMBAT LANE", 30.5f, 44.5f}, {"HOSTILE PEN", 21.5f, 69.5f},
            {"ARCHERY RANGE", 14.5f, 59.5f}, {"INTERACTION SHED", 55.5f, 24f}, {"WORKSTATIONS", 68.5f, 28.5f}, {"BUILD SPOT", 77.5f, 29.5f},
            {"MINING ROCKS", 54.5f, 38.5f}, {"TREE GROVE", 96.5f, 18.5f}, {"FISHING POND", 96.5f, 42f}, {"PATCHES", 72.5f, 36.5f},
            {"AGILITY COURSE", 117.5f, 5.5f}, {"NPC / SOCIAL YARD  (DevCity)", 100.5f, 64.5f}, {"STORE A", 91.5f, 63.5f},
            {"CHANCERY A", 114.5f, 63.5f}, {"STATE / ECONOMY BAY  (DevTown)", 40.5f, 98.5f}, {"STORE B", 27.5f, 106.5f},
            {"CHANCERY B", 35.5f, 111.5f}, {"QUEST KIOSK", 61.5f, 99f}, {"BANK OF GAMEWORLD", 72f, 102.5f},
            {"DEVPALACE", 128.5f, 70.5f}, {"THE SOUTHERN WOOD", 64.5f, 130.5f}, {"THE LAKE", 159.5f, 146.5f}, {"MINING HILLS", 140.5f, 131.5f}};

    /** Builds the devmap into a new engine world (lit) and returns its handle. */
    public static long build(NativeEngine e) {
        if (!e.blockTable(Blocks.engineDefs())) throw new IllegalStateException("block table refused: " + e.lastError());
        long world = e.createWorld(DungeonLayout.WORLD_X, SY, SZ, STONE, null, null, 0);
        if (world == 0) throw new IllegalStateException("world refused: " + e.lastError());
        CommandBuffer b = new CommandBuffer(1 << 16);
        BuildingKit.reset();
        new Builder(b).all();
        b.fill(164,FLOOR+3,18,169,FLOOR+3,24,STONE_BRICKS);
        for(int i=0;i<4;i++)for(int x=164;x<=166;x++)b.blockSet(x,FLOOR+i,14+i,STAIRS);
        b.fill(162,FLOOR,20,162,FLOOR+4,20,STONE_BRICKS);
        for(int y=FLOOR;y<=FLOOR+4;y++)b.blockSet(163,y,20,LADDER);
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
            // the new ground (0.2.0): DevPalace, the southern wood, the lake country, the meadow, the lodge
            new Expanse.Builder(b).all();
            debugZone();
            combatLane();
            skillLab();
            agility();
            yard();
            economyBay();
            roads();
            floors();
            wilds();
            dungeons();
            farm();
            graveyard();
            for (Jails.Cell cell : Jails.CELLS) {
                int wallMaterial=cell.jurisdiction().equals(ALPHA)?PLASTER:cell.jurisdiction().equals(PALACE)?LIMESTONE_BRICKS:BRICK;
                fill(cell.wallX(), FLOOR, cell.frontZ(), cell.wallX(), FLOOR + 2, cell.backZ(), !cell.jurisdiction().equals(BETA)?FENCE:wallMaterial);
                fill(cell.endX(), FLOOR, cell.frontZ(), cell.endX(), FLOOR + 2, cell.backZ(), wallMaterial);
                fill(cell.wallX(), FLOOR, cell.backZ(), cell.endX(), FLOOR + 2, cell.backZ(), wallMaterial);
                fill(cell.wallX(), FLOOR + 3, cell.frontZ(), cell.endX(), FLOOR + 3, cell.backZ(), wallMaterial);
                fill(cell.wallX(), FLOOR, cell.frontZ(), cell.endX(), FLOOR + 2, cell.frontZ(), FENCE);
                fill(cell.gateX(), FLOOR, cell.frontZ(), cell.gateX(), FLOOR + DOOR_HEIGHT - 1, cell.frontZ(), LOCKED_DOOR);
                Piece slab = cell.slab();
                fill(slab.x0(), FLOOR, slab.z0(), slab.x1(), FLOOR, slab.z1(), BED);
            }
            // furniture: the collision cells of the pieces (the game draws them)
            for (Piece p : concat(concat(FURNITURE, GRAVEYARD_PIECES), Expanse.PIECES)) {
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
            // simple cover: weathered sandstone, as a sandy range would have
            fill(26, FLOOR, 50, 29, FLOOR + 1, 50, SANDSTONE);
            fill(33, FLOOR, 56, 33, FLOOR + 1, 60, RED_SANDSTONE);
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
            ground(ALTAR[0] - 1, ALTAR[1] - 1, ALTAR[0] + 1, ALTAR[1] + 1, MARBLE);          // the altar stands on a marble dais
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
            // what the locked store keeps: smithing stock in blocks, stacked at its far end
            set(61, FLOOR, 21, IRON_BLOCK); set(60, FLOOR, 21, COPPER_BLOCK); set(61, FLOOR, 20, COAL_BLOCK); set(61, FLOOR + 1, 21, GOLD_BLOCK);
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
            ground(114, 44, 121, 45, COBBLE);   // up to the town wall (the shortcut is climbed from it), not under it
            // course fences so it is walked in order
            fill(112, FLOOR, 6, 112, FLOOR, 46, FENCE);
            fill(122, FLOOR, 6, 122, FLOOR, 46, FENCE);
            fill(112, FLOOR, AG_WALL_Z - 1, 112, FLOOR + 2, AG_WALL_Z + 1, BRICK);
            fill(122, FLOOR, AG_WALL_Z - 1, 122, FLOOR + 2, AG_WALL_Z + 1, BRICK);
            fill(113, FLOOR, 4, 121, FLOOR, 4, AIR);
        }

        void yard() {
            // the town wall along z 46 with a gate and a climbable section (the Agility shortcut): dressed stone bricks
            fill(82, FLOOR, 46, 126, FLOOR + 2, 46, STONE_BRICKS);
            fill(97, FLOOR, 46, 100, FLOOR + 2, 46, AIR);
            set(SHORTCUT[0], FLOOR + 2, SHORTCUT[1], MARK_GREEN);
            fill(82, FLOOR, 46, 82, FLOOR + 2, 100, STONE_BRICKS);
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
            // DevTown builds in pale limestone, its chancery lit through blue-stained panes
            building(CH_B_X0, CH_B_Z0, CH_B_X1, CH_B_Z1, LIMESTONE_BRICKS, new int[]{35, CH_B_Z0}, 'z', LIGHT_BLUE_GLASS);
            building(CONTACT_X0, CONTACT_Z0, CONTACT_X1, CONTACT_Z1, PLASTER, new int[]{48, CONTACT_Z0}, 'z');
            ground(CONTACT_X0 + 2, CONTACT_Z0 + 3, CONTACT_X1 - 2, CONTACT_Z1 - 2, BLACK_CARPET);
            // the quest kiosk: an open hut on four posts (its table is furniture)
            ground(KIOSK_X0, KIOSK_Z0, KIOSK_X1, KIOSK_Z1, PLANKS);
            for (int[] c : new int[][]{{KIOSK_X0, KIOSK_Z0}, {KIOSK_X1, KIOSK_Z0}, {KIOSK_X0, KIOSK_Z1}, {KIOSK_X1, KIOSK_Z1}}) fill(c[0], FLOOR, c[1], c[0], FLOOR + 2, c[1], BIRCH_LOG);
            fill(KIOSK_X0, FLOOR + 3, KIOSK_Z0, KIOSK_X1, FLOOR + 3, KIOSK_Z1, ROOF);
            // blue posts on Beta's edge
            for (int x = 6; x < 82; x += 8) { if (x >= 26 && x <= 30) continue; fill(x, FLOOR, 93, x, FLOOR + 2, 93, BEAM); set(x, FLOOR + 3, 93, MARK_BLUE); }
            lampPost(30, 100); lampPost(55, 100);
            bank();
        }

        /**
         * The Bank of ASHENWRATH: a stone-brick hall with a marble floor, entered from the road through a door in its north
         * wall; a counter runs across it, the teller's place behind and the strongroom's wall at the back.
         */
        void bank() {
            building(BANK_X0, BANK_Z0, BANK_X1, BANK_Z1, STONE_BRICKS, new int[]{BANK_DOOR[0][0], BANK_Z0}, 'z', WHITE_GLASS);
            ground(BANK_X0 + 1, BANK_Z0 + 1, BANK_X1 - 1, BANK_Z1 - 1, MARBLE);
            ground(BANK_DOOR[0][0], BANK_Z0 + 1, BANK_DOOR[1][0], BANK_COUNTER_Z - 1, RED_CARPET);
            ground(BANK_DOOR[0][0], 98, BANK_DOOR[1][0], BANK_Z0 - 1, COBBLE);
            // the strongroom: a gold-banded wall across the back, its iron door shut
            fill(BANK_X0 + 1, FLOOR, BANK_Z1 - 2, BANK_X1 - 1, FLOOR + 2, BANK_Z1 - 2, STONE_BRICKS);
            fill(BANK_X0 + 4, FLOOR + 2, BANK_Z1 - 2, BANK_X1 - 4, FLOOR + 2, BANK_Z1 - 2, GOLD_BLOCK);
            fill(71, FLOOR, BANK_Z1 - 2, 72, FLOOR + 1, BANK_Z1 - 2, IRON_BLOCK);
            lampPost(BANK_DOOR[0][0] - 2, BANK_Z0 - 2); lampPost(BANK_DOOR[1][0] + 2, BANK_Z0 - 2);
        }

        /**
         * The buildings' floors, laid again after the roads (the caravan road was paved straight through the first house's
         * floor), and their furnishing: a red runner up the chancery to the lawman's desk, rugs in the houses.
         */
        void floors() {
            for (int[] b : new int[][]{{SHOP_A_X0, SHOP_A_Z0, SHOP_A_X1, SHOP_A_Z1}, {CH_A_X0, CH_A_Z0, CH_A_X1, CH_A_Z1}, {HOUSE1_X0, HOUSE1_Z0, HOUSE1_X1, HOUSE1_Z1}, {HOUSE2_X0, HOUSE2_Z0, HOUSE2_X1, HOUSE2_Z1}})
                ground(b[0] + 1, b[1] + 1, b[2] - 1, b[3] - 1, PLANKS);
            ground(113, CH_A_Z0 + 2, 114, CH_A_Z1 - 1, RED_CARPET);
            ground(HOUSE1_X0 + 2, HOUSE1_Z0 + 3, HOUSE1_X1 - 2, HOUSE1_Z1 - 3, BROWN_CARPET);
            ground(HOUSE2_X0 + 2, HOUSE2_Z0 + 3, HOUSE2_X1 - 2, HOUSE2_Z1 - 3, WHITE_CARPET);
        }

        void roads() {
            // the caravan road: store A south to the bay, west to store B - round the houses, through the gap between
            // them (gamefixes: it ran in at house 1's front wall and out through its back one)
            ground(90, 63, 92, 85, COBBLE);
            ground(93, 84, 98, 85, COBBLE);
            ground(96, 85, 98, 97, COBBLE);
            ground(28, 95, 98, 97, COBBLE);
            // a doorstep from the square to each house's door
            ground(HOUSE1_X0 + 3, HOUSE1_Z0 - 1, HOUSE1_X0 + 4, HOUSE1_Z0 - 1, COBBLE);
            ground(HOUSE2_X0 + 3, HOUSE2_Z0 - 1, HOUSE2_X0 + 4, HOUSE2_Z0 - 1, COBBLE);
            ground(27, 97, 29, 109, COBBLE);
            // walks from the debug zone to each area
            ground(63, 28, 65, 54, COBBLE);
            ground(44, 63, 54, 65, COBBLE);
            ground(74, 66, 87, 68, COBBLE);   // through the west gate (it ran into the wall beside it)
            ground(63, 74, 65, 95, COBBLE);
            ground(98, 40, 99, 46, COBBLE);
        }

        /** The wild wood: trees, leafy cover, a pond to drink at, a rock den. */
        void wilds() {
            for (int[] t : WILD_TREES) tree(t[0], t[1], (t[0] + t[1]) % 2 == 0 ? OAK_LOG : LOG, (t[0] + t[1]) % 2 == 0 ? OAK_LEAVES : LEAVES, 4 + (t[0] * 7 + t[1]) % 3);
            for (int[] b : WILD_BUSHES) fill(b[0], FLOOR, b[1], b[0] + 1, FLOOR, b[1], LEAVES);
            pond(WILD_POND_X0, WILD_POND_Z0, WILD_POND_X1, WILD_POND_Z1);
            // its brook, two wide and a block deep, out of the south bank to a pool at its foot
            fill(STREAM_X0, 2, STREAM_Z0, STREAM_X1, FLOOR - 1, STREAM_Z1, AIR);
            fill(STREAM_X0, 2, STREAM_Z0, STREAM_X1, 2, STREAM_Z1, SAND);
            fill(STREAM_X0, 3, STREAM_Z0, STREAM_X1, 3, STREAM_Z1, WATER);
            pond(STREAM_X0 - 2, STREAM_Z1 - 1, STREAM_X1 + 2, STREAM_Z1 + 2);
            // the den: a granite shelter open to the south-east
            fill(DEN_X0, FLOOR, DEN_Z0, DEN_X1, FLOOR + 2, DEN_Z0, GRANITE);
            fill(DEN_X0, FLOOR, DEN_Z0, DEN_X0, FLOOR + 2, DEN_Z1, GRANITE);
            fill(DEN_X0, FLOOR + 3, DEN_Z0, DEN_X1, FLOOR + 3, DEN_Z1, GRANITE);
            fill(DEN_X1, FLOOR + 2, DEN_Z0, DEN_X1, FLOOR + 2, DEN_Z1, GRANITE);
            ground(DEN_X0 + 1, DEN_Z0 + 1, DEN_X1, DEN_Z1, DIRT);
            // the wild pond's banks: clay on the dry side, mud where the animals come down to drink
            ground(WILD_POND_X0 - 1, WILD_POND_Z0, WILD_POND_X0 - 1, WILD_POND_Z1, CLAY);
            ground(WILD_POND_X1 + 1, WILD_POND_Z0, WILD_POND_X1 + 1, WILD_POND_Z1, MUD);
            goblinCamp();
        }

        /**
         * The dungeons (gamefixes): beyond the overworld's edge the land rises in a sheer, grassed cliff of rock, and
         * inside the rock each dungeon is carved from its plan (DungeonLayout) - floors and walls in its theme's stone,
         * a roof over every room, lamps set in the walls, fire pits and sunken pools, pillars, braziers and fallen rock.
         * Nothing joins them to the overworld or to each other: their entrances and exits are the only ways in and out.
         */
        void dungeons() {
            int top = DungeonLayout.ROCK_TOP;
            fill(SX, 0, 0, DungeonLayout.WORLD_X - 1, top - 2, SZ - 1, STONE);
            fill(SX, top - 1, 0, DungeonLayout.WORLD_X - 1, top - 1, SZ - 1, GRASS);
            fill(SX, FLOOR - 1, 0, SX, top - 2, SZ - 1, GRANITE);                     // the cliff's face, seen from the overworld
            // the face stays sheer (nobody climbs it from the border wall); its skyline is broken by crags of rock rising
            // in uneven steps above the top, grassed over, so it is not a ruler's line
            for (int z = 0; z < SZ; z++) {
                int n = (z / 3) * 374761393; n = (n ^ (n >>> 13)) * 1274126177; n ^= n >>> 16;
                int depth = 2 + Math.floorMod(n, 6), rise = Math.floorMod(n >>> 5, 4);
                for (int k = 0; k < depth; k++) {
                    int h = top - 1 + Math.max(0, rise - Math.abs(k - depth / 2)) ;
                    if (h <= top - 1) continue;
                    fill(SX + 1 + k, top - 1, z, SX + 1 + k, h - 1, z, k % 3 == 0 ? GRANITE : STONE);
                    set(SX + 1 + k, h, z, GRASS);
                }
            }
            for (DungeonLayout l : DungeonLayout.all()) {
                int floor = switch (l.theme) { case CAVE -> STONE; case WARREN -> DIRT; case CRYPT -> COBBLE; case HALL -> GRANITE; };
                int wall = switch (l.theme) { case CAVE -> STONE; case WARREN -> MUD; case CRYPT -> STONE_BRICKS; case HALL -> GRANITE; };
                int roof = switch (l.theme) { case CAVE, HALL -> STONE; case WARREN -> DIRT; case CRYPT -> STONE_BRICKS; };
                // walls: the rock round every open cell faced in the theme's stone
                for (int x = 0; x < l.w; x++) for (int z = 0; z < l.d; z++) {
                    int wx = l.worldX(x), wz = l.worldZ(z);
                    if (l.open[x][z]) {
                        int h = l.height[x][z];
                        set(wx, FLOOR - 1, wz, floor);
                        fill(wx, FLOOR, wz, wx, FLOOR + h - 1, wz, AIR);
                        set(wx, FLOOR + h, wz, roof);
                        continue;
                    }
                    int near = 0;
                    for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                        int nx = x + dx, nz = z + dz;
                        if (nx >= 0 && nz >= 0 && nx < l.w && nz < l.d && l.open[nx][nz]) near = Math.max(near, l.height[nx][nz]);
                    }
                    if (near > 0) fill(wx, FLOOR, wz, wx, FLOOR + near, wz, wall);
                }
                for (DungeonLayout.Spot s : l.spots) {
                    int wx = l.worldX(s.x()), wz = l.worldZ(s.z());
                    switch (s.what()) {
                        case "lamp" -> { }   // a torch on the wall there (drawn and lit by the renderer: Gfx.dungeonLights)
                        case "embers" -> set(wx, FLOOR, wz, EMBERS);
                        case "pool" -> { set(wx, FLOOR - 1, wz, WATER); set(wx, FLOOR - 2, wz, WATER); }
                        case "pillar" -> fill(wx, FLOOR, wz, wx + 1, FLOOR + 6, wz + 1, GRANITE);
                        case "brazier" -> { set(wx, FLOOR, wz, COBBLE); set(wx, FLOOR + 1, wz, EMBERS); }
                        case "boulder" -> set(wx, FLOOR, wz, GRANITE);
                        default -> { }
                    }
                }
            }
        }

        /**
         * The goblins' camp (GDD 3.14: huts, fire pit, totem, loot pile): two low hide huts, a ring of stones round a fire
         * pit, a bone-fence of stakes and a totem pole with a skull on top - the camp's marks, so it can be read before it
         * is walked into. Trampled earth underfoot.
         */
        void goblinCamp() {
            int cx = (int) GOBLIN_CAMP[0], cz = (int) GOBLIN_CAMP[1];
            ground(cx - 6, cz - 6, cx + 6, cz + 5, DIRT);
            ground(cx - 1, cz - 1, cx + 1, cz + 1, BARE_PATCH);
            for (int[] s : new int[][]{{-2, 0}, {2, 0}, {0, -2}, {0, 2}, {-1, -2}, {1, 2}}) set(cx + s[0], FLOOR, cz + s[1], COBBLE);
            // the huts: hide over a frame of poles, each open to the fire
            for (int[] h : new int[][]{{cx - 5, cz - 5}, {cx + 3, cz - 5}}) {
                fill(h[0], FLOOR, h[1], h[0] + 2, FLOOR + 1, h[1], BROWN_CARPET);
                fill(h[0], FLOOR, h[1], h[0], FLOOR + 1, h[1] + 2, BROWN_CARPET);
                fill(h[0] + 2, FLOOR, h[1], h[0] + 2, FLOOR + 1, h[1] + 2, BROWN_CARPET);
                fill(h[0], FLOOR + 2, h[1], h[0] + 2, FLOOR + 2, h[1] + 2, HAY_BALE);
            }
            // stakes round the camp's south side, a gap to walk in by; the totem pole beside the way in
            for (int x = cx - 5; x <= cx + 5; x += 2) if (Math.abs(x - cx) > 1) set(x, FLOOR, cz + 5, BIRCH_LOG);
            fill(cx + 2, FLOOR, cz + 4, cx + 2, FLOOR + 2, cz + 4, OAK_LOG);
            set(cx + 2, FLOOR + 3, cz + 4, MARBLE);
        }

        /** The farm: a fenced paddock with a gate, a barn with nest boxes, a duck pond, a trough. */
        void farm() {
            ground(FARM_X0 + 1, FARM_Z0 + 1, FARM_X1 - 1, FARM_Z1 - 1, GRASS);
            walls(FARM_X0, FARM_Z0, FARM_X1, FARM_Z1, FLOOR, FLOOR, FENCE);
            // (0.2.2) a fence-gate, one block high like the fence it hangs in
            for (int[] d : FARM_GATE) set(d[0], FLOOR, d[1], DOOR);
            ground(FARM_GATE[0][0], 97, FARM_GATE[1][0], FARM_Z0 - 1, COBBLE);
            // Barn entrance is two blocks wide; animal and human movers share its physical door.
            int top = FLOOR + 3;
            fill(BARN_X0, FLOOR, BARN_Z0, BARN_X1, top, BARN_Z0, PLANKS);
            fill((BARN_X0+BARN_X1)/2,FLOOR,BARN_Z0,(BARN_X0+BARN_X1)/2+1,FLOOR+1,BARN_Z0,AIR);
            fill(BARN_X0, FLOOR, BARN_Z1, BARN_X1, top, BARN_Z1, PLANKS);
            fill(BARN_X0, FLOOR, BARN_Z0, BARN_X0, top, BARN_Z1, PLANKS);
            fill(BARN_X1, FLOOR, BARN_Z0, BARN_X1, top, BARN_Z1, PLANKS);
            for (int[] c : new int[][]{{BARN_X0, BARN_Z0}, {BARN_X1, BARN_Z0}, {BARN_X0, BARN_Z1}, {BARN_X1, BARN_Z1}}) fill(c[0], FLOOR, c[1], c[0], top, c[1], SPRUCE_LOG);
            fill(BARN_X0, top + 1, BARN_Z0, BARN_X1, top + 1, BARN_Z1, ROOF);
            ground(BARN_X0 + 1, BARN_Z0, BARN_X1 - 1, BARN_Z1 - 1, DIRT);
            // the winter fodder, baled in the corner away from the nest boxes
            fill(BARN_X1 - 2, FLOOR, BARN_Z0 + 1, BARN_X1 - 1, FLOOR, BARN_Z0 + 2, HAY_BALE);
            set(BARN_X1 - 1, FLOOR + 1, BARN_Z0 + 1, HAY_BALE);
            pond(DUCK_POND_X0, DUCK_POND_Z0, DUCK_POND_X1, DUCK_POND_Z1);
            // the ducks have trodden its sides to mud
            ground(DUCK_POND_X0 - 1, DUCK_POND_Z0, DUCK_POND_X0 - 1, DUCK_POND_Z1, MUD);
            ground(DUCK_POND_X1 + 1, DUCK_POND_Z0, DUCK_POND_X1 + 1, DUCK_POND_Z1, MUD);
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
            building(UNDERTAKER_X0, UNDERTAKER_Z0, UNDERTAKER_X1, UNDERTAKER_Z1, PLASTER, new int[]{UNDERTAKER_X0, 96}, 'x', WHITE_GLASS);
            lampPost(118, 94);
        }

        void building(int x0, int z0, int x1, int z1, int wall, int[] door, char axis) { building(x0, z0, x1, z1, wall, door, axis, GLASS); }
        void building(int x0, int z0, int x1, int z1, int wall, int[] door, char axis, int pane) {
            int top = FLOOR + 3;
            ground(x0, z0, x1, z1, PLANKS);
            // (0.2.1) raised to the standard: two-faced walls (brick or stone outside, plaster within; plaster outside,
            // planks within), the plan kept for the standard's checks
            BuildingKit.walls(this::set, x0, z0, x1, z1, FLOOR, top, wall);
            BuildingKit.plan(new BuildingKit.Plan("building at " + x0 + "," + z0, x0, z0, x1, z1, door[0], door[1], axis, top - FLOOR + 1));
            for (int[] c : new int[][]{{x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}}) fill(c[0], FLOOR, c[1], c[0], top, c[1], BEAM);
            fill(x0, top + 1, z0, x1, top + 1, z1, ROOF);
            if (axis == 'z') { fill(door[0], FLOOR, door[1], door[0] + 1, FLOOR + 1, door[1], AIR); fill(door[0], FLOOR + 2, door[1], door[0] + 1, FLOOR + 2, door[1], BEAM); }
            else { fill(door[0], FLOOR, door[1], door[0], FLOOR + 1, door[1] + 1, AIR); fill(door[0], FLOOR + 2, door[1], door[0], FLOOR + 2, door[1] + 1, BEAM); }
            // windows on the long walls
            int mx = (x0 + x1) / 2, mz = (z0 + z1) / 2;
            if (axis == 'z') { fill(x0, FLOOR + 1, mz - 1, x0, FLOOR + 2, mz, pane); fill(x1, FLOOR + 1, mz - 1, x1, FLOOR + 2, mz, pane); }
            else { fill(mx - 1, FLOOR + 1, z0, mx, FLOOR + 2, z0, pane); fill(mx - 1, FLOOR + 1, z1, mx, FLOOR + 2, z1, pane); }
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

}
