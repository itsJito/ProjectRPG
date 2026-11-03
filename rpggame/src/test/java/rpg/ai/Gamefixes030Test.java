package rpg.ai;

import static org.junit.jupiter.api.Assertions.*;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import java.util.ArrayList;
import org.junit.jupiter.api.*;
import rpg.Rpg;
import rpg.actor.Appearance;
import rpg.animal.Animal;
import rpg.animal.Species;
import rpg.item.ItemDef;
import rpg.item.ItemStack;
import rpg.item.Items;
import rpg.monster.Monster;
import rpg.mount.Riding;
import rpg.world.Blocks;
import rpg.world.DevMap;

/** Alpha 0.3.0 gamefixes: ladders, the seamless hang, the torch, riding and taming, the undertaker, wounds easing. */
class Gamefixes030Test {
    private Game game;
    private Rpg g;
    @BeforeEach void open() {
        var cfg = new NativeEngine.Config(); cfg.deterministic = true; game = Game.create(cfg); game.activate(DevMap.build(game.engine()));
        g = game.add(new Rpg(game, "Gamefixes 0.3.0", Appearance.henric(), true));
        for (Monster m : new ArrayList<>(g.monsters.all())) g.ai.unload(m);
        game.tick(0);
    }
    @AfterEach void close() { game.close(); }

    private void yard() {
        for (int x = 56; x <= 73; x++) for (int z = 51; z <= 70; z++) for (int y = DevMap.FLOOR; y < DevMap.FLOOR + 8; y++) g.setBlock(x, y, z, Blocks.AIR);
        for (Npc o : g.npcs) if (o.distTo(64.5f, 60f) < 24) g.ai.unload(o);
    }
    private void keys(float fwd, float strafe, boolean sprint, int frames) {
        for (int i = 0; i < frames; i++) { var in = g.controller.in; in.forward = fwd; in.strafe = strafe; in.sprint = sprint; game.tick(1f / 60); }
        g.controller.in.forward = 0; g.controller.in.strafe = 0; g.controller.in.sprint = false;
    }

    // ------------------------------------------------------------------ ladders
    private void ladderWall() {
        yard();
        for (int x = 60; x <= 68; x++) for (int z = 57; z <= 59; z++) for (int y = 0; y < 5; y++) g.setBlock(x, DevMap.FLOOR + y, z, Blocks.BRICK);
        for (int y = 0; y < 5; y++) g.setBlock(64, DevMap.FLOOR + y, 60, Blocks.LADDER);
        game.tick(0);
    }

    @Test void aLadderIsClimbedFacingItAndClimbedOutAtTheTop() {
        ladderWall();
        g.teleport(g.player, 64.5f, DevMap.FLOOR, 62.4f); g.controller.snapTo(0); game.tick(0);
        float top = 0;
        for (int i = 0; i < 60 * 5 && !g.state.all().stream().anyMatch(e -> e.type().equals("LadderLeft")); i++) { g.controller.in.forward = 1; game.tick(1f / 60); top = Math.max(top, g.player.y - DevMap.FLOOR); }
        g.controller.in.forward = 0; game.tick(1f / 60);
        assertNull(g.player.climb, "off the ladder at the top");
        assertTrue(g.player.y > DevMap.FLOOR + 4.8f, "stood on top of the wall: y " + (g.player.y - DevMap.FLOOR) + " (highest " + top + ")");
        assertTrue(g.state.all().stream().anyMatch(e -> e.type().equals("LadderMounted")));
    }

    @Test void walkingPastALadderFromTheSideTakesNoHold() {
        ladderWall();
        g.teleport(g.player, 62.5f, DevMap.FLOOR, 60.5f); g.controller.snapTo((float) -Math.PI / 2); game.tick(0);
        keys(1, 0, false, 60);
        assertNull(g.player.climb);
        assertTrue(g.player.y < DevMap.FLOOR + 0.3f, "still on the ground");
    }

    @Test void aLadderIsTakenFromTheTopAndClimbedDownToTheGround() {
        ladderWall();
        g.teleport(g.player, 64.5f, DevMap.FLOOR + 5, 58.5f); g.controller.snapTo((float) Math.PI); game.tick(0);
        keys(1, 0, false, 40);
        assertNotNull(g.player.climb, "onto the ladder from the ledge");
        keys(-1, 0, false, 60 * 5);
        assertNull(g.player.climb, "stepped off at the bottom");
        assertTrue(g.player.y < DevMap.FLOOR + 0.3f);
    }

    // ------------------------------------------------------------------ the hang (0.3.0)
    private void wall(int height) {
        yard();
        for (int x = 61; x <= 67; x++) for (int y = 0; y < height; y++) g.setBlock(x, DevMap.FLOOR + y, 59, Blocks.BRICK);
        game.tick(0);
        g.player.skills.setLevel(rpg.skill.Skill.AGILITY, 20); g.player.restore(); g.player.eq.clear();
        g.teleport(g.player, 64.5f, DevMap.FLOOR, 60.8f); g.controller.snapTo(0); game.tick(0);
    }

    @Test void aOneBlockEdgeIsNeverHungFrom() {
        wall(1);
        keys(1, 0, true, 60);
        assertFalse(g.traversal.hanging(g.player));
        assertNull(g.player.traversal);
    }

    @Test void holdingSprintCarriesStraightOverTheEdgeAndLettingGoHangs() {
        wall(2);
        keys(1, 0, true, 90);
        assertNull(g.player.traversal, "sprint held: straight up and over");
        assertTrue(g.player.y > DevMap.FLOOR + 1.8f);
        wall(2);
        assertTrue(g.traversal.begin(g.player, 0));
        keys(0, 0, false, 60);
        assertTrue(g.traversal.hanging(g.player), "let go of sprint: the body hangs to shimmy");
    }

    // ------------------------------------------------------------------ the torch
    @Test void aTorchIsLitWithTheGuardKeyAndPutOutWhenPutAway() {
        yard();
        g.player.eq.clear();
        g.player.eq.set(ItemDef.Slot.MAIN, new ItemStack(Items.TORCH, 1));
        assertEquals(1, Items.TORCH.gw); assertEquals(2, Items.TORCH.gh);
        g.teleport(g.player, 64.5f, DevMap.FLOOR, 60.5f); game.tick(0);
        g.combat.sheathe(g.player, false);
        for (int i = 0; i < 60; i++) game.tick(1f / 60);
        g.controller.in.offDown = true; game.tick(1f / 60); g.controller.in.offDown = false;
        for (int i = 0; i < 90; i++) game.tick(1f / 60);
        assertTrue(g.player.torchLit, "lit after the strike");
        assertTrue(g.ai.stealth.light(g.player) > 0.9f, "a burning torch lights its bearer");
        g.combat.sheathe(g.player, true);
        for (int i = 0; i < 60; i++) game.tick(1f / 60);
        assertFalse(g.player.torchLit, "put away, it goes out");
    }

    // ------------------------------------------------------------------ riding
    private Animal horse(Species s, boolean tame, boolean saddled) {
        yard();
        Animal h = g.animals.spawnNew(s, false, true, 64.5f, 66.5f, null, 0, 0);
        h.yaw = 0;
        if (tame) { h.tamedBy = g.player.id; h.owner = g.player.id; }
        h.saddled = saddled;
        g.teleport(g.player, 66f, DevMap.FLOOR, 66.5f); game.tick(0);
        return h;
    }

    @Test void aSaddledTameHorseIsRiddenWhereTheRiderSteers() {
        Animal h = horse(Species.HORSE, true, true);
        assertFalse(g.riding.mount(g.player, h).rejected());
        assertEquals(h.id, g.player.riding);
        float z0 = h.z;
        keys(1, 0, false, 90);
        assertTrue(z0 - h.z > 3, "carried forward: " + (z0 - h.z));
        assertEquals(h.x, g.player.x, 0.2f, "the rider is in the saddle");
        g.controller.in.interact = true; game.tick(1f / 60); g.controller.in.interact = false; game.tick(1f / 60);
        assertEquals(0, g.player.riding, "dismounted");
    }

    @Test void anUnsaddledTameHorseCarriesButIsNotSteered() {
        Animal h = horse(Species.HORSE, true, false);
        g.riding.mount(g.player, h);
        float z0 = h.z;
        keys(1, 0, true, 90);
        assertEquals(z0, h.z, 0.3f, "no saddle: the reins mean nothing");
    }

    @Test void aWildHorseIsTamedByStayingOnItAndKeepsItsProgress() {
        Animal h = horse(Species.HORSE, false, false);
        g.player.skills.setLevel(rpg.skill.Skill.AGILITY, 99);
        g.riding.mount(g.player, h);
        for (int i = 0; i < 60 * 8 && g.player.riding != 0; i++) game.tick(1f / 60);
        float progress = h.tame;
        assertTrue(progress > 0.15f, "taming is earned in the saddle: " + progress);
        if (g.player.riding != 0) g.riding.dismount(g.player, false);
        game.tick(1f / 60);
        assertEquals(progress, h.tame, 1e-6, "kept after getting off");
        h.tame = 0.999f;
        g.teleport(g.player, h.x + 1.5f, DevMap.FLOOR, h.z); game.tick(0);
        g.riding.mount(g.player, h);
        for (int i = 0; i < 30; i++) game.tick(1f / 60);
        assertEquals(g.player.id, h.tamedBy, "tamed");
    }

    @Test void theJumpChargePeaksAtTenTicksThenSagsTowardEightyPercent() {
        Animal h = horse(Species.HORSE, true, true);
        g.riding.mount(g.player, h);
        var ride = g.riding.of(g.player);
        g.controller.in.jumpHeld = true;
        for (int i = 0; i < 10; i++) game.tick(1f / 60);
        assertEquals(1.0f, ride.charge, 0.12f, "at the peak");
        for (int i = 0; i < 120; i++) game.tick(1f / 60);
        assertEquals(Riding.CHARGE_FLOOR, ride.charge, 0.05f, "held too long it sags");
        g.controller.in.jumpHeld = false; game.tick(1f / 60);
        assertTrue(g.state.all().stream().anyMatch(e -> e.type().equals("MountJumped")));
    }

    @Test void eachHorseHasItsOwnStatsAndAFoalTakesAfterItsParents() {
        Animal a = horse(Species.HORSE, true, true);
        var sa = Riding.stats(a);
        var sb = rpg.mount.MountStats.horse(a.id + 17);
        assertNotEquals(sa.gallop, sb.gallop, 1e-4f);
        var foal = rpg.mount.MountStats.inherit(sa, sb, 99);
        assertTrue(foal.gallop > Math.min(sa.gallop, sb.gallop) * 0.85f && foal.gallop < Math.max(sa.gallop, sb.gallop) * 1.15f);
        assertTrue(Riding.stats(g.animals.spawnNew(Species.PEGASUS, false, true, 60.5f, 64.5f, null, 0, 0)).flies);
    }

    @Test void aSmallRiseIsSteppedInTheStrideWithoutAJump() {
        Animal h = horse(Species.HORSE, true, true);
        for (int x = 60; x <= 69; x++) for (int z = 55; z <= 62; z++) g.setBlock(x, DevMap.FLOOR, z, Blocks.PLANKS);
        game.tick(0);
        g.riding.mount(g.player, h);
        var ride = g.riding.of(g.player);
        float best = 0;
        for (int i = 0; i < 150; i++) { g.controller.in.forward = 1; game.tick(1f / 60); best = Math.max(best, h.y - DevMap.FLOOR);
            if (i % 15 == 0) System.out.println("[step] z " + h.z + " y " + (h.y - DevMap.FLOOR) + " ctl y " + (ride.body.y - DevMap.FLOOR) + " speed " + ride.speed + " blocked " + ride.body.blocked + " grounded " + ride.body.grounded); }
        g.controller.in.forward = 0;
        assertTrue(best > 0.9f, "up onto the step: y " + best + " z " + h.z);
    }

    // ------------------------------------------------------------------ the dead, and the hurt
    @Test void woundsEaseAsHealthReturns() {
        g.player.restore();
        var w = g.gore.of(g.player);
        w.hurt[4] = 0.9f;
        g.player.hp = g.player.maxHp() * 0.9f;
        game.tick(1f / 60);
        assertTrue(g.player.injury[4] <= 0.17f, "a little hurt is a little limp: " + g.player.injury[4]);
        g.player.hp = g.player.maxHp();
        game.tick(1f / 60);
        assertEquals(0, g.player.injury[4], 1e-6, "whole again, no limp");
    }

    @Test void dyingWhileHangingDoesNotPutTheRespawnBackOnTheLedge() {
        wall(2);
        assertTrue(g.traversal.begin(g.player, 0));
        keys(0, 0, false, 60);
        assertTrue(g.traversal.hanging(g.player));
        g.player.hp = 0; g.player.alive = false; g.onKilled(g.player, null, 0);
        assertNull(g.player.traversal);
        g.respawn();
        for (int i = 0; i < 10; i++) game.tick(1f / 60);
        assertTrue(Math.hypot(g.player.x - DevMap.GRAVEYARD_SPAWN[0], g.player.z - DevMap.GRAVEYARD_SPAWN[1]) < 2, "woke at the graveyard: " + g.player.x + "," + g.player.z);
    }
}
