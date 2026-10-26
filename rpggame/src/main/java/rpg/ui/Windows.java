package rpg.ui;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import rpg.Ids;
import rpg.Rpg;
import rpg.ai.Npc;
import rpg.econ.Economy;
import rpg.interact.Result;
import rpg.item.Currency;
import rpg.item.ItemDef;
import rpg.item.ItemStack;
import rpg.item.Items;
import rpg.item.Recipes;
import rpg.item.Tier;
import rpg.objects.Container;
import rpg.objects.Fixtures;
import rpg.objects.Station;
import rpg.world.DevMap;

/**
 * The windows things in the world open: a container's grid and a shop's counter (gamefixes: free-standing windows of
 * their own beside the inventory, never part of it), a station's recipes, the test cabinet and the quest kiosk.
 */
final class Windows {
    private Windows() { }

    static void register(Hud hud) {
        hud.add(new ContainerWin(hud));
        hud.add(new ShopWin(hud));
        hud.add(new StationWin(hud));
        hud.add(new CabinetWin(hud));
        hud.add(new KioskWin(hud));
    }

    static abstract class Win extends Fragment {
        final float ww, wh;
        Win(String id, float ww, float wh) { super(id); this.ww = ww; this.wh = wh; fadeIn = 0.1f; fadeOut = 0.15f; }
        @Override public float width() { return ww; }
        @Override public float height() { return wh; }
        /** A free-standing window's card, title and close button; returns the content's top. */
        float card(Ui2D ui, String title, float x, float y) {
            Style.card(ui, x, y, ww, wh);
            ui.text(ui.heading, title, x + 12, y + 6, 20, Style.ACCENT);
            if (Style.button(ui, "x", x + ww - 32, y + 7, 24, 22, live)) { hud.win = Hud.Win.NONE; hud.sound("UI_windowclose", 0.4f); }
            ui.rect(x + 12, y + 36, ww - 24, 1, Style.EDGE);
            return y + 46;
        }
    }

    /** Where a detached window stands: left of the inventory's frame, or at the screen's edge when that is too narrow. */
    static void detach(Win w) {
        float room = (w.hud.w - w.hud.frameWidth()) / 2f;
        w.moved = room >= w.ww + 28 ? null : Fragment.Placement.at(Fragment.Anchor.LEFT, 12, Hud.FRAME_Y);
    }

    // ------------------------------------------------------------------ a container: a window of its own, beside the inventory
    static final class ContainerWin extends Win {
        private long shownContainer;
        private int firstRow;
        ContainerWin(Hud h) { super("container", 300, 500); }
        @Override protected boolean active() { detach(this); return hud.win == Hud.Win.CONTAINER && hud.container != null; }
        @Override public void draw(Ui2D ui, float x, float y) {
            Container c = hud.container;
            if (c == null) return;
            Rpg g = hud.g;
            boolean theft = c.isTheft(g.player);
            float top = card(ui, c.name, x, y), ix = x + 12, iw = ww - 24;
            ui.text(theft ? "Owned by " + g.nameOf(c.owner) + ": taking is theft" : c.owner == g.player.id ? "Yours" : "Free to take", ix, top, 11, theft ? Style.BAD : Style.DIM);
            if (c.kind.equals("vault")) {
                ui.text(Currency.text(g.bank.coinsIn(c.owner)) + " G / " + c.contents.size() + " cells", ix, top + 15, 11, Style.DIM);
                top += 20;
            }
            boolean vault = c.kind.equals("vault");
            float cs = vault ? Math.min(30, iw / c.contents.w) : Math.min(Math.min(30, iw / c.contents.w), (wh - 200) / c.contents.h);
            int rows = vault ? Math.min(8, c.contents.h) : c.contents.h;
            if (shownContainer != c.id) { shownContainer = c.id; firstRow = 0; }
            if (vault && ui.hover(ix, top + 20, iw, rows * cs) && ui.scroll != 0) firstRow -= (int)Math.signum(ui.scroll) * 2;
            firstRow = Math.clamp(firstRow, 0, c.contents.h - rows);
            hud.inventory.drawGrid(ui, c.contents, ix, top + 20, cs, InventoryPanel.Source.CONTAINER, firstRow, rows);
            float by = top + 20 + rows * cs + 10;
            if (vault) {
                if (Style.button(ui, "Up", ix, by, 44, 22, live && firstRow > 0)) firstRow = Math.max(0, firstRow - rows);
                ui.flatCentered("Rows " + (firstRow + 1) + "-" + (firstRow + rows) + " / " + c.contents.h, ix + iw / 2, by + 4, 11, Style.DIM);
                if (Style.button(ui, "Down", ix + iw - 44, by, 44, 22, live && firstRow + rows < c.contents.h)) firstRow = Math.min(c.contents.h - rows, firstRow + rows);
                by += 28;
            }
            x = ix;
            // a body: what they still wear, as it is on them - a piece taken comes off the body
            if (c instanceof rpg.objects.Corpse cp) {
                var worn = cp.worn(g);
                ui.text(worn.isEmpty() ? "Wearing nothing worth taking" : "Wearing", x, by, 11, Style.DIM);
                float ws = 40;
                int i = 0;
                for (var slot : worn) {
                    float wx = x + (i % 6) * (ws + 4), wy = by + 16 + (i / 6) * (ws + 4);
                    i++;
                    Style.well(ui, wx, wy, ws, ws);
                    var npc = cp.npc(g);
                    var st = npc == null ? null : npc.eq.get(slot);
                    if (st == null) continue;
                    hud.itemIcon(ui, st, wx + 2, wy + 2, ws - 4, ws - 4, 1);
                    if (ui.hover(wx, wy, ws, ws)) hud.tooltip(st.def.name + " (" + slot.name().toLowerCase() + ")\nClick to take");
                    if (live && ui.clicked(wx, wy, ws, ws)) g.interact.request(g.player, cp, "takeworn:" + slot.name());
                }
                by += 16 + ((worn.size() + 5) / 6) * (ws + 4) + 8;
            }
            // two take-alls (gamefixes): the gear (anything worn or wielded, and what is still on a body) and the rest
            if (!c.kind.equals("pockets")) {
                boolean gear = c instanceof rpg.objects.Corpse cp2 && !cp2.worn(g).isEmpty() || c.contents.stacks().stream().anyMatch(s -> s.def.equippable());
                boolean rest = c.contents.stacks().stream().anyMatch(s -> !s.def.equippable());
                float bw = (iw - 8) / 2;
                if (Style.button(ui, "Take All Equipment", ix, by, bw, 28, live && gear)) g.interact.request(g.player, c, "takeall:equipment");
                if (Style.button(ui, "Take All Items", ix + bw + 8, by, bw, 28, live && rest)) g.interact.request(g.player, c, "takeall:items");
                by += 36;
            }
            // the bank (gamefixes): everything carried into the vault in one go
            if (c.kind.equals("vault")) {
                boolean carrying = g.player.grids().stream().anyMatch(grid -> !grid.stacks().isEmpty());
                if (Style.button(ui, "Move All", ix, by, iw, 28, live && carrying)) {
                    var r = g.interact.request(g.player, c, "depositall");
                    if (r.rejected()) g.chat.add(r.reason(), rpg.Chat.Kind.WARN);
                }
                if (ui.hover(ix, by, iw, 28)) hud.tooltip("Move everything in your inventory into the vault\n(what you wear stays on you)");
                by += 36;
            }
            ui.text(String.format("%.1f kg inside", c.contents.weight()), ix, by + 2, 12, Style.FAINT);
        }
    }

    // ------------------------------------------------------------------ a shop's counter: a window of its own
    static final class ShopWin extends Win {
        ShopWin(Hud h) { super("shop", 300, 500); }
        @Override protected boolean active() { detach(this); return hud.win == Hud.Win.SHOP && hud.shop != null; }
        @Override public void draw(Ui2D ui, float x, float y) {
            Rpg g = hud.g;
            Economy.Shop s = hud.shop;
            if (s == null) return;
            Npc m = g.econ.merchant(s);
            float top = card(ui, s.name, x, y);
            x += 12; y = top - 42;
            int purse = m == null ? 0 : m.count(Items.COINS);
            ui.text((m == null ? "" : m.name + "'s purse: ") + Currency.text(purse) + " G", x, y + 20, 11, Style.DIM);
            // one tile per kind in stock (the storeroom is behind the counter)
            Map<ItemDef, Integer> kinds = new LinkedHashMap<>();
            Map<ItemDef, ItemStack> sample = new LinkedHashMap<>();
            for (ItemStack it : s.stock.stacks()) { kinds.merge(it.def, it.amount, Integer::sum); sample.putIfAbsent(it.def, it); }
            int i = 0;
            float tw = 64, th = 64;
            for (var e : kinds.entrySet()) {
                ItemDef d = e.getKey();
                float cx = x + (i % 4) * (tw + 4), cy = y + 42 + (i / 4) * (th + 4);
                i++;
                ItemStack show = new ItemStack(d, e.getValue());
                Style.well(ui, cx, cy, tw, th);
                hud.itemIcon(ui, show, cx + 4, cy + 2, tw - 8, th - 20, 1);
                int price = g.econ.priceFor(g.player, s, d), list = g.econ.buyPrice(s, d);
                ui.flatCentered(price + " G", cx + tw / 2, cy + th - 16, 11, Currency.color(price));
                if (!live) continue;
                if (ui.hover(cx, cy, tw, th)) {
                    ui.border(cx, cy, tw, th, 1, Style.EDGE_HI);
                    hud.inventory.hovered = show;
                    hud.tooltip(d.name + "\nBuy for " + price + " G" + (price < list ? " (Honor price; " + list + " G to others)" : "") + " (" + e.getValue() + " in stock, wants " + s.target.getOrDefault(d.id, 4) + ")\nThe shop buys at " + g.econ.sellPrice(s, d) + " G\nClick to buy one, or drag it to your inventory");
                }
                if (ui.clicked(cx, cy, tw, th)) { Result r = g.econ.buy(g.player, s, d, 1, g.state.newRequestId()); if (r.rejected()) g.chat.warn(r.reason()); }
            }
            ui.text("Prices follow stock. Drag your goods here", x, y + wh - 32, 11, Style.FAINT);
            ui.text("to sell them.", x, y + wh - 18, 11, Style.FAINT);
            hud.inventory.zone(x - 12, y, ww, wh, d -> { if (d.source == InventoryPanel.Source.PLAYER) { Result r = g.econ.sell(g.player, d.grid, s, d.slot, d.stack.amount, g.state.newRequestId()); if (r.rejected()) g.chat.warn(r.reason()); } });
        }
    }

    // ------------------------------------------------------------------ a station's recipes
    static final class StationWin extends Win {
        StationWin(Hud h) { super("station", 460, 380); }
        @Override protected boolean active() { return hud.win == Hud.Win.STATION && hud.station != null; }
        @Override public void draw(Ui2D ui, float x, float y) {
            Station s = hud.station;
            if (s == null) return;
            Rpg g = hud.g;
            float yy = card(ui, s.name + " - " + s.verb(), x, y);
            for (Recipes.Recipe rc : s.recipes()) {
                if (yy > y + wh - 34) break;
                String why = s.refusal(rc, g.player);
                boolean ok = why == null;
                ui.img(Atlas.skill(rc.skill), x + 12, yy + 5, 20, ok ? 0xFFFFFFFF : 0x60FFFFFF);
                StringBuilder in = new StringBuilder();
                for (Recipes.Part pt : rc.inputs) in.append(in.length() > 0 ? ", " : "").append(pt.amount()).append(" ").append(pt.item().name);
                ui.text(rc.name + "  (level " + rc.level + ")", x + 40, yy + 1, 13, ok ? Style.TEXT : Style.DIM);
                ui.text(ok ? in.toString() : why, x + 40, yy + 17, 11, ok ? Style.DIM : Style.BAD);
                if (Style.button(ui, "Make", x + ww - 116, yy + 4, 50, 24, live && ok)) { g.interact.request(g.player, s, "make:" + rc.id); hud.win = Hud.Win.NONE; }
                if (Style.button(ui, "All", x + ww - 60, yy + 4, 46, 24, live && ok)) { g.interact.request(g.player, s, "makeall:" + rc.id); hud.win = Hud.Win.NONE; }
                yy += 36;
            }
        }
    }

    // ------------------------------------------------------------------ the equipment cabinet (test supply)
    static final class CabinetWin extends Win {
        private float scroll;
        CabinetWin(Hud h) { super("cabinet", 520, 460); }
        @Override protected boolean active() { return hud.win == Hud.Win.CABINET; }
        @Override public void draw(Ui2D ui, float x, float y) {
            Rpg g = hud.g;
            float top = card(ui, "Equipment cabinet (test supply)", x, y);
            if (ui.hover(x, top, ww, wh - 46)) scroll = Math.max(0, Math.min(400, scroll - (float) ui.scroll * 30));
            Fixtures.Cabinet cab = (Fixtures.Cabinet) g.objects.all().stream().filter(o -> o instanceof Fixtures.Cabinet).findFirst().orElse(null);
            float yy = top - scroll;
            String[] kinds = {"sword", "axe", "mace", "dagger", "pickaxe", "shield", "helm", "body", "legs"};
            for (Tier t : Tier.values()) {
                if (yy > top - 4 && yy < y + wh - 28) {
                    ui.text(t.title + " (" + t.requirement + ")", x + 12, yy + 5, 12, 0xFF000000 | t.color | 0x303030);
                    float bx = x + 130;
                    for (String k : kinds) {
                        ItemDef d = Items.tierItem(t, k);
                        if (d == null) continue;
                        String why = rpg.item.Equipment.refusal(d, g.player.skills);
                        if (Style.button(ui, k.substring(0, 3), bx, yy, 38, 22, live)) g.interact.request(g.player, cab, "take:" + d.id);
                        if (ui.hover(bx, yy, 38, 22)) hud.tooltip(d.name + "\nRequires " + d.reqSkill.title + " " + d.reqLevel + (why == null ? "\nYou can equip it" : "\n" + why));
                        bx += 41;
                    }
                }
                yy += 27;
            }
            Object[][] extras = {{"Bows & arrows", new String[]{"shortbow", "oak_shortbow", "willow_shortbow", "bronze_arrows", "iron_arrows"}},
                    {"Magic & faith", new String[]{"apprentice_staff", "fire_staff", "wand", "holy_symbol", "blessed_symbol"}},
                    {"Tools", new String[]{"tinderbox", "knife", "hammer", "fishing_rod", "lockpick"}},
                    {"Bags", new String[]{"backpack", "pouch", "sack"}}};
            for (Object[] e : extras) {
                if (yy > top - 4 && yy < y + wh - 28) {
                    ui.text((String) e[0], x + 12, yy + 5, 12, Style.ACCENT);
                    float bx = x + 130;
                    for (String id : (String[]) e[1]) {
                        ItemDef d = Items.get(id);
                        if (d == null) continue;
                        if (Style.button(ui, Hud.abbreviation(d.name), bx, yy, 60, 22, live)) g.interact.request(g.player, cab, "take:" + id);
                        if (ui.hover(bx, yy, 60, 22)) hud.tooltip(d.name);
                        bx += 64;
                    }
                }
                yy += 27;
            }
        }
    }

    // ------------------------------------------------------------------ the quest-state kiosk
    static final class KioskWin extends Win {
        KioskWin(Hud h) { super("kiosk", 500, 360); }
        @Override protected boolean active() { return hud.win == Hud.Win.KIOSK; }
        @Override public void draw(Ui2D ui, float x, float y) {
            Rpg g = hud.g;
            float yy = card(ui, "Quest-state kiosk - QT-COURIER", x, y);
            String[] states = {"NOT_STARTED", "ACTIVE", "COMPLETED", "FAILED"};
            for (int i = 0; i < states.length; i++) {
                boolean on = g.quests.courier.name().equals(states[i]);
                Style.tab(ui, states[i].toLowerCase().replace('_', ' '), x + 12 + i * 119, yy, 113, 28, on);
            }
            ui.text("Active to completed: deliver the letter to Kit. Any state to failed: Kit dies.", x + 12, yy + 36, 11, Style.DIM);
            yy += 58;
            Npc kit = (Npc) g.actor(Ids.CONTACT);
            ui.text("Dependency NPC: Kit (#" + Ids.CONTACT + ") - " + (kit.alive ? "alive" : "DEAD (permanent)"), x + 12, yy, 13, kit.alive ? Style.GOOD : Style.BAD);
            yy += 20;
            if (g.quests.failReason != null) { ui.text("Failure reason: " + g.quests.failReason, x + 12, yy, 12, Style.BAD); yy += 18; }
            ui.text("Journal", x + 12, yy, 13, Style.ACCENT);
            yy += 18;
            List<String> lines = new ArrayList<>();
            for (String j : g.quests.journal) lines.addAll(HudFragments.wrap(ui, j, 11, ww - 30));
            for (int i = Math.max(0, lines.size() - 7); i < lines.size(); i++) { ui.text(lines.get(i), x + 18, yy, 11, Style.TEXT); yy += 14; }
            float by = y + wh - 36;
            if (Style.button(ui, "Remove Kit (kill)", x + 12, by, 150, 26, live && kit.alive)) {
                kit.hp = 0;
                var ev = g.state.commit("DebugRemoveNpc", g.player.id, kit.id, DevMap.BETA, 0, "kiosk: remove dependency NPC");
                kit.alive = false;
                g.state.commit("ActorKilled", 0, kit.id, DevMap.BETA, ev.id(), "Kit removed at the kiosk");
                g.ai.onKilled(kit, g.player);
            }
            if (Style.button(ui, "Reset fixture (debug)", x + 170, by, 170, 26, live)) { g.quests.reset(); if (!kit.alive) g.ai.revive(kit, true); }
            if (Style.button(ui, "Flag: test", x + 348, by, 140, 26, live)) { g.quests.flags.put("test", !g.quests.flags.getOrDefault("test", false)); g.state.commit("QuestFlag", 0, 0, null, 0, "test=" + g.quests.flags.get("test")); }
        }
    }
}
