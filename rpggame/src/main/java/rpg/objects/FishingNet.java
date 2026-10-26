package rpg.objects;

import astratto.util.Json;
import java.util.Map;
import rpg.Rpg;
import rpg.item.Items;

/** A deployed tool over a specific fishing resource. Catch accrues into this real inventory, capped by net saturation. */
public final class FishingNet extends Container {
    public long source;
    public double nextCatch;
    public FishingNet(long id,long owner,long source,float x,float y,float z,double nextCatch) {
        super(id,"fishing_net","Lake fishing net","net",x,y,z,40,owner);this.source=source;this.nextCatch=nextCatch;
    }
    public void update(Rpg g) {
        if(removed || !contents.has(Items.FISHING_NET,1))return;
        if(!(g.objects.get(source) instanceof Node node) || node.kind!=Node.Kind.FISH || node.waters!=Node.Waters.LAKE)return;
        if((g.chemistry.state(id)&astratto.bridge.AstrattoNative.CHEM_BURNT)!=0) {
            int tools=contents.count(Items.FISHING_NET);contents.remove(Items.FISHING_NET,tools);g.state.burn(Items.FISHING_NET,tools);
            for(var item:contents.stacks())g.objects.drop(item,x,y,z);contents.clear();removed=true;return;
        }
        int capacity=5-contents.count(node.product);
        while(capacity-->0 && g.state.minutes>=nextCatch) {
            if(!node.catchInNet(g,this))break;
            nextCatch+=Math.max(2,4+g.timeSystem.weather.rain*3);
        }
        if(contents.count(node.product)>=5)nextCatch=g.state.minutes+4;
    }
    @Override public Map<String,Object> save(){var m=super.save();m.put("source",source);m.put("nextCatch",nextCatch);return m;}
    @Override public void load(Rpg g,Map<String,Object> m){super.load(g,m);source=Json.lng(m,"source",0);nextCatch=Json.num(m,"nextCatch",g.state.minutes+4);}
    @Override public String examine(Rpg g){return "A deployed lake net. It holds at most five fish; its tool and catch can be taken.";}
}
