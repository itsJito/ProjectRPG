package rpg.ui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import rpg.combat.Spells;
import rpg.item.ItemDef;
import rpg.item.Items;
import rpg.item.Recipes;
import rpg.skill.Skill;

/**
 * A skill's unlocks, read from the rules themselves - the items, the recipes, the spells, the mining ladder, the fish,
 * the crops, the traversal and swimming thresholds - so the guide can never disagree with the game. (0.3.7, gamefixes:
 * the skills tab did not show everything a level unlocks, Agility and Fishing least of all) every gate a level opens
 * is listed, each under the kind of thing it is.
 */
public final class SkillGuide {
    private SkillGuide() {}

    /** What a level opens: the level, a line saying what, and the kind of thing it is (the guide groups and marks by it). */
    public record Unlock(int level, String text, Kind kind) {}
    public enum Kind { GATHER, MAKE, GEAR, ABILITY, BONUS, MILESTONE }

    private static final java.util.Map<Skill, List<Unlock>> CACHE = new java.util.EnumMap<>(Skill.class);

    public static List<Unlock> entries(Skill s) { return CACHE.computeIfAbsent(s, SkillGuide::build); }
    /** The same as "level text" lines (the level-up messages read these). */
    public static List<String> unlocks(Skill s) { return entries(s).stream().map(u -> u.level() + " " + u.text()).toList(); }

    private static List<Unlock> build(Skill s) {
        List<Unlock> l = new ArrayList<>();
        java.util.function.BiConsumer<Integer, String> gather = (lv, t) -> l.add(new Unlock(lv, t, Kind.GATHER));
        java.util.function.BiConsumer<Integer, String> ability = (lv, t) -> l.add(new Unlock(lv, t, Kind.ABILITY));
        java.util.function.BiConsumer<Integer, String> bonus = (lv, t) -> l.add(new Unlock(lv, t, Kind.BONUS));
        // gear wielded or worn by this skill, and what is made with it
        for (ItemDef d : Items.all()) if (d.reqSkill == s && d.slot != null && d.reqLevel > 1 && d.family != ItemDef.Family.ARMOR || d.reqSkill == s && d.slot == ItemDef.Slot.BODY)
            l.add(new Unlock(d.reqLevel, (d.slot == ItemDef.Slot.MAIN || d.slot == ItemDef.Slot.OFF ? "Wield " : "Wear ") + d.name, Kind.GEAR));
        for (Recipes.Recipe r : Recipes.ALL) if (r.skill == s) l.add(new Unlock(r.level, stationWord(r.station) + ": " + r.name, Kind.MAKE));
        switch (s) {
            case MAGIC -> { for (Spells.Spell sp : Spells.Spell.values()) l.add(new Unlock(sp.level, "Cast " + sp.title + " (" + sp.book.title + ")", Kind.ABILITY)); bonus.accept(1, "Your Mana is your Magic level"); }
            case FAITH -> { for (Spells.Prayer b : Spells.Prayer.values()) l.add(new Unlock(b.level, "Pray " + b.title, Kind.ABILITY)); bonus.accept(1, "Bury bones; your Faith points are your Faith level"); }
            case WOODCUTTING -> { gather.accept(1, "Chop trees (logs)"); gather.accept(15, "Chop oak trees"); gather.accept(30, "Chop willow trees"); bonus.accept(1, "Every level fells a tree in fewer blows"); }
            case MINING -> {
                for (rpg.item.Minerals.Mineral m : rpg.item.Minerals.LADDER) gather.accept(m.level(), "Mine " + m.rockName().toLowerCase() + " (" + m.ore().name.toLowerCase() + ")");
                bonus.accept(1, "Every level breaks a rock in fewer strikes");
            }
            case SMITHING -> bonus.accept(1, "Smelt at a furnace, smith at an anvil with a hammer");
            case FISHING -> {
                for (rpg.fishing.FishKind k : rpg.fishing.FishKind.values())
                    gather.accept(k.level, "Catch " + k.title.toLowerCase() + " with " + gearWord(k.method) + " (" + watersWord(k.waters) + ")");
                ability.accept(rpg.fishing.FishKind.BAREHANDED_FISHING, "Catch a tuna with your bare hands (with Strength " + rpg.fishing.FishKind.BAREHANDED_STRENGTH + ")");
                bonus.accept(1, "Any gear can be tried at any spot; the fish decide what takes them");
            }
            case HARVESTING -> {
                gather.accept(rpg.world.Farming.APPLE_LEVEL, "Pick apples from apple trees");
                for (rpg.world.Farming.Crop c : rpg.world.Farming.Crop.values()) gather.accept(c.level, "Harvest " + c.title.toLowerCase());
                gather.accept(1, "Pick guam leaves"); gather.accept(5, "Pick flax"); gather.accept(10, "Pick redberries");
                ability.accept(1, "Shear a sheep (with shears)");
            }
            case FIREMAKING -> { gather.accept(1, "Light logs"); gather.accept(15, "Light oak logs"); gather.accept(30, "Light willow logs"); }
            case AGILITY -> {
                bonus.accept(1, "Stamina +0.5 for every level (100 more at level 100)");
                bonus.accept(1, "Breath under water: " + Math.round(rpg.actor.Swimming.lungs(1)) + " s, +0.3 s every level");
                bonus.accept(1, "Stamina comes back faster, and climbing ladders quicker, with every level");
                ability.accept(rpg.actor.Traversal.VAULT_LEVEL, "Vault fences and low walls (jump at them)");
                ability.accept(rpg.actor.Traversal.CLIMB_LEVEL, "Climb a ledge two blocks high (hold Sprint against it)");
                ability.accept(rpg.actor.Traversal.HANG_LEVEL, "Catch and hang from a high edge; shimmy along it");
                ability.accept(rpg.actor.Traversal.CHARGE_LEVEL, "Shoulder charge: a long sprint bowls people over");
                ability.accept(rpg.actor.Swimming.HAUL_LEVEL, "Haul yourself out of the water onto a high bank (hold Sprint)");
                for (int lv : new int[]{20, 50, 80}) ability.accept(lv, String.format("Climb ledges up to %.2f blocks high", rpg.actor.Traversal.reach(lv)));
                bonus.accept(10, "Crossbows reload faster (up to 30% at level 100)");
            }
            case THIEVING -> {
                ability.accept(1, "Pickpocket townsfolk (each guards their purse to their own level)");
                ability.accept(rpg.objects.Fixtures.Stall.LEVEL, "Steal from the market stall");
                ability.accept(1, "Pick any lock, with a lockpick and patience");
                ability.accept(rpg.interact.Lockpicking.KEEP_ANGLE_LEVEL, "A fresh lockpick goes back in where the last one broke");
                ability.accept(rpg.interact.Lockpicking.FREE_HANDS_LEVEL, "Pick locks without carrying a lockpick");
                bonus.accept(1, "Every level widens a lock's sweet spot and makes picks last longer");
            }
            case HEALTH -> bonus.accept(1, "Maximum Health equals your Health level");
            case ATTACK -> bonus.accept(1, "Faster melee handling and recovery; no damage bonus");
            case STRENGTH -> bonus.accept(1, "Higher maximum melee hit and impact");
            case DEFENSE -> bonus.accept(1, "Protection: Defense and armor combined");
            case ARCHERY -> bonus.accept(1, "Higher maximum ranged hit");
            default -> { }
        }
        l.add(new Unlock(100, s.title + " skillcape", Kind.MILESTONE));
        l.sort(Comparator.comparingInt(Unlock::level).thenComparing(u -> u.kind().ordinal()));
        return List.copyOf(l);
    }

    private static String stationWord(Recipes.Station st) {
        return switch (st) { case FURNACE -> "Furnace"; case ANVIL -> "Anvil"; case RANGE -> "Cook"; case CRAFT_TABLE -> "Craft"; case SAWBENCH -> "Saw";
            default -> { String n = st.name().toLowerCase().replace('_', ' '); yield Character.toUpperCase(n.charAt(0)) + n.substring(1); } };
    }
    private static String gearWord(rpg.fishing.FishKind.Method m) {
        return switch (m) { case NET -> "a net"; case ROD -> "a rod"; case HARPOON -> "a harpoon"; case POT -> "a pot or cage"; };
    }
    private static String watersWord(int w) {
        List<String> l = new ArrayList<>();
        if ((w & rpg.fishing.FishKind.W.POND) != 0) l.add("ponds");
        if ((w & rpg.fishing.FishKind.W.LAKE) != 0) l.add("lakes");
        if ((w & rpg.fishing.FishKind.W.RIVER) != 0) l.add("rivers");
        if ((w & rpg.fishing.FishKind.W.SHORE) != 0) l.add("shore");
        if ((w & rpg.fishing.FishKind.W.SEA) != 0) l.add("sea");
        if ((w & rpg.fishing.FishKind.W.DEEP) != 0) l.add("deep sea");
        return String.join(", ", l);
    }
}
