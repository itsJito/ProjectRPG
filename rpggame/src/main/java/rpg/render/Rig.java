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
import rpg.item.Items;

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
        /** How fast the body is turning about its feet (rad/s, smoothed), from the yaw a frame ago. */
        float lastYaw, turnRate, turnSign = 1;
        /** How far into the side-on archer's stance the body has turned (0..1). */
        float aimSide;
        float headYaw, bowRaise, guardRaise, crouchBlend;
        /** The bow: how drawn it was last frame, when it was loosed, how long it has been held at full draw. */
        float lastDraw, heldFull; double loosedAt = -9;
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
            st.lastYaw = yaw; st.turnRate = 0;
            st.init = true;
        }
        st.lastX = a.x; st.lastZ = a.z;
        // turning on the spot: the feet must keep up with the body above them (gamefixes: the legs did not step when a
        // figure turned round - the planted feet stayed put and the legs wound round each other)
        float dyaw = Actor.wrap(yaw - st.lastYaw), yawStep = dt > 0 ? Math.abs(dyaw) / dt : 0;
        if (Math.abs(dyaw) > 1e-4f) st.turnSign = Math.signum(dyaw);
        st.lastYaw = yaw;
        st.turnRate += (yawStep - st.turnRate) * (1 - (float) Math.exp(-dt / 0.06f));
        boolean turning = st.turnRate > 1.0f;
        // aiming a bow, a crossbow or a throw in third person, the body turns side-on to the target (gamefixes): the
        // leading shoulder toward it, the hips and feet following, the head still looking down the line
        ItemDef held = a.main();
        boolean rangedAim = !firstPerson && !still && held != null && (held.family == Family.BOW || held.family == Family.CROSSBOW || held.family == Family.THROWN)
                && (a.cs.draw > 0 || a.cs.aim > 0.1f || a.cs.charge > 0);
        st.aimSide += ((rangedAim ? 1 : 0) - st.aimSide) * (1 - (float) Math.exp(-dt / 0.09f));
        // a bow is shot fully side-on; a crossbow from the shoulder, a little less turned
        float sideTurn = -(held != null && held.family == Family.BOW ? 1.45f : 1.2f) * st.aimSide;

        // ---- the gait: how fast, which way (figure space), how far through the stride cycle
        st.spd += ((grounded ? speed : 0) - st.spd) * Math.min(1, dt * 8);
        float lx = vx * rx + vz * rz, lz = vx * bx + vz * bz;
        if (speed > 0.2f) { float k = Math.min(1, dt * 9); st.dx += (lx / speed - st.dx) * k; st.dz += (lz / speed - st.dz) * k; }
        float dl = (float) Math.hypot(st.dx, st.dz);
        if (dl > 1e-3f) { st.dx /= dl; st.dz /= dl; } else { st.dx = 0; st.dz = -1; }
        // a walk starts a little above the still threshold and stops a little below it, so a body hovering at the
        // threshold does not flick between stepping and standing
        boolean wasWalking = st.walking;
        // Braking hard (the body's real speed fallen well under the smoothed one) ends the stride cycle at once: a foot
        // in the air finishes its own quick step in under the body. Left to the cycle, whose cadence falls with the
        // smoothed speed, the back foot of a stopped sprint hung a stride behind, kicked up, for half a second (gamefixes)
        boolean braking = speed < st.spd * 0.45f && speed < 1.2f;
        st.walking = grounded && !braking && (st.walking ? st.spd > Gait.STILL * 0.75f : st.spd > Gait.STILL * 1.25f);
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
        if (walking || st.corrective < 0 && st.stance[0] != st.stance[1]) {
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
            // Turning, the threshold drops and the steps quicken (left, right, left round the turn), and each lands where
            // the turn will have carried the body by then, so the feet come round with it instead of trailing.
            // a foot still in the air when the walk ended (a hard stop) comes down on its own step - left alone it hung
            // there, mid-stride, until the other foot's correction was done
            for (int k = 0; k < 2; k++) if (!st.stance[k] && st.corrective != k) {
                st.swing[k] = Math.min(1, st.swing[k] + dt / Math.max(0.12f, st.swingTime[k]));
                if (st.swing[k] >= 1) plant(a, st, k);
            }
            if (st.corrective < 0 && st.stance[0] && st.stance[1]) {
                int worst = -1; float far = 0;
                for (int k = 0; k < 2; k++) {
                    float[] rest = toWorldTurned(a, (k == 0 ? 1 : -1) * hipX, k == 0 ? -0.6f : 0.6f, sideTurn);
                    float d = (float) Math.hypot(st.foot[k][0] - rest[0], st.foot[k][2] - rest[2]);
                    if (d > far) { far = d; worst = k; }
                }
                if (far > (turning ? 0.9f : 1.8f) * Body.PX) {
                    st.corrective = worst;
                    // a foot left far out by a hard stop is brought in briskly; a small shuffle takes its time
                    liftOff(st, worst, turning ? Math.max(0.15f, 0.24f - st.turnRate * 0.012f) : far > 5 * Body.PX ? 0.2f : 0.26f);
                }
            }
            if (st.corrective >= 0) {
                int k = st.corrective;
                st.swing[k] += dt / st.swingTime[k];
                float lead = turning ? st.turnSign * Math.min(0.6f, st.turnRate * st.swingTime[k] * (1 - st.swing[k])) : 0;
                float[] rest = toWorldTurned(a, (k == 0 ? 1 : -1) * hipX, k == 0 ? -0.6f : 0.6f, lead + sideTurn);
                st.to[k][0] = rest[0]; st.to[k][1] = a.y; st.to[k][2] = rest[2];
                if (st.swing[k] >= 1) { plant(a, st, k); st.corrective = -1; }
            }
        }
        if (DEBUG && a instanceof rpg.actor.Player) { float[] f0 = toFigure(a, st.foot[0][0], st.foot[0][1], st.foot[0][2]), f1 = toFigure(a, st.foot[1][0], st.foot[1][1], st.foot[1][2]); System.out.printf("RIG ph %.3f walk %s cad %.2f st %s/%s sw %.2f/%.2f z %.1f/%.1f spd %.2f raw %.2f corr %d%n", st.phase, walking, cadence, st.stance[0], st.stance[1], st.swing[0], st.swing[1], f0[2], f1[2], st.spd, speed, st.corrective); }
        // where each foot is, in figure space: planted ones pinned; swinging ones on their arc
        float[][] foot = new float[2][3];
        float[] footFwd = new float[2];
        // a walking foot clears the ground by a little; a running one is kicked up behind (the heel toward the seat) and
        // carried through at knee height
        // (0.1.8: the heel comes up high and early at a run - the trailing leg trailed low and straight, so every stride
        // read as a lunge)
        // (0.2.0, gamefixes: the sprint still bounded - the trailing leg kicked out straight and low) a sprinter's heel
        // comes right up under the seat as the foot leaves the ground and is carried through high and tight, not trailed
        float lift = (1.3f + 4.6f * runF + 6.0f * sprintF) * Math.max(0.55f, st.amp);
        float sd = Math.max(1e-3f, speed), tdx = vx / sd, tdz = vz / sd;
        for (int k = 0; k < 2; k++) {
            float wx, wy, wz;
            if (st.stance[k]) { wx = st.foot[k][0]; wy = st.foot[k][1]; wz = st.foot[k][2]; }
            else {
                float t = st.swing[k], e = t * t * (3 - 2 * t);
                // running, the foot stays back a moment as the heel kicks up, then swings through fast
                float er = (float) Math.pow(e, 1 + 0.6f * runF - 0.45f * sprintF);
                float kick = runF * (2.2f - 1.4f * sprintF) * Body.PX * (float) Math.sin(Math.PI * Math.min(1, t * 1.7f)) * (1 - t);
                wx = st.from[k][0] + (st.to[k][0] - st.from[k][0]) * er - tdx * kick;
                wz = st.from[k][2] + (st.to[k][2] - st.from[k][2]) * er - tdz * kick;
                float arc = (float) Math.sin(Math.PI * Math.pow(t, 1 - 0.55f * runF));
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
        // (0.2.0, gamefixes: sneaking looked wrong - folded flat with the head thrust out) a sneak is knees bent and hips
        // down, the back leaning in a little, the head held level and watchful
        extraLean += 0.36f * st.crouchBlend;
        // (0.2.1) handed back from a get-up part-way (Reactions' rising): from the kneel to full height over half a second
        float rise = (float) (time - a.riseAt);
        if (rise >= 0 && rise < 0.5f) { float k = 1 - rise / 0.5f; k = k * k * (3 - 2 * k); crouch += 10 * k; extraLean += 0.4f * k; }
        float looting = a instanceof rpg.ai.Npc npc && (npc.goal == rpg.ai.Brain.Goal.LOOT || npc.goal == rpg.ai.Brain.Goal.AID || npc.goal == rpg.ai.Brain.Goal.WORK && npc.role == rpg.ai.Npc.Role.HUNTER) && time < npc.lootUntil
                ? Math.max(0, Math.min(1, Math.min((float) (time - npc.lootStarted) / 0.25f, (float) (npc.lootUntil - time) / 0.2f))) : 0;
        if (a.traversal != null) extraLean += 0.38f * (float) Math.sin(Math.PI * a.traversal.progress);
        float carryLift = a.carryBody == 0 ? 0 : Math.max(0, Math.min(1, (float) (time - a.carrySince) / 0.65f));
        float carryLower = a.carryLowerAt < 0 ? 0 : Math.min(1, (float) (time - a.carryLowerAt) / 0.45f);
        crouch += 4 * carryLower; extraLean += 0.55f * carryLower;
        if (a.carryBody != 0) { crouch += 5 * (float) Math.sin(Math.PI * carryLift); extraLean += 0.35f; }
        crouch += 5 * looting; extraLean += 0.9f * looting;
        // a grab: the free hand out to what is taken and back (0.6 s), the body only bending toward it when it is low
        float grabT = (float) (time - a.grabAt), grab = grabT < 0 || grabT > GRAB_TIME ? 0 : grabT < 0.22f ? ease(grabT / 0.22f) : grabT < 0.32f ? 1 : 1 - ease((grabT - 0.32f) / (GRAB_TIME - 0.32f));
        float grabLow = grab > 0 ? Math.max(0, Math.min(1, (a.y + a.height * 0.45f - a.grabY) / Math.max(0.1f, a.height * 0.45f))) : 0;
        crouch += 3.5f * grab * grabLow; extraLean += 0.6f * grab * grabLow;
        // a level's cheer (gamefixes): both arms thrown up, pumped twice, a dip and a lift through the knees, then down
        float cheerT = (float) (time - a.cheerAt);
        float cheer = cheerT < 0 || cheerT > CHEER_TIME || c.phase != Phase.IDLE || c.blocking || c.draw > 0 ? 0
                : cheerT < 0.16f ? ease(cheerT / 0.16f) : cheerT < CHEER_TIME - 0.28f ? 1 : 1 - ease((cheerT - (CHEER_TIME - 0.28f)) / 0.28f);
        float pump = cheer > 0 ? (float) Math.max(0, Math.sin((cheerT - 0.16f) / (CHEER_TIME - 0.44f) * Math.PI * 2)) : 0;
        crouch += cheer * (cheerT < 0.2f ? 1.6f * (float) Math.sin(cheerT / 0.2f * Math.PI) : 0) - cheer * 0.6f * pump;
        extraLean -= 0.12f * cheer;
        // which hand: the right if it is free (an archer's is: the bow is in the left), else the left - free or not, it
        // still makes the motion
        ItemDef grabMain = a.main();
        int grabHand = grabMain == null || grabMain.family == Family.BOW ? 0 : 1;
        if (c.staggered > 0) { float k = Math.min(1, c.staggered / 0.4f); extraLean -= 0.32f * k; extraTwist += 0.25f * k * (float) Math.sin(time * 9 + a.id); crouch += 1.2f * k; }
        extraLean -= 0.14f * c.flash;
        if (c.blocking) { extraLean += 0.06f; extraTwist -= 0.16f; crouch += 0.8f; }
        // (0.2.2, gamefixes: the walk and run change with the injury) the hurt are drawn hurt, by where they are hurt: a
        // body wound hunches them over it, a head wound tips the head and sets the walk wavering, an arm hangs or is
        // held to the body, a leg limps (below, in the hips); the player's pace is not touched by any of it
        float[] inj = a.injury;
        float hurtHead = inj == null ? 0 : clamp01(inj[0] * 1.6f), hurtBody = inj == null ? 0 : clamp01(inj[1] * 1.6f);
        float hurtArmR = inj == null ? 0 : clamp01(inj[2] * 1.8f), hurtArmL = inj == null ? 0 : clamp01(inj[3] * 1.8f);
        float hurtLegR = inj == null ? 0 : clamp01(inj[4] * 1.8f), hurtLegL = inj == null ? 0 : clamp01(inj[5] * 1.8f);
        extraLean += 0.22f * hurtBody + 0.05f * hurtHead;
        crouch += 1.2f * hurtBody;
        roll += 0.06f * hurtHead * (float) Math.sin(time * 1.3 + a.id) + 0.05f * (hurtLegR - hurtLegL);
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
            float wallWeight = anchor.vault()
                    ? clamp((motion.progress - motion.reachEnd) / 0.08f, 0, 1) * clamp((motion.mantleEnd + 0.06f - motion.progress) / 0.12f, 0, 1)
                    : clamp((motion.progress - motion.reachEnd * 0.5f) / (motion.reachEnd * 0.5f), 0, 1) * clamp((motion.hangEnd + 0.08f - motion.progress) / 0.08f, 0, 1);
            float landingWeight = clamp((motion.progress - motion.mantleEnd) / (1 - motion.mantleEnd), 0, 1);
            for (int k = 0; k < 2; k++) {
                float side = (k == 0 ? 1 : -1) * hipX * Body.PX;
                // a vault tucks the feet up level with the top as the body goes over; a climb sets them against the wall
                float[] wallFoot = toFigure(a, anchor.handX() + anchor.nx() * 0.07f + rx * side, anchor.vault() ? anchor.top() + 0.06f : anchor.top() - a.height * 0.75f, anchor.handZ() + anchor.nz() * 0.07f + rz * side);
                float[] landing = toFigure(a, anchor.landX() + rx * side, anchor.landY(), anchor.landZ() + rz * side);
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
        // a limp: the weight comes down hard and short on a hurt leg - the hips drop and lurch over it
        if (planted == 1 && walking) { float limp = st.stance[0] ? hurtLegR : hurtLegL; want -= limp * 1.6f * st.amp; sway *= 1 + limp * 0.8f; }
        sway *= (1 - runF * 0.6f);
        st.sway += (sway * Math.min(1, st.amp + 0.3f) - st.sway) * Math.min(1, dt * 7);
        // the pendulum rides down between the feet only so far: a stride is not a lunge
        if (grounded && (runF < 0.5f || !walking)) want = Math.max(want, hipFloor);
        // nor is a run: the reaching leg straightens toward a long stride rather than dragging the hips down into a lunge
        // (0.1.8: the jog and sprint were run crouched low, every stride a lunge)
        // (0.2.1) and higher still: a run is carried tall, the dip between footfalls a pixel, not two
        // (0.2.3) a run is carried taller again: hips a pixel under standing, the knees soft, not sat into
        else if (grounded) want = Math.max(want, b.hipY() - 0.35f - strideBend * 0.3f - 0.05f * (1 - sprintF) - 0.25f * sprintF);
        want -= st.land * 2.6f + crouch + runF * 0.05f;
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
        float lean = (0.03f + 0.06f * runF + 0.12f * sprintF) * st.amp * -st.dz + st.accLean + extraLean + 0.012f * (float) Math.sin(time * 1.7 + a.id);
        // (0.2.3, gamefixes: the sheathe 2/10) the body takes part: the shoulders turn toward the sheath and dip to it as
        // the hand goes there, and come back round with the draw
        float shS = (float) ((time - a.sheatheAt) / Actor.SHEATHE_TIME);
        if (shS >= 0 && shS < 1 && a.sheathable()) {
            float bodyIn = (float) Math.sin(Math.PI * clamp01(shS));
            boolean hip = a.main() != null && !a.main().twoHanded() && a.main().family != Family.BOW && a.main().family != Family.CROSSBOW
                    && a.main().family != Family.STAFF && a.main().family != Family.SPEAR && a.main().family != Family.HALBERD;
            extraTwist += (hip ? 0.28f : -0.16f) * bodyIn;
            lean += (hip ? 0.1f : 0.04f) * bodyIn;
        }
        float twist = gaitTwist + extraTwist + sideTurn;
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
        float hipTwist = -0.6f * gaitTwist + sideTurn * 0.75f;
        for (int k = 0; k < 2; k++) {
            int side = k == 0 ? 1 : -1;
            float cs = (float) Math.cos(hipTwist), sn = (float) Math.sin(hipTwist);
            float[] hip = {pelvis[0] + side * hipX * cs, pelvis[1], pelvis[2] - side * hipX * sn};
            float[] target = foot[k];
            float[] d = sub(target, hip);
            float dd = len(d);
            if (dd > legLen - 0.02f) target = add(hip, d, (legLen - 0.02f) / dd);   // out of reach: the leg straight toward it
            // the knee points along the foot's own heading, which leads the hips a little in a turn - and down: (0.2.1) a heel
            // kicked up behind left the leg's line nearly opposite a level pole, so only its small outward lean survived the
            // projection and the knee swung out to the side as a sprint braked (SprintStopTest)
            float[] pole = norm(new float[]{side * 0.06f + sn * -1, -0.6f, -cs});
            float[] knee = ik(hip, target, L_LEG1, L_LEG2, pole);
            float[] right = {cs, 0, -sn};
            bone(seg[(k == 0 ? CharModel.Seg.R_LEG_UP : CharModel.Seg.L_LEG_UP).ordinal()], hip, knee, right);
            bone(seg[(k == 0 ? CharModel.Seg.R_LEG_LO : CharModel.Seg.L_LEG_LO).ordinal()], knee, target, right);
        }
        // ---- the arms
        // (0.2.2) put away, what is equipped rides on the body and the arms are free; drawing or sheathing, the hand goes
        // to the item's place on the body and back, the item changing hands with the place halfway
        boolean inHand = a.inHand(time);
        ItemDef main = inHand ? a.main() : null, off = inHand ? a.off() : null;
        float sheathS = (float) ((time - a.sheatheAt) / Actor.SHEATHE_TIME);
        boolean sheathing = sheathS >= 0 && sheathS < 1 && a.sheathable();
        boolean bowEquipped = a.main() != null && a.main().family == Family.BOW;
        boolean bow = main != null && main.family == Family.BOW;
        boolean crossbow = main != null && main.family == Family.CROSSBOW;
        st.bowRaise += ((bow && c.draw > 0 ? 1 : 0) - st.bowRaise) * (1 - (float) Math.exp(-18 * dt));
        st.guardRaise += ((c.blocking && main == null && off == null ? 1 : 0) - st.guardRaise) * (1 - (float) Math.exp(-22 * dt));
        float[] aim = {(float) Math.sin(st.headYaw) * -(float) Math.cos(a.pitch), (float) Math.sin(a.pitch), -(float) Math.cos(a.pitch) * (float) Math.cos(st.headYaw)};
        // the bow through the eyes (gamefixes: in first person the bow did not move): pushed out toward the target as the
        // string comes back, trembling a little and then more the longer full draw is held, and jumping forward and down
        // as the arrow leaves; a little bob with the stride
        if (bow && st.lastDraw > 0.2f && c.draw <= 0) st.loosedAt = time;
        st.heldFull = bow && c.draw >= 0.98f ? st.heldFull + dt : Math.max(0, st.heldFull - dt * 3);
        st.lastDraw = bow ? c.draw : 0;
        float kick = (float) Math.max(0, 1 - (time - st.loosedAt) / 0.35);
        float shake = Math.min(1, st.heldFull / 3f) * 0.35f + 0.04f;
        float swayR = shake * (float) (Math.sin(time * 7.3 + a.id) * 0.6 + Math.sin(time * 13.1) * 0.4);
        float swayU = shake * (float) (Math.sin(time * 6.1 + 1.3) * 0.6 + Math.sin(time * 11.7) * 0.4) + st.amp * 0.25f * (float) Math.sin(st.phase * Math.PI * 4);
        float[] viewBow = firstPerson && bow ? view(aim, 1f - c.draw * 0.6f + swayR + kick * 0.4f, -1, 3.5f - c.draw * 0.8f - swayU + kick * 1.2f, 10f + c.draw * 1.6f - kick * 0.8f) : null;
        // aiming from the shoulder in third person (gamefixes: the drawing arm went through the chest): the line of the
        // shot runs past the right cheek - the string is drawn to it, a crossbow's stock is set under it - with 'perp' the
        // aim line's right, so the arms work either side of the body instead of across it
        float[] perp = norm(new float[]{-aim[2], 0, aim[0]});
        float[] cheek = add(add(add(point(tm, 0, b.neckY() - b.hipY(), 0), new float[]{0, 1, 0}, 2.0f), aim, 2.4f), perp, 4.4f);
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
            // (0.2.2, gamefixes: the arms barely swung) the swing is the arm's own pendulum on the stride's clock - not read
            // off where the feet happen to stand, which a planted foot, a short step or a stop on the spot held still -
            // so it keeps its full arc through a short stride and eases out as the body comes to rest
            float armPh = (st.phase + (k == 0 ? 0 : 0.5f)) % 1f;
            float desiredSwing = walking ? -(float) Math.cos(2 * Math.PI * armPh) : 0;
            st.armSwing[k] += (desiredSwing - st.armSwing[k]) * (1 - (float) Math.exp(-18 * dt));
            float opp = st.armSwing[k];
            float theta = swingAmp * opp;
            // the free arm as a runner carries it (the old target pushed the hand straight out ahead at chest height, the
            // arm nearly straight - every running stride read as a jab): the upper arm swings from the shoulder, further
            // back than forward, and the elbow is held bent - a little at a walk, near a right angle at a run, closing as
            // the arm comes forward - so the hand arcs from beside the hip up to the chest and drifts in toward the
            // body's middle on the forward swing; the hand's place follows from those two angles
            // (0.2.1) a runner's arms pump: the old run swing (0.5 rad forward) left the forearm parked across the chest
            // (0.2.3) at a jog the forward swing stops at the ribs, the hand never out past the chest like a guard
            float fAmp = (0.5f + 0.02f * runF + 0.3f * sprintF) * st.amp, bAmp = (0.46f + 0.4f * runF + 0.2f * sprintF) * st.amp;
            // (taken against the torso's lean, so a sprinter's forward swing stays a forward swing in the world, not a
            // reach up past the chin)
            float phi = (opp >= 0 ? opp * fAmp : opp * bAmp) - lean * 0.8f;
            float eps = 0.2f + runF * (1.0f + 0.1f * Math.max(0, opp) + 0.2f * sprintF);
            float u1 = L_ARM1 * 0.99f, u2 = L_ARM2 * 0.99f;
            float hy = -(u1 * (float) Math.cos(phi) + u2 * (float) Math.cos(phi + eps));
            float hz = -(u1 * (float) Math.sin(phi) + u2 * (float) Math.sin(phi + eps));
            float hx = side * (0.75f + 0.25f * (1 - runF) - 0.45f * runF * Math.max(0, opp));
            float[] rest = torsoPoint(shoulder, hx, hy, hz);
            // a hurt arm swings less, and a badly hurt one is held in against the body; so is the hand over a body wound
            float armHurt = k == 0 ? hurtArmR : hurtArmL;
            if (holds == null && armHurt > 0) rest = lerp(rest, torsoPoint(shoulder, -side * 1.6f, -7.2f, -3.6f), Math.min(1, armHurt * 1.2f));
            else if (holds == null && k == 1 && hurtBody > 0.3f) rest = lerp(rest, torsoPoint(shoulder, -side * 2.6f, -6.4f, -3.4f), (hurtBody - 0.3f) * 1.4f);
            if (holds == null) rest = lerp(rest, new float[]{side * (b.shoulderX() + 0.6f), 4.8f, -8.5f + opp * 0.5f}, st.crouchBlend);
            if (holds != null && holds.family != Family.SHIELD && holds.family != Family.BOW && holds.family != Family.STAFF)
                rest = torsoPoint(shoulder, side * 0.5f, -7.4f + 1.0f * (float) Math.cos(theta), -3.8f - 3.0f * (float) Math.sin(theta));
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
            } else if (sheathing && (bowEquipped ? (k == 1 ? a.main() : null) : isMain ? a.main() : a.off()) != null) {
                ItemDef it = bowEquipped ? a.main() : isMain ? a.main() : a.off();
                float[][] pl = sheathPlace(it, bowEquipped || isMain, b, a.eq.def(ItemDef.Slot.BACK) != null, a.eq.def(ItemDef.Slot.CAPE) != null);
                float[] spot = point(tm, pl[0][0], pl[0][1], pl[0][2]);
                // over the shoulder for the back, across the body for the hip: out from the body a little on the way
                // (0.2.3) to the sheath by four tenths, the grip held there a beat while the item changes hands, then
                // away; drawn, the blade comes out up and forward in an arc before it settles; put away, it is lifted
                // round in that arc first and slid home
                float sIn = clamp01(sheathS / 0.4f), sOut = clamp01((sheathS - 0.6f) / 0.4f);
                float reach = sheathS < 0.4f ? sIn * sIn * (3 - 2 * sIn) : sheathS < 0.6f ? 1 : 1 - sOut * sOut * (3 - 2 * sOut);
                float flourish = a.sheathed ? (float) Math.sin(Math.PI * sIn) * (1 - sIn * 0.3f) : (float) Math.sin(Math.PI * sOut);
                float clear = (float) Math.sin(Math.PI * clamp01(sheathS)) * 0.5f;
                spot = add(spot, torsoDir(0, 0, -1), clear * 1.5f);
                target = add(lerp(rest, spot, reach), torsoDir(0, 0.8f, -0.6f), flourish * 3.2f);
                dir = norm(lerp(dir, torsoDir(pl[1][0], pl[1][1], pl[1][2]), reach));
            } else if (crossbow && (isMain || off == null)) {
                // (0.2.2, gamefixes: crossbows in third person) a two-handed weapon held in both hands: carried across the
                // body at the waist, the trigger hand at the grip and the other under the fore-stock; raised, the stock to
                // the cheek; loosed, it kicks up and back; then it is tipped down and the string spanned back with the
                // free hand, and brought up again
                float raise = firstPerson ? 0.35f + c.aim * 0.65f : c.aim;
                float spent = clamp01(1 - c.reload / 1.25f), reloading = c.reload > 0 ? 1 : 0;
                float xkick = c.reload > 0 ? (float) Math.sin(Math.PI * clamp01(spent / 0.12f)) : 0;
                float span = c.reload > 0 && spent > 0.15f ? (float) Math.sin(Math.PI * clamp01((spent - 0.15f) / 0.8f)) : 0;
                float[] port = torsoPoint(point(tm, -b.shoulderX(), b.shoulderY() - b.hipY(), 0), 1.2f, -6.8f, -3.4f);
                float[] portDir = norm(torsoDir(0.5f, 0.35f, -0.8f)), lowDir = norm(torsoDir(0.1f, -0.75f, -0.65f));
                if (isMain) {
                    // the grip under the cheek, the stock against the shoulder, the bolt on the line of the eye
                    float[] up = firstPerson ? view(aim, 0.5f, 1, 3.6f, 4.5f) : add(add(add(cheek, aim, 2.6f), new float[]{0, 1, 0}, -2.6f), perp, -1.2f);
                    target = firstPerson ? lerp(rest, up, raise) : lerp(port, up, raise * (1 - span));
                    dir = firstPerson ? norm(lerp(dir, aim, raise)) : norm(lerp(lerp(portDir, aim, raise), lowDir, span));
                    target = add(add(target, aim, -1.4f * xkick), new float[]{0, 1, 0}, 0.9f * xkick);
                    dir = norm(add(dir, new float[]{0, 1, 0}, 0.5f * xkick));
                    if (!firstPerson && reloading > 0) target = lerp(target, add(port, new float[]{0, 1, 0}, -0.6f), span);
                } else if (firstPerson) {
                    target = add(add(hand[0], itemDir[0], 2.5f), torsoRight, -0.8f);
                    target = add(target, torsoUp, -0.6f);
                } else {
                    // under the fore-stock; spanning, back along the stock to the latch and forward again
                    float along = 5.2f - 3.6f * span;
                    target = add(add(hand[0], itemDir[0], along), new float[]{0, 1, 0}, -0.6f + 0.8f * span);
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
            } else if (cheer > 0.001f && a.carryBody == 0) {
                // both hands up over the head, a little apart, open to the sky - whatever they hold goes up with them
                float[] up = torsoPoint(shoulder, side * (1.6f + 0.4f * pump), 9.2f + 1.0f * pump, -1.2f);
                target = lerp(rest, up, cheer);
                dir = norm(lerp(dir, new float[]{side * 0.2f, 1, -0.15f}, cheer));
            } else if (grab > 0.001f && k == grabHand && c.phase == Phase.IDLE && c.draw <= 0 && !c.blocking) {
                float[] at = toFigure(a, a.grabX, a.grabY, a.grabZ);
                // no further than the arm reaches from where the shoulder is: the hand closes on air if it is out of reach
                float[] off2 = sub(at, shoulder);
                float reachLen = (L_ARM1 + L_ARM2) * 0.97f, l2 = (float) Math.sqrt(off2[0] * off2[0] + off2[1] * off2[1] + off2[2] * off2[2]);
                if (l2 > reachLen) at = add(shoulder, off2, reachLen / l2);
                target = lerp(rest, at, grab);
                dir = norm(lerp(dir, new float[]{0, -1, -0.3f}, grab));
            } else if (looting > 0.001f) {
                target = lerp(rest, toFigure(a, a.reachX + (float) Math.cos(a.yaw) * side * 0.12f, a.reachY, a.reachZ - (float) Math.sin(a.yaw) * side * 0.12f), looting);
                dir = new float[]{0, -1, -0.2f};
            } else if (fishing(a) != null) {
                float[][] fp = fishPose(a, st, shoulder, side, isMain, time, b);
                target = fp[0]; dir = fp[1];
            } else if (cooking(a) != null) {
                float[][] cp = cookPose(a, shoulder, side, isMain, time);
                target = cp[0]; dir = cp[1];
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
                // (0.2.1, gamefixes: the third-person draw reworked) the bow comes up in an arc from low before the body to
                // straight out along the line at the shoulder's height - not slid there - and is held canted a little
                // toward the drawing side, as an archer holds it
                float up = st.bowRaise * st.bowRaise * (3 - 2 * st.bowRaise), dropAngle = (1 - up) * 0.9f;
                float[] low = norm(add(aim, new float[]{0, -1, 0}, (float) Math.tan(dropAngle)));
                target = viewBow != null ? lerp(rest, viewBow, st.bowRaise) : lerp(rest, add(shoulder, low, 8.6f), Math.min(1, st.bowRaise * 1.4f));
                dir = firstPerson ? new float[]{0, 1, 0} : norm(add(new float[]{0, 1, 0}, perp, 0.22f * up));
            } else if (bow && isMain && st.bowRaise > 0.001f) {
                float[] bowHand = hand[1];
                // the string from the bow back to the cheek as the draw comes on (through the eyes: straight back down the line)
                float[] drawn = firstPerson ? add(add(bowHand, aim, -(1.5f + c.draw * 6.5f)), new float[]{0, 1, 0}, 0.6f * c.draw) : lerp(add(bowHand, aim, -1.5f), cheek, Math.min(1, c.draw * 1.15f));
                target = lerp(rest, drawn, st.bowRaise);
                dir = aim;
            } else if (isMain && time - a.castAt >= 0 && time - a.castAt < 0.7) {
                float cast=(float)((time-a.castAt)/0.7),reach=(float)Math.sin(Math.PI*Math.min(1,cast*1.4f));
                target = lerp(rest, firstPerson ? view(aim,7,side,4,5) : add(shoulder, aim, 8.6f), 0.45f+0.55f*reach);
                dir = norm(add(aim, new float[]{0, 1, 0}, 0.6f));
            } else if (c.staggered > 0) {
                target = torsoPoint(shoulder, side * 4f, -6f, -2.5f);
            } else if (!grounded) {
                target = torsoPoint(shoulder, side * 2.6f, -6.5f, -3.2f);
            } else target = rest;
            float[] pole = norm(lerp(torsoDir(side * 0.45f, -0.25f, 1f), new float[]{side, 0.1f, 0.3f}, st.crouchBlend));
            if (firstPerson && bareGuard) pole = norm(new float[]{side * 0.8f, -0.7f, -0.15f});
            // aiming side-on: the drawing elbow back along the line at the shoulder's height, the bow arm's elbow turned
            // down and out of the string's way; a crossbow's elbows down either side of the stock
            // (0.2.0, gamefixes: the bow's draw looked wrong - the drawing elbow rose over the head) the drawing elbow points
            // straight back and out at the height of the shoulder, the bow arm's elbow turned down
            if (!firstPerson && bow && st.bowRaise > 0.001f)
                pole = norm(lerp(pole, isMain ? norm(new float[]{-aim[0] + perp[0] * 0.55f, -0.05f + 0.3f * c.draw - aim[1], -aim[2] + perp[2] * 0.55f}) : norm(new float[]{-perp[0] * 0.35f, -1, -perp[2] * 0.35f}), st.bowRaise));
            else if (!firstPerson && crossbow && c.aim > 0.05f)
                pole = norm(lerp(pole, isMain ? norm(new float[]{perp[0] * 0.8f, -0.7f, perp[2] * 0.8f}) : norm(new float[]{-perp[0] * 0.4f, -1, -perp[2] * 0.4f}), Math.min(1, c.aim)));
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
    /** What is being cooked while cooking is under way at a range or a fire (gamefixes: cooking is animated), or null. */
    public static ItemDef cooking(Actor a) {
        var w = a.working;
        if (w == null || !(w.target instanceof rpg.objects.Station s) || s.kind != rpg.item.Recipes.Station.RANGE) return null;
        String[] parts = w.action.split(":");
        var r = parts.length > 1 ? rpg.item.Recipes.byId(parts[1]) : null;
        return r == null || r.inputs.isEmpty() ? null : r.inputs.get(0).item();
    }

    /**
     * Cooking, on each cook's cycle: the food held out over the heat at arm's length and low, slowly drawn round as it
     * cooks, lifted and turned over at half time, then settled back to finish; the other hand held out to the warmth,
     * palm toward the fire.
     */
    private float[][] cookPose(Actor a, float[] shoulder, int side, boolean isMain, double time) {
        var w = a.working;
        float k = w.t / Math.max(0.2f, w.duration);
        if (!isMain) {
            float sway = (float) Math.sin(time * 1.3 + a.id) * 0.25f;
            return new float[][]{torsoPoint(shoulder, -side * 0.5f + sway, -4.6f, -5.6f), norm(torsoDir(0, 0.25f, -1f))};
        }
        float settle = Math.min(1, w.t / 0.35f), e = settle * settle * (3 - 2 * settle);
        float[] rest = torsoPoint(shoulder, side * 0.3f, -3.5f, -3.0f), over = torsoPoint(shoulder, -side * 0.4f, -5.8f, -7.4f);
        // a slow circle over the coals, and the turn at half time: up a little and over
        float circle = (float) (time * 2.1);
        float turn = (float) Math.exp(-Math.pow((k - 0.5f) / 0.07f, 2));
        float[] p = lerp(rest, over, e);
        p = add(add(p, torsoRight, (float) Math.cos(circle) * 0.35f), torsoUp, (float) Math.sin(circle) * 0.2f + turn * 1.4f);
        float[] dir = norm(torsoDir(side * 0.15f, -0.35f + turn * 0.9f, -1f));
        return new float[][]{p, dir};
    }

    /** The fishing tool in the hand while fishing is under way at a fishing spot (gamefixes: fishing is animated), or null. */
    static ItemDef fishing(Actor a) {
        var w = a.working;
        if (w == null || !(w.target instanceof rpg.objects.Node n) || n.kind != rpg.objects.Node.Kind.FISH) return null;
        ItemDef t = a.main();
        return t != null && t.toolSkill == rpg.skill.Skill.FISHING ? t : null;
    }

    /**
     * Where each hand goes, fishing, by the tool and how far through the work it is. A rod: swung back over the shoulder
     * and cast forward (the first half second), then held out low with the tip up, nodding with the line, the other hand
     * at the reel - turning it as the work comes to its end. A net: gathered at the chest, flung out and down with both
     * hands, then its line held low in front. A crab cage: carried out and lowered to the water at arm's length.
     */
    private float[][] fishPose(Actor a, State st, float[] shoulder, int side, boolean isMain, double time, Body b) {
        ItemDef tool = fishing(a);
        var w = a.working;
        float t = w.t, left = w.duration - w.t;
        if (tool == Items.FISHING_ROD) {
            float cast = Math.min(1, t / 0.55f), e = cast * cast * (3 - 2 * cast);
            float[] back = torsoPoint(shoulder, side * 0.8f, 2.8f, 2.6f), out = torsoPoint(shoulder, side * 0.4f, -4.0f, -7.2f);
            float bob = (float) Math.sin(time * 2.3 + a.id) * 0.06f;
            float[] dirBack = norm(torsoDir(0, 0.6f, 0.8f)), dirOut = norm(torsoDir(0, 0.62f + bob, -0.78f));
            float[] main = cast < 1 ? lerp(back, out, e) : out;
            float[] mdir = cast < 1 ? norm(lerp(dirBack, dirOut, e)) : dirOut;
            if (isMain) return new float[][]{main, mdir};
            // the other hand on the reel, a hand's width down the butt; it winds as the catch comes in
            float[] reel = add(main, mdir, -3.2f);
            if (left < 0.8f) { float r = (float) (time * 9); reel = add(add(reel, torsoRight, (float) Math.cos(r) * 0.7f), torsoUp, (float) Math.sin(r) * 0.7f); }
            return new float[][]{add(reel, torsoRight, -0.6f), mdir};
        }
        if (tool == Items.FISHING_NET) {
            float fling = Math.min(1, t / 0.5f), e = (float) Math.sin(Math.PI * 0.5f * fling);
            float[] chest = torsoPoint(shoulder, -side * 1.2f, -3.5f, -2.0f), flung = torsoPoint(shoulder, side * 0.8f, -6.0f, -8.0f), held = torsoPoint(shoulder, side * 0.5f, -7.8f, -5.0f);
            float[] p = fling < 1 ? lerp(chest, flung, e) : lerp(flung, held, Math.min(1, (t - 0.5f) / 0.6f));
            return new float[][]{p, norm(torsoDir(0, -0.6f, -0.8f))};
        }
        // a crab cage, lowered to arm's length and held there
        float lower = Math.min(1, t / 0.9f), e = lower * lower * (3 - 2 * lower);
        float[] carried = torsoPoint(shoulder, side * 0.9f, -6.0f, -3.5f), down = torsoPoint(shoulder, side * 1.4f, -9.5f, -7.0f);
        return new float[][]{lerp(carried, down, e), new float[]{0, -1, 0}};
    }

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

    /** A figure-space point on the ground, as it will lie once the body has turned a further extra radians. */
    static float[] toWorldTurned(Actor a, float fx, float fz, float extra) {
        float y = a.yaw + extra, rx = (float) Math.cos(y), rz = (float) -Math.sin(y), bx = (float) Math.sin(y), bz = (float) Math.cos(y);
        return new float[]{a.x + (fx * rx + fz * bx) * Body.PX, a.y, a.z + (fx * rz + fz * bz) * Body.PX};
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

    /**
     * (0.2.2, gamefixes: the sheathe system) Where a put-away item rides, in the torso's frame (figure pixels, +x the
     * figure's left, +y up from the hips, +z its back): {grip, direction from the grip to the tip, the item's broad side}.
     * A bow, a crossbow, a staff, a great weapon or a polearm goes across the back, the hilt or the top over the right
     * shoulder where the right hand finds it; a shield flat on the back; a one-handed weapon at the hip, the main hand's
     * on the left (drawn across the body), the off hand's on the right behind the pouch. Over a pack or a cape, further out.
     */
    static float[][] sheathPlace(ItemDef d, boolean mainHand, Body b, boolean pack, boolean cape) {
        float back = b.torso[2] * b.depth / 2f + 0.9f + (pack ? 2.6f : 0) + (cape ? 0.4f : 0), w = b.torso[0] * b.girth / 2f, h = b.torso[1];
        Family f = d.family;
        if (f == Family.SHIELD) return new float[][]{{0, h * 0.5f, back + 0.3f}, {0, 1, 0}, {-1, 0, 0}};
        if (f == Family.BOW) return new float[][]{{0, h * 0.55f, back}, norm(new float[]{-0.45f, 0.89f, 0}), {1, 0, 0}};
        if (f == Family.CROSSBOW) return new float[][]{{-w * 0.2f, h * 0.55f, back + 0.4f}, norm(new float[]{0.35f, -0.94f, 0}), {0, 0, 1}};
        if (f == Family.STAFF || f == Family.SPEAR || f == Family.HALBERD) return new float[][]{{0, h * 0.45f, back}, norm(new float[]{-0.33f, 0.94f, 0}), {1, 0, 0}};
        // an axe, a hammer or a club hangs head up (its haft from the left hip to over the right shoulder); a blade hilt up
        if (d.twoHanded() && (f == Family.AXE || f == Family.WARHAMMER || f == Family.MACE)) return new float[][]{{w * 0.5f, h * 0.2f, back}, norm(new float[]{-0.55f, 0.83f, 0}), {1, 0, 0}};
        if (d.twoHanded()) return new float[][]{{-w * 0.55f, h * 1.02f, back}, norm(new float[]{0.55f, -0.83f, 0}), {1, 0, 0}};
        float side = mainHand ? 1 : -1;
        return new float[][]{{side * (w + 0.7f), 1.6f, mainHand ? -1.0f : 1.4f}, norm(new float[]{side * 0.12f, -0.72f, 0.68f}), {0, 0, 1}};
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
    /** How long a level's cheer lasts (s). */
    static final float CHEER_TIME = 1.15f;
    /** How long a grab takes, out and back (s). */
    static final float GRAB_TIME = 0.6f;
    private static float ease(float t) { t = Math.max(0, Math.min(1, t)); return t * t * (3 - 2 * t); }

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
