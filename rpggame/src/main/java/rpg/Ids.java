package rpg;

/**
 * Stable entity ids for the devmap's authored fixtures and agents (GDD 3.8: save-stable ids; authored characters have
 * stable references). Things made in play take ids from the world state, above these.
 */
public final class Ids {
    private Ids() {}

    public static final long PLAYER = 1;
    public static final long MERCHANT_A = 501, LAWMAN_A = 502, GUARD = 503, WORKER = 504, VILLAGER = 505, THIEF = 506,
            MERCHANT_B = 507, LAWMAN_B = 508, CARTER = 509, QUEST_GIVER = 510, CONTACT = 511, FARMER = 512,
            UNDERTAKER = 513, FENCE = 514,
            BANDIT_1 = 520, BANDIT_2 = 521, BRUTE = 522, DUMMY_0 = 530, BOBWIN = 540, BEGGAR = 541, HEALER = 542, PRIEST = 543, HUNTER = 544, SCHOLAR = 545, FISHERMAN = 546, MINER = 547, CRAFTSMAN = 548, KNIGHT = 549, PREDATOR_HUNTER = 550, TELLER = 551,
            COOK = 552, QUARRY_WORKER = 553, BORDER_KNIGHT = 554, SHELLFISH_GATHERER = 555, RIVER_FISHERMAN = 556;
    /** DevPalace's twenty (0.2.0). */
    public static final long EMPEROR = 560, PRAETOR = 561, LEGIONARY_1 = 562, LEGIONARY_2 = 563, LEGIONARY_3 = 564, LEGIONARY_4 = 565, SENATOR = 566,
            MACELLUM = 567, PRIESTESS = 568, MEDICUS = 569, PHILOSOPHER = 570, STONEMASON = 571, PALACE_BEGGAR = 572, CITIZEN_1 = 573, CITIZEN_2 = 574,
            CITIZEN_3 = 575, FISHER_P = 576, HUNTRESS = 577, CITIZEN_4 = 578, CENTURION = 579;
    /** The six newcomers at the forester's lodge (0.2.0), each of a calling no one on the devmap held. */
    public static final long ARTHUR = 580, LANCELOT = 581, GAWAIN = 582, TRISTAN = 583, PERCIVAL = 584, MERLIN = 585;
    /** Alpha 0.2.4 support trades; stable across new games and migrations. */
    public static final long CARPENTER_CITY=586, CARPENTER_TOWN=587, CARPENTER_WOOD=588, CARPENTER_PALACE=589,
            WOODCUTTER_CITY=590, WOODCUTTER_TOWN=591, MINER_CITY=592, MINER_PALACE=593, TRADER_CITY=594, TRADER_PALACE=595;
    /**
     * (0.3.2) The farms' and the inns' people: DevCity's crop farmer and orchard keeper, DevPalace's; each inn's innkeeper,
     * bartender and cook.
     */
    public static final long CROP_FARMER_CITY = 960, ORCHARD_KEEPER_CITY = 961, CROP_FARMER_PALACE = 962, ORCHARD_KEEPER_PALACE = 963,
            INNKEEPER_CITY = 964, BARTENDER_CITY = 965, COOK_CITY = 966, INNKEEPER_PALACE = 967, BARTENDER_PALACE = 968, COOK_PALACE = 969;
    /**
     * (0.3.5) Guild Row's people: the seven guildmasters, the Armory's keeper, the outworkers (carder, spinner), the
     * Artisans' workshop's fuller and dyer, the Metallists' smelter, and a coastal fisher of the Mariners.
     */
    public static final long GUILD_METALLISTS = 970, ARMORY = 971, GUILD_FORESTERS = 972, CARDER = 973, SPINNER = 974, GUILD_ARTISANS = 975,
            FULLER = 976, DYER = 977, GUILD_FARMERS = 978, GUILD_PROVISIONERS = 979, GUILD_MARINERS = 980, GUILD_ARCANISTS = 981, SMELTER = 982, COASTAL_FISHER = 983;
    /** (0.3.7) The Shrines of Light (rpg.world.Shrines). */
    public static final long SHRINE_0 = 990;
    /** Fixtures are numbered from here in the order they are built. */
    public static final long FIXTURES = 100;
    /** The dungeons' doors, traps and chests (rpg.dungeon.Dungeons), in the order they are built. */
    public static final long DUNGEONS = 600;
    /** Ids made in play start here. */
    public static final long DYNAMIC = 1000;
}
