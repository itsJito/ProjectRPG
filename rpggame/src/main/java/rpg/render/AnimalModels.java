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
        public ItemModels.Model body, head, headClosed, tail, legFront, legBack, wing;
        /**
         * (0.3.4, gamefixes: horse and Pegasus animations) a long-legged hoofed animal's legs in two: the upper leg (forearm,
         * gaskin) and the lower (cannon, fetlock and hoof), joined at the knee (fore) and the hock (hind), so the joint can
         * fold as the leg swings; how long the upper part is (voxels). Null for the rest.
         */
        public ItemModels.Model legFrontHi, legFrontLo, legBackHi, legBackLo;
        public float kneeAt;
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
        int n = switch (s) { case COW, DOG, CAT, HORSE, CHICKEN, DEER -> 3; case PIG, SHEEP, DUCK -> 2; default -> 1; };
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
            case HORSE -> quad(s, v, 11, 11, 26, 15, 4, 5, 6, 7, 5, horseCoat(v));
            case PEGASUS -> pegasus(v);
            // (0.3.5, gamefixes: deer looked like small horses) a compact, deep-chested body on long slender legs, a
            // short wedge of a head carried high on a thin upright neck
            case DEER -> quad(s, v, 7, 9, 14, 17, 2, 5, 5, 4, 3, deerCoat(v));
            case DOG -> quad(s, v, 6, 7, 13, 7, 2, 5, 5, 5, 3, dogCoat(v));
            case CAT -> quad(s, v, 5, 5, 10, 5, 2, 4, 4, 3, 1, catCoat(v));
            case FOX -> quad(s, v, 5, 6, 11, 6, 2, 5, 4, 4, 3, foxCoat());
            case COYOTE -> quad(s, v, 6, 7, 13, 8, 2, 5, 5, 5, 4, coyoteCoat());
            case WOLF -> quad(s, v, 7, 8, 15, 8, 3, 6, 6, 6, 4, wolfCoat());
            case MOUNTAIN_LION -> quad(s, v, 7, 7, 16, 7, 3, 6, 5, 5, 2, lionCoat());
            // (0.3.9) the Dire Wolf, built on the Barbary lion: broad and deep at the shoulder, a big square head, heavy
            // forelegs, the mane over all its front half
            case DIRE_WOLF -> quad(s, v, 12, 12, 25, 15, 5, 9, 9, 7, 3, direCoat());
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
            if (horseLike(s) || s == Species.MOUNTAIN_LION || s == Species.DIRE_WOLF || s == Species.DOG || s == Species.WOLF || s == Species.COYOTE || s == Species.FOX || s == Species.CAT) {
                // a tucked waist: the belly rises toward the hind legs
                float t = (z + 0.5f) / bl;
                if (y < bh * 0.28f * Math.max(0, t - 0.45f) * 2.2f) continue;
            }
            b.set(x, y, z, coat.at("body", x, y, z, bw, bh, bl));
        }
        if (s == Species.BROWN_BEAR) for (int x = 3; x < bw - 3; x++) for (int z = 3; z < 9; z++) b.set(x, bh - 1, z, coat.at("body", x, bh - 1, z, bw, bh, bl));
        // (0.3.9) the Dire Wolf's mane over its shoulders: a ruff standing out from the front of the body, and a fringe
        // hanging under the chest and down the belly
        if (s == Species.DIRE_WOLF) {
            VoxModel big = new VoxModel(bw + 2, bh + 2, bl);
            for (int x = 0; x < bw; x++) for (int y = 0; y < bh; y++) for (int z = 0; z < bl; z++) { int c0 = b.get(x, y, z); if (c0 != 0) big.set(x + 1, y + 1, z, c0); }
            int maneZ = Math.round(bl * 0.42f);
            for (int z = 0; z < maneZ; z++) for (int x = 0; x < bw + 2; x++) for (int y = 0; y < bh + 2; y++) {
                if (big.get(x, y, z) != 0) continue;
                // a voxel beside the body there: the ruff (thinning toward the back)
                boolean touches = false;
                for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) { int nx = x + d[0], ny = y + d[1]; if (nx > 0 && ny > 0 && nx < bw + 1 && ny < bh + 1 && b.get(nx - 1, ny - 1, z) != 0) touches = true; }
                if (!touches || z > maneZ - 3 && ((x * 7 + y * 3 + z) % 3 == 0)) continue;
                big.set(x, y, z, coat.at("mane", x, y, z, bw + 2, bh + 2, maneZ));
            }
            for (int z = 2; z < bl - 6; z++) for (int x = 2; x < bw; x++) {
                int drop = z < maneZ ? 2 : z < bl * 0.62f ? 1 : 0;
                if (drop > 0 && (x + z) % 4 != 0) for (int y = 0; y < drop; y++) if (big.get(x, y, z) == 0) big.set(x, y, z, coat.at("mane", x, 0, z, bw, 1, bl));
            }
            b = big;
            f.body = new ItemModels.Model(b, (bw + 2) / 2f, (bh + 2) / 2f, bl / 2f);
        }
        if (s != Species.DIRE_WOLF) f.body = new ItemModels.Model(b, bw / 2f, bh / 2f, bl / 2f);
        // legs: a column, a darker hoof or paw at the foot
        VoxModel lf = leg(s, legH, legW, coat, true), lb = leg(s, legH, legW, coat, false);
        f.legFront = new ItemModels.Model(lf, legW / 2f, legH, legW / 2f);
        f.legBack = new ItemModels.Model(lb, legW / 2f, legH, legW / 2f);
        if (horseLike(s)) {
            int knee = Math.round(legH * 0.52f);           // the joint a little above the middle: a long cannon below it
            f.kneeAt = legH - knee;
            f.legFrontHi = new ItemModels.Model(slice(lf, knee, legH), legW / 2f, legH - knee, legW / 2f);
            f.legFrontLo = new ItemModels.Model(slice(lf, 0, knee), legW / 2f, knee, legW / 2f);
            f.legBackHi = new ItemModels.Model(slice(lb, knee, legH), legW / 2f, legH - knee, legW / 2f);
            f.legBackLo = new ItemModels.Model(slice(lb, 0, knee), legW / 2f, knee, legW / 2f);
        }
        float inX = bw / 2f - legW / 2f - (s == Species.BROWN_BEAR ? 1 : 0.5f);
        f.legF = new float[]{inX, -bh / 2f + 2, -bl / 2f + legW / 2f + 1.5f};
        f.legB = new float[]{inX, -bh / 2f + 2, bl / 2f - legW / 2f - 1.5f};
        // the head, on its neck
        f.head = head(s, v, hw, hh, hl, snout, coat);
        f.headClosed = closeEyes(f.head, (horseLike(s) ? 7 : 1) + hh - Math.max(2, hh / 3), snout + 2, 2, hw + 1);
        f.neck = horseLike(s) ? new float[]{0, bh / 2f - 1, -bl / 2f + 4} : new float[]{0, bh / 2f - hh * 0.35f + (s == Species.PIG ? -1 : 1), -bl / 2f + 1};
        f.tail = tail(s, coat);
        f.tailAt = new float[]{0, bh / 2f - (s == Species.PIG ? 2 : 1.5f), bl / 2f - 0.5f};
        return f;
    }

    /** Rows y0 (inclusive) to y1 (exclusive) of a part, as a part of their own. */
    private static VoxModel slice(VoxModel src, int y0, int y1) {
        VoxModel m = new VoxModel(src.sx, y1 - y0, src.sz);
        for (int x = 0; x < src.sx; x++) for (int y = y0; y < y1; y++) for (int z = 0; z < src.sz; z++) m.set(x, y - y0, z, src.get(x, y, z));
        return m;
    }

    private static VoxModel leg(Species s, int legH, int legW, Coat coat, boolean front) {
        VoxModel l = new VoxModel(legW, legH, legW);
        boolean hoof = s == Species.COW || s == Species.SHEEP || s == Species.PIG || horseLike(s);
        for (int y = 0; y < legH; y++) for (int x = 0; x < legW; x++) for (int z = 0; z < legW; z++) {
            int col = coat.at(front ? "legF" : "legB", x, y, z, legW, legH, legW);
            if (y == 0) col = hoof ? c(s == Species.PIG ? 0x6A4A40 : 0x2A2420) : c(Tex.shade(col & 0xFFFFFF, 0.75f));
            if (hoof && y == 1) col = c(horseLike(s) ? 0x3A3028 : Tex.shade(col & 0xFFFFFF, 0.85f));
            l.set(x, y, z, col);
        }
        // the haunch is thicker at the top for a big animal
        return l;
    }

    /** The head: a block with a snout in front, eyes, a nose, and the species' ears, horns, mane. Pivot: where the neck joins (its back-bottom). */
    private static ItemModels.Model head(Species s, int v, int hw, int hh, int hl, int snout, Coat coat) {
        boolean horse = horseLike(s), deer = s == Species.DEER;
        int neckL = deer ? 3 : horse ? 6 : 0;
        int sx = hw + 4, sy = hh + (horse ? 13 : 6), sz = hl + snout + neckL + 2;
        VoxModel m = new VoxModel(sx, sy, sz);
        int ox = 2, oy = deer ? 7 : horse ? 7 : 1, oz = 1;   // where the skull's box starts (back-bottom-left); a deer carries its head high
        int back = oz + hl + snout + neckL;      // z of the back of the skull (+z is backward)
        // the skull
        for (int x = 0; x < hw; x++) for (int y = 0; y < hh; y++) for (int z = 0; z < hl; z++) {
            boolean corner = (x == 0 || x == hw - 1) && (y == hh - 1 || y == 0) && (z == 0 || z == hl - 1);
            if (corner && hw > 4) continue;
            m.set(ox + x, oy + y, oz + snout + z, coat.at("head", x, y, z, hw, hh, hl));
        }
        // the snout (narrower and lower), its nose
        int snw = Math.max(2, hw - (s == Species.PIG ? 1 : deer ? 3 : s == Species.COW || horse || s == Species.BROWN_BEAR ? 2 : 3)), snh = Math.max(2, hh - (s == Species.PIG ? 2 : hh / 2));
        int sx0 = ox + (hw - snw) / 2;
        for (int x = 0; x < snw; x++) for (int y = 0; y < snh; y++) for (int z = 0; z < snout; z++) {
            // a deer's muzzle tapers to a fine dark nose, with a pale band behind it
            if (deer && z == 0 && y == snh - 1) continue;
            m.set(sx0 + x, oy + y, oz + z, deer && z == 1 && y < snh - 1 ? c(0xEEE6D8) : coat.at("snout", x, y, z, snw, snh, snout));
        }
        if (deer) {
            // the skull's wedge: the cheeks cut back toward the muzzle, the forehead narrowing
            for (int y = 0; y < hh; y++) { m.set(ox, oy + y, oz + snout, 0); m.set(ox + hw - 1, oy + y, oz + snout, 0); }
            m.set(ox + 1, oy + hh - 1, oz + snout, 0); m.set(ox + hw - 2, oy + hh - 1, oz + snout, 0);
        }
        int nose = deer ? 0x141210 : switch (s) { case PIG -> 0xE89A98; case COW -> 0xE4A8A0; case SHEEP -> 0x2A2420; case HORSE, PEGASUS -> 0x3A3430; case CAT -> 0xE08890; default -> 0x1A1614; };
        if (snout > 0) {
            for (int x = 0; x < snw; x++) for (int y = snh - (s == Species.PIG ? snh : 1); y < snh; y++) if (s == Species.PIG || x > 0 && x < snw - 1 || snw <= 2) m.set(sx0 + x, oy + y, oz, c(nose));
            if (s == Species.PIG) { m.set(sx0 + 1, oy + snh / 2, oz, c(0x6A3A3A)); m.set(sx0 + snw - 2, oy + snh / 2, oz, c(0x6A3A3A)); }
        } else m.set(ox + hw / 2, oy + 1, oz, c(nose));
        // eyes: dark, a glint (a deer's are large and set to the side, ringed pale)
        int ey = oy + hh - Math.max(2, hh / 3);
        if (deer) for (int side : new int[]{0, hw - 1}) { m.set(ox + side, ey, oz + snout + 2, c(0x141010)); m.set(ox + side, ey + 1, oz + snout + 2, c(0x2A2220)); m.set(ox + side, ey - 1, oz + snout + 2, c(0xEEE6D8)); }
        for (int side : new int[]{0, hw - 1}) {
            m.set(ox + side, ey, oz + snout + 1, c(s == Species.CAT || s == Species.MOUNTAIN_LION ? 0x7A8A2A : s == Species.WOLF ? 0x8A7A30 : s == Species.DIRE_WOLF ? 0xC08A20 : 0x141010));
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
            case DIRE_WOLF -> {
                // the mane: a great dark ruff framing the face - out round the cheeks, over the crown, down under the jaw
                // and the throat - the ears all but lost in it
                int maneZ0 = oz + snout + hl / 2 - 1;
                for (int z = maneZ0; z < oz + snout + hl + 1 && z < sz; z++) for (int x = ox - 2; x < ox + hw + 2; x++) for (int y = Math.max(0, oy - 1); y < Math.min(sy, top + 3); y++) {
                    if (x < 0 || x >= sx || m.get(x, y, z) != 0) continue;
                    float cx = (x + 0.5f) - (ox + hw / 2f), cy = (y + 0.5f) - (oy + hh / 2f);
                    float r = (float) Math.hypot(cx / (hw / 2f + 2.2f), cy / (hh / 2f + 2.4f));
                    boolean rough = ((x * 31 + y * 17 + z * 7) % 5) == 0 && r > 0.85f;
                    if (r <= 1f && !rough) m.set(x, y, z, coat.at("mane", x, y, z - maneZ0, sx, sy, hl));
                }
                for (int side : new int[]{ox, ox + hw - 2}) m.box(side, top + 1, ez, side + 1, top + 1, ez, earC);
            }
            case DEER -> {
                // big ears set out to the sides, white within
                for (int side : new int[]{-1, 1}) {
                    int ex = side < 0 ? ox - 1 : ox + hw;
                    for (int y = 0; y < 3; y++) m.set(ex + side * (y / 2), top - 1 + y, ez, y == 1 ? c(0xEEE6D8) : earC);
                }
                // a buck's antlers: a beam up and back off each side of the crown, out and forward tines along it
                if (v == 1) {
                    int an = c(0xD8C8A0), tip = c(0xF0E6CC);
                    for (int side : new int[]{-1, 1}) {
                        int bx = side < 0 ? ox + 1 : ox + hw - 2;
                        for (int i = 0; i < 7; i++) {
                            int px = bx + side * (i / 2), py = top + i, pz = ez + 1 + i / 3;
                            m.set(px, py, pz, i == 6 ? tip : an);
                            if (i == 2 || i == 4) { m.set(px, py + 1, pz - 1, an); m.set(px, py + 2, pz - 2, tip); }
                        }
                    }
                }
            }
            case HORSE, PEGASUS -> {
                for (int side : new int[]{ox, ox + hw - 1}) { m.set(side, top, ez, earC); m.set(side, top + 1, ez, earC); }
                // (0.3.0) the Pegasus's horn: a pale spiral out of the forehead, forward and up
                if (s == Species.PEGASUS) for (int i = 0; i < 6; i++) { int hc = i % 2 == 0 ? 0xF2E8C8 : 0xD8C890; m.set(ox + hw / 2, top + i, oz + snout + 1 - i / 2, c(hc)); if (i < 3) m.set(ox + hw / 2 - 1 + i % 2, top + i, oz + snout + 1 - i / 2, c(hc)); }
                // the mane down the neck
                int mane = coat.at("mane", 0, 0, 0, 1, 1, 1);
                for (int z = 0; z < neckL + 3; z++) for (int y = 0; y < 2; y++) m.set(ox + hw / 2, top - z * (oy - 1) / (neckL + 3) - y + 1, ez + z, mane);
            }
            case COW, SHEEP, PIG -> {
                for (int side : new int[]{ox - 1, ox + hw}) { m.set(side, top - 2, ez, earC); m.set(side, top - 3, ez - (s == Species.PIG ? 1 : 0), earC); }
                if (s == Species.COW) for (int side : new int[]{ox, ox + hw - 1}) { m.set(side, top, ez - 1, c(0xE8E0C8)); m.set(side + (side == ox ? -1 : 1), top + 1, ez - 1, c(0xD8D0B8)); }
                if (s == Species.SHEEP) for (int x = 0; x < hw; x++) for (int z = 0; z < 3; z++) m.set(ox + x, top, ez - z + 1, coat.at("body", x, 0, z, hw, 1, 3));   // the wool cap
            }
            default -> { }
        }
        if (deer) {
            // a deer's neck: slender (narrower than the head), rising steeply from the shoulders, a pale throat under it
            int nc = coat.at("head", 1, 2, 2, hw, hh, hl);
            for (int z = 0; z < neckL + 2; z++) {
                int yTop = oy + hh - 1 - z * 2, yBot = Math.max(1, oy - z * (oy - 1) / (neckL + 1));
                for (int x = 1; x < hw - 1; x++) for (int y = yBot; y <= Math.max(yBot, yTop); y++)
                    m.set(ox + x, y, oz + snout + hl + z - 1, y <= yBot + 1 && z < neckL ? c(0xE4D8C4) : fur(nc & 0xFFFFFF, x, y, z, 0.07f));
            }
        } else if (horse) {
            // the neck: from the skull's back down and back to the shoulders
            int nc = coat.at("head", 0, 0, 0, hw, hh, hl);
            for (int z = 0; z < neckL + 1; z++) {
                int yBase = oy - z * (oy - 1) / (neckL + 1);
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
            case DIRE_WOLF -> {
                // (0.3.9) a long cat's tail ending in a dark tuft
                int len = 15;
                m = new VoxModel(4, 4, len + 1);
                for (int z = 0; z < len - 3; z++) m.box(1, 1, z, 2, 2, z, fur(tc & 0xFFFFFF, 0, 0, z, 0.12f));
                for (int z = len - 3; z <= len; z++) m.box(0, 0, z, 3, 3, z, fur(tip & 0xFFFFFF, 0, 0, z, 0.2f));
                return new ItemModels.Model(m, 2f, 2f, 0.5f);
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
            case DEER -> {
                // the short flag of a tail: brown above, white beneath (raised when it bolts)
                m = new VoxModel(3, 2, 4);
                m.box(0, 1, 0, 2, 1, 3, tc); m.box(0, 0, 0, 2, 0, 3, tip);
                return new ItemModels.Model(m, 1.5f, 1.5f, 0.5f);
            }
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
        f.headClosed = closeEyes(f.head, 2, 2, 0, 3);
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
        f.headClosed = closeEyes(f.head, 4, sk0 + 1, 0, 2);
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

    private static ItemModels.Model closeEyes(ItemModels.Model head, int eyeY, int eyeZ, int... eyeX) {
        VoxModel original = head.vox(), closed = new VoxModel(original.sx, original.sy, original.sz);
        for (int x = 0; x < original.sx; x++) for (int y = 0; y < original.sy; y++) for (int z = 0; z < original.sz; z++) closed.set(x, y, z, original.get(x, y, z));
        for (int x : eyeX) {
            int coat = original.get(x, eyeY, eyeZ + 1);
            if (coat == 0) coat = original.get(x, eyeY + 1, eyeZ);
            closed.set(x, eyeY, eyeZ, c(Tex.shade(coat & 0xFFFFFF, 0.85f)));
        }
        return new ItemModels.Model(closed, head.gx(), head.gy(), head.gz());
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

    /** The long-necked, hoofed build (a horse, a Pegasus - and a deer, lighter). */
    static boolean horseLike(Species s) { return s == Species.HORSE || s == Species.PEGASUS || s == Species.DEER; }

    /**
     * (0.3.4, gamefixes: the deer) A deer's coat by its variant (set from the animal at birth: 0 a doe, 1 a buck, 2 a
     * fawn): warm red-brown above, a paler fawn flank, the belly, throat, the inside of the ears and the underside of the
     * tail white, a dark muzzle; a buck is darker over the shoulders; a fawn is dappled with white spots down its back.
     */
    private static Coat deerCoat(int v) {
        int back = v == 1 ? 0x7A5030 : 0x9A6A40, flank = v == 1 ? 0x9A6A40 : 0xB4844E, belly = 0xEEE6D8;
        return (part, x, y, z, sx, sy, sz) -> switch (part) {
            case "body" -> {
                if (y < 2) yield fur(belly, x, y, z, 0.05f);
                // the white rump patch round the tail
                if (z >= sz - 2 && y >= sy * 0.3f && y < sy - 1 && x > 0 && x < sx - 1) yield fur(0xF2ECE0, x, y, z, 0.04f);
                boolean spot = v == 2 && y >= sy - 3 && (x * 7 + z * 13 + y * 3) % 9 == 0 && x > 0 && x < sx - 1;
                yield spot ? c(0xF4EEE2) : fur(y > sy * 0.6f ? back : flank, x, y, z, 0.07f);
            }
            case "head" -> fur(y == 0 && z >= 1 && z < 3 && x > 0 && x < sx - 1 ? belly : flank, x, y, z, 0.06f);   // a pale throat patch (the neck takes the flank)
            case "snout" -> fur(y >= sy - 1 ? 0x2A2420 : Tex.shade(flank, 0.9f), x, y, z, 0.05f);
            case "ear" -> c(Tex.shade(flank, 0.92f));
            case "legF", "legB" -> fur(Tex.shade(flank, 0.9f), x, y, z, 0.06f);
            case "tail" -> c(back);
            case "tip" -> c(belly);
            default -> fur(flank, x, y, z, 0.06f);
        };
    }

    /**
     * (0.3.0, gamefixes) The Pegasus: the horse's figure, brolic - a broader, deeper body, thicker legs, a heavy neck -
     * in white with a silver mane, a horn, and a pair of great feathered wings folded along its back from the withers.
     */
    private static Figure pegasus(int v) {
        Figure f = quad(Species.PEGASUS, v, 14, 13, 28, 15, 5, 6, 7, 8, 5, pegasusCoat(v));
        // a wing: a long panel of primaries, its leading edge thick, the feathers fanned and layered (built pointing back, +z)
        int wl = 26, ww = 3, wh = 12;
        VoxModel w = new VoxModel(ww, wh, wl);
        for (int z = 0; z < wl; z++) for (int y = 0; y < wh; y++) {
            float t = z / (float) wl;
            int depth = Math.round(wh * (1 - 0.65f * t)) - (z % 3 == 2 ? 1 : 0);
            if (wh - 1 - y >= depth) continue;
            int col = y == wh - 1 ? 0xE8E2D6 : (z + y) % 4 == 0 ? 0xD6D0C6 : 0xF6F2EA;
            if (wh - 1 - y > depth - 3 && z > 6) col = 0xC8C2B8;          // the primaries' darker tips
            for (int x = 0; x < (y == wh - 1 ? ww : 1 + (y > wh - 4 ? 1 : 0)); x++) w.set(x, y, z, c(col));
        }
        f.wing = new ItemModels.Model(w, 0.5f, wh - 1, 1);
        f.wingAt = new float[]{f.bw / 2f - 1, f.bh / 2f - 1, -f.bl / 2f + 6};
        return f;
    }

    private static Coat pegasusCoat(int v) {
        int coat = v == 1 ? 0xE6E0D8 : 0xF4F0EA, mane = v == 2 ? 0xC8B068 : 0xC0C4CC;
        return (part, x, y, z, sx, sy, sz) -> switch (part) {
            case "mane", "tip", "tail" -> fur(mane, x, y, z, 0.12f);
            case "ear" -> c(Tex.shade(coat, 0.9f));
            case "snout" -> fur(0xD8CFC6, x, y, z, 0.05f);
            case "legF", "legB" -> fur(y < 3 ? 0xCFC6BC : coat, x, y, z, 0.05f);
            default -> fur(coat, x, y, z, 0.05f);
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

    /** (0.3.9) The Dire Wolf's: tawny gold, paler beneath; the mane black-brown, lighter at its roots; the tail's tuft black. */
    private static Coat direCoat() {
        return (part, x, y, z, sx, sy, sz) -> switch (part) {
            case "snout" -> fur(y == 0 ? 0xD8C09A : 0xC09466, x, y, z, 0.06f);
            case "ear" -> c(0x3A2414);
            case "tip" -> c(0x24160E);
            case "tail" -> c(0xB88C58);
            // the mane in locks: deep brown-black with chestnut streaks, its outer edge (the part's extent) lighter
            case "mane" -> {
                int h = (x * 73856093) ^ (y * 19349663) ^ (z * 83492791);
                boolean streak = ((h >>> 9) & 7) == 0, fringe = ((h >>> 13) & 3) == 0 && (x == 0 || x == sx - 1 || y == sy - 1);
                yield fur(fringe ? 0x9A6636 : streak ? 0x6E4628 : ((h >>> 5) & 1) == 0 ? 0x4C2E1A : 0x3A2414, x, y, z, 0.12f);
            }
            case "body" -> fur(y < 2 ? 0xCFB284 : 0xBC925C, x, y, z, 0.09f);
            case "head" -> fur(y < 1 ? 0xCFB284 : 0xB48A56, x, y, z, 0.08f);
            default -> fur(0xB48A56, x, y, z, 0.1f);
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
