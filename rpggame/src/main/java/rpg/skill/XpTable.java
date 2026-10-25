package rpg.skill;

/** The standard cumulative schedule shared by all eighteen skills. Existing saves retain their raw XP.
 * NPCs use this table to express fixed profiles; only the player earns experience.
 */
public final class XpTable {
    private XpTable() {}

    public static final int MAX_LEVEL = 100;
    public static final long MAX_XP = 14_391_160L;
    /** THRESHOLD[level] = the cumulative XP at which that level is reached (index 0 unused). */
    private static final long[] THRESHOLD = build();

    private static long[] build() {
        long[] t = new long[MAX_LEVEL + 1];
        long points = 0;
        for (int level = 2; level <= MAX_LEVEL; level++) {
            int previous = level - 1;
            points += (long) Math.floor(previous + 300 * Math.pow(2, previous / 7.0));
            t[level] = points / 4;
        }
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
