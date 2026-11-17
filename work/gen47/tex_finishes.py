p = 'src/main/java/rpg/world/Textures.java'
s = open(p, encoding='utf-8').read()
a = """            case L_STUCCO -> stucco(cv);"""
b = """            case L_STUCCO -> stucco(cv);
            // (0.4.7, gamefixes: six new interior finishes, the territories' walls' insides)
            case L_LIMEWASH -> limewash(cv);
            case L_PAINTED -> paintedPlaster(cv);
            case L_LINENFOLD -> linenfold(cv);
            case L_CLAY_RENDER -> clayRender(cv);
            case L_ZELLIGE -> zellige(cv);
            case L_FRESCO -> fresco(cv);"""
assert a in s
s = s.replace(a, b, 1)
a = """    /** (0.4.2) Harlette's daub:"""
b = """    /**
     * (0.4.7) Loxford's limewash: coat on coat of lime brushed over the rubble - a cool chalky white, the stones' humps
     * showing through as soft swells, the brush's broad strokes, a grey bloom of damp toward the foot.
     */
    static void limewash(Canvas cv) {
        cv.fill(0xE8E4D8);
        for (int y = 0; y < T; y++) for (int x = 0; x < T; x++) {
            float n = cv.noise(2, x, y), m = cv.noise(3, x + 17, y + 5);
            cv.light(y * T + x, (n - 0.5f) * 0.16f + (m > 0.62f ? 0.05f : m < 0.3f ? -0.05f : 0));
        }
        for (int k = 0; k < 18; k++) { float x = cv.rf() * T, y = cv.rf() * T, a = cv.rs() * 0.5f, l = 10 + cv.rf() * 12; cv.toneStroke(x, y, x + (float) Math.cos(a) * l, y + (float) Math.sin(a) * l, 3.5f, 2.5f, cv.rf() < 0.5f ? 0.05f : -0.045f); }
        for (int y = T - 14; y < T; y++) for (int x = 0; x < T; x++) { float n = cv.noise(3, x, y); cv.set(y * T + x, rgb(0xB8B6A8), (y - (T - 14)) / 14f * 0.35f * n); }
        for (int k = 0; k < 2; k++) cv.wander(cv.rf() * T, cv.rf() * T, cv.rf() * 6.28f, 5, 2f, 0.9f, 0.22f, rgb(0xA8A496), 0.45f);
    }
    /**
     * (0.4.7) Harlette's painted plaster: a deep red-ochre dado to waist height under a band of stencilled lozenges, the
     * wall above a warm cream - the way a manor village's hall is painted.
     */
    static void paintedPlaster(Canvas cv) {
        plaster(cv);
        float[] dado = rgb(0x8E3A26), band = rgb(0x2E3A5A), gold = rgb(0xC8A048);
        for (int y = 30; y < T; y++) for (int x = 0; x < T; x++) {
            int i = y * T + x;
            float[] c = shade(dado, (cv.noise(3, x, y) - 0.5f) * 0.18f + (y >= T - 3 ? -0.25f : 0));
            cv.set(i, c, 0.88f);
        }
        for (int y = 24; y < 31; y++) for (int x = 0; x < T; x++) {
            int i = y * T + x, cx = Math.floorMod(x, 8) - 4, cy = y - 27;
            boolean loz = Math.abs(cx) + Math.abs(cy) <= 2;
            cv.set(i, y == 24 || y == 30 ? band : loz ? gold : band, loz ? 0.85f : 0.8f);
        }
        grime(cv, 0.04f);
    }
    /**
     * (0.4.7) Valgard's linenfold: oak panels framed by rails and stiles, each panel carved as folded cloth hanging in long
     * upright pleats, lit on their crests and shadowed in their folds.
     */
    static void linenfold(Canvas cv) {
        cv.fill(0x7A4E2C);
        cv.mottle(0.1f, 19);
        grainLines(cv, false, 0x4A2C16, 12);
        for (int y = 0; y < T; y++) for (int x = 0; x < T; x++) {
            int i = y * T + x;
            boolean rail = y < 5 || y >= T - 5, stile = x % 32 < 4;
            if (rail || stile) { cv.light(i, rail && (y == 0 || y == T - 5) ? 0.32f : rail && (y == 4 || y == T - 1) ? -0.38f : x % 32 == 0 ? 0.25f : x % 32 == 3 ? -0.3f : 0.04f); continue; }
            int px = x % 32 - 4, py = y - 5, ph = T - 10;
            // the folds: four pleats across the panel, a ripple in profile, the ends curled over top and foot
            float f = (float) Math.sin(px / 28f * Math.PI * 4);
            float end = py < 5 ? (5 - py) / 5f : py > ph - 6 ? (py - (ph - 6)) / 6f : 0;
            cv.light(i, f * 0.24f * (1 - end) + (end > 0 ? (float) Math.sin(px / 28f * Math.PI * 8) * 0.12f - end * 0.12f : 0) + (px == 0 || px == 27 ? -0.25f : 0));
        }
    }
    /**
     * (0.4.7) Gorrick's clay render: a hand-thrown coat of earth and straw over the boards, a warm buff, the palm's and
     * the float's marks across it, straw flecks bright in it.
     */
    static void clayRender(Canvas cv) {
        cv.fill(0xB08A60);
        cv.mottle(0.2f, 23);
        for (int k = 0; k < 16; k++) { float x = cv.rf() * T, y = cv.rf() * T, r = 5 + cv.rf() * 8; cv.tone(x, y, r, r * 0.55f, cv.rf() * 3, cv.rf() < 0.5f ? 0.1f : -0.1f, 1f); }
        for (int k = 0; k < 9; k++) { float x = cv.rf() * T, y = cv.rf() * T; cv.toneStroke(x, y, x + 6 + cv.rf() * 8, y + cv.rs() * 3, 2.4f, 1.2f, cv.rf() < 0.5f ? 0.08f : -0.07f); }
        for (int k = 0; k < 46; k++) { float x = cv.rf() * T, y = cv.rf() * T, a = cv.rs() * 3f; cv.stroke(x, y, x + (float) Math.cos(a) * 2.8f, y + (float) Math.sin(a) * 2.8f, 0.3f, 0.2f, rgb(0xE2C27A), 0.65f); }
        cracks(cv, 3, 0x6E4E30);
        grime(cv, 0.06f);
    }
    /**
     * (0.4.7) Azharad's zellige: glazed tiles cut and set as eight-pointed stars, deep blue and green on white with a
     * honey-gold border, each tile's glaze a little uneven and shining.
     */
    static void zellige(Canvas cv) {
        float[] white = rgb(0xEAE6DC), blue = rgb(0x1E4A86), green = rgb(0x2E7A5A), gold = rgb(0xC8962E), grout = rgb(0xB4AA96);
        for (int y = 0; y < T; y++) for (int x = 0; x < T; x++) {
            int i = y * T + x;
            int cx = Math.floorMod(x, 32) - 16, cy = Math.floorMod(y, 32) - 16;
            int ax = Math.abs(cx), ay = Math.abs(cy);
            // an eight-pointed star: a square and the same square turned, overlaid
            boolean star = Math.max(ax, ay) <= 9 || ax + ay <= 12;
            boolean core = Math.max(ax, ay) <= 4 && ax + ay <= 6;
            boolean edge = Math.max(ax, ay) == 9 && ax + ay > 12 || ax + ay == 12 && Math.max(ax, ay) < 9 || Math.max(ax, ay) == 10 && ax + ay <= 13;
            int tileX = Math.floorMod(x, 8), tileY = Math.floorMod(y, 8);
            float[] c = edge ? gold : core ? green : star ? blue : ((x / 32 + y / 32) % 2 == 0 ? white : shade(white, -0.04f));
            boolean joint = !star && (tileX == 7 || tileY == 7);
            if (joint) c = grout;
            float glaze = (cv.noise(4, x, y) - 0.5f) * 0.14f + (Math.floorMod(x + y, 8) == 1 ? 0.07f : 0);
            float[] o = shade(c, glaze);
            cv.r[i] = o[0]; cv.g[i] = o[1]; cv.b[i] = o[2];
        }
        grime(cv, 0.02f);
    }
    /**
     * (0.4.7) Severia's fresco: plaster painted while wet - a deep oxblood dado, a frieze of vine scroll on ochre above
     * it, the field over that a faded sky-blue with the pigment worn thin in patches, the Byzantine way of a merchant's hall.
     */
    static void fresco(Canvas cv) {
        cv.fill(0x9AB4C4);
        cv.mottle(0.12f, 29);
        for (int k = 0; k < 10; k++) { float x = cv.rf() * T, y = cv.rf() * 26, r = 6 + cv.rf() * 9; cv.tone(x, y, r, r * 0.6f, cv.rf() * 3, 0.12f, 1f); }
        float[] ochre = rgb(0xC89A52), vine = rgb(0x4E6A30), red = rgb(0x6E1E1C), line = rgb(0x2A1A14);
        for (int y = 26; y < 38; y++) for (int x = 0; x < T; x++) {
            int i = y * T + x;
            cv.set(i, ochre, 0.9f);
            float v = 32 + (float) Math.sin(x / 64f * Math.PI * 4) * 3.5f;
            if (Math.abs(y - v) < 0.9f) cv.set(i, vine, 0.9f);
            int lx = Math.floorMod(x, 16);
            if ((lx == 5 || lx == 6) && Math.abs(y - (v - (Math.sin(x / 64f * Math.PI * 4) > 0 ? 2.5f : -2.5f))) < 1.4f) cv.set(i, vine, 0.85f);
            if (y == 26 || y == 37) cv.set(i, line, 0.8f);
        }
        for (int y = 38; y < T; y++) for (int x = 0; x < T; x++) cv.set(y * T + x, shade(red, (cv.noise(3, x, y) - 0.5f) * 0.2f), 0.9f);
        for (int y = 0; y < T; y++) for (int x = 0; x < T; x++) { float n = cv.noise(2, x + 31, y + 7); if (n > 0.7f) cv.set(y * T + x, rgb(0xE2D6BC), (n - 0.7f) * 1.6f); }
        grime(cv, 0.04f);
    }

    /** (0.4.2) Harlette's daub:"""
assert a in s
s = s.replace(a, b, 1)
open(p, 'w', encoding='utf-8').write(s)
print("ok")
