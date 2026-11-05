package rpg.render;

import rpg.world.DevMap;
import rpg.world.Farming;

/**
 * (0.3.2) What grows in the kitchen gardens, drawn: each plot a raised ridge of turned earth, and on it the crop at its
 * stage - carrots in a double row of feathery tops that thicken as they grow (their orange shoulders showing at the soil
 * when ripe, a tall white umbel when they bolt), a cabbage that spreads its outer leaves and then folds a pale head inside
 * them (a yellow-flowered stalk when it bolts). One mesh a garden, rebuilt when any plot in it changes stage.
 */
final class FarmModels {
    private FarmModels() {}

    static Meshes.Boxes field(Farming farming, int field) {
        Meshes.Boxes b = new Meshes.Boxes();
        float y0 = DevMap.FLOOR;
        for (Farming.Plot p : farming.plots) {
            if (p.field != field) continue;
            float cx = p.bx + 0.5f, cz = p.bz + 0.5f;
            long h = p.id * 0x9E3779B97F4A7C15L;
            // the ridge: turned earth, a shade lighter on its crown, two furrow lines along it
            b.rgb(cx, y0 + 0.03f, cz, 0.86f, 0.06f, 0.86f, 0x4A3424);
            b.rgb(cx, y0 + 0.065f, cz, 0.7f, 0.012f, 0.6f, 0x5A4230);
            for (float dz : new float[]{-0.2f, 0.2f}) b.rgb(cx, y0 + 0.072f, cz + dz, 0.72f, 0.01f, 0.05f, 0x3A2818);
            int stage = p.stage();
            if (stage == 0 || p.crop == null) continue;
            float top = y0 + 0.07f;
            if (p.crop == Farming.Crop.CARROT) carrots(b, cx, top, cz, stage, h);
            else if (p.crop == Farming.Crop.BARLEY) barley(b, cx, top, cz, stage, h);
            else if (p.crop == Farming.Crop.HOPS) hops(b, cx, top, cz, stage, h);
            else cabbage(b, cx, top, cz, stage, h);
        }
        return b;
    }

    /**
     * (0.3.4) Barley: a close stand of thin stalks, green and short to start, tall and gold when ripe, each ripe stalk
     * nodding under a bearded ear; gone to seed, grey-gold and leaning over.
     */
    private static void barley(Meshes.Boxes b, float cx, float y, float cz, int stage, long h) {
        float[] heights = {0, 0.12f, 0.32f, 0.52f, 0.66f, 0.5f};
        int[] stalk = {0, 0x5E9A36, 0x6EA83C, 0x9AAA44, 0xC8A848, 0x9A8A5A};
        float ht = heights[stage];
        for (int row = 0; row < 3; row++) for (int i = 0; i < 4; i++) {
            long k = h + row * 11 + i * 17;
            float x = cx - 0.3f + i * 0.2f + jit(k, 1) * 0.05f, z = cz - 0.22f + row * 0.22f + jit(k, 2) * 0.05f;
            float hh = ht * (0.88f + 0.24f * (jit(k, 3) + 0.5f));
            float lean = stage == 5 ? 0.18f : 0.02f * stage * (jit(k, 4) + 0.5f);
            b.rgb(x + lean * 0.5f, y + hh * 0.5f, z, 0.025f, hh, 0.025f, stalk[stage]);
            if (stage >= 3) {
                int ear = stage == 3 ? 0xA8B850 : stage == 4 ? 0xE0C060 : 0xB8A070;
                b.rgb(x + lean + 0.02f, y + hh - 0.02f, z, 0.05f, 0.11f, 0.045f, ear);
                if (stage >= 4) b.rgb(x + lean + 0.03f, y + hh + 0.07f, z, 0.012f, 0.08f, 0.012f, 0xEED890);   // the beard
            }
        }
    }

    /**
     * (0.3.4) Hops: a bine climbing a pole set in the plot - leaves up it as it grows, and hung with pale green cones when
     * ripe; gone to seed, the cones brown and the leaves yellowing.
     */
    private static void hops(Meshes.Boxes b, float cx, float y, float cz, int stage, long h) {
        float pole = 1.2f;
        b.rgb(cx, y + pole / 2, cz, 0.05f, pole, 0.05f, 0x7A5A38);
        float[] reach = {0, 0.2f, 0.5f, 0.85f, 1.1f, 1.1f};
        float up = reach[stage];
        int leaf = stage == 5 ? 0xA8A040 : 0x4E8A2E, leafHi = stage == 5 ? 0xC0B050 : 0x62A23A;
        for (int i = 0; i < 10; i++) {
            float t = (i + 0.5f) / 10f * up;
            if (t <= 0.02f) continue;
            double a = i * 1.9 + jit(h, i % 8) * 0.6;
            float r = 0.07f + 0.02f * (i % 3);
            float lx = cx + (float) Math.cos(a) * r, lz = cz + (float) Math.sin(a) * r;
            b.rgb(lx, y + t, lz, 0.1f, 0.07f, 0.1f, i % 2 == 0 ? leaf : leafHi);
            if (stage >= 4 && i % 2 == 1) b.rgb(cx + (float) Math.cos(a + 1) * (r + 0.05f), y + t - 0.05f, cz + (float) Math.sin(a + 1) * (r + 0.05f), 0.055f, 0.08f, 0.055f, stage == 5 ? 0x8A6A3A : 0xC8E08A);
        }
    }

    private static float jit(long h, int k) { return (((h >>> (k * 5)) & 31) / 31f - 0.5f); }

    private static void carrots(Meshes.Boxes b, float cx, float y, float cz, int stage, long h) {
        float[] heights = {0, 0.07f, 0.15f, 0.23f, 0.29f, 0.3f};
        float ht = heights[stage];
        for (int row = 0; row < 2; row++) for (int i = 0; i < 3; i++) {
            long k = h + row * 7 + i * 13;
            float x = cx - 0.27f + i * 0.27f + jit(k, 1) * 0.06f, z = cz + (row == 0 ? -0.2f : 0.2f) + jit(k, 2) * 0.05f;
            float hh = ht * (0.85f + 0.3f * (jit(k, 3) + 0.5f));
            if (stage == 5) {
                // bolted: a tall stalk, a flat white umbel of flowers going to seed
                b.rgb(x, y + 0.36f, z, 0.025f, 0.72f, 0.025f, 0x6A8A3A);
                b.rgb(x, y + 0.73f, z, 0.17f, 0.035f, 0.17f, 0xEEEAD8);
                b.rgb(x, y + 0.75f, z, 0.1f, 0.02f, 0.1f, 0xF8F6EA);
                b.rgb(x, y + hh * 0.3f, z, 0.12f, hh * 0.6f, 0.12f, 0x5A8A30);
                continue;
            }
            // the fronds: a few thin stalks leaning out from the crown, lighter at their tips
            int fronds = stage == 1 ? 2 : 4;
            for (int f = 0; f < fronds; f++) {
                float a = (float) (f * Math.PI * 2 / fronds + jit(k, 4 + f));
                float lean = 0.03f + 0.03f * stage;
                float fx = x + (float) Math.cos(a) * lean, fz = z + (float) Math.sin(a) * lean;
                b.rgb(fx, y + hh * 0.5f, fz, 0.03f, hh, 0.03f, f % 2 == 0 ? 0x3E8A2C : 0x4E9A34);
                if (stage >= 2) b.rgb(fx + (float) Math.cos(a) * 0.03f, y + hh * 0.92f, fz + (float) Math.sin(a) * 0.03f, 0.08f, 0.04f, 0.08f, 0x6AB848);
            }
            // ripe: the orange shoulder at the soil
            if (stage >= 4) b.rgb(x, y + 0.012f, z, 0.07f, 0.03f, 0.07f, 0xE8782A);
        }
    }

    private static void cabbage(Meshes.Boxes b, float cx, float y, float cz, int stage, long h) {
        float x = cx + jit(h, 1) * 0.06f, z = cz + jit(h, 2) * 0.06f;
        if (stage == 5) {
            // bolted: the head split by a tall stalk of yellow flowers
            b.rgb(x, y + 0.08f, z, 0.32f, 0.16f, 0.32f, 0x5E8E3C);
            b.rgb(x, y + 0.45f, z, 0.04f, 0.7f, 0.04f, 0x7A9A44);
            for (int i = 0; i < 4; i++) b.rgb(x + jit(h, 3 + i) * 0.16f, y + 0.62f + i * 0.07f, z + jit(h, 7 + i) * 0.16f, 0.07f, 0.04f, 0.07f, 0xE8D040);
            return;
        }
        float[] spread = {0, 0.1f, 0.2f, 0.28f, 0.34f};
        float sp = spread[stage];
        // the outer leaves, broad and low, fanned round
        int leaves = stage == 1 ? 4 : 6;
        for (int i = 0; i < leaves; i++) {
            double a = i * Math.PI * 2 / leaves + jit(h, 3) * 0.6;
            float lx = x + (float) Math.cos(a) * sp * 0.55f, lz = z + (float) Math.sin(a) * sp * 0.55f;
            float lw = 0.08f + sp * 0.4f;
            b.rgb(lx, y + 0.02f + sp * 0.12f, lz, lw, 0.025f, lw, i % 2 == 0 ? 0x4E7E34 : 0x5A8E3C);
            if (stage >= 2) b.rgb(lx, y + 0.035f + sp * 0.14f, lz, 0.012f, 0.012f, lw * 0.9f, 0x9CC07A);   // the leaf's pale rib
        }
        // the head: none yet in the seedling, then folding pale and round inside the outer leaves
        if (stage >= 3) {
            float hs = stage == 3 ? 0.16f : 0.26f;
            b.rgb(x, y + hs * 0.5f, z, hs, hs * 0.9f, hs, 0x8EBC5E);
            b.rgb(x, y + hs * 0.55f, z, hs * 0.78f, hs, hs * 0.78f, 0xA8D27A);
            b.rgb(x, y + hs * 0.95f, z, hs * 0.5f, 0.03f, hs * 0.5f, 0xC0E098);
        }
    }

    /**
     * Where apple i hangs on a tree (same every frame): every other one just under the crown's floor, the rest against the
     * crown's sides near the middle of a face - never inside the leaves, where it would not be seen.
     */
    static float[] applePlace(Farming.AppleTree t, int i) {
        long h = (t.id * 31 + i) * 0x9E3779B97F4A7C15L;
        float u = ((h >>> 20) & 1023) / 1023f, v = ((h >>> 32) & 1023) / 1023f, w = ((h >>> 44) & 1023) / 1023f;
        float floorY = DevMap.FLOOR + Farming.TREE_HEIGHT - 1;
        if (i % 2 == 0) {
            double a = i * 2.39996 + u;
            float r = 0.75f + v * 1.35f;
            return new float[]{t.x + (float) Math.cos(a) * r, floorY - 0.08f - w * 0.06f, t.z + (float) Math.sin(a) * r};
        }
        // a face: one of the four sides, a little off its middle; the crown's lowest layer reaches 2.5 from the trunk
        int side = (int) (((h >>> 12) & 3));
        float along = (u - 0.5f) * 2.2f, out = 2.58f, y = floorY + 0.2f + v * 0.6f;
        float dx = side == 0 ? out : side == 1 ? -out : along, dz = side == 2 ? out : side == 3 ? -out : along;
        return new float[]{t.x + dx, y, t.z + dz};
    }
}
