package rpg.render;

import rpg.item.Recipes;

/**
 * (0.3.5, gamefixes: the textile pipeline with proper animations) the textile workers' stations, each a fixed part and
 * a moving one: the carding bench and its pair of hand cards (stroked against each other), the spinning wheel's frame and
 * its wheel (turned by the treadle), the loom's frame and its beater (swung to beat up each pick), the fulling tub and the
 * cloth lying in it (trodden down), the dye vat and its stirring pole. One unit is a metre; each faces -z, its front to the
 * worker, set on the floor at its cell's middle.
 */
final class WorkshopModels {
    private WorkshopModels() {}

    /** The fixed part's and the moving part's vertices, and where the moving part pivots (x, y, z). */
    record Parts(float[] fixed, float[] moving, float[] pivot) {}

    static Parts of(Recipes.Station k) {
        Meshes.Boxes f = new Meshes.Boxes(), m = new Meshes.Boxes();
        int wood = 0x8A6038, dark = 0x5A3E24, iron = 0x5A5A60;
        float[] pivot = {0, 0, 0};
        switch (k) {
            case CARDING_BENCH -> {
                // a low bench, a basket of fleece on it, a heap of carded rolls
                f.rgb(0, 0.42f, 0, 0.9f, 0.06f, 0.42f, wood);
                for (int sx = -1; sx <= 1; sx += 2) for (int sz = -1; sz <= 1; sz += 2) f.rgb(sx * 0.38f, 0.2f, sz * 0.16f, 0.06f, 0.4f, 0.06f, dark);
                f.rgb(0.28f, 0.52f, 0.02f, 0.26f, 0.14f, 0.26f, 0xA8844E);
                f.rgb(0.28f, 0.6f, 0.02f, 0.22f, 0.08f, 0.22f, 0xF0EADA);
                for (int i = 0; i < 4; i++) f.rgb(-0.3f + i * 0.06f, 0.49f, 0.08f, 0.05f, 0.05f, 0.18f, 0xF8F4EA);
                // the two hand cards, worked against each other (the moving part: the top card)
                f.rgb(-0.05f, 0.47f, -0.08f, 0.2f, 0.02f, 0.12f, 0xB89060);
                m.rgb(0, 0.012f, 0, 0.2f, 0.02f, 0.12f, 0xB89060);
                m.rgb(0, 0.04f, 0.07f, 0.05f, 0.03f, 0.08f, dark);
                pivot = new float[]{-0.05f, 0.5f, -0.08f};
            }
            case SPINNING_WHEEL -> {
                // the bench, the legs, the uprights holding the wheel's axle, the flyer and the treadle
                f.rgb(0, 0.38f, 0, 0.22f, 0.06f, 0.9f, wood);
                f.rgb(0, 0.19f, -0.36f, 0.05f, 0.38f, 0.05f, dark); f.rgb(-0.1f, 0.19f, 0.36f, 0.05f, 0.38f, 0.05f, dark); f.rgb(0.1f, 0.19f, 0.36f, 0.05f, 0.38f, 0.05f, dark);
                for (int s = -1; s <= 1; s += 2) f.rgb(s * 0.07f, 0.62f, 0.1f, 0.03f, 0.5f, 0.04f, dark);
                f.rgb(0, 0.58f, -0.38f, 0.05f, 0.36f, 0.05f, dark); f.rgb(0, 0.78f, -0.38f, 0.12f, 0.06f, 0.06f, 0xC8A060);   // the flyer and its bobbin
                f.rgb(0, 0.05f, 0.18f, 0.18f, 0.03f, 0.3f, dark);                                                               // the treadle
                // the wheel: a rim of spokes about its axle (the moving part)
                for (int i = 0; i < 16; i++) {
                    double a = i * Math.PI / 8;
                    m.rgb(0, (float) Math.sin(a) * 0.33f, (float) Math.cos(a) * 0.33f, 0.035f, 0.07f, 0.07f, wood);
                    if (i % 2 == 0) m.rgb(0, (float) Math.sin(a) * 0.16f, (float) Math.cos(a) * 0.16f, 0.02f, i % 4 == 0 ? 0.3f : 0.02f, i % 4 == 0 ? 0.02f : 0.3f, dark);
                }
                m.rgb(0, 0, 0, 0.08f, 0.06f, 0.06f, dark);
                pivot = new float[]{0, 0.82f, 0.1f};
            }
            case LOOM -> {
                // the frame: posts, the breast beam and the warp beam, the warp threads stretched between, the cloth wound on
                for (int sx = -1; sx <= 1; sx += 2) { f.rgb(sx * 0.62f, 0.75f, -0.35f, 0.08f, 1.5f, 0.08f, dark); f.rgb(sx * 0.62f, 0.75f, 0.35f, 0.08f, 1.5f, 0.08f, dark); f.rgb(sx * 0.62f, 1.45f, 0, 0.08f, 0.08f, 0.78f, dark); }
                f.rgb(0, 0.82f, -0.38f, 1.2f, 0.1f, 0.1f, wood);                // the breast beam
                f.rgb(0, 0.95f, 0.38f, 1.2f, 0.12f, 0.12f, wood);               // the warp beam
                f.rgb(0, 0.82f, -0.38f, 1.06f, 0.14f, 0.12f, 0xD8CCAC);         // the cloth woven so far
                for (int i = 0; i < 12; i++) f.rgb(-0.5f + i * 0.09f, 0.9f, 0.0f, 0.012f, 0.012f, 0.76f, 0xE8DCC0);   // the warp
                f.rgb(0, 1.35f, 0.05f, 1.1f, 0.05f, 0.05f, wood); f.rgb(0, 1.1f, 0.05f, 1.06f, 0.24f, 0.02f, 0xC8B890);  // the heddles
                f.rgb(0, 0.3f, 0.1f, 0.9f, 0.04f, 0.1f, dark);                  // the treadles
                // the beater: hung from the top beams, swung toward the weaver at each pick (the moving part)
                for (int sx = -1; sx <= 1; sx += 2) m.rgb(sx * 0.55f, -0.3f, 0, 0.05f, 0.6f, 0.05f, wood);
                m.rgb(0, -0.58f, 0, 1.14f, 0.07f, 0.07f, wood);
                m.rgb(0, -0.47f, 0, 1.06f, 0.16f, 0.015f, 0xA89878);           // the reed
                pivot = new float[]{0, 1.42f, -0.1f};
            }
            case FULLING_TUB -> {
                // a long staved tub of water, the cloth heaped in it (the moving part: trodden down and springing up)
                for (int sx = -1; sx <= 1; sx += 2) f.rgb(sx * 0.48f, 0.3f, 0, 0.06f, 0.6f, 0.9f, wood);
                for (int sz = -1; sz <= 1; sz += 2) f.rgb(0, 0.3f, sz * 0.42f, 0.9f, 0.6f, 0.06f, wood);
                f.rgb(0, 0.03f, 0, 0.92f, 0.06f, 0.86f, dark);
                for (int y = 0; y < 2; y++) f.rgb(0, 0.15f + y * 0.32f, 0, 1.0f, 0.04f, 0.96f, iron);   // the hoops
                f.rgb(0, 0.46f, 0, 0.88f, 0.03f, 0.8f, 0x4A6A7A);                                        // the water
                m.rgb(0, 0, 0, 0.7f, 0.12f, 0.6f, 0xD8CCAC);
                m.rgb(0.1f, 0.07f, -0.1f, 0.4f, 0.08f, 0.3f, 0xCCC0A0);
                pivot = new float[]{0, 0.46f, 0};
            }
            case DYE_VAT -> {
                // a round vat on a hearth of stones, deep red within, the cloth's corner hung over its rim; the pole stirs
                for (int i = 0; i < 12; i++) { double a = i * Math.PI / 6; f.rgb((float) Math.cos(a) * 0.38f, 0.4f, (float) Math.sin(a) * 0.38f, 0.16f, 0.8f, 0.16f, i % 2 == 0 ? wood : 0x7A5430); }
                f.rgb(0, 0.04f, 0, 0.9f, 0.08f, 0.9f, 0x6A6460);
                f.rgb(0, 0.76f, 0, 0.66f, 0.03f, 0.66f, 0x7A1E22);
                f.rgb(0.32f, 0.7f, -0.24f, 0.16f, 0.3f, 0.02f, 0x9A3A3A);
                for (int y = 0; y < 2; y++) f.rgb(0, 0.2f + y * 0.45f, 0, 0.92f, 0.04f, 0.92f, iron);
                m.rgb(0, 0.35f, 0, 0.04f, 1.1f, 0.04f, 0x9A7A4A);
                m.rgb(0, -0.18f, 0, 0.05f, 0.08f, 0.05f, 0x7A1E22);   // stained at its end
                pivot = new float[]{0, 0.76f, 0};
            }
            default -> { }
        }
        return new Parts(f.vertices(), m.list.isEmpty() ? null : m.vertices(), pivot);
    }
}
