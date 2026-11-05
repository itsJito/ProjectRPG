package rpg.econ;

import astratto.util.Json;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import rpg.Ids;
import rpg.Rpg;
import rpg.actor.Actor;
import rpg.ai.Npc;
import rpg.item.ItemDef;
import rpg.item.ItemStack;
import rpg.item.Items;
import rpg.item.Recipes;
import rpg.objects.Container;
import rpg.objects.Door;
import rpg.objects.Furniture;
import rpg.objects.Station;
import rpg.world.Blocks;
import rpg.world.DevMap;

/**
 * (0.3.2, gamefixes: an inn that works, feeds and provides drink and recreation for the townsfolk dynamically and takes
 * part in the economy - one in DevCity and one in DevPalace, fully staffed with named people)
 * <p>
 * An inn is a building with a common room - a bar, tables with benches, a hearth - and a few beds. Three people keep it:
 * <ul>
 *   <li>the <b>Innkeeper</b> keeps the guest register at the desk: lets the guest beds by the night (the player can take
 *       one), buys in the bread the bar sells from the town's market, and sees the beds made of a morning;</li>
 *   <li>the <b>Bartender</b> keeps the bar: it is a shop with real stock and the bartender's own purse - ale, hot cider,
 *       vegetable stew, bread, apples - and it buys produce from the farms (carrots, cabbages, apples) at its price;</li>
 *   <li>the <b>Cook</b> works the hearth: takes carrots and cabbages from the bar's stock and cooks them into stew, mulls
 *       apples into hot cider, and puts what is made back on the bar.</li>
 * </ul>
 * The townsfolk come when their day has a meal or an evening out in it (Schedule), or when they are hungry and have the
 * coin: they buy at the bar, take a seat on a bench, eat or drink, and talk with whoever sits near - news and rumours go
 * round the tables (Rumors). Every coin and every bowl moves through the same ledger as the rest of the world: a bar with
 * no carrots left serves no stew until the farmer brings more.
 */
public final class Inns {
    /** A building: its walls (x0..x1, z0..z1, the walls on the edge), its door, materials, furniture, and where its people stand. */
    public record Layout(String name, String jurisdiction, int x0, int z0, int x1, int z1, int[][] door, int wall, int floor, int partitionZ, int[] partitionGap,
                         DevMap.Piece[] pieces, int[] hearth, float[] barSpot, float[] customerSpot, float[] deskSpot, float[] hearthSpot, int[] larder,
                         int[][] windows, int[][] lamps, long innkeeper, long bartender, long cook) {}

    public static final Layout[] LAYOUTS = {
            // DevCity: east of the square, its door on the west wall onto the paving; common room south, beds north
            new Layout("DevCity Inn", DevMap.ALPHA, 121, 48, 130, 68, new int[][]{{121, 63}, {121, 64}}, Blocks.PLASTER, Blocks.PLANKS, 55, new int[]{125, 126},
                    new DevMap.Piece[]{
                            new DevMap.Piece("COUNTER", 127, 58, 127, 62, 1, 1, Ids.BARTENDER_CITY),
                            new DevMap.Piece("TABLE", 123, 58, 123, 59, 1, 0, Ids.INNKEEPER_CITY), new DevMap.Piece("BENCH", 122, 58, 122, 59, 1, 0, Ids.INNKEEPER_CITY), new DevMap.Piece("BENCH", 124, 58, 124, 59, 1, 0, Ids.INNKEEPER_CITY),
                            new DevMap.Piece("TABLE", 123, 62, 123, 63, 1, 0, Ids.INNKEEPER_CITY), new DevMap.Piece("BENCH", 122, 62, 122, 63, 1, 0, Ids.INNKEEPER_CITY), new DevMap.Piece("BENCH", 124, 62, 124, 63, 1, 0, Ids.INNKEEPER_CITY),
                            new DevMap.Piece("TABLE", 126, 66, 127, 66, 1, 0, Ids.INNKEEPER_CITY), new DevMap.Piece("BENCH", 126, 67, 127, 67, 1, 0, Ids.INNKEEPER_CITY), new DevMap.Piece("BENCH", 126, 65, 127, 65, 1, 0, Ids.INNKEEPER_CITY),
                            new DevMap.Piece("COUNTER", 122, 66, 122, 67, 1, 1, Ids.INNKEEPER_CITY),
                            new DevMap.Piece("BED", 122, 49, 122, 50, 1, 2, 0), new DevMap.Piece("BED", 124, 49, 124, 50, 1, 2, 0),
                            new DevMap.Piece("BED", 127, 49, 127, 50, 1, 2, Ids.BARTENDER_CITY), new DevMap.Piece("BED", 129, 49, 129, 50, 1, 2, Ids.COOK_CITY),
                            new DevMap.Piece("BED", 128, 53, 129, 53, 1, 1, Ids.INNKEEPER_CITY)},
                    new int[]{129, 57}, new float[]{128.5f, 60.5f}, new float[]{126.0f, 60.5f}, new float[]{123.5f, 66.5f}, new float[]{128.5f, 57.5f}, new int[]{129, 64},
                    new int[][]{{130, 59}, {130, 62}, {125, 68}, {121, 58}, {123, 48}, {128, 48}}, new int[][]{{121, 60}, {130, 65}, {130, 51}},
                    Ids.INNKEEPER_CITY, Ids.BARTENDER_CITY, Ids.COOK_CITY),
            // DevPalace: a taberna on the decumanus, its door on the south wall (west of it the stair up to the wall walk);
            // common room south, beds north
            new Layout("DevPalace Taberna", DevMap.PALACE, 136, 54, 145, 69, new int[][]{{140, 69}, {141, 69}}, Blocks.LIMESTONE_BRICKS, Blocks.MARBLE, 60, new int[]{140, 141},
                    new DevMap.Piece[]{
                            new DevMap.Piece("COUNTER", 143, 62, 143, 66, 1, 1, Ids.BARTENDER_PALACE),
                            new DevMap.Piece("TABLE", 138, 63, 139, 63, 1, 0, Ids.INNKEEPER_PALACE), new DevMap.Piece("BENCH", 138, 62, 139, 62, 1, 0, Ids.INNKEEPER_PALACE), new DevMap.Piece("BENCH", 138, 64, 139, 64, 1, 0, Ids.INNKEEPER_PALACE),
                            new DevMap.Piece("TABLE", 138, 67, 139, 67, 1, 0, Ids.INNKEEPER_PALACE), new DevMap.Piece("BENCH", 138, 66, 139, 66, 1, 0, Ids.INNKEEPER_PALACE), new DevMap.Piece("BENCH", 138, 68, 139, 68, 1, 0, Ids.INNKEEPER_PALACE),
                            new DevMap.Piece("TABLE", 140, 65, 140, 66, 1, 0, Ids.INNKEEPER_PALACE), new DevMap.Piece("BENCH", 141, 65, 141, 66, 1, 0, Ids.INNKEEPER_PALACE),
                            new DevMap.Piece("COUNTER", 143, 68, 143, 68, 1, 2, Ids.INNKEEPER_PALACE),
                            new DevMap.Piece("BED", 137, 55, 137, 56, 1, 2, 0), new DevMap.Piece("BED", 138, 55, 138, 56, 1, 2, 0),
                            new DevMap.Piece("BED", 140, 55, 140, 56, 1, 2, Ids.BARTENDER_PALACE), new DevMap.Piece("BED", 143, 55, 143, 56, 1, 2, Ids.COOK_PALACE),
                            new DevMap.Piece("BED", 144, 55, 144, 56, 1, 2, Ids.INNKEEPER_PALACE)},
                    new int[]{144, 61}, new float[]{144.5f, 64.5f}, new float[]{142.0f, 64.5f}, new float[]{143.5f, 67.4f}, new float[]{143.5f, 61.4f}, new int[]{144, 68},
                    new int[][]{{136, 64}, {136, 58}, {145, 58}, {138, 69}, {143, 69}, {139, 54}}, new int[][]{{136, 66}, {145, 66}, {145, 57}},
                    Ids.INNKEEPER_PALACE, Ids.BARTENDER_PALACE, Ids.COOK_PALACE)
    };
    /** What a bed for the night costs; the hours a bar serves; the game minutes a patron sits over a drink or a meal. */
    public static final int ROOM_PRICE = 15;
    public static final double OPEN_HOUR = 7, CLOSE_HOUR = 23.5;

    public static final class Inn {
        public final int index;
        public final Layout layout;
        public Economy.Shop bar;
        public Station hearth;
        public Container larder;
        public Door door;
        public final List<Furniture> guestBeds = new ArrayList<>(), benches = new ArrayList<>(), tables = new ArrayList<>();
        /** Who has a guest bed, until when (game minutes). */
        public final Map<Long, Double> lodgers = new HashMap<>();
        /** Who sits where: seat index -> patron id; the seats themselves (x, z, yaw toward the table). */
        public final Map<Integer, Long> taken = new HashMap<>();
        public final List<float[]> seats = new ArrayList<>();
        /** Ledger tallies, for the report and the tests: meals, drinks, nights let, coin taken. */
        public int meals, drinks, nights, produceBought;
        Inn(int index, Layout layout) { this.index = index; this.layout = layout; }
        public String name() { return layout.name(); }
        public boolean staffed(Rpg g) { return g.actor(layout.bartender()) instanceof Npc b && b.alive; }
        public boolean open(Rpg g) {
            double h = g.state.hourOfDay();
            return h >= OPEN_HOUR && h < CLOSE_HOUR && staffed(g) && g.actor(layout.bartender()) instanceof Npc b && Math.hypot(b.x - layout.barSpot()[0], b.z - layout.barSpot()[1]) < 3;
        }
        public boolean inside(float x, float z) { return x > layout.x0() && x < layout.x1() + 1 && z > layout.z0() && z < layout.z1() + 1; }
        public boolean lodging(long who, double now) { Double until = lodgers.get(who); return until != null && until > now; }
    }

    private final Rpg g;
    public final List<Inn> all = new ArrayList<>();
    public Inns(Rpg g) { this.g = g; }

    /** The building: walls (doorway and windows left), floor, roof, the partition between the common room and the beds, the lamps, the hearth, the furniture's cells. */
    public static void build(astratto.bridge.CommandBuffer b) {
        int f = DevMap.FLOOR;
        for (Layout l : LAYOUTS) {
            for (int x = l.x0(); x <= l.x1(); x++) for (int z = l.z0(); z <= l.z1(); z++) {
                for (int y = f; y <= f + 6; y++) b.blockSet(x, y, z, Blocks.AIR);
                b.blockSet(x, f - 1, z, l.floor());
                boolean edge = x == l.x0() || x == l.x1() || z == l.z0() || z == l.z1();
                if (edge) for (int y = f; y <= f + 3; y++) b.blockSet(x, y, z, (x == l.x0() || x == l.x1()) && (z == l.z0() || z == l.z1()) ? Blocks.BEAM : l.wall());
                b.blockSet(x, f + 4, z, Blocks.ROOF);
            }
            // a ridge down the middle of the roof
            boolean alongZ = l.z1() - l.z0() >= l.x1() - l.x0();
            if (alongZ) for (int z = l.z0(); z <= l.z1(); z++) b.blockSet((l.x0() + l.x1()) / 2, f + 5, z, Blocks.ROOF);
            else for (int x = l.x0(); x <= l.x1(); x++) b.blockSet(x, f + 5, (l.z0() + l.z1()) / 2, Blocks.ROOF);
            // the partition between the common room and the beds, with its doorway
            for (int x = l.x0() + 1; x < l.x1(); x++) {
                if (x == l.partitionGap()[0] || x == l.partitionGap()[1]) continue;
                for (int y = f; y <= f + 3; y++) b.blockSet(x, y, l.partitionZ(), l.wall());
            }
            for (int[] d : l.door()) for (int y = f; y < f + DevMap.DOOR_HEIGHT; y++) b.blockSet(d[0], y, d[1], Blocks.AIR);
            for (int[] w : l.windows()) b.blockSet(w[0], f + 1, w[1], Blocks.GLASS);
            for (int[] lamp : l.lamps()) b.blockSet(lamp[0], f + 2, lamp[1], Blocks.LAMP);
            b.blockSet(l.hearth()[0], f, l.hearth()[1], Blocks.RANGE);
            for (DevMap.Piece p : l.pieces()) {
                if (p.kind().equals("BENCH")) continue;
                int id = switch (p.kind()) { case "BED" -> Blocks.BED; default -> Blocks.TABLE; };
                for (int x = p.x0(); x <= p.x1(); x++) for (int z = p.z0(); z <= p.z1(); z++) b.blockSet(x, f, z, id);
            }
        }
    }

    /** The fixtures (fixed ids from a base, after every older one), the bars as shops, the opening stock. */
    public long init(long nextId) {
        float f = DevMap.FLOOR;
        for (int i = 0; i < LAYOUTS.length; i++) {
            Layout l = LAYOUTS[i];
            Inn inn = new Inn(i, l);
            for (DevMap.Piece p : l.pieces()) {
                // a bed with no owner in the plan is a guest bed: the innkeeper's to let
                boolean guest = p.kind().equals("BED") && p.owner() == 0;
                Furniture fu = g.objects.add(new Furniture(nextId++, Furniture.Kind.valueOf(p.kind()), p.x0(), p.z0(), p.x1(), p.z1(), p.h(), p.facing(), guest ? l.innkeeper() : p.owner(), true));
                g.objects.furniture.add(fu);
                if (guest) inn.guestBeds.add(fu);
                if (fu.kind == Furniture.Kind.BENCH) inn.benches.add(fu);
                if (fu.kind == Furniture.Kind.TABLE) inn.tables.add(fu);
            }
            for (Furniture bench : inn.benches) for (int x = bench.x0; x <= bench.x1; x++) for (int z = bench.z0; z <= bench.z1; z++) {
                // facing the nearest table across it
                Furniture t = null; float bd = 9;
                for (Furniture table : inn.tables) { float d = (float) Math.hypot(table.x - (x + 0.5f), table.z - (z + 0.5f)); if (d < bd) { bd = d; t = table; } }
                float yaw = t == null ? 0 : (float) Math.atan2(-(t.x - (x + 0.5f)), -(t.z - (z + 0.5f)));
                inn.seats.add(new float[]{x + 0.5f, z + 0.5f, yaw});
            }
            inn.hearth = g.objects.add(new Station(nextId++, "range", l.name() + " hearth", Recipes.Station.RANGE, l.hearth()[0] + 0.5f, f + 0.6f, l.hearth()[1] + 0.5f));
            inn.larder = g.objects.add(new Container(nextId++, "chest", l.name() + " larder", "chest", l.larder()[0] + 0.5f, f + 0.4f, l.larder()[1] + 0.5f, 20, l.innkeeper()));
            inn.door = g.objects.add(new Door(nextId++, l.name() + " door", l.door(), false, null, 1, l.innkeeper()));
            inn.door.spawn(g);
            all.add(inn);
        }
        return nextId;
    }

    /** The bars as shops (Economy), with their opening stock and what each buys in. */
    public void shops(Economy econ) {
        for (Inn inn : all) {
            Layout l = inn.layout;
            inn.bar = new Economy.Shop(l.bartender(), l.name() + " bar", l.jurisdiction(), l.customerSpot()[0], l.customerSpot()[1]);
            econ.stockShop(inn.bar, Items.ALE, 12, 16); econ.stockShop(inn.bar, Items.HOT_CIDER, 4, 8); econ.stockShop(inn.bar, Items.VEGETABLE_STEW, 4, 8);
            econ.stockShop(inn.bar, Items.BREAD, 4, 8); econ.stockShop(inn.bar, Items.APPLE, 4, 10);
            econ.stockShop(inn.bar, Items.CARROT, 3, 8); econ.stockShop(inn.bar, Items.CABBAGE, 3, 8);
            // (0.3.4) the brewing: barley and hops bought in from the gardens, bottles for the ale (filled at the water)
            econ.stockShop(inn.bar, Items.BARLEY, 3, 8); econ.stockShop(inn.bar, Items.HOPS, 3, 8);
            econ.stockShop(inn.bar, Items.GLASS_BOTTLE, 6, 14); econ.stockShop(inn.bar, Items.WATER_BOTTLE, 2, 6);
        }
    }

    public Inn of(Npc staff) { for (Inn inn : all) if (inn.layout.innkeeper() == staff.id || inn.layout.bartender() == staff.id || inn.layout.cook() == staff.id) return inn; return null; }
    public Inn inJurisdiction(String jurisdiction) { for (Inn inn : all) if (inn.layout.jurisdiction().equals(jurisdiction)) return inn; return null; }
    public Inn barOf(Economy.Shop shop) { for (Inn inn : all) if (inn.bar == shop) return inn; return null; }
    /** The inn whose guest bed this is, if any. */
    public Inn ofBed(Furniture bed) { for (Inn inn : all) if (inn.guestBeds.contains(bed)) return inn; return null; }

    /** A free seat at an inn (nearest the bar first, so a full room fills from the fire outward), reserved for the patron; or -1. */
    public int takeSeat(Inn inn, Npc n) {
        for (var e : inn.taken.entrySet()) if (e.getValue() == n.id) return e.getKey();
        inn.taken.values().removeIf(id -> !(g.actor(id) instanceof Npc p) || !p.alive || !inn.inside(p.x, p.z) && p.goal != rpg.ai.Brain.Goal.INN);
        int best = -1; float bd = Float.MAX_VALUE;
        for (int i = 0; i < inn.seats.size(); i++) {
            if (inn.taken.containsKey(i)) continue;
            float[] s = inn.seats.get(i);
            float d = (float) Math.hypot(s[0] - inn.layout.barSpot()[0], s[1] - inn.layout.barSpot()[1]) + (float) ((n.id * 7 + i * 13) % 5) * 0.3f;
            if (d < bd) { bd = d; best = i; }
        }
        if (best >= 0) inn.taken.put(best, n.id);
        return best;
    }
    public void leaveSeat(Npc n) { for (Inn inn : all) inn.taken.values().removeIf(id -> id == n.id); }

    /** A bed for the night, paid to the innkeeper (coins from the guest's purse to theirs); until the next noon. */
    public rpg.interact.Result rent(Actor guest, Npc keeper, long req) {
        Inn inn = of(keeper);
        if (inn == null || inn.layout.innkeeper() != keeper.id) return rpg.interact.Result.no("They don't let rooms.");
        if (inn.lodging(guest.id, g.state.minutes)) return rpg.interact.Result.no("You already have a bed here tonight.");
        long busy = inn.lodgers.entrySet().stream().filter(e -> e.getValue() > g.state.minutes).count();
        if (busy >= inn.guestBeds.size()) return rpg.interact.Result.no("Every bed is taken tonight.");
        if (!guest.has(Items.COINS, ROOM_PRICE)) return rpg.interact.Result.no("A bed is " + ROOM_PRICE + " G a night.");
        guest.takeItems(Items.COINS, ROOM_PRICE);
        keeper.store(new ItemStack(Items.COINS, ROOM_PRICE, keeper.id, false, keeper.id));
        double day = Math.floor(g.state.minutes / 1440) * 1440, noon = day + 12 * 60;
        inn.lodgers.put(guest.id, g.state.hourOfDay() < 12 ? noon : noon + 1440);
        inn.nights++;
        g.state.commit(req, "RoomLet", keeper.id, guest.id, inn.layout.jurisdiction(), ROOM_PRICE, inn.name());
        return rpg.interact.Result.ok("A bed is yours until noon. The guest beds are through the back.");
    }

    public void update() {
        for (Inn inn : all) inn.lodgers.values().removeIf(until -> until < g.state.minutes - 60);
    }

    public Map<String, Object> save() {
        Map<String, Object> root = new HashMap<>();
        List<Object> list = new ArrayList<>();
        for (Inn inn : all) {
            Map<String, Object> m = new HashMap<>();
            m.put("meals", inn.meals); m.put("drinks", inn.drinks); m.put("nights", inn.nights); m.put("produce", inn.produceBought);
            Map<String, Object> lodgers = new HashMap<>(); for (var e : inn.lodgers.entrySet()) lodgers.put(Long.toString(e.getKey()), e.getValue());
            m.put("lodgers", lodgers);
            m.put("bar", Economy.saveStock(inn.bar));
            list.add(m);
        }
        root.put("inns", list);
        return root;
    }
    @SuppressWarnings("unchecked")
    public void load(Map<String, Object> root) {
        if (root == null) return;
        List<Object> list = Json.list(root, "inns");
        for (int i = 0; i < Math.min(list.size(), all.size()); i++) if (list.get(i) instanceof Map<?, ?> raw) {
            Map<String, Object> m = (Map<String, Object>) raw; Inn inn = all.get(i);
            inn.meals = (int) Json.num(m, "meals", 0); inn.drinks = (int) Json.num(m, "drinks", 0); inn.nights = (int) Json.num(m, "nights", 0); inn.produceBought = (int) Json.num(m, "produce", 0);
            inn.lodgers.clear();
            if (Json.obj(m, "lodgers") != null) for (var e : Json.obj(m, "lodgers").entrySet()) if (e.getValue() instanceof Number t) inn.lodgers.put(Long.parseLong(e.getKey()), t.doubleValue());
            if (m.get("bar") != null) Economy.loadStock(inn.bar, m.get("bar"));
            inn.taken.clear();
        }
    }

    /** The fare an inn offers a patron: something to eat when hungry, else a drink; null when the bar has neither in stock. */
    public ItemDef fareFor(Inn inn, Npc n) {
        boolean hungry = n.hunger > 0.35f;
        ItemDef[] order = hungry ? new ItemDef[]{Items.VEGETABLE_STEW, Items.BREAD, Items.APPLE, Items.HOT_CIDER, Items.ALE}
                : (n.id % 3 == 0 ? new ItemDef[]{Items.HOT_CIDER, Items.ALE, Items.APPLE} : new ItemDef[]{Items.ALE, Items.HOT_CIDER, Items.APPLE});
        for (ItemDef d : order) if (inn.bar.stock.count(d) > 0 && n.has(Items.COINS, g.econ.buyPrice(inn.bar, d))) return d;
        return null;
    }
    /** Whether a thing the bar sells is a drink (for the tallies). */
    public static boolean drink(ItemDef d) { return d == Items.ALE || d == Items.HOT_CIDER; }
}
