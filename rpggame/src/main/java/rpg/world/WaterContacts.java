package rpg.world;

import java.util.HashMap;
import java.util.Map;
import rpg.Rpg;

/**
 * Surface crossings and displaced-water wakes from authoritative mover, ragdoll and rigid-body snapshots (gamefixes:
 * water answers whatever is in it): on the way in a splash as big as the landing was hard - a crown of drops, a jet
 * after a hard landing, spreading foam, a ring of waves and the sound of it; a wake that follows a body wading, at its contact point and
 * the closer together the faster it goes, with spray thrown up by a run; soft rings round someone standing still.
 */
public final class WaterContacts {
    private static final class Contact { float x,y,z,surface; boolean wet;double last,seen;int stroke; }
    private final java.util.Set<Long> skip=new java.util.HashSet<>();
    private final Map<Long,Contact> contacts=new HashMap<>();
    private final Rpg g;
    private int budget;
    public long splashes;
    public WaterContacts(Rpg g){this.g=g;}
    public void update() {
        budget=16;
        // (0.3.5, gamefixes: water did not answer whoever was in it) bodies are read as the actors they are - a swimmer
        // is posed by its own physics, so its mover reads still - from where they really go, with their own girth;
        // a swimmer leaves a bow wave and a wake, a stroke throws up spray, someone treading water rings the surface
        skip.clear();
        for(var a:g.actors()) {
            if(!a.alive || a.dummy || a.body==0 || Math.abs(a.x-g.player.x)>40 || Math.abs(a.z-g.player.z)>40)continue;
            if(a instanceof rpg.ai.Npc n && !n.loaded || a instanceof rpg.animal.Animal an && !an.loaded)continue;
            skip.add(a.body);
            sample(a.body,a.x,a.y+0.12f,a.z,Float.NaN,Float.NaN,Float.NaN,Math.max(0.2f,a.radius+0.12f));
            Contact c=contacts.get(a.body);
            if(c!=null && c.wet && a.swimMode==rpg.actor.Swimming.SWIM && budget>0) {
                // a stroke breaks the surface twice a cycle: a hand going in ahead, a kick behind
                int stroke=(int)Math.floor(a.swimStroke*2);
                if(stroke!=c.stroke){c.stroke=stroke;budget--;
                    float side=(stroke&1)==0?0.28f:-0.28f,fx=(float)-Math.sin(a.yaw),fz=(float)-Math.cos(a.yaw),rx=(float)Math.cos(a.yaw),rz=(float)-Math.sin(a.yaw);
                    float hx=a.x+fx*0.55f+rx*side,hz=a.z+fz*0.55f+rz*side;
                    g.chemistry.disturb(hx,c.surface,hz,0.35f,1.6f,true);
                    g.chemistry.disturb(a.x-fx*0.7f,c.surface,a.z-fz*0.7f,0.3f,1.0f,false);
                    if(g.sfx!=null && g.sfx.has("splash") && a==g.player)g.sfx.play("splash",hx,c.surface,hz,0.09f,1.35f+(stroke&3)*0.05f,8);
                }
            }
        }
        for(var mover:g.lastBodies.movers)if(!skip.contains(mover.handle))sample(mover.handle,mover.x,mover.y+0.12f,mover.z,mover.vx,mover.vy,mover.vz,0.4f);
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
        int top=Math.min(rpg.world.WorldMap.active().sizeY-2,(int)Math.ceil(Math.max(y,previous)+radius));
        int bottom=Math.max(0,(int)Math.floor(Math.min(y,previous)-radius));
        for(int h=top;h>=Math.max(bottom,top-12);h--)if(g.vox.block(bx,h,bz)==Blocks.WATER && g.vox.block(bx,h+1,bz)!=Blocks.WATER){surface=h+0.9f;break;}
        boolean wet=Float.isFinite(surface) && y-radius<surface && y+radius>surface-1.5f;
        if(wet)contact.surface=surface;
        float speed=(float)Math.hypot(vx,vz),down=Math.max(0,-vy);
        boolean entered=wet && !contact.wet && !initial && previous+radius>=surface;
        float every=speed>0.4f?0.12f+0.3f*(1-Math.min(1,speed/4)):1.1f;
        boolean wake=wet && g.time-contact.last>=every;
        if(budget>0 && (entered || wake)) {
            budget--;contact.last=g.time;splashes++;
            boolean still=speed<=0.4f;
            float strength=entered?Math.min(5,0.6f+speed*0.15f+down*0.2f):still?0.85f:Math.min(6,1.2f+speed*0.65f);
            // Disturb the surface where the physical body touches it, including a still body.
            float ax=x,az=z;
            if(entered) {
                // going in: as big as the landing was hard and the body heavy (a jump, a fall, a thrown body, a stone)
                float energy=down*(0.7f+radius)+speed*0.3f;
                g.chemistry.disturb(ax,surface,az,Math.max(0.6f,radius*1.5f+energy*0.12f),Math.min(10,strength+energy*0.5f),true);
                if(g.fx!=null && energy>0.6f)g.fx.splash(ax,surface,az,energy,Math.max(0.15f,radius));
                if(g.sfx!=null && energy>0.4f)g.sfx.play("splash",ax,surface,az,Math.min(1f,0.25f+energy*0.09f),Math.max(0.7f,1.15f-energy*0.04f-radius*0.2f),18+energy*3);
                // (0.3.5) the fish there scatter from it
                if(g.fishery!=null && energy>0.3f)g.fishery.alarm(ax,az,2.5f+energy*0.8f);
            }
            else {
                g.chemistry.disturb(ax,surface,az,Math.max(0.5f,radius*1.5f),strength,speed>2.2f);
                // moving, the water heaps ahead of the body (its bow wave) and the wake trails off behind
                if(speed>0.6f && budget>0){budget--;float k=Math.min(0.9f,0.25f+speed*0.12f);
                    g.chemistry.disturb(ax+vx/speed*k,surface,az+vz/speed*k,Math.max(0.4f,radius*1.2f),strength*0.6f,false);}
            }
        }
        contact.wet=wet;
    }
    public void reset(){contacts.clear();}
}
