package rpg.ai;

import static org.junit.jupiter.api.Assertions.*;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import org.junit.jupiter.api.*;
import rpg.Ids;
import rpg.Rpg;
import rpg.actor.Appearance;
import rpg.combat.CombatSystem;
import rpg.world.DevMap;

/**
 * Gamefixes: NPCs react to the player swinging a weapon, loosing arrows or casting in a town - each kind of person in
 * their own way. A blade swung in the street beside a guard and a townswoman: she flinches away, the guard keeps an eye on the
 * player; it is no crime. A spell cast near the scholar draws her to look.
 */
class ArmsReactionTest {
    private Game game;
    private Rpg g;
    @BeforeEach void open() {
        var cfg = new NativeEngine.Config(); cfg.deterministic = true;
        game = Game.create(cfg); game.activate(DevMap.build(game.engine()));
        g = game.add(new Rpg(game, "Arms", new Appearance(), true));
        g.state.minutes = 11 * 60;
        for (Npc n : g.npcs) n.memories.clear();
    }
    @AfterEach void close() { game.close(); }
    private void frames(int n) { for (int i = 0; i < n; i++) assertTrue(game.tick(1f / 60)); }

    @Test void aBladeSwungInTheStreetStartlesTheTownAndDrawsTheWatch() {
        Npc cole = (Npc) g.actor(Ids.GUARD), eda = (Npc) g.actor(Ids.VILLAGER);
        for (Npc n : g.npcs) if (n != cole && n != eda) g.ai.unload(n);
        float x = cole.x, z = cole.z;
        assertNotEquals(DevMap.WILDS, DevMap.jurisdiction(x, z), "the guard stands in a town");
        g.teleport(g.player, x + 5, DevMap.FLOOR, z);
        g.teleport(eda, x + 6.5f, DevMap.FLOOR, z + 1.5f);
        g.controller.snapTo((float) (Math.PI / 2));   // facing away from both, -x
        frames(30);
        for (int k = 0; k < 3; k++) { g.controller.snapTo((float)Math.atan2(-(g.player.x-eda.x),-(g.player.z-eda.z))); assertTrue(g.combat.attack(g.player, false, CombatSystem.lightMove(g.player, false)), "swing " + k); frames(70); }
        System.out.printf("eda: goal %s, fear %s, said '%s'; cole: goal %s, watching %s, %s%n", eda.goal, eda.fearOf == g.player.id, eda.speech, cole.goal, cole.watchTarget == g.player.id, cole.lastOutcome);
        assertEquals(g.player.id, eda.fearOf, "the townswoman beside the swinging blade backs away from it");
        assertEquals(g.player.id, cole.watchTarget, "the guard keeps an eye on whoever is swinging a blade");
        boolean watching = false;
        for (int i = 0; i < 60 * 4 && !watching; i++) { frames(1); watching = cole.goal == Brain.Goal.WATCH; }
        assertTrue(watching, "the guard comes to watch (goal " + cole.goal + ", " + cole.lastOutcome + ")");
        assertEquals(0, g.law.bounty(g.player.id, DevMap.jurisdiction(x, z)), "showing arms is no crime");
        assertNotEquals(Brain.Goal.FIGHT, cole.goal, "nor a reason for the guard to fight");
    }

    @Test void aSpellNearTheScholarDrawsHerToLook() {
        Npc scholar = (Npc) g.actor(Ids.SCHOLAR);
        for (Npc n : g.npcs) if (n != scholar) g.ai.unload(n);
        // within her own ground (nobody leaves their routine's bounds to go and look), in the open: she 9 m from her post,
        // the player at it
        int px = (int) Math.floor(scholar.postX), pz = (int) Math.floor(scholar.postZ);
        for (int x = px - 10; x <= px + 10; x++) for (int z = pz - 2; z <= pz + 2; z++) for (int y = DevMap.FLOOR; y < DevMap.FLOOR + 4; y++) g.setBlock(x, y, z, rpg.world.Blocks.AIR);
        game.tick(0);
        g.teleport(g.player, scholar.postX, DevMap.FLOOR, scholar.postZ);
        g.teleport(scholar, scholar.postX + (ZonePolicy.routineAllowed(scholar, scholar.postX + 9, scholar.postZ) ? 9 : -9), DevMap.FLOOR, scholar.postZ);
        frames(30);
        float before = scholar.distTo(g.player);
        g.ai.arms.shown(g.player, Arms.Kind.SPELL);
        frames(60 * 8);   // (the way round may be longer than the distance)
        System.out.printf("scholar: %.1f m -> %.1f m, %s, goal %s, scores %s%n", before, scholar.distTo(g.player), scholar.lastOutcome, scholar.goal, scholar.scores);
        assertTrue(scholar.armsSeen >= 1, "the scholar saw the spell");
        assertTrue(scholar.distTo(g.player) < before - 2, "the scholar came closer to see the working");
    }
}
