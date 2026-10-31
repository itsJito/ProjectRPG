package rpg.ai;

import java.util.*;
import java.util.function.Predicate;
import rpg.objects.Door;
import rpg.world.*;

/** Runtime surface graph. Chunks own their cells; crossing a chunk boundary is a portal.
 * The graph is independent of authored roads and retains each storey's actual height.
 * Only edited chunks are rebuilt. Short native legs execute the verified coarse route. */
public final class RuntimeNavigation {
    private final Voxels vox;
    private static final astratto.bridge.NativeEngine.BlockDef[] PHYSICS=Blocks.engineDefs();
    private boolean solid(int x,int y,int z){return (PHYSICS[vox.block(x,y,z)].flags & astratto.bridge.AstrattoNative.BLOCK_SOLID)!=0;}
    public float standY(float x,float near,float z){
        int bx=(int)Math.floor(x),bz=(int)Math.floor(z),mid=(int)Math.floor(near);
        for(int d=0;d<=12;d++)for(int y:new int[]{mid-d,mid+d})if(surface(bx,y,bz))return y;
        return Float.NaN;
    }
    private final Map<Integer,Chunk> chunks = new HashMap<>();
    private final Map<Long,Door> doors = new HashMap<>();
    public long searches, expansions, rebuiltChunks, failures, regionExpansions;
    private record Chunk(long version, Set<Integer> surfaces, Map<Integer,Integer> regions, Map<Integer,List<Integer>> members) {}
    private record Visit(int cell, float cost, float estimate) {}
    private static final int[][] DIR = {{1,0},{-1,0},{0,1},{0,-1}};
    public RuntimeNavigation(Voxels vox) { this.vox=vox; }
    public static int key(int x,int y,int z) { return x | z<<10 | y<<20; }
    public static int x(int k) { return k&1023; }
    public static int y(int k) { return (k>>>20)&1023; }
    public static int z(int k) { return (k>>>10)&1023; }
    /** Stable coordinate handles, unlike transient designer waypoint indices. */
    public static int handle(int cell) { return cell|Integer.MIN_VALUE; }
    public static float[] point(int handle) { return new float[]{x(handle)+.5f,z(handle)+.5f,y(handle)}; }
    public void doors(List<Door> values) {
        doors.clear();for(var d:values)for(int[] c:d.cells)doors.put(((long)c[0]<<32)|(c[1]&0xffffffffL),d);
    }
    private boolean pass(int x,int y,int z) {
        int b=vox.block(x,y,z);
        return (b==Blocks.DOOR || b==Blocks.LOCKED_DOOR || !solid(x,y,z)) && b!=Blocks.EMBERS && (b!=Blocks.WATER || vox.block(x,y+1,z)!=Blocks.WATER);
    }
    private boolean surface(int x,int y,int z) {
        return x>0 && z>0 && x<vox.sizeX()-1 && z<vox.sizeZ()-1 && y>0 && y<vox.sizeY()-2
            && pass(x,y,z) && pass(x,y+1,z) && (solid(x,y-1,z) || ladder(x,y,z) || ladder(x,y-1,z));
    }
    private boolean ladder(int x,int y,int z) { return Blocks.ladder(vox.block(x,y,z)); }
    private Chunk chunk(int x,int z) {
        int cx=x>>4,cz=z>>4,id=cx<<16|cz;
        long version=vox.chunkVersion(x,z);Chunk old=chunks.get(id);
        if(old!=null && old.version==version)return old;
        Set<Integer> cells=new HashSet<>();
        for(int bx=Math.max(1,cx*16);bx<Math.min(vox.sizeX()-1,cx*16+16);bx++)
            for(int bz=Math.max(1,cz*16);bz<Math.min(vox.sizeZ()-1,cz*16+16);bz++)
                for(int by=1;by<vox.sizeY()-2;by++)if(surface(bx,by,bz))cells.add(key(bx,by,bz));
        Map<Integer,Integer> regions=new HashMap<>();Map<Integer,List<Integer>> members=new HashMap<>();
        // A doorway, ladder or height transition is a portal; ordinary connected floor is a zone.
        for(int seed:new TreeSet<>(cells))if(!regions.containsKey(seed)){
            var queue=new ArrayDeque<Integer>();var area=new ArrayList<Integer>();queue.add(seed);regions.put(seed,seed);
            while(!queue.isEmpty()){
                int c=queue.removeFirst();area.add(c);
                if(portal(c))continue;
                for(int[] d:DIR){int n=key(x(c)+d[0],y(c),z(c)+d[1]);
                    if(cells.contains(n) && !portal(n) && !regions.containsKey(n)){regions.put(n,seed);queue.add(n);}
                }
            }members.put(seed,area);
        }
        var result=new Chunk(version,cells,regions,members);chunks.put(id,result);rebuiltChunks++;return result;
    }
    private boolean portal(int c){int b=vox.block(x(c),y(c),z(c));return b==Blocks.DOOR || b==Blocks.LOCKED_DOOR || ladder(x(c),y(c),z(c)) || ladder(x(c),y(c)-1,z(c));}
    private int region(int c){return chunk(x(c),z(c)).regions.getOrDefault(c,-1);}
    private int[] neighbours(int c,boolean climb){
        int x=x(c),y=y(c),z=z(c);int[] out=new int[6];Arrays.fill(out,-1);
        for(int i=0;i<4;i++)out[i]=step(x+DIR[i][0],y,z+DIR[i][1],climb);
        if(climb && ladder(x,y,z) && exists(x,y+1,z))out[4]=key(x,y+1,z);
        if(climb && ladder(x,y-1,z) && exists(x,y-1,z))out[5]=key(x,y-1,z);
        return out;
    }
    private Set<Integer> corridor(int start,int goal,Predicate<Door> permission,boolean climb,RouteBook book,double now){
        int from=region(start),to=region(goal);Map<Integer,Integer> previous=new HashMap<>();Map<Integer,Float> costs=new HashMap<>();
        var queue=new PriorityQueue<Visit>(Comparator.comparingDouble(Visit::estimate).thenComparingInt(Visit::cell));
        costs.put(from,0f);queue.add(new Visit(from,0,0));
        while(!queue.isEmpty()){
            var visit=queue.remove();int zone=visit.cell;if(visit.cost>costs.getOrDefault(zone,Float.MAX_VALUE))continue;
            regionExpansions++;
            if(zone==to){Set<Integer> result=new HashSet<>();for(int c=to;;c=previous.get(c)){result.add(c);if(c==from)break;}return result;}
            for(int c:chunk(x(zone),z(zone)).members.get(zone))for(int next:neighbours(c,climb)){
                if(next<0 || !allowed(x(next),z(next),permission))continue;int other=region(next);if(other==zone)continue;
                float price=visit.cost+1+(book==null?0:book.penalty(x(next)+.5f,y(next),z(next)+.5f,now));
                if(price>=costs.getOrDefault(other,Float.MAX_VALUE))continue;
                costs.put(other,price);previous.put(other,zone);queue.add(new Visit(other,price,price));
            }
        }return null;
    }
    public boolean validPoint(float x,float y,float z,Predicate<Door> permission){return exists((int)Math.floor(x),Math.round(y),(int)Math.floor(z)) && allowed((int)Math.floor(x),(int)Math.floor(z),permission);}
    public boolean validLeg(float ax,float ay,float az,float bx,float by,float bz,Predicate<Door> permission){
        int steps=Math.max(1,(int)Math.ceil(Math.max(Math.hypot(bx-ax,bz-az),Math.abs(by-ay))*3));
        for(int i=0;i<=steps;i++){float t=i/(float)steps;int x=(int)Math.floor(ax+(bx-ax)*t),z=(int)Math.floor(az+(bz-az)*t),y=Math.round(ay+(by-ay)*t);
            if(!allowed(x,z,permission) || !exists(x,y,z))return false;
        }return true;
    }
    private boolean exists(int x,int y,int z) {
        return x>0 && z>0 && x<vox.sizeX()-1 && z<vox.sizeZ()-1 && y>0 && y<vox.sizeY()-2 && chunk(x,z).surfaces.contains(key(x,y,z));
    }
    private boolean allowed(int x,int z,Predicate<Door> permission) {
        Door door=doors.get(((long)x<<32)|(z&0xffffffffL));return door==null || permission.test(door);
    }
    private int step(int x,int y,int z,boolean climb) {
        for(int dy:new int[]{0,1,-1,-2,-3}) {
            int h=y+dy;
            if(exists(x,h,z) && (climb || !ladder(x,h,z) && !ladder(x,h-1,z)))return key(x,h,z);
            if(dy<0 && solid(x,h,z))break;
        }
        return -1;
    }
    private static float estimate(int a,int b) { return Math.abs(x(a)-x(b))+Math.abs(z(a)-z(b))+.7f*Math.abs(y(a)-y(b)); }
    /** null means no verified route, never a successful empty/partial path. */
    public int[] route(float sx,float sy,float sz,float tx,float ty,float tz,Predicate<Door> permission,boolean climb,long person,RouteBook book,double now) {
        searches++;
        int start=key((int)Math.floor(sx),Math.round(sy),(int)Math.floor(sz));
        int goal=key((int)Math.floor(tx),Math.round(ty),(int)Math.floor(tz));
        if(!exists(x(start),y(start),z(start))){
            float supported=standY(sx,sy,sz);
            if(Float.isFinite(supported) && Math.abs(supported-sy)<1.2f)start=key((int)Math.floor(sx),Math.round(supported),(int)Math.floor(sz));
        }
        if(!exists(x(start),y(start),z(start)) || !exists(x(goal),y(goal),z(goal))) {failures++;return null;}
        if(Math.abs(ty-sy)<.1f && Math.hypot(tx-sx,tz-sz)<22 && validLeg(sx,sy,sz,tx,ty,tz,permission) && (climb || !ladder(x(goal),y(goal),z(goal))))return new int[]{handle(goal)};
        Set<Integer> corridor=corridor(start,goal,permission,climb,book,now);if(corridor==null){failures++;return null;}
        // Nearby trips need no extra stopping points beside swinging door leaves.
        // Region connectivity still validates permissions and ladder capability first.
        if(Math.hypot(tx-sx,tz-sz)<22)return new int[]{handle(goal)};
        Map<Integer,Float> cost=new HashMap<>();Map<Integer,Integer> parent=new HashMap<>();
        var queue=new PriorityQueue<Visit>(Comparator.comparingDouble(Visit::estimate).thenComparingInt(Visit::cell));
        cost.put(start,0f);queue.add(new Visit(start,0,estimate(start,goal)));
        boolean reached=false;
        while(!queue.isEmpty()) {
            Visit at=queue.remove();if(at.cost>cost.getOrDefault(at.cell,Float.MAX_VALUE))continue;
            expansions++;if(at.cell==goal){reached=true;break;}
            int x=x(at.cell),y=y(at.cell),z=z(at.cell);
            int[] next=neighbours(at.cell,climb);
            for(int cell:next) {
                if(cell<0 || !corridor.contains(region(cell)) || !allowed(x(cell),z(cell),permission))continue;
                // Stable small personal costs spread equivalent routes without random replanning.
                float noise=((Integer.rotateLeft(cell,13)^(int)person)*0x9e3779b9>>>24)/255f*.08f;
                float rise=Math.max(0,y(cell)-y),extra=rise*(Blocks.stairs(vox.block(x(cell),y(cell)-1,z(cell)))?.6f:7f);
                if(x(cell)==x && z(cell)==z)extra=.5f;
                float value=at.cost+1+extra+noise+(book==null?0:book.penalty(x(cell)+.5f,y(cell),z(cell)+.5f,now));
                if(value>=cost.getOrDefault(cell,Float.MAX_VALUE))continue;
                cost.put(cell,value);parent.put(cell,at.cell);queue.add(new Visit(cell,value,value+estimate(cell,goal)));
            }
        }
        if(!reached){failures++;return null;}
        List<Integer> cells=new ArrayList<>();for(int c=goal;c!=start;c=parent.get(c))cells.add(c);Collections.reverse(cells);
        List<Integer> portals=new ArrayList<>();int prior=start,last=start;
        for(int i=0;i<cells.size();i++) {
            int c=cells.get(i),next=i+1<cells.size()?cells.get(i+1):c;
            boolean turn=x(c)-x(prior)!=x(next)-x(c) || z(c)-z(prior)!=z(next)-z(c);
            boolean boundary=x(c)/16!=x(prior)/16 || z(c)/16!=z(prior)/16;
            if(turn || boundary || y(c)!=y(prior) || estimate(last,c)>=8 || i==cells.size()-1){portals.add(handle(c));last=c;}
            prior=c;
        }
        return portals.stream().mapToInt(Integer::intValue).toArray();
    }
}
