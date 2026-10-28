package rpg.dungeon;

import static org.junit.jupiter.api.Assertions.*;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import org.junit.jupiter.api.Test;
import rpg.Rpg;
import rpg.actor.Appearance;
import rpg.monster.Monster;
import rpg.skill.Skill;
import rpg.world.DevMap;
import rpg.world.DungeonLayout;

/**
 * Gamefixes: dungeons, all but the boss dungeons, are matched to the player as they enter. No such dungeon's
 * strongest resident is above the player's combat level, elite ones included, with weaker ones filling in; from combat
 * level 20 the weakest start at 10 and rise one level for every two the player gains.
 */
class DungeonScalingTest {
    private static void train(Rpg g, int level) {
        for (Skill s : new Skill[]{Skill.ATTACK, Skill.STRENGTH, Skill.DEFENSE, Skill.HEALTH, Skill.ARCHERY, Skill.MAGIC, Skill.FAITH}) g.player.skills.setLevel(s, level);
        g.player.restore();
    }

    @Test void theRangeFollowsTheRules() {
        DungeonLayout warren = DungeonLayout.all().get(0), crypt = DungeonLayout.all().get(2);
        assertArrayEquals(new int[]{5, 10}, Dungeons.range(warren, 10));
        assertArrayEquals(new int[]{10, 20}, Dungeons.range(warren, 20));
        assertArrayEquals(new int[]{10, 21}, Dungeons.range(warren, 21));
        assertArrayEquals(new int[]{11, 22}, Dungeons.range(warren, 22));
        assertArrayEquals(new int[]{50, 100}, Dungeons.range(warren, 100));
        assertArrayEquals(new int[]{30, 60}, Dungeons.range(crypt, 60), "an elite dungeon is capped like the rest");
    }

    @Test void enteringMatchesTheResidentsToThePlayer() {
        var config = new NativeEngine.Config(); config.deterministic = true;
        try (Game game = Game.create(config)) {
            game.activate(DevMap.build(game.engine()));
            Rpg g = game.add(new Rpg(game, "Scaling", new Appearance(), true));
            for (int level : new int[]{12, 45, 90}) {
                train(g, level);
                int combat = g.player.skills.combatLevel();
                for (Dungeons.Door door : g.dungeons.doors) {
                    if (door.inside) continue;
                    DungeonLayout l = door.layout;
                    java.util.Map<Long, Integer> before = new java.util.HashMap<>();
                    for (Monster m : g.monsters.all()) if (m.alive && DungeonLayout.at(m.lairX, m.lairZ) == l) before.put(m.id, m.skills.combatLevel());
                    g.teleport(g.player, door.x, DevMap.FLOOR, door.z - 0.8f);
                    assertTrue(door.perform(g, g.player, "enter", g.state.newRequestId()).committed() || DungeonLayout.at(g.player.x, g.player.z) == l, "into the " + l.name);
                    int[] r = Dungeons.range(l, combat);
                    int top = 0, bottom = Integer.MAX_VALUE;
                    for (Monster m : g.monsters.all()) {
                        if (!m.alive || DungeonLayout.at(m.lairX, m.lairZ) != l) continue;
                        int c = m.skills.combatLevel();
                        top = Math.max(top, c); bottom = Math.min(bottom, c);
                        if (l.type == DungeonLayout.Type.BOSS) assertEquals(before.get(m.id), c, "a boss dungeon is as it was made");
                    }
                    System.out.printf("%-14s combat %3d -> residents %3d..%3d (range %d..%d)%n", l.name, combat, bottom, top, r[0], r[1]);
                    if (l.type == DungeonLayout.Type.BOSS) continue;
                    assertTrue(top <= r[1], l.name + ": strongest " + top + " above the cap " + r[1]);
                    assertTrue(top <= combat, l.name + ": " + top + " above the player's combat level " + combat);
                    assertTrue(top >= r[1] - 3, l.name + ": the strongest are near the cap, " + top);
                    assertTrue(bottom >= Math.min(r[0], r[1]) - 3, l.name + ": the weakest at the floor or above, " + bottom);
                    assertTrue(bottom < top, l.name + ": lower levels fill in");
                }
            }
        }
    }
}
