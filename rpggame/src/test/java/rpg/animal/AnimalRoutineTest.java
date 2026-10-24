package rpg.animal;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import org.junit.jupiter.api.Test;
import rpg.Rpg;
import rpg.actor.Appearance;
import rpg.world.DevMap;
import static org.junit.jupiter.api.Assertions.*;

class AnimalRoutineTest {
    @Test void familiarFedAnimalsCareAndPlayButUrgentNeedsAndDangerTakePriority() {
        var cfg = new NativeEngine.Config(); cfg.deterministic = true;
        try (Game game = Game.create(cfg)) {
            game.activate(DevMap.build(game.engine()));
            Rpg g = game.add(new Rpg(game, "Animal routines", new Appearance(), true));
            g.state.minutes = 18 * 60;
            Animal cat = g.animals.release(Species.CAT, false, false, 64.5f, 66.5f);
            Animal dog = g.animals.release(Species.DOG, false, false, 64.5f, 67.5f);
            g.teleport(g.player, 64.5f, DevMap.FLOOR, 68.5f);
            for (Animal a : new Animal[]{cat, dog}) {
                a.goal = Animal.Goal.GRAZE; a.other = 0; a.owner = g.player.id; a.hunger = a.thirst = 0.15f; a.fatigue = a.fear = 0; a.careAgain = 0;
                g.animals.mind.think(a);
            }
            assertEquals(Animal.Goal.GROOM, cat.goal);
            assertEquals(Animal.Goal.PLAY, dog.goal);
            dog.thirst = 0.95f; g.animals.mind.think(dog);
            assertEquals(Animal.Goal.DRINK, dog.goal);
            cat.threat = g.player.id; cat.fear = 0.9f; cat.cs.lastAttacker = g.player.id; cat.cs.lastHurt = g.time;
            g.animals.mind.think(cat);
            assertNotEquals(Animal.Goal.GROOM, cat.goal);
        }
    }
}
