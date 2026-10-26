package rpg.actor;

import java.util.Random;

/**
 * A human's look (GDD 2.1, the creator's fields and limits): sex, one of three body silhouettes, one of twelve realistic
 * skin tones, one of twenty hairstyles per sex, one of twenty facial-hair presets, twenty cosmetic tops, twenty legs and
 * twenty shoes, with ten natural hair colours (shared by head and facial hair) and twenty-four garment colours. The
 * numbers stored are the GDD's stable selection ids, from zero. Purely cosmetic: nothing here touches a skill, an
 * attribute, an equipment slot or the rules.
 */
public final class Appearance {
    public static final int SKIN_TONES = 12, HAIRSTYLES = 20, BEARDS = 20, TOPS = 20, BOTTOMS = 20, SHOES = 20, COLORS = 24, HAIR_COLORS = 10, BODY_TYPES = 3;
    /** The body silhouettes (visual only): Masculine, Heavy, Skinny. */
    public static final int MASCULINE = 0, HEAVY = 1, SKINNY = 2;

    public boolean female;
    public int bodyType = MASCULINE, skin = 3, hair = 1, hairColor = 2, beard = 0, beardColor = 2, top = 0, topColor = 16, bottom = 0, bottomColor = 9, shoes = 0, shoesColor = 16;

    /** Twelve realistic skin tones, light to deep (their own catalog; never garment colours). */
    public static final int[] SKIN = {0xF6D9C4, 0xF1C9A5, 0xE8B58E, 0xDDA27A, 0xD09068, 0xC07E58, 0xAD6B47, 0x98593A, 0x82492F, 0x6C3B26, 0x56301F, 0x412519};
    /** H01-H10: the ten natural hair colours (GDD 2.1 reference swatches). */
    public static final int[] HAIR = {0x201A19, 0x39271F, 0x68452E, 0x9B7451, 0x79402D, 0x8D3723, 0xBE6030, 0xCE9464, 0xAA9167, 0xE4C78F};
    /** The sole authored creator starting point; randomisation remains optional. */
    public static Appearance henric() {
        Appearance a = new Appearance(); a.female = false; a.bodyType = 0; a.skin = 0; a.hair = 0; a.hairColor = a.beardColor = 1;
        a.beard = 9; a.top = 8; a.topColor = 5; a.bottom = 1; a.bottomColor = 16; a.shoes = 10; a.shoesColor = 23; return a;
    }

    public static final String[] HAIR_NAMES = {"Black", "Dark Brown", "Medium Brown", "Light Brown", "Chestnut", "Auburn", "Natural Red", "Strawberry Blonde", "Dark Blonde", "Light Blonde"};
    /** C01-C24: the twenty-four starting-clothes colours (GDD 2.1 reference swatches). */
    public static final int[] PALETTE = {0xD12C36, 0xF08424, 0xEAD43A, 0x9ACF34, 0x278B49, 0x707837, 0x1A827B, 0x35BEDA, 0x3570D6, 0x283B7B, 0x793DAF, 0xB69EE3,
            0xC83F9F, 0xEF97C3, 0xED6C5C, 0x7E2A45, 0x73442F, 0xAD5331, 0xC9A12D, 0xC69A6D, 0xD8C4A3, 0x898C92, 0xF4F2E8, 0x1B1C20};
    public static final String[] COLOR_NAMES = {"Red", "Orange", "Yellow", "Lime", "Green", "Olive", "Teal", "Cyan", "Blue", "Navy", "Purple", "Lavender",
            "Magenta", "Pink", "Coral", "Burgundy", "Brown", "Rust", "Gold", "Tan", "Beige", "Gray", "White", "Black"};
    public static final String[] BODY_NAMES = {"Masculine", "Heavy", "Skinny"};
    /** A woman's builds: never muscular (gamefixes); the same three ids drawn as her own silhouettes. */
    public static final String[] BODY_NAMES_FEMALE = {"Average", "Curvy", "Slender"};
    public static String[] bodyNames(boolean female) { return female ? BODY_NAMES_FEMALE : BODY_NAMES; }
    public String bodyName() { return bodyNames(female)[Math.floorMod(bodyType, BODY_TYPES)]; }

    /** GDD 2.1 hair catalogs: twenty separately authored variants per sex (index 0 is Bald). */
    public static final String[] HAIR_MALE = {"Bald", "Close-cropped", "Crew / buzz cut", "Spiky textured crop", "Short swept-back", "Short curls", "Side-part", "Curtains",
            "Ear-length layered", "Mid ponytail", "Top-knot / man-bun", "Medium tousled waves", "Straight long", "Wavy layered long", "High ponytail", "Single long braid",
            "Dual braids", "Long dreadlocks", "Mohawk", "Undercut / half-shaved"};
    public static final String[] HAIR_FEMALE = {"Bald", "Close-cropped pixie", "Short bob", "Spiky textured pixie", "Swept-back crop", "Short curls", "Side-part long bob",
            "Curtain-fringe shoulder cut", "Shoulder-length layers", "Half-up mid ponytail", "Top-knot", "Medium loose waves", "Straight long", "Wavy layered long",
            "High ponytail", "Single long braid", "Dual braids", "Long dreadlocks", "Mohawk", "Side-shaved long cut"};
    public static final String[] BEARD_NAMES = {"Clean-shaven", "Light stubble", "Dense stubble", "Short boxed beard", "Medium boxed beard", "Long full beard",
            "Short rounded full beard", "Long tapered beard", "Bare goatee", "Goatee with moustache", "Pointed goatee", "Van Dyke beard", "Thin pencil moustache",
            "Chevron moustache", "Handlebar moustache", "Horseshoe moustache", "Full walrus moustache", "Mutton-chop sideburns", "Short sideburns only", "Long sideburns only"};
    public static final String[] TOP_NAMES = {"Plain tunic", "Fitted everyday tunic", "Belted tunic", "High-collar shirt", "Fitted laced bodice", "Tailored fitted shirt",
            "Cross-wrapped fitted top", "High-neck fitted top", "Open-collar shirt", "Sleeveless waistcoat", "Lace-up vest", "Long open-front vest", "Plain robe top",
            "Layered over-tunic", "Monk-style robe top", "Cross-over layered robe top", "Loose pullover shirt", "Oversized tunic", "Wide-sleeved shirt", "Asymmetrical draped top"};
    public static final String[] BOTTOM_NAMES = {"Straight trousers", "Fitted trousers", "Loose trousers", "Drawstring trousers", "Knee-length shorts", "Rolled-cuff cropped trousers",
            "Split-hem cropped trousers", "Loose knee breeches", "Plain long skirt", "Short overskirt", "Divided riding skirt", "Pleated skirt", "Plain robe bottom",
            "Layered robe bottom", "Split-front robe bottom", "Wrapped monk-style bottom", "Leg-wrap leggings", "Strapped-shin trousers", "Panelled greaves-look leggings",
            "Fitted reinforced-look leggings"};
    public static final String[] SHOE_NAMES = {"Plain tall boots", "Laced tall boots", "Folded-cuff tall boots", "Fitted riding boots", "Soft travel boots", "Strapped tall boots",
            "Panelled tall boots", "Tall pull-on boots", "Plain low shoes", "Pointed low shoes", "Rounded work shoes", "Lace-up low shoes", "Soft slip-on shoes",
            "Ankle-height shoes", "Buckle-detail low shoes", "Single-strap sandals", "Cross-strap sandals", "Ankle-tie sandals", "Open-toe wrap sandals", "High-laced open sandals"};

    public int skinRgb() { return SKIN[Math.floorMod(skin, SKIN_TONES)]; }
    /** A garment colour by its id (C01 = 0). */
    public static int color(int index) { return PALETTE[Math.floorMod(index, COLORS)]; }
    /** A natural hair colour by its id (H01 = 0). */
    public static int hairColor(int index) { return HAIR[Math.floorMod(index, HAIR_COLORS)]; }
    public String hairName() { return (female ? HAIR_FEMALE : HAIR_MALE)[Math.floorMod(hair, HAIRSTYLES)]; }

    /** A monster's figure (its kind's name; null for a person): its body, skin and features are its kind's (gamefixes). */
    public String monster;
    public static Appearance monster(rpg.monster.MonsterKind kind, long seed) {
        Appearance a = new Appearance();
        a.monster = kind.name(); a.female = false; a.bodyType = (int) Math.floorMod(seed, 3); a.skin = (int) Math.floorMod(seed >>> 3, 4);
        a.hair = 0; a.beard = 0; a.top = (int) Math.floorMod(seed >>> 7, 3);
        return a;
    }

    public Appearance copy() {
        Appearance a = new Appearance();
        a.female = female; a.bodyType = bodyType; a.skin = skin; a.hair = hair; a.hairColor = hairColor; a.beard = beard; a.beardColor = beardColor;
        a.top = top; a.topColor = topColor; a.bottom = bottom; a.bottomColor = bottomColor; a.shoes = shoes; a.shoesColor = shoesColor;
        a.monster = monster;
        return a;
    }

    public static Appearance random(long seed) { return random(seed, null); }

    /**
     * A random look; {@code female} fixes the sex when the person already has one (a named townsman is a man whatever
     * the dice say: his face, his body and the voice he dies with all follow from it), or null to roll it too.
     */
    public static Appearance random(long seed, Boolean female) {
        Random r = new Random(seed);
        Appearance a = new Appearance();
        boolean rolled = r.nextBoolean();
        a.female = female != null ? female : rolled; a.bodyType = r.nextInt(BODY_TYPES); a.skin = r.nextInt(SKIN_TONES);
        a.hair = r.nextInt(10) == 0 ? 0 : 1 + r.nextInt(HAIRSTYLES - 1);
        a.hairColor = r.nextInt(HAIR_COLORS);
        a.beard = a.female || r.nextInt(3) == 0 ? 0 : r.nextInt(BEARDS); a.beardColor = a.hairColor;
        a.top = r.nextInt(TOPS); a.topColor = r.nextInt(COLORS);
        a.bottom = r.nextInt(BOTTOMS); a.bottomColor = r.nextInt(COLORS); a.shoes = r.nextInt(SHOES); a.shoesColor = r.nextInt(COLORS);
        return a;
    }

    public int[] toArray() { return new int[]{female ? 1 : 0, bodyType, skin, hair, hairColor, beard, beardColor, top, topColor, bottom, bottomColor, shoes, shoesColor}; }
    public static Appearance fromArray(int[] v) {
        Appearance a = new Appearance();
        if (v == null || v.length < 13) return a;
        a.female = v[0] == 1; a.bodyType = v[1]; a.skin = v[2]; a.hair = v[3]; a.hairColor = v[4]; a.beard = v[5]; a.beardColor = v[6];
        a.top = v[7]; a.topColor = v[8]; a.bottom = v[9]; a.bottomColor = v[10]; a.shoes = v[11]; a.shoesColor = v[12];
        a.clamp();
        return a;
    }

    public void clamp() {
        bodyType = Math.floorMod(bodyType, BODY_TYPES); skin = Math.floorMod(skin, SKIN_TONES); hair = Math.floorMod(hair, HAIRSTYLES);
        beard = female ? 0 : Math.floorMod(beard, BEARDS); top = Math.floorMod(top, TOPS); bottom = Math.floorMod(bottom, BOTTOMS); shoes = Math.floorMod(shoes, SHOES);
        hairColor = Math.floorMod(hairColor, HAIR_COLORS); beardColor = Math.floorMod(beardColor, HAIR_COLORS); topColor = Math.floorMod(topColor, COLORS);
        bottomColor = Math.floorMod(bottomColor, COLORS); shoesColor = Math.floorMod(shoesColor, COLORS);
    }
}
