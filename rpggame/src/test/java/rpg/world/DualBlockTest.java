package rpg.world;

import static astratto.bridge.AstrattoNative.*;
import static org.junit.jupiter.api.Assertions.*;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import org.junit.jupiter.api.*;
import rpg.Rpg;
import rpg.actor.Appearance;

/**
 * Gamefixes 0.2.1: blocks with two halves - one material and texture outside, another inside - so a house's walls are
 * brick without and plaster within in a single layer of blocks, each half behaving as its material: fire burns the
 * wooden half away and leaves the plaster (or the iron) standing.
 */
class DualBlockTest {
    private Game game;
    private Rpg g;
    @BeforeEach void start() {
        var cfg = new NativeEngine.Config(); cfg.deterministic = true; game = Game.create(cfg); game.activate(DevMap.build(game.engine()));
        g = game.add(new Rpg(game, "Dual", Appearance.henric(), true)); for (var n : g.npcs) g.ai.unload(n);
        g.timeSystem.weather.set(astratto.weather.Weather.Kind.CLEAR); g.timeSystem.weather.snap();
    }
    @AfterEach void close() { game.close(); }

    @Test void eachHalfIsItsOwnMaterial() {
        int b = Blocks.dualOf(Blocks.BRICK, Blocks.PLASTER, Blocks.IN_PX);
        assertTrue(Blocks.dual(b));
        assertEquals(Blocks.BRICK, Blocks.outerOf(b));
        assertEquals(Blocks.PLASTER, Blocks.innerOf(b));
        assertEquals(Blocks.IN_PX, Blocks.innerDir(b));
        assertTrue(Blocks.traversable(b));
        assertEquals(Blocks.Material.STONE, Blocks.material(b));
        assertEquals(Blocks.PLASTER, Blocks.survivorOf(Blocks.dualOf(Blocks.PLASTER, Blocks.PLANKS, Blocks.IN_NZ)));
        assertEquals(Blocks.IRON_BLOCK, Blocks.survivorOf(Blocks.dualOf(Blocks.IRON_BLOCK, Blocks.PLANKS, Blocks.IN_PZ)));
        // every pair in each direction is its own id, and none is past the engine's 256
        java.util.Set<Integer> ids = new java.util.HashSet<>();
        for (int[] pair : Blocks.DUAL_PAIRS) for (int d = 0; d < 4; d++) ids.add(Blocks.dualOf(pair[0], pair[1], d));
        assertEquals(Blocks.DUAL_PAIRS.length * 4, ids.size());
        assertTrue(Blocks.COUNT <= 256);
    }

    @Test void fireBurnsTheWoodenHalfAndLeavesTheOther() {
        int y = 12;
        int plasterPlanks = Blocks.dualOf(Blocks.PLASTER, Blocks.PLANKS, Blocks.IN_PX), ironPlanks = Blocks.dualOf(Blocks.IRON_BLOCK, Blocks.PLANKS, Blocks.IN_PX);
        int brickPlaster = Blocks.dualOf(Blocks.BRICK, Blocks.PLASTER, Blocks.IN_PX);
        g.setBlock(100, y, 70, plasterPlanks); g.setBlock(104, y, 70, ironPlanks); g.setBlock(108, y, 70, brickPlaster);
        game.tick(0);
        for (int x : new int[]{100, 104, 108}) g.chemistry.emit(g.player, CHEM_FIRE, x + 0.5f, y + 0.5f, 70.5f, 0.3f, 3, 0.5f);
        float ironHeat = 0;
        for (int i = 0; i < 60 * 40; i++) {
            assertTrue(game.tick(1f / 60));
            for (var c : g.chemistry.active) if ((int) Math.floor(c.x()) == 104 && (int) Math.floor(c.z()) == 70) ironHeat = Math.max(ironHeat, c.heat());
        }
        System.out.printf("plaster|planks -> %d, iron|planks -> %d (hottest %.2f), brick|plaster -> %d%n", g.vox.block(100, y, 70), g.vox.block(104, y, 70), ironHeat, g.vox.block(108, y, 70));
        assertEquals(Blocks.PLASTER, g.vox.block(100, y, 70), "the planks behind the plaster burnt the plaster away too");
        assertEquals(Blocks.IRON_BLOCK, g.vox.block(104, y, 70), "the iron did not survive its wooden half");
        assertTrue(ironHeat > 0.3f, "the iron never grew hot");
        assertEquals(brickPlaster, g.vox.block(108, y, 70), "brick and plaster caught fire");
    }
}
