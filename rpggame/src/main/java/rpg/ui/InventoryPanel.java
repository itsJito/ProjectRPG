package rpg.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import rpg.Rpg;
import rpg.actor.Player;
import rpg.interact.ItemActions;
import rpg.interact.Option;
import rpg.interact.Result;
import rpg.item.Inventory;
import rpg.item.ItemDef;
import rpg.item.ItemDef.Slot;
import rpg.item.ItemStack;

/**
 * The inventory panel (gamefixes: tabbed, filterable inventories over the spatial grids). Every grid the player carries
 * is drawn as it really is (the base 10 x 4, the backpack's, the pouch's), items at their footprints. Tabs and the name
 * filter dim what does not match rather than reflowing, because where things lie is part of the inventory.
 *
 * <p>Items are dragged: within a grid or between grids (R turns the item while it is held), onto an equipment slot to
 * wear it, onto the action bar to bind it, into an open container or shop, or out of the panel to drop it on the ground.
 * A click without a drag does the item's first action; a right click lists them all.
 */
final class InventoryPanel extends Fragment {
    enum Source { PLAYER, CONTAINER, SHOP }
    /** An item in the hand: where it came from, and how it is turned. */
    static final class Drag {
        final Source source;
        final Inventory grid;
        final int slot;
        final ItemStack stack;
        boolean turned;
        Drag(Source source, Inventory grid, int slot, ItemStack stack, boolean turned) { this.source = source; this.grid = grid; this.slot = slot; this.stack = stack; this.turned = turned; }
        int fw() { return Inventory.fw(stack.def, turned); }
        int fh() { return Inventory.fh(stack.def, turned); }
    }
    record Zone(float x, float y, float w, float h, Consumer<Drag> accept) {}

    static final String[] TABS = {"All", "Arms", "Armour", "Tools", "Goods", "Food", "Bags", "Misc"};
    static final float CS = 30;

    boolean filterFocus;
    String filter = "";
    int tab;
    ItemStack hovered;
    private Drag drag;
    private Source pressSource;
    private Inventory pressGrid;
    private int pressSlot = -1;
    private float pressX, pressY;
    private final List<Zone> zones = new ArrayList<>(), nextZones = new ArrayList<>();
    private double hoverSoundAt;
    private ItemStack lastHovered;

    InventoryPanel(Hud h) { super("grids"); this.hud = h; fadeIn = 0.12f; fadeOut = 0.12f; }

    @Override public float width() { return 372; }
    @Override public float height() { return 452; }

    boolean dragging() { return drag != null; }
    void cancelDrag() { drag = null; pressSlot = -1; }

    /** A place this frame that takes a dropped item (registered while drawing; the last registered is on top). */
    void zone(float x, float y, float w, float h, Consumer<Drag> accept) { nextZones.add(new Zone(x, y, w, h, accept)); }

    boolean matches(ItemStack s) {
        if (s == null) return false;
        if (!filter.isEmpty() && !s.def.name.toLowerCase().contains(filter.toLowerCase())) return false;
        ItemDef.Category c = s.def.category();
        return switch (tab) {
            case 1 -> c == ItemDef.Category.WEAPON;
            case 2 -> c == ItemDef.Category.ARMOR;
            case 3 -> c == ItemDef.Category.TOOL;
            case 4 -> c == ItemDef.Category.RESOURCE;
            case 5 -> c == ItemDef.Category.CONSUMABLE;
            case 6 -> c == ItemDef.Category.CONTAINER;
            case 7 -> c == ItemDef.Category.CURRENCY || c == ItemDef.Category.MISC;
            default -> true;
        };
    }

    @Override public void draw(Ui2D ui, float x, float y) {
        Rpg g = hud.g;
        Player p = g.player;
        if (hovered != null && hovered != lastHovered && hud.now - hoverSoundAt > 0.08) { hud.sound("UI_hover", 0.18f); hoverSoundAt = hud.now; }
        lastHovered = hovered;
        hovered = null;
        // tabs
        float tx = x;
        for (int i = 0; i < TABS.length; i++) {
            float tw = ui.width(TABS[i], 13) + 14;
            if (Style.tab(ui, TABS[i], tx, y, tw, 24, tab == i) && live) { tab = i; hud.sound("UIbutton1", 0.35f); }
            tx += tw + 1;
        }
        // the filter box
        float fy = y + 30;
        ui.rect(x, fy, 372, 24, filterFocus ? 0x30FFFFFF : 0x14FFFFFF);
        ui.border(x, fy, 372, 24, 1, filterFocus ? Style.ACCENT : Style.EDGE);
        if (filterFocus) {
            filter += hud.typed.replaceAll("[^ -~]", "");
            if (hud.backspace && !filter.isEmpty()) filter = filter.substring(0, filter.length() - 1);
        }
        boolean blink = filterFocus && (hud.now * 2 % 2) < 1;
        ui.text(filter.isEmpty() && !filterFocus ? "Filter by name..." : filter + (blink ? "|" : ""), x + 8, fy + 5, 13, filter.isEmpty() && !filterFocus ? Style.FAINT : Style.TEXT);
        if (!filter.isEmpty() && Style.button(ui, "x", x + 372 - 22, fy + 3, 18, 18, live)) filter = "";
        if (live && ui.clicked(x, fy, 372, 24)) filterFocus = true;
        // the grids, as carried
        List<Inventory> grids = p.grids();
        float gy = fy + 34;
        List<String> names = new ArrayList<>(List.of("Carried"));
        for (Slot sl : new Slot[]{Slot.BACK, Slot.BELT}) { ItemStack bag = p.eq.get(sl); if (bag != null && bag.inner != null) names.add(bag.def.name); }
        label(ui, names.get(0), grids.get(0), x, gy);
        drawGrid(ui, grids.get(0), x, gy + 18, CS, Source.PLAYER);
        float by = gy + 18 + grids.get(0).h * CS + 10, bx = x;
        for (int i = 1; i < grids.size(); i++) {
            Inventory inv = grids.get(i);
            label(ui, names.get(i), inv, bx, by);
            drawGrid(ui, inv, bx, by + 18, CS, Source.PLAYER);
            bx += inv.w * CS + 12;
        }
        if (grids.size() == 1) ui.text("Wear a backpack or a belt pouch for more room.", x, by + 4, 12, Style.FAINT);
    }

    private void label(Ui2D ui, String name, Inventory inv, float x, float y) {
        ui.text(name, x, y, 12, Style.DIM);
        String kg = String.format("%.1f kg", inv.weight());
        ui.text(kg, x + inv.w * CS - ui.width(kg, 11), y + 1, 11, Style.FAINT);
    }

    /** One grid: its cells, its items at their footprints, the drop preview, and the mouse on it. */
    void drawGrid(Ui2D ui, Inventory inv, float x, float y, float cs, Source src) {
        Rpg g = hud.g;
        hud.spots.put(inv, new float[]{x, y, cs});
        ui.rect(x, y, inv.w * cs, inv.h * cs, 0x40000000);
        for (int i = 1; i < inv.w; i++) ui.rect(x + i * cs, y, 1, inv.h * cs, 0x10FFFFFF);
        for (int j = 1; j < inv.h; j++) ui.rect(x, y + j * cs, inv.w * cs, 1, 0x10FFFFFF);
        ui.border(x, y, inv.w * cs, inv.h * cs, 1, Style.EDGE);
        boolean any = !filter.isEmpty() || tab != 0;
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.get(i);
            if (s == null) continue;
            boolean turned = inv.turned(i);
            float ix = x + inv.x(i) * cs, iy = y + inv.y(i) * cs, iw = Inventory.fw(s.def, turned) * cs, ih = Inventory.fh(s.def, turned) * cs;
            boolean held = drag != null && drag.grid == inv && drag.slot == i;
            float dim = held ? 0.25f : any && !matches(s) ? 0.22f : 1f;
            hud.itemIcon(ui, s, ix + 1, iy + 1, iw - 2, ih - 2, dim, turned);
            if (!live || drag != null) continue;
            if (ui.hover(ix, iy, iw, ih)) {
                hovered = s;
                ui.border(ix, iy, iw, ih, 1, Style.EDGE_HI);
                hud.tooltip(hud.describe(s) + hint(src));
                if (ui.click && !ui.consumed) { ui.consumed = true; pressSource = src; pressGrid = inv; pressSlot = i; pressX = ui.mx; pressY = ui.my; }
                if (ui.rclicked(ix, iy, iw, ih)) contextMenu(ui, src, inv, i, s);
            }
        }
        // a press that moves becomes a drag
        if (drag == null && pressGrid == inv && pressSlot >= 0 && ui.down && Math.hypot(ui.mx - pressX, ui.my - pressY) > 5 && inv.get(pressSlot) != null) {
            drag = new Drag(pressSource, inv, pressSlot, inv.get(pressSlot), inv.turned(pressSlot));
            hud.sound("UI_Trading_ItemSelect", 0.35f);
        }
        if (drag == null && pressGrid == inv && pressSlot >= 0 && ui.released) { click(src, inv, pressSlot); pressSlot = -1; }
        // where the held item would land
        if (drag != null && ui.hover(x, y, inv.w * cs, inv.h * cs)) {
            int[] c = cell(drag, x, y, cs, ui);
            boolean ok = drag.source == Source.SHOP || landing(drag, inv, c[0], c[1]);
            ui.rect(x + c[0] * cs, y + c[1] * cs, drag.fw() * cs, drag.fh() * cs, ok ? 0x3086B97E : 0x30D06A5E);
        }
        zone(x, y, inv.w * cs, inv.h * cs, d -> dropOnGrid(d, inv, x, y, cs, src));
    }

    private String hint(Source src) {
        return switch (src) {
            case CONTAINER -> "\nClick or drag to take";
            case SHOP -> "";
            default -> hud.win == Hud.Win.SHOP ? "\nClick to sell one; drag to the shop to sell all" : hud.win == Hud.Win.CONTAINER ? "\nClick or drag to deposit" : "\nDrag to move; right-click for options";
        };
    }

    private int[] cell(Drag d, float x, float y, float cs, Ui2D ui) {
        return new int[]{Math.round((ui.mx - x) / cs - d.fw() / 2f), Math.round((ui.my - y) / cs - d.fh() / 2f)};
    }

    /** Whether the held item can go at a cell of a grid (into free room, or onto a stack of its kind). */
    private boolean landing(Drag d, Inventory to, int cx, int cy) {
        if (d.stack.inner == to) return false;
        int there = to.at(Math.max(0, cx), Math.max(0, cy));
        if (there >= 0 && there != (to == d.grid ? d.slot : -2) && d.stack.def.stackable && to.get(there).sameKind(d.stack)) return true;
        if (to != d.grid && !to.accepts(d.stack)) return false;
        return to.fits(d.stack.def, cx, cy, d.turned, to == d.grid ? d.slot : -1);
    }

    // ------------------------------------------------------------------ clicks and menus
    private void click(Source src, Inventory inv, int slot) {
        Rpg g = hud.g;
        ItemStack s = inv.get(slot);
        if (s == null) return;
        switch (src) {
            case CONTAINER -> { if (hud.container != null) g.interact.request(g.player, hud.container, "take:" + slot); }
            case SHOP -> { }
            default -> {
                int gi = g.player.grids().indexOf(inv);
                if (hud.win == Hud.Win.SHOP) { Result r = g.econ.sell(g.player, inv, hud.shop, slot, 1, g.state.newRequestId()); if (r.rejected()) g.chat.warn(r.reason()); return; }
                if (hud.win == Hud.Win.CONTAINER) { g.interact.request(g.player, hud.container, "deposit:" + gi + ":" + slot); return; }
                for (Option op : g.items.options(g.player, inv, slot)) {
                    if (op.id().equals("drop") || op.id().equals("examine")) continue;
                    if (op.enabled()) { g.items.perform(g.player, inv, slot, op.id()); hud.sound("UI_select", 0.35f); } else g.chat.warn(op.reason());
                    return;
                }
                g.items.perform(g.player, inv, slot, "examine");
            }
        }
    }

    private void contextMenu(Ui2D ui, Source src, Inventory inv, int slot, ItemStack s) {
        Rpg g = hud.g;
        List<Hud.MenuItem> items = new ArrayList<>();
        if (src == Source.CONTAINER) items.add(new Hud.MenuItem("Take " + s.def.name, true, null, () -> g.interact.request(g.player, hud.container, "take:" + slot)));
        else if (src == Source.PLAYER) {
            int gi = g.player.grids().indexOf(inv);
            if (hud.win == Hud.Win.SHOP) {
                items.add(new Hud.MenuItem("Sell 1 (" + g.econ.sellPrice(hud.shop, s.def) + " G)", true, null, () -> { Result r = g.econ.sell(g.player, inv, hud.shop, slot, 1, g.state.newRequestId()); if (r.rejected()) g.chat.warn(r.reason()); }));
                if (s.amount > 1) items.add(new Hud.MenuItem("Sell all", true, null, () -> { Result r = g.econ.sell(g.player, inv, hud.shop, slot, s.amount, g.state.newRequestId()); if (r.rejected()) g.chat.warn(r.reason()); }));
            }
            if (hud.win == Hud.Win.CONTAINER) items.add(new Hud.MenuItem("Deposit " + s.def.name, true, null, () -> g.interact.request(g.player, hud.container, "deposit:" + gi + ":" + slot)));
            for (Option op : g.items.options(g.player, inv, slot)) items.add(new Hud.MenuItem(op.label() + " " + s.def.name, op.enabled(), op.reason(), () -> g.items.perform(g.player, inv, slot, op.id())));
            items.add(new Hud.MenuItem("Bind to the action bar", true, null, () -> hud.bar.bindFirstFree(s.def)));
        }
        hud.openMenu(s.def.name, ui.mx, ui.my, items);
    }

    // ------------------------------------------------------------------ dropping
    private void dropOnGrid(Drag d, Inventory to, float x, float y, float cs, Source toSrc) {
        Rpg g = hud.g;
        int[] c = cell(d, x, y, cs, hud.ui);
        if (d.source == Source.SHOP) { if (toSrc == Source.PLAYER) buy(d); return; }
        if (d.source == Source.CONTAINER && toSrc == Source.PLAYER) { g.interact.request(g.player, hud.container, "take:" + d.slot); return; }
        if (d.source == Source.PLAYER && toSrc == Source.CONTAINER) {
            int gi = g.player.grids().indexOf(d.grid);
            g.interact.request(g.player, hud.container, "deposit:" + gi + ":" + d.slot + ":" + c[0] + ":" + c[1] + ":" + (d.turned ? 1 : 0));
            return;
        }
        if (d.source == Source.PLAYER && toSrc == Source.SHOP) { sellAll(d); return; }
        // rearranging within what is carried (or within the open container)
        if (!landing(d, to, c[0], c[1])) { hud.sound("UIbutton2", 0.25f); return; }
        if (to == d.grid && to.fits(d.stack.def, c[0], c[1], d.turned, d.slot)) { to.move(d.slot, c[0], c[1], d.turned); hud.sound("UI_Trading_ItemSelect", 0.3f); return; }
        ItemStack taken = d.grid.take(d.slot, d.stack.amount);
        if (taken == null) return;
        if (!to.putAt(taken, Math.max(0, c[0]), Math.max(0, c[1]), d.turned) && !d.grid.add(taken)) g.objects.drop(taken, g.player.x, g.player.y + 0.05f, g.player.z);
        hud.sound("UI_Trading_ItemSelect", 0.3f);
    }

    void equipFromDrag(Drag d, Slot where) {
        Rpg g = hud.g;
        if (d.source != Source.PLAYER) return;
        ItemDef def = d.stack.def;
        Slot to = where == Slot.OFF && ItemActions.offHandable(def) ? Slot.OFF : def.slot;
        if (to != where) { g.chat.warn("That doesn't go there."); return; }
        Result r = g.items.equip(g.player, d.grid, d.slot, where, g.state.newRequestId());
        if (r.rejected() && r.reason() != null) g.chat.warn(r.reason());
        else hud.sound("UI_Trading_ItemSelect", 0.4f);
    }

    private void buy(Drag d) {
        Rpg g = hud.g;
        Result r = g.econ.buy(g.player, hud.shop, d.stack.def, 1, g.state.newRequestId());
        if (r.rejected()) g.chat.warn(r.reason());
    }

    private void sellAll(Drag d) {
        Rpg g = hud.g;
        Result r = g.econ.sell(g.player, d.grid, hud.shop, d.slot, d.stack.amount, g.state.newRequestId());
        if (r.rejected()) g.chat.warn(r.reason());
    }

    /** Called once all fragments have drawn: the held item follows the mouse; a release lands it. */
    void drawDragged(Ui2D ui) {
        zones.clear();
        zones.addAll(nextZones);
        nextZones.clear();
        if (!ui.down && drag == null) pressSlot = -1;
        if (drag == null) return;
        if (hud.rotate && drag.source != Source.SHOP) { drag.turned = !drag.turned; hud.sound("UIbutton2", 0.25f); }
        float iw = drag.fw() * CS, ih = drag.fh() * CS;
        hud.itemIcon(ui, drag.stack, ui.mx - iw / 2, ui.my - ih / 2, iw, ih, 0.9f, drag.turned);
        if (!ui.released) return;
        Drag d = drag;
        drag = null;
        pressSlot = -1;
        for (int i = zones.size() - 1; i >= 0; i--) {
            Zone z = zones.get(i);
            if (ui.mx >= z.x() && ui.my >= z.y() && ui.mx < z.x() + z.w() && ui.my < z.y() + z.h()) { z.accept().accept(d); return; }
        }
        // outside every panel: it goes on the ground (only what the player carries)
        float fx = (hud.w - Hud.FW) / 2, fy = (hud.h - Hud.FH) / 2 + Hud.FRAME_Y;
        boolean inFrame = ui.mx >= fx && ui.my >= fy && ui.mx < fx + Hud.FW && ui.my < fy + Hud.FH;
        if (!inFrame && d.source == Source.PLAYER && d.grid.get(d.slot) == d.stack) { hud.g.items.perform(hud.g.player, d.grid, d.slot, "drop"); hud.sound("UI_windowclose", 0.25f); }
    }
}
