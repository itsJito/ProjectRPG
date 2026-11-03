package rpg.objects;

import astratto.util.Json;
import java.util.Map;
import rpg.Rpg;
import rpg.item.Items;

/**
 * A deployed tool over a specific fishing resource. Catch accrues into this real inventory, capped by net saturation.
 * (0.2.9, gamefixes: a net sat in the DevMap's fishing spot for good) a net soaks for a while and is hauled back in -
 * by its owner, catch and net together - so the water is clear between soaks and through the night; one whose owner is
 * dead or gone washes ashore where it lies.
 */
public final class FishingNet extends Container {
    /** How long a net soaks before it is hauled in, full or not (game minutes). */
    public static final double SOAK = 75;
    public long source;
    public double nextCatch, laidAt;
    public FishingNet(long id,long owner,long source,float x,float y,float z,double nextCatch) {
        super(id,"fishing_net","Lake fishing net","net",x,y,z,40,owner);this.source=source;this.nextCatch=nextCatch;this.laidAt=nextCatch-4;
    }
    /** Whether the net has soaked long enough, or is full, and should come in. */
    public boolean due(Rpg g) {
        if(!(g.objects.get(source) instanceof Node node))return true;
        return contents.count(node.product)>=5 || g.state.minutes-laidAt>=SOAK;
    }
    public void update(Rpg g) {
        if(removed)return;
        var keeper=g.actor(owner);
        if(keeper==null || !keeper.alive) {
            // nobody left to haul it: it drifts to the edge and its catch and net lie on the bank
            for(var item:contents.stacks())g.objects.drop(item,x,y+0.3f,z);
            contents.clear();removed=true;return;
        }
        if(!contents.has(Items.FISHING_NET,1))return;
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
    @Override public Map<String,Object> save(){var m=super.save();m.put("source",source);m.put("nextCatch",nextCatch);m.put("laidAt",laidAt);return m;}
    @Override public void load(Rpg g,Map<String,Object> m){super.load(g,m);source=Json.lng(m,"source",0);nextCatch=Json.num(m,"nextCatch",g.state.minutes+4);laidAt=Json.num(m,"laidAt",g.state.minutes-SOAK);}
}
