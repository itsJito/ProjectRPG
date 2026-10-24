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
    /** Closest entry along a unit camera ray; props may override with their actual bounds. */
    default float focusEntry(float ox, float oy, float oz, float dx, float dy, float dz) {
        float best = Float.POSITIVE_INFINITY, r = focusRadius();
        for (float up : focusColumn()) {
            float x = ax() - ox, y = ay() + up - oy, z = az() - oz, along = x * dx + y * dy + z * dz;
            float off2 = x * x + y * y + z * z - along * along;
            if (off2 > r * r || along < 0) continue;
            best = Math.min(best, Math.max(0, along - (float) Math.sqrt(Math.max(0, r * r - off2))));
        }
        return best;
    }
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
