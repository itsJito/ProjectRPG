package rpg.combat;

import static astratto.bridge.AstrattoNative.*;
import static org.junit.jupiter.api.Assertions.*;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import org.junit.jupiter.api.*;
import rpg.Rpg;
import rpg.actor.Appearance;
import rpg.world.DevMap;

/**
 * Gamefixes: a player put in Sydney's hands (thrown down, a long fall, a shock) could not bring themselves upright and
 * was left in a glitched stuck state. Each way down must end with the player back on their feet, on the ground, in
 * control and able to walk away.
 */
class PlayerGetUpTest {
    private Game game;
    private Rpg g;
    @BeforeEach void start() {
        var config = new NativeEngine.Config(); config.deterministic = true;
        game = Game.create(config); game.activate(DevMap.build(game.engine()));
        g = game.add(new Rpg(game, "Getup", Appearance.henric(), true));
        g.state.minutes = 600;
        g.teleport(g.player, 60.5f, DevMap.FLOOR, 70.5f);
        game.tick(0);
        frames(30);
    }
    @AfterEach void close() { game.close(); }
    private void frames(int n) { for (int i = 0; i < n; i++) assertTrue(game.tick(1f / 60)); }

    /** Runs until the player is handed back (or the limit), printing Sydney's state as it changes; seconds taken. */
    private double untilUp(String what, double limit) {
        double start = g.time; int lastState = -1, lastOutcome = -1;
        while (g.player.reacting && g.time - start < limit) {
            frames(1);
            var rd = g.lastBodies == null || g.player.ragdoll == 0 ? null : g.lastBodies.ragdoll(g.player.ragdoll);
            if (rd != null && (rd.state != lastState || rd.outcome != lastOutcome)) {
                System.out.printf("  %s t=%.2f state=%d outcome=%d com=(%.2f %.2f %.2f) grounded=%d%n", what, g.time - start, rd.state, rd.outcome, rd.comX, rd.comY, rd.comZ, rd.grounded);
                lastState = rd.state; lastOutcome = rd.outcome;
            }
        }
        System.out.printf("  %s handed back after %.2f s: reacting=%s alive=%s hp=%s y=%.2f%n", what, g.time - start, g.player.reacting, g.player.alive, g.player.hp, g.player.y);
        return g.time - start;
    }

    private void assertBackInControl(String what, double took) {
        assertFalse(g.player.reacting, what + ": still in Sydney's hands after " + took + " s");
        assertEquals(0, g.player.ragdoll, what + ": ragdoll left behind");
        assertTrue(took < 9, what + ": took " + took + " s to get up (expected a get-up, not the time-out)");
        frames(20);
        float floor = g.vox.groundY(g.player.x, g.player.y + 1.5f, g.player.z);
        assertTrue(Math.abs(g.player.y - floor) < 0.3f, what + ": standing at " + g.player.y + " over ground " + floor);
        // and the body walks again
        float x0 = g.player.x, z0 = g.player.z;
        for (int i = 0; i < 90; i++) { g.controller.in.forward = 1; frames(1); }
        g.controller.in.forward = 0;
        assertTrue(Math.hypot(g.player.x - x0, g.player.z - z0) > 0.8, what + ": cannot walk after getting up");
    }

    @Test void thrownDownByAForcewave() {
        g.reactions.falling(g.player, 1);
        g.game.commands().ragdollHit(g.player.ragdoll, RD_PELVIS, 1, 0.15f, 0, 120, 24);
        assertBackInControl("forcewave", untilUp("forcewave", 20));
    }

    @Test void aLongFall() {
        g.player.skills.setLevel(rpg.skill.Skill.HEALTH, 99); g.player.restore();
        g.teleport(g.player, 60.5f, DevMap.FLOOR + 14, 70.5f);
        double start = g.time;
        while (!g.player.reacting && g.time - start < 4) frames(1);
        assertTrue(g.player.reacting, "a 14 m drop hands the body to Sydney");
        g.player.hp = g.player.maxHp();
        assertBackInControl("fall", untilUp("fall", 20));
    }

    @Test void shocked() {
        for (int i = 0; i < 40; i++) { g.reactions.shocked(g.player); frames(1); }
        assertBackInControl("shock", untilUp("shock", 20));
    }
}
