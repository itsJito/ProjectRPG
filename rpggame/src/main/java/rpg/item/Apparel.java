package rpg.item;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import rpg.skill.Skill;

/**
 * (0.4.5, gamefixes: the clothing update) The outfits as data ({@code data/apparel.json}): the Pigskin, Rogue,
 * Prospector, Desert (Commoner, Noble and Royal), Farmer's and Lumberjack outfits. Each piece is an item like any other -
 * worn in its slot, carried, sold, stolen, looted from whoever wore it - with every requirement its outfit asks (the
 * Rogue's 50 Agility and 50 Thieving both) and the look it is drawn with ({@link ItemDef#wear}: the shape and colours
 * rpg.render.Garments reads, on the body and as the thing itself).
 */
public final class Apparel {
    private Apparel() {}

    /** An outfit: its id, name, what it asks to wear, and its pieces (in the data's order). */
    public record Outfit(String id, String name, Map<Skill, Integer> requires, List<ItemDef> pieces) {}

    private static final Map<String, Outfit> OUTFITS = new LinkedHashMap<>();

    public static Map<String, Outfit> outfits() { return Collections.unmodifiableMap(OUTFITS); }
    public static Outfit outfit(String id) { return OUTFITS.get(id); }

    /** The pieces read from the data and registered as items (called once, as the item table is made). */
    static void load(java.util.function.Function<ItemDef, ItemDef> register) {
        Map<String, Object> root;
        try (InputStream in = Apparel.class.getResourceAsStream("/data/apparel.json")) {
            if (in == null) return;
            root = astratto.util.Json.parseObject(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (Exception e) { throw new IllegalStateException("data/apparel.json could not be read", e); }
        Map<String, Object> outfits = astratto.util.Json.obj(root, "outfits");
        if (outfits == null) return;
        for (var oe : outfits.entrySet()) {
            if (!(oe.getValue() instanceof Map<?, ?> om)) continue;
            @SuppressWarnings("unchecked") Map<String, Object> o = (Map<String, Object>) om;
            Map<Skill, Integer> req = new EnumMap<>(Skill.class);
            Map<String, Object> rq = astratto.util.Json.obj(o, "requires");
            if (rq != null) for (var r : rq.entrySet()) { Skill s = Skill.byName(r.getKey()); if (s != null && r.getValue() instanceof Number n) req.put(s, n.intValue()); }
            List<ItemDef> pieces = new ArrayList<>();
            for (Object po : astratto.util.Json.list(o, "pieces")) {
                if (!(po instanceof Map<?, ?> pm)) continue;
                @SuppressWarnings("unchecked") Map<String, Object> p = (Map<String, Object>) pm;
                ItemDef d = new ItemDef(astratto.util.Json.str(p, "id", "?"), astratto.util.Json.str(p, "name", "?"));
                d.slot = ItemDef.Slot.valueOf(astratto.util.Json.str(p, "slot", "BODY"));
                d.family = d.slot == ItemDef.Slot.NECK ? ItemDef.Family.JEWEL : ItemDef.Family.ARMOR;
                d.value = (int) astratto.util.Json.num(p, "value", 10);
                d.examine = astratto.util.Json.str(p, "examine", "");
                d.armor = (float) astratto.util.Json.num(p, "armor", 0.5);
                d.weight = (float) astratto.util.Json.num(p, "weight", 0.5);
                List<Object> cells = astratto.util.Json.list(p, "cells");
                if (cells.size() == 2) { d.gw = ((Number) cells.get(0)).intValue(); d.gh = ((Number) cells.get(1)).intValue(); }
                @SuppressWarnings("unchecked") Map<String, Object> look = (Map<String, Object>) astratto.util.Json.obj(p, "look");
                d.wear = look == null ? Map.of() : Collections.unmodifiableMap(look);
                d.color = hex(look == null ? null : look.get("color"), 0x808080);
                d.outfit = oe.getKey();
                // the first requirement shows as the item's own; all of them are checked (ItemDef.requirements)
                d.reqs = req.isEmpty() ? Map.of() : Collections.unmodifiableMap(new EnumMap<>(req));
                if (!req.isEmpty()) { var first = req.entrySet().iterator().next(); d.reqSkill = first.getKey(); d.reqLevel = first.getValue(); }
                pieces.add(register.apply(d));
            }
            OUTFITS.put(oe.getKey(), new Outfit(oe.getKey(), astratto.util.Json.str(o, "name", oe.getKey()), Collections.unmodifiableMap(req), List.copyOf(pieces)));
        }
    }

    /** A colour from the data ("5B2C7A"), or the fallback. */
    public static int hex(Object o, int fallback) {
        if (!(o instanceof String s) || s.isBlank()) return fallback;
        try { return Integer.parseInt(s.startsWith("#") ? s.substring(1) : s, 16); } catch (NumberFormatException e) { return fallback; }
    }
}
