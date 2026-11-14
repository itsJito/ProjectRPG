p='rpggame/src/main/java/rpg/world/shadowveil/Fitout.java'
s=open(p,encoding='utf-8').read()
def rep(a,b,cnt=1):
    global s
    assert s.count(a)==cnt,(a[:90],s.count(a)); s=s.replace(a,b)

# ---- rooms carry merged uses
rep("""        final Map<String, Object> spec;
        boolean walled;
        int solid;""","""        final Map<String, Object> spec;
        /** The uses of rooms the storey had no room to divide off: furnished here too. */
        final List<String> merged = new ArrayList<>();
        boolean walled;
        int solid;""")

# ---- split: walls, else an open zone, else one room with the rest merged
a=s.index("    private void split(Plan p, Storey st, int u0, int v0, int u1, int v1, List<Map<String, Object>> specs, int minArea, Architecture.Fitted fitted) {")
b=s.index("    /** Whether a line across a storey is free of stairs and kept cells")
s=s[:a]+"""    private void split(Plan p, Storey st, int u0, int v0, int u1, int v1, List<Map<String, Object>> specs, int minArea, Architecture.Fitted fitted) {
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
        boolean alongV = Atlas.s(first, "axis", d >= w * 0.8 ? "v" : "u").equals("v");
        int len = alongV ? d : w, across = alongV ? w : d;
        int want = (int) Math.round((len - 1) * f0);
        boolean wantWalls = !Boolean.FALSE.equals(first.get("walls")) && !Boolean.FALSE.equals(specs.get(1).get("walls"));
        int a0 = alongV ? v0 : u0, a1 = alongV ? v1 : u1;
        // walled rooms first (each side two deep at least and big enough), else an open zone's edge (each side one deep)
        for (int pass = wantWalls ? 0 : 1; pass < 2; pass++) {
            boolean walls = pass == 0;
            int minSide = walls ? 2 : 1;
            for (int off = 0; off <= len; off++) for (int sgn : new int[]{1, -1}) {
                if (off == 0 && sgn < 0) continue;
                int at = a0 + want + sgn * off;
                if (at - a0 < minSide || a1 - at < (walls ? minSide : minSide - 1) || across < 2) continue;
                if (walls && ((at - a0) * across < minArea || (a1 - at) * across < minArea)) continue;
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
        // no room to divide: one room, furnished for every use asked of it
        Room one = new Room(u0, v0, u1, v1, Atlas.s(first, "use", "hall"), first);
        for (Map<String, Object> sp : specs.subList(1, specs.size())) one.merged.add(Atlas.s(sp, "use", "hall"));
        st.rooms.add(one);
    }

"""+s[b:]

# ---- partitions tentative: the storey must stay whole behind it
rep("""        int wallMat = BuildingKit.innerFinish(p.s.style.b("wall"));""","""        // tried on the grid first: every walkable cell of the storey must still be reached through its doorway
        byte[] saved = new byte[a1 - a0 + 1];
        int before = reach(st, null).size();
        int walkLine = 0;
        for (int a = a0; a <= a1; a++) {
            int u = alongV ? a : at, v = alongV ? at : a;
            saved[a - a0] = st.g[u][v];
            if (walk(st.g[u][v]) && a != pick) walkLine++;
            if (st.g[u][v] != VOID && a != pick) st.g[u][v] = WALL;
        }
        boolean whole = reach(st, null).size() >= before - walkLine && !st.entries.isEmpty();
        for (int a = a0; a <= a1; a++) { int u = alongV ? a : at, v = alongV ? at : a; st.g[u][v] = saved[a - a0]; }
        if (!whole) return null;
        int wallMat = BuildingKit.innerFinish(p.s.style.b("wall"));""")
rep("""            if (b == OPEN && p.in(bu, bv) && p.in(fu, fv) && walk(st.g[bu][bv]) && walk(st.g[fu][fv]) && a > a0 && a < a1) doorAt.add(a);""","""            if (b == OPEN && p.in(bu, bv) && p.in(fu, fv) && walk(st.g[bu][bv]) && walk(st.g[fu][fv])) doorAt.add(a);""")

# ---- rails only where the floor stays whole
rep("""                if (Math.abs(a - last[0]) + Math.abs(b - last[1]) <= 1) continue;
                c.set(p.f.wx(a, b), up.floorY + 1, p.f.wz(a, b), Blocks.FENCE);
                up.g[a][b] = WALL;""","""                if (Math.abs(a - last[0]) + Math.abs(b - last[1]) <= 1) continue;
                // a rail only where the floor round the opening stays whole (never shutting the stairhead in)
                int before = reach(up, null).size();
                up.g[a][b] = WALL;
                if (reach(up, null).size() < before - 1) { up.g[a][b] = OPEN; continue; }
                c.set(p.f.wx(a, b), up.floorY + 1, p.f.wz(a, b), Blocks.FENCE);""")

# ---- the ways in: any opening in the volume's edge (a doorway, an arch from the next room) on every storey
rep("""        if (p.levels.get(0).entries.isEmpty()) fitted.defects.add("no doorway leads into the ground storey of a room volume");
    }""","""        // and any opening through the volume's edge onto open floor beyond it (an arch from a tower into a nave, a range's
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
                if (!open(c.get(x2, st.walk(), z2)) && !open(c.get(x, st.walk(), z))) continue;
                boolean dup = false;
                for (int[] en : st.entries) if (en[0] == u && en[1] == v) dup = true;
                if (!dup) { st.entries.add(new int[]{u, v}); keep(st, u, v); }
            }
        }
        if (p.levels.get(0).entries.isEmpty()) fitted.defects.add("no doorway leads into the ground storey of a room volume");
    }""")

# ---- furnishing: every use of the room, beds and needs first; a fallback for a piece with no room
rep("""        int budget = Math.max(2, Math.min(Atlas.i(data, "mostPerRoom", 14), r.area() / Math.max(1, Atlas.i(data, "cellsPerPiece", 5))));
        int k = 0;
        for (Map<String, Object> pc : Atlas.list(use, "pieces")) {
            k++;""","""        int budget = Math.max(2, Math.min(Atlas.i(data, "mostPerRoom", 14), r.area() / Math.max(1, Atlas.i(data, "cellsPerPiece", 5))));
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
            k++;""")
rep("""                if (!ok) break;
                made++;""","""                // a piece with no room may give way to a smaller one doing the same work (a range for a fireplace)
                if (!ok && n == 0 && !Atlas.map(pc, "fallback").isEmpty()) { pc = Atlas.map(pc, "fallback"); place = Atlas.s(pc, "place", "wall"); ok = placeWall(st, r, fitted, pc, place.equals("corner")); }
                if (!ok) break;
                made++;""")
rep("""            if (made == 0 && Boolean.TRUE.equals(pc.get("need"))) fitted.defects.add""","""            if (made == 0 && Boolean.TRUE.equals(pc0.get("need"))) fitted.defects.add""")
open(p,'w',encoding='utf-8').write(s)

p='rpggame/src/main/resources/data/shadowveil_interiors.json'
s=open(p,encoding='utf-8').read()
def rj(a,b,cnt=1):
    global s
    assert s.count(a)==cnt,(a[:90],s.count(a)); s=s.replace(a,b)
# hearths: a range where a fireplace has no room
rj('{"place": "wall", "kind": "FIREPLACE", "w": 2, "h": 2, "need": true},\n        {"place": "centre", "kind": "TABLE", "w": 2, "d": 1, "seats": "STOOL", "wealth": ["poor", "common"]},',
   '{"place": "wall", "kind": "FIREPLACE", "w": 2, "h": 2, "need": true, "fallback": {"place": "wall", "station": "RANGE", "post": "kitchen"}},\n        {"place": "centre", "kind": "TABLE", "w": 2, "d": 1, "seats": "STOOL", "wealth": ["poor", "common"]},')
rj('{"place": "wall", "kind": "FIREPLACE", "w": 2, "h": 2},\n        {"place": "wall", "fitting": "chest", "tag": "home"},\n        {"place": "wall", "kind": "STOOL"},',
   '{"place": "wall", "kind": "FIREPLACE", "w": 2, "h": 2, "need": true, "fallback": {"place": "wall", "station": "RANGE"}},\n        {"place": "wall", "fitting": "chest", "tag": "home"},\n        {"place": "wall", "kind": "STOOL"},')
rj('{"place": "wall", "kind": "FIREPLACE", "w": 2, "h": 2, "need": true},\n        {"place": "wall", "kind": "SIDEBOARD", "w": 2},',
   '{"place": "wall", "kind": "FIREPLACE", "w": 2, "h": 2, "need": true, "fallback": {"place": "wall", "station": "RANGE"}},\n        {"place": "wall", "kind": "SIDEBOARD", "w": 2},')
# shops: the storeroom only in a big enough storey (the counter needs the floor)
rj('"single": [{"use": "shopfloor", "share": 3}, {"use": "storeroom", "share": 1, "door": "plain"}],\n          "ground": [{"use": "shopfloor", "share": 3}, {"use": "storeroom", "share": 1, "door": "plain"}],',
   '"single": [{"use": "shopfloor", "share": 3}, {"use": "storeroom", "share": 1, "door": "plain", "minStorey": 48}],\n          "ground": [{"use": "shopfloor", "share": 3}, {"use": "storeroom", "share": 1, "door": "plain", "minStorey": 48}],')
rj('"single": [{"use": "banking_hall", "share": 3}, {"use": "vault", "share": 1, "door": "locked", "doorName": "vault"}],\n          "ground": [{"use": "banking_hall", "share": 3}, {"use": "vault", "share": 1, "door": "locked", "doorName": "vault"}],',
   '"single": [{"use": "banking_hall", "share": 3}, {"use": "vault", "share": 1, "door": "locked", "doorName": "vault", "minStorey": 40}],\n          "ground": [{"use": "banking_hall", "share": 3}, {"use": "vault", "share": 1, "door": "locked", "doorName": "vault", "minStorey": 40}],')
# churches: the tower's upper storeys are the clergy's rooms
rj('{"levels": {"single": [{"use": "vestry"}], "ground": [{"use": "vestry"}], "upper": [{"use": "belfry"}], "top": [{"use": "belfry"}]}},',
   '{"levels": {"single": [{"use": "vestry"}], "ground": [{"use": "vestry"}], "upper": [{"use": "bedroom"}], "top": [{"use": "belfry"}]}},')
open(p,'w',encoding='utf-8').write(s)
print('ok')
