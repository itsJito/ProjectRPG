package rpg.interact;

import java.util.function.Supplier;
import rpg.actor.Actor;

/**
 * A deferred action (GDD 3.2): gathering, crafting, lockpicking. It has a clearly defined commit point - the end of its
 * duration - before which nothing durable changes; moving away, being hit or losing the target interrupts it with
 * nothing spent. A repeating work starts again after each commit while it stays valid (continuous gathering).
 */
public final class Work {
    public final Actor actor;
    public final Interactable target;
    public final String action, verb;
    public float duration, t;
    public final float startX, startZ;
    public final Supplier<Result> commit;
    public final boolean repeat;
    public final double startedAt;
    public int commits;
    /** For a repeating work, how many commits it stops after (0: until it can go on no more) - "cook five". */
    public int limit;

    public Work(Actor actor, Interactable target, String action, String verb, float duration, boolean repeat, double now, Supplier<Result> commit) {
        this.actor = actor; this.target = target; this.action = action; this.verb = verb; this.duration = Math.max(0.2f, duration);
        this.repeat = repeat; this.commit = commit; this.startX = actor.x; this.startZ = actor.z; this.startedAt = now;
    }

    public float progress() { return Math.min(1, t / duration); }
}
