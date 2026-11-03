package rpg.mount;

import java.util.HashMap;
import java.util.Map;

/**
 * (0.3.0, gamefixes: horse riding) What one particular mount can do: every horse carries its own performance - its
 * speeds at each gait, how high it jumps, how tall a step it takes in its stride without a jump, how quickly it turns,
 * and its wind. Rolled from the animal itself when it is first known (so a horse is the same horse after a load), and
 * handed down at a birth: a foal's is its parents' between them, give or take a little.
 */
public final class MountStats {
    /** Gait speeds (m/s), jump height at a perfect charge (m), the step it takes without jumping (m), turn rate (rad/s), wind. */
    public float walk, trot, canter, gallop, jump, step, turn, vigor;
    /** Whether it flies (the Pegasus). */
    public boolean flies;

    /** A horse's: varied a little by the seed (its id). */
    public static MountStats horse(long seed) {
        MountStats s = new MountStats();
        s.walk = 1.6f + roll(seed, 1) * 0.3f;
        s.trot = 3.4f + roll(seed, 2) * 0.7f;
        s.canter = 6.0f + roll(seed, 3) * 1.1f;
        s.gallop = 9.5f + roll(seed, 4) * 3.0f;
        s.jump = 1.15f + roll(seed, 5) * 1.15f;
        s.step = 1.05f;
        s.turn = 2.5f + roll(seed, 6) * 0.8f;
        s.vigor = 80 + roll(seed, 7) * 40;
        return s;
    }

    /** A Pegasus's: built like a war horse, faster and springier, and it flies. */
    public static MountStats pegasus(long seed) {
        MountStats s = horse(seed);
        s.gallop = 11f + roll(seed, 8) * 3f; s.canter += 0.8f;
        s.jump = 2.0f + roll(seed, 9) * 1.0f;
        s.vigor = 100 + roll(seed, 10) * 40;
        s.step = 1.1f;
        s.flies = true;
        return s;
    }

    /** A foal's: its parents' between them, each figure moved a little either way. */
    public static MountStats inherit(MountStats a, MountStats b, long seed) {
        MountStats s = new MountStats();
        s.walk = mix(a.walk, b.walk, seed, 1, 0.08f); s.trot = mix(a.trot, b.trot, seed, 2, 0.08f);
        s.canter = mix(a.canter, b.canter, seed, 3, 0.08f); s.gallop = mix(a.gallop, b.gallop, seed, 4, 0.1f);
        s.jump = mix(a.jump, b.jump, seed, 5, 0.1f); s.step = Math.max(a.step, b.step);
        s.turn = mix(a.turn, b.turn, seed, 6, 0.08f); s.vigor = mix(a.vigor, b.vigor, seed, 7, 0.1f);
        s.flies = a.flies || b.flies;
        return s;
    }
    private static float mix(float a, float b, long seed, int k, float spread) { return (a + b) / 2 * (1 + (roll(seed, k + 20) - 0.5f) * 2 * spread); }
    /** 0..1, fixed for a seed and a channel. */
    static float roll(long seed, int k) {
        long h = seed * 0x9E3779B97F4A7C15L + k * 0xBF58476D1CE4E5B9L;
        h ^= h >>> 31; h *= 0x94D049BB133111EBL; h ^= h >>> 29;
        return (h >>> 40) / (float) (1L << 24);
    }

    public Map<String, Object> save() {
        Map<String, Object> m = new HashMap<>();
        m.put("walk", walk); m.put("trot", trot); m.put("canter", canter); m.put("gallop", gallop); m.put("jump", jump);
        m.put("step", step); m.put("turn", turn); m.put("vigor", vigor); m.put("flies", flies);
        return m;
    }
    public static MountStats load(Map<String, Object> m) {
        if (m == null) return null;
        MountStats s = new MountStats();
        s.walk = (float) astratto.util.Json.num(m, "walk", 1.6); s.trot = (float) astratto.util.Json.num(m, "trot", 3.4);
        s.canter = (float) astratto.util.Json.num(m, "canter", 6); s.gallop = (float) astratto.util.Json.num(m, "gallop", 10);
        s.jump = (float) astratto.util.Json.num(m, "jump", 1.4); s.step = (float) astratto.util.Json.num(m, "step", 1.05);
        s.turn = (float) astratto.util.Json.num(m, "turn", 2.8); s.vigor = (float) astratto.util.Json.num(m, "vigor", 100);
        s.flies = astratto.util.Json.bool(m, "flies", false);
        return s;
    }

    @Override public String toString() { return String.format("gallop %.1f m/s, jump %.2f m%s", gallop, jump, flies ? ", flies" : ""); }
}
