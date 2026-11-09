package rpg.render;

import rpg.fishing.FishKind;

/**
 * (0.3.5, gamefixes: all the fish modeled and animated) every kind of fish, drawn in two parts so it swims: the fore-body
 * (head, body, fins) and the tail, hinged where the body narrows to it and swung from side to side by the swim cycle -
 * the whale's flukes beat up and down, the squid trails its arms, the eel's tail is half of it. One unit is the fish's
 * length; it faces -z, as every figure does. Crabs, lobsters, shrimp and starfish are walkers of the bed: one part, their
 * legs ticking over.
 */
final class FishModels {
    private FishModels() {}

    /** A kind's meshes' vertices: the fore-body, the tail (null: none), the glowing part (the angler's lure; null: none), and the tail's hinge (z). */
    record Parts(float[] body, float[] tail, float[] glow, float hinge) {}

    static Parts of(FishKind k) {
        Meshes.Boxes b = new Meshes.Boxes(), t = new Meshes.Boxes(), glow = new Meshes.Boxes();
        float hinge = 0.18f;
        switch (k.shape) {
            case FISH, STRIPED, SPOTTED -> { fish(b, t, k, 0.21f, 0.42f, hinge); if (k.shape == FishKind.Shape.STRIPED) stripes(b, k, 0.21f); if (k.shape == FishKind.Shape.SPOTTED) spots(b, k, 0.21f); }
            case TUNA -> { fish(b, t, k, 0.27f, 0.5f, hinge); finlets(b, t, k); }
            case BILLFISH -> { hinge = 0.22f; fish(b, t, k, 0.16f, 0.36f, hinge); bill(b, k); sail(b, k); }
            case SHARK -> { hinge = 0.16f; shark(b, t, k, hinge); }
            case CATFISH -> { fish(b, t, k, 0.17f, 0.75f, hinge); barbels(b, k); }
            case EEL -> { hinge = 0f; eel(b, t, k); }
            case ANGLER -> { hinge = 0.2f; angler(b, t, glow, k, hinge); }
            case WHALE -> { hinge = 0.25f; whale(b, t, k, hinge); }
            case SQUID -> { hinge = 0.08f; squid(b, t, k); }
            case LOBSTER -> lobster(b, k);
            case CRAB -> crab(b, k);
            case SHRIMP -> shrimp(b, k);
            case STARFISH -> starfish(b, k);
        }
        return new Parts(b.vertices(), t.list.isEmpty() ? null : t.vertices(), glow.list.isEmpty() ? null : glow.vertices(), hinge);
    }

    /** A spindle body from the snout (-0.5) to the hinge, then the tail stock to the forked fin, the back dark over a pale belly. */
    private static void fish(Meshes.Boxes b, Meshes.Boxes t, FishKind k, float depth, float width, float hinge) {
        int n = 9;
        for (int i = 0; i < n; i++) {
            float z0 = -0.5f + (hinge + 0.5f) * i / n, z1 = -0.5f + (hinge + 0.5f) * (i + 1) / n, zc = (z0 + z1) / 2;
            float u = (zc + 0.12f) / 0.62f;   // the widest a little ahead of the middle
            float h = depth * (float) Math.sqrt(Math.max(0.06, 1 - u * u)), w = h * width;
            b.rgb(0, h * 0.22f, zc, w, h * 0.56f, z1 - z0 + 0.004f, k.back);
            b.rgb(0, -h * 0.25f, zc, w * 0.92f, h * 0.5f, z1 - z0 + 0.004f, k.belly);
        }
        // the eyes, the gill line, the fins: dorsal on the back, a pair of pectorals behind the gills, the anal fin under
        float eh = depth * 0.62f;
        for (int s = -1; s <= 1; s += 2) {
            b.rgb(s * depth * width * 0.42f, eh * 0.18f, -0.38f, 0.012f, depth * 0.11f, depth * 0.11f, 0x101010);
            b.rgb(s * depth * width * 0.44f, eh * 0.08f, -0.27f, 0.006f, depth * 0.55f, 0.01f, shade(k.back, 0.75f));
            b.rgb(s * depth * width * 0.6f, -depth * 0.12f, -0.2f, depth * width * 0.6f, 0.008f, 0.09f, k.fin);
        }
        b.rgb(0, depth * 0.5f, -0.06f, 0.008f, depth * 0.38f, 0.2f, k.fin);
        b.rgb(0, -depth * 0.42f, hinge - 0.08f, 0.008f, depth * 0.24f, 0.1f, k.fin);
        // the tail: its stock narrowing from the hinge, then the forked fin
        for (int i = 0; i < 4; i++) {
            float z0 = i * 0.07f, h = depth * (0.42f - i * 0.07f);
            t.rgb(0, h * 0.1f, z0 + 0.035f, h * width, h * 0.5f, 0.074f, k.back);
            t.rgb(0, -h * 0.22f, z0 + 0.035f, h * width * 0.9f, h * 0.4f, 0.074f, k.belly);
        }
        float tz = 0.29f;
        for (int s = -1; s <= 1; s += 2) for (int i = 0; i < 4; i++) t.rgb(0, s * (0.02f + i * depth * 0.14f), tz + 0.01f + i * 0.035f, 0.008f, depth * 0.16f, 0.05f, k.fin);
    }
    private static void stripes(Meshes.Boxes b, FishKind k, float depth) {
        for (int i = 0; i < 6; i++) { float z = -0.28f + i * 0.08f; b.rgb(0, depth * 0.28f, z, depth * 0.42f * 0.95f, depth * 0.36f, 0.018f, shade(k.fin, 0.9f)); }
    }
    private static void spots(Meshes.Boxes b, FishKind k, float depth) {
        for (int i = 0; i < 10; i++) { float z = -0.32f + (i % 5) * 0.11f, y = depth * (0.08f + (i / 5) * 0.18f); for (int s = -1; s <= 1; s += 2) b.rgb(s * depth * 0.42f * 0.48f, y, z, 0.004f, depth * 0.1f, 0.03f, shade(k.back, 0.6f)); }
    }
    private static void finlets(Meshes.Boxes b, Meshes.Boxes t, FishKind k) {
        for (int i = 0; i < 5; i++) { float z = 0.02f + i * 0.04f; b.rgb(0, 0.13f, z, 0.008f, 0.03f, 0.02f, k.fin); b.rgb(0, -0.12f, z, 0.008f, 0.03f, 0.02f, k.fin); }
        // the sickle: tall, swept-back lobes
        for (int s = -1; s <= 1; s += 2) for (int i = 0; i < 5; i++) t.rgb(0, s * (0.03f + i * 0.045f), 0.3f + i * 0.03f, 0.008f, 0.06f, 0.035f, shade(k.back, 0.9f));
    }
    private static void bill(Meshes.Boxes b, FishKind k) {
        b.rgb(0, 0.0f, -0.62f, 0.022f, 0.018f, 0.26f, shade(k.back, 0.8f));
        b.rgb(0, 0.0f, -0.77f, 0.014f, 0.012f, 0.06f, shade(k.back, 0.7f));
    }
    private static void sail(Meshes.Boxes b, FishKind k) {
        float tall = k == FishKind.SWORDFISH ? 0.1f : 0.2f;
        for (int i = 0; i < 6; i++) { float z = -0.32f + i * 0.06f, h = tall * (1 - i / 7f); b.rgb(0, 0.08f + h / 2, z, 0.008f, h, 0.06f, k.fin); }
    }
    private static void barbels(Meshes.Boxes b, FishKind k) {
        for (int s = -1; s <= 1; s += 2) { b.rgb(s * 0.07f, -0.01f, -0.53f, 0.006f, 0.006f, 0.12f, shade(k.back, 0.6f)); b.rgb(s * 0.05f, -0.05f, -0.5f, 0.005f, 0.005f, 0.08f, shade(k.back, 0.6f)); }
        b.rgb(0, 0.02f, -0.46f, 0.11f, 0.06f, 0.06f, k.back);   // the broad flat head
    }

    private static void shark(Meshes.Boxes b, Meshes.Boxes t, FishKind k, float hinge) {
        int n = 10;
        for (int i = 0; i < n; i++) {
            float z0 = -0.5f + (hinge + 0.5f) * i / n, z1 = -0.5f + (hinge + 0.5f) * (i + 1) / n, zc = (z0 + z1) / 2;
            float u = (zc + 0.1f) / 0.58f, h = 0.17f * (float) Math.sqrt(Math.max(0.05, 1 - u * u)), w = h * 0.8f;
            b.rgb(0, h * 0.2f, zc, w, h * 0.6f, z1 - z0 + 0.004f, k.back);
            b.rgb(0, -h * 0.26f, zc, w * 0.94f, h * 0.48f, z1 - z0 + 0.004f, k.belly);
        }
        b.rgb(0, -0.02f, -0.5f, 0.07f, 0.05f, 0.05f, k.back);   // the snout
        for (int s = -1; s <= 1; s += 2) {
            b.rgb(s * 0.055f, 0.02f, -0.42f, 0.008f, 0.014f, 0.014f, 0x0A0A0A);
            for (int i = 0; i < 4; i++) b.rgb(s * 0.066f, -0.005f, -0.33f + i * 0.02f, 0.004f, 0.06f, 0.004f, shade(k.back, 0.7f));   // the gill slits
            b.rgb(s * 0.13f, -0.06f, -0.22f, 0.15f, 0.008f, 0.09f, k.back);                                                       // the pectorals, wide
        }
        for (int i = 0; i < 5; i++) b.rgb(0, 0.1f + i * 0.025f, -0.14f + i * 0.025f, 0.01f, 0.03f, 0.13f - i * 0.022f, k.back);   // the dorsal, the fin above the water
        for (int i = 0; i < 4; i++) { float h = 0.11f - i * 0.02f; t.rgb(0, h * 0.1f, i * 0.07f + 0.035f, h * 0.7f, h * 0.55f, 0.074f, k.back); t.rgb(0, -h * 0.2f, i * 0.07f + 0.035f, h * 0.65f, h * 0.4f, 0.074f, k.belly); }
        for (int i = 0; i < 6; i++) t.rgb(0, 0.02f + i * 0.035f, 0.28f + i * 0.03f, 0.009f, 0.04f, 0.04f, k.back);   // the long upper lobe
        for (int i = 0; i < 3; i++) t.rgb(0, -0.02f - i * 0.03f, 0.28f + i * 0.02f, 0.009f, 0.035f, 0.035f, k.back);
    }

    private static void eel(Meshes.Boxes b, Meshes.Boxes t, FishKind k) {
        for (int i = 0; i < 8; i++) { float z = -0.5f + i * 0.0625f + 0.031f, h = i == 0 ? 0.05f : 0.065f; b.rgb(0, h * 0.15f, z, 0.055f, h * 0.6f, 0.064f, k.back); b.rgb(0, -h * 0.28f, z, 0.05f, h * 0.45f, 0.064f, k.belly); }
        for (int s = -1; s <= 1; s += 2) b.rgb(s * 0.026f, 0.012f, -0.47f, 0.006f, 0.01f, 0.01f, 0x0A0A0A);
        for (int i = 0; i < 8; i++) { float z = i * 0.0625f + 0.031f, h = 0.065f - i * 0.006f; t.rgb(0, h * 0.15f, z, 0.05f - i * 0.004f, h * 0.6f, 0.064f, k.back); t.rgb(0, -h * 0.3f, z, 0.045f - i * 0.004f, h * 0.45f, 0.064f, k.belly); t.rgb(0, -h * 0.75f, z, 0.006f, 0.02f, 0.064f, k.fin); }
    }

    private static void angler(Meshes.Boxes b, Meshes.Boxes t, Meshes.Boxes glow, FishKind k, float hinge) {
        // a great head and gape, a short body behind it, the lure on its stalk over the jaws
        b.rgb(0, 0.04f, -0.28f, 0.42f, 0.32f, 0.44f, k.back);
        b.rgb(0, -0.12f, -0.3f, 0.4f, 0.1f, 0.46f, k.belly);
        b.rgb(0, -0.06f, -0.51f, 0.36f, 0.05f, 0.02f, 0x1A0E0C);   // the gape
        for (int i = 0; i < 9; i++) b.rgb(-0.16f + i * 0.04f, -0.04f, -0.51f, 0.008f, 0.04f, 0.01f, 0xE8E0C8);   // the teeth
        for (int s = -1; s <= 1; s += 2) b.rgb(s * 0.15f, 0.16f, -0.38f, 0.03f, 0.03f, 0.03f, 0x0A0A0A);
        b.rgb(0, 0.04f, 0.05f, 0.26f, 0.22f, 0.3f, k.back);
        b.rgb(0, 0.28f, -0.4f, 0.012f, 0.012f, 0.22f, shade(k.back, 0.8f));   // the stalk
        glow.rgb(0, 0.28f, -0.53f, 0.05f, 0.05f, 0.05f, k.fin);
        for (int i = 0; i < 3; i++) { float h = 0.16f - i * 0.04f; t.rgb(0, 0.02f, i * 0.07f + 0.035f, h * 0.9f, h, 0.074f, k.back); }
        for (int i = 0; i < 4; i++) t.rgb(0, -0.06f + i * 0.04f, 0.24f + i * 0.01f, 0.008f, 0.05f, 0.06f, k.back);
    }

    private static void whale(Meshes.Boxes b, Meshes.Boxes t, FishKind k, float hinge) {
        int n = 12;
        for (int i = 0; i < n; i++) {
            float z0 = -0.5f + (hinge + 0.5f) * i / n, z1 = -0.5f + (hinge + 0.5f) * (i + 1) / n, zc = (z0 + z1) / 2;
            float u = (zc + 0.08f) / 0.55f, h = 0.15f * (float) Math.sqrt(Math.max(0.12, 1 - u * u)), w = h * 1.05f;
            b.rgb(0, h * 0.18f, zc, w, h * 0.64f, z1 - z0 + 0.004f, k.back);
            b.rgb(0, -h * 0.3f, zc, w * 0.96f, h * 0.42f, z1 - z0 + 0.004f, k.belly);
            if (i < 6) for (int g = -2; g <= 2; g++) b.rgb(g * w * 0.18f, -h * 0.52f, zc, 0.004f, 0.006f, z1 - z0, shade(k.belly, 0.8f));   // the throat grooves
        }
        for (int s = -1; s <= 1; s += 2) {
            b.rgb(s * 0.078f, -0.02f, -0.33f, 0.006f, 0.01f, 0.01f, 0x0A0A0A);
            b.rgb(s * 0.12f, -0.07f, -0.24f, 0.1f, 0.008f, 0.045f, k.back);   // the flippers
        }
        b.rgb(0, 0.085f, -0.4f, 0.012f, 0.004f, 0.012f, 0x101418);   // the blowhole
        b.rgb(0, 0.1f, 0.12f, 0.006f, 0.03f, 0.04f, k.back);          // the small dorsal
        for (int i = 0; i < 4; i++) { float h = 0.08f - i * 0.014f; t.rgb(0, 0, i * 0.055f + 0.028f, h * 0.9f, h, 0.058f, k.back); }
        // the flukes: broad and flat, out to either side
        for (int i = 0; i < 5; i++) t.rgb(0, 0, 0.23f + i * 0.02f, 0.26f - i * 0.04f, 0.008f, 0.024f, shade(k.back, 0.92f));
    }

    private static void squid(Meshes.Boxes b, Meshes.Boxes t, FishKind k) {
        // it swims mantle first: the mantle ahead, its fins at the tip, the head and eyes at the back, the arms trailing
        for (int i = 0; i < 7; i++) { float z = -0.5f + i * 0.06f + 0.03f, h = 0.03f + i * 0.012f; b.rgb(0, 0, z, h * 1.1f, h, 0.062f, i % 2 == 0 ? k.back : shade(k.back, 0.92f)); }
        b.rgb(0, 0, -0.44f, 0.16f, 0.008f, 0.08f, k.fin);
        b.rgb(0, 0, -0.05f, 0.1f, 0.09f, 0.1f, k.belly);
        for (int s = -1; s <= 1; s += 2) b.rgb(s * 0.05f, 0.01f, -0.04f, 0.02f, 0.03f, 0.03f, 0x101010);
        for (int a = 0; a < 8; a++) {
            double ang = a * Math.PI / 4;
            float ox = (float) Math.cos(ang) * 0.03f, oy = (float) Math.sin(ang) * 0.03f, len = a % 4 == 0 ? 0.5f : 0.3f;
            t.rgb(ox, oy, len / 2, 0.012f, 0.012f, len, shade(k.back, 0.95f));
        }
    }

    private static void lobster(Meshes.Boxes b, FishKind k) {
        for (int i = 0; i < 7; i++) { float z = -0.18f + i * 0.07f; b.rgb(0, 0.06f, z, 0.16f - i * 0.012f, 0.1f, 0.068f, i % 2 == 0 ? k.back : shade(k.back, 0.88f)); }
        for (int i = 0; i < 3; i++) b.rgb(-0.06f + i * 0.06f, 0.04f, 0.35f, 0.06f, 0.02f, 0.08f, k.back);   // the tail fan
        for (int s = -1; s <= 1; s += 2) {
            b.rgb(s * 0.1f, 0.05f, -0.28f, 0.04f, 0.04f, 0.14f, k.back);
            b.rgb(s * 0.14f, 0.05f, -0.42f, 0.09f, 0.06f, 0.14f, k.fin);       // the crusher and the cutter
            b.rgb(s * 0.03f, 0.08f, -0.5f, 0.006f, 0.006f, 0.4f, k.fin);        // the feelers
            for (int l = 0; l < 4; l++) b.rgb(s * 0.12f, 0.015f, -0.12f + l * 0.06f, 0.1f, 0.012f, 0.012f, k.belly);
        }
    }
    private static void crab(Meshes.Boxes b, FishKind k) {
        b.rgb(0, 0.06f, 0, 0.62f, 0.18f, 0.42f, k.back);
        b.rgb(0, 0.11f, 0, 0.5f, 0.06f, 0.32f, shade(k.back, 1.12f));
        for (int s = -1; s <= 1; s += 2) {
            b.rgb(s * 0.12f, 0.17f, -0.2f, 0.04f, 0.05f, 0.04f, 0x101010);
            b.rgb(s * 0.36f, 0.08f, -0.26f, 0.2f, 0.12f, 0.16f, k.fin);
            for (int l = 0; l < 4; l++) b.rgb(s * 0.4f, 0.02f, -0.08f + l * 0.08f, 0.22f, 0.03f, 0.03f, k.belly);
        }
    }
    private static void shrimp(Meshes.Boxes b, FishKind k) {
        for (int i = 0; i < 8; i++) { double a = Math.PI * 0.9 * i / 7; float z = (float) -Math.cos(a) * 0.4f, y = (float) Math.sin(a) * 0.14f + 0.08f; b.rgb(0, y, z, 0.16f - i * 0.012f, 0.13f, 0.12f, i % 2 == 0 ? k.back : k.belly); }
        b.rgb(0, 0.12f, -0.52f, 0.01f, 0.01f, 0.3f, k.fin);
    }
    private static void starfish(Meshes.Boxes b, FishKind k) {
        b.rgb(0, 0.04f, 0, 0.22f, 0.08f, 0.22f, k.back);
        for (int a = 0; a < 5; a++) for (int i = 1; i <= 4; i++) {
            double ang = a * Math.PI * 2 / 5;
            float r = i * 0.1f, w = 0.17f - i * 0.03f;
            b.rgb((float) Math.cos(ang) * r, 0.03f, (float) Math.sin(ang) * r, w, 0.06f - i * 0.008f, w, i % 2 == 0 ? k.back : k.belly);
        }
    }

    static int shade(int rgb, float k) {
        int r = Math.min(255, Math.round(((rgb >> 16) & 255) * k)), g = Math.min(255, Math.round(((rgb >> 8) & 255) * k)), b = Math.min(255, Math.round((rgb & 255) * k));
        return r << 16 | g << 8 | b;
    }
}
