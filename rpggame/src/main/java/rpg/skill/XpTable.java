package rpg.skill;

/**
 * The one cumulative XP schedule every skill uses, players and NPCs alike (GDD 2.2): 6,000,000 XP at level 100, the
 * curve steepening after level 60. The thresholds are computed once from the GDD formula and stored as integers; the
 * stored table is the runtime authority - nothing else computes a level.
 *
 * <pre>
 * weight(i) = 1.04^(min(i, 59) - 1) * 1.05^(max(0, i - 59))        for transitions i = 1..99
 * XP(level) = round(6_000_000 * sum(weight(1..level-1)) / sum(weight(1..99)))
 * </pre>
 */
public final class XpTable {
    private XpTable() {}

    public static final int MAX_LEVEL = 100;
    public static final long MAX_XP = 6_000_000L;
    /** THRESHOLD[level] = the cumulative XP at which that level is reached (index 0 unused). */
    private static final long[] THRESHOLD = build();

    private static long[] build() {
        double[] w = new double[MAX_LEVEL];
        double total = 0;
        for (int i = 1; i <= 99; i++) {
            w[i] = Math.pow(1.04, Math.min(i, 59) - 1) * Math.pow(1.05, Math.max(0, i - 59));
            total += w[i];
        }
        long[] t = new long[MAX_LEVEL + 1];
        double sum = 0;
        t[1] = 0;
        for (int level = 2; level <= MAX_LEVEL; level++) {
            sum += w[level - 1];
            t[level] = Math.round(MAX_XP * sum / total);
        }
        t[MAX_LEVEL] = MAX_XP;
        return t;
    }

    /** The XP a level begins at (1..100). */
    public static long xpFor(int level) {
        if (level < 1 || level > MAX_LEVEL) throw new IllegalArgumentException("level " + level);
        return THRESHOLD[level];
    }

    /** The level an amount of XP is (1..100), by binary search over the stored table. */
    public static int levelFor(long xp) {
        if (xp <= 0) return 1;
        if (xp >= MAX_XP) return MAX_LEVEL;
        int lo = 1, hi = MAX_LEVEL;
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (THRESHOLD[mid] <= xp) lo = mid; else hi = mid - 1;
        }
        return lo;
    }

    /** Share of the way from this level to the next (1 at 100). */
    public static double progress(long xp) {
        int l = levelFor(xp);
        if (l >= MAX_LEVEL) return 1;
        return (xp - THRESHOLD[l]) / (double) (THRESHOLD[l + 1] - THRESHOLD[l]);
    }

    /** A copy of the stored thresholds (index = level). */
    public static long[] thresholds() { return THRESHOLD.clone(); }
}
