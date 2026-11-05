package rpg.ai;

import static org.junit.jupiter.api.Assertions.*;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import java.util.ArrayList;
import org.junit.jupiter.api.*;
import rpg.Ids;
import rpg.Rpg;
import rpg.actor.Appearance;
import rpg.actor.Swimming;
import rpg.animal.Animal;
import rpg.animal.Species;
import rpg.item.ItemStack;
import rpg.item.Items;
import rpg.monster.Monster;
import rpg.skill.Skill;
import rpg.world.Blocks;
import rpg.world.DevMap;

/** Alpha 0.3.4 gamefixes. */
class Gamefixes034Test {
    private Game game;
    private Rpg g;
    @BeforeEach void open() {
        var cfg = new NativeEngine.Config(); cfg.deterministic = true; game = Game.create(cfg); game.activate(DevMap.build(game.engine()));
        g = game.add(new Rpg(game, "Gamefixes 0.3.4", Appearance.henric(), true));
        for (Monster m : new ArrayList<>(g.monsters.all())) g.ai.unload(m);
        g.state.minutes = 13 * 60;
        game.tick(0);
    }
    @AfterEach void close() { game.close(); }
    private void ticks(int n) { for (int i = 0; i < n; i++) game.tick(1f / 60); }
    private void yard() {
        for (int x = 52; x <= 77; x++) for (int z = 40; z <= 92; z++) for (int y = DevMap.FLOOR; y < DevMap.FLOOR + 12; y++) g.setBlock(x, y, z, Blocks.AIR);
        for (Npc o : g.npcs) if (o.distTo(64.5f, 66f) < 34) g.ai.unload(o);
    }
    /** A pool in the yard: water this many blocks deep (its surface at FLOOR - 0.1), solid round and under it. */
    private void pool(int x0, int z0, int x1, int z1, int depth) {
        for (int x = x0 - 1; x <= x1 + 1; x++) for (int z = z0 - 1; z <= z1 + 1; z++) for (int y = DevMap.FLOOR - depth - 1; y < DevMap.FLOOR; y++) g.setBlock(x, y, z, Blocks.STONE);
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) for (int y = DevMap.FLOOR - depth; y < DevMap.FLOOR; y++) g.setBlock(x, y, z, Blocks.WATER);
    }
    private void keys(float fwd, float strafe, boolean sprint, boolean jump, boolean sneak) {
        var in = g.controller.in; in.forward = fwd; in.strafe = strafe; in.sprint = sprint; in.jumpHeld = jump; in.sneak = sneak;
    }

    // ------------------------------------------------------------------ one smashed window is one vandalism charge
    @Test void smashingAWholeWindowIsOneVandalismChargeNotOnePerPane() {
        Npc bram = (Npc) g.actor(Ids.LAWMAN_A);
        g.teleport(g.player, bram.x + 2, DevMap.FLOOR, bram.z);
        ticks(5);
        for (int i = 0; i < 20; i++) g.law.crime(g.player, "VANDALISM", 0, 6, null, 0);
        // every witness reports what they saw to the lawman standing there
        for (Npc n : g.npcs) for (Memory m : new ArrayList<>(n.memories)) if (m.kind.equals("VANDALISM") && !m.reported) g.law.report(n, m, n.watch() && n.homeJurisdiction.equals(DevMap.ALPHA) ? n : bram);
        int b = g.law.bounty(g.player.id, DevMap.ALPHA);
        assertTrue(b <= 10, "twenty panes in one spell of damage are one offence: bounty " + b);
    }

    // ------------------------------------------------------------------ nobody trades with the one they are running from
    @Test void aMerchantRunningFromThePlayerWillNotTrade() {
        Npc ada = g.econ.merchant(g.econ.shopA);
        g.state.minutes = 1440 * 2 + 10 * 60;
        ada.fearOf = g.player.id; ada.fearSeenAt = g.time; ada.goal = Brain.Goal.FLEE;
        assertTrue(ada.shunning(g, g.player));
        assertNotNull(g.econ.tradeRefusal(ada, g.player), "a fleeing merchant refuses trade");
        assertTrue(ada.options(g, g.player).stream().noneMatch(o -> o.id().equals("talk")), "nor stops to talk");
    }

    // ------------------------------------------------------------------ a mount let go of in the air falls; one landing on an edge does not sink in
    private Animal saddled(Species s, float x, float y, float z) {
        Animal h = g.animals.spawnNew(s, false, true, x, z, null, 0, 0);
        g.teleport(h, x, y, z);
        h.tamedBy = g.player.id; h.owner = g.player.id; h.saddled = true; h.yaw = 0;
        return h;
    }

    @Test void aMountDismountedInMidAirFallsToTheGround() {
        yard();
        g.teleport(g.player, 66.5f, DevMap.FLOOR, 74.5f); ticks(3);
        Animal h = saddled(Species.PEGASUS, 64.5f, DevMap.FLOOR, 74.5f); ticks(10);
        assertTrue(g.riding.mount(g.player, h).committed());
        var r = g.riding.of(g.player);
        r.body.y = DevMap.FLOOR + 3; r.body.vy = 0; r.body.grounded = false;
        ticks(1);
        g.riding.dismount(g.player, false);
        ticks(60 * 3);
        assertTrue(h.y < DevMap.FLOOR + 0.5f, "the mount came down instead of hanging in the air: y " + h.y);
        float x0 = h.x, z0 = h.z;
        h.nextThink = 0;
        ticks(60 * 4);
        assertTrue(h.ragdoll == null && h.alive, "on its feet");
        assertTrue(Math.hypot(h.x - x0, h.z - z0) >= 0 && h.y < DevMap.FLOOR + 0.5f);
    }

    @Test void aMountComingDownOnABlockEdgeRestsOnItInsteadOfSinkingIn() {
        yard();
        var body = new rpg.mount.MountController(0.5f, 0.6f, 1.5f);
        g.setBlock(64, DevMap.FLOOR, 66, Blocks.STONE);
        body.x = 64.5f + 0.95f; body.z = 66.5f; body.y = DevMap.FLOOR + 1.6f; body.yaw = (float) (Math.PI / 2); body.grounded = false; body.vy = -4;
        for (int i = 0; i < 60; i++) body.move(g.vox, 0, 0, body.yaw, 1f / 60);
        assertTrue(body.fits(g.vox, body.x, body.y, body.z, body.yaw), "never left inside a block: y " + body.y);
    }

    // ------------------------------------------------------------------ swimming
    @Test void aBodyFloatsInDeepWaterAndStandsInShallowWater() {
        yard(); pool(60, 60, 68, 70, 3); pool(70, 60, 74, 70, 1);
        g.teleport(g.player, 64.5f, DevMap.FLOOR - 0.5f, 65.5f);
        keys(0, 0, false, false, false);
        ticks(60 * 6);
        assertTrue(g.swimming.active(g.player), "the water has the body");
        float surface = DevMap.FLOOR - 1 + rpg.world.Fluids.TOP;
        float eye = g.player.eyeY();
        assertTrue(eye > surface, "afloat with the head out: eye " + eye + " surface " + surface + " y " + g.player.y);
        assertTrue(g.player.y < surface, "but the body is in the water: y " + g.player.y);
        g.teleport(g.player, 72.5f, DevMap.FLOOR - 0.9f, 65.5f);
        ticks(60 * 3);
        assertEquals(DevMap.FLOOR - 1, g.player.y, 0.08f, "a block deep: standing on the bottom");
    }

    @Test void sprintSwimmingIsFasterAndDiagonalsAreNoFaster() {
        yard(); pool(56, 50, 74, 88, 3);
        g.teleport(g.player, 64.5f, DevMap.FLOOR - 2.2f, 84.5f);
        g.controller.camYaw = 0; g.controller.camPitch = 0;
        keys(1, 0, false, false, false); ticks(60);
        float z0 = g.player.z; ticks(60 * 2);
        float slow = (z0 - g.player.z) / 2;
        g.teleport(g.player, 64.5f, DevMap.FLOOR - 2.2f, 84.5f); ticks(2);
        keys(1, 0, true, false, false); ticks(60);
        z0 = g.player.z; ticks(60 * 2);
        float fast = (z0 - g.player.z) / 2;
        assertTrue(g.swimming.player.swimming, "a sprint well under the water is swimming");
        assertTrue(fast > slow * 1.2f, "sprint swimming is faster: " + fast + " vs " + slow);
        g.teleport(g.player, 64.5f, DevMap.FLOOR - 2.2f, 84.5f); ticks(2);
        keys(1, 1, false, false, false); ticks(60);
        float x0 = g.player.x; z0 = g.player.z; ticks(60 * 2);
        float diag = (float) Math.hypot(g.player.x - x0, g.player.z - z0) / 2;
        assertTrue(diag <= slow * 1.08f, "a diagonal is no faster: " + diag + " vs " + slow);
        keys(0, 0, false, false, false);
    }

    @Test void lookingDownWhileSwimmingDivesAndLookingUpClimbs() {
        yard(); pool(56, 50, 74, 88, 3);
        g.teleport(g.player, 64.5f, DevMap.FLOOR - 1.4f, 84.5f);
        g.controller.camYaw = 0; g.controller.camPitch = -0.9f;
        keys(1, 0, true, false, false); ticks(60);
        float y0 = g.player.y; ticks(30);
        assertTrue(g.player.y < y0 - 0.2f || g.player.y <= DevMap.FLOOR - 3 + 0.05f, "pitched down it goes down: " + y0 + " -> " + g.player.y);
        g.controller.camPitch = 0.9f; ticks(10);
        y0 = g.player.y; ticks(30);
        assertTrue(g.player.y > y0 + 0.2f, "pitched up it climbs: " + y0 + " -> " + g.player.y);
        keys(0, 0, false, false, false);
    }

    @Test void aSwimmerClimbsOutOverALowBankButNotUpAnUnderwaterWall() {
        yard(); pool(60, 60, 68, 70, 2);
        g.teleport(g.player, 64.5f, DevMap.FLOOR - 1.2f, 61.5f);
        g.controller.camYaw = 0; g.controller.camPitch = 0;   // facing -z: the bank at z 59
        keys(1, 0, false, true, false);
        boolean out = false;
        for (int i = 0; i < 60 * 8 && !out; i++) { game.tick(1f / 60); out = !g.swimming.active(g.player) && g.player.z < 60 && g.player.y >= DevMap.FLOOR - 0.05f; }
        keys(0, 0, false, false, false);
        assertTrue(out, "out onto the bank: at " + g.player.x + ", " + g.player.y + ", " + g.player.z);
        // an underwater wall: a pillar standing in the deep with water over its top gives no lift
        yard(); pool(60, 60, 68, 70, 3);
        for (int y = DevMap.FLOOR - 3; y < DevMap.FLOOR - 1; y++) for (int x = 60; x <= 68; x++) g.setBlock(x, y, 63, Blocks.STONE);
        g.teleport(g.player, 64.5f, DevMap.FLOOR - 3, 64.6f); ticks(2);
        keys(1, 0, false, false, true);
        float best = -99;
        for (int i = 0; i < 60 * 2; i++) { game.tick(1f / 60); best = Math.max(best, (float) g.swimming.player.vy); }
        keys(0, 0, false, false, false);
        assertTrue(best < Swimming.EXIT_LIFT * 0.9f, "no lift up a wall with water over it: best vy " + best);
    }

    @Test void underWaterTooLongTheBreathRunsOutAndItHurts() {
        yard(); pool(56, 50, 74, 88, 3);
        g.teleport(g.player, 64.5f, DevMap.FLOOR - 3, 70.5f);
        keys(0, 0, false, false, true);
        float lungs = Swimming.lungs(g.player);
        ticks((int) (60 * (lungs * 0.5f)));
        assertTrue(g.player.oxygen < lungs * 0.7f && g.player.oxygen > 0, "breath spent under water: " + g.player.oxygen);
        float hp = g.player.hp;
        ticks((int) (60 * (lungs * 0.5f + 4)));
        assertTrue(g.player.hp < hp, "out of breath, the water hurts: " + hp + " -> " + g.player.hp);
        keys(0, 0, false, false, false);
    }

    @Test void runningWaterCarriesASwimmerDownstream() {
        g.teleport(g.player, DevMap.STREAM_X0 + 1f, DevMap.FLOOR - 0.9f, DevMap.STREAM_Z0 + 4f);
        keys(0, 0, false, false, false);
        ticks(20);
        float z0 = g.player.z;
        ticks(60 * 3);
        assertTrue(g.player.z > z0 + 0.6f, "the stream carries the body south: " + z0 + " -> " + g.player.z);
    }

    @Test void wolvesSwimAndChickensDoNot() {
        assertTrue(Species.WOLF.swims && Species.DUCK.swims);
        assertFalse(Species.CHICKEN.swims);
        yard(); pool(56, 50, 74, 88, 3);
        Animal w = g.animals.spawnNew(Species.WOLF, false, true, 64.5f, 70.5f, null, 0, 0);
        g.teleport(w, 64.5f, DevMap.FLOOR - 3, 70.5f);
        ticks(60 * 3);
        float surface = DevMap.FLOOR - 1 + rpg.world.Fluids.TOP;
        assertTrue(w.y > surface - w.height, "the wolf swims at the surface: y " + w.y);
        assertEquals(Swimming.SWIM, w.swimMode);
    }

    // ------------------------------------------------------------------ the deer and the duck
    @Test void aDeerIsModelledAndGivesVenisonAndABucksAntlers() {
        assertNotNull(rpg.render.AnimalModels.of(Species.DEER, 1).head, "the deer has a figure");
        assertTrue(Species.BROWN_BEAR.prey.contains(Species.DEER), "the bear hunts deer");
        Animal buck = g.animals.spawnNew(Species.DEER, false, false, 30.5f, 30.5f, null, 0, 0);
        assertEquals(1, buck.variant, "a buck wears antlers");
        var y = buck.yields();
        assertTrue(y.stream().anyMatch(st -> st.def == Items.RAW_VENISON), "venison: " + y);
        assertTrue(y.stream().anyMatch(st -> st.def == Items.ANTLER), "antlers: " + y);
        Animal doe = g.animals.spawnNew(Species.DEER, false, true, 31.5f, 30.5f, null, 0, 0);
        assertTrue(doe.yields().stream().noneMatch(st -> st.def == Items.ANTLER), "a doe has none");
        Animal duck = g.animals.spawnNew(Species.DUCK, false, true, 32.5f, 30.5f, null, 0, 0);
        assertTrue(duck.yields().stream().anyMatch(st -> st.def == Items.RAW_DUCK) && duck.yields().stream().anyMatch(st -> st.def == Items.FEATHER));
        assertTrue(g.animals.all().stream().anyMatch(a -> a.species == Species.DEER && a.home == g.animals.southWood), "deer live in the southern wood");
    }

    @Test void aHungryBearHuntsADeer() {
        yard();
        Animal bear = g.animals.spawnNew(Species.BROWN_BEAR, false, true, 60.5f, 60.5f, null, 0, 0);
        Animal deer = g.animals.spawnNew(Species.DEER, false, true, 66.5f, 64.5f, null, 0, 0);
        g.teleport(g.player, 30.5f, DevMap.FLOOR, 30.5f);
        bear.hunger = 0.95f;
        boolean hunting = false;
        for (int i = 0; i < 60 * 8 && !hunting; i++) { bear.hunger = 0.95f; game.tick(1f / 60); hunting = bear.goal == Animal.Goal.HUNT && bear.other == deer.id; }
        assertTrue(hunting, "the bear goes after the deer: " + bear.goal + " " + bear.reason);
    }

    @Test void aDuckSitsOnItsPond() {
        Animal duck = g.animals.spawnNew(Species.DUCK, false, true, DevMap.DUCK_POND_X0 + 2.5f, DevMap.DUCK_POND_Z0 + 2.5f, null, 0, 0);
        g.teleport(duck, DevMap.DUCK_POND_X0 + 2.5f, DevMap.FLOOR - 1, DevMap.DUCK_POND_Z0 + 2.5f);
        g.teleport(g.player, DevMap.DUCK_POND_X0 - 4f, DevMap.FLOOR, DevMap.DUCK_POND_Z0);
        ticks(60 * 2);
        float surface = DevMap.FLOOR - 1 + rpg.world.Fluids.TOP;
        if (g.fluids.in(duck.x, duck.y + 0.05f, duck.z)) assertTrue(duck.y > surface - duck.height * 0.5f, "the duck rides on the water: y " + duck.y + " surface " + surface);
    }

    // ------------------------------------------------------------------ Follow, Stay, Dismiss
    @Test void aTamedDogStaysFollowsAndIsDismissedButStaysTame() {
        yard();
        g.teleport(g.player, 64.5f, DevMap.FLOOR, 66.5f);
        Animal dog = g.animals.spawnNew(Species.DOG, false, true, 65.5f, 66.5f, null, 0, 0);
        dog.owner = g.player.id; dog.tamedBy = g.player.id; dog.trust.put(g.player.id, 1f);
        ticks(10);
        assertTrue(dog.options(g, g.player).stream().anyMatch(o -> o.id().equals("cmd_stay")));
        assertTrue(g.animals.command(dog, g.player, "stay", g.state.newRequestId()).committed());
        float sx = dog.x, sz = dog.z;
        g.teleport(g.player, 64.5f, DevMap.FLOOR, 82.5f);
        ticks(60 * 8);
        assertTrue(Math.hypot(dog.x - sx, dog.z - sz) < 2.0, "it stayed: moved " + Math.hypot(dog.x - sx, dog.z - sz));
        g.teleport(g.player, 64.5f, DevMap.FLOOR, 74.5f);
        assertTrue(g.animals.command(dog, g.player, "follow", g.state.newRequestId()).committed());
        g.teleport(g.player, 64.5f, DevMap.FLOOR, 84.5f);
        ticks(60 * 10);
        assertTrue(dog.distTo(g.player) < 5, "it came along: " + dog.distTo(g.player) + " " + dog.goal + " " + dog.reason);
        assertTrue(g.animals.command(dog, g.player, "dismiss", g.state.newRequestId()).committed());
        assertEquals(Animal.Command.NONE, dog.command);
        assertEquals(g.player.id, dog.owner, "dismissed, it is still yours");
        assertTrue(dog.answersTo(g.player));
    }

    // ------------------------------------------------------------------ ale and the glass bottle
    @Test void aleLendsFiveStrengthThatEbbsAndLeavesTheBottle() {
        int base = g.player.skills.level(Skill.STRENGTH);
        g.player.store(new ItemStack(Items.ALE, 1));
        int slot = -1;
        for (int i = 0; i < g.player.inv.size(); i++) if (g.player.inv.get(i) != null && g.player.inv.get(i).def == Items.ALE) slot = i;
        assertTrue(slot >= 0);
        assertTrue(g.items.perform(g.player, slot, "drink").committed());
        assertEquals(base + 5, g.player.skills.level(Skill.STRENGTH), "five levels lent");
        assertTrue(g.player.has(Items.GLASS_BOTTLE, 1), "the empty bottle is kept");
        ticks(60 * 60);
        int later = g.player.skills.level(Skill.STRENGTH);
        assertTrue(later < base + 5 && later >= base, "it ebbs: " + later);
        ticks(60 * 240);
        assertEquals(base, g.player.skills.level(Skill.STRENGTH), "and is gone");
        assertEquals(base, g.player.skills.baseLevel(Skill.STRENGTH));
    }

    @Test void aBrokenBottleDrawsAttentionToWhereItBroke() {
        Npc n = (Npc) g.actor(Ids.GUARD);
        g.teleport(g.player, n.x + 18, DevMap.FLOOR, n.z);
        ticks(3);
        g.glass.bottle(g.player, n.x + 6, DevMap.FLOOR + 0.1f, n.z, 0, -1, 0);
        assertTrue(g.state.all().stream().anyMatch(e -> e.type().equals("BottleBroken")));
        var aware = n.aware.get(g.player.id);
        assertNotNull(aware, "the guard heard it");
        assertTrue(Math.hypot(aware.x - (n.x + 6), aware.z - n.z) < 3, "and thinks it came from where the bottle broke: " + aware.x + "," + aware.z);
        assertTrue(rpg.item.Recipes.byId("brew_ale") != null);
        assertEquals(2, Items.ALE.gh); assertEquals(1, Items.ALE.gw);
    }
}
