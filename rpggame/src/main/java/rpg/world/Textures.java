package rpg.world;

import java.util.Random;
import static rpg.world.Blocks.*;

/**
 * The world's textures (0.2.0, gamefixes: a full art-style overhaul - every terrain, block, building, plant and prop
 * surface redrawn by hand in a vibrant hand-painted fantasy style, in place of 0.1.9's procedural noise under a
 * painterly filter). Each material is its own painting, authored here stroke by stroke on a 64 x 64 canvas that wraps
 * at its edges (so every texture tiles seamlessly): big readable shapes first - stones, boards, courses, shingles, leaf
 * clusters - each lit from the upper left as one light falls on the whole world, highlights going warm and shadows cool
 * and saturated rather than grey, crevices deep but coloured, then the painted detail on top (grain, blades, cracks,
 * moss, glints). Fine high-contrast noise is avoided on purpose: at a distance a wall must read as a calm surface, not
 * shimmer.
 */
final class Textures {
    private Textures() {}
    static final int T = TEX;
    /** The light's direction across a face (upper left), as a unit vector in texel space (y down). */
    private static final float LX = -0.6f, LY = -0.8f;

    /** A 64 x 64 wrapping canvas of linear-ish RGB (0..255) and alpha. */
    static final class Canvas {
        final float[] r = new float[T * T], g = new float[T * T], b = new float[T * T], a = new float[T * T];
        final Random rnd;
        private final float[][] lattice = new float[7][];
        private static final int[] PERIODS = {2, 4, 8, 16, 32, 64, 3};
        Canvas(long seed) {
            rnd = new Random(seed);
            for (int i = 0; i < lattice.length; i++) { int p = PERIODS[i]; lattice[i] = new float[p * p]; for (int k = 0; k < p * p; k++) lattice[i][k] = rnd.nextFloat(); }
            java.util.Arrays.fill(a, 255);
        }
        static int w(int v) { return Math.floorMod(v, T); }
        float rf() { return rnd.nextFloat(); }
        float rs() { return rnd.nextFloat() * 2 - 1; }

        /** Smooth wrapping value noise at a lattice period (0..1); oct indexes PERIODS. */
        float noise(int oct, float x, float y) {
            int p = PERIODS[oct];
            float fx = x * p / T, fy = y * p / T;
            int x0 = (int) Math.floor(fx), y0 = (int) Math.floor(fy);
            float tx = fx - x0, ty = fy - y0;
            tx = tx * tx * (3 - 2 * tx); ty = ty * ty * (3 - 2 * ty);
            float[] l = lattice[oct];
            float a0 = l[Math.floorMod(y0, p) * p + Math.floorMod(x0, p)], b0 = l[Math.floorMod(y0, p) * p + Math.floorMod(x0 + 1, p)];
            float c0 = l[Math.floorMod(y0 + 1, p) * p + Math.floorMod(x0, p)], d0 = l[Math.floorMod(y0 + 1, p) * p + Math.floorMod(x0 + 1, p)];
            return (a0 + (b0 - a0) * tx) * (1 - ty) + (c0 + (d0 - c0) * tx) * ty;
        }
        /** Soft, broad variation (no fine grain). */
        float broad(float x, float y) { return noise(1, x, y) * 0.5f + noise(2, x, y) * 0.32f + noise(3, x, y) * 0.18f; }

        void fill(int rgb) { float[] c = rgb(rgb); for (int i = 0; i < T * T; i++) { r[i] = c[0]; g[i] = c[1]; b[i] = c[2]; } }
        void set(int i, float[] c, float k) { r[i] += (c[0] - r[i]) * k; g[i] += (c[1] - g[i]) * k; b[i] += (c[2] - b[i]) * k; }
        float[] at(int i) { return new float[]{r[i], g[i], b[i]}; }
        /** Lights or darkens a texel in place by k (-1..1): warm toward the light, cool and saturated toward shadow. */
        void light(int i, float k) { float[] c = shade(at(i), k); r[i] = c[0]; g[i] = c[1]; b[i] = c[2]; }

        /** Broad soft value shapes over the whole canvas (amount: how far the light swings). */
        void mottle(float amount, int seed) {
            for (int y = 0; y < T; y++) for (int x = 0; x < T; x++) light(y * T + x, (broad(x + seed * 13, y + seed * 7) - 0.5f) * 2 * amount);
        }

        /** A soft elliptical brush dab, wrapping: colour laid by alpha, feathered by softness (0 hard .. 1 all feather). */
        void dab(float cx, float cy, float rx, float ry, float ang, float[] c, float alpha, float soft) {
            float co = (float) Math.cos(ang), si = (float) Math.sin(ang), ext = Math.max(rx, ry) + 1;
            for (int y = (int) Math.floor(cy - ext); y <= (int) Math.ceil(cy + ext); y++) for (int x = (int) Math.floor(cx - ext); x <= (int) Math.ceil(cx + ext); x++) {
                float dx = x + 0.5f - cx, dy = y + 0.5f - cy;
                float u = (dx * co + dy * si) / Math.max(0.3f, rx), v = (-dx * si + dy * co) / Math.max(0.3f, ry);
                float d = (float) Math.sqrt(u * u + v * v);
                if (d >= 1) continue;
                float k = soft <= 0 ? 1 : Math.min(1, (1 - d) / soft);
                set(w(y) * T + w(x), c, k * alpha);
            }
        }
        /** A dab that only lights or darkens (k -1..1) what is under it. */
        void tone(float cx, float cy, float rx, float ry, float ang, float k, float soft) {
            float co = (float) Math.cos(ang), si = (float) Math.sin(ang), ext = Math.max(rx, ry) + 1;
            for (int y = (int) Math.floor(cy - ext); y <= (int) Math.ceil(cy + ext); y++) for (int x = (int) Math.floor(cx - ext); x <= (int) Math.ceil(cx + ext); x++) {
                float dx = x + 0.5f - cx, dy = y + 0.5f - cy;
                float u = (dx * co + dy * si) / Math.max(0.3f, rx), v = (-dx * si + dy * co) / Math.max(0.3f, ry);
                float d = (float) Math.sqrt(u * u + v * v);
                if (d >= 1) continue;
                float f = soft <= 0 ? 1 : Math.min(1, (1 - d) / soft);
                light(w(y) * T + w(x), k * f);
            }
        }
        /** A brush stroke from one point to another, its width tapering from w0 to w1. */
        void stroke(float x0, float y0, float x1, float y1, float w0, float w1, float[] c, float alpha) {
            float len = (float) Math.hypot(x1 - x0, y1 - y0);
            int n = Math.max(2, (int) (len * 1.6f));
            for (int i = 0; i <= n; i++) { float t = i / (float) n; dab(x0 + (x1 - x0) * t, y0 + (y1 - y0) * t, w0 + (w1 - w0) * t, w0 + (w1 - w0) * t, 0, c, alpha, 0.6f); }
        }
        void toneStroke(float x0, float y0, float x1, float y1, float w0, float w1, float k) {
            float len = (float) Math.hypot(x1 - x0, y1 - y0);
            int n = Math.max(2, (int) (len * 1.2f));
            for (int i = 0; i <= n; i++) { float t = i / (float) n; tone(x0 + (x1 - x0) * t, y0 + (y1 - y0) * t, w0 + (w1 - w0) * t, w0 + (w1 - w0) * t, 0, k / 1.6f, 0.8f); }
        }
        /** A wavering line (a crack, a vein, a grain line): a random walk from a point along a heading. */
        void wander(float x, float y, float ang, int steps, float step, float wobble, float width, float[] c, float alpha) {
            for (int i = 0; i < steps; i++) {
                float nx = x + (float) Math.cos(ang) * step, ny = y + (float) Math.sin(ang) * step;
                stroke(x, y, nx, ny, width, width * 0.85f, c, alpha);
                x = nx; y = ny; ang += rs() * wobble;
            }
        }

        int[][] out() {
            int[][] o = new int[T * T][];
            for (int i = 0; i < T * T; i++) o[i] = new int[]{c(r[i]), c(g[i]), c(b[i]), c(a[i])};
            return o;
        }
    }

    static int c(float v) { return Math.max(0, Math.min(255, Math.round(v))); }
    static float[] rgb(int v) { return new float[]{v >> 16 & 255, v >> 8 & 255, v & 255}; }
    static float[] mix(float[] a, float[] b, float t) { return new float[]{a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t}; }
    /**
     * The painter's light: k > 0 toward a warm, brighter version of the colour (a sunlit edge goes gold); k < 0 toward a
     * cool, deeper and more saturated one (a shadow goes violet-blue, never grey).
     */
    static float[] shade(float[] c, float k) {
        if (k >= 0) {
            float[] hi = {Math.min(255, c[0] * 1.32f + 26), Math.min(255, c[1] * 1.28f + 18), Math.min(255, c[2] * 1.12f + 4)};
            return mix(c, hi, Math.min(1, k));
        }
        float[] lo = {c[0] * 0.46f + 4, c[1] * 0.47f + 6, c[2] * 0.56f + 18};
        return mix(c, lo, Math.min(1, -k));
    }
    static float[] jitter(Canvas cv, int rgb, float amount) {
        float[] c = rgb(rgb);
        float k = cv.rs() * amount, hue = cv.rs() * amount * 18;
        c = shade(c, k);
        return new float[]{Math.max(0, Math.min(255, c[0] + hue)), c[1], Math.max(0, Math.min(255, c[2] - hue))};
    }

    // ------------------------------------------------------------------ the shared painting procedures
    /**
     * Stones laid in a field (Voronoi cells, wrapping): each stone its own colour off the base, bevelled - lit along the
     * edges that face the light, shadowed along the others - with a soft dome across it, set in grout.
     */
    static void stones(Canvas cv, int n, int base, float vary, float bevel, int grout, float groutW, float round) {
        float[][] s = new float[n][];
        float[][] col = new float[n][];
        // sites spread out evenly-ish (a jittered grid) so stones are of a size
        int side = (int) Math.ceil(Math.sqrt(n));
        for (int i = 0; i < n; i++) {
            float gx = (i % side + 0.5f) * T / side, gy = (i / side + 0.5f) * T / side;
            s[i] = new float[]{gx + cv.rs() * T / side * 0.38f, gy + cv.rs() * T / side * 0.38f};
            col[i] = jitter(cv, base, vary);
        }
        float[] gr = rgb(grout);
        for (int y = 0; y < T; y++) for (int x = 0; x < T; x++) {
            float d1 = 1e9f, d2 = 1e9f; int best = 0; float bx = 0, by = 0;
            for (int i = 0; i < n; i++) {
                float dx = x + 0.5f - s[i][0], dy = y + 0.5f - s[i][1];
                dx -= Math.round(dx / T) * T; dy -= Math.round(dy / T) * T;
                float d = dx * dx + dy * dy;
                if (d < d1) { d2 = d1; d1 = d; best = i; bx = dx; by = dy; } else if (d < d2) d2 = d;
            }
            d1 = (float) Math.sqrt(d1); d2 = (float) Math.sqrt(d2);
            float edge = (d2 - d1) * 0.5f;                       // distance to the stone's border
            int i = y * T + x;
            // warp the border a little so stones are not polygons
            edge += (cv.noise(4, x, y) - 0.5f) * 1.6f;
            if (edge < groutW) { float[] g2 = shade(gr, (cv.noise(3, x, y) - 0.5f) * 0.5f); cv.r[i] = g2[0]; cv.g[i] = g2[1]; cv.b[i] = g2[2]; continue; }
            float[] c = col[best];
            float len = Math.max(0.01f, (float) Math.hypot(bx, by));
            float facing = (bx * LX + by * LY) / len;              // >0: this side of the stone faces the light
            float k = 0;
            if (edge < groutW + bevel) { float t = 1 - (edge - groutW) / bevel; k = facing * t * 0.85f - t * 0.15f; }
            // a dome across the stone: brighter toward its lit side
            k += facing * Math.min(1, len / 14f) * round;
            k += (cv.broad(x * 2, y * 2) - 0.5f) * 0.35f;
            float[] o = shade(c, k);
            cv.r[i] = o[0]; cv.g[i] = o[1]; cv.b[i] = o[2];
        }
    }

    /** Courses of bricks (rows x cols, every other row offset by half): rounded corners, bevelled, per-brick colour, in mortar. */
    static void bricks(Canvas cv, int rows, int cols, int base, float vary, int mortar, float mw, float bevel, float chips) {
        float bh = (float) T / rows, bw = (float) T / cols;
        float[][] col = new float[rows * cols][];
        for (int i = 0; i < col.length; i++) col[i] = jitter(cv, base, vary);
        float[] mo = rgb(mortar);
        for (int y = 0; y < T; y++) for (int x = 0; x < T; x++) {
            int row = (int) (y / bh);
            float off = (row & 1) == 1 ? bw / 2 : 0;
            float xx = Math.floorMod((int) Math.floor(x + off), T) + (x + off - (float) Math.floor(x + off));
            int ci = (int) (xx / bw);
            float lx = xx - ci * bw, ly = y - row * bh;
            float ex = Math.min(lx, bw - lx), ey = Math.min(ly, bh - ly);
            // rounded corners
            float corner = 1.4f;
            float e = Math.min(ex, ey);
            if (ex < corner && ey < corner) e = corner - (float) Math.hypot(corner - ex, corner - ey);
            e += (cv.noise(4, x, y) - 0.5f) * chips * 0.6f + (cv.noise(3, x, y) - 0.5f) * chips * 0.4f;
            int i = y * T + x;
            if (e < mw) { float[] m = shade(mo, (cv.noise(4, x, y) - 0.5f) * 0.35f); cv.r[i] = m[0]; cv.g[i] = m[1]; cv.b[i] = m[2]; continue; }
            float[] c = col[(row * cols + ci) % col.length];
            float k = 0;
            if (e < mw + bevel) {
                float t = 1 - (e - mw) / bevel;
                // which edge: top/left lit, bottom/right in shadow
                boolean lit = (ey < ex ? ly < bh / 2 : lx < bw / 2);
                k = (lit ? 0.7f : -0.75f) * t;
            }
            k += (cv.broad(x * 1.5f, y * 1.5f) - 0.5f) * 0.4f + (0.5f - ly / bh) * 0.16f;
            float[] o = shade(c, k);
            cv.r[i] = o[0]; cv.g[i] = o[1]; cv.b[i] = o[2];
        }
    }

    /** Boards side by side (horizontal: lying in rows), each its own tone, grain painted along it, gaps between. */
    static void boards(Canvas cv, int count, boolean horizontal, int base, float vary, float gap, boolean nails, float knots) {
        float bw = (float) T / count;
        float[][] col = new float[count][];
        for (int i = 0; i < count; i++) col[i] = jitter(cv, base, vary);
        for (int y = 0; y < T; y++) for (int x = 0; x < T; x++) {
            float across = horizontal ? y : x, along = horizontal ? x : y;
            int bi = (int) (across / bw);
            float la = across - bi * bw;
            int i = y * T + x;
            float[] c = col[bi];
            // the grain: long soft streaks along the board
            float gx = horizontal ? x * 0.35f : x * 3f, gy = horizontal ? y * 3f : y * 0.35f;
            float grain = cv.noise(5, gx + bi * 9, gy + bi * 17) * 0.6f + cv.noise(4, gx, gy) * 0.4f;
            float k = (grain - 0.5f) * 0.55f;
            if (la < 1.2f) k += 0.45f;                                // the lit edge
            else if (la > bw - gap - 1.2f && la <= bw - gap) k -= 0.5f; // its shadowed edge
            if (la > bw - gap) { k = -0.92f; }                        // the gap
            float[] o = shade(c, k);
            cv.r[i] = o[0]; cv.g[i] = o[1]; cv.b[i] = o[2];
        }
        // knots
        for (int bi = 0; bi < count; bi++) if (cv.rf() < knots) {
            float along = cv.rf() * T, across = bi * bw + bw * (0.3f + cv.rf() * 0.4f);
            float kx = horizontal ? along : across, ky = horizontal ? across : along;
            cv.tone(kx, ky, horizontal ? 3 : 1.8f, horizontal ? 1.8f : 3, 0, -0.55f, 0.5f);
            cv.tone(kx, ky, horizontal ? 1.4f : 0.8f, horizontal ? 0.8f : 1.4f, 0, -0.7f, 0.2f);
            cv.tone(kx - 1, ky - 1, 1.2f, 1.2f, 0, 0.3f, 0.8f);
        }
        if (nails) for (int bi = 0; bi < count; bi++) for (float along : new float[]{5, T / 2f + 5}) {
            float across = bi * bw + bw / 2 - gap / 2;
            float nx = horizontal ? along : across, ny = horizontal ? across : along;
            cv.dab(nx, ny, 1.3f, 1.3f, 0, rgb(0x2C2622), 1, 0.2f);
            cv.dab(nx - 0.4f, ny - 0.4f, 0.6f, 0.6f, 0, rgb(0x9A9488), 0.9f, 0.3f);
        }
    }

    /** Leaves in clusters: dark under-leaves first, each layer lighter, lit from above; where no leaf lies, nothing (cut out). */
    static void leaves(Canvas cv, int base, float lr, float lw, int count, float hole) {
        java.util.Arrays.fill(cv.a, 0);
        float[] b = rgb(base);
        // a backing of dense dark foliage where clusters sit, leaving gaps
        for (int y = 0; y < T; y++) for (int x = 0; x < T; x++) {
            int i = y * T + x;
            float cl = cv.noise(2, x, y) * 0.6f + cv.noise(3, x, y) * 0.4f;
            if (cl > hole) { cv.a[i] = 255; float[] o = shade(b, -0.55f + (cl - hole) * 0.6f); cv.r[i] = o[0]; cv.g[i] = o[1]; cv.b[i] = o[2]; }
        }
        for (int layer = 0; layer < 3; layer++) {
            float k = -0.35f + layer * 0.42f;
            for (int n = 0; n < count; n++) {
                float x = cv.rf() * T, y = cv.rf() * T;
                float cl = cv.noise(2, x, y) * 0.6f + cv.noise(3, x, y) * 0.4f;
                if (cl < hole - 0.06f + layer * 0.05f) continue;
                float ang = (float) (cv.rf() * Math.PI);
                // the top of a cluster catches the light: lighter leaves higher up in it
                float lift = (cv.noise(3, x, y - 4) - cv.noise(3, x, y + 4)) * 1.4f;
                float[] c = shade(jitter(cv, base, 0.12f), k + lift * 0.3f);
                leaf(cv, x, y, lr * (0.8f + cv.rf() * 0.4f), lw, ang, c);
            }
        }
    }
    /** One leaf: a pointed ellipse with a lit upper edge and a midrib. */
    static void leaf(Canvas cv, float x, float y, float len, float wid, float ang, float[] c) {
        float co = (float) Math.cos(ang), si = (float) Math.sin(ang), ext = len + 1;
        for (int yy = (int) Math.floor(y - ext); yy <= (int) Math.ceil(y + ext); yy++) for (int xx = (int) Math.floor(x - ext); xx <= (int) Math.ceil(x + ext); xx++) {
            float dx = xx + 0.5f - x, dy = yy + 0.5f - y;
            float u = (dx * co + dy * si) / len, v = (-dx * si + dy * co) / wid;
            // pointed at both ends: the width narrows with |u|
            float half = 1 - u * u;
            if (half <= 0 || Math.abs(v) > half) continue;
            int i = Canvas.w(yy) * T + Canvas.w(xx);
            // lit on the side toward the light, a pale midrib, darker at the rim and toward the tip (0.2.2: it reads as a leaf)
            float k = -v * 0.35f * Math.signum(co * LY - si * LX) + (Math.abs(v) < 0.14f * half ? 0.16f : 0)
                    - (Math.abs(v) > 0.78f * half ? 0.2f : 0) - Math.max(0, u) * 0.12f;
            float[] o = shade(c, k);
            cv.r[i] = o[0]; cv.g[i] = o[1]; cv.b[i] = o[2]; cv.a[i] = 255;
        }
    }

    /** Pebbles: small stones with a lit top and a shadow beneath. */
    static void pebbles(Canvas cv, int n, int base, float rmin, float rmax) {
        for (int k = 0; k < n; k++) {
            float x = cv.rf() * T, y = cv.rf() * T, rr = rmin + cv.rf() * (rmax - rmin);
            float[] c = jitter(cv, base, 0.25f);
            cv.tone(x + 0.6f, y + rr * 0.7f, rr * 1.15f, rr * 0.7f, 0, -0.6f, 0.6f);
            cv.dab(x, y, rr, rr * 0.8f, cv.rf(), c, 1, 0.25f);
            cv.tone(x - rr * 0.3f, y - rr * 0.35f, rr * 0.55f, rr * 0.4f, 0, 0.55f, 0.7f);
        }
    }

    /** Bark: deep vertical fissures between raised plates, lit on their left faces. */
    static void bark(Canvas cv, int base, int ridges, float depth, float plateLen) {
        cv.fill(base);
        cv.mottle(0.25f, 3);
        float sp = (float) T / ridges;
        for (int y = 0; y < T; y++) for (int x = 0; x < T; x++) {
            int i = y * T + x;
            float wob = (cv.noise(2, x * 0.3f, y) - 0.5f) * sp * 0.9f + (cv.noise(4, x, y * 0.5f) - 0.5f) * 2;
            float xx = x + wob;
            float f = (xx / sp) - (float) Math.floor(xx / sp);
            // plates break up along the length
            float brk = cv.noise(3, x * 0.5f, y * (T / plateLen) / 8f);
            float k;
            if (f < 0.14f) k = -depth;                                  // the fissure
            else if (f < 0.3f) k = 0.45f * (1 - (f - 0.14f) / 0.16f);   // the lit face rising out of it
            else if (f > 0.86f) k = -0.45f * (f - 0.86f) / 0.14f;       // the far face turning into shadow
            else k = (brk - 0.5f) * 0.35f;
            if (brk > 0.78f && f > 0.2f && f < 0.8f) k -= 0.5f;          // a cross-crack between plates
            cv.light(i, k);
        }
    }

    /** The end of a log: rings from the heart out, radial checks, the bark round the rim. */
    static void endGrain(Canvas cv, int wood, int barkCol, float ringSp) {
        cv.fill(wood);
        float[] bk = rgb(barkCol);
        float cx = T / 2f, cy = T / 2f;
        for (int y = 0; y < T; y++) for (int x = 0; x < T; x++) {
            int i = y * T + x;
            float dx = x + 0.5f - cx, dy = y + 0.5f - cy;
            float d = (float) Math.hypot(dx, dy) + (cv.noise(3, x, y) - 0.5f) * 3;
            if (d > 29) { float[] o = shade(bk, d > 31 ? -0.4f : 0.15f + (cv.noise(4, x, y) - 0.5f) * 0.5f); cv.r[i] = o[0]; cv.g[i] = o[1]; cv.b[i] = o[2]; continue; }
            float ring = (float) Math.sin(d / ringSp * Math.PI * 2);
            float k = ring > 0.7f ? -0.32f : ring < -0.4f ? 0.12f : 0;
            k += (0.5f - d / 29f) * 0.25f;                               // heartwood darker toward the rim? lighter at centre
            if (d > 26) k -= 0.25f;                                        // sapwood ring under the bark
            cv.light(i, k + (cv.broad(x, y) - 0.5f) * 0.2f);
        }
        for (int k = 0; k < 4; k++) {
            double a = cv.rf() * Math.PI * 2;
            float r0 = 4 + cv.rf() * 6, r1 = 18 + cv.rf() * 9;
            cv.toneStroke(cx + (float) Math.cos(a) * r0, cy + (float) Math.sin(a) * r0, cx + (float) Math.cos(a) * r1, cy + (float) Math.sin(a) * r1, 0.9f, 0.4f, -0.8f);
        }
        cv.tone(cx, cy, 2.2f, 2.2f, 0, -0.5f, 0.5f);
    }

    /** Woven cloth: a fine over-under weave, a patterned border, a motif in the field. */
    static void carpet(Canvas cv, int field, int border, int motif) {
        cv.fill(field);
        float[] bd = rgb(border), mo = rgb(motif);
        for (int y = 0; y < T; y++) for (int x = 0; x < T; x++) {
            int i = y * T + x;
            float bx = Math.min(x, T - 1 - x), by = Math.min(y, T - 1 - y), e = Math.min(bx, by);
            if (e < 6) { cv.set(i, bd, 1); if (e > 2 && e < 4) cv.set(i, mo, 0.85f); }
            // a diamond in the middle, and its echoes
            float dx = Math.abs(x + 0.5f - T / 2f), dy = Math.abs(y + 0.5f - T / 2f);
            float dia = dx + dy;
            if (e >= 6 && (Math.abs(dia - 16) < 1.6f || dia < 5)) cv.set(i, mo, 0.9f);
            if (e >= 6 && Math.abs(dia - 22) < 1.0f) cv.set(i, bd, 0.7f);
            // the weave
            float wv = ((x + y) & 1) == 0 ? 0.08f : -0.08f;
            cv.light(i, wv + (cv.broad(x, y) - 0.5f) * 0.25f);
        }
    }

    /** Polished metal plate: brushed streaks, bevelled edges, rivets at the corners. */
    static void plate(Canvas cv, int base, boolean rivets) {
        cv.fill(base);
        for (int y = 0; y < T; y++) for (int x = 0; x < T; x++) {
            int i = y * T + x;
            float k = (cv.noise(5, x * 0.15f, y * 2) - 0.5f) * 0.25f + (0.5f - (x + y) / (2f * T)) * 0.45f;
            float e = Math.min(Math.min(x, y), Math.min(T - 1 - x, T - 1 - y));
            if (e < 3) k = (x < 3 || y < 3) && x + y < T ? 0.7f : -0.65f;
            // a broad sheen across the plate
            float sheen = 1 - Math.abs((x - y) / (float) T - 0.15f) * 4;
            if (sheen > 0) k += sheen * 0.35f;
            cv.light(i, k);
        }
        if (rivets) for (float[] p : new float[][]{{7, 7}, {T - 8, 7}, {7, T - 8}, {T - 8, T - 8}}) {
            cv.tone(p[0] + 0.8f, p[1] + 1, 2.4f, 2.4f, 0, -0.6f, 0.6f);
            cv.tone(p[0], p[1], 2, 2, 0, 0.2f, 0.3f);
            cv.tone(p[0] - 0.7f, p[1] - 0.7f, 0.9f, 0.9f, 0, 0.9f, 0.6f);
        }
    }

    /** Straw: crossing golden strands, each lit along its top. */
    static void straw(Canvas cv, int base, boolean vertical, int strands) {
        cv.fill(base);
        cv.mottle(0.3f, 5);
        float[] b = rgb(base);
        for (int k = 0; k < strands; k++) {
            float x = cv.rf() * T, y = cv.rf() * T, len = 8 + cv.rf() * 14;
            float ang = vertical ? (float) (Math.PI / 2 + cv.rs() * 0.25f) : (float) (cv.rf() * Math.PI);
            float[] c = shade(b, cv.rs() * 0.6f);
            float x1 = x + (float) Math.cos(ang) * len, y1 = y + (float) Math.sin(ang) * len;
            cv.toneStroke(x + 0.6f, y + 0.8f, x1 + 0.6f, y1 + 0.8f, 0.9f, 0.7f, -0.4f);
            cv.stroke(x, y, x1, y1, 0.75f, 0.55f, c, 0.95f);
            cv.stroke(x - 0.3f, y - 0.3f, x1 - 0.3f, y1 - 0.3f, 0.35f, 0.25f, shade(c, 0.5f), 0.7f);
        }
    }

    // ------------------------------------------------------------------ the materials, one painting each
    /** Paints one layer (not the charred twins, which Blocks burns over their wood). */
    static int[][] layer(int l) {
        Canvas cv = new Canvas(0x5EED + l * 7919L);
        switch (l) {
            case L_GRASS -> grass(cv);
            case L_GRASS_SIDE -> { dirt(cv, 0x7A5032, 10); grassLip(cv); }
            case L_DIRT -> dirt(cv, 0x7A5032, 14);
            case L_BARE -> { dirt(cv, 0x8E6741, 6); for (int k = 0; k < 6; k++) cv.wander(cv.rf() * T, cv.rf() * T, cv.rf() * 6.28f, 5, 2.2f, 0.7f, 0.45f, rgb(0x4A2E1C), 0.55f); }
            case L_SAND -> sand(cv, 0xE6CC8C, 0xC8A96A);
            case L_MUD -> mud(cv);
            case L_CLAY -> { cv.fill(0xB87E5C); cv.mottle(0.42f, 2); for (int k = 0; k < 18; k++) { float x = cv.rf() * T, y = cv.rf() * T; cv.toneStroke(x, y, x + 7 + cv.rf() * 7, y + cv.rs() * 2, 2.0f, 0.8f, cv.rf() < 0.6f ? 0.32f : -0.28f); } for (int k = 0; k < 5; k++) cv.wander(cv.rf() * T, cv.rf() * T, cv.rf() * 6.28f, 6, 2, 0.8f, 0.4f, rgb(0x6A4232), 0.6f); }
            case L_STONE -> { stones(cv, 7, 0x8E8C96, 0.22f, 4, 0x3B3546, 1.1f, 0.35f); cracks(cv, 5, 0x4A4452); }
            case L_SPENT -> { stones(cv, 7, 0x6E6A70, 0.18f, 3, 0x2E2A32, 1.1f, 0.25f); pits(cv, 9); }
            case L_COPPER -> ore(cv, 0xD9783A, 0x4FB08E, 0.0f);
            case L_TIN -> ore(cv, 0xD6DCE2, 0x9AA6B4, 0.0f);
            case L_IRON -> ore(cv, 0xB8643C, 0x5A4A48, 0.0f);
            case L_COAL -> ore(cv, 0x1E1C24, 0x6A7896, 0.0f);
            case L_BRICK -> { bricks(cv, 8, 4, 0xB24A30, 0.2f, 0x8E7C68, 0.55f, 1.6f, 0.7f); grime(cv, 0.12f); }
            case L_COBBLE -> { stones(cv, 16, 0x8C8780, 0.25f, 3.2f, 0x2F2C2A, 1.0f, 0.55f); moss(cv, 0x4E7A2A, 10, true); }
            case L_ROOF -> shingles(cv, 0xB4492E);
            case L_PLASTER -> plaster(cv);
            case L_BEAM -> { boards(cv, 1, false, 0x664226, 0.05f, 0, false, 1f); cv.mottle(0.2f, 4); grainLines(cv, false, 0x2E1C10, 12); grainLines(cv, false, 0x8A6038, 6); for (int y = 0; y < T; y++) { cv.light(y * T, 0.45f); cv.light(y * T + 1, 0.25f); cv.light(y * T + T - 1, -0.55f); cv.light(y * T + T - 2, -0.3f); } }
            case L_PLANKS -> { boards(cv, 4, true, 0xAC7642, 0.16f, 1.6f, true, 0.4f); grainLines(cv, true, 0x6E4426, 14); }
            case L_TABLE_SIDE -> { boards(cv, 2, true, 0x8E5C32, 0.1f, 1.4f, false, 0.3f); grainLines(cv, true, 0x5A3820, 10); }
            case L_TABLE_TOP -> tableTop(cv);
            case L_DOOR -> door(cv, false);
            case L_LOCKED -> door(cv, true);
            case L_BARK -> bark(cv, 0x6E4A2C, 6, 0.75f, 18);
            case L_OAK_BARK -> { bark(cv, 0x6A4A30, 5, 0.85f, 14); moss(cv, 0x587A2C, 5, false); }
            case L_WILLOW_BARK -> bark(cv, 0x7C6E58, 8, 0.6f, 30);
            case L_SPRUCE_BARK -> bark(cv, 0x5C3424, 7, 0.7f, 10);
            case L_JUNGLE_BARK -> { bark(cv, 0x6C5A3A, 6, 0.65f, 20); moss(cv, 0x4E8A30, 12, false); }
            case L_BIRCH_BARK -> birch(cv);
            case L_RINGS -> endGrain(cv, 0xC8965A, 0x6E4A2C, 3.4f);
            case L_SPRUCE_TOP -> endGrain(cv, 0xC48A5C, 0x5C3424, 2.6f);
            case L_JUNGLE_TOP -> endGrain(cv, 0xC89A60, 0x6C5A3A, 3.8f);
            case L_BIRCH_TOP -> endGrain(cv, 0xE0C896, 0xE8E2D2, 3.0f);
            // (0.2.2, gamefixes: leaves did not look like leaves) fewer, bigger leaves - each one a shape you can read at
            // a few metres: a broad pointed leaf, an oak's wide blunt one, a willow's long blade
            case L_LEAVES -> leaves(cv, 0x3E9230, 5.4f, 2.6f, 120, 0.36f);
            case L_OAK_LEAVES -> leaves(cv, 0x3C7E26, 5.8f, 3.2f, 100, 0.34f);
            case L_WILLOW_LEAVES -> leaves(cv, 0x78AE3A, 7.6f, 1.5f, 150, 0.38f);
            // the hedge: small clipped leaves packed tight on a dark body, barely a gap in it, the light on its top edge
            case L_HEDGE -> leaves(cv, 0x2F7428, 2.8f, 1.5f, 420, 0.08f);
            case L_BERRY -> berries(cv);
            case L_HERB -> herb(cv);
            case L_FLAX -> flax(cv);
            case L_FENCE -> fence(cv);
            case L_WATER -> water(cv);
            case L_GLASS -> glass(cv, 0xC8E4EC, 0xD2E6EA, 60);
            case L_WHITE_GLASS -> glass(cv, 0xF0F0EC, 0xBCB8B0, 150);
            case L_BLUE_GLASS -> glass(cv, 0x84C2EE, 0x5A86AA, 140);
            case L_LAMP -> lamp(cv);
            case L_FURNACE -> furnace(cv);
            case L_METAL -> { plate(cv, 0x5A5E66, false); for (int y = 0; y < 6; y++) for (int x = 0; x < T; x++) cv.light(y * T + x, 0.35f - y * 0.04f); }
            case L_RANGE -> range(cv);
            case L_SAW -> saw(cv);
            case L_TARGET -> target(cv);
            case L_CRATE -> crate(cv);
            case L_BOOKS -> books(cv);
            case L_RED -> banner(cv, 0xC03A30);
            case L_BLUE -> banner(cv, 0x3A5EC0);
            case L_YELLOW -> banner(cv, 0xE6B82E);
            case L_GREEN -> banner(cv, 0x3EA84A);
            case L_DEBUG -> debug(cv);
            case L_GRANITE -> granite(cv);
            case L_STONE_BRICKS -> { bricks(cv, 4, 2, 0x8C8A92, 0.16f, 0x3C3844, 0.8f, 3f, 1.0f); cracks(cv, 4, 0x4A4652); moss(cv, 0x567E2E, 6, true); }
            case L_LIMESTONE -> { bricks(cv, 4, 2, 0xDCCDA8, 0.12f, 0x8C7C62, 0.7f, 2.6f, 0.9f); grime(cv, 0.08f); }
            case L_MARBLE -> marble(cv);
            case L_SANDSTONE_TOP -> sandstoneTop(cv, 0xDCC690);
            case L_SANDSTONE -> strata(cv, 0xD6BE88);
            case L_RED_SANDSTONE_TOP -> sandstoneTop(cv, 0xC8723E);
            case L_RED_SANDSTONE -> strata(cv, 0xC06A3A);
            case L_WHITE_CARPET -> carpet(cv, 0xE8E2D4, 0xB8A888, 0x7A9AB8);
            case L_BLACK_CARPET -> carpet(cv, 0x2C2A32, 0x6A3A2A, 0xB89048);
            case L_BROWN_CARPET -> carpet(cv, 0x7A4E30, 0x3E2A1C, 0xD0A050);
            case L_RED_CARPET -> carpet(cv, 0xA8302A, 0x5A1C1E, 0xE0B050);
            case L_IRON_BLOCK -> plate(cv, 0xBCC2CA, true);
            case L_GOLD_BLOCK -> { plate(cv, 0xE8B238, true); for (int k = 0; k < 6; k++) cv.tone(cv.rf() * T, cv.rf() * T, 3, 1.2f, 0.6f, 0.5f, 0.8f); }
            case L_COPPER_BLOCK -> { plate(cv, 0xC8723E, true); moss(cv, 0x4FA88A, 7, false); }
            case L_COAL_BLOCK -> { stones(cv, 18, 0x24222A, 0.25f, 2.6f, 0x0E0C10, 0.9f, 0.6f); glints(cv, 18, 0x8A9AC0); }
            case L_HAY_TOP -> { straw(cv, 0xD6AE48, false, 260); }
            case L_HAY_SIDE -> { straw(cv, 0xD2AA46, true, 240); for (float by : new float[]{16, 46}) for (int x = 0; x < T; x++) { cv.dab(x, by, 0.6f, 1.6f, 0, rgb(0x8A5E2A), 1, 0.3f); cv.light(Canvas.w((int) by - 1) * T + x, 0.3f); } }
            default -> { cv.fill(0xFF00FF); }
        }
        return cv.out();
    }

    // ---- terrain
    static void grass(Canvas cv) {
        cv.fill(0x4C9628);
        cv.mottle(0.5f, 1);
        // patches of sunlit and shaded turf
        for (int k = 0; k < 10; k++) cv.dab(cv.rf() * T, cv.rf() * T, 6 + cv.rf() * 8, 5 + cv.rf() * 6, cv.rf() * 3, shade(rgb(0x78B634), 0.25f), 0.45f, 0.9f);
        for (int k = 0; k < 8; k++) cv.dab(cv.rf() * T, cv.rf() * T, 5 + cv.rf() * 7, 4 + cv.rf() * 5, cv.rf() * 3, rgb(0x2E6A2C), 0.45f, 0.9f);
        // blades: a dark root and a bright tip, leaning a little
        for (int k = 0; k < 170; k++) {
            float x = cv.rf() * T, y = cv.rf() * T, len = 3f + cv.rf() * 4f, lean = cv.rs() * 1.6f;
            cv.toneStroke(x + 0.4f, y + 0.8f, x + lean + 0.4f, y - len + 0.8f, 0.8f, 0.3f, -0.5f);
            float[] tip = shade(rgb(0x86C23E), 0.1f + cv.rf() * 0.45f);
            cv.stroke(x, y, x + lean, y - len, 0.55f, 0.25f, tip, 0.85f);
        }
        // a rare flower
        for (int k = 0; k < 3; k++) { float x = cv.rf() * T, y = cv.rf() * T; cv.dab(x, y, 1.1f, 1.1f, 0, rgb(k == 0 ? 0xF4E070 : 0xF2F0E6), 1, 0.3f); cv.dab(x, y, 0.4f, 0.4f, 0, rgb(0xE0A030), 1, 0.2f); }
    }
    /** The grass hanging over the top of an earth face: a scalloped lip, lit along its crown, casting a soft shadow down. */
    static void grassLip(Canvas cv) {
        float[] gr = rgb(0x5C9E2C);
        for (int x = 0; x < T; x++) {
            float depth = 9 + cv.noise(3, x, 0) * 6 + (float) Math.abs(Math.sin(x * 0.55 + cv.noise(4, x, 3) * 3)) * 4;
            for (int y = 0; y < T; y++) {
                int i = y * T + x;
                if (y < depth) {
                    float k = (y < 2 ? 0.45f : 0.1f) - y / depth * 0.45f + (cv.noise(5, x, y) - 0.5f) * 0.3f;
                    float[] o = shade(gr, k);
                    cv.r[i] = o[0]; cv.g[i] = o[1]; cv.b[i] = o[2];
                } else if (y < depth + 3) cv.light(i, -0.55f * (1 - (y - depth) / 3));
            }
        }
        for (int k = 0; k < 40; k++) { float x = cv.rf() * T, y = 2 + cv.rf() * 8; cv.stroke(x, y, x + cv.rs(), y - 2 - cv.rf() * 2, 0.5f, 0.25f, rgb(0x96CC4A), 0.8f); }
    }
    static void dirt(Canvas cv, int base, int stones) {
        cv.fill(base);
        cv.mottle(0.35f, 2);
        for (int k = 0; k < 9; k++) cv.dab(cv.rf() * T, cv.rf() * T, 4 + cv.rf() * 6, 3 + cv.rf() * 4, cv.rf() * 3, shade(rgb(base), -0.4f), 0.3f, 0.9f);
        for (int k = 0; k < 7; k++) cv.dab(cv.rf() * T, cv.rf() * T, 3 + cv.rf() * 5, 2 + cv.rf() * 3, cv.rf() * 3, shade(rgb(base), 0.3f), 0.25f, 0.9f);
        pebbles(cv, stones / 2, 0xA08466, 1.4f, 3.0f);
        for (int k = 0; k < 4; k++) cv.wander(cv.rf() * T, cv.rf() * T, cv.rf() * 6.28f, 4, 2.4f, 0.6f, 0.35f, rgb(0x4A2C18), 0.5f);
    }
    static void sand(Canvas cv, int base, int ripple) {
        cv.fill(base);
        cv.mottle(0.22f, 4);
        for (int y = 0; y < T; y++) for (int x = 0; x < T; x++) {
            float wv = (float) Math.sin((y + (cv.noise(2, x, y) - 0.5f) * 10 + (cv.noise(1, x, y) - 0.5f) * 8) * Math.PI * 2 / 10.667f);
            cv.light(y * T + x, wv * (wv > 0 ? 0.16f : 0.11f));
        }
        pebbles(cv, 5, 0xB8A890, 0.6f, 1.2f);
    }
    static void mud(Canvas cv) {
        cv.fill(0x5C3F28);
        cv.mottle(0.3f, 6);
        // puddles: dark wet hollows with a cool sky sheen and a lit far rim
        for (int k = 0; k < 4; k++) {
            float x = cv.rf() * T, y = cv.rf() * T, rx = 5 + cv.rf() * 6, ry = 3 + cv.rf() * 3;
            cv.dab(x, y, rx, ry, 0, rgb(0x4A3828), 0.55f, 0.6f);
            cv.dab(x - rx * 0.2f, y - ry * 0.3f, rx * 0.6f, ry * 0.4f, 0, rgb(0x8A94A4), 0.4f, 0.9f);
            cv.tone(x + rx * 0.2f, y + ry * 0.9f, rx * 0.8f, 1.2f, 0, 0.3f, 0.8f);
        }
        for (int k = 0; k < 10; k++) { float x = cv.rf() * T, y = cv.rf() * T; cv.toneStroke(x, y, x + 5 + cv.rf() * 5, y + cv.rs(), 1.0f, 0.5f, 0.25f); }
        pebbles(cv, 4, 0x7A6A5A, 0.8f, 1.6f);
    }

    // ---- stone and masonry details
    static void cracks(Canvas cv, int n, int col) { for (int k = 0; k < n; k++) cv.wander(cv.rf() * T, cv.rf() * T, cv.rf() * 6.28f, 3 + (int) (cv.rf() * 3), 2.2f, 0.9f, 0.4f, rgb(col), 0.7f); }
    static void grime(Canvas cv, float amount) { for (int y = 0; y < T; y++) for (int x = 0; x < T; x++) { float n = cv.noise(2, x, y); if (n < 0.35f) cv.light(y * T + x, -(0.35f - n) * amount * 6); } }
    static void pits(Canvas cv, int n) {
        for (int k = 0; k < n; k++) {
            float x = cv.rf() * T, y = cv.rf() * T, r = 1.5f + cv.rf() * 2;
            cv.dab(x, y, r, r * 0.8f, 0, rgb(0x1E1A20), 0.95f, 0.3f);
            cv.tone(x + r * 0.4f, y + r * 0.6f, r * 0.6f, r * 0.4f, 0, 0.35f, 0.7f);
            cv.dab(x, y + r + 1.5f, 0.8f, 2, 0, rgb(0x8A4A2A), 0.35f, 0.8f);   // a rust stain run down below
        }
    }
    /** Moss in the hollows: where the surface is low (or anywhere, for bark), soft green tufts. */
    static void moss(Canvas cv, int col, int clumps, boolean lowOnly) {
        float[] m = rgb(col);
        for (int k = 0; k < clumps; k++) {
            float x = cv.rf() * T, y = cv.rf() * T;
            if (lowOnly) {
                // look for a dark (grout) spot nearby
                int best = -1; float dark = 999;
                for (int t = 0; t < 12; t++) { int xx = Canvas.w((int) (x + cv.rs() * 4)), yy = Canvas.w((int) (y + cv.rs() * 4)); float l = cv.r[yy * T + xx] + cv.g[yy * T + xx]; if (l < dark) { dark = l; best = yy * T + xx; } }
                x = best % T; y = best / T;
            }
            for (int t = 0; t < 6; t++) cv.dab(x + cv.rs() * 2.5f, y + cv.rs() * 1.5f, 1 + cv.rf() * 1.4f, 0.8f + cv.rf(), 0, shade(m, cv.rs() * 0.4f), 0.85f, 0.5f);
            cv.dab(x - 0.6f, y - 0.8f, 0.9f, 0.6f, 0, shade(m, 0.5f), 0.7f, 0.6f);
        }
    }
    static void glints(Canvas cv, int n, int col) { for (int k = 0; k < n; k++) { float x = cv.rf() * T, y = cv.rf() * T; cv.dab(x, y, 0.9f, 0.5f, 0.7f, rgb(col), 0.9f, 0.5f); cv.dab(x, y, 0.35f, 0.35f, 0, rgb(0xF0F4FF), 1, 0.2f); } }
    /** Stone holding ore: clusters of nuggets that stand out of the rock, lit, with a coloured bloom round them. */
    static void ore(Canvas cv, int nugget, int accent, float unused) {
        stones(cv, 7, 0x84828C, 0.18f, 3.5f, 0x37323F, 1.1f, 0.3f);
        float[] n = rgb(nugget), ac = rgb(accent);
        for (int k = 0; k < 6; k++) {
            float cx = cv.rf() * T, cy = cv.rf() * T;
            cv.dab(cx, cy, 6, 5, 0, ac, 0.22f, 1);
            for (int j = 0; j < 4; j++) {
                float x = cx + cv.rs() * 4, y = cy + cv.rs() * 4, r = 1.3f + cv.rf() * 1.6f;
                cv.tone(x + 0.7f, y + r * 0.8f, r * 1.1f, r * 0.7f, 0, -0.65f, 0.5f);
                cv.dab(x, y, r, r * 0.85f, cv.rf() * 3, shade(n, cv.rs() * 0.25f), 1, 0.15f);
                cv.dab(x - r * 0.3f, y - r * 0.35f, r * 0.45f, r * 0.35f, 0, shade(n, 0.75f), 0.95f, 0.4f);
                if (cv.rf() < 0.5f) cv.dab(x + r * 0.4f, y + r * 0.2f, r * 0.4f, r * 0.3f, 0, ac, 0.8f, 0.5f);
            }
        }
    }
    static void shingles(Canvas cv, int base) {
        int rows = 8; float rh = (float) T / rows, sw = 8;
        float[][] col = new float[rows * 8][];
        for (int i = 0; i < col.length; i++) col[i] = jitter(cv, base, 0.2f);
        for (int y = 0; y < T; y++) for (int x = 0; x < T; x++) {
            int row = (int) (y / rh);
            float off = (row & 1) == 1 ? sw / 2 : 0;
            float xx = Math.floorMod((int) (x + off), T);
            int ci = (int) (xx / sw);
            float lx = xx - ci * sw, ly = y - row * rh;
            // a rounded tile bottom: the scallop
            float cx = (lx - sw / 2) / (sw / 2), bottom = rh - 0.6f - 3.6f * cx * cx;
            int i = y * T + x;
            float[] c = col[(row * 8 + ci) % col.length];
            float k = 0.4f - ly / rh * 0.7f - cx * 0.18f;     // each tile lit at its top and left, deep at its lip
            if (ly > bottom) k = -0.9f + (ly - bottom) * 0.12f; // the tile below shows in shadow under the lip
            else if (ly > bottom - 1.2f) k += 0.35f;            // the lip's rounded edge catching the light
            k += (cv.noise(4, x, y) - 0.5f) * 0.2f;
            float[] o = shade(c, k);
            cv.r[i] = o[0]; cv.g[i] = o[1]; cv.b[i] = o[2];
        }
    }
    static void plaster(Canvas cv) {
        cv.fill(0xE0C9A0);
        cv.mottle(0.2f, 7);
        for (int k = 0; k < 18; k++) { float x = cv.rf() * T, y = cv.rf() * T, a = cv.rs() * 0.6f; cv.toneStroke(x, y, x + (float) Math.cos(a) * 12, y + (float) Math.sin(a) * 12, 2.8f, 1.4f, cv.rf() < 0.5f ? 0.22f : -0.18f); }
        for (int k = 0; k < 3; k++) cv.wander(cv.rf() * T, cv.rf() * T, cv.rf() * 6.28f, 4, 2, 0.9f, 0.3f, rgb(0x9A8466), 0.6f);
    }
    static void grainLines(Canvas cv, boolean horizontal, int col, int n) {
        for (int k = 0; k < n; k++) {
            float x = cv.rf() * T, y = cv.rf() * T;
            cv.wander(x, y, horizontal ? 0 : (float) Math.PI / 2, 10, 2.6f, 0.12f, 0.4f, rgb(col), 0.5f);
        }
    }
    static void tableTop(Canvas cv) {
        boards(cv, 3, true, 0xA06C3C, 0.1f, 1.2f, false, 0.3f);
        grainLines(cv, true, 0x6A4222, 10);
        for (int y = 0; y < T; y++) for (int x = 0; x < T; x++) {
            float e = Math.min(Math.min(x, y), Math.min(T - 1 - x, T - 1 - y));
            int i = y * T + x;
            if (e < 4) cv.light(i, e < 1 ? -0.6f : e < 2 ? 0.45f : -0.15f);
            // polish: a soft sheen across the top
            float sheen = 1 - Math.abs((x + y) / (float) T - 0.8f) * 2.2f;
            if (sheen > 0) cv.light(i, sheen * 0.25f);
        }
    }
    static void door(Canvas cv, boolean locked) {
        boards(cv, 3, false, locked ? 0x74462A : 0x8C5832, 0.12f, 1.4f, false, 0.4f);
        grainLines(cv, false, 0x4E301A, 9);
        float[] iron = rgb(0x34303A);
        if (locked) for (float by : new float[]{12, 50}) {
            for (int x = 0; x < T; x++) for (int y = (int) by - 2; y <= by + 2; y++) { int i = Canvas.w(y) * T + x; cv.set(i, iron, 1); cv.light(i, y == by - 2 ? 0.5f : y == by + 2 ? -0.5f : 0); }
            for (float rx = 6; rx < T; rx += 12) { cv.dab(rx, by, 1.2f, 1.2f, 0, rgb(0x6A6670), 1, 0.3f); cv.dab(rx - 0.4f, by - 0.4f, 0.5f, 0.5f, 0, rgb(0xB0ACB4), 1, 0.3f); }
        }
        // the handle (a ring) and, locked, a brass plate with its keyhole
        float hx = 50, hy = 34;
        if (locked) { cv.dab(hx, hy, 3.4f, 5, 0, rgb(0xC89A3A), 1, 0.2f); cv.tone(hx - 1, hy - 2, 1.6f, 2.4f, 0, 0.5f, 0.6f); cv.dab(hx, hy + 0.5f, 0.8f, 1.8f, 0, rgb(0x1A1410), 1, 0.2f); }
        else for (int k = 0; k < 20; k++) { double a = k / 20.0 * Math.PI * 2; cv.dab(hx + (float) Math.cos(a) * 2.6f, hy + 2 + (float) Math.sin(a) * 2.6f, 0.7f, 0.7f, 0, shade(iron, (float) -Math.sin(a) * 0.6f), 1, 0.3f); }
    }
    static void birch(Canvas cv) {
        cv.fill(0xE8E2D2);
        cv.mottle(0.18f, 8);
        for (int y = 0; y < T; y++) for (int x = 0; x < T; x++) cv.light(y * T + x, (float) Math.sin(x / (float) T * Math.PI * 2) * 0.12f);
        // the black lenticels and the dark scars
        for (int k = 0; k < 26; k++) { float x = cv.rf() * T, y = cv.rf() * T, len = 2 + cv.rf() * 6; cv.stroke(x, y, x + len, y + cv.rs() * 0.4f, 0.6f + cv.rf() * 0.5f, 0.3f, rgb(0x2A2622), 0.9f); cv.toneStroke(x, y - 1, x + len, y - 1, 0.4f, 0.3f, 0.3f); }
        for (int k = 0; k < 3; k++) { float x = cv.rf() * T, y = cv.rf() * T; cv.dab(x, y, 4, 2.4f, 0, rgb(0x3A3430), 0.9f, 0.4f); cv.dab(x, y - 3, 2, 3, 0, rgb(0x3A3430), 0.7f, 0.6f); }
        for (int k = 0; k < 5; k++) { float x = cv.rf() * T, y = cv.rf() * T; cv.dab(x, y, 4, 1.4f, 0, rgb(0xC8A87A), 0.35f, 0.8f); }
    }

    // ---- plants (cut out)
    static void berries(Canvas cv) {
        leaves(cv, 0x3A8030, 3.0f, 1.8f, 260, 0.3f);
        for (int k = 0; k < 9; k++) {
            float cx = cv.rf() * T, cy = cv.rf() * T;
            for (int j = 0; j < 4; j++) {
                float x = cx + cv.rs() * 2.6f, y = cy + cv.rs() * 2.6f;
                int i = Canvas.w((int) y) * T + Canvas.w((int) x);
                if (cv.a[i] == 0) continue;
                cv.dab(x, y, 1.5f, 1.5f, 0, rgb(0xC81E32), 1, 0.15f);
                cv.dab(x + 0.5f, y + 0.6f, 0.9f, 0.7f, 0, rgb(0x781020), 0.7f, 0.5f);
                cv.dab(x - 0.5f, y - 0.5f, 0.5f, 0.5f, 0, rgb(0xFFD0D0), 1, 0.3f);
            }
        }
    }
    static void herb(Canvas cv) {
        java.util.Arrays.fill(cv.a, 0);
        for (int s = 0; s < 6; s++) {
            float x = 6 + s * 10 + cv.rs() * 2, top = 14 + cv.rf() * 16, lean = cv.rs() * 3;
            cv.stroke(x, T, x + lean, top, 1.2f, 0.6f, rgb(0x3E7A28), 1);
            for (float y = T - 8; y > top + 2; y -= 7) {
                float side = ((int) y / 7) % 2 == 0 ? 1 : -1, px = x + lean * (T - y) / (T - top);
                leaf(cv, px + side * 3.4f, y - 1.5f, 3.6f, 1.7f, side > 0 ? -0.5f : 3.64f, shade(rgb(0x5AA436), 0.15f));
            }
            leaf(cv, x + lean, top - 1, 2.6f, 1.4f, (float) -Math.PI / 2, shade(rgb(0x7CC04A), 0.25f));
        }
        markStems(cv);
    }
    static void flax(Canvas cv) {
        java.util.Arrays.fill(cv.a, 0);
        for (int s = 0; s < 7; s++) {
            float x = 4 + s * 9 + cv.rs() * 2, top = 8 + cv.rf() * 14, lean = cv.rs() * 3;
            cv.stroke(x, T, x + lean, top + 2, 0.8f, 0.5f, rgb(0x8AA050), 1);
            float fx = x + lean, fy = top;
            for (int p = 0; p < 5; p++) { double a = p / 5.0 * Math.PI * 2; leaf(cv, fx + (float) Math.cos(a) * 1.8f, fy + (float) Math.sin(a) * 1.8f, 1.9f, 1.2f, (float) a, shade(rgb(0x6E8CE6), 0.1f)); }
            cv.dab(fx, fy, 0.8f, 0.8f, 0, rgb(0xF2E070), 1, 0.2f);
        }
        markStems(cv);
    }
    /** Marks every painted texel of a cut-out plant as solid (strokes and dabs paint colour only). */
    static void markStems(Canvas cv) { for (int i = 0; i < T * T; i++) if (cv.a[i] == 0 && (cv.r[i] + cv.g[i] + cv.b[i]) > 1) cv.a[i] = 255; }
    static void fence(Canvas cv) {
        java.util.Arrays.fill(cv.a, 0);
        float[] wood = rgb(0x8E5E34);
        for (int y = 0; y < T; y++) for (int x = 0; x < T; x++) {
            boolean post = x < 8 || x >= T - 8, rail = (y >= 14 && y < 22) || (y >= 40 && y < 48);
            if (!post && !rail) continue;
            int i = y * T + x;
            float lx = post ? (x < 8 ? x : x - (T - 8)) : 0, ly = rail ? (y >= 40 ? y - 40 : y - 14) : 0;
            float k = (cv.noise(5, post ? x * 3 : x * 0.3f, post ? y * 0.3f : y * 3) - 0.5f) * 0.5f;
            if (post) k += lx < 1.5f ? 0.45f : lx > 6 ? -0.5f : 0;
            else k += ly < 1.5f ? 0.45f : ly > 6 ? -0.5f : 0;
            float[] o = shade(wood, k);
            cv.r[i] = o[0]; cv.g[i] = o[1]; cv.b[i] = o[2]; cv.a[i] = 255;
        }
    }

    // ---- water, glass, light
    static void water(Canvas cv) {
        cv.fill(0x2A86AE);
        cv.mottle(0.3f, 9);
        for (int y = 0; y < T; y++) for (int x = 0; x < T; x++) {
            float wv = (float) Math.sin((x * 0.6f + y + (cv.noise(2, x, y) - 0.5f) * 18) * Math.PI * 2 / 16);
            int i = y * T + x;
            cv.light(i, wv > 0.82f ? 0.5f : wv < -0.5f ? -0.25f : 0);
            cv.a[i] = 172;
        }
        for (int k = 0; k < 10; k++) { float x = cv.rf() * T, y = cv.rf() * T; cv.stroke(x, y, x + 3 + cv.rf() * 4, y - 1, 0.5f, 0.3f, rgb(0xC8F0F4), 0.75f); }
    }
    static void glass(Canvas cv, int tint, int frame, int clear) {
        cv.fill(tint);
        float[] fr = rgb(frame);
        for (int y = 0; y < T; y++) for (int x = 0; x < T; x++) {
            int i = y * T + x;
            float e = Math.min(Math.min(x, y), Math.min(T - 1 - x, T - 1 - y));
            if (e < 3) { cv.set(i, fr, 1); cv.light(i, (x < 3 || y < 3) && x + y < T ? 0.4f : -0.4f); cv.a[i] = 240; continue; }
            cv.a[i] = clear;
            float d = x + y;
            if (d > 14 && d < 20 || d > 24 && d < 26) { cv.light(i, 0.7f); cv.a[i] = clear + 80; }
            if (x - y > 26 && x - y < 29) { cv.light(i, 0.45f); cv.a[i] = clear + 45; }
            cv.light(i, (cv.broad(x, y) - 0.5f) * 0.15f);
        }
    }
    static void lamp(Canvas cv) {
        float[] iron = rgb(0x26222A);
        for (int y = 0; y < T; y++) for (int x = 0; x < T; x++) {
            int i = y * T + x;
            float e = Math.min(Math.min(x, y), Math.min(T - 1 - x, T - 1 - y));
            boolean frame = e < 5 || Math.abs(x - T / 2f + 0.5f) < 3 || Math.abs(y - T / 2f + 0.5f) < 3;
            if (frame) { cv.r[i] = iron[0]; cv.g[i] = iron[1]; cv.b[i] = iron[2]; cv.light(i, e < 1.5f ? -0.3f : (x + y) % 9 == 0 ? 0.3f : 0.1f); continue; }
            // a pane: brightest at the flame (the middle), warm amber toward the frame
            float px = (x % 32) - 16, py = (y % 32) - 16, d = (float) Math.hypot(Math.abs(px) < 16 ? (x < 32 ? x - 32 : x - 32) : 0, y - 32) / 32;
            float[] c = mix(rgb(0xFFF4C8), rgb(0xE89A38), Math.min(1, d * 1.2f));
            cv.r[i] = c[0]; cv.g[i] = c[1]; cv.b[i] = c[2];
        }
    }
    static void furnace(Canvas cv) {
        bricks(cv, 6, 3, 0x7A7480, 0.18f, 0x2E2A32, 0.7f, 2.4f, 0.8f);
        // the mouth: an arch of fire, brightest deep in it
        for (int y = 0; y < T; y++) for (int x = 0; x < T; x++) {
            float dx = (x + 0.5f - 32) / 16f, dy = (y + 0.5f - 44) / 18f;
            boolean in = y >= 30 && y < 58 && Math.abs(dx) < 1 && (y > 44 || dx * dx + ((y - 44) / 14f) * ((y - 44) / 14f) < 1);
            int i = y * T + x;
            if (in) {
                float depth = Math.min(1, (float) Math.hypot(dx, (y - 50) / 10f));
                float[] c = mix(rgb(0xFFE890), rgb(0xD84A10), depth);
                c = mix(c, rgb(0x5A1408), Math.max(0, depth - 0.75f) * 3);
                cv.r[i] = c[0]; cv.g[i] = c[1]; cv.b[i] = c[2];
            } else if (y >= 28 && y < 60 && Math.abs(dx) < 1.18f) cv.light(i, -0.4f);
        }
    }
    static void range(Canvas cv) {
        plate(cv, 0x3E3A42, false);
        for (float[] p : new float[][]{{18, 18}, {46, 46}}) for (int y = 0; y < T; y++) for (int x = 0; x < T; x++) {
            float d = (float) Math.hypot(x + 0.5f - p[0], y + 0.5f - p[1]);
            int i = y * T + x;
            if (Math.abs(d - 10) < 2.2f) cv.light(i, (y < p[1] ? 0.5f : -0.6f) * (1 - Math.abs(d - 10) / 2.2f));
            if (d < 8) cv.light(i, -0.45f);
            if (Math.abs(d - 5) < 0.8f) cv.light(i, 0.3f);
        }
    }
    static void saw(Canvas cv) {
        tableTop(cv);
        float[] steel = rgb(0xBCC0C8);
        for (int y = 0; y < T; y++) for (int x = 0; x < T; x++) {
            float d = (x - y) / 1.414f;
            int i = y * T + x;
            if (Math.abs(d) < 2.5f) { cv.set(i, steel, 1); cv.light(i, d < -1 ? 0.5f : d > 1.2f ? -0.4f : 0); }
            else if (d > 2.5f && d < 4 && ((x + y) % 4) < 2) { cv.set(i, steel, 1); cv.light(i, -0.2f); }
        }
    }
    static void target(Canvas cv) {
        straw(cv, 0xD8BC78, false, 120);
        float[][] rings = {{30, 0xF2EEE2}, {24, 0xC8322A}, {18, 0xF2EEE2}, {12, 0xC8322A}, {6, 0xE8C030}};
        for (float[] r : rings) for (int y = 0; y < T; y++) for (int x = 0; x < T; x++) {
            float d = (float) Math.hypot(x + 0.5f - 32, y + 0.5f - 32);
            if (d < r[0]) { int i = y * T + x; cv.set(i, rgb((int) r[1]), 0.92f); cv.light(i, (r[0] - d < 1.2f ? -0.3f : 0) + (32 - y) / 200f); }
        }
    }
    static void crate(Canvas cv) {
        boards(cv, 4, true, 0xB07A44, 0.15f, 1.4f, false, 0.3f);
        float[] fr = rgb(0x7A5030);
        for (int y = 0; y < T; y++) for (int x = 0; x < T; x++) {
            int i = y * T + x;
            float e = Math.min(Math.min(x, y), Math.min(T - 1 - x, T - 1 - y));
            float diag = Math.abs(x - (T - 1 - y)) / 1.414f;
            if (e < 6) { cv.set(i, fr, 1); cv.light(i, e < 1 ? -0.6f : e < 2 ? 0.45f : e > 5 ? -0.35f : (cv.noise(5, x, y) - 0.5f) * 0.4f); }
            else if (diag < 3.5f) { cv.set(i, fr, 1); cv.light(i, diag > 2.6f ? -0.4f : diag < 1 ? 0.3f : 0); }
        }
        for (float[] p : new float[][]{{3, 3}, {T - 4, 3}, {3, T - 4}, {T - 4, T - 4}}) { cv.dab(p[0], p[1], 1.2f, 1.2f, 0, rgb(0x2A2422), 1, 0.2f); cv.dab(p[0] - 0.4f, p[1] - 0.4f, 0.5f, 0.5f, 0, rgb(0xA09A90), 1, 0.3f); }
    }
    static void books(Canvas cv) {
        cv.fill(0x5A3820);
        int[] spines = {0x8A2A26, 0x2A4A7A, 0x3A6A34, 0x8A6A2A, 0x5A2A5A, 0x2A5A5A, 0x9A4A22, 0x3A3A44};
        for (int shelf = 0; shelf < 2; shelf++) {
            float top = shelf * 32 + 3, bottom = shelf * 32 + 28;
            float x = 1;
            while (x < T - 1) {
                float wdt = 3 + cv.rf() * 4, h = 18 + cv.rf() * 7;
                float[] c = jitter(cv, spines[(int) (cv.rf() * spines.length)], 0.15f);
                for (int y = (int) (bottom - h); y < bottom; y++) for (int xx = (int) x; xx < Math.min(T - 1, x + wdt); xx++) {
                    int i = y * T + xx;
                    float lx = xx - x;
                    float k = lx < 1 ? 0.45f : lx > wdt - 1.2f ? -0.5f : 0;
                    if (Math.abs(y - (bottom - h + 4)) < 1 || Math.abs(y - (bottom - 5)) < 1) { cv.set(i, rgb(0xD8B860), 1); k = 0.1f; }
                    else { cv.r[i] = c[0]; cv.g[i] = c[1]; cv.b[i] = c[2]; }
                    cv.light(i, k);
                }
                x += wdt + 0.5f;
            }
            for (int y = (int) bottom; y < bottom + 4; y++) for (int xx = 0; xx < T; xx++) { int i = Canvas.w(y) * T + xx; cv.set(i, rgb(0x8A5A32), 1); cv.light(i, y == (int) bottom ? 0.5f : y > bottom + 2 ? -0.5f : 0); }
            for (int y = (int) (shelf * 32); y < top; y++) for (int xx = 0; xx < T; xx++) cv.light(y * T + xx, -0.4f);
        }
    }
    static void banner(Canvas cv, int col) {
        cv.fill(col);
        for (int y = 0; y < T; y++) for (int x = 0; x < T; x++) {
            int i = y * T + x;
            float e = Math.min(Math.min(x, y), Math.min(T - 1 - x, T - 1 - y));
            float fold = (float) Math.sin(x / (float) T * Math.PI * 4) * 0.22f;
            cv.light(i, fold + (cv.broad(x, y) - 0.5f) * 0.2f + (e < 3 ? -0.4f : e < 5 && (x + y) % 3 == 0 ? 0.35f : 0));
        }
    }
    static void debug(Canvas cv) {
        cv.fill(0x3A3E48);
        for (int y = 0; y < T; y++) for (int x = 0; x < T; x++) { int i = y * T + x; if (x % 16 == 0 || y % 16 == 0) cv.set(i, rgb(0xE6C040), 0.9f); else cv.light(i, (cv.broad(x, y) - 0.5f) * 0.2f); }
    }
    static void granite(Canvas cv) {
        cv.fill(0xA8786A);
        cv.mottle(0.25f, 10);
        for (int k = 0; k < 85; k++) { float x = cv.rf() * T, y = cv.rf() * T; int[] cols = {0x3A2E30, 0xEADCCC, 0x7A4A42, 0xD0A090}; cv.dab(x, y, 1.0f + cv.rf() * 1.4f, 0.7f + cv.rf() * 0.9f, cv.rf() * 3, rgb(cols[(int) (cv.rf() * 4)]), 0.7f, 0.5f); }
        for (int y = 0; y < T; y++) for (int x = 0; x < T; x++) { float sheen = 1 - Math.abs((x + y) / (float) T - 0.7f) * 2.5f; if (sheen > 0) cv.light(y * T + x, sheen * 0.3f); }
    }
    static void marble(Canvas cv) {
        cv.fill(0xEEEAE2);
        cv.mottle(0.2f, 11);
        for (int k = 0; k < 4; k++) { float x = cv.rf() * T, y = cv.rf() * T, a = (float) (cv.rf() * 6.28); cv.wander(x, y, a, 16, 3.4f, 0.4f, 2.4f, rgb(0xC4CAD8), 0.35f); cv.wander(x, y, a, 16, 3.4f, 0.4f, 0.8f, rgb(0x7E88A0), 0.6f); }
        for (int k = 0; k < 6; k++) cv.wander(cv.rf() * T, cv.rf() * T, (float) (cv.rf() * 6.28), 8, 2.2f, 0.7f, 0.45f, rgb(0xB0A088), 0.5f);
        for (int y = 0; y < T; y++) for (int x = 0; x < T; x++) { float sheen = 1 - Math.abs((x + y) / (float) T - 0.6f) * 2.2f; if (sheen > 0) cv.light(y * T + x, sheen * 0.25f); }
    }
    static void sandstoneTop(Canvas cv, int base) {
        cv.fill(base);
        cv.mottle(0.32f, 12);
        for (int k = 0; k < 8; k++) cv.dab(cv.rf() * T, cv.rf() * T, 4 + cv.rf() * 6, 3 + cv.rf() * 4, cv.rf() * 3, shade(rgb(base), cv.rf() < 0.5f ? 0.3f : -0.3f), 0.35f, 0.9f);
        for (int k = 0; k < 6; k++) cv.wander(cv.rf() * T, cv.rf() * T, (float) (cv.rf() * 6.28), 5, 2.4f, 0.6f, 0.35f, shade(rgb(base), -0.5f), 0.6f);
        pebbles(cv, 5, base, 0.8f, 1.6f);
    }
    static void strata(Canvas cv, int base) {
        cv.fill(base);
        for (int y = 0; y < T; y++) for (int x = 0; x < T; x++) {
            float yy = y + (cv.noise(2, x, y) - 0.5f) * 5;
            float band = yy % 16;
            int i = y * T + x;
            float k = band < 1.4f ? -0.55f : band < 3 ? 0.35f : (band > 9 && band < 10) ? -0.2f : 0;
            k += (float) Math.sin(yy * 0.9f) * 0.05f + (cv.broad(x, y) - 0.5f) * 0.25f;
            cv.light(i, k);
        }
        cracks(cv, 3, 0x6A4A30);
    }
}
