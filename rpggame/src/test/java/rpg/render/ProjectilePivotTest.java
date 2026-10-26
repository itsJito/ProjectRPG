package rpg.render;

import org.junit.jupiter.api.Test;
import rpg.item.Items;
import static org.junit.jupiter.api.Assertions.*;

class ProjectilePivotTest {
    @Test void aJavelinsMeshTipIsMeasuredFromItsGripInsteadOfFromItsTail() {
        var javelin=Items.all().stream().filter(item->"javelin".equals(item.thrown)).findFirst().orElseThrow();
        float offset=Gfx.projectileTipFromGrip(javelin);
        assertTrue(offset>0.85f && offset<0.98f,"javelin grip to tip is about 0.91 m; using its full 1.8 m flight length leaves a visible gap");
        assertEquals(0.85f,Gfx.projectileTipFromGrip(Items.BRONZE_ARROWS),0.001);
    }
}
