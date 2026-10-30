package rpg.actor;

import static org.junit.jupiter.api.Assertions.*;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import org.junit.jupiter.api.*;
import rpg.Rpg;
import rpg.skill.Skill;
import rpg.world.Blocks;
import rpg.world.DevMap;

/**
 * Gamefixes 0.2.1: people should vault over one-block obstacles such as fences, on the climbing system, from Agility
 * 2. A fence ahead is vaulted - hands on its top, over, and down on the far side at the same height - while a wall two
 * blocks high is not (that is a climb, at 5), and below Agility 2 nobody vaults.
 */
class VaultTest {
    private Game game;
    private Rpg g;
    private float[] lane;
    @BeforeEach void open() {
        var cfg = new NativeEngine.Config(); cfg.deterministic = true; game = Game.create(cfg); game.activate(DevMap.build(game.engine()));
        g = game.add(new Rpg(game, "Vault", Appearance.henric(), true)); for (var n : g.npcs) g.ai.unload(n);
        lane = rpg.TestLanes.open(g);
    }
    @AfterEach void close() { game.close(); }

    /** The player in the lane facing -z, a fence (or a wall of the given height) two metres ahead across it. */
    private int setUp(int agility, int block, int height) {
        int fz = (int) Math.floor(lane[1]) - 2;
        for (int x = (int) lane[0] - 2; x <= (int) lane[0] + 2; x++) for (int h = 0; h < height; h++) g.setBlock(x, DevMap.FLOOR + h, fz, block);
        g.teleport(g.player, lane[0], DevMap.FLOOR, lane[1] - 0.9f);
        g.player.yaw = 0; g.controller.snapTo(0);
        g.player.skills.setLevel(Skill.AGILITY, agility);
        g.player.stamina = g.player.maxStamina();
        for (int i = 0; i < 20; i++) game.tick(1f / 60);
        return fz;
    }

    @Test void aFenceIsVaultedFromAgilityTwo() {
        int fz = setUp(2, Blocks.FENCE, 1);
        var anchor = g.traversal.vault(g.player, 0);
        assertNotNull(anchor, "no vault over the fence ahead");
        assertTrue(anchor.vault());
        assertTrue(g.traversal.begin(g.player, 0));
        float y0 = g.player.y;
        float highest = y0;
        for (int i = 0; i < 90 && g.player.traversal != null; i++) { assertTrue(game.tick(1f / 60)); highest = Math.max(highest, g.player.y); }
        for (int i = 0; i < 20; i++) game.tick(1f / 60);
        assertNull(g.player.traversal, "the vault never finished");
        assertTrue(g.player.z < fz, "not over the fence: z " + g.player.z + " (fence at " + fz + ")");
        assertEquals(y0, g.player.y, 0.25f, "landed at a different height");
        assertTrue(highest > y0 + 0.45f, "never went up over the fence (" + (highest - y0) + ")");
    }

    @Test void noVaultBelowAgilityTwoNorOverATallWall() {
        setUp(1, Blocks.FENCE, 1);
        assertNull(g.traversal.vault(g.player, 0), "vaulted at Agility 1");
        assertFalse(g.traversal.begin(g.player, 0));
    }

    @Test void aWallTwoHighIsNoVault() {
        setUp(10, Blocks.STONE_BRICKS, 2);
        assertNull(g.traversal.vault(g.player, 0), "a two-block wall was vaulted");
    }
}
