package rpg.skill;

import java.util.ArrayList;
import java.util.List;

/**
 * One actor's eighteen skills: cumulative XP each, every level read from {@link XpTable}. XP only ever moves through
 * {@link #award}, which applies a whole award atomically and reports the levels gained, so the UI, the level-up
 * celebration and the event log all see the same committed result.
 */
public final class Skills {
    private final long[] xp = new long[Skill.COUNT];

    /** What one award did. */
    public record Award(Skill skill, long requested, long applied, int fromLevel, int toLevel) {
        public boolean levelledUp() { return toLevel > fromLevel; }
    }

    public long xp(Skill s) { return xp[s.ordinal()]; }
    public int level(Skill s) { return XpTable.levelFor(xp[s.ordinal()]); }

    /** Adds XP (capped at the level-100 total; the cap is the GDD's post-100 rule left open, here XP stops at 6M). */
    public Award award(Skill s, long amount) {
        if (amount < 0) throw new IllegalArgumentException("negative XP");
        int i = s.ordinal();
        int before = XpTable.levelFor(xp[i]);
        long next = Math.min(XpTable.MAX_XP, xp[i] + amount);
        long applied = next - xp[i];
        xp[i] = next;
        return new Award(s, amount, applied, before, XpTable.levelFor(next));
    }

    /** Debug: sets a skill to exactly the start of a level. */
    public void setLevel(Skill s, int level) { xp[s.ordinal()] = XpTable.xpFor(Math.max(1, Math.min(XpTable.MAX_LEVEL, level))); }
    public void setXp(Skill s, long v) { xp[s.ordinal()] = Math.max(0, Math.min(XpTable.MAX_XP, v)); }

    public int totalLevel() { int t = 0; for (Skill s : Skill.ALL) t += level(s); return t; }
    public long totalXp() { long t = 0; for (long v : xp) t += v; return t; }

    /**
     * Combat level (GDD 2.3, established), exactly:
     * <pre>
     * Base   = (Defense + Health + floor(Faith / 2)) / 4
     * Melee  = (13 / 40) * (Attack + Strength)
     * Ranged = (13 / 40) * floor(Archery * 3 / 2)
     * Magic  = (13 / 40) * floor(Magic * 3 / 2)
     * CombatLevel = floor(Base + max(Melee, Ranged, Magic))
     * </pre>
     * evaluated in whole numbers (everything times 40) so no floating-point rounding can land on the wrong side of an
     * integer: a new character (Health 10, the rest 1) is level 3; every combat skill at 100 is 127.
     */
    public int combatLevel() { return combatLevel(level(Skill.ATTACK), level(Skill.STRENGTH), level(Skill.DEFENSE), level(Skill.HEALTH), level(Skill.ARCHERY), level(Skill.MAGIC), level(Skill.FAITH)); }

    public static int combatLevel(int attack, int strength, int defense, int health, int archery, int magic, int faith) {
        long base40 = 10L * (defense + health + faith / 2);                  // Base x 40
        long melee40 = 13L * (attack + strength), ranged40 = 13L * (archery * 3 / 2), magic40 = 13L * (magic * 3 / 2);
        return (int) ((base40 + Math.max(melee40, Math.max(ranged40, magic40))) / 40);
    }

    public long[] raw() { return xp.clone(); }
    public void load(long[] values) { for (int i = 0; i < Skill.COUNT && i < values.length; i++) setXp(Skill.ALL[i], values[i]); }

    public List<String> describe() {
        List<String> out = new ArrayList<>();
        for (Skill s : Skill.ALL) out.add(s.title + " " + level(s) + " (" + xp(s) + " xp)");
        return out;
    }
}
