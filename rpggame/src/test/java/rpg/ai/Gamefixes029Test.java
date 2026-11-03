package rpg.ai;

import static org.junit.jupiter.api.Assertions.*;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import java.util.ArrayList;
import org.junit.jupiter.api.*;
import rpg.Rpg;
import rpg.actor.Appearance;
import rpg.monster.Monster;
import rpg.objects.Node;
import rpg.world.DevMap;

/** Alpha 0.2.9 gamefixes: workers keep their own room at a tree or a rock. */
class Gamefixes029Test {
    private Game game;
    private Rpg g;
    @BeforeEach void open() {
        var cfg = new NativeEngine.Config(); cfg.deterministic = true; game = Game.create(cfg); game.activate(DevMap.build(game.engine()));
        g = game.add(new Rpg(game, "Gamefixes 0.2.9", Appearance.henric(), true));
        for (Monster m : new ArrayList<>(g.monsters.all())) g.ai.unload(m);
        game.tick(0);
    }
    @AfterEach void close() { game.close(); }

    private java.util.List<Npc> two() {
        var list = g.npcs.stream().filter(n -> n.alive && n.loaded).limit(2).toList();
        assertEquals(2, list.size());
        return list;
    }

    @Test void twoWorkersAtOneRockStandInDifferentCells() {
        Node rock = g.objects.nodes.stream().filter(n -> n.kind == Node.Kind.ROCK).findFirst().orElseThrow();
        var w = two();
        for (Npc n : w) g.teleport(n, rock.x + 3, DevMap.FLOOR, rock.z + 3);
        game.tick(0);
        float[] a = g.ai.standSpot(w.get(0), rock), b = g.ai.standSpot(w.get(1), rock);
        assertFalse(Math.floor(a[0]) == Math.floor(b[0]) && Math.floor(a[1]) == Math.floor(b[1]),
                () -> "both were sent to " + a[0] + "," + a[1]);
        // asking again keeps each one's own cell
        float[] again = g.ai.standSpot(w.get(0), rock);
        assertArrayEquals(a, again);
    }

    @Test void aSecondWoodcutterIsNotSentToATreeAlreadyClaimed() {
        var w = two();
        Npc first = w.get(0), second = w.get(1);
        first.skills.setLevel(rpg.skill.Skill.WOODCUTTING, 99); second.skills.setLevel(rpg.skill.Skill.WOODCUTTING, 99);
        for (Npc n : w) { n.store(new rpg.item.ItemStack(rpg.item.Items.get("bronze_axe"), 1)); g.teleport(n, 94.5f, DevMap.FLOOR, 14.5f); }
        game.tick(0);
        Node tree = g.ai.nearestNode(first, "tree");
        assertNotNull(tree);
        g.ai.standSpot(first, tree);
        Node other = g.ai.nearestNode(second, "tree");
        assertNotNull(other);
        assertNotSame(tree, other, "the claimed tree is left to the one felling it");
    }

    @Test void aBountyThatCannotBeMetTakesTheCoinCollateralAndTheCell() {
        Npc officer = (Npc) g.actor(rpg.Ids.LAWMAN_A);
        g.teleport(officer, 100.5f, DevMap.FLOOR, 70.5f); g.teleport(g.player, 101.5f, DevMap.FLOOR, 70.5f); game.tick(0);
        g.player.inv.clear();
        assertTrue(g.player.store(new rpg.item.ItemStack(rpg.item.Items.COINS, 20))); g.state.mint(rpg.item.Items.COINS, 20);
        var dear = rpg.item.Items.get("bronze_battleaxe");
        assertTrue(g.player.store(new rpg.item.ItemStack(dear, 1))); g.state.mint(dear, 1);
        g.law.setBounty(g.player, DevMap.ALPHA, 60);
        // the full sum is still refused through the plain payment
        assertTrue(g.law.payBounty(g.player, officer, g.state.newRequestId()).rejected());
        assertEquals(60, g.law.bounty(g.player.id, DevMap.ALPHA));
        var dialog = g.law.halt(officer, g.player);
        assertTrue(dialog.choices.stream().anyMatch(c -> c.label().startsWith("Pay what I have (20 of 60")), "the officer offers what the purse can do");
        int officerCoins = officer.count(rpg.item.Items.COINS);
        assertTrue(g.law.payWhatYouCan(g.player, officer, g.state.newRequestId()).committed());
        assertEquals(officerCoins + 20, officer.count(rpg.item.Items.COINS), "every coin carried goes to the watch");
        assertTrue(g.law.inCustody(g.player), "the rest is answered for in jail");
        assertTrue(g.state.all().stream().anyMatch(e -> e.type().equals("BountyShortPaid")));
        assertTrue(g.state.all().stream().anyMatch(e -> e.type().equals("Arrested")));
    }

    @Test void theWatchDefendsThePlayerAndBooksTheAttackerOnItsOwnWord() {
        Npc guard = (Npc) g.actor(rpg.Ids.GUARD), villager = (Npc) g.actor(rpg.Ids.VILLAGER);
        String jur = guard.homeJurisdiction;
        float[] spot = jur.equals(DevMap.ALPHA) ? new float[]{100.5f, 70.5f} : new float[]{guard.postX, guard.postZ};
        g.teleport(g.player, spot[0], DevMap.FLOOR, spot[1]);
        g.teleport(villager, spot[0] + 1.2f, DevMap.FLOOR, spot[1]);
        g.teleport(guard, spot[0] + 4f, DevMap.FLOOR, spot[1] + 2f);
        villager.homeJurisdiction = jur;
        game.tick(0);
        assertEquals(jur, DevMap.jurisdiction(villager.x, villager.z), "the scene is in the guard's own town");
        villager.eq.set(rpg.item.ItemDef.Slot.MAIN, new rpg.item.ItemStack(rpg.item.Items.get("bronze_sword"), 1));
        g.player.cs.lastAttacker = villager.id; g.player.cs.lastHurt = g.time;
        g.law.onHit(villager, g.player, 0);
        assertTrue(g.law.bounty(villager.id, jur) > 0, "an assault the officer saw is on the books at once");
        assertSame(villager, g.ai.threatFor(guard), "the guard goes for whoever set on the player");
        // and the same for the player striking a townsman without cause
        g.law.closeCase(villager, jur);
        g.player.cs.lastAttacker = 0; guard.fightTarget = 0;
        g.player.eq.set(rpg.item.ItemDef.Slot.MAIN, new rpg.item.ItemStack(rpg.item.Items.get("bronze_sword"), 1));
        villager.cs.lastAttacker = g.player.id; villager.cs.lastHurt = g.time + 0.01;
        g.law.onHit(g.player, villager, 0);
        assertTrue(g.law.bounty(g.player.id, jur) > 0);
        assertSame(g.player, g.ai.threatFor(guard), "the law is applied equally");
    }

    @Test void aVillagerWokenByASoundAtNightGoesToSeeWhatMadeIt() {
        Npc villager = (Npc) g.actor(rpg.Ids.VILLAGER);
        for (Npc o : g.npcs) if (o != villager) g.ai.unload(o);
        g.state.minutes = 23 * 60;
        g.teleport(villager, villager.postX, DevMap.FLOOR, villager.postZ); game.tick(0);
        float sx = villager.x + 9, sz = villager.z;
        g.teleport(g.player, sx, DevMap.FLOOR, sz); g.player.sneaking = true; game.tick(0);
        float closest = 999;
        for (int f = 0; f < 60 * 30; f++) {
            if (f % 120 == 0 && f < 60 * 6) g.noise(g.player, g.player.x, g.player.y, g.player.z, 14, 0.6f);
            game.tick(1f / 60);
            closest = Math.min(closest, villager.distTo(g.player));
        }
        assertTrue(closest < 4.5f, "the villager calls out, then creeps up to look: closest " + closest + " m");
    }

    // ------------------------------------------------------------------ traversal: the hang (Agility 6) and the charge (10)
    private void wall(int height) {
        for (int x = 60; x <= 69; x++) for (int z = 55; z <= 65; z++) for (int y = DevMap.FLOOR; y < DevMap.FLOOR + 6; y++) g.setBlock(x, y, z, rpg.world.Blocks.AIR);
        for (int x = 61; x <= 67; x++) for (int y = 0; y < height; y++) g.setBlock(x, DevMap.FLOOR + y, 59, rpg.world.Blocks.BRICK);
        game.tick(0);
    }
    private void ready(int agility) {
        g.player.skills.setLevel(rpg.skill.Skill.AGILITY, agility); g.player.restore(); g.player.eq.clear();
        for (Npc o : g.npcs) if (o.distTo(64.5f, 60.8f) < 14) g.ai.unload(o);
        g.teleport(g.player, 64.5f, DevMap.FLOOR, 60.8f); g.controller.snapTo(0); game.tick(0);
    }
    private void keys(float fwd, float strafe, boolean jump, boolean sneak, int frames) {
        for (int i = 0; i < frames; i++) {
            var in = g.controller.in; in.forward = fwd; in.strafe = strafe; in.sneak = sneak; in.jump = jump && i == 0; in.sprint = false;
            game.tick(1f / 60);
        }
        g.controller.in.forward = 0; g.controller.in.strafe = 0; g.controller.in.sneak = false;
    }

    @Test void atAgilitySixALedgeIsHeldUntilTheClimbIsPressedAgain() {
        wall(2); ready(6);
        assertTrue(g.traversal.begin(g.player, 0));
        keys(0, 0, false, false, 90);
        assertTrue(g.traversal.hanging(g.player), "the body hangs at the edge");
        assertTrue(g.player.y < DevMap.FLOOR + 1.2f);
        keys(0, 0, true, false, 90);
        assertNull(g.player.traversal);
        assertTrue(g.player.y > DevMap.FLOOR + 1.8f, "pulled up onto the top: y " + g.player.y);
    }

    @Test void atAgilityFiveTheClimbStillGoesStraightUp() {
        wall(2); ready(5);
        assertTrue(g.traversal.begin(g.player, 0));
        keys(0, 0, false, false, 90);
        assertNull(g.player.traversal);
        assertTrue(g.player.y > DevMap.FLOOR + 1.8f);
    }

    @Test void aHangShimmiesAlongTheEdgeAndStopsWhereItEnds() {
        wall(2); ready(6);
        assertTrue(g.traversal.begin(g.player, 0)); keys(0, 0, false, false, 40);
        float x0 = g.player.x;
        keys(0, 1, false, false, 60);
        assertTrue(g.traversal.hanging(g.player));
        assertTrue(g.player.x - x0 > 0.6f, "shimmied right: " + (g.player.x - x0));
        keys(0, 1, false, false, 60 * 6);
        assertTrue(g.player.x < 68f, "the shimmy stops at the wall's end: x " + g.player.x);
    }

    @Test void aHangCanBeLetGoOrLeaptBackFrom() {
        wall(2); ready(20);
        assertTrue(g.traversal.begin(g.player, 0)); keys(0, 0, false, false, 50);
        assertTrue(g.traversal.hanging(g.player));
        keys(0, 0, false, true, 60);
        assertNull(g.player.traversal, "crouch lets go");
        assertTrue(g.player.grounded && g.player.y < DevMap.FLOOR + 0.2f, "dropped to the ground");
        ready(20);
        assertTrue(g.traversal.begin(g.player, 0)); keys(0, 0, false, false, 50);
        float z0 = g.player.z;
        keys(-1, 0, true, false, 40);
        assertNull(g.player.traversal);
        assertTrue(g.player.z - z0 > 1.0f, "leapt away from the wall: " + (g.player.z - z0));
    }

    @Test void crouchWalkingOffAnEdgeLowersOntoItAtAgilitySix() {
        // a platform three blocks up, the player at its rim
        for (int x = 60; x <= 69; x++) for (int z = 55; z <= 65; z++) for (int y = DevMap.FLOOR; y < DevMap.FLOOR + 6; y++) g.setBlock(x, y, z, rpg.world.Blocks.AIR);
        for (int x = 61; x <= 67; x++) for (int z = 56; z <= 59; z++) for (int y = 0; y < 3; y++) g.setBlock(x, DevMap.FLOOR + y, z, rpg.world.Blocks.BRICK);
        game.tick(0);
        g.player.skills.setLevel(rpg.skill.Skill.AGILITY, 6); g.player.restore();
        for (Npc o : g.npcs) if (o.distTo(64.5f, 59.5f) < 14) g.ai.unload(o);
        g.teleport(g.player, 64.5f, DevMap.FLOOR + 3, 59.6f); g.controller.snapTo((float) Math.PI); game.tick(0); keys(0, 0, false, false, 10);
        keys(1, 0, false, true, 70);
        assertNotNull(g.player.traversal, "lowered onto the edge, not fallen");
        assertTrue(g.player.y < DevMap.FLOOR + 2.5f && g.player.y > DevMap.FLOOR + 1.0f, "hanging below the top: " + g.player.y);
    }

    @Test void theShoulderChargeBargesAVillagerAsideAtAgilityTenOnly() {
        for (int level : new int[]{9, 10}) {
            Npc eda = (Npc) g.actor(rpg.Ids.VILLAGER);
            for (Npc o : g.npcs) if (o != eda) g.ai.unload(o);
            g.teleport(g.player, 70.5f, DevMap.FLOOR, 90.5f); g.controller.snapTo(0);
            eda.nextThink = g.time + 30;
            g.player.skills.setLevel(rpg.skill.Skill.AGILITY, level); g.player.restore(); game.tick(0);
            long before = g.state.all().stream().filter(e -> e.type().equals("ShoulderCharge")).count();
            float closest = 99, fastest = 0; boolean sprinted = false;
            for (int i = 0; i < 300; i++) { var in = g.controller.in; in.forward = 1; in.sprint = true; if (i == 132) { g.teleport(eda, g.player.x, DevMap.FLOOR, g.player.z - 5.5f); eda.nextThink = g.time + 30; } game.tick(1f / 60); closest = Math.min(closest, g.player.distTo(eda)); fastest = Math.max(fastest, (float) Math.hypot(g.player.vx, g.player.vz)); sprinted |= g.player.charging; }
            final float c = closest, f = fastest; final boolean sp = sprinted;
            g.controller.in.forward = 0; g.controller.in.sprint = false;
            long after = g.state.all().stream().filter(e -> e.type().equals("ShoulderCharge")).count();
            if (level == 10) assertTrue(after > before, () -> "barged at Agility 10: closest " + c + " fastest " + f + " charging " + sp + " eda at " + eda.x + "," + eda.z + " player " + g.player.x + "," + g.player.z);
            else assertEquals(before, after, "no charge below Agility 10");
            for (int i = 0; i < 400 && eda.reacting; i++) game.tick(1f / 60);
        }
    }

    // ------------------------------------------------------------------ stairs and ladders round the map
    private float climb(float x, float z, float yaw, int seconds) {
        for (Npc o : g.npcs) if (o.distTo(x, z) < 6) g.ai.unload(o);
        g.player.skills.setLevel(rpg.skill.Skill.AGILITY, 1); g.player.restore();
        g.teleport(g.player, x, DevMap.FLOOR, z); g.controller.snapTo(yaw); game.tick(0);
        float top = g.player.y;
        for (int i = 0; i < 60 * seconds; i++) { g.controller.in.forward = 1; game.tick(1f / 60); top = Math.max(top, g.player.y); }
        g.controller.in.forward = 0;
        return top;
    }

    @Test void everyNewFlightOfStairsCanBeWalkedToItsTop() {
        for (int[] f : DevMap.FLIGHTS) {
            float x = f[0] - f[2] + 0.5f, z = f[1] - f[3] + 0.5f, yaw = (float) Math.atan2(-f[2], -f[3]);
            float top = climb(x, z, yaw, 4);
            assertTrue(top > DevMap.FLOOR + f[4] - 1.2f, "flight at " + f[0] + "," + f[1] + " climbed to " + (top - DevMap.FLOOR) + " of " + f[4]);
        }
    }

    @Test void everyNewLadderCanBeClimbedToTheTop() {
        int[][] corners = {{rpg.world.Expanse.PAL_X0, rpg.world.Expanse.PAL_Z0, 1, 1}, {rpg.world.Expanse.PAL_X1, rpg.world.Expanse.PAL_Z0, -1, 1},
                {rpg.world.Expanse.PAL_X0, rpg.world.Expanse.PAL_Z1, 1, -1}, {rpg.world.Expanse.PAL_X1, rpg.world.Expanse.PAL_Z1, -1, -1}};
        for (int[] c : corners) {
            float x = c[0] + 2 * c[2] + 0.5f, z = c[1] + c[3] + 0.5f, yaw = (float) Math.atan2(c[2], 0);
            float top = climb(x, z, yaw, 6);
            assertTrue(top > DevMap.FLOOR + 6.5f, "tower ladder at " + c[0] + "," + c[1] + " reached " + (top - DevMap.FLOOR));
        }
        int[] l = DevMap.LOOKOUT;
        float top = climb(l[0] + l[2] / 2 + 0.5f, l[1] + l[2] + 0.5f, 0, 6);
        assertTrue(top > DevMap.FLOOR + l[3] - 0.3f, "DevTown lookout reached " + (top - DevMap.FLOOR));
    }

    // ------------------------------------------------------------------ dialogue: the universal choices
    @Test void anyoneCanBeAskedTheWayTheDangersTheirOpinionAndTheShortages() {
        Npc eda = (Npc) g.actor(rpg.Ids.VILLAGER);
        var d = g.dialogue.talk(eda);
        // (0.3.0) three or four conversational choices: always the day and the news, the rest from the moment
        for (String ask : new String[]{"How's your day been?", "What have you heard?"})
            assertTrue(d.choices.stream().anyMatch(c -> c.label().equals(ask)), "offered: " + ask);
        long talk = d.choices.stream().filter(c -> !c.label().startsWith("Good day") && !c.label().equals("Goodbye.") && !c.label().startsWith("Let's trade") && !c.label().startsWith("Pay")).count();
        assertTrue(talk >= 3 && talk <= 4, "three or four things to ask: " + d.choices.stream().map(c -> c.label()).toList());
        String way = g.dialogue.way(eda, "the bank", rpg.world.DevMap.BANK_TELLER[0], rpg.world.DevMap.BANK_TELLER[1]);
        assertTrue(way.matches(".*(north|south|east|west).*"), way);
        assertFalse(g.dialogue.dangers(eda).isBlank());
        assertFalse(g.dialogue.shortages(eda).isBlank());
        float before = g.ai.rumors.opinion(eda);
        g.dialogue.word(eda, true);
        assertTrue(g.ai.rumors.opinion(eda) > before, "a kind word is remembered");
        float kinder = g.ai.rumors.opinion(eda);
        g.dialogue.word(eda, true);
        assertEquals(kinder, g.ai.rumors.opinion(eda), 1e-6, "once a day");
        Npc dag = (Npc) g.actor(rpg.Ids.WORKER);
        float was = g.ai.rumors.opinion(dag);
        g.dialogue.word(dag, false);
        assertTrue(g.ai.rumors.opinion(dag) < was, "a jibe is remembered too");
    }

    @Test void anNpcTellsOfTheirActualDay() {
        Npc dag = (Npc) g.actor(rpg.Ids.WORKER);
        g.state.minutes = 9 * 60;
        dag.diary.note(g.state.minutes, "sold 4 logs to Materials stall");
        dag.diary.note(g.state.minutes + 30, "gather Tree #31 -> STARTED");
        dag.diary.note(g.state.minutes + 60, "talked with Eirwen; exchanged 2 pieces of news");
        dag.diary.note(g.state.minutes + 61, "route blocked while work; replanning");
        g.state.minutes += 90;
        String said = g.dialogue.day(dag);
        assertTrue(said.contains("sold 4 logs") && said.contains("Eirwen"), said);
        var entries = dag.diary.today(g.state.minutes);
        assertEquals(3, entries.size(), "the mechanical outcome is not told: " + entries);
        assertTrue(entries.get(0).what().startsWith("sold 4 logs"), entries.toString());
        assertTrue(entries.get(1).what().contains("tree"), entries.toString());
        assertTrue(entries.get(2).what().equals("had a word with Eirwen"), entries.toString());
        // the next morning it starts afresh
        assertTrue(dag.diary.today(g.state.minutes + 1440).isEmpty());
    }
}
