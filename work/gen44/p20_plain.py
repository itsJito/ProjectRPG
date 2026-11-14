def patch(p, pairs):
    s = open(p, encoding='utf-8').read()
    for a, b in pairs:
        assert s.count(a) == 1, (p, a[:100], s.count(a))
        s = s.replace(a, b)
    open(p, 'w', encoding='utf-8').write(s)
R = 'rpggame/src/main/java/rpg/'
patch(R + 'render/Furnishings.java', [
("""    public static Layer[] fireplace(float w, int seed) {
        Meshes.Boxes st""",
 """    public static Layer[] fireplace(float w, int seed) {
        if (seed >= PLAIN) return hearth(w, seed - PLAIN);
        Meshes.Boxes st"""),
("""    /**
     * A chandelier: a chain from the ceiling""",
 """    /** (0.4.4) A plain piece's seed offset: the working household's version of a fine one (a stone hearth for a marble fireplace). */
    public static final int PLAIN = 100;

    /**
     * (0.4.4, gamefixes: interiors culturally appropriate and consistent with each building's wealth) A working hearth:
     * rough fieldstone round the fire, a squared oak beam for a lintel and a plank shelf on it with a jug and a pot, a
     * blackened iron pot hung over the grate from a crane, the hearthstone worn before it.
     */
    static Layer[] hearth(float w, int seed) {
        Meshes.Boxes st = new Meshes.Boxes(), dark = new Meshes.Boxes(), m = new Meshes.Boxes(), wd = new Meshes.Boxes(), cer = new Meshes.Boxes();
        float fw = Math.max(1.4f, w - 0.1f), depth = 0.75f, back = 0.5f, lintel = 1.15f;
        java.util.Random rnd = new java.util.Random(7 + seed);
        // the stone cheeks, a course at a time, each stone its own shade
        for (float side : new float[]{-1, 1}) for (int k = 0; k < 5; k++) {
            float y = 0.12f + k * 0.22f, x = side * (fw / 2 - 0.17f) + (rnd.nextFloat() - 0.5f) * 0.03f;
            st.rgb(x, y, back - depth / 2, 0.34f + rnd.nextFloat() * 0.05f, 0.2f, depth - 0.04f, shade(STONE, 0.82f + rnd.nextFloat() * 0.3f));
        }
        st.rgb(0, lintel + 0.42f, back - depth / 2 + 0.08f, fw, 0.62f, depth - 0.2f, shade(STONE, 0.9f));        // the breast over the lintel
        dark.rgb(0, 0.5f, back - 0.12f, fw - 0.66f, 1.0f, 0.2f, SOOT);
        dark.rgb(0, 0.95f, back - depth / 2 + 0.05f, fw - 0.66f, 0.3f, depth - 0.25f, shade(SOOT, 1.4f));        // the smoke-blackened throat
        wd.rgb(0, lintel, back - depth / 2 - 0.02f, fw + 0.08f, 0.16f, 0.2f, OAK);                              // the oak lintel beam
        wd.rgb(0, lintel + 0.12f, back - depth / 2 - 0.08f, fw + 0.12f, 0.04f, 0.26f, WALNUT);                 // the shelf on it
        st.rgb(0, 0.01f, back - depth / 2 - 0.3f, fw + 0.2f, 0.02f, 0.5f, STONE_DARK);                          // the hearthstone
        // the grate's logs and the pot on its crane
        wd.rgb(0, 0.09f, back - depth / 2 + 0.05f, 0.7f, 0.1f, 0.1f, 0x5A3A22); wd.rgb(0.05f, 0.17f, back - depth / 2 + 0.1f, 0.6f, 0.09f, 0.09f, 0x4A2E1A);
        m.rgb(-fw / 2 + 0.4f, 0.75f, back - depth / 2 + 0.05f, 0.04f, 0.9f, 0.04f, IRON);
        m.rgb(-fw / 2 + 0.62f, 1.1f, back - depth / 2 + 0.05f, 0.46f, 0.03f, 0.03f, IRON);
        m.rgb(-fw / 2 + 0.82f, 0.88f, back - depth / 2 + 0.05f, 0.015f, 0.42f, 0.015f, IRON);
        m.rgb(-fw / 2 + 0.82f, 0.56f, back - depth / 2 + 0.05f, 0.3f, 0.24f, 0.3f, 0x2A2A2E);                  // the pot
        // on the shelf: a jug and a crock
        cer.rgb(-fw / 2 + 0.35f, lintel + 0.25f, back - depth / 2 - 0.06f, 0.12f, 0.2f, 0.12f, 0xB8865A);
        cer.rgb(fw / 2 - 0.4f, lintel + 0.22f, back - depth / 2 - 0.06f, 0.16f, 0.14f, 0.16f, CERAMIC);
        return new Layer[]{stone(st), new Layer(dark, 1, 0, 0), metal(m), wood(wd), stone(cer)};
    }

    /**
     * A chandelier: a chain from the ceiling"""),
])
patch(R + 'objects/Furniture.java', [
("""    /** (0.3.8) A fireplace's fire: game minutes of wood left in the grate (0: cold). */
    public float fuel;""",
 """    /** (0.3.8) A fireplace's fire: game minutes of wood left in the grate (0: cold). */
    public float fuel;
    /** (0.4.4) A working household's version of the piece (a stone hearth, not a marble fireplace). */
    public boolean plain;"""),
])
patch(R + 'render/Gfx.java', [
("""                if (fu.drawKey == null) fu.drawKey = "furn:" + fu.kind + ":" + w + "x" + d + "x" + fu.h + ":" + (own ? fu.id % 6 : 0);
                drawLayers(fu.drawKey, () -> Props.furniture(fu.kind, w, d, fu.h, (int) (own ? fu.id % 6 : 0)));""",
 """                int seed = (int) (own ? fu.id % 6 : 0) + (fu.plain ? Furnishings.PLAIN : 0);
                if (fu.drawKey == null) fu.drawKey = "furn:" + fu.kind + ":" + w + "x" + d + "x" + fu.h + ":" + seed;
                drawLayers(fu.drawKey, () -> Props.furniture(fu.kind, w, d, fu.h, seed));"""),
])
patch(R + 'world/shadowveil/Rooms.java', [
("""            Furniture f = m.add(new Furniture(m.id(), kind, p.x0(), p.z0(), p.x1(), p.z1(), Math.max(1, p.h()), p.facing(), 0, true, p.y()));""",
 """            Furniture f = m.add(new Furniture(m.id(), kind, p.x0(), p.z0(), p.x1(), p.z1(), Math.max(1, p.h()), p.facing(), 0, true, p.y()));
            Architecture.Fitted fit = ar.fitted.get(p.owner());
            f.plain = fit != null && (fit.wealth.equals("poor") || fit.wealth.equals("common"));
            // a lived-in home's hearth is laid and lit
            if (kind == Furniture.Kind.FIREPLACE) f.fuel = 600;"""),
])
# the review camera before a piece's front (not along it)
patch(R + 'Gallery.java', [
("""            for (var fu : site.furniture) if (fu.kind == k && fu.floor == r.y && in.test(new float[]{fu.x, fu.z})) return new float[]{fu.x, fu.z};""",
 """            for (var fu : site.furniture) if (fu.kind == k && fu.floor == r.y && in.test(new float[]{fu.x, fu.z})) {
                // its front: away from the side it backs onto (quarter turns from +z)
                float bx = (float) Math.sin(fu.yaw()), bz = (float) Math.cos(fu.yaw());
                // (a counter faces its customers: its facing is its front; a wall piece's and a bed's is its back)
                return fu.kind == rpg.objects.Furniture.Kind.COUNTER ? new float[]{fu.x, fu.z, bx, bz} : new float[]{fu.x, fu.z, -bx, -bz};
            }"""),
("""                    float[] main = principal(site, r);
                    if (main != null) {
                        float vx = cx - main[0], vz = cz - main[1], l = (float) Math.hypot(vx, vz);""",
 """                    float[] main = principal(site, r);
                    if (main != null) {
                        float vx = main.length > 2 ? main[2] : cx - main[0], vz = main.length > 2 ? main[3] : cz - main[1], l = (float) Math.hypot(vx, vz);"""),
])
print('ok')
