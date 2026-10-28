package rpg.ai;

import static org.junit.jupiter.api.Assertions.*;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import org.junit.jupiter.api.Test;
import rpg.Ids;
import rpg.Rpg;
import rpg.actor.Appearance;
import rpg.monster.Monster;
import rpg.monster.MonsterKind;
import rpg.world.DevMap;

/**
 * Gamefixes: certain monsters should prompt fight or flight - a dragon in the city should cause a genuine panic, the
 * appropriate people trying to save the city and fight it off. A dragon set down in the street by the guard: the alarm
 * goes up and spreads by voice, the townsfolk make for shelter, the watch turns out against it.
 */
class TerrorAlarmTest {
    @Test void aDragonInTheCityRaisesTheAlarmTheTownShelterTheWatchTurnsOut() {
        var cfg = new NativeEngine.Config(); cfg.deterministic = true;
        try (Game game = Game.create(cfg)) {
            game.activate(DevMap.build(game.engine()));
            Rpg g = game.add(new Rpg(game, "Panic", new Appearance(), true));
            g.state.minutes = 11 * 60;
            for (int i = 0; i < 60; i++) game.tick(1f / 60);
            Npc cole = (Npc) g.actor(Ids.GUARD);
            g.teleport(g.player, 30, DevMap.FLOOR, 30);
            Monster dragon = g.monsters.spawn(MonsterKind.DRAGON, 70, cole.x + 8, cole.z, null);
            dragon.puppet = true; dragon.wantAltitude = 0;
            for (int i = 0; i < 60 * 12; i++) { dragon.hp = dragon.maxHp(); game.tick(1f / 60); }
            boolean raised = g.state.all().stream().anyMatch(e -> e.type().equals("AlarmRaised") && e.subject() == dragon.id);
            int sheltering = 0, heardOnly = 0, defending = 0, civilians = 0;
            StringBuilder b = new StringBuilder();
            for (Npc n : g.npcs) {
                if (!n.alive || !n.loaded || n instanceof Monster || n.asleep()) continue;
                boolean near = n.distTo(dragon) < 60;
                if (!near) continue;
                b.append(String.format("%s (%s) %s heard=%b %s%n", n.name, n.role, n.goal, n.alarmHeard == dragon.id, n.lastOutcome));
                if (Brain.defender(n)) { if (n.goal == Brain.Goal.FIGHT || n.goal == Brain.Goal.SEARCH) defending++; }
                else {
                    civilians++;
                    if (n.goal == Brain.Goal.HIDE && n.fearOf == dragon.id) sheltering++;
                    if (n.alarmHeard == dragon.id && !g.ai.canSee(n, dragon)) heardOnly++;
                }
            }
            System.out.print(b); g.state.all().stream().filter(e -> e.type().startsWith("Alarm")).forEach(e -> System.out.println(e.type() + " " + e.payload() + " subject " + e.subject() + " at " + e.gameMinutes() + " dragon " + dragon.id + " alive " + dragon.alive + " hp " + dragon.hp + " loaded " + dragon.loaded));
            System.out.printf("alarm %b; %d of %d townsfolk sheltering (%d only heard it), %d defenders out%n", raised, sheltering, civilians, heardOnly, defending);
            assertTrue(raised, "the alarm went up");
            assertTrue(sheltering >= Math.max(2, civilians / 2), "the townsfolk made for shelter:\n" + b);
            assertTrue(defending >= 1, "the watch turned out:\n" + b);
            assertTrue(heardOnly >= 1, "the alarm spread to people who never saw it:\n" + b);
        }
    }
}
