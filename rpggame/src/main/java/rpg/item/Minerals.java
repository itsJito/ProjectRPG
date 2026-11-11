package rpg.item;

import java.util.ArrayList;
import java.util.List;
import rpg.world.Blocks;

/**
 * The mining and smithing ladder (0.3.7, gamefixes: the Mining skill overhaul), as data. One table says what is dug at
 * which Mining level and what it is smelted into; the rocks in the world, the ores and bars in the catalogue, the
 * furnace's and the anvil's recipes and the skill guide are all made from it - so a rung is added, moved or renamed
 * here and nowhere else.
 *
 * <pre>
 *   Stone 1 - Coal 2 - Copper and Tin 3 (bronze) - Wrought iron 10 (chainmail) - Iron 20 - Mithril 30 (black, white)
 *   - Steel 40 (iron and coal: smelted, not dug) - Granite 50 - Cobalt 60 - Dragonite 70 - Moltenite 80 - Godstone 90
 *   - Malevolent 100
 * </pre>
 * A rung's level is its tier's one requirement ({@link Tier#requirement}), the same number its gear asks to be worn
 * and its tools to be used; Stone and Coal, which make no tier of their own, are the two rungs below Bronze.
 * The loop is the same all the way up: break the ore out of the rock, smelt it into a bar at a furnace (each better
 * metal asking more of the furnace - more coal, then a second ore), and work the bar at an anvil.
 */
public final class Minerals {
    private Minerals() {}

    /** Something dug out of a rock: its Mining level, the rock it stands in, what a rock holds and how hard it is to break. */
    public record Mineral(String id, String title, int level, ItemDef ore, int block, int xp, float toughness, int stock) {
        public String rockId() { return id + "_rock"; }
        public String rockName() { return title + (ore.id.endsWith("_block") ? " face" : " rocks"); }
    }
    /** A metal worked at the anvil: its tier, the bar, and what the furnace takes to make one (item, count pairs). */
    public record Metal(Tier tier, ItemDef bar, Object[] inputs) {
        /** The Smithing level to smelt it and to begin working it: the tier's requirement. */
        public int level() { return tier.requirement; }
    }
    /** One thing made at the anvil: what it is, how many bars go into it, and how far into its metal's band of levels it sits. */
    public record Piece(String kind, int bars, int offset) {}

    /** The two rungs below the first metal. */
    public static final int STONE_LEVEL = 1, COAL_LEVEL = 2;

    public static final List<Mineral> LADDER = new ArrayList<>();
    public static final List<Metal> METALS = new ArrayList<>();
    /** What the anvil makes of every metal (those a tier has no item for are skipped). */
    public static final List<Piece> PIECES = List.of(new Piece("dagger", 1, 0), new Piece("axe", 1, 1), new Piece("pickaxe", 2, 2), new Piece("mace", 1, 2),
            new Piece("helm", 1, 3), new Piece("sword", 2, 4), new Piece("shield", 2, 6), new Piece("legs", 3, 8), new Piece("body", 5, 9));

    private static Mineral dig(String id, String title, int level, ItemDef ore, int block, float toughness, int stock) {
        // the worth of a rock in XP rises with its rung: 17 at the bottom, a little over ten times that at the top
        Mineral m = new Mineral(id, title, level, ore, block, 15 + level * 2, toughness, stock);
        LADDER.add(m);
        return m;
    }
    private static void metal(Tier tier, ItemDef bar, Object... inputs) { METALS.add(new Metal(tier, bar, inputs)); }

    static {
        dig("stone", "Stone", STONE_LEVEL, Items.STONE_BLOCK, Blocks.STONE_ROCK, 2.6f, 2);
        dig("coal", "Coal", COAL_LEVEL, Items.COAL, Blocks.COAL_ROCK, 2.8f, 1);
        dig("copper", "Copper", Tier.BRONZE.requirement, Items.COPPER_ORE, Blocks.COPPER_ROCK, 3f, 1);
        dig("tin", "Tin", Tier.BRONZE.requirement, Items.TIN_ORE, Blocks.TIN_ROCK, 3f, 1);
        dig("wrought_iron", "Wrought iron", Tier.CHAINMAIL.requirement, Items.WROUGHT_IRON_ORE, Blocks.WROUGHT_ROCK, 3.3f, 1);
        dig("iron", "Iron", Tier.IRON.requirement, Items.IRON_ORE, Blocks.IRON_ROCK, 3.6f, 1);
        dig("mithril", "Mithril", Tier.BLACK.requirement, Items.MITHRIL_ORE, Blocks.MITHRIL_ROCK, 4f, 1);
        dig("granite", "Granite", Tier.GRANITE.requirement, Items.GRANITE_BLOCK, Blocks.GRANITE, 4.6f, 2);
        dig("cobalt", "Cobalt", Tier.COBALT.requirement, Items.COBALT_ORE, Blocks.COBALT_ROCK, 5f, 1);
        dig("dragonite", "Dragonite", Tier.DRAGON.requirement, Items.DRAGONITE_ORE, Blocks.DRAGONITE_ROCK, 5.4f, 1);
        dig("moltenite", "Moltenite", Tier.MOLTEN.requirement, Items.MOLTENITE_ORE, Blocks.MOLTENITE_ROCK, 5.8f, 1);
        dig("godstone", "Godstone", Tier.GODLY.requirement, Items.GODSTONE_ORE, Blocks.GODSTONE_ROCK, 6.2f, 1);
        dig("malevolent", "Malevolent", Tier.MALEVOLENT.requirement, Items.MALEVOLENT_ORE, Blocks.MALEVOLENT_ROCK, 6.8f, 1);

        // the furnace: one ore and one ore for the first alloy; then each better metal asks more of it
        metal(Tier.BRONZE, Items.BRONZE_BAR, Items.COPPER_ORE, 1, Items.TIN_ORE, 1);
        metal(Tier.CHAINMAIL, Items.WROUGHT_IRON_BAR, Items.WROUGHT_IRON_ORE, 1);
        metal(Tier.IRON, Items.IRON_BAR, Items.IRON_ORE, 1, Items.COAL, 1);
        metal(Tier.BLACK, Items.BLACK_BAR, Items.MITHRIL_ORE, 1, Items.COAL, 2);
        metal(Tier.WHITE, Items.WHITE_BAR, Items.MITHRIL_ORE, 1, Items.TIN_ORE, 2);
        metal(Tier.STEEL, Items.STEEL_BAR, Items.IRON_ORE, 1, Items.COAL, 2);
        metal(Tier.GRANITE, Items.GRANITE_BAR, Items.GRANITE_BLOCK, 1, Items.IRON_ORE, 1, Items.COAL, 2);
        metal(Tier.COBALT, Items.COBALT_BAR, Items.COBALT_ORE, 1, Items.COAL, 3);
        metal(Tier.DRAGON, Items.DRAGON_BAR, Items.DRAGONITE_ORE, 1, Items.COAL, 4);
        metal(Tier.MOLTEN, Items.MOLTEN_BAR, Items.MOLTENITE_ORE, 2, Items.COAL, 4);
        metal(Tier.GODLY, Items.GODLY_BAR, Items.GODSTONE_ORE, 2, Items.COAL, 6);
        metal(Tier.MALEVOLENT, Items.MALEVOLENT_BAR, Items.MALEVOLENT_ORE, 2, Items.GODSTONE_ORE, 1, Items.COAL, 8);
    }

    public static Mineral byId(String id) { for (Mineral m : LADDER) if (m.id.equals(id)) return m; return null; }
    /** The mineral a rock block holds, or null (a block that is no ore rock). */
    public static Mineral ofBlock(int block) { for (Mineral m : LADDER) if (m.block == block) return m; return null; }
    public static Mineral ofOre(ItemDef ore) { for (Mineral m : LADDER) if (m.ore == ore) return m; return null; }
    public static Metal metal(Tier t) { for (Metal m : METALS) if (m.tier == t) return m; return null; }
}
