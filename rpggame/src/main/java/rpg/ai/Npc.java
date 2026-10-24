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
public final class Npc extends Actor implements Interactable {
    public enum Role { MERCHANT, LAWMAN, GUARD, WORKER, VILLAGER, THIEF, CARTER, QUEST_GIVER, CONTACT, BANDIT, BRUTE, FARMER, UNDERTAKER, FENCE, BEGGAR, HEALER, PRIEST, HUNTER, SCHOLAR }

    public final Role role;
    public rpg.combat.Spells.Book spellBook = rpg.combat.Spells.Book.STANDARD;
    public final String title;
    public final String homeJurisdiction;
    /** Engine handles: the agent and its mind. */
    public long agent;
    /** Loaded: simulated in detail with a body; unloaded: resolved abstractly from the same state. */
    public boolean loaded = true;
    /** Needs, 0 (met) .. 1 (pressing). */
    public float hunger, fatigue, social;
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
    CombatTactics.Plan tactic;
    double nextTactic;
    public double lastLootAt, lootStarted = -99, lootUntil = -99;
    public boolean asleep() {
        return alive && !reacting && goal == Brain.Goal.SLEEP && !cs.busy()
                && Math.hypot(x - homeX, z - homeZ) <= 1.2 && Math.hypot(vx, vz) < 0.12;
    }
    public int lootTaken;
    public final List<Memory> memories = new ArrayList<>();
    // the mind's current state, for the inspector
    public Brain.Goal goal = Brain.Goal.IDLE;
    public String goalReason = "", lastOutcome = "", nativeAction = "-", perceived = "";
    public final List<String> scores = new ArrayList<>();
    public double nextThink, goalSince;
    public float gx, gy, gz;
    /** A long trip's waypoints (indices into the map's travel graph), the next one, and the trip's end. */
    public int[] legs;
    public int legAt;
    public float legsX, legsZ;
    public boolean hasGoalPos, seesTarget;
    public int routeCells;
    public boolean stuck;
    public long fightTarget, socialPartner, aidTarget;
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
        skills.setLevel(Skill.AGILITY, defaultAgility(role));
    }

    public static int defaultAgility(Role role) { return switch (role) { case GUARD -> 18; case BANDIT -> 16; case BRUTE -> 8; case THIEF -> 25; case WORKER, FARMER -> 6; default -> 5; }; }

    /** A line said aloud, over its head until a time. */
    public void say(String text, double until) { speech = text; speechUntil = until; }

    public boolean combatant() { return summonOwner != 0 || role == Role.HUNTER || role == Role.SCHOLAR || role == Role.GUARD || role == Role.BANDIT || role == Role.BRUTE; }

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
        return alive && loaded && !hostileToPlayer && fightTarget == 0 && goal != Brain.Goal.FIGHT && goal != Brain.Goal.FLEE && goal != Brain.Goal.HIDE
                && cs.staggered <= 0 && g.time - cs.lastHurt >= 3 && arrestedUntil < g.state.minutes;
    }

    @Override public List<Option> options(Rpg g, Actor a) {
        List<Option> o = new ArrayList<>();
        float r = g.cfg.interactRange;
        if (canTalk(g)) o.add(Option.on("talk", "Talk-to", 10, r));
        if (role == Role.MERCHANT || role == Role.FENCE) {
            String why = g.econ.tradeRefusal(this, a);
            o.add(Option.when(why == null, "trade", "Trade", 9, why, r));
        }
        if (role == Role.UNDERTAKER) {
            int held = g.undertaker.held(), fee = g.undertaker.fee();
            o.add(Option.when(held > 0, "reclaim", "Reclaim belongings" + (held > 0 ? " (" + fee + " coins)" : ""), 9, "He holds nothing of yours.", r));
        }
        if (role == Role.LAWMAN) {
            int b = g.law.bounty(a.id, homeJurisdiction);
            o.add(Option.when(b > 0, "pay", "Pay bounty" + (b > 0 ? " (" + b + ")" : ""), 9, "You have no bounty in " + homeJurisdiction + ".", r));
        }
        if (!hostileToPlayer) {
            boolean lvl = a.skills.level(Skill.THIEVING) >= pickpocketLevel, room = a.canStore(new ItemStack(Items.COINS, 1));
            o.add(Option.when(lvl && room, "pickpocket", "Pickpocket", 3, !lvl ? "You need Thieving level " + pickpocketLevel + "." : "Your inventory is too full.", 1.6f));
        }
        o.add(Option.on("examine", "Examine", 0, g.cfg.inspectRange));
        return o;
    }

    @Override public Result perform(Rpg g, Actor a, String action, long req) {
        if (action.equals("examine")) { if (a == g.player) g.chat.say(examine(g)); return Result.ok(); }
        switch (action) {
            case "talk" -> {
                String why = dist(g, a, g.cfg.interactRange);
                if (why != null) return Result.no(why);
                if (!canTalk(g)) return Result.no(name + " cannot stop to talk now.");
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
            case "reclaim" -> {
                String why = dist(g, a, g.cfg.interactRange);
                if (why != null) return Result.no(why);
                return g.undertaker.redeem(a, this);
            }
            case "pay" -> {
                String why = dist(g, a, g.cfg.interactRange);
                if (why != null) return Result.no(why);
                return g.law.payBounty(a, this, req);
            }
            case "pickpocket" -> {
                String why = dist(g, a, 1.6f);
                if (why != null) return Result.no(why);
                if (a.skills.level(Skill.THIEVING) < pickpocketLevel) return Result.no("You need Thieving level " + pickpocketLevel + " to pickpocket " + name + ".");
                g.interact.begin(new Work(a, this, "pickpocket", "Pickpocketing", 1.1f, false, g.time, () -> pickpocket(g, a)));
                return Result.started();
            }
            default -> { return Result.no("You can't do that."); }
        }
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
        var taken = inv.remove(Items.COINS, coins);
        ItemStack s = new ItemStack(Items.COINS, coins, id, true, id);
        a.store(s);
        g.law.theft(a, id, s, null, ev.id());
        g.prog.award(a, Skill.THIEVING, 8 + pickpocketLevel * 1.5, "pickpocket", ev.id());
        if (a == g.player) g.chat.say("You pick " + name + "'s pocket.");
        return taken == null ? Result.no("") : Result.ok();
    }

    @Override public String examine(Rpg g) {
        return name + ", " + title.toLowerCase() + ". Level " + skills.combatLevel() + (crimeKnown(g.player.id) != null ? ". They eye you with suspicion." : ".");
    }

    @Override public String inspect(Rpg g) {
        return String.format("%s #%d (%s) at (%.1f, %.1f) hp %.0f/%.0f %s", name, id, role, x, z, hp, maxHp(), loaded ? "loaded" : "UNLOADED (abstract)");
    }

    @Override public long id() { return id; }
    @Override public long owner() { return 0; }

    public boolean isQuestNpc() { return id == Ids.QUEST_GIVER || id == Ids.CONTACT; }
}
