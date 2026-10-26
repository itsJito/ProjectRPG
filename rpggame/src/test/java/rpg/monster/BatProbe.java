package rpg.monster;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import org.junit.jupiter.api.Test;
import rpg.Rpg;
import rpg.actor.Appearance;
import rpg.world.DevMap;

class BatProbe {
    @Test void bats() {
        if (System.getenv("RPG_PROBE") == null) return;
        var config = new NativeEngine.Config(); config.deterministic = true;
        try (Game game = Game.create(config)) {
            game.activate(DevMap.build(game.engine()));
            Rpg g = game.add(new Rpg(game, "Bats", new Appearance(), true));
            g.state.minutes = 12 * 60;
            for (int f = 0; f < 60 * 120; f++) {
                game.tick(1f / 60);
                if (f % 600 == 0) for (Monster m : g.monsters.all()) if (m.kind == MonsterKind.BAT)
                    System.out.printf("[bat] t%.0f %d alive %s at (%.1f, %.2f, %.1f) alt %.2f want %.2f %s %s hp %.0f attacker %s%n", g.time, m.id, m.alive, m.x, m.y, m.z, m.altitude, m.wantAltitude, m.state, m.why, m.hp, g.nameOf(m.cs.lastAttacker));
            }
        }
    }
}
