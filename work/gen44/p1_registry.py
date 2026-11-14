p='rpggame/src/main/java/rpg/world/shadowveil/Registry.java'
s=open(p,encoding='utf-8').read()
def rep(a,b,cnt=1):
    global s
    assert s.count(a)==cnt,(a[:70],s.count(a)); s=s.replace(a,b)
rep("""        public final List<int[]> plannedBeds = new ArrayList<>();""","""        public final List<int[]> plannedBeds = new ArrayList<>();
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
        public String navigation = "";""")
rep("""            m.put("ready", ready); m.put("issues", issues);
            return m;""","""            m.put("ready", ready); m.put("issues", issues);
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
            return m;""")
rep("""    /** Adds the people whose home a structure is (their beds counted against its capacity, re-checked). */""","""    // ------------------------------------------------------------------ (0.4.4) the interiors' quality record
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
        e.approved = e.score >= APPROVE;
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

    /** Adds the people whose home a structure is (their beds counted against its capacity, re-checked). */""")
rep("""            b.append("## ").append(k.getKey()).append("\\n\\n| Id | Name | Purpose | Footprint | Floors | Entrances | Interior m2 | Beds | Ready |\\n|---|---|---|---|---|---|---|---|---|\\n");""","""            b.append("## ").append(k.getKey()).append("\\n\\n| Id | Name | Purpose | Footprint | Floors | Entrances | Interior m2 | Beds | Ready | Rooms | Score | Approved |\\n|---|---|---|---|---|---|---|---|---|---|---|---|\\n");""")
rep("""                b.append(String.format("| %s | %s | %s | %d x %d @ (%d, %d) Y%d | %d | %d | %d | %d / %d | %s |%n", e.id, e.name, e.purpose, e.width, e.depth, e.x0, e.z0, e.foundation, e.floors, e.entrances.size(),
                        e.interiorArea, e.bedsNeeded, e.bedCapacity, e.ready ? "yes" : "**no**: " + String.join("; ", e.issues)));""","""                b.append(String.format("| %s | %s | %s | %d x %d @ (%d, %d) Y%d | %d | %d | %d | %d / %d | %s | %s | %s | %s |%n", e.id, e.name, e.purpose, e.width, e.depth, e.x0, e.z0, e.foundation, e.floors, e.entrances.size(),
                        e.interiorArea, e.bedsNeeded, e.bedCapacity, e.ready ? "yes" : "**no**: " + String.join("; ", e.issues),
                        e.fit == null ? "-" : String.valueOf(e.fit.rooms.size()), e.fit == null ? "-" : e.score + " " + e.rubric.values(), e.fit == null ? "-" : e.approved ? "yes" : "**no**: " + String.join("; ", e.fit.defects)));""")
open(p,'w',encoding='utf-8').write(s)
print("ok")
