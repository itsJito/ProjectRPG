package rpg.ai;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import org.junit.jupiter.api.Test;
import rpg.Ids;
import rpg.Rpg;
import rpg.actor.Appearance;
import rpg.world.DevMap;
import static org.junit.jupiter.api.Assertions.*;

class ThreatResponseTest {
    @Test void bruteAcquiresAVisiblePlayerEvenWhenAFightGoalHasNoTarget() {
        var cfg = new NativeEngine.Config(); cfg.deterministic = true;
        try (Game game = Game.create(cfg)) {
            game.activate(DevMap.build(game.engine()));
            Rpg g = game.add(new Rpg(game, "Brute targeting", new Appearance(), true));
            Npc brute = (Npc) g.actor(Ids.BRUTE);
            for (Npc other : g.npcs) if (other != brute) g.ai.unload(other);
            g.teleport(g.player, brute.x, DevMap.FLOOR, brute.z - 4);
            brute.yaw = brute.wantYaw = 0;
            assertTrue(g.ai.canSee(brute, g.player), "the player is plainly in front of the Brute");
            brute.goal = Brain.Goal.FIGHT; brute.fightTarget = 0;
            g.ai.brain.think(brute);
            assertEquals(Brain.Goal.FIGHT, brute.goal);
            assertEquals(g.player.id, brute.fightTarget, "target selection must not depend on changing the goal enum");
            g.ai.brain.act(brute, 1f / 60);
            assertEquals(Brain.Goal.FIGHT, brute.goal);
            long[] skills = brute.skills.raw().clone();
            g.teleport(g.player, brute.x, DevMap.FLOOR, brute.z - 1.5f);
            for (int frame = 0; frame < 180 && g.player.cs.lastAttacker != brute.id; frame++) assertTrue(game.tick(1f / 60));
            assertEquals(brute.id, g.player.cs.lastAttacker, "the real Brute must follow through and land a physical strike");
            assertArrayEquals(skills, brute.skills.raw(), "fighting must not train an NPC");
        }
    }
}
