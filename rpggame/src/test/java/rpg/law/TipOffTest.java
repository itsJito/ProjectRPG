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
 * Gamefixes: a good NPC who sees the player and knows they are wanted reports where they are to the watch nearby. The
 * townsperson walks to an officer and tells him; the officer searches where the player was seen. A crook does not.
 */
class TipOffTest {
    private Game game;
    private Rpg g;
    @BeforeEach void open() {
        var cfg = new NativeEngine.Config(); cfg.deterministic = true;
        game = Game.create(cfg); game.activate(DevMap.build(game.engine()));
        g = game.add(new Rpg(game, "Tip", new Appearance(), true));
        for (Npc npc : g.npcs) npc.memories.clear();
        g.state.minutes = 600;
    }
    @AfterEach void close() { game.close(); }
    private void frames(int n) { for (int i = 0; i < n; i++) assertTrue(game.tick(1f / 60)); }

    @Test void aTownspersonTellsTheWatchWhereTheWantedPlayerIs() {
        Npc bram = (Npc) g.actor(Ids.LAWMAN_A);
        Npc seer = g.npcs.stream().filter(n -> n.role == Npc.Role.VILLAGER && DevMap.ALPHA.equals(n.homeJurisdiction)).findFirst().orElseThrow();
        Memory m = new Memory("THEFT", g.player.id, seer.id, DevMap.ALPHA, g.state.minutes, 1f, "witnessed", 55, 80, "theft");
        seer.remember(m, 24);
        g.teleport(seer, bram.x + 1, DevMap.FLOOR, bram.z);
        assertTrue(g.law.report(seer, m, bram).committed());
        assertEquals(40, g.law.bounty(g.player.id, DevMap.ALPHA));
        // a second villager who has only heard of it, out by the houses; the player walks past them in the open
        Npc teller = g.npcs.stream().filter(n -> n != seer && n.lawAbiding() && !n.combatant() && DevMap.ALPHA.equals(n.homeJurisdiction)).findFirst().orElseThrow();
        teller.remember(new Memory("THEFT", g.player.id, seer.id, DevMap.ALPHA, g.state.minutes, 0.6f, "rumor", 55, 80, "heard of a theft"), 24);
        g.teleport(teller, 98.5f, DevMap.FLOOR, 84.5f);
        g.teleport(g.player, 98.5f, DevMap.FLOOR, 76.5f);
        frames(90);
        assertNotNull(teller.tip == null ? teller.playerSighting : teller.tip, "the townsperson saw the player");
        // the player slips off out of town; the watch hears of it only through the townsperson
        g.teleport(g.player, 60.5f, DevMap.FLOOR, 30.5f);
        boolean told = false;
        for (int s = 0; s < 60 && !told; s++) { frames(60); told = g.state.all().stream().anyMatch(e -> e.type().equals("WantedReported") && e.actor() == teller.id); }
        assertTrue(told, "the townsperson told the watch (" + teller.goal + ", " + teller.lastOutcome + ", scores " + teller.scores + ")");
        boolean searching = g.npcs.stream().anyMatch(n -> n.watch() && n.hunt != null && n.hunt.target == g.player.id && Math.hypot(n.hunt.x - 98.5f, n.hunt.z - 76.5f) < 6);
        assertTrue(searching, "an officer searches where the player was seen");
    }

    @Test void aCrookKeepsQuiet() {
        Npc crook = g.npcs.stream().filter(n -> n.role == Npc.Role.THIEF).findFirst().orElse(null);
        Assumptions.assumeTrue(crook != null);
        assertFalse(crook.lawAbiding());
    }
}
