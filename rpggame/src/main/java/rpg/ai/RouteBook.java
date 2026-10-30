package rpg.ai;

import java.util.*;
import astratto.util.Json;
import rpg.world.Voxels;

/** Personal, bounded travel memory. Only completed trips become verified routes; failures survive saves. */
public final class RouteBook {
    public record Landmark(String kind,float x,float z) {}
    public static final class Route {
        public float sx,sz,x,z,cost,confidence; public double lastUsed,avoidUntil;public int failures;
        public float[][] points=new float[0][];
        public final Map<Long,Long> versions=new HashMap<>();
    }
    public final List<Landmark> landmarks=new ArrayList<>();
    public final List<Route> routes=new ArrayList<>();
    private Route travelling;private double began;private int[] planned;
    public void seed(Npc n){if(landmarks.isEmpty()){landmarks.add(new Landmark("home",n.homeX,n.homeZ));landmarks.add(new Landmark("work",n.postX,n.postZ));}}
    private Route find(float sx,float sz,float x,float z){for(var route:routes)if(Math.hypot(route.sx-sx,route.sz-sz)<5 && Math.hypot(route.x-x,route.z-z)<1)return route;return null;}
    public double avoiding(float x,float z,double now){double until=0;for(var route:routes)if(Math.hypot(route.x-x,route.z-z)<3)until=Math.max(until,route.avoidUntil);return Math.max(now,until);}
    public int[] recall(Npc n,float x,float z,Voxels vox,double now,java.util.function.Predicate<rpg.objects.Door> doors){
        var route=find(n.x,n.z,x,z);if(route==null || route.confidence*(float)Math.exp(-Math.max(0,now-route.lastUsed)/7200)<.35f || route.avoidUntil>now)return null;
        for(var entry:route.versions.entrySet())if(vox.chunkVersion((int)(entry.getKey()>>32)*16,(int)(long)entry.getKey()*16)!=entry.getValue())return null;
        int[] result=new int[route.points.length];float px=n.x,pz=n.z;
        for(int i=0;i<result.length;i++){var point=route.points[i];result[i]=Waypoints.index(point[0],point[1]);if(result[i]<0 || !Waypoints.walkable(vox,px,pz,point[0],point[1],doors))return null;px=point[0];pz=point[1];}
        if(!Waypoints.walkable(vox,px,pz,x,z,doors))return null;
        route.lastUsed=now;return result;
    }
    public void begin(Npc n,float x,float z,int[] plan,double now){
        travelling=find(n.x,n.z,x,z);
        if(travelling==null){travelling=new Route();travelling.sx=n.x;travelling.sz=n.z;travelling.x=x;travelling.z=z;routes.add(travelling);if(routes.size()>32)routes.remove(0);}
        began=now;planned=plan.clone();
    }
    public void cancel(){travelling=null;}
    public void success(Voxels vox,double now,float x,float y,float z){
        if(travelling==null)return;
        if(Math.hypot(travelling.x-x,travelling.z-z)>.5 || Math.abs(y-rpg.world.DevMap.FLOOR)>1.2){cancel();return;}var route=travelling;route.confidence=Math.min(1,route.confidence+.4f);route.cost=(float)Math.max(.01,now-began);route.lastUsed=now;route.failures=0;route.avoidUntil=0;
        route.points=new float[planned.length][];route.versions.clear();float px=route.sx,pz=route.sz;
        for(int i=0;i<planned.length;i++){route.points[i]=Waypoints.point(planned[i]).clone();stamp(route,vox,px,pz,route.points[i][0],route.points[i][1]);px=route.points[i][0];pz=route.points[i][1];}
        stamp(route,vox,px,pz,route.x,route.z);travelling=null;
    }
    private static void stamp(Route route,Voxels vox,float x,float z,float tx,float tz){
        for(int cx=Math.floorDiv((int)Math.min(x,tx),16);cx<=Math.floorDiv((int)Math.max(x,tx),16);cx++)for(int cz=Math.floorDiv((int)Math.min(z,tz),16);cz<=Math.floorDiv((int)Math.max(z,tz),16);cz++)route.versions.put(((long)cx<<32)^(cz&0xffffffffL),vox.chunkVersion(cx*16,cz*16));
    }
    public double failed(Npc n,double now){
        var route=travelling;if(route==null){route=find(n.x,n.z,n.legsX,n.legsZ);if(route==null){route=new Route();route.sx=n.x;route.sz=n.z;route.x=n.legsX;route.z=n.legsZ;routes.add(route);if(routes.size()>32)routes.remove(0);}}
        route.confidence*=.35f;route.failures++;route.lastUsed=now;route.avoidUntil=now+(route.failures>=2?30:2);travelling=null;return route.avoidUntil;
    }
    public int tell(RouteBook other,double now){
        int count=0;for(var source:routes)if(source.confidence>.5f && now-source.lastUsed<1800 && other.find(source.sx,source.sz,source.x,source.z)==null){
            var copy=new Route();copy.sx=source.sx;copy.sz=source.sz;copy.x=source.x;copy.z=source.z;copy.cost=source.cost;copy.confidence=source.confidence*.65f;copy.lastUsed=now;copy.points=Arrays.stream(source.points).map(float[]::clone).toArray(float[][]::new);other.routes.add(copy);count++;if(other.routes.size()>32)other.routes.remove(0);
        }return count;
    }
    public List<Object> save(double now){List<Object> out=new ArrayList<>();for(var route:routes){var row=new HashMap<String,Object>();row.put("sx",route.sx);row.put("sz",route.sz);row.put("x",route.x);row.put("z",route.z);row.put("cost",route.cost);row.put("confidence",route.confidence);row.put("age",Math.max(0,now-route.lastUsed));row.put("avoid",Math.max(0,route.avoidUntil-now));row.put("failures",route.failures);row.put("points",Arrays.stream(route.points).map(p->List.of(p[0],p[1])).toList());out.add(row);}return out;}
    @SuppressWarnings("unchecked") public void load(List<Object> rows,double now){routes.clear();travelling=null;for(Object value:rows){if(routes.size()>=32)break;var row=(Map<String,Object>)value;var route=new Route();route.sx=(float)Json.num(row,"sx",0);route.sz=(float)Json.num(row,"sz",0);route.x=(float)Json.num(row,"x",0);route.z=(float)Json.num(row,"z",0);route.cost=(float)Json.num(row,"cost",0);route.confidence=Math.max(0,Math.min(1,(float)Json.num(row,"confidence",0)));route.lastUsed=now-Json.num(row,"age",0);route.avoidUntil=now+Json.num(row,"avoid",0);route.failures=Json.integer(row,"failures",0);var points=Json.list(row,"points");route.points=new float[Math.min(64,points.size())][];for(int i=0;i<route.points.length;i++){var point=(List<Number>)points.get(i);route.points[i]=new float[]{point.get(0).floatValue(),point.get(1).floatValue()};}routes.add(route);}}
}
