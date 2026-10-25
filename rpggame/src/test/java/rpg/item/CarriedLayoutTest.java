package rpg.item;

import org.junit.jupiter.api.Test;
import rpg.actor.Actor;
import static org.junit.jupiter.api.Assertions.*;

class CarriedLayoutTest {
    @Test void aFullyPackedLegacyGridRotatesWithoutLosingOrRepackingAnyStack() {
        Inventory old=new Inventory(10,4);
        for(int y=0;y<4;y++)for(int x=0;x<10;x++)assertTrue(old.putAt(new ItemStack(Items.COINS,x+y*10+1,100+x+y*10,true,777),x,y,false));
        Inventory tall=new Actor(1,"Carrier",40).inv;
        ItemIO.inventory(tall,ItemIO.transpose(ItemIO.inventory(old)));
        assertEquals(4,tall.w);assertEquals(10,tall.h);assertEquals(0,tall.freeSlots());
        for(int y=0;y<4;y++)for(int x=0;x<10;x++) {
            ItemStack item=tall.get(tall.at(y,x));assertNotNull(item);assertEquals(x+y*10+1,item.amount);assertEquals(100+x+y*10,item.owner);assertTrue(item.stolen);assertEquals(777,item.origin);
        }
        Inventory loaded=new Inventory(4,10);ItemIO.inventory(loaded,ItemIO.inventory(tall));
        assertEquals(ItemIO.inventory(tall),ItemIO.inventory(loaded));
    }
    @Test void aLongWeaponAndFilledBagRetainTheirShapesAndNestedBelongingsWhenMigrating() {
        Inventory old=new Inventory(10,4);ItemStack sword=new ItemStack(Items.get("bronze_sword"),1);
        assertTrue(old.putAt(sword,4,0,true));
        ItemStack pack=new ItemStack(Items.BACKPACK,1);assertTrue(pack.inner.add(new ItemStack(Items.COINS,27,77,true,909)));
        assertTrue(old.add(pack));
        Inventory tall=new Inventory(4,10);ItemIO.inventory(tall,ItemIO.transpose(ItemIO.inventory(old)));
        assertEquals(old.weight(),tall.weight(),0.001);assertEquals(old.count(sword.def),tall.count(sword.def));
        ItemStack bag=tall.stacks().stream().filter(s->s.inner!=null).findFirst().orElseThrow();
        assertEquals(27,bag.inner.count(Items.COINS));assertTrue(bag.inner.stacks().get(0).stolen);
    }
}
