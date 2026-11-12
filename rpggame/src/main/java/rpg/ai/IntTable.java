package rpg.ai;

/**
 * (0.4.0, optimisation: ShadowVeil's 4,489 chunks) A set of ints, or a map of int to int, by open addressing: no boxing,
 * no entry objects. The routing graph's chunks held every standable cell and its zone in HashSet&lt;Integer&gt; and
 * HashMap&lt;Integer, Integer&gt; - three milliseconds and a third of a kilobyte of garbage per chunk built, fourteen
 * seconds and 150 MB for the whole world. Keys must not be {@link #EMPTY}.
 */
final class IntTable {
    static final int EMPTY = Integer.MIN_VALUE;
    private int[] keys, values;
    private int size, mask;

    IntTable(int expected) {
        int cap = Integer.highestOneBit(Math.max(4, expected * 2 - 1)) << 1;
        keys = new int[cap]; values = new int[cap]; mask = cap - 1;
        java.util.Arrays.fill(keys, EMPTY);
    }

    private static int mix(int k) { int h = k * 0x9E3779B1; return h ^ (h >>> 15); }

    /** The value at a key, or the fallback when the key is not here. */
    int get(int k, int fallback) {
        for (int i = mix(k) & mask; ; i = (i + 1) & mask) {
            int at = keys[i];
            if (at == k) return values[i];
            if (at == EMPTY) return fallback;
        }
    }
    boolean contains(int k) {
        for (int i = mix(k) & mask; ; i = (i + 1) & mask) {
            int at = keys[i];
            if (at == k) return true;
            if (at == EMPTY) return false;
        }
    }
    /** Sets a key's value (adds the key). */
    void put(int k, int v) {
        if ((size + 1) * 2 > keys.length) grow();
        for (int i = mix(k) & mask; ; i = (i + 1) & mask) {
            int at = keys[i];
            if (at == k) { values[i] = v; return; }
            if (at == EMPTY) { keys[i] = k; values[i] = v; size++; return; }
        }
    }
    void add(int k) { put(k, 0); }
    int size() { return size; }
    private void grow() {
        int[] ok = keys, ov = values;
        keys = new int[ok.length * 2]; values = new int[ok.length * 2]; mask = keys.length - 1; size = 0;
        java.util.Arrays.fill(keys, EMPTY);
        for (int i = 0; i < ok.length; i++) if (ok[i] != EMPTY) put(ok[i], ov[i]);
    }
}
