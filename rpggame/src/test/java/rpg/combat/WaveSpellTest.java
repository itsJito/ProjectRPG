package rpg.combat;

import static org.junit.jupiter.api.Assertions.*;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import org.junit.jupiter.api.Test;
import rpg.Rpg;
import rpg.actor.Appearance;
import rpg.ai.Npc;
import rpg.combat.Spells.Book;
import rpg.combat.Spells.Spell;
import rpg.combat.Spells.Tier;
import rpg.skill.Skill;
import rpg.world.DevMap;

/**
 * Gamefixes: Earth Wave, Water Wave, Fire Wave (and the rest of the tier) hit nothing. A Wave is a crescent as wide as a
 * person: cast down a line with one foe a little off its middle and another behind and to the other side, it strikes both.
 */
class WaveSpellTest {
    @Test void everyWaveStrikesWhatItsBreadthPassesOver() {
        for (Spell s : Spell.values()) {
            if (s.tier != Tier.WAVE) continue;
            var cfg = new NativeEngine.Config(); cfg.deterministic = true;
            try (Game game = Game.create(cfg)) {
                game.activate(DevMap.build(game.engine()));
                Rpg g = game.add(new Rpg(game, "Waves", new Appearance(), true));
                Npc a = null, b = null;
                for (Npc n : g.npcs) { if (a == null) a = n; else if (b == null) b = n; else g.ai.unload(n); }
                g.player.skills.setLevel(Skill.MAGIC, 99); g.player.restore(); g.player.book = Book.SUN;
                float[] at = rpg.TestLanes.open(g);
                float px = at[0], pz = at[1];
                g.teleport(g.player, px, DevMap.FLOOR, pz);
                // the player looks along -z (yaw 0): one foe 6 m ahead and 0.45 m to the right, one 9 m ahead and 0.5 m left
                g.teleport(a, px + 0.45f, DevMap.FLOOR, pz - 6);
                g.teleport(b, px - 0.5f, DevMap.FLOOR, pz - 9);
                for (int i = 0; i < 10; i++) game.tick(1f / 60);
                float hpA = a.hp, hpB = b.hp;
                g.player.mana = g.player.maxMana(); g.player.cs.castCooldown = 0;
                assertTrue(g.combat.cast(g.player, s, 0, 0), s.title + " cast");
                var wave = g.combat.projectiles.get(g.combat.projectiles.size() - 1);
                wave.potential = 1000; wave.impact = 0;   // a sure hit: the test is whether it touches, not the roll
                for (int i = 0; i < 90; i++) game.tick(1f / 60);
                assertTrue(a.hp < hpA || !a.alive, s.title + " struck the foe off its middle");
                assertTrue(b.hp < hpB || !b.alive, s.title + " rolled on and struck the one behind");
            }
        }
    }
}
