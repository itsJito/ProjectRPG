package rpg.world.shadowveil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import rpg.world.Blocks;

/**
 * (0.4.0, gamefixes: ShadowVeil) The terrain of the permanent world, grown from {@link Atlas} - deterministic, so the
 * same design always gives the same ground, cell for cell. Geography first, as the brief asks: the regions' elevations
 * blended into one another, the settlements' ground eased flat, peaks, the northern scarp and its valleys, the shore
 * belt round all four edges, then weathering (thermal erosion), the lakes, rivers and wadis cut into it, the roads laid
 * along it at a walkable grade with their bridges, the ground's materials, the trees and the ore at the surface.
 * Buildings come later in 0.4: there are none.
 * <p>
 * Everything the game and the checks need afterwards is kept per column: the ground's top cell, the water's top cell,
 * the biome, and what is there (road, bridge, river, lake, sea, cliff).
 */
public final class Terrain {
    // column flags
    public static final int ROAD = 1, BRIDGE = 2, RIVER = 4, LAKE = 8, SEA = 16, CLIFF = 32, MARSH = 64, WADI = 128, BEACH = 256, CAUSEWAY = 512, FALLS = 1024, TREE = 2048;
    /** (0.4.1) A structure's lot (BUILT), the ground kept open round it and in yards, courts and fields (YARD), a square a road may cross (SQUARE), a door's lane to the roads (LANE). */
    public static final int BUILT = 4096, YARD = 8192, SQUARE = 16384, LANE = 32768;

    public final Atlas atlas;
    public final int S, H, sea;
    /** The ground's top cell per column (index x * S + z). */
    public final int[] top;
    /** The water's top cell per column, or -1. */
    public final int[] water;
    /** The deck's cell of a bridge per column, or -1. */
    public final int[] deck;
    public final int[] flags;
    public final byte[] biome;
    public final List<String> biomes = new ArrayList<>();
    /** The cells: (x * S + z) * H + y, the engine's own order. Released (null) once the world is made. */
    public byte[] cells;
    /** The trees grown: {x, z, base y, species index}. */
    public final List<int[]> trees = new ArrayList<>();
    public final List<String> species = new ArrayList<>();
    /** The ore at the surface: {x, y, z} and the mineral. */
    public final List<int[]> rocks = new ArrayList<>();
    public final List<String> rockMinerals = new ArrayList<>();
    /** The roads as laid: per road, its cells in order {x, z, y}, with its name and kind. */
    public final List<Road> roads = new ArrayList<>();
    public record Road(String id, String name, String kind, int[] path) {}
    /** (0.4.1) The built world: every structure's lot, doors and reserved volumes (Architecture). */
    public Architecture arch;
    /** (0.4.1) Trees the structures plant (orchards, groves): {x, z} and the species, grown with the rest. */
    final List<int[]> extraTrees = new ArrayList<>();
    final List<String> extraSpecies = new ArrayList<>();
    /** (0.4.1) Where a river falls: {x, bottom y, z, height} at each cell of a falls' curtain (the spray is drawn there). */
    public final List<int[]> falls = new ArrayList<>();
    /** Milliseconds each stage took (for the load log). */
    public final Map<String, Long> timings = new java.util.LinkedHashMap<>();

    private final Noise noise, warpX, warpZ, detail;
    private final float[] h;

    private Terrain(Atlas a) {
        this.atlas = a;
        S = a.size; H = a.height; sea = a.sea;
        top = new int[S * S]; water = new int[S * S]; deck = new int[S * S]; flags = new int[S * S]; biome = new byte[S * S];
        h = new float[S * S];
        java.util.Arrays.fill(water, -1);
        java.util.Arrays.fill(deck, -1);
        noise = new Noise(a.seed); warpX = new Noise(a.seed + 11); warpZ = new Noise(a.seed + 23); detail = new Noise(a.seed + 37);
    }

    public int idx(int x, int z) { return x * S + z; }
    public boolean in(int x, int z) { return x >= 0 && z >= 0 && x < S && z < S; }
    public String biomeAt(int x, int z) { return biomes.get(biome[idx(x, z)]); }
    private byte biomeId(String name) {
        int k = biomes.indexOf(name);
        if (k < 0) { biomes.add(name); k = biomes.size() - 1; }
        return (byte) k;
    }

    /** Grows the world from the design. With cells false only the columns are worked out (previews, checks). */
    public static Terrain generate(Atlas a, boolean cells) {
        Terrain t = new Terrain(a);
        long at = System.nanoTime();
        t.base(); at = t.stage("regions", at);
        t.overlays(); at = t.stage("overlays", at);
        t.variants(); at = t.stage("variants", at);
        t.scarp(); at = t.stage("scarp", at);
        t.coast(); at = t.stage("coast", at);
        t.erode(); at = t.stage("erosion", at);
        t.peaks(false); at = t.stage("peaks", at);
        t.lakes(); at = t.stage("lakes", at);
        t.peaks(true); at = t.stage("isles", at);
        t.rivers(); at = t.stage("rivers", at);
        t.wadis(); at = t.stage("wadis", at);
        t.settle(); at = t.stage("ground", at);
        t.roads(); at = t.stage("roads", at);
        t.causeways(); at = t.stage("causeways", at);
        // (0.4.1) the built world planned on the ground (lots levelled, walls and lanes laid), then the ground's pits filled
        t.arch = Architecture.plan(t); at = t.stage("architecture", at);
        t.pits(); at = t.stage("pits", at);
        t.banks(); at = t.stage("banks", at);
        t.clamp(); at = t.stage("clamp", at);
        t.cliffs(); at = t.stage("cliffs", at);
        if (cells) {
            t.cells = new byte[t.S * t.S * t.H];
            t.columns(); at = t.stage("columns", at);
            t.curtains(); at = t.stage("falls", at);
            t.arch.raise(); at = t.stage("raise", at);
            t.trees(); at = t.stage("trees", at);
            t.outcrops(); at = t.stage("outcrops", at);
        } else { t.plantTrees(false); t.outcrops(); at = t.stage("trees", at); }
        return t;
    }
    private long stage(String name, long since) { long now = System.nanoTime(); timings.put(name, (now - since) / 1_000_000); return now; }

    // ------------------------------------------------------------------ helpers
    private static double smooth(double e0, double e1, double v) { double t = Math.max(0, Math.min(1, (v - e0) / (e1 - e0))); return t * t * (3 - 2 * t); }
    private static double lerp(double a, double b, double t) { return a + (b - a) * t; }
    /** Signed distance (cells) into a box, positive inside. */
    private static double inside(int[] r, double x, double z) {
        double dx = Math.min(x - r[0], r[2] + 1 - x), dz = Math.min(z - r[1], r[3] + 1 - z);
        if (dx >= 0 && dz >= 0) return Math.min(dx, dz);
        double ox = Math.max(0, -dx), oz = Math.max(0, -dz);
        return -Math.sqrt(ox * ox + oz * oz);
    }
    /** Signed distance into a box drawn as a superellipse (power 2: an ellipse; large: the box), positive inside. */
    private static double rounded(int[] r, double x, double z, double power) {
        double cx = (r[0] + r[2] + 1) / 2.0, cz = (r[1] + r[3] + 1) / 2.0, rx = (r[2] + 1 - r[0]) / 2.0, rz = (r[3] + 1 - r[1]) / 2.0;
        double qx = Math.abs(x - cx) / rx, qz = Math.abs(z - cz) / rz;
        double q = Math.pow(Math.pow(qx, power) + Math.pow(qz, power), 1 / power);
        return (1 - q) * Math.min(rx, rz);
    }
    /** A region's own height at a point: its range filled by its noise, ridged as much as it asks. */
    private double regionHeight(Map<String, Object> r, double x, double z) {
        List<?> y = Atlas.arr(r, "y");
        double lo = Atlas.dn(y.get(0)), hi = Atlas.dn(y.get(1)), scale = Atlas.d(r, "scale", 120), ridge = Atlas.d(r, "ridge", 0);
        double wx = x + warpX.at(x / 180, z / 180) * 38, wz = z + warpZ.at(x / 180, z / 180) * 38;
        double n = noise.fbm(wx / scale, wz / scale, 5);
        if (ridge > 0) n = lerp(n, noise.ridge(wx / (scale * 1.3), wz / (scale * 1.3), 5), ridge);
        return lo + (hi - lo) * n;
    }

    /** The domain warp every region's and overlay's edge is drawn through: organic shapes from ruled boxes. */
    private double warpOf(int x, int z, int axis) {
        Noise n = axis == 0 ? warpX : warpZ;
        return n.at(x / 170.0 + axis * 13, z / 170.0 - axis * 7) * 58 + n.at(x / 52.0 + 50, z / 52.0 + 50) * 16 + n.at(x / 17.0 - 30, z / 17.0 + 30) * 4;
    }

    // ------------------------------------------------------------------ 1. the regions
    private List<Map<String, Object>> regions;
    private int[][] regionCells;
    private void base() {
        regions = atlas.list("regions");
        regionCells = new int[regions.size()][];
        for (int k = 0; k < regions.size(); k++) regionCells[k] = atlas.cells(Atlas.arr(regions.get(k), "rect"));
        byte[] regionBiome = new byte[regions.size()];
        for (int k = 0; k < regions.size(); k++) regionBiome[k] = biomeId(Atlas.s(regions.get(k), "biome", "meadow"));
        java.util.stream.IntStream.range(0, S).parallel().forEach(x -> {
            for (int z = 0; z < S; z++) {
                // the two regions the point is deepest in (the one it is in, the nearest other), blended across their seam
                int a = -1, b = -1; double da = -1e9, db = -1e9;
                double wx = x + 0.5 + warpOf(x, z, 0), wz = z + 0.5 + warpOf(x, z, 1);
                for (int k = 0; k < regions.size(); k++) {
                    double d = inside(regionCells[k], wx, wz);
                    if (d > da) { b = a; db = da; a = k; da = d; } else if (d > db) { b = k; db = d; }
                }
                double ha = regionHeight(regions.get(a), x, z);
                double width = Math.min(Atlas.d(regions.get(a), "blend", 40), b < 0 ? 40 : Atlas.d(regions.get(b), "blend", 40));
                double t = b < 0 ? 1 : smooth(0, 2 * width, da - db) * 0.5 + 0.5;
                double hb = b < 0 ? ha : regionHeight(regions.get(b), x, z);
                h[idx(x, z)] = (float) lerp(hb, ha, t);
                // the biome's seam dithered and frayed, never ruled
                double fray = t + (Noise.hash(atlas.seed, x, z, 41) - 0.5) * 0.5 + detail.at(x / 9.0, z / 9.0) * 0.18;
                biome[idx(x, z)] = b < 0 || fray >= 0.5 ? regionBiome[a] : regionBiome[b];
            }
        });
    }

    // ------------------------------------------------------------------ 2. settlements and named grounds
    private void overlays() {
        for (Map<String, Object> o : atlas.list("overlays")) {
            int[] r = atlas.cells(Atlas.arr(o, "rect"));
            List<?> y = Atlas.arr(o, "y");
            double lo = Atlas.dn(y.get(0)), hi = Atlas.dn(y.get(1)), flat = Atlas.d(o, "flatten", 0.5), blend = Atlas.d(o, "blend", 20), terrace = Atlas.d(o, "terrace", 0);
            byte b = biomeId(Atlas.s(o, "biome", "meadow"));
            int x0 = Math.max(0, (int) (r[0] - blend)), x1 = Math.min(S - 1, (int) (r[2] + blend)), z0 = Math.max(0, (int) (r[1] - blend)), z1 = Math.min(S - 1, (int) (r[3] + blend));
            for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
                double d = rounded(r, x + 0.5 + warpOf(x, z, 0) * 0.45, z + 0.5 + warpOf(x, z, 1) * 0.45, Atlas.d(o, "round", 2.6)) + detail.at(x / 40.0, z / 40.0) * blend * 0.35;
                double w = smooth(-blend, blend, d);
                if (w <= 0) continue;
                int i = idx(x, z);
                double own = noise.fbm((x + 911) / 90.0, (z - 377) / 90.0, 4);
                double target = lo + (hi - lo) * lerp(own, 0.5, flat);
                // what the ground was, held into the range, eased toward the settlement's own level as it is flattened
                double held = Math.max(lo, Math.min(hi, h[i]));
                double oh = lerp(held, target, Math.max(flat, 0.35));
                if (terrace > 0) {
                    double step = Math.floor(oh / terrace) * terrace, f = (oh - step) / terrace;
                    oh = step + smooth(0.62, 1.0, f) * terrace;
                }
                h[i] = (float) lerp(h[i], oh, w);
                if (w + (Noise.hash(atlas.seed, x, z, 43) - 0.5) * 0.45 + detail.at(x / 8.0, z / 8.0) * 0.15 > 0.5) biome[i] = b;
            }
        }
    }

    // ------------------------------------------------------------------ 2b. variety within a biome
    /**
     * Patches of another biome inside one (data: a biome's "variants" - {biome, share, scale, lift}): the meadows' woods,
     * the Marches' heath, the desert's dunes and rock, Severian's cypress and olive groves. Where the noise of a variant
     * rises over its share the patch takes over, its ground lifted or lowered a little ('lift'): nowhere is one even
     * expanse.
     */
    private void variants() {
        Map<String, Object> defs = atlas.map("biomes");
        List<String> names = new ArrayList<>(biomes);
        for (int bi = 0; bi < names.size(); bi++) {
            List<Map<String, Object>> vs = Atlas.list(Atlas.map(defs, names.get(bi)), "variants");
            if (vs.isEmpty()) continue;
            byte from = (byte) bi;
            byte[] to = new byte[vs.size()];
            for (int k = 0; k < vs.size(); k++) to[k] = biomeId(Atlas.s(vs.get(k), "biome", names.get(bi)));
            final int salt = bi;
            java.util.stream.IntStream.range(0, S).parallel().forEach(x -> {
                for (int z = 0; z < S; z++) {
                    int i = idx(x, z);
                    if (biome[i] != from) continue;
                    for (int k = 0; k < vs.size(); k++) {
                        Map<String, Object> v = vs.get(k);
                        double scale = Atlas.d(v, "scale", 70), share = Atlas.d(v, "share", 0.15);
                        double wx = x + warpX.at(x / 90.0 + k, z / 90.0) * scale * 0.35, wz = z + warpZ.at(x / 90.0, z / 90.0 - k) * scale * 0.35;
                        double n = noise.fbm((wx + salt * 1013 + k * 377) / scale, (wz - salt * 701 + k * 911) / scale, 3);
                        // fbm sits round 0.5: the top 'share' of it, roughly, is the patch
                        double edge = 0.5 + (0.5 - share) * 0.42;
                        double fray = (Noise.hash(atlas.seed, x, z, 60 + k) - 0.5) * 0.012;
                        if (n + fray > edge) {
                            biome[i] = to[k];
                            // a patch rises or sinks gently from its rim (a mound, a hollow), never a step
                            h[i] += (float) (Atlas.d(v, "lift", 0) * smooth(edge, edge + 0.22, n));
                            break;
                        }
                    }
                }
            });
        }
    }

    // ------------------------------------------------------------------ 3. peaks: Ashmaw, the crags, the Lantern's isle
    /** The peaks: the mountains (after the weathering, so they keep their heights), or the isles (after the lakes they rise from). */
    private void peaks(boolean isles) {
        for (Map<String, Object> p : atlas.list("peaks")) {
            if (Boolean.TRUE.equals(p.get("isle")) != isles) continue;
            double[] at = Atlas.pt(p.get("at"));
            double cx = atlas.x(at[0]), cz = atlas.z(at[1]), top = Atlas.d(p, "y", 80), R = Atlas.d(p, "radius", 40), pow = Atlas.d(p, "power", 1.4);
            double crater = Atlas.d(p, "crater", 0), craterDepth = Atlas.d(p, "craterDepth", 0), rough = Atlas.d(p, "rough", 0.2);
            byte b = biomeId(Atlas.s(p, "biome", "crags"));
            for (int x = (int) Math.max(0, cx - R); x <= Math.min(S - 1, cx + R); x++) for (int z = (int) Math.max(0, cz - R); z <= Math.min(S - 1, cz + R); z++) {
                double r = Math.hypot(x + 0.5 - cx, z + 0.5 - cz) * (1 + noise.at(x / 30.0, z / 30.0) * rough);
                // a crater's rim is the summit: the cone reaches its height at the rim, the crater sinks inside it
                double f = crater > 0 ? Math.max(0, Math.min(1, (R - r) / (R - crater))) : Math.max(0, 1 - r / R);
                if (r >= R) continue;
                int i = idx(x, z);
                double shape = Math.pow(f, pow) + noise.ridge(x / 36.0, z / 36.0, 3) * rough * f * 0.6;
                double ph = h[i] + (top - h[i]) * Math.min(1, shape);
                if (crater > 0 && r < crater) ph -= craterDepth * (1 - (r / crater) * (r / crater));
                if (ph > h[i]) {
                    h[i] = (float) ph;
                    // an isle stands out of its water
                    if (isles && water[i] >= 0 && ph >= water[i] + 0.5) { water[i] = -1; flags[i] &= ~(LAKE | RIVER); }
                }
                if (f > 0.25 && (!isles || water[i] < 0)) biome[i] = b;
            }
        }
    }

    // ------------------------------------------------------------------ 4. the northern scarp, broken by its valleys
    private void scarp() {
        Map<String, Object> s = atlas.map("scarp");
        if (s.isEmpty()) return;
        double line = atlas.z(Atlas.d(s, "z", 45.6)), width = Atlas.d(s, "width", 9), lift = Atlas.d(s, "lift", 6);
        List<Map<String, Object>> gaps = Atlas.list(s, "gaps");
        for (int x = 0; x < S; x++) {
            double gap = 0;
            for (Map<String, Object> g : gaps) {
                double gx = atlas.x(Atlas.pt(g.get("at"))[0]), half = Atlas.d(g, "half", 24);
                gap = Math.max(gap, smooth(half, half * 0.5, Math.abs(x + 0.5 - gx)));
            }
            double wobble = noise.at(x / 140.0, 3.3) * 34 + noise.at(x / 37.0, 9.1) * 9;
            // the cliff's own height wanders: higher bluffs, lower steps, and its foot frays into slopes
            double tall = 0.55 + noise.fbm(x / 90.0, 17.0, 3) * 0.9;
            for (int z = (int) (line - 4 * width); z <= (int) (line + 4 * width); z++) {
                if (z < 0 || z >= S) continue;
                int i = idx(x, z);
                // south of the line (larger z) the plateau; north of it Severian's band. The cliff: the plateau held up to
                // the line, the band held down below it, unless a valley breaks through
                double d = (z + 0.5) - (line + wobble);            // > 0: plateau side
                double south = sample(x, (int) Math.min(S - 1, line + 3 * width)), north = sample(x, (int) Math.max(0, line - 3 * width));
                if (south <= north + 2) continue;
                double edge = d > 0 ? lerp(south + lift * smooth(width * 3, 0, d), h[i], smooth(width * 2, width * 4, d))
                                    : lerp(north, h[i], smooth(width, width * 3, -d));
                double cliff = lerp(lerp(h[i], edge, Math.min(1, tall)), h[i], gap);
                h[i] = (float) cliff;
                if (gap < 0.5 && tall > 0.75 && Math.abs(d) < width) flags[i] |= CLIFF;
            }
        }
    }
    private double sample(int x, int z) { return h[idx(Math.max(0, Math.min(S - 1, x)), Math.max(0, Math.min(S - 1, z)))]; }

    // ------------------------------------------------------------------ 5. the shore belt round all four edges
    private void coast() {
        Map<String, Object> c = atlas.map("coast");
        double deep = Atlas.d(c, "deep", 8), belt = Atlas.d(c, "belt", 32), blend = Atlas.d(c, "blend", 48), floor = Atlas.d(c, "floor", 21);
        double shelf = Atlas.d(c, "shelf", 28), beach = Atlas.d(c, "beach", 32), warp = Atlas.d(c, "warp", 9), wscale = Atlas.d(c, "warpScale", 70);
        List<Map<String, Object>> cliffs = Atlas.list(c, "cliffs");
        byte beachB = biomeId("beach"), seabed = biomeId("seabed");
        java.util.stream.IntStream.range(0, S).parallel().forEach(x -> {
            for (int z = 0; z < S; z++) {
                int i = idx(x, z);
                double e = Math.min(Math.min(x + 0.5, z + 0.5), Math.min(S - 0.5 - x, S - 0.5 - z));
                // coves bite deep, headlands stand out a little: the very edge is always open sea
                double wv = noise.at(x / wscale + 40, z / wscale - 40) * warp + noise.at(x / (wscale * 0.35) - 9, z / (wscale * 0.35) + 9) * warp * 0.45
                        + detail.at(x / 22.0, z / 22.0) * warp * 0.2;
                e += Math.min(wv, deep * 0.6);
                if (e >= belt + blend) continue;
                double cliffBlend = blend;
                for (Map<String, Object> cl : cliffs) {
                    double[] at = Atlas.pt(cl.get("at"));
                    double r = Math.hypot(x - atlas.x(at[0]), z - atlas.z(at[1])) / 16.0;
                    cliffBlend = Math.min(cliffBlend, lerp(Atlas.d(cl, "blend", 6), blend, smooth(Atlas.d(cl, "radius", 4) * 0.6, Atlas.d(cl, "radius", 4), r)));
                }
                // the belt: deep sea at the very edge, a shelf, shallows and coves, beaches up to the land
                double band = smooth(deep, belt, e);
                double beltH = e < deep ? lerp(floor, shelf, Math.max(0, e / deep)) : lerp(shelf, beach, band) + noise.at(x / 26.0, z / 26.0) * 1.6;
                double land = smooth(belt * 0.55, belt + cliffBlend, e);
                double v = lerp(beltH, h[i], land);
                h[i] = (float) v;
                if (v < sea - 0.5) biome[i] = seabed;
                else if (v < sea + 2.2 && land < 0.6) biome[i] = beachB;
                if (land < 0.35 && cliffBlend < blend * 0.5) flags[i] |= CLIFF;
            }
        });
    }

    // ------------------------------------------------------------------ 6. weathering: thermal erosion
    private void erode() {
        Map<String, Object> er = atlas.map("erosion");
        int passes = Atlas.i(er, "passes", 16), smoothPasses = Atlas.i(er, "smooth", 2);
        float talus = (float) Atlas.d(er, "talus", 1.6), cliffTalus = (float) Atlas.d(er, "cliffTalus", 3.4), rate = (float) Atlas.d(er, "rate", 0.45);
        float[] delta = new float[S * S];
        for (int p = 0; p < passes; p++) {
            java.util.Arrays.fill(delta, 0);
            // (0.4.0, optimisation) columns a third of the way round apart at once on every core: a column only ever moves
            // earth into its neighbours a column either side, so columns three apart never touch the same cell
            for (int phase = 0; phase < 3; phase++) {
                final int ph = phase;
                java.util.stream.IntStream.range(0, (S - 2 + 2) / 3).parallel().forEach(k -> {
                    int x = 1 + ph + k * 3;
                    if (x >= S - 1) return;
                    for (int z = 1; z < S - 1; z++) {
                        int i = idx(x, z);
                        float lim = (flags[i] & CLIFF) != 0 ? cliffTalus : talus;
                        int lowest = -1; float drop = 0;
                        float d0 = h[i] - h[i - S], d1 = h[i] - h[i + S], d2 = h[i] - h[i - 1], d3 = h[i] - h[i + 1];
                        if (d0 > drop) { drop = d0; lowest = i - S; }
                        if (d1 > drop) { drop = d1; lowest = i + S; }
                        if (d2 > drop) { drop = d2; lowest = i - 1; }
                        if (d3 > drop) { drop = d3; lowest = i + 1; }
                        if (lowest >= 0 && drop > lim) {
                            float move = (drop - lim) * 0.5f * rate;
                            delta[i] -= move; delta[lowest] += move;
                        }
                    }
                });
            }
            for (int i = 0; i < h.length; i++) h[i] += delta[i];
        }
        // a light smoothing of the small bumps (not the cliffs): ground a body walks, not a pile of noise
        float[] copy = new float[S * S];
        for (int p = 0; p < smoothPasses; p++) {
            System.arraycopy(h, 0, copy, 0, h.length);
            for (int x = 1; x < S - 1; x++) for (int z = 1; z < S - 1; z++) {
                int i = idx(x, z);
                if ((flags[i] & CLIFF) != 0) continue;
                h[i] = copy[i] * 0.5f + (copy[i - S] + copy[i + S] + copy[i - 1] + copy[i + 1]) * 0.125f;
            }
        }
    }

    // ------------------------------------------------------------------ 7. lakes, their marsh fingers and isles
    private final List<double[]> lakeShapes = new ArrayList<>();
    private void lakes() {
        for (Map<String, Object> l : atlas.list("lakes")) {
            double[] at = Atlas.pt(l.get("at")), radii = Atlas.pt(l.get("radii"));
            double cx = atlas.x(at[0]), cz = atlas.z(at[1]), rx = radii[0] * 16, rz = radii[1] * 16;
            int surface = Atlas.i(l, "surface", 40), depth = Atlas.i(l, "depth", 4);
            double warp = Atlas.d(l, "warp", 0.12);
            byte b = biomeId(Atlas.s(l, "biome", "lakeshore")), marsh = biomeId("marsh");
            lakeShapes.add(new double[]{cx, cz, rx, rz, surface, warp});
            double R = Math.max(rx, rz) * 1.7 + 8;
            for (int x = (int) Math.max(1, cx - R); x <= Math.min(S - 2, cx + R); x++) for (int z = (int) Math.max(1, cz - R); z <= Math.min(S - 2, cz + R); z++) {
                double q = lakeQ(x + 0.5, z + 0.5, cx, cz, rx, rz, warp);
                int i = idx(x, z);
                if (q < 1) {
                    double bed = surface - 1 - depth * Math.pow(1 - q * q, 0.6) + noise.at(x / 12.0, z / 12.0) * 0.8;
                    if (h[i] > bed) h[i] = (float) bed;
                    flags[i] |= LAKE;
                    water[i] = surface;
                    biome[i] = b;
                } else if (q < 1.6) {
                    // the shore: the ground brought down to just above the water near it, a rim held up to keep it in
                    double t = smooth(1, 1.6, q);
                    double shore = lerp(surface + 1, h[i], t);
                    h[i] = (float) Math.max(surface + 1, Math.min(h[i], shore + 0.4));
                    if (t < 0.5) biome[i] = b;
                }
            }
            for (Object f : Atlas.arr(l, "marsh")) {
                double[] m = Atlas.pt(f);
                double mx = atlas.x(m[0]), mz = atlas.z(m[1]), mrx = m[2] * 16, mrz = m[3] * 16;
                for (int x = (int) Math.max(1, mx - mrx * 1.4); x <= Math.min(S - 2, mx + mrx * 1.4); x++) for (int z = (int) Math.max(1, mz - mrz * 1.4); z <= Math.min(S - 2, mz + mrz * 1.4); z++) {
                    double q = lakeQ(x + 0.5, z + 0.5, mx, mz, mrx, mrz, 0.3);
                    if (q >= 1.3) continue;
                    int i = idx(x, z);
                    biome[i] = marsh; flags[i] |= MARSH;
                    // a finger of reed-bed: ground at the water's height, pools of standing water in its hollows
                    double pool = noise.at(x / 7.0 + 5, z / 7.0 - 9) + (1 - q) * 0.5;
                    if (q < 1 && pool > 0.25) { h[i] = surface - 1; water[i] = surface; flags[i] |= LAKE; }
                    else h[i] = (float) Math.max(surface, Math.min(h[i], surface + 1 + q));
                }
            }
        }
    }
    private double lakeQ(double x, double z, double cx, double cz, double rx, double rz, double warp) {
        double dx = (x - cx) / rx, dz = (z - cz) / rz;
        double a = Math.atan2(dz, dx);
        double wob = 1 + noise.at(Math.cos(a) * 1.7 + cx * 0.01, Math.sin(a) * 1.7 + cz * 0.01) * warp + detail.at(x / 18.0, z / 18.0) * warp * 0.35;
        return Math.sqrt(dx * dx + dz * dz) / wob;
    }

    // ------------------------------------------------------------------ 8. rivers, their gorge and falls
    private void rivers() {
        float[] best = new float[S * S];
        java.util.Arrays.fill(best, Float.MAX_VALUE);
        for (Map<String, Object> r : atlas.list("rivers")) {
            List<double[]> pts = new ArrayList<>();
            for (Object o : Atlas.arr(r, "points")) { double[] p = Atlas.pt(o); pts.add(new double[]{atlas.x(p[0]), atlas.z(p[1]), p[2]}); }
            double[] widths = Atlas.pt(r.get("width"));
            double bank = Atlas.d(r, "bank", 4);
            Map<String, Object> gorge = Atlas.map(r, "gorge");
            double gFrom = gorge.isEmpty() ? 0 : atlas.z(Atlas.d(gorge, "from", 0)), gTo = gorge.isEmpty() ? 0 : atlas.z(Atlas.d(gorge, "to", 0));
            double ledge = Atlas.d(gorge, "ledge", 3);
            List<double[]> line = meander(resample(pts, 1.0), widths[widths.length - 1], gorge.isEmpty() ? Double.NaN : Math.min(gFrom, gTo), gorge.isEmpty() ? Double.NaN : Math.max(gFrom, gTo));
            double total = line.size();
            // each point's valley width, worked out first and smoothed along the river (a width that jumped from one
            // point to the next left a step of the whole depth where the narrower point cut its slice)
            double[] valley = new double[line.size()];
            for (int k = 0; k < line.size(); k++) {
                double[] p = line.get(k);
                double w0 = lerp(widths[0], widths.length > 1 ? widths[1] : widths[0], k / Math.max(1, total - 1)) / 2;
                double dx = k + 1 < line.size() ? line.get(k + 1)[0] - p[0] : p[0] - line.get(k - 1)[0], dz = k + 1 < line.size() ? line.get(k + 1)[1] - p[1] : p[1] - line.get(k - 1)[1];
                double len = Math.max(1e-6, Math.hypot(dx, dz)), nx = -dz / len, nz = dx / len, hs = 0;
                for (double side = w0 + 6; side <= w0 + 24; side += 6)
                    hs = Math.max(hs, Math.max(sample((int) (p[0] + nx * side), (int) (p[1] + nz * side)), sample((int) (p[0] - nx * side), (int) (p[1] - nz * side))));
                valley[k] = Math.max(bank, Math.min(42, (hs - (p[2] + 1)) * 3.0));
            }
            double[] wide = new double[valley.length];
            for (int k = 0; k < valley.length; k++) { double m = 0; for (int j = Math.max(0, k - 30); j <= Math.min(valley.length - 1, k + 30); j++) m = Math.max(m, valley[j]); wide[k] = m; }
            for (int k = 0; k < valley.length; k++) { double sum = 0; int c = 0; for (int j = Math.max(0, k - 30); j <= Math.min(valley.length - 1, k + 30); j++) { sum += wide[j]; c++; } valley[k] = sum / c; }
            for (int k = 0; k < line.size(); k++) {
                double[] p = line.get(k);
                double w = lerp(widths[0], widths.length > 1 ? widths[1] : widths[0], k / Math.max(1, total - 1)) * (1 + noise.at(k / 40.0, 7.7) * 0.18);
                double half = w / 2;
                boolean inGorge = !gorge.isEmpty() && p[1] >= Math.min(gFrom, gTo) && p[1] <= Math.max(gFrom, gTo);
                // the valley: as wide as the land stands above the water needs (a cell up for every three across), so a
                // river below its country runs in a valley, not a trench; the gorge keeps its walls
                double b = inGorge ? ledge : valley[k];
                double R = half + b + 1;
                int s = (int) Math.round(p[2]);
                for (int x = (int) Math.floor(p[0] - R); x <= (int) Math.ceil(p[0] + R); x++) for (int z = (int) Math.floor(p[1] - R); z <= (int) Math.ceil(p[1] + R); z++) {
                    if (x < 1 || z < 1 || x >= S - 1 || z >= S - 1) continue;
                    double d = Math.hypot(x + 0.5 - p[0], z + 0.5 - p[1]);
                    int i = idx(x, z);
                    if (d < half) {
                        if (d >= best[i]) continue;
                        best[i] = (float) d;
                        double depth = Math.max(1.2, Math.min(4, w * 0.3)) * (1 - (d / half) * (d / half));
                        double bed = s - 1 - depth;
                        if (h[i] > bed) h[i] = (float) bed;
                        if (water[i] < 0 || (flags[i] & LAKE) == 0) water[i] = s;
                        flags[i] |= RIVER;
                        if (k > 0 && line.get(k - 1)[2] - p[2] > 0.5) flags[i] |= FALLS;
                    } else if (d < half + b && (flags[i] & (RIVER | LAKE)) == 0) {
                        // the bank: down to just above the water (a gorge keeps a narrow ledge, then its walls stand)
                        double t = smooth(0, 1, (d - half) / b);
                        double target = inGorge ? s + 1 : lerp(s + 1, h[i], t);
                        h[i] = (float) Math.max(s + 1, Math.min(h[i], target));
                        if (inGorge && d >= half + b - 1.5) flags[i] |= CLIFF;
                    }
                }
            }
        }
        // a cell of water with nothing under the surface beside it would spill: falls apart, the banks are held up to it
    }
    /**
     * A river's line made to wander: each point pushed across its course by a slow noise, the push fading to nothing at
     * the design's own points (where a bridge, a falls or a mouth was placed) and inside a gorge (its walls stand where
     * they were drawn).
     */
    private List<double[]> meander(List<double[]> line, double width, double gorgeZ0, double gorgeZ1) {
        if (line.size() < 4) return line;
        List<double[]> out = new ArrayList<>(line.size());
        double amp = Math.max(4, width * 1.6);
        int seg = 0;
        for (int k = 0; k < line.size(); k++) {
            double[] p = line.get(k);
            double[] a = line.get(Math.max(0, k - 2)), b = line.get(Math.min(line.size() - 1, k + 2));
            double dx = b[0] - a[0], dz = b[1] - a[1], len = Math.max(1e-6, Math.hypot(dx, dz));
            // fades in and out over the first and last forty cells of the whole line (its mouth and its source stay put)
            double fade = smooth(0, 40, k) * smooth(0, 40, line.size() - 1 - k);
            if (!Double.isNaN(gorgeZ0) && p[1] >= gorgeZ0 - 20 && p[1] <= gorgeZ1 + 20) fade *= 0.15;
            double push = (noise.at(k / 55.0 + seg * 3.1, 11.3) * 0.75 + noise.at(k / 19.0, 4.7) * 0.25) * amp * fade;
            out.add(new double[]{p[0] - dz / len * push, p[1] + dx / len * push, p[2]});
        }
        return out;
    }

    /** A polyline resampled at an even spacing (cells), carrying its third value (a height) along it. */
    private static List<double[]> resample(List<double[]> pts, double step) {
        List<double[]> out = new ArrayList<>();
        for (int k = 0; k + 1 < pts.size(); k++) {
            double[] a = pts.get(k), b = pts.get(k + 1);
            double len = Math.hypot(b[0] - a[0], b[1] - a[1]);
            int n = Math.max(1, (int) Math.ceil(len / step));
            for (int j = 0; j < n; j++) {
                double t = j / (double) n;
                out.add(new double[]{lerp(a[0], b[0], t), lerp(a[1], b[1], t), a.length > 2 ? lerp(a[2], b[2], t) : 0});
            }
        }
        double[] last = pts.get(pts.size() - 1);
        out.add(new double[]{last[0], last[1], last.length > 2 ? last[2] : 0});
        return out;
    }
    /** A Catmull-Rom curve through control points, sampled every step cells. */
    private static List<double[]> spline(List<double[]> c, double step) {
        List<double[]> out = new ArrayList<>();
        if (c.size() < 2) { out.addAll(c); return out; }
        for (int k = 0; k + 1 < c.size(); k++) {
            double[] p0 = c.get(Math.max(0, k - 1)), p1 = c.get(k), p2 = c.get(k + 1), p3 = c.get(Math.min(c.size() - 1, k + 2));
            double len = Math.hypot(p2[0] - p1[0], p2[1] - p1[1]);
            int n = Math.max(1, (int) Math.ceil(len / step));
            for (int j = 0; j < n; j++) {
                double t = j / (double) n, t2 = t * t, t3 = t2 * t;
                double x = 0.5 * (2 * p1[0] + (-p0[0] + p2[0]) * t + (2 * p0[0] - 5 * p1[0] + 4 * p2[0] - p3[0]) * t2 + (-p0[0] + 3 * p1[0] - 3 * p2[0] + p3[0]) * t3);
                double z = 0.5 * (2 * p1[1] + (-p0[1] + p2[1]) * t + (2 * p0[1] - 5 * p1[1] + 4 * p2[1] - p3[1]) * t2 + (-p0[1] + 3 * p1[1] - 3 * p2[1] + p3[1]) * t3);
                out.add(new double[]{x, z});
            }
        }
        out.add(c.get(c.size() - 1));
        return out;
    }

    // ------------------------------------------------------------------ 9. the dry wadis
    private void wadis() {
        for (Map<String, Object> w : atlas.list("wadis")) {
            List<double[]> pts = new ArrayList<>();
            for (Object o : Atlas.arr(w, "points")) { double[] p = Atlas.pt(o); pts.add(new double[]{atlas.x(p[0]), atlas.z(p[1])}); }
            double half = Atlas.d(w, "width", 6) / 2, depth = Atlas.d(w, "depth", 3);
            List<double[]> line = spline(pts, 1.0);
            // the bed descends: never higher than where it has come from
            double bedY = Double.MAX_VALUE;
            for (double[] p : line) {
                int px = (int) p[0], pz = (int) p[1];
                if (!in(px, pz)) continue;
                bedY = Math.min(bedY, h[idx(px, pz)] - depth);
                for (int x = (int) (p[0] - half - 3); x <= (int) (p[0] + half + 3); x++) for (int z = (int) (p[1] - half - 3); z <= (int) (p[1] + half + 3); z++) {
                    if (!in(x, z)) continue;
                    int i = idx(x, z);
                    if ((flags[i] & (RIVER | LAKE)) != 0) continue;
                    double d = Math.hypot(x + 0.5 - p[0], z + 0.5 - p[1]);
                    double target = d < half ? bedY : lerp(bedY, h[i], smooth(half, half + 3, d));
                    if (target < h[i]) { h[i] = (float) Math.max(sea + 1, target); if (d < half) flags[i] |= WADI; }
                }
            }
        }
    }

    // ------------------------------------------------------------------ 10. the ground's own cells (before the roads lay on it)
    private void settle() {
        for (int i = 0; i < h.length; i++) {
            top[i] = (int) Math.floor(h[i]);
            if (top[i] < 1) top[i] = 1;
            if (water[i] < 0 && top[i] < sea) { water[i] = sea; flags[i] |= SEA; }
        }
    }

    // ------------------------------------------------------------------ 11. the roads: laid at a walkable grade, bridged over water
    private void roads() {
        // (0.4.1, gamefixes: one-block holes and ledges along the roads) each cell's shoulder is shaped by the nearest point
        // of the road only - every point along a road used to stamp the cells round it in turn, the later ones over the
        // earlier, and left notches a cell deep and steps along both verges
        float[] nearest = new float[S * S];
        java.util.Arrays.fill(nearest, Float.MAX_VALUE);
        Map<String, Object> rs = atlas.map("roads");
        double grade = Atlas.d(rs, "grade", 0.34);
        Map<String, Object> kinds = Atlas.map(rs, "kinds");
        for (Map<String, Object> r : Atlas.list(rs, "list")) {
            Map<String, Object> kind = Atlas.map(kinds, Atlas.s(r, "kind", "path"));
            double width = Atlas.d(kind, "width", 2), shoulder = Atlas.d(kind, "shoulder", 3);
            List<double[]> ctrl = new ArrayList<>();
            String ring = Atlas.s(r, "ring", null);
            if (ring != null) ctrl = ringPath(ring);
            else for (Object o : Atlas.arr(r, "points")) { double[] p = Atlas.pt(o); ctrl.add(new double[]{atlas.x(p[0]), atlas.z(p[1])}); }
            if (ctrl.size() < 2) continue;
            List<double[]> line = spline(ctrl, 0.5);
            int n = line.size();
            double[] y = new double[n];
            boolean[] wet = new boolean[n];
            for (int k = 0; k < n; k++) {
                int x = (int) Math.max(0, Math.min(S - 1, line.get(k)[0])), z = (int) Math.max(0, Math.min(S - 1, line.get(k)[1]));
                int i = idx(x, z);
                wet[k] = water[i] >= 0 && water[i] >= top[i];
                y[k] = wet[k] ? water[i] + 1 : top[i];
            }
            // the ground along it, smoothed (a road does not climb every hummock), never under water it crosses
            double[] sm = new double[n];
            int win = 28;
            for (int k = 0; k < n; k++) {
                double sum = 0; int c = 0;
                for (int j = Math.max(0, k - win); j <= Math.min(n - 1, k + win); j++) { sum += y[j]; c++; }
                sm[k] = sum / c;
            }
            for (int k = 0; k < n; k++) {
                int x = (int) line.get(k)[0], z = (int) line.get(k)[1];
                if (!in(x, z)) continue;
                int i = idx(x, z);
                if (wet[k]) sm[k] = Math.max(sm[k], water[i] + 1);
            }
            // at most 'grade' cells up or down per cell along (0.5 cells per sample): forward, then back
            double per = grade * 0.5;
            for (int k = 1; k < n; k++) sm[k] = Math.max(sm[k - 1] - per, Math.min(sm[k - 1] + per, sm[k]));
            for (int k = n - 2; k >= 0; k--) sm[k] = Math.max(sm[k + 1] - per, Math.min(sm[k + 1] + per, sm[k]));
            int[] path = new int[n * 3];
            String kindName = Atlas.s(r, "kind", "path");
            for (int k = 0; k < n; k++) {
                double[] p = line.get(k);
                int ry = (int) Math.round(sm[k]);
                path[k * 3] = (int) p[0]; path[k * 3 + 1] = (int) p[1]; path[k * 3 + 2] = ry;
                double half = width / 2;
                // how far the road stands from the ground beside it: the bank runs out at two cells across per cell of height
                int sideX = (int) Math.max(0, Math.min(S - 1, p[0] + half + 3)), sideZ = (int) Math.max(0, Math.min(S - 1, p[1] + half + 3));
                double cut = Math.abs(top[idx(sideX, sideZ)] - ry), cut2 = Math.abs(top[idx((int) Math.max(0, Math.min(S - 1, p[0] - half - 3)), (int) Math.max(0, Math.min(S - 1, p[1] - half - 3)))] - ry);
                double bank = Math.min(16, Math.max(shoulder, Math.max(cut, cut2) * 2.2 + 2));
                double R = half + bank;
                for (int x = (int) Math.floor(p[0] - R); x <= (int) Math.ceil(p[0] + R); x++) for (int z = (int) Math.floor(p[1] - R); z <= (int) Math.ceil(p[1] + R); z++) {
                    if (x < 1 || z < 1 || x >= S - 1 || z >= S - 1) continue;
                    int i = idx(x, z);
                    double d = Math.hypot(x + 0.5 - p[0], z + 0.5 - p[1]);
                    boolean overWater = water[i] >= 0 && water[i] >= top[i];
                    if (d <= half + 0.35) {
                        if (overWater) {
                            // a bridge: a deck over the water, the water and its bed left under it
                            int dk = Math.max(ry, water[i] + 1);
                            deck[i] = Math.max(deck[i], dk);
                            flags[i] |= BRIDGE | ROAD;
                            roadKind.putIfAbsent(i, kindName);
                        } else {
                            if ((flags[i] & ROAD) == 0 || Math.abs(top[i] - ry) > 0) top[i] = (flags[i] & ROAD) != 0 ? Math.max(top[i], ry) : ry;
                            flags[i] |= ROAD;
                            roadKind.put(i, kindName);
                        }
                    } else if (d < R && (flags[i] & (ROAD | RIVER | LAKE | SEA)) == 0 && d < nearest[i]) {
                        // the shoulder: cut or filled toward the road so it is never a wall or a trench (from the ground as it
                        // was, by the nearest point of the road)
                        if (nearest[i] == Float.MAX_VALUE) shoulderBase.put(i, top[i]);
                        nearest[i] = (float) d;
                        double t = smooth(0, 1, (d - half) / bank);
                        top[i] = (int) Math.round(lerp(ry, shoulderBase.get(i), t));
                    }
                }
            }
            roads.add(new Road(Atlas.s(r, "id", "road"), Atlas.s(r, "name", "Road"), kindName, path));
        }
    }
    final Map<Integer, String> roadKind = new HashMap<>();
    /** The ground's height under a road's shoulder before any road shaped it. */
    private final Map<Integer, Integer> shoulderBase = new HashMap<>();
    private List<double[]> ringPath(String lakeId) {
        List<double[]> out = new ArrayList<>();
        for (Map<String, Object> l : atlas.list("lakes")) {
            if (!lakeId.equals(Atlas.s(l, "id", ""))) continue;
            double[] at = Atlas.pt(l.get("at")), radii = Atlas.pt(l.get("radii"));
            double cx = atlas.x(at[0]), cz = atlas.z(at[1]), rx = radii[0] * 16, rz = radii[1] * 16, off = Atlas.d(l, "shorePath", 6), warp = Atlas.d(l, "warp", 0.12);
            for (int k = 0; k <= 72; k++) {
                double a = k * Math.PI * 2 / 72;
                // walk out from the middle along the angle to the first dry cell, then the path's offset beyond it
                double r = 0.6;
                while (r < 2.2 && lakeQ(cx + Math.cos(a) * rx * r, cz + Math.sin(a) * rz * r, cx, cz, rx, rz, warp) < 1) r += 0.01;
                double px = cx + Math.cos(a) * (rx * r + off), pz = cz + Math.sin(a) * (rz * r + off);
                out.add(new double[]{px, pz});
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ 12. the Lantern's causeways
    private void causeways() {
        for (Map<String, Object> c : atlas.list("causeways")) {
            double[] f = Atlas.pt(c.get("from")), t = Atlas.pt(c.get("to"));
            double fx = atlas.x(f[0]), fz = atlas.z(f[1]), tx = atlas.x(t[0]), tz = atlas.z(t[1]);
            double half = Atlas.d(c, "width", 4) / 2;
            int y = Atlas.i(c, "y", 45);
            double len = Math.hypot(tx - fx, tz - fz);
            for (double s = 0; s <= len; s += 0.5) {
                double px = lerp(fx, tx, s / len), pz = lerp(fz, tz, s / len);
                for (int x = (int) (px - half - 1); x <= (int) (px + half + 1); x++) for (int z = (int) (pz - half - 1); z <= (int) (pz + half + 1); z++) {
                    if (!in(x, z)) continue;
                    double d = Math.hypot(x + 0.5 - px, z + 0.5 - pz);
                    int i = idx(x, z);
                    if (d <= half) { top[i] = Math.max(y, Math.min(top[i], y + 1)); water[i] = -1; flags[i] = (flags[i] & ~(LAKE | RIVER)) | CAUSEWAY | ROAD; roadKind.put(i, "path"); }
                    else if (d <= half + 1 && water[i] >= 0) top[i] = Math.max(top[i], y - 1);
                }
            }
        }
    }

    // ------------------------------------------------------------------ 13. water held in: no water stands above dry ground beside it
    private void banks() {
        for (int pass = 0; pass < 3; pass++) {
            for (int x = 1; x < S - 1; x++) for (int z = 1; z < S - 1; z++) {
                int i = idx(x, z);
                if (water[i] < 0 || water[i] <= top[i]) continue;
                for (int n : new int[]{i - S, i + S, i - 1, i + 1}) {
                    if (water[n] >= 0 && water[n] > top[n]) continue;          // water beside water: one body (or a cascade)
                    if (top[n] < water[i]) {
                        // a dry cell lower than the water beside it: brought up to the water's edge
                        top[n] = water[i];
                    }
                }
            }
        }
        for (int i = 0; i < top.length; i++) if (water[i] >= 0 && water[i] <= top[i]) { water[i] = -1; flags[i] &= ~(SEA | LAKE | RIVER); }
    }

    /**
     * (0.4.1, gamefixes: one-block-deep holes in the ground, worst about the spawn) a single column sunk below all four
     * of its neighbours (or one of two side by side below all six round them) - left where a road's shoulder, a lot's
     * edge or the weathering rounded one cell down - is filled to its lowest neighbour; a lone column standing proud of
     * all four is taken down to its highest. Water, bridges and built lots are left as they are.
     */
    private void pits() {
        int filled = 0;
        for (int pass = 0; pass < 3; pass++) {
            for (int x = 1; x < S - 1; x++) for (int z = 1; z < S - 1; z++) {
                int i = idx(x, z);
                if (water[i] >= 0 || deck[i] >= 0 || (flags[i] & BUILT) != 0) continue;
                int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
                for (int n : new int[]{i - S, i + S, i - 1, i + 1}) { if (water[n] >= 0) { lo = Integer.MIN_VALUE; break; } lo = Math.min(lo, top[n]); hi = Math.max(hi, top[n]); }
                if (lo == Integer.MIN_VALUE) continue;
                if (top[i] < lo) { top[i] = lo; filled++; continue; }
                if (top[i] > hi && (flags[i] & (ROAD | CLIFF)) == 0) { top[i] = hi; filled++; continue; }
                // a two-cell trough: this and one neighbour both under every other cell round them
                for (int n : new int[]{i + S, i + 1}) {
                    if (top[n] != top[i] || water[n] >= 0 || (flags[n] & BUILT) != 0) continue;
                    int rim = Integer.MAX_VALUE;
                    int nx = n / S, nz = n % S;
                    if (nx <= 0 || nz <= 0 || nx >= S - 1 || nz >= S - 1) continue;
                    for (int m : new int[]{i - S, i + S, i - 1, i + 1, n - S, n + S, n - 1, n + 1}) if (m != i && m != n) rim = Math.min(rim, water[m] >= 0 ? Integer.MIN_VALUE : top[m]);
                    if (rim != Integer.MIN_VALUE && top[i] < rim) { top[i] = rim; top[n] = rim; filled += 2; }
                }
            }
        }
        timings.put("pitsFilled", (long) filled);
    }

    private void clamp() {
        int ceiling = atlas.ceiling;
        for (int i = 0; i < top.length; i++) { top[i] = Math.max(1, Math.min(ceiling, top[i])); if (deck[i] > ceiling) deck[i] = ceiling; }
    }

    /** Where the ground falls more than two cells to a neighbour: its face is rock. */
    private void cliffs() {
        for (int x = 1; x < S - 1; x++) for (int z = 1; z < S - 1; z++) {
            int i = idx(x, z), drop = 0;
            for (int n : new int[]{i - S, i + S, i - 1, i + 1}) drop = Math.max(drop, top[i] - top[n]);
            if (drop >= 3) flags[i] |= CLIFF; else if (drop <= 1 && (flags[i] & CLIFF) != 0 && (flags[i] & ROAD) != 0) flags[i] &= ~CLIFF;
        }
    }

    // ------------------------------------------------------------------ 14. the cells
    private int block(String name) {
        if (name == null || name.equals("none")) return Blocks.AIR;
        try { return Blocks.class.getField(name.toUpperCase(java.util.Locale.ROOT)).getInt(null); }
        catch (ReflectiveOperationException e) { throw new IllegalStateException("shadowveil.json names a block the game does not have: " + name); }
    }
    private final Map<String, int[]> mats = new java.util.concurrent.ConcurrentHashMap<>();
    /** A biome's {top, soil, soilDepth, rock, cliff, shore, grassBelow, grassTop}. */
    private int[] mats(String biome) {
        return mats.computeIfAbsent(biome, k -> {
            Map<String, Object> b = Atlas.map(atlas.map("biomes"), k);
            return new int[]{block(Atlas.s(b, "top", "grass")), block(Atlas.s(b, "soil", "dirt")), Atlas.i(b, "soilDepth", 3), block(Atlas.s(b, "rock", "stone")),
                    block(Atlas.s(b, "cliff", "stone")), block(Atlas.s(b, "shore", "sand")), Atlas.i(b, "grassBelow", 0), block(Atlas.s(b, "grassTop", "grass"))};
        });
    }
    private void columns() {
        Map<String, Object> kinds = Atlas.map(atlas.map("roads"), "kinds");
        Map<String, int[]> roadMat = new HashMap<>();
        for (var e : kinds.entrySet()) {
            if (!(e.getValue() instanceof Map<?, ?>)) continue;
            @SuppressWarnings("unchecked") Map<String, Object> k = (Map<String, Object>) e.getValue();
            roadMat.put(e.getKey(), new int[]{block(Atlas.s(k, "surface", "gravel")), block(Atlas.s(k, "edge", "dirt")), block(Atlas.s(k, "bridge", "planks"))});
        }
        for (String b : new ArrayList<>(biomes)) mats(b);
        java.util.stream.IntStream.range(0, S).parallel().forEach(x -> {
            for (int z = 0; z < S; z++) {
                int i = idx(x, z), t = top[i], base = i * H;
                int[] m = mats(biomes.get(biome[i]));
                int low = t;
                if (x > 0) low = Math.min(low, top[i - S]); if (x < S - 1) low = Math.min(low, top[i + S]);
                if (z > 0) low = Math.min(low, top[i - 1]); if (z < S - 1) low = Math.min(low, top[i + 1]);
                boolean wet = water[i] >= 0, cliff = (flags[i] & CLIFF) != 0 || t - low >= 3;
                int surface = m[0];
                if (m[6] > 0 && t < m[6]) surface = m[7];
                if (wet) surface = m[5];
                else if (t <= sea + 1 && (flags[i] & ROAD) == 0) surface = m[5] == Blocks.MUD ? Blocks.SAND : m[5];
                if (cliff && !wet) surface = m[4];
                if ((flags[i] & ROAD) != 0 && (flags[i] & BRIDGE) == 0) {
                    int[] rm = roadMat.getOrDefault(roadKind.getOrDefault(i, "path"), new int[]{Blocks.GRAVEL, Blocks.DIRT, Blocks.PLANKS});
                    // the road's crown, its edge worn ragged where it meets the verge
                    boolean edge = false;
                    for (int n : new int[]{i - S, i + S, i - 1, i + 1}) if (n >= 0 && n < flags.length && (flags[n] & ROAD) == 0) edge = true;
                    // (0.4.1) worn in patches along the verge, not cell by cell (a road read as torn at its edges)
                    surface = edge && detail.at(x / 3.5, z / 3.5) > 0.05 ? rm[1] : rm[0];
                }
                if ((flags[i] & WADI) != 0) surface = Blocks.SAND;
                for (int y = 0; y <= t && y < H; y++) {
                    int b;
                    if (y == t) b = surface;
                    else if (cliff && y > low - 1) b = m[4];
                    else if (y >= t - m[2]) b = m[1] == Blocks.SAND && y < t - 2 ? Blocks.SANDSTONE : m[1];
                    else b = m[3];
                    cells[base + y] = (byte) b;
                }
                if (wet) for (int y = t + 1; y <= water[i] && y < H; y++) cells[base + y] = (byte) Blocks.WATER;
                if (deck[i] >= 0) {
                    int[] rm = roadMat.getOrDefault(roadKind.getOrDefault(i, "main"), new int[]{Blocks.GRAVEL, Blocks.DIRT, Blocks.STONE_BRICKS});
                    cells[base + deck[i]] = (byte) (rm[2] == Blocks.AIR ? Blocks.PLANKS : rm[2]);
                    for (int y = deck[i] + 1; y <= deck[i] + 3 && y < H; y++) cells[base + y] = 0;
                }
            }
        });
    }

    /**
     * (0.4.1, gamefixes: water does not actually flow) where a river drops - its falls, the steps of its rapids - the water
     * pours over the lip as a curtain: each cell below a higher stretch of the same river is filled from its own surface up
     * to the stretch above, so the drop is a sheet of falling water, not a step of still water with air in front of it.
     */
    private void curtains() {
        for (int x = 1; x < S - 1; x++) for (int z = 1; z < S - 1; z++) {
            int i = idx(x, z);
            if ((flags[i] & RIVER) == 0 || water[i] < 0 || deck[i] >= 0) continue;
            int up = water[i];
            for (int n : new int[]{i - S, i + S, i - 1, i + 1}) if ((flags[n] & RIVER) != 0 && water[n] > up && deck[n] < 0) up = water[n];
            if (up - water[i] < 2) continue;
            int base = i * H;
            for (int y = water[i] + 1; y <= up && y < H; y++) if (cells[base + y] == 0) cells[base + y] = (byte) Blocks.WATER;
            falls.add(new int[]{x, water[i], z, up - water[i]});
        }
    }

    // ------------------------------------------------------------------ 15. trees
    private Map<String, Object> treeDefs;
    private void trees() { plantTrees(true); }
    private void plantTrees(boolean grow) {
        treeDefs = atlas.map("trees");
        Map<String, Object> biomeDefs = atlas.map("biomes");
        int cell = 4;
        for (int cx = 0; cx < S / cell; cx++) for (int cz = 0; cz < S / cell; cz++) {
            int x = cx * cell + (int) (Noise.hash(atlas.seed, cx, cz, 1) * cell), z = cz * cell + (int) (Noise.hash(atlas.seed, cx, cz, 2) * cell);
            if (x < 3 || z < 3 || x >= S - 3 || z >= S - 3) continue;
            int i = idx(x, z);
            if (water[i] >= 0 || (flags[i] & (ROAD | CLIFF | BRIDGE | CAUSEWAY | WADI | BUILT | YARD)) != 0) continue;
            Map<String, Object> trees = Atlas.map(Atlas.map(biomeDefs, biomes.get(biome[i])), "trees");
            if (trees.isEmpty()) continue;
            // a road's verge stays clear; so does ground that is too steep to root in
            boolean clear = true;
            for (int dx = -3; dx <= 3 && clear; dx++) for (int dz = -3; dz <= 3 && clear; dz++) {
                int n = idx(x + dx, z + dz);
                if ((flags[n] & (ROAD | BRIDGE | BUILT)) != 0 || (Math.abs(dx) <= 1 && Math.abs(dz) <= 1 && (Math.abs(top[n] - top[i]) > 1 || water[n] >= 0))) clear = false;
            }
            if (!clear) continue;
            double roll = Noise.hash(atlas.seed, x, z, 3);
            // a wood's density waxes and wanes across it: clearings and thickets, not an even plantation
            double patch = 0.45 + noise.fbm(x / 64.0 + 300, z / 64.0 - 300, 3) * 1.1;
            String pick = null; double acc = 0;
            for (var e : trees.entrySet()) {
                acc += Atlas.dn(e.getValue()) * cell * cell * patch;
                if (roll < acc) { pick = e.getKey(); break; }
            }
            if (pick == null) continue;
            int sp = species.indexOf(pick);
            if (sp < 0) { species.add(pick); sp = species.size() - 1; }
            this.trees.add(new int[]{x, z, top[i] + 1, sp});
            flags[i] |= TREE;
            if (grow) grow(pick, x, top[i] + 1, z);
        }
        // (0.4.1) the orchards and groves the structures planted, grown as the world's own trees (so they are worked)
        for (int k = 0; k < extraTrees.size(); k++) {
            int[] p = extraTrees.get(k);
            if (!in(p[0], p[1])) continue;
            int i = idx(p[0], p[1]);
            if (water[i] >= 0 || (flags[i] & (ROAD | BUILT | TREE)) != 0) continue;
            String pick = extraSpecies.get(k);
            int sp = species.indexOf(pick);
            if (sp < 0) { species.add(pick); sp = species.size() - 1; }
            this.trees.add(new int[]{p[0], p[1], top[i] + 1, sp});
            flags[i] |= TREE;
            if (grow) grow(pick, p[0], top[i] + 1, p[1]);
        }
        if (grow) boulders();
    }

    /**
     * The loose stone of the wild: a biome's "boulders" ({block, share}) - a single block half sunk, or a cluster of two
     * to four - on open ground off the roads and the water: the desert's sandstone, the crags' granite, the moor's grey
     * stones, Ashmaw's basalt. What makes open country read as country and not as a lawn.
     */
    private void boulders() {
        Map<String, Object> biomeDefs = atlas.map("biomes");
        Map<String, int[]> byBiome = new HashMap<>();
        for (int x = 3; x < S - 3; x += 3) for (int z = 3; z < S - 3; z += 3) {
            int i = idx(x, z);
            if (water[i] >= 0 || (flags[i] & (ROAD | BRIDGE | CAUSEWAY | TREE | WADI | BUILT | YARD)) != 0) continue;
            String b = biomes.get(biome[i]);
            int[] spec = byBiome.computeIfAbsent(b, k -> {
                Map<String, Object> d = Atlas.map(Atlas.map(biomeDefs, k), "boulders");
                return d.isEmpty() ? new int[]{0, 0} : new int[]{block(Atlas.s(d, "block", "stone")), (int) (Atlas.d(d, "share", 0) * 1_000_000)};
            });
            if (spec[1] <= 0 || Noise.hash(atlas.seed, x, z, 90) * 1_000_000 * 9 >= spec[1] * 9 * 9) continue;
            int n = 1 + (int) (Noise.hash(atlas.seed, x, z, 91) * 4);
            for (int k = 0; k < n; k++) {
                int bx = x + (int) (Noise.hash(atlas.seed, x, z, 92 + k) * 3) - 1, bz = z + (int) (Noise.hash(atlas.seed, x, z, 96 + k) * 3) - 1;
                int j = idx(bx, bz);
                if (water[j] >= 0 || (flags[j] & (ROAD | BRIDGE | TREE | BUILT | YARD)) != 0) continue;
                int y = top[j] + (k == 0 && n > 2 ? 1 : 0);
                set(bx, top[j] + 1, bz, spec[0], false);
                if (y > top[j]) set(bx, top[j] + 2, bz, spec[0], false);
            }
        }
    }
    private void set(int x, int y, int z, int b, boolean overwrite) {
        if (x < 0 || z < 0 || x >= S || z >= S || y < 0 || y >= H) return;
        int at = (x * S + z) * H + y;
        if (overwrite || cells[at] == 0) cells[at] = (byte) b;
    }
    private void grow(String sp, int x, int y, int z) {
        Map<String, Object> d = Atlas.map(treeDefs, sp);
        int log = block(Atlas.s(d, "log", "log")), leaves = block(Atlas.s(d, "leaves", "leaves"));
        double[] trunkR = Atlas.pt(d.get("trunk"));
        double roll = Noise.hash(atlas.seed, x, z, 9);
        int trunk = (int) Math.round(lerp(trunkR[0], trunkR.length > 1 ? trunkR[1] : trunkR[0], roll));
        double r = Atlas.d(d, "radius", 2.5) * (0.85 + roll * 0.3);
        String crown = Atlas.s(d, "crown", "round");
        double[] tall = d.get("height") instanceof List<?> ? Atlas.pt(d.get("height")) : null;
        int total = tall != null ? (int) Math.round(lerp(tall[0], tall[1], roll)) : trunk + (int) Math.ceil(r);
        switch (crown) {
            case "snag" -> {
                for (int k = 0; k < trunk; k++) set(x, y + k, z, log, true);
                // a dead spar or two off the trunk
                int bx = roll < 0.5 ? 1 : -1;
                set(x + bx, y + trunk - 2, z, log, false); set(x + bx * 2, y + trunk - 1, z, log, false);
                if (roll > 0.3) { set(x, y + trunk - 3, z - 1, log, false); }
            }
            case "column", "cone" -> {
                for (int k = 0; k < total - 1; k++) set(x, y + k, z, log, true);
                for (int k = trunk; k <= total; k++) {
                    double f = (k - trunk) / (double) Math.max(1, total - trunk);
                    double rr = crown.equals("column") ? r * (1 - Math.pow(Math.abs(f - 0.35) / 0.75, 2) * 0.8) : r * (1 - f) + 0.6;
                    int R = (int) Math.ceil(rr);
                    for (int dx = -R; dx <= R; dx++) for (int dz = -R; dz <= R; dz++)
                        if (dx * dx + dz * dz <= rr * rr + 0.3) set(x + dx, y + k, z + dz, leaves, false);
                }
                set(x, y + total + 1, z, leaves, false);
            }
            case "fronds" -> {
                for (int k = 0; k < trunk; k++) set(x + (k > trunk / 2 && roll > 0.5 ? 1 : 0), y + k, z, log, true);
                int tx = x + (roll > 0.5 ? 1 : 0), ty = y + trunk;
                set(tx, ty, z, leaves, false);
                for (int dir = 0; dir < 8; dir++) {
                    double a = dir * Math.PI / 4 + roll;
                    for (int s = 1; s <= Math.round(r); s++) set(tx + (int) Math.round(Math.cos(a) * s), ty - (s > 2 ? 1 : 0), z + (int) Math.round(Math.sin(a) * s), leaves, false);
                }
            }
            case "flat" -> {
                for (int k = 0; k < trunk; k++) set(x, y + k, z, log, true);
                int R = (int) Math.ceil(r);
                for (int dx = -R; dx <= R; dx++) for (int dz = -R; dz <= R; dz++) {
                    double dd = dx * dx + dz * dz;
                    if (dd <= r * r) { set(x + dx, y + trunk, z + dz, leaves, false); if (dd <= r * r * 0.4) set(x + dx, y + trunk + 1, z + dz, leaves, false); }
                }
            }
            case "droop" -> {
                for (int k = 0; k < trunk; k++) set(x, y + k, z, log, true);
                int R = (int) Math.ceil(r), cy = y + trunk;
                for (int dx = -R; dx <= R; dx++) for (int dz = -R; dz <= R; dz++) {
                    double dd = Math.sqrt(dx * dx + dz * dz);
                    if (dd > r) continue;
                    for (int dy = -1; dy <= 1; dy++) if (dd <= r - Math.abs(dy) * 0.8) set(x + dx, cy + dy, z + dz, leaves, false);
                    // the willow's curtain: long strands hanging from the rim
                    if (dd > r - 1.2 && Noise.hash(atlas.seed, x + dx, z + dz, 4) < 0.55) {
                        int hang = 1 + (int) (Noise.hash(atlas.seed, x + dx, z + dz, 6) * 3);
                        for (int s = 1; s <= hang; s++) set(x + dx, cy - 1 - s, z + dz, leaves, false);
                    }
                }
            }
            default -> { // round, tall
                for (int k = 0; k < trunk; k++) set(x, y + k, z, log, true);
                double ry = crown.equals("tall") ? r * 1.5 : r * 0.9;
                int R = (int) Math.ceil(r), RY = (int) Math.ceil(ry), cy = y + trunk;
                for (int dx = -R; dx <= R; dx++) for (int dz = -R; dz <= R; dz++) for (int dy = -RY + 1; dy <= RY; dy++) {
                    double q = (dx * dx + dz * dz) / (r * r) + (dy * dy) / (ry * ry);
                    if (q <= 1 + (Noise.hash(atlas.seed, x + dx * 7, z + dz * 7, 20 + dy) - 0.5) * 0.35) set(x + dx, cy + dy, z + dz, leaves, false);
                }
            }
        }
    }

    // ------------------------------------------------------------------ 16. ore at the surface
    private void outcrops() {
        for (Map<String, Object> o : Atlas.list(atlas.map("outcrops"), "list")) {
            int[] r = atlas.cells(Atlas.arr(o, "rect"));
            String mineral = Atlas.s(o, "mineral", "stone");
            int want = Atlas.i(o, "count", 3), placed = 0;
            for (int tries = 0; tries < want * 60 && placed < want; tries++) {
                int x = r[0] + (int) (Noise.hash(atlas.seed, tries, Atlas.s(o, "id", "").hashCode(), 30) * (r[2] - r[0] + 1));
                int z = r[1] + (int) (Noise.hash(atlas.seed, tries, Atlas.s(o, "id", "").hashCode(), 31) * (r[3] - r[1] + 1));
                if (!in(x, z) || x < 2 || z < 2 || x >= S - 2 || z >= S - 2) continue;
                int i = idx(x, z);
                if (water[i] >= 0 || (flags[i] & (ROAD | BRIDGE | TREE | CAUSEWAY | BUILT | YARD)) != 0) continue;
                boolean level = true;
                for (int n : new int[]{i - S, i + S, i - 1, i + 1}) if (Math.abs(top[n] - top[i]) > 1 || water[n] >= 0 || (flags[n] & ROAD) != 0) level = false;
                if (!level) continue;
                boolean taken = false;
                for (int[] k : rocks) if (Math.abs(k[0] - x) < 3 && Math.abs(k[2] - z) < 3) taken = true;
                if (taken) continue;
                rocks.add(new int[]{x, top[i] + 1, z});
                rockMinerals.add(mineral);
                placed++;
            }
        }
    }

    // ------------------------------------------------------------------ questions
    /** The ground's top cell at a point, or the deck of a bridge there. */
    public int ground(int x, int z) {
        if (!in(x, z)) return sea;
        int i = idx(x, z);
        return deck[i] >= 0 ? deck[i] : top[i];
    }
    /** Where a body stands at a point: one above the ground (the water's surface where there is water deeper than wading). */
    public float stand(int x, int z) {
        int g = ground(Math.max(0, Math.min(S - 1, x)), Math.max(0, Math.min(S - 1, z)));
        return g + 1;
    }
}
