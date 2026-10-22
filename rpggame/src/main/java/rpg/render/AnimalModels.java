package rpg.render;

import astratto.render.Tex;
import astratto.voxel.VoxModel;
import java.util.HashMap;
import java.util.Map;
import rpg.animal.Species;

/**
 * The fourteen animals as posable voxel figures (GDD 3.9, gamefixes: a baby is the same model at half scale). Each is
 * built from parts - body, head (with its ears, horns, comb or bill), four legs (two for a bird, with a hopper's long
 * hind legs), a tail, a bird's wings - each its own voxel model with a pivot where it joins, at the same voxel size as
 * people (1.8 m / 32). Coats are painted voxel by voxel: a cow's patches, a fox's white chest and black socks, a
 * mallard's green head, a tabby's stripes; variants change the coat, never the species.
 *
 * <p>Model axes: x to the animal's right, y up, -z forward. All lengths in voxels.
 */
public final class AnimalModels {
    private AnimalModels() { }

    /** One voxel, in metres (the figure's scale; the meshes are built at {@link ItemModels#VOX} and scaled up). */
    public static final float PX = 1.8f / 32f;

    /** A built figure: its parts and where they join the body (voxels, from the body's centre). */
    public static final class Figure {
        public ItemModels.Model body, head, tail, legFront, legBack, wing;
        /** Body size (W across, H tall, L long) and where its centre stands above the ground. */
        public float bw, bh, bl, bodyY;
        public float legH, legW;
        /** Joints: front legs (x, y, z), back legs, the neck (where the head hinges), the tail. */
        public float[] legF, legB, neck, tailAt, wingAt;
        public boolean bird, hopper;
    }

    private static final Map<String, Figure> CACHE = new HashMap<>();

    public static synchronized Figure of(Species s, int variant) {
        return CACHE.computeIfAbsent(s + ":" + coat(s, variant), k -> build(s, coat(s, variant)));
    }

    /** How many coats a species has (variants beyond wrap round). */
    static int coat(Species s, int variant) {
        int n = switch (s) { case COW, DOG, CAT, HORSE, CHICKEN -> 3; case PIG, SHEEP, DUCK -> 2; default -> 1; };
        return Math.floorMod(variant, n);
    }

    static int c(int rgb) { return 0xFF000000 | rgb; }
    /** A hair-by-hair variation on a colour, fixed per voxel. */
    static int fur(int rgb, int x, int y, int z, float amount) {
        int h = (x * 73856093) ^ (y * 19349663) ^ (z * 83492791);
        float k = 1 + ((h >>> 8 & 255) / 255f - 0.5f) * amount;
        return c(Tex.shade(rgb, k) & 0xFFFFFF);
    }

    /** A coat: a colour per voxel of a part (which part, where in it, the part's size). */
    interface Coat { int at(String part, int x, int y, int z, int sx, int sy, int sz); }

    // ------------------------------------------------------------------ building
    private static Figure build(Species s, int v) {
        return switch (s) {
            case COW -> quad(s, v, 14, 12, 22, 10, 4, 8, 8, 8, 3, cowCoat(v));
            case SHEEP -> quad(s, v, 14, 12, 18, 8, 3, 5, 6, 6, 2, sheepCoat(v));
            case PIG -> quad(s, v, 12, 10, 18, 5, 3, 7, 7, 6, 2, pigCoat(v));
            case HORSE -> quad(s, v, 11, 11, 26, 15, 4, 5, 6, 10, 0, horseCoat(v));
            case DOG -> quad(s, v, 6, 7, 13, 7, 2, 5, 5, 5, 3, dogCoat(v));
            case CAT -> quad(s, v, 5, 5, 10, 5, 2, 4, 4, 3, 1, catCoat(v));
            case FOX -> quad(s, v, 5, 6, 11, 6, 2, 5, 4, 4, 3, foxCoat());
            case COYOTE -> quad(s, v, 6, 7, 13, 8, 2, 5, 5, 5, 4, coyoteCoat());
            case WOLF -> quad(s, v, 7, 8, 15, 8, 3, 6, 6, 6, 4, wolfCoat());
            case MOUNTAIN_LION -> quad(s, v, 7, 7, 16, 7, 3, 6, 5, 5, 2, lionCoat());
            case BROWN_BEAR -> quad(s, v, 16, 13, 22, 8, 6, 9, 8, 8, 3, bearCoat());
            case RABBIT -> hopper(s, v);
            case CHICKEN, DUCK -> bird(s, v);
        };
    }

    /**
     * A four-legged figure: a body (rounded at the edges), legs under its corners, a head on the front (a snout of so
     * many voxels, the species' ears and horns), a tail behind.
     */
    private static Figure quad(Species s, int v, int bw, int bh, int bl, int legH, int legW, int hw, int hh, int hl, int snout, Coat coat) {
        Figure f = new Figure();
        f.bw = bw; f.bh = bh; f.bl = bl; f.legH = legH; f.legW = legW;
        f.bodyY = legH + bh / 2f - (s == Species.SHEEP ? 1.5f : 1);
        // body: a box with its long edges rounded off (fluffier for a sheep, a hump for a bear)
        VoxModel b = new VoxModel(bw, bh, bl);
        float round = s == Species.SHEEP ? 3.2f : s == Species.BROWN_BEAR ? 3f : Math.min(bw, bh) * 0.28f;
        for (int x = 0; x < bw; x++) for (int y = 0; y < bh; y++) for (int z = 0; z < bl; z++) {
            float ex = Math.max(0, Math.abs(x + 0.5f - bw / 2f) - (bw / 2f - round)), ey = Math.max(0, Math.abs(y + 0.5f - bh / 2f) - (bh / 2f - round));
            float ez = Math.max(0, Math.abs(z + 0.5f - bl / 2f) - (bl / 2f - round * 0.8f));
            if (ex * ex + ey * ey + ez * ez * 0.6f > round * round) continue;
            if (s == Species.HORSE || s == Species.MOUNTAIN_LION || s == Species.DOG || s == Species.WOLF || s == Species.COYOTE || s == Species.FOX || s == Species.CAT) {
                // a tucked waist: the belly rises toward the hind legs
                float t = (z + 0.5f) / bl;
                if (y < bh * 0.28f * Math.max(0, t - 0.45f) * 2.2f) continue;
            }
            b.set(x, y, z, coat.at("body", x, y, z, bw, bh, bl));
        }
        if (s == Species.BROWN_BEAR) for (int x = 3; x < bw - 3; x++) for (int z = 3; z < 9; z++) b.set(x, bh - 1, z, coat.at("body", x, bh - 1, z, bw, bh, bl));
        f.body = new ItemModels.Model(b, bw / 2f, bh / 2f, bl / 2f);
        // legs: a column, a darker hoof or paw at the foot
        f.legFront = new ItemModels.Model(leg(s, legH, legW, coat, true), legW / 2f, legH, legW / 2f);
        f.legBack = new ItemModels.Model(leg(s, legH, legW, coat, false), legW / 2f, legH, legW / 2f);
        float inX = bw / 2f - legW / 2f - (s == Species.BROWN_BEAR ? 1 : 0.5f);
        f.legF = new float[]{inX, -bh / 2f + 2, -bl / 2f + legW / 2f + 1.5f};
        f.legB = new float[]{inX, -bh / 2f + 2, bl / 2f - legW / 2f - 1.5f};
        // the head, on its neck
        f.head = head(s, v, hw, hh, hl, snout, coat);
        f.neck = s == Species.HORSE ? new float[]{0, bh / 2f + 1, -bl / 2f + 2} : new float[]{0, bh / 2f - hh * 0.35f + (s == Species.PIG ? -1 : 1), -bl / 2f + 1};
        f.tail = tail(s, coat);
        f.tailAt = new float[]{0, bh / 2f - (s == Species.PIG ? 2 : 1.5f), bl / 2f - 0.5f};
        return f;
    }

    private static VoxModel leg(Species s, int legH, int legW, Coat coat, boolean front) {
        VoxModel l = new VoxModel(legW, legH, legW);
        boolean hoof = s == Species.COW || s == Species.SHEEP || s == Species.PIG || s == Species.HORSE;
        for (int y = 0; y < legH; y++) for (int x = 0; x < legW; x++) for (int z = 0; z < legW; z++) {
            int col = coat.at(front ? "legF" : "legB", x, y, z, legW, legH, legW);
            if (y == 0) col = hoof ? c(s == Species.PIG ? 0x6A4A40 : 0x2A2420) : c(Tex.shade(col & 0xFFFFFF, 0.75f));
            if (hoof && y == 1) col = c(s == Species.HORSE ? 0x3A3028 : Tex.shade(col & 0xFFFFFF, 0.85f));
            l.set(x, y, z, col);
        }
        // the haunch is thicker at the top for a big animal
        return l;
    }

    /** The head: a block with a snout in front, eyes, a nose, and the species' ears, horns, mane. Pivot: where the neck joins (its back-bottom). */
    private static ItemModels.Model head(Species s, int v, int hw, int hh, int hl, int snout, Coat coat) {
        boolean horse = s == Species.HORSE;
        int neckL = horse ? 9 : 0;
        int sx = hw + 4, sy = hh + (horse ? 12 : 6), sz = hl + snout + neckL + 2;
        VoxModel m = new VoxModel(sx, sy, sz);
        int ox = 2, oy = horse ? 9 : 1, oz = 1;   // where the skull's box starts (back-bottom-left)
        int back = oz + hl + snout + neckL;      // z of the back of the skull (+z is backward)
        // the skull
        for (int x = 0; x < hw; x++) for (int y = 0; y < hh; y++) for (int z = 0; z < hl; z++) {
            boolean corner = (x == 0 || x == hw - 1) && (y == hh - 1 || y == 0) && (z == 0 || z == hl - 1);
            if (corner && hw > 4) continue;
            m.set(ox + x, oy + y, oz + snout + z, coat.at("head", x, y, z, hw, hh, hl));
        }
        // the snout (narrower and lower), its nose
        int snw = Math.max(2, hw - (s == Species.PIG ? 1 : s == Species.COW || s == Species.HORSE || s == Species.BROWN_BEAR ? 2 : 3)), snh = Math.max(2, hh - (s == Species.PIG ? 2 : hh / 2));
        int sx0 = ox + (hw - snw) / 2;
        for (int x = 0; x < snw; x++) for (int y = 0; y < snh; y++) for (int z = 0; z < snout; z++)
            m.set(sx0 + x, oy + y, oz + z, coat.at("snout", x, y, z, snw, snh, snout));
        int nose = switch (s) { case PIG -> 0xE89A98; case COW -> 0xE4A8A0; case SHEEP -> 0x2A2420; case HORSE -> 0x3A3430; case CAT -> 0xE08890; default -> 0x1A1614; };
        if (snout > 0) {
            for (int x = 0; x < snw; x++) for (int y = snh - (s == Species.PIG ? snh : 1); y < snh; y++) if (s == Species.PIG || x > 0 && x < snw - 1 || snw <= 2) m.set(sx0 + x, oy + y, oz, c(nose));
            if (s == Species.PIG) { m.set(sx0 + 1, oy + snh / 2, oz, c(0x6A3A3A)); m.set(sx0 + snw - 2, oy + snh / 2, oz, c(0x6A3A3A)); }
        } else m.set(ox + hw / 2, oy + 1, oz, c(nose));
        // eyes: dark, a glint
        int ey = oy + hh - Math.max(2, hh / 3);
        for (int side : new int[]{0, hw - 1}) {
            m.set(ox + side, ey, oz + snout + 1, c(s == Species.CAT || s == Species.MOUNTAIN_LION ? 0x7A8A2A : s == Species.WOLF ? 0x8A7A30 : 0x141010));
            if (hl > 4) m.set(ox + side, ey + 1, oz + snout + 1, c(Tex.shade(coat.at("head", 0, hh - 1, 1, hw, hh, hl) & 0xFFFFFF, 0.8f)));
        }
        // ears
        int earC = coat.at("ear", 0, 0, 0, 1, 1, 1);
        int top = oy + hh, ez = oz + snout + hl - 2;
        switch (s) {
            case FOX, COYOTE, WOLF, CAT -> {
                int eh = s == Species.CAT ? 2 : s == Species.FOX ? 3 : 3;
                for (int side : new int[]{ox, ox + hw - 2}) for (int y = 0; y < eh; y++) for (int x = 0; x < 2 - (y == eh - 1 ? 1 : 0); x++)
                    m.set(side + x + (side == ox ? 0 : (y == eh - 1 ? 1 : 0)), top + y, ez, y == 0 ? earC : c(Tex.shade(earC & 0xFFFFFF, 0.8f)));
            }
            case DOG -> {
                boolean floppy = v == 1;
                for (int side : new int[]{ox - 1, ox + hw}) for (int y = 0; y < 3; y++) {
                    if (floppy) m.set(side, top - 1 - y, ez, earC); else m.set(side + (side < ox ? 1 : -1), top + y, ez, earC);
                }
            }
            case MOUNTAIN_LION, BROWN_BEAR -> { for (int side : new int[]{ox, ox + hw - 2}) m.box(side, top, ez, side + 1, top, ez, earC); }
            case HORSE -> {
                for (int side : new int[]{ox, ox + hw - 1}) { m.set(side, top, ez, earC); m.set(side, top + 1, ez, earC); }
                // the mane down the neck
                int mane = coat.at("mane", 0, 0, 0, 1, 1, 1);
                for (int z = 0; z < neckL + 3; z++) for (int y = 0; y < 2; y++) m.set(ox + hw / 2, top - z * 9 / (neckL + 3) - y + 1, ez + z, mane);
            }
            case COW, SHEEP, PIG -> {
                for (int side : new int[]{ox - 1, ox + hw}) { m.set(side, top - 2, ez, earC); m.set(side, top - 3, ez - (s == Species.PIG ? 1 : 0), earC); }
                if (s == Species.COW) for (int side : new int[]{ox, ox + hw - 1}) { m.set(side, top, ez - 1, c(0xE8E0C8)); m.set(side + (side == ox ? -1 : 1), top + 1, ez - 1, c(0xD8D0B8)); }
                if (s == Species.SHEEP) for (int x = 0; x < hw; x++) for (int z = 0; z < 3; z++) m.set(ox + x, top, ez - z + 1, coat.at("body", x, 0, z, hw, 1, 3));   // the wool cap
            }
            default -> { }
        }
        if (horse) {
            // the neck: from the skull's back down and back to the shoulders
            int nc = coat.at("head", 0, 0, 0, hw, hh, hl);
            for (int z = 0; z < neckL + 1; z++) {
                int yBase = oy - z * 9 / (neckL + 1);
                for (int x = 0; x < hw; x++) for (int y = 0; y < hh; y++) m.set(ox + x, yBase + y, oz + snout + hl + z - 1, fur(nc & 0xFFFFFF, x, y, z, 0.08f));
            }
        }
        // pivot: the neck's base (the back of the head, low) - for a horse, the bottom of its neck
        float pz = horse ? oz + snout + hl + neckL - 0.5f : back - 0.5f, py = horse ? 1.5f : oy + hh * 0.35f;
        return new ItemModels.Model(m, ox + hw / 2f, py, pz);
    }

    private static ItemModels.Model tail(Species s, Coat coat) {
        int tc = coat.at("tail", 0, 0, 0, 1, 1, 1), tip = coat.at("tip", 0, 0, 0, 1, 1, 1);
        VoxModel m;
        switch (s) {
            case FOX, WOLF, COYOTE -> {
                int len = s == Species.FOX ? 9 : 9, w = s == Species.FOX ? 4 : 3;
                m = new VoxModel(w + 1, 4, len + 1);
                for (int z = 0; z < len; z++) { float r = (float) Math.sin(Math.PI * (z + 1) / (len + 1)) * w / 2f + 0.6f; for (int x = 0; x <= w; x++) for (int y = 0; y < 4; y++) if (Math.hypot(x - w / 2f, y - 1.5f) <= r) m.set(x, y, z, z >= len - 2 ? tip : fur(tc & 0xFFFFFF, x, y, z, 0.15f)); }
                return new ItemModels.Model(m, w / 2f + 0.5f, 2, 0.5f);
            }
            case CAT, MOUNTAIN_LION -> {
                int len = s == Species.CAT ? 9 : 12;
                m = new VoxModel(2, 2, len);
                for (int z = 0; z < len; z++) m.box(0, 0, z, s == Species.CAT ? 0 : 1, s == Species.CAT ? 0 : 1, z, z >= len - 2 ? tip : fur(tc & 0xFFFFFF, 0, 0, z, 0.15f));
                return new ItemModels.Model(m, 0.5f, 0.5f, 0.5f);
            }
            case DOG -> { m = new VoxModel(2, 2, 6); m.box(0, 0, 0, 1, 1, 5, tc); m.box(0, 0, 5, 1, 1, 5, tip); return new ItemModels.Model(m, 1, 1, 0.5f); }
            case HORSE -> {
                m = new VoxModel(3, 3, 12);
                for (int z = 0; z < 12; z++) m.box(0, 0, z, 2, 2, z, fur(tip & 0xFFFFFF, 0, 0, z, 0.2f));
                return new ItemModels.Model(m, 1.5f, 1.5f, 0.5f);
            }
            case COW -> { m = new VoxModel(1, 1, 11); m.box(0, 0, 0, 0, 0, 8, tc); m = new VoxModel(3, 3, 11); m.box(1, 1, 0, 1, 1, 8, tc); m.box(0, 0, 8, 2, 2, 10, tip); return new ItemModels.Model(m, 1.5f, 1.5f, 0.5f); }
            case PIG -> {
                m = new VoxModel(3, 3, 4);
                int[][] curl = {{1, 1, 0}, {1, 2, 1}, {2, 2, 2}, {2, 1, 3}, {1, 0, 3}};
                for (int[] p : curl) m.set(p[0], p[1], p[2], tc);
                return new ItemModels.Model(m, 1.5f, 1, 0.5f);
            }
            case SHEEP -> { m = new VoxModel(4, 4, 4); m.sphere(2, 2, 2, 2, tc); return new ItemModels.Model(m, 2, 2, 1); }
            case BROWN_BEAR -> { m = new VoxModel(3, 3, 3); m.box(0, 0, 0, 2, 2, 2, tc); return new ItemModels.Model(m, 1.5f, 1.5f, 0.5f); }
            default -> { m = new VoxModel(3, 3, 3); m.sphere(1.5f, 1.5f, 1.5f, 1.6f, tc); return new ItemModels.Model(m, 1.5f, 1.5f, 0.5f); }
        }
    }

    // ------------------------------------------------------------------ the rabbit: a hopper
    private static Figure hopper(Species s, int v) {
        Coat coat = (part, x, y, z, sx, sy, sz) -> {
            if (part.equals("tail") || part.equals("tip")) return c(0xF4F0E8);
            if (part.equals("ear")) return c(0xA88A70);
            boolean belly = part.equals("body") && y < 2 || part.equals("snout") && y == 0;
            return belly ? fur(0xE8DCCC, x, y, z, 0.06f) : fur(0x9A7A5C, x, y, z, 0.12f);
        };
        Figure f = new Figure();
        f.hopper = true;
        f.bw = 5; f.bh = 5; f.bl = 7; f.legH = 3; f.legW = 2;
        f.bodyY = 3 + 2.5f - 0.5f;
        VoxModel b = new VoxModel(5, 6, 8);
        b.sphere(2.5f, 3, 4.6f, 3.1f, 1); b.sphere(2.5f, 3.4f, 2.4f, 2.4f, 1);
        for (int x = 0; x < 5; x++) for (int y = 0; y < 6; y++) for (int z = 0; z < 8; z++) if (b.get(x, y, z) != 0) b.set(x, y, z, coat.at("body", x, y, z, 5, 6, 8));
        f.body = new ItemModels.Model(b, 2.5f, 3, 4);
        VoxModel fl = new VoxModel(1, 3, 2); fl.box(0, 0, 0, 0, 2, 1, coat.at("legF", 0, 1, 0, 1, 3, 2));
        f.legFront = new ItemModels.Model(fl, 0.5f, 3, 1);
        VoxModel hl = new VoxModel(2, 3, 5); hl.box(0, 0, 0, 1, 1, 4, coat.at("legB", 0, 2, 0, 2, 3, 5)); hl.box(0, 2, 2, 1, 2, 4, coat.at("legB", 0, 2, 0, 2, 3, 5));
        f.legBack = new ItemModels.Model(hl, 1, 3, 3);
        f.legF = new float[]{1.2f, -2, -2.5f};
        f.legB = new float[]{1.6f, -1.5f, 1.8f};
        VoxModel h = new VoxModel(4, 10, 5);
        h.box(0, 0, 1, 3, 3, 4, 1);
        for (int x = 0; x < 4; x++) for (int y = 0; y < 4; y++) for (int z = 1; z < 5; z++) h.set(x, y, z, coat.at("head", x, y, z, 4, 4, 4));
        h.box(1, 0, 0, 2, 1, 0, coat.at("snout", 0, 1, 0, 2, 2, 1)); h.set(1, 1, 0, c(0xD89090)); h.set(2, 1, 0, c(0xD89090));
        h.set(0, 2, 2, c(0x141010)); h.set(3, 2, 2, c(0x141010));
        for (int y = 4; y < 10; y++) { h.set(0, y, 3, y > 5 ? c(0xD8B0A0) : c(0x9A7A5C)); h.set(3, y, 3, y > 5 ? c(0xD8B0A0) : c(0x9A7A5C)); h.set(0, y, 4, c(0x8A6A4E)); h.set(3, y, 4, c(0x8A6A4E)); }
        f.head = new ItemModels.Model(h, 2, 1, 4.5f);
        f.neck = new float[]{0, 2, -3};
        VoxModel t = new VoxModel(2, 2, 2); t.box(0, 0, 0, 1, 1, 1, c(0xF4F0E8));
        f.tail = new ItemModels.Model(t, 1, 1, 0.5f);
        f.tailAt = new float[]{0, 1, 4};
        return f;
    }

    // ------------------------------------------------------------------ birds
    private static Figure bird(Species s, int v) {
        boolean duck = s == Species.DUCK;
        Figure f = new Figure();
        f.bird = true;
        int body, belly, wingC, headC, beak = duck ? 0xE8A030 : 0xE8C040, legC = duck ? 0xE88A30 : 0xE8B840;
        if (duck) {
            if (v == 0) { body = 0x8A8A86; belly = 0x7A5A44; wingC = 0x6A6A68; headC = 0x1E5A2E; }       // a mallard drake
            else { body = 0x8A6A4A; belly = 0x9A7A58; wingC = 0x6A503A; headC = 0x7A5A3E; }             // a brown duck
        } else {
            if (v == 0) { body = 0xF0ECE4; belly = 0xE8E2D8; wingC = 0xE2DCD0; headC = 0xF0ECE4; }       // white
            else if (v == 1) { body = 0xA8642E; belly = 0x8A4A22; wingC = 0x7A4220; headC = 0xC07838; }  // red-brown
            else { body = 0x2A2A2C; belly = 0x343438; wingC = 0x1E2A28; headC = 0x2A2A2C; }             // black
        }
        f.bw = 5; f.bh = 6; f.bl = duck ? 9 : 7; f.legH = duck ? 3 : 4; f.legW = 1;
        f.bodyY = f.legH + 2.5f;
        int bl = (int) f.bl;
        VoxModel b = new VoxModel(5, 7, bl + 3);
        for (int x = 0; x < 5; x++) for (int y = 0; y < 7; y++) for (int z = 0; z < bl + 3; z++) {
            float dx = (x - 2) / 2.6f, dy = (y - 3) / 3.2f, dz = (z - bl / 2f) / (bl / 2f + 0.4f);
            if (dx * dx + dy * dy + dz * dz > 1) continue;
            b.set(x, y, z, y < 3 ? fur(belly, x, y, z, 0.08f) : fur(body, x, y, z, 0.08f));
        }
        // the tail: a chicken's raised fan, a duck's short flat point (with the drake's curl)
        for (int i = 0; i < (duck ? 2 : 4); i++) for (int x = 1; x < 4; x++) b.set(x, duck ? 4 + i / 2 : 4 + i, bl + (duck ? i : i / 2), fur(duck && v == 0 ? 0x2A2A2A : wingC, x, i, 0, 0.1f));
        if (duck && v == 0) b.set(2, 6, bl + 1, c(0x1A1A1A));
        f.body = new ItemModels.Model(b, 2.5f, 3, bl / 2f);
        // legs: thin, with the foot forward
        VoxModel l = new VoxModel(1, (int) f.legH + 1, 3);
        for (int y = 1; y <= f.legH; y++) l.set(0, y, 1, c(legC));
        l.box(0, 0, 0, 0, 0, 2, c(legC));
        f.legFront = new ItemModels.Model(l, 0.5f, f.legH + 1, 1.5f);
        f.legBack = f.legFront;
        f.legF = new float[]{1, -2.2f, 0.5f};
        f.legB = f.legF;
        // the head on its neck: comb and wattle, or the duck's flat bill
        // a small head (a third of the body's width) on a short neck as thick as the head
        VoxModel h = new VoxModel(3, 8, 7);
        int sk0 = duck ? 2 : 2, sk1 = duck ? 5 : 4;
        for (int y = 0; y < 3; y++) for (int x = 0; x < 3; x++) for (int z = sk1 - 1; z <= sk1; z++)
            h.set(x, y, z, fur(duck && y < 2 ? (v == 0 && y == 1 ? 0xF0F0EC : body) : headC, x, y, z, 0.06f));   // the neck (a drake's white collar)
        for (int x = 0; x < 3; x++) for (int y = 3; y < 6; y++) for (int z = sk0; z <= sk1; z++) h.set(x, y, z, fur(headC, x, y, z, duck ? 0.12f : 0.05f));
        h.set(0, 4, sk0 + 1, c(0x101010)); h.set(2, 4, sk0 + 1, c(0x101010));
        if (duck) { h.box(0, 3, 0, 2, 3, 1, c(beak)); h.box(1, 4, 0, 1, 4, 1, c(beak)); }
        else {
            h.set(1, 4, 1, c(beak)); h.set(1, 3, 1, c(Tex.shade(beak, 0.85f)));
            for (int z = sk0; z <= sk1; z++) h.set(1, 6 + (z % 2 == 0 ? 0 : 1) - (z == sk1 ? 1 : 0), z, c(0xD02A28));   // the comb
            h.set(1, 2, sk0, c(0xD02A28)); h.set(1, 1, sk0, c(0xC02424));                                              // the wattle
        }
        f.head = new ItemModels.Model(h, 1.5f, 0.5f, sk1 + 0.5f);
        f.neck = new float[]{0, 2, -bl / 2f + 1.2f};
        // wings: a flat panel each side, pivoting at the shoulder
        VoxModel w = new VoxModel(1, 4, bl - 2);
        for (int y = 0; y < 4; y++) for (int z = 0; z < bl - 2; z++) if (y + z / 2 < 6) w.set(0, y, z, fur(z > bl - 5 ? Tex.shade(wingC, 0.8f) : wingC, 0, y, z, 0.1f));
        if (duck && v == 0) for (int z = 3; z < 5; z++) w.set(0, 1, z, c(0x3A4AA8));   // the speculum
        f.wing = new ItemModels.Model(w, 0.5f, 3.5f, 0.5f);
        f.wingAt = new float[]{2.4f, 1.5f, -bl / 2f + 1.5f};
        f.tail = null;
        f.tailAt = new float[]{0, 0, 0};
        return f;
    }

    // ------------------------------------------------------------------ coats
    private static Coat cowCoat(int v) {
        int base = v == 1 ? 0x6A4026 : v == 2 ? 0x2A2420 : 0xF2EEE6, patch = v == 0 ? 0x221E1C : v == 1 ? 0xF0EAE0 : 0xF2EEE6;
        return (part, x, y, z, sx, sy, sz) -> {
            switch (part) {
                case "snout": return y == sy - 1 ? c(0xE4A8A0) : fur(0xE8D8CC, x, y, z, 0.05f);
                case "ear": return c(base == 0xF2EEE6 ? 0x2A2420 : base);
                case "tail": return c(base);
                case "tip": return c(0x2A2420);
                default: { }
            }
            int h = ((x / 4) * 31 + (y / 3) * 17 + (z / 5) * 13 + v * 7) % 7;
            boolean spot = v != 2 && (h == 0 || h == 3) && !(part.equals("legF") || part.equals("legB")) || (part.startsWith("leg") && v == 1 && y < 5);
            if (v == 2 && part.equals("head") && x > 1 && x < sx - 2 && y > 1) spot = true;   // a black cow's white face
            return fur(spot ? patch : base, x, y, z, 0.07f);
        };
    }

    private static Coat sheepCoat(int v) {
        int wool = v == 0 ? 0xEDE8DA : 0x5A4E44, face = 0x2A2420;
        return (part, x, y, z, sx, sy, sz) -> switch (part) {
            case "body" -> fur(wool, x, y, z, 0.18f);
            case "head", "snout", "ear" -> fur(face, x, y, z, 0.1f);
            case "legF", "legB" -> fur(y > sy - 3 ? wool : face, x, y, z, 0.1f);
            default -> c(wool);
        };
    }

    private static Coat pigCoat(int v) {
        int skin = v == 0 ? 0xE8A8A0 : 0x3A3236;
        return (part, x, y, z, sx, sy, sz) -> {
            if (v == 1 && part.equals("body") && z > sz / 3 && z < sz * 2 / 3) return fur(0xE8A8A0, x, y, z, 0.05f);   // a saddleback's band
            return fur(skin, x, y, z, 0.06f);
        };
    }

    private static Coat horseCoat(int v) {
        int coat = v == 0 ? 0x7A4A26 : v == 1 ? 0x2E2420 : 0xC8C0B4, mane = v == 2 ? 0x8A8278 : 0x1E1814;
        return (part, x, y, z, sx, sy, sz) -> switch (part) {
            case "mane", "tip", "tail" -> c(mane);
            case "ear" -> c(Tex.shade(coat, 0.85f));
            case "snout" -> fur(v == 2 ? 0x8A8278 : Tex.shade(coat, 0.8f), x, y, z, 0.05f);
            case "legF", "legB" -> fur(y < 4 && v == 0 ? 0x1E1814 : coat, x, y, z, 0.06f);
            case "head" -> fur(v == 0 && x > 1 && x < sx - 2 && y > sy - 3 ? 0xF0EAE0 : coat, x, y, z, 0.06f);   // a blaze
            default -> fur(coat, x, y, z, 0.06f);
        };
    }

    private static Coat dogCoat(int v) {
        int a = v == 0 ? 0xA06A34 : v == 1 ? 0x2A2420 : 0xE8DCC8, b = v == 0 ? 0xF0E6D4 : v == 1 ? 0xA86A34 : 0xC89A64;
        return (part, x, y, z, sx, sy, sz) -> switch (part) {
            case "snout" -> fur(b, x, y, z, 0.05f);
            case "ear" -> c(Tex.shade(a, 0.8f));
            case "tip" -> c(b);
            case "tail" -> c(a);
            case "body" -> fur(y < sy / 3 || (v == 0 && z < 3 && y < sy - 2) ? b : a, x, y, z, 0.1f);
            case "legF", "legB" -> fur(y < 2 ? b : a, x, y, z, 0.1f);
            default -> fur(a, x, y, z, 0.08f);
        };
    }

    private static Coat catCoat(int v) {
        int a = v == 0 ? 0xD88A40 : v == 1 ? 0x2A2626 : 0x8A847C;
        return (part, x, y, z, sx, sy, sz) -> {
            if (part.equals("ear")) return c(Tex.shade(a, 0.85f));
            if (part.equals("tip")) return c(Tex.shade(a, 0.6f));
            if (part.equals("tail")) return c(a);
            boolean stripe = v != 1 && (z % 3 == 0) && y > sy / 3;
            boolean white = v == 0 && (part.equals("snout") || part.equals("body") && y < 2) || v == 1 && part.equals("snout") && y == 0;
            return fur(white ? 0xF0E8DC : stripe ? Tex.shade(a, 0.68f) : a, x, y, z, 0.08f);
        };
    }

    private static Coat foxCoat() {
        return (part, x, y, z, sx, sy, sz) -> switch (part) {
            case "snout" -> fur(y < sy - 1 ? 0xF4EEE4 : 0xD86A28, x, y, z, 0.05f);
            case "ear" -> c(0x2A1E18);
            case "tip" -> c(0xF6F2EA);
            case "tail" -> c(0xD06A26);
            case "legF", "legB" -> fur(y < sy * 0.6f ? 0x221A16 : 0xC85E22, x, y, z, 0.06f);
            case "body" -> fur(y < 2 && z < sz / 2 ? 0xF4EEE4 : 0xD06A26, x, y, z, 0.12f);
            default -> fur(0xD06A26, x, y, z, 0.1f);
        };
    }

    private static Coat coyoteCoat() {
        return (part, x, y, z, sx, sy, sz) -> switch (part) {
            case "snout" -> fur(0xD8C0A0, x, y, z, 0.06f);
            case "ear" -> c(0x9A7A58);
            case "tip" -> c(0x2A2420);
            case "tail" -> c(0xA08868);
            case "body" -> fur(y < 2 ? 0xE0D0B8 : y > sy - 2 ? 0x7A6A58 : 0xA88C6C, x, y, z, 0.15f);
            default -> fur(0xA88C6C, x, y, z, 0.12f);
        };
    }

    private static Coat wolfCoat() {
        return (part, x, y, z, sx, sy, sz) -> switch (part) {
            case "snout" -> fur(0xD8D4CC, x, y, z, 0.06f);
            case "ear" -> c(0x4A4844);
            case "tip" -> c(0x2A2826);
            case "tail" -> c(0x7A7670);
            case "body" -> fur(y < 2 ? 0xD8D4CC : y > sy - 3 ? 0x4E4C48 : 0x8A8680, x, y, z, 0.16f);
            case "head" -> fur(y < 2 ? 0xD8D4CC : 0x6A6660, x, y, z, 0.12f);
            default -> fur(0x8A8680, x, y, z, 0.12f);
        };
    }

    private static Coat lionCoat() {
        return (part, x, y, z, sx, sy, sz) -> switch (part) {
            case "snout" -> fur(y == 0 ? 0xF0E8DC : 0xE8D8C0, x, y, z, 0.05f);
            case "ear" -> c(0x4A3424);
            case "tip" -> c(0x2A1E18);
            case "tail" -> c(0xB08858);
            case "body" -> fur(y < 2 ? 0xE8DCC8 : 0xB8905E, x, y, z, 0.08f);
            default -> fur(0xB08858, x, y, z, 0.08f);
        };
    }

    private static Coat bearCoat() {
        return (part, x, y, z, sx, sy, sz) -> switch (part) {
            case "snout" -> fur(0x8A6A4A, x, y, z, 0.08f);
            case "ear" -> c(0x3A2818);
            default -> fur(y > sy - 3 && part.equals("body") ? 0x6A4A2C : 0x523620, x, y, z, 0.18f);
        };
    }
}
