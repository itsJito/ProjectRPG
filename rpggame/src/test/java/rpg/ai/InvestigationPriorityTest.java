package rpg.ai;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import org.junit.jupiter.api.Test;
import rpg.Ids;
import rpg.Rpg;
import rpg.actor.Appearance;
import rpg.item.ItemStack;
import rpg.item.Items;
import rpg.objects.Container;
import rpg.world.DevMap;

import static org.junit.jupiter.api.Assertions.*;

class InvestigationPriorityTest {
    @Test void credibleNightNoiseInterruptsLootingAndProducesAThiefsSafetyPlan() {
        var config = new NativeEngine.Config(); config.deterministic = true;
        try (Game game = Game.create(config)) {
            game.activate(DevMap.build(game.engine()));
            Rpg g = game.add(new Rpg(game, "Investigation regression", new Appearance(), true));
            g.state.minutes = 60;
            Npc thief = (Npc) g.actor(Ids.THIEF);
            g.teleport(thief, 73.5f, DevMap.FLOOR, 13.5f);
            thief.goal = Brain.Goal.LOOT; thief.aware.clear(); thief.memories.clear();
            var grave = g.objects.add(new Container(900001, "grave", "Exposed grave", "grave", 74, DevMap.FLOOR, 13, 40, g.player.id));
            grave.contents.add(new ItemStack(Items.BRONZE_ARROWS, 10));
            thief.graveSeen = grave.id;
            Stealth.Aware sound = new Stealth.Aware();
            sound.level = 0.65f; sound.heardAt = g.time; sound.x = 70; sound.z = 13;
            thief.aware.put(g.player.id, sound);
            g.ai.brain.think(thief);
            assertEquals(Brain.Goal.INVESTIGATE, thief.goal, thief.scores.toString());
            g.ai.brain.act(thief, 1f / 60);
            assertNotNull(thief.investigation);
            assertEquals(Investigation.Plan.HIDE, thief.investigation.plan);
        }
    }

    /** Gamefixes: guards went for the bodies first. With a killer to find, the body waits; with nothing else, it is fetched. */
    @Test void aGuardHuntsTheKillerBeforeFetchingTheBody() {
        var config = new NativeEngine.Config(); config.deterministic = true;
        try (Game game = Game.create(config)) {
            game.activate(DevMap.build(game.engine()));
            Rpg g = game.add(new Rpg(game, "Body priority", new Appearance(), true));
            g.state.minutes = 12 * 60;
            Npc guard = (Npc) g.actor(Ids.GUARD);
            var grave = g.objects.add(new Container(900002, "grave", "A grave", "grave", guard.x + 2, DevMap.FLOOR, guard.z, 40, g.player.id));
            grave.contents.add(new ItemStack(Items.BRONZE_ARROWS, 10));
            grave.secured = true; grave.expiresAt = g.time - 999;
            guard.collectAgain = 0;
            g.ai.brain.think(guard);
            assertEquals(Brain.Goal.COLLECT, guard.goal, "with nothing more pressing, the guard fetches the grave: " + guard.scores);
            guard.hunt = new Hunt(Ids.BANDIT_1, "a murder", guard.x + 20, guard.z, g.time, 60);
            g.ai.brain.think(guard);
            assertEquals(Brain.Goal.SEARCH, guard.goal, "the killer comes first: " + guard.scores);
        }
    }
}
