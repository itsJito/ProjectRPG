package rpg.ui;

import astratto.bridge.AstrattoNative;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import org.lwjgl.system.MemoryUtil;
import rpg.Chat;
import rpg.Rpg;
import rpg.item.ItemDef;
import rpg.actor.Actor;
import rpg.actor.Player;
import rpg.ai.Brain;
import rpg.ai.Memory;
import rpg.ai.Npc;
import rpg.ai.Stealth;
import rpg.interact.Interactable;
import rpg.interact.Option;
import rpg.interact.Work;
import rpg.interact.WorldObject;
import rpg.objects.Container;
import rpg.objects.Fixtures;
import rpg.objects.Furniture;
import rpg.objects.Station;
import rpg.render.Fx;
import rpg.skill.Skill;
import rpg.skill.XpTable;
import rpg.state.DomainEvent;
import rpg.world.Blocks;
import rpg.world.DevMap;

/**
 * The world's HUD fragments: what floats over the world (names, speech bubbles, hit splats), the prompt at the
 * crosshair, the minimap, the vitals, XP drops, the wanted and combat read-outs, the chat, the dialogue card, the
 * context menu and the developer inspectors. Each is wanted only while its state is live.
 */
final class HudFragments {
    private HudFragments() { }

    static void register(Hud hud) {
        Rpg g = hud.g;
        hud.add(new Overlays(hud));
        hud.add(new Prompt(hud));
        hud.add(Fragment.of("death", 0, 0, () -> !g.player.alive, (ui, x, y) -> deathScreen(hud, ui)));
        hud.add(new Vitals(hud));
        hud.add(new Sentence(hud));
        hud.add(new SneakMeter(hud));
        hud.add(new XpDrops(hud));
        hud.add(new Minimap(hud));
        hud.add(new WorldMap(hud));
        hud.add(new Area(hud)).wake("area", "world", "scene").linger(5);
        hud.add(new Wanted(hud)).wake("law").linger(8);
        hud.add(new HonorToast(hud)).wake("honor");
        hud.add(new Target(hud)).wake("combat").linger(5);
        hud.add(new Milestone(hud));
        hud.add(new ChatLog(hud)).wake("chat").linger(9);
        hud.add(new WorkBar(hud));
        hud.add(new DialogueCard(hud));
        hud.add(new PauseCard(hud));
        hud.add(new MenuFrag(hud));
        hud.add(new Inspector(hud, "ai", 440, () -> hud.aiInspector, "AI inspector", HudFragments::aiLines));
        hud.add(new Inspector(hud, "state", 480, () -> hud.stateInspector, "World state inspector", HudFragments::stateLines));
        hud.add(new Inspector(hud, "telemetry", 820, () -> hud.telemetry, "Combat telemetry", HudFragments::telemetryLines));
        hud.add(new Inspector(hud, "xpboard", 480, () -> hud.xpDashboard, "XP / level dashboard", HudFragments::xpLines));
    }

    /**
     * The Honor bar (GDD 3.5): -100 to +100, the seventeen ranks ticked along it, and a face that physically slides to
     * the exact score - a sinister scowl at the left end, a level mouth in the middle, a broad smile at the right - so it
     * reads without its colours. shown: the score the face is at now (eased toward the real one by the caller).
     */
    static void honorBar(Ui2D ui, float x, float y, float w, float shown, int score) {
        float h = 10;
        // the track: dark red through slate to gold, in segments
        int seg = 40;
        for (int i = 0; i < seg; i++) {
            float t = i / (float) (seg - 1), v = t * 2 - 1;
            int c = v < 0 ? mixc(0xFF5A1A1A, 0xFF3A3E46, v + 1) : mixc(0xFF3A3E46, 0xFF9C7A2A, v);
            ui.rect(x + w * i / seg, y, w / seg + 1, h, c);
        }
        ui.border(x - 1, y - 1, w + 2, h + 2, 1, 0x90000000);
        // the rank boundaries, and the thresholds that matter (the discounts at +33 +55 +77 +89) a little taller
        int[] ticks = {-88, -76, -65, -54, -43, -32, -21, -10, 11, 22, 33, 44, 55, 66, 77, 89};
        for (int t : ticks) {
            boolean big = t == 33 || t == 55 || t == 77 || t == 89;
            float tx = x + (t + 100) / 200f * w;
            ui.rect(tx, y - (big ? 3 : 0), 1, h + (big ? 6 : 0), big ? 0xC0F0D890 : 0x50FFFFFF);
        }
        ui.rect(x + w / 2, y - 4, 1, h + 8, 0xA0FFFFFF);
        face(ui, x + (shown + 100) / 200f * w, y + h / 2, 12, shown / 100f);
    }

    private static int mixc(int a, int b, float t) {
        t = Math.max(0, Math.min(1, t));
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t), g2 = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t), bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return 0xFF000000 | r << 16 | g2 << 8 | bl;
    }

    /**
     * The face on the Honor bar: m from -1 (evil) to +1 (good). The brows slant down to a scowl and the eyes narrow as m
     * falls; the mouth's curve runs from a frown through a level line to a smile, its corners lifting as m rises.
     */
    static void face(Ui2D ui, float cx, float cy, float r, float m) {
        int skin = m < 0 ? mixc(0xFFE8D2B0, 0xFFB0A090, -m) : mixc(0xFFE8D2B0, 0xFFF4DE9A, m);
        ui.disc(cx, cy, r + 1.6f, 0xF0101010);
        ui.disc(cx, cy, r, skin);
        int ink = 0xFF201810;
        float ey = cy - r * 0.18f, ex = r * 0.38f;
        float open = 0.16f + 0.12f * Math.max(0, m) - 0.06f * Math.max(0, -m);
        for (int s = -1; s <= 1; s += 2) {
            ui.rect(cx + s * ex - r * 0.1f, ey - r * open, r * 0.2f, r * open * 2, ink);
            // the brow: level for good humour, slanting to the nose (a scowl) for ill
            float slant = -m * r * 0.22f, bx = cx + s * ex;
            ui.line(bx - r * 0.24f, ey - r * 0.36f + (s > 0 ? -slant : slant) * 0.5f, bx + r * 0.24f, ey - r * 0.36f - (s > 0 ? -slant : slant) * 0.5f, 1.4f, ink);
        }
        // the mouth: a curve of short strokes, its corners up (a smile) or down (a frown)
        float my = cy + r * 0.38f, mw = r * 0.5f, curve = m * r * 0.32f;
        float px = cx - mw, py = my - curve;
        for (int i = 1; i <= 8; i++) {
            float t = i / 8f * 2 - 1, nx = cx + t * mw, ny = my - curve * (t * t);
            ui.line(px, py, nx, ny, 1.6f, ink);
            px = nx; py = ny;
        }
        if (m < -0.6f) ui.line(cx - r * 0.1f, my - curve * 0.2f + 1, cx + r * 0.15f, my - curve * 0.25f + 2, 1.2f, 0xFF8A2018);   // a sneer
    }

    /** The Honor toast: when Honor moves, the bar with its face sliding to the new score, the change and its reason. */
    static final class HonorToast extends Fragment {
        private float shown = 0;
        private boolean init;
        HonorToast(Hud h) { super("honor"); fadeIn = 0.2f; fadeOut = 0.4f; linger = 6 - fadeOut; }
        @Override protected boolean active() { return false; }
        @Override public float width() { return 380; }
        @Override public float height() { return 74; }
        @Override public void draw(Ui2D ui, float x, float y) {
            var hn = hud.g.honor;
            if (!init) { shown = hn.score - hn.lastDelta; init = true; }
            shown += (hn.score - shown) * Math.min(1, hud.dt * 3.2f);
            Style.card(ui, x, y, 380, 74);
            String d = (hn.lastDelta > 0 ? "+" : "") + hn.lastDelta;
            ui.text("Honor  " + d, x + 14, y + 8, 14, hn.lastDelta >= 0 ? 0xFFE9D27A : 0xFFE07060);
            ui.text(hn.rankName() + "  (" + (hn.score > 0 ? "+" : "") + hn.score + ")", x + 380 - 14 - ui.width(hn.rankName() + "  (" + (hn.score > 0 ? "+" : "") + hn.score + ")", 13), y + 9, 13, Style.TEXT);
            honorBar(ui, x + 22, y + 34, 336, shown, hn.score);
            ui.text(hn.lastWhy, x + 14, y + 54, 11, Style.DIM);
        }
    }

    private static float ease(float t) { t = Math.max(0, Math.min(1, t)); return t * t * (3 - 2 * t); }

    /**
     * The death screen (gamefixes): the frame closes to a letterbox as the camera finds the killer; the words come up
     * through the grey - what happened, who did it, what becomes of what was carried - and after the five seconds the
     * choice: Continue (wake at the graveyard) or Exit (to the title). Enter is Continue.
     */
    static void deathScreen(Hud hud, Ui2D ui) {
        Rpg g = hud.g;
        var d = g.death;
        float w = hud.w, h = hud.h, t = d.active ? d.elapsed() : rpg.PlayerDeath.LOCK + 1;
        // the letterbox, sliding in
        float bar = h * 0.12f * ease(t / 1.4f);
        ui.rect(0, 0, w, bar, 0xF0000000);
        ui.rect(0, h - bar, w, bar, 0xF0000000);
        // a darkening toward the edges and the bottom, under the words
        float dark = ease((t - 0.6f) / 2f);
        for (int i = 0; i < 12; i++) ui.rect(0, h * 0.45f + i * h * 0.035f, w, h * 0.035f + 1, Style.fade(0xFF000000, dark * 0.05f * i));
        float cx = w / 2f, cy = h * 0.36f;
        // a soft dark band behind the words, so they read over whatever the camera finds
        float band = ease((t - 0.5f) / 1.5f);
        for (int i = 0; i < 16; i++) {
            float k = 1 - Math.abs(i - 7.5f) / 8f;
            ui.rect(0, cy - 40 + i * 9, w, 9, Style.fade(0xFF000000, band * 0.42f * k));
        }
        // the line: letters spaced wide, rising into place
        float a1 = ease((t - 0.7f) / 1.5f);
        String title = "Y O U   H A V E   F A L L E N";
        float ts = Math.min(52, w / 22f);
        float rise = (1 - a1) * 14;
        ui.text(title, cx - ui.width(title, ts) / 2 + 2, cy + rise + 2, ts, Style.fade(0xFF000000, a1 * 0.8f));
        ui.text(title, cx - ui.width(title, ts) / 2, cy + rise, ts, Style.fade(0xFFE9E1D2, a1));
        // a red rule drawing out from the middle
        float rule = ease((t - 1.3f) / 1.2f) * Math.min(520, w * 0.4f);
        ui.rect(cx - rule / 2, cy + ts * 1.25f, rule, 2, Style.fade(0xFF9A1E1A, a1));
        ui.rect(cx - rule / 2, cy + ts * 1.25f + 2, rule, 1, Style.fade(0xFF3A0A08, a1));
        // what killed them
        float a2 = ease((t - 1.9f) / 1.2f);
        Actor k = d.killer != 0 ? g.actor(d.killer) : null;
        String by = d.killerName == null ? "Your wounds were the end of you." : "Slain by " + d.killerName
                + (k != null && !(k instanceof rpg.animal.Animal) ? ", level " + k.skills.combatLevel() : "") + ".";
        ui.centeredFade(by, cx, cy + ts * 1.25f + 16, 19, Style.fade(0xFFCFC6B6, a2));
        // what becomes of their things
        float a3 = ease((t - 3.0f) / 1.2f);
        String claim = d.secured ? "Inside the walls. Your belongings will rest in a grave where you fell - guarded: nobody may touch them."
                : "Outside the walls. Your belongings will rest in a grave where you fell - exposed: anyone who finds it may take from it.";
        ui.centeredFade(claim, cx, cy + ts * 1.25f + 48, 13, Style.fade(d.secured ? 0xFF9DB7C9 : 0xFFD6A06A, a3));
        // the choice, once the five seconds are over
        if (!d.choosing()) {
            // the seconds running out, as a thin line filling under the words
            float p = Math.min(1, t / rpg.PlayerDeath.LOCK);
            ui.rect(cx - 90, h - bar - 34, 180, 2, 0x30FFFFFF);
            ui.rect(cx - 90, h - bar - 34, 180 * p, 2, 0x90E9E1D2);
            return;
        }
        float a4 = ease((t - rpg.PlayerDeath.LOCK) / 0.6f);
        float bw = 230, bh = 54, gap = 26, by0 = h - bar - bh - 46;
        String[] labels = {"Continue", "Exit"};
        String[] notes = {"Wake at the graveyard", "Return to the title"};
        for (int i = 0; i < 2; i++) {
            float bx = cx - bw - gap / 2 + i * (bw + gap);
            boolean hv = ui.hover(bx, by0, bw, bh);
            float glow = hv ? 1 : 0;
            ui.rect(bx, by0, bw, bh, Style.fade(hv ? 0xE0241A16 : 0xC0141010, a4));
            ui.border(bx, by0, bw, bh, 1, Style.fade(hv ? 0xFFC9A26A : 0x70E9E1D2, a4));
            ui.rect(bx + 1, by0 + 1, bw - 2, 1, Style.fade(0x30FFFFFF, a4));
            if (hv) ui.rect(bx, by0 + bh - 3, bw, 3, Style.fade(0xFF9A1E1A, a4));
            ui.centeredFade(labels[i], bx + bw / 2, by0 + 9, 20, Style.fade(hv ? 0xFFF4E7C8 : 0xFFE0D6C4, a4));
            ui.centeredFade(notes[i], bx + bw / 2, by0 + 34, 11, Style.fade(hv ? 0xFFC9B79A : 0xFF8F877A, a4 * (0.7f + 0.3f * glow)));
            if (a4 > 0.5f && ui.clicked(bx, by0, bw, bh)) {
                ui.consumed = true;
                if (g.sfx != null) g.sfx.ui(i == 0 ? "UI_select" : "LogoutProfileButton", 0.6f);
                d.proceed(i == 1);
                return;
            }
        }
        ui.centeredFade("Enter to continue", cx, by0 + bh + 12, 11, Style.fade(0xFF8F877A, a4 * 0.8f));
    }

    /**
     * A skull (gamefixes: the grave's map marker is just a skull): the cranium, cheekbones narrowing to the jaw, dark
     * sockets, the nose, a row of teeth; outlined so it reads on any ground.
     */
    static void skull(Ui2D ui, float x, float y, float s, int bone) {
        int edge = 0xE0101010, hole = 0xFF141414;
        ui.disc(x, y - s * 0.1f, s * 0.5f + 1.2f, edge);
        ui.rect(x - s * 0.3f - 1.2f, y + s * 0.1f, s * 0.6f + 2.4f, s * 0.36f + 1.2f, edge);
        ui.disc(x, y - s * 0.1f, s * 0.5f, bone);
        ui.rect(x - s * 0.3f, y + s * 0.1f, s * 0.6f, s * 0.36f, bone);
        ui.disc(x - s * 0.19f, y + s * 0.0f, s * 0.14f, hole);
        ui.disc(x + s * 0.19f, y + s * 0.0f, s * 0.14f, hole);
        ui.rect(x - s * 0.05f, y + s * 0.16f, s * 0.1f, s * 0.1f, hole);
        for (int i = -1; i <= 1; i++) ui.rect(x + i * s * 0.12f - 0.5f, y + s * 0.32f, 1, s * 0.14f, hole);
    }

    // ------------------------------------------------------------------ over the world
    static final class Overlays extends Fragment {
        private final float[] p2 = new float[3];
        Overlays(Hud h) { super("overlays"); }
        @Override public float width() { return 0; }
        @Override public float height() { return 0; }
        @Override public void draw(Ui2D ui, float x0, float y0) {
            Rpg g = hud.g;
            var gfx = hud.gfx;
            for (Object[] s : DevMap.SIGNS) {
                float x = (float) s[1], z = (float) s[2];
                float d = (float) Math.hypot(x - g.player.x, z - g.player.z);
                if (d > 22 || !gfx.project(x, DevMap.FLOOR + 3.1f, z, p2)) continue;
                float a = Math.min(1, (22 - d) / 6);
                String t = (String) s[0];
                float tw = ui.width(t, 13);
                ui.rect(p2[0] - tw / 2 - 6, p2[1] - 3, tw + 12, 20, Style.fade(0xB0141518, a));
                ui.text(t, p2[0] - tw / 2, p2[1], 13, Style.fade(Style.ACCENT, a));
            }
            Dialog talk = hud.dialog;
            for (Npc n : g.npcs) {
                if (!n.alive || !n.loaded) continue;
                float d = n.distTo(g.player);
                if (d > 26 || !gfx.project(n.x, n.y + n.height + 0.35f, n.z, p2)) continue;
                boolean focus = g.interact.focus == n;
                String says = talk != null && talk.npc == n.id ? talk.text : hud.settings.values.subtitles ? n.speech : null;
                float top = p2[1] - 16;
                if (d <= 18) {
                    if (focus || d < 7) {
                        String label = n.name + (focus ? "  (level " + n.skills.combatLevel() + ")" : "");
                        ui.centered(label, p2[0], top, 12, n.hostileToPlayer ? 0xFFFF6040 : Style.NPC);
                        // what they think of you, if they know of you at all: a pip by the name (and a word when focused)
                        if (n.knowsPlayer && !n.hostileToPlayer) {
                            float op = g.ai.rumors.opinion(n), px = p2[0] - ui.width(label, 12) / 2 - 9;
                            int pc = op > 0.25f ? 0xFF6AC46A : op < -0.25f ? 0xFFE0574C : 0xFFB8B2A6;
                            ui.disc(px, top + 7, 4, 0xE0101010); ui.disc(px, top + 7, 3, pc);
                            if (focus) ui.centered(op > 0.5f ? "thinks well of you" : op > 0.25f ? "friendly" : op < -0.6f ? "dreads you" : op < -0.25f ? "wary of you" : "knows of you", p2[0], top + 15, 10, pc);
                        }
                    }
                    if (g.time - n.cs.lastHurt < 6 || n.goal == Brain.Goal.FIGHT) { bar(ui, p2[0], p2[1], n.hp / n.maxHp()); }
                }
                if (says != null && d <= 18) {
                    // talking: the words in the bubble, and how they are (if anything) at the bubble's side
                    float bw = bubble(ui, says, p2[0], top - 4, d);
                    mood(ui, n, Math.max(14, p2[0] - bw / 2 - 14), top - 20, d);
                } else if (!awareness(ui, n, p2[0], top - 14)) mood(ui, n, p2[0], top - 16, d);
            }
            for (Actor d : g.dummies) if (g.time - d.cs.lastHurt < 4 && gfx.project(d.x, d.y + 2.1f, d.z, p2)) ui.centered(d.name, p2[0], p2[1] - 16, 12, Style.NPC);
            for (Fx.Floating f : g.fx.floating) {
                float t = (float) (g.time - f.born());
                if (!gfx.project(f.x(), f.y() + t * f.rise(), f.z(), p2)) continue;
                float a = Math.max(0, Math.min(1, (1.6f - t) / 0.5f));
                boolean number = f.text().chars().allMatch(Character::isDigit);
                if (number) {
                    boolean zero = f.text().equals("0");
                    ui.disc(p2[0], p2[1] + 8, 11, Style.fade(zero ? 0xFF2F5CA8 : 0xFFA82A22, a));
                    ui.flatCentered(f.text(), p2[0], p2[1], 14, Style.fade(0xFFFFFFFF, a));
                } else ui.centered(f.text(), p2[0], p2[1], 15, Style.fade(0xFF000000 | f.color(), a));
            }
            if (g.player.alive && g.time - g.player.cs.lastHurt < 6 && !g.player.firstPerson && gfx.project(g.player.x, g.player.y + 2.15f, g.player.z, p2))
                bar(ui, p2[0], p2[1], g.player.hp / g.player.maxHp());
        }

        /**
         * How aware an NPC is of the player, over its head: a ring that fills as it grows, with "?" while it wonders or
         * searches and "!" once it is sure. Shown while the player creeps, or for a moment after it changes.
         */
        private boolean awareness(Ui2D ui, Npc n, float x, float y) {
            Rpg g = hud.g;
            Stealth.Aware w = g.ai.stealth.of(n, g.player);
            if (w == null || w.level < 0.04f) return false;
            boolean recent = g.time - w.changedAt < 3;
            if (!g.player.sneaking && !recent) return false;
            Stealth.Level lv = w.level();
            float a = g.player.sneaking ? 1 : Math.min(1, (float) (3 - (g.time - w.changedAt)) / 0.8f);
            int c = switch (lv) { case ALERT -> 0xFFE0573C; case INVESTIGATING -> 0xFFE8B84A; case CURIOUS -> 0xFFE8E2D4; default -> 0xFF9A958C; };
            float share = Math.min(1, w.level / Stealth.ALERT), r = 9;
            ui.disc(x, y, r + 2, Style.fade(0xB0141518, a));
            // the ring: segments lit up to the share
            int segs = 20;
            for (int i = 0; i < segs; i++) {
                double t = i / (double) segs * 2 * Math.PI - Math.PI / 2;
                boolean lit = i < Math.round(share * segs);
                ui.rect(x + (float) Math.cos(t) * r - 1, y + (float) Math.sin(t) * r - 1, 2.4f, 2.4f, Style.fade(lit ? c : 0x50FFFFFF, a));
            }
            if (lv != Stealth.Level.UNAWARE) ui.flatCentered(lv == Stealth.Level.ALERT ? "!" : "?", x, y - 7, 13, Style.fade(c, a));
            return true;
        }

        /**
         * How an NPC is, at a glance (gamefixes: visual feedback on what the AI feels and knows): a badge over the head -
         * afraid (an orange "!" with a bead of sweat) when fleeing or hiding, a "?" while looking into something (its ring
         * filling as the look goes on), a blue "!" running for the guard, an eye while a guard watches you, a fist when
         * fighting, "..." while trading news, "z" asleep. Faded with distance; nothing over the ordinary and content.
         */
        private void mood(Ui2D ui, Npc n, float x, float y, float d) {
            Rpg g = hud.g;
            float a = Math.max(0, Math.min(1, (26 - d) / 8)) * 0.95f;
            if (a <= 0.02f) return;
            float bob = (float) Math.sin(g.time * 3 + n.id) * 1.2f;
            y += bob;
            var v = n.investigation;
            switch (n.goal) {
                case FLEE, HIDE -> { badge(ui, x, y, 0xFFE8913A, "!", a); ui.disc(x + 9, y - 7, 3, Style.fade(0xFF7CC8F0, a)); ui.rect(x + 8, y - 12, 2, 4, Style.fade(0xFF7CC8F0, a)); }
                case FIGHT -> badge(ui, x, y, 0xFFC8362C, "!", a);
                case REPORT_CRIME, WARN -> badge(ui, x, y, 0xFF3C78C8, "!", a);
                case SEARCH -> badge(ui, x, y, 0xFFE07A2C, "?", a);
                case WATCH -> eye(ui, x, y, a);
                case SLEEP -> { if (n.speech == null) for (int i = 0; i < 3; i++) { float t = (float) ((g.time * 0.6 + i / 3.0) % 1.0); ui.text("z", x + 4 + t * 10, y - t * 18, 10 + t * 5, Style.fade(0xFFE8E2D4, a * (1 - t))); } }
                case INVESTIGATE, LOOK -> {
                    if (v != null && v.stage == rpg.ai.Investigation.Stage.FETCHING) { badge(ui, x, y, 0xFF3C78C8, "!", a); break; }
                    if (v != null && v.stage == rpg.ai.Investigation.Stage.HIDING) { badge(ui, x, y, 0xFFE8913A, "!", a); break; }
                    int c = n.goal == Brain.Goal.LOOK ? 0xFF9A958C : 0xFFE8B84A;
                    badge(ui, x, y, c, "?", a);
                    if (v != null) {   // the look going on: a ring round the badge filling with the plan's progress
                        float share = Math.min(1, (float) (g.time - v.startedAt) / 12f);
                        int segs = 16;
                        for (int i = 0; i < Math.round(share * segs); i++) {
                            double t = i / (double) segs * 2 * Math.PI - Math.PI / 2;
                            ui.rect(x + (float) Math.cos(t) * 11 - 1, y + (float) Math.sin(t) * 11 - 1, 2.2f, 2.2f, Style.fade(c, a));
                        }
                    }
                }
                default -> {
                    if (g.time < n.gossipUntil) {
                        ui.rect(x - 11, y - 8, 22, 14, Style.fade(0xF0F4EEE0, a));
                        for (int i = 0; i < 3; i++) ui.disc(x - 6 + i * 6, y - 1, 1.6f, Style.fade(0xFF2A2620, a));
                        for (int i = 0; i < 4; i++) ui.rect(x - 4 + i, y + 6 + i, 4 - i, 1, Style.fade(0xF0F4EEE0, a));
                    }
                }
            }
        }

        private void badge(Ui2D ui, float x, float y, int c, String s, float a) {
            ui.disc(x, y, 9.5f, Style.fade(0xE0101010, a));
            ui.disc(x, y, 8, Style.fade(c, a));
            ui.flatCentered(s, x, y - 7.5f, 13, Style.fade(0xFFFFFFFF, a));
        }

        private void eye(Ui2D ui, float x, float y, float a) {
            ui.disc(x, y, 9.5f, Style.fade(0xE0101010, a));
            for (int i = -6; i <= 6; i++) { float hh = (float) Math.sqrt(Math.max(0, 1 - (i / 7f) * (i / 7f))) * 4.2f; ui.rect(x + i - 0.5f, y - hh, 1.2f, hh * 2, Style.fade(0xFFF2EEE6, a)); }
            ui.disc(x, y, 2.6f, Style.fade(0xFF2A4A7A, a));
            ui.disc(x, y, 1.2f, Style.fade(0xFF101010, a));
        }

        private void bar(Ui2D ui, float x, float y, float share) {
            ui.rect(x - 16, y, 32, 4, 0xC0101010);
            ui.rect(x - 16, y, 32 * Math.max(0, Math.min(1, share)), 4, share > 0.35f ? Style.GOOD : Style.BAD);
        }

        /**
         * What an NPC says, in a speech bubble over their head (dialogue is text, never a voice): dark words on a pale
         * card, wrapped to a readable measure, with a tail down to the speaker; it fades with distance.
         */
        private float bubble(Ui2D ui, String text, float x, float bottom, float dist) {
            float size = 14, maxW = 260, pad = 8, lh = 17;
            List<String> lines = wrap(ui, text, size, maxW);
            float bw = 0;
            for (String l : lines) bw = Math.max(bw, ui.width(l, size));
            bw += pad * 2;
            float bh = lines.size() * lh + pad * 2 - 4;
            float bx = Math.max(4, Math.min(hud.w - bw - 4, x - bw / 2)), by = bottom - bh - 9;
            float a = Math.max(0.4f, Math.min(1, (18 - dist) / 6));
            ui.rect(bx - 1, by - 1, bw + 2, bh + 2, Style.fade(0xFF2A2620, a));
            ui.rect(bx, by, bw, bh, Style.fade(0xF8F4EEE0, a));
            for (int i = 0; i < 7; i++) ui.rect(x - 7 + i, by + bh + i, 14 - 2 * i, 1, Style.fade(0xF8F4EEE0, a));
            for (int i = 0; i < lines.size(); i++) ui.text(lines.get(i), bx + pad, by + pad - 3 + i * lh, size, Style.fade(0xFF1C1914, a));
            return bw;
        }
    }

    static List<String> wrap(Ui2D ui, String text, float size, float maxW) {
        List<String> lines = new ArrayList<>();
        for (String para : text.split("\n")) {
            StringBuilder line = new StringBuilder();
            for (String word : para.split(" ")) {
                String t = line.length() == 0 ? word : line + " " + word;
                if (ui.width(t, size) > maxW && line.length() > 0) { lines.add(line.toString()); line = new StringBuilder(word); }
                else { line.setLength(0); line.append(t); }
            }
            lines.add(line.toString());
        }
        return lines;
    }

    // ------------------------------------------------------------------ the crosshair and what it is on
    static final class Prompt extends Fragment {
        Prompt(Hud h) { super("prompt"); fadeIn = 0.08f; fadeOut = 0.15f; }
        @Override protected boolean active() { return hud.g.player.alive && hud.menu == null && hud.dialog == null; }
        @Override public float width() { return 0; }
        @Override public float height() { return 0; }
        @Override public void draw(Ui2D ui, float x0, float y0) {
            Rpg g = hud.g;
            float cx = hud.w / 2f, cy = hud.h / 2f;
            Player p = g.player;
            if (hud.scene == Hud.Scene.WORLD) {
                hud.gfx.drawThrowGuide(ui);
                ItemDef held = p.main();
                boolean ranged = held != null && (held.family == ItemDef.Family.BOW || held.family == ItemDef.Family.CROSSBOW);
                float gap = ranged ? 6 + 10 * (1 - p.cs.draw) : 4;
                int aimColor = p.cs.reload > 0 ? Style.DIM : 0xD0FFFFFF;
                ui.rect(cx - 1, cy - 1, 2, 2, aimColor);
                if (ranged) {
                    if (held.family == ItemDef.Family.CROSSBOW && p.cs.reload <= 0) gap = 6;
                    ui.line(cx - gap - 5, cy, cx - gap, cy, 1.5f, aimColor);
                    ui.line(cx + gap, cy, cx + gap + 5, cy, 1.5f, aimColor);
                    ui.line(cx, cy - gap - 5, cx, cy - gap, 1.5f, aimColor);
                    ui.line(cx, cy + gap, cx, cy + gap + 5, 1.5f, aimColor);
                    if (p.cs.reload > 0) ui.flatCentered("Reloading", cx, cy + 29, 11, Style.DIM);
                } else ui.ring(cx, cy, 6, 1.2f, 0x50FFFFFF);
                double hitAge = g.time - g.combat.lastPlayerHit, killAge = g.time - g.combat.lastPlayerKill;
                if (hitAge >= 0 && hitAge < 0.22 || killAge >= 0 && killAge < 0.3) {
                    boolean kill = killAge >= 0 && killAge < 0.3;
                    float fade = (float) (1 - (kill ? killAge / 0.3 : hitAge / 0.22));
                    int color = Style.fade(kill ? 0xFFFF3434 : 0xFFFFFFFF, fade);
                    for (int sx : new int[]{-1, 1}) for (int sy : new int[]{-1, 1})
                        ui.line(cx + sx * 7, cy + sy * 7, cx + sx * 12, cy + sy * 12, 2, color);
                    if (hitAge >= 0 && hitAge < 0.06) ui.rect(0, 0, hud.w, hud.h, Style.fade(0x0CFFF0D0, (float) (1 - hitAge / 0.06)));
                }
            }
            if (p.cs.charge > 0.05f) Style.bar(ui, cx - 16, cy + 14, 32, 2, p.cs.charge, p.cs.charge >= 0.7f ? Style.ACCENT : Style.TEXT);
            if (p.cs.draw > 0) ui.ring(cx, cy, 16 - p.cs.draw * 8, 1.5f, p.cs.draw >= 1 ? Style.GOOD : 0xA0FFFFFF);
            Interactable f = g.interact.focus;
            if (f == null || hud.scene != Hud.Scene.WORLD) return;
            Option best = null;
            for (Option o : g.interact.focusOptions) if (best == null && o.enabled()) best = o;
            if (best == null && !g.interact.focusOptions.isEmpty()) best = g.interact.focusOptions.get(0);
            if (best == null) return;
            int more = Math.max(0, g.interact.focusOptions.size() - 1);
            String line = best.label() + "  " + f.label();
            float tw = ui.width(line, 14) + 34;
            ui.rect(cx - tw / 2, cy + 22, tw, 24, 0xA0141518);
            ui.rect(cx - tw / 2 + 4, cy + 26, 16, 16, 0x30FFFFFF);
            ui.flatCentered("E", cx - tw / 2 + 12, cy + 27, 12, Style.TEXT);
            ui.text(line, cx - tw / 2 + 26, cy + 26, 14, best.enabled() ? Style.TEXT : Style.BAD);
            if (!best.enabled() && best.reason() != null) ui.flatCentered(best.reason(), cx, cy + 50, 12, Style.BAD);
            else if (more > 0) ui.flatCentered("hold E for " + more + " more", cx, cy + 50, 11, Style.DIM);
        }
    }

    static final class WorkBar extends Fragment {
        WorkBar(Hud h) { super("work"); fadeOut = 0.25f; }
        @Override protected boolean active() { return hud.g.interact.of(hud.g.player) != null; }
        @Override public float width() { return 180; }
        @Override public float height() { return 26; }
        @Override public void draw(Ui2D ui, float x, float y) {
            Work wk = hud.g.interact.of(hud.g.player);
            if (wk == null) return;
            Style.bar(ui, x, y, 180, 4, wk.progress(), Style.ACCENT);
            ui.flatCentered(wk.verb + "...", x + 90, y + 8, 12, Style.TEXT);
        }
    }

    // ------------------------------------------------------------------ vitals: compact, independently timed resource rows
    static final class Vitals extends Fragment {
        private final float[] previous = {Float.NaN, Float.NaN, Float.NaN, Float.NaN};
        private final double[] until = new double[4];
        private final float[] opacity = new float[4];
        Vitals(Hud h) { super("vitals"); fadeOut = 0.25f; }
        @Override protected boolean active() {
            Player p = hud.g.player;
            float[] values = {p.hp, p.stamina, p.mana, p.faith};
            boolean combat = hud.g.time - p.cs.lastHurt < 6 || p.cs.busy() || p.cs.blocking;
            boolean shown = false;
            for (int i = 0; i < 4; i++) {
                if (Float.isNaN(previous[i]) || Math.abs(values[i] - previous[i]) > 0.05f) until[i] = hud.g.time + 6;
                previous[i] = values[i];
                if (combat) until[i] = hud.g.time + 2;
                opacity[i] = Math.min(1, Math.max(0, (float) (until[i] - hud.g.time) / 0.4f));
                shown |= opacity[i] > 0;
            }
            return p.alive && shown;
        }
        @Override public float width() { return 236; }
        @Override public float height() { return 116; }
        @Override public void draw(Ui2D ui, float x, float y) {
            Player p = hud.g.player;
            String[] names = {"Health", "Stamina", "Magic", "Faith"};
            String[] icons = {"Health_Icon", "Agility_Icon", "Magic_Icon", "Faith_Icon"};
            float[] values = {p.hp, p.stamina, p.mana, p.faith};
            float[] max = {p.maxHp(), p.maxStamina(), p.maxMana(), p.maxFaith()};
            int[] colors = {Style.HP, Style.STAMINA, Style.MANA, Style.FAITH};
            for (int i = 0; i < 4; i++) {
                float yy = y + i * 28, a = opacity[i];
                if (a <= 0) continue;
                ui.rect(x, yy, 236, 24, Style.fade(0xD51D2023, a));
                ui.img(icons[i], x + 5, yy + 4, 16, Style.fade(0xFFFFFFFF, a));
                ui.text(names[i], x + 27, yy + 2, 11, Style.fade(Style.TEXT, a));
                String amount = String.format("%.0f / %.0f", values[i], max[i]);
                ui.text(amount, x + 226 - ui.width(amount, 10), yy + 2, 10, Style.fade(Style.TEXT, a));
                ui.rect(x + 27, yy + 17, 198, 3, Style.fade(0xFF090C0E, a));
                ui.rect(x + 27, yy + 17, 198 * Math.max(0, Math.min(1, max[i] <= 0 ? 0 : values[i] / max[i])), 3, Style.fade(colors[i], a));
            }
        }
    }

    static final class Sentence extends Fragment {
        Sentence(Hud h) { super("sentence"); }
        @Override protected boolean active() { return hud.g.law.inCustody(hud.g.player); }
        @Override public float width() { return 360; }
        @Override public float height() { return 70; }
        @Override public void draw(Ui2D ui, float x, float y) {
            Style.card(ui, x, y, width(), height());
            ui.text("IN CUSTODY  /  " + hud.g.law.sentenceJurisdiction(), x + 12, y + 9, 13, Style.ACCENT);
            ui.text(String.format("Sentence remaining: %.0f minutes   |   Admission %d", Math.ceil(hud.g.law.sentenceRemaining()), hud.g.law.jailAdmissions()), x + 12, y + 30, 12, Style.TEXT);
            ui.text("Sleep on the cell slab to serve the remaining time.", x + 12, y + 49, 12, Style.DIM);
        }
    }

    // ------------------------------------------------------------------ the sneak meter: only while creeping
    static final class SneakMeter extends Fragment {
        private float shown;
        SneakMeter(Hud h) { super("sneak"); fadeOut = 0.4f; }
        @Override protected boolean active() { return hud.g.player.sneaking && hud.g.player.alive; }
        @Override public float width() { return 260; }
        @Override public float height() { return 22; }
        @Override public void draw(Ui2D ui, float x, float y) {
            Rpg g = hud.g;
            Player p = g.player;
            float light = g.ai.stealth.light(p), seen = g.ai.stealth.mostExposed(p);
            shown += (Math.min(1, seen / Stealth.CLEAR) - shown) * 0.2f;
            Npc who = g.ai.stealth.mostAware(p);
            Stealth.Level lv = who == null ? Stealth.Level.UNAWARE : g.ai.stealth.level(who, p);
            String word = lv == Stealth.Level.ALERT ? "Detected" : lv == Stealth.Level.INVESTIGATING ? "Searched for" : lv == Stealth.Level.CURIOUS ? "Noticed" : shown > 0.5f ? "Exposed" : shown > 0.05f ? "Glimpsed" : "Hidden";
            int c = lv == Stealth.Level.ALERT ? 0xFFE0573C : lv == Stealth.Level.INVESTIGATING ? 0xFFE8B84A : Style.TEXT;
            Style.card(ui, x, y, 260, 22);
            ui.img("Thieving_Icon", x + 5, y + 4, 14, 0xE0FFFFFF);
            ui.text(word, x + 24, y + 4, 12, c);
            // how exposed, and under it how lit the ground is
            Style.bar(ui, x + 110, y + 6, 140, 5, shown, lv == Stealth.Level.ALERT ? 0xFFE0573C : 0xFFD8CFB8);
            Style.bar(ui, x + 110, y + 14, 140, 2, light, 0xFF8C8670);
            if (ui.hover(x, y, 260, 22)) hud.tooltip(String.format("Light here %.0f%%. Creeping halves how easily you are seen and quietens your steps.", light * 100));
        }
    }

    static final class XpDrops extends Fragment {
        XpDrops(Hud h) { super("xpdrops"); fadeOut = 0.3f; }
        @Override protected boolean active() { var d = hud.g.prog.drops; return !d.isEmpty() && hud.g.time - d.peekLast().at() < 2.4; }
        @Override public float width() { return 120; }
        @Override public float height() { return 120; }
        @Override public void draw(Ui2D ui, float x, float y) {
            for (var dr : hud.g.prog.drops) {
                float t = (float) (hud.g.time - dr.at());
                if (t > 2.4f) continue;
                float yy = y + 100 - t * 40, a = Math.max(0, Math.min(1, (2.4f - t) / 0.6f));
                ui.img(Atlas.skill(dr.skill()), x, yy, 16, Style.fade(0xFFFFFFFF, a));
                ui.text("+" + String.format("%,d", dr.amount()), x + 22, yy, 14, Style.fade(Style.TEXT, a));
            }
            ui.img("XP_Icon", x, y - 2, 16, 0xC0FFFFFF);
            ui.text(String.format("%,d", hud.g.player.skills.totalXp()), x + 22, y - 1, 12, Style.DIM);
        }
    }

    /** The full playable area, sharing the minimap's edited-column texture and landmark data. */
    static final class WorldMap extends Fragment {
        WorldMap(Hud h) { super("worldmap"); }
        @Override public float width() { return Hud.FW - 32; }
        @Override public float height() { return Hud.FH - 68; }
        @Override public void draw(Ui2D ui, float x, float y) {
            Rpg g = hud.g;
            Minimap map = (Minimap) hud.fragment("minimap");
            if (map.tex == 0 || g.mapDirty) map.build();
            if (g.time - map.markersAt > 2) map.markers();
            float size = height() - 30, left = x + 8, top = y + 22, side = left + size + 28;
            ui.text("WORLD MAP", left, y, 14, Style.ACCENT);
            ui.quad(map.tex, new float[]{left, top, left + size, top, left + size, top + size, left, top + size},
                    new float[]{0, 0, 1, 0, 1, 1, 0, 1}, 0xFFFFFFFF, 0);
            ui.border(left, top, size, size, 1, Style.EDGE_HI);
            for (Object[] marker : map.markers) {
                float mx = left + (float) marker[1] / DevMap.SX * size, my = top + (float) marker[2] / DevMap.SZ * size;
                ui.img((String) marker[0], mx - 7, my - 7, 14, 0xFFFFFFFF);
            }
            // Local people only: a world map is not remote knowledge of hidden actors.
            for (Npc n : g.npcs) if (n.alive && n.loaded && n.distTo(g.player) < 28 && g.vox.sight(g.player.x, g.player.eyeY(), g.player.z, n.x, n.chestY(), n.z))
                ui.disc(left + n.x / DevMap.SX * size, top + n.z / DevMap.SZ * size, 2, n.hostileToPlayer ? Style.NPC_HOSTILE : Style.NPC);
            for (var grave : g.undertaker.graves()) skull(ui, left + grave.x / DevMap.SX * size, top + grave.z / DevMap.SZ * size, 12, Style.TEXT);
            for (var contract : g.bountyBoard.carried()) {
                float mx = left + contract.x() / DevMap.SX * size, my = top + contract.z() / DevMap.SZ * size;
                ui.disc(mx, my, 7, 0x70402010); ui.border(mx - 5, my - 5, 10, 10, 2, 0xFFFFC05A);
            }
            if (hud.flag != null) ui.disc(left + hud.flag[0] / DevMap.SX * size, top + hud.flag[1] / DevMap.SZ * size, 4, Style.BAD);
            float px = left + g.player.x / DevMap.SX * size, py = top + g.player.z / DevMap.SZ * size;
            float yaw = g.controller.camYaw;
            ui.disc(px, py, 4, Style.ACCENT);
            ui.line(px, py, px - (float) Math.sin(yaw) * 10, py - (float) Math.cos(yaw) * 10, 2, Style.ACCENT);
            ui.text("NORTH", side, top, 14, Style.TEXT);
            ui.text(DevMap.SX + " x " + DevMap.SZ + " m playable area", side, top + 30, 13, Style.DIM);
            ui.text("You", side + 20, top + 78, 13, Style.TEXT); ui.disc(side + 5, top + 85, 4, Style.ACCENT);
            ui.text("Nearby people", side + 20, top + 110, 13, Style.TEXT); ui.disc(side + 5, top + 117, 3, Style.NPC);
            ui.text("Nearby hostiles", side + 20, top + 142, 13, Style.TEXT); ui.disc(side + 5, top + 149, 3, Style.NPC_HOSTILE);
            ui.text("Your grave", side + 20, top + 174, 13, Style.TEXT); skull(ui, side + 5, top + 181, 12, Style.TEXT);
            ui.text("Gold square: warrant last sighting", side, top + 211, 12, Style.ACCENT);
            ui.text("Click to mark a destination", side, top + 242, 13, Style.DIM);
            ui.text("M or Esc to return", side, top + 268, 13, Style.DIM);
            if (live && ui.clicked(left, top, size, size))
                hud.flag = new float[]{Math.max(0, Math.min(DevMap.SX - 1, (ui.mx - left) / size * DevMap.SX)), Math.max(0, Math.min(DevMap.SZ - 1, (ui.my - top) / size * DevMap.SZ))};
        }
    }

    // ------------------------------------------------------------------ minimap: top right, flat; the NPC colours as they always were
    static final class Minimap extends Fragment {
        private static final float R = 76, SCALE = 2.6f;
        private int tex;
        private double builtAt = -99, markersAt = -99;
        private final float[] p2 = new float[3];
        private final List<Object[]> markers = new ArrayList<>();
        Minimap(Hud h) { super("minimap"); }
        @Override public float width() { return R * 2; }
        @Override public float height() { return R * 2 + 6 + Math.min(2, hud.g.undertaker.graves().size()) * 20; }

        private ByteBuffer px;

        /** The colour of one map column, from its top block. */
        private void column(Rpg g, int x, int z) {
            int top = 0, ty = 0;
            for (int y = DevMap.SY - 1; y >= 0; y--) { int b = g.vox.block(x, y, z); if (b != 0) { top = b; ty = y; break; } }
            int c = Blocks.mapColor(top);
            float shade = 0.62f + (ty - DevMap.FLOOR + 1) * 0.06f;
            if (top == Blocks.WATER) { c = 0x355E8C; shade = 1; }
            boolean wall = ty >= DevMap.FLOOR && ty <= DevMap.FLOOR + 3 && (top == Blocks.BRICK || top == Blocks.PLANKS || top == Blocks.PLASTER || top == Blocks.STONE || top == Blocks.FENCE);
            // flat and low-contrast: colours pulled toward grey, walls a soft light line
            int rr = (int) (((c >> 16) & 255) * shade), gg = (int) (((c >> 8) & 255) * shade), bb = (int) ((c & 255) * shade);
            int grey = (rr + gg + bb) / 3;
            rr = (rr * 2 + grey) / 3; gg = (gg * 2 + grey) / 3; bb = (bb * 2 + grey) / 3;
            if (wall) { rr = 196; gg = 192; bb = 184; }
            int at = (z * DevMap.SX + x) * 4;
            px.put(at, (byte) Math.min(255, rr)).put(at + 1, (byte) Math.min(255, gg)).put(at + 2, (byte) Math.min(255, bb)).put(at + 3, (byte) 255);
        }

        /**
         * The map image. 2.5: only the columns edited since last time are coloured again (the whole map was scanned top
         * to bottom on every edit - a million block reads, 20 - 45 ms, each time an NPC felled a tree or opened a door);
         * the whole map only at the start and after a load.
         */
        private void build() {
            Rpg g = hud.g;
            int S = DevMap.SX;
            boolean all = px == null || g.mapColumns.isEmpty();
            if (px == null) px = MemoryUtil.memAlloc(S * S * 4);
            if (all) { for (int z = 0; z < S; z++) for (int x = 0; x < S; x++) column(g, x, z); }
            else for (int k : g.mapColumns) { int x = k >> 16, z = (short) (k & 0xFFFF); if (x >= 0 && z >= 0 && x < S && z < S) column(g, x, z); }
            g.mapColumns.clear();
            if (tex != 0) hud.r.release(AstrattoNative.RES_TEXTURE, tex);
            px.position(0).limit(S * S * 4);
            tex = hud.r.texture(AstrattoNative.TEX_2D, S, S, 1, AstrattoNative.TEX_NEAREST, px);
            builtAt = g.time;
            g.mapDirty = false;
        }

        /** The map's markers, from what is in the world (the provided map icons). */
        private void markers() {
            Rpg g = hud.g;
            markers.clear();
            for (WorldObject o : g.objects.all()) {
                String icon = null;
                if (o instanceof Station s) icon = switch (s.def) { case "range" -> "Cookingrange_map_icon"; case "anvil", "furnace" -> "Smithing_Icon"; case "craft_table" -> "Crafting_shop_map_icon"; case "sawbench" -> "Construction_Icon"; default -> null; };
                else if (o instanceof Container c) icon = c.kind.equals("chest") && c != g.objects.custody ? "Bank_map_icon" : null;
                else if (o instanceof Fixtures.Stall) icon = "Food_shop_map_icon";
                else if (o instanceof Fixtures.Kiosk) icon = "Sidequest_map_icon";
                else if (o instanceof Fixtures.Cabinet) icon = "Platebody_map_icon";
                else if (o instanceof Fixtures.Climb) icon = "Agility_training_map_icon";
                else if (o instanceof Furniture f && f.def.contains("bed")) icon = "Estate_map_icon";
                if (icon != null) markers.add(new Object[]{icon, o.x, o.z});
            }
            for (Npc n : g.npcs) {
                String icon = switch (n.role) { case MERCHANT, FENCE -> "General_store_map_icon"; case LAWMAN -> "Shield_map_icon"; case QUEST_GIVER -> "Quest_map_icon"; case UNDERTAKER -> "Gravestone_map_icon"; default -> null; };
                if (icon != null) markers.add(new Object[]{icon, n.postX, n.postZ});
            }
            markers.add(new Object[]{"Water_source_map_icon", DevMap.WELL[0] + 0.5f, DevMap.WELL[1] + 0.5f});
            markers.add(new Object[]{"Fishing_spot_map_icon", DevMap.FISH_SPOTS[0][0], DevMap.FISH_SPOTS[0][1]});
            markers.add(new Object[]{"Woodcutting_Icon", 94.5f, 14.5f});
            markers.add(new Object[]{"Mining_Icon", 54.5f, 34.5f});
            markers.add(new Object[]{"Harvesting_area_map_icon", 72.5f, 34.5f});
            markers.add(new Object[]{"Sword_map_icon", 16.5f, 47.5f});
            markers.add(new Object[]{"Minigame_map_icon", DevMap.SPAWN_X, DevMap.SPAWN_Z});
            markersAt = g.time;
        }

        @Override public void draw(Ui2D ui, float x, float y) {
            Rpg g = hud.g;
            if (tex == 0 || g.mapDirty && g.time - builtAt > 1.0) build();
            if (g.time - markersAt > 2) markers();
            float cx = x + R, cy = y + R;
            float yaw = g.controller.camYaw;
            float fx = (float) -Math.sin(yaw), fz = (float) -Math.cos(yaw), rx = (float) Math.cos(yaw), rz = (float) -Math.sin(yaw);
            Player p = g.player;
            ui.disc(cx, cy, R + 3, 0x90141518);
            int seg = 48;
            for (int i = 0; i < seg; i++) {
                double a0 = i * Math.PI * 2 / seg, a1 = (i + 1) * Math.PI * 2 / seg;
                float[] xy = {cx, cy, cx, cy, cx + (float) Math.cos(a0) * R, cy + (float) Math.sin(a0) * R, cx + (float) Math.cos(a1) * R, cy + (float) Math.sin(a1) * R};
                float[] uv = new float[8];
                for (int k = 0; k < 4; k++) {
                    float dx = (xy[k * 2] - cx) / SCALE, dy = (xy[k * 2 + 1] - cy) / SCALE;
                    float wx = p.x + rx * dx - fx * dy, wz = p.z + rz * dx - fz * dy;
                    uv[k * 2] = wx / DevMap.SX; uv[k * 2 + 1] = wz / DevMap.SZ;
                }
                ui.quad(tex, xy, uv, 0xE8FFFFFF, 0);
            }
            ui.ring(cx, cy, R + 1, 1, Style.EDGE_HI);
            for (Object[] m : markers) if (toMap((float) m[1], (float) m[2], cx, cy)) ui.img((String) m[0], p2[0] - 7, p2[1] - 7, 14, 0xF0FFFFFF);
            for (WorldObject o : g.objects.all()) {
                if (o instanceof rpg.objects.GroundItem) dot(ui, o.x, o.z, cx, cy, 0xFFE02020, 2);
                if (o instanceof rpg.objects.Fire) dot(ui, o.x, o.z, cx, cy, 0xFFFF9020, 3);
            }
            // the areas being searched (gamefixes): a soft patch where they think you are, pulsing at its edge
            for (var area : g.ai.searchAreas()) searchArea(ui, area, cx, cy, (float) g.time);
            for (Npc n : g.npcs) if (n.alive && n.loaded) dot(ui, n.x, n.z, cx, cy, n.hostileToPlayer ? Style.NPC_HOSTILE : Style.NPC, 3);
            if (hud.flag != null) dot(ui, hud.flag[0], hud.flag[1], cx, cy, 0xFFFF2020, 4);
            // the player's graves (gamefixes): a skull where each stands - held to the rim when it is off the map - and
            // under the map the time each has left before the undertaker takes it
            var graves = g.undertaker.graves();
            for (var gr : graves) {
                if (gr.heldBy != 0) continue;
                boolean on = toMap(gr.x, gr.z, cx, cy);
                if (!on) rim(gr.x, gr.z, cx, cy);
                float pulse = gr.expiresAt - g.time < 60 ? 0.6f + 0.4f * (float) Math.abs(Math.sin(g.time * 5)) : 1;
                skull(ui, p2[0], p2[1], on ? 12 : 10, Style.fade(0xFFF1EBDD, pulse));
            }
            float ty = y + R * 2 + 8;
            for (int i = 0; i < Math.min(2, graves.size()); i++) {
                var gr = graves.get(i);
                double left = g.undertaker.left(gr);
                int col = gr.heldBy != 0 ? Style.INFO : left < 60 ? Style.BAD : 0xFFE9E1D2;
                ui.rect(x + 8, ty - 1, R * 2 - 16, 18, 0xA0141518);
                skull(ui, x + 20, ty + 7, 10, 0xFFF1EBDD);
                String what = gr.heldBy != 0 ? "Carried to the undertaker" : String.format("%d:%02d  %s", (int) left / 60, (int) left % 60, gr.secured ? "guarded" : "exposed");
                ui.text(what, x + 32, ty + 1, 12, col);
                ty += 20;
            }
            // the player: a small arrow along the camera (up)
            ui.line(cx, cy - 4, cx - 3, cy + 3, 1.5f, 0xFFFFFFFF);
            ui.line(cx, cy - 4, cx + 3, cy + 3, 1.5f, 0xFFFFFFFF);
            // north: a letter riding the rim; click it to face north
            float ndx = -(float) Math.sin(yaw), ndy = -(float) Math.cos(yaw);
            float qx = cx + ndx * (R - 2), qy = cy + ndy * (R - 2);
            ui.disc(qx, qy, 8, 0xE0141518);
            ui.flatCentered("N", qx, qy - 7, 11, Style.ACCENT);
            if (ui.clicked(qx - 9, qy - 9, 18, 18)) g.controller.snapTo(0);
            if (ui.hover(x, y, R * 2, R * 2)) hud.fragment("area").woken(hud.now);
            if (ui.click && !ui.consumed && Math.hypot(ui.mx - cx, ui.my - cy) < R) {
                ui.consumed = true;
                float dx = (ui.mx - cx) / SCALE, dy = (ui.my - cy) / SCALE;
                float tx = p.x + rx * dx - fx * dy, tz = p.z + rz * dx - fz * dy;
                hud.flag = new float[]{tx, tz};
                g.controller.walkTo(tx, tz);
            }
        }

        /** A point off the map: where its direction meets the rim (p2). */
        private void rim(float x, float z, float cx, float cy) {
            float yaw = hud.g.controller.camYaw;
            float fx = (float) -Math.sin(yaw), fz = (float) -Math.cos(yaw), rx = (float) Math.cos(yaw), rz = (float) -Math.sin(yaw);
            float dx = x - hud.g.player.x, dz = z - hud.g.player.z;
            float sx = dx * rx + dz * rz, sy = -(dx * fx + dz * fz), l = Math.max(1e-3f, (float) Math.hypot(sx, sy));
            p2[0] = cx + sx / l * (R - 9); p2[1] = cy + sy / l * (R - 9);
        }

        private boolean toMap(float x, float z, float cx, float cy) {
            float yaw = hud.g.controller.camYaw;
            float fx = (float) -Math.sin(yaw), fz = (float) -Math.cos(yaw), rx = (float) Math.cos(yaw), rz = (float) -Math.sin(yaw);
            float dx = x - hud.g.player.x, dz = z - hud.g.player.z;
            float sx = (dx * rx + dz * rz) * SCALE, sy = -(dx * fx + dz * fz) * SCALE;
            if (Math.hypot(sx, sy) > R - 6) return false;
            p2[0] = cx + sx; p2[1] = cy + sy;
            return true;
        }

        /** A searched area on the map: a filled patch (clipped to the map's rim) with a pulsing edge, and a "?" at its middle. */
        private void searchArea(Ui2D ui, rpg.ai.AiSystem.SearchArea a, float cx, float cy, float t) {
            Rpg g = hud.g;
            boolean me = a.target() == g.player.id;
            int fill = me ? 0x38D06A5E : 0x307EA8C9, edge = me ? Style.BAD : Style.INFO;
            float yaw = g.controller.camYaw;
            float fx = (float) -Math.sin(yaw), fz = (float) -Math.cos(yaw), rx = (float) Math.cos(yaw), rz = (float) -Math.sin(yaw);
            int seg = 32;
            float[] px = new float[seg + 1], py = new float[seg + 1];
            boolean any = false;
            for (int i = 0; i <= seg; i++) {
                double ang = i * Math.PI * 2 / seg;
                float wx = a.x() + (float) Math.cos(ang) * a.r(), wz = a.z() + (float) Math.sin(ang) * a.r();
                float dx = wx - g.player.x, dz = wz - g.player.z;
                float sx = (dx * rx + dz * rz) * SCALE, sy = -(dx * fx + dz * fz) * SCALE;
                float l = (float) Math.hypot(sx, sy);
                if (l < R - 3) any = true;
                if (l > R - 3) { sx *= (R - 3) / l; sy *= (R - 3) / l; }
                px[i] = cx + sx; py[i] = cy + sy;
            }
            float dx0 = a.x() - g.player.x, dz0 = a.z() - g.player.z;
            float mx = cx + (dx0 * rx + dz0 * rz) * SCALE, my = cy - (dx0 * fx + dz0 * fz) * SCALE;
            float ml = (float) Math.hypot(mx - cx, my - cy);
            if (!any && ml > R - 3) {
                // entirely off the map: a chevron on the rim pointing to it
                float ux = (mx - cx) / ml, uy = (my - cy) / ml, ex = cx + ux * (R - 8), ey = cy + uy * (R - 8);
                ui.line(ex - uy * 5 - ux * 4, ey + ux * 5 - uy * 4, ex + ux * 3, ey + uy * 3, 2, edge);
                ui.line(ex + uy * 5 - ux * 4, ey - ux * 5 - uy * 4, ex + ux * 3, ey + uy * 3, 2, edge);
                return;
            }
            float ccx = Math.max(cx - R + 3, Math.min(cx + R - 3, mx)), ccy = Math.max(cy - R + 3, Math.min(cy + R - 3, my));
            for (int i = 0; i < seg; i++) ui.quad(0, new float[]{ccx, ccy, ccx, ccy, px[i], py[i], px[i + 1], py[i + 1]}, new float[8], fill, 0);
            float pulse = 0.45f + 0.55f * (float) Math.abs(Math.sin(t * 3));
            for (int i = 0; i < seg; i += 2) ui.line(px[i], py[i], px[i + 1], py[i + 1], 1.5f, Style.fade(edge, pulse));
            if (ml < R - 8) ui.flatCentered("?", mx, my - 7, 12, Style.fade(edge, 0.9f));
        }

        private void dot(Ui2D ui, float x, float z, float cx, float cy, int c, float s) {
            if (!toMap(x, z, cx, cy)) return;
            ui.rect(p2[0] - s / 2 - 0.5f, p2[1] - s / 2 - 0.5f, s + 1, s + 1, 0xC0000000);
            ui.rect(p2[0] - s / 2, p2[1] - s / 2, s, s, c);
        }
    }

    /** Where and when: under the minimap, on arriving somewhere new, when time jumps, or in a panel scene. */
    static final class Area extends Fragment {
        Area(Hud h) { super("area"); }
        @Override protected boolean active() { return false; }
        @Override public float width() { return 152; }
        @Override public float height() { return 34; }
        @Override public void draw(Ui2D ui, float x, float y) {
            Rpg g = hud.g;
            String jur = DevMap.jurisdiction(g.player.x, g.player.z);
            ui.flatCentered(jur, x + 76, y, 13, jur.equals(DevMap.WILDS) ? Style.DIM : Style.ACCENT);
            ui.flatCentered(g.state.clockText(), x + 76, y + 16, 12, Style.DIM);
        }
    }

    // ------------------------------------------------------------------ the law: wanted, witnessed, searched for
    /**
     * The law's read-out (gamefixes: a better wanted GUI). A heat gauge from the bounties; one status line saying what
     * the law is doing about the player right now - closing in (they can see you), searching (they have lost you: how
     * many, and how long until they give up), hidden - and below it the bounties by jurisdiction and the witnesses still
     * on their way to report, each with how far they have left to go.
     */
    static final class Wanted extends Fragment {
        private static final float W = 300;
        private record Row(String icon, String text, int color, float bar, int barColor) {}
        private final List<Row> rows = new ArrayList<>();
        private String status = "";
        private int statusColor = Style.DIM, heat;
        private boolean spotted;
        private float searchLeft = -1, searchTotal = 1;
        private double builtAt = -1;
        Wanted(Hud h) { super("wanted"); h.events.listen((type, detail) -> { if (type.equals("law")) builtAt = -1; }); }
        @Override protected boolean active() { refresh(); return !rows.isEmpty() || heat > 0 || searchLeft > 0 || spotted; }
        @Override public float width() { return W; }
        @Override public float height() { return 46 + (searchLeft > 0 ? 12 : 0) + rows.size() * 20 + (rows.isEmpty() ? 0 : 6); }

        private void refresh() {
            Rpg g = hud.g;
            if (g.time - builtAt < 0.2) return;
            builtAt = g.time;
            rows.clear();
            long me = g.player.id;
            int total = 0;
            for (var j : g.law.all().entrySet()) {
                Integer b = j.getValue().get(me);
                if (b == null || b <= 0) continue;
                total += b;
                rows.add(new Row("Gravestone_map_icon", j.getKey() + ": " + rpg.item.Currency.text(b) + " G bounty", Style.BAD, -1, 0));
            }
            heat = total <= 0 ? 0 : total < 50 ? 1 : total < 150 ? 2 : total < 400 ? 3 : total < 800 ? 4 : 5;
            int closing = 0, searchers = 0;
            searchLeft = -1;
            for (Npc n : g.npcs) {
                if (!n.alive || !n.loaded) continue;
                boolean law = n.role == Npc.Role.GUARD || n.role == Npc.Role.LAWMAN;
                if (law && n.goal == Brain.Goal.FIGHT && n.fightTarget == me) closing++;
                if (n.hunt != null && n.hunt.target == me && (law || n.hostileToPlayer)) {
                    searchers++;
                    float left = (float) (n.hunt.patience - (g.time - n.hunt.clueAt));
                    if (left > searchLeft) { searchLeft = left; searchTotal = n.hunt.patience; }
                }
                // witnesses: on their way to the lawman, with how far they still have to go
                Memory m = null;
                for (Memory k : n.memories) if (k.subject == me && k.crime() && k.witnessed() && !k.reported) { m = k; break; }
                if (m == null) continue;
                Npc lawman = g.ai.lawmanFor(m.jurisdiction);
                if (n.goal == Brain.Goal.REPORT_CRIME && lawman != null) {
                    float d = n.distTo(lawman);
                    rows.add(new Row("Quest_map_icon", String.format("%s is going to report your %s - %.0f m to go", n.name, m.kind.toLowerCase(), d), Style.ACCENT,
                            1 - Math.min(1, d / 60f), Style.ACCENT));
                } else rows.add(new Row("questionmark_UI", n.name + " saw your " + m.kind.toLowerCase(), Style.DIM, -1, 0));
            }
            if (g.law.resisting(me)) rows.add(new Row("Boss_Icon", "Resisting arrest", Style.BAD, -1, 0));
            spotted = closing > 0;
            if (spotted) { status = closing == 1 ? "SPOTTED - a guard is closing in" : "SPOTTED - " + closing + " guards are closing in"; statusColor = Style.BAD; }
            else if (searchers > 0) { status = searchers == 1 ? "SEARCHING - they've lost sight of you" : "SEARCHING - " + searchers + " are combing the area"; statusColor = Style.ACCENT; }
            else if (heat > 0) { status = "HIDDEN - no one has eyes on you"; statusColor = Style.GOOD; }
            else { status = rows.isEmpty() ? "" : "NOT WANTED - yet"; statusColor = Style.DIM; }
        }

        @Override public void draw(Ui2D ui, float x, float y) {
            Rpg g = hud.g;
            float hgt = height();
            Style.card(ui, x, y, W, hgt);
            // a band down the left edge in the status colour; it pulses while you are seen
            float pulse = spotted ? 0.55f + 0.45f * (float) Math.abs(Math.sin(g.time * 5)) : 1;
            ui.rect(x, y, 3, hgt, Style.fade(statusColor, pulse));
            // heat: five pips, lit by the total bounty
            ui.text(heat > 0 ? "WANTED" : "LAW", x + 12, y + 7, 14, heat > 0 ? Style.BAD : Style.DIM);
            for (int i = 0; i < 5; i++) {
                float px = x + W - 16 - (4 - i) * 16, py = y + 9;
                boolean on = i < heat;
                ui.rect(px, py, 11, 11, on ? Style.fade(Style.BAD, spotted ? pulse : 1) : 0x30FFFFFF);
                ui.border(px, py, 11, 11, 1, on ? 0x60000000 : Style.EDGE);
            }
            ui.text(status, x + 12, y + 27, 12, statusColor);
            float cy = y + 44;
            if (searchLeft > 0) {
                // how long until the search is given up, draining
                Style.bar(ui, x + 12, cy, W - 24, 4, searchLeft / Math.max(1, searchTotal), Style.ACCENT);
                ui.text(String.format("%.0fs", searchLeft), x + W - 40, cy + 4, 10, Style.FAINT);
                cy += 12;
            }
            if (!rows.isEmpty()) { ui.rect(x + 10, cy, W - 20, 1, Style.EDGE); cy += 5; }
            for (Row r : rows) {
                ui.img(r.icon(), x + 10, cy + 1, 15, 0xE0FFFFFF);
                ui.text(r.text(), x + 32, cy + 1, 12, r.color());
                if (r.bar() >= 0) Style.bar(ui, x + 32, cy + 16, W - 44, 2, r.bar(), r.barColor());
                cy += 20;
            }
        }
    }

    // ------------------------------------------------------------------ combat: who you are fighting
    static final class Target extends Fragment {
        Target(Hud h) { super("target"); }
        private Actor target() {
            Rpg g = hud.g;
            Actor best = null;
            if (g.interact.focus instanceof Npc n && n.alive && g.time - n.cs.lastHurt < 8) best = n;
            // an animal in the sights: its own levels make its combat level, as a person's do
            if (best == null && g.interact.focus instanceof rpg.animal.Animal an && an.alive && (g.time - an.cs.lastHurt < 8 || an.dangerous())) best = an;
            for (Npc n : g.npcs) if (best == null && n.alive && n.loaded && n.goal == Brain.Goal.FIGHT && n.fightTarget == g.player.id && n.distTo(g.player) < 20) best = n;
            if (best == null && g.player.cs.lastAttacker != 0 && g.time - g.player.cs.lastHurt < 8) { Actor a = g.actor(g.player.cs.lastAttacker); if (a != null && a.alive) best = a; }
            return best;
        }
        @Override protected boolean active() { return target() != null; }
        @Override public float width() { return 280; }
        @Override public float height() { return 40; }
        @Override public void draw(Ui2D ui, float x, float y) {
            Actor t = target();
            if (t == null) return;
            ui.rect(x, y, 280, 40, 0x90141518);
            int lvl = t.skills.combatLevel(), mine = hud.g.player.skills.combatLevel();
            boolean boss = lvl >= mine + 10;
            if (boss) ui.img("Boss_Icon", x + 6, y + 6, 26, 0xFFFFFFFF);
            float tx = x + (boss ? 38 : 10);
            ui.text(t.name, tx, y + 4, 14, t instanceof Npc n && n.hostileToPlayer ? 0xFFFF6040 : Style.NPC);
            ui.text("level " + lvl, x + 270 - ui.width("level " + lvl, 11), y + 6, 11, lvl > mine ? Style.BAD : Style.DIM);
            Style.bar(ui, tx, y + 26, x + 270 - tx, 6, t.hp / t.maxHp(), Style.HP);
            String hp = (int) Math.ceil(Math.max(0, t.hp)) + " / " + (int) t.maxHp();
            ui.text(hp, x + 270 - ui.width(hp, 10), y + 19, 10, Style.DIM);
        }
    }

    /** One bounded queue prevents simultaneous quest rewards and skill levels from painting over each other. */
    static final class Milestone extends Fragment {
        private record Notice(String title,String detail,String icon,int color,List<String> unlocks) {}
        private final java.util.ArrayDeque<Notice> pending=new java.util.ArrayDeque<>();
        private Notice current;
        private double since=-99;
        Milestone(Hud h) {
            super("levelup");fadeIn=0.25f;fadeOut=0.5f;
            h.events.listen((type,detail)-> {
                Notice notice=null;
                String title=switch(type) { case "event:QuestStarted"->"Quest accepted";case "event:QuestCompleted"->"Quest complete";case "event:QuestFailed"->"Quest failed";default->null; };
                if(title!=null)notice=new Notice(title,detail.startsWith("QT-COURIER")?"Courier":detail,"Quest_map_icon",type.endsWith("Failed")?Style.BAD:Style.ACCENT,List.of());
                else if(type.equals("event:LevelUp")) {
                    try {
                        String[] fields=detail.split(" ",2),levels=fields[1].split("->",2);
                        Skill skill=Skill.valueOf(fields[0]);int from=Integer.parseInt(levels[0]),to=Integer.parseInt(levels[1]);
                        List<String> unlocked=new ArrayList<>();
                        for(String line:SkillGuide.unlocks(skill)) {
                            int space=line.indexOf(' '),need=Integer.parseInt(line.substring(0,space));
                            if(need>from && need<=to && unlocked.size()<2)unlocked.add(line.substring(space+1));
                        }
                        notice=new Notice("Level up",skill.title+"  "+to,Atlas.skill(skill),Style.ACCENT,unlocked);
                    } catch(IllegalArgumentException|IndexOutOfBoundsException ignored) { return; }
                }
                if(notice==null)return;
                if(pending.size()==6)pending.removeFirst();pending.addLast(notice);
            });
        }
        @Override protected boolean active() {
            if((current==null || hud.now-since>4.2) && !pending.isEmpty()) { current=pending.removeFirst();since=hud.now; }
            return current!=null && hud.now-since<3.5;
        }
        @Override public float width(){return 360;}
        @Override public float height(){return 108+(current==null?0:current.unlocks.size()*17);}
        @Override public void draw(Ui2D ui,float x,float y) {
            if(current==null)return;
            y+=hud.fragment("honor").alpha*88-(1-alpha)*12;
            ui.rect(x,y,360,height(),0xF814171C);ui.border(x,y,360,height(),1,0x70D8B46A);
            ui.rect(x+60,y,240,2,current.color);
            ui.img(current.icon,x+160,y+10,40,0xFFFFFFFF);
            ui.flatCentered(current.title,x+180,y+56,20,current.color);
            ui.flatCentered(current.detail,x+180,y+82,14,Style.TEXT);
            for(int i=0;i<current.unlocks.size();i++)ui.flatCentered("Unlocked: "+current.unlocks.get(i),x+180,y+106+i*17,11,Style.GOOD);
        }
    }

    // ------------------------------------------------------------------ chat: fades out when nothing new is said
    static final class ChatLog extends Fragment {
        private int filter;
        ChatLog(Hud h) { super("chat"); fadeOut = 1.2f; }
        @Override protected boolean active() { return hud.dialog == null && hud.hovering(this); }
        @Override public float width() { return Math.min(460, hud.w - 560); }
        @Override public float height() { return 150; }
        @Override public void draw(Ui2D ui, float x, float y) {
            if (hud.dialog != null && !hud.dialog.pauseMenu) return;
            float cw = width(), ch = 150;
            boolean hv = ui.hover(x, y, cw, ch);
            if (hv) woken(hud.now);
            ui.rect(x, y, cw, ch, hv ? 0x90141518 : 0x50141518);
            String[] filters = {"All", "Game", "Crime", "Debug"};
            if (hv) for (int i = 0; i < filters.length; i++) if (Style.tab(ui, filters[i], x + i * 58, y + ch - 20, 58, 20, filter == i)) { filter = i; hud.sound("UIbutton1", 0.4f); }
            List<Chat.Line> lines = new ArrayList<>();
            for (Chat.Line l : hud.g.chat.last(60)) {
                boolean keep = switch (filter) { case 1 -> l.kind() == Chat.Kind.GAME || l.kind() == Chat.Kind.LEVEL || l.kind() == Chat.Kind.WARN || l.kind() == Chat.Kind.DIALOGUE; case 2 -> l.kind() == Chat.Kind.CRIME; case 3 -> l.kind() == Chat.Kind.DEBUG; default -> true; };
                if (keep) lines.add(l);
            }
            float ly = y + ch - (hv ? 40 : 22);
            for (int i = lines.size() - 1; i >= 0 && ly > y + 2; i--) {
                Chat.Line l = lines.get(i);
                int c = switch (l.kind()) { case WARN -> Style.BAD; case LEVEL -> Style.ACCENT; case CRIME -> 0xFFC48AD8; case DEBUG -> Style.GOOD; case DIALOGUE -> Style.INFO; default -> Style.TEXT; };
                String t = l.text();
                while (ui.width(t, 13) > cw - 14 && t.length() > 10) t = t.substring(0, t.length() - 4) + "...";
                ui.shadow(t, x + 7, ly, 13, c);
                ly -= 17;
            }
        }
    }

    // ------------------------------------------------------------------ Chatbox: speech replaces the Worldbox during a conversation
    static final class DialogueCard extends Fragment {
        private Dialog measured;
        private float measuredWidth;
        private List<String> textLines = List.of();
        private List<String> lines(Dialog d) {
            float width = width() - (hud.g.actor(d.npc) instanceof Npc ? 144 : 34);
            if (d != measured || width != measuredWidth) { measured = d; measuredWidth = width; textLines = wrap(hud.ui, d.text, 13, width); }
            return textLines;
        }
        DialogueCard(Hud h) { super("dialogue"); fadeIn = 0.12f; fadeOut = 0.15f; }
        @Override protected boolean active() { return hud.dialog != null && !hud.dialog.pauseMenu; }
        @Override public float width() { return Math.min(660, hud.w - 32); }
        @Override public float height() {
            Dialog d = hud.dialog;
            return d == null || hud.ui == null ? 190 : Math.min(hud.h - 100, Math.max(190, 70 + Math.min(6, lines(d).size()) * 16 + Math.max(1, Math.min(8, d.choices.size())) * 23));
        }
        @Override public void draw(Ui2D ui, float x, float y) {
            Dialog d = hud.dialog;
            if (d == null || d.pauseMenu) return;
            float cw = width(), ch = height();
            ui.rectTex(ui.parchment, x, y, cw, ch, 0, 0, cw / 128, ch / 128, 0xFFFFFFFF);
            ui.border(x, y, cw, ch, 1, 0xFF85714D);
            ui.border(x + 4, y + 4, cw - 8, ch - 8, 1, 0x665F5136);
            float nameX = x + (cw - ui.width(d.speaker, 16)) / 2;
            ui.text(d.speaker, nameX, y + 13, 16, 0xFFFFD15E);
            ui.text(d.speaker, nameX + 0.45f, y + 13, 16, 0xFFFFD15E);
            boolean portrait = hud.g.actor(d.npc) instanceof Npc;
            float tx = x + (portrait ? 126 : 16), textWidth = cw - (tx - x) - 18;
            if (portrait) {
                ui.rect(x + 15, y + 44, 96, 126, 0x40302015);
                ui.flush();
                hud.gfx.portrait((Npc) hud.g.actor(d.npc), x + 15, y + 40, 96, 126, hud.now, ui.alpha);
            }
            List<String> lines = lines(d);
            int rows = Math.min(6, lines.size());
            if (live && ui.hover(tx, y + 40, textWidth, Math.max(32, rows * 16))) d.textOffset = Math.max(0, Math.min(Math.max(0, lines.size() - rows), d.textOffset - (int) ui.scroll));
            for (int i = 0; i < rows; i++) ui.text(lines.get(i + d.textOffset), tx, y + 43 + i * 16, 13, 0xFFF0E5CD);
            float top = y + 48 + Math.max(2, rows) * 16;
            int count = d.choices.size();
            int visible = Math.min(8, count);
            if (live && ui.hover(tx, top, textWidth, ch - (top - y))) d.choiceOffset = Math.max(0, Math.min(Math.max(0, count - visible), d.choiceOffset - (int) ui.scroll));
            for (int i = d.choiceOffset; i < d.choiceOffset + visible; i++) {
                float yy = top + (i - d.choiceOffset) * 23;
                boolean hover = live && ui.hover(tx - 4, yy, textWidth + 4, 22);
                if (hover) ui.rect(tx - 4, yy, textWidth + 4, 22, 0x308E7447);
                String label = (i - d.choiceOffset + 1) + "  " + d.choices.get(i).label();
                float size = Math.min(12, textWidth / Math.max(1, ui.width(label, 1)));
                ui.text(label, tx, yy + 3, size, hover ? 0xFFFFD15E : 0xFFD7C7A4);
                if (live && ui.clicked(tx - 4, yy, textWidth + 4, 22)) { hud.choose(i); return; }
            }
            if (count > visible) ui.text("Scroll for more replies", tx, y + ch - 18, 10, Style.DIM);
            if (count == 0) { ui.text("Click to continue", tx, top + 8, 12, 0xFFD7C7A4); if (live && ui.clicked(x, y, cw, ch)) hud.closeDialog(); }
        }
    }

    /** A full pause presentation using the same actions and input routing as an ordinary dialogue. */
    static final class PauseCard extends Fragment {
        private float lastMouseX = Float.NaN, lastMouseY = Float.NaN;
        PauseCard(Hud h) { super("pause"); fadeIn = 0.12f; fadeOut = 0.12f; }
        @Override protected boolean active() { return hud.dialog != null && hud.dialog.pauseMenu; }
        @Override public float width() { return Math.min(900, Math.max(560, hud.w - 48)); }
        @Override public float height() { return 520; }
        @Override public void draw(Ui2D ui, float x, float y) {
            Dialog d = hud.dialog;
            if (d == null || !d.pauseMenu) return;
            float w = width(), split = w * 0.48f, right = x + split + 32, rw = w - split - 64;
            ui.rect(0, 0, hud.w, hud.h, 0xA0000000);
            Style.card(ui, x, y, w, height());
            ui.rect(x, y, w, 3, Style.ACCENT);
            ui.text("GAME PAUSED", x + 32, y + 30, 28, Style.TEXT);
            ui.text("Esc to resume", x + 34, y + 70, 12, Style.DIM);
            ui.rect(x + split, y + 32, 1, 440, Style.EDGE);
            boolean moved = ui.mx != lastMouseX || ui.my != lastMouseY;
            lastMouseX = ui.mx; lastMouseY = ui.my;
            for (int i = 0; i < d.choices.size(); i++) {
                float bx = x + 32, by = y + 114 + i * 62, bw = split - 64;
                boolean hover = live && ui.hover(bx, by, bw, 48);
                if (hover && moved) d.focusedChoice = i;
                boolean selected = d.focusedChoice == i;
                ui.rect(bx, by, bw, 48, selected ? 0x28E8C878 : hover ? 0x20FFFFFF : 0x10FFFFFF);
                ui.border(bx, by, bw, 48, 1, selected || hover ? Style.ACCENT : Style.EDGE);
                ui.text(Integer.toString(i + 1), bx + 18, by + 16, 12, selected ? Style.ACCENT : Style.DIM);
                ui.text(d.choices.get(i).label(), bx + 52, by + 13, 16, Style.TEXT);
                if (live && ui.clicked(bx, by, bw, 48)) { hud.choose(i); return; }
            }
            var p = hud.g.player;
            ui.text(p.name, right, y + 48, Math.min(24, rw / Math.max(1, ui.width(p.name, 1))), Style.ACCENT);
            ui.text("Level " + p.skills.combatLevel() + "   Total " + p.skills.totalLevel(), right, y + 88, 13, Style.TEXT);
            ui.text(hud.g.state.clockText(), right, y + 114, 13, Style.DIM);
            ui.text("Honor  " + hud.g.honor.rankName(), right, y + 164, 13, Style.TEXT);
            honorBar(ui, right, y + 196, rw, hud.g.honor.score, hud.g.honor.score);
            ui.text(rpg.item.Currency.full(p.count(rpg.item.Items.COINS)), right, y + 230, 14, Style.ACCENT);
            var item = p.eq.get(rpg.item.ItemDef.Slot.MAIN);
            if (item != null) {
                hud.itemIcon(ui, item, right + (rw - 112) / 2, y + 274, 112, 112, 1);
                ui.centered(item.def.name, right + rw / 2, y + 404, 14, Style.TEXT);
            }
            ui.text("Up/Down select  -  Enter confirm  -  1-5 choose", x + 34, y + 474, 11, Style.DIM);
        }
    }

    // ------------------------------------------------------------------ the context menu
    static final class MenuFrag extends Fragment {
        MenuFrag(Hud h) { super("menu"); fadeIn = 0.05f; fadeOut = 0.08f; }
        @Override protected boolean active() { return hud.menu != null; }
        @Override public float width() { return 0; }
        @Override public float height() { return 0; }
        @Override public void draw(Ui2D ui, float x0, float y0) {
            Hud.Menu m = hud.menu;
            if (m == null) return;
            float mw = 180;
            for (Hud.MenuItem it : m.items) mw = Math.max(mw, ui.width(it.label() + (it.enabled() || it.reason() == null ? "" : "  - " + it.reason()), 13) + 24);
            float x = Math.min(m.x, hud.w - mw - 4), y = Math.min(m.y, hud.h - 26 - m.items.size() * 20);
            float mh = 24 + m.items.size() * 20;
            ui.rect(x, y, mw, mh, 0xF0141518);
            ui.border(x, y, mw, mh, 1, Style.EDGE_HI);
            ui.text(m.title, x + 8, y + 5, 12, Style.ACCENT);
            for (int i = 0; i < m.items.size(); i++) {
                Hud.MenuItem it = m.items.get(i);
                float iy = y + 22 + i * 20;
                boolean hv = ui.hover(x, iy, mw, 20);
                if (hv) ui.rect(x + 1, iy, mw - 2, 20, 0x20FFFFFF);
                ui.text(it.label(), x + 8, iy + 3, 13, it.enabled() ? Style.TEXT : Style.FAINT);
                if (!it.enabled() && it.reason() != null) ui.text("- " + it.reason(), x + 14 + ui.width(it.label(), 13), iy + 4, 11, Style.BAD);
                if (ui.clicked(x, iy, mw, 20)) { hud.choose(i); return; }
            }
        }
    }

    // ------------------------------------------------------------------ the developer inspectors (GDD 4.2)
    interface Lines { List<String> of(Hud hud); }
    static final class Inspector extends Fragment {
        private final float iw;
        private final java.util.function.BooleanSupplier on;
        private final String title;
        private final Lines lines;
        private List<String> cache = List.of();
        Inspector(Hud h, String id, float iw, java.util.function.BooleanSupplier on, String title, Lines lines) { super(id); this.iw = iw; this.on = on; this.title = title; this.lines = lines; fadeOut = 0.15f; }
        @Override protected boolean active() { if (!on.getAsBoolean()) return false; cache = lines.of(hud); return true; }
        @Override public float width() { return Math.min(iw, hud.w - 280); }
        @Override public float height() { return 24 + cache.size() * 15; }
        @Override public void draw(Ui2D ui, float x, float y) {
            float ow = width(), oh = height();
            ui.rect(x, y, ow, oh, 0xD0101012);
            ui.border(x, y, ow, oh, 1, Style.EDGE);
            ui.text(title, x + 6, y + 4, 13, Style.INFO);
            for (int i = 0; i < cache.size(); i++) {
                String t = cache.get(i);
                while (ui.width(t, 12) > ow - 12 && t.length() > 8) t = t.substring(0, t.length() - 4) + "...";
                ui.text(t, x + 6, y + 22 + i * 15, 12, t.startsWith("[FAIL]") ? Style.BAD : t.startsWith("[ ok ]") ? Style.GOOD : 0xFFD8D8D8);
            }
        }
    }

    static List<String> aiLines(Hud hud) {
        Npc n = hud.inspected();
        List<String> lines = new ArrayList<>();
        if (n == null) return lines;
        lines.add(n.name + " #" + n.id + " - " + n.title + "  [PgUp/PgDn to cycle]");
        lines.add(String.format("%s  hp %.0f/%.0f  stamina %.0f  at (%.1f, %.1f) %s", n.alive ? (n.loaded ? "loaded" : "UNLOADED (abstract)") : "DEAD", n.hp, n.maxHp(), n.stamina, n.x, n.z, DevMap.jurisdiction(n.x, n.z)));
        lines.add(String.format("needs: hunger %.0f%%  fatigue %.0f%%  social %.0f%%", n.hunger * 100, n.fatigue * 100, n.social * 100));
        lines.add("GOAL: " + n.goal + " - " + n.goalReason);
        lines.add("scores:");
        for (String s : n.scores) lines.add("   " + s);
        lines.add("perceives: " + n.perceived);
        lines.add("engine action: " + n.nativeAction + (n.seesTarget ? " (sees its target)" : "") + "  route " + n.routeCells + " cells" + (n.stuck ? "  STUCK" : ""));
        lines.add(String.format("nav target: %s", n.hasGoalPos ? String.format("(%.1f, %.1f)", n.gx, n.gz) : "-"));
        lines.add("last outcome: " + n.lastOutcome);
        lines.add(String.format("next reconsider in %.1f s", Math.max(0, n.nextThink - hud.g.time)));
        lines.add("memory (" + n.memories.size() + "):");
        for (int i = Math.max(0, n.memories.size() - 6); i < n.memories.size(); i++) lines.add("   " + n.memories.get(i).line(hud.g::nameOf));
        List<String> inv = new ArrayList<>();
        for (var grid : n.grids()) for (var s : grid.stacks()) inv.add(s.toString());
        lines.add("inventory: " + inv);
        return lines;
    }

    static List<String> stateLines(Hud hud) {
        Rpg g = hud.g;
        List<String> lines = new ArrayList<>();
        lines.add(String.format("tick %d  revision %d  events %d  %s", g.state.tick, g.state.revision, g.state.eventsTotal, g.state.clockText()));
        Interactable f = g.interact.focus;
        if (f != null) {
            lines.add("focus: " + f.inspect(g).replace("\n", " |"));
            for (Option o : f.options(g, g.player)) lines.add("   option " + o.id() + " \"" + o.label() + "\" " + (o.enabled() ? "enabled" : "DISABLED: " + o.reason()));
        }
        lines.add("requests " + g.interact.requests + "  committed " + g.interact.committed + "  rejected " + g.interact.rejected + "  interrupted " + g.interact.interrupted + "  duplicates " + g.interact.duplicates);
        lines.add("last: " + g.interact.lastOutcome);
        for (var j : g.law.all().entrySet()) for (var b : j.getValue().entrySet()) if (b.getValue() > 0) lines.add("bounty: " + g.nameOf(b.getKey()) + " @" + j.getKey() + " = " + b.getValue());
        lines.add("Store A logs " + g.econ.shopA.stock.count(rpg.item.Items.LOGS) + ", Store B logs " + g.econ.shopB.stock.count(rpg.item.Items.LOGS));
        for (var s : g.econ.shipments) lines.add("shipment #" + s.id + " " + s.status + " cargo " + s.crate.contents.stacks() + " " + s.note);
        lines.addAll(g.invariants.lastReport);
        lines.add("event trace (newest last):");
        for (DomainEvent e : g.state.recent(12)) lines.add("  " + e.line());
        return lines;
    }

    static List<String> telemetryLines(Hud hud) {
        var c = hud.g.combat;
        List<String> lines = new ArrayList<>(c.telemetry);
        lines.add(0, String.format("contacts %d  blocked %d  parried %d  dodged %d  staggers %d  kills %d  deflected %d", c.contacts, c.blocked, c.parried, c.dodged, c.staggers, c.kills, c.deflections));
        return lines;
    }

    static List<String> xpLines(Hud hud) {
        List<String> lines = new ArrayList<>();
        lines.add(String.format("%-13s %4s %11s %11s %11s", "skill", "lvl", "xp", "next", "to go"));
        for (Skill s : Skill.ALL) {
            int l = hud.g.player.skills.level(s);
            long xp = hud.g.player.skills.xp(s), next = l >= 100 ? XpTable.MAX_XP : XpTable.xpFor(l + 1);
            lines.add(String.format("%-13s %4d %,11d %,11d %,11d", s.title, l, xp, next, Math.max(0, next - xp)));
        }
        lines.add(String.format("table: L60 %,d  L80 %,d  L90 %,d  L100 %,d (one table for every skill)", XpTable.xpFor(60), XpTable.xpFor(80), XpTable.xpFor(90), XpTable.xpFor(100)));
        return lines;
    }
}
