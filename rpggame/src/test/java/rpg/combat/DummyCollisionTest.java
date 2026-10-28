package rpg.combat;

import static org.junit.jupiter.api.Assertions.*;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import org.junit.jupiter.api.Test;
import rpg.Rpg;
import rpg.actor.Actor;
import rpg.actor.Appearance;
import rpg.world.DevMap;

/** Gamefixes: the target practice dummies had no collision. Walked at face on, a body stops at the sack, not inside it. */
class DummyCollisionTest {
    @Test void aDummyStopsABodyWalkingIntoIt() {
        var cfg = new NativeEngine.Config(); cfg.deterministic = true;
        try (Game game = Game.create(cfg)) {
            game.activate(DevMap.build(game.engine()));
            Rpg g = game.add(new Rpg(game, "Dummy", new Appearance(), true));
            for (var n : g.npcs) g.ai.unload(n);
            for (Actor d : g.dummies) {
                g.teleport(g.player, d.x, DevMap.FLOOR, d.z + 2.5f);
                for (int i = 0; i < 20; i++) game.tick(1f / 60);
                for (int i = 0; i < 150; i++) { g.controller.in.forward = 1; g.controller.snapTo(0); game.tick(1f / 60); }
                g.controller.in.forward = 0;
                float gap = g.player.z - d.z - 0.18f - g.player.radius;
                System.out.printf("%s: stopped %.2f m from its face%n", d.name, gap);
                assertTrue(gap > -0.1f, d.name + ": walked " + -gap + " m into the dummy");
                assertTrue(gap < 0.15f, d.name + ": stopped " + gap + " m short");
            }
        }
    }
}
