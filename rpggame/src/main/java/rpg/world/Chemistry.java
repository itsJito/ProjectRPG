package rpg.world;

import astratto.bridge.Snapshots;
import astratto.util.Json;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import rpg.Rpg;
import rpg.actor.Actor;
import rpg.ai.Npc;
import rpg.animal.Animal;
import rpg.item.ItemDef;
import static astratto.bridge.AstrattoNative.*;

/** Gameplay adapter for native chemical state. It registers moving receivers and applies actual attributed harm. */
public final class Chemistry {
    private final Rpg g;
    private final Set<Long> registered = new HashSet<>();
    private final Map<Long, Integer> states = new HashMap<>();
    private final Map<Long, Double> hurtAgain = new HashMap<>();
    private final Map<Long,Double> cleansed=new HashMap<>();
    private final Map<Long,Double> smothered=new HashMap<>();
    private final Set<Long> objectReceivers=new HashSet<>();
    public List<Snapshots.Chemical> active = List.of();
    private double next;
    private record Edit(int x,int y,int z,int original,int block) {}
    private final Map<Long,Edit> edits=new HashMap<>();
    private void changed(long id,int x,int y,int z,int original,int block) {
        Edit previous=edits.get(id);edits.put(id,new Edit(x,y,z,previous==null?original:previous.original,block));
        g.setBlock(x,y,z,block);
    }
    /** Restore only chemistry-owned edits before applying an earlier save's object state. */
    public void beforeLoad() { for(Edit edit:edits.values())g.setBlock(edit.x,edit.y,edit.z,edit.original);edits.clear();thawAt.clear(); }
    private final Map<Long,Double> thawAt=new HashMap<>();
    public record Visual(long id,int kind,float x,float y,float z,float radius,float rate,float life,float r,float green,float b,double until) {}
    public final java.util.ArrayDeque<Visual> visuals=new java.util.ArrayDeque<>();
    public record Ripple(float x,float z,float radius,float strength,double until) {}
    public final java.util.ArrayDeque<Ripple> ripples=new java.util.ArrayDeque<>();
    public void splash(float x,float y,float z) { disturb(x,y,z,0.8f,3,true); }
    public void disturb(float x,float y,float z,float radius,float strength,boolean spray) {
        if(spray)visual(8,x,y,z,0.035f,Math.min(120,30+strength*20),0.12f,0.45f,0.6f,0.85f,1);
        if(ripples.size()>=32)ripples.removeFirst();ripples.addLast(new Ripple(x,z,radius,-strength,g.time+0.18));
    }
    private long visualId=1L<<48;
    public void visual(int kind,float x,float y,float z,float radius,float rate,float duration,float life,float red,float green,float blue) {
        if(visuals.size()>=48)visuals.removeFirst();
        visuals.addLast(new Visual(++visualId,kind,x,y,z,radius,rate,life,red,green,blue,g.time+duration));
    }
    public Chemistry(Rpg g) {
        this.g = g; var c = g.game.commands(); c.chemReset();
        for (int block : new int[]{Blocks.LOG, Blocks.OAK_LOG, Blocks.WILLOW_LOG, Blocks.PLANKS, Blocks.BEAM, Blocks.ROOF}) c.chemMaterial(block, CHEM_FLAMMABLE, Blocks.CHARRED, 14);
        for (int block : new int[]{Blocks.GRASS, Blocks.LEAVES, Blocks.OAK_LEAVES, Blocks.WILLOW_LEAVES, Blocks.FLAX_PATCH, Blocks.HERB_PATCH, Blocks.BERRY_BUSH}) c.chemMaterial(block, CHEM_FLAMMABLE | CHEM_WIND_SENSITIVE, block == Blocks.GRASS ? Blocks.BARE_PATCH : Blocks.AIR, 5);
        for (int block : new int[]{Blocks.COPPER_ROCK, Blocks.IRON_ROCK, Blocks.ANVIL, Blocks.LAMP}) c.chemMaterial(block, CHEM_CONDUCTIVE, block, 0);
        c.chemMaterial(Blocks.ICE,CHEM_FREEZABLE,Blocks.WATER,0);
        c.chemMaterial(Blocks.WATER, CHEM_FREEZABLE | CHEM_CONDUCTIVE, Blocks.WATER, 0);
    }
    /** Reset this receiver immediately and on the native command stream; never carry fire into a new life. */
    public void cleanse(Actor actor) {
        cleansed.put(actor.id,g.time+0.5);
        g.game.commands().chemRemove(actor.id); registered.remove(actor.id); states.remove(actor.id); hurtAgain.remove(actor.id);
        active = active.stream().filter(sensor -> sensor.id() != actor.id).toList();
        actor.cs.slowT = 0; actor.cs.staggered = 0; actor.burnSince=-1;actor.charred=0;actor.rollContact=0;
        if(actor.ragdoll!=0)g.game.commands().ragdollControl(actor.ragdoll,CONTROL_STAY_DOWN,0);
    }
    private final float[] receiverCenter=new float[3];
    /** Fire and chemistry follow the actual posed body, including a fallen torso rather than an upright chest offset. */
    public void center(Actor actor,float[] out) {
        out[0]=actor.x;out[1]=actor.chestY();out[2]=actor.z;
        long ragdoll=actor==g.player && g.death!=null && g.death.active?g.death.ragdoll:actor.ragdoll;
        if(ragdoll!=0) {var body=g.lastBodies.ragdoll(ragdoll);if(body!=null){out[0]=body.comX;out[1]=body.comY;out[2]=body.comZ;}}
        if(actor instanceof Animal animal && animal.ragdoll!=null) {var trunk=g.lastRigids.body(animal.ragdoll.trunk().body());if(trunk!=null){out[0]=trunk.x;out[1]=trunk.y;out[2]=trunk.z;}}
    }
    public void register(Actor actor) {
        if(actor.dummy)return;
        int tags=CHEM_FLAMMABLE|CHEM_FREEZABLE|(actor.eq.stacks().stream().anyMatch(item->item.def.conductive)?CHEM_CONDUCTIVE:0);
        if(g.time<smothered.getOrDefault(actor.id,-1.0))tags &= ~CHEM_FLAMMABLE;
        center(actor,receiverCenter);registered.add(actor.id);
        g.game.commands().chemSensor(actor.id,0,receiverCenter[0],receiverCenter[1],receiverCenter[2],actor.radius,tags,0,10,0,0,false);
    }
    public int state(long id) { return states.getOrDefault(id, 0); }
    public void emit(Actor owner, int element, float x, float y, float z, float radius, float strength, float life) { g.game.commands().chemEmit(owner == null ? 0 : owner.id, element, x, y, z, radius, strength, life); }
    public void update() {
        visuals.removeIf(v->g.time>v.until); ripples.removeIf(v->g.time>v.until);
        if (g.time < next) return; next = g.time + 0.25;
        var thaw=thawAt.entrySet().iterator();
        while(thaw.hasNext()) { var entry=thaw.next(); if(g.time<entry.getValue())continue;
            long id=entry.getKey();int x=(int)((id>>>40)&0xfffff),y=(int)((id>>>20)&0xfffff),z=(int)(id&0xfffff);
            if(g.vox.block(x,y,z)==Blocks.ICE)changed(id,x,y,z,Blocks.WATER,Blocks.WATER);g.game.commands().chemRemove(id);thaw.remove();
        }
        Set<Long> live = new HashSet<>();
        for (Actor a : g.actors()) {
            boolean present = a == g.player || a instanceof Npc n && n.loaded && (a.alive || n.ragdoll != 0) || a instanceof Animal an && an.loaded && !an.butchered;
            if (!present || a.dummy) continue;
            live.add(a.id);
            register(a);
            if (g.vox.block((int) a.x, (int) (a.y + 0.1f), (int) a.z) == Blocks.WATER) {
                emit(null, CHEM_WATER, a.x, a.chestY(), a.z, a.radius, 2, 0.3f);

            }
            if(g.timeSystem.weather.rain>0.1f && g.vox.skyLight((int)a.x,(int)a.eyeY(),(int)a.z)>0.7f)
                emit(null,CHEM_WATER,a.x,a.chestY(),a.z,a.radius,g.timeSystem.weather.rain,0.25f);
        }
        for (long id : registered) if (!live.contains(id)) g.game.commands().chemRemove(id);
        registered.clear(); registered.addAll(live);
        Set<Long> objects=new HashSet<>();
        for(var object:g.objects.all()) {
            if(object instanceof Foliage.Patch || object instanceof rpg.objects.Trap || object instanceof rpg.objects.Corpse || object instanceof rpg.objects.Fire)continue;
            boolean physical=object instanceof rpg.objects.Furniture || object instanceof rpg.objects.Door || object instanceof rpg.objects.Station || object instanceof rpg.objects.GroundItem || object instanceof rpg.objects.Container container && !container.kind.equals("claim") && !container.kind.equals("escrow") && !container.kind.equals("grave") && !container.kind.equals("memorial");
            if(!physical)continue;
            boolean wood=object instanceof rpg.objects.Door || object instanceof rpg.objects.Container || object instanceof rpg.objects.Furniture furniture && furniture.kind!=rpg.objects.Furniture.Kind.HEADSTONE
                    || object instanceof rpg.objects.GroundItem item && !item.stack.def.conductive;
            int tags=wood?CHEM_FLAMMABLE:CHEM_CONDUCTIVE;
            objects.add(object.id);g.game.commands().chemSensor(object.id,0,object.x,object.y+0.25f,object.z,0.45f,tags,0,wood?6:0,0,0,false);
        }
        for(long id:objectReceivers)if(!objects.contains(id))g.game.commands().chemRemove(id);
        objectReceivers.clear();objectReceivers.addAll(objects);
        cleansed.entrySet().removeIf(entry->g.time>entry.getValue());
        smothered.entrySet().removeIf(entry->g.time>entry.getValue());
        active = g.game.engine().chemistry().stream().filter(sensor->!cleansed.containsKey(sensor.id())).toList(); states.clear();
        for (var sensor : active) {
            states.put(sensor.id(), sensor.state());
            Actor a = g.actor(sensor.id());
            if(a==null) {
                if(sensor.id()>=0){
                    g.foliage.chemical(sensor);
                    var object=g.objects.get(sensor.id());
                    if(object!=null && !(object instanceof Foliage.Patch) && sensor.heat()>0.35f)object.charred=Math.min(1,object.charred+0.025f);
                    continue;
                }
                int x=(int)sensor.x(),y=(int)sensor.y(),z=(int)sensor.z();
                if((sensor.state()&CHEM_FROZEN)!=0 && g.vox.block(x,y,z)==Blocks.WATER) { changed(sensor.id(),x,y,z,Blocks.WATER,Blocks.ICE);thawAt.put(sensor.id(),g.time+20); }
                if(sensor.heat()>0.2f && g.vox.block(x,y,z)==Blocks.ICE) { changed(sensor.id(),x,y,z,Blocks.WATER,Blocks.WATER);thawAt.remove(sensor.id());emit(null,CHEM_WATER,sensor.x(),sensor.y(),sensor.z(),0.6f,1,0.25f); }
                if((sensor.state()&CHEM_BURNT)!=0 && !edits.containsKey(sensor.id()))changed(sensor.id(),x,y,z,sensor.originalBlock(),sensor.block());
                if((sensor.state()&CHEM_BURNT)!=0)for(var object:g.objects.all())if(object instanceof rpg.objects.Node node && !node.depleted() && node.bx==x && node.bz==z && (node.kind==rpg.objects.Node.Kind.TREE||node.kind==rpg.objects.Node.Kind.PATCH)) {
                    node.stock=0;node.regrowAt=g.state.minutes+node.respawnMinutes;
                    g.state.commit("NodeBurnt",sensor.owner(),node.id,node.jurisdiction(),0,node.name);
                }
                continue;
            }
            if(!a.alive)continue;
            int bits = sensor.state();
            if((bits & CHEM_BURNING)!=0) {
                if(a.burnSince<0)a.burnSince=g.time;
                a.charred=Math.min(1,a.charred+0.02f);
                if(a instanceof Npc && g.time-a.burnSince>=3) {
                    if(!a.reacting){g.reactions.falling(a,0);g.game.commands().ragdollControl(a.ragdoll,CONTROL_COLLAPSE,1);}
                    g.game.commands().ragdollControl(a.ragdoll,CONTROL_STAY_DOWN,1);
                    var body=g.lastBodies.ragdoll(a.ragdoll);
                    if(body!=null && g.time-a.lastRoll>=0.35) {
                        a.lastRoll=g.time;
                        // Opposing vertical forces across the shoulders produce a roll around the torso's long axis.
                        // A horizontal twist merely slides a prone body and cannot smother its burning clothes.
                        g.game.commands().ragdollHit(a.ragdoll,RD_L_SHOULDER,0,1,0,22,0);
                        g.game.commands().ragdollHit(a.ragdoll,RD_R_SHOULDER,0,-1,0,22,0);
                        var floor=g.vox.ray(body.comX,body.comY,body.comZ,0,-1,0,0.8f,false);
                        if(floor.hit && Math.abs(body.p[RD_L_SHOULDER*3+1]-body.p[RD_R_SHOULDER*3+1])>0.08f)a.rollContact+=0.35;
                        if(a.rollContact>=2) {
                            // Ground contact briefly excludes air from the clothes. Old self-emitted flame volumes
                            // must expire before the fabric can ignite again; nearby sustained fire can still relight it.
                            smothered.put(a.id,g.time+0.75);
                            center(a,receiverCenter);
                            g.game.commands().chemSensor(a.id,sensor.owner(),receiverCenter[0],receiverCenter[1],receiverCenter[2],a.radius,sensor.tags() & ~CHEM_FLAMMABLE,bits & ~CHEM_BURNING,sensor.fuel(),sensor.wet(),0,true);
                            g.state.commit("FireSmothered",a.id,0,null,0,"rolled against solid ground");
                        }
                    }
                }
            } else {
                a.burnSince=-1;a.rollContact=0;
                if(a.ragdoll!=0)g.game.commands().ragdollControl(a.ragdoll,CONTROL_STAY_DOWN,0);
            }
            if ((bits & CHEM_FROZEN) != 0) a.cs.slowT = Math.max(a.cs.slowT, 0.6f);
            if ((bits & CHEM_CHARGED) != 0) { a.cs.cancel(); a.cs.staggered = Math.max(a.cs.staggered, 0.3f); }
            if ((bits & (CHEM_BURNING | CHEM_CHARGED)) != 0 && g.time >= hurtAgain.getOrDefault(a.id, -99.0)) {
                hurtAgain.put(a.id, g.time + 0.75); Actor owner = g.actor(sensor.owner());
                if (owner != null && owner != a) g.combat.resolve(owner, a, 2, 0, ItemDef.DamageType.MAGIC, null, a.x, a.chestY(), a.z, (bits & CHEM_CHARGED) != 0 ? "electric shock" : "burning", false);
                else g.combat.environmentalDamage(a, 1, "burning");
            }
        }
    }
    public List<Map<String, Object>> save() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (var s : g.game.engine().chemistry()) {
            if(cleansed.containsKey(s.id()))continue;
            var object=s.id()>=0?g.objects.get(s.id()):null;if(object!=null && object.removed)continue;
            Map<String, Object> m = new HashMap<>(); m.put("id", s.id()); m.put("owner", s.owner()); m.put("x", s.x()); m.put("y", s.y()); m.put("z", s.z()); m.put("r", s.radius()); m.put("tags", s.tags()); m.put("state", s.state()); m.put("fuel", s.fuel()); m.put("wet", s.wet()); m.put("heat", s.heat()); out.add(m);
        }
        for(var entry:edits.entrySet()) {
            Edit edit=entry.getValue();Map<String,Object> m=new HashMap<>();m.put("edit",true);m.put("id",entry.getKey());m.put("x",edit.x);m.put("y",edit.y);m.put("z",edit.z);m.put("original",edit.original);m.put("block",edit.block);
            m.put("thawLeft",Math.max(0,thawAt.getOrDefault(entry.getKey(),g.time)-g.time));out.add(m);
        }
        return out;
    }
    public void load(List<Object> records) {
        g.game.commands().chemReset(); registered.clear();objectReceivers.clear();cleansed.clear();smothered.clear(); states.clear(); hurtAgain.clear(); active = List.of(); thawAt.clear(); next = g.time + 0.25; // Let queued reset/restore commands replace the old native snapshot first.
        if(records==null)return;
        for (Object record : records) if (record instanceof Map<?, ?> raw) {
            @SuppressWarnings("unchecked") Map<String, Object> m = (Map<String, Object>) raw;
            if(Json.bool(m,"edit",false)) {
                long id=Json.lng(m,"id",0);changed(id,Json.integer(m,"x",0),Json.integer(m,"y",0),Json.integer(m,"z",0),Json.integer(m,"original",0),Json.integer(m,"block",0));
                double left=Json.num(m,"thawLeft",0);if(left>0)thawAt.put(id,g.time+left);continue;
            }
            g.game.commands().chemSensor(Json.lng(m,"id",0),Json.lng(m,"owner",0),(float)Json.num(m,"x",0),(float)Json.num(m,"y",0),(float)Json.num(m,"z",0),(float)Json.num(m,"r",0.4),Json.integer(m,"tags",0),Json.integer(m,"state",0),(float)Json.num(m,"fuel",0),(float)Json.num(m,"wet",0),(float)Json.num(m,"heat",0),true);
        }
    }
}
