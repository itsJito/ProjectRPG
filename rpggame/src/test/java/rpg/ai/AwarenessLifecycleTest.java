package rpg.ai;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import org.junit.jupiter.api.Test;
import rpg.*;
import rpg.actor.*;
import rpg.item.*;
import rpg.objects.Corpse;
import rpg.world.DevMap;
import static org.junit.jupiter.api.Assertions.*;

class AwarenessLifecycleTest {
    @Test void aSleepGoalOnlyClosesTheEyesOnceTheNpcActuallyStopsAtTheirBed() {
        Npc npc = new Npc(1, "Sleeper", Npc.Role.VILLAGER, "", 10, 10);
        npc.homeX = npc.x; npc.homeZ = npc.z; npc.goal = Brain.Goal.SLEEP;
        npc.vx = 2; assertFalse(npc.asleep());
        npc.vx = 0; assertTrue(npc.asleep());
        npc.goal = Brain.Goal.WORK; assertFalse(npc.asleep());
    }

    private static Game game() {
        var cfg = new NativeEngine.Config(); cfg.deterministic = true;
        Game game = Game.create(cfg); game.activate(DevMap.build(game.engine())); return game;
    }
    @Test void bloodQuestionsExpireAndOnlyFreshBloodStartsAnotherQuestionEarly() {
        try (Game game = game()) {
            Rpg g = game.add(new Rpg(game, "Blood questions", new Appearance(), true));
            Npc cole = (Npc) g.actor(Ids.GUARD);
            for (Npc npc : g.npcs) if (npc != cole) g.ai.unload(npc);
            g.teleport(cole, 100.5f, DevMap.FLOOR, 72.5f); cole.yaw = 0;
            g.teleport(g.player, 100.5f, DevMap.FLOOR, 70.5f); g.player.bloodied = 0.5f;
            game.tick(0); g.ai.brain.think(cole);
            assertEquals(Brain.Goal.WATCH, cole.goal);
            g.time += 9; g.ai.brain.think(cole);
            assertNotEquals(Brain.Goal.WATCH, cole.goal, "unchanged blood must not renew a watch forever");
            assertEquals(0, g.law.bounty(g.player.id, DevMap.ALPHA));
            g.player.bloodied = 0.9f; g.ai.brain.think(cole);
            assertEquals(Brain.Goal.WATCH, cole.goal);
        }
    }
    @Test void aTimedOutSearchSettlesItsOriginalLeadEvenWhenAnotherLeadRanksHigher() {
        try (Game game = game()) {
            Rpg g = game.add(new Rpg(game, "Search timeout", new Appearance(), true));
            Npc cole = (Npc) g.actor(Ids.GUARD);
            cole.investigation = new Investigation(g.player.id, 1, 1, Investigation.Stimulus.SOUND, 0);
            cole.investigation.plan = Investigation.Plan.SEARCH;
            cole.investigation.stage = Investigation.Stage.FETCHING;
            cole.goal = Brain.Goal.INVESTIGATE;
            Stealth.Aware original = new Stealth.Aware(); original.level = 0.7f;
            cole.aware.put(g.player.id, original);
            Stealth.Aware other = new Stealth.Aware(); other.level = 0.9f;
            double untouchedAt = other.settledAt;
            cole.aware.put(Ids.THIEF, other);
            g.time = 51; g.ai.brain.act(cole, 1f / 60);
            assertNull(cole.investigation); assertEquals(51, original.settledAt);
            assertEquals(untouchedAt, other.settledAt);
        }
    }
    @Test void identifyingAnInnocentNeighborSettlesAGlimpseButNotViolence() {
        try (Game game = game()) {
            Rpg g = game.add(new Rpg(game,"Identified",new Appearance(),true));
            Npc guard=(Npc)g.actor(Ids.GUARD), neighbor=(Npc)g.actor(Ids.VILLAGER);
            g.teleport(neighbor,guard.x+2,DevMap.FLOOR,guard.z);neighbor.sneaking=false;
            Stealth.Aware known=new Stealth.Aware();known.level=0.9f;known.exposure=1;known.seenAt=2;known.heardAt=1;guard.aware.put(neighbor.id,known);
            g.time=2;
            for(var stimulus:new Investigation.Stimulus[]{Investigation.Stimulus.GLIMPSE,Investigation.Stimulus.VIOLENCE}) {
                guard.goal=Brain.Goal.INVESTIGATE;guard.investigation=new Investigation(neighbor.id,neighbor.x,neighbor.z,stimulus,g.time);
                guard.investigation.plan=Investigation.Plan.CHECK;g.ai.brain.act(guard,1f/60);
                if(stimulus==Investigation.Stimulus.GLIMPSE)assertNull(guard.investigation);
                else assertNotNull(guard.investigation,"seeing a neighbor does not explain violence");
            }
        }
    }
    @Test void aThiefReachesIntoAnActualNpcCorpseAndTransfersWornGearExactlyOnce() {
        try (Game game = game()) {
            Rpg g = game.add(new Rpg(game, "Corpse loot", new Appearance(), true));
            Npc thief = (Npc) g.actor(Ids.THIEF), dead = (Npc) g.actor(Ids.BANDIT_1);
            dead.alive = false;
            for (ItemDef.Slot slot : ItemDef.Slot.values()) dead.eq.set(slot, null);
            ItemStack sword = new ItemStack(Items.get("iron_sword"), 1);
            dead.eq.set(ItemDef.Slot.MAIN, sword);
            g.teleport(thief, 65.5f, DevMap.FLOOR, 62.5f);
            var corpse = g.objects.add(new Corpse(900100, dead.name, dead.id, 65.5f, DevMap.FLOOR, 61.8f, 0));
            thief.goal = Brain.Goal.LOOT; thief.goalSince = 0; g.time = 2;
            assertSame(corpse, g.ai.brain.graveFor(thief));
            g.ai.brain.act(thief, 1f / 60);
            assertSame(sword, dead.eq.get(ItemDef.Slot.MAIN), "gear stays on the body until the reach contacts it");
            assertTrue(thief.lootUntil > g.time);
            g.time += 0.61; g.ai.brain.act(thief, 1f / 60);
            assertNull(dead.eq.get(ItemDef.Slot.MAIN)); assertEquals(1, thief.count(sword.def));
            g.time += 0.02; g.ai.brain.act(thief, 1f / 60);
            assertEquals(1, thief.count(sword.def));
            assertFalse(thief.grids().stream().flatMap(grid -> grid.stacks().stream()).filter(s -> s.def == sword.def).findFirst().orElseThrow().stolen);
        }
    }
}
