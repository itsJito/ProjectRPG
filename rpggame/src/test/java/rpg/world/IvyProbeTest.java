package rpg.world;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import rpg.Rpg;
import rpg.actor.Appearance;

/** Probe: every Ivy and Moss patch in the world, and what holds it up. */
@EnabledIfEnvironmentVariable(named = "RPG_PROBE", matches = "1")
class IvyProbeTest {
    @Test void probe() throws Exception {
        var cfg = new NativeEngine.Config(); cfg.deterministic = true; Game game = Game.create(cfg); game.activate(DevMap.build(game.engine()));
        Rpg g = game.add(new Rpg(game, "Probe", Appearance.henric(), true));
        // generate every chunk
        for (int x = 8; x < DevMap.SX; x += 16) for (int z = 8; z < DevMap.SZ; z += 16) { g.teleport(g.player, x, DevMap.FLOOR + 30, z); for (int i = 0; i < 3; i++) { g.time += 0.3; g.foliage.update(); } }
        java.util.Map<String, Integer> tally = new java.util.TreeMap<>();
        StringBuilder odd = new StringBuilder();
        for (var chunk : g.foliage.chunks.values()) for (var p : chunk.plants) {
            if (p.kind != Foliage.Kind.IVY && p.kind != Foliage.Kind.MOSS) continue;
            int x = (int) Math.floor(p.x), y = (int) p.y, z = (int) Math.floor(p.z);
            int dx = Math.round((float) Math.sin(p.facing)), dz = Math.round((float) Math.cos(p.facing));
            int wall = g.vox.block(x + dx, y, z + dz), above = g.vox.block(x + dx, y + 1, z + dz);
            boolean ground = g.vox.solid(x, y - 1, z);
            // along the wall, to each side: is there wall there too?
            int ax = dz, az = -dx;
            boolean left = g.vox.solid(x + dx + ax, y, z + dz + az), right = g.vox.solid(x + dx - ax, y, z + dz - az);
            String key = p.kind + (ground ? "" : " NO-GROUND") + (left || right ? "" : " LONE-COLUMN") + (g.vox.solid(x + dx, y + 1, z + dz) ? "" : " SHORT") + " on " + String.valueOf(wall);
            tally.merge(key, 1, Integer::sum);
            if (!ground || !(left || right)) odd.append(String.format("  %s at (%d,%d,%d) wall %s above %s%n", p.kind, x, y, z, String.valueOf(wall), String.valueOf(above)));
        }
        StringBuilder out = new StringBuilder();
        tally.forEach((k, v) -> out.append(v).append("  ").append(k).append('\n'));
        out.append(odd);
        java.nio.file.Files.writeString(java.nio.file.Path.of("build/probe.txt"), out.toString());
        game.close();
    }
}
