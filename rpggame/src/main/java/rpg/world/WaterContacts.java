package rpg.world;

import java.util.HashMap;
import java.util.Map;
import rpg.Rpg;

/** Surface crossings and displaced-water wakes from authoritative mover, ragdoll and rigid-body snapshots. */
public final class WaterContacts {
    private static final class Contact { float x,y,z; boolean wet;double last,seen; }
    private final Map<Long,Contact> contacts=new HashMap<>();
    private final Rpg g;
    private int budget;
    public long splashes;
    public WaterContacts(Rpg g){this.g=g;}
    public void update() {
        budget=8;
        for(var mover:g.lastBodies.movers)sample(mover.handle,mover.x,mover.y+0.12f,mover.z,mover.vx,mover.vy,mover.vz,0.4f);
        for(var ragdoll:g.lastBodies.ragdolls)sample(ragdoll.handle,ragdoll.comX,ragdoll.comY,ragdoll.comZ,Float.NaN,Float.NaN,Float.NaN,0.5f);
        for(var rigid:g.lastRigids.bodies.values())if(!rigid.asleep)sample(rigid.handle,rigid.x,rigid.y,rigid.z,rigid.vx,rigid.vy,rigid.vz,0.15f);
        contacts.entrySet().removeIf(entry->g.time-entry.getValue().seen>1);
    }
    private void sample(long id,float x,float y,float z,float vx,float vy,float vz,float radius) {
        if(Math.hypot(x-g.player.x,z-g.player.z)>40)return;
        Contact contact=contacts.computeIfAbsent(id,key->new Contact());boolean initial=contact.seen==0;
        float previous=initial?y:contact.y;
        if(!Float.isFinite(vy)) {
            double elapsed=g.time-contact.seen;
            vx=initial || elapsed<=0?0:(float)((x-contact.x)/elapsed);
            vy=initial || elapsed<=0?0:(float)((y-contact.y)/elapsed);
            vz=initial || elapsed<=0?0:(float)((z-contact.z)/elapsed);
        }
        contact.x=x;contact.y=y;contact.z=z;contact.seen=g.time;
        int bx=(int)Math.floor(x),bz=(int)Math.floor(z);float surface=Float.NaN;
        int top=Math.min(DevMap.SY-2,(int)Math.ceil(Math.max(y,previous)+radius));
        int bottom=Math.max(0,(int)Math.floor(Math.min(y,previous)-radius));
        for(int h=top;h>=Math.max(bottom,top-12);h--)if(g.vox.block(bx,h,bz)==Blocks.WATER && g.vox.block(bx,h+1,bz)!=Blocks.WATER){surface=h+0.9f;break;}
        boolean wet=Float.isFinite(surface) && y-radius<surface && y+radius>surface-1.5f;
        float speed=(float)Math.hypot(vx,vz),down=Math.max(0,-vy);
        boolean entered=wet && !contact.wet && !initial && previous+radius>=surface;
        boolean wake=wet && speed>0.4f && g.time-contact.last>=0.45;
        if(budget>0 && (entered || wake)) {
            budget--;contact.last=g.time;splashes++;
            float strength=Math.min(5,0.6f+speed*0.15f+down*0.2f);
            g.chemistry.disturb(x,surface,z,Math.max(0.25f,radius*2),strength,entered);
        }
        contact.wet=wet;
    }
    public void reset(){contacts.clear();}
}
