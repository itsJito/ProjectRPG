package rpg.world;

import static org.junit.jupiter.api.Assertions.*;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import org.junit.jupiter.api.*;
import rpg.Ids;
import rpg.Rpg;
import rpg.actor.Appearance;
import rpg.ai.Npc;

/**
 * Gamefixes: destructible things (fences, windows...) should be replaced, repaired or rebuilt by the right people. A
 * window smashed in DevCity is remembered; once things settle the carpenter comes and puts the pane back. Nothing in the
 * wilds is anyone's to mend.
 */
class RepairsTest {
    private Game game;
    private Rpg g;
    @BeforeEach void open() {
        var cfg = new NativeEngine.Config(); cfg.deterministic = true;
        game = Game.create(cfg); game.activate(DevMap.build(game.engine()));
        g = game.add(new Rpg(game, "Repairs", new Appearance(), true));
        g.state.minutes = 10 * 60;
    }
    @AfterEach void close() { game.close(); }

    @Test void theCarpenterPutsABrokenWindowBack() {
        // a pane of the first house (its long wall's window)
        int x = DevMap.HOUSE1_X0, y = DevMap.FLOOR + 1, z = (DevMap.HOUSE1_Z0 + DevMap.HOUSE1_Z1) / 2;
        assertTrue(Blocks.glass(g.vox.block(x, y, z)), "a pane where the window is");
        int pane = g.vox.block(x, y, z);
        g.glass.shatter(x, y, z, x + 0.5f, y + 0.5f, z + 0.5f, 1, 0, 0, 1, null);
        game.tick(1f / 60);
        assertEquals(Blocks.AIR, g.vox.block(x, y, z));
        assertEquals(1, g.repairs.open().size(), "the broken pane is remembered");
        Npc arthur = (Npc) g.actor(Ids.ARTHUR);
        g.teleport(arthur, 88.5f, DevMap.FLOOR, 80.5f);
        g.teleport(g.player, x - 3.5f, DevMap.FLOOR, z - 6.5f);   // close by, so the street is simulated in full
        boolean mended = false;
        for (int s = 0; s < 120 && !mended; s++) { for (int i = 0; i < 60; i++) game.tick(1f / 60); mended = g.vox.block(x, y, z) == pane; if (s % 10 == 0) System.out.printf("t%d %s %s at %.1f,%.1f legs %s%n", s, arthur.goal, arthur.lastOutcome, arthur.x, arthur.z, arthur.goalReason); }
        float[] spot = g.repairs.open().isEmpty() ? new float[2] : g.repairs.standSpot(arthur, g.repairs.open().get(0));
        assertTrue(mended, "the carpenter mended the window (" + arthur.goal + ", " + arthur.lastOutcome + ") at " + arthur.x + "," + arthur.z + " spot " + spot[0] + "," + spot[1] + " stuck " + arthur.stuck + " loaded " + arthur.loaded + " | 85,90: solid " + g.vox.solid(85, DevMap.FLOOR, 90) + "/" + g.vox.solid(85, DevMap.FLOOR + 1, 90) + " under " + g.vox.solid(85, DevMap.FLOOR - 1, 90) + " sky " + g.vox.skyLight(85, DevMap.FLOOR + 1, 90) + " | 87,89 sky " + g.vox.skyLight(87, DevMap.FLOOR + 1, 89));
        assertTrue(g.repairs.open().stream().noneMatch(d->d.x()==x && d.y()==y && d.z()==z), "the repaired pane leaves the damage record");
    }

    @Test void theWildsAreNobodysToMend() {
        g.repairs.broken(10, DevMap.FLOOR, 10, Blocks.FENCE, "fire");
        assertTrue(g.repairs.open().isEmpty());
        g.repairs.broken(100, DevMap.FLOOR, 70, Blocks.FENCE, "fire");
        assertEquals(1, g.repairs.open().size(), "a fence burnt in DevCity is remembered");
    }
}
