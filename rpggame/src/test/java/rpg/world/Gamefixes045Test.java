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
import rpg.item.Apparel;
import rpg.item.ItemDef;
import rpg.item.Items;
import rpg.objects.Furniture;
import rpg.world.shadowveil.Registry;
import rpg.world.shadowveil.ShadowVeil;

/**
 * (0.4.5) The pass's fixes checked in ShadowVeil itself: the shrines, the stations indoors, the chandeliers clear of the
 * stairs, the hangings clear of the standing pieces, the houses facing their streets, breakable furniture, the outfits
 * and who wears them, 225 people, the water's motion, the wild's gathering, the tail budget and the dungeon register.
 */
@org.junit.jupiter.api.TestMethodOrder(org.junit.jupiter.api.MethodOrderer.OrderAnnotation.class)
class Gamefixes045Test {
    private static Game game;
    private static Rpg g;

    @BeforeAll static void grow() {
        var cfg = new NativeEngine.Config(); cfg.deterministic = true; cfg.audioDevice = false;
        game = Game.create(cfg);
        game.activate(ShadowVeil.build(game.engine()));
        g = game.add(new Rpg(game, "Gamefixes 0.4.5", new Appearance(), true));
        game.tick(0);
    }
    @AfterAll static void close() { if (game != null) game.close(); WorldMap.activateDevmap(); }
    private static Registry reg() { return ShadowVeil.terrain().arch.registry; }

    @Test @Order(1) void theVersionIs045() { assertTrue(rpg.Main.VERSION.startsWith("Alpha 0.4."), rpg.Main.VERSION); }

    @Test @Order(2) void twoHundredTwentyFivePeople() { assertEquals(225, g.npcs.size()); }

    @Test @Order(3) void aShrineStandsOnTheGroundByEveryGraveyard() {
        assertEquals(g.graveyards.plots.size(), g.shrines.all.size());
        for (var s : g.shrines.all) {
            assertTrue(g.vox.solid(s.x0 - 1, s.floor - 1, s.z0) && !g.vox.solid(s.x0 - 1, s.floor + 1, s.z0), s.graveyard + " shrine not on the ground");
            assertTrue(g.vox.solid(s.x0, s.floor, s.z0) && g.vox.solid(s.x0 + 1, s.floor + 1, s.z0 + 1), s.graveyard + " shrine has no collision");
            assertFalse(s.options(g, g.player).isEmpty(), s.graveyard + " shrine offers nothing");
        }
    }

    @Test @Order(4) void everyStationIsIndoors() {
        List<String> out = new ArrayList<>();
        for (var st : g.objects.stations()) {
            boolean in = false;
            for (Registry.Entry e : reg().entries) if (e.enterable && !e.underground && st.x >= e.x0 && st.x < e.x1 + 1 && st.z >= e.z0 && st.z < e.z1 + 1) in = true;
            if (!in) out.add(st.label() + " at " + (int) st.x + "," + (int) st.y + "," + (int) st.z);
        }
        assertTrue(out.isEmpty(), out.size() + " stations out of doors: " + out.subList(0, Math.min(15, out.size())));
    }

    @Test @Order(5) void chandeliersHangClearOfTheStairsAndApart() {
        List<String> bad = new ArrayList<>();
        List<Furniture> ch = g.objects.furniture.stream().filter(f -> f.kind == Furniture.Kind.CHANDELIER).toList();
        for (Furniture f : ch) {
            for (int[] d : new int[][]{{0, 0}}) for (int y = f.floor - 1; y < f.floor + f.h; y++)
                if (g.vox.block(f.x0 + d[0], y, f.z0 + d[1]) == Blocks.STAIRS) bad.add("chandelier by stairs at " + f.x0 + "," + f.floor + "," + f.z0);
            for (Furniture o : ch) if (o != f && o.floor == f.floor && Math.abs(o.x0 - f.x0) + Math.abs(o.z0 - f.z0) < 2) bad.add("chandeliers doubled at " + f.x0 + "," + f.floor + "," + f.z0);
        }
        assertTrue(bad.isEmpty(), bad.size() + ": " + bad.subList(0, Math.min(10, bad.size())));
    }

    @Test @Order(6) void nothingHangsBehindAStandingPiece() {
        List<String> bad = new ArrayList<>();
        for (Furniture f : g.objects.furniture) {
            if (f.kind != Furniture.Kind.PAINTING && f.kind != Furniture.Kind.TAPESTRY && f.kind != Furniture.Kind.MIRROR && f.kind != Furniture.Kind.WEAPON_RACK && f.kind != Furniture.Kind.POT_RACK) continue;
            for (int x = f.x0; x <= f.x1; x++) for (int z = f.z0; z <= f.z1; z++) {
                int b = g.vox.block(x, f.floor + 1, z);
                if (b == Blocks.TABLE || b == Blocks.BOOKCASE || b == Blocks.HEADSTONE) bad.add(f.label() + " behind a piece at " + x + "," + f.floor + "," + z);
            }
        }
        assertTrue(bad.isEmpty(), bad.size() + ": " + bad.subList(0, Math.min(10, bad.size())));
    }

    @Test @Order(7) void aBlowReachesFurnitureAndBreaksIt() {
        Furniture table = g.objects.furniture.stream().filter(f -> f.kind == Furniture.Kind.TABLE && f.exists()).findFirst().orElseThrow();
        float ox = table.x0 - 0.6f, oy = table.floor + 0.7f, oz = (table.z0 + table.z1 + 1) / 2f;
        Voxels.Hit h = g.vox.ray(ox, oy, oz, 1, 0, 0, 3, false, true, true);
        float limit = h.hit ? h.dist : 3;
        Object[] hit = g.wreckage.ray(ox, oy, oz, 1, 0, 0, limit);
        assertNotNull(hit, "a swing at a table meets no part of it");
        var st = g.wreckage.of(table);
        for (int k = 0; k < 40 && !st.wrecked; k++) g.wreckage.strike(table, k % Math.max(1, rpg.objects.Wreckage.model(table).length), table.x, table.floor + 0.5f, table.z, 8f, 1, 0, 0, ItemDef.DamageType.BLUNT, g.player);
        assertTrue(st.wrecked, "the table never broke");
        assertTrue(rpg.objects.Wreckage.breakable(Furniture.Kind.CHAIR) && !rpg.objects.Wreckage.breakable(Furniture.Kind.RUG));
    }

    @Test @Order(8) void theOutfitsAreItemsWithTheirRequirements() {
        assertEquals(8, Apparel.outfits().size(), "Pigskin, Rogue, Prospector, three Desert, Farmer's, Lumberjack");
        assertEquals(7, Apparel.outfit("pigskin").pieces().size());
        assertEquals(5, Apparel.outfit("rogue").pieces().size());
        assertEquals(4, Apparel.outfit("prospector").pieces().size());
        for (String st : new String[]{"commoner", "noble", "royal"}) assertEquals(5, Apparel.outfit("desert_" + st).pieces().size());
        ItemDef mask = Items.get("rogue_mask");
        var skills = new rpg.skill.Skills();
        skills.setLevel(rpg.skill.Skill.AGILITY, 50);
        assertNotNull(rpg.item.Equipment.refusal(mask, skills), "the Rogue mask asks Thieving too");
        skills.setLevel(rpg.skill.Skill.THIEVING, 50);
        assertNull(rpg.item.Equipment.refusal(mask, skills));
        assertNull(rpg.item.Equipment.refusal(Items.get("pigskin_hood"), new rpg.skill.Skills()));
        assertNotNull(rpg.item.Equipment.refusal(Items.get("lumberjack_top"), new rpg.skill.Skills()));
        for (var o : Apparel.outfits().values()) for (ItemDef d : o.pieces()) assertNotNull(rpg.render.ItemModels.of(d), d.id + " has no model");
    }

    @Test @Order(9) void peopleWearTheirOutfitsAndArmsThatCanBeLooted() {
        int miners = 0, loggers = 0, bandits = 0, desert = 0, guards = 0;
        for (Npc n : g.npcs) {
            ItemDef head = n.eq.def(ItemDef.Slot.HEAD), body = n.eq.def(ItemDef.Slot.BODY);
            if (n.role == Npc.Role.MINER && head != null && "prospector".equals(head.outfit)) miners++;
            if (n.profession == rpg.ai.Profession.FIREWOOD_CUTTER && body != null && "lumberjack".equals(body.outfit)) loggers++;
            if (n.role == Npc.Role.BANDIT && head != null && head.outfit != null) bandits++;
            if (body != null && body.outfit != null && body.outfit.startsWith("desert_")) desert++;
            if (n.role == Npc.Role.GUARD && head != null && body != null && n.eq.def(ItemDef.Slot.LEGS) != null) guards++;
        }
        System.out.printf("[outfits] miners %d loggers %d bandits %d desert %d guards %d%n", miners, loggers, bandits, desert, guards);
        assertTrue(miners >= 8 && loggers >= 8 && bandits >= 6 && desert >= 20 && guards >= 10);
    }

    @Test @Order(10) void allTheWaterRunsByItself() {
        var f = g.fluids.field;
        assertNotNull(f);
        var t = ShadowVeil.terrain();
        int river = 0, moving = 0;
        for (int i = 0; i < t.S * t.S; i++) if ((t.flags[i] & rpg.world.shadowveil.Terrain.RIVER) != 0 && t.water[i] >= 0) { river++; if (f.vx(i / t.S, i % t.S) != 0 || f.vz(i / t.S, i % t.S) != 0) moving++; }
        System.out.printf("[water] %d of %d river columns moving, %d in all%n", moving, river, f.moving);
        assertTrue(moving > river * 0.9, moving + " of " + river + " river columns move");
        // a swimmer is carried at most two thirds of the water's pace
        assertTrue(rpg.actor.Swimming.FLOW_K / (1 - rpg.actor.Swimming.DRAG) * 20 < 0.7f);
    }

    @Test @Order(11) void theWildHasThingsToGatherAndTheyAreGathered() {
        var patches = g.objects.nodes.stream().filter(n -> n.kind == rpg.objects.Node.Kind.PATCH).toList();
        assertTrue(patches.size() > 150, "only " + patches.size() + " wild patches");
        var herb = patches.stream().filter(p -> p.level == 1).findFirst().orElseThrow();
        g.teleport(g.player, herb.x + 0.9f, WorldMap.standY(herb.x + 0.9f, herb.z), herb.z);
        for (int i = 0; i < 20; i++) game.tick(1 / 60f);
        assertTrue(herb.options(g, g.player).stream().anyMatch(o -> o.enabled()), "no enabled gather option at a herb patch");
    }

    @Test @Order(12) void theDungeonRegisterIsBound() {
        var sites = rpg.dungeon.DungeonSites.all();
        System.out.println("[dungeons] " + sites.size() + " sites; problems " + rpg.dungeon.DungeonSites.problems());
        assertTrue(sites.size() >= 10);
        assertTrue(rpg.dungeon.DungeonSites.problems().isEmpty(), rpg.dungeon.DungeonSites.problems().toString());
    }

    @Test @Order(13) void noOneTailsThePlayerPastTheBudget() {
        Npc n = g.npcs.stream().filter(x -> x.role == Npc.Role.GUARD && x.loaded && x.alive).findFirst().orElseThrow();
        n.goal = rpg.ai.Brain.Goal.WATCH; n.watchTarget = g.player.id; n.watchUntil = g.time + 999;
        n.tailSince = g.time; n.tailX = n.x + 60; n.tailZ = n.z;
        g.ai.brain.think(n);
        assertNotEquals(rpg.ai.Brain.Goal.WATCH, n.goal, "still watching sixty metres on");
        assertTrue(n.tailRestUntil > g.time);
    }
}
