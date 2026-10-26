package rpg.econ;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import rpg.Ids;
import rpg.Rpg;
import rpg.actor.Actor;
import rpg.ai.Npc;
import rpg.interact.Result;
import rpg.item.Currency;
import rpg.item.ItemStack;
import rpg.item.Items;
import rpg.objects.Container;
import rpg.ui.Dialog;
import rpg.world.DevMap;

/**
 * The Bank of Gameworld, in DevTown (gamefixes): one teller at one counter, and an account for anyone who opens one -
 * the player or any NPC. An account is a set of vaults, each a grid the size of a person's own inventory (10 x 4): the
 * first account holds two (twice the inventory), and up to four more can be bought, each for more than the last. A vault
 * is a real container in the bank's strongroom, so what is in it is held exactly once, kept in saves and counted by
 * the item ledger like anything else. It is reached only across the counter, and only while the teller is there to
 * serve (the bank shuts when its teller sleeps, flees, or is dead).
 *
 * <p>NPCs bank too: someone carrying more coin than they need to work with takes the surplus in, and someone run short
 * draws from their account - real coins, carried there and back on foot.
 */
public final class Bank {
    /** A vault's grid: a person's own inventory, 10 across, 4 down. */
    public static final int VAULT_W = 10, VAULT_H = 4;
    /** The first account's vaults, and how many more may be bought. */
    public static final int FIRST_VAULTS = 2, MORE_VAULTS = 4;
    /** What each further vault costs (the first bought, the second ...). */
    public static final int[] VAULT_PRICE = {1000, 2000, 4000, 8000};
    /** NPC banking: coins above this go in, and below this some come out (when the account has them). */
    static final int KEEP_MAX = 400, KEEP_MIN = 25, DRAW = 120;

    private final Rpg g;
    public Bank(Rpg g) { this.g = g; }

    public Npc teller() { return g.actor(Ids.TELLER) instanceof Npc n ? n : null; }

    /** Where a customer stands to be served: before the counter, in front of the teller's place. */
    public float[] customerSpot() { return new float[]{DevMap.BANK_TELLER[0], DevMap.BANK_COUNTER_Z - 0.9f}; }

    /** Whether the bank is serving: the teller alive, at the counter, not asleep or running. */
    public boolean open() {
        Npc t = teller();
        return t != null && t.alive && t.loaded && !t.asleep() && t.goal != rpg.ai.Brain.Goal.FLEE && t.goal != rpg.ai.Brain.Goal.HIDE
                && Math.hypot(t.x - DevMap.BANK_TELLER[0], t.z - DevMap.BANK_TELLER[1]) < 1.6;
    }

    /** Why someone cannot be served now (null: they can). */
    public String refusal(Actor a) {
        if (!open()) return "There's nobody at the bank counter.";
        float[] at = customerSpot();
        if (Math.hypot(a.x - at[0], a.z - at[1]) > 2.6f) return "You need to be at the bank counter.";
        return null;
    }

    /** An account's vaults, in order (empty: no account). */
    public List<Container> vaults(long owner) {
        List<Container> l = new ArrayList<>();
        for (var o : g.objects.all()) if (o instanceof Container c && c.kind.equals("vault") && c.owner == owner) l.add(c);
        l.sort(Comparator.comparingLong(c -> c.id));
        return l;
    }
    public boolean hasAccount(long owner) { return !vaults(owner).isEmpty(); }

    /** Opens an account: its first vaults made in the strongroom. */
    public Result openAccount(Actor a, long req) {
        if (hasAccount(a.id)) return Result.no("You already have an account here.");
        String why = refusal(a);
        if (why != null) return Result.no(why);
        for (int i = 0; i < FIRST_VAULTS; i++) vault(a);
        g.state.commit(req, "BankAccountOpened", a.id, Ids.TELLER, DevMap.BETA, 0, a.name + ": " + FIRST_VAULTS + " vaults");
        return Result.ok();
    }

    /** The price of the next vault for an account, or -1 when it has all it may. */
    public int nextVaultPrice(long owner) {
        int bought = vaults(owner).size() - FIRST_VAULTS;
        return bought < 0 || bought >= MORE_VAULTS ? -1 : VAULT_PRICE[bought];
    }

    /** Buys one more vault, paid in coins from the purse (or, failing that, from the account itself). */
    public Result buyVault(Actor a, long req) {
        if (!hasAccount(a.id)) return Result.no("You need an account first.");
        String why = refusal(a);
        if (why != null) return Result.no(why);
        int price = nextVaultPrice(a.id);
        if (price < 0) return Result.no("Your account already has every vault the bank will rent you.");
        int purse = a.count(Items.COINS), held = coinsIn(a.id);
        if (purse + held < price) return Result.no("That costs " + Currency.text(price) + " G.");
        int fromPurse = Math.min(purse, price);
        if (fromPurse > 0) a.takeItems(Items.COINS, fromPurse);
        if (price > fromPurse) takeCoins(a.id, price - fromPurse);
        // the fee is the bank's: into the teller's till (a real holder, so the coins still exist once)
        Npc t = teller();
        ItemStack fee = new ItemStack(Items.COINS, price);
        if (t == null || !t.store(fee)) g.state.burn(Items.COINS, price);
        vault(a);
        g.state.commit(req, "BankVaultBought", a.id, Ids.TELLER, DevMap.BETA, 0, a.name + ": vault " + vaults(a.id).size() + " for " + price + " G");
        return Result.ok();
    }

    private Container vault(Actor a) {
        int n = vaults(a.id).size() + 1;
        Container c = new Container(g.state.newEntityId(), "bank_vault", "Bank vault " + n, "vault", DevMap.BANK_TELLER[0], DevMap.FLOOR - 6, DevMap.BANK_TELLER[1] + 1.5f, VAULT_W * VAULT_H, a.id);
        c.ownerOnly = true;
        return g.objects.add(c);
    }

    /** Coins held across an account's vaults. */
    public int coinsIn(long owner) { int n = 0; for (Container c : vaults(owner)) n += c.contents.count(Items.COINS); return n; }

    private int takeCoins(long owner, int amount) {
        int left = amount;
        for (Container c : vaults(owner)) {
            for (int i = 0; i < c.contents.size() && left > 0; i++) {
                ItemStack s = c.contents.get(i);
                if (s == null || s.def != Items.COINS) continue;
                int k = Math.min(left, s.amount);
                c.contents.take(i, k);
                left -= k;
            }
        }
        return amount - left;
    }

    /** Whether the player may open a vault right now (it is theirs and the teller is serving them). */
    public String vaultRefusal(Actor a, Container vault) {
        if (vault.owner != a.id) return "That isn't your vault.";
        return refusal(a);
    }

    // ------------------------------------------------------------------ NPCs and their money

    /** Why an NPC wants the bank now (null: they do not): too much coin to carry about, or too little to work with. */
    public String errand(Npc n) {
        if (n.id == Ids.TELLER || !n.alive || n.hostileToPlayer || n.posse) return null;
        if (DevMap.WILDS.equals(n.homeJurisdiction)) return null;
        if (g.time < n.bankAgain) return null;
        int purse = n.count(Items.COINS);
        if (purse > KEEP_MAX) return "carrying " + purse + " coins: the surplus to the bank";
        if (purse < KEEP_MIN && coinsIn(n.id) > 0) return "short of coin (" + purse + "): drawing on the account";
        return null;
    }

    /** At the counter: the surplus deposited, or a little drawn. Opens an account for them the first time. */
    public String serve(Npc n) {
        if (refusal(n) != null) return null;
        long req = g.state.newRequestId();
        int purse = n.count(Items.COINS);
        n.bankAgain = g.time + 900;
        if (purse > KEEP_MAX) {
            if (!hasAccount(n.id)) openAccount(n, req);
            int deposit = purse - KEEP_MAX / 2, moved = 0;
            for (Container c : vaults(n.id)) {
                if (moved >= deposit) break;
                ItemStack pile = new ItemStack(Items.COINS, deposit - moved);
                if (c.contents.add(pile)) moved = deposit;
            }
            if (moved > 0) n.takeItems(Items.COINS, moved);
            g.state.commit(req, "BankDeposit", n.id, Ids.TELLER, DevMap.BETA, 0, n.name + " deposited " + moved + " G");
            return moved > 0 ? "deposited " + moved + " G" : null;
        }
        int drawn = takeCoins(n.id, DRAW);
        if (drawn > 0) n.store(new ItemStack(Items.COINS, drawn));
        g.state.commit(req, "BankWithdrawal", n.id, Ids.TELLER, DevMap.BETA, 0, n.name + " withdrew " + drawn + " G");
        return drawn > 0 ? "withdrew " + drawn + " G" : null;
    }

    // ------------------------------------------------------------------ the teller's conversation

    /** What the teller offers the player across the counter. */
    public void choices(Npc teller, Dialog d) {
        var p = g.player;
        if (!hasAccount(p.id)) {
            d.choice("I'd like to open an account.", () -> {
                Result r = openAccount(p, g.state.newRequestId());
                if (r.rejected()) { g.chat.warn(r.reason()); return; }
                g.ai.say(teller, "There. Two vaults in your name, each as big as what you carry. Your things are safe with us.");
                openVault(p, vaults(p.id).get(0));
            });
        } else {
            d.choice("I'd like to see my vaults.", () -> openVault(p, vaults(p.id).get(0)));
            int price = nextVaultPrice(p.id);
            if (price > 0) d.choice("I'd like another vault (" + Currency.text(price) + " G).", () -> {
                Result r = buyVault(p, g.state.newRequestId());
                if (r.rejected()) g.chat.warn(r.reason()); else g.ai.say(teller, "Done. Vault " + vaults(p.id).size() + " is yours.");
            });
        }
        d.choice("How does the bank work?", () -> g.ui.dialog(new Dialog(teller.name, "An account is two vaults, each as big as your own pack. You can rent up to four more: "
                + Currency.text(VAULT_PRICE[0]) + " G for the first, doubling each time. Anything you leave stays exactly where you left it. We open when I'm at this counter, and not before.")));
    }

    private void openVault(Actor p, Container vault) {
        String why = vaultRefusal(p, vault);
        if (why != null) { g.chat.warn(why); return; }
        g.ui.openContainer(vault);
        g.state.commit(g.state.newRequestId(), "ContainerOpened", p.id, vault.id, DevMap.BETA, 0, vault.name);
    }
}
