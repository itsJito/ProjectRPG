package rpg.item;

import java.util.ArrayList;
import java.util.List;
import rpg.skill.Skill;

/**
 * Production recipes (content data): where they are made, the skill and level they ask, what they consume and make, the
 * XP they give and how long they take. Inputs are consumed only at the commit point at the end of the work (GDD 3.2).
 * XP values are test values, scaled at run time by the devmap's XP rate.
 */
public final class Recipes {
    private Recipes() {}

    public enum Station { FURNACE, ANVIL, RANGE, CRAFT_TABLE, SAWBENCH, BUILD_SPOT }

    public record Part(ItemDef item, int amount) {}

    public static final class Recipe {
        public final String id, name;
        public final Station station;
        public final Skill skill;
        public final int level;
        public final List<Part> inputs = new ArrayList<>(), outputs = new ArrayList<>();
        public final int xp;
        public final float seconds;
        public ItemDef tool;
        /** Cooking: the level at which it stops burning, and what a burnt one is. */
        public int burnStop;
        public ItemDef burnt;
        /** Construction: which furniture a build makes. */
        public String furniture;

        Recipe(String id, String name, Station station, Skill skill, int level, int xp, float seconds) {
            this.id = id; this.name = name; this.station = station; this.skill = skill; this.level = level; this.xp = xp; this.seconds = seconds;
        }
        Recipe in(ItemDef d, int n) { inputs.add(new Part(d, n)); return this; }
        Recipe out(ItemDef d, int n) { outputs.add(new Part(d, n)); return this; }
        Recipe tool(ItemDef d) { tool = d; return this; }
        Recipe burn(int stop) { burnStop = stop; burnt = Items.BURNT_FISH; return this; }
        Recipe burnt(ItemDef d) { burnt = d; return this; }
        Recipe furniture(String f) { furniture = f; return this; }
    }

    public static final List<Recipe> ALL = new ArrayList<>();

    private static Recipe r(String id, String name, Station st, Skill sk, int level, int xp, float seconds) {
        Recipe r = new Recipe(id, name, st, sk, level, xp, seconds);
        ALL.add(r);
        return r;
    }

    static {
        // smelting
        r("smelt_bronze", "Bronze bar", Station.FURNACE, Skill.SMITHING, 1, 62, 2.4f).in(Items.COPPER_ORE, 1).in(Items.TIN_ORE, 1).out(Items.BRONZE_BAR, 1);
        r("smelt_iron", "Iron bar", Station.FURNACE, Skill.SMITHING, 15, 125, 2.4f).in(Items.IRON_ORE, 1).out(Items.IRON_BAR, 1);
        r("smelt_steel", "Steel bar", Station.FURNACE, Skill.SMITHING, 30, 175, 2.4f).in(Items.IRON_ORE, 1).in(Items.COAL, 2).out(Items.STEEL_BAR, 1);
        // smithing: bronze, iron and steel gear at the anvil
        Object[][] metals = {{Tier.BRONZE, Items.BRONZE_BAR, 1}, {Tier.IRON, Items.IRON_BAR, 15}, {Tier.STEEL, Items.STEEL_BAR, 30}};
        Object[][] pieces = {{"dagger", 1, 0}, {"axe", 1, 1}, {"pickaxe", 2, 2}, {"mace", 1, 2}, {"sword", 2, 4}, {"helm", 1, 3}, {"shield", 2, 6}, {"legs", 3, 8}, {"body", 5, 9}};
        for (Object[] m : metals) {
            Tier t = (Tier) m[0];
            for (Object[] p : pieces) {
                ItemDef out = Items.tierItem(t, (String) p[0]);
                int bars = (int) p[1], lvl = (int) m[2] + (int) p[2];
                r("smith_" + out.id, out.name, Station.ANVIL, Skill.SMITHING, lvl, 125 * bars * (1 + t.power / 3), 3f).in((ItemDef) m[1], bars).out(out, 1).tool(Items.HAMMER);
            }
        }
        // cooking (a range, or any fire)
        r("cook_shrimp", "Shrimp", Station.RANGE, Skill.COOKING, 1, 300, 1.8f).in(Items.RAW_SHRIMP, 1).out(Items.SHRIMP, 1).burn(20);
        r("cook_meat", "Cooked meat", Station.RANGE, Skill.COOKING, 1, 300, 2f).in(Items.RAW_MEAT, 1).out(Items.COOKED_MEAT, 1).burn(25).burnt(Items.BURNT_MEAT);
        r("cook_trout", "Trout", Station.RANGE, Skill.COOKING, 20, 700, 1.8f).in(Items.RAW_TROUT, 1).out(Items.TROUT, 1).burn(45);
        // crafting
        r("weave_linen", "Linen cloth", Station.CRAFT_TABLE, Skill.CRAFTING, 1, 30, 1.2f).in(Items.FLAX, 2).out(Items.CLOTH, 1);
        r("sew_hood", "Linen hood", Station.CRAFT_TABLE, Skill.CRAFTING, 1, 35, 1.2f).in(Items.CLOTH, 1).out(Items.CLOTH_HOOD, 1);
        r("sew_tunic", "Linen tunic", Station.CRAFT_TABLE, Skill.CRAFTING, 5, 60, 1.5f).in(Items.CLOTH, 2).out(Items.CLOTH_TUNIC, 1);
        r("craft_snare", "Rabbit snare", Station.CRAFT_TABLE, Skill.CRAFTING, 1, 30, 1.2f).in(Items.LOGS, 1).in(Items.BOWSTRING, 1).out(Items.SNARE, 1);
        r("craft_shafts", "Arrow shafts (15)", Station.CRAFT_TABLE, Skill.CRAFTING, 1, 50, 1.6f).in(Items.LOGS, 1).out(Items.ARROW_SHAFT, 15).tool(Items.KNIFE);
        r("craft_bowstring", "Bowstring", Station.CRAFT_TABLE, Skill.CRAFTING, 1, 150, 1.6f).in(Items.FLAX, 1).out(Items.BOWSTRING, 1);
        r("craft_bronze_arrows", "Bronze arrows (15)", Station.CRAFT_TABLE, Skill.CRAFTING, 1, 195, 2f).in(Items.ARROW_SHAFT, 15).in(Items.BRONZE_BAR, 1).out(Items.BRONZE_ARROWS, 15);
        r("craft_iron_arrows", "Iron arrows (15)", Station.CRAFT_TABLE, Skill.CRAFTING, 15, 375, 2f).in(Items.ARROW_SHAFT, 15).in(Items.IRON_BAR, 1).out(Items.IRON_ARROWS, 15);
        r("craft_shortbow", "Shortbow", Station.CRAFT_TABLE, Skill.CRAFTING, 5, 250, 2.2f).in(Items.LOGS, 1).in(Items.BOWSTRING, 1).out(Items.SHORTBOW, 1).tool(Items.KNIFE);
        r("craft_oak_bow", "Oak shortbow", Station.CRAFT_TABLE, Skill.CRAFTING, 20, 500, 2.2f).in(Items.OAK_LOGS, 1).in(Items.BOWSTRING, 1).out(Items.OAK_BOW, 1).tool(Items.KNIFE);
        r("craft_willow_bow", "Willow shortbow", Station.CRAFT_TABLE, Skill.CRAFTING, 35, 830, 2.2f).in(Items.WILLOW_LOGS, 1).in(Items.BOWSTRING, 1).out(Items.WILLOW_BOW, 1).tool(Items.KNIFE);
        r("craft_wand", "Oak wand", Station.CRAFT_TABLE, Skill.CRAFTING, 10, 300, 2.2f).in(Items.OAK_LOGS, 1).out(Items.WAND, 1).tool(Items.KNIFE);
        // jewellery (GDD 2.2 Crafting V: a mould completes an existing recipe; the mould is kept)
        Object[][] gems = {{"sapphire", 20}, {"emerald", 27}, {"ruby", 34}, {"diamond", 43}};
        for (Object[] gm : gems) {
            String gid = (String) gm[0]; int lv = (int) gm[1];
            r("cut_" + gid, Items.get(gid).name, Station.CRAFT_TABLE, Skill.CRAFTING, lv, 50 + lv * 3, 2f).in(Items.get("uncut_" + gid), 1).out(Items.get(gid), 1).tool(Items.get("chisel"));
        }
        Object[][] kinds = {{"ring", 5, 15}, {"necklace", 8, 20}, {"amulet", 12, 30}};
        for (Object[] k : kinds) {
            String kind = (String) k[0]; int lv = (int) k[1], xp = (int) k[2];
            r("mould_gold_" + kind, Items.get("gold_" + kind).name, Station.CRAFT_TABLE, Skill.CRAFTING, lv, xp * 10, 2.4f).in(Items.get("gold_bar"), 1).out(Items.get("gold_" + kind), 1).tool(Items.get(kind + "_mould"));
            for (Object[] gm : gems) {
                String gid = (String) gm[0]; int glv = (int) gm[1];
                ItemDef out = Items.get(gid + "_" + kind);
                r("mould_" + out.id, out.name, Station.CRAFT_TABLE, Skill.CRAFTING, glv + lv, (xp + glv * 2) * 10, 2.6f).in(Items.get("gold_bar"), 1).in(Items.get(gid), 1).out(out, 1).tool(Items.get(kind + "_mould"));
            }
        }
        // sawing planks (Construction's raw material)
        r("saw_plank", "Plank", Station.SAWBENCH, Skill.CONSTRUCTION, 1, 40, 1.5f).in(Items.LOGS, 1).out(Items.PLANK, 1);
        r("saw_oak_plank", "Oak plank", Station.SAWBENCH, Skill.CONSTRUCTION, 15, 80, 1.5f).in(Items.OAK_LOGS, 1).out(Items.OAK_PLANK, 1);
        // building at the build spot: furniture objects placed into the world, the builder's own
        r("build_bed", "Bed", Station.BUILD_SPOT, Skill.CONSTRUCTION, 15, 1800, 4f).in(Items.PLANK, 4).in(Items.FLAX, 2).tool(Items.HAMMER).furniture("bed");
        r("build_crate", "Crate", Station.BUILD_SPOT, Skill.CONSTRUCTION, 1, 600, 3f).in(Items.PLANK, 2).tool(Items.HAMMER).furniture("crate");
        r("build_table", "Table", Station.BUILD_SPOT, Skill.CONSTRUCTION, 10, 1200, 3.5f).in(Items.PLANK, 4).tool(Items.HAMMER).furniture("table");
        r("build_bookcase", "Bookcase", Station.BUILD_SPOT, Skill.CONSTRUCTION, 25, 2400, 4f).in(Items.OAK_PLANK, 4).tool(Items.HAMMER).furniture("bookcase");
    }

    public static List<Recipe> at(Station s) {
        List<Recipe> l = new ArrayList<>();
        for (Recipe r : ALL) if (r.station == s) l.add(r);
        return l;
    }

    public static Recipe byId(String id) { for (Recipe r : ALL) if (r.id.equals(id)) return r; return null; }
}
