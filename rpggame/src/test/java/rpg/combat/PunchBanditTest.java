package rpg.combat;

import static org.junit.jupiter.api.Assertions.*;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import org.junit.jupiter.api.*;
import rpg.*;
import rpg.actor.Actor;
import rpg.actor.Appearance;
import rpg.ai.Npc;
import rpg.world.DevMap;

/** Gamefixes 0.2.2: the game crashed when the player punched a bandit inside DevTown. A long brawl there must run clean. */
class PunchBanditTest {
    private Game game;
    private Rpg g;
    @BeforeEach void open() {
        var cfg = new NativeEngine.Config(); cfg.deterministic = true;
        game = Game.create(cfg); game.activate(DevMap.build(game.engine()));
        g = game.add(new Rpg(game, "Punch", new Appearance(), true));
        g.state.minutes = 600;
    }
    @AfterEach void close() { game.close(); }
    private void frames(int n) { for (int i = 0; i < n; i++) assertTrue(game.tick(1f / 60)); }

    @Test void punchingABanditInTownRunsClean() {
        Npc bandit = (Npc) g.actor(Ids.BANDIT_1);
        g.teleport(bandit, 98.5f, DevMap.FLOOR, 78.5f);
        g.teleport(g.player, 98.5f, DevMap.FLOOR, 80.0f);
        g.player.yaw = 0;
        assertEquals(DevMap.ALPHA, DevMap.jurisdiction(bandit.x, bandit.z));
        for (int i = 0; i < 40 && bandit.alive; i++) {
            float dx = bandit.x - g.player.x, dz = bandit.z - g.player.z;
            g.player.yaw = (float) Math.atan2(-dx, -dz);
            if (Math.hypot(dx, dz) > 1.6) g.teleport(g.player, bandit.x - dx / (float) Math.hypot(dx, dz) * 1.3f, DevMap.FLOOR, bandit.z - dz / (float) Math.hypot(dx, dz) * 1.3f);
            g.combat.attack(g.player, false, Actor.Combat.Move.LIGHT_R);
            frames(40);
        }
        frames(600);
    }
}
