package rpg.render;

import astratto.voxel.VoxModel;
import rpg.item.ItemDef;

/**
 * What monsters leave behind, modelled by kind (ItemDef.look) and coloured by the item: an essence glowing in a stoppered
 * vial, a folded pelt, a fang or claw, a tied pouch of dust, a wing folded on itself, a glossy eye, a lump of fat or
 * gland, a flask, a trinket on its cord, a string of beads, a rolled scroll, a banner on its pole, a bone totem, a
 * pile of ash, a spool of silk, a shell plate, a horned face-plate. One voxel is 1/64 m, as for every item.
 */
final class LootModels {
    private LootModels() { }

    static int mat(int rgb) { return ItemModels.mat(rgb); }
    static int sh(int rgb, float f) { return astratto.render.Tex.shade(rgb, f) & 0xFFFFFF; }

    private static float hash(int x, int y, int z) {
        long h = (x * 73856093L) ^ (y * 19349663L) ^ (z * 83492791L);
        h ^= h >>> 13; h *= 0x5DEECE66DL; h ^= h >>> 17;
        return ((h >>> 8) & 0xFFFF) / 65535f;
    }

    static ItemModels.Model of(ItemDef d) {
        int c = d.color;
        return switch (d.look) {
            case "essence" -> vial(c, true);
            case "flask" -> vial(c, false);
            case "pelt" -> pelt(c, d.id.contains("alpha"));
            case "fang" -> fang(c, d.id.equals("claw"));
            case "dust" -> pouch(c);
            case "wing" -> wing(c);
            case "eye" -> eye(c);
            case "gland" -> gland(c, d.id.contains("heart"));
            case "trinket" -> trinket(c, d.id);
            case "beads" -> beads(c);
            case "scroll" -> scroll(c);
            case "banner" -> banner(c, d.id.contains("standard"));
            case "totem" -> totem(c);
            case "ash" -> ash(c);
            case "silk" -> silk(c, d.id.equals("webbing"));
            case "plate" -> plate(c);
            case "visage" -> visage(c);
            case "bone" -> bone(c);
            case "cloth" -> cloth(c, d.id.contains("tattered") || d.id.contains("bloodied"));
            default -> null;
        };
    }

    /**
     * A glass vial, stoppered with cork. An essence is a light: its colour fills the vial, white-hot at its heart and
     * brighter toward the top, and the glass is clear. A flask holds a dark liquid to the shoulder, with a paper label.
     */
    static ItemModels.Model vial(int c, boolean glow) {
        int W = 12, H = 24;
        VoxModel m = new VoxModel(W, H, W);
        int cork = 0x8A6440, corkDk = 0x6A4A2A, core = mix(c, 0xFFFFFF, 0.75f), hi = mix(c, 0xFFFFFF, 0.35f), dk = sh(c, 0.55f), label = 0xE8DCB8;
        float cx = 5.5f;
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) for (int z = 0; z < W; z++) {
            float d = (float) Math.hypot(x - cx, z - cx);
            float r = glow ? (y < 15 ? 4.6f + (float) Math.sin(Math.PI * y / 15) * 0.9f : y < 18 ? 2.6f : 2.9f) : (y < 13 ? 5.2f : y < 16 ? 5.2f - (y - 13) * 1.0f : y < 20 ? 2.2f : 2.6f);
            if (d > r) continue;
            int col;
            if (y >= (glow ? 18 : 20)) col = y % 2 == 0 ? cork : corkDk;
            else if (glow) {
                // the light: white at its heart, the colour round it, brighter as it rises
                float heart = (float) Math.hypot(Math.hypot(x - cx, z - cx), (y - 7) * 0.8f);
                col = heart < 2.2f ? core : heart < 3.8f ? hi : y > 13 ? mix(c, 0xFFFFFF, 0.2f) : c;
                if (x < 3 && z > 4 && z < 8) col = 0xF8FCFF;   // the glint on the glass
            } else {
                boolean liquid = y < 12, glass = d > r - 0.9f;
                col = liquid ? (y >= 5 && y <= 8 && glass ? label : dk) : glass ? 0xB8C8CC : 0xD8E4E8;
                if (glass && x < 3 && z > 4 && z < 8 && !(y >= 5 && y <= 8)) col = 0xF0F6F8;
            }
            m.set(x, y, z, mat(col));
        }
        return new ItemModels.Model(m, cx, 0, cx);
    }

    private static int mix(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t), g = Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t), bl = Math.round((a & 255) * (1 - t) + (b & 255) * t);
        return r << 16 | g << 8 | bl;
    }

    /** A pelt laid out flat: the hide's outline with its four leg-flaps and a tail, the fur in tufted streaks along its length. */
    static ItemModels.Model pelt(int c, boolean silver) {
        int W = 34, H = 4, D = 40;
        VoxModel m = new VoxModel(W, H, D);
        int tip = silver ? 0xE6E6E2 : sh(c, 1.35f), dk = sh(c, 0.65f), skin = 0xD8C8A8;
        for (int x = 0; x < W; x++) for (int z = 0; z < D; z++) {
            float u = (x - 16.5f) / 10f, v = (z - 19.5f) / 15f;
            boolean body = u * u + v * v < 1;
            boolean leg = Math.abs(Math.abs(u) - 1.15f) < 0.35f && (Math.abs(v - 0.62f) < 0.12f || Math.abs(v + 0.55f) < 0.12f) && Math.abs(u) < 1.5f;
            boolean tail = Math.abs(u) < 0.14f && v > 0.95f && v < 1.3f;
            if (!body && !leg && !tail) continue;
            float n = hash(x, 0, z);
            boolean edge = !body;
            m.set(x, 0, z, mat(skin));
            int fur = edge ? dk : n < 0.18f ? tip : (z + (int) (n * 3)) % 4 == 0 ? dk : c;
            m.set(x, 1, z, mat(fur));
            if (!edge && n > 0.6f) m.set(x, 2, z, mat(n > 0.88f ? tip : c));       // the pile of the fur
            if (!edge && n > 0.93f) m.set(x, 3, z, mat(tip));
            if (silver && body && Math.abs(u) < 0.25f) m.set(x, 2, z, mat(0x9A9A96));  // the dark saddle
        }
        return new ItemModels.Model(m, W / 2f, 0, D / 2f);
    }

    /** A fang (ivory, curving to a point) or a claw (black, hooked). */
    static ItemModels.Model fang(int c, boolean claw) {
        int W = 10, H = 22, D = 6;
        VoxModel m = new VoxModel(W, H, D);
        int root = claw ? 0x6A5A4A : 0xC8B89A, tipC = claw ? 0x1A1614 : 0xFFFAF0;
        for (int y = 0; y < H; y++) {
            float t = y / (float) (H - 1);
            float r = 2.6f * (1 - t * 0.9f), off = (claw ? 4.5f : 2.5f) * t * t;
            for (int x = 0; x < W; x++) for (int z = 0; z < D; z++)
                if (Math.hypot(x - 3 - off, (z - 2.5f) * 1.2f) <= r) m.set(x, y, z, mat(y < 3 ? root : t > 0.75f ? tipC : c));
        }
        return new ItemModels.Model(m, 3, 0, 2.5f);
    }

    /** A small cloth pouch, open, its neck rolled down and the dust heaped up inside it, a pinch spilled at its foot. */
    static ItemModels.Model pouch(int c) {
        int W = 16, H = 16;
        VoxModel m = new VoxModel(W + 5, H, W);
        int cloth = 0xB8A47C, clothDk = 0x9A8660, roll = 0x8A7A5A, hi = sh(c, 1.25f), dk = sh(c, 0.8f);
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) for (int z = 0; z < W; z++) {
            float d = (float) Math.hypot(x - 7.5f, z - 7.5f);
            float r = y < 10 ? 5.0f + 2.2f * (float) Math.sin(Math.PI * (y + 1) / 12) : 6.4f;
            if (y < 12) {
                if (d > r) continue;
                boolean wall = d > r - 1.3f || y == 0;
                if (wall) m.set(x, y, z, mat(y >= 10 ? roll : (y % 3 == 0 ? clothDk : cloth)));
                else if (y >= 8) m.set(x, y, z, mat(hash(x, y, z) < 0.3f ? dk : c));
            } else {
                // the heap of dust standing up out of the neck
                float hr = 5.0f - (y - 12) * 1.4f;
                if (d <= hr) m.set(x, y, z, mat(hash(x, y, z) < 0.25f ? hi : hash(x, y, z) > 0.8f ? dk : c));
            }
        }
        for (int x = 13; x < W + 4; x++) for (int z = 4; z < 12; z++) if (Math.hypot(x - 16, z - 8) < 3.6f) m.set(x, 0, z, mat(hash(x, 0, z) < 0.5f ? c : dk));
        return new ItemModels.Model(m, 7.5f, 0, 7.5f);
    }

    /** A wing folded on itself: fingers of bone and the membrane between, the claw at its wrist. */
    static ItemModels.Model wing(int c) {
        int W = 34, H = 4, D = 20;
        VoxModel m = new VoxModel(W, H, D);
        int bone = sh(c, 0.6f), mem = c, memLt = sh(c, 1.25f);
        for (int x = 0; x < W; x++) for (int z = 0; z < D; z++) {
            float u = x / (float) W;
            float edge = 4 + 14 * (float) Math.sin(Math.PI * Math.min(1, u * 1.1f)) - 3 * (float) Math.abs(Math.sin(u * Math.PI * 4));
            if (z > edge) continue;
            boolean finger = Math.abs(z - u * 16) < 0.8f || Math.abs(z - u * 9) < 0.8f || z < 1.5f;
            m.set(x, 0, z, mat(finger ? bone : (x + z) % 7 == 0 ? memLt : mem));
            if (finger) m.set(x, 1, z, mat(bone));
        }
        m.set(0, 1, 0, mat(0x1A1614)); m.set(0, 2, 0, mat(0x1A1614));
        return new ItemModels.Model(m, W / 2f, 0, D / 2f);
    }

    /** A glossy eye: a white (or dark) ball, its iris in the item's colour, a pupil, a highlight. */
    static ItemModels.Model eye(int c) {
        int W = 16;
        VoxModel m = new VoxModel(W, W, W);
        boolean dark = ((c >> 16) & 255) + ((c >> 8) & 255) + (c & 255) < 120;
        int white = dark ? 0x1A1A20 : 0xF0E8DC, vein = 0xC04040;
        float cx = 7.5f;
        for (int y = 0; y < W; y++) for (int x = 0; x < W; x++) for (int z = 0; z < W; z++) {
            float dx = x - cx, dy = y - cx, dz = z - cx;
            if (dx * dx + dy * dy + dz * dz > 7.4f * 7.4f) continue;
            float front = (float) Math.hypot(dx, dy);
            int col = white;
            if (dz > 4) col = front < 1.8f ? 0x050505 : front < 4.4f ? (dark ? 0x6A1A8A : c) : white;
            else if (!dark && hash(x, y, z) < 0.06f) col = vein;
            if (dz > 4 && dx < -2 && dy > 2 && dy < 4) col = 0xFFFFFF;
            m.set(x, y, z, mat(col));
        }
        return new ItemModels.Model(m, cx, 0, cx);
    }

    /** A soft lump: rendered fat, a gland, a heart (lobed, with its vessels). */
    static ItemModels.Model gland(int c, boolean heart) {
        int W = 18, H = 16;
        VoxModel m = new VoxModel(W, H, W - 4);
        int hi = sh(c, 1.25f), dk = sh(c, 0.7f), vessel = 0x6A1A22;
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) for (int z = 0; z < W - 4; z++) {
            float dx = (x - 8.5f) / 8f, dy = (y - 7f) / 7f, dz = (z - 6.5f) / 6f;
            float lump = heart ? (float) (Math.min(Math.hypot((x - 6) / 5.2f, (y - 9) / 5.5f), Math.hypot((x - 11) / 5.2f, (y - 9) / 5.5f)) + Math.max(0, (6 - y) / 8f)) : dx * dx + dy * dy * 1.3f;
            if ((heart ? lump * lump : lump) + dz * dz > 1 + hash(x / 3, y / 3, z / 3) * 0.12f) continue;
            m.set(x, y, z, mat(dx < -0.3f && dy > 0 ? hi : hash(x, y, z) < 0.15f ? dk : c));
        }
        if (heart) for (int k = 0; k < 3; k++) for (int y = 13; y < 16; y++) m.set(7 + k * 2, y, 6, mat(vessel));
        return new ItemModels.Model(m, 8.5f, 0, 6.5f);
    }

    /** A trinket on its cord: each kind its own centrepiece (a tusk in gold wire, a horned figure, a disc, a shard). */
    static ItemModels.Model trinket(int c, String id) {
        int W = 22, H = 26, D = 6;
        VoxModel m = new VoxModel(W, H, D);
        int cord = 0x6A4A2A, gold = 0xD8A838, hi = sh(c, 1.4f), dk = sh(c, 0.65f);
        for (int i = 0; i <= 24; i++) { double t = Math.PI * i / 24; m.set((int) Math.round(10.5 + 8 * Math.cos(t)), 14 + (int) Math.round(10 * Math.sin(t)), 2, mat(cord)); }
        for (int y = 0; y < 15; y++) for (int x = 0; x < W; x++) for (int z = 0; z < D; z++) {
            float dx = x - 10.5f, dy = y - 7f, dz = z - 2.5f;
            boolean in;
            if (id.contains("clan") || id.contains("undead")) in = Math.abs(dx + dy * 0.3f) < 2.6f - dy * 0.12f && y < 14 && Math.abs(dz) < 1.8f;          // a tusk/badge
            else if (id.contains("imp")) in = Math.hypot(dx, (dy - 1) * 0.9f) < 4 || (y > 10 && Math.abs(Math.abs(dx) - 3) < 1 && Math.abs(dz) < 1);   // a horned head
            else if (id.contains("lunar") || id.contains("spirit") || id.contains("trinket")) in = Math.hypot(dx, dy) < 6 && Math.abs(dz) < 1.2f;             // a disc
            else in = Math.abs(dx) + Math.abs(dy) * 0.7f < 6 && Math.abs(dz) < 1.6f;                                                                         // a cut stone or shard
            if (!in) continue;
            int col = Math.hypot(dx, dy) > 5 ? gold : (dx < -1 && dy > 1) ? hi : (dx > 2 && dy < -1) ? dk : c;
            if (id.contains("lunar") && Math.hypot(dx + 1.5f, dy) < 3.6f && Math.hypot(dx - 0.2f, dy) > 3) col = hi;   // a crescent
            m.set(x, y, z, mat(col));
        }
        return new ItemModels.Model(m, 10.5f, 0, 2.5f);
    }

    /** A loop of glass beads on a thread. */
    static ItemModels.Model beads(int c) {
        int W = 22;
        VoxModel m = new VoxModel(W, 6, W);
        int[] cols = {c, sh(c, 1.3f), 0xE8D8A0, sh(c, 0.7f)};
        for (int i = 0; i < 14; i++) {
            double t = Math.PI * 2 * i / 14;
            int bx = (int) Math.round(10.5 + 8.5 * Math.cos(t)), bz = (int) Math.round(10.5 + 7 * Math.sin(t));
            for (int x = -1; x <= 1; x++) for (int y = 0; y < 3; y++) for (int z = -1; z <= 1; z++) if (Math.abs(x) + Math.abs(y - 1) + Math.abs(z) <= 2) m.set(bx + x, y, bz + z, mat(cols[i % 4]));
        }
        return new ItemModels.Model(m, 10.5f, 0, 10.5f);
    }

    /** A rolled scroll tied with a cord, its end curling open, a wax seal. */
    static ItemModels.Model scroll(int c) {
        int W = 10, H = 34, D = 10;
        VoxModel m = new VoxModel(W, H, D);
        int edge = sh(c, 0.8f), ink = 0x3A2A1A, wax = 0xA01818, cord = 0x6A4A2A;
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) for (int z = 0; z < D; z++) {
            float d = (float) Math.hypot(x - 4.5f, z - 4.5f);
            if (d > 4.2f || d < 1.2f && (y < 1 || y > H - 2)) continue;
            int col = d > 3.4f ? (y == 15 || y == 16 ? cord : (y % 4 == 0 && x > 4 ? ink : c)) : edge;
            m.set(x, y, z, mat(col));
        }
        for (int x = 3; x <= 6; x++) for (int y = 13; y <= 17; y++) m.set(x, y, 9, mat(wax));
        return new ItemModels.Model(m, 4.5f, 0, 4.5f);
    }

    /** A war banner: a shaft, a crossbar, the cloth hung from it with its device; a standard's cloth longer and torn. */
    static ItemModels.Model banner(int c, boolean standard) {
        int W = 24, H = standard ? 78 : 64, D = 4;
        VoxModel m = new VoxModel(W, H, D);
        int wood = 0x6A4A2A, metal = 0x8A8A8E, dev = standard ? 0x1A1A1A : 0xE8D8A0;
        for (int y = 0; y < H; y++) m.set(3, y, 2, mat(wood));
        m.set(3, H - 1, 2, mat(metal)); m.set(3, H - 2, 2, mat(metal));
        for (int x = 3; x < W - 1; x++) m.set(x, H - 6, 2, mat(wood));
        int top = H - 7, len = standard ? 34 : 26;
        for (int y = top - len; y <= top; y++) for (int x = 5; x < W - 2; x++) {
            boolean torn = y < top - len + 5 && ((x * 7 + y * 3) % 5 < 2);
            if (torn) continue;
            float u = (x - 13.5f) / 6f, v = (y - (top - len / 2f)) / 6f;
            boolean device = standard ? Math.abs(u) < 0.25f || Math.abs(v - 0.4f) < 0.25f && Math.abs(u) < 0.9f : u * u + v * v < 1 && !(Math.abs(v + 0.3f) < 0.18f && Math.abs(u) < 0.6f);
            m.set(x, y, 1, mat(device ? dev : (x + y) % 9 == 0 ? sh(c, 0.8f) : c));
        }
        return new ItemModels.Model(m, 3.5f, 0, 2);
    }

    /** A troll's totem: a gnawed thighbone hung with teeth on cords and a lump of flint bound at its head. */
    static ItemModels.Model totem(int c) {
        int W = 16, H = 54, D = 10;
        VoxModel m = new VoxModel(W, H, D);
        int bone = 0xD8CCB0, boneDk = 0xAA9C80, flint = 0x3A3A42, cord = 0x5A3A22, tooth = 0xF0E8D8;
        for (int y = 0; y < 46; y++) for (int x = 5; x < 11; x++) for (int z = 2; z < 8; z++) {
            float r = y < 6 || y > 40 ? 3.2f : 2.2f;
            if (Math.hypot(x - 7.5f, z - 4.5f) <= r) m.set(x, y, z, mat(hash(x, y, z) < 0.2f ? boneDk : bone));
        }
        for (int y = 44; y < 53; y++) for (int x = 3; x < 13; x++) for (int z = 1; z < 9; z++) if (Math.hypot((x - 7.5f) / 5, (y - 48.5f) / 4.5f) + Math.abs(z - 4.5f) / 6 < 1) m.set(x, y, z, mat(flint));
        for (int k = 0; k < 3; k++) { int tx = 2 + k * 5; for (int y = 30; y < 40; y++) m.set(tx, y, 4, mat(cord)); for (int y = 26; y < 30; y++) m.set(tx, y, 4, mat(tooth)); }
        return new ItemModels.Model(m, 7.5f, 0, 4.5f);
    }

    /** A little heap (ash, droppings): coarse at the rim, finer at its crest. */
    static ItemModels.Model ash(int c) {
        int W = 18, H = 7;
        VoxModel m = new VoxModel(W, H, W);
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) for (int z = 0; z < W; z++) {
            float d = (float) Math.hypot(x - 8.5f, z - 8.5f), r = 8.2f - y * 1.2f + hash(x, 0, z) * 1.4f;
            if (d <= r) m.set(x, y, z, mat(hash(x, y, z) < 0.3f ? sh(c, 0.7f) : hash(x, y, z) > 0.85f ? sh(c, 1.3f) : c));
        }
        return new ItemModels.Model(m, 8.5f, 0, 8.5f);
    }

    /** A spool of silk (threads wound in bands), or a bundle of web rolled tight with strands trailing. */
    static ItemModels.Model silk(int c, boolean web) {
        int W = web ? 22 : 14, H = web ? 14 : 18;
        VoxModel m = new VoxModel(W, H, W);
        int dk = sh(c, 0.82f), wood = 0x8A6440;
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) for (int z = 0; z < W; z++) {
            float d = (float) Math.hypot(x - W / 2f + 0.5f, z - W / 2f + 0.5f);
            if (web) {
                float r = 9 - Math.abs(y - 6.5f) * 0.6f + hash(x / 2, y, z / 2) * 1.5f;
                if (d <= r) m.set(x, y, z, mat((x + y * 2 + z) % 5 == 0 ? dk : c));
            } else {
                boolean flange = y < 2 || y > H - 3;
                if (flange ? d <= 6.5f : d <= 5.5f) m.set(x, y, z, mat(flange ? wood : (y % 3 == 0 ? dk : c)));
            }
        }
        return new ItemModels.Model(m, W / 2f, 0, W / 2f);
    }

    /** A curved plate of shell (carapace) or a vessel's shard: dark, its ridges catching the light. */
    static ItemModels.Model plate(int c) {
        int W = 28, H = 10, D = 26;
        VoxModel m = new VoxModel(W, H, D);
        int ridge = sh(c, 1.5f), dk = sh(c, 0.7f);
        for (int x = 0; x < W; x++) for (int z = 0; z < D; z++) {
            float u = (x - 13.5f) / 13.5f, v = (z - 12.5f) / 12.5f;
            if (u * u + v * v > 1) continue;
            int y = Math.round(6 * (1 - u * u));
            for (int k = Math.max(0, y - 2); k <= y; k++) m.set(x, k, z, mat(k < y ? dk : (z % 6 == 0 ? ridge : c)));
        }
        return new ItemModels.Model(m, 13.5f, 0, 12.5f);
    }

    /** A dragon's face-plate: a scaled brow, two swept horns, the eye sockets, the snout's ridge. */
    static ItemModels.Model visage(int c) {
        int W = 34, H = 26, D = 10;
        VoxModel m = new VoxModel(W, H, D);
        int scale = c, hi = sh(c, 1.35f), dk = sh(c, 0.55f), horn = 0xD8CCB0, hornDk = 0x8A7C60, socket = 0x1A0A0A;
        for (int y = 0; y < 18; y++) for (int x = 0; x < W; x++) {
            float u = (x - 16.5f) / 12f, v = (y - 9f) / 9f;
            if (Math.abs(u) > 1 - Math.max(0, -v) * 0.5f || Math.abs(v) > 1) continue;
            for (int z = 0; z < D; z++) {
                if (z > 6 - Math.abs(u) * 4) continue;
                int col = (x + (y / 2) * 3) % 4 == 0 ? dk : y > 12 ? hi : scale;
                if (Math.hypot(Math.abs(u) - 0.45f, v - 0.25f) < 0.18f && z > 3) col = socket;
                if (Math.abs(u) < 0.08f && y < 10) col = hi;
                m.set(x, y, z, mat(col));
            }
        }
        for (int s = -1; s <= 1; s += 2) for (int i = 0; i < 14; i++) {
            int x = Math.round(16.5f + s * (9 + i * 0.7f)), y = 14 + Math.round(i * 0.8f);
            for (int k = 0; k < 3 - i / 6; k++) for (int z = 2; z < 5; z++) m.set(x, y + k, z, mat(i > 9 ? hornDk : horn));
        }
        return new ItemModels.Model(m, 16.5f, 0, 5);
    }

    /** Bones of a beast (or what is left of the dead): two knuckled ends and a shaft, stained by its colour. */
    static ItemModels.Model bone(int c) {
        int W = 10, H = 32, D = 8;
        VoxModel m = new VoxModel(W, H, D);
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) for (int z = 0; z < D; z++) {
            float endT = Math.min(y, H - 1 - y);
            boolean in = endT < 5 ? Math.hypot((x - 2.5f) / 2.8f, (endT - 2.5f) / 3f) < 1 || Math.hypot((x - 6.5f) / 2.8f, (endT - 2.5f) / 3f) < 1
                    : Math.hypot((x - 4.5f) / 1.9f, (z - 3.5f) / 1.9f) < 1;
            if (in && Math.abs(z - 3.5f) < 3.5f) m.set(x, y, z, mat(hash(x, y, z) < 0.25f ? sh(c, 0.8f) : c));
        }
        return new ItemModels.Model(m, 4.5f, 0, 3.5f);
    }

    /** Folded cloth: a few layers, the weave showing; tattered cloth frayed and holed. */
    static ItemModels.Model cloth(int c, boolean tattered) {
        int W = 26, H = 6, D = 24;
        VoxModel m = new VoxModel(W, H, D);
        for (int y = 0; y < H; y++) for (int x = 0; x < W; x++) for (int z = 0; z < D; z++) {
            int inset = y / 2;
            if (x < inset || x >= W - inset || z < inset || z >= D - inset) continue;
            if (tattered && (y % 2 == 1) && hash(x, y, z) < 0.12f) continue;
            if (tattered && (x == inset || z == D - inset - 1) && hash(x, 0, z) < 0.5f) continue;
            m.set(x, y, z, mat(y % 2 == 1 ? ((x + z) % 2 == 0 ? c : sh(c, 0.88f)) : sh(c, 0.75f)));
        }
        return new ItemModels.Model(m, W / 2f, 0, D / 2f);
    }
}
