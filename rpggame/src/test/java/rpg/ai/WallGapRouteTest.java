package rpg.ai;

import static org.junit.jupiter.api.Assertions.*;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import org.junit.jupiter.api.*;
import rpg.Rpg;
import rpg.actor.Appearance;
import rpg.world.DevMap;

/**
 * Gamefixes 0.2.1: an NPC outside DevCity's west wall was routed the long way round - the door router knew doors and
 * the map's gates, not the openings in the walls. Every walkable gap through a tall wall is now a link in the graph,
 * so a trip from just outside the wall to just inside it goes through the nearest opening.
 */
class WallGapRouteTest {
    private Game game;
    private Rpg g;
    @BeforeEach void open() {
        var cfg = new NativeEngine.Config(); cfg.deterministic = true; game = Game.create(cfg); game.activate(DevMap.build(game.engine()));
        g = game.add(new Rpg(game, "Gaps", Appearance.henric(), true)); for (var n : g.npcs) g.ai.unload(n);
    }
    @AfterEach void close() { game.close(); }

    private float length(float sx, float sz, int[] route, float tx, float tz) {
        float len = 0, x = sx, z = sz;
        for (int i : route) { float[] p = Waypoints.point(i); len += (float) Math.hypot(p[0] - x, p[1] - z); x = p[0]; z = p[1]; }
        return len + (float) Math.hypot(tx - x, tz - z);
    }

    @Test void aTripThroughATownWallTakesTheNearestOpening() {
        var gaps = Waypoints.gaps(g.vox, g.objects.doors());
        System.out.println("wall gaps: " + gaps.size());
        for (float[] gp : gaps) System.out.printf("  gap (%.1f, %.1f) <-> (%.1f, %.1f)%n", gp[0], gp[1], gp[2], gp[3]);
        assertFalse(gaps.isEmpty(), "no openings found in any wall");
        // for each opening: from a few metres out on one side to a few in on the other, the route is never a detour
        int checked = 0;
        for (float[] gp : gaps) {
            float dx = gp[2] - gp[0], dz = gp[3] - gp[1], l = (float) Math.hypot(dx, dz);
            float sx = gp[0] - dx / l * 6, sz = gp[1] - dz / l * 6, tx = gp[2] + dx / l * 6, tz = gp[3] + dz / l * 6;
            if (!Waypoints.clear(g.vox, sx, sz, gp[0], gp[1]) || !Waypoints.clear(g.vox, gp[2], gp[3], tx, tz)) continue;
            float straight = (float) Math.hypot(tx - sx, tz - sz), walked = length(sx, sz, Waypoints.route(g.vox, sx, sz, tx, tz), tx, tz);
            assertTrue(walked < straight * 1.6f + 4, String.format("through the gap at (%.1f, %.1f): %.1f m walked for %.1f m", (gp[0] + gp[2]) / 2, (gp[1] + gp[3]) / 2, walked, straight));
            checked++;
        }
        assertTrue(checked > 0, "no opening had clear ground either side to test");
    }
}
