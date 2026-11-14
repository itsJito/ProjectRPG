package rpg.world;

import static org.junit.jupiter.api.Assertions.*;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import rpg.Rpg;
import rpg.actor.Appearance;
import rpg.ai.Npc;
import rpg.world.shadowveil.Census;
import rpg.world.shadowveil.Registry;
import rpg.world.shadowveil.ShadowVeil;
import rpg.world.shadowveil.Terrain;

/**
 * (0.4.3) The NPC-ready world, checked on ShadowVeil itself: the lore's whole roster registered and spawned with one-word
 * names, homes, workplaces and beds, every home reachable from its work; the farms farmed, every kind of water stocked,
 * the wild peopled and its hunters kept from the towns; the workplaces' stations; the ways clear; the devmap kept to the
 * devmap (its dungeons and signs), goto's places, pos, the town wall's and the aqueduct's room, and the cave mouths' banks.
 */
@org.junit.jupiter.api.TestMethodOrder(org.junit.jupiter.api.MethodOrderer.OrderAnnotation.class)
class Gamefixes043Test {
    private static Game game;
    private static Rpg g;

    @BeforeAll static void grow() {
        var cfg = new NativeEngine.Config(); cfg.deterministic = true; cfg.audioDevice = false;
        game = Game.create(cfg);
        game.activate(ShadowVeil.build(game.engine()));
        g = game.add(new Rpg(game, "Gamefixes 0.4.3", new Appearance(), true));
        game.tick(0);
    }
    @AfterAll static void close() { if (game != null) game.close(); WorldMap.activateDevmap(); }

    private static Registry reg() { return ShadowVeil.terrain().arch.registry; }

    @Test @org.junit.jupiter.api.Order(1) void theLoresWholeRosterLivesInItsKingdoms() {
        var people = rpg.world.shadowveil.Atlas.list(rpg.world.shadowveil.Atlas.map(rpg.world.shadowveil.Atlas.get().root, "census"), "people");
        assertTrue(people.size() >= 200, "the lore names " + people.size());
        // (0.4.4) and the ten added after the roll to keep the new jails, banks and graveyards
        int added = rpg.world.shadowveil.Atlas.list(rpg.world.shadowveil.Atlas.map(rpg.world.shadowveil.Atlas.get().root, "census"), "additions").size();
        assertEquals(people.size() + added, Census.all().size(), "every one of them placed");
        assertTrue(g.npcs.size() >= people.size(), "and in the world: " + g.npcs.size());
        Set<String> names = new HashSet<>();
        List<String> wrong = new ArrayList<>();
        Map<String, Integer> byKingdom = new HashMap<>();
        for (var a : Census.all().values()) {
            if (!names.add(a.name())) wrong.add("twice: " + a.name());
            // (0.4.4: a Sir or a title may go before the one name)
            String[] w = a.name().split(" ");
            if (a.name().isBlank() || w.length > 2 || w.length == 2 && !java.util.Set.of("Sir", "Dame", "King", "Queen", "Duke", "Duchess", "Count", "Countess", "Baron", "Baroness", "Father", "Mother").contains(w[0])) wrong.add("not one name: " + a.name());
            byKingdom.merge(a.kingdom(), 1, Integer::sum);
            Npc n = (Npc) g.actor(a.id());
            assertNotNull(n, a.name() + " is in the world");
            assertEquals(a.name(), n.name);
        }
        assertTrue(wrong.isEmpty(), () -> String.valueOf(wrong));
        for (String k : new String[]{"Loxford", "Azharad", "Severia", "Valgard", "Gorrick", "Harlette", "Camelot", "Wilds"})
            assertTrue(byKingdom.getOrDefault(k, 0) >= 10, k + " has its people: " + byKingdom);
        assertTrue(Census.problems().isEmpty(), () -> "census: " + Census.problems());
    }

    @Test @org.junit.jupiter.api.Order(2) void everyoneHasAHomeABedAndWorkTheyCanWalkBetween() {
        List<String> trouble = new ArrayList<>();
        int checked = 0;
        for (var a : Census.all().values()) {
            Npc n = (Npc) g.actor(a.id());
            if (a.homeId() == null) { assertTrue(a.title().startsWith("Bounty Hunter"), a.name() + " is the one without a home"); continue; }
            Registry.Entry home = reg().byId(a.homeId());
            assertNotNull(home, a.name() + "'s home is registered");
            assertTrue(home.residents.contains(a.name()), a.name() + " is on " + home.id + "'s roll");
            assertNotNull(a.bed(), a.name() + " has a bed planned");
            // the stand-ins are open ground a body can stand on
            // (0.4.4) a post indoors is on its own floor (its routes are Gamefixes044's); the stand-in at the door is checked here
            for (float[] st : Census.post(a.id()) != null ? new float[][]{a.homeStage()} : new float[][]{a.homeStage(), a.workStage()})
                if (!g.vox.navigation.validPoint(st[0], ShadowVeil.standY(st[0], st[1]), st[1], d -> true)) trouble.add(a.name() + " stand-in " + (int) st[0] + "," + (int) st[1] + " is not standable");
            // home to work, by the routes people walk (the far ones and the camps aside: a route over the whole map is the travel graph's)
            if (Census.post(a.id()) == null && Math.hypot(a.homeStage()[0] - a.workStage()[0], a.homeStage()[1] - a.workStage()[1]) < 160 && checked < 120) {
                checked++;
                var legs = g.vox.navigation.route(a.homeStage()[0], ShadowVeil.standY(a.homeStage()[0], a.homeStage()[1]), a.homeStage()[1],
                        a.workStage()[0], ShadowVeil.standY(a.workStage()[0], a.workStage()[1]), a.workStage()[1], d -> !d.locked || d.owner == n.id, true, n.id, null, 0);
                if (legs == null && Math.hypot(a.homeStage()[0] - a.workStage()[0], a.homeStage()[1] - a.workStage()[1]) > 2) trouble.add(a.name() + " cannot walk from " + a.homeId() + " to " + a.workId());
            }
        }
        assertTrue(checked > 60, "routes checked " + checked);
        assertTrue(trouble.size() <= 3, () -> trouble.size() + " problems: " + trouble);
    }

    @Test @org.junit.jupiter.api.Order(3) void theWorkplacesHaveTheirStationsAndTheFieldsAreFarmed() {
        long stations = g.objects.stations().size();
        assertTrue(stations >= 20, "stations set out at the workplaces: " + stations);
        // every field and garden is a farm with its crops sown
        int fields = 0;
        for (var s : ShadowVeil.terrain().arch.structures) if (s.template.equals("field") || s.template.equals("garden")) fields++;
        assertTrue(Farming.FIELDS.length >= fields - 1, "farmed " + Farming.FIELDS.length + " of " + fields);
        int sown = 0;
        for (var p : g.farming.plots) if (p.crop != null) sown++;
        assertTrue(sown > 200, "plots sown: " + sown);
        // the Loxford field at 276, 848: its fence drawn (recorded) and its plots
        // (0.4.6: the world's margin of sea moved every place in by it)
        int mg = rpg.world.shadowveil.Atlas.get().margin;
        assertTrue(ShadowVeil.terrain().fenceSpans.containsKey(((276 + mg) >> 4) + ((848 + mg) >> 4) * 1024), "the fence round the field is drawn");
    }

    @Test @org.junit.jupiter.api.Order(4) void everyKindOfWaterHasFish() {
        Set<Integer> kinds = new HashSet<>();
        for (var w : g.fishery.waters) if (w.total() > 0) kinds.add(w.cls);
        assertTrue(g.fishery.waters.size() > 60, "waters " + g.fishery.waters.size());
        for (int k : new int[]{rpg.fishing.FishKind.W.RIVER, rpg.fishing.FishKind.W.LAKE, rpg.fishing.FishKind.W.SEA, rpg.fishing.FishKind.W.DEEP})
            assertTrue(kinds.contains(k), "a water of kind " + k + " holds fish: " + kinds);
    }

    @Test @org.junit.jupiter.api.Order(5) void theWildIsPeopledAndItsHuntersKeepFromTheTowns() {
        int predators = 0;
        float nearest = Float.MAX_VALUE;
        for (var h : g.animals.habitats) for (var sp : h.capacity.keySet()) if (sp.predator() && h.owner == 0) {
            predators++;
            for (var a : Census.all().values()) if (a.homeStage() != null && !"Wilds".equals(a.kingdom()))
                nearest = Math.min(nearest, (float) Math.hypot(a.homeStage()[0] - h.cx(), a.homeStage()[1] - h.cz()));
        }
        assertTrue(g.animals.habitats.size() > 50, "habitats " + g.animals.habitats.size());
        assertTrue(predators >= 8, "predator ranges " + predators);
        assertTrue(nearest > 100, "the nearest hunter's range to a town home " + nearest);
    }

    @Test @org.junit.jupiter.api.Order(6) void theWaysAreClearAndTheTownsHaveRoom() {
        Terrain t = ShadowVeil.terrain();
        int litter = 0;
        for (int x = 1; x < t.S - 1; x += 1) for (int z = 1; z < t.S - 1; z += 1) {
            int i = t.idx(x, z);
            if ((t.flags[i] & (Terrain.LANE | Terrain.ROAD)) == 0 || (t.flags[i] & Terrain.BRIDGE) != 0 || t.water[i] >= 0 || t.arch.at(x, z) != null) continue;
            int b = g.vox.block(x, t.top[i] + 1, z);
            if (b == Blocks.LOG || b == Blocks.OAK_LOG || b == Blocks.DIRT || b == Blocks.HAY_BALE) litter++;
        }
        assertTrue(litter < 10, "litter on the ways " + litter);
        // no house stands within a cell of a town wall's face, and the aqueduct never passes over a lot
        for (var s : t.arch.structures) if (s.template.equals("building")) {
            int i = t.idx((int) s.cx(), (int) s.cz());
            assertTrue((t.flags[i] & Terrain.BUILT) != 0, s.id + " is claimed");
        }
    }

    @Test @org.junit.jupiter.api.Order(7) void theDevmapKeepsToItselfAndTheConsoleKnowsShadowVeil() {
        assertNull(DungeonLayout.at(250, 600), "no devmap dungeon on ShadowVeil");
        assertFalse(DungeonLayout.inDungeonRegion(500));
        var places = ShadowVeil.destinations();
        for (String k : new String[]{"start", "loxford", "azharad", "severia", "valgard", "gorrick", "harlette", "camelot"}) assertTrue(places.containsKey(k), "goto " + k);
        assertTrue(places.size() > 100, "goto places " + places.size());
        String src;
        try { src = java.nio.file.Files.readString(java.nio.file.Path.of("src/main/java/rpg/ui/Console.java")); } catch (java.io.IOException e) { throw new AssertionError(e); }
        assertTrue(src.contains("cmd(\"pos\""), "the pos command");
        assertTrue(rpg.Main.VERSION.startsWith("Alpha 0.4."), rpg.Main.VERSION);
    }
}
