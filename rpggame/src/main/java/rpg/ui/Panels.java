package rpg.ui;

import java.util.List;
import rpg.Chat;
import rpg.Ids;
import rpg.Rpg;
import rpg.actor.Player;
import rpg.ai.Npc;
import rpg.combat.CombatMath;
import rpg.combat.Spells;
import rpg.interact.Result;
import rpg.item.Currency;
import rpg.item.ItemDef;
import rpg.item.ItemDef.Slot;
import rpg.item.ItemStack;
import rpg.item.Items;
import rpg.skill.Skill;
import rpg.skill.XpTable;
import rpg.world.DevMap;

/**
 * The panel scenes' fragments: the scene frame with its tabs, the equipment doll, the item read-out, the character
 * sheet, the skills grid and guide, the journal and the map legend, and magic. Each is a reusable
 * fragment; the scenes decide which are shown together.
 */
final class Panels {
    private Panels() { }

    static void register(Hud hud) {
        hud.add(new Frame(hud));
        hud.add(new EquipmentDoll(hud));
        hud.add(new ItemInfo(hud));
        hud.add(new Stats(hud));
        hud.add(new Skills(hud));
        hud.add(new Guide(hud));
        hud.add(new Journal(hud));
        hud.add(new Legend(hud));
        hud.add(new Magic(hud));
        hud.add(hud.inventory);
    }

    static abstract class Panel extends Fragment {
        final float pw, ph;
        Panel(String id, float pw, float ph) { super(id); this.pw = pw; this.ph = ph; fadeIn = 0.12f; fadeOut = 0.12f; }
        @Override public float width() { return pw; }
        @Override public float height() { return ph; }
        void title(Ui2D ui, String t, float x, float y) { ui.text(t, x, y, Style.TITLE, Style.ACCENT); }
    }

    // ------------------------------------------------------------------ the frame: tabs to every panel scene
    static final class Frame extends Panel {
        static final Object[][] TABS = {{Hud.Scene.INVENTORY, "Inventory", "Inventory_Icon_UI", "I"}, {Hud.Scene.CHARACTER, "Character", "Worn_Equipment_icon_UI", "C"},
                {Hud.Scene.SKILLS, "Skills", "Skills_icon_UI", "K"}, {Hud.Scene.JOURNAL, "Journal", "Quest_map_icon", "J"}, {Hud.Scene.MAGIC, "Magic", "Magic_Icon", "M"}};
        Frame(Hud h) { super("frame", Hud.FW, Hud.FH); }
        @Override public void draw(Ui2D ui, float x, float y) {
            ui.rect(0, 0, hud.w, hud.h, 0x48000000);   // the world steps back behind a panel
            ui.rect(x, y, pw, ph, 0xF4141518);
            ui.border(x, y, pw, ph, 1, Style.EDGE);
            float tw = 128;
            for (int i = 0; i < TABS.length; i++) {
                Hud.Scene s = (Hud.Scene) TABS[i][0];
                float tx = x + 12 + i * (tw + 4);
                boolean on = hud.scene == s;
                if (Style.tab(ui, "", tx, y + 6, tw, 34, on)) { hud.go(s); hud.sound("UIbutton1", 0.4f); }
                ui.img((String) TABS[i][2], tx + 8, y + 12, 20, on ? 0xFFFFFFFF : 0xA0FFFFFF);
                ui.text((String) TABS[i][1], tx + 34, y + 14, 13, on ? Style.TEXT : Style.DIM);
                if (ui.hover(tx, y + 6, tw, 34)) hud.tooltip(TABS[i][1] + "\nKey " + TABS[i][3]);
            }
            ui.rect(x + 12, y + 41, pw - 24, 1, Style.EDGE);
            if (Style.button(ui, "x", x + pw - 34, y + 10, 24, 24, true)) hud.go(Hud.Scene.WORLD);
            ui.img("questionmark_UI", x + pw - 64, y + 12, 20, 0xA0FFFFFF);
            if (ui.hover(x + pw - 66, y + 10, 24, 24)) hud.tooltip("Controls\nI inventory, C character, K skills, J journal, M magic\nDrag items between grids; R rotates while dragging\nDrop on an equipment slot to wear it, on the bar to bind it\nDrop outside the panel to drop it on the ground\nRight-click an item for every option; Esc closes");
        }
    }

    // ------------------------------------------------------------------ the equipment doll (in the inventory and character scenes)
    static final class EquipmentDoll extends Panel {
        static final Object[][] SLOTS = {{Slot.HEAD, 1, 0}, {Slot.CAPE, 0, 1}, {Slot.AMMO, 2, 1}, {Slot.MAIN, 0, 2}, {Slot.BODY, 1, 2}, {Slot.OFF, 2, 2},
                {Slot.LEGS, 1, 3}, {Slot.HANDS, 0, 4}, {Slot.FEET, 1, 4}, {Slot.BACK, 0, 5}, {Slot.BELT, 2, 5},
                {Slot.NECK, 1, 1}, {Slot.RING, 2, 4}};
        EquipmentDoll(Hud h) { super("equipment", 200, 400); }
        @Override public void draw(Ui2D ui, float x, float y) {
            Rpg g = hud.g;
            Player p = g.player;
            title(ui, "Equipment", x, y);
            float cs = 50, ox = x + (pw - cs * 3) / 2 - 4, oy = y + 26;
            for (Object[] e : SLOTS) {
                Slot s = (Slot) e[0];
                float cx = ox + (int) e[1] * (cs + 4), cy = oy + (int) e[2] * (cs + 4);
                Style.well(ui, cx, cy, cs, cs);
                ItemStack it = p.eq.get(s);
                boolean hv = ui.hover(cx, cy, cs, cs);
                if (hv) ui.border(cx, cy, cs, cs, 1, Style.EDGE_HI);
                if (it != null) hud.itemIcon(ui, it, cx + 2, cy + 2, cs - 4, cs - 4, 1);
                else ui.flatCentered(s.name().toLowerCase(), cx + cs / 2, cy + cs / 2 - 7, 10, Style.FAINT);
                if (hv && it != null) hud.tooltip(hud.describe(it) + "\nClick to remove");
                if (it != null && live && ui.clicked(cx, cy, cs, cs)) { Result r = g.items.unequip(p, s); if (r.rejected()) g.chat.warn(r.reason()); else hud.sound("UI_Trading_ItemSelect", 0.4f); }
                hud.inventory.zone(cx, cy, cs, cs, d -> hud.inventory.equipFromDrag(d, s));
                hud.named.put("slot:" + s.name(), new float[]{cx + cs / 2, cy + cs / 2});
            }
            float sy = oy + 6 * (cs + 4) + 4;
            ui.text(String.format("Armor %.1f   Protection %.0f", p.eq.armor(), CombatMath.protection(p.skills.level(Skill.DEFENSE), p.eq.armor())), x + 4, sy, 12, Style.DIM);
        }
    }

    // ------------------------------------------------------------------ what the mouse is over, the load and the purse
    static final class ItemInfo extends Panel {
        ItemInfo(Hud h) { super("iteminfo", 268, 400); }
        @Override protected boolean active() { return hud.win != Hud.Win.CONTAINER && hud.win != Hud.Win.SHOP; }
        @Override public void draw(Ui2D ui, float x, float y) {
            Rpg g = hud.g;
            Player p = g.player;
            title(ui, "Carrying", x, y);
            float kg = p.carried(), b = p.burden();
            ui.text(String.format("%.1f kg", kg), x, y + 26, 13, Style.TEXT);
            Style.bar(ui, x, y + 46, pw, 4, Math.min(1, kg / 60f), b < 1 ? Style.BAD : Style.GOOD);
            ui.text(b < 1 ? String.format("Burdened: %.0f%% speed", b * 100) : "Unburdened (free to 30 kg)", x, y + 54, 12, b < 1 ? Style.BAD : Style.DIM);
            int coins = p.count(Items.COINS);
            ui.text("Gold", x, y + 82, 13, Style.DIM);
            ui.text(Currency.text(coins) + " G", x + 60, y + 82, 13, Currency.color(coins));
            if (coins >= 100_000) ui.text(Currency.full(coins), x + 60, y + 98, 11, Style.FAINT);
            ItemStack s = hud.inventory.hovered;
            float iy = y + 128;
            ui.rect(x, iy - 8, pw, 1, Style.EDGE);
            if (s == null) { ui.text("Point at an item to see it here.", x, iy, 12, Style.FAINT); return; }
            hud.itemIcon(ui, s, x, iy, 48, 48, 1);
            ui.text(s.def.name, x + 58, iy + 2, 14, Style.TEXT);
            ui.text(s.def.category().name().toLowerCase(), x + 58, iy + 22, 11, Style.DIM);
            float ly = iy + 60;
            for (String l : hud.describe(s).split("\n")) { if (l.equals(s.def.name)) continue; ui.text(l, x, ly, 12, l.startsWith("Stolen") ? Style.BAD : Style.DIM); ly += 16; }
            for (String l : HudFragments.wrap(ui, s.def.examine == null ? "" : s.def.examine, 12, pw)) { ui.text(l, x, ly + 4, 12, Style.FAINT); ly += 15; }
        }
    }

    // ------------------------------------------------------------------ the character sheet
    static final class Stats extends Panel {
        Stats(Hud h) { super("stats", 652, 440); }
        @Override public void draw(Ui2D ui, float x, float y) {
            Rpg g = hud.g;
            Player p = g.player;
            ItemDef main = p.main();
            title(ui, p.name, x, y);
            ui.text("Combat level " + p.skills.combatLevel() + "    Total level " + p.skills.totalLevel(), x, y + 24, 13, Style.DIM);
            ui.text("Combat & condition", x, y + 56, 13, Style.TEXT);
            float prot = CombatMath.protection(p.skills.level(Skill.DEFENSE), p.eq.armor());
            String[][] rows = {
                    {"Health", String.format("%.0f / %.0f", p.hp, p.maxHp())}, {"Stamina", String.format("%.0f / %.0f", p.stamina, p.maxStamina())},
                    {"Faith", String.format("%.0f / %.0f", p.faith, p.maxFaith())}, {"Mana", String.format("%.0f / %.0f", p.mana, p.maxMana())},
                    {"Attack tempo", String.format("x%.2f", 1f / CombatMath.attackCadence(p.skills.level(Skill.ATTACK), main == null ? 0 : main.attackBonus))},
                    {"Impact force", String.format("x%.2f", CombatMath.strengthForce(p.skills.level(Skill.STRENGTH)))},
                    {"Protection", String.format("%.0f (%.0f%% slash mitigation)", prot, CombatMath.mitigation(prot, ItemDef.DamageType.SLASH) * 100)},
                    {"Stability", String.format("%.0f%s", CombatMath.stability(p.skills.level(Skill.DEFENSE), p.skills.level(Skill.STRENGTH), p.eq.armor(), p.cs.stabilityBonus), p.cs.stabilityBonus > 0 ? " (resisting)" : "")},
                    {"Weapon", main != null && main.damage > 0 ? main.name : "Fists"},
                    {"Damage", main != null && main.damage > 0 ? String.format("%.1f HP / %s", main.damage, main.damageType) : "0.6 HP / BLUNT"},
                    {"Reach / impact", main != null && main.damage > 0 ? String.format("%.1f m / %.0f", main.reach, main.impact) : "0.7 m / 6"},
                    {"Load", String.format("%.1f kg (%.0f%% speed)", p.carried(), p.burden() * 100)},
                    {"Gold", Currency.full(p.count(Items.COINS))}};
            // Honor (GDD 3.5): the bar with its face, the rank, and what it is worth at the counter
            var hn = g.honor;
            ui.text("Honor", x + 360, y + 56, 13, Style.DIM);
            ui.text(hn.rankName() + "  " + (hn.score > 0 ? "+" : "") + hn.score, x + 430, y + 56, 13, Style.TEXT);
            HudFragments.honorBar(ui, x + 368, y + 88, 270, hn.score, hn.score);
            ui.text(hn.discount() > 0 ? String.format("Merchants who know you: %.0f%% off", hn.discount() * 100) : "No Honor discount (from +33)", x + 360, y + 112, 11, Style.FAINT);
            for (int i = 0; i < rows.length; i++) {
                float ry = y + 84 + i * 21;
                ui.text(rows[i][0], x, ry, 13, Style.DIM);
                ui.text(rows[i][1], x + 140, ry, 13, i == rows.length - 1 ? Currency.color(p.count(Items.COINS)) : Style.TEXT);
            }
            String[] help = {"LMB tap: light swing    LMB hold: heavy", "RMB: guard (shield or weapon), or the off hand", "Guard just before a hit to parry", "Q dodge    R cast    1-0 the action bar", "V first / third person    E interact (hold for all options)"};
            ui.text("Controls", x + 360, y + 168, 13, Style.TEXT);
            for (int i = 0; i < help.length; i++) ui.text(help[i], x + 360, y + 194 + i * 20, 12, Style.FAINT);
        }
    }

    // ------------------------------------------------------------------ skills: the grid and the selected skill's guide
    static final class Skills extends Panel {
        Skills(Hud h) { super("skills", 420, 440); }
        @Override public void draw(Ui2D ui, float x, float y) {
            Rpg g = hud.g;
            float cw = 140, ch = 58;
            for (Skill s : Skill.ALL) {
                int i = s.ordinal();
                float cx = x + (i % 3) * cw, cy = y + (i / 3) * ch;
                boolean hv = ui.hover(cx, cy, cw - 4, ch - 4), sel = hud.guide == s;
                ui.rect(cx, cy, cw - 4, ch - 4, sel ? 0x26FFFFFF : hv ? 0x18FFFFFF : 0x0CFFFFFF);
                if (sel) ui.rect(cx, cy + ch - 6, cw - 4, 2, Style.ACCENT);
                ui.img(Atlas.skill(s), cx + 6, cy + 7, 38, 0xFFFFFFFF);
                int lvl = g.player.skills.level(s);
                ui.text(s.title, cx + 50, cy + 6, 12, Style.DIM);
                ui.text(String.valueOf(lvl), cx + 50, cy + 20, 18, lvl >= 100 ? Style.ACCENT : Style.TEXT);
                Style.bar(ui, cx + 50, cy + 44, cw - 62, 3, (float) XpTable.progress(g.player.skills.xp(s)), lvl >= 100 ? Style.ACCENT : Style.GOOD);
                if (hv) {
                    long xp = g.player.skills.xp(s);
                    hud.tooltip(lvl >= XpTable.MAX_LEVEL ? s.title + "\n" + String.format("%,d XP", xp) + "\nMastered: skillcape available"
                            : s.title + "\n" + String.format("%,d XP", xp) + "\nNext level at " + String.format("%,d", XpTable.xpFor(lvl + 1)) + "\n" + String.format("%,d to go", XpTable.xpFor(lvl + 1) - xp));
                }
                if (live && ui.clicked(cx, cy, cw, ch)) { hud.guide = s; hud.sound("UI_select", 0.4f); }
            }
            float ty = y + 6 * ch + 6;
            ui.img("XP_Icon", x, ty, 22, 0xFFFFFFFF);
            ui.text("Total level " + g.player.skills.totalLevel(), x + 30, ty + 1, 14, Style.TEXT);
            ui.text(String.format("%,d XP", g.player.skills.totalXp()), x + 30, ty + 19, 12, Style.DIM);
        }
    }

    static final class Guide extends Panel {
        private float scroll;
        private Skill seen;
        Guide(Hud h) { super("guide", 432, 440); }
        @Override public void draw(Ui2D ui, float x, float y) {
            Skill s = hud.guide;
            if (s != seen) { scroll = 0; seen = s; }
            ui.img(Atlas.skill(s), x, y, 40, 0xFFFFFFFF);
            ui.text(s.title, x + 50, y + 2, Style.TITLE, Style.ACCENT);
            ui.text("level " + hud.g.player.skills.level(s), x + 50, y + 22, 12, Style.DIM);
            float dy = y + 50;
            for (String l : HudFragments.wrap(ui, s.description, 12, pw)) { ui.text(l, x, dy, 12, Style.DIM); dy += 15; }
            dy += 8;
            List<String> lines = SkillGuide.unlocks(s);
            if (ui.hover(x, dy, pw, y + ph - dy)) scroll = Math.max(0, Math.min(Math.max(0, lines.size() * 18 - (y + ph - dy)), scroll - (float) ui.scroll * 36));
            int lvl = hud.g.player.skills.level(s);
            for (int i = 0; i < lines.size(); i++) {
                float ly = dy + i * 18 - scroll;
                if (ly < dy - 1 || ly > y + ph - 18) continue;
                String line = lines.get(i);
                int need = Integer.parseInt(line.substring(0, line.indexOf(' ')));
                ui.text(String.valueOf(need), x, ly, 12, need <= lvl ? Style.GOOD : Style.FAINT);
                // what a level gives stays a mystery until it is reached
                if (need <= lvl) ui.text(line.substring(line.indexOf(' ') + 1), x + 36, ly, 12, Style.TEXT);
                else ui.text("???  (revealed at level " + need + ")", x + 36, ly, 12, Style.FAINT);
            }
        }
    }

    // ------------------------------------------------------------------ the journal: quests, what was learned, the law
    static final class Journal extends Panel {
        Journal(Hud h) { super("journal", 520, 440); }
        @Override public void draw(Ui2D ui, float x, float y) {
            Rpg g = hud.g;
            title(ui, "Quests", x, y);
            var st = g.quests.courier;
            int c = switch (st) { case NOT_STARTED -> Style.DIM; case ACTIVE -> Style.ACCENT; case COMPLETED -> Style.GOOD; case FAILED -> Style.BAD; };
            ui.img("Quest_map_icon", x, y + 26, 16, 0xFFFFFFFF);
            ui.text("Courier (test quest) - " + st.name().toLowerCase().replace('_', ' ') + (g.quests.failReason != null ? ": " + g.quests.failReason : ""), x + 22, y + 26, 13, c);
            float yy = y + 60;
            title(ui, "Journal", x, yy);
            yy += 24;
            List<String> j = g.quests.journal;
            if (j.isEmpty()) { ui.text("Nothing learned yet.", x, yy, 12, Style.FAINT); yy += 16; }
            for (int i = Math.max(0, j.size() - 8); i < j.size(); i++) for (String l : HudFragments.wrap(ui, j.get(i), 12, pw)) { ui.text(l, x, yy, 12, Style.TEXT); yy += 15; }
            yy += 14;
            title(ui, "The law", x, yy);
            yy += 24;
            boolean any = false;
            for (var e : g.law.all().entrySet()) { Integer b = e.getValue().get(g.player.id); if (b != null && b > 0) { ui.text(e.getKey() + ": bounty " + b + " G", x, yy, 13, Style.BAD); yy += 17; any = true; } }
            if (!any) ui.text("You are not wanted anywhere.", x, yy, 13, Style.GOOD);
        }
    }

    /** The map's key: every map icon the world uses or can use, named. */
    static final class Legend extends Panel {
        Legend(Hud h) { super("legend", 332, 440); }
        @Override public void draw(Ui2D ui, float x, float y) {
            title(ui, "Map key", x, y);
            int i = 0;
            for (String n : Atlas.NAMES) {
                if (!n.endsWith("_map_icon")) continue;
                float cx = x + (i % 2) * 166, cy = y + 28 + (i / 2) * 25;
                ui.img(n, cx, cy, 20, 0xFFFFFFFF);
                ui.text(label(n), cx + 26, cy + 3, 12, Style.DIM);
                i++;
            }
        }
        static String label(String n) {
            String t = n.replace("_map_icon", "").replace('_', ' ').replace("BowArrow", "Archery").replace("Cookingrange", "Cooking range").replace("Cookery", "Cookery");
            return Character.toUpperCase(t.charAt(0)) + t.substring(1);
        }
    }

    // ------------------------------------------------------------------ magic and faith
    static final class Magic extends Panel {
        Magic(Hud h) { super("magic", 868, 440); }
        @Override public void draw(Ui2D ui, float x, float y) {
            Rpg g = hud.g;
            Player p = g.player;
            // the open spellbook (GDD 2.5: one at a time, changed at an altar) and its eleven slots
            title(ui, p.book.title + " spellbook", x, y);
            ui.text(String.format("Mana %.0f / %.0f  -  a point a minute", p.mana, p.maxMana()), x + 230, y + 3, 11, Style.MANA);
            ui.text(p.book.about + " Click to select; R (or a staff) casts. Change books at an altar.", x, y + 20, 10, Style.FAINT);
            float yy = y + 40;
            for (int lv : Spells.SLOTS) {
                Spells.Spell s = Spells.slot(p.book, lv);
                boolean has = s != null, lvl = p.skills.level(Skill.MAGIC) >= lv, sel = s != null && p.spell == s;
                if (has && Style.tab(ui, "", x, yy, 420, 30, sel) && live) { if (lvl) { p.spell = s; hud.sound("UI_select", 0.4f); } else g.chat.warn("You need Magic level " + lv + "."); }
                ui.img("Magic_Icon", x + 4, yy + 5, 20, has && lvl ? 0xFFFFFFFF : 0x40FFFFFF);
                if (!has) { ui.text("Level " + lv + "  -  (an empty slot)", x + 30, yy + 8, 11, Style.FAINT); yy += 33; continue; }
                // hidden until earned (gamefixes): what a level gives is shown only once it is reached
                if (!lvl) { ui.text("Level " + lv + "  -  ???", x + 30, yy + 8, 12, Style.FAINT); yy += 33; continue; }
                ui.text(s.title + (s.element != Spells.Element.NONE ? "  (" + s.element.name().toLowerCase() + ")" : ""), x + 30, yy + 2, 12, Style.TEXT);
                ui.text("level " + lv + "  -  " + s.cost + " Mana" + (s == Spells.Spell.FIRE_BLAST ? " + a staff charge" : ""), x + 30, yy + 16, 10, Style.DIM);
                if (ui.hover(x, yy, 420, 30)) hud.tooltip(s.title + "\n" + s.effect);
                yy += 33;
            }
            // the prayers: toggled; each drains a point of Faith per interval while it is raised
            title(ui, "Prayers", x + 448, y);
            ui.text(String.format("Faith %.0f / %.0f  -  a point every %.1f s for each prayer", p.faith, p.maxFaith(), rpg.combat.Prayers.interval(p)), x + 548, y + 3, 11, Style.FAITH);
            yy = y + 28;
            for (Spells.Prayer b : Spells.Prayer.values()) {
                boolean lvl = p.skills.level(Skill.FAITH) >= b.level, on = g.prayers.on(p, b);
                if (Style.tab(ui, "", x + 448, yy, 420, 36, on) && live) { if (lvl) g.combat.bless(p, b); else g.chat.warn("You need Faith level " + b.level + "."); }
                ui.img("Faith_Icon", x + 452, yy + 6, 22, lvl ? (on ? 0xFFFFFFFF : 0xC0FFFFFF) : 0x40FFFFFF);
                if (!lvl) { ui.text("Level " + b.level + "  -  ???", x + 482, yy + 10, 12, Style.FAINT); yy += 39; continue; }
                ui.text(b.title + (on ? "  (raised)" : ""), x + 482, yy + 2, 13, on ? Style.FAITH : Style.TEXT);
                ui.text("level " + b.level + (b.category == Spells.Category.OVERHEAD ? "  -  overhead: one at a time" : ""), x + 482, yy + 19, 10, Style.DIM);
                if (ui.hover(x + 448, yy, 420, 36)) hud.tooltip(b.title + "\n" + b.effect);
                yy += 39;
            }
        }
    }

}
