package rpg.render;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;
import rpg.item.Tier;

/**
 * (0.4.5, gamefixes: every equipment tier its own look, by its material) Each armour tier's design from
 * {@code data/armour.json}: the surface of its plates, its pauldrons, its helm's face and crest, its knees, and (for the
 * tiers that have them) a glowing seam or an accent metal. Skin paints the surface and the helm's face, Gfx builds what
 * stands off the body, HiResItems builds the pieces as things - all from the same design, so a tier reads the same worn
 * and on the ground.
 */
public record ArmourDesign(String pattern, String pauldron, String visor, String crest, String knee, int glow, int accent) {
    private static final Map<Tier, ArmourDesign> BY_TIER = new EnumMap<>(Tier.class);
    private static final ArmourDesign PLAIN = new ArmourDesign("bands", "none", "open", "none", "none", -1, -1);

    static {
        try (InputStream in = ArmourDesign.class.getResourceAsStream("/data/armour.json")) {
            if (in != null) {
                Map<String, Object> root = astratto.util.Json.parseObject(new String(in.readAllBytes(), StandardCharsets.UTF_8));
                Map<String, Object> tiers = astratto.util.Json.obj(root, "tiers");
                if (tiers != null) for (var e : tiers.entrySet()) {
                    if (!(e.getValue() instanceof Map<?, ?> m)) continue;
                    @SuppressWarnings("unchecked") Map<String, Object> d = (Map<String, Object>) m;
                    try {
                        BY_TIER.put(Tier.valueOf(e.getKey()), new ArmourDesign(astratto.util.Json.str(d, "pattern", "bands"), astratto.util.Json.str(d, "pauldron", "none"),
                                astratto.util.Json.str(d, "visor", "open"), astratto.util.Json.str(d, "crest", "none"), astratto.util.Json.str(d, "knee", "none"),
                                rpg.item.Apparel.hex(d.get("glow"), -1), rpg.item.Apparel.hex(d.get("accent"), -1)));
                    } catch (IllegalArgumentException ignored) { }
                }
            }
        } catch (Exception e) { throw new IllegalStateException("data/armour.json could not be read", e); }
    }

    /** A tier's design (a plain banded plate for one the data leaves out, or none). */
    public static ArmourDesign of(Tier t) { return t == null ? PLAIN : BY_TIER.getOrDefault(t, PLAIN); }
}
