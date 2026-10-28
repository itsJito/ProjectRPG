package rpg.law;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import org.junit.jupiter.api.Test;
import rpg.Rpg;
import rpg.actor.Appearance;
import rpg.objects.Fixtures;
import rpg.world.DevMap;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Gamefixes: the bounty board's collision took far more room than the board. Walk the player into it face on and past
 * its end: the body must stop at the panel (not a block short of it), and pass freely half a metre beyond its edge.
 */
class BountyBoardCollisionTest {
    @Test void theBoardStopsABodyAtItsPanelAndNowhereElse() {
        var cfg = new NativeEngine.Config(); cfg.deterministic = true;
        try (Game game = Game.create(cfg)) {
            game.activate(DevMap.build(game.engine()));
            Rpg g = game.add(new Rpg(game, "Board", new Appearance(), true));
            for (var n : g.npcs) g.ai.unload(n);
            Fixtures.Board b = (Fixtures.Board) g.objects.all().stream().filter(o -> o instanceof Fixtures.Board).findFirst().orElseThrow();
            // face on: from 3 m in front, walking at the panel
            float stopped = walk(game, g, b.x, b.z + 3, 0, -1);
            float gap = stopped - b.z - Fixtures.Board.HALF_D - g.player.radius;
            System.out.printf("board at %.2f,%.2f: stopped %.2f from the panel face%n", b.x, b.z, gap);
            assertTrue(gap < 0.12f, "the body stopped " + gap + " m short of the panel");
            assertTrue(gap > -0.1f, "the body went " + -gap + " m into the panel");
            // just past its end: nothing there to stop it
            float past = walk(game, g, b.x + Fixtures.Board.HALF_W + g.player.radius + 0.15f, b.z + 3, 0, -1);
            assertTrue(past < b.z - Fixtures.Board.HALF_D - g.player.radius - 0.3f, "the body was stopped at " + past + " by empty air beside the board");
        }
    }

    /** Walks the player from (x, z) along (dx, dz) for three seconds; where it got to along z. */
    private static float walk(Game game, Rpg g, float x, float z, float dx, float dz) {
        g.teleport(g.player, x, DevMap.FLOOR, z);
        for (int i = 0; i < 20; i++) game.tick(1f / 60);
        for (int i = 0; i < 180; i++) {
            g.controller.in.forward = 1;
            g.controller.snapTo((float) Math.atan2(-dx, -dz));
            game.tick(1f / 60);
        }
        g.controller.in.forward = 0;
        return g.player.z;
    }
}
