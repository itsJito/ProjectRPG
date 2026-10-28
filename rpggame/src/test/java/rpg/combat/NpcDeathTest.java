package rpg.combat;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import org.junit.jupiter.api.Test;
import rpg.Ids;
import rpg.Rpg;
import rpg.actor.Appearance;
import rpg.ai.Npc;
import rpg.item.ItemDef;
import rpg.world.DevMap;

import static astratto.bridge.AstrattoNative.*;
import static org.junit.jupiter.api.Assertions.*;

class NpcDeathTest {
    @Test
    void allFourGameKillPathsKeepTheSameBodyDeadWithoutBalanceRetries() {
        NativeEngine.Config config = new NativeEngine.Config();
        config.deterministic = true;
        try (Game game = Game.create(config)) {
            game.activate(DevMap.build(game.engine()));
            Rpg g = game.add(new Rpg(game, "Death regression", new Appearance(), true));
            for (Npc other : g.npcs) g.ai.unload(other);
            assertTrue(game.tick(0));
            Npc npc = (Npc) g.actor(Ids.BANDIT_1);
            for (int way = 0; way < 4; way++) {
                g.ai.revive(npc, true);
                g.teleport(npc, 102.5f, DevMap.FLOOR, 74.5f);
                npc.hostileToPlayer = false;
                npc.nextThink = Double.MAX_VALUE;
                // Supply an actual native standing pose, without requiring a renderer to draw it first.
                long pose = game.reserve(KIND_RAGDOLL);
                game.commands().ragdollCreate(pose, 1, SYDNEY_HUMAN, RAGDOLL_STAND,
                        npc.x, npc.y, npc.z, npc.yaw, 0, 0, 0);
                assertTrue(game.tick(0));
                npc.posePts = g.lastBodies.ragdoll(pose).p.clone();
                npc.poseAt = g.time;
                game.commands().release(pose);

                long reactingBody = 0;
                if (way >= 2) {
                    g.reactions.struck(npc, 0, 0.1f, 1, 160, 60, npc.x, npc.y + 1.3f, npc.z);
                    assertTrue(npc.reacting, "the pre-kill hit must exercise the actual reaction system");
                    reactingBody = npc.ragdoll;
                    for (int tick = 0; tick < 24; tick++) assertTrue(game.tick(1f / 60));
                }
                if (way == 3) {
                    npc.hp = 0.5f;
                    g.gore.of(npc).bleed = 2;
                    for (int tick = 0; tick < 600 && npc.alive; tick++) assertTrue(game.tick(1f / 60));
                } else {
                    // (damage is rolled up to the blow's maximum: blows land until one is the killing one)
                    for (int blow = 0; blow < 8 && npc.alive; blow++)
                        g.combat.resolve(g.player, npc, npc.maxHp() * 3, way == 1 ? 400 : 0,
                                ItemDef.DamageType.BLUNT, null, npc.x, npc.y + 1, npc.z, "death regression", false);
                }
                assertFalse(npc.alive, "kill path " + way + " must actually kill its target");
                assertNotEquals(0, npc.ragdoll);
                if (reactingBody != 0) assertEquals(reactingBody, npc.ragdoll, "death must reuse the reacting body");
                long corpse = npc.ragdoll;
                long recovered = g.reactions.recovered;
                for (int tick = 0; tick < 720; tick++) {
                    assertTrue(game.tick(1f / 60));
                    var body = g.lastBodies.ragdoll(corpse);
                    assertNotNull(body);
                    assertEquals(SYDNEY_DEAD, body.state, "a dead NPC may never return to balance or get-up");
                    assertNotEquals(OUTCOME_RECOVERED, body.outcome);
                    if (tick >= 120) {
                        assertEquals(OUTCOME_DEAD, body.outcome);
                        assertFalse(body.active);
                        assertTrue(body.p[RD_HEAD * 3 + 1] <= DevMap.FLOOR + 0.9f,
                                "the head must remain down after the fall");
                    }
                    for (float position : body.p) assertTrue(Float.isFinite(position));
                }
                assertFalse(npc.reacting);
                assertEquals(recovered, g.reactions.recovered);
                assertEquals(0, g.ai.deathsReasserted, "death must work without the game's corrective DIE retries");
            }
        }
    }
}
