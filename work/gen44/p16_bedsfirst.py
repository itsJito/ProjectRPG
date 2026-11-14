import json
def patch(p, pairs):
    s = open(p, encoding='utf-8').read()
    for a, b in pairs:
        assert s.count(a) == 1, (p, a[:100], s.count(a))
        s = s.replace(a, b)
    open(p, 'w', encoding='utf-8').write(s)
F = 'rpggame/src/main/java/rpg/world/shadowveil/Fitout.java'
patch(F, [
("""        // the furniture, room by room, beds shared out over the bedrooms as the people need them
        int bedRooms = 0;
        for (Storey st : all) for (Room r : st.rooms) if (Atlas.i(Atlas.map(uses, r.use), "beds", 0) > 0) bedRooms++;
        int[] roomsLeft = {bedRooms};
        for (Storey st : all) for (Room r : st.rooms) furnish(st, r, fitted, bedsLeft, roomsLeft);
        // beds still owed: wherever a bed will go
        for (int pass = 0; pass < 2 && bedsLeft[0] > 0; pass++) for (Storey st : all) for (Room r : st.rooms) {
            if (bedsLeft[0] <= 0 || st.ladder) break;
            Map<String, Object> u = Atlas.map(uses, r.use);
            boolean sleeps = !Atlas.arr(u, "overflow").isEmpty() || Boolean.TRUE.equals(u.get("sleeps"));
            if (pass == 0 ? sleeps : !Boolean.TRUE.equals(u.get("noBeds")))
                while (bedsLeft[0] > 0 && placeBed(st, r, fitted, "BED")) bedsLeft[0]--;
        }
        if (bedsLeft[0] > 0) fitted.defects.add(bedsLeft[0] + " of " + bedsWanted + " beds found no room");""",
 """        // what nobody can reach (a pocket behind a tower's footing, a corner a wall cut off) is walled up flush first
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
        if (bedsLeft[0] > 0) fitted.defects.add(bedsLeft[0] + " of " + bedsWanted + " beds found no room");
        // then everything else, room by room
        for (Storey st : all) for (Room r : st.rooms) furnish(st, r, fitted);"""),
# beds: the bedroom pieces' own pass
("""    /** A room furnished for its use: its pieces by the data, as the room and the wealth allow, each placed only if the room stays whole. */
    private void furnish(Storey st, Room r, Architecture.Fitted fitted, int[] bedsLeft, int[] bedRoomsLeft) {
        Map<String, Object> use = Atlas.map(uses, r.use);
        if (use.isEmpty()) { fitted.defects.add("no furnishing defined for a " + r.use); return; }
        Plan p = st.plan;
        Architecture.RoomRecord rec = new Architecture.RoomRecord(r.use, p.levels.indexOf(st), worldRect(p, r.u0, r.v0, r.u1, r.v1), st.walk(), st.clear, r.walled);
        fitted.rooms.add(rec);""",
 """    /** A room's own beds (its share of the building's), each by its use's bed piece, before anything else is set in it. */
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
        if (r.bedLit) rec.lit = true;"""),
("""            if (Boolean.TRUE.equals(pc.get("beds")) && st.ladder) continue;
            if (Boolean.TRUE.equals(pc.get("beds"))) {
                int share = bedRoomsLeft[0] > 0 ? (int) Math.ceil(bedsLeft[0] / (double) bedRoomsLeft[0]) : bedsLeft[0];
                count = Math.max(Atlas.i(pc, "atLeast", 1), share);
                bedRoomsLeft[0] = Math.max(0, bedRoomsLeft[0] - 1);
            }""",
 """            if (Boolean.TRUE.equals(pc.get("beds"))) continue;   // laid already (beds())"""),
("""                if (Boolean.TRUE.equals(pc.get("beds"))) bedsLeft[0]--;
                if (Boolean.TRUE.equals(pc.get("beds")) && bedsLeft[0] <= 0 && n + 1 >= Atlas.i(pc, "atLeast", 1)) break;
""", ""),
# the bedside table's light belongs to the room's record made later
("""            if (r.holds(ns[0], ns[1]) && st.g[ns[0]][ns[1]] == OPEN && !fitted.wealth.equals("poor") && commit(st, List.of(ns), List.of())) {
                piece(p, st, fitted, "NIGHTSTAND", new int[]{p.f.wx(ns[0], ns[1]), p.f.wz(ns[0], ns[1]), p.f.wx(ns[0], ns[1]), p.f.wz(ns[0], ns[1])}, 1, facing(p, wu, wv), "");
                fitted.rooms.get(fitted.rooms.size() - 1).lit = true;
            }""",
 """            if (r.holds(ns[0], ns[1]) && st.g[ns[0]][ns[1]] == OPEN && !fitted.wealth.equals("poor") && commit(st, List.of(ns), List.of())) {
                piece(p, st, fitted, "NIGHTSTAND", new int[]{p.f.wx(ns[0], ns[1]), p.f.wz(ns[0], ns[1]), p.f.wx(ns[0], ns[1]), p.f.wz(ns[0], ns[1])}, 1, facing(p, wu, wv), "");
                r.bedLit = true;
            }"""),
("""        boolean walled;
        int solid;""", """        boolean walled, bedLit;
        int solid;"""),
# sealing the dead pockets
("""    /** The storey's last word: every walkable cell reachable from its ways in (none shut off by what was built). */""",
 """    /**
     * The cells of a storey nobody can walk to from its ways in, before it is furnished: a small pocket (a few cells
     * behind a tower's footing or a chimney breast) is walled up flush in the room's inner finish; a bigger one is a
     * defect (its storey is wrongly reached).
     */
    private void seal(Storey st, Architecture.Fitted fitted) {
        if (st.entries.isEmpty()) return;
        Set<Long> got = reach(st, null);
        Plan p = st.plan;
        Set<Long> seen = new HashSet<>();
        int wallMat = BuildingKit.innerFinish(p.s.style.b("wall"));
        if (wallMat == Blocks.TIMBER_FRAME) wallMat = Blocks.PLASTER;
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

    /** The storey's last word: every walkable cell reachable from its ways in (none shut off by what was built). */"""),
])
patch('rpggame/src/main/java/rpg/world/shadowveil/Architecture.java', [
("""        public int bedsWanted, beds, seats, stairs, ladders, walkable, unreached;""", """        public int bedsWanted, beds, seats, stairs, ladders, walkable, unreached, sealed;"""),
])

p = 'rpggame/src/main/resources/data/shadowveil_interiors.json'
d = json.load(open(p, encoding='utf-8'))
I = d['interiors']
P = I['programs']
for k in ('single', 'ground'):
    for r in P['shop']['levels'][k]:
        if r['use'] == 'storeroom': r['minStorey'] = 60
P['inn']['levels']['single'] = [{"use": "common_room", "share": 3}, {"use": "dormitory", "share": 2, "door": "plain"}, {"use": "inn_kitchen", "share": 1, "minStorey": 70}]
I['uses']['office'].pop('noBeds', None)
for pc in I['uses']['shopfloor']['pieces']:
    if pc.get('place') == 'counter': pc['fallback'] = {"place": "wall", "kind": "COUNTER", "w": 3, "post": "counter"}
for pc in I['uses']['common_room']['pieces']:
    if pc.get('place') == 'counter': pc['fallback'] = {"place": "wall", "kind": "COUNTER", "w": 3, "post": "bar"}
json.dump(d, open(p, 'w', encoding='utf-8'), indent=1, ensure_ascii=False)
print('ok')
