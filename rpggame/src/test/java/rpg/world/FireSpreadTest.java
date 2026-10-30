package rpg.world;

import static astratto.bridge.AstrattoNative.*;
import static org.junit.jupiter.api.Assertions.*;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import org.junit.jupiter.api.*;
import rpg.Rpg;
import rpg.actor.Appearance;

/**
 * Gamefixes 0.2.1: fire raced through a whole wooden building in a few seconds, and the soot it left hung in the air
 * where a block had burnt or broken away. A plank wall lit at one end now takes the better part of a minute to burn
 * along, and a block taken away takes its soot with it.
 */
class FireSpreadTest {
    private Game game;
    private Rpg g;
    @BeforeEach void start() {
        var cfg = new NativeEngine.Config(); cfg.deterministic = true; game = Game.create(cfg); game.activate(DevMap.build(game.engine()));
        g = game.add(new Rpg(game, "Spread", Appearance.henric(), true)); for (var n : g.npcs) g.ai.unload(n);
        g.timeSystem.weather.set(astratto.weather.Weather.Kind.CLEAR); g.timeSystem.weather.snap();
    }
    @AfterEach void close() { game.close(); }

    private int caught(int y) {
        int n = 0;
        for (int x = 100; x < 112; x++) for (int dy = 0; dy < 3; dy++) { int b = g.vox.block(x, y + dy, 70); if (b != Blocks.PLANKS) n++; }
        for (var c : g.chemistry.active) if ((c.state() & 2) != 0 && c.z() > 70 && c.z() < 71 && c.y() >= y && c.y() < y + 3) n++;
        return n;
    }

    @Test void aPlankWallBurnsAlongOverAMinuteNotInSeconds() {
        int y = 12;
        for (int x = 100; x < 112; x++) for (int dy = 0; dy < 3; dy++) g.setBlock(x, y + dy, 70, Blocks.PLANKS);
        game.tick(0);
        g.chemistry.emit(g.player, CHEM_FIRE, 100.5f, y + 0.5f, 70.5f, 0.3f, 3, 0.5f);
        int at6 = 0, at60 = 0;
        for (int i = 1; i <= 60 * 60; i++) {
            assertTrue(game.tick(1f / 60));
            if (i == 60 * 6) at6 = caught(y);
            if (i == 60 * 60) at60 = caught(y);
        }
        System.out.printf("cells caught or charred of 36: %d after 6 s, %d after 60 s; soot laid %d%n", at6, at60, g.chemistry.sootCount);
        assertTrue(at6 <= 9, "fire took " + at6 + " of the wall's 36 cells in 6 s");
        assertTrue(at60 > at6, "the fire never spread along the wall");
    }

    @Test void aBlockTakenAwayTakesItsSootWithIt() {
        int y = 12;
        g.setBlock(104, y, 70, Blocks.PLANKS); g.setBlock(105, y, 70, Blocks.PLANKS);
        game.tick(0);
        g.chemistry.emit(g.player, CHEM_FIRE, 104.5f, y + 0.5f, 70.5f, 0.3f, 3, 0.5f);
        for (int i = 0; i < 60 * 8 && g.chemistry.soot.isEmpty(); i++) assertTrue(game.tick(1f / 60));
        assertFalse(g.chemistry.soot.isEmpty(), "the burning planks laid no soot");
        g.setBlock(104, y, 70, Blocks.AIR); g.setBlock(105, y, 70, Blocks.AIR);
        for (var st : g.chemistry.soot) {
            int bx = (int) Math.floor(st.x() - st.nx() * 0.6f), by = (int) Math.floor(st.y() - st.ny() * 0.6f), bz = (int) Math.floor(st.z() - st.nz() * 0.6f);
            assertFalse(bz == 70 && by == y && (bx == 104 || bx == 105), "soot left hanging where a block was: " + st);
        }
        assertFalse(g.chemistry.gone.isEmpty(), "the renderer was not told to take the marks down");
    }
}
