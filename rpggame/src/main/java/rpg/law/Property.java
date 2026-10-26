package rpg.law;

import rpg.Rpg;
import rpg.item.Inventory;
import rpg.item.ItemStack;

/** Provenance-aware confiscation shared by prison and death recovery. Originals never disappear. */
final class Property {
    private Property() {}
    static int coins(Inventory grid) {
        int total=grid.count(rpg.item.Items.COINS);
        for(var item:grid.stacks()) if(item.inner!=null)total+=coins(item.inner);
        return total;
    }
    static java.util.List<ItemStack> takeCoins(Inventory grid,int amount) {
        java.util.List<ItemStack> result=new java.util.ArrayList<>(grid.remove(rpg.item.Items.COINS,Math.min(amount,grid.count(rpg.item.Items.COINS))));
        int left=amount;for(var item:result)left-=item.amount;
        for(var item:grid.stacks())if(left>0 && item.inner!=null) {
            var taken=takeCoins(item.inner,left);for(var coin:taken)left-=coin.amount;result.addAll(taken);
        }
        return result;
    }
    static int confiscate(Rpg g, Inventory inventory) {
        int returned = 0;
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack item = inventory.get(i);
            if (item == null) continue;
            if (!item.stolen) { if (item.inner != null) returned += confiscate(g, item.inner); continue; }
            inventory.set(i, null);
            for (var victim : item.victims().entrySet()) {
                ItemStack back = item.copy(victim.getValue()); back.owner = victim.getKey(); back.stolen = false;
                var owner = g.actor(back.owner);
                if (owner == null || !owner.store(back)) {
                    var evidence = g.objects.custody;
                    if (!evidence.contents.add(back)) g.objects.drop(back, evidence.x, evidence.y, evidence.z);
                }
                returned += back.amount;
            }
        }
        return returned;
    }
}
