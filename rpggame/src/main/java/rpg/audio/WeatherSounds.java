package rpg.audio;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import rpg.Rpg;

/**
 * (0.4.7, gamefixes: every weather polished) The weather heard: looped beds of light and heavy rain, a soft wind and a
 * gale, hail, a blizzard and a sandstorm ({@code data/audio.json} "weather"), each brought up by the part of the weather
 * it belongs to - the rain's strength, the wind's, the hail's, snow and wind together - so a drizzle patters, a storm
 * roars and a clear day is still. Under a roof the beds are heard muffled (the rain on the roof, the wind round the
 * walls); underground not at all. Each eases toward its level over the data's fade, and a bed is only loaded and started
 * the first time it is wanted.
 */
public final class WeatherSounds {
    /** A bed: its file and level, the weather it follows (one part, or two multiplied: "snow*wind"), and the window of it. */
    record Bed(String name, String file, float gain, String[] parts, float from, float full, float fadeFrom, float fadeTo, boolean roof) {}

    private final Rpg g;
    private final List<Bed> beds = new ArrayList<>();
    private final long[] voices;
    private final float[] level, sent;
    private final float fade, indoors, underground;

    public WeatherSounds(Rpg g, Map<String, Object> data) {
        this.g = g;
        fade = num(data, "fade", 1.5f); indoors = num(data, "indoors", 0.4f); underground = num(data, "underground", 0f);
        if (data.get("beds") instanceof Map<?, ?> bs) for (var e : bs.entrySet()) {
            if (!(e.getValue() instanceof Map<?, ?> raw)) continue;
            @SuppressWarnings("unchecked") Map<String, Object> b = (Map<String, Object>) raw;
            beds.add(new Bed(String.valueOf(e.getKey()), String.valueOf(b.getOrDefault("file", "")), num(b, "gain", 0.5f),
                    String.valueOf(b.getOrDefault("follows", "rain")).split("[*]"), num(b, "from", 0.05f), num(b, "full", 0.6f), num(b, "fadeFrom", 0), num(b, "fadeTo", 0),
                    Boolean.TRUE.equals(b.get("roof"))));
        }
        voices = new long[beds.size()]; level = new float[beds.size()]; sent = new float[beds.size()];
        java.util.Arrays.fill(sent, -1);
    }

    /** The weather's part by name, as the engine eases it. */
    private float part(astratto.weather.Weather w, String k) {
        return switch (k.trim()) {
            case "rain" -> w.rain; case "snow" -> w.snow; case "hail" -> w.hail; case "sand" -> w.sand; case "drift" -> w.drift;
            case "wind" -> w.wind; case "storm" -> w.storm; case "fog" -> w.fog; default -> 0;
        };
    }

    private static float smooth(float a, float b, float v) { if (b <= a) return v >= a ? 1 : 0; float t = Math.max(0, Math.min(1, (v - a) / (b - a))); return t * t * (3 - 2 * t); }

    /** Each bed eased toward what the weather over the player asks of it, muffled under a roof (a few times a second). */
    public void update(float dt, Soundscape.Place place) {
        if (g.sfx == null || beds.isEmpty()) return;
        var w = g.timeSystem.weather;
        // (0.4.8) out of doors the open air's beds; under a roof they are heard through the walls (the mixer dulls them,
        // VOICE_OUTDOOR) and the rain drums on the roof itself (a "roof" bed, heard only there)
        float where = place == Soundscape.Place.UNDERGROUND ? underground : place == Soundscape.Place.INDOORS ? indoors : 1;
        boolean under = place == Soundscape.Place.INDOORS;
        float ease = Math.min(1, dt / Math.max(0.05f, fade));
        for (int i = 0; i < beds.size(); i++) {
            Bed b = beds.get(i);
            float v = 1;
            for (String p : b.parts()) v *= part(w, p);
            float want = smooth(b.from(), b.full(), v) * (b.fadeTo() > b.fadeFrom() ? 1 - smooth(b.fadeFrom(), b.fadeTo(), v) : 1) * b.gain() * (b.roof() ? (under ? 1 : 0) : where);
            level[i] += (want - level[i]) * ease;
            if (level[i] < 0.002f && voices[i] == 0) continue;
            if (voices[i] == 0) { voices[i] = g.sfx.loop("weather." + b.name(), b.file(), !b.roof()); if (voices[i] == 0) continue; }
            if (Math.abs(level[i] - sent[i]) > 0.003f) { sent[i] = level[i]; g.sfx.loopLevel(voices[i], level[i]); }
        }
    }

    /** A bed's level now (for the tests and the console). */
    public float level(String name) { for (int i = 0; i < beds.size(); i++) if (beds.get(i).name().equals(name)) return level[i]; return 0; }

    private static float num(Map<String, Object> m, String k, float def) { return m.get(k) instanceof Number n ? n.floatValue() : def; }
}
