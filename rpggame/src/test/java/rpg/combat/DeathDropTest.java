package rpg.combat;

import static org.junit.jupiter.api.Assertions.*;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import org.junit.jupiter.api.Test;
import rpg.Rpg;
import rpg.actor.Appearance;
import rpg.ai.Npc;
import rpg.item.ItemDef;
import rpg.item.ItemStack;
import rpg.objects.GroundItem;
import rpg.world.DevMap;

/** Gamefixes: the dead drop the weapon and shield they held, which fall to the ground beside them. */
class DeathDropTest {
    @Test void theDeadLetGoOfWhatTheyHeld() {
        var cfg = new NativeEngine.Config(); cfg.deterministic = true;
        try (Game game = Game.create(cfg)) {
            game.activate(DevMap.build(game.engine()));
            Rpg g = game.add(new Rpg(game, "Drops", new Appearance(), true));
            Npc guard = (Npc) g.actor(rpg.Ids.GUARD);
            for (Npc n : g.npcs) if (n != guard) g.ai.unload(n);
            float[] lane = rpg.TestLanes.open(g);
            g.teleport(guard, lane[0], DevMap.FLOOR, lane[1] - 4);
            if (guard.main() == null) guard.eq.set(ItemDef.Slot.MAIN, new ItemStack(rpg.item.Items.get("iron_sword"), 1));
            if (guard.off() == null) guard.eq.set(ItemDef.Slot.OFF, new ItemStack(rpg.item.Items.get("wooden_shield"), 1));
            ItemDef weapon = guard.main(), shield = guard.off();
            for (int i = 0; i < 10; i++) game.tick(1f / 60);
            g.combat.resolve(g.player, guard, 9999, 10, ItemDef.DamageType.SLASH, null, guard.x, guard.chestY(), guard.z, "test", false);
            for (int i = 0; i < 60 * 6; i++) game.tick(1f / 60);
            assertFalse(guard.alive);
            assertNull(guard.main(), "the weapon left the dead hand");
            GroundItem sword = null, board = null;
            for (var o : g.objects.all()) if (o instanceof GroundItem gi && !gi.removed && Math.hypot(gi.x - guard.x, gi.z - guard.z) < 3) {
                if (gi.stack.def == weapon) sword = gi;
                if (gi.stack.def == shield) board = gi;
            }
            assertNotNull(sword, "the " + weapon.name + " lies by the body");
            assertNotNull(board, "the " + shield.name + " lies by the body");
            assertTrue(sword.y < DevMap.FLOOR + 0.4f && sword.y > DevMap.FLOOR - 0.2f, "it fell to the ground: y " + sword.y);
        }
    }
}
