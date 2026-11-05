package rpg.ai;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import rpg.Rpg;
import rpg.actor.Appearance;
import rpg.world.DevMap;

/** Probe (RPG_PROBE=1): who is paused on a blocked route while reporting or warning, from where to where. */
@EnabledIfEnvironmentVariable(named = "RPG_PROBE", matches = "1")
class ReportPauseProbeTest {
    @Test void probe() {
        var config = new NativeEngine.Config(); config.deterministic = true;
        try (Game game = Game.create(config)) {
            game.activate(DevMap.build(game.engine()));
            Rpg g = game.add(new Rpg(game, "Routine probe", new Appearance(), true));
            g.state.minutes = 6 * 60;
            g.teleport(g.player, 64.5f, DevMap.FLOOR, 66.5f);
            g.player.skills.setLevel(rpg.skill.Skill.HEALTH, 100); g.player.restore();
            int printed = 0;
            for (int f = 0; f < 60 * 60 * 5 && printed < 5000; f++) {
                game.tick(1f / 60);
                if (f % 240 != 0) continue;
                for (Npc n : g.npcs) {
                    String who = System.getenv().getOrDefault("RPG_PROBE_WHO", "");
                    if (!who.isEmpty()) {
                        if (!n.name.equals(who) || n.investigation == null) continue;
                        var v = n.investigation;
                        System.out.printf("[inv] %s %s stage %s plan %s subject %s since %.0f s at (%.1f,%.1f) spot (%.1f,%.1f) why %s | %s%n", g.state.clockText(), n.name, v.stage, v.plan, g.nameOf(v.subject), g.time - v.startedAt, n.x, n.z, v.x, v.z, v.why, n.lastOutcome);
                        continue;
                    }
                    if (!n.alive || !n.loaded || n.lastOutcome == null || !n.lastOutcome.contains("paused")) continue;
                    printed++;
                    System.out.printf("[pause] %s %s %s at (%.1f,%.1f,%.1f) legsTo (%.1f,%.1f,%.1f) goalpos (%.1f,%.1f) pause %.1f reason %s | noRoutes %d%n",
                            g.state.clockText(), n.name, n.goal, n.x, n.y, n.z, n.legsX, n.legsY, n.legsZ, n.gx, n.gz, n.navPauseUntil - g.time, n.goalReason, n.noRoutes);
                }
            }
        }
    }
}
