package rpg.ai;

import static org.junit.jupiter.api.Assertions.*;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import java.util.ArrayList;
import org.junit.jupiter.api.*;
import rpg.Rpg;
import rpg.actor.Appearance;
import rpg.monster.Monster;
import rpg.monster.MonsterKind;
import rpg.world.DevMap;

/**
 * Gamefixes 0.2.1: human and monster AI choices expanded. Townsfolk caught out in a downpour go home to shelter, and
 * the devout keep morning prayers at their town's altar; a brute with another of its kind on the same foe goes round to
 * the far side of it rather than queue behind.
 */
class AiChoicesTest {
    private Game game;
    private Rpg g;
    @BeforeEach void open() {
        var cfg = new NativeEngine.Config(); cfg.deterministic = true; game = Game.create(cfg); game.activate(DevMap.build(game.engine()));
        g = game.add(new Rpg(game, "Choices", Appearance.henric(), true));
        for (Monster m : new ArrayList<>(g.monsters.all())) g.ai.unload(m);
    }
    @AfterEach void close() { game.close(); }

    private Npc outdoorsVillager() {
        for (Npc n : g.npcs) if (!(n instanceof Monster) && n.alive && n.loaded && n.role == Npc.Role.VILLAGER) {
            g.teleport(n, 100.5f, DevMap.FLOOR, 72.5f);   // DevCity's square, open to the sky
            return n;
        }
        throw new AssertionError("no villager");
    }

    @Test void aDownpourSendsTownsfolkInOutOfIt() {
        Npc n = outdoorsVillager();
        for (int i = 0; i < 10; i++) game.tick(1f / 60);
        g.timeSystem.weather.rain = 0.9f;
        g.ai.brain.think(n);
        assertEquals(Brain.Goal.SHELTER, n.goal, "out in the rain: " + n.goal + " (" + n.lastOutcome + ")");
        g.timeSystem.weather.rain = 0;
        g.ai.brain.think(n);
        assertNotEquals(Brain.Goal.SHELTER, n.goal, "sheltering from rain that has stopped");
    }

    @Test void theDevoutKeepMorningPrayers() {
        Npc devout = null;
        for (Npc n : g.npcs) if (!(n instanceof Monster) && n.alive && n.loaded && (n.role == Npc.Role.VILLAGER || n.role == Npc.Role.CIVIC) && Math.floorMod(n.id, 3) == 0 && n.homeJurisdiction != null && !DevMap.WILDS.equals(n.homeJurisdiction)) { devout = n; break; }
        assertNotNull(devout, "nobody devout in a town");
        double now = g.state.hourOfDay();
        g.advanceTime(((7.0 - now) % 24 + 24) % 24 * 60, "morning");
        devout.hunger = 0; devout.social = 0; devout.fatigue = 0;
        g.ai.brain.think(devout);
        assertEquals(Brain.Goal.PRAY, devout.goal, devout.name + " chose " + devout.goal + " (" + devout.lastOutcome + ")");
    }

    @Test void aSecondBruteFlanksTheFoe() {
        float[] lane = rpg.TestLanes.open(g);
        g.teleport(g.player, lane[0], DevMap.FLOOR, lane[1] - 6);
        g.player.skills.setLevel(rpg.skill.Skill.HEALTH, 99); g.player.restore();
        Monster a = g.monsters.spawn(MonsterKind.TROLL, 60, lane[0] + 1.5f, lane[1] - 4.5f, null), b = g.monsters.spawn(MonsterKind.TROLL, 60, lane[0] - 2f, lane[1], null);
        for (Monster m : new Monster[]{a, b}) { m.hostileToPlayer = true; m.target = g.player.id; m.state = Monster.State.ENGAGE; }
        boolean flanked = false;
        for (int i = 0; i < 240 && !flanked; i++) {
            // both have the foe in their senses and are in the fight (the second would otherwise doze by day, as trolls do)
            for (Monster m : new Monster[]{a, b}) {
                var k = m.known.computeIfAbsent(g.player.id, Monster.Known::new); k.x = g.player.x; k.z = g.player.z; k.at = g.time; k.threat = 1;
                m.target = g.player.id; m.state = Monster.State.ENGAGE;
            }
            assertTrue(game.tick(1f / 60));
            flanked = a.lastOutcome != null && a.lastOutcome.startsWith("flanking") || b.lastOutcome != null && b.lastOutcome.startsWith("flanking");
        }
        assertTrue(flanked, "neither troll went round: " + a.lastOutcome + " / " + b.lastOutcome);
    }
}
