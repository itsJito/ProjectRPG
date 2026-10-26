package rpg;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import rpg.actor.Actor;
import rpg.ai.Memory;
import rpg.ai.Npc;
import rpg.combat.CombatSystem;
import rpg.econ.Economy;
import rpg.interact.WorldObject;
import rpg.item.ItemStack;
import rpg.objects.Container;
import rpg.objects.Fixtures;
import rpg.objects.GroundItem;
import rpg.skill.Skill;
import rpg.skill.XpTable;
import rpg.state.DomainEvent;

/**
 * The world-state invariants (GDD 3.8), asserted against the live state on demand (the debug panel's "Run invariant
 * checks", and every acceptance run): one item, one holder, and every unit accounted for by the ledger; a shipment
 * delivered at most once and never both delivered and lost; no bounty without a valid report; XP equal to the sum of
 * the awards committed (no duplicated rewards); the XP table exact; no dangling references.
 */
public final class Invariants {
    private final Rpg g;
    /** Per actor, per skill: the XP the committed awards account for. */
    private final Map<Long, long[]> expectedXp = new HashMap<>();
    /** (offender @ jurisdiction) pairs that a valid report reached. */
    private final java.util.Set<String> reported = new java.util.HashSet<>();
    public final List<String> lastReport = new ArrayList<>();
    public int checksRun, lastFailures;

    public Invariants(Rpg g) {
        this.g = g;
        g.state.subscribe(this::onEvent);
    }

    private void onEvent(DomainEvent e) {
        if (e.type().equals("CrimeReported")) reported.add(e.subject() + "@" + e.jurisdiction());
    }

    /** Takes every actor's present XP as accounted for (the start, a load, a debug setter). */
    public void baseline() { for (Actor a : all()) rebase(a); }
    public void rebase(Actor a) { expectedXp.put(a.id, a.skills.raw()); }
    public void onAward(Actor a, Skill s, long applied) { expectedXp.computeIfAbsent(a.id, k -> a.skills.raw().clone())[s.ordinal()] += applied; }
    public java.util.Set<String> reportedPairs() { return reported; }

    private List<Actor> all() { List<Actor> l = new ArrayList<>(); l.add(g.player); l.addAll(g.npcs); l.addAll(g.dummies); if (g.monsters != null) l.addAll(g.monsters.all()); return l; }

    public List<String> check() {
        checksRun++;
        List<String> fail = new ArrayList<>(), ok = new ArrayList<>();
        // 1. one item, one holder; every unit accounted for
        IdentityHashMap<ItemStack, String> seen = new IdentityHashMap<>();
        Map<String, Long> counted = new HashMap<>();
        java.util.function.BiConsumer<ItemStack, String> at = new java.util.function.BiConsumer<>() {
            @Override public void accept(ItemStack s, String where) {
                if (s == null) return;
                String prev = seen.put(s, where);
                if (prev != null) fail.add("item stack " + s + " is in two places: " + prev + " and " + where);
                counted.merge(s.def.id, (long) s.amount, Long::sum);
                // a bag's contents are where the bag is
                if (s.inner != null) for (ItemStack in : s.inner.stacks()) accept(in, where + " / " + s.def.name);
            }
        };
        for (Actor a : all()) {
            for (ItemStack s : a.inv.stacks()) at.accept(s, a.name + " inventory");
            for (ItemStack s : a.eq.stacks()) at.accept(s, a.name + " equipment");
            for (var lodged : a.stuck) at.accept(lodged.stack(), a.name + " (lodged in the body)");
        }
        for (WorldObject o : g.objects.everything()) {
            if (o.removed) continue;
            if (o instanceof Container c) for (ItemStack s : c.contents.stacks()) at.accept(s, c.name);
            if (o instanceof GroundItem gi) at.accept(gi.stack, "ground #" + gi.id);
            if (o instanceof Fixtures.Stall st) for (ItemStack s : st.goods.stacks()) at.accept(s, "stall");
        }
        for (ItemStack s : g.econ.shopA.stock.stacks()) at.accept(s, "Store A stock");
        for (ItemStack s : g.econ.shopB.stock.stacks()) at.accept(s, "Store B stock");
        for (ItemStack s : g.econ.fence.stock.stacks()) at.accept(s, "the fence's stock");
        for (CombatSystem.Projectile p : g.combat.projectiles) at.accept(p.ammo, "arrow in flight");
        Map<String, Long> ledger = g.state.ledger();
        java.util.Set<String> ids = new java.util.HashSet<>(ledger.keySet());
        ids.addAll(counted.keySet());
        int mismatches = 0;
        for (String id : ids) {
            long want = ledger.getOrDefault(id, 0L), have = counted.getOrDefault(id, 0L);
            if (want != have) {
                mismatches++; fail.add("item conservation: " + id + " ledger " + want + " but " + have + " exist");
                // where the kind is, and what the world-state log says happened to it (to find the leak)
                if (System.getenv("RPG_CHECK_EACH") != null) {
                    for (var e : seen.entrySet()) if (e.getKey().def.id.equals(id)) System.out.println("      " + id + " x" + e.getKey().amount + " at " + e.getValue());
                    String name = rpg.item.Items.get(id).name;
                    for (DomainEvent e : g.state.all()) if (e.payload() != null && (e.payload().contains(name) || e.type().startsWith("Actor"))) System.out.println("      event " + e.line());
                }
            }
        }
        if (mismatches == 0) ok.add("item conservation: " + ids.size() + " item kinds, every unit in exactly one place");
        // 2. shipments: one transition each, delivered xor lost, cargo in one place
        Map<Long, Integer> delivered = new HashMap<>(), lost = new HashMap<>();
        for (DomainEvent e : g.state.all()) {
            if (e.type().equals("ShipmentDelivered")) delivered.merge(e.subject(), 1, Integer::sum);
            if (e.type().equals("ShipmentLost")) lost.merge(e.subject(), 1, Integer::sum);
        }
        for (Economy.Shipment s : g.econ.shipments) {
            int d = delivered.getOrDefault(s.id, 0), l = lost.getOrDefault(s.id, 0);
            if (d > 1 || l > 1 || d + l > 1) fail.add("shipment #" + s.id + " delivered " + d + " and lost " + l + " times");
            if (s.status == Economy.Status.DELIVERED && !s.crate.contents.isEmpty()) fail.add("shipment #" + s.id + " delivered but its crate still holds cargo");
        }
        ok.add("shipments: " + g.econ.shipments.size() + " checked (delivered once at most, never both)");
        // 3. no bounty without a valid report
        int bounties = 0;
        for (var j : g.law.all().entrySet()) for (var b : j.getValue().entrySet()) {
            if (b.getValue() <= 0) continue;
            bounties++;
            if (!reported.contains(b.getKey() + "@" + j.getKey())) fail.add("bounty on " + g.nameOf(b.getKey()) + " in " + j.getKey() + " with no report");
        }
        ok.add("report-before-bounty: " + bounties + " bounties, each from a report");
        // 4. XP: every actor's XP is what its committed awards add up to
        int xpBad = 0;
        for (Actor a : all()) {
            long[] want = expectedXp.get(a.id);
            if (want == null) continue;
            for (Skill s : Skill.ALL) if (Math.min(XpTable.MAX_XP, want[s.ordinal()]) != a.skills.xp(s)) { xpBad++; fail.add("XP: " + a.name + " " + s.title + " has " + a.skills.xp(s) + " but awards account for " + want[s.ordinal()]); }
        }
        if (xpBad == 0) ok.add("XP accounting: every skill of " + all().size() + " actors matches its committed awards");
        // 5. the table itself
        if (XpTable.xpFor(100) != 14_391_160L || XpTable.xpFor(60) != 273_742L || XpTable.xpFor(80) != 1_986_068L || XpTable.xpFor(90) != 5_346_332L || XpTable.levelFor(XpTable.xpFor(100) - 1) != 99)
            fail.add("XP table thresholds differ from the GDD");
        else ok.add("XP table: 60/80/90/100 thresholds exact, 14,391,160 at 100");
        // 6. references resolve (memories, quest, dead NPCs stay resolvable)
        int refs = 0;
        // (subject 0 is a crime with no known culprit - a body or blood found - and is allowed)
        for (Npc n : g.npcs) for (Memory m : n.memories) if (m.crime()) { refs++; if (m.subject != 0 && g.actor(m.subject) == null) fail.add(n.name + " remembers a crime by unknown #" + m.subject); }
        if (g.actor(Ids.CONTACT) == null) fail.add("the quest's contact reference does not resolve");
        ok.add("references: " + refs + " crime memories and the quest's NPCs resolve");
        // 8. animals: every habitat's population is its start plus births and hatchings less deaths (live and cohort together)
        if (g.animals != null) {
            String bad = g.animals.ledgerProblem();
            if (bad != null) fail.add("animal population ledger: " + bad); else ok.add("animal ledger: " + g.animals.living() + " animals, every habitat's count matches its recorded births and deaths");
        }
        lastReport.clear();
        for (String s : ok) lastReport.add("[ ok ] " + s);
        for (String s : fail) lastReport.add("[FAIL] " + s);
        lastFailures = fail.size();
        g.state.commit("InvariantsChecked", 0, 0, null, 0, fail.isEmpty() ? "all passed" : fail.size() + " failures");
        return fail;
    }

    // ------------------------------------------------------------------ persistence of the accounting
    public Map<String, Object> save() {
        Map<String, Object> m = new HashMap<>();
        for (var e : expectedXp.entrySet()) { List<Object> l = new ArrayList<>(); for (long v : e.getValue()) l.add(v); m.put(String.valueOf(e.getKey()), l); }
        Map<String, Object> out = new HashMap<>();
        out.put("xp", m);
        out.put("reported", new ArrayList<Object>(reported));
        return out;
    }

    @SuppressWarnings("unchecked")
    public void load(Map<String, Object> m) {
        expectedXp.clear(); reported.clear();
        if (m == null) { baseline(); return; }
        Map<String, Object> xp = astratto.util.Json.obj(m, "xp");
        if (xp != null) for (var e : xp.entrySet()) {
            List<Object> l = (List<Object>) e.getValue();
            long[] v = new long[Skill.COUNT];
            for (int i = 0; i < v.length && i < l.size(); i++) v[i] = ((Number) l.get(i)).longValue();
            expectedXp.put(Long.parseLong(e.getKey()), v);
        }
        List<Object> r = astratto.util.Json.list(m, "reported");
        if (r != null) for (Object o : r) reported.add(String.valueOf(o));
    }
}
