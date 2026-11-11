package rpg.ui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import rpg.Rpg;
import rpg.item.ItemDef;
import rpg.item.ItemStack;
import rpg.item.Recipes;
import rpg.objects.Station;

/**
 * (0.3.7, gamefixes: the smithing and cooking windows redone, more polished and presentable) one window for every
 * station - the forge's furnace and anvil, the range and the fire, the benches - in three columns:
 *
 * <ul>
 *   <li>what kind of thing (at the anvil each metal of the ladder with its bars carried; at the furnace bars and
 *       castings; at a fire what is on hand to cook and everything else);</li>
 *   <li>what can be made of that kind, each a card - its picture, name and level - lit when it can be made now, plain
 *       when something is missing, greyed with its level when the level is not there yet;</li>
 *   <li>the one chosen, large: what goes in (each with how many are carried against how many it takes), the tool, the
 *       XP, a fire's chance to burn it, how many to make, and the button.</li>
 * </ul>
 * (A list of a hundred anvil recipes on one page, each line "name (level)" with a Make and an All button, was the old
 * smithing window; cooking had its own, of a different look.)
 */
final class CraftWindow extends Windows.Win {
    private String category;
    private String chosen;
    private float scroll;
    private final Map<String, Integer> amounts = new LinkedHashMap<>();
    private Station seen;

    static final float W = 780, H = 470, CAT_W = 180, LIST_W = 300, CARD_H = 46;

    CraftWindow(Hud h) { super("station", W, H); }

    @Override protected boolean active() { return hud.win == Hud.Win.STATION && hud.station != null; }

    /** The kind a recipe is listed under at its station. */
    static String categoryOf(Recipes.Recipe r, Rpg g) {
        ItemDef out = r.outputs.isEmpty() ? null : r.outputs.get(0).item();
        return switch (r.station) {
            case ANVIL -> out != null && out.tier != null ? out.tier.title : "Other";
            case FURNACE -> out != null && out.id.endsWith("_bar") ? "Bars" : "Castings";
            case RANGE -> !r.inputs.isEmpty() && g.player.count(r.inputs.get(0).item()) >= r.inputs.get(0).amount() ? "On hand" : "Every dish";
            default -> "Everything";
        };
    }

    @Override public void draw(Ui2D ui, float x, float y) {
        Station s = hud.station;
        if (s == null) return;
        Rpg g = hud.g;
        var p = g.player;
        if (s != seen) { seen = s; category = null; chosen = null; scroll = 0; }
        List<Recipes.Recipe> all = s.recipes();
        rpg.skill.Skill skill = all.isEmpty() ? rpg.skill.Skill.CRAFTING : all.get(0).skill;
        float top = card(ui, s.name + "  -  " + s.verb(), x, y);
        String lv = skill.title + " level " + p.skills.level(skill);
        ui.text(lv, x + ww - 44 - ui.width(lv, 13), y + 11, 13, Style.DIM);
        // ---- the kinds
        Map<String, List<Recipes.Recipe>> kinds = new LinkedHashMap<>();
        for (Recipes.Recipe r : all) kinds.computeIfAbsent(categoryOf(r, g), k -> new ArrayList<>()).add(r);
        if (s.kind == Recipes.Station.RANGE && !kinds.containsKey("On hand")) { var rest = kinds.remove("Every dish"); kinds.put("On hand", List.of()); if (rest != null) kinds.put("Every dish", rest); }
        if (category == null || !kinds.containsKey(category)) category = kinds.keySet().stream().findFirst().orElse("");
        float cy = top + 4;
        for (var e : kinds.entrySet()) {
            boolean on = e.getKey().equals(category);
            int able = 0, lowest = Integer.MAX_VALUE;
            for (Recipes.Recipe r : e.getValue()) { if (s.refusal(r, p) == null) able++; lowest = Math.min(lowest, r.level); }
            boolean reached = e.getValue().isEmpty() || p.skills.level(skill) >= lowest;
            boolean hv = ui.hover(x + 12, cy, CAT_W - 8, 34);
            ui.rect(x + 12, cy, CAT_W - 8, 34, on ? 0xFF3A3024 : hv ? 0xFF2C251C : 0xFF211C16);
            ui.border(x + 12, cy, CAT_W - 8, 34, 1, on ? Style.GOLD : 0xFF0E0B08);
            if (on) ui.rect(x + 12, cy, 3, 34, Style.ACCENT);
            // the metal's bar (or the first thing the kind makes) as its picture
            ItemDef pic = picture(e.getKey(), e.getValue());
            if (pic != null) hud.itemIcon(ui, new ItemStack(pic, 1), x + 18, cy + 3, 28, 28, reached ? 1 : 0.35f);
            ui.text(e.getKey(), x + 52, cy + 3, 13, on ? Style.TEXT : reached ? Style.DIM : Style.FAINT);
            String sub = e.getValue().isEmpty() ? "nothing raw carried" : !reached ? "from level " + lowest : able > 0 ? able + " can be made" : e.getValue().size() + " recipes";
            ui.text(sub, x + 52, cy + 18, 10, able > 0 ? Style.GOOD : Style.FAINT);
            if (hv && live && ui.click) { category = e.getKey(); chosen = null; scroll = 0; hud.sound("UIbutton1", 0.35f); ui.consumed = true; }
            cy += 38;
            if (cy > y + H - 40) break;
        }
        // ---- the recipes of the kind
        List<Recipes.Recipe> list = kinds.getOrDefault(category, List.of());
        float lx = x + CAT_W + 14, ly = top + 4, lh = y + H - 14 - ly;
        Panels.Panel.sheet(ui, lx - 4, ly - 4, LIST_W + 8, lh + 8);
        if (list.isEmpty()) {
            ui.flatCentered(s.kind == Recipes.Station.RANGE ? "You have nothing raw to cook." : "Nothing to make here.", lx + LIST_W / 2, ly + 40, 14, Style.TEXT);
            if (s.kind == Recipes.Station.RANGE) ui.flatCentered("Catch fish, butcher game or pick crops.", lx + LIST_W / 2, ly + 62, 12, Style.DIM);
        }
        float content = list.size() * (CARD_H + 4);
        if (ui.hover(lx, ly, LIST_W, lh)) scroll = Math.max(0, Math.min(Math.max(0, content - lh), scroll - (float) ui.scroll * 40));
        if (chosen == null && !list.isEmpty()) {
            chosen = list.get(0).id;
            for (Recipes.Recipe r : list) if (s.refusal(r, p) == null) { chosen = r.id; break; }
        }
        for (int i = 0; i < list.size(); i++) {
            Recipes.Recipe r = list.get(i);
            float ry = ly + i * (CARD_H + 4) - scroll;
            if (ry < ly - 1 || ry + CARD_H > ly + lh + 1) continue;
            String why = s.refusal(r, p);
            boolean ok = why == null, reached = p.skills.level(r.skill) >= r.level, on = r.id.equals(chosen);
            boolean hv = ui.hover(lx, ry, LIST_W, CARD_H);
            ui.rect(lx, ry, LIST_W, CARD_H, on ? 0xFF3A3024 : hv ? 0xFF2A231B : 0xFF1E1914);
            ui.border(lx, ry, LIST_W, CARD_H, 1, on ? Style.GOLD : ok ? 0x806E9A5E : 0xFF0E0B08);
            ItemDef out = r.outputs.isEmpty() ? null : r.outputs.get(0).item();
            Style.well(ui, lx + 4, ry + 4, 38, 38);
            if (out != null) hud.itemIcon(ui, new ItemStack(out, Math.max(1, r.outputs.get(0).amount())), lx + 6, ry + 6, 34, 34, reached ? 1 : 0.3f);
            ui.text(r.name, lx + 50, ry + 6, 13, ok ? Style.TEXT : reached ? Style.DIM : Style.FAINT);
            String sub = !reached ? "Level " + r.level : ok ? "Ready - level " + r.level : missing(r, p);
            ui.text(sub, lx + 50, ry + 24, 11, !reached ? Style.FAINT : ok ? Style.GOOD : Style.WARN);
            if (hv && live && ui.click) { chosen = r.id; hud.sound("UI_select", 0.3f); ui.consumed = true; }
        }
        if (content > lh) { float sb = lh * lh / content, sp = (lh - sb) * scroll / Math.max(1, content - lh); ui.rect(lx + LIST_W + 1, ly + sp, 3, sb, 0x60E8C878); }
        // ---- the one chosen
        Recipes.Recipe r = Recipes.byId(chosen == null ? "" : chosen);
        float dx = lx + LIST_W + 18, dw = x + W - 14 - dx;
        if (r == null || !list.contains(r)) return;
        ItemDef out = r.outputs.isEmpty() ? null : r.outputs.get(0).item();
        String why = s.refusal(r, p);
        boolean ok = why == null;
        float dy = top + 2;
        Style.well(ui, dx, dy, 64, 64);
        if (out != null) hud.itemIcon(ui, new ItemStack(out, Math.max(1, r.outputs.get(0).amount())), dx + 3, dy + 3, 58, 58, 1);
        for (String l : HudFragments.wrap(ui, r.name, 16, dw - 74)) { ui.text(l, dx + 74, dy + 2, 16, 0xFFFFD15E); dy += 19; }
        dy = Math.max(dy, top + 2);
        ui.text("Level " + r.level + "   " + String.format("%,d", r.xp / 10) + " XP", dx + 74, top + 44, 12, p.skills.level(r.skill) >= r.level ? Style.DIM : Style.BAD);
        dy = top + 80;
        ui.text("Needs", dx, dy, 12, Style.ACCENT); dy += 18;
        for (Recipes.Part part : r.inputs) {
            int have = p.count(part.item());
            hud.itemIcon(ui, new ItemStack(part.item(), 1), dx, dy, 26, 26, 1);
            ui.text(part.item().name, dx + 32, dy + 6, 12, Style.TEXT);
            String n = have + " / " + part.amount();
            ui.text(n, dx + dw - ui.width(n, 12), dy + 6, 12, have >= part.amount() ? Style.GOOD : Style.BAD);
            dy += 30;
        }
        if (r.tool != null) {
            boolean has = p.has(r.tool, 1);
            hud.itemIcon(ui, new ItemStack(r.tool, 1), dx, dy, 26, 26, 1);
            ui.text(r.tool.name + " (kept)", dx + 32, dy + 6, 12, Style.TEXT);
            ui.text(has ? "carried" : "missing", dx + dw - ui.width(has ? "carried" : "missing", 12), dy + 6, 12, has ? Style.GOOD : Style.BAD);
            dy += 30;
        }
        double burn = Station.burnChance(r, p);
        if (r.burnt != null) {
            dy += 4;
            ui.text("Chance to burn", dx, dy, 12, Style.DIM);
            String b = burn <= 0 ? "none" : Math.round(burn * 100) + "%";
            ui.text(b, dx + dw - ui.width(b, 12), dy, 12, burn <= 0 ? Style.GOOD : burn < 0.2 ? Style.WARN : Style.BAD);
            Style.bar(ui, dx, dy + 17, dw, 4, (float) burn / 0.6f, burn < 0.2 ? Style.WARN : Style.BAD);
            ui.text(burn <= 0 ? "Never burnt at your level." : "Stops burning at level " + r.burnStop + ".", dx, dy + 25, 11, Style.FAINT);
            dy += 44;
        }
        // what it is: its stats as the item read-out gives them, then its own words
        if (out != null) {
            dy += 6;
            Panels.Panel.sheet(ui, dx, dy, dw, 1);
            dy += 8;
            float stop = y + H - 140;
            for (String l : hud.describe(new ItemStack(out, 1)).split("\n")) {
                if (l.equals(out.name) || l.isBlank()) continue;
                if (dy > stop) break;
                ui.text(l, dx, dy, 11, Style.DIM); dy += 15;
            }
            Ui2D.Face was = ui.use(ui.chat);
            for (String l : HudFragments.wrap(ui, out.examine == null ? "" : out.examine, 13, dw)) { if (dy > stop) break; ui.text(l, dx, dy + 2, 13, 0xFFC8B896); dy += 16; }
            ui.use(was);
        }
        // how many, and the button
        int can = ok ? makeable(r, p) : 0;
        int n = Math.max(1, Math.min(Math.max(1, can), amounts.getOrDefault(r.id, Math.max(1, can))));
        float by = y + H - 96;
        if (!ok) { List<String> ls = HudFragments.wrap(ui, why, 12, dw); for (int i = 0; i < ls.size(); i++) ui.text(ls.get(i), dx, by - 12 - 16 * (ls.size() - i), 12, Style.BAD); }
        ui.text("How many", dx, by + 6, 12, Style.DIM);
        float qx = dx + dw - 150;
        if (Style.button(ui, "-", qx, by, 26, 26, live && ok && n > 1)) amounts.put(r.id, n - 1);
        ui.flatCentered(String.valueOf(n), qx + 46, by + 5, 15, ok ? Style.TEXT : Style.FAINT);
        if (Style.button(ui, "+", qx + 66, by, 26, 26, live && ok && n < can)) amounts.put(r.id, n + 1);
        if (Style.button(ui, "All", qx + 98, by, 52, 26, live && ok && n < can)) amounts.put(r.id, can);
        if (Style.button(ui, s.verb() + (n > 1 ? " " + n : ""), dx, by + 36, dw, 34, live && ok)) {
            g.interact.request(p, s, "cook:" + r.id + ":" + n);
            hud.win = Hud.Win.NONE; hud.sound("UI_select", 0.4f);
        }
    }

    /** How many of a recipe the carried inputs make. */
    private static int makeable(Recipes.Recipe r, rpg.actor.Actor a) {
        int n = Integer.MAX_VALUE;
        for (Recipes.Part part : r.inputs) n = Math.min(n, a.count(part.item()) / Math.max(1, part.amount()));
        return n == Integer.MAX_VALUE ? 1 : n;
    }
    /** What the first missing input is, in a few words. */
    private static String missing(Recipes.Recipe r, rpg.actor.Actor a) {
        for (Recipes.Part part : r.inputs) if (a.count(part.item()) < part.amount()) return "Needs " + part.amount() + " " + part.item().name.toLowerCase();
        if (r.tool != null && !a.has(r.tool, 1)) return "Needs a " + r.tool.name.toLowerCase();
        return "No room for it";
    }
    /** The picture a kind is shown by: a metal's bar, else the first thing it makes. */
    private static ItemDef picture(String kind, List<Recipes.Recipe> rs) {
        for (Recipes.Recipe r : rs) for (Recipes.Part part : r.inputs) if (part.item().id.endsWith("_bar") && part.item().name.toLowerCase().startsWith(kind.toLowerCase())) return part.item();
        for (rpg.item.Minerals.Metal m : rpg.item.Minerals.METALS) if (m.tier().title.equals(kind)) return m.bar();
        return rs.isEmpty() || rs.get(0).outputs.isEmpty() ? null : rs.get(0).outputs.get(0).item();
    }
}
