def patch(p, pairs):
    s = open(p, encoding='utf-8').read()
    for a, b in pairs:
        assert s.count(a) == 1, (p, a[:100], s.count(a))
        s = s.replace(a, b)
    open(p, 'w', encoding='utf-8').write(s)
R = 'rpggame/src/main/java/rpg/'
patch(R + 'world/shadowveil/Architecture.java', [
("""            for (Map<String, Object> b : Atlas.list(s, "later")) ar.place(Atlas.s(s, "id", "?"), st, b);""",
 """            for (Map<String, Object> b : Atlas.list(s, "later")) { Map<String, Object> o = new HashMap<>(b); o.put("later", true); ar.place(Atlas.s(s, "id", "?"), st, o); }"""),
("""            if (!free(x0, z0, x0 + sx - 1, z0 + sz - 1, margin, maxSlope, water, onRoad)) continue;
            s.x0 = x0; s.z0 = z0; s.x1 = x0 + sx - 1; s.z1 = z0 + sz - 1;""",
 """            if (!free(x0, z0, x0 + sx - 1, z0 + sz - 1, margin, maxSlope, water, onRoad)) continue;
            if (Boolean.TRUE.equals(s.opts.get("later")) && !laterFits(s, x0, z0, x0 + sx - 1, z0 + sz - 1)) continue;
            s.x0 = x0; s.z0 = z0; s.x1 = x0 + sx - 1; s.z1 = z0 + sz - 1;"""),
("""    private int waterLevel(Structure s) {""",
 """    /**
     * (0.4.4) Whether a building added after the streets fits a lot: never across another building's front (the ground
     * three cells before its face, where its door and its lane are), and with a road near its own front for its lane.
     */
    private boolean laterFits(Structure s, int x0, int z0, int x1, int z1) {
        for (Structure o : structures) {
            if (o.x1 < x0 - 6 || o.x0 > x1 + 6 || o.z1 < z0 - 6 || o.z0 > z1 + 6) continue;
            int fx0 = o.x0, fz0 = o.z0, fx1 = o.x1, fz1 = o.z1;
            switch (o.facing) { case 'n' -> { fz1 = o.z0 - 1; fz0 = o.z0 - 4; } case 's' -> { fz0 = o.z1 + 1; fz1 = o.z1 + 4; } case 'w' -> { fx1 = o.x0 - 1; fx0 = o.x0 - 4; } default -> { fx0 = o.x1 + 1; fx1 = o.x1 + 4; } }
            if (fx0 <= x1 && x0 <= fx1 && fz0 <= z1 && z0 <= fz1) return false;
        }
        int cx = (x0 + x1) / 2, cz = (z0 + z1) / 2;
        int[] out = switch (s.facing) { case 'n' -> new int[]{cx, z0 - 3}; case 's' -> new int[]{cx, z1 + 3}; case 'w' -> new int[]{x0 - 3, cz}; default -> new int[]{x1 + 3, cz}; };
        int reach = Atlas.i(s.opts, "nearRoad", 16);
        for (int dx = -reach; dx <= reach; dx++) for (int dz = -reach; dz <= reach; dz++) {
            int x = out[0] + dx, z = out[1] + dz;
            if (t.in(x, z) && (t.flags[t.idx(x, z)] & Terrain.ROAD) != 0) return true;
        }
        return false;
    }
    private int waterLevel(Structure s) {"""),
])
print('ok')
