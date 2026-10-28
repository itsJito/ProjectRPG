package rpg;

import rpg.world.Blocks;
import rpg.world.DevMap;

/** Test placement: open ground on the devmap with nothing in the way. */
public final class TestLanes {
    private TestLanes() {}

    /** A spot with a clear lane 5 m wide and 16 m long to its -z, on open ground (x, z of the spot). */
    public static float[] open(Rpg g) {
        int y = (int) Math.floor(DevMap.FLOOR);
        for (int x = 20; x < 220; x += 3) for (int z = 20; z < 120; z += 3) {
            boolean clear = g.vox.solid(x, y - 1, z);
            for (int dz = -15; dz <= 1 && clear; dz++) for (int dx = -2; dx <= 2 && clear; dx++) for (int h = 0; h < 3 && clear; h++)
                if (g.vox.solid(x + dx, y + h, z + dz) || g.vox.block(x + dx, y + h, z + dz) != Blocks.AIR) clear = false;
            if (clear) return new float[]{x + 0.5f, z + 0.5f};
        }
        throw new AssertionError("no open lane on the devmap");
    }
}
