package rpg.actor;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import org.junit.jupiter.api.*;
import rpg.*;
import rpg.skill.Skill;
import rpg.world.*;
import static org.junit.jupiter.api.Assertions.*;

class TraversalTest {
    private Game game; private Rpg g;
    @BeforeEach void open() {
        var cfg = new NativeEngine.Config(); cfg.deterministic = true;
        game = Game.create(cfg); game.activate(DevMap.build(game.engine())); g = game.add(new Rpg(game, "Traversal", new Appearance(), true));
        for (var npc : g.npcs) g.ai.unload(npc);
        for (int x = 60; x <= 69; x++) for (int z = 55; z <= 65; z++) {
            g.setBlock(x, DevMap.FLOOR - 1, z, Blocks.STONE);
            for (int y = DevMap.FLOOR; y < DevMap.FLOOR + 7; y++) g.setBlock(x, y, z, Blocks.AIR);
        }
        for (int x = 63; x <= 65; x++) for (int y = 0; y < 2; y++) g.setBlock(x, DevMap.FLOOR + y, 59, Blocks.BRICK);
        g.teleport(g.player, 64.5f, DevMap.FLOOR, 60.8f); g.controller.snapTo(0); game.tick(0);
    }
    @AfterEach void close() { game.close(); }
    @Test void levelOneCannotTraverseButLevelFiveMantlesAnUnmarkedTwoBlockLedge() {
        assertFalse(g.traversal.begin(g.player, 0));
        g.player.skills.setLevel(Skill.AGILITY, 5); long before = g.player.skills.xp(Skill.AGILITY);
        assertTrue(g.traversal.begin(g.player, 0));
        for (int i = 0; i < 75; i++) assertTrue(game.tick(1f / 60));
        assertNull(g.player.traversal); assertTrue(g.player.y >= DevMap.FLOOR + 1.9f, "feet " + g.player.y);
        assertTrue(g.player.z < 60); assertTrue(g.player.skills.xp(Skill.AGILITY) > before);
    }
    @Test void fullAgilityUnlocksHigherGeometryWithoutLettingLevelFiveUseIt() {
        for (int x = 63; x <= 65; x++) for (int y = 2; y < 4; y++) g.setBlock(x, DevMap.FLOOR + y, 59, Blocks.BRICK);
        game.tick(0); g.player.skills.setLevel(Skill.AGILITY, 5); assertFalse(g.traversal.begin(g.player, 0));
        g.player.skills.setLevel(Skill.AGILITY, 100); assertTrue(g.traversal.begin(g.player, 0));
        assertTrue(g.player.traversal.duration <= 1.2f);
        for (int i = 0; i < 80; i++) game.tick(1f / 60);
        assertNull(g.player.traversal); assertTrue(g.player.y > DevMap.FLOOR + 3.9f);
    }
    @Test void anNpcUsesTheSameMantleToPursueAnElevatedTargetWithoutGainingXp() {
        for (int x = 63; x <= 65; x++) for (int z = 57; z <= 59; z++) for (int y = 0; y < 2; y++) g.setBlock(x, DevMap.FLOOR + y, z, Blocks.BRICK);
        var npc = (rpg.ai.Npc) g.actor(Ids.BANDIT_1); g.ai.reload(npc); g.teleport(npc, 64.5f, DevMap.FLOOR, 60.8f);
        g.teleport(g.player, 64.5f, DevMap.FLOOR + 2.03f, 57.5f); npc.yaw = npc.wantYaw = 0;
        npc.cs.lastAttacker = g.player.id; npc.cs.lastHurt = g.time; npc.nextThink = 0;
        long[] skills = npc.skills.raw().clone(); game.tick(0);
        for (int i = 0; i < 100; i++) game.tick(1f / 60);
        assertTrue(npc.y > DevMap.FLOOR + 1.8f, npc.goal + " / " + npc.lastOutcome + " y=" + npc.y);
        assertArrayEquals(skills, npc.skills.raw());
    }
    @Test void occupiedLandingOrClosedGateDoesNotBecomeATraversalAnchor() {
        g.player.skills.setLevel(Skill.AGILITY, 100);
        g.setBlock(64, DevMap.FLOOR + 3, 59, Blocks.STONE); game.tick(0);
        assertFalse(g.traversal.begin(g.player, 0));
        g.setBlock(64, DevMap.FLOOR + 3, 59, Blocks.AIR);
        for (int x = 63; x <= 65; x++) for (int y = 0; y < 2; y++) g.setBlock(x, DevMap.FLOOR + y, 59, Blocks.LOCKED_DOOR);
        game.tick(0); assertFalse(g.traversal.begin(g.player, 0));
    }
    @Test void changingTheLandingDuringAMantleCancelsWithoutPassingThroughIt() {
        g.player.skills.setLevel(Skill.AGILITY, 5); assertTrue(g.traversal.begin(g.player, 0));
        for (int i = 0; i < 20; i++) game.tick(1f / 60);
        g.setBlock(64, DevMap.FLOOR + 2, 59, Blocks.STONE); game.tick(0); game.tick(1f / 60);
        assertNull(g.player.traversal); assertTrue(g.player.z > 60);
    }
    @Test void headTrackingDoesNotDragAStandingThirdPersonBodyAround() {
        float yaw = g.player.yaw; g.controller.in.lookYaw = 2.5f;
        for (int i = 0; i < 30; i++) game.tick(1f / 60);
        assertEquals(yaw, g.player.yaw, 0.001f); assertTrue(Math.abs(g.controller.camYaw - yaw) > 2);
    }
}
