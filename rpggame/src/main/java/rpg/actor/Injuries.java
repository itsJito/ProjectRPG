package rpg.actor;

import java.util.ArrayList;
import java.util.List;

/** Bounded surface injuries. Missing health controls their depth; healing rebuilds the original surface. */
public final class Injuries {
    public record Cut(int part,int face,float u,float v,float radius,int seed) {}
    private final List<Cut> cuts=new ArrayList<>();
    private int serial;
    public void cut(Actor actor,float x,float y,float z,int damage) {
        float[] location=actor instanceof rpg.animal.Animal animal?animalLocation(animal,x,y,z):Blood.locate(actor,x,y,z);
        if(cuts.size()==10)cuts.removeFirst();
        cuts.add(new Cut((int)location[0],(int)location[1],location[2],location[3],Math.min(4,1+damage*0.15f),++serial));
    }
    private static float[] animalLocation(rpg.animal.Animal animal,float x,float y,float z) {
        var part=rpg.combat.Gore.animalPartAt(animal,x,y,z);
        var figure=rpg.render.AnimalModels.of(animal.species,animal.variant);
        int region=switch(part){case HEAD->Blood.HEAD;case ARM_R->Blood.R_ARM;case ARM_L->Blood.L_ARM;case LEG_R->Blood.R_LEG;case LEG_L->Blood.L_LEG;default->Blood.TORSO;};
        var model=switch(part){case HEAD->figure.head;case ARM_R,ARM_L->figure.bird?figure.wing:figure.legFront;case LEG_R,LEG_L->figure.legBack;default->figure.body;};
        if(model==null)model=figure.body;
        float[] joint=switch(part){case HEAD->figure.neck;case ARM_R,ARM_L->figure.bird?figure.wingAt:figure.legF;case LEG_R,LEG_L->figure.legB;default->null;};
        float scale=rpg.render.AnimalModels.PX*animal.scale(),co=(float)Math.cos(animal.yaw),si=(float)Math.sin(animal.yaw);
        float side=((x-animal.x)*co-(z-animal.z)*si)/scale,back=((x-animal.x)*si+(z-animal.z)*co)/scale;
        float jx=joint==null?0:joint[0],jy=joint==null?0:joint[1],jz=joint==null?0:joint[2];
        if(part==rpg.combat.Gore.Part.ARM_L || part==rpg.combat.Gore.Part.LEG_L)jx=-jx;
        var vox=model.vox();
        float u=(side-jx+model.gx())/vox.sx,v=((y-animal.y)/scale-figure.bodyY-jy+model.gy())/vox.sy,w=(back-jz+model.gz())/vox.sz;
        float nx=(u-0.5f)*2,ny=(v-0.5f)*2,nz=(w-0.5f)*2;int face;
        if(Math.abs(ny)>Math.max(Math.abs(nx),Math.abs(nz))) {face=ny>0?Blood.TOP:Blood.BOTTOM;v=w;}
        else if(Math.abs(nx)>Math.abs(nz)){face=nx>0?Blood.RIGHT:Blood.LEFT;u=w;}
        else face=nz<0?Blood.FRONT:Blood.BACK;
        return new float[]{region,face,Math.clamp(u,0.05f,0.95f),1-Math.clamp(v,0.05f,0.95f)};
    }
    public int severity(Actor actor) { return cuts.isEmpty() || actor.hp>=actor.maxHp()?0:Math.max(1,Math.min(8,(int)Math.ceil((1-actor.hp/actor.maxHp())*8))); }
    public int version(Actor actor) { return severity(actor)==0?0:serial*9+severity(actor); }
    public boolean affects(int part) {for(Cut cut:cuts)if(cut.part==part)return true;return false;}
    public List<Cut> snapshot() { return List.copyOf(cuts); }
    public void clear() { cuts.clear(); }
    public List<Object> save() {
        List<Object> out=new ArrayList<>();for(Cut cut:cuts)out.add(List.of(cut.part,cut.face,cut.u,cut.v,cut.radius,cut.seed));return out;
    }
    public void load(List<Object> records) {
        clear();serial=0;if(records==null)return;
        for(Object record:records)if(record instanceof List<?> values && values.size()==6 && cuts.size()<10) {
            int part=((Number)values.get(0)).intValue(),face=((Number)values.get(1)).intValue();
            if(part<0 || part>5 || face<0 || face>5)continue;
            Cut cut=new Cut(part,face,((Number)values.get(2)).floatValue(),((Number)values.get(3)).floatValue(),((Number)values.get(4)).floatValue(),((Number)values.get(5)).intValue());
            cuts.add(cut);serial=Math.max(serial,cut.seed);
        }
    }
}
