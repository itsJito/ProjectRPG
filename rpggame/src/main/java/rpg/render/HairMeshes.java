package rpg.render;

import rpg.render.Meshes.Boxes;

/**
 * The GDD 2.1 hair catalogs - twenty authored styles for each sex - and the twenty facial-hair presets, as half-pixel
 * voxel boxes ("HD voxels": sixteen to the head, the skin texture's own resolution) in head space: the head is the unit
 * cube, centred, its face at -z. Hair that hangs below the jaw is a separate piece hung from the neck on the torso, so
 * long hair falls down the back and over the shoulders instead of turning through them with the head.
 *
 * <p>Shades (the seventh number) darken the tint per box: hair is drawn in its natural colour with the shade carving
 * strands, partings and the roots.
 */
public final class HairMeshes {
    private HairMeshes() {}

    /** A sixteenth: one texel of the head. */
    private static final float U = 1f / 16f;

    // head landmarks (head units)
    private static final float TOP = 0.5f, BROW = 0.12f, EAR_TOP = 0.0f, EAR_BOTTOM = -0.25f, JAW = -0.5f;

    /** Part of a head of hair: a box from corner to corner, with a shade. */
    private static void box(Boxes b, float x0, float y0, float z0, float x1, float y1, float z1, float shade) {
        b.add((x0 + x1) / 2, (y0 + y1) / 2, (z0 + z1) / 2, Math.abs(x1 - x0), Math.abs(y1 - y0), Math.abs(z1 - z0), shade);
    }

    /** A close cap over the crown and down the back and sides to a line (thickness t), the face left clear. */
    private static void cap(Boxes b, float t, float backDown, float sideDown, float frontLine, float shade) {
        box(b, -0.5f - t, TOP, -0.5f - t * 0.5f, 0.5f + t, TOP + t, 0.5f + t, shade);                          // crown
        box(b, -0.5f - t, backDown, 0.5f, 0.5f + t, TOP, 0.5f + t, shade * 0.96f);                               // back
        box(b, -0.5f - t, sideDown, frontLine, -0.5f, TOP, 0.5f, shade * 0.94f);                                 // sides
        box(b, 0.5f, sideDown, frontLine, 0.5f + t, TOP, 0.5f, shade * 0.94f);
        box(b, -0.5f, TOP - 2 * U, -0.5f - t, 0.5f, TOP, -0.5f, shade);                                          // the hairline across the brow
    }

    /** A fringe across the brow: from x0 to x1, down to a height, a little proud of the face. */
    private static void fringe(Boxes b, float x0, float x1, float down, float shade) {
        box(b, x0, down, -0.5f - 1.5f * U, x1, TOP, -0.5f, shade);
    }

    /** Small staggered boxes over the surface: curls or texture. */
    private static void bumps(Boxes b, float y0, float y1, float step, float size, float shade, int seed) {
        int k = seed;
        for (float x = -0.5f; x <= 0.5f; x += step) for (float z = -0.45f; z <= 0.5f; z += step) {
            k = k * 1103515245 + 12345;
            float h = size * (0.6f + ((k >>> 16) & 7) / 10f);
            box(b, x - step * 0.45f, TOP, z - step * 0.45f, x + step * 0.45f, TOP + h, z + step * 0.45f, shade * (0.85f + ((k >>> 20) & 3) * 0.05f));
        }
        for (float y = y0; y <= y1; y += step) for (float x = -0.5f; x <= 0.5f; x += step) {
            k = k * 1103515245 + 12345;
            float h = size * (0.5f + ((k >>> 16) & 7) / 12f);
            box(b, x - step * 0.45f, y - step * 0.45f, 0.5f, x + step * 0.45f, y + step * 0.45f, 0.5f + h, shade * (0.85f + ((k >>> 20) & 3) * 0.05f));
        }
    }

    /** A braid hanging from a point, down to a length: alternating plaits, a tie and a tuft. */
    private static void braid(Boxes b, float x, float y, float z, float length, float w, float shade) {
        int n = Math.max(2, Math.round(length / (w * 0.8f)));
        for (int i = 0; i < n; i++) {
            float yy = y - i * w * 0.8f, off = (i & 1) == 0 ? -w * 0.18f : w * 0.18f;
            box(b, x - w / 2 + off, yy - w * 0.9f, z - w / 2, x + w / 2 + off, yy, z + w / 2, shade * ((i & 1) == 0 ? 1f : 0.88f));
        }
        float end = y - n * w * 0.8f;
        box(b, x - w * 0.35f, end - U, z - w * 0.35f, x + w * 0.35f, end + U, z + w * 0.35f, 0.45f);     // the tie
        box(b, x - w * 0.3f, end - 3 * U, z - w * 0.3f, x + w * 0.3f, end - U, z + w * 0.3f, shade * 0.95f);
    }

    /**
     * The hair on the head itself (head space) for a style of a sex (GDD 2.1 table; 0 is bald). What hangs below the
     * jaw comes from {@link #hang}.
     */
    public static Boxes head(boolean female, int style) {
        Boxes b = new Boxes();
        float t = 1.5f * U;
        switch (style) {
            case 0 -> { }
            case 1 -> {                                            // close-cropped / close-cropped pixie
                cap(b, U * 0.75f, EAR_TOP + 2 * U, EAR_TOP + 3 * U, -0.32f, 0.9f);
                if (female) { fringe(b, -0.45f, 0.2f, TOP - 4 * U, 1f); box(b, 0.1f, TOP - 3 * U, -0.5f - 2 * U, 0.42f, TOP - U, -0.5f, 0.95f); box(b, -0.52f, EAR_TOP - 3 * U, -0.38f, -0.5f, EAR_TOP + 2 * U, -0.28f, 0.92f); box(b, 0.5f, EAR_TOP - 3 * U, -0.38f, 0.52f, EAR_TOP + 2 * U, -0.28f, 0.92f); }
            }
            case 2 -> {
                if (female) {                                      // short bob: to the chin, a blunt fringe
                    cap(b, t, JAW + 2 * U, JAW + 2 * U, -0.42f, 1f);
                    fringe(b, -0.5f, 0.5f, BROW + U, 1f);
                    box(b, -0.5f - 2.5f * U, JAW, -0.42f, -0.5f, BROW, 0.5f + t, 0.97f);
                    box(b, 0.5f, JAW, -0.42f, 0.5f + 2.5f * U, BROW, 0.5f + t, 0.97f);
                    box(b, -0.5f - t, JAW, 0.5f, 0.5f + t, TOP, 0.5f + 2.5f * U, 0.95f);
                } else {                                           // crew / buzz cut: a hair longer on top
                    cap(b, U * 0.6f, EAR_TOP, EAR_TOP + 2 * U, -0.32f, 0.85f);
                    box(b, -0.46f, TOP, -0.46f, 0.46f, TOP + 1.5f * U, 0.4f, 0.92f);
                }
            }
            case 3 -> {                                            // spiky textured crop / pixie
                cap(b, U, EAR_TOP + U, EAR_TOP + 2 * U, -0.32f, 0.9f);
                int k = female ? 7 : 3;
                for (int i = 0; i < 4; i++) for (int j = 0; j < 4; j++) {
                    k = k * 1103515245 + 12345;
                    float x = -0.36f + i * 0.24f, z = -0.36f + j * 0.24f, h = (3 + ((k >>> 16) & 3)) * U, lean = (j - 1.5f) * 0.02f;
                    box(b, x - U * 1.5f, TOP, z - U * 1.5f + lean, x + U * 1.5f, TOP + h, z + U * 1.5f + lean, 0.88f + ((k >>> 20) & 3) * 0.04f);
                }
                if (female) fringe(b, -0.5f, 0.25f, BROW + 2 * U, 0.95f);
            }
            case 4 -> {                                            // short swept-back / swept-back crop: volume high at the front
                cap(b, U, EAR_TOP, EAR_TOP + 2 * U, -0.34f, 0.92f);
                for (int i = 0; i < 5; i++) {
                    float z0 = -0.52f + i * 0.2f, h = (5 - i) * U * (female ? 0.9f : 1.1f);
                    box(b, -0.48f, TOP, z0, 0.48f, TOP + h, z0 + 0.22f, 0.95f - i * 0.02f);
                }
                box(b, -0.48f, TOP - 2 * U, -0.5f - 2 * U, 0.48f, TOP + 4 * U, -0.48f, 1f);
            }
            case 5 -> {                                            // short curls
                cap(b, U, female ? EAR_BOTTOM : EAR_TOP, female ? EAR_BOTTOM : EAR_TOP + U, -0.36f, 0.88f);
                bumps(b, female ? EAR_BOTTOM : EAR_TOP, TOP, 0.125f, 2.5f * U, 0.95f, female ? 11 : 5);
                fringe(b, -0.45f, 0.45f, TOP - 3 * U, 0.9f);
            }
            case 6 -> {                                            // side-part (male) / side-part long bob (female)
                cap(b, t, female ? JAW : EAR_TOP - U, female ? JAW - 2 * U : EAR_TOP, -0.36f, 1f);
                box(b, -0.5f - t, TOP, -0.52f, -0.18f, TOP + 3 * U, 0.5f, 0.98f);          // the parted side
                box(b, -0.18f + U, TOP, -0.52f, 0.5f + t, TOP + 2.5f * U, 0.5f, 0.96f);
                box(b, -0.2f, TOP + 0.01f, -0.5f, -0.18f + U, TOP + 0.5f * U, 0.45f, 0.5f);   // the part itself
                fringe(b, -0.18f, 0.5f, female ? BROW - U : BROW + 3 * U, 1f);             // swept over to the figure's right
                if (female) { box(b, -0.5f - 2.5f * U, JAW - 3 * U, -0.44f, -0.5f, BROW, 0.5f, 0.97f); box(b, 0.5f, JAW, -0.4f, 0.5f + 2.5f * U, BROW, 0.5f, 0.97f); }
            }
            case 7 -> {                                            // curtains / curtain-fringe shoulder cut
                cap(b, t, female ? JAW : EAR_BOTTOM, female ? JAW : EAR_BOTTOM, -0.38f, 1f);
                box(b, -0.5f, TOP, -0.5f, 0.5f, TOP + 2 * U, 0.5f, 0.97f);
                box(b, -U * 0.5f, TOP + 0.005f, -0.5f, U * 0.5f, TOP + 2.2f * U, 0.0f, 0.5f);   // a centre part
                for (int s = -1; s <= 1; s += 2) {                                        // the two curtains framing the face
                    float x0 = s < 0 ? -0.5f - U : 0.08f, x1 = s < 0 ? -0.08f : 0.5f + U;
                    box(b, x0, BROW - (female ? 0.08f : 0.02f), -0.5f - 2 * U, x1, TOP, -0.48f, 0.98f);
                }
                if (female) { box(b, -0.5f - 3 * U, JAW, -0.4f, -0.5f, TOP, 0.5f, 0.96f); box(b, 0.5f, JAW, -0.4f, 0.5f + 3 * U, TOP, 0.5f, 0.96f); }
            }
            case 8 -> {                                            // ear-length layered / shoulder-length layers
                cap(b, t, EAR_BOTTOM - 2 * U, female ? JAW : EAR_BOTTOM, -0.38f, 1f);
                for (int i = 0; i < 3; i++) {                                              // layers: steps at the sides
                    float y = EAR_TOP + 0.12f - i * 0.12f, o = (3 - i) * U * 0.6f;
                    box(b, -0.5f - t - o, y - 0.12f, -0.36f, -0.5f, y, 0.5f + t, 0.92f + i * 0.03f);
                    box(b, 0.5f, y - 0.12f, -0.36f, 0.5f + t + o, y, 0.5f + t, 0.92f + i * 0.03f);
                }
                box(b, -0.5f, TOP, -0.5f, 0.5f, TOP + 2.5f * U, 0.5f, 0.97f);
                fringe(b, -0.5f, 0.5f, TOP - 4 * U, 0.98f);
            }
            case 9 -> {                                            // mid ponytail / half-up mid ponytail
                cap(b, U * 1.2f, female ? JAW : EAR_TOP - 2 * U, female ? JAW : EAR_TOP, -0.36f, 0.98f);
                box(b, -0.12f, -0.12f, 0.5f, 0.12f, 0.12f, 0.5f + 3 * U, 0.4f);           // the tie, mid-height at the back
                box(b, -0.1f, -0.06f, 0.5f + 3 * U, 0.1f, 0.1f, 0.5f + 6 * U, 0.98f);
                if (female) { box(b, -0.5f - 2 * U, JAW, -0.42f, -0.5f, TOP, 0.5f, 0.97f); box(b, 0.5f, JAW, -0.42f, 0.5f + 2 * U, TOP, 0.5f, 0.97f); fringe(b, -0.5f, 0.5f, TOP - 5 * U, 1f); }
            }
            case 10 -> {                                           // top-knot / man-bun: pulled back flat, a knot on the crown
                cap(b, U, EAR_TOP - U, EAR_TOP, -0.36f, 0.96f);
                box(b, -0.16f, TOP + U, 0.02f, 0.16f, TOP + 5 * U, 0.34f, 1f);
                box(b, -0.12f, TOP + 5 * U, 0.06f, 0.12f, TOP + 7 * U, 0.3f, 0.92f);
                box(b, -0.17f, TOP + U, 0.01f, 0.17f, TOP + 2 * U, 0.35f, 0.45f);        // its tie
            }
            case 11 -> {                                           // medium tousled waves / medium loose waves
                cap(b, t, JAW, JAW + U, -0.38f, 1f);
                for (int i = 0; i < 6; i++) {
                    float y = TOP - i * 0.17f, o = ((i & 1) == 0 ? 2 : 0.5f) * U;
                    box(b, -0.5f - t - o, y - 0.17f, -0.38f, -0.5f, y, 0.5f + t, 0.93f + (i & 1) * 0.06f);
                    box(b, 0.5f, y - 0.17f, -0.38f, 0.5f + t + o, y, 0.5f + t, 0.93f + ((i + 1) & 1) * 0.06f);
                    box(b, -0.5f - t, y - 0.17f, 0.5f, 0.5f + t, y, 0.5f + t + o, 0.94f + (i & 1) * 0.05f);
                }
                box(b, -0.5f, TOP, -0.5f, 0.5f, TOP + 3 * U, 0.5f, 1f);
                box(b, -0.3f, TOP + 2 * U, -0.3f, 0.2f, TOP + 4 * U, 0.2f, 0.94f);
                fringe(b, female ? -0.5f : -0.3f, 0.5f, BROW + (female ? 0 : 2 * U), 0.98f);
            }
            case 12, 13 -> {                                       // straight long / wavy layered long: the head's part
                cap(b, t, JAW, JAW, -0.38f, 1f);
                box(b, -0.5f, TOP, -0.5f, 0.5f, TOP + 2 * U, 0.5f, 0.98f);
                box(b, -U * 0.5f, TOP + 0.005f, -0.5f, U * 0.5f, TOP + 2.2f * U, 0.1f, 0.55f);
                box(b, -0.5f - 2.5f * U, JAW, -0.4f, -0.5f, TOP, 0.5f + t, style == 13 ? 0.95f : 0.98f);
                box(b, 0.5f, JAW, -0.4f, 0.5f + 2.5f * U, TOP, 0.5f + t, style == 13 ? 0.95f : 0.98f);
                if (female) fringe(b, -0.5f, 0.5f, TOP - 6 * U, 1f);
            }
            case 14 -> {                                           // high ponytail: tight, the tail from the crown's back
                cap(b, U * 0.9f, EAR_TOP - 2 * U, EAR_TOP, -0.36f, 0.97f);
                box(b, -0.13f, TOP - 0.08f, 0.38f, 0.13f, TOP + 0.08f, 0.5f + 3 * U, 0.4f);
                box(b, -0.12f, TOP - 0.12f, 0.5f + 3 * U, 0.12f, TOP + 0.1f, 0.5f + 7 * U, 1f);
                box(b, -0.1f, TOP - 0.6f, 0.5f + 5 * U, 0.1f, TOP - 0.1f, 0.5f + 8 * U, 0.97f);
            }
            case 15 -> {                                           // single long braid (the braid hangs, see hang)
                cap(b, U * 1.2f, EAR_TOP - 3 * U, EAR_TOP, -0.36f, 0.97f);
                box(b, -0.14f, -0.1f, 0.5f, 0.14f, 0.2f, 0.5f + 3 * U, 0.97f);
                if (female) fringe(b, -0.5f, 0.0f, TOP - 4 * U, 0.98f);
            }
            case 16 -> {                                           // dual braids, from behind each ear
                cap(b, U * 1.2f, EAR_TOP - 2 * U, EAR_TOP - 3 * U, -0.36f, 0.97f);
                box(b, -U * 0.5f, TOP + 0.005f, -0.5f, U * 0.5f, TOP + 1.4f * U, 0.5f, 0.5f);
                for (int s = -1; s <= 1; s += 2) braid(b, s * (0.5f + U), -0.05f, 0.25f, 0.45f, 0.18f, 0.97f);
                if (female) fringe(b, -0.5f, 0.5f, TOP - 4 * U, 0.98f);
            }
            case 17 -> {                                           // long dreadlocks: the roots on the head
                cap(b, t, EAR_BOTTOM, EAR_BOTTOM, -0.36f, 0.9f);
                for (float x = -0.42f; x <= 0.43f; x += 0.14f) for (float z = -0.35f; z <= 0.43f; z += 0.14f)
                    box(b, x - U * 1.2f, TOP, z - U * 1.2f, x + U * 1.2f, TOP + 2 * U, z + U * 1.2f, 0.84f + ((int) ((x + z) * 20) & 1) * 0.08f);
                for (float x = -0.42f; x <= 0.43f; x += 0.14f) box(b, x - U * 1.2f, BROW, -0.5f - 2.4f * U, x + U * 1.2f, TOP, -0.5f, 0.86f);
            }
            case 18 -> {                                           // mohawk: shaved sides (painted), a crest
                float h = female ? 6 * U : 7 * U;
                box(b, -0.14f, TOP, -0.5f, 0.14f, TOP + h * 0.6f, 0.52f, 0.95f);
                for (int i = 0; i < 5; i++) {
                    float z = -0.45f + i * 0.22f;
                    box(b, -0.1f, TOP + h * 0.5f, z - 0.07f, 0.1f, TOP + h * (0.9f + (i & 1) * 0.25f), z + 0.07f, 0.92f + (i & 1) * 0.06f);
                }
                box(b, -0.12f, EAR_TOP, 0.5f, 0.12f, TOP, 0.5f + 1.5f * U, 0.93f);
            }
            case 19 -> {                                           // undercut / half-shaved (male) / side-shaved long cut (female)
                if (female) {
                    box(b, -0.5f, TOP, -0.5f, 0.5f + t, TOP + 2.5f * U, 0.5f, 1f);         // swept to the figure's left
                    box(b, 0.5f, JAW, -0.42f, 0.5f + 3 * U, TOP, 0.5f + t, 0.98f);
                    box(b, -0.3f, JAW, 0.5f, 0.5f + t, TOP, 0.5f + 2.5f * U, 0.96f);
                    fringe(b, -0.2f, 0.5f, BROW, 1f);
                } else {
                    box(b, -0.42f, TOP, -0.52f, 0.46f, TOP + 4 * U, 0.42f, 1f);
                    box(b, -0.42f, TOP + 3 * U, -0.56f, 0.46f, TOP + 5 * U, 0.1f, 0.96f);  // swept up off the brow
                    box(b, -0.42f, TOP - 2 * U, -0.5f - 2 * U, 0.46f, TOP + 3 * U, -0.48f, 0.98f);
                }
            }
            default -> { }
        }
        return b;
    }

    /**
     * What hangs below the jaw (torso space, in head units: the origin at the top of the neck, -z forward): the length
     * of long hair down the back and over the shoulders, tails and braids. Empty for short styles.
     */
    public static Boxes hang(boolean female, int style) {
        Boxes b = new Boxes();
        float back = 0.42f;   // the back of the head, behind the neck
        switch (style) {
            case 7, 8, 11 -> {
                if (!female && style != 11) break;
                float len = style == 11 ? (female ? 0.85f : 0.45f) : 0.62f;
                box(b, -0.55f, -len, back - 0.05f, 0.55f, 0.0f, back + 0.12f, 0.96f);
                if (female) { box(b, -0.62f, -len * 0.8f, -0.25f, -0.48f, 0.0f, back, 0.95f); box(b, 0.48f, -len * 0.8f, -0.25f, 0.62f, 0.0f, back, 0.95f); }
            }
            case 9 -> {
                float len = female ? 0.75f : 0.6f;
                box(b, -0.1f, -len, back + 0.12f, 0.1f, 0.05f, back + 0.3f, 0.97f);
                box(b, -0.06f, -len - 0.1f, back + 0.14f, 0.06f, -len, back + 0.26f, 0.92f);
                if (female) box(b, -0.55f, -0.5f, back - 0.04f, 0.55f, 0.0f, back + 0.1f, 0.95f);
            }
            case 12, 13 -> {
                float len = female ? 1.65f : 1.25f;
                boolean wavy = style == 13;
                int steps = wavy ? 7 : 1;
                for (int i = 0; i < steps; i++) {
                    float y0 = -len * (i + 1) / steps, y1 = -len * i / steps, o = wavy ? ((i & 1) == 0 ? 0.04f : -0.02f) : 0;
                    float narrow = wavy ? i * 0.01f : 0;
                    box(b, -0.56f + narrow, y0, back - 0.06f + o, 0.56f - narrow, y1, back + 0.14f + o, wavy ? 0.93f + (i & 1) * 0.05f : 0.97f);
                }
                // locks in front of the shoulders
                float fl = female ? 0.9f : 0.6f;
                box(b, -0.66f, -fl, -0.22f, -0.48f, 0.0f, 0.15f, 0.95f);
                box(b, 0.48f, -fl, -0.22f, 0.66f, 0.0f, 0.15f, 0.95f);
            }
            case 14 -> {
                float len = female ? 1.25f : 0.95f;
                box(b, -0.1f, -len, back + 0.3f, 0.1f, 0.2f, back + 0.48f, 0.97f);
                box(b, -0.07f, -len - 0.12f, back + 0.32f, 0.07f, -len, back + 0.46f, 0.9f);
            }
            case 15 -> braid(b, 0, 0.05f, back + 0.12f, female ? 1.45f : 1.1f, 0.2f, 0.97f);
            case 16 -> { for (int s = -1; s <= 1; s += 2) braid(b, s * 0.55f, 0.0f, -0.12f, female ? 1.0f : 0.8f, 0.17f, 0.97f); }
            case 17 -> {
                int k = female ? 3 : 9;
                for (float x = -0.5f; x <= 0.51f; x += 0.125f) for (int row = 0; row < 2; row++) {
                    k = k * 1103515245 + 12345;
                    float len = (female ? 1.2f : 1.0f) - ((k >>> 16) & 7) * 0.04f, z = back + 0.02f + row * 0.1f;
                    box(b, x - U * 1.1f, -len, z - U * 1.1f, x + U * 1.1f, 0.05f, z + U * 1.1f, 0.82f + ((k >>> 20) & 3) * 0.05f);
                }
                for (int s = -1; s <= 1; s += 2) for (int i = 0; i < 3; i++) box(b, s * 0.56f - U, -0.7f + i * 0.08f, -0.2f + i * 0.12f, s * 0.56f + U, 0.0f, -0.12f + i * 0.12f, 0.84f);
            }
            case 19 -> { if (female) box(b, 0.0f, -0.55f, back - 0.04f, 0.6f, 0.0f, back + 0.1f, 0.96f); }
            default -> { }
        }
        return b;
    }

    /** Whether a style shaves or leaves stubble on the sides and back (the skin painter darkens them with roots). */
    public static boolean shavedSides(boolean female, int style) { return style == 18 || style == 19 && !female; }
    /** How far down the head (texel rows from the top) the painted hair reaches at the back and the sides; 0 for bald. */
    public static int scalpRows(boolean female, int style) {
        return switch (style) {
            case 0 -> 0;
            case 1, 2, 3, 4, 10, 14, 15, 16, 18 -> female && style == 2 ? 16 : 8;
            case 19 -> female ? 16 : 7;
            default -> 16;
        };
    }

    // ------------------------------------------------------------------ facial hair (GDD 2.1: twenty presets, 0 clean-shaven)
    /**
     * Facial hair as volume (head space). Stubble (1, 2) is painted, not modelled; sideburns and moustaches are small
     * volumes on the face; beards hang from the jaw with their length.
     */
    public static Boxes beard(int index) {
        Boxes b = new Boxes();
        float f = -0.5f;   // the face
        switch (index) {
            case 3, 4 -> {                                          // short / medium boxed beard: chin and jaw, the mouth clear
                float l = index == 3 ? 2 * U : 4 * U;
                fullBeard(b, l, 0.96f);
                moustache(b, 0.34f, 1.2f * U, 0.98f);
            }
            case 5 -> {                                             // long full beard
                fullBeard(b, 5 * U, 0.96f);
                box(b, -0.38f, JAW - 0.48f, f - 2.5f * U, 0.38f, JAW - 5 * U, f + 0.12f, 0.93f);
                box(b, -0.24f, JAW - 0.62f, f - 2 * U, 0.24f, JAW - 0.48f, f + 0.06f, 0.9f);
                moustache(b, 0.4f, 1.6f * U, 0.99f);
            }
            case 6 -> {                                             // short rounded full beard
                fullBeard(b, 3 * U, 0.96f);
                box(b, -0.36f, JAW - 5 * U, f - 2 * U, 0.36f, JAW - 3 * U, f + 0.08f, 0.94f);
                box(b, -0.2f, JAW - 6 * U, f - 1.5f * U, 0.2f, JAW - 5 * U, f + 0.04f, 0.92f);
                moustache(b, 0.36f, 1.2f * U, 0.98f);
            }
            case 7 -> {                                             // long tapered beard: narrowing to a point
                fullBeard(b, 2 * U, 0.96f);
                for (int i = 0; i < 5; i++) { float w = 0.36f - i * 0.065f; box(b, -w, JAW - (4 + i * 2.5f) * U, f - 2 * U, w, JAW - (2 + i * 2.5f) * U, f + 0.08f, 0.95f - i * 0.01f); }
                moustache(b, 0.38f, 1.2f * U, 0.98f);
            }
            case 8 -> box(b, -0.12f, JAW - 3 * U, f - 2 * U, 0.12f, -0.36f, f + 0.06f, 0.95f);    // bare goatee
            case 9 -> { box(b, -0.14f, JAW - 3 * U, f - 2 * U, 0.14f, -0.36f, f + 0.06f, 0.95f); moustache(b, 0.28f, 1.5f * U, 0.98f);
                box(b, -0.22f, -0.36f, f - 1.5f * U, -0.18f, -0.2f, f, 0.96f); box(b, 0.18f, -0.36f, f - 1.5f * U, 0.22f, -0.2f, f, 0.96f); }
            case 10 -> {                                            // pointed goatee
                box(b, -0.14f, JAW - 2 * U, f - 2 * U, 0.14f, -0.36f, f + 0.06f, 0.95f);
                box(b, -0.08f, JAW - 5 * U, f - 2 * U, 0.08f, JAW - 2 * U, f + 0.04f, 0.93f);
                box(b, -0.04f, JAW - 7 * U, f - 1.5f * U, 0.04f, JAW - 5 * U, f + 0.02f, 0.9f);
            }
            case 11 -> {                                            // Van Dyke: a pointed goatee apart from a twirled moustache
                box(b, -0.12f, JAW - 4 * U, f - 2 * U, 0.12f, JAW + 2 * U, f + 0.05f, 0.95f);
                box(b, -0.05f, JAW - 6 * U, f - 1.5f * U, 0.05f, JAW - 4 * U, f + 0.02f, 0.92f);
                moustache(b, 0.3f, 1.2f * U, 0.98f);
                box(b, -0.36f, -0.2f, f - 1.5f * U, -0.28f, -0.14f, f, 0.97f); box(b, 0.28f, -0.2f, f - 1.5f * U, 0.36f, -0.14f, f, 0.97f);
            }
            case 12 -> moustache(b, 0.3f, 0.6f * U, 0.95f);         // thin pencil moustache
            case 13 -> { moustache(b, 0.36f, 2 * U, 0.98f); box(b, -0.18f, -0.26f, f - 2 * U, 0.18f, -0.22f, f, 0.96f); }  // chevron: full, over the lip
            case 14 -> {                                            // handlebar: curled up at the ends
                moustache(b, 0.34f, 1.5f * U, 0.98f);
                for (int s = -1; s <= 1; s += 2) { box(b, s * 0.38f - U, -0.24f, f - 2 * U, s * 0.38f + U, -0.12f, f, 0.97f); box(b, s * 0.42f - U, -0.12f, f - 2 * U, s * 0.42f + U, -0.06f, f, 0.95f); }
            }
            case 15 -> {                                            // horseshoe: down past the corners of the mouth to the jaw
                moustache(b, 0.38f, 1.5f * U, 0.98f);
                box(b, -0.36f, JAW - U, f - 2 * U, -0.28f, -0.2f, f, 0.97f); box(b, 0.28f, JAW - U, f - 2 * U, 0.36f, -0.2f, f, 0.97f);
            }
            case 16 -> { box(b, -0.4f, -0.36f, f - 3 * U, 0.4f, -0.16f, f, 0.98f); box(b, -0.34f, -0.42f, f - 2.5f * U, 0.34f, -0.36f, f, 0.95f); }   // full walrus
            case 17 -> {                                            // mutton chops: sideburns sweeping into a moustache, the chin bare
                for (int s = -1; s <= 1; s += 2) {
                    box(b, s * 0.5f - U, -0.36f, f, s * 0.5f + U, EAR_TOP, -0.08f, 0.96f);
                    float x0 = s < 0 ? -0.52f : 0.34f, x1 = s < 0 ? -0.34f : 0.52f;
                    box(b, x0, -0.38f, f - 2 * U, x1, -0.2f, f + 0.1f, 0.95f);
                }
                moustache(b, 0.4f, 1.5f * U, 0.98f);
            }
            case 18 -> { box(b, -0.52f, -0.12f, f + 0.12f, -0.48f, EAR_TOP, -0.12f, 0.95f); box(b, 0.48f, -0.12f, f + 0.12f, 0.52f, EAR_TOP, -0.12f, 0.95f); }
            case 19 -> { box(b, -0.52f, -0.36f, f + 0.1f, -0.48f, EAR_TOP, -0.1f, 0.95f); box(b, 0.48f, -0.36f, f + 0.1f, 0.52f, EAR_TOP, -0.1f, 0.95f); }
            default -> { }   // 0 clean-shaven; 1, 2 stubble is painted on the skin
        }
        return b;
    }

    /** A beard that covers the jaw and cheeks and hangs below the chin by l, the lips and the mouth's corners left clear. */
    private static void fullBeard(Boxes b, float l, float shade) {
        float f = -0.5f;
        box(b, -0.5f, JAW - l, f - 2 * U, 0.5f, -0.39f, f + 0.18f, shade);                 // the chin and under the mouth
        box(b, -0.52f, -0.39f, f - 1.5f * U, -0.26f, -0.12f, f + 0.12f, shade * 0.98f);    // the cheeks, beside the mouth
        box(b, 0.26f, -0.39f, f - 1.5f * U, 0.52f, -0.12f, f + 0.12f, shade * 0.98f);
        box(b, -0.53f, -0.12f, f + 0.05f, -0.48f, EAR_TOP, -0.1f, shade * 0.95f);          // up into the sideburns
        box(b, 0.48f, -0.12f, f + 0.05f, 0.53f, EAR_TOP, -0.1f, shade * 0.95f);
    }

    private static void moustache(Boxes b, float half, float thick, float shade) {
        box(b, -half, -0.27f - thick, -0.5f - 2 * U, half, -0.235f, -0.5f, shade);   // under the nose, over the lip
    }

    /** Whether a facial-hair preset is stubble (painted, how dense) - 0 none. */
    public static float stubble(int index) { return index == 1 ? 0.25f : index == 2 ? 0.45f : index >= 3 && index <= 7 ? 0.3f : 0; }
}
