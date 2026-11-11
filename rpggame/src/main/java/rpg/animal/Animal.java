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
    public enum Goal { IDLE, GRAZE, DRINK, REST, WANDER, FOLLOW, ALERT, WARN, FLEE, DEFEND, HUNT, FEED, MATE, NEST, SEEK, PLAY, GROOM, STAY }

    public final Species species;
    public long trappedBy;
    astratto.ai.TaskNetwork<Animal> needs;
    float needStep;
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
    /** (0.3.5) How far a duck afloat is tipped up to feed (0 .. 1), eased (drawn only). */
    public float dabble;
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
    /** Game minutes when it was last milked: a cow gives a bucket and then needs a few hours to fill again. */
    public double lastMilked = -1e9;
    /** (0.3.5, gamefixes: the textile industry) when a sheep was last shorn (its fleece grows back over a few days). */
    public double lastShorn = -1e9;
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
    // ---- (0.3.0, gamefixes) a mount: its own performance, the taming it has had (0..1, kept), who tamed it, its saddle,
    // its saddlebags, who rides it, a Pegasus's vigor and flight (pitch and bank, for the figure)
    public rpg.mount.MountStats mountStats;
    public float tame, vigor = 100, flightPitch, flightBank;
    public long tamedBy, rider;
    /**
     * (0.3.4, gamefixes: Follow, Stay and Dismiss for a tamed animal) what its keeper last told it: nothing (its own life
     * about its home), to come along with them, or to stay put where it was told; who told it; where it was told to stay.
     * Dismissed, it is still tamed and still theirs.
     */
    public enum Command { NONE, FOLLOW, STAY }
    public Command command = Command.NONE;
    public long commandBy;
    public float stayX, stayZ;
    /** Whether this one answers to someone's word: they tamed it, own it, or it trusts them as its own. */
    public boolean answersTo(Actor a) { return a != null && (tamedBy == a.id || owner == a.id || owned() && trustOf(a.id) > 0.6f || species.domestic && trustOf(a.id) > 0.8f); }
    /** (0.3.1) Whose saddle it wears: a saddle can go on an untamed horse first (it gentles the taming), and stays its owner's. */
    public long saddledBy;
    /**
     * (0.3.2, gamefixes: a saddle can be started however agitated the horse is) who has it by the bridle while a saddle is
     * eased on, and until when: it is held standing (AnimalMind) and settles a little the longer it is held.
     */
    public long heldBy;
    public double heldUntil = -1;
    /** (0.3.1) When it was last fed or gentled by hand toward its taming (a little each time, a while apart). */
    public double gentledAt = -1e9;
    public boolean saddled, flying;
    /**
     * (0.3.3, gamefixes: the rider clipped through a horse that rose under them) the drawn body's movement this frame
     * against its standing pose - gait bob, rearing, the canter's rock, a Pegasus's climb and bank - as a column-major
     * matrix the renderer fills; the rider is drawn carried by it, and when it was last filled (render clock).
     */
    public final float[] seatDelta = new float[16];
    public double seatAt = -1;
    /**
     * (0.3.9, gamefixes: Pegasus flight animation polished and dynamic) the wings' stroke phase (radians, run on by the
     * work the wings are doing), the airspeed and the height over the ground under it while flown, and when its rider last
     * called for a surge of the wings.
     */
    public float wingPhase, flightSpeed, flightHeight = 99;
    public double surgeAt = -99;
    public final rpg.item.Inventory bags = new rpg.item.Inventory(6, 4);

    public Animal(long id, Species s, boolean baby, boolean female, float x, float z) {
        super(id, s.label(baby), 0);
        this.domain=astratto.ai.AgentDomain.ANIMAL;
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
    /**
     * (0.3.6, gamefixes: tamed animals defended themselves against their owner) whether this one is tamed to someone - its
     * owner, the one who tamed it, or one it has come to trust fully: it never takes them for a threat, however they treat it.
     */
    public boolean tamedTo(long who) { return who != 0 && (owner == who || tamedBy == who || trustOf(who) >= TRUSTED); }
    /** Trust at which an animal is as good as tamed to someone. */
    public static final float TRUSTED = 0.9f;
    public boolean adult() { return !baby; }
    public boolean carcass() { return !alive && diedAt >= 0; }
    public float trustOf(long who) { return trust.getOrDefault(who, 0f); }
    public String kind() { return species.label(baby); }

    /** Dangerous to people now: hunting, defending, or already in a fight. */
    public boolean dangerous() { return alive && menacing; }
    /** A warning is directed at its subject; an actual attack also endangers nearby bystanders. */
    /**
     * (0.3.3, gamefixes: everyone performing their duties) a fox or a coyote on the hunt is after hens and rabbits, not
     * a grown man: it threatens only the one it is going for (a fisherman spent his working days running from foxes).
     * Wolves, bears and big cats on the prowl are a danger to anyone near.
     */
    public boolean threatens(Actor observer) {
        if (!dangerous() || goal == Goal.WARN && other != observer.id) return false;
        boolean small = species == Species.FOX || species == Species.COYOTE;
        return !small || other == observer.id || observer instanceof rpg.animal.Animal;
    }

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
            if (species == Species.COW && female && !baby) {
                boolean full = g.state.minutes - lastMilked > Animals.MILK_REFILL;
                o.add(Option.when(calm && full && a.has(Items.BUCKET, 1), "milk", "Milk", 8, !calm ? "It's too agitated." : !full ? "She has been milked recently." : "You need an empty bucket.", r));
            }
            if (species == Species.SHEEP && !baby) {
                boolean grown = g.state.minutes - lastShorn > Animals.FLEECE_REGROW;
                o.add(Option.when(calm && grown && (a.has(Items.SHEARS, 1) || a.main() == Items.SHEARS), "shear", "Shear", 8, !calm ? "It's too agitated." : !grown ? "It was shorn not long ago." : "You need shears.", r));
            }
            o.add(Option.when(food != null && calm, "feed", "Feed" + (food == null ? "" : " (" + food.name.toLowerCase() + ")"), 7, food == null ? "You have nothing it would eat." : "It's too agitated.", r));
            // (0.3.4) told what to do: come along, stay here, or be let go about its own business (still tame, still yours)
            if (answersTo(a) && !baby && rider == 0) {
                float far = Math.max(r, 12);
                if (command != Command.FOLLOW || commandBy != a.id) o.add(Option.on("cmd_follow", "Follow", 6, far));
                if (command != Command.STAY || commandBy != a.id) o.add(Option.on("cmd_stay", "Stay", 5, far));
                if (command != Command.NONE) o.add(Option.on("cmd_dismiss", "Dismiss", 4, far));
            }
            // (0.3.0) a horse or a Pegasus: ridden, saddled, its saddlebags opened
            if (rpg.mount.Riding.mountable(this)) {
                boolean mine = rpg.mount.Riding.tamedFor(this, a), theirs = owned() && owner != a.id && !mine;
                boolean hasSaddle = a.has(Items.SADDLE, 1);
                // (0.3.1) the saddle goes on first when one is carried - an untamed horse too, if it stands calm
                // (0.3.2, gamefixes) however agitated it is: an agitated one is caught by the bridle and held while it is
                // done, which takes longer the worse it is
                if (!saddled) o.add(Option.when(hasSaddle && rider == 0 && !theirs, "saddle", "Saddle" + (mine ? "" : calm ? " (gentles the taming)" : " (agitated: hold it steady)"), hasSaddle ? 10 : 4,
                        theirs ? "It isn't yours to saddle." : !hasSaddle ? "You have no saddle." : "Someone is riding it.", r));
                o.add(Option.when(rider == 0 && (calm || mine), "mount", "Mount" + (mine ? "" : theirs ? " (not yours)" : saddled ? " (saddled, untamed)" : " (untamed)"), 9,
                        rider != 0 ? "Someone is riding it." : "It's too agitated.", 2.4f));
                if (saddled) {
                    boolean saddleMine = saddledBy == a.id || saddledBy == 0 && mine;
                    o.add(Option.when((mine || saddleMine) && rider == 0 && calm, "unsaddle", "Remove saddle", 3, "It isn't yours to unsaddle.", r));
                    o.add(Option.when((mine || saddleMine) && rider == 0, "bags", "Open saddlebags", 5, "Those aren't your saddlebags.", r));
                }
            }
        }
        return o;
    }

    @Override public Result perform(Rpg g, Actor a, String action, long req) {
        if (action.equals("carry")) return g.carrying.begin(a, this);
        switch (action) {
            case "mount" -> { return g.riding.mount(a, this); }
            case "saddle" -> {
                boolean mine = rpg.mount.Riding.tamedFor(this, a);
                if (saddled || rider != 0) return Result.no("You can't saddle it now.");
                if (owned() && owner != a.id && !mine) return Result.no("It isn't yours to saddle.");
                if (!a.has(Items.SADDLE, 1)) return Result.no("You have no saddle.");
                if (Math.hypot(a.x - x, a.z - z) > g.cfg.interactRange + radius) return Result.no("You're too far away.");
                // a tame one stands for it at once; an untamed one is eased into it over a few seconds, held calm; an
                // agitated one is caught by the bridle first and held while it fights it - the longer the worse it is
                float agitation = agitation();
                float time = (mine ? 1.2f : 2.6f) + 4.5f * agitation;
                heldBy = a.id; heldUntil = g.time + time + 0.3;
                goal = Goal.ALERT; nextThink = g.time;
                if (agitation > 0.2f && a == g.player) g.chat.say("You catch the " + kind().toLowerCase() + " by the bridle and hold it steady.");
                g.interact.begin(new rpg.interact.Work(a, this, "saddle", "Saddling", time, false, g.time, () -> saddleNow(g, a, req)));
                return Result.started();
            }
            case "unsaddle" -> {
                boolean saddleMine = saddledBy == a.id || saddledBy == 0 && rpg.mount.Riding.tamedFor(this, a);
                if (!saddled || !(saddleMine || rpg.mount.Riding.tamedFor(this, a))) return Result.no("You can't unsaddle it.");
                if (!bags.isEmpty()) return Result.no("Empty the saddlebags first.");
                if (!a.store(new rpg.item.ItemStack(Items.SADDLE, 1, a.id, false, a.id))) return Result.no("You have no room for the saddle.");
                saddled = false; saddledBy = 0;
                g.state.commit(req, "MountUnsaddled", a.id, id, DevMap.jurisdiction(x, z), 0, kind());
                return Result.ok();
            }
            case "bags" -> {
                if (!saddled || !(saddledBy == a.id || rpg.mount.Riding.tamedFor(this, a))) return Result.no("Those aren't your saddlebags.");
                if (a == g.player && g.ui != null) g.ui.openContainer(new rpg.objects.Saddlebags(id + 900_000_000L, kind() + "'s saddlebags", x, y + 1, z, a.id, bags));
                return Result.ok();
            }
            default -> { }
        }
        if (carriedBy != 0) return Result.no("Put the body down first.");
        return g.animals.perform(this, a, action, req);
    }


    /** How worked up it is, 0 (calm) .. 1 (bolting or fighting): its fear and what it is doing about it. */
    public float agitation() {
        float g0 = goal == Goal.FLEE || goal == Goal.DEFEND ? 0.55f : goal == Goal.WARN ? 0.4f : goal == Goal.ALERT ? 0.15f : 0;
        return Math.min(1, Math.max(fear, 0) * 0.6f + g0);
    }

    /** The saddle buckled on (after the work): the rider's saddle, its bags with it; an untamed horse is gentled by it. */
    private Result saddleNow(Rpg g, Actor a, long req) {
        heldBy = 0; heldUntil = -1;
        if (saddled || rider != 0 || !alive) return Result.no("You can't saddle it now.");
        if (a.takeItems(Items.SADDLE, 1).isEmpty()) return Result.no("You have no saddle.");
        saddled = true; saddledBy = a.id;
        boolean tamed = rpg.mount.Riding.tamedFor(this, a);
        if (!tamed) { trust.merge(a.id, 0.1f, (p, q) -> Math.min(1, p + q)); fear = Math.max(0, fear - 0.3f); }
        g.state.commit(req, "MountSaddled", a.id, id, DevMap.jurisdiction(x, z), 0, kind() + (tamed ? "" : " (untamed)"));
        if (a == g.player) g.chat.say(tamed ? "You saddle the " + kind().toLowerCase() + "."
                : "You ease the saddle onto the " + kind().toLowerCase() + ". It stamps, but lets it stay. It will be far easier to break in now.");
        return Result.ok();
    }

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
        // (0.3.4) a deer gives venison and a duck its own meat; a grown buck his antlers
        rpg.item.ItemDef cut = species == Species.DEER ? Items.RAW_VENISON : species == Species.DUCK ? Items.RAW_DUCK : Items.RAW_MEAT;
        if (meat > 0) l.add(new rpg.item.ItemStack(cut, meat));
        if (species == Species.DEER && !female && !baby) l.add(new rpg.item.ItemStack(Items.ANTLER, 2));
        if (hide > 0) l.add(new rpg.item.ItemStack(Items.RAWHIDE, hide));
        l.add(new rpg.item.ItemStack(species.bigBones && !baby ? Items.BIG_BONES : Items.BONES, 1));
        if (species.feathers > 0) l.add(new rpg.item.ItemStack(Items.FEATHER, Math.max(1, Math.round(species.feathers * k))));
        return l;
    }
}
