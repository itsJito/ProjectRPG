package rpg.fishing;

import rpg.item.ItemDef;
import rpg.item.Items;

/**
 * (0.3.5, gamefixes: fishing involves real fish) every kind of fish and shellfish that lives in the waters: where it
 * lives, at what depth, how it is caught and at what Fishing level, how big and quick it is, whether it shoals or hunts,
 * and the skills it fights with (the same skill system as every creature: its combat level is read from them). A catch
 * is always one real fish out of the water's own stock (Fishery); the levels are the gamefixes schedule.
 *
 * <p>Waters are a mask of the kinds of water a fish lives in; the band is the depth it keeps (surface, mid-water, the
 * bed); the method is the gear that takes it (the barehanded tuna is the one fish caught with the fists).
 */
public enum FishKind {
    //               name             level waters                band     method          len   cruise burst school pred  att str def hp   colours: back      belly     fin/accent
    SHRIMP          ("Shrimp",            1, W.POND | W.LAKE | W.SHORE, D.BED,  Method.NET,     0.07f, 0.25f, 1.2f, true,  0,     1,  1,  1,  1, 0xC89A90, 0xE8C8B8, 0xB07060, Shape.SHRIMP),
    SARDINE         ("Sardine",           2, W.SHORE | W.SEA,           D.MID,  Method.NET,     0.18f, 0.7f,  2.6f, true,  0,     1,  1,  1,  2, 0x4A6A8A, 0xD8E0E8, 0x7A9AB8, Shape.FISH),
    GUPPY           ("Guppy",             5, W.POND | W.LAKE,           D.TOP,  Method.NET,     0.05f, 0.35f, 1.6f, true,  0,     1,  1,  1,  1, 0x8A8A50, 0xD8C890, 0xE86A30, Shape.FISH),
    CRAB            ("Crab",             10, W.POND | W.LAKE | W.SHORE, D.BED,  Method.POT,     0.2f,  0.18f, 0.6f, false, 0,     4,  4,  8,  4, 0xB04A30, 0xD8A070, 0x8A3020, Shape.CRAB),
    HERRING         ("Herring",          10, W.SHORE | W.SEA,           D.MID,  Method.NET,     0.3f,  0.8f,  2.8f, true,  0,     2,  2,  2,  3, 0x3A5A7A, 0xE0E6EC, 0x6A8AA8, Shape.FISH),
    MACKEREL        ("Mackerel",         15, W.SEA,                     D.MID,  Method.ROD,     0.35f, 0.9f,  3.2f, true,  0,     4,  4,  3,  4, 0x2E6A5A, 0xE8ECE8, 0x1A3A30, Shape.STRIPED),
    TROUT           ("Trout",            20, W.RIVER | W.LAKE | W.POND, D.MID,  Method.ROD,     0.45f, 0.6f,  2.6f, false, 0,     6,  5,  4,  6, 0x6A7A5A, 0xE8E0C8, 0xC86A6A, Shape.SPOTTED),
    COD             ("Cod",              25, W.SEA | W.DEEP,            D.BED,  Method.ROD,     0.8f,  0.5f,  2.0f, true,  0.12f, 8,  8,  6,  9, 0x7A7A5A, 0xE8E4D4, 0x5A5A40, Shape.SPOTTED),
    SALMON          ("Salmon",           30, W.RIVER | W.SEA,           D.MID,  Method.ROD,     0.75f, 0.9f,  3.4f, false, 0,    10, 10,  8, 11, 0x5A6A7A, 0xE8D8D0, 0xD88070, Shape.FISH),
    TUNA            ("Tuna",             35, W.SEA | W.DEEP,            D.MID,  Method.HARPOON, 1.6f,  1.4f,  5.5f, true,  0.35f,18, 20, 14, 22, 0x1E2E5A, 0xC8CCD8, 0xE8C040, Shape.TUNA),
    LOBSTER         ("Lobster",          40, W.SHORE | W.SEA,           D.BED,  Method.POT,     0.4f,  0.2f,  1.4f, false, 0,    14, 12, 22, 12, 0x2A3A5A, 0x5A4A6A, 0x6A2A2A, Shape.LOBSTER),
    BASS            ("Bass",             45, W.LAKE | W.POND,           D.MID,  Method.ROD,     0.55f, 0.6f,  2.8f, false, 0.12f,16, 16, 14, 16, 0x4A6A3A, 0xE0E4C0, 0x2A3A20, Shape.STRIPED),
    CATFISH         ("Catfish",          45, W.LAKE | W.POND | W.RIVER, D.BED,  Method.ROD,     0.9f,  0.35f, 1.8f, false, 0.1f, 15, 18, 16, 20, 0x4A4438, 0xC8C0A8, 0x2A2420, Shape.CATFISH),
    SWORDFISH       ("Swordfish",        50, W.DEEP,                    D.TOP,  Method.HARPOON, 2.4f,  1.6f,  7.0f, false, 0.35f,30, 32, 24, 34, 0x2A3A5A, 0xD0D4DC, 0x3A4A6A, Shape.BILLFISH),
    STARFISH        ("Starfish",         60, W.SHORE | W.SEA,           D.BED,  Method.NET,     0.25f, 0.03f, 0.08f,false, 0,     1,  1, 30, 10, 0xE07A3A, 0xF0A060, 0xC85A2A, Shape.STARFISH),
    SQUID           ("Squid",            65, W.SEA | W.DEEP,            D.MID,  Method.ROD,     0.6f,  0.7f,  4.2f, true,  0.15f,30, 26, 26, 30, 0xD8B0B0, 0xF0E0E0, 0xA86A7A, Shape.SQUID),
    HADDOCK         ("Haddock",          70, W.SEA | W.DEEP,            D.BED,  Method.ROD,     0.6f,  0.55f, 2.2f, true,  0,    32, 30, 30, 34, 0x5A5A60, 0xE4E4E0, 0x1A1A1E, Shape.FISH),
    SHARK           ("Shark",            75, W.DEEP | W.SEA,            D.MID,  Method.HARPOON, 3.0f,  1.2f,  5.0f, false, 0.6f, 60, 62, 48, 60, 0x5A646E, 0xE8E8E4, 0x3A424A, Shape.SHARK),
    ELECTRIC_EEL    ("Electric eel",     80, W.LAKE | W.RIVER,          D.BED,  Method.HARPOON, 1.8f,  0.4f,  2.4f, false, 0.2f, 55, 50, 46, 56, 0x3A3A2E, 0xC89A50, 0x2A2A20, Shape.EEL),
    ANGLERFISH      ("Anglerfish",       85, W.DEEP,                    D.BED,  Method.ROD,     0.9f,  0.2f,  2.6f, false, 0.25f,60, 66, 58, 64, 0x3A2E2A, 0x5A4A40, 0xE8F0A0, Shape.ANGLER),
    BLUE_MARLIN     ("Blue marlin",      90, W.DEEP,                    D.TOP,  Method.HARPOON, 3.5f,  1.8f,  8.0f, false, 0.4f, 72, 76, 66, 78, 0x1A3A7A, 0xD8DCE8, 0x3A6AC8, Shape.BILLFISH),
    WHALE           ("Whale",            95, W.DEEP,                    D.TOP,  Method.HARPOON, 9.0f,  0.9f,  2.6f, false, 0,    70, 92, 86, 99, 0x3A4450, 0xB8BCC0, 0x2A323A, Shape.WHALE),
    BLACK_MARLIN    ("Black marlin",    100, W.DEEP,                    D.TOP,  Method.HARPOON, 4.0f,  2.0f,  9.0f, false, 0.45f,88, 92, 80, 95, 0x1A1E2A, 0xC8CCD4, 0x2A2E3A, Shape.BILLFISH);

    /** The kinds of water (a mask). */
    public static final class W {
        private W() {}
        public static final int POND = 1, LAKE = 2, RIVER = 4, SHORE = 8, SEA = 16, DEEP = 32;
    }
    /** The depth a fish keeps: just under the surface, in mid-water, on the bed. */
    public static final int TOP = D.TOP, MID = D.MID, BED = D.BED;
    private static final class D { static final int TOP = 0, MID = 1, BED = 2; }
    /** The gear that takes it: the small net, the rod, the harpoon, a pot (a crab cage or a lobster pot). */
    public enum Method { NET, ROD, HARPOON, POT }
    /** How it is drawn. */
    public enum Shape { FISH, STRIPED, SPOTTED, TUNA, BILLFISH, SHARK, CATFISH, EEL, ANGLER, WHALE, SQUID, LOBSTER, CRAB, SHRIMP, STARFISH }

    /** The barehanded tuna (gamefixes): caught by punching one, at this Fishing level and this Strength. */
    public static final int BAREHANDED_FISHING = 55, BAREHANDED_STRENGTH = 35;

    public final String title;
    public final int level, waters, band;
    public final Method method;
    /** Length (m), cruising and burst speed (m/s); whether it shoals; how big a fish it takes as prey (a share of its own length; 0: it hunts nothing). */
    public final float length, cruise, burst, preys;
    public final boolean school;
    /** Its fighting skills (Attack, Strength, Defense, Health): its combat level comes from them as anyone's does. */
    public final int attack, strength, defense, health;
    public final int back, belly, fin;
    public final Shape shape;

    FishKind(String title, int level, int waters, int band, Method method, float length, float cruise, float burst, boolean school, float preys,
             int attack, int strength, int defense, int health, int back, int belly, int fin, Shape shape) {
        this.title = title; this.level = level; this.waters = waters; this.band = band; this.method = method; this.length = length;
        this.cruise = cruise; this.burst = burst; this.school = school; this.preys = preys;
        this.attack = attack; this.strength = strength; this.defense = defense; this.health = health;
        this.back = back; this.belly = belly; this.fin = fin; this.shape = shape;
    }

    /** The item id stem ("blue_marlin"); the raw catch is "raw_" + stem, the cooked dish the stem itself. */
    public String stem() { return this == ELECTRIC_EEL ? "eel" : name().toLowerCase(); }
    public ItemDef raw() { return Items.get(this == STARFISH ? "starfish" : "raw_" + stem()); }
    /** What cooking it makes (null: nothing - a starfish is not eaten). */
    public ItemDef cooked() { return this == STARFISH ? null : Items.get(stem()); }
    /** Fishing XP for landing one: grows with the level it needs (shrimp 10, crab 30, trout 50, black marlin 210). */
    public float xp() { return this == SHRIMP ? 10 : 10 + 2 * level; }
    /** Its combat level, from its skills, by the one formula every creature's is read with. */
    public int combatLevel() { return rpg.skill.Skills.combatLevel(attack, strength, defense, health, 1, 1, 1); }
    /** Whether it lives in a kind of water. */
    public boolean livesIn(int water) { return (waters & water) != 0; }
    /** The gear method an item is (null: not fishing gear). */
    public static Method methodOf(ItemDef held) {
        if (held == null) return null;
        return switch (held.id) {
            case "small_fishing_net" -> Method.NET;
            case "fishing_rod" -> Method.ROD;
            case "harpoon" -> Method.HARPOON;
            case "crab_cage", "lobster_pot" -> Method.POT;
            default -> null;
        };
    }
    /** Whether this kind can be taken with that gear: its own method (a crab is not caught in a lobster pot's bigger mesh, nor the reverse). */
    public boolean takenBy(ItemDef held) {
        Method m = methodOf(held);
        if (m != method) return false;
        if (m == Method.POT) return this == CRAB ? held.id.equals("crab_cage") : held.id.equals("lobster_pot");
        return true;
    }
    /** The kind an item is the catch or dish of (null: none) - the shrimp, crab and trout's own models stand. */
    public static FishKind ofItem(String id) {
        for (FishKind k : values()) {
            if (k == SHRIMP || k == CRAB || k == TROUT) continue;
            String stem = k.stem();
            if (id.equals("raw_" + stem) || id.equals(stem) || k == STARFISH && id.equals("starfish")) return k;
        }
        return null;
    }
    /** (0.3.6) Its half-thickness against its length: the capsule it is hit and pushed as. */
    public float girth() {
        return switch (shape) { case WHALE, BILLFISH -> 0.12f; case EEL -> 0.07f; case SHARK, TUNA -> 0.16f; case CRAB, LOBSTER, STARFISH -> 0.3f; default -> 0.18f; };
    }
    /**
     * (0.3.6, gamefixes: whales did not protect themselves) whether it turns on what hurts it rather than only fleeing:
     * the great solitary fish - two metres and more, not shoaling, not crawling the bed - stand and fight a swimmer who
     * strikes them, and dive and run from one they cannot reach. The rest scatter.
     */
    public boolean defends() { return !school && !crawler() && length >= 2.0f; }
    /** What it strikes with: the whale's flukes, a billfish's sword, a shark's jaws, else its tail. */
    public String weapon() { return switch (shape) { case WHALE -> "flukes"; case BILLFISH -> "bill"; case SHARK -> "jaws"; default -> "tail"; }; }
    /** A blow it lands: the most it can do (HP), from its Strength as any creature's is; how hard it throws a body (impulse). */
    public int maxHit() { return 1 + strength / 8; }
    public float throwing() { return Math.min(260, 40 + length * length * 4); }

    /** Whether an aggressive kind attacks swimmers. */
    public boolean attacksSwimmers() { return this == SHARK; }
    /** Whether it shocks what comes near it. */
    public boolean shocks() { return this == ELECTRIC_EEL; }
    /** A shellfish or crustacean walks the bed rather than swimming. */
    public boolean crawler() { return shape == Shape.CRAB || shape == Shape.LOBSTER || shape == Shape.STARFISH || shape == Shape.SHRIMP; }

    /** How long a cooked one restores (HP), for the items: grows with the level. */
    public int heal() { return Math.max(3, Math.min(26, 3 + level / 4)); }
    /** A raw one's worth (G). */
    public int value() { return Math.max(3, Math.round(3 + level * level * 0.035f + level * 0.9f)); }
    /** Its footprint in a grid and weight in kg (raw). */
    public int[] cells() { return length < 0.12f ? new int[]{1, 1} : length < 0.5f ? new int[]{2, 1} : length < 1.0f ? new int[]{3, 1} : length < 2.0f ? new int[]{4, 2} : new int[]{4, 3}; }
    public float weight() { return Math.max(0.05f, Math.min(25f, length * length * length * 9f)); }
}
