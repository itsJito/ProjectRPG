package rpg.ai;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import rpg.Rpg;
import rpg.actor.Appearance;
import rpg.world.DevMap;

/**
 * Probe (RPG_PROBE=1, RPG_PROBE_START hour, RPG_PROBE_HOURS): every time someone gives up a route for want of headway
 * (Brain's stall) or finds no route at all, where they were and where they were going - clustered by place, with the
 * nearest door - the map of where the town's paths fail, inside and out.
 */
@EnabledIfEnvironmentVariable(named = "RPG_PROBE", matches = "1")
class StallMapProbeTest {
    @Test void probe() {
        var config = new NativeEngine.Config(); config.deterministic = true;
        try (Game game = Game.create(config)) {
            game.activate(DevMap.build(game.engine()));
            Rpg g = game.add(new Rpg(game, "Stall probe", new Appearance(), true));
            double start = Double.parseDouble(System.getenv().getOrDefault("RPG_PROBE_START", "5")), hours = Double.parseDouble(System.getenv().getOrDefault("RPG_PROBE_HOURS", "6"));
            g.state.minutes = 1440 + start * 60;
            g.cfg.minutesPerSecond = 8;
            g.teleport(g.player, 64.5f, DevMap.FLOOR, 66.5f);
            g.player.skills.setLevel(rpg.skill.Skill.HEALTH, 100); g.player.restore();
            Map<Long, String> last = new HashMap<>();
            Map<String, Integer> spots = new TreeMap<>();
            Map<String, List<String>> who = new HashMap<>();
            int events = 0;
            double end = g.state.minutes + hours * 60;
            while (g.state.minutes < end) {
                game.tick(1f / 60);
                for (Npc n : g.npcs) {
                    if (!n.alive || !n.loaded) continue;
                    String o = n.lastOutcome == null ? "" : n.lastOutcome;
                    String count = n.stalls + ":" + n.noRoutes;
                    String prev = last.put(n.id, count);
                    if (prev == null || count.equals(prev)) continue;
                    events++;
                    rpg.objects.Door near = null; float nd = 1e9f;
                    for (var d : g.objects.doors()) { float dd = (float) Math.hypot(d.x - n.x, d.z - n.z); if (dd < nd) { nd = dd; near = d; } }
                    String key = String.format("(%3d,%3d)%s", (int) n.x / 2 * 2, (int) n.z / 2 * 2, nd < 3 && near != null ? " door " + near.name + (near.locked ? " LOCKED" : "") + (near.open ? " open" : "") : "");
                    spots.merge(key, 1, Integer::sum);
                    who.computeIfAbsent(key, k -> new ArrayList<>()).add(String.format("%s %s ->(%.0f,%.0f) %s", n.name, n.goal, n.stallX, n.stallZ, o.startsWith("no headway") ? "stall" : "noroute"));
                }
            }
            StringBuilder b = new StringBuilder("\n=== stall map: " + events + " events ===\n");
            spots.entrySet().stream().sorted((a, c) -> c.getValue() - a.getValue()).limit(30).forEach(e -> {
                b.append(String.format("%4d  %s%n", e.getValue(), e.getKey()));
                who.get(e.getKey()).stream().distinct().limit(4).forEach(w -> b.append("        ").append(w).append('\n'));
            });
            System.out.println(b);
        }
    }
}
