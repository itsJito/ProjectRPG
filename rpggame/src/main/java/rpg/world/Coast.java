package rpg.world;

import astratto.bridge.CommandBuffer;
import static rpg.world.Blocks.*;
import static rpg.world.DevMap.FLOOR;

/**
 * (0.3.5, gamefixes: a proper ocean zone with deep water at the end of the river in the Wilds) the sea the woodland
 * brook runs out into, along the whole southern edge of the map: the southern wood gives way to dune grass and a sand
 * beach; the shore wanders; the water shelves from a wading strip to swimming depth and on to the deep sea, over a bed of
 * sand, then gravelly stone, then dark rock where it is deepest. A timber pier runs out from the beach into the deep water
 * (the place for the big fish), a rocky islet stands off the western shore, and the long-shore drift and the brook's
 * outflow move the water (Fluids currents).
 *
 * <p>The map's ground is four blocks thick, so the deep sea is three blocks of water over its rock: deep enough to swim
 * and dive in everywhere past the shelf, and the deepest water on the map.
 */
public final class Coast {
    private Coast() {}

    /** The first row of the coast (dunes), and the map's far edge (the sea wall). */
    public static final int Z0 = 186, Z1 = DevMap.SZ - 1;
    /** The pier: its two plank lanes, from the beach out to its head in the deep water. */
    public static final int PIER_X0 = 128, PIER_X1 = 129, PIER_Z0 = 194, PIER_Z1 = 230;
    /** The islet off the western shore. */
    public static final int ISLE_X0 = 26, ISLE_Z0 = 218, ISLE_X1 = 34, ISLE_Z1 = 224;
    /** Where the Mariners' Guild keeps its hall on the beach (Guilds builds it). */
    public static final int HALL_X0 = 134, HALL_Z0 = 187, HALL_X1 = 145, HALL_Z1 = 195;

    /** The shoreline (first row of water) at a column: around z 200, wandering a few blocks either way. */
    public static int shore(int x) {
        double w = 2.6 * Math.sin(x * 0.071) + 1.4 * Math.sin(x * 0.193 + 1.3) + 0.8 * Math.sin(x * 0.41 + 0.4);
        int z = 200 + (int) Math.round(w);
        // the brook's mouth: the beach is cut back where it comes out
        if (Math.abs(x - 60.5f) < 5) z = Math.min(z, 197 + (int) Math.abs(x - 60.5f) / 2);
        return z;
    }
    /** How many blocks of water stand over a cell of the sea (0: dry land): the wading strip, the shelf, the deep. */
    public static int depth(int x, int z) {
        if (z >= Z1 || x <= 0 || x >= DevMap.SX - 1) return 0;
        int s = shore(x);
        if (z < s) return 0;
        if (x >= ISLE_X0 && x <= ISLE_X1 && z >= ISLE_Z0 && z <= ISLE_Z1) return 0;
        int out = z - s;
        return out < 4 ? 1 : out < 10 ? 2 : 3;
    }
    /** The open sea's zones, for the fish (Fishery): the wading strip and shelf (SHORE), the swimming sea (SEA), the deep. */
    public static boolean shelf(int x, int z) { int d = depth(x, z); return d == 1 || d == 2 && z - shore(x) < 7; }
    public static boolean deep(int x, int z) { return depth(x, z) == 3 && z - shore(x) >= 20; }

    /** Builds the coast into the devmap (after the southern wood, which it trims). */
    static void build(CommandBuffer b) {
        for (int x = 1; x < DevMap.SX - 1; x++) {
            int s = shore(x);
            for (int z = Z0; z < Z1; z++) {
                // the dunes and the beach: the wood's grass thins to sand over a few rows; nothing built stands here
                if (z < s) {
                    boolean sand = z >= s - 9 || z >= Z0 + 3 && ((x * 7 + z * 3) % 5) < (z - Z0);
                    if (sand) { set(b, x, FLOOR - 1, z, SAND); set(b, x, FLOOR - 2, z, SAND); }
                    for (int y = FLOOR; y <= FLOOR + 6; y++) set(b, x, y, z, AIR);
                    continue;
                }
                int d = depth(x, z);
                if (d == 0) continue;
                for (int y = FLOOR; y <= FLOOR + 6; y++) set(b, x, y, z, AIR);
                // the water, and under it the bed: sand in the shallows, gravelly stone on the shelf, dark rock in the deep
                for (int y = FLOOR - d; y <= FLOOR - 1; y++) set(b, x, y, z, WATER);
                int bed = d == 1 ? SAND : d == 2 ? ((x + z) % 4 == 0 ? COBBLE : SAND) : ((x * 3 + z) % 5 == 0 ? STONE : GRANITE);
                for (int y = 0; y < FLOOR - d; y++) set(b, x, y, z, y == FLOOR - d - 1 ? bed : STONE);
            }
        }
        // the sea wall along the far edge, and the map's side walls run down to it
        for (int x = 0; x < DevMap.SX; x++) for (int y = 0; y <= FLOOR + 1; y++) set(b, x, y, Z1, STONE);
        for (int z = Z0; z <= Z1; z++) for (int y = 0; y <= FLOOR + 1; y++) { set(b, 0, y, z, STONE); set(b, DevMap.SX - 1, y, z, STONE); }
        // the brook runs on across the beach into the sea
        for (int z = Expanse.BROOK_Z1 + 1; z < shore(60) + 1; z++) {
            for (int x = Expanse.BROOK_X0; x <= Expanse.BROOK_X1; x++) { set(b, x, FLOOR - 1, z, WATER); set(b, x, FLOOR - 2, z, SAND); for (int y = FLOOR; y <= FLOOR + 3; y++) set(b, x, y, z, AIR); }
            set(b, Expanse.BROOK_X0 - 1, FLOOR - 1, z, SAND); set(b, Expanse.BROOK_X1 + 1, FLOOR - 1, z, SAND);
        }
        // the islet: a hump of dark rock with a sandy lee, standing out of the shelf
        for (int x = ISLE_X0; x <= ISLE_X1; x++) for (int z = ISLE_Z0; z <= ISLE_Z1; z++) {
            float u = (x - (ISLE_X0 + ISLE_X1) / 2f) / ((ISLE_X1 - ISLE_X0) / 2f + 0.5f), v = (z - (ISLE_Z0 + ISLE_Z1) / 2f) / ((ISLE_Z1 - ISLE_Z0) / 2f + 0.5f);
            float r = u * u + v * v;
            if (r > 1) { for (int y = 1; y <= FLOOR - 1; y++) set(b, x, y, z, WATER); set(b, x, 0, z, GRANITE); continue; }
            for (int y = 0; y <= FLOOR - 1; y++) set(b, x, y, z, GRANITE);
            int top = r < 0.25f ? 2 : r < 0.6f ? 1 : 0;
            for (int y = FLOOR; y < FLOOR + top; y++) set(b, x, y, z, (x + z) % 3 == 0 ? STONE : GRANITE);
            if (top == 0 && v > 0.2f) set(b, x, FLOOR - 1, z, SAND);
        }
        // the pier: a two-plank deck at the water's surface on log posts, its head widened into a landing
        for (int z = PIER_Z0; z <= PIER_Z1; z++) {
            for (int x = PIER_X0; x <= PIER_X1; x++) { set(b, x, FLOOR - 1, z, PLANKS); for (int y = FLOOR; y <= FLOOR + 3; y++) set(b, x, y, z, AIR); }
            if ((z - PIER_Z0) % 4 == 0) for (int x : new int[]{PIER_X0 - 1, PIER_X1 + 1}) {
                for (int y = 0; y <= FLOOR - 1; y++) set(b, x, y, z, LOG);
                set(b, x, FLOOR, z, FENCE);
            }
        }
        for (int x = PIER_X0 - 2; x <= PIER_X1 + 2; x++) for (int z = PIER_Z1 - 3; z <= PIER_Z1; z++) set(b, x, FLOOR - 1, z, PLANKS);
        for (int x = PIER_X0 - 2; x <= PIER_X1 + 2; x += PIER_X1 + 4 - PIER_X0) for (int z = PIER_Z1 - 3; z <= PIER_Z1; z += 3) for (int y = 0; y <= FLOOR; y++) set(b, x, y, z, y == FLOOR ? LAMP : LOG);
    }

    /** The water moving along the coast: the long-shore drift east, and the brook's outflow pushing out to sea. */
    public static void currents(Fluids f) {
        f.current(1, 196, DevMap.SX - 2, Z1 - 1, 1, 0, 0.22f);
        f.current(Expanse.BROOK_X0 - 2, Expanse.BROOK_Z1 + 1, Expanse.BROOK_X1 + 2, 206, 0, 1, 0.6f);
    }

    /** Where the fishing spots of the coast are: the beach shallows, the shelf, the rocks of the islet, along the pier and at its head. */
    public static float[][] spots() {
        return new float[][]{
                {44.5f, shore(44) + 1.5f}, {96.5f, shore(96) + 1.5f}, {164.5f, shore(164) + 1.5f},     // the wading strip (nets, pots)
                {ISLE_X1 + 1.5f, ISLE_Z0 + 2.5f},                                                       // off the islet's rocks
                {PIER_X1 + 1.5f, 210.5f}, {PIER_X0 - 1.5f, 218.5f},                                      // along the pier
                {PIER_X1 + 2.5f, PIER_Z1 + 1.5f}, {PIER_X0 - 2.5f, PIER_Z1 + 1.5f}};                     // the pier's head, over the deep
    }

    private static void set(CommandBuffer b, int x, int y, int z, int id) { b.blockSetRaw(x, y, z, id); }
}
