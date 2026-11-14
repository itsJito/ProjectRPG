def patch(p, pairs):
    s = open(p, encoding='utf-8').read()
    for a, b in pairs:
        assert s.count(a) == 1, (p, a[:100], s.count(a))
        s = s.replace(a, b)
    open(p, 'w', encoding='utf-8').write(s)
R = 'rpggame/src/main/java/rpg/'
patch(R + 'world/shadowveil/Architecture.java', [
("""            for (Map<String, Object> b : Atlas.list(s, "later")) { Map<String, Object> o = new HashMap<>(b); o.put("later", true); ar.place(Atlas.s(s, "id", "?"), st, o); }""",
 """            for (Map<String, Object> b : Atlas.list(s, "later")) {
                // inside its own kingdom's bounds where there is room there, else the nearest ground that will take it
                Map<String, Object> o = new HashMap<>(b); o.put("later", true); o.put("realm", Atlas.s(s, "realm", ""));
                if (ar.place(Atlas.s(s, "id", "?"), st, o) == null) { o.remove("realm"); ar.failures.removeIf(f -> f.startsWith("no ground for " + Atlas.s(b, "id", "?"))); ar.place(Atlas.s(s, "id", "?"), st, o); }
            }"""),
("""    private boolean laterFits(Structure s, int x0, int z0, int x1, int z1) {
        for (Structure o : structures) {
            if (o.x1 < x0 - 6 || o.x0 > x1 + 6 || o.z1 < z0 - 6 || o.z0 > z1 + 6) continue;""",
 """    private boolean laterFits(Structure s, int x0, int z0, int x1, int z1) {
        String realm = Atlas.s(s.opts, "realm", "");
        if (!realm.isEmpty() && !realm.equals(ShadowVeil.realmAt(a, (x0 + x1) / 2, (z0 + z1) / 2))) return false;
        for (Structure o : structures) {
            if (o.x1 < x0 - 6 || o.x0 > x1 + 6 || o.z1 < z0 - 6 || o.z0 > z1 + 6) continue;
            // a clear way round every building already standing (its back doors, its eaves, its eased ground)
            if (!YARDS.contains(o.template) && o.x0 - 4 <= x1 && x0 <= o.x1 + 4 && o.z0 - 4 <= z1 && z0 <= o.z1 + 4) return false;"""),
("""    public Structure byId(String id) { for (Structure s : structures) if (s.id.equals(id)) return s; return null; }""",
 """    public Structure byId(String id) { for (Structure s : structures) if (s.id.equals(id)) return s; return null; }
    /** (0.4.4) The kingdom a structure's settlement belongs to (its data's realm), else the ground's under it. */
    public String realmOf(Structure s) {
        for (Map<String, Object> st : Atlas.list(data, "settlements")) if (Atlas.s(st, "id", "").equals(s.settlement) && st.get("realm") instanceof String r) return r;
        return ShadowVeil.realmAt(a, (int) s.cx(), (int) s.cz());
    }"""),
])
patch(R + 'world/shadowveil/Rooms.java', [
("""                ALTAR_REALMS.add(ShadowVeil.jurisdiction(f.x(), f.z()));""",
 """                Architecture.Structure st = ar.byId(f.owner());
                ALTAR_REALMS.add(st == null ? ShadowVeil.jurisdiction(f.x(), f.z()) : ar.realmOf(st));"""),
("""            String realm = ShadowVeil.jurisdiction(c.gateX() + 0.5f, c.gateZ() + 0.5f);""",
 """            Architecture.Structure gaol = ar.byId(c.owner());
            String realm = gaol == null ? ShadowVeil.jurisdiction(c.gateX() + 0.5f, c.gateZ() + 0.5f) : ar.realmOf(gaol);"""),
])
patch(R + 'Rpg.java', [
("""                graveyards.plots.add(new rpg.law.Graveyards.Plot(s.name, rpg.world.shadowveil.ShadowVeil.jurisdiction(s.cx(), s.cz()), plot[0],""",
 """                graveyards.plots.add(new rpg.law.Graveyards.Plot(s.name, rpg.world.shadowveil.ShadowVeil.terrain().arch.realmOf(s), plot[0],"""),
])
print('ok')
