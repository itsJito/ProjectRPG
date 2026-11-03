package rpg.animal;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import org.junit.jupiter.api.Test;
import rpg.Rpg;
import rpg.actor.Appearance;
import rpg.world.DevMap;
import static org.junit.jupiter.api.Assertions.*;

/** (0.3.0, gamefixes: some animals such as dogs did not ragdoll on death) every species falls as a physical body when killed. */
class RagdollProbeTest {
    @Test void everySpeciesFallsAsAPhysicalBody() {
        var cfg = new NativeEngine.Config(); cfg.deterministic = true;
        try (Game game = Game.create(cfg)) {
            game.activate(DevMap.build(game.engine()));
            Rpg g = game.add(new Rpg(game, "Ragdolls", new Appearance(), true));
            for (var npc : g.npcs) g.ai.unload(npc);
            StringBuilder report = new StringBuilder();
            boolean ok = true;
            for (Species s : Species.values()) {
                for (Animal o : new java.util.ArrayList<>(g.animals.all())) g.animals.remove(o);
                Animal a = g.animals.release(s, false, false, 64.5f, 66.5f);
                for (int i = 0; i < 20; i++) game.tick(1f / 60);
                a.blowX = 1; a.blowZ = 0; a.blowImpact = 40;
                a.alive = false; a.hp = 0;
                g.animals.onKilled(a, g.player, 0);
                boolean had = a.ragdoll != null;
                int pieces = had ? a.ragdoll.pieces.size() : 0;
                float[] start = had ? a.ragdoll.trunkAt(g) : null;
                for (int i = 0; i < 120; i++) game.tick(1f / 60);
                float[] end = a.ragdoll != null ? a.ragdoll.trunkAt(g) : null;
                report.append(String.format("%-14s ragdoll=%s pieces=%d trunkY %s -> %s%n", s, had, pieces,
                        start == null ? "-" : String.format("%.2f", start[1] - DevMap.FLOOR), end == null ? "-" : String.format("%.2f", end[1] - DevMap.FLOOR)));
                if (!had || pieces < 3) ok = false;
            }
            System.out.println(report);
            assertTrue(ok, report.toString());
        }
    }
}
