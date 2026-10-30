package rpg.ai;

import static org.junit.jupiter.api.Assertions.*;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import org.junit.jupiter.api.*;
import rpg.Rpg;
import rpg.actor.Appearance;
import rpg.objects.Door;
import rpg.world.DevMap;

/**
 * Gamefixes: an opened door did not become a pushable physics object. Once open and still, a leaf walked into from
 * either face is pushed round on its hinge; shut, it does not give.
 */
class DoorPushTest {
    private Game game;
    private Rpg g;
    @BeforeEach void open() {
        var cfg = new NativeEngine.Config(); cfg.deterministic = true;
        game = Game.create(cfg); game.activate(DevMap.build(game.engine()));
        g = game.add(new Rpg(game, "Door push", new Appearance(), true));
        for (Npc n : g.npcs) g.ai.unload(n);
    }
    @AfterEach void close() { game.close(); }
    private Door door(int cx, int cz) { for (Door d : g.objects.doors()) for (int[] c : d.cells) if (c[0] == cx && c[1] == cz) return d; throw new AssertionError(); }
    private void frames(int n) { for (int i = 0; i < n; i++) assertTrue(game.tick(1f / 60)); }

    /** Walks the player into the leaf's face from one side; returns how far the leaf turned. */
    private float push(Door d, float side) {
        float[] m = d.leafPose(0);
        float nx = m[8], nz = m[10], cx = m[12], cz = m[14];
        float before = d.angle[0];
        g.teleport(g.player, cx + nx * side * 0.95f, DevMap.FLOOR, cz + nz * side * 0.95f);
        g.controller.snapTo((float) Math.atan2(-(cx - g.player.x), -(cz - g.player.z)));
        frames(2);
        float sx = g.player.x, sz = g.player.z;
        g.controller.in.forward = 1;
        frames(70);
        System.out.printf("  walked from (%.2f, %.2f) to (%.2f, %.2f); leaf centre (%.2f, %.2f) normal (%.2f, %.2f)%n", sx, sz, g.player.x, g.player.z, cx, cz, nx, nz);
        g.controller.in.forward = 0;
        frames(20);
        System.out.printf("push from side %+.0f: leaf %.2f -> %.2f rad%n", side, before, d.angle[0]);
        return Math.abs(d.angle[0] - before);
    }

    @Test void anOpenLeafIsPushedRoundByABodyWalkingIntoIt() {
        Door d = door(103, DevMap.HOUSE2_Z0);
        g.teleport(g.player, 103.5f, DevMap.FLOOR, DevMap.HOUSE2_Z0 - 2.5f);
        frames(2);
        assertTrue(g.interact.request(g.player, d, "open").committed());
        frames(150);   // swung open and come to rest
        float a = push(d, 1), b = push(d, -1);
        assertTrue(a > 0.25f || b > 0.25f, "the open leaf did not give when walked into (" + a + ", " + b + " rad)");
    }
}
