package rpg.animal;

import java.util.EnumSet;
import java.util.Set;
import rpg.item.ItemDef.DamageType;

/**
 * The fourteen land animals (GDD 3.9) as data: one shared animal mind reads these numbers, so the species differ by what
 * they are - their size (the gamefixes hitboxes, width x height in metres), their gaits, what they eat and what eats
 * them, how they keep together, how they meet a threat, how they fight (their own skill levels, as people have), how
 * they breed and grow, and what is left when one dies. Babies are the same model at half scale (a foal at its own).
 */
public enum Species {
    //            name          baby        w       h     baby  plan          walk  run   diet               group             size  dom    night
    CHICKEN("Chicken", "Chick", 0.4f, 0.7f, 0.5f, Plan.BIRD, 0.9f, 3.2f, Diet.FORAGER, Group.FLOCK, 4, 8, true, false),
    PIG("Pig", "Piglet", 0.9f, 0.9f, 0.5f, Plan.QUAD, 1.1f, 3.6f, Diet.FORAGER, Group.HERD, 2, 5, true, false),
    HORSE("Horse", "Foal", 1.3965f, 1.6f, 0.62f, Plan.QUAD, 1.6f, 7.5f, Diet.GRAZER, Group.HERD, 2, 6, true, false),
    RABBIT("Rabbit", "Kit", 0.4f, 0.5f, 0.5f, Plan.HOPPER, 1.0f, 5.6f, Diet.GRAZER, Group.FAMILY, 2, 5, false, false),
    FOX("Fox", "Kit", 0.6f, 0.7f, 0.5f, Plan.QUAD, 1.3f, 5.8f, Diet.HUNTER, Group.SOLO, 1, 2, false, true),
    COYOTE("Coyote", "Pup", 0.6f, 0.85f, 0.5f, Plan.QUAD, 1.4f, 6.2f, Diet.HUNTER, Group.FAMILY, 1, 3, false, true),
    WOLF("Wolf", "Pup", 0.6f, 0.85f, 0.5f, Plan.QUAD, 1.5f, 6.6f, Diet.HUNTER, Group.PACK, 3, 6, false, true),
    MOUNTAIN_LION("Mountain lion", "Cub", 0.7f, 0.8f, 0.5f, Plan.QUAD, 1.3f, 7.2f, Diet.HUNTER, Group.SOLO, 1, 1, false, true),
    BROWN_BEAR("Brown bear", "Cub", 1.35f, 1.4f, 0.5f, Plan.QUAD, 1.2f, 5.4f, Diet.OMNIVORE, Group.SOLO, 1, 1, false, false),
    DUCK("Duck", "Duckling", 0.4f, 0.7f, 0.5f, Plan.BIRD, 0.8f, 2.8f, Diet.FORAGER, Group.FLOCK, 3, 7, true, false),
    COW("Cow", "Calf", 0.9f, 1.4f, 0.5f, Plan.QUAD, 1.0f, 4.2f, Diet.GRAZER, Group.HERD, 2, 6, true, false),
    SHEEP("Sheep", "Lamb", 0.9f, 1.3f, 0.5f, Plan.QUAD, 1.0f, 4.4f, Diet.GRAZER, Group.FLOCK, 3, 8, true, false),
    DOG("Dog", "Puppy", 0.7f, 0.9f, 0.5f, Plan.QUAD, 1.5f, 6.0f, Diet.SCAVENGER, Group.SOLO, 1, 3, true, false),
    CAT("Cat", "Kitten", 0.5f, 0.6f, 0.5f, Plan.QUAD, 1.0f, 5.0f, Diet.SCAVENGER, Group.SOLO, 1, 1, true, true);

    /** Body plan: how the model is built and how it walks. */
    public enum Plan { QUAD, BIRD, HOPPER }
    /** What it eats: grass and browse, scraps and seeds, other animals, or a little of everything. */
    public enum Diet { GRAZER, FORAGER, HUNTER, OMNIVORE, SCAVENGER }
    /** How it keeps company. */
    public enum Group { SOLO, FAMILY, PACK, HERD, FLOCK }

    public final String label, babyLabel;
    /** The adult hitbox (metres): width (the body's capsule diameter) and height. */
    public final float w, h;
    /** A baby's scale of the adult model and hitbox. */
    public final float babyScale;
    public final Plan plan;
    /** Walking and running speeds (m/s), adult. */
    public final float walk, run;
    public final Diet diet;
    public final Group group;
    public final int groupMin, groupMax;
    /** Kept by people (may be owned); wild species are never anyone's. */
    public final boolean domestic;
    /** Most active by night (rests by day). */
    public final boolean nocturnal;

    // ---- filled in below, species by species
    /** Skill levels, as people have them: Attack, Strength, Defense, Health, Agility. */
    public int attack = 1, strength = 1, defense = 1, health = 1, agility = 1;
    /** Its natural weapon (bite, kick, peck, claw): base damage, impact, the type, how far it reaches, and its name. */
    public float damage = 1, impact = 4, reach = 0.4f;
    public DamageType damageType = DamageType.PIERCE;
    public String weapon = "bite";
    /** An elemental weakness on record (GDD 2.5): a spell of that element strikes it double. */
    public rpg.combat.Spells.Element weakness = rpg.combat.Spells.Element.NONE;
    /** How sharp its senses are (the stealth exposure's acuity) and how far it hears (a multiple of a noise's radius). */
    public float acuity = 1, hearing = 1;
    /** How close a stranger may come before it is uneasy (m), and before it flees (m). 0: it does not mind people. */
    public float wary = 0, flight = 0;
    /** Whether it stands its ground: defends its young (or itself when cornered) instead of fleeing. */
    public boolean defends;
    /** What it hunts (when hungry) and what it will scavenge. */
    public final Set<Species> prey = new java.util.HashSet<>();
    /** Breeding: gestation or incubation (game minutes), young per birth, game minutes to grow up, minutes before it breeds again. */
    public float gestation = 600, mature = 1440, rest = 900;
    public int litterMin = 1, litterMax = 2;
    /** Lays a clutch in a nest (birds) rather than giving birth. */
    public boolean lays;
    /** What a carcass gives when butchered: meat, hide, the kind of bones, feathers. */
    public int meat, hide, feathers;
    public boolean bigBones;
    /** The noises it makes: when idle (a moo, a cluck), when alarmed, when hurt, when it dies (sound keys). */
    public String idleSound, alarmSound, hurtSound, deathSound;
    /** Builds the model at this scale per voxel (shared with people: about 5.6 cm). */
    public final float px = 1.8f / 32f;
    static {
        // the weaknesses on record: fur and fire
        for (Species s : new Species[]{BROWN_BEAR, WOLF, COYOTE, FOX, MOUNTAIN_LION}) s.weakness = rpg.combat.Spells.Element.FIRE;
    }

    Species(String label, String babyLabel, float w, float h, float babyScale, Plan plan, float walk, float run, Diet diet, Group group, int gMin, int gMax, boolean domestic, boolean nocturnal) {
        this.label = label; this.babyLabel = babyLabel; this.w = w; this.h = h; this.babyScale = babyScale; this.plan = plan;
        this.walk = walk; this.run = run; this.diet = diet; this.group = group; this.groupMin = gMin; this.groupMax = gMax;
        this.domestic = domestic; this.nocturnal = nocturnal;
    }

    private Species skills(int att, int str, int def, int hp, int agi) { attack = att; strength = str; defense = def; health = hp; agility = agi; return this; }
    private Species weapon(String name, float dmg, float imp, float reach, DamageType type) { weapon = name; damage = dmg; impact = imp; this.reach = reach; damageType = type; return this; }
    private Species senses(float acuity, float hearing, float wary, float flight) { this.acuity = acuity; this.hearing = hearing; this.wary = wary; this.flight = flight; return this; }
    private Species breeds(float gestation, int min, int max, float mature, float rest) { this.gestation = gestation; litterMin = min; litterMax = max; this.mature = mature; this.rest = rest; return this; }
    private Species yields(int meat, int hide, boolean bigBones, int feathers) { this.meat = meat; this.hide = hide; this.bigBones = bigBones; this.feathers = feathers; return this; }
    private Species sounds(String idle, String alarm, String hurt, String death) { idleSound = idle; alarmSound = alarm; hurtSound = hurt; deathSound = death; return this; }

    static {
        CHICKEN.skills(1, 1, 1, 3, 6).weapon("peck", 1, 2, 0.3f, DamageType.PIERCE).senses(1.25f, 1.1f, 2.5f, 1.4f).breeds(480, 3, 5, 1440, 960).yields(1, 0, false, 6).sounds("animal.chicken", "animal.chicken.alarm", "animal.chicken.alarm", "animal.chicken.alarm");
        CHICKEN.lays = true;
        PIG.skills(5, 8, 4, 10, 3).weapon("bite", 3, 18, 0.45f, DamageType.BLUNT).senses(0.9f, 1.1f, 2.5f, 1.4f).breeds(600, 2, 4, 2160, 1200).yields(4, 1, true, 0).sounds("animal.pig", "animal.pig.alarm", "animal.pig.alarm", "animal.pig.alarm");
        HORSE.skills(10, 22, 12, 25, 25).weapon("kick", 7, 70, 0.7f, DamageType.BLUNT).senses(1.2f, 1.3f, 4, 2).breeds(1200, 1, 1, 4320, 2400).yields(6, 2, true, 0).sounds("animal.horse", "animal.horse.alarm", "animal.horse.alarm", "animal.horse.alarm");
        HORSE.defends = true;
        RABBIT.skills(1, 1, 1, 3, 20).weapon("nip", 1, 2, 0.25f, DamageType.PIERCE).senses(1.4f, 1.6f, 9, 6).breeds(240, 2, 4, 1080, 600).yields(1, 1, false, 0).sounds(null, null, "animal.rabbit.hurt", "animal.rabbit.hurt");
        FOX.skills(8, 6, 6, 8, 20).weapon("bite", 3, 10, 0.4f, DamageType.PIERCE).senses(1.3f, 1.5f, 12, 7).breeds(480, 2, 4, 1440, 1440).yields(1, 1, false, 0).sounds("animal.fox", "animal.fox", "animal.fox.death", "animal.fox.death");
        FOX.prey.addAll(EnumSet.of(RABBIT, CHICKEN, DUCK));
        COYOTE.skills(12, 10, 8, 12, 18).weapon("bite", 4, 14, 0.45f, DamageType.PIERCE).senses(1.25f, 1.4f, 12, 7).breeds(480, 2, 4, 1440, 1440).yields(2, 1, false, 0).sounds("animal.coyote", "animal.coyote", "animal.dog.hurt", "animal.dog.hurt");
        COYOTE.prey.addAll(EnumSet.of(RABBIT, CHICKEN, DUCK, SHEEP, CAT));
        WOLF.skills(25, 22, 18, 25, 22).weapon("bite", 6, 26, 0.5f, DamageType.PIERCE).senses(1.3f, 1.5f, 14, 0).breeds(540, 2, 4, 2160, 1800).yields(3, 1, true, 0).sounds("animal.wolf", "animal.wolf.growl", "animal.dog.hurt", "animal.dog.hurt");
        WOLF.prey.addAll(EnumSet.of(RABBIT, SHEEP, PIG, CHICKEN, DUCK, COW, HORSE));
        MOUNTAIN_LION.skills(32, 30, 20, 30, 30).weapon("claw", 8, 40, 0.55f, DamageType.SLASH).senses(1.35f, 1.4f, 10, 0).breeds(600, 1, 2, 2160, 2160).yields(3, 1, true, 0).sounds("animal.lion", "animal.lion", "animal.lion", "animal.lion");
        MOUNTAIN_LION.prey.addAll(EnumSet.of(RABBIT, SHEEP, PIG, DUCK, CHICKEN, DOG, COYOTE, FOX));
        BROWN_BEAR.skills(26, 28, 20, 35, 12).weapon("swipe", 6, 70, 0.75f, DamageType.SLASH).senses(1.0f, 1.2f, 9, 0).breeds(900, 1, 2, 2880, 2880).yields(8, 2, true, 0).sounds("animal.bear", "animal.bear", "animal.bear", "animal.bear");
        BROWN_BEAR.defends = true;
        BROWN_BEAR.prey.addAll(EnumSet.of(RABBIT, PIG, SHEEP));
        DUCK.skills(1, 1, 1, 3, 6).weapon("peck", 1, 2, 0.3f, DamageType.PIERCE).senses(1.25f, 1.2f, 3, 1.6f).breeds(480, 3, 6, 1440, 960).yields(1, 0, false, 5).sounds("animal.duck", "animal.duck", "animal.duck", "animal.duck");
        DUCK.lays = true;
        COW.skills(6, 18, 10, 22, 4).weapon("butt", 5, 60, 0.6f, DamageType.BLUNT).senses(1.0f, 1.0f, 2.5f, 1.4f).breeds(900, 1, 1, 2880, 2160).yields(6, 2, true, 0).sounds("animal.cow", "animal.cow", "animal.cow", "animal.cow");
        COW.defends = true;
        SHEEP.skills(2, 4, 3, 8, 6).weapon("butt", 2, 20, 0.45f, DamageType.BLUNT).senses(1.1f, 1.2f, 3, 1.8f).breeds(720, 1, 2, 2160, 1440).yields(3, 1, false, 0).sounds("animal.sheep", "animal.sheep", "animal.sheep", "animal.sheep");
        DOG.skills(12, 10, 8, 14, 18).weapon("bite", 4, 16, 0.45f, DamageType.PIERCE).senses(1.3f, 1.8f, 0, 0).breeds(480, 2, 5, 1800, 1800).yields(2, 1, false, 0).sounds("animal.dog", "animal.dog", "animal.dog.hurt", "animal.dog.hurt");
        DOG.prey.addAll(EnumSet.of(RABBIT));
        CAT.skills(6, 3, 6, 6, 28).weapon("claw", 2, 4, 0.35f, DamageType.SLASH).senses(1.4f, 1.7f, 2.5f, 1.4f).breeds(420, 2, 4, 1440, 1440).yields(1, 1, false, 0).sounds("animal.cat", "animal.cat.growl", "animal.cat.growl", "animal.cat.growl");
        CAT.prey.addAll(EnumSet.of(RABBIT));
    }

    /** A wild hunter: has prey and is not kept by people. */
    public boolean predator() { return !prey.isEmpty() && !domestic; }

    /** Whether something of species s would hunt this one. */
    public boolean preyOf(Species s) { return s.prey.contains(this); }

    public String label(boolean baby) { return baby ? babyLabel : label; }
}
