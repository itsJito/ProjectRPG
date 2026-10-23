package rpg.render;

import java.util.Arrays;
import rpg.actor.Body;
import rpg.render.Skin.Part;

/**
 * A human figure's geometry, built from its skin: each body segment is the part's box with every face carrying the
 * skin's texels (merged into as few quads as the colours allow), in the segment's own pixel space with its joint at the
 * origin. Limbs are cut at the elbow and knee so they bend; the lower half laps half a pixel into the upper so a bent
 * joint shows no gap. The outer layer (armor) is the same, puffed out a little, as its own mesh.
 */
public final class CharModel {
    public enum Seg { HEAD, TORSO, R_ARM_UP, R_ARM_LO, L_ARM_UP, L_ARM_LO, R_LEG_UP, R_LEG_LO, L_LEG_UP, L_LEG_LO }
    public static final int SEGS = Seg.values().length;

    /** Vertices (10 floats each: position, colour, normal) per segment, base and outer layer. */
    public final float[][] base = new float[SEGS][], outer = new float[SEGS][];

    public static CharModel build(Skin s) {
        CharModel m = new CharModel();
        Body b = s.body;
        int[] h = b.head, t = b.torso, a = b.arm, l = b.leg;
        m.seg(Seg.HEAD, s, Part.HEAD, Part.HAT, 0.5f, -h[0] / 2f, 0, -h[2] / 2f, h[0] / 2f, h[1], h[2] / 2f, 0, true, true, 0, 0);
        // the silhouette fills out or draws in the torso and limbs (the same texels stretch over them)
        float tx = t[0] * b.girth / 2f, tz = t[2] * b.depth / 2f;
        if (b.bust <= 0) m.seg(Seg.TORSO, s, Part.TORSO, Part.JACKET, 0.25f, -tx, 0, -tz, tx, t[1], tz, 0, true, true, 0, 0);
        else m.feminine(s, b, tx, tz, t[1]);
        // arms: the shoulder pivot two pixels below the arm's top; cut at the elbow
        float ax = a[0] * b.limb / 2f, az = a[2] * b.limb / 2f;
        for (boolean right : new boolean[]{true, false}) {
            Part p = right ? Part.R_ARM : Part.L_ARM, o = right ? Part.R_SLEEVE : Part.L_SLEEVE;
            float cut = Body.ARM_CUT, top = Body.ARM_TOP, end = Body.ARM_END;
            m.seg(right ? Seg.R_ARM_UP : Seg.L_ARM_UP, s, p, o, 0.25f, -ax, cut, -az, ax, top, az, 0, true, false, 0, (top - cut));
            m.seg(right ? Seg.R_ARM_LO : Seg.L_ARM_LO, s, p, o, 0.25f, -ax, end - cut, -az, ax, 0.5f, az, (top - cut - 0.5f) * Skin.T, false, true, 0.5f, 0);
        }
        // legs: hanging from the hip; cut at the knee
        float lx = l[0] * b.limb / 2f, lz = l[2] * b.limb / 2f;
        for (boolean right : new boolean[]{true, false}) {
            Part p = right ? Part.R_LEG : Part.L_LEG, o = right ? Part.R_PANTS : Part.L_PANTS;
            m.seg(right ? Seg.R_LEG_UP : Seg.L_LEG_UP, s, p, o, 0.25f, -lx, -Body.THIGH, -lz, lx, 0, lz, 0, true, false, 0, Body.THIGH);
            m.seg(right ? Seg.R_LEG_LO : Seg.L_LEG_LO, s, p, o, 0.25f, -lx, -Body.SHIN, -lz, lx, 0.5f, lz, (Body.THIGH - 0.5f) * Skin.T, false, true, 0.5f, 0);
        }
        return m;
    }

    /**
     * A woman's torso: the chest, a drawn-in waist and flared hips stacked on the same skeleton (each showing its own
     * rows of the torso's texels, so the clothes run on unbroken), and the bust standing forward of the chest in two
     * steps so it reads rounded rather than as a shelf. The ledges where the waist draws in are closed in the colour of
     * the cloth beside them. The outer layer (armor, loose cloth) follows the same shape, puffed.
     */
    private void feminine(Skin s, Body b, float tx, float tz, float h) {
        float wy = 4, cy = 7;                                    // the hips run to 4 px up, the waist to 7, the chest to the top
        float ww = tx - b.waist, hw = tx + b.hips * 0.5f, wz = tz - b.waist * 0.35f, hz = tz + b.hips * 0.2f;
        float by0 = cy + 0.8f, by1 = cy + 2.6f, by2 = cy + 3.5f, bx = tx - 0.55f;
        java.util.List<float[]> in = new java.util.ArrayList<>(), out = new java.util.ArrayList<>();
        for (int layer = 0; layer < 2; layer++) {
            Part p = layer == 0 ? Part.TORSO : Part.JACKET;
            float u = layer == 0 ? 0 : 0.25f;
            java.util.List<float[]> to = layer == 0 ? in : out;
            to.add(box(s, p, u, -tx - u, cy, -tz - u, tx + u, h + u, tz + u, 0, true, false, 0, 1));                                 // chest
            to.add(box(s, p, u, -ww - u, wy, -wz - u, ww + u, cy, wz + u, Math.round((h - cy) * Skin.T), false, false, 0, 0));        // waist
            to.add(box(s, p, u, -hw - u, -u, -hz - u, hw + u, wy, hz + u, Math.round((h - wy) * Skin.T), false, true, 1, 0));          // hips
            to.add(box(s, p, u, -bx - u, by0, -tz - b.bust - u, bx + u, by1, -tz, Math.round((h - by1) * Skin.T), false, false, 1, 1)); // bust
            to.add(box(s, p, u, -bx + 0.2f - u, by1, -tz - b.bust * 0.55f - u, bx - 0.2f + u, by2, -tz, Math.round((h - by2) * Skin.T), false, false, 1, 0));
        }
        base[Seg.TORSO.ordinal()] = concat(in);
        outer[Seg.TORSO.ordinal()] = concat(out);
    }

    private static float[] concat(java.util.List<float[]> parts) {
        int n = 0;
        for (float[] f : parts) n += f.length;
        float[] r = new float[n];
        int at = 0;
        for (float[] f : parts) { System.arraycopy(f, 0, r, at, f.length); at += f.length; }
        return r;
    }

    /**
     * One segment: the box x0..x1, y0..y1, z0..z1 (pixels) showing rows from rowOffset of the part's side faces; with its
     * top and/or bottom face; a lapped top (lap pixels) has no top face; a cut bottom (cutDepth) is closed in one colour.
     */
    private void seg(Seg seg, Skin s, Part p, Part over, float puff, float x0, float y0, float z0, float x1, float y1, float z1,
                     float rowOffset, boolean topFace, boolean bottomFace, float lap, float cutDepth) {
        base[seg.ordinal()] = box(s, p, 0, x0, y0, z0, x1, y1, z1, (int) rowOffset, topFace, bottomFace, lap, cutDepth);
        float py0 = bottomFace ? y0 - puff : y0, py1 = topFace ? y1 + puff : y1;
        outer[seg.ordinal()] = box(s, over, puff, x0 - puff, py0, z0 - puff, x1 + puff, py1, z1 + puff, (int) rowOffset, topFace, bottomFace, lap, cutDepth);
    }

    private static float[] box(Skin s, Part p, float puff, float x0, float y0, float z0, float x1, float y1, float z1, int rowOffset,
                               boolean topFace, boolean bottomFace, float lap, float cutDepth) {
        Quads q = new Quads();
        // the rows of the side faces this segment shows (puffed ends add no rows: the texels stretch over them)
        int[] fr = s.face(p, Skin.FRONT);
        int rows = Math.min(fr[3] - rowOffset, Math.round((y1 - y0 - (topFace ? puff : 0) - (bottomFace ? puff : 0)) * Skin.T));
        // front (-z), back (+z), right (+x), left (-x): top-left corner, top-right, bottom-left, normal
        face(q, s, p, Skin.FRONT, rowOffset, rows, new float[]{x1, y1, z0}, new float[]{x0, y1, z0}, new float[]{x1, y0, z0}, 0, 0, -1);
        face(q, s, p, Skin.BACK, rowOffset, rows, new float[]{x0, y1, z1}, new float[]{x1, y1, z1}, new float[]{x0, y0, z1}, 0, 0, 1);
        face(q, s, p, Skin.RIGHT, rowOffset, rows, new float[]{x1, y1, z1}, new float[]{x1, y1, z0}, new float[]{x1, y0, z1}, 1, 0, 0);
        face(q, s, p, Skin.LEFT, rowOffset, rows, new float[]{x0, y1, z0}, new float[]{x0, y1, z1}, new float[]{x0, y0, z0}, -1, 0, 0);
        int[] tf = s.face(p, Skin.TOP);
        if (topFace) face(q, s, p, Skin.TOP, 0, tf[3], new float[]{x1, y1, z1}, new float[]{x0, y1, z1}, new float[]{x1, y1, z0}, 0, 1, 0);
        if (bottomFace) face(q, s, p, Skin.BOTTOM, 0, tf[3], new float[]{x1, y0, z0}, new float[]{x0, y0, z0}, new float[]{x1, y0, z1}, 0, -1, 0);
        // a cut end: closed in the colour of the side texels beside it (seen only when the joint bends)
        if (!bottomFace && cutDepth > 0 && rows > 0) {
            int col = s.get(p, Skin.FRONT, fr[2] / 2, rowOffset + rows - 1);
            if ((col >>> 24) != 0) q.quad(new float[]{x1, y0, z0}, new float[]{x0, y0, z0}, new float[]{x1, y0, z1}, col, 0, -1, 0);
        }
        if (!topFace && lap > 0) {
            int col = s.get(p, Skin.FRONT, fr[2] / 2, rowOffset);
            if ((col >>> 24) != 0) q.quad(new float[]{x1, y1, z1}, new float[]{x0, y1, z1}, new float[]{x1, y1, z0}, col, 0, 1, 0);
        }
        return q.array();
    }

    /** One face: its texels from the skin (rows from rowOffset), merged greedily into rectangles of one colour. */
    private static void face(Quads q, Skin s, Part p, int f, int rowOffset, int rows, float[] tl, float[] tr, float[] bl, float nx, float ny, float nz) {
        int[] r = s.face(p, f);
        int cols = r[2];
        if (cols <= 0 || rows <= 0) return;
        int[] grid = new int[cols * rows];
        for (int j = 0; j < rows; j++) for (int i = 0; i < cols; i++) grid[j * cols + i] = s.get(p, f, i, rowOffset + j);
        float[] u = {(tr[0] - tl[0]) / cols, (tr[1] - tl[1]) / cols, (tr[2] - tl[2]) / cols};
        float[] v = {(bl[0] - tl[0]) / rows, (bl[1] - tl[1]) / rows, (bl[2] - tl[2]) / rows};
        boolean[] used = new boolean[grid.length];
        for (int j = 0; j < rows; j++) for (int i = 0; i < cols; i++) {
            int k = j * cols + i, col = grid[k];
            if (used[k] || (col >>> 24) == 0) continue;
            int w = 1;
            while (i + w < cols && !used[k + w] && grid[k + w] == col) w++;
            int h = 1;
            grow:
            while (j + h < rows) {
                for (int x = 0; x < w; x++) { int kk = (j + h) * cols + i + x; if (used[kk] || grid[kk] != col) break grow; }
                h++;
            }
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) used[(j + y) * cols + i + x] = true;
            float[] a = {tl[0] + u[0] * i + v[0] * j, tl[1] + u[1] * i + v[1] * j, tl[2] + u[2] * i + v[2] * j};
            float[] b = {a[0] + u[0] * w, a[1] + u[1] * w, a[2] + u[2] * w};
            float[] d = {a[0] + v[0] * h, a[1] + v[1] * h, a[2] + v[2] * h};
            q.quad(a, b, d, col, nx, ny, nz);
        }
    }

    /** A growing vertex list. */
    private static final class Quads {
        float[] v = new float[60 * 64];
        int n;

        /** The parallelogram a, b, d (and b + d - a), wound to face its normal. */
        void quad(float[] a, float[] b, float[] d, int argb, float nx, float ny, float nz) {
            float[] c = {b[0] + d[0] - a[0], b[1] + d[1] - a[1], b[2] + d[2] - a[2]};
            float e1x = b[0] - a[0], e1y = b[1] - a[1], e1z = b[2] - a[2], e2x = c[0] - a[0], e2y = c[1] - a[1], e2z = c[2] - a[2];
            float dot = (e1y * e2z - e1z * e2y) * nx + (e1z * e2x - e1x * e2z) * ny + (e1x * e2y - e1y * e2x) * nz;
            float[][] tri = dot >= 0 ? new float[][]{a, b, c, a, c, d} : new float[][]{a, c, b, a, d, c};
            if (n + 60 > v.length) v = Arrays.copyOf(v, v.length * 2);
            float r = ((argb >> 16) & 255) / 255f, g = ((argb >> 8) & 255) / 255f, bl = (argb & 255) / 255f;
            for (float[] p : tri) { v[n++] = p[0]; v[n++] = p[1]; v[n++] = p[2]; v[n++] = r; v[n++] = g; v[n++] = bl; v[n++] = 1; v[n++] = nx; v[n++] = ny; v[n++] = nz; }
        }

        float[] array() { return Arrays.copyOf(v, n); }
    }
}
