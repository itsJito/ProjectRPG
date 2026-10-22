package rpg.objects;

import java.util.ArrayList;
import java.util.List;
import rpg.Rpg;
import rpg.actor.Actor;
import rpg.ai.Npc;
import rpg.interact.Option;
import rpg.interact.Result;
import rpg.item.ItemDef;
import rpg.item.ItemStack;

/**
 * A dead person's body, to be searched: what they carried is in its grid, and what they wore is still on them - the
 * search window mirrors their equipment, and a piece taken comes off the body you see. It lies where the body lies (the
 * AI moves it with the ragdoll). Searching an outlaw's body is fair; anyone else's belongings are theirs still, and taking
 * them is theft.
 */
public final class Corpse extends Container {
    /** Whose body it is. */
    public final long body;

    public Corpse(long id, String name, long body, float x, float y, float z, long owner) {
        super(id, "corpse", name + "'s body", "corpse", x, y, z, 40, owner);
        this.body = body;
    }

    /** The dead person, while their body is there to search. */
    public Npc npc(Rpg g) { return g.actor(body) instanceof Npc n && !n.alive ? n : null; }

    /** What they still wear, slot by slot (nothing for a slot left empty). */
    public List<ItemDef.Slot> worn(Rpg g) {
        List<ItemDef.Slot> l = new ArrayList<>();
        Npc n = npc(g);
        if (n != null) for (ItemDef.Slot s : ItemDef.Slot.values()) if (n.eq.get(s) != null) l.add(s);
        return l;
    }

    public boolean empty(Rpg g) { return contents.isEmpty() && worn(g).isEmpty(); }

    @Override public float focusRadius() { return 0.8f; }
    @Override public float ay() { return y + 0.1f; }
    @Override public boolean exists() { return !removed; }

    @Override public List<Option> options(Rpg g, Actor a) {
        List<Option> o = new ArrayList<>();
        float r = g.cfg.interactRange;
        o.add(Option.on("open", "Search body" + (isTheft(a) ? " (theft)" : "") + (empty(g) ? " (nothing left)" : ""), 10, r));
        o.add(Option.on("examine", "Examine", 0, g.cfg.inspectRange));
        return o;
    }

    @Override public Result perform(Rpg g, Actor a, String action, long req) {
        if (action.startsWith("takeworn:")) return takeWorn(g, a, ItemDef.Slot.valueOf(action.substring(9)), req);
        if (action.startsWith("takeall")) {
            // what they carried, then what they wore
            Result r = contents.isEmpty() ? Result.ok() : super.perform(g, a, action, req);
            if (r.rejected()) return r;
            for (ItemDef.Slot s : worn(g)) { Result w = takeWorn(g, a, s, g.state.newRequestId()); if (w.rejected()) return w; }
            return Result.ok();
        }
        return super.perform(g, a, action, req);
    }

    /** Takes a worn piece off the body: all of it or nothing, theft as for anything else of theirs. */
    public Result takeWorn(Rpg g, Actor a, ItemDef.Slot slot, long req) {
        String why = reach(g, a, g.cfg.interactRange);
        if (why != null) return Result.no(why);
        Npc n = npc(g);
        ItemStack s = n == null ? null : n.eq.get(slot);
        if (s == null) return Result.no("That's no longer there.");
        ItemStack moving = s.copyWhole();
        boolean theft = isTheft(a);
        if (theft) moving.markStolen(owner);
        if (!a.canStore(moving)) return Result.no("You don't have enough inventory space.");
        n.eq.set(slot, null);
        a.store(moving);
        var ev = g.state.commit(req, "ItemTaken", a.id, id, jurisdiction(), 0, moving + " worn by " + n.name);
        if (theft) g.law.theft(a, owner, moving, this, ev.id());
        if (a == g.player && g.sfx != null) g.sfx.ui("pickup", 0.5f);
        return Result.ok();
    }

    @Override public String examine(Rpg g) {
        Npc n = npc(g);
        return n == null ? "A body." : n.name + ", dead." + (empty(g) ? " Nothing left on them." : "");
    }
}
