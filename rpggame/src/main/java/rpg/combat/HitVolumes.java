package rpg.combat;

import static astratto.bridge.AstrattoNative.*;
import rpg.Rpg;
import rpg.actor.Actor;
import rpg.actor.Body;
import rpg.ai.Npc;

/** Continuous segment contacts against the same articulated joints used to draw a reacting human. */
final class HitVolumes {
    private HitVolumes() { }
    private static final int[][] BONES = {{RD_NECK, RD_PELVIS}, {RD_L_SHOULDER, RD_R_SHOULDER},
            {RD_L_SHOULDER, RD_L_ELBOW}, {RD_L_ELBOW, RD_L_WRIST}, {RD_R_SHOULDER, RD_R_ELBOW}, {RD_R_ELBOW, RD_R_WRIST},
            {RD_L_HIP, RD_L_KNEE}, {RD_L_KNEE, RD_L_ANKLE}, {RD_R_HIP, RD_R_KNEE}, {RD_R_KNEE, RD_R_ANKLE}};

    private static final Gore.Part[] PARTS = {Gore.Part.TORSO, Gore.Part.TORSO,
            Gore.Part.ARM_L, Gore.Part.ARM_L, Gore.Part.ARM_R, Gore.Part.ARM_R,
            Gore.Part.LEG_L, Gore.Part.LEG_L, Gore.Part.LEG_R, Gore.Part.LEG_R};

    private static float[] pose(Rpg g, Actor actor) {
        if (actor instanceof Npc n && n.ragdoll != 0 && g.lastBodies != null) {
            var body = g.lastBodies.ragdoll(n.ragdoll);
            return body == null ? null : body.p;
        }
        return actor.posePts != null && !actor.poseReset && g.time - actor.poseAt < 0.2 ? actor.posePts : null;
    }

    static Gore.Part part(Rpg g, Actor actor, float x, float y, float z) {
        float[] points = pose(g, actor);
        if (points == null || actor instanceof rpg.animal.Animal) return null;
        int head = RD_HEAD * 3;
        float dx = x - points[head], dy = y - points[head + 1], dz = z - points[head + 2];
        float nearest = (float) Math.sqrt(dx * dx + dy * dy + dz * dz) - 4.5f * Body.PX;
        Gore.Part part = Gore.Part.HEAD;
        if (g.gore.lost(actor, part)) nearest = Float.POSITIVE_INFINITY;
        for (int i = 0; i < BONES.length; i++) {
            if (g.gore.lost(actor, PARTS[i])) continue;
            int a = BONES[i][0] * 3, b = BONES[i][1] * 3;
            float ux = points[b] - points[a], uy = points[b + 1] - points[a + 1], uz = points[b + 2] - points[a + 2];
            float length = ux * ux + uy * uy + uz * uz;
            float t = Math.max(0, Math.min(1, ((x - points[a]) * ux + (y - points[a + 1]) * uy + (z - points[a + 2]) * uz) / Math.max(1e-8f, length)));
            dx = x - points[a] - ux * t; dy = y - points[a + 1] - uy * t; dz = z - points[a + 2] - uz * t;
            float distance = (float) Math.sqrt(dx * dx + dy * dy + dz * dz) - (i < 2 ? 3.6f : 2) * Body.PX;
            if (distance < nearest) { nearest = distance; part = PARTS[i]; }
        }
        return part;
    }

    /** Distance along a normalized ray, or infinity. No hit against the old standing capsule while ragdolled. */
    static float ray(Rpg g, Actor actor, float ox, float oy, float oz, float dx, float dy, float dz, float limit, float pad) {
        float[] pose = pose(g, actor);
        if (pose == null || actor instanceof rpg.animal.Animal) {
            float r = actor.radius + pad, height = actor.height * (actor.sneaking || actor.crouched ? rpg.actor.Body.CROUCH_HEIGHT : 1);
            return capsule(ox, oy, oz, dx, dy, dz, actor.x, actor.y + r, actor.z,
                    actor.x, actor.y + Math.max(r, height - r), actor.z, r, limit);
        }
        // The head is a separate volume, not the narrow upper end of the torso collider.
        float best = g.gore.lost(actor, Gore.Part.HEAD) ? Float.POSITIVE_INFINITY
                : head(g, actor, pose, ox, oy, oz, dx, dy, dz, limit, pad);
        for (int i = 0; i < BONES.length; i++) {
            if (g.gore.lost(actor, PARTS[i])) continue;
            int a = BONES[i][0] * 3, b = BONES[i][1] * 3;
            float radius = (i < 2 ? 3.6f : 2f) * Body.PX + pad;
            best = Math.min(best, capsule(ox, oy, oz, dx, dy, dz, pose[a], pose[a + 1], pose[a + 2],
                    pose[b], pose[b + 1], pose[b + 2], radius, limit));
        }
        return best;
    }

    /** The rendered eight-pixel head is a box above the neck, including its corners. */
    private static float head(Rpg g, Actor actor, float[] pose, float ox, float oy, float oz, float dx, float dy, float dz, float limit, float pad) {
        int neck = RD_NECK * 3, head = RD_HEAD * 3;
        float ux = pose[head] - pose[neck], uy = pose[head + 1] - pose[neck + 1], uz = pose[head + 2] - pose[neck + 2];
        float length = (float) Math.sqrt(ux * ux + uy * uy + uz * uz);
        if (length < 1e-5f) { ux = 0; uy = 1; uz = 0; length = 1; }
        ux /= length; uy /= length; uz /= length;
        float yaw = actor.yaw;
        if (actor instanceof Npc npc && npc.ragdoll != 0 && g.lastBodies != null) {
            var body = g.lastBodies.ragdoll(npc.ragdoll); if (body != null) yaw = body.yaw;
        }
        float rx = (float) Math.cos(yaw), ry = 0, rz = (float) -Math.sin(yaw);
        float dot = rx * ux + rz * uz;
        rx -= ux * dot; ry -= uy * dot; rz -= uz * dot;
        length = (float) Math.sqrt(rx * rx + ry * ry + rz * rz);
        if (length < 1e-5f) { rx = 0; ry = -1; rz = 0; length = 1; }
        rx /= length; ry /= length; rz /= length;
        float half = 4 * Body.PX, near = 0, far = limit;
        float x = ox - pose[neck] - ux * half, y = oy - pose[neck + 1] - uy * half, z = oz - pose[neck + 2] - uz * half;
        for (int axis = 0; axis < 3; axis++) {
            float ax = axis == 0 ? rx : axis == 1 ? ux : ry * uz - rz * uy;
            float ay = axis == 0 ? ry : axis == 1 ? uy : rz * ux - rx * uz;
            float az = axis == 0 ? rz : axis == 1 ? uz : rx * uy - ry * ux;
            float origin = x * ax + y * ay + z * az, direction = dx * ax + dy * ay + dz * az;
            if (Math.abs(direction) < 1e-6f) { if (Math.abs(origin) > half + pad) return Float.POSITIVE_INFINITY; continue; }
            float a = (-half - pad - origin) / direction, b = (half + pad - origin) / direction;
            near = Math.max(near, Math.min(a, b)); far = Math.min(far, Math.max(a, b));
            if (near > far) return Float.POSITIVE_INFINITY;
        }
        return near;
    }

    /** Ray against the finite cylinder plus its two spherical caps, including parallel and inside starts. */
    static float capsule(float ox, float oy, float oz, float dx, float dy, float dz,
                         float ax, float ay, float az, float bx, float by, float bz, float radius, float limit) {
        float ux = bx - ax, uy = by - ay, uz = bz - az;
        float vx = ox - ax, vy = oy - ay, vz = oz - az;
        float uu = ux * ux + uy * uy + uz * uz, uv = ux * vx + uy * vy + uz * vz;
        float fraction = uu > 1e-8f ? Math.max(0, Math.min(1, uv / uu)) : 0;
        float qx = vx - fraction * ux, qy = vy - fraction * uy, qz = vz - fraction * uz;
        if (qx * qx + qy * qy + qz * qz <= radius * radius) return 0;
        float best = Math.min(sphere(vx, vy, vz, dx, dy, dz, radius), sphere(ox - bx, oy - by, oz - bz, dx, dy, dz, radius));
        if (uu > 1e-8f) {
            float ud = ux * dx + uy * dy + uz * dz, vd = vx * dx + vy * dy + vz * dz;
            float aa = uu - ud * ud, bb = uu * vd - uv * ud;
            float cc = uu * (vx * vx + vy * vy + vz * vz - radius * radius) - uv * uv;
            float discriminant = bb * bb - aa * cc;
            if (aa > 1e-8f && discriminant >= 0) {
                float t = (-bb - (float) Math.sqrt(discriminant)) / aa, along = uv + t * ud;
                if (t >= 0 && along >= 0 && along <= uu) best = Math.min(best, t);
            }
        }
        return best <= limit ? best : Float.POSITIVE_INFINITY;
    }

    private static float sphere(float x, float y, float z, float dx, float dy, float dz, float radius) {
        float b = x * dx + y * dy + z * dz, c = x * x + y * y + z * z - radius * radius, d = b * b - c;
        if (d < 0) return Float.POSITIVE_INFINITY;
        float t = -b - (float) Math.sqrt(d);
        return t >= 0 ? t : Float.POSITIVE_INFINITY;
    }
}
