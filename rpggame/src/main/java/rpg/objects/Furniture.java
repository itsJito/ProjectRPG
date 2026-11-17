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
        BED("Bed", Blocks.BED), TARGET("Archery target", Blocks.TARGET), HEADSTONE("Headstone", Blocks.HEADSTONE), SLAB("Cell slab", Blocks.BED),
        /** (0.3.2) A bench at an inn's table; (0.3.9) solid, like every seat: a sitter is set down onto it (rpg.ai.Seating). */
        BENCH("Bench", Blocks.SEAT),
        // (0.3.8, gamefixes: the DevMansion - its every furnishing, each for any interior) seats, tables and case pieces
        // (solid, see-through to light), beds, the hearth (stone), and what hangs on a wall or a ceiling or lies on the
        // floor (no cells at all). (0.3.9, gamefixes: couches, chairs, stools and lamps had no collision) seats and the
        // standing candelabra are solid now; only a rug, what hangs flat on a wall and what hangs from the ceiling are not
        CHAIR("Chair", Blocks.SEAT), ARMCHAIR("Armchair", Blocks.SEAT), SOFA("Settee", Blocks.SEAT), STOOL("Stool", Blocks.SEAT),
        DINING_TABLE("Dining table", Blocks.TABLE), DESK("Writing desk", Blocks.TABLE), WARDROBE("Wardrobe", Blocks.TABLE), DRESSER("Chest of drawers", Blocks.TABLE),
        NIGHTSTAND("Bedside table", Blocks.TABLE), SIDEBOARD("Sideboard", Blocks.TABLE), CLOCK("Longcase clock", Blocks.TABLE), CUPBOARD("Kitchen dresser", Blocks.TABLE),
        SHELVES("Pantry shelves", Blocks.TABLE), WINE_RACK("Wine rack", Blocks.TABLE), FOURPOSTER("Four-poster bed", Blocks.BED), BATHTUB("Bathtub", Blocks.TABLE),
        WASHSTAND("Washstand", Blocks.TABLE), FIREPLACE("Fireplace", Blocks.HEADSTONE), CHANDELIER("Chandelier", Blocks.AIR), CANDELABRA("Candelabrum", Blocks.TABLE),
        RUG("Rug", Blocks.AIR), PAINTING("Painting", Blocks.AIR), TAPESTRY("Tapestry", Blocks.AIR), MIRROR("Mirror", Blocks.AIR), WEAPON_RACK("Weapon trophy", Blocks.AIR),
        POT_RACK("Pot rack", Blocks.AIR), ARMOR_STAND("Suit of armour", Blocks.TABLE), PLANTER("Potted fern", Blocks.TABLE), BUST("Marble bust", Blocks.HEADSTONE),
        /** (0.4.7) A candle sconce on the wall: a low room's light, where a chandelier would strike a head (no cells). */
        SCONCE("Wall sconce", Blocks.AIR),
        // (0.4.8, gamefixes: fifteen new interior models, fully destructible) the cellar's and the storeroom's, the study's
        // and the library's, the nursery's and the great house's
        BARREL("Barrel", Blocks.TABLE), KEG("Keg", Blocks.TABLE), LECTERN("Lectern", Blocks.TABLE), SPINNING_WHEEL("Spinning wheel", Blocks.TABLE),
        SCROLL_RACK("Scroll rack", Blocks.TABLE), THRONE("Throne", Blocks.TABLE), CRADLE("Cradle", Blocks.TABLE), COAT_RACK("Coat rack", Blocks.TABLE),
        BUTTER_CHURN("Butter churn", Blocks.TABLE), GLOBE("Globe", Blocks.TABLE), TOOL_RACK("Tool rack", Blocks.AIR), MAP_TABLE("Map table", Blocks.TABLE),
        DISPLAY_CABINET("Display cabinet", Blocks.TABLE), FOLDING_SCREEN("Folding screen", Blocks.TABLE), SACKS("Sacks", Blocks.TABLE);
        public final String title;
        public final int block;
        Kind(String title, int block) { this.title = title; this.block = block; }
        /** Slept in. */
        public boolean bed() { return this == BED || this == FOURPOSTER; }
        /** Sat on. */
        public boolean seat() { return this == BENCH || this == CHAIR || this == ARMCHAIR || this == SOFA || this == STOOL; }
        /**
         * (0.3.8) The light it gives, if any: {radius (m), r, g, b, height above its floor}. Candles a warm small pool,
         * a chandelier a room's light from above, a fire in the grate (only while it burns, see fuel).
         */
        public float[] light() {
            return switch (this) {
                case CHANDELIER -> new float[]{11, 4.2f, 2.9f, 1.45f, -1.05f};
                case CANDELABRA -> new float[]{7, 3.0f, 2.05f, 0.95f, 1.7f};
                case SCONCE -> new float[]{6.5f, 2.6f, 1.8f, 0.82f, 1.85f};
                case FIREPLACE -> new float[]{8, 3.2f, 1.6f, 0.55f, 0.5f};
                case DESK, NIGHTSTAND -> new float[]{3.2f, 1.5f, 1.0f, 0.45f, 0.95f};
                default -> null;
            };
        }
    }

    /** Surface heights above the floor (m): a table's top, a counter's top, a bed's mattress. */
    public static final float TABLE_TOP = 0.8f, COUNTER_TOP = 1.0f, MATTRESS = 0.55f, SEAT = 0.46f;

    public final Kind kind;
    /** The cells it stands on: x0..x1, z0..z1 from the floor up, h cells tall. */
    public final int x0, z0, x1, z1, h;
    /** Quarter turns from facing +z (the side a bed's head, a counter's front, a target's face is on). */
    public final int facing;
    /** Built and standing (furniture a build spot makes is not, until it is built). */
    public boolean placed;
    /** (0.3.8) The floor it stands on (the cell above the floor's own blocks): the ground floor's, or an upper storey's. */
    public final int floor;
    /** (0.3.8) The renderer's key for its model (made once). */
    public transient String drawKey;
    /** (0.4.4) The seed its model was made with (its look, and whether it is the plain one). */
    public transient int drawSeed;
    /** (0.3.8) A fireplace's fire: game minutes of wood left in the grate (0: cold). */
    public float fuel;
    /** (0.4.4) A working household's version of the piece (a stone hearth, not a marble fireplace). */
    public boolean plain;
    /** (0.4.4) A table laid for a meal (its bowls, cups, bread and candle drawn on it). */
    public boolean laid;

    public Furniture(long id, Kind kind, int x0, int z0, int x1, int z1, int h, int facing, long owner, boolean placed) {
        this(id, kind, x0, z0, x1, z1, h, facing, owner, placed, DevMap.FLOOR);
    }
    /** (0.3.8) On any floor: floor is the first cell above that floor's blocks. */
    public Furniture(long id, Kind kind, int x0, int z0, int x1, int z1, int h, int facing, long owner, boolean placed, int floor) {
        super(id, kind.name().toLowerCase(), kind.title, (x0 + x1 + 1) / 2f, floor + Math.min(1f, h * 0.5f), (z0 + z1 + 1) / 2f);
        this.kind = kind; this.x0 = Math.min(x0, x1); this.z0 = Math.min(z0, z1); this.x1 = Math.max(x0, x1); this.z1 = Math.max(z0, z1); this.h = h;
        this.facing = facing; this.owner = owner; this.placed = placed; this.floor = floor;
    }

    public float yaw() { return facing * (float) Math.PI / 2; }
    /**
     * (0.4.7, gamefixes: people pull a chair out to sit and push it back when they rise) how far a chair stands out from
     * its table (0 tucked under, 1 drawn right out: CHAIR_PULL m back from the table), eased toward pullTarget; and who
     * sits in it (0: no one).
     */
    public transient float pull, pullTarget;
    public transient long sitter;
    public static final float CHAIR_PULL = 0.45f;
    /** Whether a person sits on it (a chair, a stool, a bench, an armchair, a settee). */
    public boolean seat() { return kind == Kind.CHAIR || kind == Kind.STOOL || kind == Kind.BENCH || kind == Kind.ARMCHAIR || kind == Kind.SOFA; }
    /** The way a seat faces (toward its table): {x, z}. */
    public float[] front() { return new float[]{(float) -Math.sin(yaw()), (float) -Math.cos(yaw())}; }
    public float width() { return x1 - x0 + 1; }
    public float depth() { return z1 - z0 + 1; }

    @Override public boolean exists() { return placed && !removed; }
    @Override public float focusRadius() { return Math.max(0.6f, Math.max(width(), depth()) * 0.55f); }

    /** A long counter occupies a low box, not a head-height sphere several metres wide. */
    @Override public float focusEntry(float ox, float oy, float oz, float dx, float dy, float dz) {
        float top = switch (kind) { case TABLE -> TABLE_TOP; case COUNTER -> COUNTER_TOP; case BED, SLAB -> 0.65f; case BENCH -> SEAT; default -> h; };
        float enter = 0, exit = Float.POSITIVE_INFINITY;
        for (int axis = 0; axis < 3; axis++) {
            float o = axis == 0 ? ox : axis == 1 ? oy : oz, d = axis == 0 ? dx : axis == 1 ? dy : dz;
            float lo = axis == 0 ? x0 : axis == 1 ? floor : z0, hi = axis == 0 ? x1 + 1 : axis == 1 ? floor + top : z1 + 1;
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
        // (0.3.8) a piece with no cells (a rug, a painting, a chandelier, a chair) leaves the world's blocks as they are
        if (kind.block == Blocks.AIR) return;
        for (int x = x0; x <= x1; x++) for (int z = z0; z <= z1; z++) for (int y = 0; y < h; y++) g.setBlock(x, floor + y, z, on ? kind.block : Blocks.AIR);
    }

    @Override public List<Option> options(Rpg g, Actor a) {
        List<Option> o = new ArrayList<>();
        if (kind.bed() || kind == Kind.SLAB) o.add(Option.when(sleepRefusal(g, a) == null, "sleep", "Sleep", 10, sleepRefusal(g, a), g.cfg.interactRange));
        if (kind == Kind.FIREPLACE) o.add(Option.when(a.has(rpg.item.Items.LOGS, 1), "stoke", fuel > 0 ? "Put a log on the fire" : "Lay and light a fire", 9, "You need logs.", g.cfg.interactRange));
        return o;
    }

    public boolean cellSlab() { return kind == Kind.SLAB; }

    /** Null when the actor may sleep here now, else why not. */
    public String sleepRefusal(Rpg g, Actor a) {
        if (cellSlab()) return a == g.player && g.law.inCustody(a) ? null : "This slab is reserved for prisoners.";
        // (0.3.2) an inn's guest bed is the lodger's for the night they paid for
        var inn = g.inns == null ? null : g.inns.ofBed(this);
        if (inn != null) { if (!inn.lodging(a.id, g.state.minutes)) return "A guest bed: ask " + g.nameOf(inn.layout.innkeeper()) + " for one (" + rpg.econ.Inns.ROOM_PRICE + " G)."; }
        else if (owner != 0 && owner != a.id) return "This is " + g.nameOf(owner) + "'s bed.";
        for (Npc n : g.npcs) if (n.alive && n.loaded && (n.hostileToPlayer || n.fightTarget == a.id) && n.distTo(a) < 14) return "You can't sleep with enemies nearby.";
        if (a.cs.busy()) return "You're busy.";
        return null;
    }

    @Override public Result perform(Rpg g, Actor a, String action, long req) {
        String why = reach(g, a, g.cfg.interactRange);
        if (why != null) return Result.no(why);
        if (action.equals("stoke") && kind == Kind.FIREPLACE) return stoke(g, a, req);
        if (action.equals("sleep") && (kind.bed() || kind == Kind.SLAB)) {
            why = sleepRefusal(g, a);
            if (why != null) return Result.no(why);
            if (cellSlab() && a == g.player) { if (g.ui != null) g.ui.fadeThen(g.law::sleepThroughSentence); else g.law.sleepThroughSentence(); return Result.ok(); }
            // sleep until six in the morning (at least an hour): time passes through the catch-up coordinator
            double hour = g.state.hourOfDay();
            double minutes = Math.max(60, ((6 - hour + 24) % 24) * 60);
            g.state.commit(req, "Slept", a.id, id, jurisdiction(), 0, String.format("%.0f minutes in %s", minutes, label()));
            g.advanceTime(minutes, "sleeping");
            if (a == g.player) g.restoreWorld("slept");
            a.hp = a.maxHp(); a.stamina = a.maxStamina();
            if (a == g.player) g.chat.say("You sleep soundly and wake at " + g.state.clockText() + ", rested.");
            return Result.ok();
        }
        return Result.no("You can't do that.");
    }

    @Override public String inspect(Rpg g) { return super.inspect(g) + String.format("\n  %s cells (%d,%d)-(%d,%d) h%d facing %d placed=%s", kind, x0, z0, x1, z1, h, facing, placed); }

    /** (0.3.8) A fire minutes a log keeps going in a grate. */
    public static final float LOG_MINUTES = 90, FUEL_MAX = 360;

    /** (0.3.8) A log from the hands onto the grate (a fire laid and lit if it was cold). */
    public Result stoke(Rpg g, Actor a, long req) {
        if (kind != Kind.FIREPLACE) return Result.no("That's no hearth.");
        if (fuel >= FUEL_MAX - LOG_MINUTES) return Result.no("The fire has all the wood it can take.");
        // (0.3.9, gamefixes: a log put on the DevMansion fire crashed the game) the logs are looked for before any are taken
        if (!a.has(rpg.item.Items.LOGS, 1)) return Result.no(fuel > 0 ? "You have no logs to put on it." : "You need logs to lay a fire.");
        if (a.takeItems(rpg.item.Items.LOGS, 1).isEmpty()) return Result.no("You need logs.");
        boolean lit = fuel <= 0;
        fuel = Math.min(FUEL_MAX, fuel + LOG_MINUTES);
        g.state.burn(rpg.item.Items.LOGS, 1);
        g.state.commit(req, lit ? "FireLit" : "FireStoked", a.id, id, jurisdiction(), 0, String.format("%.0f minutes of fire", fuel));
        if (g.sfx != null) g.sfx.play(lit ? "crackle" : "wood.light", x, floor + 0.4f, z, 0.6f);
        if (a == g.player) g.chat.say(lit ? "You lay a fire in the grate and light it." : "You put another log on the fire.");
        return Result.ok();
    }

    /** (0.3.8) The fire burns down by the game minute. */
    public void burn(float minutes) { if (fuel > 0) fuel = Math.max(0, fuel - minutes); }

    @Override public Map<String, Object> save() { Map<String, Object> m = super.save(); m.put("placed", placed); if (fuel > 0) m.put("fuel", fuel); return m; }
    @Override public void load(Rpg g, Map<String, Object> m) { super.load(g, m); place(g, astratto.util.Json.bool(m, "placed", placed)); fuel = (float) astratto.util.Json.num(m, "fuel", 0); }
}
