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
 * Gamefixes: five new people, of callings no one held - a Cook, a Quarry Worker, a Border Knight, a Shellfish Gatherer
 * and a River Fisherman. Three hours of the morning: each is out at their own trade, and what they make is real.
 */
class NewResidentsTest {
    @Test void theFiveGetOnWithTheirTrades() {
        var cfg = new NativeEngine.Config(); cfg.deterministic = true;
        try (Game game = Game.create(cfg)) {
            game.activate(DevMap.build(game.engine()));
            Rpg g = game.add(new Rpg(game, "Residents", new Appearance(), true));
            g.state.minutes = 7 * 60;
            g.teleport(g.player, 64.5f, DevMap.FLOOR, 66.5f);
            Npc hilda = (Npc) g.actor(Ids.COOK), bertram = (Npc) g.actor(Ids.QUARRY_WORKER), roderic = (Npc) g.actor(Ids.BORDER_KNIGHT),
                    wynn = (Npc) g.actor(Ids.SHELLFISH_GATHERER), corwin = (Npc) g.actor(Ids.RIVER_FISHERMAN);
            for (Npc n : new Npc[]{hilda, bertram, roderic, wynn, corwin}) assertNotNull(n, "a new resident is missing");
            int rawBefore = hilda.count(Items.RAW_SHRIMP) + hilda.count(Items.RAW_MEAT);
            Map<String, Integer> seen = new HashMap<>();
            float roderidFar = 0;
            int samples = 0, atBorder = 0;
            boolean stone = false, trout = false, crab = false, cooked = false;
            // (at least a minute and a half, so the knight is judged on more than his walk out to the border)
            for (int f = 0; f < 60 * 90 * 2 && !(stone && trout && cooked && f > 60 * 90); f++) {
                game.tick(1f / 60);
                if (f % 60 != 0) continue;
                stone |= bertram.count(Items.STONE_BLOCK) > 0 || bertram.lastOutcome != null && bertram.lastOutcome.contains("deliver");
                trout |= corwin.count(Items.RAW_TROUT) > 0;
                crab |= wynn.count(Items.RAW_CRAB) > 0;
                cooked |= hilda.count(Items.RAW_SHRIMP) + hilda.count(Items.RAW_MEAT) < rawBefore;
                roderidFar = Math.max(roderidFar, (float) Math.hypot(roderic.x - roderic.dutyX, roderic.z - roderic.dutyZ));
                samples++; if (Math.hypot(roderic.x - roderic.dutyX, roderic.z - roderic.dutyZ) < 12) atBorder++;
                for (Npc n : new Npc[]{hilda, bertram, roderic, wynn, corwin}) seen.merge(n.name + ": " + n.goal + " " + (n.lastOutcome == null ? "" : n.lastOutcome.replaceAll("[0-9.]+", "#")), 1, Integer::sum);
            }
            seen.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(20).forEach(e -> System.out.println("  " + e.getValue() + "  " + e.getKey()));
            for (var node : g.objects.nodes) if (node.def.equals("quarry_face")) System.out.printf("quarry face: owner %d depleted %s stock %d refusal %s; bertram holds %s, carries %s%n", node.owner, node.depleted(), node.stock, node.refusal(bertram), bertram.main(), bertram.inv.stacks());
            System.out.printf("stone %s, trout %s, crab %s, cooked %s; the knight strayed at most %.1f m from the border, at it %d of %d%n", stone, trout, crab, cooked, roderidFar, atBorder, samples);
            assertTrue(stone, "the quarry worker cut no stone");
            assertTrue(trout, "the river fisherman caught nothing in the brook");
            assertTrue(cooked, "the cook cooked nothing");
            // (he may go to look into something - but his place is the border)
            assertTrue(atBorder > samples * 0.7f, "the border knight spent only " + atBorder + " of " + samples + " samples at the border");
            assertNotEquals(DevMap.jurisdiction(roderic.dutyX - 3, roderic.dutyZ - 3), DevMap.jurisdiction(roderic.dutyX + 3, roderic.dutyZ + 3), "the knight's post is on a border");
        }
    }
}
