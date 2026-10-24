package rpg.law;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import rpg.Ids;
import rpg.Rpg;
import rpg.actor.Actor;
import rpg.actor.Appearance;
import rpg.ai.Brain;
import rpg.ai.Hunt;
import rpg.ai.Investigation;
import rpg.ai.Memory;
import rpg.ai.Npc;
import rpg.world.DevMap;

import static org.junit.jupiter.api.Assertions.*;

class RespawnJusticeTest {
    private Game game;
    private Rpg g;
    private Npc witness, lawman;

    @BeforeEach void open() {
        var config = new NativeEngine.Config(); config.deterministic = true;
        game = Game.create(config);
        game.activate(DevMap.build(game.engine()));
        g = game.add(new Rpg(game, "Justice regression", new Appearance(), true));
        for (Npc n : g.npcs) { n.nextThink = Double.MAX_VALUE; n.memories.clear(); }
        witness = (Npc) g.actor(Ids.VILLAGER); lawman = (Npc) g.actor(Ids.LAWMAN_A);
        g.teleport(witness, 100.5f, DevMap.FLOOR, 70.5f);
        g.teleport(lawman, 101.5f, DevMap.FLOOR, 70.5f);
    }

    @AfterEach void close() { if (game != null) game.close(); }

    private Memory crime(long event, long offender, String jurisdiction) {
        Memory m = new Memory("ASSAULT", offender, witness.id, jurisdiction, g.state.minutes,
                1, "witnessed", event, 0, "an unprovoked attack");
        witness.remember(m, 24);
        return m;
    }

    @Test void respawnClearsEveryPlayerBountyButKeepsOtherOffendersAndHonor() {
        assertTrue(g.law.report(witness, crime(41, g.player.id, DevMap.ALPHA), lawman).committed());
        Npc beta = (Npc) g.actor(Ids.LAWMAN_B);
        g.teleport(beta, witness.x + 1, DevMap.FLOOR, witness.z);
        assertTrue(g.law.report(witness, crime(42, g.player.id, DevMap.BETA), beta).committed());
        assertTrue(g.law.report(witness, crime(43, Ids.THIEF, DevMap.ALPHA), lawman).committed());
        int other = g.law.bounty(Ids.THIEF, DevMap.ALPHA);
        g.honor.score = -40;
        g.respawn();
        assertFalse(g.law.wantedAnywhere(g.player.id));
        assertEquals(other, g.law.bounty(Ids.THIEF, DevMap.ALPHA));
        assertEquals(-40, g.honor.score);
    }

    @Test void pendingAndSavedOldEvidenceCannotReopenJusticeAfterDeath() {
        Memory old = crime(41, g.player.id, DevMap.ALPHA);
        float opinion = g.ai.rumors.opinion(witness);
        g.respawn();
        assertTrue(g.law.report(witness, old, lawman).rejected());
        Memory restored = Memory.load(old.save());
        restored.reported = false;
        assertTrue(g.law.report(witness, restored, lawman).rejected(), "legal closure survives serialization independently of report delivery");
        assertSame(old, witness.crimeKnown(g.player.id));
        assertEquals(opinion, g.ai.rumors.opinion(witness), "a grudge is not a new warrant");
        assertFalse(g.ai.rumors.afraid(witness), "old settled violence must not restart flight after respawn");
    }

    @Test void respawnStopsPlayerPursuitsWithoutErasingUnrelatedFights() {
        witness.fightTarget = g.player.id; witness.cs.lastAttacker = g.player.id;
        witness.goal = Brain.Goal.FIGHT;
        witness.cs.phase = Actor.Combat.Phase.WINDUP;
        witness.hunt = new Hunt(g.player.id, "old pursuit", 10, 10, g.time, 60);
        witness.fearOf = g.player.id;
        witness.investigation = new Investigation(g.player.id, 10, 10, Investigation.Stimulus.SOUND, g.time);
        lawman.fightTarget = Ids.BANDIT_1;
        g.respawn();
        assertEquals(0, witness.fightTarget); assertEquals(0, witness.cs.lastAttacker);
        assertEquals(0, witness.fearOf); assertNull(witness.hunt); assertNull(witness.investigation);
        assertNotEquals(Brain.Goal.FIGHT, witness.goal);
        assertEquals(Actor.Combat.Phase.IDLE, witness.cs.phase);
        assertEquals(Ids.BANDIT_1, lawman.fightTarget);
    }

    @Test void aNewOffenseAfterRespawnStillCreatesABounty() {
        crime(41, g.player.id, DevMap.ALPHA);
        g.respawn();
        Memory fresh = crime(42, g.player.id, DevMap.ALPHA);
        assertTrue(g.law.report(witness, fresh, lawman).committed());
        assertTrue(g.law.bounty(g.player.id, DevMap.ALPHA) > 0);
        assertTrue(g.ai.rumors.afraid(witness));
    }

    @Test void aClosedAccountStaysClosedWhenRetoldOrUpgradedAfterRespawn() {
        Memory old = crime(41, g.player.id, DevMap.ALPHA);
        g.respawn();
        Memory heard = old.rumorFrom(witness);
        lawman.remember(heard, 24);
        assertTrue(heard.settled);
        Memory freshCopy = new Memory(old.kind, old.subject, old.victim, old.jurisdiction, old.minutes,
                1, "witnessed", old.eventId, 0, old.detail);
        lawman.remember(freshCopy, 24);
        Memory canonical = lawman.crimeKnown(g.player.id);
        assertTrue(canonical.settled);
        assertTrue(g.law.report(lawman, canonical, lawman).rejected());
    }

    @Test void shopkeepersDistinguishSettledGossipFromTheirOwnGrievance() {
        Npc merchant = (Npc) g.actor(Ids.MERCHANT_A);
        Memory gossip = crime(41, g.player.id, DevMap.ALPHA).rumorFrom(witness);
        merchant.remember(gossip, 24);
        assertNotNull(g.econ.tradeRefusal(merchant, g.player));
        g.respawn();
        assertNull(g.econ.tradeRefusal(merchant, g.player));
        Memory grievance = new Memory("ASSAULT", g.player.id, merchant.id, DevMap.ALPHA, g.state.minutes,
                1, "witnessed", 42, 0, "attacked me");
        merchant.remember(grievance, 24);
        g.respawn();
        assertNotNull(g.econ.tradeRefusal(merchant, g.player));
        merchant.remember(new Memory("THEFT", g.player.id, witness.id, DevMap.ALPHA, g.state.minutes,
                0.3f, "rumor from elsewhere", 43, 0, "new weak gossip"), 24);
        assertNotNull(g.econ.tradeRefusal(merchant, g.player), "new weak gossip must not mask a personal grudge");
    }
}
