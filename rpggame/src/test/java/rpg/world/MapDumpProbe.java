package rpg.world;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import org.junit.jupiter.api.Test;
import rpg.Rpg;
import rpg.actor.Appearance;

/** Prints an area of the devmap from above (a probe for finding room to build): '.' open ground, '#' solid, '~' water. */
class MapDumpProbe {
    @Test void dump() {
        String area = System.getenv("RPG_DUMP");
        if (area == null) return;
        String[] a = area.split(",");
        int x0 = Integer.parseInt(a[0]), z0 = Integer.parseInt(a[1]), x1 = Integer.parseInt(a[2]), z1 = Integer.parseInt(a[3]);
        var config = new NativeEngine.Config(); config.deterministic = true;
        try (Game game = Game.create(config)) {
            game.activate(DevMap.build(game.engine()));
            Rpg g = game.add(new Rpg(game, "Map dump", new Appearance(), true));
            StringBuilder b = new StringBuilder("\n");
            for (int z = z0; z <= z1; z++) {
                b.append(String.format("%3d ", z));
                for (int x = x0; x <= x1; x++) {
                    int up = g.vox.block(x, DevMap.FLOOR, z), floor = g.vox.block(x, DevMap.FLOOR - 1, z);
                    char c = up != Blocks.AIR ? '#' : floor == Blocks.WATER ? '~' : floor == Blocks.COBBLE ? '=' : '.';
                    for (var o : g.objects.all()) if ((int) Math.floor(o.x) == x && (int) Math.floor(o.z) == z && c == '.') c = 'o';
                    b.append(c);
                }
                b.append('\n');
            }
            System.out.println(b);
        }
    }
}
