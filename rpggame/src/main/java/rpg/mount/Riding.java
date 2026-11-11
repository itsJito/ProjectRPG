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
        /** (0.3.6) When the brush it is pushing through next rustles. */
        double rustleAt;
        /** (0.3.9) Afloat (swimming with its rider); when it last splashed. */
        public boolean afloat;
        double splashAt;
        /**
         * (0.3.9) The climb into the saddle: how far along (0..1, 1 = up), how long it takes, the side it is made from (-1 the
         * mount's left, +1 its right), the spot beside the saddle it starts from and the ground there; whether an untamed
         * mount has had its chance to shy yet.
         */
        public float mountT = 1, mountDur = 1;
        /** (0.3.9) How far the rider is turned in the saddle (rad, + to the left). */
        public float twist;
        public int side = -1;
        float sideX, sideZ, groundY;
        boolean shyChecked;
        Ride(Animal mount, Actor rider, MountController body, double since) { this.mount = mount; this.rider = rider; this.body = body; this.since = since; }
        /** Still climbing up into the saddle. */
        public boolean mounting() { return mountT < 1; }
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
    /**
     * (0.3.6) The saddle, in the mount's own pixels: its seat's top over the barrel's, its middle ahead of the body's (a
     * share of the body's length), how far its stirrups hang outside the barrel, below its top and ahead of the seat; and
     * the rider's hip joint above the seat (the rider's pixels). Gfx draws the saddle from the same numbers.
     */
    public static final float SADDLE_SEAT = 0.8f, SADDLE_AHEAD = 0.06f, STIRRUP_OUT = 2.6f, STIRRUP_DROP = 6.2f, STIRRUP_AHEAD = 1f, SEAT_HIP = 1.2f;
    /** A glide's sink (m/s): wings out, a Pegasus comes down about a metre for every four or five it goes. */
    public static final float GLIDE_SINK = 2.1f;
    /**
     * (0.3.9, gamefixes: a mount ridden into water sank to the bottom, the fall threw both, and the rider went through the
     * map) water is swum, not fallen through: a mount in water deeper than this share of its height floats with the water
     * at that line (its back and its rider above it), paddles at no more than its swimming pace, and walks out where the
     * bottom rises to meet its hooves. A Pegasus coming down onto water lights on it and swims.
     */
    public static final float MOUNT_FLOAT = rpg.Tuning.f("mounts.swimFloat", 0.58f), MOUNT_SWIM_FLOOR = rpg.Tuning.f("mounts.swimPaceFloor", 1.8f);
    /** (0.3.6) Flight: the climb and dive pitch the keys ask for (rad), the full bank (rad), the turn's speed floor and limit (rad/s). */
    public static final float FLY_CLIMB = 0.55f, FLY_DIVE = 0.75f, FLY_BANK = 0.7f, FLY_TURN_FLOOR = 6f, FLY_TURN_MAX = 1.35f;

    private final Rpg g;
    public final Map<Long, Ride> rides = new HashMap<>();
    public Riding(Rpg g) { this.g = g; }

    /** (0.3.9) Any grown animal of a riding kind (Species.mount: the horse, the Pegasus, the Dire Wolf). */
    public static boolean mountable(Animal a) { return a.alive && !a.baby && a.species.mount; }
    public Ride of(Actor rider) { for (Ride r : rides.values()) if (r.rider == rider) return r; return null; }
    public Ride ridden(Animal a) { return rides.get(a.id); }

    /** The stats a mount carries (rolled the first time they are asked for, then kept with the animal). */
    public static MountStats stats(Animal a) {
        if (a.mountStats == null) a.mountStats = switch (a.species) { case PEGASUS -> MountStats.pegasus(a.id); case DIRE_WOLF -> MountStats.direWolf(a.id); default -> MountStats.horse(a.id); };
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
        // (0.3.9, gamefixes) up by the stirrup from beside the saddle - its left side if there is room there, else its
        // right - over a second or so (quicker with Agility, slower with no saddle to climb by)
        float[] spot = mountSpot(rider, a);
        if (spot == null) return Result.no("There's no room beside it to climb up.");
        r.side = (int) spot[3]; r.sideX = spot[0]; r.groundY = spot[1]; r.sideZ = spot[2];
        r.mountT = 0; r.mountDur = mountTime(rider, a);
        rides.put(a.id, r);
        a.rider = rider.id; rider.riding = a.id;
        rider.mountPhase = 0; rider.mountNear = r.side < 0 ? 1 : 0;
        rider.cs.cancel(); rider.cs.blocking = false;
        g.interact.cancel(rider, "mounting");
        g.state.commit("MountingStarted", rider.id, a.id, DevMap.jurisdiction(a.x, a.z), 0, String.format("from its %s side, %.2f s", r.side < 0 ? "left" : "right", r.mountDur));
        if (g.sfx != null) g.sfx.play("cloth", a.x, a.y + 1, a.z, 0.5f);
        if (g.ai != null && a.owner != 0 && a.owner != rider.id && !(a.trustOf(rider.id) > 0.6f)) {
            // someone else's horse taken: that is theft where it is seen
            g.law.crime(rider, "THEFT", a.owner, 0, null, 0);
        }
        place(r);
        return Result.ok();
    }

    /** (0.3.9) The climb's length, s: about 1.3 s, a tenth less for every 25 Agility; under half a second more bareback. */
    public static float mountTime(Actor rider, Animal a) {
        float base = rpg.Tuning.f("mounts.mountSeconds", 1.3f);
        return (base - base * 0.1f * Math.min(99, rider.skills.level(Skill.AGILITY)) / 25f) + (a.saddled ? 0 : rpg.Tuning.f("mounts.barebackExtra", 0.45f));
    }

    /** (0.3.9) Where to stand to climb up: beside the saddle on the mount's left, else its right ({x, ground, z, side}); null with no room either side. */
    private float[] mountSpot(Actor rider, Animal a) {
        float rx = (float) Math.cos(a.yaw), rz = (float) -Math.sin(a.yaw);
        for (int side : new int[]{-1, 1}) {
            float out = a.radius + 0.42f, x = a.x + rx * side * out, z = a.z + rz * side * out;
            float y = g.vox.groundY(x, a.y + 1.2f, z);
            if (!Float.isFinite(y) || Math.abs(y - a.y) > 1.1f) continue;
            if (g.traversal != null && !g.traversal.free(rider, x, y, z)) continue;
            return new float[]{x, y, z, side};
        }
        return null;
    }

    /** (0.3.9) Up and settled: the ride proper begins. */
    private void mounted(Ride r) {
        Animal a = r.mount; Actor rider = r.rider;
        r.mountT = 1; rider.mountPhase = 1;
        String state = tamedFor(a, rider) ? a.saddled ? "saddled" : "tame, unsaddled" : "wild";
        g.state.commit("Mounted", rider.id, a.id, DevMap.jurisdiction(a.x, a.z), 0, a.kind() + " (" + state + ")");
        if (rider == g.player) {
            if (!tamedFor(a, rider)) g.chat.say("The " + a.kind().toLowerCase() + " has never been ridden. Hold on" + (a.saddled ? " - the saddle helps." : ". A saddle on it first would make this far easier."));
            else if (!a.saddled) g.chat.say("Without a saddle you can't steer it.");
        }
    }

    /** (0.3.9) Straight into the saddle with no climb (a review shot's or a test's set-up, a loaded save). */
    public void seatNow(Actor rider) { Ride r = of(rider); if (r != null && r.mounting()) { r.mountT = 1; place(r); mounted(r); } }

    /**
     * (0.3.9) The climb cut short - a blow, the rider backing off, the mount shying or bolting: they come back down to
     * where they started, on their feet (or left to the blow that took them off).
     */
    public void abortMount(Ride r, String why) {
        Animal a = r.mount; Actor rider = r.rider;
        rides.remove(a.id);
        a.rider = 0; rider.riding = 0; rider.mountPhase = 1;
        release(a, 0, 0, 0, accelOf(a));
        a.nextThink = 0;
        if (!rider.reacting) {
            g.teleport(rider, r.sideX, r.groundY, r.sideZ);
            release(rider, 0, 0, 0, 80);
        }
        g.state.commit("MountingAborted", rider.id, a.id, DevMap.jurisdiction(a.x, a.z), 0, why);
        if (rider == g.player) g.chat.add(why.substring(0, 1).toUpperCase() + why.substring(1) + ".", rpg.Chat.Kind.WARN);
    }

    /** (0.3.9) A moment of the climb: cut short by what interrupts it, else on up; the mount stands for it. */
    private void climb(Ride r, Controls c, float dt) {
        Animal a = r.mount; Actor rider = r.rider;
        if (!a.alive || !rider.alive || a.ragdoll != null) { abortMount(r, "the mount went down"); return; }
        if (rider.reacting || rider.cs.lastHurt > r.since || rider.cs.staggered > 0) { abortMount(r, "knocked from the stirrup"); return; }
        if (rider == g.player && (c.throttle() < -0.5f || Math.abs(c.steer()) > 0.5f && r.mountT < 0.5f)) { abortMount(r, "you step back down"); return; }
        // an untamed one may shy from the weight in the stirrup (less with a saddle on it, and the tamer it is)
        if (!r.shyChecked && r.mountT > 0.3f) {
            r.shyChecked = true;
            if (!tamedFor(a, rider)) {
                float shy = 0.35f * (1 - a.tame) * (a.saddled ? 0.6f : 1f) * (1 - 0.5f * Math.min(1, a.trustOf(rider.id)));
                if ((((long) (g.time * 733) + a.id * 13) % 1000) / 1000f < shy) {
                    abortMount(r, "the " + a.kind().toLowerCase() + " sidesteps and you lose the stirrup");
                    a.rearing = true; a.goalSince = g.time; a.fear = Math.min(1, a.fear + 0.25f);
                    return;
                }
            }
        }
        float was = r.mountT;
        r.mountT = Math.min(1, r.mountT + dt / r.mountDur);
        if (was < 0.55f && r.mountT >= 0.55f && g.sfx != null) g.sfx.play("cloth", a.x, a.y + 1.2f, a.z, 0.55f, 0.85f, 12);
        r.speed = 0;
        place(r);
        if (r.mountT >= 1) mounted(r);
    }

    /**
     * (0.3.3, gamefixes: a fall in the saddle ragdolled the rider, then snapped them back onto the horse) a mount that
     * drops this far goes down, and its rider with it: the two part company in the air, both in physics' hands.
     */
    public static final float UNSEAT_DROP = 4.5f;
    /** A landing from at least this high is a hard one (the mount stumbles and is hurt; the rider is shaken). */
    public static final float HARD_DROP = 2.6f;
    /**
     * (0.3.6, gamefixes: mounts should ragdoll hitting a wall at speed) a horse that runs into a wall at this share of its
     * gallop or more, or a Pegasus that flies into one at this airspeed (m/s) or more, goes down - and its rider over its
     * head. Slower, or a glancing blow along the wall, only checks it. A head-on blow is one that kept less than this share
     * of the way it was going.
     */
    public static final float CRASH_GALLOP = 0.8f, CRASH_AIRSPEED = 13f, HEAD_ON = 0.4f;
    /** (0.3.6) Brush (leaves, a bush) takes this share of the pace off for every cell of it the body is in, to at most BRUSH_MAX. */
    public static final float BRUSH_DRAG = 0.12f, BRUSH_MAX = 0.55f;
    /** Mounts sent down by a fall, waiting to land: the height they fell from (paid as fall damage when they meet the ground). */
    private final Map<Long, Float> downed = new HashMap<>();

    /**
     * Rider and mount parted where they are, both off their feet: the rider's body takes the saddle's momentum, the mount
     * is knocked down with its own. Neither is moved first - the ragdolls start from the poses they were in.
     */
    public void unseat(Ride r, float vy) {
        float speed = r.flying ? r.airspeed : r.speed;
        part(r, speed, speed, vy, vy, "fell together");
        if (r.rider == g.player) g.chat.add("You and the " + r.mount.kind().toLowerCase() + " go down together!", rpg.Chat.Kind.WARN);
    }

    /**
     * Rider and mount parted in the physics' hands: the rider's body thrown along the mount's heading at riderSpeed (and
     * riderVy up), the mount knocked down with mountSpeed along it (negative: back the way it came) - both ragdolls start
     * from the poses they were in.
     */
    private void part(Ride r, float riderSpeed, float mountSpeed, float riderVy, float mountVy, String why) {
        Animal a = r.mount; Actor rider = r.rider;
        rides.remove(a.id);
        a.rider = 0; rider.riding = 0;
        float fx = (float) -Math.sin(a.yaw), fz = (float) -Math.cos(a.yaw);
        rider.vx = fx * riderSpeed; rider.vz = fz * riderSpeed;
        if (g.falling != null) g.falling.carry(rider, Float.isFinite(r.fallTop) ? rider.y + (r.fallTop - a.y) : rider.y);
        g.reactions.falling(rider, Math.min(-1, riderVy));
        float mass = Math.max(0.6f, a.species.w * a.species.w * a.species.h * 420 * a.scale() * a.scale() * a.scale());
        a.vx = fx * mountSpeed; a.vz = fz * mountSpeed; a.rearing = false; a.flying = false;
        release(a, a.vx, mountVy, a.vz, accelOf(a));
        release(rider, rider.vx, riderVy, rider.vz, 80);
        if (g.animals != null && a.alive && a.ragdoll == null) {
            g.animals.makeRagdoll(a, fx * mountSpeed * mass * 0.5f, mountVy * mass * 0.5f, fz * mountSpeed * mass * 0.5f, true);
            if (a.ragdoll != null) { a.cs.cancel(); downed.put(a.id, Float.isFinite(r.fallTop) ? r.fallTop : a.y); }
        }
        g.state.commit("Dismounted", rider.id, a.id, DevMap.jurisdiction(a.x, a.z), 0, why);
    }

    /**
     * (0.3.6, gamefixes) Into a wall at speed: the mount is stopped dead and thrown back off it, hurt by the blow; the rider
     * goes on over its head into the wall and down. How hard each is hurt grows with the speed of it.
     */
    void crash(Ride r, float speed) {
        Animal a = r.mount; Actor rider = r.rider;
        boolean flew = r.flying || r.gliding;
        float vy = flew ? r.body.vy + speed * (float) Math.sin(r.pitch) : 0;
        part(r, speed * 0.55f, -speed * 0.18f, Math.max(1.5f, vy + 2.2f), Math.max(0.5f, vy + 1.2f), String.format("crashed into a wall at %.1f m/s", speed));
        // the blow itself, as hard as a fall from the height that speed is gained in (v^2 / 2g); the rider, thrown clear of
        // the mount's weight, takes less of it (the fall that follows is paid as any fall is)
        float riderSpeed = speed * 0.85f;
        int mountHarm = rpg.combat.Falling.damage(speed * speed / 19.6f), riderHarm = rpg.combat.Falling.damage(riderSpeed * riderSpeed / 19.6f);
        if (mountHarm > 0) g.combat.environmentalDamage(a, mountHarm, "a wall");
        if (riderHarm > 0) g.combat.environmentalDamage(rider, riderHarm, "a wall");
        g.state.commit("MountCrashed", rider.id, a.id, DevMap.jurisdiction(a.x, a.z), 0, String.format("%s into a wall at %.1f m/s (%d, %d harm)", flew ? "flew" : "galloped", speed, mountHarm, riderHarm));
        if (g.sfx != null) { g.sfx.play("body.fall", a.x, a.y + 1, a.z, 1f); if (a.species.hurtSound != null) g.sfx.play(a.species.hurtSound, a.x, a.y + 1, a.z, 0.9f); }
        if (rider == g.player) g.chat.add("The " + a.kind().toLowerCase() + " slams into the wall and you are thrown!", rpg.Chat.Kind.WARN);
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

    /**
     * (0.3.9) A person in the saddle rides toward where their errand takes them (Actor.rideTo; NaN: stand), reining round
     * to it, at a canter (a gallop when it is far and the mount has the wind for it), slowing to a walk to come in.
     */
    public Controls npcControls(Ride r) {
        Actor a = r.rider;
        if (!Float.isFinite(a.rideToX)) return new Controls(0, 0, false, false, false, false);
        float dx = a.rideToX - r.mount.x, dz = a.rideToZ - r.mount.z, d = (float) Math.hypot(dx, dz);
        if (d < 1.2f) return new Controls(0, 0, false, false, false, false);
        float want = (float) Math.atan2(-dx, -dz), off = Actor.wrap(want - r.body.yaw);
        float steer = Math.max(-1, Math.min(1, -off * 2.2f));
        float throttle = Math.abs(off) > 1.6f ? 0.3f : 1;
        return new Controls(throttle, steer, d > 25 && r.mount.stamina > 20, d < 6, false, false);
    }

    /** The player's keys as a mount's controls. */
    public Controls playerControls() {
        var in = g.controller.in;
        return new Controls(in.forward, in.strafe, in.sprint, in.sneak, in.jumpHeld, in.interact);
    }

    /** (0.3.9) The tick the rides were last moved on (they are moved once a tick, whoever asks first: the player's controls or the world). */
    private long movedOn = -1;

    public void update(float dt) {
        if (rides.isEmpty() || dt <= 0 || g.state.tick == movedOn) return;
        movedOn = g.state.tick;
        lastDt = dt;
        for (Ride r : new java.util.ArrayList<>(rides.values())) {
            Animal a = r.mount; Actor rider = r.rider;
            Controls c = rider == g.player ? playerControls() : npcControls(r);
            if (r.mounting()) { climb(r, c, dt); continue; }
            if (!a.alive || !rider.alive || !a.loaded || rider.reacting || a.ragdoll != null) { dismount(rider, false); continue; }
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
                if (c.jumpHeld() && !r.afloat) {
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

    /** (0.3.9) The furthest a rider turns in the saddle to fight or aim (rad); either side, or over the shoulder no further. */
    public static final float TWIST_MAX = 1.25f;
    private float lastDt = 1f / 60;
    private float dt() { return lastDt; }

    /** (0.3.9) Whether the rider's hands are at work (a swing, a draw, a raised guard, an aim, a spell): then they turn to it. */
    static boolean fighting(Actor a) {
        var c = a.cs;
        return c.phase != Actor.Combat.Phase.IDLE || c.draw > 0 || c.aim > 0 || c.charge > 0 || c.blocking || a instanceof rpg.ai.Npc n && n.fightTarget != 0 && !a.sheathed;
    }
    /** (0.3.9) Where the rider means to strike or shoot: the player's aim; a person's foe. */
    private float aimOf(Actor a) {
        if (a == g.player) return a.aimYaw;
        Actor t = a instanceof rpg.ai.Npc n && n.fightTarget != 0 ? g.actor(n.fightTarget) : null;
        return t == null ? a.yaw : (float) Math.atan2(-(t.x - a.x), -(t.z - a.z));
    }

    /** (0.3.9) The irons in the world (the feet stay in them however the rider turns in the saddle): right foot, then left. */
    private void stirrups(Ride r, float seatX, float seatY, float seatZ, float rpx) {
        Animal a = r.mount; Actor rider = r.rider;
        float rx = (float) Math.cos(a.yaw), rz = (float) -Math.sin(a.yaw), bx = (float) Math.sin(a.yaw), bz = (float) Math.cos(a.yaw);
        float sx = rider.stirrupX * rpx, sy = rider.stirrupY * rpx, sz = rider.stirrupZ * rpx;
        rider.mountFeet[0] = seatX + rx * sx + bx * sz; rider.mountFeet[1] = seatY + sy; rider.mountFeet[2] = seatZ + rz * sx + bz * sz;
        rider.mountFeet[3] = seatX - rx * sx + bx * sz; rider.mountFeet[4] = seatY + sy; rider.mountFeet[5] = seatZ - rz * sx + bz * sz;
    }

    private static float smooth(float t) { t = Math.max(0, Math.min(1, t)); return t * t * (3 - 2 * t); }

    /**
     * (0.3.9, gamefixes: mounting) the climb, as the body and limbs follow it. From beside the saddle, facing the mount's
     * flank: the near foot up into its stirrup (0 - 0.2), a push up off it with both hands on the pommel (0.12 - 0.55),
     * the far leg swung high over the croup (0.5 - 0.9) as the body turns to face ahead and settles into the seat (to 1).
     * The seat (x, y, z) is where the rider's body sits when up; the far stirrup and the near are this mount's own.
     */
    private void climbPose(Ride r, float seatX, float seatY, float seatZ, float rpx) {
        Animal a = r.mount; Actor rider = r.rider;
        float t = r.mountT;
        float rx = (float) Math.cos(a.yaw), rz = (float) -Math.sin(a.yaw), bx = (float) Math.sin(a.yaw), bz = (float) Math.cos(a.yaw);
        float lateral = smooth((t - 0.2f) / 0.55f), rise = smooth((t - 0.16f) / 0.5f), settle = smooth((t - 0.55f) / 0.4f);
        float over = 0.07f * rise * (1 - settle);   // a little above the seat at the top of the push, then down into it
        rider.x = r.sideX + (seatX - r.sideX) * lateral;
        rider.z = r.sideZ + (seatZ - r.sideZ) * lateral;
        rider.y = r.groundY + (seatY - r.groundY) * rise + over;
        rider.yaw = a.yaw + r.side * (float) (Math.PI / 2) * (1 - smooth((t - 0.45f) / 0.4f));
        // the stirrups in the world (the seated figure's numbers, from the seat)
        float sxN = r.side * rider.stirrupX * rpx, sy = rider.stirrupY * rpx, sz = rider.stirrupZ * rpx;
        float[] near = {seatX + rx * sxN + bx * sz, seatY + sy, seatZ + rz * sxN + bz * sz};
        float[] far = {seatX - rx * sxN + bx * sz, seatY + sy, seatZ - rz * sxN + bz * sz};
        // the near foot: from the ground beside it, lifted up into the iron
        float[] nearGround = {r.sideX + rx * r.side * 0.08f - bx * 0.12f, r.groundY, r.sideZ + rz * r.side * 0.08f - bz * 0.12f};
        float up = smooth(t / 0.2f), lift = (float) Math.sin(Math.PI * Math.min(1, t / 0.2f)) * 0.12f;
        float[] nf = {nearGround[0] + (near[0] - nearGround[0]) * up, nearGround[1] + (near[1] - nearGround[1]) * up + lift, nearGround[2] + (near[2] - nearGround[2]) * up};
        // the far foot: on the ground, then the push off it, then swung high over the croup and down into its iron
        float[] farGround = {r.sideX + rx * r.side * 0.3f + bx * 0.08f, r.groundY, r.sideZ + rz * r.side * 0.3f + bz * 0.08f};
        float[] push = {rider.x + rx * r.side * 0.25f + bx * 0.25f, rider.y + 0.25f, rider.z + rz * r.side * 0.25f + bz * 0.25f};
        float[] ff;
        if (t < 0.3f) ff = farGround;
        else if (t < 0.5f) { float k = smooth((t - 0.3f) / 0.2f); ff = new float[]{farGround[0] + (push[0] - farGround[0]) * k, farGround[1] + (push[1] - farGround[1]) * k, farGround[2] + (push[2] - farGround[2]) * k}; }
        else {
            float k = smooth((t - 0.5f) / 0.4f), arc = (float) Math.sin(Math.PI * k);
            ff = new float[]{push[0] + (far[0] - push[0]) * k + bx * arc * 0.45f, push[1] + (far[1] - push[1]) * k + arc * 0.7f, push[2] + (far[2] - push[2]) * k + bz * arc * 0.45f};
        }
        int nearK = rider.mountNear, farK = 1 - nearK;
        System.arraycopy(nf, 0, rider.mountFeet, nearK * 3, 3);
        System.arraycopy(ff, 0, rider.mountFeet, farK * 3, 3);
        // the hands on the pommel (the front of the seat) while climbing
        rider.mountHands[0] = seatX - bx * 0.28f; rider.mountHands[1] = seatY + (rpg.actor.Body.LEGS - SEAT_HIP) * rpx + 0.32f; rider.mountHands[2] = seatZ - bz * 0.28f;
        rider.mountPhase = t;
    }

    /** (0.3.6) Shouldering through brush: the pace eased down by how much of it there is, and the leaves rustle. */
    private void brush(Ride r, float dt) {
        if (r.body.brush <= 0) return;
        MountStats st = stats(r.mount);
        float cap = Math.max(st.trot, st.gallop * (1 - Math.min(BRUSH_MAX, r.body.brush * BRUSH_DRAG)));
        if (Math.abs(r.speed) > cap) r.speed += (Math.signum(r.speed) * cap - r.speed) * Math.min(1, dt * 4);
        rustle(r);
    }
    private void rustle(Ride r) {
        if (g.sfx == null || g.time < r.rustleAt) return;
        r.rustleAt = g.time + 0.3;
        Animal a = r.mount;
        // (0.3.6, gamefixes: a Pegasus in flight made footstep noises) leaves swished aside, not a foot on grass (it played a
        // footstep for every brush of a treetop the Pegasus flew through)
        g.sfx.play("cloth", a.x, a.y + a.height * 0.7f, a.z, Math.min(0.7f, 0.25f + Math.abs(r.flying ? r.airspeed : r.speed) * 0.04f), 1.35f, 14);
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
        // (0.3.9) in deep water it swims: buoyed at its float line, at its swimming pace, never falling
        float surface = waterSurface(r.body);
        float floatY = surface - r.body.height * MOUNT_FLOAT;
        float bottom = Float.isFinite(surface) ? r.body.ground(g.vox, r.body.x, surface, r.body.z, r.body.yaw) : Float.NEGATIVE_INFINITY;
        boolean afloat = Float.isFinite(surface) && floatY > bottom + 0.05f && r.body.y <= floatY + 0.6f;
        if (afloat != r.afloat) {
            r.afloat = afloat;
            g.state.commit(afloat ? "MountSwam" : "MountWadedOut", r.rider.id, r.mount.id, DevMap.jurisdiction(r.mount.x, r.mount.z), 0, String.format("at %.1f m/s", r.speed));
            if (afloat) splash(r, Math.max(0.5f, Math.min(1, Math.abs(r.body.vy) * 0.15f + 0.4f)));
        }
        if (afloat) { swim(r, st, wantYaw, want, dt, floatY); return; }
        boolean glide = st.flies && !r.body.grounded && c.jumpHeld() && r.body.vy < 0.5f && tamedFor(r.mount, r.rider);
        if (glide && !r.gliding) g.state.commit("MountGlided", r.rider.id, r.mount.id, DevMap.jurisdiction(r.mount.x, r.mount.z), 0, String.format("from %.1f m/s", r.speed));
        r.gliding = glide;
        float accel = want > r.speed ? (r.speed < st.canter ? 6f : 3.2f) : 9f;
        if (!r.body.grounded) accel *= 0.1f;
        if (glide) { want = Math.max(st.canter, Math.min(r.speed, st.gallop)); accel = 1.2f; r.chargeTicks = 0; r.charge = 0; }
        // a glide leans into the reins' turn as flight does (and levels again out of it)
        float glideBank = glide ? c.steer() * FLY_BANK * 0.6f : 0;
        r.bank += Math.max(-2.4f * dt, Math.min(2.4f * dt, glideBank - r.bank));
        r.speed += Math.max(-accel * dt, Math.min(accel * dt, want - r.speed));
        float fx = (float) -Math.sin(r.body.yaw), fz = (float) -Math.cos(r.body.yaw);
        if (glide) r.body.vy = Math.max(r.body.vy, -GLIDE_SINK);
        float asked = Math.abs(r.speed) * dt;
        float covered = r.body.move(g.vox, fx * r.speed * dt, fz * r.speed * dt, wantYaw, dt);
        if (glide) { r.body.vy = Math.max(r.body.vy, -GLIDE_SINK); if (r.mount.vigor > 0) r.mount.vigor = Math.max(0, r.mount.vigor - 1.5f * dt); }
        // run into something at speed: head-on at a gallop it goes down (and its rider over its head); slower, or along
        // the wall, it is checked hard
        if (r.body.blocked && r.speed >= st.gallop * CRASH_GALLOP && covered < asked * HEAD_ON) { crash(r, r.speed); return; }
        if (r.body.blocked && Math.abs(r.speed) > st.trot) r.speed *= 0.3f;
        else if (r.body.blocked) r.speed = 0;
        brush(r, dt);
        Animal a = r.mount;
        a.stamina = Math.min(a.maxStamina(), a.stamina + (Math.abs(r.speed) <= st.canter ? 4 : 0) * dt);
        if (st.flies) a.vigor = Math.min(st.vigor, a.vigor + 10 * dt);
    }

    /** (0.3.9) The top of the water the body's middle is in (the highest water cell's top over it), or NaN out of water. */
    float waterSurface(MountController b) {
        int bx = (int) Math.floor(b.x), bz = (int) Math.floor(b.z);
        int lo = (int) Math.floor(b.y + 0.05f), hi = (int) Math.floor(b.y + b.height);
        int wet = Integer.MIN_VALUE;
        for (int y = lo; y <= hi; y++) if (g.vox.block(bx, y, bz) == rpg.world.Blocks.WATER) { wet = y; break; }
        if (wet == Integer.MIN_VALUE) return Float.NaN;
        int top = wet;
        while (top < DevMap.SY - 1 && g.vox.block(bx, top + 1, bz) == rpg.world.Blocks.WATER) top++;
        return top + 1;
    }

    /**
     * (0.3.9) Swimming with its rider: the reins and the throttle still steer and drive it, at its swimming pace; it rides
     * at its float line, rising or settling to it smoothly (a plunge in takes it under for a moment and it bobs up); the
     * bank or a rising bottom is walked up onto as any step is.
     */
    private void swim(Ride r, MountStats st, float wantYaw, float want, float dt, float floatY) {
        Animal a = r.mount;
        float pace = Math.max(MOUNT_SWIM_FLOOR, a.species.swim), cap = Math.min(Math.abs(want), pace) * Math.signum(want);
        r.gliding = false; r.chargeTicks = 0; r.charge = 0;
        r.speed += Math.max(-3f * dt, Math.min(1.6f * dt, cap - r.speed));
        if (Math.abs(r.speed) > pace) r.speed = Math.signum(r.speed) * pace;
        float fx = (float) -Math.sin(r.body.yaw), fz = (float) -Math.cos(r.body.yaw);
        float gravity = r.body.gravity;
        r.body.gravity = 0; r.body.vy = 0; r.body.grounded = true;
        r.body.move(g.vox, fx * r.speed * dt, fz * r.speed * dt, wantYaw, dt);
        r.body.gravity = gravity;
        if (r.body.blocked) r.speed *= 0.5f;
        // buoyancy: to the float line (not above the water, never into the bottom)
        float floor = r.body.ground(g.vox, r.body.x, Math.max(r.body.y, floatY) + 0.5f, r.body.z, r.body.yaw);
        float target = Math.max(floatY + 0.04f * (float) Math.sin(g.time * 2.2 + a.id), Float.isFinite(floor) ? floor : floatY);
        float ny = r.body.y + (target - r.body.y) * Math.min(1, dt * 3.5f);
        if (r.body.fits(g.vox, r.body.x, ny, r.body.z, r.body.yaw)) r.body.y = ny;
        r.body.vy = 0; r.body.grounded = false;
        r.fallTop = r.body.y;     // water is no fall: nothing is paid for coming out of it
        r.flying = false; r.bank += (0 - r.bank) * Math.min(1, dt * 3);
        a.stamina = Math.max(0, a.stamina - (Math.abs(r.speed) > 0.3f ? 1.2f : 0) * dt);
        if (Math.abs(r.speed) > 0.4f && g.time >= r.splashAt) splash(r, 0.25f + Math.abs(r.speed) * 0.08f);
    }

    private void splash(Ride r, float gain) {
        r.splashAt = g.time + 0.55;
        if (g.sfx != null) g.sfx.play("splash", r.mount.x, r.mount.y + r.mount.height * 0.5f, r.mount.z, gain);
    }

    /**
     * Flight: momentum. Pitched down it gathers speed as it falls; pitched up it climbs and pays for it in speed; level
     * it glides, slowly losing both. The strafe keys bank it, and the bank turns it - tighter when slow. The jump key
     * surges the wings (vigor). Below stall speed the wings no longer hold it and it sinks. Meeting the ground slowly
     * enough it lands and gallops on; too fast and steep, it lands hard.
     */
    private void fly(Ride r, MountStats st, Controls c, float dt) {
        Animal a = r.mount;
        // (0.3.6, gamefixes: the steering was lopsided and the climb and dive the wrong way round) forward climbs, back
        // dives (crouch also pulls up, to slow it); the strafe keys bank it the way it turns - right rolls it right. The
        // bank used to be drawn the other way from the turn, so a turn right leaned it left
        float wantPitch = c.throttle() > 0.2f ? FLY_CLIMB : c.throttle() < -0.2f ? -FLY_DIVE : c.slow() ? 0.35f : -0.06f;
        r.pitch += Math.max(-1.6f * dt, Math.min(1.6f * dt, wantPitch - r.pitch));
        float wantBank = c.steer() * FLY_BANK;
        // the bank eases in and out at the same rate either way, and levels quicker than it rolls in
        float bankRate = Math.abs(wantBank) < Math.abs(r.bank) ? 2.8f : 2.0f;
        r.bank += Math.max(-bankRate * dt, Math.min(bankRate * dt, wantBank - r.bank));
        // the surge: a wingbeat on the press of the jump key
        if (c.jumpHeld() && !r.jumpWas && a.vigor >= SURGE_COST) {
            a.vigor -= SURGE_COST; r.airspeed += SURGE_PUSH; r.body.vy = Math.max(r.body.vy, 0) + 2.5f; a.surgeAt = g.time;
            g.state.commit("MountSurged", r.rider.id, a.id, null, 0, String.format("vigor %.0f", a.vigor));
        }
        // energy: gravity along the flight path, drag growing with the square of speed
        float gacc = 9.8f;
        r.airspeed += (-gacc * (float) Math.sin(r.pitch) - 0.0045f * r.airspeed * r.airspeed - (c.slow() ? 3f : 0)) * dt;
        r.airspeed = Math.max(0, Math.min(38, r.airspeed));
        // the banked turn: tan(bank) g / v, clamped the same both ways (banked right, the heading turns right: yaw falls)
        float turnRate = (float) (Math.tan(r.bank) * gacc / Math.max(FLY_TURN_FLOOR, r.airspeed));
        float newYaw = r.body.yaw - Math.max(-FLY_TURN_MAX, Math.min(FLY_TURN_MAX, turnRate)) * dt;
        // lift: the wings hold it up above stall; below, it sinks harder the slower it goes
        float sink = r.airspeed < STALL ? (STALL - r.airspeed) / STALL * 7f : 0;
        r.body.vy += (-sink - r.body.vy) * Math.min(1, dt * 2);
        float horiz = r.airspeed * (float) Math.cos(r.pitch), vert = r.airspeed * (float) Math.sin(r.pitch) + r.body.vy;
        float fx = (float) -Math.sin(newYaw), fz = (float) -Math.cos(newYaw);
        float nx = r.body.x + fx * horiz * dt, ny = r.body.y + vert * dt, nz = r.body.z + fz * horiz * dt;
        float floor = r.body.ground(g.vox, nx, Math.max(ny, r.body.y) + 0.5f, nz, newYaw);
        // (0.3.9) water under it is met at its float line: it lights on the water and swims
        float wet = waterSurface(r.body);
        if (Float.isFinite(wet)) floor = Math.max(floor, wet - r.body.height * MOUNT_FLOAT);
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
        else {
            // struck something: along a wall it slides and loses its way; head-on and fast, it crashes
            boolean slideX = r.body.fits(g.vox, nx, ny, r.body.z, newYaw), slideZ = !slideX && r.body.fits(g.vox, r.body.x, ny, nz, newYaw);
            float kept = slideX ? Math.abs(fx) : slideZ ? Math.abs(fz) : 0;
            if (r.airspeed >= CRASH_AIRSPEED && kept < HEAD_ON) { crash(r, r.airspeed); return; }
            if (slideX) r.body.x = nx; else if (slideZ) r.body.z = nz;
            r.body.y = slideX || slideZ ? ny : r.body.y;
            r.airspeed *= slideX || slideZ ? 0.75f : 0.4f; r.body.vy = Math.min(r.body.vy, -2);
        }
        r.body.brush = r.body.brushAt(g.vox, r.body.x, r.body.y, r.body.z, r.body.yaw);
        if (r.body.brush > 0) { r.airspeed *= 1 - Math.min(BRUSH_MAX, r.body.brush * BRUSH_DRAG) * Math.min(1, dt * 4); rustle(r); }
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
        a.swimMode = r.afloat ? rpg.actor.Swimming.SWIM : rpg.actor.Swimming.NONE;
        a.flightSpeed = r.flying ? r.airspeed : r.gliding ? Math.abs(r.speed) : 0;
        if (a.flying || r.flying || r.gliding) { float gy = g.vox.groundY(a.x, a.y + 0.2f, a.z); a.flightHeight = Float.isFinite(gy) ? a.y - gy : 99; } else a.flightHeight = 0;
        a.flying = r.flying || r.gliding; a.flightPitch = r.flying ? r.pitch : r.gliding ? -0.12f : 0;
        a.flightBank = r.flying ? r.bank : r.gliding ? r.bank * 0.9f + (float) Math.max(-0.5, Math.min(0.5, Actor.wrap(r.body.yaw - a.yaw) * 6)) * 0.1f : 0;
        a.wantYaw = Float.NaN;
        if (a.body != 0) { g.game.commands().moverPlace(a.body, a.x, a.y, a.z, 0, 0, 0); g.game.commands().moverInput(a.body, 0, 0, 0, 0, 80, MOVER_POSED); }
        // (0.3.6, gamefixes: riders did not sit properly on their mounts) in the saddle as it is drawn on this mount: the
        // hips on the seat's top (they sat a fifth of a metre down inside the barrel, the saddle's height having been taken
        // as a share of the hitbox), over its middle (they were behind it), the feet in stirrups hung just outside the
        // barrel (the legs ran through the horse's sides); in flight the body pitches, and the rider with it
        var fig = rpg.render.AnimalModels.of(a.species, a.variant);
        float vs = rpg.render.AnimalModels.PX * a.scale(), rpx = rider.height / rpg.actor.Body.HEIGHT_PX;
        float top = (fig.bodyY + fig.bh / 2f) * vs, seat = top + SADDLE_SEAT * vs, ahead = fig.bl * SADDLE_AHEAD * vs;
        float rideY = a.y + seat - (rpg.actor.Body.LEGS - SEAT_HIP) * rpx;
        rider.x = a.x + fx * ahead; rider.z = a.z + fz * ahead; rider.y = rideY;
        rider.stirrupX = (fig.bw / 2f + STIRRUP_OUT) * vs / rpx;
        rider.stirrupY = (top - STIRRUP_DROP * vs - (seat - (rpg.actor.Body.LEGS - SEAT_HIP) * rpx)) / rpx;
        rider.stirrupZ = -STIRRUP_AHEAD * vs / rpx;
        if (r.mounting()) climbPose(r, rider.x, rideY, rider.z, rpx);
        else {
            // (0.3.9, gamefixes: attacking from the saddle) the rider turns in the saddle toward what they fight or aim at -
            // the shoulders, so the swing, the shot and the spell go that way - while the feet stay in the irons
            float want = 0;
            if (fighting(rider)) want = Math.max(-TWIST_MAX, Math.min(TWIST_MAX, Actor.wrap(aimOf(rider) - a.yaw)));
            r.twist += (want - r.twist) * Math.min(1, dt() * 8);
            rider.yaw = a.yaw + r.twist;
            stirrups(r, rider.x, rideY, rider.z, rpx);
        }
        rider.vx = 0; rider.vz = 0; rider.grounded = false;
        rider.rideSpeed = r.flying ? r.airspeed : Math.abs(r.speed); rider.rideGait = a.gait;
        if (rider.body != 0) { g.game.commands().moverPlace(rider.body, rider.x, rider.y, rider.z, 0, 0, 0); g.game.commands().moverInput(rider.body, 0, 0, 0, 0, 80, MOVER_POSED); }
    }
}
