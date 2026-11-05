package rpg.monster;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import java.util.ArrayList;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import rpg.Rpg;
import rpg.actor.Appearance;
import rpg.ai.Npc;
import rpg.world.Blocks;
import rpg.world.DevMap;

/** Probe (RPG_PROBE=1): how an imp and a ghost close on and fight the player, in the open and round a wall. */
@EnabledIfEnvironmentVariable(named = "RPG_PROBE", matches = "1")
class HaunterProbeTest {
    @Test void probe() {
        for (MonsterKind kind : new MonsterKind[]{MonsterKind.IMP, MonsterKind.GHOST}) for (boolean wall : new boolean[]{false, true}) {
            var cfg = new NativeEngine.Config(); cfg.deterministic = true;
            try (Game game = Game.create(cfg)) {
                game.activate(DevMap.build(game.engine()));
                Rpg g = game.add(new Rpg(game, "probe", Appearance.henric(), true));
                for (Monster m : new ArrayList<>(g.monsters.all())) g.ai.unload(m);
                for (int x = 52; x <= 77; x++) for (int z = 46; z <= 76; z++) for (int y = DevMap.FLOOR; y < DevMap.FLOOR + 10; y++) g.setBlock(x, y, z, Blocks.AIR);
                for (Npc o : g.npcs) if (o.distTo(64.5f, 60f) < 30) g.ai.unload(o);
                if (wall) for (int x = 58; x <= 70; x++) for (int y = 0; y < 4; y++) g.setBlock(x, DevMap.FLOOR + y, 60, Blocks.BRICK);
                g.teleport(g.player, 64.5f, DevMap.FLOOR, 56.5f); game.tick(0);
                g.player.restore();
                g.state.minutes = 23 * 60;
                var lair = g.monsters.addLair(kind, "probe", 64.5f, 66.5f, kind.minLevel, kind.minLevel + 4, 1);
                Monster m = g.monsters.spawn(kind, kind.minLevel + 4, 64.5f, 66.5f, lair);
                game.tick(0);
                float hp0 = g.player.hp; int hits = 0; double firstHit = -1;
                StringBuilder log = new StringBuilder();
                for (int i = 0; i < 60 * 30; i++) {
                    float before = g.player.hp;
                    game.tick(1f / 60);
                    if (g.player.hp < before) { hits++; if (firstHit < 0) firstHit = g.time; }
                    g.player.hp = g.player.maxHp();
                    if (i % 60 == 0 || kind == MonsterKind.GHOST && !wall && i > 600 && i < 780 && i % 10 == 0) log.append(String.format("  t%2d d %.1f alt %.1f y %.1f state %s dist %.1f at %.1f,%.1f %s%n", i / 60, m.distTo(g.player), m.altitude, m.y - DevMap.FLOOR, m.state, m.disturbed, m.x, m.z, m.why + " sees " + g.ai.canSee(m, g.player) + " known " + m.known.size() + " tgt " + m.tx + "," + m.tz + " goal " + m.hasGoalPos + " out " + m.lastOutcome + " next " + (m.nextStrike - g.time) + " ph " + m.cs.phase + " yawerr " + rpg.actor.Actor.wrap(m.wantYaw - m.yaw) + " wind " + (m.windupUntil - g.time) + " sta " + m.stamina + " gas " + m.gaseousT + " sh " + m.sheathed + " arms " + g.combat.armsReady(m) + " busy " + m.cs.busy() + " react " + m.reacting + " morph " + m.morphT + " throw " + (g.time - m.throwAt)));
                }
                System.out.println("[haunter] " + kind + (wall ? " (wall)" : " (open)") + " hits " + hits + " first at " + firstHit + "\n" + log);
            }
        }
    }
}
