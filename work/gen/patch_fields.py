import json
ROOT = 'C:/Users/John/Documents/ProjectRPG/rpggame/src/main/'
# data: each kingdom's field crops
p = ROOT + 'resources/data/shadowveil_census.json'
d = json.load(open(p, encoding='utf-8'))
d['census']['fields'] = {
    "_about": "(0.4.3, gamefixes: farms did not work or have their crops) every field and garden the architecture raised is farmed: its rows sown with its kingdom's crops (row by row, cycling), kept by the crop farmer the census sets to work there - else the nearest crop farmer of its kingdom within 'reach' cells - or, with nobody to keep it, open to anyone. 'unfarmed' names the ones that are not (the vineyard's vines).",
    "templates": ["field", "garden"], "reach": 240, "unfarmed": ["vineyard"],
    "crops": {"Loxford": [["BARLEY"], ["POTATO", "CABBAGE"], ["CARROT", "ONION"]], "Harlette": [["CABBAGE", "POTATO"], ["CARROT"], ["ONION", "BARLEY"]],
              "Valgard": [["BARLEY"], ["POTATO"], ["CABBAGE", "ONION"]], "Gorrick": [["POTATO"], ["CABBAGE", "CARROT"], ["ONION"]],
              "Azharad": [["COTTON"], ["TOMATO", "ONION"], ["SWEETCORN"]], "Severia": [["TOMATO"], ["ONION", "SWEETCORN"], ["HOPS"]],
              "Camelot": [["BARLEY"], ["CABBAGE", "POTATO"]], "_default": [["CARROT", "POTATO"], ["CABBAGE"]]}}
json.dump(d, open(p, 'w', encoding='utf-8'), indent=1, ensure_ascii=False)

p = ROOT + 'java/rpg/world/shadowveil/ShadowVeil.java'
s = open(p, encoding='utf-8').read()
old = '''            out.add(new rpg.world.Farming.Field(s.name, jurisdiction(s.cx(), s.cz()), owner, s.x0, s.z0, s.x1, s.z1, rowsA, -1, gate, rc, s.floor + 1));
        }
        return out.toArray(new rpg.world.Farming.Field[0]);'''
new = '''            out.add(new rpg.world.Farming.Field(s.name, jurisdiction(s.cx(), s.cz()), owner, s.x0, s.z0, s.x1, s.z1, rowsA, -1, gate, rc, s.floor + 1));
            farmed.add(s.id);
        }
        // (0.4.3, gamefixes: farms did not work or have their crops) every other field and garden, farmed too
        Map<String, Object> fd = Atlas.map(Atlas.map(Atlas.get().root, "census"), "fields");
        List<?> templates = Atlas.arr(fd, "templates"), unfarmed = Atlas.arr(fd, "unfarmed");
        Map<String, Object> cropsBy = Atlas.map(fd, "crops");
        for (var s : t.arch.structures) {
            if (farmed.contains(s.id) || !templates.contains(s.template) || unfarmed.contains(s.id) || s.x1 - s.x0 < 4 || s.z1 - s.z0 < 4) continue;
            String kingdom = kingdomOf(s);
            long owner = farmerFor(s, kingdom, Atlas.d(fd, "reach", 240));
            List<Integer> rows = new ArrayList<>();
            for (int z = s.z0 + 2; z <= s.z1 - 2; z += 2) rows.add(z);
            if (rows.isEmpty()) continue;
            List<?> crops = Atlas.arr(cropsBy, kingdom);
            if (crops.isEmpty()) crops = Atlas.arr(cropsBy, "_default");
            rpg.world.Farming.Crop[][] rc = new rpg.world.Farming.Crop[rows.size()][];
            for (int k = 0; k < rc.length; k++) {
                List<?> row = crops.isEmpty() ? List.of("CARROT") : (List<?>) crops.get(k % crops.size());
                rc[k] = new rpg.world.Farming.Crop[row.size()];
                for (int j = 0; j < row.size(); j++) rc[k][j] = rpg.world.Farming.Crop.valueOf(String.valueOf(row.get(j)));
            }
            int[] rowsA = rows.stream().mapToInt(Integer::intValue).toArray();
            int gx = (s.x0 + s.x1) / 2;
            out.add(new rpg.world.Farming.Field(s.name, kingdom, owner, s.x0, s.z0, s.x1, s.z1, rowsA, -1, new int[][]{{gx, s.z0}, {gx + 1, s.z0}}, rc, s.floor + 1));
        }
        return out.toArray(new rpg.world.Farming.Field[0]);'''
assert old in s
s = s.replace(old, new)
old = '''        List<rpg.world.Farming.Field> out = new ArrayList<>();
        Terrain t = terrain;'''
new = '''        List<rpg.world.Farming.Field> out = new ArrayList<>();
        java.util.Set<String> farmed = new java.util.HashSet<>();
        Terrain t = terrain;'''
assert old in s
s = s.replace(old, new)
old = '''    /** The person whose post a service names'''
new = '''    /** (0.4.3) The kingdom a structure belongs to: its settlement's, else whose law holds where it stands. */
    static String kingdomOf(Architecture.Structure s) {
        for (Realm r : REALMS) if (s.settlement != null && r.name.equalsIgnoreCase(s.settlement)) return r.name;
        return jurisdiction(s.cx(), s.cz());
    }
    /**
     * (0.4.3) Who keeps a field: the census's crop farmer set to work there, else the nearest crop farmer of its kingdom
     * within reach (their Ids constant's id, or their own on ShadowVeil); 0 - nobody's, open to anyone.
     */
    static long farmerFor(Architecture.Structure s, String kingdom, double reach) {
        List<Map<String, Object>> people = Atlas.list(Atlas.map(Atlas.get().root, "census"), "people");
        long best = 0; double bd = reach;
        for (int k = 0; k < people.size(); k++) {
            Map<String, Object> p = people.get(k);
            if (!"CROP_FARMER".equals(p.get("profession"))) continue;
            long id = Census.idOf(p, k);
            if (s.id.equals(p.get("work"))) return id;
            if (!kingdom.equals(p.get("kingdom"))) continue;
            var w = p.get("work") instanceof String ws && terrain != null && terrain.arch != null ? terrain.arch.byId(ws) : null;
            if (w == null) continue;
            double d = Math.hypot(w.cx() - s.cx(), w.cz() - s.cz());
            if (d < bd) { bd = d; best = id; }
        }
        return best;
    }

    /** The person whose post a service names'''
assert old in s
s = s.replace(old, new)
open(p, 'w', encoding='utf-8').write(s)

p = ROOT + 'java/rpg/world/shadowveil/Census.java'
s = open(p, encoding='utf-8').read()
old = '''    private static int slot('''
new = '''    /** A census entry's person id: their roster Ids constant's, else their own on ShadowVeil (Ids.SV_PEOPLE + their index). */
    public static long idOf(Map<String, Object> p, int index) {
        String body = Atlas.s(p, "body", null);
        if (body != null) try { return Ids.class.getField(body).getLong(null); } catch (ReflectiveOperationException ignored) { }
        return Ids.SV_PEOPLE + index;
    }

    private static int slot('''
assert old in s
s = s.replace(old, new, 1)
open(p, 'w', encoding='utf-8').write(s)

# furrows along the plots' rows
p = ROOT + 'java/rpg/world/shadowveil/Kit.java'
s = open(p, encoding='utf-8').read()
old = '''        for (int u = 1; u < w - 1; u++) for (int v = 1; v < d - 1; v++) {
            int x = f.wx(u, v), z = f.wz(u, v), g = f.c.ground(x, z);
            if (f.c.get(x, g, z) == Blocks.WATER) continue;
            f.c.set(x, g, z, (u % 2 == 0) ? furrow : crop);'''
new = '''        // (0.4.3) the furrows run east-west on the lot's even rows from its north edge - where the farming system's rows of
        // plots lie (Farming.Field rows z0 + 2, z0 + 4 ...), whichever way the field faces
        int zNorth = Math.min(f.wz(0, 0), f.wz(w - 1, d - 1));
        for (int u = 1; u < w - 1; u++) for (int v = 1; v < d - 1; v++) {
            int x = f.wx(u, v), z = f.wz(u, v), g = f.c.ground(x, z);
            if (f.c.get(x, g, z) == Blocks.WATER) continue;
            f.c.set(x, g, z, ((z - zNorth) % 2 == 0) ? furrow : crop);'''
assert old in s
s = s.replace(old, new)
open(p, 'w', encoding='utf-8').write(s)
print('ok')
