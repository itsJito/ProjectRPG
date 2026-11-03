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
        Ride(Animal mount, Actor rider, MountController body, double since) { this.mount = mount; this.rider = rider; this.body = body; this.since = since; }
    }

    /** Charge: ticks to the peak, and where it sags to. */
    public static final float CHARGE_PEAK_TICKS = 10, CHARGE_FLOOR = 0.8f;
    /** Flight: below this airspeed the wings no longer hold it up (m/s); a surge's cost in vigor and its push. */
    public static final float STALL = 6.5f, SURGE_COST = 18, SURGE_PUSH = 4.5f;

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
            if (!tamedFor(a, rider)) g.chat.say("The " + a.kind().toLowerCase() + " has never been ridden. Hold on.");
            else if (!a.saddled) g.chat.say("Without a saddle you can't steer it.");
        }
        return Result.ok();
    }

    /** Off: down beside it (thrown: flung off and knocked down). */
    public void dismount(Actor rider, boolean thrown) {
        Ride r = of(rider);
        if (r == null) return;
        rides.remove(r.mount.id);
        r.mount.rider = 0; rider.riding = 0;
        Animal a = r.mount;
        // down on its left side, where there is room, else its right, else behind
        float rx = (float) Math.cos(a.yaw), rz = (float) -Math.sin(a.yaw), fx = (float) -Math.sin(a.yaw), fz = (float) -Math.cos(a.yaw);
        float[][] spots = {{a.x - rx * (a.radius + 0.6f), a.z - rz * (a.radius + 0.6f)}, {a.x + rx * (a.radius + 0.6f), a.z + rz * (a.radius + 0.6f)}, {a.x - fx * 1.6f, a.z - fz * 1.6f}};
        float[] at = spots[0];
        for (float[] s : spots) if (g.traversal.free(rider, s[0], g.vox.groundY(s[0], a.y + 1.5f, s[1]), s[1])) { at = s; break; }
        float y = g.vox.groundY(at[0], a.y + 1.5f, at[1]);
        if (!Float.isFinite(y)) y = a.y;
        g.teleport(rider, at[0], y, at[1]);
        rider.yaw = a.yaw;
        a.vx = a.vz = 0;
        if (a.body != 0) g.game.commands().moverPlace(a.body, a.x, a.y + 0.05f, a.z, 0, 0, 0);
        a.nextThink = 0;
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
                wantYaw = r.wildYaw; want = r.wildSpeed * (1 - a.tame * 0.7f);
                int agility = rider.skills.level(Skill.AGILITY);
                float rate = 0.035f * (1 + agility / 40f) * (0.6f + a.trustOf(rider.id));
                a.tame = Math.min(1, a.tame + rate * dt);
                // a buck: the harder it is still fighting, the likelier it throws its rider
                if (g.time - r.buckAt > 1.2) {
                    r.buckAt = g.time;
                    a.rearing = true; a.goalSince = g.time;
                    float throwChance = 0.22f * (1 - a.tame) * (1 - Math.min(0.8f, agility / 99f));
                    if ((((long) (g.time * 977) + a.id) % 1000) / 1000f < throwChance) { a.rearing = false; dismount(rider, true); continue; }
                } else if (g.time - r.buckAt > 0.5) a.rearing = false;
                if (a.tame >= 1) {
                    a.tamedBy = rider.id; a.rearing = false;
                    if (a.owner == 0) { a.owner = rider.id; a.home = null; }
                    a.trust.merge(rider.id, 0.7f, (x, y) -> Math.max(x, y));
                    var e = g.state.commit("MountTamed", rider.id, a.id, DevMap.jurisdiction(a.x, a.z), 0, a.kind());
                    g.prog.award(rider, Skill.AGILITY, 40, "tamed a " + a.kind().toLowerCase(), e.id());
                    if (rider == g.player) g.chat.say("The " + a.kind().toLowerCase() + " settles under you. It's yours now." + (a.saddled ? "" : " It needs a saddle to be steered."));
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
                    if (r.body.grounded && !r.flying) jump(r, st);
                    r.chargeTicks = 0;
                } else { r.chargeTicks = 0; r.charge = 0; }
            } else {
                // tame, no saddle: it carries its rider, standing or ambling, deaf to the reins
                want = 0;
            }
            r.jumpWas = c.jumpHeld();
            if (r.flying) fly(r, st, c, dt);
            else gallop(r, st, wantYaw, want, dt);
            place(r);
        }
    }

    /** A jump: the horse's strength times the rider's timing. A Pegasus at a gallop with a strong jump takes to the air. */
    private void jump(Ride r, MountStats st) {
        float height = st.jump * Math.max(0.25f, r.charge);
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
    private void gallop(Ride r, MountStats st, float wantYaw, float want, float dt) {
        float accel = want > r.speed ? (r.speed < st.canter ? 6f : 3.2f) : 9f;
        if (!r.body.grounded) accel *= 0.1f;
        r.speed += Math.max(-accel * dt, Math.min(accel * dt, want - r.speed));
        float fx = (float) -Math.sin(r.body.yaw), fz = (float) -Math.cos(r.body.yaw);
        r.body.move(g.vox, fx * r.speed * dt, fz * r.speed * dt, wantYaw, dt);
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
        a.flying = r.flying; a.flightPitch = r.pitch; a.flightBank = r.bank;
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
