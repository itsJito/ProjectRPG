package rpg.objects;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import rpg.Rpg;
import rpg.actor.Actor;
import rpg.ai.Npc;
import rpg.interact.Option;
import rpg.interact.Result;
import rpg.interact.WorldObject;
import rpg.world.Blocks;
import rpg.world.DevMap;

/**
 * A piece of furniture (GDD 3.2 owned furnishing): an object, not a block and not an item - it has its own model, an
 * owner, a placement and persistent state, and stands in the world on invisible collision cells so bodies, arrows and
 * eyes meet it where it is drawn. A bed can be slept in by its owner (or in an unowned one), skipping the night through
 * the catch-up clock; the archery target scores the arrows that strike its cells.
 */
public final class Furniture extends WorldObject {
    public enum Kind {
        TABLE("Table", Blocks.TABLE), COUNTER("Counter", Blocks.TABLE), CRATE("Crate", Blocks.CRATE), BOOKCASE("Bookcase", Blocks.BOOKCASE),
        BED("Bed", Blocks.BED), TARGET("Archery target", Blocks.TARGET), HEADSTONE("Headstone", Blocks.HEADSTONE), SLAB("Cell slab", Blocks.BED);
        public final String title;
        public final int block;
        Kind(String title, int block) { this.title = title; this.block = block; }
    }

    /** Surface heights above the floor (m): a table's top, a counter's top, a bed's mattress. */
    public static final float TABLE_TOP = 0.8f, COUNTER_TOP = 1.0f, MATTRESS = 0.55f;

    public final Kind kind;
    /** The cells it stands on: x0..x1, z0..z1 from the floor up, h cells tall. */
    public final int x0, z0, x1, z1, h;
    /** Quarter turns from facing +z (the side a bed's foot, a counter's front, a target's face is on). */
    public final int facing;
    /** Built and standing (furniture a build spot makes is not, until it is built). */
    public boolean placed;

    public Furniture(long id, Kind kind, int x0, int z0, int x1, int z1, int h, int facing, long owner, boolean placed) {
        super(id, kind.name().toLowerCase(), kind.title, (x0 + x1 + 1) / 2f, DevMap.FLOOR + Math.min(1f, h * 0.5f), (z0 + z1 + 1) / 2f);
        this.kind = kind; this.x0 = Math.min(x0, x1); this.z0 = Math.min(z0, z1); this.x1 = Math.max(x0, x1); this.z1 = Math.max(z0, z1); this.h = h;
        this.facing = facing; this.owner = owner; this.placed = placed;
    }

    public float yaw() { return facing * (float) Math.PI / 2; }
    public float width() { return x1 - x0 + 1; }
    public float depth() { return z1 - z0 + 1; }

    @Override public boolean exists() { return placed && !removed; }
    @Override public float focusRadius() { return Math.max(0.6f, Math.max(width(), depth()) * 0.55f); }

    /** A long counter occupies a low box, not a head-height sphere several metres wide. */
    @Override public float focusEntry(float ox, float oy, float oz, float dx, float dy, float dz) {
        float top = switch (kind) { case TABLE -> TABLE_TOP; case COUNTER -> COUNTER_TOP; case BED, SLAB -> 0.65f; default -> h; };
        float enter = 0, exit = Float.POSITIVE_INFINITY;
        for (int axis = 0; axis < 3; axis++) {
            float o = axis == 0 ? ox : axis == 1 ? oy : oz, d = axis == 0 ? dx : axis == 1 ? dy : dz;
            float lo = axis == 0 ? x0 : axis == 1 ? DevMap.FLOOR : z0, hi = axis == 0 ? x1 + 1 : axis == 1 ? DevMap.FLOOR + top : z1 + 1;
            if (Math.abs(d) < 1e-6f) { if (o < lo || o > hi) return Float.POSITIVE_INFINITY; continue; }
            float a = (lo - o) / d, b = (hi - o) / d;
            enter = Math.max(enter, Math.min(a, b)); exit = Math.min(exit, Math.max(a, b));
            if (enter > exit) return Float.POSITIVE_INFINITY;
        }
        return enter;
    }

    /** Puts the collision cells in or takes them out (the model is drawn while it is placed). */
    public void place(Rpg g, boolean on) {
        placed = on;
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) for (int y = 0; y < h; y++) g.setBlock(x, DevMap.FLOOR + y, z, on ? kind.block : Blocks.AIR);
    }

    @Override public List<Option> options(Rpg g, Actor a) {
        List<Option> o = new ArrayList<>();
        if (kind == Kind.BED || kind == Kind.SLAB) o.add(Option.when(sleepRefusal(g, a) == null, "sleep", "Sleep", 10, sleepRefusal(g, a), g.cfg.interactRange));
        o.add(Option.on("examine", "Examine", 0, g.cfg.inspectRange));
        return o;
    }

    public boolean cellSlab() { return kind == Kind.SLAB; }

    /** Null when the actor may sleep here now, else why not. */
    public String sleepRefusal(Rpg g, Actor a) {
        if (cellSlab()) return a == g.player && g.law.inCustody(a) ? null : "This slab is reserved for prisoners.";
        if (owner != 0 && owner != a.id) return "This is " + g.nameOf(owner) + "'s bed.";
        for (Npc n : g.npcs) if (n.alive && n.loaded && (n.hostileToPlayer || n.fightTarget == a.id) && n.distTo(a) < 14) return "You can't sleep with enemies nearby.";
        if (a.cs.busy()) return "You're busy.";
        return null;
    }

    @Override public Result perform(Rpg g, Actor a, String action, long req) {
        if (action.equals("examine")) { if (a == g.player) g.chat.say(examine(g)); return Result.ok(); }
        String why = reach(g, a, g.cfg.interactRange);
        if (why != null) return Result.no(why);
        if (action.equals("sleep") && (kind == Kind.BED || kind == Kind.SLAB)) {
            why = sleepRefusal(g, a);
            if (why != null) return Result.no(why);
            if (cellSlab() && a == g.player) { if (g.ui != null) g.ui.fadeThen(g.law::sleepThroughSentence); else g.law.sleepThroughSentence(); return Result.ok(); }
            // sleep until six in the morning (at least an hour): time passes through the catch-up coordinator
            double hour = g.state.hourOfDay();
            double minutes = Math.max(60, ((6 - hour + 24) % 24) * 60);
            g.state.commit(req, "Slept", a.id, id, jurisdiction(), 0, String.format("%.0f minutes in %s", minutes, label()));
            g.advanceTime(minutes, "sleeping");
            a.hp = a.maxHp(); a.stamina = a.maxStamina();
            if (a == g.player) g.chat.say("You sleep soundly and wake at " + g.state.clockText() + ", rested.");
            return Result.ok();
        }
        return Result.no("You can't do that.");
    }

    @Override public String examine(Rpg g) {
        return switch (kind) {
            case TABLE -> "A sturdy table.";
            case COUNTER -> "A shop counter.";
            case CRATE -> "A wooden crate.";
            case BOOKCASE -> "Shelves of books.";
            case SLAB -> "A cold stone sleeping slab. Prisoners can sleep here to serve their sentence.";
            case BED -> owner == 0 ? "A bed. Anyone may sleep here." : "A bed belonging to " + g.nameOf(owner) + ".";
            case TARGET -> "An archery target. Shoot it for Archery XP; the farther the shot, the more it pays.";
            case HEADSTONE -> HEADSTONE_LINES[(int) Math.floorMod(id * 7919L, (long) HEADSTONE_LINES.length)];
        };
    }

    private static final String[] HEADSTONE_LINES = {"Here lies Old Wen, who trusted a bridge.", "In memory of Tamsin. She kept the lamps lit.",
            "Rowan Ashby. Gone to the great forge.", "Here lies a stranger. The guards never learned his name.", "Brother Aldo. He prayed, and then he didn't.",
            "Hesk the miller. Loved by his hens.", "Mira Vell. Taken by the wolves of the north wood.", "Here lies Cobb. He owed nobody anything.",
            "The weathered stone is too worn to read.", "Ina and Joss, together still."};

    @Override public String inspect(Rpg g) { return super.inspect(g) + String.format("\n  %s cells (%d,%d)-(%d,%d) h%d facing %d placed=%s", kind, x0, z0, x1, z1, h, facing, placed); }

    @Override public Map<String, Object> save() { Map<String, Object> m = super.save(); m.put("placed", placed); return m; }
    @Override public void load(Rpg g, Map<String, Object> m) { super.load(g, m); place(g, astratto.util.Json.bool(m, "placed", placed)); }
}
