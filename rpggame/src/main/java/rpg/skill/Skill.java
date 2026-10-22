package rpg.skill;

/** The eighteen skills (GDD 2.2), in skill-panel order: three columns, read across. */
public enum Skill {
    ATTACK("Attack", Category.COMBAT, "Maximum melee damage on successful physical hits."),
    HEALTH("Health", Category.COMBAT, "Maximum HP and endurance."),
    MINING("Mining", Category.GATHERING, "Ore, minerals and stone extraction."),
    STRENGTH("Strength", Category.COMBAT, "Physical force, impact and stagger; sword-tier eligibility."),
    AGILITY("Agility", Category.UTILITY, "Movement and stamina efficiency; traversal."),
    SMITHING("Smithing", Category.PRODUCTION, "Metal processing and forging."),
    DEFENSE("Defense", Category.COMBAT, "Physical mitigation, guarding; armor-tier eligibility."),
    HARVESTING("Harvesting", Category.GATHERING, "Plants, herbs, crops and natural gathering."),
    FISHING("Fishing", Category.GATHERING, "Aquatic resource gathering."),
    ARCHERY("Archery", Category.COMBAT, "Projectile offense and ranged effectiveness."),
    THIEVING("Thieving", Category.UTILITY, "Theft, lockpicking and illicit access."),
    COOKING("Cooking", Category.PRODUCTION, "Meals, food and preparation consumables."),
    FAITH("Faith", Category.COMBAT, "Spiritual protection, cast blessings, supernatural resistance."),
    CRAFTING("Crafting", Category.PRODUCTION, "Manufactured goods and equipment."),
    FIREMAKING("Firemaking", Category.UTILITY, "Fire, ignition, fuel and camps."),
    MAGIC("Magic", Category.COMBAT, "Arcane offense and manipulation."),
    WOODCUTTING("Woodcutting", Category.GATHERING, "Timber and wood extraction."),
    CONSTRUCTION("Construction", Category.PRODUCTION, "Housing, furnishing and building work.");

    public enum Category { COMBAT, GATHERING, PRODUCTION, UTILITY }

    public final String title, description;
    public final Category category;

    Skill(String title, Category category, String description) { this.title = title; this.category = category; this.description = description; }

    public static final Skill[] ALL = values();
    public static final int COUNT = ALL.length;

    public static Skill byName(String s) {
        for (Skill k : ALL) if (k.name().equalsIgnoreCase(s) || k.title.equalsIgnoreCase(s)) return k;
        return null;
    }
}
