package rpg.law;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import astratto.util.Json;
import rpg.Ids;
import rpg.Rpg;
import rpg.actor.Actor;
import rpg.ai.Npc;
import rpg.interact.Result;
import rpg.item.ItemStack;
import rpg.item.Items;
import rpg.objects.Container;
import rpg.world.DevMap;

/**
 * Graves and the undertaker (GDD 3.12, with gamefixes' ten-minute grave). A player's death leaves everything they
 * carried and wore in one grave where the body lay - made when they come back, not before (the death screen shows the
 * body). The death site is classified once: inside a town's boundary the grave is the city's to secure (no one may loot
 * it, and a guard comes to carry it to the undertaker); outside, it lies exposed, and anyone who finds it may help
 * themselves. The player may reclaim an exposed grave while it stands. After ten minutes the undertaker travels to collect it;
 * secured town graves wait for the watch. Burial retains the originals in a named gravestone, released for a fee
 * (more for recovery outside the walls). Every item moves exactly once: grave -> memorial -> player.
 */
public final class Undertaker {
    /** How long a grave stands (seconds of play) before what is left goes to the undertaker. */
    public static final double GRAVE_SECONDS = 600;
    /** The fee to redeem a claim, and the surcharge for one recovered from outside the walls. */
    public static final int FEE = 15, OUTSIDE_SURCHARGE = 20;

    /** One recovered death: when, from where, and what it costs to have back. */
    public static final class Claim {
        public final long id;
        public final boolean outside;
        public final String from;
        public int fee;
        public long holder;
        Claim(long id, boolean outside, String from, int fee) { this.id = id; this.outside = outside; this.from = from; this.fee = fee; }
    }

    private final Rpg g;
    public final List<Claim> claims = new ArrayList<>();

    public Undertaker(Rpg g) { this.g = g; }

    /** The player's standing graves (not yet collected), oldest first. */
    public List<Container> graves() {
        List<Container> l = new ArrayList<>();
        for (var o : g.objects.all()) if (o instanceof Container c && !c.removed && c.kind.equals("grave") && !c.buried && c.owner == g.player.id) l.add(c);
        return l;
    }

    /** Seconds a grave has left. */
    public double left(Container grave) { return Math.max(0, grave.expiresAt - g.time); }

    public void update() {
        for (Container c : graves()) {
            if (c.heldBy != 0) {
                // carried by a guard: the bundle is where the guard is
                Actor guard = g.actor(c.heldBy);
                if (guard == null || !guard.alive || guard instanceof Npc worker && worker.goal!=rpg.ai.Brain.Goal.COLLECT) { c.heldBy = 0; continue; }
                c.x = guard.x; c.z = guard.z;
                continue;
            }
            if (c.contents.isEmpty()) { c.removed = true; g.state.commit("GraveEmptied", g.player.id, c.id, DevMap.jurisdiction(c.x, c.z), 0, c.name); continue; }
            // City claims wait safely for the watch. Exposed claims schedule a real undertaker journey.
            if(!c.secured && g.time>=c.expiresAt && g.actor(Ids.UNDERTAKER) instanceof Npc collector && collector.alive)collector.nextThink=0;
        }
    }

    /**
     * What is left in a grave goes into the undertaker's chest under one claim: the fee is set then (and the surcharge
     * if the grave lay outside the walls). The grave is gone.
     */
    public void toCustody(Container grave, String how) {
        if (claims.stream().anyMatch(claim->claim.id==grave.id))return;
        if (grave.buried) {
            Claim claim = new Claim(grave.id, !grave.secured, "the graveyard", FEE + (grave.secured ? 0 : OUTSIDE_SURCHARGE));
            claim.holder = grave.id; claims.add(claim); grave.heldBy = 0;
            g.state.commit("GraveToUndertaker", 0, grave.id, DevMap.jurisdiction(grave.x, grave.z), 0, "originals secured in the gravestone");
            return;
        }
        Container chest = g.objects.custody;
        int moved = 0;
        for (int i = 0; i < grave.contents.size(); i++) {
            ItemStack s = grave.contents.get(i);
            if (s == null) continue;
            grave.contents.set(i, null);
            if (chest.contents.add(s)) moved += s.amount;
            else g.objects.drop(s, chest.x - 0.6f, DevMap.FLOOR + 0.05f, chest.z);   // a full chest: beside it, still in the hut
        }
        grave.removed = true;
        grave.heldBy = 0;
        if (moved > 0) {
            Claim c = new Claim(grave.id, !grave.secured, grave.secured ? "the town" : String.format("outside the walls (%.0f, %.0f)", grave.x, grave.z), FEE + (grave.secured ? 0 : OUTSIDE_SURCHARGE));
            claims.add(c);
            g.chat.add("Your grave has gone to the undertaker (" + how + "). " + (grave.secured ? "The town guarantees everything in it." : "Whatever was still in it is safe now."), rpg.Chat.Kind.WARN);
        }
        g.state.commit("GraveToUndertaker", grave.heldBy, grave.id, DevMap.jurisdiction(grave.x, grave.z), 0, moved + " item(s), " + how);
    }

    public void secureClaim(Container holder) {
        if (claims.stream().anyMatch(claim -> claim.id == holder.id)) return;
        int seized = Property.confiscate(g, holder.contents);
        if (holder.contents.isEmpty()) { holder.removed=true; return; }
        Claim claim = new Claim(holder.id, false, "inside the walls", FEE);
        claim.holder = holder.id; claims.add(claim); holder.ownerOnly = true; holder.locked = true;
        g.state.commit("DeathClaimSecured", g.player.id, holder.id, DevMap.ALPHA, 0, "stolen units confiscated: " + seized);
    }

    /** What redeeming every claim costs now. */
    public int fee() { int f = 0; for (Claim c : claims) f += c.fee; return f; }

    /** The player's goods in the undertaker's keeping. */
    public int held() {
        int n = 0;
        for (Container holder : holders()) for (ItemStack s : holder.contents.stacks()) if(recoverable(holder,s))n++;
        return n;
    }

    /**
     * Redeems every claim: the fee paid from the coins the player carries, or - what the undertaker accepts - from the
     * coins held in their own claim; then everything held is theirs again (what will not fit is set at their feet).
     */
    private List<Container> holders() {
        List<Container> result = new ArrayList<>();
        if(claims.stream().anyMatch(c->c.holder==0))result.add(g.objects.custody);
        for (Claim claim : claims) if (claim.holder != 0 && g.objects.get(claim.holder) instanceof Container c && !result.contains(c)) result.add(c);
        return result;
    }

    private boolean recoverable(Container holder,ItemStack item) {
        // Old saves share the evidence chest. Stolen property is never part of a redeemable claim.
        return item!=null && item.amount>0 && !item.stolen && (holder!=g.objects.custody || item.owner==0 || item.owner==g.player.id);
    }

    /** What the undertaker asks to send for the player's standing graves at once and hand back what is in them. */
    public static final int PROMPT = 10;
    public int buyBackFee() {
        int f = fee();
        for (Container grave : graves()) f += FEE + (grave.secured ? 0 : OUTSIDE_SURCHARGE);
        return graves().isEmpty() ? f : f + PROMPT;
    }

    /**
     * Buys back what lies in the player's gravestones while they still stand (gamefixes): the undertaker sends for
     * them at once - each a claim as if collected, and a small charge for the haste - and hands everything back with
     * whatever he already held. The coins in the graves themselves count toward it, as they do at any redemption.
     */
    public Result buyBack(Actor a, Npc undertaker) {
        List<Container> standing = graves();
        if (standing.isEmpty()) return redeem(a, undertaker);
        int fee = buyBackFee(), purse = a.count(Items.COINS), inGraves = 0;
        for (Container grave : standing) inGraves += grave.contents.count(Items.COINS);
        for (Container holder : holders()) for (ItemStack item : holder.contents.stacks()) if (item.def == Items.COINS && recoverable(holder, item)) inGraves += item.amount;
        if (purse + inGraves < fee) return Result.no("The undertaker wants " + fee + " coins to fetch your things, and you have " + (purse + inGraves) + " all told.");
        for (Container grave : standing) toCustody(grave, "sent for by the undertaker");
        if (!claims.isEmpty()) claims.get(claims.size() - 1).fee += PROMPT;
        return redeem(a, undertaker);
    }

    public Result redeem(Actor a, Npc undertaker) {
        if (claims.isEmpty() || held() == 0) return Result.no("The undertaker holds nothing of yours.");
        List<Container> holders = holders();
        for (Container holder : holders) if (holder != g.objects.custody) Property.confiscate(g, holder.contents);
        int fee = fee();
        int carried = a.count(Items.COINS), inClaim = 0;
        for (Container holder : holders) for(ItemStack item:holder.contents.stacks())if(item.def==Items.COINS && recoverable(holder,item))inClaim+=item.amount;
        if (carried + inClaim < fee) return Result.no("The undertaker wants " + fee + " coins, and you have " + (carried + inClaim) + " all told.");
        if(!undertaker.canStore(new ItemStack(Items.COINS,fee,undertaker.id,false,undertaker.id)))return Result.no("The undertaker has no room to receive payment.");
        long req = g.state.newRequestId();
        int fromPurse = Math.min(fee, carried), fromClaim = fee - fromPurse; boolean paidFromClaim = fromClaim > 0;
        if (fromPurse > 0) for (ItemStack s : a.takeItems(Items.COINS, fromPurse)) undertaker.store(new ItemStack(s.def, s.amount, undertaker.id, false, undertaker.id));
        if (fromClaim > 0) for (Container holder : holders) for(int i=0;i<holder.contents.size() && fromClaim>0;i++) {
            ItemStack item=holder.contents.get(i);if(!recoverable(holder,item) || item.def!=Items.COINS)continue;
            int take=Math.min(fromClaim,item.amount);ItemStack paid=holder.contents.take(i,take);
            undertaker.store(new ItemStack(paid.def,paid.amount,undertaker.id,false,undertaker.id));fromClaim-=take;
        }
        int back = 0;
        for (Container chest : holders) for (int i = 0; i < chest.contents.size(); i++) {
            ItemStack s = chest.contents.get(i);
            if (!recoverable(chest,s)) continue;
            chest.contents.set(i, null);
            if (!a.store(s)) g.objects.drop(s, a.x, a.y + 0.05f, a.z);
            back += s.amount;
        }
        g.state.commit(req, "ClaimRedeemed", a.id, undertaker.id, DevMap.jurisdiction(undertaker.x, undertaker.z), 0, fee + " coins for " + back + " item(s)");
        for (Container holder : holders) if (holder.kind.equals("claim") && holder.contents.isEmpty()) holder.removed = true;
        claims.clear();
        if (a == g.player) g.chat.say(undertaker.name + " hands back your belongings for " + fee + " coins" + (paidFromClaim ? " (taken from your own purse in his keeping)." : "."));
        return Result.ok();
    }

    // ------------------------------------------------------------------ saving
    public Map<String, Object> save() {
        Map<String, Object> m = new HashMap<>();
        List<Object> l = new ArrayList<>();
        for (Claim c : claims) { Map<String, Object> x = new HashMap<>(); x.put("id", c.id); x.put("outside", c.outside); x.put("from", c.from); x.put("fee", c.fee); x.put("holder", c.holder); l.add(x); }
        m.put("claims", l);
        return m;
    }

    public void load(Map<String, Object> m) {
        claims.clear();
        if (m == null) return;
        List<Object> l = Json.list(m, "claims");
        if (l == null) return;
        for (Object o : l) {
            @SuppressWarnings("unchecked") Map<String, Object> x = (Map<String, Object>) o;
            Claim claim = new Claim(Json.lng(x, "id", 0), Json.bool(x, "outside", false), Json.str(x, "from", "?"), (int) Json.lng(x, "fee", FEE)); claim.holder = Json.lng(x, "holder", 0); claims.add(claim);
        }
    }

    /** Whether the world has an undertaker on hand (the roster's). */
    public Npc who() { return g.actor(Ids.UNDERTAKER) instanceof Npc n ? n : null; }
}
