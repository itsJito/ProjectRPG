package rpg.world.shadowveil;

import astratto.bridge.AstrattoNative;
import astratto.bridge.CommandBuffer;
import astratto.bridge.NativeEngine;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import rpg.world.Blocks;
import rpg.world.DevMap;
import rpg.world.WorldMap;

/**
 * (0.4.0, gamefixes: ShadowVeil) The permanent world of Ashenwrath's playable square, 1,072 x 1,072 cells and 200 high
 * (the ground kept to Y99 and below), grown from {@link Atlas} by {@link Terrain}. This is the map the game launches on;
 * the devmap stays beside it, whole, for regression testing (WorldMap).
 * <p>
 * 0.4.0 is terrain only - no towns or buildings yet. What the shared systems need of the map is answered here from the
 * design: whose law holds at a point (the settlements' cores; the rest is the wilds), the watch's round in each town,
 * where everyone the devmap had lives now ({@link #home}), where the animals and monsters range.
 */
public final class ShadowVeil {
    private ShadowVeil() {}

    /** The columns as grown (kept for the game's questions about the ground after the cells went to the engine). */
    private static Terrain terrain;
    private static final List<Realm> REALMS = new ArrayList<>();
    public record Realm(String name, int[][] boxes, float cx, float cz) {
        boolean contains(double x, double z) { for (int[] b : boxes) if (x >= b[0] && x <= b[2] + 1 && z >= b[1] && z <= b[3] + 1) return true; return false; }
    }

    public static Terrain terrain() { return terrain; }

    /** Grows ShadowVeil into a new engine world (lit, its pristine copy kept for saves), makes it the active map, and returns its handle. */
    public static long build(NativeEngine e) {
        Atlas a = Atlas.get();
        if (!e.blockTable(Blocks.engineDefs(true))) throw new IllegalStateException("block table refused: " + e.lastError());
        long t0 = System.nanoTime();
        Terrain t = Terrain.generate(a, true);
        long t1 = System.nanoTime();
        long world = e.createWorld(t.S, t.H, t.S, Blocks.STONE, t.cells, null, AstrattoNative.WORLD_LIGHT);
        t.cells = null;     // the engine has its own copy now (230 MB not to keep twice)
        if (world == 0) throw new IllegalStateException("ShadowVeil was refused by the engine: " + e.lastError());
        CommandBuffer b = new CommandBuffer(1 << 12);
        b.pristineSave();
        if (!e.edit(world, b)) throw new IllegalStateException("ShadowVeil's pristine copy was refused: " + e.lastError());
        b.free();
        terrain = t;
        realms(a);
        Map<String, Object> names = a.map("realms");
        double[] spawn = Atlas.pt(a.root.get("spawn"));
        float sx = (float) a.x(spawn[0]), sz = (float) a.z(spawn[1]), yaw = (float) Math.PI;
        // (0.4.1, gamefixes: the spawn inside Loxford Castle, facing the way out) where the architecture says a new
        // arrival wakes - in the keep's hall - turned toward the keep's door and the gate beyond it
        if (t.arch != null && t.arch.spawn != null) {
            sx = t.arch.spawn[0]; sz = t.arch.spawn[1];
            yaw = (float) Math.atan2(-(t.arch.spawnLook[0] - sx), -(t.arch.spawnLook[1] - sz));
        }
        WorldMap.activate(WorldMap.Kind.SHADOWVEIL, "shadowveil", "ShadowVeil", t.S, t.H, t.S, a.sea, sx, sz, yaw,
                Atlas.s(names, "city", "Loxford"), Atlas.s(names, "town", "Harlette"), Atlas.s(names, "empire", "Severia"));
        System.out.printf("[Load] ShadowVeil grown in %.2fs (%s), made and lit in %.2fs%n", (t1 - t0) / 1e9, t.timings, (System.nanoTime() - t1) / 1e9);
        return world;
    }

    /** Takes a grown terrain as the map's without an engine (surveys and tests of the ground alone). */
    public static void prime(Terrain t) { terrain = t; realms(t.atlas); }

    private static void realms(Atlas a) {
        REALMS.clear();
        for (Map<String, Object> r : Atlas.list(a.map("realms"), "list")) {
            List<int[]> boxes = new ArrayList<>();
            for (Object o : Atlas.arr(r, "rects")) boxes.add(a.cells((List<?>) o));
            double[] c = Atlas.pt(r.get("centre"));
            REALMS.add(new Realm(Atlas.s(r, "name", "?"), boxes.toArray(new int[0][]), (float) a.x(c[0]), (float) a.z(c[1])));
        }
    }

    // ------------------------------------------------------------------ the law
    /** Whose law holds at (x, z): a settlement's core, or the wilds. */
    public static String jurisdiction(double x, double z) {
        for (Realm r : REALMS) if (r.contains(x, z)) return r.name;
        return DevMap.WILDS;
    }
    /** Whose law holds at a cell, read straight from the design (for the generator, before the realms are made). */
    static String realmAt(Atlas a, int x, int z) {
        for (Map<String, Object> r : Atlas.list(a.map("realms"), "list"))
            for (Object o : Atlas.arr(r, "rects")) { int[] c = a.cells((List<?>) o); if (x >= c[0] && x <= c[2] && z >= c[1] && z <= c[3]) return Atlas.s(r, "name", "?"); }
        return DevMap.WILDS;
    }
    public static List<Realm> realms() { return REALMS; }
    public static Realm realm(String name) { for (Realm r : REALMS) if (r.name.equals(name)) return r; return null; }

    /** The watch's round in a town: five points about its centre, on dry ground (the town's streets come with its buildings). */
    public static float[][] patrol(String jurisdiction) {
        // (0.4.1) the town's own round through its gates, squares and streets, where the architecture gives one
        if (terrain != null && terrain.arch != null && terrain.arch.patrols.containsKey(jurisdiction)) {
            List<float[]> round = terrain.arch.patrols.get(jurisdiction);
            float[][] out = new float[round.size()][];
            for (int k = 0; k < out.length; k++) out[k] = dry(round.get(k)[0], round.get(k)[1]);
            return out;
        }
        Realm r = realm(jurisdiction);
        if (r == null) r = realm(DevMap.ALPHA);
        if (r == null) return new float[][]{{WorldMap.active().spawnX, WorldMap.active().spawnZ}};
        float[][] out = new float[5][];
        for (int k = 0; k < 5; k++) {
            double ang = k * Math.PI * 2 / 5 + 0.4;
            out[k] = dry(r.cx + (float) Math.cos(ang) * 26, r.cz + (float) Math.sin(ang) * 26);
        }
        return out;
    }

    // ------------------------------------------------------------------ places
    /** A named place's middle in cells {x, z} (null: no such place). */
    public static float[] place(String key) {
        Atlas a = Atlas.get();
        double[] p = a.places().get(key);
        if (p == null) return null;
        return dry((float) a.x(p[0]), (float) a.z(p[1]));
    }
    /** The dry, standable cell nearest a point (a spiral out to 40 cells), {x + 0.5, z + 0.5}. */
    public static float[] dry(float x, float z) {
        Terrain t = terrain;
        if (t == null) return new float[]{x, z};
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        for (int r = 0; r <= 40; r++) for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
            if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
            int cx = bx + dx, cz = bz + dz;
            if (!t.in(cx, cz) || cx < 3 || cz < 3 || cx >= t.S - 3 || cz >= t.S - 3) continue;
            int i = t.idx(cx, cz);
            if (t.water[i] >= 0 && t.deck[i] < 0) continue;
            if ((t.flags[i] & (Terrain.TREE | Terrain.CLIFF | Terrain.BUILT)) != 0) continue;
            return new float[]{cx + 0.5f, cz + 0.5f};
        }
        return new float[]{x, z};
    }
    /**
     * (0.4.1) ShadowVeil's gardens as the farming system's fields: each of the data's 'farms' (a grower by their rpg.Ids
     * name, the garden structure, its rows' crops), its rows across it every other cell, its gate where the garden's is.
     */
    public static rpg.world.Farming.Field[] farms() {
        List<rpg.world.Farming.Field> out = new ArrayList<>();
        java.util.Set<String> farmed = new java.util.HashSet<>();
        Terrain t = terrain;
        if (t == null || t.arch == null) return new rpg.world.Farming.Field[0];
        for (Map<String, Object> farm : Atlas.list(Atlas.get().map("architecture"), "farms")) {
            var s = t.arch.byId(Atlas.s(farm, "structure", ""));
            if (s == null) continue;
            long owner = 0;
            try { owner = rpg.Ids.class.getField(Atlas.s(farm, "owner", "")).getLong(null); } catch (ReflectiveOperationException e) { continue; }
            List<Integer> rows = new ArrayList<>();
            for (int z = s.z0 + 2; z <= s.z1 - 2; z += 2) rows.add(z);
            if (rows.isEmpty()) continue;
            List<?> crops = Atlas.arr(farm, "crops");
            rpg.world.Farming.Crop[][] rc = new rpg.world.Farming.Crop[rows.size()][];
            for (int k = 0; k < rc.length; k++) {
                List<?> row = crops.isEmpty() ? List.of("CARROT") : (List<?>) crops.get(k % crops.size());
                rc[k] = new rpg.world.Farming.Crop[row.size()];
                for (int j = 0; j < row.size(); j++) rc[k][j] = rpg.world.Farming.Crop.valueOf(String.valueOf(row.get(j)));
            }
            int[] rowsA = rows.stream().mapToInt(Integer::intValue).toArray();
            int gx = (s.x0 + s.x1) / 2;
            int[][] gate = {{gx, s.z0}, {gx + 1, s.z0}};
            out.add(new rpg.world.Farming.Field(s.name, jurisdiction(s.cx(), s.cz()), owner, s.x0, s.z0, s.x1, s.z1, rowsA, -1, gate, rc, s.floor + 1));
            farmed.add(s.id);
        }
        // (0.4.3, gamefixes: farms did not work or have their crops) every other field and garden, farmed too
        Map<String, Object> fd = Atlas.map(Atlas.map(Atlas.get().root, "census"), "fields");
        List<?> templates = Atlas.arr(fd, "templates"), unfarmed = Atlas.arr(fd, "unfarmed");
        Map<String, Object> cropsBy = Atlas.map(fd, "crops");
        for (var s : t.arch.structures) {
            if (farmed.contains(s.id) || !templates.contains(s.template) || unfarmed.contains(s.id) || s.x1 - s.x0 < 4 || s.z1 - s.z0 < 4) continue;
            String kingdom = kingdomOf(s);
            long owner = farmerFor(s, kingdom, Atlas.d(fd, "reach", 240));
            List<Integer> rows = new ArrayList<>();
            for (int z = s.z0 + 2; z <= s.z1 - 2; z += 2) rows.add(z);
            if (rows.isEmpty()) continue;
            List<?> crops = Atlas.arr(cropsBy, kingdom);
            if (crops.isEmpty()) crops = Atlas.arr(cropsBy, "_default");
            rpg.world.Farming.Crop[][] rc = new rpg.world.Farming.Crop[rows.size()][];
            for (int k = 0; k < rc.length; k++) {
                List<?> row = crops.isEmpty() ? List.of("CARROT") : (List<?>) crops.get(k % crops.size());
                rc[k] = new rpg.world.Farming.Crop[row.size()];
                for (int j = 0; j < row.size(); j++) rc[k][j] = rpg.world.Farming.Crop.valueOf(String.valueOf(row.get(j)));
            }
            int[] rowsA = rows.stream().mapToInt(Integer::intValue).toArray();
            int gx = (s.x0 + s.x1) / 2;
            out.add(new rpg.world.Farming.Field(s.name, kingdom, owner, s.x0, s.z0, s.x1, s.z1, rowsA, -1, new int[][]{{gx, s.z0}, {gx + 1, s.z0}}, rc, s.floor + 1));
        }
        return out.toArray(new rpg.world.Farming.Field[0]);
    }

    /** (0.4.3) The kingdom a structure belongs to: its settlement's, else whose law holds where it stands. */
    static String kingdomOf(Architecture.Structure s) {
        for (Realm r : REALMS) if (s.settlement != null && r.name.equalsIgnoreCase(s.settlement)) return r.name;
        return jurisdiction(s.cx(), s.cz());
    }
    /**
     * (0.4.3) Who keeps a field: the census's crop farmer set to work there, else the nearest crop farmer of its kingdom
     * within reach (their Ids constant's id, or their own on ShadowVeil); 0 - nobody's, open to anyone.
     */
    static long farmerFor(Architecture.Structure s, String kingdom, double reach) {
        List<Map<String, Object>> people = Atlas.list(Atlas.map(Atlas.get().root, "census"), "people");
        long best = 0; double bd = reach;
        for (int k = 0; k < people.size(); k++) {
            Map<String, Object> p = people.get(k);
            if (!"CROP_FARMER".equals(p.get("profession"))) continue;
            long id = Census.idOf(p, k);
            if (s.id.equals(p.get("work"))) return id;
            if (!kingdom.equals(p.get("kingdom"))) continue;
            var w = p.get("work") instanceof String ws && terrain != null && terrain.arch != null ? terrain.arch.byId(ws) : null;
            if (w == null) continue;
            double d = Math.hypot(w.cx() - s.cx(), w.cz() - s.cz());
            if (d < bd) { bd = d; best = id; }
        }
        return best;
    }

    /** The open water nearest a point that has a dry bank beside it: {x, z, water cell y} (null: none within 48 cells). */
    public static int[] fishingSpot(float x, float z) {
        Terrain t = terrain;
        if (t == null) return null;
        int bx = (int) x, bz = (int) z;
        for (int r = 0; r <= 48; r++) for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
            if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
            int cx = bx + dx, cz = bz + dz;
            if (cx < 2 || cz < 2 || cx >= t.S - 2 || cz >= t.S - 2) continue;
            int i = t.idx(cx, cz);
            if (t.water[i] < 0 || t.deck[i] >= 0 || t.water[i] - t.top[i] < 1) continue;
            boolean bank = false;
            for (int n : new int[]{i - t.S, i + t.S, i - 1, i + 1}) if (t.water[n] < 0 && t.deck[n] < 0) bank = true;
            if (bank) return new int[]{cx, cz, t.water[i]};
        }
        return null;
    }

    /** Where a body stands at (x, z): one above the ground (a bridge's deck where there is one). */
    public static float standY(float x, float z) {
        Terrain t = terrain;
        return t == null ? WorldMap.active().floor : t.stand((int) Math.floor(x), (int) Math.floor(z));
    }

    // ------------------------------------------------------------------ who lives where
    /** The devmap town an id-less rule calls by its role in the law: city, town, empire, wilds. */
    private static String townRole(String jurisdiction) {
        if (jurisdiction == null) return "wilds";
        if (jurisdiction.equals("DevCity") || jurisdiction.equals(DevMap.ALPHA)) return "city";
        if (jurisdiction.equals("DevTown") || jurisdiction.equals(DevMap.BETA)) return "town";
        if (jurisdiction.equals("DevPalace") || jurisdiction.equals(DevMap.PALACE)) return "empire";
        return "wilds";
    }

    /**
     * Where a person the devmap had lives on ShadowVeil: the transfer rules (data) by their id's name, role, profession or
     * old town; a place, scattered a little about it so a household is not one spot. Returns {x, z} or null.
     */
    public static float[] home(String idName, String role, String profession, String oldJurisdiction, String name, long seed) {
        Atlas a = Atlas.get();
        Map<String, Object> transfer = a.map("transfer");
        String town = townRole(oldJurisdiction);
        String to = null;
        for (Map<String, Object> rule : Atlas.list(transfer, "rules")) {
            if (rule.get("id") instanceof String v && !v.equals(idName)) continue;
            if (rule.get("role") instanceof String v && !v.equals(role)) continue;
            if (rule.get("profession") instanceof String v && !v.equals(profession)) continue;
            if (rule.get("jurisdiction") instanceof String v && !v.equals(town)) continue;
            if (rule.get("name") instanceof String v && !v.equals(name)) continue;
            to = Atlas.s(rule, "to", null);
            break;
        }
        if (to == null) return null;
        float[] p = place(to);
        if (p == null) return null;
        double scatter = Atlas.d(transfer, "scatter", 9);
        double ang = Noise.hash(a.seed, (int) seed, 1, 77) * Math.PI * 2, r = 2 + Noise.hash(a.seed, (int) seed, 2, 78) * scatter;
        return dry(p[0] + (float) (Math.cos(ang) * r), p[1] + (float) (Math.sin(ang) * r));
    }

    // ------------------------------------------------------------------ the air and the water
    /**
     * Each realm's grade and the wilds' (the design's tints, from projectrpg.txt's Visual Tint lines), as the time
     * system's regions: the wilds over the whole square, each realm blended in over its cores.
     */
    public static void atmosphere(astratto.weather.TimeSystem time) {
        Atlas a = Atlas.get();
        Map<String, Object> realms = a.map("realms");
        float blend = (float) Atlas.d(realms, "blend", 48);
        time.regions.add(new astratto.weather.TimeSystem.Region("Wilds", -64, -64, a.world + 64, a.world + 64, 0, profile(Atlas.map(realms, "wilds").get("tint"))));
        for (Map<String, Object> r : Atlas.list(realms, "list")) {
            astratto.weather.TimeSystem.Profile p = profile(r.get("tint"));
            for (Object o : Atlas.arr(r, "rects")) {
                int[] c = a.cells((List<?>) o);
                time.regions.add(new astratto.weather.TimeSystem.Region(Atlas.s(r, "name", "?"), c[0], c[1], c[2] + 1, c[3] + 1, blend, p));
            }
        }
    }
    /**
     * (0.4.4, gamefixes: fog should have a different colour/tint depending on which region of the world you're in) the fog
     * colour at a point, as a multiplier on the hour's own: the wilds' by the biomes round it (sampled over a few dozen
     * cells, so it changes gradually as the land does), each realm's over its bounds, blended in toward its edge as the
     * realms' tints are. {r, g, b}.
     */
    public static float[] fogTint(float x, float z) {
        Atlas a = Atlas.get();
        Terrain t = terrain;
        Map<String, Object> realms = a.map("realms");
        Map<String, Object> biomeFog = Atlas.map(Atlas.map(realms, "wilds"), "fogBiomes");
        float r = 0, g = 0, b = 0; int n = 0;
        if (t != null) for (int dx = -24; dx <= 24; dx += 12) for (int dz = -24; dz <= 24; dz += 12) {
            int sx = (int) Math.max(0, Math.min(t.S - 1, x + dx)), sz = (int) Math.max(0, Math.min(t.S - 1, z + dz));
            double[] c = Atlas.pt(biomeFog.get(t.biomeAt(sx, sz)));
            if (c.length < 3) c = new double[]{1, 1, 1};
            r += c[0]; g += c[1]; b += c[2]; n++;
        }
        float[] out = n == 0 ? new float[]{1, 1, 1} : new float[]{r / n, g / n, b / n};
        float blend = (float) Atlas.d(realms, "blend", 48);
        for (Map<String, Object> rl : Atlas.list(realms, "list")) {
            double[] c = Atlas.pt(rl.get("fog"));
            if (c.length < 3) continue;
            float w = 0;
            for (Object o : Atlas.arr(rl, "rects")) {
                int[] q = a.cells((List<?>) o);
                float inX = Math.min(x - q[0], q[2] + 1 - x), inZ = Math.min(z - q[1], q[3] + 1 - z), in = Math.min(inX, inZ);
                w = Math.max(w, blend <= 0 ? (in >= 0 ? 1 : 0) : Math.max(0, Math.min(1, (in + blend * 0.5f) / blend)));
            }
            if (w <= 0) continue;
            for (int k = 0; k < 3; k++) out[k] += ((float) c[k] - out[k]) * w;
        }
        return out;
    }

    /**
     * (0.4.0, gamefixes: volumetric ray marching) the design's ground mists as fog volumes for the volumetric march:
     * banks over the lakes, in the valleys and hollows (set on the ground or the water where each falls), drawn near
     * the camera and thickest at dawn, dusk, by night and in the wet.
     */
    public static void mists(astratto.weather.TimeSystem time) {
        Atlas a = Atlas.get();
        for (Map<String, Object> m : Atlas.list(a.map("mists"), "list")) {
            double[] at = Atlas.pt(m.get("at")), col = Atlas.pt(m.get("colour"));
            double spread = Atlas.d(m, "spread", 2) * 16, size = Atlas.d(m, "size", 8), lift = Atlas.d(m, "lift", 1);
            int count = Atlas.i(m, "count", 6);
            String id = Atlas.s(m, "id", "mist");
            for (int k = 0; k < count; k++) {
                double ang = Noise.hash(a.seed, k, id.hashCode(), 61) * Math.PI * 2, r = Math.sqrt(Noise.hash(a.seed, k, id.hashCode(), 62)) * spread;
                float x = (float) (a.x(at[0]) + Math.cos(ang) * r), z = (float) (a.z(at[1]) + Math.sin(ang) * r);
                Terrain t = terrain;
                int xi = (int) Math.max(0, Math.min(a.world - 1, x)), zi = (int) Math.max(0, Math.min(a.world - 1, z));
                float base = t == null ? a.sea : Math.max(t.top[t.idx(xi, zi)], t.water[t.idx(xi, zi)]) + 1;
                float rad = (float) (size * (0.7 + 0.6 * Noise.hash(a.seed, k, id.hashCode(), 63)));
                time.fogVolumes.add(new astratto.weather.TimeSystem.Fog(x, base + (float) lift, z, rad, (float) Atlas.d(m, "density", 0.04),
                        col.length > 2 ? (float) col[0] : 0.6f, col.length > 2 ? (float) col[1] : 0.65f, col.length > 2 ? (float) col[2] : 0.7f));
            }
        }
    }

    private static astratto.weather.TimeSystem.Profile profile(Object tint) {
        double[] t = Atlas.pt(tint);
        if (t.length < 7) return astratto.weather.TimeSystem.Profile.NEUTRAL;
        return new astratto.weather.TimeSystem.Profile((float) t[0], (float) t[1], (float) t[2], (float) t[3], (float) t[4], (float) t[5], (float) t[6]);
    }

    /**
     * (0.4.5, gamefixes: flowing water set up for all water, by itself) the water's motion (Fluids' field) grown from the
     * terrain's own water - every river, brook, lake and outflow running toward the sea (rpg.world.FlowField) - with the
     * aqueducts' channels laid over it. The field is kept with the terrain: grown once per world.
     */
    public static void currents(rpg.world.Fluids f) {
        if (terrain == null) return;
        if (field == null || fieldOf != terrain) {
            Terrain t = terrain;
            int n = t.S * t.S;
            boolean[] sea = new boolean[n], river = new boolean[n];
            for (int i = 0; i < n; i++) { sea[i] = (t.flags[i] & Terrain.SEA) != 0; river[i] = (t.flags[i] & (Terrain.RIVER | Terrain.FALLS)) != 0; }
            long t0 = System.nanoTime();
            // the falls: each drop's lower column and the first water upstream of it, joined
            List<int[]> links = new ArrayList<>();
            for (int[] fl : t.falls) {
                int lx = fl[0], lz = fl[2], dx = fl[4], dz = fl[5];
                if (!t.in(lx, lz) || t.water[t.idx(lx, lz)] < 0 || dx == 0 && dz == 0) continue;
                for (int k = 1; k <= 4; k++) {
                    int ux = lx - dx * k, uz = lz - dz * k;
                    if (!t.in(ux, uz)) break;
                    int u = t.idx(ux, uz);
                    if (t.water[u] > t.water[t.idx(lx, lz)]) { links.add(new int[]{u, t.idx(lx, lz)}); break; }
                }
            }
            field = rpg.world.FlowField.grow(t.S, t.water, sea, river, links.toArray(new int[0][]));
            if (t.arch != null) {
                for (float[] c : t.arch.channels) f.current((int) c[0], (int) c[1], (int) c[2], (int) c[3], c[4], c[5], rpg.Tuning.f("water.aqueductSpeed", 1.1f));
                field.overlay(f.currents());
            }
            fieldOf = t;
            System.out.printf("[water] flow field grown in %.0f ms: %d columns moving%n", (System.nanoTime() - t0) / 1e6, field.moving);
        }
        f.field = field;
    }
    private static rpg.world.FlowField field;
    private static Terrain fieldOf;

    /**
     * (0.4.3, gamefixes: 'goto' still took the devmap's places) every place a developer can be sent to on ShadowVeil, by
     * name: the start, each kingdom (its heart, on dry open ground), every named place of the design and each cave or mine
     * mouth the architecture raised. {x, z} in cells, in the order the console lists them.
     */
    public static Map<String, float[]> destinations() {
        Map<String, float[]> out = new LinkedHashMap<>();
        WorldMap m = WorldMap.active();
        out.put("start", new float[]{m.spawnX, m.spawnZ});
        for (Realm r : REALMS) out.put(r.name.toLowerCase(java.util.Locale.ROOT), dry(r.cx, r.cz));
        for (String key : Atlas.get().places().keySet()) { if (key.startsWith("_")) continue; float[] p = place(key); if (p != null) out.putIfAbsent(key, p); }
        if (terrain != null && terrain.arch != null) for (Architecture.Structure s : terrain.arch.structures) {
            float[] mouth = s.points.get("mouth");
            if (mouth != null) out.putIfAbsent(s.id.toLowerCase(java.util.Locale.ROOT), dry(mouth[0], mouth[1]));
        }
        return out;
    }

    /** The named places, in cells (the map screen's labels). */
    public static Map<String, float[]> labels() {
        Map<String, float[]> out = new LinkedHashMap<>();
        Atlas a = Atlas.get();
        for (Realm r : REALMS) out.put(r.name.toUpperCase(java.util.Locale.ROOT), new float[]{r.cx, r.cz});
        for (Map<String, Object> l : a.list("lakes")) {
            String n = Atlas.s(l, "name", null);
            if (n == null || out.containsKey(n.toUpperCase(java.util.Locale.ROOT))) continue;
            double[] p = Atlas.pt(l.get("at"));
            out.put(n.toUpperCase(java.util.Locale.ROOT), new float[]{(float) a.x(p[0]), (float) a.z(p[1])});
        }
        String[][] named = {{"ASHMAW", "ashmaw"}, {"DEAD WOOD", "dead_wood_lodge"}, {"GALLOWS HEATH", "gallows_heath"}, {"HOLLOW CRAGS", "hollow_crags"},
                {"MURK GORGE", "murk_gorge"}, {"WAYSTATION", "waystation"}, {"SMUGGLERS' COVE", "smugglers_cove"}, {"GLEAM FALLS", "gleam_falls"},
                {"MILLWOOD", "millwood_lodge"}, {"MARCHES CROSSROADS", "marches_crossroads"}, {"PILGRIM'S STAIR", "pilgrims_stair"}};
        for (String[] n : named) { double[] p = a.places().get(n[1]); if (p != null) out.put(n[0], new float[]{(float) a.x(p[0]), (float) a.z(p[1])}); }
        return out;
    }
}
