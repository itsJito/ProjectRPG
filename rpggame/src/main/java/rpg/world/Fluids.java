package rpg.world;

import java.util.ArrayList;
import java.util.List;

/**
 * (0.3.4, gamefixes: a new water system) Water as volumes, read live from the voxels so that anything that changes a
 * block (a bucket, a fire put out, the new map's own lakes) is water at once with nothing to rebuild.
 *
 * <p>A water cell is filled to a height: the topmost cell of a column holds water to {@link #TOP} of its height (the
 * drawn surface), a cell with water above it is full. Every question is asked of those partial heights - whether a box
 * touches the water, how deep in it a body is, where the surface over a point lies, how many blocks of water stand
 * under it - so a body at the surface is judged by the surface, not by the block it is in.
 *
 * <p>Water moves where a map says it runs: each {@link Current} is a channel (a brook, a stream, the outflow of a pond)
 * with a direction and a speed, strongest mid-channel and at the surface, fading toward the banks and the bed. The
 * channels are data, given by each map, so the same system runs on the new map with its own rivers.
 */
public final class Fluids {
    /** How far up its cell the topmost water reaches (the drawn surface sits at 0.9 of the block). */
    public static final float TOP = 0.9f;

    /** A channel of moving water: a box of columns (inclusive), the way it runs (unit x, z) and its speed (m/s). */
    public record Current(int x0, int z0, int x1, int z1, float dx, float dz, float speed) {
        boolean holds(int x, int z) { return x >= x0 && x <= x1 && z >= z0 && z <= z1; }
    }

    private final Voxels vox;
    private final List<Current> currents = new ArrayList<>();

    public Fluids(Voxels vox) { this.vox = vox; }

    /** (0.4.5) The water's motion everywhere (FlowField): set once the map's water is known. */
    public FlowField field;

    public void current(int x0, int z0, int x1, int z1, float dx, float dz, float speed) {
        float l = (float) Math.hypot(dx, dz);
        currents.add(new Current(Math.min(x0, x1), Math.min(z0, z1), Math.max(x0, x1), Math.max(z0, z1), dx / l, dz / l, speed));
    }
    public List<Current> currents() { return currents; }

    public boolean water(int x, int y, int z) { return vox.block(x, y, z) == Blocks.WATER; }

    /** How high the water in this cell stands above the cell's floor (0: none, {@link #TOP}: the surface cell, 1: full). */
    public float height(int x, int y, int z) {
        if (!water(x, y, z)) return 0;
        return water(x, y + 1, z) ? 1f : TOP;
    }

    /** Whether a point is in water (below the surface of the cell it is in). */
    public boolean in(float x, float y, float z) {
        int bx = (int) Math.floor(x), by = (int) Math.floor(y), bz = (int) Math.floor(z);
        float h = height(bx, by, bz);
        return h > 0 && y < by + h;
    }

    /**
     * The surface of the water over a point: the top of the water column the point is in or just above (searching a
     * little above and down to a dozen blocks below), or NaN if there is none.
     */
    public float surface(float x, float y, float z) {
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        int top = (int) Math.floor(y) + 2;
        for (int h = top; h >= Math.max(0, top - 14); h--)
            if (water(bx, h, bz) && !water(bx, h + 1, bz)) return h + TOP;
        return Float.NaN;
    }

    /** How many blocks of water stand in the column under the surface at a point (0 if none). */
    public int depth(float x, float y, float z) {
        float s = surface(x, y, z);
        if (Float.isNaN(s)) return 0;
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z), h = (int) Math.floor(s), n = 0;
        while (h >= 0 && water(bx, h, bz)) { n++; h--; }
        return n;
    }

    /**
     * How deep a box stands in water: the highest water surface (partial heights) among the cells the box overlaps,
     * less the box's bottom, clamped to the box's height. 0: no contact. A sliver of tolerance keeps a box resting on
     * the surface of a block of water from counting as wet.
     */
    public float submerged(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        int x0 = (int) Math.floor(minX + 1e-3), x1 = (int) Math.floor(maxX - 1e-3), z0 = (int) Math.floor(minZ + 1e-3), z1 = (int) Math.floor(maxZ - 1e-3);
        int y0 = (int) Math.floor(minY + 1e-3), y1 = (int) Math.floor(maxY - 1e-3);
        double best = 0;
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) for (int y = y0; y <= y1; y++) {
            float h = height(x, y, z);
            if (h <= 0) continue;
            double top = Math.min(y + h, maxY);
            if (top > minY + 1e-3) best = Math.max(best, top - minY);
        }
        return (float) Math.min(best, maxY - minY);
    }

    /** Whether any part of a box touches water. */
    public boolean contact(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        return submerged(minX, minY, minZ, maxX, maxY, maxZ) > 0;
    }

    /**
     * (0.3.4) Where to stand to dip something in water near a place: the nearest cell a body can stand in with water
     * within arm's reach (beside it, or a step below - a well, a pond's edge, a brook), within a radius; or null.
     */
    public float[] edge(float x, float z, int y, int radius) {
        int cx = (int) Math.floor(x), cz = (int) Math.floor(z);
        float best = Float.MAX_VALUE; float[] at = null;
        for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
            float d = dx * dx + dz * dz;
            if (d >= best || d > radius * radius) continue;
            int bx = cx + dx, bz = cz + dz;
            for (int by = y - 1; by <= y + 1; by++) {
                if (vox.solid(bx, by, bz) || vox.solid(bx, by + 1, bz) || !vox.solid(bx, by - 1, bz) || water(bx, by, bz) || water(bx, by - 1, bz)) continue;
                boolean wet = false;
                for (int ox = -1; ox <= 1 && !wet; ox++) for (int oz = -1; oz <= 1 && !wet; oz++) for (int oy = -2; oy <= 0 && !wet; oy++) wet = water(bx + ox, by + oy, bz + oz);
                if (!wet) continue;
                best = d; at = new float[]{bx + 0.5f, by, bz + 0.5f};
                break;
            }
        }
        return at;
    }

    /**
     * The water's own motion at a point (m/s, x and z), or zero in still water. Strongest in the middle of a channel and
     * near the surface; slack at the banks and along the bed.
     */
    public float[] flow(float x, float y, float z) {
        float[] out = new float[2];
        if (field == null || !in(x, y, z) && !in(x, y - 0.5f, z)) return out;
        // (0.4.5) from the field (eased between columns), strongest at the surface and slack along the bed
        field.sample(x, z, out);
        float s = surface(x, y, z);
        float near = Float.isNaN(s) ? 1 : Math.max(0.35f, 1 - Math.max(0, s - y) * 0.35f);
        out[0] *= near; out[1] *= near;
        return out;
    }
}
