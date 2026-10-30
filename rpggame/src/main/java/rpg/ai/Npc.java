package rpg.ai;

import java.util.ArrayList;
import java.util.List;
import rpg.Ids;
import rpg.Rpg;
import rpg.actor.Actor;
import rpg.interact.Interactable;
import rpg.interact.Option;
import rpg.interact.Result;
import rpg.interact.Work;
import rpg.item.ItemStack;
import rpg.item.Items;
import rpg.skill.Skill;
import rpg.ui.Dialog;
import rpg.world.DevMap;

/**
 * A non-player character: an actor (skills, inventory, equipment, HP - the same rules as the player) with a mind -
 * needs, a role, memories, a current goal and the reasons it was chosen - walked by an engine agent. It is also
 * interactable: Talk, Trade, Pickpocket, Pay bounty, Examine, each enabled or disabled with its reason.
 */
public class Npc extends Actor implements Interactable {
    /** Time saved up while simulated at a reduced rate far from the player (AiSystem's level of detail). */
    /** The day morning prayers were last said (Brain.PRAY), and when this prayer began. */
    public int prayedDay = -1;
    public double prayStarted = -1;
    /** The day the player last spoke with them (Dialogue's 'you again'). */
    public int talkedDay = -1;
    public float lodDt;
    public enum Role { MERCHANT, LAWMAN, GUARD, WORKER, VILLAGER, THIEF, CARTER, QUEST_GIVER, CONTACT, BANDIT, BRUTE, FARMER, UNDERTAKER, FENCE, BEGGAR, HEALER, PRIEST, HUNTER, SCHOLAR, FISHERMAN, MINER, CRAFTSMAN, KNIGHT, CIVIC, MONSTER }

    public final Role role;
    /** Not before then does the bank see them again (an errand done, or the bank found shut). */
    public double bankAgain;
    /** (0.2.2) Its pack is full: off to sell what it carries, or to bank it, before the work can go on. */
    public boolean unloading;
    /** (0.2.2) How long it has watched the player go about a town with a weapon drawn (seconds; negative: answered). */
    public float drawnFor;
    /** Rank by birth, which weighs on a murder's bounty (gamefixes): commoner 400 G, noble 600 G, royal 1,000 G. */
    public enum Standing { COMMON, NOBLE, ROYAL }
    public Standing standing = Standing.COMMON;
    /** A bounty hunter of a posse (law/Posses): hunted or killed, they add nothing to a bounty. */
    public boolean posse;
    public rpg.combat.Spells.Book spellBook = rpg.combat.Spells.Book.SUN;
    public String title;
    public Profession profession;
    astratto.ai.TaskNetwork<Npc> job;
    public long dutyTarget;
    public int dutyPhase;
    public float dutyX, dutyZ;
    public double dutyUntil;
    /** The kingdom whose law this person answers to (a posse's hunters come from the kingdom of the bounty). */
    public String homeJurisdiction;
    /** Engine handles: the agent and its mind. */
    public long agent;
    /** Loaded: simulated in detail with a body; unloaded: resolved abstractly from the same state. */
    public boolean loaded = true;
    /** Needs, 0 (met) .. 1 (pressing). */
    public float hunger, fatigue, social;
    /** How long a flyer it fights has hung out of its reach overhead (it waits for the dive; the unarmed give up and run). */
    public float flyerWait;
    /** The terror whose alarm this one has heard (0: none), and when it last cried it. */
    public long alarmHeard;
    public double alarmCryAt = -99;
    public float homeX, homeZ, postX, postZ;
    /** A grave this NPC has seen and has business with (negative: one he is done with), and his looting's pace. */
    public long graveSeen;
    /** Sparing (Honor): when this beaten outlaw was let go by the player (0: not; -1: spent or forfeited today). */
    public double spareSince;
    /** Whether the player has greeted them (once each: a courtesy, not a farm). */
    public boolean greeted;
    /** Whether they know the player by name and repute: met them (talked or traded), or heard of them from others. */
    public boolean knowsPlayer;
    /** What it is looking into now (a noise, a glimpse), and how (see Investigation); null when nothing. */
    public Investigation investigation;
    /** Trading news until (the gossip icon), the last alarm it raised, and when it last heard violence (blows, a scream). */
    public double gossipUntil = -1, lastAlarmAt = -99, violenceHeardAt = -99;
    /** What it fears and got away from (the player, a beast), where it was last seen, and when; and where it hides. */
    public long fearOf;
    public float fearX, fearZ, hideX, hideZ;
    public double fearSeenAt = -99;
    public boolean hideSet;
    /** A guard keeping an eye on a player he has heard bad things about, until (then a while before he does again). */
    public double watchUntil = -999;
    public long watchTarget;
    /** Arms seen shown lately (Arms): how often (fading over a minute), when last, what kind, and when they last spoke of it. */
    public float armsSeen;
    public double armsAt = -999, armsSaidAt = -999;
    public Arms.Kind armsKind;
    CombatTactics.Plan tactic;
    double nextTactic;
    public double lastLootAt, lootStarted = -99, lootUntil = -99;
    /** When this NPC may next hold something out to the player (an offer is not pressed again straight after an answer). */
    public double offerAgain = -99;
    public boolean asleep() {
        return alive && !reacting && goal == Brain.Goal.SLEEP && !cs.busy()
                && Math.hypot(x - homeX, z - homeZ) <= 1.2 && Math.hypot(vx, vz) < 0.12;
    }
    /** A confirmed identification, carried only by its eyewitness and people they actually tell. */
    public record Sighting(long witness, float x, float z, float vx, float vz, double at) {}
    public long navRevision=-1;
    public double missingBodySince=-1;
    public long damageReportTarget;
    public Sighting playerSighting;
    /** A tip-off (gamefixes): a sighting of a wanted player this NPC is carrying to the watch, and when it last told one. */
    public Sighting tip;
    public double tippedAt = -999;
    /** The last knock of a mender's hammer (a sound every so often while they work). */
    public double mendKnock = -99;
    /** Law-abiding folk: people who would tell the watch where a wanted person is (no crook, outlaw or hireling of the player's). */
    public boolean lawAbiding() {
        return !hostileToPlayer && summonOwner == 0 && !posse && switch (role) { case THIEF, BANDIT, BRUTE, FENCE, MONSTER -> false; default -> true; };
    }
    public enum EmploymentState { FREE, WORKING }
    public EmploymentState employmentState() {
        return alive && loaded && !reacting && fightTarget == 0
                && (goal == Brain.Goal.WORK || watch() && goal == Brain.Goal.PATROL)
                ? EmploymentState.WORKING : EmploymentState.FREE;
    }
    public boolean working() { return employmentState() == EmploymentState.WORKING; }
    public boolean watch() { return role == Role.GUARD || role == Role.LAWMAN; }
    public int lootTaken;
    public final List<Memory> memories = new ArrayList<>();
    // the mind's current state, for the inspector
    public Brain.Goal goal = Brain.Goal.IDLE;
    public String goalReason = "", lastOutcome = "", nativeAction = "-", perceived = "";
    public final List<String> scores = new ArrayList<>();
    public double nextThink, goalSince;
    public float gx, gy, gz;
    /** A long trip's waypoints (indices into the map's travel graph), the next one, and the trip's end. */
    public final RouteBook routeBook=new RouteBook();
    public double navPauseUntil;
    public float goalY;
    public int[] legs;
    public int legAt;
    public float legsX, legsY, legsZ;
    public boolean hasGoalPos, seesTarget;
    /** Sprinting now (AiSystem.pace): it costs stamina, and it is given up when the breath runs short. */
    public boolean sprinting;
    public int routeCells;
    public boolean stuck;
    public long fightTarget, socialPartner, aidTarget;
    public float socialSide;
    /** People they set out to talk with but could find no way to (a fence, a pen, a locked door between): until when they stop trying. */
    public final java.util.Map<Long, Double> unreachable = new java.util.HashMap<>();
    public int conversations;
    /** Under the player's Friends (they will not report the player) and Charm Person (they will not fight the player), until these times. */
    public double friendUntil = -1, charmUntil = -1;
    public int patrolIndex;
    public String arrestJurisdiction = "";
    public double busyUntil, arrestedUntil = -1, diedAt = -1, lastDialogAt = -99, chatUntil = -1;
    /** The body to search, once dead (0: none). */
    public long corpse;
    public double collectAgain = -99, leaveUntil = -99;
    /** When it reached the spot it went to look into (the investigation's clock). */
    public double arrivedAt = -99;
    /** The weapon knocked from the hand in a fall, where it lies (picked up again on getting up, if within reach). */
    /** Alive and in Sydney's hands (struck off balance): the mind waits, the body is simulated (see combat.Reactions). */
    /** Progress to the next footfall (1 = a foot comes down). */
    public float stepClock;
    public int pickpocketLevel = 1;
    public boolean hostileToPlayer;
    public float aggroRange = 12;
    public float spawnX, spawnZ;
    /** A line said aloud (shown over their head) until a time. */
    public String speech;
    public double speechUntil;
    /** What it is aware of, by actor id (stealth: Unaware -> Curious -> Investigating -> Alert). */
    public final java.util.Map<Long, Stealth.Aware> aware = new java.util.HashMap<>();
    /** An officer's questioning on a search: who was asked about the one sought, and when (asked once a while each). */
    public final java.util.Map<Long, Double> questioned = new java.util.HashMap<>();
    public double askAgain;
    /** A bounty hunter's snares set on this hunt, and when the next may be. */
    public int snaresSet;
    public double snareAgain;
    /** The heading the mind wants the body to face (NaN: wherever it is going); the engine agent's own heading. */
    public float wantYaw = Float.NaN, agentYaw;
    /** Its velocity smoothed over a few physics steps: where it is heading, as a body turns to it (not step to step). */
    float svx, svz;
    /** Placed somewhere new (a teleport): the motion it had is not carried there. */
    public void resetMotion() { svx = svz = 0; vx = vz = 0; wantYaw = Float.NaN; tactic = null; nextTactic = 0; }
    /** The search it is on (a foe lost from sight, a body found), or null; and until when it stays watchful after giving one up. */
    public Hunt hunt;
    public double vigilantUntil = -1;
    /** When the farmer last filled the trough (a full set of nest boxes is not left for ever behind a hungry herd). */
    public double lastTroughFill = -999;
    /** The desire last written to each of the agent's action facts. */
    final float[] facts = new float[AiSystem.ACTIONS];
    long targetBody = -1;

    public Npc(long id, String name, Role role, String title, float x, float z) {
        super(id, name, 28);
        this.role = role; this.title = title;
        this.x = x; this.z = z; this.y = DevMap.FLOOR;
        this.homeX = this.postX = this.spawnX = x; this.homeZ = this.postZ = this.spawnZ = z;
        this.homeJurisdiction = DevMap.jurisdiction(x, z);
        this.team = role == Role.BANDIT || role == Role.BRUTE ? 3 : role == Role.THIEF ? 4 : 2;
        this.hostileToPlayer = role == Role.BANDIT || role == Role.BRUTE;
        this.sheathed = true;   // (0.2.2) people go about with their weapons put away until there is a use for them
        skills.setLevel(Skill.AGILITY, defaultAgility(role));
    }

    public static int defaultAgility(Role role) { return switch (role) { case GUARD -> 18; case BANDIT -> 16; case BRUTE -> 8; case THIEF -> 25; case WORKER, FARMER -> 6; default -> 5; }; }

    /** A line said aloud, over its head until a time. */
    public void say(String text, double until) { speech = text; speechUntil = until; }

    public boolean combatant() { return summonOwner != 0 || role == Role.HUNTER || role == Role.KNIGHT || role == Role.SCHOLAR || watch() || role == Role.BANDIT || role == Role.BRUTE; }

    /** What this NPC believes about an actor's crimes (witnessed or heard), strongest first. */
    public Memory crimeKnown(long culprit) {
        Memory best = null;
        for (Memory m : memories) if (m.crime() && m.subject == culprit && (best == null || best.settled && !m.settled || best.settled == m.settled && m.confidence > best.confidence)) best = m;
        return best;
    }

    public void remember(Memory m, int cap) {
        for (int i = memories.size() - 1; i >= 0; i--) {
            Memory old = memories.get(i);
            if (!old.sameIncident(m)) continue;
            m.influenceTried|=old.influenceTried;m.withheld|=old.withheld;
            m.settled |= old.settled; old.settled = m.settled;
            if (old.witnessed() && m.witnessed()) { m.reported |= old.reported; old.reported = m.reported; }
            if (old.witnessed() && !m.witnessed() || old.witnessed() == m.witnessed() && old.confidence >= m.confidence) m = old;
            memories.remove(i);
        }
        memories.add(m);
        while (memories.size() > cap) {
            // forget the oldest that is not an unreported witnessed crime
            int drop = 0;
            for (int i = 0; i < memories.size(); i++) if (!(memories.get(i).crime() && !memories.get(i).settled && memories.get(i).witnessed() && !memories.get(i).reported)) { drop = i; break; }
            memories.remove(drop);
        }
    }

    // ------------------------------------------------------------------ Interactable
    @Override public String label() { return name + (alive ? "" : " (dead)"); }
    @Override public float ax() { return x; }
    @Override public float ay() { return y + 1.1f; }
    @Override public float az() { return z; }
    @Override public float focusRadius() { return 0.5f; }
    private static final float[] COLUMN = {-0.5f, 0, 0.5f, 0.75f};   // waist, chest, shoulders, head
    @Override public float[] focusColumn() { return COLUMN; }
    @Override public boolean exists() { return alive && loaded; }

    public String dist(Rpg g, Actor a, float range) {
        if (!alive) return "They're dead.";
        if (Math.hypot(a.x - x, a.z - z) > range + 0.6f) return "You're too far away.";
        if (!g.vox.sight(a.x, a.eyeY(), a.z, x, y + 1.5f, z)) return "You can't reach them from here.";
        return null;
    }

    public boolean canTalk(Rpg g) {
        return alive && loaded && !asleep() && !reacting && !hostileToPlayer && fightTarget == 0 && goal != Brain.Goal.FIGHT && goal != Brain.Goal.FLEE && goal != Brain.Goal.HIDE
                && cs.staggered <= 0 && g.time - cs.lastHurt >= 3 && arrestedUntil < g.state.minutes;
    }

    @Override public List<Option> options(Rpg g, Actor a) {
        List<Option> o = new ArrayList<>();
        float r = g.cfg.interactRange;
        var report=rpg.law.WitnessInfluence.pending(g,this);
        if(a==g.player && report!=null) {
            o.add(Option.on("witness_persuade","Ask them not to report",12,r));
            o.add(Option.on("witness_threaten","Threaten the witness",11,r));
            int price=rpg.law.WitnessInfluence.price(g,report);
            o.add(Option.when(a.has(Items.COINS,price),"witness_bribe","Offer "+price+" Gold for silence",11,"You do not have enough Gold.",r));
        }
        if (canTalk(g)) o.add(Option.on("talk", "Talk-to", 10, r));
        if (role == Role.MERCHANT || role == Role.FENCE || g.econ.shopOf(this) != null) {
            String why = g.econ.tradeRefusal(this, a);
            o.add(Option.when(why == null, "trade", "Trade", 9, why, r));
        }
        if ((role == Role.LAWMAN || role == Role.GUARD) && !g.law.refusesPeace(a.id,homeJurisdiction)) {
            int b = g.law.bounty(a.id, homeJurisdiction);
            o.add(Option.when(b > 0, "pay", "Pay bounty" + (b > 0 ? " (" + b + ")" : ""), 9, "You have no bounty in " + homeJurisdiction + ".", r));
        }
        if (!hostileToPlayer) {
            boolean lvl = a.skills.level(Skill.THIEVING) >= pickpocketLevel, room = a.canStore(new ItemStack(Items.COINS, 1));
            o.add(Option.when(lvl, "pickpocket", "Inspect pockets", 3, "You need Thieving level " + pickpocketLevel + ".", 1.6f));
            o.add(Option.when(lvl && room,"pickpocket-direct","Quick pickpocket",2,!lvl?"Your Thieving level is too low.":"Your inventory is too full.",1.6f));
        }
        return o;
    }

    @Override public Result perform(Rpg g, Actor a, String action, long req) {
        if(action.equals("witness_persuade") || action.equals("witness_threaten") || action.equals("witness_bribe"))return rpg.law.WitnessInfluence.attempt(g,this,a,action,req);
        switch (action) {
            case "talk" -> {
                String why = dist(g, a, g.cfg.interactRange);
                if (why != null) return Result.no(why);
                if (!canTalk(g)) return Result.no(name + " cannot stop to talk now.");
                // a wanted player who walks up to the watch is dealt with, however small the bounty (gamefixes)
                if (a == g.player && (role == Role.GUARD || role == Role.LAWMAN) && g.law.bounty(a.id, homeJurisdiction) > 0) {
                    g.law.confront(this, a);
                    g.state.commit(req, "Talked", a.id, id, DevMap.jurisdiction(x, z), 0, name + " (wanted)");
                    return Result.ok();
                }
                if (a == g.player) { g.ui.dialog(g.dialogue.talk(this)); knowsPlayer = true; }
                g.state.commit(req, "Talked", a.id, id, DevMap.jurisdiction(x, z), 0, name);
                lastDialogAt = g.time;
                return Result.ok();
            }
            case "trade" -> {
                String why = dist(g, a, g.cfg.interactRange);
                if (why == null) why = g.econ.tradeRefusal(this, a);
                if (why != null) return Result.no(why);
                if (a == g.player) g.ui.openShop(g.econ.shopOf(this));
                return Result.ok();
            }
            case "pay" -> {
                String why = dist(g, a, g.cfg.interactRange);
                if (why != null) return Result.no(why);
                return g.law.payBounty(a, this, req);
            }
            case "pickpocket", "pickpocket-direct" -> {
                String why = dist(g, a, 1.6f);
                if (why != null) return Result.no(why);
                if (a.skills.level(Skill.THIEVING) < pickpocketLevel) return Result.no("You need Thieving level " + pickpocketLevel + " to pickpocket " + name + ".");
                if(a==g.player && action.equals("pickpocket")) { if(g.ui!=null)g.ui.openContainer(pockets()); return Result.ok(); }
                g.interact.begin(new Work(a, this, "pickpocket", "Pickpocketing", 1.1f, false, g.time, () -> pickpocket(g, a)));
                return Result.started();
            }
            default -> { return Result.no("You can't do that."); }
        }
    }

    public rpg.objects.Container pockets() { return new Pocket(this); }

    private static final class Pocket extends rpg.objects.Container {
        private final Npc person;
        Pocket(Npc person) { super(person.id,person.name+"'s pockets","pockets",person.x,person.y,person.z,person.id,person.inv);this.person=person; }
        @Override public boolean exists(){return person.alive && person.loaded;}
        @Override public float dist(Actor actor){x=person.x;y=person.y;z=person.z;return super.dist(actor);}
        @Override public Result perform(Rpg g,Actor thief,String action,long request) {
            if(!person.alive)return Result.no("They are no longer alive.");
            if(action.equals("takeall"))return Result.no("Select one item to attempt a theft.");
            if(!action.startsWith("take:"))return Result.no("Choose an item to attempt a theft.");
            int slot=Integer.parseInt(action.substring(5));ItemStack selected=contents.get(slot);
            if(selected==null)return Result.no("That item has moved.");
            if(thief.skills.level(Skill.THIEVING)<person.pickpocketLevel)return Result.no("Your Thieving level is too low.");
            ItemStack stolen = selected.copyWhole(); stolen.markStolen(person.id);
            if(!thief.canStore(stolen))return Result.no("You need room for that item.");
            String why=person.dist(g,thief,1.6f);if(why!=null)return Result.no(why);
            g.interact.begin(new Work(thief,person,"pickpocket","Lifting "+selected.def.name,1.1f,false,g.time,()->{
                if(contents.get(slot)!=selected)return Result.no("The item has moved.");
                String range=person.dist(g,thief,1.6f);if(range!=null)return Result.no(range);
                if(!thief.canStore(stolen))return Result.no("You no longer have room.");
                long attempt=g.state.newRequestId();double chance=Math.min(0.92,0.55+(thief.skills.level(Skill.THIEVING)-person.pickpocketLevel)*0.03);
                boolean success=g.roll(attempt,person.id)<chance;
                if(!success) {
                    var event=g.state.commit(attempt,"PickpocketFailed",thief.id,person.id,DevMap.jurisdiction(person.x,person.z),0,"selected "+selected.def.id);
                    if(thief.eq.def(rpg.item.ItemDef.Slot.NECK)==Items.get("dodgy_necklace") && g.roll(attempt+1,person.id)<0.25)return Result.ok();
                    g.ai.notice(person,thief,"caught pickpocketing");thief.cs.staggered=0.8f;
                    person.remember(new Memory("THEFT",thief.id,person.id,DevMap.jurisdiction(person.x,person.z),g.state.minutes,1,"witnessed",event.id(),selected.value(),"caught lifting an item"),24);
                    g.law.theft(thief,person.id,selected,null,event.id());return Result.ok();
                }
                if (!thief.store(stolen)) return Result.no("You no longer have room.");
                contents.take(slot, selected.amount);
                var event=g.state.commit(attempt,"Pickpocketed",thief.id,person.id,DevMap.jurisdiction(person.x,person.z),0,"selected "+stolen.def.id);
                if (thief == g.player) {
                    g.chat.say("You lift " + stolen + " from " + person.name + ".");
                    if (g.ui != null) { g.ui.openContainer(person.pockets()); g.ui.received(stolen.def); }
                }
                g.law.theft(thief,person.id,stolen,null,event.id());g.prog.award(thief,Skill.THIEVING,8+person.pickpocketLevel*1.5,"pickpocket",event.id());
                return Result.ok();
            }));
            if (thief == g.player && g.ui != null) g.ui.closeAll();
            return Result.started();
        }
        @Override public Result take(Rpg g,Actor a,int slot,int count,long request){return perform(g,a,"take:"+slot,request);}
    }

    /** A conditional skill check (GDD 3.2): the roll is seeded by the request and stored with the event. */
    private Result pickpocket(Rpg g, Actor a) {
        long req = g.state.newRequestId();
        String why = dist(g, a, 1.6f);
        if (why != null) return Result.no(why);
        int lvl = a.skills.level(Skill.THIEVING);
        double chance = Math.min(0.92, 0.55 + (lvl - pickpocketLevel) * 0.03);
        double roll = g.roll(req, id);
        boolean ok = roll < chance;
        int coins = Math.min(inv.count(Items.COINS), 8 + pickpocketLevel);
        var ev = g.state.commit(req, ok ? "Pickpocketed" : "PickpocketFailed", a.id, id, DevMap.jurisdiction(x, z), 0, String.format("chance=%.2f roll=%.3f", chance, roll));
        // the dodgy necklace (GDD 2.2 Thieving): a fumble that goes unnoticed one time in four
        boolean charm = !ok && a.eq.def(rpg.item.ItemDef.Slot.NECK) == Items.get("dodgy_necklace") && g.roll(req + 1, id) < 0.25;
        if (charm) { if (a == g.player) g.chat.say("Your fingers slip - but " + name + " doesn't notice. (Dodgy necklace)"); return Result.ok(); }
        if (!ok || coins <= 0) {
            if (a == g.player) g.chat.say(ok ? name + " has nothing worth taking." : "You fail to pick " + name + "'s pocket. They noticed!");
            if (!ok) {
                g.ai.notice(this, a, "caught pickpocketing");
                remember(new Memory("THEFT", a.id, id, DevMap.jurisdiction(x, z), g.state.minutes, 1f, "witnessed", ev.id(), 10, "caught pickpocketing"), 24);
                a.hp = Math.max(1, a.hp - 5);
                a.cs.staggered = 0.8f;
            }
            return Result.ok();
        }
        ItemStack s = new ItemStack(Items.COINS, coins, id, true, id);
        if (!a.canStore(s)) return Result.no("You need room for those coins.");
        var taken = inv.remove(Items.COINS, coins);
        if (taken == null) return Result.no("Those coins have moved.");
        a.store(s);
        if (a == g.player && g.ui != null) g.ui.received(s.def);
        g.law.theft(a, id, s, null, ev.id());
        g.prog.award(a, Skill.THIEVING, 8 + pickpocketLevel * 1.5, "pickpocket", ev.id());
        if (a == g.player) g.chat.say("You pick " + name + "'s pocket.");
        return Result.ok();
    }


    @Override public String inspect(Rpg g) {
        return String.format("%s #%d (%s) at (%.1f, %.1f) hp %.0f/%.0f %s", name, id, role, x, z, hp, maxHp(), loaded ? "loaded" : "UNLOADED (abstract)");
    }

    @Override public long id() { return id; }
    @Override public long owner() { return 0; }

}
