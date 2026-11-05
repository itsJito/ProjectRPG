package rpg.ai;

import java.util.ArrayList;
import java.util.List;

/**
 * (0.3.2, gamefixes: proper NPC schedules per human family and subtype; an overhauled weekly schedule system - every NPC
 * on a week where each day has its own priorities, tasks and routine, never the same day twice, and nothing hard-coded to
 * a place)
 * <p>
 * A schedule is a plan of activities by the hour for each of the seven days, made for one person from their family (the
 * role: a trade, a shop, the watch, the clergy, the inn...), their calling within it and their own nature (their id is a
 * seed: an early riser, a regular at the inn, which day is their market day). It names <i>what</i> to be doing - work,
 * a meal, the market, worship, an evening at the inn, a visit, chores at home, sleep - never <i>where</i>: the brain finds
 * the where from the world (their post or workplace, their own town's inn and market, their own bed, a friend). An
 * activity is a weight on the brain's choices, not a command: danger, a crime seen, hunger or a fire still win.
 * <p>
 * The week has a shape every family shares - Solday is the week's rest (worship in the morning, the afternoon free), Merday
 * is market day - and a shape of each family's own: a tradesman works four full days, a short day and a market morning; a
 * shopkeeper opens six days and shuts early on Solday; the watch rotates through day, late and night shifts with a day off
 * that moves through the week; the clergy keep Solday's services and visit on Iovday; the inn's people work the evenings
 * and take a slow morning. Within that, each person's days differ: the hour they rise and turn in moves a little day by
 * day, which evenings they spend at the inn, when they see friends, when they do the chores.
 */
public final class Schedule {
    public enum Activity { SLEEP, MORNING, WORK, MEAL, MARKET, WORSHIP, LEISURE, VISIT, INN, CHORES, REST }
    public record Block(double from, double to, Activity what) {}
    /** The families a schedule is built for (from role and calling). */
    public enum Family { TRADE, SHOP, WATCH, CLERGY, INN, GROWER, LEISURED, NONE }

    public static final int SOLDAY = 0, MARKET_DAY = 3;
    private Schedule() {}

    public static Family family(Npc n) {
        if (n.profession == Profession.INNKEEPER || n.profession == Profession.BARTENDER || n.profession == Profession.COOK && (n.id == rpg.Ids.COOK_CITY || n.id == rpg.Ids.COOK_PALACE)) return Family.INN;
        if (n.profession == Profession.CROP_FARMER || n.profession == Profession.ORCHARD_KEEPER || n.role == Npc.Role.FARMER) return Family.GROWER;
        return switch (n.role) {
            case WORKER, CRAFTSMAN, MINER, FISHERMAN, HUNTER, CARTER, VILLAGER -> Family.TRADE;
            // a counter, a desk, a seat of office or a post someone comes to: kept through the hours, as a shop is
            case MERCHANT, FENCE, CIVIC, QUEST_GIVER, CONTACT -> Family.SHOP;
            case GUARD, LAWMAN, KNIGHT -> Family.WATCH;
            case PRIEST, HEALER, UNDERTAKER -> Family.CLERGY;
            case SCHOLAR, BEGGAR -> Family.LEISURED;
            default -> Family.NONE;   // outlaws, monsters, summoned: no week
        };
    }

    /** A person's own small numbers: 0..1, stable, from their id and a salt. */
    static float trait(long id, int salt) { long h = id * 0x9E3779B97F4A7C15L + salt * 0xBF58476D1CE4E5B9L; h ^= h >>> 31; h *= 0x94D049BB133111EBL; h ^= h >>> 29; return (h >>> 11) / (float) (1L << 53); }

    private static final java.util.Map<Long, List<Block>> cache = new java.util.HashMap<>();
    /** The plan for one day of the week (cached: a schedule never changes by itself). */
    public static List<Block> day(Npc n, int weekday) {
        long key = n.id * 8 + weekday;
        List<Block> b = cache.get(key);
        if (b == null) { b = build(n, weekday); if (cache.size() > 4096) cache.clear(); cache.put(key, b); }
        return b;
    }
    /** What the plan says now; null for someone without a week. */
    public static Activity now(Npc n, int weekday, double hour) {
        if (family(n) == Family.NONE) return null;
        List<Block> plan = day(n, weekday);
        for (Block bl : plan) if (hour >= bl.from() && hour < bl.to()) return bl.what();
        return Activity.SLEEP;
    }
    /** The block in force now (null outside the plan). */
    public static Block block(Npc n, int weekday, double hour) {
        if (family(n) == Family.NONE) return null;
        for (Block bl : day(n, weekday)) if (hour >= bl.from() && hour < bl.to()) return bl;
        return null;
    }

    /** One person's day: wake, the day's shape by family and weekday, the evening, bed - each hour moved a little by who they are and which day it is. */
    static List<Block> build(Npc n, int wd) {
        Family fam = family(n);
        List<Block> out = new ArrayList<>();
        long id = n.id;
        // the hour they rise and turn in: an early or late person, and a little different every day of the week
        float early = trait(id, 1), jitterW = trait(id, 10 + wd) - 0.5f, jitterS = trait(id, 20 + wd) - 0.5f;
        // (everyone is up by a little after seven: the working morning starts by eight, Solday's included)
        double wake = 5.4 + early * 1.2 + jitterW * 0.4;
        double bed = 21.4 + (1 - early) * 1.2 + jitterS * 0.5 + (wd == 5 || wd == 6 ? 0.4 : 0);
        // how much of a regular at the inn they are, and their own evening there (never every night)
        float regular = trait(id, 2);
        boolean innTonight = trait(id, 30 + wd) < 0.18f + regular * 0.5f && wd != SOLDAY || wd == 6 && regular > 0.4f;
        int ownMarket = (int) (trait(id, 3) * 7);
        int visitDay = (int) (trait(id, 4) * 7);
        switch (fam) {
            case TRADE, GROWER -> {
                // four full days, one short day (an afternoon off), a market morning, Solday's rest; the short day and the
                // trade's own late start move through the week person by person
                int shortDay = 1 + (int) (trait(id, 5) * 6);
                double start = wake + 0.7, lunch = 12 + (trait(id, 6 + wd) - 0.5) * 0.6;
                out.add(new Block(wake, start, Activity.MORNING));
                if (wd == SOLDAY) {
                    // Solday: a working morning, the week's rest from noon - worship for those who keep it, then visits
                    out.add(new Block(start, 12, Activity.WORK));
                    out.add(new Block(12, 13, Activity.MEAL));
                    out.add(new Block(13, 14, trait(id, 9) < 0.5f ? Activity.WORSHIP : Activity.CHORES));
                    out.add(new Block(14, 18, visitDay == wd || regular < 0.5f ? Activity.VISIT : Activity.LEISURE));
                    out.add(new Block(18, bed, Activity.REST));
                } else if (wd == MARKET_DAY || wd == ownMarket) {
                    out.add(new Block(start, start + 1.6, Activity.MARKET));
                    out.add(new Block(start + 1.6, lunch, Activity.WORK));
                    out.add(new Block(lunch, lunch + 0.9, Activity.MEAL));
                    out.add(new Block(lunch + 0.9, 17.5, Activity.WORK));
                    out.add(new Block(17.5, bed, innTonight ? Activity.INN : Activity.CHORES));
                } else if (wd == shortDay) {
                    out.add(new Block(start, lunch, Activity.WORK));
                    out.add(new Block(lunch, lunch + 1, Activity.MEAL));
                    out.add(new Block(lunch + 1, 16.5, visitDay == wd ? Activity.VISIT : Activity.LEISURE));
                    out.add(new Block(16.5, bed, innTonight ? Activity.INN : Activity.REST));
                } else {
                    double end = 17.5 + (trait(id, 40 + wd) - 0.5);
                    out.add(new Block(start, lunch, Activity.WORK));
                    out.add(new Block(lunch, lunch + 0.8, Activity.MEAL));
                    out.add(new Block(lunch + 0.8, end, Activity.WORK));
                    out.add(new Block(end, bed, innTonight ? Activity.INN : wd == visitDay ? Activity.VISIT : Activity.CHORES));
                }
            }
            case SHOP -> {
                // open six days and late on market day; Solday's door shut after the morning
                double open = Math.max(wake + 0.6, 7.2), close = wd == MARKET_DAY ? 19.5 : 18 + (trait(id, 50 + wd) - 0.5) * 0.6;
                out.add(new Block(wake, open, Activity.MORNING));
                if (wd == SOLDAY) {
                    out.add(new Block(open, 11.5, Activity.WORK));
                    out.add(new Block(11.5, 12.5, Activity.WORSHIP));
                    out.add(new Block(12.5, 13.5, Activity.MEAL));
                    out.add(new Block(13.5, 18, Activity.VISIT));
                    out.add(new Block(18, bed, Activity.REST));
                } else {
                    double lunch = 12.5 + (trait(id, 60 + wd) - 0.5) * 0.8;
                    out.add(new Block(open, lunch, Activity.WORK));
                    out.add(new Block(lunch, lunch + 0.6, Activity.MEAL));
                    out.add(new Block(lunch + 0.6, close, Activity.WORK));
                    out.add(new Block(close, bed, innTonight ? Activity.INN : wd == visitDay ? Activity.VISIT : Activity.CHORES));
                }
            }
            case WATCH -> {
                // two shifts, early (6-14) and late (14-23), turning through the week; Solday always the early shift (the
                // town's busiest morning - worship, visiting - wants the watch out); a day off that moves, never Solday;
                // the handover a little earlier or later each day, and the free hours given to something different
                // each day. Asleep at night they still wake for a fight or a cry (those outrank sleep in the brain).
                int off = 1 + (int) (trait(id, 8) * 6);
                int shift = wd == SOLDAY ? 0 : Math.floorMod(wd + (int) (trait(id, 7) * 2), 2);
                double hand = (trait(id, 100 + wd) - 0.5) * 0.6;
                Activity[] free = {Activity.CHORES, Activity.VISIT, Activity.LEISURE, Activity.MARKET, Activity.REST};
                Activity freeA = wd == MARKET_DAY ? Activity.MARKET : free[(int) (trait(id, 110 + wd) * free.length)];
                Activity freeB = free[(int) (trait(id, 120 + wd) * free.length)];
                if (wd == off) {
                    out.add(new Block(wake + 1, wake + 2, Activity.MORNING));
                    out.add(new Block(wake + 2, 12, freeA));
                    out.add(new Block(12, 13, Activity.MEAL));
                    out.add(new Block(13, 17, freeB == Activity.MARKET ? Activity.LEISURE : freeB));
                    out.add(new Block(17, bed, regular > 0.3f ? Activity.INN : Activity.REST));
                    out.add(new Block(bed, 24, Activity.SLEEP));
                } else if (shift == 0) {
                    out.add(new Block(5.0 + hand * 0.3, 5.8 + hand * 0.3, Activity.MORNING));
                    out.add(new Block(5.8 + hand * 0.3, 14 + hand, Activity.WORK));
                    out.add(new Block(14 + hand, 15 + hand, Activity.MEAL));
                    out.add(new Block(15 + hand, 19, freeA));
                    out.add(new Block(19, 21.5 + hand, innTonight ? Activity.INN : freeB == Activity.MARKET ? Activity.REST : freeB));
                    out.add(new Block(21.5 + hand, 24, Activity.SLEEP));
                } else {
                    out.add(new Block(7.0 + hand * 0.4, 7.7 + hand * 0.4, Activity.MORNING));
                    out.add(new Block(7.7 + hand * 0.4, 12.5, freeA));
                    out.add(new Block(12.5, 13.5 + hand, Activity.MEAL));
                    out.add(new Block(13.5 + hand, 23 + hand, Activity.WORK));
                    out.add(new Block(23 + hand, 24, Activity.SLEEP));
                }
                return close(out);
            }
            case CLERGY -> {
                out.add(new Block(wake, wake + 0.6, Activity.MORNING));
                if (wd == SOLDAY) {
                    out.add(new Block(wake + 0.6, 12, Activity.WORSHIP));
                    out.add(new Block(12, 13, Activity.MEAL));
                    out.add(new Block(13, 17, Activity.WORK));
                    out.add(new Block(17, bed, Activity.REST));
                } else if (wd == 4) {
                    // Iovday: the rounds - visits to the people of the town
                    out.add(new Block(wake + 0.6, 12, Activity.VISIT));
                    out.add(new Block(12, 13, Activity.MEAL));
                    out.add(new Block(13, 17.5, Activity.WORK));
                    out.add(new Block(17.5, bed, Activity.CHORES));
                } else {
                    out.add(new Block(wake + 0.6, wake + 1.4, Activity.WORSHIP));
                    out.add(new Block(wake + 1.4, 12.5, wd == MARKET_DAY ? Activity.MARKET : Activity.WORK));
                    out.add(new Block(12.5, 13.3, Activity.MEAL));
                    out.add(new Block(13.3, 18, Activity.WORK));
                    out.add(new Block(18, bed, innTonight && regular > 0.6f ? Activity.INN : Activity.CHORES));
                }
            }
            case INN -> {
                // the inn's people: a slow morning, the bar and the pot through the day into the night; the cook's
                // afternoon lull, the innkeeper's market morning for the larder; a quiet Solday morning off
                boolean cook = n.profession == Profession.COOK;
                double up = 7.4 + jitterW * 0.4 + (wd == SOLDAY ? 1.5 : 0);
                out.add(new Block(up - 0.6, up, Activity.MORNING));
                if (n.profession == Profession.INNKEEPER && wd == MARKET_DAY) { out.add(new Block(up, up + 1.5, Activity.MARKET)); up += 1.5; }
                if (wd == SOLDAY && !cook) { out.add(new Block(up, up + 1.6, Activity.WORSHIP)); up += 1.6; }
                if (cook) {
                    out.add(new Block(up, 14, Activity.WORK));
                    out.add(new Block(14, 15.2, wd == visitDay ? Activity.VISIT : Activity.REST));
                    out.add(new Block(15.2, 22.6, Activity.WORK));
                    out.add(new Block(22.6, 23.4, Activity.MEAL));
                    return close(out);
                }
                out.add(new Block(up, 23.6, Activity.WORK));
                out.add(new Block(23.6, 24, Activity.CHORES));
                return close(out);
            }
            case LEISURED -> {
                // the townsfolk without a trade: errands, visits, worship, the market, the inn - in a different order each day
                out.add(new Block(wake, wake + 0.6, Activity.MORNING));
                Activity[] am = {Activity.MARKET, Activity.VISIT, Activity.CHORES, Activity.LEISURE, Activity.WORK};
                Activity[] pm = {Activity.LEISURE, Activity.WORK, Activity.VISIT, Activity.CHORES};
                Activity morning = wd == SOLDAY ? Activity.WORSHIP : wd == MARKET_DAY ? Activity.MARKET : am[(int) (trait(id, 70 + wd) * am.length)];
                Activity afternoon = pm[(int) (trait(id, 80 + wd) * pm.length)];
                out.add(new Block(wake + 0.6, 12, morning));
                out.add(new Block(12, 13, regular > 0.55f && wd != SOLDAY ? Activity.INN : Activity.MEAL));
                out.add(new Block(13, 17.5, afternoon));
                out.add(new Block(17.5, bed, innTonight ? Activity.INN : Activity.REST));
            }
            default -> { return out; }
        }
        out.add(new Block(bed, 24, Activity.SLEEP));
        return close(out);
    }
    /** Sleep fills the hours the plan leaves before its first block. */
    private static List<Block> close(List<Block> out) {
        if (!out.isEmpty() && out.get(0).from() > 0) out.add(0, new Block(0, out.get(0).from(), Activity.SLEEP));
        return out;
    }

    /** A line for the log and the inspector: the day's plan in short. */
    public static String text(Npc n, int weekday) {
        StringBuilder s = new StringBuilder(rpg.state.WorldState.WEEKDAYS[weekday]).append(':');
        for (Block b : day(n, weekday)) s.append(String.format(" %02d:%02d %s", (int) b.from(), (int) Math.round((b.from() % 1) * 60) % 60, b.what().name().toLowerCase()));
        return s.toString();
    }
}
