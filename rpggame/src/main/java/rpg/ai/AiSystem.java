package rpg.ai;

import astratto.bridge.CommandBuffer;
import astratto.bridge.Snapshots;
import java.util.ArrayList;
import java.util.List;
import rpg.Ids;
import rpg.Rpg;
import rpg.actor.Actor;
import rpg.actor.Appearance;
import rpg.item.ItemDef;
import rpg.item.ItemStack;
import rpg.item.Items;
import rpg.objects.Node;
import rpg.skill.Skill;
import rpg.world.DevMap;

import static astratto.bridge.AstrattoNative.*;

/**
 * The NPCs and their bodies in the engine. One policy - nine movement actions, each scored by a fact the Java brain
 * sets (its desire) and, for investigating, by the engine's own ears - drives every agent; the engine perceives,
 * chooses with inertia, searches routes and walks the movers. Java perceives what the engine does not (who can see a
 * crime), keeps memories, chooses goals with reasons, and carries out the work, talk, reports and fights.
 */
public final class AiSystem {
    public static final int A_IDLE = 0, A_WANDER = 1, A_GOTO = 2, A_RUN = 3, A_APPROACH = 4, A_FLEE = 5, A_INVESTIGATE = 6, A_HOLD = 7, A_COMBAT = 8, ACTIONS = 9;
    public static final String[] ACTION_NAMES = {"idle", "wander", "go to goal", "run to goal", "approach", "flee", "investigate", "hold/face", "combat move"};

    private final Rpg g;
    public final Brain brain;
    public final Stealth stealth;
    /** How fast an NPC's body gathers and sheds speed (m/s per s). */
    static final float NPC_ACCEL = System.getenv("RPG_NPC_ACCEL") != null ? Float.parseFloat(System.getenv("RPG_NPC_ACCEL")) : 14f;
    private long policy;
    public long decisions, sightings, stuckEvents, arrivals, heardEvents, shared, reports;
    /** Dead bodies Sydney had to be told again were dead (should stay 0: anything else is a body trying to rise). */
    public long deathsReasserted;
    private final java.util.Map<Long, Double> heardAt = new java.util.HashMap<>();
    private double abstractClock;

    public AiSystem(Rpg g) { this.g = g; this.brain = new Brain(g, this); this.stealth = new Stealth(g); this.rumors = new Rumors(g, this); }

    // ------------------------------------------------------------------ setting up
    public void init() {
        CommandBuffer c = g.game.commands();
        policy = g.game.reserve(KIND_POLICY);
        List<CommandBuffer.Action> acts = new ArrayList<>();
        acts.add(new CommandBuffer.Action(A_IDLE, ACT_IDLE).base(1).consider(IN_FACT0 + A_IDLE, CURVE_LINEAR, 0, 1, 1));
        acts.add(new CommandBuffer.Action(A_WANDER, ACT_WANDER).base(1).range(6).speed(g.cfg.walkSpeed * 0.8f).consider(IN_FACT0 + A_WANDER, CURVE_LINEAR, 0, 1, 1));
        acts.add(new CommandBuffer.Action(A_GOTO, ACT_MOVE_TO_GOAL).base(1).speed(g.cfg.walkSpeed).consider(IN_FACT0 + A_GOTO, CURVE_LINEAR, 0, 1, 1));
        acts.add(new CommandBuffer.Action(A_RUN, ACT_MOVE_TO_GOAL).base(1).speed(4.8f).consider(IN_FACT0 + A_RUN, CURVE_LINEAR, 0, 1, 1));
        acts.add(new CommandBuffer.Action(A_APPROACH, ACT_APPROACH).base(1).range(1.6f).speed(4.2f).consider(IN_FACT0 + A_APPROACH, CURVE_LINEAR, 0, 1, 1));
        acts.add(new CommandBuffer.Action(A_FLEE, ACT_FLEE).base(1).range(16).speed(6.2f).consider(IN_FACT0 + A_FLEE, CURVE_LINEAR, 0, 1, 1));
        acts.add(new CommandBuffer.Action(A_INVESTIGATE, ACT_INVESTIGATE).base(1).speed(2.2f).consider(IN_FACT0 + A_INVESTIGATE, CURVE_LINEAR, 0, 1, 1)
                .consider(IN_SINCE_HEARD, CURVE_INVERSE, 0, 15, 0.6f));
        acts.add(new CommandBuffer.Action(A_HOLD, ACT_HOLD).base(1).consider(IN_FACT0 + A_HOLD, CURVE_LINEAR, 0, 1, 1));
        acts.add(new CommandBuffer.Action(A_COMBAT, ACT_CUSTOM).base(1).speed(3.4f).consider(IN_FACT0 + A_COMBAT, CURVE_LINEAR, 0, 1, 1));
        c.policyCreate(policy, acts, 0.1f, 0.1f);
        roster();
    }

    private void roster() {
        Npc ada = npc(Ids.MERCHANT_A, "Ada", Npc.Role.MERCHANT, "Merchant (Store A)", 91.5f, 55.0f, 101);
        ada.homeX = 91.5f; ada.homeZ = 89.5f;
        give(ada, Items.COINS, 600); give(ada, Items.BREAD, 2);
        Npc bram = npc(Ids.LAWMAN_A, "Bram", Npc.Role.LAWMAN, "Lawman (DevCity)", DevMap.LAWMAN_A[0], 55.0f, 102);
        give(bram, Items.COINS, 2500); // finite watch treasury, replenished by actual fines
        bram.homeX = 110.5f; bram.homeZ = 54.5f;
        levels(bram, 40, 40, 40, 40); wear(bram, "steel_sword", "steel_shield", "steel_body");
        Npc cole = npc(Ids.GUARD, "Cole", Npc.Role.GUARD, "Guard (DevCity)", 98.5f, 66.5f, 103);
        levels(cole, 30, 30, 30, 30); wear(cole, "iron_sword", "iron_shield", "iron_body", "iron_helm", "iron_legs"); give(cole, Items.TROUT, 3);
        Npc dag = npc(Ids.WORKER, "Dag", Npc.Role.WORKER, "Woodcutter", 89.5f, 21.5f, 104);
        dag.homeX = 90.5f; dag.homeZ = 90.5f;
        dag.skills.setLevel(Skill.WOODCUTTING, 12); give(dag, Items.get("bronze_axe"), 1); give(dag, Items.BREAD, 1);
        Npc eda = npc(Ids.VILLAGER, "Eda", Npc.Role.VILLAGER, "Villager", 101.5f, 75.5f, 105);
        eda.homeX = 104.5f; eda.homeZ = 90.5f;
        eda.skills.setLevel(Skill.HARVESTING, 15); give(eda, Items.COINS, 40);
        Npc finn = npc(Ids.THIEF, "Finn", Npc.Role.THIEF, "Thief", 96.5f, 74.5f, 106);
        finn.skills.setLevel(Skill.THIEVING, 25); levels(finn, 10, 8, 8, 12); wear(finn, "bronze_dagger", null, "leather_body"); finn.pickpocketLevel = 15; give(finn, Items.COINS, 25);
        Npc gus = npc(Ids.MERCHANT_B, "Gus", Npc.Role.MERCHANT, "Merchant (Store B)", 17.5f, 109.5f, 107);
        give(gus, Items.COINS, 600);
        Npc hale = npc(Ids.LAWMAN_B, "Hale", Npc.Role.LAWMAN, "Lawman (DevTown)", DevMap.LAWMAN_B[0], 120.0f, 108);
        give(hale, Items.COINS, 2500);
        levels(hale, 40, 40, 40, 40); wear(hale, "steel_sword", "steel_shield", "steel_body");
        Npc ivo = npc(Ids.CARTER, "Ivo", Npc.Role.CARTER, "Carter", 89.5f, 64.5f, 109);
        ivo.homeX = 89.5f; ivo.homeZ = 90.5f; give(ivo, Items.COINS, 30);
        Npc jun = npc(Ids.QUEST_GIVER, "Jun", Npc.Role.QUEST_GIVER, "Quest giver (test)", 61.5f, 102.5f, 110);
        Npc kit = npc(Ids.CONTACT, "Kit", Npc.Role.CONTACT, "Courier contact (removable)", 48.5f, 112.5f, 111);
        Npc mara = npc(Ids.FARMER, "Mara", Npc.Role.FARMER, "Livestock farmer", 104.5f, 106.5f, 112);
        mara.homeX = 117.5f; mara.homeZ = 119.5f; mara.postX = 104.5f; mara.postZ = 107.5f;
        give(mara, Items.COINS, 30);
        // the undertaker keeps the graveyard and what the guards bring in from the dead (GDD 3.12)
        Npc morrow = npc(Ids.UNDERTAKER, "Morrow", Npc.Role.UNDERTAKER, "Undertaker (DevCity)", DevMap.UNDERTAKER_POST[0], DevMap.UNDERTAKER_POST[1], 113);
        morrow.postX = DevMap.UNDERTAKER_POST[0]; morrow.postZ = DevMap.UNDERTAKER_POST[1];
        give(morrow, Items.COINS, 40);
        // the fence keeps a nook behind Store B (GDD 3.3): buys what is stolen, sells it on quietly
        Npc silas = npc(Ids.FENCE, "Silas", Npc.Role.FENCE, "Fence (DevTown)", DevMap.FENCE_POST[0], DevMap.FENCE_POST[1], 114);
        silas.postX = DevMap.FENCE_POST[0]; silas.postZ = DevMap.FENCE_POST[1];
        give(silas, Items.COINS, 900);
        silas.skills.setLevel(Skill.THIEVING, 40);
        silas.pickpocketLevel = 30;
        for (Npc n : new Npc[]{ada, gus, jun, kit, eda, ivo, mara, morrow}) n.pickpocketLevel = 1;
        for(long id:ADDED_ROSTER)addReleaseNpc(id);
        int[][] bs = DevMap.BANDIT_SPAWNS;
        Npc b1 = npc(Ids.BANDIT_1, "Bandit", Npc.Role.BANDIT, "Hostile test NPC (melee)", bs[0][0] + 0.5f, bs[0][1] + 0.5f, 120);
        levels(b1, 12, 12, 10, 14); wear(b1, "bronze_sword", "wooden_shield", "leather_body", "leather_helm"); give(b1, Items.SHRIMP, 2); give(b1, Items.COINS, 15);
        Npc b2 = npc(Ids.BANDIT_2, "Bandit archer", Npc.Role.BANDIT, "Hostile test NPC (ranged)", bs[1][0] + 0.5f, bs[1][1] + 0.5f, 121);
        levels(b2, 8, 8, 8, 12); b2.skills.setLevel(Skill.ARCHERY, 15); wear(b2, "shortbow", null, "leather_body");
        b2.eq.set(ItemDef.Slot.AMMO, new ItemStack(Items.BRONZE_ARROWS, 40)); g.state.mint(Items.BRONZE_ARROWS, 40); give(b2, Items.COINS, 10);
        Npc brute = npc(Ids.BRUTE, "Brute", Npc.Role.BRUTE, "Hostile test NPC (heavy)", bs[2][0] + 0.5f, bs[2][1] + 0.5f, 122);
        levels(brute, 35, 40, 30, 45); wear(brute, "iron_mace", null, "chainmail_body", "chainmail_legs"); give(brute, Items.TROUT, 2); give(brute, Items.COINS, 60);
        brute.aggroRange = 9;
        // those with a bed sleep in it
        for (Npc n : g.npcs) { float[] bed = DevMap.bedOf(n.id); if (bed != null) { n.homeX = bed[0]; n.homeZ = bed[1]; } }
        for (Npc n : g.npcs) { n.restore(); n.social = (n.id % 7) / 10f; n.hunger = (n.id % 5) / 10f; spawnBody(n); }
    }

    private static final List<Long> ADDED_ROSTER=List.of(Ids.BOBWIN,Ids.BEGGAR,Ids.HEALER,Ids.PRIEST,Ids.HUNTER,Ids.SCHOLAR,Ids.FISHERMAN,Ids.MINER,Ids.CRAFTSMAN,Ids.KNIGHT,Ids.PREDATOR_HUNTER);
    private Npc addReleaseNpc(long id) {
        switch((int)id) {
            case (int)Ids.BOBWIN -> {
                Npc bobwin = npc(Ids.BOBWIN, "Bobwin", Npc.Role.GUARD, "City watch", 105.5f, 77.5f, 140);
                levels(bobwin, 28, 30, 32, 32); wear(bobwin, "iron_sword", "iron_shield", "iron_body"); give(bobwin, Items.TROUT, 3);
                return bobwin;
            }
            case (int)Ids.BEGGAR -> {
                Npc oswin = npc(Ids.BEGGAR, "Oswin", Npc.Role.BEGGAR, "Vagrant", 100.5f, 85.5f, 141);
                give(oswin, Items.BREAD, 1);
                return oswin;
            }
            case (int)Ids.HEALER -> {
                Npc maud = npc(Ids.HEALER, "Maud", Npc.Role.HEALER, "Herbalist", 108.5f, 82.5f, 142);
                give(maud, Items.TROUT, 8); give(maud, Items.COINS, 30); maud.skills.setLevel(Skill.MAGIC, 60); maud.spellBook = rpg.combat.Spells.Book.LUNAR;
                return maud;
            }
            case (int)Ids.PRIEST -> {
                Npc alric = npc(Ids.PRIEST, "Alric", Npc.Role.PRIEST, "Graveyard chaplain", 110.5f, 88.5f, 143);
                alric.skills.setLevel(Skill.FAITH, 35); give(alric, Items.BREAD, 3);
                return alric;
            }
            case (int)Ids.HUNTER -> {
                Npc godric = npc(Ids.HUNTER, "Godric", Npc.Role.HUNTER, "Woodsman", 70.5f, 80.5f, 144);
                levels(godric,12,12,10,18);godric.skills.setLevel(Skill.HARVESTING,25);godric.skills.setLevel(Skill.ARCHERY,18);
                Profession.GAME_HUNTER.assign(godric);give(godric,Items.get("bronze_dagger"),1);give(godric,Items.BREAD,2);give(godric,Items.KNIFE,1);wear(godric,"shortbow",null,"leather_body");
                godric.eq.set(ItemDef.Slot.AMMO,new ItemStack(Items.BRONZE_ARROWS,20));g.state.mint(Items.BRONZE_ARROWS,20);
                return godric;
            }
            case (int)Ids.SCHOLAR -> {
                Npc isolde = npc(Ids.SCHOLAR, "Isolde", Npc.Role.SCHOLAR, "Travelling scholar", 103.5f, 58.5f, 145);
                isolde.skills.setLevel(Skill.MAGIC, 40); isolde.spellBook = rpg.combat.Spells.Book.ARCANE; give(isolde, Items.COINS, 60); give(isolde, Items.BREAD, 2);
                return isolde;
            }
            case (int)Ids.FISHERMAN -> {
                Npc elowen=npc(id,"Elowen",Npc.Role.FISHERMAN,"Lake Net Fisherman",93.5f,41.5f,146);
                Profession.LAKE_NET_FISHERMAN.assign(elowen);elowen.skills.setLevel(Skill.FISHING,25);
                give(elowen,Items.FISHING_NET,1);give(elowen,Items.BREAD,2);give(elowen,Items.COINS,40);return elowen;
            }
            case (int)Ids.MINER -> {
                Npc oren=npc(id,"Oren",Npc.Role.MINER,"Ore Miner",92.5f,22.5f,147);
                Profession.ORE_MINER.assign(oren);oren.skills.setLevel(Skill.MINING,30);
                give(oren,Items.get("bronze_pickaxe"),1);give(oren,Items.BREAD,2);give(oren,Items.COINS,40);return oren;
            }
            case (int)Ids.CRAFTSMAN -> {
                Npc tamsin=npc(id,"Tamsin",Npc.Role.CRAFTSMAN,"Blacksmith",81.5f,22.5f,148);
                Profession.BLACKSMITH.assign(tamsin);tamsin.skills.setLevel(Skill.SMITHING,35);
                give(tamsin,Items.HAMMER,1);give(tamsin,Items.BRONZE_BAR,8);give(tamsin,Items.BREAD,2);give(tamsin,Items.COINS,120);return tamsin;
            }
            case (int)Ids.KNIGHT -> {
                Npc alden=npc(id,"Sir Alden",Npc.Role.KNIGHT,"Escort Knight",87.5f,64.5f,149);
                Profession.ESCORT_KNIGHT.assign(alden);alden.dutyTarget=Ids.CARTER;alden.dutyX=alden.postX;alden.dutyZ=alden.postZ;
                levels(alden,35,35,40,40);wear(alden,"steel_sword","steel_shield","steel_body");give(alden,Items.TROUT,3);return alden;
            }
            case (int)Ids.PREDATOR_HUNTER -> {
                Npc fenna=npc(id,"Fenna",Npc.Role.HUNTER,"Predator Hunter",82.5f,91.5f,150);
                Profession.PREDATOR_HUNTER.assign(fenna);levels(fenna,18,18,15,22);fenna.skills.setLevel(Skill.ARCHERY,25);
                wear(fenna,"shortbow",null,"leather_body");give(fenna,Items.get("bronze_dagger"),1);give(fenna,Items.KNIFE,1);give(fenna,Items.BREAD,2);give(fenna,Items.SNARE,1);
                fenna.eq.set(ItemDef.Slot.AMMO,new ItemStack(Items.BRONZE_ARROWS,30));g.state.mint(Items.BRONZE_ARROWS,30);return fenna;
            }
            default -> throw new IllegalArgumentException("Not an authored release NPC: "+id);
        }
    }

    /** Older profiles acquire new authored residents and their initial stock exactly once per saved timeline. */
    public List<Npc> restoreMissingRoster(java.util.Set<Long> saved) {
        List<Npc> added=new ArrayList<>();
        for(long id:ADDED_ROSTER)if(!saved.contains(id)) {
            if(g.actor(id) instanceof Npc old) {
                if(old.body!=0)despawnBody(old);
                if(old.ragdoll!=0)g.game.commands().release(old.ragdoll);
                g.npcs.remove(old);
            }
            Npc n=addReleaseNpc(id);n.restore();n.poseReset=true;n.social=(n.id%7)/10f;n.hunger=(n.id%5)/10f;spawnBody(n);added.add(n);
        }
        return added;
    }

    /** The townsfolk who are women; everyone else on the roster is a man (the sex follows the name, never a dice roll). */
    private static final java.util.Set<String> FEMALE_NAMES = java.util.Set.of("Ada", "Eda", "Jun", "Kit", "Mara", "Maud", "Isolde", "Bandit archer", "Wren", "Elowen", "Tamsin", "Fenna");

    private Npc npc(long id, String name, Npc.Role role, String title, float x, float z, long lookSeed) {
        Npc n = new Npc(id, name, role, title, x, z);
        // an ordinary grown man or woman: Health 10 (10 HP), as a new player has; fighters are given more below
        n.skills.setLevel(Skill.HEALTH, 10);
        n.look = Appearance.random(lookSeed, FEMALE_NAMES.contains(name));
        if(id>=Ids.FISHERMAN && id<=Ids.PREDATOR_HUNTER) {
            n.look.top=2;n.look.bottom=1;n.look.shoes=10;n.look.shoesColor=16;
            n.look.topColor=switch(role){case FISHERMAN->6;case MINER->21;case CRAFTSMAN->16;case KNIGHT->9;default->5;};
            n.look.bottomColor=role==Npc.Role.KNIGHT?9:23;
            n.look.hair=role==Npc.Role.CRAFTSMAN?10:role==Npc.Role.FISHERMAN?15:6;
            if(!n.look.female)n.look.beard=role==Npc.Role.MINER?5:0;
        }
        n.fitBody();
        n.yaw = 0;
        g.npcs.add(n);
        return n;
    }

    private void levels(Npc n, int att, int str, int def, int hp) {
        n.skills.setLevel(Skill.ATTACK, att); n.skills.setLevel(Skill.STRENGTH, str); n.skills.setLevel(Skill.DEFENSE, def); n.skills.setLevel(Skill.HEALTH, hp);
    }

    private void wear(Npc n, String... ids) {
        for (String id : ids) {
            if (id == null) continue;
            ItemDef d = Items.get(id);
            n.eq.set(d.slot, new ItemStack(d, 1));
            g.state.mint(d, 1);
        }
    }

    private void give(Npc n, ItemDef d, int amount) { g.grant(n, new ItemStack(d, amount)); }

    /** Makes the NPC's mover and agent in the engine (on spawning, reloading or reviving). */
    public void spawnBody(Npc n) {
        CommandBuffer c = g.game.commands();
        n.body = g.game.reserve(KIND_MOVER);
        n.agent = g.game.reserve(KIND_AGENT);
        c.moverCreate(n.body, n.x, n.y + 0.05f, n.z, n.radius, n.height, 0.6f, MOVER_AGENT, n.height * rpg.actor.Body.CROUCH_HEIGHT);
        c.agentCreate(n.agent, policy, n.body, 0, 0, 0, 1.6f, 22, 0.25f, 24, n.team, n.domain.flags | AGENT_DOORS | AGENT_EXTERNAL_TRAVERSAL | (n.skills.level(Skill.AGILITY) >= 5 ? AGENT_MANTLE : 0), (int) n.id * 7919);
        c.agentTarget(n.agent, g.player == null ? 0 : g.player.body, 0, 0, 0);
        c.moverInput(n.body, 0, 0, 0, 0, NPC_ACCEL, 0);   // a person gathers speed and sheds it (the walk has weight): no instant starts
        java.util.Arrays.fill(n.facts, -1);
        n.targetBody = -1;
        n.loaded = true;
        desire(n, A_IDLE);
        if(g.chemistry!=null)g.chemistry.register(n);
    }

    public void despawnBody(Npc n) {
        if (n.agent != 0) g.game.commands().release(n.agent);
        if (n.body != 0) g.game.commands().release(n.body);
        n.agent = 0; n.body = 0;
        g.interact.cancel(n, "despawned");
    }

    // ------------------------------------------------------------------ steering the agent
    /** The one action this NPC wants now: its desire fact high, the others low (the engine chooses, with inertia). */
    public void desire(Npc n, int action) {
        if (n.agent == 0) return;
        for (int i = 0; i < ACTIONS; i++) {
            float v = i == action ? 1f : i == A_IDLE ? 0.08f : 0.02f;
            if (n.facts[i] != v) { n.facts[i] = v; g.game.commands().agentFact(n.agent, i, v); }
        }
    }

    public void target(Npc n, long body) {
        if (n.agent == 0 || n.targetBody == body) return;
        n.targetBody = body;
        g.game.commands().agentTarget(n.agent, body, 0, 0, 0);
    }

    public void combatGoal(Npc n, float x, float z) {
        Actor target = g.actor(n.fightTarget);
        float y = target == null ? n.y : target.y;
        if (!n.hasGoalPos || Math.hypot(n.gx - x, n.gz - z) > 0.35 || Math.abs(n.gy - y) > 0.4f) {
            n.gx = x; n.gy = y; n.gz = z; n.hasGoalPos = true;
            g.game.commands().agentGoal(n.agent, x, y, z);
        }
        desire(n, A_COMBAT);
    }

    // ------------------------------------------------------------------ the frame
    private double watchAt;
    public void update(float dt) {
        if (g.scripted && System.getenv("RPG_WATCH") != null && g.time > watchAt) {
            watchAt = g.time + 0.5;
            for (Npc n : g.npcs) if (java.util.Arrays.asList(System.getenv("RPG_WATCH").split(",")).contains(n.name))
                System.out.printf("      [watch] " + n.name + " t %.1f %s at (%.2f, %.2f) v (%.2f, %.2f) goal (%.1f, %.1f) %s route %d inv %s last %s%n", g.time, n.goal, n.x, n.z, n.vx, n.vz, n.gx, n.gz, n.nativeAction, n.routeCells, n.inv.stacks(), n.lastOutcome);
        }
        for (Npc n : g.npcs) {
            if (!n.alive) { respawnCheck(n); continue; }
            if (!n.loaded) continue;
            g.game.commands().agentRate(n.agent, n.actionRate() * (n.cs.slowT > 0 ? 0.6f : 1));
            if (n.morphT > 0) {
                n.cs.cancel(); n.cs.blocking = false; n.sneaking = true;
                g.game.commands().moverInput(n.body, 0, 0, 0, 0, NPC_ACCEL, MOVER_CROUCH | MOVER_STANCE_ONLY);
                Actor attacker = g.actor(n.cs.lastAttacker);
                if (attacker != null && attacker.alive) { target(n, attacker.body); desire(n, A_FLEE); } else desire(n, A_HOLD);
                continue;
            }
            stealth.update(n, dt);
            if (g.traversal.update(n, dt)) continue;
            if (n.reacting) continue;           // off balance in Sydney's hands: no thinking, no walking, until back on their feet
            if (g.time >= n.nextThink) { brain.think(n); decisions++; }
            Actor elevated = n.goal == Brain.Goal.FIGHT ? g.actor(n.fightTarget) : null;
            if (elevated != null && elevated.y > n.y + 0.75f && g.traversal.begin(n, (float) Math.atan2(-(elevated.x - n.x), -(elevated.z - n.z))) && g.traversal.update(n, dt)) continue;
            brain.act(n, dt);
            if (n.stuck && (n.goal == Brain.Goal.FIGHT || n.goal == Brain.Goal.FLEE || n.goal == Brain.Goal.RETURN) && n.hasGoalPos) {
                float heading = (float) Math.atan2(-(n.gx - n.x), -(n.gz - n.z));
                if (g.traversal.begin(n, heading) && g.traversal.update(n, dt)) continue;
            }
            g.game.commands().moverInput(n.body, 0, 0, 0, 0, NPC_ACCEL, MOVER_STANCE_ONLY | (n.sneaking && n.goal != Brain.Goal.COLLECT ? MOVER_CROUCH : 0));
            face(n, dt);
            if (n.speech != null && g.time > n.speechUntil) n.speech = null;
            footsteps(n, dt);
        }
        rumors.update();
        abstractSim();
    }

    /**
     * Where the body faces, turned at a body's pace (it never snaps round): the way the mind wants it to face when it has
     * a reason (a foe, a tree being cut, someone it talks to), else the way it is walking, else the way its agent looks.
     * The head looks a little further: at the player when they are close and in sight, at a foe always.
     */
    private void face(Npc n, float dt) {
        float k = 1 - (float) Math.exp(-dt / 0.12f);
        n.svx += (n.vx - n.svx) * k; n.svz += (n.vz - n.svz) * k;
        float speed = (float) Math.hypot(n.svx, n.svz);
        if (speed > 0.8f && n.goal != Brain.Goal.FIGHT) n.wantYaw = Float.NaN;    // walking off: whatever held it is done
        // standing with nothing to face, the body keeps its heading - unless its agent holds it facing something
        float target = !Float.isNaN(n.wantYaw) ? n.wantYaw : speed > 0.4f ? (float) Math.atan2(-n.svx, -n.svz) : n.nativeAction.startsWith("hold") ? n.agentYaw : n.yaw;
        // a heading within a hair of the present one is no reason to turn (an agent's facing wobbles by a degree or two)
        if (Math.abs(Actor.wrap(target - n.yaw)) < 0.03f) target = n.yaw;
        float rate = n.goal == Brain.Goal.FIGHT ? 9f : speed > 3f ? 4.5f : 6.5f;
        n.turnToward(target, rate, dt);
        Actor focus = n.fightTarget != 0 ? g.actor(n.fightTarget) : null;
        if (focus == null && n.distTo(g.player) < 4.5f && g.player.alive && Math.abs(Actor.wrap((float) Math.atan2(-(g.player.x - n.x), -(g.player.z - n.z)) - n.yaw)) < 1.4f) focus = g.player;
        float look = focus != null ? (float) Math.atan2(-(focus.x - n.x), -(focus.z - n.z)) : n.yaw;
        n.aimYaw = n.yaw + Math.max(-1.1f, Math.min(1.1f, Actor.wrap(look - n.yaw)));
    }

    /** Footfalls near the player, one per planted foot, in time with the gait the figure is drawn walking. */
    private void footsteps(Npc n, float dt) {
        float speed = (float) Math.hypot(n.vx, n.vz);
        if (!n.grounded || speed < rpg.actor.Gait.STILL || g.sfx == null || n.distTo(g.player) > 16) { n.stepClock = 0.5f; return; }
        n.stepClock += dt * rpg.actor.Gait.steps(speed, n.sneaking);
        if (n.stepClock >= 1) { n.stepClock -= 1; g.sfx.footstep(n, Math.min(1, speed / 5f)); }
    }

    /** After the frame: where the engine put each body, what its agent is doing. */
    public void afterFrame(Snapshots.Bodies bodies, Snapshots.Agents agents) {
        for (Npc n : g.npcs) {
            // a body to search lies where the ragdoll lies
            if (!n.alive && n.ragdoll != 0) {
                Snapshots.Ragdoll rd = bodies.ragdoll(n.ragdoll);
                if (rd != null) {
                    if(n.posePts==null || n.posePts.length!=rd.p.length)n.posePts=new float[rd.p.length];
                    System.arraycopy(rd.p,0,n.posePts,0,rd.p.length); n.poseAt=g.time;
                    n.x = rd.comX; n.z = rd.comZ;
                    float low = rd.p[1]; for (int k = 1; k < RD_PARTICLES; k++) low = Math.min(low, rd.p[k * 3 + 1]);
                    n.y = Math.max(0, low - 0.1f);
                    if (n.corpse != 0 && g.objects.get(n.corpse) instanceof rpg.objects.Corpse c) { c.x = rd.comX; c.z = rd.comZ; c.y = n.y + 0.2f; }
                    // the dead stay dead (gamefixes): a body of the dead that Sydney reports as anything but dying or done -
                    // standing, stepping, getting up - is told again, at once, that it is dead
                    if (rd.state != SYDNEY_DEAD && rd.outcome != OUTCOME_DEAD) { g.game.commands().ragdollControl(n.ragdoll, CONTROL_DIE, 1); deathsReasserted++; }
                    n.x = rd.comX; n.z = rd.comZ;
                }
            }
            if (!n.alive || !n.loaded) continue;
            Snapshots.Mover m = bodies.mover(n.body);
            if (m != null) { n.x = m.x; n.y = m.y; n.z = m.z; n.vx = m.vx; n.vz = m.vz; n.grounded = m.grounded; n.crouched = m.crouched; }
            Snapshots.Agent a = agents.agent(n.agent);
            if (a != null) {
                n.agentYaw = a.yaw;
                n.routeCells = a.routeCells;
                n.stuck = a.stuck;
                n.nativeAction = a.action >= 0 && a.action < ACTIONS ? ACTION_NAMES[a.action] : "-";
            }
        }
    }

    // ------------------------------------------------------------------ engine events
    public void agentAction(long agent, int action, int previous) { decisions++; }
    public void agentSees(long agent, long other) { Npc n = byAgent(agent); if (n != null) { n.seesTarget = true; sightings++; } }
    public void agentLost(long agent, long other) { Npc n = byAgent(agent); if (n != null) n.seesTarget = false; }
    public void agentArrived(long agent, long target) { arrivals++; }
    public void agentHeard(long agent, int loudness) { Npc n = byAgent(agent); if (n != null) { heardAt.put(n.id, g.time); heardEvents++; } }
    public void agentStuck(long agent) {
        Npc n = byAgent(agent);
        if (n == null) return;
        stuckEvents++;
        if (g.scripted) System.out.printf("      [stuck] %s %s at (%.2f, %.2f, %.2f) -> (%.1f, %.1f), %s, v (%.2f, %.2f) ground %s route %d facts %s%n", n.name, n.goal, n.x, n.y, n.z, n.gx, n.gz, n.nativeAction, n.vx, n.vz, n.grounded, n.routeCells, java.util.Arrays.toString(n.facts));
        n.lastOutcome = "route blocked while " + n.goal.toString().toLowerCase() + "; replanning";
        n.remember(new Memory("BLOCKED", 0, 0, DevMap.jurisdiction(n.gx, n.gz), g.state.minutes, 1, "personal", 0, 0, String.format("could not reach (%.0f, %.0f)", n.gx, n.gz)), 24);
        g.state.commit("NpcStuck", n.id, 0, DevMap.jurisdiction(n.x, n.z), 0, n.goal + String.format(" to (%.1f, %.1f)", n.gx, n.gz));
        n.hasGoalPos = false;
        n.legs = null;   // plan the trip again from where it stands
        n.nextThink = 0;
        if (n.goal == Brain.Goal.PATROL) n.patrolIndex++;
        if (n.goal == Brain.Goal.WORK) g.interact.cancel(n, "stuck");
    }

    public double lastHeard(Npc n) { return heardAt.getOrDefault(n.id, -99.0); }

    private Npc byAgent(long agent) { for (Npc n : g.npcs) if (n.agent == agent) return n; return null; }

    // ------------------------------------------------------------------ perception and judgement
    /** Whether an NPC can see an actor: plainly (in its eyes' cone, lines clear, lit enough), or at all once alert to them (Stealth). */
    public boolean canSee(Npc n, Actor a) {
        if (!n.alive || !n.loaded || a == null || !a.alive) return false;
        return stealth.sees(n, a);
    }

    /** Everyone who can see an act happen right now (the culprit excluded). */
    public List<Npc> witnesses(Actor culprit) {
        List<Npc> l = new ArrayList<>();
        for (Npc n : g.npcs) if (n != culprit && n.alive && n.loaded && Brain.bystander(n) && canSee(n, culprit)) l.add(n);
        return l;
    }

    /** Who this NPC must deal with now (fight or flee), or null. */
    public Actor threatFor(Npc n) {
        if (n.summonOwner != 0) {
            Actor owner = g.actor(n.summonOwner);
            if (owner == null || !owner.alive || g.law.inCustody(owner)) return null;
            Actor target = owner instanceof Npc npc ? g.actor(npc.fightTarget) : g.time-owner.cs.lastHurt<15?g.actor(owner.cs.lastAttacker):null;
            if (target == null || !target.alive || target == owner || target.summonOwner == owner.id || target.team == owner.team) {
                target = null;
                for (Actor candidate : g.actors()) if (candidate.alive && candidate != owner && candidate != n && candidate.cs.lastAttacker == owner.id && g.time-candidate.cs.lastHurt<15 && !g.law.inCustody(candidate) && candidate.team != owner.team && n.distTo(candidate) < 16 && canSee(n,candidate)) { target = candidate; break; }
            }
            return target != null && n.distTo(target) < 20 && canSee(n,target) ? target : null;
        }

        boolean law = n.role == Npc.Role.GUARD || n.role == Npc.Role.LAWMAN;
        // nobody knows where a foe is unless their senses say so: in sight (or out of it for a heartbeat, behind a post)
        // it is still the fight; lost for longer, all that is left is where they were last placed - a search (Hunt)
        // charmed (Charm Person): no lifted hand against the player while it lasts
        if (n.charmUntil > g.time && n.fightTarget == g.player.id) { n.fightTarget = 0; n.cs.cancel(); }
        if (n.charmUntil > g.time && n.hostileToPlayer) return null;
        if (n.fightTarget != 0 && alliedWatch(n, g.actor(n.fightTarget))) { n.fightTarget = 0; n.hunt = null; n.cs.cancel(); }
        if (n.cs.lastAttacker != 0 && g.time - n.cs.lastHurt < 0.6) {
            Actor recent = g.actor(n.cs.lastAttacker);
            if (recent != null && recent.alive && !alliedWatch(n, recent) && !g.law.inCustody(recent) && !(law && g.law.settled(recent.id, n.cs.lastHurt))) { n.fightTarget = recent.id; return recent; }
        }
        if (n.fightTarget != 0) {
            Actor t = g.actor(n.fightTarget);
            boolean unwelcome = t != null && !ZonePolicy.routineAllowed(n, t.x, t.z) && !(n.cs.lastAttacker == t.id && g.time - n.cs.lastHurt < 12);
            if (unwelcome && n.hunt != null && n.hunt.target == t.id) n.hunt = null;
            boolean stood = unwelcome || g.law.inCustody(t) || law && (t != null && g.law.settled(t.id, n.goalSince) && g.law.bounty(t.id, n.homeJurisdiction) <= 0 && !(n.cs.lastAttacker == t.id && !g.law.settled(t.id, n.cs.lastHurt)));
            if (t != null && t.alive && n.distTo(t) < 30 && !(t == g.player && t.hp <= 0) && !stood) {
                if (placed(n, t)) return t;
                lose(n, t, "lost sight of " + t.name);
            }
            n.fightTarget = 0;
        }
        Actor p = g.player;
        if (n.cs.lastAttacker != 0 && g.time - n.cs.lastHurt < 12) {
            Actor t = g.actor(n.cs.lastAttacker);
            // a blow struck before the striker's arrest is answered by the sentence, not by the guard
            if (t != null && t.alive && !alliedWatch(n, t) && !g.law.inCustody(t) && !(law && g.law.settled(t.id, n.cs.lastHurt))) {
                if (placed(n, t) || g.time - n.cs.lastHurt < 0.6) return t;    // a blow just taken: its striker is right there
                if (n.combatant()) lose(n, t, "struck by someone unseen");
            }
        }
        // searching for whoever did it (a body, blood): someone in sight with blood on them, close to it, is a suspect
        if (n.hunt != null && n.hunt.target == 0 && g.gore != null) for (Actor a : g.actors()) {
            if (a == n || !a.alive || a.dummy || law && g.law.inCustody(a) || a instanceof rpg.animal.Animal || a.bloodied < 0.25f || !canSee(n, a)) continue;
            if (g.gore.suspect(n, a)) { n.hunt = null; n.fightTarget = a.id; return a; }
        }
        // a foe being searched for, found again
        if (n.hunt != null && n.hunt.target != 0) {
            Actor t = g.actor(n.hunt.target);
            if (t != null && t.alive && !g.law.inCustody(t) && ZonePolicy.routineAllowed(n, t.x, t.z) && !(law && (g.law.settled(t.id, n.hunt.startedAt) && g.law.bounty(t.id, n.homeJurisdiction) <= 0)) && canSee(n, t)) { callOut(n, t); return t; }
        }
        if (n.hostileToPlayer && !g.law.inCustody(p) && ZonePolicy.routineAllowed(n, p.x, p.z) && p.alive && n.distTo(p) < n.aggroRange && canSee(n, p)) return p;
        // a dangerous animal close by: guards deal with it, everyone else gets away from it
        if (g.animals != null) for (rpg.animal.Animal an : g.animals.all())
            if (an.alive && an.loaded && an.dangerous() && n.distTo(an) < (n.combatant() ? 14 : 8) && canSee(n, an)) return an;
        if (n.role == Npc.Role.GUARD || n.role == Npc.Role.LAWMAN) {
            for (Actor a : g.actors()) {
                if (a == n || !a.alive || a.dummy || alliedWatch(n,a)) continue;
                if (g.law.bounty(a.id, n.homeJurisdiction) > 0 && ZonePolicy.routineAllowed(n, a.x, a.z) && !g.law.inCustody(a) && n.distTo(a) < (n.role == Npc.Role.LAWMAN ? 6 : 24) && canSee(n, a)) return a;
            }
        }
        if (!n.combatant()) {
            for (Npc o : g.npcs) if (o != n && o.alive && o.loaded && o.goal == Brain.Goal.FIGHT && o.hostileToPlayer && n.distTo(o) < 8 && canSee(n, o)) return o;
            Memory m = n.crimeKnown(p.id);
            if (n.role == Npc.Role.VILLAGER && m != null && !m.settled && m.witnessed() && n.distTo(p) < 2.5f && p.alive) return p;
        }
        return null;
    }

    /** Whether the NPC can place a foe right now: seen, or seen a heartbeat ago (stepping behind a post does not make anyone vanish). */
    boolean placed(Npc n, Actor t) {
        if (canSee(n, t)) return true;
        Stealth.Aware w = stealth.of(n, t);
        return w != null && g.time - w.seenAt < 1.2;
    }

    /** Where the NPC last placed an actor (seen or heard), or null when it never has. */
    public float[] lastKnown(Npc n, Actor t) {
        Stealth.Aware w = stealth.of(n, t);
        if (w == null) return null;
        return new float[]{w.x, w.z};
    }

    /**
     * A foe slips from the NPC's senses: it keeps only their last known location and sets out to search there (a
     * combatant does; anyone else is only glad they are gone). Guards of the same force close by join the search.
     */
    public void lose(Npc n, Actor t, String why) {
        if (!n.combatant() && n.role != Npc.Role.LAWMAN || t == n) return;
        if (n.hunt != null && n.hunt.target == t.id) return;
        Stealth.Aware w = stealth.of(n, t);
        float x = w != null ? w.x : t.x, z = w != null ? w.z : t.z;
        if (w == null) {
            // never placed by its own senses (struck from the dark): only roughly where the blow came from
            x = n.x + (t.x - n.x) * 0.6f; z = n.z + (t.z - n.z) * 0.6f;
        }
        double clue = w == null ? g.time : Math.max(w.seenAt, w.heardAt);
        float patience = n.role == Npc.Role.GUARD ? 55 : n.role == Npc.Role.LAWMAN ? 25 : 22;
        Hunt h = new Hunt(t.id, why, x, z, g.time, patience);
        h.clueAt = Math.min(g.time, clue);
        h.vx = t.vx; h.vz = t.vz;
        if (w == null || g.time - w.seenAt > 2.0) { h.vx = 0; h.vz = 0; }   // where they were going is only known from watching them go
        n.hunt = h;
        n.nextThink = 0;
        say(n, n.hostileToPlayer ? "Where'd you go?" : n.role == Npc.Role.GUARD ? "Lost them - spread out!" : "Where did they go?");
        g.state.commit("SearchStarted", n.id, t.id, DevMap.jurisdiction(x, z), 0, String.format("%s; last known at (%.1f, %.1f)", why, x, z));
        // the force searches together: guards close by who are free take up the same search
        if (n.role == Npc.Role.GUARD || n.role == Npc.Role.LAWMAN) for (Npc o : g.npcs) {
            if (o == n || o == t || !o.alive || !o.loaded || o.hunt != null || o.goal == Brain.Goal.FIGHT) continue;
            if (o.role != Npc.Role.GUARD || !o.homeJurisdiction.equals(n.homeJurisdiction) || o.distTo(n) > 35) continue;
            Hunt j = new Hunt(t.id, n.name + " lost " + t.name, x, z, g.time, o.role == Npc.Role.GUARD ? 55 : 25);
            j.clueAt = h.clueAt; j.vx = h.vx; j.vz = h.vz; j.joined = true;
            o.hunt = j;
            o.nextThink = 0;
            g.state.commit("SearchJoined", o.id, t.id, DevMap.jurisdiction(x, z), 0, "with " + n.name);
        }
    }

    /** Found again: the NPC shouts it, and everyone searching for the same one hears where (a shout carries). */
    void callOut(Npc n, Actor t) {
        Hunt mine = n.hunt;
        if (mine == null || mine.target != t.id) return;
        n.hunt = null;
        n.fightTarget = t.id;
        say(n, n.hostileToPlayer ? "There you are!" : "Over here!");
        g.state.commit("SearchFound", n.id, t.id, DevMap.jurisdiction(t.x, t.z), 0, String.format("found at (%.1f, %.1f)", t.x, t.z));
        g.noise(n, n.x, n.eyeY(), n.z, 20, 0.8f);
        for (Npc o : g.npcs) {
            if (o == n || o.hunt == null || o.hunt.target != t.id || o.distTo(n) > 30) continue;
            o.hunt.clue(t.x, t.z, t.vx, t.vz, g.time);
            o.nextThink = 0;
        }
    }

    /**
     * Keeps a search up to date from the NPC's own senses (a glimpse or a footstep moves it), drops it when it no longer
     * makes sense, and gives it up when the trail has gone cold. Called as the NPC thinks.
     */
    void keepHunt(Npc n) {
        Hunt h = n.hunt;
        if (h == null) return;
        Actor t = h.target != 0 ? g.actor(h.target) : null;
        boolean law = n.role == Npc.Role.GUARD || n.role == Npc.Role.LAWMAN;
        if (h.target != 0 && (t == null || !t.alive || law && t == g.player && g.law.bounty(t.id, n.homeJurisdiction) <= 0 && g.law.settled(t.id, h.startedAt)
                || law && g.law.inCustody(t))) { n.hunt = null; return; }
        if (t != null) {
            Stealth.Aware w = stealth.of(n, t);
            if (w != null && Math.max(w.seenAt, w.heardAt) > h.clueAt + 0.05) h.clue(w.x, w.z, w.seenAt >= w.heardAt ? t.vx : 0, w.seenAt >= w.heardAt ? t.vz : 0, Math.max(w.seenAt, w.heardAt));
        }
        // a bandit does not chase far from its camp; a lawman does not leave his post for long
        boolean leash = n.hostileToPlayer && Math.hypot(h.x - n.spawnX, h.z - n.spawnZ) > 26 || n.role == Npc.Role.LAWMAN && Math.hypot(h.x - n.postX, h.z - n.postZ) > 22;
        if (h.expired(g.time) || leash) {
            n.hunt = null;
            n.vigilantUntil = g.time + 90;
            say(n, leash ? "Not worth it." : h.target == 0 ? "Whoever did this is long gone." : n.role == Npc.Role.GUARD ? "Lost them. Keep your eyes open." : "Must have got away.");
            g.state.commit("SearchAbandoned", n.id, h.target, DevMap.jurisdiction(n.x, n.z), 0, String.format("after %.0fs, %d spots checked%s", g.time - h.startedAt, h.checked, leash ? " (too far)" : ""));
        }
    }

    /** One area being searched: a centre, how far it reaches, who is sought, and how many are searching it. */
    public record SearchArea(float x, float z, float r, long target, int searchers, String jurisdiction) {}

    /** The areas NPCs are searching right now (searches for the same quarry close together are one area). */
    public List<SearchArea> searchAreas() {
        List<SearchArea> out = new ArrayList<>();
        for (Npc n : g.npcs) {
            if (!n.alive || !n.loaded || n.hunt == null) continue;
            Hunt h = n.hunt;
            float r = h.radius(g.time);
            boolean merged = false;
            for (int i = 0; i < out.size(); i++) {
                SearchArea a = out.get(i);
                if (a.target() != h.target || Math.hypot(a.x() - h.x, a.z() - h.z) > a.r() + r) continue;
                float cx = (a.x() * a.searchers() + h.x) / (a.searchers() + 1), cz = (a.z() * a.searchers() + h.z) / (a.searchers() + 1);
                float rr = Math.max(a.r(), r) + (float) Math.hypot(cx - h.x, cz - h.z) * 0.5f;
                out.set(i, new SearchArea(cx, cz, Math.min(18, rr), h.target, a.searchers() + 1, a.jurisdiction()));
                merged = true;
                break;
            }
            if (!merged) out.add(new SearchArea(h.x, h.z, r, h.target, 1, n.homeJurisdiction));
        }
        return out;
    }

    public static boolean alliedWatch(Actor a, Actor b) {
        return a instanceof Npc n && b instanceof Npc m && watch(n) && watch(m) && n.homeJurisdiction.equals(m.homeJurisdiction);
    }
    private static boolean watch(Npc n) { return n.role == Npc.Role.GUARD || n.role == Npc.Role.LAWMAN; }

    /** Whether a may strike v though they share a team (an NPC fighting its chosen opponent). */
    public boolean hostile(Actor a, Actor v) {
        if (alliedWatch(a, v)) return false;
        if (a instanceof Npc n && n.charmUntil > g.time && v == g.player || v instanceof Npc m && m.charmUntil > g.time && a == g.player) return false;
        return a instanceof Npc n && n.fightTarget == v.id || v instanceof Npc m && m.fightTarget == a.id;
    }

    /** Someone struck: combatants turn on the attacker, everyone else remembers it. */
    public void onAttacked(Actor victim, Actor attacker) {
        if (alliedWatch(victim, attacker)) return;
        if (victim instanceof Npc n && n.alive) {
            if (n.combatant()) { n.fightTarget = attacker.id; n.nextThink = 0; }
            if (!n.combatant()) n.nextThink = 0;
        }
    }

    /** A bystander saw something alarming: they turn to look, and remember. */
    public void notice(Npc n, Actor who, String what) {
        n.wantYaw = (float) Math.atan2(-(who.x - n.x), -(who.z - n.z));
        say(n, "Hey! What do you think you're doing?");
        n.nextThink = 0;
    }

    public Npc lawmanFor(String jurisdiction) {
        long id = DevMap.ALPHA.equals(jurisdiction) ? Ids.LAWMAN_A : DevMap.BETA.equals(jurisdiction) ? Ids.LAWMAN_B : -1;
        Actor a = g.actor(id);
        return a instanceof Npc n && n.alive ? n : null;
    }

    /** The nearest guard (or lawman) of this place free to be told something - however far, if it is their own town; or null. */
    public Npc guardFor(Npc n) {
        Npc best = null;
        float bd = 160;
        String jur = DevMap.jurisdiction(n.x, n.z);
        for (Npc o : g.npcs) {
            if (o == n || !o.alive || !o.loaded || !(o.role == Npc.Role.GUARD || o.role == Npc.Role.LAWMAN) || o.goal == Brain.Goal.FIGHT) continue;
            if (!o.homeJurisdiction.equals(jur) && !o.homeJurisdiction.equals(n.homeJurisdiction)) continue;
            // (a guard is the one to fetch: the lawman keeps his chancery, and is fetched only when there is no guard)
            float d = n.distTo(o) + (o.role == Npc.Role.LAWMAN ? 100 : 0);
            if (d < bd) { bd = d; best = o; }
        }
        return best;
    }

    public Npc partnerFor(Npc n) {
        Npc best = null;
        float bd = 30;
        for (Npc o : g.npcs) {
            if (o == n || !o.alive || !o.loaded || o.combatant() || o.role == Npc.Role.THIEF && n.role != Npc.Role.THIEF) continue;
            if (o.goal == Brain.Goal.FIGHT || o.goal == Brain.Goal.FLEE || o.goal == Brain.Goal.SLEEP || o.goal == Brain.Goal.REPORT_CRIME) continue;
            float d = n.distTo(o);
            if (d < bd) { bd = d; best = o; }
        }
        return best;
    }

    /** Passes news through the same hearing, evidence and feedback rules as street gossip. */
    public int share(Npc from, Npc to) {
        int n = 0;
        for (Memory m : new ArrayList<>(from.memories)) {
            if (rumors.tell(from, to, m, false)) n++;
        }
        return n;
    }

    public String smallTalk(Npc n, Npc p) {
        for (Memory m : n.memories) if (m.crime() && m.subject != p.id) return "Did you hear? " + g.nameOf(m.subject) + " - " + m.kind.toLowerCase() + "!";
        return g.dialogue.greeting(n);
    }

    public void say(Npc n, String text) { n.speech = text; n.speechUntil = g.time + 3.5; }

    /** An answer to something said nearby: shown a beat later, so the two lines read as a conversation. */
    public void reply(Npc n, String text) { n.speech = text; n.speechUntil = g.time + 4.5; }

    /** Word of mouth (the rumor system). */
    public final Rumors rumors;

    /**
     * How bold this person is (0 .. 1): their calling first (a guard or an outlaw is bold, a shopkeeper is not), then
     * their own nature - the same calling is not the same person twice.
     */
    public float bravery(Npc n) {
        float base = switch (n.role) {
            case GUARD, KNIGHT -> 0.9f; case LAWMAN -> 0.85f; case BANDIT, BRUTE -> 0.95f; case UNDERTAKER -> 0.7f; case WORKER, FARMER, MINER, CRAFTSMAN -> 0.6f;
            case CARTER -> 0.55f; case THIEF -> 0.5f; case FENCE -> 0.45f; case VILLAGER, BEGGAR, HEALER, PRIEST, HUNTER, SCHOLAR, FISHERMAN -> 0.4f; case QUEST_GIVER -> 0.35f; case CONTACT -> 0.3f; case MERCHANT -> 0.3f;
        };
        return Math.max(0, Math.min(1, base + ((n.id * 2654435761L >>> 20) % 21 - 10) / 100f));
    }

    /** How good a moment it is for the thief: someone nearby who could see would spoil it. */
    public float stealOpportunity(Npc thief) {
        var stall = g.objects.stall;
        if (stall.goods.isEmpty() || thief.count(Items.BREAD) >= 3) return 0;
        for (Npc o : g.npcs) {
            if (o == thief || !o.alive || !o.loaded || !Brain.bystander(o)) continue;
            if (Math.hypot(o.x - stall.x, o.z - stall.z) < 16 && g.vox.sight(o.x, o.eyeY(), o.z, stall.x, stall.y + 0.5f, stall.z)) return 0.1f;
        }
        if (Math.hypot(g.player.x - stall.x, g.player.z - stall.z) < 10) return 0.15f;
        return 0.62f;
    }

    public Node nearestNode(Npc n, String def) {
        Node best = null;
        float bd = Float.MAX_VALUE;
        for (Node node : g.objects.nodes) {
            if (!node.def.equals(def) || node.depleted() || n.skills.level(node.skill) < node.level) continue;
            // the tool must be one they carry (or hold): a woodcutter takes his axe in hand when he gets there
            if (node.tool(n) == null && !carriesTool(n, node)) continue;
            boolean busy = false;
            for (var w : g.interact.work.values()) if (w.target == node && w.actor != n) busy = true;
            if (busy) continue;
            float d = (float) Math.hypot(node.x - n.x, node.z - n.z);
            if (d < bd) { bd = d; best = node; }
        }
        return best;
    }

    /** A free cell beside a node to stand in while working it. */
    /** Whether an NPC carries (or holds) the tool a node is worked with. */
    static boolean carriesTool(Npc n, Node node) {
        if (node.kind == Node.Kind.PATCH) return true;
        for (var grid : n.grids()) for (ItemStack s : grid.stacks()) if (node.kind == Node.Kind.FISH ? s.def == node.needs : s.def.toolSkill == node.skill && !(node.kind == Node.Kind.TREE && s.def.twoHanded())) return true;
        return n.main() != null && (node.kind == Node.Kind.FISH ? n.main() == node.needs : n.main().toolSkill == node.skill);
    }

    public float[] standSpot(Npc n, Node node) {
        int[][] sides = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        float[] best = {node.x + 1, node.z};
        float bd = Float.MAX_VALUE;
        for (int[] s : sides) {
            int cx = node.bx + s[0], cz = node.bz + s[1];
            if (g.vox.solid(cx, DevMap.FLOOR, cz) || g.vox.solid(cx, DevMap.FLOOR + 1, cz) || !g.vox.solid(cx, DevMap.FLOOR - 1, cz)) continue;
            float d = (float) Math.hypot(cx + 0.5f - n.x, cz + 0.5f - n.z);
            if (d < bd) { bd = d; best = new float[]{cx + 0.5f, cz + 0.5f}; }
        }
        return best;
    }

    // ------------------------------------------------------------------ death, respawn, unloading
    public boolean ensureCorpsePhysical(Npc dead) {
        if(dead.alive || dead.corpse==0 || !(g.objects.get(dead.corpse) instanceof rpg.objects.Corpse corpse) || corpse.removed)return false;
        if(dead.ragdoll!=0 && g.lastBodies.ragdoll(dead.ragdoll)==null && g.time-dead.reactSince>0.5)dead.ragdoll=0;
        if(dead.ragdoll!=0)return true;
        dead.ragdoll=g.game.reserve(KIND_RAGDOLL);dead.reactSince=g.time;
        g.game.commands().ragdollCreate(dead.ragdoll,1,SYDNEY_HUMAN,RAGDOLL_ACTIVE,dead.x,dead.y,dead.z,dead.yaw,0,0,0);
        if(dead.posePts!=null)g.game.commands().ragdollPose(dead.ragdoll,0,0,0,dead.posePts);
        g.game.commands().ragdollControl(dead.ragdoll,CONTROL_DIE,1);
        if(g.gore!=null)g.gore.limbsToBody(dead);return true;
    }

    public void onKilled(Npc n, Actor killer) {
        if (n.summonOwner != 0) { g.magic.dismiss(n); return; }
        n.diedAt = g.time; n.reactSince=g.time;
        n.goal = Brain.Goal.IDLE;
        n.fightTarget = 0;
        // the body falls as a ragdoll; the mind and the mover are let go. A body already reeling in Sydney's hands is the
        // same body, going limp; otherwise one is made in the pose the figure was last drawn in, moving as it was
        float dx = killer == null ? 0 : n.x - killer.x, dz = killer == null ? 0 : n.z - killer.z, l = Math.max(0.01f, (float) Math.hypot(dx, dz));
        if (g.reactions == null || !g.reactions.died(n)) {
            n.ragdoll = g.game.reserve(KIND_RAGDOLL);
            boolean posed = n.posePts != null && g.time - n.poseAt < 0.5;
            g.game.commands().ragdollCreate(n.ragdoll, 1, SYDNEY_HUMAN, RAGDOLL_ACTIVE | (posed ? 0 : RAGDOLL_STAND), n.x, n.y, n.z, n.yaw, 0, 0, 0);
            if (posed) g.game.commands().ragdollPose(n.ragdoll, n.vx, 0, n.vz, n.posePts);
            g.game.commands().ragdollControl(n.ragdoll, CONTROL_DIE, 1);
        }
        if (g.gore != null) g.gore.limbsToBody(n);
        g.game.commands().ragdollHit(n.ragdoll, RD_NECK, dx / l, 0.2f, dz / l, 120, 30);
        despawnBody(n);
        // what they carried stays on the body, to be searched (worn things stay worn: the search mirrors them); an outlaw's
        // is anyone's, anyone else's is still theirs. What will not fit lies beside the body; bones are left
        boolean outlaw = n.role == Npc.Role.BANDIT || n.role == Npc.Role.BRUTE;
        rpg.objects.Corpse corpse = g.objects.add(new rpg.objects.Corpse(g.state.newEntityId(), n.name, n.id, n.x, DevMap.FLOOR + 0.2f, n.z, outlaw ? 0 : n.id));
        for (int i = 0; i < n.inv.size(); i++) {
            ItemStack s = n.inv.get(i);
            if (s == null) continue;
            n.inv.set(i, null);
            if (!corpse.contents.add(s)) g.objects.drop(s, n.x + 0.4f, DevMap.FLOOR + 0.05f, n.z - 0.3f);
        }
        n.corpse = corpse.id;
        if (killer == g.player && g.honor != null) g.honor.corpseLoot(n, corpse);
        if (n.role == Npc.Role.BANDIT || n.role == Npc.Role.BRUTE) {
            ItemStack w = n.eq.get(ItemDef.Slot.MAIN);
            if (w != null && n.role == Npc.Role.BANDIT) { g.objects.drop(w, n.x + 0.4f, DevMap.FLOOR + 0.05f, n.z); n.eq.set(ItemDef.Slot.MAIN, null); }
        }
        ItemDef bones = n.role == Npc.Role.BRUTE ? Items.BIG_BONES : Items.BONES;
        g.state.mint(bones, 1);
        g.objects.drop(new ItemStack(bones, 1), n.x, DevMap.FLOOR + 0.05f, n.z + 0.3f);
    }

    /** Hostile test NPCs come back after a while (fixtures); townsfolk do not - their deaths are permanent. */
    private void respawnCheck(Npc n) {
        if (n.role != Npc.Role.BANDIT && n.role != Npc.Role.BRUTE) return;
        if (n.carriedBy != 0 || n.diedAt < 0 || g.time - n.diedAt < 60) return;
        revive(n, true);
    }

    public void revive(Npc n, boolean atSpawn) {
        if (n.ragdoll != 0) { g.game.commands().release(n.ragdoll); n.ragdoll = 0; }
        // the old body goes; whatever was left in its pack lies where it lay
        if (n.corpse != 0 && g.objects.get(n.corpse) instanceof rpg.objects.Corpse c && !c.removed) {
            for (ItemStack s : c.contents.stacks()) g.objects.drop(s, c.x, DevMap.FLOOR + 0.05f, c.z);
            c.contents.clear();
            c.removed = true;
        }
        n.corpse = 0;
        if (atSpawn) { n.x = n.spawnX; n.z = n.spawnZ; }
        n.alive = true; n.diedAt = -1; n.restore();
        n.hostileToPlayer = n.role == Npc.Role.BANDIT || n.role == Npc.Role.BRUTE;   // (an outlaw comes back an outlaw)
        if (g.gore != null) g.gore.healed(n); n.cs.cancel(); n.cs.staggered = 0; n.cs.lastAttacker = 0; n.fightTarget = 0;
        n.goal = Brain.Goal.IDLE; n.nextThink = 0;
        if (n.role == Npc.Role.BANDIT && n.eq.get(ItemDef.Slot.MAIN) == null) {
            ItemDef w = Items.get(n.id == Ids.BANDIT_2 ? "shortbow" : "bronze_sword");
            n.eq.set(ItemDef.Slot.MAIN, new ItemStack(w, 1)); g.state.mint(w, 1);
        }
        if (n.id == Ids.BANDIT_2 && n.eq.get(ItemDef.Slot.AMMO) == null) { n.eq.set(ItemDef.Slot.AMMO, new ItemStack(Items.BRONZE_ARROWS, 30)); g.state.mint(Items.BRONZE_ARROWS, 30); }
        give(n, Items.COINS, 10);
        spawnBody(n);
        g.state.commit("ActorRespawned", 0, n.id, DevMap.jurisdiction(n.x, n.z), 0, n.name);
    }

    /** Unloads an NPC: its body goes, and its work is resolved abstractly from the same state and rules (GDD 3.8). */
    public void unload(Npc n) {
        if (!n.loaded || !n.alive) return;
        g.reactions.cancel(n);
        despawnBody(n);
        n.loaded = false;
        g.state.commit("NpcUnloaded", 0, n.id, DevMap.jurisdiction(n.x, n.z), 0, n.name + " now simulated abstractly");
    }

    public void reload(Npc n) {
        if (n.loaded || !n.alive) return;
        // where their abstract activity says they are
        if (n.role == Npc.Role.WORKER) {
            Node t = nearestNode(n, "tree");
            if (n.count(Items.LOGS) >= 5 || t == null) { float[] c = g.econ.counterSpot(g.econ.shopA); n.x = c[0]; n.z = c[1]; }
            else { float[] s = standSpot(n, t); n.x = s[0]; n.z = s[1]; }
        }
        spawnBody(n);
        g.state.commit("NpcReloaded", 0, n.id, DevMap.jurisdiction(n.x, n.z), 0, n.name + String.format(" at (%.1f, %.1f) with %s", n.x, n.z, n.inv.stacks()));
    }

    /** Abstract resolution for unloaded NPCs, one step every ten game minutes, through the same node and shop rules. */
    private void abstractSim() {
        while (g.state.minutes - abstractClock >= 10) {
            abstractClock += 10;
            for (Npc n : g.npcs) {
                if (n.loaded || !n.alive) continue;
                if (n.role == Npc.Role.WORKER) {
                    double h = (abstractClock % 1440) / 60.0;
                    if (h < 6 || h >= 22) continue;
                    if (n.count(Items.LOGS) >= 5) { int sold = g.econ.sellToShop(n, g.econ.shopA, Items.LOGS, n.count(Items.LOGS)); n.lastOutcome = "(abstract) sold " + sold + " logs"; continue; }
                    Node t = nearestNode(n, "tree");
                    if (t == null) { n.lastOutcome = "(abstract) no tree standing"; continue; }
                    for (int i = 0; i < 2 && !t.depleted(); i++) {
                        if (!n.canStore(new ItemStack(Items.LOGS, 1))) break;
                        t.stock--;
                        g.state.mint(Items.LOGS, 1);
                        n.store(new ItemStack(Items.LOGS, 1));
                        var ev = g.state.commit("ResourceGathered", n.id, t.id, null, 0, "Logs (abstract, " + t.stock + " left)");
                        g.prog.award(n, Skill.WOODCUTTING, 25, "abstract woodcutting", ev.id());
                        if (t.stock <= 0) { t.regrowAt = g.state.minutes + t.respawnMinutes; t.setBlocks(g, false); g.state.commit("NodeDepleted", 0, t.id, null, ev.id(), t.name); }
                    }
                    n.lastOutcome = "(abstract) cutting logs: " + n.count(Items.LOGS) + " held";
                }
            }
        }
    }

    public void syncAbstractClock() { abstractClock = Math.floor(g.state.minutes / 10) * 10; }
}
