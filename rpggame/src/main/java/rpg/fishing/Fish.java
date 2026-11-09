package rpg.fishing;

import java.util.List;
import rpg.Rpg;
import rpg.actor.Actor;
import rpg.interact.Interactable;
import rpg.interact.Option;
import rpg.interact.Result;
import rpg.skill.Skill;

/**
 * One live fish in the water near the player (Fishery): where it is and how it moves, what it is doing and why, how hurt
 * it is. It is one of its water's stock - landing it, or a predator eating it, takes it out of that stock for good.
 *
 * <p>(0.3.6, gamefixes: fish had no collision or health bars) a fish is a body in the world like any creature: it can be
 * looked at and aimed at (its kind and combat level, how it is caught), it shows its health when hurt, it has a body that
 * swimmers and other fish cannot pass through, and the great ones fight back.
 */
public final class Fish implements Interactable {
    public enum State { SWIM, SHOAL, FEED, LURED, HOOKED, FLEE, HUNT, REST, SURFACE, DEFEND }

    /** Fish ids live in their own range, apart from every entity's (a fish is never mistaken for a person or an object). */
    public static final long ID_BASE = 0x4000000000000L;

    public final long id;
    public final FishKind kind;
    public final Fishery.Water water;
    /** Position (the middle of its body), velocity (m/s), heading and pitch (radians; yaw 0 faces -z as every body's does). */
    public float x, y, z, vx, vy, vz, yaw, pitch;
    /** Its size against its kind's (0.75 .. 1.2), its HP. */
    public final float size;
    public float hp;
    public State state = State.SWIM;
    public String why = "";
    /** The wander heading and depth it is easing toward; the time of its next decision. */
    public float wantYaw, wantY;
    public double thinkAt, stateSince, fleeUntil, biteAt, breathAt;
    /** When it was last hurt (its health shows for a while after). */
    public double lastHurt = -99;
    /** The swim cycle (tail beats), for drawing. */
    public float phase;
    /** How hungry a hunter is (0 fed .. 1 starving); the fish it is after; the lure it is going to or hooked on. */
    public float hunger;
    public Fish prey;
    public Fishery.Lure lure;
    /** Where the last fright came from. */
    public float fearX, fearZ;
    public boolean alive = true;
    /** Who struck it last (a fish dies to someone); negative: the swimmer a shark is after. */
    public long struckBy;

    Fish(long id, FishKind kind, Fishery.Water water, float x, float y, float z, float size) {
        this.id = id; this.kind = kind; this.water = water; this.x = x; this.y = y; this.z = z; this.size = size;
        this.hp = kind.health; this.wantY = y;
    }

    /** Its length (m), as it is drawn and as it is hit. */
    public float length() { return kind.length * size; }
    /** The half-thickness of its body (m): the radius of the capsule it is hit and pushed as. */
    public float girth() { return Math.max(0.03f, length() * kind.girth()); }
    /** Its mass (kg), as bodies push each other: a fish's weight grows with the cube of its length. */
    public float mass() { float l = length(); return Math.max(0.02f, l * l * l * 9f); }
    public float speed() { return (float) Math.sqrt(vx * vx + vy * vy + vz * vz); }
    public float maxHp() { return Math.max(1, kind.health); }
    /** The combat level its kind's skills give it. */
    public int combatLevel() { return kind.combatLevel(); }
    @Override public String label() { return kind.title + " (level " + combatLevel() + ")"; }

    // ------------------------------------------------------------------ as something in the world (Interactable)
    @Override public long id() { return id; }
    @Override public float ax() { return x; }
    @Override public float ay() { return y; }
    @Override public float az() { return z; }
    @Override public float focusRadius() { return Math.min(2.5f, girth() + 0.12f); }
    @Override public boolean exists() { return alive; }
    /** Its body as a capsule along its heading (the whale is long, not round). */
    @Override public float focusEntry(float ox, float oy, float oz, float dx, float dy, float dz) {
        float t = Fishery.capsule(this, ox, oy, oz, dx, dy, dz, 0.12f);
        return t < 0 ? Float.POSITIVE_INFINITY : t;
    }
    @Override public List<Option> options(Rpg g, Actor a) {
        String gear = switch (kind.method) { case NET -> "a small fishing net"; case ROD -> "a fishing rod"; case HARPOON -> "a harpoon"; case POT -> kind == FishKind.CRAB ? "a crab cage" : "a lobster pot"; };
        int level = a.skills.level(Skill.FISHING);
        String how = "Caught with " + gear + " from a fishing spot, at Fishing " + kind.level + (level < kind.level ? " (you have " + level + ")" : "") + ".";
        return List.of(Option.off("catch", "Catch", -1, how, 0));
    }
    @Override public Result perform(Rpg g, Actor a, String action, long req) { return Result.no("Fish are caught from a fishing spot, with the right gear."); }
    @Override public String inspect(Rpg g) {
        return String.format("%s #%d: %s%s, hp %.0f/%.0f, %.2f m, in %s, %.1f m/s", kind.title, id - ID_BASE, state.name().toLowerCase(), why.isEmpty() ? "" : " (" + why + ")", hp, maxHp(), length(), water.name, speed());
    }
}
