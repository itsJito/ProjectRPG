package rpg.econ;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import org.junit.jupiter.api.Test;
import rpg.Ids;
import rpg.Rpg;
import rpg.SaveSystem;
import rpg.actor.Appearance;
import rpg.ai.Npc;
import rpg.item.ItemStack;
import rpg.item.Items;
import rpg.world.DevMap;

import static org.junit.jupiter.api.Assertions.*;

/** The Bank of ASHENWRATH (gamefixes): accounts twice the inventory, up to four more vaults bought, NPCs banking their coin. */
class BankTest {
    private static void settle(Game game, Rpg g, Npc teller, int frames) {
        for (int i = 0; i < frames; i++) { teller.goal = rpg.ai.Brain.Goal.WORK; assertTrue(game.tick(1f / 60)); }
    }

    @Test void anAccountHoldsTwiceTheInventoryAndGrowsByBoughtVaults() throws Exception {
        var config = new NativeEngine.Config(); config.deterministic = true;
        try (Game game = Game.create(config)) {
            game.activate(DevMap.build(game.engine()));
            Rpg g = game.add(new Rpg(game, "Bank", new Appearance(), true));
            g.state.minutes = 11 * 60;
            Npc teller = (Npc) g.actor(Ids.TELLER);
            assertNotNull(teller, "the bank has a teller");
            g.teleport(teller, DevMap.BANK_TELLER[0], DevMap.FLOOR, DevMap.BANK_TELLER[1]);
            float[] at = g.bank.customerSpot();
            g.teleport(g.player, at[0], DevMap.FLOOR, at[1]);
            settle(game, g, teller, 120);
            assertTrue(g.bank.open(), "the bank is open with the teller at the counter (" + teller.x + "," + teller.z + " " + teller.goal + ")");
            g.give(g.player,Items.COINS,25);
            int openingPurse=g.player.count(Items.COINS);
            assertTrue(g.bank.openAccount(g.player, 1).committed());
            assertEquals(openingPurse-25,g.player.count(Items.COINS));
            var vaults = g.bank.vaults(g.player.id);
            int cells = 0; for (var v : vaults) cells += v.contents.w * v.contents.h;
            assertEquals(2 * g.player.inv.w * g.player.inv.h, cells, "the first account is twice the inventory");
            // a deposit through the vault, as the window does it
            g.give(g.player, Items.get("uncut_ruby"), 1);
            int slot = g.player.inv.first(Items.get("uncut_ruby"));
            assertTrue(g.interact.request(g.player, vaults.get(0), "deposit:0:" + slot).committed());
            assertEquals(1, vaults.get(0).contents.count(Items.get("uncut_ruby")));
            // "Move All" (gamefixes): the whole pack into the vault at once; what is worn stays worn
            var worn = g.player.main();
            g.give(g.player, Items.BREAD, 3);
            int bread = g.player.count(Items.BREAD), coins = g.player.count(Items.COINS);
            assertTrue(g.interact.request(g.player, vaults.get(0), "depositall").committed());
            assertTrue(g.player.grids().stream().allMatch(grid -> grid.stacks().isEmpty()), "the pack is empty: " + g.player.inv.stacks());
            assertEquals(bread, vaults.get(0).contents.count(Items.BREAD));
            assertEquals(coins, vaults.get(0).contents.count(Items.COINS));
            assertSame(worn, g.player.main(), "the wielded item stays in the hand");
            assertTrue(g.interact.request(g.player, vaults.get(0), "depositall").rejected(), "nothing left to move");
            for (int i = 0; i < vaults.get(0).contents.size(); i++) if (vaults.get(0).contents.get(i) != null && vaults.get(0).contents.get(i).def == Items.COINS) assertTrue(g.interact.request(g.player, vaults.get(0), "take:" + i).committed());
            // four more vaults, each paid for
            g.give(g.player, Items.COINS, 20000);
            for (int i = 0; i < Bank.MORE_VAULTS; i++) assertTrue(g.bank.buyVault(g.player, 10 + i).committed(), "vault " + (i + 3));
            assertEquals(1, g.bank.vaults(g.player.id).size());
            assertSame(vaults.get(0),g.bank.vaults(g.player.id).get(0));
            assertEquals((Bank.FIRST_VAULTS+Bank.MORE_VAULTS)*40,vaults.get(0).contents.size());
            assertTrue(g.bank.buyVault(g.player, 20).rejected(), "no more than four extra");
            assertEquals(openingPurse - 25 + 20000 - 1000 - 2000 - 4000 - 8000, g.player.count(Items.COINS));
            // kept across a save
            var file = java.nio.file.Files.createTempFile("bank", ".json");
            assertTrue(new SaveSystem(g).save(file));
            assertTrue(new SaveSystem(g).load(file));
            assertEquals(1, g.bank.vaults(g.player.id).size());
            assertEquals(240,g.bank.vaults(g.player.id).get(0).contents.size());
            assertEquals(1, g.bank.vaults(g.player.id).get(0).contents.count(Items.get("uncut_ruby")));
            // the bank shuts when its teller is away
            g.teleport(teller, DevMap.BANK_TELLER[0] + 4, DevMap.FLOOR, DevMap.BANK_Z0 - 6);
            assertFalse(g.bank.open());
            assertNotNull(g.bank.vaultRefusal(g.player, g.bank.vaults(g.player.id).get(0)));
        }
    }

    @Test void aMerchantWithASurplusBanksIt() {
        var config = new NativeEngine.Config(); config.deterministic = true;
        try (Game game = Game.create(config)) {
            game.activate(DevMap.build(game.engine()));
            Rpg g = game.add(new Rpg(game, "Bank errand", new Appearance(), true));
            g.state.minutes = 10 * 60;
            g.teleport(g.player, 64.5f, DevMap.FLOOR, 66.5f);
            Npc teller = (Npc) g.actor(Ids.TELLER);
            Npc gus = (Npc) g.actor(Ids.MERCHANT_B);
            for (Npc n : g.npcs) if (n != teller && n != gus) g.ai.unload(n);
            gus.store(new ItemStack(Items.COINS, 900)); g.state.mint(Items.COINS, 900);
            int carried = gus.count(Items.COINS);
            boolean banked = false;
            for (int f = 0; f < 60 * 90 && !banked; f++) { assertTrue(game.tick(1f / 60)); banked = g.bank.coinsIn(gus.id) > 0; }
            assertTrue(banked, "Gus took his surplus to the bank (" + gus.goal + ": " + gus.lastOutcome + ", " + gus.goalReason + ")");
            assertEquals(carried-25, gus.count(Items.COINS) + g.bank.coinsIn(gus.id), "every coin is either carried or in the account");
            assertTrue(gus.count(Items.COINS) <= Bank.KEEP_MAX);
        }
    }
}
