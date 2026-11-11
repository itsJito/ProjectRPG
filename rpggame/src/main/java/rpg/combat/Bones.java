package rpg.combat;

import astratto.bridge.AstrattoNative;
import rpg.Rpg;
import rpg.item.ItemDef;
import rpg.item.ItemStack;
import rpg.monster.Monster;
import rpg.render.MonsterModels;
import rpg.world.DevMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * (0.3.8, gamefixes: a skeleton should fall apart when it dies) A body held together by nothing but its own will comes
 * apart the moment that goes: every bone of its figure - the skull and the jaw, the ribs ring by ring, the spine, the
 * pelvis, each arm and leg bone and each foot - becomes a physical body of its own where that bone stood (from the pose
 * it died in), carrying the body's motion and the blow's, tumbling, clattering where it strikes the ground or one
 * another, and coming to rest as a scatter of bones. The pieces are cut from the kind's own figure (MonsterModels.pieces),
 * so whatever figure a kind that falls apart has, it falls apart into. Its belongings stay with the pile to be looted;
 * what it held falls from its hands. The bones go with the rest of its remains.
 */
public final class Bones {
    public static final int MATERIAL_BONE = 5;
    /** The most loose bones in the world at once (a cap, oldest first): enough for several skeletons at a time. */
    public static final int MAX = 160;

    /** One loose bone: its rigid body, whose it was, the piece of figure it is and that figure's scale. */
    public static final class Bone {
        public long body;
        public final long owner;
        public final MonsterModels.Piece piece;
        public final float vs;
        Bone(long body, long owner, MonsterModels.Piece piece, float vs) { this.body = body; this.owner = owner; this.piece = piece; this.vs = vs; }
    }

    private final Rpg g;
    public final List<Bone> bones = new ArrayList<>();
    private final Random rnd = new Random(0xB0E5);
    /** How many bodies have come apart (for tests and the record). */
    public int collapsed;

    public Bones(Rpg g) { this.g = g; }

    /**
     * The body comes apart where it stands. (pushX, pushZ): the way the last blow drove it (unit or zero); force: how hard
     * (1 a sword's blow). Its pose is the one it was last drawn in, or - never drawn (far off, or a run with no window) -
     * its kind's standing pose.
     */
    public void collapse(Monster m, float pushX, float pushZ, float force) {
        MonsterModels.Fig f = MonsterModels.of(m.kind, m.breed);
        if (!f.humanoid) return;
        float[] pts = posed(m, f);
        float vs = f.vs;
        float[] right = norm(sub(at(pts, AstrattoNative.RD_R_SHOULDER), at(pts, AstrattoNative.RD_L_SHOULDER)));
        if (len(right) < 0.5f) right = new float[]{(float) Math.cos(m.yaw), 0, (float) -Math.sin(m.yaw)};
        float[] pelvis = at(pts, AstrattoNative.RD_PELVIS);
        for (MonsterModels.Piece p : MonsterModels.pieces(f)) {
            float[][] fr = frame(p, f, pts, right);
            if (fr == null) continue;
            float ox = p.cx(), oy = p.cy(), oz = p.cz();
            float[] o = fr[0], x = fr[1], y = fr[2], z = fr[3];
            float wx = o[0] + (x[0] * ox + y[0] * oy + z[0] * oz) * vs;
            float wy = o[1] + (x[1] * ox + y[1] * oy + z[1] * oz) * vs;
            float wz = o[2] + (x[2] * ox + y[2] * oy + z[2] * oz) * vs;
            wy = Math.max(wy, DevMap.FLOOR + p.hy() * vs);
            float[] q = quat(x, y, z);
            // its own motion: the body's, the blow's, and outward from the middle as it loses its shape
            float outX = wx - pelvis[0], outZ = wz - pelvis[2], outL = (float) Math.max(0.05, Math.hypot(outX, outZ));
            float kick = force * (0.6f + rnd.nextFloat() * 0.9f);
            float vx = m.vx + pushX * kick + outX / outL * (0.9f + rnd.nextFloat() * 1.3f) + (rnd.nextFloat() - 0.5f) * 0.8f;
            float vz = m.vz + pushZ * kick + outZ / outL * (0.9f + rnd.nextFloat() * 1.3f) + (rnd.nextFloat() - 0.5f) * 0.8f;
            float vy = (wy - DevMap.FLOOR) * 0.4f + rnd.nextFloat() * 0.8f;
            float mass = Math.max(0.06f, Math.min(2.5f, p.voxels() * vs * vs * vs * 1100));
            long body = g.game.reserve(AstrattoNative.KIND_RIGID);
            g.game.commands().rigidCreate(body, AstrattoNative.SHAPE_BOX, AstrattoNative.RIGID_DEBRIS | AstrattoNative.RIGID_REPORT | AstrattoNative.RIGID_NO_MOVERS,
                    Math.max(0.015f, p.hx() * vs), Math.max(0.015f, p.hy() * vs), Math.max(0.015f, p.hz() * vs), mass,
                    wx, wy, wz, q[0], q[1], q[2], q[3], vx, vy, vz,
                    (rnd.nextFloat() - 0.5f) * 9, (rnd.nextFloat() - 0.5f) * 9, (rnd.nextFloat() - 0.5f) * 9, 0.6f, 0.3f, MATERIAL_BONE, null);
            bones.add(new Bone(body, m.id, p, vs));
        }
        // what it held falls from its hands as it comes apart
        for (ItemDef.Slot s : new ItemDef.Slot[]{ItemDef.Slot.MAIN, ItemDef.Slot.OFF}) {
            ItemStack held = m.eq.get(s);
            if (held == null || held.def.natural) continue;
            float[] hand = at(pts, s == ItemDef.Slot.MAIN ? AstrattoNative.RD_R_WRIST : AstrattoNative.RD_L_WRIST);
            m.eq.set(s, null);
            g.objects.drop(held, hand[0], Math.max(DevMap.FLOOR + 0.05f, hand[1] - 0.3f), hand[2]);
        }
        while (bones.size() > MAX) release(bones.remove(0));
        collapsed++;
        if (g.sfx != null) g.sfx.play("wood.heavy", pelvis[0], pelvis[1], pelvis[2], 0.7f, 1.45f, 22);
        g.state.commit("FellApart", 0, m.id, DevMap.jurisdiction(m.x, m.z), 0, m.kind.title);
    }

    /** Whether a body has come apart (its bones are loose in the world). */
    public boolean apart(long owner) { for (Bone b : bones) if (b.owner == owner) return true; return false; }

    /** The bones of remains that have gone go with them; the pile's middle is where its belongings are looted from. */
    public void update() {
        for (var it = bones.iterator(); it.hasNext(); ) {
            Bone b = it.next();
            Monster m = g.monsters.byId(b.owner);
            var r = g.lastRigids.body(b.body);
            if (m == null || m.alive || r != null && r.y < 0) { release(b); it.remove(); }
        }
        for (Monster m : g.monsters.all()) {
            if (m.alive || m.corpse == 0 || !(g.objects.get(m.corpse) instanceof rpg.objects.Corpse c)) continue;
            float sx = 0, sz = 0, sy = 1e9f; int n = 0;
            for (Bone b : bones) {
                if (b.owner != m.id) continue;
                var r = g.lastRigids.body(b.body);
                if (r == null) continue;
                sx += r.x; sz += r.z; sy = Math.min(sy, r.y); n++;
            }
            if (n == 0) continue;
            c.x = sx / n; c.z = sz / n; c.y = sy + 0.1f;
            m.x = c.x; m.z = c.z;
        }
    }

    /** A bone struck something: a dry clack, lighter and higher than wood, quieter as it slows. */
    public void clatter(float x, float y, float z, float speed) {
        if (g.sfx != null && speed > 0.9f) g.sfx.play(speed > 3 ? "wood" : "wood.light", x, y, z, Math.min(0.45f, speed * 0.07f), 1.55f + rnd.nextFloat() * 0.35f, 16);
    }

    public void clear() { for (Bone b : bones) release(b); bones.clear(); }

    private void release(Bone b) { if (b != null && b.body != 0) { g.game.commands().release(b.body); b.body = 0; } }

    // ------------------------------------------------------------------ the pose and the bones' frames
    /** The fifteen pose points it died in, or its kind's standing pose where it stands. */
    static float[] posed(Monster m, MonsterModels.Fig f) {
        if (m.posePts != null && m.posePts.length >= AstrattoNative.RD_PARTICLES * 3 && m.poseAt > 0) return m.posePts.clone();
        float vs = f.vs;
        float[] p = new float[AstrattoNative.RD_PARTICLES * 3];
        float rx = (float) Math.cos(m.yaw), rz = (float) -Math.sin(m.yaw);
        float px = m.x, py = Math.max(DevMap.FLOOR, m.y) + f.hipY * vs, pz = m.z;
        put(p, AstrattoNative.RD_PELVIS, px, py, pz);
        put(p, AstrattoNative.RD_NECK, px, py + f.neck[1] * vs, pz);
        put(p, AstrattoNative.RD_HEAD, px, py + (f.neck[1] + f.headHalf * 2) * vs, pz);
        for (int s = 0; s < 2; s++) {
            float side = s == 0 ? 1 : -1;
            int sh = s == 0 ? AstrattoNative.RD_R_SHOULDER : AstrattoNative.RD_L_SHOULDER;
            float sx = px + rx * side * f.shoulder[0] * vs, sy = py + f.shoulder[1] * vs, sz = pz + rz * side * f.shoulder[0] * vs;
            put(p, sh, sx, sy, sz);
            put(p, sh + 1, sx, sy - f.upperArm * vs, sz);
            put(p, sh + 2, sx, sy - (f.upperArm + f.foreArm) * vs, sz);
            int hp = s == 0 ? AstrattoNative.RD_R_HIP : AstrattoNative.RD_L_HIP;
            float hx = px + rx * side * f.hip[0] * vs, hy = py + f.hip[1] * vs, hz = pz + rz * side * f.hip[0] * vs;
            put(p, hp, hx, hy, hz);
            put(p, hp + 1, hx, hy - f.thigh * vs, hz);
            put(p, hp + 2, hx, hy - (f.thigh + f.shin) * vs, hz);
        }
        return p;
    }

    /**
     * The frame a piece's part is drawn in (as the renderer draws a fallen humanoid): its origin and its x, y, z axes.
     * A torso and a head stand up from their joint; an arm or a leg bone hangs down from its own.
     */
    static float[][] frame(MonsterModels.Piece p, MonsterModels.Fig f, float[] pts, float[] right) {
        int side = p.side();
        return switch (p.part()) {
            case TORSO -> between(at(pts, AstrattoNative.RD_PELVIS), at(pts, AstrattoNative.RD_NECK), right, true);
            case HEAD -> between(at(pts, AstrattoNative.RD_NECK), at(pts, AstrattoNative.RD_HEAD), right, true);
            case ARM_UP -> { int s = side > 0 ? AstrattoNative.RD_R_SHOULDER : AstrattoNative.RD_L_SHOULDER; yield between(at(pts, s), at(pts, s + 1), right, false); }
            case ARM_LO -> { int s = side > 0 ? AstrattoNative.RD_R_SHOULDER : AstrattoNative.RD_L_SHOULDER; yield between(at(pts, s + 1), at(pts, s + 2), right, false); }
            case LEG_UP -> { int h = side > 0 ? AstrattoNative.RD_R_HIP : AstrattoNative.RD_L_HIP; yield between(at(pts, h), at(pts, h + 1), right, false); }
            case LEG_LO -> { int h = side > 0 ? AstrattoNative.RD_R_HIP : AstrattoNative.RD_L_HIP; yield between(at(pts, h + 1), at(pts, h + 2), right, false); }
            case FOOT -> {
                int h = side > 0 ? AstrattoNative.RD_R_HIP : AstrattoNative.RD_L_HIP;
                float[][] lo = between(at(pts, h + 1), at(pts, h + 2), right, false);
                // down the shin to the ankle, then turned forward as the renderer turns a foot
                float[] o = lo[0], y = lo[2], z = lo[3];
                float[] ankle = {o[0] - y[0] * f.shin * f.vs, o[1] - y[1] * f.shin * f.vs, o[2] - y[2] * f.shin * f.vs};
                float c = (float) Math.cos(-0.6f), s = (float) Math.sin(-0.6f);
                float[] ny = {y[0] * c + z[0] * s, y[1] * c + z[1] * s, y[2] * c + z[2] * s};
                float[] nz = {-y[0] * s + z[0] * c, -y[1] * s + z[1] * c, -y[2] * s + z[2] * c};
                yield new float[][]{ankle, lo[1], ny, nz};
            }
        };
    }

    private static float[][] between(float[] a, float[] b, float[] ref, boolean up) {
        float[] y = norm(up ? sub(b, a) : sub(a, b));
        if (len(y) < 0.5f) y = new float[]{0, 1, 0};
        float d = ref[0] * y[0] + ref[1] * y[1] + ref[2] * y[2];
        float[] x = norm(new float[]{ref[0] - y[0] * d, ref[1] - y[1] * d, ref[2] - y[2] * d});
        if (len(x) < 0.5f) x = Math.abs(y[0]) < 0.9f ? norm(new float[]{1 - y[0] * y[0], -y[0] * y[1], -y[0] * y[2]}) : norm(new float[]{-y[2] * y[0], -y[2] * y[1], 1 - y[2] * y[2]});
        float[] z = {x[1] * y[2] - x[2] * y[1], x[2] * y[0] - x[0] * y[2], x[0] * y[1] - x[1] * y[0]};
        return new float[][]{a, x, y, z};
    }

    /** The turn (w, x, y, z) whose axes are x, y, z (a right-handed orthonormal frame). */
    static float[] quat(float[] x, float[] y, float[] z) {
        float m00 = x[0], m10 = x[1], m20 = x[2], m01 = y[0], m11 = y[1], m21 = y[2], m02 = z[0], m12 = z[1], m22 = z[2];
        float tr = m00 + m11 + m22, w, qx, qy, qz;
        if (tr > 0) { float s = (float) Math.sqrt(tr + 1) * 2; w = 0.25f * s; qx = (m21 - m12) / s; qy = (m02 - m20) / s; qz = (m10 - m01) / s; }
        else if (m00 > m11 && m00 > m22) { float s = (float) Math.sqrt(1 + m00 - m11 - m22) * 2; w = (m21 - m12) / s; qx = 0.25f * s; qy = (m01 + m10) / s; qz = (m02 + m20) / s; }
        else if (m11 > m22) { float s = (float) Math.sqrt(1 + m11 - m00 - m22) * 2; w = (m02 - m20) / s; qx = (m01 + m10) / s; qy = 0.25f * s; qz = (m12 + m21) / s; }
        else { float s = (float) Math.sqrt(1 + m22 - m00 - m11) * 2; w = (m10 - m01) / s; qx = (m02 + m20) / s; qy = (m12 + m21) / s; qz = 0.25f * s; }
        float l = (float) Math.sqrt(w * w + qx * qx + qy * qy + qz * qz);
        return l < 1e-6f ? new float[]{1, 0, 0, 0} : new float[]{w / l, qx / l, qy / l, qz / l};
    }

    private static float[] at(float[] p, int i) { return new float[]{p[i * 3], p[i * 3 + 1], p[i * 3 + 2]}; }
    private static void put(float[] p, int i, float x, float y, float z) { p[i * 3] = x; p[i * 3 + 1] = y; p[i * 3 + 2] = z; }
    private static float[] sub(float[] a, float[] b) { return new float[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]}; }
    private static float len(float[] v) { return (float) Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]); }
    private static float[] norm(float[] v) { float l = len(v); return l < 1e-6f ? new float[]{0, 0, 0} : new float[]{v[0] / l, v[1] / l, v[2] / l}; }
}
