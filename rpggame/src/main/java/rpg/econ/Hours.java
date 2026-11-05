package rpg.econ;

import java.util.ArrayList;
import java.util.List;
import rpg.Rpg;
import rpg.ai.Npc;
import rpg.ai.Schedule;
import rpg.state.WorldState;

/**
 * (0.3.3, gamefixes: shops and businesses should have schedules of when they're open) A business keeps the hours its
 * keeper works: the posted hours of a shop, a stall, a bar or the forge's rack are the working blocks of the keeper's
 * week (Schedule) - so a shop opens a little after its keeper rises, shuts for their dinner, shuts early on Solday and
 * stays open late on market day, and a bar keeps the inn's long day. Nothing is a fixed table: change the keeper's week
 * and the shop's hours follow. Outside its hours a business is closed to everyone, the player and the townsfolk alike (an
 * errand to buy bread waits for the morning), and says when it opens again. The fence keeps no hours: his trade is
 * whenever nobody is looking.
 */
public final class Hours {
    private Hours() {}

    /** The open spans (hours of the day, from - to) of a business on a weekday; empty when it does not open that day. */
    public static List<double[]> spans(Rpg g, Economy.Shop s, int weekday) {
        List<double[]> out = new ArrayList<>();
        Npc keeper = g.econ.merchant(s);
        if (s == g.econ.fence) { out.add(new double[]{0, 24}); return out; }
        if (keeper == null || Schedule.family(keeper) == Schedule.Family.NONE) { out.add(new double[]{0, 24}); return out; }
        for (Schedule.Block b : Schedule.day(keeper, weekday)) {
            if (b.what() != Schedule.Activity.WORK) continue;
            if (!out.isEmpty() && b.from() - out.get(out.size() - 1)[1] < 0.05) out.get(out.size() - 1)[1] = b.to();
            else out.add(new double[]{b.from(), b.to()});
        }
        return out;
    }

    /** Whether the business is open now (by its posted hours: whether the keeper is actually behind the counter is another matter). */
    public static boolean open(Rpg g, Economy.Shop s) {
        double h = g.state.hourOfDay();
        for (double[] span : spans(g, s, g.state.weekday())) if (h >= span[0] && h < span[1]) return true;
        return false;
    }

    /** When it next opens: "at 13:05", "tomorrow (Merday) at 07:12", or "on Iovday at 07:20". */
    public static String nextOpening(Rpg g, Economy.Shop s) {
        double h = g.state.hourOfDay();
        int wd = g.state.weekday();
        for (int ahead = 0; ahead < 8; ahead++) {
            int day = (wd + ahead) % 7;
            for (double[] span : spans(g, s, day)) {
                if (ahead == 0 && span[0] <= h) continue;
                String at = "at " + clock(span[0]);
                return ahead == 0 ? at : ahead == 1 ? "tomorrow (" + WorldState.WEEKDAYS[day] + ") " + at : "on " + WorldState.WEEKDAYS[day] + " " + at;
            }
        }
        return "another day";
    }

    /** When it shuts next today ("18:02"), or null if it is not open now. */
    public static String closing(Rpg g, Economy.Shop s) {
        double h = g.state.hourOfDay();
        for (double[] span : spans(g, s, g.state.weekday())) if (h >= span[0] && h < span[1]) return clock(Math.min(24, span[1]));
        return null;
    }

    /** Today's hours in short ("07:12-12:31, 13:07-18:02"; "closed today"). */
    public static String today(Rpg g, Economy.Shop s) {
        if (s == g.econ.fence) return "whenever it's quiet";
        List<double[]> spans = spans(g, s, g.state.weekday());
        if (spans.isEmpty()) return "closed today";
        StringBuilder b = new StringBuilder();
        for (double[] span : spans) { if (b.length() > 0) b.append(", "); b.append(clock(span[0])).append('-').append(clock(Math.min(24, span[1]))); }
        return b.toString();
    }

    /** Why it will not trade now, if the hours are the reason: "Store A is closed. It opens tomorrow (Merday) at 07:12." */
    public static String closedReason(Rpg g, Economy.Shop s) {
        if (open(g, s)) return null;
        return s.name + " is closed. It opens " + nextOpening(g, s) + ".";
    }

    static String clock(double hour) {
        int m = (int) Math.round(hour * 60) % (24 * 60);
        return String.format("%02d:%02d", m / 60, m % 60);
    }
}
