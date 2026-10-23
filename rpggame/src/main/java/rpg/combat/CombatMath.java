package rpg.combat;


/** Physical contact decides hits; a bounded uniform integer roll decides HP damage. Active defenses act afterward. */
public final class CombatMath {
    private CombatMath() {}

    /** Converts legacy authored item power into bonus points; spell base damage already uses HP units. */
    public static final float HP_SCALE = 0.1f;

    /**
     * Attack (GDD 2.3: "how often can I hit?"): it never adds damage; it quickens the chain - the share of a move's
     * wind-up and recovery a trained hand saves (to a quarter at 100), with the weapon's Attack bonus on top.
     */
    public static float attackCadence(int attack, float attackBonus) { return 1f - Math.min(0.32f, (attack - 1) * 0.0025f + attackBonus * 0.004f); }
    /** Effective level includes the trained stat, a Faith multiplier and the ordinary +8 handling baseline. */
    public static int effectiveLevel(int level, float prayer) { return Math.max(0, (int) Math.floor(level * prayer)) + 8; }

    public static int maxHit(int effectiveLevel, float strengthBonus) {
        return Math.max(0, (int) Math.floor(0.5 + Math.max(0, effectiveLevel) * (Math.max(-64, strengthBonus) + 64) / 640.0));
    }

    /** Uniform 0..max inclusive. Player zero rolls become one only if max is positive. */
    public static int roll(int max, boolean player, java.util.random.RandomGenerator random) {
        if (max <= 0) return 0;
        int result = random.nextInt(max + 1);
        return player ? Math.max(1, result) : result;
    }

    public static float strengthForce(int strength) { return 1f + (strength - 1) * 0.015f; }

    /** Defense and armor combine into the active guard rating. Unblocked absorption is handled separately by armoredMax. */
    public static float protection(int defense, float armor) { return defense * 1.2f + armor; }

    /** Armor reduces the maximum before rolling. Each 24 trained armor points absorbs one maximum HP. */
    public static int armoredMax(int max, int defense, float armor) {
        int absorbed = (int) Math.floor(Math.max(0, armor) * (1 + Math.max(0, defense) / 100f) / 24f);
        return Math.max(0, max - absorbed);
    }

    /** How much of a blow a raised guard stops: the shield or blade's own guard, made better by Defense and the armour behind it. */
    public static float guardShare(float itemGuard, int defense, float armor) { return Math.min(0.97f, itemGuard * (0.85f + defense * 0.0018f + armor * 0.002f)); }
    /** The parry window (seconds): wider for a better defender. */
    public static float parryWindow(float base, int defense) { return base * (1f + Math.min(0.6f, defense * 0.006f)); }

    public static int magicMax(float spellBase, float gearBonusPct) { return Math.max(0, (int) Math.floor(spellBase * (1 + gearBonusPct / 100f))); }

    public static float impact(float weaponImpact, int strength, float moveImpact) { return weaponImpact * strengthForce(strength) * moveImpact; }

    /** Effective Stability: a base, the actor's skills and armor, and the resistance built up by recent staggers. */
    public static float stability(int defense, int strength, float armor, float resistanceBonus) {
        return 20f + defense * 0.6f + strength * 0.3f + armor * 0.4f + resistanceBonus;
    }

    /** How much Stability resistance one stagger adds (it decays over seconds - no permanent stagger loops). */
    public static float staggerResistance(float stability) { return 0.6f * stability; }

    public static float moveDamage(rpg.actor.Actor.Combat.Move m) {
        return switch (m) { case LIGHT_R, LIGHT_L -> 1f; case HEAVY -> 1.6f; case THRUST -> 1.2f; case PUNCH -> 1f; case BASH -> 0.4f; case BITE -> 1f; };
    }

    public static float moveImpact(rpg.actor.Actor.Combat.Move m) {
        return switch (m) { case LIGHT_R, LIGHT_L -> 1f; case HEAVY -> 1.9f; case THRUST -> 0.8f; case PUNCH -> 0.7f; case BASH -> 2.2f; case BITE -> 1f; };
    }

}
