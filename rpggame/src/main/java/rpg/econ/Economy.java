package rpg.econ;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import rpg.Ids;
import rpg.Rpg;
import rpg.actor.Actor;
import rpg.ai.Memory;
import rpg.ai.Npc;
import rpg.interact.Result;
import rpg.item.ItemDef;
import rpg.item.ItemIO;
import rpg.item.ItemStack;
import rpg.item.Inventory;
import rpg.item.Items;
import rpg.objects.Container;
import rpg.world.DevMap;

/**
 * The economy bay (GDD 3.4, 3.8 invariants 1-2): two shops in two jurisdictions with real stock and real cash, prices
 * that follow stock, NPC producers who sell what they actually gathered, and a caravan carrying one shop's surplus to
 * the other. Cargo exists in exactly one place - the shop's stock, the crate, the destination's stock, or whoever took
 * it - and a shipment's status moves forward once: planned, in transit, then delivered or lost; never both.
 */
public final class Economy {
    private final Rpg g;

    public static final class Shop {
        public final long merchant;
        public final String name, jurisdiction;
        public final Inventory stock = new Inventory(20, 20);   // the shop's storeroom
        public final Map<String, Integer> target = new HashMap<>();
        public final float counterX, counterZ;
        Shop(long merchant, String name, String jurisdiction, float cx, float cz) { this.merchant = merchant; this.name = name; this.jurisdiction = jurisdiction; this.counterX = cx; this.counterZ = cz; }
    }

    public enum Status { PLANNED, IN_TRANSIT, DELIVERED, LOST }

    public static final class Shipment {
        public final long id;
        public Status status = Status.PLANNED;
        public final Container crate;
        public double created, updated;
        public String note = "";
        Shipment(long id, Container crate) { this.id = id; this.crate = crate; }
    }

    public Shop shopA, shopB;
    /** The fence's stock (GDD 3.3 Fence): goods bought quietly, sold quietly; what is stolen stays stolen. */
    public Shop fence;
    public final List<Shipment> shipments = new ArrayList<>();
    public long sales, purchases, deliveries, losses;

    public Economy(Rpg g) { this.g = g; }

    public void init() {
        shopA = new Shop(Ids.MERCHANT_A, "Store A", DevMap.ALPHA, DevMap.SHOP_A_COUNTER[0], DevMap.SHOP_A_COUNTER[1] + 2.2f);
        shopB = new Shop(Ids.MERCHANT_B, "Store B", DevMap.BETA, DevMap.SHOP_B_COUNTER[0] + 2.2f, DevMap.SHOP_B_COUNTER[1]);
        fence = new Shop(Ids.FENCE, "Silas's crates", DevMap.BETA, DevMap.FENCE_POST[0], DevMap.FENCE_POST[1] - 1.2f);
        stock(shopA, Items.BREAD, 8, 10); stock(shopA, Items.SHRIMP, 5, 8); stock(shopA, Items.LOGS, 3, 6); stock(shopA, Items.BERRIES, 2, 8);
        stock(shopA, Items.TINDERBOX, 2, 2); stock(shopA, Items.KNIFE, 2, 2); stock(shopA, Items.HAMMER, 2, 2); stock(shopA, Items.FISHING_ROD, 2, 2);
        stock(shopA, Items.ANIMAL_FEED, 16, 16);
        stock(shopA, Items.get("leather_axe"), 2, 2); stock(shopA, Items.get("leather_pickaxe"), 2, 2); stock(shopA, Items.FISHING_NET, 2, 2); stock(shopA, Items.CRAB_CAGE, 1, 1);
        stock(shopA, Items.RELIGIOUS_WINE, 6, 6); stock(shopB, Items.RESTORATIVE, 3, 3);
        stock(shopA, Items.get("bronze_axe"), 2, 2); stock(shopA, Items.get("bronze_pickaxe"), 2, 2); stock(shopA, Items.BRONZE_ARROWS, 200, 200);
        stock(shopB, Items.LOGS, 0, 12); stock(shopB, Items.BREAD, 4, 10); stock(shopB, Items.get("iron_sword"), 1, 1); stock(shopB, Items.get("iron_shield"), 1, 1);
        // jewellery work: gold, moulds, a chisel, rough stones (Store B, the bay's dealer)
        stock(shopB, Items.get("gold_bar"), 3, 4); stock(shopB, Items.get("ring_mould"), 1, 1); stock(shopB, Items.get("necklace_mould"), 1, 1);
        stock(shopB, Items.get("amulet_mould"), 1, 1); stock(shopB, Items.get("chisel"), 2, 2); stock(shopB, Items.get("uncut_sapphire"), 2, 3);
        stock(shopB, Items.get("uncut_emerald"), 1, 2); stock(shopA, Items.get("gold_ring"), 1, 1); stock(shopA, Items.get("gold_necklace"), 1, 1);
        stock(shopB, Items.OAK_BOW, 1, 1);
        // what the fence has on hand at the start: odds and ends of uncertain origin
        stock(fence, Items.LOCKPICK, 2, 2); stock(fence, Items.get("dodgy_necklace"), 1, 1); stock(fence, Items.TOBACCO, 3, 3); stock(fence, Items.ALE, 4, 4); stock(shopB, Items.LOCKPICK, 3, 3); stock(shopB, Items.HOLY_SYMBOL, 1, 1); stock(shopB, Items.STAFF, 1, 1);
    }

    private void stock(Shop s, ItemDef d, int n, int target) {
        s.target.put(d.id, target);
        if (n > 0 && s.stock.add(new ItemStack(d, n, s.merchant, false, s.merchant))) g.state.mint(d, n);
    }

    public Shop shopOf(Npc merchant) { return merchant.id == Ids.MERCHANT_A ? shopA : merchant.id == Ids.MERCHANT_B ? shopB : merchant.id == Ids.FENCE ? fence : null; }
    public boolean isFence(Shop s) { return s == fence; }
    public Npc merchant(Shop s) { return g.actor(s.merchant) instanceof Npc n ? n : null; }
    public float[] counterSpot(Shop s) { return new float[]{s.counterX, s.counterZ}; }

    /** The shop's price for one, from stock against what it wants to hold (fewer in stock: dearer). */
    public int buyPrice(Shop s, ItemDef d) {
        if (s == fence) return Math.max(1, Math.round(d.value * 0.9f));      // a fence sells low: he wants it gone
        int target = s.target.getOrDefault(d.id, 4), have = s.stock.count(d);
        double f = Math.max(0.5, Math.min(2.5, (target + 1.0) / (have + 1.0)));
        return Math.max(1, (int) Math.round(d.value * f));
    }
    public int sellPrice(Shop s, ItemDef d) {
        // a fence pays a third of what a thing is worth (its risk is the rest); a shop sixty percent of its own price
        if (s == fence) return Math.max(1, (int) Math.floor(d.value * 0.35f));
        return Math.max(1, (int) Math.floor(buyPrice(s, d) * 0.6));
    }

    /**
     * What this buyer pays for one (GDD 3.5 shop order: legality and stock first, then the quote, then the Honor discount,
     * then the merchant's floor): the player's Honor discount applies where the merchant knows their good name - they
     * have dealt with them, or heard of them - and never takes the price to or below what the shop would pay for the same
     * thing, so there is no buying low to sell back.
     */
    public int priceFor(Actor a, Shop s, ItemDef d) {
        int p = buyPrice(s, d);
        if (a != g.player || g.honor == null || s == fence) return p;   // (Honor buys nothing in a back alley)
        float disc = g.honor.discount();
        Npc m = merchant(s);
        if (disc <= 0 || m == null || !m.knowsPlayer) return p;
        return Math.max(sellPrice(s, d) + 1, Math.round(p * (1 - disc)));
    }

    /** Null when the merchant will trade with this actor, else why not (wanted here; or known to them as a thief). */
    public String tradeRefusal(Npc merchant, Actor a) {
        Shop s = shopOf(merchant);
        if (s == null) return "They have nothing to trade.";
        if (!merchant.alive) return "They're dead.";
        if (s == fence) {
            // a fence deals with the wanted and the known alike - but never under the eyes of the law, and never with
            // someone who has turned on him
            for (Npc law : g.npcs) if (law.alive && (law.role == Npc.Role.GUARD || law.role == Npc.Role.LAWMAN) && law.distTo(merchant) < 16 && g.ai.canSee(law, merchant))
                return merchant.name + ": \"Not with " + law.name + " watching. Come back when it's quiet.\"";
            for (Memory hurt : merchant.memories) if (hurt.subject == a.id && hurt.victim == merchant.id &&
                    (hurt.kind.equals("ASSAULT") || hurt.kind.equals("MURDER"))) return merchant.name + " wants nothing to do with you.";
            return null;
        }
        if (g.law.bounty(a.id, s.jurisdiction) > 0) return merchant.name + " won't trade with someone wanted in " + s.jurisdiction + ".";
        for (Memory m : merchant.memories) if (m.crime() && m.subject == a.id && m.confidence >= 0.4f && (!m.settled || m.victim == merchant.id))
            return merchant.name + " won't trade with you: \"" + (m.witnessed() ? "I saw what you did." : "I've heard what you did.") + "\"";
        return null;
    }

    public Result buy(Actor a, Shop s, ItemDef d, int n, long req) {
        Npc m = merchant(s);
        String why = m == null ? "There's no one to trade with." : tradeRefusal(m, a);
        if (why == null && m != null) why = m.dist(g, a, g.cfg.interactRange + 1);
        if (why != null) return Result.no(why);
        n = Math.min(n, s.stock.count(d));
        if (n <= 0) return Result.no("The shop is out of " + d.name + ".");
        int cost = 0;
        for (int i = 0; i < n; i++) cost += priceFor(a, s, d);
        if (!a.has(Items.COINS, cost)) return Result.no("You need " + cost + " coins.");
        Inventory probe = a.inv.copy();
        probe.remove(Items.COINS, cost);
        ItemStack goods = new ItemStack(d, n);
        if (!probe.add(goods)) return Result.no("You don't have enough inventory space.");
        a.takeItems(Items.COINS, cost);
        m.store(new ItemStack(Items.COINS, cost));
        s.stock.remove(d, n);
        a.store(goods);
        purchases++;
        m.knowsPlayer |= a == g.player;
        g.state.commit(req, "Trade", a.id, m.id, s.jurisdiction, 0, "bought " + n + " x " + d.name + " for " + cost);
        if (a == g.player) g.sfx.ui("coins", 0.5f);
        return Result.ok();
    }

    public Result sell(Actor a, Shop s, int slot, int n, long req) { return sell(a, a.inv, s, slot, n, req); }

    /** Sells from any grid the actor carries (the base grid or a worn bag's). */
    public Result sell(Actor a, rpg.item.Inventory grid, Shop s, int slot, int n, long req) {
        Npc m = merchant(s);
        String why = m == null ? "There's no one to trade with." : tradeRefusal(m, a);
        if (why == null && m != null) why = m.dist(g, a, g.cfg.interactRange + 1);
        if (why != null) return Result.no(why);
        ItemStack it = grid.get(slot);
        if (it == null) return Result.no("Nothing there.");
        if (it.stolen && s != fence) return Result.no(m.name + ": \"I don't buy stolen goods.\"");
        if (it.def == Items.COINS || it.def.value <= 0) return Result.no("The shop won't buy that.");
        n = Math.min(n, it.amount);
        int pay = 0;
        for (int i = 0; i < n; i++) pay += sellPrice(s, it.def);
        if (!m.has(Items.COINS, pay)) return Result.no(m.name + " can't afford that.");
        ItemStack goods = it.copy(n);
        goods.owner = s.merchant;
        if (!s.stock.canAdd(goods)) return Result.no("The shop has no room for that.");
        grid.take(slot, n);
        s.stock.add(goods);
        m.takeItems(Items.COINS, pay);
        ItemStack coins = new ItemStack(Items.COINS, pay);
        if (!a.store(coins)) g.objects.drop(coins, a.x, a.y + 0.05f, a.z);   // the purse is full: the coins land at their feet
        sales++;
        m.knowsPlayer |= a == g.player;
        g.state.commit(req, "Trade", a.id, m.id, s.jurisdiction, 0, "sold " + n + " x " + it.def.name + " for " + pay);
        if (a == g.player) g.sfx.ui("coins", 0.5f);
        return Result.ok();
    }

    /** An NPC producer sells what it gathered: the goods go into stock, the merchant's coins pay its wages. */
    public int sellToShop(Npc seller, Shop s, ItemDef d, int n) {
        Npc m = merchant(s);
        if (m == null || !m.alive) return 0;
        int sold = 0, pay = 0;
        for (int i = 0; i < n && seller.has(d, 1); i++) {
            int p = sellPrice(s, d);
            if (!m.has(Items.COINS, pay + p)) break;
            ItemStack one = new ItemStack(d, 1, s.merchant, false, seller.id);
            if (!s.stock.canAdd(one)) break;
            seller.takeItems(d, 1);
            s.stock.add(one);
            pay += p; sold++;
        }
        if (sold > 0) {
            m.takeItems(Items.COINS, pay);
            seller.store(new ItemStack(Items.COINS, pay));
            g.state.commit("Trade", seller.id, m.id, s.jurisdiction, 0, seller.name + " sold " + sold + " x " + d.name + " for " + pay + " (stock now " + s.stock.count(d) + ")");
        }
        return sold;
    }

    // ------------------------------------------------------------------ shipments
    public Shipment active() { for (Shipment s : shipments) if (s.status == Status.PLANNED || s.status == Status.IN_TRANSIT) return s; return null; }

    public void update() {
        Npc carter = (Npc) g.actor(Ids.CARTER);
        if (active() == null && carter != null && carter.alive && shopA.stock.count(Items.LOGS) >= 6 && shopB.stock.count(Items.LOGS) < 12) plan(5);
        Shipment s = active();
        if (s != null && s.status == Status.IN_TRANSIT) {
            if (carter == null || !carter.alive) lose(s, "the carter was killed on the road");
            else if (s.crate.contents.isEmpty()) lose(s, "the cargo was taken on the road");
            else { s.crate.x = carter.x - (float) Math.sin(carter.yaw) * -1.2f; s.crate.z = carter.z - (float) Math.cos(carter.yaw) * -1.2f; s.crate.y = DevMap.FLOOR + 0.4f; }
        }
    }

    /** Plans a shipment: the cargo leaves Store A's stock for the crate now, so it is never in both. */
    public Shipment plan(int logs) {
        List<ItemStack> moved = shopA.stock.remove(Items.LOGS, logs);
        if (moved == null) return null;
        Container crate = g.objects.add(new Container(g.state.newEntityId(), "cargo", "Caravan cargo", "cargo", shopA.counterX - 1.5f, DevMap.FLOOR + 0.4f, shopA.counterZ + 1.5f, 10, Ids.MERCHANT_A));
        for (ItemStack it : moved) if (!crate.contents.add(it)) shopA.stock.add(it);   // (what the crate cannot take stays in stock)
        Shipment s = new Shipment(crate.id, crate);
        s.created = s.updated = g.state.minutes;
        shipments.add(s);
        g.state.commit("ShipmentPlanned", Ids.MERCHANT_A, s.id, DevMap.ALPHA, 0, logs + " logs, Store A -> Store B");
        return s;
    }

    /** Where the carter should be going for the shipment in hand (null: nothing to carry). */
    public float[] carterStep(Npc carter) {
        Shipment s = active();
        if (s == null) return null;
        if (s.status == Status.PLANNED) {
            if (Math.hypot(carter.x - s.crate.x, carter.z - s.crate.z) < 1.8) {
                s.status = Status.IN_TRANSIT; s.updated = g.state.minutes;
                g.state.commit("ShipmentDeparted", carter.id, s.id, DevMap.ALPHA, 0, s.crate.contents.stacks().toString());
            }
            return new float[]{s.crate.x + 1.2f, s.crate.z};
        }
        if (Math.hypot(carter.x - DevMap.CARAVAN_B[0], carter.z - DevMap.CARAVAN_B[1]) < 1.6) { deliver(s); return null; }
        return DevMap.CARAVAN_B;
    }

    /** Delivery: in transit -> delivered exactly once; the crate's cargo goes into Store B's stock, Store B pays Store A. */
    public boolean deliver(Shipment s) {
        if (s.status != Status.IN_TRANSIT) return false;
        Npc mb = merchant(shopB), ma = merchant(shopA);
        int value = 0;
        for (ItemStack it : s.crate.contents.stacks()) { ItemStack c = it.copy(it.amount); c.owner = Ids.MERCHANT_B; shopB.stock.add(c); value += it.value(); }
        s.crate.contents.clear();
        s.crate.removed = true;
        if (mb != null && ma != null && mb.has(Items.COINS, value)) { mb.takeItems(Items.COINS, value); ma.store(new ItemStack(Items.COINS, value)); }
        s.status = Status.DELIVERED; s.updated = g.state.minutes; s.note = "delivered for " + value;
        deliveries++;
        g.state.commit("ShipmentDelivered", Ids.CARTER, s.id, DevMap.BETA, 0, "Store B paid " + value + "; Store B logs now " + shopB.stock.count(Items.LOGS));
        return true;
    }

    private void lose(Shipment s, String why) {
        if (s.status != Status.IN_TRANSIT) return;
        s.status = Status.LOST; s.updated = g.state.minutes; s.note = why;
        losses++;
        g.state.commit("ShipmentLost", 0, s.id, DevMap.jurisdiction(s.crate.x, s.crate.z), 0, why + "; what remains stays in the crate");
    }

    // ------------------------------------------------------------------ persistence
    public Map<String, Object> save() {
        Map<String, Object> m = new HashMap<>();
        m.put("a", ItemIO.inventory(shopA.stock));
        m.put("b", ItemIO.inventory(shopB.stock));
        m.put("fence", ItemIO.inventory(fence.stock));
        List<Object> sh = new ArrayList<>();
        for (Shipment s : shipments) {
            Map<String, Object> o = new HashMap<>();
            o.put("id", s.id); o.put("status", s.status.name()); o.put("created", s.created); o.put("updated", s.updated); o.put("note", s.note);
            o.put("x", s.crate.x); o.put("z", s.crate.z); o.put("removed", s.crate.removed); o.put("items", ItemIO.inventory(s.crate.contents));
            sh.add(o);
        }
        m.put("shipments", sh);
        return m;
    }

    @SuppressWarnings("unchecked")
    public void load(Map<String, Object> m) {
        ItemIO.inventory(shopA.stock, astratto.util.Json.list(m, "a"));
        ItemIO.inventory(shopB.stock, astratto.util.Json.list(m, "b"));
        if (astratto.util.Json.list(m, "fence") != null) ItemIO.inventory(fence.stock, astratto.util.Json.list(m, "fence"));
        for (Shipment s : shipments) g.objects.everything().remove(s.crate);
        shipments.clear();
        List<Object> sh = astratto.util.Json.list(m, "shipments");
        if (sh == null) return;
        for (Object o : sh) {
            Map<String, Object> e = (Map<String, Object>) o;
            long id = astratto.util.Json.lng(e, "id", 0);
            Container crate = g.objects.add(new Container(id, "cargo", "Caravan cargo", "cargo", (float) astratto.util.Json.num(e, "x", 0), DevMap.FLOOR + 0.4f, (float) astratto.util.Json.num(e, "z", 0), 10, Ids.MERCHANT_A));
            crate.removed = astratto.util.Json.bool(e, "removed", false);
            ItemIO.inventory(crate.contents, astratto.util.Json.list(e, "items"));
            Shipment s = new Shipment(id, crate);
            s.status = Status.valueOf(astratto.util.Json.str(e, "status", "PLANNED"));
            s.created = astratto.util.Json.num(e, "created", 0); s.updated = astratto.util.Json.num(e, "updated", 0); s.note = astratto.util.Json.str(e, "note", "");
            shipments.add(s);
        }
    }
}
