package rpg.world;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import rpg.Rpg;
import rpg.actor.Appearance;

/**
 * Probe (RPG_PROBE=1): the devmap from above as text, one character a block - where the buildings, the yards and the
 * open ground are - for laying out new places. Written to build/reports/devmap.txt.
 * '#' a wall or anything solid at head height, '+' a door, '~' water, '=' a paved floor, '.' open ground, 'T' a tree trunk,
 * 'o' an object (a station, a chest, a bed), 'n' a person standing there.
 */
@EnabledIfEnvironmentVariable(named = "RPG_PROBE", matches = "1")
class MapDumpProbeTest {
    @Test void dump() throws Exception {
        var cfg = new NativeEngine.Config(); cfg.deterministic = true;
        try (Game game = Game.create(cfg)) {
            game.activate(DevMap.build(game.engine()));
            Rpg g = game.add(new Rpg(game, "probe", Appearance.henric(), true));
            game.tick(0);
            char[][] map = new char[DevMap.SZ][DevMap.SX];
            int f = DevMap.FLOOR;
            for (int z = 0; z < DevMap.SZ; z++) for (int x = 0; x < DevMap.SX; x++) {
                int head = g.vox.block(x, f + 1, z), foot = g.vox.block(x, f, z), ground = g.vox.block(x, f - 1, z);
                char c = '.';
                if (foot == Blocks.WATER || ground == Blocks.WATER) c = '~';
                else if (Blocks.isTreeLog(foot)) c = 'T';
                else if (foot == Blocks.DOOR || foot == Blocks.LOCKED_DOOR || head == Blocks.DOOR || head == Blocks.LOCKED_DOOR) c = '+';
                else if (g.vox.solid(x, f + 1, z) || g.vox.solid(x, f, z)) c = '#';
                else if (ground != Blocks.GRASS && ground != Blocks.DIRT && ground != Blocks.BARE_PATCH) c = '=';
                map[z][x] = c;
            }
            for (var o : g.objects.all()) {
                if (o instanceof Foliage.Patch || o.removed) continue;
                int x = (int) Math.floor(o.x), z = (int) Math.floor(o.z);
                if (x >= 0 && z >= 0 && x < DevMap.SX && z < DevMap.SZ && map[z][x] != '#') map[z][x] = 'o';
            }
            for (var n : g.npcs) { int x = (int) Math.floor(n.x), z = (int) Math.floor(n.z); if (x >= 0 && z >= 0 && x < DevMap.SX && z < DevMap.SZ) map[z][x] = 'n'; }
            StringBuilder out = new StringBuilder("     ");
            for (int x = 0; x < DevMap.SX; x++) out.append(x % 10 == 0 ? (char) ('0' + (x / 10) % 10) : ' ');
            out.append('\n');
            for (int z = 0; z < DevMap.SZ; z++) { out.append(String.format("%4d ", z)).append(map[z]).append('\n'); }
            var file = new java.io.File("build/reports/devmap.txt"); file.getParentFile().mkdirs();
            java.nio.file.Files.writeString(file.toPath(), out.toString());
            System.out.println("[devmap] written to " + file.getAbsolutePath());
        }
    }
}
