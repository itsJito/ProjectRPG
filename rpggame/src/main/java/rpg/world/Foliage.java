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
import rpg.interact.Work;
import rpg.interact.WorldObject;
import static astratto.bridge.AstrattoNative.*;

/** Deterministic surface-driven growth, generated a chunk at a time. Saves retain depletion, not thousands of fixtures. */
public final class Foliage {
    public enum Kind { FERN,TALL_GRASS,CLOVER,REED,BRAMBLE,WILDFLOWER,IVY,MOSS,MUSHROOM,SAPLING }
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
        private final Foliage field;
        Patch(Foliage field,long id,Kind kind,int seed,int chunk,float x,float y,float z,float facing) {
            super(id,"foliage",kind.name().replace('_',' ').toLowerCase(java.util.Locale.ROOT),x,y,z);this.field=field;this.kind=kind;this.seed=seed;this.chunk=chunk;this.facing=facing;
        }
        @Override public String examine(Rpg g){return "Naturally growing "+name+". It can be cut, burned or disturbed by wind.";}
        @Override public float focusRadius(){return kind==Kind.IVY?0.6f:0.35f;}
        @Override public List<Option> options(Rpg g,Actor actor){return List.of(Option.on("cut","Cut back "+name,2,2),Option.on("examine","Examine",0,4));}
        @Override public Result perform(Rpg g,Actor actor,String action,long request) {
            if(action.equals("examine")){if(actor==g.player)g.chat.say("Naturally growing "+name+". It can be cut, burned or disturbed by wind.");return Result.ok();}
            if(!action.equals("cut"))return Result.no("You cannot do that.");String why=reach(g,actor,2);if(why!=null)return Result.no(why);
            g.interact.begin(new Work(actor,this,"cut","Cutting back",0.6f,false,g.time,()->{
                String range=reach(g,actor,2);if(range!=null)return Result.no(range);field.remove(this,"cut back");return Result.ok();
            }));return Result.started();
        }
    }
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
            boolean wall=false;for(int[] d:SIDES){int block=g.vox.block(x+d[0],y,z+d[1]);if(block==Blocks.STONE || block==Blocks.BRICK || block==Blocks.PLANKS || block==Blocks.LOG || block==Blocks.BEAM)wall=true;}
            return wall && (kind==Kind.IVY || sky<0.7f);
        }
        if(!earth || g.vox.block(x,y+1,z)!=Blocks.AIR)return false;
        return switch(kind){case FERN,MUSHROOM->sky<0.65f;case REED->nearWater(x,y,z);case BRAMBLE,SAPLING->sky>0.3f && g.vox.block(x,y+2,z)==Blocks.AIR;default->sky>0.35f;};
    }
    private static final int[][] SIDES={{1,0},{-1,0},{0,1},{0,-1}};
    private boolean nearWater(int x,int y,int z){for(int[] direction:SIDES)for(int step=1;step<=4;step++)if(g.vox.block(x+direction[0]*step,y-1,z+direction[1]*step)==Blocks.WATER)return true;return false;}
    private Chunk generate(int cx,int cz) {
        int key=cx+cz*8;Chunk chunk=new Chunk(key,cx*16,cz*16);
        for(int x=chunk.x+1;x<chunk.x+16;x+=2)for(int z=chunk.z+1;z<chunk.z+16;z+=2) {
            int seed=hash(x,z);if((seed&3)!=0)continue;Kind kind=Kind.values()[Math.floorMod(seed>>>3,Kind.values().length)];int y=DevMap.FLOOR;
            if(!suitable(kind,x,y,z))continue;
            float facing=(seed>>>16)*0.00009587f;
            if(kind==Kind.IVY || kind==Kind.MOSS)for(int[] direction:SIDES) {
                int wall=g.vox.block(x+direction[0],y,z+direction[1]);
                if(wall==Blocks.STONE || wall==Blocks.BRICK || wall==Blocks.PLANKS || wall==Blocks.LOG || wall==Blocks.BEAM){facing=(float)Math.atan2(direction[0],direction[1]);break;}
            }
            long id=id(x,y,z);Patch plant=new Patch(this,id,kind,seed,key,x+0.5f,y,z+0.5f,facing);
            plant.removed=depleted.getOrDefault(id,-1.0)>g.state.minutes;chunk.plants.add(plant);g.objects.add(plant);
        }
        chunk.version++;chunks.put(key,chunk);return chunk;
    }
    public void update() {
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
    public void chemical(Snapshots.Chemical sensor) {
        if((sensor.state()&CHEM_BURNT)==0 || (sensor.id()&0x1000000000000L)==0)return;
        var object=g.objects.get(sensor.id());if(object instanceof Patch patch && !patch.removed)remove(patch,"burned");
    }
    public void remove(Patch plant,String why) {
        if(plant.removed)return;plant.removed=true;depleted.put(plant.id,g.state.minutes+60);chunks.get(plant.chunk).version++;
        g.game.commands().chemRemove(plant.id);g.state.commit("FoliageRemoved",0,plant.id,plant.jurisdiction(),0,why);
    }
    public Map<String,Object> save(){Map<String,Object> saved=new HashMap<>();for(var entry:depleted.entrySet())saved.put(Long.toString(entry.getKey()),entry.getValue());return saved;}
    public void load(Map<String,Object> saved){epoch++;chunks.clear();depleted.clear();if(saved!=null)for(var entry:saved.entrySet())if(entry.getValue() instanceof Number time)depleted.put(Long.parseLong(entry.getKey()),time.doubleValue());next=0;}
}
