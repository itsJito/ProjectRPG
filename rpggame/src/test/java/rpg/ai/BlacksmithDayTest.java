package rpg.ai;

import static org.junit.jupiter.api.Assertions.*;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import rpg.Ids;
import rpg.Rpg;
import rpg.actor.Appearance;
import rpg.item.Items;
import rpg.world.DevMap;

/**
 * Gamefixes: the blacksmith waited for supplies most of the day. A working day (07:00 - 15:00) of the real devmap: she
 * may not spend more than a fifth of her working hours with no work she can do.
 */
class BlacksmithDayTest {
    @Test void theBlacksmithKeepsWorkingThroughTheDay() {
        var config = new NativeEngine.Config(); config.deterministic = true;
        try (Game game = Game.create(config)) {
            game.activate(DevMap.build(game.engine()));
            Rpg g = game.add(new Rpg(game, "Smith probe", new Appearance(), true));
            // (at the real pace: crafting and walking take real seconds)
            g.state.minutes = 7 * 60;
            g.teleport(g.player, 64.5f, DevMap.FLOOR, 66.5f);
            g.player.skills.setLevel(rpg.skill.Skill.HEALTH, 100); g.player.restore();
            Npc smith = (Npc) g.actor(Ids.CRAFTSMAN);
            Map<String, Integer> outcomes = new HashMap<>();
            int working = 0, idle = 0;
            int frames = 60 * 60 * 8;
            for (int f = 0; f < frames; f++) {
                game.tick(1f / 60);
                if (f % 120 != 0) continue;
                if (!smith.alive) { System.out.println("smith died at " + g.state.minutes); break; }
                if (smith.goal != Brain.Goal.WORK) continue;
                working++;
                String o = smith.lastOutcome == null ? "" : smith.lastOutcome;
                if (o.startsWith("No eligible work")) idle++;
                outcomes.merge(o.replaceAll("[0-9.]+", "#"), 1, Integer::sum);
                if (f % (60 * 60 * 2) == 0) System.out.printf("t %.0f min: bars %d copper %d tin %d coins %d shop copper %d tin %d bronze bars %d%n", g.state.minutes, smith.count(Items.BRONZE_BAR), smith.count(Items.COPPER_ORE), smith.count(Items.TIN_ORE),
                        smith.count(Items.COINS), g.econ.shopA.stock.count(Items.COPPER_ORE), g.econ.shopA.stock.count(Items.TIN_ORE), g.econ.shopA.stock.count(Items.BRONZE_BAR));
            }
            StringBuilder b = new StringBuilder("\n=== blacksmith: " + working + " working samples, " + idle + " with no work ===\n");
            outcomes.entrySet().stream().sorted((a, c) -> c.getValue() - a.getValue()).limit(12).forEach(e -> b.append("  ").append(e.getValue()).append("  ").append(e.getKey()).append('\n'));
            b.append(String.format("end: bars %d copper %d tin %d coins %d%n", smith.count(Items.BRONZE_BAR), smith.count(Items.COPPER_ORE), smith.count(Items.TIN_ORE), smith.count(Items.COINS)));
            for (var o : g.objects.all()) if (o instanceof rpg.objects.Station st && st.kind == rpg.item.Recipes.Station.ANVIL)
                for (var r : st.recipes()) if (r.id.contains("bronze")) b.append("  ").append(r.id).append(": ").append(st.refusal(r, smith)).append('\n');
            b.append("  alive ").append(smith.alive).append(" goal ").append(smith.goal).append(" ").append(smith.lastOutcome).append("  main ").append(smith.main()).append(" inv ").append(smith.inv.stacks()).append('\n');
            b.append("  shop ").append(g.econ.shopA.stock.stacks()).append(" merchant coins ").append(g.econ.merchant(g.econ.shopA).count(Items.COINS)).append(" oren ").append(((Npc) g.actor(Ids.MINER)).inv.stacks()).append('\n');
            System.out.println(b);
            assertTrue(working > 0, "the smith never went to work:" + b);
            assertTrue(idle < working / 5, "the smith waited for supplies " + idle + " of " + working + " working samples:" + b);
        }
    }
}
