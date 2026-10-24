package rpg.item;

import rpg.skill.Skill;

/** An immutable item definition (content data). Instances are {@link ItemStack}s. */
public final class ItemDef {
    /** Equipment slots (GDD 2.4), with BACK for a backpack and BELT for a pouch: containers worn, each its own grid. */
    public enum Slot { HEAD, BODY, LEGS, FEET, HANDS, MAIN, OFF, AMMO, CAPE, BACK, BELT, RING, NECK }
    public enum Family { NONE, SWORD, AXE, MACE, DAGGER, BOW, STAFF, WAND, SHIELD, SYMBOL, PICKAXE, ARMOR, ARROW, TOOL, BAG,
        LONGSWORD, SCIMITAR, WARHAMMER, SPEAR, CLAWS, HALBERD, CROSSBOW, BOLT, THROWN, JEWEL }
    public enum DamageType { NONE, SLASH, PIERCE, BLUNT, MAGIC }
    /** What kind of thing it is, for the inventory's tabs and filters. */
    public enum Category { WEAPON, ARMOR, TOOL, RESOURCE, CONSUMABLE, CONTAINER, CURRENCY, MISC }

    public final String id, name;
    public boolean stackable;
    /** Jewellery: the colour of its stone (0: none). */
    public int gemColor;
    /** Reference value in Gold (G). */
    public int value = 1;
    public Slot slot;
    public Family family = Family.NONE;
    public Tier tier;
    public boolean conductive;
    public Skill reqSkill;
    public int reqLevel;
    /** Weapon: base damage, impact, reach (m), swing time scale; armor: protection and guard; arrows: damage bonus. */
    public float damage, impact, reach = 1.2f, speed = 1f, armor, guard;
    /** Weapon bonuses (GDD 2.4): Strength's (damage of landed hits; the Sword family's is the tier's locked baseline) and Attack's (cadence). */
    public float strengthBonus, attackBonus;
    public float magicDamageBonusPct;
    /** Faith bonus (GDD 2.5: each point lengthens a prayer's drain interval by 3.33%); Faith restored when drunk; charges a stack is made with. */
    public int faithBonus, restoresFaith, maxCharges;
    public DamageType damageType = DamageType.NONE;
    /** Food: HP restored. */
    public int heal;
    /** Gathering tool power (axes for Woodcutting, pickaxes for Mining). */
    public int toolPower;
    public Skill toolSkill;
    public int color = 0xA0A0A0;
    public String examine = "";
    /** Its footprint in an inventory grid (cells across, cells down, lying straight), and its weight (kg). */
    public int gw = 1, gh = 1;
    public float weight = 0.2f;
    /** A container item's own grid (0: not a container). */
    public int bagW, bagH;

    public ItemDef(String id, String name) { this.id = id; this.name = name; }

    public boolean equippable() { return slot != null; }
    /** A weapon held in both hands (a greatsword, a battleaxe), as bows and staffs always are. */
    public boolean twoHand;
    /** A thrown weapon's kind (dart, knife, tomahawk, javelin); null for anything else. */
    public String thrown;
    public boolean twoHanded() { return twoHand || family == Family.BOW || family == Family.STAFF || family == Family.CROSSBOW; }

    public Category category() {
        if (bagW > 0) return Category.CONTAINER;
        if (id.equals("coins")) return Category.CURRENCY;
        if (heal > 0 || restoresFaith > 0 || id.startsWith("raw_") || id.equals("xp_lamp")) return Category.CONSUMABLE;
        return switch (family) {
            case SWORD, AXE, MACE, DAGGER, BOW, STAFF, WAND, ARROW, LONGSWORD, SCIMITAR, WARHAMMER, SPEAR, CLAWS, HALBERD, CROSSBOW, BOLT, THROWN -> Category.WEAPON;
            case SHIELD, ARMOR, SYMBOL -> Category.ARMOR;
            case PICKAXE, TOOL -> Category.TOOL;
            default -> id.endsWith("_ore") || id.endsWith("_bar") || id.endsWith("logs") || id.endsWith("plank") || id.equals("coal") || id.equals("flax")
                    || id.equals("guam") || id.equals("bowstring") || id.equals("arrow_shaft") || id.contains("bones") ? Category.RESOURCE
                    : id.equals("tinderbox") || id.equals("hammer") || id.equals("knife") || id.equals("lockpick") ? Category.TOOL : Category.MISC;
        };
    }

    @Override public String toString() { return name; }
}
