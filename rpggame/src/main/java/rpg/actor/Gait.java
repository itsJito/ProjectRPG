package rpg.actor;

/**
 * How a human body steps at a ground speed: one model shared by the figure (its stride cycle), the footfall sounds and
 * the noise a body makes. Three gaits blend into one another (gamefixes, Human Walk System): a walk of long, unhurried
 * strides with both feet down between them, a run with a moment in the air each step, and a sprint of quick, driving
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
    public static final float WALK_BEND = 0.45f, PENDULUM = 0.4f, SNEAK_CROUCH = 4.4f;   // (0.1.8: a taller walk and run - knees soft, not folded)

    /** 0 at a walk, 1 at a run and faster. */
    public static float runShare(float speed) { return Math.max(0, Math.min(1, (speed - WALK_TOP) / (RUN_FROM - WALK_TOP))); }

    /** Comfortable half-span of a planted leg in model pixels, shared with the rig's foot targeting. */
    public static float halfReach(boolean sneaking) {
        // Deep crouching needs short steps; full standing reach straightened the trailing leg along the floor.
        if (sneaking) return 4.5f;
        float leg = Body.THIGH + Body.SHIN - 0.15f;
        // (0.1.9) the reach is judged at the bottom of the stride, where the hips dip a pixel and a half whatever the
        // standing height: judged at the taller walk's standing hips, a planted leg could not span a walking step at
        // the half-second cadence, and the walk lost its double support
        float hip = Body.LEGS - 0.35f - Math.max(WALK_BEND + PENDULUM, 1.5f);
        return (float) Math.sqrt(Math.max(0, leg * leg - hip * hip)) * 0.95f;
    }

    /** Stride cycles per second (two steps a cycle). */
    public static float cadence(float speed, boolean sneaking) {
        if (speed < 0.05f) return 0;
        // Preferred walk step: 0.42 m plus a fifth of the speed, shortened below if leg reach requires it.
        // Running cadence (0.1.9) suits this figure's short legs: 1.9 cycles/s at 4 m/s, up to 2 (a half-second cycle,
        // the quickest a loop may be) - at the old 1.45 a jog's step was over twice the leg, and every stride a lunge.
        // (0.2.0, gamefixes: the sprint still bounded) a sprint quickens the legs past the half-second loop - the rule
        // that holds for every other cycle - to three a second at full pace: about a body-length and a half a stride,
        // driven, not long floating bounds.
        float walk = Math.max(0.6f, speed / (2 * (0.42f + 0.2f * Math.min(speed, WALK_TOP))));
        // Walking retains double support without stretching a planted leg beyond its reach.
        walk = Math.max(walk, speed * 0.55f / (2 * halfReach(sneaking) * Body.PX));
        // (0.2.1, gamefixes: the run looked worse than ever) quicker again: at 2 cycles a second a 4.6 m/s jog still took
        // 1.1 m steps on 0.68 m legs - a lunge each stride, the hips dropping to reach it. 2.4 at a jog (0.95 m steps)
        // to 3.3 at a full sprint keeps the step within what the leg can span with a short flight between
        // (0.2.3, gamefixes: the run and jog 2/10) quicker still at a jog - 2.8 a second at 4.6 m/s, 0.82 m steps a
        // little over the leg, a light run with a short flight instead of a split on bent knees
        float run = 2.6f + (speed - 4f) * 0.35f;
        float k = runShare(speed);
        return Math.min(2.85f + 0.65f * sprintShare(speed), Math.max(0.6f, walk * (1 - k) + Math.max(1.6f, run) * k));
    }

    /** 0 at a jog, 1 at a full sprint: how far past the half-second loop the legs may go. */
    public static float sprintShare(float speed) { return Math.max(0, Math.min(1, (speed - 4.8f) / 3.4f)); }

    /** Ground contact per cycle. At a run the contact shortens to fit the leg's reachable span; the rest is flight. */
    public static float stance(float speed, boolean sneaking) {
        float k = runShare(speed);
        float run = 0.32f - 0.12f * Math.max(0, Math.min(1, (speed - 4f) / 4f));
        float duty = 0.55f * (1 - k) + run * k;
        return speed < 0.05f ? duty : Math.min(duty, 2 * halfReach(sneaking) * Body.PX * cadence(speed, sneaking) / speed);
    }

    /** Footfalls a second (two a cycle). */
    public static float steps(float speed, boolean sneaking) { return 2 * cadence(speed, sneaking); }
}
