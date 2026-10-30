package rpg.ai;

import static org.junit.jupiter.api.Assertions.*;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import org.junit.jupiter.api.*;
import rpg.*;
import rpg.actor.Appearance;
import rpg.item.ItemStack;
import rpg.item.Items;
import rpg.world.DevMap;

/**
 * Gamefixes 0.2.2: the fisherman stopped working when his pack was full. A worker with a full pack sells what a store
 * will buy, and banks what none will, then goes back to work.
 */
class FullPackTest {
    private Game game;
    private Rpg g;
    @BeforeEach void open() {
        var cfg = new NativeEngine.Config(); cfg.deterministic = true;
        game = Game.create(cfg); game.activate(DevMap.build(game.engine()));
        g = game.add(new Rpg(game, "Pack", new Appearance(), true));
        g.state.minutes = 10 * 60;
    }
    @AfterEach void close() { game.close(); }
    private void frames(int n) { for (int i = 0; i < n; i++) assertTrue(game.tick(1f / 60)); }

    private Npc fisherman() { return (Npc) g.actor(Ids.FISHERMAN); }

    private void fill(Npc n, rpg.item.ItemDef d) {
        while (n.inv.freeSlots() > 0) if (!n.store(new ItemStack(d, 1))) break;
    }

    @Test void aFullPackIsSoldWhereAStoreBuys() {
        Npc f = fisherman();
        fill(f, Items.RAW_SHRIMP);
        int before = f.count(Items.RAW_SHRIMP);
        boolean emptied = false;
        for (int s = 0; s < 240 && !emptied; s++) { frames(60); emptied = f.inv.freeSlots() >= 6 || f.count(Items.RAW_SHRIMP) < before - 5; }
        assertTrue(emptied, "the fisherman made room (" + f.lastOutcome + ", goal " + f.goal + ", free " + f.inv.freeSlots() + ")");
        assertTrue(g.state.all().stream().anyMatch(e -> e.type().equals("PackFull") && e.actor() == f.id));
    }

    @Test void whatNoStoreBuysIsBanked() {
        Npc f = fisherman();
        // every store's shelves already full of it: nobody will buy any more
        for (var shop : new rpg.econ.Economy.Shop[]{g.econ.shopA, g.econ.shopB}) for (int i = 0; i < 12; i++) shop.stock.add(new ItemStack(Items.RAW_SHRIMP, 1, shop.merchant, false, 0));
        f.store(new ItemStack(Items.COINS, 60));
        fill(f, Items.RAW_SHRIMP);
        boolean banked = false;
        for (int s = 0; s < 300 && !banked; s++) { frames(60); banked = g.bank.vaults(f.id).stream().anyMatch(c -> c.contents.count(Items.RAW_SHRIMP) > 0); }
        assertTrue(banked, "the fisherman banked the catch (" + f.lastOutcome + ", goal " + f.goal + ", free " + f.inv.freeSlots() + ")");
    }
}
