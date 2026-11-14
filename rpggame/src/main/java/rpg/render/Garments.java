package rpg.render;

import astratto.voxel.VoxModel;
import java.util.Map;
import rpg.item.ItemDef;

/**
 * (0.4.5, gamefixes: the clothing update) The outfits' pieces as they are seen: their look read from the data
 * (ItemDef.wear - a shape and its colours), and each piece built as the thing itself for the pack, the ground and a
 * shop's shelf (1/32 m designs, doubled like the rest of the gear). On the body the look is painted by Skin (cloth,
 * leather, checks, patches, dust, trims) and what stands off the body - a hat's brim and crown, a fez and its tassel,
 * a helmet's dome and lamp, a hood's cowl, a robe's skirts, a coat's long hem, a cape - is built by Gfx from the same
 * look, so a Lumberjack's checked shirt and cap, a Prospector's helmet and dusty jacket and a Royal's coat and fez can
 * each be told at a glance.
 */
public final class Garments {
    private Garments() {}

    public static boolean is(ItemDef d) { return d != null && d.wear != null; }
    public static String shape(ItemDef d) { return d == null || d.wear == null ? "" : String.valueOf(d.wear.getOrDefault("shape", "")); }
    public static String str(ItemDef d, String k, String fallback) { Object o = d == null || d.wear == null ? null : d.wear.get(k); return o == null ? fallback : String.valueOf(o); }
    public static int color(ItemDef d, String k, int fallback) { return d == null || d.wear == null ? fallback : rpg.item.Apparel.hex(d.wear.get(k), fallback); }
    public static boolean flag(ItemDef d, String k) { return d != null && d.wear != null && Boolean.TRUE.equals(d.wear.get(k)); }
    public static double num(ItemDef d, String k, double fallback) { return d != null && d.wear != null && d.wear.get(k) instanceof Number n ? n.doubleValue() : fallback; }
    /** Whether a head piece sits on the hair (a hat) rather than over it (a hood, a mask, a helmet). */
    public static boolean onHair(ItemDef d) { String s = shape(d); return s.equals("fez") || s.equals("straw_hat") || s.equals("cap"); }

    // ------------------------------------------------------------------ the pieces as things (coarse, 1/32 m)
    private static int m(int rgb) { return ItemModels.mat(rgb); }
    private static int sh(int c, float f) { return ItemModels.sh(c, f); }
    private static int metal(int rgb) { return ItemModels.METAL << 24 | (rgb & 0xFFFFFF); }

    /** The piece's own model (coarse; ItemModels doubles it). */
    static ItemModels.Model model(ItemDef d) {
        int c = m(d.color), trim = m(color(d, "trim", Skin.shade(d.color, 0.7f) & 0xFFFFFF));
        return switch (shape(d)) {
            case "hood", "mask" -> hood(d, c, trim, shape(d).equals("mask"));
            case "mining_helmet" -> helmet(d, c, trim);
            case "fez" -> fez(d, c);
            case "straw_hat" -> strawHat(d, c);
            case "cap" -> cap(c, trim);
            case "tunic", "jacket", "shirt" -> top(d, c, trim);
            case "overcoat" -> coat(d, c, trim);
            case "robe" -> robe(d, c, trim);
            case "trousers" -> trousers(d, c, trim);
            case "gloves" -> gloves(c, trim);
            case "boots" -> boots(d, c, trim);
            case "cloak" -> cloak(d, c, trim);
            case "insignia" -> insignia(d, c);
            default -> top(d, c, trim);
        };
    }

    private static ItemModels.Model hood(ItemDef d, int c, int trim, boolean mask) {
        VoxModel v = new VoxModel(12, 13, 12);
        int lining = m(color(d, "lining", Skin.shade(d.color, 0.45f) & 0xFFFFFF));
        // a shell round the head, the front open (a hood) or closed but for the eyes (a mask), a point at the back, the cowl
        for (int x = 0; x < 12; x++) for (int y = 2; y < 13; y++) for (int z = 0; z < 12; z++) {
            boolean shell = x == 0 || x == 11 || z == 11 || y == 12 || z == 0;
            if (!shell) continue;
            if (z == 0) {
                boolean face = x >= 2 && x <= 9 && y >= 3 && y <= 9;
                if (!mask && face) continue;
                if (mask && x >= 2 && x <= 9 && y >= 7 && y <= 8) continue;
            }
            int col = (x + y * 3 + z) % 5 == 0 ? sh(c, 0.92f) : c;
            if (z == 0 && (y == 10 || (!mask && (x == 1 || x == 10) && y <= 10))) col = mask ? sh(c, 0.85f) : trim;
            v.set(x, y, z, col);
        }
        for (int x = 1; x < 11; x++) for (int z = 1; z < 11; z++) v.set(x, 2, z, lining);
        for (int y = 9; y < 13; y++) v.box(4, y, 11, 7, y, 11, sh(c, 0.85f));
        v.box(5, 12, 9, 6, 12, 11, sh(c, 1.08f));
        // the cowl over the shoulders
        v.box(0, 0, 2, 11, 1, 11, sh(c, 0.9f));
        return new ItemModels.Model(v, 6, 0.5f, 6);
    }

    private static ItemModels.Model helmet(ItemDef d, int c, int trim) {
        VoxModel v = new VoxModel(16, 10, 16);
        // the brim, the dome, the ridge over it, the lamp at the front
        v.cylY(0, 0, 8, 8, 7.6f, sh(c, 0.82f));
        v.cylY(1, 4, 8, 8, 5.6f, c);
        v.cylY(5, 6, 8, 8, 4.8f, c);
        v.cylY(7, 7, 8, 8, 3.6f, sh(c, 1.08f));
        v.box(7, 1, 2, 8, 8, 13, sh(c, 1.15f));
        v.box(6, 2, 1, 9, 5, 2, metal(0x3A3A3E));
        v.box(7, 3, 0, 8, 4, 0, m(color(d, "lamp", 0xF2D27A)));
        v.cylY(1, 1, 8, 8, 5.7f, trim);
        return new ItemModels.Model(v, 8, 0.5f, 8);
    }

    private static ItemModels.Model fez(ItemDef d, int c) {
        VoxModel v = new VoxModel(9, 9, 9);
        int band = color(d, "band", -1), tassel = m(color(d, "tassel", 0x1A1A1A));
        for (int y = 0; y < 6; y++) v.cylY(y, y, 4.5f, 4.5f, 3.9f - y * 0.12f, y == 0 && band >= 0 ? m(band) : c);
        v.cylY(6, 6, 4.5f, 4.5f, 3.2f, sh(c, 0.82f));
        v.box(4, 7, 4, 4, 7, 4, tassel);
        v.box(5, 7, 4, 6, 7, 4, tassel);
        v.box(7, 3, 4, 7, 6, 4, tassel);
        v.box(7, 2, 3, 8, 3, 5, sh(tassel, 1.3f));
        return new ItemModels.Model(v, 4.5f, 0.5f, 4.5f);
    }

    private static ItemModels.Model strawHat(ItemDef d, int c) {
        VoxModel v = new VoxModel(19, 7, 19);
        int weave = m(color(d, "weave", Skin.shade(d.color, 0.85f) & 0xFFFFFF)), band = m(color(d, "band", 0x7A4A2A));
        for (int x = 0; x < 19; x++) for (int z = 0; z < 19; z++) {
            float r = (float) Math.hypot(x - 9, z - 9);
            if (r > 9.3f) continue;
            v.set(x, r > 7.5f ? 0 : 1, z, ((x + z) & 1) == 0 ? c : weave);   // the brim, its edge turned down
        }
        for (int y = 2; y < 6; y++) v.cylY(y, y, 9.5f, 9.5f, 4.6f - (y == 5 ? 0.6f : 0), ((y & 1) == 0) ? c : weave);
        v.cylY(2, 2, 9.5f, 9.5f, 4.7f, band);
        v.cylY(6, 6, 9.5f, 9.5f, 3.2f, weave);
        return new ItemModels.Model(v, 9.5f, 0.5f, 9.5f);
    }

    private static ItemModels.Model cap(int c, int trim) {
        VoxModel v = new VoxModel(11, 6, 15);
        v.cylY(0, 2, 5.5f, 9.5f, 5.2f, c);
        v.cylY(3, 4, 5.5f, 9.5f, 4.2f, c);
        v.cylY(5, 5, 5.5f, 9.5f, 2.6f, sh(c, 1.1f));
        v.box(2, 0, 0, 8, 0, 4, trim);                                   // the peak
        v.cylY(0, 0, 5.5f, 9.5f, 5.3f, trim);
        return new ItemModels.Model(v, 5.5f, 0.5f, 9.5f);
    }

    /** A top laid out flat: the body, the sleeves out to the sides, the collar, and what its look adds. */
    private static ItemModels.Model top(ItemDef d, int c, int trim) {
        VoxModel v = new VoxModel(22, 16, 3);
        int check = color(d, "check", -1), under = color(d, "under", -1), sash = color(d, "sash", -1), belt = color(d, "belt", -1);
        boolean loose = "loose".equals(str(d, "fit", ""));
        int bw = loose ? 12 : 10, x0 = (22 - bw) / 2;
        for (int x = x0; x < x0 + bw; x++) for (int y = 0; y < 15; y++) v.set(x, y, 1, cloth(c, check, x, y));
        for (int s = 0; s < 2; s++) for (int k = 0; k < (loose ? 6 : 5); k++) for (int y = 9 - k / 2; y < 15; y++) {
            int x = s == 0 ? x0 - 1 - k : x0 + bw + k;
            v.set(x, y, 1, cloth(c, check, x, y));
        }
        for (int s = 0; s < 2; s++) { int x = s == 0 ? x0 - (loose ? 6 : 5) : x0 + bw + (loose ? 5 : 4); for (int y = 9; y < 15; y++) v.set(x, y - (loose ? 3 : 2), 1, trim); }
        v.box(x0 + bw / 2 - 1, 13, 0, x0 + bw / 2, 14, 2, 0);                   // the neck
        v.box(x0 + bw / 2 - 2, 14, 2, x0 + bw / 2 + 1, 14, 2, trim);            // the collar behind it
        if (under >= 0) v.box(x0 + bw / 2 - 1, 0, 0, x0 + bw / 2, 12, 0, m(under));
        else for (int y = 2; y < 13; y++) v.set(x0 + bw / 2, y, 0, y % 3 == 0 ? sh(trim, 1.3f) : sh(c, 0.85f));
        if (flag(d, "pockets")) for (int s = 0; s < 2; s++) v.box(s == 0 ? x0 + 1 : x0 + bw - 4, 8, 0, s == 0 ? x0 + 3 : x0 + bw - 2, 10, 0, trim);
        if (sash >= 0) v.box(x0, 3, 0, x0 + bw - 1, 4, 2, m(sash));
        if (belt >= 0) v.box(x0, 3, 0, x0 + bw - 1, 3, 2, m(belt));
        v.box(x0, 0, 0, x0 + bw - 1, 0, 2, trim);
        return new ItemModels.Model(v, 11, 0.5f, 1.5f);
    }

    private static int cloth(int c, int check, int x, int y) {
        if (check < 0) return (x * 3 + y) % 7 == 0 ? sh(c, 0.92f) : c;
        boolean a = (x / 2 & 1) == 0, b = (y / 2 & 1) == 0;
        return a && b ? c : a != b ? Skin.mix(c, 0xFF000000 | check, 0.6f) | 0xFF000000 : m(check);
    }

    private static ItemModels.Model coat(ItemDef d, int c, int trim) {
        VoxModel v = new VoxModel(24, 24, 3);
        int layers = (int) num(d, "layers", 1);
        for (int x = 4; x < 20; x++) for (int y = 0; y < 22; y++) {
            if (Math.abs(x - 11.5f) < 1.5f + (22 - y) * 0.05f) continue;          // open down the front
            v.set(x, y, 1, (x + y * 2) % 9 == 0 ? sh(c, 0.92f) : c);
        }
        for (int y = 0; y < 22; y++) { v.set(10, y, 1, trim); v.set(13, y, 1, trim); }
        for (int s = 0; s < 2; s++) for (int k = 0; k < 4; k++) for (int y = 13; y < 22; y++) v.set(s == 0 ? 3 - k : 20 + k, y, 1, c);
        for (int s = 0; s < 2; s++) for (int y = 13; y < 22; y++) v.set(s == 0 ? 0 : 23, y, 1, trim);
        v.box(4, 0, 0, 19, 0, 2, trim);
        if (layers > 1) v.box(4, 2, 0, 19, 3, 0, sh(trim, 0.9f));
        return new ItemModels.Model(v, 12, 0.5f, 1.5f);
    }

    private static ItemModels.Model robe(ItemDef d, int c, int trim) {
        VoxModel v = new VoxModel(16, 20, 3);
        for (int y = 0; y < 19; y++) { int half = 5 + (18 - y) / 6; for (int x = 8 - half; x < 8 + half; x++) v.set(x, y, 1, (x + y) % 6 == 0 ? sh(c, 0.92f) : c); }
        v.box(2, 0, 0, 13, 1, 2, trim);
        v.box(3, 18, 0, 12, 18, 2, trim);
        return new ItemModels.Model(v, 8, 0.5f, 1.5f);
    }

    private static ItemModels.Model trousers(ItemDef d, int c, int trim) {
        VoxModel v = new VoxModel(13, 18, 3);
        int patches = color(d, "patches", -1), braces = color(d, "braces", -1), dust = color(d, "dust", -1);
        boolean loose = "loose".equals(str(d, "fit", ""));
        for (int s = 0; s < 2; s++) for (int y = 0; y < 15; y++) for (int k = 0; k < (loose ? 6 : 5); k++) {
            int x = s == 0 ? k : 12 - k;
            int col = (x + y) % 5 == 0 ? sh(c, 0.9f) : c;
            if (dust >= 0 && (x * 7 + y * 3) % 11 == 0 && y < 6) col = m(dust);
            v.set(x, y, 1, col);
        }
        v.box(0, 15, 1, 12, 16, 1, c);
        v.box(0, 16, 1, 12, 16, 1, trim);
        if (patches >= 0) { v.box(1, 6, 0, 3, 8, 0, m(patches)); v.box(9, 10, 0, 11, 12, 0, m(patches)); }
        if (braces >= 0) { v.box(2, 16, 1, 2, 17, 1, m(braces)); v.box(10, 16, 1, 10, 17, 1, m(braces)); }
        if (flag(d, "gathered")) for (int s = 0; s < 2; s++) v.box(s == 0 ? 0 : 8, 0, 0, s == 0 ? 4 : 12, 1, 2, sh(c, 0.85f));
        return new ItemModels.Model(v, 6.5f, 0.5f, 1.5f);
    }

    private static ItemModels.Model gloves(int c, int trim) {
        VoxModel v = new VoxModel(11, 9, 3);
        for (int k = 0; k < 2; k++) {
            int x0 = k * 6;
            v.box(x0, 0, 1, x0 + 4, 4, 1, c);
            for (int f = 0; f < 4; f++) v.box(x0 + f, 5, 1, x0 + f, 7 - (f == 0 || f == 3 ? 1 : 0), 1, sh(c, 1.1f));
            v.box(x0, 0, 0, x0 + 4, 1, 2, trim);
        }
        return new ItemModels.Model(v, 5.5f, 0.5f, 1.5f);
    }

    private static ItemModels.Model boots(ItemDef d, int c, int trim) {
        int rows = (int) Math.max(3, Math.min(14, num(d, "rows", 10) * 0.6));
        VoxModel v = new VoxModel(11, rows + 3, 9);
        int sole = m(color(d, "sole", 0x1A1410)), toe = color(d, "toe", -1), laces = color(d, "laces", -1);
        for (int k = 0; k < 2; k++) {
            int x0 = k * 6;
            v.box(x0, 0, 0, x0 + 4, 0, 8, sole);
            v.box(x0, 1, 0, x0 + 4, 2, 8, c);
            v.box(x0, 3, 0, x0 + 4, rows + 1, 3, c);
            v.box(x0, rows + 1, 0, x0 + 4, rows + 2, 3, sh(trim, 1.15f));
            if (toe >= 0) v.box(x0, 1, 6, x0 + 4, 2, 8, metal(toe));
            if (laces >= 0) for (int y = 3; y < rows; y += 2) v.box(x0 + 1, y, 4, x0 + 3, y, 4, m(laces));
            if (flag(d, "heavy")) v.box(x0 - 0, 0, 0, x0 + 4, 0, 8, sh(sole, 0.8f));
        }
        return new ItemModels.Model(v, 5.5f, 0.5f, 4.5f);
    }

    private static ItemModels.Model cloak(ItemDef d, int c, int trim) {
        int len = (int) Math.max(16, Math.min(30, num(d, "length", 22)));
        VoxModel v = new VoxModel(17, len + 1, 2);
        int lining = m(color(d, "lining", Skin.shade(d.color, 0.6f) & 0xFFFFFF));
        for (int y = 0; y < len; y++) {
            int half = 5 + (len - 1 - y) / 5;
            for (int x = -half; x <= half; x++) {
                boolean edge = Math.abs(x) == half || y == 0;
                v.set(8 + x, y, 1, edge ? trim : (x + y) % 5 == 0 ? sh(c, 0.9f) : c);   // the outside (seen from behind)
                v.set(8 + x, y, 0, lining);
            }
        }
        v.box(4, len, 0, 12, len, 1, trim);
        return new ItemModels.Model(v, 8.5f, len - 0.5f, 0.5f);
    }

    private static ItemModels.Model insignia(ItemDef d, int c) {
        VoxModel v = new VoxModel(9, 11, 2);
        int cord = m(color(d, "cord", 0x2A2230)), mark = m(color(d, "mark", 0x5B2C7A));
        for (int t = 0; t < 14; t++) {
            double a = Math.PI * t / 13;
            v.set((int) Math.round(4 + Math.cos(a) * 4), (int) Math.round(10 - Math.sin(a) * 4), 1, cord);
        }
        for (int x = 2; x <= 6; x++) for (int y = 0; y <= 4; y++) if (Math.hypot(x - 4, y - 2) <= 2.4) v.set(x, y, 0, metal(c));
        v.box(4, 1, 0, 4, 3, 0, mark);
        v.box(3, 2, 0, 5, 2, 0, mark);
        v.box(4, 5, 0, 4, 6, 0, cord);
        return new ItemModels.Model(v, 4.5f, 10.5f, 0.5f);
    }
}
