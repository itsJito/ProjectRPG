package rpg.skill;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StandardXpTest {
    @Test void standardMilestonesAndEveryThresholdHaveExactBoundaries() {
        assertEquals(83, XpTable.xpFor(2)); assertEquals(1_154, XpTable.xpFor(10));
        assertEquals(101_333, XpTable.xpFor(50)); assertEquals(13_034_431, XpTable.xpFor(99));
        assertEquals(14_391_160, XpTable.xpFor(100));
        for(int level=2;level<=100;level++) {
            assertEquals(level-1,XpTable.levelFor(XpTable.xpFor(level)-1));
            assertEquals(level,XpTable.levelFor(XpTable.xpFor(level)));
        }
    }
    @Test void oldRawExperienceIsRetainedWithoutRescalingAnySkill() {
        long[] raw=new long[Skill.COUNT]; java.util.Arrays.fill(raw,935_503);
        Skills skills=new Skills(); skills.load(raw); assertArrayEquals(raw,skills.raw());
        for(Skill skill:Skill.ALL) assertEquals(72,skills.level(skill));
    }
}
