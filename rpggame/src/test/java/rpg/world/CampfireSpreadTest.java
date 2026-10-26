package rpg.world;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import org.junit.jupiter.api.Test;
import rpg.Rpg;
import rpg.actor.Appearance;
import rpg.item.ItemStack;
import rpg.item.Items;
import static org.junit.jupiter.api.Assertions.*;

/** A campfire lit on the grass warms its surroundings; it does not set the whole meadow, the herb beds and passers-by alight. */
class CampfireSpreadTest {
    private static int bare(Rpg g) { int k = 0; for (int x = 40; x < 100; x++) for (int z = 5; z < 60; z++) if (g.vox.block(x, DevMap.FLOOR - 1, z) == Blocks.BARE_PATCH) k++; return k; }

    @Test void aCampfireOnGrassStaysACampfire() {
        var config = new NativeEngine.Config(); config.deterministic = true;
        try (Game game = Game.create(config)) {
            game.activate(DevMap.build(game.engine()));
            Rpg g = game.add(new Rpg(game, "Campfire", new Appearance(), true));
            for (var n : g.npcs) g.ai.unload(n);
            int bareBefore = bare(g);
            g.teleport(g.player, 70.5f, DevMap.FLOOR, 30.5f);
            g.player.store(new ItemStack(Items.LOGS, 1));
            for (int i = 0; i < 30; i++) game.tick(1f / 60);
            int slot = g.player.inv.first(Items.LOGS);
            g.items.perform(g.player, slot, "light");
            for (int i = 0; i < 60 * 8 && g.objects.all().stream().noneMatch(o -> o instanceof rpg.objects.Fire); i++) game.tick(1f / 60);
            assertTrue(g.objects.all().stream().anyMatch(o -> o instanceof rpg.objects.Fire), "the fire was lit");
            g.teleport(g.player, 60.5f, DevMap.FLOOR, 50.5f);
            int plantsBefore = 0;
            for (var c : g.foliage.chunks.values()) for (var p : c.plants) if (!p.removed) plantsBefore++;
            for (int i = 0; i < 60 * 60; i++) game.tick(1f / 60);
            int burnt = bare(g) - bareBefore;
            int plantsAfter = 0;
            for (var c : g.foliage.chunks.values()) for (var p : c.plants) if (!p.removed) plantsAfter++;
            long herbs = g.objects.nodes.stream().filter(n -> (n.def.equals("herb_patch") || n.def.equals("flax")) && n.depleted()).count();
            System.out.println("campfire: grass burnt " + burnt + ", plants " + plantsBefore + " -> " + plantsAfter + ", patches burnt " + herbs);
            assertEquals(0, herbs, "the herb and flax beds three metres off are untouched");
            assertTrue(burnt < 6, "the fire stays round its pit: " + burnt + " turf cells burnt");
        }
    }
}
