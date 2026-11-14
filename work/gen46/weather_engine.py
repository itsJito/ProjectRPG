E = r'C:\Users\John\Documents\ProjectRPG\theastrattoengine\java\src\main\java\astratto\weather'

def patch(p, pairs):
    s = open(E + p, encoding='utf-8').read()
    for a, b in pairs:
        assert s.count(a) == 1, (p, a[:70], s.count(a))
        s = s.replace(a, b)
    open(E + p, 'w', encoding='utf-8').write(s)

patch(r'\Weather.java', [
('''    private Kind kind = Kind.CLEAR;''', '''    private Kind kind = Kind.CLEAR;
    /**
     * (2.13) A weather is any mix the game asks for, not only the six kinds: its name and its targets - rain, snow, hail,
     * wind-borne sand, fog, wind (1 a strong wind; past it a gale), cloud, storm (how often lightning strikes), drifting
     * snow over the ground, the sky's extra darkness, and the tint the air takes (sand's ochre, a whiteout's white).
     */
    private String name = "Clear";
    private float tRain, tSnow, tFog, tWind = 0.18f, tCloud, tHail, tSand, tStorm, tDrift, tDark, tTintR = 1, tTintG = 1, tTintB = 1;
    /** The eased extras (as {@link #rain} and the rest): hail, sand in the wind, the storm's lightning, snow drifting over the ground, darkness, the air's tint. */
    public float hail, sand, storm, drift, dark, tintR = 1, tintG = 1, tintB = 1;'''),
('''    public Kind kind() { return kind; }

    /** Asks for a kind of weather; it arrives over {@link #transition} seconds. */
    public void set(Kind k) { if (k != null) kind = k; }

    /** Puts the weather where it is going at once (loading a save, a test, the console's "now"). */
    public void snap() {
        rain = kind.rain; snow = kind.snow; fog = kind.fog; overcast = kind.cloud; wind = kind.wind;
        wetness = kind.rain > 0 ? 1 : 0; snowCover = kind.snow > 0 ? 0.85f : 0;
    }''', '''    public Kind kind() { return kind; }
    /** (2.13) The name of the weather asked for (a kind's, or the game's own). */
    public String name() { return name; }

    /** Asks for a kind of weather; it arrives over {@link #transition} seconds. */
    public void set(Kind k) {
        if (k == null) return;
        kind = k;
        String n = k.name();
        target(n.charAt(0) + n.substring(1).toLowerCase(java.util.Locale.ROOT), k.rain, k.snow, 0, 0, k.fog, k.wind, k.cloud, k == Kind.STORM ? 1 : 0, 0, k == Kind.STORM ? 0.3f : 0, 1, 1, 1);
    }

    /** (2.13) Asks for any weather by its targets (see {@link #name}); it arrives over {@link #transition} seconds. */
    public void target(String name, float rain, float snow, float hail, float sand, float fog, float wind, float cloud, float storm, float drift, float dark, float tr, float tg, float tb) {
        this.name = name == null ? "" : name;
        tRain = rain; tSnow = snow; tHail = hail; tSand = sand; tFog = fog; tWind = wind; tCloud = cloud; tStorm = storm; tDrift = drift; tDark = dark;
        tTintR = tr; tTintG = tg; tTintB = tb;
        // the nearest of the six kinds, for whatever asks only for those
        kind = storm > 0.5f && rain > 0.5f ? Kind.STORM : snow > 0.2f ? Kind.SNOW : rain > 0.15f || hail > 0.3f ? Kind.RAIN : fog > 0.6f || sand > 0.5f ? Kind.FOG : cloud > 0.5f ? Kind.OVERCAST : Kind.CLEAR;
    }

    /** Puts the weather where it is going at once (loading a save, a test, the console's "now"). */
    public void snap() {
        rain = tRain; snow = tSnow; fog = tFog; overcast = tCloud; wind = tWind;
        hail = tHail; sand = tSand; storm = tStorm; drift = tDrift; dark = tDark; tintR = tTintR; tintG = tTintG; tintB = tTintB;
        wetness = tRain > 0 ? 1 : 0; snowCover = tSnow > 0 || tDrift > 0.3f ? 0.85f : 0;
    }'''),
('''        rain = toward(rain, kind.rain, step); snow = toward(snow, kind.snow, step); fog = toward(fog, kind.fog, step);
        overcast = toward(overcast, kind.cloud, step); wind = toward(wind, kind.wind, step * 1.5f);''',
'''        rain = toward(rain, tRain, step); snow = toward(snow, tSnow, step); fog = toward(fog, tFog, step);
        overcast = toward(overcast, tCloud, step); wind = toward(wind, tWind, step * 1.5f);
        hail = toward(hail, tHail, step * 1.5f); sand = toward(sand, tSand, step); storm = toward(storm, tStorm, step); drift = toward(drift, tDrift, step);
        dark = toward(dark, tDark, step); tintR = toward(tintR, tTintR, step); tintG = toward(tintG, tTintG, step); tintB = toward(tintB, tTintB, step);'''),
('''        if (snow > 0.02f) snowCover = Math.min(1, snowCover + dt * snow / 150f);''', '''        if (snow > 0.02f || drift > 0.3f) snowCover = Math.min(1, snowCover + dt * Math.max(snow, drift * 0.5f) / 150f);'''),
('''        float storminess = Math.max(0, Math.min(1, (rain - 0.7f) / 0.3f)) * Math.max(0, Math.min(1, (wind - 0.5f) / 0.4f));''',
'''        float storminess = Math.max(storm, Math.max(0, Math.min(1, (rain - 0.7f) / 0.3f)) * Math.max(0, Math.min(1, (wind - 0.5f) / 0.4f)));'''),
])

patch(r'\TimeSystem.java', [
('''    public float tintR=1,tintG=1,tintB=1,fogScale=1;''', '''    public float tintR=1,tintG=1,tintB=1,fogScale=1;
    /** (2.13) Whether the system rolls its own weather every four hours (off: the game asks for the weather itself). */
    public boolean autoWeather=true;'''),
('''        if(epoch!=weatherEpoch) {''', '''        if(autoWeather && epoch!=weatherEpoch) {'''),
])
print('ok')
