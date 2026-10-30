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
    /**
     * The frame a lodged thing rides in: the anchor bone's start, a side axis, the bone's axis, and their cross.
     * (gamefixes: arrows in the edges of a body twitched) The side axis was the body's right projected off the bone - on
     * the shoulder line, which IS the body's right, that projection is next to nothing and flipped about from frame to
     * frame. The reference now leans continuously from the body's right to its front as the bone comes round to the
     * right, so it never degenerates. And a pose a moment stale is carried along with the body (as {@link #pose} does
     * while fresh) rather than dropped for one frame at the feet.
     */
    static float[] attachmentFrame(Rpg g, Actor actor, int anchorA, int anchorB) {
        float[] points=pose(g,actor);
        if(points==null && actor.ragdoll==0 && actor.posePts!=null && !actor.poseReset)points=carried(actor);
        float rx=(float)Math.cos(actor.yaw),rz=-(float)Math.sin(actor.yaw);
        if(points==null || anchorA<0)return new float[]{actor.x,actor.y,actor.z,rx,0,rz,0,1,0,rz,0,-rx};
        int a=anchorA*3,b=anchorB*3;
        float ux=points[b]-points[a],uy=points[b+1]-points[a+1],uz=points[b+2]-points[a+2];
        float length=(float)Math.sqrt(ux*ux+uy*uy+uz*uz);if(length<1e-6f){ux=0;uy=1;uz=0;length=1;}ux/=length;uy/=length;uz/=length;
        // the reference: the body's right, leaning to its front as the bone lines up with the right
        float fx=-(float)Math.sin(actor.yaw),fz=-(float)Math.cos(actor.yaw);
        float lean=smooth(0.55f,0.92f,Math.abs(rx*ux+rz*uz));
        float sx=rx*(1-lean)+fx*lean, sz=rz*(1-lean)+fz*lean;
        float dot=sx*ux+sz*uz, sy=-uy*dot; sx-=ux*dot; sz-=uz*dot;
        length=(float)Math.sqrt(sx*sx+sy*sy+sz*sz);if(length<1e-6f){sx=0;sy=-1;sz=0;length=1;}sx/=length;sy/=length;sz/=length;
        return new float[]{points[a],points[a+1],points[a+2],sx,sy,sz,ux,uy,uz,uy*sz-uz*sy,uz*sx-ux*sz,ux*sy-uy*sx};
    }
    private static float smooth(float e0, float e1, float v) { float t=Math.clamp((v-e0)/(e1-e0),0,1); return t*t*(3-2*t); }
    /** The last pose taken, however old, moved and turned with the body since. */
    private static float[] carried(Actor actor) {
        if (!Float.isFinite(actor.poseX)) return actor.posePts;
        float[] posed=actor.posePts.clone();
        float turn=actor.yaw-actor.poseYaw,c=(float)Math.cos(turn),s=(float)Math.sin(turn);
        for(int i=0;i<posed.length;i+=3) {
            float x=posed[i]-actor.poseX,z=posed[i+2]-actor.poseZ;
            posed[i]=actor.x+c*x+s*z; posed[i+1]+=actor.y-actor.poseY; posed[i+2]=actor.z-s*x+c*z;
        }
        return posed;
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
        if (actor instanceof rpg.monster.Monster mo && mo.kind.form != rpg.monster.MonsterKind.Form.HUMANOID) return beast(g, mo, ox, oy, oz, dx, dy, dz, limit, pad);
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
    private static float beast(Rpg g, rpg.monster.Monster mo, float ox, float oy, float oz, float dx, float dy, float dz, float limit, float pad) {
        float best = Float.POSITIVE_INFINITY;
        for (float[] z : zones(mo)) {
            if (g.gore != null && g.gore.lost(mo, Gore.Part.values()[(int) z[0]])) continue;
            best = Math.min(best, capsule(ox, oy, oz, dx, dy, dz, z[1], z[2], z[3], z[4], z[5], z[6], z[7] + pad, limit));
        }
        return best;
    }

    /**
     * (0.2.1, gamefixes: monsters were not dismemberable - one capsule each) a beast's parts, articulated: {part, ax, ay,
     * az, bx, by, bz, radius} - the trunk, the head out in front, a dragon's tail behind, the wings either side (the
     * right as ARM_R, the left as ARM_L). Struck, the nearest names the part; severed, its volume is gone.
     */
    static float[][] zones(rpg.monster.Monster mo) {
        var k = mo.kind;
        float fx = (float) -Math.sin(mo.yaw), fz = (float) -Math.cos(mo.yaw), rx = -fz, rz = fx, cy = mo.y + k.hh * 0.5f;
        float d = k.hd * 0.5f, w = k.hw * 0.5f, r = Math.min(k.hw, k.hh) * 0.5f;
        java.util.List<float[]> out = new java.util.ArrayList<>();
        java.util.function.Consumer<float[]> add = out::add;
        boolean dragon = k.form == rpg.monster.MonsterKind.Form.DRAGON, bat = k.form == rpg.monster.MonsterKind.Form.BAT;
        float body = dragon ? Math.min(k.hh * 0.45f, d * 0.3f) : bat ? Math.min(k.hh, d) * 0.45f : r;
        float b0 = dragon ? -0.3f * d : -0.45f * d, b1 = dragon ? 0.35f * d : bat ? 0.2f * d : 0.35f * d;
        add.accept(new float[]{Gore.Part.TORSO.ordinal(), mo.x + fx * b0, cy, mo.z + fz * b0, mo.x + fx * b1, cy, mo.z + fz * b1, body});
        float hr = dragon ? Math.min(k.hh * 0.22f, d * 0.14f) : Math.max(0.12f, body * 0.6f), h0 = b1 + hr * 0.4f, h1 = d - hr, hy = dragon ? cy + k.hh * 0.25f : cy;
        add.accept(new float[]{Gore.Part.HEAD.ordinal(), mo.x + fx * h0, hy, mo.z + fz * h0, mo.x + fx * Math.max(h0, h1), hy, mo.z + fz * Math.max(h0, h1), hr});
        if (dragon) add.accept(new float[]{Gore.Part.TAIL.ordinal(), mo.x + fx * b0, cy, mo.z + fz * b0, mo.x - fx * d, cy - k.hh * 0.2f, mo.z - fz * d, body * 0.45f});
        if (dragon || bat) {
            float wr = Math.max(0.06f, (dragon ? k.hh * 0.12f : body * 0.35f)), w0 = body * 0.9f, wy = cy + (dragon ? k.hh * 0.2f : 0);
            for (int side = 0; side < 2; side++) {
                float s = side == 0 ? 1 : -1;
                add.accept(new float[]{(side == 0 ? Gore.Part.ARM_R : Gore.Part.ARM_L).ordinal(), mo.x + rx * s * w0, wy, mo.z + rz * s * w0, mo.x + rx * s * w, wy, mo.z + rz * s * w, wr});
            }
        }
        return out.toArray(new float[0][]);
    }

    /** Which of a beast's zones a point is nearest (its surface, not its line). */
    static Gore.Part beastPart(rpg.monster.Monster mo, float x, float y, float z) {
        Gore.Part best = Gore.Part.TORSO; float nearest = Float.POSITIVE_INFINITY;
        for (float[] c : zones(mo)) {
            float ux = c[4] - c[1], uy = c[5] - c[2], uz = c[6] - c[3];
            float t = Math.max(0, Math.min(1, ((x - c[1]) * ux + (y - c[2]) * uy + (z - c[3]) * uz) / Math.max(1e-8f, ux * ux + uy * uy + uz * uz)));
            float d = (float) Math.sqrt(Math.pow(x - c[1] - ux * t, 2) + Math.pow(y - c[2] - uy * t, 2) + Math.pow(z - c[3] - uz * t, 2)) - c[7];
            if (d < nearest) { nearest = d; best = Gore.Part.values()[(int) c[0]]; }
        }
        return best;
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
