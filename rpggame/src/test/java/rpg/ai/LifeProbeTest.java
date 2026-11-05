package rpg.ai;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import rpg.Rpg;
import rpg.actor.Appearance;
import rpg.world.DevMap;

/**
 * Probe (RPG_PROBE=1; RPG_PROBE_START=hour of Lunday, RPG_PROBE_HOURS): a stretch of the scheduled day at eight times the
 * clock, and for everyone who spends a share of it blocked, reporting or hiding, the goals and outcomes they were in -
 * the quick way to find where a schedule sends someone they cannot go.
 */
@EnabledIfEnvironmentVariable(named = "RPG_PROBE", matches = "1")
class LifeProbeTest {
    @Test void probe() {
        var config = new NativeEngine.Config(); config.deterministic = true;
        try (Game game = Game.create(config)) {
            game.activate(DevMap.build(game.engine()));
            Rpg g = game.add(new Rpg(game, "Life probe", new Appearance(), true));
            double start = Double.parseDouble(System.getenv().getOrDefault("RPG_PROBE_START", "5")), hours = Double.parseDouble(System.getenv().getOrDefault("RPG_PROBE_HOURS", "8"));
            g.state.minutes = 1440 + start * 60;
            g.cfg.minutesPerSecond = 8;
            g.teleport(g.player, 64.5f, DevMap.FLOOR, 66.5f);
            g.player.skills.setLevel(rpg.skill.Skill.HEALTH, 100); g.player.restore();
            Map<Long, Integer> samples = new HashMap<>(), bad = new HashMap<>();
            Map<Long, Map<String, Integer>> why = new HashMap<>();
            Map<String, Integer> crimes = new HashMap<>();
            double end = g.state.minutes + hours * 60;
            int frame = 0;
            while (g.state.minutes < end) {
                game.tick(1f / 60);
                if (++frame % 60 != 0) continue;
                for (Npc n : g.npcs) {
                    if (!n.alive || !n.loaded) continue;
                    samples.merge(n.id, 1, Integer::sum);
                    String o = n.lastOutcome == null ? "" : n.lastOutcome;
                    boolean blocked = o.contains("blocked") || o.contains("no route") || o.contains("no headway");
                    boolean off = n.goal == Brain.Goal.REPORT_CRIME || n.goal == Brain.Goal.HIDE || n.goal == Brain.Goal.SHELTER || n.goal == Brain.Goal.INVESTIGATE;
                    if (blocked || off) {
                        bad.merge(n.id, 1, Integer::sum);
                        String r = n.goal + (blocked ? " [blocked] " + o : "") + " / " + (n.goalReason == null ? "" : n.goalReason);
                        why.computeIfAbsent(n.id, k -> new HashMap<>()).merge(r.replaceAll("[0-9]+(\\.[0-9]+)?", "#"), 1, Integer::sum);
                    }
                    if (n.goal == Brain.Goal.REPORT_CRIME && n.goalReason != null) crimes.merge(n.goalReason.replaceAll("[0-9]+", "#"), 1, Integer::sum);
                }
            }
            StringBuilder b = new StringBuilder("\n=== life probe ===\ncrimes reported: " + crimes + "\n");
            b.append(String.format("farms: sown %d harvested %d apples %d%n", g.farming.sownCount, g.farming.harvestCount, g.farming.pickedCount));
            for (var inn : g.inns.all) b.append(String.format("%s: meals %d drinks %d produce %d%n", inn.name(), inn.meals, inn.drinks, inn.produceBought));
            for (Npc n : g.npcs) {
                int all = samples.getOrDefault(n.id, 0), k = bad.getOrDefault(n.id, 0);
                if (all == 0 || k < all / 10) continue;
                b.append(String.format("%-12s %-9s %d/%d  home (%.1f,%.1f) post (%.1f,%.1f) at (%.1f,%.1f)%n", n.name, n.role, k, all, n.homeX, n.homeZ, n.postX, n.postZ, n.x, n.z));
                why.get(n.id).entrySet().stream().sorted((a, c) -> c.getValue() - a.getValue()).limit(4)
                        .forEach(e -> b.append("     ").append(e.getValue()).append("  ").append(e.getKey(), 0, Math.min(150, e.getKey().length())).append('\n'));
            }
            System.out.println(b);
        }
    }

    @Test void trace() {
        String who = System.getenv("RPG_PROBE_TRACE");
        if (who == null) return;
        var config = new NativeEngine.Config(); config.deterministic = true;
        try (Game game = Game.create(config)) {
            game.activate(DevMap.build(game.engine()));
            Rpg g = game.add(new Rpg(game, "Trace probe", new Appearance(), true));
            g.state.minutes = 1440 + 9 * 60; g.cfg.minutesPerSecond = 8;
            g.teleport(g.player, 64.5f, DevMap.FLOOR, 66.5f);
            Npc n = null; for (Npc o : g.npcs) if (o.name.equals(who)) n = o;
            StringBuilder b = new StringBuilder("=== trace " + who + " ===%n".formatted());
            for (int f = 0; f < 60 * 60 * 2; f++) {
                game.tick(1f / 60);
                if (f % 120 == 0) b.append(String.format("%s %.2f at (%.2f,%.2f,%.2f) goal %s goalpos (%.2f,%.2f) legs %s legsTo (%.2f,%.2f) stall %.1f vs %s -> %s%n", g.state.clockText(), g.state.hourOfDay(), n.x, n.y, n.z, n.goal, n.gx, n.gz, n.legs == null ? "null" : n.legs.length + "@" + n.legAt, n.legsX, n.legsZ, n.stallBest, g.nameOf(n.fightTarget) + (g.actor(n.fightTarget) == null ? "" : String.format("@(%.1f,%.1f,%.1f)", g.actor(n.fightTarget).x, g.actor(n.fightTarget).y, g.actor(n.fightTarget).z)), n.lastOutcome));
            }
            System.out.println(b);
        }
    }
}
