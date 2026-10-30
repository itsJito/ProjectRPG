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
import rpg.ui.Dialog;
import rpg.world.DevMap;

/**
 * Gamefixes: a bounty could not be paid to the guard who halted the player (the reply went nowhere and the watch kept
 * after them), and the watch attacked over bounties as small as 10 G. A halting guard's "Pay" settles the bounty; a
 * petty bounty is a fine asked for face to face, and putting it off is never fought over.
 */
class BountyPaymentTest {
    private Game game;
    private Rpg g;
    @BeforeEach void open() {
        var cfg = new NativeEngine.Config(); cfg.deterministic = true;
        game = Game.create(cfg); game.activate(DevMap.build(game.engine()));
        g = game.add(new Rpg(game, "Pay", new Appearance(), true));
        for (Npc npc : g.npcs) npc.memories.clear();
        g.state.minutes = 600;
        g.player.store(new ItemStack(Items.COINS, 500));
    }
    @AfterEach void close() { game.close(); }
    private void frames(int n) { for (int i = 0; i < n; i++) assertTrue(game.tick(1f / 60)); }
    private static boolean law(Npc n) { return n.role == Npc.Role.GUARD || n.role == Npc.Role.LAWMAN; }

    private void wanted(int value) {
        Npc bram = (Npc) g.actor(Ids.LAWMAN_A);
        Npc seer = g.npcs.stream().filter(n -> n.role == Npc.Role.VILLAGER && DevMap.ALPHA.equals(n.homeJurisdiction)).findFirst().orElseThrow();
        Memory m = new Memory("THEFT", g.player.id, seer.id, DevMap.ALPHA, g.state.minutes, 1f, "witnessed", 77, value, "theft");
        seer.remember(m, 24);
        g.teleport(seer, bram.x + 1, DevMap.FLOOR, bram.z);
        assertTrue(g.law.report(seer, m, bram).committed());
    }
    private long hitsOnPlayerSince(long rev) {
        return g.state.all().stream().filter(e -> e.revision() > rev && e.type().equals("ActorHit") && e.subject() == g.player.id).count();
    }

    @Test void payingTheGuardWhoHaltedYouSettlesIt() {
        wanted(120);   // 60 G: above the pursuit line
        Npc guard = (Npc) g.actor(Ids.GUARD);
        g.teleport(g.player, guard.x + 1.5f, DevMap.FLOOR, guard.z);
        guard.fightTarget = g.player.id; guard.goal = Brain.Goal.FIGHT;
        Dialog d = g.law.halt(guard, g.player);
        assertNotNull(d, "no halt dialog");
        assertTrue(guard.fightTarget == g.player.id && !guard.canTalk(g), "the halting guard is squared up (cannot chat)");
        assertTrue(d.stands(g), "the replies to a halt still stand while the guard is in his fight stance");
        int coins = g.player.count(Items.COINS);
        d.choices.get(0).action().run();
        assertEquals(0, g.law.bounty(g.player.id, DevMap.ALPHA), "paid");
        assertEquals(coins - 60, g.player.count(Items.COINS));
        long rev = g.state.revision;
        frames(60 * 20);
        long after = g.npcs.stream().filter(n -> law(n) && n.alive && (n.fightTarget == g.player.id || n.hunt != null && n.hunt.target == g.player.id)).count();
        assertEquals(0, after, "officers still after the player once the bounty was paid");
        assertEquals(0, hitsOnPlayerSince(rev));
    }

    @Test void aPettyBountyIsAFineNeverAFight() {
        wanted(20);    // 10 G
        assertTrue(g.law.petty(g.player.id, DevMap.ALPHA));
        Npc guard = (Npc) g.actor(Ids.GUARD);
        g.teleport(g.player, guard.x + 5, DevMap.FLOOR, guard.z);
        long rev = g.state.revision;
        // the guard notices and walks up to ask for the fine
        boolean asked = false;
        for (int i = 0; i < 60 * 15 && !asked; i++) { frames(1); asked = "A word with you.".equals(guard.speech) || guard.lastOutcome.contains("halt"); }
        assertTrue(asked, "the guard comes over about the fine (" + guard.goal + ", " + guard.lastOutcome + ")");
        // the player walks off without answering: nobody draws on them
        g.teleport(g.player, guard.x + 6, DevMap.FLOOR, guard.z + 3);
        frames(60 * 30);
        assertEquals(0, hitsOnPlayerSince(rev), "struck over a 10 G fine");
        assertFalse(g.law.resisting(g.player.id));
        assertEquals(10, g.law.bounty(g.player.id, DevMap.ALPHA), "the fine stays on the books");
        long chasing = g.npcs.stream().filter(n -> law(n) && n.alive && n.fightTarget == g.player.id).count();
        assertEquals(0, chasing);
    }
}
