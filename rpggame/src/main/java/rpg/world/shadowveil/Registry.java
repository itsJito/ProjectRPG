package rpg.world.shadowveil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import rpg.world.Blocks;

/**
 * (0.4.2, gamefixes: a persistent building registry - every structure by unique id, kingdom, coordinates, footprint,
 * exterior dimensions, foundation, purpose, planned floors, entrances, reserved interior, anticipated rooms and housing -
 * and architectural feasibility checks on every one, so 0.4.3 can build every interior without moving an exterior)
 * The built world as a register: one entry per structure the architecture raised, read from what was actually built
 * (its cells), never from what was meant. Each entry is checked against the standards ({@code shadowveil_architecture.json}
 * "standards") and its purpose's needs ("purposes"):
 * <ul>
 * <li>an enterable building has a doorway, and every doorway opens two cells high onto a floor, from walkable open
 *     ground outside into its reserved interior (never into solid terrain or a wall);</li>
 * <li>its interior volume is reserved, clear of anything but its storeys' floors, as tall as a room must be on every
 *     floor, as large as its work needs, with an open stair shaft through every floor above the ground;</li>
 * <li>its floors hold the beds its purpose and its people need;</li>
 * <li>an underground place (a crypt, a cellar, a mine, a cave) has its mouth and a volume kept beyond it, under enough
 *     rock, dry, and clear of every other.</li>
 * </ul>
 * What passes is ready for its interior; what does not is flagged with why. Written by the survey
 * ({@code gradlew shadowveil}: registry.json and registry.md) and kept with the release in docs/.
 */
public final class Registry {
    /** One doorway: where its sill is, which way it opens, how wide, whether it hangs a door. */
    public record Entrance(int x, int y, int z, char facing, int width, boolean leaf) {}
    /** A reserved volume, in world cells. */
    public record Volume(String kind, int x0, int y0, int z0, int x1, int y1, int z1) {
        /** A round room's area is its circle's (a tower's), a box's its rectangle's. */
        int area() { return round() ? (int) Math.round(Math.PI * (x1 - x0 + 1) * (z1 - z0 + 1) / 4) : (x1 - x0 + 1) * (z1 - z0 + 1); }
        boolean round() { return kind.endsWith("_round"); }
        /** Whether a column lies in the room's plan (a round room's circle). */
        boolean plan(int x, int z) {
            if (!round()) return true;
            double rx = (x1 - x0 + 1) / 2.0, rz = (z1 - z0 + 1) / 2.0, dx = (x + 0.5 - x0 - rx) / rx, dz = (z + 0.5 - z0 - rz) / rz;
            return dx * dx + dz * dz <= 1.0;
        }
        int height() { return y1 - y0 + 1; }
        boolean holds(int x, int y, int z) { return x >= x0 && x <= x1 && y >= y0 && y <= y1 && z >= z0 && z <= z1; }
    }
    /** A structure as registered. */
    public static final class Entry {
        public String id, name, kingdom, settlement, template, purpose, sizeClass;
        public int x0, z0, x1, z1, width, depth, height, foundation, floors, roofTop, interiorArea, bedsNeeded, bedCapacity;
        public boolean enterable, underground, ready;
        public final List<Entrance> entrances = new ArrayList<>();
        public final List<Volume> interior = new ArrayList<>(), below = new ArrayList<>();
        public final List<String> rooms = new ArrayList<>(), residents = new ArrayList<>(), issues = new ArrayList<>();
        /** (0.4.3) Who works here (the census), and the beds planned inside for its residents {x, y, z}. */
        public final List<String> workers = new ArrayList<>(), stations = new ArrayList<>();
        public final List<int[]> plannedBeds = new ArrayList<>();
        /**
         * (0.4.4, gamefixes: a persistent building-by-building quality registry) the interior as fitted out and its review:
         * rooms and storeys, what was placed, its people, working interactions, the navigation result, the screenshots
         * taken of it, the score on the 100-point rubric (by part), the defects found, the history, and approval.
         */
        public Architecture.Fitted fit;
        public final Map<String, Integer> rubric = new LinkedHashMap<>();
        public int score;
        public boolean approved;
        public final List<String> interactions = new ArrayList<>(), screenshots = new ArrayList<>(), history = new ArrayList<>();
        public String navigation = "";
        /** The whole entry as plain data (the registry file's record). */
        public Map<String, Object> data() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", id); m.put("name", name); m.put("kingdom", kingdom); m.put("settlement", settlement); m.put("template", template); m.put("purpose", purpose);
            if (sizeClass != null) m.put("sizeClass", sizeClass);
            m.put("footprint", List.of(x0, z0, x1, z1)); m.put("size", List.of(width, depth, height)); m.put("foundationY", foundation); m.put("roofTopY", roofTop);
            m.put("floors", floors); m.put("enterable", enterable); m.put("underground", underground);
            List<Object> es = new ArrayList<>();
            for (Entrance e : entrances) es.add(Map.of("x", e.x, "y", e.y, "z", e.z, "facing", String.valueOf(e.facing), "width", e.width, "door", e.leaf));
            m.put("entrances", es);
            m.put("interior", volumes(interior)); m.put("underground_volumes", volumes(below));
            m.put("interiorArea", interiorArea); m.put("rooms", rooms); m.put("bedsNeeded", bedsNeeded); m.put("bedCapacity", bedCapacity); m.put("residents", residents); m.put("workers", workers); m.put("stations", stations);
            List<Object> pb = new ArrayList<>(); for (int[] b : plannedBeds) pb.add(List.of(b[0], b[1], b[2])); m.put("plannedBeds", pb);
            m.put("ready", ready); m.put("issues", issues);
            if (fit != null) {
                Map<String, Object> q = new LinkedHashMap<>();
                List<Object> rs = new ArrayList<>();
                for (Architecture.RoomRecord r : fit.rooms) rs.add(Map.of("use", r.use, "storey", r.level, "box", List.of(r.rect[0], r.rect[1], r.rect[2], r.rect[3]), "y", r.y, "clear", r.clear, "walled", r.walled, "lit", r.lit));
                q.put("wealth", fit.wealth); q.put("rooms", rs); q.put("storeys", fit.rooms.stream().mapToInt(r -> r.level).max().orElse(0) + 1); q.put("stairs", fit.stairs);
                Map<String, Integer> counts = new java.util.TreeMap<>();
                for (String k : fit.pieces) counts.merge(k, 1, Integer::sum);
                q.put("objects", counts); q.put("stations", fit.stations); q.put("fittings", fit.fittings);
                q.put("beds", fit.beds); q.put("bedsWanted", fit.bedsWanted); q.put("seats", fit.seats);
                q.put("interactions", interactions); q.put("navigation", navigation); q.put("screenshots", screenshots);
                q.put("rubric", rubric); q.put("score", score); q.put("defects", fit.defects); q.put("history", history); q.put("approved", approved);
                m.put("interiorQuality", q);
            }
            return m;
        }
        private static List<Object> volumes(List<Volume> vs) {
            List<Object> out = new ArrayList<>();
            for (Volume v : vs) out.add(Map.of("kind", v.kind, "box", List.of(v.x0, v.y0, v.z0, v.x1, v.y1, v.z1)));
            return out;
        }
    }

    public final List<Entry> entries = new ArrayList<>();
    private final Map<String, Object> standards, purposes;
    private final List<Map<String, Object>> rules;

    Registry(Map<String, Object> architecture) {
        standards = Atlas.map(architecture, "standards");
        purposes = Atlas.map(architecture, "purposes");
        rules = Atlas.list(architecture, "purposeRules");
    }

    private int std(String k, int def) { return Atlas.i(standards, k, def); }
    private int stdAt(String k, int i, int def) { List<?> l = Atlas.arr(standards, k); return l.size() > i && l.get(i) instanceof Number n ? n.intValue() : def; }

    /** A structure's purpose: its own, else the first rule its template or name matches. */
    String purposeOf(Architecture.Structure s) {
        Object own = s.opts.get("purpose");
        if (own instanceof String p && purposes.containsKey(p)) return p;
        for (Map<String, Object> r : rules) {
            String t = Atlas.s(r, "template", null), n = Atlas.s(r, "name", null);
            List<?> ts = Atlas.arr(r, "templates");
            boolean ok = t != null ? s.template.equals(t) : !ts.isEmpty() ? ts.contains(s.template) : n != null && Pattern.compile(n).matcher(s.name).find();
            if (t != null && n != null) ok = s.template.equals(t) && Pattern.compile(n).matcher(s.name).find();
            if (ok) return Atlas.s(r, "purpose", "open");
        }
        return "open";
    }

    /** Registers and checks every structure raised (the cells still in hand). */
    static Registry of(Architecture ar, Kit.Canvas c) {
        Registry reg = new Registry(ar.a.map("architecture"));
        for (Architecture.Structure s : ar.structures) reg.entries.add(reg.entry(ar, s, c));
        return reg;
    }

    private Entry entry(Architecture ar, Architecture.Structure s, Kit.Canvas c) {
        Entry e = new Entry();
        e.id = s.id; e.name = s.name; e.settlement = s.settlement; e.template = s.template;
        e.kingdom = ShadowVeil.realmAt(ar.a, (int) s.cx(), (int) s.cz());
        e.purpose = purposeOf(s);
        Map<String, Object> pu = Atlas.map(purposes, e.purpose);
        e.enterable = Boolean.TRUE.equals(pu.get("enter"));
        e.underground = Boolean.TRUE.equals(pu.get("underground"));
        e.x0 = s.x0; e.z0 = s.z0; e.x1 = s.x1; e.z1 = s.z1; e.foundation = s.floor;
        e.width = s.x1 - s.x0 + 1; e.depth = s.z1 - s.z0 + 1;
        // the exterior's top: the highest built cell over the lot (roofs, spires, merlons)
        int top = s.floor;
        for (int x = s.x0 - 1; x <= s.x1 + 1; x++) for (int z = s.z0 - 1; z <= s.z1 + 1; z++)
            for (int y = Math.min(c.H - 1, s.floor + 60); y > top; y--) { int b = c.get(x, y, z); if (b != Blocks.AIR && b != Blocks.WATER && !Blocks.isLeaves(b)) { top = y; break; } }
        e.roofTop = top; e.height = top - s.floor;
        for (int k = 0; k < s.doors.size(); k++) {
            int[][] cells = s.doors.get(k);
            int[] out = s.doorOut.get(k);
            char f = out[0] > 0 ? 'e' : out[0] < 0 ? 'w' : out[1] > 0 ? 's' : 'n';
            e.entrances.add(new Entrance(cells[0][0], s.doorFloors.get(k), cells[0][1], f, cells.length, s.doorLeaf.get(k)));
        }
        for (Architecture.Reserved r : ar.reserved) if (r.owner().equals(s.id))
            (r.kind().startsWith("interior") || r.kind().equals("stair") ? e.interior : e.below).add(new Volume(r.kind(), r.x0(), r.y0(), r.z0(), r.x1(), r.y1(), r.z1()));
        for (Object o : Atlas.arr(pu, "rooms")) e.rooms.add(String.valueOf(o));
        // planned floors: the template's storeys (a keep's own), else what the interior's height holds
        int storeyMin = std("storeyMin", 4), areaPerBed = Math.max(1, std("areaPerBed", 6));
        int asked = Atlas.i(s.opts, "storeys", Atlas.i(s.opts, "keepStoreys", 0));
        int tallest = 0;
        for (Volume v : e.interior) if (v.kind.startsWith("interior")) tallest = Math.max(tallest, v.height());
        int storeyH = Atlas.i(pu, "storeyHeight", storeyMin);
        e.floors = Atlas.i(pu, "floors", 0) > 0 ? Atlas.i(pu, "floors", 0) : asked > 0 ? asked : Math.max(1, (tallest + 1) / storeyH);
        int area = 0;
        for (Volume v : e.interior) if (v.kind.startsWith("interior")) area = Math.max(area, v.area());
        e.interiorArea = area;
        e.bedsNeeded = Atlas.i(pu, "beds", 0);
        e.bedCapacity = area * e.floors / areaPerBed;
        if (e.purpose.equals("house") || e.purpose.equals("cottage")) e.sizeClass = e.width * e.depth <= 30 ? "small" : e.width * e.depth <= 52 ? "medium" : "large";
        check(ar, s, e, c);
        e.ready = e.issues.isEmpty();
        return e;
    }

    // ------------------------------------------------------------------ the checks
    private void check(Architecture ar, Architecture.Structure s, Entry e, Kit.Canvas c) {
        Map<String, Object> pu = Atlas.map(purposes, e.purpose);
        if (!e.enterable && !e.underground) return;
        int clearMin = std("clearMin", 3), doorH = stdAt("doorClear", 1, 2), cover = std("undergroundCover", 2);
        if (e.entrances.isEmpty()) e.issues.add("no entrance");
        // every doorway: open two high, a floor under it, walkable ground outside, its reserved room (or court) inside
        for (int k = 0; k < s.doors.size(); k++) {
            int fy = s.doorFloors.get(k);
            int[] out = s.doorOut.get(k);
            for (int[] cell : s.doors.get(k)) {
                int x = cell[0], z = cell[1];
                for (int y = 1; y <= doorH; y++) { int b = c.get(x, fy + y, z); if (b != Blocks.AIR && b != Blocks.DOOR && b != Blocks.LOCKED_DOOR) e.issues.add("doorway " + x + "," + z + " is not open " + doorH + " high"); }
                if (!solid(c.get(x, fy, z))) e.issues.add("doorway " + x + "," + z + " has no threshold under it");
                int ix = x - out[0], iz = z - out[1];
                boolean intoRoom = false;
                for (Volume v : e.interior) if (v.holds(ix, fy + 1, iz)) intoRoom = true;
                boolean court = s.contains(ix, iz) && open(c, ix, fy + 1, iz) && open(c, ix, fy + 2, iz);
                if (e.underground) court = open(c, ix, fy + 1, iz) || c.get(ix, fy + 1, iz) == Blocks.STAIRS || c.get(ix, fy, iz) == Blocks.STAIRS;
                if (!intoRoom && !court) e.issues.add("doorway " + x + "," + z + " leads into " + (solid(c.get(ix, fy + 1, iz)) ? "solid " + blockName(c.get(ix, fy + 1, iz)) : "no reserved room"));
                else if (intoRoom && !open(c, ix, fy + 1, iz)) e.issues.add("doorway " + x + "," + z + " is blocked inside by " + blockName(c.get(ix, fy + 1, iz)));
                int ox = x + out[0], oz = z + out[1];
                boolean ground = solid(c.get(ox, fy, oz)) || solid(c.get(ox, fy - 1, oz)) || c.get(ox, fy, oz) == Blocks.WATER;
                if (!ground) e.issues.add("doorway " + x + "," + z + " opens onto a drop");
                if (solid(c.get(ox, fy + 1, oz)) && solid(c.get(ox, fy + 2, oz)) && !e.underground) e.issues.add("doorway " + x + "," + z + " opens onto a wall");
            }
        }
        if (e.underground) {
            if (e.below.isEmpty()) e.issues.add("no underground volume reserved beyond its entrance");
            for (Volume v : e.below) {
                if (v.kind.equals("connection")) continue;
                int wet = 0, shallow = 0, n = 0;
                for (int x = v.x0; x <= v.x1; x += 2) for (int z = v.z0; z <= v.z1; z += 2) {
                    n++;
                    if (c.ground(x, z) < v.y1 + cover && !s.contains(x, z)) shallow++;
                    for (int y = v.y0; y <= v.y1; y += 2) if (c.get(x, y, z) == Blocks.WATER) wet++;
                }
                if (wet > 0) e.issues.add(v.kind + " volume holds water (" + wet + " samples)");
                if (n > 0 && shallow > n / 4) e.issues.add(v.kind + " volume lies under less than " + cover + " cells of cover in " + shallow + " of " + n + " columns");
                if (v.y0 < 2) e.issues.add(v.kind + " volume reaches below the world's floor");
            }
            return;
        }
        if (e.interior.isEmpty()) { e.issues.add("no interior volume reserved"); return; }
        // the volume: tall enough on every floor, large enough for its work, clear of anything but its floors
        int minArea = Atlas.i(pu, "minArea", 0), minFloors = Atlas.i(pu, "minFloors", 1);
        Volume main = null;
        for (Volume v : e.interior) if (v.kind.startsWith("interior") && (main == null || v.area() > main.area())) main = v;
        if (main == null) { e.issues.add("no interior volume reserved"); return; }
        int perFloor = main.height() / Math.max(1, e.floors);
        if (perFloor - (e.floors > 1 ? 1 : 0) < clearMin) e.issues.add("rooms only " + (perFloor - (e.floors > 1 ? 1 : 0)) + " cells clear (standard " + clearMin + ")");
        if (e.interiorArea < minArea) e.issues.add("interior " + e.interiorArea + " m2, its purpose needs " + minArea);
        if (e.floors < minFloors) e.issues.add(e.floors + " floor(s), its purpose needs " + minFloors);
        if (e.floors > 1 && e.interior.stream().noneMatch(v -> v.kind.equals("stair"))) e.issues.add("no stair shaft reserved through its upper floors");
        // clear of everything but its floors: a level of the volume that is mostly solid is a storey's floor (allowed);
        // what stands in the rest - a wall through a room, a tower's footing, rock - may be no more than a pillar or a
        // beam's worth
        int solidCells = 0, cells = 0;
        for (int y = main.y0; y <= main.y1; y++) {
            int levelSolid = 0, levelCells = 0;
            for (int x = main.x0; x <= main.x1; x++) for (int z = main.z0; z <= main.z1; z++) {
                if (!main.plan(x, z)) continue;
                levelCells++;
                int b = c.get(x, y, z);
                if (b != Blocks.AIR && b != Blocks.DOOR && b != Blocks.LOCKED_DOOR) levelSolid++;
            }
            cells += levelCells;
            if (levelSolid * 10 < levelCells * 7) solidCells += levelSolid;
        }
        int allowed = cells * Math.max(1, std("obstructionPercent", 8)) / 100;
        if (solidCells > allowed) e.issues.add("interior volume obstructed: " + solidCells + " solid cells where " + allowed + " are allowed");
        if (e.bedCapacity < e.bedsNeeded) e.issues.add("room for " + e.bedCapacity + " bed(s), needs " + e.bedsNeeded);
    }

    // ------------------------------------------------------------------ (0.4.4) the interiors' quality record
    /**
     * Takes each building's fitted interior into its entry and scores it on the rubric, from what was measured of it:
     * <ul>
     * <li>architectural completeness (20): every storey floored and reached by a staircase, the rooms its programme asks
     *     for raised, none missing;</li>
     * <li>furniture and object placement (20): pieces for the rooms' size, the variety of them, nothing it needs left out;</li>
     * <li>lighting and atmosphere (20): every room lit, windows to the day;</li>
     * <li>functional realism and regional identity (15): the fittings its purpose works by (a counter, stations, an altar,
     *     cells, vault chests), beds for its people;</li>
     * <li>navigation and accessibility (15): every walkable cell of every storey reachable from the way in, nothing used
     *     from a side shut off;</li>
     * <li>technical polish (10): no defect left.</li>
     * </ul>
     * Approved at 80 or more.
     */
    void fitted(Architecture ar) {
        for (Entry e : entries) {
            Architecture.Fitted f = ar.fitted.get(e.id);
            if (f == null) continue;
            e.fit = f;
            e.history.add(rpg.Main.VERSION + ": interior built - " + f.rooms.size() + " rooms, " + f.pieces.size() + " pieces, " + f.stairs + " stairs, " + f.defects.size() + " defects");
            score(e, ar);
        }
    }

    /** Scores one entry (see {@link #fitted}). */
    void score(Entry e, Architecture ar) {
        Architecture.Fitted f = e.fit;
        if (f == null) return;
        e.rubric.clear();
        long missing = f.defects.stream().filter(d -> d.startsWith("no room for its") || d.contains("has no room for its")).count();
        int storeys = f.rooms.stream().mapToInt(r -> r.level).max().orElse(-1) + 1;
        boolean noStair = f.defects.stream().anyMatch(d -> d.contains("no stair") || d.contains("no room for a stair") || d.contains("no way in"));
        int arch = (storeys > 0 ? 8 : 0) + (noStair ? 0 : 6) + (int) Math.max(0, 6 - missing * 3);
        int solid = 0;
        java.util.Set<String> kinds = new java.util.HashSet<>(f.pieces);
        kinds.addAll(f.stations); kinds.addAll(f.fittings);
        for (String k : f.pieces) if (!k.equals("rug") && !k.equals("painting") && !k.equals("tapestry") && !k.equals("chandelier") && !k.equals("mirror") && !k.equals("weapon_rack") && !k.equals("pot_rack")) solid++;
        double perRoom = f.rooms.isEmpty() ? 0 : (solid + f.fittings.size() + f.stations.size()) / (double) f.rooms.size();
        int furn = (int) Math.round(Math.min(10, perRoom * 2.5) + Math.min(6, kinds.size() * 0.75) + Math.max(0, 4 - missing * 2));
        long lit = f.rooms.stream().filter(r -> r.lit).count();
        int light = (int) Math.round(f.rooms.isEmpty() ? 0 : 14.0 * lit / f.rooms.size()) + (windows(e, ar) ? 6 : 2);
        int func = functional(e, f);
        int nav = f.walkable == 0 ? 0 : (int) Math.round(15.0 * (f.walkable - f.unreached) / f.walkable) - (noStair ? 5 : 0);
        int tech = Math.max(0, 10 - 2 * f.defects.size());
        e.rubric.put("architecture", Math.max(0, Math.min(20, arch)));
        e.rubric.put("furniture", Math.max(0, Math.min(20, furn)));
        e.rubric.put("lighting", Math.max(0, Math.min(20, light)));
        e.rubric.put("function", Math.max(0, Math.min(15, func)));
        e.rubric.put("navigation", Math.max(0, Math.min(15, nav)));
        e.rubric.put("technical", Math.max(0, Math.min(10, tech)));
        e.score = e.rubric.values().stream().mapToInt(Integer::intValue).sum();
        e.approved = e.score >= APPROVE && f.defects.isEmpty();
        e.navigation = f.walkable + " walkable cells, " + f.unreached + " unreached; " + f.stairs + " staircase(s)";
    }
    /** The pass mark (gamefixes: a verified minimum of 80 of 100). */
    public static final int APPROVE = 80;

    private boolean windows(Entry e, Architecture ar) {
        if (ar.t.cells == null) return false;
        Kit.Canvas c = new Kit.Canvas(ar.t);
        for (int x = e.x0; x <= e.x1; x++) for (int z = e.z0; z <= e.z1; z++) {
            if (x != e.x0 && x != e.x1 && z != e.z0 && z != e.z1) continue;
            for (int y = e.foundation + 1; y <= e.roofTop; y++) if (Blocks.glass(c.get(x, y, z))) return true;
        }
        return false;
    }

    /** What its purpose works by, present (15). */
    private int functional(Entry e, Architecture.Fitted f) {
        int pts = 15;
        java.util.function.Predicate<String> has = k -> f.fittings.stream().anyMatch(x -> x.contains(k)) || f.stations.stream().anyMatch(x -> x.contains(k)) || f.pieces.contains(k);
        switch (e.purpose) {
            case "bank" -> { if (!has.test("counter")) pts -= 8; if (f.fittings.stream().filter(x -> x.equals("chest")).count() < 2) pts -= 4; }
            case "shop" -> { if (!has.test("counter")) pts -= 8; }
            case "inn" -> { if (!has.test("counter")) pts -= 5; if (!has.test("range")) pts -= 4; }
            case "church" -> { if (!has.test("altar")) pts -= 8; if (f.seats == 0) pts -= 4; }
            case "workshop" -> { if (f.stations.isEmpty()) pts -= 9; }
            case "jail" -> { if (!has.test("cells")) pts -= 10; }
            case "barracks", "hall", "tower", "gatehouse" -> { if (f.seats == 0 && f.beds == 0) pts -= 6; }
            case "palace", "castle" -> { if (f.seats == 0) pts -= 4; if (!has.test("chandelier") && !has.test("candelabra")) pts -= 3; }
            case "stable" -> { if (!has.test("stalls")) pts -= 8; }
            default -> { }
        }
        if (f.beds < f.bedsWanted) pts -= Math.min(8, (f.bedsWanted - f.beds) * 2);
        return pts;
    }

    /** Adds the people whose home a structure is (their beds counted against its capacity, re-checked). */
    public void residents(List<String> names, String id, boolean perResident) {
        for (Entry e : entries) if (e.id.equals(id)) {
            e.residents.addAll(names);
            Map<String, Object> pu = Atlas.map(purposes, e.purpose);
            if (Boolean.TRUE.equals(pu.get("perResident"))) e.bedsNeeded = Math.max(Atlas.i(pu, "beds", 0), e.residents.size());
            e.issues.removeIf(i -> i.startsWith("room for "));
            if (e.bedCapacity < e.bedsNeeded) e.issues.add("room for " + e.bedCapacity + " bed(s), needs " + e.bedsNeeded);
            e.ready = e.issues.isEmpty();
        }
    }

    /**
     * (0.4.2, gamefixes: every applicable NPC able to receive an assigned home and bed) a person's home in the registry:
     * the building their bed stands in or beside, else the nearest dwelling in their own kingdom (a house, a cottage, an
     * inn's guest rooms, a barracks...) with a bed to spare within reach. Null: none (flagged by whoever asks).
     */
    public Entry house(String name, float x, float z, float reach) {
        Entry e = homeAt(x, z, 12);
        if (e == null || Atlas.i(Atlas.map(purposes, e.purpose), "beds", 0) == 0 && !Boolean.TRUE.equals(Atlas.map(purposes, e.purpose).get("perResident"))) {
            String kingdom = null;
            Entry near = homeAt(x, z, 60);
            if (near != null) kingdom = near.kingdom;
            Entry best = null; double bd = reach;
            for (Entry c : entries) {
                Map<String, Object> pu = Atlas.map(purposes, c.purpose);
                if (!c.enterable || c.underground || !Boolean.TRUE.equals(pu.get("perResident")) || c.residents.size() >= c.bedCapacity) continue;
                if (kingdom != null && !kingdom.equals(c.kingdom)) continue;
                double d = Math.hypot((c.x0 + c.x1 + 1) / 2.0 - x, (c.z0 + c.z1 + 1) / 2.0 - z);
                if (d < bd) { bd = d; best = c; }
            }
            e = best;
        }
        if (e != null) residents(List.of(name), e.id, true);
        return e;
    }

    public Entry byId(String id) { for (Entry e : entries) if (e.id.equals(id)) return e; return null; }
    /** The registered building whose footprint holds a point, or the nearest enterable one within a reach. */
    public Entry homeAt(float x, float z, float reach) {
        Entry best = null; double bd = reach;
        for (Entry e : entries) {
            if (!e.enterable || e.underground) continue;
            double dx = Math.max(0, Math.max(e.x0 - x, x - (e.x1 + 1))), dz = Math.max(0, Math.max(e.z0 - z, z - (e.z1 + 1))), d = Math.hypot(dx, dz);
            if (d < bd || d == 0 && bd > 0) { bd = d; best = e; }
        }
        return best;
    }

    // ------------------------------------------------------------------ the files
    /** The registry as JSON (every entry). */
    public String json() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("_about", "ShadowVeil's building registry (" + rpg.Main.VERSION + "): every structure by id, with its kingdom, footprint (x0, z0, x1, z1 cells), size (width, depth, height over its foundation), foundation and roof-top Y, planned floors, entrances (sill cell, facing, width, whether a door hangs), reserved interior and underground volumes (x0, y0, z0, x1, y1, z1), anticipated rooms, beds needed and room for, residents, and whether it is ready for its interior (issues if not).");
        root.put("standards", standards);
        List<Object> list = new ArrayList<>();
        for (Entry e : entries) list.add(e.data());
        root.put("structures", list);
        return astratto.util.Json.write(root);
    }

    /** The report: by kingdom, what is ready for its interior and what is flagged, with why. */
    public String report() {
        StringBuilder b = new StringBuilder("# ShadowVeil building registry - " + rpg.Main.VERSION + "\n\n");
        int enter = 0, ready = 0, under = 0, underReady = 0;
        for (Entry e : entries) { if (e.enterable && !e.underground) { enter++; if (e.ready) ready++; } if (e.underground) { under++; if (e.ready) underReady++; } }
        b.append(String.format("%d structures registered: %d enterable buildings (%d ready for interiors), %d underground places (%d ready), %d open places.%n%n",
                entries.size(), enter, ready, under, underReady, entries.size() - enter - under));
        b.append("Standards: ").append(standards).append("\n\n");
        Map<String, List<Entry>> byKingdom = new LinkedHashMap<>();
        for (Entry e : entries) byKingdom.computeIfAbsent(e.kingdom, k -> new ArrayList<>()).add(e);
        for (var k : byKingdom.entrySet()) {
            b.append("## ").append(k.getKey()).append("\n\n| Id | Name | Purpose | Footprint | Floors | Entrances | Interior m2 | Beds | Ready | Rooms | Score | Approved |\n|---|---|---|---|---|---|---|---|---|---|---|---|\n");
            for (Entry e : k.getValue()) {
                if (!e.enterable && !e.underground) continue;
                b.append(String.format("| %s | %s | %s | %d x %d @ (%d, %d) Y%d | %d | %d | %d | %d / %d | %s | %s | %s | %s |%n", e.id, e.name, e.purpose, e.width, e.depth, e.x0, e.z0, e.foundation, e.floors, e.entrances.size(),
                        e.interiorArea, e.bedsNeeded, e.bedCapacity, e.ready ? "yes" : "**no**: " + String.join("; ", e.issues),
                        e.fit == null ? "-" : String.valueOf(e.fit.rooms.size()), e.fit == null ? "-" : e.score + " " + e.rubric.values(), e.fit == null ? "-" : e.approved ? "yes" : "**no**: " + String.join("; ", e.fit.defects)));
            }
            b.append('\n');
        }
        return b.toString();
    }

    private static boolean solid(int b) { return b != Blocks.AIR && b != Blocks.WATER && !Blocks.isLeaves(b) && b != Blocks.DOOR; }
    private static boolean open(Kit.Canvas c, int x, int y, int z) { int b = c.get(x, y, z); return b == Blocks.AIR || b == Blocks.DOOR; }
    private static String blockName(int id) {
        for (var f : Blocks.class.getFields()) {
            if (f.getType() != int.class || f.getName().startsWith("L_") || f.getName().startsWith("IN_")) continue;
            try { if (f.getInt(null) == id) return f.getName().toLowerCase(java.util.Locale.ROOT); } catch (IllegalAccessException ignored) { }
        }
        return "#" + id;
    }
}
