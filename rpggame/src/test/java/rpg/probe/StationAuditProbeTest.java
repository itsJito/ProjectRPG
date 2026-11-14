package rpg.probe;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import rpg.Rpg;
import rpg.actor.Appearance;
import rpg.world.WorldMap;
import rpg.world.shadowveil.ShadowVeil;

/** (0.4.6, gamefixes: work items still outside) every station of ShadowVeil that stands under the open sky, and where. */
@Tag("probe")
class StationAuditProbeTest {
    @Test void audit() {
        var cfg = new NativeEngine.Config(); cfg.deterministic = true; cfg.audioDevice = false;
        Game game = Game.create(cfg);
        try {
            game.activate(ShadowVeil.build(game.engine()));
            Rpg g = game.add(new Rpg(game, "stations", new Appearance(), true));
            game.tick(0);
            int all = 0, out = 0;
            for (var o : g.objects.all()) if (o instanceof rpg.objects.Station st && !st.removed) {
                all++;
                int x = (int) Math.floor(st.x), z = (int) Math.floor(st.z), y = (int) Math.floor(st.y) + 1;
                boolean open = true;
                for (int k = 1; k <= 24 && open; k++) if (g.vox.solid(x, y + k, z)) open = false;
                var s = ShadowVeil.terrain().arch.at(x, z);
                if (open) { out++; System.out.printf("[stations] OUTSIDE %s at %.1f,%.1f,%.1f in %s%n", st.label(), st.x, st.y, st.z, s == null ? "-" : s.id); }
            }
            System.out.println("[stations] " + out + " of " + all + " stations under the open sky");
        } finally { game.close(); WorldMap.activateDevmap(); }
    }
}
