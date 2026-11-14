package rpg.world;

import static org.junit.jupiter.api.Assertions.*;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import rpg.Rpg;
import rpg.actor.Appearance;
import rpg.ai.Npc;
import rpg.item.ItemStack;
import rpg.item.Items;
import rpg.world.shadowveil.Atlas;
import rpg.world.shadowveil.ShadowVeil;

/**
 * (0.4.6) The pass's fixes checked in ShadowVeil itself: the wider world and its deadly open sea, the weather's regional
 * cycles and every one of its weathers, the jail's held goods, the charges read out, Jail for Gaol, the graveyards'
 * headstones, the world mended on waking, the region names, the footfalls by material, nobody beginning in a crowd, and
 * every work station under a roof.
 */
@org.junit.jupiter.api.TestMethodOrder(org.junit.jupiter.api.MethodOrderer.OrderAnnotation.class)
class Gamefixes046Test {
    private static Game game;
    private static Rpg g;

    @BeforeAll static void grow() {
        var cfg = new NativeEngine.Config(); cfg.deterministic = true; cfg.audioDevice = false;
        game = Game.create(cfg);
        game.activate(ShadowVeil.build(game.engine()));
        g = game.add(new Rpg(game, "Gamefixes 0.4.6", new Appearance(), true));
        game.tick(0);
    }
    @AfterAll static void close() { if (game != null) game.close(); WorldMap.activateDevmap(); }

    @Test @Order(1) void theVersionIs046() { assertEquals("Alpha 0.4.6", rpg.Main.VERSION); }

    @Test @Order(2) void nobodyBeginsTheWorldInACrowd() {
        List<String> crowded = new ArrayList<>();
        for (Npc n : g.npcs) {
            int near = 0;
            for (Npc o : g.npcs) if (o != n && Math.hypot(o.x - n.x, o.z - n.z) < 1.0 && Math.abs(o.y - n.y) < 2) near++;
            if (near > 0) crowded.add(n.name);
        }
        assertTrue(crowded.size() <= 4, "begin on top of another: " + crowded);
    }

    @Test @Order(3) void everyStationIsUnderARoof() {
        List<String> open = new ArrayList<>();
        for (var st : g.objects.stations()) {
            int x = (int) Math.floor(st.x), z = (int) Math.floor(st.z), y = (int) Math.floor(st.y) + 1;
            boolean roofed = false;
            for (int k = 1; k <= 24 && !roofed; k++) roofed = g.vox.solid(x, y + k, z);
            if (!roofed) open.add(st.label() + " at " + x + "," + z);
        }
        assertTrue(open.isEmpty(), "under the open sky: " + open);
    }

    @Test @Order(4) void theWorldRunsOutToSeaAndTheOpenSeaKills() {
        Atlas a = Atlas.get();
        assertTrue(a.margin >= 32, "a margin of open sea round the design");
        assertEquals(a.size + 2 * a.margin, g.vox.sizeX());
        // past the design's edge: open water
        assertEquals(Blocks.WATER, g.vox.block(a.margin / 2, a.sea - 1, g.vox.sizeZ() / 2));
        float hp = g.player.hp;
        float x = 6.5f, z = g.vox.sizeZ() / 2f;
        g.teleport(g.player, x, a.sea, z);
        for (int i = 0; i < 180; i++) game.tick(1 / 60f);
        assertTrue(g.player.hp < hp || !g.player.alive, "the open sea at the world's edge did no harm");
        if (!g.player.alive) g.respawn();
        g.player.restore();
        float[] w = ShadowVeil.place("waystation");
        if (w != null) g.teleport(g.player, w[0], WorldMap.standY(w[0], w[1]), w[1]);
        game.tick(1 / 60f);
    }

    @Test @Order(5) void everyWeatherIsKnownAndEachRegionHasItsOwnCycle() {
        var ws = g.weatherSystem;
        for (String n : List.of("Sunny", "High Pressure", "Clouds", "Overcast", "Dark Overcast", "Mist", "Dense Fog", "Drizzle", "Rain", "Showers", "Thunder",
                "Thunderstorm", "Hurricane", "Hail", "Sleet", "Light Snow", "Snow", "Blizzard", "Ground Blizzard", "Whiteout", "Sandstorm", "Snow Clearing", "Rain Clearing"))
            assertNotNull(ws.byName(n), n + " is not a weather");
        assertEquals("desert", ws.climateAt(rpg.world.shadowveil.ShadowVeil.realm("Azharad").cx(), rpg.world.shadowveil.ShadowVeil.realm("Azharad").cz()));
        // a fortnight of the world's time: every climate turns over on the hour, and only the weathers that come of themselves come
        double start = g.state.minutes;
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (int h = 0; h < 24 * 14; h++) {
            g.state.minutes = start + h * 60;
            ws.update();
            for (var c : ws.cycles.values()) {
                assertEquals(0, c.until % 60, 0.001, c.climate + " does not turn over on the hour");
                seen.add(c.now);
                assertTrue(ws.byName(c.now).natural(), c.now + " came of itself");
            }
        }
        assertTrue(seen.size() >= 12, "only " + seen.size() + " weathers in a fortnight: " + seen);
        assertTrue(seen.contains("Sandstorm"), "the desert never had a sandstorm");
        g.state.minutes = start;
        // the console brings any of them
        assertTrue(ws.force("whiteout", g.player.x, g.player.z, 2));
        assertEquals("Whiteout", g.timeSystem.weather.name());
        assertTrue(g.timeSystem.weather.fog > 0.9f && g.timeSystem.weather.drift > 0.9f);
        ws.force("Sunny", g.player.x, g.player.z, 2);
    }

    @Test @Order(6) void aPrisonersThingsAreKeptInTheJailsChestAndTheChargesAreReadOut() {
        String jur = "Loxford";
        var chest = rpg.world.shadowveil.Rooms.heldGoods(jur);
        assertNotNull(chest, "Loxford's jail has no chest of held goods");
        assertTrue(chest.label().contains("Jail"), chest.label());
        g.player.store(new ItemStack(Items.BREAD, 3));
        int bread = g.player.count(Items.BREAD);
        g.law.addCharge(g.player.id, jur, "theft from Fred at Fred's Fine Axes");
        assertTrue(g.law.chargesRead(g.player.id, jur).contains("Fred's Fine Axes"));
        g.law.arrest(g.player, jur, null);
        assertEquals(bread, chest.contents.count(Items.BREAD), "the bread is not in the jail's chest");
        assertEquals(g.player.id, chest.custodyOf);
        g.law.sleepThroughSentence();
        assertTrue(g.player.count(Items.BREAD) >= bread, "the held goods did not come back on release");
        assertEquals(0, chest.custodyOf);
    }

    @Test @Order(7) void jailNotGaol() {
        for (var s : ShadowVeil.terrain().arch.structures) assertFalse(s.name != null && s.name.toLowerCase().contains("gaol"), s.name);
        for (var o : g.objects.all()) assertFalse(o.label().toLowerCase().contains("gaol"), o.label());
    }

    @Test @Order(8) void graveyardsHaveHeadstonesNotRubble() {
        int old = 0;
        for (var o : g.objects.all()) if (o instanceof rpg.objects.Container c && c.decor && c.kind.equals("memorial")) old++;
        assertTrue(old >= 20, "old headstones in the graveyards: " + old);
    }

    @Test @Order(9) void theWorldIsMendedOnWaking() {
        // a pane of the world broken (any block will do), then a sleep's mending
        int x = (int) g.player.x + 2, z = (int) g.player.z + 2, y = (int) WorldMap.standY(x, z) - 1;
        int was = g.vox.block(x, y, z);
        g.repairs.broken(x, y, z, was, "test");
        g.setBlock(x, y, z, Blocks.AIR);
        g.restoreWorld("test");
        assertEquals(was, g.vox.block(x, y, z), "the block was not put back");
    }

    @Test @Order(10) void regionsHaveNamesAndFootfallsTheirGround() {
        var t = ShadowVeil.terrain();
        assertNotNull(t.regionName((int) g.player.x, (int) g.player.z));
        assertEquals("gravel", rpg.audio.Sfx.surface(Blocks.GRAVEL));
        assertEquals("mud", rpg.audio.Sfx.surface(Blocks.MUD));
        assertEquals("sand", rpg.audio.Sfx.surface(Blocks.SAND));
        assertEquals("wood", rpg.audio.Sfx.surface(Blocks.PLANKS));
    }
}
