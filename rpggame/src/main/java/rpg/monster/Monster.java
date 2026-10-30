package rpg.monster;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import rpg.Rpg;
import rpg.actor.Actor;
import rpg.ai.Npc;
import rpg.interact.Option;
import rpg.interact.Result;
import rpg.skill.Skill;

/**
 * A monster (gamefixes, GDD 3.14): an actor like any other - skills that make its combat level, hit points, a body the
 * engine walks or flies, a Sydney ragdoll to fall in and lose limbs from, a corpse to search - with a mind of instinct
 * rather than routine: live drives (hunger, fear, aggression, territory, fatigue, social, curiosity), a lair it is tied
 * to, a behaviour state, and what it knows of the things around it (each sensed, scored as a threat, remembered for a
 * while). It is not a person: the law does not count its death, it trades with no one and it has no dialogue.
 */
public final class Monster extends Npc {
    public final MonsterKind kind;
    public int level;
    /** A dragon's breed (null for every other kind). */
    public MonsterKind.Breed breed;
    /** Its lair (0: a wanderer), and where that lair is. */
    public long lair;
    public float lairX, lairZ;

    /** The behaviour states of GDD 3.14. */
    public enum State { DORMANT, IDLE, PATROL, ALERT, INVESTIGATE, STALK, ENGAGE, FLEE, REGROUP, FEED, REST, RETURN }
    public State state = State.IDLE;
    public double stateSince;
    public String why = "";

    /** The drives, live (0..1), and this one's nudge of its species' weights (+-15%: no two goblins alike). */
    public float fear, aggression, territorial, curiosityNow;
    public final float[] nudge = new float[7];

    /** What it knows about one thing near it: where it was last sensed, when, by which sense, how much damage it has done, the threat it poses. */
    public static final class Known {
        public final long id;
        public float x, z, threat, damage;
        public double at;
        public String sense = "";
        public Known(long id) { this.id = id; }
    }
    public final Map<Long, Known> known = new HashMap<>();
    /** Its current target (0: none), since when, and the spot it is heading to. */
    public long target;
    public double targetSince = -99, nextSwitch;
    public float tx, tz, ty;
    /** Where it is roosting/perched (flyers), its flight height now, and where it means to be up to. */
    public float altitude, wantAltitude;
    /** A dead one's remains going to ash and mist (0 whole .. 1 gone): see {@link Monsters#CORPSE_SECONDS}. */
    public float dissolve;
    /** The cadence of its own attacks (natural strikes, ranged): next allowed, and the telegraph under way (a creature's wind-up). */
    public double nextStrike, nextRanged, windupUntil;
    public String windup;
    public double strikeAt = -99, windupStart = -99;
    public long strikeTarget;
    /** A carcass it is feeding on. */
    public long feedOn;
    /** Morale's share in this one: when its group's leader is the one the group follows. */
    public boolean leader;
    /** Haunters escalate the longer the living remain (seconds a living thing has been within its range). */
    public float disturbed;
    /** Stuck patterns (cheese protection): how long its target has been out of reach while in sight. */
    public float outOfReach;
    /** Running (its faster gait), and when it next makes a noise of its own. */
    public boolean running;
    public double nextVoice;
    /**
     * A fight's own rhythm: a bat's swoop (0 circling overhead, 1 diving in, 2 climbing away) and a pack raider's (0
     * waiting on the ring round the target, 1 darting in, 2 falling back); until when it holds, and which way it circles.
     */
    public int swoop;
    public double swoopUntil;
    public float orbit = 1;
    /** A gallery puppet: moved by hand, its mind left out. */
    public boolean puppet;

    public Monster(long id, MonsterKind kind, int level, float x, float z) {
        super(id, kind.title, Role.MONSTER, kind.title + " (level " + level + ")", x, z);
        this.kind = kind;
        this.level = level;
        this.domain = astratto.ai.AgentDomain.MONSTER;
        this.team = 40 + kind.ordinal();
        this.hostileToPlayer = false;          // hostility is a state, never a label
        this.sheathed = false;
        this.homeJurisdiction = rpg.world.DevMap.WILDS;
        stats();
        fit();
        restore();
    }

    /**
     * Its skills from its level (as a person's combat level is from theirs): a monster of level L fights with melee
     * skills that make combat level L, its Health a little above them (the brutes more), and its ranged or magic skill
     * where it has those attacks.
     */
    public void stats() {
        int s = Math.max(1, Math.round(level / 1.15f));
        float hp = switch (kind.archetype) { case SOLITARY_BRUTE, APEX_SOVEREIGN -> 1.6f; case HARASSER -> 0.75f; case BOUND_DEAD -> 1.1f; default -> 1.0f; };
        for (Skill sk : new Skill[]{Skill.ATTACK, Skill.STRENGTH, Skill.DEFENSE}) skills.setStat(sk, s);
        skills.setStat(Skill.HEALTH, Math.max(3, Math.round(s * hp)));
        skills.setStat(Skill.AGILITY, Math.max(1, kind.archetype == MonsterKind.Archetype.HARASSER ? s * 2 : s));
        if (kind.ranged != null) { skills.setStat(Skill.ARCHERY, s); skills.setStat(Skill.MAGIC, Math.max(10, s)); }
    }

    /** The body and hitbox for its kind (the gamefixes sizes). */
    public void fit() {
        radius = kind.hw * 0.5f;
        height = kind.hh;
    }

    @Override public void fitBody() { fit(); }
    @Override public float ragdollScale() { return kind.form == MonsterKind.Form.HUMANOID ? kind.scale() : Math.max(0.5f, Math.min(2.5f, kind.mh / 1.8f)); }
    @Override public int sydneyProfile() { return kind.sydney; }
    // (its body's y is already up in the air when it flies: the altitude is only how far that is above the ground - added
    // again here it put a bat's chest metres over the bat, where every arrow, spell and eye was aimed)
    @Override public float eyeY() { return y + height * (kind.form == MonsterKind.Form.HUMANOID ? 0.9f : 0.7f); }
    @Override public float chestY() { return y + height * 0.55f; }

    /** How big its figure is against a person's (its own skeleton's scale): the humanoids' swings reach as far as their arms. */
    @Override public float figureScale() { return kind.form == MonsterKind.Form.HUMANOID ? kind.scale() : 1; }

    public String label() { return (breed != null ? breed.title + " dragon" : kind.title) + "  (level " + level + ")"; }

    @Override public List<Option> options(Rpg g, Actor a) {
        return List.of();
    }
    @Override public Result perform(Rpg g, Actor a, String action, long req) {
        return Result.no("You can't do that.");
    }
    @Override public String inspect(Rpg g) {
        return String.format("%s level %d: %s (%s); hunger %.0f%% fear %.2f aggression %.2f territory %.2f fatigue %.0f%%; target %s; lair %d at (%.0f, %.0f)",
                kind.title, level, state, why, hunger * 100, fear, aggression, territorial, fatigue * 100, g.nameOf(target), lair, lairX, lairZ);
    }
}
