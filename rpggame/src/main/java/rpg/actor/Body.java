package rpg.actor;

/**
 * The human body plan, in model pixels: every part's size, where the joints are, and the hitbox. One pixel is
 * 1.8 m / 32, so the 32-pixel-tall figure (8 head + 12 torso + 12 legs) stands exactly as tall as its hitbox. Shared by
 * the renderer (the boxes it draws and the skin they are painted from), the engine's collider and combat's contact
 * geometry, so what is drawn, what collides and what is struck all agree.
 *
 * <p>Male: head 8 x 8 x 8, torso 8 x 12 x 4, arms and legs 4 x 12 x 4. Female: head 8 x 8 x 8, body 6 x 12 x 3, arms
 * 3 x 12 x 4, legs 4 x 12 x 4 (gamefixes, character models). The three GDD body silhouettes (Masculine, Heavy, Skinny)
 * are the same parts filled out or drawn in - a girth on the torso, a thickness on the limbs - over the same skeleton
 * heights and the same 128 x 128 skin; they are visual only, and the hitbox does not change with them.
 */
public final class Body {
    /** One model pixel in metres. */
    public static final float PX = 1.8f / 32f;
    /** Every human is 32 pixels (1.8 blocks) tall: head 8, torso 12, legs 12. */
    public static final int HEAD = 8, TORSO = 12, LEGS = 12, HEIGHT_PX = HEAD + TORSO + LEGS;
    /** The skin texture's size (texels), and texels per model pixel. */
    public static final int SKIN = 128, TEXELS = 2;

    private static final float[][] SILHOUETTE = {{1f, 1f, 1f}, {1.16f, 1.42f, 1.13f}, {0.88f, 0.84f, 0.84f}};   // torso width, torso depth, limb thickness
    /** A woman's silhouettes (Average, Curvy, Slender): softer limbs, and the fullness at the hips and bust rather than the belly. */
    private static final float[][] SILHOUETTE_F = {{1f, 1f, 0.9f}, {1.08f, 1.12f, 0.98f}, {0.9f, 0.88f, 0.8f}};
    /** Her figure (pixels): how far the waist draws in, the hips flare out and the bust stands forward, per silhouette. */
    private static final float[][] FIGURE_F = {{0.65f, 0.55f, 1.0f}, {0.4f, 0.85f, 1.3f}, {0.55f, 0.35f, 0.75f}};

    public static final Body MALE = new Body(false, 0, new int[]{8, 8, 8}, new int[]{8, 12, 4}, new int[]{4, 12, 4}, new int[]{4, 12, 4}, 0.6f);
    public static final Body FEMALE = new Body(true, 0, new int[]{8, 8, 8}, new int[]{6, 12, 3}, new int[]{3, 12, 4}, new int[]{4, 12, 4}, 0.45f);
    private static final Body[] ALL = new Body[6];
    static {
        for (int t = 0; t < 3; t++) {
            ALL[t] = t == 0 ? MALE : new Body(false, t, MALE.head, MALE.torso, MALE.arm, MALE.leg, MALE.width);
            ALL[3 + t] = t == 0 ? FEMALE : new Body(true, t, FEMALE.head, FEMALE.torso, FEMALE.arm, FEMALE.leg, FEMALE.width);
        }
    }

    public final boolean female;
    /** Width (x), height (y), depth (z) of each part. */
    public final int[] head, torso, arm, leg;
    /** The silhouette: 0 Masculine, 1 Heavy, 2 Skinny; and its torso width and depth and limb thickness (shares). */
    public final int type;
    public final float girth, depth, limb;
    /** A woman's figure in pixels (all 0 for a man): the waist drawn in, the hips flared, the bust's depth. */
    public final float waist, hips, bust;
    /** Hitbox: 1.8 blocks tall, 0.6 wide for a man and 0.45 for a woman (her narrower frame, in proportion). */
    public final float width, height = HEIGHT_PX * PX, radius;

    private Body(boolean female, int type, int[] head, int[] torso, int[] arm, int[] leg, float width) {
        this.female = female; this.type = type; this.head = head; this.torso = torso; this.arm = arm; this.leg = leg; this.width = width; this.radius = width / 2;
        float[] sil = (female ? SILHOUETTE_F : SILHOUETTE)[type];
        this.girth = sil[0]; this.depth = sil[1]; this.limb = sil[2];
        this.waist = female ? FIGURE_F[type][0] : 0; this.hips = female ? FIGURE_F[type][1] : 0; this.bust = female ? FIGURE_F[type][2] : 0;
    }

    public static Body of(Appearance look) {
        if (look == null) return MALE;
        return ALL[(look.female ? 3 : 0) + Math.floorMod(look.bodyType, 3)];
    }

    // joints, in pixels from the feet (y up), the figure facing -z with its right side at +x
    /** The hips: the top of the legs. */
    public float hipY() { return LEGS; }
    /** Where each leg hangs from the hips, either side of the centre (a woman's legs, wider than her waist, sit a half pixel in: her hips). */
    public float hipX() { return leg[0] * limb / 2f - (female ? 0.5f : 0); }
    /** The shoulder pivot: two pixels below the top of the arm, which is level with the top of the torso. */
    public float shoulderY() { return LEGS + TORSO - 2; }
    public float shoulderX() { return (torso[0] * girth + arm[0] * limb) / 2f; }
    /** The neck: the top of the torso. */
    public float neckY() { return LEGS + TORSO; }
    /** The eyes, from the feet (pixels). */
    public float eyeY() { return LEGS + TORSO + 4.5f; }

    /** Arm bones: shoulder pivot to elbow, elbow to the grip (the hand is the last pixels of the arm). */
    public static final float UPPER_ARM = 4, FOREARM = 5, ARM_TOP = 2, ARM_CUT = -4, ARM_END = -10;
    /** Leg bones: hip to knee, knee to sole. */
    public static final float THIGH = 6, SHIN = 6;
}
