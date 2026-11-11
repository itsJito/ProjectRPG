package rpg.render;

import java.util.Random;
import rpg.render.Props.Layer;

/**
 * (0.3.8, gamefixes: the DevMansion's interior - every object modelled, each to be reused in any interior) The house
 * furnishings, each built from coloured boxes in metres like the rest of {@link Props}: the footprint's middle on the
 * floor at the origin, the front toward -z, a wall piece's back against +z, a hanging piece's top at the ceiling. They
 * are sized to the cells they are given (w across, d deep, h tall), so one model serves a short table or a long one, a
 * low room or a tall one; a seed varies the ones that should not all look alike (paintings, rugs, books, quilts).
 * Drawn in layers by material: wood, cloth, metal (iron, brass, gilt), stone, glass, and what glows (candle flames, the
 * fire in a grate).
 */
public final class Furnishings {
    private Furnishings() {}

    // the house's palette: warm woods, rich cloth, gilt and brass, pale stone
    static final int WALNUT = 0x5A3820, WALNUT_DARK = 0x3E2614, OAK = 0x8E5E34, OAK_LIGHT = 0xAA7444, MAHOGANY = 0x6E2C1C, MAHOGANY_DARK = 0x4A1C12,
            GILT = 0xD8A840, GILT_DARK = 0xA07A28, BRASS = 0xC8A050, IRON = 0x3A3A40, IRON_LIGHT = 0x5E5E66, PEWTER = 0x9A9EA4,
            RED_VELVET = 0x8A1E28, RED_DARK = 0x5E1218, GREEN_VELVET = 0x2E5A3A, BLUE_VELVET = 0x2A3A7A, LINEN = 0xE8E0CC, LINEN_SHADE = 0xC8BEA8,
            MARBLE = 0xE6E2D8, MARBLE_SHADE = 0xC4C0B6, STONE = 0x8E8A84, STONE_DARK = 0x5E5A56, SOOT = 0x1E1A18, WAX = 0xF2EAD4, FLAME = 0xFFC060,
            GLASS_GREEN = 0x3E6A42, GLASS_AMBER = 0x8A5A20, CERAMIC = 0xE8E4D8, CERAMIC_BLUE = 0x3A5AA0, COPPER = 0xB8683A, LEAF = 0x3E7A2E, LEAF_DARK = 0x2A5A22;

    static Layer wood(Meshes.Boxes b) { return new Layer(b, 0.62f, 0, 0); }
    static Layer cloth(Meshes.Boxes b) { return new Layer(b, 0.96f, 0, 0); }
    static Layer metal(Meshes.Boxes b) { return new Layer(b, 0.32f, 0.85f, 0); }
    static Layer stone(Meshes.Boxes b) { return new Layer(b, 0.5f, 0, 0); }
    static Layer glass(Meshes.Boxes b) { return new Layer(b, 0.08f, 0.1f, 0); }
    static Layer glow(Meshes.Boxes b, float e) { return new Layer(b, 0.9f, 0, e); }

    static int shade(int rgb, float k) {
        int r = Math.min(255, (int) (((rgb >> 16) & 255) * k)), g = Math.min(255, (int) (((rgb >> 8) & 255) * k)), b = Math.min(255, (int) ((rgb & 255) * k));
        return r << 16 | g << 8 | b;
    }

    /** A turned leg: a block foot, a bulb, a slim shaft, a collar under the top. */
    static void turnedLeg(Meshes.Boxes b, float x, float z, float h, float t, int col) {
        b.rgb(x, 0.03f, z, t * 1.3f, 0.06f, t * 1.3f, shade(col, 0.85f));
        b.rgb(x, h * 0.28f, z, t * 1.25f, h * 0.12f, t * 1.25f, col);
        b.rgb(x, h * 0.55f, z, t * 0.8f, h * 0.5f, t * 0.8f, col);
        b.rgb(x, h - 0.05f, z, t * 1.1f, 0.1f, t * 1.1f, shade(col, 1.1f));
    }

    /** A candle in a cup: the cup (metal), the candle (wax), its flame (glow). */
    static void candle(Meshes.Boxes metal, Meshes.Boxes wax, Meshes.Boxes flame, float x, float y, float z, float len) {
        metal.rgb(x, y + 0.012f, z, 0.07f, 0.024f, 0.07f, BRASS);
        wax.rgb(x, y + 0.024f + len / 2, z, 0.032f, len, 0.032f, WAX);
        flame.rgb(x, y + 0.024f + len + 0.022f, z, 0.018f, 0.04f, 0.018f, FLAME);
    }

    // ------------------------------------------------------------------ seats
    /** A high-backed dining chair: carved crest rail, turned front legs, a stuffed seat in red velvet. */
    public static Layer[] chair(int seed) {
        Meshes.Boxes w = new Meshes.Boxes(), c = new Meshes.Boxes(), m = new Meshes.Boxes();
        float seat = 0.46f, s = 0.46f;
        turnedLeg(w, -s / 2 + 0.03f, -s / 2 + 0.03f, seat - 0.06f, 0.05f, WALNUT);
        turnedLeg(w, s / 2 - 0.03f, -s / 2 + 0.03f, seat - 0.06f, 0.05f, WALNUT);
        for (float x : new float[]{-s / 2 + 0.03f, s / 2 - 0.03f}) w.rgb(x, 0.55f, s / 2 - 0.03f, 0.05f, 1.1f, 0.05f, WALNUT);   // back legs run up into the back
        w.rgb(0, seat - 0.06f, 0, s, 0.05f, s, WALNUT_DARK);                                                                   // the seat rail
        w.rgb(0, 0.16f, 0, s - 0.06f, 0.025f, 0.025f, WALNUT); w.rgb(0, 0.16f, -s / 2 + 0.03f, s - 0.06f, 0.025f, 0.025f, WALNUT);
        c.rgb(0, seat - 0.005f, -0.005f, s - 0.03f, 0.06f, s - 0.04f, RED_VELVET);                                               // the cushion
        c.rgb(0, seat + 0.022f, -0.005f, s - 0.08f, 0.012f, s - 0.08f, shade(RED_VELVET, 1.12f));
        // the back: a splat between two rails, a carved crest over it
        w.rgb(0, 0.66f, s / 2 - 0.03f, s - 0.1f, 0.05f, 0.03f, WALNUT);
        c.rgb(0, 0.83f, s / 2 - 0.025f, s - 0.14f, 0.28f, 0.03f, RED_VELVET);
        w.rgb(0, 1.0f, s / 2 - 0.03f, s - 0.04f, 0.07f, 0.04f, WALNUT_DARK);
        w.rgb(0, 1.06f, s / 2 - 0.03f, 0.16f, 0.05f, 0.035f, WALNUT_DARK);
        for (float x : new float[]{-0.12f, 0.12f}) m.rgb(x, 0.83f, s / 2 - 0.045f, 0.015f, 0.015f, 0.01f, BRASS);           // the upholstery nails
        return new Layer[]{wood(w), cloth(c), metal(m)};
    }

    /** A wingback armchair: deep and padded, rolled arms, wings at the head, short cabriole legs. */
    public static Layer[] armchair(int seed) {
        Meshes.Boxes w = new Meshes.Boxes(), c = new Meshes.Boxes();
        int cloth = seed % 3 == 0 ? GREEN_VELVET : seed % 3 == 1 ? RED_VELVET : BLUE_VELVET;
        float s = 0.86f, seat = 0.42f;
        for (float x : new float[]{-s / 2 + 0.07f, s / 2 - 0.07f}) for (float z : new float[]{-s / 2 + 0.07f, s / 2 - 0.07f}) {
            w.rgb(x, 0.06f, z, 0.07f, 0.12f, 0.07f, MAHOGANY); w.rgb(x + (x < 0 ? -0.01f : 0.01f), 0.015f, z - 0.015f, 0.08f, 0.03f, 0.09f, MAHOGANY_DARK);
        }
        c.rgb(0, 0.22f, 0.02f, s - 0.04f, 0.2f, s - 0.08f, shade(cloth, 0.85f));                       // the base
        c.rgb(0, seat - 0.04f, -0.04f, s - 0.28f, 0.12f, s - 0.2f, cloth);                               // the seat cushion
        c.rgb(0, seat + 0.025f, -0.04f, s - 0.32f, 0.02f, s - 0.24f, shade(cloth, 1.14f));
        for (float x : new float[]{-s / 2 + 0.08f, s / 2 - 0.08f}) {
            c.rgb(x, 0.48f, 0.0f, 0.16f, 0.36f, s - 0.12f, cloth);                                       // the arms
            c.rgb(x, 0.67f, -0.04f, 0.19f, 0.06f, s - 0.18f, shade(cloth, 1.1f));                         // rolled over
        }
        c.rgb(0, 0.82f, s / 2 - 0.1f, s - 0.04f, 0.92f, 0.18f, cloth);                                    // the back
        c.rgb(0, 0.86f, s / 2 - 0.2f, s - 0.32f, 0.62f, 0.04f, shade(cloth, 1.12f));                      // its padding
        for (float x : new float[]{-s / 2 + 0.06f, s / 2 - 0.06f}) c.rgb(x, 1.03f, s / 2 - 0.22f, 0.12f, 0.42f, 0.3f, shade(cloth, 0.95f));   // the wings
        c.rgb(0, 1.29f, s / 2 - 0.1f, s - 0.14f, 0.07f, 0.17f, shade(cloth, 1.05f));
        return new Layer[]{wood(w), cloth(c)};
    }

    /** A settee: a long padded seat for two or three, a buttoned back, scrolled arms, turned feet. */
    public static Layer[] sofa(float w, int seed) {
        Meshes.Boxes wd = new Meshes.Boxes(), c = new Meshes.Boxes(), m = new Meshes.Boxes();
        int cloth = seed % 2 == 0 ? GREEN_VELVET : RED_VELVET;
        float len = w - 0.1f, d = 0.84f;
        for (float x : new float[]{-len / 2 + 0.07f, 0, len / 2 - 0.07f}) for (float z : new float[]{-d / 2 + 0.08f, d / 2 - 0.08f}) wd.rgb(x, 0.06f, z, 0.07f, 0.12f, 0.07f, WALNUT);
        wd.rgb(0, 0.15f, -d / 2 + 0.05f, len - 0.04f, 0.07f, 0.04f, WALNUT_DARK);                          // the carved apron
        c.rgb(0, 0.25f, 0, len, 0.18f, d - 0.04f, shade(cloth, 0.85f));
        int seats = Math.max(2, Math.round(len / 0.62f));
        float sw = (len - 0.3f) / seats;
        for (int i = 0; i < seats; i++) {
            float x = -len / 2 + 0.15f + sw * (i + 0.5f);
            c.rgb(x, 0.4f, -0.05f, sw - 0.02f, 0.12f, d - 0.24f, cloth);
            c.rgb(x, 0.465f, -0.05f, sw - 0.06f, 0.015f, d - 0.28f, shade(cloth, 1.14f));
        }
        c.rgb(0, 0.74f, d / 2 - 0.1f, len, 0.6f, 0.16f, cloth);
        for (int i = 0; i < seats * 2; i++) m.rgb(-len / 2 + 0.25f + i * (len - 0.5f) / Math.max(1, seats * 2 - 1), 0.78f, d / 2 - 0.185f, 0.02f, 0.02f, 0.01f, BRASS);   // the buttons
        for (float x : new float[]{-len / 2 + 0.07f, len / 2 - 0.07f}) { c.rgb(x, 0.5f, 0, 0.14f, 0.34f, d - 0.06f, cloth); c.rgb(x, 0.69f, -0.03f, 0.17f, 0.06f, d - 0.12f, shade(cloth, 1.1f)); }
        wd.rgb(0, 1.06f, d / 2 - 0.09f, len * 0.4f, 0.06f, 0.1f, WALNUT_DARK);                              // the crest
        return new Layer[]{wood(wd), cloth(c), metal(m)};
    }

    /** A plain kitchen stool: a round-ish top on three splayed legs. */
    public static Layer[] stool() {
        Meshes.Boxes w = new Meshes.Boxes();
        w.rgb(0, 0.44f, 0, 0.34f, 0.05f, 0.34f, OAK);
        w.rgb(0, 0.445f, 0, 0.26f, 0.06f, 0.4f, OAK); w.rgb(0, 0.445f, 0, 0.4f, 0.06f, 0.26f, OAK);
        for (float[] p : new float[][]{{-0.12f, -0.1f}, {0.12f, -0.1f}, {0, 0.13f}}) w.rgb(p[0], 0.21f, p[1], 0.045f, 0.42f, 0.045f, shade(OAK, 0.85f));
        w.rgb(0, 0.14f, 0, 0.22f, 0.03f, 0.03f, shade(OAK, 0.8f));
        return new Layer[]{wood(w)};
    }

    // ------------------------------------------------------------------ tables and case pieces
    /** A long dining table: a thick top with a moulded edge, turned legs, a linen runner, candlesticks and a bowl of fruit. */
    public static Layer[] diningTable(float w, float d, int seed) {
        Meshes.Boxes wd = new Meshes.Boxes(), c = new Meshes.Boxes(), m = new Meshes.Boxes(), wax = new Meshes.Boxes(), fl = new Meshes.Boxes(), f = new Meshes.Boxes();
        float top = 0.8f, tw = w - 0.12f, td = d - 0.2f;
        wd.rgb(0, top - 0.035f, 0, tw, 0.07f, td, MAHOGANY);
        wd.rgb(0, top - 0.08f, 0, tw - 0.04f, 0.03f, td - 0.04f, MAHOGANY_DARK);
        wd.rgb(0, top - 0.13f, 0, tw - 0.24f, 0.08f, td - 0.24f, MAHOGANY);                                 // the apron
        int legs = Math.max(2, Math.round(tw / 1.6f) + 1);
        for (int i = 0; i < legs; i++) {
            float x = -tw / 2 + 0.14f + i * (tw - 0.28f) / (legs - 1);
            for (float z : new float[]{-td / 2 + 0.14f, td / 2 - 0.14f}) turnedLeg(wd, x, z, top - 0.17f, 0.075f, MAHOGANY);
        }
        c.rgb(0, top + 0.004f, 0, tw - 0.5f, 0.01f, 0.42f, LINEN);                                           // the runner
        c.rgb(-tw / 2 + 0.25f, top - 0.06f, 0, 0.01f, 0.12f, 0.42f, LINEN); c.rgb(tw / 2 - 0.25f, top - 0.06f, 0, 0.01f, 0.12f, 0.42f, LINEN);
        // place settings down both sides
        Random r = new Random(seed);
        int places = Math.max(1, Math.round((tw - 0.4f) / 0.7f));
        for (int i = 0; i < places; i++) {
            float x = -tw / 2 + 0.35f + (i + 0.5f) * (tw - 0.7f) / places;
            for (float z : new float[]{-td / 2 + 0.22f, td / 2 - 0.22f}) {
                m.rgb(x, top + 0.008f, z, 0.24f, 0.016f, 0.24f, PEWTER);                                     // the plate
                m.rgb(x + 0.17f, top + 0.006f, z, 0.02f, 0.012f, 0.18f, PEWTER); m.rgb(x - 0.17f, top + 0.006f, z, 0.02f, 0.012f, 0.18f, PEWTER);
                m.rgb(x + 0.12f, top + 0.06f, z + (z < 0 ? 0.14f : -0.14f), 0.05f, 0.11f, 0.05f, PEWTER);  // the goblet
            }
        }
        // candlesticks along the runner, and a bowl of apples between them
        for (float x : new float[]{-tw / 4, tw / 4}) { m.rgb(x, top + 0.13f, 0, 0.04f, 0.24f, 0.04f, BRASS); m.rgb(x, top + 0.01f, 0, 0.12f, 0.02f, 0.12f, BRASS); candle(m, wax, fl, x, top + 0.25f, 0, 0.14f); }
        m.rgb(0, top + 0.04f, 0, 0.3f, 0.07f, 0.3f, PEWTER);
        for (int k = 0; k < 5; k++) f.rgb((r.nextFloat() - 0.5f) * 0.18f, top + 0.1f + (k == 4 ? 0.05f : 0), (r.nextFloat() - 0.5f) * 0.18f, 0.08f, 0.08f, 0.08f, k % 2 == 0 ? 0xB8282A : 0x8AA830);
        return new Layer[]{wood(wd), cloth(c), metal(m), cloth(wax), glow(fl, 3.0f), cloth(f)};
    }

    /** A writing desk: drawers either side of the knee space, a leather top, an inkwell and quill, papers, a candle. */
    public static Layer[] desk(float w, int seed) {
        Meshes.Boxes wd = new Meshes.Boxes(), c = new Meshes.Boxes(), m = new Meshes.Boxes(), p = new Meshes.Boxes(), wax = new Meshes.Boxes(), fl = new Meshes.Boxes();
        float top = 0.78f, tw = w - 0.12f, d = 0.72f;
        wd.rgb(0, top - 0.03f, 0, tw, 0.06f, d, WALNUT);
        c.rgb(0, top + 0.002f, -0.02f, tw - 0.2f, 0.004f, d - 0.2f, 0x2E4A2E);                               // the leather inset
        for (float side : new float[]{-1, 1}) {
            float x = side * (tw / 2 - 0.22f);
            wd.rgb(x, (top - 0.06f) / 2, 0, 0.42f, top - 0.06f, d - 0.04f, WALNUT);
            for (int k = 0; k < 3; k++) {
                float y = 0.14f + k * 0.21f;
                wd.rgb(x, y, -d / 2 + 0.005f, 0.38f, 0.17f, 0.02f, shade(WALNUT, 1.12f));
                m.rgb(x, y, -d / 2 - 0.01f, 0.08f, 0.02f, 0.015f, BRASS);
            }
        }
        wd.rgb(0, top - 0.1f, -d / 2 + 0.02f, tw - 0.88f, 0.08f, 0.02f, shade(WALNUT, 1.1f)); m.rgb(0, top - 0.1f, -d / 2 + 0.005f, 0.07f, 0.018f, 0.012f, BRASS);
        // on it: an inkwell and a quill, a stack of paper, an open book, a candle
        m.rgb(tw / 2 - 0.3f, top + 0.03f, 0.12f, 0.07f, 0.06f, 0.07f, 0x1E1E2A); p.rgb(tw / 2 - 0.27f, top + 0.12f, 0.12f, 0.008f, 0.18f, 0.008f, 0xF4F0E4);
        Random r = new Random(seed);
        for (int k = 0; k < 4; k++) p.rgb(-0.1f + r.nextFloat() * 0.04f, top + 0.004f + k * 0.004f, -0.05f + r.nextFloat() * 0.04f, 0.21f, 0.004f, 0.29f, k % 2 == 0 ? 0xF0EAD8 : 0xE6DEC8);
        c.rgb(-tw / 2 + 0.35f, top + 0.02f, 0.05f, 0.22f, 0.03f, 0.3f, 0x6A2A1A); p.rgb(-tw / 2 + 0.35f, top + 0.04f, 0.05f, 0.2f, 0.01f, 0.28f, 0xF0EAD8);
        candle(m, wax, fl, tw / 2 - 0.15f, top, -0.12f, 0.12f);
        return new Layer[]{wood(wd), cloth(c), metal(m), cloth(p), cloth(wax), glow(fl, 3.0f)};
    }

    /** A tall double wardrobe: panelled doors, a crown moulding, bracket feet, brass handles. */
    public static Layer[] wardrobe(float w, float h) {
        Meshes.Boxes wd = new Meshes.Boxes(), m = new Meshes.Boxes();
        float tw = w - 0.1f, d = 0.62f, th = Math.min(2.2f, h - 0.1f);
        wd.rgb(0, th / 2 + 0.06f, 0.02f, tw, th - 0.12f, d - 0.04f, WALNUT);
        wd.rgb(0, th - 0.02f, 0, tw + 0.08f, 0.08f, d + 0.04f, WALNUT_DARK);                                 // the crown
        wd.rgb(0, th + 0.03f, 0, tw + 0.02f, 0.04f, d, WALNUT);
        wd.rgb(0, 0.09f, 0, tw + 0.04f, 0.06f, d, WALNUT_DARK);                                               // the plinth
        for (float x : new float[]{-tw / 2 + 0.05f, tw / 2 - 0.05f}) wd.rgb(x, 0.03f, -d / 2 + 0.05f, 0.1f, 0.06f, 0.1f, WALNUT_DARK);
        for (float side : new float[]{-1, 1}) {
            float x = side * tw / 4;
            for (float[] pn : new float[][]{{0.42f, 0.5f}, {1.28f, 0.98f}}) {
                wd.rgb(x, pn[0] + pn[1] / 2 - 0.12f, -d / 2 + 0.005f, tw / 2 - 0.16f, pn[1] - 0.14f, 0.02f, shade(WALNUT, 1.15f));
                wd.rgb(x, pn[0] + pn[1] / 2 - 0.12f, -d / 2 - 0.004f, tw / 2 - 0.24f, pn[1] - 0.22f, 0.01f, shade(WALNUT, 1.04f));
            }
            m.rgb(side * 0.05f, th * 0.5f, -d / 2 - 0.02f, 0.02f, 0.12f, 0.02f, BRASS);
        }
        wd.rgb(0, th / 2, -d / 2 - 0.002f, 0.012f, th - 0.24f, 0.012f, WALNUT_DARK);                         // the doors' meeting
        return new Layer[]{wood(wd), metal(m)};
    }

    /** A chest of drawers: four graduated drawers, brass pulls, a moulded top, and on it a small mirror and a pot. */
    public static Layer[] dresser(float w, int seed) {
        Meshes.Boxes wd = new Meshes.Boxes(), m = new Meshes.Boxes(), gl = new Meshes.Boxes(), cer = new Meshes.Boxes();
        float tw = w - 0.12f, d = 0.5f, top = 0.86f;
        wd.rgb(0, top / 2 + 0.04f, 0.01f, tw, top - 0.08f, d - 0.02f, OAK);
        wd.rgb(0, top - 0.01f, 0, tw + 0.05f, 0.04f, d + 0.04f, shade(OAK, 0.85f));
        for (float x : new float[]{-tw / 2 + 0.04f, tw / 2 - 0.04f}) for (float z : new float[]{-d / 2 + 0.04f, d / 2 - 0.04f}) wd.rgb(x, 0.03f, z, 0.07f, 0.06f, 0.07f, shade(OAK, 0.75f));
        float[] hs = {0.15f, 0.17f, 0.19f, 0.2f};
        float y = 0.1f;
        for (float hh : hs) {
            wd.rgb(0, y + hh / 2, -d / 2 + 0.005f, tw - 0.06f, hh - 0.02f, 0.02f, shade(OAK, 1.12f));
            for (float x : new float[]{-tw / 4, tw / 4}) m.rgb(x, y + hh / 2, -d / 2 - 0.012f, 0.1f, 0.025f, 0.02f, BRASS);
            y += hh;
        }
        // a swing mirror on the top, and a jug
        for (float x : new float[]{-0.2f, 0.2f}) wd.rgb(x, top + 0.22f, 0.08f, 0.04f, 0.42f, 0.04f, shade(OAK, 0.9f));
        wd.rgb(0, top + 0.27f, 0.08f, 0.36f, 0.38f, 0.03f, shade(OAK, 0.9f));
        gl.rgb(0, top + 0.27f, 0.064f, 0.3f, 0.32f, 0.01f, 0xC8D8E0);
        cer.rgb(tw / 2 - 0.18f, top + 0.08f, -0.05f, 0.1f, 0.14f, 0.1f, CERAMIC); cer.rgb(tw / 2 - 0.18f, top + 0.08f, -0.05f, 0.105f, 0.03f, 0.105f, CERAMIC_BLUE);
        return new Layer[]{wood(wd), metal(m), glass(gl), stone(cer)};
    }

    /** A bedside table: one drawer, a shelf under it, a candlestick and a book on top. */
    public static Layer[] nightstand(int seed) {
        Meshes.Boxes wd = new Meshes.Boxes(), m = new Meshes.Boxes(), wax = new Meshes.Boxes(), fl = new Meshes.Boxes(), b = new Meshes.Boxes();
        float s = 0.46f, top = 0.62f;
        for (float x : new float[]{-s / 2 + 0.03f, s / 2 - 0.03f}) for (float z : new float[]{-s / 2 + 0.03f, s / 2 - 0.03f}) wd.rgb(x, (top - 0.03f) / 2, z, 0.04f, top - 0.03f, 0.04f, WALNUT);
        wd.rgb(0, top - 0.02f, 0, s, 0.04f, s, WALNUT_DARK);
        wd.rgb(0, top - 0.12f, 0, s - 0.04f, 0.14f, s - 0.04f, WALNUT);
        wd.rgb(0, top - 0.12f, -s / 2 + 0.015f, s - 0.1f, 0.1f, 0.01f, shade(WALNUT, 1.15f)); m.rgb(0, top - 0.12f, -s / 2 + 0.005f, 0.04f, 0.03f, 0.02f, BRASS);
        wd.rgb(0, 0.14f, 0, s - 0.04f, 0.03f, s - 0.04f, WALNUT);
        m.rgb(0.08f, top + 0.01f, 0.05f, 0.1f, 0.02f, 0.1f, BRASS); m.rgb(0.08f, top + 0.06f, 0.05f, 0.03f, 0.09f, 0.03f, BRASS);
        candle(m, wax, fl, 0.08f, top + 0.1f, 0.05f, 0.1f);
        b.rgb(-0.1f, top + 0.02f, -0.04f, 0.16f, 0.035f, 0.22f, (seed & 1) == 0 ? 0x6A1A1E : 0x1E3A5A);
        return new Layer[]{wood(wd), metal(m), cloth(wax), glow(fl, 3.0f), cloth(b)};
    }

    /** A sideboard: a long low cabinet with cupboard doors, a tureen and silver on it. */
    public static Layer[] sideboard(float w, int seed) {
        Meshes.Boxes wd = new Meshes.Boxes(), m = new Meshes.Boxes(), cer = new Meshes.Boxes();
        float tw = w - 0.1f, d = 0.52f, top = 0.92f;
        wd.rgb(0, top / 2 + 0.06f, 0.01f, tw, top - 0.12f, d - 0.02f, MAHOGANY);
        wd.rgb(0, top - 0.015f, 0, tw + 0.06f, 0.05f, d + 0.04f, MAHOGANY_DARK);
        for (float x : new float[]{-tw / 2 + 0.05f, tw / 2 - 0.05f}) for (float z : new float[]{-d / 2 + 0.05f, d / 2 - 0.05f}) turnedLeg(wd, x, z, 0.12f, 0.06f, MAHOGANY_DARK);
        int doors = Math.max(2, Math.round(tw / 0.5f));
        float dw = (tw - 0.08f) / doors;
        for (int i = 0; i < doors; i++) {
            float x = -tw / 2 + 0.04f + dw * (i + 0.5f);
            wd.rgb(x, 0.46f, -d / 2 + 0.005f, dw - 0.04f, 0.5f, 0.02f, shade(MAHOGANY, 1.18f));
            wd.rgb(x, 0.46f, -d / 2 - 0.004f, dw - 0.12f, 0.4f, 0.01f, shade(MAHOGANY, 1.06f));
            m.rgb(x + (i % 2 == 0 ? dw / 2 - 0.06f : -dw / 2 + 0.06f), 0.5f, -d / 2 - 0.014f, 0.02f, 0.05f, 0.015f, BRASS);
        }
        wd.rgb(0, top - 0.09f, -d / 2 + 0.004f, tw - 0.08f, 0.08f, 0.02f, shade(MAHOGANY, 1.1f));
        // a tureen with its lid, a stack of plates, a silver jug
        cer.rgb(-tw / 4, top + 0.08f, 0, 0.3f, 0.14f, 0.22f, CERAMIC); cer.rgb(-tw / 4, top + 0.16f, 0, 0.26f, 0.03f, 0.19f, CERAMIC); cer.rgb(-tw / 4, top + 0.19f, 0, 0.05f, 0.04f, 0.05f, CERAMIC_BLUE);
        for (int k = 0; k < 5; k++) cer.rgb(tw / 4, top + 0.01f + k * 0.015f, 0, 0.24f, 0.012f, 0.24f, k % 2 == 0 ? CERAMIC : 0xDCD6C6);
        m.rgb(0, top + 0.1f, 0.05f, 0.1f, 0.18f, 0.1f, PEWTER); m.rgb(0.06f, top + 0.14f, 0.05f, 0.03f, 0.08f, 0.02f, PEWTER);
        return new Layer[]{wood(wd), metal(m), stone(cer)};
    }

    /** A grandfather clock: a plinth, a long trunk with a glazed door and its pendulum, a hood over the dial, a brass face. */
    public static Layer[] clock() {
        Meshes.Boxes wd = new Meshes.Boxes(), m = new Meshes.Boxes(), gl = new Meshes.Boxes(), dial = new Meshes.Boxes();
        wd.rgb(0, 0.22f, 0.04f, 0.56f, 0.44f, 0.4f, MAHOGANY);
        wd.rgb(0, 0.46f, 0.04f, 0.5f, 0.06f, 0.36f, MAHOGANY_DARK);
        wd.rgb(0, 1.06f, 0.06f, 0.42f, 1.14f, 0.3f, MAHOGANY);
        wd.rgb(0, 1.0f, -0.095f, 0.28f, 0.82f, 0.02f, MAHOGANY_DARK);
        gl.rgb(0, 1.0f, -0.108f, 0.22f, 0.72f, 0.01f, 0x283A3E);
        m.rgb(0, 1.12f, -0.09f, 0.012f, 0.42f, 0.01f, BRASS); m.rgb(0, 0.88f, -0.093f, 0.1f, 0.1f, 0.012f, BRASS);   // the pendulum
        wd.rgb(0, 1.9f, 0.04f, 0.52f, 0.56f, 0.38f, MAHOGANY);
        wd.rgb(0, 2.2f, 0.04f, 0.58f, 0.06f, 0.42f, MAHOGANY_DARK);
        wd.rgb(0, 2.28f, 0.04f, 0.3f, 0.1f, 0.3f, MAHOGANY_DARK);
        m.rgb(0, 2.36f, 0.04f, 0.06f, 0.06f, 0.06f, GILT);
        dial.rgb(0, 1.9f, -0.16f, 0.36f, 0.36f, 0.02f, 0xEAE2C8);
        m.rgb(0, 1.9f, -0.172f, 0.4f, 0.04f, 0.01f, BRASS); m.rgb(0, 1.9f, -0.172f, 0.04f, 0.4f, 0.01f, BRASS);
        for (int k = 0; k < 12; k++) { double a = k * Math.PI / 6; dial.rgb((float) Math.cos(a) * 0.14f, 1.9f + (float) Math.sin(a) * 0.14f, -0.174f, 0.025f, 0.025f, 0.005f, 0x1E1A16); }
        dial.rgb(0.035f, 1.93f, -0.178f, 0.09f, 0.012f, 0.004f, 0x1E1A16); dial.rgb(0, 1.95f, -0.178f, 0.012f, 0.11f, 0.004f, 0x1E1A16);
        return new Layer[]{wood(wd), metal(m), glass(gl), stone(dial)};
    }

    /** A kitchen dresser: a cupboard base, an open rack of shelves above it, blue-and-white plates and jugs on them. */
    public static Layer[] cupboard(float w, float h, int seed) {
        Meshes.Boxes wd = new Meshes.Boxes(), cer = new Meshes.Boxes(), m = new Meshes.Boxes();
        float tw = w - 0.1f, d = 0.5f, base = 0.86f, th = Math.min(2.1f, h - 0.1f);
        wd.rgb(0, base / 2, 0.01f, tw, base, d, OAK);
        wd.rgb(0, base + 0.02f, -0.02f, tw + 0.04f, 0.04f, d + 0.04f, shade(OAK, 0.85f));
        for (float side : new float[]{-1, 1}) { wd.rgb(side * tw / 4, 0.45f, -d / 2 + 0.005f, tw / 2 - 0.08f, 0.62f, 0.02f, shade(OAK, 1.12f)); m.rgb(side * 0.06f, 0.5f, -d / 2 - 0.01f, 0.02f, 0.06f, 0.02f, IRON); }
        // the rack: back boards, sides, three shelves, a cornice
        wd.rgb(0, (base + th) / 2 + 0.02f, d / 2 - 0.13f, tw, th - base, 0.03f, shade(OAK, 0.9f));
        for (float x : new float[]{-tw / 2 + 0.02f, tw / 2 - 0.02f}) wd.rgb(x, (base + th) / 2 + 0.02f, d / 2 - 0.25f, 0.04f, th - base, 0.26f, OAK);
        Random r = new Random(seed);
        for (int k = 0; k < 3; k++) {
            float sy = base + 0.3f + k * (th - base - 0.4f) / 2.5f;
            wd.rgb(0, sy, d / 2 - 0.25f, tw - 0.04f, 0.03f, 0.25f, OAK);
            // plates stood on edge, leaning on the rail, and a jug or two
            int n = Math.max(2, (int) ((tw - 0.2f) / 0.26f));
            for (int i = 0; i < n; i++) {
                float x = -tw / 2 + 0.15f + i * (tw - 0.3f) / Math.max(1, n - 1);
                if (r.nextFloat() < 0.25f) { cer.rgb(x, sy + 0.09f, d / 2 - 0.24f, 0.11f, 0.15f, 0.11f, CERAMIC); cer.rgb(x, sy + 0.13f, d / 2 - 0.24f, 0.115f, 0.025f, 0.115f, CERAMIC_BLUE); }
                else { cer.rgb(x, sy + 0.12f, d / 2 - 0.16f, 0.21f, 0.21f, 0.015f, CERAMIC); cer.rgb(x, sy + 0.12f, d / 2 - 0.168f, 0.13f, 0.13f, 0.004f, CERAMIC_BLUE); }
            }
            wd.rgb(0, sy + 0.05f, d / 2 - 0.12f, tw - 0.04f, 0.015f, 0.015f, shade(OAK, 0.8f));
        }
        wd.rgb(0, th + 0.03f, d / 2 - 0.2f, tw + 0.06f, 0.06f, 0.34f, shade(OAK, 0.8f));
        return new Layer[]{wood(wd), stone(cer), metal(m)};
    }

    /** Pantry shelves: plain boards on uprights, crowded with crocks, jars, sacks, a cheese and a ham. */
    public static Layer[] shelves(float w, float h, int seed) {
        Meshes.Boxes wd = new Meshes.Boxes(), cer = new Meshes.Boxes(), c = new Meshes.Boxes(), gl = new Meshes.Boxes(), food = new Meshes.Boxes();
        float tw = w - 0.08f, d = 0.42f, th = Math.min(2.0f, h - 0.1f);
        for (float x : new float[]{-tw / 2 + 0.03f, tw / 2 - 0.03f}) wd.rgb(x, th / 2, 0, 0.05f, th, d, shade(OAK_LIGHT, 0.85f));
        Random r = new Random(seed);
        for (int k = 0; k < 4; k++) {
            float sy = 0.1f + k * (th - 0.2f) / 3.3f;
            wd.rgb(0, sy, 0, tw - 0.06f, 0.03f, d, OAK_LIGHT);
            float x = -tw / 2 + 0.12f;
            while (x < tw / 2 - 0.15f) {
                float pick = r.nextFloat(), wdt;
                if (pick < 0.3f) { wdt = 0.16f; cer.rgb(x + 0.08f, sy + 0.1f, -0.02f, 0.15f, 0.17f, 0.15f, r.nextBoolean() ? 0x8A5A3A : 0x6A5A4A); cer.rgb(x + 0.08f, sy + 0.19f, -0.02f, 0.1f, 0.02f, 0.1f, LINEN_SHADE); }
                else if (pick < 0.55f) { wdt = 0.1f; gl.rgb(x + 0.05f, sy + 0.09f, -0.06f, 0.08f, 0.15f, 0.08f, r.nextBoolean() ? GLASS_GREEN : GLASS_AMBER); food.rgb(x + 0.05f, sy + 0.08f, -0.06f, 0.06f, 0.1f, 0.06f, r.nextBoolean() ? 0xC8402A : 0xD8A830); }
                else if (pick < 0.75f && k < 2) { wdt = 0.26f; c.rgb(x + 0.13f, sy + 0.13f, 0, 0.24f, 0.24f, 0.26f, 0xC8B080); c.rgb(x + 0.13f, sy + 0.26f, 0, 0.1f, 0.05f, 0.1f, 0xA88A58); }
                else if (pick < 0.88f) { wdt = 0.2f; food.rgb(x + 0.1f, sy + 0.06f, 0, 0.19f, 0.09f, 0.19f, 0xE0B850); food.rgb(x + 0.1f, sy + 0.1f, 0, 0.17f, 0.012f, 0.17f, 0xC89A40); }
                else { wdt = 0.18f; food.rgb(x + 0.09f, sy + 0.12f, 0.02f, 0.14f, 0.2f, 0.14f, 0x8A3A2A); food.rgb(x + 0.09f, sy + 0.24f, 0.02f, 0.03f, 0.06f, 0.03f, LINEN); }
                x += wdt + 0.04f;
            }
        }
        wd.rgb(0, th - 0.02f, 0, tw, 0.04f, d, shade(OAK_LIGHT, 0.85f));
        return new Layer[]{wood(wd), stone(cer), cloth(c), glass(gl), cloth(food)};
    }

    /** A wine rack: a tall frame of diagonal lattice holding bottles neck-out, a few gaps. */
    public static Layer[] wineRack(float w, float h, int seed) {
        Meshes.Boxes wd = new Meshes.Boxes(), gl = new Meshes.Boxes(), m = new Meshes.Boxes();
        float tw = w - 0.1f, d = 0.38f, th = Math.min(2.0f, h - 0.1f);
        for (float x : new float[]{-tw / 2 + 0.03f, tw / 2 - 0.03f}) wd.rgb(x, th / 2, 0, 0.06f, th, d, OAK);
        wd.rgb(0, th - 0.03f, 0, tw, 0.06f, d, OAK); wd.rgb(0, 0.05f, 0, tw, 0.1f, d, OAK);
        Random r = new Random(seed);
        int cols = Math.max(2, (int) ((tw - 0.12f) / 0.13f)), rows = Math.max(3, (int) ((th - 0.2f) / 0.13f));
        for (int i = 0; i < cols; i++) for (int j = 0; j < rows; j++) {
            float x = -tw / 2 + 0.1f + i * (tw - 0.2f) / Math.max(1, cols - 1), y = 0.17f + j * (th - 0.3f) / Math.max(1, rows - 1);
            wd.rgb(x, y - 0.065f, 0, 0.12f, 0.012f, d - 0.02f, shade(OAK, 0.8f));
            if (r.nextFloat() < 0.18f) continue;
            gl.rgb(x, y, 0.02f, 0.075f, 0.075f, d - 0.06f, r.nextFloat() < 0.6f ? 0x1E3A22 : 0x3A1E1E);
            m.rgb(x, y, -d / 2 + 0.01f, 0.03f, 0.03f, 0.04f, r.nextFloat() < 0.5f ? GILT : 0x6A1A1A);
        }
        return new Layer[]{wood(wd), glass(gl), metal(m)};
    }

    // ------------------------------------------------------------------ beds and the bath
    /** A four-poster bed: carved posts, a canopy with valances and tied-back curtains, a deep quilt, bolster and pillows. */
    public static Layer[] fourPoster(float w, float d, int seed) {
        Meshes.Boxes wd = new Meshes.Boxes(), c = new Meshes.Boxes(), q = new Meshes.Boxes(), m = new Meshes.Boxes();
        int drape = seed % 3 == 0 ? RED_VELVET : seed % 3 == 1 ? GREEN_VELVET : BLUE_VELVET;
        float bw = w - 0.16f, bl = d - 0.16f, mat = 0.62f, tall = 2.25f;
        // the posts: the head at +z, the foot at -z
        for (float x : new float[]{-bw / 2, bw / 2}) for (float z : new float[]{-bl / 2, bl / 2}) {
            wd.rgb(x, 0.06f, z, 0.13f, 0.12f, 0.13f, WALNUT_DARK);
            wd.rgb(x, tall / 2, z, 0.08f, tall, 0.08f, WALNUT);
            wd.rgb(x, 0.55f, z, 0.11f, 0.22f, 0.11f, WALNUT);
            wd.rgb(x, tall + 0.04f, z, 0.1f, 0.08f, 0.1f, WALNUT_DARK);
            m.rgb(x, tall + 0.11f, z, 0.05f, 0.06f, 0.05f, GILT);
        }
        wd.rgb(0, 0.32f, 0, bw, 0.2f, bl, WALNUT_DARK);                                                     // the rails
        wd.rgb(0, 0.9f, bl / 2, bw - 0.06f, 0.82f, 0.06f, WALNUT);                                         // the headboard
        wd.rgb(0, 0.95f, bl / 2 - 0.035f, bw - 0.3f, 0.56f, 0.015f, shade(WALNUT, 1.15f));
        wd.rgb(0, 1.33f, bl / 2, bw * 0.5f, 0.1f, 0.07f, WALNUT_DARK);
        wd.rgb(0, 0.5f, -bl / 2, bw - 0.06f, 0.3f, 0.05f, WALNUT);                                         // the footboard
        // the canopy's frame and its valance
        wd.rgb(0, tall - 0.03f, 0, bw + 0.04f, 0.06f, bl + 0.04f, WALNUT);
        c.rgb(0, tall - 0.14f, -bl / 2 - 0.02f, bw + 0.06f, 0.2f, 0.02f, drape); c.rgb(0, tall - 0.14f, bl / 2 + 0.02f, bw + 0.06f, 0.2f, 0.02f, drape);
        c.rgb(-bw / 2 - 0.02f, tall - 0.14f, 0, 0.02f, 0.2f, bl + 0.06f, drape); c.rgb(bw / 2 + 0.02f, tall - 0.14f, 0, 0.02f, 0.2f, bl + 0.06f, drape);
        c.rgb(0, tall - 0.005f, 0, bw, 0.01f, bl, shade(drape, 0.8f));
        for (float x : new float[]{-bw / 2 - 0.02f, bw / 2 + 0.02f}) for (float z : new float[]{-bl / 2 + 0.05f, bl / 2 - 0.05f}) {
            c.rgb(x, tall - 0.75f, z, 0.03f, 1.1f, 0.18f, drape);                                          // the curtains gathered at each post
            c.rgb(x, tall - 1.3f, z, 0.05f, 0.08f, 0.13f, shade(drape, 0.85f));
            m.rgb(x, tall - 1.3f, z, 0.055f, 0.025f, 0.135f, GILT);                                        // its tie-back
            c.rgb(x, tall - 1.62f, z, 0.03f, 0.56f, 0.22f, drape);
        }
        // the bedding: the mattress, the quilt turned down, a bolster and pillows
        q.rgb(0, mat - 0.1f, -0.02f, bw - 0.08f, 0.2f, bl - 0.12f, LINEN);
        int quilt = seed % 2 == 0 ? 0xC8A060 : 0x7A2E3A;
        q.rgb(0, mat + 0.02f, -0.18f, bw - 0.04f, 0.06f, bl - 0.46f, quilt);
        q.rgb(0, mat - 0.1f, -bl / 2 + 0.05f, bw - 0.02f, 0.26f, 0.02f, quilt);
        for (float x : new float[]{-bw / 2 + 0.01f, bw / 2 - 0.01f}) q.rgb(x, mat - 0.1f, -0.18f, 0.02f, 0.26f, bl - 0.46f, quilt);
        q.rgb(0, mat + 0.055f, 0.06f + (bl - 0.46f) / 2 - 0.4f, bw - 0.04f, 0.02f, 0.2f, LINEN);               // the sheet turned over the quilt
        for (int k = 0; k < 8; k++) q.rgb(-bw / 2 + 0.1f + k * (bw - 0.2f) / 7, mat + 0.06f, -0.18f, 0.02f, 0.004f, bl - 0.5f, shade(quilt, 0.85f));
        q.rgb(0, mat + 0.06f, bl / 2 - 0.22f, bw - 0.14f, 0.14f, 0.18f, LINEN_SHADE);
        for (float x : new float[]{-bw / 4, bw / 4}) q.rgb(x, mat + 0.1f, bl / 2 - 0.36f, bw / 2 - 0.12f, 0.12f, 0.28f, LINEN);
        return new Layer[]{wood(wd), cloth(c), cloth(q), metal(m)};
    }

    /** A copper bathtub on claw feet: rolled rim, a dark inside, a step stool beside it. */
    public static Layer[] bathtub(float w) {
        Meshes.Boxes m = new Meshes.Boxes(), in = new Meshes.Boxes(), wd = new Meshes.Boxes(), water = new Meshes.Boxes();
        float len = w - 0.2f, wid = 0.74f, rim = 0.62f;
        for (float x : new float[]{-len / 2 + 0.16f, len / 2 - 0.16f}) for (float z : new float[]{-wid / 2 + 0.12f, wid / 2 - 0.12f}) { m.rgb(x, 0.06f, z, 0.08f, 0.12f, 0.08f, GILT_DARK); m.rgb(x, 0.015f, z - 0.02f, 0.1f, 0.03f, 0.1f, GILT_DARK); }
        m.rgb(0, 0.32f, 0, len - 0.14f, 0.36f, wid - 0.1f, COPPER);
        m.rgb(0, rim - 0.11f, 0, len, 0.22f, wid, COPPER);
        m.rgb(0, rim + 0.01f, 0, len + 0.04f, 0.04f, wid + 0.04f, shade(COPPER, 1.2f));
        m.rgb(len / 2 - 0.06f, rim + 0.08f, 0, 0.18f, 0.14f, wid - 0.06f, COPPER);                         // the high back
        in.rgb(0, rim - 0.02f, 0, len - 0.12f, 0.03f, wid - 0.12f, 0x5A3020);
        water.rgb(0, rim - 0.08f, 0, len - 0.12f, 0.01f, wid - 0.12f, 0x6A9AB0);
        wd.rgb(-len / 2 - 0.02f, 0.11f, -wid / 2 - 0.12f, 0.36f, 0.04f, 0.26f, OAK);
        for (float x : new float[]{-len / 2 - 0.16f, -len / 2 + 0.12f}) wd.rgb(x, 0.05f, -wid / 2 - 0.12f, 0.04f, 0.1f, 0.22f, OAK);
        return new Layer[]{metal(m), stone(in), glass(water), wood(wd)};
    }

    /** A washstand: a marble-topped stand with a basin and a pitcher, a towel on its rail. */
    public static Layer[] washstand() {
        Meshes.Boxes wd = new Meshes.Boxes(), st = new Meshes.Boxes(), cer = new Meshes.Boxes(), c = new Meshes.Boxes();
        float s = 0.62f, top = 0.78f;
        for (float x : new float[]{-s / 2 + 0.04f, s / 2 - 0.04f}) for (float z : new float[]{-0.18f, 0.18f}) wd.rgb(x, top / 2, z, 0.05f, top, 0.05f, OAK);
        wd.rgb(0, 0.2f, 0, s - 0.06f, 0.03f, 0.38f, OAK);
        st.rgb(0, top + 0.02f, 0, s, 0.04f, 0.44f, MARBLE);
        wd.rgb(0, top + 0.18f, 0.2f, s, 0.3f, 0.03f, OAK);                                                 // the splashback
        cer.rgb(0, top + 0.07f, -0.02f, 0.32f, 0.06f, 0.3f, CERAMIC); cer.rgb(0, top + 0.095f, -0.02f, 0.26f, 0.01f, 0.24f, 0x6A9AB0);
        cer.rgb(0.2f, top + 0.15f, 0.08f, 0.11f, 0.22f, 0.11f, CERAMIC); cer.rgb(0.2f, top + 0.2f, 0.08f, 0.115f, 0.03f, 0.115f, CERAMIC_BLUE);
        wd.rgb(-s / 2 - 0.02f, top - 0.08f, 0, 0.02f, 0.02f, 0.36f, OAK);
        c.rgb(-s / 2 - 0.03f, top - 0.24f, 0, 0.02f, 0.32f, 0.26f, LINEN);
        return new Layer[]{wood(wd), stone(st), stone(cer), cloth(c)};
    }

    // ------------------------------------------------------------------ the hearth and the lights
    /**
     * A fireplace: a stone surround with a deep moulded mantel shelf, a black firebox with andirons and logs, a hearthstone
     * before it, and on the mantel a clock and two candlesticks. The fire itself is the renderer's (a flame and its light)
     * while it burns.
     */
    public static Layer[] fireplace(float w, int seed) {
        Meshes.Boxes st = new Meshes.Boxes(), dark = new Meshes.Boxes(), m = new Meshes.Boxes(), wd = new Meshes.Boxes(), wax = new Meshes.Boxes(), fl = new Meshes.Boxes();
        float fw = Math.max(1.4f, w - 0.1f), depth = 0.7f, mantel = 1.25f, back = 0.5f;
        st.rgb(0, mantel / 2, back - depth / 2 + 0.05f, fw, mantel, depth - 0.1f, MARBLE_SHADE);
        dark.rgb(0, 0.42f, back - depth / 2 - 0.0f, fw - 0.62f, 0.8f, depth - 0.12f, SOOT);
        st.rgb(0, mantel + 0.04f, back - depth / 2, fw + 0.16f, 0.08f, depth + 0.06f, MARBLE);           // the mantel shelf
        st.rgb(0, mantel - 0.04f, back - depth / 2 - 0.02f, fw + 0.06f, 0.08f, depth, MARBLE_SHADE);
        for (float x : new float[]{-fw / 2 + 0.16f, fw / 2 - 0.16f}) { st.rgb(x, mantel / 2 - 0.04f, -depth / 2 + back - 0.05f, 0.22f, mantel - 0.1f, 0.12f, MARBLE); st.rgb(x, 0.06f, -depth / 2 + back - 0.06f, 0.28f, 0.12f, 0.16f, MARBLE_SHADE); }
        st.rgb(0, 0.86f, -depth / 2 + back - 0.05f, fw - 0.5f, 0.1f, 0.12f, MARBLE);
        st.rgb(0, 0.01f, -depth / 2 + back - 0.35f, fw + 0.2f, 0.02f, 0.5f, STONE_DARK);                  // the hearthstone
        // the grate: andirons and three logs
        for (float x : new float[]{-0.25f, 0.25f}) { m.rgb(x, 0.12f, back - depth / 2 - 0.05f, 0.04f, 0.24f, 0.04f, IRON); m.rgb(x, 0.04f, back - depth / 2, 0.04f, 0.04f, 0.32f, IRON); }
        wd.rgb(0, 0.1f, back - depth / 2 + 0.02f, 0.7f, 0.1f, 0.1f, 0x5A3A22); wd.rgb(0.05f, 0.18f, back - depth / 2 + 0.07f, 0.62f, 0.09f, 0.09f, 0x4A2E1A);
        wd.rgb(-0.05f, 0.1f, back - depth / 2 + 0.13f, 0.6f, 0.09f, 0.09f, 0x6A4628);
        // on the mantel: a small clock between two candlesticks
        wd.rgb(0, mantel + 0.18f, back - depth / 2 + 0.1f, 0.2f, 0.26f, 0.12f, MAHOGANY); m.rgb(0, mantel + 0.2f, back - depth / 2 + 0.035f, 0.12f, 0.12f, 0.01f, 0xEAE2C8);
        for (float x : new float[]{-fw / 2 + 0.25f, fw / 2 - 0.25f}) { m.rgb(x, mantel + 0.16f, back - depth / 2 + 0.1f, 0.04f, 0.24f, 0.04f, BRASS); candle(m, wax, fl, x, mantel + 0.28f, back - depth / 2 + 0.1f, 0.12f); }
        return new Layer[]{stone(st), new Layer(dark, 1, 0, 0), metal(m), wood(wd), cloth(wax), glow(fl, 3.0f)};
    }

    /**
     * A chandelier: a chain from the ceiling to an iron hoop, a smaller hoop above it, candles all round both, a finial
     * under. The top is at the ceiling (y = ceiling); the candles' flames are its glow (the renderer adds their light).
     */
    public static Layer[] chandelier(float ceiling) {
        Meshes.Boxes m = new Meshes.Boxes(), wax = new Meshes.Boxes(), fl = new Meshes.Boxes(), gl = new Meshes.Boxes();
        float hoop = ceiling - 1.15f;
        m.rgb(0, ceiling - 0.03f, 0, 0.18f, 0.06f, 0.18f, IRON);
        for (int k = 0; k < 9; k++) m.rgb(0, ceiling - 0.1f - k * 0.08f, 0, k % 2 == 0 ? 0.03f : 0.05f, 0.06f, k % 2 == 0 ? 0.05f : 0.03f, IRON_LIGHT);   // the chain
        m.rgb(0, hoop + 0.2f, 0, 0.08f, 0.5f, 0.08f, GILT_DARK);
        m.rgb(0, hoop - 0.1f, 0, 0.12f, 0.16f, 0.12f, GILT);
        m.rgb(0, hoop - 0.22f, 0, 0.06f, 0.1f, 0.06f, GILT_DARK);
        for (int ring = 0; ring < 2; ring++) {
            float r = ring == 0 ? 0.56f : 0.3f, y = ring == 0 ? hoop : hoop + 0.32f;
            int n = ring == 0 ? 8 : 5;
            // the hoop: short segments round the circle
            for (int s = 0; s < 16; s++) { double a = s * Math.PI / 8; m.rgb((float) Math.cos(a) * r, y, (float) Math.sin(a) * r, 0.24f * r, 0.04f, 0.04f, IRON); }
            for (int k = 0; k < n; k++) {
                double a = k * 2 * Math.PI / n + ring * 0.3;
                float x = (float) Math.cos(a) * r, z = (float) Math.sin(a) * r;
                m.rgb(x * 0.5f, y + 0.01f, z * 0.5f, Math.abs(x) + 0.02f, 0.02f, Math.abs(z) + 0.02f, IRON_LIGHT);   // the arm
                candle(m, wax, fl, x, y + 0.02f, z, 0.16f);
                if (ring == 0) gl.rgb(x, y - 0.07f, z, 0.03f, 0.08f, 0.03f, 0xDDE8F0);                            // a crystal drop
            }
        }
        return new Layer[]{metal(m), cloth(wax), glow(fl, 3.4f), glass(gl)};
    }

    /** A standing candelabrum: a weighted foot, a turned column, five branches of candles. */
    public static Layer[] candelabra() {
        Meshes.Boxes m = new Meshes.Boxes(), wax = new Meshes.Boxes(), fl = new Meshes.Boxes();
        m.rgb(0, 0.03f, 0, 0.36f, 0.06f, 0.36f, BRASS); m.rgb(0, 0.09f, 0, 0.22f, 0.06f, 0.22f, GILT_DARK);
        m.rgb(0, 0.75f, 0, 0.05f, 1.3f, 0.05f, BRASS);
        for (float y : new float[]{0.3f, 0.7f, 1.1f}) m.rgb(0, y, 0, 0.09f, 0.06f, 0.09f, GILT);
        m.rgb(0, 1.4f, 0, 0.5f, 0.03f, 0.04f, BRASS); m.rgb(0, 1.4f, 0, 0.04f, 0.03f, 0.5f, BRASS);
        for (float[] p : new float[][]{{0, 0}, {0.24f, 0}, {-0.24f, 0}, {0, 0.24f}, {0, -0.24f}}) {
            m.rgb(p[0], 1.44f + (p[0] == 0 && p[1] == 0 ? 0.08f : 0), p[1], 0.04f, 0.08f + (p[0] == 0 && p[1] == 0 ? 0.16f : 0), 0.04f, BRASS);
            candle(m, wax, fl, p[0], 1.48f + (p[0] == 0 && p[1] == 0 ? 0.16f : 0), p[1], 0.18f);
        }
        return new Layer[]{metal(m), cloth(wax), glow(fl, 3.2f)};
    }

    // ------------------------------------------------------------------ on the walls and the floor
    /** A rug: a patterned field (a medallion and corner pieces) inside a border, its fringe at the ends. Seeded colours. */
    public static Layer[] rug(float w, float d, int seed) {
        Meshes.Boxes c = new Meshes.Boxes();
        int[][] pal = {{0x8A1E22, 0x2A2A5A, 0xD8B060, 0xE8DCC0}, {0x2A3A6A, 0x8A2A1E, 0xD8B060, 0xE8DCC0}, {0x2E5A3A, 0x6A2A1A, 0xC8A050, 0xE0D4B8}};
        int[] p = pal[Math.floorMod(seed, pal.length)];
        float rw = w - 0.3f, rd = d - 0.3f, y = 0.008f;
        c.rgb(0, y, 0, rw, 0.012f, rd, p[1]);
        c.rgb(0, y + 0.002f, 0, rw - 0.24f, 0.012f, rd - 0.24f, p[0]);
        c.rgb(0, y + 0.004f, 0, rw - 0.34f, 0.012f, rd - 0.34f, shade(p[0], 0.88f));
        // the border's running pattern: little gold lozenges
        int nx = Math.max(2, (int) (rw / 0.24f)), nz = Math.max(2, (int) (rd / 0.24f));
        for (int i = 0; i < nx; i++) { float x = -rw / 2 + 0.12f + i * (rw - 0.24f) / (nx - 1); c.rgb(x, y + 0.004f, -rd / 2 + 0.06f, 0.06f, 0.012f, 0.04f, p[2]); c.rgb(x, y + 0.004f, rd / 2 - 0.06f, 0.06f, 0.012f, 0.04f, p[2]); }
        for (int i = 0; i < nz; i++) { float z = -rd / 2 + 0.12f + i * (rd - 0.24f) / (nz - 1); c.rgb(-rw / 2 + 0.06f, y + 0.004f, z, 0.04f, 0.012f, 0.06f, p[2]); c.rgb(rw / 2 - 0.06f, y + 0.004f, z, 0.04f, 0.012f, 0.06f, p[2]); }
        // the medallion: a stepped diamond in the middle
        float s = Math.min(rw, rd) * 0.36f;
        for (int k = 0; k < 4; k++) { float ss = s * (1 - k * 0.22f); c.rgb(0, y + 0.006f + k * 0.001f, 0, ss, 0.012f, ss * 0.6f, k % 2 == 0 ? p[1] : p[2]); c.rgb(0, y + 0.006f + k * 0.001f, 0, ss * 0.6f, 0.012f, ss, k % 2 == 0 ? p[1] : p[2]); }
        // corner pieces
        for (float sx : new float[]{-1, 1}) for (float sz : new float[]{-1, 1}) c.rgb(sx * (rw / 2 - 0.32f), y + 0.006f, sz * (rd / 2 - 0.32f), 0.2f, 0.012f, 0.2f, p[1]);
        // the fringe at the ends
        for (int i = 0; i < nx * 2; i++) { float x = -rw / 2 + 0.03f + i * (rw - 0.06f) / (nx * 2 - 1); c.rgb(x, y - 0.002f, -rd / 2 - 0.05f, 0.012f, 0.006f, 0.1f, p[3]); c.rgb(x, y - 0.002f, rd / 2 + 0.05f, 0.012f, 0.006f, 0.1f, p[3]); }
        return new Layer[]{cloth(c)};
    }

    /** A painting in a gilt frame on the wall (back at +z): a landscape, a portrait or a still life, chosen by the seed. */
    public static Layer[] painting(float w, float h, int seed) {
        Meshes.Boxes m = new Meshes.Boxes(), cv = new Meshes.Boxes();
        float pw = Math.min(w - 0.2f, 1.4f), ph = Math.min(h * 0.5f, pw * (seed % 2 == 0 ? 0.72f : 1.25f)), y = 1.65f, z = 0.5f - 0.04f;
        m.rgb(0, y, z, pw + 0.14f, ph + 0.14f, 0.06f, GILT);
        m.rgb(0, y, z - 0.02f, pw + 0.06f, ph + 0.06f, 0.03f, GILT_DARK);
        Random r = new Random(seed * 31L + 7);
        int kind = Math.floorMod(seed, 3);
        float zc = z - 0.036f;
        if (kind == 0) {
            // a landscape: sky over hills over a meadow, a tree and a river
            cv.rgb(0, y + ph * 0.25f, zc, pw, ph * 0.5f, 0.005f, 0x8AB0D0);
            cv.rgb(0, y + ph * 0.12f, zc - 0.001f, pw, ph * 0.12f, 0.005f, 0xE8D8B0);
            cv.rgb(-pw * 0.2f, y + 0.02f, zc - 0.002f, pw * 0.7f, ph * 0.22f, 0.005f, 0x5A7A4A);
            cv.rgb(pw * 0.25f, y + 0.0f, zc - 0.002f, pw * 0.6f, ph * 0.18f, 0.005f, 0x4A6A3E);
            cv.rgb(0, y - ph * 0.3f, zc - 0.003f, pw, ph * 0.4f, 0.005f, 0x6A8A3A);
            cv.rgb(pw * 0.1f, y - ph * 0.28f, zc - 0.004f, pw * 0.18f, ph * 0.36f, 0.005f, 0x5A8AA8);
            cv.rgb(-pw * 0.3f, y - ph * 0.05f, zc - 0.004f, pw * 0.04f, ph * 0.3f, 0.005f, 0x4A3220);
            cv.rgb(-pw * 0.3f, y + ph * 0.12f, zc - 0.005f, pw * 0.2f, ph * 0.2f, 0.005f, 0x2E5A2A);
        } else if (kind == 1) {
            // a portrait: a dark ground, the sitter's shoulders in their coat, the face, the hair
            int coat = r.nextBoolean() ? 0x2A2A4A : 0x5A1A1E;
            cv.rgb(0, y, zc, pw, ph, 0.005f, 0x2A2420);
            cv.rgb(0, y - ph * 0.3f, zc - 0.001f, pw * 0.7f, ph * 0.4f, 0.005f, coat);
            cv.rgb(0, y - ph * 0.12f, zc - 0.002f, pw * 0.14f, ph * 0.1f, 0.005f, LINEN);
            cv.rgb(0, y + ph * 0.1f, zc - 0.002f, pw * 0.26f, ph * 0.3f, 0.005f, 0xD8A888);
            cv.rgb(0, y + ph * 0.27f, zc - 0.003f, pw * 0.3f, ph * 0.1f, 0.005f, r.nextBoolean() ? 0x4A3020 : 0xA88850);
            cv.rgb(-pw * 0.05f, y + ph * 0.12f, zc - 0.004f, pw * 0.03f, ph * 0.02f, 0.005f, 0x2A1E1A); cv.rgb(pw * 0.05f, y + ph * 0.12f, zc - 0.004f, pw * 0.03f, ph * 0.02f, 0.005f, 0x2A1E1A);
        } else {
            // a still life: a dark table, a jug, fruit and a candle
            cv.rgb(0, y, zc, pw, ph, 0.005f, 0x3A3024);
            cv.rgb(0, y - ph * 0.3f, zc - 0.001f, pw, ph * 0.4f, 0.005f, 0x5A3A22);
            cv.rgb(-pw * 0.15f, y - ph * 0.02f, zc - 0.002f, pw * 0.18f, ph * 0.3f, 0.005f, 0xC8B890);
            for (int k = 0; k < 4; k++) cv.rgb(pw * (0.05f + k * 0.08f), y - ph * 0.1f + (k % 2) * 0.03f, zc - 0.003f, pw * 0.07f, pw * 0.07f, 0.005f, k % 2 == 0 ? 0xB83028 : 0xC8A028);
        }
        return new Layer[]{metal(m), cloth(cv)};
    }

    /** A tapestry on the wall: a long woven hanging on a rod, a border, a hunting scene of a stag and trees, tasseled. */
    public static Layer[] tapestry(float w, float h, int seed) {
        Meshes.Boxes c = new Meshes.Boxes(), m = new Meshes.Boxes();
        float tw = Math.min(w - 0.2f, 1.8f), th = Math.min(h * 0.62f, 2.2f), top = Math.min(h - 0.35f, 2.9f), z = 0.5f - 0.03f, y = top - th / 2;
        m.rgb(0, top + 0.04f, z, tw + 0.24f, 0.04f, 0.04f, GILT_DARK);
        for (float x : new float[]{-tw / 2 - 0.12f, tw / 2 + 0.12f}) m.rgb(x, top + 0.04f, z, 0.06f, 0.08f, 0.06f, GILT);
        c.rgb(0, y, z, tw, th, 0.02f, 0x6A2A22);
        c.rgb(0, y, z - 0.011f, tw - 0.16f, th - 0.16f, 0.004f, 0x3E5A3A);
        c.rgb(0, y - th * 0.3f, z - 0.013f, tw - 0.16f, th * 0.24f, 0.004f, 0x5A6A30);
        // the trees, and the stag between them
        for (float x : new float[]{-tw * 0.32f, tw * 0.3f}) { c.rgb(x, y - th * 0.05f, z - 0.015f, 0.05f, th * 0.4f, 0.004f, 0x4A3020); c.rgb(x, y + th * 0.18f, z - 0.016f, tw * 0.2f, th * 0.22f, 0.004f, 0x2A4A26); }
        c.rgb(0, y - th * 0.08f, z - 0.016f, tw * 0.22f, th * 0.1f, 0.004f, 0x9A6A3A);
        c.rgb(tw * 0.1f, y + 0.0f, z - 0.017f, tw * 0.07f, th * 0.1f, 0.004f, 0x9A6A3A);
        for (float x : new float[]{-0.08f, -0.02f, 0.04f, 0.09f}) c.rgb(x * tw, y - th * 0.17f, z - 0.017f, 0.025f, th * 0.1f, 0.004f, 0x7A5030);
        c.rgb(tw * 0.13f, y + th * 0.08f, z - 0.018f, tw * 0.1f, 0.02f, 0.004f, 0xE8D8B0);
        // the border's gold thread, the tassels
        c.rgb(0, y + th / 2 - 0.05f, z - 0.012f, tw - 0.06f, 0.02f, 0.004f, 0xD8B060); c.rgb(0, y - th / 2 + 0.05f, z - 0.012f, tw - 0.06f, 0.02f, 0.004f, 0xD8B060);
        for (int k = 0; k < 7; k++) c.rgb(-tw / 2 + 0.1f + k * (tw - 0.2f) / 6, y - th / 2 - 0.06f, z, 0.03f, 0.12f, 0.02f, 0xD8B060);
        return new Layer[]{cloth(c), metal(m)};
    }

    /** A tall mirror on the wall: a gilt frame with a crest, the glass. */
    public static Layer[] mirror() {
        Meshes.Boxes m = new Meshes.Boxes(), gl = new Meshes.Boxes();
        float z = 0.5f - 0.035f, y = 1.55f;
        m.rgb(0, y, z, 0.72f, 1.22f, 0.05f, GILT);
        m.rgb(0, y + 0.66f, z, 0.32f, 0.12f, 0.05f, GILT); m.rgb(0, y + 0.74f, z, 0.12f, 0.06f, 0.05f, GILT_DARK);
        gl.rgb(0, y, z - 0.026f, 0.6f, 1.1f, 0.005f, 0xB8C8D0);
        return new Layer[]{metal(m), new Layer(gl, 0.02f, 1f, 0)};
    }

    /** A weapon trophy on the wall: a round shield with two swords crossed behind it. */
    public static Layer[] weaponRack(int seed) {
        Meshes.Boxes m = new Meshes.Boxes(), wd = new Meshes.Boxes(), paint = new Meshes.Boxes();
        float z = 0.5f - 0.04f, y = 1.8f;
        for (float s : new float[]{-1, 1}) for (int k = 0; k < 9; k++) {
            float t = k / 8f;
            m.rgb(s * (0.45f - t * 0.9f), y - 0.4f + t * 0.8f, z, 0.07f, 0.11f, 0.015f, k < 2 ? IRON : 0xC8CCD2);
            if (k == 1) wd.rgb(s * (0.45f - t * 0.9f) + s * 0.07f, y - 0.4f + t * 0.8f - 0.08f, z, 0.14f, 0.04f, 0.03f, GILT_DARK);
        }
        int field = seed % 2 == 0 ? 0x8A1E22 : 0x2A3A7A;
        wd.rgb(0, y, z - 0.03f, 0.56f, 0.56f, 0.04f, OAK);
        paint.rgb(0, y, z - 0.052f, 0.5f, 0.5f, 0.004f, field);
        paint.rgb(0, y, z - 0.054f, 0.5f, 0.08f, 0.004f, 0xD8B060); paint.rgb(0, y, z - 0.054f, 0.08f, 0.5f, 0.004f, 0xD8B060);
        m.rgb(0, y, z - 0.06f, 0.12f, 0.12f, 0.03f, IRON_LIGHT);
        for (float[] p : new float[][]{{-0.22f, 0}, {0.22f, 0}, {0, 0.22f}, {0, -0.22f}}) m.rgb(p[0], y + p[1], z - 0.055f, 0.04f, 0.04f, 0.01f, IRON);
        return new Layer[]{metal(m), wood(wd), cloth(paint)};
    }

    /** A rack of pots and pans hung on the kitchen wall: an iron rail, hooks, copper pans and ladles. */
    public static Layer[] potRack(float w) {
        Meshes.Boxes m = new Meshes.Boxes(), cu = new Meshes.Boxes();
        float rw = Math.min(w - 0.2f, 1.6f), z = 0.5f - 0.08f, y = 1.85f;
        m.rgb(0, y, z, rw, 0.03f, 0.03f, IRON);
        for (float x : new float[]{-rw / 2, rw / 2}) m.rgb(x, y, z + 0.04f, 0.03f, 0.03f, 0.08f, IRON);
        int n = Math.max(3, (int) (rw / 0.3f));
        for (int i = 0; i < n; i++) {
            float x = -rw / 2 + 0.15f + i * (rw - 0.3f) / Math.max(1, n - 1);
            m.rgb(x, y - 0.05f, z, 0.01f, 0.08f, 0.01f, IRON);
            if (i % 3 == 0) { cu.rgb(x, y - 0.26f, z, 0.26f, 0.03f, 0.03f, COPPER); cu.rgb(x, y - 0.42f, z - 0.01f, 0.24f, 0.24f, 0.02f, COPPER); cu.rgb(x, y - 0.42f, z - 0.02f, 0.2f, 0.2f, 0.01f, shade(COPPER, 0.8f)); }
            else if (i % 3 == 1) { cu.rgb(x, y - 0.3f, z - 0.05f, 0.2f, 0.16f, 0.16f, COPPER); cu.rgb(x, y - 0.18f, z - 0.05f, 0.02f, 0.1f, 0.02f, IRON); }
            else { m.rgb(x, y - 0.25f, z, 0.02f, 0.34f, 0.02f, PEWTER); m.rgb(x, y - 0.44f, z - 0.02f, 0.07f, 0.04f, 0.06f, PEWTER); }
        }
        return new Layer[]{metal(m), metal(cu)};
    }

    /** A suit of armour on its stand: plate from helm to sabatons, a poleaxe at its side. */
    public static Layer[] armorStand(int seed) {
        Meshes.Boxes m = new Meshes.Boxes(), wd = new Meshes.Boxes(), c = new Meshes.Boxes();
        int plate = 0xB4BAC2, dark = 0x7A808A;
        wd.rgb(0, 0.04f, 0, 0.5f, 0.08f, 0.5f, WALNUT_DARK);
        for (float s : new float[]{-1, 1}) {
            m.rgb(s * 0.11f, 0.13f, -0.03f, 0.12f, 0.08f, 0.24f, plate);                                     // sabatons
            m.rgb(s * 0.11f, 0.42f, 0, 0.12f, 0.46f, 0.13f, plate);                                          // greaves
            m.rgb(s * 0.11f, 0.68f, -0.01f, 0.13f, 0.08f, 0.14f, dark);                                       // knee cops
            m.rgb(s * 0.11f, 0.86f, 0, 0.14f, 0.32f, 0.15f, plate);                                          // cuisses
            m.rgb(s * 0.3f, 1.3f, 0, 0.1f, 0.3f, 0.11f, plate);                                               // the arms
            m.rgb(s * 0.3f, 1.05f, -0.02f, 0.11f, 0.24f, 0.12f, plate);
            m.rgb(s * 0.3f, 0.9f, -0.03f, 0.12f, 0.1f, 0.12f, dark);                                          // gauntlets
            m.rgb(s * 0.27f, 1.5f, 0, 0.2f, 0.12f, 0.22f, plate);                                             // pauldrons
        }
        m.rgb(0, 1.06f, 0, 0.36f, 0.18f, 0.2f, dark);                                                       // the fauld
        m.rgb(0, 1.33f, 0, 0.4f, 0.42f, 0.24f, plate);                                                      // the breastplate
        m.rgb(0, 1.33f, -0.125f, 0.04f, 0.38f, 0.01f, 0xD8DCE2);
        m.rgb(0, 1.73f, 0, 0.24f, 0.28f, 0.26f, plate);                                                     // the helm
        m.rgb(0, 1.73f, -0.135f, 0.2f, 0.03f, 0.01f, 0x1E1E22);                                             // its visor slit
        m.rgb(0, 1.9f, 0, 0.05f, 0.08f, 0.2f, dark);
        c.rgb(0, 1.95f, 0.04f, 0.06f, 0.12f, 0.16f, seed % 2 == 0 ? RED_VELVET : BLUE_VELVET);           // the plume
        wd.rgb(0.42f, 1.1f, 0.04f, 0.04f, 2.1f, 0.04f, OAK);                                              // the poleaxe
        m.rgb(0.42f, 2.05f, 0.04f, 0.04f, 0.2f, 0.04f, plate); m.rgb(0.48f, 1.95f, 0.04f, 0.16f, 0.16f, 0.02f, plate); m.rgb(0.36f, 1.95f, 0.04f, 0.08f, 0.06f, 0.02f, dark);
        return new Layer[]{metal(m), wood(wd), cloth(c)};
    }

    /** A potted plant: a glazed pot on a little stand, a fern spilling over its rim. */
    public static Layer[] planter(int seed) {
        Meshes.Boxes cer = new Meshes.Boxes(), lf = new Meshes.Boxes(), wd = new Meshes.Boxes();
        Random r = new Random(seed);
        wd.rgb(0, 0.06f, 0, 0.42f, 0.04f, 0.42f, WALNUT); for (float[] p : new float[][]{{-0.17f, -0.17f}, {0.17f, -0.17f}, {-0.17f, 0.17f}, {0.17f, 0.17f}}) wd.rgb(p[0], 0.02f, p[1], 0.05f, 0.04f, 0.05f, WALNUT_DARK);
        int glaze = r.nextBoolean() ? CERAMIC_BLUE : 0x3A6A5A;
        cer.rgb(0, 0.26f, 0, 0.34f, 0.36f, 0.34f, glaze); cer.rgb(0, 0.45f, 0, 0.38f, 0.04f, 0.38f, shade(glaze, 1.15f)); cer.rgb(0, 0.1f, 0, 0.26f, 0.04f, 0.26f, shade(glaze, 0.8f));
        lf.rgb(0, 0.47f, 0, 0.3f, 0.02f, 0.3f, 0x3A2A1E);
        for (int k = 0; k < 9; k++) {
            double a = k * 2 * Math.PI / 9 + r.nextFloat() * 0.3;
            float len = 0.32f + r.nextFloat() * 0.18f;
            for (int s = 0; s < 4; s++) {
                float t = (s + 0.5f) / 4f;
                float x = (float) Math.cos(a) * len * t, z = (float) Math.sin(a) * len * t, y = 0.5f + (float) Math.sin(t * Math.PI * 0.8) * 0.28f - t * t * 0.14f;
                lf.rgb(x, y, z, 0.07f - s * 0.012f, 0.025f, 0.07f - s * 0.012f, s % 2 == 0 ? LEAF : LEAF_DARK);
            }
        }
        return new Layer[]{stone(cer), cloth(lf), wood(wd)};
    }

    /** A marble bust on a fluted pedestal: shoulders, the head, a laurel. */
    public static Layer[] bust(int seed) {
        Meshes.Boxes st = new Meshes.Boxes(), m = new Meshes.Boxes();
        st.rgb(0, 0.05f, 0, 0.46f, 0.1f, 0.46f, MARBLE_SHADE);
        st.rgb(0, 0.55f, 0, 0.3f, 0.9f, 0.3f, MARBLE);
        for (int k = 0; k < 4; k++) { st.rgb(-0.09f + k * 0.06f, 0.55f, -0.152f, 0.02f, 0.8f, 0.006f, MARBLE_SHADE); }
        st.rgb(0, 1.03f, 0, 0.4f, 0.06f, 0.4f, MARBLE_SHADE);
        st.rgb(0, 1.18f, 0.02f, 0.42f, 0.2f, 0.22f, MARBLE);                                               // the shoulders
        st.rgb(0, 1.33f, 0.02f, 0.11f, 0.12f, 0.11f, MARBLE);                                              // the neck
        st.rgb(0, 1.47f, 0.0f, 0.2f, 0.24f, 0.22f, MARBLE);                                                // the head
        st.rgb(0, 1.46f, -0.12f, 0.04f, 0.06f, 0.03f, MARBLE_SHADE);                                       // the nose
        st.rgb(0, 1.57f, 0.02f, 0.22f, 0.06f, 0.24f, MARBLE_SHADE);
        if (seed % 2 == 0) for (int k = 0; k < 6; k++) m.rgb(-0.1f + k * 0.04f, 1.6f - Math.abs(k - 2.5f) * 0.01f, 0.02f - (k < 3 ? 0.08f : -0.08f) * 0, 0.04f, 0.03f, 0.25f, GILT);
        return new Layer[]{stone(st), metal(m)};
    }
}
