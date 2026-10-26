package rpg.render;

import rpg.actor.Appearance;
import rpg.actor.Body;
import rpg.item.ItemDef;
import rpg.item.Tier;

/**
 * A human's 128 x 128 skin texture (two texels to the model pixel), painted from code from their look and the armor
 * they wear. The layout is the familiar box unwrap: each part's six faces laid out round its front, the base layer
 * (skin, clothes) and an outer layer (armor) one band apart. {@link CharModel} turns it into the figure's geometry.
 */
public final class Skin {
    public static final int N = Body.SKIN, T = Body.TEXELS;
    public static final int TOP = 0, BOTTOM = 1, RIGHT = 2, FRONT = 3, LEFT = 4, BACK = 5;

    /** Each part's box on the texture: where its unwrapped faces start, in model pixels. */
    public enum Part {
        HEAD(0, 0), HAT(32, 0), TORSO(16, 16), JACKET(16, 32), R_ARM(40, 16), R_SLEEVE(40, 32), L_ARM(32, 48), L_SLEEVE(48, 48),
        R_LEG(0, 16), R_PANTS(0, 32), L_LEG(16, 48), L_PANTS(0, 48);
        final int u, v;
        Part(int u, int v) { this.u = u; this.v = v; }
    }

    /** ARGB; alpha 0 is a hole (the outer layer is mostly holes). */
    public final int[] px = new int[N * N];
    private float[] cuts;
    public final Body body;

    private Skin(Body body) { this.body = body; }

    public int[] dims(Part p) {
        return switch (p) {
            case HEAD, HAT -> body.head;
            case TORSO, JACKET -> body.torso;
            case R_ARM, R_SLEEVE, L_ARM, L_SLEEVE -> body.arm;
            default -> body.leg;
        };
    }

    /** A face's rectangle on the texture, in texels: x, y, width, height. */
    public int[] face(Part p, int f) {
        int[] d = dims(p);
        int w = d[0], h = d[1], dp = d[2], u = p.u, v = p.v;
        int[] r = switch (f) {
            case TOP -> new int[]{u + dp, v, w, dp};
            case BOTTOM -> new int[]{u + dp + w, v, w, dp};
            case RIGHT -> new int[]{u, v + dp, dp, h};
            case FRONT -> new int[]{u + dp, v + dp, w, h};
            case LEFT -> new int[]{u + dp + w, v + dp, dp, h};
            default -> new int[]{u + 2 * dp + w, v + dp, w, h};
        };
        return new int[]{r[0] * T, r[1] * T, r[2] * T, r[3] * T};
    }

    public float depth(Part part,int face,int x,int y) {
        if(cuts==null)return 0;int[] area=face(part,face);
        return x<0 || y<0 || x>=area[2] || y>=area[3]?0:cuts[(area[1]+y)*N+area[0]+x];
    }
    public void injure(java.util.List<rpg.actor.Injuries.Cut> wounds,int severity) {
        if(severity==0 || wounds.isEmpty())return;cuts=new float[N*N];
        for(var wound:wounds)for(Part layer:BLOOD_PARTS[wound.part()]) {
            int[] face=face(layer,wound.face());int w=face[2],h=face[3];
            float cx=wound.u()*w,cy=wound.v()*h,angle=wound.seed()*2.39996f;
            for(int y=0;y<h;y++)for(int x=0;x<w;x++) {
                float dx=x+0.5f-cx,dy=y+0.5f-cy;
                float across=Math.abs(dx*(float)Math.cos(angle)+dy*(float)Math.sin(angle));
                float along=Math.abs(-dx*(float)Math.sin(angle)+dy*(float)Math.cos(angle));
                if(across>0.7f || along>wound.radius())continue;
                int index=(face[1]+y)*N+face[0]+x;if((px[index]>>>24)==0)continue;
                cuts[index]=Math.max(cuts[index],Math.min(0.75f,0.15f+severity*0.075f));
                px[index]=0xFF000000 | (mix(px[index],0xFF6B221C,0.8f)&0xFFFFFF);
            }
        }
    }

    public int get(Part p, int f, int i, int j) {
        int[] r = face(p, f);
        if (i < 0 || j < 0 || i >= r[2] || j >= r[3]) return 0;
        return px[(r[1] + j) * N + r[0] + i];
    }

    private void set(Part p, int f, int i, int j, int argb) {
        int[] r = face(p, f);
        if (i < 0 || j < 0 || i >= r[2] || j >= r[3]) return;
        px[(r[1] + j) * N + r[0] + i] = argb;
    }

    // ------------------------------------------------------------------ painting tools
    private long seed;

    /** A little texel-to-texel variation so flat colours read as cloth and skin. */
    private int grain(int rgb, Part p, int f, int i, int j, float amount) {
        long h = (seed + p.ordinal() * 7919L + f * 104729L + i * 31L + j * 1013L) * 0x9E3779B97F4A7C15L;
        h ^= h >>> 29;
        float n = 1 + ((h & 1023) / 1023f - 0.5f) * 2 * amount;
        return shade(rgb, n);
    }

    static int shade(int rgb, float f) {
        int r = clamp(((rgb >> 16) & 255) * f), g = clamp(((rgb >> 8) & 255) * f), b = clamp((rgb & 255) * f);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    static int mix(int a, int b, float t) {
        int r = clamp(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t), g = clamp(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t), bl = clamp((a & 255) * (1 - t) + (b & 255) * t);
        return 0xFF000000 | r << 16 | g << 8 | bl;
    }

    private static int clamp(float v) { return Math.max(0, Math.min(255, Math.round(v))); }

    /** Rows j0..j1-1 (texels from the top) of the four side faces. */
    private void sides(Part p, int j0, int j1, int rgb, float grain) {
        for (int f = RIGHT; f <= BACK; f++) {
            int[] r = face(p, f);
            for (int j = Math.max(0, j0); j < Math.min(r[3], j1); j++) for (int i = 0; i < r[2]; i++) set(p, f, i, j, grain(rgb, p, f, i, j, grain));
        }
    }

    private void cap(Part p, int f, int rgb, float grain) {
        int[] r = face(p, f);
        for (int j = 0; j < r[3]; j++) for (int i = 0; i < r[2]; i++) set(p, f, i, j, grain(rgb, p, f, i, j, grain));
    }

    private void all(Part p, int rgb, float grain) { cap(p, TOP, rgb, grain); cap(p, BOTTOM, rgb, grain); sides(p, 0, 999, rgb, grain); }

    private void rect(Part p, int f, int i0, int j0, int i1, int j1, int rgb) { for (int j = j0; j < j1; j++) for (int i = i0; i < i1; i++) set(p, f, i, j, rgb); }

    /** Multiplies what is already painted in a rectangle. */
    private void darken(Part p, int f, int i0, int j0, int i1, int j1, float k) {
        for (int j = j0; j < j1; j++) for (int i = i0; i < i1; i++) { int c = get(p, f, i, j); if ((c >>> 24) != 0) set(p, f, i, j, shade(c, k)); }
    }

    private int faceW(Part p, int f) { return face(p, f)[2]; }
    private int faceH(Part p, int f) { return face(p, f)[3]; }

    // ------------------------------------------------------------------ the figure
    /** What is worn over the body, by slot (null where nothing is). */
    public record Armor(ItemDef head, ItemDef body, ItemDef legs, ItemDef feet, ItemDef hands) {
        public String key() { return id(head) + "," + id(body) + "," + id(legs) + "," + id(feet) + "," + id(hands); }
        private static String id(ItemDef d) { return d == null ? "-" : d.id; }
    }

    public static Skin paint(Appearance look, Armor armor) { return paint(look, armor, null); }

    /** The skin with the blood on the body painted over it (on the clothes, and on armor where armor covers). */
    public static Skin paint(Appearance look, Armor armor, java.util.List<rpg.actor.Blood.Mark> blood) {
        Skin s = new Skin(Body.of(look));
        s.seed = 0x51ED + look.skin * 31L + look.hair * 977L + look.top * 7L;
        s.head(look);
        s.torso(look);
        s.arm(look, Part.R_ARM, true);
        s.arm(look, Part.L_ARM, false);
        s.leg(look, Part.R_LEG, true);
        s.leg(look, Part.L_LEG, false);
        if (armor != null) s.armor(armor);
        if (blood != null) for (rpg.actor.Blood.Mark m : blood) s.blood(m);
        return s;
    }

    private static final Part[][] BLOOD_PARTS = {{Part.HEAD, Part.HAT}, {Part.TORSO, Part.JACKET}, {Part.R_ARM, Part.R_SLEEVE}, {Part.L_ARM, Part.L_SLEEVE},
            {Part.R_LEG, Part.R_PANTS}, {Part.L_LEG, Part.L_PANTS}};

    /**
     * One splash: a ragged blot round its centre (soaked in the middle, thinner at the edge), and - wet blood running -
     * a drip or two down the face below it. On the base layer and on whatever armor texels lie over it.
     */
    private void blood(rpg.actor.Blood.Mark m) {
        if (m.part() < 0 || m.part() >= BLOOD_PARTS.length) return;
        int[] r = face(BLOOD_PARTS[m.part()][0], m.face());
        int w = r[2], h = r[3];
        if (w <= 0 || h <= 0) return;
        float cx = m.u() * w, cy = m.v() * h, rad = m.radius();
        int base = m.wet() ? 0xFF8A100C : 0xFF4A0C0A;
        long sd = m.seed() * 0x9E3779B97F4A7C15L;
        for (int layer = 0; layer < 2; layer++) {
            Part p = BLOOD_PARTS[m.part()][layer];
            for (int j = (int) Math.floor(cy - rad - 1); j <= cy + rad + 1; j++) for (int i = (int) Math.floor(cx - rad - 1); i <= cx + rad + 1; i++) {
                if (i < 0 || j < 0 || i >= w || j >= h) continue;
                long hh = (sd + i * 0x632BE59BD9B4E019L + j * 0x85EBCA77C2B2AE63L);
                hh ^= hh >>> 31; hh *= 0xBF58476D1CE4E5B9L; hh ^= hh >>> 29;
                float noise = (hh & 1023) / 1023f;
                float d = (float) Math.hypot(i + 0.5f - cx, (j + 0.5f - cy) * 1.15f) / Math.max(0.5f, rad);
                if (d > 0.7f + noise * 0.55f) continue;
                int c = get(p, m.face(), i, j);
                if ((c >>> 24) == 0) continue;
                float t = Math.max(0.35f, Math.min(0.9f, 1.05f - d * 0.6f));
                set(p, m.face(), i, j, mix(c, shade(base, 0.85f + noise * 0.3f), t));
            }
            // a wet splash runs: one or two thin drips down from its lower edge (not on the top or bottom of a part)
            if (m.wet() && m.face() != TOP && m.face() != BOTTOM) for (int k = 0; k < 1 + (m.seed() & 1); k++) {
                int i = (int) (cx + ((sd >>> (8 + k * 5)) % 5 - 2) * rad * 0.25f);
                int len = (int) (rad * (0.8f + ((sd >>> (20 + k * 3)) & 7) / 7f * 1.2f));
                for (int j = (int) cy; j < Math.min(h, cy + rad + len); j++) {
                    if (i < 0 || i >= w) break;
                    int c = get(p, m.face(), i, j);
                    if ((c >>> 24) != 0) set(p, m.face(), i, j, mix(c, base, 0.75f));
                }
            }
        }
    }

    private static int garment(int index) { return 0xFF000000 | Appearance.color(index); }
    /** A lighter or darker companion of a garment colour (linings, undershirts, trims), never another catalog colour. */
    private static int companion(int rgb, float k) { return k >= 1 ? mix(rgb, 0xFFF0EBDD, k - 1) : shade(rgb, k); }

    private void head(Appearance look) {
        int skin = 0xFF000000 | look.skinRgb(), hair = 0xFF000000 | Appearance.hairColor(look.hairColor);
        all(Part.HEAD, skin, 0.025f);
        cap(Part.HEAD, BOTTOM, shade(skin, 0.78f), 0.02f);
        // the hair as it lies on the scalp under the volume: roots for the styles that have them, stubble on shaved sides
        int rows = HairMeshes.scalpRows(look.female, look.hair);
        boolean shaved = HairMeshes.shavedSides(look.female, look.hair);
        if (rows > 0) {
            int scalp = shade(hair, 0.92f);
            cap(Part.HEAD, TOP, scalp, 0.08f);
            for (int i = 0; i < 16; i++) for (int j = 0; j < rows; j++) set(Part.HEAD, BACK, i, j, grain(scalp, Part.HEAD, BACK, i, j, 0.08f));
            for (int f : new int[]{RIGHT, LEFT}) for (int i = 0; i < 16; i++) {
                boolean front = f == RIGHT ? i >= 12 : i <= 3;
                int r = front ? Math.min(rows, 6) : Math.min(rows, 7) + (i % 3 == 0 ? 1 : 0);
                for (int j = 0; j < r; j++) set(Part.HEAD, f, i, j, grain(scalp, Part.HEAD, f, i, j, 0.08f));
            }
            for (int i = 0; i < 16; i++) for (int j = 0; j < 2 + ((i * 7 + look.hair) % 3 == 0 ? 1 : 0); j++) set(Part.HEAD, FRONT, i, j, grain(scalp, Part.HEAD, FRONT, i, j, 0.08f));
        } else if (look.hair == 0) darken(Part.HEAD, TOP, 0, 0, 16, 16, 1.06f);   // bald: the scalp catches the light
        if (shaved) {
            int stub = mix(skin, hair, 0.35f);
            for (int f : new int[]{RIGHT, LEFT, BACK}) for (int i = 0; i < 16; i++) for (int j = 0; j < 9; j++) set(Part.HEAD, f, i, j, grain(stub, Part.HEAD, f, i, j, 0.1f));
        }
        // ears: on both sides, level with the eyes
        for (int f : new int[]{RIGHT, LEFT}) {
            int c0 = f == RIGHT ? 6 : 7;
            rect(Part.HEAD, f, c0, 8, c0 + 3, 12, shade(skin, 0.9f));
            rect(Part.HEAD, f, c0 + 1, 9, c0 + 2, 11, shade(skin, 0.74f));
        }
        // the face (the viewer's left is the figure's right)
        int brow = look.hair == 0 && look.beard == 0 ? shade(hair, 0.8f) : shade(hair, 0.85f);
        if (look.female) {
            // hers are finer and arched: a thin line, high in the middle, falling at the outer ends, softened toward the skin
            int fine = mix(skin, brow, 0.75f);
            rect(Part.HEAD, FRONT, 3, 5, 6, 6, fine); set(Part.HEAD, FRONT, 2, 6, fine);
            rect(Part.HEAD, FRONT, 10, 5, 13, 6, fine); set(Part.HEAD, FRONT, 13, 6, fine);
        } else {
            rect(Part.HEAD, FRONT, 2, 6, 6, 7, brow);
            rect(Part.HEAD, FRONT, 10, 6, 14, 7, brow);
        }
        rect(Part.HEAD, FRONT, 2, 7, 7, 8, shade(skin, 0.9f));   // lids
        rect(Part.HEAD, FRONT, 9, 7, 14, 8, shade(skin, 0.9f));
        int[] irises = {0xFF5A3A20, 0xFF7A5A2A, 0xFF3A6AA8, 0xFF4A7A3A, 0xFF6A7078};
        int iris = irises[(look.skin * 3 + look.hairColor + look.top) % irises.length];
        for (int e = 0; e < 2; e++) {
            int x0 = e == 0 ? 3 : 10;
            rect(Part.HEAD, FRONT, x0, 8, x0 + 3, 10, 0xFFF2EEE6);
            int ix = e == 0 ? x0 + 1 : x0;
            rect(Part.HEAD, FRONT, ix, 8, ix + 2, 10, iris);
            set(Part.HEAD, FRONT, e == 0 ? ix + 1 : ix, 8, 0xFF141418);  // the pupil, toward the nose
            if (look.female) {
                // lashes along the lid, flicked out at the outer corner
                rect(Part.HEAD, FRONT, x0, 7, x0 + 3, 8, 0xFF1E1A1A);
                set(Part.HEAD, FRONT, e == 0 ? x0 - 1 : x0 + 3, 7, 0xFF1E1A1A);
                set(Part.HEAD, FRONT, e == 0 ? x0 - 1 : x0 + 3, 6, mix(skin, 0xFF1E1A1A, 0.6f));
            }
        }
        rect(Part.HEAD, FRONT, 7, 8, 9, 11, shade(skin, 0.95f));   // the nose's bridge and tip
        rect(Part.HEAD, FRONT, 6, 11, 10, 12, shade(skin, 0.82f));
        set(Part.HEAD, FRONT, 6, 11, shade(skin, 0.7f)); set(Part.HEAD, FRONT, 9, 11, shade(skin, 0.7f));
        int lip = mix(skin, look.female ? 0xFFB04050 : 0xFF8A4038, look.female ? 0.5f : 0.38f);
        rect(Part.HEAD, FRONT, 6, 13, 10, 14, lip);
        rect(Part.HEAD, FRONT, 6, 14, 10, 15, shade(skin, 0.9f));
        if (look.female) { rect(Part.HEAD, FRONT, 2, 11, 4, 13, mix(skin, 0xFFE07080, 0.16f)); rect(Part.HEAD, FRONT, 12, 11, 14, 13, mix(skin, 0xFFE07080, 0.16f)); }
        darken(Part.HEAD, FRONT, 0, 15, 16, 16, 0.9f);              // under the jaw
        if (look.female) {                                           // a softer, narrower jaw: its corners fall into shadow
            darken(Part.HEAD, FRONT, 0, 13, 1, 16, 0.86f); darken(Part.HEAD, FRONT, 15, 13, 16, 16, 0.86f);
            darken(Part.HEAD, FRONT, 1, 15, 2, 16, 0.9f); darken(Part.HEAD, FRONT, 14, 15, 15, 16, 0.9f);
        }
        // stubble, and the shadow a beard's roots leave under its volume
        float st = look.female ? 0 : HairMeshes.stubble(look.beard);
        if (st > 0) {
            int bc = mix(skin, 0xFF000000 | Appearance.hairColor(look.beardColor), st);
            for (int i = 1; i < 15; i++) for (int j = 11; j < 16; j++) {
                if (j < 13 && i > 4 && i < 11) continue;              // the lips and the nose's foot stay clear
                if (((i * 13 + j * 7 + look.beard) % 5) == 0) set(Part.HEAD, FRONT, i, j, shade(bc, 0.92f)); else set(Part.HEAD, FRONT, i, j, bc);
            }
            rect(Part.HEAD, FRONT, 5, 12, 11, 13, mix(skin, 0xFF000000 | Appearance.hairColor(look.beardColor), st * 0.9f));
            for (int f : new int[]{RIGHT, LEFT}) for (int i = 0; i < 16; i++) for (int j = 12; j < 16; j++) if (f == RIGHT ? i >= 9 : i <= 6) set(Part.HEAD, f, i, j, bc);
            rect(Part.HEAD, FRONT, 6, 13, 10, 14, lip);
        }
    }

    // ------------------------------------------------------------------ clothes (GDD 2.1 catalogs, see Outfit)
    private void torso(Appearance look) {
        Outfit.Top t = Outfit.top(look.top);
        Outfit.Legs l = Outfit.legs(look.bottom);
        Part p = Part.TORSO;
        int skin = 0xFF000000 | look.skinRgb(), top = garment(look.topColor), under = companion(top, 1.55f);
        int w = faceW(p, FRONT), cx = w / 2, h = faceH(p, FRONT);
        // the undershirt, where something is worn over it, is a pale companion of the garment
        all(p, t.layer() == Outfit.LAYER_NONE ? top : under, 0.05f);
        if (t.layer() == Outfit.LAYER_UNDERSHIRT || t.layer() == Outfit.LAYER_OPEN_FRONT) {
            // the bodice, vest or waistcoat itself
            for (int f = RIGHT; f <= BACK; f++) {
                int fw = faceW(p, f);
                for (int j = t.neck() == Outfit.NECK_SCOOP ? 6 : 2; j < h; j++) for (int i = 0; i < fw; i++) set(p, f, i, j, grain(top, p, f, i, j, 0.05f));
            }
            cap(p, TOP, under, 0.04f);
            if (t.layer() == Outfit.LAYER_OPEN_FRONT) rect(p, FRONT, cx - 2, 0, cx + 2, h, under);       // open down the front
            if (t.neck() == Outfit.NECK_V) for (int j = 0; j < 10; j++) rect(p, FRONT, cx - 1 - (10 - j) / 3, j, cx + 1 + (10 - j) / 3, j + 1, under);
        }
        if (t.layer() == Outfit.LAYER_OVER_TUNIC) sides(p, 0, h, top, 0.05f);
        // the neckline
        switch (t.neck()) {
            case Outfit.NECK_CREW -> { rect(p, FRONT, cx - 2, 0, cx + 2, 1, skin); rect(p, TOP, cx - 2, faceH(p, TOP) / 2 - 1, cx + 2, faceH(p, TOP), skin); sides(p, 0, 1, shade(top, 0.85f), 0.03f); rect(p, FRONT, cx - 2, 0, cx + 2, 1, skin); }
            case Outfit.NECK_V -> { if (t.layer() == Outfit.LAYER_NONE) for (int j = 0; j < 5; j++) rect(p, FRONT, cx - 2 + j / 2, j, cx + 2 - j / 2, j + 1, skin); rect(p, TOP, cx - 2, faceH(p, TOP) / 2 - 1, cx + 2, faceH(p, TOP), skin); }
            case Outfit.NECK_SCOOP -> { for (int j = 0; j < 4; j++) rect(p, FRONT, 2 + j / 2, j, w - 2 - j / 2, j + 1, skin); rect(p, TOP, 1, 0, faceW(p, TOP) - 1, faceH(p, TOP), skin); }
            case Outfit.NECK_HIGH -> { cap(p, TOP, shade(top, 0.9f), 0.03f); rect(p, TOP, cx - 1, faceH(p, TOP) / 2 - 1, cx + 1, faceH(p, TOP), shade(top, 0.75f)); sides(p, 0, 2, shade(top, 0.92f), 0.03f); }
            case Outfit.NECK_COLLAR -> { for (int j = 0; j < 6; j++) rect(p, FRONT, cx - 1 - j / 3, j, cx + 1 + j / 3, j + 1, skin); rect(p, FRONT, cx - 4, 0, cx - 2, 3, companion(top, 1.2f)); rect(p, FRONT, cx + 2, 0, cx + 4, 3, companion(top, 1.2f)); rect(p, TOP, cx - 2, faceH(p, TOP) / 2 - 1, cx + 2, faceH(p, TOP), skin); }
            case Outfit.NECK_WRAP -> {
                for (int j = 0; j < 12; j++) { set(p, FRONT, Math.min(w - 1, cx - 3 + j), j, shade(top, 0.78f)); set(p, FRONT, Math.min(w - 1, cx - 2 + j), j, shade(top, 0.86f)); }
                for (int j = 0; j < 4; j++) rect(p, FRONT, cx - 2, j, cx - 2 + 4 - j, j + 1, skin);
                rect(p, TOP, cx - 2, faceH(p, TOP) / 2 - 1, cx + 2, faceH(p, TOP), skin);
            }
            case Outfit.NECK_ONE_SHOULDER -> {
                // bare on the figure's left shoulder, the drape running across from the right
                int tw = faceW(p, TOP);
                rect(p, TOP, tw / 2, 0, tw, faceH(p, TOP), skin);
                for (int j = 0; j < 8; j++) rect(p, FRONT, cx + 1 + j / 2, j, w, j + 1, skin);
                for (int j = 0; j < 8; j++) rect(p, BACK, 0, j, Math.max(0, cx - 1 - j / 2), j + 1, skin);
                rect(p, LEFT, 0, 0, faceW(p, LEFT), 4, skin);
            }
            default -> { }
        }
        // the shape under the cloth: fitted garments follow the body, loose ones fold
        if (t.fitted()) { darken(p, FRONT, 0, 0, 1, h, 0.86f); darken(p, FRONT, w - 1, 0, w, h, 0.86f); darken(p, FRONT, 1, 16, w - 1, 17, 0.92f); }
        else { for (int j = 4; j < h; j += 5) darken(p, FRONT, 1 + (j % 3), j, 2 + (j % 3), j + 3, 0.92f); }
        if (look.bodyType == Appearance.HEAVY) { darken(p, FRONT, 1, 14, w - 1, 15, 0.9f); darken(p, RIGHT, 0, 0, 99, h, 0.95f); darken(p, LEFT, 0, 0, 99, h, 0.95f); }
        if (look.bodyType == Appearance.MASCULINE && !look.female) { darken(p, FRONT, 1, 7, cx - 1, 8, 0.9f); darken(p, FRONT, cx + 1, 7, w - 1, 8, 0.9f); }
        if (look.female) { darken(p, FRONT, 1, 8, cx - 1, 9, 0.86f); darken(p, FRONT, cx + 1, 8, w - 1, 9, 0.86f); darken(p, FRONT, 1, 5, w - 1, 6, 1.07f); darken(p, FRONT, 0, 12, w, 15, 0.95f); }
        darken(p, BACK, cx - 1, 2, cx + 1, 18, 0.94f);              // the spine's crease
        // details
        switch (t.detail()) {
            case Outfit.T_LACES -> { for (int j = 6; j < 20; j += 2) { set(p, FRONT, cx - 1, j, 0xFFE8DCC0); set(p, FRONT, cx, j + 1, 0xFFE8DCC0); } darken(p, FRONT, cx - 1, 5, cx + 1, 21, 0.75f); for (int j = 6; j < 20; j += 2) { set(p, FRONT, cx - 1, j, 0xFFE8DCC0); set(p, FRONT, cx, j + 1, 0xFFE8DCC0); } }
            case Outfit.T_BUTTONS -> { rect(p, FRONT, cx - 1, 3, cx + 1, 22, shade(top, 0.85f)); for (int j = 5; j < 22; j += 4) set(p, FRONT, cx, j, 0xFFD8C890); }
            case Outfit.T_SASH -> { sides(p, 17, 20, companion(top, 0.62f), 0.04f); rect(p, FRONT, 1, 18, 4, 23, companion(top, 0.62f)); }
            default -> { }
        }
        // what holds it at the waist
        boolean skirt = Outfit.skirtLength(l) > 0;
        if (t.belt()) { sides(p, 19, 21, 0xFF4A3420, 0.06f); rect(p, FRONT, cx - 1, 19, cx + 1, 21, 0xFFC8A040); }
        else if (t.hem() == Outfit.HEM_WAIST) {
            int legsCol = garment(look.bottomColor);
            if (l.kind() == Outfit.L_TROUSERS || l.kind() == Outfit.L_SHORTS || l.kind() == Outfit.L_CROPPED || l.kind() == Outfit.L_BREECHES || l.kind() == Outfit.L_DIVIDED) {
                sides(p, 22, 24, 0xFF4A3420, 0.06f); rect(p, FRONT, cx - 1, 22, cx + 1, 24, 0xFFC8A040);
                if (l.detail() == Outfit.D_DRAWSTRING) { sides(p, 21, 24, shade(legsCol, 0.9f), 0.05f); set(p, FRONT, cx - 1, 23, 0xFFEDE6D2); set(p, FRONT, cx, 23, 0xFFEDE6D2); set(p, FRONT, cx - 1, 24 - 0, 0xFFEDE6D2); }
            } else sides(p, 22, 24, skirt ? legsCol : shade(legsCol, 0.9f), 0.05f);
        }
        // the hood's fall down the back
        if (t.detail() == Outfit.T_HOOD) { rect(p, BACK, 1, 0, w - 1, 6, shade(top, 0.82f)); rect(p, BACK, 2, 6, w - 2, 7, shade(top, 0.7f)); }
        // loose garments stand off the body: their cloth is the outer layer too (armor is painted over it)
        if (t.loose() > 0 && t.layer() != Outfit.LAYER_UNDERSHIRT) {
            Part o = Part.JACKET;
            for (int f = RIGHT; f <= BACK; f++) { int[] r = face(o, f); for (int j = 2; j < r[3]; j++) for (int i = 0; i < r[2]; i++) set(o, f, i, j, get(p, f, i, j)); }
            if (t.neck() != Outfit.NECK_ONE_SHOULDER) cap(o, TOP, top, 0.04f);
            rect(o, TOP, cx - 3, faceH(o, TOP) / 2 - 2, cx + 3, faceH(o, TOP), 0);
            rect(o, FRONT, cx - 3, 0, cx + 3, 4, 0);
        }
    }

    private void arm(Appearance look, Part p, boolean right) {
        Outfit.Top t = Outfit.top(look.top);
        int skin = 0xFF000000 | look.skinRgb(), top = garment(look.topColor), under = companion(top, 1.55f);
        all(p, skin, 0.025f);
        // the sleeve: over an undershirt the undershirt's; else the garment's
        int sleeveCol = t.layer() == Outfit.LAYER_UNDERSHIRT || t.layer() == Outfit.LAYER_OPEN_FRONT ? under : t.layer() == Outfit.LAYER_OVER_TUNIC ? companion(top, 0.7f) : top;
        int sleeve = t.sleeve();
        if (t.layer() == Outfit.LAYER_OVER_TUNIC) sleeve = Outfit.SLEEVE_LONG;
        boolean bare = t.neck() == Outfit.NECK_ONE_SHOULDER && !right;
        int end = bare ? 0 : switch (sleeve) { case Outfit.SLEEVE_SHORT -> 8; case Outfit.SLEEVE_ELBOW -> 13; case Outfit.SLEEVE_LONG -> 20; default -> 0; };
        if (end > 0) { cap(p, TOP, sleeveCol, 0.05f); sides(p, 0, end, sleeveCol, 0.05f); sides(p, end - 1, end, shade(sleeveCol, 0.82f), 0.03f); }
        else if (!bare) { cap(p, TOP, top, 0.05f); sides(p, 0, 2, top, 0.05f); }
        if (t.layer() == Outfit.LAYER_OVER_TUNIC) { cap(p, TOP, top, 0.05f); sides(p, 0, 8, top, 0.05f); sides(p, 7, 8, shade(top, 0.8f), 0.03f); }
        if (t.detail() == Outfit.T_ROLLED && end > 0) { sides(p, end - 3, end, companion(sleeveCol, 1.25f), 0.03f); sides(p, end - 1, end, shade(sleeveCol, 0.85f), 0.03f); }
        if (t.detail() == Outfit.T_BUTTONS && end >= 20) sides(p, 17, 20, shade(sleeveCol, 0.88f), 0.03f);   // cuffs
        // the hand: the last pixels, knuckles and a thumb toward the front
        cap(p, BOTTOM, shade(skin, 0.88f), 0.02f);
        darken(p, FRONT, 0, 22, 99, 23, 0.86f);
        darken(p, right ? LEFT : RIGHT, 0, 20, 99, 24, 0.94f);
        // a loose sleeve stands off the arm
        if (t.loose() > 0 && end > 0) {
            Part o = right ? Part.R_SLEEVE : Part.L_SLEEVE;
            for (int f = RIGHT; f <= BACK; f++) { int[] r = face(o, f); for (int j = 0; j < Math.min(end, r[3]); j++) for (int i = 0; i < r[2]; i++) set(o, f, i, j, get(p, f, i, j)); }
            cap(o, TOP, sleeveCol, 0.04f);
        }
    }

    private void leg(Appearance look, Part p, boolean right) {
        Outfit.Legs l = Outfit.legs(look.bottom);
        Outfit.Shoes sh = Outfit.shoes(look.shoes);
        int skin = 0xFF000000 | look.skinRgb(), cloth = garment(look.bottomColor), shoe = garment(look.shoesColor);
        int legsW = faceW(p, FRONT), cxw = legsW / 2;
        // under a skirt or robe the legs wear plain dark hose (or trousers under a split front or overskirt)
        int clothEnd = switch (l.kind()) {
            case Outfit.L_TROUSERS, Outfit.L_DIVIDED, Outfit.L_LEGGINGS -> 24;
            case Outfit.L_SHORTS -> 13;
            case Outfit.L_CROPPED -> 19;
            case Outfit.L_BREECHES -> 14;
            default -> 0;
        };
        int base = l.kind() == Outfit.L_OVERSKIRT || l.detail() == Outfit.D_SPLIT_FRONT ? companion(cloth, 0.55f) : clothEnd == 0 ? shade(cloth, 0.45f) : skin;
        all(p, clothEnd == 0 ? base : skin, 0.03f);
        if (clothEnd == 0) clothEnd = 24;
        int c = clothEnd == 24 && (l.kind() == Outfit.L_OVERSKIRT || l.kind() == Outfit.L_LONG_SKIRT || l.kind() == Outfit.L_PLEATED || l.kind() == Outfit.L_ROBE) ? base : cloth;
        cap(p, TOP, c, 0.05f);
        sides(p, 0, clothEnd, c, 0.05f);
        switch (l.detail()) {
            case Outfit.D_CUFF_ROLL -> { sides(p, clothEnd - 3, clothEnd, companion(cloth, 1.2f), 0.03f); sides(p, clothEnd - 1, clothEnd, shade(cloth, 0.8f), 0.02f); }
            case Outfit.D_SPLIT_HEM -> { int f = right ? RIGHT : LEFT; rect(p, f, cxw - 1, clothEnd - 4, cxw + 1, clothEnd, skin); sides(p, clothEnd - 1, clothEnd, shade(cloth, 0.8f), 0.02f); }
            case Outfit.D_SEAMS -> { int f = right ? RIGHT : LEFT; rect(p, f, cxw - 1, 0, cxw, 24, shade(cloth, 0.8f)); darken(p, FRONT, 0, 11, 99, 13, 0.9f); }
            case Outfit.D_WRAPS -> { for (int j = 10; j < 22; j++) for (int f = RIGHT; f <= BACK; f++) for (int i = 0; i < faceW(p, f); i++) if (((i + j + f * 2) % 4) < 2) set(p, f, i, j, companion(cloth, 1.3f)); }
            case Outfit.D_STRAPS -> { for (int j = 13; j < 22; j += 3) sides(p, j, j + 1, 0xFF4A3420, 0.04f); }
            case Outfit.D_PANELS -> { rect(p, FRONT, 1, 12, legsW - 1, 22, companion(cloth, 0.7f)); rect(p, FRONT, 1, 12, legsW - 1, 13, companion(cloth, 1.25f)); rect(p, FRONT, cxw - 1, 13, cxw, 22, companion(cloth, 1.15f)); }
            case Outfit.D_REINFORCED -> { rect(p, FRONT, 1, 9, legsW - 1, 15, companion(cloth, 0.72f)); rect(p, FRONT, 2, 10, legsW - 2, 14, companion(cloth, 0.82f)); darken(p, FRONT, 0, 2, 99, 3, 0.85f); }
            default -> { }
        }
        if (l.kind() == Outfit.L_TROUSERS && l.detail() == Outfit.D_NONE) darken(p, FRONT, 0, 11, 99, 12, 0.92f);   // the knee's crease
        if (l.kind() == Outfit.L_BREECHES) { sides(p, 12, 14, shade(cloth, 0.75f), 0.03f); }
        if (l.kind() == Outfit.L_SHORTS) sides(p, 12, 13, shade(cloth, 0.8f), 0.02f);
        if (l.kind() == Outfit.L_DIVIDED) darken(p, right ? LEFT : RIGHT, 0, 0, 99, 24, 0.85f);
        // loose legs stand off the body
        if (l.loose() > 0 && clothEnd > 0 && Outfit.skirtLength(l) == 0) {
            Part o = right ? Part.R_PANTS : Part.L_PANTS;
            // down to where the shoe begins: over low shoes, tucked into tall boots (never hiding them)
            int rows = Math.min(l.kind() == Outfit.L_DIVIDED ? 22 : clothEnd, 24 - (sh.sandal() ? 1 : sh.rows()));
            for (int f = RIGHT; f <= BACK; f++) { int[] r = face(o, f); for (int j = 0; j < Math.min(rows, r[3]); j++) for (int i = 0; i < r[2]; i++) set(o, f, i, j, get(p, f, i, j)); }
            cap(o, TOP, cloth, 0.04f);
        }
        // the shoes
        int rows = sh.rows();
        int top = 24 - rows;
        if (sh.sandal()) {
            // a sole, the foot bare, straps of the style
            sides(p, 23, 24, shade(shoe, 0.6f), 0.03f);
            cap(p, BOTTOM, shade(shoe, 0.5f), 0.04f);
            switch (sh.style()) {
                case Outfit.S_SANDAL_STRAP -> rect(p, FRONT, 0, 21, legsW, 22, shoe);
                case Outfit.S_SANDAL_CROSS -> { for (int i = 0; i < legsW; i++) { set(p, FRONT, i, 20 + (i * 3 / legsW), shoe); set(p, FRONT, legsW - 1 - i, 20 + (i * 3 / legsW), shoe); } }
                case Outfit.S_SANDAL_ANKLE -> { rect(p, FRONT, 0, 21, legsW, 22, shoe); sides(p, 19, 20, shoe, 0.03f); }
                case Outfit.S_SANDAL_WRAP -> { for (int j = top; j < 23; j += 2) for (int f = RIGHT; f <= BACK; f++) for (int i = 0; i < faceW(p, f); i++) if ((i + j) % 3 == 0) set(p, f, i, j, shoe); }
                case Outfit.S_SANDAL_LACED -> { for (int j = top; j < 23; j += 2) sides(p, j, j + 1, shoe, 0.03f); for (int j = top; j < 23; j += 4) for (int i = 0; i < legsW; i += 2) set(p, FRONT, i, j + 1, shoe); }
                default -> { }
            }
        } else {
            sides(p, top, 24, shoe, 0.05f);
            switch (sh.style()) {
                case Outfit.S_LACES -> { for (int j = top + 1; j < 22; j += 2) { set(p, FRONT, cxw - 1, j, companion(shoe, 1.4f)); set(p, FRONT, cxw, j + 1, companion(shoe, 1.4f)); } rect(p, FRONT, cxw - 1, top, cxw + 1, 22, shade(shoe, 0.78f)); for (int j = top + 1; j < 22; j += 2) { set(p, FRONT, cxw - 1, j, companion(shoe, 1.4f)); set(p, FRONT, cxw, j + 1, companion(shoe, 1.4f)); } }
                case Outfit.S_CUFF -> sides(p, top, top + 3, companion(shoe, 1.3f), 0.04f);
                case Outfit.S_FITTED -> { int f = right ? LEFT : RIGHT; rect(p, f, 0, top, faceW(p, f), 22, shade(shoe, 0.82f)); sides(p, top, top + 1, shade(shoe, 0.7f), 0.02f); rect(p, BACK, 1, 21, legsW - 1, 24, shade(shoe, 0.6f)); }
                case Outfit.S_SOFT -> { for (int j = top + 1; j < 22; j += 3) sides(p, j, j + 1, shade(shoe, 0.85f), 0.03f); }
                case Outfit.S_STRAPS -> { for (int j = top + 2; j < 22; j += 3) { sides(p, j, j + 1, shade(shoe, 0.6f), 0.02f); set(p, right ? RIGHT : LEFT, 1, j, 0xFFC8A040); } }
                case Outfit.S_PANELS -> { rect(p, FRONT, 1, top + 1, legsW - 1, 21, companion(shoe, 1.2f)); rect(p, FRONT, 2, top + 2, legsW - 2, 20, shoe); }
                case Outfit.S_PULL_ON -> { rect(p, BACK, cxw - 1, top, cxw + 1, top + 2, companion(shoe, 1.35f)); rect(p, FRONT, cxw - 1, top, cxw + 1, top + 2, companion(shoe, 1.35f)); }
                case Outfit.S_POINTED -> rect(p, FRONT, cxw - 2, 21, cxw + 2, 23, shade(shoe, 1.15f));
                case Outfit.S_ROUNDED -> rect(p, FRONT, 0, 20, legsW, 23, companion(shoe, 0.75f));
                case Outfit.S_SLIP_ON -> sides(p, top, top + 1, companion(shoe, 1.35f), 0.02f);
                case Outfit.S_BUCKLE -> { rect(p, FRONT, cxw - 1, top, cxw + 1, top + 2, 0xFFC8B060); set(p, FRONT, cxw - 1, top, 0xFF8A7030); }
                default -> { }
            }
            sides(p, top, top + 1, shade(shoe, 0.8f), 0.03f);
            sides(p, 23, 24, shade(shoe, 0.5f), 0.04f);
            cap(p, BOTTOM, shade(shoe, 0.45f), 0.04f);
        }
    }

    // ------------------------------------------------------------------ armor: the outer layer
    private void armor(Armor a) {
        if (a.body() != null) {
            int c = 0xFF000000 | a.body().color;
            plate(Part.JACKET, 0, 24, c, a.body().tier);
            plate(Part.R_SLEEVE, 0, 10, c, a.body().tier);
            plate(Part.L_SLEEVE, 0, 10, c, a.body().tier);
            cap(Part.JACKET, TOP, shade(c, 1.08f), 0.04f);
            cap(Part.R_SLEEVE, TOP, shade(c, 1.1f), 0.04f);
            cap(Part.L_SLEEVE, TOP, shade(c, 1.1f), 0.04f);
            int w = faceW(Part.JACKET, FRONT), cx = w / 2;
            rect(Part.JACKET, FRONT, cx - 2, 0, cx + 2, 2, 0);              // the neck stays open
            int th = faceH(Part.JACKET, TOP);
            rect(Part.JACKET, TOP, cx - 2, th / 2 - 1, cx + 2, th, 0);
            if (metal(a.body())) darken(Part.JACKET, FRONT, cx - 1, 2, cx + 1, 20, 1.18f);   // a ridge down the breastplate
            darken(Part.JACKET, FRONT, 0, 22, w, 24, 0.75f);
        }
        if (a.legs() != null) {
            int c = 0xFF000000 | a.legs().color;
            for (Part p : new Part[]{Part.R_PANTS, Part.L_PANTS}) { plate(p, 0, 18, c, a.legs().tier); cap(p, TOP, c, 0.04f); darken(p, FRONT, 0, 10, 99, 14, 1.2f); darken(p, FRONT, 0, 14, 99, 15, 0.7f); }
            if (a.body() == null) plate(Part.JACKET, 19, 24, c, a.legs().tier);   // tassets over the hips
        }
        if (a.feet() != null) {
            int c = 0xFF000000 | a.feet().color;
            for (Part p : new Part[]{Part.R_PANTS, Part.L_PANTS}) { sides(p, 16, 24, c, 0.05f); sides(p, 16, 17, shade(c, 0.75f), 0.02f); sides(p, 23, 24, shade(c, 0.5f), 0.02f); cap(p, BOTTOM, shade(c, 0.45f), 0.03f); }
        }
        if (a.hands() != null) {
            int c = 0xFF000000 | a.hands().color;
            for (Part p : new Part[]{Part.R_SLEEVE, Part.L_SLEEVE}) { sides(p, 17, 24, c, 0.05f); sides(p, 17, 18, shade(c, 0.75f), 0.02f); cap(p, BOTTOM, shade(c, 0.85f), 0.03f); }
        }
        if (a.head() != null) {
            int c = 0xFF000000 | a.head().color;
            boolean metal = metal(a.head());
            int rows = metal ? 16 : 7;                                         // a full helm, or a cap
            cap(Part.HAT, TOP, shade(c, 1.08f), 0.05f);
            sides(Part.HAT, 0, rows, c, 0.05f);
            if (metal) {
                rect(Part.HAT, FRONT, 2, 6, 14, 16, 0);                         // the face opening
                rect(Part.HAT, FRONT, 7, 5, 9, 12, shade(c, 1.12f));           // the nasal
                for (int f = RIGHT; f <= BACK; f++) for (int i = 1; i < faceW(Part.HAT, f); i += 4) set(Part.HAT, f, i, 2, shade(c, 1.35f));   // rivets
                sides(Part.HAT, 4, 5, shade(c, 0.72f), 0.02f);
                rect(Part.HAT, FRONT, 2, 4, 14, 5, shade(c, 0.72f));
            } else {
                sides(Part.HAT, rows - 1, rows, shade(c, 0.75f), 0.02f);
            }
        }
    }

    private static boolean metal(ItemDef d) { return d.tier != null && (d.tier.metal() || d.tier == Tier.CHAINMAIL); }

    /** Armor over rows j0..j1 of a part: leather is stitched, chainmail is ringed, plate is banded and catches the light. */
    private void plate(Part p, int j0, int j1, int c, Tier tier) {
        boolean chain = tier == Tier.CHAINMAIL, metal = tier != null && tier.metal();
        for (int f = RIGHT; f <= BACK; f++) {
            int w = faceW(p, f);
            for (int j = j0; j < Math.min(j1, faceH(p, f)); j++) for (int i = 0; i < w; i++) {
                int col;
                if (chain) col = shade(c, ((i + j) & 1) == 0 ? 1.12f : 0.82f);
                else if (metal) { int band = (j - j0) % 6; col = band == 0 ? shade(c, 0.68f) : band == 1 ? shade(c, 1.22f) : grain(c, p, f, i, j, 0.03f); }
                else { col = grain(c, p, f, i, j, 0.07f); if (i == 0 || i == w - 1 || j == j0 || j == j1 - 1) col = shade(c, 0.78f); }
                set(p, f, i, j, col);
            }
        }
    }
}
