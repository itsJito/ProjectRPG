package rpg.ai;

/**
 * A search (gamefixes: NPCs lose the player). Nobody knows where someone is unless their senses say so: when a foe
 * slips out of sight, the NPC keeps only where it last placed them - seen, or heard - and goes there. Arrived and
 * still without them, it searches: the area widens with the time since the last clue (as far as someone could have
 * got), and the spots it checks are the ones it cannot already see - round corners, behind cover. A fresh clue (a
 * glimpse, a footstep heard, a body found) moves the search; with none for long enough it gives up. Guards of the same
 * force share a search and spread over it. The area is what the player's minimap shows as "being searched".
 */
public final class Hunt {
    /** Who is sought (0: someone unknown - the culprit behind a body or blood found), and why. */
    public long target;
    public long source, lastTrail;
    public float lookYaw;
    public String why;
    /** The last known location: where the last clue put them, and when; where the search is centred. */
    public float x, z;
    public double clueAt, startedAt;
    /** The heading of the last clue (where they seemed to be going), m/s. */
    public float vx, vz;
    /** The spot being checked now, and until when it is looked round once reached. */
    public float px, pz;
    public boolean hasPoint, reachedLkp;
    public double lookUntil = -1;
    public int checked;
    /** How long it is kept up with no new clue (seconds), and whether it was joined from another's. */
    public float patience;
    public boolean joined;

    public Hunt(long target, String why, float x, float z, double now, float patience) {
        this.target = target; this.why = why; this.x = x; this.z = z; this.clueAt = now; this.startedAt = now; this.patience = patience;
    }

    /** How far from the last known location the sought could be by now: walking pace for the time since, within limits. */
    public float radius(double now) { return (float) Math.min(16, 3 + (now - clueAt) * 0.55); }

    /** A new clue: the search moves to it (and the spot being checked is dropped if it is now far off). */
    public void clue(float cx, float cz, float cvx, float cvz, double now) {
        x = cx; z = cz; vx = cvx; vz = cvz; clueAt = now;
        if (hasPoint && Math.hypot(px - cx, pz - cz) > radius(now) + 2) hasPoint = false;
        reachedLkp = false;
    }

    public boolean expired(double now) { return now - clueAt > patience; }
}
