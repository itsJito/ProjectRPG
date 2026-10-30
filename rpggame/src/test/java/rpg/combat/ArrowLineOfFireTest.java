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
import rpg.item.Items;
import rpg.world.DevMap;

/**
 * Gamefixes: when an NPC shoots the player, the arrow phased through other NPCs to hit the player. Loosed down a line with
 * a friend of the archer's standing in it, the arrow stops in the friend; and the archer, seeing them there, looks for a
 * clear line rather than loose.
 */
class ArrowLineOfFireTest {
    @Test void aFriendInTheWayTakesTheArrowAndTheArcherLooksForAClearShot() {
        var cfg = new NativeEngine.Config(); cfg.deterministic = true;
        try (Game game = Game.create(cfg)) {
            game.activate(DevMap.build(game.engine()));
            Rpg g = game.add(new Rpg(game, "Arrows", new Appearance(), true));
            Npc archer = null, friend = null;
            for (Npc n : g.npcs) { if (archer == null) archer = n; else if (friend == null) friend = n; else g.ai.unload(n); }
            archer.team = friend.team = 7;
            archer.eq.set(ItemDef.Slot.MAIN, new ItemStack(Items.get("shortbow"), 1));
            archer.eq.set(ItemDef.Slot.AMMO, new ItemStack(Items.get("bronze_arrows"), 20));
            float[] lane = rpg.TestLanes.open(g);
            g.teleport(archer, lane[0], DevMap.FLOOR, lane[1]);
            g.teleport(friend, lane[0], DevMap.FLOOR, lane[1] - 5);
            g.teleport(g.player, lane[0], DevMap.FLOOR, lane[1] - 10);
            for (int i = 0; i < 5; i++) game.tick(1f / 60);
            assertSame(friend, g.combat.inLineOfFire(archer, g.player), "the archer sees the friend in the line");
            archer.sheathed = false; archer.sheatheAt = -99;
            float before = g.player.hp;
            long stray = g.state.all().stream().filter(e -> e.type().equals("EnvironmentalDamage")).count();
            assertTrue(g.combat.shoot(archer, 0, (float) Math.atan2(g.player.chestY() - archer.eyeY(), 10), 1));
            for (int i = 0; i < 60; i++) {
                g.teleport(archer, lane[0], DevMap.FLOOR, lane[1]); g.teleport(friend, lane[0], DevMap.FLOOR, lane[1] - 5);
                game.tick(1f / 60);
            }
            assertEquals(before, g.player.hp, 1e-4f, "the arrow did not pass through the friend to the player");
            assertTrue(g.state.all().stream().filter(e -> e.type().equals("EnvironmentalDamage")).count() > stray, "the friend took the stray arrow");
            assertNotEquals(archer.id, friend.fightTarget, "a stray starts no fight");
        }
    }
}
