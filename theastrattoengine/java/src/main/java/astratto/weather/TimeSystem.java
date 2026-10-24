package astratto.weather;

import java.util.ArrayList;
import java.util.List;

/** Reads a caller-owned clock: advancing/saving time stays the game's responsibility. */
public final class TimeSystem {
    /** Linear RGB filters, blended across dawn/dusk and region borders. */
    public record Profile(float dayR,float dayG,float dayB,float nightR,float nightG,float nightB,float fog) {
        public static final Profile NEUTRAL = new Profile(1,1,1,1,1,1,1);
    }
    public record Region(String name,float x0,float z0,float x1,float z1,float blend,Profile profile) {
        public Region { if(x1<=x0 || z1<=z0 || blend<0 || profile==null)throw new IllegalArgumentException("Invalid time region"); }
        float weight(float x,float z) {
            float inside=Math.min(Math.min(x-x0,x1-x),Math.min(z-z0,z1-z));
            if(inside < -blend)return 0;
            float t=blend==0?1:Math.max(0,Math.min(1,(inside+blend)/(2*blend))); return t*t*(3-2*t);
        }
    }
    public record Fog(float x,float y,float z,float radius,float density,float red,float green,float blue) {}
    public final List<Fog> fogVolumes = new ArrayList<>();
    public final List<Region> regions = new ArrayList<>();
    public final Weather weather = new Weather();
    public float tintR=1,tintG=1,tintB=1,fogScale=1;
    private long weatherEpoch=Long.MIN_VALUE;
    public static float daylight(double hour) {
        double sun=Math.sin((hour-6)/12*Math.PI);
        float t=(float)Math.max(0,Math.min(1,(sun+0.3)/0.7));
        return 0.05f+0.95f*t*t*(3-2*t);
    }
    public java.util.Map<String,Object> save() {
        java.util.Map<String,Object> out=new java.util.HashMap<>();
        out.put("epoch",weatherEpoch);out.put("kind",weather.kind().name());out.put("wet",weather.wetness);out.put("snowCover",weather.snowCover);
        out.put("rain",weather.rain);out.put("snow",weather.snow);out.put("fog",weather.fog);out.put("cloud",weather.overcast);out.put("wind",weather.wind);
        out.put("phase",weather.time);return out;
    }
    public void load(java.util.Map<String,Object> state) {
        weatherEpoch=Long.MIN_VALUE;weather.clearTransient();
        if(state==null) { weather.set(Weather.Kind.CLEAR);weather.snap();return; }
        weatherEpoch=astratto.util.Json.lng(state,"epoch",Long.MIN_VALUE);
        weather.set(Weather.Kind.of(astratto.util.Json.str(state,"kind","CLEAR")));
        weather.rain=(float)astratto.util.Json.num(state,"rain",0);weather.snow=(float)astratto.util.Json.num(state,"snow",0);
        weather.fog=(float)astratto.util.Json.num(state,"fog",0);weather.overcast=(float)astratto.util.Json.num(state,"cloud",0);
        weather.wind=(float)astratto.util.Json.num(state,"wind",0);weather.wetness=(float)astratto.util.Json.num(state,"wet",0);
        weather.snowCover=(float)astratto.util.Json.num(state,"snowCover",0);weather.time=(float)astratto.util.Json.num(state,"phase",0);
    }

    public void update(double minutes,float dt,float x,float y,float z) {
        long epoch=(long)Math.floor(minutes/240);
        if(epoch!=weatherEpoch) {
            weatherEpoch=epoch;
            int roll=(int)Math.floorMod(epoch*6364136223846793005L+1442695040888963407L,100);
            weather.set(roll<55?Weather.Kind.CLEAR:roll<82?Weather.Kind.OVERCAST:roll<97?Weather.Kind.RAIN:Weather.Kind.STORM);
        }
        weather.update(Math.min(0.25f,Math.max(0,dt)),x,y,z,null,null);
        float day=daylight(minutes/60%24),red=0,green=0,blue=0,fog=0,weight=0;
        for(Region region:regions) {
            float w=region.weight(x,z); if(w<=0)continue;
            Profile p=region.profile;
            red+=(p.nightR+(p.dayR-p.nightR)*day)*w;
            green+=(p.nightG+(p.dayG-p.nightG)*day)*w;
            blue+=(p.nightB+(p.dayB-p.nightB)*day)*w;
            fog+=p.fog*w;weight+=w;
        }
        if(weight<1) { float neutral=1-weight;red+=neutral;green+=neutral;blue+=neutral;fog+=neutral; }
        else { red/=weight;green/=weight;blue/=weight;fog/=weight; }
        float ease=1-(float)Math.exp(-Math.max(0,dt)/0.8f);
        tintR+=(red-tintR)*ease;tintG+=(green-tintG)*ease;tintB+=(blue-tintB)*ease;fogScale+=(fog-fogScale)*ease;
    }
}
