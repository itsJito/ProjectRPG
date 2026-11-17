package rpg.quest;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import rpg.Rpg;
import rpg.actor.Actor;
import rpg.ai.Npc;

/**
 * (0.4.8, gamefixes: the journal redone - main and side quests in a colour-coded list, an overview with "Set Active", a
 * filter and a sort) every quest the world offers the player, as the journal shows it.
 *
 * <p>The entries are read fresh from the world each time they are asked for: the authored quests from {@code
 * data/quests.json} (their state the quest's own - Quests - their giver and goal found by entity id, where those people
 * are now), and the warrants the player carries (side quests the world made: a fugitive, the watch that wants them, the
 * last place they were seen). Each is unstarted, under way, completed or failed; each may ask something of the player
 * before it can be taken (a combat level, a skill's level), met or not.
 *
 * <p>One quest may be the active one: its destination is marked on the minimap and the world map (where the giver is, for
 * a quest not begun; where its goal is, under way). The journal's settings - whether quests not begun and quests whose
 * requirements are unmet are shown, and the sort - are kept with the active quest in the save.
 */
public final class QuestLog {
    public enum Category { MAIN, SIDE }
    public enum Status { UNSTARTED, ACTIVE, COMPLETED, FAILED }
    public enum Sort { STATUS, NAME, CATEGORY }

    /** One quest as the journal shows it. */
    public record Entry(String id, String title, Category category, Status status, String summary, String objective,
                        String requires, boolean met, String giver, float[] destination, String destinationName, List<String> notes) {}

    private final Rpg g;
    private final Map<String, Object> data;
    /** The active quest's id (null: none). */
    public String active;
    /** The journal's settings: quests not begun shown (by default not), those whose requirements are unmet shown, the sort. */
    public boolean showUnstarted, showUnmet = true;
    public Sort sort = Sort.STATUS;
    /** (0.4.8) The player's own markers on the world map: {x, z, number} each, and the next number. */
    public final List<float[]> mapMarks = new ArrayList<>();
    public int nextMark;

    public QuestLog(Rpg g) { this.g = g; this.data = read(); }

    private static Map<String, Object> read() {
        try (InputStream in = QuestLog.class.getResourceAsStream("/data/quests.json")) {
            if (in == null) return Map.of();
            return astratto.util.Json.parseObject(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (Exception e) { throw new IllegalStateException("data/quests.json could not be read", e); }
    }

    /** The list's colour for a status (from the data: red not begun, yellow under way, green done, grey failed). */
    public int color(Status s) {
        Map<String, Object> c = astratto.util.Json.obj(data, "colors");
        String hex = c == null ? null : astratto.util.Json.str(c, s.name().toLowerCase(java.util.Locale.ROOT), null);
        int fallback = switch (s) { case UNSTARTED -> 0xD8584A; case ACTIVE -> 0xE8C84A; case COMPLETED -> 0x6AC46A; case FAILED -> 0x8A8278; };
        try { return 0xFF000000 | (hex == null ? fallback : Integer.parseInt(hex, 16)); } catch (NumberFormatException e) { return 0xFF000000 | fallback; }
    }

    /** Every quest, unfiltered. */
    public List<Entry> all() {
        List<Entry> out = new ArrayList<>();
        Map<String, Object> quests = astratto.util.Json.obj(data, "quests");
        if (quests != null) for (var e : quests.entrySet()) if (e.getValue() instanceof Map<?, ?> m) {
            @SuppressWarnings("unchecked") Entry en = authored(e.getKey(), (Map<String, Object>) m);
            if (en != null) out.add(en);
        }
        out.addAll(warrants());
        return out;
    }

    /** The quests the journal lists under a category, by its filter and sort. */
    public List<Entry> shown(Category c) {
        List<Entry> out = new ArrayList<>();
        for (Entry e : all()) {
            if (e.category() != c) continue;
            if (e.status() == Status.UNSTARTED && !showUnstarted) continue;
            if (!e.met() && !showUnmet) continue;
            out.add(e);
        }
        Comparator<Entry> by = switch (sort) {
            case NAME -> Comparator.comparing(Entry::title, String.CASE_INSENSITIVE_ORDER);
            case CATEGORY -> Comparator.comparing((Entry e) -> e.category()).thenComparing(Entry::title, String.CASE_INSENSITIVE_ORDER);
            default -> Comparator.comparing((Entry e) -> switch (e.status()) { case ACTIVE -> 0; case UNSTARTED -> 1; case COMPLETED -> 2; case FAILED -> 3; }).thenComparing(Entry::title, String.CASE_INSENSITIVE_ORDER);
        };
        out.sort(by);
        return out;
    }

    public Entry find(String id) { if (id == null) return null; for (Entry e : all()) if (e.id().equals(id)) return e; return null; }

    /** The active quest's destination on the map ({x, z}) and its name, if it has one now. */
    public Entry activeEntry() {
        Entry e = find(active);
        if (e == null && active != null) active = null;          // finished, failed or gone: nothing stays set
        return e;
    }
    /** "Set Active" (or, on the active one, clear it). */
    public void toggleActive(String id) { active = id != null && id.equals(active) ? null : id; }

    // ------------------------------------------------------------------ the entries
    private Entry authored(String id, Map<String, Object> d) {
        String title = astratto.util.Json.str(d, "title", id);
        Category cat = "main".equals(astratto.util.Json.str(d, "category", "side")) ? Category.MAIN : Category.SIDE;
        Actor giver = entity(astratto.util.Json.str(d, "giver", "")), target = entity(astratto.util.Json.str(d, "target", ""));
        Status st = Status.UNSTARTED; String objective = ""; List<String> notes = new ArrayList<>();
        if (id.equals("courier")) {
            var q = g.quests;
            st = switch (q.courier) { case NOT_STARTED -> Status.UNSTARTED; case ACTIVE -> Status.ACTIVE; case COMPLETED -> Status.COMPLETED; case FAILED -> Status.FAILED; };
            String gn = giver == null ? "the letter's sender" : giver.name, tn = target == null ? "its addressee" : target.name;
            objective = switch (st) {
                case UNSTARTED -> "Speak to " + gn + ", who has a letter that needs carrying.";
                case ACTIVE -> "Carry the sealed letter to " + tn + ", who lives " + q.kitWhere() + ".";
                case COMPLETED -> "The letter reached " + tn + ".";
                case FAILED -> q.failReason == null ? "The delivery can never be made." : q.failReason;
            };
            for (String j : q.journal) if (j.contains("Courier:")) notes.add(j);
        }
        // what it asks before it can be taken
        Map<String, Object> req = astratto.util.Json.obj(d, "requires");
        String requires = ""; boolean met = true;
        if (req != null) for (var r : req.entrySet()) {
            int need = r.getValue() instanceof Number n ? n.intValue() : 1;
            if (need <= 1) continue;
            int have;
            String what;
            if (r.getKey().equals("combat")) { have = g.player.skills.combatLevel(); what = "Combat level " + need; }
            else {
                rpg.skill.Skill sk;
                try { sk = rpg.skill.Skill.valueOf(r.getKey().toUpperCase(java.util.Locale.ROOT)); } catch (IllegalArgumentException e) { continue; }
                have = g.player.skills.level(sk); what = sk.title + " " + need;
            }
            requires += (requires.isEmpty() ? "" : ", ") + what;
            if (have < need) met = false;
        }
        Actor goal = st == Status.UNSTARTED ? giver : st == Status.ACTIVE ? target : null;
        float[] dest = goal != null && goal.alive ? new float[]{goal.x, goal.z} : null;
        return new Entry(id, title, cat, st, astratto.util.Json.str(d, "summary", ""), objective, requires, met,
                giver == null ? "" : giver.name, dest, goal == null ? "" : goal.name, notes);
    }

    private List<Entry> warrants() {
        List<Entry> out = new ArrayList<>();
        Map<String, Object> w = astratto.util.Json.obj(data, "warrants");
        String titleF = w == null ? "Warrant: %s" : astratto.util.Json.str(w, "title", "Warrant: %s");
        String sumF = w == null ? "" : astratto.util.Json.str(w, "summary", "");
        Category cat = w != null && "main".equals(astratto.util.Json.str(w, "category", "side")) ? Category.MAIN : Category.SIDE;
        if (g.bountyBoard == null) return out;
        for (var c : g.bountyBoard.carried()) {
            Actor t = g.actor(c.target());
            String name = t == null ? "the fugitive" : t.name;
            boolean open = t != null && t.alive && g.law.bounty(c.target(), c.jurisdiction()) > 0 && !g.law.inCustody(t);
            Status st = open ? Status.ACTIVE : Status.COMPLETED;
            String objective = open ? "Find " + name + " and bring them to justice; the watch last saw them at the mark on your map."
                    : "Bring the warrant back to the watch of " + c.jurisdiction() + " to claim " + c.reward() + " gold.";
            float[] dest = open ? new float[]{c.x(), c.z()} : lawman(c.jurisdiction());
            String sum;
            try { sum = String.format(sumF, c.jurisdiction(), name, c.reward()); } catch (java.util.IllegalFormatException e) { sum = sumF; }
            out.add(new Entry("warrant:" + c.caseId(), String.format(titleF, name), cat, st, sum, objective, "", true,
                    "The watch of " + c.jurisdiction(), dest, open ? "last seen" : "the watch", List.of()));
        }
        return out;
    }

    private float[] lawman(String jurisdiction) { Npc l = g.ai.lawmanFor(jurisdiction); return l == null ? null : new float[]{l.postX, l.postZ}; }

    private Actor entity(String idName) {
        if (idName == null || idName.isEmpty()) return null;
        try { return g.actor(rpg.Ids.class.getField(idName).getLong(null)); } catch (ReflectiveOperationException e) { return null; }
    }

    // ------------------------------------------------------------------ saved
    public Map<String, Object> save() {
        Map<String, Object> m = new HashMap<>();
        m.put("active", active); m.put("showUnstarted", showUnstarted); m.put("showUnmet", showUnmet); m.put("sort", sort.name());
        List<Object> marks = new ArrayList<>();
        for (float[] mk : mapMarks) marks.add(List.of((double) mk[0], (double) mk[1], (double) mk[2]));
        m.put("marks", marks); m.put("nextMark", nextMark);
        return m;
    }
    public void load(Map<String, Object> m) {
        if (m == null) return;
        active = astratto.util.Json.str(m, "active", null);
        showUnstarted = Boolean.TRUE.equals(m.get("showUnstarted"));
        showUnmet = !Boolean.FALSE.equals(m.get("showUnmet"));
        try { sort = Sort.valueOf(astratto.util.Json.str(m, "sort", "STATUS")); } catch (IllegalArgumentException e) { sort = Sort.STATUS; }
        mapMarks.clear();
        List<Object> marks = astratto.util.Json.list(m, "marks");
        if (marks != null) for (Object o : marks) if (o instanceof List<?> l && l.size() == 3) mapMarks.add(new float[]{((Number) l.get(0)).floatValue(), ((Number) l.get(1)).floatValue(), ((Number) l.get(2)).floatValue()});
        nextMark = (int) astratto.util.Json.num(m, "nextMark", mapMarks.size());
    }
}
