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
    public void splash(float x,float y,float z) {
        visual(8,x,y,z,0.07f,70,0.15f,0.65f,0.6f,0.85f,1);
        if(ripples.size()>=32)ripples.removeFirst();ripples.addLast(new Ripple(x,z,0.8f,-3,g.time+0.18));
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
            int tags = CHEM_FLAMMABLE | CHEM_FREEZABLE | (a.eq.stacks().stream().anyMatch(item->item.def.conductive) ? CHEM_CONDUCTIVE : 0);
            g.game.commands().chemSensor(a.id, 0, a.x, a.chestY(), a.z, a.radius, tags, 0, 10, 0, 0, false);
            if (g.vox.block((int) a.x, (int) (a.y + 0.1f), (int) a.z) == Blocks.WATER) {
                emit(null, CHEM_WATER, a.x, a.chestY(), a.z, a.radius, 2, 0.3f);
                if(Math.hypot(a.vx,a.vz)>0.5 && g.time>=a.nextSplash) { a.nextSplash=g.time+0.5;splash(a.x,a.y+0.1f,a.z); }
            }
            if(g.timeSystem.weather.rain>0.1f && g.vox.skyLight((int)a.x,(int)a.eyeY(),(int)a.z)>0.7f)
                emit(null,CHEM_WATER,a.x,a.chestY(),a.z,a.radius,g.timeSystem.weather.rain,0.25f);
        }
        for (long id : registered) if (!live.contains(id)) g.game.commands().chemRemove(id);
        registered.clear(); registered.addAll(live);
        active = g.game.engine().chemistry(); states.clear();
        for (var sensor : active) {
            states.put(sensor.id(), sensor.state());
            Actor a = g.actor(sensor.id());
            if(a==null) {
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
            Map<String, Object> m = new HashMap<>(); m.put("id", s.id()); m.put("owner", s.owner()); m.put("x", s.x()); m.put("y", s.y()); m.put("z", s.z()); m.put("r", s.radius()); m.put("tags", s.tags()); m.put("state", s.state()); m.put("fuel", s.fuel()); m.put("wet", s.wet()); m.put("heat", s.heat()); out.add(m);
        }
        for(var entry:edits.entrySet()) {
            Edit edit=entry.getValue();Map<String,Object> m=new HashMap<>();m.put("edit",true);m.put("id",entry.getKey());m.put("x",edit.x);m.put("y",edit.y);m.put("z",edit.z);m.put("original",edit.original);m.put("block",edit.block);
            m.put("thawLeft",Math.max(0,thawAt.getOrDefault(entry.getKey(),g.time)-g.time));out.add(m);
        }
        return out;
    }
    public void load(List<Object> records) {
        g.game.commands().chemReset(); registered.clear(); states.clear(); hurtAgain.clear(); active = List.of(); thawAt.clear(); next = g.time + 0.25; // Let queued reset/restore commands replace the old native snapshot first.
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
