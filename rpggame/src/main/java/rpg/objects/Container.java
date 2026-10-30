package rpg.objects;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import rpg.Rpg;
import rpg.actor.Actor;
import rpg.interact.Option;
import rpg.interact.Result;
import rpg.interact.Work;
import rpg.interact.WorldObject;
import rpg.item.ItemIO;
import rpg.item.ItemStack;
import rpg.item.Inventory;
import rpg.item.Items;
import rpg.skill.Skill;

/**
 * A container (GDD 3.2): a chest, a grave, a caravan's cargo. Its contents are items in exactly one place; taking moves
 * them to the taker atomically (all or nothing - a full inventory takes nothing). Taking what belongs to someone else
 * is physically possible: the item keeps its owner and is marked stolen, and a theft is committed for witnesses to see.
 */
public class Container extends WorldObject {
    public final Inventory contents;
    public final Map<String, Integer> custodySlots = new java.util.HashMap<>();
    public boolean locked;
    public int lockLevel;
    public final String kind;
    /** Who may take freely besides the owner (a grave's owner only). */
    public boolean ownerOnly;
    /** A player's grave (GDD 3.12): when it goes to the undertaker (game seconds), whether it lay inside a town's walls
     *  (secured: no one may loot it), the guard carrying it in (0: none), and who killed them, as the grave remembers. */
    public double expiresAt = -1;
    public boolean secured, buried;
    public String burialPlot = "";
    public long heldBy, killer;
    /** The owner's Honor at their death (a grave's memorial). */
    public int honor;

    public Container(long id, String def, String name, String kind, float x, float y, float z, int size, long owner) {
        super(id, def, name, x, y, z);
        this.kind = kind; this.owner = owner;
        // a chest is a 10 x 10 grid, a grave or a corpse 10 x 4 like a person's own, a cart's crate 10 x 6
        int[] dims = switch (kind) { case "chest" -> new int[]{10, 10}; case "corpse" -> new int[]{10, 4}; case "grave" -> size>40?new int[]{16,Math.max(1,(size+15)/16)}:new int[]{10,4}; case "cargo" -> new int[]{10, 6}; case "memorial", "claim", "escrow" -> new int[]{16, Math.max(1,(size+15)/16)}; default -> new int[]{10, Math.max(1, (size + 9) / 10)}; };
        this.contents = new Inventory(dims[0], dims[1]);
        // A death record holds the complete possessions, including filled worn bags.
        this.contents.container = !kind.equals("grave") && !kind.equals("corpse") && !kind.equals("memorial") && !kind.equals("claim") && !kind.equals("escrow");
    }

    /** The drawn chest's half extents (Gfx: a 0.94 x 0.66 x 0.64 box on the floor). */
    public static final float CHEST_HX = 0.47f, CHEST_HY = 0.34f, CHEST_HZ = 0.32f;
    private long solidBody;

    /**
     * (0.2.2, gamefixes: the undertaker's chest and every other had no collision) a chest is solid: a fixed box exactly
     * its drawn size, as the bounty boards are, so bodies stand against it and it can be stood on.
     */
    public void solid(Rpg g) {
        if (solidBody != 0 || g.game == null) return;
        solidBody = g.game.reserve(astratto.bridge.AstrattoNative.KIND_RIGID);
        g.game.commands().rigidCreate(solidBody, astratto.bridge.AstrattoNative.SHAPE_BOX, astratto.bridge.AstrattoNative.RIGID_STATIC, CHEST_HX, CHEST_HY, CHEST_HZ, 0,
                x, rpg.world.DevMap.FLOOR + CHEST_HY, z, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0.6f, 0.1f, 0, null);
    }

    /** A transient view over an actor's live inventory; never registered or serialized as a second owner. */
    protected Container(long id,String name,String kind,float x,float y,float z,long owner,Inventory contents) {
        super(id,kind,name,x,y,z);this.kind=kind;this.owner=owner;this.contents=contents;
    }

    @Override public float focusRadius() { return 0.7f; }

    /** A bank vault is reached across the bank's counter while the teller serves (its strongroom is under the floor). */
    @Override public String reach(Rpg g, Actor a, float range) {
        if (kind.equals("vault")) return g.bank.vaultRefusal(a, this);
        return super.reach(g, a, range);
    }
    @Override public String label() { return name + ((kind.equals("memorial") || kind.equals("grave") && buried) ? buried ? " (Filled)" : " (Unfilled)" : locked ? " (locked)" : ""); }


    public boolean isTheft(Actor a) { return owner != 0 && owner != a.id; }

    @Override public List<Option> options(Rpg g, Actor a) {
        List<Option> o = new ArrayList<>();
        float r = g.cfg.interactRange;
        if (kind.equals("claim") || kind.equals("escrow") || kind.equals("vault")) return o;
        if (kind.equals("memorial") && !buried) {
            o.add(Option.when(g.carrying.busy(a), "bury", "Bury carried body", 10, "Carry a body to this unfilled grave first.", r));
            return o;
        }
        String open = kind.equals("grave") ? "Search grave" : kind.equals("cargo") ? "Search cargo" : "Open";
        if(kind.equals("grave") && (secured||buried)) { o.add(Option.off("open",open,10,"Speak to the undertaker about this protected claim.",r));return o; }
        if (locked) {
            o.add(Option.off("open", open, 10, "It's locked.", r));
            boolean lvl = a.skills.level(Skill.THIEVING) >= lockLevel, pick = a.has(Items.LOCKPICK, 1);
            o.add(Option.when(lvl && pick, "lockpick", "Pick lock", 9, !lvl ? "You need Thieving level " + lockLevel + "." : "You need a lockpick.", r));
        } else o.add(Option.on("open", open + (isTheft(a) ? " (owned)" : ""), 10, r));
        return o;
    }

    @Override public Result perform(Rpg g, Actor a, String action, long req) {
        String why = reach(g, a, g.cfg.interactRange);
        if (why != null) return Result.no(why);
        if (kind.equals("claim") || kind.equals("escrow")) return Result.no("These belongings are held in custody.");
        if (action.equals("bury")) return g.graveyards.buryCarried(a, this) ? Result.ok() : Result.no("This stone must be unfilled and you must carry a body beside it.");
        if (kind.equals("memorial") && !buried) return Result.no("This grave is unfilled.");
        if (action.equals("open")) {
            if ((buried||secured) && kind.equals("grave")) return Result.no("Speak to the undertaker to reclaim these belongings.");
            if (locked) return Result.no("It's locked.");
            if (heldBy != 0) return Result.no(g.nameOf(heldBy) + " is carrying it to the undertaker.");
            if (a == g.player) g.ui.openContainer(this);
            g.state.commit(req, "ContainerOpened", a.id, id, jurisdiction(), 0, name);
            return Result.ok();
        }
        if (action.equals("lockpick")) {
            if (!locked) return Result.no("It isn't locked.");
            if (a.skills.level(Skill.THIEVING) < lockLevel) return Result.no("You need Thieving level " + lockLevel + " to pick this lock.");
            if (!a.has(Items.LOCKPICK, 1)) return Result.no("You need a lockpick.");
            g.interact.begin(new Work(a, this, "lockpick", "Picking the lock", 2.4f, false, g.time, () -> {
                long r2 = g.state.newRequestId();
                String w2 = reach(g, a, g.cfg.interactRange);
                if (w2 != null) return Result.no(w2);
                if (!locked) return Result.no("It isn't locked any more.");
                double chance = Math.min(0.95, 0.4 + (a.skills.level(Skill.THIEVING) - lockLevel) * 0.04), roll = g.roll(r2, id);
                boolean ok = roll < chance;
                var ev = g.state.commit(r2, ok ? "LockPicked" : "LockpickFailed", a.id, id, jurisdiction(), 0, String.format("%s chance=%.2f roll=%.3f", name, chance, roll));
                if (isTheft(a)) g.law.crime(a, "LOCKPICKING", owner, 0, this, ev.id());
                if (ok) { locked = false; g.prog.award(a, Skill.THIEVING, 35, "picked a lock", ev.id()); }
                if (a == g.player) g.chat.say(ok ? "You pick the lock." : "You fail to pick the lock.");
                return Result.ok();
            }));
            return Result.started();
        }
        if (action.startsWith("take:")) return take(g, a, Integer.parseInt(action.substring(5)), Integer.MAX_VALUE, req);
        if (action.startsWith("takeall")) {
            // takeall, takeall:equipment (gear: anything worn or wielded) or takeall:items (everything else)
            java.util.function.Predicate<ItemStack> which = takeAllFilter(action);
            List<ItemStack> all = contents.stacks().stream().filter(which).toList();
            if (all.isEmpty()) return Result.no(action.endsWith(":equipment") ? "There's no equipment in it." : action.endsWith(":items") ? "There are no items in it." : "It's empty.");
            if (!a.canStoreAll(all)) return Result.no("You don't have enough inventory space.");
            for (int i = 0; i < contents.size(); i++) if (contents.get(i) != null && which.test(contents.get(i))) { Result r = take(g, a, i, Integer.MAX_VALUE, g.state.newRequestId()); if (r.rejected()) return r; }
            return Result.ok();
        }
        if (action.equals("depositcoins")) {
            // the gold carried, into it (the bank's "Deposit Gold")
            if (!kind.equals("vault")) return Result.no("You can't do that.");
            int n = a.count(rpg.item.Items.COINS);
            if (n <= 0) return Result.no("You aren't carrying any gold.");
            ItemStack probe = new ItemStack(rpg.item.Items.COINS, n);
            if (!contents.canAdd(probe)) return Result.no("There's no room for it.");
            for (ItemStack coin : a.takeItems(rpg.item.Items.COINS, n)) contents.add(coin);
            g.state.commit(g.state.newRequestId(), "ItemDeposited", a.id, id, jurisdiction(), 0, n + " x Gold coins");
            if (a == g.player && g.sfx != null) g.sfx.ui("LootCoinSmall", 0.5f);
            return Result.ok();
        }
        if (action.equals("depositall")) {
            // everything carried into it (gamefixes: "Move All" at the bank) - what is worn stays worn; a bag that still
            // holds things stays carried; anything that will not fit stays in the pack
            if (kind.equals("grave")) return Result.no("You can't put things in there.");
            int moved = 0, left = 0;
            for (var from : a.grids()) for (int slot = 0; slot < from.size(); slot++) {
                ItemStack s = from.get(slot);
                if (s == null) continue;
                ItemStack moving = s.copyWhole();
                if (!contents.accepts(moving) || !contents.canAdd(moving)) { left++; continue; }
                from.take(slot, s.amount);
                contents.add(moving);
                moved++;
                g.state.commit(g.state.newRequestId(), "ItemDeposited", a.id, id, jurisdiction(), 0, moving.toString());
            }
            if (moved == 0) return Result.no(left > 0 ? "There's no room for any of it." : "You aren't carrying anything.");
            if (a == g.player) { if (g.sfx != null) g.sfx.ui("ContainerOpen", 0.4f); if (left > 0) g.chat.add(left + (left == 1 ? " thing" : " things") + " would not fit and stayed in your pack.", rpg.Chat.Kind.WARN); }
            return Result.ok();
        }
        if (action.startsWith("deposit:")) {
            // deposit:<slot> from the base grid, or deposit:<grid>:<slot>[:<x>:<y>:<turned>] from any carried grid to a place
            String[] f = action.substring(8).split(":");
            var grids = a.grids();
            int gi = f.length >= 2 ? Integer.parseInt(f[0]) : 0, slot = Integer.parseInt(f[f.length >= 2 ? 1 : 0]);
            if (gi < 0 || gi >= grids.size()) return Result.no("Nothing there.");
            var from = grids.get(gi);
            ItemStack s = from.get(slot);
            if (s == null) return Result.no("Nothing there.");
            // (0.2.2, gamefixes) a caravan's crate takes things like any other box - what is in it when it arrives goes
            // into the receiving store's stock with the rest of the load
            if (kind.equals("grave")) return Result.no("You can't put things in there.");
            ItemStack moving = s.copyWhole();
            if (!contents.accepts(moving)) return Result.no("Only an empty bag can go inside another container.");
            boolean placed = f.length >= 5 && contents.fits(moving.def, Integer.parseInt(f[2]), Integer.parseInt(f[3]), f[4].equals("1"), -1);
            if (!placed && !contents.canAdd(moving)) return Result.no("It's full.");
            from.take(slot, s.amount);
            if (placed) contents.putAt(moving, Integer.parseInt(f[2]), Integer.parseInt(f[3]), f[4].equals("1")); else contents.add(moving);
            g.state.commit(req, "ItemDeposited", a.id, id, jurisdiction(), 0, moving.toString());
            return Result.ok();
        }
        return Result.no("You can't do that.");
    }

    static java.util.function.Predicate<ItemStack> takeAllFilter(String action) {
        if (action.endsWith(":equipment")) return s -> s.def.equippable();
        if (action.endsWith(":items")) return s -> !s.def.equippable();
        return s -> true;
    }

    /** Moves a slot's stack to the actor: all of it or nothing. */
    public Result take(Rpg g, Actor a, int slot, int n, long req) {
        String why = reach(g, a, g.cfg.interactRange);
        if (why != null) return Result.no(why);
        if ((buried||secured) && kind.equals("grave")) return Result.no("Speak to the undertaker first.");
        if (locked) return Result.no("It's locked.");
        ItemStack s = contents.get(slot);
        if (s == null) return Result.no("That's no longer there.");
        if (ownerOnly && owner != a.id) return Result.no("That isn't yours to search.");
        n = Math.min(n, s.amount);
        ItemStack moving = s.copy(n);
        boolean theft = isTheft(a) && moving.owner != a.id;
        if (theft) moving.markStolen(owner);
        if (!a.canStore(moving)) return Result.no("You don't have enough inventory space.");
        contents.take(slot, n);
        a.grab(g.time, x, y + 0.4f, z);
        a.store(moving);
        var ev = g.state.commit(req, "ItemTaken", a.id, id, jurisdiction(), 0, moving + " from " + name);
        if (theft) g.law.theft(a, owner, moving, this, ev.id());
        if (a == g.player && g.sfx != null) g.sfx.ui("pickup", 0.5f);
        onTaken(g);
        return Result.ok();
    }

    protected void onTaken(Rpg g) { }

    @Override public String inspect(Rpg g) { return super.inspect(g) + "\n  locked=" + locked + " contents=" + contents.stacks(); }

    @Override public Map<String, Object> save() { Map<String, Object> m = super.save(); m.put("locked", locked); m.put("buried", buried); m.put("burialPlot", burialPlot); m.put("gridCells", contents.size()); m.put("items", ItemIO.inventory(contents)); if (!custodySlots.isEmpty()) m.put("custodySlots", custodySlots); return m; }
    @Override public void load(Rpg g, Map<String, Object> m) { super.load(g, m); buried = astratto.util.Json.bool(m, "buried", false); burialPlot = astratto.util.Json.str(m, "burialPlot", ""); locked = astratto.util.Json.bool(m, "locked", locked); ItemIO.inventory(contents, astratto.util.Json.list(m, "items")); custodySlots.clear();
        var slots = astratto.util.Json.obj(m, "custodySlots"); if (slots != null) for (var entry : slots.entrySet()) if (entry.getValue() instanceof Number value) custodySlots.put(entry.getKey(), value.intValue());
    }
}
