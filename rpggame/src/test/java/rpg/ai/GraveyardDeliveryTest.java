package rpg.ai;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import org.junit.jupiter.api.Test;
import rpg.Ids;
import rpg.Rpg;
import rpg.actor.Appearance;
import rpg.world.DevMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * gamefixes: a guard who has carried a body to the graveyard is done with it - neither he nor anyone else takes it up
 * as a fresh murder scene, and nobody goes back to collect it again.
 */
class GraveyardDeliveryTest {
    @Test void aDeliveredBodyIsNotInvestigatedAgain() {
        var config = new NativeEngine.Config(); config.deterministic = true;
        try (Game game = Game.create(config)) {
            game.activate(DevMap.build(game.engine()));
            Rpg g = game.add(new Rpg(game, "Graveyard delivery", new Appearance(), true));
            g.state.minutes = 10 * 60;
            g.teleport(g.player, 30, DevMap.FLOOR, 30);
            g.player.skills.setLevel(rpg.skill.Skill.HEALTH, 100); g.player.restore();
            Npc victim = (Npc) g.actor(Ids.BEGGAR);
            g.teleport(victim, 100, DevMap.FLOOR, 78);
            g.combat.bleedOutNow(victim);
            double buriedAt = -1;
            int collectsAfter = 0, investigationsAfter = 0, collecting = 0;
            StringBuilder log = new StringBuilder();
            for (int f = 0; f < 60 * 60 * 8; f++) {
                assertTrue(game.tick(1f / 60));
                if (buriedAt < 0 && victim.corpse == 0 && victim.ragdoll == 0) { buriedAt = g.time; log.append(String.format("buried at %.0f s%n", g.time)); }
                if (f % 30 != 0) continue;
                for (Npc n : g.npcs) {
                    if (!n.alive || n == victim) continue;
                    if (n.goal == Brain.Goal.COLLECT) collecting++;
                    if (buriedAt < 0) continue;
                    // (another death in the town over these minutes is another body to fetch: only this one's grave counts)
                    if (n.goal == Brain.Goal.COLLECT && n.goalReason != null && n.goalReason.contains(String.format("(%.0f, %.0f)", 100f, 78f))) collectsAfter++;
                    if (n.hunt != null && n.hunt.source == victim.id) { investigationsAfter++; if (investigationsAfter < 5) log.append(n.name).append(" investigating: ").append(n.lastOutcome).append('\n'); }
                }
            }
            System.out.println("collect samples " + collecting + "\n" + log);
            assertTrue(buriedAt > 0, "the watch buried the body: " + log);
            assertEquals(0, collectsAfter, "nobody went back to collect it: " + log);
            assertEquals(0, investigationsAfter, "nobody treated the graveyard as a murder scene: " + log);
        }
    }
}
