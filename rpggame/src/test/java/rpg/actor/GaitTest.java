package rpg.actor;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GaitTest {
    @Test
    void walkingKeepsDoubleSupportAndRunningHasFlight() {
        assertTrue(Gait.stance(2.1f, false) > 0.5f);
        assertTrue(Gait.stance(1.45f, true) > 0.5f);
        assertTrue(Gait.stance(4.6f, false) < 0.5f);
        assertTrue(Gait.stance(7.8f, false) < Gait.stance(4.6f, false));
    }

    @Test
    void plantedLegCanReachAcrossEachStanceWithoutSpeedingUpTheSprint() {
        for (boolean sneaking : new boolean[]{false, true}) {
            for (float speed : new float[]{0.8f, 1.45f, 2.1f, 2.3f, 3.2f, 4.6f, 7.8f}) {
                float cadence = Gait.cadence(speed, sneaking);
                if (speed >= 1.45f) assertTrue(cadence >= 1f / 1.5f && cadence <= 2,
                        "locomotion cycles should take 0.5–1.5 seconds");
                float travelled = speed * Gait.stance(speed, sneaking) / cadence;
                assertTrue(travelled <= 2 * Gait.halfReach(sneaking) * Body.PX + 0.0001f,
                        "a planted leg must reach from the start of contact to the end");
            }
        }
    }
}
