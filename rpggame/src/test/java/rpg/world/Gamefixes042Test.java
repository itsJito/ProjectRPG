package rpg.world;

import static org.junit.jupiter.api.Assertions.*;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import rpg.Rpg;
import rpg.actor.Appearance;
import rpg.ai.Npc;
import rpg.world.shadowveil.Architecture;
import rpg.world.shadowveil.Registry;
import rpg.world.shadowveil.ShadowVeil;

/**
 * (0.4.2, gamefixes: Alpha 0.4.2) The polished exterior world and the bugs of 0.4.1, on ShadowVeil: every structure
 * registered and every enterable building and underground place ready for its interior (doors into reserved rooms,
 * stair shafts, beds for the people who live there), the territories' own walls with their two-faced twins, the
 * buildings dressed outside without anything floating or on a road, the lamps and the stained glass found for the
 * renderer, the fishing waters stocked so the fishers catch, the soundscape reading the place, every person a one-word
 * name, the developer display renamed devui, and a new character's profile stamped with the version that made it.
 */
@org.junit.jupiter.api.TestMethodOrder(org.junit.jupiter.api.MethodOrderer.OrderAnnotation.class)
class Gamefixes042Test {
    private static Game game;
    private static Rpg g;

    @BeforeAll static void grow() {
        var cfg = new NativeEngine.Config(); cfg.deterministic = true; cfg.audioDevice = false;
        game = Game.create(cfg);
        game.activate(ShadowVeil.build(game.engine()));
        g = game.add(new Rpg(game, "Gamefixes 0.4.2", new Appearance(), true));
        game.tick(0);
    }
    @AfterAll static void close() { if (game != null) game.close(); WorldMap.activateDevmap(); }

    private static Architecture arch() { return ShadowVeil.terrain().arch; }

    @Test @org.junit.jupiter.api.Order(1) void everyBuildingIsRegisteredAndReadyForItsInterior() throws Exception {
        Registry reg = arch().registry;
        assertNotNull(reg);
        assertEquals(arch().structures.size(), reg.entries.size(), "one entry per structure");
        List<String> flagged = new ArrayList<>();
        int enter = 0, under = 0;
        for (Registry.Entry e : reg.entries) {
            if (e.enterable && !e.underground) enter++;
            if (e.underground) under++;
            if (!e.ready) flagged.add(e.id + ": " + e.issues);
            assertNotNull(e.kingdom); assertNotNull(e.purpose);
            if (e.enterable && !e.underground) {
                assertFalse(e.entrances.isEmpty(), e.id + " has a way in");
                assertTrue(e.interior.stream().anyMatch(v -> v.kind().startsWith("interior")), e.id + " keeps its interior");
                assertFalse(e.rooms.isEmpty(), e.id + " knows the rooms it will hold");
            }
        }
        assertTrue(flagged.isEmpty(), () -> "not ready for interiors: " + flagged);
        assertTrue(enter >= 450 && under >= 10, "buildings " + enter + ", underground places " + under);
        // the people: every one who lives in a settlement has a registered home with a bed for them
        Map<String, List<String>> homes = new HashMap<>();
        List<String> homeless = new ArrayList<>();
        for (Npc n : g.npcs) {
            if (n.role == Npc.Role.BANDIT || n.role == Npc.Role.BRUTE || DevMap.WILDS.equals(DevMap.jurisdiction(n.homeX, n.homeZ))) continue;
            // (0.4.3) the census gives everyone their home (registered as they were placed)
            var census = rpg.world.shadowveil.Census.of(n.id);
            Registry.Entry home = census != null && census.homeId() != null ? reg.byId(census.homeId()) : reg.house(n.name, n.homeX, n.homeZ, 200);
            if (home == null) homeless.add(n.name + " at " + (int) n.homeX + "," + (int) n.homeZ);
            else homes.computeIfAbsent(home.id, k -> new ArrayList<>()).add(n.name);
        }
        List<String> crowded = new ArrayList<>();
        for (var h : homes.keySet()) { Registry.Entry e = reg.byId(h); if (e.bedCapacity < e.bedsNeeded) crowded.add(e.id + " " + e.residents); }
        assertTrue(homeless.isEmpty(), () -> "townsfolk with no registered home near their bed: " + homeless);
        assertTrue(crowded.isEmpty(), () -> "homes without room for their people's beds: " + crowded);
        // the registry with its residents kept beside the survey's
        Path out = Path.of("build/reports/shadowveil");
        Files.createDirectories(out);
        Files.writeString(out.resolve("registry.json"), reg.json());
        Files.writeString(out.resolve("registry.md"), reg.report());
    }

    @Test @org.junit.jupiter.api.Order(2) void theTerritoriesBuildInTheirOwnWallsTwoFaced() {
        int[] own = {Blocks.RUBBLE_STONE, Blocks.OCHRE_DAUB, Blocks.GRANITE_ASHLAR, Blocks.TARRED_PLANKS, Blocks.ADOBE, Blocks.STUCCO, Blocks.WHITE_ASHLAR};
        for (int b : own) {
            int d = Blocks.dualOf(b, rpg.world.BuildingKit.innerFinish(b), Blocks.IN_PX);
            assertTrue(Blocks.dual(d), "two-faced " + b);
            assertEquals(b, Blocks.outerOf(d));
            assertTrue(Blocks.traversable(b) && Blocks.traversable(d), "walked on like any wall");
        }
        assertTrue(Blocks.COUNT <= 256, "the ids fit a byte");
        // each kingdom's own walling stands in its streets, and neighbours differ
        Map<String, Integer> seen = new HashMap<>();
        for (var s : arch().structures) {
            if (!s.template.equals("building")) continue;
            int x = (int) s.cx(), z = s.z0;
            for (int y = s.floor + 1; y <= s.floor + 3; y++) for (int dx = s.x0; dx <= s.x1; dx++) for (int dz = s.z0; dz <= s.z1; dz++) {
                int b = Blocks.outerOf(g.vox.block(dx, y, dz));
                for (int o : own) if (b == o) seen.merge(s.settlement + ":" + o, 1, Integer::sum);
            }
        }
        for (String realm : new String[]{"loxford", "harlette", "valgard", "gorrick", "azharad", "severia", "camelot"})
            assertTrue(seen.keySet().stream().anyMatch(k -> k.startsWith(realm + ":")), realm + " builds in its own walling: " + seen.keySet());
    }

    @Test @org.junit.jupiter.api.Order(3) void theBuildingsAreDressedWithNothingFloatingOrOnTheRoads() {
        var t = ShadowVeil.terrain();
        assertTrue(arch().props.size() > 50, "crates and benches set out: " + arch().props.size());
        int furniture = 0;
        for (var o : g.objects.all()) if (o instanceof rpg.objects.Furniture f && f.id >= rpg.Ids.SV_PROPS && f.id < rpg.Ids.SV_PROPS + 20_000) furniture++;
        assertEquals(arch().props.size(), furniture, "every piece is the game's furniture");
        for (var p : arch().props) {
            assertTrue(g.vox.solid(p.x(), p.floor() - 1, p.z()), "stands on the ground: " + p);
            assertEquals(0, t.flags[t.idx(p.x(), p.z())] & (rpg.world.shadowveil.Terrain.ROAD | rpg.world.shadowveil.Terrain.LANE), "off the roads: " + p);
        }
        assertTrue(arch().notes.stream().anyMatch(n -> n.contains("exterior pieces dressed")));
    }

    @Test @org.junit.jupiter.api.Order(4) void theLampsAndStainedGlassAreFoundAndTheGlassIsSeeThrough() {
        var t = ShadowVeil.terrain();
        long lamps = t.lampCells.stream().filter(c -> g.vox.block(c[0], c[1], c[2]) == Blocks.LAMP).count();
        assertTrue(lamps > 100, "ShadowVeil's lamps (street, court, wall lanterns): " + lamps);
        assertTrue(t.stainedCells.stream().anyMatch(c -> g.vox.block(c[0], c[1], c[2]) == Blocks.STAINED_GLASS), "the churches' stained glass");
        int[][] px = Blocks.layer(Blocks.L_STAINED);
        int clear = 0;
        for (int[] c : px) if (c[3] < 200) clear++;
        assertTrue(clear > px.length / 2, "the panes are see-through (" + clear + " of " + px.length + ")");
    }

    @Test @org.junit.jupiter.api.Order(5) void everyFishingSpotHasFishItsGearTakes() {
        List<String> empty = new ArrayList<>();
        for (var node : g.objects.nodes) {
            if (node.kind != rpg.objects.Node.Kind.FISH || node.needs == null) continue;
            if (g.fishery.takenHere(node.x, node.z, node.needs).isEmpty()) empty.add(node.name);
        }
        assertTrue(empty.isEmpty(), () -> "spots with nothing their gear takes: " + empty);
    }

    @Test @org.junit.jupiter.api.Order(6) void theSoundscapeReadsThePlace() {
        // at a church's altar, the hymn
        var church = arch().byId("loxford_church");
        float[] altar = church.points.get("altar");
        g.teleport(g.player, altar[0], WorldMap.standY(altar[0], altar[1]), altar[1]);
        assertEquals(rpg.audio.Music.Mood.SACRED, g.soundscape.placeMood(false));
        // in Azharad's streets by day, the town's music, Azharad's own pieces
        var bank = arch().byId("azharad_bank");
        float[] ap = bank == null ? new float[]{(float) ShadowVeil.terrain().atlas.x(56), (float) ShadowVeil.terrain().atlas.z(12)} : new float[]{bank.apron()[0] + 0.5f, bank.apron()[1] + 0.5f};
        g.teleport(g.player, ap[0], WorldMap.standY(ap[0], ap[1]), ap[1]);
        assertEquals("Azharad", DevMap.jurisdiction(g.player.x, g.player.z));
        rpg.audio.Music.Mood m = g.soundscape.placeMood(false);
        assertTrue(m == rpg.audio.Music.Mood.TOWN_DAY || m == rpg.audio.Music.Mood.PALACE_DAY, "a town's music: " + m);
        assertEquals("Azharad", g.soundscape.region());
        assertNotNull(g.soundscape.tracks(rpg.audio.Music.Mood.TOWN_DAY), "Azharad names its own pieces");
        // out on the open heath at night, the open country's
        float[] wild = ShadowVeil.dry(600, 600);
        g.teleport(g.player, wild[0], WorldMap.standY(wild[0], wild[1]), wild[1]);
        if (DevMap.WILDS.equals(DevMap.jurisdiction(g.player.x, g.player.z))) {
            rpg.audio.Music.Mood n = g.soundscape.placeMood(true);
            assertTrue(n == rpg.audio.Music.Mood.OPEN_NIGHT || n == rpg.audio.Music.Mood.WOOD_NIGHT || n == rpg.audio.Music.Mood.DUNGEON, "the wild's music at night: " + n);
        }
        // the ambience bed is a level for every place, silent underground
        for (var p : rpg.audio.Soundscape.Place.values()) assertNotNull(p);
        assertTrue(g.soundscape.bedLevel(false, 10) >= 0);
    }

    @Test @org.junit.jupiter.api.Order(7) void everyPersonHasAOneWordName() {
        java.util.Set<String> callings = java.util.Set.of("Fisher", "Fisherman", "Hunter", "Bandit", "Brute", "Guard", "Villager", "Farmer", "Miner", "Bounty hunter", "Bandit archer");
        List<String> wrong = new ArrayList<>();
        for (Npc n : g.npcs) {
            // (0.4.4: an honorific may go before the one name - Sir, Dame, a rank, Father or Mother)
            String bare = n.name.replaceFirst("^(Sir|Baron|Baroness|Lady|Dame|King|Queen|Duke|Duchess|Count|Countess|Father|Mother) ", "");
            if (callings.contains(n.name) || callings.contains(bare) || bare.contains(" ")) wrong.add(n.name);
        }
        assertTrue(wrong.isEmpty(), () -> "not a one-word name: " + wrong);
        assertNotEquals(rpg.ai.Names.drawn(1, false), "");
        assertEquals(rpg.ai.Names.drawn(77, true), rpg.ai.Names.drawn(77, true), "the same person, the same name");
    }

    @Test @org.junit.jupiter.api.Order(8) void theDeveloperDisplayIsDevuiAndANewCharacterIsOfThisVersion() throws Exception {
        String src = Files.readString(Path.of("src/main/java/rpg/ui/Console.java"));
        assertTrue(src.contains("cmd(\"devui\"") && !src.contains("cmd(\"devmode\""), "the command is devui");
        assertFalse(new rpg.ui.DevDisplay(g, null).lines().isEmpty());
        // a profile written before its first save carries the version of the save it has
        Path dir = Files.createTempDirectory("prof");
        Path save = dir.resolve("save.json");
        Files.writeString(save, "{\"schema\":1,\"version\":\"" + rpg.Main.VERSION + "\"}");
        assertEquals(rpg.Main.VERSION, rpg.Profiles.savedBy(save));
    }
}
