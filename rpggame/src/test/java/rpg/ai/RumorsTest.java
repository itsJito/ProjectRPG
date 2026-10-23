package rpg.ai;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import astratto.util.Json;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import rpg.Ids;
import rpg.Rpg;
import rpg.actor.Appearance;
import rpg.world.Blocks;
import rpg.world.DevMap;

import static org.junit.jupiter.api.Assertions.*;

class RumorsTest {
    private Game game;
    private Rpg g;
    private Npc witness, listener, relay;

    @BeforeEach void open() {
        var config = new NativeEngine.Config(); config.deterministic = true;
        game = Game.create(config);
        game.activate(DevMap.build(game.engine()));
        g = game.add(new Rpg(game, "Rumor regression", new Appearance(), true));
        for (Npc n : g.npcs) g.ai.unload(n);
        witness = place(Ids.VILLAGER, 100.5f);
        listener = place(Ids.WORKER, 102.5f);
        relay = place(Ids.CARTER, 104.5f);
        g.teleport(g.player, 50.5f, DevMap.FLOOR, 80.5f);
        for (int x = 99; x <= 113; x++) for (int z = 69; z <= 71; z++)
            for (int y = (int) DevMap.FLOOR; y <= DevMap.FLOOR + 4; y++) g.setBlock(x, y, z, Blocks.AIR);
        assertTrue(game.tick(0));
    }

    private Npc place(long id, float x) {
        Npc n = (Npc) g.actor(id);
        g.ai.reload(n); g.teleport(n, x, DevMap.FLOOR, 70.5f);
        n.goal = Brain.Goal.IDLE; n.nextThink = Double.MAX_VALUE; n.memories.clear();
        return n;
    }

    @AfterEach void close() { if (game != null) game.close(); }

    private Memory crime(long event, double minute, String source, float confidence) {
        return new Memory("MURDER", g.player.id, Ids.BANDIT_1, DevMap.ALPHA, minute,
                confidence, source, event, 0, "a killing seen");
    }

    @Test void repeatingTheSameStoryDoesNotMultiplyFearOrEvents() {
        Memory m = crime(42, 10, "witnessed", 1);
        witness.remember(m, 24);
        g.ai.rumors.tell(witness, listener, m, false);
        float opinion = g.ai.rumors.opinion(listener);
        long shared = g.ai.shared;
        g.ai.rumors.tell(witness, listener, m, false);
        assertEquals(1, listener.memories.size());
        assertEquals(opinion, g.ai.rumors.opinion(listener));
        assertEquals(shared, g.ai.shared, "duplicate delivery must have no social side effects");
        assertFalse(listener.memories.get(0).witnessed());
        assertTrue(listener.memories.get(0).reported, "hearsay must never become a legal report");
    }

    @Test void aBetterSourceCanReplaceWeakHearsayWithoutAddingAnotherIncident() {
        Memory m = crime(42, 10, "witnessed", 1);
        witness.remember(m, 24);
        listener.remember(crime(42, 10, "rumor from far away", 0.343f), 24);
        assertSame(m, g.ai.rumors.hottest(witness, listener));
        g.ai.rumors.tell(witness, listener, m, false);
        assertEquals(1, listener.memories.size());
        assertEquals(0.7f, listener.memories.get(0).confidence, 0.0001f);
    }

    @Test void distinctLegacyIncidentsStillTravelThroughConversations() {
        Memory first = crime(0, 10, "witnessed", 1), second = crime(0, 20, "witnessed", 1);
        witness.remember(first, 24); witness.remember(second, 24);
        listener.remember(crime(0, 10, "rumor from elsewhere", 0.7f), 24);
        assertSame(second, g.ai.rumors.hottest(witness, listener));
        assertEquals(1, g.ai.share(witness, listener));
        assertEquals(2, listener.memories.size());
        assertEquals(0, g.ai.share(witness, listener));
    }

    @Test void eyewitnessEvidenceUpgradesRumorAndKeepsItsLegalReportState() {
        listener.remember(crime(42, 10, "rumor from elsewhere", 0.7f), 24);
        Memory seen = crime(42, 10, "witnessed", 1);
        listener.remember(seen, 24);
        assertEquals(1, listener.memories.size());
        assertSame(seen, listener.memories.get(0));
        assertFalse(seen.reported, "a new eyewitness must still be able to report");
        seen.reported = true;
        listener.remember(crime(42, 10, "witnessed", 1), 24);
        assertEquals(1, listener.memories.size());
        assertTrue(listener.memories.get(0).reported, "the same evidence must not reopen a report");
    }

    @Test void aWallMufflesAnAlarmAndAnOpeningLetsTheWarningThrough() {
        g.teleport(listener, 110.5f, DevMap.FLOOR, 70.5f);
        relay.memories.clear(); relay.loaded = false;
        for (int y = (int) DevMap.FLOOR; y <= DevMap.FLOOR + 4; y++) g.setBlock(106, y, 70, Blocks.STONE);
        assertTrue(game.tick(0));
        assertFalse(g.vox.clear(witness.x, witness.eyeY(), witness.z, listener.x, listener.eyeY(), listener.z));
        Memory m = crime(42, 10, "witnessed", 1);
        witness.remember(m, 24);
        g.ai.rumors.alarm(witness, m);
        assertTrue(listener.memories.isEmpty(), "a distant alarm behind a wall must not deliver the culprit's identity");
        for (int y = (int) DevMap.FLOOR; y <= DevMap.FLOOR + 4; y++) g.setBlock(106, y, 70, Blocks.AIR);
        assertTrue(game.tick(0));
        witness.lastAlarmAt = g.time - 7;
        g.ai.rumors.alarm(witness, m);
        assertEquals(1, listener.memories.size());
        assertFalse(listener.memories.get(0).witnessed());
    }

    @Test void distantAndUnloadedActorsCannotExchangeNews() {
        Memory m = crime(42, 10, "witnessed", 1);
        witness.remember(m, 24);
        g.teleport(listener, 120.5f, DevMap.FLOOR, 70.5f);
        g.ai.share(witness, listener);
        assertTrue(listener.memories.isEmpty());
        g.teleport(listener, 102.5f, DevMap.FLOOR, 70.5f);
        g.ai.unload(listener);
        g.ai.rumors.tell(witness, listener, m, true);
        assertTrue(listener.memories.isEmpty());
    }

    @Test void aRelayKeepsItsEyewitnessAndIncidentThroughJsonWithoutBecomingEvidence() {
        Memory m = crime(42, 10, "witnessed", 1);
        witness.remember(m, 24);
        g.ai.rumors.tell(witness, listener, m, false);
        g.ai.rumors.tell(listener, relay, listener.memories.get(0), false);
        Memory heard = relay.memories.get(0);
        @SuppressWarnings("unchecked")
        var saved = (java.util.Map<String, Object>) Json.parse(Json.write(heard.save()));
        Memory loaded = Memory.load(saved);
        assertTrue(loaded.sameIncident(m));
        assertEquals(witness.id, loaded.witness);
        assertEquals(2, loaded.relays);
        assertEquals(0.49f, loaded.confidence, 0.0001f);
        assertFalse(loaded.witnessed());
        assertTrue(loaded.reported);
        assertTrue(g.ai.rumors.line(relay, loaded).contains(witness.name));
        assertEquals(0, g.ai.share(relay, witness), "an account returning to its eyewitness adds no news");
    }

    @Test void anOldAnonymousAccountLoadsWithoutInventingAnEyewitnessOrCulprit() {
        Memory anonymous = new Memory("MURDER", 0, Ids.BANDIT_1, DevMap.ALPHA, 10,
                0.7f, "rumor from an old save", 42, 0, "an unidentified assailant");
        var saved = anonymous.save(); saved.remove("witness"); saved.remove("relays");
        Memory old = Memory.load(saved);
        witness.remember(old, 24);
        g.ai.rumors.tell(witness, listener, old, false);
        Memory heard = listener.memories.get(0);
        assertEquals(0, heard.subject);
        assertEquals(0, heard.witness);
        assertEquals(2, heard.relays);
        assertFalse(listener.knowsPlayer);
        assertEquals(0, g.ai.rumors.opinion(listener));
        assertTrue(g.ai.rumors.line(listener, heard).contains("someone"));
    }

    @Test void oneAlarmIsOverheardOnceEvenWhenItReachesSeveralPeople() {
        g.teleport(g.player, 101.5f, DevMap.FLOOR, 72.5f);
        Memory m = crime(42, 10, "witnessed", 1);
        witness.remember(m, 24);
        g.ai.rumors.alarm(witness, m);
        assertEquals(1, listener.memories.size());
        assertEquals(1, relay.memories.size());
        assertEquals(1, g.ai.rumors.overheard);
        assertEquals(1, g.chat.last(200).stream().filter(l -> l.text().startsWith("[Overheard]")).count());
    }

    @Test void actorsCanOnlyTellNewsTheyActuallyKnow() {
        g.ai.rumors.tell(witness, listener, crime(42, 10, "witnessed", 1), true);
        assertTrue(listener.memories.isEmpty());
        assertEquals(0, g.ai.shared);
    }

    @Test void aWitnessWhoDoesNotKnowThePlayersNameCannotTeachItToSomeoneElse() {
        witness.knowsPlayer = false;
        Memory m = crime(42, 10, "witnessed", 1); witness.remember(m, 24);
        g.ai.rumors.tell(witness, listener, m, false);
        assertFalse(listener.knowsPlayer);
        assertTrue(g.ai.rumors.line(listener, listener.memories.get(0)).contains("that stranger"));
    }

    @Test void goodDeedsUseTheSameConversationPath() {
        Memory deed = new Memory("DEED", g.player.id, witness.id, DevMap.ALPHA, 10,
                1, "witnessed", 0, 0, "helped with the harvest");
        witness.remember(deed, 24);
        assertEquals(1, g.ai.share(witness, listener));
        assertTrue(g.ai.rumors.opinion(listener) > 0);
        assertFalse(listener.memories.get(0).witnessed());
        assertEquals(0, g.ai.share(witness, listener));
    }

    @Test void hearingAnAccusationDoesNotCreateABountyButEyewitnessEvidenceStillCan() {
        Npc lawman = place(Ids.LAWMAN_A, 101.5f);
        Memory m = crime(42, 10, "witnessed", 1); witness.remember(m, 24);
        g.ai.rumors.tell(witness, listener, m, false);
        assertTrue(g.law.report(listener, listener.memories.get(0), lawman).rejected());
        assertEquals(0, g.law.bounty(g.player.id, DevMap.ALPHA));
        listener.remember(crime(42, 10, "witnessed", 1), 24);
        assertFalse(g.law.report(listener, listener.memories.get(0), lawman).rejected());
        assertTrue(g.law.bounty(g.player.id, DevMap.ALPHA) > 0);
    }
}
