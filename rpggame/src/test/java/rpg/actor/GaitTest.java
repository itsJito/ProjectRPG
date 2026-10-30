package rpg.actor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GaitTest {
    @Test
    void walkingKeepsDoubleSupportAndRunningHasFlight() {
        float walk = new rpg.Config().walkSpeed;
        assertEquals(0, Gait.runShare(walk), "the configured walk must remain a walk");
        assertTrue(Gait.stance(walk, false) > 0.5f);
        assertTrue(Gait.stance(1.45f, true) > 0.5f);
        assertTrue(Gait.stance(4.6f, false) < 0.5f);
        assertTrue(Gait.stance(7.8f, false) < Gait.stance(4.6f, false));
    }

    @Test
    void plantedLegCanReachAcrossEachStanceAndOnlyTheSprintQuickensPastTheHalfSecondLoop() {
        for (boolean sneaking : new boolean[]{false, true}) {
            for (float speed : new float[]{0.8f, 1.45f, 2.1f, 2.45f, 2.6f, 3.5f, 4.6f, 7.8f, 8.2f}) {
                float cadence = Gait.cadence(speed, sneaking);
                // (gamefixes 0.2.0: the 0.5 - 1.5 s rule need not hold for the sprint, which bounded at two cycles a second;
                // 0.2.1: nor for the jog - at two a second its steps were lunges on the figure's short legs; 0.2.3: 2.8 a second at a jog)
                if (speed >= 1.45f) assertTrue(cadence >= 1f / 1.5f && cadence <= 2.85f + 0.65f * Gait.sprintShare(speed) + 1e-4f,
                        "locomotion cycles should take 0.4-1.5 seconds (a sprint may be quicker)");
                if (speed >= 7.8f) assertTrue(speed / cadence < 3.0f, "a full sprint's stride is under three metres a cycle (" + speed / cadence + ")");
                float travelled = speed * Gait.stance(speed, sneaking) / cadence;
                assertTrue(travelled <= 2 * Gait.halfReach(sneaking) * Body.PX + 0.0001f,
                        "a planted leg must reach from the start of contact to the end");
            }
        }
    }
}
