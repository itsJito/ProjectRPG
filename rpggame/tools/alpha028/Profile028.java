package rpg;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import rpg.actor.Appearance;
import rpg.world.DevMap;
import java.util.Arrays;

/** Repeatable CPU route and full simulation timing; excludes the renderer. */
public final class Profile028 {
    public static void main(String[] args) {
        var cfg = new NativeEngine.Config(); cfg.deterministic = true; cfg.audioDevice = false;
        try (var game = Game.create(cfg)) {
            game.activate(DevMap.build(game.engine()));
            var g = game.add(new Rpg(game, "Timing", new Appearance(), true));
            g.state.minutes = 600; g.ai.syncAbstractClock();
            long[] times = new long[360];
            for (int i = 0; i < times.length + 60; i++) {
                long start = System.nanoTime(); game.tick(1f / 60);
                if (i >= 60) times[i - 60] = System.nanoTime() - start;
            }
            Arrays.sort(times);
            System.out.printf("simulation median=%.3f p99=%.3f max=%.3f ms%n", times[180]/1e6,times[356]/1e6,times[359]/1e6);
            var nav = g.vox.navigation;
            long start = System.nanoTime(), success = 0;
            for (int i = 0; i < 80; i++) if (nav.route(64.5f,4,76.5f,150.5f,4,78.5f,d->true,true,100+i,null,g.time)!=null) success++;
            System.out.printf("routes total=%.3f ms successes=%d searches=%d regions=%d cells=%d rebuilt=%d%n",(System.nanoTime()-start)/1e6,success,nav.searches,nav.regionExpansions,nav.expansions,nav.rebuiltChunks);
        }
    }
}
