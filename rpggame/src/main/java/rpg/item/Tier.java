package rpg.item;

/**
 * The material ladder (GDD 2.4, established): one level requirement per tier, for every tiered item - checked against
 * the item's own governing skill (a Godly sword asks 90 Strength, Godly armour 90 Defense). Black and White are peers on
 * one requirement, separate sets. There are no light/mail/heavy classes: a tier sets baseline power. The Sword-family
 * Strength bonus is the GDD's locked baseline (fractional in the data so each tier above Steel still improves).
 */
public enum Tier {
    LEATHER("Leather", 1, 1, 0x8B5A2B, 0),
    BRONZE("Bronze", 5, 2, 0xB0703A, 0),
    CHAINMAIL("Chainmail", 10, 3, 0x8A8F96, 3),
    IRON("Iron", 20, 4, 0x6E6E72, 6),
    BLACK("Black", 30, 5, 0x2A2A30, 9),
    WHITE("White", 30, 5, 0xE6E6E0, 9),
    STEEL("Steel", 40, 6, 0x9CA4AE, 12),
    GRANITE("Granite", 50, 7, 0x8A847C, 12.5f),
    COBALT("Cobalt", 60, 8, 0x2F55A8, 13),
    DRAGON("Dragon", 70, 9, 0xB02020, 13.5f),
    MOLTEN("Molten", 80, 10, 0xE0602A, 14),
    GODLY("Godly", 90, 11, 0xF0C850, 15),
    MALEVOLENT("Malevolent", 100, 12, 0x4A1A5A, 16);

    public final String title;
    /** The level every item of the tier asks of its governing skill. */
    public final int requirement;
    /** Baseline power the stats are derived from (tuning data). */
    public final int power;
    public final int color;
    /** The Sword-family Strength bonus (GDD 2.4: locked). */
    public final float swordStrength;

    Tier(String title, int requirement, int power, int color, float swordStrength) { this.title = title; this.requirement = requirement; this.power = power; this.color = color; this.swordStrength = swordStrength; }

    /** Tiers that make weapons and shields (Leather and Chainmail are armor only in the devmap's catalogue). */
    public boolean metal() { return this != LEATHER && this != CHAINMAIL; }
}
