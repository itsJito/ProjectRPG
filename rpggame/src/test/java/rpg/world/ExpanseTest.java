package rpg.world;

import static org.junit.jupiter.api.Assertions.*;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import org.junit.jupiter.api.Test;
import rpg.Ids;
import rpg.Rpg;
import rpg.actor.Appearance;
import rpg.ai.Npc;

/**
 * Gamefixes 0.2.0: the devmap is the real map's size (192 x 192 blocks, 12 x 12 chunks, 100 high), with DevPalace and
 * its twenty residents, and six newcomers of callings nobody else holds. Everyone stands on open ground in the world.
 */
class ExpanseTest {
    @Test void theMapIsTheRealSizeAndDevPalaceIsPeopled() {
        assertEquals(192, DevMap.SX); assertEquals(192, DevMap.SZ); assertEquals(100, DevMap.SY);
        assertEquals(36_864, DevMap.SX * DevMap.SZ);
        var cfg = new NativeEngine.Config(); cfg.deterministic = true;
        try (Game game = Game.create(cfg)) {
            game.activate(DevMap.build(game.engine()));
            Rpg g = game.add(new Rpg(game, "Expanse", new Appearance(), true));
            long palace = g.npcs.stream().filter(n -> n.id >= Ids.EMPEROR && n.id <= Ids.CENTURION).count();
            assertEquals(20, palace, "DevPalace's own twenty");
            for (Npc n : g.npcs) if (n.id >= Ids.EMPEROR && n.id <= Ids.CENTURION) assertEquals(DevMap.PALACE, n.homeJurisdiction, n.name);
            java.util.Set<rpg.ai.Profession> others = new java.util.HashSet<>();
            for (Npc n : g.npcs) if (n.profession != null && n.id < Ids.CARPENTER_CITY && (n.id < Ids.ARTHUR || n.id > Ids.MERLIN)) others.add(n.profession);
            java.util.Set<rpg.ai.Profession> six = new java.util.HashSet<>();
            for (long id = Ids.ARTHUR; id <= Ids.MERLIN; id++) {
                Npc n = (Npc) g.actor(id);
                assertNotNull(n, "newcomer " + id);
                assertFalse(DevMap.PALACE.equals(DevMap.jurisdiction(n.x, n.z)), n.name + " lives outside DevPalace");
                assertFalse(others.contains(n.profession), n.name + "'s calling (" + n.profession + ") was already held on the map");
                assertTrue(six.add(n.profession), n.name + "'s calling overlaps another newcomer's");
            }
            // nobody spawned inside a wall
            for (Npc n : g.npcs) if (n.alive && n.loaded) {
                int bx = (int) Math.floor(n.x), bz = (int) Math.floor(n.z);
                assertFalse(g.vox.solid(bx, DevMap.FLOOR, bz) && g.vox.solid(bx, DevMap.FLOOR + 1, bz), n.name + " stands in a wall at " + bx + "," + bz);
            }
            // the lake's spots stand in its water
            for (float[] at : Expanse.LAKE_SPOTS) assertEquals(Blocks.WATER, g.vox.block((int) at[0], DevMap.FLOOR - 1, (int) at[1]), "lake spot at " + at[0] + "," + at[1]);
            for (int i = 0; i < 120; i++) assertTrue(game.tick(1f / 60));
        }
    }
}
