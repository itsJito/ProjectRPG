package rpg.ai;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import org.junit.jupiter.api.Test;
import rpg.Ids;
import rpg.Rpg;
import rpg.actor.Appearance;
import rpg.skill.Skill;
import rpg.world.DevMap;
import static org.junit.jupiter.api.Assertions.*;

class BruteNavigationTest {
    @Test void aMissingReactionBodyCannotLeaveTheBrutePermanentlyUnableToAct() {
        var config = new NativeEngine.Config(); config.deterministic = true;
        try (Game game = Game.create(config)) {
            game.activate(DevMap.build(game.engine()));
            Rpg g = game.add(new Rpg(game, "Brute recovery", new Appearance(), true));
            Npc brute = (Npc) g.actor(Ids.BRUTE);
            for (Npc npc : g.npcs) if (npc != brute) g.ai.unload(npc);
            g.player.skills.setLevel(Skill.HEALTH, 100); g.player.restore();
            g.teleport(g.player, brute.x, DevMap.FLOOR, brute.z - 4);
            game.tick(0);
            brute.posePts = new float[45]; brute.poseAt = g.time;
            for (int i = 0; i < 15; i++) { brute.posePts[i * 3] = brute.x; brute.posePts[i * 3 + 1] = brute.y + 1; brute.posePts[i * 3 + 2] = brute.z; }
            g.reactions.struck(brute, 0, 0, 1, 0, 0, brute.x, brute.chestY(), brute.z);
            assertTrue(brute.reacting); game.tick(0);
            game.commands().release(brute.ragdoll); game.tick(0);
            g.time += 0.6; g.reactions.afterFrame(g.lastBodies);
            assertFalse(brute.reacting); assertEquals(0, brute.ragdoll);
            brute.yaw = 0;
            for (int frame = 0; frame < 600 && g.player.cs.lastAttacker != brute.id; frame++) assertTrue(game.tick(1f / 60));
            assertEquals(brute.id, g.player.cs.lastAttacker);
        }
    }

    @Test void bruteClosesEightMetresAndAttacksOnTheActualDevmap() {
        var config = new NativeEngine.Config(); config.deterministic = true;
        try (Game game = Game.create(config)) {
            game.activate(DevMap.build(game.engine()));
            Rpg g = game.add(new Rpg(game, "Brute route", new Appearance(), true));
            Npc brute = (Npc) g.actor(Ids.BRUTE);
            for (Npc n : g.npcs) if (n != brute) g.ai.unload(n);
            g.player.skills.setLevel(Skill.HEALTH, 100); g.player.restore();
            g.teleport(g.player, brute.x, DevMap.FLOOR, brute.z - 8);
            brute.yaw = 0; brute.nextThink = 0;
            assertTrue(game.tick(0));
            assertTrue(g.ai.canSee(brute, g.player));
            for (int frame = 0; frame < 600 && g.player.cs.lastAttacker != brute.id; frame++) assertTrue(game.tick(1f / 60));
            assertEquals(brute.id, g.player.cs.lastAttacker, () -> "Brute at " + brute.x + "," + brute.z + " goal " + brute.goal + " native " + brute.nativeAction + " route " + brute.routeCells + " stuck " + brute.stuck + " outcome " + brute.lastOutcome);
        }
    }
}
