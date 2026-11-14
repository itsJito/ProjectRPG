import os, re
os.chdir(r'C:\Users\John\Documents\ProjectRPG\rpggame\src\main\java\rpg')
def patch(path, pairs):
    s = open(path, encoding='utf-8').read()
    for old, new in pairs:
        assert old in s, (path, old[:90])
        s = s.replace(old, new, 1)
    open(path, 'w', encoding='utf-8').write(s)

patch('world/shadowveil/Fitout.java', [
 ('''            String prof = Atlas.s(p, "profession", null);
            if (w != null && prof != null) trades.computeIfAbsent(w, k -> new ArrayList<>()).add(prof);
        }''',
  '''            String prof = Atlas.s(p, "profession", null);
            // (0.4.5, gamefixes: furnaces and the like stood out of doors across every kingdom) a calling's stations go
            // inside: in the building it works in, or - where it works out of doors (a yard, a mine, a field) - in its
            // own home
            String in = w != null && indoors(w) ? w : h != null && indoors(h) ? h : null;
            if (in != null && prof != null) trades.computeIfAbsent(in, k -> new ArrayList<>()).add(prof);
        }'''),
 ('''    // ================================================================== the whole world''',
  '''    /** Whether a building is fitted out (enterable, above ground, with rooms). */
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
            if (!done) fitted.defects.add("no room inside for its " + k.toLowerCase(java.util.Locale.ROOT).replace('_', ' '));
        }
    }

    // ================================================================== the whole world'''),
 ('''        for (Storey st : all) for (Room r : st.rooms) furnish(st, r, fitted);
        for (Storey st : all) reachCheck(st, fitted);''',
  '''        for (Storey st : all) for (Room r : st.rooms) furnish(st, r, fitted);
        if (!all.isEmpty()) ensureTrades(s, all, fitted);
        for (Storey st : all) reachCheck(st, fitted);'''),
])

# the out-of-doors stations are gone
s = open('world/shadowveil/Census.java', encoding='utf-8').read()
i = s.index('    /**\n     * The workplaces\' stations (the data\'s \'stations\' by calling)')
j = s.index('    /** Everyone the census placed whose stand-ins or bed are missing')
s = s[:i] + s[j:]
open('world/shadowveil/Census.java', 'w', encoding='utf-8').write(s)
patch('ai/AiSystem.java', [('''        rpg.world.shadowveil.Census.worksites(g);\n''', '')])
print('ok')
