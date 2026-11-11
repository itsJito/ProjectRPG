package rpg.ai;

import static org.junit.jupiter.api.Assertions.*;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import org.junit.jupiter.api.*;
import rpg.Ids;
import rpg.Rpg;
import rpg.actor.Appearance;
import rpg.item.ItemDef;
import rpg.item.ItemStack;
import rpg.item.Items;
import rpg.world.Blocks;
import rpg.world.DevMap;

/** (0.3.7, gamefixes) people fighting in a group round the player and come at their back while another holds their front. */
class FlankingTest {
    private Game game;
    private Rpg g;
    @BeforeEach void open() {
        var cfg = new NativeEngine.Config(); cfg.deterministic = true;
        game = Game.create(cfg); game.activate(DevMap.build(game.engine()));
        g = game.add(new Rpg(game, "Flanking", new Appearance(), true));
        for (Npc n : g.npcs) if (n.id != Ids.BANDIT_1 && n.id != Ids.BRUTE) g.ai.unload(n);
        for (int x = 56; x <= 74; x++) for (int z = 50; z <= 71; z++) {
            g.setBlock(x, DevMap.FLOOR - 1, z, Blocks.GRASS);
            for (int y = DevMap.FLOOR; y <= DevMap.FLOOR + 4; y++) g.setBlock(x, y, z, Blocks.AIR);
        }
        g.state.minutes = 13 * 60;
        g.player.skills.setLevel(rpg.skill.Skill.HEALTH, 99); g.player.restore();
        assertTrue(game.tick(0));
    }
    @AfterEach void close() { game.close(); }

    @Test void theSecondComesRoundToThePlayersBack() {
        Npc front = (Npc) g.actor(Ids.BANDIT_1), second = (Npc) g.actor(Ids.BRUTE);
        for (Npc b : new Npc[]{front, second}) { b.hostileToPlayer = true; b.eq.set(ItemDef.Slot.MAIN, new ItemStack(Items.get("iron_sword"), 1)); b.nextThink = 0; b.spawnX = 64.5f; b.spawnZ = 57f; b.skills.setLevel(rpg.skill.Skill.HEALTH, 99); b.restore(); }
        // the player faces the first, who is right in front; the second comes from the same side
        g.teleport(g.player, 64.5f, DevMap.FLOOR, 60.5f);
        g.teleport(front, 64.5f, DevMap.FLOOR, 58.6f);
        g.teleport(second, 65.5f, DevMap.FLOOR, 55.0f);
        int behindFrames = 0; double minAngle = 9;
        for (int i = 0; i < 60 * 12; i++) {
            // the player keeps turning to face the first bandit (as a player locked in a duel does)
            g.player.yaw = (float) Math.atan2(-(front.x - g.player.x), -(front.z - g.player.z));
            g.player.hp = g.player.maxHp();
            game.tick(1f / 60);
            float to = (float) Math.atan2(-(second.x - g.player.x), -(second.z - g.player.z));
            double off = Math.abs(rpg.actor.Actor.wrap(to - g.player.yaw));
            if (second.distTo(g.player) < 3 && off > Brain.BEHIND) behindFrames++;
        }
        assertTrue(behindFrames > 60, "the second bandit got round behind for " + behindFrames + " frames (" + second.lastOutcome + ")");
    }
}
