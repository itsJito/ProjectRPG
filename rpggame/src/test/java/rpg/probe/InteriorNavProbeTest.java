package rpg.probe;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import rpg.Rpg;
import rpg.actor.Appearance;
import rpg.world.WorldMap;
import rpg.world.shadowveil.ShadowVeil;

/**
 * (0.4.4) A building's storeys as the routes see them: every cell of every room, marked by whether a route reaches it
 * from before the building's main door ('o' reached, 'x' standable but not reached, '#' no floor to stand on, 'D' a
 * door's cell, 'S' a stair's). {@code gradlew probe -PprobeClass=*InteriorNav* -Prpg.probeWho=id,id}.
 */
@Tag("probe")
class InteriorNavProbeTest {
    @Test void storeys() {
        String who = System.getProperty("rpg.probeWho", "loxford_castle");
        var cfg = new NativeEngine.Config(); cfg.deterministic = true; cfg.audioDevice = false;
        Game game = Game.create(cfg);
        try {
            game.activate(ShadowVeil.build(game.engine()));
            Rpg g = game.add(new Rpg(game, "interior nav", new Appearance(), true));
            game.tick(0);
            var reg = ShadowVeil.terrain().arch.registry;
            String leg = System.getProperty("rpg.probeLeg", "");
            if (!leg.isBlank()) {
                String[] ab = leg.split(";");
                float[] p = java.util.Arrays.stream(ab[0].split(",")).map(Float::parseFloat).collect(() -> new float[3], new java.util.function.BiConsumer<float[], Float>() { int i; public void accept(float[] a2, Float v) { a2[i++] = v; } }, (x2, y2) -> { });
                float[] q = java.util.Arrays.stream(ab[1].split(",")).map(Float::parseFloat).collect(() -> new float[3], new java.util.function.BiConsumer<float[], Float>() { int i; public void accept(float[] a2, Float v) { a2[i++] = v; } }, (x2, y2) -> { });
                System.out.println("[leg] route " + java.util.Arrays.toString(g.vox.navigation.route(p[0] + .5f, p[1], p[2] + .5f, q[0] + .5f, q[1], q[2] + .5f, d -> true, false, 0, new rpg.ai.RouteBook(), 0)));
                try {
                    var rm = rpg.ai.RuntimeNavigation.class.getDeclaredMethod("region", int.class); rm.setAccessible(true);
                    for (float[] c : new float[][]{p, q, {q[0], q[1], q[2] - 1}}) { int k = rpg.ai.RuntimeNavigation.key((int) c[0], (int) c[1], (int) c[2]); int r = (int) rm.invoke(g.vox.navigation, k);
                        System.out.println("[leg] region of " + (int) c[0] + "," + (int) c[1] + "," + (int) c[2] + " = " + rpg.ai.RuntimeNavigation.x(r) + "," + rpg.ai.RuntimeNavigation.y(r) + "," + rpg.ai.RuntimeNavigation.z(r)); }
                } catch (ReflectiveOperationException ex) { throw new RuntimeException(ex); }
                for (float[] c : new float[][]{p, q}) { StringBuilder sb = new StringBuilder("[leg] column " + (int) c[0] + "," + (int) c[2] + ":"); for (int y = (int) c[1] - 2; y <= (int) c[1] + 3; y++) sb.append(" ").append(y).append("=").append(g.vox.block((int) c[0], y, (int) c[2])); System.out.println(sb); }
            }
            for (String id : who.split(",")) {
                var e = reg.byId(id.trim());
                if (e == null || e.fit == null) { System.out.println("[nav] no fitted " + id); continue; }
                var en = e.entrances.get(0);
                float dx = en.facing() == 'e' ? 1 : en.facing() == 'w' ? -1 : 0, dz = en.facing() == 's' ? 1 : en.facing() == 'n' ? -1 : 0;
                float ox = en.x() + 0.5f + dx * 2, oz = en.z() + 0.5f + dz * 2, oy = ShadowVeil.standY(ox, oz);
                System.out.printf("[nav] %s from %.1f,%.1f,%.1f%n", id, ox, oy, oz);
                java.util.Set<Integer> ys = new java.util.TreeSet<>();
                for (var r : e.fit.rooms) ys.add(r.y);
                for (int y : ys) {
                    StringBuilder b = new StringBuilder("  storey y" + y + " (x " + e.x0 + ".." + e.x1 + " across, z " + e.z0 + ".." + e.z1 + " down)\n");
                    for (int z = e.z0; z <= e.z1; z++) {
                        b.append("   ");
                        for (int x = e.x0; x <= e.x1; x++) {
                            int blk = g.vox.block(x, y, z);
                            char c;
                            if (blk == rpg.world.Blocks.DOOR || blk == rpg.world.Blocks.LOCKED_DOOR) c = 'D';
                            else if (g.vox.block(x, y - 1, z) == rpg.world.Blocks.STAIRS || blk == rpg.world.Blocks.STAIRS) c = 'S';
                            else if (!g.vox.navigation.validPoint(x + 0.5f, y, z + 0.5f, d -> true)) c = '#';
                            else c = g.vox.navigation.route(ox, oy, oz, x + 0.5f, y, z + 0.5f, d -> true, false, 0, new rpg.ai.RouteBook(), 0) != null ? 'o' : 'x';
                            b.append(c);
                        }
                        b.append('\n');
                    }
                    System.out.print(b);
                }
            }
        } finally { game.close(); WorldMap.activateDevmap(); }
    }
}
