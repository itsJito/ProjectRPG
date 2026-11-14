package rpg.ai;

import static org.junit.jupiter.api.Assertions.*;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import rpg.Rpg;
import rpg.actor.Appearance;
import rpg.world.DevMap;

/**
 * Alpha 0.3.2 (gamefixes: the weekly schedules, the inns, the farms; "run multiple tests at high speed to make sure all
 * NPCs are performing their duties and the simulation runs dynamically"): the real devmap run through whole days at eight
 * times the clock, every NPC watched. The towns' days must actually happen - the gardens sown and harvested, the apples
 * picked, the inns' pots cooked and their tables filled by townsfolk buying meals and drinks - and everyone's week must
 * be a week: no two days the same, each person at their work when their plan says work, nobody standing blocked.
 */
class WeeklyLifeTest {
    @Test void everyScheduledPersonHasAWeekWhoseDaysDiffer() {
        var config = new NativeEngine.Config(); config.deterministic = true;
        try (Game game = Game.create(config)) {
            game.activate(DevMap.build(game.engine()));
            Rpg g = game.add(new Rpg(game, "Week probe", new Appearance(), true));
            int scheduled = 0;
            StringBuilder b = new StringBuilder();
            for (Npc n : g.npcs) {
                if (Schedule.family(n) == Schedule.Family.NONE) continue;
                scheduled++;
                Set<String> days = new HashSet<>();
                for (int wd = 0; wd < 7; wd++) days.add(Schedule.text(n, wd).substring(Schedule.text(n, wd).indexOf(':')));
                b.append(String.format("%-12s %-8s %d distinct days%n", n.name, Schedule.family(n), days.size()));
                assertTrue(days.size() >= 6, n.name + "'s week repeats itself: " + days);
            }
            System.out.println("[week] " + scheduled + " people with a week\n" + b);
            assertTrue(scheduled >= 50, "the townsfolk have weeks");
            // and the families differ from one another on the same day
            Npc smith = (Npc) g.actor(rpg.Ids.CRAFTSMAN), guard = (Npc) g.actor(rpg.Ids.GUARD), bartender = (Npc) g.actor(rpg.Ids.BARTENDER_CITY);
            assertNotEquals(Schedule.text(smith, 2), Schedule.text(guard, 2));
            assertNotEquals(Schedule.text(smith, 2), Schedule.text(bartender, 2));
            System.out.println(Schedule.text(smith, 1) + "\n" + Schedule.text(guard, 1) + "\n" + Schedule.text(bartender, 1));
        }
    }

    @Test void theTownsLiveThroughTwoDaysAtSpeed() {
        var config = new NativeEngine.Config(); config.deterministic = true;
        try (Game game = Game.create(config)) {
            game.activate(DevMap.build(game.engine()));
            Rpg g = game.add(new Rpg(game, "Weekly life", new Appearance(), true));
            // Lunday (a working day) before dawn
            g.state.minutes = 1440 + 5 * 60;
            g.cfg.minutesPerSecond = 8;
            g.teleport(g.player, 64.5f, DevMap.FLOOR, 66.5f);
            g.player.skills.setLevel(rpg.skill.Skill.HEALTH, 100); g.player.restore();
            Map<Long, Integer> samples = new HashMap<>(), blocked = new HashMap<>(), planWork = new HashMap<>(), atWork = new HashMap<>();
            Set<Long> patrons = new HashSet<>();
            Map<String, Integer> goals = new HashMap<>();
            double end = g.state.minutes + 2 * 1440;
            int frame = 0;
            long t0 = System.nanoTime();
            while (g.state.minutes < end) {
                game.tick(1f / 60);
                if (++frame % 60 != 0) continue;
                double hour = g.state.hourOfDay();
                int wd = g.state.weekday();
                for (Npc n : g.npcs) {
                    if (!n.alive || !n.loaded || Schedule.family(n) == Schedule.Family.NONE) continue;
                    samples.merge(n.id, 1, Integer::sum);
                    String o = n.lastOutcome == null ? "" : n.lastOutcome;
                    // (sheltering from rain is the plan giving way to the weather, not a block)
                    if ((o.contains("blocked") || o.contains("no route")) && n.goal != Brain.Goal.SHELTER) blocked.merge(n.id, 1, Integer::sum);
                    if (n.goal == Brain.Goal.INN) patrons.add(n.id);
                    goals.merge(n.goal.name(), 1, Integer::sum);
                    // (0.3.9) the plan giving way to the rain (0.3.8), to danger (a fisherman out in the wilds hiding from a
                    // wolf) or to the law (word of a crime carried to town) is not an absence from work
                    boolean overruled = n.goal == Brain.Goal.SHELTER || n.goal == Brain.Goal.HIDE || n.goal == Brain.Goal.FLEE || n.goal == Brain.Goal.REPORT_CRIME;
                    if (Schedule.now(n, wd, hour) == Schedule.Activity.WORK && !(overruled && !n.watch())) {
                        planWork.merge(n.id, 1, Integer::sum);
                        // (0.3.3) a hunter's quarry fought out in the wilds is the hunter's work, as a fight is the watch's
                        if (n.goal == Brain.Goal.WORK || n.goal == Brain.Goal.PATROL || n.goal == Brain.Goal.COLLECT
                                || n.goal == Brain.Goal.FIGHT && (n.role == Npc.Role.HUNTER || n.watch()) && !DevMap.ALPHA.equals(DevMap.jurisdiction(n.x, n.z))
                                // (0.3.6) the watch's duty is its work: searching, looking into a crime, keeping an eye on someone, seeing outlaws off
                                || n.watch() && (n.goal == Brain.Goal.SEARCH || n.goal == Brain.Goal.INVESTIGATE || n.goal == Brain.Goal.WATCH || n.goal == Brain.Goal.EVICT || n.goal == Brain.Goal.CASE
                                    || n.goal == Brain.Goal.FIGHT || n.goal == Brain.Goal.RETURN)) atWork.merge(n.id, 1, Integer::sum);
                    }
                }
            }
            System.out.printf("[life] two days in %.0fs real, %d frames%n", (System.nanoTime() - t0) / 1e9, frame);
            long sown = g.farming.sownCount, harvested = g.farming.harvestCount, picked = g.farming.pickedCount;
            long ate = g.inns.all.stream().mapToInt(i -> i.meals + i.drinks).sum();
            StringBuilder b = new StringBuilder(String.format("sown %d, harvested %d, apples %d, meals eaten at an inn %d, patrons %d%n", sown, harvested, picked, ate, patrons.size()));
            for (var inn : g.inns.all) b.append(String.format("  %s: meals %d drinks %d nights %d produce bought %d; bar now %s%n", inn.name(), inn.meals, inn.drinks, inn.nights, inn.produceBought, inn.bar.stock.stacks().stream().map(s -> s.amount + " " + s.def.id).toList()));
            b.append("goals: ").append(goals).append('\n');
            for (Npc n : g.npcs) {
                if (Schedule.family(n) == Schedule.Family.NONE) continue;
                int pw = planWork.getOrDefault(n.id, 0), aw = atWork.getOrDefault(n.id, 0);
                b.append(String.format("  %-12s %-8s work %3d/%3d blocked %3d/%4d  %s%n", n.name, Schedule.family(n), aw, pw, blocked.getOrDefault(n.id, 0), samples.getOrDefault(n.id, 0), n.lastOutcome));
            }
            System.out.println(b);
            assertTrue(sown > 0, "the gardens were sown\n" + b);
            assertTrue(harvested >= 4, "the gardens were harvested\n" + b);
            assertTrue(picked >= 6, "the apples were picked\n" + b);
            for (var inn : g.inns.all) {
                assertTrue(inn.meals + inn.drinks >= 6, inn.name() + " served its town\n" + b);
                assertTrue(inn.produceBought > 0, inn.name() + " bought from the farms\n" + b);
            }
            assertTrue(patrons.size() >= 10, "townsfolk went to the inns\n" + b);
            for (Npc n : g.npcs) {
                if (!n.alive || Schedule.family(n) == Schedule.Family.NONE) continue;
                int all = samples.getOrDefault(n.id, 0);
                if (all > 0) assertTrue(blocked.getOrDefault(n.id, 0) < all / 5, n.name + " spent the days blocked\n" + b);
                int pw = planWork.getOrDefault(n.id, 0);
                if (pw > 60) assertTrue(atWork.getOrDefault(n.id, 0) >= pw * 0.35, n.name + " was not at work when the plan said work\n" + b);
            }
        }
    }
}
