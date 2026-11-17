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
import rpg.objects.Furniture;
import rpg.world.shadowveil.ShadowVeil;

/**
 * (0.4.7) The pass's fixes checked in ShadowVeil itself: the shops named for their buildings, the guild halls, the
 * dungeons cut under the map and their shafts whole, lava a hazard, the birds that fly, the interior's two-faced walls,
 * chandeliers only where there is head room, a chair drawn out, sat in and pushed home, King Arthur in his own gear and
 * everyone able to wear what they wear.
 */
@org.junit.jupiter.api.TestMethodOrder(org.junit.jupiter.api.MethodOrderer.OrderAnnotation.class)
class Gamefixes047Test {
    private static Game game;
    private static Rpg g;

    @BeforeAll static void grow() {
        var cfg = new NativeEngine.Config(); cfg.deterministic = true; cfg.audioDevice = false;
        game = Game.create(cfg);
        game.activate(ShadowVeil.build(game.engine()));
        g = game.add(new Rpg(game, "Gamefixes 0.4.7", new Appearance(), true));
        game.tick(0);
    }
    @AfterAll static void close() { if (game != null) game.close(); WorldMap.activateDevmap(); }

    @Test @Order(1) void theVersionIs047() { assertTrue(rpg.Main.VERSION.compareTo("Alpha 0.4.7") >= 0); }

    @Test @Order(2) void noShopIsCalledStoreA() {
        for (var s : new rpg.econ.Economy.Shop[]{g.econ.shopA, g.econ.shopB}) {
            assertNotNull(s.name);
            assertFalse(s.name.matches("(?i)store ?[a-z]\\b.*"), "a shop still called " + s.name);
        }
    }

    @Test @Order(3) void theGuildHallsStand() {
        assertNotNull(g.guilds);
        assertTrue(g.guilds.seats.size() >= 7, "guild seats: " + g.guilds.seats);
        java.util.Set<rpg.econ.Guilds.Kind> kinds = java.util.EnumSet.noneOf(rpg.econ.Guilds.Kind.class);
        for (var s : g.guilds.seats) { kinds.add(s.guild()); assertTrue(g.actor(s.keeper()) instanceof Npc, s.name() + " has no master"); }
        assertEquals(rpg.econ.Guilds.Kind.values().length, kinds.size(), "every guild has a hall: " + kinds);
    }

    @Test @Order(4) void theDungeonsGoDownUnderTheMap() {
        var levels = DungeonLayout.underground();
        assertFalse(levels.isEmpty());
        int deeper = 0;
        for (var l : levels) {
            int ex = l.worldX(l.entryX), ez = l.worldZ(l.entryZ);
            assertTrue(g.vox.block(ex, l.floorY, ez) == Blocks.AIR && g.vox.solid(ex, l.floorY - 1, ez), l.name + "'s entry is not open floor");
            assertTrue(l.floorY < ShadowVeil.terrain().top[ShadowVeil.terrain().idx(ex, ez)], l.name + " is not under the ground");
            if (l.depth > 1) deeper++;
        }
        assertTrue(deeper > 0, "no dungeon goes a level deeper");
        for (int[] sh : rpg.dungeon.Underground.SHAFTS)
            for (int y = sh[3]; y < sh[2]; y++) assertEquals(Blocks.LADDER, g.vox.block(sh[0], y, sh[1]), "a shaft's ladder is broken at " + sh[0] + "," + y + "," + sh[1]);
    }

    @Test @Order(5) void lavaIsAHazard() {
        assertTrue(rpg.Tuning.f("world.lavaHurt", 1) > 0, "lava does not hurt");
        assertFalse(ShadowVeil.terrain().lavaCells.isEmpty(), "Ashmaw has no lava");
    }

    @Test @Order(6) void theBirdsFly() {
        for (var s : new rpg.animal.Species[]{rpg.animal.Species.CROW, rpg.animal.Species.PIGEON, rpg.animal.Species.VULTURE})
            assertTrue(s.flies, s + " does not fly");
    }

    @Test @Order(7) void interiorsHaveTwoFacedWalls() {
        assertTrue(Blocks.AUTO_PAIRS.length >= 6, "six interior materials");
        for (int[] p : Blocks.AUTO_PAIRS) assertEquals(p[0], Blocks.autoOf(p[1]));
    }

    @Test @Order(8) void chandeliersOnlyWhereThereIsHeadRoom() {
        int need = 4;   // shadowveil.json interiors.chandelierMinClear
        List<String> low = new ArrayList<>();
        for (Furniture f : g.objects.furniture) {
            if (f.kind != Furniture.Kind.CHANDELIER) continue;
            int x = (int) Math.floor(f.x), z = (int) Math.floor(f.z), clear = 0;
            while (clear < 12 && !g.vox.solid(x, f.floor + clear, z)) clear++;
            if (clear < need) low.add(f.id + " at " + x + "," + f.floor + "," + z + " (" + clear + ")");
        }
        assertTrue(low.isEmpty(), "chandeliers in low rooms: " + low);
    }

    @Test @Order(9) void aChairIsDrawnOutSatInAndPushedHome() {
        Furniture chair = null;
        for (Furniture f : g.objects.furniture) if (f.kind == Furniture.Kind.CHAIR && f.exists() && g.ai.brain.seating.approach(f.x, f.floor, f.z, f.yaw()) != null) { chair = f; break; }
        assertNotNull(chair, "no chair to sit on");
        Npc who = null;
        for (Npc n : g.npcs) if (n.alive && n.role != Npc.Role.GUARD && (who == null || n.distTo(chair.x, chair.z) < who.distTo(chair.x, chair.z))) who = n;
        if (!who.loaded) { g.ai.reload(who); who.offstage = false; who.indoors = false; }
        float[] fr = chair.front();
        g.teleport(who, chair.x - fr[0] * 1.4f, chair.floor, chair.z - fr[1] * 1.4f);
        boolean sat = false; float most = 0;
        for (int i = 0; i < 60 * 8 && !(sat && chair.pull < 0.5f); i++) {
            who.nextThink = g.time + 1e6; who.goal = rpg.ai.Brain.Goal.HELD;
            sat = g.ai.brain.seating.sitOn(who, chair);
            most = Math.max(most, chair.pull);
            game.tick(1 / 60f);
        }
        assertTrue(sat, who.name + " never sat: " + who.lastOutcome);
        assertTrue(most > 0.9f, "the chair was never drawn out");
        assertTrue(chair.pull < 0.5f, "the chair did not go in with its sitter");
        g.ai.brain.seating.stand(who);
        for (int i = 0; i < 60 * 3; i++) game.tick(1 / 60f);
        assertEquals(0, chair.pull, 1e-3, "the chair was not pushed home");
        assertEquals(0, chair.sitter, "the chair is still held");
    }

    @Test @Order(10) void kingArthurIsHimself() {
        Npc arthur = null;
        for (Npc n : g.npcs) if (n.name.contains("Arthur")) arthur = n;
        assertNotNull(arthur, "no King Arthur");
        var head = arthur.eq.get(rpg.item.ItemDef.Slot.HEAD);
        assertNotNull(head, "Arthur wears no crown");
        assertEquals("crown_of_camelot", head.def.id);
        assertTrue(arthur.skills.combatLevel() >= 90, "Arthur's combat level " + arthur.skills.combatLevel());
    }

    @Test @Order(12) void everyWeatherIsHeard() throws Exception {
        java.util.Map<String, Object> audio;
        try (var in = Gamefixes047Test.class.getResourceAsStream("/data/audio.json")) { audio = astratto.util.Json.parseObject(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)); }
        @SuppressWarnings("unchecked") var beds = (java.util.Map<String, Object>) ((java.util.Map<String, Object>) audio.get("weather")).get("beds");
        for (String k : new String[]{"rain_light", "rain_heavy", "wind_soft", "wind_gale", "hail", "blizzard", "sand"}) {
            assertTrue(beds.containsKey(k), "no weather bed " + k);
            @SuppressWarnings("unchecked") String file = String.valueOf(((java.util.Map<String, Object>) beds.get(k)).get("file"));
            assertNotNull(Gamefixes047Test.class.getResource("/sounds/" + file), "no sound " + file);
            assertTrue(decoded(file) > 44100 * 10, file + " is not a loop of ten seconds or more");
        }
        // the birds' recorded calls
        for (String f : new String[]{"animal/crow_caw_1.ogg", "animal/crow_caw_2.ogg", "animal/crow_caw_3.ogg", "animal/pigeon_coo_1.ogg", "animal/pigeon_coo_2.ogg", "animal/vulture_hiss_1.ogg", "animal/vulture_hiss_2.ogg"})
            assertTrue(decoded(f) > 44100 / 2, f + " did not decode");
        assertNotNull(Furniture.Kind.SCONCE.light(), "a sconce gives light");
    }

    /** Samples a sound decodes to at the mixer's rate (the game's own decoder). */
    private static int decoded(String file) throws Exception {
        try (var in = Gamefixes047Test.class.getResourceAsStream("/sounds/" + file)) { return astratto.audio.Codecs.decode(in.readAllBytes(), file, 0).mono().length; }
    }

    @Test @Order(11) void everyoneCanWearWhatTheyWear() {
        List<String> unfit = new ArrayList<>();
        for (Npc n : g.npcs) for (rpg.item.ItemDef.Slot s : rpg.item.ItemDef.Slot.values()) {
            var d = n.eq.def(s);
            if (d == null) continue;
            for (var r : d.requirements().entrySet()) if (n.skills.level(r.getKey()) < r.getValue()) unfit.add(n.name + " " + d.id + " " + r.getKey());
        }
        assertTrue(unfit.isEmpty(), "wear what they cannot: " + unfit.subList(0, Math.min(12, unfit.size())));
    }
}
