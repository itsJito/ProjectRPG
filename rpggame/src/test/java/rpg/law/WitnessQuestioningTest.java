package rpg.law;

import static org.junit.jupiter.api.Assertions.*;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import org.junit.jupiter.api.*;
import rpg.*;
import rpg.actor.Appearance;
import rpg.ai.*;
import rpg.world.DevMap;

/**
 * Gamefixes: searching for a wanted suspect, the watch asks people whether they have seen them, and the answer is what
 * that person actually remembers - a newer sighting moves the search to it; someone who saw nothing says so.
 */
class WitnessQuestioningTest {
    private Game game;
    private Rpg g;
    private Npc guard, villager;
    @BeforeEach void open() {
        var cfg = new NativeEngine.Config(); cfg.deterministic = true;
        game = Game.create(cfg); game.activate(DevMap.build(game.engine()));
        g = game.add(new Rpg(game, "Witness", new Appearance(), true));
        guard = (Npc) g.actor(Ids.GUARD); villager = (Npc) g.actor(Ids.VILLAGER);
        for (Npc n : g.npcs) { n.memories.clear(); if (n != guard) n.nextThink = Double.MAX_VALUE; }
        g.state.minutes = 600;
        g.teleport(g.player, 20.5f, DevMap.FLOOR, 20.5f);          // far away, out of the guard's sight
        g.teleport(guard, 100.5f, DevMap.FLOOR, 70.5f);
        g.teleport(villager, 104.5f, DevMap.FLOOR, 70.5f);
        game.tick(0);
    }
    @AfterEach void close() { game.close(); }
    private void frames(int n) { for (int i = 0; i < n; i++) assertTrue(game.tick(1f / 60)); }

    private void search() {
        guard.hunt = new Hunt(g.player.id, "wanted", 101.5f, 72.5f, g.time - 120, 300);
        guard.hunt.reachedLkp = true;
        guard.goal = Brain.Goal.SEARCH; guard.nextThink = 0;
    }

    @Test void aNewerSightingMovesTheSearch() {
        villager.playerSighting = new Npc.Sighting(villager.id, 88.5f, 60.5f, 0, 0, g.time - 20);
        search();
        for (int i = 0; i < 60 * 12 && guard.questioned.isEmpty(); i++) frames(1);
        System.out.println("guard: " + guard.lastOutcome);
        assertTrue(guard.questioned.containsKey(villager.id), "the guard asks the villager: " + guard.lastOutcome);
        assertEquals(88.5f, guard.hunt.x, 0.01f, "the search moves to where the villager saw them");
        assertEquals(60.5f, guard.hunt.z, 0.01f);
    }

    @Test void someoneWhoSawNothingSaysSoAndTheSearchStays() {
        search();
        for (int i = 0; i < 60 * 12 && guard.questioned.isEmpty(); i++) frames(1);
        assertTrue(guard.questioned.containsKey(villager.id), "the guard asks the villager: " + guard.lastOutcome);
        assertEquals(101.5f, guard.hunt.x, 0.01f, "nothing new: the search stays where it was");
        assertTrue(guard.lastOutcome.contains("not seen"), guard.lastOutcome);
    }
}
