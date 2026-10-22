package rpg.interact;

import java.util.List;
import rpg.Rpg;
import rpg.actor.Actor;

/**
 * Anything physically present that can be interacted with (GDD 3.2): it has an anchor and a reach, offers the actions
 * available in the present context - enabled, or disabled with a reason - and carries one out on request, revalidating
 * everything at the commit. The UI is not an authority: a request by action id goes through the same validation as
 * the primary button.
 */
public interface Interactable {
    long id();
    String label();
    float ax();
    float ay();
    float az();
    /** Radius of the focus volume round the anchor. */
    default float focusRadius() { return 0.6f; }
    /**
     * A tall thing's focus volume is a column: these heights above the anchor (and below it, if negative), each a sphere
     * of focusRadius. A person is aimable by the head and shoulders over a counter as well as by the chest.
     */
    default float[] focusColumn() { return ZERO; }
    float[] ZERO = {0};
    /** Whether it is still there (a removed item or a dead NPC is not). */
    boolean exists();
    /** The options in this context, best first; never hides a crucial state behind a silent button. */
    List<Option> options(Rpg g, Actor actor);
    /** Carries out an action now (or begins its timed work), after revalidating it. */
    Result perform(Rpg g, Actor actor, String actionId, long requestId);
    String examine(Rpg g);
    /** A line or two for the developer inspector. */
    default String inspect(Rpg g) { return ""; }
    /** Owner entity id (0: nobody's). */
    default long owner() { return 0; }
}
