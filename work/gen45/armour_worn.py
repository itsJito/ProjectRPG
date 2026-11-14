import os
os.chdir(r'C:\Users\John\Documents\ProjectRPG\rpggame\src\main\java\rpg\render')
def patch(path, pairs):
    s = open(path, encoding='utf-8').read()
    for old, new in pairs:
        assert old in s, (path, old[:90])
        s = s.replace(old, new, 1)
    open(path, 'w', encoding='utf-8').write(s)

# ---------------- Skin: the plate's surface by design, and the helm's face
s = open('Skin.java', encoding='utf-8').read()
i = s.index('    /** Armor over rows j0..j1 of a part: leather is stitched')
j = s.index('\n}', i)
s = s[:i] + '''    /**
     * Armor over rows j0..j1 of a part. Leather is stitched; every other tier's plates have their own surface by their
     * design (0.4.5, ArmourDesign: stone slabs lashed with cord, bronze scales, mail's rings, iron's bands, blackened
     * plate's rivets, white's flutes, steel's lames, granite hewn in facets, cobalt's chevrons, molten plate cracked to
     * the glow, godly filigree, malevolent shards) - the material told by its look, not only its colour.
     */
    private void plate(Part p, int j0, int j1, int c, Tier tier) {
        ArmourDesign ad = ArmourDesign.of(tier);
        String pat = tier == null ? "leather" : ad.pattern();
        int glow = ad.glow() >= 0 ? 0xFF000000 | ad.glow() : 0xFFFF8A2A, accent = ad.accent() >= 0 ? 0xFF000000 | ad.accent() : shade(c, 1.4f);
        for (int f = RIGHT; f <= BACK; f++) {
            int w = faceW(p, f);
            for (int j = j0; j < Math.min(j1, faceH(p, f)); j++) for (int i = 0; i < w; i++) {
                int r = j - j0;
                long h = (seed + p.ordinal() * 7919L + f * 104729L + (i / 3) * 31L + (r / 3) * 1013L) * 0x9E3779B97F4A7C15L; h ^= h >>> 29;
                int col = switch (pat) {
                    case "leather" -> i == 0 || i == w - 1 || j == j0 || j == j1 - 1 ? shade(c, 0.78f) : grain(c, p, f, i, j, 0.07f);
                    case "rings" -> shade(c, ((i + j) & 1) == 0 ? 1.12f : 0.82f);
                    case "slab" -> { boolean mortar = r % 5 == 4 || (i + (r / 5 % 2) * 3) % 6 == 5; boolean cord = (i + r) % 9 == 0; yield cord ? 0xFF6A5030 : mortar ? shade(c, 0.62f) : grain(c, p, f, i, j, 0.09f); }
                    case "scale" -> { int v = r % 3, u = (i + (r / 3 % 2) * 2) % 4; yield v == 2 || u == 0 ? shade(c, 0.7f) : v == 0 ? shade(c, 1.18f) : grain(c, p, f, i, j, 0.04f); }
                    case "rivets" -> { int band = r % 6; yield band == 0 ? shade(c, 0.6f) : band == 1 && i % 4 == 1 ? shade(c, 1.9f) : grain(shade(c, 0.9f), p, f, i, j, 0.03f); }
                    case "flutes" -> i % 3 == 0 ? shade(c, 1.22f) : i % 3 == 2 ? shade(c, 0.82f) : grain(c, p, f, i, j, 0.02f);
                    case "lames" -> r % 3 == 0 ? shade(c, 0.7f) : r % 3 == 1 ? shade(c, 1.2f) : grain(c, p, f, i, j, 0.03f);
                    case "chisel" -> shade(c, 0.8f + ((h >>> 9) & 7) * 0.05f);
                    case "chevron" -> { int v = (Math.abs(i * 2 - w + 1) / 2 + r) % 6; yield v < 2 ? shade(c, 1.25f) : v == 2 ? shade(c, 0.75f) : grain(c, p, f, i, j, 0.03f); }
                    case "cracks" -> { long k = ((long) (i * 5 + r * 3) ^ (h >>> 20)) % 11; yield k == 0 || (i + r * 2) % 13 == 0 ? glow : grain(shade(c, 0.55f), p, f, i, j, 0.05f); }
                    case "filigree" -> (i + r) % 5 == 0 && (i * r) % 3 == 0 || r % 8 == 0 && i % 2 == 0 ? accent : grain(c, p, f, i, j, 0.02f);
                    case "jagged" -> { int v = (i * 2 + r) % 7; yield v == 0 ? shade(c, 0.6f) : v == 1 ? shade(c, 1.3f) : (i * 3 + r) % 17 == 0 ? glow : grain(c, p, f, i, j, 0.04f); }
                    default -> { int band = r % 6; yield band == 0 ? shade(c, 0.68f) : band == 1 ? shade(c, 1.22f) : grain(c, p, f, i, j, 0.03f); }
                };
                set(p, f, i, j, col);
            }
        }
    }

    /** (0.4.5) A metal helm's face by its tier's design: open, a nasal, a mail coif, a Corinthian T, slits, a grill, a sallet, a skull. */
    private void helmFace(Tier tier, int c) {
        ArmourDesign ad = ArmourDesign.of(tier);
        int w = faceW(Part.HAT, FRONT), h = faceH(Part.HAT, FRONT);
        int dark = shade(c, 0.72f);
        switch (ad.visor()) {
            case "coif" -> { for (int f = RIGHT; f <= BACK; f++) for (int j = 0; j < h; j++) for (int i = 0; i < faceW(Part.HAT, f); i++) set(Part.HAT, f, i, j, shade(c, ((i + j) & 1) == 0 ? 1.12f : 0.82f));
                for (int j = 4; j < 15; j++) for (int i = 3; i < 13; i++) { float dx = (i - 7.5f) / 5f, dy = (j - 9.5f) / 6f; if (dx * dx + dy * dy < 1) set(Part.HAT, FRONT, i, j, 0); } }
            case "corinthian" -> { for (int i = 3; i < 13; i++) if (i < 7 || i > 8) { set(Part.HAT, FRONT, i, 6, 0); set(Part.HAT, FRONT, i, 7, 0); } rect(Part.HAT, FRONT, 7, 7, 9, h, 0); for (int j = 7; j < h; j++) { set(Part.HAT, FRONT, 6, j, dark); set(Part.HAT, FRONT, 9, j, dark); } }
            case "tslit" -> { rect(Part.HAT, FRONT, 3, 6, 13, 8, 0); rect(Part.HAT, FRONT, 7, 8, 9, 13, 0); }
            case "grill" -> { for (int j = 6; j < 14; j++) for (int i = 4; i < 12; i++) if ((i & 1) == 0 && (j & 1) == 0) set(Part.HAT, FRONT, i, j, 0); rect(Part.HAT, FRONT, 3, 5, 13, 6, dark); }
            case "slot" -> { rect(Part.HAT, FRONT, 3, 7, 13, 8, 0); rect(Part.HAT, FRONT, 2, 6, 14, 7, dark); rect(Part.HAT, FRONT, 2, 8, 14, 9, dark); }
            case "vslit" -> { for (int i = 3; i < 13; i++) { int j = 6 + Math.abs(i * 2 - 15) / 4; set(Part.HAT, FRONT, i, j, 0); set(Part.HAT, FRONT, i, j + 1, 0); } }
            case "sallet" -> { rect(Part.HAT, FRONT, 3, 6, 13, 8, 0); rect(Part.HAT, FRONT, 3, 11, 13, h, 0); rect(Part.HAT, FRONT, 2, 10, 14, 11, dark); }
            case "skull" -> { rect(Part.HAT, FRONT, 3, 6, 6, 9, 0); rect(Part.HAT, FRONT, 10, 6, 13, 9, 0); rect(Part.HAT, FRONT, 7, 9, 9, 11, 0); for (int i = 4; i < 12; i++) set(Part.HAT, FRONT, i, 12, (i & 1) == 0 ? 0 : shade(c, 1.4f)); }
            case "nasal" -> { rect(Part.HAT, FRONT, 2, 6, 14, 16, 0); rect(Part.HAT, FRONT, 7, 5, 9, 12, shade(c, 1.12f)); }
            default -> rect(Part.HAT, FRONT, 2, 6, 14, 16, 0);
        }
    }''' + s[j:]
# the helm's face from the design
old = '''            if (metal) {
                rect(Part.HAT, FRONT, 2, 6, 14, 16, 0);                         // the face opening
                rect(Part.HAT, FRONT, 7, 5, 9, 12, shade(c, 1.12f));           // the nasal
                for (int f = RIGHT; f <= BACK; f++) for (int i = 1; i < faceW(Part.HAT, f); i += 4) set(Part.HAT, f, i, 2, shade(c, 1.35f));   // rivets
                sides(Part.HAT, 4, 5, shade(c, 0.72f), 0.02f);
                rect(Part.HAT, FRONT, 2, 4, 14, 5, shade(c, 0.72f));
            } else {'''
new = '''            if (metal) {
                plate(Part.HAT, 0, rows, c, a.head().tier);
                for (int f = RIGHT; f <= BACK; f++) for (int i = 1; i < faceW(Part.HAT, f); i += 4) set(Part.HAT, f, i, 2, shade(c, 1.35f));   // rivets
                sides(Part.HAT, 4, 5, shade(c, 0.72f), 0.02f);
                rect(Part.HAT, FRONT, 2, 4, 14, 5, shade(c, 0.72f));
                helmFace(a.head().tier, c);                                     // (0.4.5) its tier's own face
            } else {'''
assert old in s; s = s.replace(old, new, 1)
# the breastplate's ridge only where the design has a smooth plate
s = s.replace('''            if (metal(a.body())) darken(Part.JACKET, FRONT, cx - 1, 2, cx + 1, 20, 1.18f);   // a ridge down the breastplate''',
              '''            if (metal(a.body()) && !ArmourDesign.of(a.body().tier).pattern().equals("rings")) darken(Part.JACKET, FRONT, cx - 1, 2, cx + 1, 20, 1.18f);   // a ridge down the breastplate''')
open('Skin.java', 'w', encoding='utf-8').write(s)

# ---------------- Gfx: pauldrons, crests, knees
patch('Gfx.java', [
 ('''        wornPieces(a, b, tm, tw, td, hipW, hipD, skirt, fwd, back, firstPerson);''',
  '''        wornPieces(a, b, tm, tw, td, hipW, hipD, skirt, fwd, back, firstPerson);
        armourPieces(a, b, tm, tw, td, firstPerson);'''),
 ('''    /** A box on the head (model pixels from the head's pivot at the neck). */''',
  '''    /**
     * (0.4.5, gamefixes: every equipment tier its own look) What a tier's armour builds off the body by its design
     * (ArmourDesign): its pauldrons (slab, round, layered, spiked, flared), its helm's crest (a ridge, a plume, horns,
     * wings, a crown, spikes) and a glow behind the visor where its plates are lit from within, its knee guards.
     */
    private void armourPieces(Actor a, Body b, float[] tm, float tw, float td, boolean firstPerson) {
        ItemDef body = a.eq.def(Slot.BODY), legs = a.eq.def(Slot.LEGS), head = a.eq.def(Slot.HEAD);
        if (body != null && body.tier != null && body.wear == null) {
            ArmourDesign ad = ArmourDesign.of(body.tier);
            int c = body.color, dark = Skin.shade(c, 0.72f) & 0xFFFFFF, light = Skin.shade(c, 1.15f) & 0xFFFFFF;
            float mt = body.tier.metal() ? 0.75f : 0, ro = body.tier.metal() ? 0.4f : 0.85f;
            for (int s = -1; s <= 1; s += 2) {
                float x = s * (tw / 2 + 0.55f), y = b.torso[1] - 0.6f;
                switch (ad.pauldron()) {
                    case "slab" -> torsoBox(tm, x, y, 0, 3.0f, 1.9f, td + 1.3f, 0, c, ro, mt);
                    case "round" -> { torsoBox(tm, x, y, 0, 3.0f, 1.5f, td + 1.0f, 0, c, ro, mt); torsoBox(tm, x, y + 0.75f, 0, 2.3f, 0.6f, td + 0.4f, 0, light, ro, mt); }
                    case "layered" -> { for (int k = 0; k < 3; k++) torsoBox(tm, x + s * k * 0.25f, y - k * 0.75f, 0, 3.2f - k * 0.3f, 0.75f, td + 1.1f - k * 0.2f, s * 0.12f, k % 2 == 0 ? c : dark, ro, mt); }
                    case "spiked" -> { torsoBox(tm, x, y, 0, 3.0f, 1.6f, td + 1.1f, 0, c, ro, mt); for (int k = -1; k <= 1; k++) torsoBox(tm, x + s * 0.4f, y + 1.3f, k * 1.2f, 0.5f, 1.4f, 0.5f, s * -0.35f, dark, ro, mt); }
                    case "flared" -> { torsoBox(tm, x + s * 0.3f, y + 0.2f, 0, 3.6f, 0.5f, td + 1.6f, s * -0.45f, c, ro, mt); torsoBox(tm, x + s * 0.9f, y + 0.55f, 0, 1.4f, 0.4f, td + 1.9f, s * -0.6f, ad.accent() >= 0 ? ad.accent() : light, ro, mt); }
                    default -> { }
                }
            }
        }
        if (legs != null && legs.tier != null && legs.wear == null) {
            ArmourDesign ad = ArmourDesign.of(legs.tier);
            int c = legs.color, dark = Skin.shade(c, 0.72f) & 0xFFFFFF;
            float mt = legs.tier.metal() ? 0.75f : 0, ro = legs.tier.metal() ? 0.4f : 0.85f;
            if (!ad.knee().equals("none")) for (Seg sg : new Seg[]{Seg.R_LEG_LO, Seg.L_LEG_LO}) {
                System.arraycopy(rig.seg[sg.ordinal()], 0, seg.m, 0, 16);
                float lw = b.leg[0] * b.limb, ld = b.leg[2] * b.limb;
                switch (ad.knee()) {
                    case "slab" -> limbBox(0, -0.4f, -ld / 2 - 0.35f, lw + 0.9f, 2.0f, 0.8f, 0, c, ro, mt);
                    case "round" -> { limbBox(0, -0.35f, -ld / 2 - 0.3f, lw + 0.5f, 1.6f, 0.7f, 0, c, ro, mt); limbBox(0, -0.35f, -ld / 2 - 0.6f, lw * 0.5f, 0.9f, 0.4f, 0, Skin.shade(c, 1.15f) & 0xFFFFFF, ro, mt); }
                    case "spiked" -> { limbBox(0, -0.35f, -ld / 2 - 0.3f, lw + 0.5f, 1.6f, 0.7f, 0, c, ro, mt); limbBox(0, -0.2f, -ld / 2 - 0.95f, 0.45f, 0.45f, 1.0f, 0, dark, ro, mt); }
                    case "fan" -> { limbBox(0, -0.35f, -ld / 2 - 0.3f, lw + 0.5f, 1.5f, 0.7f, 0, c, ro, mt); for (int s = -1; s <= 1; s += 2) limbBox(s * (lw / 2 + 0.45f), -0.3f, -0.1f, 0.35f, 1.7f, ld * 0.8f, 0, ad.accent() >= 0 ? ad.accent() : dark, ro, mt); }
                    default -> { }
                }
            }
        }
        if (head != null && head.tier != null && head.wear == null && !firstPerson && head.tier.metal()) {
            ArmourDesign ad = ArmourDesign.of(head.tier);
            float hw = b.head[0], hh = b.head[1], hd = b.head[2];
            int c = head.color, dark = Skin.shade(c, 0.72f) & 0xFFFFFF, light = Skin.shade(c, 1.15f) & 0xFFFFFF;
            switch (ad.crest()) {
                case "ridge" -> headBox(0, hh + 0.45f, 0.1f, 0.8f, 0.9f, hd + 0.4f, light, 0.75f, 0.4f);
                case "plume" -> { headBox(0, hh + 0.45f, 0.1f, 0.8f, 0.9f, hd + 0.2f, light, 0.75f, 0.4f);
                    m.set(headBase).translate(0, hh + 1.5f, 1.2f).rotateX(0.5f).scale(0.9f, 2.6f, hd * 0.75f); put(box, 0x9A1C1C, 1, 0.95f, 0); }
                case "horns" -> { for (int s = -1; s <= 1; s += 2) for (int k = 0; k < 3; k++) {
                    m.set(headBase).translate(s * (hw / 2 + 0.4f + k * 0.55f), hh - 0.6f + k * 0.85f, -0.2f + k * 0.2f).rotateZ(s * (-0.9f + k * 0.45f)).scale(0.9f - k * 0.22f, 1.1f, 0.9f - k * 0.22f);
                    put(box, k == 2 ? 0xE8D8B0 : 0xD8C8A0, 1, 0.6f, 0); } }
                case "wings" -> { for (int s = -1; s <= 1; s += 2) { m.set(headBase).translate(s * (hw / 2 + 0.3f), hh - 0.2f, 0.6f).rotateZ(s * -0.7f).rotateY(s * 0.35f).scale(0.3f, 2.6f, 1.8f); put(box, light, 1, 0.35f, 0.8f); } }
                case "crown" -> { for (int k = 0; k < 8; k++) { double an = k * Math.PI / 4; m.set(headBase).translate((float) Math.cos(an) * (hw / 2 + 0.2f), hh + 0.55f, (float) Math.sin(an) * (hd / 2 + 0.2f)).scale(0.6f, 1.2f, 0.6f);
                    put(box, ad.accent() >= 0 ? ad.accent() : light, 1, 0.3f, 0.9f); } headBox(0, hh + 0.05f, 0, hw + 0.6f, 0.4f, hd + 0.6f, ad.accent() >= 0 ? ad.accent() : light, 0.9f, 0.3f); }
                case "spikes" -> { for (int k = -2; k <= 2; k++) { m.set(headBase).translate(0, hh + 0.9f - Math.abs(k) * 0.25f, k * 1.3f).scale(0.5f, 1.9f - Math.abs(k) * 0.35f, 0.5f); put(box, dark, 1, 0.45f, 0.7f); } }
                default -> { }
            }
            // a glow behind the visor (molten plate, the malevolent)
            if (ad.glow() >= 0) {
                int gl = ad.glow();
                m.set(headBase).translate(0, hh * 0.58f, -hd / 2 + 0.25f).scale(hw * 0.7f, 0.5f, 0.1f);
                put(box, 0, ((gl >> 16) & 255) / 255f, ((gl >> 8) & 255) / 255f, (gl & 255) / 255f, 1.4f, 1, 0.5f, 0);
            }
        }
    }

    /** A box on the torso (model pixels in the torso's frame), turned about z. */
    private void torsoBox(float[] torsoLocal, float x, float y, float z, float w, float h, float d, float rotZ, int rgb, float rough, float metal) {
        System.arraycopy(torsoLocal, 0, seg.m, 0, 16);
        m.set(root).mul(seg).translate(x, y, z).rotateZ(rotZ).scale(w, h, d);
        put(box, rgb, 1, rough, metal);
    }

    /** A box on the limb whose frame is in seg (model pixels from its joint). */
    private void limbBox(float x, float y, float z, float w, float h, float d, float rotX, int rgb, float rough, float metal) {
        m.set(root).mul(seg).translate(x, y, z).rotateX(rotX).scale(w, h, d);
        put(box, rgb, 1, rough, metal);
    }

    /** A box on the head (model pixels from the head's pivot at the neck). */'''),
])
print('ok')
