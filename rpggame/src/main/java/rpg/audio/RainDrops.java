package rpg.audio;

import java.util.Map;
import java.util.Random;
import rpg.Rpg;
import rpg.world.Blocks;

/**
 * (0.4.8, gamefixes: the rain sounded like a small puddle being filled - simulate the sound of each drop) the drops
 * that fall near the listener, heard one by one over the rain's wash (WeatherSounds' beds).
 *
 * <p>Every frame the rain over the player sends down its share of drops ({@code audio.json} "weather" "drops": so many a
 * second at full rain), each landing somewhere round the listener - more of them further off, as the ground about grows
 * with the distance - on whatever is uppermost there: a leaf in a tree's crown, the surface of water, a roof's slates or
 * boards, stone or a street, or soft ground (a dull tick on turf). Each is a real recording of a drop on that surface,
 * picked from its bank, pitched a little, placed in the world and heard through it like any sound (the engine occludes
 * and reverberates it), so a drop on the far side of a wall is muffled and one on the leaves overhead patters above you.
 * Under a roof the drops are on the roof itself, over your head; underground there are none.
 */
public final class RainDrops {
    private final Rpg g;
    private final float rate, near, far, range, gain;
    private final Map<String, Object> surfaces;
    private final Random rnd = new Random(17);
    private double acc;
    public int fell;

    public RainDrops(Rpg g, Map<String, Object> data) {
        this.g = g;
        rate = num(data, "rate", 22); near = num(data, "near", 1.2f); far = num(data, "far", 9); range = num(data, "range", 16); gain = num(data, "gain", 0.5f);
        surfaces = data.get("surfaces") instanceof Map<?, ?> m ? cast(m) : Map.of();
        if (g.sfx != null) for (var e : surfaces.entrySet()) if (e.getValue() instanceof Map<?, ?> s && s.get("files") instanceof java.util.List<?> files)
            for (Object f : files) g.sfx.bankFile("rain.drop." + e.getKey(), String.valueOf(f));
    }

    @SuppressWarnings("unchecked") private static Map<String, Object> cast(Map<?, ?> m) { return (Map<String, Object>) m; }

    /** This frame's drops, by how hard it rains (hail adds its own patter to the roof). */
    public void update(float dt, Soundscape.Place place) {
        if (g.sfx == null || g.player == null || place == Soundscape.Place.UNDERGROUND) { acc = 0; return; }
        var w = g.timeSystem.weather;
        float wet = Math.min(1, w.rain + w.hail * 0.6f);
        if (wet < 0.02f) { acc = 0; return; }
        acc += dt * rate * (0.25f + 0.75f * wet) * wet * 1.6f;
        int n = (int) acc;
        acc -= n;
        for (int i = 0; i < Math.min(n, 12); i++) drop(place == Soundscape.Place.INDOORS, wet);
    }

    private void drop(boolean indoors, float wet) {
        var p = g.player;
        int px = (int) Math.floor(p.x), pz = (int) Math.floor(p.z), head = (int) Math.floor(p.eyeY()) + 1;
        if (indoors) {
            // the roof over the room: the first solid cell above the head, a few cells either way
            float ox = (rnd.nextFloat() * 2 - 1) * 3.5f, oz = (rnd.nextFloat() * 2 - 1) * 3.5f;
            int x = (int) Math.floor(p.x + ox), z = (int) Math.floor(p.z + oz);
            for (int y = head; y < head + 12; y++) if (g.vox.solid(x, y, z) && !Blocks.isLeaves(g.vox.block(x, y, z))) {
                play("roof", p.x + ox, y - 0.15f, p.z + oz, 0.8f);
                return;
            }
            return;
        }
        // out of doors: further off more often (the ground grows with the ring's radius), never on the listener's head
        double a = rnd.nextDouble() * Math.PI * 2, r = near + (far - near) * Math.sqrt(rnd.nextDouble());
        float x = (float) (p.x + Math.cos(a) * r), z = (float) (p.z + Math.sin(a) * r);
        int cx = (int) Math.floor(x), cz = (int) Math.floor(z);
        for (int y = head + 10; y > head - 14; y--) {
            int b = g.vox.block(cx, y, cz);
            if (b == Blocks.AIR) continue;
            String s = Blocks.isLeaves(b) ? "leaf" : b == Blocks.WATER ? "water" : surfaceOf(b);
            play(s, x, y + (s.equals("water") ? 0.9f : 1.02f), z, 1);
            return;
        }
    }

    /** The bank a block's top sounds as under a drop. */
    private static String surfaceOf(int b) {
        return switch (Sfx.surface(b)) {
            case "wood", "straw" -> "roof";
            case "grass", "earth", "cloth", "mud", "sand", "leaves" -> "soft";
            default -> "stone";
        };
    }

    private void play(String surface, float x, float y, float z, float scale) {
        Map<String, Object> s = surfaces.get(surface) instanceof Map<?, ?> m ? cast(m) : null;
        if (s == null) return;
        String bank = "rain.drop." + (s.get("as") instanceof String as ? as : surface);
        float pitch = num(s, "pitch", 1) * (0.85f + rnd.nextFloat() * 0.3f), level = num(s, "gain", 1) * gain * scale * (0.55f + rnd.nextFloat() * 0.45f);
        g.sfx.play(bank, x, y, z, level, pitch, range);
        fell++;
    }

    private static float num(Map<String, Object> m, String k, float def) { return m.get(k) instanceof Number n ? n.floatValue() : def; }
}
