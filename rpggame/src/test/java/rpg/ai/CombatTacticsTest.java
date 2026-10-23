package rpg.ai;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import rpg.Ids;
import rpg.Rpg;
import rpg.actor.Appearance;
import rpg.item.ItemDef;
import rpg.item.ItemStack;
import rpg.item.Items;
import rpg.world.Blocks;
import rpg.world.DevMap;
import static org.junit.jupiter.api.Assertions.*;

class CombatTacticsTest {
    private Game game;
    private Rpg g;
    private Npc guard;
    @BeforeEach void open() {
        var cfg = new NativeEngine.Config(); cfg.deterministic = true;
        game = Game.create(cfg); game.activate(DevMap.build(game.engine()));
        g = game.add(new Rpg(game, "Perceived tactics", new Appearance(), true));
        guard = (Npc) g.actor(Ids.GUARD);
        for (Npc n : g.npcs) if (n != guard) g.ai.unload(n);
        for (int x = 56; x <= 74; x++) for (int z = 50; z <= 71; z++) {
            g.setBlock(x, DevMap.FLOOR - 1, z, Blocks.GRASS);
            for (int y = DevMap.FLOOR; y <= DevMap.FLOOR + 4; y++) g.setBlock(x, y, z, Blocks.AIR);
        }
        g.teleport(guard, 64.5f, DevMap.FLOOR, 64.5f); guard.yaw = guard.wantYaw = 0;
        g.teleport(g.player, 64.5f, DevMap.FLOOR, 56.5f);
        g.state.minutes = 600;
        guard.nextThink = Double.MAX_VALUE;
        assertTrue(game.tick(0));
    }
    @AfterEach void close() { if (game != null) game.close(); }

    @Test void guardsQuestionVisibleBloodWithoutInventingACrime() {
        g.player.bloodied = 0.8f;
        g.ai.brain.think(guard);
        assertEquals(Brain.Goal.WATCH, guard.goal);
        assertEquals(g.player.id, guard.watchTarget);
        g.ai.brain.act(guard, 1f / 60);
        assertTrue(guard.lastOutcome.contains("visible blood"));
        assertEquals(0, g.law.bounty(g.player.id, guard.homeJurisdiction));
        assertNull(guard.crimeKnown(g.player.id));
        g.player.bloodied = 0; g.ai.brain.think(guard);
        assertNotEquals(Brain.Goal.WATCH, guard.goal, "washed blood must not create a lasting accusation");
    }
    @Test void bloodyNpcsAreQuestionedButBloodBehindAWallIsNotKnown() {
        Npc villager = (Npc) g.actor(Ids.VILLAGER); g.ai.reload(villager);
        g.teleport(villager, 64.5f, DevMap.FLOOR, 60.5f); villager.bloodied = 1;
        assertTrue(game.tick(0));
        g.ai.brain.think(guard);
        assertEquals(villager.id, guard.watchTarget);
        assertEquals(Brain.Goal.WATCH, guard.goal);
        for (int y = DevMap.FLOOR; y <= DevMap.FLOOR + 3; y++) g.setBlock(64, y, 62, Blocks.STONE);
        assertTrue(game.tick(0));
        g.ai.brain.think(guard);
        assertNotEquals(Brain.Goal.WATCH, guard.goal);
        assertEquals(0, g.law.bounty(villager.id, guard.homeJurisdiction));
    }
    @Test void incomingFireChoosesPhysicalCoverAndDestroyedCoverIsReconsidered() {
        for (int y = DevMap.FLOOR; y <= DevMap.FLOOR + 3; y++) g.setBlock(66, y, 62, Blocks.STONE);
        g.player.eq.set(ItemDef.Slot.MAIN, new ItemStack(Items.get("oak_shortbow"), 1));
        guard.cs.lastAttacker = g.player.id; guard.cs.lastHurt = g.time;
        guard.fightTarget = g.player.id;
        assertTrue(game.tick(0));
        var tactics = new CombatTactics(g);
        assertTrue(tactics.move(guard, g.player, true));
        assertEquals(CombatTactics.Kind.COVER, guard.tactic.kind);
        assertFalse(g.vox.sight(g.player.x, g.player.eyeY(), g.player.z, guard.tactic.x, guard.eyeY(), guard.tactic.z));
        assertFalse(g.vox.solid((int) guard.tactic.x, DevMap.FLOOR, (int) guard.tactic.z));
        long[] skills = guard.skills.raw().clone();
        for (int y = DevMap.FLOOR; y <= DevMap.FLOOR + 3; y++) g.setBlock(66, y, 62, Blocks.AIR);
        assertTrue(game.tick(0));
        assertFalse(tactics.move(guard, g.player, true));
        assertNull(guard.tactic);
        assertArrayEquals(skills, guard.skills.raw());
    }
    @Test void flankingNeedsAnAllyTheNpcCanActuallySee() {
        Npc ally = (Npc) g.actor(Ids.BANDIT_1); g.ai.reload(ally);
        ally.team = guard.team; ally.fightTarget = g.player.id;
        g.teleport(ally, 62.5f, DevMap.FLOOR, 61.5f);
        ally.spawnX = ally.x; ally.spawnZ = ally.z; ally.nextThink = Double.MAX_VALUE; ally.goal = Brain.Goal.IDLE;
        assertTrue(game.tick(0));
        var tactics = new CombatTactics(g);
        assertEquals(1, tactics.allies(guard, g.player));
        assertTrue(tactics.move(guard, g.player, true));
        assertEquals(CombatTactics.Kind.FLANK, guard.tactic.kind);
        assertTrue(Math.abs(guard.tactic.x - g.player.x) > 1, "a flank needs lateral separation from the direct approach");
        ally.loaded = false;
        assertEquals(0, tactics.allies(guard, g.player));
    }
    @Test void theNativeAgentCanActuallyReachItsChosenCover() {
        for (int y = DevMap.FLOOR; y <= DevMap.FLOOR + 3; y++) g.setBlock(66, y, 62, Blocks.STONE);
        assertTrue(game.tick(0));
        guard.cs.lastAttacker = g.player.id; guard.cs.lastHurt = g.time;
        g.player.eq.set(ItemDef.Slot.MAIN, new ItemStack(Items.get("oak_shortbow"), 1));
        guard.goal = Brain.Goal.FIGHT; guard.fightTarget = g.player.id; guard.nextThink = Double.MAX_VALUE;
        var tactics = new CombatTactics(g);
        assertTrue(tactics.move(guard, g.player, true));
        float x = guard.tactic.x, z = guard.tactic.z;
        double nearest = Math.hypot(guard.x - x, guard.z - z);
        for (int frame = 0; frame < 120; frame++) {
            assertTrue(game.tick(1f / 60));
            nearest = Math.min(nearest, Math.hypot(guard.x - x, guard.z - z));
        }
        assertTrue(nearest < 1.3, "cover must be usable by the actual native navigator; nearest distance " + nearest);
    }
}
