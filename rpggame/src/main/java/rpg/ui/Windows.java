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
        hud.add(new VaultWin(hud));
        hud.add(new ShopWin(hud));
        hud.add(new StationWin(hud));
        hud.add(new CookWin(hud));
        hud.add(new CabinetWin(hud));
        hud.add(new KioskWin(hud));
    }

    static abstract class Win extends Fragment {
        final float ww, wh;
        Win(String id, float ww, float wh) { super(id); this.ww = ww; this.wh = wh; fadeIn = 0.1f; fadeOut = 0.15f; }
        @Override public float width() { return ww; }
        @Override public float height() { return wh; }
        /** A free-standing window's card, title and close button; returns the content's top. */
        float card(Ui2D ui, String title, float x, float y) { return cardOf(ui, title, x, y, wh); }
        float cardOf(Ui2D ui, String title, float x, float y, float wh) {
            Style.card(ui, x, y, ww, wh);
            ui.text(ui.body, title, x + 12, y + 6, 20, Style.ACCENT);
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
            float cs = Math.min(Math.min(30, iw / c.contents.w), (wh - (c.kind.equals("vault") ? 256 : 200)) / c.contents.h);
            hud.inventory.drawGrid(ui, c.contents, ix, top + 20, cs, InventoryPanel.Source.CONTAINER);
            float by = top + 20 + c.contents.h * cs + 10;
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

    // ------------------------------------------------------------------ the bank vault: the centre of the bank's window
    /**
     * The vault (gamefixes: a better, easier bank, the vault taking centre stage): the Bank of ASHENWRATH's seal and the
     * holder's name across the top with the gold and the room left; the vault itself, large, in the middle - drag into it
     * from the pack on the right, or out of it; and under it the three things one comes to a bank to do in one press:
     * put away everything carried, put away the gold, take everything out.
     */
    static final class VaultWin extends Win {
        VaultWin(Hud h) { super("vault", Hud.VAULT_W, Hud.FH + 120); }
        @Override protected boolean active() { return hud.vaultOpen(); }
        @Override public void draw(Ui2D ui, float x, float y) {
            Container c = hud.container;
            if (c == null) return;
            Rpg g = hud.g;
            Style.card(ui, x, y, ww, wh);
            // the seal: a gold coin ring with the bank's letter
            float sx = x + 40, sy = y + 40;
            ui.disc(sx, sy, 24, 0xFF3A2C14);
            ui.ring(sx, sy, 24, 3, Style.GOLD_LIT);
            ui.ring(sx, sy, 18, 1, Style.GOLD);
            ui.flatCentered(ui.heading, "G", sx, sy - 14, 24, Style.GOLD_LIT);
            ui.text(ui.heading, "Bank of ASHENWRATH", x + 76, y + 12, 26, Style.ACCENT);
            ui.text(g.nameOf(c.owner) + "'s vault", x + 78, y + 44, 13, Style.DIM);
            if (Style.button(ui, "x", x + ww - 36, y + 12, 26, 26, live)) { hud.win = Hud.Win.NONE; hud.sound("UI_windowclose", 0.4f); }
            // what is held
            int used = c.contents.stacks().size(), cells = c.contents.size();
            float ix = x + 330;
            ui.img("Bank_map_icon", ix, y + 16, 22, 0xFFFFFFFF);
            ui.text(Currency.full(g.bank.coinsIn(c.owner)), ix + 28, y + 18, 15, Currency.color(g.bank.coinsIn(c.owner)));
            ui.text(used + " of " + cells + " spaces used", ix + 28, y + 40, 11, Style.DIM);
            Style.bar(ui, ix + 28, y + 56, 230, 4, used / (float) Math.max(1, cells), used > cells * 0.9f ? Style.BAD : Style.GOOD);
            Style.rule(ui, x + 12, y + 74, ww - 24);
            // the vault, large
            float gw = ww - 40, gh = wh - 74 - 26 - 64;
            float cs = Math.min(Math.min(56, gw / c.contents.w), gh / c.contents.h);
            float gx = x + (ww - cs * c.contents.w) / 2, gy = y + 86;
            Style.well(ui, gx - 4, gy - 4, cs * c.contents.w + 8, cs * c.contents.h + 8);
            hud.inventory.drawGrid(ui, c.contents, gx, gy, cs, InventoryPanel.Source.CONTAINER);
            // the three errands
            float by = y + wh - 56, bw = (ww - 24 - 16) / 3;
            boolean carrying = g.player.grids().stream().anyMatch(grid -> !grid.stacks().isEmpty());
            boolean coins = g.player.has(Items.COINS, 1), held = !c.contents.isEmpty();
            if (Style.button(ui, "Deposit Inventory", x + 12, by, bw, 34, live && carrying)) { var r = g.interact.request(g.player, c, "depositall"); if (r.rejected()) g.chat.warn(r.reason()); }
            if (Style.button(ui, "Deposit Gold", x + 20 + bw, by, bw, 34, live && coins)) { var r = g.interact.request(g.player, c, "depositcoins"); if (r.rejected()) g.chat.warn(r.reason()); }
            if (Style.button(ui, "Withdraw All", x + 28 + bw * 2, by, bw, 34, live && held)) { var r = g.interact.request(g.player, c, "takeall:items"); var r2 = g.interact.request(g.player, c, "takeall:equipment"); if (r.rejected() && r2.rejected()) g.chat.warn(r.reason()); }
            if (ui.hover(x + 12, by, bw, 34)) hud.tooltip("Everything in your pack into the vault\n(what you wear stays on you)");
            if (ui.hover(x + 20 + bw, by, bw, 34)) hud.tooltip("All the gold you carry into the vault");
            if (ui.hover(x + 28 + bw * 2, by, bw, 34)) hud.tooltip("Everything in the vault back into your pack, as far as it fits");
            ui.text("Drag between the vault and your pack; right-click a thing for more.", x + 12, y + wh - 16, 11, Style.FAINT);
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
        @Override protected boolean active() { return hud.win == Hud.Win.STATION && hud.station != null && hud.station.kind != Recipes.Station.RANGE; }
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

    // ------------------------------------------------------------------ cooking: the raw food carried, and how many
    /**
     * The cooking window (gamefixes: a proper cooking menu). At a range or a fire it lists what raw food the player
     * carries - raw on the left, what it becomes on the right, how much of it there is, the level it needs and how
     * likely it is to burn at theirs - each with how many to cook (one, a few, all of it) and a Cook button.
     */
    static final class CookWin extends Win {
        private final Map<String, Integer> amounts = new LinkedHashMap<>();
        CookWin(Hud h) { super("cooking", 520, 380); }
        /** As tall as its rows (one a kind of raw food carried; a message when there is none), at most five. */
        @Override public float height() {
            Station s = hud.station;
            int rows = 0;
            if (s != null) for (Recipes.Recipe rc : s.recipes()) if (!rc.inputs.isEmpty() && hud.g.player.count(rc.inputs.get(0).item()) > 0) rows++;
            return rows == 0 ? 200 : 54 + Math.min(5, rows) * 64;
        }
        @Override protected boolean active() { return hud.win == Hud.Win.STATION && hud.station != null && hud.station.kind == Recipes.Station.RANGE; }
        @Override public void draw(Ui2D ui, float x, float y) {
            Station s = hud.station;
            if (s == null) return;
            Rpg g = hud.g;
            var p = g.player;
            float wh = height();
            float yy = cardOf(ui, "Cooking - " + s.name, x, y, wh);
            int level = p.skills.level(rpg.skill.Skill.COOKING);
            String lv = "Cooking level " + level;
            ui.text(lv, x + ww - 44 - ui.width(lv, 12), y + 12, 12, Style.DIM);
            List<Recipes.Recipe> raw = new ArrayList<>();
            for (Recipes.Recipe rc : s.recipes()) if (!rc.inputs.isEmpty() && p.count(rc.inputs.get(0).item()) > 0) raw.add(rc);
            if (raw.isEmpty()) {
                ui.flatCentered("You have nothing raw to cook.", x + ww / 2, yy + 50, 15, Style.TEXT);
                ui.flatCentered("Catch fish or butcher game, then come back to the fire.", x + ww / 2, yy + 74, 12, Style.DIM);
                return;
            }
            for (Recipes.Recipe rc : raw) {
                if (yy > y + wh - 60) break;
                ItemDef in = rc.inputs.get(0).item(), out = rc.outputs.isEmpty() ? null : rc.outputs.get(0).item();
                int have = p.count(in) / Math.max(1, rc.inputs.get(0).amount());
                String why = s.refusal(rc, p);
                boolean ok = why == null;
                int n = Math.max(1, Math.min(have, amounts.getOrDefault(rc.id, have)));
                ui.rect(x + 10, yy, ww - 20, 58, 0x30000000);
                ui.border(x + 10, yy, ww - 20, 58, 1, Style.EDGE);
                hud.itemIcon(ui, new ItemStack(in, have), x + 16, yy + 9, 40, 40, 1);
                ui.text(">", x + 61, yy + 20, 14, Style.FAINT);
                if (out != null) hud.itemIcon(ui, new ItemStack(out, 1), x + 74, yy + 9, 40, 40, ok ? 1 : 0.4f);
                ui.text(rc.name, x + 124, yy + 7, 14, ok ? Style.TEXT : Style.DIM);
                double burn = Station.burnChance(rc, p);
                String risk = rc.burnt == null ? "" : burn <= 0 ? "  -  you won't burn it" : burn < 0.15 ? "  -  rarely burns" : burn < 0.35 ? "  -  sometimes burns" : "  -  often burns";
                ui.text(ok ? have + " raw carried" + risk : why, x + 124, yy + 25, 11, ok ? Style.DIM : Style.BAD);
                ui.text("Level " + rc.level + "   " + (rc.xp / 10) + " xp each", x + 124, yy + 40, 10, Style.FAINT);
                // how many: - n + and All
                float qx = x + ww - 196;
                if (Style.button(ui, "-", qx, yy + 16, 24, 26, live && ok && n > 1)) amounts.put(rc.id, n - 1);
                ui.flatCentered(String.valueOf(n), qx + 42, yy + 21, 15, Style.TEXT);
                if (Style.button(ui, "+", qx + 60, yy + 16, 24, 26, live && ok && n < have)) amounts.put(rc.id, n + 1);
                if (Style.button(ui, "All", qx + 88, yy + 16, 36, 26, live && ok && n < have)) amounts.put(rc.id, have);
                if (Style.button(ui, "Cook", qx + 130, yy + 16, 56, 26, live && ok)) {
                    g.interact.request(p, s, "cook:" + rc.id + ":" + n);
                    hud.win = Hud.Win.NONE; hud.sound("UI_select", 0.4f);
                }
                yy += 64;
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
                    {"Magic & faith", new String[]{"apprentice_staff", "fire_staff", "air_staff", "water_staff", "electric_staff", "earth_staff", "wand", "holy_symbol", "blessed_symbol"}},
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
