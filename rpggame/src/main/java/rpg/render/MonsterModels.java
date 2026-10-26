package rpg.render;

import astratto.render.Tex;
import astratto.voxel.VoxModel;
import java.util.HashMap;
import java.util.Map;
import rpg.monster.MonsterKind;

/**
 * The eleven monsters as posable voxel figures (gamefixes: each its own design, at the sizes given). Every figure is
 * built from parts with a pivot where each joins - for the humanoids a torso, a head, upper and lower arms and legs
 * (the werewolf's legs bent back at the hock, the ghost's a robe), with their tails, wings and ears; for the beasts a
 * body, a head, and their legs or wings - so the renderer can walk, run, strike and ragdoll them joint by joint.
 * Colours follow the game's palette: flat, readable hues with a little texel-to-texel grain (WoW / OSRS).
 *
 * <p>Model axes: x to the figure's right, y up, -z forward. All lengths in voxels; {@link Fig#vs} is one voxel in
 * metres, chosen so the standing figure is the height the gamefixes gives.
 */
public final class MonsterModels {
    private MonsterModels() { }

    /** A built figure: its parts and the joints they hang from (voxels, in the torso's frame: from the pelvis). */
    public static final class Fig {
        public MonsterKind kind;
        /** One voxel in metres (the whole figure scales by it). */
        public float vs;
        // ---- humanoids
        public ItemModels.Model torso, head, armUp, armLo, legUp, legLo, foot, tail, wing, robe;
        /** Joints from the pelvis: the neck, the right shoulder (mirrored for the left), the right hip, the tail's root, the wings' root. */
        public float[] neck, shoulder, hip, tailAt, wingAt;
        /** Bone lengths: upper arm, forearm (to the grip), thigh, shin (to the ankle); the pelvis height standing. */
        public float upperArm, foreArm, thigh, shin, hipY;
        /** The werewolf's hock: the shin is drawn bent back and the foot carries the body on its toes. */
        public boolean digitigrade;
        /** Half the head's width (voxels): a swing is kept clear of a head bigger than a person's. */
        public float headHalf = 4;
        // ---- beasts (bat, spider, dragon)
        public ItemModels.Model body, beastHead, jaw, legA, legB, wingIn, wingOut, tailSeg;
        public float[] headAt, jawAt;
        /** A spider's eight leg roots (x, y, z each), a dragon's four (front pair, back pair). */
        public float[][] legRoots;
        public float legALen, legBLen, wingInLen;
        public int tailSegs;
        public float tailLen;
        public boolean humanoid;
        /** The standing figure's height in voxels (to scale it to the kind's model height). */
        public float standH;
    }

    private static final Map<String, Fig> CACHE = new HashMap<>();

    /** The figure for a kind (a dragon by its breed's colour). */
    public static synchronized Fig of(MonsterKind kind, MonsterKind.Breed breed) {
        String key = kind + (breed == null ? "" : ":" + breed);
        return CACHE.computeIfAbsent(key, k -> build(kind, breed));
    }

    // ------------------------------------------------------------------ colour
    static int c(int rgb) { return 0xFF000000 | rgb; }
    /** A colour varied per voxel (skin, fur, scales and bone are never one flat tone). */
    static int grain(int rgb, int x, int y, int z, float amount) {
        int h = (x * 73856093) ^ (y * 19349663) ^ (z * 83492791);
        h ^= h >>> 13; h *= 0x5bd1e995; h ^= h >>> 15;
        float k = 1 + ((h >>> 8 & 255) / 255f - 0.5f) * amount;
        return c(Tex.shade(rgb, k) & 0xFFFFFF);
    }
    static int mix(int a, int b, float t) { return c(Tex.mix(a, b, t) & 0xFFFFFF); }
    static int shade(int rgb, float k) { return c(Tex.shade(rgb, k) & 0xFFFFFF); }
    static boolean hash(int x, int y, int z, int salt, int in) {
        int h = (x * 92837111) ^ (y * 689287499) ^ (z * 283923481) ^ salt * 0x27d4eb2d;
        h ^= h >>> 15; h *= 0x2c1b3c6d; h ^= h >>> 12;
        return Math.floorMod(h, in) == 0;
    }

    /** A colour per voxel of a part. */
    interface Paint { int at(int x, int y, int z); }

    // ------------------------------------------------------------------ shape helpers
    /** A rounded box: w x h x d with its edges taken off by r. */
    static VoxModel rounded(int w, int h, int d, float r, Paint p) {
        VoxModel m = new VoxModel(w, h, d);
        for (int x = 0; x < w; x++) for (int y = 0; y < h; y++) for (int z = 0; z < d; z++) {
            float ex = Math.max(0, Math.abs(x + 0.5f - w / 2f) - (w / 2f - r)), ey = Math.max(0, Math.abs(y + 0.5f - h / 2f) - (h / 2f - r)), ez = Math.max(0, Math.abs(z + 0.5f - d / 2f) - (d / 2f - r));
            if (ex * ex + ey * ey + ez * ez > r * r + 0.01f) continue;
            m.set(x, y, z, p.at(x, y, z));
        }
        return m;
    }
    /** An ellipsoid filling w x h x d. */
    static VoxModel egg(int w, int h, int d, Paint p) {
        VoxModel m = new VoxModel(w, h, d);
        for (int x = 0; x < w; x++) for (int y = 0; y < h; y++) for (int z = 0; z < d; z++) {
            float ex = (x + 0.5f - w / 2f) / (w / 2f), ey = (y + 0.5f - h / 2f) / (h / 2f), ez = (z + 0.5f - d / 2f) / (d / 2f);
            if (ex * ex + ey * ey + ez * ez <= 1.04f) m.set(x, y, z, p.at(x, y, z));
        }
        return m;
    }
    /** A limb: a column w x len x d, hanging down from its top (pivot at the top centre), a tone at its end. */
    static ItemModels.Model limb(int w, int len, int d, Paint p) {
        VoxModel m = new VoxModel(w, len, d);
        for (int x = 0; x < w; x++) for (int y = 0; y < len; y++) for (int z = 0; z < d; z++) {
            boolean corner = w > 2 && d > 2 && (x == 0 || x == w - 1) && (z == 0 || z == d - 1) && (y == 0 || y == len - 1);
            if (!corner) m.set(x, y, z, p.at(x, y, z));
        }
        return new ItemModels.Model(m, w / 2f, len, d / 2f);
    }
    /** A tapering column (from w0 x d0 at its top to w1 x d1 at its bottom), hanging from its top. */
    static ItemModels.Model taper(int w0, int d0, int w1, int d1, int len, Paint p) {
        int w = Math.max(w0, w1), d = Math.max(d0, d1);
        VoxModel m = new VoxModel(w, len, d);
        for (int y = 0; y < len; y++) {
            float t = 1 - y / (float) Math.max(1, len - 1);
            float hw = (w0 + (w1 - w0) * t) / 2f, hd = (d0 + (d1 - d0) * t) / 2f;
            for (int x = 0; x < w; x++) for (int z = 0; z < d; z++)
                if (Math.abs(x + 0.5f - w / 2f) <= hw && Math.abs(z + 0.5f - d / 2f) <= hd) m.set(x, y, z, p.at(x, y, z));
        }
        return new ItemModels.Model(m, w / 2f, len, d / 2f);
    }

    // ------------------------------------------------------------------ building
    private static Fig build(MonsterKind k, MonsterKind.Breed breed) {
        Fig f = switch (k) {
            case GOBLIN -> goblin();
            case ORC -> orc();
            case TROLL -> troll();
            case IMP -> imp();
            case GHOST -> ghost();
            case ZOMBIE -> zombie();
            case SKELETON -> skeleton();
            case WEREWOLF -> werewolf();
            case BAT -> bat();
            case GIANT_SPIDER -> spider();
            case DRAGON -> dragon(breed == null ? MonsterKind.Breed.FIRE : breed);
        };
        f.kind = k;
        f.vs = k.mh / f.standH;
        return f;
    }

    /** The common humanoid frame: joints from bone lengths and the torso's size. */
    private static Fig frame(int torsoW, int torsoH, int torsoD, int armW, int legW, float upperArm, float foreArm, float thigh, float shin, float footH) {
        Fig f = new Fig();
        f.humanoid = true;
        f.upperArm = upperArm; f.foreArm = foreArm; f.thigh = thigh; f.shin = shin;
        f.hipY = thigh + shin + footH;
        f.neck = new float[]{0, torsoH, 0};
        f.shoulder = new float[]{torsoW / 2f + armW / 2f, torsoH - 1.5f, 0};
        f.hip = new float[]{torsoW / 2f - legW / 2f - 0.25f, 0.5f, 0};
        return f;
    }

    // ---- goblin: small, wiry, green; an oversized head with long pointed ears; a crouch
    private static Fig goblin() {
        int skin = 0x5F7F30, dark = 0x435C22, cloth = 0x5E4228;
        Fig f = frame(7, 7, 4, 2, 3, 5, 5.5f, 5, 4.5f, 1);
        f.torso = new ItemModels.Model(rounded(7, 7, 4, 1.2f, (x, y, z) -> {
            if (y < 2) return grain(cloth, x, y, z, 0.18f);                                   // the loincloth
            if (y == 2) return grain(0x3A2A1A, x, y, z, 0.1f);                                // its cord
            if (z == 0 && y >= 3 && y <= 4 && x >= 2 && x <= 4) return grain(shade(skin, 1.12f), x, y, z, 0.08f);   // the pot belly
            return grain(z == 3 ? dark : skin, x, y, z, 0.14f);
        }), 3.5f, 0, 2);
        f.head = goblinHead(skin, dark);
        f.armUp = limb(2, 5, 2, (x, y, z) -> grain(skin, x, y, z, 0.14f));
        f.armLo = limb(2, 6, 2, (x, y, z) -> y < 2 ? grain(shade(skin, 0.85f), x, y, z, 0.1f) : grain(skin, x, y, z, 0.14f));
        f.legUp = limb(3, 5, 3, (x, y, z) -> y > 3 ? grain(cloth, x, y, z, 0.15f) : grain(skin, x, y, z, 0.14f));
        f.legLo = limb(3, 5, 3, (x, y, z) -> grain(skin, x, y, z, 0.14f));
        f.foot = footModel(3, 1, 5, (x, y, z) -> z == 0 ? grain(0x2A2A1A, x, y, z, 0.1f) : grain(dark, x, y, z, 0.12f));
        f.standH = f.hipY + 7 + 8;
        return f;
    }
    private static ItemModels.Model goblinHead(int skin, int dark) {
        // a big round skull (8 x 7 x 7), long ears swept out and back, a hooked nose, a wide toothy grin, yellow eyes
        VoxModel m = new VoxModel(16, 9, 9);
        int ox = 4, oz = 1;
        for (int x = 0; x < 8; x++) for (int y = 0; y < 7; y++) for (int z = 0; z < 7; z++) {
            boolean corner = (x == 0 || x == 7) && (y == 6 || y == 0) && (z == 0 || z == 6);
            if (!corner) m.set(ox + x, y, oz + z, grain(y >= 5 ? shade(skin, 0.94f) : skin, x, y, z, 0.12f));
        }
        // the ears: pointed blades out to the sides, rising toward the back, a darker inside
        for (int side = 0; side < 2; side++) for (int i = 0; i < 4; i++) {
            int x = side == 0 ? ox - 1 - i : ox + 8 + i;
            int y = 3 + i / 2;
            m.set(x, y, oz + 3, grain(skin, x, y, 3, 0.1f));
            m.set(x, y, oz + 4, grain(i < 3 ? shade(dark, 0.9f) : skin, x, y, 4, 0.1f));
            if (i < 2) m.set(x, y + 1, oz + 4, grain(skin, x, y + 1, 4, 0.1f));
        }
        // a heavy brow, the yellow eyes under it with black slits
        for (int x = 1; x < 7; x++) m.set(ox + x, 5, oz - 0 , grain(shade(skin, 0.8f), x, 5, 0, 0.08f));
        for (int e : new int[]{1, 5}) { m.set(ox + e, 4, oz, c(0xE8D040)); m.set(ox + e + 1, 4, oz, c(0xE8D040)); m.set(ox + (e == 1 ? e + 1 : e), 4, oz, c(0x141008)); }
        // the hooked nose, out over the mouth
        m.set(ox + 3, 3, oz - 1, grain(shade(skin, 1.05f), 3, 3, 0, 0.05f)); m.set(ox + 4, 3, oz - 1, grain(shade(skin, 1.05f), 4, 3, 0, 0.05f));
        m.set(ox + 3, 2, oz - 1, grain(shade(skin, 0.95f), 3, 2, 0, 0.05f)); m.set(ox + 4, 2, oz - 1, grain(shade(skin, 0.95f), 4, 2, 0, 0.05f));
        // a wide mouth with jagged teeth
        for (int x = 1; x < 7; x++) m.set(ox + x, 1, oz, c(x % 2 == 0 ? 0xE6DEB0 : 0x3A1410));
        return new ItemModels.Model(m, 8, 0, oz + 3.5f);
    }

    // ---- orc: muscular, grey-green, heavy brow, tusks, broad shoulders; iron-studded leather
    private static Fig orc() {
        int skin = 0x667A52, dark = 0x48583A, leather = 0x4A3424, iron = 0x6A6A70;
        Fig f = frame(13, 13, 7, 4, 5, 7, 7.5f, 7, 6.5f, 1);
        f.torso = new ItemModels.Model(rounded(13, 13, 7, 2f, (x, y, z) -> {
            if (y < 3) return grain(leather, x, y, z, 0.15f);                                // the kilt of hide
            if (y == 3) return (x % 4 == 1) ? c(iron) : grain(0x2E2018, x, y, z, 0.1f);      // a studded belt
            if ((x + 12 - y) % 13 == 0 || (x + y) % 13 == 4) return grain(leather, x, y, z, 0.1f); // the cross strap
            if (z == 0 && y >= 7 && y <= 9 && (x == 4 || x == 8)) return grain(shade(skin, 0.86f), x, y, z, 0.06f);   // the chest's line
            if (z == 0 && (y == 5 || y == 6) && x >= 4 && x <= 8 && x % 2 == 0) return grain(shade(skin, 0.9f), x, y, z, 0.06f); // abdomen
            return grain(z >= 5 ? dark : skin, x, y, z, 0.13f);
        }), 6.5f, 0, 3.5f);
        f.head = orcHead(skin, dark);
        f.armUp = limb(4, 7, 4, (x, y, z) -> y == 6 && (x == 0 || x == 3) ? c(iron) : y >= 5 ? grain(leather, x, y, z, 0.12f) : grain(skin, x, y, z, 0.13f));
        f.armLo = limb(4, 8, 4, (x, y, z) -> y >= 4 && y <= 6 ? grain(leather, x, y, z, 0.12f) : y < 2 ? grain(shade(skin, 0.9f), x, y, z, 0.1f) : grain(skin, x, y, z, 0.13f));
        f.legUp = limb(5, 7, 5, (x, y, z) -> grain(y > 4 ? leather : 0x3A3A2E, x, y, z, 0.14f));
        f.legLo = limb(5, 7, 5, (x, y, z) -> y < 4 ? grain(0x2E2420, x, y, z, 0.12f) : y == 4 ? c(iron) : grain(0x3A3A2E, x, y, z, 0.14f));
        f.foot = footModel(5, 1, 7, (x, y, z) -> grain(0x2A201C, x, y, z, 0.1f));
        f.standH = f.hipY + 13 + 9;
        return f;
    }
    private static ItemModels.Model orcHead(int skin, int dark) {
        VoxModel m = new VoxModel(10, 11, 10);
        int ox = 1, oz = 1;
        for (int x = 0; x < 8; x++) for (int y = 0; y < 9; y++) for (int z = 0; z < 8; z++) {
            boolean corner = (x == 0 || x == 7) && (y == 8) && (z == 0 || z == 7);
            if (!corner) m.set(ox + x, y, oz + z, grain(skin, x, y, z, 0.12f));
        }
        // the jutting jaw (wider than the skull at the bottom), the heavy overhanging brow, deep-set red eyes
        for (int x = -1; x < 9; x++) for (int y = 0; y < 3; y++) m.set(ox + x, y, oz + (y == 0 ? 1 : 0), grain(shade(skin, 0.96f), x, y, 0, 0.1f));
        for (int x = 0; x < 8; x++) m.set(ox + x, 6, oz - 1, grain(shade(dark, 0.95f), x, 6, 0, 0.08f));
        for (int e : new int[]{1, 5}) { m.set(ox + e, 5, oz, c(0x1A1010)); m.set(ox + e + 1, 5, oz, c(0xC02818)); }
        m.set(ox + 3, 4, oz - 1, grain(shade(skin, 0.9f), 3, 4, 0, 0.05f)); m.set(ox + 4, 4, oz - 1, grain(shade(skin, 0.9f), 4, 4, 0, 0.05f));
        // the tusks: up from the lower jaw past the lip
        for (int t : new int[]{0, 7}) { m.set(ox + t, 2, oz - 1, c(0xE8E0C4)); m.set(ox + t, 3, oz - 1, c(0xF2ECD8)); m.set(ox + t, 4, oz - 1, c(0xF2ECD8)); }
        for (int x = 1; x < 7; x++) m.set(ox + x, 1, oz - 1, c(0x2A1410));
        // a black topknot, bound
        for (int x = 3; x < 5; x++) for (int z = 4; z < 6; z++) { m.set(ox + x, 9, oz + z, c(0x1A1614)); m.set(ox + x, 10, oz + z + 1, c(0x221C18)); }
        return new ItemModels.Model(m, ox + 4, 0, oz + 4);
    }

    // ---- troll: a hulking stoneskin brute: a small head hung low before huge shoulders, arms to the knees, short legs
    private static Fig troll() {
        int rock = 0x6E7262, dark = 0x50544A, moss = 0x4E6232, belly = 0x8A8670;
        Fig f = frame(16, 13, 11, 6, 6, 9, 11, 5, 4, 2);
        // craggy skin: patches a few voxels across (not single specks), moss in the hollows
        Paint skin = (x, y, z) -> {
            if (hash(x / 3, y / 3, z / 3, 7, 7)) return grain(moss, x, y, z, 0.16f);
            if (hash(x / 2, y / 2, z / 2, 3, 4)) return grain(dark, x, y, z, 0.12f);
            return grain(rock, x, y, z, 0.12f);
        };
        f.torso = new ItemModels.Model(rounded(16, 13, 11, 3.2f, (x, y, z) -> {
            if (y < 3) return grain(y == 2 ? 0x3A2A1A : 0x4A3A28, x, y, z, 0.15f);                     // a hide round the hips, its thong
            if (z <= 1 && y >= 3 && y <= 8 && x >= 4 && x <= 11) return grain(belly, x, y, z, 0.1f);    // the paler gut
            return skin.at(x, y, z);
        }), 8, 0, 5.5f);
        f.neck = new float[]{0, 10.5f, -4.5f};   // set forward and low: the head hangs before the shoulders
        f.head = trollHead(rock, dark);
        f.armUp = limb(6, 9, 6, skin);
        f.armLo = limb(7, 11, 7, (x, y, z) -> y < 3 ? grain(shade(rock, 0.9f), x, y, z, 0.12f) : skin.at(x, y, z));
        f.legUp = limb(6, 5, 6, skin);
        f.legLo = limb(6, 4, 6, skin);
        f.foot = footModel(7, 2, 8, (x, y, z) -> z == 0 && x % 2 == 0 ? c(0x34342C) : grain(dark, x, y, z, 0.12f));
        f.standH = f.hipY + 11 + 6;
        return f;
    }
    private static ItemModels.Model trollHead(int rock, int dark) {
        // a heavy skull with a sloped brow, a lump of a nose, an underbite with tusks, small dim eyes, ears like flaps
        VoxModel m = new VoxModel(11, 9, 10);
        for (int x = 1; x < 10; x++) for (int y = 0; y < 7; y++) for (int z = 1; z < 9; z++) {
            if (y == 6 && (x == 1 || x == 9)) continue;
            if (y >= 5 && z < 3) continue;                                              // the brow slopes back
            m.set(x, y, z, grain(y > 3 ? rock : shade(rock, 0.95f), x, y, z, 0.12f));
        }
        for (int x = 0; x < 11; x++) for (int y = 0; y < 2; y++) m.set(x, y, 0, grain(shade(rock, 0.88f), x, y, 0, 0.1f));      // the jutting jaw
        m.set(1, 2, 0, c(0xD8D0B0)); m.set(9, 2, 0, c(0xD8D0B0)); m.set(1, 3, 0, c(0xE8E0C8)); m.set(9, 3, 0, c(0xE8E0C8));       // the tusks
        for (int x = 2; x < 9; x++) m.set(x, 1, 0, c(x % 2 == 0 ? 0xC8C0A0 : 0x2A2018));
        for (int x = 2; x < 9; x++) m.set(x, 5, 2, grain(dark, x, 5, 2, 0.08f));                                                 // the brow ridge
        m.set(3, 4, 1, c(0x0E0C08)); m.set(7, 4, 1, c(0x0E0C08)); m.set(4, 4, 1, c(0xC8A838)); m.set(6, 4, 1, c(0xC8A838));     // eyes
        for (int y = 2; y < 4; y++) for (int z = 0; z < 2; z++) m.set(5, y, z, grain(shade(rock, 1.08f), 5, y, z, 0.05f));         // the nose
        for (int sd : new int[]{0, 10}) for (int y = 3; y < 6; y++) m.set(sd, y, 5, grain(dark, sd, y, 5, 0.1f));                 // ears
        return new ItemModels.Model(m, 5.5f, 0, 6.5f);
    }

    // ---- imp: tiny, red, horned, an oversized head, a pointed tail, small leathery wings
    private static Fig imp() {
        int red = 0xB0302A, dark = 0x6A1A16;
        Fig f = frame(8, 8, 5, 3, 3, 5, 5.5f, 6, 5, 1);
        f.torso = new ItemModels.Model(rounded(8, 8, 5, 1.6f, (x, y, z) -> z == 0 && y >= 2 && y <= 5 && x >= 2 && x <= 5 ? grain(0xC85040, x, y, z, 0.08f) : grain(z >= 4 ? dark : red, x, y, z, 0.12f)), 4, 0, 2.5f);
        VoxModel h = new VoxModel(12, 15, 11);
        int ox = 0, oz = 1;
        for (int x = 0; x < 12; x++) for (int y = 0; y < 10; y++) for (int z = 0; z < 10; z++) {
            float ex = (x + 0.5f - 6) / 6f, ey = (y + 0.5f - 5) / 5.2f, ez = (z + 0.5f - 5) / 5f;
            if (ex * ex + ey * ey + ez * ez <= 1.08f) h.set(ox + x, y, oz + z, grain(y < 3 ? shade(red, 0.92f) : red, x, y, z, 0.1f));
        }
        // curled black horns, big yellow eyes, a wicked grin, pointed ears
        for (int side = 0; side < 2; side++) {
            int x0 = side == 0 ? 2 : 9;
            for (int i = 0; i < 5; i++) h.set(x0 + (side == 0 ? -i / 3 : i / 3), 9 + i, oz + 4 + i / 2, c(i < 2 ? 0x2A2020 : 0x1A1414));
            h.set(side == 0 ? 0 : 11, 6, oz + 5, grain(red, 0, 6, 5, 0.1f));
        }
        for (int e : new int[]{2, 7}) for (int dx = 0; dx < 3; dx++) for (int dy = 0; dy < 2; dy++) h.set(e + dx, 5 + dy, oz, c(dx == 1 && dy == 0 ? 0x141008 : 0xF0D030));
        for (int x = 3; x < 9; x++) h.set(x, 2, oz, c(x == 3 || x == 8 ? 0x3A0A08 : 0xF0E8D8));
        for (int x = 4; x < 8; x++) h.set(x, 3, oz, c(0x3A0A08));
        f.head = new ItemModels.Model(h, 6, 0, oz + 5);
        f.headHalf = 6;
        f.armUp = limb(3, 5, 3, (x, y, z) -> grain(red, x, y, z, 0.12f));
        f.armLo = limb(3, 6, 3, (x, y, z) -> y < 1 ? c(0x1A1010) : grain(red, x, y, z, 0.12f));
        f.legUp = limb(3, 6, 3, (x, y, z) -> grain(red, x, y, z, 0.12f));
        f.legLo = limb(3, 5, 3, (x, y, z) -> grain(dark, x, y, z, 0.12f));
        f.foot = footModel(3, 1, 4, (x, y, z) -> c(0x1A1010));
        // the tail: thin, hanging behind, with a spade at its tip
        VoxModel t = new VoxModel(3, 2, 13);
        for (int z = 0; z < 10; z++) t.set(1, 0, z, grain(red, 1, 0, z, 0.1f));
        for (int x = 0; x < 3; x++) for (int z = 10; z < 13; z++) if (x == 1 || z == 11) t.set(x, 0, z, c(0x2A1010));
        f.tail = new ItemModels.Model(t, 1.5f, 0.5f, 0);
        f.tailAt = new float[]{0, 2, 2.5f};
        // a leathery wing on a finger bone (one model: drawn mirrored for the left)
        VoxModel w = new VoxModel(10, 8, 1);
        for (int x = 0; x < 10; x++) for (int y = 0; y < 8; y++) {
            boolean bone = y == 7 - x * 7 / 10 || x == 0;
            boolean inside = y <= 7 - x * 7 / 10 && y >= (x > 5 ? (x - 5) : 0) - (x % 3 == 0 ? 1 : 0);
            if (bone || inside) w.set(x, y, 0, bone ? c(0x3A1210) : grain(0x8A2420, x, y, 0, 0.12f));
        }
        f.wing = new ItemModels.Model(w, 0, 7, 0.5f);
        f.wingAt = new float[]{1.5f, 6.5f, 2.5f};
        f.standH = f.hipY + 8 + 10;
        return f;
    }

    // ---- ghost: a translucent hooded shade in tattered robes, no legs, a faint glow
    private static Fig ghost() {
        int robe = 0xC8DCE8, deep = 0x8AA0B4;
        Fig f = frame(9, 12, 5, 3, 4, 7, 7, 0, 0, 0);
        f.hipY = 16;
        f.torso = new ItemModels.Model(rounded(9, 12, 5, 1.5f, (x, y, z) -> grain((x + z) % 3 == 0 ? deep : robe, x, y, z, 0.1f)), 4.5f, 0, 2.5f);
        // the hood: a deep cowl round a dark face, two pale points of light in it
        VoxModel h = new VoxModel(10, 10, 10);
        for (int x = 0; x < 10; x++) for (int y = 0; y < 10; y++) for (int z = 0; z < 10; z++) {
            float ex = (x + 0.5f - 5) / 5f, ey = (y + 0.5f - 4.5f) / 5f, ez = (z + 0.5f - 5) / 5f;
            if (ex * ex + ey * ey + ez * ez > 1.05f) continue;
            boolean face = z < 3 && x >= 2 && x <= 7 && y >= 1 && y <= 6;
            h.set(x, y, z, face ? (z == 2 ? c(0x1A2230) : 0) : grain(robe, x, y, z, 0.1f));
        }
        h.set(3, 4, 2, c(0xF0FCFF)); h.set(6, 4, 2, c(0xF0FCFF));
        f.head = new ItemModels.Model(h, 5, 0, 5);
        // the robe below the waist: a long, widening skirt torn into tatters at the hem
        VoxModel r = new VoxModel(13, 17, 8);
        for (int y = 0; y < 17; y++) {
            float t = y / 16f;                    // 0 at the hem, 1 at the waist
            float hw = 6.5f - t * 2.2f, hd = 4 - t * 1.4f;
            for (int x = 0; x < 13; x++) for (int z = 0; z < 8; z++) {
                if (Math.abs(x + 0.5f - 6.5f) > hw || Math.abs(z + 0.5f - 4) > hd) continue;
                if (y < 4 && hash(x, 0, z, 11, 2) && y < 1 + Math.floorMod(x * 3 + z, 4)) continue;   // the tattered hem
                r.set(x, y, z, grain((x + y) % 4 == 0 ? deep : robe, x, y, z, 0.1f));
            }
        }
        f.robe = new ItemModels.Model(r, 6.5f, 17, 4);
        f.armUp = limb(3, 7, 3, (x, y, z) -> grain(robe, x, y, z, 0.1f));
        f.armLo = limb(3, 7, 3, (x, y, z) -> y < 2 ? c(0xE8F4FA) : grain(deep, x, y, z, 0.1f));
        f.standH = 16 + 12 + 9;
        return f;
    }

    // ---- zombie: a rotting man - grey-green flesh, torn shirt and trousers, ribs showing, a slack jaw
    private static Fig zombie() {
        int flesh = 0x7A8A62, rot = 0x5A6A48, shirt = 0x5A5A48, trousers = 0x3E4250, blood = 0x5A1A14;
        Fig f = frame(8, 12, 4, 4, 4, 5, 6, 6, 5, 1);
        f.torso = new ItemModels.Model(rounded(8, 12, 4, 0.9f, (x, y, z) -> {
            if (y < 2) return grain(trousers, x, y, z, 0.12f);
            boolean torn = hash(x, y, z, 5, 3) || (x >= 4 && x <= 6 && y >= 5 && y <= 8 && z == 0);
            if (torn && z == 0 && y >= 5 && y <= 8 && x >= 4 && x <= 6) return (y % 2 == 0) ? c(0xD8D0B8) : c(0x5A2A20);   // exposed ribs
            if (torn) return grain(hash(x, y, z, 9, 4) ? blood : flesh, x, y, z, 0.15f);
            return grain(shirt, x, y, z, 0.15f);
        }), 4, 0, 2);
        VoxModel h = new VoxModel(8, 8, 8);
        for (int x = 0; x < 8; x++) for (int y = 0; y < 8; y++) for (int z = 0; z < 8; z++) h.set(x, y, z, grain(hash(x, y, z, 2, 5) ? rot : flesh, x, y, z, 0.15f));
        for (int x = 0; x < 8; x++) for (int z = 0; z < 8; z++) if (hash(x, 7, z, 3, 2)) h.set(x, 7, z, c(0x2A2620));  // patchy hair
        h.set(1, 4, 0, c(0xE8E8D8)); h.set(2, 4, 0, c(0xC8C8B8));        // one clouded eye
        h.set(5, 4, 0, c(0x1A1410)); h.set(6, 4, 0, c(0x2A1A14));        // one empty socket
        for (int x = 2; x < 6; x++) { h.set(x, 1, 0, c(0x2A0C0A)); h.set(x, 2, 0, c(x % 2 == 0 ? 0xC8C0A0 : 0x2A0C0A)); }   // the slack jaw
        f.head = new ItemModels.Model(h, 4, 0, 4);
        f.armUp = limb(4, 5, 4, (x, y, z) -> x == 0 && y < 3 ? grain(flesh, x, y, z, 0.15f) : grain(shirt, x, y, z, 0.14f));
        f.armLo = limb(4, 6, 4, (x, y, z) -> grain(hash(x, y, z, 4, 6) ? blood : flesh, x, y, z, 0.15f));
        f.legUp = limb(4, 6, 4, (x, y, z) -> grain(trousers, x, y, z, 0.13f));
        f.legLo = limb(4, 5, 4, (x, y, z) -> y < 2 && hash(x, y, z, 6, 2) ? grain(flesh, x, y, z, 0.15f) : grain(trousers, x, y, z, 0.13f));
        f.foot = footModel(4, 1, 6, (x, y, z) -> grain(0x2A2420, x, y, z, 0.1f));
        f.standH = f.hipY + 12 + 8;
        return f;
    }

    // ---- skeleton: bare bones - a hollow ribcage on a spine, thin limbs with knobbed joints, a grinning skull
    private static Fig skeleton() {
        int bone = 0xE6DECA, old = 0xC8BEA4, gap = 0;
        Fig f = frame(8, 12, 5, 2, 2, 5, 6, 6, 5.5f, 1);
        VoxModel t = new VoxModel(8, 12, 5);
        for (int y = 0; y < 3; y++) for (int x = 1; x < 7; x++) for (int z = 1; z < 4; z++) if (y == 2 || x == 1 || x == 6 || z == 3) t.set(x, y, z, grain(old, x, y, z, 0.1f));   // the pelvis
        for (int y = 2; y < 12; y++) { t.set(3, y, 4, grain(old, 3, y, 4, 0.08f)); t.set(4, y, 4, grain(bone, 4, y, 4, 0.08f)); }                                            // the spine
        for (int y = 5; y < 12; y += 2) for (int x = 0; x < 8; x++) for (int z = 0; z < 5; z++) {                                                                          // the ribs: rings
            boolean ring = x == 0 || x == 7 || z == 4 || z == 0 && (x == 1 || x == 6 || y >= 9);
            if (ring) t.set(x, y, z, grain(y == 11 ? bone : old, x, y, z, 0.1f));
        }
        for (int y = 6; y < 12; y++) { t.set(3, y, 0, grain(bone, 3, y, 0, 0.06f)); t.set(4, y, 0, grain(bone, 4, y, 0, 0.06f)); }                                          // the breastbone
        for (int x = 0; x < 8; x++) t.set(x, 11, 1, grain(bone, x, 11, 1, 0.06f));                                                                                          // collarbones
        f.torso = new ItemModels.Model(t, 4, 0, 2.5f);
        VoxModel h = new VoxModel(7, 8, 7);
        for (int x = 0; x < 7; x++) for (int y = 2; y < 8; y++) for (int z = 0; z < 7; z++) {
            boolean corner = (x == 0 || x == 6) && (y == 7) && (z == 0 || z == 6);
            if (!corner) h.set(x, y, z, grain(bone, x, y, z, 0.08f));
        }
        for (int x = 1; x < 6; x++) for (int z = 0; z < 5; z++) h.set(x, 1, z, grain(old, x, 1, z, 0.08f));   // the jaw
        for (int x = 1; x < 6; x++) h.set(x, 2, 0, c(x % 2 == 0 ? 0x2A2420 : 0xF0EAD8));                          // the teeth
        for (int s : new int[]{1, 4}) for (int dx = 0; dx < 2; dx++) for (int dy = 0; dy < 2; dy++) h.set(s + dx, 4 + dy, 0, c(gap == 0 ? 0x141210 : gap));   // sockets
        h.set(3, 3, 0, c(0x2A2420));                                                                               // the nose hole
        f.head = new ItemModels.Model(h, 3.5f, 1, 3.5f);
        Paint b = (x, y, z) -> grain(y == 0 ? bone : old, x, y, z, 0.08f);
        f.armUp = limb(2, 5, 2, b);
        f.armLo = limb(2, 6, 2, (x, y, z) -> y < 2 ? grain(bone, x, y, z, 0.06f) : b.at(x, y, z));
        f.legUp = limb(2, 6, 2, b);
        f.legLo = limb(2, 6, 2, b);
        f.foot = footModel(3, 1, 4, (x, y, z) -> grain(old, x, y, z, 0.08f));
        f.standH = f.hipY + 12 + 8;
        return f;
    }

    // ---- werewolf: a hunched wolf-man - digitigrade legs, a barrel chest and mane, a wolf's head, claws, a brush
    private static Fig werewolf() {
        int fur = 0x5A4A3A, light = 0x8A7A62, dark = 0x3A2E24;
        Fig f = frame(11, 13, 8, 4, 5, 7, 8, 7, 7, 3);
        f.digitigrade = true;
        Paint coat = (x, y, z) -> grain(hash(x / 2, y, z / 2, 1, 6) ? mix(fur, dark, 0.5f) : fur, x, y, z, 0.14f);
        f.torso = new ItemModels.Model(rounded(11, 13, 8, 2.4f, (x, y, z) -> {
            if (z <= 1 && y >= 3 && y <= 10 && x >= 3 && x <= 7) return grain(light, x, y, z, 0.15f);     // the paler chest and belly
            if (y >= 10 && z >= 4) return grain(dark, x, y, z, 0.2f);                                      // the mane over the shoulders
            return coat.at(x, y, z);
        }), 5.5f, 0, 4);
        f.neck = new float[]{0, 12, -2};
        VoxModel h = new VoxModel(9, 11, 15);
        int oz = 7;
        for (int x = 1; x < 8; x++) for (int y = 1; y < 7; y++) for (int z = 0; z < 7; z++) {
            if (y == 6 && (x == 1 || x == 7)) continue;
            h.set(x, y, oz + z, coat.at(x, y, z));                                                      // the skull
        }
        for (int x = 0; x < 9; x++) for (int y = 0; y < 4; y++) for (int z = 3; z < 8; z++) if (x == 0 || x == 8 || y == 0) h.set(x, y, oz + z, grain(dark, x, y, z, 0.15f));   // the ruff
        for (int x = 2; x < 7; x++) for (int y = 2; y < 5; y++) for (int z = 0; z < 7; z++) {          // the long muzzle, tapering
            if (z < 2 && (x == 2 || x == 6)) continue;
            h.set(x, y, z, grain(y == 4 ? fur : light, x, y, z, 0.12f));
        }
        for (int x = 3; x < 6; x++) for (int z = 0; z < 6; z++) h.set(x, 1, z + 1, grain(light, x, 1, z, 0.1f));   // the lower jaw
        for (int x = 3; x < 6; x++) { h.set(x, 4, 0, c(0x141010)); h.set(x, 3, 0, c(0x221816)); }                  // the nose
        h.set(2, 2, 1, c(0xF0EAD8)); h.set(6, 2, 1, c(0xF0EAD8)); h.set(3, 1, 1, c(0xF0EAD8)); h.set(5, 1, 1, c(0xF0EAD8));   // fangs
        for (int x = 3; x < 6; x++) h.set(x, 2, 0, c(0x2A1A16));                                                   // the lips
        h.set(2, 5, oz, c(0xE8A820)); h.set(6, 5, oz, c(0xE8A820)); h.set(2, 6, oz, grain(dark, 2, 6, 0, 0.1f)); h.set(6, 6, oz, grain(dark, 6, 6, 0, 0.1f));
        for (int sd : new int[]{1, 6}) for (int y = 7; y < 11; y++) {                                           // tall pointed ears
            int w = y < 9 ? 2 : 1;
            for (int dx = 0; dx < w; dx++) {
                h.set(sd + dx + (y >= 9 && sd == 6 ? 1 : 0), y, oz + 4, grain(y > 8 ? dark : fur, sd, y, 4, 0.12f));
                if (y < 9) h.set(sd + dx, y, oz + 5, grain(dark, sd, y, 5, 0.1f));
            }
        }
        f.head = new ItemModels.Model(h, 4.5f, 1, oz + 5);
        f.armUp = limb(4, 7, 4, coat);
        f.armLo = limb(4, 8, 4, (x, y, z) -> y == 0 ? c(0x1A1614) : coat.at(x, y, z));
        f.legUp = limb(5, 7, 5, coat);
        f.legLo = limb(4, 7, 4, coat);
        f.foot = footModel(4, 3, 4, (x, y, z) -> y == 0 && z == 0 ? c(0x1A1614) : coat.at(x, y, z));
        VoxModel tl = new VoxModel(4, 4, 11);
        for (int z = 0; z < 11; z++) { float r = z < 3 ? 1.2f : z < 8 ? 2 : 1.4f; for (int x = 0; x < 4; x++) for (int y = 0; y < 4; y++) if (Math.hypot(x + 0.5f - 2, y + 0.5f - 2) <= r) tl.set(x, y, z, z > 8 ? grain(light, x, y, z, 0.1f) : coat.at(x, y, z)); }
        f.tail = new ItemModels.Model(tl, 2, 2, 0);
        f.tailAt = new float[]{0, 2, 4};
        f.standH = f.hipY + 13 + 6;
        return f;
    }

    // ---- bat: a furred body, big ears, and two great leathery wings on their finger bones
    private static Fig bat() {
        int fur = 0x4A3C40, wingC = 0x5A4248, bone = 0x2A2226;
        Fig f = new Fig();
        f.body = new ItemModels.Model(egg(8, 10, 7, (x, y, z) -> grain(z <= 1 && y < 7 ? 0x6E5A54 : fur, x, y, z, 0.16f)), 4, 5, 3.5f);
        VoxModel h = new VoxModel(9, 10, 7);
        for (int x = 1; x < 8; x++) for (int y = 0; y < 5; y++) for (int z = 1; z < 7; z++) if (!((x == 1 || x == 7) && (y == 0 || y == 4))) h.set(x, y, z, grain(fur, x, y, z, 0.14f));
        for (int sd : new int[]{1, 6}) for (int y = 5; y < 10; y++) {                      // the great ears, pink inside
            int w = y < 8 ? 2 : 1;
            for (int dx = 0; dx < w; dx++) { h.set(sd + dx, y, 3, grain(wingC, sd, y, 3, 0.1f)); if (y < 8) h.set(sd + dx, y, 2, grain(0x9A6E6E, sd, y, 2, 0.1f)); }
        }
        h.set(2, 3, 1, c(0xD02A20)); h.set(6, 3, 1, c(0xD02A20));
        for (int x = 3; x < 6; x++) { h.set(x, 1, 0, c(x == 4 ? 0x6A4A4A : 0xF0EAD8)); h.set(x, 2, 0, grain(0x6E5A54, x, 2, 0, 0.1f)); }
        h.set(4, 3, 0, c(0x3A2A2C));
        f.beastHead = new ItemModels.Model(h, 4.5f, 1, 6);
        f.headAt = new float[]{0, 3.5f, -2.5f};
        // a wing in two panels: the arm's (inner) and the fingers' (outer), each drawn mirrored for the left
        f.wingIn = new ItemModels.Model(membrane(8, 8, bone, wingC, false), 0, 0.5f, 4);
        f.wingOut = new ItemModels.Model(membrane(9, 8, bone, wingC, true), 0, 0.5f, 4);
        f.wingInLen = 8;
        f.wingAt = new float[]{3.5f, 2.5f, 0};
        f.legA = limb(1, 3, 1, (x, y, z) -> c(bone));
        f.standH = (8 + 2 * (8 + 9)) * 0.6f / 1.5f;   // scaled by its span: 1.5 blocks wingtip to wingtip
        return f;
    }
    /** A wing panel lying in the x-z plane: a bone along its leading edge, ribs out across it, a scalloped trailing edge. */
    private static VoxModel membrane(int len, int depth, int bone, int skin, boolean outer) {
        VoxModel m = new VoxModel(len, 1, depth);
        for (int x = 0; x < len; x++) {
            int reach = outer ? Math.max(1, depth - x * depth / len) : depth;
            for (int z = 0; z < reach; z++) {
                boolean scallop = z == reach - 1 && x % 3 == 1;
                if (scallop) continue;
                boolean rib = z == 0 || outer && (x == len / 3 && z < reach) || x == 0;
                m.set(x, 0, z, rib ? c(bone) : grain(skin, x, 0, z, 0.12f));
            }
        }
        return m;
    }

    // ---- giant spider: a bulbous banded abdomen, a cephalothorax with eight red eyes and fangs, eight jointed legs
    private static Fig spider() {
        int chitin = 0x2A2622, band = 0x6A3A2A, hair = 0x3A3228;
        Fig f = new Fig();
        f.body = new ItemModels.Model(egg(14, 11, 16, (x, y, z) -> {
            if (y > 6 && (z == 6 || z == 10) && Math.abs(x - 6.5f) < 4) return grain(band, x, y, z, 0.12f);                 // bands across its back
            if (y > 8 && Math.abs(x - 6.5f) < 1.2f && z > 3 && z < 13) return grain(0x8A2A1A, x, y, z, 0.1f);              // the red stripe
            return grain(hash(x, y, z, 1, 3) ? hair : chitin, x, y, z, 0.16f);
        }), 7, 4, 1);                 // pivot at its front, where it joins the cephalothorax
        VoxModel h = new VoxModel(9, 7, 9);
        for (int x = 0; x < 9; x++) for (int y = 0; y < 6; y++) for (int z = 0; z < 9; z++) {
            float ex = (x + 0.5f - 4.5f) / 4.5f, ey = (y + 0.5f - 3) / 3.2f, ez = (z + 0.5f - 4.5f) / 4.5f;
            if (ex * ex + ey * ey + ez * ez <= 1.1f) h.set(x, y, z, grain(chitin, x, y, z, 0.14f));
        }
        int[][] eyes = {{3, 4}, {5, 4}, {2, 5}, {6, 5}, {4, 5}, {1, 4}, {7, 4}, {4, 3}};
        for (int[] e : eyes) h.set(e[0], e[1], e[0] == 4 && e[1] == 3 ? 0 : 1, c(0xD82018));
        for (int s : new int[]{3, 5}) { h.set(s, 1, 0, c(0x1A1612)); h.set(s, 0, 0, c(0x1A1612)); h.set(s, 0, 1, c(0xC8B890)); }   // fangs
        f.beastHead = new ItemModels.Model(h, 4.5f, 3, 9);
        f.headAt = new float[]{0, 0, 0};
        f.legA = new ItemModels.Model(seg(2, 10, chitin, hair), 1, 1, 0);
        f.legB = new ItemModels.Model(seg(2, 12, chitin, 0x1A1612), 1, 1, 0);
        f.legALen = 10; f.legBLen = 12;
        f.legRoots = new float[8][];
        for (int i = 0; i < 4; i++) { float z = -7.5f + i * 1.8f; f.legRoots[i] = new float[]{3.5f, 2, z}; f.legRoots[i + 4] = new float[]{-3.5f, 2, z}; }
        f.standH = 12;
        return f;
    }
    /** A leg segment along +z (its length), banded at its joints. */
    private static VoxModel seg(int w, int len, int col, int tip) {
        VoxModel m = new VoxModel(w, w, len);
        for (int z = 0; z < len; z++) for (int x = 0; x < w; x++) for (int y = 0; y < w; y++) m.set(x, y, z, z == len - 1 ? c(tip) : grain(z % 4 == 0 ? shade(col, 1.25f) : col, x, y, z, 0.12f));
        return m;
    }

    // ---- dragon: a heavy-scaled four-legged reptile with great wings, a long neck and tail, a horned head; its breed's colour
    private static Fig dragon(MonsterKind.Breed breed) {
        int scale = breed.color, belly = shade(mix(breed.color, 0xD8C890, 0.45f), 1.05f), horn = 0xE8DCC0, dark = shade(breed.color, 0.55f);
        Fig f = new Fig();
        // overlapping scales: a darker lower edge to every row, staggered row to row
        Paint scales = (x, y, z) -> grain(Math.floorMod(y + (z / 2 + x / 2) % 2, 3) == 0 ? shade(scale, 0.8f) : scale, x, y, z, 0.1f);
        f.body = new ItemModels.Model(rounded(14, 12, 26, 4.5f, (x, y, z) -> {
            if (y < 4) return grain(belly, x, y, z, 0.1f);                                                    // the plated belly
            if (y >= 11 && Math.abs(x - 6.5f) < 1 && z % 3 == 0) return c(horn);                              // the spine's ridge
            return scales.at(x, y, z);
        }), 7, 6, 13);
        // the neck, in two links; the tail, in five, each narrower
        f.tailSeg = new ItemModels.Model(seg(5, 7, scale, scale), 2.5f, 2.5f, 0);
        f.tailSegs = 5; f.tailLen = 7;
        VoxModel h = new VoxModel(9, 9, 16);
        for (int x = 0; x < 9; x++) for (int y = 2; y < 8; y++) for (int z = 7; z < 15; z++) h.set(x, y, z, scales.at(x, y, z));             // the skull
        for (int x = 2; x < 7; x++) for (int y = 2; y < 6; y++) for (int z = 0; z < 7; z++) h.set(x, y, z, scales.at(x, y, z));               // the snout
        for (int x = 2; x < 7; x++) for (int z = 0; z < 7; z++) h.set(x, 2, z, grain(belly, x, 2, z, 0.1f));
        h.set(2, 5, 0, c(0x1A1010)); h.set(6, 5, 0, c(0x1A1010));                                                                        // nostrils
        h.set(1, 6, 8, c(0xF0C020)); h.set(7, 6, 8, c(0xF0C020));                                                                        // eyes
        for (int s : new int[]{1, 7}) for (int i = 0; i < 5; i++) h.set(s + (s == 1 ? -i / 3 : i / 3), 7 + i / 2, 13 + i / 2, c(i < 2 ? horn : shade(horn, 0.85f)));   // horns, swept back
        for (int x = 2; x < 7; x += 2) h.set(x, 1, 1, c(0xF2ECD8));                                                                      // upper teeth
        f.beastHead = new ItemModels.Model(h, 4.5f, 4, 15);
        VoxModel j = new VoxModel(5, 2, 9);
        for (int x = 0; x < 5; x++) for (int z = 0; z < 9; z++) { j.set(x, 0, z, grain(belly, x, 0, z, 0.1f)); if (z < 6 && (x == 0 || x == 4) && z % 2 == 0) j.set(x, 1, z, c(0xF2ECD8)); }
        f.jaw = new ItemModels.Model(j, 2.5f, 1, 8.5f);
        f.jawAt = new float[]{0, 2, 8};
        f.legA = taper(6, 6, 4, 5, 8, scales);
        VoxModel shin = new VoxModel(5, 8, 7);
        for (int x = 0; x < 5; x++) for (int y = 1; y < 8; y++) for (int z = 1; z < 5; z++) if (x > 0 && x < 4 || y > 2) shin.set(x, y, z, scales.at(x, y, z));
        for (int x = 0; x < 5; x++) for (int z = 0; z < 6; z++) shin.set(x, 0, z, grain(shade(scale, 0.75f), x, 0, z, 0.1f));    // the broad foot
        for (int x = 0; x < 5; x += 2) shin.set(x, 0, 0, c(horn));                                                             // claws
        f.legB = new ItemModels.Model(shin, 2.5f, 8, 3);
        f.legALen = 8; f.legBLen = 8;
        f.legRoots = new float[][]{{6, 1, -8}, {-6, 1, -8}, {6, 1, 8.5f}, {-6, 1, 8.5f}};
        // the wings: the arm, then the membrane spread behind it
        f.wingIn = new ItemModels.Model(membrane(16, 13, dark, shade(scale, 0.8f), false), 0, 0.5f, 0);
        f.wingOut = new ItemModels.Model(membrane(18, 13, dark, shade(scale, 0.72f), true), 0, 0.5f, 0);
        f.wingInLen = 16;
        f.wingAt = new float[]{5.5f, 5, -6};
        f.headAt = new float[]{0, 0, 0};
        f.standH = 30;     // its 4 blocks of height: the body on its legs, the neck and head above
        return f;
    }

    /** A foot: a block w wide, h tall, d long, its pivot at the ankle (the back top), so it reaches forward. */
    private static ItemModels.Model footModel(int w, int h, int d, Paint p) {
        VoxModel m = new VoxModel(w, h, d);
        for (int x = 0; x < w; x++) for (int y = 0; y < h; y++) for (int z = 0; z < d; z++) m.set(x, y, z, p.at(x, y, z));
        return new ItemModels.Model(m, w / 2f, h, d - 1.5f);
    }
}
