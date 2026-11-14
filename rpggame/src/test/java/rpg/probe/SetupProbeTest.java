package rpg.probe;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import rpg.Rpg;
import rpg.actor.Appearance;
import rpg.world.DevMap;

/** (0.4.2) How long a test's world takes to make, stage by stage (a probe: prints, asserts nothing). */
@Tag("probe")
class SetupProbeTest {
    @Test void setupStages() {
        for (int k = 0; k < 4; k++) {
            long t0 = System.nanoTime();
            var cfg = new NativeEngine.Config(); cfg.deterministic = true;
            Game game = Game.create(cfg);
            long t1 = System.nanoTime();
            game.activate(DevMap.build(game.engine()));
            long t2 = System.nanoTime();
            Rpg g = game.add(new Rpg(game, "probe", new Appearance(), true));
            long t3 = System.nanoTime();
            game.tick(0);
            long t4 = System.nanoTime();
            game.close();
            long t5 = System.nanoTime();
            System.out.printf("[setup] create %.0f ms, devmap %.0f ms, rpg %.0f ms, first tick %.0f ms, close %.0f ms%n", (t1 - t0) / 1e6, (t2 - t1) / 1e6, (t3 - t2) / 1e6, (t4 - t3) / 1e6, (t5 - t4) / 1e6);
            if (k == 3 && Rpg.PROF) System.out.println(g.profile());
        }
    }
}
