package rpg.animal;

import java.util.EnumMap;
import java.util.Map;

/**
 * An authored habitat cell (GDD 3.9): an area with what animals need in it - water to drink at, places to rest and
 * shelter, ground to graze - which species it supports and how many of each it can carry (a soft cap: breeding stops at
 * it). Its population ledger counts every animal that belongs here, live or held as a cohort when nobody is near, and
 * records each change by its cause, so the totals always add up.
 */
public final class Habitat {
    public final String id, name;
    public final float x0, z0, x1, z1;
    /** Where to drink (water's edge or a trough) and where to rest (a shelter, a den, cover). */
    public final float[][] water, rest;
    /** The soft carrying capacity per species (live and cohort together). */
    public final Map<Species, Integer> capacity = new EnumMap<>(Species.class);
    /** Owned by (0: wild). */
    public final long owner;

    /** Animals held abstractly while nobody is near: per species, [baby female, baby male, adult female, adult male]. */
    public final Map<Species, int[]> cohort = new EnumMap<>(Species.class);
    /** Changes by cause, for the inspector and the conservation test: births, hatchings, deaths by cause, maturations, moves in and out. */
    public final Map<String, Integer> causes = new java.util.TreeMap<>();

    public Habitat(String id, String name, float x0, float z0, float x1, float z1, long owner, float[][] water, float[][] rest) {
        this.id = id; this.name = name; this.x0 = x0; this.z0 = z0; this.x1 = x1; this.z1 = z1; this.owner = owner; this.water = water; this.rest = rest;
    }

    public boolean contains(float x, float z) { return x >= x0 && x <= x1 && z >= z0 && z <= z1; }
    public float cx() { return (x0 + x1) / 2; }
    public float cz() { return (z0 + z1) / 2; }

    public Habitat cap(Species s, int n) { capacity.put(s, n); return this; }

    public int cohortCount(Species s) { int[] c = cohort.get(s); return c == null ? 0 : c[0] + c[1] + c[2] + c[3]; }
    public int cohortTotal() { int n = 0; for (int[] c : cohort.values()) n += c[0] + c[1] + c[2] + c[3]; return n; }

    static int slot(boolean baby, boolean female) { return (baby ? 0 : 2) + (female ? 0 : 1); }

    void count(String cause, int n) { causes.merge(cause, n, Integer::sum); }

    /** The nearest point of a list to (x, z). */
    static float[] nearest(float[][] pts, float x, float z) {
        float[] best = null; float bd = Float.MAX_VALUE;
        for (float[] p : pts) { float d = (p[0] - x) * (p[0] - x) + (p[1] - z) * (p[1] - z); if (d < bd) { bd = d; best = p; } }
        return best;
    }
}
