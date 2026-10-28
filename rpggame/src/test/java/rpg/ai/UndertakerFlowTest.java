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

    /** Gamefixes: the undertaker offers, while a grave still stands, to send for it and sell its contents back. */
    @Test void theUndertakerSellsBackWhatLiesInAStandingGrave() {
        var config = new NativeEngine.Config(); config.deterministic = true;
        try (Game game = Game.create(config)) {
            game.activate(DevMap.build(game.engine()));
            Rpg g = game.add(new Rpg(game, "Buy back", new Appearance(), true));
            Npc morrow = (Npc) g.actor(Ids.UNDERTAKER);
            g.teleport(g.player, morrow.x + 1, DevMap.FLOOR, morrow.z);
            assertTrue(morrow.options(g, g.player).stream().noneMatch(o -> o.id().equals("buyback") || o.id().equals("reclaim")), "nothing to get back: nothing offered");
            var grave = g.objects.add(new Container(900002, "grave", "Your grave", "grave", 20.5f, DevMap.FLOOR, 30.5f, 40, g.player.id));
            grave.expiresAt = g.time + Undertaker.GRAVE_SECONDS;
            grave.contents.add(new ItemStack(Items.get("iron_sword"), 1));
            int fee = g.undertaker.buyBackFee(), purse = g.player.count(Items.COINS);   // (a new character starts with 25 gold)
            assertEquals(Undertaker.FEE + Undertaker.OUTSIDE_SURCHARGE + Undertaker.PROMPT, fee, "outside the walls, at once");
            assertTrue(morrow.options(g, g.player).stream().anyMatch(o -> o.id().equals("buyback")), "a standing grave: buy-back offered");
            assertTrue(g.undertaker.buyBack(g.player, morrow).rejected(), "no coins, no service");
            assertFalse(grave.removed, "and the grave is left as it was");
            g.give(g.player, Items.COINS, fee);
            assertTrue(g.undertaker.buyBack(g.player, morrow).committed());
            assertTrue(grave.removed, "the grave is emptied");
            assertTrue(g.player.has(Items.get("iron_sword"), 1), "and its contents are back in hand");
            assertEquals(purse, g.player.count(Items.COINS), "for the fee");
        }
    }
}
