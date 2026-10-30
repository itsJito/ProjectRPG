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
    private final Map<Long,Long> retired = new HashMap<>();
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
        // a built thing burnt away is damage someone will come to mend (Repairs)
        int was=previous==null?original:previous.original;
        if(g.repairs!=null && (block==Blocks.AIR || Blocks.charred(block) || Blocks.dual(was) && block==Blocks.survivorOf(was)) && rpg.world.Repairs.mendable(was))g.repairs.broken(x,y,z,previous==null?original:previous.original,"fire");
    }
    /** A cell mended (Repairs): the burning there is no longer this world's to restore or to keep. */
    public void forget(int x,int y,int z) { edits.values().removeIf(e->e.x==x && e.y==y && e.z==z); }
    /** Restore only chemistry-owned edits before applying an earlier save's object state. */
    public void beforeLoad() { for(Edit edit:edits.values())g.setBlock(edit.x,edit.y,edit.z,edit.original);edits.clear();thawAt.clear(); }
    private final Map<Long,Double> thawAt=new HashMap<>();
    public record Visual(long id,int kind,float x,float y,float z,float radius,float rate,float life,float r,float green,float b,double until) {}
    public final java.util.ArrayDeque<Visual> visuals=new java.util.ArrayDeque<>();
    public record Ripple(float x,float z,float radius,float strength,double until) {}
    public final java.util.ArrayDeque<Ripple> ripples=new java.util.ArrayDeque<>();
    /**
     * Water carrying a charge (gamefixes: water should react visibly when electricity hits it): where, how far it runs
     * across the surface, since and until when. The renderer flickers it - light jumping over the surface, sparks
     * skittering across it, the surface shivering.
     */
    public record Charge(float x,float y,float z,float radius,double since,double until) {}
    /**
     * Soot (gamefixes: fire did not burn the world's surfaces as it went): where a block is alight its open faces blacken,
     * and the face above it (flames lick upward) - a scorch laid on the surface, darker with each layer, long before the
     * block itself burns through to char. Drawn as decals; count says how many have ever been laid.
     */
    public record Soot(float x,float y,float z,float nx,float ny,float nz,float size,float rot) {}
    public final java.util.ArrayDeque<Soot> soot=new java.util.ArrayDeque<>();
    public int sootCount;
    private final Map<Long,Double> sootAt=new HashMap<>();
    private static final int[][] FACES={{1,0,0},{-1,0,0},{0,1,0},{0,0,1},{0,0,-1}};
    /** Faces already sooted (each blackens once). */
    private final Map<Long,Integer> sootLayers=new HashMap<>();
    private void soot(long id,int x,int y,int z,float heat) {
        if(g.time<sootAt.getOrDefault(id,0.0))return;
        sootAt.put(id,g.time+2.5);
        long k=(long)(g.time*10)+id;
        for(int[] f:FACES) {
            int ax=x+f[0],ay=y+f[1],az=z+f[2];
            if(g.vox.solid(ax,ay,az) || !layer(x,y,z,f))continue;
            // two or three patches of uneven size scattered over the face, leaning up (smoke rises): no two faces alike
            int patches=1+(int)(g.roll(k,f[0]*5+f[2]*3+1)*2);
            for(int p=0;p<patches;p++) {
                long q=k*7+p*131+f[0]*17+f[1]*29+f[2]*41;
                float u=(float)(g.roll(q,1)-0.5)*0.8f, v=(float)(g.roll(q,2)-0.35)*0.8f;
                float ox=f[0]==0?u:0, oz=f[0]!=0?u:0, oy=f[1]==0?v:0;
                if(f[1]!=0){ox=u;oz=v;}
                addSoot(x+0.5f+f[0]*0.51f+ox,y+0.5f+f[1]*0.51f+oy,z+0.5f+f[2]*0.51f+oz,f[0],f[1],f[2],(0.45f+(float)g.roll(q,3)*0.75f)*(0.8f+heat*0.4f),(float)(g.roll(q,4)*6.28));
            }
        }
        // the face above takes the smoke: the side of the block over it, where it is open
        int uy=y+1;
        if(g.vox.solid(x,uy,z)) for(int[] f:FACES) if(f[1]==0 && !g.vox.solid(x+f[0],uy,z+f[2]) && layer(x,uy,z,f)) addSoot(x+0.5f+f[0]*0.51f,uy+0.35f,z+0.5f+f[2]*0.51f,f[0],0,f[2],0.9f+heat*0.4f,(float)(g.roll(k,13)*6.28));
    }
    /** Blocks gone since the renderer last looked (packed cells): their soot and stains are taken down with them. */
    public final java.util.ArrayDeque<int[]> gone=new java.util.ArrayDeque<>();
    /** (0.2.1, gamefixes: soot hung on blocks that had broken away) a block emptied: its soot goes, and may be laid again if it is rebuilt. */
    public void cleared(int x,int y,int z) {
        soot.removeIf(st->(int)Math.floor(st.x()-st.nx()*0.6f)==x && (int)Math.floor(st.y()-st.ny()*0.6f)==y && (int)Math.floor(st.z()-st.nz()*0.6f)==z);
        long base=cell(x,y,z)*8;
        for(int k=0;k<18;k++)sootLayers.remove(base+k);
        if(gone.size()<4096)gone.add(new int[]{x,y,z});
    }
    private boolean layer(int x,int y,int z,int[] f) {
        long key=cell(x,y,z)*8+(f[0]+1)*3+(f[2]+1)+(f[1]>0?9:0);
        int n=sootLayers.getOrDefault(key,0);
        if(n>=1)return false;
        sootLayers.put(key,n+1);return true;
    }
    private void addSoot(float x,float y,float z,float nx,float ny,float nz,float size,float rot) {
        if(soot.size()>=2000)soot.removeFirst();
        soot.addLast(new Soot(x,y,z,nx,ny,nz,size,rot)); sootCount++;
    }
    public final java.util.ArrayDeque<Charge> charges=new java.util.ArrayDeque<>();
    /** Whether the point is in or on water (the block at it, or the one under it). */
    public boolean onWater(float x,float y,float z) {
        int bx=(int)Math.floor(x),bz=(int)Math.floor(z);
        return g.vox.block(bx,(int)Math.floor(y),bz)==Blocks.WATER || g.vox.block(bx,(int)Math.floor(y-0.6f),bz)==Blocks.WATER;
    }
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
    /** How charred each fence cell is, 0 .. 1 (Props darkens the fence by it). */
    private final java.util.Map<Long, Float> fenceChar = new java.util.HashMap<>();
    public float fenceChar(int x, int y, int z) { return fenceChar.getOrDefault(cell(x, y, z), 0f); }
    private static long cell(int x, int y, int z) { return ((long) x & 0xfffff) << 40 | ((long) y & 0xfffff) << 20 | (long) z & 0xfffff; }

    public Chemistry(Rpg g) {
        this.g = g; var c = g.game.commands(); c.chemReset();
        // what burns, carries a charge or freezes follows each block's material (gamefixes: trees and leaves burn, water conducts)
        for (int block = 1; block < Blocks.COUNT; block++) {
            if (Blocks.objectCell(block) && Blocks.material(block) != Blocks.Material.METAL || block == Blocks.DOOR || block == Blocks.LOCKED_DOOR || Blocks.charred(block)) continue;   // objects burn as objects
            // a fence (drawn as posts and rails, not a cube) burns in its own shape - blackening (Props) - and then is gone,
            // never turning into a block of char (gamefixes); a wooden cube chars into its own charred twin
            if (block == Blocks.FENCE) { c.chemMaterial(block, CHEM_FLAMMABLE | CHEM_WIND_SENSITIVE, Blocks.AIR, 9); continue; }
            // (0.2.1) a two-faced block burns as its halves do: a wooden half burns away and leaves the other standing
            // (plaster, stone, iron); an iron half carries a charge and glows with the heat (Gfx.hotMetal)
            if (Blocks.dual(block)) {
                Blocks.Material o = Blocks.material(Blocks.outerOf(block)), in = Blocks.material(Blocks.innerOf(block));
                boolean burns = o == Blocks.Material.WOOD || in == Blocks.Material.WOOD, metal = o == Blocks.Material.METAL || in == Blocks.Material.METAL;
                int tags = (burns ? CHEM_FLAMMABLE : 0) | (metal ? CHEM_CONDUCTIVE : 0);
                if (tags != 0) c.chemMaterial(block, tags, burns ? Blocks.survivorOf(block) : block, burns ? 12 : 0);
                continue;
            }
            switch (Blocks.material(block)) {
                case WOOD -> c.chemMaterial(block, CHEM_FLAMMABLE, Blocks.charredOf(block), Blocks.isTreeLog(block) ? 18 : 14);
                case FOLIAGE -> c.chemMaterial(block, CHEM_FLAMMABLE | CHEM_WIND_SENSITIVE, Blocks.AIR, 5);
                case STRAW -> c.chemMaterial(block, CHEM_FLAMMABLE | CHEM_WIND_SENSITIVE, Blocks.AIR, 9);
                case CLOTH -> c.chemMaterial(block, CHEM_FLAMMABLE, Blocks.CHARRED, 8);
                case COAL -> c.chemMaterial(block, CHEM_FLAMMABLE, Blocks.AIR, 40);
                case METAL -> c.chemMaterial(block, CHEM_CONDUCTIVE, block, 0);
                case WATER -> c.chemMaterial(block, CHEM_FREEZABLE | CHEM_CONDUCTIVE, block, 0);
                case ICE -> c.chemMaterial(block, CHEM_FREEZABLE, Blocks.WATER, 0);
                default -> { }
            }
        }
        // (green turf is not tinder: a campfire on a lawn burned the whole meadow, the herb beds and a woodcutter with it.
        // What grows on it - the plants, the leaves, the trees - still burns)
        // wet earth and the ore in a rock carry a charge too
        for (int block : new int[]{Blocks.COPPER_ROCK, Blocks.IRON_ROCK, Blocks.MUD}) c.chemMaterial(block, CHEM_CONDUCTIVE, block, 0);
    }
    /** Reset this receiver immediately and on the native command stream; never carry fire into a new life. */
    public void cleanse(Actor actor) {
        cleansed.put(actor.id,g.time+0.8);
        g.game.commands().chemRemove(actor.id); registered.remove(actor.id); states.remove(actor.id); hurtAgain.remove(actor.id);
        active = active.stream().filter(sensor -> sensor.id() != actor.id).toList();
        actor.shockUntil=-99; actor.lastSpasm=-99;
        center(actor,receiverCenter);
        g.game.commands().chemSensor(actor.id,0,receiverCenter[0],receiverCenter[1],receiverCenter[2],actor.radius,0,0,10,0,0,true);
        actor.cs.slowT = 0; actor.cs.staggered = 0; actor.burnSince=-1;actor.charred=0;actor.rollContact=0;
        if(actor.ragdoll!=0)g.game.commands().ragdollControl(actor.ragdoll,CONTROL_STAY_DOWN,0);
    }
    private final float[] receiverCenter=new float[3];

    /**
     * (gamefixes: fire should char bodies as the blood engine marks them) a burning body is charred patch by patch where
     * the flames are: from the legs up in the first seconds (fire climbs), then anywhere; each patch grows while the fire
     * stays on it. A handful of marks a second at most, painted into the figure's own skin - nothing per frame.
     */
    private void scorch(Actor a) {
        if (a instanceof rpg.animal.Animal || g.time < a.scorchAt) return;
        a.scorchAt = g.time + 0.3;
        long k = (long) (g.time * 10);
        double r0 = g.roll(a.id, k), r1 = g.roll(a.id, k + 1), r2 = g.roll(a.id, k + 2), r3 = g.roll(a.id, k + 3);
        double burning = g.time - a.burnSince;
        int part;
        if (burning < 1.5) part = r0 < 0.5 ? rpg.actor.Blood.R_LEG : rpg.actor.Blood.L_LEG;
        else if (burning < 3.5) part = r0 < 0.3 ? rpg.actor.Blood.R_LEG : r0 < 0.6 ? rpg.actor.Blood.L_LEG : r0 < 0.85 ? rpg.actor.Blood.TORSO : r0 < 0.93 ? rpg.actor.Blood.R_ARM : rpg.actor.Blood.L_ARM;
        else part = (int) (r0 * 6);
        int face = 2 + (int) (r1 * 4);
        // low on a leg first, climbing as it burns
        float v = burning < 1.5 ? 0.55f + (float) r3 * 0.4f : (float) r3;
        a.blood.scorch(part, face, (float) r2, v, 1.3f, g.time);
    }
    /** Fire and chemistry follow the actual posed body, including a fallen torso rather than an upright chest offset. */
    public void center(Actor actor,float[] out) {
        out[0]=actor.x;out[1]=actor.chestY();out[2]=actor.z;
        long ragdoll=actor==g.player && g.death!=null && g.death.active?g.death.ragdoll:actor.ragdoll;
        if(ragdoll!=0) {var body=g.lastBodies.ragdoll(ragdoll);if(body!=null){out[0]=body.comX;out[1]=body.comY;out[2]=body.comZ;}}
        if(actor instanceof Animal animal && animal.ragdoll!=null) {var trunk=g.lastRigids.body(animal.ragdoll.trunk().body());if(trunk!=null){out[0]=trunk.x;out[1]=trunk.y;out[2]=trunk.z;}}
    }
    public void register(Actor actor) {
        if(actor.dummy)return;
        int tags=CHEM_FLAMMABLE|CHEM_FREEZABLE|CHEM_CONDUCTIVE;
        if(g.time<smothered.getOrDefault(actor.id,-1.0))tags &= ~CHEM_FLAMMABLE;
        if(cleansed.containsKey(actor.id))tags=0;
        center(actor,receiverCenter);registered.add(actor.id);
        g.game.commands().chemSensor(actor.id,0,receiverCenter[0],receiverCenter[1],receiverCenter[2],actor.radius,tags,0,10,0,0,false);
    }
    public int state(long id) { return states.getOrDefault(id, 0); }
    /** Death ends attribution for this life, not the fire itself. Reset after native command read-back. */
    public void retire(Actor owner) {
        retired.put(owner.id,g.game.engine().frameSequence()); g.game.commands().chemDisown(owner.id);
        for (var object:g.objects.all()) if (object instanceof rpg.objects.Fire fire && fire.owner==owner.id) fire.retireAttribution();
    }
    public void emit(Actor owner, int element, float x, float y, float z, float radius, float strength, float life) {
        g.game.commands().chemEmit(owner == null || !owner.alive ? 0 : owner.id, element, x, y, z, radius, strength, life);
        if (element == CHEM_ELECTRIC && onWater(x, y, z)) {
            // the surface: the top of the water column here
            int bx = (int) Math.floor(x), bz = (int) Math.floor(z), by = (int) Math.floor(y);
            while (by < rpg.world.DevMap.SY - 1 && g.vox.block(bx, by + 1, bz) == Blocks.WATER) by++;
            while (by > 0 && g.vox.block(bx, by, bz) != Blocks.WATER) by--;
            if (charges.size() >= 12) charges.removeFirst();
            charges.addLast(new Charge(x, by + 0.92f, z, Math.max(1.5f, radius * 1.8f), g.time, g.time + 1.2 + strength * 0.25));
            disturb(x, by + 0.92f, z, radius, 2.2f, false);
        }
    }
    private void acknowledgeRetirements() {
        long frame=g.game.engine().frameSequence();retired.values().removeIf(queued -> queued<frame);
    }
    public void update() {
        acknowledgeRetirements();
        visuals.removeIf(v->g.time>v.until); ripples.removeIf(v->g.time>v.until); charges.removeIf(c->g.time>c.until);
        // a charged surface shivers: small impulses round it, here and there, as long as the charge lasts
        for (Charge c : charges) if (ripples.size() < 28) {
            double a = g.roll(g.state.tick, (long) (c.since * 1000)) * Math.PI * 2, r = g.roll(g.state.tick, 7 + (long) (c.since * 1000)) * c.radius;
            ripples.addLast(new Ripple(c.x + (float) (Math.cos(a) * r), c.z + (float) (Math.sin(a) * r), 0.18f, -0.5f, g.time + 0.05));
        }
        if (g.time < next) return; next = g.time + 0.25;
        // a fence burning blackens where the flames are, post by post, until there is nothing left to burn (the cell
        // goes to air); the char stays on what survives a fire put out
        for (var c : active) if ((c.state() & CHEM_BURNING) != 0 && c.originalBlock() == Blocks.FENCE) {
            long key = cell((int) Math.floor(c.x()), (int) Math.floor(c.y()), (int) Math.floor(c.z()));
            fenceChar.merge(key, 0.08f, (a, b) -> Math.min(1, a + b));
        }
        fenceChar.entrySet().removeIf(e -> { long id = e.getKey(); return g.vox.block((int) ((id >>> 40) & 0xfffff), (int) ((id >>> 20) & 0xfffff), (int) (id & 0xfffff)) != Blocks.FENCE; });
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
            boolean physical=object instanceof rpg.objects.Furniture || object instanceof rpg.objects.Door || object instanceof rpg.objects.Station || object instanceof rpg.objects.GroundItem || object instanceof rpg.objects.Container container && !container.kind.equals("claim") && !container.kind.equals("escrow") && !container.kind.equals("vault") && !container.kind.equals("grave") && !container.kind.equals("memorial");
            if(!physical)continue;
            boolean wood=object instanceof rpg.objects.Door door && !door.cellDoor || object instanceof rpg.objects.Container || object instanceof rpg.objects.Furniture furniture && furniture.kind!=rpg.objects.Furniture.Kind.HEADSTONE
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
                    if(object!=null && (sensor.state()&CHEM_BURNING)!=0 && (object instanceof rpg.objects.Door || object instanceof rpg.objects.Furniture || object instanceof rpg.objects.Container) && object.owner()!=sensor.owner())arson(sensor.owner(),sensor.x(),sensor.z());
                    if(object!=null && !(object instanceof Foliage.Patch) && sensor.heat()>0.35f)object.charred=Math.min(1,object.charred+0.025f);
                    continue;
                }
                int x=(int)sensor.x(),y=(int)sensor.y(),z=(int)sensor.z();
                if((sensor.state()&CHEM_BURNING)!=0 && Blocks.material(sensor.originalBlock())!=Blocks.Material.FOLIAGE)soot(sensor.id(),x,y,z,Math.min(1,sensor.heat()));
                if((sensor.state()&CHEM_BURNING)!=0 && Blocks.material(sensor.originalBlock())==Blocks.Material.WOOD && !Blocks.isTreeLog(sensor.originalBlock()))arson(sensor.owner(),sensor.x(),sensor.z());
                if((sensor.state()&CHEM_FROZEN)!=0 && g.vox.block(x,y,z)==Blocks.WATER) { changed(sensor.id(),x,y,z,Blocks.WATER,Blocks.ICE);thawAt.put(sensor.id(),g.time+20); }
                if(sensor.heat()>0.2f && g.vox.block(x,y,z)==Blocks.ICE) { changed(sensor.id(),x,y,z,Blocks.WATER,Blocks.WATER);thawAt.remove(sensor.id());emit(null,CHEM_WATER,sensor.x(),sensor.y(),sensor.z(),0.6f,1,0.25f); }
                if((sensor.state()&CHEM_BURNT)!=0 && !edits.containsKey(sensor.id()))changed(sensor.id(),x,y,z,sensor.originalBlock(),sensor.block());
                if((sensor.state()&CHEM_BURNT)!=0)for(var object:g.objects.all())if(object instanceof rpg.objects.Node node && !node.depleted() && node.bx==x && node.bz==z && (node.kind==rpg.objects.Node.Kind.TREE||node.kind==rpg.objects.Node.Kind.PATCH)) {
                    node.stock=0;node.regrowAt=g.state.minutes+node.respawnMinutes;
                    g.state.commit("NodeBurnt",sensor.owner(),node.id,node.jurisdiction(),0,node.name);
                }
                continue;
            }
            if(!a.alive) { if((sensor.state()&CHEM_BURNING)!=0) { if(a.burnSince<0)a.burnSince=g.time; a.charred=Math.min(1,a.charred+0.02f); scorch(a); } continue; }
            int bits = sensor.state();
            if((bits & CHEM_BURNING)!=0) {
                if(a.burnSince<0)a.burnSince=g.time;
                a.charred=Math.min(1,a.charred+0.02f);
                scorch(a);
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
            if ((bits & CHEM_CHARGED) != 0) g.reactions.shocked(a);
            if ((bits & (CHEM_BURNING | CHEM_CHARGED)) != 0 && g.time >= hurtAgain.getOrDefault(a.id, -99.0)) {
                hurtAgain.put(a.id, g.time + 0.75); Actor owner = g.actor(sensor.owner());
                // (walking into someone's campfire is an accident, not an attack by whoever lit it)
                if (owner != null && owner.alive && owner != a && !retired.containsKey(owner.id) && !hearth(a, owner)) g.combat.resolve(owner, a, 2, 0, ItemDef.DamageType.MAGIC, null, a.x, a.chestY(), a.z, (bits & CHEM_CHARGED) != 0 ? "electric shock" : "burning", false);
                else g.combat.environmentalDamage(a, 1, (bits & CHEM_CHARGED) != 0 ? "electric shock" : "burning");
            }
        }
    }
    /** Whether a body is burning at a cooking fire its owner lit (in or right beside it). */
    private boolean hearth(Actor a, Actor owner) {
        for (var object : g.objects.all()) if (object instanceof rpg.objects.Fire fire && !fire.removed && fire.owner == owner.id && Math.hypot(fire.x - a.x, fire.z - a.z) < 1.6) return true;
        return false;
    }
    private final Map<Long,Double> arsonAt=new HashMap<>();
    /** Someone's fire has caught a building or another's property in a town: arson, once a minute per fire-raiser (gamefixes). */
    private void arson(long culprit,float x,float z) {
        Actor raiser=g.actor(culprit);
        if(raiser==null || !raiser.alive || DevMap.WILDS.equals(DevMap.jurisdiction(x,z)) || g.time<arsonAt.getOrDefault(culprit,-99.0))return;
        arsonAt.put(culprit,g.time+60);
        g.law.crime(raiser,"ARSON",0,0,null,0);
    }
    public List<Map<String, Object>> save() {
        acknowledgeRetirements();
        List<Map<String, Object>> out = new ArrayList<>();
        for (var s : g.game.engine().chemistry()) {
            if(cleansed.containsKey(s.id()))continue;
            var object=s.id()>=0?g.objects.get(s.id()):null;if(object!=null && object.removed)continue;
            Map<String, Object> m = new HashMap<>(); m.put("id", s.id()); m.put("owner", retired.containsKey(s.owner()) ? 0L : s.owner()); m.put("x", s.x()); m.put("y", s.y()); m.put("z", s.z()); m.put("r", s.radius()); m.put("tags", s.tags()); m.put("state", s.state()); m.put("fuel", s.fuel()); m.put("wet", s.wet()); m.put("heat", s.heat()); out.add(m);
        }
        for(var entry:edits.entrySet()) {
            Edit edit=entry.getValue();Map<String,Object> m=new HashMap<>();m.put("edit",true);m.put("id",entry.getKey());m.put("x",edit.x);m.put("y",edit.y);m.put("z",edit.z);m.put("original",edit.original);m.put("block",edit.block);
            m.put("thawLeft",Math.max(0,thawAt.getOrDefault(entry.getKey(),g.time)-g.time));out.add(m);
        }
        return out;
    }
    public void load(List<Object> records) {
        g.game.commands().chemReset(); retired.clear();registered.clear();objectReceivers.clear();cleansed.clear();smothered.clear(); states.clear(); hurtAgain.clear(); active = List.of(); thawAt.clear(); next = g.time + 0.25; // Let queued reset/restore commands replace the old native snapshot first.
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
