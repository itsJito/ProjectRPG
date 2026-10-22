package rpg.actor;

import rpg.combat.Spells;

/** The player: an actor with a camera, a chosen melee style, a selected spell and its own death state. */
public final class Player extends Actor {
    /** Melee XP style: 0 Attack, 1 Strength, 2 Defense, 3 shared. */
    public int style = 0;
    public Spells.Spell spell = Spells.Spell.SPARK;
    /** The one spellbook open to the player (GDD 2.5), changed at an altar. */
    public Spells.Book book = Spells.Book.STANDARD;
    public boolean firstPerson;
    public float camDist = 3.6f;
    public double diedAt = -1;
    public boolean sprinting;
    /** The run key is held (a run; with a sprint armed, a sprint). */
    public boolean running;
    public float walked;
    /** Where the posed figure's eyes were last drawn (the first-person camera rides them). */
    public float headX, headY, headZ;
    public boolean headValid;

    public Player(long id, String name) { super(id, name, 28); team = 1; }

    public static final String[] STYLES = {"Attack (precise)", "Strength (forceful)", "Defense (guarded)", "Shared"};
}
