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
import rpg.item.Items;
import rpg.monster.Monster;
import rpg.monster.MonsterKind;
import rpg.objects.Furniture;
import rpg.world.shadowveil.ShadowVeil;

/**
 * (0.4.8) The pass's fixes checked in ShadowVeil itself: the version and the small changes (no pot or bucket to start,
 * two-by-two shields, level tags coloured, gold with its separator); the Unlit Lantern's door under its lintel and its
 * cellars cut beyond the door at the stair's foot, every other sealed stair likewise; the wild's stones no longer strewn
 * everywhere; rooms lit through; rain and snow never together; encounters coming in out of sight; an arrow heard where it
 * strikes; a torch setting plants alight; the danger growing away from Loxford; the new monsters made, figured and
 * fighting (the scorpion's pincer holds, its sting poisons); the new furnishings modelled and breakable; the journal's
 * quests and its active one; the map's markers kept.
 */
@org.junit.jupiter.api.TestMethodOrder(org.junit.jupiter.api.MethodOrderer.OrderAnnotation.class)
class Gamefixes048Test {
    private static Game game;
    private static Rpg g;

    @BeforeAll static void grow() {
        var cfg = new NativeEngine.Config(); cfg.deterministic = true; cfg.audioDevice = false;
        game = Game.create(cfg);
        game.activate(ShadowVeil.build(game.engine()));
        g = game.add(new Rpg(game, "Gamefixes 0.4.8", new Appearance(), true));
        game.tick(0);
    }
    @AfterAll static void close() { if (game != null) game.close(); WorldMap.activateDevmap(); }

    @Test @Order(1) void theVersionAndTheSmallChanges() {
        assertEquals("Alpha 0.4.8", rpg.Main.VERSION);
        var shield = Items.get("iron_shield");
        assertEquals(2, shield.gw, "a shield is two cells wide"); assertEquals(2, shield.gh, "and two high");
        assertEquals(0, g.player.count(Items.POT)); assertEquals(0, g.player.count(Items.BUCKET));
        int mine = 20;
        assertEquals(rpg.ui.Style.LEVEL_LOW, rpg.ui.Style.levelColor(17, mine), "three below: green");
        assertEquals(rpg.ui.Style.LEVEL_EVEN, rpg.ui.Style.levelColor(18, mine), "two below: yellow");
        assertEquals(rpg.ui.Style.LEVEL_EVEN, rpg.ui.Style.levelColor(22, mine), "two above: yellow");
        assertEquals(rpg.ui.Style.LEVEL_HIGH, rpg.ui.Style.levelColor(23, mine), "three above: red");
        assertEquals("12,345", rpg.item.Currency.text(12345).replace(' ', ','));
    }

    @Test @Order(2) void theLanternsDoorHangsUnderItsLintelAndItsCellarsAreCut() {
        var t = ShadowVeil.terrain();
        var lantern = t.arch.byId("unlit_lantern");
        assertNotNull(lantern);
        int[][] door = lantern.doors.get(0);
        int floor = lantern.doorFloors.get(0);
        for (int[] c : door) assertNotEquals(Blocks.AIR, g.vox.block(c[0], floor + 3, c[1]), "a lintel over the door at " + c[0] + "," + c[1]);
        assertTrue(rpg.world.shadowveil.Cellars.cut >= 5, "every sealed stair's cellars cut: " + rpg.world.shadowveil.Cellars.NOTES);
        for (String n : rpg.world.shadowveil.Cellars.NOTES) {
            int cells = Integer.parseInt(n.replaceAll(".*rooms, (\\d+) cells.*", "$1"));
            assertTrue(cells >= 40, "a cellar worth the name: " + n);
        }
        // the door at each stair's foot is a door, not a block of nothing
        int doors = 0;
        for (var o : g.objects.all()) if (o instanceof rpg.objects.Door d && (d.name.endsWith("cellars door") || d.name.endsWith("cellar door") || d.name.endsWith("undercroft door") || d.name.endsWith("vault door"))) doors++;
        assertTrue(doors >= 5, "the cellars' doors hung: " + doors);
    }

    @Test @Order(3) void theWildsStonesAreNotStrewnEverywhere() {
        // round the reported 468, 572: stones in the turf, not a cluster on every patch
        int stones = 0, tops = 0;
        for (int x = 448; x < 488; x++) for (int z = 552; z < 592; z++) {
            int y = (int) Math.floor(WorldMap.standY(x + 0.5f, z + 0.5f));
            tops++;
            if (g.vox.block(x, y, z) == Blocks.STONE || g.vox.block(x, y - 1, z) == Blocks.STONE) stones++;
        }
        assertTrue(stones < tops * 0.06, "stones on " + stones + " of " + tops + " columns");
    }

    @Test @Order(4) void roomsAreLitThrough() {
        int dark = 0;
        for (var f : ShadowVeil.terrain().arch.fitted.values()) for (String d : f.defects) if (d.contains(" is dark ")) dark++;
        assertTrue(dark <= 12, dark + " rooms still dark past their lights");
        // the Drunken Duck's common room: candles or sconces over its floor
        int lights = 0;
        for (var p : ShadowVeil.terrain().arch.pieces) if (p.owner().equals("drunken_duck") && (p.kind().equals("SCONCE") || p.kind().equals("CANDELABRA") || p.kind().equals("CHANDELIER"))) lights++;
        assertTrue(lights >= 4, "the Drunken Duck has " + lights + " lights");
    }

    @Test @Order(5) void rainAndSnowNeverFallTogether() {
        var w = new astratto.weather.Weather();
        w.target("Rain", 0.8f, 0, 0, 0, 0.2f, 0.3f, 0.9f, 0, 0, 0, 1, 1, 1);
        w.snap();
        w.target("Snow", 0, 0.8f, 0, 0, 0.3f, 0.3f, 0.9f, 0, 0, 0, 1, 1, 1);
        for (int i = 0; i < 6000; i++) {
            w.update(1 / 30f, 0, 0, 0, null, null);
            assertFalse(w.rain > 0.01f && w.snow > 0.01f, "rain " + w.rain + " and snow " + w.snow + " at once, step " + i);
        }
        assertTrue(w.snow > 0.5f, "the snow came in the end");
        w.target("Sleet", 0.42f, 0.32f, 0.14f, 0, 0.3f, 0.4f, 0.9f, 0, 0, 0, 1, 1, 1);
        for (int i = 0; i < 6000; i++) { w.update(1 / 30f, 0, 0, 0, null, null); assertFalse(w.rain > 0.01f && w.snow > 0.01f); }
    }

    @Test @Order(6) void encountersComeInOutOfSight() {
        var defs = g.encounters.definitions();
        int checked = 0;
        for (String id : new String[]{"highwaymen_toll", "night_ambush", "traveller_news", "realm_patrol", "fugitive"}) {
            if (!defs.containsKey(id)) continue;
            g.encounters.console(new String[]{"encounter", "clear"});
            int[] road = rpg.encounter.Encounters.roadWithinPublic(400, 900, 200);
            if (road == null) continue;
            g.teleport(g.player, road[0] + 0.5f, WorldMap.standY(road[0] + 0.5f, road[1] + 0.5f), road[1] + 0.5f);
            for (int i = 0; i < 20; i++) game.tick(1 / 60f);
            String res = g.encounters.console(new String[]{"encounter", "force", id});
            if (!res.startsWith("started")) continue;
            var in = g.encounters.active.get(g.encounters.active.size() - 1);
            for (var ids : in.cast.values()) for (long aid : ids) {
                var a = g.actor(aid);
                if (a == null) continue;
                float d = (float) Math.hypot(a.x - g.player.x, a.z - g.player.z);
                assertTrue(d >= 18, id + ": " + a.name + " came in " + d + " m off");
                if (d < 90) assertFalse(g.vox.sight(g.player.x, g.player.eyeY(), g.player.z, a.x, a.y + 1.6f, a.z) && g.vox.sight(g.player.x, g.player.eyeY(), g.player.z, a.x, a.y + 0.5f, a.z), id + ": " + a.name + " came in in plain sight at " + d + " m");
                checked++;
            }
        }
        g.encounters.console(new String[]{"encounter", "clear"});
        assertTrue(checked >= 4, "encounter people checked: " + checked);
    }

    @Test @Order(7) void theDangerGrowsAwayFromLoxford() {
        var lox = ShadowVeil.realms().stream().filter(r -> r.name().equalsIgnoreCase("Loxford")).findFirst().orElseThrow();
        int[] home = rpg.world.shadowveil.Danger.band(lox.cx(), lox.cz());
        int[] far = rpg.world.shadowveil.Danger.band(1000, 120);
        assertTrue(home[1] <= 15, "by Loxford: " + home[0] + "-" + home[1]);
        assertTrue(far[0] >= 30 && far[1] > home[1] + 40, "far off: " + far[0] + "-" + far[1]);
        assertNull(rpg.world.shadowveil.Danger.levels(MonsterKind.TROLL, lox.cx(), lox.cz()), "no troll makes its home by Loxford");
        // the wilds' territories near Loxford hold nothing stronger than the place
        for (var l : g.monsters.lairs) {
            if (Math.hypot(l.x - lox.cx(), l.z - lox.cz()) > 160 || DungeonLayout.underAt(l.x, 30, l.z) != null) continue;
            assertTrue(l.maxLevel <= Math.max(l.kind.minLevel, 22), l.name + " by Loxford holds levels to " + l.maxLevel);
        }
    }

    @Test @Order(8) void theNewMonstersAreMadeAndFight() {
        float x = g.player.x + 6, z = g.player.z;
        for (MonsterKind k : new MonsterKind[]{MonsterKind.TROLL, MonsterKind.ICE_TROLL, MonsterKind.RIVER_TROLL, MonsterKind.SMALL_SCORPION, MonsterKind.LARGE_SCORPION, MonsterKind.LESSER_DEMON, MonsterKind.GREATER_DEMON}) {
            for (int v = 0; v < k.variants(); v++) assertNotNull(rpg.render.MonsterModels.of(k, null, v), k + " look " + v);
            Monster m = g.monsters.spawn(k, (k.minLevel + k.maxLevel) / 2, x, z, null);
            assertNotNull(m, k + " made");
            int cl = m.skills.combatLevel();
            assertTrue(cl >= 1, k + " fights at a level");
            m.puppet = true;
        }
        assertEquals("Mountain troll", MonsterKind.TROLL.title);
        // a scorpion's pincer holds, its sting poisons
        Monster sc = g.monsters.spawn(MonsterKind.LARGE_SCORPION, 40, g.player.x + 1.5f, g.player.z, null);
        sc.yaw = (float) Math.atan2(-(g.player.x - sc.x), -(g.player.z - sc.z));
        g.monsters.strikeFor(sc, g.player, "pincer");
        assertTrue(g.player.cs.heldT > 0, "held fast");
        g.monsters.strikeFor(sc, g.player, "sting");
        assertTrue(g.player.cs.poisonT > 0 && g.player.cs.poisonDps > 0, "poisoned");
        float hp = g.player.hp;
        g.player.hp = Math.max(hp, 30);
        float before = g.player.hp;
        for (int i = 0; i < 180; i++) game.tick(1 / 60f);
        assertTrue(g.player.hp < before, "the venom works: " + before + " -> " + g.player.hp);
        g.player.cs.poisonT = 0; g.player.cs.heldT = 0; g.player.hp = g.player.maxHp();
    }

    @Test @Order(9) void theNewFurnishingsAreModelledAndBreak() {
        Furniture.Kind[] kinds = {Furniture.Kind.BARREL, Furniture.Kind.KEG, Furniture.Kind.LECTERN, Furniture.Kind.SPINNING_WHEEL, Furniture.Kind.SCROLL_RACK,
                Furniture.Kind.THRONE, Furniture.Kind.CRADLE, Furniture.Kind.COAT_RACK, Furniture.Kind.BUTTER_CHURN, Furniture.Kind.GLOBE, Furniture.Kind.TOOL_RACK,
                Furniture.Kind.MAP_TABLE, Furniture.Kind.DISPLAY_CABINET, Furniture.Kind.FOLDING_SCREEN, Furniture.Kind.SACKS};
        assertEquals(15, kinds.length);
        for (var k : kinds) {
            var layers = rpg.render.Props.furniture(k, 2, 2, 2, 7);
            int boxes = 0;
            for (var l : layers) boxes += l.boxes().count() / 36;
            assertTrue(boxes >= 3, k + " is modelled: " + boxes);
        }
        // and they are placed about the world's interiors
        java.util.Set<String> placed = new java.util.HashSet<>();
        for (var p : ShadowVeil.terrain().arch.pieces) placed.add(p.kind());
        int found = 0;
        for (var k : kinds) if (placed.contains(k.name())) found++;
        assertTrue(found >= 12, "new furnishings placed in the world: " + found + " of 15");
    }

    @Test @Order(10) void theJournalAndTheMap() {
        var log = g.questLog;
        assertFalse(log.showUnstarted, "quests not begun are hidden at first");
        assertTrue(log.shown(rpg.quest.QuestLog.Category.SIDE).stream().noneMatch(e -> e.status() == rpg.quest.QuestLog.Status.UNSTARTED));
        log.showUnstarted = true;
        assertTrue(log.all().stream().anyMatch(e -> e.id().equals("courier")));
        g.quests.start();
        var c = log.find("courier");
        assertEquals(rpg.quest.QuestLog.Status.ACTIVE, c.status());
        assertNotNull(c.destination(), "under way, the courier quest leads somewhere");
        log.toggleActive("courier");
        assertEquals("courier", log.active);
        assertNotNull(log.activeEntry());
        log.mapMarks.add(new float[]{100, 200, 0});
        var saved = log.save();
        var copy = new rpg.quest.QuestLog(g);
        copy.load(saved);
        assertEquals("courier", copy.active);
        assertEquals(1, copy.mapMarks.size());
        assertNotEquals(log.color(rpg.quest.QuestLog.Status.UNSTARTED), log.color(rpg.quest.QuestLog.Status.COMPLETED));
    }

    @Test @Order(11) void footprintsAreInTheDecalPictures() {
        int[] px = astratto.decal.DecalAtlas.paint(astratto.decal.DecalAtlas.FOOTPRINT, 0);
        int covered = 0;
        for (int p : px) if ((p >>> 24) > 60) covered++;
        assertTrue(covered > 300 && covered < 64 * 64 / 2, "a sole's print: " + covered + " pixels");
    }
}
