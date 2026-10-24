package rpg.combat;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import org.junit.jupiter.api.*;
import rpg.*;
import rpg.actor.*;
import rpg.animal.*;
import rpg.item.*;
import rpg.skill.Skill;
import rpg.world.*;
import static org.junit.jupiter.api.Assertions.*;

class MeleeAimTest {
    private Game game; private Rpg g; private Animal duck;
    @BeforeEach void open() {
        var cfg = new NativeEngine.Config(); cfg.deterministic = true;
        game = Game.create(cfg); game.activate(DevMap.build(game.engine())); g = game.add(new Rpg(game, "Aim", new Appearance(), true));
        for (var n : g.npcs) g.ai.unload(n);
        for (int x = 61; x <= 68; x++) for (int z = 56; z <= 65; z++) for (int y = DevMap.FLOOR; y < DevMap.FLOOR + 4; y++) g.setBlock(x, y, z, Blocks.AIR);
        g.teleport(g.player, 64.5f, DevMap.FLOOR, 62.5f); g.controller.snapTo(0); g.controller.camPitch = -0.7f; g.player.firstPerson = true;
        g.player.skills.setLevel(Skill.STRENGTH, 100); g.player.eq.set(ItemDef.Slot.MAIN, new ItemStack(Items.get("bronze_sword"), 1)); g.player.eq.set(ItemDef.Slot.OFF, null);
        duck = g.animals.release(Species.DUCK, false, true, 64.5f, 61.2f); duck.puppet = true; game.tick(0);
    }
    @AfterEach void close() { game.close(); }
    private void swing() { assertTrue(g.combat.attack(g.player, false, Actor.Combat.Move.LIGHT_R)); for (int i = 0; i < 45; i++) game.tick(1f / 60); }
    @Test void lookingDownCanStrikeASmallAnimalBeforeTheBladeMeetsTheFloor() {
        float before = duck.hp; swing(); assertTrue(duck.hp < before, "the low target must be reachable by the actual aimed sweep");
    }
    @Test void solidGeometryBeforeTheTargetStillStopsTheSameStroke() {
        for (int y = DevMap.FLOOR; y <= DevMap.FLOOR + 2; y++) g.setBlock(64, y, 61, Blocks.STONE);
        float before = duck.hp; game.tick(0); swing(); assertEquals(before, duck.hp);
    }
}
