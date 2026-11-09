package rpg.ai;

import static org.junit.jupiter.api.Assertions.*;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import java.util.ArrayList;
import org.junit.jupiter.api.*;
import rpg.Ids;
import rpg.Rpg;
import rpg.actor.Appearance;
import rpg.fishing.Fish;
import rpg.fishing.FishKind;
import rpg.fishing.Fishery;
import rpg.interact.Interactions;
import rpg.item.ItemDef;
import rpg.item.ItemStack;
import rpg.item.Items;
import rpg.monster.Monster;
import rpg.objects.Node;
import rpg.skill.Skill;
import rpg.world.Blocks;
import rpg.world.Coast;
import rpg.world.DevMap;

/** Alpha 0.3.5 gamefixes. */
class Gamefixes035Test {
    private Game game;
    private Rpg g;
    @BeforeEach void open() {
        var cfg = new NativeEngine.Config(); cfg.deterministic = true; game = Game.create(cfg); game.activate(DevMap.build(game.engine()));
        g = game.add(new Rpg(game, "Gamefixes 0.3.5", Appearance.henric(), true));
        for (Monster m : new ArrayList<>(g.monsters.all())) g.ai.unload(m);
        g.state.minutes = 13 * 60;
        game.tick(0);
    }
    @AfterEach void close() { game.close(); }
    private void ticks(int n) { for (int i = 0; i < n; i++) game.tick(1f / 60); }
    private void wield(ItemDef d) {
        g.player.eq.set(ItemDef.Slot.MAIN, new ItemStack(d, 1));
        g.state.mint(d, 1);
    }
    private Node spot(String def, float x, float z) {
        Node best = null; float bd = 1e9f;
        for (Node n : g.objects.nodes) if (n.kind == Node.Kind.FISH && (def == null || n.def.equals(def))) { float d = (float) Math.hypot(n.x - x, n.z - z); if (d < bd) { bd = d; best = n; } }
        return best;
    }

    // ------------------------------------------------------------------ the sea
    @Test void theSeaRunsAlongTheSouthAtTheBrooksEndAndIsDeepPastTheShelf() {
        assertEquals(256, DevMap.SZ);
        int s = Coast.shore(60);
        assertTrue(g.fluids.water(60, DevMap.FLOOR - 1, s), "the brook runs into the sea");
        assertEquals(3, g.fluids.depth(129.5f, DevMap.FLOOR, 232.5f), "the water off the pier's head is three deep");
        assertTrue(Coast.deep(100, 240));
        assertEquals(Blocks.PLANKS, g.vox.block(Coast.PIER_X0, DevMap.FLOOR - 1, 210), "the pier's deck");
        assertEquals(DevMap.WILDS, DevMap.jurisdiction(100, 230));
    }

    // ------------------------------------------------------------------ real fish
    @Test void fishSwimAsRealFishNearThePlayerAndKeepToTheirWater() {
        g.teleport(g.player, DevMap.POND_X0 - 3, DevMap.FLOOR, DevMap.POND_Z0 + 5);
        ticks(30);
        long inPond = g.fishery.live.stream().filter(f -> f.water.name.equals("the fishing pond")).count();
        assertTrue(inPond >= 30, "the pond's stock swims: " + inPond);
        float[][] before = new float[g.fishery.live.size()][];
        for (int i = 0; i < before.length; i++) { Fish f = g.fishery.live.get(i); before[i] = new float[]{f.x, f.z}; }
        ticks(240);
        int moved = 0;
        for (int i = 0; i < Math.min(before.length, g.fishery.live.size()); i++) { Fish f = g.fishery.live.get(i); if (Math.hypot(f.x - before[i][0], f.z - before[i][1]) > 0.2f) moved++; }
        assertTrue(moved > before.length / 3, "fish move about: " + moved + "/" + before.length);
        for (Fish f : g.fishery.live) assertTrue(g.fluids.in(f.x, f.y, f.z), f.kind + " out of the water at " + f.x + "," + f.y + "," + f.z);
    }

    @Test void aCastDrawsAnEligibleFishWhichIsLandedOutOfItsWatersStock() {
        Node net = spot("shrimp_spot", DevMap.FISH_SPOTS[0][0], DevMap.FISH_SPOTS[0][1]);
        assertNotNull(net);
        g.teleport(g.player, net.x, DevMap.FLOOR, net.z - 2.2f);
        if (!g.vox.solid((int) g.player.x, DevMap.FLOOR - 1, (int) g.player.z)) g.teleport(g.player, net.x - 2.2f, DevMap.FLOOR, net.z);
        wield(Items.FISHING_NET);
        ticks(10);
        Fishery.Water pond = g.fishery.waterAt(net.x, net.z);
        int before = pond.total();
        int shrimpBefore = g.player.count(Items.RAW_SHRIMP), guppyBefore = g.player.count(Items.get("raw_guppy"));
        var r = g.interact.request(g.player, net, "gather");
        assertFalse(r.rejected(), String.valueOf(r.reason()));
        for (int i = 0; i < 60 * 60 && g.player.count(Items.RAW_SHRIMP) == shrimpBefore && g.player.count(Items.get("raw_guppy")) == guppyBefore; i++) game.tick(1f / 60);
        // level 1: only shrimp can be taken with a net (guppies want Fishing 5)
        assertTrue(g.player.count(Items.RAW_SHRIMP) > shrimpBefore, "a shrimp landed");
        assertEquals(guppyBefore, g.player.count(Items.get("raw_guppy")));
        assertTrue(pond.total() < before, "the catch came out of the pond's stock");
    }

    @Test void gearDecidesWhatCanBeCaughtAndLevelsGateIt() {
        Node net = spot("shrimp_spot", DevMap.FISH_SPOTS[0][0], DevMap.FISH_SPOTS[0][1]);
        assertTrue(g.fishery.takenHere(net.x, net.z, Items.HARPOON).isEmpty(), "nothing in a pond is taken with a harpoon");
        Node deep = spot("sea_spot", Coast.PIER_X1 + 2.5f, Coast.PIER_Z1 + 1.5f);
        var kinds = g.fishery.takenHere(deep.x, deep.z, Items.HARPOON);
        assertTrue(kinds.contains(FishKind.TUNA) && kinds.contains(FishKind.WHALE) || kinds.contains(FishKind.BLACK_MARLIN), "the deep holds the great fish: " + kinds);
        g.teleport(g.player, Coast.PIER_X1, DevMap.FLOOR, Coast.PIER_Z1);
        wield(Items.HARPOON);
        assertTrue(deep.refusal(g.player).contains("level 35"), deep.refusal(g.player));
        g.player.skills.setLevel(Skill.FISHING, 40);
        assertNull(deep.refusal(g.player));
    }

    @Test void theLevelsAreTheSchedule() {
        int[] want = {1, 2, 5, 10, 10, 15, 20, 25, 30, 35, 40, 45, 45, 50, 60, 65, 70, 75, 80, 85, 90, 95, 100};
        FishKind[] order = {FishKind.SHRIMP, FishKind.SARDINE, FishKind.GUPPY, FishKind.CRAB, FishKind.HERRING, FishKind.MACKEREL, FishKind.TROUT, FishKind.COD, FishKind.SALMON, FishKind.TUNA, FishKind.LOBSTER, FishKind.BASS, FishKind.CATFISH, FishKind.SWORDFISH, FishKind.STARFISH, FishKind.SQUID, FishKind.HADDOCK, FishKind.SHARK, FishKind.ELECTRIC_EEL, FishKind.ANGLERFISH, FishKind.BLUE_MARLIN, FishKind.WHALE, FishKind.BLACK_MARLIN};
        for (int i = 0; i < order.length; i++) assertEquals(want[i], order[i].level, order[i].name());
        for (FishKind k : FishKind.values()) { assertNotNull(k.raw()); assertTrue(k.combatLevel() >= 1, k + " fights with its skills"); }
        assertTrue(FishKind.WHALE.combatLevel() > FishKind.TUNA.combatLevel() && FishKind.TUNA.combatLevel() > FishKind.SHRIMP.combatLevel(), "the bigger fish fight harder");
        assertEquals(55, FishKind.BAREHANDED_FISHING);
        assertEquals(35, FishKind.BAREHANDED_STRENGTH);
    }

    @Test void fightingAFishTrainsFightingNotFishingAndTheTunaIsTakenBarehanded() {
        g.teleport(g.player, Coast.PIER_X1, DevMap.FLOOR, Coast.PIER_Z1 - 1);
        ticks(30);
        Fish tuna = null;
        for (Fish f : g.fishery.live) if (f.kind == FishKind.TUNA) { tuna = f; break; }
        assertNotNull(tuna, "tuna swim in the deep by the pier");
        double fishingBefore = g.player.skills.xp(Skill.FISHING), attackBefore = g.player.skills.xp(Skill.ATTACK);
        g.fishery.struck(g.player, tuna, 6, true, false, null, tuna.x, tuna.y, tuna.z);
        assertEquals(fishingBefore, g.player.skills.xp(Skill.FISHING), 1e-6, "no Fishing XP for a blow");
        assertTrue(g.player.skills.xp(Skill.ATTACK) > attackBefore, "a blow trains Attack");
        Fish other = null;
        for (Fish f : g.fishery.live) if (f.kind == FishKind.TUNA && f.alive) { other = f; break; }
        assertNotNull(other);
        g.player.skills.setLevel(Skill.FISHING, 55); g.player.skills.setLevel(Skill.STRENGTH, 35);
        int had = g.player.count(Items.get("raw_tuna"));
        g.fishery.struck(g.player, other, 1, true, true, null, other.x, other.y, other.z);
        assertEquals(had + 1, g.player.count(Items.get("raw_tuna")), "a punch at Fishing 55 / Strength 35 takes the tuna");
        assertFalse(other.alive);
    }

    // ------------------------------------------------------------------ the bugs
    @Test void waterBreaksAFall() {
        g.teleport(g.player, 96.5f, DevMap.FLOOR + 14, 34.5f);   // over the fishing pond
        float hp = g.player.hp;
        for (int i = 0; i < 60 * 4; i++) game.tick(1f / 60);
        assertTrue(g.player.alive);
        assertEquals(hp, g.player.hp, 0.01f, "a drop into water costs nothing");
    }

    @Test void talkIsThePrimaryOptionAndPickpocketingIsWhenSneaking() {
        Npc n = null;
        for (Npc o : g.npcs) if (o.alive && o.loaded && !o.watch() && o.canTalk(g)) { n = o; break; }
        assertNotNull(n);
        g.teleport(g.player, n.x + 1.0f, n.y, n.z);
        var opts = Interactions.ranked(n.options(g, g.player));
        assertEquals("talk", Interactions.primaryOf(opts).id());
        g.player.sneaking = true;
        opts = Interactions.ranked(n.options(g, g.player));
        assertEquals("pickpocket-direct", Interactions.primaryOf(opts).id());
        g.player.sneaking = false;
    }

    @Test void theWatchSpeaksOnlyFaceToFace() {
        Npc bram = (Npc) g.actor(Ids.LAWMAN_A);
        g.teleport(g.player, bram.x + 2, bram.y + 3, bram.z);
        assertFalse(g.law.withinSpeech(bram, g.player), "not up on a roof");
        g.teleport(g.player, bram.x + 5, bram.y, bram.z);
        assertFalse(g.law.withinSpeech(bram, g.player), "not from across a room");
        g.law.setBounty(g.player, DevMap.ALPHA, 12);
        assertNull(g.law.halt(bram, g.player), "no halt put to someone out of earshot");
    }

    @Test void plateSinksABodyAndClothFloatsIt() {
        Npc n = null;
        for (Npc o : g.npcs) if (!o.watch()) { n = o; break; }
        for (var s : ItemDef.Slot.values()) n.eq.set(s, null);
        assertTrue(AiSystem.buoyancy(n) > 1, "a body in its clothes floats");
        n.eq.set(ItemDef.Slot.BODY, new ItemStack(Items.get("steel_body"), 1));
        n.eq.set(ItemDef.Slot.LEGS, new ItemStack(Items.get("steel_legs"), 1));
        assertTrue(AiSystem.buoyancy(n) < 1, "plate drags it under");
    }

    // ------------------------------------------------------------------ guilds, the textile and metal industries
    @Test void guildRowStandsWithItsGuildmastersStationsAndStores() {
        for (var h : rpg.econ.Guilds.HALLS) {
            assertTrue(g.actor(h.master()) instanceof Npc, h.name() + " has its keeper");
            assertNotNull(g.econ.shopOf((Npc) g.actor(h.master())), h.name() + "'s store");
            for (Object[] st : h.stations()) {
                boolean found = false;
                for (var s : g.objects.stations()) found |= s.kind == st[0] && Math.abs(s.x - ((int) st[1] + 0.5f)) < 0.01f && Math.abs(s.z - ((int) st[2] + 0.5f)) < 0.01f;
                assertTrue(found, h.name() + " " + st[0]);
            }
        }
        assertEquals(7, rpg.econ.Guilds.Kind.values().length);
        // members by calling
        assertTrue(g.guilds.member(g.actor(Ids.SMELTER), rpg.econ.Guilds.Kind.METALLISTS));
        assertTrue(g.guilds.member(g.actor(Ids.MINER_CITY), rpg.econ.Guilds.Kind.METALLISTS));
        assertTrue(g.guilds.member(g.actor(Ids.SPINNER), rpg.econ.Guilds.Kind.ARTISANS));
        assertTrue(g.guilds.member(g.actor(Ids.CROP_FARMER_CITY), rpg.econ.Guilds.Kind.FARMERS));
        assertTrue(g.guilds.member(g.actor(Ids.COASTAL_FISHER), rpg.econ.Guilds.Kind.MARINERS));
        assertFalse(g.guilds.member(g.player, rpg.econ.Guilds.Kind.METALLISTS));
    }

    @Test void thePlayerJoinsAGuildWhenSkilledEnoughAndWorksQuickerAtItsHall() {
        Npc master = (Npc) g.actor(Ids.GUILD_ARTISANS);
        g.give(g.player, Items.COINS, 100);
        assertNotNull(g.guilds.joinRefusal(g.player, rpg.econ.Guilds.Kind.ARTISANS), "Crafting 1 is not enough");
        g.player.skills.setLevel(Skill.CRAFTING, 20);
        assertFalse(g.guilds.join(g.player, rpg.econ.Guilds.Kind.ARTISANS, master, g.state.newRequestId()).rejected());
        assertTrue(g.guilds.member(g.player, rpg.econ.Guilds.Kind.ARTISANS));
        rpg.objects.Station loom = null;
        for (var s : g.objects.stations()) if (s.kind == rpg.item.Recipes.Station.LOOM) loom = s;
        assertEquals(rpg.econ.Guilds.HALL_SPEED, g.guilds.speed(g.player, loom), 1e-6);
    }

    @Test void fleeceBecomesClothThroughEveryStageOfTheTrade() {
        g.player.skills.setLevel(Skill.CRAFTING, 25);
        for (var grid : g.player.grids()) grid.clear();
        g.give(g.player, Items.WOOL, 2); g.give(g.player, Items.BUCKET_OF_WATER, 1); g.give(g.player, Items.BERRIES, 2);
        String[][] steps = {{"CARDING_BENCH", "card_wool"}, {"CARDING_BENCH", "card_wool"}, {"SPINNING_WHEEL", "spin_yarn"}, {"SPINNING_WHEEL", "spin_yarn"},
                {"LOOM", "weave_wool"}, {"FULLING_TUB", "full_cloth"}, {"DYE_VAT", "dye_cloth"}};
        for (String[] step : steps) {
            rpg.objects.Station st = null;
            for (var s : g.objects.stations()) if (s.kind.name().equals(step[0])) st = s;
            g.teleport(g.player, st.x, DevMap.FLOOR, st.z - 1.4f);
            ticks(2);
            var r = g.interact.request(g.player, st, "make:" + step[1]);
            assertFalse(r.rejected(), step[1] + ": " + r.reason());
            for (int i = 0; i < 60 * 8 && g.player.working != null; i++) game.tick(1f / 60);
        }
        assertEquals(1, g.player.count(Items.DYED_CLOTH), "wool carded, spun, woven, fulled and dyed");
        assertEquals(1, g.player.count(Items.BUCKET), "the fuller's bucket comes back empty");
    }

    @Test void aSheepIsShornForItsFleeceAndEatingTakesAMoment() {
        rpg.animal.Animal sheep = null;
        for (var a : g.animals.all()) if (a.species == rpg.animal.Species.SHEEP && !a.baby && a.alive) { sheep = a; break; }
        assertNotNull(sheep);
        g.player.eq.set(ItemDef.Slot.MAIN, new ItemStack(Items.SHEARS, 1)); g.state.mint(Items.SHEARS, 1);
        g.teleport(g.player, sheep.x + 1, DevMap.FLOOR, sheep.z);
        sheep.puppet = true;
        int wool = g.player.count(Items.WOOL);
        var shorn = g.interact.request(g.player, sheep, "shear");
        assertFalse(shorn.rejected(), String.valueOf(shorn.reason()));
        for (int i = 0; i < 60 * 5 && g.player.working != null; i++) game.tick(1f / 60);
        assertEquals(wool + 2, g.player.count(Items.WOOL));
        // a meal is a few bites, not a blink
        g.player.hp = 3; g.give(g.player, Items.BREAD, 1);
        var grid = g.player.inv; int slot = grid.first(Items.BREAD);
        assertEquals(rpg.interact.Result.Status.STARTED, g.items.perform(g.player, slot, "eat").status());
        assertEquals(3, g.player.hp, 0.01f, "nothing healed before the last bite");
        for (int i = 0; i < 60 * 3 && g.player.working != null; i++) game.tick(1f / 60);
        assertTrue(g.player.hp > 3);
    }
}
