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
 * Gamefixes: lawmen did not arrest - they should take in the wanted as the guards do, and kill only when they must. A
 * lawman alone with a wanted player: he closes in and tells them to halt; resisted and fought down, he takes them in
 * rather than killing them.
 */
class LawmanArrestTest {
    private Game game;
    private Rpg g;
    private Npc bram;
    @BeforeEach void open() {
        var cfg = new NativeEngine.Config(); cfg.deterministic = true;
        game = Game.create(cfg); game.activate(DevMap.build(game.engine()));
        g = game.add(new Rpg(game, "Lawman", new Appearance(), true));
        bram = (Npc) g.actor(Ids.LAWMAN_A);
        for (Npc npc : g.npcs) { npc.memories.clear(); if (npc != bram) npc.nextThink = Double.MAX_VALUE; }
        g.state.minutes = 600;
        g.teleport(bram, 100.5f, DevMap.FLOOR, 70.5f);
        g.teleport(g.player, 106.5f, DevMap.FLOOR, 70.5f);
        game.tick(0);
        Memory evidence = new Memory("ASSAULT", g.player.id, bram.id, DevMap.ALPHA, g.state.minutes, 1, "witnessed", 77, 0, "assault");
        bram.remember(evidence, 24);
        assertTrue(g.law.report(bram, evidence, bram).committed(), "the lawman takes his own report");
        bram.nextThink = 0;
    }
    @AfterEach void close() { game.close(); }
    private void frames(int n) { for (int i = 0; i < n; i++) assertTrue(game.tick(1f / 60)); }

    @Test void aLawmanHaltsTheWantedAndTakesThemInWhenTheyResist() {
        boolean halted = false;
        for (int i = 0; i < 60 * 20 && !halted; i++) {
            frames(1);
            halted = bram.lastOutcome != null && bram.lastOutcome.contains("halt");
        }
        System.out.println("bram: goal " + bram.goal + " / " + bram.lastOutcome + " / " + bram.scores);
        assertTrue(halted, "the lawman orders the wanted player to halt (goal " + bram.goal + ", " + bram.lastOutcome + ")");
        // the player resists: he fights - and beaten down, they are arrested, not killed
        g.law.resist(g.player, bram);
        g.player.hp = 3;
        for (int i = 0; i < 60 * 30 && !g.law.inCustody(g.player) && g.player.alive; i++) frames(1);
        System.out.println("bram after resisting: goal " + bram.goal + " / " + bram.lastOutcome + "; player alive " + g.player.alive + " hp " + g.player.hp);
        assertTrue(g.player.alive, "killed by the lawman instead of arrested");
        assertTrue(g.law.inCustody(g.player), "the lawman takes the resisting player in");
    }
}
