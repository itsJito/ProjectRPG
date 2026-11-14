import json
R = r'C:\Users\John\Documents\ProjectRPG\rpggame\src\main'

def patch(p, pairs):
    s = open(R + p, encoding='utf-8').read()
    for a, b in pairs:
        assert s.count(a) == 1, (p, a[:70], s.count(a))
        s = s.replace(a, b)
    open(R + p, 'w', encoding='utf-8').write(s)

# --- data: every surface's footfall, layered; hooves their own
P = R + r'\resources\data\audio.json'
d = json.load(open(P, encoding='utf-8'))
d['footsteps'] = {
    "_about": "(0.4.6, gamefixes: foley to match every material, in time with the feet, quieter) what a footfall on each kind of ground sounds like: one or more layers (a recorded bank, its share of the step's loudness, its pitch), played together. 'feet' is the people's overall loudness (and how much harder a running foot lands); 'hooves' the same for a horse or a Pegasus, whose shod hooves ring on stone and drum on wood. 'wading' is laid over any step in water up to the shins; 'snowCover' over open ground the snow has settled on.",
    "feet": {"gain": 0.09, "force": 0.17, "range": 15},
    "hooves": {"gain": 0.16, "force": 0.2, "range": 26, "pitch": 0.78},
    "surfaces": {
        "grass":  [{"bank": "step.grass", "gain": 1.0, "pitch": 1.0}],
        "earth":  [{"bank": "step.earth", "gain": 1.0, "pitch": 1.0}],
        "mud":    [{"bank": "step.earth", "gain": 0.9, "pitch": 0.8}, {"bank": "step.water", "gain": 0.35, "pitch": 0.75}],
        "sand":   [{"bank": "step.earth", "gain": 0.55, "pitch": 1.18}, {"bank": "step.cloth", "gain": 0.45, "pitch": 0.85}],
        "gravel": [{"bank": "step.stone", "gain": 0.6, "pitch": 0.9}, {"bank": "step.earth", "gain": 0.6, "pitch": 1.2}],
        "stone":  [{"bank": "step.stone", "gain": 1.0, "pitch": 1.0}],
        "wood":   [{"bank": "step.wood", "gain": 1.0, "pitch": 1.0}],
        "cloth":  [{"bank": "step.cloth", "gain": 1.0, "pitch": 1.0}],
        "straw":  [{"bank": "step.grass", "gain": 0.7, "pitch": 1.12}, {"bank": "step.cloth", "gain": 0.4, "pitch": 1.25}],
        "leaves": [{"bank": "step.grass", "gain": 0.85, "pitch": 1.2}, {"bank": "step.cloth", "gain": 0.3, "pitch": 1.4}],
        "snow":   [{"bank": "step.snow", "gain": 1.0, "pitch": 1.0}],
        "ice":    [{"bank": "step.stone", "gain": 0.8, "pitch": 1.35}],
        "metal":  [{"bank": "step.stone", "gain": 0.55, "pitch": 1.05}, {"bank": "step.metal", "gain": 0.3, "pitch": 1.3}],
        "glass":  [{"bank": "step.stone", "gain": 0.7, "pitch": 1.25}, {"bank": "step.metal", "gain": 0.15, "pitch": 1.6}],
        "coal":   [{"bank": "step.stone", "gain": 0.6, "pitch": 0.85}, {"bank": "step.earth", "gain": 0.5, "pitch": 0.9}],
        "water":  [{"bank": "step.water", "gain": 0.9, "pitch": 1.0}]
    },
    "hoofSurfaces": {
        "stone":  [{"bank": "step.stone", "gain": 0.9, "pitch": 0.75}, {"bank": "step.metal", "gain": 0.45, "pitch": 0.9}],
        "gravel": [{"bank": "step.stone", "gain": 0.7, "pitch": 0.7}, {"bank": "step.earth", "gain": 0.7, "pitch": 0.85}],
        "wood":   [{"bank": "step.wood", "gain": 1.0, "pitch": 0.7}, {"bank": "step.plank", "gain": 0.5, "pitch": 0.8}],
        "metal":  [{"bank": "step.metal", "gain": 0.7, "pitch": 0.85}]
    },
    "wading": {"bank": "step.water", "gain": 0.75, "pitch": 1.0},
    "snowCover": {"bank": "step.snow", "gain": 0.8, "pitch": 1.0}
}
open(P, 'w', encoding='utf-8').write(json.dumps(d, indent=1, ensure_ascii=False))

patch(r'\java\rpg\audio\Sfx.java', [
('''        bank("step.snow", "foley/footstep_snow_00", 5);''',
'''        bank("step.snow", "foley/footstep_snow_00", 5);
        // (0.4.6) the other halves of the layered footfalls: a splash for wading and mud, a shod hoof's ring, a board's knock
        bank("step.water", "elements/water", 3, ".ogg", 1);
        bank("step.metal", "foley/impactMetal_light_00", 5);
        bank("step.plank", "foley/impactPlank_medium_00", 5);'''),
('''    /** A footfall: what it sounds like depends on what the foot came down on, and how hard (0..1). */
    public void footstep(Actor a, float force) {
        int below = a.y > 0 ? blockBelow(a) : 0;
        String surface = surface(below);
        float gain = 0.16f + 0.3f * force, pitch = 0.9f + rnd.nextFloat() * 0.2f;
        play("step." + surface, a.x, a.y + 0.05f, a.z, gain, pitch, 18 + 10 * force);
    }''',
'''    /**
     * A footfall: what it sounds like depends on what the foot came down on, and how hard (0..1). (0.4.6, gamefixes: foley
     * to match every material) each kind of ground its own layered sound from the data - gravel the stone and the earth
     * together, mud a squelch under the earth, a board's knock, a shod hoof's ring on the cobbles - with a splash laid over
     * a step in shallow water and the crunch of settled snow over open ground; quieter than before.
     */
    public void footstep(Actor a, float force) {
        int below = a.y > 0 ? blockBelow(a) : 0;
        boolean hooves = a instanceof rpg.animal.Animal an && (an.species == rpg.animal.Species.HORSE || an.species == rpg.animal.Species.PEGASUS);
        String surface = surface(below);
        java.util.Map<String, Object> fs = FOOTSTEPS;
        java.util.Map<String, Object> who = obj(fs, hooves ? "hooves" : "feet");
        float base = num(who, "gain", 0.09f) + num(who, "force", 0.17f) * force, range = num(who, "range", 15) + 8 * force, tone = num(who, "pitch", 1f);
        float pitch = (0.92f + rnd.nextFloat() * 0.16f) * tone;
        Object layers = hooves && obj(fs, "hoofSurfaces").containsKey(surface) ? obj(fs, "hoofSurfaces").get(surface) : obj(fs, "surfaces").get(surface);
        if (!(layers instanceof java.util.List<?> l) || l.isEmpty()) { play("step." + surface, a.x, a.y + 0.05f, a.z, base, pitch, range); return; }
        for (Object o : l) if (o instanceof java.util.Map<?, ?> m) layer(m, a, base, pitch, range);
        if (wading.test(a)) layer(obj(fs, "wading"), a, base, pitch, range);
        else if (snowy.test(a) && !surface.equals("snow")) layer(obj(fs, "snowCover"), a, base, pitch, range);
    }
    private void layer(java.util.Map<?, ?> m, Actor a, float base, float pitch, float range) {
        Object bank = m.get("bank");
        if (!(bank instanceof String b)) return;
        float gain = base * (m.get("gain") instanceof Number n ? n.floatValue() : 1), p = pitch * (m.get("pitch") instanceof Number n ? n.floatValue() : 1);
        play(b, a.x, a.y + 0.05f, a.z, gain, p, range);
    }
    /** Whether an actor's feet are in water up to the shins, and whether it walks on open ground the snow lies on (the game's). */
    public java.util.function.Predicate<Actor> wading = a -> false, snowy = a -> false;
    /** (0.4.6) The footfalls' data (audio.json "footsteps"). */
    private static final java.util.Map<String, Object> FOOTSTEPS = readFootsteps();
    @SuppressWarnings("unchecked")
    private static java.util.Map<String, Object> readFootsteps() {
        try (var in = Sfx.class.getResourceAsStream("/data/audio.json")) {
            if (in == null) return java.util.Map.of();
            var m = astratto.util.Json.parseObject(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            return m.get("footsteps") instanceof java.util.Map<?, ?> f ? (java.util.Map<String, Object>) f : java.util.Map.of();
        } catch (Exception e) { return java.util.Map.of(); }
    }
    @SuppressWarnings("unchecked")
    private static java.util.Map<String, Object> obj(java.util.Map<?, ?> m, String k) { return m.get(k) instanceof java.util.Map<?, ?> v ? (java.util.Map<String, Object>) v : java.util.Map.of(); }
    private static float num(java.util.Map<?, ?> m, String k, float d) { return m.get(k) instanceof Number n ? n.floatValue() : d; }'''),
('''    public static String surface(int block) {
        if (block == Blocks.GRASS) return "grass";
        return switch (Blocks.material(block)) {
            case FOLIAGE, STRAW -> "grass";
            case EARTH, SAND -> "earth";
            case WOOD -> "wood";
            case CLOTH -> "cloth";
            default -> "stone";
        };
    }''',
'''    public static String surface(int block) {
        if (block == Blocks.GRASS || block == Blocks.HEATH || block == Blocks.DRY_GRASS) return "grass";
        if (block == Blocks.MUD || block == Blocks.CLAY) return "mud";
        if (block == Blocks.GRAVEL) return "gravel";
        if (block == Blocks.ICE) return "ice";
        if (Blocks.isLeaves(block)) return "leaves";
        return switch (Blocks.material(block)) {
            case FOLIAGE -> "leaves";
            case STRAW -> "straw";
            case EARTH -> "earth";
            case SAND -> "sand";
            case WOOD -> "wood";
            case CLOTH -> "cloth";
            case METAL -> "metal";
            case GLASS -> "glass";
            case COAL -> "coal";
            case ICE -> "ice";
            case WATER -> "water";
            default -> "stone";
        };
    }'''),
])
print('ok')
