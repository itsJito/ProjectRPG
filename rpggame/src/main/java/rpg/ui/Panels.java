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
        hud.add(new Faith(hud));
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
                {Hud.Scene.SKILLS, "Skills", "Skills_icon", "K"}, {Hud.Scene.JOURNAL, "Journal", "Quest_map_icon", "J"}, {Hud.Scene.MAGIC, "Magic", "Magic_Icon", "B"},
                {Hud.Scene.FAITH, "Faith", "Faith_Icon", "P"}, {Hud.Scene.MAP, "Map", "Minigame_map_icon", "M"}};
        Frame(Hud h) { super("frame", Hud.FW, Hud.FH); }
        @Override public float width() { return hud.frameWidth(); }
        @Override public void draw(Ui2D ui, float x, float y) {
            float pw=width();
            ui.rect(0, 0, hud.w, hud.h, 0x60000000);   // the world steps back behind a panel
            Style.card(ui, x, y, pw, ph);
            // the tab strip: a darker band the tabs hang in, the open one joined to the page below it
            ui.rect(x + 4, y + 4, pw - 8, 40, 0x70000000);
            float tw = 118;
            for (int i = 0; i < TABS.length; i++) {
                if (hud.inventoryOnly() && i > 0) continue;
                Hud.Scene s = (Hud.Scene) TABS[i][0];
                float tx = x + 10 + i * (tw + 4);
                boolean on = hud.scene == s;
                if (Style.tab(ui, "", tx, y + 8, tw, 34, on)) { hud.go(s); hud.sound("UIbutton1", 0.4f); }
                ui.img((String) TABS[i][2], tx + 8, y + 12, 24, on ? 0xFFFFFFFF : 0xB0FFFFFF);
                ui.text((String) TABS[i][1], tx + 38, y + 17, 14, on ? 0xFFFFD15E : Style.DIM);
                if (ui.hover(tx, y + 8, tw, 34)) {
                    var action = rpg.Preferences.Action.valueOf(s.name());
                    hud.tooltip(TABS[i][1] + "\nKey " + hud.settings.values.label(action));
                }
            }
            Style.rule(ui, x + 6, y + 44, pw - 12);
            if (Style.button(ui, "x", x + pw - 36, y + 12, 26, 26, true)) hud.go(Hud.Scene.WORLD);
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
                if (it != null && live && ui.rclicked(cx, cy, cs, cs)) hud.openMenu(it.def.name, ui.mx, ui.my, java.util.List.of(
                        new Hud.MenuItem("Remove", true, null, () -> g.items.unequip(p, s)),
                        new Hud.MenuItem("Drop", true, null, () -> g.items.dropEquipped(p, s))));
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
            // (gamefixes: the controls came off the character sheet) where the player stands with each kingdom's law
            ui.text("Standing", x + 360, y + 166, 13, 0xFFFFD15E);
            String[] kingdoms = {rpg.world.DevMap.ALPHA, rpg.world.DevMap.BETA, rpg.world.DevMap.PALACE};
            for (int i = 0; i < kingdoms.length; i++) {
                int b = g.law.bounty(p.id, kingdoms[i]);
                float ry = y + 194 + i * 24;
                ui.text(kingdoms[i], x + 360, ry, 13, Style.DIM);
                ui.text(b <= 0 ? "In good standing" : b <= g.cfg.pursuitBounty ? "Fine owed: " + b + " G" : "Wanted: " + Currency.text(b) + " G", x + 470, ry, 13, b <= 0 ? Style.GOOD : b <= g.cfg.pursuitBounty ? Style.WARN : Style.BAD);
            }
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

    // ------------------------------------------------------------------ magic: the spellbook
    /**
     * The spellbook (gamefixes: magic and the prayers were one crowded page; magic wanted a cleaner, more polished look).
     * A tome laid open: the book and the Mana it has to work with across the top; the spells as a grid of cards, each
     * marked with its element's colour and tier, the one chosen raised in gold; what is not yet learned a dim locked card;
     * and down the right, the chosen spell written out in full - cost, strength, what it does.
     */
    static final class Magic extends Panel {
        Magic(Hud h) { super("magic", 868, 440); }
        static int element(Spells.Element e) {
            return switch (e) { case FIRE -> 0xFFE0703A; case WATER -> 0xFF4A9AE0; case EARTH -> 0xFFA88050; case AIR -> 0xFFC8E0E8; case ICE -> 0xFF9AD8F0;
                case ELECTRIC -> 0xFFE8E070; case BLOOD -> 0xFFC03A4A; default -> 0xFFB090E0; };
        }
        @Override public void draw(Ui2D ui, float x, float y) {
            Rpg g = hud.g;
            Player p = g.player;
            // the tome's page: a deep violet wash over the frame, ruled
            ui.rect(x - 6, y - 4, 880, 446, 0x30281A40);
            ui.text(ui.heading, p.book.title + " Spellbook", x, y - 6, 26, 0xFFD8C8FF);
            ui.text(p.book.about, x, y + 24, 11, Style.FAINT);
            // the Mana: a jewelled bar
            float mx = x + 470, mw = 380;
            ui.text("Mana", mx, y - 2, 12, Style.MANA);
            ui.text(String.format("%.0f / %.0f", p.mana, p.maxMana()), mx + mw - 64, y - 2, 12, Style.TEXT);
            Style.well(ui, mx, y + 14, mw, 10);
            Style.bar(ui, mx + 1, y + 15, mw - 2, 8, p.mana / Math.max(1, p.maxMana()), Style.MANA);
            Style.rule(ui, x, y + 40, 852);
            java.util.List<Spells.Spell> spells = Spells.book(p.book);
            int magic = p.skills.level(Skill.MAGIC);
            float cw = 140, ch = 46, gx = x, gy = y + 50;
            int cols = 4;
            Spells.Spell shown = p.spell;
            for (int i = 0; i < spells.size(); i++) {
                Spells.Spell s = spells.get(i);
                float sx = gx + (i % cols) * (cw + 4), sy = gy + (i / cols) * (ch + 4);
                boolean known = magic >= s.level, sel = p.spell == s, hv = ui.hover(sx, sy, cw, ch);
                int el = element(s.element);
                ui.rect(sx, sy, cw, ch, sel ? 0xFF3A2C4A : hv ? 0xFF2C2236 : 0xFF1E1826);
                ui.border(sx, sy, cw, ch, 1, sel ? Style.GOLD : hv ? 0xFF6A5A80 : 0xFF0C0A10);
                if (sel) ui.rect(sx + 1, sy + 1, cw - 2, 1, 0x90E0BC6C);
                // the element's gem
                ui.disc(sx + 17, sy + ch / 2, 11, known ? Style.fade(el, 0.35f) : 0x20FFFFFF);
                ui.img("Magic_Icon", sx + 7, sy + ch / 2 - 10, 20, known ? 0xFFFFFFFF : 0x30FFFFFF);
                if (!known) {
                    ui.text(s.title.replaceAll("[A-Za-z]", "?"), sx + 34, sy + 8, 11, Style.FAINT);
                    ui.text("Magic " + s.level, sx + 34, sy + 25, 10, 0xFF6A5A70);
                } else {
                    ui.text(s.title, sx + 34, sy + 7, 12, sel ? 0xFFFFE8B0 : Style.TEXT);
                    int cost = g.combat.cost(p, s);
                    ui.text(cost + " mana" + (s.tier != null ? "  " + s.tier.title : ""), sx + 34, sy + 25, 10, cost < s.cost ? Style.GOOD : Style.fade(el, 0.9f));
                }
                if (hv) shown = s;
                if (hv && live && ui.clicked(sx, sy, cw, ch)) { if (known) { p.spell = s; hud.sound("UI_select", 0.4f); } else g.chat.warn("You need Magic level " + s.level + "."); }
            }
            // the chosen (or pointed-at) spell, written out
            float dx = x + 4 * (cw + 4) + 6, dy = gy, dw = 852 - 4 * (cw + 4) - 6;
            ui.rect(dx, dy, dw, 380, 0x40000000);
            ui.border(dx, dy, dw, 380, 1, 0x40B090E0);
            if (shown != null) {
                boolean known = magic >= shown.level;
                int el = element(shown.element);
                ui.disc(dx + dw / 2, dy + 46, 30, Style.fade(el, 0.25f));
                ui.ring(dx + dw / 2, dy + 46, 30, 2, Style.fade(el, 0.8f));
                ui.img("Magic_Icon", dx + dw / 2 - 18, dy + 28, 36, known ? 0xFFFFFFFF : 0x40FFFFFF);
                ui.flatCentered(ui.heading, known ? shown.title : "Unlearned", dx + dw / 2, dy + 84, 20, known ? 0xFFFFE8B0 : Style.FAINT);
                float ly = dy + 116;
                String[][] rows = {{"Level", "Magic " + shown.level}, {"Element", shown.element == Spells.Element.NONE ? "None" : shown.element.word().substring(0, 1).toUpperCase() + shown.element.word().substring(1)},
                        {"Cost", g.combat.cost(p, shown) + " mana"}, {"Strikes for", shown.utility ? "-" : "up to " + (int) shown.maxBase(magic)}};
                for (String[] r : rows) { ui.text(r[0], dx + 14, ly, 12, Style.DIM); ui.text(r[1], dx + 110, ly, 12, Style.TEXT); ly += 20; }
                Style.rule(ui, dx + 10, ly + 4, dw - 20);
                ly += 14;
                for (String l : HudFragments.wrap(ui, known ? shown.effect : "Not yet learned. It opens at Magic level " + shown.level + ".", 12, dw - 28)) { ui.text(l, dx + 14, ly, 12, 0xFFC8B8E0); ly += 16; }
                if (shown.tier != null && known) { ly += 6; for (String l : HudFragments.wrap(ui, "Every " + shown.tier.title + " you know hits as hard as the strongest you have learned.", 11, dw - 28)) { ui.text(l, dx + 14, ly, 11, Style.FAINT); ly += 14; } }
            }
            ui.text("Click a spell to ready it; R (or a staff) casts it. Change books at an altar.", x, y + 428, 11, Style.FAINT);
        }
    }

    // ------------------------------------------------------------------ faith: the prayers
    /**
     * Faith (gamefixes: the prayers have their own tab, with a religious feel): an altar page in ivory and gold. A sun
     * with a halo crowns it and the Faith left to give is its light; each prayer is a votive tablet with a candle that
     * burns while the prayer is raised; the overhead protections (one at a time) stand apart from the blessings.
     */
    static final class Faith extends Panel {
        Faith(Hud h) { super("faith", 868, 440); }
        @Override public void draw(Ui2D ui, float x, float y) {
            Rpg g = hud.g;
            Player p = g.player;
            float cx = x + 434;
            // the altar page: warm light falling from the top
            for (int i = 0; i < 12; i++) ui.rect(x - 6, y - 4 + i * 12, 880, 12, Style.fade(0xFFF0D890, 0.11f - i * 0.009f));
            // the sun and its halo; its rays as long as the Faith that is left
            float share = p.faith / Math.max(1, p.maxFaith());
            for (int r = 0; r < 16; r++) {
                double a = r / 16.0 * Math.PI * 2 + g.time * 0.05;
                float len = 6 + 12 * share * (r % 2 == 0 ? 1 : 0.6f);
                ui.line(cx + (float) Math.cos(a) * 20, y + 34 + (float) Math.sin(a) * 20, cx + (float) Math.cos(a) * (20 + len), y + 34 + (float) Math.sin(a) * (20 + len), 2, Style.fade(0xFFF2D27A, 0.35f + 0.5f * share));
            }
            ui.disc(cx, y + 34, 18, 0xFFF4DC8A);
            ui.ring(cx, y + 34, 18, 2, 0xFFB8862E);
            ui.ring(cx, y + 34, 26, 1, 0x80F4DC8A);
            ui.img("Faith_Icon", cx - 12, y + 22, 24, 0xFF6A4A1A);
            ui.flatCentered(ui.heading, "Faith", cx - 230, y + 4, 30, 0xFFF4DC8A);
            ui.flatCentered("\"Light before me, light behind me.\"", cx - 230, y + 40, 12, 0xFFC8B48A);
            ui.text("Faith", cx + 90, y + 6, 12, Style.FAITH);
            ui.text(String.format("%.0f / %.0f", p.faith, p.maxFaith()), cx + 330, y + 6, 12, Style.TEXT);
            Style.well(ui, cx + 90, y + 22, 300, 10);
            Style.bar(ui, cx + 91, y + 23, 298, 8, share, 0xFFE8C860);
            ui.text(String.format("each raised prayer draws a point every %.1f s", rpg.combat.Prayers.interval(p)), cx + 90, y + 38, 10, Style.FAINT);
            Style.rule(ui, x, y + 76, 852);
            // the prayers: the overhead protections first, then the blessings
            float ty = y + 104;
            int col = 0; float colY[] = {ty, ty};
            for (Spells.Category cat : new Spells.Category[]{Spells.Category.OVERHEAD, null}) {
                for (Spells.Prayer b : Spells.Prayer.values()) {
                    boolean overhead = b.category == Spells.Category.OVERHEAD;
                    if ((cat == Spells.Category.OVERHEAD) != overhead) continue;
                    int c = overhead ? 0 : 1;
                    float bx = x + c * 430, by = colY[c];
                    boolean lvl = p.skills.level(Skill.FAITH) >= b.level, on = g.prayers.on(p, b), hv = ui.hover(bx, by, 422, 40);
                    // the tablet: ivory stone, gold-edged when raised
                    ui.rect(bx, by, 422, 40, on ? 0xFF4A3A20 : hv ? 0xFF332A1E : 0xFF241E16);
                    ui.border(bx, by, 422, 40, 1, on ? 0xFFF2C24A : hv ? 0xFF8A7040 : 0xFF100C08);
                    if (on) { ui.rect(bx + 1, by + 1, 420, 1, 0xA0F4DC8A); ui.rect(bx, by, 422, 40, 0x18F4DC8A); }
                    // the candle: lit while raised
                    float kx = bx + 18, ky = by + 30;
                    ui.rect(kx - 3, ky - 14, 6, 14, lvl ? 0xFFEDE3CC : 0x40EDE3CC);
                    if (on) { float fl = 1 + 0.15f * (float) Math.sin(g.time * 12 + b.ordinal()); ui.disc(kx, ky - 19, 5 * fl, 0x60FFC860); ui.disc(kx, ky - 19, 2.6f * fl, 0xFFFFE6A0); }
                    else ui.line(kx, ky - 14, kx, ky - 17, 1, 0x80302820);
                    ui.img("Faith_Icon", bx + 32, by + 9, 22, lvl ? (on ? 0xFFFFFFFF : 0xC0FFFFFF) : 0x30FFFFFF);
                    if (!lvl) { ui.text("Faith " + b.level + "  -  not yet granted", bx + 62, by + 12, 12, Style.FAINT); }
                    else {
                        ui.text(b.title, bx + 62, by + 4, 14, on ? 0xFFF4DC8A : Style.TEXT);
                        ui.text(on ? "raised" : overhead ? "protection  -  one at a time" : "blessing", bx + 62, by + 22, 10, on ? Style.FAITH : Style.DIM);
                    }
                    if (hv) hud.tooltip(b.title + " (Faith " + b.level + ")\n" + b.effect);
                    if (hv && live && ui.clicked(bx, by, 422, 40)) { if (lvl) g.combat.bless(p, b); else g.chat.warn("You need Faith level " + b.level + "."); }
                    colY[c] += 44;
                }
            }
            ui.text("Protections", x + 2, y + 84, 12, 0xFFC8A860);
            ui.text("Blessings", x + 432, y + 84, 12, 0xFFC8A860);
        }
    }

}
