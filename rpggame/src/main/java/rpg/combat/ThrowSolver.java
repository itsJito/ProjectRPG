package rpg.combat;

import rpg.Rpg;
import rpg.actor.Actor;
import rpg.world.Voxels;

/**
 * The throw (gamefixes): one formula for the path shown and the path flown, pos = start + v t + g t^2 / 2.
 *
 * <p>The target is where the camera's centre ray meets the world; the start is the throwing hand. The flight time is
 * the distance over the throw's speed, and the launch velocity is solved so the path passes through the target at that
 * time: v = (target - start - g t^2 / 2) / t. Thirty points are plotted along it and swept with a 0.15 m sphere between
 * each pair; the first contact cuts the path there and is where it lands. The landing is marked red when something is
 * in the way short of the target, or when the landing is under 1.5 m from the hand; white otherwise. The solve is redone
 * only when the aim has moved more than half a degree (or the hand has moved), and the release uses the very velocity
 * the guide shows.
 */
public final class ThrowSolver {
    public static final int POINTS = 30;
    public static final float RADIUS = 0.15f, MIN_DISTANCE = 1.5f, REACH = 45f;
    private static final float HALF_DEGREE = (float) Math.toRadians(0.5);

    public final float[][] points = new float[POINTS][3];
    public int count;
    public final float[] start = new float[3], target = new float[3], landing = new float[3], velocity = new float[3];
    public float gravity, time;
    /** Something stands between the hand and the target; or the landing is too close to throw at. */
    public boolean blocked, tooClose;
    private float lastYaw = Float.NaN, lastPitch, lastSpeed;
    /** When it was last solved (game time), and the eye it was aimed from (a release re-uses the guide's aim). */
    public double solvedAt = -99;
    public final float[] eye = new float[3];

    /** Gravity for what is thrown (m/s^2, tuned 12 to 22): a dart floats, a javelin drops, a body drops hardest. */
    public static float gravityFor(String thrown) {
        if (thrown == null) return 20f;
        return switch (thrown) { case "dart" -> 13f; case "knife" -> 14f; case "tomahawk" -> 17f; case "javelin" -> 16f; default -> 15f; };
    }

    /** Where the camera's centre looks: the first thing its ray meets, or as far as a throw could reach. */
    public static void cameraTarget(Rpg g, Actor a, float[] eye, float[] out) {
        float cp = (float) Math.cos(a.pitch);
        float dx = (float) -Math.sin(a.aimYaw) * cp, dy = (float) Math.sin(a.pitch), dz = (float) -Math.cos(a.aimYaw) * cp;
        Voxels.Hit h = g.vox.ray(eye[0], eye[1], eye[2], dx, dy, dz, REACH, true);
        float d = h.hit ? h.dist : REACH;
        out[0] = eye[0] + dx * d; out[1] = eye[1] + dy * d; out[2] = eye[2] + dz * d;
    }

    /** Re-solves the throw when the aim has moved more than half a degree (or force). Returns whether it changed. */
    public boolean solve(Rpg g, Actor a, float[] hand, float[] eye, float speed, float gravity, boolean force) {
        boolean moved = Float.isNaN(lastYaw) || Math.abs(Actor.wrap(a.aimYaw - lastYaw)) > HALF_DEGREE || Math.abs(a.pitch - lastPitch) > HALF_DEGREE
                || Math.abs(speed - lastSpeed) > 0.25f || Math.hypot(hand[0] - start[0], hand[2] - start[2]) > 0.05f || Math.abs(hand[1] - start[1]) > 0.05f;
        if (!force && !moved) return false;
        lastYaw = a.aimYaw; lastPitch = a.pitch; lastSpeed = speed; solvedAt = g.time;
        this.gravity = gravity;
        System.arraycopy(hand, 0, start, 0, 3);
        System.arraycopy(eye, 0, this.eye, 0, 3);
        cameraTarget(g, a, eye, target);
        float dx = target[0] - start[0], dy = target[1] - start[1], dz = target[2] - start[2];
        float distance = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        // no throw carries further than its arm can send it: a farther aim is brought in along the line to that range
        float range = speed * speed / gravity * 1.6f;
        if (distance > range) { float k = range / distance; dx *= k; dy *= k; dz *= k; distance = range; target[0] = start[0] + dx; target[1] = start[1] + dy; target[2] = start[2] + dz; }
        time = Math.max(0.12f, distance / Math.max(1f, speed));
        // v = (target - start - g t^2 / 2) / t, with g pointing down
        velocity[0] = dx / time; velocity[1] = (dy + 0.5f * gravity * time * time) / time; velocity[2] = dz / time;
        plot(g, a);
        return true;
    }

    /** Thirty points along the path, swept between with a sphere; the first contact ends it. */
    private void plot(Rpg g, Actor a) {
        count = 0; blocked = false;
        float px = start[0], py = start[1], pz = start[2];
        points[count][0] = px; points[count][1] = py; points[count][2] = pz; count++;
        // the path is plotted on past the target (aimed at the sky, it still comes down somewhere): two and a half flight
        // times, cut where it first meets something
        float span = time * 2.5f;
        for (int i = 1; i < POINTS; i++) {
            float s = span * i / (POINTS - 1);
            float x = start[0] + velocity[0] * s, y = start[1] + velocity[1] * s - 0.5f * gravity * s * s, z = start[2] + velocity[2] * s;
            float[] hit = sweep(g, a, px, py, pz, x, y, z);
            if (hit != null) {
                points[count][0] = hit[0]; points[count][1] = hit[1]; points[count][2] = hit[2]; count++;
                landing[0] = hit[0]; landing[1] = hit[1]; landing[2] = hit[2];
                // short of the target by more than the sphere's own size: something is in the way
                blocked = Math.hypot(Math.hypot(hit[0] - target[0], hit[1] - target[1]), hit[2] - target[2]) > 0.6f;
                tooClose = Math.hypot(hit[0] - start[0], hit[2] - start[2]) < MIN_DISTANCE;
                return;
            }
            points[count][0] = x; points[count][1] = y; points[count][2] = z; count++;
            px = x; py = y; pz = z;
        }
        landing[0] = px; landing[1] = py; landing[2] = pz;
        tooClose = Math.hypot(px - start[0], pz - start[2]) < MIN_DISTANCE;
    }

    /** A 0.15 m sphere swept along a segment: the world (the centre ray and four rays at the sphere's rim) and other bodies. */
    private static float[] sweep(Rpg g, Actor a, float ax, float ay, float az, float bx, float by, float bz) {
        float dx = bx - ax, dy = by - ay, dz = bz - az, len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1e-5f) return null;
        // two vectors across the segment, for the rim rays
        float ux = -dz / len, uz = dx / len, vx = 0, vy = 1, vz = 0;
        float best = Float.MAX_VALUE; float[] at = null;
        float[][] rim = {{0, 0, 0}, {ux * RADIUS, 0, uz * RADIUS}, {-ux * RADIUS, 0, -uz * RADIUS}, {vx * RADIUS, vy * RADIUS, vz * RADIUS}, {-vx * RADIUS, -vy * RADIUS, -vz * RADIUS}};
        for (float[] o : rim) {
            Voxels.Hit h = g.vox.ray(ax + o[0], ay + o[1], az + o[2], dx, dy, dz, len, true);
            if (h.hit && h.dist < best) { best = h.dist; at = new float[]{ax + dx / len * h.dist, ay + dy / len * h.dist, az + dz / len * h.dist}; }
        }
        for (Actor o : g.actors()) {
            if (o == a || !o.alive) continue;
            // the body as an upright capsule: closest approach of the segment to its axis, within its radius plus the sphere's
            float t = Math.max(0, Math.min(1, ((o.x - ax) * dx + (o.z - az) * dz) / Math.max(1e-6f, dx * dx + dz * dz)));
            float cx = ax + dx * t, cy = ay + dy * t, cz = az + dz * t;
            if (cy < o.y || cy > o.y + o.height) continue;
            if (Math.hypot(cx - o.x, cz - o.z) > o.radius + RADIUS) continue;
            float d = t * len;
            if (d < best) { best = d; at = new float[]{cx, cy, cz}; }
        }
        return at;
    }
}
