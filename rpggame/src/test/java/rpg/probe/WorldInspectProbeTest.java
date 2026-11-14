package rpg.probe;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import rpg.Rpg;
import rpg.actor.Appearance;
import rpg.world.Blocks;
import rpg.world.WorldMap;
import rpg.world.shadowveil.ShadowVeil;

/**
 * (0.4.3) What is at a point of ShadowVeil, for a bug report's coordinates: the structure there and those near, the
 * world objects, nodes and fields about it, the people, and the cells round it layer by layer (a letter per block, the
 * legend under each). {@code gradlew probe -PprobeClass=*WorldInspect* -Prpg.probeWho="276,40,848;246,55,607"}.
 */
@Tag("probe")
class WorldInspectProbeTest {
    @Test void inspect() {
        String at = System.getProperty("rpg.probeWho", "276,40,848");
        int radius = Integer.getInteger("rpg.probeFrames", 10);
        var cfg = new NativeEngine.Config(); cfg.deterministic = true; cfg.audioDevice = false;
        Game game = Game.create(cfg);
        try {
            game.activate(ShadowVeil.build(game.engine()));
            Rpg g = game.add(new Rpg(game, "inspect", new Appearance(), true));
            game.tick(0);
            for (String p : at.split(";")) {
                String[] v = p.trim().split("[, ]+");
                int x = Integer.parseInt(v[0]), y = Integer.parseInt(v[1]), z = Integer.parseInt(v[2]);
                System.out.println("\n================ " + x + ", " + y + ", " + z + "  law " + rpg.world.DevMap.jurisdiction(x, z) + "  stand Y " + WorldMap.standY(x + 0.5f, z + 0.5f));
                var arch = ShadowVeil.terrain().arch;
                var s = arch.at(x, z);
                System.out.println("structure here: " + (s == null ? "-" : s.id + " " + s.name + " (" + s.template + ") lot " + s.x0 + "," + s.z0 + " - " + s.x1 + "," + s.z1 + " floor " + s.floor));
                for (var o : arch.structures) {
                    double d = Math.hypot(o.cx() - x, o.cz() - z);
                    if (d < 40 && o != s) System.out.printf("  near %-26s %-40s %-14s %.0f m lot %d,%d-%d,%d floor %d%n", o.id, o.name, o.template, d, o.x0, o.z0, o.x1, o.z1, o.floor);
                }
                for (var o : g.objects.near(x, z, radius)) System.out.printf("  object %-22s %-30s (%.1f, %.1f, %.1f)%n", o.getClass().getSimpleName(), o.label(), o.x, o.y, o.z);
                for (var f : rpg.world.Farming.FIELDS) if (x >= f.x0() - radius && x <= f.x1() + radius && z >= f.z0() - radius && z <= f.z1() + radius)
                    System.out.println("  field " + f.name() + " " + f.x0() + "," + f.z0() + "-" + f.x1() + "," + f.z1() + " y " + f.y());
                for (var n : g.npcs) if (Math.hypot(n.x - x, n.z - z) < 30) System.out.printf("  npc %-12s %-14s (%.1f, %.1f, %.1f) goal %s%n", n.name, n.role, n.x, n.y, n.z, n.goal);
                Map<Integer, Character> legend = new LinkedHashMap<>();
                String letters = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
                for (int yy = y - 2; yy <= y + 4; yy++) {
                    System.out.println("  layer Y" + yy + " (x " + (x - radius) + ".." + (x + radius) + " across, z down; * the point)");
                    for (int zz = z - radius; zz <= z + radius; zz++) {
                        StringBuilder row = new StringBuilder("   ");
                        for (int xx = x - radius; xx <= x + radius; xx++) {
                            int b = g.vox.block(xx, yy, zz);
                            char c = b == Blocks.AIR ? '.' : b == Blocks.WATER ? '~' : legend.computeIfAbsent(b, k -> letters.charAt(Math.min(letters.length() - 1, legend.size())));
                            row.append(xx == x && zz == z ? '*' : c);
                        }
                        System.out.println(row);
                    }
                }
                StringBuilder lg = new StringBuilder("  legend:");
                for (var e : legend.entrySet()) lg.append(' ').append(e.getValue()).append('=').append(name(e.getKey()));
                System.out.println(lg);
            }
        } finally { game.close(); WorldMap.activateDevmap(); }
    }

    static String name(int id) {
        for (var f : Blocks.class.getFields()) {
            if (f.getType() != int.class || !java.lang.reflect.Modifier.isStatic(f.getModifiers()) || f.getName().startsWith("L_") || f.getName().startsWith("IN_")) continue;
            try { if (f.getInt(null) == id) return f.getName().toLowerCase(java.util.Locale.ROOT); } catch (IllegalAccessException ignored) { }
        }
        return "#" + id;
    }
}
