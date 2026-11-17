package rpg.probe;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import rpg.Rpg;
import rpg.actor.Appearance;
import rpg.animal.Animal;
import rpg.animal.Species;
import rpg.world.WorldMap;
import rpg.world.shadowveil.ShadowVeil;

/**
 * (0.4.7) The fliers on ShadowVeil: how many of each there are and where; then a flock walked up to - does it go up,
 * fly, and come down again - and a day of vultures: do they soar. {@code gradlew probe -PprobeClass=*BirdsProbe*}.
 */
@Tag("probe")
class BirdsProbeTest {
    @Test void birds() {
        var cfg = new NativeEngine.Config(); cfg.deterministic = true; cfg.audioDevice = false;
        Game game = Game.create(cfg);
        try {
            game.activate(ShadowVeil.build(game.engine()));
            Rpg g = game.add(new Rpg(game, "birds", new Appearance(), true));
            game.tick(0);
            for (Species s : new Species[]{Species.CROW, Species.PIGEON, Species.VULTURE}) {
                int n = 0; StringBuilder where = new StringBuilder();
                for (Animal a : g.animals.all()) if (a.species == s && a.alive) { n++; if (n <= 4) where.append(String.format(" (%.0f,%.0f %s)", a.x, a.z, a.home == null ? "-" : a.home.name)); }
                System.out.println("[birds] " + s + " " + n + where);
            }
            // a pigeon flock: the player walks up to it
            Animal pick = null;
            for (Animal a : g.animals.all()) if (a.species == Species.PIGEON && a.alive) { pick = a; break; }
            if (pick == null) for (Animal a : g.animals.all()) if (a.species == Species.CROW && a.alive) { pick = a; break; }
            if (pick != null) {
                g.teleport(g.player, pick.x + 25, WorldMap.standY(pick.x + 25, pick.z), pick.z);
                for (int i = 0; i < 120; i++) game.tick(1 / 60f);
                int maxFlying = 0; float maxHigh = 0; int landed = 0;
                for (int i = 0; i < 60 * 40; i++) {
                    if (i < 60 * 6) { float t = i / (60f * 6); g.teleport(g.player, pick.x + 25 * (1 - t) + 1.5f, WorldMap.standY(pick.x + 25 * (1 - t) + 1.5f, pick.z), pick.z); }
                    game.tick(1 / 60f);
                    int flying = 0;
                    for (Animal a : g.animals.all()) if (a.alive && a.species.flies && a.distTo(g.player) < 80 && a.flying) { flying++; maxHigh = Math.max(maxHigh, a.flightHeight); }
                    maxFlying = Math.max(maxFlying, flying);
                    if (i % 300 == 0) System.out.printf("[birds] t=%.0fs flying %d (picked %s at %.1f,%.1f,%.1f flying=%s mode=%d %s)%n", i / 60f, flying, pick.species, pick.x, pick.y, pick.z, pick.flying, pick.flightMode, pick.lastEvent);
                }
                for (Animal a : g.animals.all()) if (a.alive && a.species.flies && a.distTo(g.player) < 120 && !a.flying) landed++;
                System.out.println("[birds] most aloft at once " + maxFlying + ", highest " + maxHigh + " m, down again near " + landed);
            }
            // vultures by day
            g.state.minutes = Math.floor(g.state.minutes / 1440) * 1440 + 11 * 60;
            Animal v = null;
            for (Animal a : g.animals.all()) if (a.species == Species.VULTURE && a.alive) { v = a; break; }
            if (v == null) { v = g.animals.spawnNew(Species.VULTURE, false, true, g.player.x + 12, g.player.z, g.animals.habitatAt(g.player.x, g.player.z), 0, 0); v.homeX = v.x; v.homeZ = v.z; v.range = 20; }
            if (v != null) {
                g.teleport(g.player, v.x + 60, WorldMap.standY(v.x + 60, v.z), v.z);
                float high = 0; int aloft = 0;
                for (int i = 0; i < 60 * 60; i++) { game.tick(1 / 60f); if (v.flying) { aloft++; high = Math.max(high, v.flightHeight); } if (i % 600 == 0) System.out.printf("[birds] vulture %.1f,%.1f,%.1f flying %s mode %d h %.0f%n", v.x, v.y, v.z, v.flying, v.flightMode, v.flightHeight); }
                System.out.printf("[birds] vulture aloft %.0f%% of a minute, highest %.0f m, %s%n", aloft / 36f, high, v.lastEvent);
            }
        } finally { game.close(); WorldMap.activateDevmap(); }
    }
}
