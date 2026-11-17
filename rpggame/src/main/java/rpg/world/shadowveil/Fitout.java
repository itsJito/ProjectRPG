package rpg.world.shadowveil;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import rpg.world.Blocks;
import rpg.world.BuildingKit;

/**
 * (0.4.4, gamefixes: every interior of ShadowVeil constructed, furnished and made to work) The fitting-out of every
 * enterable building the architecture raised, inside the walls it already has. For each of its rooms' volumes (kept
 * empty by 0.4.2's reservations) it:
 * <ol>
 * <li>reads the storeys actually built - every floor slab in the volume, and the clear height over each;</li>
 * <li>replaces the old open stair wells with a real staircase between each pair of storeys: a flight of steps hugging
 *     a wall, one cell of rise to each, its last step set into the floor above, the floor opened over it for headroom
 *     and railed round the opening;</li>
 * <li>lays out each storey's rooms from its purpose's programme (data: {@code shadowveil_interiors.json}) - a front
 *     room where the door comes in, the rest behind it - walling them off with partitions in the building's own inner
 *     finish, each with its doorway (hung with a door where the programme asks: a bedroom's, a vault's, a cell's);</li>
 * <li>furnishes each room for its use from the furniture library, by the building's wealth: what stands against a wall
 *     backs onto it, beds put their heads to it, a table gathers its seats, a counter divides the staff from the
 *     customers, a church's pews face its altar - every solid piece kept only if every cell of the room it left open can
 *     still be walked to from the door, and everything used from a side (a bed's flank, a counter's two sides, a
 *     station's front, a seat) can still be reached;</li>
 * <li>lights every room (a chandelier under a tall ceiling, candles elsewhere), and sets out its working fittings - the
 *     trades' stations, the storage chests, the altars, the posts where its people work, the customers' places, the
 *     cells of a jail - for the game's systems to take up.</li>
 * </ol>
 * Everything is deterministic (hashed from the world seed and the structure), and what was made is recorded room by room
 * with every defect found, for the registry's quality record.
 */
final class Fitout {
    // grid states of a storey's cells
    static final byte VOID = 0, OPEN = 1, WALL = 2, STAIR = 3, KEEP = 4, FURN = 5;
    /**
     * (0.4.5, gamefixes: most stairs kept the player from going fully up or down) how many steps below the top one the
     * floor above is opened over. A body 1.8 m tall crossing from one step onto the next is lifted to the higher tread
     * while still partly under the lower: the slab must stand clear of the next tread's top by its height, so it is
     * opened over every step within four of the top (it was two: every flight in the world struck the head).
     */
    static final int OPEN_OVER = rpg.Tuning.i("interiors.stairOpenOver", 4);

    private final Architecture ar;
    private final Kit.Canvas c;
    private final Registry reg;
    private final Map<String, Object> data, uses, programs;
    private final Map<String, Integer> residents = new HashMap<>(), staff = new HashMap<>();
    /** The callings of the people who work at each structure (the census's), for its stations. */
    private final Map<String, List<String>> trades = new HashMap<>();
    /** (0.4.5) Callings whose stations go in their home (they work out of doors): a home too small for one is no fault. */
    private final java.util.Set<String> homeTrades = new java.util.HashSet<>();
    final List<String> notes = new ArrayList<>();
    int rooms, pieces, stairs;

    Fitout(Architecture ar, Kit.Canvas c, Registry reg) {
        this.ar = ar; this.c = c; this.reg = reg;
        this.data = ar.a.map("interiors");
        this.uses = Atlas.map(data, "uses");
        this.programs = Atlas.map(data, "programs");
        List<Map<String, Object>> roll = new ArrayList<>(Atlas.list(ar.a.map("census"), "people"));
        roll.addAll(Atlas.list(ar.a.map("census"), "additions"));
        for (Map<String, Object> p : roll) {
            String h = Atlas.s(p, "home", null), w = Atlas.s(p, "work", null);
            if (h != null) residents.merge(h, 1, Integer::sum);
            if (w != null) staff.merge(w, 1, Integer::sum);
            String prof = Atlas.s(p, "profession", null);
            // (0.4.5, gamefixes: furnaces and the like stood out of doors across every kingdom) a calling's stations go
            // inside: in the building it works in, or - where it works out of doors (a yard, a mine, a field) - in its
            // own home
            String in = w != null && indoors(w) ? w : h != null && indoors(h) ? h : null;
            if (in != null && prof != null) { trades.computeIfAbsent(in, k -> new ArrayList<>()).add(prof); if (!in.equals(w)) homeTrades.add(in + "|" + prof); }
        }
    }

    /** (0.4.7) The movable pieces a station may push out of its way. */
    private static final java.util.Set<String> MOVABLE = java.util.Set.of("CRATE", "SHELVES", "CUPBOARD", "SIDEBOARD", "DRESSER", "PLANTER", "WINE_RACK", "ARMOR_STAND");

    /** (0.4.7) Takes one movable piece out of a room (its cells open again); false if there is none left. */
    private boolean clearOne(Architecture.Structure s, Storey st, Room r, Architecture.Fitted fitted) {
        Plan p = st.plan;
        for (int k = ar.pieces.size() - 1; k >= 0; k--) {
            Architecture.Piece pc = ar.pieces.get(k);
            if (!pc.owner().equals(s.id) || pc.y() != st.walk() || !MOVABLE.contains(pc.kind())) continue;
            List<int[]> cells = new ArrayList<>();
            for (int u = r.u0; u <= r.u1; u++) for (int v = r.v0; v <= r.v1; v++) {
                int x = p.f.wx(u, v), z = p.f.wz(u, v);
                if (x >= pc.x0() && x <= pc.x1() && z >= pc.z0() && z <= pc.z1()) cells.add(new int[]{u, v});
            }
            if (cells.isEmpty()) continue;
            for (int x = pc.x0(); x <= pc.x1(); x++) for (int z = pc.z0(); z <= pc.z1(); z++)
                for (int y = 0; y < Math.max(1, Math.min(pc.h(), 2)); y++) if (c.get(x, st.walk() + y, z) == furnitureBlock(pc.kind())) c.set(x, st.walk() + y, z, Blocks.AIR);
            for (int[] q : cells) if (st.g[q[0]][q[1]] == FURN) st.g[q[0]][q[1]] = OPEN;
            ar.pieces.remove(k);
            fitted.pieces.remove(pc.kind().toLowerCase(java.util.Locale.ROOT));
            pieces--;
            return true;
        }
        return false;
    }

    /** Whether a building is fitted out (enterable, above ground, with rooms). */
    private boolean indoors(String id) {
        Registry.Entry e = reg == null ? null : reg.byId(id);
        return e != null && e.enterable && !e.underground && !e.interior.isEmpty();
    }

    /**
     * (0.4.5) Every station the callings working here need, inside: those a workroom already took, and the rest against
     * a wall of a workroom, else of the ground storey's largest room, else of any room - never out of doors.
     */
    private void ensureTrades(Architecture.Structure s, List<Storey> all, Architecture.Fitted fitted) {
        Map<String, Object> table = Atlas.map(ar.a.map("census"), "stations");
        java.util.LinkedHashSet<String> want = new java.util.LinkedHashSet<>();
        for (String prof : trades.getOrDefault(s.id, List.of())) for (Object o : Atlas.arr(table, prof)) want.add(String.valueOf(o));
        if (want.isEmpty()) return;
        List<Object[]> order = new ArrayList<>();
        for (Storey st : all) for (Room r : st.rooms) {
            boolean work = false;
            for (Map<String, Object> pc : Atlas.list(Atlas.map(uses, r.use), "pieces")) if ("trades".equals(Atlas.s(pc, "place", ""))) work = true;
            int rank = (work ? 0 : 2_000_000) + (st == all.get(0) || all.indexOf(st) == 0 ? 0 : 1_000_000) - r.area();
            order.add(new Object[]{rank, st, r});
        }
        order.sort((a, b) -> Integer.compare((Integer) a[0], (Integer) b[0]));
        for (String k : want) {
            if (fitted.stations.contains(k.toLowerCase(java.util.Locale.ROOT))) continue;
            boolean done = false;
            for (Object[] o : order) {
                Storey st = (Storey) o[1];
                if (st.ladder) continue;
                if (placeWall(st, (Room) o[2], fitted, Map.of("station", k, "post", "station:" + k), false)) { done = true; break; }
            }
            // (0.4.7) a calling's station before the room's odds and ends: a crate, a set of shelves or a dresser against a
            // wall of the ground storey gives way to the anvil or the furnace, one at a time until it stands
            for (Object[] o : order) {
                if (done) break;
                Storey st = (Storey) o[1];
                if (st.ladder || all.indexOf(st) != 0) continue;
                while (!done && clearOne(s, st, (Room) o[2], fitted))
                    done = placeWall(st, (Room) o[2], fitted, Map.of("station", k, "post", "station:" + k), false);
            }
            if (!done) {
                boolean fromHome = false;
                for (String prof : trades.getOrDefault(s.id, List.of())) if (homeTrades.contains(s.id + "|" + prof) && Atlas.arr(table, prof).contains(k)) fromHome = true;
                int area = 0;
                for (Storey st : all) for (Room r : st.rooms) area += r.area();
                if (area < Atlas.i(data, "stationMinArea", 16)) fromHome = true;   // a hut or a lean-to: no room for a bench at all
                if (fromHome) notes.add(s.id + ": no room at home for a " + k.toLowerCase(java.util.Locale.ROOT).replace('_', ' ') + " (its user works at the nearest)");
                else fitted.defects.add("no room inside for its " + k.toLowerCase(java.util.Locale.ROOT).replace('_', ' '));
            }
        }
    }

    /**
     * (0.4.5, gamefixes: a tapestry behind a suit of armour, a pot rack behind a dresser) whatever hangs on a wall where a
     * tall piece was later set before it (a bed's, a counter's, a hearth's own placing) is taken down: a low piece under a
     * painting is a sideboard under a painting, a tall one hides it.
     */
    private void unhangBehind(Architecture.Structure s, int from, Architecture.Fitted fitted) {
        java.util.Set<String> hung = java.util.Set.of("PAINTING", "TAPESTRY", "MIRROR", "WEAPON_RACK", "POT_RACK", "SCONCE");
        for (int k = ar.pieces.size() - 1; k >= from; k--) {
            Architecture.Piece pc = ar.pieces.get(k);
            if (!pc.owner().equals(s.id) || !hung.contains(pc.kind())) continue;
            boolean hidden = false;
            for (int x = pc.x0(); x <= pc.x1() && !hidden; x++) for (int z = pc.z0(); z <= pc.z1() && !hidden; z++) {
                int b = c.get(x, pc.y() + 1, z);
                hidden = b == Blocks.TABLE || b == Blocks.BOOKCASE || b == Blocks.HEADSTONE || b == Blocks.BED || b == Blocks.CRATE;
            }
            if (hidden) { ar.pieces.remove(k); fitted.pieces.remove(pc.kind().toLowerCase(java.util.Locale.ROOT)); pieces--; }
        }
    }

    // ================================================================== the whole world
    void fitAll() {
        if (data.isEmpty() || reg == null) return;
        for (Architecture.Structure s : ar.structures) {
            Registry.Entry e = reg.byId(s.id);
            if (e == null || !e.enterable || e.underground || e.interior.isEmpty()) continue;
            fit(s, e);
        }
        notes.add(rooms + " rooms fitted out in " + ar.fitted.size() + " buildings: " + pieces + " pieces, " + stairs + " staircases");
    }

    /** One building's whole interior. */
    private void fit(Architecture.Structure s, Registry.Entry e) {
        Map<String, Object> prog = Atlas.map(programs, e.purpose);
        if (prog.isEmpty()) prog = Atlas.map(programs, "_default");
        Architecture.Fitted fitted = new Architecture.Fitted(s.id, e.purpose, wealth(s, e, prog));
        ar.fitted.put(s.id, fitted);
        // the volumes of its rooms, the biggest first (a courtyard's ranges, a church's nave and tower)
        List<Registry.Volume> vols = new ArrayList<>();
        for (Registry.Volume v : e.interior) if (v.kind().startsWith("interior") && v.area() >= 4) vols.add(v);
        vols.sort((a, b) -> a.area() != b.area() ? Integer.compare(b.area(), a.area()) : Integer.compare(throughs(s, a), throughs(s, b)));
        int bedsWanted = Math.max(Atlas.i(prog, "beds", 0), residents.getOrDefault(s.id, 0));
        if (bedsWanted == 0 && Atlas.i(prog, "bedsBySize", 0) > 0) bedsWanted = Math.max(1, e.width * e.depth / Atlas.i(prog, "bedsBySize", 40));
        fitted.bedsWanted = bedsWanted;
        int[] bedsLeft = {bedsWanted};
        List<Map<String, Object>> volProgs = Atlas.list(prog, "volumes");
        List<Storey> all = new ArrayList<>();
        int piecesFrom = ar.pieces.size();
        for (int k = 0; k < vols.size(); k++) {
            Map<String, Object> vp = volProgs.isEmpty() ? prog : volProgs.get(k % volProgs.size());
            Plan p = plan(s, vols.get(k), fitted);
            if (p == null || p.levels.isEmpty()) { fitted.defects.add("volume " + k + " has no storey to stand on"); continue; }
            fillWells(p);
            doors(p, fitted);
            p.stairFront = Atlas.s(prog, "stair", "back").equals("front");
            for (int i = 0; i + 1 < p.levels.size(); i++) stair(p, i, fitted);
            for (int i = 0; i < p.levels.size(); i++) {
                Storey st = p.levels.get(i);
                String role = levelRole(i, p.levels.size());
                List<Map<String, Object>> specs = Atlas.list(Atlas.map(vp, "levels"), role);
                // (0.4.8, gamefixes: every floor of the tower at 630, 70, 552 a repeat of the last) the upper storeys may
                // take their turn through a cycle of programmes ("upperCycle"), started where the building's hash says,
                // so no two storeys running up a tower (and no two towers) are fitted alike
                List<?> cycle = Atlas.arr(Atlas.map(vp, "levels"), "upperCycle");
                if (role.equals("upper") && !cycle.isEmpty()) {
                    int start = (int) (Noise.hash(ar.a.seed, s.id.hashCode(), k, 811) * cycle.size());
                    @SuppressWarnings("unchecked") List<Map<String, Object>> turn = (List<Map<String, Object>>) cycle.get((start + i - 1) % cycle.size());
                    specs = turn;
                }
                if (specs.isEmpty()) specs = Atlas.list(Atlas.map(vp, "levels"), i == 0 ? "ground" : "upper");
                if (specs.isEmpty()) specs = List.of(Map.of("use", "hall"));
                layout(p, st, specs, fitted);
                all.add(st);
            }
        }
        // what nobody can reach (a pocket behind a tower's footing, a corner a wall cut off) is walled up flush first
        for (Storey st : all) seal(st, fitted);
        // the beds first, over the whole building: the bedrooms' share each, then any room people sleep in, then any room
        // not kept from them - so a house full of furniture never leaves its people without a bed
        int bedRooms = 0;
        for (Storey st : all) for (Room r : st.rooms) if (Atlas.i(Atlas.map(uses, r.use), "beds", 0) > 0 && !st.ladder) bedRooms++;
        int[] roomsLeft = {bedRooms};
        for (Storey st : all) for (Room r : st.rooms) if (!st.ladder) beds(st, r, fitted, bedsLeft, roomsLeft);
        for (int pass = 0; pass < 2 && bedsLeft[0] > 0; pass++) for (Storey st : all) for (Room r : st.rooms) {
            if (bedsLeft[0] <= 0) break;
            if (st.ladder) continue;
            Map<String, Object> u = Atlas.map(uses, r.use);
            boolean sleeps = !Atlas.arr(u, "overflow").isEmpty() || Boolean.TRUE.equals(u.get("sleeps"));
            if (pass == 0 ? sleeps : !Boolean.TRUE.equals(u.get("noBeds")))
                while (bedsLeft[0] > 0 && placeBed(st, r, fitted, "BED")) { bedsLeft[0]--; r.solid++; }
        }
        // the residents' beds must all be found; the programme's spare (guest) beds only where there is room
        int required = residents.getOrDefault(s.id, 0), placed = bedsWanted - bedsLeft[0];
        if (placed < required) fitted.defects.add((required - placed) + " of " + required + " residents' beds found no room");
        fitted.bedsWanted = Math.max(required, placed);
        // then everything else, room by room
        for (Storey st : all) for (Room r : st.rooms) furnish(st, r, fitted);
        if (!all.isEmpty()) ensureTrades(s, all, fitted);
        unhangBehind(s, piecesFrom, fitted);
        for (Storey st : all) reachCheck(st, fitted);
        if (java.util.Arrays.asList(DEBUG.split(",")).contains(s.id)) { System.out.println("== " + s.id + " " + fitted.defects); for (Storey st : all) dump(st); }
    }
    /** (debugging) RPG_FIT_DEBUG names a structure whose storeys are printed after fitting. */
    private static final String DEBUG = System.getenv().getOrDefault("RPG_FIT_DEBUG", "");
    private void dump(Storey st) {
        StringBuilder b = new StringBuilder("storey y" + st.floorY + " clear " + st.clear + " frame " + st.plan.f.facing + " " + st.plan.W + "x" + st.plan.D + " entries " + st.entries.size() + "\n");
        for (int v = st.plan.D - 1; v >= 0; v--) {
            for (int u = 0; u < st.plan.W; u++) {
                int x = st.plan.f.wx(u, v), z = st.plan.f.wz(u, v);
                int blk = c.get(x, st.walk(), z);
                char ch = switch (st.g[u][v]) { case VOID -> ' '; case OPEN -> '.'; case WALL -> '#'; case STAIR -> '/'; case KEEP -> 'k'; default -> 'F'; };
                if (st.g[u][v] == FURN) ch = blk == Blocks.BED ? 'B' : blk == Blocks.SEAT ? 's' : blk == Blocks.TABLE ? 'T' : blk == Blocks.HEADSTONE ? 'H' : blk == Blocks.CRATE ? 'c' : 'F';
                for (int[] en : st.entries) if (en[0] == u && en[1] == v) ch = 'E';
                b.append(ch);
            }
            b.append('\n');
        }
        for (Room r : st.rooms) b.append("  room ").append(r.use).append(" u").append(r.u0).append("-").append(r.u1).append(" v").append(r.v0).append("-").append(r.v1).append(r.walled ? " walled" : "").append('\n');
        System.out.print(b);
    }

    /** How many of a structure's doorways open into a volume (a gateway range has two: the way through it). */
    private static int throughs(Architecture.Structure s, Registry.Volume v) {
        int n = 0;
        for (int k = 0; k < s.doors.size(); k++) for (int[] cell : s.doors.get(k)) {
            int[] out = s.doorOut.get(k);
            for (int step = -1; step <= 1; step += 2) { int x = cell[0] + out[0] * step, z = cell[1] + out[1] * step; if (x >= v.x0() && x <= v.x1() && z >= v.z0() && z <= v.z1()) { n++; break; } }
        }
        return n;
    }

    private static String levelRole(int i, int n) { return n == 1 ? "single" : i == 0 ? "ground" : i == n - 1 && n >= 3 ? "top" : "upper"; }

    private String wealth(Architecture.Structure s, Registry.Entry e, Map<String, Object> prog) {
        String w = Atlas.s(s.opts, "wealth", Atlas.s(prog, "wealth", "common"));
        if (e.sizeClass != null) w = Atlas.s(Atlas.map(Atlas.map(data, "wealth"), "sizeClass"), e.sizeClass, w);
        return w;
    }

    // ================================================================== reading what was built
    /** A storey: its floor slab's y, the walking level over it, how many cells are clear above, its cells' states. */
    static final class Storey {
        final int floorY, clear;
        final byte[][] g;
        final List<int[]> entries = new ArrayList<>();
        /** (0.4.6) Which of the ways in are openings into the next volume (an arch from a tower into a nave), not doors out. */
        final Set<Long> openings = new HashSet<>();
        final List<Room> rooms = new ArrayList<>();
        /** Cells something is used from (a bed's flank, a counter's sides, a station's front): kept reachable. */
        final Set<Long> use = new HashSet<>();
        /** Walls something already hangs on. */
        final Set<String> hung = new HashSet<>();
        /** (0.4.5) Where its chandeliers hang. */
        final List<int[]> hungAt = new ArrayList<>();
        /** Reached only by a ladder (no beds for those who live here: not everyone climbs). */
        boolean ladder;
        Plan plan;
        Set<Long> useCells() { return use; }
        Storey(int floorY, int clear, int W, int D) { this.floorY = floorY; this.clear = clear; this.g = new byte[W][D]; }
        int walk() { return floorY + 1; }
    }
    /** A volume in its own frame: u across its front (the side its door is in), v back from it. */
    static final class Plan {
        final Kit.Frame f;
        final int W, D;
        final boolean round;
        final Registry.Volume vol;
        final Architecture.Structure s;
        final List<Storey> levels = new ArrayList<>();
        /** Whether its stairs go at its front (by the way in) rather than at its back. */
        boolean stairFront;
        Plan(Kit.Frame f, int W, int D, boolean round, Registry.Volume vol, Architecture.Structure s) { this.f = f; this.W = W; this.D = D; this.round = round; this.vol = vol; this.s = s; }
        int[] local(int x, int z) {
            int ox = f.ox, oz = f.oz;
            return switch (f.facing) {
                case 's' -> new int[]{W - 1 - (x - ox), D - 1 - (z - oz)};
                case 'w' -> new int[]{W - 1 - (z - oz), x - ox};
                case 'e' -> new int[]{z - oz, D - 1 - (x - ox)};
                default -> new int[]{x - ox, z - oz};
            };
        }
        boolean in(int u, int v) { return u >= 0 && v >= 0 && u < W && v < D; }
    }
    /** A room: its rectangle in the plan's frame, what it is for, whether it is walled off. */
    static final class Room {
        final int u0, v0, u1, v1;
        final String use;
        final Map<String, Object> spec;
        /** The uses of rooms the storey had no room to divide off: furnished here too. */
        final List<String> merged = new ArrayList<>();
        boolean walled, bedLit;
        int solid;
        Room(int u0, int v0, int u1, int v1, String use, Map<String, Object> spec) { this.u0 = u0; this.v0 = v0; this.u1 = u1; this.v1 = v1; this.use = use; this.spec = spec; }
        int w() { return u1 - u0 + 1; }
        int d() { return v1 - v0 + 1; }
        int area() { return w() * d(); }
        boolean holds(int u, int v) { return u >= u0 && u <= u1 && v >= v0 && v <= v1; }
    }

    private static boolean solid(int b) { return b != Blocks.AIR && b != Blocks.WATER && !Blocks.isLeaves(b) && b != Blocks.DOOR && b != Blocks.LOCKED_DOOR; }
    private static boolean open(int b) { return b == Blocks.AIR || b == Blocks.DOOR || b == Blocks.LOCKED_DOOR; }

    /** The volume's frame (its front where its main door comes in), its storeys and their cells. */
    private Plan plan(Architecture.Structure s, Registry.Volume v, Architecture.Fitted fitted) {
        char facing = s.facing;
        int P = v.y0() - 1;
        for (int k = 0; k < s.doors.size(); k++) {
            int[] out = s.doorOut.get(k);
            int fy = s.doorFloors.get(k);
            boolean into = false;
            for (int[] cell : s.doors.get(k)) {
                int ix = cell[0] - out[0], iz = cell[1] - out[1];
                if (ix >= v.x0() && ix <= v.x1() && iz >= v.z0() && iz <= v.z1() && fy + 1 >= v.y0() - 1 && fy + 1 <= v.y1()) into = true;
            }
            if (into) { facing = out[0] > 0 ? 'e' : out[0] < 0 ? 'w' : out[1] > 0 ? 's' : 'n'; break; }
        }
        int W = facing == 'n' || facing == 's' ? v.x1() - v.x0() + 1 : v.z1() - v.z0() + 1;
        int D = facing == 'n' || facing == 's' ? v.z1() - v.z0() + 1 : v.x1() - v.x0() + 1;
        Kit.Frame f = new Kit.Frame(c, v.x0(), v.z0(), W, D, facing, P);
        Plan p = new Plan(f, W, D, v.round(), v, s);
        // the storeys: a level of the volume mostly solid with the level over it mostly open is a floor
        int lo = v.y0() - 1, hi = v.y1();
        int cells = 0;
        for (int u = 0; u < W; u++) for (int w = 0; w < D; w++) if (v.plan(f.wx(u, w), f.wz(u, w))) cells++;
        if (cells == 0) return null;
        double[] solidAt = new double[hi - lo + 4];
        for (int y = lo; y <= hi + 3; y++) {
            int n = 0;
            for (int u = 0; u < W; u++) for (int w = 0; w < D; w++) { int x = f.wx(u, w), z = f.wz(u, w); if (v.plan(x, z) && solid(c.get(x, y, z))) n++; }
            solidAt[y - lo] = n / (double) cells;
        }
        if (java.util.Arrays.asList(DEBUG.split(",")).contains(s.id)) { StringBuilder sb = new StringBuilder("levels " + v.kind() + " " + v.x0() + "," + v.z0() + "-" + v.x1() + "," + v.z1() + ":"); for (int y = lo; y <= hi; y++) sb.append(String.format(" %d=%.2f", y, solidAt[y - lo])); System.out.println(sb); }
        for (int y = lo; y <= hi - 1; y++) {
            if (solidAt[y - lo] < 0.55 || solidAt[y + 1 - lo] > 0.45) continue;
            int clear = 0;
            for (int k = y + 1; k <= hi + 3 && solidAt[k - lo] <= 0.45; k++) clear++;
            if (clear < 2) continue;
            Storey st = new Storey(y, clear, W, D);
            st.plan = p;
            p.levels.add(st);
        }
        for (Storey st : p.levels) read(p, st);
        return p;
    }

    /** The ways in on each storey a doorway opens onto: its first cell inside and the step after it, kept clear. */
    private void doors(Plan p, Architecture.Fitted fitted) {
        Architecture.Structure s = p.s;
        for (int k = 0; k < s.doors.size(); k++) {
            int[] out = s.doorOut.get(k);
            for (Storey st : p.levels) {
                if (s.doorFloors.get(k) != st.floorY) continue;
                for (int[] cell : s.doors.get(k)) for (int step = 0; step <= 2; step++) {
                    int[] l = p.local(cell[0] - out[0] * step, cell[1] - out[1] * step);
                    if (!p.in(l[0], l[1]) || !walk(st.g[l[0]][l[1]])) continue;
                    if (step <= 1) st.entries.add(new int[]{l[0], l[1]});
                    keep(st, l[0], l[1]);
                    // the cells either side of the first step in: a door swings and a body turns there
                    if (step == 1) for (int[] d : sides(p, out)) keep(st, l[0] + d[0], l[1] + d[1]);
                }
            }
        }
        // and any opening through the volume's edge onto open floor beyond it (an arch from a tower into a nave, a range's
        // court door the plan did not list): its first cell inside is a way in
        for (Storey st : p.levels) for (int u = 0; u < p.W; u++) for (int v = 0; v < p.D; v++) {
            if (u != 0 && v != 0 && u != p.W - 1 && v != p.D - 1 || !walk(st.g[u][v])) continue;
            for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int a = u + d[0], b = v + d[1];
                if (p.in(a, b)) continue;
                int[] wd = p.f.dir(d[0], d[1]);
                int x = p.f.wx(u, v) + wd[0], z = p.f.wz(u, v) + wd[1];
                boolean gap = open(c.get(x, st.walk(), z)) && open(c.get(x, st.walk() + 1, z)) && (solid(c.get(x, st.floorY, z)) || c.get(x, st.floorY, z) == Blocks.STAIRS);
                if (!gap || !p.vol.plan(p.f.wx(u, v), p.f.wz(u, v))) continue;
                // beyond it, open floor too (not a drop through a window's hole)
                int x2 = x + wd[0], z2 = z + wd[1];
                if (!open(c.get(x2, st.walk(), z2)) || !open(c.get(x2, st.walk() + 1, z2))) continue;
                // a gap in a wall: the cells either side of it along the wall are the wall
                int[] lat = p.f.dir(d[1], d[0]);
                boolean walled = solid(c.get(x + lat[0], st.walk(), z + lat[1])) || solid(c.get(x - lat[0], st.walk(), z - lat[1]));
                if (!walled) continue;
                boolean dup = false;
                for (int[] en : st.entries) if (en[0] == u && en[1] == v) dup = true;
                if (!dup) { st.entries.add(new int[]{u, v}); st.openings.add(key(u, v)); keep(st, u, v); }
            }
        }
        if (p.levels.get(0).entries.isEmpty()) fitted.defects.add("no doorway leads into the ground storey of a room volume");
    }

    /** The local directions across a world direction (a doorway's sides). */
    private static int[][] sides(Plan p, int[] out) {
        int[] a = p.local(p.f.ox + out[1], p.f.oz - out[0]), o = p.local(p.f.ox, p.f.oz);
        int du = Integer.signum(a[0] - o[0]), dv = Integer.signum(a[1] - o[1]);
        return new int[][]{{du, dv}, {-du, -dv}};
    }

    /** A storey's cells read from the canvas. */
    private void read(Plan p, Storey st) {
        for (int u = 0; u < p.W; u++) for (int v = 0; v < p.D; v++) {
            int x = p.f.wx(u, v), z = p.f.wz(u, v);
            if (!p.vol.plan(x, z)) { st.g[u][v] = VOID; continue; }
            int below = c.get(x, st.floorY, z), at = c.get(x, st.floorY + 1, z), over = c.get(x, st.floorY + 2, z);
            if (!open(at) || !open(over)) st.g[u][v] = WALL;
            else if (!solid(below) && below != Blocks.STAIRS) st.g[u][v] = VOID;
            else st.g[u][v] = OPEN;
        }
    }

    private static void keep(Storey st, int u, int v) { if (u >= 0 && v >= 0 && u < st.g.length && v < st.g[0].length && st.g[u][v] == OPEN) st.g[u][v] = KEEP; }
    private static boolean walk(byte b) { return b == OPEN || b == KEEP; }

    // ================================================================== the stairs
    /** The old open wells in every upper floor closed with the floor's own material (the real stairs open their own). */
    private void fillWells(Plan p) {
        for (int i = 1; i < p.levels.size(); i++) {
            Storey st = p.levels.get(i);
            Map<Integer, Integer> mats = new HashMap<>();
            for (int u = 0; u < p.W; u++) for (int v = 0; v < p.D; v++) { int x = p.f.wx(u, v), z = p.f.wz(u, v); int b = c.get(x, st.floorY, z); if (p.vol.plan(x, z) && solid(b)) mats.merge(b, 1, Integer::sum); }
            int mat = mats.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(Blocks.PLANKS);
            for (int u = 0; u < p.W; u++) for (int v = 0; v < p.D; v++) {
                int x = p.f.wx(u, v), z = p.f.wz(u, v);
                if (!p.vol.plan(x, z) || solid(c.get(x, st.floorY, z))) continue;
                // only a hole in the slab with the storey's air over it (never a doorway, never into a wall)
                if (!open(c.get(x, st.floorY + 1, z))) continue;
                c.set(x, st.floorY, z, mat);
                // and the hole's cells on every storey over it that were the same shaft
            }
            read(p, st);
        }
        for (Storey st : p.levels) read(p, st);
    }

    /**
     * A staircase from storey i to storey i + 1: a path of cells, one step of rise to each, along a wall where it can -
     * straight if it fits, else turning once at a corner (a round tower's winds round its wall) - its last step set into
     * the upper floor, the floor opened over the steps a body would strike its head on, a rail round the opening.
     */
    private void stair(Plan p, int i, Architecture.Fitted fitted) {
        Storey lo = p.levels.get(i), up = p.levels.get(i + 1);
        int rise = up.floorY - lo.floorY;
        if (rise < 2 || rise > 12) { fitted.defects.add("storeys " + i + "-" + (i + 1) + " are " + rise + " apart: no stair"); return; }
        int[][] best = null; double bestScore = -1e9;
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        // the search: from every cell, every first direction, at most two turns (each a quarter turn toward a wall)
        for (int u = 0; u < p.W; u++) for (int v = 0; v < p.D; v++) for (int[] d0 : dirs) {
            List<int[]> path = new ArrayList<>();
            path.add(new int[]{u, v});
            int[][] got = grow(p, lo, up, path, d0, rise, 0);
            if (got == null) continue;
            double sc = score(p, lo, up, got);
            if (sc > bestScore) { bestScore = sc; best = got; }
        }
        if (best == null) { if (!ladder(p, lo, up)) fitted.defects.add("no room for a stair or a ladder between storeys " + i + " and " + (i + 1)); else { fitted.stairs++; fitted.ladders++; } return; }
        build(p, lo, up, best, fitted);
        stairs++;
        fitted.stairs++;
    }

    /** Grows a stair path to its length: each next cell straight on, or a quarter turn (twice at most). */
    private int[][] grow(Plan p, Storey lo, Storey up, List<int[]> path, int[] dir, int rise, int turns) {
        int k = path.size() - 1;
        int[] at = path.get(k);
        if (!stepOk(p, lo, up, at[0], at[1], k, rise)) return null;
        if (path.size() == rise) {
            int[] last = path.get(rise - 1);
            // the foot: a cell to step up from, on the lower storey, before the first step
            int[] first = path.get(0), second = path.size() > 1 ? path.get(1) : new int[]{first[0] + dir[0], first[1] + dir[1]};
            int fu = first[0] - (second[0] - first[0]), fv = first[1] - (second[1] - first[1]);
            if (!p.in(fu, fv) || lo.g[fu][fv] != OPEN && lo.g[fu][fv] != KEEP) return null;
            // the head: a cell to step off onto, on the upper storey, beyond the last step (or beside it)
            int[] prev = path.get(rise - 2);
            int hu = last[0] + (last[0] - prev[0]), hv = last[1] + (last[1] - prev[1]);
            boolean head = p.in(hu, hv) && walk(up.g[hu][hv]) && !contains(path, hu, hv);
            if (!head) for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int a = last[0] + d[0], b = last[1] + d[1];
                if (p.in(a, b) && walk(up.g[a][b]) && !contains(path, a, b) && !(a == prev[0] && b == prev[1])) { head = true; break; }
            }
            if (!head) return null;
            int[][] got = path.toArray(new int[0][]);
            return whole(p, lo, up, got) ? got : null;
        }
        for (int t = 0; t < 3; t++) {
            int[] nd = t == 0 ? dir : t == 1 ? new int[]{-dir[1], dir[0]} : new int[]{dir[1], -dir[0]};
            if (t > 0 && (turns >= 2 || k < 1)) continue;
            int nu = at[0] + nd[0], nv = at[1] + nd[1];
            if (!p.in(nu, nv) || contains(path, nu, nv)) continue;
            path.add(new int[]{nu, nv});
            int[][] got = grow(p, lo, up, path, nd, rise, turns + (t > 0 ? 1 : 0));
            if (got != null) return got;
            path.remove(path.size() - 1);
        }
        return null;
    }

    /**
     * Whether a flight leaves both its floors whole: below, every open cell still reached from the ways in round the
     * steps; above, every open cell reached from the stairhead round the opening.
     */
    private boolean whole(Plan p, Storey lo, Storey up, int[][] path) {
        int rise = path.length;
        byte[] keptLo = new byte[rise], keptUp = new byte[rise];
        for (int k = 0; k < rise; k++) { keptLo[k] = lo.g[path[k][0]][path[k][1]]; keptUp[k] = up.g[path[k][0]][path[k][1]]; }
        int openLo = 0, openUp = 0;
        for (int u = 0; u < p.W; u++) for (int v = 0; v < p.D; v++) { if (walk(lo.g[u][v])) openLo++; if (walk(up.g[u][v])) openUp++; }
        int beforeLo = reach(lo, null).size(), groupsLo = groups(lo);
        for (int k = 0; k < rise; k++) lo.g[path[k][0]][path[k][1]] = STAIR;
        int afterLo = reach(lo, null).size();
        boolean joined = groups(lo) <= groupsLo;
        for (int k = 0; k < rise - 1; k++) if (k >= rise - OPEN_OVER) up.g[path[k][0]][path[k][1]] = VOID;
        List<int[]> saved = new ArrayList<>(up.entries);
        up.entries.clear(); up.entries.add(path[rise - 1]);
        int afterUp = reach(up, null).size();
        // what the stairhead reaches with the floor left whole (pockets it never reached do not count against it)
        for (int k = 0; k < rise - 1; k++) up.g[path[k][0]][path[k][1]] = keptUp[k];
        int baseUp = reach(up, null).size();
        if (baseUp * 2 < openUp) { up.entries.clear(); up.entries.addAll(saved); for (int k = 0; k < rise; k++) lo.g[path[k][0]][path[k][1]] = keptLo[k]; return false; }
        int voided = 0;
        for (int k = 0; k < rise - 1; k++) if (k >= rise - OPEN_OVER && walk(keptUp[k])) voided++;
        up.entries.clear(); up.entries.addAll(saved);
        for (int k = 0; k < rise; k++) { lo.g[path[k][0]][path[k][1]] = keptLo[k]; up.g[path[k][0]][path[k][1]] = keptUp[k]; }
        int stepsLo = 0;
        for (int k = 0; k < rise; k++) if (walk(keptLo[k])) stepsLo++;
        return joined && afterLo >= beforeLo - stepsLo && afterUp >= baseUp - voided;
    }

    private static boolean contains(List<int[]> path, int u, int v) { for (int[] q : path) if (q[0] == u && q[1] == v) return true; return false; }

    /** Whether step k of a flight of the given rise may stand at a cell. */
    private boolean stepOk(Plan p, Storey lo, Storey up, int u, int v, int k, int rise) {
        if (!p.in(u, v)) return false;
        int x = p.f.wx(u, v), z = p.f.wz(u, v);
        if (k < rise - 1) {
            // a step on the lower storey: open floor, not a doorway's; the air over it to the slab
            if (lo.g[u][v] != OPEN) return false;
            for (int y = lo.floorY + 1; y < up.floorY; y++) if (!open(c.get(x, y, z))) return false;
            // where the slab is opened over it, the upper storey must have open air there (never cut into a wall)
            if (k >= rise - OPEN_OVER && (up.g[u][v] != OPEN)) return false;
            return true;
        }
        // the last step: in the upper floor, open over it
        return up.g[u][v] == OPEN && solid(c.get(x, up.floorY, z));
    }

    /** How good a flight is: along a wall, away from the doors, toward the back. */
    private double score(Plan p, Storey lo, Storey up, int[][] path) {
        double sc = 0;
        for (int[] q : path) {
            for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int a = q[0] + d[0], b = q[1] + d[1];
                if (!p.in(a, b) || lo.g[a][b] == WALL || lo.g[a][b] == VOID) sc += 2;
            }
            sc += (p.stairFront ? -q[1] : q[1]) * (p.stairFront ? 1.5 : 0.4);
            for (int[] en : lo.entries) if (Math.abs(en[0] - q[0]) + Math.abs(en[1] - q[1]) < 3) sc -= 30;
        }
        // fewer turns read better
        int turns = 0;
        for (int k = 2; k < path.length; k++) if ((path[k][0] - path[k - 1][0]) != (path[k - 1][0] - path[k - 2][0])) turns++;
        return sc - turns * 3;
    }

    /**
     * A ladder where no flight fits: up a wall from the lower floor through a hole in the upper, its top rung in the
     * floor's opening, a cell kept clear at its foot and beside its head. The upper storey then holds nothing a person
     * must reach to live (beds go elsewhere): it is the watch's or the bell's.
     */
    private boolean ladder(Plan p, Storey lo, Storey up) {
        for (int u = 0; u < p.W; u++) for (int v = 0; v < p.D; v++) {
            if (lo.g[u][v] != OPEN || up.g[u][v] != OPEN || wallsBeside(lo, u, v, false).isEmpty()) continue;
            int x = p.f.wx(u, v), z = p.f.wz(u, v);
            boolean clear = true;
            for (int y = lo.floorY + 1; y < up.floorY; y++) if (!open(c.get(x, y, z))) clear = false;
            if (!clear) continue;
            // a foot to stand at below and a cell to step onto above
            int[] foot = null, head = null;
            for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int a = u + d[0], b = v + d[1];
                if (!p.in(a, b)) continue;
                if (foot == null && walk(lo.g[a][b])) foot = new int[]{a, b};
                if (head == null && up.g[a][b] == OPEN) head = new int[]{a, b};
            }
            if (foot == null || head == null) continue;
            for (int y = lo.floorY + 1; y <= up.floorY; y++) c.set(x, y, z, Blocks.LADDER);
            lo.g[u][v] = STAIR; up.g[u][v] = KEEP;
            keep(lo, foot[0], foot[1]); keep(up, head[0], head[1]);
            up.entries.add(new int[]{u, v});
            up.ladder = true;
            return true;
        }
        return false;
    }

    private void build(Plan p, Storey lo, Storey up, int[][] path, Architecture.Fitted fitted) {
        int rise = path.length;
        // the way it is walked (for the stair-walking stress test): foot, steps, head
        {
            int[][] way = new int[rise + 2][];
            int fu = path[0][0] - (path[1][0] - path[0][0]), fv = path[0][1] - (path[1][1] - path[0][1]);
            way[0] = new int[]{p.f.wx(fu, fv), lo.walk(), p.f.wz(fu, fv)};
            for (int k = 0; k < rise; k++) way[k + 1] = new int[]{p.f.wx(path[k][0], path[k][1]), lo.floorY + 1 + k, p.f.wz(path[k][0], path[k][1])};
            int[] last = path[rise - 1], prev = path[rise - 2];
            int hu = last[0] + (last[0] - prev[0]), hv = last[1] + (last[1] - prev[1]);
            if (!(p.in(hu, hv) && walk(up.g[hu][hv]) && !contains(java.util.Arrays.asList(path), hu, hv)))
                for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    int a = last[0] + d[0], b = last[1] + d[1];
                    if (p.in(a, b) && walk(up.g[a][b]) && !contains(java.util.Arrays.asList(path), a, b) && !(a == prev[0] && b == prev[1])) { hu = a; hv = b; break; }
                }
            way[rise + 1] = new int[]{p.f.wx(hu, hv), up.walk(), p.f.wz(hu, hv)};
            fitted.flights.add(way);
        }
        if (java.util.Arrays.asList(DEBUG.split(",")).contains(p.s.id)) { StringBuilder sb = new StringBuilder("stair " + lo.floorY + "->" + up.floorY + ":"); for (int[] q : path) sb.append(" ").append(p.f.wx(q[0], q[1])).append(",").append(p.f.wz(q[0], q[1])); System.out.println(sb); }
        int floorMat = c.get(p.f.wx(path[rise - 1][0], path[rise - 1][1]), up.floorY, p.f.wz(path[rise - 1][0], path[rise - 1][1]));
        if (!solid(floorMat)) floorMat = Blocks.PLANKS;
        for (int k = 0; k < rise; k++) {
            int x = p.f.wx(path[k][0], path[k][1]), z = p.f.wz(path[k][0], path[k][1]);
            int y = lo.floorY + 1 + k;
            // the step, and the stair's body under it
            for (int b = lo.floorY + 1; b < y; b++) c.set(x, b, z, floorMat);
            c.set(x, y, z, Blocks.STAIRS);
            // the floor above opened where a head would strike it
            if (k < rise - 1 && k >= rise - OPEN_OVER) { c.set(x, up.floorY, z, Blocks.AIR); up.g[path[k][0]][path[k][1]] = VOID; }
            if (k < rise - 1) lo.g[path[k][0]][path[k][1]] = STAIR;
            // (0.4.5, gamefixes: a fireplace stood half inside a staircase) the top step's body fills the lower storey's
            // cell under it to the slab: that cell is the stair's, never furnished
            else if (walk(lo.g[path[k][0]][path[k][1]])) lo.g[path[k][0]][path[k][1]] = STAIR;
        }
        // the foot and the head kept clear
        int[] f0 = path[0], f1 = path[1];
        keep(lo, f0[0] - (f1[0] - f0[0]), f0[1] - (f1[1] - f0[1]));
        int[] last = path[rise - 1];
        up.g[last[0]][last[1]] = KEEP;
        up.entries.add(new int[]{last[0], last[1]});
        for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) keep(up, last[0] + d[0], last[1] + d[1]);
        // the rail round the opening, on the upper floor's own cells beside it (never across the head)
        List<int[]> rails = new ArrayList<>();
        for (int k = 0; k < rise - 1; k++) {
            if (up.g[path[k][0]][path[k][1]] != VOID) continue;
            for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int a = path[k][0] + d[0], b = path[k][1] + d[1];
                if (!p.in(a, b) || up.g[a][b] != OPEN || a == last[0] && b == last[1]) continue;
                if (Math.abs(a - last[0]) + Math.abs(b - last[1]) <= 1) continue;
                // a rail only where the floor round the opening stays whole (never shutting the stairhead in)
                int before = reach(up, null).size();
                up.g[a][b] = WALL;
                if (reach(up, null).size() < before - 1) { up.g[a][b] = OPEN; continue; }
                c.set(p.f.wx(a, b), up.floorY + 1, p.f.wz(a, b), Blocks.FENCE);
                rails.add(new int[]{a, b});
            }
        }
        // (0.4.7, gamefixes: railings round stairwells did not join) where two rails meet at the opening's corner - one
        // beside it across, one along - the corner cell between them takes a rail too, so the railing turns unbroken
        for (int pass = 0; pass < 2; pass++) for (int i = 0, n = rails.size(); i < n; i++) for (int j = 0; j < n; j++) {
            int[] r1 = rails.get(i), r2 = rails.get(j);
            if (Math.abs(r1[0] - r2[0]) != 1 || Math.abs(r1[1] - r2[1]) != 1) continue;
            for (int[] cnr : new int[][]{{r1[0], r2[1]}, {r2[0], r1[1]}}) {
                int[] other = cnr[0] == r1[0] ? new int[]{r2[0], r1[1]} : new int[]{r1[0], r2[1]};
                if (!p.in(cnr[0], cnr[1]) || !p.in(other[0], other[1]) || up.g[cnr[0]][cnr[1]] != OPEN || up.g[other[0]][other[1]] != VOID) continue;
                if (Math.abs(cnr[0] - last[0]) + Math.abs(cnr[1] - last[1]) <= 1) continue;
                int before = reach(up, null).size();
                up.g[cnr[0]][cnr[1]] = WALL;
                if (reach(up, null).size() < before - 1) { up.g[cnr[0]][cnr[1]] = OPEN; continue; }
                c.set(p.f.wx(cnr[0], cnr[1]), up.floorY + 1, p.f.wz(cnr[0], cnr[1]), Blocks.FENCE);
                rails.add(cnr);
            }
        }
    }

    // ================================================================== the rooms
    /** A storey divided into its programme's rooms: the first where the way in comes, the rest behind it. */
    private void layout(Plan p, Storey st, List<Map<String, Object>> specs, Architecture.Fitted fitted) {
        int u0 = Integer.MAX_VALUE, v0 = Integer.MAX_VALUE, u1 = -1, v1 = -1;
        for (int u = 0; u < p.W; u++) for (int v = 0; v < p.D; v++) if (st.g[u][v] != VOID) { u0 = Math.min(u0, u); v0 = Math.min(v0, v); u1 = Math.max(u1, u); v1 = Math.max(v1, v); }
        if (u1 < 0) return;
        List<Map<String, Object>> want = new ArrayList<>();
        int minArea = Atlas.i(data, "minRoomArea", 6);
        int area = (u1 - u0 + 1) * (v1 - v0 + 1);
        // a room the programme asks for only above a size is left out of a smaller storey
        for (Map<String, Object> sp : specs) if (area >= Atlas.i(sp, "minStorey", 0)) want.add(sp);
        if (want.isEmpty()) want.add(specs.get(0));
        split(p, st, u0, v0, u1, v1, want, minArea, fitted);
        rooms += st.rooms.size();
    }

    private void split(Plan p, Storey st, int u0, int v0, int u1, int v1, List<Map<String, Object>> specs, int minArea, Architecture.Fitted fitted) {
        Map<String, Object> first = specs.get(0);
        if (specs.size() == 1 || p.round) {
            Room one = new Room(u0, v0, u1, v1, Atlas.s(first, "use", "hall"), first);
            for (Map<String, Object> sp : specs.subList(1, specs.size())) one.merged.add(Atlas.s(sp, "use", "hall"));
            st.rooms.add(one);
            return;
        }
        double total = 0;
        for (Map<String, Object> sp : specs) total += Atlas.d(sp, "share", 1);
        double f0 = Atlas.d(first, "share", 1) / total;
        int w = u1 - u0 + 1, d = v1 - v0 + 1;
        boolean prefV = Atlas.s(first, "axis", d >= w * 0.8 ? "v" : "u").equals("v");
        boolean wantWalls = !Boolean.FALSE.equals(first.get("walls")) && !Boolean.FALSE.equals(specs.get(1).get("walls"));
        // a room behind a door (a vault, a bedroom) is walled or not had at all
        boolean mustWall = !Atlas.s(specs.get(1), "door", "none").equals("none") || !Atlas.s(first, "door", "none").equals("none");
        double tolerance = Atlas.d(data, "shareTolerance", 0.22);
        for (int pass = wantWalls ? 0 : 1; pass < (mustWall ? 1 : 2); pass++) {
            boolean walls = pass == 0;
            for (int strict = 0; strict < 2; strict++) for (boolean alongV : new boolean[]{prefV, !prefV}) {
                if (Atlas.s(first, "axis", null) != null && alongV != prefV) continue;
                int len = alongV ? d : w, across = alongV ? w : d;
                int want = (int) Math.round((len - 1) * f0);
                int a0 = alongV ? v0 : u0, a1 = alongV ? v1 : u1;
                for (int off = 0; off <= len; off++) for (int sgn : new int[]{1, -1}) {
                    if (off == 0 && sgn < 0) continue;
                    int at = a0 + want + sgn * off;
                    if (at - a0 < 2 || a1 - at < (walls ? 2 : 1) || across < 2) continue;
                    // near the share asked (the first round); anywhere it fits (the second)
                    if (strict == 0 && Math.abs((at - a0) / (double) (len - (walls ? 1 : 0)) - f0) > tolerance) continue;
                    if (walls && ((at - a0) * across < minArea || (a1 - at) * across < minArea)) continue;
                    // a room behind a locked door never holds the stairs (nobody but its keyholder could go up)
                    if (Atlas.s(specs.get(1), "door", "").equals("locked") && hasStair(st, alongV ? u0 : at + 1, alongV ? at + 1 : v0, u1, v1)) continue;
                    if (Atlas.s(first, "door", "").equals("locked") && hasStair(st, u0, v0, alongV ? u1 : at - 1, alongV ? at - 1 : v1)) continue;
                    if (walls) { if (partition(p, st, alongV, at, alongV ? u0 : v0, alongV ? u1 : v1, first, specs.get(1)) == null) continue; }
                    else if (!lineClear(st, alongV, at, alongV ? u0 : v0, alongV ? u1 : v1)) continue;
                    Room r = alongV ? new Room(u0, v0, u1, at - 1, Atlas.s(first, "use", "hall"), first) : new Room(u0, v0, at - 1, v1, Atlas.s(first, "use", "hall"), first);
                    r.walled = walls;
                    st.rooms.add(r);
                    List<Map<String, Object>> rest = specs.subList(1, specs.size());
                    int next = walls ? at + 1 : at;
                    if (alongV) split(p, st, u0, next, u1, v1, rest, minArea, fitted);
                    else split(p, st, next, v0, u1, v1, rest, minArea, fitted);
                    return;
                }
            }
        }
        // no room to divide: one room, furnished for every use asked of it
        Room one = new Room(u0, v0, u1, v1, Atlas.s(first, "use", "hall"), first);
        for (Map<String, Object> sp : specs.subList(1, specs.size())) one.merged.add(Atlas.s(sp, "use", "hall"));
        st.rooms.add(one);
    }

    private static boolean hasStair(Storey st, int u0, int v0, int u1, int v1) {
        for (int u = u0; u <= u1; u++) for (int v = v0; v <= v1; v++) if (u >= 0 && v >= 0 && u < st.g.length && v < st.g[0].length && st.g[u][v] == STAIR) return true;
        return false;
    }

    /** Whether a line across a storey is free of stairs and kept cells (a zone's edge needs no wall, only room). */
    private static boolean lineClear(Storey st, boolean alongV, int at, int a0, int a1) {
        return true;
    }

    /**
     * A partition along a line, floor to ceiling in the inner finish, with its doorway (hung with a door where the room
     * behind asks for one). Returns the doorway's cell, or null if the line cannot be walled (it would cut a stair, a
     * doorway's way in, or leave no place for a doorway).
     */
    private int[] partition(Plan p, Storey st, boolean alongV, int at, int a0, int a1, Map<String, Object> front, Map<String, Object> back) {
        // every cell of the line: open floor or already wall, never a stair or a kept cell
        List<Integer> doorAt = new ArrayList<>();
        for (int a = a0; a <= a1; a++) {
            int u = alongV ? a : at, v = alongV ? at : a;
            byte b = st.g[u][v];
            if (b == KEEP) return null;
            if (b == STAIR) continue;
            int bu = alongV ? u : u - 1, bv = alongV ? v - 1 : v, fu = alongV ? u : u + 1, fv = alongV ? v + 1 : v;
            if (b == OPEN && p.in(bu, bv) && p.in(fu, fv) && walk(st.g[bu][bv]) && walk(st.g[fu][fv])) doorAt.add(a);
        }
        if (doorAt.isEmpty()) return null;
        // the doorway: nearest the way in (the middle when there is none)
        int pick = doorAt.get(doorAt.size() / 2);
        if (!st.entries.isEmpty()) {
            int[] en = st.entries.get(0);
            double bd = 1e9;
            for (int a : doorAt) { double dd = alongV ? Math.abs(a - en[0]) : Math.abs(a - en[1]); if (dd < bd) { bd = dd; pick = a; } }
        }
        // tried on the grid first: every walkable cell of the storey must still be reached through its doorway
        byte[] saved = new byte[a1 - a0 + 1];
        int before = reach(st, null).size(), groupsBefore = groups(st);
        int walkLine = 0;
        for (int a = a0; a <= a1; a++) {
            int u = alongV ? a : at, v = alongV ? at : a;
            saved[a - a0] = st.g[u][v];
            if (walk(st.g[u][v]) && a != pick) walkLine++;
            if (st.g[u][v] != VOID && st.g[u][v] != STAIR && a != pick) st.g[u][v] = WALL;
        }
        boolean whole = reach(st, null).size() >= before - walkLine && !st.entries.isEmpty() && groups(st) <= groupsBefore;
        for (int a = a0; a <= a1; a++) { int u = alongV ? a : at, v = alongV ? at : a; st.g[u][v] = saved[a - a0]; }
        if (!whole) return null;
        int wallMat = partitionMat(p);
        int top = st.floorY + Math.min(st.clear, 6);
        String doorKind = Atlas.s(back, "door", "none");
        for (int a = a0; a <= a1; a++) {
            int u = alongV ? a : at, v = alongV ? at : a;
            if (st.g[u][v] == VOID || st.g[u][v] == STAIR) continue;
            int x = p.f.wx(u, v), z = p.f.wz(u, v);
            if (a == pick) {
                // the doorway: two high, a lintel over it in a tall room
                for (int y = st.floorY + 1; y <= st.floorY + 2; y++) c.set(x, y, z, doorKind.equals("none") ? Blocks.AIR : doorKind.equals("locked") ? Blocks.LOCKED_DOOR : Blocks.DOOR);
                for (int y = st.floorY + 3, t = ceilingOver(x, z, st, top); y <= t; y++) if (open(c.get(x, y, z))) c.set(x, y, z, wallMat);
                st.g[u][v] = KEEP;
                if (!doorKind.equals("none")) ar.innerDoors.add(new Architecture.InnerDoor(p.s.id, Atlas.s(back, "doorName", Atlas.s(back, "use", "room")), new int[][]{{x, z}}, st.floorY, doorKind.equals("locked"), Atlas.s(back, "key", "")));
                continue;
            }
            for (int y = st.floorY + 1, t = ceilingOver(x, z, st, top); y <= t; y++) if (open(c.get(x, y, z))) c.set(x, y, z, wallMat);
            st.g[u][v] = WALL;
        }
        // the cells either side of the doorway kept clear
        int du = alongV ? pick : at, dv = alongV ? at : pick;
        if (alongV) { keep(st, du, dv - 1); keep(st, du, dv + 1); } else { keep(st, du - 1, dv); keep(st, du + 1, dv); }
        return new int[]{du, dv};
    }

    /**
     * (0.4.5, gamefixes: the inner walls under a gable stopped short of the roof, a gap of open air over them and a
     * piece of wall hanging in it) how high a partition rises at a column: to the underside of whatever closes it above
     * (a ceiling or the roof's slope), at most 'partitionMax' cells over the floor; where nothing does, to the storey's
     * own clear height.
     */
    private int ceilingOver(int x, int z, Storey st, int fallback) {
        int most = Atlas.i(data, "partitionMax", 16);
        for (int y = st.floorY + 1; y <= st.floorY + most; y++) if (solid(c.get(x, y, z)) && y > st.floorY + 2) return y - 1;
        return fallback;
    }

    // ================================================================== the furniture
    /** A room's own beds (its share of the building's), each by its use's bed piece, before anything else is set in it. */
    private void beds(Storey st, Room r, Architecture.Fitted fitted, int[] bedsLeft, int[] bedRoomsLeft) {
        List<Map<String, Object>> list = new ArrayList<>(Atlas.list(Atlas.map(uses, r.use), "pieces"));
        for (String m : r.merged) list.addAll(Atlas.list(Atlas.map(uses, m), "pieces"));
        boolean counted = false;
        for (Map<String, Object> pc : list) {
            if (!Boolean.TRUE.equals(pc.get("beds"))) continue;
            List<?> tiers = Atlas.arr(pc, "wealth");
            if (!tiers.isEmpty() && !tiers.contains(fitted.wealth)) continue;
            int share = bedRoomsLeft[0] > 0 ? (int) Math.ceil(bedsLeft[0] / (double) bedRoomsLeft[0]) : bedsLeft[0];
            int count = Math.max(Atlas.i(pc, "atLeast", 1), share);
            if (!counted) { bedRoomsLeft[0] = Math.max(0, bedRoomsLeft[0] - 1); counted = true; }
            for (int n = 0; n < count; n++) {
                if (!placeBed(st, r, fitted, Atlas.s(pc, "kind", "BED"))) { if (n == 0 && !Atlas.s(pc, "kind", "BED").equals("BED") && placeBed(st, r, fitted, "BED")) { bedsLeft[0]--; r.solid++; } break; }
                bedsLeft[0]--; r.solid++;
                if (bedsLeft[0] <= 0 && n + 1 >= Atlas.i(pc, "atLeast", 1)) break;
            }
        }
    }

    /** A room furnished for its use: its pieces by the data, as the room and the wealth allow, each placed only if the room stays whole. */
    private void furnish(Storey st, Room r, Architecture.Fitted fitted) {
        Map<String, Object> use = Atlas.map(uses, r.use);
        if (use.isEmpty()) { fitted.defects.add("no furnishing defined for a " + r.use); return; }
        Plan p = st.plan;
        Architecture.RoomRecord rec = new Architecture.RoomRecord(r.use, p.levels.indexOf(st), worldRect(p, r.u0, r.v0, r.u1, r.v1), st.walk(), st.clear, r.walled);
        fitted.rooms.add(rec);
        if (r.bedLit) rec.lit = true;
        int budget = Math.max(2, Math.min(Atlas.i(data, "mostPerRoom", 14), r.area() / Math.max(1, Atlas.i(data, "cellsPerPiece", 5))));
        // the pieces of every use the room serves: its beds first, then what each use needs, then the rest in order
        List<Map<String, Object>> list = new ArrayList<>(Atlas.list(use, "pieces"));
        for (String m : r.merged) list.addAll(Atlas.list(Atlas.map(uses, m), "pieces"));
        List<Map<String, Object>> ordered = new ArrayList<>();
        for (Map<String, Object> pc : list) if (Boolean.TRUE.equals(pc.get("beds"))) ordered.add(pc);
        for (Map<String, Object> pc : list) if (!Boolean.TRUE.equals(pc.get("beds")) && Boolean.TRUE.equals(pc.get("need"))) ordered.add(pc);
        for (Map<String, Object> pc : list) if (!Boolean.TRUE.equals(pc.get("beds")) && !Boolean.TRUE.equals(pc.get("need"))) ordered.add(pc);
        int k = 0;
        for (Map<String, Object> pc0 : ordered) {
            Map<String, Object> pc = pc0;
            k++;
            List<?> tiers = Atlas.arr(pc, "wealth");
            if (!tiers.isEmpty() && !tiers.contains(fitted.wealth)) continue;
            if (pc.containsKey("chance") && Noise.hash(ar.a.seed, p.s.id.hashCode() + st.floorY * 31 + r.u0 * 7 + r.v0, k, 141) > Atlas.d(pc, "chance", 1)) continue;
            if (r.area() < Atlas.i(pc, "minArea", 0) || r.d() < Atlas.i(pc, "minDepth", 0) || r.w() < Atlas.i(pc, "minWidth", 0)) continue;
            String place = Atlas.s(pc, "place", "wall");
            int count = Atlas.i(pc, "count", 1);
            if (Boolean.TRUE.equals(pc.get("beds"))) continue;   // laid already (beds())
            if (pc.containsKey("perArea")) count = Math.max(count, r.area() / Math.max(1, Atlas.i(pc, "perArea", 12)));
            int made = 0;
            for (int n = 0; n < count; n++) {
                boolean solidPiece = !place.equals("hang") && !place.equals("rug") && !place.equals("ceiling");
                if (solidPiece && r.solid >= budget && !Boolean.TRUE.equals(pc.get("need")) && !Boolean.TRUE.equals(pc.get("beds"))) break;
                boolean ok = switch (place) {
                    case "bed" -> placeBed(st, r, fitted, Atlas.s(pc, "kind", "BED"));
                    case "centre" -> placeTable(st, r, fitted, pc);
                    case "counter" -> placeCounter(st, r, fitted, pc);
                    case "rows" -> placeRows(st, r, fitted, pc);
                    case "altar" -> placeAltar(st, r, fitted, pc);
                    case "cells" -> placeCells(st, r, fitted, pc);
                    case "stalls" -> placeStalls(st, r, fitted, pc);
                    case "hang" -> placeHang(st, r, fitted, pc, n);
                    case "rug" -> placeRug(st, r, fitted, pc);
                    case "ceiling" -> placeCeiling(st, r, fitted, pc);
                    case "corner" -> placeWall(st, r, fitted, pc, true);
                    case "trades" -> placeTrades(st, r, fitted, pc);
                    default -> placeWall(st, r, fitted, pc, false);
                };
                // a piece with no room may give way to a smaller one doing the same work (a range for a fireplace)
                if (!ok && n == 0 && !Atlas.map(pc, "fallback").isEmpty()) { pc = Atlas.map(pc, "fallback"); place = Atlas.s(pc, "place", "wall"); ok = placeWall(st, r, fitted, pc, place.equals("corner")); }
                if (!ok) break;
                made++;
            }
            if (made == 0 && Boolean.TRUE.equals(pc0.get("need"))) fitted.defects.add("the " + r.use + " (storey " + p.levels.indexOf(st) + ") has no room for its " + label(pc));
        }
        // every room lit: if its pieces gave it no light, candles in a corner, a chandelier, sconces on its walls, a lamp
        // (0.4.7, gamefixes: chandeliers in every house, some low enough to strike a head) a plain house's room is lit by
        // candles and sconces; a chandelier is a fine house's, a hall's or a great building's, and hangs only
        // where its candles clear a tall man's head ('chandelierMinClear' cells of headroom)
        boolean fine = chandelierWealth().contains(fitted.wealth);
        if (!rec.lit) {
            // a candle stand in a corner or on a wall; a fine house's chandelier; else sconces on the walls (no floor taken
            // from a workshop's anvil or a shop's counter); a nightstand's lamp or a narrow stand; a lamp by the stairs last
            if (!placeWall(st, r, fitted, Map.of("kind", "CANDELABRA", "place", "corner"), true) && !placeWall(st, r, fitted, Map.of("kind", "CANDELABRA"), false)
                    && !(fine && placeCeiling(st, r, fitted, Map.of("kind", "CHANDELIER")))) {
                for (int sc = 0; sc < Math.max(1, Math.min(3, r.area() / Atlas.i(data, "sconcePer", 16))); sc++)
                    if (!placeHang(st, r, fitted, Map.of("kind", "SCONCE", "h", 3), 40 + sc)) break;
                if (!rec.lit && !placeWall(st, r, fitted, Map.of("kind", "NIGHTSTAND"), false) && !placeWall(st, r, fitted, Map.of("kind", "CANDELABRA", "w", 1), false))
                    placeCeiling(st, r, fitted, Map.of("kind", "CHANDELIER", "nearStairs", true));
            }
        }
        if (!rec.lit) fitted.defects.add("the " + r.use + " (storey " + p.levels.indexOf(st) + ") has no light");
        // (0.4.4, review: a great hall or a palace kitchen lit by one candle stand was dark past it) a big room is lit
        // through: a chandelier hung over its middle under a tall ceiling, and a candle stand on its walls for each
        // stretch of floor ('lightPer' cells, at most 'lightsMost'), whatever its hearth or forge already gives
        int per = Atlas.i(data, "lightPer", 24), most = Atlas.i(data, "lightsMost", 4);
        if (r.area() >= per * 2 && fine) placeCeiling(st, r, fitted, Map.of("kind", "CHANDELIER"));
        for (int lamp = 0, extra = Math.min(most, r.area() / per); lamp < extra; lamp++)
            if (!placeWall(st, r, fitted, Map.of("kind", "CANDELABRA", "place", "corner"), true)) break;   // corners only: never narrowing a way through
        coverLight(st, r, fitted);
    }

    /**
     * (0.4.8, gamefixes: the Drunken Duck's interior at 324, 40, 994 too dark) a room lit through, not merely lit: no
     * floor of it further than 'lightReach' cells from a room's light (a candle stand, a chandelier, a sconce; a hearth
     * counts for less, being lit only while it burns, and a bedside or desk lamp not at all). Where the darkest floor is
     * still out of reach a sconce goes on the wall nearest it, up to 'lightsAdded' more.
     */
    private void coverLight(Storey st, Room r, Architecture.Fitted fitted) {
        Plan p = st.plan;
        int reach = Atlas.i(data, "lightReach", 5), most = Atlas.i(data, "lightsAdded", 6);
        for (int added = 0; added <= most; added++) {
            List<float[]> lamps = new ArrayList<>();
            for (Architecture.Piece pc : ar.pieces) {
                if (!pc.owner().equals(p.s.id) || pc.y() != st.walk()) continue;
                float r0 = switch (pc.kind()) { case "CHANDELIER" -> reach * 1.6f; case "CANDELABRA", "SCONCE" -> reach; case "FIREPLACE" -> reach * 0.6f; default -> 0; };
                if (r0 > 0) lamps.add(new float[]{(pc.x0() + pc.x1() + 1) / 2f, (pc.z0() + pc.z1() + 1) / 2f, r0});
            }
            float worst = 0; int du = -1, dv = -1;
            for (int u = r.u0; u <= r.u1; u++) for (int v = r.v0; v <= r.v1; v++) {
                if (!r.holds(u, v) || !walk(st.g[u][v]) && st.g[u][v] != FURN) continue;
                float x = p.f.wx(u, v) + 0.5f, z = p.f.wz(u, v) + 0.5f, dark = Float.MAX_VALUE;
                for (float[] l : lamps) dark = Math.min(dark, (float) Math.hypot(x - l[0], z - l[1]) - l[2]);
                if (dark > worst) { worst = dark; du = u; dv = v; }
            }
            if (du < 0) return;                         // every floor in reach
            if (added == most || !placeHang(st, r, fitted, Map.of("kind", "SCONCE", "h", 3, "nearU", du, "nearV", dv), 60 + added)) {
                fitted.defects.add("the " + r.use + " (storey " + p.levels.indexOf(st) + ") is dark " + String.format("%.0f", worst) + " cells past its lights");
                return;
            }
        }
    }

    private static String label(Map<String, Object> pc) {
        if (pc.containsKey("kind")) return Atlas.s(pc, "kind", "?").toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        if (pc.containsKey("station")) return Atlas.s(pc, "station", "?").toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        return Atlas.s(pc, "fitting", Atlas.s(pc, "place", "?"));
    }

    private int[] worldRect(Plan p, int u0, int v0, int u1, int v1) {
        int ax = p.f.wx(u0, v0), az = p.f.wz(u0, v0), bx = p.f.wx(u1, v1), bz = p.f.wz(u1, v1);
        return new int[]{Math.min(ax, bx), Math.min(az, bz), Math.max(ax, bx), Math.max(az, bz)};
    }

    /** The facing (quarter turns from +z) toward a local direction. */
    private static int facing(Plan p, int du, int dv) {
        int[] d = p.f.dir(du, dv);
        return d[1] > 0 ? 0 : d[0] > 0 ? 1 : d[1] < 0 ? 2 : 3;
    }

    /**
     * (0.4.7) What a building's inner walls are built of: its territory's auto-faced walling where it has one (its interior
     * finish on both sides), else the inside finish of its walls (plaster for timber framing).
     */
    private static int partitionMat(Plan p) {
        int wall = p.s.style.b("wall"), auto = Blocks.autoOf(wall);
        if (auto > 0) return auto;
        int mat = BuildingKit.innerFinish(wall);
        return mat == Blocks.TIMBER_FRAME ? Blocks.PLASTER : mat;
    }

    /** (0.4.7, gamefixes: paintings hung on doors) a door's or a gate's cell is never a wall to hang or stand against. */
    private static boolean doorway(int block) { return block == Blocks.DOOR || block == Blocks.LOCKED_DOOR || block == Blocks.AIR; }

    /** The walls a cell of a room stands against (local directions toward solid wall, not glass), in order. */
    private List<int[]> wallsBeside(Storey st, int u, int v, boolean tall) {
        Plan p = st.plan;
        List<int[]> out = new ArrayList<>();
        for (int[] d : new int[][]{{0, 1}, {1, 0}, {-1, 0}, {0, -1}}) {
            int a = u + d[0], b = v + d[1];
            boolean wall;
            if (!p.in(a, b)) {
                int x = p.f.wx(u, v) + p.f.dir(d[0], d[1])[0], z = p.f.wz(u, v) + p.f.dir(d[0], d[1])[1];
                int at = c.get(x, st.walk(), z), over = c.get(x, st.walk() + 1, z);
                wall = solid(at) && solid(over) && !Blocks.glass(at) && (!tall || !Blocks.glass(over));
                if (at == Blocks.FENCE || doorway(at) || doorway(over)) wall = false;
            } else {
                if (st.g[a][b] != WALL) continue;
                int x = p.f.wx(a, b), z = p.f.wz(a, b);
                int at = c.get(x, st.walk(), z), over = c.get(x, st.walk() + 1, z);
                wall = solid(at) && solid(over) && !Blocks.glass(at) && at != Blocks.FENCE && !doorway(at) && !doorway(over) && (!tall || !Blocks.glass(over));
            }
            if (wall) out.add(d);
        }
        return out;
    }

    /**
     * A piece against a wall: its back to it, w cells along it and d out from it, h tall. A corner piece wants two walls.
     * The cell before its front is kept clear (a cupboard is opened, a station worked, from there).
     */
    private boolean placeWall(Storey st, Room r, Architecture.Fitted fitted, Map<String, Object> pc, boolean corner) {
        Plan p = st.plan;
        int w = Atlas.i(pc, "w", 1), dd = Atlas.i(pc, "d", 1), h = Atlas.i(pc, "h", 1);
        if (st.clear < h) h = st.clear;
        List<int[]> cand = new ArrayList<>();
        for (int u = r.u0; u <= r.u1; u++) for (int v = r.v0; v <= r.v1; v++) {
            if (st.g[u][v] != OPEN) continue;
            List<int[]> ws = wallsBeside(st, u, v, h >= 2);
            if (ws.isEmpty() || corner && ws.size() < 2) continue;
            for (int[] wd : ws) cand.add(new int[]{u, v, wd[0], wd[1]});
        }
        // spread round the room: start where the hash says, take the first that fits
        if (cand.isEmpty()) return false;
        int start = (int) (Noise.hash(ar.a.seed, p.s.id.hashCode() + r.u0 * 13 + r.v0, st.floorY + fitted.pieces.size(), 142) * cand.size());
        for (int t = 0; t < cand.size(); t++) {
            int[] q = cand.get(pick(start, t, 7, cand.size()));
            int wu = q[2], wv = q[3];
            // along the wall: (-wv, wu) ; out from it: (-wu, -wv)
            int au = -wv, av = wu;
            List<int[]> cells = new ArrayList<>();
            boolean fits = true;
            for (int i = 0; i < w && fits; i++) for (int j = 0; j < dd && fits; j++) {
                int u = q[0] + au * i - wu * j, v = q[1] + av * i - wv * j;
                if (!r.holds(u, v) || st.g[u][v] != OPEN) fits = false;
                else if (j == 0 && i > 0 && wallsBeside(st, u, v, h >= 2).stream().noneMatch(x -> x[0] == wu && x[1] == wv)) fits = false;
                else cells.add(new int[]{u, v});
            }
            if (!fits) continue;
            // (0.4.5) a tall piece never stands in front of a wall something already hangs on
            if (h >= 2) { boolean covers = false; for (int[] cc : cells) if (st.hung.contains(cc[0] + "," + cc[1] + "," + wu + "," + wv)) covers = true; if (covers) continue; }
            // the cell(s) before its front
            List<int[]> front = new ArrayList<>();
            for (int i = 0; i < w; i++) front.add(new int[]{q[0] + au * i - wu * dd, q[1] + av * i - wv * dd});
            boolean useFront = !Boolean.FALSE.equals(pc.get("front"));
            if (useFront) { boolean any = false; for (int[] fc : front) if (p.in(fc[0], fc[1]) && walk(st.g[fc[0]][fc[1]])) any = true; if (!any) continue; }
            if (!commit(st, cells, useFront ? front.subList(0, 1) : List.of())) continue;
            int fac = facing(p, wu, wv);
            String kind = Atlas.s(pc, "kind", null), station = Atlas.s(pc, "station", null), fitting = Atlas.s(pc, "fitting", null);
            int[] rect = rectOf(p, cells);
            if (station != null) {
                int x = rect[0], z = rect[1];
                c.set(x, st.walk(), z, stationBlock(station));
                int[] fc = front.get(0);
                ar.fittings.add(new Architecture.Fitting(p.s.id, "station:" + station, x + 0.5f, st.walk(), z + 0.5f, fac, Atlas.s(pc, "tag", ""), p.f.wx(fc[0], fc[1]) + 0.5f, p.f.wz(fc[0], fc[1]) + 0.5f));
                fitted.stations.add(station.toLowerCase(java.util.Locale.ROOT));
            } else if (fitting != null) {
                int x = rect[0], z = rect[1];
                int[] fc = front.get(0);
                ar.fittings.add(new Architecture.Fitting(p.s.id, fitting, x + 0.5f, st.walk(), z + 0.5f, fac, Atlas.s(pc, "tag", ""), p.f.wx(fc[0], fc[1]) + 0.5f, p.f.wz(fc[0], fc[1]) + 0.5f));
                // a chest's cell: solid, drawn by the game
                c.set(x, st.walk(), z, Blocks.CRATE);
                fitted.fittings.add(fitting);
            } else {
                piece(p, st, fitted, kind, rect, h, fac, Atlas.s(pc, "tag", ""));
                if (Atlas.s(pc, "post", null) != null) {
                    int[] fc = front.get(0);
                    ar.fittings.add(new Architecture.Fitting(p.s.id, "post:" + Atlas.s(pc, "post", ""), p.f.wx(fc[0], fc[1]) + 0.5f, st.walk(), p.f.wz(fc[0], fc[1]) + 0.5f, facing(p, wu, wv) ^ 2, "", 0, 0));
                }
            }
            r.solid++;
            if (light(kind)) fitted.rooms.get(fitted.rooms.size() - 1).lit = true;
            return true;
        }
        return false;
    }

    /**
     * The stations the people working here need for their callings (the census's 'stations' by calling: a smith's anvil and
     * furnace, a cook's range, the cloth trades' benches, wheels, looms, tubs and vats), one of each kind, against the
     * walls with room to work before each; a workshop nobody's calling names gets a crafting table.
     */
    private boolean placeTrades(Storey st, Room r, Architecture.Fitted fitted, Map<String, Object> pc) {
        Map<String, Object> table = Atlas.map(ar.a.map("census"), "stations");
        java.util.LinkedHashSet<String> want = new java.util.LinkedHashSet<>();
        for (String prof : trades.getOrDefault(st.plan.s.id, List.of())) for (Object o : Atlas.arr(table, prof)) want.add(String.valueOf(o));
        if (want.isEmpty()) for (Object o : Atlas.arr(pc, "default")) want.add(String.valueOf(o));
        if (want.isEmpty()) want.add("CRAFT_TABLE");
        boolean any = false;
        for (String k : want) {
            if (fitted.stations.contains(k.toLowerCase(java.util.Locale.ROOT))) { any = true; continue; }
            if (placeWall(st, r, fitted, Map.of("station", k, "post", "station:" + k), false)) any = true;
            else fitted.defects.add("no room in the " + r.use + " for its " + k.toLowerCase(java.util.Locale.ROOT).replace('_', ' '));
        }
        return any;
    }

    private static boolean light(String kind) { return kind != null && (kind.equals("CANDELABRA") || kind.equals("CHANDELIER") || kind.equals("DESK") || kind.equals("NIGHTSTAND") || kind.equals("FIREPLACE") || kind.equals("SCONCE")); }

    private static int stationBlock(String station) {
        return switch (station) { case "FURNACE" -> Blocks.FURNACE; case "ANVIL" -> Blocks.ANVIL; case "RANGE" -> Blocks.RANGE; case "CRAFT_TABLE" -> Blocks.CRAFT_TABLE; case "SAWBENCH" -> Blocks.SAWBENCH; default -> Blocks.TABLE; };
    }

    /** The world rectangle {x0, z0, x1, z1} of some local cells. */
    private int[] rectOf(Plan p, List<int[]> cells) {
        int x0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, z1 = Integer.MIN_VALUE;
        for (int[] q : cells) { int x = p.f.wx(q[0], q[1]), z = p.f.wz(q[0], q[1]); x0 = Math.min(x0, x); z0 = Math.min(z0, z); x1 = Math.max(x1, x); z1 = Math.max(z1, z); }
        return new int[]{x0, z0, x1, z1};
    }

    /** A piece of furniture recorded, its cells set to its kind's block (a hung or lying piece takes none). */
    private void piece(Plan p, Storey st, Architecture.Fitted fitted, String kind, int[] rect, int h, int facing, String tag) {
        int block = furnitureBlock(kind);
        if (block != Blocks.AIR) for (int x = rect[0]; x <= rect[2]; x++) for (int z = rect[1]; z <= rect[3]; z++) for (int y = 0; y < Math.max(1, Math.min(h, 2)); y++) c.set(x, st.walk() + y, z, block);
        ar.pieces.add(new Architecture.Piece(p.s.id, kind, rect[0], rect[1], rect[2], rect[3], st.walk(), h, facing, tag));
        fitted.pieces.add(kind.toLowerCase(java.util.Locale.ROOT));
        pieces++;
    }

    /** The block a kind of furniture stands in (rpg.objects.Furniture.Kind's): solid pieces only. */
    static int furnitureBlock(String kind) {
        return switch (kind) {
            case "TABLE", "COUNTER", "DINING_TABLE", "DESK", "WARDROBE", "DRESSER", "NIGHTSTAND", "SIDEBOARD", "CLOCK", "CUPBOARD", "SHELVES", "WINE_RACK",
                 "BATHTUB", "WASHSTAND", "CANDELABRA", "ARMOR_STAND", "PLANTER" -> Blocks.TABLE;
            case "BED", "FOURPOSTER", "SLAB" -> Blocks.BED;
            case "CRATE" -> Blocks.CRATE;
            case "BOOKCASE" -> Blocks.BOOKCASE;
            case "BENCH", "CHAIR", "ARMCHAIR", "SOFA", "STOOL" -> Blocks.SEAT;
            case "FIREPLACE", "HEADSTONE", "BUST" -> Blocks.HEADSTONE;
            case "TARGET" -> Blocks.TARGET;
            default -> Blocks.AIR;
        };
    }

    /**
     * Takes cells for a solid piece if the storey stays whole: every walkable cell it had still reachable from its ways
     * in (but the ones taken), and every cell some piece is used from still reachable. The use cells given are kept.
     */
    private boolean commit(Storey st, List<int[]> cells, List<int[]> useCells) {
        for (int[] q : cells) if (st.g[q[0]][q[1]] != OPEN) return false;
        for (int[] q : useCells) if (!st.plan.in(q[0], q[1]) || !walk(st.g[q[0]][q[1]])) return false;
        int before = reach(st, null).size();
        byte[] old = new byte[cells.size()];
        for (int i = 0; i < cells.size(); i++) { old[i] = st.g[cells.get(i)[0]][cells.get(i)[1]]; st.g[cells.get(i)[0]][cells.get(i)[1]] = FURN; }
        Set<Long> now = reach(st, null);
        // (a counter is meant to part its room into the staff's side and the customers': furniture is held only to the
        // reach of the floor, not to the ways in staying joined - the stairs and the partitions are)
        boolean whole = now.size() >= before - cells.size();
        for (int[] q : useCells) if (!now.contains(key(q[0], q[1]))) whole = false;
        for (long u : st.useCells()) if (!now.contains(u)) whole = false;
        if (!whole) { for (int i = 0; i < cells.size(); i++) st.g[cells.get(i)[0]][cells.get(i)[1]] = old[i]; return false; }
        for (int[] q : useCells) { keep(st, q[0], q[1]); st.use.add(key(q[0], q[1])); }
        return true;
    }

    private static long key(int u, int v) { return (long) u << 32 | (v & 0xffffffffL); }

    /**
     * The t-th candidate of n tried, spread through them from a start: every candidate exactly once (a stride that shares
     * a factor with n would revisit a few and never reach the rest).
     */
    static int pick(int start, int t, int stride, int n) {
        int s = stride;
        while (gcd(s, n) != 1) s++;
        return (int) Math.floorMod(start + (long) t * s, (long) n);
    }
    private static int gcd(int a, int b) { while (b != 0) { int t = a % b; a = b; b = t; } return Math.abs(a); }

    /** The walkable cells reachable from a storey's ways in. */
    private Set<Long> reach(Storey st, int[] extra) {
        Set<Long> seen = new HashSet<>();
        ArrayDeque<int[]> q = new ArrayDeque<>();
        for (int[] en : st.entries) if (st.plan.in(en[0], en[1]) && walk(st.g[en[0]][en[1]]) && seen.add(key(en[0], en[1]))) q.add(en);
        while (!q.isEmpty()) {
            int[] a = q.poll();
            for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int u = a[0] + d[0], v = a[1] + d[1];
                if (!st.plan.in(u, v) || !walk(st.g[u][v]) || !seen.add(key(u, v))) continue;
                q.add(new int[]{u, v});
            }
        }
        return seen;
    }

    /**
     * (0.4.6, gamefixes: a church's stair filled the tower's only way through to the nave) how many of a storey's openings
     * into the next volume (an arch from a tower into its nave) no door's floor reaches: a flight or a partition that
     * leaves every cell reached but cuts the way through from the door to the opening is no longer taken as leaving the
     * floor whole (a stair that parts two doors of a house, each half reached from its own, still is).
     */
    private int groups(Storey st) {
        // the openings into the next volume no door's floor reaches (a flight or a wall that cuts the way through)
        Set<Long> seen = new HashSet<>();
        ArrayDeque<int[]> q = new ArrayDeque<>();
        for (int[] en : st.entries) if (!st.openings.contains(key(en[0], en[1])) && st.plan.in(en[0], en[1]) && walk(st.g[en[0]][en[1]]) && seen.add(key(en[0], en[1]))) q.add(en);
        if (q.isEmpty()) return 0;
        while (!q.isEmpty()) {
            int[] a = q.poll();
            for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int u = a[0] + d[0], v = a[1] + d[1];
                if (st.plan.in(u, v) && walk(st.g[u][v]) && seen.add(key(u, v))) q.add(new int[]{u, v});
            }
        }
        int n = 0;
        for (int[] en : st.entries) if (st.openings.contains(key(en[0], en[1])) && !seen.contains(key(en[0], en[1]))) n++;
        return n;
    }

    /** A bed: its head to a wall, one wide and two long, its flank kept clear (where its sleeper gets in). */
    private boolean placeBed(Storey st, Room r, Architecture.Fitted fitted, String kind) {
        Plan p = st.plan;
        boolean four = kind.equals("FOURPOSTER");
        int width = four ? 2 : 1;
        List<int[]> cand = new ArrayList<>();
        for (int u = r.u0; u <= r.u1; u++) for (int v = r.v0; v <= r.v1; v++) if (st.g[u][v] == OPEN) for (int[] wd : wallsBeside(st, u, v, false)) cand.add(new int[]{u, v, wd[0], wd[1]});
        int start = (int) (Noise.hash(ar.a.seed, p.s.id.hashCode() + r.u0, r.v0 + fitted.pieces.size(), 143) * Math.max(1, cand.size()));
        for (int t = 0; t < cand.size(); t++) {
            int[] q = cand.get(pick(start, t, 5, cand.size()));
            int wu = q[2], wv = q[3], au = -wv, av = wu;
            List<int[]> cells = new ArrayList<>();
            for (int i = 0; i < width; i++) for (int j = 0; j < 2; j++) cells.add(new int[]{q[0] + au * i - wu * j, q[1] + av * i - wv * j});
            boolean ok = true;
            for (int[] cc : cells) if (!r.holds(cc[0], cc[1]) || st.g[cc[0]][cc[1]] != OPEN) ok = false;
            if (!ok) continue;
            // the flank: beside the bed's foot half, on either side
            int[] side1 = {q[0] - au - wu, q[1] - av - wv}, side2 = {q[0] + au * width - wu, q[1] + av * width - wv};
            int[] flank = p.in(side1[0], side1[1]) && walk(st.g[side1[0]][side1[1]]) ? side1 : side2;
            if (!commit(st, cells, List.of(flank))) continue;
            int[] rect = rectOf(p, cells);
            String tag = Atlas.s(Atlas.map(uses, r.use), "bedTag", "bed");
            piece(p, st, fitted, kind, rect, 1, facing(p, wu, wv), tag);
            fitted.beds++;
            r.solid++;
            // a bedside table at its head, where the wall goes on
            int[] ns = {q[0] + au * width, q[1] + av * width};
            if (r.holds(ns[0], ns[1]) && st.g[ns[0]][ns[1]] == OPEN && !fitted.wealth.equals("poor") && commit(st, List.of(ns), List.of())) {
                piece(p, st, fitted, "NIGHTSTAND", new int[]{p.f.wx(ns[0], ns[1]), p.f.wz(ns[0], ns[1]), p.f.wx(ns[0], ns[1]), p.f.wz(ns[0], ns[1])}, 1, facing(p, wu, wv), "");
                r.bedLit = true;
            }
            return true;
        }
        return false;
    }

    /** A table in the room's middle with its seats round it (chairs at a fine house's, benches or stools at a plain one's). */
    private boolean placeTable(Storey st, Room r, Architecture.Fitted fitted, Map<String, Object> pc) {
        Plan p = st.plan;
        String kind = Atlas.s(pc, "kind", "TABLE"), seat = Atlas.s(pc, "seats", "STOOL");
        int tw = Atlas.i(pc, "w", 2), td = Atlas.i(pc, "d", 1);
        // lengthwise along the room's longer side
        boolean alongU = r.w() >= r.d();
        int lu = alongU ? tw : td, lv = alongU ? td : tw;
        if (r.w() < lu + 2 || r.d() < lv + 2) { lu = Math.min(lu, Math.max(1, r.w() - 2)); lv = Math.min(lv, Math.max(1, r.d() - 2)); }
        if (lu < 1 || lv < 1) return false;
        int cu = r.u0 + (r.w() - lu) / 2, cv = r.v0 + (r.d() - lv) / 2;
        for (int off = 0; off <= Math.max(r.w(), r.d()); off++) for (int[] o : new int[][]{{0, 0}, {off, 0}, {-off, 0}, {0, off}, {0, -off}, {off, off}, {-off, -off}, {off, -off}, {-off, off}}) {
            if (off == 0 && (o[0] != 0 || o[1] != 0)) continue;
            int u0 = cu + o[0], v0 = cv + o[1];
            List<int[]> cells = new ArrayList<>();
            boolean ok = true;
            for (int a = 0; a < lu && ok; a++) for (int b = 0; b < lv && ok; b++) { int u = u0 + a, v = v0 + b; if (!r.holds(u, v) || st.g[u][v] != OPEN) ok = false; else cells.add(new int[]{u, v}); }
            if (!ok) continue;
            // the ring round it must be in the room and open (seats and the walk round them)
            for (int u = u0 - 1; u <= u0 + lu && ok; u++) for (int v = v0 - 1; v <= v0 + lv && ok; v++) if (!r.holds(u, v) || !walk(st.g[u][v])) ok = false;
            if (!ok) continue;
            if (!commit(st, cells, List.of())) continue;
            int[] rect = rectOf(p, cells);
            // a table people eat at is laid (bowls, cups, bread, a candle); a workroom's or a records room's is not
            piece(p, st, fitted, kind, rect, 1, alongU ? facing(p, 0, 1) : facing(p, 1, 0), Atlas.arr(data, "laidTables").contains(r.use) ? "laid" : "");
            r.solid++;
            // seats along the long sides (a bench the table's length, or a chair or stool to each cell), where the room stays whole
            int seats = 0;
            for (int side : new int[]{-1, 1}) {
                List<int[]> line = new ArrayList<>();
                for (int a = 0; a < (alongU ? lu : lv); a++) {
                    int u = alongU ? u0 + a : (side < 0 ? u0 - 1 : u0 + lu), v = alongU ? (side < 0 ? v0 - 1 : v0 + lv) : v0 + a;
                    line.add(new int[]{u, v});
                }
                // (0.4.7, gamefixes: chairs faced away from their tables) a piece's facing is the side its back is on
                // (Furnishings: front toward -z at facing 0), so a seat's back is away from the table, its front to it
                int fac = alongU ? facing(p, 0, side) : facing(p, side, 0);
                if (seat.equals("BENCH")) {
                    // its far side from the table walked past
                    if (commit(st, line, List.of())) { piece(p, st, fitted, "BENCH", rectOf(p, line), 1, fac, ""); seats += line.size(); r.solid++; }
                } else for (int[] q : line) if (commit(st, List.of(q), List.of())) { piece(p, st, fitted, seat, rectOf(p, List.of(q)), 1, fac, ""); seats++; r.solid++; }
            }
            // a chair at each end too, at a dining table
            if (seat.equals("CHAIR")) for (int end : new int[]{-1, 1}) {
                int u = alongU ? (end < 0 ? u0 - 1 : u0 + lu) : u0 + (lu - 1) / 2, v = alongU ? v0 + (lv - 1) / 2 : (end < 0 ? v0 - 1 : v0 + lv);
                if (r.holds(u, v) && st.g[u][v] == OPEN && commit(st, List.of(new int[]{u, v}), List.of())) { piece(p, st, fitted, "CHAIR", rectOf(p, List.of(new int[]{u, v})), 1, alongU ? facing(p, end, 0) : facing(p, 0, end), ""); r.solid++; }
            }
            if (Atlas.s(pc, "post", null) != null) {
                int[] q = {u0 - (alongU ? 0 : 1), v0 - (alongU ? 1 : 0)};
                ar.fittings.add(new Architecture.Fitting(p.s.id, "post:" + Atlas.s(pc, "post", ""), p.f.wx(q[0], q[1]) + 0.5f, st.walk(), p.f.wz(q[0], q[1]) + 0.5f, 0, "", 0, 0));
            }
            fitted.seats += seats;
            return true;
        }
        return false;
    }

    /**
     * A counter across the room (a shop's, a bank's, a bar's): along u at a depth into the room, a gap at one end to come
     * round by; the staff's side behind it, the customers' before it. Its posts and customer places are recorded.
     */
    private boolean placeCounter(Storey st, Room r, Architecture.Fitted fitted, Map<String, Object> pc) {
        Plan p = st.plan;
        if (r.w() < 3 || r.d() < 3) return false;
        double depth = Atlas.d(pc, "depth", 0.55);
        int dv0 = r.v0 + Math.max(1, Math.min(r.d() - 3, (int) Math.round((r.d() - 1) * depth)));
        for (int off = 0; off < r.d(); off++) for (int sgn : new int[]{1, -1}) {
            int v = dv0 + sgn * off;
            if (off == 0 && sgn < 0) continue;
            if (v - r.v0 < 1 || r.v1 - v < 1) continue;
            // the run: from one wall, leaving a gap of one cell at the end nearest the way in... the gap at the side
            for (int gapAtEnd : new int[]{1, 0}) {
                int u0 = gapAtEnd == 1 ? r.u0 : r.u0 + 1, u1 = gapAtEnd == 1 ? r.u1 - 1 : r.u1;
                List<int[]> cells = new ArrayList<>();
                boolean ok = u1 - u0 + 1 >= 2;
                for (int u = u0; u <= u1 && ok; u++) { if (st.g[u][v] != OPEN) ok = false; else cells.add(new int[]{u, v}); }
                int gu = gapAtEnd == 1 ? r.u1 : r.u0;
                if (!ok || !walk(st.g[gu][v])) continue;
                // staff and customer places: behind and before its middle
                int mu = (u0 + u1) / 2;
                int[] staffAt = {mu, v + 1}, custAt = {mu, v - 1};
                if (!commit(st, cells, List.of(staffAt, custAt, new int[]{gu, v}))) continue;
                int[] rect = rectOf(p, cells);
                piece(p, st, fitted, Atlas.s(pc, "kind", "COUNTER"), rect, 1, facing(p, 0, -1), Atlas.s(pc, "tag", ""));
                r.solid++;
                String post = Atlas.s(pc, "post", "counter");
                int n = Math.max(1, Atlas.i(pc, "posts", Math.max(1, (u1 - u0 + 1) / 3)));
                for (int k = 0; k < n; k++) {
                    int pu = u0 + (int) Math.round((u1 - u0) * (n == 1 ? 0.5 : k / (double) (n - 1)));
                    if (!walk(st.g[pu][v + 1]) || !walk(st.g[pu][v - 1])) continue;
                    ar.fittings.add(new Architecture.Fitting(p.s.id, "post:" + post, p.f.wx(pu, v + 1) + 0.5f, st.walk(), p.f.wz(pu, v + 1) + 0.5f, facing(p, 0, -1), Atlas.s(pc, "tag", ""),
                            p.f.wx(pu, v - 1) + 0.5f, p.f.wz(pu, v - 1) + 0.5f));
                    keep(st, pu, v + 1); keep(st, pu, v - 1);
                    st.use.add(key(pu, v + 1)); st.use.add(key(pu, v - 1));
                }
                fitted.fittings.add("counter");
                return true;
            }
        }
        return false;
    }

    /** Rows of benches (a church's pews, a hall's forms) across the room, an aisle down its middle, all facing its far end. */
    private boolean placeRows(Storey st, Room r, Architecture.Fitted fitted, Map<String, Object> pc) {
        Plan p = st.plan;
        int aisle = r.u0 + r.w() / 2, edge = r.w() >= 7 ? 1 : 0, most = Atlas.i(pc, "most", 5);
        int rowsMade = 0;
        int startV = r.v0 + Atlas.i(pc, "from", 2), endV = r.v1 - Atlas.i(pc, "to", 3);
        for (int v = startV; v <= endV; v += 2) {
            for (int side : new int[]{-1, 1}) {
                int ua = side < 0 ? Math.max(r.u0 + edge, aisle - most) : aisle + 1, ub = side < 0 ? aisle - 1 : Math.min(r.u1 - edge, aisle + most);
                List<int[]> line = new ArrayList<>();
                for (int u = ua; u <= ub; u++) line.add(new int[]{u, v});
                if (line.size() < 2 || !commit(st, line, List.of())) continue;
                piece(p, st, fitted, Atlas.s(pc, "kind", "BENCH"), rectOf(p, line), 1, facing(p, 0, 1), "pew");
                fitted.seats += line.size();
                rowsMade++;
                r.solid++;
            }
        }
        return rowsMade > 0;
    }

    /** An altar at the room's far end in the middle, a step clear before it (where the faithful kneel), a post behind it. */
    private boolean placeAltar(Storey st, Room r, Architecture.Fitted fitted, Map<String, Object> pc) {
        Plan p = st.plan;
        int u = r.u0 + r.w() / 2;
        for (int v = r.v1; v >= r.v0 + 1; v--) {
            if (st.g[u][v] != OPEN || !walk(st.g[u][v - 1])) continue;
            int[] behind = {u, v + 1};
            boolean priest = p.in(behind[0], behind[1]) && walk(st.g[behind[0]][behind[1]]);
            if (!commit(st, List.of(new int[]{u, v}), List.of(new int[]{u, v - 1}))) continue;
            int x = p.f.wx(u, v), z = p.f.wz(u, v);
            String what = Atlas.s(pc, "fitting", "altar"), seat = Atlas.s(pc, "kind", null);
            if (seat != null) piece(p, st, fitted, seat, new int[]{x, z, x, z}, 1, facing(p, 0, 1), what);
            else c.set(x, st.walk(), z, p.s.style.b("accent") == Blocks.AIR || Blocks.glass(p.s.style.b("accent")) ? Blocks.MARBLE : p.s.style.b("accent"));
            ar.fittings.add(new Architecture.Fitting(p.s.id, what, x + 0.5f, st.walk(), z + 0.5f, facing(p, 0, -1), "",
                    p.f.wx(u, v - 1) + 0.5f, p.f.wz(u, v - 1) + 0.5f));
            float[] post = priest ? new float[]{p.f.wx(behind[0], behind[1]) + 0.5f, p.f.wz(behind[0], behind[1]) + 0.5f} : new float[]{p.f.wx(u + 1, v) + 0.5f, p.f.wz(u + 1, v) + 0.5f};
            ar.fittings.add(new Architecture.Fitting(p.s.id, "post:" + what, post[0], st.walk(), post[1], facing(p, 0, -1), "", 0, 0));
            // candles either side of it
            for (int side : new int[]{-1, 1}) {
                int cu = u + side * 2;
                if (r.holds(cu, v) && st.g[cu][v] == OPEN && commit(st, List.of(new int[]{cu, v}), List.of())) {
                    piece(p, st, fitted, "CANDELABRA", rectOf(p, List.of(new int[]{cu, v})), 1, facing(p, 0, -1), "");
                    fitted.rooms.get(fitted.rooms.size() - 1).lit = true;
                }
            }
            fitted.fittings.add(what);
            r.solid++;
            return true;
        }
        return false;
    }

    /**
     * A jail's cells along the room's back wall: each a few cells wide and deep, iron bars across its front with a barred
     * door in the middle and between it and the next, a slab along its back wall; the corridor before them kept clear.
     * Each cell is recorded for the law.
     */
    private boolean placeCells(Storey st, Room r, Architecture.Fitted fitted, Map<String, Object> pc) {
        Plan p = st.plan;
        int deep = Atlas.i(pc, "deep", 3), wide = Atlas.i(pc, "wide", 3);
        if (r.d() < deep + 3 || r.w() < wide) return false;
        int frontV = r.v1 - deep;   // the bars' line
        int made = 0, tall = Math.max(2, Math.min(st.clear, 3));
        int bars = Kit.block(Atlas.s(pc, "bars", "iron_bars"));
        for (int ua = r.u0; ua + wide - 1 <= r.u1; ua += wide + 1) {
            int ub = ua + wide - 1;
            boolean ok = true;
            for (int uu = ua; uu <= Math.min(r.u1, ub + 1) && ok; uu++) for (int vv = frontV; vv <= r.v1 && ok; vv++) if (st.g[uu][vv] != OPEN) ok = false;
            for (int uu = ua; uu <= ub && ok; uu++) if (!walk(st.g[uu][frontV - 1])) ok = false;
            if (!ok) continue;
            int gate = (ua + ub) / 2;
            for (int uu = ua; uu <= ub; uu++) {
                int x = p.f.wx(uu, frontV), z = p.f.wz(uu, frontV);
                for (int y = st.walk(); y < st.walk() + tall; y++) c.set(x, y, z, uu == gate && y <= st.walk() + 1 ? Blocks.LOCKED_DOOR : bars);
                st.g[uu][frontV] = WALL;
            }
            if (ub + 1 <= r.u1) for (int vv = frontV; vv <= r.v1; vv++) {
                int x = p.f.wx(ub + 1, vv), z = p.f.wz(ub + 1, vv);
                for (int y = st.walk(); y < st.walk() + tall; y++) c.set(x, y, z, bars);
                st.g[ub + 1][vv] = WALL;
            }
            int su = ua == gate ? ub : ua;
            List<int[]> slab = List.of(new int[]{su, r.v1}, new int[]{su, r.v1 - 1});
            int[] rect = rectOf(p, slab);
            for (int x = rect[0]; x <= rect[2]; x++) for (int z = rect[1]; z <= rect[3]; z++) c.set(x, st.walk(), z, Blocks.BED);
            ar.pieces.add(new Architecture.Piece(p.s.id, "SLAB", rect[0], rect[1], rect[2], rect[3], st.walk(), 1, facing(p, 0, 1), "cell"));
            fitted.pieces.add("slab");
            int gx = p.f.wx(gate, frontV), gz = p.f.wz(gate, frontV);
            float[] inside = {p.f.wx(gate, frontV + 1) + 0.5f, p.f.wz(gate, frontV + 1) + 0.5f}, release = {p.f.wx(gate, frontV - 1) + 0.5f, p.f.wz(gate, frontV - 1) + 0.5f};
            int[] box = rectOf(p, List.of(new int[]{ua, frontV}, new int[]{ub, r.v1}));
            ar.cells.add(new Architecture.Cell(p.s.id, made, gx, gz, st.walk(), box, inside, release, rect, facing(p, 0, 1)));
            keep(st, gate, frontV - 1);
            // the cell's own floor is not the room's: it leaves the room's reach
            for (int uu = ua; uu <= ub; uu++) for (int vv = frontV + 1; vv <= r.v1; vv++) st.g[uu][vv] = WALL;
            made++;
            pieces++;
        }
        if (made > 0) fitted.fittings.add(made + " cells");
        return made > 0;
    }

    /** A stable's stalls: boarded partitions out from the back wall, hay and a trough's place in each. */
    private boolean placeStalls(Storey st, Room r, Architecture.Fitted fitted, Map<String, Object> pc) {
        Plan p = st.plan;
        int deep = Math.min(3, r.d() - 2), made = 0;
        if (deep < 2) return false;
        for (int u = r.u0 + 2; u <= r.u1 - 1; u += 3) {
            List<int[]> boards = new ArrayList<>();
            for (int v = r.v1 - deep + 1; v <= r.v1; v++) boards.add(new int[]{u, v});
            if (!commit(st, boards, List.of())) continue;
            for (int[] q : boards) { c.set(p.f.wx(q[0], q[1]), st.walk(), p.f.wz(q[0], q[1]), Blocks.FENCE); st.g[q[0]][q[1]] = WALL; }
            made++;
            // hay in the stall's back corner
            int hu = u - 1, hv = r.v1;
            if (st.g[hu][hv] == OPEN && commit(st, List.of(new int[]{hu, hv}), List.of())) c.set(p.f.wx(hu, hv), st.walk(), p.f.wz(hu, hv), Blocks.HAY_BALE);
        }
        if (made > 0) fitted.fittings.add(made + " stalls");
        return made > 0;
    }

    /** What hangs on a wall: a painting, a tapestry, a trophy of arms, a mirror, a pot rack - over a cell beside the wall. */
    private boolean placeHang(Storey st, Room r, Architecture.Fitted fitted, Map<String, Object> pc, int n) {
        Plan p = st.plan;
        int w = Atlas.i(pc, "w", 1);
        // (0.4.7) a sconce sits high on the wall, over a bed's head or a table as well as open floor
        boolean high = Atlas.s(pc, "kind", "PAINTING").equals("SCONCE");
        List<int[]> cand = new ArrayList<>();
        for (int u = r.u0; u <= r.u1; u++) for (int v = r.v0; v <= r.v1; v++) {
            byte b = st.g[u][v];
            // (0.4.5, gamefixes: a tapestry hung behind a suit of armour, a trophy behind a wardrobe) only on a wall
            // with open floor before it - never behind something already standing there
            if (!walk(b) && !(high && b == FURN && lowAt(st, u, v))) continue;
            for (int[] wd : wallsBeside(st, u, v, true)) cand.add(new int[]{u, v, wd[0], wd[1]});
        }
        if (cand.isEmpty()) return false;
        int start = (int) (Noise.hash(ar.a.seed, p.s.id.hashCode() + n * 17, st.floorY + r.u0, 144) * cand.size());
        // (0.4.8) a light sought for a dark corner: the walls nearest it first
        boolean near = pc.containsKey("nearU");
        if (near) { int nu = Atlas.i(pc, "nearU", 0), nv = Atlas.i(pc, "nearV", 0); cand.sort(java.util.Comparator.comparingInt(c -> (c[0] - nu) * (c[0] - nu) + (c[1] - nv) * (c[1] - nv))); }
        for (int t = 0; t < cand.size(); t++) {
            int[] q = cand.get(near ? t : pick(start, t, 11, cand.size()));
            int au = -q[3], av = q[2];
            List<int[]> cells = new ArrayList<>();
            boolean ok = true;
            // (0.4.7, gamefixes: a tapestry hung half over a door) every cell of a wide piece has the same wall behind it
            for (int i = 0; i < w; i++) { int u = q[0] + au * i, v = q[1] + av * i; if (!r.holds(u, v) || !walk(st.g[u][v]) && !(high && st.g[u][v] == FURN && lowAt(st, u, v)) || st.hung.contains(u + "," + v + "," + q[2] + "," + q[3]) && !(high && takeDown(st, u, v, q[2], q[3], fitted)) || !hasWall(st, u, v, q[2], q[3])) { ok = false; break; } cells.add(new int[]{u, v}); }
            if (!ok) continue;
            for (int[] cc : cells) st.hung.add(cc[0] + "," + cc[1] + "," + q[2] + "," + q[3]);
            int[] rect = rectOf(p, cells);
            String kind = Atlas.s(pc, "kind", "PAINTING");
            ar.pieces.add(new Architecture.Piece(p.s.id, kind, rect[0], rect[1], rect[2], rect[3], st.walk(), Math.min(st.clear, Atlas.i(pc, "h", 3)), facing(p, q[2], q[3]), ""));
            fitted.pieces.add(kind.toLowerCase(java.util.Locale.ROOT));
            if (light(kind)) fitted.rooms.get(fitted.rooms.size() - 1).lit = true;
            pieces++;
            return true;
        }
        return false;
    }

    /**
     * (0.4.7) A room's light before its pictures: a painting, a tapestry, a mirror or a trophy hung on this cell's wall
     * (local direction du, dv) is taken down to give a sconce its place. False if what hangs there is not decoration.
     */
    private boolean takeDown(Storey st, int u, int v, int du, int dv, Architecture.Fitted fitted) {
        Plan p = st.plan;
        int x = p.f.wx(u, v), z = p.f.wz(u, v), fac = facing(p, du, dv);
        java.util.Set<String> decor = java.util.Set.of("PAINTING", "TAPESTRY", "MIRROR", "WEAPON_RACK");
        for (int k = ar.pieces.size() - 1; k >= 0; k--) {
            Architecture.Piece pc = ar.pieces.get(k);
            if (!pc.owner().equals(p.s.id) || pc.y() != st.walk() || pc.facing() != fac || !decor.contains(pc.kind())) continue;
            if (x < pc.x0() || x > pc.x1() || z < pc.z0() || z > pc.z1()) continue;
            ar.pieces.remove(k);
            fitted.pieces.remove(pc.kind().toLowerCase(java.util.Locale.ROOT));
            pieces--;
            st.hung.remove(u + "," + v + "," + du + "," + dv);
            return true;
        }
        return false;
    }

    /** (0.4.7) Whether what stands on a cell is low (a bed, a table: nothing a cell up), so a sconce shows over it. */
    private boolean lowAt(Storey st, int u, int v) { return open(c.get(st.plan.f.wx(u, v), st.walk() + 1, st.plan.f.wz(u, v))); }

    private boolean hasWall(Storey st, int u, int v, int du, int dv) { for (int[] d : wallsBeside(st, u, v, true)) if (d[0] == du && d[1] == dv) return true; return false; }

    /** A rug on the floor in the room's open middle (no cells: walked over). */
    private boolean placeRug(Storey st, Room r, Architecture.Fitted fitted, Map<String, Object> pc) {
        Plan p = st.plan;
        int w = Math.min(Atlas.i(pc, "w", 2), r.w() - 1), d = Math.min(Atlas.i(pc, "d", 2), r.d() - 1);
        if (w < 1 || d < 1) return false;
        int u0 = r.u0 + (r.w() - w) / 2, v0 = r.v0 + (r.d() - d) / 2;
        for (int u = u0; u < u0 + w; u++) for (int v = v0; v < v0 + d; v++) if (!walk(st.g[u][v]) && st.g[u][v] != FURN) return false;
        int[] rect = worldRect(p, u0, v0, u0 + w - 1, v0 + d - 1);
        ar.pieces.add(new Architecture.Piece(p.s.id, Atlas.s(pc, "kind", "RUG"), rect[0], rect[1], rect[2], rect[3], st.walk(), 1, facing(p, 0, 1), ""));
        fitted.pieces.add("rug");
        pieces++;
        return true;
    }

    /**
     * A chandelier hung in the room's middle (under a ceiling tall enough to walk beneath it). (0.4.5, gamefixes: one hung
     * down a stairwell over the steps, another twice in the same spot) the cell nearest the middle whose ceiling is the
     * storey's own slab (not the open well of a stair, not the roof seen up through it), beside no step, and no other chandelier within 'chandelierApart' cells.
     */
    private boolean placeCeiling(Storey st, Room r, Architecture.Fitted fitted, Map<String, Object> pc) {
        Plan p = st.plan;
        if (st.clear < Math.max(Atlas.i(pc, "minClear", 4), Atlas.i(data, "chandelierMinClear", 4))) return false;
        int apart = Atlas.i(data, "chandelierApart", 4);
        double mu = r.u0 + (r.w() - 1) / 2.0, mv = r.v0 + (r.d() - 1) / 2.0;
        int[] best = null; double bestD = 1e9;
        for (int u = r.u0; u <= r.u1; u++) for (int v = r.v0; v <= r.v1; v++) {
            if (!walk(st.g[u][v]) && st.g[u][v] != FURN) continue;
            double d = Math.hypot(u - mu, v - mv);
            if (d >= bestD || !hangable(st, u, v, apart, Boolean.TRUE.equals(pc.get("nearStairs")))) continue;
            // (0.4.7) the head room under this very cell, not the storey's at its tallest (a beam, a lower ceiling)
            if (headroom(st, u, v) < Atlas.i(data, "chandelierMinClear", 4)) continue;
            bestD = d; best = new int[]{u, v};
        }
        if (best == null) return false;
        int[] rect = worldRect(p, best[0], best[1], best[0], best[1]);
        ar.pieces.add(new Architecture.Piece(p.s.id, Atlas.s(pc, "kind", "CHANDELIER"), rect[0], rect[1], rect[2], rect[3], st.walk(), Math.min(headroom(st, best[0], best[1]), 8), 0, ""));
        st.hungAt.add(best);
        fitted.pieces.add("chandelier");
        fitted.rooms.get(fitted.rooms.size() - 1).lit = true;
        pieces++;
        return true;
    }

    /** (0.4.7) The buildings' wealths that hang chandeliers (interiors data 'chandelierWealth'). */
    private java.util.List<?> chandelierWealth() { List<?> l = Atlas.arr(data, "chandelierWealth"); return l.isEmpty() ? List.of("rich", "noble", "royal") : l; }

    /** Whether a chandelier may hang over a cell: its ceiling the storey's own slab, no step beside it, none hung near. */
    private boolean hangable(Storey st, int u, int v, int apart, boolean nearStairs) {
        Plan p = st.plan;
        for (int[] h : st.hungAt) if (Math.abs(h[0] - u) + Math.abs(h[1] - v) < apart) return false;
        if (st.g[u][v] == STAIR) return false;
        // (the last resort for a room with no other light: beside the flight, never over a tread or a step)
        for (int[] d : nearStairs ? new int[][]{{0, 0}} : new int[][]{{0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            int a = u + d[0], b = v + d[1];
            if (!p.in(a, b)) continue;
            if (st.g[a][b] == STAIR) return false;
            // nor over a flight's head (its top tread is set in this floor)
            if (c.get(p.f.wx(a, b), st.floorY, p.f.wz(a, b)) == Blocks.STAIRS) return false;
        }
        return headroom(st, u, v) >= 3;
    }

    /** The open cells over a cell up to whatever closes it above (its ceiling or the roof's slope): 0 if it is open to the sky or a stair's well. */
    private int headroom(Storey st, int u, int v) {
        Plan p = st.plan;
        int x = p.f.wx(u, v), z = p.f.wz(u, v);
        for (int y = st.walk(); y < st.walk() + 12; y++) {
            int b = c.get(x, y, z);
            if (open(b)) continue;
            return solid(b) && b != Blocks.STAIRS ? y - st.walk() : 0;
        }
        return 0;
    }

    /**
     * The cells of a storey nobody can walk to from its ways in, before it is furnished: a small pocket (a few cells
     * behind a tower's footing or a chimney breast) is walled up flush in the room's inner finish; a bigger one is a
     * defect (its storey is wrongly reached).
     */
    private void seal(Storey st, Architecture.Fitted fitted) {
        if (st.entries.isEmpty()) return;
        Set<Long> got = reach(st, null);
        Plan p = st.plan;
        Set<Long> seen = new HashSet<>();
        int wallMat = partitionMat(p);
        for (int u = 0; u < p.W; u++) for (int v = 0; v < p.D; v++) {
            if (!walk(st.g[u][v]) || got.contains(key(u, v)) || seen.contains(key(u, v))) continue;
            // the pocket: its cells, flooding out from here
            List<int[]> pocket = new ArrayList<>();
            ArrayDeque<int[]> q = new ArrayDeque<>();
            q.add(new int[]{u, v}); seen.add(key(u, v));
            while (!q.isEmpty()) {
                int[] a = q.poll(); pocket.add(a);
                for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) { int x = a[0] + d[0], y = a[1] + d[1]; if (p.in(x, y) && walk(st.g[x][y]) && seen.add(key(x, y))) q.add(new int[]{x, y}); }
            }
            if (pocket.size() > Atlas.i(data, "sealPocket", 6)) continue;
            for (int[] a : pocket) {
                int x = p.f.wx(a[0], a[1]), z = p.f.wz(a[0], a[1]);
                for (int y = st.walk(); y < st.walk() + Math.min(st.clear, 6); y++) if (open(c.get(x, y, z))) c.set(x, y, z, wallMat);
                st.g[a[0]][a[1]] = WALL;
            }
            fitted.sealed += pocket.size();
        }
    }

    /** The storey's last word: every walkable cell reachable from its ways in (none shut off by what was built). */
    private void reachCheck(Storey st, Architecture.Fitted fitted) {
        Set<Long> got = reach(st, null);
        int walkable = 0, cut = 0;
        for (int u = 0; u < st.g.length; u++) for (int v = 0; v < st.g[0].length; v++) if (walk(st.g[u][v])) { walkable++; if (!got.contains(key(u, v))) cut++; }
        fitted.walkable += walkable;
        fitted.unreached += cut;
        if (st.entries.isEmpty()) fitted.defects.add("storey at y " + st.walk() + " has no way in");
        else if (cut > 0) fitted.defects.add(cut + " cells of the storey at y " + st.walk() + " cannot be walked to");
    }
}
