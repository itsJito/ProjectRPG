
    // ================================================================== (0.4.8) fifteen more, each as destructible as the rest
    /** A barrel: staves bellied at the middle, three iron hoops, a lid with its bung; on its end. */
    public static Layer[] barrel(int seed) {
        Meshes.Boxes wd = new Meshes.Boxes(), m = new Meshes.Boxes();
        float h = 0.86f;
        int staves = 10;
        for (int i = 0; i < staves; i++) {
            double a = i * Math.PI * 2 / staves;
            for (int k = 0; k < 4; k++) {
                float y0 = k * h / 4, belly = 0.25f + 0.035f * (float) Math.sin((k + 0.5) / 4 * Math.PI);
                float x = (float) Math.cos(a) * belly, z = (float) Math.sin(a) * belly;
                wd.rgb(x, y0 + h / 8, z, 0.15f, h / 4 + 0.005f, 0.15f, shade(OAK, 0.85f + (i % 3) * 0.08f));
            }
        }
        wd.rgb(0, h - 0.01f, 0, 0.46f, 0.03f, 0.46f, shade(OAK, 0.75f));
        wd.rgb(0.1f, h + 0.01f, 0.05f, 0.05f, 0.02f, 0.05f, WALNUT_DARK);
        for (float y : new float[]{0.1f, h / 2, h - 0.1f}) {
            float r = y == h / 2 ? 0.29f : 0.27f;
            for (int i = 0; i < 12; i++) { double a = i * Math.PI / 6; m.rgb((float) Math.cos(a) * r, y, (float) Math.sin(a) * r, 0.16f, 0.04f, 0.16f, IRON); }
        }
        return new Layer[]{wood(wd), metal(m)};
    }
    /** A keg on a cradle: a small cask on its side on two cut supports, a brass tap at its head and a mug under it. */
    public static Layer[] keg(int seed) {
        Meshes.Boxes wd = new Meshes.Boxes(), m = new Meshes.Boxes(), cer = new Meshes.Boxes();
        for (float z : new float[]{-0.2f, 0.2f}) { wd.rgb(-0.2f, 0.12f, z, 0.08f, 0.24f, 0.08f, WALNUT); wd.rgb(0.2f, 0.12f, z, 0.08f, 0.24f, 0.08f, WALNUT); wd.rgb(0, 0.2f, z, 0.5f, 0.06f, 0.08f, WALNUT_DARK); }
        for (int i = 0; i < 10; i++) {
            double a = i * Math.PI * 2 / 10;
            float y = 0.47f + (float) Math.sin(a) * 0.22f, x = (float) Math.cos(a) * 0.22f;
            wd.rgb(x, y, 0, 0.13f, 0.13f, 0.6f, shade(OAK_LIGHT, 0.85f + (i % 2) * 0.1f));
        }
        for (float z : new float[]{-0.22f, 0.22f}) m.rgb(0, 0.47f, z, 0.5f, 0.5f, 0.03f, IRON);
        m.rgb(0, 0.42f, -0.34f, 0.04f, 0.04f, 0.08f, BRASS); m.rgb(0, 0.38f, -0.39f, 0.03f, 0.06f, 0.03f, BRASS);
        cer.rgb(0, 0.1f, -0.38f, 0.1f, 0.16f, 0.1f, 0x8A6A4A);
        return new Layer[]{wood(wd), metal(m), stone(cer)};
    }
    /** A lectern: a stepped foot, a turned pillar, a slanted desk with a great book open on it and a ribbon hanging. */
    public static Layer[] lectern(int seed) {
        Meshes.Boxes wd = new Meshes.Boxes(), c = new Meshes.Boxes(), pg = new Meshes.Boxes();
        wd.rgb(0, 0.04f, 0, 0.5f, 0.08f, 0.44f, WALNUT_DARK); wd.rgb(0, 0.1f, 0, 0.36f, 0.05f, 0.32f, WALNUT);
        wd.rgb(0, 0.55f, 0, 0.12f, 0.9f, 0.12f, WALNUT); wd.rgb(0, 0.32f, 0, 0.16f, 0.12f, 0.16f, shade(WALNUT, 1.1f));
        for (int k = 0; k < 6; k++) wd.rgb(0, 1.0f + k * 0.025f, 0.12f - k * 0.05f, 0.56f, 0.03f, 0.06f, WALNUT);      // the slanted desk, stepped
        for (int k = 0; k < 5; k++) { c.rgb(-0.13f, 1.04f + k * 0.025f, 0.1f - k * 0.05f, 0.24f, 0.03f, 0.06f, RED_DARK); c.rgb(0.13f, 1.04f + k * 0.025f, 0.1f - k * 0.05f, 0.24f, 0.03f, 0.06f, RED_DARK); }
        for (int k = 0; k < 5; k++) { pg.rgb(-0.12f, 1.06f + k * 0.025f, 0.1f - k * 0.05f, 0.2f, 0.02f, 0.055f, LINEN); pg.rgb(0.12f, 1.06f + k * 0.025f, 0.1f - k * 0.05f, 0.2f, 0.02f, 0.055f, LINEN); }
        c.rgb(0.02f, 0.92f, -0.15f, 0.02f, 0.2f, 0.01f, RED_VELVET);
        return new Layer[]{wood(wd), cloth(c), cloth(pg)};
    }
    /** A spinning wheel: the great wheel on its two posts, the treadle, the spindle and a hank of wool on it. */
    public static Layer[] spinningWheel(int seed) {
        Meshes.Boxes wd = new Meshes.Boxes(), c = new Meshes.Boxes();
        wd.rgb(0, 0.32f, 0, 0.12f, 0.08f, 0.9f, OAK);                                   // the bench
        for (float[] l : new float[][]{{-0.08f, -0.38f}, {0.08f, -0.38f}, {0, 0.38f}}) wd.rgb(l[0], 0.16f, l[1], 0.05f, 0.32f, 0.05f, OAK);
        for (float x : new float[]{-0.07f, 0.07f}) wd.rgb(x, 0.62f, 0.2f, 0.04f, 0.6f, 0.04f, OAK_LIGHT);   // the wheel's posts
        int spokes = 16; float rr = 0.32f, cy = 0.78f, cz = 0.2f;
        for (int i = 0; i < spokes; i++) {
            double a = i * Math.PI * 2 / spokes;
            wd.rgb(0, cy + (float) Math.sin(a) * rr, cz + (float) Math.cos(a) * rr, 0.05f, 0.07f, 0.07f, shade(OAK_LIGHT, 0.9f));
            if (i % 2 == 0) for (int k = 1; k < 4; k++) wd.rgb(0, cy + (float) Math.sin(a) * rr * k / 4, cz + (float) Math.cos(a) * rr * k / 4, 0.02f, 0.025f, 0.025f, OAK);
        }
        wd.rgb(0, cy, cz, 0.1f, 0.08f, 0.08f, WALNUT_DARK);
        wd.rgb(0, 0.06f, -0.1f, 0.24f, 0.03f, 0.3f, OAK);                               // the treadle
        wd.rgb(0, 0.58f, -0.3f, 0.03f, 0.3f, 0.03f, OAK); wd.rgb(0, 0.72f, -0.3f, 0.14f, 0.04f, 0.04f, OAK);
        c.rgb(0, 0.72f, -0.38f, 0.1f, 0.08f, 0.08f, 0xE8E0D0); c.rgb(0, 0.8f, -0.42f, 0.06f, 0.06f, 0.06f, 0xD8D0C0);
        return new Layer[]{wood(wd), cloth(c)};
    }
    /** A scroll rack: a wall case of pigeonholes, each holding rolled scrolls, a few with seals and ribbons. */
    public static Layer[] scrollRack(float w, float h, int seed) {
        Meshes.Boxes wd = new Meshes.Boxes(), pg = new Meshes.Boxes(), c = new Meshes.Boxes();
        float tw = w - 0.08f, d = 0.34f, th = Math.min(2.0f, h - 0.1f);
        Random r = new Random(seed);
        int cols = Math.max(2, Math.round(tw / 0.22f)), rows = Math.max(4, Math.round(th / 0.22f));
        float cw = tw / cols, rh = th / rows;
        for (int i = 0; i <= cols; i++) wd.rgb(-tw / 2 + i * cw, th / 2, 0, 0.025f, th, d, WALNUT);
        for (int j = 0; j <= rows; j++) wd.rgb(0, j * rh, 0, tw, 0.025f, d, WALNUT_DARK);
        wd.rgb(0, th / 2, d / 2 - 0.01f, tw, th, 0.02f, WALNUT_DARK);
        for (int i = 0; i < cols; i++) for (int j = 0; j < rows; j++) {
            int n = r.nextInt(4);
            for (int k = 0; k < n; k++) {
                float x = -tw / 2 + (i + 0.3f + k * 0.2f) * cw, y = j * rh + 0.045f + (k % 2) * 0.05f;
                pg.rgb(x, y, -0.02f, 0.05f, 0.05f, d - 0.06f, r.nextBoolean() ? LINEN : 0xD8C8A0);
                if (r.nextInt(4) == 0) c.rgb(x, y, -d / 2 + 0.03f, 0.055f, 0.02f, 0.02f, r.nextBoolean() ? RED_VELVET : BLUE_VELVET);
            }
        }
        return new Layer[]{wood(wd), cloth(pg), cloth(c)};
    }
    /** A throne: a broad seat on a dais step, a tall carved back crowned with gilt, arms ending in lions' heads, a red cushion. */
    public static Layer[] throne(int seed) {
        Meshes.Boxes wd = new Meshes.Boxes(), c = new Meshes.Boxes(), g = new Meshes.Boxes(), st = new Meshes.Boxes();
        st.rgb(0, 0.06f, 0, 1.1f, 0.12f, 0.95f, MARBLE_SHADE);                          // the dais step
        wd.rgb(0, 0.36f, 0, 0.82f, 0.48f, 0.62f, MAHOGANY);
        c.rgb(0, 0.64f, -0.02f, 0.68f, 0.1f, 0.54f, RED_VELVET);
        wd.rgb(0, 1.28f, 0.27f, 0.82f, 1.4f, 0.1f, MAHOGANY);                           // the back
        c.rgb(0, 1.15f, 0.215f, 0.6f, 0.9f, 0.02f, RED_VELVET);
        for (float x : new float[]{-0.36f, 0.36f}) {
            wd.rgb(x, 0.82f, -0.05f, 0.1f, 0.12f, 0.6f, MAHOGANY_DARK);                  // the arms
            g.rgb(x, 0.86f, -0.37f, 0.12f, 0.14f, 0.1f, GILT);                           // the lions' heads
            g.rgb(x, 2.02f, 0.27f, 0.12f, 0.12f, 0.12f, GILT);                           // the finials
        }
        g.rgb(0, 2.02f, 0.27f, 0.6f, 0.08f, 0.12f, GILT_DARK); g.rgb(0, 2.12f, 0.27f, 0.2f, 0.14f, 0.1f, GILT);
        for (float x : new float[]{-0.2f, 0, 0.2f}) g.rgb(x, 1.9f, 0.21f, 0.06f, 0.06f, 0.02f, GILT);
        return new Layer[]{wood(wd), cloth(c), metal(g), stone(st)};
    }
    /** A cradle: a deep wooden box on curved rockers, a hood at its head, a little quilt and pillow inside. */
    public static Layer[] cradle(int seed) {
        Meshes.Boxes wd = new Meshes.Boxes(), c = new Meshes.Boxes();
        for (float x : new float[]{-0.24f, 0.24f}) for (int k = 0; k < 7; k++) { float z = -0.42f + k * 0.14f; wd.rgb(x, 0.05f + Math.abs(z) * Math.abs(z) * 0.35f, z, 0.05f, 0.04f, 0.15f, WALNUT); }
        wd.rgb(0, 0.12f, 0, 0.5f, 0.04f, 0.8f, OAK);
        for (float x : new float[]{-0.24f, 0.24f}) wd.rgb(x, 0.3f, 0, 0.04f, 0.36f, 0.8f, OAK);
        wd.rgb(0, 0.3f, -0.4f, 0.5f, 0.36f, 0.04f, OAK); wd.rgb(0, 0.45f, 0.4f, 0.5f, 0.66f, 0.04f, OAK);
        wd.rgb(0, 0.76f, 0.3f, 0.52f, 0.04f, 0.24f, OAK_LIGHT);                         // the hood
        c.rgb(0, 0.2f, -0.05f, 0.42f, 0.08f, 0.6f, 0xC8D4E8); c.rgb(0, 0.24f, 0.28f, 0.3f, 0.08f, 0.14f, LINEN);
        return new Layer[]{wood(wd), cloth(c)};
    }
    /** A coat rack: a turned pole on splayed feet, pegs near the top, a cloak and a hat hung on it. */
    public static Layer[] coatRack(int seed) {
        Meshes.Boxes wd = new Meshes.Boxes(), c = new Meshes.Boxes();
        for (int i = 0; i < 4; i++) { double a = i * Math.PI / 2 + 0.4; wd.rgb((float) Math.cos(a) * 0.16f, 0.03f, (float) Math.sin(a) * 0.16f, 0.3f * Math.abs((float) Math.cos(a)) + 0.04f, 0.05f, 0.3f * Math.abs((float) Math.sin(a)) + 0.04f, WALNUT); }
        wd.rgb(0, 0.92f, 0, 0.06f, 1.8f, 0.06f, WALNUT); wd.rgb(0, 1.84f, 0, 0.1f, 0.06f, 0.1f, WALNUT_DARK);
        for (int i = 0; i < 4; i++) { double a = i * Math.PI / 2; wd.rgb((float) Math.cos(a) * 0.1f, 1.7f, (float) Math.sin(a) * 0.1f, 0.14f * Math.abs((float) Math.cos(a)) + 0.03f, 0.03f, 0.14f * Math.abs((float) Math.sin(a)) + 0.03f, WALNUT_DARK); }
        Random r = new Random(seed);
        int cloak = r.nextBoolean() ? GREEN_VELVET : 0x5A3A2A;
        c.rgb(0.12f, 1.25f, 0, 0.1f, 0.8f, 0.34f, cloak); c.rgb(0.14f, 1.62f, 0, 0.12f, 0.12f, 0.22f, shade(cloak, 0.85f));
        c.rgb(-0.14f, 1.68f, 0, 0.2f, 0.06f, 0.2f, 0x3A2A1A); c.rgb(-0.14f, 1.74f, 0, 0.12f, 0.08f, 0.12f, 0x3A2A1A);
        return new Layer[]{wood(wd), cloth(c)};
    }
    /** A butter churn: a tall staved tub banded in iron, the dasher's handle standing out of its lid. */
    public static Layer[] butterChurn(int seed) {
        Meshes.Boxes wd = new Meshes.Boxes(), m = new Meshes.Boxes();
        for (int i = 0; i < 8; i++) { double a = i * Math.PI / 4; float r0 = 0.15f; wd.rgb((float) Math.cos(a) * r0, 0.36f, (float) Math.sin(a) * r0, 0.12f, 0.72f, 0.12f, shade(OAK_LIGHT, 0.85f + (i % 2) * 0.1f)); }
        wd.rgb(0, 0.73f, 0, 0.3f, 0.03f, 0.3f, OAK);
        wd.rgb(0, 0.95f, 0, 0.035f, 0.44f, 0.035f, OAK_LIGHT); wd.rgb(0, 1.17f, 0, 0.12f, 0.03f, 0.03f, OAK_LIGHT);
        for (float y : new float[]{0.12f, 0.6f}) for (int i = 0; i < 8; i++) { double a = i * Math.PI / 4; m.rgb((float) Math.cos(a) * 0.17f, y, (float) Math.sin(a) * 0.17f, 0.1f, 0.035f, 0.1f, IRON); }
        return new Layer[]{wood(wd), metal(m)};
    }
    /** A globe of the heavens on a brass meridian, in a ring stand on three legs. */
    public static Layer[] globe(int seed) {
        Meshes.Boxes wd = new Meshes.Boxes(), m = new Meshes.Boxes(), s = new Meshes.Boxes();
        for (int i = 0; i < 3; i++) { double a = i * Math.PI * 2 / 3; wd.rgb((float) Math.cos(a) * 0.17f, 0.32f, (float) Math.sin(a) * 0.17f, 0.04f, 0.64f, 0.04f, WALNUT); }
        for (int i = 0; i < 16; i++) { double a = i * Math.PI / 8; wd.rgb((float) Math.cos(a) * 0.26f, 0.64f, (float) Math.sin(a) * 0.26f, 0.1f, 0.03f, 0.1f, WALNUT_DARK); }
        Random r = new Random(seed);
        float cy = 0.86f, rr = 0.2f;
        for (int i = -4; i <= 4; i++) for (int j = 0; j < 12; j++) {
            double lat = i * Math.PI / 10, lon = j * Math.PI / 6;
            float x = (float) (Math.cos(lat) * Math.cos(lon)) * rr, y = (float) Math.sin(lat) * rr, z = (float) (Math.cos(lat) * Math.sin(lon)) * rr;
            s.rgb(x, cy + y, z, 0.07f, 0.06f, 0.07f, r.nextInt(3) == 0 ? 0x6A8A4A : 0x3A5A8A);
        }
        for (int i = 0; i < 20; i++) { double a = i * Math.PI / 10; m.rgb(0, cy + (float) Math.sin(a) * (rr + 0.03f), (float) Math.cos(a) * (rr + 0.03f), 0.02f, 0.03f, 0.03f, BRASS); }
        return new Layer[]{wood(wd), metal(m), stone(s)};
    }
    /** A tool rack on the wall: a board with pegs holding a hammer, saw, tongs, chisels and a coil of rope. */
    public static Layer[] toolRack(float w, int seed) {
        Meshes.Boxes wd = new Meshes.Boxes(), m = new Meshes.Boxes(), c = new Meshes.Boxes();
        float tw = Math.max(0.8f, w - 0.1f);
        wd.rgb(0, 1.45f, 0.47f, tw, 0.5f, 0.04f, OAK);
        for (int i = 0; i < 6; i++) wd.rgb(-tw / 2 + 0.1f + i * (tw - 0.2f) / 5, 1.62f, 0.42f, 0.03f, 0.03f, 0.08f, WALNUT_DARK);
        float x = -tw / 2 + 0.1f;
        wd.rgb(x, 1.42f, 0.41f, 0.04f, 0.32f, 0.04f, OAK_LIGHT); m.rgb(x, 1.6f, 0.41f, 0.14f, 0.06f, 0.06f, IRON);                          // a hammer
        x += (tw - 0.2f) / 5;
        m.rgb(x + 0.05f, 1.42f, 0.43f, 0.22f, 0.28f, 0.01f, IRON_LIGHT); wd.rgb(x - 0.07f, 1.56f, 0.42f, 0.06f, 0.1f, 0.03f, OAK);      // a saw
        x += (tw - 0.2f) / 5 * 1.3f;
        m.rgb(x - 0.02f, 1.42f, 0.42f, 0.02f, 0.4f, 0.02f, IRON); m.rgb(x + 0.02f, 1.42f, 0.42f, 0.02f, 0.4f, 0.02f, IRON);             // tongs
        x += (tw - 0.2f) / 5;
        for (int k = 0; k < 3; k++) { wd.rgb(x + k * 0.05f, 1.5f, 0.42f, 0.025f, 0.12f, 0.025f, OAK); m.rgb(x + k * 0.05f, 1.39f, 0.42f, 0.02f, 0.1f, 0.01f, IRON_LIGHT); }
        x += (tw - 0.2f) / 5;
        for (int k = 0; k < 8; k++) { double a = k * Math.PI / 4; c.rgb(x + (float) Math.cos(a) * 0.09f, 1.42f + (float) Math.sin(a) * 0.09f, 0.42f, 0.05f, 0.05f, 0.05f, 0xB89A6A); }
        return new Layer[]{wood(wd), metal(m), cloth(c)};
    }
    /** A map table: a trestle table with a great chart spread on it, held down by a candle, a dagger and markers. */
    public static Layer[] mapTable(float w, float d, int seed) {
        Meshes.Boxes wd = new Meshes.Boxes(), pg = new Meshes.Boxes(), m = new Meshes.Boxes(), wax = new Meshes.Boxes(), fl = new Meshes.Boxes(), mk = new Meshes.Boxes();
        float tw = Math.max(1.2f, w - 0.1f), td = Math.max(0.8f, d - 0.1f), top = 0.8f;
        wd.rgb(0, top - 0.025f, 0, tw, 0.05f, td, OAK);
        for (float x : new float[]{-tw / 2 + 0.15f, tw / 2 - 0.15f}) { wd.rgb(x, (top - 0.05f) / 2, 0, 0.08f, top - 0.05f, td - 0.2f, OAK); wd.rgb(x, 0.04f, 0, 0.12f, 0.08f, td - 0.1f, WALNUT); }
        wd.rgb(0, 0.3f, 0, tw - 0.3f, 0.06f, 0.06f, WALNUT);
        pg.rgb(0, top + 0.005f, 0, tw * 0.8f, 0.01f, td * 0.75f, 0xD8C8A0);
        Random r = new Random(seed);
        for (int k = 0; k < 9; k++) pg.rgb((r.nextFloat() - 0.5f) * tw * 0.7f, top + 0.012f, (r.nextFloat() - 0.5f) * td * 0.65f, 0.04f + r.nextFloat() * 0.18f, 0.004f, 0.01f + r.nextFloat() * 0.03f, k % 3 == 0 ? 0x4A6A9A : 0x6A5A3A);
        for (int k = 0; k < 6; k++) mk.rgb((r.nextFloat() - 0.5f) * tw * 0.6f, top + 0.04f, (r.nextFloat() - 0.5f) * td * 0.5f, 0.035f, 0.06f, 0.035f, k % 2 == 0 ? RED_VELVET : BLUE_VELVET);
        m.rgb(tw * 0.25f, top + 0.015f, td * 0.2f, 0.3f, 0.015f, 0.03f, IRON_LIGHT); m.rgb(tw * 0.25f - 0.17f, top + 0.02f, td * 0.2f, 0.06f, 0.025f, 0.04f, BRASS);
        candle(m, wax, fl, -tw * 0.32f, top, -td * 0.28f, 0.12f);
        return new Layer[]{wood(wd), cloth(pg), metal(m), cloth(mk), cloth(wax), glow(fl, 2.2f)};
    }
    /** A display cabinet: a tall glazed case on short legs, its shelves set with plates, goblets and curios. */
    public static Layer[] displayCabinet(float w, float h, int seed) {
        Meshes.Boxes wd = new Meshes.Boxes(), gl = new Meshes.Boxes(), cer = new Meshes.Boxes(), m = new Meshes.Boxes();
        float tw = w - 0.12f, d = 0.42f, th = Math.min(2.0f, h - 0.15f);
        for (float x : new float[]{-tw / 2 + 0.04f, tw / 2 - 0.04f}) { wd.rgb(x, th / 2, 0, 0.06f, th, d, MAHOGANY); wd.rgb(x, 0.05f, -d / 2 + 0.05f, 0.07f, 0.1f, 0.07f, MAHOGANY_DARK); }
        wd.rgb(0, th - 0.03f, 0, tw + 0.06f, 0.08f, d + 0.04f, MAHOGANY_DARK); wd.rgb(0, 0.13f, 0, tw, 0.06f, d, MAHOGANY_DARK);
        wd.rgb(0, th / 2, d / 2 - 0.015f, tw, th, 0.03f, MAHOGANY_DARK);
        Random r = new Random(seed);
        for (int k = 0; k < 4; k++) {
            float sy = 0.18f + k * (th - 0.3f) / 4;
            wd.rgb(0, sy, 0, tw - 0.08f, 0.025f, d - 0.04f, MAHOGANY);
            for (float x = -tw / 2 + 0.16f; x < tw / 2 - 0.12f; x += 0.2f) {
                int pick = r.nextInt(3);
                if (pick == 0) cer.rgb(x, sy + 0.12f, 0.1f, 0.18f, 0.18f, 0.02f, r.nextBoolean() ? CERAMIC : CERAMIC_BLUE);
                else if (pick == 1) { m.rgb(x, sy + 0.03f, -0.02f, 0.06f, 0.03f, 0.06f, GILT); m.rgb(x, sy + 0.09f, -0.02f, 0.02f, 0.09f, 0.02f, GILT); m.rgb(x, sy + 0.15f, -0.02f, 0.07f, 0.05f, 0.07f, GILT); }
                else cer.rgb(x, sy + 0.07f, -0.02f, 0.09f, 0.12f, 0.09f, r.nextBoolean() ? 0x6A8A9A : 0x8A5A3A);
            }
        }
        gl.rgb(0, th / 2 + 0.05f, -d / 2 + 0.01f, tw - 0.06f, th - 0.2f, 0.01f, 0xC8D8E0);
        wd.rgb(0, th / 2 + 0.05f, -d / 2, 0.025f, th - 0.2f, 0.02f, MAHOGANY_DARK);
        return new Layer[]{wood(wd), glass(gl), stone(cer), metal(m)};
    }
    /** A folding screen: three tall leaves of framed cloth standing in a zigzag. */
    public static Layer[] foldingScreen(float w, int seed) {
        Meshes.Boxes wd = new Meshes.Boxes(), c = new Meshes.Boxes();
        Random r = new Random(seed);
        int cloth = new int[]{RED_VELVET, GREEN_VELVET, BLUE_VELVET, 0xC8B080}[r.nextInt(4)];
        float lw = Math.max(0.4f, (w - 0.1f) / 3), hh = 1.7f;
        for (int i = 0; i < 3; i++) {
            float x = -lw + i * lw, z = i % 2 == 0 ? -0.08f : 0.08f;
            wd.rgb(x - lw / 2 + 0.02f, hh / 2, z, 0.04f, hh, 0.04f, WALNUT); wd.rgb(x + lw / 2 - 0.02f, hh / 2, z, 0.04f, hh, 0.04f, WALNUT);
            wd.rgb(x, hh - 0.02f, z, lw, 0.04f, 0.04f, WALNUT); wd.rgb(x, 0.12f, z, lw, 0.04f, 0.04f, WALNUT);
            c.rgb(x, hh / 2 + 0.05f, z, lw - 0.06f, hh - 0.2f, 0.015f, i == 1 ? shade(cloth, 0.9f) : cloth);
        }
        return new Layer[]{wood(wd), cloth(c)};
    }
    /** A pile of sacks: grain and flour in bulging sacking, tied at the neck, heaped against one another. */
    public static Layer[] sacks(float w, int seed) {
        Meshes.Boxes c = new Meshes.Boxes(), t = new Meshes.Boxes();
        Random r = new Random(seed);
        int n = Math.max(3, Math.round(w / 0.35f) + 1);
        for (int i = 0; i < n; i++) {
            float x = -w / 2 + 0.25f + (w - 0.5f) * i / Math.max(1, n - 1), z = (r.nextFloat() - 0.5f) * 0.15f, lie = i % 3 == 2 ? 0.28f : 0;
            int col = r.nextBoolean() ? 0xB8A07A : 0xA88E66;
            c.rgb(x, 0.2f + lie, z, 0.34f, 0.38f, 0.3f, col); c.rgb(x, 0.42f + lie, z, 0.26f, 0.08f, 0.22f, shade(col, 0.92f));
            t.rgb(x, 0.49f + lie, z, 0.08f, 0.06f, 0.08f, 0x8A6A3A);
            if (r.nextInt(3) == 0) t.rgb(x, 0.3f + lie, z - 0.16f, 0.14f, 0.12f, 0.005f, 0x4A3A2A);   // a stencilled mark
        }
        return new Layer[]{cloth(c), cloth(t)};
    }
