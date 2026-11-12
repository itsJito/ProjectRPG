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
 * (0.4.0, gamefixes: NPCs all cluster together and idle) Three hours of the devmap's day: how often people stand idle
 * shoulder to shoulder in a knot of three or more, and what each of them was doing there. Fails when anyone spends more
 * than an eighth of the day in such a knot.
 */
class ClusterProbeTest {
    @org.junit.jupiter.api.Tag("slow") @Test void peopleDoNotStandInIdleKnots() {
        var config = new NativeEngine.Config(); config.deterministic = true;
        try (Game game = Game.create(config)) {
            game.activate(DevMap.build(game.engine()));
            Rpg g = game.add(new Rpg(game, "Cluster probe", new Appearance(), true));
            g.state.minutes = 8 * 60;
            g.teleport(g.player, 64.5f, DevMap.FLOOR, 66.5f);
            Map<Long, Integer> knotted = new HashMap<>();
            Map<String, Integer> why = new HashMap<>();
            int frames = 60 * 60 * 3, samples = 0;
            for (int f = 0; f < frames; f++) {
                game.tick(1f / 60);
                if (f % 60 != 0) continue;
                samples++;
                for (Npc n : g.npcs) {
                    if (!n.alive || !n.loaded || n.seated) continue;
                    boolean idle = Math.hypot(n.vx, n.vz) < 0.15f && n.goal != Brain.Goal.WORK && n.goal != Brain.Goal.SLEEP;
                    if (!idle) continue;
                    int near = 0;
                    for (Npc o : g.npcs) if (o != n && o.alive && o.loaded && Math.hypot(o.vx, o.vz) < 0.15f && n.distTo(o) < 1.6f) near++;
                    if (near >= 2) {
                        knotted.merge(n.id, 1, Integer::sum);
                        why.merge(n.goal + ": " + (n.lastOutcome == null ? "" : n.lastOutcome.replaceAll("[0-9.]+", "#")), 1, Integer::sum);
                    }
                }
            }
            StringBuilder b = new StringBuilder("\n=== idle knots over " + samples + " samples ===\n");
            for (Npc n : g.npcs) if (knotted.getOrDefault(n.id, 0) > 0) b.append(String.format("%-14s %-12s knotted %4d at (%.1f,%.1f)%n", n.name, n.role, knotted.get(n.id), n.x, n.z));
            why.entrySet().stream().sorted((x, y) -> y.getValue() - x.getValue()).limit(14).forEach(e -> b.append("   ").append(e.getValue()).append("  ").append(e.getKey()).append('\n'));
            System.out.println(b);
            final int limit = samples / 8;
            for (Npc n : g.npcs) org.junit.jupiter.api.Assertions.assertTrue(knotted.getOrDefault(n.id, 0) <= limit, () -> n.name + " stood in an idle knot too long:" + b);
        }
    }
}
