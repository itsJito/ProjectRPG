package rpg.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import rpg.Rpg;
import rpg.actor.Appearance;
import rpg.ai.Npc;
import rpg.world.shadowveil.Atlas;
import rpg.world.shadowveil.ShadowVeil;
import rpg.world.shadowveil.Survey;
import rpg.world.shadowveil.Terrain;

/**
 * (0.4.0, gamefixes: ShadowVeil - automated validation and stress tests) The permanent world, grown, surveyed and run:
 * the design's rules held by the ground (ocean round all four edges, the elevation targets, Dimwater at Y44, rivers
 * running down to the sea, roads walkable, every settlement and chunk reachable), the world taken by the engine at its
 * full 1,072 x 200 x 1,072, the same design always growing the same ground, and a whole game day of every system run on
 * it without a fault, everyone standing on the ground, and the devmap untouched beside it.
 */
@org.junit.jupiter.api.TestMethodOrder(org.junit.jupiter.api.MethodOrderer.OrderAnnotation.class)
class ShadowVeilTest {
    static { System.setProperty("rpg.prof", "true"); System.setProperty("rpg.slowProbe", "true"); }
    private static Game game;
    private static Rpg g;

    @BeforeAll static void grow() {
        var cfg = new NativeEngine.Config(); cfg.deterministic = true; cfg.audioDevice = false;
        game = Game.create(cfg);
        game.activate(ShadowVeil.build(game.engine()));
        g = game.add(new Rpg(game, "ShadowVeil probe", new Appearance(), true));
    }
    @AfterAll static void close() { if (game != null) game.close(); WorldMap.activateDevmap(); }

    @Test @org.junit.jupiter.api.Order(1) void theGroundKeepsEveryRuleOfTheDesign() {
        Survey s = new Survey(ShadowVeil.terrain()).check();
        assertTrue(s.failures.isEmpty(), () -> "the survey failed: " + s.failures + "\n" + s.notes);
    }

    @Test @org.junit.jupiter.api.Order(2) void theEngineHoldsTheWholeWorldAndTheMapIsShadowVeil() {
        assertEquals(1072, g.vox.sizeX()); assertEquals(1072, g.vox.sizeZ()); assertEquals(200, g.vox.sizeY());
        assertTrue(WorldMap.shadowveil());
        assertEquals("Loxford", DevMap.ALPHA);
        // the far corner is open sea; the spawn stands on dry ground in Loxford
        assertEquals(Blocks.WATER, g.vox.block(1071, 29, 1071));
        assertEquals("Loxford", DevMap.jurisdiction(WorldMap.active().spawnX, WorldMap.active().spawnZ));
        int below = g.vox.block((int) g.player.x, (int) Math.floor(g.player.y) - 1, (int) g.player.z);
        assertTrue(g.vox.solid((int) g.player.x, (int) Math.floor(g.player.y) - 1, (int) g.player.z), "the player stands on ground (" + below + ")");
        // cells past x 1,024 are routed (the engine's packing was ten bits a side)
        float[] east = ShadowVeil.dry(1040, 540);
        assertTrue(g.vox.solid((int) east[0], (int) WorldMap.standY(east[0], east[1]) - 1, (int) east[1]));
    }

    @Test @org.junit.jupiter.api.Order(6) void probeWarm() {
        long t = System.nanoTime(); long before = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        g.vox.navigation.warm();
        System.gc(); long after = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        System.out.printf("[ShadowVeil] warm %.2f s, %d chunks, heap +%d MB%n", (System.nanoTime() - t) / 1e9, g.vox.navigation.rebuiltChunks, (after - before) >> 20);
    }

    @Test @org.junit.jupiter.api.Order(3) void theSameDesignAlwaysGrowsTheSameGround() {
        Terrain a = Terrain.generate(Atlas.get(), false), b = ShadowVeil.terrain();
        assertTrue(java.util.Arrays.equals(a.top, b.top), "the heights differ between two growths");
        assertTrue(java.util.Arrays.equals(a.water, b.water), "the water differs between two growths");
        assertEquals(a.trees.size(), b.trees.size());
    }

    @Test @org.junit.jupiter.api.Order(4) void everyoneTheDevmapHadLivesOnShadowVeilOnTheGround() {
        assertTrue(g.npcs.size() >= 90, "the whole roster came over: " + g.npcs.size());
        List<String> wrong = new ArrayList<>();
        for (Npc n : g.npcs) {
            if (n.x < 16 || n.z < 16 || n.x > 1056 || n.z > 1056) wrong.add(n.name + " off the map");
            float ground = WorldMap.standY(n.x, n.z);
            if (Math.abs(n.y - ground) > 1.5f) wrong.add(n.name + " at y " + n.y + " over ground " + ground);
        }
        assertTrue(wrong.isEmpty(), wrong::toString);
        Npc bram = (Npc) g.actor(rpg.Ids.LAWMAN_A);
        assertEquals("Loxford", bram.homeJurisdiction, "Loxford's lawman keeps Loxford's law");
        assertTrue(g.animals.all().size() > 20 && g.monsters.all().size() > 5, "the wildlife and the lairs came over");
        assertTrue(g.objects.nodes.size() > 1000, "the woods, outcrops and fishing waters are worked: " + g.objects.nodes.size());
    }

    /** The stress run: a game day at speed with every system awake - no exception, no one under the ground. */
    @Test @org.junit.jupiter.api.Order(5) void aWholeGameDayRunsWithoutAFault() {
        g.cfg.minutesPerSecond = 24;
        g.state.minutes = 5 * 60;
        long start = System.nanoTime(), worst = 0;
        int frames = 60 * 60;
        for (int f = 0; f < frames; f++) {
            long t = System.nanoTime();
            long[] before = g.prof.clone();
            game.tick(1f / 60);
            long took = System.nanoTime() - t;
            worst = Math.max(worst, took);
            if (took > 300_000_000L) {
                StringBuilder slow = new StringBuilder(String.format("[ShadowVeil] slow tick %d: %.0f ms -", f, took / 1e6));
                for (int k = 0; k < before.length; k++) if (g.prof[k] - before[k] > 20_000_000L) slow.append(String.format(" %s %.0f", Rpg.PROF_NAMES[k], (g.prof[k] - before[k]) / 1e6));
                System.out.println(slow);
            }
            if (f % 600 == 0) {
                // the player wanders the wild between the towns, as a traveller would
                float[] p = ShadowVeil.dry(200 + (f / 600) * 110, 900 - (f / 600) * 120);
                g.teleport(g.player, p[0], WorldMap.standY(p[0], p[1]), p[1]);
            }
        }
        double mean = (System.nanoTime() - start) / 1e6 / frames;
        System.out.println(g.profile());
        // who spent the route searches (the native pathfinder's expansions), the costliest first
        var agents = new ArrayList<>(g.lastAgents.agents);
        agents.sort((x, y) -> Integer.compare(y.expansions, x.expansions));
        for (var ag : agents.subList(0, Math.min(14, agents.size()))) {
            String who = "?";
            for (Npc n : g.npcs) if (n.agent == ag.handle) who = n.name + " " + n.role + " goal " + n.goal + " (" + n.lastOutcome + ")";
            for (var an : g.animals.all()) if (an.agent == ag.handle) who = an.kind() + " " + an.species;
            System.out.printf("  %-70s expansions %8d repaths %5d capped %4d partial %4d%n", who, ag.expansions, ag.repaths, ag.capHits, ag.partialPaths);
        }
        System.out.printf("[ShadowVeil] %d frames, %.2f ms a tick, worst %.1f ms, %d people, %d animals, %d monsters%n",
                frames, mean, worst / 1e6, g.npcs.size(), g.animals.all().size(), g.monsters.all().size());
        List<String> buried = new ArrayList<>();
        for (Npc n : g.npcs) if (n.alive && n.loaded && g.vox.solid((int) Math.floor(n.x), (int) Math.floor(n.y + 0.5f), (int) Math.floor(n.z))) buried.add(n.name);
        assertTrue(buried.isEmpty(), () -> "inside the ground: " + buried);
        assertTrue(mean < 16, "a tick must stay inside a frame: " + mean + " ms");
    }
}
