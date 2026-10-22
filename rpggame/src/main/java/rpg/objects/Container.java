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
    public boolean locked;
    public int lockLevel;
    public final String kind;
    /** Who may take freely besides the owner (a grave's owner only). */
    public boolean ownerOnly;
    /** A player's grave (GDD 3.12): when it goes to the undertaker (game seconds), whether it lay inside a town's walls
     *  (secured: no one may loot it), the guard carrying it in (0: none), and who killed them, as the grave remembers. */
    public double expiresAt = -1;
    public boolean secured;
    public long heldBy, killer;
    /** The owner's Honor at their death (a grave's memorial). */
    public int honor;

    public Container(long id, String def, String name, String kind, float x, float y, float z, int size, long owner) {
        super(id, def, name, x, y, z);
        this.kind = kind; this.owner = owner;
        // a chest is a 10 x 10 grid, a grave or a corpse 10 x 4 like a person's own, a cart's crate 10 x 6
        int[] dims = switch (kind) { case "chest" -> new int[]{10, 10}; case "grave", "corpse" -> new int[]{10, 4}; case "cargo" -> new int[]{10, 6}; default -> new int[]{10, Math.max(1, (size + 9) / 10)}; };
        this.contents = new Inventory(dims[0], dims[1]);
        this.contents.container = true;
    }

    @Override public float focusRadius() { return 0.7f; }
    @Override public String label() { return name + (locked ? " (locked)" : ""); }

    /** A grave's inscription, chosen by the Honor its owner died with (GDD 3.5 memorial). */
    public String epitaph(Rpg g) {
        String who = g.player.name;
        if (honor >= 66) return "\"Here lies " + who + ". The town grieves a good heart.\"";
        if (honor >= 22) return "\"" + who + ". Remembered kindly.\"";
        if (honor > -22) return "\"Here lies " + who + ".\"";
        if (honor > -66) return "\"" + who + ". Few will mourn.\"";
        return "\"" + who + ". Good riddance.\" (scratched in by another hand)";
    }

    public boolean isTheft(Actor a) { return owner != 0 && owner != a.id; }

    @Override public List<Option> options(Rpg g, Actor a) {
        List<Option> o = new ArrayList<>();
        float r = g.cfg.interactRange;
        String open = kind.equals("grave") ? "Search grave" : kind.equals("cargo") ? "Search cargo" : "Open";
        if (locked) {
            o.add(Option.off("open", open, 10, "It's locked.", r));
            boolean lvl = a.skills.level(Skill.THIEVING) >= lockLevel, pick = a.has(Items.LOCKPICK, 1);
            o.add(Option.when(lvl && pick, "lockpick", "Pick lock", 9, !lvl ? "You need Thieving level " + lockLevel + "." : "You need a lockpick.", r));
        } else o.add(Option.on("open", open + (isTheft(a) ? " (owned)" : ""), 10, r));
        o.add(Option.on("examine", "Examine", 0, g.cfg.inspectRange));
        return o;
    }

    @Override public Result perform(Rpg g, Actor a, String action, long req) {
        if (action.equals("examine")) { if (a == g.player) g.chat.say(examine(g)); return Result.ok(); }
        String why = reach(g, a, g.cfg.interactRange);
        if (why != null) return Result.no(why);
        if (action.equals("open")) {
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
                if (isTheft(a)) g.law.crime(a, "Lockpicking", owner, 0, this, ev.id());
                if (ok) { locked = false; g.prog.award(a, Skill.THIEVING, 35, "picked a lock", ev.id()); }
                if (a == g.player) g.chat.say(ok ? "You pick the lock." : "You fail to pick the lock.");
                return Result.ok();
            }));
            return Result.started();
        }
        if (action.startsWith("take:")) return take(g, a, Integer.parseInt(action.substring(5)), Integer.MAX_VALUE, req);
        if (action.startsWith("takeall")) {
            List<ItemStack> all = contents.stacks();
            if (all.isEmpty()) return Result.no("It's empty.");
            if (!a.canStoreAll(all)) return Result.no("You don't have enough inventory space.");
            for (int i = 0; i < contents.size(); i++) if (contents.get(i) != null) { Result r = take(g, a, i, Integer.MAX_VALUE, g.state.newRequestId()); if (r.rejected()) return r; }
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
            if (kind.equals("cargo") || kind.equals("grave") || kind.equals("corpse")) return Result.no("You can't put things in there.");
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

    /** Moves a slot's stack to the actor: all of it or nothing. */
    public Result take(Rpg g, Actor a, int slot, int n, long req) {
        String why = reach(g, a, g.cfg.interactRange);
        if (why != null) return Result.no(why);
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
        a.store(moving);
        var ev = g.state.commit(req, "ItemTaken", a.id, id, jurisdiction(), 0, moving + " from " + name);
        if (theft) g.law.theft(a, owner, moving, this, ev.id());
        if (a == g.player && g.sfx != null) g.sfx.ui("pickup", 0.5f);
        onTaken(g);
        return Result.ok();
    }

    protected void onTaken(Rpg g) { }

    @Override public String examine(Rpg g) { return kind.equals("grave") ? String.format("%s What you were carrying when you died. %s %d:%02d before the undertaker takes what is left.", epitaph(g),
            secured ? "Inside the walls: nobody may touch it." : "Outside the walls: anyone may.", (int) Math.max(0, expiresAt - g.time) / 60, (int) Math.max(0, expiresAt - g.time) % 60) : owner == 0 ? "A container." : "It belongs to " + g.nameOf(owner) + "."; }
    @Override public String inspect(Rpg g) { return super.inspect(g) + "\n  locked=" + locked + " contents=" + contents.stacks(); }

    @Override public Map<String, Object> save() { Map<String, Object> m = super.save(); m.put("locked", locked); m.put("items", ItemIO.inventory(contents)); return m; }
    @Override public void load(Rpg g, Map<String, Object> m) { super.load(g, m); locked = astratto.util.Json.bool(m, "locked", locked); ItemIO.inventory(contents, astratto.util.Json.list(m, "items")); }
}
