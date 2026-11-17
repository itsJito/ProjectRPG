import os
p = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', 'rpggame', 'src', 'main', 'java', 'rpg', 'render', 'MonsterModels.java')
s = open(p, encoding='utf-8').read()
def sub(old, new):
    global s
    if old not in s: raise SystemExit('MISSING: ' + old[:90])
    s = s.replace(old, new, 1)
# ---- troll hides calmer: the ice troll blue-grey with small frost crusts, its mane on the shoulders' back only; the
# river troll's hide mottled gently, not in camouflage
sub('''        int hide = ice ? (variant == 0 ? 0xB8C8D6 : 0xA8BCCE) : river ? (variant == 0 ? 0x566E44 : 0x4E6248) : 0x7A6E5A;''',
    '''        int hide = ice ? (variant == 0 ? 0x9AB0C2 : 0x8EA6BC) : river ? (variant == 0 ? 0x506A40 : 0x4A5E44) : 0x7A6E5A;''')
sub('''        int dark = ice ? 0x7C92A8 : river ? 0x34442A : 0x5A5040;''', '''        int dark = ice ? 0x6E86A0 : river ? 0x42563A : 0x5A5040;''')
sub('''        int spot = ice ? 0xF0F6FA : river ? 0x6E8A3A : 0x8A8A5A;          // frost crust, algae, lichen''',
    '''        int spot = ice ? 0xE8F0F6 : river ? 0x5E7A42 : 0x8A8A5A;          // frost crust, algae, lichen''')
sub('''            if (hash(x / 3, y / 3, z / 3, ice ? 11 : 13, ice ? 4 : 6)) return grain(spot, x, y, z, ice ? 0.06f : 0.14f);''',
    '''            if (hash(x / 2, y / 2, z / 2, ice ? 11 : 13, ice ? 7 : 6)) return grain(spot, x, y, z, ice ? 0.06f : 0.1f);''')
sub('''            if (ice && y >= th - 4 && z >= 2) return grain(hash(x, y, z, 4, 3) ? 0xE6ECF0 : 0xF6F8FA, x, y, z, 0.05f);               // the white mane over the back''',
    '''            if (ice && y >= th - 3 && z >= td / 2) return grain(hash(x, y, z, 4, 3) ? 0xDCE6EE : 0xF0F4F8, x, y, z, 0.05f);           // the white mane over the shoulders' back''')
sub('''        Paint weed = (x, y, z) -> river && (x + z) % 3 == 0 && y > 2 && hash(x, y / 2, z, 9, 2) ? grain(0x3E6A2A, x, y, z, 0.18f) : skin.at(x, y, z);
        f.armUp = limb(6, (int) upperArm, 6, ice ? (x, y, z) -> y < 3 ? grain(0xEEF2F6, x, y, z, 0.05f) : skin.at(x, y, z) : weed);''',
    '''        Paint weed = (x, y, z) -> river && (x + z) % 4 == 0 && y > 3 && hash(x, y / 3, z, 9, 3) ? grain(0x3A5E26, x, y, z, 0.12f) : skin.at(x, y, z);
        f.armUp = limb(6, (int) upperArm, 6, ice ? (x, y, z) -> y < 2 ? grain(0xE6EEF4, x, y, z, 0.05f) : skin.at(x, y, z) : weed);''')
# ---- demon bellies: knotted, not chequered
sub('''            if (z == 0 && y >= 2 && y <= 5 && x >= 4 && x <= 7) return grain((y + x) % 2 == 0 ? light : sinew, x, y, z, 0.05f);   // the belly's knots''',
    '''            if (z == 0 && y >= 2 && y <= 5 && x >= 4 && x <= 7) return grain(y % 2 == 0 && (x == 5 || x == 6) ? light : raw, x, y, z, 0.06f);   // the belly's knots''')
sub('''            if (z == 0 && y >= 4 && y <= 8 && x >= 6 && x <= 10) return grain((y + x) % 2 == 0 ? light : sinew, x, y, z, 0.05f);''',
    '''            if (z == 0 && y >= 4 && y <= 8 && x >= 6 && x <= 10) return grain(y % 2 == 0 && (x == 7 || x == 9) ? light : raw, x, y, z, 0.06f);''')
# ---- the greater demon's wing: a filled sail, longer, the fingers as bones across it and the edge scalloped between them
sub('''        VoxModel w = new VoxModel(24, 18, 1);
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
        f.wing = new ItemModels.Model(w, 0, 17, 0.5f);''',
    '''        int WW = 30, WH = 22;
        VoxModel w = new VoxModel(WW, WH, 1);
        int membrane = purple ? 0x4A2850 : 0x6A1A14, boneC = purple ? 0x2A1430 : 0x2A0A08, vein = purple ? 0x3A1E42 : 0x501210;
        for (int x = 0; x < WW; x++) {
            int topY = WH - 1 - x * 5 / WW;                                                                       // the arm bone, rising slightly to the wrist
            // the trailing edge: high at the body, sweeping down to the tip, scalloped (raised midway between the fingers)
            float fx = (x % 7) / 7f;
            int edge = (int) Math.round(Math.max(0, WH * 0.55f - x * 0.45f) + 3.2f * Math.sin(fx * Math.PI));
            for (int y = 0; y < WH; y++) {
                if (y > topY) continue;
                boolean arm = y >= topY - 1;
                boolean finger = x > 1 && x % 7 == 0 && y >= edge - 1;
                if (arm || finger) w.set(x, y, 0, c(boneC));
                else if (y >= edge) w.set(x, y, 0, grain(hash(x, y, 0, 3, 9) ? vein : membrane, x, y, 0, 0.1f));
            }
        }
        w.set(WW - 1, WH - 6, 0, c(0x0E0806)); w.set(WW - 1, WH - 7, 0, c(0x0E0806));                       // the claw at the wrist
        f.wing = new ItemModels.Model(w, 0, WH - 1, 0.5f);''')
# ---- the demon's mouth: a dark maw, a few fangs, long ones at the corners
sub('''        for (int x = 2; x < W - 2; x++) { m.set(ox + x, 2, oz - 2, c(0x140606)); m.set(ox + x, 1, oz - 2, c(0x2A0808)); }
        for (int x = 2; x < W - 2; x += 2) { m.set(ox + x, 2, oz - 2, c(0xEAE2C8)); m.set(ox + x + 1, 1, oz - 2, c(0xEAE2C8)); }
        m.set(ox + 2, 0, oz - 2, c(0xF2ECD8)); m.set(ox + W - 3, 0, oz - 2, c(0xF2ECD8));''',
    '''        for (int x = 2; x < W - 2; x++) { m.set(ox + x, 2, oz - 2, c(0x1A0606)); m.set(ox + x, 1, oz - 2, c(0x2A0A08)); }
        // the fangs: a long one at each corner, top and bottom, and two short in between
        int a1 = 2, a2 = W - 3;
        m.set(ox + a1, 2, oz - 2, c(0xF0E8D0)); m.set(ox + a1, 1, oz - 2, c(0xF0E8D0)); m.set(ox + a1, 0, oz - 2, c(0xE0D6B8));
        m.set(ox + a2, 2, oz - 2, c(0xF0E8D0)); m.set(ox + a2, 1, oz - 2, c(0xF0E8D0)); m.set(ox + a2, 0, oz - 2, c(0xE0D6B8));
        m.set(ox + W / 2 - 2, 2, oz - 2, c(0xE8E0C6)); m.set(ox + W / 2 + 1, 2, oz - 2, c(0xE8E0C6));
        m.set(ox + W / 2 - 1, 1, oz - 2, c(0xDCD2B4));''')
# ---- the horns: thick at the root, out from the brow's sides and forward, then up, curling back at the tip
sub('''        int th = greater ? 2 : 1, len = greater ? 8 : 6;
        for (int sd = -1; sd <= 1; sd += 2) for (int i = 0; i < len; i++) {
            int hx = W / 2 + sd * (W / 2 - 1 + (greater ? i / 2 : i / 3)), hy = H - 2 + (greater ? i * 2 / 3 + 1 : i * 2 / 3), hz = 1 + (greater ? i / 2 : i / 2);
            int w = i < len / 2 ? th : 1;
            for (int a = 0; a < w; a++) for (int b = 0; b < w; b++) if (ox + hx + a * sd >= 0 && ox + hx + a * sd < W + 4 && hy + b < H + 7 && oz + hz < D + 8)
                m.set(ox + hx + a * sd, hy + b, oz + hz, c(i >= len - 2 ? 0x8A7E62 : i >= len / 2 ? 0xC4B890 : bone));
        }''',
    '''        int len = greater ? 9 : 6;
        float th0 = greater ? 2.6f : 2.0f;
        for (int sd = -1; sd <= 1; sd += 2) {
            // a polyline: out from the brow, forward a little, then up, the tip curling back
            float px = W / 2f + sd * (W / 2f - 1.5f), py = H - 3.5f, pz = 1.5f;
            for (int i = 0; i < len; i++) {
                float q = i / (float) (len - 1);
                float r = th0 * (1 - q * 0.7f) / 2f;
                for (int a = -2; a <= 2; a++) for (int b = -2; b <= 2; b++) for (int cdz = -2; cdz <= 2; cdz++) {
                    if (a * a + b * b + cdz * cdz > r * r * 1.6f + 0.2f) continue;
                    int vx = ox + Math.round(px + a), vy = Math.round(py + b), vz = oz + Math.round(pz + cdz);
                    if (vx < 0 || vy < 0 || vz < 0 || vx >= W + 4 || vy >= H + 7 || vz >= D + 8) continue;
                    m.set(vx, vy, vz, c(q > 0.8f ? 0x7A6E52 : q > 0.5f ? 0xBFB28A : bone));
                }
                px += sd * (q < 0.4f ? 0.7f : 0.25f);
                py += q < 0.3f ? 0.35f : 0.95f;
                pz += q < 0.4f ? -0.45f : q < 0.75f ? 0.1f : 0.6f;
            }
        }''')
# ---- the small scorpion's legs and tail in proportion (the giant's kept)
sub('''        f.legA = new ItemModels.Model(seg(2, 7, leg, band), 1, 1, 0);
        f.legB = new ItemModels.Model(seg(2, 9, leg, tip), 1, 1, 0);
        f.legALen = 7; f.legBLen = 9;''', '''        f.legA = new ItemModels.Model(seg(2, 6, leg, band), 1, 1, 0);
        f.legB = new ItemModels.Model(seg(2, 7, leg, tip), 1, 1, 0);
        f.legALen = 6; f.legBLen = 7;''')
sub('''        f.tailSeg = new ItemModels.Model(egg(5, 5, 5, (x, y, z) -> grain(z == 0 || z == 4 ? band : large ? 0x5A2A1A : plate, x, y, z, 0.1f)), 2.5f, 2.5f, 0.5f);
        f.tailSegs = 5; f.tailLen = 4.2f;''', '''        f.tailSeg = new ItemModels.Model(egg(4, 4, 5, (x, y, z) -> grain(z == 0 || z == 4 ? band : large ? 0x5A2A1A : plate, x, y, z, 0.1f)), 2f, 2f, 0.5f);
        f.tailSegs = 5; f.tailLen = 3.6f;''')
open(p, 'w', encoding='utf-8', newline='').write(s)
print('rev1')
