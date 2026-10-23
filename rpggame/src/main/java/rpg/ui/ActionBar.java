package rpg.ui;

import rpg.Rpg;
import rpg.combat.Spells;
import rpg.interact.Option;
import rpg.item.Inventory;
import rpg.item.ItemDef;
import rpg.item.Items;

/**
 * The bottom-anchored action bar: ten slots on the number keys, each bound to an item (its first action: eat, wield,
 * bury, light...), a prayer or a spell. Items are bound by dropping them on a slot; a right click clears one. It shows
 * in a fight, after a slot is used, while hovered and in every panel scene; otherwise it gets out of the way.
 */
final class ActionBar extends Fragment {
    static final int SLOTS = 10;
    static final float S = 44, GAP = 4;
    /** A slot's binding: an item kind, a prayer, a spell, or nothing. */
    record Bind(ItemDef item, Spells.Prayer blessing, Spells.Spell spell) {
        static Bind of(ItemDef d) { return new Bind(d, null, null); }
        static Bind of(Spells.Prayer b) { return new Bind(null, b, null); }
        static Bind of(Spells.Spell s) { return new Bind(null, null, s); }
    }
    final Bind[] binds = new Bind[SLOTS];
    private final float[] flash = new float[SLOTS];

    ActionBar(Hud h) {
        super("actionbar");
        this.hud = h;
        binds[0] = Bind.of(Spells.Prayer.WARD); binds[1] = Bind.of(Spells.Prayer.BURST_OF_STRENGTH); binds[2] = Bind.of(Spells.Prayer.MEND);
        binds[3] = Bind.of(Spells.Spell.SPARK);
        wake("combat", "scene").linger(5);
        h.add(this);
    }

    @Override protected boolean active() { return hud.scene != Hud.Scene.WORLD || hud.hovering(this); }
    @Override public float width() { return SLOTS * S + (SLOTS - 1) * GAP; }
    @Override public float height() { return S; }

    void bindFirstFree(ItemDef d) {
        for (int i = 0; i < SLOTS; i++) if (binds[i] == null) { binds[i] = Bind.of(d); hud.sound("UI_select", 0.35f); return; }
        binds[SLOTS - 1] = Bind.of(d);
    }

    /** A number key: does what the slot holds. */
    void use(int k) {
        if (k < 0 || k >= SLOTS || binds[k] == null) return;
        Rpg g = hud.g;
        Bind b = binds[k];
        flash[k] = 0.25f;
        woken(hud.now);
        if (b.blessing() != null) { g.controller.in.bless = b.blessing().ordinal(); return; }
        if (b.spell() != null) {
            if (g.player.skills.level(rpg.skill.Skill.MAGIC) < b.spell().level) { g.chat.warn("You need Magic level " + b.spell().level + "."); return; }
            g.player.spell = b.spell();
            g.controller.in.cast = true;
            return;
        }
        for (Inventory grid : g.player.grids()) {
            int slot = grid.first(b.item());
            if (slot < 0) continue;
            for (Option op : g.items.options(g.player, grid, slot)) {
                if (op.id().equals("drop") || op.id().equals("examine")) continue;
                if (op.enabled()) g.items.perform(g.player, grid, slot, op.id()); else g.chat.warn(op.reason());
                return;
            }
            return;
        }
        g.chat.warn("You have no " + b.item().name.toLowerCase() + ".");
    }

    @Override public void draw(Ui2D ui, float x, float y) {
        Rpg g = hud.g;
        for (int i = 0; i < SLOTS; i++) {
            float sx = x + i * (S + GAP);
            flash[i] = Math.max(0, flash[i] - hud.dt);
            ui.rect(sx, y, S, S, flash[i] > 0 ? 0x40FFFFFF : 0x70141518);
            ui.border(sx, y, S, S, 1, Style.EDGE);
            Bind b = binds[i];
            if (b != null) {
                if (b.item() != null) {
                    int n = g.player.count(b.item());
                    hud.itemIcon(ui, new rpg.item.ItemStack(b.item(), Math.max(1, n)), sx + 3, y + 3, S - 6, S - 6, n > 0 ? 1 : 0.3f);
                    if (n > 1 && b.item() != Items.COINS) ui.shadow(String.valueOf(n), sx + S - 4 - ui.width(String.valueOf(n), 11), y + S - 16, 11, Style.TEXT);
                } else if (b.blessing() != null) {
                    boolean lvl = g.player.skills.level(rpg.skill.Skill.FAITH) >= b.blessing().level;
                    ui.img("Faith_Icon", sx + 8, y + 6, S - 16, lvl ? 0xFFFFFFFF : 0x50FFFFFF);
                    ui.flatCentered(b.blessing().title.split(" ")[0], sx + S / 2, y + S - 14, 9, Style.DIM);
                    if (g.prayers.on(g.player, b.blessing())) ui.border(sx, y, S, S, 2, Style.FAITH);
                } else {
                    boolean lvl = g.player.skills.level(rpg.skill.Skill.MAGIC) >= b.spell().level;
                    ui.img("Magic_Icon", sx + 8, y + 6, S - 16, lvl ? 0xFFFFFFFF : 0x50FFFFFF);
                    ui.flatCentered(b.spell().title.split(" ")[0], sx + S / 2, y + S - 14, 9, Style.DIM);
                }
                if (g.player.cs.castCooldown > 0 && (b.spell() != null || b.item() != null && b.item().heal > 0)) ui.rect(sx, y, S, S * Math.min(1, g.player.cs.castCooldown / 1.2f), 0x60000000);
            }
            ui.text(i == 9 ? "0" : String.valueOf(i + 1), sx + 3, y + 1, 10, Style.FAINT);
            if (!live) continue;
            if (ui.hover(sx, y, S, S)) {
                ui.border(sx, y, S, S, 1, Style.EDGE_HI);
                if (b != null) hud.tooltip((b.item() != null ? b.item().name : b.blessing() != null ? b.blessing().title + " (prayer: " + b.blessing().effect + ")" : b.spell().title + " (spell)") + "\nKey " + (i == 9 ? 0 : i + 1) + "; right-click to clear");
                else hud.tooltip("Empty: drag an item here to bind it");
            }
            if (ui.clicked(sx, y, S, S)) use(i);
            if (ui.rclicked(sx, y, S, S) && b != null) { binds[i] = null; hud.sound("UIbutton2", 0.3f); }
            hud.named.put("bar:" + i, new float[]{sx + S / 2, y + S / 2});
            final int slot = i;
            hud.inventory.zone(sx, y, S, S, d -> { if (d.source == InventoryPanel.Source.PLAYER) { binds[slot] = Bind.of(d.stack.def); hud.sound("UI_select", 0.35f); } });
        }
    }
}
