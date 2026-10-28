package rpg.audio;

import static org.junit.jupiter.api.Assertions.*;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import org.junit.jupiter.api.Test;
import rpg.Rpg;
import rpg.actor.Appearance;
import rpg.monster.MonsterKind;
import rpg.world.DevMap;

/** Gamefixes: every monster sounds like itself - its own recorded idle, alarm, hurt and death banks, none a person's. */
class MonsterVoiceTest {
    @Test void everyMonsterKindHasItsOwnLoadedVoice() {
        var config = new NativeEngine.Config(); config.deterministic = true;
        try (Game game = Game.create(config)) {
            game.activate(DevMap.build(game.engine()));
            Rpg g = game.add(new Rpg(game, "Voices", new Appearance(), true));
            assertNotNull(g.sfx);
            for (MonsterKind k : MonsterKind.values()) for (String bank : new String[]{k.idleSound, k.alarmSound, k.hurtSound, k.deathSound}) {
                assertTrue(g.sfx.has(bank), k + ": no sound loaded for " + bank);
                assertFalse(bank.startsWith("hurt.") || bank.startsWith("death.") || bank.startsWith("voice"), k + " uses a person's voice: " + bank);
            }
            assertTrue(g.sfx.has("body.fall"));
        }
    }
}
