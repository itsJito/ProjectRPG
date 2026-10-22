package rpg.actor;

/**
 * How a human body steps at a ground speed: one model shared by the figure (its stride cycle), the footfall sounds and
 * the noise a body makes. Three gaits blend into one another (gamefixes, Human Walk System): a walk of long, unhurried
 * strides with both feet down between them, a run with a moment in the air each step, and a sprint of long bounding
 * strides. Cadence is set by the stride a leg of this figure can take, not by speed alone: the figure's legs are short
 * for its height (12 of its 32 pixels), so a walk is held to about two and a third steps a second, a run three, a
 * sprint under four.
 */
public final class Gait {
    private Gait() {}

    /** Below this ground speed (m/s) a figure is standing, not walking. */
    public static final float STILL = 0.25f;
    /** Where a walk has fully become a run (m/s): between them the stride blends. */
    public static final float WALK_TOP = 2.3f, RUN_FROM = 3.2f;

    /** 0 at a walk, 1 at a run and faster. */
    public static float runShare(float speed) { return Math.max(0, Math.min(1, (speed - WALK_TOP) / (RUN_FROM - WALK_TOP))); }

    /** One step's length (m) at a ground speed. */
    public static float stepLength(float speed) { return speed < 0.05f ? 0 : speed / (2 * cadence(speed)); }

    /** Stride cycles per second (two steps a cycle). */
    public static float cadence(float speed) {
        if (speed < 0.05f) return 0;
        // walking: a step of 0.42 m plus a fifth of the speed (0.74 m at 1.6 m/s); running: a cadence that rises slowly
        // with speed (1.45 cycles a second at 4 m/s, 1.85 at 6.6) while the steps lengthen through the air
        float walk = Math.max(0.6f, speed / (2 * (0.42f + 0.2f * Math.min(speed, WALK_TOP))));
        float run = 1.45f + (speed - 4f) * 0.154f;
        float k = runShare(speed);
        return Math.max(0.6f, walk * (1 - k) + Math.max(1.25f, run) * k);
    }

    /** The fraction of a cycle each foot is on the ground: 0.6 at a walk (both feet down between steps), 0.32 at a run, 0.26 sprinting. */
    public static float stance(float speed) {
        float k = runShare(speed);
        float run = 0.32f - 0.06f * Math.max(0, Math.min(1, (speed - 4f) / 2.6f));
        return 0.55f * (1 - k) + run * k;
    }

    /** Footfalls a second (two a cycle). */
    public static float steps(float speed) { return 2 * cadence(speed); }
}
