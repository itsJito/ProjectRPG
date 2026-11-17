p = 'src/main/java/rpg/world/shadowveil/Fitout.java'
s = open(p, encoding='utf-8').read()
a = """        if (!rec.lit) {
            if (!placeWall(st, r, fitted, Map.of("kind", "CANDELABRA", "place", "corner"), true) && !placeWall(st, r, fitted, Map.of("kind", "CANDELABRA"), false)
                    && !placeCeiling(st, r, fitted, Map.of("kind", "CHANDELIER", "minClear", 3)) && !placeWall(st, r, fitted, Map.of("kind", "NIGHTSTAND"), false))
                placeCeiling(st, r, fitted, Map.of("kind", "CHANDELIER", "minClear", 3, "nearStairs", true));
        }"""
b = """        // (0.4.7, gamefixes: chandeliers in every house, some low enough to strike a head) a plain house's room is lit by
        // candles and a nightstand's lamp; a chandelier is a fine house's, a hall's or a great building's, and hangs only
        // where its candles clear a tall man's head ('chandelierMinClear' cells of headroom)
        boolean fine = chandelierWealth().contains(fitted.wealth);
        if (!rec.lit) {
            if (!placeWall(st, r, fitted, Map.of("kind", "CANDELABRA", "place", "corner"), true) && !placeWall(st, r, fitted, Map.of("kind", "CANDELABRA"), false)
                    && !(fine && placeCeiling(st, r, fitted, Map.of("kind", "CHANDELIER"))) && !placeWall(st, r, fitted, Map.of("kind", "NIGHTSTAND"), false)
                    && !placeWall(st, r, fitted, Map.of("kind", "CANDELABRA", "w", 1), false))
                placeCeiling(st, r, fitted, Map.of("kind", "CHANDELIER", "nearStairs", true));
        }"""
assert a in s
s = s.replace(a, b, 1)
a = """        if (r.area() >= per * 2) placeCeiling(st, r, fitted, Map.of("kind", "CHANDELIER", "minClear", 4));"""
b = """        if (r.area() >= per * 2 && fine) placeCeiling(st, r, fitted, Map.of("kind", "CHANDELIER"));"""
assert a in s
s = s.replace(a, b, 1)
a = """    private boolean placeCeiling(Storey st, Room r, Architecture.Fitted fitted, Map<String, Object> pc) {
        Plan p = st.plan;
        if (st.clear < Atlas.i(pc, "minClear", 4)) return false;"""
b = """    private boolean placeCeiling(Storey st, Room r, Architecture.Fitted fitted, Map<String, Object> pc) {
        Plan p = st.plan;
        if (st.clear < Math.max(Atlas.i(pc, "minClear", 4), Atlas.i(data, "chandelierMinClear", 4))) return false;"""
assert a in s
s = s.replace(a, b, 1)
a = """    /** Whether a chandelier may hang over a cell:"""
b = """    /** (0.4.7) The buildings' wealths that hang chandeliers (interiors data 'chandelierWealth'). */
    private java.util.List<?> chandelierWealth() { List<?> l = Atlas.arr(data, "chandelierWealth"); return l.isEmpty() ? List.of("rich", "noble", "royal") : l; }

    /** Whether a chandelier may hang over a cell:"""
assert a in s
s = s.replace(a, b, 1)
# the hanging height: never lower than the clearance allows (the piece's height is the headroom it hangs from)
open(p, 'w', encoding='utf-8').write(s)

import json, collections
pi = 'src/main/resources/data/shadowveil_interiors.json'
I = json.load(open(pi, encoding='utf-8'), object_pairs_hook=collections.OrderedDict)
i = I['interiors']
i['_chandeliers'] = "(0.4.7, gamefixes: chandeliers in every house, low enough to touch) a chandelier's candles hang 1.4 m under its ceiling: it is hung only under 'chandelierMinClear' cells of headroom, and only in buildings of these wealths (a plain house is lit by candle stands and a nightstand)"
i['chandelierMinClear'] = 4
i['chandelierWealth'] = ["rich", "noble", "royal"]
open(pi, 'w', encoding='utf-8').write(json.dumps(I, indent=1, ensure_ascii=False) + "\n")
print(sorted(set(v.get('wealth', '-') for v in i['programs'].values() if isinstance(v, dict))))
