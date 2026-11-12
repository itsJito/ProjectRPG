package rpg.world.shadowveil;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import rpg.ai.Npc;
import rpg.world.DevMap;
import rpg.world.WorldMap;

/**
 * (0.4.0, gamefixes: "transfer all NPC's there") Everyone the devmap had, brought to ShadowVeil. The game's systems are
 * the same on both maps; only where things are differs. People go to the homes the transfer rules give them (the
 * places projectrpg.txt names for their trades and towns); the animals' habitats and the monsters' lairs move whole to
 * the wilds the design gives their kinds, so a herd, a pack or a camp keeps its own shape; everything is set down on
 * the ground where it now stands. 0.4.0 has no buildings, so people keep their roles and live round their places.
 */
public final class Settle {
    private Settle() {}

    private static Map<Long, String> idNames;
    /** An id's name among rpg.Ids' constants ("LAWMAN_A"), or "". */
    public static synchronized String idName(long id) {
        if (idNames == null) {
            idNames = new HashMap<>();
            for (var f : rpg.Ids.class.getFields()) {
                if (f.getType() != long.class || !java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                try { idNames.putIfAbsent(f.getLong(null), f.getName()); } catch (IllegalAccessException ignored) { }
            }
        }
        return idNames.getOrDefault(id, "");
    }

    /** Sends a person to their ShadowVeil home before their body is made: where they stand, sleep, work and whose law is theirs. */
    public static void person(Npc n) {
        if (!WorldMap.shadowveil()) return;
        String old = n.homeJurisdiction;
        float[] home = ShadowVeil.home(idName(n.id), n.role.name(), n.profession == null ? "" : n.profession.name(), old, n.name, n.id);
        if (home == null) home = ShadowVeil.dry(WorldMap.active().spawnX, WorldMap.active().spawnZ);
        // a post a few steps from the bed (their work in the place, not on the pillow)
        float[] post = ShadowVeil.dry(home[0] + (float) Math.cos(n.id * 1.7) * 4, home[1] + (float) Math.sin(n.id * 1.7) * 4);
        // (0.4.1) a post the world names for this person (the teller at the bank in Loxford Castle's keep)
        float[] named = ShadowVeil.post(idName(n.id));
        if (named != null) { home = named; post = named; }
        n.x = home[0]; n.z = home[1]; n.y = ShadowVeil.standY(n.x, n.z);
        n.homeX = home[0]; n.homeZ = home[1];
        n.postX = post[0]; n.postZ = post[1];
        n.spawnX = n.x; n.spawnZ = n.z; n.dutyX = post[0]; n.dutyZ = post[1];
        n.chaseOffX = n.x; n.chaseOffZ = n.z;
        String here = ShadowVeil.jurisdiction(n.x, n.z);
        // the law they answer to: the town they live in; outlaws in their camps stay the wilds'
        if (!DevMap.WILDS.equals(here) || n.role == Npc.Role.BANDIT || n.role == Npc.Role.BRUTE) n.homeJurisdiction = here;
    }

    /** Every person in the roster (before their bodies are made). */
    public static void people(List<Npc> npcs) { for (Npc n : npcs) person(n); }

    // ------------------------------------------------------------------ the wild: habitats and lairs moved whole
    /** The place a devmap habitat (by its id) or a monster kind's lair moves to: the 'fauna' table's first place for it. */
    private static String destination(String group, String key) {
        Map<String, Object> fauna = Atlas.get().map("fauna");
        Map<String, Object> moves = Atlas.map(fauna, group);
        Object v = moves.get(key);
        if (v instanceof String s) return s;
        if (v instanceof List<?> l && !l.isEmpty()) return String.valueOf(l.get(0));
        Object d = moves.get("_default");
        if (d instanceof String s) return s;
        if (d instanceof List<?> l && !l.isEmpty()) return String.valueOf(l.get(0));
        return "millwood_lodge";
    }
    /** How far a habitat moves: {dx, dz} from its devmap middle to its ShadowVeil place. */
    public static float[] habitatShift(String habitatId, float cx, float cz) {
        float[] to = ShadowVeil.place(destination("habitats", habitatId));
        if (to == null) return new float[]{0, 0};
        return new float[]{to[0] - cx, to[1] - cz};
    }
    /** A monster kind's lair on ShadowVeil (by MonsterKind name). */
    public static float[] lair(String kind, float x, float z) {
        float[] to = ShadowVeil.place(destination("lairs", kind));
        return to == null ? new float[]{x, z} : to;
    }
    /** A devmap point with no home of its own (a town cat, a stray): the place the devmap's town of that point became. */
    public static float[] stray(float x, float z, long seed) {
        float[] p = ShadowVeil.home("", "", "", "DevCity", "", seed);
        return p == null ? ShadowVeil.dry(WorldMap.active().spawnX, WorldMap.active().spawnZ) : p;
    }
}
