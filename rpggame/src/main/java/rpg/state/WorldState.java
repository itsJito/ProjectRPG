package rpg.state;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import rpg.item.ItemDef;

/**
 * The single authoritative world state's bookkeeping (GDD 3.8): stable entity ids, the world revision, the game clock,
 * the ordered event log with causes, the requests already committed (so a repeated input cannot transact twice), and
 * the item ledger every created or destroyed unit passes through (so item conservation can be asserted at any time).
 *
 * The things themselves - actors, containers, nodes, shops, quests - live in their systems and change only inside a
 * commit: validate against the latest state, apply every mutation, then {@link #commit} the event.
 */
public final class WorldState {
    public static final int LOG_KEPT = 600;

    public long revision, tick;
    private long nextEntity = 1000, nextEvent = 1, nextRequest = 1;
    /** Game time in minutes since the devmap began (day 1, 08:00). */
    public double minutes = 8 * 60;
    private final ArrayDeque<DomainEvent> log = new ArrayDeque<>();
    public long eventsTotal;
    private final LinkedHashSet<Long> committedRequests = new LinkedHashSet<>();
    private final List<Consumer<DomainEvent>> subscribers = new ArrayList<>();
    /** Units of each item created minus destroyed: what must exist somewhere, exactly once. */
    private final Map<String, Long> ledger = new HashMap<>();

    public long newEntityId() { return nextEntity++; }
    public long newRequestId() { return nextRequest++; }
    /** Fixtures are made with fixed ids so saves and scenarios name them stably. */
    public void reserveIdsBelow(long id) { nextEntity = Math.max(nextEntity, id); }

    public void subscribe(Consumer<DomainEvent> s) { subscribers.add(s); }

    /** Whether a request has already been committed (a duplicate input must change nothing). */
    public boolean alreadyCommitted(long requestId) { return requestId != 0 && committedRequests.contains(requestId); }

    /** Records a committed change: the revision moves on, the event is appended, subscribers are told. */
    public DomainEvent commit(long requestId, String type, long actor, long subject, String jurisdiction, long cause, String payload) {
        if (requestId != 0) {
            committedRequests.add(requestId);
            if (committedRequests.size() > 4096) committedRequests.remove(committedRequests.iterator().next());
        }
        revision++;
        DomainEvent e = new DomainEvent(nextEvent++, tick, minutes, revision, type, actor, subject, jurisdiction, cause, payload);
        log.addLast(e);
        eventsTotal++;
        if (log.size() > LOG_KEPT) log.removeFirst();
        for (Consumer<DomainEvent> s : subscribers) s.accept(e);
        return e;
    }

    public DomainEvent commit(String type, long actor, long subject, String jurisdiction, long cause, String payload) {
        return commit(0, type, actor, subject, jurisdiction, cause, payload);
    }

    public List<DomainEvent> recent(int n) {
        List<DomainEvent> out = new ArrayList<>(log);
        return out.subList(Math.max(0, out.size() - n), out.size());
    }
    public List<DomainEvent> all() { return new ArrayList<>(log); }
    public DomainEvent event(long id) { for (DomainEvent e : log) if (e.id() == id) return e; return null; }

    // ------------------------------------------------------------------ the item ledger
    public void mint(ItemDef d, int n) { ledger.merge(d.id, (long) n, Long::sum); }
    public void burn(ItemDef d, int n) { ledger.merge(d.id, (long) -n, Long::sum); }
    public long ledger(String itemId) { return ledger.getOrDefault(itemId, 0L); }
    public Map<String, Long> ledger() { return new HashMap<>(ledger); }
    public void loadLedger(Map<String, Long> l) { ledger.clear(); ledger.putAll(l); }

    // ------------------------------------------------------------------ time
    public int day() { return (int) (minutes / 1440) + 1; }
    public double hourOfDay() { return (minutes % 1440) / 60.0; }
    public String clockText() {
        int m = (int) (minutes % 1440);
        return String.format("Day %d, %02d:%02d", day(), m / 60, m % 60);
    }

    // ------------------------------------------------------------------ persistence
    public long[] counters() { return new long[]{revision, tick, nextEntity, nextEvent, nextRequest, eventsTotal}; }
    public void loadCounters(long[] c) { revision = c[0]; tick = c[1]; nextEntity = c[2]; nextEvent = c[3]; nextRequest = c[4]; eventsTotal = c[5]; }
    public void loadLog(List<DomainEvent> events) { log.clear(); log.addAll(events); }
}
