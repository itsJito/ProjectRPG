package rpg.ai;

/**
 * One NPC looking into something it noticed but cannot place (gamefixes: the investigation overhaul; GDD 3.10 awareness
 * states). Not one routine for everyone: what an NPC does about a noise in the dark depends on who they are and how they
 * feel - a guard calls out and goes to see with his hand on his sword, and checks round the spot; a bold farmhand goes
 * near enough to peek; a timid shopkeeper calls out from where she stands and, if it comes again, goes to fetch the
 * guard instead; a thief takes it for the law and slips away to hide; an outlaw comes looking for a fight. Night, the
 * dark at the spot, a scream or the clash of blades, and company close by all move the choice.
 *
 * <p>The plan is chosen once (and again if the noise comes back), then carried through in stages; every stage is shown
 * over the NPC's head and in the AI inspector, so the player can read what they are thinking.
 */
public final class Investigation {
    /** What set it off. */
    public enum Stimulus { SOUND, GLIMPSE, VIOLENCE }
    /** The plan, chosen from temperament and circumstance. */
    public enum Plan { CHECK, SEARCH, PEEK, CALL_OUT, FETCH_HELP, HIDE, CONFRONT }
    /** Where in the plan they are. */
    public enum Stage { NOTICED, CALLING, APPROACHING, LOOKING, SEARCHING, FETCHING, HIDING, DONE }

    public final long subject;
    public float x, z;
    public final Stimulus stimulus;
    public Plan plan;
    public Stage stage = Stage.NOTICED;
    public double startedAt, stageAt, lastClue;
    /** How brave they felt about it when they chose (for the inspector), and why. */
    public float courage;
    public String why = "";
    /** Search spots checked, the current one; and the guard being fetched. */
    public int checked;
    public float px, pz;
    public boolean hasSpot;
    public long helper;
    /** Where they are hiding. */
    public float hx, hz, lookYaw;
    /** How many times the noise has come back since the plan was made. */
    public int repeats;

    public Investigation(long subject, float x, float z, Stimulus s, double now) {
        this.subject = subject; this.x = x; this.z = z; this.stimulus = s; this.startedAt = now; this.stageAt = now; this.lastClue = now;
    }

    public void stage(Stage s, double now) { stage = s; stageAt = now; }
    public double inStage(double now) { return now - stageAt; }

    /** A short word for what they are doing (the inspector, and the icon over their head). */
    public String label() {
        return switch (stage) {
            case NOTICED -> "noticed something";
            case CALLING -> "calling out";
            case APPROACHING -> plan == Plan.PEEK ? "creeping closer to look" : plan == Plan.CONFRONT ? "coming for a fight" : "going to look";
            case LOOKING -> "looking round the spot";
            case SEARCHING -> "searching nearby (" + checked + ")";
            case FETCHING -> "fetching help";
            case HIDING -> "hiding";
            case DONE -> "settled";
        };
    }
}
