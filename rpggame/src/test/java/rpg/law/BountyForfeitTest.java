package rpg.law;

import static org.junit.jupiter.api.Assertions.*;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import org.junit.jupiter.api.*;
import rpg.*;
import rpg.actor.Appearance;
import rpg.ai.*;
import rpg.item.ItemStack;
import rpg.item.Items;
import rpg.world.DevMap;

/**
 * Gamefixes: killed with a bounty over 25 G, the player loses a share of what they carried, scaling with the bounty up to
 * everything; a death without a bounty (or a small one) still sends it all to the grave for the undertaker's buy-back.
 */
class BountyForfeitTest {
    private Game game;
    private Rpg g;
    @BeforeEach void open() {
        var cfg = new NativeEngine.Config(); cfg.deterministic = true;
        game = Game.create(cfg); game.activate(DevMap.build(game.engine()));
        g = game.add(new Rpg(game, "Forfeit", new Appearance(), true));
        for (Npc npc : g.npcs) npc.memories.clear();
        g.player.inv.clear();
        for (int i = 0; i < 10; i++) g.player.store(new ItemStack(Items.get("bronze_dagger"), 1));
        g.player.store(new ItemStack(Items.COINS, 200));
    }
    @AfterEach void close() { game.close(); }

    private void bounty(int amount) {
        Npc bram = (Npc) g.actor(Ids.LAWMAN_A);
        Npc seer = g.npcs.stream().filter(n -> n.role == Npc.Role.VILLAGER && DevMap.ALPHA.equals(n.homeJurisdiction)).findFirst().orElseThrow();
        Memory m = new Memory("THEFT", g.player.id, seer.id, DevMap.ALPHA, g.state.minutes, 1f, "witnessed", 33, amount * 2, "theft");
        seer.remember(m, 24);
        g.teleport(seer, bram.x + 1, DevMap.FLOOR, bram.z);
        assertTrue(g.law.report(seer, m, bram).committed());
        assertEquals(amount, g.law.bounty(g.player.id, DevMap.ALPHA));
    }
    private int carried() { int n = 0; for (var grid : g.player.grids()) n += grid.stacks().size(); return n + g.player.eq.stacks().size(); }

    @Test void noBountyLosesNothing() {
        int before = carried();
        assertEquals(0, g.law.forfeitOnDeath(g.player));
        assertEquals(before, carried());
        bounty(20);
        assertEquals(0, g.law.forfeitOnDeath(g.player), "a petty bounty costs nothing at death");
    }

    @Test void aModerateBountyTakesAShare() {
        bounty(150);
        int before = carried();
        int taken = g.law.forfeitOnDeath(g.player);
        assertTrue(taken > 0 && carried() > 0, "some taken, some left (" + taken + " of " + before + ")");
        assertTrue(g.law.forfeitShare(150) > 0.2f && g.law.forfeitShare(150) < 0.4f);
    }

    @Test void aGreatBountyTakesEverything() {
        bounty(600);
        Npc bram = (Npc) g.actor(Ids.LAWMAN_A);
        int daggers = bram.count(Items.get("bronze_dagger"));
        g.law.forfeitOnDeath(g.player);
        assertEquals(0, carried(), "everything is seized at a bounty this great");
        assertTrue(bram.count(Items.get("bronze_dagger")) > daggers || g.objects.custody.contents.count(Items.get("bronze_dagger")) > 0, "the seized goods went to the law");
    }
}
