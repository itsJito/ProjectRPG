package rpg.law;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import org.junit.jupiter.api.Test;
import rpg.Rpg;
import rpg.actor.Appearance;
import rpg.ai.Npc;
import rpg.world.DevMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * gamefixes: bounty hunters have no knowledge of the player beyond what someone saw - they start from, and search,
 * the last area the kingdom knows of, and a later sighting moves them only once word of it could have reached them.
 */
class PosseKnowledgeTest {
    @Test void huntersSearchTheLastKnownAreaNotThePlayer() {
        var config = new NativeEngine.Config(); config.deterministic = true;
        try (Game game = Game.create(config)) {
            game.activate(DevMap.build(game.engine()));
            Rpg g = game.add(new Rpg(game, "Posse knowledge", new Appearance(), true));
            g.player.skills.setLevel(rpg.skill.Skill.HEALTH, 100); g.player.restore();
            for (Npc n : g.npcs) { g.ai.unload(n); n.aware.clear(); }
            // the player was last seen by a townsperson at the square, then slipped away to the far corner unseen
            Npc witness = g.npcs.stream().filter(n -> n.homeJurisdiction.equals(DevMap.ALPHA)).findFirst().orElseThrow();
            var seen = new rpg.ai.Stealth.Aware(); seen.x = 100; seen.z = 64; seen.seenAt = g.time; witness.aware.put(g.player.id, seen);
            g.teleport(g.player, 24, DevMap.FLOOR, 24);
            g.law.all().computeIfAbsent(DevMap.ALPHA, k -> new java.util.HashMap<>()).put(g.player.id, 1200);
            assertTrue(g.law.posses.form(DevMap.ALPHA, 1200, false));
            var hunters = g.law.posses.members();
            assertFalse(hunters.isEmpty(), "a posse formed");
            for (Npc h : hunters) assertTrue(Math.hypot(h.hunt.x - 100, h.hunt.z - 64) < 1, "the search starts at the last sighting, not at the player");
            for (int f = 0; f < 60 * 30; f++) assertTrue(game.tick(1f / 60));
            for (Npc h : hunters) if (h.alive && !g.ai.canSee(h, g.player))
                assertTrue(Math.hypot(h.hunt.x - g.player.x, h.hunt.z - g.player.z) > 20, () -> "an unseen player stays unknown: " + h.name + " searching at " + h.hunt.x + "," + h.hunt.z);
            // a new sighting reaches them, but only after word could have travelled
            var later = new rpg.ai.Stealth.Aware(); later.x = 40; later.z = 30; later.seenAt = g.time; witness.aware.put(g.player.id, later);
            for (int f = 0; f < 60 * 5; f++) assertTrue(game.tick(1f / 60));
            for (Npc h : hunters) if (h.alive && !g.ai.canSee(h, g.player)) assertTrue(Math.hypot(h.hunt.x - 40, h.hunt.z - 30) > 1, "word does not arrive instantly");
            for (int f = 0; f < 60 * 15; f++) assertTrue(game.tick(1f / 60));
            boolean heard = false;
            for (Npc h : hunters) if (h.alive && h.hunt != null && Math.hypot(h.hunt.x - 40, h.hunt.z - 30) < 6) heard = true;
            assertTrue(heard, "word of the new sighting reached the hunters");
        }
    }
}
