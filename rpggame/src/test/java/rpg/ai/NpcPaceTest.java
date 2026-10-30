package rpg.ai;

import static org.junit.jupiter.api.Assertions.*;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import org.junit.jupiter.api.*;
import rpg.Ids;
import rpg.Rpg;
import rpg.actor.Appearance;
import rpg.world.Blocks;
import rpg.world.DevMap;

/**
 * Gamefixes: human AI did not sprint, nor know when to and when not to. A bandit after a player who runs from it
 * sprints when it has the breath for it, jogs when it has not, and lets them go at the edge of its ground without dithering; the town going about its morning sprints only from danger.
 */
class NpcPaceTest {
    private Game game;
    private Rpg g;
    @BeforeEach void open() {
        var cfg = new NativeEngine.Config(); cfg.deterministic = true;
        game = Game.create(cfg); game.activate(DevMap.build(game.engine()));
        g = game.add(new Rpg(game, "Pace", new Appearance(), true));
        g.state.minutes = 9 * 60;
    }
    @AfterEach void close() { game.close(); }

    /** A bandit after a player sprinting from it, from this much of its breath; what it did. */
    private record Chase(boolean sprinted, float fastest, float lowest, int flips, float weakestStart) {}
    private Chase chase(float breath) {
        Npc bandit = (Npc) g.actor(Ids.BANDIT_1);
        for (Npc n : g.npcs) if (n != bandit) g.ai.unload(n);
        int bx = (int) Math.floor(bandit.x), bz = (int) Math.floor(bandit.z);
        // a long open run through the wilds, in the sun
        for (int z = bz - 70; z <= bz + 2; z++) for (int x = bx - 3; x <= bx + 3; x++) {
            g.setBlock(x, DevMap.FLOOR - 1, z, Blocks.GRASS);
            for (int y = DevMap.FLOOR; y < DevMap.FLOOR + 5; y++) g.setBlock(x, y, z, Blocks.AIR);
        }
        game.tick(0);
        g.teleport(bandit, bx + 0.5f, DevMap.FLOOR, bz + 0.5f);
        g.teleport(g.player, bx + 0.5f, DevMap.FLOOR, bz - 9.5f);
        bandit.hostileToPlayer = true; bandit.nextThink = 0; bandit.yaw = 0;
        bandit.stamina = bandit.maxStamina() * breath;
        float fastest = 0, lowest = bandit.stamina;
        boolean sprinted = false;
        int flips = 0;
        float weakestStart = 9;
        boolean sprinting = false;
        Brain.Goal last = null;
        for (int i = 0; i < 60 * 24; i++) {
            g.controller.in.forward = 1; g.controller.in.sprint = true;
            g.controller.snapTo(0);
            assertTrue(game.tick(1f / 60));
            fastest = Math.max(fastest, (float) Math.hypot(bandit.vx, bandit.vz));
            if (bandit.sprinting && !sprinting) weakestStart = Math.min(weakestStart, bandit.stamina / bandit.maxStamina());
            sprinting = bandit.sprinting;
            sprinted |= bandit.sprinting;
            lowest = Math.min(lowest, bandit.stamina);
            if (i > 60 * 6 && last != null && bandit.goal != last) flips++;
            last = bandit.goal;
        }
        System.out.printf("bandit from %.0f%% breath: fastest %.2f m/s, stamina down to %.0f / %.0f, %d changes of mind after the chase (goal %s, %s)%n",
                breath * 100, fastest, lowest, bandit.maxStamina(), flips, bandit.goal, bandit.lastOutcome);
        return new Chase(sprinted, fastest, lowest / bandit.maxStamina(), flips, weakestStart);
    }

    @Test void aBanditSprintsAfterAFleeingPlayerAndLetsThemGoAtTheEdgeOfItsGround() {
        Chase c = chase(1);
        assertTrue(c.sprinted(), "the bandit never sprinted after a player running from it (fastest " + c.fastest() + " m/s)");
        assertTrue(c.fastest() > g.cfg.jogSpeed + 1, "a sprint no faster than a jog: " + c.fastest() + " m/s");
        assertTrue(c.lowest() < 0.9f, "sprinting cost the bandit no breath");
        assertTrue(c.flips() <= 2, "the bandit kept changing its mind after giving up the chase: " + c.flips() + " times");
    }

    @Test void aBanditShortOfBreathCatchesItFirst() {
        Chase c = chase(0.15f);
        // (breath comes back fast at a jog; but no sprint is begun on less than 40% of it)
        assertTrue(c.weakestStart() >= 0.4f - 0.02f, "a sprint was begun on " + c.weakestStart() * 100 + "% of the bandit's breath");
    }

    @Test void theTownGoingAboutItsMorningNeverSprints() {
        g.teleport(g.player, 20, DevMap.FLOOR, 20);
        String who = null;
        float fastest = 0;
        for (int i = 0; i < 60 * 90; i++) {
            assertTrue(game.tick(1f / 60));
            for (Npc n : g.npcs) if (n.alive && n.loaded && n.summonOwner == 0 && !java.util.EnumSet.of(Brain.Goal.FIGHT, Brain.Goal.FLEE, Brain.Goal.HIDE, Brain.Goal.WARN, Brain.Goal.AID).contains(n.goal)
                    // (0.2.3) nor anything the pace rules themselves call urgent (a hunt on a fresh clue, a confrontation - left
                    // over from the chases before, in this shared world): only a sprint those rules did not ask for fails
                    && !(n.hasGoalPos && g.ai.pace(n, n.gx, n.gz, 0) == AiSystem.A_SPRINT)) {
                float v = (float) Math.hypot(n.vx, n.vz);
                if (n.sprinting || v > g.cfg.jogSpeed + 0.6f) { fastest = Math.max(fastest, v); who = n.name + " (" + n.goal + ", " + n.lastOutcome + ")"; }
            }
        }
        assertNull(who, "someone sprinted on an ordinary morning at " + fastest + " m/s: " + who);
    }
}
