package rpg.state;

/**
 * A fact about a committed change (GDD 3.8): what happened, when (tick and game time), who did it to what, where (the
 * jurisdiction), what caused it (another event's id, 0 none) and its details. Events are never commands: subscribers
 * update knowledge, journals and presentation from them, and never re-apply the change they describe.
 */
public record DomainEvent(long id, long tick, double gameMinutes, long revision, String type, long actor, long subject,
                          String jurisdiction, long cause, String payload) {
    public String line() {
        return String.format("#%d t%d %s actor=%d subject=%d%s%s %s", id, tick, type, actor, subject,
                jurisdiction == null ? "" : " @" + jurisdiction, cause == 0 ? "" : " cause=#" + cause, payload == null ? "" : payload);
    }
}
