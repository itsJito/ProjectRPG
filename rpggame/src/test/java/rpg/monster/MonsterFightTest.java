package rpg.monster;

import static org.junit.jupiter.api.Assertions.*;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.*;
import rpg.Rpg;
import rpg.actor.Appearance;
import rpg.ai.Npc;
import rpg.world.DevMap;

/**
 * Gamefixes: bats and goblins had bad combat AI, and people stood swinging at bats overhead. A bat now circles out of
 * reach and swoops in to bite; goblins spread round their target and dart in by turns; a guard fighting a bat overhead
 * waits for it to dive instead of swinging at the air.
 */
class MonsterFightTest {
    private Game game;
    private Rpg g;
    private float[] lane;

    @BeforeEach void open() {
        var cfg = new NativeEngine.Config(); cfg.deterministic = true;
        game = Game.create(cfg); game.activate(DevMap.build(game.engine()));
        g = game.add(new Rpg(game, "Fights", new Appearance(), true));
        g.state.minutes = 23 * 60;
        for (Monster m : new ArrayList<>(g.monsters.all())) g.ai.unload(m);
        lane = rpg.TestLanes.open(g);
        g.teleport(g.player, lane[0], DevMap.FLOOR, lane[1]);
        g.player.skills.setLevel(rpg.skill.Skill.HEALTH, 99); g.player.restore();
    }
    @AfterEach void close() { game.close(); }

    private Monster spawn(MonsterKind k, float dx, float dz) {
        Monster m = g.monsters.spawn(k, 3, lane[0] + dx, lane[1] + dz, null);
        Monster.Known known = m.known.computeIfAbsent(g.player.id, Monster.Known::new);
        known.x = g.player.x; known.z = g.player.z; known.at = g.time; known.sense = "seen"; known.damage = 50;
        m.cs.lastHurt = g.time; m.aggression = 1.5f;
        return m;
    }

    @Test void aBatCirclesOutOfReachAndSwoopsInToBite() {
        Monster bat = spawn(MonsterKind.BAT, 4, -4);
        int frames = 60 * 25, high = 0, dives = 0, wasSwoop = 0;
        float hp = g.player.hp;
        for (int f = 0; f < frames; f++) {
            g.player.hp = Math.max(g.player.hp, 20);
            game.tick(1f / 60);
            if (!bat.alive) break;
            if (bat.state == Monster.State.ENGAGE && bat.altitude > 2.2f) high++;
            if (bat.swoop == 1 && wasSwoop != 1) dives++;
            wasSwoop = bat.swoop;
        }
        System.out.printf("bat: %d dives, %.0f%% of the time out of reach overhead%n", dives, 100f * high / frames);
        assertTrue(dives >= 3, "the bat made " + dives + " dives in 25 s");
        assertTrue(high > frames * 0.25f, "the bat spent most of the fight circling out of reach");
    }

    @Test void goblinsSpreadRoundTheirTargetAndDartInByTurns() {
        // the camp's own band, the player walked in among them
        for (Monster m : g.monsters.all()) if (m.kind == MonsterKind.GOBLIN) g.ai.reload(m);
        List<Monster> band = g.monsters.all().stream().filter(m -> m.kind == MonsterKind.GOBLIN && !rpg.world.DungeonLayout.inDungeonRegion(m.x)).toList();
        g.state.minutes = 12 * 60;
        g.teleport(g.player, DevMap.GOBLIN_CAMP[0] + 1, DevMap.FLOOR, DevMap.GOBLIN_CAMP[1] + 3);
        // (the wild animals round the camp - a mountain lion sends the band running - are not this test's business)
        for (var an : new ArrayList<>(g.animals.all())) if (an.distTo(g.player) < 45) g.animals.remove(an);
        for (Monster m : band) { Monster.Known k = m.known.computeIfAbsent(g.player.id, Monster.Known::new); k.x = g.player.x; k.z = g.player.z; k.at = g.time; k.sense = "seen"; k.damage = 30; m.cs.lastHurt = g.time; }
        int most = 0, samples = 0;
        double spread = 0;
        for (int f = 0; f < 60 * 20; f++) {
            g.player.hp = Math.max(g.player.hp, 20);
            game.tick(1f / 60);
            int darting = 0;
            for (Monster m : band) if (m.alive && m.state == Monster.State.ENGAGE && m.swoop == 1) darting++;
            most = Math.max(most, darting);
            if (f > 60 * 6 && f % 30 == 0) {
                // the widest gap between neighbours round the player: bunched in front it is near a full turn
                List<Double> angles = new ArrayList<>();
                for (Monster m : band) if (m.alive) angles.add(Math.atan2(m.z - g.player.z, m.x - g.player.x));
                angles.sort(Double::compare);
                double gap = 0;
                for (int i = 0; i < angles.size(); i++) gap = Math.max(gap, (angles.get((i + 1) % angles.size()) - angles.get(i) + 4 * Math.PI) % (2 * Math.PI));
                spread += gap; samples++;
            }
        }
        double meanGap = spread / Math.max(1, samples);
        System.out.printf("goblins: at most %d darting at once, widest gap round the player %.0f degrees%n", most, Math.toDegrees(meanGap));
        assertTrue(most <= 2, "no more than two dart in at once");
        assertTrue(meanGap < Math.toRadians(220), "the band spread round the player rather than bunching on one side");
        assertTrue(g.player.cs.lastHurt > 0, "the goblins landed blows");
    }

    @Test void aGuardWaitsForABatToDiveInsteadOfSwingingAtTheAir() {
        Npc guard = (Npc) g.actor(rpg.Ids.GUARD);
        g.teleport(guard, lane[0] + 1, DevMap.FLOOR, lane[1] - 6);
        Monster bat = spawn(MonsterKind.BAT, 1, -9);
        g.teleport(g.player, lane[0] + 30, DevMap.FLOOR, lane[1]);
        bat.known.clear();
        Monster.Known k = bat.known.computeIfAbsent(guard.id, Monster.Known::new);
        k.x = guard.x; k.z = guard.z; k.at = g.time; k.sense = "seen"; k.damage = 50;
        int wasted = 0, waited = 0;
        for (int f = 0; f < 60 * 20; f++) {
            guard.hp = Math.max(guard.hp, 20);
            boolean swinging = guard.cs.busy();
            game.tick(1f / 60);
            if (!bat.alive) break;
            if (!swinging && guard.cs.busy() && guard.fightTarget == bat.id && bat.chestY() - guard.y > 2.4f) { wasted++; System.out.printf("swing t%.2f phase %s move %s outcome %s bat alt %.2f chestDiff %.2f target %d bat %d y %.2f%n", g.time, guard.cs.phase, guard.cs.move, guard.lastOutcome, bat.altitude, bat.chestY() - guard.y, guard.fightTarget, bat.id, bat.y); }
            if ("wait for it to dive".equals(guard.lastOutcome)) waited++;
        }
        System.out.printf("guard: %d swings at a bat out of reach, %d frames waiting for the dive%n", wasted, waited);
        assertEquals(0, wasted, "the guard swung at a bat high out of reach");
        assertTrue(waited > 0, "the guard waited for the bat to dive");
    }
}
