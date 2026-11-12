package rpg.objects;

import astratto.bridge.AstrattoNative;
import astratto.voxel.VoxModel;
import rpg.Rpg;
import rpg.actor.Actor;
import rpg.item.ItemDef;
import rpg.item.ItemStack;
import rpg.item.Items;
import rpg.world.DevMap;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * (0.3.8, gamefixes) The breakable wooden things in the world (see {@link Breakable}): their rigid bodies, the blows
 * they take - from blades, clubs and fists (CombatSystem's swing), arrows, bolts, thrown things and spells (its
 * projectiles), and from hitting the ground or being hit by another body hard (Impulsis' impacts) - and the pieces they
 * come apart into. All of it is the world's: a broken crate stays broken, its boards lie where they fell for a while,
 * and its spilled goods are where they spilled.
 */
public final class Breakables {
    public static final int MATERIAL_WOOD = 6;
    /** Debris lies this long (s) before it goes, the oldest first past the cap. */
    public static final float DEBRIS_LIFE = 120;
    public static final int MAX_DEBRIS = 160;

    /** A piece of one that came apart: its body, its own model, when it fell. */
    public static final class Piece {
        public long body;
        public final VoxModel vox;
        public final float cx, cy, cz;
        public int gpu, gpuMetal;
        public final double born;
        Piece(long body, VoxModel vox, float cx, float cy, float cz, double born) { this.body = body; this.vox = vox; this.cx = cx; this.cy = cy; this.cz = cz; this.born = born; }
    }

    private final Rpg g;
    public final List<Breakable> all = new ArrayList<>();
    public final ArrayDeque<Piece> debris = new ArrayDeque<>();
    private final Map<Long, Breakable> byBody = new HashMap<>();
    private final Random rnd = new Random(0x5B11);
    /** How many have come apart (tests, the record). */
    public int burst;

    public Breakables(Rpg g) { this.g = g; }

    /** A new one placed in the world (a fixture, or made). */
    public Breakable place(Breakable b) {
        g.objects.add(b);
        all.add(b);
        respawn(b);
        return b;
    }

    /** Its body made (again) where and how it lies; none for one that is broken. */
    public void respawn(Breakable b) {
        if (b.body != 0) { byBody.remove(b.body); g.game.commands().release(b.body); b.body = 0; }
        if (b.broken || g.game == null) return;
        if (!all.contains(b)) all.add(b);
        b.body = g.game.reserve(AstrattoNative.KIND_RIGID);
        g.game.commands().rigidCreate(b.body, AstrattoNative.SHAPE_BOX, AstrattoNative.RIGID_REPORT,
                b.form.hx(), b.form.hy(), b.form.hz(), b.form.mass, b.x, b.y, b.z, b.qw, b.qx, b.qy, b.qz,
                0, 0, 0, 0, 0, 0, 0.7f, 0.12f, MATERIAL_WOOD, null);
        byBody.put(b.body, b);
    }

    /** Where each lies now (its body's pose), and old debris cleared. */
    public void update() {
        for (Breakable b : all) {
            if (b.broken || b.body == 0) continue;
            var r = g.lastRigids.body(b.body);
            if (r == null) continue;
            b.x = r.x; b.y = r.y; b.z = r.z; b.qw = r.qw; b.qx = r.qx; b.qy = r.qy; b.qz = r.qz;
            // fallen out of the world (into the void under it): gone
            if (r.y < -4) { b.broken = true; b.removed = true; release(b); }
        }
        while (!debris.isEmpty() && (g.time - debris.peekFirst().born > DEBRIS_LIFE || debris.size() > MAX_DEBRIS)) release(debris.pollFirst());
    }

    public Breakable byBody(long body) { return byBody.get(body); }

    // ------------------------------------------------------------------ finding them along a line
    /** What a ray meets first among them within limit: {the breakable, distance, local voxel x, y, z} or null. */
    public Object[] ray(float ox, float oy, float oz, float dx, float dy, float dz, float limit) {
        Object[] best = null;
        float bestT = limit;
        for (Breakable b : all) {
            if (b.broken) continue;
            float cx = ox - b.x, cy = oy - b.y, cz = oz - b.z;
            float reachR = Math.max(b.form.hx(), Math.max(b.form.hy(), b.form.hz())) * 1.75f;
            // a quick reject: the ray's nearest pass by its middle
            float along = cx * -dx + cy * -dy + cz * -dz;
            float px = cx + dx * along, py = cy + dy * along, pz = cz + dz * along;
            if (px * px + py * py + pz * pz > reachR * reachR || along < -reachR || along > bestT + reachR) continue;
            // into its own frame (the inverse turn), in voxels from its corner
            float[] lo = local(b, cx, cy, cz), ld = local(b, dx, dy, dz);
            float s = 1 / Breakable.VOXEL;
            float lx = lo[0] * s + b.form.sx / 2f, ly = lo[1] * s + b.form.sy / 2f, lz = lo[2] * s + b.form.sz / 2f;
            float[] hit = march(b, lx, ly, lz, ld[0], ld[1], ld[2], bestT * s);
            if (hit != null && hit[0] * Breakable.VOXEL < bestT) { bestT = hit[0] * Breakable.VOXEL; best = new Object[]{b, bestT, (int) hit[1], (int) hit[2], (int) hit[3]}; }
        }
        return best;
    }

    /** Through its voxel grid (a DDA) from a point outside or in it, to the first voxel still there: {t, x, y, z} in voxels. */
    private static float[] march(Breakable b, float ox, float oy, float oz, float dx, float dy, float dz, float limit) {
        int sx = b.form.sx, sy = b.form.sy, sz = b.form.sz;
        // to where the line enters its box
        float t0 = 0, t1 = limit;
        float[] o = {ox, oy, oz}, d = {dx, dy, dz}, hi = {sx, sy, sz};
        for (int k = 0; k < 3; k++) {
            if (Math.abs(d[k]) < 1e-6f) { if (o[k] < 0 || o[k] > hi[k]) return null; continue; }
            float a = (0 - o[k]) / d[k], c = (hi[k] - o[k]) / d[k];
            t0 = Math.max(t0, Math.min(a, c)); t1 = Math.min(t1, Math.max(a, c));
        }
        if (t0 > t1) return null;
        float t = t0 + 1e-4f;
        int x = (int) Math.floor(ox + dx * t), y = (int) Math.floor(oy + dy * t), z = (int) Math.floor(oz + dz * t);
        int stepX = dx > 0 ? 1 : -1, stepY = dy > 0 ? 1 : -1, stepZ = dz > 0 ? 1 : -1;
        float tdx = Math.abs(dx) < 1e-6f ? Float.MAX_VALUE : Math.abs(1 / dx), tdy = Math.abs(dy) < 1e-6f ? Float.MAX_VALUE : Math.abs(1 / dy), tdz = Math.abs(dz) < 1e-6f ? Float.MAX_VALUE : Math.abs(1 / dz);
        float nx = Math.abs(dx) < 1e-6f ? Float.MAX_VALUE : ((dx > 0 ? x + 1 : x) - (ox + dx * t)) / dx + t;
        float ny = Math.abs(dy) < 1e-6f ? Float.MAX_VALUE : ((dy > 0 ? y + 1 : y) - (oy + dy * t)) / dy + t;
        float nz = Math.abs(dz) < 1e-6f ? Float.MAX_VALUE : ((dz > 0 ? z + 1 : z) - (oz + dz * t)) / dz + t;
        for (int step = 0; step < 64; step++) {
            if (!b.inside(x, y, z) || t > t1 + 1e-3f) return null;
            if (b.mat[b.index(x, y, z)] != Breakable.AIR) return new float[]{t, x, y, z};
            if (nx <= ny && nx <= nz) { t = nx; nx += tdx; x += stepX; }
            else if (ny <= nz) { t = ny; ny += tdy; y += stepY; }
            else { t = nz; nz += tdz; z += stepZ; }
        }
        return null;
    }

    /** A world vector into its own frame (the inverse of its turn). */
    static float[] local(Breakable b, float x, float y, float z) {
        float w = b.qw, qx = -b.qx, qy = -b.qy, qz = -b.qz;
        return rotate(w, qx, qy, qz, x, y, z);
    }
    /** A vector in its own frame out into the world. */
    static float[] world(Breakable b, float x, float y, float z) { return rotate(b.qw, b.qx, b.qy, b.qz, x, y, z); }
    private static float[] rotate(float w, float qx, float qy, float qz, float x, float y, float z) {
        // v' = v + 2w(q x v) + 2 q x (q x v)
        float cx = qy * z - qz * y, cy = qz * x - qx * z, cz = qx * y - qy * x;
        float ccx = qy * cz - qz * cy, ccy = qz * cx - qx * cz, ccz = qx * cy - qy * cx;
        return new float[]{x + 2 * (w * cx + ccx), y + 2 * (w * cy + ccy), z + 2 * (w * cz + ccz)};
    }

    // ------------------------------------------------------------------ blows
    /**
     * A blow lands on it at a voxel: damage (in hit points, as a blow on a body would deal), the way it was going (world,
     * unit), its kind (an edge splits, a point punches, a club or a fall staves in), the push it gives the body, and who.
     */
    public void strike(Breakable b, int vx, int vy, int vz, float damage, float dx, float dy, float dz, ItemDef.DamageType type, float push, Actor by) {
        if (b.broken || damage <= 0) return;
        float[] ld = local(b, dx, dy, dz);
        float ll = (float) Math.max(1e-4, Math.sqrt(ld[0] * ld[0] + ld[1] * ld[1] + ld[2] * ld[2]));
        ld[0] /= ll; ld[1] /= ll; ld[2] /= ll;
        // the blow's shape: a point goes deep and narrow, an edge long along its cut, a club wide and shallow
        boolean point = type == ItemDef.DamageType.PIERCE, blunt = type == null || type == ItemDef.DamageType.BLUNT || type == ItemDef.DamageType.NONE;
        float depth = point ? 1.5f + damage * 0.35f : blunt ? 0.8f + damage * 0.12f : 1 + damage * 0.2f;
        float radius = point ? 0.7f : blunt ? 1.2f + damage * 0.12f : 0.9f + damage * 0.08f;
        int reach = (int) Math.ceil(Math.max(depth, radius)) + 1;
        int before = b.lost;
        List<Integer> broke = new ArrayList<>();
        for (int x = vx - reach; x <= vx + reach; x++) for (int y = vy - reach; y <= vy + reach; y++) for (int z = vz - reach; z <= vz + reach; z++) {
            if (!b.inside(x, y, z)) continue;
            int i = b.index(x, y, z);
            if (b.mat[i] == Breakable.AIR) continue;
            float ox = x - vx, oy = y - vy, oz = z - vz;
            float in = ox * ld[0] + oy * ld[1] + oz * ld[2];
            if (in < -0.6f || in > depth) continue;
            float off = (float) Math.sqrt(Math.max(0, ox * ox + oy * oy + oz * oz - in * in));
            if (off > radius) continue;
            // the blow spreads along the board it lands on (the grain) and weakens with depth and with distance off its line
            boolean sameBoard = b.board[i] == b.board[b.index(vx, vy, vz)];
            float k = (1 - off / (radius + 0.5f)) * (1 - Math.max(0, in) / (depth + 0.5f)) * (sameBoard ? 1.25f : 0.8f);
            b.hp[i] -= damage * k;
            if (b.hp[i] <= 0) broke.add(i);
        }
        for (int i : broke) b.remove(i);
        int gone = b.lost - before;
        float px = b.x, py = b.y, pz = b.z;
        float[] wp = world(b, (vx + 0.5f - b.form.sx / 2f) * Breakable.VOXEL, (vy + 0.5f - b.form.sy / 2f) * Breakable.VOXEL, (vz + 0.5f - b.form.sz / 2f) * Breakable.VOXEL);
        px += wp[0]; py += wp[1]; pz += wp[2];
        if (g.fx != null && gone > 0) g.fx.chips(px, py, pz, Math.min(14, 2 + gone));
        if (g.sfx != null && g.time - b.struckAt > 0.05) g.sfx.play(damage > 6 ? "wood.heavy" : damage > 2.5f ? "wood" : "wood.light", px, py, pz, Math.min(0.9f, 0.3f + damage * 0.06f));
        b.struckAt = g.time;
        g.noise(by, px, py, pz, 6 + damage, 0.4f);
        // the push of it
        if (push > 0 && b.body != 0) g.game.commands().rigidImpulse(b.body, px, py, pz, dx * push, dy * push * 0.5f + push * 0.1f, dz * push);
        // a blow hard enough bursts it outright; enough broken and the rest gives
        if (damage >= b.form.burst || b.integrity() < 0.62f) breakApart(b, px, py, pz, dx, dy, dz, Math.max(1, damage / b.form.burst), by);
        else if (gone > 0 && by != null && b.owner != 0 && b.owner != by.id && b.integrity() < 0.9f && !reported.contains(b.id))
            vandal(b, by, false, g.state.commit("Damaged", by.id, b.id, DevMap.jurisdiction(b.x, b.z), 0, b.form.title).id());
    }

    /** Struck by another body or the ground at speed (Impulsis): the faster and heavier, the harder it lands. */
    public void impact(long body, float x, float y, float z, float nx, float ny, float nz, float speed, float impulse) {
        Breakable b = byBody.get(body);
        if (b == null || b.broken) return;
        if (g.sfx != null && speed > 1.2f && g.time - b.struckAt > 0.08) g.sfx.play(speed > 4 ? "wood" : "wood.light", x, y, z, Math.min(0.7f, speed * 0.1f), 0.8f, 18);
        if (speed < 5.5f) return;
        float damage = (speed - 5.5f) * 1.6f + impulse * 0.02f;
        float[] lo = local(b, x - b.x, y - b.y, z - b.z);
        float s = 1 / Breakable.VOXEL;
        int vx = clamp((int) Math.floor(lo[0] * s + b.form.sx / 2f), b.form.sx), vy = clamp((int) Math.floor(lo[1] * s + b.form.sy / 2f), b.form.sy), vz = clamp((int) Math.floor(lo[2] * s + b.form.sz / 2f), b.form.sz);
        strike(b, vx, vy, vz, damage, -nx, -ny, -nz, ItemDef.DamageType.BLUNT, 0, null);
    }
    private static int clamp(int v, int n) { return Math.max(0, Math.min(n - 1, v)); }

    /**
     * It comes apart: each board that is left, in the pieces it is in, a body of its own flung from the blow; what it
     * held spills; it is gone from where it stood.
     */
    public void breakApart(Breakable b, float px, float py, float pz, float dx, float dy, float dz, float force, Actor by) {
        if (b.broken) return;
        b.broken = true;
        burst++;
        // the boards: their voxels, each board a piece
        Map<Integer, List<Integer>> boards = new HashMap<>();
        for (int i = 0; i < b.mat.length; i++) if (b.mat[i] != Breakable.AIR) boards.computeIfAbsent((int) b.board[i], k -> new ArrayList<>()).add(i);
        int sy = b.form.sy, sz = b.form.sz;
        for (List<Integer> vs : boards.values()) {
            if (vs.size() < 2) continue;
            int lx = 99, ly = 99, lz = 99, hx = -1, hy = -1, hz = -1;
            for (int i : vs) { int x = i / (sy * sz), y = (i / sz) % sy, z = i % sz; lx = Math.min(lx, x); ly = Math.min(ly, y); lz = Math.min(lz, z); hx = Math.max(hx, x); hy = Math.max(hy, y); hz = Math.max(hz, z); }
            VoxModel v = new VoxModel(hx - lx + 1, hy - ly + 1, hz - lz + 1);
            for (int i : vs) { int x = i / (sy * sz), y = (i / sz) % sy, z = i % sz; v.set(x - lx, y - ly, z - lz, b.vox.get(x, y, z)); }
            // its middle in the world, and its turn the whole thing's
            float mx = (lx + hx + 1) / 2f - b.form.sx / 2f, my = (ly + hy + 1) / 2f - b.form.sy / 2f, mz = (lz + hz + 1) / 2f - b.form.sz / 2f;
            float[] w = world(b, mx * Breakable.VOXEL, my * Breakable.VOXEL, mz * Breakable.VOXEL);
            float wx = b.x + w[0], wy = b.y + w[1], wz = b.z + w[2];
            float ox = wx - b.x, oy = wy - b.y, oz = wz - b.z, ol = (float) Math.max(0.05, Math.sqrt(ox * ox + oy * oy + oz * oz));
            float near = 1f / (1f + 2f * (float) Math.sqrt((wx - px) * (wx - px) + (wy - py) * (wy - py) + (wz - pz) * (wz - pz)));
            float sp = (0.8f + 2.6f * near) * Math.min(3, force) + rnd.nextFloat() * 0.5f;
            float vx = dx * sp * near + ox / ol * (0.6f + rnd.nextFloat()), vy = 0.8f + rnd.nextFloat() * 1.4f + Math.max(0, dy) * sp, vz = dz * sp * near + oz / ol * (0.6f + rnd.nextFloat());
            float mass = Math.max(0.1f, vs.size() * Breakable.VOXEL * Breakable.VOXEL * Breakable.VOXEL * 600);
            long body = g.game.reserve(AstrattoNative.KIND_RIGID);
            g.game.commands().rigidCreate(body, AstrattoNative.SHAPE_BOX, AstrattoNative.RIGID_DEBRIS | AstrattoNative.RIGID_REPORT | AstrattoNative.RIGID_NO_MOVERS,
                    Math.max(0.02f, v.sx * Breakable.VOXEL / 2), Math.max(0.02f, v.sy * Breakable.VOXEL / 2), Math.max(0.02f, v.sz * Breakable.VOXEL / 2), mass,
                    wx, wy, wz, b.qw, b.qx, b.qy, b.qz, vx, vy, vz,
                    (rnd.nextFloat() - 0.5f) * 10, (rnd.nextFloat() - 0.5f) * 10, (rnd.nextFloat() - 0.5f) * 10, 0.7f, 0.15f, MATERIAL_WOOD, null);
            debris.addLast(new Piece(body, v, v.sx / 2f, v.sy / 2f, v.sz / 2f, g.time));
        }
        while (debris.size() > MAX_DEBRIS) release(debris.pollFirst());
        // what it held spills where it stood
        int k = 0;
        for (int i = 0; i < b.contents.size(); i++) {
            ItemStack s = b.contents.get(i);
            if (s == null) continue;
            b.contents.set(i, null);
            g.objects.drop(s, b.x + (k % 3 - 1) * 0.25f, Math.max(rpg.world.Ground.at(b.x, b.z) + 0.05f, b.y - b.form.hy() + 0.05f), b.z + (k / 3 % 3 - 1) * 0.25f);
            k++;
        }
        // a good plank or two survives a crate (the joiner's wood is not lost)
        if (b.form == Breakable.Form.CRATE && b.integrity() > 0.5f) g.objects.drop(new ItemStack(Items.PLANK, 1), b.x, Math.max(rpg.world.Ground.at(b.x, b.z) + 0.05f, b.y - b.form.hy() + 0.05f), b.z);
        if (g.fx != null) g.fx.chips(b.x, b.y, b.z, 18);
        if (g.sfx != null) { g.sfx.play("wood.heavy", b.x, b.y, b.z, 0.95f, 0.9f, 30); g.sfx.play("wood", b.x, b.y, b.z, 0.7f, 1.2f, 24); }
        g.noise(by, b.x, b.y, b.z, 16, 0.85f);
        release(b);
        b.removed = true;
        var ev = g.state.commit("Broken", by == null ? 0 : by.id, b.id, DevMap.jurisdiction(b.x, b.z), 0, b.form.title);
        vandal(b, by, true, ev.id());
    }

    /** Someone's goods broken by someone else, in a town: vandalism, for any witness to report (once a thing). */
    private final java.util.Set<Long> reported = new java.util.HashSet<>();
    private void vandal(Breakable b, Actor by, boolean whole, long event) {
        if (by == null || b.owner == 0 || b.owner == by.id || DevMap.WILDS.equals(DevMap.jurisdiction(b.x, b.z))) return;
        if (!reported.add(b.id)) return;
        g.law.crime(by, "VANDALISM", 0, whole ? 8 : 4, null, event);
    }

    private void release(Breakable b) {
        if (b.body != 0) { byBody.remove(b.body); g.game.commands().release(b.body); b.body = 0; }
        if (b.gpu != 0) { freed.add(b.gpu); b.gpu = 0; }
        if (b.gpuMetal != 0) { freed.add(b.gpuMetal); b.gpuMetal = 0; }
    }

    /** Meshes no longer drawn, for the renderer to let go (it owns them). */
    public final List<Integer> freed = new ArrayList<>();
    private void release(Piece p) {
        if (p == null) return;
        if (p.body != 0) { g.game.commands().release(p.body); p.body = 0; }
        if (p.gpu != 0) { freed.add(p.gpu); p.gpu = 0; }
        if (p.gpuMetal != 0) { freed.add(p.gpuMetal); p.gpuMetal = 0; }
    }

    /** All bodies let go (a world unloaded). */
    public void clear() {
        for (Breakable b : all) release(b);
        for (Piece p : debris) release(p);
        debris.clear(); all.clear(); byBody.clear();
    }
}
