package rpg.actor;

import static org.junit.jupiter.api.Assertions.*;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import org.junit.jupiter.api.Test;
import rpg.Rpg;
import rpg.world.DevMap;

/** Gamefixes: controls should be snappier in first person, third person left as it was. */
class FirstPersonResponseTest {
    /** Frames from rest to 90% of walking speed, and from walking to a stand. */
    private static int[] response(boolean firstPerson) {
        var cfg = new NativeEngine.Config(); cfg.deterministic = true;
        try (Game game = Game.create(cfg)) {
            game.activate(DevMap.build(game.engine()));
            Rpg g = game.add(new Rpg(game, "Feel", new Appearance(), true));
            for (var n : g.npcs) g.ai.unload(n);
            float[] lane = rpg.TestLanes.open(g);
            g.teleport(g.player, lane[0], DevMap.FLOOR, lane[1]);
            g.player.firstPerson = firstPerson;
            for (int i = 0; i < 20; i++) game.tick(1f / 60);
            int up = -1, down = -1;
            for (int i = 0; i < 90; i++) {
                g.controller.in.forward = 1; g.controller.snapTo(0); game.tick(1f / 60);
                if (up < 0 && Math.hypot(g.player.vx, g.player.vz) > g.cfg.walkSpeed * 0.9f) up = i;
            }
            for (int i = 0; i < 90; i++) {
                g.controller.in.forward = 0; game.tick(1f / 60);
                if (down < 0 && Math.hypot(g.player.vx, g.player.vz) < 0.1f) down = i;
            }
            return new int[]{up, down};
        }
    }

    @Test void firstPersonAnswersTheKeysSoonerThanThirdPerson() {
        int[] fp = response(true), tp = response(false);
        System.out.printf("first person: up %d frames, stop %d; third person: up %d, stop %d%n", fp[0], fp[1], tp[0], tp[1]);
        assertTrue(fp[0] >= 0 && fp[1] >= 0 && tp[0] >= 0 && tp[1] >= 0);
        assertTrue(fp[0] < tp[0], "first person gets up to speed sooner");
        assertTrue(fp[1] <= tp[1], "and stops no later");
    }
}
