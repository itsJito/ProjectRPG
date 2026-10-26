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
    private final java.util.Map<String, Double> crimeNotices = new java.util.HashMap<>();
    /** Presentation cooldown only: evidence, witnesses and bounty transactions still commit normally. */
    public void crime(String type, String text) {
        if (now - crimeNotices.getOrDefault(type, -1e30) < 30) return;
        crimeNotices.put(type, now); add(text, Kind.CRIME);
    }

    public void say(String text) { add(text, Kind.GAME); }
    public void warn(String text) { add(text, Kind.WARN); }
    public void add(String text, Kind kind) {
        lines.addLast(new Line(text, kind, now));
        total++;
        while (lines.size() > 200) lines.removeFirst();
    }
    public List<Line> last(int n) {
        List<Line> l = new ArrayList<>(lines);
        return l.subList(Math.max(0, l.size() - n), l.size());
    }
    /** How many lines were ever added (the interface wakes the chat when it grows). */
    public int total() { return total; }
    public boolean contains(String s) { for (Line l : lines) if (l.text().contains(s)) return true; return false; }
}
