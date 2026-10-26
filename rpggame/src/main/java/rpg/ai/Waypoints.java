package rpg.ai;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import rpg.world.DevMap;
import rpg.world.Voxels;

/**
 * Long-distance travel for NPCs: the shortest chain of devmap waypoints (gates, roads, doors) from near where an NPC is
 * to near where it is going. The engine searches each leg's route itself; keeping the legs short keeps every search
 * well inside its budget, so a trip round the pond and through the town gate is walked, not given up as blocked.
 */
public final class Waypoints {
    private Waypoints() {}

    /** Trips shorter than this (m), or with a clear line, go straight. */
    public static final float DIRECT = 22f;

    private static float[][] P = DevMap.WAYPOINTS;
    private static final List<List<Integer>> ADJ = new ArrayList<>();
    /** The door each doorway point belongs to (null for the map's own waypoints). */
    private static rpg.objects.Door[] DOOR = new rpg.objects.Door[0];
    static { base(); }
    private static void base() {
        P = DevMap.WAYPOINTS;
        DOOR = new rpg.objects.Door[P.length];
        ADJ.clear();
        for (int i = 0; i < P.length; i++) ADJ.add(new ArrayList<>());
        for (int[] l : DevMap.LINKS) { ADJ.get(l[0]).add(l[1]); ADJ.get(l[1]).add(l[0]); }
    }

    /**
     * Every doorway joins the graph (gamefixes: people reach the insides of buildings): a point a metre in front of it on
     * each side, linked through it, and each linked to the waypoints and other doorways it has a clear walk to. A trip to
     * the back of a shop or into a locked store room is then a route like any other.
     */
    public static void doors(Voxels vox, List<rpg.objects.Door> doors) {
        base();
        List<float[]> pts = new ArrayList<>(Arrays.asList(DevMap.WAYPOINTS));
        int first = pts.size();
        for (rpg.objects.Door d : doors) {
            float[] in = d.front(d.x + (d.alongXAxis() ? 0 : 2), d.z + (d.alongXAxis() ? 2 : 0)), out = d.front(d.x - (d.alongXAxis() ? 0 : 2), d.z - (d.alongXAxis() ? 2 : 0));
            pts.add(in); pts.add(out);
        }
        P = pts.toArray(new float[0][]);
        DOOR = java.util.Arrays.copyOf(DOOR, P.length);
        for (int i = 0; i < doors.size(); i++) { DOOR[first + 2 * i] = doors.get(i); DOOR[first + 2 * i + 1] = doors.get(i); }
        for (int i = ADJ.size(); i < P.length; i++) ADJ.add(new ArrayList<>());
        for (int i = first; i < P.length; i += 2) link(i, i + 1);
        for (int i = first; i < P.length; i++) for (int j = 0; j < P.length; j++) {
            if (j == i || ADJ.get(i).contains(j)) continue;
            if (Math.hypot(P[i][0] - P[j][0], P[i][1] - P[j][1]) < 30 && clear(vox, P[i][0], P[i][1], P[j][0], P[j][1])) link(i, j);
        }
    }
    private static void link(int a, int b) { if (!ADJ.get(a).contains(b)) { ADJ.get(a).add(b); ADJ.get(b).add(a); } }

    public static float[] point(int i) { return P[i]; }

    /** The nearest waypoint outside every jurisdiction (out of town, by the nearest gate). */
    public static float[] nearestOutside(float x, float z) {
        float[] best = null; float bd = Float.MAX_VALUE;
        for (float[] p : DevMap.WAYPOINTS) if (DevMap.WILDS.equals(DevMap.jurisdiction(p[0], p[1]))) { float d = (float) Math.hypot(p[0] - x, p[1] - z); if (d < bd) { bd = d; best = p; } }
        return best == null ? new float[]{DevMap.SPAWN_X, DevMap.SPAWN_Z} : best;
    }

    /** The waypoints to walk through from (sx, sz) to (tx, tz), in order (empty: go straight). */
    public static int[] route(Voxels vox, float sx, float sz, float tx, float tz) { return route(vox, sx, sz, tx, tz, d -> !d.locked); }

    /** The same for a walker who may pass only the doors that test true (shut ones open by hand; locked ones by the key). */
    public static int[] route(Voxels vox, float sx, float sz, float tx, float tz, java.util.function.Predicate<rpg.objects.Door> mayPass) {
        if (Math.hypot(tx - sx, tz - sz) < DIRECT && clear(vox, sx, sz, tx, tz)) return new int[0];
        int a = nearest(vox, sx, sz), b = nearest(vox, tx, tz);
        if (a < 0 || b < 0) return new int[0];
        // Dijkstra over the links
        float[] dist = new float[P.length];
        int[] prev = new int[P.length];
        boolean[] done = new boolean[P.length];
        Arrays.fill(dist, Float.MAX_VALUE);
        Arrays.fill(prev, -1);
        dist[a] = 0;
        for (int it = 0; it < P.length; it++) {
            int u = -1;
            for (int i = 0; i < P.length; i++) if (!done[i] && (u < 0 || dist[i] < dist[u])) u = i;
            if (u < 0 || dist[u] == Float.MAX_VALUE) break;
            done[u] = true;
            if (u == b) break;
            for (int v : ADJ.get(u)) {
                // through a doorway (its two points) only if this walker may open it
                if (DOOR[u] != null && DOOR[u] == DOOR[v] && !mayPass.test(DOOR[u])) continue;
                float d = dist[u] + (float) Math.hypot(P[v][0] - P[u][0], P[v][1] - P[u][1]);
                if (d < dist[v]) { dist[v] = d; prev[v] = u; }
            }
        }
        if (dist[b] == Float.MAX_VALUE) return new int[0];
        List<Integer> path = new ArrayList<>();
        for (int v = b; v >= 0; v = prev[v]) path.add(0, v);
        // skip a first waypoint that is behind the walker (already nearer the second than it is)
        if (path.size() >= 2) {
            float[] p0 = P[path.get(0)], p1 = P[path.get(1)];
            if (Math.hypot(p1[0] - sx, p1[1] - sz) < Math.hypot(p1[0] - p0[0], p1[1] - p0[1]) && clear(vox, sx, sz, p1[0], p1[1])) path.remove(0);
        }
        return path.stream().mapToInt(Integer::intValue).toArray();
    }

    /** The nearest waypoint in plain sight of a point (or simply the nearest, when none is). */
    static int nearest(Voxels vox, float x, float z) {
        int best = -1, any = -1;
        float bd = Float.MAX_VALUE, ad = Float.MAX_VALUE;
        for (int i = 0; i < P.length; i++) {
            float d = (float) Math.hypot(P[i][0] - x, P[i][1] - z);
            if (d < ad) { ad = d; any = i; }
            if (d < bd && d < 40 && clear(vox, x, z, P[i][0], P[i][1])) { bd = d; best = i; }
        }
        return best >= 0 ? best : any;
    }

    /** A wall in the way; a doorway, shut or locked, is a way through (who may open it is the walker's business). */
    private static boolean blocked(Voxels vox, int x, int y, int z) {
        int b = vox.block(x, y, z);
        return vox.solid(x,y,z) && b != rpg.world.Blocks.DOOR && b != rpg.world.Blocks.LOCKED_DOOR;
    }

    /** Whether a body could walk straight along the line: nothing solid in the way, and ground underfoot all along (no pond, no pit). */
    static boolean clear(Voxels vox, float ax, float az, float bx, float bz) {
        if (vox == null) return true;
        float d = (float) Math.hypot(bx - ax, bz - az);
        int steps = Math.max(1, (int) Math.ceil(d / 0.5f));
        for (int i = 0; i <= steps; i++) {
            int x = (int) Math.floor(ax + (bx - ax) * i / steps), z = (int) Math.floor(az + (bz - az) * i / steps);
            if (blocked(vox, x, DevMap.FLOOR, z) || blocked(vox, x, DevMap.FLOOR + 1, z) || !vox.solid(x, DevMap.FLOOR - 1, z)) return false;
        }
        return true;
    }
}
