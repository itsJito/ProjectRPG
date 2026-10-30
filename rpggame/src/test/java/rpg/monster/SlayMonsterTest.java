package rpg.monster;

import static org.junit.jupiter.api.Assertions.*;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import java.util.ArrayList;
import org.junit.jupiter.api.*;
import rpg.Rpg;
import rpg.actor.Appearance;
import rpg.world.DevMap;

/** Gamefixes: the slay command did not work on monsters - a monster was never in the sights (0.2.1: examining the world is gone; the monster is still in the sights). */
class SlayMonsterTest {
    private Game game;
    private Rpg g;

    @BeforeEach void open() {
        var cfg = new NativeEngine.Config(); cfg.deterministic = true;
        game = Game.create(cfg); game.activate(DevMap.build(game.engine()));
        g = game.add(new Rpg(game, "Slay", new Appearance(), true));
        for (Monster m : new ArrayList<>(g.monsters.all())) g.ai.unload(m);
    }
    @AfterEach void close() { game.close(); }

    @Test void aMonsterInTheSightsCanBeSlain() {
        float[] lane = rpg.TestLanes.open(g);
        g.teleport(g.player, lane[0], DevMap.FLOOR, lane[1]);
        Monster goblin = g.monsters.spawn(MonsterKind.GOBLIN, 2, lane[0], lane[1] - 3, null);
        game.tick(1f / 60);
        float eye = g.player.eyeY();
        g.interact.computeFocus(g.player, g.player.x, eye, g.player.z, goblin.x - g.player.x, goblin.chestY() - eye, goblin.z - g.player.z);
        assertSame(goblin, g.interact.focus, "the goblin is in the sights");
        assertTrue(g.interact.focusOptions.stream().noneMatch(o -> o.id().equals("examine")), "0.2.1: nothing in the world but an item is examined");
        long before = g.monsters.killed;
        g.combat.bleedOutNow(goblin);
        assertFalse(goblin.alive);
        assertEquals(before + 1, g.monsters.killed);
    }
}
