package rpg.monster;

import static org.junit.jupiter.api.Assertions.*;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import org.junit.jupiter.api.Test;
import rpg.Rpg;
import rpg.actor.Appearance;
import rpg.item.ItemDef;
import rpg.objects.Corpse;
import rpg.objects.GroundItem;
import rpg.world.DevMap;

/**
 * Gamefixes: a monster's corpse dissolves after a minute. It lies whole for the minute, goes over a couple of seconds,
 * and what was still on it is left on the ground where it lay; searching it was never theft.
 */
class MonsterRemainsTest {
    @Test void aMonstersRemainsLieAMinuteThenDissolveLeavingWhatTheyCarried() {
        var config = new NativeEngine.Config(); config.deterministic = true;
        try (Game game = Game.create(config)) {
            game.activate(DevMap.build(game.engine()));
            Rpg g = game.add(new Rpg(game, "Remains", new Appearance(), true));
            Monster gob = g.monsters.all().stream().filter(m -> m.kind == MonsterKind.GOBLIN && m.alive && !rpg.world.DungeonLayout.inDungeonRegion(m.x)).findFirst().orElseThrow();
            g.combat.resolve(g.player, gob, 999, 10, ItemDef.DamageType.SLASH, null, gob.x, gob.chestY(), gob.z, "test", false);
            for (int f = 0; f < 30; f++) assertTrue(game.tick(1f / 60));
            assertFalse(gob.alive);
            Corpse body = (Corpse) g.objects.get(gob.corpse);
            assertNotNull(body, "a body to search");
            assertFalse(body.isTheft(g.player), "searching a monster's remains is not theft");
            int carried = (int) body.contents.stacks().stream().filter(s -> s != null).count();
            for (ItemDef.Slot slot : ItemDef.Slot.values()) if (gob.eq.get(slot) != null && !gob.eq.get(slot).def.natural) carried++;   // and what it wore
            long groundBefore = g.objects.all().stream().filter(o -> o instanceof GroundItem gi && !gi.removed).count();
            // most of the minute: still there, whole
            gob.diedAt = g.time - (Monsters.CORPSE_SECONDS - 1);
            for (int f = 0; f < 30; f++) assertTrue(game.tick(1f / 60));
            assertTrue(g.monsters.all().contains(gob));
            assertEquals(0, gob.dissolve, 1e-6f, "not yet going");
            // past the minute: dissolving, then gone
            for (int f = 0; f < 60; f++) assertTrue(game.tick(1f / 60));
            assertTrue(gob.dissolve > 0 && gob.dissolve < 1, "dissolving over a moment, not vanishing: " + gob.dissolve);
            for (int f = 0; f < 60 * 3; f++) assertTrue(game.tick(1f / 60));
            assertFalse(g.monsters.all().contains(gob), "the remains are gone");
            assertTrue(body.removed, "and so is the body to search");
            long groundAfter = g.objects.all().stream().filter(o -> o instanceof GroundItem gi && !gi.removed).count();
            assertEquals(groundBefore + carried, groundAfter, "what was on it, carried or worn, is left on the ground");
        }
    }
}
