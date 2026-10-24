package rpg.combat;

import rpg.world.Voxels;

/** One reusable prediction buffer, integrated at a fixed step and resampled into a small aim guide. */
public final class Ballistics {
    private final float[] path = new float[481*3];
    public int arc(Voxels world,float x,float y,float z,float vx,float vy,float vz,float gravity,float[][] points) {
        path[0]=x;path[1]=y;path[2]=z;
        int count=1;
        final float step=1f/60;
        for(int i=1;i<481;i++) {
            vy-=gravity*step;
            float dx=vx*step,dy=vy*step,dz=vz*step,len=(float)Math.sqrt(dx*dx+dy*dy+dz*dz);
            var hit=world.ray(x,y,z,dx,dy,dz,len,false);
            x=hit.hit?hit.x:x+dx;y=hit.hit?hit.y+0.025f:y+dy;z=hit.hit?hit.z:z+dz;
            path[count*3]=x;path[count*3+1]=y;path[count*3+2]=z;count++;
            if(hit.hit)break;
        }
        int dots=Math.min(points.length,count);
        for(int i=0;i<dots;i++) {
            float sample=(count-1)*i/(float)Math.max(1,dots-1);int first=(int)sample,last=Math.min(count-1,first+1);float blend=sample-first;
            for(int axis=0;axis<3;axis++)points[i][axis]=path[first*3+axis]+(path[last*3+axis]-path[first*3+axis])*blend;
        }
        return dots;
    }
}
