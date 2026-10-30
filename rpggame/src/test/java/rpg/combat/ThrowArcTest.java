package rpg.combat;

import static org.junit.jupiter.api.Assertions.*;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import org.junit.jupiter.api.*;
import rpg.Rpg;
import rpg.actor.Appearance;
import rpg.ai.Npc;
import rpg.item.ItemDef;
import rpg.item.ItemStack;
import rpg.item.Items;
import rpg.world.DevMap;

/**
 * Gamefixes: the arc shown for an arrow or a throw was not where it actually went. Thrown on open ground at a point some
 * way off, the thing comes down where the guide's ring was, within a hand's breadth - at a smooth frame rate and a poor one.
 */
class ThrowArcTest {
    private Game game;
    private Rpg g;
    @BeforeEach void open() {
        var cfg = new NativeEngine.Config(); cfg.deterministic = true;
        game = Game.create(cfg); game.activate(DevMap.build(game.engine()));
        g = game.add(new Rpg(game, "Arc", new Appearance(), true));
        for (Npc n : g.npcs) g.ai.unload(n);
        for (var m : new java.util.ArrayList<>(g.monsters.all())) g.ai.unload(m);
        g.player.skills.setLevel(rpg.skill.Skill.ARCHERY, 99);
    }
    @AfterEach void close() { game.close(); }

    private float miss(float dt, float pitch) {
        float[] lane = rpg.TestLanes.open(g);
        g.teleport(g.player, lane[0], DevMap.FLOOR, lane[1]);
        g.player.eq.set(ItemDef.Slot.MAIN, new ItemStack(Items.get("bronze_javelin") != null ? Items.get("bronze_javelin") : Items.get("bronze_dart"), 3));
        for (int i = 0; i < 10; i++) game.tick(1f / 60);
        g.player.aimYaw = 0; g.player.yaw = 0; g.player.pitch = pitch;
        g.player.cs.draw = 1;
        float[] eye = {g.player.x, g.player.eyeY(), g.player.z};
        assertTrue(CombatSystem.aimThrow(g, g.player, eye), "a throwing weapon in hand, drawn");
        float[] ring = g.player.throwPlan.landing.clone();
        int before = g.combat.projectiles.size();
        assertTrue(g.combat.throwOne(g.player, 0, pitch, 1), "thrown");
        var p = g.combat.projectiles.get(before);
        float lx = p.x, ly = p.y, lz = p.z;
        for (int i = 0; i < 600 && !p.done; i++) { lx = p.x; ly = p.y; lz = p.z; game.tick(dt); }
        if (p.done) { lx = p.x; ly = p.y; lz = p.z; }
        float d = (float) Math.sqrt(Math.pow(lx - ring[0], 2) + Math.pow(ly - ring[1], 2) + Math.pow(lz - ring[2], 2));
        System.out.printf("dt %.3f pitch %.2f: ring (%.2f, %.2f, %.2f), landed (%.2f, %.2f, %.2f), off by %.2f m%n", dt, pitch, ring[0], ring[1], ring[2], lx, ly, lz, d);
        return d;
    }

    @Test void aThrowLandsWhereTheRingWas() {
        assertTrue(miss(1f / 60, -0.12f) < 0.35f, "off the ring at 60 fps");
    }

    @Test void evenAtALowFrameRate() {
        assertTrue(miss(1f / 24, -0.12f) < 0.35f, "off the ring at 24 fps");
    }
}
