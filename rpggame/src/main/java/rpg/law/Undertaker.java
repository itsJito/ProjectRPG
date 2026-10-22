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
 * themselves. Either way the player may reclaim it in person while it stands. A grave stands ten minutes: then what is
 * left of it goes to the undertaker's chest, as it does when a guard brings it in, and the undertaker releases it for a
 * fee (more for a recovery from outside the walls). Every item moves exactly once: grave -> custody -> player.
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
        Claim(long id, boolean outside, String from, int fee) { this.id = id; this.outside = outside; this.from = from; this.fee = fee; }
    }

    private final Rpg g;
    public final List<Claim> claims = new ArrayList<>();

    public Undertaker(Rpg g) { this.g = g; }

    /** The player's standing graves (not yet collected), oldest first. */
    public List<Container> graves() {
        List<Container> l = new ArrayList<>();
        for (var o : g.objects.all()) if (o instanceof Container c && !c.removed && c.kind.equals("grave") && c.owner == g.player.id) l.add(c);
        return l;
    }

    /** Seconds a grave has left. */
    public double left(Container grave) { return Math.max(0, grave.expiresAt - g.time); }

    public void update() {
        for (Container c : graves()) {
            if (c.heldBy != 0) {
                // carried by a guard: the bundle is where the guard is
                Actor guard = g.actor(c.heldBy);
                if (guard == null || !guard.alive) { c.heldBy = 0; continue; }
                c.x = guard.x; c.z = guard.z;
                continue;
            }
            if (c.contents.isEmpty()) { c.removed = true; g.state.commit("GraveEmptied", g.player.id, c.id, DevMap.jurisdiction(c.x, c.z), 0, c.name); continue; }
            if (g.time >= c.expiresAt) toCustody(c, "its time ran out");
        }
    }

    /**
     * What is left in a grave goes into the undertaker's chest under one claim: the fee is set then (and the surcharge
     * if the grave lay outside the walls). The grave is gone.
     */
    public void toCustody(Container grave, String how) {
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

    /** What redeeming every claim costs now. */
    public int fee() { int f = 0; for (Claim c : claims) f += c.fee; return f; }

    /** The player's goods in the undertaker's keeping. */
    public int held() {
        int n = 0;
        for (ItemStack s : g.objects.custody.contents.stacks()) if (s.owner == 0 || s.owner == g.player.id) n++;
        return n;
    }

    /**
     * Redeems every claim: the fee paid from the coins the player carries, or - what the undertaker accepts - from the
     * coins held in their own claim; then everything held is theirs again (what will not fit is set at their feet).
     */
    public Result redeem(Actor a, Npc undertaker) {
        if (claims.isEmpty() || held() == 0) return Result.no("The undertaker holds nothing of yours.");
        int fee = fee();
        Container chest = g.objects.custody;
        int carried = a.count(Items.COINS), inClaim = chest.contents.count(Items.COINS);
        if (carried + inClaim < fee) return Result.no("The undertaker wants " + fee + " coins, and you have " + (carried + inClaim) + " all told.");
        long req = g.state.newRequestId();
        int fromPurse = Math.min(fee, carried), fromClaim = fee - fromPurse;
        if (fromPurse > 0) for (ItemStack s : a.takeItems(Items.COINS, fromPurse)) undertaker.store(new ItemStack(s.def, s.amount, undertaker.id, false, undertaker.id));
        if (fromClaim > 0) for (ItemStack s : chest.contents.remove(Items.COINS, fromClaim)) undertaker.store(new ItemStack(s.def, s.amount, undertaker.id, false, undertaker.id));
        int back = 0;
        for (int i = 0; i < chest.contents.size(); i++) {
            ItemStack s = chest.contents.get(i);
            if (s == null || !(s.owner == 0 || s.owner == a.id)) continue;
            chest.contents.set(i, null);
            if (!a.store(s)) g.objects.drop(s, a.x, a.y + 0.05f, a.z);
            back += s.amount;
        }
        g.state.commit(req, "ClaimRedeemed", a.id, undertaker.id, DevMap.jurisdiction(undertaker.x, undertaker.z), 0, fee + " coins for " + back + " item(s)");
        claims.clear();
        if (a == g.player) g.chat.say(undertaker.name + " hands back your belongings for " + fee + " coins" + (fromClaim > 0 ? " (taken from your own purse in his keeping)." : "."));
        return Result.ok();
    }

    // ------------------------------------------------------------------ saving
    public Map<String, Object> save() {
        Map<String, Object> m = new HashMap<>();
        List<Object> l = new ArrayList<>();
        for (Claim c : claims) { Map<String, Object> x = new HashMap<>(); x.put("id", c.id); x.put("outside", c.outside); x.put("from", c.from); x.put("fee", c.fee); l.add(x); }
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
            claims.add(new Claim(Json.lng(x, "id", 0), Json.bool(x, "outside", false), Json.str(x, "from", "?"), (int) Json.lng(x, "fee", FEE)));
        }
    }

    /** Whether the world has an undertaker on hand (the roster's). */
    public Npc who() { return g.actor(Ids.UNDERTAKER) instanceof Npc n ? n : null; }
}
