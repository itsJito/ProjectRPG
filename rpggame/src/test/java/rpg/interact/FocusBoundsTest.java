package rpg.interact;

import org.junit.jupiter.api.Test;
import rpg.objects.Furniture;
import rpg.world.DevMap;
import static org.junit.jupiter.api.Assertions.*;

class FocusBoundsTest {
    @Test void aLongCounterDoesNotOccupyTheNpcHeadspaceOrTheStreetBesideIt() {
        var counter = new Furniture(1, Furniture.Kind.COUNTER, 112, 57, 116, 57, 1, 2, 0, true);
        assertEquals(Float.POSITIVE_INFINITY, counter.focusEntry(113.5f, DevMap.FLOOR + 1.6f, 60, 0, 0, -1));
        assertEquals(Float.POSITIVE_INFINITY, counter.focusEntry(117.5f, DevMap.FLOOR + 0.6f, 60, 0, 0, -1));
        assertEquals(2, counter.focusEntry(113.5f, DevMap.FLOOR + 0.6f, 60, 0, 0, -1), 0.001f);
    }
}
