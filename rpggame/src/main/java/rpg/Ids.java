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
            BANDIT_1 = 520, BANDIT_2 = 521, BRUTE = 522, DUMMY_0 = 530;
    /** Fixtures are numbered from here in the order they are built. */
    public static final long FIXTURES = 100;
    /** Ids made in play start here. */
    public static final long DYNAMIC = 1000;
}
