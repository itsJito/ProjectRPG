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
        /** A section heading: Morpheus in gold over a gold rule the panel's width. */
        void title(Ui2D ui, String t, float x, float y) { title(ui, t, x, y, pw); }
        /** The lore face (Morpheus) heads the quest log and the spellbooks; the rest of the interface is Friz Quadrata. */
        boolean lore(String t) { return t.equals("Quests") || t.equals("Journal") || t.endsWith("spellbook"); }
        void title(Ui2D ui, String t, float x, float y, float w) { ui.text(lore(t) ? ui.heading : ui.body, t, x, y - 4, 21, Style.ACCENT); Style.rule(ui, x, y + 19, w); }
        /** An inset section behind a group of rows: a darker sheet with a faint gold edge. */
        static void sheet(Ui2D ui, float x, float y, float w, float h) {
            ui.rect(x, y, w, h, 0x50000000);
            ui.border(x, y, w, h, 1, 0x30C8A060);
        }
    }

    // ------------------------------------------------------------------ the frame: tabs to every panel scene
    static final class Frame extends Panel {
        static final Object[][] TABS = {{Hud.Scene.INVENTORY, "Inventory", "Inventory", "I"}, {Hud.Scene.CHARACTER, "Character", "Equipment", "C"},
                {Hud.Scene.SKILLS, "Skills", "Skills_icon", "K"}, {Hud.Scene.JOURNAL, "Journal", "Quest_map_icon", "J"}, {Hud.Scene.MAGIC, "Magic", "Magic_Icon", "B"}, {Hud.Scene.MAP, "Map", "Minigame_map_icon", "M"}};
        Frame(Hud h) { super("frame", Hud.FW, Hud.FH); }
        @Override public float width() { return hud.frameWidth(); }
        @Override public void draw(Ui2D ui, float x, float y) {
            float pw=width();
            ui.rect(0, 0, hud.w, hud.h, 0x60000000);   // the world steps back behind a panel
            Style.card(ui, x, y, pw, ph);
            // the tab strip: a darker band the tabs hang in, the open one joined to the page below it
            ui.rect(x + 4, y + 4, pw - 8, 40, 0x70000000);
            float tw = 124;
            for (int i = 0; i < TABS.length; i++) {
                if (hud.inventoryOnly() && i > 0) continue;
                Hud.Scene s = (Hud.Scene) TABS[i][0];
                float tx = x + 10 + i * (tw + 4);
                boolean on = hud.scene == s;
                if (Style.tab(ui, "", tx, y + 8, tw, 34, on)) { hud.go(s); hud.sound("UIbutton1", 0.4f); }
                ui.img((String) TABS[i][2], tx + 8, y + 12, 24, on ? 0xFFFFFFFF : 0xB0FFFFFF);
                ui.text((String) TABS[i][1], tx + 38, y + 17, 14, on ? 0xFFFFD15E : Style.DIM);
                if (ui.hover(tx, y + 8, tw, 34)) hud.tooltip(TABS[i][1] + "\nKey " + TABS[i][3]);
            }
            Style.rule(ui, x + 6, y + 44, pw - 12);
            if (Style.button(ui, "x", x + pw - 36, y + 12, 26, 26, true)) hud.go(Hud.Scene.WORLD);
            ui.img("questionmark_UI", x + pw - 68, y + 14, 22, 0xC0FFFFFF);
            if (ui.hover(x + pw - 66, y + 10, 24, 24)) hud.tooltip("Controls\nI inventory, C character, K skills, J journal, M map, B magic\nDrag items between grids; R rotates while dragging\nDrop on an equipment slot to wear it, on the bar to bind it\nDrop outside the panel to drop it on the ground\nRight-click an item for every option; Esc closes");
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
            float cs = 50, ox = x + (pw - cs * 3) / 2 - 4, oy = y + 30;
            // the figure's outline behind the slots, faint, so the doll reads as a body
            ui.img("Equipment", ox + cs * 0.5f, oy + cs * 0.6f, cs * 2.2f, 0x18FFFFFF);
            for (Object[] e : SLOTS) {
                Slot s = (Slot) e[0];
                float cx = ox + (int) e[1] * (cs + 4), cy = oy + (int) e[2] * (cs + 4);
                ItemStack it = p.eq.get(s);
                boolean hv = ui.hover(cx, cy, cs, cs);
                String plate = slotIcon(s);
                // an empty slot is its stone plate (the provided slot icons); a filled one, the item on a darkened plate
                Style.well(ui, cx, cy, cs, cs);
                if (plate != null) ui.img(plate, cx + 1, cy + 1, cs - 2, it == null ? 0xFFFFFFFF : 0x50FFFFFF);
                else if (it == null) ui.flatCentered(s.name().toLowerCase(), cx + cs / 2, cy + cs / 2 - 7, 10, Style.FAINT);
                if (hv) ui.border(cx, cy, cs, cs, 1, Style.EDGE_HI);
                if (it != null) hud.itemIcon(ui, it, cx + 2, cy + 2, cs - 4, cs - 4, 1);
                if (hv && it != null) hud.tooltip(hud.describe(it) + "\nClick to remove");
                if (it != null && live && ui.clicked(cx, cy, cs, cs)) { Result r = g.items.unequip(p, s); if (r.rejected()) g.chat.warn(r.reason()); else hud.sound("UI_Trading_ItemSelect", 0.4f); }
                hud.inventory.zone(cx, cy, cs, cs, d -> hud.inventory.equipFromDrag(d, s));
                hud.named.put("slot:" + s.name(), new float[]{cx + cs / 2, cy + cs / 2});
            }
            float sy = oy + 6 * (cs + 4) + 4;
            sheet(ui, x, sy - 4, pw, 24);
            ui.text(String.format("Armor %.1f   Protection %.0f", p.eq.armor(), CombatMath.protection(p.skills.level(Skill.DEFENSE), p.eq.armor())), x + 8, sy + 1, 12, Style.DIM);
        }
        /** The stone plate an empty slot shows (the provided OSRS-style slot icons); a worn bag's slots have none. */
        static String slotIcon(Slot s) {
            return switch (s) {
                case HEAD -> "Head_slot"; case CAPE -> "Cape_slot"; case NECK -> "Neck_slot"; case AMMO -> "Ammo_slot"; case MAIN -> "Weapon_slot";
                case BODY -> "Body_slot"; case OFF -> "Shield_slot"; case LEGS -> "Legs_slot"; case HANDS -> "Hands_slot"; case FEET -> "Feet_slot";
                case RING -> "Ring_slot"; default -> null;
            };
        }
    }

    // ------------------------------------------------------------------ what the mouse is over, the load and the purse
    static final class ItemInfo extends Panel {
        ItemInfo(Hud h) { super("iteminfo", 268, 400); }
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
            title(ui, "Item", x, iy - 6);
            iy += 22;
            if (s == null) { ui.text("Point at an item to see it here.", x, iy, 12, Style.FAINT); return; }
            Style.well(ui, x, iy, 52, 52);
            hud.itemIcon(ui, s, x + 2, iy + 2, 48, 48, 1);
            ui.text(s.def.name, x + 62, iy + 4, Math.min(15, (pw - 64) / Math.max(1, ui.width(s.def.name, 1))), 0xFFFFD15E);
            ui.text(s.def.category().name().toLowerCase(), x + 62, iy + 26, 11, Style.DIM);
            float ly = iy + 62;
            for (String l : hud.describe(s).split("\n")) { if (l.equals(s.def.name)) continue; ui.text(l, x, ly, 12, l.startsWith("Stolen") ? Style.BAD : Style.DIM); ly += 16; }
            // the flavour text: info text, in Arial Narrow
            Ui2D.Face was = ui.use(ui.chat);
            for (String l : HudFragments.wrap(ui, s.def.examine == null ? "" : s.def.examine, 13, pw)) { ui.text(l, x, ly + 4, 13, 0xFFC8B896); ly += 16; }
            ui.use(was);
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
            ui.text("Combat level " + p.skills.combatLevel() + "    Total level " + p.skills.totalLevel(), x, y + 28, 13, Style.DIM);
            sheet(ui, x - 6, y + 50, 340, 372);
            sheet(ui, x + 352, y + 50, 300, 90);
            sheet(ui, x + 352, y + 158, 300, 152);
            ui.text("Combat & condition", x, y + 56, 13, 0xFFFFD15E);
            float prot = CombatMath.protection(p.skills.level(Skill.DEFENSE), p.eq.armor());
            String[][] rows = {
                    {"Health", String.format("%.0f / %.0f", p.hp, p.maxHp())}, {"Stamina", String.format("%.0f / %.0f", p.stamina, p.maxStamina())},
                    {"Faith", String.format("%.0f / %.0f", p.faith, p.maxFaith())}, {"Mana", String.format("%.0f / %.0f", p.mana, p.maxMana())},
                    {"Attack tempo", String.format("x%.2f", 1f / CombatMath.attackCadence(p.skills.level(Skill.ATTACK), main == null ? 0 : main.attackBonus))},
                    {"Impact force", String.format("x%.2f", CombatMath.strengthForce(p.skills.level(Skill.STRENGTH)))},
                    {"Protection", String.format("%.0f rating / %d max hit absorbed", prot, CombatMath.armoredMax(1000, p.skills.level(Skill.DEFENSE), p.eq.armor()) == 1000 ? 0 : 1000 - CombatMath.armoredMax(1000, p.skills.level(Skill.DEFENSE), p.eq.armor()))},
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
                if (i % 2 == 0) ui.rect(x - 4, ry - 3, 336, 20, 0x14FFE0A0);
                ui.text(rows[i][0], x, ry, 13, Style.DIM);
                ui.text(rows[i][1], x + 140, ry, 13, i == rows.length - 1 ? Currency.color(p.count(Items.COINS)) : Style.TEXT);
            }
            String[] help = {"LMB tap: light swing    LMB hold: heavy", "RMB: guard (shield or weapon), or the off hand", "Guard just before a hit to parry", "Q dodge    R cast    1-0 the action bar", "V first / third person    E interact (hold for all options)"};
            ui.text("Controls", x + 360, y + 166, 13, 0xFFFFD15E);
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
                // each skill a stone tile: its icon in a sunken plate, the level large, the way to the next as a bar
                ui.rect(cx, cy, cw - 4, ch - 4, sel ? 0xFF3A3024 : hv ? 0xFF2C251C : 0xFF211C16);
                ui.border(cx, cy, cw - 4, ch - 4, 1, sel ? Style.GOLD : hv ? 0xFF5A4A30 : 0xFF0E0B08);
                if (sel) ui.rect(cx + 1, cy + 1, cw - 6, 1, 0x80E0BC6C);
                Style.well(ui, cx + 5, cy + 6, 40, 40);
                ui.img(Atlas.skill(s), cx + 7, cy + 8, 36, 0xFFFFFFFF);
                int lvl = g.player.skills.level(s);
                ui.text(s.title, cx + 52, cy + 6, 12, sel ? Style.TEXT : Style.DIM);
                ui.text(String.valueOf(lvl), cx + 52, cy + 20, 18, lvl >= 100 ? 0xFFFFD15E : Style.TEXT);
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
            Style.well(ui, x, y, 44, 44);
            ui.img(Atlas.skill(s), x + 2, y + 2, 40, 0xFFFFFFFF);
            ui.text(ui.body, s.title, x + 54, y - 2, 24, Style.ACCENT);
            ui.text("level " + hud.g.player.skills.level(s), x + 54, y + 26, 12, Style.DIM);
            Style.rule(ui, x, y + 48, pw);
            float dy = y + 56;
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
            sheet(ui, x - 4, y + 26, pw + 8, 24);
            ui.img("Quest_map_icon", x, y + 29, 18, 0xFFFFFFFF);
            ui.text("Courier (test quest) - " + st.name().toLowerCase().replace('_', ' ') + (g.quests.failReason != null ? ": " + g.quests.failReason : ""), x + 26, y + 31, 13, c);
            float yy = y + 66;
            title(ui, "Journal", x, yy);
            yy += 24;
            List<String> j = g.quests.journal;
            if (j.isEmpty()) { ui.text("Nothing learned yet.", x, yy, 12, Style.FAINT); yy += 16; }
            Ui2D.Face was = ui.use(ui.body);
            for (int i = Math.max(0, j.size() - 8); i < j.size(); i++) for (String l : HudFragments.wrap(ui, j.get(i), 13, pw)) { ui.text(l, x, yy, 13, 0xFFE0D2B0); yy += 16; }
            ui.use(was);
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
            title(ui, p.book.title + " spellbook", x, y, 420);
            ui.text(String.format("Mana %.0f / %.0f  -  a point a minute", p.mana, p.maxMana()), x + 230, y + 3, 11, Style.MANA);
            ui.text(p.book.about + " Click to select; R (or a staff) casts. Change books at an altar.", x, y + 20, 10, Style.FAINT);
            float yy = y + 40;
            // the book's spells in the order they open, two to a row (Sun has thirty); what is not yet earned shows as ???
            java.util.List<Spells.Spell> spells = Spells.book(p.book);
            int magic = p.skills.level(Skill.MAGIC);
            for (int i = 0; i < spells.size(); i++) {
                Spells.Spell s = spells.get(i);
                float sx = x + (i % 2) * 212, sy = yy + (i / 2) * 26;
                boolean lvl = magic >= s.level, sel = p.spell == s;
                if (Style.tab(ui, "", sx, sy, 208, 24, sel) && live) { if (lvl) { p.spell = s; hud.sound("UI_select", 0.4f); } else g.chat.warn("You need Magic level " + s.level + "."); }
                ui.img("Magic_Icon", sx + 3, sy + 4, 16, lvl ? 0xFFFFFFFF : 0x40FFFFFF);
                if (!lvl) { ui.text("Level " + s.level + "  -  ???", sx + 24, sy + 6, 11, Style.FAINT); continue; }
                ui.text(s.title, sx + 24, sy + 1, 11, Style.TEXT);
                int cost = g.combat.cost(p, s);
                String hit = s.utility ? "" : "  -  hits " + (int) s.maxBase(magic);
                ui.text(cost + " Mana" + hit, sx + 24, sy + 13, 9, cost < s.cost ? Style.GOOD : Style.DIM);
                if (ui.hover(sx, sy, 208, 24)) hud.tooltip(s.title + " (Magic " + s.level + ")\n" + s.effect + (s.tier != null ? "\nEvery " + s.tier.title + " you know hits as hard as the strongest you have learned." : "")
                        + (cost < s.cost ? "\nYour staff makes it cheap to cast." : ""));
            }
            // the prayers: toggled; each drains a point of Faith per interval while it is raised
            title(ui, "Prayers", x + 448, y, 420);
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
