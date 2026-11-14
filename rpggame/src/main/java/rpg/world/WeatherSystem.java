package rpg.world;

import astratto.weather.Weather;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import rpg.Rpg;

/**
 * (0.4.6, gamefixes: rework the entire weather system) The weather of the world, region by region, from
 * {@code data/weather.json}.
 * <p>
 * Every climate (temperate, highland, desert, coastal, marsh - which rules where is the data's: the realm first, else the
 * wild region) runs its own cycle of weathers, all the time, wherever the player is: each weather lasts its own span of
 * game hours and always gives way on the hour, as the people's schedules turn over; what comes next is drawn from the
 * climate's own likelihoods, leaning by what came before (rain gives way to a clearing, clear skies cloud over before
 * they rain, a thunderstorm comes out of dark cloud or distant thunder), with fog likelier at dawn and thunder in the
 * afternoon. Some weathers happen only after others (a clearing only after rain or snow), and some never of themselves
 * (a hurricane, a whiteout: the console's, or a story's, to bring). The weather over the player is their climate's: walk
 * from the lowlands into the highlands and the sky turns over to theirs as it would on the road. On top of the weather:
 * showers come and go in bursts, the cloud cover drifts, the wind gusts, and the air's temperature follows the climate,
 * the hour and the weather.
 */
public final class WeatherSystem {
    /** A kind of weather (the data's preset). */
    public record Preset(String name, String group, float rain, float snow, float hail, float sand, float fog, float wind, float cloud, float storm,
                         float drift, float dark, float[] tint, float temp, int minHours, int maxHours, boolean natural, List<String> after, float pulse) {}
    /** A climate's cycle as it stands: the weather now, and when (game minutes) it gives way. */
    public static final class Cycle {
        public final String climate;
        public String now;
        public double until;
        public long draws;
        Cycle(String climate) { this.climate = climate; }
    }

    private final Rpg g;
    private final Map<String, Object> data;
    public final Map<String, Preset> presets = new LinkedHashMap<>();
    public final Map<String, Cycle> cycles = new LinkedHashMap<>();
    private final Map<String, Object> climates, realms, regions, groups;
    private final String fallback;
    /** The climate over the player now, and the weather last asked of the engine (to tell another hand's from ours). */
    private String here = "", asked = "";
    private double nextTouch;
    private float temperature = Float.NaN;

    public WeatherSystem(Rpg g) {
        this.g = g;
        data = read();
        Map<String, Object> ps = map(data, "presets");
        for (var e : ps.entrySet()) {
            Map<String, Object> p = asMap(e.getValue());
            List<?> h = list(p, "hours"), tint = list(p, "tint");
            List<String> after = new ArrayList<>();
            for (Object o : list(p, "after")) after.add(String.valueOf(o));
            presets.put(e.getKey(), new Preset(e.getKey(), str(p, "group", "clear"), f(p, "rain"), f(p, "snow"), f(p, "hail"), f(p, "sand"), f(p, "fog"), f(p, "wind", 0.18f),
                    f(p, "cloud"), f(p, "storm"), f(p, "drift"), f(p, "dark"),
                    tint.size() == 3 ? new float[]{num(tint.get(0)), num(tint.get(1)), num(tint.get(2))} : new float[]{1, 1, 1}, f(p, "temp"),
                    h.size() > 0 ? (int) num(h.get(0)) : 2, h.size() > 1 ? (int) num(h.get(1)) : 4, !Boolean.FALSE.equals(p.get("natural")), after, f(p, "pulse")));
        }
        climates = map(data, "climates"); realms = map(data, "realms"); regions = map(data, "regions"); groups = map(data, "groups");
        fallback = str(data, "default", "temperate");
        for (String c : climates.keySet()) cycles.put(c, new Cycle(c));
        g.timeSystem.autoWeather = false;
        g.timeSystem.weather.transition = f(data, "transition", 45f);
    }

    // ------------------------------------------------------------------ the cycle
    /** Every climate's weather moved on to the clock's minute (a long sleep turns them all over as often as the hours ask). */
    public void update() {
        double now = g.state.minutes;
        for (Cycle c : cycles.values()) {
            int guard = 0;
            while ((c.now == null || now >= c.until) && guard++ < 64) next(c, c.now == null ? now : c.until);
        }
        String climate = climateAt(g.player.x, g.player.z);
        Cycle c = cycles.get(climate);
        if (c == null) return;
        Preset p = presets.get(c.now);
        if (p == null) return;
        Weather w = g.timeSystem.weather;
        // someone else set the weather (the console, a review): theirs stands until ours next changes
        boolean ours = w.name().equals(asked) || asked.isEmpty();
        boolean changed = !climate.equals(here) || !p.name().equals(lastPreset);
        if (changed || ours && g.time >= nextTouch) {
            here = climate; lastPreset = p.name();
            apply(w, p);
            nextTouch = g.time + 20;
        }
        // the air's temperature: the climate's, warmer by day, the weather's own on top; eased
        Map<String, Object> cl = asMap(climates.get(climate));
        float day = astratto.weather.TimeSystem.daylight(g.state.hourOfDay());
        float want = f(cl, "temp", 12) + f(cl, "swing", 8) * (day - 0.5f) + p.temp();
        temperature = Float.isNaN(temperature) ? want : temperature + (want - temperature) * 0.01f;
    }
    private String lastPreset = "";

    /** Asks the engine for a weather (a burst of a shower, the cloud's drift and the gusts on top). */
    private void apply(Weather w, Preset p) {
        double t = g.state.minutes;
        float burst = p.pulse() > 0 ? 1 - p.pulse() * (0.5f + 0.5f * (float) Math.sin(t / 9.0)) : 1;
        float drift = 0.12f * (float) Math.sin(t / 47.0 + here.hashCode());
        float cloud = Math.max(0, Math.min(1, p.cloud() + (p.cloud() > 0.1f && p.cloud() < 0.95f ? drift : 0)));
        String name = p.name();
        w.target(name, p.rain() * burst, p.snow(), p.hail(), p.sand(), p.fog(), p.wind(), cloud, p.storm(), p.drift(), p.dark(), p.tint()[0], p.tint()[1], p.tint()[2]);
        asked = name;
    }

    /** The next weather of a climate, from a moment (game minutes): drawn by its likelihoods, leaning by what came before. */
    private void next(Cycle c, double from) {
        Map<String, Object> cl = asMap(climates.get(c.climate));
        Map<String, Object> weights = map(cl, "weights");
        Preset was = c.now == null ? null : presets.get(c.now);
        double hour = (from / 60.0) % 24;
        boolean dawn = hour >= 4 && hour < 9, afternoon = hour >= 13 && hour < 19;
        List<Preset> pool = new ArrayList<>();
        List<Double> w = new ArrayList<>();
        double total = 0;
        for (var e : weights.entrySet()) {
            Preset p = presets.get(e.getKey());
            if (p == null || !p.natural()) continue;
            if (was != null && p.name().equals(was.name())) continue;
            if (!p.after().isEmpty() && (was == null || !p.after().contains(was.name()))) continue;
            double k = num(e.getValue());
            if (was != null) k *= f(asMap(groups.get(was.group())), p.group(), 1);
            if (dawn && p.group().equals("fog")) k *= f(cl, "dawnFog", 1);
            if (afternoon && p.group().equals("storm")) k *= f(cl, "afternoonStorm", 1);
            if (k <= 0) continue;
            pool.add(p); w.add(k); total += k;
        }
        Preset pick = null;
        if (total > 0) {
            // drawn from the climate's own seed and how many it has drawn: the same world turns over the same way
            double r = new java.util.Random(c.climate.hashCode() * 31L + c.draws * 7919L + 17).nextDouble() * total;
            for (int i = 0; i < pool.size(); i++) { r -= w.get(i); if (r <= 0) { pick = pool.get(i); break; } }
            if (pick == null) pick = pool.get(pool.size() - 1);
        } else pick = presets.values().iterator().next();
        c.draws++;
        int hours = pick.minHours() + (int) Math.floor(new java.util.Random(c.climate.hashCode() * 131L + c.draws).nextDouble() * (pick.maxHours() - pick.minHours() + 1));
        // it gives way on the hour, as the people's day turns over
        c.now = pick.name();
        c.until = Math.floor(from / 60.0 + Math.max(1, hours)) * 60.0;
        if (c.until <= from) c.until = from + 60;
        g.state.commit("WeatherChanged", 0, 0, null, 0, c.climate + ": " + c.now + " until " + Math.round(c.until));
    }

    /** Brings a weather at once over the climate a point is in, for some game hours (the console's; natural or not). */
    public boolean force(String name, float x, float z, int hours) {
        Preset p = byName(name);
        if (p == null) return false;
        Cycle c = cycles.get(climateAt(x, z));
        if (c == null) return false;
        c.now = p.name();
        c.until = Math.floor(g.state.minutes / 60.0 + Math.max(1, hours)) * 60.0;
        lastPreset = "";
        update();
        g.timeSystem.weather.snap();
        return true;
    }
    /** A preset by its name, any case, spaces or underscores. */
    public Preset byName(String name) {
        if (name == null) return null;
        String want = name.trim().replace('_', ' ');
        for (Preset p : presets.values()) if (p.name().equalsIgnoreCase(want)) return p;
        return null;
    }

    // ------------------------------------------------------------------ where, and what it is like there
    /** The climate that rules a point: its realm's, else its wild region's, else the default. */
    public String climateAt(float x, float z) {
        if (!WorldMap.shadowveil()) return climates.containsKey(fallback) ? fallback : cycles.keySet().iterator().next();
        String realm = DevMap.jurisdiction(x, z);
        Object c = realms.get(realm);
        if (c == null) {
            var t = rpg.world.shadowveil.ShadowVeil.terrain();
            String region = t == null ? null : t.regionId((int) Math.floor(x), (int) Math.floor(z));
            c = region == null ? null : regions.get(region);
        }
        String id = c == null ? fallback : String.valueOf(c);
        return climates.containsKey(id) ? id : fallback;
    }
    /** The weather over the player (its name). */
    public String name() { Cycle c = cycles.get(here); return c == null || c.now == null ? g.timeSystem.weather.name() : asked.equals(g.timeSystem.weather.name()) ? c.now : g.timeSystem.weather.name(); }
    /** The air's temperature over the player (C). */
    public float temperature() { return Float.isNaN(temperature) ? 12 : temperature; }
    /** The render settings of a kind of precipitation (the data's). */
    public Map<String, Object> render(String kind) { return map(map(data, "render"), kind); }

    // ------------------------------------------------------------------ saving
    public Map<String, Object> save() {
        Map<String, Object> m = new HashMap<>();
        for (Cycle c : cycles.values()) if (c.now != null) m.put(c.climate, Map.of("now", c.now, "until", c.until, "draws", c.draws));
        return m;
    }
    public void load(Map<String, Object> m) {
        for (Cycle c : cycles.values()) { c.now = null; c.until = 0; c.draws = 0; }
        here = ""; lastPreset = ""; asked = "";
        if (m == null) return;
        for (var e : m.entrySet()) {
            Cycle c = cycles.get(e.getKey());
            Map<String, Object> o = asMap(e.getValue());
            if (c == null || !(o.get("now") instanceof String now) || !presets.containsKey(now)) continue;
            c.now = now; c.until = num(o.get("until")); c.draws = (long) num(o.get("draws"));
        }
    }

    // ------------------------------------------------------------------ reading
    private static Map<String, Object> read() {
        try (InputStream in = WeatherSystem.class.getResourceAsStream("/data/weather.json")) {
            if (in == null) throw new IllegalStateException("data/weather.json is missing from the game");
            return astratto.util.Json.parseObject(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (java.io.IOException e) { throw new IllegalStateException("data/weather.json could not be read: " + e.getMessage(), e); }
    }
    @SuppressWarnings("unchecked") private static Map<String, Object> asMap(Object o) { return o instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of(); }
    private static Map<String, Object> map(Map<String, Object> m, String k) { return asMap(m.get(k)); }
    private static List<?> list(Map<String, Object> m, String k) { return m.get(k) instanceof List<?> l ? l : List.of(); }
    private static String str(Map<String, Object> m, String k, String d) { return m.get(k) instanceof String s ? s : d; }
    private static float f(Map<String, Object> m, String k) { return f(m, k, 0); }
    private static float f(Map<String, Object> m, String k, float d) { return m.get(k) instanceof Number n ? n.floatValue() : d; }
    private static float num(Object o) { return o instanceof Number n ? n.floatValue() : 0; }
}
