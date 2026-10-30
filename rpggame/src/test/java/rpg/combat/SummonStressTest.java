package rpg.combat;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import org.junit.jupiter.api.*;
import rpg.*;
import rpg.actor.Appearance;
import rpg.ai.Npc;
import rpg.skill.Skill;
import rpg.world.DevMap;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Gamefixes: the game crashed with too many familiars summoned. Allies are called again and again - left to serve out
 * their time, killed where they stand, killed and then left to expire as corpses, called while others still lie dead
 * - with a fight going on round them, across a save and a load.
 */
class SummonStressTest {
    private Game game;
    private Rpg g;
    @BeforeEach void start() {
        var config = new NativeEngine.Config(); config.deterministic = true;
        game = Game.create(config); game.activate(DevMap.build(game.engine()));
        g = game.add(new Rpg(game, "Summons", Appearance.henric(), true));
        g.state.minutes = 600;
        g.teleport(g.player, 60.5f, DevMap.FLOOR, 70.5f);
        g.player.skills.setLevel(Skill.MAGIC, 99); g.player.skills.setLevel(Skill.HEALTH, 99); g.player.restore();
        if (g.player instanceof rpg.actor.Player p) p.book = Spells.Book.FOGISH;
        game.tick(0);
    }
    @AfterEach void close() { game.close(); }
    private void frames(int n) { for (int i = 0; i < n; i++) assertTrue(game.tick(1f / 60)); }

    private int call(int tries) {
        int made = 0;
        for (int i = 0; i < tries; i++) {
            g.player.mana = g.player.maxMana();
            g.player.cs.castCooldown = 0;
            if (g.magic.cast(g.player, Spells.Spell.NECROCALL, (float) (i * 1.3), 0)) made++;
            frames(3);
        }
        return made;
    }

    @Test void summoningOverAndOverNeverBreaksTheWorld(@org.junit.jupiter.api.io.TempDir java.nio.file.Path dir) {
        int npcs = g.npcs.size(), made = 0;
        for (int round = 0; round < 25; round++) {
            // a fight on: the bandits and the brute come at the caster and its allies
            for (long id : new long[]{Ids.BANDIT_1, Ids.BANDIT_2, Ids.BRUTE}) {
                Npc b = (Npc) g.actor(id);
                if (b == null || !b.alive) continue;
                if (!b.loaded) g.ai.reload(b);
                g.teleport(b, g.player.x + 3 + (id % 3), DevMap.FLOOR, g.player.z + 2);
                b.hostileToPlayer = true; b.hp = b.maxHp(); b.nextThink = 0;
            }
            g.player.hp = g.player.maxHp();
            made += call(4);
            long serving = g.npcs.stream().filter(n -> n.summonOwner == g.player.id && n.alive).count();
            assertTrue(serving <= 2, "at most two allies at once, had " + serving);
            switch (round % 4) {
                case 0 -> frames(60);                                                      // serving
                case 1 -> { for (Npc n : new java.util.ArrayList<>(g.npcs)) if (n.summonOwner == g.player.id) g.combat.environmentalDamage(n, 9999, "test"); frames(90); }   // cut down
                case 2 -> g.advanceTime(1, "allies expire");                                // their time runs out
                default -> { for (Npc n : new java.util.ArrayList<>(g.npcs)) if (n.summonOwner == g.player.id && n.alive) g.combat.resolve(g.player, n, 99999, 0, rpg.item.ItemDef.DamageType.SLASH, null, n.x, n.chestY(), n.z, "test", true); g.advanceTime(1, "corpses expire"); frames(30); }
            }
            frames(40);
        }
        assertTrue(made >= 25, "allies actually called: " + made);
        // a fallen ally leaves no body behind to be fetched, searched or carried: it is gone the moment it falls
        for (Npc n : new java.util.ArrayList<>(g.npcs)) if (n.summonOwner == g.player.id) g.magic.dismiss(n);
        // The fresh-cast assertion needs a free landing after the crowded battle above.
        float[] lane=rpg.TestLanes.open(g);g.teleport(g.player,lane[0],DevMap.FLOOR,lane[1]);game.tick(0);
        assertEquals(1, call(1), "a fresh ally");
        Npc ally = g.npcs.stream().filter(n -> n.summonOwner == g.player.id).findFirst().orElseThrow();
        long objects = g.objects.all().stream().filter(o -> o instanceof rpg.objects.Corpse c && !c.removed).count();
        g.combat.environmentalDamage(ally, 9999, "test");
        frames(30);
        assertFalse(g.npcs.contains(ally));
        assertEquals(objects, g.objects.all().stream().filter(o -> o instanceof rpg.objects.Corpse c && !c.removed).count(), "no corpse for a conjured ally");
        // the dismissed are gone entirely: nobody is left behind in the town's lists, and the world still saves and loads
        frames(60 * 35);
        assertEquals(npcs, g.npcs.size(), "dismissed allies must leave the world");
        var saves = new SaveSystem(g);
        assertTrue(saves.save(dir.resolve("summons.json")));
        assertTrue(saves.load(dir.resolve("summons.json")));
        frames(120);
    }
}
