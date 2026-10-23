package rpg.actor;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import org.junit.jupiter.api.*;
import rpg.*;
import rpg.ai.Npc;
import rpg.item.ItemDef;
import rpg.world.*;
import static org.junit.jupiter.api.Assertions.*;

class MovementTest {
    private Game game;
    private Rpg g;
    @BeforeEach void open() {
        var cfg = new NativeEngine.Config(); cfg.deterministic = true;
        game = Game.create(cfg); game.activate(DevMap.build(game.engine()));
        g = game.add(new Rpg(game, "Movement", new Appearance(), true));
        for (Npc n : g.npcs) g.ai.unload(n);
        g.teleport(g.player, 60.5f, DevMap.FLOOR, 54.5f); game.tick(1f / 60);
    }
    @AfterEach void close() { game.close(); }
    @Test void walkingStopsInUnderAQuarterSecondAndUnderHalfAMetre() {
        for (int frame = 0; frame < 90; frame++) { g.controller.in.forward = 1; game.tick(1f / 60); }
        float x = g.player.x, z = g.player.z;
        g.controller.in.forward = 0;
        for (int frame = 0; frame < 15; frame++) game.tick(1f / 60);
        assertTrue(Math.hypot(g.player.vx, g.player.vz) < 0.05);
        assertTrue(Math.hypot(g.player.x - x, g.player.z - z) < 0.5);
    }
    @Test void crouchRetainsItsShorterColliderUntilThereIsStandingRoom() {
        g.game.commands().release(g.player.body);
        g.player.body = g.game.reserve(astratto.bridge.AstrattoNative.KIND_MOVER);
        // A taller probe body makes the low-clearance invariant independent of voxel rounding.
        g.game.commands().moverCreate(g.player.body, 60.5f, DevMap.FLOOR, 54.5f, g.player.radius, 2.4f, 0.6f, 0, 2.4f * Body.CROUCH_HEIGHT);
        g.setBlock(60, DevMap.FLOOR + 2, 54, Blocks.STONE);
        g.controller.in.sneak = true; game.tick(1f / 60);
        assertTrue(g.lastBodies.mover(g.player.body).crouched);
        float x = g.player.x, z = g.player.z;
        g.controller.in.sneak = false;
        for (int frame = 0; frame < 10; frame++) game.tick(1f / 60);
        assertTrue(g.player.crouched);
        assertEquals(x, g.player.x, 0.01); assertEquals(z, g.player.z, 0.01);
        g.setBlock(60, DevMap.FLOOR + 2, 54, Blocks.AIR);
        game.tick(1f / 60); assertFalse(g.player.crouched);
    }
    @Test void emptyHandsCanGuardMeleeButCannotParryAMetalWeaponOrStopArrows() {
        g.player.eq.set(ItemDef.Slot.MAIN, null); g.player.eq.set(ItemDef.Slot.OFF, null);
        g.controller.in.offDown = true; game.tick(1f / 60);
        assertTrue(g.player.cs.blocking);
        Actor attacker = g.actor(Ids.BANDIT_1);
        attacker.x = g.player.x; attacker.z = g.player.z - 1; g.player.yaw = 0;
        g.player.hp = g.player.maxHp(); float before = g.player.hp;
        g.combat.resolve(attacker, g.player, 4, 0, ItemDef.DamageType.BLUNT, null, g.player.x, g.player.chestY(), g.player.z, "bare guard", false);
        assertEquals(0, g.combat.parried); assertTrue(g.combat.blocked > 0);
        assertTrue(g.player.hp < before && g.player.hp > before - 4);
        long blocked = g.combat.blocked;
        var arrow = new rpg.combat.CombatSystem.Projectile(); arrow.owner = attacker;
        g.combat.resolve(attacker, g.player, 1, 0, ItemDef.DamageType.PIERCE, arrow, g.player.x, g.player.chestY(), g.player.z, "arrow", false);
        assertEquals(blocked, g.combat.blocked);
    }
}
