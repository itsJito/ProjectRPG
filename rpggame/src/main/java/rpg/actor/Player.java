package rpg.actor;

import rpg.combat.Spells;

/** The player: an actor with a camera, a selected spell and its own death state. */
public final class Player extends Actor {
    public Spells.Spell spell = Spells.Spell.WIND_STRIKE;
    /** The one spellbook open to the player (GDD 2.5), changed at an altar. */
    public Spells.Book book = Spells.Book.SUN;
    public boolean firstPerson;
    public boolean morphCameraSaved, morphFirstPerson;
    public float camDist = 3.6f;
    public double diedAt = -1;
    public boolean sprinting;
    /** The run key is held (a run; with a sprint armed, a sprint). */
    public boolean running;
    public float walked;
    /** Where the posed figure's eyes were last drawn (the first-person camera rides them). */
    public float headX, headY, headZ, headYaw;
    public boolean headValid;

    public Player(long id, String name) { super(id, name, 28); team = 1; }

    /**
     * One of the radial wheel's eight quick slots (gamefixes): the item it holds (by kind, as the action bar binds), and
     * the stack count last seen of it - the wheel draws the item's icon from it and uses it by its first action.
     */
    public record QuickSlot(rpg.item.ItemDef item, int count) {}
    /** The radial wheel (TAB held): eight slots, nullable, clockwise from the top. Saved with the player. */
    public final QuickSlot[] quick = new QuickSlot[8];

    /** Brings each slot's count up to date; a slot whose item is gone (eaten, dropped, sold, stolen) is cleared. */
    public void refreshQuick() {
        for (int i = 0; i < quick.length; i++) {
            QuickSlot q = quick[i];
            if (q == null) continue;
            int n = count(q.item());
            quick[i] = n <= 0 ? null : n == q.count() ? q : new QuickSlot(q.item(), n);
        }
    }

}
