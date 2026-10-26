package rpg.objects;

import astratto.util.Json;
import java.util.Map;
import rpg.Rpg;

/** A deployed physical snare; its tool, captured actor and provenance survive save/load. */
public final class Trap extends Container {
    public long caught, quarry;
    public double checkedAt;
    public Trap(long id,long owner,float x,float y,float z) { super(id,"snare","Rabbit snare","trap",x,y,z,40,owner); }
    @Override public Map<String,Object> save() { var m=super.save(); m.put("caught",caught);m.put("quarry",quarry);m.put("checkedAt",checkedAt);return m; }
    @Override public void load(Rpg g,Map<String,Object> m) { super.load(g,m);caught=Json.lng(m,"caught",0);quarry=Json.lng(m,"quarry",0);checkedAt=Json.num(m,"checkedAt",0); }
    @Override public String examine(Rpg g) { return !contents.has(rpg.item.Items.SNARE,1)?"An empty, disarmed snare.":caught==0?"A set snare. Only its quarry physically crossing it can be caught.":"A snare holding "+g.nameOf(caught)+"."; }
}
