package rpg.render;

import astratto.voxel.VoxModel;
import rpg.item.ItemDef;

/**
 * The cut gems, the dragon materials, the fossil and the plain vessels (gamefixes: each has its own design). Built at
 * the item density like {@link HiResItems}, gems shown larger than life so their shape reads in a single grid cell.
 * Voxels cannot glow, so a light "inside" a stone is painted: a brighter core showing through the facets nearest the
 * viewer, the facets themselves banded light and dark around the stone so the cut reads in flat colour (the WoW / OSRS
 * way of drawing a gem).
 */
final class TreasureModels {
    private TreasureModels() { }

    static int mat(int rgb) { return ItemModels.mat(rgb); }
    static int metal(int rgb) { return ItemModels.metal(rgb); }

    private static int mix(int a, int b, float t) {
        t = Math.max(0, Math.min(1, t));
        int r = Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t), g = Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t), bl = Math.round((a & 255) * (1 - t) + (b & 255) * t);
        return r << 16 | g << 8 | bl;
    }

    /** A stable per-voxel noise in 0..1. */
    private static float hash(int x, int y, int z) {
        long h = (x * 73856093L) ^ (y * 19349663L) ^ (z * 83492791L);
        h ^= h >>> 13; h *= 0x5DEECE66DL; h ^= h >>> 17;
        return ((h >>> 8) & 0xFFFF) / 65535f;
    }

    /** Facet banding: the angle round the stone cut into n faces, alternating light and dark, the top one brightest. */
    private static float facet(float dx, float dz, int n) {
        double a = Math.atan2(dz, dx) + Math.PI;
        int f = (int) (a / (2 * Math.PI) * n) % n;
        return f % 2 == 0 ? 1.12f : 0.86f;
    }

    static ItemModels.Model of(ItemDef d) {
        return switch (d.id) {
            case "ruby" -> ruby();
            case "emerald" -> emerald();
            case "sapphire" -> sapphire();
            case "diamond" -> diamond();
            case "onyx" -> onyx();
            case "dragonstone" -> dragonstone();
            case "amethyst" -> amethyst(false);
            case "amethyst_cluster" -> amethyst(true);
            case "dragonhide" -> dragonhide();
            case "dragon_bones" -> dragonBones();
            case "dragon_egg" -> dragonEgg();
            case "rare_fossil" -> fossil();
            case "pot" -> pot();
            case "bucket" -> bucket(0);
            case "bucket_of_water" -> bucket(0x3A6EA8);
            default -> null;
        };
    }

    // ------------------------------------------------------------------ gems

    /** Deep crimson teardrop, a soft pink star glowing inside, faint embers rising off its point. */
    static ItemModels.Model ruby() {
        int W = 16, H = 22;
        VoxModel m = new VoxModel(W, H, W);
        float cx = 7.5f, cz = 7.5f, R = 7.2f;
        int deep = 0x8A0818, red = 0xC8142A, hi = 0xF04A5A, star = 0xFFB0C0, core = 0xFF7890;
        for (int y = 0; y < 19; y++) {
            float t = y / 18f;
            // round below the girdle (t 0.38), a gently concave taper to the point above
            float r = t < 0.38f ? R * (float) Math.sqrt(1 - Math.pow((0.38f - t) / 0.38f, 2)) : R * (float) Math.pow(1 - (t - 0.38f) / 0.62f, 1.25f);
            for (int x = 0; x < W; x++) for (int z = 0; z < W; z++) {
                float dx = x - cx, dz = z - cz, dist = (float) Math.hypot(dx, dz);
                if (dist > r + 0.15f) continue;
                float rim = r - dist;
                float f = facet(dx, dz, 10);
                int c = mix(deep, red, 0.35f + t * 0.6f);
                c = shade(c, f);
                if (dx < -1 && y > 6 && rim < 1.2f) c = mix(c, hi, 0.55f);           // the light catching the upper left
                // the star: six soft arms seen through the front of the stone, centred low in the body
                float sy = y - 6.5f, ang = (float) Math.atan2(sy, dx), rr = (float) Math.hypot(dx, sy);
                float arm = (float) Math.abs(Math.cos(ang * 3));
                boolean front = dz > 0 && rim < 1.4f;
                if (front && rr < 5.5f && arm > 0.7f + rr * 0.02f) c = mix(c, star, 0.85f - rr * 0.09f);
                if (front && rr < 2.0f) c = core;
                m.set(x, y, z, mat(c));
            }
        }
        // the embers: loose sparks above the point
        int[][] sparks = {{8, 20, 7}, {6, 21, 8}, {9, 19, 9}, {7, 21, 6}};
        for (int[] s : sparks) m.set(s[0], s[1], s[2], mat(s[1] == 21 ? 0xFFB040 : 0xFF7030));
        return new ItemModels.Model(m, 7.5f, 0, 7.5f);
    }

    /** A chunky rectangular block of vivid green, a shining stepped top, rough mossy sides. */
    static ItemModels.Model emerald() {
        int W = 18, H = 14, D = 12;
        VoxModel m = new VoxModel(W, H, D);
        int moss = 0x2E6A2A, mossDk = 0x1E4A1C, green = 0x18B050, bright = 0x50E080, shine = 0xC8FFD8;
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) for (int z = 0; z < D; z++) {
            // the step cut: the top two layers inset, the very top a table
            int inset = y >= H - 1 ? 3 : y >= H - 3 ? 1 : 0;
            if (x < inset || x >= W - inset || z < inset || z >= D - inset) continue;
            boolean side = x == 0 || x == W - 1 || z == 0 || z == D - 1;
            float n = hash(x, y, z);
            if (side && y < H - 3 && n < 0.18f) continue;                          // the rough, pitted sides
            int c;
            if (y >= H - 1) c = (x + z) % 5 == 0 ? shine : bright;                 // the table
            else if (y >= H - 3) c = (x < W / 2) ? bright : green;                 // the bevel
            else if (side) { float p = hash(x / 3, y / 3, z / 3); c = p < 0.3f ? (n < 0.5f ? moss : mossDk) : n < 0.25f ? mix(green, moss, 0.4f) : green; }
            else c = green;
            m.set(x, y, z, mat(c));
        }
        // a shine streak down the front-left edge
        for (int y = 3; y < H - 3; y++) m.set(1, y, 1, mat(mix(green, shine, 0.5f)));
        return new ItemModels.Model(m, W / 2f, 0, D / 2f);
    }

    /** An oval, dark royal blue below fading to pale sky blue at the top, with a watery shine. */
    static ItemModels.Model sapphire() {
        int W = 20, H = 15, D = 14;
        VoxModel m = new VoxModel(W, H, D);
        int royal = 0x10208A, blue = 0x2A55D8, sky = 0x9ACCF8, shine = 0xE8F6FF;
        float cx = 9.5f, cy = 7f, cz = 6.5f;
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) for (int z = 0; z < D; z++) {
            float dx = (x - cx) / 9.6f, dy = (y - cy) / 7.2f, dz = (z - cz) / 6.6f;
            if (dx * dx + dy * dy + dz * dz > 1) continue;
            float t = y / (float) (H - 1);
            int c = t < 0.5f ? mix(royal, blue, t * 2) : mix(blue, sky, (t - 0.5f) * 2);
            c = shade(c, facet(x - cx, z - cz, 12));
            // the watery shine: a wavy highlight band across the upper front
            float wave = (float) Math.sin(x * 0.7f) * 0.8f;
            if (y > 9 && Math.abs(y - 11.5f - wave) < 0.7f && x > 4 && x < 15) c = mix(c, shine, 0.7f);
            m.set(x, y, z, mat(c));
        }
        return new ItemModels.Model(m, cx, 0, cz);
    }

    /** A chunky round white gem, an icy blue core, a bright sparkle in the centre. */
    static ItemModels.Model diamond() {
        int W = 18, H = 16;
        VoxModel m = new VoxModel(W, H, W);
        int white = 0xEEF4FA, ice = 0x9AD4F4, deep = 0x5AA8E0, spark = 0xFFFFFF, shade = 0xC2CED8;
        float cx = 8.5f, cz = 8.5f;
        for (int y = 0; y < H; y++) {
            // a brilliant cut: the pavilion a cone to the culet, the crown a short frustum to a broad table
            float r = y < 9 ? 1 + y * 0.92f : 9.3f - (y - 9) * 0.75f;
            if (y == H - 1) r = 5.2f;
            for (int x = 0; x < W; x++) for (int z = 0; z < W; z++) {
                float dx = x - cx, dz = z - cz, dist = (float) Math.hypot(dx, dz);
                if (dist > r) continue;
                float rim = r - dist;
                int c = facet(dx, dz, 8) > 1 ? white : shade;
                // the icy core showing through the facets
                float fr = (float) Math.hypot(dx, y - 9.5f);
                if (rim < 1.4f && dz > 0 && fr < 5.5f) c = mix(c, fr < 2.6f ? deep : ice, 0.8f);
                if (y == H - 1) c = dist < 2.2f ? ice : white;
                m.set(x, y, z, mat(c));
            }
        }
        // the sparkle: a four-point star on the table and through the middle of the front
        for (int k = -3; k <= 3; k++) { m.set((int) cx + k, H - 1, (int) cz, mat(spark)); m.set((int) cx, H - 1, (int) cz + k, mat(spark)); }
        for (int k = -2; k <= 2; k++) for (int z = W - 1; z >= 0; z--) { if (m.get((int) cx + k, 9, z) != 0) { m.set((int) cx + k, 9, z, mat(spark)); break; } }
        for (int k = -2; k <= 2; k++) for (int z = W - 1; z >= 0; z--) { if (m.get((int) cx, 9 + k, z) != 0) { m.set((int) cx, 9 + k, z, mat(spark)); break; } }
        return new ItemModels.Model(m, cx, 0, cz);
    }

    /** A jet-black sphere on a rough dark base, a faint red glow inside where the light turns it. */
    static ItemModels.Model onyx() {
        int W = 18, H = 19;
        VoxModel m = new VoxModel(W, H, W);
        int jet = 0x0E0C10, sheen = 0x3A3640, rough = 0x2A2622, roughDk = 0x181512, glow = 0x7A1018, glowHi = 0xC02A2A;
        float cx = 8.5f, cz = 8.5f, cy = 10.5f, R = 8;
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) for (int z = 0; z < W; z++) {
            float dx = x - cx, dy = y - cy, dz = z - cz, d = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            boolean base = y < 4 && Math.hypot(dx, dz) < 6.5f - y * 0.4f + hash(x, 0, z) * 1.4f;
            if (d > R && !base) continue;
            int c;
            if (d > R) c = hash(x, y, z) < 0.5f ? rough : roughDk;
            else {
                c = jet;
                if (dx < -2 && dy > 2 && d > R - 1.5f) c = sheen;                  // the sheen on its upper left
                float inner = (float) Math.hypot(dx - 1f, dy + 1f);
                if (d > R - 1.5f && dz > 0 && inner < 4.6f) c = mix(jet, inner < 2.2f ? glowHi : glow, 0.75f - inner / 4.6f * 0.55f);   // the red within, toward the light
            }
            m.set(x, y, z, mat(c));
        }
        return new ItemModels.Model(m, cx, 0, cz);
    }

    /** A heart-shaped purple-red gem, a soft flame flickering inside. */
    static ItemModels.Model dragonstone() {
        int W = 20, H = 21, D = 9;
        VoxModel m = new VoxModel(W, H, D);
        int deep = 0x5A1040, body = 0x9A2A6A, hi = 0xD0508A, flame = 0xFF8A30, flameHi = 0xFFD070;
        float cx = 9.5f, cz = 4f;
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) {
            // the heart curve: (x^2 + y^2 - 1)^3 - x^2 y^3 <= 0, scaled into the box
            float u = (x - cx) / 8.2f, v = (y - 7.4f) / 8.0f;
            float q = u * u + v * v - 1;
            if (q * q * q - u * u * v * v * v > 0) continue;
            if (v > 0.7f && Math.abs(u) < (v - 0.7f) * 0.5f) continue;          // the cleft between the lobes
            float depth = 0.8f + 2.4f * (float) Math.sqrt(Math.max(0, -q));
            boolean outline = q > -0.18f;
            for (int z = 0; z < D; z++) {
                if (Math.abs(z - cz) > depth + 0.6f) continue;
                int c = mix(deep, body, 0.45f + y / (float) H * 0.5f);
                c = shade(c, (x + y) % 6 < 3 ? 1.1f : 0.9f);                           // the facets in diagonal bands
                if (u < -0.3f && v > 0.2f) c = mix(c, hi, 0.45f);
                if (outline) c = deep;
                // the flame: a teardrop of orange rising from the lower middle, its heart pale gold
                float fu = (x - cx) / 3.2f, fv = (y - 4f) / 7f;
                if (fv > 0 && fv < 1 && Math.abs(fu) < (1 - fv) * (0.6f + fv * 1.4f) && Math.abs(z - cz) >= depth - 0.6f)
                    c = Math.abs(fu) < (1 - fv) * 0.5f && fv < 0.55f ? flameHi : flame;
                m.set(x, y, z, mat(c));
            }
        }
        return new ItemModels.Model(m, cx, 0, cz);
    }

    /** A jagged purple crystal cluster, dark veins through it: the cluster (several points on a rock) or the single crystal. */
    static ItemModels.Model amethyst(boolean cluster) {
        int W = cluster ? 18 : 12, H = cluster ? 30 : 24, D = cluster ? 14 : 10;
        VoxModel m = new VoxModel(W, H, D);
        int purple = 0x7A3AB0, light = 0xB07AE0, pale = 0xDCC0F4, vein = 0x3A1260, rock = 0x6A6460, rockDk = 0x4A4542;
        // points: base x, z, height, radius, lean x, lean z
        float[][] pts = cluster ? new float[][]{{9, 7, 27, 3.4f, 0, 0}, {5, 6, 18, 2.6f, -0.18f, 0.02f}, {13, 8, 20, 2.6f, 0.2f, -0.04f}, {8, 11, 14, 2.2f, -0.04f, 0.2f}, {11, 3, 13, 2f, 0.1f, -0.2f}}
                : new float[][]{{6, 5, 22, 3.6f, 0, 0}, {3.5f, 6, 11, 1.8f, -0.2f, 0.05f}};
        int base = cluster ? 4 : 2;
        if (cluster) for (int y = 0; y < base; y++) for (int x = 1; x < W - 1; x++) for (int z = 1; z < D - 1; z++)
            if (Math.hypot((x - 9) / 8f, (z - 7) / 6f) < 1 - y * 0.12f + hash(x, y, z) * 0.15f) m.set(x, y, z, mat(hash(x, y, z) < 0.6f ? rock : rockDk));
        for (float[] p : pts) {
            int top = (int) p[2];
            for (int y = base - 1; y < top; y++) {
                float t = (y - base + 1) / (float) (top - base + 1);
                float r = t < 0.72f ? p[3] : p[3] * (1 - (t - 0.72f) / 0.28f);       // a hexagonal prism, then the point
                float ox = p[0] + p[4] * (y - base), oz = p[1] + p[5] * (y - base);
                for (int x = 0; x < W; x++) for (int z = 0; z < D; z++) {
                    float dx = x - ox, dz = z - oz;
                    float hex = Math.max(Math.abs(dx) * 0.866f + Math.abs(dz) * 0.5f, Math.abs(dz));
                    if (hex > r + 0.2f) continue;
                    int c = facet(dx, dz, 6) > 1 ? light : purple;
                    if (t > 0.72f) c = mix(c, pale, 0.5f);
                    // the dark veins: a few thin bands winding up the crystal
                    float vy = y + (float) Math.sin((dx + dz) * 0.9f) * 2.2f;
                    if (((int) vy) % 7 == 0 && hex > r - 1.2f) c = vein;
                    m.set(x, y, z, mat(c));
                }
            }
        }
        return new ItemModels.Model(m, W / 2f, 0, D / 2f);
    }

    // ------------------------------------------------------------------ dragon materials and the fossil

    /** A thick folded block of big black scales edged in red, its dark greenish backing showing at the fold. */
    static ItemModels.Model dragonhide() {
        int W = 26, H = 10, D = 26;
        VoxModel m = new VoxModel(W, H, D);
        int scale = 0x16141A, scaleHi = 0x34303C, edge = 0xA01818, backing = 0x2A3A26, backingDk = 0x1C2818;
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) for (int z = 0; z < D; z++) {
            // three folded layers, each a little shorter, the fold rounded at the front edge
            int layer = y / 3, inset = layer;
            if (x < inset || x >= W - inset || z < inset * 2 || z >= D - inset) continue;
            int c;
            boolean topOfLayer = y % 3 == 2, frontFold = z < inset * 2 + 3 || z >= D - inset - 1;
            if (frontFold && !topOfLayer) c = (x + y) % 3 == 0 ? backingDk : backing;   // the fold shows the backing
            else {
                // big scales: a half-offset grid of rounded plates, each rimmed in red
                int sx = (x + (z / 5 % 2) * 3) % 6, sz = z % 5;
                boolean rim = sz == 0 && sx != 0;
                c = rim ? edge : sx == 0 ? 0x0A090C : (sx + sz < 4 ? scaleHi : scale);
            }
            m.set(x, y, z, mat(c));
        }
        return new ItemModels.Model(m, W / 2f, 0, D / 2f);
    }

    /** A big ivory bone, cracked with golden cracks, the knuckle ends charred. */
    static ItemModels.Model dragonBones() {
        int W = 16, H = 44, D = 12;
        VoxModel m = new VoxModel(W, H, D);
        int ivory = 0xE6DCC0, ivoryDk = 0xC8BC9C, gold = 0xF0C040, char_ = 0x2A2018, charLt = 0x4A3A2A;
        float cx = 7.5f, cz = 5.5f;
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) for (int z = 0; z < D; z++) {
            float dx = x - cx, dz = z - cz;
            boolean inside;
            float endT = Math.min(y, H - 1 - y);
            if (endT < 8) {
                // the knuckles: two lobes side by side at each end
                float ly = endT - 4;
                inside = Math.hypot((dx - 3.4f) / 4.4f, ly / 4.4f) + Math.abs(dz) / 7f < 1.15f || Math.hypot((dx + 3.4f) / 4.4f, ly / 4.4f) + Math.abs(dz) / 7f < 1.15f;
            } else inside = Math.hypot(dx / (2.4f + Math.max(0, 14 - endT) * 0.12f), dz / 2.4f) < 1;
            if (!inside) continue;
            int c = (x + z) % 4 == 0 ? ivoryDk : ivory;
            if (endT < 4) c = hash(x, y, z) < 0.6f ? char_ : charLt;                 // charred ends
            else if (endT < 6 && hash(x, y, z) < 0.4f) c = charLt;
            // the golden cracks: a jagged line wandering down the shaft, and a branch
            float crack = cx + (float) Math.sin(y * 0.45f) * 2.2f;
            if (endT >= 6 && Math.abs(x - crack) < 0.7f && dz > 0) c = gold;
            if (y > 18 && y < 26 && Math.abs(x - (crack + (y - 18) * 0.5f)) < 0.7f && dz > 0) c = gold;
            if (endT >= 6 && Math.abs(x - crack) < 0.7f && dz < 0 && (y / 3) % 2 == 0) c = gold;
            m.set(x, y, z, mat(c));
        }
        return new ItemModels.Model(m, cx, 0, cz);
    }

    /** A spotted egg, glowing orange cracks, a little smoke curling off its top. */
    static ItemModels.Model dragonEgg() {
        int W = 24, H = 34;
        VoxModel m = new VoxModel(W, H, W);
        int shell = 0x7A5A3E, shellLt = 0x9A7A56, spot = 0x3A2A1E, crack = 0xFF8A20, crackHi = 0xFFD060, smoke = 0x8A8A8E, smokeLt = 0xB4B4B8;
        float cx = 11.5f, cz = 11.5f;
        for (int y = 0; y < 28; y++) for (int x = 0; x < W; x++) for (int z = 0; z < W; z++) {
            float dx = (x - cx) / 10.5f, dz = (z - cz) / 10.5f, dy = (y - 12f) / (y > 12 ? 15.5f : 12.2f);
            if (dx * dx + dy * dy + dz * dz > 1) continue;
            float n = hash(x / 3, y / 3, z / 3);
            int c = n < 0.18f ? spot : (x + y) % 5 == 0 ? shellLt : shell;
            // the cracks: zig-zag seams round the egg that glow
            double a = Math.atan2(z - cz, x - cx);
            float seam = 14 + (float) Math.sin(a * 5) * 3;
            float seam2 = 20 + (float) Math.cos(a * 4 + 1) * 2.5f;
            if (Math.abs(y - seam) < 0.7f || Math.abs(y - seam2) < 0.6f && Math.cos(a) > 0) c = (int) (a * 10) % 3 == 0 ? crackHi : crack;
            m.set(x, y, z, mat(c));
        }
        // a wisp of smoke above the top
        int[][] wisp = {{11, 28, 12}, {12, 29, 12}, {12, 30, 11}, {13, 31, 11}, {12, 32, 12}, {11, 33, 12}, {13, 30, 12}};
        for (int[] w : wisp) m.set(w[0], w[1], w[2], mat(w[1] % 2 == 0 ? smoke : smokeLt));
        return new ItemModels.Model(m, cx, 0, cz);
    }

    /** A stone slab with a tiny raptor skeleton set in amber and leaf marks pressed round it. */
    static ItemModels.Model fossil() {
        int W = 40, H = 6, D = 58;
        VoxModel m = new VoxModel(W, H, D);
        int stone = 0xA89878, stoneDk = 0x8A7C60, stoneLt = 0xC0B294, amber = 0xB06410, amberLt = 0xC87A20, bone = 0xFFF6E0, leaf = 0x6A6048;
        for (int y = 0; y < 5; y++) for (int x = 0; x < W; x++) for (int z = 0; z < D; z++) {
            boolean chipped = (x < 2 || x >= W - 2 || z < 2 || z >= D - 2) && hash(x, y, z) < 0.35f;
            if (chipped || y == 4 && (x == 0 || x == W - 1 || z == 0 || z == D - 1)) continue;
            float n = hash(x, y, z);
            m.set(x, y, z, mat(n < 0.2f ? stoneDk : n > 0.85f ? stoneLt : stone));
        }
        // the amber window, raised a voxel above the slab
        float ax = 20, az = 30;
        for (int x = 0; x < W; x++) for (int z = 0; z < D; z++) if (Math.hypot((x - ax) / 13f, (z - az) / 18f) < 1) {
            m.set(x, 4, z, mat((x + z) % 7 == 0 ? amberLt : amber));
            m.set(x, 5, z, mat(amber));
        }
        // the raptor in the amber: skull, spine curving to a tail, ribs, two legs, a raised claw
        int[][] bones = {{20, 16}, {21, 16}, {19, 17}, {20, 17}, {21, 17}, {22, 17}, {20, 18}};       // skull
        for (int[] b : bones) m.set(b[0], 5, b[1], mat(bone));
        for (int z = 19; z <= 44; z++) { int x = 20 + (int) Math.round(Math.sin((z - 19) * 0.16f) * 3); m.set(x, 5, z, mat(bone)); if (z < 36) m.set(x + 1, 5, z, mat(bone)); }   // spine and tail
        for (int z = 21; z <= 28; z += 2) for (int k = 1; k <= 3; k++) { m.set(20 + (int) Math.round(Math.sin((z - 19) * 0.16f) * 3) - k, 5, z, mat(bone)); m.set(20 + (int) Math.round(Math.sin((z - 19) * 0.16f) * 3) + k, 5, z, mat(bone)); }
        int[][] legs = {{24, 32}, {25, 33}, {26, 34}, {26, 35}, {27, 36}, {16, 31}, {15, 32}, {14, 33}, {14, 34}, {13, 35}, {18, 23}, {17, 22}, {16, 21}};
        for (int[] b : legs) { m.set(b[0], 5, b[1], mat(bone)); m.set(b[0] + 1, 5, b[1], mat(bone)); }
        for (int[] b : bones) m.set(b[0], 5, b[1] - 1, mat(bone));
        // the leaf marks pressed into the slab around it
        float[][] leaves = {{6, 8, 0.6f}, {33, 10, -0.5f}, {7, 48, 0.3f}, {34, 50, -0.8f}, {4, 28, 1.4f}};
        for (float[] l : leaves) for (int k = -4; k <= 4; k++) for (int w = -1; w <= 1; w++) {
            if (Math.abs(w) == 1 && Math.abs(k) > 2) continue;
            int x = Math.round(l[0] + k * (float) Math.cos(l[2]) - w * (float) Math.sin(l[2])), z = Math.round(l[1] + k * (float) Math.sin(l[2]) + w * (float) Math.cos(l[2]));
            if (x >= 0 && x < W && z >= 0 && z < D && m.get(x, 4, z) != 0) m.set(x, 4, z, mat(leaf));
        }
        return new ItemModels.Model(m, W / 2f, 0, D / 2f);
    }

    // ------------------------------------------------------------------ vessels

    /** An empty clay pot: a round belly, a narrow neck and a rolled lip, its inside dark. */
    static ItemModels.Model pot() {
        int W = 20, H = 22;
        VoxModel m = new VoxModel(W, H, W);
        int clay = 0x9A5A3A, clayLt = 0xB8764E, clayDk = 0x6A3A24, inside = 0x2A1A12;
        float cx = 9.5f, cz = 9.5f;
        for (int y = 0; y < H; y++) {
            float t = y / (float) (H - 1);
            float r = y == 0 ? 5.5f : t < 0.7f ? 6 + 3.4f * (float) Math.sin(Math.PI * t / 0.7f * 0.95f) : t < 0.92f ? 5.4f - (t - 0.7f) * 6 : 5.8f;
            for (int x = 0; x < W; x++) for (int z = 0; z < W; z++) {
                float dx = x - cx, dz = z - cz, d = (float) Math.hypot(dx, dz);
                if (d > r) continue;
                boolean hollow = y > 1 && d < r - 1.3f;
                if (hollow && y < H - 2) continue;
                int c = hollow ? inside : dx < -2 ? clayLt : dx > 3 ? clayDk : clay;
                if (y == (int) (H * 0.45f) && !hollow) c = clayDk;                  // a scored band round the belly
                m.set(x, y, z, mat(c));
            }
        }
        // the dark mouth
        for (int x = 0; x < W; x++) for (int z = 0; z < W; z++) if (Math.hypot(x - cx, z - cz) < 4.2f) m.set(x, H - 1, z, mat(inside));
        return new ItemModels.Model(m, cx, 0, cz);
    }

    /** A wooden bucket with iron hoops and a bail: empty (the dark inside showing) or full of water. */
    static ItemModels.Model bucket(int water) {
        VoxModel m = new VoxModel(13, 15, 13);
        int wood = mat(0x9A7246), woodDk = mat(0x7A5634), hoop = metal(0x6A6A70), inside = mat(0x4A3420);
        for (int y = 0; y < 12; y++) {
            float r = 5.2f + y * 0.08f;
            for (int x = 0; x < 13; x++) for (int z = 0; z < 13; z++) {
                float d = (float) Math.hypot(x - 6, z - 6);
                if (d > r) continue;
                if (d > r - 1.2f || y == 0) m.set(x, y, z, y == 2 || y == 9 ? hoop : (Math.round(Math.atan2(z - 6, x - 6) * 4) % 2 == 0 ? wood : woodDk));
                else if (y == 1) m.set(x, y, z, inside);
                else if (water != 0 && y == 10) m.set(x, y, z, mat((x + z) % 4 == 0 ? 0x8AC0E8 : water));
            }
        }
        for (int i = 0; i <= 12; i++) { double t = Math.PI * i / 12; m.set((int) Math.round(6 - 5.6 * Math.cos(t)), 12 + (int) Math.round(1.6 * Math.sin(t)), 6, hoop); }
        return new ItemModels.Model(m, 6.5f, 0.5f, 6.5f);
    }

    private static int shade(int rgb, float f) { return astratto.render.Tex.shade(rgb, f) & 0xFFFFFF; }
}
