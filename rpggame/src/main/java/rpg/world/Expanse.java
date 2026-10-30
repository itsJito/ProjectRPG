package rpg.world;

import astratto.bridge.AstrattoNative;
import astratto.bridge.CommandBuffer;
import static rpg.world.Blocks.*;
import static rpg.world.DevMap.FLOOR;

/**
 * The devmap's new ground (0.2.0, gamefixes: the devmap grown to the real map's size - 192 x 192 blocks, 12 x 12
 * chunks, 100 high - with more forest, more skilling ground and a third kingdom). What was the whole map is now its
 * north-west; east of DevCity stands DevPalace, a walled Roman city - a paved grid of streets crossing at a marble
 * forum with its fountain and colonnades, the temple on its podium to the north, the emperor's palace beyond it, the
 * basilica where the praetor sits, the market hall, the baths and the houses of its citizens round their atria. South of
 * the old map runs the great southern wood; south of the palace the lake country - a lake with its beaches and spots,
 * the mining hills, a marble quarry, fields of herbs and flax; north of the palace a meadow. At the wood's edge a
 * forester's lodge houses six newcomers. Fixture positions are public, as DevMap's are, for the systems that place them.
 */
public final class Expanse {
    private Expanse() {}

    // ------------------------------------------------------------------ DevPalace
    public static final int PAL_X0 = 132, PAL_Z0 = 22, PAL_X1 = 188, PAL_Z1 = 126;
    /** The streets: the decumanus (west to east, from the west gate) and the cardo (north to south, to the south gate). */
    public static final int DEC_Z0 = 72, DEC_Z1 = 75, CARDO_X0 = 158, CARDO_X1 = 161;
    public static final int FORUM_X0 = 142, FORUM_Z0 = 80, FORUM_X1 = 176, FORUM_Z1 = 100;
    public static final int[] FOUNTAIN = {159, 90};
    public static final int TEMPLE_X0 = 150, TEMPLE_Z0 = 56, TEMPLE_X1 = 168, TEMPLE_Z1 = 69;
    public static final int PALACE_X0 = 148, PALACE_Z0 = 26, PALACE_X1 = 170, PALACE_Z1 = 50;
    public static final int BASILICA_X0 = 134, BASILICA_Z0 = 104, BASILICA_X1 = 154, BASILICA_Z1 = 122;
    public static final int MARKET_X0 = 178, MARKET_Z0 = 80, MARKET_X1 = 187, MARKET_Z1 = 100;
    public static final int BATHS_X0 = 164, BATHS_Z0 = 104, BATHS_X1 = 186, BATHS_Z1 = 122;
    /** The four houses: north-west, west, north-east, east of the temple. */
    public static final int[][] DOMUS = {{134, 26, 145, 38}, {134, 42, 145, 54}, {175, 26, 186, 38}, {175, 42, 186, 54}};
    /** Where the palace's people stand at their work. */
    public static final float[] THRONE = {159.5f, 29.5f}, PRAETOR = {144.5f, 116.0f}, MERCHANT_POST = {181.0f, 90.5f}, ALTAR = {159.5f, 59.5f},
            MEDICUS_POST = {172.5f, 108.5f}, SENATE_POST = {150.5f, 84.5f};
    /** The legionaries' round: the gates, the forum's corners, the palace steps. */
    public static final float[][] PATROL = {{134.5f, 73.5f}, {146.5f, 78.5f}, {159.5f, 52.5f}, {174.5f, 78.5f}, {174.5f, 102.5f}, {159.5f, 124.5f}, {146.5f, 102.5f}};

    // ------------------------------------------------------------------ the lands round it
    public static final int WOOD_X0 = 4, WOOD_Z0 = 132, WOOD_X1 = 124, WOOD_Z1 = 188;
    public static final int LAKE_X0 = 150, LAKE_Z0 = 150, LAKE_X1 = 176, LAKE_Z1 = 172;
    public static final int BROOK_X0 = 60, BROOK_X1 = 61, BROOK_Z0 = 132, BROOK_Z1 = 186;
    public static final int LODGE_X0 = 84, LODGE_Z0 = 132, LODGE_X1 = 98, LODGE_Z1 = 142;
    /** The southern wood's trees (x, z, species: 0 tree, 1 oak, 2 willow, 3 birch, 4 spruce, 5 jungle). */
    public static final int[][] WOOD_TREES = trees();
    public static final int[][] COPPER = {{136, 136}, {139, 135}}, TIN = {{136, 140}, {139, 141}}, IRON = {{142, 136}, {145, 137}}, COAL = {{142, 142}, {145, 143}}, GEMS = {{140, 146}};
    public static final int[] MARBLE_FACE = {182, 136}, MARBLE_FACE_2 = {184, 138};
    public static final float[][] LAKE_SPOTS = {{152.5f, 161.5f}, {174.5f, 160.5f}, {163.5f, 170.5f}};
    public static final float[][] BROOK_SPOTS = {{60.5f, 150.5f}, {61.5f, 172.5f}};
    public static final int[][] HERBS = {{136, 162}, {138, 162}, {140, 162}}, FLAXES = {{136, 168}, {138, 168}, {140, 168}}, BERRIES = {{136, 174}, {138, 174}, {142, 174}, {146, 8}, {150, 10}, {170, 8}};
    public static final int[][] MEADOW_TREES = {{140, 8}, {156, 12}, {176, 6}, {184, 14}};

    /** Furniture of the new buildings (collision cells; drawn as models): beds, counters, tables. */
    public static final DevMap.Piece[] PIECES = {
            // the emperor's bed in the palace's west chamber, the praetor's cot in the basilica
            new DevMap.Piece("BED", 151, 27, 151, 28, 1, 2, rpg.Ids.EMPEROR),
            new DevMap.Piece("BED", 152, 120, 152, 121, 1, 0, rpg.Ids.PRAETOR),
            new DevMap.Piece("COUNTER", 141, 114, 147, 114, 1, 2, rpg.Ids.PRAETOR),
            new DevMap.Piece("COUNTER", 180, 88, 180, 93, 1, 1, rpg.Ids.MACELLUM),
            new DevMap.Piece("TABLE", 168, 106, 169, 106, 1, 2, rpg.Ids.MEDICUS),
            // the houses' beds: four to a house
            new DevMap.Piece("BED", 136, 27, 136, 28, 1, 2, rpg.Ids.SENATOR), new DevMap.Piece("BED", 138, 27, 138, 28, 1, 2, rpg.Ids.CITIZEN_1),
            new DevMap.Piece("BED", 141, 27, 141, 28, 1, 2, rpg.Ids.CITIZEN_2), new DevMap.Piece("BED", 143, 27, 143, 28, 1, 2, rpg.Ids.PRIESTESS),
            new DevMap.Piece("BED", 136, 43, 136, 44, 1, 2, rpg.Ids.MEDICUS), new DevMap.Piece("BED", 138, 43, 138, 44, 1, 2, rpg.Ids.PHILOSOPHER),
            new DevMap.Piece("BED", 141, 43, 141, 44, 1, 2, rpg.Ids.STONEMASON), new DevMap.Piece("BED", 143, 43, 143, 44, 1, 2, rpg.Ids.CITIZEN_3),
            new DevMap.Piece("BED", 177, 27, 177, 28, 1, 2, rpg.Ids.MACELLUM), new DevMap.Piece("BED", 179, 27, 179, 28, 1, 2, rpg.Ids.CITIZEN_4),
            new DevMap.Piece("BED", 182, 27, 182, 28, 1, 2, rpg.Ids.FISHER_P), new DevMap.Piece("BED", 184, 27, 184, 28, 1, 2, rpg.Ids.HUNTRESS),
            new DevMap.Piece("BED", 177, 43, 177, 44, 1, 2, rpg.Ids.CENTURION), new DevMap.Piece("BED", 179, 43, 179, 44, 1, 2, 0),
            new DevMap.Piece("BED", 182, 43, 182, 44, 1, 2, 0), new DevMap.Piece("BED", 184, 43, 184, 44, 1, 2, 0),
            // the forester's lodge: a bed for each of the six newcomers
            new DevMap.Piece("BED", 86, 140, 86, 141, 1, 0, rpg.Ids.ARTHUR), new DevMap.Piece("BED", 88, 140, 88, 141, 1, 0, rpg.Ids.LANCELOT),
            new DevMap.Piece("BED", 90, 140, 90, 141, 1, 0, rpg.Ids.GAWAIN), new DevMap.Piece("BED", 92, 140, 92, 141, 1, 0, rpg.Ids.TRISTAN),
            new DevMap.Piece("BED", 94, 140, 94, 141, 1, 0, rpg.Ids.PERCIVAL), new DevMap.Piece("BED", 96, 140, 96, 141, 1, 0, rpg.Ids.MERLIN),
            new DevMap.Piece("TABLE", 90, 135, 93, 135, 1, 0, 0)};

    public static String jurisdiction(double x, double z) {
        return x >= PAL_X0 && x <= PAL_X1 && z >= PAL_Z0 && z <= PAL_Z1 ? DevMap.PALACE : null;
    }

    private static int[][] trees() {
        java.util.List<int[]> l = new java.util.ArrayList<>();
        java.util.Random r = new java.util.Random(0xF0E57L);
        for (int gx = WOOD_X0 + 4; gx < WOOD_X1 - 3; gx += 9) for (int gz = WOOD_Z0 + 5; gz < WOOD_Z1 - 3; gz += 9) {
            int x = gx + r.nextInt(5) - 2, z = gz + r.nextInt(5) - 2;
            if (Math.abs(x - BROOK_X0) < 4) continue;                                                  // the brook's banks
            if (x >= LODGE_X0 - 4 && x <= LODGE_X1 + 4 && z <= LODGE_Z1 + 5) continue;                 // the lodge's clearing
            if (x >= 20 && x <= 34 && z >= 150 && z <= 164) continue;                                   // a glade
            int species = r.nextInt(10) < 3 ? 1 : r.nextInt(10) < 3 ? 3 : r.nextInt(10) < 3 ? 4 : r.nextInt(10) < 2 ? 5 : r.nextInt(10) < 2 ? 2 : 0;
            l.add(new int[]{x, z, species});
        }
        return l.toArray(new int[0][]);
    }
    public static int logOf(int species) { return switch (species) { case 1 -> OAK_LOG; case 2 -> WILLOW_LOG; case 3 -> BIRCH_LOG; case 4 -> SPRUCE_LOG; case 5 -> JUNGLE_LOG; default -> LOG; }; }
    public static int leavesOf(int species) { return switch (species) { case 1 -> OAK_LEAVES; case 2 -> WILLOW_LEAVES; default -> LEAVES; }; }
    public static int heightOf(int x, int z, int species) { return (species == 4 ? 6 : species == 1 ? 5 : 4) + Math.floorMod(x * 7 + z, 2); }

    // ------------------------------------------------------------------ building it
    /** (Reeds: the flax plant's tall stalks stand in for them by the water.) */
    static final int FLAX_PATCH_REEDS = FLAX_PATCH;

    static final class Builder {
        final CommandBuffer b;
        Builder(CommandBuffer b) { this.b = b; }
        void fill(int x0, int y0, int z0, int x1, int y1, int z1, int id) { b.fill(Math.min(x0, x1), Math.min(y0, y1), Math.min(z0, z1), Math.max(x0, x1), Math.max(y0, y1), Math.max(z0, z1), id | AstrattoNative.SET_RAW); }
        void set(int x, int y, int z, int id) { b.blockSetRaw(x, y, z, id); }
        void ground(int x0, int z0, int x1, int z1, int id) { fill(x0, FLOOR - 1, z0, x1, FLOOR - 1, z1, id); }
        void walls(int x0, int z0, int x1, int z1, int y0, int y1, int id) {
            fill(x0, y0, z0, x1, y1, z0, id); fill(x0, y0, z1, x1, y1, z1, id); fill(x0, y0, z0, x0, y1, z1, id); fill(x1, y0, z0, x1, y1, z1, id);
        }
        /** A Roman column: a limestone base, a marble shaft, a capital wider than it. */
        void column(int x, int z, int y0, int h) {
            set(x, y0, z, LIMESTONE_BRICKS);
            fill(x, y0 + 1, z, x, y0 + h - 2, z, MARBLE);
            set(x, y0 + h - 1, z, LIMESTONE_BRICKS);
        }
        void lamp(int x, int z) { fill(x, FLOOR, z, x, FLOOR + 1, z, LIMESTONE_BRICKS); set(x, FLOOR + 2, z, LAMP); }
        // (0.2.2, gamefixes) DevPalace's greenery is clipped hedge, not loose leaves
        void cypress(int x, int z) { fill(x, FLOOR, z, x, FLOOR + 1, z, SPRUCE_LOG); fill(x, FLOOR + 2, z, x, FLOOR + 6, z, HEDGE); set(x, FLOOR + 7, z, HEDGE); }
        void tree(int x, int z, int log, int leaves, int height) {
            fill(x - 2, FLOOR + height - 1, z - 2, x + 2, FLOOR + height + 1, z + 2, leaves);
            fill(x - 1, FLOOR + height + 2, z - 1, x + 1, FLOOR + height + 2, z + 1, leaves);
            for (int[] c : new int[][]{{-2, -2}, {2, -2}, {-2, 2}, {2, 2}}) set(x + c[0], FLOOR + height + 1, z + c[1], AIR);
            fill(x, FLOOR, z, x, FLOOR + height, z, log);
        }
        void pool(int x0, int z0, int x1, int z1, int depth, int bottom) {
            fill(x0, FLOOR - depth - 1, z0, x1, FLOOR - 1, z1, AIR);
            fill(x0, FLOOR - depth - 1, z0, x1, FLOOR - depth - 1, z1, bottom);
            fill(x0, FLOOR - depth, z0, x1, FLOOR - 1, z1, WATER);
        }

        void all() {
            palace();
            southWood();
            lakeCountry();
            meadow();
            lodge();
            // the road from DevCity's square east to the palace's west gate, and the track south to the lodge
            ground(121, DEC_Z0, PAL_X0, DEC_Z1, STONE_BRICKS);
            ground(96, 98, 98, LODGE_Z0 - 1, COBBLE);
            // from the palace's south gate down to the lake
            ground(CARDO_X0, PAL_Z1, CARDO_X1, LAKE_Z0 - 2, COBBLE);
        }

        void palace() {
            // the ground inside the walls: paved where people walk, gardens between
            ground(PAL_X0, PAL_Z0, PAL_X1, PAL_Z1, GRASS);
            // the wall, its four corner towers, and its gates (west, south)
            walls(PAL_X0, PAL_Z0, PAL_X1, PAL_Z1, FLOOR, FLOOR + 3, LIMESTONE_BRICKS);
            fill(PAL_X0, FLOOR + 4, PAL_Z0, PAL_X1, FLOOR + 4, PAL_Z0, STONE_BRICKS); fill(PAL_X0, FLOOR + 4, PAL_Z1, PAL_X1, FLOOR + 4, PAL_Z1, STONE_BRICKS);
            fill(PAL_X0, FLOOR + 4, PAL_Z0, PAL_X0, FLOOR + 4, PAL_Z1, STONE_BRICKS); fill(PAL_X1, FLOOR + 4, PAL_Z0, PAL_X1, FLOOR + 4, PAL_Z1, STONE_BRICKS);
            for (int x = PAL_X0; x <= PAL_X1; x += 2) { set(x, FLOOR + 5, PAL_Z0, LIMESTONE_BRICKS); set(x, FLOOR + 5, PAL_Z1, LIMESTONE_BRICKS); }
            for (int z = PAL_Z0; z <= PAL_Z1; z += 2) { set(PAL_X0, FLOOR + 5, z, LIMESTONE_BRICKS); set(PAL_X1, FLOOR + 5, z, LIMESTONE_BRICKS); }
            for (int[] c : new int[][]{{PAL_X0, PAL_Z0}, {PAL_X1, PAL_Z0}, {PAL_X0, PAL_Z1}, {PAL_X1, PAL_Z1}}) {
                fill(c[0] - 1, FLOOR, c[1] - 1, c[0] + 1, FLOOR + 6, c[1] + 1, LIMESTONE_BRICKS);
                fill(c[0] - 1, FLOOR + 7, c[1] - 1, c[0] + 1, FLOOR + 7, c[1] + 1, ROOF);
            }
            gate(PAL_X0, DEC_Z0, true); gate(CARDO_X0, PAL_Z1, false);
            // the streets: the decumanus and the cardo, in dressed stone, with kerbs
            ground(PAL_X0, DEC_Z0, PAL_X1 - 1, DEC_Z1, STONE_BRICKS);
            ground(CARDO_X0, PAL_Z0 + 1, CARDO_X1, PAL_Z1, STONE_BRICKS);
            ground(PAL_X0 + 1, DEC_Z0 - 1, PAL_X1 - 1, DEC_Z0 - 1, LIMESTONE_BRICKS); ground(PAL_X0 + 1, DEC_Z1 + 1, PAL_X1 - 1, DEC_Z1 + 1, LIMESTONE_BRICKS);
            // lanes to the houses and the market
            // (0.2.1, gamefixes: DevPalace's houses opened onto the palace wall, their lanes ran through it) an alley either
            // side of the palace, from the houses' doors down to the decumanus
            ground(146, 27, 147, DEC_Z0 - 1, COBBLE); ground(171, 27, 174, DEC_Z0 - 1, COBBLE);
            forum(); temple(); imperialPalace(); basilica(); market(); baths();
            for (int[] d : DOMUS) domus(d[0], d[1], d[2], d[3]);
            // cypresses along the decumanus, lamps at the crossings
            for (int x = 136; x < PAL_X1 - 2; x += 6) { if (x >= CARDO_X0 - 2 && x <= CARDO_X1 + 2) continue; cypress(x, DEC_Z0 - 2); cypress(x, DEC_Z1 + 2); }
            for (int[] p : new int[][]{{CARDO_X0 - 1, DEC_Z0 - 1}, {CARDO_X1 + 1, DEC_Z0 - 1}, {CARDO_X0 - 1, DEC_Z1 + 1}, {CARDO_X1 + 1, DEC_Z1 + 1}, {PAL_X0 + 3, DEC_Z0 - 1}, {PAL_X0 + 3, DEC_Z1 + 1}, {CARDO_X0 - 1, PAL_Z1 - 3}, {CARDO_X1 + 1, PAL_Z1 - 3}})
                lamp(p[0], p[1]);
        }

        /** A gate: an arch through the wall, two storeys of dressed stone over it, flanking pillars. */
        void gate(int at, int from, boolean west) {
            if (west) {
                fill(at, FLOOR, from, at, FLOOR + 2, from + 3, AIR);
                fill(at - 1, FLOOR, from - 2, at + 1, FLOOR + 5, from - 1, LIMESTONE_BRICKS); fill(at - 1, FLOOR, from + 4, at + 1, FLOOR + 5, from + 5, LIMESTONE_BRICKS);
                fill(at - 1, FLOOR + 3, from - 2, at + 1, FLOOR + 5, from + 5, LIMESTONE_BRICKS);
                fill(at - 1, FLOOR, from, at + 1, FLOOR + 2, from + 3, AIR);
                fill(at - 1, FLOOR + 6, from - 2, at + 1, FLOOR + 6, from + 5, MARBLE);
            } else {
                int z = from;
                fill(at, FLOOR, z, at + 3, FLOOR + 2, z, AIR);
                fill(at - 2, FLOOR, z - 1, at - 1, FLOOR + 5, z + 1, LIMESTONE_BRICKS); fill(at + 4, FLOOR, z - 1, at + 5, FLOOR + 5, z + 1, LIMESTONE_BRICKS);
                fill(at - 2, FLOOR + 3, z - 1, at + 5, FLOOR + 5, z + 1, LIMESTONE_BRICKS);
                fill(at, FLOOR, z - 1, at + 3, FLOOR + 2, z + 1, AIR);
                fill(at - 2, FLOOR + 6, z - 1, at + 5, FLOOR + 6, z + 1, MARBLE);
            }
        }

        /** The forum: a marble square ringed by colonnades, the fountain at its heart. */
        void forum() {
            ground(FORUM_X0, FORUM_Z0, FORUM_X1, FORUM_Z1, MARBLE);
            for (int x = FORUM_X0; x <= FORUM_X1; x++) for (int z = FORUM_Z0; z <= FORUM_Z1; z++)
                if ((x + z) % 4 == 0 && x > FORUM_X0 + 1 && x < FORUM_X1 - 1 && z > FORUM_Z0 + 1 && z < FORUM_Z1 - 1) set(x, FLOOR - 1, z, LIMESTONE_BRICKS);
            ground(FORUM_X0, FORUM_Z0, FORUM_X1, FORUM_Z0, LIMESTONE_BRICKS); ground(FORUM_X0, FORUM_Z1, FORUM_X1, FORUM_Z1, LIMESTONE_BRICKS);
            // the colonnades along the north and south edges: columns every three, an entablature over them
            for (int z : new int[]{FORUM_Z0 + 1, FORUM_Z1 - 1}) {
                for (int x = FORUM_X0 + 1; x <= FORUM_X1 - 1; x += 3) { if (x >= CARDO_X0 - 1 && x <= CARDO_X1 + 1) continue; column(x, z, FLOOR, 5); }
                fill(FORUM_X0 + 1, FLOOR + 5, z, CARDO_X0 - 2, FLOOR + 5, z, LIMESTONE_BRICKS);
                fill(CARDO_X1 + 2, FLOOR + 5, z, FORUM_X1 - 1, FLOOR + 5, z, LIMESTONE_BRICKS);
            }
            // the fountain: a marble basin, its water, and a column in the middle
            int fx = FOUNTAIN[0], fz = FOUNTAIN[1];
            walls(fx - 2, fz - 2, fx + 3, fz + 3, FLOOR, FLOOR, MARBLE);
            pool(fx - 1, fz - 1, fx + 2, fz + 2, 1, MARBLE);
            fill(fx - 1, FLOOR - 1, fz - 1, fx + 2, FLOOR - 1, fz + 2, WATER);
            fill(fx, FLOOR, fz, fx + 1, FLOOR + 2, fz + 1, MARBLE);
            set(fx, FLOOR + 3, fz, GOLD_BLOCK);
            // statues on their plinths at the corners
            for (int[] c : new int[][]{{FORUM_X0 + 3, FORUM_Z0 + 4}, {FORUM_X1 - 3, FORUM_Z0 + 4}, {FORUM_X0 + 3, FORUM_Z1 - 4}, {FORUM_X1 - 3, FORUM_Z1 - 4}}) {
                set(c[0], FLOOR, c[1], LIMESTONE_BRICKS); fill(c[0], FLOOR + 1, c[1], c[0], FLOOR + 2, c[1], MARBLE);
            }
        }

        /** The temple: raised on a podium with steps to the south, a marble cella ringed by columns, a red-tiled pediment. */
        void temple() {
            int x0 = TEMPLE_X0, z0 = TEMPLE_Z0, x1 = TEMPLE_X1, z1 = TEMPLE_Z1;
            fill(x0, FLOOR, z0, x1, FLOOR, z1, LIMESTONE_BRICKS);                 // the podium, a step up
            fill(CARDO_X0 - 1, FLOOR, z1 + 1, CARDO_X1 + 1, FLOOR - 1, z1 + 1, LIMESTONE_BRICKS);
            int fy = FLOOR + 1;
            ground(x0, z0, x1, z1, LIMESTONE_BRICKS);
            // the cella
            walls(x0 + 3, z0 + 2, x1 - 3, z1 - 4, fy, fy + 4, MARBLE);
            fill(CARDO_X0, fy, z1 - 4, CARDO_X1, fy + 2, z1 - 4, AIR);
            fill(x0 + 4, FLOOR, z0 + 3, x1 - 4, FLOOR, z1 - 5, RED_CARPET);
            // the peristyle: columns round the podium's edge
            for (int x = x0; x <= x1; x += 3) { column(x, z0, fy, 5); if (x < CARDO_X0 - 1 || x > CARDO_X1 + 1) column(x, z1, fy, 5); }
            for (int z = z0 + 3; z < z1; z += 3) { column(x0, z, fy, 5); column(x1, z, fy, 5); }
            // the roof and its pediment, stepped in red tiles toward the ridge
            fill(x0, fy + 5, z0, x1, fy + 5, z1, LIMESTONE_BRICKS);
            int w = (x1 - x0) / 2;
            for (int k = 0; k <= w / 2 + 1; k++) fill(x0 + k * 2, fy + 6 + k, z0, x1 - k * 2, fy + 6 + k, z1, k == 0 ? MARBLE : ROOF);
            // the altar: a marble block crowned in gold
            fill((int) ALTAR[0] - 1, fy, (int) ALTAR[1] - 1, (int) ALTAR[0], fy, (int) ALTAR[1] - 1, MARBLE);
            set((int) ALTAR[0] - 1, fy + 1, (int) ALTAR[1] - 1, GOLD_BLOCK); set((int) ALTAR[0], fy + 1, (int) ALTAR[1] - 1, GOLD_BLOCK);
            set(x0 + 5, fy + 3, (z0 + z1) / 2, LAMP); set(x1 - 5, fy + 3, (z0 + z1) / 2, LAMP);
        }

        /** The emperor's palace: a long marble hall with a gold-trimmed cornice, columns down it, the throne at its head. */
        void imperialPalace() {
            int x0 = PALACE_X0, z0 = PALACE_Z0, x1 = PALACE_X1, z1 = PALACE_Z1;
            ground(x0, z0, x1, z1, MARBLE);
            walls(x0, z0, x1, z1, FLOOR, FLOOR + 5, MARBLE);
            BuildingKit.plan(new BuildingKit.Plan("imperial palace", x0, z0, x1, z1, CARDO_X0, z1, 'z', 6));
            fill(x0, FLOOR + 5, z0, x1, FLOOR + 5, z0, GOLD_BLOCK); fill(x0, FLOOR + 5, z1, x1, FLOOR + 5, z1, GOLD_BLOCK);
            fill(x0, FLOOR + 6, z0, x1, FLOOR + 6, z1, LIMESTONE_BRICKS);
            fill(x0 + 1, FLOOR + 7, z0 + 1, x1 - 1, FLOOR + 7, z1 - 1, ROOF);
            // the great door to the cardo, a portico of columns before it
            fill(CARDO_X0, FLOOR, z1, CARDO_X1, FLOOR + 3, z1, AIR);
            for (int x = CARDO_X0 - 4; x <= CARDO_X1 + 4; x += 2) if (x < CARDO_X0 || x > CARDO_X1) column(x, z1 + 2, FLOOR, 5);   // (0.2.1) none in the doorway
            fill(CARDO_X0 - 4, FLOOR + 5, z1 + 1, CARDO_X1 + 4, FLOOR + 5, z1 + 2, LIMESTONE_BRICKS);
            // the hall: a red runner to the throne, columns either side, windows of white glass
            ground(CARDO_X0, z0 + 2, CARDO_X1, z1 - 1, RED_CARPET);
            for (int z = z0 + 4; z < z1 - 2; z += 4) { column(CARDO_X0 - 3, z, FLOOR, 6); column(CARDO_X1 + 3, z, FLOOR, 6); }
            for (int z = z0 + 4; z < z1 - 2; z += 5) { fill(x0, FLOOR + 1, z, x0, FLOOR + 3, z + 1, WHITE_GLASS); fill(x1, FLOOR + 1, z, x1, FLOOR + 3, z + 1, WHITE_GLASS); }
            // the throne on its dais
            fill(CARDO_X0 - 1, FLOOR, z0 + 1, CARDO_X1 + 1, FLOOR, z0 + 2, MARBLE);
            fill((int) THRONE[0] - 1, FLOOR + 1, z0 + 1, (int) THRONE[0] + 1, FLOOR + 3, z0 + 1, GOLD_BLOCK);
            set((int) THRONE[0], FLOOR + 1, z0 + 1, RED_CARPET);
            // the emperor's chamber to the west, behind a wall with a doorway
            fill(x0 + 7, FLOOR, z0 + 1, x0 + 7, FLOOR + 4, z0 + 10, MARBLE);
            fill(x0 + 7, FLOOR, z0 + 6, x0 + 7, FLOOR + 1, z0 + 7, AIR);
            ground(x0 + 1, z0 + 1, x0 + 6, z0 + 10, RED_CARPET);
            for (int[] l : new int[][]{{CARDO_X0 - 1, z0 + 8}, {CARDO_X1 + 1, z0 + 8}, {CARDO_X0 - 1, z1 - 6}, {CARDO_X1 + 1, z1 - 6}, {x0 + 3, z0 + 5}}) set(l[0], FLOOR + 4, l[1], LAMP);
        }

        /** The basilica: the law's hall - limestone, a colonnaded front on the forum side, the praetor's bench and cell inside. */
        void basilica() {
            int x0 = BASILICA_X0, z0 = BASILICA_Z0, x1 = BASILICA_X1, z1 = BASILICA_Z1;
            ground(x0, z0, x1, z1, STONE_BRICKS);
            BuildingKit.walls(this::set, x0, z0, x1, z1, FLOOR, FLOOR + 4, LIMESTONE_BRICKS);
            BuildingKit.plan(new BuildingKit.Plan("basilica", x0, z0, x1, z1, 144, z0, 'z', 5));
            fill(x0, FLOOR + 5, z0, x1, FLOOR + 5, z1, ROOF);
            fill(143, FLOOR, z0, 146, FLOOR + 2, z0, AIR);
            for (int x = x0 + 1; x < x1; x += 3) if (x < 143 || x > 146) column(x, z0 - 2, FLOOR, 5);   // (0.2.1) none before the door
            fill(x0, FLOOR + 5, z0 - 2, x1, FLOOR + 5, z0 - 1, LIMESTONE_BRICKS);
            ground(x0, z0 - 2, x1, z0 - 1, LIMESTONE_BRICKS);
            ground(144, z0 + 1, 145, 113, RED_CARPET);
            for (int z = z0 + 3; z < z1 - 1; z += 5) { fill(x0, FLOOR + 1, z, x0, FLOOR + 2, z + 1, WHITE_GLASS); fill(x1, FLOOR + 1, z, x1, FLOOR + 2, z + 1, WHITE_GLASS); }
            set(144, FLOOR + 4, 110, LAMP); set(150, FLOOR + 4, 118, LAMP);
        }

        /** The market hall: a court open to the sky inside a portico, its stalls along the walls and a counter for the trader. */
        void market() {
            int x0 = MARKET_X0, z0 = MARKET_Z0, x1 = MARKET_X1, z1 = MARKET_Z1;
            ground(x0, z0, x1, z1, COBBLE);
            walls(x0, z0, x1, z1, FLOOR, FLOOR + 3, SANDSTONE);
            fill(x0, FLOOR, 88, x0, FLOOR + 2, 93, AIR);
            BuildingKit.plan(new BuildingKit.Plan("market hall", x0, z0, x1, z1, x0, 90, 'x', 4));
            for (int z = z0 + 2; z < z1; z += 3) column(x0 + 3, z, FLOOR, 4);
            fill(x0 + 1, FLOOR + 4, z0 + 1, x1 - 1, FLOOR + 4, z1 - 1, ROOF);
            fill(x0 + 4, FLOOR + 4, z0 + 3, x1 - 2, FLOOR + 4, z1 - 3, AIR);          // open to the sky over the court
            for (int z = z0 + 2; z < z1 - 1; z += 4) { fill(x1 - 1, FLOOR, z, x1 - 1, FLOOR, z + 1, CRATE); set(x1 - 1, FLOOR + 1, z, HAY_BALE); }
            set(x0 + 2, FLOOR + 3, 85, LAMP); set(x0 + 2, FLOOR + 3, 96, LAMP);
        }

        /** The baths: a sandstone hall round a great pool, columns along its sides. */
        void baths() {
            int x0 = BATHS_X0, z0 = BATHS_Z0, x1 = BATHS_X1, z1 = BATHS_Z1;
            ground(x0, z0, x1, z1, MARBLE);
            walls(x0, z0, x1, z1, FLOOR, FLOOR + 4, SANDSTONE);
            fill(x0, FLOOR + 5, z0, x1, FLOOR + 5, z1, ROOF);
            fill(173, FLOOR, z0, 176, FLOOR + 2, z0, AIR);
            BuildingKit.plan(new BuildingKit.Plan("baths", x0, z0, x1, z1, 174, z0, 'z', 5));
            pool(x0 + 4, z0 + 6, x1 - 4, z1 - 4, 1, MARBLE);
            fill(x0 + 3, FLOOR - 1, z0 + 5, x1 - 3, FLOOR - 1, z0 + 5, MARBLE);
            for (int x = x0 + 3; x <= x1 - 3; x += 4) { column(x, z0 + 5, FLOOR, 5); column(x, z1 - 2, FLOOR, 5); }
            for (int z = z0 + 3; z < z1 - 1; z += 5) { fill(x1, FLOOR + 1, z, x1, FLOOR + 3, z + 1, LIGHT_BLUE_GLASS); }
            set(x0 + 2, FLOOR + 4, z0 + 8, LAMP); set(x1 - 2, FLOOR + 4, z0 + 8, LAMP); set((x0 + x1) / 2, FLOOR + 4, z1 - 3, LAMP);
        }

        /** A house round its atrium: ochre-plastered walls on a red sandstone footing, a roof open over a pool in the middle. */
        void domus(int x0, int z0, int x1, int z1) {
            ground(x0, z0, x1, z1, PLANKS);
            walls(x0, z0, x1, z1, FLOOR, FLOOR, RED_SANDSTONE);
            // (0.2.1) to the building standard: plaster outside, planks within (two-faced), on the sandstone footing
            BuildingKit.walls(this::set, x0, z0, x1, z1, FLOOR + 1, FLOOR + 3, PLASTER);
            for (int[] c : new int[][]{{x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}}) fill(c[0], FLOOR, c[1], c[0], FLOOR + 3, c[1], LIMESTONE_BRICKS);
            fill(x0, FLOOR + 4, z0, x1, FLOOR + 4, z1, ROOF);
            int mx = (x0 + x1) / 2, mz = (z0 + z1) / 2;
            fill(mx - 1, FLOOR + 4, mz - 1, mx + 1, FLOOR + 4, mz + 1, AIR);
            pool(mx - 1, mz - 1, mx + 1, mz + 1, 1, MARBLE);
            ground(mx - 2, mz - 2, mx + 2, mz + 2, MARBLE);
            fill(mx - 1, FLOOR - 1, mz - 1, mx + 1, FLOOR - 1, mz + 1, WATER);
            // its doorway, on the side toward the lane
            boolean west = x0 < CARDO_X0;
            int dx = west ? x1 : x0;
            fill(dx, FLOOR, mz + 3 - 1, dx, FLOOR + 2, mz + 3, AIR);
            BuildingKit.plan(new BuildingKit.Plan("domus at " + x0 + "," + z0, x0, z0, x1, z1, dx, mz + 2, 'x', 4));
            fill(x0, FLOOR + 2, mz - 4, x0, FLOOR + 2, mz - 3, WHITE_GLASS); fill(x1, FLOOR + 2, mz - 4, x1, FLOOR + 2, mz - 3, WHITE_GLASS);
            ground(x0 + 1, z1 - 3, x1 - 1, z1 - 1, BROWN_CARPET);
            set(x0 + 2, FLOOR + 3, mz, LAMP); set(x1 - 2, FLOOR + 3, mz, LAMP);
        }

        /** The great southern wood: many trees of every kind, undergrowth, a glade, a brook running north to south. */
        void southWood() {
            ground(WOOD_X0, WOOD_Z0, WOOD_X1, WOOD_Z1, GRASS);
            for (int[] t : WOOD_TREES) tree(t[0], t[1], logOf(t[2]), leavesOf(t[2]), heightOf(t[0], t[1], t[2]));
            java.util.Random r = new java.util.Random(0xB05EL);
            for (int k = 0; k < 90; k++) {
                int x = WOOD_X0 + 2 + r.nextInt(WOOD_X1 - WOOD_X0 - 4), z = WOOD_Z0 + 2 + r.nextInt(WOOD_Z1 - WOOD_Z0 - 4);
                if (Math.abs(x - BROOK_X0) < 3 || x >= LODGE_X0 - 2 && x <= LODGE_X1 + 2 && z <= LODGE_Z1 + 2) continue;
                set(x, FLOOR, z, LEAVES);
            }
            // the brook: two wide, a block deep, banks of mud and sand
            fill(BROOK_X0, 2, BROOK_Z0, BROOK_X1, FLOOR - 1, BROOK_Z1, AIR);
            fill(BROOK_X0, 2, BROOK_Z0, BROOK_X1, 2, BROOK_Z1, SAND);
            fill(BROOK_X0, 3, BROOK_Z0, BROOK_X1, 3, BROOK_Z1, WATER);
            ground(BROOK_X0 - 1, BROOK_Z0, BROOK_X0 - 1, BROOK_Z1, MUD); ground(BROOK_X1 + 1, BROOK_Z0, BROOK_X1 + 1, BROOK_Z1, SAND);
            // a plank bridge where the track crosses it
            fill(BROOK_X0 - 1, FLOOR - 1, 158, BROOK_X1 + 1, FLOOR - 1, 160, PLANKS);
            ground(20, 150, 34, 164, BARE_PATCH);
            for (int x = 22; x <= 32; x += 5) set(x, FLOOR, 157, STUMP);
        }

        /** South of the palace: the lake and its beaches, the mining hills, the marble quarry, the fields. */
        void lakeCountry() {
            ground(130, 130, 188, 188, GRASS);
            // the lake: two deep in the middle, a block at its shallows, sand all round
            // (an uneven shore: an ellipse broken by a wandering edge, shallows round it, deep water in the middle)
            float cx = (LAKE_X0 + LAKE_X1) / 2f, cz = (LAKE_Z0 + LAKE_Z1) / 2f, rx = (LAKE_X1 - LAKE_X0) / 2f + 1, rz = (LAKE_Z1 - LAKE_Z0) / 2f + 1;
            for (int x = LAKE_X0 - 4; x <= LAKE_X1 + 4; x++) for (int z = LAKE_Z0 - 4; z <= LAKE_Z1 + 4; z++) {
                double a = Math.atan2(z - cz, x - cx);
                double edge = 1 + 0.16 * Math.sin(a * 3 + 0.7) + 0.09 * Math.sin(a * 7 + 2.1);
                double d = Math.hypot((x - cx) / rx, (z - cz) / rz) / edge;
                if (d < 1.0) {
                    int depth = d < 0.6 ? 2 : 1;
                    fill(x, FLOOR - depth - 1, z, x, FLOOR - 1, z, AIR);
                    set(x, FLOOR - depth - 1, z, d < 0.6 ? SAND : x % 3 == 0 ? CLAY : SAND);
                    fill(x, FLOOR - depth, z, x, FLOOR - 1, z, WATER);
                } else if (d < 1.22) set(x, FLOOR - 1, z, d < 1.12 ? SAND : (x + z) % 4 == 0 ? MUD : SAND);
            }
            // reeds and rocks along the shore
            for (int k = 0; k < 24; k++) {
                double a = k / 24.0 * Math.PI * 2;
                double edge = 1 + 0.16 * Math.sin(a * 3 + 0.7) + 0.09 * Math.sin(a * 7 + 2.1);
                int x = (int) Math.round(cx + Math.cos(a) * rx * edge * 1.12), z = (int) Math.round(cz + Math.sin(a) * rz * edge * 1.12);
                if (Math.abs(x - 162.5) < 3 && z < cz) continue;
                set(x, FLOOR, z, k % 3 == 0 ? STONE : FLAX_PATCH_REEDS);
            }
            // a jetty out over it
            fill(162, FLOOR - 1, LAKE_Z0 - 4, 163, FLOOR - 1, LAKE_Z0 + 6, PLANKS);
            for (int z = LAKE_Z0; z <= LAKE_Z0 + 6; z += 3) { set(161, FLOOR - 1, z, LOG); set(164, FLOOR - 1, z, LOG); }
            // the mining hills: stony ground, granite boulders, the ore rocks among them
            ground(133, 133, 147, 149, COBBLE);
            for (int[] k : new int[][]{{134, 145}, {146, 134}, {147, 147}, {143, 139}}) { fill(k[0], FLOOR, k[1], k[0] + 1, FLOOR + 1, k[1] + 1, GRANITE); set(k[0], FLOOR + 2, k[1], STONE); }
            for (int[] r : COPPER) set(r[0], FLOOR, r[1], COPPER_ROCK);
            for (int[] r : TIN) set(r[0], FLOOR, r[1], TIN_ROCK);
            for (int[] r : IRON) set(r[0], FLOOR, r[1], IRON_ROCK);
            for (int[] r : COAL) set(r[0], FLOOR, r[1], COAL_ROCK);
            walls(132, 132, 148, 150, FLOOR, FLOOR, GRANITE);
            fill(140, FLOOR, 150, 141, FLOOR, 150, AIR);
            // the marble quarry: a stepped cut into a white bluff
            fill(178, FLOOR, 132, 187, FLOOR + 4, 135, MARBLE);
            fill(180, FLOOR, 135, 186, FLOOR + 2, 139, MARBLE);
            fill(181, FLOOR, 136, 185, FLOOR + 1, 139, AIR);
            ground(178, 136, 187, 142, COBBLE);
            // the fields: herb, flax and berry rows on tilled earth
            ground(134, 160, 148, 178, DIRT);
            lamp(150, 150); lamp(176, 150);
        }

        /** North of the palace: a meadow of grass with a few trees and berry bushes. */
        void meadow() {
            ground(130, 2, 188, 18, GRASS);
            for (int[] t : MEADOW_TREES) tree(t[0], t[1], OAK_LOG, OAK_LEAVES, 5);
        }

        /** The forester's lodge at the wood's edge: a long timber house with six beds and a table. */
        void lodge() {
            int x0 = LODGE_X0, z0 = LODGE_Z0, x1 = LODGE_X1, z1 = LODGE_Z1;
            ground(x0, z0, x1, z1, PLANKS);
            walls(x0, z0, x1, z1, FLOOR, FLOOR + 2, SPRUCE_LOG);
            for (int[] c : new int[][]{{x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}}) fill(c[0], FLOOR, c[1], c[0], FLOOR + 3, c[1], OAK_LOG);
            fill(x0, FLOOR + 3, z0, x1, FLOOR + 3, z1, ROOF);
            fill(x0 + 1, FLOOR + 4, z0 + 1, x1 - 1, FLOOR + 4, z1 - 1, ROOF);
            fill(96, FLOOR, z0, 97, FLOOR + 1, z0, AIR);
            BuildingKit.plan(new BuildingKit.Plan("forester's lodge", x0, z0, x1, z1, 96, z0, 'z', 3));
            fill(x0, FLOOR + 1, z0 + 4, x0, FLOOR + 1, z0 + 5, GLASS); fill(x1, FLOOR + 1, z0 + 4, x1, FLOOR + 1, z0 + 5, GLASS);
            set((x0 + x1) / 2, FLOOR + 2, (z0 + z1) / 2, LAMP);
        }
    }
}
