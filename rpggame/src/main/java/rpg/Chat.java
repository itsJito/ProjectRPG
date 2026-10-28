package rpg;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** The message log shown in the chatbox: game messages, dialogue, warnings. */
public final class Chat {
    public enum Kind { GAME, DIALOGUE, WARN, LEVEL, DEBUG, CRIME }
    public record Line(String text, Kind kind, double at) {}

    private final ArrayDeque<Line> lines = new ArrayDeque<>();
    private int total;
    public double now;

    public void say(String text) { add(text, Kind.GAME); }
    public void warn(String text) { add(text, Kind.WARN); }
    public void add(String text, Kind kind) {
        lines.addLast(new Line(text, kind, now));
        total++;
        while (lines.size() > 200) lines.removeFirst();
    }
    /**
     * A crime notice, at most once every 30 seconds for each kind of crime (gamefixes: the same crime spammed the box -
     * every witness, every report). Notices held back are not lost to the game: the law still records each one.
     */
    public static final double CRIME_PAUSE = 30;
    private final java.util.Map<String, Double> crimeSaid = new java.util.HashMap<>();
    public boolean crime(String kind, String text) {
        Double at = crimeSaid.get(kind);
        if (at != null && now - at < CRIME_PAUSE && now >= at) return false;
        crimeSaid.put(kind, now);
        add(text, Kind.CRIME);
        return true;
    }
    public List<Line> last(int n) {
        List<Line> l = new ArrayList<>(lines);
        return l.subList(Math.max(0, l.size() - n), l.size());
    }
    /** How many lines were ever added (the interface wakes the chat when it grows). */
    public int total() { return total; }
    public boolean contains(String s) { for (Line l : lines) if (l.text().contains(s)) return true; return false; }
}
