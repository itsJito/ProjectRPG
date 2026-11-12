package rpg.world.shadowveil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import rpg.world.Blocks;
import rpg.world.BuildingKit;

/**
 * (0.4.1, gamefixes: the exterior architecture of ShadowVeil) The builder's kit: the reusable templates every structure
 * of the world is raised from - houses and halls, towers, keeps and curtain walls, churches, domed temples, courtyard
 * ranges, stables, mills, market stalls, wells, piers, camps, ruins, stones and gibbets, and the mouths of the caves,
 * mines and crypts - each drawn in its own frame (front toward -v) and turned onto the world by the lot's facing.
 * <p>
 * A template knows shapes, never places or materials: where a structure stands and how big it is comes from the lot
 * ({@link Architecture}), what it is made of from its culture's style (data: {@code shadowveil_architecture.json}).
 * Everything is deterministic - the variation a template shows (a chimney's side, a window left out, a broken wall's
 * height) is hashed from the world seed and the lot, so the same design always raises the same town.
 */
final class Kit {
    private Kit() {}

    // ------------------------------------------------------------------ where blocks go
    /** The grown world's cells (Terrain's array, the engine's own order). */
    static final class Canvas {
        final Terrain t;
        final int S, H;
        Canvas(Terrain t) { this.t = t; S = t.S; H = t.H; }
        boolean in(int x, int y, int z) { return x >= 0 && z >= 0 && x < S && z < S && y >= 0 && y < H; }
        int get(int x, int y, int z) { return in(x, y, z) ? t.cells[(x * S + z) * H + y] & 0xFF : Blocks.AIR; }
        void set(int x, int y, int z, int id) { if (in(x, y, z)) t.cells[(x * S + z) * H + y] = (byte) id; }
        /** Sets a cell only where it is open air (leaves what is already built). */
        void soft(int x, int y, int z, int id) { if (in(x, y, z) && t.cells[(x * S + z) * H + y] == 0) t.cells[(x * S + z) * H + y] = (byte) id; }
        /** Fills down from y until ground (or 24 cells): a footing under whatever stands at y. */
        void footing(int x, int y, int z, int id) {
            for (int k = y; k > y - 24 && k > 0; k--) {
                int b = get(x, k, z);
                if (b != Blocks.AIR && b != Blocks.WATER && !Blocks.isLeaves(b)) return;
                set(x, k, z, id);
            }
        }
        /** The highest solid ground cell in a column (ignoring water and leaves). */
        int ground(int x, int z) {
            for (int y = H - 1; y > 0; y--) { int b = get(x, y, z); if (b != Blocks.AIR && b != Blocks.WATER && !Blocks.isLeaves(b)) return y; }
            return 0;
        }
    }

    // ------------------------------------------------------------------ a structure's own frame
    /**
     * A lot's frame: u across its front (0 .. w-1), v from its front back (0 .. d-1), y up from its floor. The front faces
     * the lot's facing ('n' toward -z, 's' +z, 'e' +x, 'w' -x); turning the frame is a rotation, never a mirror.
     */
    static final class Frame {
        final int ox, oz, w, d, P;
        final char facing;
        final Canvas c;
        Frame(Canvas c, int ox, int oz, int w, int d, char facing, int P) { this.c = c; this.ox = ox; this.oz = oz; this.w = w; this.d = d; this.facing = facing; this.P = P; }
        int wx(int u, int v) { return switch (facing) { case 's' -> ox + (w - 1 - u); case 'w' -> ox + v; case 'e' -> ox + (d - 1 - v); default -> ox + u; }; }
        int wz(int u, int v) { return switch (facing) { case 's' -> oz + (d - 1 - v); case 'w' -> oz + (w - 1 - u); case 'e' -> oz + u; default -> oz + v; }; }
        /** A local direction (du, dv) in the world: {dx, dz}. */
        int[] dir(int du, int dv) { return switch (facing) { case 's' -> new int[]{-du, -dv}; case 'w' -> new int[]{dv, -du}; case 'e' -> new int[]{-dv, du}; default -> new int[]{du, dv}; }; }
        /** The world cell of a local one: {x, y, z}. y is absolute. */
        void put(int u, int y, int v, int id) { c.set(wx(u, v), y, wz(u, v), id); }
        void soft(int u, int y, int v, int id) { c.soft(wx(u, v), y, wz(u, v), id); }
        int get(int u, int y, int v) { return c.get(wx(u, v), y, wz(u, v)); }
        void footing(int u, int y, int v, int id) { c.footing(wx(u, v), y, wz(u, v), id); }
        void fill(int u0, int y0, int v0, int u1, int y1, int v1, int id) {
            for (int u = Math.min(u0, u1); u <= Math.max(u0, u1); u++) for (int v = Math.min(v0, v1); v <= Math.max(v0, v1); v++)
                for (int y = Math.min(y0, y1); y <= Math.max(y0, y1); y++) put(u, y, v, id);
        }
        /** A two-faced wall cell: outside material out, its inside finish toward the room (local inward du, dv). */
        void wall(int u, int y, int v, int outer, int du, int dv) {
            int inner = BuildingKit.innerFinish(outer);
            if (inner == outer) { put(u, y, v, outer); return; }
            int[] wd = dir(du, dv);
            int in = wd[0] > 0 ? Blocks.IN_PX : wd[0] < 0 ? Blocks.IN_NX : wd[1] > 0 ? Blocks.IN_PZ : Blocks.IN_NZ;
            put(u, y, v, Blocks.dualOf(outer, inner, in));
        }
        /** A world point from local coordinates (cell centres): {x, z}. */
        float[] point(double u, double v) {
            int x0 = wx(0, 0), z0 = wz(0, 0), xu = wx(1, 0) - x0, zu = wz(1, 0) - z0, xv = wx(0, 1) - x0, zv = wz(0, 1) - z0;
            return new float[]{(float) (x0 + 0.5 + xu * u + xv * v), (float) (z0 + 0.5 + zu * u + zv * v)};
        }
    }

    // ------------------------------------------------------------------ what a culture builds with
    /** A culture's materials (data: the style's block names) and its roofs' pitch. */
    static final class Style {
        final Map<String, Integer> blocks = new HashMap<>();
        double pitch = 1;
        String roofShape = "gable";
        int b(String key) { Integer v = blocks.get(key); return v == null ? blocks.getOrDefault("wall", Blocks.STONE_BRICKS) : v; }
        /** This style with one material set outright (a block id). */
        Style withBlock(String key, int id) { Style s = with(Map.of()); s.blocks.put(key, id); return s; }
        Style with(Map<String, Object> over) {
            Style s = new Style();
            s.blocks.putAll(blocks); s.pitch = pitch; s.roofShape = roofShape;
            for (var e : over.entrySet()) {
                if (e.getKey().equals("pitch") && e.getValue() instanceof Number n) s.pitch = n.doubleValue();
                else if (e.getKey().equals("roofShape") && e.getValue() instanceof String r) s.roofShape = r;
                else if (e.getValue() instanceof String name && MATERIAL_KEYS.contains(e.getKey())) s.blocks.put(e.getKey(), block(name));
            }
            return s;
        }
    }
    static final List<String> MATERIAL_KEYS = List.of("wall", "base", "upper", "trim", "post", "roof", "glass", "floor", "chimney", "accent", "banner",
            "court", "fence", "column", "dome", "cloth", "deck", "lining", "rock");

    static int block(String name) {
        if (name == null || name.equals("none") || name.equals("air")) return Blocks.AIR;
        try { return Blocks.class.getField(name.toUpperCase(java.util.Locale.ROOT)).getInt(null); }
        catch (ReflectiveOperationException e) { throw new IllegalStateException("the architecture names a block the game does not have: " + name); }
    }

    // ------------------------------------------------------------------ what a template leaves behind
    /** Doors, named points and the volumes kept for interiors and underground places. */
    static final class Out {
        /** Each doorway: its cells {x, z} (one or two), the cell y its sill stands on (the floor one below), outward {dx, dz}. */
        final List<int[][]> doors = new ArrayList<>();
        final List<Integer> doorFloors = new ArrayList<>();
        final List<int[]> doorOut = new ArrayList<>();
        /** Whether each doorway hangs a door (a house's) or stands open (a gate, a court's arch, a cave's mouth). */
        final List<Boolean> doorLeaf = new ArrayList<>();
        final Map<String, float[]> points = new HashMap<>();
        /** Boxes {x0, y0, z0, x1, y1, z1} kept empty for what comes later (rooms; dungeons below). */
        final List<int[]> reserved = new ArrayList<>();
        final List<String> reservedKind = new ArrayList<>();
        /** Trees the structure plants (an orchard, a grove): {x, z} and the species. */
        final List<int[]> trees = new ArrayList<>();
        final List<String> treeSpecies = new ArrayList<>();
        void door(Frame f, int u, int v, int width, int floorY, int outDu, int outDv) {
            int[][] cells = new int[width][];
            for (int k = 0; k < width; k++) cells[k] = new int[]{f.wx(u + k, v), f.wz(u + k, v)};
            doors.add(cells); doorFloors.add(floorY); doorOut.add(f.dir(outDu, outDv)); doorLeaf.add(true);
        }
        /** An opening that hangs no door. */
        void gate(Frame f, int u, int v, int width, int floorY, int outDu, int outDv) { door(f, u, v, width, floorY, outDu, outDv); doorLeaf.set(doorLeaf.size() - 1, false); }
        void reserve(String kind, Frame f, int u0, int y0, int v0, int u1, int y1, int v1) {
            int ax = f.wx(u0, v0), az = f.wz(u0, v0), bx = f.wx(u1, v1), bz = f.wz(u1, v1);
            reserved.add(new int[]{Math.min(ax, bx), Math.min(y0, y1), Math.min(az, bz), Math.max(ax, bx), Math.max(y0, y1), Math.max(az, bz)});
            reservedKind.add(kind);
        }
    }

    /** A template's options (the lot's data), read with defaults. */
    record Opts(Map<String, Object> m) {
        int i(String k, int def) { return Atlas.i(m, k, def); }
        double d(String k, double def) { return Atlas.d(m, k, def); }
        String s(String k, String def) { return Atlas.s(m, k, def); }
        boolean b(String k, boolean def) { return m.get(k) instanceof Boolean v ? v : def; }
        boolean has(String k) { return m.containsKey(k); }
    }

    private static double hash(long seed, int a, int b, int salt) { return Noise.hash(seed, a, b, salt); }

    // =================================================================== templates
    /**
     * A building: a footing and floor, walls a storey at a time (a stone base course, the ground storey in the wall
     * material, the ones above in the upper material - jettied a cell over the front and back where the culture does),
     * corner posts, windows with lintels, a doorway in the front with its lintel, floors between the storeys (a stairwell
     * kept open), and a roof - gable, hipped, flat with a parapet (crenellated if asked), a dome, or a lean-to - with a
     * chimney stack up the gable where the culture keeps hearths.
     */
    static void building(Frame f, Style s, Opts o, long seed, Out out) {
        int w = f.w, d = f.d, P = f.P;
        int storeys = Math.max(1, o.i("storeys", 1)), wallH = Math.max(3, o.i("wallH", 4));
        boolean jetty = o.b("jetty", false) && storeys > 1;
        String roof = o.s("roof", s.roofShape);
        double pitch = o.d("pitch", s.pitch);
        if (o.b("stone", false)) s = s.withBlock("upper", s.b("wall"));
        int wall = s.b("wall"), base = s.b("base"), upper = s.b("upper"), post = s.b("post"), trim = s.b("trim"), glass = s.b("glass"), floor = s.b("floor");
        int top = P + storeys * wallH;
        // timber-framed storeys show their frame: a dark post every third cell up the plaster panels
        boolean framed = upper == Blocks.TIMBER_FRAME && o.b("beams", true);
        int doorW = o.i("doorW", w >= 6 ? 2 : 1), doorU = o.i("doorU", (w - doorW) / 2);
        boolean open = o.b("openFront", false);
        // the footing and the floor
        for (int u = 0; u < w; u++) for (int v = 0; v < d; v++) {
            boolean edge = u == 0 || v == 0 || u == w - 1 || v == d - 1;
            f.put(u, P, v, edge ? base : floor);
            f.footing(u, P - 1, v, base);
            for (int y = P + 1; y <= top; y++) f.put(u, y, v, Blocks.AIR);
        }
        // the walls, storey by storey
        for (int k = 0; k < storeys; k++) {
            int y0 = P + 1 + k * wallH, y1 = P + (k + 1) * wallH;
            int mat = k == 0 ? wall : upper;
            int jv = jetty && k > 0 ? 1 : 0;
            for (int y = y0; y <= y1; y++) {
                for (int u = 0; u < w; u++) for (int v = -jv; v < d + jv; v++) {
                    boolean side = u == 0 || u == w - 1, front = v == -jv, back = v == d - 1 + jv;
                    if (!side && !front && !back) continue;
                    if (jv > 0 && (v < 0 || v >= d) && !side && !front && !back) continue;
                    boolean corner = side && (front || back);
                    int m = k == 0 && y == y0 && !(open && front) ? base : mat;
                    if (corner) { f.put(u, y, v, post); continue; }
                    if (open && front && k == 0) { if (u % 3 == 0 && (u < doorU || u >= doorU + doorW)) f.put(u, y, v, post); continue; }
                    int du = u == 0 ? 1 : u == w - 1 ? -1 : 0, dv = front ? 1 : back ? -1 : 0;
                    if (framed && k > 0 && ((side ? v : u) % 3 == 0)) { f.put(u, y, v, trim); continue; }
                    f.wall(u, y, v, m, du, dv);
                }
            }
            // the jetty's floor out over the street, on brackets at the corners
            if (jv > 0) for (int u = 0; u < w; u++) {
                f.put(u, y0 - 1, -1, trim); f.put(u, y0 - 1, d, trim);
                if (u == 0 || u == w - 1) { f.put(u, y0 - 2, -1, trim); f.put(u, y0 - 2, d, trim); }
            }
            // the floor above the storey below, a stairwell kept at the back
            if (k > 0) for (int u = 1; u < w - 1; u++) for (int v = 1; v < d - 1; v++) {
                boolean well = u >= w - 3 && u <= w - 2 && v >= d - 3 && v <= d - 2;
                f.put(u, y0 - 1, v, well ? Blocks.AIR : floor);
            }
            if (k > 0) for (int u = 0; u < w; u++) { f.put(u, y0 - 1, -jv, trim); f.put(u, y0 - 1, d - 1 + jv, trim); }
            if (k > 0) for (int v = 0; v < d; v++) { f.put(0, y0 - 1, v, trim); f.put(w - 1, y0 - 1, v, trim); }
            // windows: two tall where the storey allows, a lintel over each; none beside a corner or the door
            int wy0 = y0 + 1, wy1 = wallH >= 6 ? y0 + 3 : wallH >= 4 ? y0 + 2 : y0 + 1;
            int spacing = o.i("windowSpacing", 3);
            for (int side = 0; side < 4; side++) {
                int len = side < 2 ? w : d;
                for (int a = 2; a < len - 2; a += spacing) {
                    if (hash(seed, a, side + k * 4, 71) < o.d("windowGaps", 0.12)) continue;
                    int u = side == 0 || side == 1 ? a : side == 2 ? 0 : w - 1;
                    int v = side == 0 ? -jv * (k > 0 ? 1 : 0) : side == 1 ? d - 1 + (k > 0 ? jv : 0) : a;
                    if (side == 0 && k == 0 && a >= doorU - 1 && a <= doorU + doorW) continue;
                    if (side == 0 && open && k == 0) continue;
                    for (int y = wy0; y <= wy1; y++) f.put(u, y, v, glass);
                    if (wy1 + 1 <= y1) f.put(u, wy1 + 1, v, trim);
                }
            }
        }
        // the doorway, its lintel and a step where the street is lower
        if (!open) {
            for (int k = 0; k < doorW; k++) {
                f.put(doorU + k, P + 1, 0, Blocks.AIR); f.put(doorU + k, P + 2, 0, Blocks.AIR);
                f.put(doorU + k, P, 0, floor);
                if (wallH >= 4) f.put(doorU + k, P + 3, 0, trim);
            }
            f.put(doorU - 1, P + 1, 0, post); f.put(doorU - 1, P + 2, 0, post); f.put(doorU + doorW, P + 1, 0, post); f.put(doorU + doorW, P + 2, 0, post);
        }
        if (open) out.gate(f, doorU, 0, Math.min(2, w - 2), P, 0, -1); else out.door(f, doorU, 0, doorW, P, 0, -1);
        out.reserve("interior", f, 1, P + 1, 1, w - 2, top, d - 2);
        out.points.put("inside", f.point(w / 2.0 - 0.5, Math.min(d - 2, 3)));
        out.points.put("door", f.point(doorU + doorW / 2.0 - 0.5, 0));
        // a back door on long buildings, onto the yard behind
        if (o.b("backDoor", d >= 10 && w >= 8)) {
            int bu = w / 2 - 1;
            for (int k = 0; k < 2; k++) { f.put(bu + k, P + 1, d - 1, Blocks.AIR); f.put(bu + k, P + 2, d - 1, Blocks.AIR); }
            out.door(f, bu, d - 1, 2, P, 0, 1);
        }
        roof(f, s, o, roof, pitch, top, jetty ? 1 : 0, seed);
        // towers at the front corners (a manor's, a keep's), or at all four
        String towers = o.s("towers", null);
        if (towers != null) {
            int tw = o.i("towerW", 5), th = top - P + o.i("towerRise", 5);
            int[][] at = towers.equals("four") ? new int[][]{{0, 0}, {w - 1, 0}, {0, d - 1}, {w - 1, d - 1}} : new int[][]{{0, 0}, {w - 1, 0}};
            for (int[] c : at) {
                Frame tf = sub(f, c[0] - tw / 2, c[1] - tw / 2, tw, tw);
                tower(tf, s, new Opts(new HashMap<>(Map.of("height", th, "shape", o.s("towerShape", "round"), "crown", o.s("towerCrown", "cone"), "door", false))), seed + c[0] * 7 + c[1], new Out());
            }
        }
        // a chimney up the gable end
        if (o.b("chimney", false)) {
            int cu = hash(seed, w, d, 72) < 0.5 ? 0 : w - 1, cv = Math.max(1, d / 2 - 1);
            int ridge = top + 1 + (int) Math.floor(Math.min(d, w) / 2.0 * pitch) + 2;
            int ch = s.b("chimney");
            for (int y = P + 1; y <= ridge; y++) f.put(cu, y, cv, ch);
            f.put(cu, ridge + 1, cv, Blocks.COBBLE);
        }
        // a banner over the door (where the culture hangs them)
        int banner = s.b("banner");
        if (o.b("banner", false) && banner != Blocks.AIR) for (int y = P + 4; y <= Math.min(top - 1, P + 6); y++) f.put(doorU + doorW / 2, y, -1, banner);
    }

    /**
     * A roof over a box whose walls top out at {@code top}: gable (ridge across the front, or along the depth), hip,
     * flat with a parapet (crenellated), a dome over a flat roof, a lean-to (falling to the front), or a pyramid.
     * The overhang is a cell all round; the gables are closed in the upper wall material.
     */
    static void roof(Frame f, Style s, Opts o, String shape, double pitch, int top, int jetty, long seed) {
        int w = f.w, d = f.d, ov = o.i("overhang", 1);
        int roofB = s.b("roof"), gable = s.b("upper"), trim = s.b("trim");
        int u0 = -ov, u1 = w - 1 + ov, v0 = -ov - jetty, v1 = d - 1 + ov + jetty;
        boolean ridgeU = o.s("ridge", w >= d ? "u" : "v").equals("u");
        switch (shape) {
            case "flat", "dome", "crenel" -> {
                for (int u = 0; u < w; u++) for (int v = 0; v < d; v++) f.put(u, top + 1, v, s.b("base"));
                boolean cren = shape.equals("crenel") || o.b("crenel", false);
                for (int u = 0; u < w; u++) for (int v = 0; v < d; v++) {
                    if (u != 0 && v != 0 && u != w - 1 && v != d - 1) continue;
                    f.put(u, top + 2, v, s.b("wall"));
                    if (cren && (u + v) % 2 == 0) f.put(u, top + 3, v, s.b("wall"));
                }
                if (shape.equals("dome")) dome(f, s.b("dome"), (w - 1) / 2.0, (d - 1) / 2.0, Math.min(w, d) / 2.0 - 1.5, top + 2, o.b("finial", true) ? s.b("accent") : Blocks.AIR);
            }
            case "lean" -> {
                for (int u = u0; u <= u1; u++) for (int v = v0; v <= v1; v++) {
                    int h = (int) Math.floor((v - v0) * pitch * 0.5);
                    f.put(u, top + 1 + h, v, roofB);
                    if ((u == 0 || u == w - 1) && v >= 0 && v < d) for (int y = top + 1; y < top + 1 + h; y++) f.put(u, y, v, gable);
                }
            }
            case "none" -> { }
            default -> {
                boolean hip = shape.equals("hip") || shape.equals("pyramid");
                int peak = 0;
                for (int u = u0; u <= u1; u++) for (int v = v0; v <= v1; v++) {
                    double across = ridgeU ? Math.min(v - v0, v1 - v) : Math.min(u - u0, u1 - u);
                    if (hip) across = Math.min(across, ridgeU ? Math.min(u - u0, u1 - u) + (shape.equals("pyramid") ? 0 : (u1 - u0 - (v1 - v0)) / 2.0) : Math.min(v - v0, v1 - v) + (shape.equals("pyramid") ? 0 : (v1 - v0 - (u1 - u0)) / 2.0));
                    int h = (int) Math.floor(Math.max(0, across) * pitch);
                    peak = Math.max(peak, h);
                    f.put(u, top + 1 + h, v, roofB);
                    // a pitch under one: the step below is filled so the roof reads as one sheet
                    if (pitch < 1 && h > 0) f.soft(u, top + h, v, roofB);
                    // the gables, closed in under the roof line
                    boolean gableEnd = ridgeU ? (u == 0 || u == w - 1) : (v == -jetty || v == d - 1 + jetty);
                    boolean within = ridgeU ? v >= -jetty && v <= d - 1 + jetty : u >= 0 && u <= w - 1;
                    if (!hip && gableEnd && within) for (int y = top + 1; y < top + 1 + h; y++) f.put(u, y, v, gable);
                    // the attic closed under a hip
                    if (hip && u >= 0 && u < w && v >= -jetty && v <= d - 1 + jetty && h > 0 && (u == 0 || u == w - 1 || v == -jetty || v == d - 1 + jetty))
                        for (int y = top + 1; y < top + 1 + h; y++) f.put(u, y, v, gable);
                }
                // the ridge capped in the trim
                for (int u = u0; u <= u1; u++) for (int v = v0; v <= v1; v++) {
                    double across = ridgeU ? Math.min(v - v0, v1 - v) : Math.min(u - u0, u1 - u);
                    if (!hip && (int) Math.floor(across * pitch) == peak && peak > 0 && f.get(u, top + 1 + peak, v) == roofB && o.b("ridgeCap", true)) f.put(u, top + 2 + peak, v, roofB);
                }
            }
        }
    }

    /** A dome: a half-ellipsoid of cells about (cu, cv), radius r, standing on y0, a finial at its crown. */
    static void dome(Frame f, int mat, double cu, double cv, double r, int y0, int finial) {
        if (r < 1.5) return;
        int R = (int) Math.ceil(r);
        double ry = r * 0.95;
        for (int du = -R; du <= R; du++) for (int dv = -R; dv <= R; dv++) for (int dy = 0; dy <= (int) Math.ceil(ry); dy++) {
            double q = (du * du + dv * dv) / (r * r) + dy * dy / (ry * ry);
            double qi = (du * du + dv * dv) / ((r - 1.1) * (r - 1.1)) + dy * dy / ((ry - 1.1) * (ry - 1.1));
            if (q <= 1.0 && qi > 1.0) f.put((int) Math.round(cu + du), y0 + dy, (int) Math.round(cv + dv), mat);
        }
        if (finial != Blocks.AIR) { f.put((int) Math.round(cu), y0 + (int) Math.ceil(ry) + 1, (int) Math.round(cv), finial); }
    }

    /**
     * A tower: round (radius from the lot) or square, a door at the foot of its front, arrow slits climbing it, floors
     * every few storeys, and a crown - crenellations, a conical roof or a pyramid - its height from the data.
     */
    static void tower(Frame f, Style s, Opts o, long seed, Out out) {
        int w = f.w, d = f.d, P = f.P, H = o.has("topY") ? Math.max(8, o.i("topY", 0) - P) : o.i("height", 12);
        boolean round = o.s("shape", "round").equals("round");
        double cu = (w - 1) / 2.0, cv = (d - 1) / 2.0, r = Math.min(w, d) / 2.0;
        int wall = s.b("wall"), base = s.b("base"), glass = s.b("glass"), floor = s.b("floor"), trim = s.b("trim");
        java.util.function.BiPredicate<Integer, Integer> inside = (u, v) -> round ? Math.hypot(u - cu, v - cv) <= r - 0.15 : true;
        java.util.function.BiPredicate<Integer, Integer> rim = (u, v) -> {
            if (!inside.test(u, v)) return false;
            for (int[] n : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) { int a = u + n[0], b = v + n[1]; if (a < 0 || b < 0 || a >= w || b >= d || !inside.test(a, b)) return true; }
            return false;
        };
        for (int u = 0; u < w; u++) for (int v = 0; v < d; v++) {
            if (!inside.test(u, v)) continue;
            f.put(u, P, v, floor); f.footing(u, P - 1, v, base);
            for (int y = P + 1; y <= P + H; y++) {
                if (rim.test(u, v)) f.put(u, y, v, y == P + 1 ? base : wall);
                else f.put(u, y, v, (y - P) % 6 == 0 ? floor : Blocks.AIR);
            }
        }
        // slits up the tower, a quarter turn apart each storey
        for (int y = P + 4, k = 0; y < P + H - 1; y += 4, k++) {
            int[][] spots = {{(int) Math.round(cu), 0}, {w - 1, (int) Math.round(cv)}, {(int) Math.round(cu), d - 1}, {0, (int) Math.round(cv)}};
            int[] sp = spots[(k + 1) % 4];
            int u = sp[0], v = sp[1];
            while (v < d && !rim.test(u, v)) v += sp[1] == 0 ? 1 : 0;
            if (rim.test(u, v)) f.put(u, y, v, glass);
        }
        // the door at the foot of the front
        if (o.b("door", true)) {
            int du = (int) Math.round(cu) - (w >= 6 ? 1 : 0), dw = w >= 6 ? 2 : 1;
            int dv = 0;
            while (dv < d && !inside.test(du, dv)) dv++;
            for (int k = 0; k < dw; k++) for (int y = P + 1; y <= P + 2; y++) { f.put(du + k, y, dv, Blocks.AIR); f.put(du + k, y, dv + 1, Blocks.AIR); }
            out.door(f, du, dv, dw, P, 0, -1);
            f.put(du, P + 3, dv, trim); if (dw > 1) f.put(du + 1, P + 3, dv, trim);
        }
        out.reserve("interior", f, 1, P + 1, 1, w - 2, P + H, d - 2);
        String crown = o.s("crown", "crenel");
        int topY = P + H;
        if (crown.equals("crenel") || crown.equals("cone") || crown.equals("pyramid")) {
            // the fighting top: a floor, a corbel a cell out, merlons on it
            for (int u = -1; u <= w; u++) for (int v = -1; v <= d; v++) {
                boolean in = u >= 0 && v >= 0 && u < w && v < d && inside.test(u, v);
                boolean near = in;
                if (!in) for (int[] n : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) { int a = u + n[0], b = v + n[1]; if (a >= 0 && b >= 0 && a < w && b < d && inside.test(a, b)) near = true; }
                if (!near) continue;
                if (in && !rim.test(u, v)) { f.put(u, topY, v, floor); continue; }
                f.put(u, topY, v, trim);
                if (crown.equals("crenel")) { f.put(u, topY + 1, v, wall); if ((u + v) % 2 == 0) f.put(u, topY + 2, v, wall); }
            }
        }
        if (crown.equals("cone") || crown.equals("pyramid")) {
            double rr = r + 1;
            int h = (int) Math.ceil(rr * o.d("spire", 1.6));
            for (int dy = 0; dy <= h; dy++) {
                double rad = rr * (1 - dy / (double) (h + 1));
                for (int u = -2; u <= w + 1; u++) for (int v = -2; v <= d + 1; v++) {
                    double dist = crown.equals("cone") ? Math.hypot(u - cu, v - cv) : Math.max(Math.abs(u - cu), Math.abs(v - cv));
                    if (dist <= rad && dist > rad - 1.3) f.put(u, topY + 1 + dy, v, s.b("roof"));
                }
            }
            f.put((int) Math.round(cu), topY + 2 + h, (int) Math.round(cv), s.b("accent"));
        }
        if (crown.equals("lantern")) lanternCrown(f, s, (int) Math.round(cu), (int) Math.round(cv), (int) Math.floor(r), topY);
        // a cellar stair down from the tower's floor (the Lantern's cellars), its volume reserved under the isle
        if (o.has("cellar")) stairDown(f, s, (int) Math.round(cu) - 1, (int) Math.round(cv) - 1, P, o.i("cellar", 10), 's', out, o.s("cellarReserve", "cellar"));
        out.points.put("top", new float[]{f.point(cu, cv)[0], topY + 1, f.point(cu, cv)[1]});
    }

    /** The Unlit Lantern's crown: a glazed lamp room on a corbelled gallery, a cap over it, the cold brazier inside. */
    static void lanternCrown(Frame f, Style s, int cu, int cv, int r, int topY) {
        for (int u = cu - r - 1; u <= cu + r + 1; u++) for (int v = cv - r - 1; v <= cv + r + 1; v++) {
            double dd = Math.hypot(u - cu, v - cv);
            if (dd <= r + 1.2) f.put(u, topY, v, s.b("trim"));
            if (dd <= r + 1.2 && dd > r + 0.2) f.put(u, topY + 1, v, s.b("fence"));
            if (dd <= r - 0.6 && dd > r - 1.8) for (int y = topY + 1; y <= topY + 4; y++) f.put(u, y, v, (u + v) % 3 == 0 ? s.b("post") : s.b("glass"));
            if (dd <= r - 0.6) f.put(u, topY + 5, v, s.b("roof"));
            if (dd <= r - 2) f.put(u, topY + 6, v, s.b("roof"));
        }
        f.put(cu, topY + 1, cv, s.b("accent"));
    }

    /**
     * A curtain wall along a run of world points (cells), as thick and as high as asked, following the ground: a walk
     * along its top behind merlons, and wherever a road crosses it a gateway arched over the road with a tower either
     * side. Corner towers stand at the run's turns.
     */
    static void curtain(Canvas c, Terrain t, Style s, List<int[]> pts, int thick, int high, boolean closed, List<int[]> gatesIn, long seed, Out out, List<int[]> gatesOut) {
        int wall = s.b("wall"), trim = s.b("trim"), base = s.b("base");
        List<int[]> line = new ArrayList<>();
        int n = pts.size();
        for (int k = 0; k + 1 < n + (closed ? 1 : 0); k++) {
            int[] a = pts.get(k), b = pts.get((k + 1) % n);
            int steps = Math.max(Math.abs(b[0] - a[0]), Math.abs(b[1] - a[1]));
            for (int i = 0; i < steps; i++) line.add(new int[]{a[0] + (b[0] - a[0]) * i / steps, a[1] + (b[1] - a[1]) * i / steps});
        }
        // where roads cross, the gaps (with the run's index), merged
        boolean[] gap = new boolean[line.size()];
        for (int i = 0; i < line.size(); i++) {
            int[] p = line.get(i);
            for (int dx = -thick; dx <= thick && !gap[i]; dx++) for (int dz = -thick; dz <= thick && !gap[i]; dz++) {
                int x = p[0] + dx, z = p[1] + dz;
                if (t.in(x, z) && (t.flags[t.idx(x, z)] & (Terrain.ROAD | Terrain.RIVER | Terrain.LAKE)) != 0 && (t.flags[t.idx(x, z)] & Terrain.BUILT) == 0) gap[i] = true;
            }
            for (int[] g : gatesIn) if (Math.abs(g[0] - p[0]) <= 1 && Math.abs(g[1] - p[1]) <= 1) gap[i] = true;
        }
        boolean[] wide = gap.clone();
        for (int i = 0; i < line.size(); i++) if (gap[i]) for (int j = Math.max(0, i - 2); j <= Math.min(line.size() - 1, i + 2); j++) wide[j] = true;
        for (int i = 0; i < line.size(); i++) {
            int[] p = line.get(i);
            for (int ox = 0; ox < thick; ox++) for (int oz = 0; oz < thick; oz++) {
                int x = p[0] + ox, z = p[1] + oz;
                if (!t.in(x, z)) continue;
                int i2 = t.idx(x, z);
                if ((t.flags[i2] & (Terrain.RIVER | Terrain.LAKE | Terrain.SEA)) != 0) continue;
                int g = c.ground(x, z);
                if (wide[i]) {
                    // the gateway: an arch over the road, the wall carried over it
                    for (int y = g + 1; y <= g + high; y++) c.set(x, y, z, y >= g + 6 ? wall : Blocks.AIR);
                    c.set(x, g + 5, z, trim);
                    if (high >= 6) for (int y = g + 6; y <= g + high; y++) c.set(x, y, z, wall);
                    c.set(x, g + high + 1, z, wall);
                    continue;
                }
                c.footing(x, g, z, base);
                for (int y = g + 1; y <= g + high; y++) c.set(x, y, z, y == g + 1 ? base : wall);
                c.set(x, g + high + 1, z, ((p[0] + p[1]) & 1) == 0 && (ox == 0 || oz == 0) ? wall : Blocks.AIR);
                if ((ox == 0 || oz == 0) && ((p[0] + p[1]) & 1) == 0) c.set(x, g + high + 2, z, Blocks.AIR);
            }
        }
        // the gate towers either side of each gateway, and towers at the turns
        List<int[]> towers = new ArrayList<>();
        for (int i = 1; i < line.size(); i++) if (wide[i] != wide[i - 1]) towers.add(line.get(wide[i] ? i - 1 : i));
        for (int k = 0; k < n; k++) if (closed || (k > 0 && k < n - 1)) towers.add(pts.get(k));
        for (int i = 0; i < line.size(); i++) if (gap[i] && (i == 0 || !gap[i - 1])) gatesOut.add(line.get(i));
        for (int[] tw : towers) {
            int g = c.ground(tw[0], tw[1]);
            int R = thick + 1;
            for (int dx = -R; dx <= R + thick - 1; dx++) for (int dz = -R; dz <= R + thick - 1; dz++) {
                int x = tw[0] + dx, z = tw[1] + dz;
                if (!t.in(x, z) || (t.flags[t.idx(x, z)] & (Terrain.ROAD | Terrain.RIVER | Terrain.LAKE | Terrain.SEA)) != 0) continue;
                double dd = Math.hypot(dx - (thick - 1) / 2.0, dz - (thick - 1) / 2.0);
                if (dd > R + 0.4) continue;
                int gg = c.ground(x, z);
                c.footing(x, gg, z, base);
                for (int y = gg + 1; y <= g + high + 3; y++) c.set(x, y, z, dd > R - 0.8 ? (y == gg + 1 ? base : wall) : (y == g + high ? trim : Blocks.AIR));
                if (dd > R - 0.8 && ((x + z) & 1) == 0) c.set(x, g + high + 4, z, wall);
            }
        }
    }

    /** A courtyard range: an outer ring of rooms round an open court, a gate through the front, doors into the ring from the court. */
    static void courtyard(Frame f, Style s, Opts o, long seed, Out out) {
        int w = f.w, d = f.d, P = f.P, ring = o.i("ring", 6), wallH = o.i("wallH", 5), storeys = o.i("storeys", 1);
        int wall = s.b("wall"), base = s.b("base"), floor = s.b("floor"), court = s.b("court"), glass = s.b("glass"), trim = s.b("trim");
        int top = P + wallH * storeys;
        for (int u = 0; u < w; u++) for (int v = 0; v < d; v++) {
            boolean inCourt = u >= ring && v >= ring && u < w - ring && v < d - ring;
            f.footing(u, P - 1, v, base);
            f.put(u, P, v, inCourt ? court : floor);
            for (int y = P + 1; y <= top + 3; y++) f.put(u, y, v, Blocks.AIR);
            if (inCourt) continue;
            boolean outer = u == 0 || v == 0 || u == w - 1 || v == d - 1;
            boolean inner = (u == ring - 1 || u == w - ring) && v >= ring - 1 && v <= d - ring || (v == ring - 1 || v == d - ring) && u >= ring - 1 && u <= w - ring;
            if (outer || inner) {
                for (int y = P + 1; y <= top; y++) {
                    int du = u == 0 ? 1 : u == w - 1 ? -1 : u == ring - 1 ? -1 : u == w - ring ? 1 : 0;
                    int dv = v == 0 ? 1 : v == d - 1 ? -1 : v == ring - 1 ? -1 : v == d - ring ? 1 : 0;
                    if (du != 0 && dv != 0) f.put(u, y, v, s.b("post")); else f.wall(u, y, v, y == P + 1 ? base : wall, du, dv);
                }
                // windows out and onto the court
                int a = (u == 0 || u == w - 1 || u == ring - 1 || u == w - ring) ? v : u;
                if (a % 4 == 2 && !(u == 0 || u == w - 1) == !(v == 0 || v == d - 1) && hash(seed, u, v, 73) > 0.15)
                    for (int k = 0; k < storeys; k++) { f.put(u, P + 2 + k * wallH, v, glass); f.put(u, P + 3 + k * wallH, v, glass); }
            }
            for (int k = 1; k < storeys; k++) f.put(u, P + k * wallH, v, floor);
            f.put(u, top + 1, v, base);
        }
        // the parapet round the outside and the court, crenellated where asked
        boolean cren = o.b("crenel", false);
        for (int u = 0; u < w; u++) for (int v = 0; v < d; v++) {
            boolean inCourt = u >= ring && v >= ring && u < w - ring && v < d - ring;
            if (inCourt) continue;
            boolean edge = u == 0 || v == 0 || u == w - 1 || v == d - 1 || u == ring - 1 || v == ring - 1 || u == w - ring || v == d - ring;
            if (edge) { f.put(u, top + 2, v, wall); if (cren && (u + v) % 2 == 0 && (u == 0 || v == 0 || u == w - 1 || v == d - 1)) f.put(u, top + 3, v, wall); }
        }
        // the gate through the front range
        int gw = o.i("gateW", 3), gu = (w - gw) / 2;
        for (int k = 0; k < gw; k++) for (int v = 0; v < ring; v++) {
            f.put(gu + k, P, v, court);
            for (int y = P + 1; y <= P + 3; y++) f.put(gu + k, y, v, Blocks.AIR);
            f.put(gu + k, P + 4, v, trim);
        }
        out.gate(f, gu, 0, Math.min(2, gw), P, 0, -1);
        // doors from the court into each range
        int cw = w - 2 * ring, cd = d - 2 * ring;
        if (cw > 2 && cd > 2) {
            int[][] inner = {{ring + cw / 2 + 2, ring - 1}, {ring + cw / 2, d - ring}, {ring - 1, ring + cd / 2}, {w - ring, ring + cd / 2}};
            for (int[] dd : inner) { f.put(dd[0], P + 1, dd[1], Blocks.AIR); f.put(dd[0], P + 2, dd[1], Blocks.AIR); }
        }
        out.reserve("interior", f, 1, P + 1, 1, w - 2, top, d - 2);
        out.points.put("court", f.point(w / 2.0 - 0.5, d / 2.0 - 0.5));
        if (o.b("well", true)) well(f, s, w / 2, d / 2, P);
        if (o.has("dome")) {
            Opts dm = new Opts(Atlas.map(o.m, "dome"));
            double du = dm.d("u", w / 2.0 - 0.5), dv = dm.d("v", d - ring / 2.0 - 0.5), r = dm.d("r", ring / 2.0 + 1);
            for (int u = (int) (du - r); u <= (int) (du + r); u++) for (int v = (int) (dv - r); v <= (int) (dv + r); v++)
                if (Math.hypot(u - du, v - dv) <= r && f.get(u, top + 2, v) != Blocks.AIR) f.put(u, top + 2, v, Blocks.AIR);
            dome(f, s.b("dome"), du, dv, r, top + 2, s.b("accent"));
        }
        // minarets at the corners where asked
        if (o.b("minarets", false)) for (int[] cc : new int[][]{{0, 0}, {w - 1, 0}, {0, d - 1}, {w - 1, d - 1}}) {
            for (int y = P + 1; y <= top + 9; y++) for (int a = -1; a <= 1; a++) for (int b = -1; b <= 1; b++) if (Math.abs(a) + Math.abs(b) <= 1 || y > top + 6) f.put(cc[0] + a, y, cc[1] + b, y > top + 7 ? s.b("trim") : wall);
            f.put(cc[0], top + 10, cc[1], s.b("dome"));
            f.put(cc[0], top + 11, cc[1], s.b("accent"));
        }
    }

    /** A well: a stone curb round a cell of water, two posts and a little roof over it. */
    static void well(Frame f, Style s, int cu, int cv, int P) {
        int curb = s.b("base"), post = s.b("post"), roof = s.b("roof");
        for (int a = -1; a <= 1; a++) for (int b = -1; b <= 1; b++) {
            if (a == 0 && b == 0) { f.put(cu, P, cv, Blocks.WATER); f.put(cu, P - 1, cv, Blocks.WATER); f.put(cu, P + 1, cv, Blocks.AIR); continue; }
            f.put(cu + a, P + 1, cv + b, curb);
        }
        f.put(cu - 1, P + 2, cv, post); f.put(cu + 1, P + 2, cv, post); f.put(cu - 1, P + 3, cv, post); f.put(cu + 1, P + 3, cv, post);
        for (int a = -1; a <= 1; a++) for (int b = -1; b <= 1; b++) f.put(cu + a, P + 4, cv + b, roof);
        f.put(cu, P + 5, cv, roof);
    }

    /**
     * A keep in a curtain: the ward walled round (corner towers, a gatehouse over the front gate), the court paved, and
     * the keep itself at the back - a tall hall of storeys under a crenellated roof, towers at its front corners, its
     * door facing the gate. The undercroft's stair goes down from a corner of the court.
     */
    static void castle(Frame lot, Style s, Opts o, long seed, Out out) {
        // the ward behind the gatehouse, which stands out two cells in front of the curtain (inside the lot)
        Frame f = sub(lot, 0, 2, lot.w, lot.d - 2);
        int w = f.w, d = f.d, P = f.P, wallH = o.i("wallH", 8), thick = 2;
        int wall = s.b("wall"), base = s.b("base"), court = s.b("court"), trim = s.b("trim");
        // the court
        for (int u = 0; u < w; u++) for (int v = 0; v < d; v++) {
            f.footing(u, P - 1, v, base); f.put(u, P, v, court);
            for (int y = P + 1; y <= P + 30; y++) f.put(u, y, v, Blocks.AIR);
        }
        // the curtain: thick walls round the ward, a walk along the top behind merlons
        int gw = o.i("gateW", 4), gu = (w - gw) / 2;
        for (int u = 0; u < w; u++) for (int v = 0; v < d; v++) {
            boolean ring = u < thick || v < thick || u >= w - thick || v >= d - thick;
            if (!ring) continue;
            boolean gate = v < thick && u >= gu && u < gu + gw;
            for (int y = P + 1; y <= P + wallH; y++) {
                if (gate && y <= P + 5) continue;
                f.put(u, y, v, y == P + 1 ? base : y == P + 6 && gate ? trim : wall);
            }
            boolean outerEdge = u == 0 || v == 0 || u == w - 1 || v == d - 1;
            if (outerEdge && (u + v) % 2 == 0) f.put(u, P + wallH + 1, v, wall);
            if (outerEdge) f.put(u, P + wallH + 1, v, (u + v) % 2 == 0 ? wall : Blocks.AIR);
        }
        // the corner towers: round, a storey over the wall
        int tr = o.i("towerR", 4), th = wallH + 5;
        // inset so each tower stands wholly inside the lot, the curtain running into it
        for (int[] cc : new int[][]{{tr - 1, tr - 1}, {w - tr, tr - 1}, {tr - 1, d - tr}, {w - tr, d - tr}}) roundTower(f, s, cc[0], cc[1], tr, P, th, true);
        // the gatehouse: two towers flanking the gate, a passage under a chamber
        int gh = wallH + 4;
        for (int side = 0; side < 2; side++) {
            int u0 = side == 0 ? gu - 4 : gu + gw, u1 = u0 + 3;
            for (int u = u0; u <= u1; u++) for (int v = -2; v <= 3; v++) {
                boolean rimC = u == u0 || u == u1 || v == -2 || v == 3;
                for (int y = P + 1; y <= P + gh; y++) f.put(u, y, v, rimC ? (y == P + 1 ? base : wall) : (y == P + wallH ? s.b("floor") : Blocks.AIR));
                f.put(u, P, v, court);
                if (rimC && (u + v) % 2 == 0) f.put(u, P + gh + 1, v, wall);
            }
            f.put((u0 + u1) / 2, P + 4, -2, s.b("glass")); f.put((u0 + u1) / 2, P + wallH + 2, -2, s.b("glass"));
        }
        for (int u = gu; u < gu + gw; u++) for (int v = -2; v <= 3; v++) {
            f.put(u, P, v, court);
            for (int y = P + 1; y <= P + 5; y++) f.put(u, y, v, Blocks.AIR);
            f.put(u, P + 6, v, trim);
            for (int y = P + 7; y <= P + gh; y++) f.put(u, y, v, (v == -2 || v == 3) ? wall : y == P + gh ? s.b("floor") : Blocks.AIR);
            if ((v == -2) && u % 2 == 0) f.put(u, P + gh + 1, v, wall);
        }
        out.gate(f, gu + gw / 2 - 1, -2, 2, P, 0, -1);
        out.points.put("gate", f.point(gu + gw / 2.0 - 0.5, -3));
        // the keep at the back of the ward
        int kw = o.i("keepW", Math.max(12, w / 2)), kd = o.i("keepD", Math.max(10, d / 3)), ks = o.i("keepStoreys", 3), kwh = o.i("keepWallH", 5);
        int ku = (w - kw) / 2, kv = d - thick - kd - 1;
        Frame kf = sub(f, ku, kv, kw, kd);
        Map<String, Object> keepOpts = new HashMap<>(Map.of("storeys", ks, "wallH", kwh, "roof", "crenel", "doorW", 2, "windowSpacing", 3, "backDoor", false, "stone", true));
        Out ko = new Out();
        building(kf, s.withBlock("trim", s.b("base")), new Opts(keepOpts), seed + 1, ko);
        out.doors.addAll(ko.doors); out.doorFloors.addAll(ko.doorFloors); out.doorOut.addAll(ko.doorOut); out.doorLeaf.addAll(ko.doorLeaf);
        out.reserved.addAll(ko.reserved); out.reservedKind.addAll(ko.reservedKind);
        int ktop = P + ks * kwh;
        for (int[] cc : new int[][]{{ku, kv}, {ku + kw - 1, kv}}) roundTower(f, s, cc[0], cc[1], 3, P, ktop - P + 6, false);
        // the keep's back towers (square), where the bank's high room looks out
        for (int[] cc : new int[][]{{ku, kv + kd - 1}, {ku + kw - 1, kv + kd - 1}}) {
            for (int a = -2; a <= 2; a++) for (int b = -2; b <= 2; b++) {
                boolean rimC = Math.abs(a) == 2 || Math.abs(b) == 2;
                for (int y = P + 1; y <= ktop + 4; y++) if (rimC) f.put(cc[0] + a, y, cc[1] + b, y == P + 1 ? base : wall);
                if (rimC && (a + b) % 2 == 0) f.put(cc[0] + a, ktop + 5, cc[1] + b, wall);
                f.put(cc[0] + a, ktop + 4, cc[1] + b, rimC ? wall : s.b("floor"));
            }
        }
        // banners either side of the keep door
        int banner = s.b("banner");
        if (banner != Blocks.AIR) for (int side : new int[]{ku + kw / 2 - 3, ku + kw / 2 + 2}) for (int y = P + 5; y <= P + 8; y++) f.put(side, y, kv - 1, banner);
        // where a new arrival wakes: in the keep's hall, a few steps in, facing the door and the gate beyond it
        float[] door = kf.point(kw / 2.0 - 0.5, 0), wake = kf.point(kw / 2.0 - 0.5, Math.min(kd - 3, 5));
        out.points.put("spawn", wake);
        // the bank's ground-floor counter at the back of the hall (its high room comes with the keep's interior)
        out.points.put("bankTeller", kf.point(kw / 2.0 - 0.5, kd - 3));
        out.points.put("bankCustomer", kf.point(kw / 2.0 - 0.5, kd - 5));
        out.points.put("spawnLook", door);
        out.points.put("keepDoor", door);
        // the undercroft's stair, down from the court's back corner
        int su = thick + 2, sv = d - thick - 4;
        stairDown(f, s, su, sv, P, o.i("undercroftDepth", 10), 's', out, "undercroft");
        // a well in the court, and the court's lamps
        well(f, s, w / 2 + kw / 2 + 3 < w - thick - 2 ? Math.min(w - thick - 3, w / 2 + kw / 2 + 3) : w / 2, thick + 5, P);
        for (int[] l : new int[][]{{thick + 2, thick + 2}, {w - thick - 3, thick + 2}}) lamp(f, s, l[0], l[1], P);
    }

    /** A round tower about a corner cell (u, v): radius r, h high, merlons on its top; open inside (a floor at the wall walk). */
    static void roundTower(Frame f, Style s, int cu, int cv, int r, int P, int h, boolean walkFloor) {
        int wall = s.b("wall"), base = s.b("base");
        for (int a = -r; a <= r; a++) for (int b = -r; b <= r; b++) {
            double dd = Math.hypot(a, b);
            if (dd > r + 0.3) continue;
            boolean rim = dd > r - 0.9;
            f.footing(cu + a, P, cv + b, base);
            f.put(cu + a, P, cv + b, base);
            for (int y = P + 1; y <= P + h; y++) f.put(cu + a, y, cv + b, rim ? (y == P + 1 ? base : wall) : (y == P + h ? s.b("floor") : Blocks.AIR));
            if (rim && ((a + b) & 1) == 0) f.put(cu + a, P + h + 1, cv + b, wall);
            if (rim && (a == 0 || b == 0) && Math.abs(a + b) == r) f.put(cu + a, P + h - 3, cv + b, s.b("glass"));
        }
    }

    /** A frame inside another (local u, v offsets and size), facing the same way. */
    static Frame sub(Frame f, int u, int v, int w, int d) {
        int ax = f.wx(u, v), az = f.wz(u, v), bx = f.wx(u + w - 1, v + d - 1), bz = f.wz(u + w - 1, v + d - 1);
        return new Frame(f.c, Math.min(ax, bx), Math.min(az, bz), w, d, f.facing, f.P);
    }

    /**
     * A stair going down (crypt, cellar, undercroft): a flight of steps from the ground at (u, v) descending along the
     * frame's depth (or the given way), walled each side, roofed where it runs under the ground, to a landing and a
     * sealed door - the underground place beyond kept for later, its volume reserved.
     */
    static void stairDown(Frame f, Style s, int u, int v, int P, int depth, char way, Out out, String kind) {
        int lining = s.b("lining"), step = Blocks.STAIRS;
        int dv = way == 'n' ? -1 : 1;
        int len = depth;
        // the head: a low hood over the top of the flight
        for (int k = 0; k <= len + 2; k++) {
            int vv = v + dv * k, yTop = P - k;
            for (int a = -1; a <= 2; a++) {
                boolean side = a == -1 || a == 2;
                for (int y = yTop - 1; y <= Math.max(yTop + 3, P + 3); y++) {
                    if (side) { if (y <= P + (k < 2 ? 2 : 0)) f.put(u + a, y, vv, lining); continue; }
                    if (y == yTop && k < len) f.put(u + a, y, vv, step);
                    else if (y == yTop - 1) f.put(u + a, y, vv, lining);
                    else if (y > yTop && y <= yTop + 3) f.put(u + a, y, vv, k <= len && y <= P ? Blocks.AIR : (y > P ? Blocks.AIR : lining));
                    if (k >= len && y == P - len) f.put(u + a, y, vv, lining);
                }
                // a roof where the flight runs below the ground
                if (!side && yTop + 4 <= P) f.put(u + a, yTop + 4, vv, lining);
            }
        }
        // the landing at the foot and its sealed door
        int footV = v + dv * (len + 1), footY = P - len + 1;
        for (int a = 0; a <= 1; a++) { f.put(u + a, footY - 1, footV, lining); f.put(u + a, footY, footV, Blocks.AIR); f.put(u + a, footY + 1, footV, Blocks.AIR); f.put(u + a, footY + 2, footV, Blocks.AIR); }
        for (int a = 0; a <= 1; a++) for (int y = footY; y <= footY + 1; y++) f.put(u + a, y, footV + dv, Blocks.LOCKED_DOOR);
        f.put(u - 1, P + 1, v, s.b("post")); f.put(u + 2, P + 1, v, s.b("post"));
        // what lies beyond, kept
        int r0 = footV + dv, r1 = footV + dv * 18;
        out.reserve(kind, f, u - 6, Math.max(1, footY - 8), Math.min(r0, r1), u + 7, footY + 4, Math.max(r0, r1));
        out.points.put(kind, f.point(u + 0.5, v));
    }

    /** A lamp post: a timber post, a lamp on its arm. */
    static void lamp(Frame f, Style s, int u, int v, int P) {
        f.put(u, P + 1, v, s.b("post")); f.put(u, P + 2, v, s.b("post")); f.put(u, P + 3, v, s.b("post"));
        f.put(u, P + 4, v, Blocks.LAMP);
    }

    /**
     * The mouth of an underground place cut into the hillside: a portal (dressed stone for a crypt or vault, timbers for a
     * mine, bare rock for a cave), a passage a few cells in and down, and a sealed end; the volume beyond reserved.
     * Where the ground is flat a rise of rock is heaped over the mouth first (Architecture's plan does that).
     */
    static void mouth(Frame f, Style s, Opts o, long seed, Out out) {
        int P = f.P, len = o.i("length", 10), wide = o.i("width", 3), tall = o.i("height", 3);
        String kind = o.s("mouth", "cave");
        int lining = s.b("lining"), rock = s.b("rock"), post = s.b("post"), trim = s.b("trim");
        int cu = f.w / 2;
        int u0 = cu - wide / 2, u1 = u0 + wide - 1;
        for (int k = 0; k < len; k++) {
            int floorY = P - k / 3;
            for (int u = u0 - 1; u <= u1 + 1; u++) {
                boolean side = u < u0 || u > u1;
                for (int y = floorY - 1; y <= floorY + tall + 1; y++) {
                    boolean roofC = y == floorY + tall + 1, floorC = y == floorY - 1;
                    if (side || roofC || floorC) {
                        int cur = f.get(u, y, k);
                        if (kind.equals("cave")) { if (cur == Blocks.AIR && (floorC || k > 1)) f.put(u, y, k, rock); else if (cur != Blocks.AIR && hash(seed, u, y + k * 31, 74) < 0.35) f.put(u, y, k, rock); }
                        else f.put(u, y, k, kind.equals("mine") && !floorC && (k % 3 == 0) ? post : lining);
                    } else f.put(u, y, k, Blocks.AIR);
                }
                if (kind.equals("mine") && k % 3 == 0 && !side) f.put(u, floorY + tall, k, trim);
            }
        }
        // the portal on the face
        if (!kind.equals("cave")) {
            for (int y = P; y <= P + tall + 1; y++) { f.put(u0 - 1, y, 0, kind.equals("mine") ? post : trim); f.put(u1 + 1, y, 0, kind.equals("mine") ? post : trim); }
            for (int u = u0 - 1; u <= u1 + 1; u++) f.put(u, P + tall + 1, 0, kind.equals("mine") ? post : trim);
        }
        // the sealed end
        int endY = P - (len - 1) / 3;
        for (int u = u0; u <= u1; u++) for (int y = endY; y < endY + tall; y++) f.put(u, y, len, kind.equals("cave") ? rock : Blocks.LOCKED_DOOR);
        out.gate(f, cu - (wide >= 2 ? 1 : 0), 0, Math.min(2, wide), P, 0, -1);
        out.reserve(o.s("reserve", "dungeon"), f, u0 - 8, Math.max(1, endY - o.i("reserveDepth", 14)), len + 1, u1 + 8, endY + tall + 2, len + 24);
        out.points.put("mouth", f.point(cu, 0));
    }

    /** A market stall: four posts, a cloth awning, a counter across the front. */
    static void stall(Frame f, Style s, int u, int v, int P, int cloth) {
        int post = s.b("post"), counter = s.b("trim");
        for (int[] p : new int[][]{{0, 0}, {2, 0}, {0, 2}, {2, 2}}) for (int y = P + 1; y <= P + 3; y++) f.put(u + p[0], y, v + p[1], post);
        for (int a = -1; a <= 3; a++) for (int b = -1; b <= 3; b++) f.put(u + a, P + 4, v + b, cloth);
        for (int a = 0; a <= 2; a++) f.put(u + a, P + 4, v + 1, cloth);
        f.put(u + 1, P + 1, v, counter);
    }

    /** A tent: an A of cloth over a ridge pole, open at the front. */
    static void tent(Frame f, Style s, int u, int v, int P, int len, int cloth) {
        for (int k = 0; k < len; k++) {
            f.put(u - 1, P + 1, v + k, cloth); f.put(u + 1, P + 1, v + k, cloth);
            f.put(u - 1, P + 2, v + k, cloth); f.put(u + 1, P + 2, v + k, cloth);
            f.put(u, P + 3, v + k, cloth);
        }
        f.put(u, P + 1, v + len - 1, cloth); f.put(u, P + 2, v + len - 1, cloth);
        f.put(u, P + 4, v, s.b("post"));
    }

    /** A torchlit stake: a post with a lamp at its head (the wilds' failed lamps). */
    static void stake(Frame f, Style s, int u, int v, int P) {
        f.put(u, P + 1, v, s.b("post")); f.put(u, P + 2, v, s.b("post")); f.put(u, P + 3, v, Blocks.LAMP);
    }

    /** A gibbet: a post, an arm, a cage of iron hung from it. */
    static void gibbet(Frame f, Style s, int u, int v, int P) {
        int post = s.b("post");
        for (int y = P + 1; y <= P + 5; y++) f.put(u, y, v, post);
        f.put(u + 1, P + 5, v, post); f.put(u + 2, P + 5, v, post);
        f.put(u + 2, P + 4, v, Blocks.FENCE); f.put(u + 2, P + 3, v, Blocks.IRON_BLOCK);
    }

    /** Fences round a rect (local, inclusive), a gate gap in the front. */
    static void fence(Frame f, Style s, int u0, int v0, int u1, int v1, int P, int gapU) {
        int fence = s.b("fence");
        for (int u = u0; u <= u1; u++) for (int v = v0; v <= v1; v++) {
            if (u != u0 && u != u1 && v != v0 && v != v1) continue;
            if (v == v0 && (u == gapU || u == gapU + 1)) continue;
            int g = f.c.ground(f.wx(u, v), f.wz(u, v));
            if (f.c.get(f.wx(u, v), g, f.wz(u, v)) == Blocks.WATER) continue;
            f.c.set(f.wx(u, v), g + 1, f.wz(u, v), fence);
        }
    }

    /** A field: furrows across it (tilled earth and the crop's green), a fence round it with a gate. */
    static void field(Frame f, Style s, Opts o, long seed, Out out) {
        int w = f.w, d = f.d, P = f.P;
        int furrow = block(o.s("furrow", "dirt")), crop = block(o.s("crop", "grass")), rowBlock = block(o.s("rowBlock", "none"));
        for (int u = 1; u < w - 1; u++) for (int v = 1; v < d - 1; v++) {
            int x = f.wx(u, v), z = f.wz(u, v), g = f.c.ground(x, z);
            if (f.c.get(x, g, z) == Blocks.WATER) continue;
            f.c.set(x, g, z, (u % 2 == 0) ? furrow : crop);
            if (u % 2 == 1 && rowBlock != Blocks.AIR && v > 1 && v < d - 2) f.c.set(x, g + 1, z, rowBlock);
            else if (u % 2 == 1 && o.b("hay", false) && hash(seed, u, v, 75) < 0.04) f.c.set(x, g + 1, z, Blocks.HAY_BALE);
        }
        if (o.b("fenced", true)) fence(f, s, 0, 0, w - 1, d - 1, P, w / 2 - 1);
        out.points.put("field", f.point(w / 2.0, d / 2.0));
    }

    /** A pier: a deck of boards on posts out over the water, a mooring post at its head. */
    static void pier(Frame f, Style s, Opts o, long seed, Out out) {
        int deck = s.b("deck"), post = s.b("post");
        int y = o.i("deckY", f.P);
        boolean span = o.b("span", false);
        if (span) {
            // a span (a rope crossing): its deck level with the lower of the two banks it joins
            int a = f.c.ground(f.wx(f.w / 2, -1), f.wz(f.w / 2, -1)), b = f.c.ground(f.wx(f.w / 2, f.d), f.wz(f.w / 2, f.d));
            y = Math.min(a, b);
        }
        for (int u = 0; u < f.w; u++) for (int v = 0; v < f.d; v++) {
            f.put(u, y, v, deck);
            for (int k = 1; k <= 3; k++) f.put(u, y + k, v, Blocks.AIR);
            if (span) { if (u == 0 || u == f.w - 1) f.put(u, y + 1, v, Blocks.FENCE); continue; }
            if ((u == 0 || u == f.w - 1) && v % 3 == 0) { f.footing(u, y - 1, v, post); f.put(u, y + 1, v, post); }
        }
        out.points.put("head", f.point(f.w / 2.0, f.d - 1));
    }

    /** A ruin: walls broken to uneven heights, fallen stones about, moss over all - the shape of what stood. */
    static void ruin(Frame f, Style s, Opts o, long seed, Out out) {
        int w = f.w, d = f.d, P = f.P, H = o.i("height", 5);
        int wall = s.b("wall"), accent = s.b("accent"), base = s.b("base");
        for (int u = 0; u < w; u++) for (int v = 0; v < d; v++) {
            boolean edge = u == 0 || v == 0 || u == w - 1 || v == d - 1;
            f.put(u, P, v, hash(seed, u, v, 76) < 0.5 ? base : s.b("court"));
            if (!edge) { if (hash(seed, u, v, 77) < 0.05) f.put(u, P + 1, v, accent); continue; }
            double n = hash(seed, u / 2, v / 2, 78);
            int h = (int) Math.round(n * n * H);
            if (u == w / 2 && v == 0) h = 0;
            for (int y = P + 1; y <= P + h; y++) f.put(u, y, v, hash(seed, u + y, v, 79) < 0.3 ? accent : wall);
        }
        out.points.put("ruin", f.point(w / 2.0, d / 2.0));
        if (o.b("hearth", false)) { f.put(w / 2, P + 1, d - 2, base); f.put(w / 2 - 1, P + 1, d - 2, base); f.put(w / 2, P + 2, d - 2, base); f.put(w / 2, P + 3, d - 2, base); }
    }

    /** A camp: tents round a fire ring, a stake every few cells round the edge (torchlit where the culture keeps them). */
    static void camp(Frame f, Style s, Opts o, long seed, Out out) {
        int w = f.w, d = f.d, P = f.P, cloth = s.b("cloth");
        int tents = o.i("tents", 3);
        for (int k = 0; k < tents; k++) {
            double a = k * Math.PI * 2 / tents + hash(seed, k, 0, 80);
            int u = (int) Math.round(w / 2.0 + Math.cos(a) * (w / 2.0 - 3)), v = (int) Math.round(d / 2.0 + Math.sin(a) * (d / 2.0 - 3)) - 1;
            tent(f, s, u, v, f.c.ground(f.wx(u, v), f.wz(u, v)), 3, cloth);
        }
        // the fire ring
        for (int a = -1; a <= 1; a++) for (int b = -1; b <= 1; b++) if (a != 0 || b != 0) f.put(w / 2 + a, P + 1, d / 2 + b, Blocks.COBBLE);
        f.put(w / 2, P, d / 2, Blocks.CHARRED);
        if (o.b("stakes", true)) for (int u = 0; u < w; u++) for (int v = 0; v < d; v++) {
            boolean edge = u == 0 || v == 0 || u == w - 1 || v == d - 1;
            if (!edge || (u + v) % 4 != 0 || (v == 0 && Math.abs(u - w / 2) <= 1)) continue;
            int x = f.wx(u, v), z = f.wz(u, v), g = f.c.ground(x, z);
            if ((u + v) % 12 == 0) stake(f, s, u, v, g); else { f.c.set(x, g + 1, z, s.b("post")); f.c.set(x, g + 2, z, s.b("post")); }
        }
        out.points.put("fire", f.point(w / 2.0, d / 2.0));
    }

    /** A ring of standing stones, each a pillar of uneven height. */
    static void stones(Frame f, Style s, Opts o, long seed, Out out) {
        int n = o.i("count", 9);
        double r = Math.min(f.w, f.d) / 2.0 - 1;
        for (int k = 0; k < n; k++) {
            double a = k * Math.PI * 2 / n;
            int u = (int) Math.round((f.w - 1) / 2.0 + Math.cos(a) * r), v = (int) Math.round((f.d - 1) / 2.0 + Math.sin(a) * r);
            int g = f.c.ground(f.wx(u, v), f.wz(u, v)), h = 2 + (int) (hash(seed, k, 0, 81) * 3);
            for (int y = g + 1; y <= g + h; y++) f.put(u, y, v, s.b("rock"));
        }
        out.points.put("ring", f.point(f.w / 2.0, f.d / 2.0));
    }

    /** A water wheel on the mill's flank (a ring of boards on an axle), in the stream beside it. */
    static void wheel(Frame f, Style s, int u, int v, int y, int r) {
        int post = s.b("post"), deck = s.b("deck");
        for (int a = -r; a <= r; a++) for (int b = -r; b <= r; b++) {
            double dd = Math.hypot(a, b);
            if (dd <= r + 0.3 && dd > r - 0.8) { f.put(u, y + b, v + a, deck); f.put(u - 1, y + b, v + a, deck); }
            if ((a == 0 || b == 0) && dd <= r) f.put(u, y + b, v + a, post);
        }
    }

    /** A bell (the Mere's sunken bell): a bronze hood and its lip, lying in the bed. */
    static void bell(Frame f, int u, int v, int y) {
        for (int a = -2; a <= 2; a++) for (int b = -2; b <= 2; b++) {
            double dd = Math.hypot(a, b);
            if (dd <= 2.2) f.put(u + a, y, v + b, Blocks.COPPER_BLOCK);
            if (dd <= 1.6) f.put(u + a, y + 1, v + b, Blocks.COPPER_BLOCK);
            if (dd <= 0.8) f.put(u + a, y + 2, v + b, Blocks.COPPER_BLOCK);
        }
    }

    /** A stone statue on a plinth: a pillar shaped to a figure, the culture's own stone. */
    static void statue(Frame f, Style s, int u, int v, int P) {
        int rock = s.b("column"), base = s.b("base");
        for (int a = -1; a <= 1; a++) for (int b = -1; b <= 1; b++) f.put(u + a, P + 1, v + b, base);
        f.put(u, P + 2, v, rock); f.put(u, P + 3, v, rock); f.put(u, P + 4, v, rock); f.put(u - 1, P + 4, v, rock); f.put(u + 1, P + 4, v, rock); f.put(u, P + 5, v, rock);
    }

    /** A colonnade: columns along the front a cell out, carrying an entablature and a low roof (a portico). */
    static void portico(Frame f, Style s, int depth, int height, int P) {
        int col = s.b("column"), trim = s.b("trim"), roof = s.b("roof");
        for (int u = 0; u < f.w; u++) for (int v = -depth; v < 0; v++) {
            f.put(u, P, v, s.b("base"));
            f.footing(u, P - 1, v, s.b("base"));
            for (int y = P + 1; y < P + height; y++) f.put(u, y, v, v == -depth && u % 2 == 0 ? col : Blocks.AIR);
            f.put(u, P + height, v, trim);
            f.put(u, P + height + 1, v, v == -depth ? roof : trim);
        }
    }

    // ------------------------------------------------------------------ dispatch
    interface Template { void draw(Frame f, Style s, Opts o, long seed, Out out); }
    static final Map<String, Template> TEMPLATES = new HashMap<>();
    static {
        TEMPLATES.put("building", Kit::building);
        TEMPLATES.put("tower", Kit::tower);
        TEMPLATES.put("castle", Kit::castle);
        TEMPLATES.put("courtyard", Kit::courtyard);
        TEMPLATES.put("mouth", Kit::mouth);
        TEMPLATES.put("field", Kit::field);
        TEMPLATES.put("pier", Kit::pier);
        TEMPLATES.put("ruin", Kit::ruin);
        TEMPLATES.put("camp", Kit::camp);
        TEMPLATES.put("stones", Kit::stones);
        TEMPLATES.put("church", Kit::church);
        TEMPLATES.put("plaza", Kit::plaza);
        TEMPLATES.put("stairs", Kit::stairs);
        TEMPLATES.put("stable", Kit::stable);
        TEMPLATES.put("mill", Kit::mill);
        TEMPLATES.put("hall", Kit::hall);
        TEMPLATES.put("gibbets", Kit::gibbets);
        TEMPLATES.put("yard", Kit::yard);
        TEMPLATES.put("keep", Kit::keep);
        TEMPLATES.put("shrine", Kit::shrine);
        TEMPLATES.put("graveyard", Kit::graveyard);
        TEMPLATES.put("cellar", (f, s, o, seed, out) -> stairDown(f, s, Math.max(1, f.w / 2 - 1), 1, f.P, o.i("depth", 8), 's', out, o.s("reserve", "cellar")));
        TEMPLATES.put("weir", Kit::weir);
        TEMPLATES.put("orchard", Kit::orchard);
        TEMPLATES.put("gatehouse", Kit::gatehouse);
        TEMPLATES.put("classical", Kit::classical);
        TEMPLATES.put("garden", Kit::garden);
        TEMPLATES.put("sunkbell", (f, s, o, seed, out) -> bell(f, f.w / 2, f.d / 2, f.c.ground(f.wx(f.w / 2, f.d / 2), f.wz(f.w / 2, f.d / 2)) + 1));
    }

    /**
     * A church: the nave (a hall under a steep roof, tall windows of coloured glass down each side) and a square bell
     * tower over the door at the front, its spire or battlements per the culture.
     */
    static void church(Frame f, Style s, Opts o, long seed, Out out) {
        int w = f.w, d = f.d, P = f.P, tw = Math.min(w - 2, o.i("towerW", 6));
        int tu = (w - tw) / 2;
        Frame nave = sub(f, 0, tw - 2, w, d - tw + 2);
        Map<String, Object> no = new HashMap<>(o.m);
        no.putIfAbsent("storeys", 1); no.putIfAbsent("wallH", 7); no.put("windowSpacing", 3); no.putIfAbsent("roof", "gable"); no.put("ridge", "v"); no.put("backDoor", false);
        Style ns = s.with(Map.of("glass", Atlas.s(o.m, "windowGlass", "stained_glass"))).withBlock("upper", s.b("wall"));
        building(nave, ns, new Opts(no), seed, new Out());
        Frame tf = sub(f, tu, 0, tw, tw);
        Map<String, Object> to = new HashMap<>(Map.of("height", o.i("towerH", 16), "shape", "square", "crown", o.s("crown", "pyramid"), "spire", o.d("spire", 2.2)));
        tower(tf, s, new Opts(to), seed, out);
        // open the tower into the nave
        for (int u = tu + 1; u < tu + tw - 1; u++) for (int y = P + 1; y <= P + 3; y++) f.put(u, y, tw - 1, Blocks.AIR);
        for (int u = tu + 1; u < tu + tw - 1; u++) for (int y = P + 1; y <= P + 3; y++) f.put(u, y, tw - 2, Blocks.AIR);
        out.reserve("interior", f, 1, P + 1, tw - 1, w - 2, P + 7, d - 2);
        out.points.put("altar", f.point(w / 2.0 - 0.5, d - 3));
    }

    /** A paved open place - a square, a market, a green - its edge kerbed, with stalls, a well or a statue as asked. */
    static void plaza(Frame f, Style s, Opts o, long seed, Out out) {
        int w = f.w, d = f.d, P = f.P, court = s.b("court");
        for (int u = 0; u < w; u++) for (int v = 0; v < d; v++) {
            int x = f.wx(u, v), z = f.wz(u, v);
            if ((f.c.t.flags[f.c.t.idx(x, z)] & (Terrain.RIVER | Terrain.LAKE | Terrain.SEA)) != 0) continue;
            f.put(u, P, v, o.b("green", false) ? Blocks.GRASS : (hash(seed, u, v, 82) < 0.08 ? s.b("base") : court));
            f.footing(u, P - 1, v, s.b("base"));
            for (int y = P + 1; y <= P + 5; y++) f.put(u, y, v, Blocks.AIR);
        }
        int stalls = o.i("stalls", 0);
        int cloth0 = s.b("cloth"), cloth1 = s.b("banner");
        for (int k = 0; k < stalls; k++) {
            int perRow = Math.max(1, (w - 4) / 5);
            int row = k / perRow, col = k % perRow;
            int u = 2 + col * 5, v = row % 2 == 0 ? 2 + row / 2 * 6 : d - 5 - row / 2 * 6;
            if (u + 3 >= w || v < 1 || v + 3 >= d) continue;
            stall(f, s, u, v, P, k % 2 == 0 ? cloth0 : (cloth1 == Blocks.AIR ? cloth0 : cloth1));
        }
        if (o.b("well", false)) well(f, s, w / 2, d / 2, P);
        if (o.b("statue", false)) statue(f, s, w / 2, d / 2, P);
        if (o.b("lamps", true)) for (int[] l : new int[][]{{0, 0}, {w - 1, 0}, {0, d - 1}, {w - 1, d - 1}}) lamp(f, s, l[0], l[1], P);
        out.points.put("centre", f.point(w / 2.0, d / 2.0));
    }

    /** A flight of stone steps (Pilgrim's Stair, the Falls Stair): straight down the slope from its head, walled each side. */
    static void stairs(Frame f, Style s, Opts o, long seed, Out out) {
        int width = f.w, len = f.d;
        int top = f.c.ground(f.wx(width / 2, 0), f.wz(width / 2, 0));
        int y = top;
        for (int v = 0; v < len; v++) {
            int g = f.c.ground(f.wx(width / 2, v), f.wz(width / 2, v));
            if (g < y) y = y - 1;
            if (g > y + 1) break;
            for (int u = -1; u <= width; u++) {
                boolean side = u == -1 || u == width;
                f.footing(u, y - 1, v, s.b("base"));
                f.put(u, y, v, side ? s.b("base") : (y < top && v > 0 ? Blocks.STAIRS : s.b("court")));
                for (int k = 1; k <= 3; k++) f.put(u, y + k, v, side && k == 1 ? s.b("wall") : Blocks.AIR);
            }
        }
        out.points.put("head", f.point(width / 2.0, 0));
    }

    /** A stable: a long range open to its yard on the front, stalls inside, a paddock fenced before it, hay by the door. */
    static void stable(Frame f, Style s, Opts o, long seed, Out out) {
        int paddock = o.i("paddock", 8);
        Frame body = sub(f, 0, paddock, f.w, f.d - paddock);
        Map<String, Object> bo = new HashMap<>(o.m);
        bo.put("openFront", true); bo.putIfAbsent("wallH", 4); bo.putIfAbsent("roof", "gable"); bo.put("ridge", "u"); bo.put("backDoor", false);
        building(body, s, new Opts(bo), seed, out);
        fence(f, s, 0, 0, f.w - 1, paddock, f.P, f.w / 2 - 1);
        for (int k = 0; k < 3; k++) f.put(1 + k, f.P + 1, paddock - 1, Blocks.HAY_BALE);
        f.put(1, f.P + 2, paddock - 1, Blocks.HAY_BALE);
        out.points.put("paddock", f.point(f.w / 2.0, paddock / 2.0));
    }

    /** A water mill: the mill house on the bank, its wheel turning in the stream on its flank. */
    static void mill(Frame f, Style s, Opts o, long seed, Out out) {
        building(f, s, o, seed, out);
        // the wheel on whichever flank has the stream beside it
        for (int side : new int[]{f.w + 1, -2}) {
            boolean wet = false;
            for (int k = 0; k <= 3 && !wet; k++) {
                int u = side > 0 ? side + k : side - k, x = f.wx(u, f.d / 2), z = f.wz(u, f.d / 2);
                if (f.c.t.in(x, z) && f.c.t.water[f.c.t.idx(x, z)] >= 0) wet = true;
            }
            if (wet) { wheel(f, s, side, f.d / 2, f.P + 1, 3); out.points.put("wheel", f.point(side, f.d / 2.0)); return; }
        }
    }

    /** A long hall (an inn, a lodge, a barracks, a bunkhouse): a building with a chimney at either end and a lamp at its door. */
    static void hall(Frame f, Style s, Opts o, long seed, Out out) {
        Map<String, Object> ho = new HashMap<>(o.m);
        ho.putIfAbsent("chimney", true);
        building(f, s, new Opts(ho), seed, out);
        int du = (f.w - 2) / 2;
        lamp(f, s, du - 2, -2, f.P);
        lamp(f, s, du + 3, -2, f.P);
    }

    /** The gibbets of a heath or a grove: a few at hand-spans apart, a hanging tree's dead arms among them. */
    static void gibbets(Frame f, Style s, Opts o, long seed, Out out) {
        int n = o.i("count", 3);
        for (int k = 0; k < n; k++) {
            int u = 1 + (int) (hash(seed, k, 1, 83) * (f.w - 4)), v = 1 + (int) (hash(seed, k, 2, 84) * (f.d - 2));
            gibbet(f, s, u, v, f.c.ground(f.wx(u, v), f.wz(u, v)));
        }
        if (o.b("stakes", false)) for (int k = 0; k < n * 2; k++) {
            int u = (int) (hash(seed, k, 3, 85) * f.w), v = (int) (hash(seed, k, 4, 86) * f.d);
            stake(f, s, u, v, f.c.ground(f.wx(u, v), f.wz(u, v)));
        }
        out.points.put("gibbets", f.point(f.w / 2.0, f.d / 2.0));
    }

    /** A work yard: a fenced stand of ground with woodpiles, hay or stone stacked about it (a wood yard, a cutting yard, a wagon stop). */
    static void yard(Frame f, Style s, Opts o, long seed, Out out) {
        int stock = block(o.s("stock", "oak_log"));
        int w = f.w, d = f.d, P = f.P;
        for (int u = 0; u < w; u++) for (int v = 0; v < d; v++) {
            f.put(u, P, v, s.b("court"));
            for (int y = P + 1; y <= P + 4; y++) f.put(u, y, v, Blocks.AIR);
        }
        int piles = o.i("piles", 3);
        for (int k = 0; k < piles; k++) {
            int u = 1 + (int) (hash(seed, k, 5, 87) * (w - 4)), v = 2 + (int) (hash(seed, k, 6, 88) * (d - 5));
            for (int a = 0; a < 3; a++) for (int b = 0; b < 2; b++) for (int y = 1; y <= (a == 1 ? 2 : 1); y++) f.put(u + a, P + y, v + b, stock);
        }
        if (o.b("fenced", true)) fence(f, s, 0, 0, w - 1, d - 1, P, w / 2 - 1);
        if (o.b("shelter", false)) {
            for (int[] p : new int[][]{{1, d - 4}, {w - 2, d - 4}, {1, d - 2}, {w - 2, d - 2}}) for (int y = P + 1; y <= P + 3; y++) f.put(p[0], y, p[1], s.b("post"));
            for (int u = 0; u < w; u++) for (int v = d - 5; v <= d - 1; v++) f.put(u, P + 4, v, s.b("roof"));
        }
        out.points.put("yard", f.point(w / 2.0, d / 2.0));
    }

    /** A keep: a tall block of storeys under battlements, a round tower at each corner rising over it. */
    static void keep(Frame f, Style s, Opts o, long seed, Out out) {
        Map<String, Object> ko = new HashMap<>(o.m);
        ko.putIfAbsent("storeys", 3); ko.putIfAbsent("wallH", 5); ko.put("roof", "crenel"); ko.putIfAbsent("backDoor", false);
        ko.putIfAbsent("towers", "four"); ko.putIfAbsent("towerCrown", "crenel"); ko.putIfAbsent("towerRise", 6); ko.put("stone", true);
        building(f, s, new Opts(ko), seed, out);
        int banner = s.b("banner");
        if (banner != Blocks.AIR) for (int side : new int[]{f.w / 2 - 3, f.w / 2 + 2}) for (int y = f.P + 4; y <= f.P + 7; y++) f.put(side, y, -1, banner);
    }

    /** A wayside shrine: a stone plinth, four posts under a little pyramid roof, the altar stone, a lamp. */
    static void shrine(Frame f, Style s, Opts o, long seed, Out out) {
        int w = f.w, d = f.d, P = f.P;
        for (int u = 0; u < w; u++) for (int v = 0; v < d; v++) { f.put(u, P, v, s.b("base")); f.footing(u, P - 1, v, s.b("base")); for (int y = P + 1; y <= P + 6; y++) f.put(u, y, v, Blocks.AIR); }
        for (int[] c : new int[][]{{0, 0}, {w - 1, 0}, {0, d - 1}, {w - 1, d - 1}}) for (int y = P + 1; y <= P + 3; y++) f.put(c[0], y, c[1], s.b("post"));
        roof(f, s, new Opts(new HashMap<>(Map.of("overhang", 1))), "pyramid", 1, P + 3, 0, seed);
        f.put(w / 2, P + 1, d / 2, s.b("accent"));
        f.put(w / 2, P + 2, d / 2, Blocks.LAMP);
        out.points.put("altar", f.point(w / 2.0, d / 2.0));
    }

    /** A graveyard: a fence with a gate, rows of grave markers off a middle path, and (where there is one) a crypt's house over its stair. */
    static void graveyard(Frame f, Style s, Opts o, long seed, Out out) {
        int w = f.w, d = f.d, P = f.P;
        for (int u = 0; u < w; u++) for (int v = 0; v < d; v++) {
            f.put(u, P, v, u >= w / 2 - 1 && u <= w / 2 ? s.b("court") : Blocks.GRASS);
            for (int y = P + 1; y <= P + 5; y++) f.put(u, y, v, Blocks.AIR);
        }
        int mark = s.b("rock");
        boolean crypt = o.b("crypt", false);
        for (int u = 2; u < w - 2; u += 3) for (int v = 3; v < d - (crypt ? 9 : 2); v += 3) {
            if (u >= w / 2 - 2 && u <= w / 2 + 1) continue;
            if (hash(seed, u, v, 90) < 0.15) continue;
            f.put(u, P + 1, v, mark);
            if (hash(seed, u, v, 91) < 0.4) f.put(u, P + 2, v, mark);
            f.put(u, P, v + 1, Blocks.BARE_PATCH);
        }
        fence(f, s, 0, 0, w - 1, d - 1, P, w / 2 - 1);
        if (crypt) {
            int cw = 6, cd = 6, cu = (w - cw) / 2, cv = d - cd - 1;
            Frame cf = sub(f, cu, cv, cw, cd);
            building(cf, s, new Opts(new HashMap<>(Map.of("storeys", 1, "wallH", 4, "roof", "pyramid", "doorW", 2, "backDoor", false, "windowGaps", 1.0))), seed + 5, new Out());
            stairDown(f, s, cu + 2, cv + 2, P, o.i("depth", 10), 's', out, "crypt");
        }
        out.points.put("graves", f.point(w / 2.0, d / 2.0));
    }

    /** A weir across a river: a stone sill at the water's face, broken through in the middle (the breach the footbridge spans). */
    static void weir(Frame f, Style s, Opts o, long seed, Out out) {
        int y = f.P - 1, gap = o.i("breach", 5);
        for (int u = 0; u < f.w; u++) for (int v = 0; v < f.d; v++) {
            if (Math.abs(u - f.w / 2) < gap / 2.0 + 0.5) continue;
            f.put(u, y, v, hash(seed, u, v, 92) < 0.3 ? s.b("accent") : s.b("wall"));
            if (hash(seed, u, v, 93) < 0.35) f.put(u, y + 1, v, s.b("wall"));
        }
        out.points.put("breach", f.point(f.w / 2.0, f.d / 2.0));
    }

    /** An orchard or a grove: trees in rows (the species from the data) - planted as the world's own, so they are worked. */
    static void orchard(Frame f, Style s, Opts o, long seed, Out out) {
        int sp = o.i("spacing", 5);
        String species = o.s("species", "olive");
        for (int u = 1; u < f.w - 1; u += sp) for (int v = 1; v < f.d - 1; v += sp) {
            int du = (int) (hash(seed, u, v, 94) * 2), dv = (int) (hash(seed, u, v, 95) * 2);
            out.trees.add(new int[]{f.wx(u + du, v + dv), f.wz(u + du, v + dv)});
            out.treeSpecies.add(species);
        }
        if (o.b("fenced", false)) fence(f, s, 0, 0, f.w - 1, f.d - 1, f.P, f.w / 2 - 1);
        out.points.put("orchard", f.point(f.w / 2.0, f.d / 2.0));
    }

    /** A gatehouse astride a road: two towers and the arch between them, the road running through. */
    static void gatehouse(Frame f, Style s, Opts o, long seed, Out out) {
        int w = f.w, d = f.d, P = f.P, gw = o.i("gateW", 5), H = o.i("height", 10);
        int gu = (w - gw) / 2;
        for (int u = 0; u < w; u++) for (int v = 0; v < d; v++) {
            boolean passage = u >= gu && u < gu + gw;
            f.footing(u, P - 1, v, s.b("base"));
            for (int y = P + 1; y <= P + H; y++) {
                boolean rim = u == 0 || v == 0 || u == w - 1 || v == d - 1 || u == gu - 1 || u == gu + gw;
                if (passage) f.put(u, y, v, y <= P + 5 ? Blocks.AIR : y == P + 6 ? s.b("trim") : (v == 0 || v == d - 1) ? s.b("wall") : y == P + H ? s.b("floor") : Blocks.AIR);
                else f.put(u, y, v, rim ? (y == P + 1 ? s.b("base") : s.b("wall")) : y == P + H ? s.b("floor") : Blocks.AIR);
            }
            if (!passage) f.put(u, P, v, s.b("floor"));
            boolean edge = u == 0 || v == 0 || u == w - 1 || v == d - 1;
            if (edge && (u + v) % 2 == 0) f.put(u, P + H + 1, v, s.b("wall"));
        }
        int banner = s.b("banner");
        if (banner != Blocks.AIR) for (int y = P + 7; y <= P + 9 && y < P + H; y++) { f.put(gu - 2, y, -1, banner); f.put(gu + gw + 1, y, -1, banner); }
        out.points.put("gate", f.point(w / 2.0, d / 2.0));
    }

    /** A classical front (the Museum, the Severian civic halls): steps up to a portico of columns, the building behind it. */
    static void classical(Frame f, Style s, Opts o, long seed, Out out) {
        int depth = o.i("portico", 3), height = o.i("porticoH", o.i("wallH", 5));
        Frame body = sub(f, 0, depth, f.w, f.d - depth);
        Map<String, Object> bo = new HashMap<>(o.m);
        bo.putIfAbsent("doorW", 2);
        building(body, s, new Opts(bo), seed, out);
        portico(body, s, depth, height, f.P);
    }

    /**
     * A kitchen garden laid for the farming system (rpg.world.Farming): its rows run east and west across the lot every other
     * cell (tilled mud in the rows, trodden earth between), a fence round it with its gate in the north fence - drawn in the
     * world's own axes whatever way the lot faces, as the farming system reads it.
     */
    static void garden(Frame f, Style s, Opts o, long seed, Out out) {
        Canvas c = f.c;
        int x0 = Math.min(f.wx(0, 0), f.wx(f.w - 1, f.d - 1)), x1 = Math.max(f.wx(0, 0), f.wx(f.w - 1, f.d - 1));
        int z0 = Math.min(f.wz(0, 0), f.wz(f.w - 1, f.d - 1)), z1 = Math.max(f.wz(0, 0), f.wz(f.w - 1, f.d - 1));
        int P = f.P, gx = (x0 + x1) / 2;
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
            boolean edge = x == x0 || x == x1 || z == z0 || z == z1;
            boolean row = !edge && (z - z0) % 2 == 0 && z >= z0 + 2 && z <= z1 - 2;
            c.set(x, P, z, edge ? Blocks.GRASS : row ? Blocks.MUD : Blocks.DIRT);
            for (int y = P + 1; y <= P + 3; y++) c.set(x, y, z, Blocks.AIR);
            if (edge && !(z == z0 && (x == gx || x == gx + 1))) c.set(x, P + 1, z, s.b("fence"));
        }
        out.points.put("garden", new float[]{(x0 + x1 + 1) / 2f, (z0 + z1 + 1) / 2f});
    }
}
