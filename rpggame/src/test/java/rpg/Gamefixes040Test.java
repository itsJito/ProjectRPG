package rpg;

import static org.junit.jupiter.api.Assertions.*;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import org.junit.jupiter.api.*;
import rpg.actor.Appearance;
import rpg.ai.Npc;
import rpg.objects.Furniture;
import rpg.world.DevMap;

/** Alpha 0.4.0 gamefixes (the devmap's: ShadowVeil's own are in rpg.world.ShadowVeilTest). */
class Gamefixes040Test {
    private Game game;
    private Rpg g;
    @BeforeEach void open() {
        var cfg = new NativeEngine.Config(); cfg.deterministic = true; game = Game.create(cfg); game.activate(DevMap.build(game.engine()));
        g = game.add(new Rpg(game, "Gamefixes 0.4.0", Appearance.henric(), true));
        game.tick(0);
    }
    @AfterEach void close() { game.close(); }

    private Furniture aTable() {
        for (Furniture f : g.objects.furniture) if (f.kind == Furniture.Kind.TABLE && f.placed) return f;
        throw new AssertionError("the devmap has a table");
    }

    // ------------------------------------------------------------------ struck from hiding: no name to put to it
    @Test void aShotFromOutOfSightLeavesTheVictimSearchingNotBlamingThePlayer() {
        Npc cole = (Npc) g.actor(Ids.GUARD);
        // the player well behind the guard, the guard facing away
        g.teleport(g.player, cole.x, DevMap.FLOOR, cole.z + 14);
        cole.yaw = 0;                          // forward is -z: the player at +z is behind it
        for (int i = 0; i < 10; i++) game.tick(1 / 60f);
        assertFalse(g.ai.knowsStriker(cole, g.player, true), "a shot from behind, out of sight, comes from no one the guard can name");
        cole.cs.lastAttacker = g.player.id; cole.cs.lastHurt = g.time; cole.cs.attackerKnown = false;
        g.ai.onAttacked(cole, g.player);
        assertNotEquals(g.player.id, cole.fightTarget, "the guard does not set on the player for a shot it never saw");
        assertNotNull(cole.hunt, "it searches for whoever did it");
        assertEquals(0, cole.hunt.target, "the search is for someone unknown");
        // the archer slips away out of sight: the guard turns toward the shot and finds no one to name
        g.teleport(g.player, cole.x + 70, DevMap.FLOOR, cole.z + 40);
        for (int i = 0; i < 30; i++) game.tick(1 / 60f);
        assertNotEquals(g.player.id, cole.fightTarget, "nor does it decide on the player a moment later");
        // a blow at arm's length is another matter
        g.teleport(g.player, cole.x, DevMap.FLOOR, cole.z + 1.2f);
        assertTrue(g.ai.knowsStriker(cole, g.player, false), "a blow from beside is felt from its striker");
    }

    // ------------------------------------------------------------------ no one walks on the furniture
    @Test void aTableTopIsNoFloorForARoute() {
        Furniture t = aTable();
        float x = t.x0 + 0.5f, z = t.z0 + 0.5f;
        assertFalse(g.vox.navigation.validPoint(x, DevMap.FLOOR + 1, z, d -> true), "the top of a table is not a place to walk");
        assertTrue(g.vox.navigation.validPoint(x, DevMap.FLOOR, z - 2, d -> true) || g.vox.navigation.validPoint(x - 2, DevMap.FLOOR, z, d -> true) || g.vox.navigation.validPoint(x + 2, DevMap.FLOOR, z, d -> true),
                "the floor beside it is");
    }

    @Test void aBodyMadeOnAPieceOfFurnitureIsSetDownBesideIt() {
        Furniture t = aTable();
        Npc eda = (Npc) g.actor(Ids.VILLAGER);
        g.ai.despawnBody(eda);
        eda.x = t.x0 + 0.5f; eda.z = t.z0 + 0.5f; eda.y = DevMap.FLOOR;
        g.ai.spawnBody(eda);
        int bx = (int) Math.floor(eda.x), bz = (int) Math.floor(eda.z), by = (int) Math.floor(eda.y);
        assertFalse(g.vox.solid(bx, by, bz) || g.vox.solid(bx, by + 1, bz), "made in open air");
        assertFalse(rpg.world.Blocks.objectCell(g.vox.block(bx, by - 1, bz)), "standing on the floor, not on the table");
    }

    // ------------------------------------------------------------------ getting down from the saddle
    @Test void steppingDownFromAHorseTakesItsTimeAndEndsOnTheGroundBesideIt() {
        g.teleport(g.player, 63.0f, DevMap.FLOOR, 62.5f);
        var h = g.animals.spawnNew(rpg.animal.Species.HORSE, false, true, 64.5f, 62.5f, null, 0, 0);
        h.tame = 1; h.tamedBy = g.player.id; h.owner = g.player.id; h.saddled = true;
        for (int i = 0; i < 5; i++) game.tick(1 / 60f);
        g.riding.mount(g.player, h); g.riding.seatNow(g.player);
        for (int i = 0; i < 20; i++) game.tick(1 / 60f);
        assertEquals(h.id, g.player.riding);
        g.riding.stepDown(g.player);
        game.tick(1 / 60f);
        assertEquals(h.id, g.player.riding, "a moment in, still on the way down");
        assertTrue(g.player.mountPhase < 1, "the climb played backward");
        for (int i = 0; i < 150 && g.player.riding != 0; i++) game.tick(1 / 60f);
        assertEquals(0, g.player.riding, "down within a couple of seconds");
        assertTrue(Math.abs(g.player.y - DevMap.FLOOR) < 0.3f, "on the ground");
        assertTrue(g.player.distTo(h) < h.radius + 1.6f, "beside the horse");
    }
}
