package rpg.combat;

/**
 * Magic's spells and Faith's prayers (GDD 2.5; the spellbooks as the gamefixes have them).
 *
 * <p><b>Magic</b>: three spellbooks - Sun (the elemental combat spells), Fogish (ice and blood) and Moon (traversal,
 * healing, illusion and influence). One book is active at a time, chosen at an altar; a spell opens at its Magic level.
 * Every committed cast costs Mana; the pool is the Magic level itself and regains one point a minute.
 *
 * <p>Sun's elemental spells come in five tiers - Strike, Bolt, Blast, Wave, Surge - each in five elements, Wind
 * (air), Water, Electric, Earth and Fire, in that order of strength. Within a tier, the lower elements hit as hard as
 * the strongest one the caster has unlocked (once Fire Bolt is open, every Bolt hits up to 12). Electric and Earth share
 * their base from Bolt on; Electric opens first. The maximum hits are bases, before equipment's Magic damage. An
 * elemental staff makes its own element's spells very cheap. Wind from Blast up can knock a target off its feet, more
 * so a small one, and one whose skill is well below the caster's. A creature with a recorded weakness takes double.
 *
 * <p><b>Faith</b>: prayers are toggled on and off, each draining one Faith point per six seconds (longer with an equipped
 * Faith bonus: +3.33% of the interval per point), paid at activation for the first interval and never refunded. The pool
 * is the Faith level and does not refill by itself - an altar, religious wine or a restorative potion does that. The
 * overhead protections negate their attack type entirely, one at a time; the stat prayers multiply their stats.
 */
public final class Spells {
    private Spells() {}

    public enum Book {
        SUN("Sun", "Elemental combat magic: wind, water, electric, earth and fire, from Strike to Surge."),
        FOGISH("Fogish", "Ice and blood: magic that freezes the foe in place, conjures blades and raises the dead."),
        MOON("Moon", "Traversal, healing, illusion and influence.");
        public final String title, about;
        Book(String title, String about) { this.title = title; this.about = about; }
        /** A saved book by its name, the old names included (Standard is Sun, Ancients Fogish, Lunar and Arcane Moon). */
        public static Book of(String name) {
            return switch (name) { case "STANDARD" -> SUN; case "ANCIENTS" -> FOGISH; case "LUNAR", "ARCANE" -> MOON; default -> valueOf(name); };
        }
    }

    /** The elements: Wind (air), Water, Electric, Earth and Fire for Sun, in rising order; Ice and Blood for Fogish. */
    public enum Element { NONE, FIRE, WATER, EARTH, AIR, ICE, BLOOD, ELECTRIC;
        /** Its name as a caster says it (air is "wind"). */
        public String word() { return this == AIR ? "wind" : name().toLowerCase(); }
    }

    /** Sun's five tiers, each with the base maximum its strongest element hits. */
    public enum Tier {
        STRIKE("Strike", 8), BOLT("Bolt", 12), BLAST("Blast", 16), WAVE("Wave", 20), SURGE("Surge", 24);
        public final String title; public final int top;
        Tier(String title, int top) { this.title = title; this.top = top; }
    }

    public enum Spell {
        //  title               book         level cost base speed xp utility element        tier
        WIND_STRIKE("Wind Strike", Book.SUN, 1, 1, 2, 32, 5, false, Element.AIR, Tier.STRIKE, "A blast of air."),
        WATER_STRIKE("Water Strike", Book.SUN, 5, 1, 4, 30, 7, false, Element.WATER, Tier.STRIKE, "A burst of water."),
        ELECTRIC_STRIKE("Electric Strike", Book.SUN, 7, 2, 5, 36, 8, false, Element.ELECTRIC, Tier.STRIKE, "A crackling spark that jolts what it touches."),
        EARTH_STRIKE("Earth Strike", Book.SUN, 9, 2, 6, 28, 9, false, Element.EARTH, Tier.STRIKE, "A clod of hard earth."),
        FIRE_STRIKE("Fire Strike", Book.SUN, 13, 2, 8, 30, 11, false, Element.FIRE, Tier.STRIKE, "A spit of flame."),
        WIND_BOLT("Wind Bolt", Book.SUN, 17, 3, 9, 32, 13, false, Element.AIR, Tier.BOLT, "A driving bolt of air."),
        WATER_BOLT("Water Bolt", Book.SUN, 23, 3, 10, 30, 16, false, Element.WATER, Tier.BOLT, "A bolt of water."),
        ELECTRIC_BOLT("Electric Bolt", Book.SUN, 26, 4, 11, 36, 17, false, Element.ELECTRIC, Tier.BOLT, "A bolt of lightning that jolts what it strikes."),
        EARTH_BOLT("Earth Bolt", Book.SUN, 29, 4, 11, 28, 19, false, Element.EARTH, Tier.BOLT, "A hurled stone of packed earth."),
        FIRE_BOLT("Fire Bolt", Book.SUN, 35, 4, 12, 30, 22, false, Element.FIRE, Tier.BOLT, "A bolt of flame."),
        WIND_BLAST("Wind Blast", Book.SUN, 41, 5, 13, 32, 25, false, Element.AIR, Tier.BLAST, "A blast of wind that can throw a foe off their feet."),
        WATER_BLAST("Water Blast", Book.SUN, 47, 5, 14, 30, 28, false, Element.WATER, Tier.BLAST, "A blast of water."),
        ELECTRIC_BLAST("Electric Blast", Book.SUN, 50, 6, 15, 36, 30, false, Element.ELECTRIC, Tier.BLAST, "A blast of lightning."),
        EARTH_BLAST("Earth Blast", Book.SUN, 53, 6, 15, 28, 31, false, Element.EARTH, Tier.BLAST, "A blast of earth and stone."),
        FIRE_BLAST("Fire Blast", Book.SUN, 59, 6, 16, 30, 34, false, Element.FIRE, Tier.BLAST, "A bursting blast of fire that catches those beside the target."),
        WIND_WAVE("Wind Wave", Book.SUN, 62, 7, 17, 32, 36, false, Element.AIR, Tier.WAVE, "A wave of wind that can throw a foe off their feet."),
        WATER_WAVE("Water Wave", Book.SUN, 65, 7, 18, 30, 38, false, Element.WATER, Tier.WAVE, "A wave of water."),
        ELECTRIC_WAVE("Electric Wave", Book.SUN, 68, 8, 19, 36, 40, false, Element.ELECTRIC, Tier.WAVE, "A wave of lightning."),
        EARTH_WAVE("Earth Wave", Book.SUN, 70, 8, 19, 28, 41, false, Element.EARTH, Tier.WAVE, "A wave of earth."),
        FIRE_WAVE("Fire Wave", Book.SUN, 75, 8, 20, 30, 44, false, Element.FIRE, Tier.WAVE, "A wave of fire that catches those beside the target."),
        WIND_SURGE("Wind Surge", Book.SUN, 81, 9, 21, 32, 47, false, Element.AIR, Tier.SURGE, "A surge of wind that hurls foes off their feet."),
        WATER_SURGE("Water Surge", Book.SUN, 85, 9, 22, 30, 50, false, Element.WATER, Tier.SURGE, "A surge of water."),
        ELECTRIC_SURGE("Electric Surge", Book.SUN, 87, 10, 23, 36, 51, false, Element.ELECTRIC, Tier.SURGE, "A surge of lightning."),
        EARTH_SURGE("Earth Surge", Book.SUN, 90, 10, 23, 28, 53, false, Element.EARTH, Tier.SURGE, "A surge of earth."),
        FIRE_SURGE("Fire Surge", Book.SUN, 95, 10, 24, 30, 56, false, Element.FIRE, Tier.SURGE, "A surge of fire that catches those beside the target."),
        FORCEWAVE("Forcewave", Book.SUN, 40, 8, 0, 0, 28, true, Element.AIR, null, "A short concussive cone that interrupts and pushes enemies back."),
        // Fogish
        NECROCALL("Necrocall", Book.FOGISH, 30, 9, 0, 0, 24, true, Element.BLOOD, null, "Calls one temporary unarmed undead ally. At most two can serve one caster."),
        SPELLDAGGERS("Spelldaggers", Book.FOGISH, 40, 10, 12, 28, 28, false, Element.NONE, null, "Conjures three spectral blades that steer gently toward a visible foe."),
        ICE_RUSH("Ice Rush", Book.FOGISH, 58, 10, 16, 34, 34, false, Element.ICE, null, "A rush of ice that freezes the target in place for 5 seconds."),
        ICE_BURST("Ice Burst", Book.FOGISH, 70, 12, 22, 34, 42, false, Element.ICE, null, "A burst of ice that freezes the target in place for 10 seconds."),
        ICE_BLITZ("Ice Blitz", Book.FOGISH, 82, 14, 26, 36, 48, false, Element.ICE, null, "A blitz of ice that freezes the target in place for 15 seconds."),
        ICE_BARRAGE("Ice Barrage", Book.FOGISH, 94, 16, 30, 36, 54, false, Element.ICE, null, "A barrage of ice that freezes the target in place for 20 seconds."),
        // Moon (the old Lunar and Arcane books)
        SPEAK_WITH_ANIMALS("Speak With Animals", Book.MOON, 1, 1, 0, 0, 4, true, Element.NONE, null, "Hear what an animal is thinking; it takes to you a little."),
        MINOR_ILLUSION("Minor Illusion", Book.MOON, 1, 1, 0, 0, 4, true, Element.NONE, null, "A sound where you look - footsteps, a clatter - that draws those who hear it."),
        SPIRITUAL_STEP("Spiritual Step", Book.MOON, 10, 3, 0, 0, 8, true, Element.NONE, null, "Step through the air to where you look, up to six metres."),
        DARKNESS("Darkness", Book.MOON, 10, 4, 0, 0, 10, true, Element.NONE, null, "A globe of magical darkness where you look, for twenty seconds."),
        TELEGRAB("Telekinetic Grab", Book.MOON, 20, 3, 0, 0, 14, true, Element.NONE, null, "Pull a loose item to your hand from afar."),
        MORPHBEAST("Morphbeast", Book.MOON, 20, 6, 0, 0, 16, true, Element.NONE, null, "Turns a living target into a harmless sheep for eight seconds (players: 2.5 seconds); identity and belongings remain."),
        GASEOUS_FORM("Gaseous Form", Book.MOON, 30, 6, 0, 0, 20, true, Element.NONE, null, "For ten seconds you are mist: hard to see, silent, untouchable - and unable to strike."),
        DISGUISE_SELF("Disguise Self", Book.MOON, 30, 8, 0, 0, 22, true, Element.NONE, null, "For a minute you wear a stranger's face: what witnesses see, they cannot put a name to."),
        ENHANCE_ABILITY("Enhance Ability", Book.MOON, 40, 8, 0, 0, 26, true, Element.NONE, null, "For a minute your hands are surer: gathering, picking pockets and climbing all come easier."),
        CHRONOSLOW("Chronoslow", Book.MOON, 40, 8, 0, 0, 28, true, Element.NONE, null, "Slows hostile movement and combat actions in a small visible area for six seconds."),
        FRIENDS("Friends", Book.MOON, 50, 8, 0, 0, 30, true, Element.NONE, null, "The person you look at takes you for a friend for two minutes: they will not report you, and they trade kindly."),
        CHARM_PERSON("Charm Person", Book.MOON, 50, 10, 0, 0, 32, true, Element.NONE, null, "The person you look at is charmed for half a minute: they will not lift a hand against you."),
        VITALMEND("Vitalmend", Book.MOON, 60, 12, 0, 0, 38, true, Element.NONE, null, "Restores health and closes bleeding wounds on yourself or a visible ally.");

        public final String title, effect;
        public final Book book;
        public final int level, cost, xp;
        /** The spell's own base maximum hit (see {@link #maxBase} for what it hits for a caster), and its speed. */
        public final float base, speed;
        public final boolean utility;
        public final Element element;
        /** Sun's tier (null: not one of the tiered elemental spells). */
        public final Tier tier;
        Spell(String title, Book book, int level, int cost, float base, float speed, int xp, boolean utility, Element element, Tier tier, String effect) {
            this.title = title; this.book = book; this.level = level; this.cost = cost; this.base = base; this.speed = speed; this.xp = xp;
            this.utility = utility; this.element = element; this.tier = tier; this.effect = effect;
        }

        /** The base maximum hit at a Magic level: a tiered spell hits as hard as the strongest of its tier that is open. */
        public float maxBase(int magic) {
            if (tier == null) return base;
            float best = base;
            for (Spell o : values()) if (o.tier == tier && o.level <= magic) best = Math.max(best, o.base);
            return best;
        }
        /** The seconds a Fogish ice spell freezes its target in place (0: none). */
        public float freeze() { return switch (this) { case ICE_RUSH -> 5; case ICE_BURST -> 10; case ICE_BLITZ -> 15; case ICE_BARRAGE -> 20; default -> 0; }; }
        /** How likely a Wind spell is to throw a target down, before size and skill (Blast and up). */
        public float knockdown() { return element != Element.AIR || tier == null ? 0 : switch (tier) { case BLAST -> 0.22f; case WAVE -> 0.35f; case SURGE -> 0.5f; default -> 0; }; }
        /** What it costs with a staff of this element in hand: an elemental staff makes its own element's spells very cheap. */
        public int cost(Element staff) { return staff != null && staff != Element.NONE && staff == element && tier != null ? Math.max(1, Math.round(cost * 0.15f)) : cost; }
        /** A saved spell by its name, the replaced ones included (Spark was Wind Strike; Frost lance and Voltchain gave way to the new spells). */
        public static Spell of(String name) {
            return switch (name) { case "SPARK" -> WIND_STRIKE; case "FROST_LANCE" -> ICE_RUSH; case "VOLTCHAIN" -> ELECTRIC_BOLT; default -> valueOf(name); };
        }
    }

    /** A book's spells in the order they open (by Magic level). */
    public static java.util.List<Spell> book(Book b) {
        java.util.List<Spell> l = new java.util.ArrayList<>();
        for (Spell s : Spell.values()) if (s.book == b) l.add(s);
        l.sort(java.util.Comparator.comparingInt((Spell s) -> s.level).thenComparing(Enum::ordinal));
        return l;
    }

    /** The prayer categories: at most one active in each of OVERHEAD and STRENGTH; the others combine. */
    public enum Category { OVERHEAD, STRENGTH, ARCHERY, GUARD, RESTORE, HOLY }

    public enum Prayer {
        WARD("Ward", 1, Category.GUARD, "A holy ward: incoming blows land 15% softer."),
        BURST_OF_STRENGTH("Burst of Strength", 4, Category.STRENGTH, "Strength +12%: your landed blows hit harder."),
        MEND("Mend", 10, Category.RESTORE, "Heals 1 HP each time a point of Faith is paid."),
        SMITE("Smite", 25, Category.HOLY, "Your blade burns with holy force: +10% damage to what it strikes."),
        PROTECT_FROM_MAGIC("Protect from Magic", 37, Category.OVERHEAD, "Negates all damage from spells."),
        PROTECT_FROM_MISSILES("Protect from Missiles", 40, Category.OVERHEAD, "Negates all damage from arrows, bolts and thrown weapons."),
        PROTECT_FROM_MELEE("Protect from Melee", 43, Category.OVERHEAD, "Negates all damage from melee blows and bites."),
        PIETY("Piety", 70, Category.STRENGTH, "Attack tempo +15%, Strength +20%, Defense (your guard) +20%."),
        RIGOUR("Rigour", 74, Category.ARCHERY, "Archery damage +20%, draw and reload 15% faster.");

        public final String title, effect;
        public final int level;
        public final Category category;
        Prayer(String title, int level, Category category, String effect) { this.title = title; this.level = level; this.category = category; this.effect = effect; }
    }
}
