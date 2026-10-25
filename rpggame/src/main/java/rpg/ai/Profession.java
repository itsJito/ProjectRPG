package rpg.ai;

import rpg.skill.Skill;

/** Concrete occupations, never a permission to perform every job in a family. */
public enum Profession {
    RIVER_FISHERMAN("River Fisherman", Npc.Role.FISHERMAN, Skill.FISHING),
    COASTAL_FISHERMAN("Coastal Fisherman", Npc.Role.FISHERMAN, Skill.FISHING),
    DEEP_WATER_FISHERMAN("Deep-Water Fisherman", Npc.Role.FISHERMAN, Skill.FISHING),
    SHELLFISH_GATHERER("Shellfish Gatherer", Npc.Role.FISHERMAN, Skill.FISHING),
    LAKE_NET_FISHERMAN("Lake Net Fisherman", Npc.Role.FISHERMAN, Skill.FISHING),
    ORE_MINER("Ore Miner", Npc.Role.MINER, Skill.MINING),
    QUARRY_WORKER("Quarry Worker", Npc.Role.MINER, Skill.MINING),
    GEM_PROSPECTOR("Gem Prospector", Npc.Role.MINER, Skill.MINING),
    CLAY_DIGGER("Clay Digger", Npc.Role.MINER, Skill.MINING),
    COAL_MINER("Coal Miner", Npc.Role.MINER, Skill.MINING),
    BLACKSMITH("Blacksmith", Npc.Role.CRAFTSMAN, Skill.SMITHING),
    ARMORER("Armorer", Npc.Role.CRAFTSMAN, Skill.SMITHING),
    CARPENTER("Carpenter", Npc.Role.CRAFTSMAN, Skill.CONSTRUCTION),
    COOK("Cook", Npc.Role.CRAFTSMAN, Skill.COOKING),
    TAILOR("Tailor", Npc.Role.CRAFTSMAN, Skill.CRAFTING),
    HOUSEHOLD_KNIGHT("Household Knight", Npc.Role.KNIGHT, Skill.DEFENSE),
    BORDER_KNIGHT("Border Knight", Npc.Role.KNIGHT, Skill.DEFENSE),
    ESCORT_KNIGHT("Escort Knight", Npc.Role.KNIGHT, Skill.DEFENSE),
    KNIGHT_ERRANT("Knight Errant", Npc.Role.KNIGHT, Skill.DEFENSE),
    ORDER_KNIGHT("Order Knight", Npc.Role.KNIGHT, Skill.DEFENSE),
    GAME_HUNTER("Game Hunter", Npc.Role.HUNTER, Skill.HARVESTING),
    TRAPPER("Trapper", Npc.Role.HUNTER, Skill.HARVESTING),
    MONSTER_HUNTER("Monster Hunter", Npc.Role.HUNTER, Skill.ARCHERY),
    BOUNTY_HUNTER("Bounty Hunter", Npc.Role.HUNTER, Skill.THIEVING),
    PREDATOR_HUNTER("Predator Hunter", Npc.Role.HUNTER, Skill.ARCHERY);

    public final String title;
    public final Npc.Role family;
    public final Skill skill;
    Profession(String title, Npc.Role family, Skill skill) { this.title=title; this.family=family; this.skill=skill; }
    public void assign(Npc npc) {
        if(npc.role!=family)throw new IllegalArgumentException(title+" does not belong to "+npc.role);
        if(npc.job!=null)npc.job.cancel(); npc.job=null;
        npc.profession=this; npc.title=title; npc.dutyPhase=0; npc.nextThink=0;
    }
}
