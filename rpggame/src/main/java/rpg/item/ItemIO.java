package rpg.item;

import astratto.util.Json;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Item stacks to and from save data: definition id, amount, owner, provenance, and a bag's own grid with it. A grid
 * keeps where each item lies in it (its corner and whether it is turned), so a load puts back the same arrangement.
 */
public final class ItemIO {
    private ItemIO() {}

    public static Map<String, Object> stack(ItemStack s) {
        if (s == null) return null;
        Map<String, Object> m = new HashMap<>();
        m.put("id", s.def.id); m.put("n", s.amount); m.put("owner", s.owner); m.put("stolen", s.stolen); m.put("origin", s.origin);
        if (s.def.maxCharges > 0) m.put("charges", s.charges);
        if (s.contract != null) { var c = s.contract; m.put("contract", java.util.Map.of("case", c.caseId(), "target", c.target(), "jur", c.jurisdiction(), "reward", c.reward(), "x", c.x(), "z", c.z())); }
        if (s.stolen && s.victims().size() > 1) { Map<String, Object> v = new HashMap<>(); for (var e : s.victims().entrySet()) v.put(String.valueOf(e.getKey()), e.getValue()); m.put("victims", v); }
        if (s.inner != null && !s.inner.isEmpty()) m.put("inner", inventory(s.inner));
        return m;
    }

    @SuppressWarnings("unchecked")
    public static ItemStack stack(Object o) {
        if (!(o instanceof Map)) return null;
        Map<String, Object> m = (Map<String, Object>) o;
        ItemDef d = Items.find(Json.str(m, "id", ""));
        if (d == null) return null;
        ItemStack s = new ItemStack(d, Json.integer(m, "n", 1), Json.lng(m, "owner", 0), Json.bool(m, "stolen", false), Json.lng(m, "origin", 0));
        if (s.inner != null) inventory(s.inner, Json.list(m, "inner"));
        if (d.maxCharges > 0) s.charges = Json.integer(m, "charges", d.maxCharges);
        var contract = Json.obj(m, "contract");
        if (contract != null && d == Items.BOUNTY_SCROLL) s.contract = new ItemStack.Contract(Json.lng(contract, "case", 0), Json.lng(contract, "target", 0), Json.str(contract, "jur", ""), Json.integer(contract, "reward", 0), (float) Json.num(contract, "x", 0), (float) Json.num(contract, "z", 0));
        Map<String, Object> v = Json.obj(m, "victims");
        if (v != null && !v.isEmpty()) { java.util.LinkedHashMap<Long, Integer> l = new java.util.LinkedHashMap<>(); for (var e : v.entrySet()) l.put(Long.parseLong(e.getKey()), ((Number) e.getValue()).intValue()); s.ledger(l); }
        return s;
    }

    /** A grid: each item with where it lies. */
    public static List<Object> inventory(Inventory inv) {
        List<Object> l = new ArrayList<>();
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.get(i);
            if (s == null) continue;
            Map<String, Object> m = stack(s);
            m.put("x", inv.x(i)); m.put("y", inv.y(i)); m.put("r", inv.turned(i));
            l.add(m);
        }
        return l;
    }

    /** Rotate an old 10-by-4 carried layout into 4-by-10 without repacking, loss or provenance changes. */
    public static List<Object> transpose(List<Object> saved) {
        List<Object> rotated = new ArrayList<>();
        if (saved == null) return rotated;
        for (Object value : saved) {
            if (!(value instanceof Map<?, ?> entry)) continue;
            Map<String, Object> item = new HashMap<>();
            for (var e : entry.entrySet()) item.put(e.getKey().toString(), e.getValue());
            if (item.containsKey("x")) {
                int x = Json.integer(item, "x", 0);
                item.put("x", Json.integer(item, "y", 0)); item.put("y", x);
                item.put("r", !Json.bool(item, "r", false));
            }
            rotated.add(item);
        }
        return rotated;
    }

    /** Puts a saved grid back: each item where it lay (or wherever it fits, for an old save without places). */
    @SuppressWarnings("unchecked")
    public static void inventory(Inventory inv, List<Object> l) {
        inv.clear();
        if (l == null) return;
        List<ItemStack> unplaced = new ArrayList<>();
        for (Object o : l) {
            ItemStack s = stack(o);
            if (s == null) continue;
            Map<String, Object> m = (Map<String, Object>) o;
            if (m.containsKey("x") && inv.putAt(s, Json.integer(m, "x", 0), Json.integer(m, "y", 0), Json.bool(m, "r", false))) continue;
            unplaced.add(s);
        }
        for (ItemStack s : unplaced) if (!inv.add(s)) System.err.println("[save] no room for " + s + " in a " + inv.w + " x " + inv.h + " grid");
    }

    public static List<Object> equipment(Equipment eq) {
        List<Object> l = new ArrayList<>();
        for (ItemDef.Slot s : ItemDef.Slot.values()) l.add(stack(eq.get(s)));
        return l;
    }

    public static void equipment(Equipment eq, List<Object> l) {
        eq.clear();
        if (l == null) return;
        ItemDef.Slot[] slots = ItemDef.Slot.values();
        for (int i = 0; i < l.size() && i < slots.length; i++) eq.set(slots[i], stack(l.get(i)));
    }
}
