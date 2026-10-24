package rpg.combat;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DamageRollTest {
    @Test void maxHitUsesTheSpecifiedIntegerFormulaAndFaithBoost() {
        assertEquals(20, CombatMath.maxHit(100, 64));
        assertEquals(9, CombatMath.effectiveLevel(1, 1));
        assertEquals(128, CombatMath.effectiveLevel(100, 1.2f));
        assertTrue(CombatMath.maxHit(128, 64) > CombatMath.maxHit(108, 64));
    }
    @Test void magicUsesSpellBaseAndGearWithoutAnExtraLevelMultiplier() {
        assertEquals(24, CombatMath.magicMax(24, 0)); assertEquals(30, CombatMath.magicMax(24, 25));
    }
    @Test void everyNpcIntegerIncludingZeroAndMaximumIsEquallyReachable() {
        int[] counts = new int[21]; var random = new java.util.Random(471);
        for (int i = 0; i < 210000; i++) counts[CombatMath.roll(20, false, random)]++;
        for (int count : counts) assertTrue(count > 9500 && count < 10500, "uniform count " + count);
    }
    @Test void playerMinimumCannotManufactureDamageWhenArmorMakesTheMaximumZero() {
        int weak = CombatMath.maxHit(CombatMath.effectiveLevel(1, 1), 0);
        int stopped = CombatMath.armoredMax(weak, 100, 110);
        var random = new java.util.Random(41);
        assertEquals(0, stopped);
        for (int i = 0; i < 100; i++) { assertEquals(0, CombatMath.roll(stopped, true, random)); assertTrue(CombatMath.roll(20, true, random) >= 1); }
    }
}
