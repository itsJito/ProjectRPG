package rpg.animal;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import org.junit.jupiter.api.Test;
import rpg.Rpg;
import rpg.actor.Appearance;
import rpg.world.DevMap;
import static org.junit.jupiter.api.Assertions.*;

class AnimalDeathTest {
    @Test void killingAKnockedDownAnimalKeepsItsPhysicalBodyAndDisablesRecovery() {
        var cfg = new NativeEngine.Config(); cfg.deterministic = true;
        try (Game game = Game.create(cfg)) {
            game.activate(DevMap.build(game.engine()));
            Rpg g = game.add(new Rpg(game, "Animal death", new Appearance(), true));
            Animal a = g.animals.release(Species.COW, false, false, 64.5f, 66.5f);
            g.animals.struck(a, 1, 0, 70, true);
            assertNotNull(a.ragdoll);
            AnimalRagdoll fallen = a.ragdoll;
            a.amp = 0.6f; a.flap = 1; a.rearing = true;
            a.alive = false; a.hp = 0;
            g.animals.onKilled(a, g.player, 0);
            assertSame(fallen, a.ragdoll, "death must not replace a fallen body with a fresh upright one");
            assertFalse(a.ragdoll.knockdown);
            assertEquals(0, a.amp); assertEquals(0, a.flap); assertFalse(a.rearing);
            for (int i = 0; i < 240; i++) assertTrue(game.tick(1f / 60));
            assertSame(fallen, a.ragdoll, "dead animals never return to their living animation");
            assertEquals(0, a.body);
        }
    }
}
