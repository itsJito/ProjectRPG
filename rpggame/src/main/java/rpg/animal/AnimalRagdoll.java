package rpg.animal;

import static astratto.bridge.AstrattoNative.*;

import astratto.bridge.Snapshots;
import java.util.ArrayList;
import java.util.List;
import rpg.Rpg;
import rpg.combat.Gore;
import rpg.render.AnimalModels;
import rpg.render.ItemModels;

/**
 * An animal's body handed to physics (gamefixes: animals ragdoll, yet keep their animations while they live). Its
 * figure's parts - the trunk, the head on its neck, each leg (or a bird's legs and wings), the tail - become rigid
 * bodies in Impulsis, the engine's physics, in exactly the pose the animation had them in and moving as the animal was,
 * joined where the figure joins them by cone-twist joints that bend only as far as that joint can. A killing blow is
 * thrown into it, so the body falls the way it was struck and settles as it lands; a heavy blow on a big animal knocks
 * it off its feet the same way for a moment, after which it is handed back to its animation where it lies, and gets up.
 * A part the animal has lost (Gore) is not made.
 */
public final class AnimalRagdoll {
    /** One part: its rigid body, the figure part drawn on it, and which part it is. */
    public record Piece(long body, ItemModels.Model model, Gore.Part part) {}

    public final List<Piece> pieces = new ArrayList<>();
    final List<Long> joints = new ArrayList<>();
    /** Knocked down (and to be handed back) rather than dead; when it came down. */
    public boolean knockdown;
    public final double since;

    private AnimalRagdoll(boolean knockdown, double since) { this.knockdown = knockdown; this.since = since; }

    /** The trunk: where the animal is while it lies in physics' hands. */
    public Piece trunk() { return pieces.get(0); }

    /**
     * Makes the ragdoll from the animal as it is drawn now, the impulse (N s, world) thrown into the trunk where the
     * blow landed; or null if the animal has no figure to make it from.
     */
    static AnimalRagdoll create(Rpg g, Animal a, float jx, float jy, float jz, boolean knockdown) {
        var f = AnimalModels.of(a.species, a.variant);
        if (f == null || f.body == null) return null;
        float vs = AnimalModels.PX * a.scale();
        AnimalRagdoll r = new AnimalRagdoll(knockdown, g.time);
        float standY = f.bodyY, lyingY = f.bird ? f.bh * 0.45f : f.bh * 0.5f;
        float oy = a.y + (standY + (lyingY - standY) * a.lieT) * vs;
        float cs = (float) Math.cos(a.yaw), sn = (float) Math.sin(a.yaw);
        float qw = (float) Math.cos(a.yaw / 2), qy = (float) Math.sin(a.yaw / 2);
        float mass = Math.max(0.6f, a.species.w * a.species.w * a.species.h * 420 * a.scale() * a.scale() * a.scale());
        var wounds = g.gore == null ? null : g.gore.wounds.get(a.id);
        long trunk = piece(g, r, a, f.body, Gore.Part.TORSO, 0, 0, 0, vs, oy, cs, sn, qw, qy, mass * 0.72f);
        if (trunk == 0) return null;
        // the parts, each at its joint on the figure (figure voxels, from the body's middle)
        List<Object[]> parts = new ArrayList<>();
        parts.add(new Object[]{f.head, Gore.Part.HEAD, f.neck, 0.08f, new float[]{0, 0, -1}, 0.6f, 0.4f});
        if (f.tail != null && f.tailAt != null) parts.add(new Object[]{f.tail, Gore.Part.TAIL, f.tailAt, 0.02f, new float[]{0, 0, 1}, 1.0f, 0.5f});
        if (f.bird) {
            for (int s = -1; s <= 1; s += 2) {
                parts.add(new Object[]{f.legFront, s > 0 ? Gore.Part.LEG_R : Gore.Part.LEG_L, new float[]{f.legF[0] * s * 0.6f, f.legF[1], f.legF[2]}, 0.03f, new float[]{0, -1, 0}, 0.7f, 0.3f});
                if (f.wing != null) parts.add(new Object[]{f.wing, s > 0 ? Gore.Part.ARM_R : Gore.Part.ARM_L, new float[]{f.wingAt[0] * s, f.wingAt[1], f.wingAt[2]}, 0.05f, new float[]{s, 0, 0}, 1.2f, 0.3f});
            }
        } else for (int s = -1; s <= 1; s += 2) {
            parts.add(new Object[]{f.legFront, s > 0 ? Gore.Part.ARM_R : Gore.Part.ARM_L, new float[]{f.legF[0] * s, f.legF[1], f.legF[2]}, 0.04f, new float[]{0, -1, 0}, 0.85f, 0.3f});
            parts.add(new Object[]{f.legBack, s > 0 ? Gore.Part.LEG_R : Gore.Part.LEG_L, new float[]{f.legB[0] * s, f.legB[1], f.legB[2]}, 0.04f, new float[]{0, -1, 0}, 0.85f, 0.3f});
        }
        for (Object[] p : parts) {
            ItemModels.Model md = (ItemModels.Model) p[0];
            Gore.Part gp = (Gore.Part) p[1];
            float[] j = (float[]) p[2];
            if (md == null || j == null || wounds != null && wounds.lost(gp)) continue;
            long b = piece(g, r, a, md, gp, j[0], j[1], j[2], vs, oy, cs, sn, qw, qy, mass * (float) p[3]);
            if (b == 0) continue;
            // the joint where the part meets the trunk, bending as far as that joint can
            float wx = a.x + (cs * j[0] + sn * j[2]) * vs, wy = oy + j[1] * vs, wz = a.z + (-sn * j[0] + cs * j[2]) * vs;
            float[] ax = (float[]) p[4];
            float axx = cs * ax[0] + sn * ax[2], axz = -sn * ax[0] + cs * ax[2];
            long jh = g.game.reserve(KIND_JOINT);
            g.game.commands().jointCreate(jh, trunk, b, JOINT_CONE_TWIST, JOINT_LIMITED, wx, wy, wz, axx, ax[1], axz, (float) p[5], (float) p[6], 0, mass * 0.002f, 0);
            r.joints.add(jh);
        }
        // the blow, and the way it was going - landed high on the trunk, so the body topples rather than slides; a dead
        // one also loses its legs (a sideways shove at the top: four stiff legs would otherwise stand like a table)
        float top = oy + f.bh * 0.45f * vs;
        if (jx != 0 || jy != 0 || jz != 0) g.game.commands().rigidImpulse(trunk, a.x, top, a.z, jx, jy, jz);
        if (knockdown) {
            // felled: the top of the body goes over the way it was struck (its feet stay where they were)
            float l = Math.max(1e-3f, (float) Math.hypot(jx, jz)), k = mass * 0.72f * 2.2f;
            g.game.commands().rigidImpulse(trunk, a.x, top, a.z, jx / l * k, 0, jz / l * k);
        } else {
            float side = ((a.id & 1) == 0 ? 1 : -1) * mass * 0.72f * 1.3f;
            g.game.commands().rigidImpulse(trunk, a.x, top, a.z, cs * side, 0, -sn * side);
        }
        return r;
    }

    /** One part as a rigid box the size of its figure part, centred where the part's middle is drawn now. */
    private static long piece(Rpg g, AnimalRagdoll r, Animal a, ItemModels.Model md, Gore.Part gp, float jx, float jy, float jz, float vs, float oy,
                              float cs, float sn, float qw, float qy, float mass) {
        if (md == null) return 0;
        var vm = md.vox();
        // the part's middle in the figure (voxels): its joint, plus the mesh's middle from its pivot
        float cx = jx + (vm.sx / 2f - md.gx()), cy = jy + (vm.sy / 2f - md.gy()), cz = jz + (vm.sz / 2f - md.gz());
        float x = a.x + (cs * cx + sn * cz) * vs, y = oy + cy * vs, z = a.z + (-sn * cx + cs * cz) * vs;
        float hx = Math.max(0.02f, vm.sx * vs / 2), hy = Math.max(0.02f, vm.sy * vs / 2), hz = Math.max(0.02f, vm.sz * vs / 2);
        long b = g.game.reserve(KIND_RIGID);
        // (knocked down, the animal's own mover is still there: the pieces must not be shoved by it)
        g.game.commands().rigidCreate(b, SHAPE_BOX, RIGID_DEBRIS | (r.knockdown ? RIGID_NO_MOVERS : 0), hx, hy, hz, Math.max(0.05f, mass), x, y, z, qw, 0, qy, 0,
                a.vx, 0, a.vz, 0, 0, 0, 0.8f, 0.02f, 0, null);
        r.pieces.add(new Piece(b, md, gp));
        return b;
    }

    /** Where the trunk is now (from the last frame's physics), or null before the first. */
    public float[] trunkAt(Rpg g) {
        Snapshots.Rigid t = g.lastRigids.body(trunk().body());
        return t == null ? null : new float[]{t.x, t.y, t.z, t.qw, t.qx, t.qy, t.qz};
    }

    /** Whether everything has come to rest. */
    public boolean asleep(Rpg g) {
        for (Piece p : pieces) { Snapshots.Rigid r = g.lastRigids.body(p.body()); if (r == null || !r.asleep) return false; }
        return true;
    }

    void release(Rpg g) {
        for (long j : joints) g.game.commands().release(j);
        for (Piece p : pieces) g.game.commands().release(p.body());
        joints.clear();
        pieces.clear();
    }
}
