package rpg.interact;

/** What a request came to (GDD 3.2): committed, rejected with a reason code, interrupted, or started as timed work. */
public record Result(Status status, String reason) {
    public enum Status { COMMITTED, REJECTED, INTERRUPTED, STARTED }
    public static Result ok() { return new Result(Status.COMMITTED, null); }
    public static Result ok(String note) { return new Result(Status.COMMITTED, note); }
    public static Result no(String reason) { return new Result(Status.REJECTED, reason); }
    public static Result started() { return new Result(Status.STARTED, null); }
    public boolean committed() { return status == Status.COMMITTED; }
    public boolean rejected() { return status == Status.REJECTED; }
}
