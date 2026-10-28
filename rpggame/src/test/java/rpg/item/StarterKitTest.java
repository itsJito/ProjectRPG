package rpg.item;

import static org.junit.jupiter.api.Assertions.*;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import org.junit.jupiter.api.Test;
import rpg.Rpg;
import rpg.actor.Appearance;
import rpg.world.DevMap;

/**
 * Gamefixes: a new profile starts with 25 gold, and its starting inventory is neat - everything packed (nothing left on
 * the ground for want of room), kinds kept together, coins last; Auto-Sort lays a jumbled pack out the same way.
 */
class StarterKitTest {
    @Test void aNewCharacterCarriesTheWholeKitTidilyWith25Gold() {
        var cfg = new NativeEngine.Config(); cfg.deterministic = true;
        try (Game game = Game.create(cfg)) {
            game.activate(DevMap.build(game.engine()));
            Rpg g = game.add(new Rpg(game, "Kit", new Appearance(), true));
            var p = g.player;
            assertEquals(25, p.count(Items.COINS), "25 gold to start");
            for (ItemDef d : new ItemDef[]{Items.SHORTBOW, Items.TINDERBOX, Items.FISHING_NET, Items.POT, Items.BUCKET, Items.BREAD, Items.SHRIMP})
                assertEquals(1, p.count(d), "the kit's " + d.name + " is carried");
            assertNotNull(p.eq.get(ItemDef.Slot.MAIN), "the sword is worn");
            assertTrue(g.objects.all().stream().noneMatch(o -> o instanceof rpg.objects.GroundItem gi && Math.hypot(gi.x - p.x, gi.z - p.z) < 3), "nothing of the kit was dropped for want of room");
            // tidy: read row by row, the kinds come in the shelf's order
            assertTidy(p.inv);
            // jumbled, then Auto-Sort: tidy again, nothing lost
            Inventory inv = p.inv;
            java.util.List<ItemStack> all = inv.stacks();
            int before = all.stream().mapToInt(s -> s.amount).sum();
            inv.clear();
            java.util.Collections.reverse(all);
            for (ItemStack s : all) assertTrue(inv.add(s));
            assertTrue(inv.sort());
            assertEquals(before, inv.stacks().stream().mapToInt(s -> s.amount).sum(), "sorting lost or made items");
            assertTidy(inv);
        }
    }

    private static void assertTidy(Inventory inv) {
        java.util.List<ItemStack> order = new java.util.ArrayList<>();
        java.util.Set<Integer> seen = new java.util.HashSet<>();
        for (int y = 0; y < inv.h; y++) for (int x = 0; x < inv.w; x++) { int e = inv.at(x, y); if (e >= 0 && seen.add(e)) order.add(inv.get(e)); }
        for (int i = 1; i < order.size(); i++)
            assertTrue(Inventory.TIDY.compare(order.get(i - 1), order.get(i)) <= 0 || order.get(i - 1).def.category() == order.get(i).def.category(),
                    "out of order: " + order.get(i - 1).def.name + " before " + order.get(i).def.name + " in " + order);
    }
}
