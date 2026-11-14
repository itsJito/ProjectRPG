package rpg.world;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import rpg.Rpg;
import rpg.actor.Appearance;
import rpg.world.shadowveil.ShadowVeil;

/**
 * (0.4.6) A player-sized body walked along given points (any stair, a template's as well as a fitted flight), printing
 * where it sticks. {@code gradlew probe -PprobeClass=*StairPoints* -Prpg.probeWho="x,y,z;x,y,z;..."}.
 */
@Tag("probe")
class StairPointsProbeTest {
    @Test void walk() {
        var cfg = new NativeEngine.Config(); cfg.deterministic = true; cfg.audioDevice = false;
        try (Game game = Game.create(cfg)) {
            game.activate(ShadowVeil.build(game.engine()));
            Rpg g = new Rpg(game, "stairs", new Appearance(), true);
            StairWalkTest.Gate gate = game.add(new StairWalkTest.Gate(g));
            game.tick(0); game.tick(1 / 60f);
            gate.live = false;
            StairWalkTest.Walker w = game.add(new StairWalkTest.Walker());
            game.tick(1 / 60f);
            for (String run : System.getProperty("rpg.probeWho", "").split("[|]")) {
                List<float[]> pts = new ArrayList<>();
                for (String p : run.split(";")) { String[] v = p.trim().split(","); pts.add(new float[]{Float.parseFloat(v[0]), Float.parseFloat(v[1]), Float.parseFloat(v[2])}); }
                System.out.println("[stairs] " + run + " -> " + w.walk(game, pts) + String.format(" (at %.2f,%.2f,%.2f)", w.x, w.y, w.z));
                java.util.Collections.reverse(pts);
                System.out.println("[stairs] reversed -> " + w.walk(game, pts) + String.format(" (at %.2f,%.2f,%.2f)", w.x, w.y, w.z));
            }
        } finally { WorldMap.activateDevmap(); }
    }
}
