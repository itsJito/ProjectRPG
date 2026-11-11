package rpg.world;

import static org.junit.jupiter.api.Assertions.*;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import java.util.ArrayList;
import org.junit.jupiter.api.*;
import rpg.Rpg;
import rpg.actor.Appearance;
import rpg.monster.Monster;

/** (0.3.7, gamefixes) a Shrine of Light by the graveyard: rest heals, passes the night, brings the wild's dead back, binds the respawn. */
class ShrineTest {
    private Game game;
    private Rpg g;
    @BeforeEach void open() {
        var cfg = new NativeEngine.Config(); cfg.deterministic = true;
        game = Game.create(cfg); game.activate(DevMap.build(game.engine()));
        g = game.add(new Rpg(game, "Shrine", new Appearance(), true));
        game.tick(0);
    }
    @AfterEach void close() { game.close(); }

    @Test void oneStandsByTheGraveyardAndIsSolid() {
        assertEquals(1, g.shrines.all.size());
        var s = g.shrines.all.get(0);
        var plot = g.graveyards.plots.get(0);
        assertTrue(Math.hypot(s.x - plot.x(), s.z - plot.z()) < 22, "near the graveyard");
        assertTrue(g.vox.solid(s.x0, DevMap.FLOOR, s.z0) && g.vox.solid(s.x0 + 1, DevMap.FLOOR + 1, s.z0 + 1), "a 2 x 2 tower");
    }

    @Test void restingHealsPassesTheNightBringsTheWildBackAndBinds() {
        var s = g.shrines.all.get(0);
        g.teleport(g.player, s.x + 1.8f, DevMap.FLOOR, s.z); game.tick(1f / 60);
        g.player.hp = 1; g.player.stamina = 0; g.player.cs.slowT = 5;
        g.state.minutes = 22 * 60;
        // kill a goblin of the camp: its lair is a member short
        Monster gob = null;
        for (Monster m : g.monsters.all()) if (m.alive && m.kind == rpg.monster.MonsterKind.GOBLIN && rpg.world.DungeonLayout.at(m.x, m.z) == null) { gob = m; break; }
        assertNotNull(gob);
        long lair = gob.lair;
        long before = g.monsters.all().stream().filter(m -> m.alive && m.lair == lair).count();
        g.combat.bleedOutNow(gob);
        for (int i = 0; i < 5; i++) game.tick(1f / 60);
        assertEquals(before - 1, g.monsters.all().stream().filter(m -> m.alive && m.lair == lair).count());
        // the boss's den keeps its dead
        Monster troll = null;
        for (Monster m : g.monsters.all()) if (m.alive && m.kind == rpg.monster.MonsterKind.TROLL) troll = m;
        if (troll != null) { g.combat.bleedOutNow(troll); for (int i = 0; i < 5; i++) game.tick(1f / 60); }
        var r = g.interact.request(g.player, s, "rest");
        assertTrue(r.committed(), "rested: " + r.reason());
        assertEquals(g.player.maxHp(), g.player.hp, 0.01f);
        assertEquals(0, g.player.cs.slowT, 0.001f);
        double hour = g.state.hourOfDay();
        assertTrue(hour >= 6 && hour < 7.5, "morning: " + hour);
        assertEquals(before, g.monsters.all().stream().filter(m -> m.alive && m.lair == lair).count(), "the camp made up again");
        if (troll != null) assertEquals(0, g.monsters.living(rpg.monster.MonsterKind.TROLL), "the boss stays dead");
        assertEquals(s.id, g.shrines.bound);
        g.player.hp = 0; g.player.alive = false;
        g.respawn();
        assertTrue(Math.hypot(g.player.x - s.wakeX, g.player.z - s.wakeZ) < 1.5f, "wakes by the bound shrine's graveyard");
    }

    @Test void noRestWithSomethingAfterYou() {
        var s = g.shrines.all.get(0);
        g.teleport(g.player, s.x + 1.8f, DevMap.FLOOR, s.z); game.tick(1f / 60);
        Monster m = g.monsters.spawn(rpg.monster.MonsterKind.GOBLIN, 3, s.x + 5, s.z, null);
        m.target = g.player.id; m.state = Monster.State.ENGAGE;
        assertNotNull(g.shrines.refusal(g.player));
        assertFalse(s.options(g, g.player).get(0).enabled());
    }
}
