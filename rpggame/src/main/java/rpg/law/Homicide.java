package rpg.law;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import rpg.Rpg;
import rpg.actor.Actor;
import rpg.ai.Memory;
import rpg.ai.Npc;
import rpg.item.ItemDef;
import rpg.item.ItemStack;

/**
 * (0.3.9, gamefixes: guards should do actual homicide investigations into murdered citizens in their zones) A murder case:
 * opened when a body is reported to the watch, worked by one of its guards - the detective - and closed with a name or
 * left cold. It runs only on what the world really holds: the body (how it was killed, how long ago), the blood round it
 * (whether the killer bled too), and what the people round the scene saw and remember - asked one by one. The officer
 * never knows who did it until the evidence says so, and an unwitnessed killing with nothing left behind stays unsolved.
 *
 * <p>The detective's walk through it (to the scene, the examination, the canvass) is the AI's (rpg.ai.Detective); this is
 * the case as the watch keeps it: its stage, its findings, its suspects, and the judgement at the end.
 */
public final class Homicide {
    /** Where a case stands: waiting for its officer, at the scene, asking round, closed with a name, or gone cold. */
    public enum Stage { OPEN, SCENE, CANVASS, CLOSED, COLD }

    /** One killing under investigation. */
    public static final class Case {
        public final long id, victim;
        /** The watch working it (the town whose officers it falls to) and where the body was found. */
        public final String jurisdiction;
        public final float x, z;
        public final double openedMinutes;
        public long detective;
        public Stage stage = Stage.OPEN;
        /** What the officer has found out, in the officer's words (the inspector shows it; the journal could). */
        public final List<String> findings = new ArrayList<>();
        /** Who it points at and how strongly (summed over the testimony and the evidence). */
        public final Map<Long, Float> suspects = new LinkedHashMap<>();
        /** The people still to be asked, and those asked. */
        public final List<Long> toAsk = new ArrayList<>(), asked = new ArrayList<>();
        /** What the killing tells of the weapon (a damage type's name; null until examined) and of the killer's state. */
        public String wound;
        public boolean killerBled;
        /** The witnesses' descriptions put together: what is known of the killer's look. */
        public Evidence.Profile profile;
        /** The name the case closed on, and how sure the watch was. */
        public long suspect;
        public float certainty;
        /** (Game time of the stage's start: not kept - a loaded case starts its stage over.) */
        public transient double stageAt = -1, askingSince = -1;
        Case(long id, long victim, String jurisdiction, float x, float z, double openedMinutes) {
            this.id = id; this.victim = victim; this.jurisdiction = jurisdiction; this.x = x; this.z = z; this.openedMinutes = openedMinutes;
        }
        public boolean open() { return stage != Stage.CLOSED && stage != Stage.COLD; }
        public void note(String line) { if (!findings.contains(line)) findings.add(line); }
    }

    /** How sure the watch must be to name someone (a bounty is put on them); below it the case waits for more. */
    public static final float CHARGE_AT = rpg.Tuning.f("homicide.chargeAt", 0.6f);
    /** How far round the scene the people who might have seen something live or stand (m), and how many are asked. */
    public static final float CANVASS_RADIUS = rpg.Tuning.f("homicide.canvassRadius", 42);
    public static final int CANVASS_MAX = rpg.Tuning.i("homicide.canvassMax", 5);
    /** How long the examination of the body takes (s), and how long the officer tries to reach one witness (s). */
    public static final float EXAMINE_TIME = rpg.Tuning.f("homicide.examineTime", 6), ASK_PATIENCE = rpg.Tuning.f("homicide.askPatience", 45);
    /** A body this fresh (s since the killing) still has wet blood: the killer cannot be far. */
    public static final float FRESH = rpg.Tuning.f("homicide.freshBlood", 180);

    private final Rpg g;
    private final Law law;
    public final List<Case> cases = new ArrayList<>();
    private long serial;

    Homicide(Rpg g, Law law) { this.g = g; this.law = law; }

    /** The case on a victim (open or not), or null. */
    public Case of(long victim) { for (Case c : cases) if (c.victim == victim) return c; return null; }
    /** The open case this officer is working, or null. */
    public Case workedBy(long officer) { for (Case c : cases) if (c.open() && c.detective == officer) return c; return null; }

    /**
     * A killing reported to the watch of a town: the case on it (a new one, or the one already open, given the new word).
     * An officer is put on it at once - the nearest guard of that town free to go, else its lawman.
     */
    public Case open(long victim, String jurisdiction, float x, float z, String why) {
        // a case is opened on the killing of a person of the place: not a beast, not a monster, not an outlaw of the wilds
        if (!(g.actor(victim) instanceof Npc person) || person instanceof rpg.monster.Monster || person.role == Npc.Role.MONSTER
                || person.role == Npc.Role.BANDIT || person.role == Npc.Role.BRUTE) return null;
        Case c = of(victim);
        if (c != null) {
            if (c.stage == Stage.COLD) { c.stage = Stage.OPEN; c.note("Reopened: " + why); assign(c); }
            return c;
        }
        c = new Case(++serial, victim, jurisdiction, x, z, g.state.minutes);
        c.note(why);
        cases.add(c);
        assign(c);
        g.state.commit("CaseOpened", c.detective, victim, jurisdiction, 0, "the killing of " + g.nameOf(victim) + ": " + why);
        return c;
    }

    /** The officer for a case: a guard of its town not already on one (nearest the scene), else the lawman. */
    void assign(Case c) {
        Npc best = null; float bd = Float.MAX_VALUE;
        for (Npc n : g.npcs) {
            if (!n.alive || n.role != Npc.Role.GUARD || !c.jurisdiction.equals(n.homeJurisdiction) || workedBy(n.id) != null) continue;
            float d = (float) Math.hypot(n.x - c.x, n.z - c.z);
            if (d < bd) { bd = d; best = n; }
        }
        if (best == null) best = g.ai.lawmanFor(c.jurisdiction);
        c.detective = best == null ? 0 : best.id;
        if (best != null) { best.nextThink = 0; c.stage = c.stage == Stage.OPEN ? Stage.SCENE : c.stage; c.stageAt = -1; }
    }

    /** Each frame: a case whose officer has fallen (or left the force's reach) passes to another. */
    public void update() {
        for (Case c : cases) {
            if (!c.open()) continue;
            if (!(g.actor(c.detective) instanceof Npc d) || !d.alive) assign(c);
        }
    }

    // ------------------------------------------------------------------ what the scene tells
    /**
     * The body examined: how the victim was killed (the last blow's kind - cut, stabbed, beaten, shot, burnt by magic),
     * how long ago, whose blood lies round it, and who lives or stands close enough to have seen. Done once per case.
     */
    public void examine(Case c) {
        Actor v = g.actor(c.victim);
        if (v != null) {
            ItemDef.DamageType t = v.cs.lastDamageType;
            c.wound = t == null ? null : t.name();
            if (t != null) c.note(switch (t) {
                case SLASH -> "Cut down with a blade.";
                case PIERCE -> v.cs.lastRanged ? "Shot - an arrow or a bolt did this." : "Stabbed: a point, driven in.";
                case BLUNT -> "Beaten to death with something heavy.";
                case MAGIC -> "Burnt and torn: magic did this.";
                default -> "No mark on the body that says how.";
            });
            if (v instanceof Npc dead && dead.diedAt >= 0)
                c.note(g.time - dead.diedAt < FRESH ? "Killed not long ago - the blood is still wet." : "Dead a while: the blood has dried.");
        }
        // blood that is not the victim's, near the body: the killer was hurt too
        if (g.gore != null) for (var e : g.gore.evidence) {
            if (e.kind != rpg.combat.Gore.Kind.BLOOD || e.source == c.victim || e.source == 0) continue;
            if (Math.hypot(e.x - c.x, e.z - c.z) > 10) continue;
            c.killerBled = true;
            c.note("Someone else bled here as well: the killer was hurt.");
            break;
        }
        // who might have seen: those who live or are standing near (nearest first), never the officer
        List<Npc> near = new ArrayList<>();
        for (Npc n : g.npcs) {
            if (!n.alive || n.id == c.detective || n.id == c.victim || !n.loaded || n.combatant() && n.role != Npc.Role.GUARD) continue;
            float home = (float) Math.hypot(n.homeX - c.x, n.homeZ - c.z), now = (float) Math.hypot(n.x - c.x, n.z - c.z);
            if (Math.min(home, now) <= CANVASS_RADIUS) near.add(n);
        }
        near.sort((p, q) -> Float.compare((float) Math.hypot(p.x - c.x, p.z - c.z), (float) Math.hypot(q.x - c.x, q.z - c.z)));
        for (int i = 0; i < near.size() && c.toAsk.size() < CANVASS_MAX; i++) if (!c.asked.contains(near.get(i).id)) c.toAsk.add(near.get(i).id);
        g.state.commit("CaseExamined", c.detective, c.victim, c.jurisdiction, 0, String.join(" ", c.findings) + " (" + c.toAsk.size() + " to ask)");
    }

    /**
     * One person asked what they know of it: what they saw of it with their own eyes or heard said (an account names
     * someone, or describes them), and whether they themselves fit what is known. Returns what they say.
     */
    public String ask(Case c, Npc w) {
        c.toAsk.remove(w.id);
        if (!c.asked.contains(w.id)) c.asked.add(w.id);
        String said = null;
        for (Memory m : w.memories) {
            if (m.victim != c.victim || !(m.kind.equals("MURDER") || m.kind.equals("ASSAULT"))) continue;
            float weight = m.confidence * (m.witnessed() ? 1f : 0.45f);
            if (m.subject != 0 && m.subject != w.id) {
                c.suspects.merge(m.subject, weight, Float::sum);
                said = m.witnessed() ? "I saw it. It was " + g.nameOf(m.subject) + "." : "People are saying it was " + g.nameOf(m.subject) + ".";
                c.note(w.name + (m.witnessed() ? " saw " : " has heard it was ") + g.nameOf(m.subject) + ".");
            }
            if (m.evidence != null) {
                if (c.profile == null) { c.profile = m.evidence.copy(); c.profile.victim = c.victim; }
                else for (var e : m.evidence.seen.entrySet()) if (!c.profile.seen.containsKey(e.getKey())) { c.profile.seen.put(e.getKey(), e.getValue()); c.profile.key.put(e.getKey(), m.evidence.key.get(e.getKey())); }
                if (said == null) said = "I didn't see a face. " + describe(m.evidence);
                c.note(w.name + " describes the killer: " + describe(m.evidence));
            }
        }
        if (said == null) said = w.asleep() ? "Wha-? No. I was asleep." : "I didn't see anything. I'm sorry.";
        g.state.commit("CaseWitnessAsked", c.detective, w.id, c.jurisdiction, 0, w.name + ": " + said);
        return said;
    }

    private static String describe(Evidence.Profile p) {
        List<String> bits = new ArrayList<>(p.seen.values());
        return bits.isEmpty() ? "Nothing to tell them by." : String.join(", ", bits) + ".";
    }

    /**
     * The judgement: everyone the testimony names, and anyone close by who fits the description and carries what could
     * have made the wounds, weighed. Sure enough, a name - and the bounty for a killing on it; else the case goes cold
     * (until a new account reopens it). Returns the line the officer says.
     */
    public String conclude(Case c) {
        Map<Long, Float> weigh = new HashMap<>(c.suspects);
        // the description against those about (the player among them): it must fit, and their arms must fit the wound
        if (c.profile != null && !c.profile.seen.isEmpty()) {
            List<Actor> about = new ArrayList<>();
            if (g.player.alive) about.add(g.player);
            for (Npc n : g.npcs) if (n.alive && n.loaded && n.id != c.detective && n.id != c.victim) about.add(n);
            for (Actor a : about) {
                if (Math.hypot(a.x - c.x, a.z - c.z) > 80) continue;
                float fit = Evidence.fit(c.profile, a);
                if (fit < 0.75f) continue;
                float w = fit * (armsFit(c, a) ? 0.7f : 0.25f) * (c.killerBled && a.hp < a.maxHp() * 0.85f ? 1.2f : 1f);
                weigh.merge(a.id, w, Float::sum);
            }
        }
        long best = 0; float bw = 0, second = 0;
        for (var e : weigh.entrySet()) {
            Actor a = g.actor(e.getKey());
            if (a == null || !a.alive) continue;
            float w = e.getValue() * (c.wound != null && armsFit(c, a) ? 1.15f : 1f);
            if (w > bw) { second = bw; bw = w; best = e.getKey(); } else if (w > second) second = w;
        }
        // two who fit as well as each other are no answer
        float certainty = Math.min(1, bw) * (second > bw * 0.8f ? 0.6f : 1f);
        if (best != 0 && certainty >= CHARGE_AT) {
            c.stage = Stage.CLOSED; c.suspect = best; c.certainty = certainty;
            int bounty = law.chargeFromCase(c);
            g.state.commit("CaseSolved", c.detective, best, c.jurisdiction, 0, String.format("the killing of %s: %s (%.0f%% sure), bounty %d", g.nameOf(c.victim), g.nameOf(best), certainty * 100, bounty));
            return "It was " + g.nameOf(best) + ". I'm sure of it. The watch will have them.";
        }
        c.stage = Stage.COLD; c.certainty = certainty;
        g.state.commit("CaseCold", c.detective, c.victim, c.jurisdiction, 0, "the killing of " + g.nameOf(c.victim) + ": nothing certain" + (best != 0 ? String.format(" (%s, %.0f%%)", g.nameOf(best), certainty * 100) : ""));
        return best == 0 ? "Whoever did this left nothing to go on. Not yet." : "I have my suspicions. Not enough to hang anyone on.";
    }

    /** Whether what someone carries could have made the wounds (a blade for a cut, a point or a bow for a stab, a club...). */
    static boolean armsFit(Case c, Actor a) {
        if (c.wound == null) return true;
        ItemDef main = a.main();
        if (main != null && main.damageType != null && main.damageType.name().equals(c.wound)) return true;
        for (var grid : a.grids()) for (ItemStack s : grid.stacks()) if (s.def.damageType != null && s.def.damageType.name().equals(c.wound) && s.def.family != ItemDef.Family.TOOL && s.def.family != ItemDef.Family.ARROW) return true;
        return c.wound.equals("BLUNT") && main == null;   // fists do beat
    }

    // ------------------------------------------------------------------ persistence
    public List<Object> save() {
        List<Object> l = new ArrayList<>();
        for (Case c : cases) {
            Map<String, Object> m = new HashMap<>();
            m.put("id", c.id); m.put("victim", c.victim); m.put("jur", c.jurisdiction); m.put("x", c.x); m.put("z", c.z); m.put("opened", c.openedMinutes);
            m.put("detective", c.detective); m.put("stage", c.stage.name()); m.put("findings", new ArrayList<>(c.findings));
            Map<String, Object> s = new HashMap<>(); for (var e : c.suspects.entrySet()) s.put(String.valueOf(e.getKey()), e.getValue()); m.put("suspects", s);
            m.put("toAsk", new ArrayList<Object>(c.toAsk)); m.put("asked", new ArrayList<Object>(c.asked));
            if (c.wound != null) m.put("wound", c.wound);
            m.put("bled", c.killerBled); m.put("suspect", c.suspect); m.put("certainty", c.certainty);
            if (c.profile != null) m.put("profile", c.profile.save());
            l.add(m);
        }
        return l;
    }

    @SuppressWarnings("unchecked")
    public void load(List<Object> l) {
        cases.clear(); serial = 0;
        if (l == null) return;
        for (Object o : l) {
            if (!(o instanceof Map<?, ?> raw)) continue;
            Map<String, Object> m = (Map<String, Object>) raw;
            Case c = new Case(astratto.util.Json.lng(m, "id", 0), astratto.util.Json.lng(m, "victim", 0), astratto.util.Json.str(m, "jur", ""),
                    (float) astratto.util.Json.num(m, "x", 0), (float) astratto.util.Json.num(m, "z", 0), astratto.util.Json.num(m, "opened", 0));
            c.detective = astratto.util.Json.lng(m, "detective", 0);
            try { c.stage = Stage.valueOf(astratto.util.Json.str(m, "stage", "OPEN")); } catch (IllegalArgumentException e) { c.stage = Stage.OPEN; }
            for (Object f : astratto.util.Json.list(m, "findings")) c.findings.add(f.toString());
            Map<String, Object> s = astratto.util.Json.obj(m, "suspects");
            if (s != null) for (var e : s.entrySet()) c.suspects.put(Long.parseLong(e.getKey()), ((Number) e.getValue()).floatValue());
            for (Object a : astratto.util.Json.list(m, "toAsk")) c.toAsk.add(((Number) a).longValue());
            for (Object a : astratto.util.Json.list(m, "asked")) c.asked.add(((Number) a).longValue());
            c.wound = (String) m.get("wound");
            c.killerBled = astratto.util.Json.bool(m, "bled", false);
            c.suspect = astratto.util.Json.lng(m, "suspect", 0);
            c.certainty = (float) astratto.util.Json.num(m, "certainty", 0);
            Map<String, Object> p = astratto.util.Json.obj(m, "profile");
            if (p != null) c.profile = Evidence.Profile.load(p);
            cases.add(c);
            serial = Math.max(serial, c.id);
        }
    }
}
