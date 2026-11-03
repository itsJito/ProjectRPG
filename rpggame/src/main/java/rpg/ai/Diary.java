package rpg.ai;

import java.util.ArrayList;
import java.util.List;

/**
 * (0.3.0, gamefixes: dialogue that reflects the NPC's day) what a person has actually done today, in their own words.
 * It is kept from what they do - each new outcome of their errands, put the way they would put it ("sold 3 logs to the
 * Materials stall", "had a word with Eirwen") - one line per thing, repeats counted, and starts afresh each morning.
 * Asked how their day has been, they tell it from this; asked about their work, what they did of it today.
 */
public final class Diary {
    public record Entry(double minutes, String what, int times) {}
    private final List<Entry> entries = new ArrayList<>();
    private long day = -1;
    private String lastOutcome;
    public static final int KEPT = 14;

    /** Notes the person's latest outcome, if it is new and worth telling (called as they go about their day). */
    public void note(double minutes, String outcome) {
        long today = (long) Math.floor((minutes - 5 * 60) / 1440);          // a day runs from five in the morning
        if (today != day) { entries.clear(); day = today; }
        if (outcome == null || outcome.equals(lastOutcome)) return;
        lastOutcome = outcome;
        String said = phrase(outcome);
        if (said == null) return;
        if (!entries.isEmpty() && entries.getLast().what().equals(said)) { Entry e = entries.removeLast(); entries.add(new Entry(e.minutes(), said, e.times() + 1)); return; }
        for (int i = 0; i < entries.size(); i++) if (entries.get(i).what().equals(said)) { Entry e = entries.remove(i); entries.add(new Entry(e.minutes(), said, e.times() + 1)); return; }
        entries.add(new Entry(minutes, said, 1));
        if (entries.size() > KEPT) entries.removeFirst();
    }

    public List<Entry> today(double minutes) {
        long today = (long) Math.floor((minutes - 5 * 60) / 1440);
        return today == day ? List.copyOf(entries) : List.of();
    }

    /** Whether they did anything of this kind today (a word in what they said). */
    public boolean did(double minutes, String word) { for (Entry e : today(minutes)) if (e.what().contains(word)) return true; return false; }

    /** An errand's outcome as the person would tell it, first person; null for the mechanical and the not worth telling. */
    static String phrase(String o) {
        String s = o.replace("(abstract) ", "");
        int arrow = s.indexOf(" -> ");
        String status = arrow >= 0 ? s.substring(arrow + 4) : "";
        if (arrow >= 0) s = s.substring(0, arrow);
        boolean done = status.isEmpty() || status.startsWith("COMMITTED") || status.startsWith("OK") || status.startsWith("STARTED");
        if (!done) return null;
        if (s.startsWith("sold ")) return s;
        if (s.startsWith("ate ")) return "had some " + s.substring(4);
        if (s.startsWith("bought bread")) return "picked up a loaf at the store";
        if (s.startsWith("bought ") && s.contains("feed")) return "bought feed for the animals";
        if (s.startsWith("buried the dead")) return "laid someone to rest";
        if (s.startsWith("collected eggs")) return "collected the eggs";
        if (s.startsWith("put ") && s.contains("eggs")) return "put the eggs away";
        if (s.startsWith("filled the trough")) return "filled the trough";
        if (s.startsWith("took feed")) return "fetched feed from the chest";
        if (s.equals("drank from the well")) return "had a drink at the well";
        if (s.startsWith("gather ")) {
            String what = s.substring(7).replaceAll("#\\d+", "").replaceAll("\\d+", "").trim().toLowerCase();
            return what.isEmpty() ? null : "worked the " + plural(what);
        }
        if (s.startsWith("cutting logs")) return "cut logs";
        if (s.startsWith("praying")) return "said my prayers at the altar";
        if (s.startsWith("talked with ")) { String who = s.substring(12); int semi = who.indexOf(';'); return "had a word with " + (semi >= 0 ? who.substring(0, semi) : who); }
        if (s.startsWith("reported ")) { int by = s.indexOf(" by "), to = s.lastIndexOf(" to "); return to > 0 ? "reported " + article(s.substring(9, by > 0 ? by : to)) + " to " + s.substring(to + 4) : null; }
        if (s.startsWith("recorded ")) return "wrote up something I saw";
        if (s.startsWith("hiding from ")) return "had to hide from " + s.substring(12);
        if (s.startsWith("running from ")) return "ran from " + s.substring(13);
        if (s.startsWith("sheltering from the rain")) return "sheltered from the rain";
        if (s.startsWith("reached patrol point")) return "walked my round";
        if (s.startsWith("checked a spot") || s.startsWith("reached the last known")) return "went to look into a noise";
        if (s.startsWith("checked on a ")) return "checked on " + article(s.substring(13));
        if (s.startsWith("saw ") && s.contains("weapon drawn")) return "saw someone with a weapon out in town";
        if (s.startsWith("saw ") && (s.contains("cast a spell") || s.contains("loose"))) return "saw someone shooting and casting about";
        if (s.startsWith("on the way to mend")) return "mended " + s.substring(s.indexOf("the broken"), s.indexOf(" at (") > 0 ? s.indexOf(" at (") : s.length());
        if (s.startsWith("steal from stall")) return "did a bit of business at the stalls";
        if (s.startsWith("gathered up the grave")) return "fetched a fallen stranger's things";
        if (s.startsWith("told ") && s.contains("wanted")) return "told the watch where to find a wanted man";
        if (s.startsWith("set a snare")) return "set my snares";
        if (s.startsWith("foraged")) return "foraged for food";
        return null;
    }
    private static String article(String w) { w = w.trim(); return (w.matches("^[aeiou].*") ? "an " : "a ") + w; }
    private static String plural(String w) {
        if (w.endsWith("bush")) return w + "es";
        if (w.endsWith("s") || w.endsWith("spot") || w.endsWith("bed") || w.endsWith("rock") || w.endsWith("tree") || w.endsWith("vein") || w.endsWith("patch")) return w.endsWith("s") ? w : w + "s";
        return w;
    }
}
