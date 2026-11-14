import json
p = 'C:/Users/John/Documents/ProjectRPG/rpggame/src/main/java/rpg/fishing/Fishery.java'
s = open(p, encoding='utf-8').read()
old = '''            waters.add(w);
        }
    }

    public Fish release('''
new = '''            waters.add(w);
        }
        allWaters(stock);
    }

    /**
     * (0.4.3, gamefixes: no fish in any of the oceans, rivers, lakes or ponds) every water of ShadowVeil holds fish, not
     * only the fishing spots' reaches: the map in tiles ('fishery.tile' cells), each with enough water ('fishery.minCells')
     * a water of the kind most of its cells are - a river, a lake or pond, the sea's shore, coast or open deep by its
     * depth - stocked as its kind is, in proportion to how much water it holds. The spots' own waters come first (a
     * fisher's catch is still their spot's), these after.
     */
    private void allWaters(java.util.Map<String, Object> stock) {
        var t = rpg.world.shadowveil.ShadowVeil.terrain();
        if (t == null) return;
        java.util.Map<String, Object> fishery = rpg.world.shadowveil.Atlas.map(rpg.world.shadowveil.Atlas.get().root, "fishery");
        int tile = rpg.world.shadowveil.Atlas.i(fishery, "tile", 48), min = rpg.world.shadowveil.Atlas.i(fishery, "minCells", 24);
        int shore = rpg.world.shadowveil.Atlas.i(fishery, "shoreDepth", 2), deep = rpg.world.shadowveil.Atlas.i(fishery, "deepDepth", 7);
        for (int x0 = 0; x0 < t.S; x0 += tile) for (int z0 = 0; z0 < t.S; z0 += tile) {
            int river = 0, lake = 0, sh = 0, co = 0, off = 0;
            for (int x = x0; x < Math.min(t.S, x0 + tile); x++) for (int z = z0; z < Math.min(t.S, z0 + tile); z++) {
                int i = t.idx(x, z);
                if (t.water[i] < 0 || t.deck[i] >= 0) continue;
                int f = t.flags[i], depth = t.water[i] - t.top[i];
                if (depth < 1) continue;
                if ((f & rpg.world.shadowveil.Terrain.SEA) != 0) { if (depth <= shore) sh++; else if (depth >= deep) off++; else co++; }
                else if ((f & rpg.world.shadowveil.Terrain.RIVER) != 0) river++;
                else lake++;
            }
            int total = river + lake + sh + co + off;
            if (total < min) continue;
            String kind; int cls, cells;
            if (off >= Math.max(co, sh) && off >= river + lake) { kind = "OFFSHORE"; cls = FishKind.W.DEEP; cells = off; }
            else if (co + sh >= river + lake) { kind = co >= sh ? "COAST" : "SHORE"; cls = co >= sh ? FishKind.W.SEA : FishKind.W.SHORE; cells = co + sh; }
            else if (river >= lake) { kind = "RIVER"; cls = FishKind.W.RIVER; cells = river; }
            else { kind = lake < 120 ? "POND" : "LAKE"; cls = lake < 120 ? FishKind.W.POND : FishKind.W.LAKE; cells = lake; }
            Water w = new Water(waterName(kind, cls, x0 + tile / 2, z0 + tile / 2), cls, x0, z0, Math.min(t.S, x0 + tile) - 1, Math.min(t.S, z0 + tile) - 1);
            float share = Math.min(1f, cells / (float) rpg.world.shadowveil.Atlas.i(fishery, "fullCells", 400));
            for (var e : rpg.world.shadowveil.Atlas.map(stock, kind).entrySet()) {
                try { if (e.getValue() instanceof Number n) { int c = Math.round(n.intValue() * share); if (c > 0) w.hold(FishKind.valueOf(e.getKey()), c); } }
                catch (IllegalArgumentException unknown) { System.err.println("[fishery] no fish called " + e.getKey()); }
            }
            if (w.total() > 0) waters.add(w);
        }
    }
    /** A water's name: the lake's, the realm's or the wilds' it lies in, by its kind. */
    private static String waterName(String kind, int cls, int x, int z) {
        String where = rpg.world.shadowveil.ShadowVeil.jurisdiction(x, z);
        String what = switch (kind) { case "OFFSHORE" -> "the open sea"; case "COAST" -> "the coastal waters"; case "SHORE" -> "the shallows"; case "RIVER" -> "the river";
            default -> cls == FishKind.W.POND ? "the pond" : "the lake"; };
        return DevMap.WILDS.equals(where) ? what : what + " by " + where;
    }

    public Fish release('''
assert old in s
s = s.replace(old, new, 1)
open(p, 'w', encoding='utf-8').write(s)

p = 'C:/Users/John/Documents/ProjectRPG/rpggame/src/main/resources/data/shadowveil.json'
d = json.load(open(p, encoding='utf-8'))
f = d['fishery']
f['tile'] = 48; f['minCells'] = 24; f['fullCells'] = 400; f['shoreDepth'] = 2; f['deepDepth'] = 7
f['_aboutAll'] = "(0.4.3) every water of the map holds fish: the map in tiles of 'tile' cells, a tile with at least 'minCells' water cells a water of the kind most of them are (the sea by its depth: up to 'shoreDepth' the shore, from 'deepDepth' offshore, the coast between), stocked as that kind is above in proportion to its cells (full stock at 'fullCells')"
if 'POND' not in f['stock']:
    f['stock']['POND'] = {"GUPPY": 8, "SHRIMP": 6, "TROUT": 2, "BASS": 2}
open(p, 'w', encoding='utf-8').write(json.dumps(d, indent=1, ensure_ascii=False))
print('ok')
