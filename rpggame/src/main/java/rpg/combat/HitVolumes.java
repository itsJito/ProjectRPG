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

    static float[] pose(Rpg g, Actor actor) {
        if (actor.ragdoll != 0 && g.lastBodies != null) {
            var body = g.lastBodies.ragdoll(actor.ragdoll);
            return body == null ? null : body.p;
        }
        if (actor.posePts == null || actor.poseReset || g.time-actor.poseAt >= 0.2) return null;
        if (!Float.isFinite(actor.poseX)) return actor.posePts;
        float[] posed=actor.posePts.clone();
        float turn=actor.yaw-actor.poseYaw,c=(float)Math.cos(turn),s=(float)Math.sin(turn);
        for(int i=0;i<posed.length;i+=3) {
            float x=posed[i]-actor.poseX,z=posed[i+2]-actor.poseZ;
            posed[i]=actor.x+c*x+s*z; posed[i+1]+=actor.y-actor.poseY; posed[i+2]=actor.z-s*x+c*z;
        }
        return posed;
    }

    static int[] attachment(Rpg g, Actor actor, float x, float y, float z) {
        float[] points=pose(g,actor);
        if(points==null || actor instanceof rpg.animal.Animal)return new int[]{-1,-1};
        if(part(g,actor,x,y,z)==Gore.Part.HEAD)return new int[]{RD_NECK,RD_HEAD};
        int[] best=BONES[0]; float nearest=Float.POSITIVE_INFINITY;
        for(int[] bone:BONES) {
            int a=bone[0]*3,b=bone[1]*3;
            float ux=points[b]-points[a],uy=points[b+1]-points[a+1],uz=points[b+2]-points[a+2];
            float t=Math.clamp(((x-points[a])*ux+(y-points[a+1])*uy+(z-points[a+2])*uz)/Math.max(1e-8f,ux*ux+uy*uy+uz*uz),0,1);
            float d=(float)Math.sqrt(Math.pow(x-points[a]-ux*t,2)+Math.pow(y-points[a+1]-uy*t,2)+Math.pow(z-points[a+2]-uz*t,2));
            if(d<nearest){nearest=d;best=bone;}
        }
        return best;
    }
    static float[] attachmentFrame(Rpg g, Actor actor, int anchorA, int anchorB) {
        float[] points=pose(g,actor);
        float rx=(float)Math.cos(actor.yaw),rz=-(float)Math.sin(actor.yaw);
        if(points==null || anchorA<0)return new float[]{actor.x,actor.y,actor.z,rx,0,rz,0,1,0,rz,0,-rx};
        int a=anchorA*3,b=anchorB*3;
        float ux=points[b]-points[a],uy=points[b+1]-points[a+1],uz=points[b+2]-points[a+2];
        float length=(float)Math.sqrt(ux*ux+uy*uy+uz*uz);if(length<1e-6f){ux=0;uy=1;uz=0;length=1;}ux/=length;uy/=length;uz/=length;
        float dot=rx*ux+rz*uz, ry=-uy*dot;rx-=ux*dot;rz-=uz*dot;
        length=(float)Math.sqrt(rx*rx+ry*ry+rz*rz);if(length<1e-6f){rx=0;ry=-1;rz=0;length=1;}rx/=length;ry/=length;rz/=length;
        return new float[]{points[a],points[a+1],points[a+2],rx,ry,rz,ux,uy,uz,uy*rz-uz*ry,uz*rx-ux*rz,ux*ry-uy*rx};
    }

    static Gore.Part part(Rpg g, Actor actor, float x, float y, float z) {
        float[] points = pose(g, actor);
        if (points == null || actor instanceof rpg.animal.Animal) return null;
        int head = RD_HEAD * 3;
        float dx = x - points[head], dy = y - points[head + 1], dz = z - points[head + 2];
        float k = actor.figureScale();
        float nearest = (float) Math.sqrt(dx * dx + dy * dy + dz * dz) - 4.5f * Body.PX * k;
        Gore.Part part = Gore.Part.HEAD;
        if (g.gore.lost(actor, part)) nearest = Float.POSITIVE_INFINITY;
        for (int i = 0; i < BONES.length; i++) {
            if (g.gore.lost(actor, PARTS[i])) continue;
            int a = BONES[i][0] * 3, b = BONES[i][1] * 3;
            float ux = points[b] - points[a], uy = points[b + 1] - points[a + 1], uz = points[b + 2] - points[a + 2];
            float length = ux * ux + uy * uy + uz * uz;
            float t = Math.max(0, Math.min(1, ((x - points[a]) * ux + (y - points[a + 1]) * uy + (z - points[a + 2]) * uz) / Math.max(1e-8f, length)));
            dx = x - points[a] - ux * t; dy = y - points[a + 1] - uy * t; dz = z - points[a + 2] - uz * t;
            float distance = (float) Math.sqrt(dx * dx + dy * dy + dz * dz) - (i < 2 ? 3.6f : 2) * Body.PX * k;
            if (distance < nearest) { nearest = distance; part = PARTS[i]; }
        }
        return part;
    }

    /** Distance along a normalized ray, or infinity. No hit against the old standing capsule while ragdolled. */
    static float ray(Rpg g, Actor actor, float ox, float oy, float oz, float dx, float dy, float dz, float limit, float pad) {
        if (actor instanceof rpg.monster.Monster mo && mo.kind.form != rpg.monster.MonsterKind.Form.HUMANOID) return beast(mo, ox, oy, oz, dx, dy, dz, limit, pad);
        float[] pose = pose(g, actor);
        if (pose == null || actor instanceof rpg.animal.Animal) {
            float r = actor.radius + pad, height = actor.height * (actor.sneaking || actor.crouched ? rpg.actor.Body.CROUCH_HEIGHT : 1);
            if (actor instanceof Npc && !(actor instanceof rpg.monster.Monster) || actor == g.player) {
                // Without a recent rig pose, the mover's wide rounded cap must never stand in for a human head.
                float neck=actor.y+height-Body.HEAD*Body.PX;
                float[] standing=new float[RD_PARTICLES*3];
                standing[RD_NECK*3]=standing[RD_HEAD*3]=actor.x;
                standing[RD_NECK*3+1]=neck;standing[RD_HEAD*3+1]=neck+5*Body.PX;
                standing[RD_NECK*3+2]=standing[RD_HEAD*3+2]=actor.z;
                float head=g.gore.lost(actor,Gore.Part.HEAD)?Float.POSITIVE_INFINITY:head(g,actor,standing,ox,oy,oz,dx,dy,dz,limit,pad);
                return Math.min(head,capsule(ox,oy,oz,dx,dy,dz,actor.x,actor.y+r,actor.z,actor.x,Math.max(actor.y+r,neck-r),actor.z,r,limit));
            }
            return capsule(ox, oy, oz, dx, dy, dz, actor.x, actor.y + r, actor.z,
                    actor.x, actor.y + Math.max(r, height - r), actor.z, r, limit);
        }
        // The head is a separate volume, not the narrow upper end of the torso collider.
        float best = g.gore.lost(actor, Gore.Part.HEAD) ? Float.POSITIVE_INFINITY
                : head(g, actor, pose, ox, oy, oz, dx, dy, dz, limit, pad);
        for (int i = 0; i < BONES.length; i++) {
            if (g.gore.lost(actor, PARTS[i])) continue;
            int a = BONES[i][0] * 3, b = BONES[i][1] * 3;
            float radius = (i < 2 ? 3.6f : 2f) * Body.PX * actor.figureScale() + pad;
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
        float half = 4 * Body.PX * actor.figureScale(), near = 0, far = limit;
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

    /**
     * A bat, a spider or a dragon: its hitbox (the gamefixes' width, height and depth) as a capsule lying along its
     * body, from tail to head, as thick as it is high - so a dragon is struck along its length, not in a column.
     */
    private static float beast(rpg.monster.Monster mo, float ox, float oy, float oz, float dx, float dy, float dz, float limit, float pad) {
        var k = mo.kind;
        float r = Math.min(k.hw, k.hh) * 0.5f + pad, half = Math.max(0, k.hd * 0.5f - r + pad);
        float fx = (float) -Math.sin(mo.yaw), fz = (float) -Math.cos(mo.yaw), cy = mo.y + k.hh * 0.5f;
        float wide = Math.max(0, k.hw * 0.5f - r);
        // as wide as it is long (a bat's spread wings): a second capsule across it
        float along = capsule(ox, oy, oz, dx, dy, dz, mo.x - fx * half, cy, mo.z - fz * half, mo.x + fx * half, cy, mo.z + fz * half, r, limit);
        if (wide <= 0.05f) return along;
        float rx = -fz, rz = fx;
        return Math.min(along, capsule(ox, oy, oz, dx, dy, dz, mo.x - rx * wide, cy, mo.z - rz * wide, mo.x + rx * wide, cy, mo.z + rz * wide, r, limit));
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
