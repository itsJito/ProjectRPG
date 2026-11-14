import os
os.chdir(r'C:\Users\John\Documents\ProjectRPG\rpggame\src\main\java\rpg\render')
def patch(path, pairs):
    s = open(path, encoding='utf-8').read()
    for old, new in pairs:
        assert old in s, (path, old[:90])
        s = s.replace(old, new, 1)
    open(path, 'w', encoding='utf-8').write(s)

patch('Gfx.java', [
 ('''    private static Skin.Armor armorOf(Actor a) { return new Skin.Armor(a.eq.def(Slot.HEAD), a.eq.def(Slot.BODY), a.eq.def(Slot.LEGS), a.eq.def(Slot.FEET), a.eq.def(Slot.HANDS)); }''',
  '''    private static Skin.Armor armorOf(Actor a) { return new Skin.Armor(a.eq.def(Slot.HEAD), a.eq.def(Slot.BODY), a.eq.def(Slot.LEGS), a.eq.def(Slot.FEET), a.eq.def(Slot.HANDS), a.eq.def(Slot.CAPE)); }'''),
 ('''    private static final Slot[] ARMOR_SLOTS = {Slot.HEAD, Slot.BODY, Slot.LEGS, Slot.FEET, Slot.HANDS};''',
  '''    private static final Slot[] ARMOR_SLOTS = {Slot.HEAD, Slot.BODY, Slot.LEGS, Slot.FEET, Slot.HANDS, Slot.CAPE};'''),
 ('''        int hairRgb = Appearance.hairColor(look.hairColor);
        if (a.eq.def(Slot.HEAD) == null) {''',
  '''        int hairRgb = Appearance.hairColor(look.hairColor);
        // (0.4.5) a hat sits on the hair (a fez, a straw hat, a cap); a hood, a mask or a helmet hides it
        if (a.eq.def(Slot.HEAD) == null || Garments.onHair(a.eq.def(Slot.HEAD))) {'''),
 ('''            ItemDef cape = a.eq.def(Slot.CAPE);
            if (cape != null) {''',
  '''            ItemDef cape = a.eq.def(Slot.CAPE);
            // (0.4.5) an overcoat is worn, not hung: its sleeves and body painted (Skin), its long skirts built (garments)
            if (cape != null && "overcoat".equals(Garments.shape(cape))) cape = null;
            if (cape != null) {'''),
 ('''        // the toes of shoes that have a shape of their own
        if (!feetArmor && (sh.style()''',
  '''        wornPieces(a, b, tm, tw, td, hipW, hipD, skirt, fwd, back, firstPerson);
        // the toes of shoes that have a shape of their own
        if (!feetArmor && (sh.style()'''),
 ('''    /** A skirt (or a hem) as a front and a back panel hung from the hips, each tipped out as far as the thighs swing. */''',
  '''    /**
     * (0.4.5, gamefixes: the clothing update) What an outfit's pieces build off the body, from their look (Garments): a
     * top's hem and collar, a robe's skirts (with the hem's trim showing beneath), an open coat's long skirts, a hood's
     * cowl, and a hat's brim and crown, a fez and its tassel, a cap's peak, a mining helmet's dome, brim and lamp.
     */
    private void wornPieces(Actor a, Body b, float[] tm, float tw, float td, float hipW, float hipD, float skirt, float fwd, float back, boolean firstPerson) {
        ItemDef top = a.eq.def(Slot.BODY), legs = a.eq.def(Slot.LEGS), head = a.eq.def(Slot.HEAD), coat = a.eq.def(Slot.CAPE);
        if (Garments.is(legs) && Garments.shape(legs).equals("robe")) {
            float len = (float) Garments.num(legs, "length", 11.6);
            int c = legs.color, trim = Garments.color(legs, "trim", Skin.shade(legs.color, 0.7f) & 0xFFFFFF);
            panels(tm, hipW + 0.45f, hipD + 0.45f, len, fwd, back, trim, false, false, firstPerson);
            panels(tm, hipW + 0.55f, hipD + 0.55f, len - 0.7f, fwd, back, c, false, false, firstPerson);
        }
        if (Garments.is(top)) {
            float hem = (float) Garments.num(top, "hem", 0);
            boolean robe = Garments.is(legs) && Garments.shape(legs).equals("robe");
            if (hem > 0) {
                float out = 0.45f + ("loose".equals(Garments.str(top, "fit", "")) ? 0.3f : 0) + (robe ? 0.6f : 0);
                panels(tm, hipW + out, hipD + out, hem, fwd, back, top.color, false, Garments.color(top, "under", -1) >= 0, firstPerson);
            }
            int light = Skin.shade(top.color, 1.12f) & 0xFFFFFF;
            switch (Garments.str(top, "collar", "")) {
                case "fold" -> { hangBox(tm, -1.7f, b.torso[1] + 0.45f, -td / 2 - 0.2f, 1.7f, 1.7f, 0.4f, -0.4f, light, 1); hangBox(tm, 1.7f, b.torso[1] + 0.45f, -td / 2 - 0.2f, 1.7f, 1.7f, 0.4f, -0.4f, light, 1); }
                case "high" -> hangBox(tm, 0, b.torso[1] + 1.3f, 0, 4.6f, 1.4f, Math.min(td, 4f) + 0.6f, 0, Garments.color(top, "trim", top.color), 0.95f);
                default -> { }
            }
        }
        if (Garments.is(coat) && Garments.shape(coat).equals("overcoat")) {
            float len = (float) Garments.num(coat, "length", 12.5);
            int c = coat.color, trim = Garments.color(coat, "trim", Skin.shade(coat.color, 0.7f) & 0xFFFFFF);
            boolean robe = Garments.is(legs) && Garments.shape(legs).equals("robe");
            float out = 0.9f + (robe ? 0.6f : 0);
            if (Garments.num(coat, "layers", 1) > 1) panels(tm, hipW + out - 0.15f, hipD + out - 0.15f, len + 0.6f, fwd, back, trim, false, true, firstPerson);
            panels(tm, hipW + out, hipD + out, len, fwd, back, c, false, true, firstPerson);
            hangBox(tm, 0, b.torso[1] + 0.35f, td / 2 + 0.15f, tw + 0.6f, 1.2f, 0.5f, 0, trim, 1);   // the collar behind the neck
        }
        if (!Garments.is(head) || firstPerson) return;
        int c = head.color, trim = Garments.color(head, "trim", Skin.shade(head.color, 0.75f) & 0xFFFFFF);
        float hw = b.head[0], hh = b.head[1], hd = b.head[2];
        switch (Garments.shape(head)) {
            case "hood", "mask" -> {
                // the cowl over the shoulders, and the hood's fall down the back
                hangBox(tm, 0, b.torso[1] + 0.6f, 0.4f, tw + 0.9f, 1.9f, td + 1.2f, 0, Skin.shade(c, 0.92f) & 0xFFFFFF, 1);
                if (Garments.shape(head).equals("hood")) hangBox(tm, 0, b.torso[1] + 0.9f, td / 2 + 0.55f, Math.min(tw, 6f), 3.4f, 0.6f, 0.18f, Skin.shade(c, 0.85f) & 0xFFFFFF, 1);
                headBox(0, hh + 0.35f, 0.9f, hw * 0.45f, 0.7f, hd * 0.55f, Skin.shade(c, 1.04f) & 0xFFFFFF, 0, 0.95f);   // the hood's peak
            }
            case "mining_helmet" -> {
                headBox(0, hh + 0.25f, 0, hw + 1.6f, 2.6f, hd + 1.6f, c, 0, 0.55f);
                headBox(0, hh + 1.75f, 0, hw * 0.72f, 0.9f, hd * 0.72f, Skin.shade(c, 1.06f) & 0xFFFFFF, 0, 0.55f);
                headBox(0, hh + 2.05f, 0, 0.9f, 0.5f, hd + 1.0f, Skin.shade(c, 1.15f) & 0xFFFFFF, 0, 0.5f);       // the ridge
                headBox(0, hh - 1.0f, 0, hw + 3.0f, 0.35f, hd + 3.0f, Skin.shade(c, 0.85f) & 0xFFFFFF, 0, 0.6f);  // the brim
                headBox(0, hh - 1.0f, -hd / 2 - 1.9f, hw * 0.9f, 0.35f, 1.4f, Skin.shade(c, 0.85f) & 0xFFFFFF, 0, 0.6f);
                headBox(0, hh + 0.55f, -hd / 2 - 1.05f, 1.6f, 1.6f, 0.8f, 0x3A3A3E, 0.8f, 0.35f);                 // the lamp
                int lamp = Garments.color(head, "lamp", 0xF2D27A);
                m.set(headBase).translate(0, hh + 0.55f, -hd / 2 - 1.5f).scale(1.0f, 1.0f, 0.15f);
                put(box, 0, ((lamp >> 16) & 255) / 255f, ((lamp >> 8) & 255) / 255f, (lamp & 255) / 255f, 0.6f, 1, 0.3f, 0);
            }
            case "fez" -> {
                int band = Garments.color(head, "band", -1), tassel = Garments.color(head, "tassel", 0x1A1A1A);
                headBox(0, hh + 0.95f, 0.2f, hw * 0.66f, 2.6f, hd * 0.66f, c, 0, 0.9f);
                headBox(0, hh + 2.3f, 0.2f, hw * 0.6f, 0.15f, hd * 0.6f, Skin.shade(c, 0.8f) & 0xFFFFFF, 0, 0.9f);
                if (band >= 0) headBox(0, hh - 0.2f, 0.2f, hw * 0.68f, 0.5f, hd * 0.68f, band, 0.3f, 0.5f);
                float sw = (float) Math.sin(clock * 2.1 + a.id) * 0.12f + Math.min(0.5f, (float) Math.hypot(a.vx, a.vz) * 0.08f);
                m.set(headBase).translate(0, hh + 2.35f, 0.2f).rotateX(sw).translate(hw * 0.22f, 0, 0).rotateZ(-1.2f).translate(0, -1.0f, 0).scale(0.25f, 2.0f, 0.25f);
                put(box, tassel, 1, 0.9f, 0);
                m.set(headBase).translate(0, hh + 2.35f, 0.2f).rotateX(sw).translate(hw * 0.22f + 1.7f, -0.7f, 0).scale(0.55f, 0.8f, 0.55f);
                put(box, Skin.shade(tassel, 1.2f) & 0xFFFFFF, 1, 0.9f, 0);
            }
            case "straw_hat" -> {
                int weave = Garments.color(head, "weave", Skin.shade(c, 0.85f) & 0xFFFFFF), band = Garments.color(head, "band", 0x7A4A2A);
                headBox(0, hh - 0.35f, 0, hw + 7.0f, 0.32f, hd + 7.0f, c, 0, 1);
                headBox(0, hh - 0.6f, 0, hw + 8.2f, 0.3f, hd + 8.2f, weave, 0, 1);       // the brim's turned-down edge
                headBox(0, hh + 1.0f, 0, hw + 1.3f, 2.6f, hd + 1.3f, c, 0, 1);
                headBox(0, hh + 2.35f, 0, hw + 0.5f, 0.3f, hd + 0.5f, weave, 0, 1);
                headBox(0, hh + 0.05f, 0, hw + 1.45f, 0.65f, hd + 1.45f, band, 0, 0.9f);
            }
            case "cap" -> {
                headBox(0, hh - 0.15f, 0, hw + 0.7f, 1.9f, hd + 0.7f, c, 0, 0.95f);
                headBox(0, hh + 0.85f, 0, hw * 0.75f, 0.4f, hd * 0.75f, Skin.shade(c, 1.08f) & 0xFFFFFF, 0, 0.95f);
                m.set(headBase).translate(0, hh - 1.0f, -hd / 2 - 1.1f).rotateX(-0.18f).scale(hw * 0.82f, 0.28f, 2.3f);
                put(box, trim, 1, 0.9f, 0);
                headBox(0, hh - 1.0f, 0, hw + 0.75f, 0.3f, hd + 0.75f, trim, 0, 0.9f);
            }
            default -> { }
        }
    }

    /** A box on the head (model pixels from the head's pivot at the neck). */
    private void headBox(float x, float y, float z, float w, float h, float d, int rgb, float metal, float rough) {
        m.set(headBase).translate(x, y, z).scale(w, h, d);
        put(box, rgb, 1, rough, metal);
    }

    /** A skirt (or a hem) as a front and a back panel hung from the hips, each tipped out as far as the thighs swing. */'''),
 # insignia on the necklace
 ('''    private void necklace(float[] torsoLocal, Body b, ItemDef d) {
        boolean dodgy''',
  '''    private void necklace(float[] torsoLocal, Body b, ItemDef d) {
        if (Garments.shape(d).equals("insignia")) { insignia(torsoLocal, b, d); return; }
        boolean dodgy'''),
 ('''    /** A skirt (or a hem) as a front and a back panel hung from the hips, each tipped out as far as the thighs swing. */''',
  '''    /** (0.4.5) An outfit's insignia: a cord round the neck and a disc at the breastbone with its mark. */
    private void insignia(float[] torsoLocal, Body b, ItemDef d) {
        int cord = Garments.color(d, "cord", 0x2A2230), mark = Garments.color(d, "mark", 0x5B2C7A);
        float top = b.torso[1], front = -b.torso[2] * b.depth / 2f - 0.9f, half = 1.6f, drop = 2.9f;
        System.arraycopy(torsoLocal, 0, seg.m, 0, 16);
        for (int s = -1; s <= 1; s += 2) {
            float x0 = s * half, y0 = top - 0.1f, x1 = 0, y1 = top - drop;
            float len = (float) Math.hypot(x1 - x0, y1 - y0), ang = (float) Math.atan2(y1 - y0, x1 - x0);
            m.set(root).mul(seg).translate((x0 + x1) / 2, (y0 + y1) / 2, front).rotateZ(ang).scale(len, 0.24f, 0.24f);
            put(box, cord, 1, 0.9f, 0);
        }
        m.set(root).mul(seg).translate(0, top - drop - 0.6f, front - 0.08f).scale(1.3f, 1.3f, 0.3f);
        put(box, d.color, 1, 0.3f, 0.8f);
        m.set(root).mul(seg).translate(0, top - drop - 0.6f, front - 0.26f).scale(0.35f, 0.95f, 0.08f);
        put(box, mark, 1, 0.6f, 0);
        m.set(root).mul(seg).translate(0, top - drop - 0.6f, front - 0.26f).scale(0.95f, 0.35f, 0.08f);
        put(box, mark, 1, 0.6f, 0);
    }

    /** A skirt (or a hem) as a front and a back panel hung from the hips, each tipped out as far as the thighs swing. */'''),
])
print('ok')
