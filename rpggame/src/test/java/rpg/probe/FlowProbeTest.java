package rpg.probe;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import rpg.world.WorldMap;
import rpg.world.shadowveil.ShadowVeil;
import rpg.world.shadowveil.Terrain;

/** (0.4.5) The water's flow field over ShadowVeil: which river columns it does not reach, and what lies about them. */
@Tag("probe")
class FlowProbeTest {
    @Test void flow() {
        var cfg = new NativeEngine.Config(); cfg.deterministic = true; cfg.audioDevice = false;
        Game game = Game.create(cfg);
        try {
            game.activate(ShadowVeil.build(game.engine()));
            Terrain t = ShadowVeil.terrain();
            var f = new rpg.world.Fluids(null);
            ShadowVeil.currents(f);
            int shown = 0, still = 0;
            for (int x = 0; x < t.S; x++) for (int z = 0; z < t.S; z++) {
                int i = t.idx(x, z);
                if ((t.flags[i] & Terrain.RIVER) == 0 || t.water[i] < 0) continue;
                if (f.field.vx(x, z) != 0 || f.field.vz(x, z) != 0) continue;
                still++;
                if (shown++ % 400 != 0) continue;
                StringBuilder b = new StringBuilder("[flow] still river " + x + "," + t.water[i] + "," + z + " flags " + t.flags[i] + " around:");
                for (int dz = -2; dz <= 2; dz++) { b.append("  "); for (int dx = -2; dx <= 2; dx++) { int j = t.idx(x + dx, z + dz); b.append(t.water[j] < 0 ? " ." : String.format("%3d", t.water[j])); } }
                System.out.println(b);
            }
            System.out.println("[flow] still river columns " + still + "; falls " + t.falls.size());
            int k = 0;
            for (int[] fl : t.falls) { if (k++ % 40 != 0) continue;
                StringBuilder b = new StringBuilder("[flow] fall " + java.util.Arrays.toString(fl) + " around:");
                for (int dz = -3; dz <= 3; dz++) { b.append("  "); for (int dx = -3; dx <= 3; dx++) { int j = t.idx(fl[0] + dx, fl[2] + dz); b.append(t.water[j] < 0 ? "  ." : String.format("%3d", t.water[j])); } }
                System.out.println(b); }
        } finally { game.close(); WorldMap.activateDevmap(); }
    }
}
