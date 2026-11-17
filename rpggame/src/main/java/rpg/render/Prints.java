package rpg.render;

/**
 * (0.4.8, gamefixes: a snow and puddle system - stepped on, footprints appear in each, differently) where feet and
 * hooves came down, as the figures plant them (Rig.plant, the animals' hooves), for the renderer to press into the
 * ground as it is now (Gfx.prints): a shadowed hollow in lying snow that lasts until fresh snow fills it; a dark wet sole
 * on soaked ground that dries away within a couple of minutes; a print in mud or sand. A small ring: the oldest give way.
 */
public final class Prints {
    private Prints() {}
    /** {x, y, z, yaw, hoof (1) or foot (0)} each. */
    static final float[][] RING = new float[256][5];
    static int head, tail;

    public static synchronized void push(float x, float y, float z, float yaw, boolean hoof) {
        float[] p = RING[head];
        p[0] = x; p[1] = y; p[2] = z; p[3] = yaw; p[4] = hoof ? 1 : 0;
        head = (head + 1) % RING.length;
        if (head == tail) tail = (tail + 1) % RING.length;
    }
    /** The next print laid since the last asked, or null. */
    static synchronized float[] poll() {
        if (tail == head) return null;
        float[] p = RING[tail].clone();
        tail = (tail + 1) % RING.length;
        return p;
    }
}
