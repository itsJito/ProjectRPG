package rpg.render;

import astratto.voxel.VoxModel;
import java.util.List;
import rpg.actor.Blood;
import rpg.actor.Injuries;
import rpg.combat.Gore;

/** Destructive surface carving on a private copy; shared species assets and collision silhouettes remain intact. */
final class VoxelInjuries {
    private VoxelInjuries() {}
    static int part(Gore.Part part) { return switch(part){case HEAD->Blood.HEAD;case ARM_R->Blood.R_ARM;case ARM_L->Blood.L_ARM;case LEG_R->Blood.R_LEG;case LEG_L->Blood.L_LEG;case TAIL->-1;default->Blood.TORSO;}; }
    static ItemModels.Model carve(ItemModels.Model source,Gore.Part part,List<Injuries.Cut> wounds,int severity) {
        if(severity==0 || wounds.stream().noneMatch(wound->wound.part()==part(part)))return source;
        VoxModel original=source.vox(),copy=new VoxModel(original.sx,original.sy,original.sz);System.arraycopy(original.col,0,copy.col,0,copy.col.length);
        for(var wound:wounds)if(wound.part()==part(part)) {
            int face=wound.face(),width=face==Blood.RIGHT || face==Blood.LEFT?copy.sz:copy.sx;
            int height=face==Blood.TOP || face==Blood.BOTTOM?copy.sz:copy.sy;
            int thickness=face==Blood.RIGHT || face==Blood.LEFT?copy.sx:face==Blood.TOP || face==Blood.BOTTOM?copy.sy:copy.sz;
            int depth=Math.min(Math.max(1,thickness/5),1+severity/3);
            float cx=wound.u()*width,cy=(1-wound.v())*height;
            for(int u=0;u<width;u++)for(int v=0;v<height;v++) {
                float across=Math.abs(u+0.5f-cx),along=Math.abs(v+0.5f-cy);
                if(across>0.9f || along>wound.radius()*1.5f)continue;
                int removed=0;
                for(int d=0;d<thickness;d++) {
                    int x=face==Blood.RIGHT?copy.sx-1-d:face==Blood.LEFT?d:u;
                    int y=face==Blood.TOP?copy.sy-1-d:face==Blood.BOTTOM?d:v;
                    int z=face==Blood.FRONT?d:face==Blood.BACK?copy.sz-1-d:face==Blood.TOP || face==Blood.BOTTOM?v:u;
                    if(copy.get(x,y,z)==0)continue;
                    if(removed++<depth)copy.set(x,y,z,0);else {copy.set(x,y,z,0xFF71231C);break;}
                }
            }
        }
        return new ItemModels.Model(copy,source.gx(),source.gy(),source.gz());
    }
    static ItemModels.Model scorch(ItemModels.Model source,Gore.Part part,List<Blood.Mark> marks) {
        if(marks.stream().noneMatch(m->m.scorch() && m.part()==part(part)))return source;
        VoxModel original=source.vox(),copy=new VoxModel(original.sx,original.sy,original.sz);
        System.arraycopy(original.col,0,copy.col,0,copy.col.length);
        for(var mark:marks)if(mark.scorch() && mark.part()==part(part)) {
            int face=mark.face(),width=face==Blood.RIGHT||face==Blood.LEFT?copy.sz:copy.sx;
            int height=face==Blood.TOP||face==Blood.BOTTOM?copy.sz:copy.sy;
            int depth=face==Blood.RIGHT||face==Blood.LEFT?copy.sx:face==Blood.TOP||face==Blood.BOTTOM?copy.sy:copy.sz;
            float radius=Math.max(1,mark.radius()/16f*Math.max(width,height));
            for(int u=0;u<width;u++)for(int v=0;v<height;v++) {
                float distance=(float)Math.hypot(u+.5f-mark.u()*width,v+.5f-(1-mark.v())*height);
                float rough=0.82f+Math.floorMod(u*73+v*37+mark.seed(),17)/50f;
                if(distance>radius*rough)continue;
                for(int d=0;d<depth;d++) {
                    int x=face==Blood.RIGHT?copy.sx-1-d:face==Blood.LEFT?d:u;
                    int y=face==Blood.TOP?copy.sy-1-d:face==Blood.BOTTOM?d:v;
                    int z=face==Blood.FRONT?d:face==Blood.BACK?copy.sz-1-d:face==Blood.TOP||face==Blood.BOTTOM?v:u;
                    if(copy.get(x,y,z)==0)continue;
                    int ash=25+Math.floorMod(u*31+v*13+mark.seed(),22);
                    int color=0xff000000|ash<<16|ash<<8|ash;
                    if(mark.wet() && distance>radius*.72f)color=0xff68331c;
                    copy.set(x,y,z,color);break;
                }
            }
        }
        return new ItemModels.Model(copy,source.gx(),source.gy(),source.gz());
    }

}
