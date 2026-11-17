package rpg.ai;

import static org.junit.jupiter.api.Assertions.*;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import java.util.ArrayList;
import org.junit.jupiter.api.*;
import rpg.Rpg;
import rpg.actor.Appearance;
import rpg.actor.Swimming;
import rpg.animal.Animal;
import rpg.animal.Species;
import rpg.fishing.Fish;
import rpg.fishing.FishKind;
import rpg.monster.Monster;
import rpg.mount.MountStats;
import rpg.mount.Riding;
import rpg.skill.Skill;
import rpg.world.Blocks;
import rpg.world.DevMap;
import rpg.world.Foliage;

/** Alpha 0.3.6 gamefixes. */
class Gamefixes036Test {
    private Game game;
    private Rpg g;
    @BeforeEach void open() {
        var cfg = new NativeEngine.Config(); cfg.deterministic = true; game = Game.create(cfg); game.activate(DevMap.build(game.engine()));
        g = game.add(new Rpg(game, "Gamefixes 0.3.6", Appearance.henric(), true));
        for (Monster m : new ArrayList<>(g.monsters.all())) g.ai.unload(m);
        g.state.minutes = 13 * 60;
        game.tick(0);
    }
    @AfterEach void close() { game.close(); }
    private void ticks(int n) { for (int i = 0; i < n; i++) game.tick(1f / 60); }
    private void clear(int x0, int z0, int x1, int z1) {
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) {
            for (int y = DevMap.FLOOR; y < DevMap.FLOOR + 10; y++) g.setBlock(x, y, z, Blocks.AIR);
            g.setBlock(x, DevMap.FLOOR - 1, z, Blocks.GRASS);
        }
    }

    // ------------------------------------------------------------------ deer
    @Test void deerAreNoFasterThanAGallopingHorseAndWaterHoldsEveryBodyBack() {
        assertTrue(Species.DEER.run < MountStats.horse(1).gallop, "a deer runs slower than a horse gallops");
        assertTrue(Species.DEER.run <= Species.MOUNTAIN_LION.run * 1.18f, "a lion at full stretch can run a deer down");
        Animal deer = g.animals.spawnNew(Species.DEER, false, true, 60.5f, 60.5f, null, 0, 0);
        deer.running = true; deer.swimMode = Swimming.SWIM;
        float rate = waterPace(deer);
        assertTrue(rate * Species.DEER.run <= Species.DEER.swim + 1e-3f, "afloat, a deer goes no faster than it swims: " + rate * Species.DEER.run);
        deer.swimMode = Swimming.WADE;
        assertTrue(waterPace(deer) * Species.DEER.run <= Species.DEER.walk * Species.WADE_PACE + 1e-3f, "wading, no faster than a walk and a half");
        deer.swimMode = Swimming.NONE;
        assertEquals(1f, waterPace(deer), 1e-6f, "on land, its own pace");
    }
    private static float waterPace(Animal a) {
        try { var m = rpg.animal.Animals.class.getDeclaredMethod("waterPace", Animal.class); m.setAccessible(true); return (float) m.invoke(null, a); }
        catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }

    // ------------------------------------------------------------------ fish
    private Fish oneOf(FishKind k) {
        g.teleport(g.player, 129.5f, DevMap.FLOOR, 214.5f);
        ticks(10);
        Fish f = g.fishery.release(k, 129.5f, 236.5f);
        assertNotNull(f, "a " + k.title + " goes into the deep off the pier");
        return f;
    }

    @Test void spawnPutsAFishInTheNearestWaterAndSlayKillsIt() {
        Fish shark = oneOf(FishKind.SHARK);
        assertTrue(g.fluids.in(shark.x, shark.y, shark.z), "in the water");
        assertTrue(g.fishery.live.contains(shark));
        int stock = shark.water.stock[FishKind.SHARK.ordinal()];
        g.fishery.slay(shark, g.player);
        assertFalse(shark.alive);
        assertEquals(stock - 1, shark.water.stock[FishKind.SHARK.ordinal()], "out of its water's stock");
        assertTrue(g.state.all().stream().anyMatch(e -> e.type().equals("FishKilled")));
        float[] dry = dryLand();
        assertNotNull(dry, "somewhere on the map is 13 m from any water");
        assertNull(g.fishery.release(FishKind.SHARK, dry[0], dry[1]), "no fish where there is no water");
    }

    private float[] dryLand() {
        for (int x = 20; x < DevMap.SZ - 20; x += 7) for (int z = 20; z < DevMap.SZ - 20; z += 7) {
            boolean wet = false;
            for (int dx = -13; dx <= 13 && !wet; dx++) for (int dz = -13; dz <= 13 && !wet; dz++)
                wet = !Float.isNaN(g.fluids.surface(x + dx + 0.5f, DevMap.FLOOR + 2, z + dz + 0.5f));
            if (!wet) return new float[]{x + 0.5f, z + 0.5f};
        }
        return null;
    }

    @Test void aFishIsSomethingInTheWorldWithAHealthBarAndABodyARayMeets() {
        Fish whale = oneOf(FishKind.WHALE);
        assertTrue(g.interact.all().contains(whale), "fish can be looked at");
        assertEquals(FishKind.WHALE.health, whale.maxHp(), 1e-6f);
        assertEquals(FishKind.WHALE.combatLevel(), whale.levelTag());
        // a line along the whale's flank, near its tail, meets the body (not only its middle)
        float ax = (float) -Math.sin(whale.yaw), az = (float) -Math.cos(whale.yaw), tail = whale.length() * 0.4f;
        float ox = whale.x - ax * tail - az * 4, oz = whale.z - az * tail + ax * 4;
        float dx = az, dz = -ax;
        float[] at = new float[3];
        assertSame(whale, g.fishery.ray(ox, whale.y, oz, dx, 0, dz, 10, 0, at), "the tail is part of the body");
        assertTrue(whale.focusEntry(ox, whale.y, oz, dx, 0, dz) < 10);
        assertTrue(g.fishery.live.stream().allMatch(f -> f.id >= Fish.ID_BASE), "fish ids are their own");
    }

    @Test void fishCannotPassThroughOneAnotherNorThroughASwimmer() {
        Fish a = oneOf(FishKind.TUNA), b = g.fishery.release(FishKind.TUNA, 129.5f, 236.5f);
        b.x = a.x + 0.05f; b.y = a.y; b.z = a.z; b.yaw = a.yaw;
        ticks(2);
        float[] gap = {b.x - a.x, b.y - a.y, b.z - a.z};
        assertTrue(Math.sqrt(gap[0] * gap[0] + gap[1] * gap[1] + gap[2] * gap[2]) > 0.2f, "pushed apart");
        // a swimmer set into a whale is shouldered out of it
        Fish whale = g.fishery.release(FishKind.WHALE, 129.5f, 240.5f);
        g.teleport(g.player, whale.x, whale.y - 0.6f, whale.z);
        ticks(30);
        float d = (float) Math.hypot(g.player.x - whale.x, g.player.z - whale.z);
        assertTrue(d > 0.5f || Math.abs(g.player.y + 0.6f - whale.y) > whale.girth(), "out of the whale: " + d);
    }

    @Test void aWhaleStruckBySwimmerTurnsOnThemAndOneOnThePierItDivesFrom() {
        Fish whale = oneOf(FishKind.WHALE);
        g.teleport(g.player, whale.x + 3, whale.y, whale.z);
        ticks(30);
        assertNotEquals(0, g.player.swimMode, "the player is in the water");
        g.fishery.struck(g.player, whale, 5, true, false, null, whale.x, whale.y, whale.z);
        assertEquals(Fish.State.DEFEND, whale.state, "a whale fights back");
        float hp = g.player.hp;
        for (int i = 0; i < 600 && g.player.hp >= hp; i++) { ticks(1); whale.biteAt = Math.min(whale.biteAt, g.time + 0.2); }
        assertTrue(g.player.hp < hp || g.state.all().stream().anyMatch(e -> e.type().equals("FishMissed")), "it strikes at the swimmer");
        assertSame(whale, g.fishery.foeOf(g.player), "the target frame knows it");
        // from the pier it cannot be reached: it sounds and goes
        Fish other = g.fishery.release(FishKind.WHALE, 120.5f, 240.5f);
        g.teleport(g.player, 128.5f, DevMap.FLOOR, 228.5f);
        ticks(5);
        g.fishery.struck(g.player, other, 5, false, false, Skill.ARCHERY, other.x, other.y, other.z);
        float d0 = (float) Math.hypot(other.x - g.player.x, other.z - g.player.z);
        ticks(240);
        assertTrue(Math.hypot(other.x - g.player.x, other.z - g.player.z) > d0, "it makes off");
    }

    // ------------------------------------------------------------------ tamed animals
    @Test void aTamedAnimalNeverTurnsOnItsOwner() {
        Animal horse = g.animals.spawnNew(Species.HORSE, false, true, g.player.x + 3, g.player.z, null, 0, 0);
        horse.tamedBy = g.player.id; horse.owner = g.player.id;
        horse.cs.lastAttacker = g.player.id; horse.cs.lastHurt = g.time;
        g.animals.onHurt(horse, g.player);
        assertNotEquals(g.player.id, horse.threat, "its owner is no threat, even after a blow");
        ticks(120);
        assertNotEquals(Animal.Goal.DEFEND, horse.goal);
        assertTrue(horse.tamedTo(g.player.id));
    }

    // ------------------------------------------------------------------ riding
    private Riding.Ride saddle(Species s, float x, float z, float yaw) {
        Animal a = g.animals.spawnNew(s, false, true, x, z, null, 0, 0);
        a.tamedBy = g.player.id; a.owner = g.player.id; a.saddled = true; a.yaw = yaw;
        g.teleport(g.player, x + 1.5f, DevMap.FLOOR, z);
        ticks(5);
        assertTrue(g.riding.mount(g.player, a).committed()); g.riding.seatNow(g.player);
        return g.riding.of(g.player);
    }

    @Test void aHorseGallopingIntoAWallGoesDownAndThrowsItsRider() {
        clear(40, 40, 70, 70);
        for (int z = 40; z <= 70; z++) for (int y = DevMap.FLOOR; y < DevMap.FLOOR + 4; y++) g.setBlock(44, y, z, Blocks.STONE);
        Riding.Ride r = saddle(Species.HORSE, 62.5f, 55.5f, (float) (Math.PI / 2));   // facing -x, toward the wall
        r.speed = Riding.stats(r.mount).gallop;
        var in = g.controller.in;
        in.forward = 1; in.sprint = true;
        for (int i = 0; i < 300 && g.player.riding != 0; i++) ticks(1);
        in.forward = 0; in.sprint = false;
        assertEquals(0, g.player.riding, "thrown from the saddle");
        assertTrue(g.state.all().stream().anyMatch(e -> e.type().equals("MountCrashed")), "a crash");
        assertNotNull(r.mount.ragdoll, "the horse goes down");
    }

    @Test void aPegasusClimbsOnForwardAndBanksTheWayItTurns() {
        clear(30, 30, 90, 90);
        Riding.Ride r = saddle(Species.PEGASUS, 60.5f, 60.5f, 0);
        r.flying = true; r.airspeed = 14; r.pitch = 0; r.body.y = DevMap.FLOOR + 20; r.body.grounded = false;
        var in = g.controller.in;
        in.forward = 1; in.strafe = 1;
        float yaw0 = r.body.yaw;
        ticks(40);
        assertTrue(r.pitch > 0.2f, "forward climbs: " + r.pitch);
        assertTrue(r.bank > 0.3f, "banked right: " + r.bank);
        assertTrue(rpg.actor.Actor.wrap(r.body.yaw - yaw0) < -0.05f, "and turning right");
        in.forward = -1; in.strafe = -1;
        ticks(60);
        assertTrue(r.pitch < -0.2f, "back dives: " + r.pitch);
        assertTrue(r.bank < -0.3f, "banked left the same: " + r.bank);
        in.forward = 0; in.strafe = 0;
    }

    @Test void aMountPushesThroughLeavesRatherThanStickingInThem() {
        clear(40, 40, 70, 70);
        for (int x = 50; x <= 52; x++) for (int z = 50; z <= 60; z++) for (int y = DevMap.FLOOR + 1; y < DevMap.FLOOR + 3; y++) g.setBlock(x, y, z, Blocks.OAK_LEAVES);
        Riding.Ride r = saddle(Species.PEGASUS, 60.5f, 55.5f, (float) (Math.PI / 2));
        var in = g.controller.in;
        in.forward = 1;
        ticks(360);
        in.forward = 0;
        assertTrue(r.mount.x < 49, "through the low canopy: " + r.mount.x);
    }

    // ------------------------------------------------------------------ water
    @Test void aHeadUnderTheWaterIsUnderTheWater() {
        g.teleport(g.player, 129.5f, DevMap.FLOOR - 2.6f, 236.5f);
        ticks(20);
        assertTrue(g.swimming.headUnder(g.player, g.player.x, g.player.y, g.player.z), "under");
        float o = g.player.oxygen;
        ticks(60);
        assertTrue(g.player.oxygen >= 0 && g.player.oxygen < Swimming.lungs(g.player), "the breath runs out: " + o + " -> " + g.player.oxygen);
    }

    @Test void thePlayerWalksOutOfTheWellOnTheirOwnFeet() {
        g.teleport(g.player, DevMap.WELL[0] + 0.5f, DevMap.FLOOR, DevMap.WELL[1] + 0.5f);
        ticks(20);
        assertFalse(g.swimming.active(g.player), "a well a block deep is waded, not swum");
        assertEquals(Swimming.WADE, g.player.swimMode);
        var in = g.controller.in;
        g.controller.camYaw = 0;
        in.forward = 1; in.jump = true; in.jumpHeld = true;
        float startY = g.player.y;
        for (int i = 0; i < 120 && Math.hypot(g.player.x - DevMap.WELL[0] - 0.5f, g.player.z - DevMap.WELL[1] - 0.5f) < 1.6f; i++) ticks(1);
        in.forward = 0; in.jump = false; in.jumpHeld = false;
        ticks(30);
        assertTrue(Math.hypot(g.player.x - DevMap.WELL[0] - 0.5f, g.player.z - DevMap.WELL[1] - 0.5f) >= 1.4f, "out over the rim");
        assertTrue(g.player.y >= startY - 0.05f && g.player.y < DevMap.FLOOR + 1.2f, "on the ground, not flung: " + g.player.y);
        assertEquals(Swimming.NONE, g.player.swimMode);
    }

    // ------------------------------------------------------------------ the watch
    @Test void theWatchChasesSomeoneOffPastItsBorderThenGoesBack() {
        Npc guard = null;
        for (Npc n : g.npcs) if (n.alive && n.loaded && n.role == Npc.Role.GUARD && DevMap.jurisdiction(n.x, n.z).equals(n.homeJurisdiction)) { guard = n; break; }
        assertNotNull(guard);
        var ai = g.ai;
        // inside: no question
        assertTrue(ai.chaseOff(guard, g.player) || !DevMap.jurisdiction(g.player.x, g.player.z).equals(guard.homeJurisdiction));
        // the player steps over the border: the guard may follow a while
        float[] out = Waypoints.nearestOutside(guard.x, guard.z);
        g.teleport(g.player, out[0], DevMap.FLOOR, out[1]);
        assertFalse(DevMap.jurisdiction(g.player.x, g.player.z).equals(guard.homeJurisdiction));
        assertTrue(ai.chaseOff(guard, g.player), "seen off past the border");
        // far past the border or long after, it turns back
        guard.chaseOffX = guard.x - AiSystem.CHASE_OFF_REACH - 1;
        assertFalse(ai.chaseOff(guard, g.player), "not beyond its reach");
        assertTrue(g.state.all().stream().anyMatch(e -> e.type().equals("ChasedOff")));
        assertEquals(Brain.Goal.RETURN, guard.goal);
        assertFalse(ai.chaseOff(guard, g.player), "not again until they come back in");
    }

    // ------------------------------------------------------------------ bodies
    @Test void townsfolkReactToABodyAsTheyPassItAndWalkRoundIt() {
        Npc victim = null, passer = null;
        for (Npc n : g.npcs) if (n.alive && n.loaded && n.role == Npc.Role.MERCHANT) { if (victim == null) victim = n; else if (passer == null && n != victim) passer = n; }
        if (passer == null) for (Npc n : g.npcs) if (n.alive && n.loaded && n != victim && !n.watch() && n.role != Npc.Role.UNDERTAKER && !n.hostileToPlayer) { passer = n; break; }
        assertNotNull(victim); assertNotNull(passer);
        clear(56, 56, 76, 66);
        g.teleport(victim, 66.5f, DevMap.FLOOR, 61.5f);
        ticks(5);
        g.combat.bleedOutNow(victim);
        ticks(60);
        assertFalse(victim.alive);
        // the passer walks a line straight over where the body lies
        g.teleport(passer, 58.5f, DevMap.FLOOR, 61.5f);
        passer.goal = Brain.Goal.ERRAND; passer.nextThink = g.time + 60;
        float closest = Float.MAX_VALUE;
        for (int i = 0; i < 600; i++) {
            g.ai.desire(passer, AiSystem.A_GOTO);
            if (i % 30 == 0) g.game.commands().agentGoal(passer.agent, 74.5f, DevMap.FLOOR, 61.5f);
            passer.nextThink = g.time + 60;
            ticks(1);
            var pose = victim.ragdoll != 0 ? g.lastBodies.ragdoll(victim.ragdoll) : null;
            float bx = pose != null ? pose.comX : victim.x, bz = pose != null ? pose.comZ : victim.z;
            closest = Math.min(closest, (float) Math.hypot(passer.x - bx, passer.z - bz));
            if (passer.x > 72) break;
        }
        assertTrue(passer.x > 70, "it got past: " + passer.x);
        assertTrue(closest > 0.6f, "it gave the body a berth: " + closest);
        g.gore.perceive(passer);
        ticks(1);
        assertTrue(g.state.all().stream().anyMatch(e -> e.type().equals("BodyPassed") || e.type().equals("EvidenceFound")), "it reacts to the body");
    }

    // ------------------------------------------------------------------ fire and the plants
    @Test void aSpellPathMeetsTheGrowthItFliesInto() {
        g.foliage.preload(g.player.x, g.player.z, 1);
        Foliage.Patch bush = null;
        for (int r = 0; r < 4 && bush == null; r++) for (var o : g.objects.all()) if (o instanceof Foliage.Patch p && !p.removed && p.kind.dense) { bush = p; break; }
        assumeBush(bush);
        var hits = g.foliage.along(bush.x - 3, bush.y + 0.4f, bush.z, 1, 0, 0, 6);
        assertFalse(hits.isEmpty(), "the line meets the bush");
        assertSame(bush, hits.get(0)[1]);
        assertEquals(3 - bush.kind.radius, (float) hits.get(0)[0], 0.35f);
        final Foliage.Patch target = bush;
        assertTrue(g.foliage.along(bush.x - 3, bush.y + bush.kind.height + 1, bush.z, 1, 0, 0, 6).stream().noneMatch(h -> h[1] == target), "a line over it misses it");
    }
    private static void assumeBush(Object bush) { Assumptions.assumeTrue(bush != null, "no dense plant grows near the start"); }

    // ------------------------------------------------------------------ the fence
    @Test void theFenceTradesWithACustomerWhoseBladeIsOut() {
        Npc f = null; for (Npc n : g.npcs) if (n.id == rpg.Ids.FENCE) f = n;
        assertNotNull(f);
        g.state.minutes = 7 * 60 + 20; ticks(60);
        for (int i = 0; i < 60 * 120 && !(f.working() && f.distTo(g.player) < 99 && Math.hypot(f.x - f.postX, f.z - f.postZ) < 1.5); i++) game.tick(1f / 60);
        assertTrue(f.working(), "at his stall in the morning");
        g.teleport(g.player, f.x + 1.4f, f.y, f.z);
        assertTrue(g.combat.drawn(g.player) || g.combat.sheathe(g.player, false), "the customer's blade is out");
        ticks(60 * 12);   // long past the moment a drawn blade is answered
        assertTrue(f.goal != Brain.Goal.HIDE && f.goal != Brain.Goal.FLEE, "he does not hide from a customer: " + f.goal);
        assertNull(g.econ.tradeRefusal(f, g.player), "and trades");
    }
}
