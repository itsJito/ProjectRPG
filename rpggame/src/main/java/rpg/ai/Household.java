package rpg.ai;

import rpg.Ids;
import rpg.Rpg;
import rpg.interact.Result;
import rpg.interact.WorldObject;
import rpg.item.ItemDef;
import rpg.item.ItemStack;
import rpg.item.Items;
import rpg.objects.Breakable;
import rpg.objects.Furniture;
import rpg.objects.Node;
import rpg.objects.Station;
import rpg.world.Farming;
import rpg.world.Mansion;

/**
 * (0.3.8, gamefixes: the DevMansion's people, who keep the estate and the property - and who must work) The household's
 * day, each by their calling (GDD): every step a real action on real things through the same systems the player uses.
 * <ul>
 * <li>the <b>Household Baron</b> sees to the household: at his desk over its accounts, round the house to each of his
 * people to see that their work is done and what they lack, and their wages paid on the week's first day; he dines at
 * his table at the dinner hour on what his cook has made;</li>
 * <li>the <b>Cook</b> cooks at the kitchen's range from what is in the pantry's crates and barrels - a pot of stew from
 * its carrots and cabbage, cider from its apples - and serves the Baron at his table;</li>
 * <li>the <b>Porter</b> carries what is left at the gate into the pantry (the orchard's apples, deliveries), puts back
 * on their feet the crates and barrels that have been knocked over, and takes the wood the cutter leaves into the
 * kitchen;</li>
 * <li>the <b>Firewood Cutter</b> fells for logs in the wood about the estate and keeps the house's fires: each grate
 * with wood in it, laid and lit when it has gone cold;</li>
 * <li>the <b>Orchard Keeper</b> picks the estate's apple trees and leaves the apples at the gate for the porter.</li>
 * </ul>
 * The Household Knight keeps to his patron (Jobs, as every household knight does). A fire burns its wood by the minute.
 */
final class Household {
    private final Rpg g;
    private final Brain brain;
    private double lastMinutes = -1;

    Household(Rpg g, Brain brain) { this.g = g; this.brain = brain; }

    /** Whether this is one of the household's working people (the Jobs dispatcher hands them here). */
    boolean covers(Npc n) {
        // (0.4.3) the DevMansion's household runs on the devmap only (its rooms and range are the devmap's)
        if (!rpg.world.WorldMap.devmap()) return false;
        long id = n.id;
        return id == Ids.BARON || id == Ids.MANSION_COOK || id == Ids.MANSION_PORTER || id == Ids.MANSION_WOODCUTTER || id == Ids.MANSION_ORCHARDIST;
    }

    void tick(Npc n) {
        if (n.id == Ids.BARON) baron(n);
        else if (n.id == Ids.MANSION_COOK) cook(n);
        else if (n.id == Ids.MANSION_PORTER) porter(n);
        else if (n.id == Ids.MANSION_WOODCUTTER) cutter(n);
        else if (n.id == Ids.MANSION_ORCHARDIST) orchard(n);
    }

    /** Whether this person eats at the house (the household and its knight), not in town. */
    boolean eatsAtHome(Npc n) { return covers(n) || n.id == Ids.MANSION_KNIGHT; }

    /**
     * (0.3.9) Hungry in the house: something from the pantry's stores - a dish the cook has made first, then bread, then
     * what is there to hand - taken, carried and eaten. With the pantry bare, they wait on the cook in the kitchen rather
     * than walk to the town for bread (the Baron left his estate to buy a loaf in DevCity). True while it is in hand.
     */
    boolean eat(Npc n) {
        if (!eatsAtHome(n) || !Mansion.onEstate(n.x, n.z) && Math.hypot(n.x - Mansion.DELIVERY[0], n.z - Mansion.DELIVERY[1]) > 60) return false;
        ItemDef[] dishes = {Items.VEGETABLE_STEW, Items.BREAD, Items.APPLE, Items.CARROT, Items.CABBAGE};
        for (ItemDef d : dishes) if (n.has(d, 1)) {
            g.ai.desire(n, AiSystem.A_IDLE);
            n.takeItems(d, 1); g.state.burn(d, 1);
            n.hunger = Math.max(0, n.hunger - (d.heal >= 5 ? 0.9f : 0.45f));
            n.hp = Math.min(n.maxHp(), n.hp + d.heal);
            g.state.commit("NpcAte", n.id, 0, rpg.world.DevMap.jurisdiction(n.x, n.z), 0, d.name + " at the house");
            n.lastOutcome = "ate " + d.name.toLowerCase() + " at the house";
            if (n.hunger <= 0.05f) { n.goal = Brain.Goal.IDLE; n.nextThink = 0; }
            return true;
        }
        for (ItemDef d : dishes) {
            Breakable c = store(d, true);
            if (c == null) continue;
            if (brain.goTo(n, c.x, fy(c.y), c.z, false, 1.1f)) { takeFrom(n, c, d, 1); n.lastOutcome = "a bite from the pantry"; }
            else n.lastOutcome = "to the pantry for something to eat";
            return true;
        }
        Breakable kitchen = kitchenCrate();
        if (kitchen != null && brain.goTo(n, kitchen.x, fy(kitchen.y), kitchen.z, false, 2.2f)) { g.ai.desire(n, AiSystem.A_IDLE); n.lastOutcome = "waiting on the cook - the pantry's bare"; }
        else n.lastOutcome = "to the kitchen, hungry";
        return true;
    }

    /** The fires burn down by the game minute (called every frame by the AI). */
    void update() {
        double now = g.state.minutes;
        if (lastMinutes < 0 || now < lastMinutes) { lastMinutes = now; return; }
        float dm = (float) Math.min(600, now - lastMinutes);
        lastMinutes = now;
        if (dm <= 0) return;
        // (0.4.7, gamefixes: aggressive optimization) the hearths alone, found once: every piece of furniture in the world
        // (some thousands on ShadowVeil) was walked every frame for the few that burn
        if (hearthsOf != g.objects.furniture.size()) {
            hearthsOf = g.objects.furniture.size(); hearths.clear();
            for (Furniture f : g.objects.furniture) if (f.kind == Furniture.Kind.FIREPLACE) hearths.add(f);
        }
        for (Furniture f : hearths) if (f.fuel > 0) f.burn(dm);
    }
    private final java.util.List<Furniture> hearths = new java.util.ArrayList<>();
    private int hearthsOf = -1;

    // ------------------------------------------------------------------ helpers
    /** The floor under a height (where to stand to reach something at that height). */
    private static float fy(float y) { return Mansion.LEVELS[Mansion.level(y)]; }


    /** The pantry's stores: the crates and barrels on the ground floor's pantry (or the kitchen) that are still whole. */
    private Breakable store(ItemDef d, boolean withSome) {
        Breakable best = null;
        for (Breakable b : g.breakables.all) {
            if (b.broken || b.form == Breakable.Form.SIGN || !Mansion.inside(b.x, b.z) || Mansion.level(b.y) != 0) continue;
            int have = b.contents.count(d);
            if (withSome && have <= 0) continue;
            if (!withSome && (have <= 0 && best != null)) continue;
            if (best == null || have > best.contents.count(d)) best = b;
        }
        return best;
    }

    /** The crate by the gate where things for the house are left. */
    private Breakable gate() {
        Breakable best = null; float bd = 4;
        for (Breakable b : g.breakables.all) {
            if (b.broken || b.form == Breakable.Form.SIGN) continue;
            float d = (float) Math.hypot(b.x - Mansion.DELIVERY[0], b.z - Mansion.DELIVERY[1]);
            if (d < bd) { bd = d; best = b; }
        }
        return best;
    }

    private Furniture fit(Furniture.Kind kind, int level, float nearX, float nearZ) {
        Furniture best = null; float bd = Float.MAX_VALUE;
        for (Furniture f : g.objects.furniture) {
            if (f.kind != kind || !f.exists() || f.floor != Mansion.LEVELS[level] || !Mansion.inside(f.x, f.z)) continue;
            float d = (float) Math.hypot(f.x - nearX, f.z - nearZ);
            if (d < bd) { bd = d; best = f; }
        }
        return best;
    }

    private Station range() {
        for (Station s : g.objects.stations()) if (Math.hypot(s.x - Mansion.RANGE_AT[0], s.z - Mansion.RANGE_AT[1]) < 1.2f) return s;
        return null;
    }

    /** Moves up to n of a thing from a container into the hands (true if any moved). */
    private boolean takeFrom(Npc n, rpg.objects.Container c, ItemDef d, int count) {
        int moved = 0;
        for (int i = 0; i < c.contents.size() && moved < count; i++) {
            ItemStack s = c.contents.get(i);
            if (s == null || s.def != d) continue;
            int k = Math.min(count - moved, s.amount);
            ItemStack one = s.copy(k);
            if (!n.canStore(one)) break;
            c.contents.take(i, k);
            n.store(one);
            moved += k;
        }
        if (moved > 0) g.state.commit("TookFromStore", n.id, c.id, c.jurisdiction(), 0, moved + " " + d.name);
        return moved > 0;
    }

    /** Puts what is carried of a thing into a container (returns how many). */
    private int putInto(Npc n, rpg.objects.Container c, ItemDef d) {
        int put = 0;
        for (ItemStack s : n.takeItems(d, n.count(d))) { if (c.contents.add(s)) put += s.amount; else n.store(s); }
        if (put > 0) g.state.commit("PutInStore", n.id, c.id, c.jurisdiction(), 0, put + " " + d.name);
        return put;
    }

    private static boolean meal(double hour) { return hour >= 12 && hour < 13 || hour >= 18.5 && hour < 19.5; }

    // ------------------------------------------------------------------ the Household Baron
    private void baron(Npc n) {
        double h = g.state.hourOfDay();
        // the dinner hour: to his place at the head of the table
        if (meal(h)) {
            Furniture chair = fit(Furniture.Kind.CHAIR, 0, 39.5f, 149.5f);
            Furniture table = chair == null ? null : fit(Furniture.Kind.DINING_TABLE, 0, chair.x, chair.z);
            float faceTable = table == null ? 0 : (float) Math.atan2(-(table.x - chair.x), -(table.z - chair.z));
            // (0.3.9) seats are solid: in from the chair's open side and down onto it
            if (chair != null && brain.seating.sit(n, chair.x, Mansion.LEVELS[0], chair.z, faceTable)) {
                ItemStack food = null;
                for (ItemDef d : new ItemDef[]{Items.VEGETABLE_STEW, Items.HOT_CIDER, Items.BREAD}) if (food == null && n.has(d, 1)) food = new ItemStack(d, 1);
                if (food != null && n.hunger > 0.2f && g.time - n.lastLootAt > 20) {
                    n.takeItems(food.def, 1);
                    n.hunger = Math.max(0, n.hunger - 0.6f);
                    n.lastLootAt = g.time;
                    g.state.commit("Ate", n.id, 0, rpg.world.DevMap.jurisdiction(n.x, n.z), 0, food.def.name);
                    if (g.sfx != null) g.sfx.play("eat", n.x, n.y + 1.2f, n.z, 0.4f);
                    n.lastOutcome = "Baron: dining (" + food.def.name.toLowerCase() + ")";
                } else n.lastOutcome = food == null ? "Baron: at table, waiting on the cook" : "Baron: at table";
            } else n.lastOutcome = "Baron: in to dinner";
            return;
        }
        // wages: on the week's first day each of his people is paid what the week's work is worth, from his own purse
        int day = (int) (g.state.minutes / 1440);
        if (day % 7 == 0 && n.paidOn != day && h >= 9) {
            for (long id : new long[]{Ids.MANSION_COOK, Ids.MANSION_PORTER, Ids.MANSION_WOODCUTTER, Ids.MANSION_ORCHARDIST, Ids.MANSION_KNIGHT}) {
                if (!(g.actor(id) instanceof Npc s) || !s.alive) continue;
                int wage = id == Ids.MANSION_KNIGHT ? 40 : 20;
                if (n.count(Items.COINS) < wage) break;
                for (ItemStack c : n.takeItems(Items.COINS, wage)) s.store(c);
                g.state.commit("WagesPaid", n.id, s.id, rpg.world.DevMap.jurisdiction(n.x, n.z), 0, wage + " G to " + s.name);
            }
            n.paidOn = day;
        }
        // the round of the house: each of his people looked in on, in turn, through the day; between, his desk
        int beat = (int) ((g.state.minutes / 40 + n.id) % 6);
        long[] people = {Ids.MANSION_COOK, Ids.MANSION_PORTER, Ids.MANSION_WOODCUTTER, Ids.MANSION_ORCHARDIST};
        if (beat < people.length && g.actor(people[beat]) instanceof Npc s && s.alive && s.loaded && Mansion.onEstate(s.x, s.z)) {
            if (brain.goTo(n, s.x, fy(s.y), s.z, false, 2.2f)) {
                g.ai.desire(n, AiSystem.A_IDLE);
                n.wantYaw = (float) Math.atan2(-(s.x - n.x), -(s.z - n.z));
                if (g.time - n.lastTalk > 30) { g.ai.say(n, checkLine(s)); n.lastTalk = g.time; }
                n.lastOutcome = "Baron: seeing how " + s.name + " does (" + (s.lastOutcome == null ? "idle" : s.lastOutcome) + ")";
            } else n.lastOutcome = "Baron: to see " + s.name;
            return;
        }
        Furniture desk = fit(Furniture.Kind.DESK, 1, 38.5f, 139.5f);
        Furniture seat = desk == null ? null : fit(Furniture.Kind.CHAIR, 1, desk.x + 1.5f, desk.z);
        if (seat != null && brain.seating.sit(n, seat.x, Mansion.LEVELS[1], seat.z, (float) Math.atan2(-(desk.x - seat.x), -(desk.z - seat.z)))) {
            n.lastOutcome = "Baron: at his desk over the household's accounts";
        } else n.lastOutcome = "Baron: up to the study";
    }

    /** What the Baron says looking in on one of his people (by what they are about, and what they lack). */
    private String checkLine(Npc s) {
        if (s.id == Ids.MANSION_COOK) {
            Breakable veg = store(Items.CARROT, true);
            return veg == null ? "The pantry's bare of carrots? Tell the porter." : "Something good for dinner, I hope.";
        }
        if (s.id == Ids.MANSION_WOODCUTTER) {
            for (Furniture f : g.objects.furniture) if (f.kind == Furniture.Kind.FIREPLACE && Mansion.inside(f.x, f.z) && f.fuel <= 0) return "The " + rpg.world.Mansion.roomAt((int) f.x, (int) f.z, Mansion.level(f.floor)).name().toLowerCase() + " fire's gone out.";
            return "Keep the fires in. Good.";
        }
        if (s.id == Ids.MANSION_PORTER) return "Anything at the gate today?";
        return "The trees look well.";
    }

    // ------------------------------------------------------------------ the Cook
    private void cook(Npc n) {
        double h = g.state.hourOfDay();
        Npc baron = g.actor(Ids.BARON) instanceof Npc b && b.alive ? b : null;
        // dinner served: what is cooked carried to the Baron at his table
        if (baron != null && meal(h) && Mansion.inside(baron.x, baron.z) && Mansion.level(baron.y) == 0 && !baron.has(Items.VEGETABLE_STEW, 1) && !baron.has(Items.HOT_CIDER, 1)) {
            ItemDef dish = n.has(Items.VEGETABLE_STEW, 1) ? Items.VEGETABLE_STEW : n.has(Items.HOT_CIDER, 1) ? Items.HOT_CIDER : null;
            if (dish != null) {
                if (brain.goTo(n, baron.x, fy(baron.y), baron.z, false, 1.4f)) {
                    for (ItemStack s : n.takeItems(dish, 1)) baron.store(s);
                    g.state.commit("MealServed", n.id, baron.id, rpg.world.DevMap.jurisdiction(n.x, n.z), 0, dish.name);
                    g.ai.say(n, dish == Items.VEGETABLE_STEW ? "Your stew, my lord." : "Hot cider, my lord.");
                    n.lastOutcome = "Cook: served the Baron " + dish.name.toLowerCase();
                } else n.lastOutcome = "Cook: carrying dinner in";
                return;
            }
        }
        Station range = range();
        if (range == null) { n.lastOutcome = "Cook: no range"; return; }
        int ready = n.count(Items.VEGETABLE_STEW) + n.count(Items.HOT_CIDER);
        var stew = rpg.item.Recipes.byId("cook_stew");
        var cider = rpg.item.Recipes.byId("mull_cider");
        if (ready < 2) {
            // the ingredients from the pantry's stores, then to the range
            boolean veg = n.has(Items.CARROT, 1) && n.has(Items.CABBAGE, 1);
            if (!veg) {
                Breakable c = !n.has(Items.CARROT, 1) ? store(Items.CARROT, true) : store(Items.CABBAGE, true);
                if (c != null) {
                    if (brain.goTo(n, c.x, fy(c.y), c.z, false, 1.1f)) { takeFrom(n, c, n.has(Items.CARROT, 1) ? Items.CABBAGE : Items.CARROT, 1); n.lastOutcome = "Cook: vegetables from the pantry"; }
                    else n.lastOutcome = "Cook: to the pantry";
                    return;
                }
            }
            boolean apples = n.count(Items.APPLE) >= 2;
            if (!veg && !apples) {
                Breakable a = store(Items.APPLE, true);
                if (a != null) {
                    if (brain.goTo(n, a.x, fy(a.y), a.z, false, 1.1f)) { takeFrom(n, a, Items.APPLE, 2); n.lastOutcome = "Cook: apples from the pantry"; }
                    else n.lastOutcome = "Cook: to the pantry for apples";
                    return;
                }
            }
            if (veg || apples) {
                if (brain.goTo(n, range.x + 1.1f, range.y, range.z, false, 0.8f)) {
                    n.wantYaw = (float) Math.atan2(-(range.x - n.x), -(range.z - n.z));
                    var recipe = veg ? stew : cider;
                    Result r = recipe == null ? Result.no("no recipe") : g.interact.request(n, range, "make:" + recipe.id);
                    n.lastOutcome = "Cook: " + (veg ? "a pot of stew" : "mulling cider") + " -> " + r.status() + (r.reason() == null ? "" : " (" + r.reason() + ")");
                } else n.lastOutcome = "Cook: to the range";
                return;
            }
            n.lastOutcome = "Cook: the pantry is out of what I need";
        } else n.lastOutcome = "Cook: dinner's ready; keeping it warm";
        if (brain.goTo(n, range.x + 1.1f, range.y, range.z, false, 1.0f)) { g.ai.desire(n, AiSystem.A_IDLE); n.wantYaw = (float) Math.atan2(-(range.x - n.x), -(range.z - n.z)); }
    }

    // ------------------------------------------------------------------ the Porter
    private void porter(Npc n) {
        // 1. anything carried for the house goes into the pantry's stores
        for (ItemDef d : new ItemDef[]{Items.APPLE, Items.CARROT, Items.CABBAGE, Items.POTATO, Items.ONION, Items.BREAD, Items.LOGS}) {
            if (!n.has(d, 1)) continue;
            Breakable into = store(d, false);
            if (d == Items.LOGS) into = kitchenCrate();
            if (into == null) continue;
            if (brain.goTo(n, into.x, fy(into.y), into.z, false, 1.1f)) { int put = putInto(n, into, d); n.lastOutcome = "Porter: put " + put + " " + d.name.toLowerCase() + " away"; }
            else n.lastOutcome = "Porter: carrying " + d.name.toLowerCase() + " in";
            return;
        }
        // 2. a crate or barrel knocked over in the house or the yard is put back on its feet where it lies
        for (Breakable b : g.breakables.all) {
            if (b.broken || !Mansion.onEstate(b.x, b.z)) continue;
            float upY = 1 - 2 * (b.qx * b.qx + b.qz * b.qz);
            if (upY > 0.8f) continue;
            if (brain.goTo(n, b.x, fy(b.y), b.z, false, 1.1f)) {
                float yaw = (float) Math.atan2(2 * (b.qw * b.qy + b.qx * b.qz), 1 - 2 * (b.qy * b.qy + b.qz * b.qz));
                b.qw = (float) Math.cos(yaw / 2); b.qx = 0; b.qy = (float) Math.sin(yaw / 2); b.qz = 0;
                b.y = (float) Math.floor(b.y - b.form.hy() + 0.5f) + b.form.hy() + 0.01f;
                g.breakables.respawn(b);
                g.state.commit("SetUpright", n.id, b.id, rpg.world.DevMap.jurisdiction(b.x, b.z), 0, b.form.title);
                n.lastOutcome = "Porter: set the " + b.form.title.toLowerCase() + " back on its feet";
            } else n.lastOutcome = "Porter: to the " + b.form.title.toLowerCase() + " that went over";
            return;
        }
        // 3. what was left at the gate, into the house
        Breakable gate = gate();
        if (gate != null && !gate.contents.stacks().isEmpty()) {
            if (brain.goTo(n, gate.x, fy(gate.y), gate.z, false, 1.1f)) {
                ItemStack s = gate.contents.stacks().get(0);
                takeFrom(n, gate, s.def, s.amount);
                n.lastOutcome = "Porter: took in " + s.def.name.toLowerCase() + " from the gate";
            } else n.lastOutcome = "Porter: to the gate";
            return;
        }
        // 4. nothing to carry: by the back door, ready
        if (brain.goTo(n, 46.5f, Mansion.LEVELS[0], 138.5f, false, 1.2f)) g.ai.desire(n, AiSystem.A_IDLE);
        n.lastOutcome = "Porter: waiting on the next load";
    }

    private Breakable kitchenCrate() {
        for (Breakable b : g.breakables.all) if (!b.broken && b.form == Breakable.Form.CRATE && Mansion.roomAt((int) Math.floor(b.x), (int) Math.floor(b.z), 0) != null
                && "Kitchen".equals(Mansion.roomAt((int) Math.floor(b.x), (int) Math.floor(b.z), 0).name()) && Mansion.level(b.y) == 0) return b;
        return null;
    }

    // ------------------------------------------------------------------ the Firewood Cutter
    private void cutter(Npc n) {
        // the fires first: a grate with little wood left gets a log, a cold one is laid and lit
        Furniture low = null; float least = 120;
        for (Furniture f : g.objects.furniture) if (f.kind == Furniture.Kind.FIREPLACE && f.exists() && Mansion.inside(f.x, f.z) && f.fuel < least) { least = f.fuel; low = f; }
        if (low != null && !n.has(Items.LOGS, 1)) {
            Breakable c = kitchenCrate();
            if (c != null && c.contents.count(Items.LOGS) > 0) {
                if (brain.goTo(n, c.x, fy(c.y), c.z, false, 1.1f)) { takeFrom(n, c, Items.LOGS, 3); n.lastOutcome = "Cutter: logs from the kitchen crate"; }
                else n.lastOutcome = "Cutter: to the log crate";
                return;
            }
        }
        if (low != null && n.has(Items.LOGS, 1)) {
            float fx = low.x - (float) Math.sin(low.yaw()) * 1.2f, fz = low.z - (float) Math.cos(low.yaw()) * 1.2f;
            if (brain.goTo(n, fx, low.floor, fz, false, 0.9f)) {
                n.wantYaw = (float) Math.atan2(-(low.x - n.x), -(low.z - n.z));
                Result r = low.stoke(g, n, g.state.newRequestId());
                n.lastOutcome = "Cutter: " + (least <= 0 ? "laid and lit" : "fed") + " the fire -> " + r.status();
            } else n.lastOutcome = "Cutter: logs to the fire";
            return;
        }
        // the wood: felled in the wood round the estate; a load goes to the kitchen crate
        int have = n.count(Items.LOGS);
        if (have >= 5) {
            Breakable c = kitchenCrate();
            if (c != null) {
                if (brain.goTo(n, c.x, fy(c.y), c.z, false, 1.1f)) { int put = putInto(n, c, Items.LOGS); n.lastOutcome = "Cutter: stacked " + put + " logs by the range"; }
                else n.lastOutcome = "Cutter: carrying logs in";
                return;
            }
        }
        var w = g.interact.of(n);
        if (w != null) { g.ai.desire(n, AiSystem.A_IDLE); return; }
        Node tree = null; float bd = 40;
        for (Node nd : g.objects.nodes) {
            if (nd.kind != Node.Kind.TREE || nd.depleted() || n.skills.level(nd.skill) < nd.level) continue;
            float d = (float) Math.hypot(nd.x - n.x, nd.z - n.z);
            if (d < bd) { bd = d; tree = nd; }
        }
        if (tree == null) { n.lastOutcome = "Cutter: no tree standing near; stacking the pile"; brain.goTo(n, Mansion.WOODPILE[0], Mansion.WOODPILE[1], false, 1.2f); return; }
        if (n.main() == null || n.main().toolSkill != rpg.skill.Skill.WOODCUTTING) {
            ItemDef axe = null;
            for (var grid : n.grids()) for (ItemStack s : grid.stacks()) if (axe == null && s.def.toolSkill == rpg.skill.Skill.WOODCUTTING && !s.def.twoHanded()) axe = s.def;
            if (axe != null) g.items.equipFirst(n, axe, ItemDef.Slot.MAIN, g.state.newRequestId());
        }
        float[] spot = g.ai.standSpot(n, tree);
        if (brain.goTo(n, spot[0], spot[1], false, 0.6f)) {
            n.wantYaw = (float) Math.atan2(-(tree.x - n.x), -(tree.z - n.z));
            Result r = g.interact.request(n, tree, "gather");
            n.lastOutcome = "Cutter: felling " + tree.name.toLowerCase() + " -> " + r.status();
        } else n.lastOutcome = "Cutter: to a tree for firewood";
    }

    // ------------------------------------------------------------------ the Orchard Keeper
    private void orchard(Npc n) {
        int apples = n.count(Items.APPLE);
        Farming.AppleTree tree = g.farming.pickable(n.id, apples > 0 ? 2 : 3);
        if ((apples >= 8 || apples > 0 && tree == null)) {
            Breakable gate = gate();
            if (gate != null) {
                if (brain.goTo(n, gate.x, fy(gate.y), gate.z, false, 1.1f)) { int put = putInto(n, gate, Items.APPLE); n.lastOutcome = "Orchard Keeper: left " + put + " apples at the gate for the house"; }
                else n.lastOutcome = "Orchard Keeper: apples to the gate";
                return;
            }
        }
        if (tree != null && n.canStore(new ItemStack(Items.APPLE, 1))) {
            float ox = n.x < tree.x ? -1.3f : 1.3f;
            if (brain.goTo(n, tree.x + ox, tree.z, false, 0.9f)) { n.wantYaw = (float) Math.atan2(-(tree.x - n.x), -(tree.z - n.z)); Result r = g.interact.request(n, tree, "pick"); n.lastOutcome = "Orchard Keeper: picking (" + tree.apples + " on the tree) -> " + r.status(); }
            else n.lastOutcome = "Orchard Keeper: to a tree with " + tree.apples + " apples";
            return;
        }
        // between pickings, the trees looked over, and the hedges along the drive
        int k = (int) ((g.state.minutes / 11 + n.id) % (Mansion.ORCHARD.length + 2));
        float[] spot = k < Mansion.ORCHARD.length ? new float[]{Mansion.ORCHARD[k][0] + 1.4f, Mansion.ORCHARD[k][1] + 0.5f} : new float[]{k % 2 == 0 ? 43.5f : 50.5f, 155.5f};
        if (brain.goTo(n, spot[0], spot[1], false, 1.0f)) g.ai.desire(n, AiSystem.A_IDLE);
        n.lastOutcome = k < Mansion.ORCHARD.length ? "Orchard Keeper: looking the trees over" : "Orchard Keeper: clipping the hedges";
    }

    /** The walk the Household Knight makes round the bounds when his patron is safe at home (Jobs asks). */
    static float[] bounds(int i) { return Mansion.PATROL[Math.floorMod(i, Mansion.PATROL.length)]; }
}
