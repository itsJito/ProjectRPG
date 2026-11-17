package rpg.world.shadowveil;

import java.util.Map;

/**
 * (0.4.8, gamefixes: enemy levels around the world - the start is Loxford, so the more dangerous enemies and dungeons lie
 * further away) how dangerous a place on ShadowVeil is, 0 (safe) to 1 (deadly), and the levels its enemies come in.
 *
 * <p>From {@code shadowveil.json} "danger": the danger rises from nothing within 'safe' cells of the origin realm's middle
 * (Loxford, where a new life begins) to its full at 'far' cells, along a curve; each region may add to it or take from it
 * ("regions": the volcano's coast harder, the farmland about the capital gentler). A place's band of enemy levels is the
 * data's 'low' and 'high' ranges taken at that danger: a goblin camp by Loxford holds level 1 to 10 goblins, one under
 * Ashmaw level 40 to 70 ones (as far as a goblin goes). A kind that cannot be as weak as a place's band (a troll by
 * Loxford) does not make its home there.
 */
public final class Danger {
    private Danger() {}

    private static Map<String, Object> data() { return Atlas.map(Atlas.get().root, "danger"); }

    /** The danger at a point: 0 safe .. 1 deadly (0 off ShadowVeil, where nothing is scaled). */
    public static float at(float x, float z) {
        if (!rpg.world.WorldMap.shadowveil()) return 0;
        Map<String, Object> d = data();
        if (d.isEmpty()) return 0;
        float[] o = origin(Atlas.s(d, "origin", "Loxford"));
        double safe = Atlas.d(d, "safe", 90), far = Atlas.d(d, "far", 850), curve = Atlas.d(d, "curve", 1.2);
        double dist = o == null ? far / 2 : Math.hypot(x - o[0], z - o[1]);
        double t = Math.max(0, Math.min(1, (dist - safe) / Math.max(1, far - safe)));
        t = Math.pow(t, curve);
        Terrain tr = ShadowVeil.terrain();
        String region = tr == null ? null : tr.regionId((int) x, (int) z);
        if (region != null) t += Atlas.d(Atlas.map(d, "regions"), region, 0);
        return (float) Math.max(0, Math.min(1, t));
    }

    /** The band of enemy levels at a point: {lowest, highest} ({1, 120} off ShadowVeil). */
    public static int[] band(float x, float z) {
        Map<String, Object> d = data();
        if (!rpg.world.WorldMap.shadowveil() || d.isEmpty()) return new int[]{1, 120};
        float t = at(x, z);
        java.util.List<?> lo = Atlas.arr(d, "low"), hi = Atlas.arr(d, "high");
        double l0 = lo.size() == 2 ? Atlas.dn(lo.get(0)) : 1, l1 = lo.size() == 2 ? Atlas.dn(lo.get(1)) : 60;
        double h0 = hi.size() == 2 ? Atlas.dn(hi.get(0)) : 10, h1 = hi.size() == 2 ? Atlas.dn(hi.get(1)) : 110;
        int a = (int) Math.round(l0 + (l1 - l0) * t), b = (int) Math.round(h0 + (h1 - h0) * t);
        return new int[]{Math.max(1, a), Math.max(a + 1, b)};
    }

    /** A kind's levels at a point: the place's band held to what the kind can be; null when the kind is too strong for the place. */
    public static int[] levels(rpg.monster.MonsterKind kind, float x, float z) {
        int[] b = band(x, z);
        int slack = rpg.Tuning.i("danger.kindSlack", 8);
        if (kind.minLevel > b[1] + slack) return null;
        int lo = Math.max(kind.minLevel, Math.min(kind.maxLevel, b[0])), hi = Math.max(lo, Math.min(kind.maxLevel, b[1]));
        return new int[]{lo, hi};
    }

    /** A level placed in a point's band: share 0 its weakest, 1 its strongest. */
    public static int level(float x, float z, double share) {
        int[] b = band(x, z);
        return (int) Math.round(b[0] + (b[1] - b[0]) * Math.max(0, Math.min(1.15, share)));
    }

    private static float[] origin(String name) {
        for (var r : ShadowVeil.realms()) if (r.name().equalsIgnoreCase(name)) return new float[]{r.cx(), r.cz()};
        return null;
    }
}
