package rpg.world.shadowveil;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import rpg.world.Blocks;

/**
 * (0.4.1, gamefixes: the exterior architecture of all seven kingdoms, their settlements, roads, walls, bridges, ruins,
 * landmarks and the mouths of every documented underground place) ShadowVeil's built world, raised on the grown terrain
 * from the design ({@code data/shadowveil_architecture.json}, beside {@code shadowveil.json}; projectrpg.txt's WORLD
 * BUILDING has the final say). Two passes inside {@link Terrain#generate}:
 * <ol>
 * <li>{@link #plan} - before the ground's cells are written: every structure given its lot - the documented chunk,
 *     its size, its front turned to the street - and the lot checked (dry, off the roads, clear of every other lot, not
 *     too steep) and moved to the nearest free ground if it is not; its pad levelled and its edge eased into the land
 *     about it; the town walls, the aqueducts and the streets' houses planned; a lane laid from every door to the road
 *     network;</li>
 * <li>{@link #raise} - after: each structure drawn by its template ({@link Kit}) in its culture's style, the bridges
 *     given parapets and piers, the streets their lamps, and every door, interior and reserved underground volume
 *     checked against what was built.</li>
 * </ol>
 * Interiors, furnishings and the people who live in these places come in 0.4.2: here every building has its footprint,
 * its doors and its rooms' volumes kept empty, and every dungeon its entrance and the volume beyond it reserved.
 */
public final class Architecture {
    /** A structure of the world as planned and raised. */
    public static final class Structure {
        public final String id, name, template, settlement;
        public int x0, z0, x1, z1, floor, w, d;
        public char facing;
        final Map<String, Object> opts;
        final Kit.Style style;
        final long seed;
        /** Doorways: cells {x, z}, the floor cell under the sill, outward {dx, dz}. */
        public final List<int[][]> doors = new ArrayList<>();
        public final List<Integer> doorFloors = new ArrayList<>();
        public final List<int[]> doorOut = new ArrayList<>();
        /** Whether each doorway hangs a door (false: an open gate, an arch, a cave's mouth). */
        public final List<Boolean> doorLeaf = new ArrayList<>();
        /** Named points {x, z} (or {x, y, z}): spawn, gate, court, altar, mouth... */
        public final Map<String, float[]> points = new LinkedHashMap<>();
        Structure(String id, String name, String template, String settlement, Map<String, Object> opts, Kit.Style style, long seed) {
            this.id = id; this.name = name; this.template = template; this.settlement = settlement; this.opts = opts; this.style = style; this.seed = seed;
        }
        public boolean contains(int x, int z) { return x >= x0 && x <= x1 && z >= z0 && z <= z1; }
        /** The cell two before the middle of its front (where its door's lane begins). */
        public int[] apron() {
            int cx = (x0 + x1) / 2, cz = (z0 + z1) / 2;
            return switch (facing) { case 'n' -> new int[]{cx, z0 - 2}; case 's' -> new int[]{cx, z1 + 2}; case 'w' -> new int[]{x0 - 2, cz}; default -> new int[]{x1 + 2, cz}; };
        }
        /** Whether its front is meant to be walked to (not a pier, a bell under the water, a weir, a span). */
        public boolean reachable() { return !Boolean.TRUE.equals(opts.get("water")) && !template.equals("sunkbell") && !Boolean.FALSE.equals(opts.get("lane")) || template.equals("plaza"); }
        public float cx() { return (x0 + x1 + 1) / 2f; }
        public float cz() { return (z0 + z1 + 1) / 2f; }
    }
    /** A volume kept for what comes later: interiors (0.4.2), cellars, crypts and dungeons. */
    public record Reserved(String owner, String kind, int x0, int y0, int z0, int x1, int y1, int z1) {
        boolean overlaps(Reserved o) { return x0 <= o.x1 && o.x0 <= x1 && y0 <= o.y1 && o.y0 <= y1 && z0 <= o.z1 && o.z0 <= z1; }
    }

    final Terrain t;
    final Atlas a;
    public final List<Structure> structures = new ArrayList<>();
    public final List<Reserved> reserved = new ArrayList<>();
    public final List<String> failures = new ArrayList<>(), notes = new ArrayList<>();
    /** Per settlement, the watch's round {x, z} (chunks in the data, cells here). */
    public final Map<String, List<float[]>> patrols = new LinkedHashMap<>();
    /** Where a new character wakes {x, z} and what they face {x, z}. */
    public float[] spawn, spawnLook;
    /** The templates whose lot is open ground (trees are kept off it, nothing walls it). */
    private static final java.util.Set<String> YARDS = java.util.Set.of("field", "yard", "gibbets", "stones", "camp", "orchard", "graveyard", "weir", "pier", "sunkbell");
    final Map<String, Kit.Style> styles = new HashMap<>();
    private final Map<String, Object> data;
    private final List<Wall> walls = new ArrayList<>();
    private final List<Aqueduct> aqueducts = new ArrayList<>();
    /** The flowing water the aqueducts carry: {x0, z0, x1, z1, dx, dz} (Fluids' currents). */
    public final List<float[]> channels = new ArrayList<>();
    private record Wall(String id, Kit.Style style, List<int[]> pts, int thick, int high, boolean closed, List<int[]> gates) {}
    private record Aqueduct(String id, Kit.Style style, List<int[]> line, int yFrom, int yTo) {}

    Architecture(Terrain t) {
        this.t = t; this.a = t.atlas;
        this.data = a.map("architecture");
        Map<String, Object> st = Atlas.map(data, "styles");
        for (var e : st.entrySet()) {
            @SuppressWarnings("unchecked") Map<String, Object> m = (Map<String, Object>) e.getValue();
            styles.put(e.getKey(), new Kit.Style().with(m));
        }
        if (!styles.containsKey("wilds")) styles.put("wilds", new Kit.Style());
    }
    private Kit.Style style(String name) { Kit.Style s = styles.get(name); if (s == null) throw new IllegalStateException("the architecture names a style it does not define: " + name); return s; }

    // ================================================================== 1. the plan
    /** Plans every structure on the grown ground (before its cells are written). */
    static Architecture plan(Terrain t) {
        Architecture ar = new Architecture(t);
        if (ar.data.isEmpty()) return ar;
        ar.planWalls();
        ar.planAqueducts();
        for (Map<String, Object> s : Atlas.list(ar.data, "settlements")) ar.planSettlement(s);
        for (Map<String, Object> s : Atlas.list(ar.data, "settlements")) for (Map<String, Object> dd : Atlas.list(s, "districts")) ar.planDistrict(s, dd);
        ar.lanes();
        ar.orchards();
        return ar;
    }

    private void planSettlement(Map<String, Object> s) {
        String sid = Atlas.s(s, "id", "?");
        Kit.Style st = style(Atlas.s(s, "style", "wilds"));
        for (Map<String, Object> b : Atlas.list(s, "buildings")) place(sid, st, b);
        List<float[]> round = new ArrayList<>();
        for (Object p : Atlas.arr(s, "patrol")) { double[] q = Atlas.pt(p); round.add(new float[]{(float) a.x(q[0]), (float) a.z(q[1])}); }
        if (!round.isEmpty()) patrols.put(Atlas.s(s, "realm", sid), round);
    }

    /** A structure from its data: its lot found, levelled and claimed. */
    private Structure place(String settlement, Kit.Style base, Map<String, Object> b) {
        String id = Atlas.s(b, "id", settlement + "_" + structures.size());
        String template = Atlas.s(b, "template", "building");
        Map<String, Object> opts = new HashMap<>(b);
        Kit.Style st = Atlas.s(b, "style", null) != null ? style(Atlas.s(b, "style", null)) : base;
        Map<String, Object> mats = Atlas.map(b, "materials");
        if (!mats.isEmpty()) st = st.with(mats);
        double[] at = Atlas.pt(b.get("at"));
        double[] size = Atlas.pt(b.get("size"));
        int w = (int) size[0], d = size.length > 1 ? (int) size[1] : (int) size[0];
        double cx = a.x(at[0]), cz = a.z(at[1]);
        char facing = facing(Atlas.s(b, "facing", "road"), cx, cz, b);
        long seed = a.seed * 31 + id.hashCode();
        Structure s = new Structure(id, Atlas.s(b, "name", id), template, settlement, opts, st, seed);
        s.w = w; s.d = d; s.facing = facing;
        if (!lot(s, cx, cz, Atlas.i(b, "search", 28), Atlas.i(b, "margin", 2), Atlas.d(b, "maxSlope", 9),
                Boolean.TRUE.equals(b.get("water")), Boolean.TRUE.equals(b.get("onRoad")))) {
            failures.add("no ground for " + id + " near chunk [" + at[0] + ", " + at[1] + "] (" + w + " x " + d + ")");
            return null;
        }
        structures.add(s);
        return s;
    }

    /** Which way a structure faces: given, toward a named chunk, or toward the nearest road. */
    private char facing(String f, double cx, double cz, Map<String, Object> b) {
        if (f.length() == 1 && "nsew".indexOf(f.charAt(0)) >= 0) return f.charAt(0);
        double tx, tz;
        if (b.get("toward") instanceof List<?> l) { double[] p = Atlas.pt(l); tx = a.x(p[0]); tz = a.z(p[1]); }
        else {
            int[] r = nearest(cx, cz, Terrain.ROAD, 64);
            if (r == null) return 'n';
            tx = r[0] + 0.5; tz = r[1] + 0.5;
        }
        double dx = tx - cx, dz = tz - cz;
        return Math.abs(dx) > Math.abs(dz) ? (dx > 0 ? 'e' : 'w') : (dz > 0 ? 's' : 'n');
    }
    private int[] nearest(double cx, double cz, int flag, int radius) {
        int bx = (int) cx, bz = (int) cz;
        for (int r = 0; r <= radius; r++) for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
            if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
            int x = bx + dx, z = bz + dz;
            if (t.in(x, z) && (t.flags[t.idx(x, z)] & flag) != 0 && (t.flags[t.idx(x, z)] & Terrain.BUILT) == 0) return new int[]{x, z};
        }
        return null;
    }

    /**
     * Finds the structure's lot: the free ground nearest the asked middle (a spiral out to 'search' cells), then levels
     * its pad and claims it. A lot is free when it and its margin are dry, off the roads (unless it is a square the road
     * crosses), clear of every other lot and not steeper than asked.
     */
    private boolean lot(Structure s, double cx, double cz, int search, int margin, double maxSlope, boolean water, boolean onRoad) {
        int sx = s.facing == 'n' || s.facing == 's' ? s.w : s.d, sz = s.facing == 'n' || s.facing == 's' ? s.d : s.w;
        int bx = (int) Math.round(cx - sx / 2.0), bz = (int) Math.round(cz - sz / 2.0);
        for (int r = 0; r <= search; r++) for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
            if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
            int x0 = bx + dx, z0 = bz + dz;
            if (!free(x0, z0, x0 + sx - 1, z0 + sz - 1, margin, maxSlope, water, onRoad)) continue;
            s.x0 = x0; s.z0 = z0; s.x1 = x0 + sx - 1; s.z1 = z0 + sz - 1;
            s.floor = water ? waterLevel(s) + 1 : pad(s, margin, onRoad);
            claim(s, margin);
            return true;
        }
        return false;
    }
    private int waterLevel(Structure s) {
        int best = -1;
        for (int x = s.x0; x <= s.x1; x++) for (int z = s.z0; z <= s.z1; z++) best = Math.max(best, Math.max(t.water[t.idx(x, z)], t.top[t.idx(x, z)]));
        return best;
    }
    private boolean free(int x0, int z0, int x1, int z1, int m, double maxSlope, boolean water, boolean onRoad) {
        if (x0 - m < 4 || z0 - m < 4 || x1 + m >= t.S - 4 || z1 + m >= t.S - 4) return false;
        int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE, wet = 0, dry = 0;
        for (int x = x0 - m; x <= x1 + m; x++) for (int z = z0 - m; z <= z1 + m; z++) {
            int i = t.idx(x, z), f = t.flags[i];
            boolean inside = x >= x0 && x <= x1 && z >= z0 && z <= z1;
            if ((f & Terrain.BUILT) != 0) return false;
            if (inside && (f & Terrain.YARD) != 0) return false;
            if (!onRoad && (f & (Terrain.ROAD | Terrain.BRIDGE | Terrain.CAUSEWAY)) != 0) return false;
            boolean w = t.water[i] >= 0;
            if (inside) { if (w) wet++; else dry++; }
            if (!water && w) return false;
            if (inside && !w) { lo = Math.min(lo, t.top[i]); hi = Math.max(hi, t.top[i]); }
        }
        if (water) return wet > 0;
        return dry > 0 && hi - lo <= maxSlope;
    }
    /** Levels a lot to the median of its ground and eases the ground round it back into the land. Returns the floor's cell. */
    private int pad(Structure s, int margin, boolean onRoad) {
        int n = (s.x1 - s.x0 + 1) * (s.z1 - s.z0 + 1);
        int[] hs = new int[n];
        int k = 0;
        for (int x = s.x0; x <= s.x1; x++) for (int z = s.z0; z <= s.z1; z++) hs[k++] = t.top[t.idx(x, z)];
        java.util.Arrays.sort(hs);
        int P = hs[n / 2];
        if (s.template.equals("mouth")) {
            // a mouth is cut into the hill: only its front is levelled, at the ground before it
            int[] front = frontCells(s);
            P = t.top[t.idx(front[0], front[1])];
            mound(s, P);
        }
        int spread = Math.max(3, Math.min(10, (int) Math.ceil(Math.max(Math.abs(hs[n - 1] - P), Math.abs(P - hs[0])) * 1.6)));
        for (int x = s.x0 - spread; x <= s.x1 + spread; x++) for (int z = s.z0 - spread; z <= s.z1 + spread; z++) {
            if (!t.in(x, z)) continue;
            int i = t.idx(x, z);
            boolean inside = x >= s.x0 && x <= s.x1 && z >= s.z0 && z <= s.z1;
            if (s.template.equals("mouth") && inside && !frontRow(s, x, z)) continue;
            if (inside) { if (t.water[i] < 0) t.top[i] = P; continue; }
            if ((t.flags[i] & (Terrain.ROAD | Terrain.BRIDGE | Terrain.BUILT | Terrain.RIVER | Terrain.LAKE | Terrain.SEA | Terrain.CAUSEWAY)) != 0 || t.water[i] >= 0) continue;
            double dx = Math.max(0, Math.max(s.x0 - x, x - s.x1)), dz = Math.max(0, Math.max(s.z0 - z, z - s.z1));
            double dd = Math.hypot(dx, dz);
            if (dd > spread) continue;
            double tt = dd <= margin ? 0 : (dd - margin) / Math.max(1, spread - margin);
            tt = tt * tt * (3 - 2 * tt);
            t.top[i] = (int) Math.round(P + (t.top[i] - P) * tt);
        }
        return P;
    }
    /** The front middle cell of a lot (world), and the cells of its front row. */
    private int[] frontCells(Structure s) {
        int cx = (s.x0 + s.x1) / 2, cz = (s.z0 + s.z1) / 2;
        return switch (s.facing) { case 'n' -> new int[]{cx, s.z0}; case 's' -> new int[]{cx, s.z1}; case 'w' -> new int[]{s.x0, cz}; default -> new int[]{s.x1, cz}; };
    }
    private boolean frontRow(Structure s, int x, int z) {
        return switch (s.facing) { case 'n' -> z <= s.z0 + 1; case 's' -> z >= s.z1 - 1; case 'w' -> x <= s.x0 + 1; default -> x >= s.x1 - 1; };
    }
    /** A mouth on ground too low for its passage: rock heaped over it, a rise falling away behind and to the sides. */
    private void mound(Structure s, int P) {
        int need = Atlas.i(s.opts, "height", 3) + 3;
        int len = Math.max(s.w, s.d);
        double cx = (s.x0 + s.x1 + 1) / 2.0, cz = (s.z0 + s.z1 + 1) / 2.0, R = len * 0.9 + 4;
        for (int x = (int) (cx - R); x <= (int) (cx + R); x++) for (int z = (int) (cz - R); z <= (int) (cz + R); z++) {
            if (!t.in(x, z) || frontRow(s, x, z) && s.contains(x, z)) continue;
            int i = t.idx(x, z);
            if (t.water[i] >= 0 || (t.flags[i] & (Terrain.ROAD | Terrain.BRIDGE | Terrain.BUILT)) != 0) continue;
            double dd = Math.hypot(x + 0.5 - cx, z + 0.5 - cz) / R;
            if (dd >= 1) continue;
            int want = P + (int) Math.round(need * (1 - dd * dd) + Noise.hash(a.seed, x, z, 97) * 1.2);
            if (s.contains(x, z) || t.top[i] < want) t.top[i] = Math.max(t.top[i], want);
            t.flags[i] |= Terrain.CLIFF;
        }
    }
    private void claim(Structure s, int margin) {
        for (int x = s.x0 - margin; x <= s.x1 + margin; x++) for (int z = s.z0 - margin; z <= s.z1 + margin; z++) {
            if (!t.in(x, z)) continue;
            int i = t.idx(x, z);
            boolean inside = s.contains(x, z);
            t.flags[i] |= inside ? Terrain.BUILT : Terrain.YARD;
            if (inside) t.flags[i] &= ~(Terrain.TREE | Terrain.CLIFF);
            if (inside && s.template.equals("plaza")) t.flags[i] = (t.flags[i] & ~Terrain.BUILT) | Terrain.YARD | Terrain.SQUARE;
            if (inside && YARDS.contains(s.template))
                t.flags[i] = (t.flags[i] & ~Terrain.BUILT) | Terrain.YARD;
        }
    }

    // ------------------------------------------------------------------ districts: houses along the streets
    /**
     * A district's houses: walking each road through it, a plot is tried every few cells on either side, set back from
     * the road with its front to it; the sizes are the culture's own (small, medium, large - projectrpg.txt's three
     * house sizes), picked by a hash so neighbours differ. What fits is built; what does not leaves the ground open.
     */
    private void planDistrict(Map<String, Object> s, Map<String, Object> dd) {
        String sid = Atlas.s(s, "id", "?");
        Kit.Style st = style(Atlas.s(dd, "style", Atlas.s(s, "style", "wilds")));
        int[] rect = a.cells(Atlas.arr(dd, "rect"));
        List<Map<String, Object>> sizes = Atlas.list(dd, "sizes");
        if (sizes.isEmpty()) return;
        int step = Atlas.i(dd, "step", 4), setback = Atlas.i(dd, "setback", 1), max = Atlas.i(dd, "max", 40), made = 0;
        List<?> only = Atlas.arr(dd, "roads");
        String id = Atlas.s(dd, "id", sid + "_district");
        for (Terrain.Road r : t.roads) {
            if (!only.isEmpty() && !only.contains(r.id())) continue;
            int[] p = r.path();
            int n = p.length / 3;
            Map<String, Object> kind = Atlas.map(Atlas.map(a.map("roads"), "kinds"), r.kind());
            double half = Atlas.d(kind, "width", 3) / 2;
            for (int k = 2; k + 2 < n && made < max; k += step * 2) {
                int x = p[k * 3], z = p[k * 3 + 1];
                if (x < rect[0] || x > rect[2] || z < rect[1] || z > rect[3]) continue;
                double tx = p[(k + 2) * 3] - p[(k - 2) * 3], tz = p[(k + 2) * 3 + 1] - p[(k - 2) * 3 + 1], tl = Math.hypot(tx, tz);
                if (tl < 1e-6) continue;
                tx /= tl; tz /= tl;
                for (int side = -1; side <= 1 && made < max; side += 2) {
                    double nx = -tz * side, nz = tx * side;
                    Map<String, Object> pick = sizes.get((int) (Noise.hash(a.seed, x, z, 98 + side) * sizes.size()));
                    double[] sz = Atlas.pt(pick.get("size"));
                    int w = (int) sz[0], d = (int) sz[1];
                    double off = half + setback + 2 + d / 2.0;
                    double cx = x + 0.5 + nx * off, cz = z + 0.5 + nz * off;
                    if (cx < rect[0] || cx > rect[2] || cz < rect[1] || cz > rect[3]) continue;
                    // its front to the road: the way back along the normal, to the nearest of the four
                    char facing = Math.abs(nx) > Math.abs(nz) ? (-nx > 0 ? 'e' : 'w') : (-nz > 0 ? 's' : 'n');
                    Map<String, Object> opts = new HashMap<>(Atlas.map(dd, "opts"));
                    opts.putAll(pick);
                    String hid = id + "_" + made;
                    opts.put("id", hid);
                    Structure h = new Structure(hid, Atlas.s(dd, "name", "House"), Atlas.s(pick, "template", Atlas.s(dd, "template", "building")), sid, opts,
                            Atlas.map(pick, "materials").isEmpty() ? st : st.with(Atlas.map(pick, "materials")), a.seed * 31 + hid.hashCode());
                    h.w = w; h.d = d; h.facing = facing;
                    if (lot(h, cx, cz, Atlas.i(dd, "search", 2), Atlas.i(dd, "margin", 1), Atlas.d(dd, "maxSlope", 6), false, false)) { structures.add(h); made++; }
                }
            }
        }
        if (made < Atlas.i(dd, "min", 0)) failures.add("district " + id + " holds only " + made + " houses (it asks for at least " + Atlas.i(dd, "min", 0) + ")");
        notes.add("district " + id + ": " + made + " houses");
    }

    // ------------------------------------------------------------------ walls and aqueducts
    private void planWalls() {
        for (Map<String, Object> w : Atlas.list(data, "walls")) {
            List<int[]> pts = new ArrayList<>();
            for (Object o : Atlas.arr(w, "points")) { double[] p = Atlas.pt(o); pts.add(new int[]{(int) a.x(p[0]), (int) a.z(p[1])}); }
            List<int[]> gates = new ArrayList<>();
            for (Object o : Atlas.arr(w, "gates")) { double[] p = Atlas.pt(o); gates.add(new int[]{(int) a.x(p[0]), (int) a.z(p[1])}); }
            Wall wall = new Wall(Atlas.s(w, "id", "wall"), style(Atlas.s(w, "style", "wilds")), pts, Atlas.i(w, "thick", 2), Atlas.i(w, "high", 7), !Boolean.FALSE.equals(w.get("closed")), gates);
            walls.add(wall);
            // the wall's line claimed (lots keep off it), its gateways where roads cross left as they are
            int n = pts.size();
            for (int k = 0; k + 1 < n + (wall.closed ? 1 : 0); k++) {
                int[] p = pts.get(k), q = pts.get((k + 1) % n);
                int steps = Math.max(Math.abs(q[0] - p[0]), Math.abs(q[1] - p[1]));
                for (int i = 0; i <= steps; i++) {
                    int x = p[0] + (q[0] - p[0]) * i / Math.max(1, steps), z = p[1] + (q[1] - p[1]) * i / Math.max(1, steps);
                    for (int ox = -wall.thick - 2; ox <= wall.thick + 3; ox++) for (int oz = -wall.thick - 2; oz <= wall.thick + 3; oz++) {
                        if (!t.in(x + ox, z + oz)) continue;
                        int ii = t.idx(x + ox, z + oz);
                        if ((t.flags[ii] & (Terrain.ROAD | Terrain.BRIDGE)) != 0) continue;
                        boolean atGate = false;
                        for (int[] g : wall.gates) if (Math.abs(g[0] - x - ox) <= 2 && Math.abs(g[1] - z - oz) <= 2) atGate = true;
                        if (atGate) continue;
                        boolean core = ox >= -1 && ox <= wall.thick && oz >= -1 && oz <= wall.thick;
                        t.flags[ii] |= core ? Terrain.BUILT : Terrain.YARD;
                        t.flags[ii] &= ~Terrain.TREE;
                    }
                }
            }
        }
    }
    private void planAqueducts() {
        for (Map<String, Object> q : Atlas.list(data, "aqueducts")) {
            List<int[]> line = new ArrayList<>();
            List<?> pts = Atlas.arr(q, "points");
            for (int k = 0; k + 1 < pts.size(); k++) {
                double[] p = Atlas.pt(pts.get(k)), r = Atlas.pt(pts.get(k + 1));
                int x0 = (int) a.x(p[0]), z0 = (int) a.z(p[1]), x1 = (int) a.x(r[0]), z1 = (int) a.z(r[1]);
                int steps = Math.max(Math.abs(x1 - x0), Math.abs(z1 - z0));
                for (int i = 0; i < steps; i++) line.add(new int[]{x0 + (x1 - x0) * i / steps, z0 + (z1 - z0) * i / steps});
            }
            if (line.isEmpty()) continue;
            Aqueduct aq = new Aqueduct(Atlas.s(q, "id", "aqueduct"), style(Atlas.s(q, "style", "wilds")), line, Atlas.i(q, "from", 70), Atlas.i(q, "to", 50));
            aqueducts.add(aq);
            for (int[] p : line) for (int ox = -2; ox <= 2; ox++) for (int oz = -2; oz <= 2; oz++) {
                if (!t.in(p[0] + ox, p[1] + oz)) continue;
                int i = t.idx(p[0] + ox, p[1] + oz);
                if ((t.flags[i] & (Terrain.ROAD | Terrain.BRIDGE)) != 0) continue;
                t.flags[i] |= Math.abs(ox) <= 1 && Math.abs(oz) <= 1 ? Terrain.BUILT : Terrain.YARD;
                t.flags[i] &= ~Terrain.TREE;
            }
            // the water runs down its length (the current, a stretch a few cells long at a time)
            for (int k = 0; k + 6 < line.size(); k += 6) {
                int[] p = line.get(k), r = line.get(k + 6);
                channels.add(new float[]{Math.min(p[0], r[0]) - 1, Math.min(p[1], r[1]) - 1, Math.max(p[0], r[0]) + 1, Math.max(p[1], r[1]) + 1, r[0] - p[0], r[1] - p[1]});
            }
        }
    }

    // ------------------------------------------------------------------ lanes: every door joined to the roads
    /**
     * From the step before every door, the shortest way over open ground (off every lot) to the road network, laid as a
     * lane - its ground eased so no step along it is higher than one cell. A door whose front is a square or a court
     * needs none.
     */
    private void lanes() {
        Map<String, Object> kinds = Atlas.map(a.map("roads"), "kinds");
        String laneKind = kinds.containsKey("lane") ? "lane" : "path";
        int made = 0;
        for (Structure s : structures) {
            if (Boolean.FALSE.equals(s.opts.get("lane")) || s.template.equals("plaza") || s.template.equals("field") || s.template.equals("stones") || s.template.equals("sunkbell")) continue;
            int[] f = frontCells(s);
            int[] out = switch (s.facing) { case 'n' -> new int[]{0, -1}; case 's' -> new int[]{0, 1}; case 'w' -> new int[]{-1, 0}; default -> new int[]{1, 0}; };
            int sx = f[0] + out[0] * 3, sz = f[1] + out[1] * 3;
            if (!t.in(sx, sz)) continue;
            List<int[]> path = route(sx, sz, Atlas.i(s.opts, "laneReach", 60));
            if (path == null) { if (!Boolean.FALSE.equals(s.opts.get("needsRoad"))) notes.add(s.id + ": no lane to a road within reach"); continue; }
            // the steps between the door and the start of the route, and the route itself drawn straight wherever it can
            // be (a lane runs from point to point, not round every cell's corner)
            List<int[]> all = new ArrayList<>();
            for (int k = 1; k <= 3; k++) all.add(new int[]{f[0] + out[0] * k, f[1] + out[1] * k});
            all.addAll(straighten(path));
            int prev = s.floor;
            for (int[] c : all) {
                if (!t.in(c[0], c[1])) continue;
                int i = t.idx(c[0], c[1]);
                if ((t.flags[i] & Terrain.ROAD) != 0) break;
                int y = Math.max(prev - 1, Math.min(prev + 1, t.top[i]));
                for (int[] nb : new int[][]{{0, 0}, {1, 0}, {0, 1}}) {
                    int x = c[0] + nb[0], z = c[1] + nb[1];
                    if (!t.in(x, z)) continue;
                    int j = t.idx(x, z);
                    if ((t.flags[j] & (Terrain.BUILT | Terrain.ROAD)) != 0 || t.water[j] >= 0) continue;
                    t.top[j] = y;
                    t.flags[j] |= Terrain.ROAD | Terrain.LANE;
                    t.flags[j] &= ~(Terrain.TREE | Terrain.CLIFF);
                    t.roadKind.put(j, laneKind);
                }
                prev = y;
            }
            made++;
        }
        notes.add(made + " lanes laid from doors to the roads");
    }
    /** A route's cells pulled taut: from each point, the farthest later point reachable in a clear straight line, the cells between drawn on that line. */
    private List<int[]> straighten(List<int[]> path) {
        List<int[]> out = new ArrayList<>();
        int k = 0;
        while (k < path.size() - 1) {
            int best = k + 1;
            for (int j = path.size() - 1; j > k + 1; j--) if (clearLine(path.get(k), path.get(j))) { best = j; break; }
            int[] p = path.get(k), q = path.get(best);
            int steps = Math.max(Math.abs(q[0] - p[0]), Math.abs(q[1] - p[1]));
            for (int s = 0; s < steps; s++) out.add(new int[]{p[0] + Math.round((q[0] - p[0]) * s / (float) steps), p[1] + Math.round((q[1] - p[1]) * s / (float) steps)});
            k = best;
        }
        if (!path.isEmpty()) out.add(path.get(path.size() - 1));
        return out;
    }
    private boolean clearLine(int[] p, int[] q) {
        int steps = Math.max(Math.abs(q[0] - p[0]), Math.abs(q[1] - p[1]));
        if (steps > 48) return false;
        int prev = t.top[t.idx(p[0], p[1])];
        for (int s = 1; s <= steps; s++) {
            int x = p[0] + Math.round((q[0] - p[0]) * s / (float) steps), z = p[1] + Math.round((q[1] - p[1]) * s / (float) steps);
            for (int[] d : new int[][]{{0, 0}, {1, 0}, {0, 1}}) {
                int i = t.idx(x + d[0], z + d[1]);
                if ((t.flags[i] & Terrain.BUILT) != 0 || (t.water[i] >= 0 && t.deck[i] < 0)) return false;
            }
            int i = t.idx(x, z);
            if (Math.abs(t.top[i] - prev) > 2) return false;
            prev = t.top[i];
        }
        return true;
    }

    /** A breadth-first walk over open ground to the nearest road cell, steps of at most one cell up or down. */
    private List<int[]> route(int sx, int sz, int reach) {
        int S = t.S;
        Map<Integer, Integer> from = new HashMap<>();
        ArrayDeque<int[]> q = new ArrayDeque<>();
        q.add(new int[]{sx, sz, 0});
        from.put(t.idx(sx, sz), -1);
        while (!q.isEmpty()) {
            int[] c = q.poll();
            int i = t.idx(c[0], c[1]);
            if ((t.flags[i] & Terrain.ROAD) != 0 && (t.flags[i] & Terrain.LANE) == 0) {
                List<int[]> path = new ArrayList<>();
                for (int k = i; k != -1; k = from.get(k)) path.add(0, new int[]{k / S, k % S});
                return path;
            }
            if (c[2] >= reach) continue;
            for (int[] dd : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int x = c[0] + dd[0], z = c[1] + dd[1];
                if (!t.in(x, z)) continue;
                int j = t.idx(x, z);
                if (from.containsKey(j) || (t.flags[j] & Terrain.BUILT) != 0 || (t.water[j] >= 0 && t.deck[j] < 0)) continue;
                if (Math.abs(t.top[j] - t.top[i]) > 2) continue;
                from.put(j, i);
                q.add(new int[]{x, z, c[2] + 1});
            }
        }
        return null;
    }

    /** The orchards' and groves' trees, placed with the plan (so a survey without cells grows the same trees). */
    private void orchards() {
        for (Structure s : structures) {
            if (!s.template.equals("orchard")) continue;
            Map<String, Object> o = new HashMap<>(s.opts);
            o.put("fenced", false);
            Kit.Out out = new Kit.Out();
            Kit.orchard(new Kit.Frame(null, s.x0, s.z0, s.w, s.d, s.facing, s.floor), s.style, new Kit.Opts(o), s.seed, out);
            t.extraTrees.addAll(out.trees); t.extraSpecies.addAll(out.treeSpecies);
        }
    }

    /** Where a new character wakes: the spawn structure's own point for it (its template knows its rooms), facing what it names. */
    private void spawnPoint() {
        Map<String, Object> sp = Atlas.map(data, "spawn");
        if (sp.isEmpty()) return;
        Structure s = byId(Atlas.s(sp, "structure", ""));
        if (s == null) { failures.add("the spawn's structure " + Atlas.s(sp, "structure", "") + " was not built"); return; }
        spawn = s.points.get(Atlas.s(sp, "point", "spawn"));
        spawnLook = s.points.get(Atlas.s(sp, "look", "spawnLook"));
        if (spawn == null || spawnLook == null) failures.add("the spawn's structure " + s.id + " has no point " + Atlas.s(sp, "point", "spawn"));
    }

    // ================================================================== 2. the raising
    /** Draws every planned structure into the cells (after the ground's columns are written), then checks them. */
    void raise() {
        if (data.isEmpty() || t.cells == null) return;
        Kit.Canvas c = new Kit.Canvas(t);
        for (Structure s : structures) {
            Kit.Template tp = Kit.TEMPLATES.get(s.template);
            if (tp == null) { failures.add(s.id + ": no template '" + s.template + "'"); continue; }
            Kit.Frame f = new Kit.Frame(c, s.x0, s.z0, s.w, s.d, s.facing, s.floor);
            Kit.Out out = new Kit.Out();
            tp.draw(f, s.style, new Kit.Opts(s.opts), s.seed, out);
            s.doors.addAll(out.doors); s.doorFloors.addAll(out.doorFloors); s.doorOut.addAll(out.doorOut); s.doorLeaf.addAll(out.doorLeaf);
            s.points.putAll(out.points);
            for (int k = 0; k < out.reserved.size(); k++) { int[] r = out.reserved.get(k); reserved.add(new Reserved(s.id, out.reservedKind.get(k), r[0], r[1], r[2], r[3], r[4], r[5])); }
            // the doors that hang in the doorways are built shut (their Door objects then find them as they should be)
            for (int k = 0; k < out.doors.size(); k++) if (out.doorLeaf.get(k)) for (int[] cell : out.doors.get(k)) for (int y = 1; y <= 2; y++) c.set(cell[0], out.doorFloors.get(k) + y, cell[1], Blocks.DOOR);
            if (Boolean.TRUE.equals(s.opts.get("water")) && !s.template.equals("pier") && !s.template.equals("weir") && !s.template.equals("sunkbell")) walks(c, s);
        }
        List<int[]> gates = new ArrayList<>();
        for (Wall w : walls) Kit.curtain(c, t, w.style, w.pts, w.thick, w.high, w.closed, w.gates, a.seed, new Kit.Out(), gates);
        for (Aqueduct q : aqueducts) aqueduct(c, q);
        bridges(c);
        streetLamps(c);
        spawnPoint();
        check(c);
    }

    /**
     * An aqueduct: a stone channel carried on piers from its spring down to its cistern, falling evenly, the water in it
     * running (its currents are Fluids'); where the ground rises over the channel's line it runs in a cut at the ground.
     */
    private void aqueduct(Kit.Canvas c, Aqueduct q) {
        int n = q.line.size();
        int wall = q.style.b("wall"), base = q.style.b("base"), trim = q.style.b("trim");
        for (int k = 0; k < n; k++) {
            int[] p = q.line.get(k);
            int[] nx = q.line.get(Math.min(n - 1, k + 1)), pv = q.line.get(Math.max(0, k - 1));
            int dx = Integer.signum(nx[0] - pv[0]), dz = Integer.signum(nx[1] - pv[1]);
            int sxa = dz != 0 ? 1 : 0, sza = dx != 0 ? 1 : 0;
            int deck = (int) Math.round(q.yFrom + (q.yTo - q.yFrom) * (k / (double) Math.max(1, n - 1)));
            for (int side = -1; side <= 1; side++) {
                int x = p[0] + sxa * side, z = p[1] + sza * side;
                if (!c.in(x, deck, z)) continue;
                int g = c.ground(x, z);
                if (g >= deck) for (int y = deck + 1; y <= g; y++) c.set(x, y, z, Blocks.AIR);
                // the piers every few cells, the arches' crowns between them
                // piers every fourth cell, round arches springing between them (the crown a course under the channel)
                boolean pier = k % 4 == 0;
                if (pier) for (int y = Math.min(g, deck - 1); y < deck; y++) c.set(x, y, z, base);
                else if (deck - g > 3) { c.set(x, deck - 1, z, wall); if (k % 4 != 2) c.set(x, deck - 2, z, wall); }
                else for (int y = g + 1; y < deck; y++) c.set(x, y, z, wall);
                c.set(x, deck, z, side == 0 ? base : wall);
                c.set(x, deck + 1, z, side == 0 ? Blocks.WATER : trim);
                c.set(x, deck + 2, z, Blocks.AIR);
            }
        }
        // the cistern at its foot: a basin the channel spills into
        int[] end = q.line.get(n - 1);
        int g = c.ground(end[0], end[1]);
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            boolean rim = Math.abs(dx) == 2 || Math.abs(dz) == 2;
            int x = end[0] + dx, z = end[1] + dz;
            c.set(x, g, z, base);
            c.set(x, g + 1, z, rim ? base : Blocks.WATER);
            for (int y = g + 2; y <= q.yTo; y++) c.set(x, y, z, rim && y == g + 2 ? trim : Blocks.AIR);
        }
        // the spout from the channel's end into the basin
        int deckEnd = q.yTo;
        for (int y = g + 2; y <= deckEnd + 1; y++) c.set(end[0], y, end[1], Blocks.WATER);
    }

    /** A building over the water (a pile hut, a boathouse): a walk of boards on posts from each door to the nearest dry ground. */
    private void walks(Kit.Canvas c, Structure s) {
        for (int k = 0; k < s.doors.size(); k++) {
            int[] out = s.doorOut.get(k);
            int y = s.doorFloors.get(k);
            // which way is the shore: straight out, or along the front to either side
            int[][] ways = {out, {out[1], out[0]}, {-out[1], -out[0]}};
            int best = -1, bestLen = 99;
            for (int w = 0; w < ways.length; w++) {
                int[] cell = s.doors.get(k)[0];
                int x = cell[0] + out[0], z = cell[1] + out[1];
                for (int step = 0; step < 24; step++) {
                    if (!t.in(x, z)) break;
                    int i = t.idx(x, z);
                    if (t.water[i] < 0 && t.top[i] >= y - 1 && (t.flags[i] & Terrain.BUILT) == 0) { if (step < bestLen) { bestLen = step; best = w; } break; }
                    x += ways[w][0]; z += ways[w][1];
                }
            }
            if (best < 0) { notes.add(s.id + ": no shore within reach of its door"); continue; }
            for (int[] cell : s.doors.get(k)) {
                int x = cell[0] + out[0], z = cell[1] + out[1];
                for (int step = 0; step <= bestLen + 1; step++) {
                    if (!t.in(x, z)) break;
                    int i = t.idx(x, z);
                    if (t.water[i] < 0 && t.top[i] >= y) break;
                    c.set(x, y, z, s.style.b("deck"));
                    c.set(x, y + 1, z, Blocks.AIR); c.set(x, y + 2, z, Blocks.AIR);
                    if (step % 3 == 0) c.footing(x, y - 1, z, s.style.b("post"));
                    x += ways[best][0]; z += ways[best][1];
                }
            }
        }
    }

    /** Every bridge given a kerb and a parapet along its deck, and piers down to the bed at intervals. */
    private void bridges(Kit.Canvas c) {
        Map<String, Object> kinds = Atlas.map(a.map("roads"), "kinds");
        int made = 0;
        for (int x = 1; x < t.S - 1; x++) for (int z = 1; z < t.S - 1; z++) {
            int i = t.idx(x, z);
            if (t.deck[i] < 0) continue;
            String kindName = t.roadKind.getOrDefault(i, "main");
            Map<String, Object> kind = Atlas.map(kinds, kindName);
            int deckB = Kit.block(Atlas.s(kind, "bridge", "planks"));
            boolean wood = Blocks.material(deckB) == Blocks.Material.WOOD;
            int rail = wood ? Blocks.FENCE : deckB;
            for (int[] dd : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int nx = x + dd[0], nz = z + dd[1];
                int j = t.idx(nx, nz);
                if (t.deck[j] >= 0 || (t.flags[j] & Terrain.ROAD) != 0) continue;
                if (t.water[j] < 0 && t.top[j] >= t.deck[i]) continue;
                // the kerb out over the water, the parapet on it
                c.set(nx, t.deck[i], nz, deckB);
                c.set(nx, t.deck[i] + 1, nz, rail);
                if (!wood && ((nx + nz) & 3) == 0) c.set(nx, t.deck[i] + 2, nz, rail);
                made++;
            }
            // a pier every few cells along: down from under the deck to the bed
            if (((x * 7 + z * 13) % 9) == 0) {
                for (int y = t.deck[i] - 1; y > t.top[i]; y--) c.set(x, y, z, wood ? Blocks.SPRUCE_LOG : Blocks.STONE_BRICKS);
            } else if (!wood) c.set(x, t.deck[i] - 1, z, deckB);
        }
        notes.add(made + " cells of bridge parapet");
    }

    /** Lamps along the streets of the settlements: a post at the street's edge every so many cells (data: the kind's 'lamps'). */
    private void streetLamps(Kit.Canvas c) {
        Map<String, Object> kinds = Atlas.map(a.map("roads"), "kinds");
        int made = 0;
        for (Terrain.Road r : t.roads) {
            int every = Atlas.i(Atlas.map(kinds, r.kind()), "lamps", 0);
            if (every <= 0) continue;
            double half = Atlas.d(Atlas.map(kinds, r.kind()), "width", 3) / 2 + 1;
            int[] p = r.path();
            int n = p.length / 3;
            for (int k = 2; k + 2 < n; k += every * 2) {
                double tx = p[(k + 2) * 3] - p[(k - 2) * 3], tz = p[(k + 2) * 3 + 1] - p[(k - 2) * 3 + 1], tl = Math.hypot(tx, tz);
                if (tl < 1e-6) continue;
                int side = (k / (every * 2)) % 2 == 0 ? 1 : -1;
                int x = (int) Math.round(p[k * 3] + 0.5 - tz / tl * half * side - 0.5), z = (int) Math.round(p[k * 3 + 1] + 0.5 + tx / tl * half * side - 0.5);
                if (!t.in(x, z)) continue;
                int i = t.idx(x, z);
                if ((t.flags[i] & (Terrain.ROAD | Terrain.BUILT | Terrain.BRIDGE)) != 0 || t.water[i] >= 0) continue;
                if (!rpg.world.DevMap.WILDS.equals(ShadowVeil.realmAt(a, x, z))) {
                    int g = c.ground(x, z);
                    c.set(x, g + 1, z, Blocks.SPRUCE_LOG); c.set(x, g + 2, z, Blocks.SPRUCE_LOG); c.set(x, g + 3, z, Blocks.SPRUCE_LOG); c.set(x, g + 4, z, Blocks.LAMP);
                    t.flags[i] |= Terrain.BUILT;
                    made++;
                }
            }
        }
        notes.add(made + " street lamps");
    }

    // ------------------------------------------------------------------ the checks
    /**
     * What was built, checked: every doorway open two cells high with a floor under it, a room or a court behind it and
     * open ground before it; no two lots and no two reserved volumes overlapping; no reserved volume breaching water.
     */
    private void check(Kit.Canvas c) {
        int doors = 0;
        for (Structure s : structures) for (int k = 0; k < s.doors.size(); k++) {
            int[][] cells = s.doors.get(k);
            int fy = s.doorFloors.get(k);
            int[] out = s.doorOut.get(k);
            doors++;
            for (int[] cell : cells) {
                int x = cell[0], z = cell[1];
                if (!openOrDoor(c.get(x, fy + 1, z)) || !openOrDoor(c.get(x, fy + 2, z))) failures.add(s.id + ": doorway at " + x + "," + z + " is not open two high");
                int ox = x + out[0], oz = z + out[1];
                if (!s.template.equals("mouth") && (solidAt(c, ox, fy + 1, oz) || solidAt(c, ox, fy + 2, oz)) && !solidAt(c, ox, fy, oz + 0) && !s.template.equals("castle"))
                    failures.add(s.id + ": doorway at " + x + "," + z + " opens onto a wall");
                if (!solidAt(c, ox, fy, oz) && !solidAt(c, ox, fy - 1, oz) && c.get(ox, fy, oz) != Blocks.WATER) failures.add(s.id + ": doorway at " + x + "," + z + " opens onto a drop");
            }
        }
        notes.add(structures.size() + " structures, " + doors + " doorways, " + reserved.size() + " reserved volumes");
        for (int i = 0; i < structures.size(); i++) for (int j = i + 1; j < structures.size(); j++) {
            Structure p = structures.get(i), q = structures.get(j);
            if (p.x0 <= q.x1 && q.x0 <= p.x1 && p.z0 <= q.z1 && q.z0 <= p.z1) failures.add("lots overlap: " + p.id + " and " + q.id);
        }
        List<Reserved> deep = new ArrayList<>();
        for (Reserved r : reserved) if (!r.kind().equals("interior")) deep.add(r);
        for (int i = 0; i < deep.size(); i++) for (int j = i + 1; j < deep.size(); j++)
            if (deep.get(i).overlaps(deep.get(j))) failures.add("reserved volumes overlap: " + deep.get(i).owner() + " " + deep.get(i).kind() + " and " + deep.get(j).owner() + " " + deep.get(j).kind());
        for (Reserved r : deep) {
            int wet = 0;
            for (int x = r.x0(); x <= r.x1(); x += 2) for (int z = r.z0(); z <= r.z1(); z += 2) for (int y = r.y0(); y <= r.y1(); y += 2) if (c.get(x, y, z) == Blocks.WATER) wet++;
            if (wet > 0) notes.add(r.owner() + " " + r.kind() + ": " + wet + " water samples inside its reserved volume (its roof must hold them out)");
            if (r.y0() < 1) failures.add(r.owner() + ": reserved volume below the world's floor");
        }
    }
    private static boolean openOrDoor(int b) { return b == Blocks.AIR || b == Blocks.DOOR; }
    private static boolean solidAt(Kit.Canvas c, int x, int y, int z) {
        int b = c.get(x, y, z);
        return b != Blocks.AIR && b != Blocks.WATER && !Blocks.isLeaves(b) && b != Blocks.LAMP && b != Blocks.FENCE;
    }

    // ------------------------------------------------------------------ questions
    /** The structure whose lot holds a cell, or null. */
    public Structure at(int x, int z) {
        for (Structure s : structures) if (s.contains(x, z)) return s;
        return null;
    }
    public Structure byId(String id) { for (Structure s : structures) if (s.id.equals(id)) return s; return null; }
}
