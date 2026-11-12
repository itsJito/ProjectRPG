package rpg.world.shadowveil;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * (0.4.0) ShadowVeil's design as data: {@code data/shadowveil.json}, transcribed from projectrpg.txt's WORLD BUILDING
 * (which has the final say), read once. A {@code shadowveil.json} beside the game overrides it whole, so the world can be
 * reworked and previewed without rebuilding the game. Nothing about the map's shape lives in code: the generator
 * ({@link Terrain}) reads every number from here.
 * <p>
 * Coordinates are the document's: [x, z] 1-based chunks of 16, x west to east, z south to north. The world's cells run
 * x west to east and z north to south (the map's south is +z, as on the devmap), so a chunk's north coordinate is
 * turned over when it becomes a cell: {@link #x(double)}, {@link #z(double)}.
 */
public final class Atlas {
    public final Map<String, Object> root;
    public final int size, height, sea, ceiling;
    public final long seed;

    private Atlas(Map<String, Object> root) {
        this.root = root;
        this.size = i(root, "size", 1072);
        this.height = i(root, "height", 200);
        this.sea = i(root, "sea", 30);
        this.ceiling = i(root, "ceiling", 99);
        this.seed = i(root, "seed", 400);
    }

    private static Atlas cached;

    /** The design (read once). */
    public static synchronized Atlas get() {
        if (cached != null) return cached;
        Map<String, Object> m = null;
        Path local = Path.of("shadowveil.json");
        try {
            if (Files.isRegularFile(local)) m = astratto.util.Json.parseObject(Files.readString(local));
        } catch (Exception broken) { System.err.println("[shadowveil] shadowveil.json beside the game could not be read (" + broken.getMessage() + "); the built-in design is used"); }
        if (m == null) try (InputStream in = Atlas.class.getResourceAsStream("/data/shadowveil.json")) {
            if (in == null) throw new IllegalStateException("data/shadowveil.json is missing from the game");
            m = astratto.util.Json.parseObject(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (java.io.IOException e) { throw new IllegalStateException("data/shadowveil.json could not be read: " + e.getMessage(), e); }
        // (0.4.1) the design's other parts (the architecture), read the same way and joined to it
        for (Object inc : arr(m, "include")) {
            String name = String.valueOf(inc);
            Map<String, Object> part = null;
            try {
                Path beside = Path.of(name);
                if (Files.isRegularFile(beside)) part = astratto.util.Json.parseObject(Files.readString(beside));
            } catch (Exception broken) { System.err.println("[shadowveil] " + name + " beside the game could not be read (" + broken.getMessage() + "); the built-in one is used"); }
            if (part == null) try (InputStream in = Atlas.class.getResourceAsStream("/data/" + name)) {
                if (in == null) throw new IllegalStateException("data/" + name + " is missing from the game");
                part = astratto.util.Json.parseObject(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            } catch (java.io.IOException e) { throw new IllegalStateException("data/" + name + " could not be read: " + e.getMessage(), e); }
            m.putAll(part);
        }
        cached = new Atlas(m);
        return cached;
    }

    // ------------------------------------------------------------------ coordinates
    /** A chunk x (1-based, fractions allowed, a whole number the chunk's middle) to a cell x. */
    public double x(double chunkX) { return (chunkX - 0.5) * 16.0; }
    /** A chunk z (south to north) to a cell z (north to south). */
    public double z(double chunkZ) { return size - (chunkZ - 0.5) * 16.0; }
    /** A range [x0, x1, z0, z1] of chunks to its cells {x0, z0, x1, z1} (inclusive, cell z north to south). */
    public int[] cells(List<?> rect) {
        int x0 = n(rect.get(0)), x1 = n(rect.get(1)), z0 = n(rect.get(2)), z1 = n(rect.get(3));
        return new int[]{(x0 - 1) * 16, size - z1 * 16, x1 * 16 - 1, size - 1 - (z0 - 1) * 16};
    }

    // ------------------------------------------------------------------ reading
    @SuppressWarnings("unchecked")
    public Map<String, Object> map(String key) { Object o = root.get(key); return o instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of(); }
    @SuppressWarnings("unchecked")
    public static Map<String, Object> map(Map<String, Object> m, String key) { Object o = m.get(key); return o instanceof Map<?, ?> v ? (Map<String, Object>) v : Map.of(); }
    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> list(Map<String, Object> m, String key) {
        Object o = m.get(key);
        List<Map<String, Object>> out = new ArrayList<>();
        if (o instanceof List<?> l) for (Object e : l) if (e instanceof Map<?, ?> v) out.add((Map<String, Object>) v);
        return out;
    }
    public List<Map<String, Object>> list(String key) { return list(root, key); }
    public static List<?> arr(Map<String, Object> m, String key) { return m.get(key) instanceof List<?> l ? l : List.of(); }
    public static double d(Map<String, Object> m, String key, double fallback) { return m.get(key) instanceof Number v ? v.doubleValue() : fallback; }
    public static int i(Map<String, Object> m, String key, int fallback) { return m.get(key) instanceof Number v ? v.intValue() : fallback; }
    public static String s(Map<String, Object> m, String key, String fallback) { return m.get(key) instanceof String v ? v : fallback; }
    public static int n(Object o) { return o instanceof Number v ? v.intValue() : 0; }
    public static double dn(Object o) { return o instanceof Number v ? v.doubleValue() : 0; }
    /** A point [x, z] (or [x, z, y, ...]) as numbers. */
    public static double[] pt(Object o) {
        if (!(o instanceof List<?> l)) return new double[]{0, 0};
        double[] p = new double[l.size()];
        for (int k = 0; k < p.length; k++) p[k] = dn(l.get(k));
        return p;
    }

    /** Every named place: [x, z] chunk (the 'places' table and the 'extraPlaces' beside it). */
    public Map<String, double[]> places() {
        Map<String, double[]> out = new LinkedHashMap<>();
        for (String key : new String[]{"places", "extraPlaces"})
            for (var e : map(key).entrySet()) if (e.getValue() instanceof List<?>) out.put(e.getKey(), pt(e.getValue()));
        return out;
    }
}
