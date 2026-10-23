package rpg.actor;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import org.junit.jupiter.api.*;
import rpg.*;
import rpg.ai.Npc;
import rpg.item.ItemDef;
import rpg.objects.Corpse;
import rpg.world.*;
import static org.junit.jupiter.api.Assertions.*;

class BodyCarryTest {
    private Game game; private Rpg g; private Npc dead; private Corpse corpse;
    @BeforeEach void open() {
        var cfg = new NativeEngine.Config(); cfg.deterministic = true;
        game = Game.create(cfg); game.activate(DevMap.build(game.engine())); g = game.add(new Rpg(game, "Carry", new Appearance(), true));
        dead = (Npc) g.actor(Ids.BANDIT_1);
        for (Npc npc : g.npcs) if (npc != dead) g.ai.unload(npc);
        for (int x = 60; x <= 69; x++) for (int z = 54; z <= 65; z++) for (int y = DevMap.FLOOR; y < DevMap.FLOOR + 5; y++) g.setBlock(x, y, z, Blocks.AIR);
        g.teleport(g.player, 64.5f, DevMap.FLOOR, 63.5f); g.teleport(dead, 64.5f, DevMap.FLOOR, 60.5f); game.tick(0);
        dead.alive = false; dead.hp = 0; g.ai.onKilled(dead, g.player);
        for (int i = 0; i < 90; i++) game.tick(1f / 60);
        corpse = (Corpse) g.objects.get(dead.corpse);
        g.teleport(g.player, corpse.x, DevMap.FLOOR, corpse.z + 1.2f); g.controller.snapTo(0); game.tick(0);
    }
    @AfterEach void close() { game.close(); }
    @Test void pickingUpPreservesOriginalItemsDisablesWeaponsAndTapPutsItDown() {
        var items = corpse.contents.stacks().stream().map(Object::toString).toList();
        assertFalse(g.carrying.begin(g.player, corpse).rejected());
        for (int i = 0; i < 48; i++) game.tick(1f / 60);
        assertTrue(g.carrying.ready(g.player)); assertEquals(g.player.id, dead.carriedBy);
        assertFalse(g.combat.attack(g.player, false, Actor.Combat.Move.PUNCH));
        var pose = g.lastBodies.ragdoll(dead.ragdoll); assertTrue(pose.comY > DevMap.FLOOR + 0.6f);
        int neck = astratto.bridge.AstrattoNative.RD_NECK * 3, pelvis = astratto.bridge.AstrattoNative.RD_PELVIS * 3;
        assertTrue(Math.abs(pose.p[neck + 1] - pose.p[pelvis + 1]) < 0.15f,
                "carried spine should lie across the arms, not stand upright: " + java.util.Arrays.toString(pose.p));
        assertEquals(items, corpse.contents.stacks().stream().map(Object::toString).toList());
        g.controller.in.mainDown = true; game.tick(1f / 60); g.controller.in.mainDown = false; game.tick(1f / 60);
        for (int i = 0; i < 30; i++) game.tick(1f / 60);
        assertFalse(g.carrying.busy(g.player)); assertEquals(0, dead.carriedBy);
        assertTrue(g.state.all().stream().anyMatch(e -> e.type().equals("BodyPutDown")));
    }
    @Test void holdingAttackThrowsAndSavingDropsWithoutDuplicatingOwnership(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) {
        assertFalse(g.carrying.begin(g.player, corpse).rejected()); for (int i = 0; i < 48; i++) game.tick(1f / 60);
        g.controller.in.mainDown = true; for (int i = 0; i < 26; i++) game.tick(1f / 60);
        g.controller.in.mainDown = false; game.tick(1f / 60);
        assertFalse(g.carrying.busy(g.player)); assertTrue(g.state.all().stream().anyMatch(e -> e.type().equals("BodyThrown")));
        assertTrue(new SaveSystem(g).save(dir.resolve("carry.json"))); assertEquals(0, corpse.heldBy);
    }
    @Test void savingWhileHoldingReleasesTheOriginalBodyWithoutCopyingItsInventory(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) {
        var items = corpse.contents.stacks().stream().map(Object::toString).toList();
        long corpseId = corpse.id;
        assertFalse(g.carrying.begin(g.player, corpse).rejected());
        for (int i = 0; i < 48; i++) game.tick(1f / 60);
        var saves = new SaveSystem(g); var file = dir.resolve("held.json");
        assertTrue(saves.save(file)); assertFalse(g.carrying.busy(g.player)); assertEquals(0, dead.carriedBy);
        assertTrue(saves.load(file));
        Corpse loaded = (Corpse) g.objects.get(corpseId);
        assertNotNull(loaded); assertEquals(0, loaded.heldBy); assertEquals(0, g.player.carryBody);
        assertEquals(items, loaded.contents.stacks().stream().map(Object::toString).toList());
    }

    @Test void theSameOwnershipFlowCarriesAnArticulatedAnimalCarcass() {
        var animal = g.animals.release(rpg.animal.Species.DOG, false, false, 62.5f, 59.5f); animal.alive = false; animal.hp = 0;
        g.animals.onKilled(animal, g.player, 0); for (int i = 0; i < 90; i++) game.tick(1f / 60);
        g.teleport(g.player, animal.x, DevMap.FLOOR, animal.z + 1.2f); g.controller.snapTo(0); game.tick(0);
        var result = g.carrying.begin(g.player, animal); assertFalse(result.rejected(), result.reason());
        for (int i = 0; i < 50; i++) game.tick(1f / 60);
        assertTrue(g.carrying.ready(g.player)); assertEquals(g.player.id, animal.carriedBy);
        var trunk = g.lastRigids.body(animal.ragdoll.trunk().body()); assertTrue(trunk.y > DevMap.FLOOR + 0.5f);
        g.carrying.drop(g.player, false); game.tick(1f / 60); assertEquals(0, animal.carriedBy);
    }
    @Test void humanBodiesExcludeOtherLivingMoverVolumes() {
        long a = game.reserve(astratto.bridge.AstrattoNative.KIND_MOVER), b = game.reserve(astratto.bridge.AstrattoNative.KIND_MOVER);
        game.commands().moverCreate(a, 62, DevMap.FLOOR, 55, 0.3f, 1.8f, 0.6f, 0, 1.3f);
        game.commands().moverCreate(b, 65, DevMap.FLOOR, 55, 0.3f, 1.8f, 0.6f, 0, 1.3f);
        for (int i = 0; i < 150; i++) { game.commands().moverInput(a, 2, 0, 0, 0, 80, 0); game.commands().moverInput(b, -2, 0, 0, 0, 80, 0); game.tick(1f / 60); }
        var first = g.lastBodies.mover(a); var second = g.lastBodies.mover(b);
        assertTrue(second.x - first.x >= 0.59f, "characters must not pass through each other");
    }
}
