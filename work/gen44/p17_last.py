import json
def patch(p, pairs):
    s = open(p, encoding='utf-8').read()
    for a, b in pairs:
        assert s.count(a) == 1, (p, a[:100], s.count(a))
        s = s.replace(a, b)
    open(p, 'w', encoding='utf-8').write(s)
F = 'rpggame/src/main/java/rpg/world/shadowveil/Fitout.java'
patch(F, [
("""            if (java.util.Arrays.asList(DEBUG.split(",")).contains(st.plan.s.id)) System.out.println("beds " + r.use + " count " + count + " kind " + Atlas.s(pc, "kind", "BED") + " left " + bedsLeft[0]);
""", ""),
# stalls: each partition kept only where the stable stays whole
("""            boolean ok = true;
            for (int v = r.v1 - deep + 1; v <= r.v1; v++) if (st.g[u][v] != OPEN) ok = false;
            if (!ok) continue;
            for (int v = r.v1 - deep + 1; v <= r.v1; v++) { c.set(p.f.wx(u, v), st.walk(), p.f.wz(u, v), Blocks.FENCE); st.g[u][v] = WALL; }""",
 """            List<int[]> boards = new ArrayList<>();
            for (int v = r.v1 - deep + 1; v <= r.v1; v++) boards.add(new int[]{u, v});
            if (!commit(st, boards, List.of())) continue;
            for (int[] q : boards) { c.set(p.f.wx(q[0], q[1]), st.walk(), p.f.wz(q[0], q[1]), Blocks.FENCE); st.g[q[0]][q[1]] = WALL; }"""),
])
p = 'rpggame/src/main/resources/data/shadowveil_interiors.json'
d = json.load(open(p, encoding='utf-8'))
P = d['interiors']['programs']
P['inn']['volumes'][0]['levels']['single'] = [{"use": "common_room", "share": 3}, {"use": "dormitory", "share": 2, "door": "plain"}, {"use": "inn_kitchen", "share": 1, "minStorey": 70}]
nave_rooms = [{"use": "nave", "share": 4, "axis": "v"}, {"use": "vestry", "share": 1, "door": "plain", "doorName": "vestry", "minStorey": 60}]
P['church']['levels']['single'] = nave_rooms
P['church']['levels']['ground'] = nave_rooms
P['church']['volumes'][0]['levels']['single'] = nave_rooms
P['church']['volumes'][0]['levels']['ground'] = nave_rooms
json.dump(d, open(p, 'w', encoding='utf-8'), indent=1, ensure_ascii=False)
print('ok')
