package rpg.world;

import rpg.Rpg;
import rpg.actor.Actor;
import static astratto.bridge.AstrattoNative.CHEM_BURNING;

/** Visible uncontrolled fires; ordinary campfires and lamps do not count as building hazards. */
public final class FireSafety {
    private FireSafety() {}
    public record Hazard(float x,float y,float z) {}
    public static Hazard observe(Rpg g,Actor actor,float reach) {
        Hazard closest=null;float distance=reach;
        for(var cell:g.chemistry.active) {
            if((cell.state()&CHEM_BURNING)==0 || !structural(cell.originalBlock()))continue;
            float d=actor.distTo(cell.x(),cell.z());
            if(d<distance && g.vox.sightCell(actor.x,actor.eyeY(),actor.z,(int)Math.floor(cell.x()),(int)Math.floor(cell.y()),(int)Math.floor(cell.z()))) {
                closest=new Hazard(cell.x(),cell.y(),cell.z());distance=d;
            }
        }
        return closest;
    }
    private static boolean structural(int block){return Repairs.mendable(block) || Blocks.isTreeLog(block) || Blocks.isLeaves(block);}
    /** Walk toward an open, supported retreat away from heat. */
    public static float[] refuge(Rpg g,Actor actor,Hazard fire) {
        float best=-Float.MAX_VALUE;float[] spot={actor.x,actor.z};
        double away=Math.atan2(actor.z-fire.z,actor.x-fire.x);
        for(int turn=0;turn<8;turn++) {
            double angle=away+turn*Math.PI/4;
            float x=actor.x+(float)Math.cos(angle)*4,z=actor.z+(float)Math.sin(angle)*4;
            if(g.vox.solid((int)Math.floor(x),DevMap.FLOOR,(int)Math.floor(z)) || !g.vox.solid((int)Math.floor(x),DevMap.FLOOR-1,(int)Math.floor(z))
                    || !g.vox.clear(actor.x,actor.y+0.7f,actor.z,x,actor.y+0.7f,z))continue;
            float score=(float)Math.hypot(x-fire.x,z-fire.z);
            if(score>best){best=score;spot=new float[]{x,z};}
        }
        return spot;
    }
}
