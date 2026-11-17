
    // ================================================================== (0.4.8) the troll kin, the scorpions, the demons
    /**
     * (0.4.8, gamefixes: mountain, ice and river trolls with different appearances) one troll's build, three hides and two
     * looks of each. The mountain troll as it was: stone-grey, mossy in its hollows, tusked (a second look browner, lichened).
     * The ice troll: bigger in the shoulder, pale blue-white hide crusted with frost, a shaggy white mane over its back, a
     * beard of icicles, blue-white tusks and pale eyes. The river troll: leaner and longer in the arm, a slick green-brown
     * hide darkening down its back, weed hanging off its shoulders and arms, a wide froggish mouth of fangs, broad webbed
     * feet and yellow eyes.
     */
    private static Fig trollKin(MonsterKind k, int variant) {
        if (k == MonsterKind.TROLL && variant == 0) return troll();
        boolean ice = k == MonsterKind.ICE_TROLL, river = k == MonsterKind.RIVER_TROLL;
        int hide = ice ? (variant == 0 ? 0xB8C8D6 : 0xA8BCCE) : river ? (variant == 0 ? 0x566E44 : 0x4E6248) : 0x7A6E5A;
        int dark = ice ? 0x7C92A8 : river ? 0x34442A : 0x5A5040;
        int spot = ice ? 0xF0F6FA : river ? 0x6E8A3A : 0x8A8A5A;          // frost crust, algae, lichen
        int belly = ice ? 0xD8E2EA : river ? 0x8A9A6A : 0x9A8E74;
        int torsoW = river ? 14 : ice ? 17 : 16, torsoH = river ? 12 : 13, torsoD = river ? 10 : 11;
        float foreArm = river ? 13 : 11, upperArm = river ? 10 : 9;
        Fig f = frame(torsoW, torsoH, torsoD, 6, 6, upperArm, foreArm, 5, 4, 2);
        Paint skin = (x, y, z) -> {
            if (hash(x / 3, y / 3, z / 3, ice ? 11 : 13, ice ? 4 : 6)) return grain(spot, x, y, z, ice ? 0.06f : 0.14f);
            if (hash(x / 2, y / 2, z / 2, 5, 4)) return grain(dark, x, y, z, 0.12f);
            return grain(hide, x, y, z, 0.12f);
        };
        final int tw = torsoW, th = torsoH, td = torsoD;
        f.torso = new ItemModels.Model(rounded(tw, th, td, 3.2f, (x, y, z) -> {
            if (y < 3) return grain(river ? 0x2E3A22 : ice ? 0x5A5048 : 0x4A3A28, x, y, z, 0.15f);                                      // a hide round the hips
            if (ice && y >= th - 4 && z >= 2) return grain(hash(x, y, z, 4, 3) ? 0xE6ECF0 : 0xF6F8FA, x, y, z, 0.05f);               // the white mane over the back
            if (river && z >= td - 3) return grain(shade(dark, 0.85f), x, y, z, 0.1f);                                              // the darker back
            if (z <= 1 && y >= 3 && y <= 8 && x >= 4 && x <= tw - 5) return grain(belly, x, y, z, 0.1f);                            // the paler gut
            return skin.at(x, y, z);
        }), tw / 2f, 0, td / 2f);
        f.neck = new float[]{0, th - 2.5f, -4.5f};
        f.head = trollKinHead(ice, river, hide, dark);
        Paint weed = (x, y, z) -> river && (x + z) % 3 == 0 && y > 2 && hash(x, y / 2, z, 9, 2) ? grain(0x3E6A2A, x, y, z, 0.18f) : skin.at(x, y, z);
        f.armUp = limb(6, (int) upperArm, 6, ice ? (x, y, z) -> y < 3 ? grain(0xEEF2F6, x, y, z, 0.05f) : skin.at(x, y, z) : weed);
        f.armLo = limb(7, (int) foreArm, 7, (x, y, z) -> y < 3 ? grain(shade(hide, 0.9f), x, y, z, 0.12f) : weed.at(x, y, z));
        f.legUp = limb(6, 5, 6, skin);
        f.legLo = limb(6, 4, 6, skin);
        f.foot = river ? footModel(9, 2, 9, (x, y, z) -> z == 0 && x % 3 == 0 ? c(0x1E2A16) : z <= 1 && x % 3 != 0 ? grain(shade(hide, 0.7f), x, y, z, 0.1f) : grain(dark, x, y, z, 0.12f))
                : footModel(7, 2, 8, (x, y, z) -> z == 0 && x % 2 == 0 ? c(ice ? 0xDCE6EE : 0x34342C) : grain(dark, x, y, z, 0.12f));
        f.standH = f.hipY + torsoH - 2 + 6;
        return f;
    }
    private static ItemModels.Model trollKinHead(boolean ice, boolean river, int hide, int dark) {
        VoxModel m = new VoxModel(13, 10, 11);
        int ox = 1;
        for (int x = 1; x < 10; x++) for (int y = 0; y < 7; y++) for (int z = 1; z < 9; z++) {
            if (y == 6 && (x == 1 || x == 9)) continue;
            if (y >= 5 && z < 3) continue;
            m.set(ox + x, y, z, grain(y > 3 ? hide : shade(hide, 0.95f), x, y, z, 0.12f));
        }
        if (river) {
            // a wide froggish mouth: the jaw as broad as the skull, a dark line of a lip, a row of fangs, no tusks
            for (int x = 0; x < 11; x++) for (int y = 0; y < 2; y++) m.set(ox + x, y, 0, grain(shade(hide, 0.85f), x, y, 0, 0.1f));
            for (int x = 1; x < 10; x++) m.set(ox + x, 2, 0, c(0x1A1E10));
            for (int x = 1; x < 10; x += 2) m.set(ox + x, 1, 0, c(0xD8D4B4));
            m.set(ox + 3, 4, 1, c(0x101208)); m.set(ox + 7, 4, 1, c(0x101208)); m.set(ox + 3, 5, 1, c(0xD8C830)); m.set(ox + 7, 5, 1, c(0xD8C830));
            for (int sd : new int[]{0, 10}) for (int y = 3; y < 7; y++) m.set(ox + sd, y, 4, grain(0x3E6A2A, sd, y, 4, 0.2f));   // fins of weed
        } else {
            for (int x = 0; x < 11; x++) for (int y = 0; y < 2; y++) m.set(ox + x, y, 0, grain(shade(hide, 0.88f), x, y, 0, 0.1f));
            int tusk = ice ? 0xDCEAF6 : 0xD8D0B0;
            m.set(ox + 1, 2, 0, c(tusk)); m.set(ox + 9, 2, 0, c(tusk)); m.set(ox + 1, 3, 0, c(tusk)); m.set(ox + 9, 3, 0, c(tusk)); m.set(ox + 1, 4, 0, c(tusk)); m.set(ox + 9, 4, 0, c(tusk));
            for (int x = 2; x < 9; x++) m.set(ox + x, 1, 0, c(x % 2 == 0 ? 0xC8C0A0 : 0x2A2018));
            for (int x = 2; x < 9; x++) m.set(ox + x, 5, 2, grain(dark, x, 5, 2, 0.08f));
            int eye = ice ? 0xB8E0F8 : 0xC8A838;
            m.set(ox + 3, 4, 1, c(0x0E0C08)); m.set(ox + 7, 4, 1, c(0x0E0C08)); m.set(ox + 4, 4, 1, c(eye)); m.set(ox + 6, 4, 1, c(eye));
            for (int y = 2; y < 4; y++) for (int z = 0; z < 2; z++) m.set(ox + 5, y, z, grain(shade(hide, 1.08f), 5, y, z, 0.05f));
            if (ice) {
                // a beard of icicles under the jaw, and frost on the brow
                for (int x = 1; x < 10; x += 2) for (int y = 0; y < 2 + (x % 4 == 1 ? 1 : 0); y++) if (y == 0) m.set(ox + x, 0, 1, c(0xE8F4FC));
                for (int x = 2; x < 9; x++) m.set(ox + x, 6, 3, grain(0xF4F8FC, x, 6, 3, 0.04f));
            }
            for (int sd : new int[]{0, 10}) for (int y = 3; y < 6; y++) m.set(ox + sd, y, 5, grain(dark, sd, y, 5, 0.1f));
        }
        return new ItemModels.Model(m, ox + 5.5f, 0, 6.5f);
    }
    /** (0.4.8) The icicles hung from an ice troll's jaw: a few cells down from its chin (drawn with the head). */

    /**
     * (0.4.8, gamefixes: small and large scorpions) a scorpion: a low flat body of jointed plates, eight legs on a spider's
     * walk, a pair of pincers held out before it on jointed arms, and a tail of five swelling segments arched over its back
     * to the bulb and its hooked sting. The small one sand-brown with darker bands; the giant near black with a rust-red
     * tail and pincer tips.
     */
    private static Fig scorpion(boolean large) {
        int plate = large ? 0x342A24 : 0x8A6A3A, band = large ? 0x1E1814 : 0x5A4426, tip = large ? 0x8A3A22 : 0x3A2A18, leg = large ? 0x2A221C : 0x7A5C34;
        Fig f = new Fig();
        // the body: a long flat oval of plates across its back, banded at each joint
        f.body = new ItemModels.Model(egg(13, 6, 18, (x, y, z) -> {
            if (z % 3 == 0) return grain(band, x, y, z, 0.1f);
            if (y >= 4 && Math.abs(x - 6) <= 1) return grain(shade(plate, 0.82f), x, y, z, 0.08f);          // the keel
            return grain(plate, x, y, z, 0.12f);
        }), 6.5f, 2, 1);
        VoxModel h = new VoxModel(9, 5, 7);
        for (int x = 0; x < 9; x++) for (int y = 0; y < 4; y++) for (int z = 0; z < 7; z++) {
            float ex = (x + 0.5f - 4.5f) / 4.5f, ey = (y + 0.5f - 2) / 2.4f, ez = (z + 0.5f - 3.5f) / 3.5f;
            if (ex * ex + ey * ey + ez * ez <= 1.15f) h.set(x, y, z, grain(plate, x, y, z, 0.12f));
        }
        for (int e : new int[]{3, 5}) { h.set(e, 3, 1, c(0x0A0806)); h.set(e, 3, 2, c(0x0A0806)); }       // the eyes on its crown
        h.set(4, 1, 0, c(0x1A1410)); h.set(3, 1, 0, c(tip)); h.set(5, 1, 0, c(tip));                      // the mouthparts
        f.beastHead = new ItemModels.Model(h, 4.5f, 2, 7);
        f.headAt = new float[]{0, 0, 0};
        f.legA = new ItemModels.Model(seg(2, 7, leg, band), 1, 1, 0);
        f.legB = new ItemModels.Model(seg(2, 9, leg, tip), 1, 1, 0);
        f.legALen = 7; f.legBLen = 9;
        f.legRoots = new float[8][];
        for (int i = 0; i < 4; i++) { float z = -2.5f + i * 2.4f; f.legRoots[i] = new float[]{4.5f, 1.5f, z}; f.legRoots[i + 4] = new float[]{-4.5f, 1.5f, z}; }
        // the pincers: an arm out from beside the head, then the claw - a swollen palm and two fingers
        f.clawA = new ItemModels.Model(seg(3, 8, plate, band), 1.5f, 1.5f, 0);
        VoxModel cl = new VoxModel(7, 4, 11);
        for (int x = 0; x < 7; x++) for (int y = 0; y < 4; y++) for (int z = 0; z < 6; z++) {
            float ex = (x + 0.5f - 3.5f) / 3.5f, ey = (y + 0.5f - 2) / 2f, ez = (z + 0.5f - 3) / 3f;
            if (ex * ex + ey * ey + ez * ez <= 1.1f) cl.set(x, y, z, grain(plate, x, y, z, 0.12f));
        }
        for (int z = 6; z < 11; z++) for (int y = 1; y < 3; y++) { cl.set(1, y, z, grain(z > 8 ? tip : plate, 1, y, z, 0.1f)); cl.set(5, y, z, grain(z > 8 ? tip : plate, 5, y, z, 0.1f)); }
        cl.set(2, 1, 10, c(tip)); cl.set(4, 1, 10, c(tip));                                                // the fingers' hooked ends turned in
        f.clawB = new ItemModels.Model(cl, 3.5f, 2, 0);
        f.clawAt = new float[]{3.5f, 1.5f, -6.5f};
        // the tail: five segments, each a little larger, then the bulb and its sting
        f.tailSeg = new ItemModels.Model(egg(5, 5, 5, (x, y, z) -> grain(z == 0 || z == 4 ? band : large ? 0x5A2A1A : plate, x, y, z, 0.1f)), 2.5f, 2.5f, 0.5f);
        f.tailSegs = 5; f.tailLen = 4.2f;
        VoxModel st = new VoxModel(5, 6, 8);
        for (int x = 0; x < 5; x++) for (int y = 0; y < 5; y++) for (int z = 0; z < 5; z++) {
            float ex = (x + 0.5f - 2.5f) / 2.5f, ey = (y + 0.5f - 2.5f) / 2.5f, ez = (z + 0.5f - 2.5f) / 2.5f;
            if (ex * ex + ey * ey + ez * ez <= 1.1f) st.set(x, y, z, grain(large ? 0x7A2A16 : 0xA88A4A, x, y, z, 0.1f));
        }
        for (int i = 0; i < 4; i++) st.set(2, 1 - Math.min(1, i / 2) + (i > 2 ? 0 : 0), 5 + i, c(i == 3 ? 0x0A0806 : 0x1A1410));   // the hooked sting
        st.set(2, 0, 7, c(0x0A0806));
        f.sting = new ItemModels.Model(st, 2.5f, 2.5f, 0.5f);
        f.tailAt = new float[]{0, 3, 16};
        f.standH = 10;
        return f;
    }

    /**
     * (0.4.8, gamefixes: the Lesser Demon - a squat red brute of a person's height, its skin like flayed muscle, curved bone
     * horns jutting from its brow, no wings, a hunched aggressive silhouette, a snarling fanged face; some with a long
     * green tongue lolling out) three looks: crimson; crimson with the tongue; a darker blood-red with a ridge of spines.
     */
    private static Fig lesserDemon(int variant) {
        int raw = variant == 2 ? 0x8A1E1A : 0xB02A22, sinew = variant == 2 ? 0x5E1210 : 0x7A1A16, light = variant == 2 ? 0xA8342A : 0xD24A3A, bone = 0xDCD0AC;
        Fig f = frame(12, 10, 7, 4, 5, 6.5f, 7, 5.5f, 5, 1);
        // muscle: fibres running down the body, lighter where they swell, darker in the grooves between
        Paint flesh = (x, y, z) -> {
            if (x % 3 == 0) return grain(sinew, x, y, z, 0.1f);
            if ((x + y / 2) % 5 == 1) return grain(light, x, y, z, 0.08f);
            return grain(raw, x, y, z, 0.12f);
        };
        f.torso = new ItemModels.Model(rounded(12, 10, 7, 2.2f, (x, y, z) -> {
            if (y < 2) return grain(0x2A1A14, x, y, z, 0.12f);                                                // a scrap of burnt hide
            if (z == 0 && y >= 6 && y <= 8 && (x == 3 || x == 8)) return grain(sinew, x, y, z, 0.05f);        // the chest's split
            if (z == 0 && y >= 2 && y <= 5 && x >= 4 && x <= 7) return grain((y + x) % 2 == 0 ? light : sinew, x, y, z, 0.05f);   // the belly's knots
            if (variant == 2 && z == 6 && x >= 5 && x <= 6 && y % 2 == 0) return c(bone);                     // spines down the back
            return flesh.at(x, y, z);
        }), 6, 0, 3.5f);
        f.neck = new float[]{0, 8.5f, -2.0f};                                                                 // the head carried low and forward
        f.head = demonHead(raw, sinew, light, bone, variant == 1, false, false, 0);
        f.headHalf = 5;
        f.armUp = limb(4, 7, 4, flesh);
        f.armLo = limb(4, 7, 4, (x, y, z) -> y < 2 ? c(0x1A0E0C) : flesh.at(x, y, z));                      // black-clawed hands
        f.legUp = limb(5, 6, 5, flesh);
        f.legLo = limb(5, 5, 5, flesh);
        f.foot = footModel(5, 1, 6, (x, y, z) -> z == 0 && x % 2 == 0 ? c(0x1A0E0C) : grain(sinew, x, y, z, 0.1f));
        f.standH = f.hipY + 10 - 1.5f + 9;
        return f;
    }

    /**
     * (0.4.8, gamefixes: the Greater Demon - a hulking winged humanoid taller than its lesser cousin, deep blood-red or a
     * bruised purple, great leathery bat wings from its back that beat when it lifts off the ground for a moment, thick
     * imposing horns, broad shoulders and an upright, dominant stance; the greatest of them - level 100 and more - with a
     * mohawk and a beard) three looks: blood-red; bruised purple; the crested and bearded lord.
     */
    private static Fig greaterDemon(int variant) {
        boolean purple = variant == 1;
        int raw = purple ? 0x5A2A5E : 0x8A1A18, sinew = purple ? 0x3A1640 : 0x5A0E0C, light = purple ? 0x7A4480 : 0xAA3026, bone = 0xCFC2A0;
        Fig f = frame(17, 15, 9, 6, 7, 9, 10, 8, 7.5f, 1.5f);
        Paint flesh = (x, y, z) -> {
            if (x % 4 == 0) return grain(sinew, x, y, z, 0.1f);
            if ((x + y / 2) % 6 == 2) return grain(light, x, y, z, 0.08f);
            return grain(raw, x, y, z, 0.12f);
        };
        f.torso = new ItemModels.Model(rounded(17, 15, 9, 3f, (x, y, z) -> {
            if (y < 3) return grain(0x1E1410, x, y, z, 0.12f);                                                 // a war-kilt of black hide
            if (y == 3) return x % 3 == 0 ? c(0x8A7A50) : grain(0x2A1A10, x, y, z, 0.1f);                   // its belt, bossed
            if (z == 0 && y >= 9 && y <= 12 && (x == 5 || x == 11)) return grain(sinew, x, y, z, 0.05f);     // the great chest
            if (z == 0 && y >= 4 && y <= 8 && x >= 6 && x <= 10) return grain((y + x) % 2 == 0 ? light : sinew, x, y, z, 0.05f);
            return flesh.at(x, y, z);
        }), 8.5f, 0, 4.5f);
        f.neck = new float[]{0, 14f, -0.5f};                                                                  // upright: the head over the shoulders
        f.head = demonHead(raw, sinew, light, bone, false, true, variant == 2, purple ? 0x1A1018 : 0x140A08);
        f.headHalf = 6;
        f.armUp = limb(6, 9, 6, flesh);
        f.armLo = limb(6, 10, 6, (x, y, z) -> y < 2 ? c(0x140A08) : y >= 7 && y <= 8 ? c(0x8A7A50) : flesh.at(x, y, z));   // bronze bands at the wrists
        f.legUp = limb(7, 8, 7, flesh);
        f.legLo = limb(7, 7, 7, (x, y, z) -> y < 3 ? grain(0x140A08, x, y, z, 0.1f) : flesh.at(x, y, z));
        f.foot = footModel(7, 2, 8, (x, y, z) -> z == 0 && x % 2 == 0 ? c(0x0E0806) : grain(0x140A08, x, y, z, 0.1f));
        // the wings: a great leather sail on long finger bones, clawed at the wrist (one model, mirrored for the left)
        VoxModel w = new VoxModel(24, 18, 1);
        int membrane = purple ? 0x4A2A50 : 0x5A1612, boneC = purple ? 0x2A1430 : 0x2A0A08;
        for (int x = 0; x < 24; x++) for (int y = 0; y < 18; y++) {
            boolean top = y == 17 - x * 4 / 24 || y == 16 - x * 4 / 24;                                       // the arm bone along the top
            boolean finger = x > 3 && (x - 4) % 6 == 0 && y >= (x > 12 ? (x - 12) / 2 : 0);                   // the fingers fanning down
            int edge = (x > 4 ? (x - 4) * 3 / 4 : 0) - ((x - 4) % 6 == 3 ? 2 : 0);                             // the scalloped trailing edge
            boolean inside = y <= 17 - x * 4 / 24 && y >= Math.max(0, edge);
            if (top || finger && y <= 17 - x * 4 / 24) w.set(x, y, 0, c(boneC));
            else if (inside) w.set(x, y, 0, grain(membrane, x, y, 0, 0.14f));
        }
        w.set(23, 13, 0, c(0x0E0806)); w.set(23, 14, 0, c(0x0E0806));                                       // the claw at the wrist
        f.wing = new ItemModels.Model(w, 0, 17, 0.5f);
        f.wingAt = new float[]{2.5f, 12.5f, 4.5f};
        // and a heavy tail, barbed
        VoxModel t = new VoxModel(3, 3, 16);
        for (int z = 0; z < 14; z++) for (int x = 0; x < 3; x++) for (int y = 0; y < 3; y++) if (z < 8 || x == 1 && y == 1) t.set(x, y, z, grain(z % 4 == 0 ? sinew : raw, x, y, z, 0.1f));
        for (int x = 0; x < 3; x++) { t.set(x, 1, 14, c(boneC)); t.set(1, x, 15, c(boneC)); }
        f.tail = new ItemModels.Model(t, 1.5f, 1.5f, 0);
        f.tailAt = new float[]{0, 3, 4};
        f.standH = f.hipY + 15 + 11;
        return f;
    }

    /**
     * A demon's head: a heavy brow over burning eyes, a snarling mouth of fangs, curved bone horns from the forehead
     * (thicker on the greater, sweeping back and up); the lesser's tongue may loll out long and green; the demon lord's
     * a crest of black hair down its crown and a beard at its jaw.
     */
    private static ItemModels.Model demonHead(int raw, int sinew, int light, int bone, boolean tongue, boolean greater, boolean lord, int hair) {
        int W = greater ? 12 : 10, H = greater ? 11 : 9, D = greater ? 11 : 9;
        VoxModel m = new VoxModel(W + 4, H + 7, D + 6);
        int ox = 2, oz = 3;
        for (int x = 0; x < W; x++) for (int y = 0; y < H - 1; y++) for (int z = 0; z < D; z++) {
            float ex = (x + 0.5f - W / 2f) / (W / 2f), ey = (y + 0.5f - (H - 1) / 2f) / ((H - 1) / 2f), ez = (z + 0.5f - D / 2f) / (D / 2f);
            if (ex * ex * 0.8f + ey * ey * 0.7f + ez * ez * 0.8f > 1.0f) continue;
            m.set(ox + x, y, oz + z, grain(x % 3 == 0 ? sinew : raw, x, y, z, 0.12f));
        }
        // the jaw thrust forward and wide, the brow a heavy ridge, the cheek sinews
        for (int x = 1; x < W - 1; x++) for (int y = 0; y < 3; y++) m.set(ox + x, y, oz - 1, grain(sinew, x, y, 0, 0.1f));
        for (int x = 1; x < W - 1; x++) m.set(ox + x, H - 4, oz - 1, grain(shade(raw, 0.7f), x, H - 4, 0, 0.06f));
        // the eyes: burning, under the brow
        int ey = H - 5, e1 = W / 2 - 3, e2 = W / 2 + 1;
        for (int e : new int[]{e1, e2}) { m.set(ox + e, ey, oz - 1, c(0xFFC020)); m.set(ox + e + 1, ey, oz - 1, c(0xFF7010)); }
        // the snarl: black gums, a row of fangs above and below, the long ones at the corners
        for (int x = 2; x < W - 2; x++) { m.set(ox + x, 2, oz - 2, c(0x140606)); m.set(ox + x, 1, oz - 2, c(0x2A0808)); }
        for (int x = 2; x < W - 2; x += 2) { m.set(ox + x, 2, oz - 2, c(0xEAE2C8)); m.set(ox + x + 1, 1, oz - 2, c(0xEAE2C8)); }
        m.set(ox + 2, 0, oz - 2, c(0xF2ECD8)); m.set(ox + W - 3, 0, oz - 2, c(0xF2ECD8));
        // the horns: from the forehead's sides, curving out, up and back, tapering; heavier on the greater
        int th = greater ? 2 : 1, len = greater ? 8 : 6;
        for (int sd = -1; sd <= 1; sd += 2) for (int i = 0; i < len; i++) {
            int hx = W / 2 + sd * (W / 2 - 1 + (greater ? i / 2 : i / 3)), hy = H - 2 + (greater ? i * 2 / 3 + 1 : i * 2 / 3), hz = 1 + (greater ? i / 2 : i / 2);
            int w = i < len / 2 ? th : 1;
            for (int a = 0; a < w; a++) for (int b = 0; b < w; b++) if (ox + hx + a * sd >= 0 && ox + hx + a * sd < W + 4 && hy + b < H + 7 && oz + hz < D + 6)
                m.set(ox + hx + a * sd, hy + b, oz + hz, c(i >= len - 2 ? 0x8A7E62 : i >= len / 2 ? 0xC4B890 : bone));
        }
        if (tongue) {
            // the long green tongue, out of the corner of the mouth and down over the chin
            for (int i = 0; i < 6; i++) m.set(ox + W / 2 - 1 + (i > 3 ? 1 : 0), Math.max(0, 1 - i / 3), oz - 3 - Math.min(2, i / 2), c(i % 2 == 0 ? 0x5ABE3A : 0x4AA030));
            m.set(ox + W / 2, 0, oz - 4, c(0x6ACE48));
        }
        if (lord) {
            // a crest of black hair from brow to nape, and a beard at the jaw
            for (int z = 1; z < D - 1; z++) for (int y = H - 2; y < H + (z > 2 && z < D - 3 ? 2 : 1); y++) m.set(ox + W / 2 - 1, y, oz + z, grain(hair, 0, y, z, 0.15f));
            for (int z = 1; z < D - 1; z++) for (int y = H - 2; y < H + 1; y++) m.set(ox + W / 2, y, oz + z, grain(hair, 1, y, z, 0.15f));
            for (int x = 2; x < W - 2; x++) for (int y = 0; y < 2; y++) m.set(ox + x, y, oz - 1 + (y == 0 ? 0 : 1), grain(hair, x, y, 0, 0.15f));
            for (int x = 3; x < W - 3; x++) m.set(ox + x, 0, oz - 2 + 3, grain(hair, x, 0, 3, 0.15f));
        }
        return new ItemModels.Model(m, ox + W / 2f, 0, oz + D / 2f);
    }
