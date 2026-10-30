package rpg;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import org.junit.jupiter.api.*;
import rpg.actor.Appearance;
import rpg.world.DevMap;

/**
 * (0.2.1, gamefixes: heavy stuttering on the larger map) where a game second of simulation goes, by system, with every
 * resident loaded: run with RPG_PROF=1. Not a pass/fail check; it prints the table.
 */
class SimProfileProbe {
    @Test void profileTheSimulation() {
        Assumptions.assumeTrue(Rpg.PROF, "set RPG_PROF=1 to profile");
        var cfg = new NativeEngine.Config(); cfg.deterministic = true;
        Game game = Game.create(cfg); game.activate(DevMap.build(game.engine()));
        try {
            Rpg g = game.add(new Rpg(game, "Prof", Appearance.henric(), true));
            g.teleport(g.player, 64.5f, DevMap.FLOOR, 76f);
            for (int i = 0; i < 300; i++) game.tick(1f / 60);
            java.util.Arrays.fill(g.prof, 0);
            var s0 = game.engine().stats();
            long t0 = System.nanoTime();
            int frames = 1200;
            for (int i = 0; i < frames; i++) game.tick(1f / 60);
            double total = (System.nanoTime() - t0) / 1e6;
            long java = 0; for (long p : g.prof) java += p;
            System.out.printf("PROF %d frames: %.2f ms a frame in all, %.2f ms in Rpg.update; loaded npcs %d%n", frames, total / frames, java / 1e6 / frames,
                    g.npcs.stream().filter(n -> n.loaded).count());
            for (String line : g.profile().split("\n")) System.out.println("PROF  " + line);
        } finally { game.close(); }
    }
}
