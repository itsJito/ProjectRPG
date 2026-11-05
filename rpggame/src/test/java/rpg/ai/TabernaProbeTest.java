package rpg.ai;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import rpg.Rpg;
import rpg.actor.Appearance;
import rpg.item.Items;
import rpg.world.DevMap;

/** Probe (RPG_PROBE=1): the DevPalace growers and the Taberna's bar, hour by hour over two days at speed. */
@EnabledIfEnvironmentVariable(named = "RPG_PROBE", matches = "1")
class TabernaProbeTest {
    @Test void probe() {
        var config = new NativeEngine.Config(); config.deterministic = true;
        try (Game game = Game.create(config)) {
            game.activate(DevMap.build(game.engine()));
            Rpg g = game.add(new Rpg(game, "Weekly life", new Appearance(), true));
            g.state.minutes = 1440 + 5 * 60;
            g.cfg.minutesPerSecond = 8;
            g.teleport(g.player, 64.5f, DevMap.FLOOR, 66.5f);
            g.player.skills.setLevel(rpg.skill.Skill.HEALTH, 100); g.player.restore();
            var inn = g.inns.all.stream().filter(i -> i.name().contains("Taberna")).findFirst().orElseThrow();
            Npc rufus = null, valeria = null;
            for (Npc n : g.npcs) { if (n.name.equals("Rufus")) rufus = n; if (n.name.equals("Valeria")) valeria = n; }
            double end = g.state.minutes + 36 * 60, next = 0;
            java.util.Map<Long, String> seen = new java.util.HashMap<>();
            int lastC = 0;
            while (g.state.minutes < end) {
                game.tick(1f / 60);
                for (Npc w : new Npc[]{rufus, valeria}) if (w.lastOutcome != null && w.lastOutcome.contains("sold") && !w.lastOutcome.equals(seen.get(w.id))) { seen.put(w.id, w.lastOutcome); System.out.printf("[tab] %s %s %s (home %s)%n", g.state.clockText(), w.name, w.lastOutcome, w.homeJurisdiction); }
                int nowC = rufus.count(Items.CARROT) + valeria.count(Items.APPLE) * 1000;
                if (nowC < lastC) System.out.printf("[drop] %s Rufus %d carrots goal %s (%s) at (%.1f,%.1f) | Valeria %d apples goal %s (%s) at (%.1f,%.1f)%n", g.state.clockText(), rufus.count(Items.CARROT), rufus.goal, rufus.lastOutcome, rufus.x, rufus.z, valeria.count(Items.APPLE), valeria.goal, valeria.lastOutcome, valeria.x, valeria.z);
                lastC = nowC;
                if (g.state.minutes < next) continue;
                next = g.state.minutes + 60;
                Npc b = g.econ.merchant(inn.bar);
                System.out.printf("[tab] %s bar keeper %s coins %d dist %.1f open %s | Rufus %s carrots %d cabb %d: %s | Valeria %s apples %d: %s | bought %d%n",
                        g.state.clockText(), b == null ? "-" : b.name, b == null ? 0 : b.count(Items.COINS), b == null ? -1 : Math.hypot(b.x - inn.bar.counterX, b.z - inn.bar.counterZ),
                        rpg.econ.Hours.open(g, inn.bar), rufus.goal, rufus.count(Items.CARROT), rufus.count(Items.CABBAGE), rufus.lastOutcome,
                        valeria.goal, valeria.count(Items.APPLE), valeria.lastOutcome, inn.produceBought);
            }
        }
    }
}
