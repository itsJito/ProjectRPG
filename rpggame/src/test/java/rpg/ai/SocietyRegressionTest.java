package rpg.ai;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import org.junit.jupiter.api.*;
import rpg.*;
import rpg.actor.*;
import rpg.item.*;
import rpg.objects.Corpse;
import rpg.world.*;
import static org.junit.jupiter.api.Assertions.*;

class SocietyRegressionTest {
    private Game game; private Rpg g;
    @BeforeEach void open() {
        var cfg = new NativeEngine.Config(); cfg.deterministic = true;
        game = Game.create(cfg); game.activate(DevMap.build(game.engine())); g = game.add(new Rpg(game, "Society", new Appearance(), true));
        for (Npc n : g.npcs) { g.ai.unload(n); n.nextThink = Double.MAX_VALUE; }
        for (int x = 96; x <= 107; x++) for (int z = 64; z <= 78; z++) for (int y = DevMap.FLOOR; y < DevMap.FLOOR + 5; y++) g.setBlock(x, y, z, Blocks.AIR);
        g.state.minutes = 600; g.teleport(g.player, 101.4f, DevMap.FLOOR, 70.5f); game.tick(0);
    }
    @AfterEach void close() { game.close(); }
    private static int meals(Npc n) { int k = 0; for (var grid : n.grids()) for (var s : grid.stacks()) if (s.def.heal > 0) k += s.amount; return k; }
    private Npc load(long id, float x, float z) { Npc n = (Npc) g.actor(id); g.ai.reload(n); g.teleport(n, x, DevMap.FLOOR, z); n.nextThink = Double.MAX_VALUE; game.tick(0); return n; }
    @Test void anAttackedBanditDefendsItselfInsideACityDespiteItsCampLeash() {
        Npc bandit = load(Ids.BANDIT_1, 99.8f, 70.5f); bandit.yaw = (float) -Math.PI / 2;
        bandit.cs.lastAttacker = g.player.id; bandit.cs.lastHurt = g.time;
        g.ai.brain.think(bandit); assertEquals(Brain.Goal.FIGHT, bandit.goal);
        for (int i = 0; i < 120; i++) game.tick(1f / 60);
        assertTrue(g.combat.contacts > 0, bandit.lastOutcome);
    }
    @Test void woundedNeighborsReceiveRealFoodWithoutGainingNpcExperience() {
        // (a wounded player is offered the food instead, to accept or not: the neighbour here is a townswoman)
        Npc helper = load(Ids.GUARD, 100.5f, 70.5f), hurt = load(Ids.VILLAGER, 102.5f, 70.5f); helper.yaw = helper.wantYaw = (float) -Math.PI / 2; helper.aware.clear();
        g.teleport(g.player, 90.5f, DevMap.FLOOR, 60.5f);
        long[] levels = helper.skills.raw().clone(); int food = meals(helper); hurt.hp = 2;
        g.ai.brain.think(helper); assertEquals(Brain.Goal.AID, helper.goal);
        for (int i = 0; i < 150 && meals(helper) == food; i++) game.tick(1f / 60);
        assertTrue(hurt.hp > 2); assertEquals(food - 1, meals(helper)); assertArrayEquals(levels, helper.skills.raw());
    }
    @Test void guardsPhysicallyDeliverAnActualCorpseWithItsOriginalGear() {
        Npc guard = load(Ids.GUARD, 102, 70.5f), dead = load(Ids.VILLAGER, 100.5f, 70.5f);
        g.teleport(g.player, 96.5f, DevMap.FLOOR, 76.5f); g.graveyards.plots.clear();
        g.graveyards.plots.add(new rpg.law.Graveyards.Plot("test plot", DevMap.ALPHA, 104, DevMap.FLOOR, 74, 2, 2));
        ItemStack original = new ItemStack(Items.get("bronze_sword"), 1, dead.id, true, 929); dead.eq.set(ItemDef.Slot.MAIN, original);
        dead.alive = false; dead.hp = 0; g.ai.onKilled(dead, g.player);
        for (int i = 0; i < 90; i++) game.tick(1f / 60);
        Corpse corpse = (Corpse) g.objects.get(dead.corpse); guard.yaw = (float) Math.PI / 2; guard.nextThink = 0;
        for (int i = 0; i < 600 && !corpse.delivered; i++) game.tick(1f / 60);
        assertTrue(corpse.delivered, guard.goal + ": " + guard.lastOutcome + " / " + guard.goalReason);
        assertEquals(0, guard.carryBody); assertEquals(0, corpse.heldBy); assertNull(dead.eq.get(ItemDef.Slot.MAIN));
        var memorial = g.objects.all().stream().filter(o -> o instanceof rpg.objects.Container c && c.kind.equals("memorial") && c.buried && c.burialPlot.equals("test plot")).map(o -> (rpg.objects.Container)o).findFirst().orElseThrow();
        var recovered = memorial.contents.stacks().stream().filter(item -> item.origin == 929).findFirst().orElseThrow();
        assertEquals(original.def, recovered.def); assertEquals(original.owner, recovered.owner); assertEquals(original.stolen, recovered.stolen);
        assertEquals(0, dead.corpse); assertEquals(0, dead.ragdoll); assertTrue(corpse.removed);
        assertEquals("test plot", memorial.burialPlot);
    }
    @Test void aRealNpcTheftIsWitnessedAndReportedThroughTheSameLawPipeline() {
        var stall = g.objects.stall;
        Npc thief = load(Ids.THIEF, stall.x, stall.z + 1.2f), guard = load(Ids.GUARD, stall.x, stall.z - 1.7f);
        guard.yaw = guard.wantYaw = (float) Math.PI;
        g.setBlock((int) stall.x + 1, DevMap.FLOOR + 2, (int) stall.z + 1, Blocks.LAMP); game.tick(0); guard.aware.clear();
        assertTrue(g.ai.canSee(guard, thief), "the lit witness must actually identify the thief before the test begins");
        thief.skills.setLevel(rpg.skill.Skill.THIEVING, 25);
        int before = thief.count(Items.BREAD);
        assertFalse(g.interact.request(thief, stall, "steal").rejected());
        for (int i = 0; i < 80; i++) game.tick(1f / 60);
        assertEquals(before + 1, thief.count(Items.BREAD));
        Memory memory = guard.memories.stream().filter(m -> m.kind.equals("THEFT") && m.subject == thief.id).findFirst().orElseThrow();
        Npc lawman = load(Ids.LAWMAN_A, guard.x + 1.1f, guard.z);
        assertTrue(g.law.report(guard, memory, lawman).committed());
        assertTrue(g.law.bounty(thief.id, DevMap.ALPHA) > 0);
    }
    @Test void incarcerationStopsAnExistingBanditFightAsWellAsTheGuards() {
        Npc bandit = load(Ids.BANDIT_1, 100.5f, 70.5f);
        bandit.fightTarget = g.player.id; bandit.cs.lastAttacker = g.player.id; bandit.cs.lastHurt = g.time;
        g.law.arrest(g.player, DevMap.ALPHA, (Npc) g.actor(Ids.GUARD));
        assertNull(g.ai.threatFor(bandit)); float before = g.player.hp;
        g.combat.resolve(bandit, g.player, 100, 0, ItemDef.DamageType.BLUNT, null, g.player.x, g.player.eyeY(), g.player.z, "old fight", false);
        assertEquals(before, g.player.hp);
    }
}
