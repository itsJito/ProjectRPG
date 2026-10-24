package rpg.animal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import rpg.Rpg;
import rpg.actor.Actor;
import rpg.interact.Interactable;
import rpg.interact.Option;
import rpg.interact.Result;
import rpg.item.ItemDef;
import rpg.item.Items;
import rpg.skill.Skill;
import rpg.world.DevMap;

/**
 * One animal (GDD 3.9): an actor like any other - skills, hit points, a body the engine walks and a hitbox that blows
 * land on - with a species, a life stage, a sex, needs, a home range, a group, a mother, an owner (or none), the people it
 * knows, and a pregnancy when it has one. Its goals come from the shared animal mind ({@link AnimalMind}); what it is
 * doing and why is kept for the inspector. Dead, it is a carcass: still here, butchered once, eaten by scavengers, gone
 * when it has decayed.
 */
public final class Animal extends Actor implements Interactable {
    public enum Goal { IDLE, GRAZE, DRINK, REST, WANDER, FOLLOW, ALERT, WARN, FLEE, DEFEND, HUNT, FEED, MATE, NEST, SEEK, PLAY, GROOM }

    public final Species species;
    public boolean baby, female;
    /** Game minutes: born at, grown at (babies). */
    public double bornAt, matureAt;
    public long mother, owner, group;
    /** Its home: the centre of the range it keeps to, and how far it goes. */
    public float homeX, homeZ, range = 10;
    /** Which look of the species (coat, markings). */
    public int variant;
    /** Needs, 0 (met) .. 1 (pressing), and fear (0 calm .. 1 panic). */
    public float hunger, thirst, fatigue, fear;
    /** What it is doing, since when, why; who it is dealing with (a threat, its prey, its mate, the one it follows). */
    public Goal goal = Goal.IDLE;
    public double goalSince, nextThink;
    public String reason = "", lastEvent = "";
    public long other;
    public final List<String> scores = new ArrayList<>();
    /** The habitat it belongs to (null: a stray, like the town cat). */
    public Habitat home;
    /** What frightens it: who, where it was last placed, since when; fear raised the group alarm. */
    public long threat;
    public float threatX, threatZ;
    public double threatSince, threatSeen = -99;
    public boolean alarmed, menacing;
    /** How the figure is posed this frame: lying at rest, head down to eat or drink, rearing, eyes on the threat. */
    public boolean atRest, headDown, rearing, faceThreat;
    /** The heading it wants (NaN: the way it walks); its spot at the resting place. */
    public float wantYaw = Float.NaN, restOx, restOz;
    public double lastBite = -99, nextVoice, lastEgg = -1e9;
    public double careAgain;
    /** The route last asked of the engine. */
    float rx, rz;
    boolean routed;
    /** Carcass: how long scavengers have eaten at it (game minutes). */
    /** When each person last petted it (a bond grows from a pattern, not from clicking). */
    public final Map<Long, Double> pettedAt = new HashMap<>();
    /** Where it heads (when it has somewhere to be). */
    public float gx, gz;
    public boolean hasGoal;
    /** A pregnancy (mammals) or a clutch being laid (birds): due at, and the father; when it last bred. */
    public double dueAt = -1, lastBred = -1e9;
    public long father;
    /** The people it knows and how much it trusts them (0 .. 1). */
    public final Map<Long, Float> trust = new HashMap<>();
    /** Engine handles and the facts last written to its agent. */
    public long agent;
    public boolean loaded = true;
    final float[] facts = new float[AnimalMind.ACTIONS];
    long targetBody = -1;
    public boolean running;
    /** On the slow (young or spent) gaits. */
    public boolean slow;
    // ---- a carcass
    public double diedAt = -1;
    public boolean butchered;
    public int eaten;
    public long killer;
    // ---- for the figure
    public float gait, headPitch, tailWag, lieT, amp, headYaw, flap;
    public double lastSound = -99;
    /** Held for a picture (the gallery): no mind, it stands or walks where it is told. */
    public boolean puppet;
    /** Its body in physics' hands (dead, or knocked off its feet), or null; and the last blow it took (direction, how hard). */
    public AnimalRagdoll ragdoll;
    /** Parked dead-body transforms retain the exact visible pose outside the physics budget. */
    public final java.util.Map<rpg.combat.Gore.Part,astratto.bridge.Snapshots.Rigid> restPose = new java.util.EnumMap<>(rpg.combat.Gore.Part.class);
    public float blowX, blowZ, blowImpact;

    public Animal(long id, Species s, boolean baby, boolean female, float x, float z) {
        super(id, s.label(baby), 0);
        this.species = s; this.baby = baby; this.female = female;
        this.x = x; this.z = z; this.y = DevMap.FLOOR;
        this.homeX = x; this.homeZ = z;
        this.team = 10 + s.ordinal();
        skills.setLevel(Skill.ATTACK, s.attack); skills.setLevel(Skill.STRENGTH, s.strength); skills.setLevel(Skill.DEFENSE, s.defense);
        skills.setLevel(Skill.HEALTH, s.health); skills.setLevel(Skill.AGILITY, s.agility);
        fit();
        restore();
    }

    /** The hitbox for its stage: the species' size, or a baby's scaled one. */
    public void fit() {
        float k = scale();
        radius = species.w * 0.5f * k;
        height = species.h * k;
    }

    /** Its size against an adult: half (a foal's own share) when newborn, growing to full as it matures. */
    public float scale() {
        if (!baby) return 1;
        return species.babyScale;
    }

    /** Hit points: the species' Health level, as a person's are (gamefixes: animals live by the skill system too), halved for a youngster. */
    @Override public float maxHp() { return Math.max(1, Math.round(skills.level(Skill.HEALTH) * (baby ? 0.5f : 1f))); }
    @Override public float maxStamina() { return 60 + skills.level(Skill.AGILITY) * 2; }
    @Override public float eyeY() { return y + height * (species.plan == Species.Plan.BIRD ? 0.85f : 0.82f); }
    @Override public float chestY() { return y + height * 0.5f; }

    public boolean owned() { return owner != 0; }
    public boolean adult() { return !baby; }
    public boolean carcass() { return !alive && diedAt >= 0; }
    public float trustOf(long who) { return trust.getOrDefault(who, 0f); }
    public String kind() { return species.label(baby); }

    /** Dangerous to people now: hunting, defending, or already in a fight. */
    public boolean dangerous() { return alive && menacing; }

    // ------------------------------------------------------------------ Interactable
    @Override public long id() { return id; }
    @Override public String label() { return carcass() ? kind() + " carcass" : (owned() ? kind() + " (owned)" : kind()); }
    @Override public float ax() { return x; }
    @Override public float ay() { return y + (carcass() ? 0.25f : height * 0.6f); }
    @Override public float az() { return z; }
    @Override public float focusRadius() { return Math.max(0.35f, Math.min(0.9f, radius + 0.15f)); }
    @Override public boolean exists() { return loaded && (alive || carcass() && !butchered); }
    @Override public long owner() { return owner; }

    @Override public List<Option> options(Rpg g, Actor a) {
        List<Option> o = new ArrayList<>();
        float r = g.cfg.interactRange;
        if (carcass()) {
            boolean blade = Animals.blade(a) != null;
            o.add(Option.when(blade && !butchered, "butcher", "Butcher", 10, butchered ? "Nothing left to take." : "You need a knife or a blade.", r));
            o.add(Option.when(carriedBy == 0 && a.carryBody == 0 && rpg.actor.BodyCarry.lightEnough(this), "carry", "Carry carcass", 5, rpg.actor.BodyCarry.lightEnough(this) ? "That body is already being carried." : "That body is too heavy to lift.", 2.2f));
        } else {
            boolean calm = goal != Goal.FLEE && goal != Goal.DEFEND && goal != Goal.WARN && goal != Goal.HUNT;
            boolean pettable = calm && (species.domestic || trustOf(a.id) > 0.3f) && species != Species.BROWN_BEAR;
            o.add(Option.when(pettable, "pet", "Pet", 6, !calm ? "It's too agitated." : "It won't let you near enough.", r));
            ItemDef food = Animals.feedFor(this, a);
            o.add(Option.when(food != null && calm, "feed", "Feed" + (food == null ? "" : " (" + food.name.toLowerCase() + ")"), 7, food == null ? "You have nothing it would eat." : "It's too agitated.", r));
        }
        o.add(Option.on("examine", "Examine", 0, g.cfg.inspectRange));
        return o;
    }

    @Override public Result perform(Rpg g, Actor a, String action, long req) {
        if (action.equals("carry")) return g.carrying.begin(a, this);
        if (carriedBy != 0) return Result.no("Put the body down first.");
        return g.animals.perform(this, a, action, req);
    }

    @Override public String examine(Rpg g) { return g.animals.examine(this); }

    @Override public String inspect(Rpg g) {
        return String.format("%s %s %s, %s; goal %s (%s); hunger %.0f%% thirst %.0f%% fear %.0f%%%s%s", kind(), female ? "female" : "male",
                baby ? "baby" : "adult", owned() ? "owned by " + g.nameOf(owner) : "wild", goal, reason, hunger * 100, thirst * 100, fear * 100,
                dueAt > 0 ? String.format("; due in %.0f min", dueAt - g.state.minutes) : "", baby ? String.format("; grows up in %.0f min", matureAt - g.state.minutes) : "");
    }

    /** What a carcass gives, once: meat and hide (as the species has them), bones, feathers. */
    public List<rpg.item.ItemStack> yields() {
        List<rpg.item.ItemStack> l = new ArrayList<>();
        float k = baby ? 0.5f : 1f;
        int meat = Math.max(species.meat > 0 ? 1 : 0, Math.round(species.meat * k)), hide = Math.round(species.hide * k);
        if (meat > 0) l.add(new rpg.item.ItemStack(Items.RAW_MEAT, meat));
        if (hide > 0) l.add(new rpg.item.ItemStack(Items.RAWHIDE, hide));
        l.add(new rpg.item.ItemStack(species.bigBones && !baby ? Items.BIG_BONES : Items.BONES, 1));
        if (species.feathers > 0) l.add(new rpg.item.ItemStack(Items.FEATHER, Math.max(1, Math.round(species.feathers * k))));
        return l;
    }
}
