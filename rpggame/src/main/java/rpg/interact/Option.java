package rpg.interact;

/**
 * One contextual action (GDD 3.2): a stable id, its label, priority, whether it is enabled - and if not, why - and the
 * range it needs. Labels may change with state; ids do not.
 */
public record Option(String id, String label, int priority, boolean enabled, String reason, float range) {
    public static Option on(String id, String label, int priority, float range) { return new Option(id, label, priority, true, null, range); }
    public static Option off(String id, String label, int priority, String reason, float range) { return new Option(id, label, priority, false, reason, range); }
    public static Option when(boolean ok, String id, String label, int priority, String reason, float range) { return ok ? on(id, label, priority, range) : off(id, label, priority, reason, range); }
}
