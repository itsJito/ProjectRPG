package rpg.objects;

import astratto.util.Json;
import java.util.Map;
import rpg.Rpg;

/** A deployed physical snare; its tool, captured actor and provenance survive save/load. */
public final class Trap extends Container {
    public long caught;
    public double checkedAt;
    public Trap(long id,long owner,float x,float y,float z) { super(id,"snare","Rabbit snare","trap",x,y,z,40,owner); }
    @Override public Map<String,Object> save() { var m=super.save(); m.put("caught",caught);m.put("checkedAt",checkedAt);return m; }
    @Override public void load(Rpg g,Map<String,Object> m) { super.load(g,m);caught=Json.lng(m,"caught",0);checkedAt=Json.num(m,"checkedAt",0); }
}
