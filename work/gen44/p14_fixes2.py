import json
def patch(p, pairs):
    s = open(p, encoding='utf-8').read()
    for a, b in pairs:
        assert s.count(a) == 1, (p, a[:100], s.count(a))
        s = s.replace(a, b)
    open(p, 'w', encoding='utf-8').write(s)
F = 'rpggame/src/main/java/rpg/world/shadowveil/Fitout.java'
patch(F, [
# volumes: biggest first, then the one with fewest ways through it (a court's gateway range is no nave)
("""        vols.sort((a, b) -> Integer.compare(b.area(), a.area()));""",
 """        vols.sort((a, b) -> a.area() != b.area() ? Integer.compare(b.area(), a.area()) : Integer.compare(throughs(s, a), throughs(s, b)));"""),
("""    private static String levelRole(int i, int n) {""",
 """    /** How many of a structure's doorways open into a volume (a gateway range has two: the way through it). */
    private static int throughs(Architecture.Structure s, Registry.Volume v) {
        int n = 0;
        for (int k = 0; k < s.doors.size(); k++) for (int[] cell : s.doors.get(k)) {
            int[] out = s.doorOut.get(k);
            for (int step = -1; step <= 1; step += 2) { int x = cell[0] + out[0] * step, z = cell[1] + out[1] * step; if (x >= v.x0() && x <= v.x1() && z >= v.z0() && z <= v.z1()) { n++; break; } }
        }
        return n;
    }

    private static String levelRole(int i, int n) {"""),
# the stair where the programme wants it (a gaol's at its front, clear of the cells along its back)
("""            for (int i = 0; i + 1 < p.levels.size(); i++) stair(p, i, fitted);""",
 """            p.stairFront = Atlas.s(prog, "stair", "back").equals("front");
            for (int i = 0; i + 1 < p.levels.size(); i++) stair(p, i, fitted);"""),
("""        final List<Storey> levels = new ArrayList<>();
        Plan(Kit.Frame f, int W, int D, boolean round, Registry.Volume vol, Architecture.Structure s) {""",
 """        final List<Storey> levels = new ArrayList<>();
        /** Whether its stairs go at its front (by the way in) rather than at its back. */
        boolean stairFront;
        Plan(Kit.Frame f, int W, int D, boolean round, Registry.Volume vol, Architecture.Structure s) {"""),
("""            sc += q[1] * 0.4;
            for (int[] en : lo.entries) if (Math.abs(en[0] - q[0]) + Math.abs(en[1] - q[1]) < 3) sc -= 30;""",
 """            sc += (p.stairFront ? -q[1] : q[1]) * (p.stairFront ? 1.5 : 0.4);
            for (int[] en : lo.entries) if (Math.abs(en[0] - q[0]) + Math.abs(en[1] - q[1]) < 3) sc -= 30;"""),
# zones: both sides two deep at least (a one-row zone is no room)
("""            int minSide = walls ? 2 : 1;
            for (int off = 0; off <= len; off++) for (int sgn : new int[]{1, -1}) {
                if (off == 0 && sgn < 0) continue;
                int at = a0 + want + sgn * off;
                if (at - a0 < minSide || a1 - at < (walls ? minSide : minSide - 1) || across < 2) continue;""",
 """            int minSide = 2;
            for (int off = 0; off <= len; off++) for (int sgn : new int[]{1, -1}) {
                if (off == 0 && sgn < 0) continue;
                int at = a0 + want + sgn * off;
                if (at - a0 < minSide || a1 - at < (walls ? minSide : 1) || across < 2) continue;"""),
# counters in a room three deep (customers, the counter, the staff)
("""        if (r.w() < 3 || r.d() < 4) return false;""", """        if (r.w() < 3 || r.d() < 3) return false;"""),
# the altar against the far wall of a narrow room too
("""        for (int v = r.v1; v >= r.v0 + 2; v--) {
            if (st.g[u][v] != OPEN || !walk(st.g[u][v - 1])) continue;""",
 """        for (int v = r.v1; v >= r.v0 + 1; v--) {
            if (st.g[u][v] != OPEN || !walk(st.g[u][v - 1])) continue;"""),
# a piece that only belongs in a room deep enough for it
("""            if (r.area() < Atlas.i(pc, "minArea", 0)) continue;""",
 """            if (r.area() < Atlas.i(pc, "minArea", 0) || r.d() < Atlas.i(pc, "minDepth", 0) || r.w() < Atlas.i(pc, "minWidth", 0)) continue;"""),
])

p = 'rpggame/src/main/resources/data/shadowveil_interiors.json'
d = json.load(open(p, encoding='utf-8'))
I = d['interiors']
I['programs']['jail']['stair'] = 'front'
for k in ('single', 'ground'):
    for r in I['programs']['bank']['levels'][k]:
        if r['use'] == 'vault': r['minStorey'] = 56
for pc in I['uses']['nave']['pieces']:
    if pc.get('place') == 'rows': pc['minDepth'] = 7
I['programs']['church']['volumes'] = [
    {"levels": {"single": [{"use": "nave"}], "ground": [{"use": "nave"}], "upper": [{"use": "belfry"}]}},
    {"levels": {"single": [{"use": "vestry"}], "ground": [{"use": "vestry"}], "upper": [{"use": "bedroom"}], "top": [{"use": "belfry"}]}},
    {"levels": {"single": [{"use": "dormitory"}], "ground": [{"use": "cloister_room"}], "upper": [{"use": "dormitory"}]}},
    {"levels": {"single": [{"use": "vestry"}], "ground": [{"use": "vestry"}], "upper": [{"use": "dormitory"}]}}]
I['uses']['vestry']['sleeps'] = True
json.dump(d, open(p, 'w', encoding='utf-8'), indent=1, ensure_ascii=False)

p = 'rpggame/src/main/resources/data/shadowveil_architecture.json'
d = json.load(open(p, encoding='utf-8'))
for s in d['architecture']['settlements']:
    for b in s.get('later', []):
        if b.get('purpose') in ('jail', 'bank'): b['backDoor'] = False
json.dump(d, open(p, 'w', encoding='utf-8'), indent=1, ensure_ascii=False)
print('ok')
