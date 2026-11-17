package rpg.econ;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import rpg.Ids;
import rpg.Rpg;
import rpg.actor.Actor;
import rpg.ai.Npc;
import rpg.ai.Profession;
import rpg.interact.Result;
import rpg.item.ItemDef;
import rpg.item.Items;
import rpg.item.Recipes;
import rpg.objects.Container;
import rpg.objects.Door;
import rpg.objects.Furniture;
import rpg.objects.Station;
import rpg.skill.Skill;
import rpg.world.Blocks;
import rpg.world.DevMap;

/**
 * (0.3.5, gamefixes: guilds per industry) the seven guilds and their halls on Guild Row, the seafront street along the
 * beach. Each guild has its trade's skills, a guildmaster who keeps its hall and its store, and its members: every NPC
 * whose calling is of its trade belongs to it by that calling, and the player may join when one of its skills is high
 * enough (and the dues are paid). A hall helps its members' output: work at the hall's own stations goes a quarter
 * quicker for a member, and the guild store pays its members more for the trade's goods and sells them its stock for
 * less. The Row also holds the Armory, which sells what the smiths and the armourers make, and the outworkers' cottage,
 * where the guild's carder and spinner live and work beside their cotton field.
 */
public final class Guilds {
    public enum Kind {
        METALLISTS("Metallists' Guild", "ore extraction, refining and metalworking", 20, Skill.MINING, Skill.SMITHING),
        FORESTERS("Foresters' Guild", "timber, fuel, bows and arrows", 20, Skill.WOODCUTTING, Skill.FIREMAKING),
        ARTISANS("Artisans' Guild", "jewellery, textiles, furniture and masonry", 20, Skill.CRAFTING, Skill.CONSTRUCTION),
        FARMERS("Farmers' Guild", "cultivation, herbs and medicinal ingredients", 20, Skill.HARVESTING),
        PROVISIONERS("Provisioners' Guild", "food: catching, hunting and preparing it", 20, Skill.FISHING, Skill.COOKING),
        ARCANISTS("Arcanists' Guild", "magic, faith, magical research and enchantment", 25, Skill.MAGIC, Skill.FAITH),
        MARINERS("Mariners' Guild", "fishing the sea, navigation and maritime commerce", 30, Skill.FISHING);
        public final String title, trade;
        public final int joinLevel;
        public final Skill[] skills;
        Kind(String title, String trade, int joinLevel, Skill... skills) { this.title = title; this.trade = trade; this.joinLevel = joinLevel; this.skills = skills; }
    }

    /** A building on the Row: its walls (inclusive), its door's x (two cells, in the north wall), its stations, beds and who lives there. */
    public record Hall(String name, Kind guild, int x0, int x1, int wall, long master, long[] residents, Object[][] stations, int[][] beds) {
        public boolean inside(float x, float z) { return x > x0 && x < x1 + 1 && z > Z0 && z < Z1 + 1; }
        public int doorX() { return (x0 + x1) / 2; }
        /** Where the keeper stands: inside the door, at the counter's end. */
        public float[] post() { return new float[]{doorX() + 1.5f, Z0 + 2.5f}; }
    }
    public static final int Z0 = 189, Z1 = 196;
    public static final Hall[] HALLS = {
            new Hall("the Metallists' Guild hall", Kind.METALLISTS, 4, 16, Blocks.STONE_BRICKS, Ids.GUILD_METALLISTS, new long[]{Ids.GUILD_METALLISTS, Ids.SMELTER},
                    new Object[][]{{Recipes.Station.FURNACE, 6, 195}, {Recipes.Station.ANVIL, 9, 195}, {Recipes.Station.FURNACE, 12, 195}}, new int[][]{{5, 190}, {15, 190}}),
            new Hall("the Armory", null, 19, 27, Blocks.BRICK, Ids.ARMORY, new long[]{Ids.ARMORY}, new Object[][]{}, new int[][]{{26, 194}}),
            new Hall("the Foresters' Guild hall", Kind.FORESTERS, 30, 40, Blocks.STONE_BRICKS, Ids.GUILD_FORESTERS, new long[]{Ids.GUILD_FORESTERS},
                    new Object[][]{{Recipes.Station.SAWBENCH, 32, 195}, {Recipes.Station.CRAFT_TABLE, 36, 195}}, new int[][]{{39, 190}}),
            new Hall("the outworkers' cottage", null, 78, 85, Blocks.PLASTER, Ids.CARDER, new long[]{Ids.CARDER, Ids.SPINNER},
                    new Object[][]{{Recipes.Station.CARDING_BENCH, 79, 195}, {Recipes.Station.SPINNING_WHEEL, 83, 195}}, new int[][]{{79, 190}, {84, 190}}),
            new Hall("the Artisans' Guild workshop", Kind.ARTISANS, 90, 102, Blocks.STONE_BRICKS, Ids.GUILD_ARTISANS, new long[]{Ids.GUILD_ARTISANS, Ids.FULLER, Ids.DYER},
                    new Object[][]{{Recipes.Station.LOOM, 92, 195}, {Recipes.Station.FULLING_TUB, 95, 195}, {Recipes.Station.DYE_VAT, 98, 195}, {Recipes.Station.CRAFT_TABLE, 101, 194}},
                    new int[][]{{91, 190}, {93, 190}, {99, 190}}),
            new Hall("the Farmers' Guild hall", Kind.FARMERS, 105, 113, Blocks.BRICK, Ids.GUILD_FARMERS, new long[]{Ids.GUILD_FARMERS},
                    new Object[][]{{Recipes.Station.CRAFT_TABLE, 107, 195}}, new int[][]{{112, 190}}),
            new Hall("the Provisioners' Guild hall", Kind.PROVISIONERS, 116, 124, Blocks.BRICK, Ids.GUILD_PROVISIONERS, new long[]{Ids.GUILD_PROVISIONERS},
                    new Object[][]{{Recipes.Station.RANGE, 118, 195}}, new int[][]{{123, 190}}),
            new Hall("the Mariners' Guild hall", Kind.MARINERS, 133, 143, Blocks.STONE_BRICKS, Ids.GUILD_MARINERS, new long[]{Ids.GUILD_MARINERS, Ids.COASTAL_FISHER},
                    new Object[][]{}, new int[][]{{134, 190}, {142, 190}}),
            new Hall("the Arcanists' Guild hall", Kind.ARCANISTS, 160, 170, Blocks.STONE_BRICKS, Ids.GUILD_ARCANISTS, new long[]{Ids.GUILD_ARCANISTS},
                    new Object[][]{{Recipes.Station.CRAFT_TABLE, 165, 195}}, new int[][]{{169, 190}})};

    /**
     * (0.4.7, gamefixes: all seven guild halls on ShadowVeil) a guild's seat on the map: its hall (a structure whose data
     * names its guild), whether it is the guild's headquarters or a chapter, and who keeps it - the guildmaster at a
     * headquarters, the chapter's head at a chapter. Read from the architecture (no hall is placed in code).
     */
    public record Seat(Kind guild, String structure, String name, boolean hq, long keeper) { }
    public final List<Seat> seats = new ArrayList<>();
    /** Where a chapter's store is filed (after the Row's halls). */
    static final int CHAPTER_STORES = 100;
    /** (0.4.7) How far (m) a member will carry their goods to a seat's store rather than their town's market. */
    static final double SEAT_TRADE_REACH = rpg.Tuning.f("guilds.seatReach", 260f);

    /** What joining costs (G), and how much quicker a member works at their own hall's stations. */
    public static final int DUES = 25;
    public static final float HALL_SPEED = 0.75f, MEMBER_SELL = 1.2f, MEMBER_BUY = 0.9f;

    private final Rpg g;
    /** The guilds the player has joined. */
    public final Set<Kind> joined = EnumSet.noneOf(Kind.class);
    /** Each hall's store (the Armory's too), by hall index; and its door. */
    public final Map<Integer, Economy.Shop> stores = new HashMap<>();
    public final List<Door> doors = new ArrayList<>();
    public final List<Station> stations = new ArrayList<>();
    public Guilds(Rpg g) { this.g = g; }

    // ------------------------------------------------------------------ the buildings
    /** The halls' blocks: floor, walls (doorway, windows), roof and ridge, lamps; the stations' and furniture's cells. */
    public static void build(astratto.bridge.CommandBuffer b) {
        int f = DevMap.FLOOR;
        for (Hall h : HALLS) {
            for (int x = h.x0(); x <= h.x1(); x++) for (int z = Z0; z <= Z1; z++) {
                for (int y = f; y <= f + 6; y++) b.blockSet(x, y, z, Blocks.AIR);
                b.blockSet(x, f - 1, z, Blocks.PLANKS);
                b.blockSet(x, f - 2, z, Blocks.DIRT);
                b.blockSet(x, f + 4, z, Blocks.ROOF);
            }
            rpg.world.BuildingKit.walls(b::blockSet, h.x0(), Z0, h.x1(), Z1, f, f + 3, h.wall());
            for (int x = h.x0(); x <= h.x1(); x++) b.blockSet(x, f + 5, (Z0 + Z1) / 2, Blocks.ROOF);
            for (int y = f; y < f + DevMap.DOOR_HEIGHT; y++) { b.blockSet(h.doorX(), y, Z0, Blocks.AIR); b.blockSet(h.doorX() + 1, y, Z0, Blocks.AIR); }
            // windows to the street and the sea, lamps on the end walls
            for (int x = h.x0() + 2; x < h.x1() - 1; x += 3) { if (Math.abs(x - h.doorX()) > 1) b.blockSet(x, f + 1, Z0, Blocks.GLASS); b.blockSet(x, f + 1, Z1, Blocks.GLASS); }
            b.blockSet(h.x0(), f + 2, (Z0 + Z1) / 2, Blocks.LAMP); b.blockSet(h.x1(), f + 2, (Z0 + Z1) / 2, Blocks.LAMP);
            // and lamps along the long walls, over the benches and by the door, so the work can be seen
            for (int x = h.x0() + 3; x < h.x1() - 1; x += 4) { b.blockSet(x, f + 3, Z1, Blocks.LAMP); if (Math.abs(x - h.doorX()) > 2) b.blockSet(x, f + 3, Z0, Blocks.LAMP); }
            // a clear apron of sand before the door
            for (int x = h.doorX() - 1; x <= h.doorX() + 2; x++) for (int z = Z0 - 2; z < Z0; z++) for (int y = f; y <= f + 3; y++) b.blockSet(x, y, z, Blocks.AIR);
            for (Object[] s : h.stations()) {
                Recipes.Station k = (Recipes.Station) s[0];
                int id = switch (k) { case FURNACE -> Blocks.FURNACE; case ANVIL -> Blocks.ANVIL; case RANGE -> Blocks.RANGE; case CRAFT_TABLE -> Blocks.CRAFT_TABLE; case SAWBENCH -> Blocks.SAWBENCH; default -> Blocks.TABLE; };
                b.blockSet((int) s[1], f, (int) s[2], id);
            }
            for (int[] bed : h.beds()) { b.blockSet(bed[0], f, bed[1], Blocks.BED); b.blockSet(bed[0], f, bed[1] + 1, Blocks.BED); }
            // each guild's trappings: the smiths' coal, the foresters' stacked logs, hay, the mariners' crates, books
            if (h.guild() != null) switch (h.guild()) {
                case METALLISTS -> { b.blockSet(14, f, 195, Blocks.COAL_BLOCK); b.blockSet(15, f, 195, Blocks.IRON_BLOCK); }
                case FORESTERS -> { b.blockSet(39, f, 195, Blocks.LOG); b.blockSet(39, f + 1, 195, Blocks.LOG); }
                case FARMERS -> { b.blockSet(105 + 2, f, 191, Blocks.HAY_BALE); b.blockSet(110, f, 195, Blocks.HAY_BALE); b.blockSet(111, f, 195, Blocks.HAY_BALE); }
                case MARINERS -> { b.blockSet(135, f, 195, Blocks.CRATE); b.blockSet(136, f, 195, Blocks.CRATE); b.blockSet(141, f, 195, Blocks.CRATE); }
                case ARCANISTS -> { for (int x = 161; x <= 163; x++) b.blockSet(x, f, 195, Blocks.BOOKCASE); for (int x = 167; x <= 169; x++) b.blockSet(x, f, 195, Blocks.BOOKCASE); }
                default -> { }
            }
            if (h.guild() == null && h.name().equals("the Armory")) for (int x = 20; x <= 25; x++) b.blockSet(x, f, 192, Blocks.TABLE);
        }
    }

    /** The fixtures - doors, stations, beds, a strongbox in each hall - from fixed ids after every older one. */
    public long init(long nextId) {
        float f = DevMap.FLOOR;
        for (int i = 0; i < HALLS.length; i++) {
            Hall h = HALLS[i];
            Door d = g.objects.add(new Door(nextId++, h.name().substring(4) + " door", new int[][]{{h.doorX(), Z0}, {h.doorX() + 1, Z0}}, false, null, 1, h.master()));
            d.spawn(g);
            doors.add(d);
            for (Object[] s : h.stations()) {
                Recipes.Station k = (Recipes.Station) s[0];
                String def = k.name().toLowerCase(), what = switch (k) { case CARDING_BENCH -> "Carding bench"; case SPINNING_WHEEL -> "Spinning wheel"; case LOOM -> "Loom"; case FULLING_TUB -> "Fulling tub"; case DYE_VAT -> "Dye vat";
                    case FURNACE -> "Furnace"; case ANVIL -> "Anvil"; case RANGE -> "Range"; case SAWBENCH -> "Sawbench"; default -> "Workbench"; };
                stations.add(g.objects.add(new Station(nextId++, def, what, k, (int) s[1] + 0.5f, f + 0.6f, (int) s[2] + 0.5f)));
            }
            for (int b = 0; b < h.beds().length; b++) {
                int[] bed = h.beds()[b];
                long owner = b < h.residents().length ? h.residents()[b] : 0;
                // (0.3.6, gamefixes: beds stood with their heads out in the room) the head to the nearer wall
                int facing = bed[1] + 2 >= Z1 ? 0 : 2;
                Furniture fu = g.objects.add(new Furniture(nextId++, Furniture.Kind.BED, bed[0], bed[1], bed[0], bed[1] + 1, 1, facing, owner, true));
                g.objects.furniture.add(fu);
            }
            g.objects.add(new Container(nextId++, "chest", h.name().substring(4) + " strongbox", "chest", h.x1() - 1.5f, f + 0.4f, Z0 + 1.5f, 20, h.master()));
        }
        return nextId;
    }

    /** Where a resident of the Row sleeps (their bed in their hall), or null. */
    public static float[] bedOf(long id) {
        for (Hall h : HALLS) for (int b = 0; b < h.beds().length && b < h.residents().length; b++) if (h.residents()[b] == id) return new float[]{h.beds()[b][0] + 0.5f, h.beds()[b][1] + 1f};
        return null;
    }
    public static Hall hallOf(long resident) { for (Hall h : HALLS) for (long r : h.residents()) if (r == resident) return h; return null; }

    // ------------------------------------------------------------------ the stores
    /** Each hall's store, kept by its master: the trade's materials to sell, its goods to buy in. */
    public void shops(Economy econ) {
        for (int i = 0; i < HALLS.length; i++) {
            Hall h = HALLS[i];
            float[] p = h.post();
            Economy.Shop s = new Economy.Shop(h.master(), h.guild() == null ? (h.name().equals("the Armory") ? "The Armory" : "The outworkers' cottage") : h.guild().title + " store", DevMap.jurisdiction(p[0], p[1]), p[0], p[1] - 1.2f);
            stores.put(i, s);
            if (h.guild() == null) {
                if (h.name().equals("the Armory")) {
                    for (String t : new String[]{"bronze", "iron", "steel"}) for (String piece : new String[]{"sword", "dagger", "axe", "mace", "helm", "body", "legs", "shield"}) {
                        ItemDef d = Items.find(t + "_" + piece);
                        if (d != null) econ.stockShop(s, d, t.equals("bronze") ? 1 : 0, 2);
                    }
                }
                continue;
            }
            switch (h.guild()) {
                case METALLISTS -> { for (String id : new String[]{"copper_ore", "tin_ore", "iron_ore", "coal"}) econ.stockShop(s, Items.get(id), 4, 12);
                    for (String id : new String[]{"bronze_bar", "iron_bar", "steel_bar"}) econ.stockShop(s, Items.get(id), 1, 6); econ.stockShop(s, Items.HAMMER, 1, 2); econ.stockShop(s, Items.POT, 0, 3); econ.stockShop(s, Items.BUCKET, 1, 4); }
                case FORESTERS -> { econ.stockShop(s, Items.LOGS, 6, 16); econ.stockShop(s, Items.OAK_LOGS, 2, 8); econ.stockShop(s, Items.PLANK, 4, 12); econ.stockShop(s, Items.TINDERBOX, 2, 3); econ.stockShop(s, Items.get("arrow_shaft"), 20, 60); econ.stockShop(s, Items.get("bronze_axe"), 1, 2); }
                case ARTISANS -> { econ.stockShop(s, Items.WOOL, 6, 14); econ.stockShop(s, Items.COTTON, 4, 14); econ.stockShop(s, Items.FLAX, 4, 12); econ.stockShop(s, Items.BERRIES, 6, 12); econ.stockShop(s, Items.BUCKET_OF_WATER, 3, 6);
                    for (ItemDef d : new ItemDef[]{Items.CLEAN_WOOL, Items.CLEAN_COTTON, Items.YARN, Items.COTTON_THREAD, Items.LINEN_THREAD, Items.LOOM_CLOTH, Items.FULLED_CLOTH, Items.DYED_CLOTH}) econ.stockShop(s, d, 0, 8);
                    econ.stockShop(s, Items.CLOTH, 2, 10); econ.stockShop(s, Items.FINE_CLOTH, 0, 6); econ.stockShop(s, Items.SHEARS, 1, 2); }
                case FARMERS -> { for (rpg.world.Farming.Crop c : rpg.world.Farming.Crop.values()) econ.stockShop(s, c.seed, 6, 10);
                    for (ItemDef d : new ItemDef[]{Items.CARROT, Items.CABBAGE, Items.BARLEY, Items.HOPS, Items.COTTON, Items.APPLE}) econ.stockShop(s, d, 2, 10); econ.stockShop(s, Items.get("guam"), 2, 8); econ.stockShop(s, Items.SHEARS, 1, 2); }
                case PROVISIONERS -> { econ.stockShop(s, Items.BREAD, 4, 10); econ.stockShop(s, Items.RAW_MEAT, 2, 8); econ.stockShop(s, Items.FISHING_BAIT, 20, 40);
                    for (rpg.fishing.FishKind k : rpg.fishing.FishKind.values()) if (k.level <= 45 && k != rpg.fishing.FishKind.STARFISH) econ.stockShop(s, k.raw(), 0, 6); econ.stockShop(s, Items.SHRIMP, 3, 8); econ.stockShop(s, Items.TROUT, 2, 6); }
                case MARINERS -> { econ.stockShop(s, Items.HARPOON, 1, 2); econ.stockShop(s, Items.LOBSTER_POT, 1, 2); econ.stockShop(s, Items.FISHING_NET, 2, 3); econ.stockShop(s, Items.FISHING_ROD, 2, 3); econ.stockShop(s, Items.FISHING_BAIT, 30, 60);
                    for (rpg.fishing.FishKind k : rpg.fishing.FishKind.values()) if (k.livesIn(rpg.fishing.FishKind.W.SEA | rpg.fishing.FishKind.W.DEEP | rpg.fishing.FishKind.W.SHORE)) econ.stockShop(s, k.raw(), 0, 4); }
                case ARCANISTS -> { econ.stockShop(s, Items.STAFF, 1, 2); econ.stockShop(s, Items.HOLY_SYMBOL, 1, 2); econ.stockShop(s, Items.RELIGIOUS_WINE, 2, 4); econ.stockShop(s, Items.RESTORATIVE, 2, 4); econ.stockShop(s, Items.get("wand"), 1, 2); }
            }
        }
        if (rpg.world.WorldMap.shadowveil()) seatShadowveil(econ);
    }

    /**
     * (0.4.7) ShadowVeil's guild seats: every structure the architecture gives a guild. A headquarters' store is its
     * guildmaster's (the Row's store, moved to the master's counter and named for the hall); a chapter's head keeps a
     * store of their own stocked as the guild's. The Row's halls with no seat on the map keep no store here.
     */
    private void seatShadowveil(Economy econ) {
        seats.clear();
        var t = rpg.world.shadowveil.ShadowVeil.terrain();
        if (t == null || t.arch == null) return;
        int chapters = 0;
        for (var st : t.arch.structures) {
            String gk = st.opt("guild");
            if (gk == null) continue;
            Kind k;
            try { k = Kind.valueOf(gk); } catch (IllegalArgumentException e) { continue; }
            boolean hq = !"chapter".equals(st.opt("seat"));
            long keeper = 0;
            if (hq) for (Hall h : HALLS) if (h.guild() == k) keeper = h.master();
            if (keeper == 0 || !(g.actor(keeper) instanceof Npc)) {
                keeper = 0;
                for (var as : rpg.world.shadowveil.Census.all().values()) if (st.id.equals(as.workId())) { keeper = as.id(); break; }
            }
            if (keeper == 0) continue;
            seats.add(new Seat(k, st.id, st.name, hq, keeper));
            Economy.Shop s = null;
            if (hq) for (var e : stores.entrySet()) if (e.getKey() < CHAPTER_STORES && HALLS[e.getKey()].guild() == k) s = e.getValue();
            if (s == null) {
                s = new Economy.Shop(keeper, st.name + " store", DevMap.jurisdiction(st.cx(), st.cz()), st.cx(), st.cz());
                stores.put(CHAPTER_STORES + chapters++, s);
                stockLike(econ, s, k);
            }
            s.name = st.name + " store";
            if (g.actor(keeper) instanceof Npc n) {
                s.counterX = n.postX; s.counterZ = n.postZ + 1.4f; s.jurisdiction = n.homeJurisdiction;
                float[] cust = rpg.world.shadowveil.Census.customerSpot(keeper);
                if (cust != null) { s.counterX = cust[0]; s.counterZ = cust[1]; }
            }
        }
    }
    /** (0.4.7) A chapter's opening stock: as its guild's headquarters' (built the same way, into its own store). */
    private void stockLike(Economy econ, Economy.Shop s, Kind k) {
        for (int i = 0; i < HALLS.length; i++) if (HALLS[i].guild() == k && stores.get(i) != null)
            for (var stack : stores.get(i).stock.stacks()) econ.stockShop(s, stack.def, Math.max(1, stack.amount / 2), Math.max(2, stack.amount));
    }
    /** (0.4.7) The seat a structure is, or null. */
    public Seat seatAt(float x, float z) {
        var t = rpg.world.shadowveil.ShadowVeil.terrain();
        if (seats.isEmpty() || t == null || t.arch == null) return null;
        var st = t.arch.at((int) Math.floor(x), (int) Math.floor(z));
        if (st == null) return null;
        for (Seat s : seats) if (s.structure().equals(st.id)) return s;
        return null;
    }
    /** (0.4.7) The seat someone keeps, or null. */
    public Seat seatOf(long keeper) { for (Seat s : seats) if (s.keeper() == keeper) return s; return null; }
    /** The store a merchant keeps (a guildmaster's, the Armory's), or null. */
    public Economy.Shop storeOf(Npc keeper) {
        for (var e : stores.entrySet()) if (e.getValue().merchant == keeper.id && (e.getKey() >= CHAPTER_STORES || HALLS[e.getKey()].master() == keeper.id)) return e.getValue();
        return null;
    }
    public Hall hallOfStore(Economy.Shop s) { for (var e : stores.entrySet()) if (e.getValue() == s && e.getKey() < CHAPTER_STORES) return HALLS[e.getKey()]; return null; }
    /** (0.4.7) The guild a store trades for (a Row hall's, or a ShadowVeil seat's), or null. */
    public Kind guildOfStore(Economy.Shop s) {
        Hall h = hallOfStore(s);
        if (h != null && h.guild() != null) return h.guild();
        Seat seat = seatOf(s.merchant);
        return seat == null ? null : seat.guild();
    }
    /** The store a Row worker sells at and buys from: the smiths sell to the Armory; the rest to their own guild's store. */
    public Economy.Shop storeFor(Npc n) {
        // (0.4.7) on ShadowVeil a guild's member sells to the nearest of its seats' stores
        if (!seats.isEmpty()) {
            Kind mine = null;
            for (Kind k : Kind.values()) if (member(n, k)) { mine = k; break; }
            if (mine == null) return null;
            Economy.Shop best = null; double bd = Double.MAX_VALUE;
            for (Seat seat : seats) if (seat.guild() == mine) for (var st : stores.values()) if (st.merchant == seat.keeper()) {
                double d = Math.hypot(st.counterX - n.postX, st.counterZ - n.postZ);
                if (d < bd && d < SEAT_TRADE_REACH) { bd = d; best = st; }
            }
            return best;
        }
        Hall home = hallOf(n.id);
        if (home == null) return null;
        if (n.profession == Profession.BLACKSMITH || n.profession == Profession.ARMORER) return stores.get(1);
        if (home.guild() == null && home.name().startsWith("the outworkers")) return stores.get(4);   // the outworkers work for the Artisans' workshop
        for (var e : stores.entrySet()) if (HALLS[e.getKey()] == home) return e.getValue();
        return null;
    }

    // ------------------------------------------------------------------ membership
    /** Whether someone belongs to a guild: an NPC by their calling, the player by having joined. */
    public boolean member(Actor a, Kind k) {
        if (a == g.player) return joined.contains(k);
        if (!(a instanceof Npc n)) return false;
        Hall h = hallOf(n.id);
        if (h != null && h.guild() == k) return true;
        Profession p = n.profession;
        return switch (k) {
            case METALLISTS -> n.role == Npc.Role.MINER || p == Profession.BLACKSMITH || p == Profession.ARMORER || p == Profession.SMELTER;
            case FORESTERS -> n.role == Npc.Role.WORKER && n.title != null && n.title.contains("Woodcutter");
            case ARTISANS -> p == Profession.CARPENTER || p == Profession.TAILOR || p == Profession.CARDER || p == Profession.SPINNER || p == Profession.WEAVER || p == Profession.FULLER || p == Profession.DYER;
            case FARMERS -> n.role == Npc.Role.FARMER;
            case PROVISIONERS -> p == Profession.COOK || p == Profession.INNKEEPER || p == Profession.GAME_HUNTER || p == Profession.TRAPPER
                    || p == Profession.RIVER_FISHERMAN || p == Profession.LAKE_NET_FISHERMAN || p == Profession.SHELLFISH_GATHERER;
            case MARINERS -> p == Profession.COASTAL_FISHERMAN || p == Profession.DEEP_WATER_FISHERMAN;
            case ARCANISTS -> n.role == Npc.Role.PRIEST || n.role == Npc.Role.SCHOLAR || n.role == Npc.Role.HEALER;
        };
    }
    /** Every member of a guild, living, by name. */
    public List<Npc> members(Kind k) { List<Npc> l = new ArrayList<>(); for (Npc n : g.npcs) if (n.alive && member(n, k)) l.add(n); return l; }
    /** The guild a master keeps, or null. */
    public Kind guildOf(Npc master) {
        Seat seat = seatOf(master.id);
        if (seat != null) return seat.guild();
        for (Hall h : HALLS) if (h.master() == master.id && h.guild() != null) return h.guild();
        return null;
    }
    /** Why the player cannot join (null: they can). */
    public String joinRefusal(Actor a, Kind k) {
        if (member(a, k)) return "You are already a member of the " + k.title + ".";
        int best = 0; for (Skill s : k.skills) best = Math.max(best, a.skills.level(s));
        if (best < k.joinLevel) return "The " + k.title + " takes members at level " + k.joinLevel + " in " + skillNames(k) + ".";
        if (!a.has(Items.COINS, DUES)) return "The dues are " + DUES + " G.";
        return null;
    }
    public Result join(Actor a, Kind k, Npc master, long req) {
        String why = joinRefusal(a, k);
        if (why != null) return Result.no(why);
        for (var coins : a.takeItems(Items.COINS, DUES)) master.store(coins);
        if (a == g.player) joined.add(k);
        g.state.commit(req, "GuildJoined", a.id, master.id, DevMap.jurisdiction(master.x, master.z), 0, k.title);
        return Result.ok();
    }
    public static String skillNames(Kind k) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < k.skills.length; i++) b.append(i == 0 ? "" : i == k.skills.length - 1 ? " or " : ", ").append(k.skills[i].title);
        return b.toString();
    }

    // ------------------------------------------------------------------ what a hall does for its members
    /** How long a member's work at a station takes, as a share: a quarter less at their own guild's hall. */
    public float speed(Actor a, Station s) {
        // (0.4.7) on ShadowVeil, at any of the guild's seats
        Seat seat = seatAt(s.x, s.z);
        if (seat != null) return member(a, seat.guild()) ? HALL_SPEED : 1;
        for (Hall h : HALLS) if (h.guild() != null && h.inside(s.x, s.z) && member(a, h.guild())) return HALL_SPEED;
        // the outworkers' cottage is the Artisans' workshop's own
        if (HALLS[3].inside(s.x, s.z) && member(a, Kind.ARTISANS)) return HALL_SPEED;
        return 1;
    }
    /** What a guild store pays a member for a thing, as a share of what it pays anyone. */
    public float sellShare(Actor seller, Economy.Shop s) { Kind k = guildOfStore(s); return k != null && member(seller, k) ? MEMBER_SELL : 1; }
    public float buyShare(Actor buyer, Economy.Shop s) { Kind k = guildOfStore(s); return k != null && member(buyer, k) ? MEMBER_BUY : 1; }

    // ------------------------------------------------------------------ the guildmaster's words
    /** What a guildmaster says of the guild, and the player's join (added to their talk). */
    public void talk(rpg.ui.Dialog d, Npc master) {
        Kind k = guildOf(master);
        if (k == null) return;
        d.choice("Tell me about the " + k.title + ".", () -> {
            var members = members(k);
            StringBuilder names = new StringBuilder();
            for (int i = 0; i < Math.min(6, members.size()); i++) names.append(i == 0 ? "" : ", ").append(members.get(i).name);
            g.ui.dialog(new rpg.ui.Dialog(master.name, "We are the " + k.title + ": " + k.trade + ". Our members work quicker at the hall's benches, and the store pays them better for the trade's goods. "
                    + "We take members at level " + k.joinLevel + " in " + skillNames(k) + ", and the dues are " + DUES + " G. "
                    + (members.isEmpty() ? "" : "Among us: " + names + (members.size() > 6 ? " and " + (members.size() - 6) + " more." : "."))));
        });
        if (!member(g.player, k)) d.choice("I would like to join the " + k.title + ".", () -> {
            Result r = join(g.player, k, master, g.state.newRequestId());
            g.ui.dialog(new rpg.ui.Dialog(master.name, r.rejected() ? r.reason() + " Come back when you are ready." : "Welcome to the " + k.title + ". The benches and the store are yours to use as a member's."));
        });
        else d.choice("How goes the guild?", () -> g.ui.dialog(new rpg.ui.Dialog(master.name, "Well enough, fellow member. " + members(k).size() + " of us at work, and the store stands open to you.")));
    }

    // ------------------------------------------------------------------ saving
    public Map<String, Object> save() {
        Map<String, Object> m = new HashMap<>();
        List<Object> j = new ArrayList<>(); for (Kind k : joined) j.add(k.name()); m.put("joined", j);
        Map<String, Object> st = new HashMap<>(); for (var e : stores.entrySet()) st.put(String.valueOf(e.getKey()), Economy.saveStock(e.getValue())); m.put("stores", st);
        return m;
    }
    public void load(Map<String, Object> m) {
        joined.clear();
        if (m == null) return;
        if (m.get("joined") instanceof List<?> l) for (Object o : l) try { joined.add(Kind.valueOf(String.valueOf(o))); } catch (IllegalArgumentException ignored) { }
        if (m.get("stores") instanceof Map<?, ?> st) for (var e : stores.entrySet()) { Object saved = st.get(String.valueOf(e.getKey())); if (saved != null) Economy.loadStock(e.getValue(), saved); }
    }
}
