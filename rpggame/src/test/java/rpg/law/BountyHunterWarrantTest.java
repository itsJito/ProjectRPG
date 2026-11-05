package rpg.law;

import static org.junit.jupiter.api.Assertions.*;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import org.junit.jupiter.api.Test;
import rpg.Rpg;
import rpg.actor.Appearance;
import rpg.ai.Npc;
import rpg.item.Items;
import rpg.world.DevMap;

/** Alpha 0.3.1: bounty hunters form well away from their quarry and carry the signed warrant that is their authority. */
class BountyHunterWarrantTest {
    @Test void huntersFormFarOffCarryingTheWarrantAndGiveUpWithoutIt() {
        var config = new NativeEngine.Config(); config.deterministic = true;
        try (Game game = Game.create(config)) {
            game.activate(DevMap.build(game.engine()));
            Rpg g = game.add(new Rpg(game, "Warrants", new Appearance(), true));
            g.teleport(g.player, 64.5f, DevMap.FLOOR, 64.5f);
            g.controller.snapTo(0);
            game.tick(0);
            g.law.all().computeIfAbsent(DevMap.ALPHA, k -> new java.util.HashMap<>()).put(g.player.id, 450);
            assertTrue(g.law.posses.form(DevMap.ALPHA, 450, false));
            for (Npc hunter : g.law.posses.members()) {
                float d = hunter.distTo(g.player);
                assertTrue(d >= 38, "a hunter formed " + d + " m away");
                var w = g.law.posses.warrantOf(hunter);
                assertNotNull(w, "every hunter carries the warrant");
                assertEquals(g.player.id, w.contract.target());
                assertEquals(450, w.contract.reward(), "the bounty is written on it");
                assertEquals(DevMap.ALPHA, w.contract.jurisdiction());
            }
            // a warrant lifted from a hunter's pack: no authority, no hunt - out of sight, they go home
            Npc hunter = g.law.posses.members().get(0);
            for (var grid : hunter.grids()) for (int i = 0; i < grid.size(); i++) if (grid.get(i) != null && grid.get(i).def == Items.BOUNTY_SCROLL) grid.set(i, null);
            assertNull(g.law.posses.warrantOf(hunter));
            for (int i = 0; i < 60 * 3; i++) game.tick(1f / 60);
            assertTrue(g.state.all().stream().anyMatch(e -> e.type().equals("WarrantLost") && e.actor() == hunter.id), "gave up the hunt");
        }
    }
}
