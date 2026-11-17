package rpg.objects;

import astratto.bridge.AstrattoNative;
import rpg.Rpg;
import rpg.actor.Actor;
import rpg.item.ItemDef;
import rpg.render.Props;
import rpg.world.DevMap;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * (0.3.8, gamefixes: most if not all the reusable furniture destructible) A furnishing is built of parts - legs, a top,
 * a drawer front, a cushion, a candle, a canopy - and a blow lands on the part it meets, as it is modelled. Each part
 * takes what its material and its size allow (cloth and thin wood little, a stout post or iron or stone a great deal);
 * a part that gives is knocked off and flies as a piece of its own, and enough of the thing gone and the rest comes
 * apart. A wrecked piece of furniture is gone from where it stood (its cells too, so its bed or its table is no more),
 * and the world keeps it so: what is knocked off it, and whether it was wrecked, is saved.
 */
public final class Wreckage {
    /** The share of a piece's bulk that, gone, brings the rest down. */
    public static final float COLLAPSE = 0.42f;
    public static final float DEBRIS_LIFE = 120;
    public static final int MAX_DEBRIS = 220;

    /** What is left of a piece of furniture: the parts gone, each part's toughness left, and the version (for the mesh). */
    public static final class State {
        public final BitSet gone = new BitSet();
        public float[] hp;
        public int version;
        public boolean wrecked;
        /** The renderer's mesh ids for this state, and the version they were made at. */
        public int[] gpu;
        public int gpuVersion = -1;
    }

    /** A part knocked off: its body, its size and colour, and how its material takes the light. */
    public static final class Shard {
        public long body;
        public final float sx, sy, sz, r, gr, b, rough, metal;
        public final double born;
        Shard(long body, float sx, float sy, float sz, float r, float gr, float b, float rough, float metal, double born) {
            this.body = body; this.sx = sx; this.sy = sy; this.sz = sz; this.r = r; this.gr = gr; this.b = b; this.rough = rough; this.metal = metal; this.born = born;
        }
    }

    /** One part of a model, flattened from its layers: {cx, cy, cz, sx, sy, sz, r, g, b, layer}. */
    record Part(float cx, float cy, float cz, float sx, float sy, float sz, float r, float g, float b, int layer) {
        float volume() { return Math.max(1e-5f, sx * sy * sz); }
    }

    private final Rpg g;
    public final Map<Long, State> states = new HashMap<>();
    public final ArrayDeque<Shard> shards = new ArrayDeque<>();
    /** Mesh ids no longer drawn, for the renderer to let go. */
    public final List<Integer> freed = new ArrayList<>();
    private final Random rnd = new Random(0x3A7E);
    private static final Map<String, Props.Layer[]> MODELS = new HashMap<>();
    private static final Map<String, Part[]> PARTS = new HashMap<>();
    public int wrecked;

    public Wreckage(Rpg g) { this.g = g; g.vox.furnitureCells = this::occupied; }

    /** (0.4.5) The cells the standing, breakable furniture occupies (its collision), kept as pieces come and go. */
    private final java.util.HashSet<Long> cells = new java.util.HashSet<>();
    private int cellsFor = -1, cellsWrecked = -1;
    /** Whether a cell is a breakable piece of furniture's own (a blow passes into it to meet the piece). */
    public boolean occupied(long key) {
        if (cellsFor != g.objects.furniture.size() || cellsWrecked != wrecked) {
            cells.clear();
            for (Furniture f : g.objects.furniture) {
                if (!f.exists() || !breakable(f.kind) || f.kind.block == rpg.world.Blocks.AIR) continue;
                State st = states.get(f.id);
                if (st != null && st.wrecked) continue;
                for (int x = f.x0; x <= f.x1; x++) for (int z = f.z0; z <= f.z1; z++) for (int y = 0; y < Math.max(1, f.h); y++) cells.add(rpg.world.Voxels.cellKey(x, f.floor + y, z));
            }
            cellsFor = g.objects.furniture.size(); cellsWrecked = wrecked;
        }
        return cells.contains(key);
    }

    /** Whether a kind of furnishing can be broken (the built-in, the graves and the shops' counters cannot). */
    public static boolean breakable(Furniture.Kind k) {
        return switch (k) { case HEADSTONE, SLAB, TARGET, COUNTER, RUG -> false; default -> true; };
    }

    /** The model a piece is drawn with (the renderer's own, so a blow meets what is seen). */
    public static synchronized Props.Layer[] model(Furniture f) {
        float w = f.facing % 2 == 0 ? f.width() : f.depth(), d = f.facing % 2 == 0 ? f.depth() : f.width();
        int seed = seed(f);
        String key = f.kind + ":" + w + "x" + d + "x" + f.h + ":" + seed;
        return MODELS.computeIfAbsent(key, k -> Props.furniture(f.kind, w, d, f.h, seed));
    }
    /** The seed a piece's look is varied by (as the renderer chooses it). */
    public static int seed(Furniture f) {
        boolean own = switch (f.kind) {
            case BED, BOOKCASE, HEADSTONE, PAINTING, RUG, FOURPOSTER, ARMCHAIR, SOFA, TAPESTRY, PLANTER, SHELVES, WINE_RACK, CUPBOARD, WEAPON_RACK, ARMOR_STAND -> true;
            default -> false;
        };
        return own ? (int) (f.id % 6) : 0;
    }
    static synchronized Part[] parts(Furniture f) {
        float w = f.facing % 2 == 0 ? f.width() : f.depth(), d = f.facing % 2 == 0 ? f.depth() : f.width();
        String key = f.kind + ":" + w + "x" + d + "x" + f.h + ":" + seed(f);
        return PARTS.computeIfAbsent(key, k -> {
            List<Part> out = new ArrayList<>();
            Props.Layer[] ls = model(f);
            for (int li = 0; li < ls.length; li++) for (float[] b : ls[li].boxes().list) {
                float r = b.length > 7 ? b[6] : b[6], gg = b.length > 7 ? b[7] : b[6], bb = b.length > 7 ? b[8] : b[6];
                out.add(new Part(b[0], b[1], b[2], b[3], b[4], b[5], r, gg, bb, li));
            }
            return out.toArray(new Part[0]);
        });
    }

    /** Its state (made on the first blow). */
    public State of(Furniture f) {
        return states.computeIfAbsent(f.id, k -> {
            State s = new State();
            Part[] ps = parts(f);
            Props.Layer[] ls = model(f);
            s.hp = new float[ps.length];
            for (int i = 0; i < ps.length; i++) s.hp[i] = toughness(ps[i], ls[ps[i].layer()]);
            return s;
        });
    }

    /** How much blow a part takes: its bulk (its thinnest side most), by its material - cloth, wood, metal, stone. */
    static float toughness(Part p, Props.Layer l) {
        float thin = Math.min(p.sx(), Math.min(p.sy(), p.sz())), bulk = (float) Math.cbrt(p.volume());
        float k = l.metal() > 0.5f ? 3.5f : l.rough() >= 0.95f ? 0.4f : l.rough() <= 0.1f ? 0.3f : l.emissive() > 0 ? 0.2f : l.rough() >= 0.45f && l.rough() <= 0.55f ? 4f : 1f;
        return Math.max(0.25f, (thin * 12 + bulk * 6) * k);
    }

    // ------------------------------------------------------------------ the frame: world <-> the model's own space
    private static float[] toLocal(Furniture f, float x, float y, float z) {
        float yaw = f.yaw(), c = (float) Math.cos(yaw), s = (float) Math.sin(yaw);
        float dx = x - f.x, dz = z - f.z;
        // the inverse of rotateY(yaw): x' = c x - s z, z' = s x + c z
        return new float[]{c * dx - s * dz, y - f.floor, s * dx + c * dz};
    }
    private static float[] dirLocal(Furniture f, float x, float y, float z) {
        float yaw = f.yaw(), c = (float) Math.cos(yaw), s = (float) Math.sin(yaw);
        return new float[]{c * x - s * z, y, s * x + c * z};
    }
    static float[] toWorld(Furniture f, float x, float y, float z) {
        float yaw = f.yaw(), c = (float) Math.cos(yaw), s = (float) Math.sin(yaw);
        return new float[]{f.x + c * x + s * z, f.floor + y, f.z - s * x + c * z};
    }

    /** What a ray meets first among the standing furniture within limit: {furniture, distance, part} or null. */
    public Object[] ray(float ox, float oy, float oz, float dx, float dy, float dz, float limit) {
        Object[] best = null;
        float bestT = limit;
        // (0.4.7, gamefixes: aggressive optimization) only the furniture about the line: every swing of every fighter tried
        // every piece in the world (thousands on ShadowVeil) every frame of the swing
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz), reach = len < 1e-6f ? 0 : limit / len;
        for (rpg.interact.WorldObject near : g.objects.near(ox + dx * reach * 0.5f, oz + dz * reach * 0.5f, limit * 0.5f + 3)) {
            if (!(near instanceof Furniture f) || !f.exists() || !breakable(f.kind)) continue;
            float half = Math.max(f.width(), f.depth()) * 0.75f + f.h;
            float cx = f.x - ox, cy = f.floor + f.h * 0.5f - oy, cz = f.z - oz;
            float along = cx * dx + cy * dy + cz * dz;
            if (along < -half || along > bestT + half || cx * cx + cy * cy + cz * cz - along * along > half * half) continue;
            State st = states.get(f.id);
            if (st != null && st.wrecked) continue;
            float[] o = toLocal(f, ox, oy, oz), d = dirLocal(f, dx, dy, dz);
            Part[] ps = parts(f);
            for (int i = 0; i < ps.length; i++) {
                if (st != null && st.gone.get(i)) continue;
                Part p = ps[i];
                float t = slab(o, d, p.cx() - p.sx() / 2 - 0.01f, p.cy() - p.sy() / 2 - 0.01f, p.cz() - p.sz() / 2 - 0.01f, p.cx() + p.sx() / 2 + 0.01f, p.cy() + p.sy() / 2 + 0.01f, p.cz() + p.sz() / 2 + 0.01f, bestT);
                if (t < bestT) { bestT = t; best = new Object[]{f, t, i}; }
            }
        }
        return best;
    }
    private static float slab(float[] o, float[] d, float x0, float y0, float z0, float x1, float y1, float z1, float limit) {
        float t0 = 0, t1 = limit;
        float[] lo = {x0, y0, z0}, hi = {x1, y1, z1};
        for (int k = 0; k < 3; k++) {
            if (Math.abs(d[k]) < 1e-7f) { if (o[k] < lo[k] || o[k] > hi[k]) return Float.MAX_VALUE; continue; }
            float a = (lo[k] - o[k]) / d[k], b = (hi[k] - o[k]) / d[k];
            t0 = Math.max(t0, Math.min(a, b)); t1 = Math.min(t1, Math.max(a, b));
            if (t0 > t1) return Float.MAX_VALUE;
        }
        return t0;
    }

    // ------------------------------------------------------------------ blows
    /**
     * A blow lands on a part at a world point: damage in hit points, the way it went, its kind (a point goes deep and
     * narrow, a club wide), and who. The parts it reaches take it by distance; the ones that give fly off.
     */
    public void strike(Furniture f, int part, float px, float py, float pz, float damage, float dx, float dy, float dz, ItemDef.DamageType type, Actor by) {
        if (!f.exists() || !breakable(f.kind) || damage <= 0) return;
        State st = of(f);
        if (st.wrecked) return;
        Part[] ps = parts(f);
        Props.Layer[] ls = model(f);
        float[] lp = toLocal(f, px, py, pz);
        boolean point = type == ItemDef.DamageType.PIERCE, blunt = type == null || type == ItemDef.DamageType.BLUNT || type == ItemDef.DamageType.NONE;
        float radius = point ? 0.06f : blunt ? 0.16f + damage * 0.015f : 0.1f + damage * 0.01f;
        int lost = 0;
        for (int i = 0; i < ps.length; i++) {
            if (st.gone.get(i)) continue;
            Part p = ps[i];
            // the distance from the point struck to this part's box
            float ex = Math.max(0, Math.abs(lp[0] - p.cx()) - p.sx() / 2), ey = Math.max(0, Math.abs(lp[1] - p.cy()) - p.sy() / 2), ez = Math.max(0, Math.abs(lp[2] - p.cz()) - p.sz() / 2);
            float dist = (float) Math.sqrt(ex * ex + ey * ey + ez * ez);
            if (i != part && dist > radius) continue;
            float k = i == part ? 1 : 1 - dist / (radius + 1e-4f);
            st.hp[i] -= damage * k * (i == part ? 1 : 0.6f);
            if (st.hp[i] <= 0) { knockOff(f, st, i, ps[i], ls[ps[i].layer()], dx, dy, dz, 1 + damage * 0.08f); lost++; }
        }
        if (g.sfx != null) {
            Props.Layer l = ls[ps[Math.max(0, Math.min(ps.length - 1, part))].layer()];
            String sound = l.metal() > 0.5f ? "block" : l.rough() >= 0.95f ? "soft.light" : l.rough() <= 0.1f ? "glass.light" : "wood";
            g.sfx.play(g.sfx.has(sound) ? sound : "wood", px, py, pz, Math.min(0.8f, 0.3f + damage * 0.05f));
        }
        if (g.fx != null && lost > 0) g.fx.chips(px, py, pz, Math.min(12, 2 + lost * 2));
        g.noise(by, px, py, pz, 5 + damage, 0.4f);
        if (lost > 0) {
            st.version++;
            if (gone(st, ps) >= COLLAPSE) wreck(f, st, ps, ls, dx, dy, dz, by);
            else damaged(f, by);
        }
    }

    /** The share of its bulk gone. */
    private static float gone(State st, Part[] ps) {
        float all = 0, out = 0;
        for (int i = 0; i < ps.length; i++) { all += ps[i].volume(); if (st.gone.get(i)) out += ps[i].volume(); }
        return all <= 0 ? 0 : out / all;
    }

    private void knockOff(Furniture f, State st, int i, Part p, Props.Layer l, float dx, float dy, float dz, float force) {
        st.gone.set(i);
        if (p.volume() < 2e-5f || g.game == null) return;
        float[] c = toWorld(f, p.cx(), p.cy(), p.cz());
        float[] dirW = {dx, dy, dz};
        float sp = (0.6f + rnd.nextFloat() * 1.4f) * Math.min(3, force);
        float yaw = f.yaw(), qw = (float) Math.cos(yaw / 2), qy = (float) Math.sin(yaw / 2);
        long body = g.game.reserve(AstrattoNative.KIND_RIGID);
        float mass = Math.max(0.05f, p.volume() * (l.metal() > 0.5f ? 3000 : l.rough() <= 0.55f && l.rough() >= 0.45f ? 2400 : 600));
        g.game.commands().rigidCreate(body, AstrattoNative.SHAPE_BOX, AstrattoNative.RIGID_DEBRIS | AstrattoNative.RIGID_YIELD,
                Math.max(0.01f, p.sx() / 2), Math.max(0.01f, p.sy() / 2), Math.max(0.01f, p.sz() / 2), mass, c[0], c[1], c[2], qw, 0, qy, 0,
                dirW[0] * sp + (rnd.nextFloat() - 0.5f), 0.6f + rnd.nextFloat() * 1.2f + Math.max(0, dirW[1]) * sp, dirW[2] * sp + (rnd.nextFloat() - 0.5f),
                (rnd.nextFloat() - 0.5f) * 8, (rnd.nextFloat() - 0.5f) * 8, (rnd.nextFloat() - 0.5f) * 8, 0.7f, 0.15f, Breakables.MATERIAL_WOOD, null);
        shards.addLast(new Shard(body, p.sx(), p.sy(), p.sz(), p.r(), p.g(), p.b(), l.rough(), l.metal(), g.time));
        while (shards.size() > MAX_DEBRIS) release(shards.pollFirst());
    }

    /** It comes apart: every part that is left flies, its cells go, what it was is gone from the world. */
    private void wreck(Furniture f, State st, Part[] ps, Props.Layer[] ls, float dx, float dy, float dz, Actor by) {
        for (int i = 0; i < ps.length; i++) if (!st.gone.get(i)) knockOff(f, st, i, ps[i], ls[ps[i].layer()], dx * 0.5f, dy, dz * 0.5f, 0.8f);
        st.wrecked = true; st.version++;
        f.place(g, false);
        f.placed = false;
        wrecked++;
        if (g.sfx != null) { g.sfx.play("wood.heavy", f.x, f.floor + 0.5f, f.z, 0.9f, 0.95f, 26); g.sfx.play("wood", f.x, f.floor + 0.5f, f.z, 0.7f, 1.2f, 20); }
        if (g.fx != null) g.fx.chips(f.x, f.floor + 0.5f, f.z, 16);
        g.noise(by, f.x, f.floor + 0.5f, f.z, 16, 0.8f);
        var ev = g.state.commit("FurnitureWrecked", by == null ? 0 : by.id, f.id, f.jurisdiction(), 0, f.kind.title);
        if (by != null && f.owner != 0 && f.owner != by.id && !DevMap.WILDS.equals(f.jurisdiction())) g.law.crime(by, "VANDALISM", 0, 8, null, ev.id());
    }

    private final java.util.Set<Long> reported = new java.util.HashSet<>();
    private void damaged(Furniture f, Actor by) {
        if (by == null || f.owner == 0 || f.owner == by.id || DevMap.WILDS.equals(f.jurisdiction()) || !reported.add(f.id)) return;
        var ev = g.state.commit("FurnitureDamaged", by.id, f.id, f.jurisdiction(), 0, f.kind.title);
        g.law.crime(by, "VANDALISM", 0, 3, null, ev.id());
    }

    /** Old pieces cleared away. */
    public void update() {
        while (!shards.isEmpty() && g.time - shards.peekFirst().born > DEBRIS_LIFE) release(shards.pollFirst());
        for (var it = shards.iterator(); it.hasNext(); ) { Shard s = it.next(); var r = g.lastRigids.body(s.body); if (r != null && r.y < -4) { release(s); it.remove(); } }
    }

    private void release(Shard s) { if (s != null && s.body != 0 && g.game != null) { g.game.commands().release(s.body); s.body = 0; } }

    /** A piece's mesh is remade from what is left of it: its parts not knocked off, by layer. */
    public Props.Layer[] remaining(Furniture f) {
        State st = states.get(f.id);
        Props.Layer[] ls = model(f);
        if (st == null || st.gone.isEmpty()) return ls;
        Part[] ps = parts(f);
        Props.Layer[] out = new Props.Layer[ls.length];
        for (int li = 0; li < ls.length; li++) {
            rpg.render.Meshes.Boxes b = new rpg.render.Meshes.Boxes();
            out[li] = new Props.Layer(b, ls[li].rough(), ls[li].metal(), ls[li].emissive());
        }
        for (int i = 0; i < ps.length; i++) {
            if (st.gone.get(i)) continue;
            Part p = ps[i];
            int rgb = Math.round(p.r() * 255) << 16 | Math.round(p.g() * 255) << 8 | Math.round(p.b() * 255);
            out[p.layer()].boxes().rgb(p.cx(), p.cy(), p.cz(), p.sx(), p.sy(), p.sz(), rgb);
        }
        return out;
    }

    /**
     * (0.4.6, gamefixes: reset all destruction on reloading, sleeping, dying or using the shrine) every broken piece of
     * furniture whole again where it stood - a chandelier back on its chain, its candles lit - and the splinters gone.
     * Returns how many were mended.
     */
    public int restoreAll() {
        int n = 0;
        for (var e : states.entrySet()) {
            State s = e.getValue();
            if (s.gone.isEmpty() && !s.wrecked) continue;
            if (g.objects.get(e.getKey()) instanceof Furniture f && s.wrecked) { f.placed = true; f.place(g, true); }
            n++;
        }
        states.clear();
        cellsFor = -1;
        while (!shards.isEmpty()) release(shards.pollFirst());
        wrecked = 0;
        return n;
    }

    // ------------------------------------------------------------------ the world remembers
    public Map<String, Object> save() {
        Map<String, Object> m = new HashMap<>();
        for (var e : states.entrySet()) {
            State s = e.getValue();
            if (s.gone.isEmpty() && !s.wrecked) continue;
            Map<String, Object> o = new HashMap<>();
            o.put("gone", s.gone.stream().mapToObj(Integer::toString).reduce((a, b) -> a + "," + b).orElse(""));
            o.put("wrecked", s.wrecked);
            m.put(Long.toString(e.getKey()), o);
        }
        return m;
    }

    public void load(Map<String, Object> m) {
        states.clear();
        if (m == null) return;
        for (var e : m.entrySet()) {
            long id;
            try { id = Long.parseLong(e.getKey()); } catch (NumberFormatException x) { continue; }
            if (!(g.objects.get(id) instanceof Furniture f) || !(e.getValue() instanceof Map<?, ?> o)) continue;
            State s = of(f);
            Object gone = o.get("gone");
            if (gone instanceof String str && !str.isEmpty()) for (String k : str.split(",")) { try { int i = Integer.parseInt(k.trim()); s.gone.set(i); s.hp[Math.min(i, s.hp.length - 1)] = 0; } catch (NumberFormatException ignored) { } }
            s.wrecked = Boolean.TRUE.equals(o.get("wrecked"));
            if (s.wrecked) { f.place(g, false); f.placed = false; }
            s.version++;
        }
    }
}
