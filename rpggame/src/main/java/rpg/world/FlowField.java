package rpg.world;

import java.util.ArrayDeque;
import java.util.List;
import java.util.PriorityQueue;
import rpg.Tuning;

/**
 * (0.4.5, gamefixes: flowing water was not set up for all water - it should happen by itself, everywhere) How the water
 * of the world moves, column by column, worked out from the water itself rather than drawn in by hand:
 *
 * <ul>
 *   <li>Water runs downhill to the sea. From every open-sea column (and every column of water at the map's edge) the
 *       water is walked back upstream - to a neighbour whose surface stands as high or higher - so each column knows how
 *       far it lies above its way out, and flows toward the neighbour nearer it. A pond with no way out is still.</li>
 *   <li>How fast: a river runs at its pace ('water.riverSpeed'), quicker toward the lip of a drop (a fall, a rapid's
 *       step) within 'water.lipReach' cells; a lake or marsh barely moves except as it is drawn into the river that
 *       leaves it; the sea is still. The middle of a channel runs fastest, the water by its banks slackest.</li>
 *   <li>Whitewater ('foam'): just below and above every drop, and wherever the channel runs fast against its bank.</li>
 * </ul>
 * The same field drives everything that feels the water (a swimmer, a wader, a floating body - Fluids.flow) and what
 * shows it (the surface drifting, the foam - Gfx), so what is seen is what is felt. A map without a grown terrain (the
 * devmap) lays its own channels' boxes into the same field.
 */
public final class FlowField {
    /** The cost (cells) of a cell of rise against the flow, in the search for each column's way out. */
    static final float UPHILL = Tuning.f("water.uphillCost", 40f);
    /** The field's corner and size in columns. */
    public final int x0, z0, w, d;
    /** The flow per column, mm/s along x and z; whitewater 0..255. */
    private final short[] fx, fz;
    private final byte[] foam;
    public int moving;

    FlowField(int x0, int z0, int w, int d) {
        this.x0 = x0; this.z0 = z0; this.w = w; this.d = d;
        fx = new short[w * d]; fz = new short[w * d]; foam = new byte[w * d];
    }

    private int at(int x, int z) { x -= x0; z -= z0; return x < 0 || z < 0 || x >= w || z >= d ? -1 : x * d + z; }

    /** The flow at a column (m/s, x and z): zero outside the field or in still water. */
    public float vx(int x, int z) { int i = at(x, z); return i < 0 ? 0 : fx[i] / 1000f; }
    public float vz(int x, int z) { int i = at(x, z); return i < 0 ? 0 : fz[i] / 1000f; }
    /** Whitewater at a column, 0..1. */
    public float foam(int x, int z) { int i = at(x, z); return i < 0 ? 0 : (foam[i] & 255) / 255f; }

    /** The flow at a point, eased between the four columns round it (m/s, into out[0..1]). */
    public void sample(float x, float z, float[] out) {
        float gx = x - 0.5f, gz = z - 0.5f;
        int ix = (int) Math.floor(gx), iz = (int) Math.floor(gz);
        float tx = gx - ix, tz = gz - iz;
        float ax = 0, az = 0;
        for (int k = 0; k < 4; k++) {
            int cx = ix + (k & 1), cz = iz + (k >> 1);
            float wt = ((k & 1) == 0 ? 1 - tx : tx) * ((k >> 1) == 0 ? 1 - tz : tz);
            ax += vx(cx, cz) * wt; az += vz(cx, cz) * wt;
        }
        out[0] = ax; out[1] = az;
    }

    private void set(int i, float vx, float vz, float white) {
        float cap = 8f;
        fx[i] = (short) Math.round(Math.max(-cap, Math.min(cap, vx)) * 1000);
        fz[i] = (short) Math.round(Math.max(-cap, Math.min(cap, vz)) * 1000);
        foam[i] = (byte) Math.round(Math.max(0, Math.min(1, white)) * 255);
        if (vx != 0 || vz != 0) moving++;
    }

    /**
     * The field of a grown world: each column's water surface (-1: no water), whether it is the open sea, a river's
     * channel, a lake or marsh - from the terrain's own columns.
     */
    public static FlowField grow(int size, int[] water, boolean[] sea, boolean[] river) { return grow(size, water, sea, river, new int[0][]); }

    /**
     * The same, with the falls joined: each {upper column, lower column} a drop pours from one to the other over dry
     * rock (a fall is drawn, not built of water), so the stretches above and below a fall are one river.
     */
    public static FlowField grow(int size, int[] water, boolean[] sea, boolean[] river, int[][] falls) {
        FlowField f = new FlowField(0, 0, size, size);
        int n = size * size;
        float riverSpeed = Tuning.f("water.riverSpeed", 0.85f), lakeSpeed = Tuning.f("water.lakeSpeed", 0.05f);
        float lipBoost = Tuning.f("water.lipBoost", 0.7f), lipReach = Tuning.f("water.lipReach", 5f), bankSlack = Tuning.f("water.bankSlack", 0.5f);
        float drawReach = Tuning.f("water.outletReach", 6f), foamLip = Tuning.f("water.foamLip", 2.5f), foamFast = Tuning.f("water.foamFast", 1.05f);
        int[][] nb = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
        // 1. the way out: from the sea and the map's edge, walked upstream (to water standing as high or higher)
        float[] dist = new float[n];
        java.util.Arrays.fill(dist, Float.MAX_VALUE);
        PriorityQueue<long[]> q = new PriorityQueue<>((a, b) -> Long.compare(a[0], b[0]));
        for (int x = 0; x < size; x++) for (int z = 0; z < size; z++) {
            int i = x * size + z;
            if (water[i] < 0) continue;
            if (sea[i] || x == 0 || z == 0 || x == size - 1 || z == size - 1) { dist[i] = 0; q.add(new long[]{0, i}); }
        }
        java.util.Map<Integer, java.util.List<Integer>> above = new java.util.HashMap<>();
        java.util.Map<Integer, Integer> pours = new java.util.HashMap<>();
        for (int[] fl : falls) { above.computeIfAbsent(fl[1], k -> new java.util.ArrayList<>()).add(fl[0]); pours.put(fl[0], fl[1]); }
        drain(q, dist, water, size, above, nb);
        // (0.4.5) water that never reaches the sea - a river ending at a beach or the map's edge, a lake fed by a stream -
        // runs to the far end of its lowest stretch, away from where the higher water comes in (a pool fed by nothing
        // stays still)
        boolean[] seen = new boolean[n];
        for (int s0 = 0; s0 < n; s0++) {
            if (water[s0] < 0 || dist[s0] != Float.MAX_VALUE || seen[s0]) continue;
            java.util.ArrayList<Integer> comp = new java.util.ArrayList<>();
            ArrayDeque<Integer> fq = new ArrayDeque<>();
            fq.add(s0); seen[s0] = true;
            int low = Integer.MAX_VALUE;
            while (!fq.isEmpty()) {
                int c = fq.poll(); comp.add(c); low = Math.min(low, water[c]);
                int cx = c / size, cz = c % size;
                for (int k = 0; k < 4; k++) { int ax = cx + nb[k][0], az = cz + nb[k][1]; if (ax < 0 || az < 0 || ax >= size || az >= size) continue; int j2 = ax * size + az; if (water[j2] >= 0 && !seen[j2] && dist[j2] == Float.MAX_VALUE) { seen[j2] = true; fq.add(j2); } }
            }
            // the lowest stretch, measured from where higher water meets it
            java.util.HashMap<Integer, Integer> far = new java.util.HashMap<>();
            ArrayDeque<Integer> bq = new ArrayDeque<>();
            for (int c : comp) {
                if (water[c] != low) continue;
                int cx = c / size, cz = c % size; boolean inflow = false;
                for (int k = 0; k < 4; k++) { int ax = cx + nb[k][0], az = cz + nb[k][1]; if (ax < 0 || az < 0 || ax >= size || az >= size) continue; int j2 = ax * size + az; if (water[j2] > low) inflow = true; }
                if (inflow || above.containsKey(c)) { far.put(c, 0); bq.add(c); }
            }
            if (bq.isEmpty()) continue;
            int most = 0;
            while (!bq.isEmpty()) {
                int c = bq.poll(), dc = far.get(c), cx = c / size, cz = c % size; most = Math.max(most, dc);
                for (int k = 0; k < 4; k++) { int ax = cx + nb[k][0], az = cz + nb[k][1]; if (ax < 0 || az < 0 || ax >= size || az >= size) continue; int j2 = ax * size + az; if (water[j2] == low && !far.containsKey(j2) && dist[j2] == Float.MAX_VALUE) { far.put(j2, dc + 1); bq.add(j2); } }
            }
            for (var e : far.entrySet()) if (e.getValue() >= most - 1) { dist[e.getKey()] = 0; q.add(new long[]{0, e.getKey()}); }
            drain(q, dist, water, size, above, nb);
        }
        if (System.getenv("RPG_FLOW_DEBUG") != null) {
            int wet = 0, seaN = 0, riverN = 0, reached = 0, riverReached = 0;
            for (int i = 0; i < n; i++) { if (water[i] < 0) continue; wet++; if (sea[i]) seaN++; if (river[i]) riverN++; if (dist[i] != Float.MAX_VALUE) { reached++; if (river[i]) riverReached++; } }
            System.out.printf("[water] wet %d sea %d river %d reached %d (river %d)%n", wet, seaN, riverN, reached, riverReached);
        }
        // 2. the lips: a column with water a level or more below it beside it (a fall's or a rapid's top) - and how far
        // every column of the same level lies from the nearest lip
        float[] toLip = new float[n], toRiver = new float[n], bank = new float[n];
        java.util.Arrays.fill(toLip, Float.MAX_VALUE); java.util.Arrays.fill(toRiver, Float.MAX_VALUE); java.util.Arrays.fill(bank, Float.MAX_VALUE);
        ArrayDeque<Integer> lips = new ArrayDeque<>(), rivers = new ArrayDeque<>(), banks = new ArrayDeque<>();
        for (int x = 0; x < size; x++) for (int z = 0; z < size; z++) {
            int i = x * size + z;
            if (water[i] < 0) continue;
            boolean lip = false, shore = false;
            for (int k = 0; k < 4; k++) {
                int ax = x + nb[k][0], az = z + nb[k][1];
                if (ax < 0 || az < 0 || ax >= size || az >= size) continue;
                int j = ax * size + az;
                if (water[j] < 0) shore = true;
                else if (water[j] < water[i]) lip = true;
            }
            if (lip) { toLip[i] = 0; lips.add(i); }
            if (river[i]) { toRiver[i] = 0; rivers.add(i); }
            if (shore) { bank[i] = 0; banks.add(i); }
        }
        for (int[] fl : falls) if (toLip[fl[0]] != 0) { toLip[fl[0]] = 0; lips.add(fl[0]); }
        spread(lips, toLip, water, size, true);
        spread(rivers, toRiver, water, size, false);
        spread(banks, bank, water, size, false);
        // 3. each column's way: toward the neighbours nearer the way out, then eased over its neighbourhood
        float[] gx = new float[n], gz = new float[n];
        for (int x = 0; x < size; x++) for (int z = 0; z < size; z++) {
            int i = x * size + z;
            if (water[i] < 0 || dist[i] == Float.MAX_VALUE || dist[i] == 0 && sea[i]) continue;
            float sx = 0, sz = 0;
            for (int[] o : nb) {
                int ax = x + o[0], az = z + o[1];
                if (ax < 0 || az < 0 || ax >= size || az >= size) continue;
                int j = ax * size + az;
                if (water[j] < 0 || dist[j] >= dist[i]) continue;
                float len = o[0] != 0 && o[1] != 0 ? 1.4142f : 1f, drop = (dist[i] - dist[j]) / len;
                sx += o[0] / len * drop; sz += o[1] / len * drop;
            }
            Integer pool = pours.get(i);
            if (pool != null) { int lx = pool / size - x, lz = pool % size - z; float ll = (float) Math.hypot(lx, lz); if (ll > 0) { sx += lx / ll * 3; sz += lz / ll * 3; } }
            float l = (float) Math.hypot(sx, sz);
            if (l > 1e-4f) { gx[i] = sx / l; gz[i] = sz / l; }
        }
        // the wider channel's mid-line: the deepest a column lies from either bank in its reach (its half width)
        for (int x = 0; x < size; x++) for (int z = 0; z < size; z++) {
            int i = x * size + z;
            if (water[i] < 0 || dist[i] == Float.MAX_VALUE) continue;
            float ax = 0, az = 0;
            for (int ox = -2; ox <= 2; ox++) for (int oz = -2; oz <= 2; oz++) {
                int bx = x + ox, bz = z + oz;
                if (bx < 0 || bz < 0 || bx >= size || bz >= size) continue;
                int j = bx * size + bz;
                if (water[j] != water[i]) continue;
                float wt = 1f / (1 + ox * ox + oz * oz);
                ax += gx[j] * wt; az += gz[j] * wt;
            }
            float l = (float) Math.hypot(ax, az);
            if (l < 1e-4f) continue;
            ax /= l; az /= l;
            boolean isRiver = river[i];
            float speed;
            if (sea[i]) speed = 0;
            else if (isRiver) speed = riverSpeed;
            else speed = lakeSpeed + (riverSpeed - lakeSpeed) * (float) Math.exp(-toRiver[i] / Math.max(0.5f, drawReach));
            if (toLip[i] != Float.MAX_VALUE) speed *= 1 + lipBoost * (float) Math.exp(-toLip[i] / Math.max(0.5f, lipReach));
            // slack by the banks: the half width is the farthest from a bank in the neighbourhood
            float half = 1;
            for (int ox = -3; ox <= 3; ox++) for (int oz = -3; oz <= 3; oz++) {
                int bx = x + ox, bz = z + oz;
                if (bx < 0 || bz < 0 || bx >= size || bz >= size) continue;
                int j = bx * size + bz;
                if (water[j] == water[i] && bank[j] != Float.MAX_VALUE) half = Math.max(half, bank[j] + 1);
            }
            float mid = Math.min(1, (bank[i] + 0.5f) / half);
            speed *= bankSlack + (1 - bankSlack) * mid;
            float white = toLip[i] == Float.MAX_VALUE ? 0 : (float) Math.exp(-toLip[i] / Math.max(0.5f, foamLip));
            if (speed > foamFast) white = Math.max(white, Math.min(1, (speed - foamFast) * 0.9f) * (1 - mid * 0.6f));
            if (speed < 0.005f) continue;
            f.set(i, ax * speed, az * speed, white);
        }
        // below a drop: the plunge pool churns white
        for (int x = 0; x < size; x++) for (int z = 0; z < size; z++) {
            int i = x * size + z;
            if (water[i] < 0) continue;
            for (int k = 0; k < 4; k++) {
                int ax = x + nb[k][0], az = z + nb[k][1];
                if (ax < 0 || az < 0 || ax >= size || az >= size) continue;
                int j = ax * size + az;
                if (water[j] > water[i]) { int cur = f.foam[i] & 255; f.foam[i] = (byte) Math.max(cur, 235); }
            }
        }
        return f;
    }

    /** The cheapest way out from the queued columns, walked upstream (Dijkstra; a cell of rise against the flow costs UPHILL). */
    private static void drain(PriorityQueue<long[]> q, float[] dist, int[] water, int size, java.util.Map<Integer, java.util.List<Integer>> above, int[][] nb) {
        while (!q.isEmpty()) {
            long[] e = q.poll();
            int i = (int) e[1];
            float di = e[0] / 1000f;
            if (di > dist[i]) continue;
            int x = i / size, z = i % size;
            // up a fall to the stretch it pours from
            for (int j : above.getOrDefault(i, java.util.List.of())) { float nd = di + 2; if (nd < dist[j]) { dist[j] = nd; q.add(new long[]{Math.round(nd * 1000), j}); } }
            for (int[] o : nb) {
                int ax = x + o[0], az = z + o[1];
                if (ax < 0 || az < 0 || ax >= size || az >= size) continue;
                int j = ax * size + az;
                if (water[j] < 0) continue;
                // a diagonal step only where both sides are water too (no flowing through a bank's corner)
                if (o[0] != 0 && o[1] != 0 && (water[x * size + az] < 0 || water[ax * size + z] < 0)) continue;
                // water runs down or level; where the carving left a reach's surface a step below the water it runs into
                // (a river's mouth cut under the sea's level, a lake's outflow) it still finds its way, at a cost that
                // keeps any truly downhill way first
                float nd = di + (o[0] != 0 && o[1] != 0 ? 1.4142f : 1f) + Math.max(0, water[i] - water[j]) * UPHILL;
                if (nd < dist[j]) { dist[j] = nd; q.add(new long[]{Math.round(nd * 1000), j}); }
            }
        }
    }

    /** A breadth-first reach over water (of the same level only, if asked) from the seeds, cell by cell. */
    private static void spread(ArrayDeque<Integer> seeds, float[] out, int[] water, int size, boolean sameLevel) {
        int[][] nb = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!seeds.isEmpty()) {
            int i = seeds.poll(), x = i / size, z = i % size;
            for (int[] o : nb) {
                int ax = x + o[0], az = z + o[1];
                if (ax < 0 || az < 0 || ax >= size || az >= size) continue;
                int j = ax * size + az;
                if (water[j] < 0 || sameLevel && water[j] != water[i] || out[j] <= out[i] + 1) continue;
                out[j] = out[i] + 1;
                seeds.add(j);
            }
        }
    }

    /** Channels' boxes laid over the field where it is still (a grown world's aqueducts), each at its own pace. */
    public void overlay(List<Fluids.Current> currents) {
        FlowField o = of(currents, x0 + w, z0 + d);
        for (int x = x0; x < x0 + w; x++) for (int z = z0; z < z0 + d; z++) {
            int i = at(x, z), j = o.at(x, z);
            if (i < 0 || j < 0 || o.fx[j] == 0 && o.fz[j] == 0 || fx[i] != 0 || fz[i] != 0) continue;
            set(i, o.fx[j] / 1000f, o.fz[j] / 1000f, 0);
        }
    }

    /** The devmap's field: its channels' boxes laid in (each strongest mid-channel, eased toward its banks). */
    public static FlowField of(List<Fluids.Current> currents, int sizeX, int sizeZ) {
        FlowField f = new FlowField(0, 0, sizeX, sizeZ);
        float[] ax = new float[sizeX * sizeZ], az = new float[sizeX * sizeZ];
        int[] over = new int[sizeX * sizeZ];
        for (Fluids.Current c : currents) for (int x = Math.max(0, c.x0()); x <= Math.min(sizeX - 1, c.x1()); x++) for (int z = Math.max(0, c.z0()); z <= Math.min(sizeZ - 1, c.z1()); z++) {
            boolean alongZ = Math.abs(c.dz()) > Math.abs(c.dx());
            float wide = alongZ ? c.x1() - c.x0() + 1 : c.z1() - c.z0() + 1;
            float across = alongZ ? x + 0.5f - (c.x0() + c.x1() + 1) * 0.5f : z + 0.5f - (c.z0() + c.z1() + 1) * 0.5f;
            float middle = 1 - 0.5f * Math.min(1, Math.abs(across) / Math.max(0.5f, wide * 0.5f));
            int i = x * sizeZ + z;
            ax[i] += c.dx() * c.speed() * middle; az[i] += c.dz() * c.speed() * middle; over[i]++;
        }
        for (int i = 0; i < ax.length; i++) if (over[i] > 0) f.set(i, ax[i] / over[i], az[i] / over[i], 0);
        return f;
    }
}
