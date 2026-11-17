package rpg.probe;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import rpg.Rpg;
import rpg.actor.Appearance;
import rpg.world.Blocks;
import rpg.world.DungeonLayout;
import rpg.world.WorldMap;
import rpg.world.shadowveil.ShadowVeil;

/**
 * (0.4.7) ShadowVeil's dungeons under the map: every level, where it lies, its rooms and residents, whether its entry is
 * open floor, and whether a body can walk there from the site's mouth (and from the level above).
 * {@code gradlew probe -PprobeClass=*DungeonsProbe*}.
 */
@Tag("probe")
class DungeonsProbeTest {
    @Test void dungeons() {
        var cfg = new NativeEngine.Config(); cfg.deterministic = true; cfg.audioDevice = false;
        Game game = Game.create(cfg);
        try {
            game.activate(ShadowVeil.build(game.engine()));
            Rpg g = game.add(new Rpg(game, "dungeons", new Appearance(), true));
            game.tick(0);
            g.vox.navigation.warm();
            for (var p : rpg.dungeon.DungeonSites.problems()) System.out.println("[dungeons] problem: " + p);
            int ok = 0, all = 0;
            for (DungeonLayout l : DungeonLayout.underground()) {
                all++;
                int ex = l.worldX(l.entryX), ez = l.worldZ(l.entryZ), F = l.floorY;
                boolean open = g.vox.block(ex, F, ez) == Blocks.AIR && g.vox.block(ex, F + 1, ez) == Blocks.AIR && g.vox.solid(ex, F - 1, ez);
                int monsters = 0;
                for (var m : g.monsters.all()) if (m.alive && DungeonLayout.underAt(m.x, m.y, m.z) == l) monsters++;
                var site = rpg.dungeon.DungeonSites.byId(l.site);
                int[] route = null;
                if (l.depth == 1 && site != null) {
                    var tt = ShadowVeil.terrain();
                    float my = tt.top[tt.idx((int) Math.floor(site.mouthX()), (int) Math.floor(site.mouthZ()))] + 1;
                    route = g.vox.navigation.route(site.mouthX(), my, site.mouthZ(), ex + 0.5f, F, ez + 0.5f, d -> true, true, 0, null, 0, false);
                }
                boolean reach = l.depth > 1 || route != null && route.length > 0;
                String spec = System.getProperty("rpg.probeWho");
                if (!reach && spec != null) for (String leg : spec.split(";")) {
                    String[] v = leg.split(",");
                    if (v.length < 6) continue;
                    float[] q = new float[6]; for (int i = 0; i < 6; i++) q[i] = Float.parseFloat(v[i]);
                    int[] rr = g.vox.navigation.route(q[0], q[1], q[2], q[3], q[4], q[5], d -> true, true, 0, null, 0, false);
                    System.out.println("[dungeons]   leg " + leg + ": " + (rr == null ? "NONE" : rr.length + " legs") + " valid ends " + g.vox.navigation.validPoint(q[0], q[1], q[2], d -> true) + "/" + g.vox.navigation.validPoint(q[3], q[4], q[5], d -> true));
                }
                if (open && reach) ok++;
                if (l.depth == 1 && site != null) System.out.printf("[dungeons]   mouth %.1f,%.1f,%.1f%n", site.mouthX(), WorldMap.standY(site.mouthX(), site.mouthZ()), site.mouthZ());
                System.out.printf("[dungeons] %-34s %-6s %-6s entry %d,%d floor Y%d at %d,%d rooms %d residents %d entry %s route %s%n", l.name, l.type, l.theme, ex, ez, F, l.ox, l.oz, l.rooms.size(), monsters, open ? "open" : "SHUT", l.depth > 1 ? "(stair)" : route == null ? "NONE" : route.length + " legs");
            }
            System.out.println("[dungeons] " + ok + " of " + all + " levels open and reached (by walking, mouth to entry)");
            // the shafts: the mouth's passage to the head, the foot's passage to the entry, the ladder whole
            int good = 0;
            for (int[] sh : rpg.dungeon.Underground.SHAFTS) {
                boolean ladder = true;
                for (int y = sh[3]; y < sh[2]; y++) if (g.vox.block(sh[0], y, sh[1]) != Blocks.LADDER) ladder = false;
                boolean head = g.vox.navigation.validPoint(sh[4] + 0.5f, sh[2], sh[5] + 0.5f, d -> true), foot = g.vox.navigation.validPoint(sh[6] + 0.5f, sh[3], sh[7] + 0.5f, d -> true);
                if (ladder && head && foot) good++;
                System.out.printf("[dungeons] shaft %d,%d Y%d->Y%d ladder %s head %s foot %s%n", sh[0], sh[1], sh[2], sh[3], ladder, head, foot);
            }
            System.out.println("[dungeons] " + good + " of " + rpg.dungeon.Underground.SHAFTS.size() + " shafts whole");
        } finally { game.close(); WorldMap.activateDevmap(); }
    }
}
