import os
os.chdir(r'C:\Users\John\Documents\ProjectRPG\rpggame\src\main\java\rpg\render')
def patch(path, pairs):
    s = open(path, encoding='utf-8').read()
    for old, new in pairs:
        assert old in s, (path, old[:90])
        s = s.replace(old, new, 1)
    open(path, 'w', encoding='utf-8').write(s)

patch('Skin.java', [
 ('''    public record Armor(ItemDef head, ItemDef body, ItemDef legs, ItemDef feet, ItemDef hands) {
        public String key() { return id(head) + "," + id(body) + "," + id(legs) + "," + id(feet) + "," + id(hands); }''',
  '''    public record Armor(ItemDef head, ItemDef body, ItemDef legs, ItemDef feet, ItemDef hands, ItemDef cape) {
        public Armor(ItemDef head, ItemDef body, ItemDef legs, ItemDef feet, ItemDef hands) { this(head, body, legs, feet, hands, null); }
        public String key() { return id(head) + "," + id(body) + "," + id(legs) + "," + id(feet) + "," + id(hands) + "," + id(cape); }'''),
 # route garments
 ('''    private void armor(Armor a) {
        if (a.body() != null) {''',
  '''    private void armor(Armor a) {
        // (0.4.5, gamefixes: the clothing update) the outfits' pieces are cloth and leather of their own cut, painted by
        // their look; the rest is armour as before
        if (a.body() != null && a.body().wear != null) garmentTop(a.body());
        if (a.cape() != null && a.cape().wear != null && "overcoat".equals(Garments.shape(a.cape()))) overcoat(a.cape(), a.body());
        if (a.legs() != null && a.legs().wear != null) garmentLegs(a.legs(), a.body());
        if (a.feet() != null && a.feet().wear != null) garmentFeet(a.feet());
        if (a.hands() != null && a.hands().wear != null) garmentHands(a.hands());
        if (a.head() != null && a.head().wear != null) garmentHead(a.head());
        a = new Armor(a.head() != null && a.head().wear != null ? null : a.head(), a.body() != null && a.body().wear != null ? null : a.body(),
                a.legs() != null && a.legs().wear != null ? null : a.legs(), a.feet() != null && a.feet().wear != null ? null : a.feet(), a.hands() != null && a.hands().wear != null ? null : a.hands());
        if (a.body() != null) {'''),
 # the painter itself, before the plate() helper
 ('''    private static boolean metal(ItemDef d) {''',
  '''    // ------------------------------------------------------------------ garments (0.4.5): cloth and leather by their look
    /** A cloth texel: the colour, a weave's grain, and (by the look) a check, a plain leather sheen or dust. */
    private int cloth(ItemDef d, int c, Part p, int f, int i, int j) {
        int out = grain(c, p, f, i, j, Garments.flag(d, "leather") ? 0.04f : 0.07f);
        int check = Garments.color(d, "check", -1);
        if (check >= 0) {
            boolean a = (i / 4 & 1) == 0, b = (j / 4 & 1) == 0;
            int ck = 0xFF000000 | check;
            out = a && b ? grain(c, p, f, i, j, 0.06f) : a != b ? mix(grain(c, p, f, i, j, 0.06f), ck, 0.62f) : grain(ck, p, f, i, j, 0.08f);
            if (i % 4 == 1 || j % 4 == 1) out = shade(out, 0.9f);
        }
        int dust = Garments.color(d, "dust", -1);
        if (dust >= 0) {
            long h = (seed * 31 + p.ordinal() * 977L + f * 131L + i * 17L + j * 1301L) * 0x9E3779B97F4A7C15L; h ^= h >>> 31;
            float k = (h & 255) / 255f, low = Math.max(0, (j - 8) / 16f);
            if (k < 0.12f + low * 0.25f) out = mix(out, 0xFF000000 | dust, 0.35f + 0.25f * k);
        }
        return out;
    }

    private void fillCloth(ItemDef d, Part p, int j0, int j1, int c) {
        for (int f = RIGHT; f <= BACK; f++) { int w = faceW(p, f), h = faceH(p, f); for (int j = Math.max(0, j0); j < Math.min(h, j1); j++) for (int i = 0; i < w; i++) set(p, f, i, j, cloth(d, c, p, f, i, j)); }
    }

    /** A top: the body and sleeves in its cloth, its collar, opening, pockets, belt or sash, cuffs and hem. */
    private void garmentTop(ItemDef d) {
        int c = 0xFF000000 | d.color, trim = 0xFF000000 | Garments.color(d, "trim", Skin.shade(d.color, 0.7f) & 0xFFFFFF);
        fillCloth(d, Part.JACKET, 0, 24, c);
        cap(Part.JACKET, TOP, shade(c, 1.05f), 0.05f);
        boolean rolled = Garments.flag(d, "rolled");
        for (Part sl : new Part[]{Part.R_SLEEVE, Part.L_SLEEVE}) {
            fillCloth(d, sl, 0, rolled ? 13 : 24, c);
            cap(sl, TOP, shade(c, 1.06f), 0.05f);
            if (rolled) sides(sl, 11, 13, shade(c, 1.18f), 0.03f);                 // the roll above the elbow
            else sides(sl, 21, 24, trim, 0.03f);                                       // the cuff
        }
        int w = faceW(Part.JACKET, FRONT), cx = w / 2, th = faceH(Part.JACKET, TOP);
        // the neck stays open; the collar round it
        rect(Part.JACKET, FRONT, cx - 2, 0, cx + 2, 2, 0);
        rect(Part.JACKET, TOP, cx - 2, th / 2 - 1, cx + 2, th, 0);
        switch (Garments.str(d, "collar", "")) {
            case "high" -> { sides(Part.JACKET, 0, 2, trim, 0.03f); rect(Part.JACKET, FRONT, cx - 2, 0, cx + 2, 1, 0); }
            case "fold" -> { for (int j = 0; j < 7; j++) { set(Part.JACKET, FRONT, cx - 3 - j / 3, j, shade(c, 1.15f)); set(Part.JACKET, FRONT, cx + 2 + j / 3, j, shade(c, 1.15f)); } rect(Part.JACKET, FRONT, cx - 1, 2, cx + 1, 6, 0); }
            case "slit" -> { rect(Part.JACKET, FRONT, cx - 1, 0, cx + 1, 6, 0); for (int j = 0; j < 7; j++) { set(Part.JACKET, FRONT, cx - 2, j, trim); set(Part.JACKET, FRONT, cx + 1, j, trim); } }
            default -> { }
        }
        // an open front over an undershirt (a work jacket worn open)
        int under = Garments.color(d, "under", -1);
        if (under >= 0) for (int j = 2; j < 24; j++) for (int i = cx - 2; i < cx + 2; i++) set(Part.JACKET, FRONT, i, j, grain(0xFF000000 | under, Part.JACKET, FRONT, i, j, 0.05f));
        // a placket and its buttons, or embroidery down the front
        if (Garments.flag(d, "embroidery")) for (int j = 2; j < 22; j++) { set(Part.JACKET, FRONT, cx - 3, j, (j & 1) == 0 ? trim : shade(trim, 0.8f)); set(Part.JACKET, FRONT, cx + 2, j, (j & 1) == 1 ? trim : shade(trim, 0.8f)); }
        else if (under < 0 && !Garments.str(d, "collar", "").equals("slit")) for (int j = 6; j < 22; j++) set(Part.JACKET, FRONT, cx, j, j % 4 == 0 ? shade(trim, 1.3f) : shade(c, 0.82f));
        // pockets on the chest
        if (Garments.flag(d, "pockets")) for (int s = -1; s <= 1; s += 2) {
            int x0 = s < 0 ? 2 : w - 7, x1 = x0 + 5;
            for (int i = x0; i < x1; i++) { set(Part.JACKET, FRONT, i, 7, trim); set(Part.JACKET, FRONT, i, 12, shade(trim, 0.8f)); }
            for (int j = 7; j < 13; j++) { set(Part.JACKET, FRONT, x0, j, trim); set(Part.JACKET, FRONT, x1 - 1, j, trim); }
            for (int i = x0; i < x1; i++) set(Part.JACKET, FRONT, i, 8, shade(c, 0.85f));   // the flap's shadow
        }
        // leather: panel seams and a sheen down the chest; straps across it
        if (Garments.flag(d, "leather")) {
            for (int f = RIGHT; f <= BACK; f++) for (int j = 0; j < 24; j++) { set(Part.JACKET, f, 0, j, shade(c, 0.7f)); }
            for (int j = 4; j < 20; j++) set(Part.JACKET, FRONT, cx - 4, j, shade(c, 1.35f));
        }
        int straps = Garments.color(d, "straps", -1);
        if (straps >= 0) for (int j = 2; j < 20; j++) { int i = 2 + j * (w - 5) / 20; set(Part.JACKET, FRONT, i, j, 0xFF000000 | straps); set(Part.JACKET, FRONT, i + 1, j, 0xFF000000 | straps); }
        // the waist: a belt, or a sash knotted at the side
        int belt = Garments.color(d, "belt", -1), sash = Garments.color(d, "sash", -1);
        if (belt >= 0) { sides(Part.JACKET, 18, 20, 0xFF000000 | belt, 0.03f); rect(Part.JACKET, FRONT, cx - 1, 18, cx + 1, 20, 0xFFB0A070); }
        if (sash >= 0) { sides(Part.JACKET, 17, 20, 0xFF000000 | sash, 0.05f); rect(Part.JACKET, FRONT, w - 4, 17, w - 1, 23, shade(0xFF000000 | sash, 0.85f)); }
        // the hem's band
        sides(Part.JACKET, 23, 24, shade(trim, 0.9f), 0.02f);
    }

    /** An open coat over whatever is beneath: its sides, back and sleeves, the front left open down the middle. */
    private void overcoat(ItemDef d, ItemDef top) {
        int c = 0xFF000000 | d.color, trim = 0xFF000000 | Garments.color(d, "trim", Skin.shade(d.color, 0.7f) & 0xFFFFFF);
        int w = faceW(Part.JACKET, FRONT), cx = w / 2;
        for (int f = RIGHT; f <= BACK; f++) for (int j = 1; j < 24; j++) for (int i = 0; i < faceW(Part.JACKET, f); i++) {
            if (f == FRONT && Math.abs(i + 0.5f - cx) < 4.5f - j * 0.06f) continue;   // the open front, wider at the chest
            set(Part.JACKET, f, i, j, cloth(d, c, Part.JACKET, f, i, j));
        }
        // the facing down each side of the opening, in the trim
        for (int j = 1; j < 24; j++) { int e = Math.round(4.5f - j * 0.06f); set(Part.JACKET, FRONT, cx - e - 1, j, trim); set(Part.JACKET, FRONT, cx + e, j, trim); }
        sides(Part.JACKET, 0, 1, trim, 0.02f);
        for (Part sl : new Part[]{Part.R_SLEEVE, Part.L_SLEEVE}) { fillCloth(d, sl, 0, 22, c); sides(sl, 19, 22, trim, 0.03f); sides(sl, 22, 24, 0, 0); }
        if (top != null && top.wear != null) rect(Part.JACKET, FRONT, cx - 2, 0, cx + 2, 2, 0);
    }

    /** Legs: trousers (slim, plain or loose, patched, dusty, gathered at the ankle) or the leg under a robe. */
    private void garmentLegs(ItemDef d, ItemDef top) {
        boolean robe = "robe".equals(Garments.shape(d));
        int c = 0xFF000000 | (robe ? Garments.color(d, "under", d.color) : d.color), trim = 0xFF000000 | Garments.color(d, "trim", Skin.shade(d.color, 0.7f) & 0xFFFFFF);
        for (Part p : new Part[]{Part.R_PANTS, Part.L_PANTS}) {
            fillCloth(d, p, 0, 24, c);
            cap(p, TOP, c, 0.04f);
            sides(p, 0, 1, trim, 0.02f);                                                // the waistband
            if ("loose".equals(Garments.str(d, "fit", ""))) { darken(p, FRONT, 0, 6, 1, 24, 0.82f); darken(p, BACK, 0, 6, 1, 24, 0.82f); darken(p, FRONT, faceW(p, FRONT) - 1, 6, faceW(p, FRONT), 24, 0.86f); }
            if ("slim".equals(Garments.str(d, "fit", ""))) for (int j = 2; j < 24; j++) set(p, RIGHT, faceW(p, RIGHT) / 2, j, shade(c, 0.75f));   // the seam down the side
            if (Garments.flag(d, "gathered")) sides(p, 20, 23, shade(c, 0.86f), 0.06f);
            int patches = Garments.color(d, "patches", -1), stitch = Garments.color(d, "stitch", -1);
            if (patches >= 0) for (int k = 0; k < 3; k++) {
                long h = (p.ordinal() * 7L + k * 131L + seed) * 0x9E3779B97F4A7C15L; h ^= h >>> 29;
                int f = new int[]{FRONT, RIGHT, BACK, LEFT}[(int) ((h >>> 8) & 3)], fw = faceW(p, f);
                int pw = 3 + (int) ((h >>> 12) & 1), ph = 3 + (int) ((h >>> 14) & 3), x0 = (int) ((h >>> 16) % Math.max(1, fw - pw)), y0 = 4 + (int) ((h >>> 20) % 14);
                for (int j = y0; j < y0 + ph; j++) for (int i = x0; i < x0 + pw; i++) {
                    boolean edge = j == y0 || j == y0 + ph - 1 || i == x0 || i == x0 + pw - 1;
                    set(p, f, i, j, edge && stitch >= 0 && ((i + j) & 1) == 0 ? 0xFF000000 | stitch : grain(0xFF000000 | patches, p, f, i, j, 0.08f));
                }
            }
        }
        // braces over the shirt, front and back
        int braces = Garments.color(d, "braces", -1);
        if (braces >= 0) { int w = faceW(Part.JACKET, FRONT); for (int j = 0; j < 19; j++) for (int f : new int[]{FRONT, BACK}) { set(Part.JACKET, f, 3, j, 0xFF000000 | braces); set(Part.JACKET, f, w - 4, j, 0xFF000000 | braces); } }
    }

    /** Boots: up the leg as far as their height, a cuff at the top, laces, a capped toe, the sole. */
    private void garmentFeet(ItemDef d) {
        int c = 0xFF000000 | d.color, rows = Math.max(3, Math.min(20, (int) Garments.num(d, "rows", 10)));
        int sole = 0xFF000000 | Garments.color(d, "sole", 0x1A1410), trim = 0xFF000000 | Garments.color(d, "trim", Skin.shade(d.color, 0.8f) & 0xFFFFFF);
        for (Part p : new Part[]{Part.R_PANTS, Part.L_PANTS}) {
            int top = 24 - rows;
            sides(p, top, 24, c, 0.05f);
            sides(p, top, top + 1, shade(trim, 1.1f), 0.02f);
            if (Garments.flag(d, "heavy")) sides(p, top + 1, top + 2, shade(c, 0.75f), 0.02f);
            sides(p, 23, 24, sole, 0.02f);
            cap(p, BOTTOM, sole, 0.03f);
            int laces = Garments.color(d, "laces", -1), w = faceW(p, FRONT);
            if (laces >= 0) for (int j = top + 2; j < 21; j += 2) { set(p, FRONT, w / 2 - 1, j, 0xFF000000 | laces); set(p, FRONT, w / 2, j + 1, 0xFF000000 | laces); }
            int toe = Garments.color(d, "toe", -1);
            if (toe >= 0) rect(p, FRONT, 0, 20, w, 23, shade(0xFF000000 | toe, 1.05f));
        }
    }

    private void garmentHands(ItemDef d) {
        int c = 0xFF000000 | d.color, trim = 0xFF000000 | Garments.color(d, "trim", Skin.shade(d.color, 0.8f) & 0xFFFFFF);
        for (Part p : new Part[]{Part.R_SLEEVE, Part.L_SLEEVE}) { sides(p, 17, 24, c, 0.05f); sides(p, 17, 18, trim, 0.02f); cap(p, BOTTOM, shade(c, 0.9f), 0.03f); }
    }

    /** A hood (the face open, lined at its edge), a mask (all but the eyes), or a hat's band (its shape is built: Gfx). */
    private void garmentHead(ItemDef d) {
        int c = 0xFF000000 | d.color, trim = 0xFF000000 | Garments.color(d, "trim", Skin.shade(d.color, 0.75f) & 0xFFFFFF);
        int w = faceW(Part.HAT, FRONT), h = faceH(Part.HAT, FRONT);
        switch (Garments.shape(d)) {
            case "hood" -> {
                for (int f = RIGHT; f <= BACK; f++) for (int j = 0; j < h; j++) for (int i = 0; i < faceW(Part.HAT, f); i++) set(Part.HAT, f, i, j, cloth(d, c, Part.HAT, f, i, j));
                cap(Part.HAT, TOP, shade(c, 1.05f), 0.06f);
                int lining = 0xFF000000 | Garments.color(d, "lining", Skin.shade(d.color, 0.5f) & 0xFFFFFF);
                rect(Part.HAT, FRONT, 3, 4, w - 3, h, 0);                                // the face, open
                for (int j = 3; j < h; j++) { set(Part.HAT, FRONT, 2, j, lining); set(Part.HAT, FRONT, w - 3, j, lining); }
                for (int i = 2; i < w - 2; i++) set(Part.HAT, FRONT, i, 3, trim);
                for (int j = 0; j < h; j += 3) { set(Part.HAT, BACK, w / 2, j, shade(c, 0.8f)); }   // the back seam
            }
            case "mask" -> {
                for (int f = RIGHT; f <= BACK; f++) for (int j = 0; j < h; j++) for (int i = 0; i < faceW(Part.HAT, f); i++) set(Part.HAT, f, i, j, cloth(d, c, Part.HAT, f, i, j));
                cap(Part.HAT, TOP, shade(c, 1.04f), 0.05f);
                rect(Part.HAT, FRONT, 3, 6, w - 3, 9, 0);                                // the eye slit
                for (int i = 2; i < w - 2; i++) { set(Part.HAT, FRONT, i, 5, trim); set(Part.HAT, FRONT, i, 9, trim); }
                for (int j = 10; j < h; j += 2) set(Part.HAT, FRONT, w / 2, j, shade(c, 0.85f));
            }
            case "mining_helmet" -> { sides(Part.HAT, 0, 7, c, 0.05f); cap(Part.HAT, TOP, shade(c, 1.08f), 0.05f); sides(Part.HAT, 6, 7, trim, 0.02f); }
            default -> { }
        }
    }

    private static boolean metal(ItemDef d) {'''),
])
print('ok')
