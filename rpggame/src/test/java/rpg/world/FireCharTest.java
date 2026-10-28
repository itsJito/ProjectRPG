package rpg.world;

import static astratto.bridge.AstrattoNative.*;
import static org.junit.jupiter.api.Assertions.*;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import org.junit.jupiter.api.*;
import rpg.Rpg;
import rpg.actor.Appearance;

/**
 * Gamefixes: blocks that are not cubes turned into a block when on fire, and fire put one premade char texture on
 * everything. A fence burns in its own shape - blackening post by post - and then is gone, never a block of char; planks
 * and a log char into their own charred twins, their grain under the soot.
 */
class FireCharTest {
    private Game game;
    private Rpg g;
    @BeforeEach void start() {
        var cfg = new NativeEngine.Config(); cfg.deterministic = true; game = Game.create(cfg); game.activate(DevMap.build(game.engine()));
        g = game.add(new Rpg(game, "Char", Appearance.henric(), true)); for (var n : g.npcs) g.ai.unload(n);
        g.timeSystem.weather.set(astratto.weather.Weather.Kind.CLEAR); g.timeSystem.weather.snap();
    }
    @AfterEach void close() { game.close(); }

    @Test void aFenceBurnsInItsOwnShapeAndWoodCharsIntoItsOwnTwin() {
        int y = 12;
        g.setBlock(100, y, 70, Blocks.FENCE); g.setBlock(101, y, 70, Blocks.FENCE);
        g.setBlock(104, y, 70, Blocks.PLANKS); g.setBlock(106, y, 70, Blocks.OAK_LOG);
        game.tick(0);
        for (float[] at : new float[][]{{100.5f, 70.5f}, {104.5f, 70.5f}, {106.5f, 70.5f}}) g.chemistry.emit(g.player, CHEM_FIRE, at[0], y + 0.5f, at[1], 0.3f, 3, 0.5f);
        float charSeen = 0;
        boolean fenceCubed = false;
        for (int i = 0; i < 60 * 40; i++) {
            assertTrue(game.tick(1f / 60));
            charSeen = Math.max(charSeen, g.chemistry.fenceChar(100, y, 70));
            int b = g.vox.block(100, y, 70);
            fenceCubed |= b != Blocks.FENCE && b != Blocks.AIR;
        }
        int planks = g.vox.block(104, y, 70), log = g.vox.block(106, y, 70), fence = g.vox.block(100, y, 70);
        System.out.printf("fence %d (char seen %.2f), planks %d, oak log %d%n", fence, charSeen, planks, log);
        assertFalse(fenceCubed, "a burning fence turned into a block");
        assertTrue(charSeen > 0.2f, "the fence never blackened as it burned");
        assertEquals(Blocks.CHARRED_PLANKS, planks, "the planks charred into their own twin");
        assertEquals(Blocks.charredOf(Blocks.OAK_LOG), log, "the oak log charred into its own twin");
        assertTrue(Blocks.charred(planks) && Blocks.material(planks) == Blocks.Material.WOOD);
    }
}
