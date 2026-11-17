package rpg.encounter;

import static org.junit.jupiter.api.Assertions.*;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import rpg.Rpg;
import rpg.actor.Appearance;
import rpg.ai.Brain;
import rpg.ai.Npc;
import rpg.item.ItemStack;
import rpg.item.Items;
import rpg.world.WorldMap;
import rpg.world.shadowveil.Atlas;
import rpg.world.shadowveil.ShadowVeil;

/**
 * (0.4.7) The Encounter System: its data whole and sound, the director's choice reproducible and regional, one encounter
 * run through both of its main paths (paying a toll, refusing it), its people let go afterwards, and its memory saved.
 */
@org.junit.jupiter.api.TestMethodOrder(org.junit.jupiter.api.MethodOrderer.OrderAnnotation.class)
class EncounterTest {
    private static Game game;
    private static Rpg g;

    @BeforeAll static void grow() {
        var cfg = new NativeEngine.Config(); cfg.deterministic = true; cfg.audioDevice = false;
        game = Game.create(cfg);
        game.activate(ShadowVeil.build(game.engine()));
        g = game.add(new Rpg(game, "Encounters", new Appearance(), true));
        game.tick(0);
    }
    @AfterAll static void close() { if (game != null) game.close(); WorldMap.activateDevmap(); }

    static final Set<String> BEHAVIORS = Set.of("hide", "block", "approach", "chase", "surround", "hold", "plead", "travel", "follow", "lead", "leave", "flee", "attack_player",
            "attack", "hunt", "migrate", "scavenge", "graze", "despawn", "arrest", "drift");
    static final Set<String> EFFECTS = Set.of("pay", "give", "reward", "honor", "rumor", "flag", "crime", "hostile", "free", "heal", "trade", "surrender");
    static final Set<String> CONDITIONS = Set.of("timer", "near", "player_far", "dead", "gone", "hurt", "hurt_half", "arrived", "wanted");

    @Test @Order(1) void definitionsAreWhole() {
        var defs = g.encounters.definitions();
        assertTrue(defs.size() >= 20, "at least twenty encounters, found " + defs.size());
        Set<String> types = new java.util.HashSet<>(), rarities = new java.util.HashSet<>();
        for (var d : defs.values()) {
            String id = Encounters.str(d, "id", "?");
            types.add(Encounters.str(d, "type", "?")); rarities.add(Encounters.str(d, "rarity", "?"));
            var stages = Encounters.map(d, "stages");
            assertTrue(stages.containsKey(Encounters.str(d, "start", "")), id + ": its start stage");
            Set<String> tags = new java.util.HashSet<>();
            for (Object o : Encounters.list(d, "cast")) {
                var c = Encounters.cast(o);
                tags.add(Encounters.str(c, "tag", ""));
                switch (Encounters.str(c, "kind", "")) {
                    case "person" -> { assertDoesNotThrow(() -> Npc.Role.valueOf(Encounters.str(c, "role", ""))); for (Object w : Encounters.list(c, "wear")) assertNotNull(Items.find(String.valueOf(w)), id + ": no item " + w); }
                    case "animal", "carcass" -> assertDoesNotThrow(() -> rpg.animal.Species.valueOf(Encounters.str(c, "species", "")));
                    case "monster" -> assertDoesNotThrow(() -> rpg.monster.MonsterKind.valueOf(Encounters.str(c, "monster", "")));
                    default -> fail(id + ": unknown cast kind " + Encounters.str(c, "kind", ""));
                }
            }
            for (var e : stages.entrySet()) {
                var st = Encounters.cast(e.getValue());
                for (var b : Encounters.map(st, "do").entrySet()) {
                    assertTrue(tags.contains(b.getKey()), id + "/" + e.getKey() + ": no party " + b.getKey());
                    String v = String.valueOf(b.getValue()), verb = v.contains(":") ? v.substring(0, v.indexOf(':')) : v;
                    assertTrue(BEHAVIORS.contains(verb), id + ": unknown behaviour " + v);
                    if (v.contains(":")) assertTrue(tags.contains(v.substring(v.indexOf(':') + 1)), id + ": " + v + " names no party");
                }
                for (Object o : Encounters.list(st, "next")) {
                    var t = Encounters.cast(o);
                    assertTrue(stages.containsKey(Encounters.str(t, "to", "")), id + ": a way to a missing stage " + Encounters.str(t, "to", ""));
                    assertTrue(CONDITIONS.contains(Encounters.str(t, "when", "").split(":")[0]), id + ": unknown condition " + Encounters.str(t, "when", ""));
                }
                for (Object o : Encounters.list(st, "effects")) assertTrue(EFFECTS.contains(String.valueOf(o).split(":")[0]), id + ": unknown effect " + o);
                var dlg = Encounters.map(st, "dialog");
                if (!dlg.isEmpty()) {
                    assertTrue(tags.contains(Encounters.str(dlg, "speaker", "")), id + ": a dialogue with no speaker");
                    for (Object o : Encounters.list(dlg, "choices")) {
                        var c = Encounters.cast(o);
                        assertTrue(stages.containsKey(Encounters.str(c, "to", "")), id + ": a choice to a missing stage");
                        if (c.containsKey("else")) assertTrue(stages.containsKey(Encounters.str(c, "else", "")), id + ": a choice's else to a missing stage");
                        for (Object f : Encounters.list(c, "effects")) assertTrue(EFFECTS.contains(String.valueOf(f).split(":")[0]), id + ": unknown effect " + f);
                    }
                }
                // every stage ends or leads somewhere (a dialogue's choices count)
                assertTrue(st.containsKey("end") || !Encounters.list(st, "next").isEmpty() || !dlg.isEmpty(), id + "/" + e.getKey() + ": a stage that never ends");
            }
        }
        assertTrue(types.containsAll(Set.of("human", "animal", "monster")), "human, animal and monster encounters: " + types);
        assertTrue(rarities.containsAll(Set.of("common", "uncommon", "rare")), "every rarity: " + rarities);
    }

    /** A road cell in a region's middle, the player stood on it facing along it. */
    private static float[] onRoadIn(String region) {
        var a = Atlas.get();
        for (var r : Atlas.list(a.root, "regions")) {
            if (!region.equals(Atlas.s(r, "id", ""))) continue;
            int[] c = a.cells((List<?>) r.get("rect"));
            int[] road = Encounters.roadWithin((c[0] + c[2]) / 2f, (c[1] + c[3]) / 2f, 160);
            assertNotNull(road, "a road in " + region);
            g.teleport(g.player, road[0] + 0.5f, WorldMap.standY(road[0] + 0.5f, road[1] + 0.5f), road[1] + 0.5f);
            g.player.vx = g.player.vz = 0;
            return new float[]{road[0] + 0.5f, road[1] + 0.5f};
        }
        fail("no region " + region);
        return null;
    }

    @Test @Order(2) void highwaymenAskTheirTollAndArePaid() {
        g.state.minutes = Math.floor(g.state.minutes / 1440) * 1440 + 13 * 60;
        onRoadIn("gallows_heath");
        g.player.skills.setLevel(rpg.skill.Skill.ATTACK, 20); g.player.skills.setLevel(rpg.skill.Skill.STRENGTH, 20); g.player.skills.setLevel(rpg.skill.Skill.DEFENSE, 20); g.player.skills.setLevel(rpg.skill.Skill.HEALTH, 20);
        assertNull(g.encounters.ineligible(g.encounters.definitions().get("highwaymen_toll"), true), "highwaymen fit Gallows Heath's road: " + g.encounters.ineligible(g.encounters.definitions().get("highwaymen_toll"), true));
        g.grant(g.player, new ItemStack(Items.COINS, 300));
        var in = g.encounters.start(g.encounters.definitions().get("highwaymen_toll"), new Random(5));
        assertNotNull(in, g.encounters.lastWhy);
        var robbers = in.cast.get("robber");
        assertTrue(robbers.size() >= 2 && robbers.size() <= 4, "two to four highwaymen: " + robbers.size());
        for (long id : robbers) { Npc n = (Npc) g.actor(id); assertNotNull(n.encounter); assertFalse(n.hostileToPlayer, "they ask before they take"); }
        assertEquals("lurk", in.stage);
        // walk up to them: they spring out and close in, then put the question
        Npc first = (Npc) g.actor(robbers.get(0));
        // (0.4.8) they come in further off now, out of sight: a longer walk to them
        for (int i = 0; i < 60 * 45 && !in.stage.equals("demand"); i++) {
            if (i % 30 == 0 && g.player.distTo(first) > 4) { float dx = first.x - g.player.x, dz = first.z - g.player.z, l = (float) Math.hypot(dx, dz); g.teleport(g.player, g.player.x + dx / l * 1.5f, WorldMap.standY(g.player.x + dx / l * 1.5f, g.player.z + dz / l * 1.5f), g.player.z + dz / l * 1.5f); }
            game.tick(1 / 60f);
        }
        assertEquals("demand", in.stage, "they demanded the toll (stage " + in.stage + ")");
        int before = g.player.count(Items.COINS);
        g.encounters.choose(in, 0);
        int paid = before - g.player.count(Items.COINS);
        assertTrue(paid >= 15 && paid <= before / 10 + 1, "a tenth paid (min 15): " + paid + " of " + before);
        assertTrue(in.ended && in.outcome.equals("paid"), "it ended paid: " + in.outcome);
        int robberCoins = 0; for (long id : robbers) robberCoins += ((Npc) g.actor(id)).count(Items.COINS);
        assertTrue(robberCoins >= paid, "the coins went to them");
        // they leave; out of sight and far off, they are let go or keep a camp as drifters
        for (int i = 0; i < 60 * 60; i++) game.tick(1 / 60f);
        g.teleport(g.player, g.player.x + 400, WorldMap.standY(g.player.x + 400, g.player.z), g.player.z);
        for (int i = 0; i < 60 * 5; i++) game.tick(1 / 60f);
        for (long id : robbers) { var a = g.actor(id); assertTrue(a == null || ((Npc) a).encounter == null || ((Npc) a).encounter.drifter, "each highwayman let go or a drifter"); }
    }

    @Test @Order(3) void refusingTheTollMeansAFight() {
        onRoadIn("gallows_heath");
        var in = g.encounters.start(g.encounters.definitions().get("highwaymen_toll"), new Random(9));
        assertNotNull(in);
        in.stage = "demand";
        g.encounters.choose(in, 1);
        assertEquals("fight", in.stage);
        for (int i = 0; i < 30; i++) game.tick(1 / 60f);
        for (long id : in.cast.get("robber")) {
            Npc n = (Npc) g.actor(id);
            n.nextThink = 0; g.ai.brain.think(n);
            assertTrue(n.hostileToPlayer, "they turn on the player");
            assertEquals(Brain.Goal.FIGHT, n.goal, n.name + " fights");
            assertEquals(g.player.id, n.fightTarget);
        }
        assertNotNull(g.encounters.console(new String[]{"encounter", "clear"}));
    }

    @Test @Order(4) void theDirectorIsReproducibleAndRegional() {
        // the same draws give the same picks
        Map<String, Integer> heath = new HashMap<>(), lowlands = new HashMap<>(), desert = new HashMap<>();
        onRoadIn("gallows_heath");
        java.util.List<String> a = new java.util.ArrayList<>(), b = new java.util.ArrayList<>();
        for (int i = 0; i < 40; i++) { var d = g.encounters.choose(Encounters.seeded(31, i), true); a.add(d == null ? "-" : Encounters.str(d, "id", "?")); }
        for (int i = 0; i < 40; i++) { var d = g.encounters.choose(Encounters.seeded(31, i), true); b.add(d == null ? "-" : Encounters.str(d, "id", "?")); }
        assertEquals(a, b, "seeded draws repeat");
        // each region's own pattern, over the day's hours
        for (int h = 0; h < 24; h++) {
            g.state.minutes = Math.floor(g.state.minutes / 1440) * 1440 + h * 60;
            for (var e : List.of(Map.entry("gallows_heath", heath), Map.entry("loxford_lowlands", lowlands), Map.entry("azharad_basin", desert))) {
                onRoadIn(e.getKey());
                for (int i = 0; i < 40; i++) { var d = g.encounters.choose(Encounters.seeded(h, i), true); if (d != null) e.getValue().merge(Encounters.str(d, "id", "?"), 1, Integer::sum); }
            }
        }
        System.out.println("[encounters] heath " + heath + "\n[encounters] lowlands " + lowlands + "\n[encounters] desert " + desert);
        assertNotEquals(top(heath), top(lowlands), "the heath and the lowlands each have their own commonest encounter");
        assertTrue(heath.getOrDefault("highwaymen_toll", 0) > lowlands.getOrDefault("highwaymen_toll", 0), "highwaymen haunt the heath more than the king's lowlands");
        assertTrue(heath.size() >= 6 && lowlands.size() >= 5, "a spread of encounters in each");
    }
    private static String top(Map<String, Integer> m) { return m.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse("-"); }

    @Test @Order(5) void everyEncounterStartsWhereItFits() {
        // each encounter forced where it may happen starts, makes its cast, and is cleared again
        g.state.minutes = Math.floor(g.state.minutes / 1440) * 1440 + 13 * 60;
        for (String region : List.of("gallows_heath", "loxford_lowlands", "azharad_basin", "dead_wood")) {
            onRoadIn(region);
            for (var d : g.encounters.definitions().values()) {
                var in = g.encounters.start(d, new Random(3));
                assertNotNull(in, Encounters.str(d, "id", "?") + " started in " + region);
                int made = 0; for (var ids : in.cast.values()) made += ids.size();
                assertTrue(made >= 1, Encounters.str(d, "id", "?") + " made its cast");
                for (int i = 0; i < 20; i++) game.tick(1 / 60f);
                g.encounters.console(new String[]{"encounter", "clear"});
            }
        }
        assertEquals(0, g.encounters.parts.size(), "everyone let go after the clear");
    }

    @Test @Order(6) void itsMemoryIsSaved() {
        g.encounters.flags.put("highwaymen_beaten", true);
        var saved = g.encounters.save();
        var other = new Encounters(g);
        other.load(saved);
        assertEquals(g.encounters.history.size(), other.history.size());
        assertTrue(other.flags.getOrDefault("highwaymen_beaten", false));
        assertEquals(g.encounters.ineligible(g.encounters.definitions().get("highwaymen_toll"), false), other.ineligible(other.definitions().get("highwaymen_toll"), false), "the same cooldowns after a load");
    }
}
