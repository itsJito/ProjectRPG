package rpg.monster;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import org.junit.jupiter.api.Test;
import rpg.Rpg;
import rpg.SaveSystem;
import rpg.actor.Appearance;
import rpg.skill.Skill;
import rpg.world.DevMap;

import static org.junit.jupiter.api.Assertions.*;

/** Monsters (gamefixes, GDD 3.14): lairs populated, hostility as a state, killing one no crime, the drop on the body, kept in saves. */
class MonsterTest {
    private Rpg open(Game game) {
        game.activate(DevMap.build(game.engine()));
        Rpg g = game.add(new Rpg(game, "Monsters", new Appearance(), true));
        g.player.skills.setLevel(Skill.HEALTH, 25); g.player.restore();
        return g;
    }

    @Test void theWildsHaveTheirLairsAndTheGoblinsTurnOnAnIntruder() throws Exception {
        var config = new NativeEngine.Config(); config.deterministic = true;
        try (Game game = Game.create(config)) {
            Rpg g = open(game);
            g.state.minutes = 12 * 60;
            assertEquals(4, g.monsters.living(MonsterKind.GOBLIN));
            assertEquals(5, g.monsters.living(MonsterKind.BAT));
            assertEquals(2, g.monsters.living(MonsterKind.IMP));
            for (Monster m : g.monsters.all()) {
                assertTrue(m.level >= m.kind.minLevel && m.level <= m.kind.maxLevel);
                assertTrue(m.skills.combatLevel() >= Math.max(3, m.level - 6) && m.skills.combatLevel() <= m.level + 6, m.label() + " fights at combat level " + m.skills.combatLevel());
            }
            // far from the camp the goblins are about their own business, not after the player
            g.teleport(g.player, 64.5f, DevMap.FLOOR, 66.5f);
            for (int f = 0; f < 60 * 10; f++) assertTrue(game.tick(1f / 60));
            for (Monster m : g.monsters.all()) if (m.kind == MonsterKind.GOBLIN) assertNotEquals(g.player.id, m.target, "no free aggro across the map");
            // walk into the camp: noticed, called, attacked
            g.teleport(g.player, DevMap.GOBLIN_CAMP[0] + 1, DevMap.FLOOR, DevMap.GOBLIN_CAMP[1] + 3);
            float hp = g.player.hp;
            boolean engaged = false;
            for (int f = 0; f < 60 * 25; f++) {
                assertTrue(game.tick(1f / 60));
                for (Monster m : g.monsters.all()) if (m.kind == MonsterKind.GOBLIN && m.state == Monster.State.ENGAGE && m.target == g.player.id) engaged = true;
            }
            StringBuilder b = new StringBuilder();
            for (Monster m : g.monsters.all()) if (m.kind == MonsterKind.GOBLIN) b.append(m.inspect(g)).append('\n');
            assertTrue(engaged, "the goblins took on the intruder at their camp:\n" + b);
            assertTrue(g.player.hp < hp, "and struck them:\n" + b);
            // kill one: no crime, a body with its drop
            Monster gob = g.monsters.all().stream().filter(m -> m.kind == MonsterKind.GOBLIN && m.alive).findFirst().orElseThrow();
            long crimes = g.law.crimes;
            g.combat.resolve(g.player, gob, 999, 10, rpg.item.ItemDef.DamageType.SLASH, null, gob.x, gob.chestY(), gob.z, "test", false);
            for (int f = 0; f < 30; f++) assertTrue(game.tick(1f / 60));
            assertFalse(gob.alive);
            assertEquals(crimes, g.law.crimes, "killing a monster is no crime");
            assertTrue(gob.corpse != 0 && g.objects.get(gob.corpse) instanceof rpg.objects.Corpse c && c.contents.count(rpg.item.Items.BONES) == 1, "the goblin's bones are on its body");
            // saved and loaded
            var file = java.nio.file.Files.createTempFile("monsters", ".json");
            int alive = 0; for (Monster m : g.monsters.all()) if (m.alive) alive++;
            for (var e : g.state.all()) if (e.type().equals("MonsterKilled") || e.type().equals("ActorKilled") && g.monsters.byId(e.subject()) != null) System.out.println("[death] " + e.line());
            assertTrue(new SaveSystem(g).save(file));
            assertTrue(new SaveSystem(g).load(file));
            int after = 0; for (Monster m : g.monsters.all()) if (m.alive) after++;
            assertEquals(alive, after);
            for (int f = 0; f < 60; f++) assertTrue(game.tick(1f / 60));
        }
    }
}
