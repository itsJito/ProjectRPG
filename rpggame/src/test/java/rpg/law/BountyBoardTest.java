package rpg.law;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import org.junit.jupiter.api.*;
import rpg.*;
import rpg.actor.Appearance;
import rpg.ai.*;
import rpg.item.*;
import rpg.world.DevMap;
import static org.junit.jupiter.api.Assertions.*;

class BountyBoardTest {
    private Game game; private Rpg g; private Npc thief, witness, issuer;
    @BeforeEach void open() {
        var cfg = new NativeEngine.Config(); cfg.deterministic = true;
        game = Game.create(cfg); game.activate(DevMap.build(game.engine())); g = game.add(new Rpg(game, "Warrants", new Appearance(), true));
        thief = (Npc) g.actor(Ids.THIEF); witness = (Npc) g.actor(Ids.GUARD); issuer = (Npc) g.actor(Ids.LAWMAN_A);
        for (Npc npc : g.npcs) npc.nextThink = Double.MAX_VALUE;
        g.teleport(issuer, 100.5f, DevMap.FLOOR, 70.5f); g.teleport(witness, 101.5f, DevMap.FLOOR, 70.5f); g.teleport(g.player, 100.5f, DevMap.FLOOR, 71.5f);
        g.state.commit("CrimeCommitted", thief.id, witness.id, DevMap.ALPHA, 0, "theft");
        Memory memory = new Memory("THEFT", thief.id, witness.id, DevMap.ALPHA, g.state.minutes, 1, "witnessed", 900009, 100, "stolen goods"); witness.remember(memory, 24);
        assertTrue(g.law.report(witness, memory, issuer).committed());
    }
    @AfterEach void close() { game.close(); }
    @Test void aReportedNpcCrimeIssuesAPhysicalScrollWithKnowledgeBasedMapCoordinates() {
        var known = new Stealth.Aware(); known.x = 97; known.z = 73; known.seenAt = 1; witness.aware.put(thief.id, known);
        assertTrue(g.bountyBoard.accept(thief, DevMap.ALPHA).committed());
        ItemStack scroll = g.player.inv.stacks().stream().filter(s -> s.def == Items.BOUNTY_SCROLL).findFirst().orElseThrow();
        assertEquals(1, scroll.def.gw); assertEquals(2, scroll.def.gh); assertEquals(97, scroll.contract.x());
        assertEquals(scroll.contract, ItemIO.stack(ItemIO.stack(scroll)).contract);
        assertTrue(g.bountyBoard.accept(thief, DevMap.ALPHA).rejected());
    }
    @Test void theWatchPaysOnceFromRealFundsAndCannotRedeemADuplicateScroll() {
        assertTrue(g.bountyBoard.accept(thief, DevMap.ALPHA).committed());
        ItemStack scroll = g.player.inv.stacks().stream().filter(s -> s.contract != null).findFirst().orElseThrow(); ItemStack duplicate = scroll.copyWhole();
        int reward = scroll.contract.reward(), before = g.player.count(Items.COINS), treasury = issuer.count(Items.COINS);
        thief.alive = false; g.state.commit("ActorKilled", g.player.id, thief.id, DevMap.ALPHA, 0, "warrant");
        assertTrue(g.bountyBoard.claim(DevMap.ALPHA).committed());
        assertEquals(before + reward, g.player.count(Items.COINS)); assertEquals(treasury - reward, issuer.count(Items.COINS));
        g.player.store(duplicate); assertTrue(g.bountyBoard.claim(DevMap.ALPHA).rejected());
    }
}
