package rpg.render;

import org.junit.jupiter.api.Test;
import rpg.actor.Actor;
import rpg.actor.Body;

import static org.junit.jupiter.api.Assertions.*;

class RigTest {
    @Test void pausedRenderingDoesNotAdvanceTheWalkingPose() {
        Actor a = standing(); a.vz = -2.45f;
        Rig rig = new Rig(); Rig.State state = new Rig.State();
        for (int i = 0; i < 90; i++) { a.z += a.vz / 60; rig.pose(a, state, 1f / 60, i / 60f, false); }
        float[][] pose = java.util.Arrays.stream(rig.seg).map(float[]::clone).toArray(float[][]::new);
        for (int i = 0; i < 600; i++) rig.pose(a, state, 0, 89 / 60f, false);
        for (int i = 0; i < pose.length; i++) assertArrayEquals(pose[i], rig.seg[i], 1e-5f);
    }

    private static Actor standing() {
        Actor actor = new Actor(1, "Test", 1);
        actor.grounded = true;
        return actor;
    }

    private static Rig.State lowered(Rig rig, Actor actor) {
        Rig.State state = new Rig.State();
        rig.pose(actor, state, 1f / 60, 0, false);
        // The pelvis is displaced after a landing or a change of stance.
        state.hipH = 8;
        state.hipV = 0;
        return state;
    }

    @Test
    void pelvisSettlesWithoutExplodingAtTenFramesPerSecond() {
        Rig rig = new Rig();
        Actor actor = standing();
        Rig.State state = lowered(rig, actor);
        for (int i = 1; i <= 30; i++) {
            rig.pose(actor, state, 0.1f, i * 0.1, false);
            assertTrue(rig.pelvis[1] >= 8 && rig.pelvis[1] <= Body.LEGS,
                    "a slow frame must not throw the hips outside the body: " + rig.pelvis[1]);
            for (float[] segment : rig.seg) for (float value : segment) assertTrue(Float.isFinite(value));
        }
        assertEquals(Body.LEGS - 0.35f, rig.pelvis[1], 0.001f);
    }

    @Test
    void restingPelvisConvergesEquallyAcrossFramePartitions() {
        Actor actor = standing();
        Rig slow = new Rig(), fast = new Rig();
        Rig.State a = lowered(slow, actor), b = lowered(fast, actor);
        slow.pose(actor, a, 0.1f, 0.1, false);
        for (int i = 1; i <= 10; i++) fast.pose(actor, b, 0.01f, i * 0.01, false);
        assertEquals(fast.pelvis[1], slow.pelvis[1], 0.0001f);
        assertEquals(b.hipV, a.hipV, 0.0001f);
    }

    @Test
    void changingGaitsKeepsTheBodyFiniteAcrossUnevenFrames() {
        Rig rig = new Rig();
        Actor actor = standing();
        Rig.State state = new Rig.State();
        double time = 0;
        float[] frameTimes = {1f / 144, 1f / 60, 0.04f, 0.1f};
        for (float speed : new float[]{2.1f, 4.6f, 7.8f, 0, 1.45f, 0}) {
            for (int i = 0; i < 80; i++) {
                float dt = frameTimes[i % frameTimes.length];
                actor.vz = -speed;
                actor.z += actor.vz * dt;
                time += dt;
                rig.pose(actor, state, dt, time, false);
                assertTrue(rig.pelvis[1] > 0 && rig.pelvis[1] <= Body.LEGS,
                        "hips must stay within the body through a gait transition");
                for (float[] segment : rig.seg) for (float value : segment) assertTrue(Float.isFinite(value));
            }
        }
    }
}
