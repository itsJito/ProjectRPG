package rpg.render;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import rpg.actor.Actor;
import rpg.actor.Body;
import rpg.item.ItemDef;
import rpg.item.ItemStack;
import rpg.item.Items;

/**
 * Gamefixes 0.2.1: the third-person bow draw was only tidied, not reworked. At full draw the bow arm reaches straight out
 * toward the target at the height of the shoulder, the bow at arm's length; the string hand is drawn back to the face
 * (by the jaw), level with the line of the shot.
 */
class BowDrawTest {
    private static float[] end(float[] seg, float len) { return new float[]{seg[12] + seg[4] * len, seg[13] + seg[5] * len, seg[14] + seg[6] * len}; }

    @Test void aFullDrawIsArmStraightOutAndStringToTheFace() {
        Actor a = new Actor(1, "Archer", 1); a.grounded = true;
        a.eq.set(ItemDef.Slot.MAIN, new ItemStack(Items.SHORTBOW, 1));
        a.cs.draw = 1;
        Rig rig = new Rig(); Rig.State st = new Rig.State();
        for (int f = 0; f < 90; f++) rig.pose(a, st, 1f / 60, f / 60f, false);
        float[] bowUp = rig.seg[CharModel.Seg.L_ARM_UP.ordinal()], bowLo = rig.seg[CharModel.Seg.L_ARM_LO.ordinal()];
        float[] shoulder = {bowUp[12], bowUp[13], bowUp[14]};
        float[] bowHand = rig.hand[1], drawHand = rig.hand[0], head = rig.seg[CharModel.Seg.HEAD.ordinal()];
        float reach = (float) Math.sqrt(Math.pow(bowHand[0] - shoulder[0], 2) + Math.pow(bowHand[1] - shoulder[1], 2) + Math.pow(bowHand[2] - shoulder[2], 2));
        float arm = Body.UPPER_ARM + Body.FOREARM;
        float headY = head[13] + 2;   // the jaw: a little up the head from the neck
        System.out.printf("shoulder %.1f %.1f %.1f  bow hand %.1f %.1f %.1f (reach %.1f of %.1f)  draw hand %.1f %.1f %.1f  jaw y %.1f%n",
                shoulder[0], shoulder[1], shoulder[2], bowHand[0], bowHand[1], bowHand[2], reach, arm, drawHand[0], drawHand[1], drawHand[2], headY);
        assertTrue(reach > arm * 0.9f, "the bow arm is bent, not out straight: " + reach + " of " + arm);
        assertEquals(shoulder[1], bowHand[1], 1.6f, "the bow hand is not at the shoulder's height");
        assertEquals(headY, drawHand[1], 2.2f, "the string hand is not at the face");
        float dx = drawHand[0] - head[12], dz = drawHand[2] - head[14];
        assertTrue(Math.hypot(dx, dz) < 5.5f, "the string hand is not by the face: " + Math.hypot(dx, dz) + " px from the head");
    }
}
