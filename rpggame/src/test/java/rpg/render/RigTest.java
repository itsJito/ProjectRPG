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

    @Test void crouchBendsTheBodyToAnatomicalHeightWithoutScalingTheBones() {
        Actor a = standing(); a.sneaking = true;
        Rig rig = new Rig(); Rig.State state = new Rig.State();
        for (int frame = 0; frame < 120; frame++) rig.pose(a, state, 1f / 60, frame / 60f, false);
        float[] head = rig.seg[CharModel.Seg.HEAD.ordinal()];
        float highest = -Float.MAX_VALUE;
        for (int x : new int[]{-4, 4}) for (int y : new int[]{0, 8}) for (int z : new int[]{-4, 4}) {
            highest = Math.max(highest, head[1] * x + head[5] * y + head[9] * z + head[13]);
        }
        assertTrue(highest <= Body.HEIGHT_PX * Body.CROUCH_HEIGHT + 0.5f, "head top " + highest);
        assertTrue(rig.pelvis[1] > 5 && rig.pelvis[1] < 9, "a crouch bends the legs instead of shrinking them");
        assertEquals(1, Math.sqrt(head[0] * head[0] + head[1] * head[1] + head[2] * head[2]), 1e-5);
    }

    @Test void crouchWalkingKeepsBothKneesBentInsteadOfDraggingAStraightLeg() {
        Actor a = standing(); a.sneaking = true; a.vz = -1.45f;
        Rig rig = new Rig(); Rig.State state = new Rig.State();
        for (int frame = 0; frame < 180; frame++) {
            a.z += a.vz / 60; rig.pose(a, state, 1f / 60, frame / 60f, false);
            if (frame < 60) continue;
            for (int side = 0; side < 2; side++) {
                float[] hip = rig.seg[(side == 0 ? CharModel.Seg.R_LEG_UP : CharModel.Seg.L_LEG_UP).ordinal()];
                float[] shin = rig.seg[(side == 0 ? CharModel.Seg.R_LEG_LO : CharModel.Seg.L_LEG_LO).ordinal()];
                double distance = 0;
                for (int axis = 0; axis < 3; axis++) { double d = shin[12 + axis] - shin[4 + axis] * Body.SHIN - hip[12 + axis]; distance += d * d; }
                assertTrue(distance < 100, "crouch steps must retain knee flexion: " + Math.sqrt(distance));
            }
        }
    }

    @Test void firstPersonBareGuardKeepsBothFistsInsideTheViewAndWithinArmReach() {
        Actor a = standing(); a.cs.blocking = true;
        Rig rig = new Rig(); rig.firstPerson = true; Rig.State st = new Rig.State();
        for (int i = 0; i < 60; i++) rig.pose(a, st, 1f / 60, i / 60f, false);
        for (int hand = 0; hand < 2; hand++) {
            float ahead = rig.eye[2] - rig.hand[hand][2] - 0.8f;
            float below = rig.eye[1] - rig.hand[hand][1];
            assertTrue(ahead > 4 && below > 0 && below / ahead < 0.5f, "fists should frame the lower view, not disappear below it");
            assertTrue(Math.abs(rig.hand[hand][0] - rig.eye[0]) / ahead < 0.8f);
            float[] arm = rig.seg[(hand == 0 ? CharModel.Seg.R_ARM_UP : CharModel.Seg.L_ARM_UP).ordinal()];
            double reach = 0;
            for (int axis = 0; axis < 3; axis++) reach += Math.pow(rig.hand[hand][axis] - arm[12 + axis], 2);
            assertTrue(Math.sqrt(reach) <= Body.UPPER_ARM + Body.FOREARM + 0.001f);
        }
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
    @Test void freeRunningArmsCounterSwingAgainstTheLegs() {
        for (boolean female : new boolean[]{false, true}) {
            Actor a = standing(); a.vz = -5; a.look.female = female;
            Rig rig = new Rig(); Rig.State state = new Rig.State();
            double correlation = 0;
            for (int i = 0; i < 300; i++) {
                a.z += a.vz / 60;
                rig.pose(a, state, 1f / 60, i / 60f, false);
                if (i > 90) correlation += (rig.hand[0][2] - rig.hand[1][2]) * (state.foot[0][2] - state.foot[1][2]);
            }
            assertTrue(correlation < 0, "a forward leg needs a backward arm, rather than the same-side jab: " + correlation);
        }
    }
}
