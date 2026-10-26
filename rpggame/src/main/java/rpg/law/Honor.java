package rpg.law;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import astratto.util.Json;
import rpg.Rpg;
import rpg.world.DevMap;

/**
 * Honor (GDD 3.5): the player's moral conduct as one integer from -100 to +100, clamped after every change, with
 * seventeen ranks. It moves only on confirmed, meaningful acts (a greeting, a finished job, a donation, sparing a beaten
 * foe, petting an animal, releasing a catch, surrendering, paying a bounty; robbing, assaulting or murdering an innocent,
 * looting an innocent's body, killing livestock), each with its own weight and its own guard against farming - once per
 * person, once per day, once per incident - and one canonical consequence per outcome: a murder replaces the assault on
 * the same victim a moment before, it does not stack on it. It needs no witness: what people make of the player is
 * the business of what they saw and heard (memories and rumor), not of this number.
 */
public final class Honor {
    public static final int MIN = -100, MAX = 100;
    /** The rank register (GDD 3.5): rank 1 Notorious (-100..-89) to rank 17 Revered (+89..+100), 9 Neutral (-10..+10). */
    private static final int[] LOWER = {-100, -88, -76, -65, -54, -43, -32, -21, -10, 11, 22, 33, 44, 55, 66, 77, 89};
    private static final String[] NAMES = {"Notorious", "Ruthless", "Vile", "Cruel", "Hostile", "Dishonorable", "Disreputable", "Questionable", "Neutral",
            "Considerate", "Respectable", "Honorable", "Generous", "Virtuous", "Noble", "Exemplary", "Revered"};

    private final Rpg g;
    public int score;
    /** The last change, for the interface: when, how much, why. */
    public double changedAt = -99;
    public int lastDelta;
    public String lastWhy = "";
    /** One-time and once-a-day keys already spent (guarding against farming), and per-victim assaults for arbitration. */
    private final Set<String> spent = new HashSet<>();
    private final Map<Long, Integer> assaultedAt = new HashMap<>();
    private final Map<Long, Integer> assaultDelta = new HashMap<>();

    public Honor(Rpg g) { this.g = g; }

    /** The rank (1 .. 17) a score falls in. */
    public static int rank(int score) {
        int s = Math.max(MIN, Math.min(MAX, score)), r = 1;
        for (int i = 0; i < LOWER.length; i++) if (s >= LOWER[i]) r = i + 1;
        return r;
    }
    public static String rankName(int score) { return NAMES[rank(score) - 1]; }
    public int rank() { return rank(score); }
    /** High Honor (Honorable and above): a reported crime's bounty is halved (gamefixes). */
    public boolean high() { return score >= 33; }
    public String rankName() { return rankName(score); }

    /** The Honor purchase discount (GDD 3.5): the highest threshold crossed only - 5% at +33, 10% at +55, 15% at +77, 20% at +89. */
    public float discount() { return score >= 89 ? 0.2f : score >= 77 ? 0.15f : score >= 55 ? 0.1f : score >= 33 ? 0.05f : 0; }

    /** The game day (for once-a-day keys). */
    private int day() { return (int) (g.state.minutes / 1440); }

    /**
     * One moral event. key: what makes it the same event again (null: never repeats); daily: the key is good once per
     * game day rather than once ever. Returns the change made (0 if spent or nothing changed).
     */
    public int event(String kind, long subject, int delta, String key, boolean daily, String why) {
        if (delta == 0) return 0;
        if (key != null) {
            String k = kind + ":" + key + (daily ? "@" + day() : "");
            if (!spent.add(k)) return 0;
        }
        int before = score;
        score = Math.max(MIN, Math.min(MAX, score + delta));
        int made = score - before;
        if (made == 0) return 0;
        int rankBefore = rank(before);
        changedAt = g.time; lastDelta = made; lastWhy = why;
        g.state.commit("HonorChanged", g.player.id, subject, DevMap.jurisdiction(g.player.x, g.player.z), 0,
                String.format("%s %+d (%s) -> %d, %s", kind, made, why, score, rankName()));
        if (rank(score) != rankBefore) g.chat.add("Your Honor is now " + rankName() + " (" + (score > 0 ? "+" : "") + score + ").", rpg.Chat.Kind.LEVEL);
        return made;
    }

    // ------------------------------------------------------------------ the events the world raises
    /** A crime by the player (Law): its moral weight, with the one-consequence rule for an assault that became a murder. */
    public void crime(String kind, long victim, int value, boolean innocent) {
        if (!innocent) return;
        int now = (int) g.time;
        switch (kind) {
            case "THEFT" -> event("ROBBERY", victim, -Math.min(10, 2 + value / 120), null, false, "robbed " + g.nameOf(victim));
            case "ASSAULT" -> {
                Integer at = assaultedAt.get(victim);
                if (at != null && now - at < 300) return;            // the same fight: one assault
                int d = event("ASSAULT", victim, -6, null, false, "attacked " + g.nameOf(victim));
                assaultedAt.put(victim, now); assaultDelta.put(victim, d);
            }
            case "MURDER" -> {
                // the murder is the canonical consequence: the assault on the same victim a moment ago is folded into it
                Integer at = assaultedAt.get(victim);
                int refund = at != null && now - at < 300 ? -assaultDelta.getOrDefault(victim, 0) : 0;
                assaultedAt.remove(victim);
                event("MURDER", victim, -25 + refund, "victim" + victim, false, "murdered " + g.nameOf(victim));
            }
            case "LIVESTOCK" -> event("LIVESTOCK", victim, -8, "victim" + victim, false, "killed a farm animal");
            case "VANDALISM" -> event("VANDALISM", victim, -2, null, false, "vandalism");
            case "LOCKPICKING" -> event("BREAK_IN", victim, -2, "lock" + victim, true, "picked someone's lock");
            case "ARSON" -> event("ARSON", victim, -10, null, false, "set a fire in town");
            case "DISORDERLY" -> event("DISORDERLY", victim, -1, null, true, "caused a disturbance");
            default -> { }
        }
    }

    /** Taking from an innocent's body (Corpse): once per body, in place of the theft it also is. */
    public void lootedCorpse(long body) { event("LOOT_CORPSE", body, -6, "body" + body, false, "looted the dead"); }

    public void greeted(long npc) { event("GREETING", npc, 1, "npc" + npc, false, "greeted " + g.nameOf(npc) + " warmly"); }
    public void choreDone(String what) { event("CHORE", 0, 5, what, false, "finished " + what); }
    public void donated(int coins) { event("DONATION", 0, Math.max(1, Math.min(3, coins / 25)), "shrine", true, "gave " + coins + " coins to the shrine"); }
    public void spared(long foe) { event("SPARED", foe, 4, "foe" + foe, true, "spared " + g.nameOf(foe)); }
    public void petted(long animal) { event("PETTING", animal, 1, "animal" + animal, true, "petted an animal"); }
    private int releasedDay = -1, releasedCount;
    /** A live catch let go: a small credit, the first three a day only. */
    public void released() {
        if (releasedDay != day()) { releasedDay = day(); releasedCount = 0; }
        if (++releasedCount <= 3) event("RELEASE", 0, 1, null, false, "returned a catch to the water");
    }
    public void surrendered(long guard) { event("SURRENDER", guard, 3, "incident" + (long) (g.state.minutes / 60), false, "surrendered peacefully"); }
    public void paidBounty(int amount) { event("RESTITUTION", 0, 2, "paid" + (long) g.time, false, "paid a bounty of " + amount); }

    // ------------------------------------------------------------------ corpse loot
    /**
     * The supplemental loot on an eligible opponent's body (GDD 3.5, single-source): resolved once, when they die at the
     * player's hand, from the player's Honor at that moment, and stored in the body with everything else it holds -
     * nothing is rerolled on opening, reloading or coming back. High Honor leans to potions and jewellery; low Honor to
     * arrows, thrown weapons, alcohol, tobacco and money (and a guard's body to money or jewellery); neutral is the plain
     * table. Innocents' bodies carry none (there is no reward for killing the harmless). Each item is minted once.
     */
    public void corpseLoot(rpg.ai.Npc n, rpg.objects.Corpse body) {
        boolean outlaw = n.role == rpg.ai.Npc.Role.BANDIT || n.role == rpg.ai.Npc.Role.BRUTE, guard = n.role == rpg.ai.Npc.Role.GUARD || n.role == rpg.ai.Npc.Role.LAWMAN;
        if (!outlaw && !guard) return;
        int h = score;
        java.util.Random r = new java.util.Random(n.id * 0x9E3779B97F4A7C15L + (long) (g.time * 1000));
        int budget = n.role == rpg.ai.Npc.Role.BRUTE ? 3 : 2;
        java.util.List<rpg.item.ItemStack> got = new java.util.ArrayList<>();
        for (int i = 0; i < budget; i++) {
            float roll = r.nextFloat();
            rpg.item.ItemDef d; int amt = 1;
            if (h >= 22) {
                if (roll < 0.35f) d = rpg.item.Items.RESTORATIVE;
                else if (roll < 0.55f) d = rpg.item.Items.RELIGIOUS_WINE;
                else if (roll < 0.7f) d = rpg.item.Items.get(r.nextBoolean() ? "gold_ring" : "sapphire_necklace");
                else { d = rpg.item.Items.COINS; amt = 5 + r.nextInt(16); }
            } else if (h <= -22) {
                if (guard && roll < 0.6f) { if (r.nextFloat() < 0.75f) { d = rpg.item.Items.COINS; amt = 25 + r.nextInt(46); } else d = rpg.item.Items.get("gold_ring"); }
                else if (roll < 0.25f) { d = rpg.item.Items.BRONZE_ARROWS; amt = 6 + r.nextInt(12); }
                else if (roll < 0.4f) { d = rpg.item.Items.get(r.nextBoolean() ? "bronze_dart" : "bronze_knife"); amt = 3 + r.nextInt(6); }
                else if (roll < 0.6f) d = rpg.item.Items.ALE;
                else if (roll < 0.75f) d = rpg.item.Items.TOBACCO;
                else { d = rpg.item.Items.COINS; amt = 10 + r.nextInt(31); }
            } else {
                if (roll < 0.45f) { d = rpg.item.Items.COINS; amt = 4 + r.nextInt(14); }
                else if (roll < 0.75f) d = rpg.item.Items.BREAD;
                else if (roll < 0.9f) d = rpg.item.Items.SHRIMP;
                else continue;
            }
            rpg.item.ItemStack s = new rpg.item.ItemStack(d, amt, body.owner, false, body.id);
            if (body.contents.add(s)) { g.state.mint(d, amt); got.add(s); }
        }
        if (!got.isEmpty()) g.state.commit("CorpseLootResolved", g.player.id, body.id, DevMap.jurisdiction(body.x, body.z), 0, "honor " + h + " (" + rankName(h) + "): " + got);
    }

    // ------------------------------------------------------------------ saving
    public Map<String, Object> save() {
        Map<String, Object> m = new HashMap<>();
        m.put("score", score);
        m.put("spent", new java.util.ArrayList<Object>(spent));
        return m;
    }

    public void load(Map<String, Object> m) {
        spent.clear(); assaultedAt.clear(); assaultDelta.clear();
        if (m == null) { score = 0; return; }
        score = (int) Json.lng(m, "score", 0);
        var l = Json.list(m, "spent");
        if (l != null) for (Object o : l) spent.add(String.valueOf(o));
    }
}
