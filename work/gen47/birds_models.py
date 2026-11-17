def sub(p, a, b, cnt=1):
    s = open(p, encoding='utf-8').read()
    assert a in s, (p, a[:90])
    s = s.replace(a, b, cnt)
    open(p, 'w', encoding='utf-8').write(s)

AM = 'src/main/java/rpg/render/AnimalModels.java'
sub(AM, '''            case CHICKEN, DUCK -> bird(s, v);''', '''            case CHICKEN, DUCK -> bird(s, v);
            case CROW, PIGEON, VULTURE -> wildBird(s, v);''')
sub(AM, '''    private static ItemModels.Model closeEyes(ItemModels.Model head, int eyeY, int eyeZ, int... eyeX) {''', '''    /**
     * (0.4.7, gamefixes: the Crow, the Pigeon and the Vulture) the birds that fly, on the same rig as the farm's (a body,
     * a head on its neck, two legs, two wings that the rig spreads and beats in flight, and the ragdoll's parts): a crow
     * all glossy black with a heavy black bill and a fanned tail; a pigeon blue-grey with dark wing bars and a green and
     * purple sheen down its neck; a vulture dark brown and broad-winged, a pale ruff at the base of a long bare neck and a
     * bald pink-grey head with a hooked ivory bill. The wings are long panels, layered at the trailing edge, the vulture's
     * with its primaries fingered at the tip.
     */
    private static Figure wildBird(Species s, int v) {
        Figure f = new Figure();
        f.bird = true;
        boolean crow = s == Species.CROW, pigeon = s == Species.PIGEON, vulture = s == Species.VULTURE;
        int body = crow ? 0x16161C : pigeon ? (v % 2 == 0 ? 0x8A929E : 0x7A808A) : 0x3A2A22;
        int belly = crow ? 0x1E1E26 : pigeon ? 0x9AA2AE : 0x2E221C;
        int wingC = crow ? 0x121218 : pigeon ? 0x7E8692 : 0x2A1E18;
        int headC = crow ? 0x141418 : pigeon ? 0x6E7684 : 0xC89A94;
        int beak = crow ? 0x18181A : pigeon ? 0x3A3434 : 0xE6DCC0, legC = crow ? 0x202024 : pigeon ? 0xC04A54 : 0x8A8A80;
        f.bw = vulture ? 8 : 5; f.bh = vulture ? 8 : 5; f.bl = vulture ? 12 : crow ? 8 : 7; f.legH = vulture ? 4 : crow ? 3 : 2; f.legW = 1;
        f.bodyY = f.legH + f.bh / 2f;
        int bw = (int) f.bw, bh = (int) f.bh, bl = (int) f.bl;
        VoxModel b = new VoxModel(bw, bh + 1, bl + 5);
        for (int x = 0; x < bw; x++) for (int y = 0; y < bh; y++) for (int z = 0; z < bl + 2; z++) {
            float dx = (x - (bw - 1) / 2f) / (bw / 2f + 0.1f), dy = (y - (bh - 1) / 2f) / (bh / 2f + 0.2f), dz = (z - bl / 2f) / (bl / 2f + 0.4f);
            if (dx * dx + dy * dy + dz * dz > 1) continue;
            b.set(x, y, z, y < bh / 2 - 1 ? fur(belly, x, y, z, 0.07f) : fur(body, x, y, z, 0.07f));
        }
        // the tail: a crow's fan, a pigeon's dark-banded square, a vulture's short broad wedge
        int tl = vulture ? 4 : crow ? 4 : 3;
        for (int z = 0; z < tl; z++) for (int x = 1; x < bw - 1; x++) {
            int c = pigeon && z == tl - 1 ? 0x2E3038 : wingC;
            b.set(x, bh / 2 + (crow ? 0 : 0), bl + z, fur(c, x, z, 0, 0.08f));
            if (crow && z == tl - 1) { b.set(0, bh / 2, bl + z, c(wingC)); b.set(bw - 1, bh / 2, bl + z, c(wingC)); }
        }
        f.body = new ItemModels.Model(b, (bw - 1) / 2f + 0.5f, bh / 2f, bl / 2f);
        // legs: thin, the foot's toes forward
        VoxModel l = new VoxModel(1, (int) f.legH + 1, 3);
        for (int y = 1; y <= f.legH; y++) l.set(0, y, 1, c(legC));
        l.box(0, 0, 0, 0, 0, 2, c(legC));
        f.legFront = new ItemModels.Model(l, 0.5f, f.legH + 1, 1.5f);
        f.legBack = f.legFront;
        f.legF = new float[]{bw / 2f - 1.5f, -bh / 2f + 0.5f, 0.5f};
        f.legB = f.legF;
        // the head on its neck
        int hw = vulture ? 4 : 3, neckLen = vulture ? 5 : 2;
        VoxModel h = new VoxModel(hw, neckLen + 6, 8);
        int z0 = 2, z1 = 4;
        for (int y = 0; y < neckLen; y++) for (int x = 0; x < hw; x++) for (int z = z1 - 1; z <= z1; z++) {
            int c = vulture ? (y < 2 ? 0xE8E0D0 : 0xB08884) : pigeon ? ((x + y) % 2 == 0 ? 0x4A8A6A : 0x7A4A8A) : body;   // the ruff, the bare neck; the pigeon's sheen
            h.set(x, y, z, fur(c, x, y, z, 0.06f));
        }
        for (int x = 0; x < hw; x++) for (int y = neckLen; y < neckLen + 3; y++) for (int z = z0; z <= z1; z++) h.set(x, y, z, fur(headC, x, y, z, 0.05f));
        h.set(0, neckLen + 2, z0 + 1, c(0x0C0C0C)); h.set(hw - 1, neckLen + 2, z0 + 1, c(0x0C0C0C));
        int mid = hw / 2;
        if (crow) { h.box(mid, neckLen + 1, 0, mid, neckLen + 2, 1, c(beak)); h.set(mid, neckLen + 1, z0 - 1, c(beak)); }
        else if (pigeon) { h.set(mid, neckLen + 1, 1, c(beak)); h.set(mid, neckLen + 2, z0 - 1, c(0xE8E4E0)); }
        else { h.box(mid - 1, neckLen + 1, 0, mid, neckLen + 2, 1, c(beak)); h.set(mid, neckLen, 0, c(Tex.shade(beak, 0.8f))); }
        f.head = new ItemModels.Model(h, hw / 2f, 0.5f, z1 + 0.5f);
        f.headClosed = closeEyes(f.head, neckLen + 2, z0 + 1, 0, hw - 1);
        f.neck = new float[]{0, bh / 2f - 1, -bl / 2f + 1.2f};
        // the wings: long panels along the body at rest (spread and beaten by the rig in flight), layered at the trailing
        // edge; a vulture's broad, its primaries fingered
        int wl = bl + (vulture ? 6 : 3), wd = vulture ? 6 : 4;
        VoxModel w = new VoxModel(1, wd, wl);
        for (int y = 0; y < wd; y++) for (int z = 0; z < wl; z++) {
            if (y + z / 3 > wd + 2) continue;
            if (vulture && z > wl - 4 && y % 2 == 1) continue;   // the fingered primaries
            int c = z > wl - 5 ? Tex.shade(wingC, 0.75f) : y == 0 ? Tex.shade(wingC, 1.15f) : wingC;
            if (pigeon && (z == 4 || z == 6) && y < 2) c = 0x2E3038;   // the wing bars
            w.set(0, y, z, fur(c, 0, y, z, 0.08f));
        }
        f.wing = new ItemModels.Model(w, 0.5f, wd - 0.5f, 0.5f);
        f.wingAt = new float[]{bw / 2f, bh / 2f - 0.5f, -bl / 2f + 1.5f};
        f.tail = null;
        f.tailAt = new float[]{0, 0, 0};
        return f;
    }

    private static ItemModels.Model closeEyes(ItemModels.Model head, int eyeY, int eyeZ, int... eyeX) {''')
print("ok")
