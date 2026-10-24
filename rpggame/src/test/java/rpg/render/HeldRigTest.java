package rpg.render;

import org.junit.jupiter.api.Test;
import rpg.actor.Actor;
import rpg.item.ItemDef;
import rpg.item.ItemStack;
import rpg.item.Items;

import static org.junit.jupiter.api.Assertions.*;

class HeldRigTest {
    private static Actor actor(String weapon) {
        Actor a = new Actor(1, "Held pose", 1); a.grounded = true;
        a.eq.set(ItemDef.Slot.MAIN, new ItemStack(Items.get(weapon), 1));
        return a;
    }

    private static Rig pose(Actor a) {
        Rig rig = new Rig(); rig.firstPerson = true;
        Rig.State state = new Rig.State();
        for (int i = 0; i < 60; i++) rig.pose(a, state, 1f / 60, i / 60f, false);
        return rig;
    }

    @Test void aRestingBladeKeepsItsViewAngleWhenLookingUpOrDown() {
        for (float pitch : new float[]{-1.2f, -0.7f, 0, 0.7f, 1.2f}) {
            Actor a = actor("iron_sword"); a.pitch = pitch;
            Rig rig = pose(a);
            float alongAim = rig.itemDir[0][1] * (float) Math.sin(pitch) - rig.itemDir[0][2] * (float) Math.cos(pitch);
            assertEquals(0.9f / (float) Math.sqrt(0.9f * 0.9f + 0.8f * 0.8f + 0.3f * 0.3f), alongAim, 1e-4f);
        }
    }

    @Test void aCrossbowPointsAlongTheAimRatherThanUpLikeASword() {
        Actor a = actor("bronze_crossbow"); a.pitch = -0.7f;
        Rig rig = pose(a);
        assertEquals((float) Math.sin(a.pitch), rig.itemDir[0][1], 1e-4f);
        assertEquals(-(float) Math.cos(a.pitch), rig.itemDir[0][2], 1e-4f);
    }

    @Test void theBowIsUprightInTheSupportHandWhileTheOtherHandDrawsTheString() {
        Actor a = actor("oak_shortbow");
        for (float draw : new float[]{0, 0.8f}) {
            a.cs.draw = draw;
            Rig rig = pose(a);
            assertTrue(rig.itemDir[1][1] > 0.9f);
            float forward = rig.eye[2] - rig.hand[1][2];
            assertTrue(forward > 0 && Math.abs(rig.hand[1][0] - rig.eye[0]) < forward * 0.9f,
                    "the support grip must remain inside the first-person horizontal view");
            if (draw > 0) assertTrue(rig.hand[0][2] > rig.hand[1][2], "string hand draws back from the bow hand");
        }
    }
    @Test void thirdPersonAxesAreCarriedUpAndAheadAndCrossbowsHaveTwoHandSupport() {
        Actor a = actor("bronze_axe"); Rig rig = new Rig(); Rig.State state = new Rig.State();
        rig.pose(a, state, 1f / 60, 0, false);
        assertTrue(rig.itemDir[0][1] > 0 && rig.itemDir[0][2] < 0, "the haft must point up and forward");
        a = actor("bronze_crossbow"); a.cs.aim = 1;
        rig.pose(a, new Rig.State(), 1f / 60, 0, false);
        assertTrue(rig.hand[1][2] < rig.hand[0][2], "the support hand must be ahead of the trigger grip");
        assertTrue(rig.itemDir[0][2] < -0.99f);
    }
}
