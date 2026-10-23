package rpg.item;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Items in one place: a definition, how many, and their legal state - who owns them (an entity id, 0 = whoever holds
 * them) and whether they were stolen (GDD 3.5, 3.8: ownership and provenance travel with the item). Clean stacks of the
 * same definition merge only when their owner agrees; stolen stacks of one definition merge whoever they were taken
 * from (gamefixes), and the stack keeps a ledger of its victims so a confiscation still returns each unit to the one it
 * was taken from. Stolen gold is the exception: coins carry no mark, so taken coins are simply coins. A container item
 * (a bag) carries its own grid, and the grid goes wherever the bag goes - dropped with it, picked up with it - so its
 * contents are never left behind or doubled.
 */
public final class ItemStack {
    public final ItemDef def;
    public int amount;
    /** The owner; for a stolen stack, the first of its victims (see {@link #victims()}). */
    public long owner;
    public boolean stolen;
    /** Where the stack first came from (an entity id), kept across transfers. */
    public long origin;
    /** A bag's own grid (null for anything that is not a container). */
    public Inventory inner;
    /** Charges left (the Fire staff's); 0 for anything without them. */
    public int charges;
    /** A signed bounty scroll names a reported case, its subject and the last location known to the watch. */
    public record Contract(long caseId, long target, String jurisdiction, int reward, float x, float z) {}
    public Contract contract;
    /** A stolen stack merged from several victims: who each unit was taken from, oldest first (null: all the owner's). */
    private LinkedHashMap<Long, Integer> ledger;

    public ItemStack(ItemDef def, int amount) {
        this.def = def; this.amount = amount;
        if (def.bagW > 0) { inner = new Inventory(def.bagW, def.bagH); inner.container = true; }
        charges = def.maxCharges;
    }
    public ItemStack(ItemDef def, int amount, long owner, boolean stolen, long origin) { this(def, amount); this.owner = owner; this.stolen = stolen; this.origin = origin; }

    /** Whether two stacks may merge: one definition, no bags; clean ones of one owner, stolen ones of anyone. */
    public boolean sameKind(ItemStack o) {
        return o != null && o.def == def && inner == null && o.inner == null && o.stolen == stolen && (stolen || o.owner == owner);
    }

    /** Takes another stack of the same kind into this one (amount and, when stolen, its victims). */
    public void absorb(ItemStack s) {
        if (stolen) {
            LinkedHashMap<Long, Integer> mine = victims();
            for (Map.Entry<Long, Integer> e : s.victims().entrySet()) mine.merge(e.getKey(), e.getValue(), Integer::sum);
            ledger = mine.size() > 1 ? mine : null;
        }
        amount += s.amount;
    }

    /**
     * Who the units were taken from, oldest first, summing to the amount. Units leave a stack from its front (see
     * {@link #copy}), so when the amount has fallen the oldest entries are the ones that went.
     */
    public LinkedHashMap<Long, Integer> victims() {
        LinkedHashMap<Long, Integer> v = new LinkedHashMap<>();
        if (ledger == null) { if (amount > 0) v.put(owner, amount); return v; }
        int total = 0;
        for (int n : ledger.values()) total += n;
        int extra = total - amount;
        for (Map.Entry<Long, Integer> e : ledger.entrySet()) {
            int cut = Math.max(0, Math.min(extra, e.getValue()));
            extra -= cut;
            if (e.getValue() - cut > 0) v.put(e.getKey(), e.getValue() - cut);
        }
        if (extra < 0) v.merge(owner, -extra, Integer::sum);
        return v;
    }

    /**
     * Marks the stack as taken from someone without leave. Coins are the exception: gold carries no mark, so stolen gold
     * stacks with honest gold (the theft itself is still a crime for whoever saw it).
     */
    public void markStolen(long from) {
        if (def == Items.COINS) { stolen = false; owner = 0; ledger = null; return; }
        if (!stolen) { stolen = true; ledger = null; if (owner == 0) owner = from; }
    }

    /** Part of the stack, from its front (a bag never splits: its grid is its own). */
    public ItemStack copy(int n) {
        ItemStack c = new ItemStack(def, n, owner, stolen, origin);
        c.charges = charges; c.contract = contract;
        if (stolen && ledger != null) {
            LinkedHashMap<Long, Integer> part = new LinkedHashMap<>();
            int left = n;
            for (Map.Entry<Long, Integer> e : victims().entrySet()) {
                if (left <= 0) break;
                int k = Math.min(left, e.getValue());
                part.put(e.getKey(), k);
                left -= k;
            }
            c.owner = part.isEmpty() ? owner : part.keySet().iterator().next();
            c.ledger = part.size() > 1 ? part : null;
        }
        return c;
    }
    /** The whole stack moved somewhere else: a bag keeps its very grid (the same contents, not a copy of them). */
    public ItemStack copyWhole() { ItemStack s = copy(amount); if (inner != null) s.inner = inner; return s; }
    /** Restores a saved ledger (save data only). */
    public void ledger(LinkedHashMap<Long, Integer> l) { ledger = l != null && l.size() > 1 ? l : null; }
    public int value() { return def.value * amount + (inner == null ? 0 : inner.stacks().stream().mapToInt(ItemStack::value).sum()); }
    /** Its weight (kg), with a bag's contents. */
    public float weight() { return def.weight * amount + (inner == null ? 0 : inner.weight()); }

    @Override public String toString() { return (amount > 1 ? amount + " x " : "") + def.name + (stolen ? " (stolen)" : "") + (inner != null && !inner.isEmpty() ? " (" + inner.stacks().size() + " inside)" : ""); }
}
