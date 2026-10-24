package rpg.law;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import org.junit.jupiter.api.*;
import rpg.*;
import rpg.actor.*;
import rpg.ai.*;
import rpg.item.Items;
import rpg.world.*;
import static org.junit.jupiter.api.Assertions.*;

class CustodyTest {
    private Game game;
    private Rpg g;
    private Npc guard, lawman;
    @BeforeEach void open() {
        var cfg = new NativeEngine.Config(); cfg.deterministic = true;
        game = Game.create(cfg); game.activate(DevMap.build(game.engine()));
        g = game.add(new Rpg(game, "Custody", new Appearance(), true));
        guard = (Npc) g.actor(Ids.GUARD); lawman = (Npc) g.actor(Ids.LAWMAN_A);
        for (Npc npc : g.npcs) { npc.nextThink = Double.MAX_VALUE; npc.memories.clear(); }
        g.teleport(guard, 100.5f, DevMap.FLOOR, 70.5f);
        g.teleport(lawman, 101.5f, DevMap.FLOOR, 70.5f);
    }
    @AfterEach void close() { game.close(); }
    private Memory charge(long event) {
        Memory evidence = new Memory("ASSAULT", g.player.id, guard.id, DevMap.ALPHA, g.state.minutes, 1, "witnessed", event, 0, "assault");
        guard.remember(evidence, 24);
        assertTrue(g.law.report(guard, evidence, lawman).committed());
        guard.goal = Brain.Goal.FIGHT; guard.fightTarget = g.player.id;
        guard.cs.lastAttacker = g.player.id; guard.cs.lastHurt = g.time;
        guard.cs.phase = Actor.Combat.Phase.WINDUP;
        guard.hunt = new Hunt(g.player.id, "wanted", g.player.x, g.player.z, g.time, 60);
        return evidence;
    }
    @Test void paymentAlsoLandsThePayersAlreadyFlyingArrowAndGrantsSmallHonor() {
        charge(190); g.give(g.player, Items.COINS, 100); int before = g.honor.score;
        var arrow = new rpg.combat.CombatSystem.Projectile(); arrow.owner = g.player; arrow.x = g.player.x; arrow.y = g.player.eyeY(); arrow.z = g.player.z; arrow.ammo = new rpg.item.ItemStack(Items.BRONZE_ARROWS, 1);
        g.combat.projectiles.add(arrow);
        assertTrue(g.law.payBounty(g.player, lawman, g.state.newRequestId()).committed());
        assertTrue(g.combat.projectiles.isEmpty()); assertTrue(g.honor.score > before && g.honor.score <= before + 2);
    }
    @Test void paymentCancelsWindupAndPursuitWithoutErasingTheMemoryOrOtherJurisdictions() {
        Memory evidence = charge(91);
        g.law.all().computeIfAbsent(DevMap.BETA, k -> new java.util.HashMap<>()).put(g.player.id, 70);
        float opinion = g.ai.rumors.opinion(guard);
        g.give(g.player, Items.COINS, 200);
        assertTrue(g.law.payBounty(g.player, lawman, g.state.newRequestId()).committed());
        assertEquals(0, guard.fightTarget); assertNull(guard.hunt);
        assertEquals(Actor.Combat.Phase.IDLE, guard.cs.phase);
        assertTrue(evidence.settled); assertTrue(guard.memories.contains(evidence));
        assertEquals(opinion, g.ai.rumors.opinion(guard));
        assertEquals(70, g.law.bounty(g.player.id, DevMap.BETA));
        assertNull(g.ai.threatFor(guard));
        g.time += 0.1; charge(92);
        assertTrue(g.law.bounty(g.player.id, DevMap.ALPHA) > 0);
    }
    @Test void criminalRecordReactionsRequirePersonalKnowledgeAndChangeWithHonor() {
        Npc witness = (Npc) g.actor(Ids.VILLAGER), stranger = (Npc) g.actor(Ids.WORKER);
        witness.remember(new Memory("IMPRISONED", g.player.id, 0, DevMap.ALPHA, g.state.minutes, 1, "witnessed", 901, 3, "was taken to jail"), 24);
        g.honor.score = -40;
        assertTrue(g.ai.rumors.afraid(witness)); assertFalse(g.ai.rumors.afraid(stranger));
        assertTrue(g.ai.rumors.opinion(witness) < g.ai.rumors.opinion(stranger));
        g.honor.score = 40;
        assertFalse(g.ai.rumors.afraid(witness)); assertTrue(g.ai.rumors.opinion(witness) > 0);
    }

    @Test void npcPrisonersAreReleasedAndTheGateStaysLockedForAnyoneStillServingTime() {
        Npc thief = (Npc) g.actor(Ids.THIEF);
        g.law.arrest(thief, DevMap.ALPHA, guard);
        g.law.arrest(g.player, DevMap.ALPHA, guard);
        thief.arrestedUntil = g.state.minutes + 60;
        g.law.sleepThroughSentence();
        assertFalse(g.law.inCustody(g.player)); assertTrue(g.law.inCustody(thief));
        assertTrue(g.objects.jailDoor(DevMap.ALPHA).locked);
        g.state.minutes += 31; g.law.update();
        assertFalse(g.law.inCustody(thief)); assertFalse(g.objects.jailDoor(DevMap.ALPHA).locked);
        assertEquals(-1, thief.arrestedUntil);
    }

    @Test void arrestPersistsCustodyAndOnlyWitnessesLearnTheRecordThenSlabServesTheRemainingTime() {
        charge(101);
        Npc stranger = (Npc) g.actor(Ids.FARMER); g.ai.unload(stranger);
        g.law.arrest(g.player, DevMap.ALPHA, guard);
        assertTrue(g.law.inCustody(g.player)); assertEquals(1, g.law.jailAdmissions());
        assertNull(g.ai.threatFor(guard));
        assertTrue(g.objects.jailDoor(DevMap.ALPHA).locked);
        assertTrue(guard.memories.stream().anyMatch(m -> m.kind.equals("IMPRISONED")));
        assertFalse(stranger.memories.stream().anyMatch(m -> m.kind.equals("IMPRISONED")));
        var saved = g.law.save(); double remaining = g.law.sentenceRemaining();
        g.law.clear(); g.law.load(saved);
        assertEquals(remaining, g.law.sentenceRemaining()); assertEquals(1, g.law.jailAdmissions());
        var slab = g.objects.furniture.stream().filter(f -> f.cellSlab() && f.x > 100).findFirst().orElseThrow();
        g.teleport(g.player, slab.x - 1, DevMap.FLOOR, slab.z);
        assertTrue(slab.perform(g, g.player, "sleep", g.state.newRequestId()).committed());
        assertFalse(g.law.inCustody(g.player)); assertFalse(g.objects.jailDoor(DevMap.ALPHA).locked);
        assertEquals(1, g.law.jailAdmissions()); assertEquals(0, g.law.bounty(g.player.id, DevMap.ALPHA));
    }
}
