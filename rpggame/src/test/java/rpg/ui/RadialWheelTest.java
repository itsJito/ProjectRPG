package rpg.ui;

import static org.junit.jupiter.api.Assertions.*;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import rpg.Rpg;
import rpg.SaveSystem;
import rpg.actor.Appearance;
import rpg.actor.Player;
import rpg.item.Items;
import rpg.world.DevMap;

/**
 * Gamefixes: the radial wheel. The pointer's angle picks one of eight slices (none in the dead centre); a slot clears
 * itself when its item is used up or gone; the layout is kept in the save.
 */
class RadialWheelTest {
    @Test void theAngleSnapsToEightSlicesClockwiseFromTheTop() {
        assertEquals(0, RadialWheel.slice(0, -100, 30), "straight up");
        assertEquals(2, RadialWheel.slice(100, 0, 30), "right");
        assertEquals(4, RadialWheel.slice(0, 100, 30), "down");
        assertEquals(6, RadialWheel.slice(-100, 0, 30), "left");
        assertEquals(1, RadialWheel.slice(70, -70, 30), "up and right");
        assertEquals(7, RadialWheel.slice(-70, -70, 30), "up and left");
        assertEquals(0, RadialWheel.slice(-20, -100, 30), "a little left of up is still the top slice");
        assertEquals(-1, RadialWheel.slice(10, 10, 30), "the dead centre picks nothing");
    }

    @Test void slotsFollowWhatIsCarriedAndAreSaved(@TempDir Path dir) {
        var cfg = new NativeEngine.Config(); cfg.deterministic = true;
        try (Game game = Game.create(cfg)) {
            game.activate(DevMap.build(game.engine()));
            Rpg g = game.add(new Rpg(game, "Wheel", new Appearance(), true));
            Player p = g.player;
            p.quick[0] = new Player.QuickSlot(Items.BREAD, 0);
            p.quick[3] = new Player.QuickSlot(Items.SHRIMP, 0);
            p.refreshQuick();
            assertEquals(1, p.quick[0].count(), "the bread's count is read from the pack");
            var saves = new SaveSystem(g);
            Path file = dir.resolve("wheel.json");
            assertTrue(saves.save(file));
            p.quick[0] = null; p.quick[3] = null;
            assertTrue(saves.load(file));
            assertNotNull(p.quick[0], "slot 1 came back from the save"); assertEquals(Items.BREAD, p.quick[0].item());
            assertNotNull(p.quick[3], "slot 4 came back from the save"); assertEquals(Items.SHRIMP, p.quick[3].item());
            // eaten: the slot empties itself
            p.takeItems(Items.BREAD, p.count(Items.BREAD));
            p.refreshQuick();
            assertNull(p.quick[0], "a slot whose item is gone clears");
            assertNotNull(p.quick[3]);
        }
    }
    @Test void equippedItemsStayInTheirQuickSlotAcrossSaveAndLoad(@TempDir Path dir) {
        var cfg=new NativeEngine.Config();cfg.deterministic=true;
        try(Game game=Game.create(cfg)) {
            game.activate(DevMap.build(game.engine()));var g=game.add(new Rpg(game,"Wheel equip",new Appearance(),true));
            var item=Items.get("wooden_shield");g.give(g.player,item,1);
            int expected=g.player.count(item)+(g.player.eq.def(rpg.item.ItemDef.Slot.OFF)==item?1:0);
            g.player.quick[2]=new Player.QuickSlot(item,1);
            assertTrue(g.items.equipFirst(g.player,item,rpg.item.ItemDef.Slot.OFF,g.state.newRequestId()).committed());
            g.player.refreshQuick();assertNotNull(g.player.quick[2]);assertEquals(expected,g.player.quick[2].count());
            var saves=new SaveSystem(g);var file=dir.resolve("equipped.json");assertTrue(saves.save(file));assertTrue(saves.load(file));
            assertNotNull(g.player.quick[2]);assertEquals(item,g.player.quick[2].item());
            assertTrue(g.items.dropEquipped(g.player,rpg.item.ItemDef.Slot.OFF).committed());
            for (var grid:g.player.grids()) { int slot=grid.first(item);if(slot>=0)g.items.perform(g.player,grid,slot,"drop"); }
            g.player.refreshQuick();assertNull(g.player.quick[2]);
        }
    }
}
