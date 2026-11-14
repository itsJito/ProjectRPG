R = r'C:\Users\John\Documents\ProjectRPG\rpggame\src\main\java\rpg'

def patch(p, pairs):
    s = open(R + p, encoding='utf-8').read()
    for a, b in pairs:
        assert s.count(a) == 1, (p, a[:60], s.count(a))
        s = s.replace(a, b)
    open(R + p, 'w', encoding='utf-8').write(s)

patch(r'\render\Gfx.java', [
('''        // GPU precipitation stops on geometry, so rain remains visible through a window.
        if(weather.rain>0.02f)
            cmd.emitter(0x400000007L,7,camX,camY+8,camZ,0.025f,180*weather.rain,6,0.7f,0.6f,0.75f,0.9f,weather.windX,-18,weather.windZ);''',
'''        // (0.4.6, gamefixes: rain looked terrible) the precipitation: rain, sleet, snow, hail, sand and drifting snow as
        // world-space layers round the eye (the engine's precipitation pass), stopping on whatever roofs the sky
        precipitation(g, weather);'''),
('''    // ------------------------------------------------------------------ fences (gamefixes: a model, not a block)''',
'''    // ------------------------------------------------------------------ the weather's precipitation (0.4.6)
    private final float[] precipLayers = new float[8 * 20];
    private int precipCount;
    /** One layer of precipitation from the data's settings for its kind, at a strength (0..1); none below a trace. */
    private void precipLayer(rpg.world.WeatherSystem ws, String which, int kind, float strength, float salt, float lengthBoost) {
        if (strength < 0.015f || precipCount >= 8) return;
        var d = ws.render(which);
        java.util.function.BiFunction<String, Float, Float> f = (k, def) -> d.get(k) instanceof Number n ? n.floatValue() : def;
        java.util.List<?> col = d.get("color") instanceof java.util.List<?> l ? l : java.util.List.of(1, 1, 1);
        int at = precipCount * 20;
        float preset = cfgPrecip();
        float[] o = precipLayers;
        o[at] = kind;
        o[at + 1] = Math.max(1, Math.round(f.apply("instances", 8000f) * Math.min(1.4f, strength) * preset));
        o[at + 2] = f.apply("fall", 8f);
        o[at + 3] = f.apply("size", 0.02f);
        o[at + 4] = f.apply("length", 0.4f) + f.apply("lengthRain", 0f) * lengthBoost;
        o[at + 5] = f.apply("tile", 32f);
        o[at + 6] = f.apply("tall", 24f);
        o[at + 7] = f.apply("low", 0f);
        o[at + 8] = f.apply("high", 0f);
        o[at + 9] = kind == 0 ? 1f : 1f;
        o[at + 10] = f.apply("turbulence", 0f);
        o[at + 11] = d.containsKey("high") ? 1 : 0;
        o[at + 12] = salt;
        for (int k = 0; k < 3; k++) o[at + 13 + k] = col.size() > k && col.get(k) instanceof Number n ? n.floatValue() : 1;
        o[at + 16] = f.apply("alpha", 0.5f) * Math.min(1, 0.45f + strength * 0.75f);
        o[at + 17] = o[at + 18] = o[at + 19] = 0;
        precipCount++;
    }
    /** The share of the precipitation's particles the quality preset affords (low: half). */
    private float cfgPrecip() { int q = sceneGame == null ? 2 : sceneGame.cfg.quality; return q <= 0 ? 0.5f : q == 1 ? 0.75f : 1f; }
    private void precipitation(Rpg g, astratto.weather.Weather w) {
        precipCount = 0;
        var ws = g.weatherSystem;
        if (ws == null) return;
        // sleet: rain and wet snow together; a hailstorm's rain with it
        precipLayer(ws, "rain", 0, w.rain, 1, w.rain);
        precipLayer(ws, "snow", 1, w.snow, 2, 0);
        precipLayer(ws, "hail", 2, w.hail, 3, 0);
        precipLayer(ws, "sand", 3, w.sand, 4, 0);
        precipLayer(ws, "drift", 3, w.drift, 5, 0);
        precipLayer(ws, "splash", 4, Math.min(1, w.rain + w.hail * 0.8f), 6, 0);
        if (precipCount > 0) cmd.precip(w.windX, w.windZ, (float) (g.time % 4096), 1f, precipLayers, precipCount);
    }

    // ------------------------------------------------------------------ fences (gamefixes: a model, not a block)'''),
# the fog: thicker and nearer in dense weather, tinted by sand or a whiteout
('''        float wfog = weather == null ? 0 : weather.fog;''', '''        float wfog = weather == null ? 0 : weather.fog;
        // (0.4.6) a sandstorm's ochre, a whiteout's white: the air takes the weather's tint as it thickens
        if (weather != null && (Math.abs(weather.tintR - 1) + Math.abs(weather.tintG - 1) + Math.abs(weather.tintB - 1)) > 0.01f) {
            float k = Math.min(1, weather.fog);
            fogR = clamp01(fogR * (1 + (weather.tintR - 1) * k)); fogG = clamp01(fogG * (1 + (weather.tintG - 1) * k)); fogB = clamp01(fogB * (1 + (weather.tintB - 1) * k));
        }
        if (weather != null && weather.dark > 0) { float k = 1 - 0.55f * weather.dark; fogR *= k; fogG *= k; fogB *= k; }'''),
('''        float mist = 0.0022f + 0.0075f * dawn + 0.0045f * dusk + 0.0045f * night + wfog * 0.012f;''',
'''        float mist = 0.0022f + 0.0075f * dawn + 0.0045f * dusk + 0.0045f * night + wfog * 0.012f + wfog * wfog * wfog * 0.03f;'''),
('''        float bankStart = indoorsScene ? 1e9f : Math.max(FOG_MIN, FOG_START - 10*dawn - 6*dusk - 8*night - 10*wfog) * reviewFogScale;''',
'''        // (0.4.6) dense fog, a sandstorm, a whiteout: the bank closes right in (a few dozen metres and the world is gone)
        float bankStart = indoorsScene ? 1e9f : Math.max(FOG_MIN * (1 - 0.6f * wfog * wfog), (FOG_START - 10*dawn - 6*dusk - 8*night - 10*wfog) * (1 - 0.72f * wfog * wfog * wfog)) * reviewFogScale;'''),
])
patch(r'\render\Atmosphere.java', [
('''        float dark = 1 - 0.12f * rain;''', '''        // (0.4.6) a dark overcast, a storm, a hurricane: the day darkened; a sandstorm or a whiteout tints the sky its own colour
        float dark = (1 - 0.12f * rain) * (1 - 0.55f * w.dark);
        float tk = Math.min(1, w.fog);
        sky = v(sky[0] * (1 + (w.tintR - 1) * tk * 0.8f), sky[1] * (1 + (w.tintG - 1) * tk * 0.8f), sky[2] * (1 + (w.tintB - 1) * tk * 0.8f));'''),
])
print('ok')
