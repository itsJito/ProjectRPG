package rpg.world;

import static rpg.world.Blocks.*;
import static rpg.world.DevMap.FLOOR;

import astratto.bridge.AstrattoNative;
import astratto.bridge.CommandBuffer;
import java.util.ArrayList;
import java.util.List;
import rpg.Ids;

/**
 * (0.3.8, gamefixes: a DevMansion, with a few new people who keep the estate and the property) A three-storey manor in a
 * clearing of the southern wood, west of the brook, fronting the track that crosses it: dressed stone below, timber
 * framing above, a steep slate roof between two chimney stacks, a porch, a walled front garden with an orchard and a
 * kitchen garden, a woodpile. Inside, nine rooms and a hall, every one furnished from the house furnishings
 * (Furnishings): on the ground floor the hall with its stair, the dining room, the drawing room, the kitchen and the
 * pantry; above them the library, the study, the master bedroom, the bathroom and a gallery; at the top the guest room,
 * the servants' room, the knight's room and the orchard keeper's room. The household that lives here is the Household
 * Baron who owns it, his Household Knight, a Cook, a Porter, a Firewood Cutter and an Orchard Keeper (GDD roles).
 *
 * <p>Everything is laid out from the constants below: the rooms (with their walls' finishes and floors), the furniture
 * (Fit), the loose crates and barrels, the hearths, the beds. Nothing is placed by hand elsewhere.
 */
public final class Mansion {
    private Mansion() {}

    public static final String NAME = "DevMansion";
    /** The estate (its wall), and the house (its outer walls), inclusive. */
    public static final int EST_X0 = 35, EST_Z0 = 133, EST_X1 = 58, EST_Z1 = 157;
    public static final int X0 = 36, Z0 = 136, X1 = 57, Z1 = 151;
    /** The storeys: the first cell above each floor's blocks (ground, first, second), the ceiling over the top one. */
    public static final int[] LEVELS = {FLOOR, FLOOR + 5, FLOOR + 10};
    public static final int TOP = FLOOR + 14, STOREY = 4;
    /** The partitions inside: walls at x = 43 and 50, and across at z = 142 (the hall runs front to back between them). */
    public static final int WEST = 43, EAST = 50, ACROSS = 142;
    /** The front door (south) and the back door (north). */
    public static final int[][] FRONT_DOOR = {{46, Z1}, {47, Z1}}, BACK_DOOR = {{46, Z0}, {47, Z0}};
    /** The stair flights: {x0, x1, the bottom step's z, the way it rises (dz), steps, the floor it starts on}. */
    public static final int[][] FLIGHTS = {{44, 46, 149, -1, 5, 0}, {47, 49, 137, 1, 5, 1}};
    /** The kitchen's range, the pantry's store, the woodpile, where deliveries are left. */
    public static final float[] RANGE_AT = {38.5f, 140.5f}, PANTRY_AT = {55.5f, 139.0f}, WOODPILE = {39.5f, 134.5f}, DELIVERY = {46.5f, 155.0f};
    /** The estate's apple trees (the Orchard Keeper's), and its gate in the front wall. */
    public static final int[][] ORCHARD = {{38, 155}, {42, 155}, {51, 155}, {55, 155}};
    public static final int[][] GATE = {{46, EST_Z1}, {47, EST_Z1}};
    /** Where the Household Knight walks the bounds. */
    public static final float[][] PATROL = {{46.5f, 155.5f}, {36.5f, 154.5f}, {36.0f, 134.5f}, {56.5f, 134.5f}, {57.5f, 154.5f}};

    /** A room: its cells (inclusive), its storey, its walls' finish inside, its floor, and its name. */
    public record Room(String name, int x0, int z0, int x1, int z1, int level, int finish, int floor) {
        public int y() { return LEVELS[level]; }
        public float cx() { return (x0 + x1 + 1) / 2f; }
        public float cz() { return (z0 + z1 + 1) / 2f; }
    }
    public static final Room[] ROOMS = {
            new Room("Entrance hall", 44, 137, 49, 150, 0, WAINSCOT, CHECKER_TILE),
            new Room("Dining room", 37, 143, 42, 150, 0, WALLPAPER_RED, PARQUET),
            new Room("Kitchen", 37, 137, 42, 141, 0, PLASTER, FLAGSTONE),
            new Room("Drawing room", 51, 143, 56, 150, 0, WALLPAPER_GREEN, PARQUET),
            new Room("Pantry", 51, 137, 56, 141, 0, PLASTER, FLAGSTONE),
            new Room("Gallery", 44, 137, 49, 150, 1, WAINSCOT, PARQUET),
            new Room("Library", 37, 143, 42, 150, 1, WAINSCOT, DARK_PLANKS),
            new Room("Study", 37, 137, 42, 141, 1, WALLPAPER_GREEN, PARQUET),
            new Room("Master bedroom", 51, 143, 56, 150, 1, WALLPAPER_RED, PARQUET),
            new Room("Bathroom", 51, 137, 56, 141, 1, WALLPAPER_BLUE, CHECKER_TILE),
            new Room("Upper landing", 44, 137, 49, 150, 2, WAINSCOT, PARQUET),
            new Room("Servants' room", 37, 143, 42, 150, 2, PLASTER, PLANKS),
            new Room("Keeper's room", 37, 137, 42, 141, 2, PLASTER, PLANKS),
            new Room("Guest room", 51, 143, 56, 150, 2, WALLPAPER_BLUE, PARQUET),
            new Room("Knight's room", 51, 137, 56, 141, 2, PLASTER, PLANKS)};

    /** The room at a cell on a storey, or null. */
    public static Room roomAt(int x, int z, int level) {
        for (Room r : ROOMS) if (r.level == level && x >= r.x0 && x <= r.x1 && z >= r.z0 && z <= r.z1) return r;
        return null;
    }
    public static boolean inside(double x, double z) { return x > X0 && x < X1 && z > Z0 && z < Z1; }
    public static boolean onEstate(double x, double z) { return x >= EST_X0 && x <= EST_X1 && z >= EST_Z0 && z <= EST_Z1; }
    /** Which storey a height is on (0 ground). */
    public static int level(float y) { return y >= LEVELS[2] - 0.5f ? 2 : y >= LEVELS[1] - 0.5f ? 1 : 0; }

    /**
     * A furnishing as placed: its kind, its cells (x0..x1, z0..z1), its height in cells (a hung piece's: the room's), its
     * facing (quarter turns: 0 its back to +z, 1 to +x, 2 to -z, 3 to -x), its storey, and its owner (0: the house's).
     */
    public record Fit(String kind, int x0, int z0, int x1, int z1, int h, int facing, int level, long owner) {
        public int y() { return LEVELS[level]; }
    }
    static Fit f(String k, int x0, int z0, int x1, int z1, int h, int facing, int level) { return new Fit(k, x0, z0, x1, z1, h, facing, level, Ids.BARON); }
    static Fit f(String k, int x, int z, int facing, int level) { return f(k, x, z, x, z, 1, facing, level); }

    /** Every furnishing of the house, room by room. */
    public static final Fit[] FITS = {
            // ---- the ground floor
            // the entrance hall: a tapestry over the stair's foot, a longcase clock, a bust and ferns, the chandelier
            f("CHANDELIER", 47, 141, 48, 142, STOREY, 0, 0), f("CLOCK", 49, 149, 49, 149, 2, 1, 0), f("BUST", 49, 140, 1, 0), f("PLANTER", 49, 150, 0, 0),
            f("PLANTER", 44, 150, 0, 0), f("TAPESTRY", 44, 141, 45, 141, STOREY, 3, 0), f("ARMOR_STAND", 49, 137, 49, 137, 2, 2, 0),
            f("RUG", 47, 146, 48, 150, 1, 0, 0), f("MIRROR", 49, 144, 1, 0), f("CANDELABRA", 46, 138, 0, 0),
            // the dining room: the long table for eight, its chairs, the sideboard, the fireplace on the west wall, paintings
            f("DINING_TABLE", 39, 145, 40, 148, 1, 1, 0),
            f("CHAIR", 38, 145, 3, 0), f("CHAIR", 38, 146, 3, 0), f("CHAIR", 38, 147, 3, 0), f("CHAIR", 38, 148, 3, 0),
            f("CHAIR", 41, 145, 1, 0), f("CHAIR", 41, 146, 1, 0), f("CHAIR", 41, 147, 1, 0), f("CHAIR", 41, 148, 1, 0),
            f("CHAIR", 39, 149, 0, 0), f("CHAIR", 40, 144, 2, 0),
            f("SIDEBOARD", 42, 149, 42, 150, 1, 1, 0), f("FIREPLACE", 37, 143, 37, 144, 2, 3, 0), f("CHANDELIER", 39, 146, 40, 147, STOREY, 0, 0),
            f("PAINTING", 38, 150, 39, 150, STOREY, 0, 0), f("PAINTING", 41, 143, 42, 143, STOREY, 2, 0), f("PLANTER", 37, 150, 0, 0),
            // the kitchen: the range (a station), the dresser, the pot rack, a work table and stools, the shelves
            f("CUPBOARD", 41, 137, 42, 137, 2, 2, 0), f("POT_RACK", 37, 138, 37, 139, STOREY, 3, 0), f("TABLE", 39, 139, 40, 140, 1, 0, 0),
            f("STOOL", 41, 139, 0, 0), f("STOOL", 41, 140, 0, 0), f("SHELVES", 37, 141, 38, 141, 2, 0, 0), f("CHANDELIER", 39, 139, 40, 140, STOREY, 0, 0), f("CANDELABRA", 42, 141, 0, 0),
            // the drawing room: the settee and armchairs round the fire, a rug, paintings, a clock, a planter, a sideboard
            f("FIREPLACE", 56, 146, 56, 147, 2, 1, 0), f("SOFA", 52, 145, 52, 148, 1, 3, 0), f("ARMCHAIR", 54, 144, 2, 0), f("ARMCHAIR", 54, 149, 0, 0),
            f("RUG", 53, 145, 55, 148, 1, 0, 0), f("CHANDELIER", 53, 146, 54, 147, STOREY, 0, 0), f("PAINTING", 51, 143, 52, 143, STOREY, 2, 0),
            f("PAINTING", 53, 150, 55, 150, STOREY, 0, 0), f("PLANTER", 56, 150, 0, 0), f("PLANTER", 56, 143, 0, 0), f("CANDELABRA", 51, 150, 0, 0),
            // the pantry: shelves round its walls, the wine rack (the crates and barrels are loose: CRATES)
            f("SHELVES", 51, 137, 53, 137, 2, 2, 0), f("SHELVES", 56, 140, 56, 141, 2, 1, 0), f("WINE_RACK", 51, 141, 52, 141, 2, 0, 0), f("CHANDELIER", 53, 139, 54, 140, STOREY, 0, 0),
            // ---- the first floor
            // the gallery: armour, a bust, portraits, a bench to sit and look at them
            f("ARMOR_STAND", 44, 137, 44, 137, 2, 2, 1), f("ARMOR_STAND", 46, 137, 46, 137, 2, 2, 1), f("BUST", 45, 137, 2, 1),
            f("PAINTING", 44, 141, 44, 142, STOREY, 3, 1), f("PAINTING", 49, 143, 49, 144, STOREY, 1, 1), f("CHANDELIER", 45, 142, 46, 143, STOREY, 0, 1),
            f("BENCH", 45, 140, 46, 140, 1, 0, 1), f("TAPESTRY", 44, 150, 45, 150, STOREY, 0, 1),
            // the library: bookcases round the walls, a desk, a reading chair, a candelabrum
            f("BOOKCASE", 37, 143, 37, 145, 2, 3, 1), f("BOOKCASE", 37, 147, 37, 149, 2, 3, 1), f("BOOKCASE", 38, 150, 41, 150, 2, 0, 1),
            f("DESK", 40, 145, 41, 145, 1, 2, 1), f("CHAIR", 40, 146, 0, 1), f("ARMCHAIR", 41, 148, 1, 1), f("CANDELABRA", 42, 149, 0, 1),
            f("RUG", 39, 146, 41, 149, 1, 0, 1), f("CHANDELIER", 39, 146, 40, 147, STOREY, 0, 1), f("PLANTER", 42, 143, 0, 1),
            // the study: the Baron's desk and chair, a bookcase, the weapon trophy, a map (a painting), a planter
            f("DESK", 38, 139, 39, 139, 1, 3, 1), f("CHAIR", 40, 139, 1, 1), f("BOOKCASE", 41, 137, 42, 137, 2, 2, 1), f("WEAPON_RACK", 37, 140, 37, 141, STOREY, 3, 1),
            f("PAINTING", 38, 141, 39, 141, STOREY, 0, 1), f("CANDELABRA", 42, 141, 0, 1), f("ARMCHAIR", 42, 139, 1, 1),
            // the master bedroom: the four-poster, its bedside tables, the wardrobe, the chest of drawers, the fire, a mirror
            f("FOURPOSTER", 53, 148, 54, 150, 1, 0, 1), f("NIGHTSTAND", 52, 150, 0, 1), f("NIGHTSTAND", 55, 150, 0, 1),
            f("WARDROBE", 51, 143, 52, 143, 2, 2, 1), f("DRESSER", 55, 143, 56, 143, 1, 2, 1), f("FIREPLACE", 56, 145, 56, 146, 2, 1, 1),
            f("RUG", 52, 145, 55, 147, 1, 0, 1), f("ARMCHAIR", 52, 145, 3, 1), f("MIRROR", 51, 148, 3, 1), f("CHANDELIER", 53, 146, 54, 147, STOREY, 0, 1),
            f("PAINTING", 51, 149, 51, 150, STOREY, 3, 1),
            // the bathroom: the copper tub, the washstand, a mirror, a fern
            f("BATHTUB", 53, 137, 55, 137, 1, 2, 1), f("WASHSTAND", 51, 140, 3, 1), f("MIRROR", 51, 139, 3, 1), f("PLANTER", 56, 141, 0, 1),
            f("CANDELABRA", 56, 138, 0, 1), f("STOOL", 52, 138, 0, 1),
            // ---- the second floor
            // the upper landing: a settee under a painting, a candelabrum, a planter
            f("SOFA", 44, 141, 44, 142, 1, 3, 2), f("PAINTING", 44, 139, 44, 140, STOREY, 3, 2), f("CANDELABRA", 44, 137, 0, 2), f("PLANTER", 49, 149, 0, 2),
            f("CHANDELIER", 45, 144, 46, 145, STOREY, 0, 2),
            // the servants' room: three plain beds (the Cook's, the Porter's, the Firewood Cutter's), a washstand, stools
            new Fit("BED", 37, 143, 37, 144, 1, 3, 2, Ids.MANSION_COOK), new Fit("BED", 37, 146, 37, 147, 1, 3, 2, Ids.MANSION_PORTER), new Fit("BED", 37, 149, 37, 150, 1, 3, 2, Ids.MANSION_WOODCUTTER),
            f("WASHSTAND", 42, 150, 1, 2), f("STOOL", 41, 145, 0, 2), f("SHELVES", 42, 143, 42, 144, 2, 1, 2), f("CANDELABRA", 41, 148, 0, 2),
            // the orchard keeper's room: a bed, a dresser, the linen shelves
            new Fit("BED", 37, 137, 38, 137, 1, 2, 2, Ids.MANSION_ORCHARDIST), f("DRESSER", 41, 137, 42, 137, 1, 2, 2), f("SHELVES", 37, 141, 38, 141, 2, 0, 2),
            f("CANDELABRA", 42, 141, 0, 2),
            // the guest room: a four-poster, a wardrobe, a washstand, a bedside table, a rug, a painting
            f("FOURPOSTER", 53, 148, 54, 150, 1, 0, 2), f("NIGHTSTAND", 55, 150, 0, 2), f("WARDROBE", 51, 143, 52, 143, 2, 2, 2), f("WASHSTAND", 56, 144, 1, 2),
            f("RUG", 52, 145, 55, 147, 1, 0, 2), f("PAINTING", 51, 146, 51, 147, STOREY, 3, 2), f("CHANDELIER", 53, 146, 54, 147, STOREY, 0, 2),
            // the knight's room: his bed, his armour on its stand, a weapon trophy, a stool
            new Fit("BED", 55, 137, 56, 137, 1, 2, 2, Ids.MANSION_KNIGHT), f("ARMOR_STAND", 51, 137, 51, 137, 2, 2, 2), f("WEAPON_RACK", 56, 140, 56, 141, STOREY, 1, 2),
            f("STOOL", 53, 140, 0, 2), f("CANDELABRA", 51, 141, 0, 2)};

    /** The loose crates, boxes and barrels (Breakable): {form, x, y-level, z, yaw quarter, what it holds (item id:count, ...)}. */
    public static final Object[][] CRATES = {
            {"CRATE", 54.5f, 0, 138.5f, 0, "potato:6,onion:4"}, {"CRATE", 55.5f, 0, 138.5f, 1, "carrot:6,cabbage:3"},
            {"BARREL", 53.5f, 0, 140.5f, 0, "ale:4"}, {"BARREL", 54.5f, 0, 140.5f, 0, "apple:8"}, {"BOX", 52.4f, 0, 139.4f, 1, "bread:3"},
            {"BARREL", 37.5f, 0, 137.6f, 0, ""}, {"CRATE", 40.5f, 0, 137.5f, 0, "logs:4"},
            {"CRATE", 37.5f, 2, 145.5f, 0, ""}, {"BOX", 56.4f, 2, 141.4f, 0, "bronze_arrows:10"},
            // outside: by the woodpile, the delivery spot at the gate, a sign at the gate
            {"BARREL", 41.5f, -1, 134.5f, 0, ""}, {"CRATE", 49.5f, -1, 154.5f, 1, "bread:2"}, {"SIGN", 49.5f, -1, 157.8f, 0, NAME + " - the household of the Baron"}};

    // ------------------------------------------------------------------ raising it
    /** Raises the estate and the house into the world (called from the devmap's builder, after the wood). */
    public static void build(CommandBuffer b) {
        Cells c = new Cells(b);
        // the clearing: the wood's trees and growth off the estate, a lawn, the gravel drive
        c.fill(EST_X0, FLOOR, EST_Z0, EST_X1, FLOOR + 12, EST_Z1, AIR);
        c.fill(EST_X0, FLOOR - 1, EST_Z0, EST_X1, FLOOR - 1, EST_Z1, GRASS);
        c.fill(46, FLOOR - 1, Z1 + 1, 47, FLOOR - 1, EST_Z1 + 3, GRAVEL);
        c.fill(X0 - 1, FLOOR - 1, Z1 + 1, X1 + 1, FLOOR - 1, Z1 + 2, GRAVEL);
        c.fill(X0 - 1, FLOOR - 1, Z0 - 2, X1 + 1, FLOOR - 1, Z0 - 1, FLAGSTONE);
        // the estate's low wall of mossy stone, with the gate in the front and a gap to the woodpile behind
        for (int x = EST_X0; x <= EST_X1; x++) { c.set(x, FLOOR, EST_Z0, MOSSY_BRICKS); c.set(x, FLOOR, EST_Z1, MOSSY_BRICKS); }
        for (int z = EST_Z0; z <= EST_Z1; z++) { c.set(EST_X0, FLOOR, z, MOSSY_BRICKS); c.set(EST_X1, FLOOR, z, MOSSY_BRICKS); }
        for (int[] g : GATE) c.set(g[0], FLOOR, g[1], AIR);
        for (int x : new int[]{GATE[0][0] - 1, GATE[1][0] + 1}) { c.fill(x, FLOOR, EST_Z1, x, FLOOR + 1, EST_Z1, STONE_BRICKS); c.set(x, FLOOR + 2, EST_Z1, LAMP); }
        c.set(38, FLOOR, EST_Z0, AIR); c.set(39, FLOOR, EST_Z0, AIR);
        // the garden: clipped hedges along the drive, the kitchen beds, the orchard (the trees are its nodes)
        for (int z = Z1 + 3; z <= EST_Z1 - 1; z++) if (z % 2 == 0) { c.set(44, FLOOR, z, HEDGE); c.set(49, FLOOR, z, HEDGE); }
        c.fill(51, FLOOR - 1, EST_Z1 - 3, 56, FLOOR - 1, EST_Z1 - 2, DIRT);
        // the woodpile behind the kitchen: stacked logs under a little roof
        c.fill(37, FLOOR, 134, 38, FLOOR, 134, OAK_LOG); c.set(37, FLOOR + 1, 134, OAK_LOG); c.fill(36, FLOOR + 2, 134, 39, FLOOR + 2, 134, SLATE_ROOF);
        // ---- the house
        // its footing and the floors' blocks: each storey's floor in its rooms' own floor, the ceiling over the top
        c.fill(X0, FLOOR - 1, Z0, X1, FLOOR - 1, Z1, STONE_BRICKS);
        for (int lv = 1; lv < 3; lv++) c.fill(X0 + 1, LEVELS[lv] - 1, Z0 + 1, X1 - 1, LEVELS[lv] - 1, Z1 - 1, DARK_PLANKS);
        c.fill(X0 + 1, TOP, Z0 + 1, X1 - 1, TOP, Z1 - 1, DARK_PLANKS);
        for (Room r : ROOMS) c.fill(r.x0, r.y() - 1, r.z0, r.x1, r.y() - 1, r.z1, r.floor);
        // the outer walls: stone below, timber framing above, each papered or panelled as the room behind it
        for (int y = FLOOR; y < TOP; y++) {
            int lv = level(y);
            int outer = lv == 0 ? STONE_BRICKS : TIMBER_FRAME;
            boolean slab = y == LEVELS[1] - 1 || y == LEVELS[2] - 1;
            for (int x = X0; x <= X1; x++) { c.set(x, y, Z0, slab ? BEAM : wall(outer, x, Z0 + 1, lv, IN_PZ)); c.set(x, y, Z1, slab ? BEAM : wall(outer, x, Z1 - 1, lv, IN_NZ)); }
            for (int z = Z0 + 1; z < Z1; z++) { c.set(X0, y, z, slab ? BEAM : wall(outer, X0 + 1, z, lv, IN_PX)); c.set(X1, y, z, slab ? BEAM : wall(outer, X1 - 1, z, lv, IN_NX)); }
            // the corners: oak posts up the timber storeys, quoins below
            for (int[] k : new int[][]{{X0, Z0}, {X1, Z0}, {X0, Z1}, {X1, Z1}}) c.set(k[0], y, k[1], lv == 0 ? LIMESTONE_BRICKS : OAK_LOG);
            // the timber storeys' frame: a post every third cell along each face (between the windows), under the beam
            // band at each floor (a half-timbered house, its panels plaster)
            if (lv > 0 && !slab) {
                for (int x = X0 + 3; x < X1; x += 3) { c.set(x, y, Z0, BEAM); c.set(x, y, Z1, BEAM); }
                for (int z = Z0 + 3; z < Z1; z += 3) { c.set(X0, y, z, BEAM); c.set(X1, y, z, BEAM); }
            }
        }
        // the partitions, each faced as the rooms on either side of it
        for (int lv = 0; lv < 3; lv++) {
            int y0 = LEVELS[lv], y1 = y0 + STOREY - 1;
            for (int y = y0; y <= y1; y++) {
                for (int z = Z0 + 1; z < Z1; z++) { c.set(WEST, y, z, partition(WEST - 1, z, WEST + 1, z, lv, 'x')); c.set(EAST, y, z, partition(EAST - 1, z, EAST + 1, z, lv, 'x')); }
                for (int x = X0 + 1; x < WEST; x++) c.set(x, y, ACROSS, partition(x, ACROSS - 1, x, ACROSS + 1, lv, 'z'));
                for (int x = EAST + 1; x < X1; x++) c.set(x, y, ACROSS, partition(x, ACROSS - 1, x, ACROSS + 1, lv, 'z'));
            }
            // the doorways: from the hall into each wing's front and back room, and between them (two high)
            for (int[] d : new int[][]{{WEST, 147}, {WEST, 139}, {EAST, 147}, {EAST, 139}}) c.fill(d[0], y0, d[1], d[0], y0 + 1, d[1], AIR);
            for (int x : new int[]{40, 54}) c.fill(x, y0, ACROSS, x, y0 + 1, ACROSS, AIR);
            // a wall lamp in each room and two in the hall (sconces), and the windows
            for (Room r : ROOMS) if (r.level == lv) c.set(r.x0 == 44 ? 44 : r.cx() < 46 ? r.x0 - 1 : r.x1 + 1, y0 + 2, (int) Math.floor(r.cz()), r.x0 == 44 ? AIR : LAMP);
            for (int x = X0 + 2; x < X1 - 1; x++) if (x != 46 && x != 47 && x != WEST && x != EAST && (x - X0) % 3 != 0) {
                c.fill(x, y0 + 1, Z1, x, y0 + 2, Z1, lv == 1 && (x == 45 || x == 48) ? STAINED_GLASS : GLASS);
                if (!(lv == 0 && (x == 46 || x == 47))) c.fill(x, y0 + 1, Z0, x, y0 + 2, Z0, GLASS);
            }
            for (int z = Z0 + 2; z < Z1 - 1; z++) if (z != ACROSS && (z - Z0) % 3 != 0) { c.fill(X0, y0 + 1, z, X0, y0 + 2, z, GLASS); c.fill(X1, y0 + 1, z, X1, y0 + 2, z, GLASS); }
        }
        // the fireplaces' chimney breasts are the outer wall: a stack up through the roof on each side
        for (int x : new int[]{X0, X1}) c.fill(x, TOP, 145, x, TOP + 9, 147, STONE_BRICKS);
        // the stair flights, their wells through the floor above, a balustrade round each well
        for (int[] f : FLIGHTS) {
            int base = LEVELS[f[5]];
            for (int i = 0; i < f[4]; i++) for (int x = f[0]; x <= f[1]; x++) {
                int z = f[2] + f[3] * i, y = base + i;
                if (i > 0) c.fill(x, base, z, x, y - 1, z, DARK_PLANKS);
                c.set(x, y, z, STAIRS);
                c.fill(x, y + 1, z, x, Math.min(y + 3, LEVELS[f[5] + 1] + STOREY - 1), z, AIR);
            }
            int above = LEVELS[f[5] + 1];
            int za = Math.min(f[2], f[2] + f[3] * (f[4] - 1)), zb = Math.max(f[2], f[2] + f[3] * (f[4] - 1));
            for (int z = za; z <= zb; z++) {
                // the well is a cell wider than the flight on its open side, the balustrade beyond that: a body on the
                // stair never brushes it (one on the outer lane caught on a rail right beside it)
                int gapX = f[0] == 44 ? f[1] + 1 : f[0] - 1, railX = f[0] == 44 ? f[1] + 2 : f[0] - 2;
                boolean top = f[3] < 0 && z == za || f[3] > 0 && z == zb;
                if (!top) c.set(gapX, above - 1, z, AIR);
                if (top) continue;   // the top step's side is open: it is the way off
                c.set(railX, above, z, DARK_PLANKS);
            }
        }
        // the front door and the back door (their leaves are the game's), a porch over the front with two columns
        for (int[] d : FRONT_DOOR) c.fill(d[0], FLOOR, d[1], d[0], FLOOR + 1, d[1], DOOR);
        for (int[] d : BACK_DOOR) c.fill(d[0], FLOOR, d[1], d[0], FLOOR + 1, d[1], DOOR);
        c.fill(45, FLOOR + 3, Z1 + 1, 48, FLOOR + 3, Z1 + 2, SLATE_ROOF);
        for (int x : new int[]{45, 48}) c.fill(x, FLOOR, Z1 + 2, x, FLOOR + 2, Z1 + 2, LIMESTONE_BRICKS);
        c.fill(45, FLOOR - 1, Z1 + 1, 48, FLOOR - 1, Z1 + 2, LIMESTONE_BRICKS);
        // the roof: slate in courses up to a ridge along the house, gable ends of timber framing
        int half = (Z1 - Z0) / 2 + 1;
        for (int k = 0; k <= half; k++) {
            int y = TOP + k, za = Z0 - 1 + k, zb = Z1 + 1 - k;
            if (za > zb) break;
            c.fill(X0 - 1, y, za, X1 + 1, y, za, SLATE_ROOF);
            c.fill(X0 - 1, y, zb, X1 + 1, y, zb, SLATE_ROOF);
            for (int x : new int[]{X0, X1}) if (zb - za > 1) c.fill(x, y, za + 1, x, y, zb - 1, TIMBER_FRAME);
        }
        // the plans, for the building rules and the routers
        BuildingKit.plan(new BuildingKit.Plan(NAME, X0, Z0, X1, Z1, 46, Z1, 'z', STOREY * 3));
    }

    /** An outer wall's block: its outside, its inside the finish of the room behind it (or the outside, if none). */
    private static int wall(int outer, int x, int z, int level, int dir) {
        Room r = roomAt(x, z, level);
        if (r == null) return outer;
        int d = dualOf(outer, r.finish, dir);
        return d;
    }

    /** A partition's block: one room's finish on one face, the other's on the other (or one finish both sides). */
    private static int partition(int ax, int az, int bx, int bz, int level, char axis) {
        Room a = roomAt(ax, az, level), b = roomAt(bx, bz, level);
        int fa = a == null ? PLASTER : a.finish, fb = b == null ? PLASTER : b.finish;
        if (fa == fb) return fa;
        int towardB = axis == 'x' ? IN_PX : IN_PZ, towardA = axis == 'x' ? IN_NX : IN_NZ;
        int d = dualOf(fa, fb, towardB);
        if (d != fa) return d;
        d = dualOf(fb, fa, towardA);
        return d != fb ? d : fa;
    }

    /** The cells, written raw (the world is lit once it is all built). */
    static final class Cells {
        final CommandBuffer b;
        Cells(CommandBuffer b) { this.b = b; }
        void fill(int x0, int y0, int z0, int x1, int y1, int z1, int id) { b.fill(Math.min(x0, x1), Math.min(y0, y1), Math.min(z0, z1), Math.max(x0, x1), Math.max(y0, y1), Math.max(z0, z1), id | AstrattoNative.SET_RAW); }
        void set(int x, int y, int z, int id) { b.blockSetRaw(x, y, z, id); }
    }

    /** The furnishings' collision cells are the Furniture objects' own (they set them when placed). */
    public static List<Fit> fits() { return List.of(FITS); }

    /** Beds of the household: the owner's bed's cell middle and its storey's floor height, or null. */
    public static float[] bedOf(long owner) {
        for (Fit f : FITS) if ((f.kind.equals("BED") || f.kind.equals("FOURPOSTER")) && f.owner == owner && (f.kind.equals("BED") || owner == Ids.BARON && f.level == 1))
            return new float[]{(f.x0 + f.x1 + 1) / 2f, f.y(), (f.z0 + f.z1 + 1) / 2f};
        return null;
    }

    /** The fireplaces' cells (for the Firewood Cutter's rounds). */
    public static List<Fit> hearths() { List<Fit> l = new ArrayList<>(); for (Fit f : FITS) if (f.kind.equals("FIREPLACE")) l.add(f); return l; }
}
