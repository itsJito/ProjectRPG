package rpg.objects;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import rpg.Rpg;
import rpg.actor.Actor;
import rpg.interact.Option;
import rpg.interact.Result;
import rpg.interact.WorldObject;
import rpg.item.ItemIO;
import rpg.item.ItemStack;

/**
 * A loose item lying in the world (GDD 3.2): Take moves it to the taker exactly once, keeping its original owner; with
 * no room it stays where it is. An owned item can still be taken - as a theft.
 */
public final class GroundItem extends WorldObject {
    public ItemStack stack;
    /**
     * A single arrow, bolt or thrown blade lies exactly as it came to rest (gamefixes): its middle at x, y, z and its
     * point along dir (null: an ordinary item, set down on its base). While it is still moving it is a rigid body in the
     * physics world (rigid), whose pose is read back each frame until it settles.
     */
    public float[] dir;
    public long rigid;
    public double rigidSince;
    /** Full resting orientation for broad gear such as shields; long projectile direction remains separate. */
    public float[] rotation;
    public float vx,vy,vz,wx,wy,wz;
    public boolean resumePhysics;
    /** Lodged in something (a wall, a tree, the ground, a target): it stays put until taken. */
    public boolean lodged;

    public GroundItem(long id, ItemStack stack, float x, float y, float z) {
        super(id, "ground", stack.def.name, x, y, z);
        this.stack = stack;
        this.owner = stack.stolen ? 0 : stack.owner;
    }

    @Override public float focusRadius() { return 0.45f; }
    @Override public String label() { return stack.amount > 1 ? stack.def.name + " (" + stack.amount + ")" : stack.def.name; }

    public boolean isTheft(Actor a) { return stack.owner != 0 && stack.owner != a.id && !stack.stolen; }

    @Override public List<Option> options(Rpg g, Actor a) {
        List<Option> o = new ArrayList<>();
        o.add(Option.when(a.canStore(stack), "take", isTheft(a) ? "Take (steal)" : "Take", 10, "Your inventory is too full.", g.cfg.interactRange));
        o.add(Option.on("examine", "Examine", 0, g.cfg.inspectRange));
        return o;
    }

    @Override public Result perform(Rpg g, Actor a, String action, long req) {
        if (action.equals("examine")) { if (a == g.player) g.chat.say(stack.def.examine); return Result.ok(); }
        if (!action.equals("take") && !action.equals("telegrab")) return Result.no("You can't do that.");
        if (action.equals("take")) { String why = reach(g, a, g.cfg.interactRange); if (why != null) return Result.no(why); }
        return takeBy(g, a, req, action.equals("telegrab") ? "telekinesis" : "hand");
    }

    /** The transfer itself, once reach is settled (by hand, or by spell). */
    public Result takeBy(Rpg g, Actor a, long req, String how) {
        if (removed) return Result.no("It's gone.");
        boolean theft = isTheft(a);
        ItemStack moving = stack.copy(stack.amount);
        if (theft) moving.markStolen(stack.owner);
        if (!a.canStore(moving)) return Result.no("You don't have enough inventory space to hold that.");
        a.store(moving);
        a.grab(g.time, x, y, z);
        removed = true;
        if (rigid != 0) { g.game.commands().release(rigid); rigid = 0; }
        var ev = g.state.commit(req, "ItemTaken", a.id, id, jurisdiction(), 0, moving + " from the ground by " + how);
        if (theft) g.law.theft(a, stack.owner, moving, this, ev.id());
        if (a == g.player && g.sfx != null) g.sfx.ui("pickup", 0.5f);
        return Result.ok();
    }

    @Override public String inspect(Rpg g) { return super.inspect(g) + "\n  stack=" + stack + " item owner=" + g.nameOf(stack.owner) + " stolen=" + stack.stolen; }

    @Override public Map<String, Object> save() {
        Map<String, Object> m = super.save(); m.put("stack", ItemIO.stack(stack)); m.put("x", x); m.put("y", y); m.put("z", z);
        if (dir != null) { m.put("dx", dir[0]); m.put("dy", dir[1]); m.put("dz", dir[2]); m.put("lodged", lodged); }
        if(rotation!=null){m.put("qw",rotation[0]);m.put("qx",rotation[1]);m.put("qy",rotation[2]);m.put("qz",rotation[3]);m.put("moving",rigid!=0);m.put("velocity",java.util.List.of(vx,vy,vz,wx,wy,wz));}
        return m;
    }

    /** A saved arrow's pose (an ordinary item has none). */
    public void restorePose(Map<String, Object> saved) {
        if (saved.containsKey("dx")) dir = new float[]{(float) astratto.util.Json.num(saved, "dx", 0), (float) astratto.util.Json.num(saved, "dy", 0), (float) astratto.util.Json.num(saved, "dz", 1)};
        lodged = astratto.util.Json.bool(saved, "lodged", false);
        if(!saved.containsKey("qw") && dir==null && !stack.def.natural && (stack.def.slot==rpg.item.ItemDef.Slot.MAIN || stack.def.slot==rpg.item.ItemDef.Slot.OFF)) {
            rotation=new float[]{0.9238795f,0.3826834f,0,0};resumePhysics=true;
        }
        if(saved.containsKey("qw")) {
            rotation=new float[]{(float)astratto.util.Json.num(saved,"qw",1),(float)astratto.util.Json.num(saved,"qx",0),(float)astratto.util.Json.num(saved,"qy",0),(float)astratto.util.Json.num(saved,"qz",0)};
            resumePhysics=astratto.util.Json.bool(saved,"moving",false);
            var velocity=astratto.util.Json.list(saved,"velocity");if(velocity.size()==6){vx=((Number)velocity.get(0)).floatValue();vy=((Number)velocity.get(1)).floatValue();vz=((Number)velocity.get(2)).floatValue();wx=((Number)velocity.get(3)).floatValue();wy=((Number)velocity.get(4)).floatValue();wz=((Number)velocity.get(5)).floatValue();}
        }
    }
}
