package rpg.combat;

import java.util.EnumMap;
import java.util.Map;
import rpg.Chat;
import rpg.Rpg;
import rpg.actor.Actor;
import rpg.combat.Spells.Category;
import rpg.combat.Spells.Prayer;
import rpg.item.ItemDef;
import rpg.item.ItemStack;
import rpg.skill.Skill;

/**
 * Faith's prayers at work (GDD 2.5): toggled on and off, each charging its own Faith point per drain interval - the
 * first point paid as it is raised, the next at each full interval after; ending one early refunds nothing, and raising
 * it again pays again (no toggling for free). The interval is six seconds, lengthened 3.33% for each point of equipped
 * Faith bonus. When a point falls due and there is none, the prayer goes out, with word of it. At most one overhead
 * protection, and one Strength prayer, at a time: raising another of the kind lowers the first.
 */
public final class Prayers {
    private final Rpg g;
    /** Each actor's raised prayers and how far through its current paid interval each is (seconds). */
    private final Map<Long, EnumMap<Prayer, Float>> active = new java.util.HashMap<>();
    public long pointsPaid, toggles;

    public Prayers(Rpg g) { this.g = g; }

    public boolean on(Actor a, Prayer p) { var m = active.get(a.id); return m != null && m.containsKey(p); }
    public java.util.Set<Prayer> of(Actor a) { var m = active.get(a.id); return m == null ? java.util.Set.of() : m.keySet(); }

    /** The equipped Faith bonus (holy symbols, the Faith cape). */
    public static int faithBonus(Actor a) {
        int b = 0;
        for (ItemStack s : a.eq.stacks()) b += s.def.faithBonus;
        return b;
    }

    /** How long one point of Faith lasts an active prayer (GDD 2.5: 6 x (1 + 0.0333 x bonus)). */
    public static float interval(Actor a) { return 6f * (1 + 0.0333f * Math.max(0, faithBonus(a))); }

    public String refusal(Actor a, Prayer p) {
        if (a.skills.level(Skill.FAITH) < p.level) return "You need Faith level " + p.level + " to pray " + p.title + ".";
        if (a.faith < 1) return "You have no Faith points left. Pray at an altar, or drink something holy.";
        return null;
    }

    /** Raises a prayer (paying its first point) or lowers it. True when the prayer is now on. */
    public boolean toggle(Actor a, Prayer p) {
        var m = active.computeIfAbsent(a.id, k -> new EnumMap<>(Prayer.class));
        toggles++;
        if (m.containsKey(p)) {
            m.remove(p);
            g.state.commit("PrayerEnded", a.id, 0, null, 0, p.title);
            if (a == g.player && g.sfx != null) g.sfx.ui("SpellBookCloseA", 0.4f);
            return false;
        }
        String no = refusal(a, p);
        if (no != null) { if (a == g.player) g.chat.add(no, Chat.Kind.WARN); return false; }
        // one of a kind: another overhead (or Strength prayer) gives way
        if (p.category == Category.OVERHEAD || p.category == Category.STRENGTH) m.keySet().removeIf(o -> o.category == p.category);
        a.faith -= 1;
        pointsPaid++;
        m.put(p, 0f);
        g.state.commit("PrayerRaised", a.id, 0, null, 0, p.title + String.format(" (interval %.1f s)", interval(a)));
        if (g.sfx != null) g.sfx.play("bless", a.x, a.y + 1, a.z, 0.5f);
        if (g.fx != null) g.fx.ring(a.x, a.y, a.z, 1.2f, 3f, 2.6f, 1.2f);
        if (a == g.player) g.chat.say("You pray " + p.title + ". " + p.effect);
        return true;
    }

    /** Ends every prayer of an actor (death, arrest): nothing refunded. */
    public void clear(Actor a) { active.remove(a.id); }

    /** Each tick: the time on each prayer's paid interval; a point due is paid, or the prayer fails. */
    public void update(float dt) {
        for (var e : active.entrySet()) {
            Actor a = g.actor(e.getKey());
            var m = e.getValue();
            if (a == null || !a.alive) { m.clear(); continue; }
            float iv = interval(a);
            for (var it = m.entrySet().iterator(); it.hasNext(); ) {
                var pe = it.next();
                float t = pe.getValue() + dt;
                if (t >= iv) {
                    if (a.faith < 1) {
                        it.remove();
                        g.state.commit("PrayerFailed", a.id, 0, null, 0, pe.getKey().title + ": out of Faith");
                        if (a == g.player) g.chat.add("You have run out of Faith points: " + pe.getKey().title + " ends.", Chat.Kind.WARN);
                        continue;
                    }
                    a.faith -= 1;
                    pointsPaid++;
                    t -= iv;
                    if (pe.getKey() == Prayer.MEND) a.hp = Math.min(a.maxHp(), a.hp + 1);
                }
                pe.setValue(t);
            }
        }
    }

    // ------------------------------------------------------------------ what they do (read by combat)
    /** The overhead protection against a kind of attack: true when it negates it entirely. */
    public boolean protects(Actor v, boolean melee, boolean magic) {
        if (magic) return on(v, Prayer.PROTECT_FROM_MAGIC);
        if (melee) return on(v, Prayer.PROTECT_FROM_MELEE);
        return on(v, Prayer.PROTECT_FROM_MISSILES);
    }

    /** The multiplier on an attacker's landed melee damage. */
    public float strength(Actor a) {
        float k = 1;
        if (on(a, Prayer.BURST_OF_STRENGTH)) k *= 1.12f;
        if (on(a, Prayer.PIETY)) k *= 1.2f;
        if (on(a, Prayer.SMITE)) k *= 1.1f;
        return k;
    }
    /** The multiplier on an archer's damage. */
    public float archery(Actor a) { return on(a, Prayer.RIGOUR) ? 1.2f : 1f; }
    /** The multiplier on wind-up and recovery (Attack's tempo). */
    public float tempo(Actor a) { return on(a, Prayer.PIETY) ? 0.85f : 1f; }
    /** The multiplier on a raised guard's share (Defense's active defence). */
    public float guard(Actor a) { return on(a, Prayer.PIETY) ? 1.2f : 1f; }
    /** The multiplier on incoming damage from the Ward. */
    public float ward(Actor v) { return on(v, Prayer.WARD) ? 0.85f : 1f; }

    // ------------------------------------------------------------------ save
    public Map<String, Object> save(Actor a) {
        Map<String, Object> m = new java.util.HashMap<>();
        for (var e : active.getOrDefault(a.id, new EnumMap<>(Prayer.class)).entrySet()) m.put(e.getKey().name(), (double) e.getValue());
        return m;
    }
    public void load(Actor a, Map<String, Object> m) {
        active.remove(a.id);
        if (m == null) return;
        var em = new EnumMap<Prayer, Float>(Prayer.class);
        for (var e : m.entrySet()) try { em.put(Prayer.valueOf(e.getKey()), ((Number) e.getValue()).floatValue()); } catch (IllegalArgumentException ignored) { }
        if (!em.isEmpty()) active.put(a.id, em);
    }

    /** A protected hit's Faith XP: a legitimate threat prevented (never for the prayer merely running). */
    public void prevented(Actor v, float damage, long cause) {
        if (damage > 0) g.prog.award(v, Skill.FAITH, damage * 2.5f, "protected by prayer", cause);
    }

}
