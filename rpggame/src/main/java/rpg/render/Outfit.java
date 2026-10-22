package rpg.render;

/**
 * The GDD 2.1 starting-outfit catalogs as construction data: what each of the twenty tops, twenty legs and twenty shoes
 * is made of. {@link Skin} paints them onto the figure's texture (and its outer layer, for loose cloth), and the
 * renderer hangs what stands off the body - tunic and robe hems, skirts, collars, hoods, flared cuffs, toes - as
 * pieces of their own, so every entry reads as its own garment, not a recolour.
 */
public final class Outfit {
    private Outfit() {}

    // tops ------------------------------------------------------------------------------------------------------------
    public static final int SLEEVE_NONE = 0, SLEEVE_SHORT = 1, SLEEVE_ELBOW = 2, SLEEVE_LONG = 3;
    public static final int NECK_CREW = 0, NECK_V = 1, NECK_HIGH = 2, NECK_COLLAR = 3, NECK_WRAP = 4, NECK_SCOOP = 5, NECK_ONE_SHOULDER = 6;
    public static final int HEM_WAIST = 0, HEM_HIP = 1, HEM_THIGH = 2, HEM_KNEE = 3;
    public static final int LAYER_NONE = 0, LAYER_UNDERSHIRT = 1, LAYER_OVER_TUNIC = 2, LAYER_OPEN_FRONT = 3;
    public static final int T_NONE = 0, T_LACES = 1, T_BUTTONS = 2, T_SASH = 3, T_ROPE = 4, T_HOOD = 5, T_WIDE_CUFFS = 6, T_DRAPE = 7, T_ROLLED = 8;

    /** A top: sleeve length, neckline, how far the hem falls, a belt, what is layered under or over it, its detail, and how loose it hangs. */
    public record Top(int sleeve, int neck, int hem, boolean belt, int layer, int detail, float loose, boolean fitted) {}

    public static final Top[] TOPS = {
            new Top(SLEEVE_LONG, NECK_CREW, HEM_HIP, false, LAYER_NONE, T_NONE, 0.15f, false),          // Plain tunic
            new Top(SLEEVE_LONG, NECK_CREW, HEM_HIP, false, LAYER_NONE, T_NONE, 0f, true),              // Fitted everyday tunic
            new Top(SLEEVE_LONG, NECK_CREW, HEM_HIP, true, LAYER_NONE, T_NONE, 0.1f, false),            // Belted tunic
            new Top(SLEEVE_LONG, NECK_HIGH, HEM_WAIST, false, LAYER_NONE, T_BUTTONS, 0f, false),       // High-collar shirt
            new Top(SLEEVE_LONG, NECK_SCOOP, HEM_WAIST, false, LAYER_UNDERSHIRT, T_LACES, 0f, true),   // Fitted laced bodice
            new Top(SLEEVE_LONG, NECK_COLLAR, HEM_WAIST, false, LAYER_NONE, T_BUTTONS, 0f, true),      // Tailored fitted shirt
            new Top(SLEEVE_LONG, NECK_WRAP, HEM_WAIST, false, LAYER_NONE, T_SASH, 0f, true),           // Cross-wrapped fitted top
            new Top(SLEEVE_LONG, NECK_HIGH, HEM_WAIST, false, LAYER_NONE, T_NONE, 0f, true),           // High-neck fitted top
            new Top(SLEEVE_ELBOW, NECK_COLLAR, HEM_WAIST, false, LAYER_NONE, T_ROLLED, 0.1f, false),   // Open-collar shirt
            new Top(SLEEVE_LONG, NECK_V, HEM_WAIST, false, LAYER_UNDERSHIRT, T_BUTTONS, 0f, true),     // Sleeveless waistcoat (over a shirt)
            new Top(SLEEVE_SHORT, NECK_V, HEM_WAIST, false, LAYER_UNDERSHIRT, T_LACES, 0f, false),     // Lace-up vest
            new Top(SLEEVE_LONG, NECK_V, HEM_THIGH, false, LAYER_OPEN_FRONT, T_NONE, 0.1f, false),     // Long open-front vest
            new Top(SLEEVE_LONG, NECK_V, HEM_KNEE, false, LAYER_NONE, T_NONE, 0.2f, false),            // Plain robe top
            new Top(SLEEVE_SHORT, NECK_CREW, HEM_HIP, true, LAYER_OVER_TUNIC, T_NONE, 0.1f, false),    // Layered over-tunic
            new Top(SLEEVE_LONG, NECK_CREW, HEM_KNEE, false, LAYER_NONE, T_HOOD, 0.25f, false),        // Monk-style robe top
            new Top(SLEEVE_LONG, NECK_WRAP, HEM_THIGH, false, LAYER_NONE, T_SASH, 0.2f, false),        // Cross-over layered robe top
            new Top(SLEEVE_LONG, NECK_CREW, HEM_WAIST, false, LAYER_NONE, T_NONE, 0.3f, false),        // Loose pullover shirt
            new Top(SLEEVE_ELBOW, NECK_CREW, HEM_THIGH, false, LAYER_NONE, T_NONE, 0.35f, false),      // Oversized tunic
            new Top(SLEEVE_LONG, NECK_V, HEM_WAIST, false, LAYER_NONE, T_WIDE_CUFFS, 0.15f, false),    // Wide-sleeved shirt
            new Top(SLEEVE_LONG, NECK_ONE_SHOULDER, HEM_HIP, false, LAYER_NONE, T_DRAPE, 0.1f, false), // Asymmetrical draped top
    };

    // legs ------------------------------------------------------------------------------------------------------------
    public static final int L_TROUSERS = 0, L_SHORTS = 1, L_CROPPED = 2, L_BREECHES = 3, L_LONG_SKIRT = 4, L_OVERSKIRT = 5, L_DIVIDED = 6, L_PLEATED = 7, L_ROBE = 8, L_LEGGINGS = 9;
    public static final int D_NONE = 0, D_CUFF_ROLL = 1, D_SPLIT_HEM = 2, D_DRAWSTRING = 3, D_WRAPS = 4, D_STRAPS = 5, D_PANELS = 6, D_REINFORCED = 7, D_PLEATS = 8,
            D_LAYERED = 9, D_SPLIT_FRONT = 10, D_WRAPPED = 11, D_SEAMS = 12;

    /** Legs: the garment's kind, its detail, how loose it hangs (the outer layer). */
    public record Legs(int kind, int detail, float loose) {}

    public static final Legs[] LEGS = {
            new Legs(L_TROUSERS, D_NONE, 0.05f),        // Straight trousers
            new Legs(L_TROUSERS, D_SEAMS, 0f),          // Fitted trousers
            new Legs(L_TROUSERS, D_NONE, 0.3f),         // Loose trousers
            new Legs(L_TROUSERS, D_DRAWSTRING, 0.15f),  // Drawstring trousers
            new Legs(L_SHORTS, D_NONE, 0.1f),           // Knee-length shorts
            new Legs(L_CROPPED, D_CUFF_ROLL, 0.05f),    // Rolled-cuff cropped trousers
            new Legs(L_CROPPED, D_SPLIT_HEM, 0.1f),     // Split-hem cropped trousers
            new Legs(L_BREECHES, D_NONE, 0.3f),         // Loose knee breeches
            new Legs(L_LONG_SKIRT, D_NONE, 0f),         // Plain long skirt
            new Legs(L_OVERSKIRT, D_NONE, 0f),          // Short overskirt
            new Legs(L_DIVIDED, D_NONE, 0.45f),         // Divided riding skirt
            new Legs(L_PLEATED, D_PLEATS, 0f),          // Pleated skirt
            new Legs(L_ROBE, D_NONE, 0f),               // Plain robe bottom
            new Legs(L_ROBE, D_LAYERED, 0f),            // Layered robe bottom
            new Legs(L_ROBE, D_SPLIT_FRONT, 0f),        // Split-front robe bottom
            new Legs(L_ROBE, D_WRAPPED, 0f),            // Wrapped monk-style bottom
            new Legs(L_LEGGINGS, D_WRAPS, 0f),          // Leg-wrap leggings
            new Legs(L_TROUSERS, D_STRAPS, 0.1f),       // Strapped-shin trousers
            new Legs(L_LEGGINGS, D_PANELS, 0f),         // Panelled greaves-look leggings
            new Legs(L_LEGGINGS, D_REINFORCED, 0f),     // Fitted reinforced-look leggings
    };

    /** Whether the legs garment hangs as a skirt or robe from the hips (a piece of its own), and how long (model pixels). */
    public static float skirtLength(Legs l) {
        return switch (l.kind) { case L_LONG_SKIRT -> 11.2f; case L_ROBE -> l.detail == D_WRAPPED ? 9.5f : 11.6f; case L_PLEATED -> 7.5f; case L_OVERSKIRT -> 5f; default -> 0; };
    }

    // shoes -----------------------------------------------------------------------------------------------------------
    public static final int S_PLAIN = 0, S_LACES = 1, S_CUFF = 2, S_FITTED = 3, S_SOFT = 4, S_STRAPS = 5, S_PANELS = 6, S_PULL_ON = 7, S_POINTED = 8, S_ROUNDED = 9,
            S_SLIP_ON = 10, S_BUCKLE = 11, S_SANDAL_STRAP = 12, S_SANDAL_CROSS = 13, S_SANDAL_ANKLE = 14, S_SANDAL_WRAP = 15, S_SANDAL_LACED = 16;

    /** Shoes: how many texel rows of the leg they rise (24 is the whole leg), and their style. */
    public record Shoes(int rows, int style) {
        public boolean sandal() { return style >= S_SANDAL_STRAP; }
    }

    public static final Shoes[] SHOES = {
            new Shoes(12, S_PLAIN),        // Plain tall boots
            new Shoes(12, S_LACES),        // Laced tall boots
            new Shoes(12, S_CUFF),         // Folded-cuff tall boots
            new Shoes(13, S_FITTED),       // Fitted riding boots
            new Shoes(9, S_SOFT),          // Soft travel boots
            new Shoes(12, S_STRAPS),       // Strapped tall boots
            new Shoes(12, S_PANELS),       // Panelled tall boots
            new Shoes(11, S_PULL_ON),      // Tall pull-on boots
            new Shoes(3, S_PLAIN),         // Plain low shoes
            new Shoes(3, S_POINTED),       // Pointed low shoes
            new Shoes(4, S_ROUNDED),       // Rounded work shoes
            new Shoes(4, S_LACES),         // Lace-up low shoes
            new Shoes(3, S_SLIP_ON),       // Soft slip-on shoes
            new Shoes(6, S_PLAIN),         // Ankle-height shoes
            new Shoes(3, S_BUCKLE),        // Buckle-detail low shoes
            new Shoes(1, S_SANDAL_STRAP),  // Single-strap sandals
            new Shoes(1, S_SANDAL_CROSS),  // Cross-strap sandals
            new Shoes(5, S_SANDAL_ANKLE),  // Ankle-tie sandals
            new Shoes(6, S_SANDAL_WRAP),   // Open-toe wrap sandals
            new Shoes(12, S_SANDAL_LACED), // High-laced open sandals
    };

    public static Top top(int i) { return TOPS[Math.floorMod(i, TOPS.length)]; }
    public static Legs legs(int i) { return LEGS[Math.floorMod(i, LEGS.length)]; }
    public static Shoes shoes(int i) { return SHOES[Math.floorMod(i, SHOES.length)]; }
}
