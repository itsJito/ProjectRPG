package rpg.ai;

import java.util.List;
import rpg.Tuning;

/**
 * (0.4.2, gamefixes: make sure every NPC has a mononym - none called Fisher or Hunter) The names people go by, as data
 * ({@code tuning.json}'s "names"): the roster's names that were once only callings, and a one-word name for everyone
 * who joins the world in play, drawn by their id - the same person is always called the same.
 */
public final class Names {
    private Names() {}

    /** A roster person's name (by their Ids constant's name), or the fallback. */
    public static String roster(String key, String fallback) { return Tuning.s("names.roster." + key, fallback); }

    /** A one-word name for a person who joins the world in play: drawn from the pool by their id. */
    public static String drawn(long id, boolean female) {
        List<String> pool = pool(female ? "female" : "male");
        if (pool.isEmpty()) return female ? "Hesta" : "Varn";
        return pool.get((int) Math.floorMod(id * 2654435761L, (long) pool.size()));
    }

    /** Whether a name is one of the women's names in the pool. */
    public static boolean female(String name) { return pool("female").contains(name); }

    @SuppressWarnings("unchecked")
    private static List<String> pool(String which) {
        Object o = rpg.Tuning.raw("names." + which);
        return o instanceof List<?> l ? (List<String>) l : List.of();
    }
}
