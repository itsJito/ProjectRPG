package rpg.render;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import rpg.actor.Actor;
import rpg.actor.Body;

/**
 * Gamefixes: stopping a sprint suddenly, the figure kicked its feet outward. Run a sprint, brake it to a stand in a
 * few frames (as the controller does), and watch where each foot goes: it must stay under the body - never flung out
 * to the side, nor left stretched far ahead or behind once the body has stopped.
 */
class SprintStopTest {
    private static float[] footFigure(Rig rig, int k) {
        float[] shin = rig.seg[(k == 0 ? CharModel.Seg.R_LEG_LO : CharModel.Seg.L_LEG_LO).ordinal()];
        return new float[]{shin[12] - shin[4] * Body.SHIN, shin[13] - shin[5] * Body.SHIN, shin[14] - shin[6] * Body.SHIN};
    }

    @Test void stoppingASprintKeepsTheFeetUnderTheBody() {
        for (float brake : new float[]{0.08f, 0.2f, 0.45f}) {
            Actor a = new Actor(1, "Test", 1); a.grounded = true;
            Rig rig = new Rig(); Rig.State st = new Rig.State();
            float hipX = Body.of(a.look).hipX();
            int frame = 0;
            a.vz = -7.2f;
            for (; frame < 150; frame++) { a.z += a.vz / 60; rig.pose(a, st, 1f / 60, frame / 60f, false); }
            float worstSide = 0, worstReach = 0;
            for (int i = 0; i < 90; i++, frame++) {
                float t = i / 60f;
                a.vz = -7.2f * Math.max(0, 1 - t / brake);
                a.z += a.vz / 60;
                rig.pose(a, st, 1f / 60, frame / 60f, false);
                for (int k = 0; k < 2; k++) {
                    float[] f = footFigure(rig, k);
                    float side = (k == 0 ? 1 : -1) * f[0] - hipX;           // + outward from its own hip
                    worstSide = Math.max(worstSide, side);
                    if (i > 40) worstReach = Math.max(worstReach, Math.abs(f[2]));
                    float[] shin = rig.seg[(k == 0 ? CharModel.Seg.R_LEG_LO : CharModel.Seg.L_LEG_LO).ordinal()];
                    float knee = (k == 0 ? 1 : -1) * shin[12] - hipX;              // + the knee out from its hip
                    worstSide = Math.max(worstSide, knee);
                    if (i % 3 == 0) System.out.printf("  brake %.2f frame %2d foot %d  out %+5.2f  knee out %+5.2f  fwd %+6.2f  up %5.2f  stance %s%n", brake, i, k, side, knee, -f[2], f[1], st.stance[k]);
                }
            }
            System.out.printf("brake %.2f s: worst outward %.2f px, worst reach after stopping %.2f px%n", brake, worstSide, worstReach);
            assertTrue(worstSide < 2.0f, "a foot kicked " + worstSide + " px outward stopping in " + brake + " s");
            assertTrue(worstReach < 4.5f, "a foot left " + worstReach + " px from under the body after stopping in " + brake + " s");
        }
    }
}
