package rpg.monster;

import java.util.ArrayList;
import java.util.List;
import rpg.Rpg;
import rpg.item.ItemDef;
import rpg.item.ItemStack;
import rpg.item.Items;

/**
 * A monster's drop (gamefixes: each kind's table): everything in "always", then one roll down the tiers - common more
 * often than not, uncommon now and then, rare seldom, very rare almost never - with a monster high in its kind's level
 * range a little likelier to carry the better things. Entries are item ids, with a count range ("coins:2-18").
 */
final class Loot {
    private Loot() { }

    /** Chances of each tier for a monster at the bottom of its range; the top of the range doubles the rarer ones. */
    static final double COMMON = 0.62, UNCOMMON = 0.2, RARE = 0.025, VERY_RARE = 0.004;

    static List<ItemStack> roll(Rpg g, Monster m) {
        List<ItemStack> out = new ArrayList<>();
        MonsterKind k = m.kind;
        for (String e : k.always) add(g, m, out, e, 1);
        float high = k.maxLevel == k.minLevel ? 0 : (m.level - k.minLevel) / (float) (k.maxLevel - k.minLevel);
        double r = g.roll(m.id, 0xD20F);
        double vr = VERY_RARE * (1 + high), ra = RARE * (1 + high), un = UNCOMMON * (1 + high * 0.5);
        String[] tier = r < vr ? k.veryRare : r < vr + ra ? k.rare : r < vr + ra + un ? k.uncommon : r < vr + ra + un + COMMON ? k.common : null;
        if (tier != null && tier.length > 0) add(g, m, out, tier[(int) Math.floorMod((long) (g.roll(m.id, 0xD211) * 1e6), tier.length)], 2);
        return out;
    }

    private static void add(Rpg g, Monster m, List<ItemStack> out, String entry, int salt) {
        String[] p = entry.split(":");
        ItemDef d = Items.find(p[0]);
        if (d == null) return;
        int n = 1;
        if (p.length > 1) {
            String[] r = p[1].split("-");
            int lo = Integer.parseInt(r[0]), hi = r.length > 1 ? Integer.parseInt(r[1]) : lo;
            n = lo + (int) Math.floor(g.roll(m.id, 0xD300 + salt) * (hi - lo + 1));
        }
        if (d.stackable) out.add(new ItemStack(d, n));
        else for (int i = 0; i < n; i++) out.add(new ItemStack(d, 1));
    }
}
