import json
def patch(p, pairs):
    s = open(p, encoding='utf-8').read()
    for a, b in pairs:
        assert s.count(a) == 1, (p, a[:100], s.count(a))
        s = s.replace(a, b)
    open(p, 'w', encoding='utf-8').write(s)
F = 'rpggame/src/main/java/rpg/world/shadowveil/Fitout.java'
patch(F, [
# (a) an opening is a gap in a wall with open floor beyond it (not a jetty's overhang strip along the wall)
("""                int x2 = x + wd[0], z2 = z + wd[1];
                if (!open(c.get(x2, st.walk(), z2)) && !open(c.get(x, st.walk(), z))) continue;""",
 """                int x2 = x + wd[0], z2 = z + wd[1];
                if (!open(c.get(x2, st.walk(), z2)) || !open(c.get(x2, st.walk() + 1, z2))) continue;
                // a gap in a wall: the cells either side of it along the wall are the wall
                int[] lat = p.f.dir(d[1], d[0]);
                boolean walled = solid(c.get(x + lat[0], st.walk(), z + lat[1])) || solid(c.get(x - lat[0], st.walk(), z - lat[1]));
                if (!walled) continue;"""),
# (b) a stair never cuts either floor in two
("""            if (!head) return null;
            return path.toArray(new int[0][]);""",
 """            if (!head) return null;
            int[][] got = path.toArray(new int[0][]);
            return whole(p, lo, up, got) ? got : null;"""),
("""    private static boolean contains(List<int[]> path, int u, int v) {""",
 """    /**
     * Whether a flight leaves both its floors whole: below, every open cell still reached from the ways in round the
     * steps; above, every open cell reached from the stairhead round the opening.
     */
    private boolean whole(Plan p, Storey lo, Storey up, int[][] path) {
        int rise = path.length;
        byte[] keptLo = new byte[rise], keptUp = new byte[rise];
        for (int k = 0; k < rise; k++) { keptLo[k] = lo.g[path[k][0]][path[k][1]]; keptUp[k] = up.g[path[k][0]][path[k][1]]; }
        int openLo = 0, openUp = 0;
        for (int u = 0; u < p.W; u++) for (int v = 0; v < p.D; v++) { if (walk(lo.g[u][v])) openLo++; if (walk(up.g[u][v])) openUp++; }
        int beforeLo = reach(lo, null).size();
        for (int k = 0; k < rise - 1; k++) lo.g[path[k][0]][path[k][1]] = STAIR;
        int afterLo = reach(lo, null).size();
        for (int k = 0; k < rise - 1; k++) if (k >= rise - 3) up.g[path[k][0]][path[k][1]] = VOID;
        List<int[]> saved = new ArrayList<>(up.entries);
        up.entries.clear(); up.entries.add(path[rise - 1]);
        int afterUp = reach(up, null).size();
        int voided = 0;
        for (int k = 0; k < rise - 1; k++) if (k >= rise - 3 && walk(keptUp[k])) voided++;
        up.entries.clear(); up.entries.addAll(saved);
        for (int k = 0; k < rise; k++) { lo.g[path[k][0]][path[k][1]] = keptLo[k]; up.g[path[k][0]][path[k][1]] = keptUp[k]; }
        int stepsLo = 0;
        for (int k = 0; k < rise - 1; k++) if (walk(keptLo[k])) stepsLo++;
        return afterLo >= beforeLo - stepsLo && afterLo >= openLo - stepsLo - (openLo - beforeLo) && afterUp >= openUp - voided;
    }

    private static boolean contains(List<int[]> path, int u, int v) {"""),
# (f) no stair fits (a tower too narrow for one): a ladder against its wall, through the floor above
("""        if (best == null) { fitted.defects.add("no room for a stair between storeys " + i + " and " + (i + 1)); return; }""",
 """        if (best == null) { if (!ladder(p, lo, up)) fitted.defects.add("no room for a stair or a ladder between storeys " + i + " and " + (i + 1)); else { fitted.stairs++; fitted.ladders++; } return; }"""),
("""    private void build(Plan p, Storey lo, Storey up, int[][] path) {""",
 """    /**
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

    private void build(Plan p, Storey lo, Storey up, int[][] path) {"""),
("""        /** Walls something already hangs on. */
        final Set<String> hung = new HashSet<>();""",
 """        /** Walls something already hangs on. */
        final Set<String> hung = new HashSet<>();
        /** Reached only by a ladder (no beds for those who live here: not everyone climbs). */
        boolean ladder;"""),
# (c) a partition may run up to a flight and on beyond it (the stair stands in the gap); (d) both axes tried
("""        for (int a = a0; a <= a1; a++) {
            int u = alongV ? a : at, v = alongV ? at : a;
            byte b = st.g[u][v];
            if (b == STAIR || b == KEEP) return null;""",
 """        for (int a = a0; a <= a1; a++) {
            int u = alongV ? a : at, v = alongV ? at : a;
            byte b = st.g[u][v];
            if (b == KEEP) return null;
            if (b == STAIR) continue;"""),
("""            saved[a - a0] = st.g[u][v];
            if (walk(st.g[u][v]) && a != pick) walkLine++;
            if (st.g[u][v] != VOID && a != pick) st.g[u][v] = WALL;""",
 """            saved[a - a0] = st.g[u][v];
            if (walk(st.g[u][v]) && a != pick) walkLine++;
            if (st.g[u][v] != VOID && st.g[u][v] != STAIR && a != pick) st.g[u][v] = WALL;"""),
("""        for (int a = a0; a <= a1; a++) {
            int u = alongV ? a : at, v = alongV ? at : a;
            if (st.g[u][v] == VOID) continue;
            int x = p.f.wx(u, v), z = p.f.wz(u, v);
            if (a == pick) {""",
 """        for (int a = a0; a <= a1; a++) {
            int u = alongV ? a : at, v = alongV ? at : a;
            if (st.g[u][v] == VOID || st.g[u][v] == STAIR) continue;
            int x = p.f.wx(u, v), z = p.f.wz(u, v);
            if (a == pick) {"""),
("""        for (int a = a0; a <= a1; a++) { byte b = alongV ? st.g[a][at] : st.g[at][a]; if (b == STAIR) return false; }
        return true;""",
 """        return true;"""),
])

# (d) split on either axis: the preferred one when it keeps near the shares asked, else the other
s = open(F, encoding='utf-8').read()
a = s.index("        boolean alongV = Atlas.s(first, \"axis\", d >= w * 0.8 ? \"v\" : \"u\").equals(\"v\");")
b = s.index("        // no room to divide: one room, furnished for every use asked of it")
s = s[:a] + """        boolean prefV = Atlas.s(first, "axis", d >= w * 0.8 ? "v" : "u").equals("v");
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
""" + s[b:]
open(F, 'w', encoding='utf-8').write(s)

patch(F, [
# (e) beds still owed: any room that is not kept from them; never on a storey reached only by ladder
("""        if (bedsLeft[0] > 0) for (Storey st : all) for (Room r : st.rooms) {
            if (bedsLeft[0] <= 0) break;
            if (!Atlas.arr(Atlas.map(uses, r.use), "overflow").isEmpty() || Boolean.TRUE.equals(Atlas.map(uses, r.use).get("sleeps")))
                while (bedsLeft[0] > 0 && placeBed(st, r, fitted, "BED")) bedsLeft[0]--;
        }""",
 """        for (int pass = 0; pass < 2 && bedsLeft[0] > 0; pass++) for (Storey st : all) for (Room r : st.rooms) {
            if (bedsLeft[0] <= 0 || st.ladder) break;
            Map<String, Object> u = Atlas.map(uses, r.use);
            boolean sleeps = !Atlas.arr(u, "overflow").isEmpty() || Boolean.TRUE.equals(u.get("sleeps"));
            if (pass == 0 ? sleeps : !Boolean.TRUE.equals(u.get("noBeds")))
                while (bedsLeft[0] > 0 && placeBed(st, r, fitted, "BED")) bedsLeft[0]--;
        }"""),
("""            if (Boolean.TRUE.equals(pc.get("beds"))) {
                int share""",
 """            if (Boolean.TRUE.equals(pc.get("beds")) && st.ladder) continue;
            if (Boolean.TRUE.equals(pc.get("beds"))) {
                int share"""),
])
patch('rpggame/src/main/java/rpg/world/shadowveil/Architecture.java', [
("""        public int bedsWanted, beds, seats, stairs, walkable, unreached;""", """        public int bedsWanted, beds, seats, stairs, ladders, walkable, unreached;"""),
])

p = 'rpggame/src/main/resources/data/shadowveil_interiors.json'
d = json.load(open(p, encoding='utf-8'))
U = d['interiors']['uses']
for u in ('nave', 'banking_hall', 'vault', 'cell_block', 'gaol_office', 'throne_room', 'gallery', 'shopfloor', 'common_room', 'inn_kitchen', 'kitchen', 'office', 'records', 'library'):
    U[u]['noBeds'] = True
U['belfry']['sleeps'] = True
json.dump(d, open(p, 'w', encoding='utf-8'), indent=1, ensure_ascii=False)
print('ok')
