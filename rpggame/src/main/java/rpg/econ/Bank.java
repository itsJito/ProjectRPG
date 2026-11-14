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
import rpg.item.ItemDef;
import rpg.item.ItemStack;
import rpg.item.Items;
import rpg.objects.Container;
import rpg.ui.Dialog;
import rpg.world.DevMap;

/**
 * The Bank of ASHENWRATH, in DevTown (gamefixes): one teller at one counter, and an account for anyone who opens one -
 * the player or any NPC. An account owns one expandable vault. Its initial capacity is twice a person's inventory;
 * four expansions each append another inventory of space. A vault
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
    /** Initial inventory-sized capacity units, maximum expansions, and the account opening fee. */
    public static final int FIRST_VAULTS = 2, MORE_VAULTS = 4, ACCOUNT_PRICE = 25;
    /** Price of each capacity expansion. */
    public static final int[] VAULT_PRICE = {1000, 2000, 4000, 8000};
    /** NPC banking: coins above this go in, and below this some come out (when the account has them). */
    static final int KEEP_MAX = 400, KEEP_MIN = 25, DRAW = 120;

    private final Rpg g;
    public Bank(Rpg g) { this.g = g; }

    /**
     * (0.4.4, gamefixes: every kingdom its own bankhouse) A branch of the bank: its banker, where they stand behind the
     * counter and where a customer stands before it, its kingdom. The accounts are one ledger: a vault opened at any
     * branch is reached at every other.
     */
    public record Branch(String name, String jurisdiction, long teller, float[] tellerSpot, float[] customerSpot) {}
    private List<Branch> branches;

    /** Every branch (the devmap's one; on ShadowVeil each banker's counter, from the census's posts). */
    public List<Branch> branches() {
        if (branches != null && !branches.isEmpty()) return branches;
        List<Branch> l = new ArrayList<>();
        if (rpg.world.WorldMap.shadowveil()) {
            for (var a : rpg.world.shadowveil.Census.all().values()) {
                float[] post = rpg.world.shadowveil.Census.post(a.id());
                if (post == null || !"teller".equals(rpg.world.shadowveil.Census.postKind(a.id()))) continue;
                float[] cust = rpg.world.shadowveil.Census.customerSpot(a.id());
                String where = a.workId() == null ? "the bank" : rpg.world.shadowveil.Census.placeName(a.workId());
                l.add(new Branch(where, a.kingdom(), a.id(), new float[]{post[0], post[1]}, cust != null ? cust : new float[]{post[0], post[1] + 2}));
            }
        }
        if (l.isEmpty()) l.add(new Branch("the bank", DevMap.BETA, Ids.TELLER, DevMap.BANK_TELLER, new float[]{DevMap.BANK_TELLER[0], DevMap.BANK_COUNTER_Z - 0.9f}));
        branches = l;
        return l;
    }
    /** Forgets the branches (the census placed its people again). */
    public void reset() { branches = null; }
    /** The branch nearest a point. */
    public Branch branchNear(float x, float z) {
        Branch best = null; double bd = Double.MAX_VALUE;
        for (Branch b : branches()) { double d = Math.hypot(b.customerSpot[0] - x, b.customerSpot[1] - z); if (d < bd) { bd = d; best = b; } }
        return best;
    }
    public Branch branchOf(Actor a) { return branchNear(a.x, a.z); }
    /** Whether someone is a banker at one of the branches. */
    public boolean isTeller(Npc n) { for (Branch b : branches()) if (b.teller == n.id) return true; return false; }
    private Branch branchOfTeller(long id) { for (Branch b : branches()) if (b.teller == id) return b; return null; }

    /** The teller at the branch nearest the player. */
    public Npc teller() { return teller(branchNear(g.player.x, g.player.z)); }
    public Npc teller(Branch b) { return b != null && g.actor(b.teller) instanceof Npc n ? n : null; }

    /** Where a customer stands to be served at the branch nearest the player: before the counter, in front of the teller's place. */
    public float[] customerSpot() { return branchNear(g.player.x, g.player.z).customerSpot; }
    /** Where a customer stands at the branch nearest someone (an NPC's errand goes to its own town's). */
    public float[] customerSpot(Actor a) { return branchOf(a).customerSpot; }
    /** Where the teller works (the branch nearest the player). */
    public float[] tellerSpot() { return branchNear(g.player.x, g.player.z).tellerSpot; }

    /** Whether the branch nearest the player is serving. */
    public boolean open() { return open(branchNear(g.player.x, g.player.z)); }
    /** Whether the branch nearest someone is serving. */
    public boolean open(Actor a) { return open(branchOf(a)); }
    /** Whether a branch is serving: its teller alive, at the counter, not asleep or running. */
    public boolean open(Branch b) {
        Npc t = teller(b);
        return t != null && t.alive && t.loaded && t.working()
                && Math.hypot(t.x - b.tellerSpot[0], t.z - b.tellerSpot[1]) < 1.6;
    }

    /** Why someone cannot be served now (null: they can). */
    public String refusal(Actor a) {
        Branch b = branchOf(a);
        if (!open(b)) return "There's nobody at the bank counter.";
        float[] at = b.customerSpot;
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

    /** Opens an account with one vault, paid from accessible carried coins. */
    public Result openAccount(Actor a, long req) {
        if (hasAccount(a.id)) return Result.no("You already have an account here.");
        String why = refusal(a);
        if (why != null) return Result.no(why);
        if (a.count(Items.COINS) < ACCOUNT_PRICE) return Result.no("You don't have enough gold to open an account with us.");
        a.takeItems(Items.COINS, ACCOUNT_PRICE);
        collectFee(a, ACCOUNT_PRICE);
        vault(a);
        g.state.commit(req, "BankAccountOpened", a.id, Ids.TELLER, DevMap.BETA, 0, a.name + ": one vault, " + FIRST_VAULTS * VAULT_W * VAULT_H + " cells");
        return Result.ok();
    }

    /**
     * (0.4.1) An account brought from another version's save (CharacterRecord): opened without a fee, its vault grown to
     * the cells it had and filled with what lay in it (made anew in this world's ledger). Returns how many stacks came.
     */
    public int restoreAccount(Actor a, List<Object> items, int cells) {
        Container c = hasAccount(a.id) ? vaults(a.id).get(0) : vault(a);
        int rows = Math.max(c.contents.h, (cells + VAULT_W - 1) / VAULT_W);
        if (rows > c.contents.h) c.contents.growRows(rows);
        for (ItemStack s : c.contents.stacks()) g.state.burn(s.def, s.amount);
        rpg.item.ItemIO.inventory(c.contents, items);
        int n = 0;
        for (ItemStack s : c.contents.stacks()) { g.state.mint(s.def, s.amount); n++; }
        g.state.commit("BankAccountRestored", a.id, Ids.TELLER, DevMap.BETA, 0, a.name + ": " + n + " stacks carried over");
        return n;
    }

    /** The price of the next vault for an account, or -1 when it has all it may. */
    public int nextVaultPrice(long owner) {
        var account = vaults(owner);
        int bought = account.isEmpty() ? -1 : account.get(0).contents.h / VAULT_H - FIRST_VAULTS;
        return bought < 0 || bought >= MORE_VAULTS ? -1 : VAULT_PRICE[bought];
    }

    /** Adds space to the existing vault, paid from the purse and then the account if needed. */
    public Result buyVault(Actor a, long req) {
        if (!hasAccount(a.id)) return Result.no("You need an account first.");
        String why = refusal(a);
        if (why != null) return Result.no(why);
        int price = nextVaultPrice(a.id);
        if (price < 0) return Result.no("Your vault is already at maximum capacity.");
        int purse = a.count(Items.COINS), held = coinsIn(a.id);
        if (purse + held < price) return Result.no("You don't have enough gold for more bank space.");
        int fromPurse = Math.min(purse, price);
        if (fromPurse > 0) a.takeItems(Items.COINS, fromPurse);
        if (price > fromPurse) takeCoins(a.id, price - fromPurse);
        collectFee(a, price);
        Container vault = vaults(a.id).get(0);
        vault.contents.growRows(vault.contents.h + VAULT_H);
        g.state.commit(req, "BankVaultBought", a.id, Ids.TELLER, DevMap.BETA, 0, a.name + ": " + vault.contents.size() + " cells for " + price + " G");
        return Result.ok();
    }

    private Container vault(Actor a) {
        float[] at = branchOf(a).tellerSpot;
        Container c = new Container(g.state.newEntityId(), "bank_vault", "Bank vault", "vault", at[0], rpg.world.Ground.at(at[0], at[1] + 1.5f) - 6, at[1] + 1.5f, FIRST_VAULTS * VAULT_W * VAULT_H, a.id);
        c.ownerOnly = true;
        return g.objects.add(c);
    }

    private void collectFee(Actor a, int amount) {
        Npc t = teller(branchOf(a));
        if (t == null || !t.store(new ItemStack(Items.COINS, amount))) g.state.burn(Items.COINS, amount);
    }

    /** Collapse pre-expansion saves into one holder, preserving every original item and its provenance. */
    public void migrateAccounts() {
        var owners = new java.util.HashSet<Long>();
        for (var o : g.objects.all()) if (o instanceof Container c && c.kind.equals("vault")) owners.add(c.owner);
        for (long owner : owners) {
            var old = vaults(owner);
            if (old.size() <= 1) continue;
            Container main = old.get(0);
            main.name = "Bank vault";
            int rows = old.stream().mapToInt(c -> c.contents.h).sum();
            int offset = main.contents.h;
            main.contents.growRows(rows);
            for (int i = 1; i < old.size(); i++) {
                Container extra = old.get(i);
                for (int slot=0;slot<extra.contents.size();slot++) {
                    ItemStack item=extra.contents.get(slot); if(item==null)continue;
                    if (!main.contents.putAt(item,extra.contents.x(slot),extra.contents.y(slot)+offset,extra.contents.turned(slot)))
                        throw new IllegalStateException("Bank migration cannot fit " + item);
                }
                offset+=extra.contents.h;
                extra.contents.clear(); extra.removed = true;
            }
        }
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
        if (isTeller(n) || !n.alive || n.hostileToPlayer || n.posse) return null;
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
            if (!hasAccount(n.id) && openAccount(n, g.state.newRequestId()).rejected()) return null;
            purse = n.count(Items.COINS);
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
        int amount=Math.min(DRAW,coinsIn(n.id));
        if(amount<=0 || !n.canStore(new ItemStack(Items.COINS,amount)))return null;
        int drawn = takeCoins(n.id, amount);
        n.store(new ItemStack(Items.COINS, drawn));
        g.state.commit(req, "BankWithdrawal", n.id, Ids.TELLER, DevMap.BETA, 0, n.name + " withdrew " + drawn + " G");
        return drawn > 0 ? "withdrew " + drawn + " G" : null;
    }

    /**
     * (0.2.2) An NPC lays goods in its vault at the counter (a full pack no store would buy from): opens an account the
     * first time, from its purse. Returns how many went in.
     */
    public int depositGoods(Npc n, ItemDef d) {
        if (refusal(n) != null) return 0;
        if (!hasAccount(n.id) && openAccount(n, g.state.newRequestId()).rejected()) return 0;
        int moved = 0;
        for (Container c : vaults(n.id)) {
            while (n.has(d, 1)) {
                List<ItemStack> one = n.takeItems(d, Math.min(n.count(d), d.stackable ? n.count(d) : 1));
                boolean all = true;
                for (ItemStack s : one) { if (c.contents.add(s)) moved += s.amount; else { n.store(s); all = false; } }
                if (!all) break;
            }
        }
        if (moved > 0) g.state.commit(g.state.newRequestId(), "BankDepositGoods", n.id, Ids.TELLER, DevMap.BETA, 0, n.name + " deposited " + moved + " x " + d.name);
        return moved;
    }

    // ------------------------------------------------------------------ the teller's conversation

    /** What the teller offers the player across the counter. */
    public void choices(Npc teller, Dialog d) {
        Branch b = branchOfTeller(teller.id);
        if (b == null || !open(b) || !teller.working()) return;
        var p = g.player;
        if (!hasAccount(p.id)) {
            d.choice("I'd like to open an account (25 G).", () -> {
                Result r = openAccount(p, g.state.newRequestId());
                if (r.rejected()) { reply(teller, r.reason()); return; }
                g.ai.say(teller, "Your account is open. One vault, twice the space of your pack, in your name.");
                openVault(p, vaults(p.id).get(0));
            });
        } else {
            d.choice("I'd like to see my vault.", () -> openVault(p, vaults(p.id).get(0)));
            int price = nextVaultPrice(p.id);
            if (price > 0) d.choice("I'd like more bank space (" + Currency.text(price) + " G).", () -> {
                Result r = buyVault(p, g.state.newRequestId());
                reply(teller, r.rejected() ? r.reason() : "Done. The extra space is now part of your vault.");
            });
        }
        d.choice("How does the bank work?", () -> g.ui.dialog(new Dialog(teller.name, "An account costs 25 G and includes one vault with twice the space of your pack. You can buy four expansions to that same vault: "
                + Currency.text(VAULT_PRICE[0]) + " G for the first, doubling each time. Anything you leave stays exactly where you left it. We open when I'm at this counter, and not before.")));
    }

    private void reply(Npc teller, String text) {
        g.ai.say(teller, text);
        if (g.ui != null) {
            Dialog reply = new Dialog(teller.name, text); reply.npc=teller.id; reply.employment=teller.employmentState();
            reply.choice("Back.", () -> g.ui.dialog(g.dialogue.talk(teller)));
            reply.closeChoice("Goodbye.", () -> {});
            g.ui.dialog(reply);
        } else g.chat.warn(text);
    }

    private void openVault(Actor p, Container vault) {
        String why = vaultRefusal(p, vault);
        if (why != null) { g.chat.warn(why); return; }
        g.ui.closeDialog();
        g.ui.openContainer(vault);
        g.state.commit(g.state.newRequestId(), "ContainerOpened", p.id, vault.id, DevMap.BETA, 0, vault.name);
    }
}
