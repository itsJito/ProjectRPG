package rpg.animal;

import static org.junit.jupiter.api.Assertions.*;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import org.junit.jupiter.api.*;
import rpg.Rpg;
import rpg.actor.Appearance;
import rpg.world.Blocks;
import rpg.world.DevMap;

/** Gamefixes: some animals, a cow among them, could not go into water. A cow led to the middle of the duck pond wades in. */
class WadingTest {
    private Game game;
    private Rpg g;
    @BeforeEach void open() {
        var cfg = new NativeEngine.Config(); cfg.deterministic = true;
        game = Game.create(cfg); game.activate(DevMap.build(game.engine()));
        g = game.add(new Rpg(game, "Wade", new Appearance(), true));
        g.state.minutes = 600;
    }
    @AfterEach void close() { game.close(); }

    private boolean inWater(Animal a) {
        int bx = (int) Math.floor(a.x), bz = (int) Math.floor(a.z);
        return g.vox.block(bx, (int) Math.floor(a.y - 0.2f), bz) == Blocks.WATER || g.vox.block(bx, (int) Math.floor(a.y + 0.2f), bz) == Blocks.WATER
                || g.vox.block(bx, DevMap.FLOOR - 1, bz) == Blocks.WATER;
    }

    private void lead(Animal a, float x, float z) {
        a.nextThink = g.time + 10; a.goal = Animal.Goal.WANDER; a.hasGoal = true; a.gx = x; a.gz = z; a.goalSince = g.time;
    }

    @Test void aCowWadesIntoThePond() {
        Animal cow = g.animals.all().stream().filter(a -> a.species == Species.COW && !a.baby).findFirst().orElseThrow();
        float cx = (DevMap.DUCK_POND_X0 + DevMap.DUCK_POND_X1) / 2f + 0.5f, cz = (DevMap.DUCK_POND_Z0 + DevMap.DUCK_POND_Z1) / 2f + 0.5f;
        g.teleport(cow, DevMap.DUCK_POND_X0 - 1.5f, DevMap.FLOOR, cz);
        game.tick(0);
        int wet = 0;
        for (int f = 0; f < 60 * 20; f++) {
            lead(cow, cx, cz);
            game.tick(1f / 60);
            if (inWater(cow)) wet++;
        }
        System.out.printf("cow at (%.2f, %.2f, y %.2f), pond centre (%.1f, %.1f); %d frames in the water, goal %s%n", cow.x, cow.z, cow.y, cx, cz, wet, cow.goal);
        // control: the same cow led over dry ground
        float sx = cow.x, sz = cow.z;
        for (int f = 0; f < 60 * 8; f++) { lead(cow, sx, sz - 6); game.tick(1f / 60); }
        System.out.printf("dry control: moved %.2f m (stuck %s, routed %s, agent %d)%n", Math.hypot(cow.x - sx, cow.z - sz), cow.stuck, cow.routed, cow.agent);
        assertTrue(wet > 60, "the cow never got into the water: at " + cow.x + ", " + cow.z);
    }

    @Test void ducksTakeToTheirPondOfTheirOwnAccord() {
        java.util.List<Animal> ducks = g.animals.all().stream().filter(a -> a.species == Species.DUCK && !a.baby && a.alive).toList();
        Assumptions.assumeFalse(ducks.isEmpty());
        java.util.Set<Long> swam = new java.util.HashSet<>();
        for (int f = 0; f < 60 * 120; f++) { game.tick(1f / 60); if (f % 30 == 0) for (Animal d : ducks) if (inWater(d)) swam.add(d.id); }
        System.out.println(swam.size() + " of " + ducks.size() + " ducks went on the water in two minutes");
        assertFalse(swam.isEmpty(), "no duck went on the water");
    }
}
