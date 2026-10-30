package rpg.ui;

import rpg.actor.Player;
import rpg.item.ItemStack;

/**
 * The radial wheel (gamefixes): TAB held freezes the hands and feet and opens eight quick slots round the middle of the
 * screen, a shortcut layer over the inventory for what is used often (food, potions, a torch, a bow). The pointer's
 * direction from the centre, as a polar angle snapped to one of eight, picks a slot - a small ring in the middle picks
 * none - and letting TAB go uses it (its item's first action, as the action bar does).
 *
 * <p>Edit mode: with the inventory open, the Quick Wheel button docks the wheel beside it. Clicking a slice arms it;
 * clicking an item then puts it in the armed slice, and an item dragged onto a slice goes there (by the angle where it
 * is let go). A right click on a slice empties it. The slots belong to the player ({@link Player#quick}) and are saved.
 */
public final class RadialWheel extends Fragment {
    static final int SLOTS = 8;
    static final float R = 168, INNER = 46, DEAD = 30;
    /** Open in play (TAB held), or docked by the inventory for assigning. */
    public boolean open, editing;
    /** The slice under the pointer (-1: the dead centre), and the armed slice for edit-mode clicks. */
    int hovered = -1, armed = 0;
    /** The pointer, from the wheel's centre (in play it is moved by the mouse's own motion; the cursor may be locked). */
    float px, py;
    private float cx, cy;
    private double openedAt;

    RadialWheel(Hud h) { super("radial"); hud = h; fadeIn = 0.08f; fadeOut = 0.12f; h.add(this); }

    @Override protected boolean active() { return open || editing && hud.scene == Hud.Scene.INVENTORY; }
    @Override public float width() { return R * 2 + 40; }
    @Override public float height() { return R * 2 + 40; }

    public void openInPlay() { if (open) return; open = true; px = py = 0; hovered = -1; openedAt = hud.now; hud.sound("UI_hover", 0.3f); }
    /** The mouse moved while the wheel is open in play (pixels). */
    public void pointer(float dx, float dy) {
        if (!open) return;
        px += dx; py += dy;
        float l = (float) Math.hypot(px, py);
        if (l > R) { px *= R / l; py *= R / l; }
    }
    /** TAB let go: the slot under the pointer is used. */
    public void release() {
        if (!open) return;
        open = false;
        hud.g.player.refreshQuick();
        hovered = slice(px, py, DEAD);   // where the pointer is now, not where it was last drawn
        Player.QuickSlot q = hovered < 0 ? null : hud.g.player.quick[hovered];
        if (q != null) { hud.useItem(q.item()); hud.sound("UI_select", 0.4f); hud.g.player.refreshQuick(); }
    }

    /** The slice a point (from the centre) falls in: its polar angle from straight up, clockwise, snapped to eighths; -1 inside the dead zone. */
    static int slice(float x, float y, float dead) {
        if (Math.hypot(x, y) < dead) return -1;
        double a = Math.atan2(x, -y);                       // 0 straight up, growing clockwise
        if (a < 0) a += Math.PI * 2;
        return (int) Math.floor((a + Math.PI / SLOTS) / (Math.PI * 2 / SLOTS)) % SLOTS;
    }

    void assign(int slot, rpg.item.ItemDef def) {
        Player p = hud.g.player;
        for (int i = 0; i < SLOTS; i++) if (p.quick[i] != null && p.quick[i].item() == def) p.quick[i] = null;   // one slot an item
        p.quick[slot] = new Player.QuickSlot(def, p.quickCount(def));
        hud.sound("UI_select", 0.35f);
    }

    @Override public void draw(Ui2D ui, float x, float y) {
        Player p = hud.g.player;
        p.refreshQuick();
        cx = x + width() / 2; cy = y + height() / 2;
        boolean edit = editing && !open;
        if (edit) { px = ui.mx - cx; py = ui.my - cy; }
        hovered = slice(px, py, edit ? INNER : DEAD);
        if (edit && Math.hypot(px, py) > R + 6) hovered = -1;
        if (edit && live && hovered >= 0 && ui.click && !ui.consumed) { armed = hovered; ui.consumed = true; }
        float grow = Math.min(1, (float) (hud.now - openedAt) / 0.1f);
        float r = edit ? R : R * (0.85f + 0.15f * grow);
        // in play the world behind is dimmed (it is held still while the wheel is up)
        if (!edit) ui.rect(0, 0, hud.w, hud.h, Style.fade(0x70000000, grow));
        // the wheel: a dark disc, eight slices with gilt seams, the one picked lit
        ui.disc(cx, cy, r + 6, 0xE0000000);
        for (int i = 0; i < SLOTS; i++) {
            double a0 = -Math.PI / 2 + (i - 0.5) * Math.PI * 2 / SLOTS, a1 = a0 + Math.PI * 2 / SLOTS;
            boolean lit = i == hovered, arm = edit && i == armed;
            ui.sector(cx, cy, INNER, r, a0 + 0.012, a1 - 0.012, lit ? 0xF84A3A22 : arm ? 0xF4362A1C : 0xF41A1612);
            ui.sector(cx, cy, r - 3, r, a0 + 0.012, a1 - 0.012, lit || arm ? Style.GOLD : 0xFF5A4A30);
            double am = (a0 + a1) / 2;
            float ix = cx + (float) Math.cos(am) * (INNER + r) / 2, iy = cy + (float) Math.sin(am) * (INNER + r) / 2;
            Player.QuickSlot q = p.quick[i];
            float s = lit ? 50 : 42;
            if (q != null) {
                hud.itemIcon(ui, new ItemStack(q.item(), Math.max(1, q.count())), ix - s / 2, iy - s / 2, s, s, 1);
                if (q.count() > 1) ui.shadow(String.valueOf(q.count()), ix + s / 2 - ui.width(String.valueOf(q.count()), 12), iy + s / 2 - 10, 12, Style.TEXT);
            } else ui.flatCentered(String.valueOf(i + 1), ix, iy - 7, 14, Style.FAINT);
            if (edit && live) {
                final int slot = i;
                // a drop onto the wheel goes to the slice under the point it was let go at
                if (i == 0) hud.inventory.zone(cx - r, cy - r, r * 2, r * 2, d -> {
                    int at = slice(ui.mx - cx, ui.my - cy, INNER);
                    if (d.source == InventoryPanel.Source.PLAYER && at >= 0) assign(at, d.stack.def);
                });
                if (lit && ui.rclick && q != null) { p.quick[slot] = null; ui.consumed = true; hud.sound("UIbutton2", 0.3f); }
            }
        }
        // the hub: what the picked slice holds, in words
        ui.disc(cx, cy, INNER - 4, 0xE0100D0A);
        ui.ring(cx, cy, INNER - 4, 2, 0xFF5A4A30);
        Player.QuickSlot pick = hovered >= 0 ? p.quick[hovered] : null;
        String name = pick != null ? pick.item().name : hovered >= 0 ? "Empty" : edit ? "Quick Wheel" : "Cancel";
        float ts = ui.width(name, 12) > (INNER - 8) * 2 ? 10 : 12;
        ui.flatCentered(name, cx, cy - 7, ts, pick != null ? Style.TEXT : Style.DIM);
        if (edit) {
            ui.centered("Click an item to put it in slot " + (armed + 1), cx, cy + r + 14, 12, Style.TEXT);
            ui.centered("Click a slot to select; Shift-click equips; right-click clears", cx, cy + r + 30, 11, Style.DIM);
        } else {
            // the pointer's direction
            // the pointer's direction: a notch on the hub's rim
            if (hovered >= 0) { float l = Math.max(1, (float) Math.hypot(px, py)); ui.line(cx + px / l * (INNER - 6), cy + py / l * (INNER - 6), cx + px / l * (INNER + 6), cy + py / l * (INNER + 6), 3, Style.GOLD); }
            String key = hud.settings.values.label(rpg.Preferences.Action.QUICK_WHEEL);
            ui.centered("Release " + key + (hovered >= 0 && pick != null ? " to use" : " to close"), cx, cy + r + 16, 13, Style.TEXT);
        }
    }
}
