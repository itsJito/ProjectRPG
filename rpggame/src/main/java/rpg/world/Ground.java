package rpg.world;

/**
 * (0.4.0, gamefixes: ShadowVeil) The height of the open ground at a point, for every system that sets something down,
 * looks along the ground or asks what stands on it: a body's feet, a dropped item, a corpse, a goal, a cell to test.
 * On the devmap that is its floor everywhere (exactly as the code always had it); on ShadowVeil it is the terrain's
 * own height there (a bridge's deck where there is one), so the same systems work on hills, banks and bridges.
 */
public final class Ground {
    private Ground() {}

    /** Where a body stands at (x, z): the first open cell above the ground. */
    public static float at(double x, double z) { return WorldMap.standY((float) x, (float) z); }
    /** That cell's y, as a whole number (for asking the voxels). */
    public static int cell(double x, double z) { return (int) Math.floor(at(x, z) + 1e-3); }
}
