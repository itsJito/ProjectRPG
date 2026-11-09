package rpg.law;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import rpg.Rpg;
import rpg.actor.Actor;
import rpg.ai.Npc;
import rpg.item.ItemDef;
import rpg.item.ItemStack;

/**
 * (0.3.5, gamefixes: lawmen's interrogations and investigations) what a witness actually took in of a culprit, kept with
 * their account of the crime, and how a suspect measures up against it when the watch questions them. Each piece is only
 * what could be seen from where the witness stood: the weapon in the culprit's hand, what they wore on their body and on
 * their head (or the colour of their hair, bare-headed), blood on them, whether they were hurt in it, their build, and -
 * for a theft - that the victim's goods went with them. Nothing here is a verdict: the more of it matches the one in
 * front of the officer now, the surer the officer is; a changed weapon, a changed coat, washed hands or a cleared pack
 * throw the likeness off, and somebody else who fits the account as well can have it turned on them instead.
 */
public final class Evidence {
    public enum Kind {
        WEAPON("the weapon", 1.0f), ATTIRE("the clothes", 1.2f), HEAD("the head", 0.6f), BLOOD("blood", 1.4f),
        WOUNDS("wounds", 0.6f), STOLEN("stolen goods", 2.0f), BUILD("build", 0.5f);
        public final String word; public final float weight;
        Kind(String word, float weight) { this.word = word; this.weight = weight; }
    }

    /** One witness's description: what each piece was (as words a witness would use, and as a key to compare). */
    public static final class Profile {
        public final Map<Kind, String> seen = new EnumMap<>(Kind.class), key = new EnumMap<>(Kind.class);
        public long victim;
        public Map<String, Object> save() {
            Map<String, Object> m = new java.util.HashMap<>();
            for (var e : seen.entrySet()) m.put(e.getKey().name(), List.of(e.getValue(), key.get(e.getKey())));
            m.put("victim", victim);
            return m;
        }
        public static Profile load(Map<String, Object> m) {
            if (m == null) return null;
            Profile p = new Profile();
            p.victim = astratto.util.Json.lng(m, "victim", 0);
            for (Kind k : Kind.values()) if (m.get(k.name()) instanceof List<?> l && l.size() == 2) { p.seen.put(k, String.valueOf(l.get(0))); p.key.put(k, String.valueOf(l.get(1))); }
            return p;
        }
        public Profile copy() { Profile p = new Profile(); p.seen.putAll(seen); p.key.putAll(key); p.victim = victim; return p; }
    }

    /** A piece of a description weighed against a suspect: whether it fits, and how the officer would put it. */
    public record Clue(Kind kind, boolean fits, String line) {}

    private Evidence() {}

    /** What a witness at this distance takes in of the culprit at the moment of the crime. */
    public static Profile observe(Rpg g, Npc witness, Actor culprit, long victim, String crime) {
        Profile p = new Profile();
        p.victim = victim;
        float d = witness.distTo(culprit);
        float light = g.vox == null ? 1 : Math.max(0.25f, g.vox.skyLight((int) Math.floor(culprit.x), (int) Math.floor(culprit.y + 1), (int) Math.floor(culprit.z)));
        float sight = d / light;   // the dark makes everything further off
        ItemDef w = culprit.main();
        if (sight < 16 && w != null && w.family != ItemDef.Family.NONE && w.family != ItemDef.Family.TOOL && !culprit.sheathed) put(p, Kind.WEAPON, article(w.name), w.family.name());
        else if (sight < 16 && (w == null || culprit.sheathed) && (crime.equals("ASSAULT") || crime.equals("MURDER"))) put(p, Kind.WEAPON, "bare fists", "FISTS");
        if (sight < 20) { String[] a = attire(culprit); put(p, Kind.ATTIRE, a[0], a[1]); }
        if (sight < 10) { String[] h = head(culprit); put(p, Kind.HEAD, h[0], h[1]); }
        if (sight < 9 && culprit.bloodied > 0.25f) put(p, Kind.BLOOD, "blood on their hands and clothes", "BLOODY");
        if (sight < 12 && culprit.hp < culprit.maxHp() * 0.75f) put(p, Kind.WOUNDS, "hurt, bleeding", "HURT");
        if (crime.equals("THEFT") || crime.equals("LIVESTOCK")) put(p, Kind.STOLEN, "made off with what they took", "GOODS");
        put(p, Kind.BUILD, build(culprit), buildKey(culprit));
        return p;
    }
    private static void put(Profile p, Kind k, String words, String key) { p.seen.put(k, words); p.key.put(k, key); }

    /** The piece as it stands on a suspect now (the same terms as a witness's). */
    public static String keyNow(Kind k, Actor s) {
        return switch (k) {
            case WEAPON -> { ItemDef w = s.main(); if (w != null && w.family != ItemDef.Family.NONE && w.family != ItemDef.Family.TOOL) yield w.family.name();
                // a pat-down finds a blade in the pack as well as one at the hip
                for (var grid : s.grids()) for (ItemStack st : grid.stacks()) if (st.def.damageType != ItemDef.DamageType.NONE && st.def.family != ItemDef.Family.TOOL && st.def.family != ItemDef.Family.ARROW && st.def.reach > 0) yield st.def.family.name();
                yield "FISTS"; }
            case ATTIRE -> attire(s)[1];
            case HEAD -> head(s)[1];
            case BLOOD -> s.bloodied > 0.25f ? "BLOODY" : "CLEAN";
            case WOUNDS -> s.hp < s.maxHp() * 0.75f ? "HURT" : "WHOLE";
            case STOLEN -> "?";
            case BUILD -> buildKey(s);
        };
    }

    /** Each piece of a description against a suspect as they stand now. */
    public static List<Clue> compare(Profile p, Actor s) {
        List<Clue> l = new ArrayList<>();
        for (var e : p.seen.entrySet()) {
            Kind k = e.getKey();
            boolean fits = k == Kind.STOLEN ? carriesStolenFrom(s, p.victim) : p.key.get(k).equals(keyNow(k, s));
            String line = switch (k) {
                case WEAPON -> fits ? "The witness saw " + e.getValue() + ", and you carry the same." : "The witness speaks of " + e.getValue() + "; you don't carry anything like it.";
                case ATTIRE -> fits ? "They described " + e.getValue() + " - as you're wearing." : "They described " + e.getValue() + ", which isn't what you're wearing.";
                case HEAD -> fits ? "And " + e.getValue() + ". So are you." : "And " + e.getValue() + " - not you.";
                case BLOOD -> fits ? "There was blood on them. There's blood on you." : "There was blood on them; you're clean.";
                case WOUNDS -> fits ? "Whoever did it was hurt. So are you." : "Whoever did it was hurt; you look whole.";
                case STOLEN -> fits ? "And these goods on you belong to the one who was robbed." : "But nothing of theirs is on you.";
                case BUILD -> fits ? "You're the build they described." : "You're not the build they described.";
            };
            l.add(new Clue(k, fits, line));
        }
        return l;
    }

    /** How well a suspect fits a description (0: nothing fits .. 1: everything), by the weight of each piece. */
    public static float fit(Profile p, Actor s) {
        float total = 0, fits = 0;
        for (Clue c : compare(p, s)) { total += c.kind.weight; if (c.fits) fits += c.kind.weight; }
        return total <= 0 ? 0.5f : fits / total;
    }

    private static boolean carriesStolenFrom(Actor s, long victim) {
        for (var grid : s.grids()) for (ItemStack st : grid.stacks()) if (st.stolen && (victim == 0 || st.owner == victim)) return true;
        return false;
    }
    private static String[] attire(Actor a) {
        ItemDef body = a.eq.def(ItemDef.Slot.BODY);
        if (body != null) return new String[]{article(body.name), body.id};
        if (a.look != null) return new String[]{"a " + colourWord(rpg.actor.Appearance.color(a.look.topColor)) + " shirt", "TOP" + a.look.topColor};
        return new String[]{"plain clothes", "PLAIN"};
    }
    private static String[] head(Actor a) {
        ItemDef h = a.eq.def(ItemDef.Slot.HEAD);
        if (h != null) return new String[]{"wearing " + article(h.name), h.id};
        if (a.look != null) return new String[]{colourWord(rpg.actor.Appearance.hairColor(a.look.hairColor)) + " hair", "HAIR" + a.look.hairColor};
        return new String[]{"bare-headed", "BARE"};
    }
    private static String build(Actor a) { float s = a.height; return (s > 1.86f ? "tall" : s < 1.7f ? "slight" : "of middling height") + (a.look != null ? (a.look.female ? ", a woman" : ", a man") : ""); }
    private static String buildKey(Actor a) { float s = a.height; return (s > 1.86f ? "T" : s < 1.7f ? "S" : "M") + (a.look != null && a.look.female ? "F" : "M"); }
    private static String article(String name) { String n = name.toLowerCase(); return ("aeiou".indexOf(n.charAt(0)) >= 0 ? "an " : "a ") + n; }
    /** A colour as a witness would name it. */
    static String colourWord(int rgb) {
        int r = (rgb >> 16) & 255, gr = (rgb >> 8) & 255, b = rgb & 255, max = Math.max(r, Math.max(gr, b)), min = Math.min(r, Math.min(gr, b));
        if (max < 60) return "black";
        if (max - min < 28) return max > 200 ? "white" : max > 120 ? "grey" : "dark grey";
        if (r >= gr && r >= b) return gr > b + 40 ? (gr > r * 0.75f ? "yellow" : "brown") : r > 150 ? "red" : "dark red";
        if (gr >= r && gr >= b) return "green";
        return r > gr ? "purple" : "blue";
    }
}
