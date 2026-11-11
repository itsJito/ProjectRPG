package rpg;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * (0.3.9, gamefixes: nothing should be hardcoded) The game's numbers as data: {@code data/tuning.json} in the game's
 * resources, overridden by a {@code tuning.json} beside the game if there is one (so a number can be changed without
 * rebuilding). Read by dotted path - {@code "companions.fee.BANDIT"} - with the value the code was written against as
 * the fallback, so a missing or broken file never stops the game; it only means the defaults.
 */
public final class Tuning {
    private Tuning() {}

    private static Map<String, Object> root;

    private static synchronized Map<String, Object> root() {
        if (root != null) return root;
        Map<String, Object> m = null;
        try (InputStream in = Tuning.class.getResourceAsStream("/data/tuning.json")) {
            if (in != null) m = astratto.util.Json.parseObject(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (Exception broken) { System.err.println("[tuning] the built-in tuning could not be read: " + broken.getMessage()); }
        Path local = Path.of("tuning.json");
        if (Files.isRegularFile(local)) try {
            Map<String, Object> over = astratto.util.Json.parseObject(Files.readString(local));
            if (m == null) m = over; else merge(m, over);
        } catch (Exception broken) { System.err.println("[tuning] tuning.json beside the game could not be read: " + broken.getMessage()); }
        root = m == null ? Map.of() : m;
        return root;
    }

    @SuppressWarnings("unchecked")
    private static void merge(Map<String, Object> into, Map<String, Object> over) {
        for (var e : over.entrySet()) {
            if (e.getValue() instanceof Map<?, ?> sub && into.get(e.getKey()) instanceof Map<?, ?> mine) merge((Map<String, Object>) mine, (Map<String, Object>) sub);
            else into.put(e.getKey(), e.getValue());
        }
    }

    private static Object at(String path) {
        Object o = root();
        for (String part : path.split("\\.")) {
            if (!(o instanceof Map<?, ?> m)) return null;
            o = m.get(part);
        }
        return o;
    }

    public static float f(String path, float fallback) { return at(path) instanceof Number n ? n.floatValue() : fallback; }
    public static int i(String path, int fallback) { return at(path) instanceof Number n ? n.intValue() : fallback; }
    public static boolean b(String path, boolean fallback) { return at(path) instanceof Boolean v ? v : fallback; }
    public static String s(String path, String fallback) { return at(path) instanceof String v ? v : fallback; }

    /** Forgets what was read (the next look reads the files again). */
    public static synchronized void reload() { root = null; }
}
