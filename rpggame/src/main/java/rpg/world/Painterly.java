package rpg.world;

/**
 * The painterly finish over a block texture (0.1.9, gamefixes: "Stylized Heroic Fantasy / Painterly Stylized
 * Realism" - hand-painted, colourful and readable, a storybook's world). The materials are painted texel by texel from
 * noise (Blocks.Painter); this turns that into paint:
 *
 * <ul>
 * <li>a Kuwahara filter - each texel takes the mean of whichever of its four neighbouring quadrants is most even - which
 * gathers the speckle of the noise into flat, hard-edged dabs of colour, the way a brush lays it;</li>
 * <li>an ink line where the value breaks sharply (between stones, along mortar, round a knot), a darker shade of the
 * colour under it, never black, so each shape reads at a distance;</li>
 * <li>light warm and shadow cool, the colour a little richer - the painter's split, not a filter's grey;</li>
 * <li>a broad soft wash of value over the whole, as a watercolour ground under the strokes.</li>
 * </ul>
 * Every step wraps round the tile's edges, so the texture still tiles. Alpha is left as it was.
 */
final class Painterly {
    private Painterly() {}

    static int[][] finish(int[][] src, int size, long seed) {
        int n = size * size;
        float[][] c = new float[n][3];
        for (int i = 0; i < n; i++) for (int k = 0; k < 3; k++) c[i][k] = src[i][k] / 255f;
        float[][] k = kuwahara(c, size, 2);
        float[] lum = new float[n];
        for (int i = 0; i < n; i++) lum[i] = luma(k[i]);
        float[] wash = wash(size, seed);
        int[][] out = new int[n][];
        for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) {
            int i = y * size + x;
            float[] p = k[i].clone();
            // ink: the Sobel of the value, darkening the side that is already darker (a line sits in the joint)
            float gx = lum[at(x + 1, y - 1, size)] + 2 * lum[at(x + 1, y, size)] + lum[at(x + 1, y + 1, size)]
                    - lum[at(x - 1, y - 1, size)] - 2 * lum[at(x - 1, y, size)] - lum[at(x - 1, y + 1, size)];
            float gy = lum[at(x - 1, y + 1, size)] + 2 * lum[at(x, y + 1, size)] + lum[at(x + 1, y + 1, size)]
                    - lum[at(x - 1, y - 1, size)] - 2 * lum[at(x, y - 1, size)] - lum[at(x + 1, y - 1, size)];
            float edge = (float) Math.sqrt(gx * gx + gy * gy);
            float around = 0;
            for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) around += lum[at(x + dx, y + dy, size)];
            around /= 9;
            float ink = smooth(0.18f, 0.55f, edge) * (lum[i] <= around + 0.01f ? 1 : 0.35f);
            float dark = 1 - 0.38f * ink;
            // the split: warm in the light, cool in the shade, about the middle value
            float l = luma(p);
            float warm = Math.max(0, l - 0.5f), cool = Math.max(0, 0.5f - l);
            p[0] *= 1 + 0.10f * warm - 0.06f * cool; p[1] *= 1 + 0.04f * warm - 0.01f * cool; p[2] *= 1 - 0.08f * warm + 0.10f * cool;
            // a little richer, and the ground wash
            float m = luma(p);
            for (int ch = 0; ch < 3; ch++) p[ch] = m + (p[ch] - m) * 1.16f;
            float w = 1 + (wash[i] - 0.5f) * 0.12f;
            out[i] = new int[]{q(p[0] * dark * w), q(p[1] * dark * w), q(p[2] * dark * w), src[i][3]};
        }
        return out;
    }

    /** Kuwahara: the mean of the most even of the four (r+1) x (r+1) quadrants round each texel. */
    private static float[][] kuwahara(float[][] c, int size, int r) {
        float[][] out = new float[c.length][];
        for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) {
            float best = Float.MAX_VALUE;
            float[] mean = null;
            for (int qy = -1; qy <= 1; qy += 2) for (int qx = -1; qx <= 1; qx += 2) {
                float[] s = new float[3];
                float s2 = 0, sl = 0;
                int cnt = 0;
                for (int dy = 0; dy <= r; dy++) for (int dx = 0; dx <= r; dx++) {
                    float[] v = c[at(x + qx * dx, y + qy * dy, size)];
                    s[0] += v[0]; s[1] += v[1]; s[2] += v[2];
                    float l = luma(v); sl += l; s2 += l * l; cnt++;
                }
                float var = s2 / cnt - (sl / cnt) * (sl / cnt);
                if (var < best) { best = var; mean = new float[]{s[0] / cnt, s[1] / cnt, s[2] / cnt}; }
            }
            out[y * size + x] = mean;
        }
        return out;
    }

    /** A soft tileable wash: value noise on a 4 x 4 lattice, smoothly interpolated (0..1). */
    private static float[] wash(int size, long seed) {
        java.util.Random r = new java.util.Random(seed);
        int p = 4;
        float[] g = new float[p * p];
        for (int i = 0; i < g.length; i++) g[i] = r.nextFloat();
        float[] out = new float[size * size];
        for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) {
            float fx = x * p / (float) size, fy = y * p / (float) size;
            int x0 = (int) fx, y0 = (int) fy;
            float tx = fx - x0, ty = fy - y0;
            tx = tx * tx * (3 - 2 * tx); ty = ty * ty * (3 - 2 * ty);
            float a = g[(y0 % p) * p + x0 % p], b = g[(y0 % p) * p + (x0 + 1) % p], c = g[((y0 + 1) % p) * p + x0 % p], d = g[((y0 + 1) % p) * p + (x0 + 1) % p];
            out[y * size + x] = (a + (b - a) * tx) * (1 - ty) + (c + (d - c) * tx) * ty;
        }
        return out;
    }

    private static int at(int x, int y, int size) { return Math.floorMod(y, size) * size + Math.floorMod(x, size); }
    private static float luma(float[] v) { return 0.2126f * v[0] + 0.7152f * v[1] + 0.0722f * v[2]; }
    private static float smooth(float a, float b, float x) { float t = Math.max(0, Math.min(1, (x - a) / (b - a))); return t * t * (3 - 2 * t); }
    private static int q(float v) { return Math.max(0, Math.min(255, Math.round(v * 255))); }
}
