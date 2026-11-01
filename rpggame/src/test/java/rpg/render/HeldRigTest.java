package rpg.render;

import org.junit.jupiter.api.Test;
import rpg.actor.Actor;
import rpg.item.ItemDef;
import rpg.item.ItemStack;
import rpg.item.Items;

import static org.junit.jupiter.api.Assertions.*;

class HeldRigTest {
    @Test void twoHandedMeleeGripsStayOnOneHandleAcrossBodiesAndTheEntireSwing() {
        for(String weapon:new String[]{"bronze_2hsword","bronze_battleaxe","bronze_spear","bronze_halberd"})
            for(boolean female:new boolean[]{false,true})for(int body=0;body<3;body++)for(boolean fp:new boolean[]{false,true}) {
                Actor a=actor(weapon);a.look.female=female;a.look.bodyType=body;
                Rig rig=new Rig();rig.firstPerson=fp;Rig.State state=new Rig.State();
                for(var move:new Actor.Combat.Move[]{Actor.Combat.Move.LIGHT_R,Actor.Combat.Move.LIGHT_L,Actor.Combat.Move.HEAVY,Actor.Combat.Move.THRUST})
                    for(int frame=0;frame<=60;frame++) {
                        a.cs.move=move;a.cs.phase=frame==60?Actor.Combat.Phase.IDLE:Actor.Combat.Phase.ACTIVE;a.cs.sweep=frame/60f;
                        a.pitch=(frame/60f-.5f)*1.6f;rig.pose(a,state,1f/60,frame/60f,false);
                        float along=Rig.supportOffset(a.main());
                        for(int axis=0;axis<3;axis++)assertEquals(rig.hand[0][axis]+rig.itemDir[0][axis]*along,rig.hand[1][axis],.03f,
                                weapon+" "+move+" frame "+frame+" body "+body+" fp "+fp);
                    }
            }
    }
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
