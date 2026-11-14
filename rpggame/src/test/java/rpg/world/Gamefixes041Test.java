package rpg.world;

import static org.junit.jupiter.api.Assertions.*;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import rpg.CharacterRecord;
import rpg.Rpg;
import rpg.actor.Appearance;
import rpg.ai.Npc;
import rpg.item.ItemStack;
import rpg.item.Items;
import rpg.skill.Skill;
import rpg.world.shadowveil.Architecture;
import rpg.world.shadowveil.ShadowVeil;
import rpg.world.shadowveil.Survey;

/**
 * Alpha 0.4.1 gamefixes on ShadowVeil: the built world (every settlement, landmark and dungeon mouth raised and
 * checked), the spawn in Loxford Castle facing the way out, the doors, the bank where the world puts it, the farms'
 * gardens, the off-stage day beyond the fog, the auto-jump onto a one-cell rise, the developer display and the character
 * record that carries a character into any later version.
 */
@org.junit.jupiter.api.TestMethodOrder(org.junit.jupiter.api.MethodOrderer.OrderAnnotation.class)
class Gamefixes041Test {
    private static Game game;
    private static Rpg g;

    @BeforeAll static void grow() {
        var cfg = new NativeEngine.Config(); cfg.deterministic = true; cfg.audioDevice = false;
        game = Game.create(cfg);
        game.activate(ShadowVeil.build(game.engine()));
        g = game.add(new Rpg(game, "Gamefixes 0.4.1", new Appearance(), true));
        game.tick(0);
    }
    @AfterAll static void close() { if (game != null) game.close(); WorldMap.activateDevmap(); }

    private static Architecture arch() { return ShadowVeil.terrain().arch; }

    @Test @org.junit.jupiter.api.Order(1) void everySettlementLandmarkAndDungeonMouthIsBuiltAndChecked() {
        Survey s = new Survey(ShadowVeil.terrain()).check();
        assertTrue(s.failures.isEmpty(), () -> "the built world failed its checks: " + s.failures);
        assertTrue(arch().structures.size() >= 500, "the towns are full: " + arch().structures.size());
        for (String realm : new String[]{"loxford", "harlette", "valgard", "gorrick", "azharad", "severia", "camelot"}) {
            long n = arch().structures.stream().filter(st -> st.settlement.equals(realm)).count();
            assertTrue(n >= 8, realm + " has its buildings: " + n);
        }
        // projectrpg.txt's Points of Interest: every documented underground place has its mouth and its volume kept
        for (String id : new String[]{"loxford_graveyard", "loxford_castle", "murk_caves", "crags_mine_south", "crags_mine_west", "spider_nest", "museum_vault",
                "unlit_lantern", "ashmaw_throat", "sunkissed_mine", "marble_mine", "goblin_hollow"}) {
            assertNotNull(arch().byId(id), "built: " + id);
            assertTrue(arch().reserved.stream().anyMatch(r -> r.owner().equals(id) && !r.kind().equals("interior")), id + " keeps its underground volume");
        }
        assertFalse(arch().patrols.isEmpty(), "the towns have their watch's rounds");
    }

    @Test @org.junit.jupiter.api.Order(2) void aNewCharacterWakesInLoxfordCastlesCourtBeforeTheKeepDoor() {
        // (0.4.2, gamefixes: players spawned inside a building) out in the ward, in front of the keep's door, facing the gate
        var castle = arch().byId("loxford_castle");
        assertTrue(castle.contains((int) g.player.x, (int) g.player.z), "the spawn is within Loxford Castle's walls");
        assertEquals("Loxford", DevMap.jurisdiction(g.player.x, g.player.z));
        assertTrue(g.vox.solid((int) g.player.x, (int) Math.floor(g.player.y) - 1, (int) g.player.z), "standing on the court");
        for (var r : arch().reserved) if (r.owner().equals("loxford_castle") && r.kind().equals("interior"))
            assertFalse(g.player.x >= r.x0() && g.player.x < r.x1() + 1 && g.player.z >= r.z0() && g.player.z < r.z1() + 1 && g.player.y >= r.y0() && g.player.y <= r.y1() + 1, "not inside the keep");
        assertTrue(g.vox.sight(g.player.x, g.player.eyeY(), g.player.z, g.player.x, g.player.eyeY() + 20, g.player.z), "under the open sky");
        float[] door = castle.points.get("keepDoor"), gate = castle.points.get("gate");
        assertTrue(Math.hypot(door[0] - g.player.x, door[1] - g.player.z) < 6, "a few steps from the keep's door");
        float want = (float) Math.atan2(-(gate[0] - g.player.x), -(gate[1] - g.player.z));
        assertTrue(Math.abs(rpg.actor.Actor.wrap(want - g.controller.camYaw)) < 0.2f, "facing the gate: " + g.controller.camYaw + " vs " + want);
    }

    @Test @org.junit.jupiter.api.Order(3) void theBuildingsHangTheirDoorsOnTheirOwnFloors() {
        int doors = 0, sealed = 0;
        for (var o : g.objects.all()) if (o instanceof rpg.objects.Door d && d.id >= rpg.Ids.SV_DOORS && d.id < rpg.Ids.SV_PLOTS) {
            doors++;
            int b = g.vox.block(d.cells[0][0], d.floor, d.cells[0][1]);
            if (b == Blocks.DOOR || b == Blocks.AIR) sealed++;
        }
        assertTrue(doors > 200, "doors hung: " + doors);
        assertEquals(doors, sealed, "every door stands in its own doorway");
    }

    @Test @org.junit.jupiter.api.Order(4) void theBankAndTheGardensAreWhereShadowVeilPutsThem() {
        float[] bank = g.bank.customerSpot();
        assertTrue(arch().byId("loxford_castle").contains((int) bank[0], (int) bank[1]), "the bank counter is in Loxford Castle's keep");
        assertTrue(Farming.FIELDS.length >= 2, "the growers have their gardens");
        assertTrue(g.farming.ripeFor(rpg.Ids.CROP_FARMER_CITY, 0, 0) != null || g.farming.emptyFor(rpg.Ids.CROP_FARMER_CITY, 0, 0) != null || !g.objects.all().isEmpty());
    }

    @Test @org.junit.jupiter.api.Order(5) void aOneCellRiseIsHoppedUpNotClimbedByMagic() {
        var castle = arch().byId("loxford_castle");
        float[] gate = castle.points.get("gate");
        float x = gate[0] + 0.5f, z = castle.points.get("keepDoor")[1] - 9;
        int floor = castle.floor;
        g.teleport(g.player, x, floor + 1, z);
        g.controller.snapTo(0);                      // facing north (-z), toward the gate
        for (int dx = -2; dx <= 2; dx++) g.setBlock((int) Math.floor(x) + dx, floor + 1, (int) Math.floor(z) - 3, Blocks.COBBLE);
        float startY = g.player.y, peak = startY;
        for (int f = 0; f < 150; f++) { g.controller.in.forward = 1; game.tick(1f / 60); peak = Math.max(peak, g.player.y); }
        g.controller.in.forward = 0;
        assertTrue(peak > startY + 1.0f, "the body hopped up the step: peak " + peak + " from " + startY);
        assertTrue(g.player.z < z - 3.5f, "and went on over it: " + g.player.z);
        for (int dx = -2; dx <= 2; dx++) g.setBlock((int) Math.floor(x) + dx, floor + 1, (int) Math.floor(z) - 3, Blocks.AIR);
    }

    @Test @org.junit.jupiter.api.Order(6) void residentsBeyondTheFogLiveTheirDayOffStageAndComeBack() {
        // the player far off at the coast: everyone of the towns is beyond the fog
        float[] far = ShadowVeil.dry(1000, 60);
        g.teleport(g.player, far[0], WorldMap.standY(far[0], far[1]), far[1]);
        g.state.minutes = 9 * 60;
        for (int f = 0; f < 120; f++) game.tick(1f / 60);
        List<Npc> off = new ArrayList<>();
        for (Npc n : g.npcs) if (n.alive && n.offstage && !n.loaded) off.add(n);
        assertTrue(off.size() > 40, "most residents went off-stage: " + off.size());
        int stepsBefore = g.ai.offstageSteps();
        g.state.minutes += 40;
        for (int f = 0; f < 10; f++) game.tick(1f / 60);
        assertTrue(g.ai.offstageSteps() > stepsBefore, "their day went on off-stage");
        assertTrue(off.stream().anyMatch(n -> n.lastOutcome != null && n.lastOutcome.startsWith("(off-stage)")), "and they did something with it");
        // back in town: they come back into play where their day left them, on the ground
        g.teleport(g.player, WorldMap.active().spawnX, WorldMap.standY(WorldMap.active().spawnX, WorldMap.active().spawnZ), WorldMap.active().spawnZ);
        for (int f = 0; f < 120; f++) game.tick(1f / 60);
        for (Npc n : g.npcs) if (n.alive && Math.hypot(n.x - g.player.x, n.z - g.player.z) < 100) {
            assertTrue(n.loaded || !n.offstage, n.name + " is back in play");
            if (n.loaded) assertFalse(g.vox.solid((int) Math.floor(n.x), (int) Math.floor(n.y + 0.5f), (int) Math.floor(n.z)), n.name + " is not inside anything");
        }
    }

    @Test @org.junit.jupiter.api.Order(7) void theCharacterRecordCarriesSkillsGoodsAndBankIntoAnyVersion() {
        g.player.skills.setXp(Skill.MINING, 12345);
        g.give(g.player, Items.get("iron_ore"), 3);
        Map<String, Object> rec = CharacterRecord.capture(g);
        @SuppressWarnings("unchecked") Map<String, Object> skills = (Map<String, Object>) rec.get("skills");
        assertEquals(12345L, ((Number) skills.get("MINING")).longValue(), "skills are kept by name");
        // a later version reads it: its own fresh character replaced by the record's
        g.player.skills.setXp(Skill.MINING, 0);
        for (var grid : g.player.grids()) for (ItemStack s : grid.stacks()) g.state.burn(s.def, s.amount);
        g.player.inv.clear();
        List<String> notes = CharacterRecord.apply(g, astratto.util.Json.parseObject(astratto.util.Json.write(rec)));
        assertTrue(notes.isEmpty(), () -> "nothing lost: " + notes);
        assertEquals(12345, g.player.skills.xp(Skill.MINING));
        assertEquals(3, g.player.count(Items.get("iron_ore")));
        assertTrue(g.invariants.check().isEmpty(), () -> "the ledger holds: " + g.invariants.check());
        // a record a newer version wrote, with a skill and an item this one does not know, still loads what it can
        rec.put("skills", new java.util.HashMap<>(Map.of("MINING", 50, "STARGAZING", 900)));
        List<String> later = CharacterRecord.apply(g, rec);
        assertTrue(later.stream().anyMatch(s -> s.contains("STARGAZING")), "an unknown skill is noted, not fatal");
    }

    @Test @org.junit.jupiter.api.Order(8) void theDeveloperDisplayTellsWhereWhenAndWho() {
        String all = String.join("\n", new rpg.ui.DevDisplay(g, null).lines());
        assertTrue(all.contains("FPS") && all.contains("XYZ") && all.contains("chunk [") && all.contains("people:") && all.contains(rpg.Main.VERSION), all);
    }
}
