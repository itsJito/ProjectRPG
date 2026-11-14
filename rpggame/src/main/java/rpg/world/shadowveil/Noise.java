package rpg.world.shadowveil;

/**
 * (0.4.0) Seeded 2D gradient noise for the terrain: smooth (Perlin's improved gradient noise over a 256-entry
 * permutation, quintic fade), summed in octaves (fbm) or folded into ridges. Pure function of the seed and the point, so
 * the same design always grows the same world.
 */
public final class Noise {
    private final int[] perm = new int[512];
    private static final double[][] GRAD = new double[16][];
    static {
        for (int i = 0; i < 16; i++) { double a = i * Math.PI * 2 / 16; GRAD[i] = new double[]{Math.cos(a), Math.sin(a)}; }
    }

    Noise(long seed) {
        int[] p = new int[256];
        for (int i = 0; i < 256; i++) p[i] = i;
        long s = seed * 0x9E3779B97F4A7C15L + 0x632BE59BD9B4E019L;
        for (int i = 255; i > 0; i--) {
            s ^= s >>> 33; s *= 0xFF51AFD7ED558CCDL; s ^= s >>> 33;
            int j = (int) Math.floorMod(s, (long) (i + 1));
            int t = p[i]; p[i] = p[j]; p[j] = t;
        }
        for (int i = 0; i < 512; i++) perm[i] = p[i & 255];
    }

    private static double fade(double t) { return t * t * t * (t * (t * 6 - 15) + 10); }

    /** Gradient noise, about -1..1. */
    double at(double x, double z) {
        int xi = (int) Math.floor(x), zi = (int) Math.floor(z);
        double xf = x - xi, zf = z - zi;
        int X = xi & 255, Z = zi & 255;
        double u = fade(xf), v = fade(zf);
        double n00 = dot(perm[perm[X] + Z], xf, zf), n10 = dot(perm[perm[X + 1] + Z], xf - 1, zf);
        double n01 = dot(perm[perm[X] + Z + 1], xf, zf - 1), n11 = dot(perm[perm[X + 1] + Z + 1], xf - 1, zf - 1);
        double a = n00 + u * (n10 - n00), b = n01 + u * (n11 - n01);
        return (a + v * (b - a)) * 1.41;
    }
    private static double dot(int h, double x, double z) { double[] g = GRAD[h & 15]; return g[0] * x + g[1] * z; }

    /** Octaves summed, 0..1 (about). */
    double fbm(double x, double z, int octaves) {
        double sum = 0, amp = 1, norm = 0, f = 1;
        for (int o = 0; o < octaves; o++) { sum += at(x * f + o * 31.7, z * f - o * 17.3) * amp; norm += amp; amp *= 0.5; f *= 2.03; }
        return Math.max(0, Math.min(1, sum / norm * 0.5 + 0.5));
    }

    /** Ridged octaves, 0..1: sharp crests where the noise crosses zero (mountain spines). */
    double ridge(double x, double z, int octaves) {
        double sum = 0, amp = 1, norm = 0, f = 1, prev = 1;
        for (int o = 0; o < octaves; o++) {
            double r = 1 - Math.abs(at(x * f + o * 13.1, z * f + o * 7.7));
            r *= r;
            sum += r * amp * prev; norm += amp;
            prev = Math.min(1, r * 1.2);
            amp *= 0.5; f *= 2.1;
        }
        return Math.max(0, Math.min(1, sum / norm));
    }

    /** A fixed pseudo-random number 0..1 for an integer point (tree spots, ore spots). */
    public static double hash(long seed, int x, int z, int salt) {
        long h = seed * 0x9E3779B97F4A7C15L ^ (x * 0xC2B2AE3D27D4EB4FL) ^ (z * 0x165667B19E3779F9L) ^ (salt * 0x27D4EB2F165667C5L);
        h ^= h >>> 29; h *= 0xBF58476D1CE4E5B9L; h ^= h >>> 32; h *= 0x94D049BB133111EBL; h ^= h >>> 29;
        return (h >>> 11) * 0x1.0p-53;
    }
}
