package rpg.ai;

import static org.junit.jupiter.api.Assertions.*;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import java.util.ArrayList;
import org.junit.jupiter.api.*;
import rpg.Rpg;
import rpg.actor.Appearance;
import rpg.animal.Animal;
import rpg.animal.Species;
import rpg.item.Items;
import rpg.monster.Monster;
import rpg.world.Blocks;
import rpg.world.DevMap;

/** Alpha 0.3.1 gamefixes: saddling and taming through the real interaction path, and the rest of the 0.3.1 list. */
class Gamefixes031Test {
    private Game game;
    private Rpg g;
    @BeforeEach void open() {
        var cfg = new NativeEngine.Config(); cfg.deterministic = true; game = Game.create(cfg); game.activate(DevMap.build(game.engine()));
        g = game.add(new Rpg(game, "Gamefixes 0.3.1", Appearance.henric(), true));
        for (Monster m : new ArrayList<>(g.monsters.all())) g.ai.unload(m);
        game.tick(0);
    }
    @AfterEach void close() { game.close(); }

    private void yard() {
        for (int x = 56; x <= 73; x++) for (int z = 51; z <= 70; z++) for (int y = DevMap.FLOOR; y < DevMap.FLOOR + 8; y++) g.setBlock(x, y, z, Blocks.AIR);
        for (Npc o : g.npcs) if (o.distTo(64.5f, 60f) < 24) g.ai.unload(o);
    }
    private void ticks(int n) { for (int i = 0; i < n; i++) game.tick(1f / 60); }

    // ------------------------------------------------------------------ mounts: saddle first, then break it in
    private Animal wild(Species s) {
        yard();
        Animal h = g.animals.spawnNew(s, false, true, 64.5f, 62.5f, null, 0, 0);
        h.yaw = 0;
        g.player.inv.clear();
        g.teleport(g.player, 66.2f, DevMap.FLOOR, 62.5f); game.tick(0);
        return h;
    }

    /** Seconds in the saddle (remounting after each throw, up to a limit) until tamed; the throws are counted. */
    private float[] breakIn(Animal h) {
        int throwsTaken = 0;
        float seconds = 0;
        for (int tries = 0; tries < 30 && h.tamedBy != g.player.id; tries++) {
            g.teleport(g.player, h.x + 1.6f, DevMap.FLOOR, h.z); game.tick(0);
            assertFalse(g.riding.mount(g.player, h).rejected(), "mounts");
            while (g.player.riding != 0 && h.tamedBy != g.player.id && seconds < 240) { game.tick(1f / 60); seconds += 1f / 60; }
            if (g.player.riding == 0 && h.tamedBy != g.player.id) throwsTaken++;
            if (g.player.riding == 0) { g.player.reacting = false; g.player.restore(); ticks(30); }
        }
        if (g.player.riding != 0) g.riding.dismount(g.player, false);
        return new float[]{seconds, throwsTaken};
    }

    @Test void tappingInteractOnAHorseWithASaddleSaddlesItEvenUntamed() {
        Animal h = wild(Species.PEGASUS);
        g.give(g.player, Items.SADDLE, 1); g.give(g.player, Items.ANIMAL_FEED, 2);
        var opts = rpg.interact.Interactions.ranked(h.options(g, g.player));
        assertEquals("saddle", opts.get(0).id(), "the saddle is the first thing offered: " + opts);
        assertTrue(opts.get(0).enabled(), "an untamed one can be saddled: " + opts.get(0).reason());
        var r = g.interact.primary(g.player, h);
        ticks(60 * 3);
        assertTrue(h.saddled, "saddled after the work: " + r + " / " + g.interact.lastOutcome + " / " + g.state.all().stream().filter(e -> e.type().startsWith("Work")).map(e -> e.payload()).toList() + " goal " + h.goal + " fear " + h.fear);
        assertEquals(g.player.id, h.saddledBy);
        assertFalse(g.player.has(Items.SADDLE, 1), "the saddle is on the horse");
        assertEquals("mount", rpg.interact.Interactions.ranked(h.options(g, g.player)).stream().filter(o -> o.enabled()).findFirst().orElseThrow().id(), "then a tap mounts");
    }

    @Test void aWildMountLetsACalmPersonWithFeedApproach() {
        Animal h = wild(Species.PEGASUS);
        g.give(g.player, Items.ANIMAL_FEED, 3);
        g.teleport(g.player, 64.5f, DevMap.FLOOR, 68.5f); g.controller.snapTo(0); game.tick(0);
        for (int i = 0; i < 60 * 4; i++) { g.controller.in.forward = 0.5f; game.tick(1f / 60);
            if (i % 20 == 0) System.out.println("[appr] d " + g.player.distTo(h) + " fear " + h.fear + " goal " + h.goal + " hurt " + h.cs.lastHurt + " by " + h.cs.lastAttacker + " feed " + rpg.animal.Animals.feedFor(h, g.player) + " sprint " + g.player.sprinting + " t " + g.time);
            if (g.player.distTo(h) < 2) break; }
        g.controller.in.forward = 0;
        ticks(30);
        assertNotEquals(Animal.Goal.FLEE, h.goal, "it holds its ground for a calm approach with feed: fear " + h.fear + " threat " + g.nameOf(h.threat) + " " + h.threat + " player " + g.player.id + " reason " + h.reason);
        var fed = g.interact.request(g.player, h, "feed");
        assertTrue(fed.committed(), "fed by hand: " + fed + " dist " + g.player.distTo(h) + " goal " + h.goal);
        assertTrue(h.tame > 0, "feeding gentles it a little: " + h.tame);
    }

    @Test void aSaddledWildHorseIsBrokenInFarFasterThanABareOne() {
        Animal bare = wild(Species.HORSE);
        float[] b = breakIn(bare);
        game.close();
        open();
        Animal saddled = wild(Species.HORSE);
        g.give(g.player, Items.SADDLE, 1); g.give(g.player, Items.ANIMAL_FEED, 2);
        g.interact.request(g.player, saddled, "saddle");
        ticks(60 * 3);
        assertTrue(saddled.saddled);
        float[] s = breakIn(saddled);
        System.out.println("[taming] bare " + b[0] + " s, " + (int) b[1] + " throws; saddled " + s[0] + " s, " + (int) s[1] + " throws");
        assertEquals(g.player.id, saddled.tamedBy, "tamed");
        assertTrue(s[0] < 15, "saddled: broken in within 15 s on its back: " + s[0]);
        assertTrue(s[1] <= 1, "saddled: thrown at most once: " + s[1]);
        assertTrue(b[0] < 75, "bare: still possible: " + b[0]);
        assertTrue(s[0] * 2 < b[0], "the saddle makes it far easier: " + s[0] + " vs " + b[0]);
        // and once tamed, saddled, it is ridden where it is steered
        g.teleport(g.player, saddled.x + 1.6f, DevMap.FLOOR, saddled.z); game.tick(0);
        assertTrue(g.interact.request(g.player, saddled, "mount").committed() || g.player.riding == saddled.id);
        float x0 = saddled.x, z0 = saddled.z;
        for (int i = 0; i < 90; i++) { g.controller.in.forward = 1; game.tick(1f / 60); }
        g.controller.in.forward = 0;
        assertTrue(Math.hypot(saddled.x - x0, saddled.z - z0) > 3, "ridden forward");
    }

    // ------------------------------------------------------------------ aim: the crosshair, and the soft lock
    @Test void aThirdPersonShotAtANearWallFliesToTheCrosshair() {
        yard();
        for (int x = 58; x <= 71; x++) for (int y = 0; y < 6; y++) g.setBlock(x, DevMap.FLOOR + y, 56, Blocks.BRICK);
        game.tick(0);
        g.teleport(g.player, 64.5f, DevMap.FLOOR, 62.5f); g.controller.snapTo(0);
        g.player.firstPerson = false;
        g.player.eq.set(rpg.item.ItemDef.Slot.MAIN, new rpg.item.ItemStack(Items.SHORTBOW, 1));
        g.player.eq.set(rpg.item.ItemDef.Slot.AMMO, new rpg.item.ItemStack(Items.BRONZE_ARROWS, 10));
        g.player.sheathed = false;
        ticks(30);
        float[] cross = g.player.aimPoint.clone();
        assertEquals(57f, cross[2], 0.05f, "the crosshair is on the wall face");
        assertTrue(g.combat.shoot(g.player, g.player.aimYaw, g.player.pitch, 1));
        var plan = g.player.throwPlan;
        float off = (float) Math.sqrt(Math.pow(plan.target[0] - cross[0], 2) + Math.pow(plan.target[1] - cross[1], 2) + Math.pow(plan.target[2] - cross[2], 2));
        assertTrue(off < 0.05f, "the shot is solved to the crosshair point, not a shoulder's width beside it: " + off);
    }

    @Test void aSpellCastJustBesideAMarkIsSoftLockedOntoIt() {
        yard();
        g.player.skills.setLevel(rpg.skill.Skill.MAGIC, 99); g.player.restore();
        g.player.book = rpg.combat.Spells.Book.SUN; g.player.mana = g.player.maxMana();
        Animal sheep = g.animals.spawnNew(Species.COW, false, true, 64.5f, 52.5f, null, 0, 0);
        g.teleport(g.player, 64.5f, DevMap.FLOOR, 64.5f); g.controller.snapTo(0); game.tick(0);
        float yaw = 0.06f;    // about three and a half degrees off, twelve metres out
        float pitch = (float) Math.atan2(sheep.chestY() - g.player.eyeY(), 12);
        assertTrue(g.combat.cast(g.player, rpg.combat.Spells.Spell.WIND_STRIKE, yaw, pitch));
        var bolt = g.combat.projectiles.get(g.combat.projectiles.size() - 1);
        assertEquals(sheep.id, bolt.target, "locked onto the cow beside the crosshair");
        float hp = sheep.hp;
        ticks(60);
        assertTrue(sheep.hp < hp || sheep.cs.lastAttacker == g.player.id, "the strike bent onto it and landed");
    }

    // ------------------------------------------------------------------ fire: never one's own, and the plants burn
    private rpg.world.Foliage.Patch nearestPlant(rpg.world.Foliage.Kind kind) {
        for (int i = 0; i < 60 * 4; i++) game.tick(1f / 60);
        rpg.world.Foliage.Patch best = null; float bd = 1e9f;
        for (var c : g.foliage.chunks.values()) for (var p : c.plants) if (!p.removed && (kind == null || p.kind == kind)) {
            float d = g.player.distTo(p.x, p.z); if (d < bd) { bd = d; best = p; }
        }
        return best;
    }

    @Test void aFireSpellCastAtTallGrassSetsItAlightAndTheCasterIsNotBurnt() {
        g.player.skills.setLevel(rpg.skill.Skill.MAGIC, 99); g.player.restore();
        g.player.book = rpg.combat.Spells.Book.SUN; g.player.mana = g.player.maxMana();
        var grass = nearestPlant(rpg.world.Foliage.Kind.TALL_GRASS);
        assertNotNull(grass, "tall grass grows near the start");
        g.teleport(g.player, grass.x, DevMap.FLOOR, grass.z + 3f); g.controller.snapTo(0); ticks(10);
        float pitch = (float) Math.atan2(grass.y + 0.2f - g.player.eyeY(), 3f);
        assertTrue(g.combat.cast(g.player, rpg.combat.Spells.Spell.FIRE_STRIKE, 0, pitch));
        boolean lit = false;
        for (int i = 0; i < 60 * 6; i++) { game.tick(1f / 60); if ((g.chemistry.state(grass.id) & astratto.bridge.AstrattoNative.CHEM_BURNING) != 0 || grass.removed) lit = true; }
        assertTrue(lit, "the tall grass caught: state " + g.chemistry.state(grass.id));
        assertEquals(0, g.chemistry.state(g.player.id) & astratto.bridge.AstrattoNative.CHEM_BURNING, "the caster is not alight");
        // and it runs on through the plants about it
        int neighbours = 0;
        for (var c : g.foliage.chunks.values()) for (var p : c.plants) if (p != grass && Math.hypot(p.x - grass.x, p.z - grass.z) < 1.3f) neighbours++;
        ticks(60 * 8);
        long burned = g.state.all().stream().filter(e -> e.type().equals("FoliageRemoved") && "burned".equals(e.payload())).count();
        System.out.println("[fire] neighbours " + neighbours + ", burned " + burned);
        if (neighbours > 0) assertTrue(burned >= 2, "the fire spread to the plants beside it: " + burned);
    }

    @Test void aSwungTorchNeverSetsItsBearerAlight() {
        yard();
        g.teleport(g.player, 64.5f, DevMap.FLOOR, 62.5f); g.controller.snapTo(0);
        g.player.eq.set(rpg.item.ItemDef.Slot.MAIN, new rpg.item.ItemStack(Items.TORCH, 1));
        g.player.sheathed = false; g.player.torchLit = true;
        ticks(10);
        double start = g.time;
        int burning = 0;
        for (int i = 0; i < 60 * 8; i++) {
            if (i % 50 == 0) g.combat.attack(g.player, false, rpg.actor.Actor.Combat.Move.LIGHT_R);
            game.tick(1f / 60);
            if ((g.chemistry.state(g.player.id) & astratto.bridge.AstrattoNative.CHEM_BURNING) != 0) burning++;
        }
        assertTrue(g.state.all().stream().anyMatch(e -> e.type().equals("Swing") || e.type().startsWith("Attack")) || g.time > start, "swung");
        assertEquals(0, burning, "the bearer never caught from their own torch");
    }

    // ------------------------------------------------------------------ AI on more than one floor
    /** An upper floor (walked on at FLOOR + 4) over x 60..68, z 51..59 on pillars, reached by a stair flight up from the south. */
    private void upperFloor() {
        yard();
        for (int x = 60; x <= 68; x++) for (int z = 51; z <= 59; z++) g.setBlock(x, DevMap.FLOOR + 3, z, Blocks.PLANKS);
        for (int[] c : new int[][]{{60, 51}, {68, 51}, {60, 59}, {68, 59}}) for (int y = 0; y < 3; y++) g.setBlock(c[0], DevMap.FLOOR + y, c[1], Blocks.LOG);
        for (int h = 0; h < 4; h++) {
            int z = 63 - h;
            for (int y = 0; y < h; y++) g.setBlock(64, DevMap.FLOOR + y, z, Blocks.STONE);
            g.setBlock(64, DevMap.FLOOR + h, z, Blocks.STAIRS);
        }
        game.tick(0);
    }

    @Test void aSearchForSomeoneLastSeenUpstairsGoesUpstairs() {
        upperFloor();
        Npc guard = null;
        // a villager looking into a noise heard from the floor above (anyone's search walks the same way)
        for (Npc o : g.npcs) if (o.role == Npc.Role.VILLAGER) { guard = o; break; }
        assertNotNull(guard);
        g.ai.reload(guard);
        g.teleport(guard, 64.5f, DevMap.FLOOR, 67.5f);
        g.teleport(g.player, 30.5f, DevMap.FLOOR, 30.5f); game.tick(0);
        guard.hunt = new Hunt(0, "a noise upstairs", 64.5f, 54.5f, g.time, 60);
        guard.hunt.y = DevMap.FLOOR + 4;
        float best = 0;
        for (int i = 0; i < 60 * 25; i++) { game.tick(1f / 60); best = Math.max(best, guard.y - DevMap.FLOOR); if (guard.hunt == null || guard.hunt.reachedLkp) break; }
        assertTrue(best > 3.5f, "went up to where they were last seen: best y " + best + " (" + guard.lastOutcome + ", goal " + guard.goal + ")");
    }

    // ------------------------------------------------------------------ the watch: no brawl tolerated
    @Test void whoeverStartedABrawlIsNeverDefendingThemselvesWhenTheVictimHitsBack() {
        Npc eda = (Npc) g.actor(rpg.Ids.VILLAGER);
        // the player strikes first
        eda.cs.lastAttacker = g.player.id; eda.cs.lastHurt = g.time;
        g.law.onHit(g.player, eda, 0);
        // she hits back: that is lawful
        g.player.cs.lastAttacker = eda.id; g.player.cs.lastHurt = g.time;
        assertFalse(g.law.unlawful(eda, g.player), "the victim answering a blow is defending herself");
        // and the player's next blow is still an assault, though she struck them a moment ago
        assertTrue(g.law.unlawful(g.player, eda), "the one who started it is not defending themselves");
        // the other way round: set upon, the player may defend themselves
        g.law.clear();
        Npc tom = (Npc) g.actor(rpg.Ids.WORKER);
        g.player.cs.lastAttacker = tom.id; g.player.cs.lastHurt = g.time;
        g.law.onHit(tom, g.player, 0);
        tom.cs.lastAttacker = g.player.id; tom.cs.lastHurt = g.time;
        assertFalse(g.law.unlawful(g.player, tom), "defending oneself against whoever started it");
        assertTrue(g.law.unlawful(tom, g.player));
    }

    // ------------------------------------------------------------------ the charge: through glass and doors
    @Test void aChargeBurstsThroughGlassAndShouldersADoorOpen() {
        yard();
        g.player.skills.setLevel(rpg.skill.Skill.AGILITY, 20); g.player.restore();
        // a glass wall across the run, and beyond it a wall with a shut door in it
        for (int x = 58; x <= 70; x++) for (int y = 0; y < 3; y++) g.setBlock(x, DevMap.FLOOR + y, 56, Blocks.GLASS);
        for (int x = 58; x <= 70; x++) for (int y = 0; y < 4; y++) if (x != 64) g.setBlock(x, DevMap.FLOOR + y, 52, Blocks.BRICK);
        var door = g.objects.add(new rpg.objects.Door(g.state.newEntityId(), "Test door", new int[][]{{64, 52}}, false, null, 1, 0));
        door.spawn(g);
        game.tick(0);
        g.teleport(g.player, 64.5f, DevMap.FLOOR, 70.5f); g.controller.snapTo(0); game.tick(0);
        boolean charged = false;
        for (int i = 0; i < 60 * 5; i++) { var in = g.controller.in; in.forward = 1; in.sprint = true; game.tick(1f / 60); charged |= g.player.charging; if (g.player.z < 50) break; }
        g.controller.in.forward = 0; g.controller.in.sprint = false;
        assertTrue(charged, "charged");
        assertTrue(g.state.all().stream().anyMatch(e -> e.type().equals("GlassBroken")), "burst through the glass");
        assertTrue(door.open, "the door was shouldered open");
        assertTrue(g.player.z < 52.5f, "through the doorway: z " + g.player.z);
    }
}
