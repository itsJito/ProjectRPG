package rpg.law;

import static org.junit.jupiter.api.Assertions.*;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import org.junit.jupiter.api.*;
import rpg.*;
import rpg.actor.Appearance;
import rpg.ai.*;
import rpg.world.DevMap;

/**
 * Gamefixes: after lying to a guard successfully, every guard still chased the player (without attacking or jailing
 * them). A wanted player is halted, talks their way out, and stands about: no officer of that city may keep after them.
 */
class LieToGuardTest {
    private Game game;
    private Rpg g;
    @BeforeEach void open() {
        var cfg = new NativeEngine.Config(); cfg.deterministic = true;
        game = Game.create(cfg); game.activate(DevMap.build(game.engine()));
        g = game.add(new Rpg(game, "Lie", new Appearance(), true));
        for (Npc npc : g.npcs) npc.memories.clear();
        g.state.minutes = 600;
    }
    @AfterEach void close() { game.close(); }
    private void frames(int n) { for (int i = 0; i < n; i++) assertTrue(game.tick(1f / 60)); }
    private static boolean law(Npc n) { return n.role == Npc.Role.GUARD || n.role == Npc.Role.LAWMAN; }

    @Test void aSuccessfulLieCallsOffTheWholeWatch() {
        Npc guard = (Npc) g.actor(Ids.GUARD);
        Npc bram = (Npc) g.actor(Ids.LAWMAN_A);
        g.teleport(g.player, guard.x + 6, DevMap.FLOOR, guard.z);
        game.tick(0);
        // a theft, seen by a villager (hearsay for the watch), reported: wanted, and pursued
        Npc seer = g.npcs.stream().filter(n -> n.role == Npc.Role.VILLAGER && DevMap.ALPHA.equals(n.homeJurisdiction)).findFirst().orElseThrow();
        Memory m = new Memory("THEFT", g.player.id, seer.id, DevMap.ALPHA, g.state.minutes, 0.6f, "witnessed", 91, 400, "theft");
        seer.remember(m, 24);
        g.teleport(seer, bram.x + 1, DevMap.FLOOR, bram.z);
        assertTrue(g.law.report(seer, m, bram).committed());
        // the watch hears and spreads it, then closes in
        frames(60 * 8);
        long chasing = g.npcs.stream().filter(n -> law(n) && n.alive && n.loaded && (n.fightTarget == g.player.id || n.hunt != null && n.hunt.target == g.player.id)).count();
        System.out.println("before the lie: " + chasing + " officers after the player");
        // the lie, landing (a request whose roll succeeds)
        Npc halting = g.npcs.stream().filter(n -> law(n) && n.alive && n.loaded && DevMap.ALPHA.equals(n.homeJurisdiction)).min((a, b) -> Float.compare(a.distTo(g.player), b.distTo(g.player))).orElseThrow();
        long req = g.state.newRequestId();
        while (!lands(halting, req)) req = g.state.newRequestId();
        assertTrue(g.law.denyCharge(g.player, halting, req).committed());
        assertEquals(0, g.law.bounty(g.player.id, DevMap.ALPHA));
        assertFalse(g.law.inCustody(g.player));
        // Standing with a drawn sword can start an independent weapons warning; this probe concerns the closed charge.
        g.combat.sheathe(g.player,true);
        // half a minute standing about: nobody of the watch pursues, searches for or squares up to the player
        int worst = 0;
        String who = "";
        for (int s = 0; s < 30; s++) {
            frames(60);
            int n = 0; StringBuilder b = new StringBuilder();
            for (Npc o : g.npcs) if (law(o) && o.alive && o.loaded && pursuing(o)) { n++; b.append(String.format(" %s(%s, %s, %.0f m)", o.name, o.goal, o.lastOutcome, o.distTo(g.player))); }
            if (n > worst) { worst = n; who = b.toString(); }
        }
        System.out.println("after the lie, the most at once: " + worst + who);
        assertEquals(0, worst, "officers still after the player after a lie that landed:" + who);
    }

    private boolean pursuing(Npc o) {
        long me = g.player.id;
        return o.fightTarget == me || o.hunt != null && o.hunt.target == me || o.watchTarget == me || o.investigation != null && o.investigation.subject == me
                || (o.goal == Brain.Goal.FIGHT || o.goal == Brain.Goal.SEARCH || o.goal == Brain.Goal.WATCH) && o.distTo(g.player) < 30 && (o.fightTarget == me || o.watchTarget == me);
    }

    /** Whether the dispute with this request id succeeds (the same roll as Law.denyCharge). */
    private boolean lands(Npc guard, long request) {
        float evidence = g.law.certainty(guard, g.player);
        float chance = Math.max(0.02f, Math.min(0.75f, (1 - evidence) * 0.7f + g.player.skills.level(rpg.skill.Skill.THIEVING) * 0.001f));
        double roll = Math.floorMod(request * 6364136223846793005L + guard.id * 1442695040888963407L, 10000) / 10000.0;
        return roll < chance;
    }
}
