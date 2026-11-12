package rpg.world;

import static org.junit.jupiter.api.Assertions.*;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import rpg.Rpg;
import rpg.actor.Appearance;
import rpg.ai.Npc;

/** Alpha 0.4.1: the way home - posts, beds and stairs on the devmap (see Gamefixes041Test for ShadowVeil). */
class Gamefixes041PostsTest {
    /** (0.4.1) Since furniture stopped being something to walk over (0.4.0), a candelabrum on the porter's post, an armour
     *  stand at the foot of the mansion's upper stair and the kitchen stools shut people out of their own houses, and a bed
     *  - not standable - sent everyone going to sleep to the roof above it. No one's post is furniture, and the mansion's
     *  three floors are one walk from the hall. */
    @Test void everyonesPostIsFloorAndTheMansionStairsAreOpen() {
        var config = new NativeEngine.Config(); config.deterministic = true;
        try (Game dev = Game.create(config)) {
            dev.activate(DevMap.build(dev.engine()));
            Rpg d = dev.add(new Rpg(dev, "Posts", new Appearance(), true));
            dev.tick(1f / 60);
            List<String> bad = new ArrayList<>();
            for (Npc n : d.npcs) if (n.alive && n.loaded && !Float.isFinite(d.vox.navigation.standY(n.spawnX, n.y, n.spawnZ)))
                bad.add(n.name + " at (" + n.spawnX + ", " + n.spawnZ + ")");
            for (Npc n : d.npcs) if (n.alive && n.loaded) {
                float s = d.vox.navigation.standY(n.spawnX, n.y, n.spawnZ);
                if (Float.isFinite(s) && Math.abs(s - n.y) > 1.2f) bad.add(n.name + " stands on something at (" + n.spawnX + ", " + n.spawnZ + ")");
            }
            assertTrue(bad.isEmpty(), "posts on furniture or in the air: " + bad);
            float hall = d.vox.navigation.standY(45.5f, DevMap.FLOOR, 150.5f);
            float[][] floors = {{45.5f, Mansion.LEVELS[1], 143.5f}, {38.5f, Mansion.LEVELS[2], 146.5f}};
            for (float[] t : floors) {
                float ty = d.vox.navigation.standY(t[0], t[1], t[2]);
                assertTrue(Float.isFinite(ty) && Math.abs(ty - t[1]) < 1.2f, "no floor at " + java.util.Arrays.toString(t) + " (" + ty + ")");
                assertNotNull(d.vox.navigation.route(46.5f, hall, 138.5f, t[0], ty, t[2], door -> true, false, 0, null, 0),
                        "no way from the mansion's hall up to " + java.util.Arrays.toString(t));
            }
        }
    }
}
