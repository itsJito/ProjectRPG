package rpg.world;

import astratto.util.Json;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import rpg.Ids;
import rpg.Rpg;
import rpg.actor.Actor;
import rpg.interact.Option;
import rpg.interact.Result;
import rpg.interact.Work;
import rpg.interact.WorldObject;
import rpg.item.ItemDef;
import rpg.item.ItemStack;
import rpg.item.Items;
import rpg.skill.Skill;

/**
 * (0.3.2, gamefixes: apples, carrots and cabbage that can be grown and harvested) the kitchen gardens and the orchards.
 * <p>
 * A garden is a fenced field of tilled plots in rows. A plot is sown with seed by hand (a short piece of work, the seed
 * taken from the sower's pack) and grows through four stages over the game hours - a little faster in wet weather, not at
 * all in a frost of snow - until it is ripe; harvested it yields its produce and some of its seed back, and lies empty
 * until it is sown again. Left ripe too long it bolts and goes to seed: then it gives seed and nothing to eat. An apple
 * tree carries a crop of apples that comes on a fruit at a time through the day, picked one by one.
 * <p>
 * Everything is owned as the land is: a garden's rows and an orchard's trees are their keeper's, and taking from them is
 * theft like taking from a chest (the law sees it the same way); one row in each garden is the town's common plots,
 * anyone's to sow and reap. The keepers work them (Jobs: the crop farmer sows and harvests, the orchard keeper picks) and
 * sell what they grow to the inns and markets; the player can do the same with seed bought or kept.
 */
public final class Farming {
    public enum Crop {
        CARROT("Carrots", Items.CARROT, Items.CARROT_SEEDS, 9 * 60, 2, 4, 1, 22),
        // (0.3.7, gamefixes: the Harvesting overhaul) what can be harvested, by level: apples 1 (APPLE_LEVEL), potatoes 2,
        // onions 5, cabbages 7, tomatoes 10, sweetcorn 20
        POTATO("Potatoes", Items.POTATO, Items.POTATO_SEEDS, 10 * 60, 2, 4, 2, 24),
        ONION("Onions", Items.ONION, Items.ONION_SEEDS, 11 * 60, 2, 3, 5, 30),
        CABBAGE("Cabbages", Items.CABBAGE, Items.CABBAGE_SEEDS, 13 * 60, 1, 2, 7, 38),
        TOMATO("Tomatoes", Items.TOMATO, Items.TOMATO_SEEDS, 14 * 60, 2, 4, 10, 46),
        SWEETCORN("Sweetcorn", Items.SWEETCORN, Items.SWEETCORN_SEEDS, 17 * 60, 1, 3, 20, 70),
        /** (0.3.4, gamefixes: ale) the brewer's crops: barley and hops, sold to the inns for their ale. */
        BARLEY("Barley", Items.BARLEY, Items.BARLEY_SEEDS, 11 * 60, 2, 4, 3, 26),
        HOPS("Hops", Items.HOPS, Items.HOP_SEEDS, 12 * 60, 2, 3, 8, 30),
        /** (0.3.5, gamefixes: the textile industry) cotton, grown by the outworkers for the spinning wheel. */
        COTTON("Cotton", Items.COTTON, Items.COTTON_SEEDS, 14 * 60, 2, 4, 10, 36);
        public final String title;
        public final ItemDef produce, seed;
        /** Game minutes from sowing to ripe in ordinary weather; produce yielded (min..max); Harvesting level; XP. */
        public final float growMinutes;
        public final int yieldMin, yieldMax, level, xp;
        Crop(String title, ItemDef produce, ItemDef seed, float growMinutes, int yieldMin, int yieldMax, int level, int xp) {
            this.title = title; this.produce = produce; this.seed = seed; this.growMinutes = growMinutes; this.yieldMin = yieldMin; this.yieldMax = yieldMax; this.level = level; this.xp = xp;
        }
    }

    /** A garden: its fence (x0..x1, z0..z1, the fence on the edge), its rows of plots, its gate, whose it is, its common row. */
    public record Field(String name, String jurisdiction, long owner, int x0, int z0, int x1, int z1, int[] rows, int commonRow, int[][] gate, Crop[][] rowCrops) {
        /** The crop a plot of a row is meant to carry: the row's crops in turn along it, a bed of each. */
        public Crop cropAt(int row, int x) { Crop[] bed = rowCrops[row]; return bed[Math.floorMod((x - x0 - 1) / BED_PLOTS, bed.length)]; }
    }
    /** How many plots in a row one bed of a crop takes before the next crop's bed begins. */
    public static final int BED_PLOTS = 3;
    /** The Harvesting level and XP of picking an apple from a tree. */
    public static final int APPLE_LEVEL = 1, APPLE_XP = 9;
    /** An orchard tree: its trunk cell and whose it is. */
    public record Orchard(String name, long owner, int[][] trees) {}

    /** DevCity's kitchen garden in the farm paddock (Tobin's), and its orchard to the east (Hedda's); DevPalace's by the east wall (Rufus's, Valeria's trees). */
    public static final Field[] FIELDS = {
            new Field("DevCity kitchen garden", DevMap.ALPHA, Ids.CROP_FARMER_CITY, 88, 104, 99, 112, new int[]{106, 108, 110}, 110, new int[][]{{99, 107}, {99, 108}},
                    new Crop[][]{{Crop.BARLEY}, {Crop.CABBAGE, Crop.POTATO, Crop.ONION}, {Crop.CARROT, Crop.TOMATO, Crop.SWEETCORN}}),
            new Field("DevPalace garden", DevMap.PALACE, Ids.CROP_FARMER_PALACE, 176, 55, 186, 63, new int[]{57, 59, 61}, 61, new int[][]{{176, 58}, {176, 59}},
                    new Crop[][]{{Crop.HOPS}, {Crop.CARROT, Crop.ONION, Crop.POTATO}, {Crop.CABBAGE, Crop.SWEETCORN, Crop.TOMATO}}),
            // (0.3.5) the outworkers' cotton field on the dunes by their cottage (its plots made after every older fixture)
            new Field("the cotton field", DevMap.WILDS, rpg.Ids.CARDER, 66, 188, 76, 195, new int[]{190, 192, 194}, -1, new int[][]{{71, 188}},
                    new Crop[][]{{Crop.COTTON}, {Crop.COTTON}, {Crop.COTTON}})
    };
    /** How many of the fields were made with the old fixtures (their plots' ids come first); the rest come with Guild Row. */
    public static final int EARLY_FIELDS = 2;
    public static final Orchard[] ORCHARDS = {
            new Orchard("DevCity orchard", Ids.ORCHARD_KEEPER_CITY, new int[][]{{113, 105}, {118, 105}, {113, 110}, {118, 110}}),
            new Orchard("DevPalace orchard", Ids.ORCHARD_KEEPER_PALACE, new int[][]{{178, 66}, {182, 66}, {186, 66}})
    };
    /**
     * The growers' beds: DevCity's two in the town's houses, DevPalace's two lodging at the taberna (the north room, under
     * the guest beds' row).
     */
    public static final DevMap.Piece[] GROWER_BEDS = {
            new DevMap.Piece("BED", 87, 87, 87, 88, 1, 2, Ids.CROP_FARMER_CITY), new DevMap.Piece("BED", 101, 87, 101, 88, 1, 2, Ids.ORCHARD_KEEPER_CITY),
            new DevMap.Piece("BED", 137, 58, 138, 58, 1, 3, Ids.CROP_FARMER_PALACE), new DevMap.Piece("BED", 143, 58, 144, 58, 1, 1, Ids.ORCHARD_KEEPER_PALACE)};
    public static final int TREE_HEIGHT = 3, APPLES_MAX = 8;
    /** Game minutes a ripe plot holds before it bolts; between one apple and the next on a tree. */
    public static final float BOLT_MINUTES = 16 * 60, FRUIT_MINUTES = 55;

    // ------------------------------------------------------------------ the plots and the trees
    public static final class Plot extends WorldObject {
        public final int field, bx, bz;
        public final boolean common;
        public Crop crop;
        /** 0 sown .. 1 ripe; past ripe, the game minutes it has stood ripe. */
        public float growth, ripeFor;
        public long sower;
        Plot(long id, int field, int bx, int bz, boolean common, long owner) {
            super(id, "crop_plot", "Tilled plot", bx + 0.5f, DevMap.FLOOR + 0.15f, bz + 0.5f);
            this.field = field; this.bx = bx; this.bz = bz; this.common = common; this.owner = common ? 0 : owner;
        }
        public boolean ripe() { return crop != null && growth >= 1 && ripeFor < BOLT_MINUTES; }
        public boolean bolted() { return crop != null && growth >= 1 && ripeFor >= BOLT_MINUTES; }
        /** 0 bare, 1 seedling, 2 half grown, 3 nearly there, 4 ripe, 5 gone to seed: what is drawn. */
        public int stage() { return crop == null ? 0 : bolted() ? 5 : growth >= 1 ? 4 : growth >= 0.6f ? 3 : growth >= 0.25f ? 2 : 1; }
        @Override public float focusRadius() { return 0.55f; }
        @Override public String label() {
            if (crop == null) return common ? "Common plot (empty)" : "Tilled plot (empty)";
            String what = crop.title;
            return bolted() ? what + " (gone to seed)" : ripe() ? what + " (ripe)" : String.format("%s (growing, %d%%)", what, Math.round(growth * 100));
        }
        @Override public List<Option> options(Rpg g, Actor a) {
            List<Option> o = new ArrayList<>();
            float r = g.cfg.interactRange;
            boolean mine = owner == 0 || owner == a.id;
            if (crop == null) {
                for (Crop c : Crop.values()) {
                    boolean has = a.has(c.seed, 1);
                    if (has || mine) o.add(Option.when(has && mine, "sow_" + c.name().toLowerCase(), "Sow " + c.seed.name.toLowerCase(), 8, !has ? "You have no " + c.seed.name.toLowerCase() + "." : "This plot is " + g.nameOf(owner) + "'s.", r));
                }
            } else if (crop != null && growth >= 1) {
                String verb = bolted() ? "Gather the seed" : "Harvest " + crop.title.toLowerCase();
                o.add(Option.when(a.skills.level(Skill.HARVESTING) >= crop.level, "harvest", verb + (mine ? "" : " (not yours)"), 9, "You need Harvesting " + crop.level + ".", r));
            }
            return o;
        }
        @Override public Result perform(Rpg g, Actor a, String action, long req) {
            String why = reach(g, a, g.cfg.interactRange);
            if (why != null) return Result.no(why);
            if (action.startsWith("sow_")) {
                Crop c = Crop.valueOf(action.substring(4).toUpperCase());
                if (crop != null) return Result.no("Something is already growing there.");
                if (owner != 0 && owner != a.id) return Result.no("This plot is " + g.nameOf(owner) + "'s.");
                if (!a.has(c.seed, 1)) return Result.no("You have no " + c.seed.name.toLowerCase() + ".");
                g.interact.begin(new Work(a, this, action, "Sowing", 1.6f, false, g.time, () -> sow(g, a, c, req)));
                return Result.started();
            }
            if (action.equals("harvest")) {
                if (crop == null || growth < 1) return Result.no("Nothing here is ready.");
                if (a.skills.level(Skill.HARVESTING) < crop.level) return Result.no("You need Harvesting " + crop.level + ".");
                g.interact.begin(new Work(a, this, action, "Harvesting", 2.2f, false, g.time, () -> harvest(g, a, req)));
                return Result.started();
            }
            return Result.no("You cannot do that.");
        }
        /** The seed goes in (the work done): one seed from the pack, the plot sown. */
        public Result sow(Rpg g, Actor a, Crop c, long req) {
            if (crop != null) return Result.no("Something is already growing there.");
            if (a.takeItems(c.seed, 1).isEmpty()) return Result.no("You have no " + c.seed.name.toLowerCase() + ".");
            g.state.burn(c.seed, 1);
            crop = c; growth = 0; ripeFor = 0; sower = a.id;
            g.farming.changed(field); g.farming.sownCount++;
            var ev = g.state.commit(req, "CropSown", a.id, id, jurisdiction(), 0, c.title.toLowerCase());
            g.prog.award(a, Skill.HARVESTING, 4, "sowed " + c.title.toLowerCase(), ev.id());
            if (a == g.player) g.chat.say("You sow the " + c.seed.name.toLowerCase() + ".");
            return Result.ok();
        }
        /** Taken up (the work done): its produce and some seed into the pack; someone else's is stolen. */
        public Result harvest(Rpg g, Actor a, long req) {
            if (crop == null || growth < 1) return Result.no("Nothing here is ready.");
            Crop c = crop;
            boolean bolted = bolted();
            long roll = id * 31 + (long) (g.state.minutes / 7);
            int food = bolted ? 0 : c.yieldMin + (int) (g.roll(roll, 1) * (c.yieldMax - c.yieldMin + 1));
            int seed = bolted ? 2 + (int) (g.roll(roll, 2) * 2) : 1 + (g.roll(roll, 3) < (c == Crop.CARROT ? 0.55 : 0.4) ? 1 : 0);
            long was = owner;
            crop = null; growth = 0; ripeFor = 0;
            g.farming.changed(field); g.farming.harvestCount++;
            var ev = g.state.commit(req, "CropHarvested", a.id, id, jurisdiction(), food, c.title.toLowerCase() + (bolted ? " (gone to seed)" : ""));
            if (food > 0) { ItemStack s = new ItemStack(c.produce, food, was, false, a.id); g.state.mint(c.produce, food); if (!a.store(s)) g.objects.drop(s, a.x, a.y + 0.05f, a.z); if (was != 0 && was != a.id) g.law.theft(a, was, s, this, ev.id()); }
            if (seed > 0) { ItemStack s = new ItemStack(c.seed, seed, was, false, a.id); g.state.mint(c.seed, seed); if (!a.store(s)) g.objects.drop(s, a.x, a.y + 0.05f, a.z); }
            if (food > 0) g.prog.award(a, Skill.HARVESTING, c.xp * (0.6 + 0.2 * food), "harvested " + c.title.toLowerCase(), ev.id());
            if (a == g.player) g.chat.say(bolted ? "It has gone to seed: you gather " + seed + " " + c.seed.name.toLowerCase() + "." : "You harvest " + food + " " + c.produce.name.toLowerCase() + (food == 1 ? "" : "s") + " and " + seed + " " + c.seed.name.toLowerCase() + ".");
            return Result.ok();
        }
        @Override public Map<String, Object> save() { Map<String, Object> m = super.save(); m.put("crop", crop == null ? "" : crop.name()); m.put("growth", (double) growth); m.put("ripeFor", (double) ripeFor); m.put("sower", sower); return m; }
        @Override public void load(Rpg g, Map<String, Object> m) {
            super.load(g, m);
            String c = Json.str(m, "crop", ""); crop = c.isEmpty() ? null : Crop.valueOf(c);
            growth = (float) Json.num(m, "growth", 0); ripeFor = (float) Json.num(m, "ripeFor", 0); sower = Json.lng(m, "sower", 0);
        }
    }

    public static final class AppleTree extends WorldObject {
        public final int bx, bz, orchard;
        public int apples;
        /** Game minutes toward the next apple. */
        public float toNext;
        AppleTree(long id, int orchard, int bx, int bz, long owner) {
            super(id, "apple_tree", "Apple tree", bx + 0.5f, DevMap.FLOOR + 1.6f, bz + 0.5f);
            this.orchard = orchard; this.bx = bx; this.bz = bz; this.owner = owner; this.apples = APPLES_MAX / 2 + (int) (Math.abs(id) % 3);
        }
        @Override public float focusRadius() { return 1.2f; }
        @Override public String label() { return apples == 0 ? "Apple tree (bare)" : "Apple tree (" + apples + " apple" + (apples == 1 ? "" : "s") + ")"; }
        @Override public List<Option> options(Rpg g, Actor a) {
            boolean mine = owner == 0 || owner == a.id;
            boolean able = a.skills.level(Skill.HARVESTING) >= APPLE_LEVEL;
            return List.of(Option.when(apples > 0 && able, "pick", "Pick an apple" + (mine ? "" : " (not yours)"), 9, able ? "There are no apples left on it." : "You need Harvesting " + APPLE_LEVEL + ".", g.cfg.interactRange + 0.6f));
        }
        @Override public Result perform(Rpg g, Actor a, String action, long req) {
            String why = reach(g, a, g.cfg.interactRange + 0.6f);
            if (why != null) return Result.no(why);
            if (!action.equals("pick")) return Result.no("You cannot do that.");
            if (apples <= 0) return Result.no("There are no apples left on it.");
            g.interact.begin(new Work(a, this, action, "Picking", 1.3f, false, g.time, () -> pick(g, a, req)));
            return Result.started();
        }
        public Result pick(Rpg g, Actor a, long req) {
            if (apples <= 0) return Result.no("There are no apples left on it.");
            apples--;
            g.farming.treeChanged(); g.farming.pickedCount++;
            ItemStack s = new ItemStack(Items.APPLE, 1, owner, false, a.id);
            g.state.mint(Items.APPLE, 1);
            var ev = g.state.commit(req, "ApplePicked", a.id, id, jurisdiction(), 1, name);
            if (!a.store(s)) g.objects.drop(s, a.x, a.y + 0.05f, a.z);
            if (owner != 0 && owner != a.id) g.law.theft(a, owner, s, this, ev.id());
            g.prog.award(a, Skill.HARVESTING, APPLE_XP, "picked an apple", ev.id());
            if (a == g.player) g.chat.say("You pick an apple.");
            return Result.ok();
        }
        @Override public Map<String, Object> save() { Map<String, Object> m = super.save(); m.put("apples", apples); m.put("toNext", (double) toNext); return m; }
        @Override public void load(Rpg g, Map<String, Object> m) { super.load(g, m); apples = (int) Json.num(m, "apples", apples); toNext = (float) Json.num(m, "toNext", 0); }
    }

    // ------------------------------------------------------------------ the system
    private final Rpg g;
    public final List<Plot> plots = new ArrayList<>();
    public final List<AppleTree> trees = new ArrayList<>();
    /** Per field, a version the renderer rebuilds its mesh on; and the trees' (their apples). */
    public final int[] versions = new int[FIELDS.length];
    public int treeVersion;
    /** Tallies for the reports and tests: plots sown, harvested, apples picked. */
    public int sownCount, harvestCount, pickedCount;
    private double lastMinutes = -1;

    public Farming(Rpg g) { this.g = g; }

    /** The plots and the trees, made once with fixed ids from a base (after every older fixture). */
    public long build(long nextId) {
        nextId = plots(nextId, 0, EARLY_FIELDS);
        for (int o = 0; o < ORCHARDS.length; o++) for (int[] t : ORCHARDS[o].trees()) trees.add(g.objects.add(new AppleTree(nextId++, o, t[0], t[1], ORCHARDS[o].owner())));
        for (DevMap.Piece p : GROWER_BEDS) g.objects.furniture.add(g.objects.add(new rpg.objects.Furniture(nextId++, rpg.objects.Furniture.Kind.BED, p.x0(), p.z0(), p.x1(), p.z1(), 1, p.facing(), p.owner(), true)));
        return nextId;
    }
    /** (0.3.5) The later fields' plots (the cotton field), after Guild Row's fixtures. */
    public long buildLate(long nextId) { return plots(nextId, EARLY_FIELDS, FIELDS.length); }
    private long plots(long nextId, int from, int to) {
        for (int f = from; f < to; f++) {
            Field field = FIELDS[f];
            for (int row = 0; row < field.rows().length; row++) {
                int z = field.rows()[row];
                for (int x = field.x0() + 1; x <= field.x1() - 1; x++) {
                    Plot p = new Plot(nextId++, f, x, z, z == field.commonRow(), field.owner());
                    // the keeper's rows start the game sown with their row's crop, a little apart in growth
                    if (z != field.commonRow() || x % 3 == 0) { p.crop = field.cropAt(row, x); p.growth = ((x * 7 + z * 3) % 10) / 10f; p.sower = field.owner(); }
                    plots.add(g.objects.add(p));
                }
            }
        }
        return nextId;
    }

    public void changed(int field) { if (field >= 0 && field < versions.length) versions[field]++; }
    public void treeChanged() { treeVersion++; }

    /** Growth, by the game minute: a wet day hastens it, snow on the ground halts it; ripe plots count toward bolting; apples come on. */
    public void update() {
        double now = g.state.minutes;
        if (lastMinutes < 0) { lastMinutes = now; return; }
        float dm = (float) Math.min(240, now - lastMinutes);
        if (dm < 1) return;
        lastMinutes = now;
        var weather = g.timeSystem.weather;
        float pace = weather.snowCover > 0.3f ? 0 : 0.85f + 0.35f * Math.min(1, weather.wetness);
        for (Plot p : plots) {
            if (p.crop == null) continue;
            int before = p.stage();
            if (p.growth < 1) p.growth = Math.min(1, p.growth + dm * pace / p.crop.growMinutes);
            else p.ripeFor += dm;
            if (p.stage() != before) changed(p.field);
        }
        // apples come on by day (a tree fruits in the light); night holds them
        double hour = g.state.hourOfDay();
        boolean day = hour >= 6 && hour < 20;
        for (AppleTree t : trees) {
            if (t.apples >= APPLES_MAX || !day) continue;
            t.toNext += dm;
            while (t.toNext >= FRUIT_MINUTES && t.apples < APPLES_MAX) { t.toNext -= FRUIT_MINUTES; t.apples++; treeChanged(); }
        }
    }

    /** The nearest ripe plot of an owner's (or a common one), or null. */
    public Plot ripeFor(long owner, float x, float z) {
        Plot best = null; float bd = Float.MAX_VALUE;
        for (Plot p : plots) if (p.crop != null && p.growth >= 1 && (p.owner == owner)) { float d = (float) Math.hypot(p.x - x, p.z - z); if (d < bd) { bd = d; best = p; } }
        return best;
    }
    /** The nearest empty plot of an owner's, or null. */
    public Plot emptyFor(long owner, float x, float z) {
        Plot best = null; float bd = Float.MAX_VALUE;
        for (Plot p : plots) if (p.crop == null && p.owner == owner) { float d = (float) Math.hypot(p.x - x, p.z - z); if (d < bd) { bd = d; best = p; } }
        return best;
    }
    /** The crop a keeper's empty plot is meant to carry (its row's), for what seed to put in. */
    public Crop rowCrop(Plot p) {
        Field f = FIELDS[p.field];
        for (int i = 0; i < f.rows().length; i++) if (f.rows()[i] == p.bz) return f.cropAt(i, p.bx);
        return Crop.CARROT;
    }
    /** The owner's tree with the most apples on it, or null if none has any worth the walk. */
    public AppleTree pickable(long owner, int atLeast) {
        AppleTree best = null;
        for (AppleTree t : trees) if (t.owner == owner && t.apples >= atLeast && (best == null || t.apples > best.apples)) best = t;
        return best;
    }

    /** The blocks: tilled soil in the plots' cells, paths between the rows, the fence round each garden (its gate left open), the apple trees. */
    public static void build(astratto.bridge.CommandBuffer b) {
        int f = DevMap.FLOOR;
        for (Field field : FIELDS) {
            for (int x = field.x0(); x <= field.x1(); x++) for (int z = field.z0(); z <= field.z1(); z++) {
                for (int y = f; y < f + 3; y++) b.blockSet(x, y, z, Blocks.AIR);
                boolean edge = x == field.x0() || x == field.x1() || z == field.z0() || z == field.z1();
                boolean row = false;
                for (int r : field.rows()) row |= z == r;
                b.blockSet(x, f - 1, z, edge ? Blocks.GRASS : row ? Blocks.MUD : Blocks.DIRT);
                if (edge) b.blockSet(x, f, z, Blocks.FENCE);
            }
            for (int[] gate : field.gate()) b.blockSet(gate[0], f, gate[1], Blocks.AIR);
        }
        for (DevMap.Piece p : GROWER_BEDS) for (int x = p.x0(); x <= p.x1(); x++) for (int z = p.z0(); z <= p.z1(); z++) b.blockSet(x, f, z, Blocks.BED);
        for (Orchard o : ORCHARDS) for (int[] t : o.trees()) {
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) for (int y = f; y < f + TREE_HEIGHT + 3; y++) b.blockSet(t[0] + dx, y, t[1] + dz, Blocks.AIR);
            for (int y = 0; y < TREE_HEIGHT; y++) b.blockSet(t[0], f + y, t[1], Blocks.OAK_LOG);
            // a low, round crown: an orchard tree is kept pruned within reach
            for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) for (int dy = 0; dy <= 2; dy++) {
                int ax = Math.abs(dx), az = Math.abs(dz);
                if (ax + az + dy > 3 || ax == 2 && az == 2) continue;
                int y = f + TREE_HEIGHT - 1 + dy;
                if (dx == 0 && dz == 0 && dy == 0) continue;
                b.blockSet(t[0] + dx, y, t[1] + dz, Blocks.OAK_LEAVES);
            }
        }
    }

    public Map<String, Object> save() { Map<String, Object> m = new HashMap<>(); m.put("last", lastMinutes); return m; }
    public void load(Map<String, Object> m) { lastMinutes = m == null ? -1 : Json.num(m, "last", -1); for (int i = 0; i < versions.length; i++) versions[i]++; treeVersion++; }
}
