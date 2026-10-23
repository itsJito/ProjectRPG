package rpg.actor;

/**
 * How a human body steps at a ground speed: one model shared by the figure (its stride cycle), the footfall sounds and
 * the noise a body makes. Three gaits blend into one another (gamefixes, Human Walk System): a walk of long, unhurried
 * strides with both feet down between them, a run with a moment in the air each step, and a sprint of long bounding
 * strides. Cadence is set by the stride a leg of this figure can take, not by speed alone: the figure's legs are short
 * for its height (12 of its 32 pixels). A run gains speed through longer flight, rather than forcing the entire
 * animation to cycle faster than the feet and arms can read. Sound and presentation use this same model.
 */
public final class Gait {
    private Gait() {}

    /** Below this ground speed (m/s) a figure is standing, not walking. */
    public static final float STILL = 0.25f;
    /** Where a walk has fully become a run (m/s): between them the stride blends. */
    public static final float WALK_TOP = 2.6f, RUN_FROM = 3.5f;
    /** Model pixels: knee bend at a walk, the hips' pendulum drop, and the extra crouch when sneaking. */
    public static final float WALK_BEND = 0.9f, PENDULUM = 0.6f, SNEAK_CROUCH = 2.3f;

    /** 0 at a walk, 1 at a run and faster. */
    public static float runShare(float speed) { return Math.max(0, Math.min(1, (speed - WALK_TOP) / (RUN_FROM - WALK_TOP))); }

    /** Comfortable half-span of a planted leg in model pixels, shared with the rig's foot targeting. */
    public static float halfReach(boolean sneaking) {
        float leg = Body.THIGH + Body.SHIN - 0.15f;
        float hip = Body.LEGS - 0.35f - WALK_BEND - PENDULUM - (sneaking ? SNEAK_CROUCH : 0);
        return (float) Math.sqrt(Math.max(0, leg * leg - hip * hip)) * 0.95f;
    }

    /** Stride cycles per second (two steps a cycle). */
    public static float cadence(float speed, boolean sneaking) {
        if (speed < 0.05f) return 0;
        // Preferred walk step: 0.42 m plus a fifth of the speed, shortened below if leg reach requires it.
        // Running cadence rises slowly (1.45 cycles/s at 4 m/s, 1.85 at 6.6) while flight lengthens the step.
        float walk = Math.max(0.6f, speed / (2 * (0.42f + 0.2f * Math.min(speed, WALK_TOP))));
        // Walking retains double support without stretching a planted leg beyond its reach.
        walk = Math.max(walk, speed * 0.55f / (2 * halfReach(sneaking) * Body.PX));
        float run = 1.45f + (speed - 4f) * 0.154f;
        float k = runShare(speed);
        return Math.min(2f, Math.max(0.6f, walk * (1 - k) + Math.max(1.25f, run) * k));
    }

    /** Ground contact per cycle. At a run the contact shortens to fit the leg's reachable span; the rest is flight. */
    public static float stance(float speed, boolean sneaking) {
        float k = runShare(speed);
        float run = 0.32f - 0.06f * Math.max(0, Math.min(1, (speed - 4f) / 2.6f));
        float duty = 0.55f * (1 - k) + run * k;
        return speed < 0.05f ? duty : Math.min(duty, 2 * halfReach(sneaking) * Body.PX * cadence(speed, sneaking) / speed);
    }

    /** Footfalls a second (two a cycle). */
    public static float steps(float speed, boolean sneaking) { return 2 * cadence(speed, sneaking); }
}
