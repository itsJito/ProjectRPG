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
 * Gamefixes: arrows lodged in the edges of a body twitched. The frame an arrow in the shoulders rides in must turn
 * smoothly with the body (no flips), and a pose gone a little stale must not drop it to the feet for a frame.
 */
class LodgedFrameTest {
    private Game game;
    private Rpg g;
    @BeforeEach void open() {
        var cfg = new NativeEngine.Config(); cfg.deterministic = true;
        game = Game.create(cfg); game.activate(DevMap.build(game.engine()));
        g = game.add(new Rpg(game, "Lodged", new Appearance(), true));
    }
    @AfterEach void close() { game.close(); }

    /** A standing pose turned to a yaw, its shoulder line along the body's right. */
    private void pose(float yaw) {
        var p = g.player;
        p.yaw = yaw;
        float rx = (float) Math.cos(yaw), rz = -(float) Math.sin(yaw);
        float[] pts = new float[RD_PARTICLES * 3];
        for (int i = 0; i < RD_PARTICLES; i++) { pts[i * 3] = p.x; pts[i * 3 + 1] = p.y + 1; pts[i * 3 + 2] = p.z; }
        set(pts, RD_PELVIS, p.x, p.y + 0.95f, p.z);
        set(pts, RD_NECK, p.x, p.y + 1.5f, p.z);
        set(pts, RD_HEAD, p.x, p.y + 1.7f, p.z);
        set(pts, RD_L_SHOULDER, p.x - rx * 0.2f, p.y + 1.42f, p.z - rz * 0.2f);
        set(pts, RD_R_SHOULDER, p.x + rx * 0.2f, p.y + 1.42f, p.z + rz * 0.2f);
        p.posePts = pts; p.poseAt = g.time; p.poseX = Float.NaN; p.poseReset = false;
    }
    private static void set(float[] pts, int i, float x, float y, float z) { pts[i * 3] = x; pts[i * 3 + 1] = y; pts[i * 3 + 2] = z; }

    @Test void anArrowInTheShouldersTurnsSmoothlyWithTheBody() {
        float[] last = null;
        float worst = 0;
        for (int k = 0; k <= 200; k++) {
            pose(k * 0.01f);
            float[] f = HitVolumes.attachmentFrame(g, g.player, RD_L_SHOULDER, RD_R_SHOULDER);
            if (last != null) {
                float d = (float) Math.sqrt(Math.pow(f[3] - last[3], 2) + Math.pow(f[4] - last[4], 2) + Math.pow(f[5] - last[5], 2));
                worst = Math.max(worst, d);
            }
            last = f;
        }
        assertTrue(worst < 0.05f, "the frame's side axis jumped by " + worst + " for a turn of 0.01 rad");
    }

    @Test void aStalePoseStillCarriesTheArrow() {
        pose(0.3f);
        float[] fresh = HitVolumes.attachmentFrame(g, g.player, RD_NECK, RD_PELVIS);
        g.player.poseAt = g.time - 0.5;   // not drawn for half a second
        float[] stale = HitVolumes.attachmentFrame(g, g.player, RD_NECK, RD_PELVIS);
        assertEquals(fresh[1], stale[1], 1e-3, "the arrow dropped to the feet while the pose was stale");
    }
}
