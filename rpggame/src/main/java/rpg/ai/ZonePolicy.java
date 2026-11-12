package rpg.ai;

import rpg.world.DevMap;

/** Routine territory preferences; immediate self-defense is evaluated before these proactive decisions. */
final class ZonePolicy {
    private ZonePolicy() {}
    /** (0.3.9) An outlaw of the wilds - unless they walk with the player now, under the player's word. */
    static boolean outlaw(Npc n) { return n.companionOf == 0 && (n.role == Npc.Role.BANDIT || n.role == Npc.Role.BRUTE || n.role == Npc.Role.THIEF); }
    /**
     * (0.4.0) On ShadowVeil a routine stays within reach of home (rpg.Tuning "shadowveil.leash", metres): 0.4.0 has no
     * buildings, and the trades' authored spots are the devmap's - followed across the new map they sent the whole
     * roster on journeys of half a kilometre. Hunters, the watch's posses, carters, couriers and companions travel on.
     */
    static final float LEASH = rpg.Tuning.f("shadowveil.leash", 120f);
    static boolean traveller(Npc n) { return n.companionOf != 0 || n.hunt != null || n.posse || (n.role == Npc.Role.CARTER || n.role == Npc.Role.CONTACT) && n.goal == Brain.Goal.WORK; }
    /** (0.4.1) Whether a place is within a routine's reach of home (always, on the devmap). */
    static boolean withinReach(Npc n, float x, float z) {
        return !rpg.world.WorldMap.shadowveil() || traveller(n) || Math.hypot(x - n.homeX, z - n.homeZ) <= LEASH - 4;
    }
    static boolean routineAllowed(Npc n, float x, float z) {
        if (rpg.world.WorldMap.shadowveil() && !traveller(n) && Math.hypot(x - n.homeX, z - n.homeZ) > LEASH) return false;
        String jurisdiction = DevMap.jurisdiction(x, z);
        return switch (n.role) {
            case BANDIT, BRUTE -> DevMap.WILDS.equals(jurisdiction);
            case GUARD, LAWMAN -> n.homeJurisdiction.equals(jurisdiction);
            default -> true;
        };
    }
}
