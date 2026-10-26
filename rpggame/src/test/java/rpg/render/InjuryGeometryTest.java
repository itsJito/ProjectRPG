package rpg.render;

import astratto.voxel.VoxModel;
import org.junit.jupiter.api.Test;
import rpg.actor.*;
import rpg.combat.Gore;
import static org.junit.jupiter.api.Assertions.*;

class InjuryGeometryTest {
    @Test void humanCutsRecessTheMeshAndHealingRestoresItsOriginalVertices() {
        Actor actor=new Actor(71,"Surface test",40);actor.look=Appearance.henric();actor.restore();actor.hp=actor.maxHp()*0.2f;
        actor.injuries.cut(actor,actor.x,actor.y+1.1f,actor.z-actor.radius,6);
        Skin clean=Skin.paint(actor.look,null),damaged=Skin.paint(actor.look,null);damaged.injure(actor.injuries.snapshot(),actor.injuries.severity(actor));
        float[] base=CharModel.build(clean).base[CharModel.Seg.TORSO.ordinal()],cut=CharModel.build(damaged).base[CharModel.Seg.TORSO.ordinal()];
        float plane=Float.NaN;
        for(int i=0;i<base.length;i+=10)if(base[i+9]<-0.99f){plane=base[i+2];break;}
        boolean recessed=false;for(int i=0;i<cut.length;i+=10)if(cut[i+9]<-0.99f && cut[i+2]>plane+0.05f)recessed=true;
        assertTrue(recessed,"a front-facing cut must have actual recessed vertices, not only a color change");
        actor.restore();Skin healed=Skin.paint(actor.look,null);healed.injure(actor.injuries.snapshot(),actor.injuries.severity(actor));
        assertArrayEquals(base,CharModel.build(healed).base[CharModel.Seg.TORSO.ordinal()]);
    }
    @Test void animalCarvingRemovesVoxelsWithoutMutatingSharedSpeciesAssets() {
        VoxModel vox=new VoxModel(8,8,8);vox.box(0,0,0,7,7,7,0xFF807060);var original=new ItemModels.Model(vox,4,0,4);
        var wounds=java.util.List.of(new Injuries.Cut(Blood.TORSO,Blood.FRONT,0.5f,0.5f,2,1));
        var cut=VoxelInjuries.carve(original,Gore.Part.TORSO,wounds,8);
        assertEquals(512,java.util.Arrays.stream(vox.col).filter(color->color!=0).count());
        assertTrue(java.util.Arrays.stream(cut.vox().col).filter(color->color!=0).count()<512);
        assertSame(original,VoxelInjuries.carve(original,Gore.Part.TORSO,wounds,0));
    }
    @Test void anActualHorseFlankHitCarvesTheStruckSideRatherThanAHumanSizedCoordinate() {
        var horse=new rpg.animal.Animal(88,rpg.animal.Species.HORSE,false,false,40,34);horse.restore();horse.hp=horse.maxHp()*0.2f;
        assertEquals(astratto.ai.AgentDomain.ANIMAL,horse.domain);
        var figure=AnimalModels.of(horse.species,horse.variant);
        horse.injuries.cut(horse,horse.x+figure.bw*AnimalModels.PX/2,horse.y+figure.bodyY*AnimalModels.PX,horse.z,8);
        var wound=horse.injuries.snapshot().getFirst();assertEquals(Blood.TORSO,wound.part());assertEquals(Blood.RIGHT,wound.face());
        assertEquals(0.5f,wound.u(),0.05f);assertEquals(0.5f,wound.v(),0.05f);
        var damaged=VoxelInjuries.carve(figure.body,Gore.Part.TORSO,horse.injuries.snapshot(),horse.injuries.severity(horse));
        assertNotEquals(figure.body.vox().get(10,5,13),damaged.vox().get(10,5,13));
    }

}
