package rpg.ai;

import rpg.world.DevMap;

/** Routine territory preferences; immediate self-defense is evaluated before these proactive decisions. */
final class ZonePolicy {
    private ZonePolicy() {}
    /** (0.3.9) An outlaw of the wilds - unless they walk with the player now, under the player's word. */
    static boolean outlaw(Npc n) { return n.companionOf == 0 && (n.role == Npc.Role.BANDIT || n.role == Npc.Role.BRUTE || n.role == Npc.Role.THIEF); }
    static boolean routineAllowed(Npc n, float x, float z) {
        String jurisdiction = DevMap.jurisdiction(x, z);
        return switch (n.role) {
            case BANDIT, BRUTE -> DevMap.WILDS.equals(jurisdiction);
            case GUARD, LAWMAN -> n.homeJurisdiction.equals(jurisdiction);
            default -> true;
        };
    }
}
