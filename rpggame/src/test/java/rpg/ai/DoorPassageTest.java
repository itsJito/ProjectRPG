package rpg.ai;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import org.junit.jupiter.api.Test;
import rpg.Ids;
import rpg.Rpg;
import rpg.actor.Appearance;
import rpg.objects.Door;
import rpg.world.DevMap;
import static org.junit.jupiter.api.Assertions.*;

/** Doors (gamefixes): people open them on their way through, and an open leaf swings fully either way on its hinge. */
class DoorPassageTest {
    private static Door door(Rpg g, int cellX, int cellZ) {
        for (Door d : g.objects.doors()) for (int[] c : d.cells) if (c[0] == cellX && c[1] == cellZ) return d;
        throw new AssertionError("no door at " + cellX + "," + cellZ);
    }

    @Test void aVillagerWalksThroughHerShutHouseDoorAndItClosesBehindHer() {
        var config = new NativeEngine.Config(); config.deterministic = true;
        try (Game game = Game.create(config)) {
            game.activate(DevMap.build(game.engine()));
            Rpg g = game.add(new Rpg(game, "Door passage", new Appearance(), true));
            Npc eda = (Npc) g.actor(Ids.VILLAGER);
            for (Npc n : g.npcs) if (n != eda) g.ai.unload(n);
            g.teleport(g.player, 70, DevMap.FLOOR, 70);
            Door d = door(g, 103, DevMap.HOUSE2_Z0);
            assertFalse(d.open);
            // outside the house's front, sent in through the shut door
            g.teleport(eda, 103.9f, DevMap.FLOOR, DevMap.HOUSE2_Z0 - 4.5f);
            eda.spawnX = 104f; eda.spawnZ = DevMap.HOUSE2_Z0 + 4.5f;
            boolean wasOpened = false;
            for (int frame = 0; frame < 60 * 25 && Math.hypot(eda.x - eda.spawnX, eda.z - eda.spawnZ) > 2; frame++) {
                eda.goal = Brain.Goal.RETURN; eda.nextThink = g.time + 5;
                assertTrue(game.tick(1f / 60));
                wasOpened |= d.open;
            }
            assertTrue(wasOpened, "the door was opened for her");
            assertTrue(Math.hypot(eda.x - eda.spawnX, eda.z - eda.spawnZ) <= 2, () -> String.format("Eda reached the house's inside (at %.2f, %.2f; %s; %s; stuck %s)", eda.x, eda.z, eda.lastOutcome, eda.nativeAction, eda.stuck));
        }
    }

    @Test void aShopkeeperUnlocksHerOwnStoreRoomOnTheWayInAndLocksItBehindHer() {
        var config = new NativeEngine.Config(); config.deterministic = true;
        try (Game game = Game.create(config)) {
            game.activate(DevMap.build(game.engine()));
            Rpg g = game.add(new Rpg(game, "Locked door", new Appearance(), true));
            Npc ada = (Npc) g.actor(Ids.MERCHANT_A);
            for (Npc n : g.npcs) if (n != ada) g.ai.unload(n);
            g.teleport(g.player, 70, DevMap.FLOOR, 70);
            Door store = door(g, DevMap.STORE_DOOR[0][0], DevMap.STORE_DOOR[0][1]);
            assertTrue(store.locked && store.owner == ada.id);
            g.teleport(ada, 55.5f, DevMap.FLOOR, 15f);
            ada.spawnX = 60.5f; ada.spawnZ = 18.5f;
            boolean unlocked = false;
            for (int frame = 0; frame < 60 * 25 && Math.hypot(ada.x - ada.spawnX, ada.z - ada.spawnZ) > 2; frame++) {
                ada.goal = Brain.Goal.RETURN; ada.nextThink = g.time + 5;
                assertTrue(game.tick(1f / 60));
                unlocked |= !store.locked;
            }
            assertTrue(unlocked, "she unlocked her store room");
            assertTrue(Math.hypot(ada.x - ada.spawnX, ada.z - ada.spawnZ) <= 2, () -> String.format("Ada got into the store room (at %.2f, %.2f; %s)", ada.x, ada.z, ada.lastOutcome));
            for (int frame = 0; frame < 60 * 12 && !store.locked; frame++) { ada.goal = Brain.Goal.RETURN; ada.nextThink = g.time + 5; assertTrue(game.tick(1f / 60)); }
            assertTrue(store.locked && !store.open, "locked again behind her");
        }
    }

    @Test void anOpenLeafSwingsIntoTheRoomPastSquareWhicheverWayThePlayerWalksThrough() {
        var config = new NativeEngine.Config(); config.deterministic = true;
        try (Game game = Game.create(config)) {
            game.activate(DevMap.build(game.engine()));
            Rpg g = game.add(new Rpg(game, "Door swing", new Appearance(), true));
            for (Npc n : g.npcs) g.ai.unload(n);
            Door d = door(g, 103, DevMap.HOUSE2_Z0);
            float[] reached = new float[2];
            for (int pass = 0; pass < 2; pass++) {
                // in through the door from the street, then back out from the inside
                float fromZ = pass == 0 ? DevMap.HOUSE2_Z0 - 3 : DevMap.HOUSE2_Z0 + 3, yaw = pass == 0 ? (float) Math.PI : 0;
                g.teleport(g.player, 103.5f, DevMap.FLOOR, fromZ);
                g.controller.snapTo(yaw);
                assertTrue(game.tick(1f / 60));
                if (!d.open) assertTrue(g.interact.request(g.player, d, "open").committed());
                for (int f = 0; f < 30; f++) assertTrue(game.tick(1f / 60));
                g.controller.in.forward = 1;
                float most = 0;
                for (int f = 0; f < 150; f++) { assertTrue(game.tick(1f / 60)); for (float a : d.angle) most = Math.max(most, Math.abs(a)); }
                g.controller.in.forward = 0;
                reached[pass] = most;
            }
            // into the room a little past square whichever side it was opened from (gamefixes: a door opens one way), never further
            assertTrue(reached[0] > 1.5f && reached[1] > 1.5f && reached[0] <= Door.SWING + 0.05f && reached[1] <= Door.SWING + 0.05f, "the leaves swung " + reached[0] + " and " + reached[1] + " rad");
        }
    }
}
