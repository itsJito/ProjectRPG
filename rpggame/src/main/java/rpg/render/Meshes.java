package rpg.render;

import java.util.ArrayList;
import java.util.List;

/**
 * Model geometry built from boxes (the devmap is greybox): a unit box, and merged box-sets - hairstyles, facial hair,
 * weapons - as 10-float vertices (position, colour with alpha, normal). Pieces are white or grey so the renderer's
 * per-instance tint colours them.
 */
public final class Meshes {
    private Meshes() {}

    /** A box set: {cx, cy, cz, sx, sy, sz, shade} each (shade multiplies the tint: 1 = full), or with a colour of its own. */
    public static final class Boxes {
        public final List<float[]> list = new ArrayList<>();
        public Boxes add(float cx, float cy, float cz, float sx, float sy, float sz, float shade) { list.add(new float[]{cx, cy, cz, sx, sy, sz, shade}); return this; }
        public Boxes add(float cx, float cy, float cz, float sx, float sy, float sz) { return add(cx, cy, cz, sx, sy, sz, 1); }
        /** A box in a colour (RGB), for models drawn untinted. */
        public Boxes rgb(float cx, float cy, float cz, float sx, float sy, float sz, int rgb) {
            list.add(new float[]{cx, cy, cz, sx, sy, sz, ((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f});
            return this;
        }
        /** A box from corner to corner (x0, y0, z0)-(x1, y1, z1), in a colour. */
        public Boxes span(float x0, float y0, float z0, float x1, float y1, float z1, int rgb) {
            return rgb((x0 + x1) / 2, (y0 + y1) / 2, (z0 + z1) / 2, Math.abs(x1 - x0), Math.abs(y1 - y0), Math.abs(z1 - z0), rgb);
        }
        public float[] vertices() {
            float[] v = new float[list.size() * 36 * 10];
            int k = 0;
            for (float[] b : list) k = b.length > 7 ? box(v, k, b[0], b[1], b[2], b[3], b[4], b[5], b[6], b[7], b[8], 1) : box(v, k, b[0], b[1], b[2], b[3], b[4], b[5], b[6], b[6], b[6], 1);
            return v;
        }
        public int count() { return list.size() * 36; }
    }

    /** Appends one box's 36 vertices at offset k (in floats); returns the new offset. */
    public static int box(float[] v, int k, float cx, float cy, float cz, float sx, float sy, float sz, float r, float g, float b, float a) {
        float[][] n = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        for (float[] f : n) {
            float[] u = Math.abs(f[1]) > 0.5f ? new float[]{1, 0, 0} : new float[]{0, 1, 0};
            float[] w = {f[1] * u[2] - f[2] * u[1], f[2] * u[0] - f[0] * u[2], f[0] * u[1] - f[1] * u[0]};
            float[][] q = new float[4][3];
            int[][] sgn = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};
            for (int c = 0; c < 4; c++) for (int i = 0; i < 3; i++) {
                float s = i == 0 ? sx : i == 1 ? sy : sz, o = i == 0 ? cx : i == 1 ? cy : cz;
                q[c][i] = o + (f[i] + u[i] * sgn[c][0] + w[i] * sgn[c][1]) * 0.5f * s;
            }
            float[] e1 = {q[1][0] - q[0][0], q[1][1] - q[0][1], q[1][2] - q[0][2]}, e2 = {q[2][0] - q[0][0], q[2][1] - q[0][1], q[2][2] - q[0][2]};
            float dot = (e1[1] * e2[2] - e1[2] * e2[1]) * f[0] + (e1[2] * e2[0] - e1[0] * e2[2]) * f[1] + (e1[0] * e2[1] - e1[1] * e2[0]) * f[2];
            int[] order = dot < 0 ? new int[]{0, 2, 1, 0, 3, 2} : new int[]{0, 1, 2, 0, 2, 3};
            for (int o : order) {
                float[] p = q[o];
                v[k++] = p[0]; v[k++] = p[1]; v[k++] = p[2]; v[k++] = r; v[k++] = g; v[k++] = b; v[k++] = a; v[k++] = f[0]; v[k++] = f[1]; v[k++] = f[2];
            }
        }
        return k;
    }

    public static float[] unitBox() { float[] v = new float[360]; box(v, 0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1); return v; }

    // ------------------------------------------------------------------ hair and faces (head space: the head is 1 x 1 x 1, centred; -z is the face)
    /** One of the twenty hairstyles of a sex (GDD 2.1), the part on the head: see {@link HairMeshes}. */
    /**
     * Closed eyelids over the face's painted eyes (head units: the head spans -0.5..0.5, the face at z -0.5, its texel
     * column i at x 0.5 - i/16 and row j at y 0.5 - j/16): each lid from the brow's foot to below the eye, in the skin a
     * shade darker, with the lash line along its lower edge.
     */
    public static Boxes eyelids(int skin, boolean female) {
        Boxes b = new Boxes();
        int lid = Skin.shade(0xFF000000 | skin, 0.86f) & 0xFFFFFF, lash = female ? 0x1E1A1A : Skin.shade(0xFF000000 | skin, 0.55f) & 0xFFFFFF;
        float z0 = -0.5f - 0.014f, z1 = -0.5f + 0.004f;
        for (int e = 0; e < 2; e++) {
            float i0 = e == 0 ? 2.5f : 9.5f, i1 = i0 + 4;
            float x0 = 0.5f - i1 / 16f, x1 = 0.5f - i0 / 16f;
            b.span(x0, 0.5f - 9.6f / 16f, z0, x1, 0.5f - 7f / 16f, z1, lid);
            b.span(x0 - (female ? 0.01f : 0), 0.5f - 10.1f / 16f, z0 - 0.004f, x1 + (female ? 0.01f : 0), 0.5f - 9.5f / 16f, z1, lash);
        }
        return b;
    }

    public static Boxes hair(boolean female, int index) { return HairMeshes.head(female, index); }

    /** The part of a hairstyle that hangs below the jaw, hung from the neck (torso space, head units). */
    public static Boxes hairHang(boolean female, int index) { return HairMeshes.hang(female, index); }

    /** Facial hair (GDD 2.1: twenty presets, 0 clean-shaven; stubble is painted). */
    public static Boxes beard(int index) { return HairMeshes.beard(index); }

    /** The face: eyes and a nose (dark and light shades on the skin tint). */
    public static Boxes face() {
        Boxes b = new Boxes();
        b.add(-0.2f, 0.08f, -0.505f, 0.14f, 0.08f, 0.02f, 0.12f);
        b.add(0.2f, 0.08f, -0.505f, 0.14f, 0.08f, 0.02f, 0.12f);
        b.add(0, -0.08f, -0.53f, 0.12f, 0.18f, 0.08f, 0.88f);
        b.add(0, -0.3f, -0.505f, 0.26f, 0.04f, 0.02f, 0.55f);
        return b;
    }

    // ------------------------------------------------------------------ held things (weapon space: the grip at the origin, +y along the blade)
    public static Boxes weapon(rpg.item.ItemDef.Family f, float reach) {
        Boxes b = new Boxes();
        switch (f) {
            case SWORD -> { b.add(0, 0.05f, 0, 0.05f, 0.18f, 0.05f, 0.35f); b.add(0, 0.16f, 0, 0.26f, 0.04f, 0.06f, 0.6f); b.add(0, 0.18f + reach * 0.4f, 0, 0.07f, reach * 0.8f, 0.018f, 1f); }
            case DAGGER -> { b.add(0, 0.04f, 0, 0.045f, 0.14f, 0.045f, 0.35f); b.add(0, 0.12f, 0, 0.16f, 0.03f, 0.05f, 0.6f); b.add(0, 0.13f + reach * 0.22f, 0, 0.05f, reach * 0.42f, 0.015f, 1f); }
            case AXE, PICKAXE -> {
                b.add(0, reach * 0.4f, 0, 0.05f, reach * 0.85f, 0.05f, 0.45f);
                if (f == rpg.item.ItemDef.Family.AXE) b.add(0.12f, reach * 0.75f, 0, 0.2f, 0.24f, 0.03f, 1f);
                else b.add(0, reach * 0.78f, 0, 0.5f, 0.07f, 0.06f, 1f);
            }
            case MACE -> { b.add(0, reach * 0.38f, 0, 0.05f, reach * 0.8f, 0.05f, 0.45f); b.add(0, reach * 0.8f, 0, 0.17f, 0.2f, 0.17f, 1f); }
            case STAFF -> { b.add(0, 0.35f, 0, 0.05f, 1.6f, 0.05f, 0.5f); b.add(0, 1.18f, 0, 0.12f, 0.12f, 0.12f, 1f); }
            case WAND -> { b.add(0, 0.15f, 0, 0.03f, 0.38f, 0.03f, 0.6f); b.add(0, 0.34f, 0, 0.05f, 0.05f, 0.05f, 1f); }
            case BOW -> { b.add(0, 0, 0.04f, 0.04f, 0.5f, 0.04f, 0.6f); b.add(0, 0.42f, 0.1f, 0.035f, 0.4f, 0.035f, 0.6f); b.add(0, -0.42f, 0.1f, 0.035f, 0.4f, 0.035f, 0.6f); b.add(0, 0, 0.2f, 0.008f, 1.18f, 0.008f, 1.6f); }
            case SHIELD -> b.add(0, 0, 0, 0.5f, 0.62f, 0.07f, 1f);
            case SYMBOL -> { b.add(0, 0.1f, 0, 0.04f, 0.28f, 0.04f, 1f); b.add(0, 0.16f, 0, 0.18f, 0.04f, 0.04f, 1f); }
            default -> b.add(0, 0.1f, 0, 0.12f, 0.2f, 0.12f, 1f);
        }
        return b;
    }
}
