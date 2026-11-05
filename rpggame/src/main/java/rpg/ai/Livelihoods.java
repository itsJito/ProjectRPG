package rpg.ai;

import rpg.Rpg;
import rpg.econ.Economy;
import rpg.econ.Inns;
import rpg.item.ItemDef;
import rpg.item.ItemStack;
import rpg.item.Items;
import rpg.world.DevMap;
import rpg.world.Farming;

/**
 * (0.3.2) The working day of the farms' and the inns' people, and the inns' patrons. Every step is a real action through
 * the interaction system or the shop ledger: a plot is harvested by the same work the player does, seed is bought with
 * the farmer's own coin, a bowl of stew is cooked at the inn's hearth from the carrots and cabbages on the bar's shelf.
 * Nothing places a location by hand beyond the buildings' own plans: where to go comes from the plots, trees, bars and
 * hearths themselves.
 */
final class Livelihoods {
    private final Rpg g;
    private final Brain brain;
    Livelihoods(Rpg g, Brain brain) { this.g = g; this.brain = brain; }

    /** Whether this calling is one of these (the Jobs dispatcher hands it here). */
    boolean covers(Npc n) {
        Profession p = n.profession;
        return p == Profession.CROP_FARMER || p == Profession.ORCHARD_KEEPER || p == Profession.INNKEEPER || p == Profession.BARTENDER
                || p == Profession.COOK && g.inns != null && g.inns.of(n) != null;
    }

    void tick(Npc n) {
        switch (n.profession) {
            case CROP_FARMER -> cropFarmer(n);
            case ORCHARD_KEEPER -> orchardKeeper(n);
            case INNKEEPER -> innkeeper(n);
            case BARTENDER -> bartender(n);
            case COOK -> cook(n);
            default -> { }
        }
    }

    // ------------------------------------------------------------------ the growers
    /** Where a grower sells: the inn of their town if its bar is taking that produce, else the town's market. */
    private Economy.Shop buyerFor(Npc n, ItemDef d) {
        Inns.Inn inn = g.inns.inJurisdiction(n.homeJurisdiction);
        if (inn != null && inn.bar != null && inn.staffed(g) && g.econ.buys(inn.bar, d) && !g.econ.glut(inn.bar, d)) return inn.bar;
        Economy.Shop market = g.econ.localShop(n);
        return market != null && g.econ.buys(market, d) && !g.econ.glut(market, d) ? market : null;
    }

    /** Whether the buyer of this produce is within a short walk (it is on the way: sold in passing). */
    private boolean buyerNear(Npc n, ItemDef d) {
        Economy.Shop shop = buyerFor(n, d);
        return shop != null && Math.hypot(shop.counterX - n.x, shop.counterZ - n.z) < 14;
    }

    /** Carries produce to a buyer and sells it there; true while that is what it is doing. */
    private boolean sell(Npc n, ItemDef produce, int carryAtLeast) {
        int have = n.count(produce);
        if (have <= 0 || have < carryAtLeast) return false;
        Economy.Shop shop = buyerFor(n, produce);
        if (shop == null) return false;
        Npc keeper = g.econ.merchant(shop);
        if (keeper == null || !keeper.alive || keeper.distTo(shop.counterX, shop.counterZ) > 4) { n.lastOutcome = n.profession.title + ": waiting for " + shop.name + " to be kept"; return false; }
        if (!brain.goTo(n, shop.counterX, shop.counterZ, false, 1.3f)) { n.lastOutcome = n.profession.title + ": taking " + have + " " + produce.name.toLowerCase() + " to " + shop.name; return true; }
        int sold = g.econ.sellToShop(n, shop, produce, have);
        Inns.Inn inn = g.inns.barOf(shop);
        if (inn != null) inn.produceBought += sold;
        n.lastOutcome = n.profession.title + ": sold " + sold + " " + produce.name.toLowerCase() + " to " + shop.name;
        return sold > 0;
    }

    /** What the gardens grow, for selling. */
    private static final ItemDef[] GROWN = {Items.CARROT, Items.CABBAGE, Items.BARLEY, Items.HOPS};

    private void cropFarmer(Npc n) {
        var farming = g.farming;
        // a basket too full to carry more goes to the buyer first
        for (ItemDef d : GROWN) if (!n.canStore(new ItemStack(d, 4)) && sell(n, d, 1)) return;
        // (0.3.3, gamefixes: the simulation running dynamically) a full basket goes to the buyer before another row is
        // cut, and anything carried goes when the buyer is close at hand: a farmer kept a day's harvest overnight, going
        // from row to row and in from the rain, and the inn bought none of it
        for (ItemDef d : GROWN) if ((n.count(d) >= 10 || n.count(d) >= 4 && g.state.hourOfDay() >= 14 || n.count(d) > 0 && buyerNear(n, d)) && sell(n, d, 1)) return;
        // a bare plot is sown before anything else is taken up: sowing is quick, and a plot left bare grows nothing
        Farming.Plot empty = farming.emptyFor(n.id, n.x, n.z);
        if (empty != null) {
            Farming.Crop want = farming.rowCrop(empty);
            Farming.Crop crop = n.has(want.seed, 1) ? want : null;
            for (Farming.Crop c : Farming.Crop.values()) if (crop == null && n.has(c.seed, 1)) crop = c;
            if (crop != null) {
                if (brain.goTo(n, empty.x, empty.z, false, 1.0f)) { var r = g.interact.request(n, empty, "sow_" + crop.name().toLowerCase()); n.lastOutcome = "Crop Farmer: sowing " + crop.title.toLowerCase() + " -> " + r.status(); }
                else n.lastOutcome = "Crop Farmer: going to sow an empty plot";
                return;
            }
        }
        Farming.Plot ripe = farming.ripeFor(n.id, n.x, n.z);
        if (ripe != null && n.canStore(new ItemStack(ripe.crop.produce, ripe.crop.yieldMax))) {
            if (brain.goTo(n, ripe.x, ripe.z, false, 1.0f)) { var r = g.interact.request(n, ripe, "harvest"); n.lastOutcome = "Crop Farmer: harvesting " + ripe.crop.title.toLowerCase() + " -> " + r.status(); }
            else n.lastOutcome = "Crop Farmer: going to a ripe row of " + ripe.crop.title.toLowerCase();
            return;
        }
        if (empty != null) {
            // no seed for the bare plots: bought with the takings
            Farming.Crop want = farming.rowCrop(empty);
            Economy.Shop market = g.econ.localShop(n);
            if (market != null && market.stock.count(want.seed) > 0 && n.has(Items.COINS, g.econ.buyPrice(market, want.seed) * 2)) {
                if (brain.goTo(n, market.counterX, market.counterZ, false, 1.3f)) { var r = g.econ.buy(n, market, want.seed, 2, g.state.newRequestId()); n.lastOutcome = "Crop Farmer: bought seed -> " + r.status(); }
                else n.lastOutcome = "Crop Farmer: off to buy " + want.seed.name.toLowerCase();
                return;
            }
        }
        // the rows done: what has been picked goes to the buyer (a fair basket at once, or whatever there is late in the day)
        for (ItemDef d : GROWN) if (sell(n, d, g.state.hourOfDay() > 15 ? 1 : 6)) return;
        // tending: down the rows, a few minutes at each, weeding and watering
        tendWalk(n, farmingField(n), "Crop Farmer: weeding and watering the rows");
    }

    private Farming.Field farmingField(Npc n) { for (Farming.Field f : Farming.FIELDS) if (f.owner() == n.id) return f; return null; }

    private void tendWalk(Npc n, Farming.Field f, String what) {
        if (f == null) { if (brain.goTo(n, n.postX, n.postZ, false, 1)) g.ai.desire(n, AiSystem.A_IDLE); n.lastOutcome = what; return; }
        int slot = (int) ((g.state.minutes / 6 + n.id) % (f.rows().length * 3));
        int row = f.rows()[slot % f.rows().length];
        float x = f.x0() + 1.5f + (f.x1() - f.x0() - 2) * ((slot / f.rows().length) / 2f);
        // between the rows, on the path, never in a plot
        if (brain.goTo(n, x, row + 1.5f, false, 0.9f)) { g.ai.desire(n, AiSystem.A_IDLE); n.wantYaw = (float) Math.atan2(0, 1); }
        n.lastOutcome = what;
    }

    private void orchardKeeper(Npc n) {
        if (!n.canStore(new ItemStack(Items.APPLE, 3)) && sell(n, Items.APPLE, 1)) return;
        // (0.3.3) a full basket to the buyer before the next tree, and anything carried when passing the buyer
        int apples = n.count(Items.APPLE);
        if ((apples >= 10 || apples >= 4 && g.state.hourOfDay() >= 14 || apples > 0 && buyerNear(n, Items.APPLE)) && sell(n, Items.APPLE, 1)) return;
        Farming.AppleTree tree = g.farming.pickable(n.id, n.count(Items.APPLE) > 0 ? 2 : 3);
        if (tree != null && n.canStore(new ItemStack(Items.APPLE, 1))) {
            float ox = n.x < tree.x ? -1.3f : 1.3f;
            if (brain.goTo(n, tree.x + ox, tree.z, false, 0.9f)) { n.wantYaw = (float) Math.atan2(-(tree.x - n.x), -(tree.z - n.z)); var r = g.interact.request(n, tree, "pick"); n.lastOutcome = "Orchard Keeper: picking apples (" + tree.apples + " on the tree) -> " + r.status(); }
            else n.lastOutcome = "Orchard Keeper: to a tree with " + tree.apples + " apples";
            return;
        }
        if (sell(n, Items.APPLE, g.state.hourOfDay() > 14 ? 1 : 6)) return;
        // between pickings, the trees are looked over and pruned
        Farming.AppleTree look = null;
        int k = (int) ((g.state.minutes / 9 + n.id) % 8);
        for (Farming.AppleTree t : g.farming.trees) if (t.owner == n.id && k-- <= 0) { look = t; break; }
        if (look == null) for (Farming.AppleTree t : g.farming.trees) if (t.owner == n.id) { look = t; break; }
        if (look != null && brain.goTo(n, look.x + 1.4f, look.z + 0.4f, false, 1.0f)) { g.ai.desire(n, AiSystem.A_IDLE); n.wantYaw = (float) Math.atan2(-(look.x - n.x), -(look.z - n.z)); }
        n.lastOutcome = "Orchard Keeper: looking the trees over and pruning";
    }

    // ------------------------------------------------------------------ the inn
    private void innkeeper(Npc n) {
        Inns.Inn inn = g.inns.of(n);
        if (inn == null) return;
        double h = g.state.hourOfDay();
        // the bread the bar sells is bought in from the town's market, when the shelf runs low
        Economy.Shop market = g.econ.localShop(n);
        if (inn.bar.stock.count(Items.BREAD) < 3 && market != null && market.stock.count(Items.BREAD) > 2 && h < 18) {
            if (n.count(Items.BREAD) < 3) {
                if (brain.goTo(n, market.counterX, market.counterZ, false, 1.3f)) { var r = g.econ.buy(n, market, Items.BREAD, 4, g.state.newRequestId()); n.lastOutcome = "Innkeeper: bought bread for the bar -> " + r.status(); }
                else n.lastOutcome = "Innkeeper: off to the market for bread";
                return;
            }
        }
        if (n.count(Items.BREAD) > 0 && !n.unloading) {
            if (brain.goTo(n, inn.layout.customerSpot()[0], inn.layout.customerSpot()[1], false, 1.2f)) {
                int put = 0;
                for (ItemStack s : n.takeItems(Items.BREAD, n.count(Items.BREAD))) { ItemStack shelf = new ItemStack(s.def, s.amount, inn.layout.bartender(), false, inn.layout.bartender()); if (inn.bar.stock.add(shelf)) put += s.amount; else n.store(s); }
                n.lastOutcome = "Innkeeper: put " + put + " loaves on the bar";
            } else n.lastOutcome = "Innkeeper: carrying bread back to the bar";
            return;
        }
        // of a morning the beds are made
        if (h >= 7 && h < 8.5) {
            var bed = inn.guestBeds.get((int) ((g.state.minutes / 10) % inn.guestBeds.size()));
            if (brain.goTo(n, bed.x + 1.1f, bed.z, false, 0.9f)) { g.ai.desire(n, AiSystem.A_IDLE); n.wantYaw = (float) Math.atan2(-(bed.x - n.x), -(bed.z - n.z)); }
            n.lastOutcome = "Innkeeper: making the guest beds";
            return;
        }
        // otherwise at the desk with the register, greeting who comes in
        float[] desk = inn.layout.deskSpot();
        if (brain.goTo(n, desk[0], desk[1], false, 0.7f)) {
            g.ai.desire(n, AiSystem.A_IDLE);
            Npc newcomer = null;
            for (Npc p : g.npcs) if (p != n && p.alive && p.loaded && p.goal == Brain.Goal.INN && inn.inside(p.x, p.z) && g.time - p.goalSince < 6 && p.distTo(n) < 6) { newcomer = p; break; }
            if (newcomer != null && n.speech == null && g.time - n.lastDialogAt > 20) { g.ai.say(n, pick(n, "Evening, " + newcomer.name + ". The usual?", "Come in, come in. Sit anywhere.", "Welcome back, " + newcomer.name + ".")); n.lastDialogAt = g.time; }
            if (g.player.alive && inn.inside(g.player.x, g.player.z) && g.player.distTo(n) < 5 && n.speech == null && g.time - n.lastDialogAt > 40) { g.ai.say(n, "A bed for the night is " + Inns.ROOM_PRICE + " G. The bar's through there."); n.lastDialogAt = g.time; }
        }
        n.lastOutcome = "Innkeeper: keeping the register (" + inn.nights + " nights let)";
    }

    private void bartender(Npc n) {
        Inns.Inn inn = g.inns.of(n);
        if (inn == null) return;
        double h = g.state.hourOfDay();
        // the cask runs low: ale is bought in from the market in the quiet of the day
        Economy.Shop market = g.econ.localShop(n);
        boolean quiet = h < Inns.OPEN_HOUR + 0.5 || h >= 14 && h < 16;
        if (inn.bar.stock.count(Items.ALE) < 4 && quiet && market != null && market.stock.count(Items.ALE) > 0 && n.has(Items.COINS, g.econ.buyPrice(market, Items.ALE) * 4)) {
            if (n.count(Items.ALE) < 4) {
                if (brain.goTo(n, market.counterX, market.counterZ, false, 1.3f)) { var r = g.econ.buy(n, market, Items.ALE, Math.min(6, market.stock.count(Items.ALE)), g.state.newRequestId()); n.lastOutcome = "Bartender: bought ale -> " + r.status(); }
                else n.lastOutcome = "Bartender: off to buy a cask of ale";
                return;
            }
        }
        float[] bar = inn.layout.barSpot();
        if (!brain.goTo(n, bar[0], bar[1], false, 0.7f)) { n.lastOutcome = "Bartender: back to the bar"; return; }
        // what was bought for the bar goes on the shelf
        for (ItemDef d : new ItemDef[]{Items.ALE}) for (ItemStack s : n.takeItems(d, n.count(d))) { ItemStack shelf = new ItemStack(s.def, s.amount, n.id, false, n.id); if (!inn.bar.stock.add(shelf)) n.store(s); }
        g.ai.desire(n, AiSystem.A_IDLE);
        n.wantYaw = (float) Math.atan2(-(inn.layout.customerSpot()[0] - n.x), -(inn.layout.customerSpot()[1] - n.z));
        n.lastOutcome = String.format("Bartender: keeping the bar (%d ale, %d cider, %d stew, %d bread)", inn.bar.stock.count(Items.ALE), inn.bar.stock.count(Items.HOT_CIDER), inn.bar.stock.count(Items.VEGETABLE_STEW), inn.bar.stock.count(Items.BREAD));
    }

    private void cook(Npc n) {
        Inns.Inn inn = g.inns.of(n);
        if (inn == null) return;
        float[] at = inn.layout.hearthSpot();
        // what was cooked goes out onto the bar
        for (ItemDef d : new ItemDef[]{Items.VEGETABLE_STEW, Items.HOT_CIDER, Items.ALE}) if (n.count(d) > 0) {
            for (ItemStack s : n.takeItems(d, n.count(d))) { ItemStack shelf = new ItemStack(s.def, s.amount, inn.layout.bartender(), false, inn.layout.bartender()); if (!inn.bar.stock.add(shelf)) n.store(s); }
        }
        if (!brain.goTo(n, at[0], at[1], false, 0.8f)) { n.lastOutcome = "Cook: to the hearth"; return; }
        n.wantYaw = (float) Math.atan2(-(inn.hearth.x - n.x), -(inn.hearth.z - n.z));
        var stew = rpg.item.Recipes.byId("cook_stew");
        var cider = rpg.item.Recipes.byId("mull_cider");
        boolean wantStew = inn.bar.stock.count(Items.VEGETABLE_STEW) < 8 && (n.has(Items.CARROT, 1) && n.has(Items.CABBAGE, 1) || inn.bar.stock.count(Items.CARROT) > 0 && inn.bar.stock.count(Items.CABBAGE) > 0);
        boolean wantCider = inn.bar.stock.count(Items.HOT_CIDER) < 6 && (n.count(Items.APPLE) >= 2 || inn.bar.stock.count(Items.APPLE) >= 4);
        if (wantStew && stew != null) {
            if (!n.has(Items.CARROT, 1)) take(inn, n, Items.CARROT); if (!n.has(Items.CABBAGE, 1)) take(inn, n, Items.CABBAGE);
            var r = g.interact.request(n, inn.hearth, "make:" + stew.id);
            n.lastOutcome = "Cook: a pot of vegetable stew -> " + r.status();
            return;
        }
        if (wantCider && cider != null) {
            while (n.count(Items.APPLE) < 2 && inn.bar.stock.count(Items.APPLE) > 2) take(inn, n, Items.APPLE);
            var r = g.interact.request(n, inn.hearth, "make:" + cider.id);
            n.lastOutcome = "Cook: mulling apples into cider -> " + r.status();
            return;
        }
        // (0.3.4, gamefixes: the inns make their ale) barley and hops in a bottle of water, brewed at the hearth; the
        // bottles come back empty off the bar, and are filled at the nearest water
        var brew = rpg.item.Recipes.byId("brew_ale");
        boolean wantAle = inn.bar.stock.count(Items.ALE) < 10 && (n.has(Items.BARLEY, 1) || inn.bar.stock.count(Items.BARLEY) > 0) && (n.has(Items.HOPS, 1) || inn.bar.stock.count(Items.HOPS) > 0);
        if (wantAle && brew != null) {
            if (!n.has(Items.WATER_BOTTLE, 1)) take(inn, n, Items.WATER_BOTTLE);
            if (!n.has(Items.WATER_BOTTLE, 1)) { if (fetchWater(n, inn)) return; }
            else {
                if (!n.has(Items.BARLEY, 1)) take(inn, n, Items.BARLEY);
                if (!n.has(Items.HOPS, 1)) take(inn, n, Items.HOPS);
                var r = g.interact.request(n, inn.hearth, "make:" + brew.id);
                n.lastOutcome = "Cook: brewing ale -> " + r.status();
                return;
            }
        }
        g.ai.desire(n, AiSystem.A_IDLE);
        n.lastOutcome = inn.bar.stock.count(Items.CARROT) == 0 || inn.bar.stock.count(Items.CABBAGE) == 0 ? "Cook: waiting on the farm for carrots and cabbage" : "Cook: keeping the pot on";
    }
    /** Where each inn's cook fills the bottles (the nearest water to its hearth), found once. */
    private final java.util.Map<Inns.Inn, float[]> waterFor = new java.util.HashMap<>();

    /**
     * Empty bottles off the shelf, out to the water and back filled. Returns whether that errand is under way (false: no
     * bottles to fill, or no water within reach of the inn).
     */
    private boolean fetchWater(Npc n, Inns.Inn inn) {
        if (!n.has(Items.GLASS_BOTTLE, 1)) for (int i = 0; i < 3; i++) take(inn, n, Items.GLASS_BOTTLE);
        if (!n.has(Items.GLASS_BOTTLE, 1)) return false;
        float[] spot = waterFor.computeIfAbsent(inn, k -> { float[] h = inn.layout.hearthSpot(); float[] e = g.fluids.edge(h[0], h[1], rpg.world.DevMap.FLOOR, 48); return e == null ? new float[0] : e; });
        if (spot.length == 0) { n.lastOutcome = "Cook: no water near enough to fill the bottles"; return false; }
        if (!brain.goTo(n, spot[0], spot[2], false, 1.2f)) { n.lastOutcome = "Cook: taking the bottles to the water"; return true; }
        int filled = 0;
        for (ItemStack s : n.takeItems(Items.GLASS_BOTTLE, n.count(Items.GLASS_BOTTLE))) for (int i = 0; i < s.amount; i++) {
            g.state.burn(Items.GLASS_BOTTLE, 1); g.state.mint(Items.WATER_BOTTLE, 1);
            if (n.store(new ItemStack(Items.WATER_BOTTLE, 1, n.id, false, n.id))) filled++;
        }
        g.state.commit("BottleFilled", n.id, 0, rpg.world.DevMap.jurisdiction(n.x, n.z), 0, filled + " for " + inn.name());
        n.lastOutcome = "Cook: filled " + filled + " bottles at the water";
        return true;
    }

    /** One of an item off the bar's shelf into the cook's hands (the inn's own stock, not a sale). */
    private void take(Inns.Inn inn, Npc n, ItemDef d) {
        if (inn.bar.stock.count(d) <= 0 || !n.canStore(new ItemStack(d, 1))) return;
        inn.bar.stock.remove(d, 1);
        n.store(new ItemStack(d, 1, n.id, false, n.id));
    }

    // ------------------------------------------------------------------ patrons
    /**
     * An evening out or a meal at the inn: to the bar, buy the fare, to a seat on a bench, eat or drink it there over a
     * while, talk with whoever sits near, then go. Returns false when the inn cannot serve them (closed, nothing to buy
     * they can pay for, no seat) - the brain then picks again.
     */
    boolean patron(Npc n) {
        Inns.Inn inn = g.inns.inJurisdiction(n.homeJurisdiction);
        if (inn == null || !inn.open(g)) { n.lastOutcome = "the inn is shut"; leave(n); return false; }
        boolean holding = n.has(Items.VEGETABLE_STEW, 1) || n.has(Items.HOT_CIDER, 1) || n.has(Items.ALE, 1) || n.has(Items.BREAD, 1) && n.innFareAt > 0 || n.has(Items.APPLE, 1) && n.innFareAt > 0;
        if (!holding && n.innFareAt <= 0) {
            ItemDef fare = g.inns.fareFor(inn, n);
            if (fare == null) { n.lastOutcome = "nothing at the bar " + n.name + " can pay for"; n.innUntil = 0; return false; }
            float[] c = inn.layout.customerSpot();
            if (!brain.goTo(n, c[0] + ((n.id % 3) - 1) * 0.5f, c[1] + ((n.id % 2) == 0 ? -0.6f : 0.6f), false, 1.0f)) { n.lastOutcome = "going to the bar at " + inn.name(); return true; }
            var r = g.econ.buy(n, inn.bar, fare, 1, g.state.newRequestId());
            if (r.rejected()) { n.lastOutcome = "could not buy " + fare.name.toLowerCase() + ": " + r.reason(); n.innUntil = 0; return false; }
            if (Inns.drink(fare)) inn.drinks++; else inn.meals++;
            n.innFareAt = g.time; n.holdingFare = fare;
            if (g.actor(inn.layout.bartender()) instanceof Npc b && b.speech == null && g.time - b.lastDialogAt > 12) { g.ai.say(b, Inns.drink(fare) ? "There you are." : "Mind, it's hot."); b.lastDialogAt = g.time; }
            n.lastOutcome = "bought " + fare.name.toLowerCase() + " at " + inn.name();
            return true;
        }
        int seat = g.inns.takeSeat(inn, n);
        if (seat < 0) { n.lastOutcome = "the inn is full; drinking standing"; float[] c = inn.layout.customerSpot(); brain.goTo(n, c[0] - 1.6f, c[1] + 2f, false, 1.2f); consume(n, inn); return true; }
        float[] s = inn.seats.get(seat);
        if (!brain.goTo(n, s[0], s[1], false, 0.35f)) { n.seat = Math.max(0, n.seat - 0.1f); n.lastOutcome = "to a seat at " + inn.name(); return true; }
        g.ai.desire(n, AiSystem.A_IDLE);
        n.seated = true; n.seatYaw = s[2]; n.wantYaw = s[2];
        consume(n, inn);
        // company: whoever sits close is talked with - news goes round the table
        if (g.time >= n.innTalkAt) {
            n.innTalkAt = g.time + 10 + (n.id % 7);
            Npc near = null;
            for (Npc p : g.npcs) if (p != n && p.alive && p.loaded && p.seated && p.distTo(n) < 2.6f) { near = p; break; }
            if (near != null) {
                g.ai.share(n, near);
                n.social = Math.max(0, n.social - 0.25f);
                if (n.speech == null) g.ai.say(n, pick(n, "Did you hear about " + near.name + "'s week?", "Another round, I think.", "Quiet in town today.", "Good stew tonight.", "My feet are killing me."));
                n.lastOutcome = "talking with " + near.name + " at " + inn.name();
            } else n.lastOutcome = "sitting at " + inn.name();
        }
        if (g.time > n.innUntil && n.innFareAt <= 0) { leave(n); n.innUntil = 0; n.nextThink = 0; }
        return true;
    }
    /** The fare eaten or drunk, a while after it was bought; then a little more time over the empty bowl or mug. */
    private void consume(Npc n, Inns.Inn inn) {
        if (n.innFareAt > 0 && g.time - n.innFareAt > 18) {
            for (ItemDef d : new ItemDef[]{Items.VEGETABLE_STEW, Items.HOT_CIDER, Items.ALE, Items.BREAD, Items.APPLE}) if (n.has(d, 1)) {
                n.takeItems(d, 1); g.state.burn(d, 1);
                // (0.3.4) a bottle of ale lends its strength to whoever drinks it, and the empty goes back on the bar
                if (d.strengthBoost > 0) {
                    n.skills.boost(rpg.skill.Skill.STRENGTH, d.strengthBoost);
                    g.state.mint(Items.GLASS_BOTTLE, 1);
                    if (!inn.bar.stock.add(new ItemStack(Items.GLASS_BOTTLE, 1, inn.bar.merchant, false, inn.bar.merchant))) g.state.burn(Items.GLASS_BOTTLE, 1);
                }
                n.hunger = Math.max(0, n.hunger - (d.heal >= 5 ? 0.9f : 0.4f));
                n.fatigue = Math.max(0, n.fatigue - 0.05f);
                n.hp = Math.min(n.maxHp(), n.hp + d.heal);
                g.state.commit("NpcAte", n.id, 0, inn.layout.jurisdiction(), 0, d.name + " at " + inn.name());
                rpg.render.Faces.react(g, n, rpg.render.Faces.Mood.HAPPY, 2);
                break;
            }
            n.innFareAt = 0; n.holdingFare = null;
        }
    }
    void leave(Npc n) { n.seated = false; g.inns.leaveSeat(n); n.innFareAt = 0; n.holdingFare = null; }

    private String pick(Npc n, String... lines) { return lines[(int) Math.floorMod(n.id * 31 + (long) (g.time / 9), (long) lines.length)]; }
}
