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
    public enum Kind { FERN,TALL_GRASS,CLOVER,REED,BRAMBLE,WILDFLOWER,IVY,MOSS,MUSHROOM,SAPLING,SHRUB,THISTLE,DAISY,BLUEBELL,CATTAIL,HEATHER,DOCK,BRACKEN }
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
    private static final double RECOVER=2.2;
    private final float[] push=new float[32];
    private final Rpg g;
    public final Map<Integer,Chunk> chunks=new HashMap<>();
    private final Map<Long,Double> depleted=new HashMap<>();
    public int epoch;
    private double next;
    public Foliage(Rpg g){this.g=g;}
    private static int hash(int x,int z){int n=x*374761393+z*668265263;n=(n^(n>>>13))*1274126177;return n^(n>>>16);}
    public static long id(int x,int y,int z){return 0x1000000000000L|((long)x<<32)|((long)y<<16)|z;}
    public boolean suitable(Kind kind,int x,int y,int z) {
        if(x<1 || z<1 || x>=DevMap.SX-1 || z>=DevMap.SZ-1 || g.vox.block(x,y,z)!=Blocks.AIR)return false;
        int soil=g.vox.block(x,y-1,z);boolean earth=soil==Blocks.GRASS || soil==Blocks.DIRT || soil==Blocks.BARE_PATCH;
        float sky=g.vox.skyLight(x,y+1,z);
        if(kind==Kind.IVY || kind==Kind.MOSS) {
            boolean wall=false;for(int[] d:SIDES)if(climbable(g.vox.block(x+d[0],y,z+d[1])))wall=true;
            return wall && (kind==Kind.IVY || sky<0.7f);
        }
        if(!earth || g.vox.block(x,y+1,z)!=Blocks.AIR)return false;
        return switch(kind){
            case FERN,MUSHROOM,BRACKEN->sky<0.65f;
            case REED,CATTAIL->nearWater(x,y,z);
            case BLUEBELL->sky<0.8f;
            case BRAMBLE,SAPLING,SHRUB->sky>0.3f && g.vox.block(x,y+2,z)==Blocks.AIR;
            default->sky>0.35f;
        };
    }
    /** A wall ivy and moss can climb: stone, brick or wood of any kind. */
    private static boolean climbable(int block){var m=Blocks.material(block);return block!=Blocks.GLASS && (m==Blocks.Material.STONE || m==Blocks.Material.WOOD) && Blocks.traversable(block);}
    private static final int[][] SIDES={{1,0},{-1,0},{0,1},{0,-1}};
    private boolean nearWater(int x,int y,int z){for(int[] direction:SIDES)for(int step=1;step<=4;step++)if(g.vox.block(x+direction[0]*step,y-1,z+direction[1]*step)==Blocks.WATER)return true;return false;}
    private Chunk generate(int cx,int cz) {
        int key=cx+cz*8;Chunk chunk=new Chunk(key,cx*16,cz*16);
        // every other cell is a candidate; half of them grow something (gamefixes: more foliage), and a plant that does
        // not suit its spot gives way to the next kind that does, so the shade, the water's edge and the open each fill
        for(int x=chunk.x+1;x<chunk.x+16;x+=2)for(int z=chunk.z+1;z<chunk.z+16;z+=2) {
            int seed=hash(x,z);if((seed&1)!=0)continue;int y=DevMap.FLOOR;
            int first=Math.floorMod(seed>>>3,Kind.values().length);Kind kind=null;
            for(int k=0;k<4 && kind==null;k++){Kind trial=Kind.values()[(first+k*5)%Kind.values().length];if(suitable(trial,x,y,z))kind=trial;}
            if(kind==null)continue;
            float facing=(seed>>>16)*0.00009587f;
            if(kind==Kind.IVY || kind==Kind.MOSS)for(int[] direction:SIDES) {
                int wall=g.vox.block(x+direction[0],y,z+direction[1]);
                if(climbable(wall)){facing=(float)Math.atan2(direction[0],direction[1]);break;}
            }
            // off the cell's centre a little, so the rows of a lattice never show
            float ox=((seed>>>20)&15)/15f*0.5f-0.25f,oz=((seed>>>24)&15)/15f*0.5f-0.25f;
            if(kind==Kind.IVY || kind==Kind.MOSS){ox=0;oz=0;}
            long id=id(x,y,z);Patch plant=new Patch(id,kind,seed,key,x+0.5f+ox,y,z+0.5f+oz,facing);
            plant.removed=depleted.getOrDefault(id,-1.0)>g.state.minutes;chunk.plants.add(plant);g.objects.add(plant);
        }
        chunk.version++;chunks.put(key,chunk);return chunk;
    }
    public void update() {
        trail();
        if(g.time<next)return;next=g.time+0.25;
        int centerX=Math.clamp((int)g.player.x/16,0,7),centerZ=Math.clamp((int)g.player.z/16,0,7);
        boolean generated=false;
        for(int ring=0;ring<=2 && !generated;ring++)for(int dz=-ring;dz<=ring && !generated;dz++)for(int dx=-ring;dx<=ring;dx++) {
            int x=centerX+dx,z=centerZ+dz;if(x<0 || z<0 || x>=8 || z>=8 || chunks.containsKey(x+z*8))continue;generate(x,z);generated=true;break;
        }
        for(Chunk chunk:chunks.values())for(Patch plant:chunk.plants) {
            if(plant.removed) {
                if(depleted.getOrDefault(plant.id,Double.MAX_VALUE)<=g.state.minutes && suitable(plant.kind,(int)plant.x,(int)plant.y,(int)plant.z)) {depleted.remove(plant.id);plant.removed=false;chunk.version++;}
                else continue;
            }
            if(!suitable(plant.kind,(int)plant.x,(int)plant.y,(int)plant.z)){remove(plant,"lost its growing surface");continue;}
            if(Math.hypot(plant.x-g.player.x,plant.z-g.player.z)>32)continue;
            g.game.commands().chemSensor(plant.id,0,plant.x,plant.y+0.25f,plant.z,0.3f,CHEM_FLAMMABLE|CHEM_WIND_SENSITIVE,0,2.5f,0,0,false);
        }
    }
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
            // a grown plant, or a planted patch (herbs, flax, a berry bush): both bend and rustle as a body goes through
            boolean patch=Blocks.plant(g.vox.block((int)Math.floor(actor.x),(int)Math.floor(actor.y+0.1f),(int)Math.floor(actor.z)));
            if(!patch && plantAt(actor.x,actor.z,actor.radius+0.35f)==null)continue;
            trailAt.put(actor.id,g.time+0.2);
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
    /** The live plant nearest a point within a reach, or null. */
    public Patch plantAt(float x,float z,float reach) {
        Chunk chunk=chunks.get(Math.clamp((int)x/16,0,7)+Math.clamp((int)z/16,0,7)*8);
        if(chunk==null)return null;
        Patch best=null;float bestD=reach;
        for(Patch p:chunk.plants)if(!p.removed && p.kind!=Kind.IVY && p.kind!=Kind.MOSS){float d=(float)Math.hypot(p.x-x,p.z-z);if(d<bestD){bestD=d;best=p;}}
        return best;
    }
    /**
     * This frame's pushers for the renderer (x, z, radius, y each; up to 8): the freshest trail points nearest the eye,
     * each shrinking as the plants recover. Returns how many.
     */
    public int pushers(float camX,float camZ,float[] out) {
        int n=0;
        var it=trail.descendingIterator();
        while(it.hasNext() && n<8) {
            Trail t=it.next();
            if(Math.hypot(t.x()-camX,t.z()-camZ)>48)continue;
            float left=(float)(1-(g.time-t.at())/RECOVER);
            if(left<=0)continue;
            out[n*4]=t.x();out[n*4+1]=t.z();out[n*4+2]=t.radius()*left*left;out[n*4+3]=t.y();n++;
        }
        return n;
    }
    public float[] pushBuffer(){return push;}

    public void chemical(Snapshots.Chemical sensor) {
        if((sensor.state()&CHEM_BURNT)==0 || (sensor.id()&0x1000000000000L)==0)return;
        var object=g.objects.get(sensor.id());if(object instanceof Patch patch && !patch.removed)remove(patch,"burned");
    }
    public void remove(Patch plant,String why) {
        if(plant.removed)return;plant.removed=true;depleted.put(plant.id,g.state.minutes+60);chunks.get(plant.chunk).version++;
        g.game.commands().chemRemove(plant.id);g.state.commit("FoliageRemoved",0,plant.id,plant.jurisdiction(),0,why);
    }
    public Map<String,Object> save(){Map<String,Object> saved=new HashMap<>();for(var entry:depleted.entrySet())saved.put(Long.toString(entry.getKey()),entry.getValue());return saved;}
    public void load(Map<String,Object> saved){epoch++;chunks.clear();depleted.clear();trail.clear();trailAt.clear();rustleAt.clear();if(saved!=null)for(var entry:saved.entrySet())if(entry.getValue() instanceof Number time)depleted.put(Long.parseLong(entry.getKey()),time.doubleValue());next=0;}
}
