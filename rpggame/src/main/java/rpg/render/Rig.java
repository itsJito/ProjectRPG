package rpg.render;

import rpg.actor.Actor;
import rpg.actor.Actor.Combat.Move;
import rpg.actor.Actor.Combat.Phase;
import rpg.actor.Body;
import rpg.actor.Gait;
import static rpg.actor.Gait.PENDULUM;
import static rpg.actor.Gait.SNEAK_CROUCH;
import static rpg.actor.Gait.WALK_BEND;
import rpg.combat.CombatSystem;
import rpg.item.ItemDef;
import rpg.item.ItemDef.Family;

/**
 * Poses a human figure each frame, procedurally, in the figure's own space (pixels; feet at the origin, facing -z, its
 * right at +x), from what the body is actually doing - never from a canned clip.
 *
 * <p>Steps are real steps. A planted foot is pinned to the ground in the world: it does not slide when the body moves
 * over it or turns above it. Each stride the lifting foot is carried to where the body will be when it lands, a half
 * stance ahead along the body's velocity and out to its own side, so steps land under the body in any direction it
 * moves (forward, backward, sideways, turning on an arc). Standing, a foot left too far behind by a turn or a shove takes
 * a corrective step on its own. The hips ride over the planted feet as an inverted pendulum (highest at mid-stance),
 * shift their weight onto the standing leg, and settle through a damped spring, so nothing pops when a foot lands; the
 * torso leans with acceleration and into turns, counter-turns against the hips, and the arms swing against the legs.
 * Knees and elbows bend by two-bone IK. In combat the striking hand follows the same grip and blade combat makes
 * contact with; the torso winds and unwinds and the feet step into heavy blows.
 */
public final class Rig {
    /** Per-figure animation memory. */
    public static final class State {
        boolean init;
        float phase, spd, amp, land, airT;
        float dx, dz = -1;
        boolean wasGrounded = true;
        // feet, in the world: where each stands (or is swinging to), the swing's start and how far through it is
        final float[][] foot = new float[2][3], from = new float[2][3], to = new float[2][3];
        final boolean[] stance = {true, true};
        final float[] swing = new float[2], swingTime = new float[2];
        int corrective = -1;
        // the hips' height (a spring), their side shift, the lean
        float hipH = -1, hipV, sway, accLean, roll, lastVx, lastVz, lastX, lastZ;
        float headYaw, bowRaise, guardRaise, crouchBlend;
        final float[] armSwing = new float[2];
        /** The body's velocity as the eye reads it: the mover's own flickers from one physics step to the next, and a
         *  figure driven by it raw twitches - its steps aim at a target that jumps, its lean at a spike of acceleration. */
        float svx, svz;
        boolean walking;
    }

    /** The segments' matrices in figure space (column-major; pixels), in {@link CharModel.Seg} order. */
    public final float[][] seg = new float[CharModel.SEGS][16];
    /** Each hand's grip point and the forearm's direction (elbow to grip), right [0] and left [1]. */
    public final float[][] hand = new float[2][3], fore = new float[2][3];
    /** The torso's axes, for things worn on it (capes, skirts) and the held items' rest directions. */
    public final float[] torsoRight = new float[3], torsoUp = new float[3], torsoBack = new float[3], pelvis = new float[3];
    /** The direction each held item points (figure space). */
    public final float[][] itemDir = new float[2][3];
    /** The eyes, in figure space (pixels): where a first-person camera sits on this body. */
    public final float[] eye = new float[3];
    /** Review probes (the gallery's twitch dump). */
    public float debugA, debugB;

    private static final float L_ARM1 = Body.UPPER_ARM, L_ARM2 = Body.FOREARM, L_LEG1 = Body.THIGH, L_LEG2 = Body.SHIN;
    /** A combat state with nothing going on (a sleeper's). */
    private static final Actor.Combat REST = new Actor.Combat();
    private static final boolean DEBUG = System.getenv("RPG_RIG_DEBUG") != null;
    /** This frame's half-stride a planted leg can reach (pixels). */
    private float reachHalf = 6;

    /** The figure is the player's, seen through its own eyes: the hands are carried up and forward where the eyes can see them. */
    public boolean firstPerson;

    /** Poses the figure; time in seconds, for idle motion; still: at rest whatever the body is doing (asleep). */
    public void pose(Actor a, State st, float dt, double time, boolean still) {
        Body b = a.plan();
        Actor.Combat c = still ? REST : a.cs;
        dt = Math.max(0, Math.min(dt, 0.1f));
        float yaw = a.yaw;
        float rx = (float) Math.cos(yaw), rz = (float) -Math.sin(yaw), bx = (float) Math.sin(yaw), bz = (float) Math.cos(yaw);
        float rvx = still ? 0 : a.vx, rvz = still ? 0 : a.vz;
        if (!st.init) { st.svx = rvx; st.svz = rvz; }
        float smooth = 1 - (float) Math.exp(-dt / 0.07f);
        st.svx += (rvx - st.svx) * smooth; st.svz += (rvz - st.svz) * smooth;
        float vx = st.svx, vz = st.svz;
        float speed = (float) Math.hypot(vx, vz);
        boolean grounded = still || a.grounded;
        float hipX = b.hipX();
        float legLen = L_LEG1 + L_LEG2;

        // ---- a fresh figure, or one moved further than it could walk (a teleport): feet planted where they stand
        if (!st.init || Math.hypot(a.x - st.lastX, a.z - st.lastZ) > 2.5f) {
            for (int k = 0; k < 2; k++) {
                float[] w = toWorld(a, (k == 0 ? 1 : -1) * hipX, 0, 0);
                st.foot[k][0] = w[0]; st.foot[k][1] = a.y; st.foot[k][2] = w[2];
                st.stance[k] = true; st.swing[k] = 0;
            }
            st.hipH = b.hipY() - 0.35f; st.hipV = 0; st.phase = 0; st.spd = 0; st.amp = 0; st.corrective = -1;
            st.lastVx = vx; st.lastVz = vz;
            st.svx = rvx; st.svz = rvz; vx = rvx; vz = rvz;
            st.init = true;
        }
        st.lastX = a.x; st.lastZ = a.z;

        // ---- the gait: how fast, which way (figure space), how far through the stride cycle
        st.spd += ((grounded ? speed : 0) - st.spd) * Math.min(1, dt * 8);
        float lx = vx * rx + vz * rz, lz = vx * bx + vz * bz;
        if (speed > 0.2f) { float k = Math.min(1, dt * 9); st.dx += (lx / speed - st.dx) * k; st.dz += (lz / speed - st.dz) * k; }
        float dl = (float) Math.hypot(st.dx, st.dz);
        if (dl > 1e-3f) { st.dx /= dl; st.dz /= dl; } else { st.dx = 0; st.dz = -1; }
        // a walk starts a little above the still threshold and stops a little below it, so a body hovering at the
        // threshold does not flick between stepping and standing
        boolean wasWalking = st.walking;
        st.walking = grounded && (st.walking ? st.spd > Gait.STILL * 0.75f : st.spd > Gait.STILL * 1.25f);
        boolean walking = st.walking;
        float ampT = walking ? Math.min(1, st.spd / 1.2f) : 0;
        st.amp += (ampT - st.amp) * Math.min(1, dt * 5);
        float cadence = Gait.cadence(Math.max(st.spd, 0.8f), a.sneaking || a.crouched);
        float beta = Gait.stance(st.spd, a.sneaking || a.crouched);
        // a walk is taken on softly bent knees, the hips settled a little: that is what lets a leg reach into a stride
        // instead of patting along on straight legs
        float strideBend = WALK_BEND * Math.min(1, st.amp * 1.4f);
        // Reach is planned for the bent stance the body is approaching. Running shortens contact instead of
        // privately accelerating the rig's cycle, which used to disagree with the footstep/noise clock.
        reachHalf = Gait.halfReach(a.sneaking || a.crouched);
        // Feet, torso and arms must reach the running gait together.
        float runF = Gait.runShare(st.spd);
        float hipFloor = b.hipY() - 0.35f - strideBend - PENDULUM * 1.15f;
        float sprintF = clamp01((st.spd - 5.2f) / 1.8f);
        // acceleration, for the lean: the body leans into a start and back against a stop (it has weight)
        float ax = dt > 0 ? (vx - st.lastVx) / dt : 0, az = dt > 0 ? (vz - st.lastVz) / dt : 0;
        st.lastVx = vx; st.lastVz = vz;
        float accFwd = -(ax * bx + az * bz);                 // + forward
        float accSide = ax * rx + az * rz;
        st.accLean += (clamp(accFwd * 0.018f, -0.22f, 0.22f) - st.accLean) * Math.min(1, dt * 6);
        // leaning into a turn: the faster and the tighter, the more
        float turnRoll = clamp(accSide * 0.012f, -0.15f, 0.15f);
        st.roll += (turnRoll - st.roll) * Math.min(1, dt * 5);

        // ---- the feet
        if (!grounded) {
            st.airT += dt;
        } else {
            if (!st.wasGrounded && st.airT > 0.12f) {
                st.land = Math.min(1, st.airT * 1.6f);
                // landing: both feet come down under the body where they are tucked
                for (int k = 0; k < 2; k++) {
                    float[] w = toWorld(a, (k == 0 ? 1 : -1) * hipX * 1.1f, 0, (k == 0 ? -1.2f : 1.2f));
                    st.foot[k][0] = w[0]; st.foot[k][1] = a.y; st.foot[k][2] = w[2]; st.stance[k] = true; st.swing[k] = 0;
                }
            }
            st.airT = 0;
        }
        st.wasGrounded = grounded;
        st.land *= (float) Math.exp(-dt * 7);
        float stanceTime = beta / cadence, swingTime = (1 - beta) / cadence;
        // a walk from standing begins with a step: the foot that will be left behind first (the one further back along the
        // way the body sets off, or the right) lifts at once, while the other takes the weight - never both planted while
        // the body moves off over them
        if (walking && !wasWalking && st.stance[0] && st.stance[1]) {
            float z0 = toFigure(a, st.foot[0][0], st.foot[0][1], st.foot[0][2])[2], z1 = toFigure(a, st.foot[1][0], st.foot[1][1], st.foot[1][2])[2];
            float back0 = z0 * -st.dz, back1 = z1 * -st.dz;
            st.phase = back1 > back0 + 0.3f ? (beta + 0.5f - 0.02f) % 1f : beta - 0.02f;
        }
        if (walking || st.stance[0] != st.stance[1]) {
            // the stride cycle: foot 0 (right) in stance for the first beta of it, foot 1 half a cycle later
            st.corrective = -1;
            st.phase = (st.phase + cadence * dt) % 1f;
            for (int k = 0; k < 2; k++) {
                float ph = (st.phase + (k == 0 ? 0 : 0.5f)) % 1f;
                boolean inStance = ph < beta || !walking && st.stance[k];
                if (!walking && !st.stance[k]) inStance = false;   // a step under way finishes even as the body stops
                if (st.stance[k] && !inStance) liftOff(st, k, swingTime);
                if (!st.stance[k]) {
                    boolean own = !walking;
                    float t = own ? st.swing[k] + dt / Math.max(0.12f, st.swingTime[k]) : (ph - beta) / (1 - beta);
                    if (!own && t < st.swing[k] - 0.5f) t = 1;   // the cycle wrapped: the step is done
                    stepTarget(a, st, k, hipX, Math.max(0, 1 - t) * st.swingTime[k], stanceTime, vx, vz);
                    capStep(a, st, k, hipX, reachHalf * (1.25f + 0.9f * runF), vx * Math.max(0, 1 - t) * st.swingTime[k], vz * Math.max(0, 1 - t) * st.swingTime[k]);
                    if (t >= 1 || !own && ph < beta) plant(a, st, k);
                    else st.swing[k] = t;
                }
            }
        } else if (grounded) {
            // standing: a foot left too far from where it should stand (by a turn above it, a shove, the end of a
            // walk) takes a step of its own; only one at a time, the worse first
            if (st.corrective < 0) {
                int worst = -1; float far = 0;
                for (int k = 0; k < 2; k++) {
                    float[] rest = toWorld(a, (k == 0 ? 1 : -1) * hipX, 0, k == 0 ? -0.6f : 0.6f);
                    float d = (float) Math.hypot(st.foot[k][0] - rest[0], st.foot[k][2] - rest[2]);
                    if (d > far) { far = d; worst = k; }
                }
                if (far > 3.6f * Body.PX * 4 / 3) {
                    st.corrective = worst;
                    liftOff(st, worst, 0.26f);
                }
            }
            if (st.corrective >= 0) {
                int k = st.corrective;
                st.swing[k] += dt / st.swingTime[k];
                float[] rest = toWorld(a, (k == 0 ? 1 : -1) * hipX, 0, k == 0 ? -0.6f : 0.6f);
                st.to[k][0] = rest[0]; st.to[k][1] = a.y; st.to[k][2] = rest[2];
                if (st.swing[k] >= 1) { plant(a, st, k); st.corrective = -1; }
            }
        }
        if (DEBUG && a instanceof rpg.actor.Player) { float[] f0 = toFigure(a, st.foot[0][0], st.foot[0][1], st.foot[0][2]); System.out.printf("RIG ph %.3f beta %.2f cad %.2f st0 %s sw0 %.2f z0 %.1f spd %.2f%n", st.phase, beta, cadence, st.stance[0], st.swing[0], f0[2], st.spd); }
        // where each foot is, in figure space: planted ones pinned; swinging ones on their arc
        float[][] foot = new float[2][3];
        float[] footFwd = new float[2];
        // a walking foot clears the ground by a little; a running one is kicked up behind (the heel toward the seat) and
        // carried through at knee height
        float lift = (1.3f + 2.4f * runF + 1.6f * sprintF) * Math.max(0.55f, st.amp);
        float sd = Math.max(1e-3f, speed), tdx = vx / sd, tdz = vz / sd;
        for (int k = 0; k < 2; k++) {
            float wx, wy, wz;
            if (st.stance[k]) { wx = st.foot[k][0]; wy = st.foot[k][1]; wz = st.foot[k][2]; }
            else {
                float t = st.swing[k], e = t * t * (3 - 2 * t);
                // running, the foot stays back a moment as the heel kicks up, then swings through fast
                float er = (float) Math.pow(e, 1 + 0.6f * runF);
                float kick = runF * (2.2f + 1.5f * sprintF) * Body.PX * (float) Math.sin(Math.PI * Math.min(1, t * 1.7f)) * (1 - t);
                wx = st.from[k][0] + (st.to[k][0] - st.from[k][0]) * er - tdx * kick;
                wz = st.from[k][2] + (st.to[k][2] - st.from[k][2]) * er - tdz * kick;
                float arc = (float) Math.sin(Math.PI * Math.pow(t, 1 - 0.35f * runF));
                wy = st.from[k][1] + (st.to[k][1] - st.from[k][1]) * e + lift * Body.PX * arc;
                st.foot[k][0] = wx; st.foot[k][1] = wy; st.foot[k][2] = wz;
            }
            float[] f = toFigure(a, wx, wy, wz);
            foot[k] = f;
            footFwd[k] = -f[2];
        }
        // ---- what the body is doing
        float crouch = 0, extraLean = 0, extraTwist = 0, roll = st.roll;
        if (c.dodgeT > 0) {
            crouch += 2.4f;
            float dlx = c.dodgeX * rx + c.dodgeZ * rz, dlz = c.dodgeX * bx + c.dodgeZ * bz;
            extraLean += -dlz * 0.3f; roll += -dlx * 0.25f;
        }
        st.crouchBlend += ((a.sneaking || a.crouched ? 1 : 0) - st.crouchBlend) * (1 - (float) Math.exp(-16 * dt));
        crouch += SNEAK_CROUCH * st.crouchBlend;
        extraLean += 0.65f * st.crouchBlend;
        float looting = a instanceof rpg.ai.Npc npc && (npc.goal == rpg.ai.Brain.Goal.LOOT || npc.goal == rpg.ai.Brain.Goal.AID || npc.goal == rpg.ai.Brain.Goal.WORK && npc.role == rpg.ai.Npc.Role.HUNTER) && time < npc.lootUntil
                ? Math.max(0, Math.min(1, Math.min((float) (time - npc.lootStarted) / 0.25f, (float) (npc.lootUntil - time) / 0.2f))) : 0;
        if (a.traversal != null) extraLean += 0.38f * (float) Math.sin(Math.PI * a.traversal.progress);
        float carryLift = a.carryBody == 0 ? 0 : Math.max(0, Math.min(1, (float) (time - a.carrySince) / 0.65f));
        float carryLower = a.carryLowerAt < 0 ? 0 : Math.min(1, (float) (time - a.carryLowerAt) / 0.45f);
        crouch += 4 * carryLower; extraLean += 0.55f * carryLower;
        if (a.carryBody != 0) { crouch += 5 * (float) Math.sin(Math.PI * carryLift); extraLean += 0.35f; }
        crouch += 5 * looting; extraLean += 0.9f * looting;
        if (c.staggered > 0) { float k = Math.min(1, c.staggered / 0.4f); extraLean -= 0.32f * k; extraTwist += 0.25f * k * (float) Math.sin(time * 9 + a.id); crouch += 1.2f * k; }
        extraLean -= 0.14f * c.flash;
        if (c.blocking) { extraLean += 0.06f; extraTwist -= 0.16f; crouch += 0.8f; }
        Move mv = c.move;
        float strike = strikeS(c);
        float strikeW = strikeWeight(c);
        if (mv != null && c.phase != Phase.IDLE) {
            extraTwist += CombatSystem.twist(mv, c.offHand, strike) * strikeW;
            extraLean += CombatSystem.lean(mv, strike) * strikeW;
            crouch += (mv == Move.HEAVY ? 1.1f : 0.5f) * strikeW;
        } else if (c.charge > 0 && a.carryBody == 0) {
            extraTwist += CombatSystem.twist(Move.HEAVY, false, -0.3f * c.charge) * c.charge;
            extraLean += CombatSystem.lean(Move.HEAVY, -0.3f * c.charge) * c.charge;
            crouch += 0.8f * c.charge;
        }
        // stepping into heavy blows and thrusts: the leading foot is carried forward through the strike
        if (mv != null && (mv == Move.HEAVY || mv == Move.THRUST) && grounded && st.stance[0] && st.stance[1]) {
            float k = strikeW * (c.phase == Phase.WINDUP ? 0 : c.phase == Phase.ACTIVE ? CombatSystem.ease(c.sweep) : 1);
            int lead = c.offHand ? 1 : 0;
            foot[lead][2] -= 3.5f * k; foot[1 - lead][2] += 1.0f * k;
        }
        if (!grounded) {   // tucked in the air
            float t = Math.min(1, st.airT * 5);
            foot[0] = new float[]{hipX, 4.5f * t, -2.5f * t};
            foot[1] = new float[]{-hipX, 3.0f * t, 1.5f * t};
        }

        if (a.traversal != null) {
            var motion = a.traversal; var anchor = motion.anchor;
            float wallWeight = clamp((motion.progress - motion.reachEnd * 0.5f) / (motion.reachEnd * 0.5f), 0, 1) * clamp((motion.hangEnd + 0.08f - motion.progress) / 0.08f, 0, 1);
            float landingWeight = clamp((motion.progress - motion.mantleEnd) / (1 - motion.mantleEnd), 0, 1);
            for (int k = 0; k < 2; k++) {
                float side = (k == 0 ? 1 : -1) * hipX * Body.PX;
                float[] wallFoot = toFigure(a, anchor.handX() + anchor.nx() * 0.07f + rx * side, anchor.top() - a.height * 0.75f, anchor.handZ() + anchor.nz() * 0.07f + rz * side);
                float[] landing = toFigure(a, anchor.landX() + rx * side, anchor.top() + 0.03f, anchor.landZ() + rz * side);
                foot[k] = lerp(lerp(foot[k], wallFoot, wallWeight), landing, landingWeight);
            }
        }

        // ---- the hips: the planted legs' reach (an inverted pendulum: highest over the foot), on a damped spring
        float sway = 0;
        int planted = 0;
        float want = b.hipY() - 0.35f - strideBend;
        for (int k = 0; k < 2; k++) {
            if (!st.stance[k] || !grounded) continue;
            planted++;
            float hx = (k == 0 ? hipX : -hipX) - foot[k][0], hz = foot[k][2];
            float d2 = hx * hx + hz * hz, L = legLen - 0.15f;
            // a planted foot left beyond the leg's comfortable reach does not pull the hips to the ground: the leg goes
            // straight toward it (the IK takes it from there) until the stride lifts it
            float hd = Math.min((float) Math.sqrt(d2), L * 0.95f);
            want = Math.min(want, (float) Math.sqrt(L * L - hd * hd) + foot[k][1]);
        }
        // the weight goes over the standing foot
        if (planted == 1) sway = st.stance[0] ? 0.55f : -0.55f;
        sway *= (1 - runF * 0.6f);
        st.sway += (sway * Math.min(1, st.amp + 0.3f) - st.sway) * Math.min(1, dt * 7);
        // the pendulum rides down between the feet only so far: a stride is not a lunge
        if (grounded && runF < 0.5f) want = Math.max(want, hipFloor);
        want -= st.land * 2.6f + crouch + runF * 0.6f;
        if (!grounded) want = b.hipY();
        if (st.hipH < 0) st.hipH = want;
        // Exact critically damped response for this frame's target. Explicit Euler integration
        // becomes unstable at long render frames (w0 * dt > ~0.8), throwing the hips upward.
        float w0 = 26f;
        float offset = st.hipH - want, j = st.hipV + w0 * offset;
        float decay = (float) Math.exp(-w0 * dt);
        st.hipH = want + (offset + j * dt) * decay;
        st.hipV = (st.hipV - w0 * j * dt) * decay;
        pelvis[0] = st.sway; pelvis[1] = st.hipH; pelvis[2] = 0;
        debugA = want; debugB = (st.stance[0] ? 1 : 0) + (st.stance[1] ? 2 : 0) + (walking ? 4 : 0) + st.land * 10;

        // ---- the torso: lean with speed and acceleration, roll into turns, counter-turn against the stride, breathe
        float stride = (footFwd[0] - footFwd[1]) / Math.max(1, legLen * 0.6f);   // + right foot ahead
        float gaitTwist = (0.1f + 0.05f * runF) * clamp(stride, -1.2f, 1.2f) * st.amp;
        float lean = (0.03f + 0.11f * runF + 0.1f * sprintF) * st.amp * -st.dz + st.accLean + extraLean + 0.012f * (float) Math.sin(time * 1.7 + a.id);
        float twist = gaitTwist + extraTwist;
        float[] tm = seg[CharModel.Seg.TORSO.ordinal()];
        identity(tm);
        translate(tm, pelvis[0], pelvis[1], pelvis[2]);
        rotY(tm, twist); rotX(tm, -lean); rotZ(tm, roll - st.sway * 0.03f);
        torsoRight[0] = tm[0]; torsoRight[1] = tm[1]; torsoRight[2] = tm[2];
        torsoUp[0] = tm[4]; torsoUp[1] = tm[5]; torsoUp[2] = tm[6];
        torsoBack[0] = tm[8]; torsoBack[1] = tm[9]; torsoBack[2] = tm[10];
        // ---- the head: on the neck, steady against the shoulders, turned toward where the actor looks
        float look = wrap(a.aimYaw - yaw);
        look = clamp(look, -1.2f, 1.2f);
        st.headYaw += (look - st.headYaw) * Math.min(1, dt * 10);
        float[] hm = seg[CharModel.Seg.HEAD.ordinal()];
        System.arraycopy(tm, 0, hm, 0, 16);
        translate(hm, 0, b.neckY() - b.hipY(), 0);
        rotZ(hm, -(roll - st.sway * 0.03f) * 0.8f);
        rotX(hm, lean * (0.7f + 0.3f * st.crouchBlend));
        rotY(hm, -twist * 0.9f + st.headYaw);
        rotX(hm, clamp(a.pitch * 0.7f, -0.7f, 0.7f));
        float[] e = point(hm, 0, b.eyeY() - b.neckY(), -b.head[2] / 2f + 0.5f);
        eye[0] = e[0]; eye[1] = e[1]; eye[2] = e[2];
        // ---- the legs (knees forward of the line from hip to foot)
        float hipTwist = -0.6f * gaitTwist;
        for (int k = 0; k < 2; k++) {
            int side = k == 0 ? 1 : -1;
            float cs = (float) Math.cos(hipTwist), sn = (float) Math.sin(hipTwist);
            float[] hip = {pelvis[0] + side * hipX * cs, pelvis[1], pelvis[2] - side * hipX * sn};
            float[] target = foot[k];
            float[] d = sub(target, hip);
            float dd = len(d);
            if (dd > legLen - 0.02f) target = add(hip, d, (legLen - 0.02f) / dd);   // out of reach: the leg straight toward it
            // the knee points along the foot's own heading, which leads the hips a little in a turn
            float[] pole = norm(new float[]{side * 0.12f + sn * -1, 0.05f, -cs});
            float[] knee = ik(hip, target, L_LEG1, L_LEG2, pole);
            float[] right = {cs, 0, -sn};
            bone(seg[(k == 0 ? CharModel.Seg.R_LEG_UP : CharModel.Seg.L_LEG_UP).ordinal()], hip, knee, right);
            bone(seg[(k == 0 ? CharModel.Seg.R_LEG_LO : CharModel.Seg.L_LEG_LO).ordinal()], knee, target, right);
        }
        // ---- the arms
        ItemDef main = a.main(), off = a.off();
        boolean bow = main != null && main.family == Family.BOW;
        boolean crossbow = main != null && main.family == Family.CROSSBOW;
        st.bowRaise += ((bow && c.draw > 0 ? 1 : 0) - st.bowRaise) * (1 - (float) Math.exp(-18 * dt));
        st.guardRaise += ((c.blocking && main == null && off == null ? 1 : 0) - st.guardRaise) * (1 - (float) Math.exp(-22 * dt));
        float[] aim = {(float) Math.sin(st.headYaw) * -(float) Math.cos(a.pitch), (float) Math.sin(a.pitch), -(float) Math.cos(a.pitch) * (float) Math.cos(st.headYaw)};
        float[] viewBow = firstPerson && bow ? view(aim, 1f, -1, 3.5f, 10f) : null;
        float swingAmp = (0.62f + 0.38f * runF) * st.amp;
        for (int h = 0; h < 2; h++) {
            int k = bow ? 1 - h : h;
            int side = k == 0 ? 1 : -1;
            boolean isMain = k == 0;
            // The bow is in the support hand; the main hand draws its string. Crossbows keep their trigger grip.
            ItemDef holds = main != null && main.family == Family.BOW ? (isMain ? null : main) : isMain ? main : off;
            float[] shoulder = point(tm, side * b.shoulderX(), b.shoulderY() - b.hipY(), 0);
            float[] target;
            float[] dir = torsoDir(0.05f * side, 0.85f, -0.55f);   // where a held weapon points at rest: carried ahead, point up
            boolean attacking = mv != null && c.phase != Phase.IDLE && c.offHand != isMain;
            boolean bareGuard = st.guardRaise > 0.001f && main == null && off == null;
            boolean shieldSide = bareGuard || c.blocking && (isMain ? off == null : off != null && off.family == Family.SHIELD);
            // Bring camera-view shoulders forward so the fists can rise without exceeding the real arm lengths.
            if (firstPerson && bareGuard) shoulder = lerp(shoulder, view(aim, 6, side, 4, 0), st.guardRaise);
            // the arm swings with the opposite leg; a running arm is bent at the elbow
            float desiredSwing = (float) Math.tanh((footFwd[1 - k] - footFwd[k]) / Math.max(1, legLen * 0.6f));
            st.armSwing[k] += (desiredSwing - st.armSwing[k]) * (1 - (float) Math.exp(-18 * dt));
            float opp = st.armSwing[k];
            float theta = swingAmp * opp;
            // the free arm as a runner carries it (the old target pushed the hand straight out ahead at chest height, the
            // arm nearly straight - every running stride read as a jab): the upper arm swings from the shoulder, further
            // back than forward, and the elbow is held bent - a little at a walk, near a right angle at a run, closing as
            // the arm comes forward - so the hand arcs from beside the hip up to the chest and drifts in toward the
            // body's middle on the forward swing; the hand's place follows from those two angles
            float fAmp = (0.32f + 0.2f * runF + 0.08f * sprintF) * st.amp, bAmp = (0.32f + 0.42f * runF + 0.16f * sprintF) * st.amp;
            // (taken against the torso's lean, so a sprinter's forward swing stays a forward swing in the world, not a
            // reach up past the chin)
            float phi = (opp >= 0 ? opp * fAmp : opp * bAmp) - lean * 0.8f;
            float eps = 0.2f + runF * (1.2f + 0.15f * Math.max(0, opp));
            float u1 = L_ARM1 * 0.99f, u2 = L_ARM2 * 0.99f;
            float hy = -(u1 * (float) Math.cos(phi) + u2 * (float) Math.cos(phi + eps));
            float hz = -(u1 * (float) Math.sin(phi) + u2 * (float) Math.sin(phi + eps));
            float hx = side * (0.75f + 0.25f * (1 - runF) - 0.9f * runF * Math.max(0, opp));
            float[] rest = torsoPoint(shoulder, hx, hy, hz);
            if (holds == null) rest = lerp(rest, new float[]{side * (b.shoulderX() + 0.6f), 4.8f, -8.5f + opp * 0.5f}, st.crouchBlend);
            if (holds != null && holds.family != Family.SHIELD && holds.family != Family.BOW && holds.family != Family.STAFF)
                rest = torsoPoint(shoulder, side * 0.5f, -7.4f + 0.6f * (float) Math.cos(theta), -3.8f - 1.25f * (float) Math.sin(theta));
            else if (holds != null && holds.family == Family.SHIELD) rest = torsoPoint(shoulder, side * 0.4f, -7.6f, -2.6f);
            // through the eyes: what the hands hold is carried up and out in front, low in the view (the shield at the left,
            // the weapon at the right), swaying with the stride
            if (holds != null && holds.family == Family.STAFF) { rest = torsoPoint(shoulder, side * 0.6f, -6.6f, -3.6f); dir = new float[]{0, 1, 0}; }
            if (holds != null && holds.family == Family.BOW) {
                dir = new float[]{0, 1, 0};
                if (viewBow != null) rest = viewBow;
            }
            if (firstPerson && holds != null && holds.family != Family.BOW && holds.family != Family.STAFF)
            {
                rest = holds.family == Family.SHIELD ? view(aim,6.0f,side,12.0f,11.0f) : view(aim,4.0f,side,4.2f+0.2f*(float)Math.cos(theta*2),9.2f);
                // the blade carried up and forward into the view, not back over the shoulder
                if (holds.family != Family.SHIELD) dir = holds.family == Family.CROSSBOW ? aim : norm(viewDirection(aim, side * 0.3f, 0.8f, 0.9f));
            }
            if (attacking) {
                float[] gr = CombatSystem.grip(a, mv, strike);
                float[] hl = toFigure(a, gr[0], gr[1], gr[2]);
                float[] dl2 = toFigureDir(a, gr[3], gr[4], gr[5]);
                target = lerp(rest, hl, strikeW);
                dir = norm(lerp(dir, dl2, strikeW));
            } else if (crossbow && (isMain || off == null)) {
                float raise = firstPerson ? 0.35f + c.aim * 0.65f : c.aim;
                if (isMain) {
                    target = lerp(rest, firstPerson ? view(aim, 0.5f, 1, 3.6f, 4.5f) : torsoPoint(shoulder, -b.shoulderX() * 0.75f, -2, -3.2f), raise);
                    dir = norm(lerp(dir, aim, raise));
                } else {
                    target = add(add(hand[0], itemDir[0], 2.5f), torsoRight, -0.8f);
                    target = add(target, torsoUp, -0.6f);
                }
            } else if (isMain && c.charge > 0 && !bow && a.carryBody == 0) {
                float[] gr = gripAs(a, Move.HEAVY, -0.3f * c.charge, false);
                target = lerp(rest, toFigure(a, gr[0], gr[1], gr[2]), c.charge);
                dir = norm(lerp(dir, toFigureDir(a, gr[3], gr[4], gr[5]), c.charge));
            } else if (a.traversal != null) {
                var anchor = a.traversal.anchor;
                float along = side * b.shoulderX() * Body.PX;
                float[] contact = toFigure(a, anchor.handX() + (float) Math.cos(a.yaw) * along, anchor.top() + 0.03f, anchor.handZ() - (float) Math.sin(a.yaw) * along);
                float weight = Math.min(1, a.traversal.progress / 0.12f) * Math.max(0, Math.min(1, (0.78f - a.traversal.progress) / 0.15f));
                target = lerp(rest, contact, weight); dir = new float[]{0, 0, -1};
            } else if (time - a.throwAt >= 0 && time - a.throwAt < 0.45) {
                float t = (float) (time - a.throwAt) / 0.45f, swing = (float) Math.sin(Math.PI * t);
                target = lerp(rest, new float[]{side * 5.2f, b.height / Body.PX * 0.52f + 2 * swing, -7.0f - 3 * swing}, 1 - t);
                dir = new float[]{0, 0, -1};
            } else if (a.carryBody != 0) {
                float[] pickup = toFigure(a, a.reachX, a.reachY, a.reachZ);
                float[] carry = new float[]{side * 5.2f, b.height / Body.PX * 0.52f * (1 - carryLower) + 6.3f * carryLower, -7.0f};
                target = lerp(pickup, carry, carryLift); dir = new float[]{0, 1, 0};
            } else if (looting > 0.001f) {
                target = lerp(rest, toFigure(a, a.reachX + (float) Math.cos(a.yaw) * side * 0.12f, a.reachY, a.reachZ - (float) Math.sin(a.yaw) * side * 0.12f), looting);
                dir = new float[]{0, -1, -0.2f};
            } else if (shieldSide) {
                // the guard: the shield (or the blade) brought across in front of the chest and up to the chin, where it
                // covers the body from a blow - and where the eyes, behind it, can still see over it
                target = bareGuard ? (firstPerson ? view(aim, 3.5f, side, 1.5f, 6.5f) : torsoPoint(shoulder, -side * 0.6f, 1.5f, -3.3f)) : firstPerson ? view(aim,6.0f,side,9.5f,11.0f) : torsoPoint(shoulder, -side * 2.2f, -1.2f, -6.6f);
                if (bareGuard) target = lerp(rest, target, st.guardRaise);
                dir = holds != null && holds.family == Family.SHIELD ? new float[]{0, 1, 0} : norm(torsoDir(1f * -side, 0.35f, -0.4f));
            } else if (isMain && holds != null && holds.family == Family.THROWN && c.draw > 0) {
                // winding up a throw: the hand drawn back past the ear, the elbow high
                target = lerp(rest, torsoPoint(shoulder, side * 1.2f, 2.6f, 3.4f), Math.min(1, c.draw * 1.6f));
                dir = norm(lerp(dir, aim, c.draw));
            } else if (bow && !isMain && (st.bowRaise > 0.001f || c.blocking)) {
                // the bow held out to the aim only while it is drawn (carried at rest, the bow hand swings like any other:
                // held out the whole time, every running stride looked like a punch)
                target = lerp(rest, viewBow != null ? viewBow : add(shoulder, aim, 8.6f), st.bowRaise);
                dir = new float[]{0, 1, 0};
            } else if (bow && isMain && st.bowRaise > 0.001f) {
                float[] bowHand = hand[1];
                target = lerp(rest, add(add(bowHand, aim, -(1.5f + c.draw * 6.5f)), new float[]{0, 1, 0}, 0.6f * c.draw), st.bowRaise);
                dir = aim;
            } else if (holds != null && (holds.family == Family.STAFF || holds.family == Family.WAND) && c.castCooldown > 0.35f) {
                target = add(shoulder, aim, 8.6f);
                dir = norm(add(aim, new float[]{0, 1, 0}, 0.6f));
            } else if (c.staggered > 0) {
                target = torsoPoint(shoulder, side * 4f, -6f, -2.5f);
            } else if (!grounded) {
                target = torsoPoint(shoulder, side * 2.6f, -6.5f, -3.2f);
            } else target = rest;
            float[] pole = norm(lerp(torsoDir(side * 0.45f, -0.25f, 1f), new float[]{side, 0.1f, 0.3f}, st.crouchBlend));
            if (firstPerson && bareGuard) pole = norm(new float[]{side * 0.8f, -0.7f, -0.15f});
            float[] elbow = ik(shoulder, target, L_ARM1, L_ARM2, pole);
            float[] grip = add(elbow, norm(sub(target, elbow)), L_ARM2);
            float[] right = {torsoRight[0], torsoRight[1], torsoRight[2]};
            bone(seg[(k == 0 ? CharModel.Seg.R_ARM_UP : CharModel.Seg.L_ARM_UP).ordinal()], shoulder, elbow, right);
            bone(seg[(k == 0 ? CharModel.Seg.R_ARM_LO : CharModel.Seg.L_ARM_LO).ordinal()], elbow, grip, right);
            hand[k] = grip;
            fore[k] = norm(sub(grip, elbow));
            itemDir[k] = norm(dir);
        }
    }

    /**
     * A point in the view, for the hands seen through the eyes (figure pixels): so far ahead along the aim, out to the
     * hand's side, and down from the eyes - where a held weapon or shield sits low in the frame, whatever the pitch.
     */
    private float[] view(float[] aim, float out, int side, float down, float ahead) {
        float[] offset = viewDirection(aim, out * side, -down, ahead);
        return new float[]{eye[0] + offset[0], eye[1] + offset[1], eye[2] + offset[2]};
    }

    /** Offset in the camera's right/up/forward frame, shared by hand placement and item orientation. */
    private float[] viewDirection(float[] aim, float right, float up, float forward) {
        float[] f = norm(aim);
        float[] r = norm(new float[]{-f[2], 0, f[0]});
        if (dot(r, torsoRight) < 0) r = new float[]{-r[0], -r[1], -r[2]};
        float[] u = cross(r, f);
        if (u[1] < 0) u = new float[]{-u[0], -u[1], -u[2]};
        return new float[]{f[0] * forward + r[0] * right + u[0] * up, f[1] * forward + r[1] * right + u[1] * up, f[2] * forward + r[2] * right + u[2] * up};
    }

    /** A foot leaves the ground: its swing starts from where it stood. */
    private static void liftOff(State st, int k, float swingTime) {
        st.stance[k] = false;
        st.swing[k] = 0;
        st.swingTime[k] = swingTime;
        System.arraycopy(st.foot[k], 0, st.from[k], 0, 3);
        System.arraycopy(st.foot[k], 0, st.to[k], 0, 3);
    }

    /**
     * Where a swinging foot will land: under where the body will be when it comes down, out to its own side, and half a
     * stance further along the body's velocity, so that the body passes over it at mid-stance.
     */
    private static void stepTarget(Actor a, State st, int k, float hipX, float timeLeft, float stanceTime, float vx, float vz) {
        float px = a.x + vx * (timeLeft + stanceTime * 0.5f), pz = a.z + vz * (timeLeft + stanceTime * 0.5f);
        float rx = (float) Math.cos(a.yaw), rz = (float) -Math.sin(a.yaw);
        float side = (k == 0 ? 1 : -1) * hipX * Body.PX;
        st.to[k][0] = px + rx * side;
        st.to[k][1] = a.y;
        st.to[k][2] = pz + rz * side;
    }

    /**
     * A step never aims further from its hip than a leg can land it (a start from rest predicts a long way ahead) - from
     * the hip where it will be when the foot comes down (ox, oz: the body's travel until then), not where it is now.
     */
    private static void capStep(Actor a, State st, int k, float hipX, float maxPx, float ox, float oz) {
        float rx = (float) Math.cos(a.yaw), rz = (float) -Math.sin(a.yaw);
        float side = (k == 0 ? hipX : -hipX) * Body.PX;
        float cx = a.x + ox + rx * side, cz = a.z + oz + rz * side;
        float dx = st.to[k][0] - cx, dz = st.to[k][2] - cz, d = (float) Math.hypot(dx, dz), max = maxPx * Body.PX;
        if (d <= max) return;
        st.to[k][0] = cx + dx / d * max; st.to[k][2] = cz + dz / d * max;
    }

    private static void plant(Actor a, State st, int k) {
        st.stance[k] = true;
        st.swing[k] = 0;
        System.arraycopy(st.to[k], 0, st.foot[k], 0, 3);
        st.foot[k][1] = a.y;
    }

    /** s through the move as the arm should show it: the wind-up draws back (from the rest pose) and comes forward to meet the strike. */
    static float strikeS(Actor.Combat c) {
        return switch (c.phase) {
            case WINDUP -> { float u = Math.min(1, c.t / Math.max(1e-3f, c.windup)); yield -0.3f * (float) Math.sin(Math.PI * Math.pow(u, 1.5)); }
            case ACTIVE -> c.sweep;
            case RECOVER -> 1f;
            default -> 0f;
        };
    }

    /** How much of the strike's pose is showing: easing in from rest through the wind-up, back out through the recovery. */
    static float strikeWeight(Actor.Combat c) {
        return switch (c.phase) {
            case WINDUP -> { float u = Math.min(1, c.t / Math.max(1e-3f, c.windup)); yield 1 - (float) Math.pow(1 - u, 2.2); }   // drawn back, slowing as it cocks
            case ACTIVE -> 1f;
            case RECOVER -> { float u = Math.min(1, c.t / Math.max(1e-3f, c.recover)); yield 1 - u * u * (3 - 2 * u); }
            default -> 0f;
        };
    }

    /** CombatSystem.grip for a move the actor is not (yet) making, from the main hand. */
    private static float[] gripAs(Actor a, Move m, float s, boolean offHand) {
        boolean was = a.cs.offHand;
        a.cs.offHand = offHand;
        float[] g = CombatSystem.grip(a, m, s);
        a.cs.offHand = was;
        return g;
    }

    // ------------------------------------------------------------------ figure space <-> world
    static float[] toFigure(Actor a, float x, float y, float z) {
        float dx = x - a.x, dy = y - a.y, dz = z - a.z;
        float rx = (float) Math.cos(a.yaw), rz = (float) -Math.sin(a.yaw), bx = (float) Math.sin(a.yaw), bz = (float) Math.cos(a.yaw);
        return new float[]{(dx * rx + dz * rz) / Body.PX, dy / Body.PX, (dx * bx + dz * bz) / Body.PX};
    }

    static float[] toWorld(Actor a, float fx, float fy, float fz) {
        float rx = (float) Math.cos(a.yaw), rz = (float) -Math.sin(a.yaw), bx = (float) Math.sin(a.yaw), bz = (float) Math.cos(a.yaw);
        return new float[]{a.x + (fx * rx + fz * bx) * Body.PX, a.y + fy * Body.PX, a.z + (fx * rz + fz * bz) * Body.PX};
    }

    private static float[] toFigureDir(Actor a, float x, float y, float z) {
        float rx = (float) Math.cos(a.yaw), rz = (float) -Math.sin(a.yaw), bx = (float) Math.sin(a.yaw), bz = (float) Math.cos(a.yaw);
        return new float[]{x * rx + z * rz, y, x * bx + z * bz};
    }

    /** A direction along the torso's axes: x right, y up, z back. */
    private float[] torsoDir(float x, float y, float z) {
        return new float[]{torsoRight[0] * x + torsoUp[0] * y + torsoBack[0] * z, torsoRight[1] * x + torsoUp[1] * y + torsoBack[1] * z, torsoRight[2] * x + torsoUp[2] * y + torsoBack[2] * z};
    }

    private float[] torsoPoint(float[] origin, float x, float y, float z) {
        return new float[]{origin[0] + torsoRight[0] * x + torsoUp[0] * y + torsoBack[0] * z, origin[1] + torsoRight[1] * x + torsoUp[1] * y + torsoBack[1] * z,
                origin[2] + torsoRight[2] * x + torsoUp[2] * y + torsoBack[2] * z};
    }

    // ------------------------------------------------------------------ small vector and matrix tools
    /** Two-bone IK: the middle joint for a chain from a toward t, bending toward the pole. */
    static float[] ik(float[] a, float[] t, float l1, float l2, float[] pole) {
        float[] d = sub(t, a);
        float dist = len(d);
        float min = Math.abs(l1 - l2) + 0.01f, max = l1 + l2 - 0.001f;
        float dc = Math.max(min, Math.min(max, dist));
        float[] dir = dist < 1e-4f ? new float[]{0, -1, 0} : new float[]{d[0] / dist, d[1] / dist, d[2] / dist};
        float along = (l1 * l1 - l2 * l2 + dc * dc) / (2 * dc);
        float h = (float) Math.sqrt(Math.max(0, l1 * l1 - along * along));
        float pd = dot(pole, dir);
        float[] p = {pole[0] - dir[0] * pd, pole[1] - dir[1] * pd, pole[2] - dir[2] * pd};
        float pl = len(p);
        if (pl < 1e-4f) p = Math.abs(dir[1]) < 0.9f ? norm(cross(dir, new float[]{0, 1, 0})) : new float[]{1, 0, 0};
        else p = new float[]{p[0] / pl, p[1] / pl, p[2] / pl};
        return new float[]{a[0] + dir[0] * along + p[0] * h, a[1] + dir[1] * along + p[1] * h, a[2] + dir[2] * along + p[2] * h};
    }

    /** A segment's matrix: origin at the joint, its -y running to the child joint, its x as near the given right as can be. */
    static void bone(float[] m, float[] from, float[] to, float[] right) {
        float[] y = norm(sub(from, to));
        float d = dot(right, y);
        float[] x = {right[0] - y[0] * d, right[1] - y[1] * d, right[2] - y[2] * d};
        if (len(x) < 1e-3f) x = Math.abs(y[2]) < 0.9f ? cross(y, new float[]{0, 0, 1}) : new float[]{1, 0, 0};
        x = norm(x);
        float[] z = cross(x, y);
        m[0] = x[0]; m[1] = x[1]; m[2] = x[2]; m[3] = 0;
        m[4] = y[0]; m[5] = y[1]; m[6] = y[2]; m[7] = 0;
        m[8] = z[0]; m[9] = z[1]; m[10] = z[2]; m[11] = 0;
        m[12] = from[0]; m[13] = from[1]; m[14] = from[2]; m[15] = 1;
    }

    static void identity(float[] m) { java.util.Arrays.fill(m, 0); m[0] = m[5] = m[10] = m[15] = 1; }
    static void translate(float[] m, float x, float y, float z) { for (int i = 0; i < 4; i++) m[12 + i] += m[i] * x + m[4 + i] * y + m[8 + i] * z; }
    static void rotX(float[] m, float a) { float c = (float) Math.cos(a), s = (float) Math.sin(a); for (int i = 0; i < 4; i++) { float y = m[4 + i], z = m[8 + i]; m[4 + i] = y * c + z * s; m[8 + i] = -y * s + z * c; } }
    static void rotY(float[] m, float a) { float c = (float) Math.cos(a), s = (float) Math.sin(a); for (int i = 0; i < 4; i++) { float x = m[i], z = m[8 + i]; m[i] = x * c - z * s; m[8 + i] = x * s + z * c; } }
    static void rotZ(float[] m, float a) { float c = (float) Math.cos(a), s = (float) Math.sin(a); for (int i = 0; i < 4; i++) { float x = m[i], y = m[4 + i]; m[i] = x * c + y * s; m[4 + i] = -x * s + y * c; } }
    static float[] point(float[] m, float x, float y, float z) { return new float[]{m[0] * x + m[4] * y + m[8] * z + m[12], m[1] * x + m[5] * y + m[9] * z + m[13], m[2] * x + m[6] * y + m[10] * z + m[14]}; }

    static float[] add(float[] a, float[] b, float s) { return new float[]{a[0] + b[0] * s, a[1] + b[1] * s, a[2] + b[2] * s}; }
    static float[] sub(float[] a, float[] b) { return new float[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]}; }
    static float[] lerp(float[] a, float[] b, float t) { return new float[]{a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t}; }
    static float dot(float[] a, float[] b) { return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]; }
    static float len(float[] a) { return (float) Math.sqrt(dot(a, a)); }
    static float[] norm(float[] a) { float l = len(a); return l < 1e-6f ? new float[]{0, -1, 0} : new float[]{a[0] / l, a[1] / l, a[2] / l}; }
    static float[] cross(float[] a, float[] b) { return new float[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]}; }
    static float clamp01(float v) { return Math.max(0, Math.min(1, v)); }
    static float clamp(float v, float lo, float hi) { return Math.max(lo, Math.min(hi, v)); }
    static float wrap(float a) { while (a > Math.PI) a -= (float) (2 * Math.PI); while (a < -Math.PI) a += (float) (2 * Math.PI); return a; }
}
