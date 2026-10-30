package rpg.monster;

import static org.junit.jupiter.api.Assertions.*;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import java.util.ArrayList;
import org.junit.jupiter.api.*;
import rpg.Rpg;
import rpg.actor.Appearance;
import rpg.combat.Gore;
import rpg.item.ItemDef.DamageType;
import rpg.world.DevMap;

/**
 * Gamefixes 0.2.1: monsters were not dismemberable - a beast was one capsule, and nothing came off a monster. A heavy
 * cut takes a goblin's arm off, a dragon's wing; the piece lies in the world as the monster's own, and a blow aimed
 * where the wing was now goes through empty air.
 */
class MonsterDismemberTest {
    private Game game;
    private Rpg g;

    @BeforeEach void open() {
        var cfg = new NativeEngine.Config(); cfg.deterministic = true;
        game = Game.create(cfg); game.activate(DevMap.build(game.engine()));
        g = game.add(new Rpg(game, "Dismember", new Appearance(), true));
        for (Monster m : new ArrayList<>(g.monsters.all())) g.ai.unload(m);
    }
    @AfterEach void close() { game.close(); }

    @Test void aHeavyCutTakesAGoblinsArmOff() {
        float[] lane = rpg.TestLanes.open(g);
        Monster goblin = g.monsters.spawn(MonsterKind.GOBLIN, 2, lane[0], lane[1] - 3, null);
        game.tick(1f / 60);
        float rx = (float) Math.cos(goblin.yaw), rz = (float) -Math.sin(goblin.yaw);
        float px = goblin.x + rx * goblin.radius * 0.95f, py = goblin.y + goblin.height * 0.62f, pz = goblin.z + rz * goblin.radius * 0.95f;
        assertEquals(Gore.Part.ARM_R, g.gore.partAt(goblin, px, py, pz));
        int limbs = g.gore.limbs.size();
        g.gore.onHit(g.player, goblin, (int) goblin.maxHp(), 40, DamageType.SLASH, null, px, py, pz, rx, rz, true);
        assertTrue(g.gore.lost(goblin, Gore.Part.ARM_R), "the goblin kept its arm");
        assertEquals(limbs + 1, g.gore.limbs.size());
        assertEquals(MonsterKind.GOBLIN, g.gore.limbs.get(g.gore.limbs.size() - 1).monster, "the arm is drawn from the goblin's own figure");
    }

    @Test void aDragonsWingIsItsOwnPartAndGoesWhenCutAway() {
        float[] lane = rpg.TestLanes.open(g);
        Monster dragon = g.monsters.spawn(MonsterKind.DRAGON, 70, lane[0], lane[1] - 12, null);
        game.tick(1f / 60);
        float rx = (float) Math.cos(dragon.yaw), rz = (float) -Math.sin(dragon.yaw), w = dragon.kind.hw * 0.42f;
        float px = dragon.x + rx * w, py = dragon.y + dragon.kind.hh * 0.7f, pz = dragon.z + rz * w;
        assertEquals(Gore.Part.ARM_R, g.gore.partAt(dragon, px, py, pz), "the far right of a dragon is its right wing");
        float fx = (float) -Math.sin(dragon.yaw), fz = (float) -Math.cos(dragon.yaw);
        assertEquals(Gore.Part.HEAD, g.gore.partAt(dragon, dragon.x + fx * dragon.kind.hd * 0.45f, dragon.y + dragon.kind.hh * 0.75f, dragon.z + fz * dragon.kind.hd * 0.45f));
        assertEquals(Gore.Part.TAIL, g.gore.partAt(dragon, dragon.x - fx * dragon.kind.hd * 0.45f, dragon.y + dragon.kind.hh * 0.35f, dragon.z - fz * dragon.kind.hd * 0.45f));
        // a ray down onto the wing from above strikes it
        float before = rpg.combat.CombatSystem.rayHit(g, dragon, px, py + 3, pz, 0, -1, 0, 6, 0);
        assertTrue(before < 6, "the wing was not struck: " + before);
        g.gore.onHit(g.player, dragon, (int) dragon.maxHp(), 400, DamageType.SLASH, null, px, py, pz, rx, rz, true);
        assertTrue(g.gore.lost(dragon, Gore.Part.ARM_R), "the wing stayed on");
        float after = rpg.combat.CombatSystem.rayHit(g, dragon, px, py + 3, pz, 0, -1, 0, 6, 0);
        assertTrue(after > before + 0.3f, "a blow still met the wing that is gone (" + before + " -> " + after + ")");
    }
}
