package rpg.ai;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import org.junit.jupiter.api.Test;
import rpg.Ids;
import rpg.Rpg;
import rpg.actor.Appearance;
import rpg.item.ItemStack;
import rpg.item.Items;
import rpg.law.Undertaker;
import rpg.objects.Container;
import rpg.world.DevMap;

import static org.junit.jupiter.api.Assertions.*;

class UndertakerFlowTest {
    @Test void anAvailableGuardCarriesASecuredGraveIntoCustody() {
        var config = new NativeEngine.Config(); config.deterministic = true;
        try (Game game = Game.create(config)) {
            game.activate(DevMap.build(game.engine()));
            Rpg g = game.add(new Rpg(game, "Grave transport", new Appearance(), true));
            Npc guard = (Npc) g.actor(Ids.GUARD);
            for (Npc n : g.npcs) if (n != guard) g.ai.unload(n);
            g.teleport(g.player, 116.5f, DevMap.FLOOR, 88.6f);
            g.teleport(guard, 106.5f, DevMap.FLOOR, 74.5f);
            var grave = g.objects.add(new Container(900001, "grave", "Secured grave", "grave", 100.5f, DevMap.FLOOR, 74.5f, 40, g.player.id));
            grave.secured = true; grave.expiresAt = g.time + Undertaker.GRAVE_SECONDS - 130;
            grave.contents.add(new ItemStack(Items.BREAD, 1));
            boolean carried = false;
            for (int i = 0; i < 3600 && !grave.buried; i++) {
                assertTrue(game.tick(1f / 60));
                carried |= grave.heldBy == guard.id;
            }
            assertTrue(carried && grave.buried && !grave.removed && g.undertaker.held() > 0,
                    () -> guard.goal + " " + guard.goalReason + " at " + guard.x + "," + guard.z + " held=" + grave.heldBy + " " + guard.lastOutcome);
            assertEquals(g.player.id, grave.owner);
        }
    }
}
