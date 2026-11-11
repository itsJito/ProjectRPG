package rpg.ui;

import java.util.ArrayList;
import java.util.List;
import rpg.Rpg;
import rpg.actor.Actor;
import rpg.ai.Companions;
import rpg.ai.Npc;

/**
 * (0.3.9, gamefixes: the ally health bars, the companion command menu, the tactical power wheel) The player's squad in the
 * interface.
 *
 * <ul>
 * <li><b>Ally frames</b> - under the player's own bars, one box per companion (up to four): their face, their name and
 *     what they are about, their health and their magic, and a pip for each ability (lit when ready). The one the orders
 *     go to is framed in gold.</li>
 * <li><b>Quick orders</b> - a tap of the Companion key (Left Alt) gives the selected companion the order that fits what
 *     the player is looking at: a foe, attack it; open ground, go there; nothing near, rally. With Shift, the whole squad.
 *     The world does not stop for it.</li>
 * <li><b>The command menu</b> - held, the key slows the world to a crawl and opens the squad: pick a companion (their
 *     frame, or 1-4), then an order - follow, rally, hold, move here, take cover, attack the marked foe - with the foe
 *     under the crosshair marked and the others in sight a click away. "Powers" opens the power wheel.</li>
 * <li><b>The power wheel</b> - the world stands still: the selected companion's abilities round the marked foe, each
 *     with its cooldown; one picked is used at once.</li>
 * </ul>
 */
final class Squad {
    private Squad() {}

    /** The world's pace while the command menu is open, and while the power wheel is. */
    static final float MENU_PACE = rpg.Tuning.f("squad.menuPace", 0.25f), WHEEL_PACE = rpg.Tuning.f("squad.wheelPace", 0);
    /** How long the Companion key must be held to open the menu (a shorter press is a quick order), s. */
    static final float HOLD_TO_OPEN = rpg.Tuning.f("squad.holdToOpen", 0.2f);
    /** How far and how wide the crosshair looks for a foe (m, rad), and how far it finds ground. */
    static final float AIM_RANGE = 45, AIM_CONE = 0.22f, GROUND_RANGE = 40;

    static void register(Hud hud) {
        hud.add(new Frames(hud));
        hud.squad = new Menu(hud);
        hud.add(hud.squad);
    }

    // ------------------------------------------------------------------ what the crosshair rests on
    /** The foes in sight, nearest the crosshair first, and the ground it points at ({x, y, z} or null). */
    static final class Aim {
        final List<Actor> foes = new ArrayList<>();
        float[] ground;
    }

    static Aim aim(Rpg g) {
        Aim a = new Aim();
        var c = g.controller;
        float fx = c.fx, fy = c.fy, fz = c.fz, l = (float) Math.sqrt(fx * fx + fy * fy + fz * fz);
        if (l < 1e-4f) return a;
        fx /= l; fy /= l; fz /= l;
        List<float[]> scored = new ArrayList<>();
        List<Actor> found = new ArrayList<>();
        for (Actor t : g.actors()) {
            if (!t.alive || t == g.player || t.dummy || g.magic.allied(g.player, t)) continue;
            boolean foe = t instanceof rpg.monster.Monster || t instanceof Npc n && (n.hostileToPlayer || n.fightTarget == g.player.id || n.companionOf == 0 && g.time - g.player.cs.lastHurt < 15 && g.player.cs.lastAttacker == n.id)
                    || t instanceof rpg.animal.Animal an && an.species.predator();
            if (!foe) continue;
            float dx = t.x - c.cx, dy = t.chestY() - c.cy, dz = t.z - c.cz, d = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (d > AIM_RANGE || d < 0.5f) continue;
            float cos = (dx * fx + dy * fy + dz * fz) / d;
            if (cos < 0.2f || !g.vox.sight(c.cx, c.cy, c.cz, t.x, t.chestY(), t.z)) continue;
            scored.add(new float[]{(float) Math.acos(Math.min(1, cos)), found.size()});
            found.add(t);
        }
        scored.sort((p, q) -> Float.compare(p[0], q[0]));
        for (float[] s : scored) a.foes.add(found.get((int) s[1]));
        // the ground: along the line of sight to the first solid cell, the open cell before it, down to its floor
        float x = c.cx, y = c.cy, z = c.cz;
        for (float t = 0.5f; t < GROUND_RANGE; t += 0.25f) {
            float nx = c.cx + fx * t, ny = c.cy + fy * t, nz = c.cz + fz * t;
            if (g.vox.solid((int) Math.floor(nx), (int) Math.floor(ny), (int) Math.floor(nz))) {
                float gy = g.vox.groundY(x, y + 0.5f, z);
                if (Float.isFinite(gy)) a.ground = new float[]{x, gy, z};
                break;
            }
            x = nx; y = ny; z = nz;
        }
        return a;
    }

    /** Whether the crosshair is on that foe (within the cone), for the quick order. */
    static boolean onCrosshair(Rpg g, Actor t) {
        var c = g.controller;
        float dx = t.x - c.cx, dy = t.chestY() - c.cy, dz = t.z - c.cz, d = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        float l = (float) Math.sqrt(c.fx * c.fx + c.fy * c.fy + c.fz * c.fz);
        return d > 0 && l > 0 && Math.acos(Math.min(1, (dx * c.fx + dy * c.fy + dz * c.fz) / (d * l))) < AIM_CONE + 0.4f / Math.max(1, d);
    }

    // ------------------------------------------------------------------ the ally frames
    static final class Frames extends Fragment {
        static final float W = 236, H = 58, GAP = 6;
        Frames(Hud h) { super("party"); fadeOut = 0.3f; }
        @Override protected boolean active() { return hud.g.player.alive && !hud.g.ai.brain.companions.of(hud.g.player).isEmpty(); }
        @Override public float width() { return W; }
        @Override public float height() { return Companions.MAX * (H + GAP); }
        @Override public void draw(Ui2D ui, float x, float y) {
            Rpg g = hud.g;
            var party = g.ai.brain.companions;
            List<Npc> l = party.of(g.player);
            int sel = hud.squad.selected(l.size());
            for (int i = 0; i < l.size(); i++) frame(ui, l.get(i), i, i == sel, x, y + i * (H + GAP));
        }
        void frame(Ui2D ui, Npc n, int index, boolean selected, float x, float y) {
            Rpg g = hud.g;
            var party = g.ai.brain.companions;
            float a = ui.alpha;
            ui.rect(x, y, W, H, Style.fade(0xD51D2023, a));
            ui.border(x, y, W, H, selected ? 2 : 1, Style.fade(selected ? Style.GOLD_LIT : 0x60C8A060, a));
            // the face, in its well
            ui.rect(x + 4, y + 4, 50, 50, Style.fade(0xFF100D0A, a));
            ui.flush();
            hud.gfx.portrait(n, x + 4, y + 2, 50, 54, hud.now + index, a);
            ui.text(String.valueOf(index + 1), x + 6, y + 40, 11, Style.fade(0xC0FFFFFF, a));
            // the name and what they are about
            float tx = x + 60;
            ui.text(n.name, tx, y + 3, 12, Style.fade(selected ? Style.GOLD_LIT : Style.TEXT, a));
            String doing = n.goal == rpg.ai.Brain.Goal.FIGHT ? "Fighting" : n.goal == rpg.ai.Brain.Goal.FLEE ? "Falling back" : switch (n.order) {
                case FOLLOW -> "Following"; case RALLY -> "Rallying"; case HOLD -> "Holding"; case MOVE -> "Moving"; case COVER -> "In cover"; case ATTACK -> "Attacking";
            };
            ui.text(doing, x + W - 6 - ui.width(doing, 10), y + 4, 10, Style.fade(n.goal == rpg.ai.Brain.Goal.FIGHT ? 0xFFE0573C : Style.DIM, a));
            // health and magic
            bar(ui, tx, y + 21, W - 66, n.hp, n.maxHp(), Style.HP, a);
            bar(ui, tx, y + 34, W - 66, n.mana, n.maxMana(), Style.MANA, a);
            // the abilities: a pip each, lit when ready, a sweep filling while it waits
            float px = tx;
            for (Companions.Ability ab : party.abilities(n)) {
                float cd = party.cooldown(n, ab);
                ui.rect(px, y + 47, 14, 6, Style.fade(0xFF090C0E, a));
                ui.rect(px, y + 47, 14 * (cd <= 0 ? 1 : 1 - cd / ab.cooldown), 6, Style.fade(cd <= 0 ? Style.GOLD_LIT : 0xFF6A5A3A, a));
                px += 18;
            }
            if (live && ui.clicked(x, y, W, H)) hud.squad.select(index);
        }
        static void bar(Ui2D ui, float x, float y, float w, float v, float max, int color, float a) {
            ui.rect(x, y, w, 9, Style.fade(0xFF090C0E, a));
            ui.rect(x, y, w * Math.max(0, Math.min(1, max <= 0 ? 0 : v / max)), 9, Style.fade(color, a));
            String s = String.format("%.0f / %.0f", v, max);
            ui.text(s, x + w - ui.width(s, 8) - 2, y, 8, Style.fade(0xE0FFFFFF, a));
        }
    }

    // ------------------------------------------------------------------ the command menu and the power wheel
    static final class Menu extends Fragment {
        static final float W = 640, H = 300, WHEEL_R = 150;
        boolean open, wheel;
        private int selected;
        private Aim aim;
        private int foe;
        private String flash; private double flashAt;
        Menu(Hud h) { super("squad"); fadeIn = 0.06f; fadeOut = 0.1f; }
        @Override protected boolean active() { return open; }
        @Override public float width() { return wheel ? WHEEL_R * 2 + 220 : W; }
        @Override public float height() { return wheel ? WHEEL_R * 2 + 80 : H; }

        /** The world's pace now (the menu slows it, the wheel stops it). */
        float pace() { return !open ? 1 : wheel ? WHEEL_PACE : MENU_PACE; }
        int selected(int count) { return count <= 0 ? -1 : Math.min(selected, count - 1); }
        void select(int i) { int c = hud.g.ai.brain.companions.of(hud.g.player).size(); if (i >= 0 && i < c) { selected = i; hud.sound("UI_hover", 0.3f); } }
        private Npc chosen() { List<Npc> l = hud.g.ai.brain.companions.of(hud.g.player); int s = selected(l.size()); return s < 0 ? null : l.get(s); }
        private Actor marked() { return aim == null || aim.foes.isEmpty() ? null : aim.foes.get(Math.floorMod(foe, aim.foes.size())); }

        void openMenu() {
            if (open || hud.g.ai.brain.companions.of(hud.g.player).isEmpty()) return;
            open = true; wheel = false; aim = aim(hud.g); foe = 0;
            hud.sound("UI_hover", 0.35f);
        }
        void close() { open = false; wheel = false; aim = null; }
        void toggleWheel() { if (open) { wheel = !wheel; hud.sound(wheel ? "UI_select" : "UI_hover", 0.35f); } }
        void cycleFoe(int d) { if (aim != null && !aim.foes.isEmpty()) foe += d; }

        /** A tap of the key: the order the crosshair suggests, for the selected companion (all of them with Shift). */
        void quickOrder(boolean all) {
            Rpg g = hud.g;
            var party = g.ai.brain.companions;
            List<Npc> l = party.of(g.player);
            if (l.isEmpty()) return;
            Aim a = aim(g);
            Actor t = a.foes.isEmpty() || !onCrosshair(g, a.foes.get(0)) ? null : a.foes.get(0);
            List<Npc> who = all ? l : List.of(l.get(selected(l.size())));
            for (Npc n : who) {
                if (t != null) party.command(n, Companions.Order.ATTACK, t.x, t.z, t.id);
                else if (a.ground != null && Math.hypot(a.ground[0] - g.player.x, a.ground[2] - g.player.z) > 3) party.command(n, Companions.Order.MOVE, a.ground[0], a.ground[2], 0);
                else party.command(n, Companions.Order.RALLY, g.player.x, g.player.z, 0);
            }
            hud.sound("UI_select", 0.3f);
        }

        private void order(Companions.Order o, boolean all) {
            Rpg g = hud.g;
            var party = g.ai.brain.companions;
            Actor t = marked();
            float[] at = aim != null && aim.ground != null ? aim.ground : new float[]{g.player.x, g.player.y, g.player.z};
            if (o == Companions.Order.ATTACK && t == null) { say("No foe marked."); return; }
            if (o == Companions.Order.HOLD) at = new float[]{chosenOrPlayer().x, 0, chosenOrPlayer().z};
            List<Npc> who = all ? party.of(g.player) : chosen() == null ? List.of() : List.of(chosen());
            for (Npc n : who) party.command(n, o, o == Companions.Order.HOLD && all ? n.x : at[0], o == Companions.Order.HOLD && all ? n.z : at[2], t == null ? 0 : t.id);
            hud.sound("UI_select", 0.35f);
            close();
        }
        private Actor chosenOrPlayer() { Npc c = chosen(); return c == null ? hud.g.player : c; }
        private void say(String s) { flash = s; flashAt = hud.now; }

        @Override public void draw(Ui2D ui, float x, float y) {
            if (wheel) { drawWheel(ui, x, y); return; }
            Rpg g = hud.g;
            var party = g.ai.brain.companions;
            List<Npc> l = party.of(g.player);
            Style.card(ui, x, y, W, H);
            ui.text(ui.heading, "Squad", x + 16, y + 10, 18, Style.GOLD_LIT);
            String hint = "1-4 select   Shift: whole squad   Space: powers   Q/E: next foe";
            ui.text(hint, x + W - 16 - ui.width(hint, 10), y + 16, 10, Style.DIM);
            // the companions, to pick from
            int sel = selected(l.size());
            float cw = (W - 32 - 3 * 8) / 4f;
            for (int i = 0; i < Companions.MAX; i++) {
                float cx = x + 16 + i * (cw + 8), cy = y + 42;
                if (i >= l.size()) { ui.rect(cx, cy, cw, 74, 0x40100D0A); ui.centered("(empty)", cx + cw / 2, cy + 30, 10, Style.DIM); continue; }
                Npc n = l.get(i);
                ui.rect(cx, cy, cw, 74, i == sel ? 0xC0302818 : 0x80100D0A);
                ui.border(cx, cy, cw, 74, i == sel ? 2 : 1, i == sel ? Style.GOLD_LIT : 0x60C8A060);
                ui.text(n.name, cx + 6, cy + 4, 12, i == sel ? Style.GOLD_LIT : Style.TEXT);
                Frames.bar(ui, cx + 6, cy + 24, cw - 12, n.hp, n.maxHp(), Style.HP, ui.alpha);
                Frames.bar(ui, cx + 6, cy + 37, cw - 12, n.mana, n.maxMana(), Style.MANA, ui.alpha);
                String doing = n.order.name().charAt(0) + n.order.name().substring(1).toLowerCase();
                ui.text(doing + (n.goal == rpg.ai.Brain.Goal.FIGHT ? " - fighting" : ""), cx + 6, cy + 54, 10, Style.DIM);
                if (live && ui.clicked(cx, cy, cw, 74)) select(i);
            }
            // the marked foe, and the others in sight
            Actor t = marked();
            float ty = y + 128;
            ui.text("Marked foe:", x + 16, ty, 12, Style.TEXT);
            String foeText = t == null ? "none in sight" : t.name + String.format(" (%.0f m)", g.player.distTo(t)) + (aim.foes.size() > 1 ? "   [" + (Math.floorMod(foe, aim.foes.size()) + 1) + " of " + aim.foes.size() + "]" : "");
            ui.text(foeText, x + 100, ty, 12, t == null ? Style.DIM : 0xFFE0573C);
            if (aim != null && aim.foes.size() > 1) {
                if (Style.button(ui, "<", x + W - 92, ty - 3, 34, 20, true) && live) cycleFoe(-1);
                if (Style.button(ui, ">", x + W - 52, ty - 3, 34, 20, true) && live) cycleFoe(1);
            }
            // the orders
            boolean all = hud.shift;
            Companions.Order[] orders = Companions.Order.values();
            String[] labels = {"Follow", "Rally", "Hold position", "Move here", "Take cover", "Attack marked"};
            float bw = (W - 32 - 2 * 8) / 3f;
            for (int i = 0; i < orders.length; i++) {
                float bx = x + 16 + (i % 3) * (bw + 8), by = y + 156 + (i / 3) * 34;
                boolean ok = l.size() > 0 && (orders[i] != Companions.Order.ATTACK || t != null) && (orders[i] != Companions.Order.MOVE && orders[i] != Companions.Order.COVER || aim != null && aim.ground != null);
                if (Style.button(ui, labels[i] + (all ? " (all)" : ""), bx, by, bw, 28, ok) && live && ok) order(orders[i], all);
            }
            if (Style.button(ui, "Powers...", x + 16, y + 232, W - 32, 30, chosen() != null) && live) toggleWheel();
            if (flash != null && hud.now - flashAt < 2) ui.centered(flash, x + W / 2, y + H - 26, 12, 0xFFE8B84A);
            else ui.centered("The world slows while the squad is up. Let the key go to return.", x + W / 2, y + H - 26, 10, Style.DIM);
        }

        private void drawWheel(Ui2D ui, float x, float y) {
            Rpg g = hud.g;
            var party = g.ai.brain.companions;
            Npc n = chosen();
            float cx = x + width() / 2, cy = y + 50 + WHEEL_R;
            Style.card(ui, x, y, width(), height());
            ui.text(ui.heading, n == null ? "Powers" : n.name + "'s powers", x + 16, y + 10, 18, Style.GOLD_LIT);
            Actor t = marked();
            String on = t == null ? "No foe marked" : "On: " + t.name + String.format(" (%.0f m)", g.player.distTo(t));
            ui.text(on, x + width() - 16 - ui.width(on, 11), y + 16, 11, t == null ? Style.DIM : 0xFFE0573C);
            if (n == null) return;
            List<Companions.Ability> ab = party.abilities(n);
            ui.ring(cx, cy, WHEEL_R, 2, 0x80C8A060);
            ui.disc(cx, cy, 44, 0xC0100D0A);
            ui.centered("Paused", cx, cy - 8, 12, Style.DIM);
            if (ab.isEmpty()) { ui.centered(n.name + " has no powers to call on.", cx, cy + 60, 11, Style.DIM); return; }
            int k = ab.size();
            for (int i = 0; i < k; i++) {
                double a0 = -Math.PI / 2 + i * 2 * Math.PI / k, a1 = a0 + 2 * Math.PI / k, mid = (a0 + a1) / 2;
                Companions.Ability a = ab.get(i);
                float cd = party.cooldown(n, a);
                float lx = cx + (float) Math.cos(mid) * WHEEL_R * 0.66f, ly = cy + (float) Math.sin(mid) * WHEEL_R * 0.66f;
                boolean hover = live && Math.hypot(ui.mx - lx, ui.my - ly) < WHEEL_R * 0.32f;
                ui.sector(cx, cy, 48, WHEEL_R - 2, a0 + 0.02, a1 - 0.02, cd > 0 ? 0x70201A12 : hover ? 0xE0503C1C : 0xB0302418);
                ui.centered(a.title, lx, ly - 14, 12, cd > 0 ? Style.DIM : Style.GOLD_LIT);
                ui.centered(cd > 0 ? String.format("%.0f s", cd) : "Ready", lx, ly + 2, 10, cd > 0 ? 0xFFB06040 : 0xFF8CC870);
                if (hover) ui.centered(a.help, cx, y + height() - 26, 11, Style.TEXT);
                if (hover && ui.click && cd <= 0) {
                    var r = party.use(n, a, t);
                    if (r.rejected()) say(r.reason()); else { hud.sound("UI_select", 0.4f); close(); return; }
                }
            }
            if (flash != null && hud.now - flashAt < 2) ui.centered(flash, cx, y + height() - 44, 12, 0xFFE8B84A);
        }
    }
}
