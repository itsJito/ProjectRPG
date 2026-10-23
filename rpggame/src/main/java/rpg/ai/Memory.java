package rpg.ai;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * One thing an NPC knows (GDD 3.3, 3.8 PerceptionAndMemory): what kind of fact, about whom, where and when, how sure
 * they are, and where it came from - their own eyes, or a rumor from someone they talked to. Only a witnessed crime
 * can become a report; a rumor changes how they treat the culprit.
 */
public final class Memory {
    public final String kind;          // THEFT, ASSAULT, MURDER, LOCKPICKING, NODE_EMPTY, BLOCKED, ATTACKED_BY
    public final long subject;         // the culprit, or the thing remembered
    public final long victim;
    public final String jurisdiction;
    public final double minutes;
    public float confidence;
    public final String source;        // "witnessed", "rumor from <name>", "personal"
    public boolean reported;
    public final long eventId;
    public final int value;
    public final String detail;
    /** Original eyewitness, when known, and how many times the account has been retold. */
    public long witness;
    public int relays;

    public Memory(String kind, long subject, long victim, String jurisdiction, double minutes, float confidence, String source, long eventId, int value, String detail) {
        this.kind = kind; this.subject = subject; this.victim = victim; this.jurisdiction = jurisdiction; this.minutes = minutes;
        this.confidence = confidence; this.source = source; this.eventId = eventId; this.value = value; this.detail = detail;
    }

    public boolean witnessed() { return source.equals("witnessed"); }
    public boolean crime() { return switch (kind) { case "THEFT", "ASSAULT", "MURDER", "LOCKPICKING", "VANDALISM", "LIVESTOCK" -> true; default -> false; }; }

    /** Stable incident identity; old saves/deeds without event ids keep their original time and details. */
    public boolean sameIncident(Memory other) {
        if (!kind.equals(other.kind) || subject != other.subject || victim != other.victim) return false;
        if (eventId != 0 || other.eventId != 0) return eventId != 0 && eventId == other.eventId;
        return Double.compare(minutes, other.minutes) == 0 && Objects.equals(jurisdiction, other.jurisdiction) && detail.equals(other.detail);
    }

    public Memory rumorFrom(Npc teller) {
        Memory m = new Memory(kind, subject, victim, jurisdiction, minutes, confidence * 0.7f, "rumor from " + teller.name, eventId, value, detail);
        m.witness = witness != 0 ? witness : witnessed() ? teller.id : 0;
        m.relays = relays + 1;
        m.reported = true;   // a rumor is not evidence: it is never reported
        return m;
    }

    public String line(java.util.function.LongFunction<String> names) {
        return String.format("%s %s%s (%s, %.0f%%, day %d %02d:%02d)%s", kind, names.apply(subject), victim != 0 ? " vs " + names.apply(victim) : "",
                source + (witness != 0 ? "; witness " + names.apply(witness) + ", " + relays + " retelling(s)" : ""),
                confidence * 100, (int) (minutes / 1440) + 1, (int) (minutes % 1440) / 60, (int) (minutes % 60), crime() && witnessed() ? (reported ? " [reported]" : " [unreported]") : "");
    }

    public Map<String, Object> save() {
        Map<String, Object> m = new HashMap<>();
        m.put("kind", kind); m.put("subject", subject); m.put("victim", victim); m.put("jur", jurisdiction); m.put("min", minutes); m.put("conf", (double) confidence);
        m.put("source", source); m.put("reported", reported); m.put("event", eventId); m.put("value", value); m.put("detail", detail);
        m.put("witness", witness); m.put("relays", relays);
        return m;
    }

    public static Memory load(Map<String, Object> m) {
        Memory r = new Memory(astratto.util.Json.str(m, "kind", "?"), astratto.util.Json.lng(m, "subject", 0), astratto.util.Json.lng(m, "victim", 0),
                astratto.util.Json.str(m, "jur", null), astratto.util.Json.num(m, "min", 0), (float) astratto.util.Json.num(m, "conf", 1),
                astratto.util.Json.str(m, "source", "witnessed"), astratto.util.Json.lng(m, "event", 0), astratto.util.Json.integer(m, "value", 0), astratto.util.Json.str(m, "detail", ""));
        r.reported = astratto.util.Json.bool(m, "reported", false);
        r.witness = astratto.util.Json.lng(m, "witness", 0);
        r.relays = astratto.util.Json.integer(m, "relays", r.source.startsWith("rumor from ") ? 1 : 0);
        return r;
    }
}
