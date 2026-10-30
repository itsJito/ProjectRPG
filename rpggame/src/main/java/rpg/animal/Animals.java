package rpg.animal;

import astratto.bridge.CommandBuffer;
import astratto.bridge.Snapshots;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import rpg.Ids;
import rpg.Rpg;
import rpg.actor.Actor;
import rpg.animal.Animal.Goal;
import rpg.interact.Result;
import rpg.interact.Work;
import rpg.item.ItemDef;
import rpg.item.ItemStack;
import rpg.item.Items;
import rpg.objects.Container;
import rpg.skill.Skill;
import rpg.world.DevMap;
import static astratto.bridge.AstrattoNative.*;

/**
 * The animal ecosystem (GDD 3.9): every animal on the map, the habitats they belong to and their population ledgers,
 * the farm's fixtures, and the life cycle - pregnancy and birth, clutches and hatching, growing up, death, carcasses
 * that are butchered once or eaten by scavengers and then rot away. Animals near the player are simulated in full
 * (bodies, agents, senses, the shared mind); wild ones far from anyone are held as exact cohort counts and come back
 * where no one is looking, never in front of the player and never one more than the ledger holds.
 */
public final class Animals {
    private final Rpg g;
    final AnimalMind mind;
    private final List<Animal> animals = new ArrayList<>();
    public final List<Habitat> habitats = new ArrayList<>();
    public Habitat wild, farm;
    public final List<Nest> nests = new ArrayList<>();
    public Trough trough;
    /** Engine policies per species, adult [0] and baby [1]: the same actions at the species' own speeds. */
    private final Map<Species, long[]> policies = new EnumMap<>(Species.class);
    private final Random rnd = new Random(31);
    /** Counters for the inspector and tests. */
    public long births, hatchings, maturations, deaths, kills, materialized, dematerialized, butchered, eggsLaid, thinks;
    private double lifeAt, tierAt;

    public Animals(Rpg g) { this.g = g; this.mind = new AnimalMind(g, this); }

    // ------------------------------------------------------------------ the farm's fixtures, as the animals see them
    /** A trough: where owned stock eats the feed people put in it. */
    public static final class Trough {
        public final Container box;
        /** Rations left from the sack already opened (a sack feeds several meals). */
        public int portions;
        public static final int PER_SACK = 6;
        Trough(Container box) { this.box = box; }
        public int feed() { return box.contents.count(Items.ANIMAL_FEED); }
        /** Each animal its own place along it (they line up, not stack). */
        float spotX(Animal a) { return box.x + ((a.id % 5) - 2) * 0.55f; }
        float spotZ(Animal a) { return box.z + (a.id % 2 == 0 ? -1 : 1) * (0.55f + a.radius); }
        float yaw(Animal a) { return a.id % 2 == 0 ? (float) Math.PI : 0; }
    }

    /** A clutch being sat (a bird's nest): its eggs, when they hatch, its mother and father. */
    public static final class Nest {
        public final long id;
        public final Species species;
        public final float x, z;
        public final long mother, father, owner;
        public int eggs;
        public final double hatchAt;
        public boolean done;
        Nest(long id, Species s, float x, float z, long mother, long father, long owner, int eggs, double hatchAt) {
            this.id = id; this.species = s; this.x = x; this.z = z; this.mother = mother; this.father = father; this.owner = owner; this.eggs = eggs; this.hatchAt = hatchAt;
        }
    }

    // ------------------------------------------------------------------ setting up
    public void init() {
        CommandBuffer c = g.game.commands();
        for (Species s : Species.values()) {
            long[] p = new long[2];
            for (int stage = 0; stage < 2; stage++) {
                float k = stage == 0 ? 1 : 0.6f;
                p[stage] = g.game.reserve(KIND_POLICY);
                List<CommandBuffer.Action> acts = new ArrayList<>();
                acts.add(new CommandBuffer.Action(AnimalMind.A_IDLE, ACT_IDLE).base(1).consider(IN_FACT0 + AnimalMind.A_IDLE, CURVE_LINEAR, 0, 1, 1));
                acts.add(new CommandBuffer.Action(AnimalMind.A_WANDER, ACT_WANDER).base(1).range(5).speed(s.walk * 0.8f * k).consider(IN_FACT0 + AnimalMind.A_WANDER, CURVE_LINEAR, 0, 1, 1));
                acts.add(new CommandBuffer.Action(AnimalMind.A_GOTO, ACT_MOVE_TO_GOAL).base(1).speed(s.walk * k).consider(IN_FACT0 + AnimalMind.A_GOTO, CURVE_LINEAR, 0, 1, 1));
                acts.add(new CommandBuffer.Action(AnimalMind.A_RUN, ACT_MOVE_TO_GOAL).base(1).speed(s.run * k).consider(IN_FACT0 + AnimalMind.A_RUN, CURVE_LINEAR, 0, 1, 1));
                acts.add(new CommandBuffer.Action(AnimalMind.A_APPROACH, ACT_APPROACH).base(1).range(s.w * 0.5f + s.reach).speed(s.run * k * (s.prey.isEmpty() ? 1 : 1.18f)).consider(IN_FACT0 + AnimalMind.A_APPROACH, CURVE_LINEAR, 0, 1, 1));
                acts.add(new CommandBuffer.Action(AnimalMind.A_FLEE, ACT_FLEE).base(1).range(18).speed(s.run * k).consider(IN_FACT0 + AnimalMind.A_FLEE, CURVE_LINEAR, 0, 1, 1));
                acts.add(new CommandBuffer.Action(AnimalMind.A_HOLD, ACT_HOLD).base(1).consider(IN_FACT0 + AnimalMind.A_HOLD, CURVE_LINEAR, 0, 1, 1));
                c.policyCreate(p[stage], acts, 0.1f, 0.1f);
            }
            policies.put(s, p);
        }
        habitats();
        trough = g.objects.trough == null ? null : new Trough(g.objects.trough);
        roster();
    }

    private void habitats() {
        float[][] wildWater = edge(DevMap.WILD_POND_X0, DevMap.WILD_POND_Z0, DevMap.WILD_POND_X1, DevMap.WILD_POND_Z1);
        float[][] wildRest = {{(DevMap.DEN_X0 + DevMap.DEN_X1) / 2f + 1, (DevMap.DEN_Z0 + DevMap.DEN_Z1) / 2f + 0.5f}, {12.5f, 20.5f}, {33.5f, 28f}, {20.5f, 32f}, {36.5f, 16f}};
        wild = new Habitat("wild_wood", "the wild wood", DevMap.WILD_X0 + 1, DevMap.WILD_Z0 + 1, DevMap.WILD_X1 - 1, DevMap.WILD_Z1 - 1, 0, wildWater, wildRest)
                .cap(Species.RABBIT, 12).cap(Species.FOX, 3).cap(Species.COYOTE, 4).cap(Species.WOLF, 6).cap(Species.MOUNTAIN_LION, 2)
                .cap(Species.BROWN_BEAR, 3).cap(Species.DUCK, 7).cap(Species.DOG, 3).cap(Species.CAT, 2);
        float[][] farmWater = edge(DevMap.DUCK_POND_X0, DevMap.DUCK_POND_Z0, DevMap.DUCK_POND_X1, DevMap.DUCK_POND_Z1);
        float[][] farmRest = {{113.5f, 119.5f}, {116.5f, 119.5f}, {119.5f, 119f}, {112.5f, 121f}};
        farm = new Habitat("farm", "Mara's farm", DevMap.FARM_X0 + 1, DevMap.FARM_Z0 + 1, DevMap.FARM_X1 - 1, DevMap.FARM_Z1 - 1, Ids.FARMER, farmWater, farmRest)
                .cap(Species.CHICKEN, 12).cap(Species.DUCK, 8).cap(Species.COW, 6).cap(Species.SHEEP, 9).cap(Species.PIG, 6).cap(Species.HORSE, 4)
                .cap(Species.DOG, 2).cap(Species.CAT, 3);
        habitats.add(wild); habitats.add(farm);
    }

    /** Drinking spots round a pond: on the bank, facing the water (x, z, yaw). */
    private static float[][] edge(int x0, int z0, int x1, int z1) {
        List<float[]> l = new ArrayList<>();
        for (int x = x0; x <= x1; x += 2) { l.add(new float[]{x + 0.5f, z0 - 1.0f, (float) Math.PI}); l.add(new float[]{x + 0.5f, z1 + 2.0f, 0}); }
        for (int z = z0; z <= z1; z += 2) { l.add(new float[]{x0 - 1.0f, z + 0.5f, (float) (-Math.PI / 2)}); l.add(new float[]{x1 + 2.0f, z + 0.5f, (float) (Math.PI / 2)}); }
        return l.toArray(new float[0][]);
    }

    /** The starting animals: the farm's stock (all Mara's) and the wood's wildlife, each with its family and group. */
    private void roster() {
        long herd = 1;
        // the farm
        herd = group(farm, Species.CHICKEN, herd, new float[]{108, 112}, 4, 1, 2);
        herd = group(farm, Species.DUCK, herd, new float[]{97, 113}, 2, 1, 2);
        herd = group(farm, Species.COW, herd, new float[]{94, 108}, 2, 1, 1);
        herd = group(farm, Species.SHEEP, herd, new float[]{112, 108}, 3, 1, 1);
        herd = group(farm, Species.PIG, herd, new float[]{104, 118}, 2, 1, 1);
        herd = group(farm, Species.HORSE, herd, new float[]{117, 106}, 1, 1, 1);
        Animal dog = spawnNew(Species.DOG, false, true, 100.5f, 106.5f, farm, herd, 0);
        dog.trust.put(Ids.FARMER, 1f);
        Animal pup = spawnNew(Species.DOG, true, false, 101.5f, 107.5f, farm, herd++, dog.id);
        pup.trust.put(Ids.FARMER, 1f);
        herd = group(farm, Species.CAT, herd, new float[]{115, 118}, 1, 0, 1);
        // the wood
        herd = group(wild, Species.RABBIT, herd, new float[]{14, 22}, 2, 2, 2);
        herd = group(wild, Species.RABBIT, herd, new float[]{34, 30}, 1, 1, 0);
        herd = group(wild, Species.FOX, herd, new float[]{36, 10}, 1, 1, 1);
        herd = group(wild, Species.COYOTE, herd, new float[]{10, 34}, 1, 1, 1);
        herd = group(wild, Species.WOLF, herd, new float[]{38, 34}, 2, 1, 1);
        herd = group(wild, Species.MOUNTAIN_LION, herd, new float[]{40, 22}, 1, 0, 1);
        herd = group(wild, Species.BROWN_BEAR, herd, new float[]{9, 10}, 1, 0, 1);
        herd = group(wild, Species.DUCK, herd, new float[]{25, 24}, 1, 1, 1);
        Animal feral = spawnNew(Species.DOG, false, true, 30.5f, 14.5f, wild, herd++, 0);
        feral.range = 14;
        // a town cat, nobody's
        spawnNew(Species.CAT, false, true, 99.5f, 78.5f, null, herd++, 0).range = 9;
        for (Habitat h : habitats) h.count("starting", count(h));
    }

    /** A group: so many adult females and males (the first female the mother of the young), and young. Returns the next group id. */
    private long group(Habitat h, Species s, long id, float[] at, int females, int males, int young) {
        Animal mother = null;
        for (int i = 0; i < females + males; i++) {
            boolean female = i < females;
            Animal a = spawnNew(s, false, female, at[0] + (i % 3) * 1.4f - 1.4f, at[1] + (i / 3) * 1.5f - 0.7f, h, id, 0);
            if (female && mother == null) mother = a;
        }
        for (int i = 0; i < young; i++) {
            Animal b = spawnNew(s, true, i % 2 == 0, at[0] + 1.2f + i * 0.7f, at[1] + 1.4f, h, id, mother == null ? 0 : mother.id);
            b.bornAt = g.state.minutes - s.mature * (0.2f + 0.15f * i);
            b.matureAt = b.bornAt + s.mature;
        }
        return id + 1;
    }

    /** A new animal in the world: an id, its home, its owner (the habitat's), a body and an agent. */
    /** An animal brought in from outside the world's own births (the console, a scenario): on the habitat's ledger as released. */
    public Animal release(Species s, boolean baby, boolean female, float x, float z) {
        Habitat h = habitatAt(x, z);
        Animal a = spawnNew(s, baby, female, x, z, h, 0, 0);
        if (h != null) h.count("released", 1);
        g.state.commit("AnimalReleased", 0, a.id, DevMap.jurisdiction(x, z), 0, a.kind());
        return a;
    }

    public Animal spawnNew(Species s, boolean baby, boolean female, float x, float z, Habitat h, long group, long mother) {
        Animal a = new Animal(g.state.newEntityId(), s, baby, female, x, z);
        a.group = group; a.mother = mother;
        a.owner = h == null ? 0 : h.owner;
        a.home = h;
        if (h != null) { a.homeX = h.cx(); a.homeZ = h.cz(); a.range = Math.max(6, Math.min(h.x1 - h.x0, h.z1 - h.z0) * 0.5f); }
        if (h == wild && !s.domestic) { a.homeX = x; a.homeZ = z; a.range = s.predator() || s == Species.BROWN_BEAR ? 16 : 11; }
        a.bornAt = g.state.minutes - (baby ? s.mature * 0.3f : s.mature * 2);
        a.matureAt = baby ? a.bornAt + s.mature : a.bornAt;
        a.variant = (int) (a.id * 2654435761L >>> 7) & 7;
        a.careAgain = g.time + 15 + a.id % 30;
        a.hunger = 0.1f + (a.id % 5) * 0.06f; a.thirst = (a.id % 7) * 0.05f; a.fatigue = (a.id % 3) * 0.1f;
        a.restOx = ((a.id % 5) - 2) * 0.6f; a.restOz = ((a.id / 5 % 3) - 1) * 0.7f;
        a.yaw = (float) ((a.id * 1.7) % (2 * Math.PI));
        if (s.domestic && a.owned()) a.trust.put(a.owner, 1f);
        a.nextThink = g.time + Math.floorMod(a.id * 73, 200) / 1000.0;
        nextSense.put(a.id, g.time + Math.floorMod(a.id * 97, 200) / 1000.0);
        animals.add(a);
        spawnBody(a);
        return a;
    }

    /** Makes the animal's mover and agent (its size and its species' gaits). */
    public void spawnBody(Animal a) {
        CommandBuffer c = g.game.commands();
        a.body = g.game.reserve(KIND_MOVER);
        a.agent = g.game.reserve(KIND_AGENT);
        a.fit();
        c.moverCreate(a.body, a.x, DevMap.FLOOR + 0.05f, a.z, a.radius, a.height, Math.min(0.6f, a.height * 0.45f), MOVER_AGENT, a.height);
        a.slow = a.baby;
        c.agentCreate(a.agent, policies.get(a.species)[a.baby ? 1 : 0], a.body, 0, 0, 0, a.eyeY() - a.y, 18, 0.25f, 20, a.team, astratto.ai.AgentDomain.ANIMAL.flags, (int) a.id * 7919);
        c.moverInput(a.body, 0, 0, 0, 0, a.species.plan == Species.Plan.BIRD ? 18 : 10, 0);
        java.util.Arrays.fill(a.facts, -1);
        a.targetBody = -1;
        a.loaded = true;
        desire(a, AnimalMind.A_HOLD);
        if(g.chemistry!=null)g.chemistry.register(a);
    }

    /** An animal for a picture: placed, facing, its mind off (the gallery's catalog and gait films). */
    public Animal display(Species s, boolean baby, boolean female, int variant, float x, float z, float yaw) {
        Animal a = spawnNew(s, baby, female, x, z, null, 0, 0);
        a.variant = variant; a.puppet = true; a.yaw = yaw; a.hunger = 0; a.thirst = 0; a.fatigue = 0;
        return a;
    }

    /** Walks (or runs) a held animal toward a point. */
    public void puppetTo(Animal a, float x, float z, boolean run) { goTo(a, x, z, run, 0.3f); }
    public void puppetHold(Animal a) { desire(a, AnimalMind.A_HOLD); }

    /** Takes an animal out of the world altogether (a gallery puppet). */
    public void remove(Animal a) { despawnBody(a); dropRagdoll(a); animals.remove(a); }

    // ------------------------------------------------------------------ bodies in physics' hands (AnimalRagdoll)
    /** At most this many animal bodies in physics at once (the oldest settled carcass goes back to its drawn pose). */
    static final int MAX_RAGDOLLS = 10;
    public int ragdolls;

    /** A blow landed: remembered for the fall, and a heavy one on a big beast knocks it off its feet for a moment. */
    public void struck(Animal a, float dx, float dz, float impact, boolean stagger) {
        a.blowX = dx; a.blowZ = dz; a.blowImpact = impact;
        if (!stagger || !a.alive || !a.loaded || a.ragdoll != null || a.species.w < 0.55f || a.baby || a.distTo(g.player.x, g.player.z) > 60) return;
        float j = Math.min(220, impact * 2.4f) * a.species.w;
        makeRagdoll(a, dx * j, j * 0.25f, dz * j, true);
        if (a.ragdoll != null) { a.cs.cancel(); g.state.commit("AnimalKnockedDown", 0, a.id, DevMap.jurisdiction(a.x, a.z), 0, a.kind()); }
    }

    public void makeRagdoll(Animal a, float jx, float jy, float jz, boolean knockdown) {
        int live = 0;
        Animal oldest = null;
        for (Animal o : animals) if (o.ragdoll != null) { live++; if (!o.ragdoll.knockdown && o.carriedBy==0 && (oldest == null || o.ragdoll.since < oldest.ragdoll.since)) oldest = o; }
        if (live >= MAX_RAGDOLLS) { if(oldest==null)return;dropRagdoll(oldest); }
        a.ragdoll = AnimalRagdoll.create(g, a, jx, jy, jz, knockdown);
        if (a.ragdoll != null) ragdolls++;
    }

    void dropRagdoll(Animal a) {
        if(a.ragdoll==null)return;
        if(!a.alive && !a.butchered)captureRest(a);else a.restPose.clear();
        a.ragdoll.release(g);a.ragdoll=null;
    }
    private void captureRest(Animal a) {
        if(a.ragdoll==null)return;
        for(var piece:a.ragdoll.pieces) { var body=g.lastRigids.body(piece.body());if(body!=null)a.restPose.put(piece.part(),body); }
    }
    /** Re-enter physics only when needed; visible settled bodies remain interactable after load or budget eviction. */
    public boolean ensurePhysical(Animal a) {
        if(a.alive || a.butchered || !a.loaded)return false;
        if(a.ragdoll!=null)return true;
        makeRagdoll(a,0,0,0,false);
        if(a.ragdoll==null)return false;
        for(var piece:a.ragdoll.pieces) {
            var rest=a.restPose.get(piece.part());if(rest==null)continue;
            g.game.commands().rigidPlace(piece.body(),rest.x,rest.y,rest.z,rest.qw,rest.qx,rest.qy,rest.qz,0,0,0,0,0,0);
        }
        return true;
    }

    /**
     * Each tick for a body in physics' hands: the animal is where its trunk is; a knocked-down one is handed back to its
     * animation where it lies (lying, so it gets up through its own motion) once it has come to rest or a moment is up.
     */
    private void ragdollTick(Animal a) {
        var r = a.ragdoll;
        float[] t = r.trunkAt(g);
        if (t == null) return;
        a.x = t[0]; a.z = t[2];
        if (!r.knockdown) {
            if (a.carriedBy == 0 && g.time-r.since > 1.5 && r.asleep(g)) dropRagdoll(a);
            return;
        }
        if (g.time < a.shockUntil || g.time - r.since < 1.2 || (!r.asleep(g) && g.time - r.since < 2.6)) return;
        // facing the way the trunk faces (its forward is -z in its own frame)
        float qw = t[3], qx = t[4], qy = t[5], qz = t[6];
        float fx = -(2 * (qx * qz + qw * qy)), fz = -(1 - 2 * (qx * qx + qy * qy));
        a.yaw = (float) Math.atan2(-fx, -fz);
        a.lieT = 1;
        dropRagdoll(a);
        if (a.body != 0) g.game.commands().moverPlace(a.body, a.x, DevMap.FLOOR, a.z, 0, 0, 0);
        a.nextThink = 0;
    }

    public void despawnBody(Animal a) {
        if (a.agent != 0) g.game.commands().release(a.agent);
        if (a.body != 0) g.game.commands().release(a.body);
        a.agent = 0; a.body = 0;
    }

    // ------------------------------------------------------------------ queries
    public List<Animal> all() { return animals; }
    public List<Animal> live() { List<Animal> l = new ArrayList<>(); for (Animal a : animals) if (a.alive && a.loaded) l.add(a); return l; }
    public Animal byId(long id) { for (Animal a : animals) if (a.id == id) return a; return null; }
    public int count(Habitat h) { int n = 0; for (Animal a : animals) if (a.alive && a.home == h) n++; return n + h.cohortTotal(); }
    public int count(Habitat h, Species s) { int n = 0; for (Animal a : animals) if (a.alive && a.home == h && a.species == s) n++; for (Nest ne : nests) if (!ne.done && ne.species == s && h.contains(ne.x, ne.z)) n += 0; return n + h.cohortCount(s); }
    public int living() { int n = 0; for (Animal a : animals) if (a.alive) n++; for (Habitat h : habitats) n += h.cohortTotal(); return n; }

    /** How sharp its eyes are now: the species' own, dimmed when it lies resting. */
    float acuity(Animal a) { return a.species.acuity * (a.atRest ? 0.35f : 1f) * (a.baby ? 0.8f : 1f); }

    boolean youngNear(Animal a, float r) {
        for (Animal b : animals) if (b.alive && b.baby && b.mother == a.id && b.distTo(a) < r) return true;
        return false;
    }

    /** A young one of hers that has strayed: she goes to it. */
    Animal strayYoung(Animal a, float r) {
        if (a.baby || !a.female) return null;
        for (Animal b : animals) if (b.alive && b.loaded && b.baby && b.mother == a.id && b.distTo(a) > r) return b;
        return null;
    }

    /** Whether it would likely win: its own fighting weight against a person's or another animal's. */
    boolean couldWin(Animal a, Actor t) {
        float mine = a.hp * (a.skills.level(Skill.STRENGTH) + 10), theirs = t.hp * (t.skills.level(Skill.STRENGTH) + 10);
        int pack = 0;
        if (a.species.group == Species.Group.PACK) for (Animal o : animals) if (o.alive && o.loaded && o.group == a.group && !o.baby && o.distTo(a) < 12) pack++;
        return mine * Math.max(1, pack) > theirs * 0.6f;
    }

    /** The group's leader to keep with: its oldest adult (not itself). */
    Animal groupLead(Animal a) {
        if (a.species.group == Species.Group.SOLO) return null;
        Animal lead = null;
        for (Animal o : animals) if (o.alive && o.loaded && o != a && o.group == a.group && !o.baby && (lead == null || o.id < lead.id)) lead = o;
        if (lead != null && lead.id > a.id && !a.baby) return null;   // the lead itself, or an older adult than the rest: it leads
        return lead;
    }

    /** A carcass worth eating within reach (one not picked clean). */
    Actor carcassNear(Animal a, float radius) {
        Actor best=null; float distance=radius;
        for(Animal corpse:animals) if(corpse.carcass() && !corpse.butchered && corpse.loaded && corpse.carriedBy==0 && corpse.species!=a.species && corpse.distTo(a)<distance) { best=corpse; distance=corpse.distTo(a); }
        for(rpg.ai.Npc corpse:g.npcs) if(!corpse.alive && corpse.corpse!=0 && corpse.ragdoll!=0 && corpse.carriedBy==0 && corpse.summonOwner==0 && corpse.distTo(a)<distance
                && g.objects.get(corpse.corpse) instanceof rpg.objects.Corpse object && !object.removed) { best=corpse; distance=corpse.distTo(a); }
        if(best!=null && !g.vox.sight(a.x,a.eyeY(),a.z,best.x,best.y+0.25f,best.z))return null;
        return best;
    }

    /** Prey it can see (or smell close by), alive, in or near its range; a pack does not hunt alone. */
    Animal preyFor(Animal a) {
        Animal best = null; float bd = Float.MAX_VALUE;
        for (Animal p : animals) {
            if (!p.alive || !p.loaded || !a.species.prey.contains(p.species)) continue;
            float d = a.distTo(p);
            boolean chasing = a.goal == Goal.HUNT && a.other == p.id;   // its chosen quarry is followed out of its range
            if (chasing ? d > 30 : d > 22 || Math.hypot(p.x - a.homeX, p.z - a.homeZ) > a.range * 1.5f) continue;
            // a large prey animal is only taken by a pack, and the young first
            if ((p.species == Species.COW || p.species == Species.HORSE) && !p.baby) continue;
            if (!chasing && d > 6 && g.ai.stealth.exposure(a, acuity(a), p) <= 0.02f) continue;
            float score = d * (p.baby ? 0.6f : 1);
            if (score < bd) { bd = score; best = p; }
        }
        return best;
    }

    /** A mate: an adult of the other sex, same species, near enough, when everything a birth needs is in place. */
    Animal mateFor(Animal a) {
        if (!canBreed(a)) return null;
        for (Animal m : animals) if (m.alive && m.loaded && !m.female && !m.baby && m.species == a.species && m.home == a.home && m.distTo(a) < 24) return m;
        return null;
    }

    /** For the tests: the mate she would court now (null: none near). */
    public Animal mateForTest(Animal a) { return mateFor(a); }

    /** Whether a female may breed now (GDD 3.9): an adult, not carrying, rested since her last, fed and watered, calm, room in the habitat, and - for owned stock - fed by her keeper. */
    public String breedRefusal(Animal a) {
        if (!a.alive || a.baby || !a.female) return "not an adult female";
        if (a.dueAt > 0) return "already carrying";
        if (clutchOf(a) != null) return "sitting a clutch";
        if (g.state.minutes - a.lastBred < a.species.rest) return "too soon since the last";
        if (a.hunger > 0.6f || a.thirst > 0.7f) return "underfed";
        if (a.fear > 0.2f) return "too frightened";
        Habitat h = a.home;
        if (h == null) return "no home range";
        Integer cap = h.capacity.get(a.species);
        if (cap == null || count(h, a.species) + pending(h, a.species) >= cap) return "no room for more in " + h.name;
        if (a.owned() && (trough == null || trough.feed() <= 0 && trough.portions <= 0)) return "the keeper has no feed out";
        return null;
    }
    boolean canBreed(Animal a) { return breedRefusal(a) == null; }

    /** Young already on the way (pregnancies and clutches), so a habitat's room counts them. */
    private int pending(Habitat h, Species s) {
        int n = 0;
        for (Animal a : animals) if (a.alive && a.home == h && a.species == s && a.dueAt > 0) n += a.species.litterMax;
        for (Nest ne : nests) if (!ne.done && ne.species == s && h.contains(ne.x, ne.z)) n += ne.eggs;
        return n;
    }

    Nest clutchOf(Animal a) { for (Nest n : nests) if (!n.done && n.mother == a.id) return n; return null; }

    /** The trough an owned grazer eats from (with feed in it). */
    Trough troughFor(Animal a) {
        if (trough == null || !a.owned() || a.home != farm || trough.feed() <= 0 && trough.portions <= 0) return null;
        // grazers take the paddock's grass first, and the trough only when they are properly hungry
        boolean grazer = a.species.diet == Species.Diet.GRAZER;
        if (a.hunger < (grazer ? 0.6f : 0.45f)) return null;   // the paddock's grass (or what scratching turns up) first
        Species s = a.species;
        return s == Species.COW || s == Species.SHEEP || s == Species.PIG || s == Species.HORSE || s == Species.CHICKEN || s == Species.DUCK ? trough : null;
    }

    float[] waterFor(Animal a) {
        Habitat h = a.home != null ? a.home : habitatAt(a.x, a.z);
        if (h == null) return null;
        float[] best = null; float bd = Float.MAX_VALUE;
        for (float[] w : h.water) {
            float d = (float) Math.hypot(w[0] - a.x, w[1] - a.z) + ((a.id * 7 + (int) (w[0] * 3)) % 5);   // spread out along the bank
            if (d < bd) { bd = d; best = w; }
        }
        return best;
    }

    float[] restFor(Animal a) {
        Habitat h = a.home;
        if (h == null) return null;
        if (a.species == Species.BROWN_BEAR || a.species == Species.MOUNTAIN_LION) return h.rest[0];
        return Habitat.nearest(h.rest, a.homeX + (a.id % 3), a.homeZ);
    }

    /** Somewhere in its range to wander to: inside its habitat, on open ground. */
    float[] roamPoint(Animal a) {
        // a wader now and then makes for the shallows of its habitat's water instead (a duck for the middle of its pond)
        if (a.species.wades > 0 && !a.baby && rnd.nextFloat() < a.species.wades) { float[] w = shallows(a); if (w != null) return w; }
        for (int t = 0; t < 12; t++) {
            double ang = rnd.nextDouble() * Math.PI * 2;
            float r = a.range * (0.3f + 0.7f * rnd.nextFloat());
            float x = a.homeX + (float) Math.cos(ang) * r, z = a.homeZ + (float) Math.sin(ang) * r;
            if (open(a, x, z)) return new float[]{x, z};
        }
        return new float[]{a.homeX, a.homeZ};
    }

    /** A grazing step: a metre or three on, staying near its group and in its range. */
    float[] grazePoint(Animal a) {
        for (int t = 0; t < 10; t++) {
            double ang = rnd.nextDouble() * Math.PI * 2;
            float r = 1 + rnd.nextFloat() * 2.5f;
            float x = a.x + (float) Math.cos(ang) * r, z = a.z + (float) Math.sin(ang) * r;
            if (Math.hypot(x - a.homeX, z - a.homeZ) > a.range) { x = a.x + (a.homeX - a.x) * 0.3f; z = a.z + (a.homeZ - a.z) * 0.3f; }
            if (open(a, x, z)) return new float[]{x, z};
        }
        return new float[]{a.x, a.z};
    }

    /** A spot in shallow water within its range (water one block deep with air over it), or null. */
    float[] shallows(Animal a) {
        for (int t = 0; t < 16; t++) {
            double ang = rnd.nextDouble() * Math.PI * 2;
            float r = a.range * rnd.nextFloat();
            float x = a.homeX + (float) Math.cos(ang) * r, z = a.homeZ + (float) Math.sin(ang) * r;
            if (a.home != null && !a.home.contains(x, z)) continue;
            int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
            if (g.vox.block(bx, DevMap.FLOOR - 1, bz) == rpg.world.Blocks.WATER && g.vox.block(bx, DevMap.FLOOR, bz) == 0 && g.vox.solid(bx, DevMap.FLOOR - 2, bz)) return new float[]{x, z};
        }
        return null;
    }

    private boolean open(Animal a, float x, float z) {
        if (a.home != null && !a.home.contains(x, z)) return false;
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            if (g.vox.block(bx + dx, DevMap.FLOOR, bz + dz) != 0 || g.vox.block(bx + dx, DevMap.FLOOR - 1, bz + dz) == rpg.world.Blocks.WATER) return false;
        }
        return true;
    }

    public Habitat habitatAt(float x, float z) { for (Habitat h : habitats) if (h.contains(x, z)) return h; return null; }

    // ------------------------------------------------------------------ steering
    void desire(Animal a, int action) {
        if (a.agent == 0) return;
        for (int i = 0; i < AnimalMind.ACTIONS; i++) {
            float v = i == action ? 1f : i == AnimalMind.A_IDLE ? 0.08f : 0.02f;
            if (a.facts[i] != v) { a.facts[i] = v; g.game.commands().agentFact(a.agent, i, v); }
        }
        a.running = action == AnimalMind.A_RUN || action == AnimalMind.A_FLEE || action == AnimalMind.A_APPROACH;
    }

    void target(Animal a, long body) {
        if (a.agent == 0 || a.targetBody == body) return;
        a.targetBody = body;
        g.game.commands().agentTarget(a.agent, body, 0, 0, 0);
    }

    /** Heads somewhere (the engine searches the route); true once within reach of it. */
    boolean goTo(Animal a, float x, float z, boolean run, float within) {
        if (Math.hypot(a.x - x, a.z - z) <= within) { desire(a, AnimalMind.A_HOLD); return true; }
        if (!a.routed || Math.hypot(a.rx - x, a.rz - z) > 0.4) {
            a.rx = x; a.rz = z; a.routed = true;
            g.game.commands().agentGoal(a.agent, x, DevMap.FLOOR, z);
        }
        desire(a, run ? AnimalMind.A_RUN : AnimalMind.A_GOTO);
        return false;
    }

    // ------------------------------------------------------------------ the frame
    private final Map<Long, Double> nextSense = new java.util.HashMap<>();

    public void update(float dt) {
        float dm = dt * g.cfg.minutesPerSecond;
        for (Animal a : new ArrayList<>(animals)) {
            if (a.ragdoll != null) ragdollTick(a);
            if (!a.alive) { carcass(a); continue; }
            if (!a.loaded) continue;
            g.game.commands().agentRate(a.agent,a.cs.frozenT>0?0.001f:a.actionRate()*(a.cs.slowT>0?0.6f:1));
            if (a.ragdoll != null) { desire(a, AnimalMind.A_HOLD); continue; }   // off its feet: no mind until it is up
            if (a.trappedBy!=0 && g.objects.get(a.trappedBy) instanceof rpg.objects.Trap trap && !trap.removed && !trap.contents.isEmpty()) { desire(a,AnimalMind.A_HOLD); continue; }
            a.trappedBy=0;
            if (a.puppet) { face(a, dt); continue; }
            double due = nextSense.getOrDefault(a.id, 0.0);
            if (g.time >= due) { mind.sense(a, (float) Math.min(0.5, g.time - due + 0.2)); nextSense.put(a.id, g.time + 0.2); }
            if (g.time >= a.nextThink) { mind.think(a); thinks++; }
            if (a.goal != Goal.HUNT) a.sneaking = false;
            // spent: an exhausted animal can only manage its young's pace until it recovers
            // lamed (a leg taken off, Gore): no faster than a young one; with too few legs left, it cannot go anywhere
            float legs = g.gore != null ? g.gore.legs(a) : 1;
            boolean slow = a.baby || a.fatigue > 0.9f || legs < 0.6f;
            if (slow != a.slow && a.agent != 0) { a.slow = slow; g.game.commands().agentPolicy(a.agent, policies.get(a.species)[slow ? 1 : 0]); }
            if (legs < 0.2f) { desire(a, AnimalMind.A_HOLD); face(a, dt); continue; }
            if (!a.cs.busy() || a.cs.phase == Actor.Combat.Phase.RECOVER) mind.act(a, dt, dm);
            face(a, dt);
        }
        if (g.time >= lifeAt) { lifeAt = g.time + 1; life(); }
        if (g.time >= tierAt) { tierAt = g.time + 2; tiers(); }
    }

    /** Where the body faces: its quarry, its threat while it watches, the trough or bank it eats at, else the way it walks. */
    private void face(Animal a, float dt) {
        float speed = (float) Math.hypot(a.vx, a.vz);
        float want = a.yaw;
        if (a.faceThreat) want = (float) Math.atan2(-(a.threatX - a.x), -(a.threatZ - a.z));
        else if (!Float.isNaN(a.wantYaw)) want = a.wantYaw;
        else if (speed > 0.3f) want = (float) Math.atan2(-a.vx, -a.vz);
        float rate = a.goal == Goal.FLEE || a.goal == Goal.HUNT || a.goal == Goal.DEFEND ? 7f : 3.2f;
        a.turnToward(want, rate, dt);
    }

    public void afterFrame(Snapshots.Bodies bodies, Snapshots.Agents agents) {
        for (Animal a : animals) {
            if (!a.alive || !a.loaded) continue;
            Snapshots.Mover m = bodies.mover(a.body);
            if (m != null) { a.x = m.x; a.y = m.y; a.z = m.z; a.vx = m.vx; a.vz = m.vz; a.grounded = m.grounded; }
        }
    }

    // ------------------------------------------------------------------ life: births, clutches, growing up, the ordinary eggs
    private void life() {
        double now = g.state.minutes;
        for (Animal a : new ArrayList<>(animals)) {
            if (!a.alive) continue;
            if (a.dueAt > 0 && now >= a.dueAt) birth(a);
            if (a.baby && now >= a.matureAt) mature(a);
            if (a.owned() && a.female && !a.baby && (a.species == Species.CHICKEN || a.species == Species.DUCK) && now - a.lastEgg > 720 && a.hunger < 0.6f) layEgg(a);
        }
        for (Nest n : nests) if (!n.done && now >= n.hatchAt) hatch(n);
        nests.removeIf(n -> n.done);
    }

    /** She gives birth (one event, every newborn a new individual beside her, hers and her owner's). */
    private void birth(Animal mother) {
        Species s = mother.species;
        int n = s.litterMin + (int) ((mother.id * 31 + (long) mother.dueAt) % (s.litterMax - s.litterMin + 1));
        mother.dueAt = -1;
        mother.lastBred = g.state.minutes;
        var ev = g.state.commit("AnimalBorn", mother.id, mother.father, DevMap.jurisdiction(mother.x, mother.z), 0, n + " " + s.label(true).toLowerCase() + (n == 1 ? "" : "s") + " to " + mother.kind().toLowerCase() + " " + mother.id);
        for (int i = 0; i < n; i++) {
            double ang = i * 2.1 + mother.yaw;
            Animal b = spawnNew(s, true, (mother.id + i) % 2 == 0, mother.x + (float) Math.cos(ang) * 0.6f, mother.z + (float) Math.sin(ang) * 0.6f, mother.home, mother.group, mother.id);
            b.owner = mother.owner;
            b.bornAt = g.state.minutes; b.matureAt = b.bornAt + s.mature;
            b.homeX = mother.homeX; b.homeZ = mother.homeZ; b.range = mother.range;
            b.lastEvent = "born (event " + ev.id() + ")";
        }
        births += n;
        if (mother.home != null) mother.home.count("born", n);
        mother.lastEvent = "gave birth to " + n;
    }

    private void hatch(Nest n) {
        n.done = true;
        Animal mother = byId(n.mother);
        Habitat h = mother != null ? mother.home : habitatAt(n.x, n.z);
        if (n.eggs <= 0) return;
        var ev = g.state.commit("AnimalHatched", n.mother, n.father, DevMap.jurisdiction(n.x, n.z), 0, n.eggs + " " + n.species.label(true).toLowerCase() + "s");
        for (int i = 0; i < n.eggs; i++) {
            Animal b = spawnNew(n.species, true, i % 2 == 0, n.x + (i % 3 - 1) * 0.35f, n.z + 0.4f + i / 3 * 0.3f, h, mother != null ? mother.group : 0, n.mother);
            b.owner = n.owner;
            b.bornAt = g.state.minutes; b.matureAt = b.bornAt + n.species.mature;
            if (mother != null) { b.homeX = mother.homeX; b.homeZ = mother.homeZ; b.range = mother.range; }
            b.lastEvent = "hatched (event " + ev.id() + ")";
        }
        hatchings += n.eggs;
        if (h != null) h.count("hatched", n.eggs);
    }

    /** A baby grows up: one less baby, one more adult (never a new animal), its body made to its full size. */
    private void mature(Animal a) {
        a.baby = false;
        a.name = a.species.label;
        maturations++;
        if (a.home != null) a.home.count("matured", 1);
        g.state.commit("AnimalMatured", a.id, a.mother, DevMap.jurisdiction(a.x, a.z), 0, a.species.label);
        a.restore();
        if (a.loaded) { despawnBody(a); spawnBody(a); }
        a.lastEvent = "grew up";
    }

    /** A pair mates: a pregnancy (mammals) or a fertile clutch to be laid and sat (birds). Validated again at the moment. */
    void conceive(Animal a, Animal m) {
        if (!canBreed(a) || !m.alive || m.baby || m.female || m.species != a.species) { a.nextThink = 0; return; }
        Species s = a.species;
        a.lastBred = g.state.minutes;
        a.father = m.id;
        if (s.lays) {
            float[] spot = nestSpot(a);
            int eggs = s.litterMin + (int) ((a.id * 17 + (long) g.state.minutes) % (s.litterMax - s.litterMin + 1));
            Nest n = new Nest(g.state.newEntityId(), s, spot[0], spot[1], a.id, m.id, a.owner, eggs, g.state.minutes + s.gestation);
            nests.add(n);
            g.state.commit("ClutchLaid", a.id, m.id, DevMap.jurisdiction(spot[0], spot[1]), 0, eggs + " eggs, hatching at minute " + (int) n.hatchAt);
            a.lastEvent = "laid a clutch of " + eggs;
        } else {
            a.dueAt = g.state.minutes + s.gestation;
            g.state.commit("AnimalBred", a.id, m.id, DevMap.jurisdiction(a.x, a.z), 0, s.label + ", due at minute " + (int) a.dueAt);
            a.lastEvent = "with young";
        }
        a.nextThink = 0; m.nextThink = 0;
    }

    /** Where a bird sits her clutch: a free nest box on the farm, a quiet spot in the wild. */
    private float[] nestSpot(Animal a) {
        if (a.home == farm) for (Container b : g.objects.nestBoxes) {
            boolean taken = false;
            for (Nest n : nests) if (!n.done && Math.hypot(n.x - b.x, n.z - b.z) < 0.4) taken = true;
            if (!taken) return new float[]{b.x, b.z - 0.35f};
        }
        return new float[]{a.x, a.z};
    }

    /** An owned hen's or duck's ordinary egg, into a nest box for the keeper to collect (a real item, once). */
    private void layEgg(Animal a) {
        a.lastEgg = g.state.minutes;
        for (Container b : g.objects.nestBoxes) {
            ItemStack egg = new ItemStack(Items.EGG, 1, a.owner, false, b.id);
            if (b.contents.add(egg)) {
                g.state.mint(Items.EGG, 1);
                eggsLaid++;
                g.state.commit("EggLaid", a.id, b.id, DevMap.jurisdiction(b.x, b.z), 0, a.kind() + " " + a.id);
                return;
            }
        }
    }

    // ------------------------------------------------------------------ death and carcasses
    public void onKilled(Animal a, Actor killer, long cause) {
        a.cs.cancel(); a.amp = a.flap = 0;
        a.rearing = a.headDown = a.faceThreat = false;
        a.diedAt = g.state.minutes;
        a.killer = killer == null ? 0 : killer.id;
        deaths++;
        String how = killer == null ? "unknown" : killer instanceof Animal k ? "predation:" + k.species.label.toLowerCase() : killer == g.player ? "hunted by the player" : "killed by " + killer.name;
        if (a.home != null) a.home.count("died (" + how + ")", 1);
        if (killer instanceof Animal) kills++;
        despawnBody(a);
        // the body falls as physics has it, thrown by the blow that killed it (a body already down keeps its fall)
        if (a.loaded && a.distTo(g.player.x, g.player.z) < 70) {
            float mass = a.species.w * a.species.w * a.species.h * 420 * a.scale() * a.scale() * a.scale();
            float j = Math.min(3.5f, 0.6f + a.blowImpact * 0.02f) * mass;
            // (knocked down and then killed: that body goes on falling, now for good)
            if (a.ragdoll != null) a.ragdoll.knockdown = false;
            if (a.ragdoll == null) makeRagdoll(a, a.blowX * j, j * 0.3f, a.blowZ * j, false);
        }
        a.lastEvent = how;
        g.state.commit("AnimalDied", a.id, a.killer, DevMap.jurisdiction(a.x, a.z), cause, a.kind() + ", " + how);
        sound(a, a.species.deathSound, 1f);
        // a mother's young lose her; a predator that killed it may come to eat
        for (Animal b : animals) if (b.mother == a.id && b.alive) b.mother = 0;
        // killing someone's livestock is a crime, seen like any other
        // (a bounty hunter's law dog is not livestock: killing it, like killing them, adds nothing)
        if (a.owned() && killer != null && killer.id != a.owner && !(killer instanceof Animal) && !(g.actor(a.owner) instanceof rpg.ai.Npc n && n.posse))
            g.law.crime(killer, "LIVESTOCK", a.owner, a.species == Species.HORSE ? 50 : 30, null, cause);
    }

    /** What an animal is worth to its owner (Gold), for a theft or a killing. */
    public static int value(Animal a) {
        int v = switch (a.species) { case HORSE -> 900; case COW -> 600; case PIG -> 260; case SHEEP -> 220; case DOG -> 160; case CAT -> 60; case CHICKEN, DUCK -> 40; default -> 50; };
        return a.baby ? v / 2 : v;
    }

    private void carcass(Animal a) {
        if (a.diedAt < 0) return;
        if (!a.butchered && g.state.minutes - a.diedAt > 1440) { a.butchered = true; a.lastEvent = "rotted away"; }
        if (a.butchered) dropRagdoll(a);
        if (a.butchered && g.state.minutes - a.diedAt > 60) animals.remove(a);
    }

    /** A scavenger or predator eats at a carcass: its meat goes, and with it what a butcher could take. */
    void eat(Animal feeder,Actor corpse,float dm) {
        float total=corpse instanceof Animal animal?30+animal.species.meat*12:90;
        corpse.eatenMinutes+=dm;
        if(g.time-feeder.lastBite>1.2) {
            feeder.lastBite=g.time;
            g.gore.pool(corpse.x,corpse.z,0.2f,corpse.id,0);
            var wound=g.gore.of(corpse);
            var parts=new rpg.combat.Gore.Part[]{rpg.combat.Gore.Part.ARM_L,rpg.combat.Gore.Part.LEG_R,rpg.combat.Gore.Part.ARM_R,rpg.combat.Gore.Part.LEG_L,rpg.combat.Gore.Part.HEAD};
            int eaten=(int)(corpse.eatenMinutes/total*parts.length);
            for(int i=0;i<Math.min(eaten,parts.length);i++)if(!wound.lost(parts[i])) {
                if(corpse instanceof Animal animal)g.gore.severAnimal(animal,parts[i],0,0,feeder);
                else g.gore.sever(corpse,parts[i],0,0,feeder);
                break;
            }
            if(g.sfx!=null)g.sfx.play("soft.heavy",corpse.x,corpse.y,corpse.z,0.25f);
        }
        if(corpse.eatenMinutes<total)return;
        if(corpse instanceof Animal animal) { animal.butchered=true; animal.lastEvent="eaten"; dropRagdoll(animal); }
        else if(corpse instanceof rpg.ai.Npc dead && g.objects.get(dead.corpse) instanceof rpg.objects.Corpse body) {
            // Flesh can be consumed; possessions cannot disappear with it.
            var items=new ArrayList<>(body.contents.stacks()); items.addAll(dead.eq.stacks());
            for(ItemStack item:items)g.objects.drop(item,dead.x,dead.y+0.1f,dead.z);
            body.contents.clear(); dead.eq.clear(); body.removed=true; dead.corpse=0;
            if(dead.ragdoll!=0)g.game.commands().release(dead.ragdoll); dead.ragdoll=0;
        }
        for(var limb:new ArrayList<>(g.gore.limbs))if(limb.owner==corpse.id) { g.game.commands().release(limb.body);g.gore.limbs.remove(limb); }
        feeder.nextThink=0;
    }

    // ------------------------------------------------------------------ the tiers: near animals in full, far wild ones as cohort counts
    private void tiers() {
        int spawned = 0;
        float px = g.player.x, pz = g.player.z;
        for (Animal a : new ArrayList<>(animals)) {
            if (!a.alive || a.home == null || a.home.owner != 0 || a.species.domestic && a.owned()) continue;
            if (a.distTo(px, pz) < 84 || a.baby || youngNear(a, 40) || a.dueAt > 0 || a.hp < a.maxHp() || clutchOf(a) != null || a.goal == Goal.HUNT || a.goal == Goal.DEFEND) continue;
            if (g.vox.sight(px, g.player.eyeY(), pz, a.x, a.y + a.height * 0.5f, a.z)) continue;   // never vanishes in plain view
            despawnBody(a);
            animals.remove(a); nextSense.remove(a.id);
            a.home.cohort.computeIfAbsent(a.species, k -> new int[4])[Habitat.slot(a.baby, a.female)]++;
            a.home.count("into the cohort", 1);
            dematerialized++;
        }
        for (Habitat h : habitats) {
            if (h.cohortTotal() == 0 || Math.hypot(h.cx() - px, h.cz() - pz) > 64) continue;
            for (var e : h.cohort.entrySet()) {
                int[] c = e.getValue();
                for (int slot = 0; slot < 4; slot++) {
                    while (c[slot] > 0) {
                        float[] at = hiddenSpot(h);
                        if (at == null) return;
                        c[slot]--;
                        Animal a = spawnNew(e.getKey(), slot < 2, slot % 2 == 0, at[0], at[1], h, 900 + e.getKey().ordinal(), 0);
                        a.homeX = at[0]; a.homeZ = at[1];
                        a.range = e.getKey().predator() || e.getKey() == Species.BROWN_BEAR ? 16 : 11;
                        h.count("out of the cohort", 1);
                        materialized++;
                        if (++spawned >= 3) { tierAt = g.time + 0.1; return; }
                    }
                }
            }
        }
    }

    /** A place in a habitat where an animal can come back without being seen arriving: out of the player's sight and not close. */
    private float[] hiddenSpot(Habitat h) {
        for (int t = 0; t < 30; t++) {
            float x = h.x0 + 1 + rnd.nextFloat() * (h.x1 - h.x0 - 2), z = h.z0 + 1 + rnd.nextFloat() * (h.z1 - h.z0 - 2);
            int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
            if (g.vox.block(bx, DevMap.FLOOR, bz) != 0 || g.vox.block(bx, DevMap.FLOOR + 1, bz) != 0 || g.vox.block(bx, DevMap.FLOOR - 1, bz) == rpg.world.Blocks.WATER) continue;
            float d = (float) Math.hypot(x - g.player.x, z - g.player.z);
            if (d < 20) continue;
            if (g.vox.sight(g.player.x, g.player.eyeY(), g.player.z, x, DevMap.FLOOR + 0.6f, z) && d < 60) continue;
            return new float[]{x, z};
        }
        return null;
    }

    /** Time passing in a lump (a sleep, a skip): needs move on, pregnancies and growth come due on the next look; cohorts breed and grow by the same rules, in bulk. */
    public void catchUp(double minutes) {
        for (Animal a : animals) {
            if (!a.alive) continue;
            a.hunger = Math.min(0.7f, a.hunger + (float) minutes / 600f);
            a.thirst = Math.min(0.7f, a.thirst + (float) minutes / 600f);
            a.fear = 0; a.threat = 0;
            a.nextThink = 0;
        }
        for (Habitat h : habitats) for (var e : h.cohort.entrySet()) {
            Species s = e.getKey(); int[] c = e.getValue();
            // growing up: babies to adults (never a net gain)
            int grown = minutes >= s.mature ? c[0] + c[1] : 0;
            if (grown > 0) { c[2] += c[0]; c[3] += c[1]; c[0] = 0; c[1] = 0; h.count("matured", grown); }
            // births: only from adult females with an adult male about, up to the habitat's room
            Integer cap = h.capacity.get(s);
            int rounds = (int) (minutes / (s.gestation + s.rest));
            if (cap == null || rounds <= 0 || c[2] == 0 || c[3] == 0) continue;
            int room = cap - count(h, s);
            int born = Math.min(room, rounds * c[2] * (s.litterMin + s.litterMax) / 2);
            if (born <= 0) continue;
            c[0] += born / 2; c[1] += born - born / 2;
            h.count(s.lays ? "hatched" : "born", born);
            births += born;
        }
    }

    // ------------------------------------------------------------------ persistence
    /** Every animal (alive or a carcass), every clutch, every habitat's cohort and ledger: the population survives a save exactly. */
    public Map<String, Object> save() {
        Map<String, Object> root = new java.util.LinkedHashMap<>();
        List<Object> list = new ArrayList<>();
        for (Animal a : animals) {
            if (a.puppet) continue;
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("id", a.id); m.put("sp", a.species.name()); m.put("baby", a.baby); m.put("female", a.female); m.put("alive", a.alive);
            if(!a.alive)captureRest(a);
            java.util.List<Object> rest=new ArrayList<>();
            for(var entry:a.restPose.entrySet()) { var b=entry.getValue();rest.add(java.util.Map.of("part",entry.getKey().name(),"x",b.x,"y",b.y,"z",b.z,"qw",b.qw,"qx",b.qx,"qy",b.qy,"qz",b.qz)); }
            m.put("restPose",rest);
            m.put("charred",a.charred);m.put("injuries",a.injuries.save());
            m.put("y",(double)a.y);
            m.put("x", (double) a.x); m.put("z", (double) a.z); m.put("yaw", (double) a.yaw); m.put("hp", (double) a.hp);
            m.put("born", a.bornAt); m.put("mature", a.matureAt); m.put("mother", a.mother); m.put("owner", a.owner); m.put("group", a.group);
            m.put("home", a.home == null ? "" : a.home.id); m.put("hx", (double) a.homeX); m.put("hz", (double) a.homeZ); m.put("range", (double) a.range);
            m.put("variant", a.variant); m.put("hunger", (double) a.hunger); m.put("thirst", (double) a.thirst); m.put("fatigue", (double) a.fatigue);
            m.put("due", a.dueAt); m.put("father", a.father); m.put("bred", a.lastBred); m.put("egg", a.lastEgg);
            m.put("died", a.diedAt); m.put("butchered", a.butchered); m.put("killer", a.killer); m.put("eaten", (double) a.eatenMinutes);
            Map<String, Object> tr = new java.util.LinkedHashMap<>();
            for (var e : a.trust.entrySet()) tr.put(String.valueOf(e.getKey()), (double) e.getValue());
            m.put("trust", tr);
            list.add(m);
        }
        root.put("animals", list);
        List<Object> nl = new ArrayList<>();
        for (Nest n : nests) {
            if (n.done) continue;
            Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("id", n.id); m.put("sp", n.species.name()); m.put("x", (double) n.x); m.put("z", (double) n.z);
            m.put("mother", n.mother); m.put("father", n.father); m.put("owner", n.owner); m.put("eggs", n.eggs); m.put("hatch", n.hatchAt);
            nl.add(m);
        }
        root.put("nests", nl);
        Map<String, Object> hs = new java.util.LinkedHashMap<>();
        for (Habitat h : habitats) {
            Map<String, Object> hm = new java.util.LinkedHashMap<>();
            Map<String, Object> co = new java.util.LinkedHashMap<>();
            for (var e : h.cohort.entrySet()) co.put(e.getKey().name(), new ArrayList<Object>(List.of(e.getValue()[0], e.getValue()[1], e.getValue()[2], e.getValue()[3])));
            hm.put("cohort", co);
            hm.put("causes", new java.util.LinkedHashMap<String, Object>(h.causes));
            hs.put(h.id, hm);
        }
        root.put("habitats", hs);
        return root;
    }

    @SuppressWarnings("unchecked")
    public void load(Map<String, Object> root) {
        if (root == null) return;
        for (Animal a : animals) if (a.loaded && a.alive) despawnBody(a);
        animals.clear(); nests.clear(); nextSense.clear();
        for (Object o : astratto.util.Json.list(root, "animals")) {
            Map<String, Object> m = (Map<String, Object>) o;
            Species s = Species.valueOf(astratto.util.Json.str(m, "sp", "CHICKEN"));
            Animal a = new Animal(astratto.util.Json.lng(m, "id", 0), s, astratto.util.Json.bool(m, "baby", false), astratto.util.Json.bool(m, "female", false), (float) astratto.util.Json.num(m, "x", 0), (float) astratto.util.Json.num(m, "z", 0));
            a.yaw = (float) astratto.util.Json.num(m, "yaw", 0);
            a.bornAt = astratto.util.Json.num(m, "born", 0); a.matureAt = astratto.util.Json.num(m, "mature", 0);
            a.mother = astratto.util.Json.lng(m, "mother", 0); a.owner = astratto.util.Json.lng(m, "owner", 0); a.group = astratto.util.Json.lng(m, "group", 0);
            String home = astratto.util.Json.str(m, "home", "");
            for (Habitat h : habitats) if (h.id.equals(home)) a.home = h;
            a.homeX = (float) astratto.util.Json.num(m, "hx", a.x); a.homeZ = (float) astratto.util.Json.num(m, "hz", a.z); a.range = (float) astratto.util.Json.num(m, "range", 10);
            a.variant = astratto.util.Json.integer(m, "variant", 0);
            a.hunger = (float) astratto.util.Json.num(m, "hunger", 0); a.thirst = (float) astratto.util.Json.num(m, "thirst", 0); a.fatigue = (float) astratto.util.Json.num(m, "fatigue", 0);
            a.dueAt = astratto.util.Json.num(m, "due", -1); a.father = astratto.util.Json.lng(m, "father", 0); a.lastBred = astratto.util.Json.num(m, "bred", -1e9); a.lastEgg = astratto.util.Json.num(m, "egg", -1e9);
            a.alive = astratto.util.Json.bool(m, "alive", true);
            a.diedAt = astratto.util.Json.num(m, "died", -1); a.butchered = astratto.util.Json.bool(m, "butchered", false); a.killer = astratto.util.Json.lng(m, "killer", 0);
            a.eatenMinutes = (float) astratto.util.Json.num(m, "eaten", 0);
            Map<String, Object> tr = astratto.util.Json.obj(m, "trust");
            if (tr != null) for (var e : tr.entrySet()) a.trust.put(Long.parseLong(e.getKey()), ((Number) e.getValue()).floatValue());
            a.restOx = ((a.id % 5) - 2) * 0.6f; a.restOz = ((a.id / 5 % 3) - 1) * 0.7f;
            a.hp = a.alive ? (float) astratto.util.Json.num(m, "hp", a.maxHp()) : 0;
            a.y = (float)astratto.util.Json.num(m,"y",DevMap.FLOOR);
            a.charred=(float)astratto.util.Json.num(m,"charred",0);a.injuries.load(astratto.util.Json.list(m,"injuries"));
            if(!a.alive)a.lieT=1;
            var poses=astratto.util.Json.list(m,"restPose");
            if(poses!=null)for(Object entry:poses)if(entry instanceof Map<?,?> raw) {
                @SuppressWarnings("unchecked") Map<String,Object> pose=(Map<String,Object>)raw;
                var b=new Snapshots.Rigid();b.x=(float)astratto.util.Json.num(pose,"x",a.x);b.y=(float)astratto.util.Json.num(pose,"y",a.y);b.z=(float)astratto.util.Json.num(pose,"z",a.z);
                b.qw=(float)astratto.util.Json.num(pose,"qw",1);b.qx=(float)astratto.util.Json.num(pose,"qx",0);b.qy=(float)astratto.util.Json.num(pose,"qy",0);b.qz=(float)astratto.util.Json.num(pose,"qz",0);
                a.restPose.put(rpg.combat.Gore.Part.valueOf(astratto.util.Json.str(pose,"part","TORSO")),b);
            }
            animals.add(a);
            if (a.alive) spawnBody(a); else a.loaded = true;
        }
        for (Object o : astratto.util.Json.list(root, "nests")) {
            Map<String, Object> m = (Map<String, Object>) o;
            nests.add(new Nest(astratto.util.Json.lng(m, "id", 0), Species.valueOf(astratto.util.Json.str(m, "sp", "CHICKEN")), (float) astratto.util.Json.num(m, "x", 0), (float) astratto.util.Json.num(m, "z", 0),
                    astratto.util.Json.lng(m, "mother", 0), astratto.util.Json.lng(m, "father", 0), astratto.util.Json.lng(m, "owner", 0), astratto.util.Json.integer(m, "eggs", 0), astratto.util.Json.num(m, "hatch", 0)));
        }
        Map<String, Object> hs = astratto.util.Json.obj(root, "habitats");
        if (hs != null) for (Habitat h : habitats) {
            Map<String, Object> hm = astratto.util.Json.obj(hs, h.id);
            h.cohort.clear(); h.causes.clear();
            if (hm == null) continue;
            Map<String, Object> co = astratto.util.Json.obj(hm, "cohort");
            if (co != null) for (var e : co.entrySet()) {
                List<Object> l = (List<Object>) e.getValue();
                int[] c = new int[4];
                for (int i = 0; i < 4; i++) c[i] = ((Number) l.get(i)).intValue();
                h.cohort.put(Species.valueOf(e.getKey()), c);
            }
            Map<String, Object> ca = astratto.util.Json.obj(hm, "causes");
            if (ca != null) for (var e : ca.entrySet()) h.causes.put(e.getKey(), ((Number) e.getValue()).intValue());
        }
    }

    /** The ledger's check: each habitat's count equals its start plus births and hatchings less deaths; moving between live and cohort makes and loses nothing. */
    public String ledgerProblem() {
        for (Habitat h : habitats) {
            int expect = 0;
            for (var e : h.causes.entrySet()) {
                String k = e.getKey();
                if (k.equals("starting") || k.equals("born") || k.equals("hatched") || k.equals("released")) expect += e.getValue();
                else if (k.startsWith("died")) expect -= e.getValue();
            }
            int now = count(h);
            if (now != expect) return h.name + ": " + now + " animals, the ledger says " + expect + " " + h.causes;
        }
        return null;
    }

    // ------------------------------------------------------------------ people and animals
    public Result perform(Animal a, Actor who, String action, long req) {
        switch (action) {
            case "pet" -> {
                if (Math.hypot(who.x - a.x, who.z - a.z) > g.cfg.interactRange + a.radius) return Result.no("You're too far away.");
                if (!a.alive) return Result.no("It's dead.");
                if (a.goal == Goal.FLEE || a.goal == Goal.DEFEND || a.goal == Goal.WARN) return Result.no("It's too agitated.");
                if (!a.species.domestic && a.trustOf(who.id) <= 0.3f) return Result.no("It won't let you near enough.");
                // a bond grows from a pattern of kindness, not from clicking: a little each time, a while apart
                double last = a.pettedAt.getOrDefault(who.id, -1e9);
                if (g.time - last > 20) a.trust.merge(who.id, 0.08f, (x, y) -> Math.min(1, x + y));
                a.pettedAt.put(who.id, g.time);
                a.fear = Math.max(0, a.fear - 0.3f);
                a.wantYaw = (float) Math.atan2(-(who.x - a.x), -(who.z - a.z));
                g.state.commit(req, "AnimalPetted", who.id, a.id, DevMap.jurisdiction(a.x, a.z), 0, a.kind());
                if (who == g.player) g.honor.petted(a.id);
                if (who == g.player) g.chat.say(petLine(a));
                sound(a, a.species.idleSound, 0.6f);
                return Result.ok();
            }
            case "feed" -> {
                ItemDef food = feedFor(a, who);
                if (food == null) return Result.no("You have nothing it would eat.");
                if (Math.hypot(who.x - a.x, who.z - a.z) > g.cfg.interactRange + a.radius) return Result.no("You're too far away.");
                who.takeItems(food, 1);
                g.state.burn(food, 1);
                a.hunger = Math.max(0, a.hunger - 0.5f);
                a.trust.merge(who.id, 0.15f, (x, y) -> Math.min(1, x + y));
                a.fear = 0;
                g.state.commit(req, "AnimalFed", who.id, a.id, DevMap.jurisdiction(a.x, a.z), 0, a.kind() + " ate " + food.name.toLowerCase());
                if (who == g.player) g.chat.say("The " + a.kind().toLowerCase() + " eats the " + food.name.toLowerCase() + ".");
                return Result.ok();
            }
            case "milk" -> {
                if (a.species != Species.COW || !a.female || a.baby || !a.alive) return Result.no("You can't milk that.");
                if (!who.has(Items.BUCKET, 1)) return Result.no("You need an empty bucket.");
                if (g.state.minutes - a.lastMilked <= MILK_REFILL) return Result.no("She has been milked recently.");
                if (Math.hypot(who.x - a.x, who.z - a.z) > g.cfg.interactRange + a.radius) return Result.no("You're too far away.");
                g.interact.begin(new Work(who, a, "milk", "Milking", 2.2f, false, g.time, () -> milkNow(a, who, req)));
                return Result.started();
            }
            case "butcher" -> {
                if (!a.carcass() || a.butchered) return Result.no("Nothing left to take.");
                if (blade(who) == null) return Result.no("You need a knife or a blade.");
                if (Math.hypot(who.x - a.x, who.z - a.z) > g.cfg.interactRange + a.radius) return Result.no("You're too far away.");
                g.interact.begin(new Work(who, a, "butcher", "Butchering", 2.4f + a.species.meat * 0.25f, false, g.time, () -> butcherNow(a, who, req)));
                return Result.started();
            }
            default -> { return Result.no("You can't do that."); }
        }
    }

    /** Game minutes a cow takes to give a full bucket again. */
    public static final double MILK_REFILL = 240;

    /** A bucket filled from the cow: the milk is the owner's when the cow is someone else's, and taking it is theft. */
    private Result milkNow(Animal a, Actor who, long req) {
        if (!a.alive || a.goal == Goal.FLEE || a.goal == Goal.DEFEND) return Result.no("She won't stand for it.");
        if (who.takeItems(Items.BUCKET, 1) == null) return Result.no("You need an empty bucket.");
        g.state.burn(Items.BUCKET, 1);
        a.lastMilked = g.state.minutes;
        boolean theirs = a.owned() && a.owner != who.id;
        g.grant(who, theirs ? new ItemStack(Items.MILK, 1, a.owner, true, a.id) : new ItemStack(Items.MILK, 1));
        var ev = g.state.commit(req, "CowMilked", who.id, a.id, DevMap.jurisdiction(a.x, a.z), 0, a.kind());
        if (who == g.player) g.chat.say("You fill the bucket with warm milk.");
        sound(a, a.species.idleSound, 0.5f);
        if (theirs) g.law.crime(who, "THEFT", a.owner, Items.MILK.value, null, ev.id());
        return Result.ok();
    }

    /** The yield, once: into the butcher's packs (or at their feet), still the owner's if it was someone's livestock. */
    private Result butcherNow(Animal a, Actor who, long req) {
        if (a.butchered) return Result.no("Nothing left to take.");
        a.butchered = true;
        butchered++;
        List<String> got = new ArrayList<>();
        for (ItemStack s : a.yields()) {
            ItemStack st = a.owned() && a.owner != who.id ? new ItemStack(s.def, s.amount, a.owner, true, a.id) : s;
            g.grant(who, st);
            got.add(s.amount + " " + s.def.name.toLowerCase());
        }
        var ev = g.state.commit(req, "AnimalButchered", who.id, a.id, DevMap.jurisdiction(a.x, a.z), 0, a.kind() + " -> " + got);
        g.prog.award(who, Skill.COOKING, 4 + a.species.meat * 2, "butchered a " + a.kind().toLowerCase(), ev.id());
        if (who == g.player) g.chat.say("You butcher the " + a.kind().toLowerCase() + ": " + String.join(", ", got) + ".");
        if (a.owned() && a.owner != who.id) g.law.crime(who, "THEFT", a.owner, value(a) / 2, null, ev.id());
        return Result.ok();
    }

    private String petLine(Animal a) {
        if (a.baby) return "The " + a.kind().toLowerCase() + " fidgets under your hand, then looks for its mother.";
        if (a.hunger > 0.65f) return "The " + a.kind().toLowerCase() + " noses about your hands for food.";
        if (a.goal == Goal.REST) return "The " + a.kind().toLowerCase() + " stirs, then settles again.";
        return switch (a.species) {
            case DOG -> a.trustOf(g.player.id) > 0.5f ? "The dog leans into your hand, tail going." : "The dog sniffs your hand, then lets you scratch its ears.";
            case CAT -> a.trustOf(g.player.id) > 0.4f ? "The cat purrs and pushes its head against you." : "The cat tolerates it - just about.";
            case HORSE -> "The horse snorts softly and noses your shoulder.";
            case COW -> "The cow keeps chewing, unbothered.";
            case SHEEP -> "The sheep's fleece is thick and warm.";
            case PIG -> "The pig grunts happily.";
            case CHICKEN -> "The hen settles her feathers beneath your palm, watching your fingers.";
            case DUCK -> "The duck mutters softly and gives its tail a brisk shake.";
            case RABBIT -> "The rabbit's nose works quickly; its ears turn toward the nearest sound.";
            case FOX -> "The fox leans away, then returns to sniff your sleeve.";
            case COYOTE -> "The coyote watches your other hand, wary even now.";
            case WOLF -> "The wolf holds still, ears tilted back, testing your scent.";
            case MOUNTAIN_LION -> "The cat follows your hand with its eyes. You keep the touch brief.";
            case BROWN_BEAR -> "The bear huffs and shifts its weight. You give it room.";
        };
    }

    /** What, of what someone carries, this animal would eat. */
    public static ItemDef feedFor(Animal a, Actor who) {
        ItemDef[] menu = switch (a.species.diet) {
            case GRAZER -> new ItemDef[]{Items.ANIMAL_FEED, Items.BERRIES};
            case FORAGER -> new ItemDef[]{Items.ANIMAL_FEED, Items.BREAD, Items.BERRIES};
            case HUNTER -> new ItemDef[]{Items.RAW_MEAT, Items.COOKED_MEAT};
            case OMNIVORE -> new ItemDef[]{Items.RAW_MEAT, Items.BERRIES, Items.BREAD, Items.RAW_TROUT};
            case SCAVENGER -> a.species == Species.CAT ? new ItemDef[]{Items.RAW_TROUT, Items.TROUT, Items.RAW_SHRIMP, Items.COOKED_MEAT, Items.RAW_MEAT} : new ItemDef[]{Items.COOKED_MEAT, Items.RAW_MEAT, Items.BREAD};
        };
        for (ItemDef d : menu) if (who.has(d, 1)) return d;
        return null;
    }

    /** A knife, or a bladed weapon in hand, to butcher with. */
    public static ItemDef blade(Actor a) {
        if (a.has(Items.KNIFE, 1)) return Items.KNIFE;
        ItemDef w = a.main();
        if (w != null && (w.family == ItemDef.Family.SWORD || w.family == ItemDef.Family.DAGGER || w.family == ItemDef.Family.AXE)) return w;
        return null;
    }

    /** Owned stock eats one sack's worth from the trough (a real item, used up). */
    boolean eatFeed(Trough t, Animal a) {
        if (t.portions <= 0) {
            var got = t.box.contents.remove(Items.ANIMAL_FEED, 1);
            if (got.isEmpty()) return false;
            g.state.burn(Items.ANIMAL_FEED, 1);
            t.portions = Trough.PER_SACK;
        }
        t.portions--;
        a.lastEvent = "ate at the trough";
        return true;
    }


    // ------------------------------------------------------------------ sound
    public void sound(Animal a, String key, float vol) {
        if (key == null || g.sfx == null) return;
        a.lastSound = g.time;
        if (a.distTo(g.player) > 34) return;
        float pitch = (a.baby ? 1.35f : 0.92f + (a.variant % 4) * 0.05f) * g.sfx.pitchOf(key);
        // a cockerel crows now and then in place of the clucking, mostly round dawn
        if (key.equals("animal.chicken") && !a.female && !a.baby && g.sfx.has("animal.rooster")) {
            double h = g.state.hourOfDay();
            if (h > 4.5 && h < 8 || (a.id + (long) g.time) % 5 == 0) key = "animal.rooster";
        }
        g.sfx.play(key, a.x, a.y + a.height * 0.7f, a.z, vol, pitch, 30);
    }

    /** A noise someone made: animals in earshot take fright in proportion. */
    public void hear(Actor source, float x, float y, float z, float radius) {
        if (source == null || source == g.player && g.notarget()) return;
        for (Animal a : animals) {
            if (!a.alive || !a.loaded || a == source) continue;
            float d = (float) Math.hypot(a.x - x, a.z - z), reach = radius * a.species.hearing;
            if (d > reach) continue;
            mind.heard(a, source, x, z, 1 - d / reach);
        }
    }

    /** Someone struck an animal: it knows who, at once. */
    public void onHurt(Animal a, Actor attacker) {
        a.fear = 1;
        a.threat = attacker.id; a.threatX = attacker.x; a.threatZ = attacker.z; a.threatSeen = g.time;
        a.nextThink = 0;
        sound(a, a.species.hurtSound, 0.9f);
    }
}
