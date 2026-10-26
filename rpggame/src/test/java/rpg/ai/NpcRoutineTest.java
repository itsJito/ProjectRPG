package rpg.ai;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import rpg.Rpg;
import rpg.actor.Appearance;
import rpg.world.DevMap;

/**
 * Every NPC's morning (gamefixes: some NPCs failed their routines): five hours of the real devmap, with how long each
 * spends with its route blocked. Lawmen were routed into their own locked cells, a fisher stood in the pond, a healer
 * waited all day on berry bushes; none may spend more than a tenth of the morning blocked now.
 */
class NpcRoutineTest {
    @Test void everyNpcGetsOnWithTheirMorningWithoutStandingBlocked() {
        var config = new NativeEngine.Config(); config.deterministic = true;
        try (Game game = Game.create(config)) {
            game.activate(DevMap.build(game.engine()));
            Rpg g = game.add(new Rpg(game, "Routine probe", new Appearance(), true));
            g.state.minutes = 6 * 60;
            g.teleport(g.player, 64.5f, DevMap.FLOOR, 66.5f);
            g.player.skills.setLevel(rpg.skill.Skill.HEALTH, 100); g.player.restore();
            Map<Long, Integer> stuck = new HashMap<>(), blocked = new HashMap<>(), still = new HashMap<>();
            Map<Long, Map<String, Integer>> outcomes = new HashMap<>();
            Map<Long, float[]> last = new HashMap<>();
            int frames = 60 * 60 * 5;
            for (int f = 0; f < frames; f++) {
                game.tick(1f / 60);
                if (f % 30 != 0) continue;
                for (Npc n : g.npcs) {
                    if (!n.alive || !n.loaded) continue;
                    if (n.stuck) stuck.merge(n.id, 1, Integer::sum);
                    String o = n.lastOutcome == null ? "" : n.lastOutcome;
                    if (o.contains("blocked") || o.contains("replanning") || o.contains("no route") || o.contains("locked")) blocked.merge(n.id, 1, Integer::sum);
                    outcomes.computeIfAbsent(n.id, k -> new HashMap<>()).merge(n.goal + ": " + o.replaceAll("[0-9.]+", "#"), 1, Integer::sum);
                    float[] p = last.get(n.id);
                    if (p != null && Math.hypot(p[0] - n.x, p[1] - n.z) < 0.05 && n.goal != Brain.Goal.IDLE && n.goal != Brain.Goal.SLEEP && n.goal != Brain.Goal.WORK && n.goal != Brain.Goal.SOCIALIZE && n.goal != Brain.Goal.EAT) still.merge(n.id, 1, Integer::sum);
                    last.put(n.id, new float[]{n.x, n.z});
                }
            }
            int samples = frames / 30;
            StringBuilder b = new StringBuilder("\n=== routine probe: " + samples + " samples ===\n");
            for (Npc n : g.npcs) {
                b.append(String.format("%-12s %-10s stuck %4d blocked %4d still-on-errand %4d  at (%.1f,%.1f) goal %s%n", n.name, n.role, stuck.getOrDefault(n.id, 0), blocked.getOrDefault(n.id, 0), still.getOrDefault(n.id, 0), n.x, n.z, n.goal));
                var top = outcomes.getOrDefault(n.id, Map.of()).entrySet().stream().sorted((a, c) -> c.getValue() - a.getValue()).limit(4).toList();
                for (var e : top) b.append("      ").append(e.getValue()).append("  ").append(e.getKey(), 0, Math.min(110, e.getKey().length())).append('\n');
            }
            System.out.println(b);
            for (Npc n : g.npcs) if (n.alive && n.loaded) org.junit.jupiter.api.Assertions.assertTrue(blocked.getOrDefault(n.id, 0) < samples / 10, () -> n.name + " spent the morning blocked:" + b);
        }
    }
}
