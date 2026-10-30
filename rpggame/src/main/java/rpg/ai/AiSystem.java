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
    public static final int A_IDLE = 0, A_WANDER = 1, A_GOTO = 2, A_RUN = 3, A_APPROACH = 4, A_FLEE = 5, A_INVESTIGATE = 6, A_HOLD = 7, A_COMBAT = 8, A_SPRINT = 9, ACTIONS = 10;
    public static final String[] ACTION_NAMES = {"idle", "wander", "go to goal", "run to goal", "approach", "flee", "investigate", "hold/face", "combat move", "sprint to goal"};

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

    public AiSystem(Rpg g) { this.g = g; this.brain = new Brain(g, this); this.stealth = new Stealth(g); this.rumors = new Rumors(g, this); this.arms = new Arms(g, this); }
    /** Arms shown in the open, and who reacts to them how. */
    public final Arms arms;

    // ------------------------------------------------------------------ setting up
    public void init() {
        CommandBuffer c = g.game.commands();
        policy = g.game.reserve(KIND_POLICY);
        List<CommandBuffer.Action> acts = new ArrayList<>();
        acts.add(new CommandBuffer.Action(A_IDLE, ACT_IDLE).base(1).consider(IN_FACT0 + A_IDLE, CURVE_LINEAR, 0, 1, 1));
        acts.add(new CommandBuffer.Action(A_WANDER, ACT_WANDER).base(1).range(6).speed(g.cfg.walkSpeed * 0.8f).consider(IN_FACT0 + A_WANDER, CURVE_LINEAR, 0, 1, 1));
        acts.add(new CommandBuffer.Action(A_GOTO, ACT_MOVE_TO_GOAL).base(1).speed(g.cfg.walkSpeed).consider(IN_FACT0 + A_GOTO, CURVE_LINEAR, 0, 1, 1));
        acts.add(new CommandBuffer.Action(A_RUN, ACT_MOVE_TO_GOAL).base(1).speed(g.cfg.jogSpeed).consider(IN_FACT0 + A_RUN, CURVE_LINEAR, 0, 1, 1));
        acts.add(new CommandBuffer.Action(A_APPROACH, ACT_APPROACH).base(1).range(1.6f).speed(4.2f).consider(IN_FACT0 + A_APPROACH, CURVE_LINEAR, 0, 1, 1));
        acts.add(new CommandBuffer.Action(A_FLEE, ACT_FLEE).base(1).range(16).speed(6.2f).consider(IN_FACT0 + A_FLEE, CURVE_LINEAR, 0, 1, 1));
        acts.add(new CommandBuffer.Action(A_INVESTIGATE, ACT_INVESTIGATE).base(1).speed(2.2f).consider(IN_FACT0 + A_INVESTIGATE, CURVE_LINEAR, 0, 1, 1)
                .consider(IN_SINCE_HEARD, CURVE_INVERSE, 0, 15, 0.6f));
        acts.add(new CommandBuffer.Action(A_HOLD, ACT_HOLD).base(1).consider(IN_FACT0 + A_HOLD, CURVE_LINEAR, 0, 1, 1));
        acts.add(new CommandBuffer.Action(A_COMBAT, ACT_CUSTOM).base(1).speed(3.4f).consider(IN_FACT0 + A_COMBAT, CURVE_LINEAR, 0, 1, 1));
        acts.add(new CommandBuffer.Action(A_SPRINT, ACT_MOVE_TO_GOAL).base(1).speed(g.cfg.runSpeed * 0.9f).consider(IN_FACT0 + A_SPRINT, CURVE_LINEAR, 0, 1, 1));
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
        finn.skills.setLevel(Skill.THIEVING, 25); levels(finn, 10, 8, 8, 12); wear(finn, "bronze_dagger", null, "stone_body"); finn.pickpocketLevel = 15; give(finn, Items.COINS, 25);
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
        levels(b1, 12, 12, 10, 14); wear(b1, "bronze_sword", "wooden_shield", "stone_body", "stone_helm"); give(b1, Items.SHRIMP, 2); give(b1, Items.COINS, 15);
        Npc b2 = npc(Ids.BANDIT_2, "Bandit archer", Npc.Role.BANDIT, "Hostile test NPC (ranged)", bs[1][0] + 0.5f, bs[1][1] + 0.5f, 121);
        levels(b2, 8, 8, 8, 12); b2.skills.setLevel(Skill.ARCHERY, 15); wear(b2, "shortbow", null, "stone_body");
        b2.eq.set(ItemDef.Slot.AMMO, new ItemStack(Items.BRONZE_ARROWS, 40)); g.state.mint(Items.BRONZE_ARROWS, 40); give(b2, Items.COINS, 10);
        Npc brute = npc(Ids.BRUTE, "Brute", Npc.Role.BRUTE, "Hostile test NPC (heavy)", bs[2][0] + 0.5f, bs[2][1] + 0.5f, 122);
        levels(brute, 35, 40, 30, 45); wear(brute, "iron_mace", null, "chainmail_body", "chainmail_legs"); give(brute, Items.TROUT, 2); give(brute, Items.COINS, 60);
        brute.aggroRange = 9;
        // those with a bed sleep in it
        for (Npc n : g.npcs) { float[] bed = DevMap.bedOf(n.id); if (bed != null) { n.homeX = bed[0]; n.homeZ = bed[1]; } }
        for (Npc n : g.npcs) { n.restore(); n.social = (n.id % 7) / 10f; n.hunger = (n.id % 5) / 10f; spawnBody(n); }
    }

    private static final List<Long> ADDED_ROSTER=List.of(Ids.BOBWIN,Ids.BEGGAR,Ids.HEALER,Ids.PRIEST,Ids.HUNTER,Ids.SCHOLAR,Ids.FISHERMAN,Ids.MINER,Ids.CRAFTSMAN,Ids.KNIGHT,Ids.PREDATOR_HUNTER,Ids.TELLER,
            Ids.COOK,Ids.QUARRY_WORKER,Ids.BORDER_KNIGHT,Ids.SHELLFISH_GATHERER,Ids.RIVER_FISHERMAN,
            // 0.2.0: DevPalace's twenty, then the six at the forester's lodge
            Ids.EMPEROR,Ids.PRAETOR,Ids.LEGIONARY_1,Ids.LEGIONARY_2,Ids.LEGIONARY_3,Ids.LEGIONARY_4,Ids.SENATOR,Ids.MACELLUM,Ids.PRIESTESS,Ids.MEDICUS,
            Ids.PHILOSOPHER,Ids.STONEMASON,Ids.PALACE_BEGGAR,Ids.CITIZEN_1,Ids.CITIZEN_2,Ids.CITIZEN_3,Ids.FISHER_P,Ids.HUNTRESS,Ids.CITIZEN_4,Ids.CENTURION,
            Ids.ARTHUR,Ids.LANCELOT,Ids.GAWAIN,Ids.TRISTAN,Ids.PERCIVAL,Ids.MERLIN,
            Ids.CARPENTER_CITY,Ids.CARPENTER_TOWN,Ids.CARPENTER_WOOD,Ids.CARPENTER_PALACE,Ids.WOODCUTTER_CITY,Ids.WOODCUTTER_TOWN,
            Ids.MINER_CITY,Ids.MINER_PALACE,Ids.TRADER_CITY,Ids.TRADER_PALACE);
    private Npc addReleaseNpc(long id) {
        switch((int)id) {
            case (int)Ids.TELLER -> {
                // the Bank of Gameworld's one teller (Civic Services): keeps the counter by day, sleeps in the back
                Npc wren = npc(Ids.TELLER, "Wren", Npc.Role.CIVIC, "Bank teller", DevMap.BANK_TELLER[0], DevMap.BANK_TELLER[1], 151);
                wren.postX = DevMap.BANK_TELLER[0]; wren.postZ = DevMap.BANK_TELLER[1]; wren.homeJurisdiction = DevMap.BETA;
                if (g.objects.bankBed != null) { wren.homeX = g.objects.bankBed.x; wren.homeZ = g.objects.bankBed.z; }
                give(wren, Items.COINS, 50);
                wren.pickpocketLevel = 10;
                return wren;
            }
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
                give(maud, Items.TROUT, 8); give(maud, Items.COINS, 30); maud.skills.setLevel(Skill.MAGIC, 60); maud.spellBook = rpg.combat.Spells.Book.MOON;
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
                Profession.GAME_HUNTER.assign(godric);give(godric,Items.get("bronze_dagger"),1);give(godric,Items.BREAD,2);give(godric,Items.KNIFE,1);wear(godric,"shortbow",null,"stone_body");
                godric.eq.set(ItemDef.Slot.AMMO,new ItemStack(Items.BRONZE_ARROWS,20));g.state.mint(Items.BRONZE_ARROWS,20);
                return godric;
            }
            case (int)Ids.SCHOLAR -> {
                Npc isolde = npc(Ids.SCHOLAR, "Isolde", Npc.Role.SCHOLAR, "Travelling scholar", 103.5f, 58.5f, 145);
                isolde.skills.setLevel(Skill.MAGIC, 40); isolde.spellBook = rpg.combat.Spells.Book.MOON; give(isolde, Items.COINS, 60); give(isolde, Items.BREAD, 2);
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
                Npc fenna=npc(id,"Fenna",Npc.Role.HUNTER,"Predator Hunter",84.5f,91.5f,150);   // (inside the lodge: 82 is its wall)
                Profession.PREDATOR_HUNTER.assign(fenna);levels(fenna,18,18,15,22);fenna.skills.setLevel(Skill.ARCHERY,25);
                wear(fenna,"shortbow",null,"stone_body");give(fenna,Items.get("bronze_dagger"),1);give(fenna,Items.KNIFE,1);give(fenna,Items.BREAD,2);give(fenna,Items.SNARE,1);
                fenna.eq.set(ItemDef.Slot.AMMO,new ItemStack(Items.BRONZE_ARROWS,30));g.state.mint(Items.BRONZE_ARROWS,30);return fenna;
            }
            // the five of 0.1.8 (gamefixes: new people of callings no one held yet)
            case (int)Ids.COOK -> {
                Npc hilda=npc(id,"Hilda",Npc.Role.CRAFTSMAN,"Cook",DevMap.RANGE_AT[0]+0.5f,DevMap.RANGE_AT[1]+1.8f,151);
                Profession.COOK.assign(hilda);hilda.skills.setLevel(Skill.COOKING,32);
                give(hilda,Items.RAW_SHRIMP,6);give(hilda,Items.RAW_MEAT,3);give(hilda,Items.COINS,50);return hilda;
            }
            case (int)Ids.QUARRY_WORKER -> {
                Npc bertram=npc(id,"Bertram",Npc.Role.MINER,"Quarry Worker",96.5f,21.5f,152);
                Profession.QUARRY_WORKER.assign(bertram);bertram.skills.setLevel(Skill.MINING,22);levels(bertram,8,14,8,14);
                give(bertram,Items.get("bronze_pickaxe"),1);give(bertram,Items.BREAD,2);give(bertram,Items.COINS,30);return bertram;
            }
            case (int)Ids.BORDER_KNIGHT -> {
                // posted where DevCity's ground meets DevTown's, on the road between them
                float[] border=border(DevMap.ALPHA,110.5f,58.5f,38.5f,116.5f);
                Npc roderic=npc(id,"Sir Roderic",Npc.Role.KNIGHT,"Border Knight",border[0],border[1],153);
                Profession.BORDER_KNIGHT.assign(roderic);roderic.dutyX=border[0];roderic.dutyZ=border[1];
                levels(roderic,38,38,42,42);wear(roderic,"steel_sword","steel_shield","steel_body");give(roderic,Items.TROUT,3);return roderic;
            }
            case (int)Ids.SHELLFISH_GATHERER -> {
                Npc wynn=npc(id,"Wynn",Npc.Role.FISHERMAN,"Shellfish Gatherer",DevMap.FISH_SPOTS[2][0]+0.5f,DevMap.FISH_SPOTS[2][1]+2.5f,154);
                Profession.SHELLFISH_GATHERER.assign(wynn);wynn.skills.setLevel(Skill.FISHING,18);
                give(wynn,Items.CRAB_CAGE,1);give(wynn,Items.BREAD,2);give(wynn,Items.COINS,25);return wynn;
            }
            case (int)Ids.RIVER_FISHERMAN -> {
                Npc corwin=npc(id,"Corwin",Npc.Role.FISHERMAN,"River Fisherman",DevMap.STREAM_X0-1.5f,(DevMap.STREAM_Z0+DevMap.STREAM_Z1)/2f,155);
                Profession.RIVER_FISHERMAN.assign(corwin);corwin.skills.setLevel(Skill.FISHING,28);
                give(corwin,Items.FISHING_ROD,1);give(corwin,Items.BREAD,2);give(corwin,Items.COINS,25);return corwin;
            }
            default -> { Npc n = support(id); if (n == null) n = expanse(id); if (n == null) throw new IllegalArgumentException("Not an authored release NPC: "+id); return n; }
        }
    }

    /** Additional ordinary workers reuse established occupations, tools and the same finite item ledger. */
    private Npc support(long id) {
        if (id < Ids.CARPENTER_CITY || id > Ids.TRADER_PALACE) return null;
        int index = (int)(id - Ids.CARPENTER_CITY);
        float[][] posts = {{74.5f,28.5f},{39.5f,108.5f},{90.5f,134.5f},{172.5f,84.5f},
                {80.5f,20.5f},{31.5f,102.5f},{93.5f,24.5f},{140.5f,152.5f},{76.5f,28.5f},{182.5f,96.5f}};
        Npc.Role role = index < 4 ? Npc.Role.CRAFTSMAN : index < 6 ? Npc.Role.WORKER : index < 8 ? Npc.Role.MINER : Npc.Role.MERCHANT;
        String title = index < 4 ? "Carpenter" : index < 6 ? "Woodcutter" : index < 8 ? "Ore Miner" : "Materials Trader";
        Npc n = npc(id, title, role, title, posts[index][0], posts[index][1], 240 + index);
        if (index < 4) {
            Profession.CARPENTER.assign(n); n.skills.setLevel(Skill.CONSTRUCTION,40); n.skills.setLevel(Skill.CRAFTING,30);
            give(n,Items.HAMMER,1); give(n,Items.PLANK,6);
        } else if (index < 6) {
            n.skills.setLevel(Skill.WOODCUTTING,30); give(n,Items.get("bronze_axe"),1); give(n,Items.LOGS,4);
        } else if (index < 8) {
            Profession.ORE_MINER.assign(n); n.skills.setLevel(Skill.MINING,30); give(n,Items.get("bronze_pickaxe"),1);
        } else { give(n,Items.PLANK,4); give(n,Items.LOGS,2); give(n,Items.HAMMER,2); }
        give(n,Items.COINS,index < 8 ? 60 : 180); give(n,Items.BREAD,3);
        if (index == 3 || index == 7 || index == 9) roman(n,22);
        float[] home = DevMap.bedOf(id); if (home != null) { n.homeX=home[0];n.homeZ=home[1]; }
        return n;
    }

    /**
     * 0.2.0's residents (gamefixes). DevPalace's twenty, in Roman dress - the emperor on his throne with a centurion of his
     * household at his side, the praetor at the basilica, four legionaries walking the city's round, a senator in the
     * forum, a trader at the market hall, a priestess at the temple, a medicus, a philosopher, a stonemason for the marble
     * quarry, a beggar, four citizens, a fisher and a huntress; and the six newcomers at the forester's lodge in the
     * southern wood, each of a calling nobody on the devmap held before: Arthur the carpenter (who mends what is broken),
     * Lancelot a knight errant, Gawain a monster hunter, Tristan a trapper, Percival a coal miner, Merlin a gem prospector.
     */
    private Npc expanse(long id) {

        Npc n = switch ((int) id) {
            case (int) Ids.EMPEROR -> { Npc s = roman(npc(id, "Severinus", Npc.Role.CIVIC, "Emperor of DevPalace", rpg.world.Expanse.THRONE[0], rpg.world.Expanse.THRONE[1] + 1.2f, 160), 15);
                s.standing = Npc.Standing.ROYAL; s.postX = rpg.world.Expanse.THRONE[0]; s.postZ = rpg.world.Expanse.THRONE[1] + 1.2f; give(s, Items.COINS, 1200); s.pickpocketLevel = 60; yield s; }
            case (int) Ids.PRAETOR -> { Npc c = roman(npc(id, "Cassia", Npc.Role.LAWMAN, "Praetor (DevPalace)", rpg.world.Expanse.PRAETOR[0], rpg.world.Expanse.PRAETOR[1], 161), 15);
                levels(c, 40, 40, 40, 40); wear(c, "steel_sword", "steel_shield", "steel_body"); give(c, Items.COINS, 2500); c.postX = rpg.world.Expanse.PRAETOR[0]; c.postZ = rpg.world.Expanse.PRAETOR[1]; yield c; }
            case (int) Ids.LEGIONARY_1, (int) Ids.LEGIONARY_2, (int) Ids.LEGIONARY_3, (int) Ids.LEGIONARY_4 -> {
                int k = (int) (id - Ids.LEGIONARY_1);
                String[] names = {"Titus", "Decimus", "Quintus", "Sextus"};
                float[] at = rpg.world.Expanse.PATROL[k * 2];
                Npc l = roman(npc(id, names[k], Npc.Role.GUARD, "Legionary (DevPalace)", at[0], at[1], 162 + k), 0);
                levels(l, 30, 32, 30, 30); wear(l, "iron_sword", "iron_shield", "iron_body", "iron_helm"); give(l, Items.TROUT, 2); l.patrolIndex = k * 2; yield l; }
            case (int) Ids.SENATOR -> { Npc s = roman(npc(id, "Lucilla", Npc.Role.CIVIC, "Senator", rpg.world.Expanse.SENATE_POST[0], rpg.world.Expanse.SENATE_POST[1], 166), 22);
                s.standing = Npc.Standing.NOBLE; s.postX = rpg.world.Expanse.SENATE_POST[0]; s.postZ = rpg.world.Expanse.SENATE_POST[1]; give(s, Items.COINS, 400); yield s; }
            case (int) Ids.MACELLUM -> { Npc f = roman(npc(id, "Flavia", Npc.Role.MERCHANT, "Trader (the Macellum)", rpg.world.Expanse.MERCHANT_POST[0], rpg.world.Expanse.MERCHANT_POST[1], 167), 18);
                f.postX = rpg.world.Expanse.MERCHANT_POST[0]; f.postZ = rpg.world.Expanse.MERCHANT_POST[1]; give(f, Items.COINS, 600); f.pickpocketLevel = 5; yield f; }
            case (int) Ids.PRIESTESS -> { Npc a = roman(npc(id, "Aurelia", Npc.Role.PRIEST, "Priestess of the temple", rpg.world.Expanse.ALTAR[0], rpg.world.Expanse.ALTAR[1] + 1.5f, 168), 22);
                a.postX = rpg.world.Expanse.ALTAR[0]; a.postZ = rpg.world.Expanse.ALTAR[1] + 1.5f; a.skills.setLevel(Skill.FAITH, 45); give(a, Items.BREAD, 2); yield a; }
            case (int) Ids.MEDICUS -> { Npc m = roman(npc(id, "Philon", Npc.Role.HEALER, "Medicus", rpg.world.Expanse.MEDICUS_POST[0], rpg.world.Expanse.MEDICUS_POST[1], 169), 20);
                m.postX = rpg.world.Expanse.MEDICUS_POST[0]; m.postZ = rpg.world.Expanse.MEDICUS_POST[1]; give(m, Items.TROUT, 6); give(m, Items.COINS, 40); m.skills.setLevel(Skill.MAGIC, 45); m.spellBook = rpg.combat.Spells.Book.MOON; yield m; }
            case (int) Ids.PHILOSOPHER -> { Npc p2 = roman(npc(id, "Nikias", Npc.Role.SCHOLAR, "Philosopher", 152.5f, 94.5f, 170), 22); p2.skills.setLevel(Skill.MAGIC, 35); give(p2, Items.COINS, 50); yield p2; }
            case (int) Ids.STONEMASON -> { Npc g2 = roman(npc(id, "Gaius", Npc.Role.MINER, "Stonemason", 181.5f, 141.5f, 171), 19);
                Profession.QUARRY_WORKER.assign(g2); g2.skills.setLevel(Skill.MINING, 32); levels(g2, 10, 16, 10, 16); give(g2, Items.get("bronze_pickaxe"), 1); give(g2, Items.BREAD, 2); give(g2, Items.COINS, 30); yield g2; }
            case (int) Ids.PALACE_BEGGAR -> { Npc b = roman(npc(id, "Servius", Npc.Role.BEGGAR, "Beggar", 165.5f, 103.5f, 172), 16); b.postX = 165.5f; b.postZ = 103.5f; give(b, Items.BREAD, 1); yield b; }
            case (int) Ids.CITIZEN_1 -> citizen(id, "Octavia", 146.5f, 92.5f, 173);
            case (int) Ids.CITIZEN_2 -> citizen(id, "Marcellus", 170.5f, 92.5f, 174);
            case (int) Ids.CITIZEN_3 -> citizen(id, "Cornelia", 150.5f, 76.5f, 175);
            case (int) Ids.CITIZEN_4 -> citizen(id, "Livia", 168.5f, 76.5f, 178);
            case (int) Ids.FISHER_P -> { Npc f = roman(npc(id, "Lucius", Npc.Role.FISHERMAN, "River Fisherman", rpg.world.Expanse.BROOK_SPOTS[0][0] - 2, rpg.world.Expanse.BROOK_SPOTS[0][1], 176), 6);
                Profession.RIVER_FISHERMAN.assign(f); f.skills.setLevel(Skill.FISHING, 26); give(f, Items.FISHING_ROD, 1); give(f, Items.BREAD, 2); give(f, Items.COINS, 25); f.homeJurisdiction = DevMap.PALACE; yield f; }
            case (int) Ids.HUNTRESS -> { Npc h = roman(npc(id, "Atalanta", Npc.Role.HUNTER, "Huntress", 150.5f, 14.5f, 177), 5);
                Profession.GAME_HUNTER.assign(h); levels(h, 16, 14, 12, 20); h.skills.setLevel(Skill.HARVESTING, 26); h.skills.setLevel(Skill.ARCHERY, 30);
                give(h, Items.KNIFE, 1); give(h, Items.BREAD, 2); wear(h, "shortbow", null, "stone_body"); h.eq.set(ItemDef.Slot.AMMO, new ItemStack(Items.BRONZE_ARROWS, 25)); g.state.mint(Items.BRONZE_ARROWS, 25); h.homeJurisdiction = DevMap.PALACE; yield h; }
            case (int) Ids.CENTURION -> { Npc m = roman(npc(id, "Maximus", Npc.Role.KNIGHT, "Centurion of the household", rpg.world.Expanse.THRONE[0] + 2.5f, rpg.world.Expanse.THRONE[1] + 3, 179), 0);
                Profession.HOUSEHOLD_KNIGHT.assign(m); m.dutyTarget = Ids.EMPEROR; m.dutyX = rpg.world.Expanse.THRONE[0] + 2.5f; m.dutyZ = rpg.world.Expanse.THRONE[1] + 3;
                levels(m, 42, 42, 44, 44); wear(m, "steel_sword", "steel_shield", "steel_body", "iron_helm"); give(m, Items.TROUT, 3); yield m; }
            case (int) Ids.ARTHUR -> { Npc a = npc(id, "Arthur", Npc.Role.CRAFTSMAN, "Carpenter", DevMap.SAW_AT[0] + 0.5f, DevMap.SAW_AT[1] + 1.8f, 180);
                Profession.CARPENTER.assign(a); a.skills.setLevel(Skill.CONSTRUCTION, 40); a.skills.setLevel(Skill.CRAFTING, 30); a.postX = DevMap.SAW_AT[0] + 0.5f; a.postZ = DevMap.SAW_AT[1] + 1.8f;
                give(a, Items.HAMMER, 1); give(a, Items.PLANK, 8); give(a, Items.LOGS, 4); give(a, Items.BREAD, 2); give(a, Items.COINS, 80); yield a; }
            case (int) Ids.LANCELOT -> { Npc l = npc(id, "Sir Lancelot", Npc.Role.KNIGHT, "Knight Errant", 96.5f, 100.5f, 181);
                Profession.KNIGHT_ERRANT.assign(l); levels(l, 46, 46, 46, 46); wear(l, "steel_sword", "steel_shield", "steel_body", "iron_helm", "iron_legs"); give(l, Items.TROUT, 3); yield l; }
            case (int) Ids.GAWAIN -> { Npc g3 = npc(id, "Sir Gawain", Npc.Role.HUNTER, "Monster Hunter", 44.5f, 44.5f, 182);
                Profession.MONSTER_HUNTER.assign(g3); levels(g3, 36, 36, 34, 38); g3.skills.setLevel(Skill.ARCHERY, 38);
                wear(g3, "oak_shortbow", null, "iron_body"); give(g3, Items.get("iron_sword"), 1); g3.eq.set(ItemDef.Slot.AMMO, new ItemStack(Items.IRON_ARROWS, 40)); g.state.mint(Items.IRON_ARROWS, 40); give(g3, Items.TROUT, 3); yield g3; }
            case (int) Ids.TRISTAN -> { Npc t = npc(id, "Tristan", Npc.Role.HUNTER, "Trapper", 40.5f, 150.5f, 183);
                Profession.TRAPPER.assign(t); t.skills.setLevel(Skill.HARVESTING, 32); levels(t, 14, 12, 12, 18); give(t, Items.SNARE, 3); give(t, Items.KNIFE, 1); give(t, Items.BREAD, 2); give(t, Items.COINS, 20); yield t; }
            case (int) Ids.PERCIVAL -> { Npc p3 = npc(id, "Percival", Npc.Role.MINER, "Coal Miner", 144.5f, 145.5f, 184);
                Profession.COAL_MINER.assign(p3); p3.skills.setLevel(Skill.MINING, 38); levels(p3, 12, 20, 12, 20); give(p3, Items.get("iron_pickaxe") != null ? Items.get("iron_pickaxe") : Items.get("bronze_pickaxe"), 1); give(p3, Items.BREAD, 2); give(p3, Items.COINS, 25); yield p3; }
            case (int) Ids.MERLIN -> { Npc m = npc(id, "Merlin", Npc.Role.MINER, "Gem Prospector", 139.5f, 147.5f, 185);
                Profession.GEM_PROSPECTOR.assign(m); m.skills.setLevel(Skill.MINING, 40); m.skills.setLevel(Skill.MAGIC, 70); m.spellBook = rpg.combat.Spells.Book.MOON;
                give(m, Items.get("iron_pickaxe") != null ? Items.get("iron_pickaxe") : Items.get("bronze_pickaxe"), 1); give(m, Items.get("chisel"), 1); give(m, Items.BREAD, 2); give(m, Items.COINS, 60); yield m; }
            default -> null;
        };
        if (n == null) return null;
        float[] bed = DevMap.bedOf(n.id);
        if (bed != null) { n.homeX = bed[0]; n.homeZ = bed[1]; }
        return n;
    }
    /** A citizen of DevPalace: picks what grows and sells it at the market hall. */
    private Npc citizen(long id, String name, float x, float z, long seed) {
        Npc c = roman(npc(id, name, Npc.Role.VILLAGER, "Citizen of DevPalace", x, z, seed), 22);
        c.skills.setLevel(Skill.HARVESTING, 12); give(c, Items.COINS, 30); c.pickpocketLevel = 1;
        return c;
    }
    /** Roman dress: a draped top over a robe and strapped sandals; colour by office (white for the citizen, red for the soldier...). */
    private static Npc roman(Npc n, int color) {
        n.look.top = 19; n.look.topColor = color; n.look.bottom = n.look.female ? 8 : 12; n.look.bottomColor = color == 0 ? 15 : 20; n.look.shoes = 5; n.look.shoesColor = 19;
        if (!n.look.female && n.role != Npc.Role.BEGGAR) n.look.beard = n.id % 3 == 0 ? 3 : 0;
        n.homeJurisdiction = DevMap.PALACE;
        return n;
    }

    /** Where a straight way from inside a jurisdiction first leaves it (its border on that road); the far end if it never does. */
    private static float[] border(String jurisdiction, float x0, float z0, float x1, float z1) {
        for (int i = 0; i <= 200; i++) {
            float t = i / 200f, x = x0 + (x1 - x0) * t, z = z0 + (z1 - z0) * t;
            if (!jurisdiction.equals(DevMap.jurisdiction(x, z))) return new float[]{x, z};
        }
        return new float[]{x1, z1};
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
    private static final java.util.Set<String> FEMALE_NAMES = java.util.Set.of("Ada", "Eda", "Jun", "Kit", "Mara", "Maud", "Isolde", "Bandit archer", "Wren", "Elowen", "Tamsin", "Fenna",
            "Cassia", "Lucilla", "Flavia", "Aurelia", "Octavia", "Cornelia", "Livia", "Atalanta");

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

    /** An agent for a monster's body (it walks on the engine's routes as people do): its eyes' height and how far it sees. */
    public void agentFor(Npc n, float eye, float sight) {
        n.agent = g.game.reserve(KIND_AGENT);
        g.game.commands().agentCreate(n.agent, policy, n.body, 0, 0, 0, eye, sight, 0.25f, 24, n.team, n.domain.flags | AGENT_DOORS, (int) n.id * 7919);
        java.util.Arrays.fill(n.facts, -1);
        n.targetBody = -1;
        desire(n, A_IDLE);
    }
    /** Walks an NPC (or a monster) toward a point by the travel graph; true once there. */
    public boolean walkTo(Npc n, float x, float z, boolean run, float within) { return brain.goTo(n, x, z, run, within); }
    /** A monster's eyes and ears (the stealth system's sight and hearing, as for anyone). */
    public void monsterSenses(Npc n, float dt) { stealth.update(n, dt); }
    /** Turns a body toward where it means to face (or where it is going). */
    public void faceAt(Npc n, float dt) { face(n, dt); }

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
        n.sprinting = action == A_SPRINT;
        for (int i = 0; i < ACTIONS; i++) {
            float v = i == action ? 1f : i == A_IDLE ? 0.08f : 0.02f;
            if (n.facts[i] != v) { n.facts[i] = v; g.game.commands().agentFact(n.agent, i, v); }
        }
    }

    /**
     * How fast someone in a hurry goes (gamefixes: people never sprinted, nor knew when to). A sprint is for what cannot
     * wait - running from danger, after a foe or a fleeing thief, to report a killing, to a wound - and only out of
     * doors, with the breath for it (they set off on 40% of their stamina and give up at 12%), and the way long enough to
     * be worth it. A quarry that only walks is closed on at a jog; the last steps to anywhere are eased into.
     */
    int pace(Npc n, float x, float z, float within) {
        float left = (float) Math.hypot(n.x - x, n.z - z) - within;
        if (left < 1.2f) return A_GOTO;
        Actor quarry = n.goal == Brain.Goal.FIGHT || n.goal == Brain.Goal.SEARCH ? g.actor(n.fightTarget != 0 ? n.fightTarget : n.hunt != null ? n.hunt.target : 0) : null;
        float quarrySpeed = quarry == null ? 99 : (float) Math.hypot(quarry.vx, quarry.vz);
        boolean urgent = switch (n.goal) {
            case FLEE, HIDE, WARN, AID -> true;
            case FIGHT -> quarry != null && (quarrySpeed > g.cfg.jogSpeed * 0.8f || left > 14);
            case SEARCH -> n.hunt != null && g.time - n.hunt.clueAt < 8;
            case REPORT_CRIME -> { Memory m = null; for (Memory k : n.memories) if (k.crime() && !k.reported) { m = k; break; } yield m != null && (m.kind.equals("MURDER") || m.kind.equals("ASSAULT")); }
            // (a foe that just slipped out of sight is run down at its last place, not strolled after)
            case INVESTIGATE -> n.investigation != null && (n.investigation.stimulus == Investigation.Stimulus.VIOLENCE
                    || n.investigation.plan == Investigation.Plan.CONFRONT && g.time - n.investigation.lastClue < 8);
            default -> false;
        };
        boolean wounded = n.hp < n.maxHp() * 0.3f && n.goal != Brain.Goal.FLEE;
        boolean indoors = g.vox.skyLight((int) Math.floor(n.x), (int) Math.floor(n.y + 1), (int) Math.floor(n.z)) < 0.2f;
        float breath = n.stamina / n.maxStamina();
        boolean sprint = urgent && !wounded && !indoors && n.carryBody == 0 && left > 6 && breath > (n.sprinting ? 0.12f : 0.4f);
        return sprint ? A_SPRINT : A_RUN;
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

    /**
     * A chase given up on purpose (an outlaw too far from its camp): the quarry is let go - what it knows of them is
     * marked looked into and any look-about for them dropped - so it walks home instead of turning back to every
     * glimpse and turning home again (gamefixes: AI choices flip-flopping each second).
     */
    void giveUp(Npc n, long quarry) {
        Stealth.Aware w = n.aware.get(quarry);
        if (w != null) w.settledAt = g.time;
        if (n.investigation != null && n.investigation.subject == quarry) n.investigation = null;
    }

    /** Closing on a foe: the careful fighting step only for the last few metres; from farther, a jog or a sprint (pace). */
    public void closeIn(Npc n, float x, float z) {
        combatGoal(n, x, z);
        if (Math.hypot(n.x - x, n.z - z) > 4.5f) desire(n, pace(n, x, z, 0));
    }

    // ------------------------------------------------------------------ the frame
    private double watchAt;
    public void update(float dt) {
        if (g.scripted && System.getenv("RPG_WATCH") != null && g.time > watchAt) {
            watchAt = g.time + 0.5;
            for (Npc n : g.npcs) if (java.util.Arrays.asList(System.getenv("RPG_WATCH").split(",")).contains(n.name))
                System.out.printf("      [watch] " + n.name + " t %.1f %s at (%.2f, %.2f) v (%.2f, %.2f) goal (%.1f, %.1f) %s route %d inv %s last %s%n", g.time, n.goal, n.x, n.z, n.vx, n.vz, n.gx, n.gz, n.nativeAction, n.routeCells, n.inv.stacks(), n.lastOutcome);
        }
        updateAlarms();
        arms.tick();
        for (Npc n : g.npcs) {
            if (!n.alive) { respawnCheck(n); continue; }
            if (!n.loaded) continue;
            keepArms(n);
            // (0.2.1, gamefixes: heavy stuttering on the larger map) far from the player and about their ordinary day, a
            // resident is simulated at a quarter of the rate - the time saved up and spent on its own frame - and its
            // agent ticks slower; near the player, or in a fight, a flight or a hunt, every frame as before
            boolean far = farAway(n);
            int lod = far ? 4 : 1;
            n.lodDt += dt;
            if (far && Math.floorMod(g.state.tick + n.id, lod) != 0) continue;
            float step = n.lodDt; n.lodDt = 0;
            // (0.2.2) an NPC with a hurt leg goes slower as well as limping (the player only limps)
            float legs = g.gore == null ? 1 : Math.max(0.35f, g.gore.legs(n));
            g.game.commands().agentRate(n.agent, n.cs.frozenT > 0 ? 0.001f : n.actionRate() * (n.cs.slowT > 0 ? 0.6f : 1) * (far ? 0.5f : 1) * legs);
            if (n.morphT > 0) {
                n.cs.cancel(); n.cs.blocking = false; n.sneaking = true;
                g.game.commands().moverInput(n.body, 0, 0, 0, 0, NPC_ACCEL, MOVER_CROUCH | MOVER_STANCE_ONLY);
                Actor attacker = g.actor(n.cs.lastAttacker);
                if (attacker != null && attacker.alive) { target(n, attacker.body); desire(n, A_FLEE); } else desire(n, A_HOLD);
                continue;
            }
            stealth.update(n, step);
            if (g.traversal.update(n, step)) continue;
            if (n.reacting) continue;           // off balance in Sydney's hands: no thinking, no walking, until back on their feet
            if (g.time >= n.nextThink) { brain.think(n); decisions++; }
            Actor elevated = n.goal == Brain.Goal.FIGHT ? g.actor(n.fightTarget) : null;
            if (elevated != null && elevated.y > n.y + 0.75f && g.traversal.begin(n, (float) Math.atan2(-(elevated.x - n.x), -(elevated.z - n.z))) && g.traversal.update(n, step)) continue;
            brain.act(n, step);
            if (n.stuck && (n.goal == Brain.Goal.FIGHT || n.goal == Brain.Goal.FLEE || n.goal == Brain.Goal.RETURN) && n.hasGoalPos) {
                float heading = (float) Math.atan2(-(n.gx - n.x), -(n.gz - n.z));
                if (g.traversal.begin(n, heading) && g.traversal.update(n, step)) continue;
            }
            g.game.commands().moverInput(n.body, 0, 0, 0, 0, NPC_ACCEL, MOVER_STANCE_ONLY | (n.sneaking && n.goal != Brain.Goal.COLLECT ? MOVER_CROUCH : 0));
            face(n, step);
            if (n.speech != null && g.time > n.speechUntil) n.speech = null;
            if (!far) footsteps(n, step);
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
            // a sprint costs breath as the player's does
            if (n.sprinting && Math.hypot(n.vx, n.vz) > g.cfg.jogSpeed) { n.stamina = Math.max(0, n.stamina - g.cfg.staminaSprint * (float) g.lastDt); n.cs.regenDelay = 0.6f; }
            Snapshots.Agent a = agents.agent(n.agent);
            if (a != null) {
                n.agentYaw = a.yaw;
                n.routeCells = a.routeCells;
                n.stuck = a.stuck;
                if (!n.stuck && Math.hypot(n.vx,n.vz)>0.2f && n.lastOutcome.startsWith("route blocked")) n.lastOutcome="route resumed";
                n.nativeAction = a.action >= 0 && a.action < ACTIONS ? ACTION_NAMES[a.action] : "-";
            }
        }
    }

    /** A cell a body can stand in: a floor under it, nothing solid at the feet or the head. */
    private boolean standable(int x, int z) {
        return g.vox.solid(x, DevMap.FLOOR - 1, z) && !g.vox.solid(x, DevMap.FLOOR, z) && !g.vox.solid(x, DevMap.FLOOR + 1, z) && g.vox.block(x, DevMap.FLOOR - 1, z) != rpg.world.Blocks.WATER
                && g.vox.block(x, DevMap.FLOOR, z) != rpg.world.Blocks.EMBERS;
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
        // no way to the one they meant to talk with: someone else, or back to the day, rather than the same wall again
        if (n.goal == Brain.Goal.SOCIALIZE && n.socialPartner != 0) { n.unreachable.put(n.socialPartner, g.time + 150); n.socialPartner = 0; }
        n.remember(new Memory("BLOCKED", 0, 0, DevMap.jurisdiction(n.gx, n.gz), g.state.minutes, 1, "personal", 0, 0, String.format("could not reach (%.0f, %.0f)", n.gx, n.gz)), 24);
        g.state.commit("NpcStuck", n.id, 0, DevMap.jurisdiction(n.x, n.z), 0, n.goal + String.format(" to (%.1f, %.1f)", n.gx, n.gz));
        n.hasGoalPos = false;
        n.legs = null;   // plan the trip again from where it stands
        n.nextThink = 0;
        // off the walkable ground (over the water at a bank's edge, wedged at a wall): a step back onto the nearest
        // cell that has a floor and room to stand, from where routes exist again
        int bx = (int) Math.floor(n.x), bz = (int) Math.floor(n.z);
        // (0.2.2, gamefixes: NPCs teleported while in a fight) in a fight it is a step to the very next cell at most, and
        // not at all for a moment after a blow - a knock-back that ends at a wall settles where it lies
        boolean fighting = n.goal == Brain.Goal.FIGHT || n.goal == Brain.Goal.FLEE || g.time - n.cs.lastHurt < 1.5;
        if (n.alive && n.loaded && !n.reacting && n.ragdoll == 0 && n.carryBody == 0 && !g.law.inCustody(n) && !standable(bx, bz) && !(fighting && g.time - n.cs.lastHurt < 1.5)) {
            for (int ring = 1; ring <= (fighting ? 1 : 3); ring++) {
                float best = Float.MAX_VALUE; int cx = 0, cz = 0;
                for (int dx = -ring; dx <= ring; dx++) for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring || !standable(bx + dx, bz + dz)) continue;
                    float d = (float) Math.hypot(bx + dx + 0.5f - n.x, bz + dz + 0.5f - n.z);
                    if (d < best) { best = d; cx = bx + dx; cz = bz + dz; }
                }
                if (best < Float.MAX_VALUE) { g.teleport(n, cx + 0.5f, DevMap.FLOOR, cz + 0.5f); g.state.commit("NpcUnstuck", n.id, 0, DevMap.jurisdiction(n.x, n.z), 0, String.format("stepped back onto (%d, %d)", cx, cz)); break; }
            }
        }
        if (n.goal == Brain.Goal.PATROL) n.patrolIndex++;
        if (n.goal == Brain.Goal.WORK) {
            g.repairs.abandon(n);
            for (var node : g.objects.nodes) if (Math.hypot(node.x-n.legsX,node.z-n.legsZ)<3) n.unreachable.put(node.id,g.time+45);
            brain.resetJob(n); g.interact.cancel(n, "stuck");
        }
    }


    private Npc byAgent(long agent) { for (Npc n : g.npcs) if (n.agent == agent) return n; return null; }

    // ------------------------------------------------------------------ perception and judgement
    /** Whether an NPC can see an actor: plainly (in its eyes' cone, lines clear, lit enough), or at all once alert to them (Stealth). */
    public boolean canSee(Npc n, Actor a) {
        if (!n.alive || !n.loaded || a == null || !a.alive) return false;
        return stealth.sees(n, a);
    }

    /** Everyone who can see an act happen right now (the culprit excluded). */
    /**
     * (0.2.2, gamefixes: a guard watched a goblin maul someone, then questioned them about the blood) whoever sees a blow
     * land knows where the blood on the struck came from, and whose blow it was - for a quarter of an hour it raises no
     * questions with them. A beast's attack is remembered as such, so its blood and its victim's body are put down to it.
     */
    public void sawHurt(Actor striker, Actor victim) {
        if (striker == null || victim == null || striker == victim) return;
        boolean beast = striker.domain != astratto.ai.AgentDomain.HUMAN;
        for (Npc n : g.npcs) {
            if (n == victim || n == striker || !n.alive || !n.loaded || n.distTo(victim) > 30 || !canSee(n, victim)) continue;
            Stealth.Aware w = n.aware.computeIfAbsent(victim.id, k -> new Stealth.Aware());
            w.bloodKnownUntil = g.time + 900; w.bloodBy = striker.id;
            if (!beast) continue;
            boolean known = false;
            for (Memory m : n.memories) if (m.kind.equals("BEAST_ATTACK") && m.victim == victim.id && g.state.minutes - m.minutes < 60) { known = true; break; }
            if (!known) n.remember(new Memory("BEAST_ATTACK", striker.id, victim.id, DevMap.jurisdiction(victim.x, victim.z), g.state.minutes, 1, "witnessed", 0, 0, "saw a creature attack " + victim.name), 24);
        }
    }

    /**
     * (0.2.2, gamefixes: NPCs abide by the sheathe system) a person's weapons go out when there is a use for them - a
     * fight, a hunt of a foe, the watch facing someone down, a tool at work, a weapon used a moment ago - and away again
     * once there is none. The watch may go armed in town; everyone else puts theirs away there.
     */
    private void keepArms(Npc n) {
        if (!n.sheathable() || n.reacting || n.cs.phase != Actor.Combat.Phase.IDLE || g.time - n.sheatheAt < Actor.SHEATHE_TIME * 2) return;
        rpg.item.ItemDef m = n.main();
        boolean want = n.goal == Brain.Goal.FIGHT || n.fightTarget != 0 || n.hunt != null && n.combatant() || g.time - n.armsUsedAt < 12
                || n.watch() && (n.goal == Brain.Goal.WATCH || n.goal == Brain.Goal.EVICT || n.goal == Brain.Goal.WARN)
                || n.goal == Brain.Goal.WORK && m != null && m.toolSkill != null;
        if (want == n.sheathed) g.combat.sheathe(n, !want);
    }

    /** Whether the blood on someone is explained to this NPC (it saw them struck, not long ago). */
    public boolean bloodExplained(Npc n, Actor a) {
        Stealth.Aware w = n.aware.get(a.id);
        return w != null && w.bloodKnownUntil > g.time;
    }

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
            if (target == null && g.time-n.cs.lastHurt < 10) target = g.actor(n.cs.lastAttacker);
            return target != null && target.alive && !g.magic.allied(n,target) && !g.law.inCustody(target)
                    && owner.distTo(target) < 14 && n.distTo(owner) < 18 && n.distTo(target) < 20 && canSee(n,target) ? target : null;
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
            if (a == n || !a.alive || a.dummy || law && g.law.inCustody(a) || a instanceof rpg.animal.Animal || a.bloodied < 0.25f || bloodExplained(n, a) || !canSee(n, a)) continue;
            if (g.gore.suspect(n, a)) { n.hunt = null; n.fightTarget = a.id; return a; }
        }
        // a foe being searched for, found again
        if (n.hunt != null && n.hunt.target != 0) {
            Actor t = g.actor(n.hunt.target);
            if (t != null && t.alive && !g.law.inCustody(t) && ZonePolicy.routineAllowed(n, t.x, t.z) && !(law && (g.law.settled(t.id, n.hunt.startedAt) && g.law.bounty(t.id, n.homeJurisdiction) <= 0)) && canSee(n, t)) { callOut(n, t); return t; }
        }
        if (n.hostileToPlayer && !g.law.inCustody(p) && ZonePolicy.routineAllowed(n, p.x, p.z) && p.alive && n.distTo(p) < n.aggroRange && canSee(n, p)) return p;
        // monsters (gamefixes: every person dislikes them, as they do people): one out for blood is dealt with or fled
        // from further off; any other in plain sight close by, a fighter goes for (in its own beat) and anyone else keeps
        // away from - a bat at its roost and a ghost at its grave included
        if (g.monsters != null) for (rpg.monster.Monster mo : g.monsters.all()) {
            if (!mo.alive || !mo.loaded || !canSee(n, mo)) continue;
            boolean hunting = mo.state == rpg.monster.Monster.State.ENGAGE || mo.state == rpg.monster.Monster.State.STALK || n.cs.lastAttacker == mo.id;
            float reach = hunting ? (n.combatant() ? 18 : 12) : (n.combatant() ? 12 : 7);
            // a terror (a dragon over the roofs): seen from far off, it is the alarm raised and the town's business
            // - come into a settlement; one in the wilds (on its own ground or hunting there) is fled or fought as it comes
            boolean loose = !DevMap.WILDS.equals(DevMap.jurisdiction(mo.x, mo.z));
            if (mo.kind.terror() > 0 && loose && n.distTo(mo) < mo.kind.terror()) { raiseAlarm(n, mo); reach = mo.kind.terror(); }
            if (n.distTo(mo) < reach && (hunting || !n.combatant() || ZonePolicy.routineAllowed(n, mo.x, mo.z))) return mo;
        }
        // a dangerous animal close by: guards deal with it, everyone else gets away from it
        if (g.animals != null) for (rpg.animal.Animal an : g.animals.all())
            if (an.alive && an.loaded && an.threatens(n) && n.distTo(an) < (n.combatant() ? 14 : 8) && canSee(n, an)) return an;
        if (n.role == Npc.Role.GUARD || n.role == Npc.Role.LAWMAN) {
            for (Actor a : g.actors()) {
                if (a == n || !a.alive || a.dummy || alliedWatch(n,a)) continue;
                // the watch goes after bounties above the pursuit line (or anyone who came to them); lawmen leave their
                // post for it, and in a lockdown everyone watches further
                float range = g.law.lockdown(n.homeJurisdiction) ? 34 : 24;
                if ((g.law.pursued(a.id, n.homeJurisdiction) || a == p && g.law.fineDue(n, a)) && ZonePolicy.routineAllowed(n, a.x, a.z) && !g.law.inCustody(a) && n.distTo(a) < range && canSee(n, a)) return a;
            }
        }
        if (!n.combatant()) {
            for (Npc o : g.npcs) if (o != n && o.alive && o.loaded && o.goal == Brain.Goal.FIGHT && o.hostileToPlayer && n.distTo(o) < 8 && canSee(n, o)) return o;
            Memory m = n.crimeKnown(p.id);
            if (n.role == Npc.Role.VILLAGER && m != null && !m.settled && m.witnessed() && n.distTo(p) < 2.5f && p.alive) return p;
        }
        return null;
    }

    // ------------------------------------------------------------------ the alarm
    /**
     * A town's alarm at a terror among it (gamefixes: a dragon in the city should cause a genuine panic): which monster,
     * where it was seen, when, by whom, and in which place. It spreads by voice - whoever is within earshot of the cry
     * knows of it without seeing it - and lasts while it is seen again; a minute after the last sighting it is let fall.
     */
    public static final class Alarm {
        public final long monster; public final String place;
        public float x, z; public double raisedAt, seenAt;
        Alarm(long monster, String place, float x, float z, double now) { this.monster = monster; this.place = place; this.x = x; this.z = z; raisedAt = seenAt = now; }
    }
    public final List<Alarm> alarms = new ArrayList<>();
    /** How far a cry of alarm carries (and from someone who has heard it, again: the town passes it on). */
    public static final float ALARM_EARSHOT = 32;

    /** Someone sees a terror: the alarm is raised (or kept up, where it is now), with a cry the first time and now and then. */
    public void raiseAlarm(Npc n, rpg.monster.Monster mo) {
        Alarm a = null;
        for (Alarm o : alarms) if (o.monster == mo.id) a = o;
        String place = DevMap.jurisdiction(mo.x, mo.z);
        if (a == null) {
            a = new Alarm(mo.id, place, mo.x, mo.z, g.time);
            alarms.add(a);
            g.state.commit("AlarmRaised", n.id, mo.id, place, 0, mo.kind.title + " sighted by " + n.name);
        }
        a.x = mo.x; a.z = mo.z; a.seenAt = g.time;
        if (!knowsWorse(n, mo)) n.alarmHeard = mo.id;
        if (g.time - n.alarmCryAt > 6) {
            n.alarmCryAt = g.time;
            String what = mo.kind.title.toUpperCase();
            if (n.speech == null) say(n, Brain.defender(n) ? "A " + mo.kind.title.toLowerCase() + "! To arms - everyone to arms!" : "A " + what + "! " + what + "! Get inside!");
            heardAlarm(n, a);
        }
    }

    /** The cry is heard: everyone awake within earshot who has not yet heard knows of it now, and cries it on. */
    public void heardAlarm(Npc crier, Alarm a) {
        for (Npc o : g.npcs) {
            if (o == crier || !o.alive || !o.loaded || o.asleep() || o instanceof rpg.monster.Monster || o.alarmHeard == a.monster) continue;
            if (o.distTo(crier) > ALARM_EARSHOT || g.actor(a.monster) instanceof rpg.monster.Monster mo && knowsWorse(o, mo)) continue;
            o.alarmHeard = a.monster; o.nextThink = Math.min(o.nextThink, g.time + 0.3 + (o.id % 5) * 0.15);
        }
    }

    /** Whether n already knows of a worse terror than this one (a spider's alarm does not drown out a dragon's). */
    private boolean knowsWorse(Npc n, rpg.monster.Monster mo) {
        Alarm known = alarmFor(n);
        return known != null && known.monster != mo.id && g.actor(known.monster) instanceof rpg.monster.Monster k && k.kind.terror() > mo.kind.terror();
    }

    /** The alarm this NPC knows of (heard or raised), still up: or null. */
    public Alarm alarmFor(Npc n) {
        for (Alarm a : alarms) if (a.monster == n.alarmHeard) return a;
        return null;
    }

    /** Alarms fall a minute after their terror was last seen, or when it is dead. */
    void updateAlarms() {
        alarms.removeIf(a -> {
            Actor m = g.actor(a.monster);
            boolean over = m == null || !m.alive || g.time - a.seenAt > 60;
            if (over) {
                g.state.commit("AlarmLifted", 0, a.monster, a.place, 0, m != null && !m.alive ? "it is dead" : "not seen for a minute");
                for (Npc o : g.npcs) if (o.alarmHeard == a.monster) { o.alarmHeard = 0; if (o.fearOf == a.monster) o.fearOf = 0; }
            }
            return over;
        });
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
        float patience = n.watch() ? 55 : 22;
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
            if (!o.watch() || !o.homeJurisdiction.equals(n.homeJurisdiction) || o.distTo(n) > 35) continue;
            Hunt j = new Hunt(t.id, n.name + " lost " + t.name, x, z, g.time, 55);
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
        // Outlaws keep to their camp; the watch shares one pursuit policy.
        boolean leash = n.hostileToPlayer && Math.hypot(h.x - n.spawnX, h.z - n.spawnZ) > 26;
        if (h.expired(g.time) || leash) {
            n.hunt = null;
            if (leash) giveUp(n, h.target);
            n.vigilantUntil = g.time + 90;
            say(n, leash ? "Not worth it." : h.target == 0 ? "Whoever did this is long gone." : n.role == Npc.Role.GUARD ? "Lost them. Keep your eyes open." : "Must have got away.");
            g.state.commit("SearchAbandoned", n.id, h.target, DevMap.jurisdiction(n.x, n.z), 0, String.format("after %.0fs, %d spots checked%s", g.time - h.startedAt, h.checked, leash ? " (too far)" : ""));
        }
    }

    /** One area being searched: a centre, how far it reaches, who is sought, and how many are searching it. */
    /** A searched area; {@code law}: the watch's search (anyone else hostile searching is shown apart). */
    public record SearchArea(float x, float z, float r, long target, int searchers, String jurisdiction, boolean law) {}

    /** The areas NPCs are searching right now (searches for the same quarry close together are one area). */
    public List<SearchArea> searchAreas() {
        List<SearchArea> out = new ArrayList<>();
        for (Npc n : g.npcs) {
            if (!n.alive || !n.loaded || n.hunt == null) continue;
            Hunt h = n.hunt;
            // (0.2.2, gamefixes) only searches that are about the player: for them, or for the culprit behind a body or
            // blood the player left - a search over someone else's doing is none of the minimap's business
            if (h.target != g.player.id && !(h.target == 0 && g.gore != null && g.gore.culpritOf(h.source) == g.player.id)) continue;
            float r = h.radius(g.time);
            boolean law = watch(n), merged = false;
            for (int i = 0; i < out.size(); i++) {
                SearchArea a = out.get(i);
                if (a.target() != h.target || a.law() != law || Math.hypot(a.x() - h.x, a.z() - h.z) > a.r() + r) continue;
                float cx = (a.x() * a.searchers() + h.x) / (a.searchers() + 1), cz = (a.z() * a.searchers() + h.z) / (a.searchers() + 1);
                float rr = Math.max(a.r(), r) + (float) Math.hypot(cx - h.x, cz - h.z) * 0.5f;
                out.set(i, new SearchArea(cx, cz, Math.min(18, rr), h.target, a.searchers() + 1, a.jurisdiction(), law));
                merged = true;
                break;
            }
            if (!merged) out.add(new SearchArea(h.x, h.z, r, h.target, 1, n.homeJurisdiction, law));
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
        long id = DevMap.ALPHA.equals(jurisdiction) ? Ids.LAWMAN_A : DevMap.BETA.equals(jurisdiction) ? Ids.LAWMAN_B : DevMap.PALACE.equals(jurisdiction) ? Ids.PRAETOR : -1;
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
            float d = n.distTo(o);
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
            if (n.unreachable.getOrDefault(o.id, -1.0) > g.time) continue;
            float d = n.distTo(o);
            if (o.id == n.socialPartner && d < 36) return o;
            if (d < bd) { bd = d; best = o; }
        }
        return best;
    }

    /** Passes news through the same hearing, evidence and feedback rules as street gossip. */
    public int share(Npc from, Npc to) {
        int n = g.law.posses.tell(from, to) ? 1 : 0;
        for (Memory m : new ArrayList<>(from.memories)) {
            if (rumors.tell(from, to, m, false)) n++;
        }
        return n;
    }

    public String smallTalk(Npc n, Npc p) { return g.dialogue.smallTalk(n, p); }

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
            case GUARD, KNIGHT -> 0.9f; case LAWMAN -> 0.85f; case BANDIT, BRUTE, MONSTER -> 0.95f; case UNDERTAKER -> 0.7f; case WORKER, FARMER, MINER, CRAFTSMAN -> 0.6f;
            case CARTER -> 0.55f; case THIEF -> 0.5f; case FENCE -> 0.45f; case VILLAGER, BEGGAR, HEALER, PRIEST, HUNTER, SCHOLAR, FISHERMAN -> 0.4f; case QUEST_GIVER -> 0.35f; case CONTACT -> 0.3f; case MERCHANT, CIVIC -> 0.3f;
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

    private boolean rockAt(Node self, int x, int z) {
        for (Node o : g.objects.nodes) if (o != self && o.kind == Node.Kind.ROCK && !o.depleted() && o.bx == x && o.bz == z) return true;
        return false;
    }

    public float[] standSpot(Npc n, Node node) {
        // the nearest dry, open cell to stand on: beside the node, or - for a fishing spot out on the water - on the bank
        // within a few metres of it (gamefixes: a fisher was sent into the pond and stood stuck at its edge all day)
        float[] best = {node.x + 1, node.z};
        float bd = Float.MAX_VALUE;
        for (int ring = 1; ring <= 4 && bd == Float.MAX_VALUE; ring++)
            for (int dx = -ring; dx <= ring; dx++) for (int dz = -ring; dz <= ring; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) continue;
                int cx = node.bx + dx, cz = node.bz + dz;
                if (g.vox.solid(cx, DevMap.FLOOR, cz) || g.vox.solid(cx, DevMap.FLOOR + 1, cz) || !g.vox.solid(cx, DevMap.FLOOR - 1, cz)) continue;
                float d = (float) Math.hypot(cx + 0.5f - node.x, cz + 0.5f - node.z) * 2 + (float) Math.hypot(cx + 0.5f - n.x, cz + 0.5f - n.z) * 0.1f;
                // a rock is worked square on, from a side where the swing's sideways sweep crosses no other deposit on its
                // way to this one - from a corner, or with a neighbouring rock in the sweep, the blow lands on the neighbour
                // (gamefixes: a quarry worker cut clay from the bank beside the face all morning)
                if (node.kind == Node.Kind.ROCK) {
                    if (dx != 0 && dz != 0) d += 4;
                    else if (ring == 1 && (rockAt(node, node.bx + dz, node.bz + dx) || rockAt(node, node.bx - dz, node.bz - dx))) d += 6;
                }
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
        g.game.commands().ragdollCreate(dead.ragdoll,dead.ragdollScale(),dead.sydneyProfile(),RAGDOLL_ACTIVE,dead.x,dead.y,dead.z,dead.yaw,0,0,0);
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
            g.game.commands().ragdollCreate(n.ragdoll, n.ragdollScale(), n.sydneyProfile(), RAGDOLL_ACTIVE | (posed ? 0 : RAGDOLL_STAND), n.x, n.y, n.z, n.yaw, 0, 0, 0);
            if (posed) g.game.commands().ragdollPose(n.ragdoll, n.vx, 0, n.vz, n.posePts);
            g.game.commands().ragdollControl(n.ragdoll, CONTROL_DIE, 1);
        }
        if (g.gore != null) g.gore.limbsToBody(n);
        g.game.commands().ragdollHit(n.ragdoll, RD_NECK, dx / l, 0.2f, dz / l, 120, 30);
        despawnBody(n);
        // what they carried stays on the body, to be searched (worn things stay worn: the search mirrors them); an outlaw's
        // (and a monster's) is anyone's, anyone else's is still theirs. What will not fit lies beside the body; bones are left
        boolean ownerless = n.role == Npc.Role.BANDIT || n.role == Npc.Role.BRUTE || n instanceof rpg.monster.Monster;
        rpg.objects.Corpse corpse = g.objects.add(new rpg.objects.Corpse(g.state.newEntityId(), n.name, n.id, n.x, DevMap.FLOOR + 0.2f, n.z, ownerless ? 0 : n.id));
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
        // (a monster's remains are its kind's own - ashes, ectoplasm, a troll's big bones - rolled with its drops)
        if (n instanceof rpg.monster.Monster) return;
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

    /** Beyond this (m) from the player, a resident about its ordinary day is simulated at a reduced rate (see {@link #update}). */
    public static final float LOD_FAR = 72;
    private boolean farAway(Npc n) {
        if (n.goal == Brain.Goal.FIGHT || n.goal == Brain.Goal.FLEE || n.goal == Brain.Goal.SEARCH || n.goal == Brain.Goal.INVESTIGATE || n.goal == Brain.Goal.HIDE
                || n.goal == Brain.Goal.REPORT_CRIME || n.goal == Brain.Goal.TIP_OFF || n.reacting || n.traversal != null || n.cs.busy()) return false;
        return Math.hypot(n.x - g.player.x, n.z - g.player.z) > LOD_FAR;
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
