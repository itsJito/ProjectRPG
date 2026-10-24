package rpg.ui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import rpg.combat.Spells;
import rpg.item.ItemDef;
import rpg.item.Items;
import rpg.item.Recipes;
import rpg.skill.Skill;

/** A skill's unlocks, from the content data itself (so the guide can never disagree with the rules): "level text". */
public final class SkillGuide {
    private SkillGuide() {}

    private static final java.util.Map<Skill, List<String>> CACHE = new java.util.EnumMap<>(Skill.class);

    public static List<String> unlocks(Skill s) { return CACHE.computeIfAbsent(s, SkillGuide::build); }

    private static List<String> build(Skill s) {
        List<Object[]> l = new ArrayList<>();
        for (ItemDef d : Items.all()) if (d.reqSkill == s && d.slot != null && d.reqLevel > 1 && d.family != ItemDef.Family.ARMOR || d.reqSkill == s && d.slot == ItemDef.Slot.BODY)
            l.add(new Object[]{d.reqLevel, (d.slot == ItemDef.Slot.MAIN || d.slot == ItemDef.Slot.OFF ? "Wield " : "Wear ") + d.name});
        for (Recipes.Recipe r : Recipes.ALL) if (r.skill == s) l.add(new Object[]{r.level, r.station.name().toLowerCase().replace('_', ' ') + ": " + r.name});
        if (s == Skill.MAGIC) { for (Spells.Spell sp : Spells.Spell.values()) l.add(new Object[]{sp.level, "Cast " + sp.title + " (" + sp.book.title + ")"}); l.add(new Object[]{1, "Your Mana is your Magic level"}); }
        if (s == Skill.FAITH) { for (Spells.Prayer b : Spells.Prayer.values()) l.add(new Object[]{b.level, "Pray " + b.title}); l.add(new Object[]{1, "Bury bones; your Faith points are your Faith level"}); }
        switch (s) {
            case WOODCUTTING -> { l.add(new Object[]{1, "Chop trees (logs)"}); l.add(new Object[]{15, "Chop oak trees"}); l.add(new Object[]{30, "Chop willow trees"}); }
            case MINING -> { l.add(new Object[]{1, "Mine copper and tin"}); l.add(new Object[]{15, "Mine iron"}); l.add(new Object[]{30, "Mine coal"}); }
            case FISHING -> { l.add(new Object[]{1, "Fish shrimp"}); l.add(new Object[]{20, "Fish trout"}); }
            case HARVESTING -> { l.add(new Object[]{1, "Pick guam leaves"}); l.add(new Object[]{5, "Pick flax"}); l.add(new Object[]{10, "Pick redberries"}); }
            case FIREMAKING -> { l.add(new Object[]{1, "Light logs"}); l.add(new Object[]{15, "Light oak logs"}); l.add(new Object[]{30, "Light willow logs"}); }
            case AGILITY -> { l.add(new Object[]{1, "Sprint stamina efficiency"}); l.add(new Object[]{5, "Catch reachable two-block ledges"}); l.add(new Object[]{10, "Town-wall shortcut"}); }
            case THIEVING -> { l.add(new Object[]{1, "Pickpocket townsfolk"}); l.add(new Object[]{5, "Steal from the market stall"}); l.add(new Object[]{10, "Pick the store-room lock"}); l.add(new Object[]{20, "Pick the store chest's lock"}); }
            case HEALTH -> l.add(new Object[]{1, "Maximum Health equals your Health level"});
            case ATTACK -> l.add(new Object[]{1, "Faster melee handling and recovery; no damage bonus"});
            case STRENGTH -> l.add(new Object[]{1, "Higher maximum melee hit and impact"});
            case DEFENSE -> l.add(new Object[]{1, "Protection: Defense and armor combined"});
            case ARCHERY -> l.add(new Object[]{1, "Higher maximum ranged hit"});
            case CONSTRUCTION -> l.add(new Object[]{100, "Construction cape"});
            default -> { }
        }
        l.add(new Object[]{100, s.title + " skillcape"});
        l.sort(Comparator.comparingInt(o -> (int) o[0]));
        List<String> out = new ArrayList<>();
        for (Object[] o : l) out.add(o[0] + " " + o[1]);
        return List.copyOf(out);
    }
}
