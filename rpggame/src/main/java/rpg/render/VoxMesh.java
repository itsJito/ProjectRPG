package rpg.render;

import astratto.voxel.VoxModel;

/**
 * An item's voxel model as triangles: only the faces between a voxel and empty space, each in the voxel's own colour,
 * with the grip at the origin and one voxel {@link ItemModels#VOX} metres across. Metal voxels and the rest come out as
 * separate meshes so each is drawn with its own material (metal reflects like metal).
 */
final class VoxMesh {
    private VoxMesh() { }

    /** Vertices (10 floats each: position, colour, normal) for the metal voxels, then for the rest. */
    static float[][] build(ItemModels.Model model) {
        VoxModel m = model.vox();
        float[][] out = new float[2][];
        for (int pass = 0; pass < 2; pass++) {
            boolean metalPass = pass == 0;
            int faces = 0;
            for (int x = 0; x < m.sx; x++) for (int y = 0; y < m.sy; y++) for (int z = 0; z < m.sz; z++) {
                int c = m.get(x, y, z);
                if (c == 0 || isMetal(c) != metalPass) continue;
                for (int[] n : N) if (m.get(x + n[0], y + n[1], z + n[2]) == 0) faces++;
            }
            float[] v = new float[faces * 6 * 10];
            int k = 0;
            for (int x = 0; x < m.sx; x++) for (int y = 0; y < m.sy; y++) for (int z = 0; z < m.sz; z++) {
                int c = m.get(x, y, z);
                if (c == 0 || isMetal(c) != metalPass) continue;
                float r = ((c >> 16) & 255) / 255f, g = ((c >> 8) & 255) / 255f, b = (c & 255) / 255f;
                for (int f = 0; f < 6; f++) {
                    int[] n = N[f];
                    if (m.get(x + n[0], y + n[1], z + n[2]) != 0) continue;
                    // a little ambient occlusion: a face is darker where it meets other voxels around it
                    float ao = 1 - 0.07f * around(m, x, y, z, n);
                    k = face(v, k, x - model.gx(), y - model.gy(), z - model.gz(), f, r * ao, g * ao, b * ao);
                }
            }
            out[pass] = v;
        }
        return out;
    }

    static boolean isMetal(int argb) { return (argb >>> 24) == ItemModels.METAL; }

    private static final int[][] N = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};

    private static int around(VoxModel m, int x, int y, int z, int[] n) {
        int c = 0;
        int ox = x + n[0], oy = y + n[1], oz = z + n[2];
        for (int a = -1; a <= 1; a++) for (int b = -1; b <= 1; b++) {
            if (a == 0 && b == 0) continue;
            int px = ox, py = oy, pz = oz;
            if (n[0] != 0) { py += a; pz += b; } else if (n[1] != 0) { px += a; pz += b; } else { px += a; py += b; }
            if (m.get(px, py, pz) != 0) c++;
        }
        return c;
    }

    /** One face of the unit voxel at (x, y, z) (its low corner), in metres; two triangles wound outward. */
    private static int face(float[] v, int k, float x, float y, float z, int f, float r, float g, float b) {
        float s = ItemModels.VOX;
        float[][] q = switch (f) {
            case 0 -> new float[][]{{1, 0, 0}, {1, 1, 0}, {1, 1, 1}, {1, 0, 1}};
            case 1 -> new float[][]{{0, 0, 0}, {0, 0, 1}, {0, 1, 1}, {0, 1, 0}};
            case 2 -> new float[][]{{0, 1, 0}, {0, 1, 1}, {1, 1, 1}, {1, 1, 0}};
            case 3 -> new float[][]{{0, 0, 0}, {1, 0, 0}, {1, 0, 1}, {0, 0, 1}};
            case 4 -> new float[][]{{0, 0, 1}, {1, 0, 1}, {1, 1, 1}, {0, 1, 1}};
            default -> new float[][]{{0, 0, 0}, {0, 1, 0}, {1, 1, 0}, {1, 0, 0}};
        };
        int[] n = N[f];
        for (int o : new int[]{0, 1, 2, 0, 2, 3}) {
            v[k++] = (x + q[o][0]) * s; v[k++] = (y + q[o][1]) * s; v[k++] = (z + q[o][2]) * s;
            v[k++] = r; v[k++] = g; v[k++] = b; v[k++] = 1;
            v[k++] = n[0]; v[k++] = n[1]; v[k++] = n[2];
        }
        return k;
    }
}
