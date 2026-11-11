package rpg.objects;

import astratto.render.Tex;

/**
 * (0.3.8) How each breakable is built, voxel by voxel, as a joiner or a cooper would: which voxels are wood (a plank's
 * face, its cut end), which are iron (a nail head, a hoop), and which board each belongs to - so that it splits along its
 * planks and comes apart into them. The colours carry the grain, a darker frame, nail heads, the hoops' sheen.
 */
final class Shapes {
    private Shapes() { }

    static final int PLANK = 0xA27A4A, PLANK_DARK = 0x86603A, FRAME = 0x6E4C2C, NAIL = 0x3E3C3A, HOOP = 0x55575C, HOOP_HI = 0x7A7C82,
            STAVE = 0x9A6E40, STAVE_DARK = 0x7E5832, HEAD = 0x8C6438, PAINT = 0xE4DCC6, INK = 0x2E241A;

    static void build(Breakable b) {
        switch (b.form) {
            case CRATE -> box(b, 3, true);
            case BOX -> box(b, 2, false);
            case BARREL -> barrel(b);
            case SIGN -> sign(b);
        }
    }

    /** The pale of freshly split wood, for the voxels a break lays bare. */
    static int fresh(int argb) {
        if ((argb >>> 24) == 0) return argb;
        return 0xFF000000 | (Tex.mix(argb & 0xFFFFFF, 0xD8B888, 0.45f) & 0xFFFFFF);
    }

    private static int grain(int rgb, int x, int y, int z, float amount, int along) {
        int h = (x * 73856093) ^ (y * 19349663) ^ (z * 83492791);
        h ^= h >>> 13; h *= 0x5bd1e995; h ^= h >>> 15;
        // streaks along the board's length: the grain runs one way
        float streak = (float) Math.sin(along * 0.9 + ((h >>> 20) & 7)) * 0.04f;
        float k = 1 + ((h >>> 8 & 255) / 255f - 0.5f) * amount + streak;
        return 0xFF000000 | (Tex.shade(rgb, k) & 0xFFFFFF);
    }

    private static void put(Breakable b, int x, int y, int z, byte mat, int board, int argb) {
        if (!b.inside(x, y, z)) return;
        int i = b.index(x, y, z);
        b.mat[i] = mat; b.board[i] = (byte) board; b.vox.set(x, y, z, argb);
    }

    /**
     * A crate (or a small box): six panels of planks, plank-wide with a hairline between, nailed to a darker frame along
     * every edge; hollow. A lidded box's lid planks are their own boards.
     */
    private static void box(Breakable b, int plankW, boolean framed) {
        int sx = b.form.sx, sy = b.form.sy, sz = b.form.sz;
        for (int x = 0; x < sx; x++) for (int y = 0; y < sy; y++) for (int z = 0; z < sz; z++) {
            boolean ex = x == 0 || x == sx - 1, ey = y == 0 || y == sy - 1, ez = z == 0 || z == sz - 1;
            int faces = (ex ? 1 : 0) + (ey ? 1 : 0) + (ez ? 1 : 0);
            if (faces == 0) continue;
            if (faces >= 2 && framed) {
                // the frame: the edges, each edge its own batten
                int edge = 40 + (ex ? (x == 0 ? 0 : 1) : 2) * 9 + (ey ? (y == 0 ? 0 : 1) : 2) * 3 + (ez ? (z == 0 ? 0 : 1) : 2);
                boolean nail = faces == 3 || ((x + y + z) % 4 == 1 && faces == 2);
                put(b, x, y, z, nail ? Breakable.IRON : Breakable.WOOD, edge, nail ? 0xFF000000 | NAIL : grain(FRAME, x, y, z, 0.12f, x + y + z));
                continue;
            }
            // a panel: which face, and which plank across it (planks run along x on the sides, along z on top and bottom)
            int face = ex ? (x == 0 ? 0 : 1) : ey ? (y == 0 ? 2 : 3) : (z == 0 ? 4 : 5);
            int across = ey ? x : y, along = ey ? z : (ex ? z : x);
            int plank = across / plankW;
            boolean seam = plankW > 2 && across % plankW == plankW - 1 && across < (ey ? sx : sy) - 2;
            int col = seam ? grain(PLANK_DARK, x, y, z, 0.1f, along) : grain((plank & 1) == 0 ? PLANK : Tex.shade(PLANK, 0.93f), x, y, z, 0.14f, along);
            boolean end = along == 1 || along == (ey ? sz : ex ? sz : sx) - 2;
            boolean nail = end && across % plankW == 1 && framed;
            put(b, x, y, z, nail ? Breakable.IRON : end ? Breakable.END : Breakable.WOOD, 1 + face * 6 + plank, nail ? 0xFF000000 | NAIL : col);
        }
    }

    /** A barrel: staves round a bulging belly, a hoop near each end and two round the middle, a round head at each end. */
    private static void barrel(Breakable b) {
        int sx = b.form.sx, sy = b.form.sy, sz = b.form.sz;
        float cx = (sx - 1) / 2f, cz = (sz - 1) / 2f;
        int staves = 14;
        for (int y = 0; y < sy; y++) {
            float t = y / (float) (sy - 1);
            float r = 3.9f + 1.0f * (float) Math.sin(Math.PI * t);
            boolean hoop = y == 1 || y == sy - 2 || y == sy / 2 - 2 || y == sy / 2 + 1;
            boolean head = y == 0 || y == sy - 1;
            for (int x = 0; x < sx; x++) for (int z = 0; z < sz; z++) {
                float dx = x - cx, dz = z - cz, d = (float) Math.sqrt(dx * dx + dz * dz);
                if (d > r + 0.35f) continue;
                if (head) {
                    if (d > r - 0.2f) continue;
                    // the head: boards across it, a darker chime round its edge
                    int boardNo = 60 + (y == 0 ? 0 : 5) + Math.min(4, (int) ((dx + r) / (2 * r) * 4));
                    put(b, x, y, z, Breakable.WOOD, boardNo, grain(d > r - 1.2f ? STAVE_DARK : HEAD, x, y, z, 0.12f, z));
                    continue;
                }
                if (d < r - 0.9f) continue;   // hollow
                double ang = Math.atan2(dz, dx);
                int stave = (int) Math.floor((ang + Math.PI) / (2 * Math.PI) * staves) % staves;
                if (hoop) {
                    boolean hi = (x + z) % 3 == 0;
                    put(b, x, y, z, Breakable.IRON, 80 + (y < sy / 2 ? 0 : 1) + (y == 1 || y == sy - 2 ? 0 : 2), 0xFF000000 | (hi ? HOOP_HI : HOOP));
                } else {
                    boolean seam = Math.floorMod((int) Math.floor((ang + Math.PI) / (2 * Math.PI) * staves * 4), 4) == 0;
                    put(b, x, y, z, Breakable.WOOD, 1 + stave, grain(seam ? STAVE_DARK : (stave & 1) == 0 ? STAVE : Tex.shade(STAVE, 0.94f), x, y, z, 0.14f, y));
                }
            }
        }
    }

    /**
     * A standing sign: a board on a post, the post in a cross-footed stand so it stands on its own (and can be knocked
     * over). The board is painted pale with dark lettering cut into it; its back is bare wood.
     */
    private static void sign(Breakable b) {
        int sx = b.form.sx, sy = b.form.sy, sz = b.form.sz;
        int mid = sx / 2, midZ = sz / 2;
        // the feet: a cross of two battens
        for (int x = 1; x < sx - 1; x++) put(b, x, 0, midZ, Breakable.WOOD, 1, grain(FRAME, x, 0, midZ, 0.12f, x));
        for (int z = 0; z < sz; z++) put(b, mid, 0, z, Breakable.WOOD, 2, grain(FRAME, mid, 0, z, 0.12f, z));
        // the post
        for (int y = 1; y < sy - 8; y++) for (int dx = 0; dx < 2; dx++) put(b, mid - 1 + dx, y, midZ, Breakable.WOOD, 3, grain(PLANK_DARK, mid - 1 + dx, y, midZ, 0.12f, y));
        // the board: two planks, a painted face with lettering, nailed to the post
        for (int y = sy - 9; y < sy - 1; y++) for (int x = 0; x < sx; x++) {
            boolean edge = x == 0 || x == sx - 1 || y == sy - 9 || y == sy - 2;
            int plank = y < sy - 5 ? 4 : 5;
            put(b, x, y, midZ - 1, Breakable.WOOD, plank, grain(edge ? FRAME : PLANK, x, y, midZ - 1, 0.12f, x));
            // the face: paint, and the letters as dark cuts (rows of strokes)
            boolean letter = !edge && (y == sy - 4 || y == sy - 6) && x > 1 && x < sx - 2 && ((x * 7 + y * 3) % 5 != 0);
            boolean nail = (x == mid - 1 || x == mid) && (y == sy - 8 || y == sy - 3);
            int face = edge ? grain(FRAME, x, y, midZ - 2, 0.12f, x) : letter ? 0xFF000000 | INK : grain(PAINT, x, y, midZ - 2, 0.06f, x);
            put(b, x, y, midZ - 2, nail ? Breakable.IRON : Breakable.WOOD, plank, nail ? 0xFF000000 | NAIL : face);
        }
    }
}
