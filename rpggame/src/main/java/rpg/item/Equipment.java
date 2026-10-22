package rpg.item;

import rpg.skill.Skills;

/**
 * Worn and held items, one per slot (GDD 2.4). Eligibility is checked per item against its own governing skill, never
 * the combat level: Strength 90 wields a Godly sword whatever Defense is, and Defense 50 wears no armor above 50.
 */
public final class Equipment {
    private final ItemStack[] slots = new ItemStack[ItemDef.Slot.values().length];

    public ItemStack get(ItemDef.Slot s) { return slots[s.ordinal()]; }
    public void set(ItemDef.Slot s, ItemStack it) { slots[s.ordinal()] = it; }
    public ItemDef def(ItemDef.Slot s) { ItemStack it = get(s); return it == null ? null : it.def; }

    /** Null when the actor may equip it, else why not. */
    public static String refusal(ItemDef d, Skills skills) {
        if (d == null || !d.equippable()) return "That can't be equipped.";
        if (d.reqSkill != null && skills.level(d.reqSkill) < d.reqLevel)
            return "You need " + d.reqSkill.title + " level " + d.reqLevel + " to equip the " + d.name + ".";
        return null;
    }

    public float armor() { float a = 0; for (ItemStack s : slots) if (s != null) a += s.def.armor; return a; }

    public java.util.List<ItemStack> stacks() { java.util.List<ItemStack> l = new java.util.ArrayList<>(); for (ItemStack s : slots) if (s != null) l.add(s); return l; }

    public void clear() { java.util.Arrays.fill(slots, null); }
}
