package rpg.probe;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import rpg.Rpg;
import rpg.actor.Appearance;
import rpg.world.DevMap;

/** (0.4.3) The roster as the game makes it: every person's Ids name, name, role, calling and title (a probe). */
@Tag("probe")
class RosterProbeTest {
    @Test void roster() {
        var cfg = new NativeEngine.Config(); cfg.deterministic = true; cfg.audioDevice = false;
        Game game = Game.create(cfg);
        try {
            game.activate(DevMap.build(game.engine()));
            Rpg g = game.add(new Rpg(game, "roster", new Appearance(), true));
            for (var n : g.npcs) System.out.printf("[roster] %-22s %-12s %-12s %-22s %s%n", rpg.world.shadowveil.Settle.idName(n.id), n.name, n.role, n.profession == null ? "-" : n.profession.name(), n.title);
        } finally { game.close(); }
    }
}
