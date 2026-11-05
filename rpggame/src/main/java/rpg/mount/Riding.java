package rpg.mount;

import static astratto.bridge.AstrattoNative.*;

import java.util.HashMap;
import java.util.Map;
import rpg.Rpg;
import rpg.actor.Actor;
import rpg.animal.Animal;
import rpg.animal.Species;
import rpg.interact.Result;
import rpg.skill.Skill;
import rpg.world.DevMap;

/**
 * (0.3.0, gamefixes: mountable horses, proper riding, the Pegasus) Riding is built round a controllable animal: the
 * mount handles its own movement and collision ({@link MountController}); the rider becomes its passenger, carried in
 * the saddle. What the rider's keys mean is translated into the mount's movement - and only as far as the mount allows:
 *
 * <ul>
 * <li>A <b>wild</b> horse will take a passenger, but not steering: it bucks and bolts where it likes, and may throw them.
 *     Every moment kept on its back is taming progress, which the horse keeps (it is persistent: a second attempt starts
 *     where the first left off). Fully tamed, it is the rider's.</li>
 * <li>A <b>tame</b> horse carries its rider calmly but, without a saddle, cannot be steered.</li>
 * <li>A <b>saddled</b> tame horse is ridden: forward to go (a canter; with the run key a gallop, on the mount's wind; with
 *     crouch a walk), back to slow and back up, the strafe keys to rein round; the jump key held charges a jump, let go
 *     to jump.</li>
 * </ul>
 *
 * <p>A jump combines two numbers: the <b>charge</b> - how well the rider timed it, rising over about ten ticks to its
 * peak and then sagging toward 80% - and the horse's own <b>jump strength</b>. Small rises are stepped in the stride
 * (the controller's step), never needing a jump. A Pegasus taken up from a gallop with a full jump flies: a momentum
 * game - pitch down to dive and gather speed, pull up to climb and spend it, bank round on the strafe keys (the faster,
 * the wider the turn), a surge of the wings on the jump key spends vigor, and too slow it sinks; it lands where it meets
 * the ground slowly enough.
 *
 * <p>The input is a small record ({@link Controls}) so the same mount can later be driven by something other than the
 * player's keys, and the same controls drive any other mountable thing that is given a controller.
 */
public final class Riding {
    /** The rider's wishes this frame, whatever is giving them. */
    public record Controls(float throttle, float steer, boolean sprint, boolean slow, boolean jumpHeld, boolean dismount) {}

    /** One ride under way. */
    public static final class Ride {
        public final Animal mount;
        public final Actor rider;
        public final MountController body;
        /** Forward speed (m/s, negative backing up); the jump key's ticks held and the resulting charge; airborne flight. */
        public float speed, chargeTicks, charge;
        public boolean jumpWas, flying;
        /** Flight: pitch (radians, + up), bank (+ right), airspeed along the heading. */
        public float pitch, bank, airspeed;
        /** A wild one's own will: where it bolts, until when; when it last tried to throw its rider. */
        float wildYaw, wildSpeed;
        double wildUntil, buckAt;
        public final double since;
        /** (0.3.3) The top of the present fall (where the mount last left the ground); a glide in progress (Pegasus). */
        public float fallTop = Float.NaN;
        public boolean gliding;
        Ride(Animal mount, Actor rider, MountController body, double since) { this.mount = mount; this.rider = rider; this.body = body; this.since = since; }
    }

    /**
     * (0.3.1) Taming on its back: progress per second at Agility 1 with no trust (about half a minute unsaddled), and
     * how much faster a saddle makes it. A buck's chance of throwing the rider shrinks with the taming done, Agility,
     * trust won by feeding and handling, and above all a saddle to hold on to.
     */
    public static final float TAME_RATE = 0.042f, SADDLE_TAMING = 2.6f, THROW_CHANCE = 0.13f, SADDLE_THROW = 0.3f;
    public static float throwChance(Animal a, int agility, float trust) {
        return THROW_CHANCE * (1 - a.tame) * (1 - Math.min(0.8f, agility / 99f)) * (1 - 0.6f * Math.min(1, trust)) * (a.saddled ? SADDLE_THROW : 1f);
    }

    /** Charge: ticks to the peak, and where it sags to. */
    public static final float CHARGE_PEAK_TICKS = 10, CHARGE_FLOOR = 0.8f;
    /** The highest a mount's hooves rise in a jump (m): a two-block wall is cleared, a three-block one is not. */
    public static final float MAX_JUMP = 2.2f;
    /** Flight: below this airspeed the wings no longer hold it up (m/s); a surge's cost in vigor and its push. */
    public static final float STALL = 6.5f, SURGE_COST = 18, SURGE_PUSH = 4.5f;
    /** A glide's sink (m/s): wings out, a Pegasus comes down about a metre for every four or five it goes. */
    public static final float GLIDE_SINK = 2.1f;

    private final Rpg g;
    public final Map<Long, Ride> rides = new HashMap<>();
    public Riding(Rpg g) { this.g = g; }

    public static boolean mountable(Animal a) { return a.alive && !a.baby && (a.species == Species.HORSE || a.species == Species.PEGASUS); }
    public Ride of(Actor rider) { for (Ride r : rides.values()) if (r.rider == rider) return r; return null; }
    public Ride ridden(Animal a) { return rides.get(a.id); }

    /** The stats a mount carries (rolled the first time they are asked for, then kept with the animal). */
    public static MountStats stats(Animal a) {
        if (a.mountStats == null) a.mountStats = a.species == Species.PEGASUS ? MountStats.pegasus(a.id) : MountStats.horse(a.id);
        return a.mountStats;
    }
    /** Whether this rider has this horse tamed (it was tamed by them, or it is theirs and knows them). */
    public static boolean tamedFor(Animal a, Actor rider) { return a.tamedBy == rider.id || a.owner == rider.id || a.owner != 0 && a.trustOf(rider.id) > 0.6f; }

    public Result mount(Actor rider, Animal a) {
        if (!mountable(a)) return Result.no("You can't ride that.");
        if (rides.containsKey(a.id)) return Result.no("Someone is already riding it.");
        if (of(rider) != null || rider.carryBody != 0 || rider.traversal != null || rider.climb != null || rider.cs.busy()) return Result.no("You can't mount now.");
        if (rider.distTo(a.x, a.z) > 2.6f) return Result.no("Move closer to it.");
        if (a.goal == Animal.Goal.FLEE && !tamedFor(a, rider) && a.fear > 0.6f) return Result.no("It shies away from you.");
        var f = rpg.render.AnimalModels.of(a.species, a.variant);
        float vs = rpg.render.AnimalModels.PX * a.scale();
        MountController body = new MountController(f.bl * vs * 0.36f, a.radius * 0.95f, a.height * 0.95f);
        body.x = a.x; body.y = a.y; body.z = a.z; body.yaw = a.yaw; body.step = stats(a).step;
        Ride r = new Ride(a, rider, body, g.time);
        r.wildYaw = a.yaw; r.wildUntil = g.time + 0.8;
        rides.put(a.id, r);
        a.rider = rider.id; rider.riding = a.id;
        rider.cs.cancel(); rider.cs.blocking = false;
        g.interact.cancel(rider, "mounting");
        if (g.ai != null && a.owner != 0 && a.owner != rider.id && !(a.trustOf(rider.id) > 0.6f)) {
            // someone else's horse taken: that is theft where it is seen
            g.law.crime(rider, "THEFT", a.owner, 0, null, 0);
        }
        String state = tamedFor(a, rider) ? a.saddled ? "saddled" : "tame, unsaddled" : "wild";
        g.state.commit("Mounted", rider.id, a.id, DevMap.jurisdiction(a.x, a.z), 0, a.kind() + " (" + state + ")");
        if (rider == g.player) {
            if (!tamedFor(a, rider)) g.chat.say("The " + a.kind().toLowerCase() + " has never been ridden. Hold on" + (a.saddled ? " - the saddle helps." : ". A saddle on it first would make this far easier."));
            else if (!a.saddled) g.chat.say("Without a saddle you can't steer it.");
        }
        return Result.ok();
    }

    /**
     * (0.3.3, gamefixes: a fall in the saddle ragdolled the rider, then snapped them back onto the horse) a mount that
     * drops this far goes down, and its rider with it: the two part company in the air, both in physics' hands.
     */
    public static final float UNSEAT_DROP = 4.5f;
    /** A landing from at least this high is a hard one (the mount stumbles and is hurt; the rider is shaken). */
    public static final float HARD_DROP = 2.6f;
    /** Mounts sent down by a fall, waiting to land: the height they fell from (paid as fall damage when they meet the ground). */
    private final Map<Long, Float> downed = new HashMap<>();

    /**
     * Rider and mount parted where they are, both off their feet: the rider's body takes the saddle's momentum, the mount
     * is knocked down with its own. Neither is moved first - the ragdolls start from the poses they were in.
     */
    public void unseat(Ride r, float vy) {
        Animal a = r.mount; Actor rider = r.rider;
        rides.remove(a.id);
        a.rider = 0; rider.riding = 0;
        float fx = (float) -Math.sin(a.yaw), fz = (float) -Math.cos(a.yaw);
        float speed = r.flying ? r.airspeed : r.speed;
        rider.vx = fx * speed; rider.vz = fz * speed;
        if (g.falling != null) g.falling.carry(rider, Float.isFinite(r.fallTop) ? rider.y + (r.fallTop - a.y) : rider.y);
        g.reactions.falling(rider, Math.min(-1, vy));
        float mass = Math.max(0.6f, a.species.w * a.species.w * a.species.h * 420 * a.scale() * a.scale() * a.scale());
        a.vx = fx * speed; a.vz = fz * speed; a.rearing = false; a.flying = false;
        release(a, a.vx, vy, a.vz, accelOf(a));
        release(rider, rider.vx, vy, rider.vz, 80);
        if (g.animals != null && a.alive && a.ragdoll == null) {
            g.animals.makeRagdoll(a, fx * speed * mass * 0.5f, vy * mass * 0.5f, fz * speed * mass * 0.5f, true);
            if (a.ragdoll != null) { a.cs.cancel(); downed.put(a.id, Float.isFinite(r.fallTop) ? r.fallTop : a.y); }
        }
        g.state.commit("Dismounted", rider.id, a.id, DevMap.jurisdiction(a.x, a.z), 0, "fell together");
        if (rider == g.player) g.chat.add("You and the " + a.kind().toLowerCase() + " go down together!", rpg.Chat.Kind.WARN);
    }

    /** A downed mount meeting the ground pays for the height it fell from. */
    public void landings() {
        if (downed.isEmpty()) return;
        for (var it = downed.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            Animal a = g.animals == null ? null : g.animals.byId(e.getKey());
            if (a == null || !a.alive || a.ragdoll == null) { it.remove(); continue; }
            float[] t = a.ragdoll.trunkAt(g);
            if (t == null) continue;
            float ground = g.vox.groundY(t[0], t[1] + 0.5f, t[2]);
            if (!Float.isFinite(ground) || t[1] - ground > a.height * 0.75f) continue;
            it.remove();
            int harm = rpg.combat.Falling.damage(e.getValue() - ground);
            if (harm > 0) g.combat.environmentalDamage(a, harm, "fall");
        }
    }

    /**
     * (0.3.4, gamefixes: a mount left in mid-air hung there until it was ridden again) the ride poses both bodies - the
     * physics takes their places from the saddle - and that pose stays until it is cleared. Parted, each body is handed
     * back to the physics with the way it was going, so a mount let go of mid-jump falls and lands like anything else.
     */
    private void release(Actor body, float vx, float vy, float vz, float accel) {
        if (body.body == 0) return;
        g.game.commands().moverPlace(body.body, body.x, body.y, body.z, vx, vy, vz);
        g.game.commands().moverInput(body.body, 0, 0, 0, 0, accel, 0);
    }
    private static float accelOf(Animal a) { return a.species.plan == Species.Plan.BIRD ? 18 : 10; }

    /** Off: down beside it (thrown: flung off and knocked down). Off in mid-air, the rider drops from the saddle's height. */
    public void dismount(Actor rider, boolean thrown) {
        Ride r = of(rider);
        if (r == null) return;
        rides.remove(r.mount.id);
        r.mount.rider = 0; rider.riding = 0;
        Animal a = r.mount;
        a.flying = false;
        boolean aloft = !r.body.grounded;
        float fwdX = (float) -Math.sin(a.yaw) * r.speed, fwdZ = (float) -Math.cos(a.yaw) * r.speed;
        if (aloft) { a.vx = fwdX; a.vz = fwdZ; a.grounded = false; }
        release(a, aloft ? fwdX : 0, aloft ? r.body.vy : 0, aloft ? fwdZ : 0, accelOf(a));
        a.nextThink = 0;
        // (0.3.3) already off their feet (a blow or a fall took them): they part where they are, not set down beside it
        if (rider.reacting) {
            if (!aloft) a.vx = a.vz = 0;
            g.state.commit("Dismounted", rider.id, a.id, DevMap.jurisdiction(a.x, a.z), 0, "knocked off");
            return;
        }
        // down on its left side, where there is room, else its right, else behind
        float rx = (float) Math.cos(a.yaw), rz = (float) -Math.sin(a.yaw), fx = (float) -Math.sin(a.yaw), fz = (float) -Math.cos(a.yaw);
        float[][] spots = {{a.x - rx * (a.radius + 0.6f), a.z - rz * (a.radius + 0.6f)}, {a.x + rx * (a.radius + 0.6f), a.z + rz * (a.radius + 0.6f)}, {a.x - fx * 1.6f, a.z - fz * 1.6f}};
        float[] at = spots[0];
        for (float[] s : spots) if (g.traversal.free(rider, s[0], g.vox.groundY(s[0], a.y + 1.5f, s[1]), s[1])) { at = s; break; }
        float y = g.vox.groundY(at[0], a.y + 1.5f, at[1]);
        if (!Float.isFinite(y)) y = a.y;
        if (aloft) {
            // stepping off in the air: beside the saddle, at its height, falling with the mount's way (a fall is a fall)
            float sy = Math.max(y, rider.y);
            float top = Float.isFinite(r.fallTop) ? Math.max(sy, r.fallTop + (rider.y - a.y)) : sy;
            g.teleport(rider, at[0], sy, at[1]);
            if (g.falling != null) g.falling.carry(rider, top);
            rider.vx = fwdX; rider.vz = fwdZ; rider.grounded = false;
            release(rider, fwdX, r.body.vy, fwdZ, 80);
        } else {
            g.teleport(rider, at[0], y, at[1]);
            a.vx = a.vz = 0;
            release(rider, 0, 0, 0, 80);
        }
        rider.yaw = a.yaw;
        if (thrown) {
            g.reactions.knockDown(rider, -fx, -fz, 120, rider.x, rider.chestY(), rider.z);
            if (rider == g.player) g.chat.add("The " + a.kind().toLowerCase() + " throws you off!", rpg.Chat.Kind.WARN);
        }
        g.state.commit("Dismounted", rider.id, a.id, DevMap.jurisdiction(a.x, a.z), 0, thrown ? "thrown" : "stepped down");
    }

    /** The player's keys as a mount's controls. */
    public Controls playerControls() {
        var in = g.controller.in;
        return new Controls(in.forward, in.strafe, in.sprint, in.sneak, in.jumpHeld, in.interact);
    }

    public void update(float dt) {
        if (rides.isEmpty() || dt <= 0) return;
        for (Ride r : new java.util.ArrayList<>(rides.values())) {
            Animal a = r.mount; Actor rider = r.rider;
            if (!a.alive || !rider.alive || !a.loaded || rider.reacting || a.ragdoll != null) { dismount(rider, false); continue; }
            Controls c = rider == g.player ? playerControls() : new Controls(0, 0, false, false, false, false);
            if (c.dismount() && !r.flying) { dismount(rider, false); continue; }
            MountStats st = stats(a);
            boolean tamed = tamedFor(a, rider), steerable = tamed && a.saddled;
            float wantYaw = r.body.yaw, want = 0;
            if (!tamed) {
                // wild: bucking and bolting as it pleases; every second held on is taming that the horse remembers
                if (g.time >= r.wildUntil) {
                    long h = (long) (g.time * 1000) * 31 + a.id;
                    r.wildYaw = r.body.yaw + ((h % 200) / 100f - 1f) * 1.6f;
                    r.wildSpeed = (h >> 3) % 3 == 0 ? 0 : (h >> 3) % 3 == 1 ? st.canter : st.trot;
                    r.wildUntil = g.time + 0.6 + (h % 7) * 0.2;
                }
                // (0.3.1) a saddle on its back steadies it a great deal: it fights less, and is won over much faster
                boolean saddle = a.saddled;
                wantYaw = r.wildYaw; want = r.wildSpeed * (1 - a.tame * 0.7f) * (saddle ? 0.6f : 1f);
                int agility = rider.skills.level(Skill.AGILITY);
                float trust = a.trustOf(rider.id);
                float rate = TAME_RATE * (1 + agility / 40f) * (0.7f + trust) * (saddle ? SADDLE_TAMING : 1f);
                a.tame = Math.min(1, a.tame + rate * dt);
                // a buck: the harder it is still fighting, the likelier it throws its rider
                if (g.time - r.buckAt > (saddle ? 1.8 : 1.2)) {
                    r.buckAt = g.time;
                    a.rearing = true; a.goalSince = g.time;
                    if ((((long) (g.time * 977) + a.id) % 1000) / 1000f < throwChance(a, agility, trust)) { a.rearing = false; dismount(rider, true); continue; }
                } else if (g.time - r.buckAt > 0.5) a.rearing = false;
                if (a.tame >= 1) {
                    a.tamedBy = rider.id; a.rearing = false;
                    if (a.owner == 0) { a.owner = rider.id; a.home = null; }
                    a.trust.merge(rider.id, 0.7f, (x, y) -> Math.max(x, y));
                    var e = g.state.commit("MountTamed", rider.id, a.id, DevMap.jurisdiction(a.x, a.z), 0, a.kind());
                    g.prog.award(rider, Skill.AGILITY, 40, "tamed a " + a.kind().toLowerCase(), e.id());
                    if (rider == g.player) g.chat.say("The " + a.kind().toLowerCase() + " settles under you. It's yours now." + (a.saddled ? " Steer with the strafe keys." : " It needs a saddle to be steered."));
                }
            } else if (steerable) {
                // reins: the strafe keys turn it (less sharply the faster it goes); the gait follows the keys
                float turn = st.turn * (1 - 0.45f * Math.min(1, Math.abs(r.speed) / Math.max(1, st.gallop)));
                wantYaw = r.body.yaw - c.steer() * turn * dt;
                boolean gallop = c.sprint() && a.stamina > 2;
                if (c.throttle() > 0.2f) want = c.slow() ? st.walk : gallop ? st.gallop : st.canter;
                else if (c.throttle() < -0.2f) want = Math.abs(r.speed) < 0.4f || r.speed < 0 ? -st.walk * 0.8f : 0;
                if (gallop && c.throttle() > 0.2f && r.speed > st.canter) { a.stamina = Math.max(0, a.stamina - 6 * dt); a.cs.regenDelay = 0.8f; }
                // the jump: charge over ten ticks to a peak, sagging toward 80% held longer; released, it goes
                if (c.jumpHeld()) {
                    r.chargeTicks += dt * 60;
                    r.charge = r.chargeTicks <= CHARGE_PEAK_TICKS ? r.chargeTicks / CHARGE_PEAK_TICKS : CHARGE_FLOOR + (1 - CHARGE_FLOOR) * (float) Math.exp(-(r.chargeTicks - CHARGE_PEAK_TICKS) / 12f);
                } else if (r.jumpWas) {
                    if (r.body.grounded && !r.flying && !r.gliding) jump(r, st);
                    r.chargeTicks = 0;
                } else { r.chargeTicks = 0; r.charge = 0; }
            } else {
                // tame, no saddle: it carries its rider, standing or ambling, deaf to the reins
                want = 0;
            }
            if (r.flying) fly(r, st, c, dt);
            else gallop(r, st, c, wantYaw, want, dt);
            r.jumpWas = c.jumpHeld();
            // (0.3.3) the fall: measured from where it left the ground (a flight or a glide is not a fall)
            if (r.flying || r.gliding || r.body.grounded) {
                float drop = Float.isFinite(r.fallTop) ? r.fallTop - r.body.y : 0;
                if (r.body.grounded && !r.flying && drop >= HARD_DROP) hardLanding(r, drop);
                r.fallTop = r.body.grounded || r.flying ? r.body.y : Math.min(r.fallTop, r.body.y);
            } else {
                if (!Float.isFinite(r.fallTop)) r.fallTop = r.body.y;
                r.fallTop = Math.max(r.fallTop, r.body.y);
                if (r.fallTop - r.body.y >= UNSEAT_DROP && r.body.vy < 0) { place(r); unseat(r, r.body.vy); continue; }
            }
            place(r);
        }
    }

    /** Down from a height that does not throw them: the mount stumbles and is hurt a little, the rider is shaken in the saddle. */
    private void hardLanding(Ride r, float drop) {
        Animal a = r.mount;
        r.speed *= 0.35f;
        r.rider.cs.staggered = Math.max(r.rider.cs.staggered, 0.25f + 0.1f * drop);
        int harm = rpg.combat.Falling.damage(drop + 2.5f);
        if (harm > 0) g.combat.environmentalDamage(a, harm, "fall");
        g.state.commit("MountLanded", r.rider.id, a.id, DevMap.jurisdiction(a.x, a.z), 0, String.format("a hard landing from %.1f m", drop));
        if (g.sfx != null) g.sfx.play("animal.horse", a.x, a.y + 1, a.z, 0.6f);
    }

    /** A jump: the horse's strength times the rider's timing. A Pegasus at a gallop with a strong jump takes to the air. */
    private void jump(Ride r, MountStats st) {
        // (0.3.3, gamefixes: mounts leapt onto anything) the best jump clears two blocks, no more
        float height = Math.min(MAX_JUMP, st.jump * Math.max(0.25f, r.charge));
        r.body.vy = (float) Math.sqrt(2 * r.body.gravity * height);
        r.body.grounded = false;
        Animal a = r.mount;
        a.rearing = false;
        var e = g.state.commit("MountJumped", r.rider.id, a.id, DevMap.jurisdiction(a.x, a.z), 0, String.format("charge %.2f, %.2f m", r.charge, height));
        if (st.flies && r.speed > st.canter && r.charge > 0.7f) {
            r.flying = true; r.pitch = 0.35f; r.airspeed = Math.max(r.speed, STALL + 2); r.bank = 0;
            g.state.commit("MountTookFlight", r.rider.id, a.id, DevMap.jurisdiction(a.x, a.z), 0, "from a gallop at " + String.format("%.1f m/s", r.speed));
            if (r.rider == g.player) g.chat.say("The pegasus spreads its wings and takes to the air.");
        }
        if (g.sfx != null) g.sfx.play("animal.horse", a.x, a.y + 1, a.z, 0.5f);
        g.prog.award(r.rider, Skill.AGILITY, 2, "mounted jump", e.id());
    }

    /** On the ground: speed eased toward the wish, the body moved by the controller (stepping, stopping at walls). */
    private void gallop(Ride r, MountStats st, Controls c, float wantYaw, float want, float dt) {
        // (0.3.3, gamefixes: Pegasi should glide) a Pegasus off the ground with the jump key held spreads its wings and
        // glides: its fall is held to a slow sink, it keeps its way (bleeding toward a canter's pace) and the reins
        // still turn it. Let go, it folds them and drops. A glide is not a fall: nothing is paid for it on landing.
        boolean glide = st.flies && !r.body.grounded && c.jumpHeld() && r.body.vy < 0.5f && tamedFor(r.mount, r.rider);
        if (glide && !r.gliding) g.state.commit("MountGlided", r.rider.id, r.mount.id, DevMap.jurisdiction(r.mount.x, r.mount.z), 0, String.format("from %.1f m/s", r.speed));
        r.gliding = glide;
        float accel = want > r.speed ? (r.speed < st.canter ? 6f : 3.2f) : 9f;
        if (!r.body.grounded) accel *= 0.1f;
        if (glide) { want = Math.max(st.canter, Math.min(r.speed, st.gallop)); accel = 1.2f; r.chargeTicks = 0; r.charge = 0; }
        r.speed += Math.max(-accel * dt, Math.min(accel * dt, want - r.speed));
        float fx = (float) -Math.sin(r.body.yaw), fz = (float) -Math.cos(r.body.yaw);
        if (glide) r.body.vy = Math.max(r.body.vy, -GLIDE_SINK);
        r.body.move(g.vox, fx * r.speed * dt, fz * r.speed * dt, wantYaw, dt);
        if (glide) { r.body.vy = Math.max(r.body.vy, -GLIDE_SINK); if (r.mount.vigor > 0) r.mount.vigor = Math.max(0, r.mount.vigor - 1.5f * dt); }
        // run into something at speed: checked hard
        if (r.body.blocked && Math.abs(r.speed) > st.trot) r.speed *= 0.3f;
        else if (r.body.blocked) r.speed = 0;
        Animal a = r.mount;
        a.stamina = Math.min(a.maxStamina(), a.stamina + (Math.abs(r.speed) <= st.canter ? 4 : 0) * dt);
        if (st.flies) a.vigor = Math.min(st.vigor, a.vigor + 10 * dt);
    }

    /**
     * Flight: momentum. Pitched down it gathers speed as it falls; pitched up it climbs and pays for it in speed; level
     * it glides, slowly losing both. The strafe keys bank it, and the bank turns it - tighter when slow. The jump key
     * surges the wings (vigor). Below stall speed the wings no longer hold it and it sinks. Meeting the ground slowly
     * enough it lands and gallops on; too fast and steep, it lands hard.
     */
    private void fly(Ride r, MountStats st, Controls c, float dt) {
        Animal a = r.mount;
        float wantPitch = c.throttle() > 0.2f ? -0.75f : c.throttle() < -0.2f ? 0.55f : c.slow() ? 0.35f : -0.06f;
        r.pitch += Math.max(-1.6f * dt, Math.min(1.6f * dt, wantPitch - r.pitch));
        float wantBank = -c.steer() * 0.75f;
        r.bank += Math.max(-2.2f * dt, Math.min(2.2f * dt, wantBank - r.bank));
        // the surge: a wingbeat on the press of the jump key
        if (c.jumpHeld() && !r.jumpWas && a.vigor >= SURGE_COST) {
            a.vigor -= SURGE_COST; r.airspeed += SURGE_PUSH; r.body.vy = Math.max(r.body.vy, 0) + 2.5f;
            g.state.commit("MountSurged", r.rider.id, a.id, null, 0, String.format("vigor %.0f", a.vigor));
        }
        // energy: gravity along the flight path, drag growing with the square of speed
        float gacc = 9.8f;
        r.airspeed += (-gacc * (float) Math.sin(r.pitch) - 0.0045f * r.airspeed * r.airspeed - (c.slow() ? 3f : 0)) * dt;
        r.airspeed = Math.max(0, Math.min(38, r.airspeed));
        // the banked turn: tan(bank) g / v, clamped
        float turnRate = (float) (Math.tan(r.bank) * gacc / Math.max(4, r.airspeed));
        float newYaw = r.body.yaw + Math.max(-1.8f, Math.min(1.8f, turnRate)) * dt;
        // lift: the wings hold it up above stall; below, it sinks harder the slower it goes
        float sink = r.airspeed < STALL ? (STALL - r.airspeed) / STALL * 7f : 0;
        r.body.vy += (-sink - r.body.vy) * Math.min(1, dt * 2);
        float horiz = r.airspeed * (float) Math.cos(r.pitch), vert = r.airspeed * (float) Math.sin(r.pitch) + r.body.vy;
        float fx = (float) -Math.sin(newYaw), fz = (float) -Math.cos(newYaw);
        float nx = r.body.x + fx * horiz * dt, ny = r.body.y + vert * dt, nz = r.body.z + fz * horiz * dt;
        float floor = r.body.ground(g.vox, nx, Math.max(ny, r.body.y) + 0.5f, nz, newYaw);
        if (ny > DevMap.SY - 6) ny = DevMap.SY - 6;
        if (ny <= floor + 0.05f) {
            // the ground: a landing - soft enough, it gallops on; too fast and steep, a hard landing that checks it
            boolean hard = r.airspeed > 16 && r.pitch < -0.35f;
            r.flying = false; r.body.y = floor; r.body.vy = 0; r.body.grounded = true;
            r.speed = Math.min(hard ? st.trot : st.canter, horiz);
            if (r.body.fits(g.vox, nx, floor, nz, newYaw)) { r.body.x = nx; r.body.z = nz; r.body.yaw = newYaw; }
            g.state.commit("MountLanded", r.rider.id, a.id, DevMap.jurisdiction(a.x, a.z), 0, hard ? "a hard landing" : "landed");
            if (hard) { r.rider.cs.staggered = Math.max(r.rider.cs.staggered, 0.4f); if (r.rider == g.player) g.chat.add("A hard landing.", rpg.Chat.Kind.WARN); }
            return;
        }
        if (r.body.fits(g.vox, nx, ny, nz, newYaw)) { r.body.x = nx; r.body.y = ny; r.body.z = nz; r.body.yaw = newYaw; }
        else { r.airspeed *= 0.4f; r.body.vy = -2; }       // struck something: it loses its way
        r.body.grounded = false;
        r.speed = horiz;
        // gliding rests the wings a little
        a.vigor = Math.min(st.vigor, a.vigor + (r.pitch < 0 ? 2.5f : 0.6f) * dt);
    }

    /** The mount where its controller put it (posed, its own AI held), the rider in the saddle. */
    private void place(Ride r) {
        Animal a = r.mount; Actor rider = r.rider;
        MountController b = r.body;
        float fx = (float) -Math.sin(b.yaw), fz = (float) -Math.cos(b.yaw);
        a.x = b.x; a.y = b.y; a.z = b.z; a.yaw = b.yaw;
        a.vx = fx * r.speed; a.vz = fz * r.speed; a.grounded = b.grounded;
        a.flying = r.flying || r.gliding; a.flightPitch = r.flying ? r.pitch : r.gliding ? -0.12f : 0;
        a.flightBank = r.flying ? r.bank : r.gliding ? r.bank * 0.9f + (float) Math.max(-0.5, Math.min(0.5, Actor.wrap(r.body.yaw - a.yaw) * 6)) * 0.1f : 0;
        a.wantYaw = Float.NaN;
        if (a.body != 0) { g.game.commands().moverPlace(a.body, a.x, a.y, a.z, 0, 0, 0); g.game.commands().moverInput(a.body, 0, 0, 0, 0, 80, MOVER_POSED); }
        // the saddle: on the back, a little ahead of the middle; in flight the body pitches, and the rider with it
        float saddleY = a.y + a.height * (a.species == Species.PEGASUS ? 0.74f : 0.76f), ahead = -0.08f;
        float rideY = saddleY - rider.height * 0.37f;
        rider.x = a.x + fx * ahead; rider.z = a.z + fz * ahead; rider.y = rideY;
        rider.yaw = a.yaw; rider.vx = 0; rider.vz = 0; rider.grounded = false;
        rider.rideSpeed = r.flying ? r.airspeed : Math.abs(r.speed); rider.rideGait = a.gait;
        if (rider.body != 0) { g.game.commands().moverPlace(rider.body, rider.x, rider.y, rider.z, 0, 0, 0); g.game.commands().moverInput(rider.body, 0, 0, 0, 0, 80, MOVER_POSED); }
    }
}
