package rpg.combat;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import rpg.Rpg;
import rpg.actor.Appearance;
import rpg.animal.Animal;
import rpg.animal.Species;
import rpg.world.DevMap;

import static org.junit.jupiter.api.Assertions.*;

/** gamefixes: a cat at the attacker's feet - in front, close, looked down at - can be struck, in either camera. */
class SmallTargetTest {
    private Game game;
    private Rpg g;
    private Animal cat;

    @BeforeEach void open() {
        var config = new NativeEngine.Config(); config.deterministic = true;
        game = Game.create(config); game.activate(DevMap.build(game.engine()));
        g = game.add(new Rpg(game, "Small target", new Appearance(), true));
        for (var n : g.npcs) g.ai.unload(n);
        cat = g.animals.all().stream().filter(a -> a.species == Species.CAT && a.adult()).findFirst().orElseThrow();
        cat.puppet = true;
        cat.skills.setLevel(rpg.skill.Skill.HEALTH, 99); cat.restore();
    }

    @AfterEach void close() { if (game != null) game.close(); }

    /** Swings at the cat placed d metres ahead, the camera looking at its back; true when it was struck. */
    private boolean swingAt(float d, boolean firstPerson) {
        float px = 64.5f, pz = 62.5f;
        g.teleport(g.player, px, DevMap.FLOOR, pz);
        g.player.firstPerson = firstPerson;
        g.controller.snapTo(0);
        g.teleport(cat, px, DevMap.FLOOR, pz - d);
        for (int i = 0; i < 20; i++) { cat.vx = cat.vz = 0; assertTrue(game.tick(1f / 60)); }
        // look at the cat's back, as a player would
        for (int k = 0; k < 30; k++) {
            float eyeY = g.controller.cy, dist = (float) Math.hypot(cat.x - g.controller.cx, cat.z - g.controller.cz);
            g.controller.camPitch = (float) Math.atan2(cat.y + cat.height * 0.6f - eyeY, dist);
            assertTrue(game.tick(1f / 60));
        }
        float hp = cat.hp;
        g.player.cs.cancel();
        g.controller.in.mainDown = true; assertTrue(game.tick(1f / 60));
        g.controller.in.mainDown = false;
        boolean touched = false;
        double started = g.player.cs.lastAttack;
        var move = g.player.cs.move;
        float low = 99;
        for (int i = 0; i < 70; i++) {
            g.teleport(cat, px, DevMap.FLOOR, pz - d); assertTrue(game.tick(1f / 60)); touched |= g.player.cs.struck.contains(cat.id);
            if (g.player.cs.phase == rpg.actor.Actor.Combat.Phase.ACTIVE) { move = g.player.cs.move; low = Math.min(low, CombatSystem.blade(g.player, move, g.player.cs.t / Math.max(0.01f, g.player.cs.active), 1, CombatSystem.reach(g.player))[1] - DevMap.FLOOR); }
        }
        System.out.printf("cat at %.2f m (%s): pitch %.2f, %s off %s, lowest tip %.2f, contact %s, hp %.1f -> %.1f%n", d, firstPerson ? "first person" : "third person", g.controller.camPitch, move, g.player.cs.offHand, low, touched, hp, cat.hp);
        return touched;
    }

    @Test void aCatAtTheFeetCanBeStruck() {
        int missed = 0;
        for (boolean fp : new boolean[]{true, false}) for (float d : new float[]{0.45f, 0.6f, 0.8f, 1.0f, 1.3f}) if (!swingAt(d, fp)) missed++;
        assertEquals(0, missed, "swings that passed over a cat in reach");
    }
}
