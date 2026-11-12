package rpg.world;

import astratto.bridge.Snapshots;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import rpg.Rpg;
import rpg.actor.Actor;
import rpg.interact.Option;
import rpg.interact.Result;
import rpg.interact.WorldObject;
import static astratto.bridge.AstrattoNative.*;

/**
 * Deterministic surface-driven growth, generated a chunk at a time. Saves retain depletion, not thousands of fixtures.
 * Plants are pressed over by whatever walks through them and spring back after (gamefixes: the trail of pushers the
 * renderer bends them by), rustle as they are pushed, and are destroyed only by the world - fire, chiefly - never by
 * a menu option.
 */
public final class Foliage {
    /**
     * Every kind of growth, with its bulk as a body meets it (0.3.6, gamefixes: fire spells passed through the plants):
     * the radius and height of the stand of it, and whether it is dense - woody or thick enough to stop a bolt flying
     * into it, where grass and flowers only part round it.
     */
    public enum Kind {
        FERN(0.35f,0.6f,false),TALL_GRASS(0.35f,0.8f,false),CLOVER(0.3f,0.15f,false),REED(0.3f,1.3f,false),BRAMBLE(0.5f,0.9f,true),
        WILDFLOWER(0.25f,0.4f,false),IVY(0.3f,1.2f,false),MOSS(0.3f,0.1f,false),MUSHROOM(0.15f,0.15f,false),SAPLING(0.3f,1.6f,true),
        SHRUB(0.5f,0.9f,true),THISTLE(0.25f,0.8f,false),DAISY(0.2f,0.25f,false),BLUEBELL(0.2f,0.3f,false),CATTAIL(0.25f,1.4f,false),
        HEATHER(0.35f,0.4f,false),DOCK(0.3f,0.6f,false),BRACKEN(0.4f,0.9f,false),BUSH(0.65f,1.3f,true);
        public final float radius,height;
        public final boolean dense;
        Kind(float radius,float height,boolean dense){this.radius=radius;this.height=height;this.dense=dense;}
    }
    public static final class Chunk {
        public final int key,x,z;
        public int version;
        public final List<Patch> plants=new ArrayList<>();
        Chunk(int key,int x,int z){this.key=key;this.x=x;this.z=z;}
    }
    public static final class Patch extends WorldObject {
        public final Kind kind;
        public final int seed,chunk;
        public final float facing;
        Patch(long id,Kind kind,int seed,int chunk,float x,float y,float z,float facing) {
            super(id,"foliage",kind.name().replace('_',' ').toLowerCase(java.util.Locale.ROOT),x,y,z);this.kind=kind;this.seed=seed;this.chunk=chunk;this.facing=facing;
        }
        @Override public List<Option> options(Rpg g,Actor actor){return List.of();}
        @Override public Result perform(Rpg g,Actor actor,String action,long request) {
            return Result.no("You cannot do that.");
        }
    }
    /** Where a body pushed through the plants a moment ago: the renderer bends them there, less as they recover. */
    private record Trail(float x,float y,float z,float radius,double at) {}
    private final java.util.ArrayDeque<Trail> trail=new java.util.ArrayDeque<>();
    private final Map<Long,Double> trailAt=new HashMap<>(), rustleAt=new HashMap<>();
    /** Where each body last left a trail point. */
    private final Map<Long,float[]> lastPoint=new HashMap<>();
    private static final double RECOVER=2.2;
    /** (0.4.0) Chunks of growth kept before the far ones are let go, and how far (chunks) is far. */
    private static final int EVICT_OVER=rpg.Tuning.i("foliage.keepChunks",120), EVICT_RING=rpg.Tuning.i("foliage.evictRing",6);
    private double evictAt;
    private final float[] push=new float[32];
    /** (0.4.0) The overworld's chunks on the map in play (the devmap's 12 x 16, ShadowVeil's 67 x 67). */
    private final int CHUNKS_X = (WorldMap.active().overworldX() + 15) / 16, CHUNKS_Z = (WorldMap.active().sizeZ + 15) / 16;
    private final Rpg g;
    public final Map<Integer,Chunk> chunks=new HashMap<>();
    private final Map<Long,Double> depleted=new HashMap<>();
    public int epoch;
    private double next;
    public Foliage(Rpg g){this.g=g;}
    private static int hash(int x,int z){int n=x*374761393+z*668265263;n=(n^(n>>>13))*1274126177;return n^(n>>>16);}
    public static long id(int x,int y,int z){return 0x1000000000000L|((long)x<<32)|((long)y<<16)|z;}
    public boolean suitable(Kind kind,int x,int y,int z) {
        if(x<1 || z<1 || x>=WorldMap.active().overworldX()-1 || z>=WorldMap.active().sizeZ-1 || g.vox.block(x,y,z)!=Blocks.AIR)return false;
        // Leave the root and first trunk block readable; wall growth must not wrap a living tree.
        for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++)
            if(Blocks.isTreeLog(g.vox.block(x+dx,y,z+dz)) || g.vox.block(x+dx,y,z+dz)==Blocks.STUMP)return false;
        int soil=g.vox.block(x,y-1,z);boolean earth=soil==Blocks.GRASS || soil==Blocks.DIRT || soil==Blocks.BARE_PATCH || soil==Blocks.HEATH || soil==Blocks.PODZOL || soil==Blocks.DRY_GRASS;
        float sky=g.vox.skyLight(x,y+1,z);
        if(kind==Kind.IVY || kind==Kind.MOSS) {
            return backing(kind,x,y,z)!=null && (kind==Kind.IVY || sky<0.7f);
        }
        // (0.4.0) the desert's and the shore's sand carries a little scrub, nothing else
        if(soil==Blocks.SAND && !earth)return (kind==Kind.SHRUB || kind==Kind.BUSH) && sky>0.3f && (hash(x*3+1,z*5+2)&7)==0 && g.vox.block(x,y+1,z)==Blocks.AIR;
        if(!earth || g.vox.block(x,y+1,z)!=Blocks.AIR)return false;
        return switch(kind){
            case FERN,MUSHROOM,BRACKEN->sky<0.65f;
            case REED,CATTAIL->nearWater(x,y,z);
            case BLUEBELL->sky<0.8f;
            case BRAMBLE,SAPLING,SHRUB,BUSH->sky>0.3f && g.vox.block(x,y+2,z)==Blocks.AIR;
            default->sky>0.35f;
        };
    }
    /** A wall ivy and moss can climb: stone, brick or wood of any kind. */
    private static boolean climbable(int block){var m=Blocks.material(block);return block!=Blocks.GLASS && (m==Blocks.Material.STONE || m==Blocks.Material.WOOD) && Blocks.traversable(block);}
    private static final int[][] SIDES={{1,0},{-1,0},{0,1},{0,-1}};
    /**
     * The wall growth climbs: a face of climbable blocks beside the cell - for Ivy two high - that goes on to at least
     * one side (0.2.9, gamefixes: Ivy stood alone on a single post or pillar, a strand with nothing around it).
     */
    private int[] backing(Kind kind,int x,int y,int z) {
        for(int[] d:SIDES)if(climbable(g.vox.block(x+d[0],y,z+d[1]))
                && (kind!=Kind.IVY || climbable(g.vox.block(x+d[0],y+1,z+d[1])))
                && (climbable(g.vox.block(x+d[0]+d[1],y,z+d[1]-d[0])) || climbable(g.vox.block(x+d[0]-d[1],y,z+d[1]+d[0]))))return d;
        return null;
    }
    private boolean nearWater(int x,int y,int z){for(int[] direction:SIDES)for(int step=1;step<=4;step++)if(g.vox.block(x+direction[0]*step,y-1,z+direction[1]*step)==Blocks.WATER)return true;return false;}
    private Chunk generate(int cx,int cz) {
        int key=cx+cz*CHUNKS_X;Chunk chunk=new Chunk(key,cx*16,cz*16);
        // half the cells are candidates - a checkerboard, every row (0.2.9, gamefixes: more foliage - it was one cell in
        // four) - and half of those grow something; a plant that does not suit its spot gives way to the next kind that
        // does, so the shade, the water's edge and the open each fill
        for(int x=chunk.x+1;x<chunk.x+16;x++)for(int z=chunk.z+1;z<chunk.z+16;z++) {
            // (0.3.0, gamefixes: more tall grass, and taller) the other half of the cells may carry tall grass alone - in
            // drifts in the open (a slow noise gates it, so it gathers in meadows rather than evenly everywhere)
            if(((x+z)&1)!=0) {
                int s2=hash(x*7+3,z*5+1);
                float drift=(float)(Math.sin(x*0.19+Math.cos(z*0.13)*1.7)+Math.sin(z*0.23+x*0.07));
                if(drift<-0.1f || (s2&3)==0 || !suitable(Kind.TALL_GRASS,x,rpg.world.Ground.cell(x, z),z))continue;
                float gx=((s2>>>20)&15)/15f*0.6f-0.3f,gz=((s2>>>24)&15)/15f*0.6f-0.3f;
                long gid=id(x,rpg.world.Ground.cell(x, z),z);Patch grass=new Patch(gid,Kind.TALL_GRASS,s2,key,x+0.5f+gx,rpg.world.Ground.at(x+0.5f+gx, z+0.5f+gz),z+0.5f+gz,(s2>>>16)*0.00009587f);
                grass.removed=depleted.getOrDefault(gid,-1.0)>g.state.minutes;chunk.plants.add(grass);g.objects.add(grass);
                continue;
            }
            int seed=hash(x,z);if((seed&1)!=0)continue;int y=Ground.cell(x,z);
            // Preserve the established species distribution, varying some shrubs into fuller bushes.
            int established=Kind.BUSH.ordinal(),first=Math.floorMod(seed>>>3,established);Kind kind=null;
            for(int k=0;k<4 && kind==null;k++){Kind trial=Kind.values()[(first+k*5)%established];if(suitable(trial,x,y,z))kind=trial;}
            if(kind==Kind.SHRUB && ((seed>>>10)&3)==0)kind=Kind.BUSH;
            if(kind==null)continue;
            float facing=(seed>>>16)*0.00009587f;
            if(kind==Kind.IVY || kind==Kind.MOSS){int[] direction=backing(kind,x,y,z);facing=(float)Math.atan2(direction[0],direction[1]);}
            // off the cell's centre a little, so the rows of a lattice never show
            float ox=((seed>>>20)&15)/15f*0.5f-0.25f,oz=((seed>>>24)&15)/15f*0.5f-0.25f;
            if(kind==Kind.IVY || kind==Kind.MOSS){ox=0;oz=0;}
            long id=id(x,y,z);Patch plant=new Patch(id,kind,seed,key,x+0.5f+ox,y,z+0.5f+oz,facing);
            plant.removed=depleted.getOrDefault(id,-1.0)>g.state.minutes;chunk.plants.add(plant);g.objects.add(plant);
        }
        chunk.version++;chunks.put(key,chunk);return chunk;
    }
    /**
     * (0.3.2, gamefixes: foliage loaded before the player spawns in) every chunk of growth within rings of a point made
     * now, at once - the load does this about the spawn before the first frame, so the player never steps into bare
     * ground that fills in around them a chunk at a time.
     */
    public int preload(float x,float z,int rings) {
        int centerX=Math.clamp((int)x/16,0,CHUNKS_X-1),centerZ=Math.clamp((int)z/16,0,CHUNKS_Z-1),made=0;
        for(int dz=-rings;dz<=rings;dz++)for(int dx=-rings;dx<=rings;dx++) {
            int cx=centerX+dx,cz=centerZ+dz;
            if(cx<0 || cz<0 || cx>=CHUNKS_X || cz>=CHUNKS_Z || chunks.containsKey(cx+cz*CHUNKS_X))continue;
            generate(cx,cz);made++;
        }
        return made;
    }
    public void update() {
        trail();
        // (0.3.2) the ground under the player is never left bare: arriving somewhere new (a respawn, a long ride, a
        // teleport) the chunk they stand in and those about it come up at once, the rest a chunk a tick
        int hereX=Math.clamp((int)g.player.x/16,0,CHUNKS_X-1),hereZ=Math.clamp((int)g.player.z/16,0,CHUNKS_Z-1);
        if(!chunks.containsKey(hereX+hereZ*CHUNKS_X)){preload(g.player.x,g.player.z,1);next=0;}
        if(g.time<next)return;next=g.time+0.25;
        int centerX=hereX,centerZ=hereZ;
        // (0.4.0) on a map the size of ShadowVeil the growth far behind the traveller is let go (its picked plants are
        // remembered in 'depleted' and stay picked when the chunk grows again on return): a journey across the world
        // otherwise left every chunk's plants in the world's list, a cost that only grew
        if(chunks.size()>EVICT_OVER && g.time>evictAt){
            evictAt=g.time+2;
            java.util.List<Patch> gone=new java.util.ArrayList<>();
            chunks.values().removeIf(c->{boolean far=Math.max(Math.abs(c.x/16-centerX),Math.abs(c.z/16-centerZ))>EVICT_RING;if(far)gone.addAll(c.plants);return far;});
            if(!gone.isEmpty()){g.objects.unlist(gone);farOrder.clear();epoch++;}
        }
        boolean generated=false;
        for(int ring=0;ring<=2 && !generated;ring++)for(int dz=-ring;dz<=ring && !generated;dz++)for(int dx=-ring;dx<=ring;dx++) {
            int x=centerX+dx,z=centerZ+dz;if(x<0 || z<0 || x>=CHUNKS_X || z>=CHUNKS_Z || chunks.containsKey(x+z*CHUNKS_X))continue;generate(x,z);generated=true;break;
        }
        // (0.3.6, gamefixes: stutter / aggressive optimization) the chunks about the player are looked over every pass; the
        // rest of the map's growth a few chunks a pass in turn (every plant on the map was checked four times a second, a
        // spike that grew with every chunk the player had walked through)
        tended.clear();
        for(Chunk chunk:chunks.values())if(Math.abs(chunk.x/16-centerX)<=NEAR_CHUNKS && Math.abs(chunk.z/16-centerZ)<=NEAR_CHUNKS)tended.add(chunk);
        if(!chunks.isEmpty()) {
            if(farOrder.size()!=chunks.size()){farOrder.clear();farOrder.addAll(chunks.keySet());java.util.Collections.sort(farOrder);}
            for(int i=0;i<FAR_CHUNKS_A_PASS;i++){farAt=(farAt+1)%farOrder.size();Chunk c=chunks.get(farOrder.get(farAt));if(c!=null && !tended.contains(c))tended.add(c);}
        }
        for(Chunk chunk:tended)for(Patch plant:chunk.plants) {
            if(plant.removed) {
                if(depleted.getOrDefault(plant.id,Double.MAX_VALUE)<=g.state.minutes && suitable(plant.kind,(int)plant.x,(int)plant.y,(int)plant.z)) {depleted.remove(plant.id);plant.removed=false;chunk.version++;}
                else continue;
            }
            if(!suitable(plant.kind,(int)plant.x,(int)plant.y,(int)plant.z)){remove(plant,"lost its growing surface");continue;}
            // (0.3.2, gamefixes: fire spells still did not burn the plants) a plant is fire's to catch while the player is
            // near it - registered once as they come within 32 m and let go past 44 m (unless it is alight or warm). Every
            // plant ever passed used to stay registered with the chemistry for good, so after a walk round the map the
            // receivers were full and the plants by the player were never taken on: nothing near them could burn
            float d=(float)Math.hypot(plant.x-g.player.x,plant.z-g.player.z);
            if(d<=32 && sensed.add(plant.id))g.game.commands().chemSensor(plant.id,0,plant.x,plant.y+0.25f,plant.z,0.3f,CHEM_FLAMMABLE|CHEM_WIND_SENSITIVE,0,2.5f,0,0,false);
            else if(d>44 && g.chemistry.state(plant.id)==0 && sensed.remove(plant.id))g.game.commands().chemRemove(plant.id);
        }
    }
    /** (0.3.6) How many chunks out from the player's are tended every pass, and how many of the rest a pass in turn. */
    static final int NEAR_CHUNKS=3, FAR_CHUNKS_A_PASS=6;
    private final List<Chunk> tended=new ArrayList<>();
    private final List<Integer> farOrder=new ArrayList<>();
    private int farAt;
    /** The plants the chemistry is watching now (those near the player). */
    private final java.util.Set<Long> sensed=new java.util.HashSet<>();
    /**
     * Bodies pushing through plants: the player's, the people's and the animals' near the player leave trail points
     * where the stems were pressed (one every fifth of a second each), and a rustle as they go.
     */
    private void trail() {
        while(!trail.isEmpty() && g.time-trail.peekFirst().at()>RECOVER)trail.removeFirst();
        for(Actor actor:g.actors()) {
            if(!actor.alive || actor.dummy || actor.distTo(g.player.x,g.player.z)>40)continue;
            if(actor instanceof rpg.ai.Npc n && !n.loaded || actor instanceof rpg.animal.Animal a && !a.loaded)continue;
            float speed=(float)Math.hypot(actor.vx,actor.vz);
            if(speed<0.25f || g.time<trailAt.getOrDefault(actor.id,-1.0))continue;
            // (0.3.6, gamefixes: a Pegasus in flight made footstep noises) only a body down among the plants moves them: one
            // flying, leaping, in a saddle or swimming over them is above the growth, and neither bends it nor rustles it
            if(actor.riding!=0 || actor.swimMode>=rpg.actor.Swimming.TREAD || actor instanceof rpg.animal.Animal a && a.flying)continue;
            float ground=g.vox.groundY(actor.x,actor.y+0.3f,actor.z);
            if(!Float.isFinite(ground) || actor.y-ground>PLANT_REACH)continue;
            // a grown plant, or a planted patch (herbs, flax, a berry bush): both bend and rustle as a body goes through
            boolean patch=Blocks.plant(g.vox.block((int)Math.floor(actor.x),(int)Math.floor(actor.y+0.1f),(int)Math.floor(actor.z)));
            if(!patch && plantAt(actor.x,actor.z,actor.radius+0.35f)==null)continue;
            // (0.3.5, gamefixes: foliage still reacted badly to being stepped in) a point is left each half metre walked,
            // not each fifth of a second: a body standing about in the growth dropped point on point where it stood, and
            // the renderer's few slots then went to those, so the plants just behind a walker sprang up all at once
            float[] last=lastPoint.get(actor.id);
            if(last!=null && Math.hypot(actor.x-last[0],actor.z-last[1])<0.45f)continue;
            lastPoint.put(actor.id,new float[]{actor.x,actor.z});
            trailAt.put(actor.id,g.time+0.12);
            if(trail.size()>=64)trail.removeFirst();
            trail.addLast(new Trail(actor.x,actor.y,actor.z,actor.radius+0.05f,g.time));
            // the rustle: louder for a run, a hush for someone creeping
            if(g.sfx!=null && g.time>=rustleAt.getOrDefault(actor.id,-1.0)) {
                rustleAt.put(actor.id,g.time+(speed>3?0.32:0.55));
                g.sfx.play("step.grass",actor.x,actor.y+0.3f,actor.z,actor.sneaking?0.12f:Math.min(0.5f,0.18f+speed*0.06f),0.85f+(float)(actor.id%7)*0.04f,6+speed*2);
            }
            if(actor!=g.player)g.noise(actor,actor.x,actor.y,actor.z,actor.sneaking?1.5f:3+speed,0.15f);
        }
    }
    /** How far above the ground a body's feet may be and still be among the plants (m). */
    static final float PLANT_REACH=0.6f;
    /** (0.3.0) Whether tall grass stands about a point (someone crouched in it is half hidden). */
    public boolean tallGrassAt(float x, float z) {
        Chunk chunk=chunks.get(Math.clamp((int)x/16,0,CHUNKS_X-1)+Math.clamp((int)z/16,0,CHUNKS_Z-1)*CHUNKS_X);
        if(chunk==null)return false;
        int near=0;
        for(Patch p:chunk.plants)if(!p.removed && (p.kind==Kind.TALL_GRASS || p.kind==Kind.REED || p.kind==Kind.BRACKEN) && Math.abs(p.x-x)<1.1f && Math.abs(p.z-z)<1.1f && ++near>=2)return true;
        return false;
    }
    /**
     * (0.3.6, gamefixes: fire spells passed through the plants without catching them) the plants a segment passes through
     * (from (x, y, z) along the unit (dx, dy, dz) for len metres), nearest first, each as {distance along, plant}: the
     * stand of each plant is a column of its kind's radius and height over where it grows.
     */
    public List<Object[]> along(float x,float y,float z,float dx,float dy,float dz,float len) {
        List<Object[]> hits=new ArrayList<>();
        float ex=x+dx*len,ez=z+dz*len;
        int c0x=Math.clamp((int)(Math.min(x,ex)-1)/16,0,CHUNKS_X-1),c1x=Math.clamp((int)(Math.max(x,ex)+1)/16,0,CHUNKS_X-1);
        int c0z=Math.clamp((int)(Math.min(z,ez)-1)/16,0,CHUNKS_Z-1),c1z=Math.clamp((int)(Math.max(z,ez)+1)/16,0,CHUNKS_Z-1);
        for(int cx=c0x;cx<=c1x;cx++)for(int cz=c0z;cz<=c1z;cz++) {
            Chunk chunk=chunks.get(cx+cz*CHUNKS_X);
            if(chunk==null)continue;
            for(Patch p:chunk.plants) {
                if(p.removed)continue;
                // the nearest the segment comes to the plant's stem, in plan
                float px=p.x-x,pz=p.z-z,flat=dx*dx+dz*dz;
                float t=flat<1e-6f?0:Math.max(0,Math.min(len,(px*dx+pz*dz)/flat));
                float qx=x+dx*t-p.x,qz=z+dz*t-p.z;
                float off=qx*qx+qz*qz,r2=p.kind.radius*p.kind.radius;
                if(off>r2)continue;
                // where the line enters the stand (in plan, along the line's own horizontal run)
                float run=(float)Math.sqrt(flat);
                float entry=run<1e-4f?t:Math.max(0,t-(float)Math.sqrt(r2-off)/run);
                float hy=y+dy*entry;
                if(hy<p.y-0.05f || hy>p.y+p.kind.height)continue;
                hits.add(new Object[]{entry,p});
            }
        }
        hits.sort((a,b)->Float.compare((float)a[0],(float)b[0]));
        return hits;
    }

    /** The live plant nearest a point within a reach, or null. */
    public Patch plantAt(float x,float z,float reach) {
        Chunk chunk=chunks.get(Math.clamp((int)x/16,0,CHUNKS_X-1)+Math.clamp((int)z/16,0,CHUNKS_Z-1)*CHUNKS_X);
        if(chunk==null)return null;
        Patch best=null;float bestD=reach;
        for(Patch p:chunk.plants)if(!p.removed && p.kind!=Kind.IVY && p.kind!=Kind.MOSS){float d=(float)Math.hypot(p.x-x,p.z-z);if(d<bestD){bestD=d;best=p;}}
        return best;
    }
    /**
     * This frame's pushers for the renderer (x, z, radius, y each; up to 8). (0.3.1, gamefixes: plants glitched when
     * stepped in) First the bodies standing or moving in the growth now, nearest the eye - live, so a plant bends over
     * smoothly as a foot comes to it, instead of standing untouched until a trail point was dropped on it and then
     * snapping flat in a frame - then the freshest trail points behind them, each easing back as its plants recover.
     * The shader caps the plants' combined lean, so a point and the body beside it never bend a stem twice over.
     */
    public int pushers(float camX,float camZ,float[] out) {
        int n=0;
        live.clear();
        for(Actor actor:g.actors()) {
            if(!actor.alive || actor.dummy || Math.hypot(actor.x-camX,actor.z-camZ)>36)continue;
            if(actor instanceof rpg.ai.Npc npc && !npc.loaded || actor instanceof rpg.animal.Animal a && !a.loaded)continue;
            if(plantAt(actor.x,actor.z,actor.radius+0.6f)==null)continue;
            live.add(actor);
        }
        live.sort(java.util.Comparator.comparingDouble(a->Math.hypot(a.x-camX,a.z-camZ)));
        for(Actor actor:live) {
            if(n>=5)break;
            // a body's reach into the stems: its own girth and a little more for the legs' stride
            out[n*4]=actor.x;out[n*4+1]=actor.z;out[n*4+2]=actor.radius+0.18f;out[n*4+3]=actor.y;n++;
        }
        var it=trail.descendingIterator();
        while(it.hasNext() && n<8) {
            Trail t=it.next();
            if(Math.hypot(t.x()-camX,t.z()-camZ)>48)continue;
            // a point under a body still standing there adds nothing the body does not (and would take a slot)
            boolean covered=false;
            for(int i=0;i<live.size() && i<5 && !covered;i++){Actor a=live.get(i);covered=Math.hypot(a.x-t.x(),a.z-t.z())<0.3f && Math.abs(a.y-t.y())<0.5f;}
            if(covered)continue;
            float left=(float)(1-(g.time-t.at())/RECOVER);
            if(left<=0)continue;
            // the stems spring back quickly at first, then settle the last of the way slowly
            float k=left*left*(3-2*left);
            out[n*4]=t.x();out[n*4+1]=t.z();out[n*4+2]=(t.radius()+0.18f)*k;out[n*4+3]=t.y();n++;
        }
        return n;
    }
    private final List<Actor> live=new ArrayList<>();
    public float[] pushBuffer(){return push;}

    /** (0.3.1) When each burning plant last flared (it throws its own flame to its neighbours while it burns). */
    private final Map<Long,Double> flaredAt=new HashMap<>();
    public void chemical(Snapshots.Chemical sensor) {
        if((sensor.id()&0x1000000000000L)==0)return;
        // (0.3.1, gamefixes: the grass would not burn) dry growth flares as it burns - tall grass, reeds, bracken and
        // heather most, a bush or bramble long, a moss bed barely - throwing flame to the plants about it, so a fire runs
        // through a meadow (wind carries it; rain and wet growth stop it, as the chemistry has it)
        if((sensor.state()&CHEM_BURNING)!=0 && g.time>=flaredAt.getOrDefault(sensor.id(),-1.0) && g.objects.get(sensor.id()) instanceof Patch patch && !patch.removed) {
            float flare=switch(patch.kind){case TALL_GRASS,REED,BRACKEN,HEATHER,CATTAIL,THISTLE,DOCK->1f;case BRAMBLE,SHRUB,BUSH,FERN,SAPLING,IVY->0.75f;case MOSS,MUSHROOM,CLOVER->0.3f;default->0.6f;};
            flaredAt.put(sensor.id(),g.time+0.4);
            Actor owner=g.actor(sensor.owner());
            g.chemistry.emit(owner,CHEM_FIRE,patch.x,patch.y+0.3f,patch.z,0.55f+0.45f*flare,0.9f*flare,0.3f);
        }
        if(flaredAt.size()>512)flaredAt.entrySet().removeIf(e->g.time>e.getValue()+5);
        if((sensor.state()&CHEM_BURNT)==0)return;
        var object=g.objects.get(sensor.id());if(object instanceof Patch patch && !patch.removed)remove(patch,"burned");
    }
    public void remove(Patch plant,String why) {
        if(plant.removed)return;plant.removed=true;depleted.put(plant.id,g.state.minutes+60);chunks.get(plant.chunk).version++;
        g.game.commands().chemRemove(plant.id);sensed.remove(plant.id);g.state.commit("FoliageRemoved",0,plant.id,plant.jurisdiction(),0,why);
    }
    public Map<String,Object> save(){Map<String,Object> saved=new HashMap<>();for(var entry:depleted.entrySet())saved.put(Long.toString(entry.getKey()),entry.getValue());return saved;}
    public void load(Map<String,Object> saved){epoch++;sensed.clear();chunks.clear();depleted.clear();trail.clear();trailAt.clear();lastPoint.clear();rustleAt.clear();if(saved!=null)for(var entry:saved.entrySet())if(entry.getValue() instanceof Number time)depleted.put(Long.parseLong(entry.getKey()),time.doubleValue());next=0;}
}
