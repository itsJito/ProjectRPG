package rpg.interact;

import rpg.Rpg;
import rpg.actor.Actor;
import rpg.item.ItemStack;
import rpg.item.Items;
import rpg.objects.Container;
import rpg.objects.Door;
import rpg.skill.Skill;

/**
 * Lockpicking (0.3.7, gamefixes: the lockpicking overhaul) - one hidden angle on a half circle, found by feel.
 *
 * <p>A pick is set somewhere on a 180-degree arc above the keyway, and the cylinder is turned against it with the
 * tension wrench. The nearer the pick to the lock's hidden sweet spot, the further the cylinder turns: inside the sweet
 * spot it turns all the way and the lock opens; in the partial zones either side of it, it turns as far as the pick is
 * near the centre and then binds; anywhere else it binds at once. A bound cylinder is the pick straining against the
 * pins: it wears while it does, and only then, until it snaps. Its wear stays with it - between tries and between
 * locks - until it breaks.
 *
 * <ul>
 *   <li>sweet spot, degrees: 60 x 2^-difficulty x (0.82 + 0.6 x skill / 100)</li>
 *   <li>each partial zone, degrees: (26 - 4 x difficulty) x (0.775 + 1.5 x skill / 100)</li>
 *   <li>straining before a pick snaps, seconds: 2, 1, 0.75, 0.5, 0.25 (Novice to Master) x (1 + 0.5 x skill / 100)</li>
 * </ul>
 * The sweet spot is rolled afresh for each attempt at a lock. There is no level a lock asks: any lock opens to any hand
 * given picks and patience; a harder one only hides a smaller target and breaks picks faster. Below Thieving
 * {@link #FREE_HANDS_LEVEL} a lockpick must be carried; from Thieving {@link #KEEP_ANGLE_LEVEL} a fresh pick goes back
 * in where the last one was, not straight up. Thieving is trained by every lock opened (by how hard it was) and every
 * pick broken.
 */
public final class Lockpicking {
    private final Rpg g;
    public Lockpicking(Rpg g) { this.g = g; }

    // ------------------------------------------------------------------ the rules
    /** A lock's grade, with how long a pick lasts straining in it at Thieving 1 (seconds). */
    public enum Difficulty {
        NOVICE("Novice", 1, 2f), APPRENTICE("Apprentice", 2, 1f), ADEPT("Adept", 3, 0.75f), EXPERT("Expert", 4, 0.5f), MASTER("Master", 5, 0.25f);
        public final String title; public final int rank; public final float breakSeconds;
        Difficulty(String title, int rank, float breakSeconds) { this.title = title; this.rank = rank; this.breakSeconds = breakSeconds; }
        /** A lock's grade from the Thieving level it was built against (its lock level). */
        public static Difficulty of(int lockLevel) {
            return lockLevel < 10 ? NOVICE : lockLevel < 25 ? APPRENTICE : lockLevel < 45 ? ADEPT : lockLevel < 70 ? EXPERT : MASTER;
        }
    }
    /** From this Thieving level no lockpick need be carried (a bent pin does); from KEEP_ANGLE_LEVEL a new pick goes in where the last was. */
    public static final int FREE_HANDS_LEVEL = 50, KEEP_ANGLE_LEVEL = 25;
    /** The pick's arc, degrees each side of straight up. */
    public static final float HALF_ARC = 90;
    /** How fast the cylinder turns under the wrench and springs back when let go (share of a full turn a second). */
    public static final float TURN_RATE = 1.4f, RETURN_RATE = 3f;
    /** How far a partial zone's best turns the cylinder (it never opens), and how little counts as already turning. */
    public static final float PARTIAL_MAX = 0.9f, AT_REST = 0.03f;
    /** Thieving XP: per difficulty rank for a lock opened; and a broken pick's, a base and per rank. */
    public static final float OPEN_XP = 22, BREAK_XP = 3, BREAK_XP_RANK = 1.5f;

    public static float sweetWidth(Difficulty d, int skill) { return 60f * (float) Math.pow(2, -d.rank) * (0.82f + 0.6f * skill / 100f); }
    public static float partialWidth(Difficulty d, int skill) { return (26f - 4f * d.rank) * (0.775f + 1.5f * skill / 100f); }
    public static float breakSeconds(Difficulty d, int skill) { return d.breakSeconds * (1 + 0.5f * skill / 100f); }

    /**
     * How far the cylinder can turn with the pick at an angle (0..1): all the way in the sweet spot, part way in a
     * partial zone (as near the centre as it is), not at all outside.
     */
    public static float limit(float pick, float target, Difficulty d, int skill) {
        float off = Math.abs(pick - target), sweet = sweetWidth(d, skill) / 2, partial = partialWidth(d, skill);
        if (off <= sweet) return 1;
        if (off <= sweet + partial) return PARTIAL_MAX * (1 - (off - sweet) / partial);
        return 0;
    }

    // ------------------------------------------------------------------ an attempt
    /** One go at one lock: its hidden angle, where the pick is, how far the cylinder has turned, and how it is going. */
    public static final class Attempt {
        public final Actor who;
        public final rpg.interact.WorldObject lock;
        public final Difficulty difficulty;
        public final int skill;
        /** The hidden sweet angle (degrees from straight up, - left .. + right). */
        public final float target;
        public float pick, cylinder;
        public boolean struggling, open, over;
        /** The pick in use (null: an improvised one, Thieving 50 and up with none carried) and an improvised pick's wear. */
        public ItemStack using;
        public float improvisedWear;
        public int broken;
        boolean tampered;
        public String outcome;
        Attempt(Actor who, rpg.interact.WorldObject lock, Difficulty d, int skill, float target) { this.who = who; this.lock = lock; this.difficulty = d; this.skill = skill; this.target = target; }
        /** What is left of the pick in use (1 new .. 0 snapped). */
        public float durability() { return 1 - (using == null ? improvisedWear : using.wear); }
    }

    /** Whether something can be picked: a locked door or a locked container. */
    public static boolean locked(rpg.interact.WorldObject o) { return o instanceof Door d ? d.locked : o instanceof Container c && c.locked; }
    public static int lockLevel(rpg.interact.WorldObject o) { return o instanceof Door d ? d.lockLevel : o instanceof Container c ? c.lockLevel : 1; }
    static long owner(rpg.interact.WorldObject o) { return o.owner; }

    /** Why this actor cannot set to work on a lock (null: they can). */
    public static String refusal(Actor a) {
        if (a.skills.level(Skill.THIEVING) < FREE_HANDS_LEVEL && pickOf(a) == null) return "You need a lockpick.";
        return null;
    }
    /** The lockpick the actor works with: the most worn one carried first (a pick is used until it breaks). */
    public static ItemStack pickOf(Actor a) {
        ItemStack best = null;
        for (var grid : a.grids()) for (ItemStack s : grid.stacks()) if (s.def == Items.LOCKPICK && (best == null || s.wear > best.wear)) best = s;
        return best;
    }

    /** Sets to work on a lock: the hidden angle rolled for this attempt, the pick put in (straight up, or where the last one was). */
    public Attempt begin(Actor a, rpg.interact.WorldObject lock) {
        int skill = a.skills.level(Skill.THIEVING);
        Difficulty d = Difficulty.of(lockLevel(lock));
        float target = (float) ((g.roll(g.state.newRequestId(), lock.id ^ a.id) * 2 - 1) * (HALF_ARC - 4));
        Attempt t = new Attempt(a, lock, d, skill, target);
        t.using = pickOf(a);
        t.pick = skill >= KEEP_ANGLE_LEVEL ? lastAngle.getOrDefault(a.id, 0f) : 0;
        g.state.commit("LockpickBegun", a.id, lock.id, lock.jurisdiction(), 0, String.format("%s, %s lock", lock.name, d.title.toLowerCase()));
        return t;
    }
    /** Where each picker's last pick stood (for KEEP_ANGLE_LEVEL). */
    private final java.util.Map<Long, Float> lastAngle = new java.util.HashMap<>();

    /**
     * A moment of an attempt: the pick moved toward an angle (only while the cylinder is at rest - a turned cylinder
     * holds the pick where it is), and the wrench held or let go. Returns what happened: null, or a word for it.
     */
    public String tick(Attempt t, float dt, float wantPick, boolean torque) {
        if (t.over) return null;
        if (!locked(t.lock)) { t.over = true; t.outcome = "It isn't locked any more."; return t.outcome; }
        if (t.cylinder <= AT_REST) t.pick = Math.max(-HALF_ARC, Math.min(HALF_ARC, wantPick));
        lastAngle.put(t.who.id, t.pick);
        float limit = limit(t.pick, t.target, t.difficulty, t.skill);
        String said = null;
        if (torque) {
            if (!t.tampered) { t.tampered = true; tamper(t); }
            if (t.cylinder < limit) { t.cylinder = Math.min(limit, t.cylinder + TURN_RATE * dt); t.struggling = false; }
            else t.struggling = limit < 1;
            if (limit >= 1 && t.cylinder >= 1) { opened(t); return t.outcome; }
            if (t.struggling) said = strain(t, dt);
        } else {
            t.cylinder = Math.max(0, t.cylinder - RETURN_RATE * dt);
            t.struggling = false;
        }
        return said;
    }

    /** The pick strains against the pins: it wears, and at the end of its life it snaps. */
    private String strain(Attempt t, float dt) {
        float wear = dt / breakSeconds(t.difficulty, t.skill);
        if (t.using != null) t.using.wear += wear; else t.improvisedWear += wear;
        if (t.durability() > 0) return null;
        // snapped: the cylinder springs back; a carried pick is gone, an improvised one is bent anew
        t.broken++;
        t.cylinder = 0; t.struggling = false;
        if (t.using != null) { remove(t.who, t.using); g.state.burn(Items.LOCKPICK, 1); }
        t.improvisedWear = 0;
        var ev = g.state.commit("LockpickBroken", t.who.id, t.lock.id, t.lock.jurisdiction(), 0, t.lock.name);
        g.prog.award(t.who, Skill.THIEVING, BREAK_XP + BREAK_XP_RANK * t.difficulty.rank, "broke a lockpick", ev.id());
        if (g.sfx != null) g.sfx.play("metal.light", t.lock.x, t.lock.y, t.lock.z, 0.5f, 1.6f, 6);
        t.using = pickOf(t.who);
        // a fresh pick: straight up again, unless the hand has learned to put it back where it was
        if (t.skill < KEEP_ANGLE_LEVEL) t.pick = 0;
        if (t.using == null && t.skill < FREE_HANDS_LEVEL) { t.over = true; t.outcome = "Your last lockpick snaps. You have none left."; return t.outcome; }
        return "The lockpick snaps.";
    }

    /** Takes one particular stack out of whatever grid holds it. */
    private static void remove(Actor a, ItemStack s) {
        for (var grid : a.grids()) for (int i = 0; i < grid.size(); i++) if (grid.get(i) == s) { grid.set(i, null); return; }
    }

    /** The first turn of the wrench: it is lock-breaking now (on someone else's lock, a crime as the law sees one). */
    private void tamper(Attempt t) {
        long owner = owner(t.lock);
        if (owner != 0 && owner != t.who.id && !(t.lock instanceof Container c && !c.isTheft(t.who))) {
            var ev = g.state.commit("LockTampered", t.who.id, t.lock.id, t.lock.jurisdiction(), 0, t.lock.name);
            g.law.crime(t.who, "LOCKPICKING", owner, 0, t.lock, ev.id());
        }
    }

    private void opened(Attempt t) {
        t.open = true; t.over = true;
        if (t.lock instanceof Door d) d.setLocked(g, false); else if (t.lock instanceof Container c) c.locked = false;
        var ev = g.state.commit("LockPicked", t.who.id, t.lock.id, t.lock.jurisdiction(), 0, String.format("%s (%s, %d picks broken)", t.lock.name, t.difficulty.title.toLowerCase(), t.broken));
        g.prog.award(t.who, Skill.THIEVING, OPEN_XP * t.difficulty.rank, "picked a " + t.difficulty.title.toLowerCase() + " lock", ev.id());
        if (g.sfx != null) g.sfx.play("lock", t.lock.x, t.lock.y, t.lock.z, 0.7f);
        t.outcome = "The lock clicks open.";
    }

    /**
     * Someone sets to pick a lock (a door's, a container's): the player at the lock's own window, worked by their hands;
     * anyone else - and a game with no screen - works it through at once by the careful search ({@link #work}).
     */
    public Result start(Actor a, rpg.interact.WorldObject lock) {
        if (!locked(lock)) return Result.no("It isn't locked.");
        String why = refusal(a);
        if (why != null) return Result.no(why);
        Attempt t = begin(a, lock);
        if (a == g.player && g.ui != null) { g.ui.openLockpick(t); return Result.started(); }
        work(t);
        if (a == g.player) g.chat.say(t.open ? "You pick the lock." + (t.broken > 0 ? " (" + t.broken + " pick" + (t.broken == 1 ? "" : "s") + " broken)" : "") : t.outcome);
        return t.open ? Result.ok() : Result.no(t.outcome);
    }

    /** Gives an attempt up (the player stepped away, or let go of it). */
    public void abandon(Attempt t) { if (!t.over) { t.over = true; t.outcome = "You stop picking the lock."; } }

    // ------------------------------------------------------------------ hands that work by themselves
    /**
     * An attempt worked through without a player's hands (anyone else, and a game without a screen): the pick set at
     * points along the arc and the wrench tried at each - the angle that turned it furthest kept, and the search
     * narrowed round it - as a careful hand would. Returns the seconds it took.
     */
    public float work(Attempt t) {
        final float step = 1 / 30f;
        float time = 0, span = HALF_ARC, centre = 0, stride = 15;
        float best = 0, bestAngle = 0;
        for (int round = 0; round < 40 && !t.over; round++) {
            for (float a = centre - span; a <= centre + span && !t.over; a += stride) {
                // the cylinder back to rest, the pick to the angle
                while (t.cylinder > 0 && !t.over) { tick(t, step, t.pick, false); time += step; }
                tick(t, step, a, false); time += step;
                // the wrench, until it turns no further
                float was = -1;
                while (!t.over && t.cylinder > was + 1e-4f) { was = t.cylinder; tick(t, step, a, true); time += step; }
                if (!t.over && t.cylinder > best) { best = t.cylinder; bestAngle = a; }
            }
            if (t.over) break;
            centre = best > 0 ? bestAngle : centre;
            span = Math.max(1, best > 0 ? stride : span);
            stride = Math.max(0.5f, stride / 2.5f);
        }
        if (!t.over) abandon(t);
        return time;
    }
}
