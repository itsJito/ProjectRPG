package rpg.ai;

import static org.junit.jupiter.api.Assertions.*;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import org.junit.jupiter.api.*;
import rpg.Ids;
import rpg.Rpg;
import rpg.actor.Appearance;
import rpg.world.Blocks;
import rpg.world.DevMap;

/**
 * Gamefixes: NPCs sometimes could not see the player right in front of them; they should be unsure of figures past ten
 * blocks but recognise anyone within ten in plain view, and through glass make out nothing past five blocks.
 */
class NpcSightTest {
    private Game game;
    private Rpg g;
    private Npc cole;
    private static final float X = DevMap.SPAWN_X, Z = DevMap.SPAWN_Z;

    @BeforeEach void open() {
        var cfg = new NativeEngine.Config(); cfg.deterministic = true;
        game = Game.create(cfg); game.activate(DevMap.build(game.engine()));
        g = game.add(new Rpg(game, "Sight", new Appearance(), true));
        cole = (Npc) g.actor(Ids.GUARD);
        g.state.minutes = 12 * 60;
        // an open strip, twenty-four blocks long, in the sun
        for (int z = (int) Z - 26; z <= (int) Z + 3; z++) for (int x = (int) X - 2; x <= (int) X + 2; x++)
            for (int y = DevMap.FLOOR; y < DevMap.FLOOR + 5; y++) g.setBlock(x, y, z, Blocks.AIR);
        game.tick(0);
    }
    @AfterEach void close() { game.close(); }

    /** The guard at the strip's head looking down it (-z), the player some way down it; how plainly he sees them. */
    private float seen(float ahead, float side, boolean sneaking) {
        g.teleport(cole, X + 0.5f, DevMap.FLOOR, Z + 0.5f); cole.yaw = 0;
        g.teleport(g.player, X + 0.5f + side, DevMap.FLOOR, Z + 0.5f - ahead);
        g.player.sneaking = sneaking; g.player.vx = g.player.vz = 0;
        return g.ai.stealth.exposure(cole, g.player);
    }

    @Test void anyoneInPlainViewWithinTenBlocksIsRecognisedAtOnce() {
        for (float d : new float[]{1, 3, 6, 9.5f}) {
            float e = seen(d, 0, false);
            assertTrue(e >= Stealth.CLEAR * 1.6f, "standing " + d + " m ahead the guard only half saw the player (" + e + ")");
        }
        // right beside him, behind his back: sensed all the same
        float behind = seen(-1.2f, 0, false);
        assertTrue(behind >= Stealth.CLEAR, "a body 1.2 m behind the guard went unnoticed (" + behind + ")");
    }

    @Test void figuresFartherOffAreLessCertain() {
        float near = seen(9, 0, false), far = seen(20, 0, false);
        System.out.printf("exposure at 9 m %.2f, at 20 m %.2f%n", near, far);
        assertTrue(far < Stealth.CLEAR * 1.6f, "a figure 20 m off is recognised outright (" + far + ")");
        assertTrue(far > 0, "a figure 20 m off in plain view is not seen at all");
        // a sneaking figure within ten is not given away by the rule
        assertTrue(seen(8, 0, true) < Stealth.CLEAR * 1.6f, "a creeping figure at 8 m is recognised outright");
    }

    @Test void throughGlassOnlyWithinFiveBlocks() {
        // a glass wall across the strip, two blocks in front of the guard
        int wz = (int) Z - 2;
        for (int x = (int) X - 2; x <= (int) X + 2; x++) for (int y = DevMap.FLOOR; y < DevMap.FLOOR + 4; y++) g.setBlock(x, y, wz, Blocks.GLASS);
        game.tick(0);
        float in = seen(4, 0, false), out = seen(7, 0, false);
        System.out.printf("through glass: at 4 m %.2f, at 7 m %.2f%n", in, out);
        assertTrue(in >= Stealth.CLEAR, "the guard could not make out the player 4 m off through a window (" + in + ")");
        assertEquals(0, out, 1e-6, "the guard made out the player 7 m off through glass");
    }
}
