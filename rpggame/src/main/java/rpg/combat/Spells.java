package rpg.combat;

/**
 * Magic's spells and Faith's prayers (GDD 2.5, established).
 *
 * <p><b>Magic</b>: four spellbooks, each a progression list of eleven slots unlocked at Magic 1, 10, 20 ... 100 (the
 * books are not skills); one book is active at a time, chosen at an altar. Every committed cast costs Mana; the pool is
 * the Magic level itself and regains one point a minute. Only the spells the game has (and the GDD names) fill slots; the
 * rest of each book stays empty until content fills it. Elemental attacks carry their element (Fire, Water, Earth, Air;
 * Ice and Blood for the Ancients), so a creature with a recorded weakness takes double from it.
 *
 * <p><b>Faith</b>: prayers are toggled on and off, each draining one Faith point per six seconds (longer with an equipped
 * Faith bonus: +3.33% of the interval per point), paid at activation for the first interval and never refunded. The pool
 * is the Faith level and does not refill by itself - an altar, religious wine or a restorative potion does that. The
 * overhead protections negate their attack type entirely, one at a time; the stat prayers multiply their stats.
 */
public final class Spells {
    private Spells() {}

    public enum Book {
        STANDARD("Standard", "Elemental offensive magic."), ANCIENTS("Ancients", "Ice- and blood-themed combat magic."),
        LUNAR("Lunar", "Traversal, enhancement and gentle, social magic."), ARCANE("Arcane", "Illusion, obscuration, identity and influence.");
        public final String title, about;
        Book(String title, String about) { this.title = title; this.about = about; }
    }

    /** The elements (GDD 2.5): the Standard four; Ice and Blood for the Ancients. */
    public enum Element { NONE, FIRE, WATER, EARTH, AIR, ICE, BLOOD, ELECTRIC }

    public enum Spell {
        //            title                   book            level cost base  speed xp  utility element
        SPARK("Spark", Book.STANDARD, 1, 1, 10, 32, 5, false, Element.AIR, "A crackling bolt of air."),
        FIRE_BOLT("Fire bolt", Book.STANDARD, 10, 3, 22, 30, 11, false, Element.FIRE, "A bolt of flame."),
        FIRE_BLAST("Fire Blast", Book.STANDARD, 20, 5, 34, 34, 18, false, Element.FIRE, "The Fire staff's explosive blast: it spends a charge of the staff as well as Mana."),
        FROST_LANCE("Frost lance", Book.ANCIENTS, 10, 4, 26, 38, 16, false, Element.ICE, "A spear of ice that slows what it strikes."),
        SPEAK_WITH_ANIMALS("Speak With Animals", Book.LUNAR, 1, 1, 0, 0, 4, true, Element.NONE, "Hear what an animal is thinking; it takes to you a little."),
        SPIRITUAL_STEP("Spiritual Step", Book.LUNAR, 10, 3, 0, 0, 8, true, Element.NONE, "Step through the air to where you look, up to six metres."),
        TELEGRAB("Telekinetic grab", Book.LUNAR, 20, 3, 0, 0, 14, true, Element.NONE, "Pull a loose item to your hand from afar."),
        GASEOUS_FORM("Gaseous Form", Book.LUNAR, 30, 6, 0, 0, 20, true, Element.NONE, "For ten seconds you are mist: hard to see, silent, untouchable - and unable to strike."),
        ENHANCE_ABILITY("Enhance Ability", Book.LUNAR, 40, 8, 0, 0, 26, true, Element.NONE, "For a minute your hands are surer: gathering, picking pockets and climbing all come easier."),
        FRIENDS("Friends", Book.LUNAR, 50, 8, 0, 0, 30, true, Element.NONE, "The person you look at takes you for a friend for two minutes: they will not report you, and they trade kindly."),
        MINOR_ILLUSION("Minor Illusion", Book.ARCANE, 1, 1, 0, 0, 4, true, Element.NONE, "A sound where you look - footsteps, a clatter - that draws those who hear it."),
        DARKNESS("Darkness", Book.ARCANE, 10, 4, 0, 0, 10, true, Element.NONE, "A globe of magical darkness where you look, for twenty seconds."),
        DISGUISE_SELF("Disguise Self", Book.ARCANE, 30, 8, 0, 0, 22, true, Element.NONE, "For a minute you wear a stranger's face: what witnesses see, they cannot put a name to."),
        VOLTCHAIN("Voltchain", Book.STANDARD, 30, 7, 24, 36, 24, false, Element.ELECTRIC, "An electric bolt that shocks touching wet or conductive targets."),
        FORCEWAVE("Forcewave", Book.STANDARD, 40, 8, 0, 0, 28, true, Element.AIR, "A short concussive cone that interrupts and pushes enemies back."),
        VITALMEND("Vitalmend", Book.LUNAR, 60, 12, 0, 0, 38, true, Element.NONE, "Restores health and closes bleeding wounds on yourself or a visible ally."),
        MORPHBEAST("Morphbeast", Book.ARCANE, 20, 6, 0, 0, 16, true, Element.NONE, "Turns a living target into a harmless sheep for eight seconds; identity and belongings remain."),
        CHRONOSLOW("Chronoslow", Book.ARCANE, 40, 8, 0, 0, 28, true, Element.NONE, "Slows hostile movement and combat actions in a small visible area for six seconds."),
        NECROCALL("Necrocall", Book.ANCIENTS, 30, 9, 0, 0, 24, true, Element.BLOOD, "Calls one temporary unarmed undead ally. At most two can serve one caster."),
        SPELLDAGGERS("Spelldaggers", Book.ANCIENTS, 40, 10, 12, 28, 28, false, Element.NONE, "Conjures three spectral blades that steer gently toward a visible foe."),
        CHARM_PERSON("Charm Person", Book.ARCANE, 50, 10, 0, 0, 32, true, Element.NONE, "The person you look at is charmed for half a minute: they will not lift a hand against you.");

        public final String title, effect;
        public final Book book;
        public final int level, cost, xp;
        public final float base, speed;
        public final boolean utility;
        public final Element element;
        Spell(String title, Book book, int level, int cost, float base, float speed, int xp, boolean utility, Element element, String effect) {
            this.title = title; this.book = book; this.level = level; this.cost = cost; this.base = base; this.speed = speed; this.xp = xp;
            this.utility = utility; this.element = element; this.effect = effect;
        }
    }

    /** The eleven slot levels of every book. */
    public static final int[] SLOTS = {1, 10, 20, 30, 40, 50, 60, 70, 80, 90, 100};

    /** The spell in a book's slot (null: an empty slot, content not yet written). */
    public static Spell slot(Book b, int level) {
        for (Spell s : Spell.values()) if (s.book == b && s.level == level) return s;
        return null;
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
