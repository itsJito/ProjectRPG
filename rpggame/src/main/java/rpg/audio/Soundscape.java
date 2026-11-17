package rpg.audio;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import rpg.Rpg;
import rpg.world.DevMap;
import rpg.world.WorldMap;

/**
 * (0.4.2, gamefixes: configure the game's audio to each region, situation and context instead of playing them all
 * everywhere) Where the player is, as the ear hears it: the place (a realm's streets, a palace's, a wood, the open
 * country, a barren waste, a shore), the hour, whether a roof is overhead or the ground over them - read from the world
 * the player stands in, never from a list of spots. The music takes its mood from it and, where the realm or the
 * ground names pieces of its own ({@code data/audio.json}), plays those; the ambience bed takes its level from it,
 * fading between places rather than stepping. On ShadowVeil the old reading of the devmap's own rectangles (its wood,
 * its altar, its palace) played the devmap's wood and hymn at the same coordinates of the new world.
 */
public final class Soundscape {
    private final Rpg g;
    private final Map<String, Object> data;
    private final List<float[]> altars = new ArrayList<>();
    private boolean altarsRead;
    /** The realm or ground whose pieces the music plays (null: the general set). */
    private String region;
    private float bed = -1;

    public Soundscape(Rpg g) {
        this.g = g;
        Map<String, Object> m = Map.of();
        try (InputStream in = Soundscape.class.getResourceAsStream("/data/audio.json")) {
            if (in != null) m = astratto.util.Json.parseObject(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (Exception broken) { System.err.println("[audio] audio.json could not be read: " + broken.getMessage()); }
        data = m;
    }

    /** (0.4.7) A section of the audio data (the weather beds'). */
    public Map<String, Object> section(String key) { return map(data, key); }

    /** What the place sounds like: the kinds of ground the ambience knows. */
    public enum Place { TOWN, FARMLAND, WOOD, OPEN, BARREN, COAST, INDOORS, UNDERGROUND }

    // ------------------------------------------------------------------ the music
    /** The mood of the place (combat and pursuit are the game's to decide first): Rpg.mood() asks this. */
    public Music.Mood placeMood(boolean night) {
        region = null;
        float x = g.player.x, z = g.player.z;
        if (!WorldMap.shadowveil()) return devmapMood(night);
        var t = rpg.world.shadowveil.ShadowVeil.terrain();
        Map<String, Object> mu = map(data, "music");
        if (underground(t)) return Music.Mood.DUNGEON;
        if (nearAltar(num(mu, "sacredReach", 9))) return Music.Mood.SACRED;
        String realm = DevMap.jurisdiction(x, z);
        String biome = t == null ? "" : t.biomeAt(clamp(x, t.S), clamp(z, t.S));
        boolean town = !DevMap.WILDS.equals(realm);
        Music.Mood mood;
        if (town && strings(mu, "palaceRealms").contains(realm)) mood = night ? Music.Mood.PALACE_NIGHT : Music.Mood.PALACE_DAY;
        else if (town) mood = night ? Music.Mood.TOWN_NIGHT : Music.Mood.TOWN_DAY;
        else if (strings(mu, "woodBiomes").contains(biome)) mood = night ? Music.Mood.WOOD_NIGHT : Music.Mood.WOOD_DAY;
        else mood = night ? Music.Mood.OPEN_NIGHT : Music.Mood.OPEN_DAY;
        // the realm's own pieces first (a town's), else the ground's (a desert's, the Dead Wood's)
        Map<String, Object> regions = map(mu, "regions");
        if (town && regions.containsKey(realm)) region = realm;
        else if (regions.containsKey(biome)) region = biome;
        return mood;
    }

    /** The devmap's places as it always had them. */
    private Music.Mood devmapMood(boolean night) {
        float x = g.player.x, z = g.player.z;
        if (DevMap.inDungeon(x, z)) return Music.Mood.DUNGEON;
        if (Math.hypot(x - DevMap.ALTAR[0] - 0.5, z - DevMap.ALTAR[1] - 0.5) < 9 || Math.hypot(x - rpg.world.Expanse.ALTAR[0], z - rpg.world.Expanse.ALTAR[1]) < 9) return Music.Mood.SACRED;
        if (DevMap.PALACE.equals(DevMap.jurisdiction(x, z))) return night ? Music.Mood.PALACE_NIGHT : Music.Mood.PALACE_DAY;
        if (x >= DevMap.WILD_X0 && x <= DevMap.WILD_X1 && z >= DevMap.WILD_Z0 && z <= DevMap.WILD_Z1) return night ? Music.Mood.WOOD_NIGHT : Music.Mood.WOOD_DAY;
        if (!DevMap.WILDS.equals(DevMap.jurisdiction(x, z))) return night ? Music.Mood.TOWN_NIGHT : Music.Mood.TOWN_DAY;
        return night ? Music.Mood.OPEN_NIGHT : Music.Mood.OPEN_DAY;
    }

    /** The pieces this place names for a mood, or null for the general set. */
    public String[] tracks(Music.Mood mood) {
        if (region == null) return null;
        Object o = map(map(data, "music"), "regions").get(region);
        if (!(o instanceof Map<?, ?> m) || !(m.get(mood.name()) instanceof List<?> l) || l.isEmpty()) return null;
        String[] out = new String[l.size()];
        for (int i = 0; i < out.length; i++) out[i] = String.valueOf(l.get(i));
        return out;
    }
    public String region() { return region; }

    // ------------------------------------------------------------------ the ambience
    /** The place as the ambience hears it. */
    public Place place() {
        float x = g.player.x, z = g.player.z;
        var t = WorldMap.shadowveil() ? rpg.world.shadowveil.ShadowVeil.terrain() : null;
        if (t != null ? underground(t) : DevMap.inDungeon(x, z)) return Place.UNDERGROUND;
        if (g.vox.skyLight((int) Math.floor(x), (int) Math.floor(g.player.y + 1), (int) Math.floor(z)) < 0.2f) return Place.INDOORS;
        if (!DevMap.WILDS.equals(DevMap.jurisdiction(x, z))) return Place.TOWN;
        if (t == null) return x >= DevMap.WILD_X0 && x <= DevMap.WILD_X1 && z >= DevMap.WILD_Z0 && z <= DevMap.WILD_Z1 ? Place.WOOD : Place.OPEN;
        Map<String, Object> am = map(data, "ambience");
        String biome = t.biomeAt(clamp(x, t.S), clamp(z, t.S));
        if (strings(am, "barrenBiomes").contains(biome)) return Place.BARREN;
        if (strings(am, "coastBiomes").contains(biome)) return Place.COAST;
        if (strings(am, "farmBiomes").contains(biome)) return Place.FARMLAND;
        if (strings(map(data, "music"), "woodBiomes").contains(biome)) return Place.WOOD;
        return Place.OPEN;
    }

    /** The ambience bed's level for the place and hour, eased toward over the fade (seconds). */
    public float bedLevel(boolean night, float dt) {
        Map<String, Object> am = map(data, "ambience");
        Place p = place();
        float want = switch (p) {
            case UNDERGROUND -> num(am, "underground", 0);
            case INDOORS -> num(am, "indoors", 0.35f) * level(am, outdoorsPlace(), night);
            default -> level(am, p, night);
        };
        if (bed < 0) bed = want;
        bed += (want - bed) * Math.min(1, dt / Math.max(0.1f, num(am, "fade", 2.5f)));
        return bed;
    }
    /** The ambience file the bed loops. */
    public String bedFile() { return String.valueOf(map(data, "ambience").getOrDefault("file", "devmapambience.ogg")); }

    private Place outdoorsPlace() {
        if (!DevMap.WILDS.equals(DevMap.jurisdiction(g.player.x, g.player.z))) return Place.TOWN;
        return Place.OPEN;
    }
    private static float level(Map<String, Object> am, Place p, boolean night) {
        Map<String, Object> m = map(am, p.name().toLowerCase(java.util.Locale.ROOT));
        return num(m, night ? "night" : "day", 0.25f);
    }

    // ------------------------------------------------------------------ the world, read
    /** Under the ground: the player's feet a few cells below the ground's top here, with no sky over them. */
    private boolean underground(rpg.world.shadowveil.Terrain t) {
        if (t == null) return false;
        int x = clamp(g.player.x, t.S), z = clamp(g.player.z, t.S);
        float depth = num(map(data, "music"), "undergroundDepth", 4);
        return g.player.y < t.ground(x, z) + 1 - depth && g.vox.skyLight(x, (int) Math.floor(g.player.y + 1), z) < 0.1f;
    }
    private boolean nearAltar(float reach) {
        if (!altarsRead) {
            altarsRead = true;
            var t = rpg.world.shadowveil.ShadowVeil.terrain();
            if (t != null && t.arch != null) for (var s : t.arch.structures) { float[] a = s.points.get("altar"); if (a != null) altars.add(a); }
        }
        for (float[] a : altars) if (Math.hypot(g.player.x - a[0], g.player.z - a[1]) < reach) return true;
        return false;
    }

    private static int clamp(float v, int size) { return Math.max(0, Math.min(size - 1, (int) Math.floor(v))); }
    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Map<String, Object> m, String k) { return m.get(k) instanceof Map<?, ?> o ? (Map<String, Object>) o : Map.of(); }
    private static List<String> strings(Map<String, Object> m, String k) {
        if (!(m.get(k) instanceof List<?> l)) return List.of();
        List<String> out = new ArrayList<>();
        for (Object o : l) out.add(String.valueOf(o));
        return out;
    }
    private static float num(Map<String, Object> m, String k, float def) { return m.get(k) instanceof Number n ? n.floatValue() : def; }
}
