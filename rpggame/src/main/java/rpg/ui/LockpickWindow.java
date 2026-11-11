package rpg.ui;

import rpg.interact.Lockpicking;

/**
 * (0.3.7, gamefixes: the lockpicking overhaul) the lock as the picker sees it: the face of the cylinder with its keyway,
 * the pick on its half circle above, the tension wrench below, and how much is left of the pick in use.
 *
 * <p>The mouse sets the pick: its angle on the arc follows where the pointer is round the lock's centre. The left
 * button turns the wrench - held, the cylinder turns as far as the pick allows; let go, it springs back and the pick can
 * be moved again. A bound cylinder shakes and the pick's bar drains. Escape (or the right button) steps away.
 */
final class LockpickWindow {
    private LockpickWindow() {}

    /** The window's size, the cylinder's radius and how far the pick reaches past it (px). */
    static final float W = 520, H = 470, CYL = 92, PICK = 150;

    static void draw(Hud hud, Ui2D ui, int w, int h, float dt) {
        var g = hud.g;
        Lockpicking.Attempt t = hud.picking;
        // the window closes itself with the lock out of reach, its picker down, or the attempt over (a moment to read it)
        if (!g.player.alive || t.lock.removed || t.lock.dist(g.player) > 4) { g.lockpicking.abandon(t); hud.picking = null; return; }
        float[] c = centre(w, h);
        float cx = c[0], cy = c[1];
        float x0 = cx - W / 2, y0 = (h - H) / 2f;
        // input: the pick's angle from the pointer round the centre; the wrench on the left button
        float want = (float) Math.toDegrees(Math.atan2(ui.mx - cx, cy - ui.my));
        boolean torque = ui.down && !t.over;
        if (ui.rclick && !t.over) { g.lockpicking.abandon(t); hud.picking = null; hud.sound("UI_windowclose", 0.5f); ui.consumed = true; return; }
        float wasCyl = t.cylinder; int wasBroken = t.broken;
        String said = t.over ? null : g.lockpicking.tick(t, dt, want, torque);
        if (said != null) { hud.pickMsg = said; hud.pickMsgT = 0; }
        if (t.broken > wasBroken) hud.pickShake = 0.35f;
        if (t.struggling) hud.pickShake = Math.max(hud.pickShake, 0.12f);
        if (t.cylinder > wasCyl + 0.0001f && hud.now - hud.pickClickAt > 0.22f) { hud.pickClickAt = (float) hud.now; if (g.sfx != null) g.sfx.ui("lock", 0.12f); }
        hud.pickShake = Math.max(0, hud.pickShake - dt);
        hud.pickMsgT += dt;
        if (t.over && hud.pickMsgT > 1.6f) {
            if (t.open && t.lock instanceof rpg.objects.Container box && g.ui != null) { hud.picking = null; g.ui.openContainer(box); return; }
            hud.picking = null; return;
        }
        ui.consumed = true;

        // the card
        ui.rect(x0, y0, W, H, Style.CARD);
        ui.border(x0, y0, W, H, 2, Style.GOLD);
        ui.border(x0 + 3, y0 + 3, W - 6, H - 6, 1, Style.EDGE);
        String title = t.lock.name;
        ui.text(title, cx - ui.width(title, 18) / 2, y0 + 16, 18, Style.TEXT);
        String grade = t.difficulty.title + " lock";
        ui.text(grade, cx - ui.width(grade, 12) / 2, y0 + 42, 12, gradeColor(t.difficulty));

        float jx = hud.pickShake > 0 ? (float) Math.sin(hud.now * 90) * 2.2f * Math.min(1, hud.pickShake * 6) : 0;
        float lx = cx + jx, ly = cy;
        // the plate: a dark brass disc with a rim, and the arc the pick travels drawn faintly over it
        disc(ui, lx, ly, CYL + 26, 0xFF2A2218);
        ring(ui, lx, ly, CYL + 26, 3, Style.GOLD);
        for (int a = -90; a <= 90; a += 15) {
            double r = Math.toRadians(a);
            float r0 = CYL + 30, r1 = CYL + (a % 45 == 0 ? 44 : 38);
            ui.line(lx + (float) Math.sin(r) * r0, ly - (float) Math.cos(r) * r0, lx + (float) Math.sin(r) * r1, ly - (float) Math.cos(r) * r1, 2, 0x60E8C878);
        }
        // the cylinder, turned clockwise as far as the wrench has it (a quarter turn opens)
        double turn = t.cylinder * Math.PI / 2;
        disc(ui, lx, ly, CYL, 0xFF6E5A36);
        disc(ui, lx, ly, CYL - 8, 0xFF8A7346);
        ring(ui, lx, ly, CYL, 2, 0xFF3A2E1C);
        // the keyway: a slot with a round head, turning with the cylinder
        float kx = (float) Math.sin(turn), ky = -(float) Math.cos(turn);
        ui.line(lx - kx * 10, ly - ky * 10, lx + kx * 44, ly + ky * 44, 9, 0xFF120E09);
        disc(ui, lx - kx * 14, ly - ky * 14, 12, 0xFF120E09);
        // the wrench: in the foot of the keyway, bent down and out to where the hand holds it
        float wx = lx + kx * 40, wy = ly + ky * 40;
        float hx = wx + (float) Math.cos(turn) * 70, hy = wy + (float) Math.sin(turn) * 70 + 40;
        ui.line(wx, wy, wx + (float) Math.cos(turn) * 12, wy + (float) Math.sin(turn) * 12 + 30, 5, 0xFF9AA0A8);
        ui.line(wx + (float) Math.cos(turn) * 12, wy + (float) Math.sin(turn) * 12 + 30, hx, hy, 5, 0xFF9AA0A8);
        // the pick: from the keyway out along its angle to the arc, its handle wrapped; fades as it wears
        double pr = Math.toRadians(t.pick);
        float px = (float) Math.sin(pr), py = -(float) Math.cos(pr);
        float tipX = lx + px * 12, tipY = ly + py * 12, endX = lx + px * (CYL + PICK - 40), endY = ly + py * (CYL + PICK - 40);
        float wear = t.durability();
        int steel = wear < 0.25f ? 0xFFC88A6A : 0xFFD2D6DC;
        ui.line(tipX, tipY, endX, endY, 3.5f, steel);
        ui.line(endX - px * 46, endY - py * 46, endX + px * 10, endY + py * 10, 8, 0xFF5A3A22);
        // the pick's life, and how many are left
        float bx = x0 + 40, by = y0 + H - 70, bw = W - 80;
        ui.text(t.using == null && t.skill >= Lockpicking.FREE_HANDS_LEVEL ? "Improvised pick" : "Lockpick", bx, by - 20, 12, Style.DIM);
        String left = t.using == null && t.skill >= Lockpicking.FREE_HANDS_LEVEL ? "bend another when it breaks" : g.player.count(rpg.item.Items.LOCKPICK) + " carried";
        ui.text(left, bx + bw - ui.width(left, 12), by - 20, 12, Style.FAINT);
        ui.rect(bx, by, bw, 10, Style.WELL);
        ui.rect(bx, by, bw * Math.max(0, wear), 10, wear < 0.25f ? Style.BAD : t.struggling ? Style.WARN : Style.GOOD);
        ui.border(bx, by, bw, 10, 1, Style.EDGE);
        // what happened, or how to work it
        String line = hud.pickMsg != null && hud.pickMsgT < 2.4f ? hud.pickMsg : t.struggling ? "The pick strains..." : "Move the mouse to set the pick.  Hold the left button to turn.  Right button to stop.";
        int col = hud.pickMsg != null && hud.pickMsgT < 2.4f ? (t.open ? Style.GOOD : Style.WARN) : t.struggling ? Style.WARN : Style.FAINT;
        ui.text(line, cx - ui.width(line, 12) / 2, y0 + H - 34, 12, col);
    }

    /** Where the lock's centre is drawn on a screen of a size: the pick's angle is read round it. */
    static float[] centre(int w, int h) { return new float[]{w / 2f, (h - H) / 2f + PLATE_Y}; }
    /** How far down the window the lock's centre sits: room above for the pick, below for the plate and the pick's bar. */
    static final float PLATE_Y = 262;

    private static int gradeColor(Lockpicking.Difficulty d) {
        return switch (d) { case NOVICE -> Style.GOOD; case APPRENTICE -> Style.INFO; case ADEPT -> Style.ACCENT; case EXPERT -> Style.WARN; case MASTER -> Style.BAD; };
    }

    /** A filled disc out of a fan of triangles. */
    static void disc(Ui2D ui, float x, float y, float r, int argb) {
        int n = 40;
        for (int i = 0; i < n; i++) {
            double a0 = i * Math.PI * 2 / n, a1 = (i + 1) * Math.PI * 2 / n;
            ui.tri(x, y, x + (float) Math.cos(a0) * r, y + (float) Math.sin(a0) * r, x + (float) Math.cos(a1) * r, y + (float) Math.sin(a1) * r, argb);
        }
    }
    /** A circle's outline. */
    static void ring(Ui2D ui, float x, float y, float r, float t, int argb) {
        int n = 48;
        for (int i = 0; i < n; i++) {
            double a0 = i * Math.PI * 2 / n, a1 = (i + 1) * Math.PI * 2 / n;
            ui.line(x + (float) Math.cos(a0) * r, y + (float) Math.sin(a0) * r, x + (float) Math.cos(a1) * r, y + (float) Math.sin(a1) * r, t, argb);
        }
    }
}
