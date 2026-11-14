def patch(p, pairs):
    s = open(p, encoding='utf-8').read()
    for a, b in pairs:
        assert s.count(a) == 1, (p, a[:100], s.count(a))
        s = s.replace(a, b)
    open(p, 'w', encoding='utf-8').write(s)
R = 'rpggame/src/main/java/rpg/'
patch(R + 'world/shadowveil/Architecture.java', [
("""        for (Map<String, Object> s : Atlas.list(ar.data, "settlements")) for (Map<String, Object> dd : Atlas.list(s, "districts")) ar.planDistrict(s, dd);
        ar.lanes();""",
 """        for (Map<String, Object> s : Atlas.list(ar.data, "settlements")) for (Map<String, Object> dd : Atlas.list(s, "districts")) ar.planDistrict(s, dd);
        // (0.4.4) the civic buildings added after the streets were laid (the gaols, banks and graveyards): on free ground
        for (Map<String, Object> s : Atlas.list(ar.data, "settlements")) {
            Kit.Style st = ar.style(Atlas.s(s, "style", "wilds"));
            for (Map<String, Object> b : Atlas.list(s, "later")) ar.place(Atlas.s(s, "id", "?"), st, b);
        }
        ar.lanes();"""),
])
patch(R + 'world/shadowveil/Census.java', [
("""        List<Map<String, Object>> people = Atlas.list(data(), "people");""",
 """        List<Map<String, Object>> people = everyone();"""),
("""    /** A census entry's person id: their roster Ids constant's, else their own on ShadowVeil (Ids.SV_PEOPLE + their index). */""",
 """    /** (0.4.4) The lore's roll and, after it, the people added to keep the new gaols, banks and graveyards. */
    public static List<Map<String, Object>> everyone() {
        List<Map<String, Object>> l = new ArrayList<>(Atlas.list(data(), "people"));
        l.addAll(Atlas.list(data(), "additions"));
        return l;
    }

    /** A census entry's person id: their roster Ids constant's, else their own on ShadowVeil (Ids.SV_PEOPLE + their index). */"""),
])
patch(R + 'world/shadowveil/Fitout.java', [
("""        for (Map<String, Object> p : Atlas.list(ar.a.map("census"), "people")) {""",
 """        List<Map<String, Object>> roll = new ArrayList<>(Atlas.list(ar.a.map("census"), "people"));
        roll.addAll(Atlas.list(ar.a.map("census"), "additions"));
        for (Map<String, Object> p : roll) {"""),
])
# the graveyard keeps its newest rows for the undertakers' burials, and says where
patch(R + 'world/shadowveil/Kit.java', [
("""        int mark = s.b("rock");
        boolean crypt = o.b("crypt", false);
        for (int u = 2; u < w - 2; u += 3) for (int v = 3; v < d - (crypt ? 9 : 2); v += 3) {
            if (u >= w / 2 - 2 && u <= w / 2 + 1) continue;""",
 """        int mark = s.b("rock");
        boolean crypt = o.b("crypt", false);
        // (0.4.4, gamefixes: each kingdom its own graveyard, the burials its undertakers make) the rows left of the
        // middle path nearest the gate are kept open for new graves: the burial plot (its stones set as the dead come)
        int plotU0 = 1, plotU1 = w / 2 - 3, plotV0 = 2, plotV1 = Math.min(d - (crypt ? 10 : 3), 2 + 5);
        for (int u = 2; u < w - 2; u += 3) for (int v = 3; v < d - (crypt ? 9 : 2); v += 3) {
            if (u >= w / 2 - 2 && u <= w / 2 + 1) continue;
            if (u >= plotU0 && u <= plotU1 && v >= plotV0 && v <= plotV1) continue;"""),
("""        fence(f, s, 0, 0, w - 1, d - 1, P, w / 2 - 1);
        if (crypt) {""",
 """        fence(f, s, 0, 0, w - 1, d - 1, P, w / 2 - 1);
        if (plotU1 - plotU0 >= 2 && plotV1 - plotV0 >= 2) {
            int ax = f.wx(plotU0, plotV0), az = f.wz(plotU0, plotV0), bx = f.wx(plotU1, plotV1), bz = f.wz(plotU1, plotV1);
            int x0 = Math.min(ax, bx), z0 = Math.min(az, bz), x1 = Math.max(ax, bx), z1 = Math.max(az, bz);
            out.points.put("plot", new float[]{x0 + 0.7f, z0 + 0.75f, Math.max(1, (int) ((x1 - x0 + 1) / 1.4f)), Math.max(1, (int) ((z1 - z0 + 1) / 1.5f))});
        }
        if (crypt) {"""),
])
patch(R + 'Rpg.java', [
("""        if (map.kind == rpg.world.WorldMap.Kind.DEVMAP) graveyards.plots.add(new rpg.law.Graveyards.Plot("DevCity graveyard", DevMap.ALPHA, 113, rpg.world.Ground.at(113, 89), 89, 8, 4));""",
 """        if (map.kind == rpg.world.WorldMap.Kind.DEVMAP) graveyards.plots.add(new rpg.law.Graveyards.Plot("DevCity graveyard", DevMap.ALPHA, 113, rpg.world.Ground.at(113, 89), 89, 8, 4));
        // (0.4.4, gamefixes: each kingdom its own graveyard) every graveyard of ShadowVeil keeps its plot for the burials
        else if (rpg.world.shadowveil.ShadowVeil.terrain() != null && rpg.world.shadowveil.ShadowVeil.terrain().arch != null)
            for (var s : rpg.world.shadowveil.ShadowVeil.terrain().arch.structures) {
                float[] plot = s.points.get("plot");
                if (plot == null || !s.template.equals("graveyard")) continue;
                graveyards.plots.add(new rpg.law.Graveyards.Plot(s.name, rpg.world.shadowveil.ShadowVeil.jurisdiction(s.cx(), s.cz()), plot[0], rpg.world.shadowveil.ShadowVeil.standY(plot[0], plot[1]), plot[1], (int) plot[2], (int) plot[3]));
            }"""),
])
print('ok')
