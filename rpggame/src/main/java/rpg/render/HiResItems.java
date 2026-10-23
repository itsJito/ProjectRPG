package rpg.render;

import astratto.render.Tex;
import astratto.voxel.VoxModel;
import rpg.item.ItemDef;
import rpg.item.Tier;

/**
 * The weapons, tools and shields at the game's texel density (gamefixes: every item remodelled, 64 x 64 - one voxel is
 * 1/64 m, the same as a texel of a block's 64 x 64 face). Real proportions: a sword's blade a hand's width and a finger
 * thick, fullered down its middle and bright along both edges; a grip wrapped in leather between a cross-guard and a
 * pommel; an axe's head a wedge that thins to its bit; a mace's flanged head; a pickaxe's curved, pointed bar on an
 * ash haft with an iron collar; a shield of planks with a metal rim and a domed boss. Each tier has its own metal (with
 * its true reflectance, not the dark colour the interface uses for it) and its own fittings - guard, pommel, wrap.
 *
 * <p>Axes as in {@link ItemModels}: y along the item, x across it, z through its thickness; the grip point is where the
 * hand closes (for a two-hander, the upper hand).
 */
final class HiResItems {
    /** Continuous tapered limbs at 64 voxels/metre, shared by held, dropped and inventory bows. */
    static ItemModels.Model bow(ItemDef d) { return bow(d, true); }

    static ItemModels.Model bow(ItemDef d, boolean string) {
        VoxModel m = new VoxModel(14, 82, 7);
        int wood = mat(d.color);
        for (int y = 0; y <= 80; y++) {
            float u = (y - 40) / 40f, centre = 3 + 7 * u * u;
            float radius = 1.2f + 0.4f * (1 - Math.abs(u));
            for (int x = 0; x < 14; x++) {
                if (Math.abs(x - centre) > radius) continue;
                int c = Math.abs(y - 40) <= 5 ? mat(y % 3 == 0 ? 0x3A261A : 0x604028)
                        : Math.abs(u) > 0.95f ? mat(0x483420) : (y / 5 + x * 3) % 7 == 0 ? sh(wood, 0.83f) : wood;
                m.set(x, y, 3, c);
            }
        }
        if (string) for (int y = 2; y <= 78; y++) m.set(10, y, 3, mat(0xE8E0C8));
        return new ItemModels.Model(m, 3.5f, 40.5f, 3.5f);
    }

    private HiResItems() { }

    /** A tier's fittings: blade metal (true reflectance), grip wrap, guard and pommel metal, the style number, a gem colour (0: none). */
    record Kit(int blade, int edge, int grip, int wrap, int guard, int pommel, int style, int gem) {}

    static int metal(int rgb) { return ItemModels.metal(rgb); }
    static int mat(int rgb) { return ItemModels.mat(rgb); }
    static int sh(int c, float f) { return ItemModels.sh(c, f); }

    static Kit kit(Tier t) {
        if (t == null) return new Kit(mat(0xA27A4C), mat(0xB88C58), mat(0x5A3A22), mat(0x4A2E1A), mat(0x6A4A2A), mat(0x5A3A20), 0, 0);
        return switch (t) {
            case LEATHER -> new Kit(mat(0xC8B48A), mat(0xE0D0A8), mat(0x5A3A22), mat(0x7A5634), mat(0x6A4A2A), mat(0x8A6A44), 0, 0);
            case CHAINMAIL -> new Kit(metal(0x9A9EA4), metal(0xC0C4C8), mat(0x3A2A1C), mat(0x5A4430), metal(0x70747A), metal(0x80848A), 2, 0);
            case BRONZE -> new Kit(metal(0xC88B4A), metal(0xE0A868), mat(0x5A3A22), mat(0x7A5634), metal(0xA86A32), metal(0xB0743A), 1, 0);
            case IRON -> new Kit(metal(0xA6A8AE), metal(0xC8CACE), mat(0x3A2A1C), mat(0x5A4430), metal(0x7A7A80), metal(0x86868C), 2, 0);
            case BLACK -> new Kit(metal(0x4C4C58), metal(0x8A8A98), mat(0x18161A), mat(0x2E2A30), metal(0x3A3A44), metal(0x50505C), 3, 0);
            case WHITE -> new Kit(metal(0xE6E4DC), metal(0xFAF8F0), mat(0xE8E4D8), mat(0xB8B0A0), metal(0xD0CCC0), metal(0xDCD8CC), 4, 0);
            case STEEL -> new Kit(metal(0xC6CDD6), metal(0xE6ECF2), mat(0x2E2A28), mat(0x4A2C20), metal(0x9AA2AC), metal(0xA8B0BA), 5, 0);
            case GRANITE -> new Kit(metal(0x9A948A), metal(0xC8C2B6), mat(0x3A322A), mat(0x5A4E40), metal(0x6A645C), metal(0x8A847C), 9, 0);
            case COBALT -> new Kit(metal(0x4A78D8), metal(0x96B8F8), mat(0x1A2A50), mat(0x2A4078), metal(0x2F55A8), metal(0x6A90E0), 6, 0xFF9AE0FF);
            case MOLTEN -> new Kit(metal(0xE07030), metal(0xFFC060), mat(0x2A1008), mat(0x5A2410), metal(0x8A3010), metal(0xF0A040), 8, 0xFFFF8020);
            case MALEVOLENT -> new Kit(metal(0x5A3A6A), metal(0xA070C0), mat(0x1A0A20), mat(0x3A1A44), metal(0x2A1A30), metal(0x6A3A80), 12, 0xFFC040FF);
            case DRAGON -> new Kit(metal(0xC8342E), metal(0xF07060), mat(0x3A0E0E), mat(0x5A1A14), metal(0xA02020), metal(0xD8B040), 10, 0xFFE02020);
            case GODLY -> new Kit(metal(0xF6EEC8), metal(0xFFFFF4), mat(0x5A2A7A), mat(0x7A44A0), metal(0xF0C850), metal(0xF0C850), 11, 0xFF40C0F0);
            default -> new Kit(metal(0xA0A0A4), metal(0xC0C0C4), mat(0x5A3A22), mat(0x6A4A2A), metal(0x808084), metal(0x808084), 0, 0);
        };
    }

    // ------------------------------------------------------------------ fittings
    /** A wrapped grip, two voxels square, from y0 for len: leather turns, darker and lighter by turns. */
    static void grip(VoxModel m, int cx, int cz, int y0, int len, Kit k) {
        for (int y = y0; y < y0 + len; y++) {
            int c = (y - y0) % 3 == 0 ? k.wrap() : k.grip();
            for (int x = 0; x < 2; x++) for (int z = 0; z < 2; z++) m.set(cx + x, y, cz + z, (x + z + y) % 2 == 0 ? c : sh(c, 0.9f));
        }
    }

    /** The pommel: a disc, a ball, a faceted cap - by tier - with the tier's gem set in it. */
    static void pommel(VoxModel m, int cx, int cz, int y0, Kit k) {
        int r = k.style() >= 5 ? 3 : 2;
        for (int y = y0; y < y0 + (k.style() >= 9 ? 4 : 3); y++) for (int x = -r + 1; x <= r; x++) for (int z = -r + 1; z <= r; z++) {
            float dx = x - 0.5f, dz = z - 0.5f, dy = y - y0 - 1;
            if (dx * dx + dz * dz + dy * dy * (k.style() == 7 ? 4 : 1) > r * r + 0.5f) continue;
            m.set(cx + x, y, cz + z, (x + y) % 3 == 0 ? sh(k.pommel(), 1.15f) : k.pommel());
        }
        if (k.gem() != 0) { m.set(cx, y0 + 1, cz - r + 1, k.gem()); m.set(cx + 1, y0 + 1, cz - r + 1, k.gem()); m.set(cx, y0 + 1, cz + r, k.gem()); m.set(cx + 1, y0 + 1, cz + r, k.gem()); }
    }

    /** The cross-guard, span voxels each side of the grip, each tier its own: straight, swept, curled, winged, a disc. */
    static void guard(VoxModel m, int cx, int cz, int y, int span, Kit k) {
        int c = k.guard();
        for (int x = -span; x <= span + 1; x++) for (int yy = 0; yy < 2; yy++) for (int z = -1; z <= 2; z++) {
            boolean core = Math.abs(x - 0.5f) < 2.5f;
            if (!core && (z == -1 || z == 2)) continue;
            m.set(cx + x, y + yy, cz + z, yy == 1 ? sh(c, 1.12f) : c);
        }
        int s = k.style();
        for (int side = -1; side <= 1; side += 2) {
            int ex = side < 0 ? cx - span : cx + span + 1;
            switch (s) {
                case 3 -> { for (int d = 1; d <= 3; d++) m.set(ex + side * (d / 2), y - d, cz, c); }                                  // Black: down-swept
                case 4, 6 -> { for (int d = 1; d <= 3; d++) { m.set(ex + side * (d / 3), y + 1 + d, cz, c); m.set(ex + side * (d / 3), y + 1 + d, cz + 1, c); } }  // up-swept
                case 8 -> { for (int d = 1; d <= 4; d++) m.set(ex + side * (d > 2 ? 1 : 0), y + 1 + d, cz, c); m.set(ex - side, y + 6, cz, c); }   // Thorium: curled
                case 10, 12 -> { for (int d = 1; d <= 5; d++) for (int z = 0; z < 2; z++) m.set(ex + side * d / 2, y + 1 + d, cz + z, d % 2 == 0 ? sh(c, 1.2f) : c); }  // Dragon: wings
                case 11 -> { for (int d = 1; d <= 3; d++) for (int z = 0; z < 2; z++) m.set(ex + side * d, y + (d == 3 ? 1 : 0), cz + z, c); m.set(ex + side * 3, y + 2, cz, k.gem()); }  // Godly: broad, jewelled
                default -> { m.set(ex, y + 2, cz, c); m.set(ex, y + 2, cz + 1, c); }                                                     // a knob at each end
            }
        }
        if (s == 7) for (int x = -3; x <= 4; x++) for (int z = -3; z <= 4; z++) { float dx = x - 0.5f, dz = z - 0.5f; if (dx * dx + dz * dz <= 14) m.set(cx + x, y, cz + z, c); }   // Titanium: a disc guard
    }

    /**
     * A blade from y0, len long: hw voxels each side of the centre line at the base, a point over the last tip voxels; two
     * thick in its middle and one at the edges; a fuller (a darker groove) down the middle for most of its length; the
     * edges bright where they are ground.
     */
    static void blade(VoxModel m, int cx, int cz, int y0, int len, float hw, int tip, Kit k, boolean fuller) {
        for (int i = 0; i < len; i++) {
            int y = y0 + i, left = len - i;
            float w = left > tip ? hw * (1 - 0.12f * i / (float) len) : hw * (left / (float) tip);
            if (k.style() == 9 && left > tip) w += (i % 6 < 3 ? 0.4f : 0);                         // Granite: stepped, hewn
            int iw = Math.max(0, Math.round(w));
            for (int x = -iw; x <= iw + 1; x++) {
                float off = Math.abs(x - 0.5f);
                boolean edge = off >= iw - 0.01f;
                for (int z = 0; z < 2; z++) {
                    if (edge && z == 1 && iw > 1) continue;                                          // the edge is ground thin
                    int c = edge ? k.edge() : k.blade();
                    if (fuller && !edge && off < 0.6f && i > 2 && left > tip + 3) c = sh(k.blade(), 0.72f);
                    if (k.style() == 11 && !edge && off < 0.6f && i % 7 == 3 && left > tip + 2) c = metal(0xF0C850);   // Godly: gold inlay
                    m.set(cx + x, y, cz + z, c);
                }
            }
            if ((k.style() == 10 || k.style() == 12) && left > tip && i % 5 == 2) { m.set(cx - iw - 1, y, cz, k.edge()); m.set(cx + iw + 2, y, cz, k.edge()); }   // Dragon: barbed
        }
    }

    /** A wooden haft, two voxels square with a little grain, from y0 for len. */
    static void haft(VoxModel m, int cx, int cz, int y0, int len, int wood) {
        for (int y = y0; y < y0 + len; y++) for (int x = 0; x < 2; x++) for (int z = 0; z < 2; z++) {
            int grain = (y * 7 + x * 3) % 11 == 0 ? 1 : 0;
            m.set(cx + x, y, cz + z, grain == 1 ? sh(wood, 0.82f) : (x + z) % 2 == 0 ? wood : sh(wood, 0.93f));
        }
    }

    // ------------------------------------------------------------------ the families
    static ItemModels.Model sword(ItemDef d, Tier t) {
        Kit k = kit(t);
        boolean great = d.twoHanded();
        int bladeLen = Math.round(d.reach * (great ? 0.72f : 0.66f) * 64), grip = great ? 18 : 9, span = great ? 9 : 6;
        float hw = great ? 2.4f : 1.8f;
        int w = span * 2 + 12, cx = w / 2 - 1, cz = 2;
        int h = 4 + grip + 2 + bladeLen + 1;
        VoxModel m = new VoxModel(w, h, 6);
        if (t == null) k = new Kit(mat(0xA27A4C), mat(0xB88C58), mat(0x5A3A22), mat(0x4A2E1A), mat(0x7A5432), mat(0x6A4A2A), 0, 0);
        pommel(m, cx, cz, 0, k);
        grip(m, cx, cz, 3, grip, k);
        guard(m, cx, cz, 3 + grip, span, k);
        blade(m, cx, cz, 5 + grip, bladeLen, hw, great ? 12 : 9, k, t != null);
        // a two-hander's ricasso: a leather-wrapped stretch of blade above the guard for the second hand
        if (great) for (int y = 5 + grip; y < 5 + grip + 6; y++) for (int x = 0; x < 2; x++) m.set(cx + x, y, cz - 1, k.wrap());
        return new ItemModels.Model(m, cx + 1, great ? 3 + grip - 4 : 3 + grip / 2f, cz + 1);
    }

    static ItemModels.Model dagger(ItemDef d, Tier t) {
        Kit k = kit(t);
        int bladeLen = 18, grip = 7, span = 3, w = 14, cx = 6, cz = 2;
        VoxModel m = new VoxModel(w, 3 + grip + 2 + bladeLen + 1, 6);
        pommel(m, cx, cz, 0, k);
        grip(m, cx, cz, 3, grip, k);
        guard(m, cx, cz, 3 + grip, span, k);
        blade(m, cx, cz, 5 + grip, bladeLen, 1.4f, 7, k, false);
        return new ItemModels.Model(m, cx + 1, 3 + grip / 2f, cz + 1);
    }

    /** An axe: a woodcutter's one-handed hatchet (a short haft, a wedge head), or a two-handed battleaxe (long haft, a bearded crescent - double for the late tiers). */
    static ItemModels.Model axe(ItemDef d, Tier t) {
        Kit k = kit(t);
        boolean battle = d.twoHanded();
        int haftLen = battle ? 72 : 38, headH = battle ? 22 : 13, out = battle ? 15 : 10, back = battle ? 4 : 5;
        boolean dbl = battle && k.style() >= 8;
        int w = out * 2 + 8, cx = w / 2 - 1, cz = 2;
        VoxModel m = new VoxModel(w, haftLen + 4, 6);
        haft(m, cx, cz, 0, haftLen, mat(battle ? 0x5A3C22 : 0x8A6236));
        // the grip end: a wrap and a cap
        for (int y = 0; y < (battle ? 14 : 9); y++) for (int x = 0; x < 2; x++) for (int z = 0; z < 2; z++) m.set(cx + x, y, cz + z, y % 3 == 0 ? k.wrap() : k.grip());
        for (int x = -1; x <= 2; x++) for (int z = -1; z <= 2; z++) m.set(cx + x, 0, cz + z, k.pommel());
        int top = haftLen - 2;
        // the eye: a metal collar round the haft where the head sits
        for (int y = top - headH + 2; y <= top + 1; y++) for (int x = -1; x <= 2; x++) for (int z = -1; z <= 2; z++) m.set(cx + x, y, cz + z, sh(k.blade(), 0.85f));
        for (int sd = (dbl ? -1 : 1); sd <= 1; sd += 2) {
            for (int j = 0; j < headH; j++) {
                int y = top - j;
                float u = j / (float) (headH - 1);
                // the bit flares top and bottom (a beard for the battleaxe), the cheek narrow by the haft
                float flare = battle ? 0.35f + 0.65f * (float) Math.pow(Math.abs(u - 0.45f) / 0.55f, 1.4f) : 0.55f + 0.45f * (float) Math.abs(u - 0.5f) * 2;
                int reach = Math.round(3 + (out - 3) * Math.max(flare, j > 2 && j < headH - 3 ? 0.55f : 0));
                for (int x = 3; x <= reach + 2; x++) {
                    int px = sd > 0 ? cx + x : cx + 1 - x;
                    float toEdge = (reach + 2 - x) / (float) reach;
                    int thick = toEdge > 0.6f ? 3 : toEdge > 0.25f ? 2 : 1;
                    boolean edge = x >= reach + 1;
                    for (int z = 0; z < thick; z++) m.set(px, y, cz + (thick == 1 ? 0 : z - (thick == 3 ? 0 : 0)), edge ? k.edge() : (x + j) % 7 == 0 ? sh(k.blade(), 0.9f) : k.blade());
                }
            }
        }
        // the poll: a hammer-face behind a hatchet's eye, a spike behind a single battleaxe
        if (!dbl) for (int j = 3; j < headH - 3; j++) for (int x = 1; x <= back; x++) for (int z = 0; z < 2; z++) m.set(cx + 1 - x - 1, top - j, cz + z, battle && x == back ? k.edge() : sh(k.blade(), 0.92f));
        if (k.gem() != 0) m.set(cx, top - headH / 2, cz - 1, k.gem());
        return new ItemModels.Model(m, cx + 1, battle ? 10 : 5, cz + 1);
    }

    static ItemModels.Model mace(ItemDef d, Tier t) {
        Kit k = kit(t);
        int haftLen = 44, r = 6 + Math.min(2, (t == null ? 1 : t.power) / 5);
        int w = r * 2 + 6, cx = w / 2 - 1, cz = w / 2 - 1;
        VoxModel m = new VoxModel(w, haftLen + r * 2 + 4, w);
        haft(m, cx, cz, 0, haftLen, mat(0x6A4628));
        for (int y = 0; y < 12; y++) for (int x = 0; x < 2; x++) for (int z = 0; z < 2; z++) m.set(cx + x, y, cz + z, y % 3 == 0 ? k.wrap() : k.grip());
        pommel(m, cx, cz, 0, k);
        int hy = haftLen + r - 1;
        // the head: a core and six flanges standing out from it, each thinning to its edge
        for (int y = hy - r; y <= hy + r; y++) for (int x = -r; x <= r + 1; x++) for (int z = -r; z <= r + 1; z++) {
            float dx = x - 0.5f, dz = z - 0.5f, dy = y - hy;
            float core = dx * dx + dz * dz + dy * dy * 0.6f;
            double a = Math.atan2(dz, dx), seg = Math.PI / 3;
            double fa = Math.abs(((a % seg) + seg) % seg - seg / 2);
            boolean flange = fa > seg / 2 - 0.28 && Math.abs(dy) < r - 1 && dx * dx + dz * dz <= (r + 0.5f) * (r + 0.5f);
            if (core <= (r * 0.55f) * (r * 0.55f)) m.set(cx + x, y, cz + z, sh(k.blade(), 0.88f));
            else if (flange) m.set(cx + x, y, cz + z, dx * dx + dz * dz > (r - 1.5f) * (r - 1.5f) ? k.edge() : k.blade());
        }
        // the cap, and spikes for the dark and the late tiers
        for (int x = -1; x <= 2; x++) for (int z = -1; z <= 2; z++) m.set(cx + x, hy + r + 1, cz + z, k.pommel());
        if (k.style() == 3 || k.style() >= 9) for (int y = hy - 2; y <= hy + 2; y += 4) for (int s2 = 0; s2 < 4; s2++) {
            int ox = s2 == 0 ? r + 2 : s2 == 1 ? -r - 1 : 0, oz = s2 == 2 ? r + 2 : s2 == 3 ? -r - 1 : 0;
            m.set(cx + (ox == 0 ? 0 : ox), y, cz + (oz == 0 ? 0 : oz), k.edge());
        }
        return new ItemModels.Model(m, cx + 1, 6, cz + 1);
    }

    static ItemModels.Model pickaxe(ItemDef d, Tier t) {
        Kit k = kit(t);
        int haftLen = 52, span = 18 + Math.min(4, (t == null ? 1 : t.power) / 3);
        int w = span * 2 + 6, cx = w / 2 - 1, cz = 2;
        VoxModel m = new VoxModel(w, haftLen + 8, 6);
        haft(m, cx, cz, 0, haftLen, mat(0x8E6A3E));
        for (int y = 0; y < 10; y++) for (int x = 0; x < 2; x++) for (int z = 0; z < 2; z++) m.set(cx + x, y, cz + z, y % 3 == 0 ? k.wrap() : k.grip());
        int top = haftLen - 1;
        // the collar
        for (int y = top - 5; y <= top + 3; y++) for (int x = -1; x <= 2; x++) for (int z = -1; z <= 2; z++) m.set(cx + x, y, cz + z, sh(k.blade(), 0.8f));
        // the bar: thick at the eye, curving down and thinning to a point at each end
        for (int x = -span; x <= span + 1; x++) {
            float u = Math.abs(x - 0.5f) / (span + 0.5f);
            int droop = Math.round(u * u * 6), thick = u < 0.4f ? 4 : u < 0.75f ? 3 : u < 0.92f ? 2 : 1;
            for (int j = 0; j < thick; j++) for (int z = 0; z < (thick >= 3 ? 2 : 1); z++)
                m.set(cx + x, top - droop - j + 2, cz + z, u > 0.88f ? k.edge() : (x + j) % 6 == 0 ? sh(k.blade(), 0.9f) : k.blade());
        }
        if (k.gem() != 0) m.set(cx, top + 2, cz - 1, k.gem());
        return new ItemModels.Model(m, cx + 1, 5, cz + 1);
    }

    /**
     * A shield: the wooden one a round targe of planks, iron-rimmed; the metal tiers a heater of boards faced with their
     * metal, rimmed, a domed boss in the middle and the tier's charge painted across it (a chevron, a bend, a cross).
     */
    static ItemModels.Model shield(ItemDef d, Tier t) {
        Kit k = kit(t);
        if (t == null) {
            int r = 22, n = r * 2 + 1;
            VoxModel m = new VoxModel(n, n, 6);
            for (int x = 0; x < n; x++) for (int y = 0; y < n; y++) {
                float dx = x - r, dy = y - r, rr = (float) Math.sqrt(dx * dx + dy * dy);
                if (rr > r + 0.4f) continue;
                boolean rim = rr > r - 2.2f;
                int plank = (x + 2) / 7, c = rim ? metal(0x8A8A90) : plank % 2 == 0 ? mat(0x9A6E3C) : mat(0x86602F);
                if (!rim && (x + 2) % 7 == 0) c = mat(0x5A3E20);                     // the joins between the boards
                if (!rim && (y * 5 + plank * 13) % 23 == 0) c = sh(c, 0.85f);         // grain
                m.set(x, y, 2, c); m.set(x, y, 3, sh(c, 0.9f));
                if (rim) m.set(x, y, 1, metal(0x9A9AA0));
                float b = dx * dx + dy * dy;
                if (b <= 20) { m.set(x, y, 1, metal(0x9C9CA2)); if (b <= 9) m.set(x, y, 0, metal(0xB0B0B6)); }
                if (Math.abs(dx) <= 0.5f && Math.abs(dy) < r - 3 && Math.abs(dy) > 4 || Math.abs(dy) <= 0.5f && Math.abs(dx) < r - 3 && Math.abs(dx) > 4) m.set(x, y, 1, metal(0x7A7A80));   // iron bands
            }
            return new ItemModels.Model(m, r, r, 4);
        }
        int w = 40, h = 52, cxw = w / 2;
        VoxModel m = new VoxModel(w + 1, h + 1, 6);
        int face = k.blade(), rim = k.guard(), charge = switch (k.style()) { case 10 -> mat(0xE8C040); case 11 -> mat(0x6A3AA0); case 3 -> mat(0x8A1A1A); case 6 -> mat(0xE8E8F0); default -> mat(0x7A1E1E); };
        for (int x = 0; x <= w; x++) for (int y = 0; y <= h; y++) {
            // the heater's outline: straight sides for the top two thirds, curving in to a point at the foot
            float u = (x - cxw) / (float) cxw, v = y / (float) h;
            float half = v > 0.36f ? 1 : (float) Math.sqrt(Math.max(0, 1 - Math.pow((0.36f - v) / 0.36f, 2)));
            if (Math.abs(u) > half + 0.01f) continue;
            boolean edge = Math.abs(u) > half - 0.07f || y >= h - 1;
            int c = face;
            // the charge: by tier, painted on the metal
            boolean paint = switch (k.style()) {
                case 2, 5, 7 -> Math.abs(Math.abs(u) - (1 - v) * 0.9f) < 0.12f && v > 0.25f;           // a chevron
                case 3, 9 -> Math.abs(u + (v - 0.5f) * 1.2f) < 0.13f;                                       // a bend
                case 6, 8, 11 -> Math.abs(u) < 0.1f || Math.abs(v - 0.62f) < 0.07f;                         // a cross
                case 10 -> Math.abs(u) < 0.42f && Math.abs(v - 0.62f) < 0.22f && (x + y) % 2 == 0;          // a scaled field
                default -> Math.abs(v - 0.62f) < 0.08f;                                                      // a fess
            };
            if (paint) c = charge;
            if (edge) c = rim;
            m.set(x, y, 2, c); m.set(x, y, 3, mat(0x6A4A2A));                                               // face, and the boards behind
            if (edge) m.set(x, y, 1, sh(rim, 1.12f));
            float bx = x - cxw, by = y - h * 0.62f, b = bx * bx + by * by;
            if (b <= 26) { m.set(x, y, 1, k.pommel()); if (b <= 12) m.set(x, y, 0, sh(k.pommel(), 1.15f)); }
            if ((x == 3 || x == w - 3) && y % 8 == 4 && v > 0.4f) m.set(x, y, 1, k.pommel());              // rivets
        }
        if (k.gem() != 0) m.set(cxw, Math.round(h * 0.62f), 0, k.gem());
        return new ItemModels.Model(m, cxw, h * 0.55f, 4);
    }


    // ------------------------------------------------------------------ armour
    /** What an armour tier is made of: boiled leather, riveted mail, or plate in its metal. */
    enum Stuff { LEATHER, MAIL, PLATE }
    static Stuff stuff(Tier t) { return t == null || t == Tier.LEATHER ? Stuff.LEATHER : t == Tier.CHAINMAIL ? Stuff.MAIL : Stuff.PLATE; }

    /** The surface of a piece of armour at a voxel: leather with seams and stitches; mail's rings; plate with a sheen and rivets along its edges. */
    static int skin(Tier t, Kit k, int x, int y, int z, boolean edge) {
        switch (stuff(t)) {
            case LEATHER -> {
                int base = 0xFF8A5A2E;
                if (edge) return mat(0x5A3618);
                if ((x + y * 3) % 9 == 0) return mat(0xC8A070);
                return sh(base, 0.92f + ((x * 7 + y * 13 + z * 5) % 9) * 0.02f);
            }
            case MAIL -> {
                boolean ring = (x + y) % 2 == 0;
                return metal(edge ? 0x70747A : ring ? 0xB4B8BE : 0x80848A);
            }
            default -> {
                if (edge) return k.guard();
                return (x + y) % 11 == 0 ? k.edge() : k.blade();
            }
        }
    }

    /**
     * A helm: a leather cap with ear flaps; a mail coif; or plate - a nasal helm (Bronze, Iron), a great helm with a
     * visor slit and breaths (Black to Steel), a crested armet (Mithril to Carbide), the horned Dragon helm, the crowned
     * Godly one.
     */
    static ItemModels.Model helm(ItemDef d, Tier t) {
        Kit k = kit(t);
        Stuff st = stuff(t);
        int n = 22, c = n / 2;
        VoxModel m = new VoxModel(n + 2, n + 6, n + 2);
        int style = t == null ? 0 : k.style();
        for (int x = 0; x < n; x++) for (int y = 0; y < n; y++) for (int z = 0; z < n; z++) {
            float dx = x - c + 0.5f, dy = Math.max(0, y - c * 0.8f), dz = z - c + 0.5f;
            float r = (float) Math.sqrt(dx * dx + dy * dy * 1.15f + dz * dz);
            if (r > c - 0.2f || r < c - 2.2f) continue;
            if (y < 2) continue;
            boolean front = dz > c * 0.55f;
            if (front) {
                if (st != Stuff.PLATE || style <= 2) { if (y < c + 1 && Math.abs(dx) < c * 0.62f && !(st == Stuff.PLATE && Math.abs(dx) < 1.2f && y > 4)) continue; }
                else if (y >= c - 2 && y <= c && Math.abs(dx) < c * 0.7f && Math.abs(dx) > 0.8f) continue;
                else if (y < c - 3 && y > 4 && Math.abs(dx) < c * 0.5f && (x + y) % 3 == 0) continue;
            }
            boolean rim = y <= 3 || r > c - 0.9f && y == c;
            m.set(x, y, z, skin(t, k, x, y, z, rim));
        }
        if (st == Stuff.LEATHER) for (int y = 0; y < 6; y++) for (int z = c - 3; z < c + 3; z++) { m.set(1, y, z, skin(t, k, 1, y, z, y == 0)); m.set(n - 2, y, z, skin(t, k, n - 2, y, z, y == 0)); }
        if (st == Stuff.MAIL) for (int y = 0; y < 3; y++) for (int x = 1; x < n - 1; x++) for (int z = 1; z < n - 1; z++) { float dx = x - c + 0.5f, dz = z - c + 0.5f; float r = (float) Math.sqrt(dx * dx + dz * dz); if (r < c + 0.4f && r > c - 1.6f && dz < c * 0.5f) m.set(x, y, z, skin(t, k, x, y, z, false)); }
        if (st == Stuff.PLATE) for (int z = 2; z < n - 2; z++) m.set(c, n - 1 - Math.abs(z - c) / 4, z, sh(k.blade(), 1.15f));
        if (style >= 6 && style <= 9) for (int z = 3; z < n - 6; z++) for (int y = 0; y < 4 - Math.abs(z - c + 2) / 3; y++) m.set(c, n + y, z, mat(style == 8 ? 0x2E8A4A : 0xB02020));
        if (style == 10) for (int sd = -1; sd <= 1; sd += 2) for (int i = 0; i < 8; i++) for (int j = 0; j < 2; j++) m.set(c + sd * (c - 2 + i / 2), n - 6 + i, c - 2 + j, mat(0xE8D8B0));
        if (style == 11) for (int x = 2; x < n - 2; x++) for (int z = 2; z < n - 2; z++) { float dx = x - c + 0.5f, dz = z - c + 0.5f, r = (float) Math.sqrt(dx * dx + dz * dz); if (r > c - 3.5f && r < c - 2) { m.set(x, n - 4, z, metal(0xF0C850)); if ((x + z) % 4 == 0) m.set(x, n - 3, z, metal(0xF0C850)); } }
        if (k.gem() != 0 && st == Stuff.PLATE) m.set(c, c + 3, n - 1, k.gem());
        return new ItemModels.Model(m, c, 0.5f, c);
    }

    /** A body: a leather jerkin (seams, a belt, lacing); a mail hauberk with short sleeves; or a plate cuirass - breastplate with its keel, pauldrons, fauld lames. */
    static ItemModels.Model body(ItemDef d, Tier t) {
        Kit k = kit(t);
        Stuff st = stuff(t);
        int w = 36, h = 36, dd = 18;
        VoxModel m = new VoxModel(w, h, dd);
        int cx = w / 2;
        for (int y = 0; y < h - 4; y++) for (int x = 6; x < w - 6; x++) for (int z = 0; z < dd; z++) {
            float u = (x - cx + 0.5f) / (w / 2f - 6), v = (z - dd / 2f + 0.5f) / (dd / 2f);
            float waist = y < 10 ? 0.86f + y * 0.014f : 1;
            float rr = u * u / (waist * waist) + v * v;
            if (rr > 1 || rr < 0.66f) continue;
            if (y > h - 9 && Math.abs(u) < 0.32f && v > -0.2f) continue;
            boolean edge = y == 0 || y == h - 5;
            m.set(x, y, z, skin(t, k, x, y, z, edge));
        }
        for (int sd = 0; sd < 2; sd++) {
            int x0 = sd == 0 ? 0 : w - 9;
            for (int y = h - 12; y < h - 2; y++) for (int x = x0; x < x0 + 9; x++) for (int z = 2; z < dd - 2; z++) {
                float px = (x - x0 - 4f) / 4.5f, py = (y - (h - 7f)) / 5f, pz = (z - dd / 2f + 0.5f) / (dd / 2f - 2);
                float q = px * px + py * py + pz * pz;
                if (q > 1 || q < 0.55f) continue;
                if (st == Stuff.PLATE ? py < -0.2f : py < -0.6f) continue;
                m.set(x, y, z, skin(t, k, x, y, z, py < -0.1f && st == Stuff.PLATE));
            }
        }
        int fz = dd - 1;
        if (st == Stuff.PLATE) {
            for (int y = 10; y < h - 8; y++) m.set(cx, y, fz, k.edge());
            for (int lame = 0; lame < 3; lame++) for (int x = 7; x < w - 7; x++) m.set(x, 2 + lame * 3, fz - (Math.abs(x - cx) > 8 ? 1 : 0), k.guard());
            for (int y = 12; y < h - 8; y += 4) { m.set(8, y, dd / 2 + 6, k.pommel()); m.set(w - 9, y, dd / 2 + 6, k.pommel()); }
            if (k.gem() != 0) m.set(cx, h - 12, fz, k.gem());
        } else if (st == Stuff.LEATHER) {
            for (int x = 7; x < w - 7; x++) for (int y = 6; y < 8; y++) m.set(x, y, fz - (Math.abs(x - cx) > 9 ? 2 : 0), mat(0x4A2C14));
            m.set(cx, 6, fz, metal(0xC8A050)); m.set(cx + 1, 6, fz, metal(0xC8A050));
            for (int y = 10; y < h - 8; y += 3) { m.set(cx - 1, y, fz, mat(0xD8C090)); m.set(cx + 1, y, fz, mat(0xD8C090)); }
        }
        return new ItemModels.Model(m, cx, 0.5f, dd / 2f);
    }

    /** Legs: leather trousers; mail chausses; or plate - cuisses over the thighs, knee cops, greaves down the shins. */
    static ItemModels.Model legs(ItemDef d, Tier t) {
        Kit k = kit(t);
        Stuff st = stuff(t);
        int w = 28, h = 46, dd = 14;
        VoxModel m = new VoxModel(w, h, dd);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) for (int z = 0; z < dd; z++) {
            boolean waist = y >= h - 8;
            int legC = x < w / 2 ? 7 : w - 8;
            float u = (x - legC + 0.5f) / 6.5f, v = (z - dd / 2f + 0.5f) / (dd / 2f);
            float uw = (x - w / 2f + 0.5f) / (w / 2f), rr = waist ? uw * uw + v * v : u * u + v * v;
            if (rr > 1 || rr < 0.6f) continue;
            if (!waist && Math.abs(x - w / 2f + 0.5f) < 1) continue;
            boolean edge = y == 0 || y == h - 1 || st == Stuff.PLATE && (y == 21 || y == 26);
            m.set(x, y, z, skin(t, k, x, y, z, edge));
        }
        if (st == Stuff.PLATE) for (int sd = 0; sd < 2; sd++) {
            int lc = sd == 0 ? 7 : w - 8;
            for (int x = lc - 3; x <= lc + 4; x++) for (int y = 21; y <= 26; y++) { float dx = x - lc - 0.5f, dy = y - 23.5f; if (dx * dx + dy * dy <= 13) m.set(x, y, dd - 1, dx * dx + dy * dy < 4 ? k.edge() : k.guard()); }
            for (int y = 2; y < 20; y++) m.set(lc, y, dd - 1, k.edge());
        }
        for (int x = 0; x < w; x++) m.set(x, h - 2, dd - 1, st == Stuff.LEATHER ? mat(0x4A2C14) : k.guard());
        return new ItemModels.Model(m, w / 2f, 0.5f, dd / 2f);
    }


    // ------------------------------------------------------------------ the skillcapes
    /** Each skill's cape: its body colour and its trim (gamefixes: the skillcapes, worn only at 100 in their skill). */
    static int[] capeColours(rpg.skill.Skill s) {
        return switch (s) {
            case ATTACK -> new int[]{0x8A1A1A, 0xE0C060}; case STRENGTH -> new int[]{0x2A7A34, 0xE0D8A0}; case DEFENSE -> new int[]{0x2A3E8A, 0xD8D8E0};
            case HEALTH -> new int[]{0xC8C8C8, 0xC02838}; case ARCHERY -> new int[]{0x3A5A22, 0xD8B060}; case MAGIC -> new int[]{0x2A2070, 0x6AA8F0};
            case FAITH -> new int[]{0xE8E4D4, 0x40A8B0}; case MINING -> new int[]{0x4A4E56, 0x78C0E0}; case SMITHING -> new int[]{0x2E2E30, 0xE07A30};
            case FISHING -> new int[]{0x2A5A8A, 0xE8D8A0}; case COOKING -> new int[]{0x7A2052, 0xF0E0C0}; case WOODCUTTING -> new int[]{0x5A7A2A, 0x8A5A2A};
            case FIREMAKING -> new int[]{0xC84A12, 0xF8D040}; case CRAFTING -> new int[]{0x7A5A30, 0xC8A060}; case AGILITY -> new int[]{0x22224E, 0xC0C0C8};
            case THIEVING -> new int[]{0x3A2048, 0xC8A0E0}; case HARVESTING -> new int[]{0x1E6A44, 0xE8C860}; case CONSTRUCTION -> new int[]{0x8A6E4E, 0xE8E0D0};
        };
    }

    /** The skill's own icon (the interface's), as pixels, for the emblem; null when it cannot be read. */
    static java.awt.image.BufferedImage icon(rpg.skill.Skill s) {
        try (var in = HiResItems.class.getResourceAsStream("/ui/icons/" + rpg.ui.Atlas.skill(s) + ".png")) {
            return in == null ? null : javax.imageio.ImageIO.read(in);
        } catch (java.io.IOException e) { return null; }
    }

    /**
     * A skillcape: a long cape in its skill's colours that widens to the hem, edged in its trim with a thin gold piping,
     * a high collar, and the skill's own icon stitched into the back as its emblem (the cape is the icon's frame).
     * The pivot is the middle of the collar, where it hangs from the shoulders.
     */
    static ItemModels.Model skillcape(rpg.skill.Skill s) {
        int[] col = capeColours(s);
        int body = mat(col[0]), trim = mat(col[1]), gold = mat(0xD8B040);
        int w = 44, h = 60, cx = w / 2;
        VoxModel m = new VoxModel(w, h + 4, 4);
        for (int y = 0; y < h; y++) {
            float v = y / (float) (h - 1);
            int half = Math.round(15 + (1 - v) * 6.5f);                      // narrow at the shoulders, full at the hem
            for (int x = cx - half; x < cx + half; x++) {
                int edgeDist = Math.min(Math.min(x - (cx - half), cx + half - 1 - x), y);
                int c = edgeDist < 2 ? trim : edgeDist == 2 ? gold : body;
                // the cloth falls in folds: soft vertical shading
                if (c == body) c = sh(body, 0.9f + 0.1f * (float) Math.cos((x - cx) * 0.55f));
                m.set(x, y, 1, c);
                m.set(x, y, 2, sh(c, 0.82f));
            }
        }
        // the collar
        for (int x = cx - 14; x < cx + 14; x++) for (int y = h - 3; y < h + 3; y++) m.set(x, y, 0, y >= h ? trim : sh(trim, 0.9f));
        // the emblem: the skill's icon, 20 x 20, stitched on the back in the upper middle
        java.awt.image.BufferedImage ic = icon(s);
        int es = 20, ex = cx - es / 2, ey = h - 30;
        for (int i = 0; i < es; i++) for (int j = 0; j < es; j++) {
            int px = 0;
            if (ic != null) px = ic.getRGB(Math.min(ic.getWidth() - 1, i * ic.getWidth() / es), Math.min(ic.getHeight() - 1, (es - 1 - j) * ic.getHeight() / es));
            boolean ring = Math.hypot(i - es / 2f + 0.5f, j - es / 2f + 0.5f) > es / 2f - 1.2f;
            if ((px >>> 24) > 100) m.set(ex + i, ey + j, 3, mat(px));
            else if (ring && Math.hypot(i - es / 2f + 0.5f, j - es / 2f + 0.5f) <= es / 2f) m.set(ex + i, ey + j, 3, gold);
            else if (Math.hypot(i - es / 2f + 0.5f, j - es / 2f + 0.5f) <= es / 2f) m.set(ex + i, ey + j, 3, sh(trim, 0.55f));
        }
        return new ItemModels.Model(m, cx, h, 1);
    }


    // ------------------------------------------------------------------ the rest of the thirteen, and the ranged five
    /** A longsword: the sword's build with a longer blade and a hand-and-a-half grip. */
    static ItemModels.Model longsword(ItemDef d, Tier t) {
        Kit k = kit(t);
        int bladeLen = Math.round(d.reach * 0.7f * 64), grip = 13, span = 7, w = span * 2 + 12, cx = w / 2 - 1, cz = 2;
        VoxModel m = new VoxModel(w, 4 + grip + 2 + bladeLen + 1, 6);
        pommel(m, cx, cz, 0, k);
        grip(m, cx, cz, 3, grip, k);
        guard(m, cx, cz, 3 + grip, span, k);
        blade(m, cx, cz, 5 + grip, bladeLen, 1.9f, 11, k, true);
        return new ItemModels.Model(m, cx + 1, 3 + grip * 0.65f, cz + 1);
    }

    /** A scimitar: a broad blade that curves back, widening toward its point, a small guard. */
    static ItemModels.Model scimitar(ItemDef d, Tier t) {
        Kit k = kit(t);
        int bladeLen = Math.round(d.reach * 0.66f * 64), grip = 9, w = 30, cx = 10, cz = 2;
        VoxModel m = new VoxModel(w, 4 + grip + 2 + bladeLen + 2, 6);
        pommel(m, cx, cz, 0, k);
        grip(m, cx, cz, 3, grip, k);
        guard(m, cx, cz, 3 + grip, 4, k);
        for (int i = 0; i < bladeLen; i++) {
            float u = i / (float) bladeLen;
            int bend = Math.round(u * u * 12);                                   // the curve, back toward the spine
            int width = Math.round(2 + 3 * (float) Math.sin(Math.PI * Math.min(1, u * 1.15f)));
            for (int x = 0; x < width; x++) for (int z = 0; z < 2; z++) {
                if (x == 0 && z == 1) continue;
                m.set(cx + 1 + bend - x, 5 + grip + i, cz + z, x == 0 ? k.edge() : k.blade());
            }
        }
        return new ItemModels.Model(m, cx + 1, 3 + grip / 2f, cz + 1);
    }

    /** A warhammer: a squared, faced head with a back spike on a stout haft. */
    static ItemModels.Model warhammer(ItemDef d, Tier t) {
        Kit k = kit(t);
        int haftLen = 46, w = 34, cx = w / 2 - 1, cz = 4;
        VoxModel m = new VoxModel(w, haftLen + 14, 10);
        haft(m, cx, cz, 0, haftLen, mat(0x5A3C22));
        for (int y = 0; y < 11; y++) for (int x = 0; x < 2; x++) for (int z = 0; z < 2; z++) m.set(cx + x, y, cz + z, y % 3 == 0 ? k.wrap() : k.grip());
        int hy = haftLen - 4;
        for (int y = hy; y < hy + 11; y++) for (int x = -6; x <= 7; x++) for (int z = -3; z <= 4; z++) {
            boolean face = x == 7 || x == -6;
            m.set(cx + x, y, cz + z, face ? k.edge() : (x + y) % 5 == 0 ? sh(k.blade(), 0.9f) : k.blade());
        }
        for (int i = 0; i < 9; i++) for (int y = hy + 3 + i / 3; y < hy + 8 - i / 3; y++) m.set(cx - 7 - i, y, cz + 0, k.edge());   // the spike
        for (int x = -1; x <= 2; x++) for (int z = -1; z <= 2; z++) m.set(cx + x, hy + 11, cz + z, k.pommel());
        return new ItemModels.Model(m, cx + 1, 6, cz + 1);
    }

    /** A spear: a long ash shaft, a leaf-shaped head on a socket, a butt cap. */
    static ItemModels.Model spear(ItemDef d, Tier t) {
        Kit k = kit(t);
        int shaft = Math.round(d.reach * 0.86f * 64), head = 18, cx = 4, cz = 2;
        VoxModel m = new VoxModel(10, shaft + head + 2, 6);
        haft(m, cx, cz, 0, shaft, mat(0x9A7444));
        for (int x = 0; x < 2; x++) for (int z = 0; z < 2; z++) { m.set(cx + x, 0, cz + z, k.pommel()); m.set(cx + x, 1, cz + z, k.pommel()); }
        for (int y = shaft - 4; y < shaft; y++) for (int x = -1; x <= 2; x++) for (int z = -1; z <= 2; z++) m.set(cx + x, y, cz + z, sh(k.blade(), 0.85f));
        blade(m, cx, cz, shaft, head, 2.2f, 8, k, false);
        return new ItemModels.Model(m, cx + 1, shaft * 0.42f, cz + 1);
    }

    /** Claws: a leather grip-strap over the knuckles carrying three curved blades. */
    static ItemModels.Model claws(ItemDef d, Tier t) {
        Kit k = kit(t);
        VoxModel m = new VoxModel(18, 30, 8);
        for (int y = 0; y < 10; y++) for (int x = 2; x < 16; x++) for (int z = 2; z < 6; z++) if (z == 2 || z == 5 || x == 2 || x == 15) m.set(x, y, z, y % 3 == 0 ? k.wrap() : k.grip());
        for (int x = 2; x < 16; x++) for (int z = 2; z < 6; z++) m.set(x, 10, z, k.guard());
        for (int b = 0; b < 3; b++) {
            int bx = 4 + b * 4;
            for (int i = 0; i < 18; i++) { int bend = i * i / 60; m.set(bx, 11 + i, 3 - bend + 2, i > 14 ? k.edge() : k.blade()); m.set(bx + 1, 11 + i, 3 - bend + 2, k.edge()); }
        }
        return new ItemModels.Model(m, 9, 5, 4);
    }

    /** A halberd: a long pole, an axe-blade on one side, a hook behind, a spike on top. */
    static ItemModels.Model halberd(ItemDef d, Tier t) {
        Kit k = kit(t);
        int pole = Math.round(d.reach * 0.82f * 64), w = 34, cx = 14, cz = 2;
        VoxModel m = new VoxModel(w, pole + 22, 6);
        haft(m, cx, cz, 0, pole, mat(0x6A4A2A));
        int top = pole - 2;
        for (int j = 0; j < 20; j++) {
            float u = j / 19f;
            int reach = Math.round(4 + 12 * (float) Math.sin(Math.PI * Math.min(1, u * 1.1f)));
            for (int x = 2; x <= reach + 1; x++) for (int z = 0; z < (x > reach - 2 ? 1 : 2); z++) m.set(cx + x, top - j, cz + z, x > reach ? k.edge() : k.blade());
        }
        for (int i = 0; i < 8; i++) m.set(cx - 1 - i, top - 4 - i / 2, cz, k.edge());                 // the hook
        blade(m, cx, cz, top, 18, 1.2f, 10, k, false);                                                // the spike
        return new ItemModels.Model(m, cx + 1, pole * 0.45f, cz + 1);
    }

    /** A crossbow: a wooden tiller with its stock, a metal prod across the front, the string drawn back to the nut. */
    static ItemModels.Model crossbow(ItemDef d, Tier t) {
        Kit k = kit(t);
        int w = 44, h = 40, cx = w / 2, cz = 2;
        VoxModel m = new VoxModel(w, h, 7);
        for (int y = 0; y < h - 4; y++) for (int x = -2; x <= 2; x++) for (int z = 0; z < 3; z++) m.set(cx + x, y, cz + z, (y + x) % 7 == 0 ? mat(0x6A4628) : mat(0x8A5E34));
        for (int y = 0; y < 10; y++) for (int x = -3; x <= 3; x++) for (int z = 0; z < 3; z++) m.set(cx + x, y, cz + z, mat(0x7A5430));   // the butt
        for (int x = -20; x <= 20; x++) { int bow = Math.abs(x) * Math.abs(x) / 50; m.set(cx + x, h - 4 - bow, cz + 1, Math.abs(x) > 17 ? k.edge() : k.blade()); m.set(cx + x, h - 5 - bow, cz + 1, k.blade()); }
        for (int x = -19; x <= 19; x++) m.set(cx + x, h - 14 + Math.abs(x) / 3, cz + 1, mat(0xE8E0C8));   // the string
        for (int x = -1; x <= 1; x++) m.set(cx + x, 16, cz + 3, k.pommel());                             // the trigger
        return new ItemModels.Model(m, cx, 14, cz + 1);
    }

    /** A thrown weapon as it is held: a dart (fins), a knife, a tomahawk (a small axe), a javelin (long and thin). */
    static ItemModels.Model thrown(ItemDef d, Tier t) {
        Kit k = kit(t);
        switch (d.thrown) {
            case "dart" -> {
                // a bundle of three: thin dark shafts, crossed white flights at the tail, a short tapered point
                VoxModel m = new VoxModel(16, 30, 10);
                for (int b = 0; b < 3; b++) {
                    int bx = 4 + b * 4, bz = 4 + (b % 2) * 2, off = b == 1 ? 2 : 0;
                    for (int y = 0; y < 20; y++) m.set(bx, y + off, bz, y < 7 ? mat(0x5A4030) : k.grip());
                    for (int y = 0; y < 6; y++) {
                        int w = y < 4 ? 2 : 1;
                        for (int q = 1; q <= w; q++) { int c = mat(y % 2 == 0 ? 0xF0EAE0 : 0xD8D0C4);
                            m.set(bx - q, y + off, bz, c); m.set(bx + q, y + off, bz, c); m.set(bx, y + off, bz - q, c); m.set(bx, y + off, bz + q, c); }
                    }
                    for (int y = 20; y < 26; y++) { m.set(bx, y + off, bz, y == 25 ? k.edge() : k.blade()); if (y < 22) { m.set(bx + 1, y + off, bz, k.blade()); m.set(bx, y + off, bz + 1, k.blade()); } }
                }
                return new ItemModels.Model(m, 8, 10, 5);
            }
            case "knife" -> {
                VoxModel m = new VoxModel(10, 24, 6);
                grip(m, 4, 2, 0, 8, k);
                blade(m, 4, 2, 8, 15, 1.2f, 6, k, false);
                return new ItemModels.Model(m, 5, 4, 3);
            }
            case "tomahawk" -> {
                // a short haft; a socketed head: a bearded blade flaring forward, a stubby spike behind
                VoxModel m = new VoxModel(26, 34, 6);
                haft(m, 10, 2, 0, 31, mat(0x8A6236));
                for (int y = 0; y < 5; y++) for (int z = 1; z < 4; z++) m.set(10, y + 1, z, mat(0x4A3424));   // the wrap
                for (int y = 23; y < 30; y++) for (int x = 9; x < 13; x++) m.set(x, y, 2, k.blade());          // the socket
                for (int x = 13; x < 22; x++) {
                    int f = x - 13;                       // flaring: a narrow neck, then the beard drops below
                    int top = 28 + (f > 5 ? 1 : 0), bot = 25 - (f * f) / 7;
                    for (int y = bot; y <= top; y++) m.set(x, y, 2, x >= 20 || y == bot ? k.edge() : k.blade());
                }
                for (int x = 5; x < 9; x++) for (int y = 26 - (8 - x) / 2; y <= 27; y++) m.set(x, y, 2, x == 5 ? k.edge() : k.blade());   // the spike
                return new ItemModels.Model(m, 11, 6, 3);
            }
            default -> {
                int len = 84;
                VoxModel m = new VoxModel(8, len + 12, 6);
                for (int y = 0; y < len; y++) { m.set(3, y, 2, (y / 6) % 2 == 0 ? mat(0xA27A4C) : mat(0x8E6A3E)); m.set(4, y, 2, mat(0x9A7444)); }
                blade(m, 3, 2, len, 12, 1.0f, 8, k, false);
                return new ItemModels.Model(m, 4, len * 0.45f, 3);
            }
        }
    }

    /** A drink in its vessel: wine in a dark bottle, a restorative in a round flask of pale glass. */
    static ItemModels.Model bottle(ItemDef d) {
        boolean wine = d.id.equals("religious_wine");
        VoxModel m = new VoxModel(14, 30, 14);
        int glass = mat(wine ? 0x3A1824 : 0xBFE6EC), glassHi = mat(wine ? 0x5C2A3A : 0xE6F6F8), liquid = mat(wine ? 0x5A0E22 : 0x2EB8D0),
            liqHi = mat(wine ? 0x7A1A34 : 0x6AD8E8), label = mat(0xE8DDC0), cork = mat(0x9A7048), wax = mat(0xA01828);
        for (int y = 0; y < 30; y++) for (int x = 0; x < 14; x++) for (int z = 0; z < 14; z++) {
            float dx = x - 6.5f, dz = z - 6.5f, r = (float) Math.sqrt(dx * dx + dz * dz);
            float want;
            if (wine) want = y < 15 ? 5.6f : y < 19 ? 5.6f - (y - 15) * 1.0f : y < 26 ? 1.6f : y < 29 ? 1.9f : 0;
            else { float cy = (y - 8.5f) / 8.5f; want = y < 17 ? 6.4f * (float) Math.sqrt(Math.max(0, 1 - cy * cy)) : y < 25 ? 1.7f : y < 28 ? 2.0f : 0; }
            if (r > want || want <= 0) continue;
            boolean shell = r > want - 1.1f || y == 0;
            boolean neck = wine ? y >= 19 : y >= 17;
            int c;
            if (neck && y >= (wine ? 25 : 24)) c = wine ? wax : cork;
            else if (wine && !neck && y >= 5 && y <= 11 && shell && dz > -2) c = y == 5 || y == 11 ? mat(0xC8A040) : label;
            else if (shell && !wine && y >= 1 && y <= 11) c = y == 11 ? mat(0x8EE4F0) : (dx < -2 && dz > 0) ? liqHi : liquid;   // the liquid, seen through clear glass
            else if (shell) c = (dx < -2 && dz > 0) ? glassHi : glass;
            else c = !wine && y > 11 ? glassHi : (dx < -1 ? liqHi : liquid);
            if (!shell && !(wine && !neck) && !( !wine && y <= 11)) continue;   // hollow where nothing fills it
            m.set(x, y, z, c);
        }
        return new ItemModels.Model(m, 7, 0.5f, 7);
    }

    // ------------------------------------------------------------------ jewellery (shown larger than life: a real ring is a voxel or two)
    /** A gold band standing on edge, its inner edge darker, a highlight round its upper left; set with a cut stone on top if it has one. */
    static ItemModels.Model ring(ItemDef d) {
        VoxModel m = new VoxModel(14, 16, 5);
        int gold = mat(0xE2B640), hi = mat(0xFFE58A), lo = mat(0x9C7420);
        float cx = 6.5f, cy = 6.5f;
        for (int y = 0; y < 14; y++) for (int x = 0; x < 14; x++) {
            float r = (float) Math.hypot(x - cx, y - cy);
            if (r > 6.4f || r < 4.4f) continue;
            for (int z = 1; z < 4; z++) {
                int c = r < 5.0f ? lo : (x < cx && y > cy && r > 5.6f) ? hi : gold;
                m.set(x, y, z, c);
            }
        }
        if (d.gemColor != 0) {
            int g = mat(d.gemColor), gh = mat(lighten(d.gemColor, 0.5f)), gd = mat(darken(d.gemColor, 0.6f));
            // the claw setting, then the stone: a faceted cushion, bright on its table
            for (int x = 4; x <= 9; x++) for (int z = 0; z < 5; z++) m.set(x, 12, z, gold);
            for (int y = 13; y <= 15; y++) for (int x = 4; x <= 9; x++) for (int z = 0; z < 5; z++) {
                boolean corner = (x == 4 || x == 9) && (z == 0 || z == 4) || y == 15 && (x == 4 || x == 9 || z == 0 || z == 4);
                if (corner) continue;
                m.set(x, y, z, y == 15 ? gh : (x == 4 || z == 0) ? gd : g);
            }
        }
        return new ItemModels.Model(m, 7, 0, 2);
    }

    /** A chain hanging in a loop (alternate links catching the light), with its pendant: a drop for a necklace, a round medallion for an amulet; the dodgy necklace is teeth on a cord. */
    static ItemModels.Model necklace(ItemDef d) {
        VoxModel m = new VoxModel(24, 30, 4);
        boolean dodgy = d.id.equals("dodgy_necklace"), amulet = d.id.endsWith("_amulet");
        int gold = mat(dodgy ? 0x6A4A2C : 0xE2B640), hi = mat(dodgy ? 0x8A6440 : 0xFFE58A);
        float cx = 11.5f, cy = 18f, a = 10f, b = 10.5f;
        for (int i = 0; i < 160; i++) {
            double t = i / 160.0 * Math.PI * 2;
            int x = Math.round(cx + (float) Math.cos(t) * a), y = Math.round(cy + (float) Math.sin(t) * b);
            if (y < 9 && Math.abs(x - cx) < 4) continue;        // the gap where the pendant hangs
            m.set(x, y, 1, (i / 4) % 2 == 0 ? gold : hi);
            if ((i / 4) % 2 == 0) m.set(x, y, 2, gold);
        }
        if (dodgy) {
            int tooth = mat(0xEDE6D0), shade = mat(0xC8BEA4);
            for (int k = 0; k < 5; k++) {
                double t = Math.PI * (1.25 + k * 0.125);
                int x = Math.round(cx + (float) Math.cos(t) * a), y = Math.round(cy + (float) Math.sin(t) * b);
                for (int j = 1; j <= 3; j++) { m.set(x, y - j, 1, j == 3 ? shade : tooth); m.set(x, y - j, 2, tooth); }
            }
            return new ItemModels.Model(m, 12, 0, 2);
        }
        int gem = d.gemColor != 0 ? mat(d.gemColor) : gold, gemHi = d.gemColor != 0 ? mat(lighten(d.gemColor, 0.5f)) : hi;
        if (amulet) {
            // a round medallion with a raised rim and the stone set in its heart
            for (int y = 0; y < 9; y++) for (int x = 7; x < 17; x++) {
                float r = (float) Math.hypot(x - 11.5f, y - 4.5f);
                if (r > 4.8f) continue;
                for (int z = 0; z < 4; z++) m.set(x, y, z, r > 3.9f ? hi : gold);
                if (r < 2.2f && d.gemColor != 0) { m.set(x, y, 0, r < 1.1f ? gemHi : gem); m.set(x, y, 3, gem); }
            }
            for (int y = 9; y < 11; y++) m.set(11, y, 1, gold);
        } else {
            // a teardrop pendant on a short bail
            for (int y = 2; y < 9; y++) for (int x = 9; x < 15; x++) {
                float w = y < 5 ? (y - 1) * 0.9f : (9 - y) * 0.75f;
                if (Math.abs(x - 11.5f) > w) continue;
                for (int z = 0; z < 4; z++) m.set(x, y, z, (x < 11 && y > 4) ? gemHi : gem);
            }
            for (int y = 9; y < 11; y++) m.set(11, y, 1, gold);
        }
        return new ItemModels.Model(m, 12, 0, 2);
    }

    /** A gem: a rough stone (lumpy, dull, its colour showing through grey) or a cut one (a faceted cushion with a bright table). */
    static ItemModels.Model gem(ItemDef d, boolean rough) {
        VoxModel m = new VoxModel(10, 8, 10);
        int col = d.color, c = mat(rough ? darken(col, 0.75f) : col), h = mat(lighten(col, 0.5f)), dk = mat(darken(col, 0.55f)), grey = mat(0x8A8680);
        for (int y = 0; y < 8; y++) for (int x = 0; x < 10; x++) for (int z = 0; z < 10; z++) {
            float dx = x - 4.5f, dz = z - 4.5f, dy = y - (rough ? 3.5f : 3f);
            if (rough) {
                long hh = (x * 73856093L) ^ (y * 19349663L) ^ (z * 83492791L);
                float lump = 4.2f + ((hh >>> 7) & 3) * 0.35f;
                if (Math.sqrt(dx * dx + dy * dy * 1.6 + dz * dz) > lump) continue;
                m.set(x, y, z, ((hh >>> 3) & 3) == 0 ? grey : ((hh >>> 5) & 1) == 0 ? c : dk);
            } else {
                // a cushion: the girdle widest at y 3, the pavilion tapering below, the crown stepping up to its table
                float w = y <= 3 ? 1.5f + y * 1.0f : 4.5f - (y - 3) * 0.9f;
                if (Math.abs(dx) + Math.abs(dz) * 0.9f > w + 1.2f || Math.max(Math.abs(dx), Math.abs(dz)) > w || y > 6) continue;
                m.set(x, y, z, y == 6 ? h : (dx < 0 && dz < 0) ? h : (dx > 0 && dz > 0) ? dk : c);
            }
        }
        return new ItemModels.Model(m, 5, 0, 5);
    }

    /** A jeweller's mould: a grey block in two halves with the shape cut into its face. */
    static ItemModels.Model mould(ItemDef d) {
        VoxModel m = new VoxModel(16, 6, 12);
        int body = mat(0x8A8680), seam = mat(0x5A5854), cut = mat(0x3A3836);
        for (int y = 0; y < 6; y++) for (int x = 0; x < 16; x++) for (int z = 0; z < 12; z++) m.set(x, y, z, y == 2 ? seam : body);
        boolean ring = d.id.startsWith("ring"), amulet = d.id.startsWith("amulet");
        for (int x = 3; x < 13; x++) for (int z = 2; z < 10; z++) {
            float r = (float) Math.hypot(x - 7.5f, z - 5.5f);
            boolean hole = ring ? r < 3.6f && r > 2.2f : amulet ? r < 3.4f || Math.abs(x - 7.5f) < 0.6f : Math.abs(r - 3.6f) < 0.6f || (z > 7 && Math.abs(x - 7.5f) < 1.2f);
            if (hole) m.set(x, 5, z, cut);
        }
        return new ItemModels.Model(m, 8, 0, 6);
    }

    /** A chisel: a turned wooden handle, a brass ferrule, a steel shank flattening to a bevelled edge. */
    static ItemModels.Model chisel() {
        VoxModel m = new VoxModel(5, 34, 5);
        int wood = mat(0x8A5A30), woodHi = mat(0xA87444), brass = mat(0xC8A040), steel = mat(0xA8ACB4), steelHi = mat(0xD6DAE0), edge = mat(0xE8ECF0);
        for (int y = 0; y < 34; y++) for (int x = 0; x < 5; x++) for (int z = 0; z < 5; z++) {
            float dx = x - 2, dz = z - 2, r = (float) Math.hypot(dx, dz);
            int c = -1;
            if (y < 14) { if (r <= (y < 2 || y > 11 ? 1.6f : 2.2f)) c = dx < 0 ? woodHi : wood; }
            else if (y < 16) { if (r <= 1.8f) c = brass; }
            else if (y < 31) { if (Math.abs(dx) <= 1 && Math.abs(dz) <= (y > 26 ? 0 : 1)) c = dx < 0 ? steelHi : steel; }
            else if (y < 33) { if (Math.abs(dx) <= 1 && dz == 0) c = edge; }
            if (c >= 0) m.set(x, y, z, c);
        }
        return new ItemModels.Model(m, 2, 0, 2);
    }

    /** A cast bar of metal: a trapezoid ingot, its top brighter. */
    static ItemModels.Model bar(int rgb) {
        VoxModel m = new VoxModel(18, 6, 9);
        int c = mat(rgb), h = mat(lighten(rgb, 0.4f)), dk = mat(darken(rgb, 0.7f));
        for (int y = 0; y < 6; y++) for (int x = 0; x < 18; x++) for (int z = 0; z < 9; z++) {
            int inset = y / 2;
            if (x < inset || x >= 18 - inset || z < inset / 2 || z >= 9 - inset / 2) continue;
            m.set(x, y, z, y == 5 ? h : (x == inset || z == inset / 2) ? dk : c);
        }
        return new ItemModels.Model(m, 9, 0, 4);
    }

    private static int lighten(int rgb, float t) {
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        return (int) (r + (255 - r) * t) << 16 | (int) (g + (255 - g) * t) << 8 | (int) (b + (255 - b) * t);
    }

    private static int darken(int rgb, float k) { return (int) (((rgb >> 16) & 255) * k) << 16 | (int) (((rgb >> 8) & 255) * k) << 8 | (int) ((rgb & 255) * k); }

    /** A bundle of bolts (or of brutal arrows): short, fletched, metal-headed. */
    static ItemModels.Model bolts(ItemDef d, Tier t) {
        Kit k = kit(t);
        VoxModel m = new VoxModel(14, 34, 8);
        for (int b = 0; b < 4; b++) {
            int bx = 2 + b * 3, bz = 2 + (b % 2) * 2;
            for (int y = 0; y < 26; y++) m.set(bx, y, bz, mat(0x9A7444));
            for (int y = 0; y < 6; y++) { m.set(bx - 1, y, bz, mat(0xD8D0C0)); m.set(bx + 1, y, bz, mat(0xD8D0C0)); }
            for (int y = 26; y < 31; y++) m.set(bx, y, bz, y == 30 ? k.edge() : k.blade());
            if (d.family == ItemDef.Family.ARROW) for (int x = -1; x <= 1; x++) m.set(bx + x, 29, bz, k.blade());   // a brutal arrow's blunt head
        }
        return new ItemModels.Model(m, 7, 16, 4);
    }

    // ------------------------------------------------------------------ the upsample for everything else
    /**
     * A model built at the old 1/32 m doubled to 1/64 m, its edges bevelled: every corner sub-voxel that sticks out where
     * two faces of the original voxel are open is shaved off, so blocky silhouettes come out rounded; and a texel-level
     * variation (a lighter top, a darker underside of each original voxel) keeps the finer surface from looking flat.
     */
    static ItemModels.Model doubled(ItemModels.Model in) {
        VoxModel a = in.vox();
        VoxModel m = new VoxModel(a.sx * 2, a.sy * 2, a.sz * 2);
        for (int x = 0; x < a.sx; x++) for (int y = 0; y < a.sy; y++) for (int z = 0; z < a.sz; z++) {
            int c = a.get(x, y, z);
            if (c == 0) continue;
            for (int i = 0; i < 2; i++) for (int j = 0; j < 2; j++) for (int l = 0; l < 2; l++) {
                int ox = i == 0 ? -1 : 1, oy = j == 0 ? -1 : 1, oz = l == 0 ? -1 : 1;
                int open = (a.get(x + ox, y, z) == 0 ? 1 : 0) + (a.get(x, y + oy, z) == 0 ? 1 : 0) + (a.get(x, y, z + oz) == 0 ? 1 : 0);
                if (open >= 3 && a.sz > 1 && a.sx > 1) continue;                       // a corner standing out on three sides
                int cc = j == 1 ? sh(c, 1.04f) : sh(c, 0.96f);
                m.set(x * 2 + i, y * 2 + j, z * 2 + l, (c & 0xFF000000) | (cc & 0xFFFFFF));
            }
        }
        return new ItemModels.Model(m, in.gx() * 2, in.gy() * 2, in.gz() * 2);
    }

    static int lum(int c) { return (Tex.r(c) * 3 + Tex.g(c) * 6 + Tex.b(c)) / 10; }
}
