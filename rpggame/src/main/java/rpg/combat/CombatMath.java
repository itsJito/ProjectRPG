package rpg.combat;

import rpg.item.ItemDef.DamageType;

/**
 * The combat formulas (GDD 2.3), as pure functions. The relationships are the GDD's; every coefficient is tuning data.
 * There is no accuracy roll and no damage roll anywhere: a hit is a physical contact, and its outcome follows from the
 * stats, the move and how it was executed.
 *
 * <pre>
 * potential melee damage = weapon base x Attack scaling x move modifier x execution
 * protection             = one combined Defense-and-armor rating, with diminishing returns
 * final HP damage        = potential x (1 - mitigation)
 * impact                 = weapon impact x Strength force x move impact
 * stagger when impact exceeds effective Stability; then Stability resistance rises and recovers
 * </pre>
 */
public final class CombatMath {
    private CombatMath() {}

    /**
     * Damage is on the HP scale (gamefixes: HP is the Health level, 10 at Health 10, 100 at 100): every base damage - a
     * weapon's, an arrow's, a spell's, an animal's bite - is its tier power times this, so a fresh character's wooden
     * sword hits for about 1 and a master's godly blade for twenty and more.
     */
    public static final float HP_SCALE = 0.1f;

    /**
     * Attack (GDD 2.3: "how often can I hit?"): it never adds damage; it quickens the chain - the share of a move's
     * wind-up and recovery a trained hand saves (to a quarter at 100), with the weapon's Attack bonus on top.
     */
    public static float attackCadence(int attack, float attackBonus) { return 1f - Math.min(0.32f, (attack - 1) * 0.0025f + attackBonus * 0.004f); }
    /** Strength ("how hard can I hit?"): the damage of a landed blow, with the weapon's Strength bonus (a flat item bonus, scaled). */
    public static float strengthScaling(int strength, float strengthBonus) { return (1f + (strength - 1) * 0.022f) * (1f + strengthBonus * 0.025f); }
    /** Kept for creatures' natural weapons (their Attack and Strength are one bite): Strength's scaling, no bonus. */
    public static float attackScaling(int strength) { return strengthScaling(strength, 0); }
    public static float archeryScaling(int archery) { return 1f + (archery - 1) * 0.02f; }
    public static float magicScaling(int magic) { return 1f + (magic - 1) * 0.022f; }
    public static float strengthForce(int strength) { return 1f + (strength - 1) * 0.015f; }

    /** Defense and armor combine into one rating: it drives active defence (how much a guard stops, how wide the parry window is), never a passive cut of a clean hit. */
    public static float protection(int defense, float armor) { return defense * 1.2f + armor; }

    /**
     * GDD 2.3 (established): neither Defense nor armour reduces the HP damage of a successful, unblocked strike - there is
     * no passive mitigation. (Spells are the one thing armour lessens, a little: it is cloth and metal between the bolt and
     * the skin, not a dice roll.)
     */
    public static float mitigation(float protection, DamageType type) {
        if (type != DamageType.MAGIC) return 0;
        return Math.min(0.3f, protection / (protection + 300f));
    }

    /** How much of a blow a raised guard stops: the shield or blade's own guard, made better by Defense and the armour behind it. */
    public static float guardShare(float itemGuard, int defense, float armor) { return Math.min(0.97f, itemGuard * (0.85f + defense * 0.0018f + armor * 0.002f)); }
    /** The parry window (seconds): wider for a better defender. */
    public static float parryWindow(float base, int defense) { return base * (1f + Math.min(0.6f, defense * 0.006f)); }

    public static float finalDamage(float potential, float mitigation) { return Math.max(0, potential * (1 - mitigation)); }

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

    /** Execution: a staggered target or a blow from behind is worth more (deterministic, from positions and state). */
    public static float execution(boolean targetStaggered, boolean fromBehind) {
        float e = 1f;
        if (targetStaggered) e *= 1.25f;
        if (fromBehind) e *= 1.3f;
        return e;
    }
}
