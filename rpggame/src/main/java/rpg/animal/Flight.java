package rpg.animal;

import astratto.bridge.AstrattoNative;
import rpg.Rpg;
import rpg.actor.Actor;
import rpg.world.Blocks;

/**
 * (0.4.7, gamefixes: the Crow, the Pigeon and the Vulture - animals that fly) A flier's time in the air, and when it goes
 * up. On the ground a bird is any animal (its mind grazes, preens, feeds at a carcass, flees on foot); it takes to the air:
 * <ul><li>when frightened - away from what frightened it, to settle again a safe way off;</li>
 * <li>when it means to go further than a hop - another part of its range, a high roost (a roof's ridge, a rock) for the
 * night or between flights, a carcass it has seen from far off (a crow at a hundred metres, a vulture at three hundred);</li>
 * <li>a soarer by day, to circle on the warm air over its range, high, for an hour at a time.</li></ul>
 * Aloft it is flown, not walked: posed where this puts it each moment (gravity and the navigation agent kept off it),
 * turning at a bird's rate, banking into its turns, climbing over the ground ahead and beating its wings to climb or
 * to take off and land, gliding between; it comes down onto the spot it chose. Shot or struck in the air it falls as any
 * body does (the ragdoll takes it from where it flew). A bird perched high stays put, watching, until something comes
 * close or it is time to go. All of it is the species' (Species.flies, cruise, airspeed, soars, roostsHigh) and the
 * tuning's ("flight.*").
 */
final class Flight {
    static final int GROUND = 0, CLIMB = 1, CRUISE = 2, CIRCLE = 3, LAND = 4, PERCHED = 5;
    static final float TURN = rpg.Tuning.f("flight.turn", 2.6f), WANDER_MIN = rpg.Tuning.f("flight.wanderMin", 45f), WANDER_MAX = rpg.Tuning.f("flight.wanderMax", 150f),
            CARRION_CROW = rpg.Tuning.f("flight.carrionCrow", 110f), CARRION_VULTURE = rpg.Tuning.f("flight.carrionVulture", 300f),
            SOAR_MINUTES = rpg.Tuning.f("flight.soarMinutes", 50f), SPOOK = rpg.Tuning.f("flight.spook", 2.4f), PEOPLE_SPOOK = rpg.Tuning.f("flight.peopleSpook", 2.2f),
            STUCK = rpg.Tuning.f("flight.stuck", 2.5f);

    private final Rpg g;
    private final Animals sys;
    private final java.util.Random rnd = new java.util.Random(0x0B1D5L);
    Flight(Rpg g, Animals sys) { this.g = g; this.sys = sys; }

    /** One moment of a flier: true while flight has the body (its mind is left out), false on the ground. */
    boolean tick(Animal a, float dt) {
        if (!a.alive || a.ragdoll != null || a.rider != 0 || a.puppet) { if (a.flying) ground(a); return false; }
        if (!a.flying) { decide(a); if (!a.flying) return false; }
        if (a.flightMode == PERCHED) return perched(a, dt);
        fly(a, dt);
        return true;
    }

    // ------------------------------------------------------------------ when it goes up
    private void decide(Animal a) {
        Species sp = a.species;
        if (g.time < a.nextFlightCheck) return;
        a.nextFlightCheck = g.time + 0.4 + rnd.nextFloat() * 0.4;
        double hour = g.state.hourOfDay();
        boolean night = hour < 6 || hour >= 20;
        // frightened: up and away from it
        Actor threat = a.threat != 0 ? g.actor(a.threat) : null;
        if (a.goal == Animal.Goal.FLEE && threat != null || nearestThreat(a) instanceof Actor t && (threat = t) != null) {
            float dx = a.x - threat.x, dz = a.z - threat.z, l = Math.max(0.1f, (float) Math.hypot(dx, dz));
            float dist = 30 + rnd.nextFloat() * 40;
            float[] to = settle(a, a.x + dx / l * dist, a.z + dz / l * dist, false);
            takeOff(a, to, CRUISE, "took fright at " + threat.name.toLowerCase());
            return;
        }
        // a carcass seen from the air
        if (sp.diet == Species.Diet.SCAVENGER && a.hunger > 0.35f && !night) {
            Actor c = sys.carcassNear(a, sp == Species.VULTURE ? CARRION_VULTURE : CARRION_CROW);
            if (c != null && a.distTo(c) > 18) {
                float ang = rnd.nextFloat() * 6.283f;
                takeOff(a, settle(a, c.x + (float) Math.cos(ang) * 2.2f, c.z + (float) Math.sin(ang) * 2.2f, false), sp == Species.VULTURE ? CIRCLE : CRUISE, "flying to a carcass");
                if (sp == Species.VULTURE) { a.circleX = c.x; a.circleZ = c.z; a.circleR = 14; a.flightUntil = g.time + 20 + rnd.nextFloat() * 25; a.landAfter = true; }
                a.hunger = Math.max(a.hunger, 0.65f);   // it came to eat: the mind feeds on arrival
                return;
            }
        }
        // a soarer goes up by day to circle on the warm air
        if (sp.soars && hour >= 8.5 && hour < 17.5 && g.time >= a.nextFlight) {
            float[] home = homeOf(a);
            a.circleX = home[0] + (rnd.nextFloat() - 0.5f) * 30; a.circleZ = home[1] + (rnd.nextFloat() - 0.5f) * 30; a.circleR = 18 + rnd.nextFloat() * 22;
            a.flightUntil = g.time + SOAR_MINUTES * 60 / Math.max(0.01, g.minuteRate()) * (0.6 + rnd.nextFloat() * 0.8);
            a.landAfter = false;
            takeOff(a, new float[]{a.circleX, groundAt(a.circleX, a.circleZ) + sp.cruise, a.circleZ}, CIRCLE, "soaring on the warm air");
            return;
        }
        // to roost high for the night (and a pigeon between its rounds of the square)
        if (sp.roostsHigh && (night || sp == Species.PIGEON && rnd.nextFloat() < 0.15f) && g.time >= a.nextFlight) {
            float[] home = homeOf(a);
            takeOff(a, settle(a, home[0], home[1], true), CRUISE, night ? "going up to roost" : "up to the rooftops");
            return;
        }
        // a hop to another part of its range now and then
        if (!night && g.time >= a.nextFlight && a.goal != Animal.Goal.FEED) {
            float[] home = homeOf(a);
            float r = Math.max(10, a.range * 1.6f);
            float ang = rnd.nextFloat() * 6.283f, d = r * (0.3f + rnd.nextFloat() * 0.7f);
            takeOff(a, settle(a, home[0] + (float) Math.cos(ang) * d, home[1] + (float) Math.sin(ang) * d, sp == Species.PIGEON && rnd.nextFloat() < 0.5f), CRUISE, "flying across its range");
        }
    }

    /** Anyone close enough to put it up: a person or a hunter within its flight distance (times SPOOK for a bird), not a friend. */
    private Actor nearestThreat(Animal a) {
        float reach = Math.max(2.5f, a.species.flight * SPOOK);
        Actor best = null; float bd = reach;
        for (Actor o : g.actors()) {
            if (Math.abs(o.x - a.x) > reach || Math.abs(o.z - a.z) > reach) continue;
            if (o == a || !o.alive || o instanceof Animal other && (other.species == a.species || !other.species.predator())) continue;
            if (o == g.player && g.notarget()) continue;
            float d = a.distTo(o);
            // townsfolk going about their day are passed at a step or two; only a running or fighting one, the player,
            // or a hunter puts the birds up from further off
            if (o instanceof rpg.ai.Npc n && Math.hypot(n.vx, n.vz) < 3.5f && n.fightTarget == 0 && d > PEOPLE_SPOOK) continue;
            if (d < bd) { bd = d; best = o; }
        }
        return best;
    }

    private float[] homeOf(Animal a) { return a.home != null ? new float[]{(a.home.x0 + a.home.x1) / 2f, (a.home.z0 + a.home.z1) / 2f} : new float[]{a.homeX, a.homeZ}; }

    /**
     * Where to come down near a point: the ground (dry, open) or, for a high roost, the highest top round about with
     * open air over it (a roof's ridge, a wall's top, a rock). {x, y, z}.
     */
    private float[] settle(Animal a, float x, float z, boolean high) {
        if (high) {
            float[] best = null;
            for (int k = 0; k < 16; k++) {
                int cx = (int) Math.floor(x + (rnd.nextFloat() - 0.5f) * 24), cz = (int) Math.floor(z + (rnd.nextFloat() - 0.5f) * 24);
                int y = topOf(cx, cz);
                if (y < 0 || g.vox.block(cx, y - 1, cz) == Blocks.WATER || Blocks.isLeaves(g.vox.block(cx, y - 1, cz))) continue;
                if (best == null || y > best[1]) best = new float[]{cx + 0.5f, y, cz + 0.5f};
            }
            if (best != null && best[1] > groundAt(best[0], best[2]) + 2) return best;
        }
        float[] d = rpg.world.WorldMap.shadowveil() ? rpg.world.shadowveil.ShadowVeil.dry(x, z) : new float[]{x, z};
        return new float[]{d[0], groundAt(d[0], d[1]), d[1]};
    }
    /** The top of a column: the cell over its highest solid block (with two open over it), or -1. */
    private int topOf(int x, int z) {
        int start = Math.min(g.vox.sizeY() - 3, (int) groundAt(x + 0.5f, z + 0.5f) + 70);
        for (int y = start; y > 1; y--) if (g.vox.solid(x, y - 1, z)) return g.vox.block(x, y, z) == Blocks.AIR && g.vox.block(x, y + 1, z) == Blocks.AIR ? y : -1;
        return -1;
    }
    private float groundAt(float x, float z) { return rpg.world.WorldMap.standY(x, z); }
    /** The top of whatever stands in a column (a roof, a tower, a crown of leaves), else the ground. */
    private float skyline(float x, float z) { int t = topOf((int) Math.floor(x), (int) Math.floor(z)); return Math.max(groundAt(x, z), t < 0 ? 0 : t); }
    /** Whether a bird's small body at a point would be in something solid (leaves count: it goes over a crown, not through). */
    private boolean blocked(float x, float y, float z) {
        for (float[] o : new float[][]{{0, 0}, {0.3f, 0}, {-0.3f, 0}, {0, 0.3f}, {0, -0.3f}}) {
            int bx = (int) Math.floor(x + o[0]), by = (int) Math.floor(y + 0.25f), bz = (int) Math.floor(z + o[1]);
            if (g.vox.solid(bx, by, bz) || Blocks.isLeaves(g.vox.block(bx, by, bz))) return true;
        }
        return false;
    }

    private void takeOff(Animal a, float[] to, int mode, String why) {
        a.flying = true; a.flightMode = CLIMB; a.nextMode = mode;
        a.fx = to[0]; a.fy = to[1]; a.fz = to[2];
        a.flightSpeed = a.species.airspeed * 0.4f; a.flap = 1;
        a.lastEvent = why;
        a.goal = Animal.Goal.WANDER;
        sys.desire(a, AnimalMind.A_HOLD);
        if (g.sfx != null && a.distTo(g.player) < 30) g.sfx.play("soft", a.x, a.y + 0.3f, a.z, 0.3f);
        if (a.species != Species.VULTURE && rnd.nextFloat() < 0.6f) sys.sound(a, a.species.alarmSound, 0.75f);
    }

    // ------------------------------------------------------------------ in the air
    private void fly(Animal a, float dt) {
        Species sp = a.species;
        sys.desire(a, AnimalMind.A_HOLD);
        float tx = a.fx, tz = a.fz;
        if (a.flightMode == CIRCLE) {
            // round the circle's centre, the aim a quarter turn on ahead
            a.circleA += dt * sp.airspeed / Math.max(6, a.circleR);
            tx = a.circleX + (float) Math.cos(a.circleA) * a.circleR; tz = a.circleZ + (float) Math.sin(a.circleA) * a.circleR;
            if (g.time >= a.flightUntil) {
                if (a.landAfter) { a.flightMode = LAND; }
                else { float[] to = settle(a, a.circleX, a.circleZ, sp.roostsHigh); a.fx = to[0]; a.fy = to[1]; a.fz = to[2]; a.flightMode = CRUISE; }
            }
        }
        float dx = tx - a.x, dz = tz - a.z, horiz = (float) Math.hypot(dx, dz);
        // the height it wants: climbing out, cruising over the ground ahead, coming down over the last stretch
        // the tallest thing below and ahead (the ground, a tower, a tree's crown) is what it keeps above
        float ahead = groundAt(a.x, a.z);
        for (int k = 0; k <= 3; k++) ahead = Math.max(ahead, skyline(a.x - (float) Math.sin(a.yaw) * k * 5, a.z - (float) Math.cos(a.yaw) * k * 5));
        float cruiseY = ahead + sp.cruise * (a.flightMode == CIRCLE ? 1 + 0.12f * (float) Math.sin(g.time * 0.21 + a.id) : 1);
        float wantY;
        boolean landing = a.flightMode == LAND || a.flightMode == CRUISE && horiz < Math.max(8, sp.cruise * 1.4f);
        if (landing) {
            float k = Math.max(0, Math.min(1, horiz / Math.max(8, sp.cruise * 1.4f)));
            wantY = a.fy + (Math.max(cruiseY, a.fy + 2) - a.fy) * k * k;
            if (a.flightMode == LAND) { tx = a.fx; tz = a.fz; dx = tx - a.x; dz = tz - a.z; horiz = (float) Math.hypot(dx, dz); }
        } else wantY = cruiseY;
        if (a.flightMode == CLIMB && a.y > ahead + Math.min(sp.cruise * 0.5f, 6)) a.flightMode = a.nextMode;
        // the heading: turned toward the aim at a bird's rate, banked into the turn
        float want = (float) Math.atan2(-dx, -dz);
        float turn = (float) Math.atan2(Math.sin(want - a.yaw), Math.cos(want - a.yaw));
        float rate = TURN * (sp == Species.VULTURE ? 0.6f : 1) * (landing ? 1.6f : 1);
        float step = Math.max(-rate * dt, Math.min(rate * dt, turn));
        a.yaw += step;
        a.flightBank += ((step / Math.max(1e-4f, dt)) * 0.35f - a.flightBank) * Math.min(1, dt * 4);
        // speed: quick in the cruise, slowing to land; climbing and landing beat the wings, the cruise glides between strokes
        float wantSpeed = sp.airspeed * (landing ? Math.max(0.3f, Math.min(1, horiz / 10f)) : a.flightMode == CLIMB ? 0.7f : 1);
        a.flightSpeed += (wantSpeed - a.flightSpeed) * Math.min(1, dt * 1.5f);
        float vy = Math.max(-4f, Math.min(3.5f, (wantY - a.y) * 1.4f));
        float glide = sp.soars ? 0.04f : 0.18f;
        float flapWant = a.flightMode == CLIMB || vy > 0.6f || landing && horiz < 6 ? 1 : glide + (sp.soars ? 0 : 0.25f * (float) Math.max(0, Math.sin(g.time * 0.7 + a.id)));
        a.flap += (flapWant - a.flap) * Math.min(1, dt * 4);
        a.flightPitch = (float) Math.atan2(vy, Math.max(1, a.flightSpeed)) * 0.8f;
        float nx = a.x - (float) Math.sin(a.yaw) * a.flightSpeed * dt, nz = a.z - (float) Math.cos(a.yaw) * a.flightSpeed * dt, ny = a.y + vy * dt;
        // never into the world: up over whatever is ahead
        if (blocked(nx, ny, nz)) {
            // something in the way: turn off to the open side and climb, never stand in the air against it
            a.yaw += (a.id % 2 == 0 ? 1 : -1) * 2.2f * dt;
            ny = a.y + 4f * dt; nx = a.x; nz = a.z;
            if (blocked(nx, ny, nz)) ny = a.y + 1.0f;
            if (a.flightMode == CIRCLE) { a.circleX += (a.circleX - a.x) * 0.02f; a.circleZ += (a.circleZ - a.z) * 0.02f; }
        }
        ny = Math.max(ny, groundAt(nx, nz) + (landing && horiz < 1.5f ? 0 : 0.6f));
        a.flightHeight = ny - groundAt(nx, nz);
        // no headway for a while (a wall in the way, a spot it cannot get down to): it comes down on whatever is under it
        if (Math.abs(a.x - a.stuckX) + Math.abs(a.y - a.stuckY) + Math.abs(a.z - a.stuckZ) > 1.2f) { a.stuckX = a.x; a.stuckY = a.y; a.stuckZ = a.z; a.stuckSince = g.time; }
        else if (g.time - a.stuckSince > STUCK && a.flightMode != CIRCLE) {
            int top = topOf((int) Math.floor(nx), (int) Math.floor(nz));
            a.fx = (float) Math.floor(nx) + 0.5f; a.fz = (float) Math.floor(nz) + 0.5f; a.fy = top > 0 ? top : groundAt(nx, nz);
            a.flightMode = LAND; a.stuckSince = g.time;
            if (Math.abs(a.y - a.fy) < 3) { nx = a.fx; ny = a.fy; nz = a.fz; }
        }
        place(a, nx, ny, nz);
        a.vx = (nx - a.x) / Math.max(1e-4f, dt); a.vz = (nz - a.z) / Math.max(1e-4f, dt);
        a.x = nx; a.y = ny; a.z = nz;
        // down: settled on the spot (a roost high stays perched; the ground hands it back to its mind)
        if ((a.flightMode == CRUISE || a.flightMode == LAND) && horiz < 0.9f && Math.abs(a.y - a.fy) < 0.6f) {
            place(a, a.fx, a.fy, a.fz);
            a.x = a.fx; a.y = a.fy; a.z = a.fz; a.vx = a.vz = 0;
            a.flightBank = 0; a.flightPitch = 0;
            boolean high = a.fy > groundAt(a.fx, a.fz) + 1.5f;
            if (high) { a.flightMode = PERCHED; a.perchUntil = g.time + 60 + rnd.nextFloat() * 240; a.lastEvent = "perched high"; }
            else ground(a);
            a.nextFlight = g.time + WANDER_MIN + rnd.nextFloat() * (WANDER_MAX - WANDER_MIN);
        }
    }

    /** Perched high: still but for the head, until something comes close or its time there is up. */
    private boolean perched(Animal a, float dt) {
        sys.desire(a, AnimalMind.A_HOLD);
        place(a, a.x, a.y, a.z);
        a.flap += (0 - a.flap) * Math.min(1, dt * 4);
        a.headYaw = 0.6f * (float) Math.sin(g.time * 0.5 + a.id);
        double hour = g.state.hourOfDay();
        boolean night = hour < 6 || hour >= 20;
        Actor t = nearestThreat(a);
        if (t != null || g.time >= a.perchUntil && !night) {
            float[] home = homeOf(a);
            float ang = rnd.nextFloat() * 6.283f, d = 12 + rnd.nextFloat() * 30;
            float[] to = settle(a, home[0] + (float) Math.cos(ang) * d, home[1] + (float) Math.sin(ang) * d, false);
            takeOff(a, to, CRUISE, t != null ? "flushed from its perch" : "down from its perch");
        }
        return true;
    }

    /** Held in the air (or on its perch) where flight puts it: posed, no gravity, no agent. */
    private void place(Animal a, float x, float y, float z) {
        if (a.body == 0) return;
        g.game.commands().moverPlace(a.body, x, y, z, 0, 0, 0);
        g.game.commands().moverInput(a.body, 0, 0, 0, 0, 10, AstrattoNative.MOVER_POSED);
    }

    /** Back on the ground: its legs and its mind have it again. */
    void ground(Animal a) {
        a.flying = false; a.flightMode = GROUND; a.flap = 0; a.flightBank = 0; a.flightPitch = 0; a.headYaw = 0;
        if (a.body != 0) g.game.commands().moverInput(a.body, 0, 0, 0, 0, 10, 0);
        a.nextThink = 0;
    }
}
