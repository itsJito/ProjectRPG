import re, json
def patch(p, pairs):
    s = open(p, encoding='utf-8').read()
    for a, b in pairs:
        assert s.count(a) == 1, (p, a[:100], s.count(a))
        s = s.replace(a, b)
    open(p, 'w', encoding='utf-8').write(s)
R = 'rpggame/src/main/java/rpg/'

# Census: the old bed planner and its slot map are dead (the interiors' own beds are given out now)
p = R + 'world/shadowveil/Census.java'
s = open(p, encoding='utf-8').read()
s = s.replace("""        Map<String, Integer> beds = new LinkedHashMap<>();\n""", "")
a = s.index("    /** The k-th bed planned in a home: inside its largest reserved room")
b = s.index("    /** A made person's means and abilities")
s = s[:a] + s[b:]
open(p, 'w', encoding='utf-8').write(s)

# ShadowVeil: the named services and posts are gone (the interiors' counters and posts replace them)
p = R + 'world/shadowveil/ShadowVeil.java'
s = open(p, encoding='utf-8').read()
a = s.index("    /**\n     * (0.4.1) A service's place (the architecture's 'services'")
b = s.index("    /**\n     * (0.4.1) ShadowVeil's gardens as the farming system's fields")
s = s[:a] + s[b:]
a = s.index("    /** The person whose post a service names (its 'posts': an Ids constant's name to a service), or null. */")
b = s.index("    /** The open water nearest a point that has a dry bank beside it")
s = s[:a] + s[b:]
open(p, 'w', encoding='utf-8').write(s)

patch(R + 'world/shadowveil/Settle.java', [
("""        // (0.4.1) a post the world names for this person (the teller at the bank in Loxford Castle's keep)
        float[] named = ShadowVeil.post(idName(n.id));
        if (named != null) { home = named; post = named; }
""", ""),
])
patch(R + 'world/shadowveil/Kit.java', [
("""        // the bank's ground-floor counter at the back of the hall (its high room comes with the keep's interior)
        out.points.put("bankTeller", kf.point(kw / 2.0 - 0.5, kd - 3));
        out.points.put("bankCustomer", kf.point(kw / 2.0 - 0.5, kd - 5));
""", ""),
])

# architecture data: services and posts removed; census: the posts table
p = 'rpggame/src/main/resources/data/shadowveil_architecture.json'
s = open(p, encoding='utf-8').read()
for key in ['_services', 'services', 'posts']:
    m = re.search(r'\n\s*"' + key + r'"\s*:\s*', s)
    assert m, key
    start = m.start()
    i = m.end()
    depth = 0
    if s[i] == '"':
        j = s.index('"', i + 1) + 1
    else:
        j = i
        while True:
            ch = s[j]
            if ch in '{[': depth += 1
            elif ch in '}]':
                depth -= 1
                if depth == 0: j += 1; break
            elif ch == '"':
                j = s.index('"', j + 1)
            j += 1
    # remove following comma
    end = j
    if s[end] == ',': end += 1
    s = s[:start] + s[end:]
json.loads(s)
open(p, 'w', encoding='utf-8').write(s)

p = 'rpggame/src/main/resources/data/shadowveil_census.json'
d = json.load(open(p, encoding='utf-8'))
c = d['census']
c['posts'] = {
    "_about": "(0.4.4) Where each person stands to work inside their workplace: the kinds of post they take there, in the order they want them - by words of their title first, then by calling, then by role. A post is one the interiors set out (teller, clerk, counter, bar, cook, kitchen, altar, desk, gaoler, guard, head, throne, mill, station:<STATION>); 'station:' alone takes any station. Whoever finds none works out of doors as before.",
    "byTitle": {"banker": ["teller", "clerk"], "prison guard": ["gaoler", "guard"], "innkeeper": ["bar", "desk", "counter"], "bartender": ["bar"],
                "king": ["throne", "head", "desk"], "duke": ["head", "desk", "clerk"], "count": ["desk", "head", "clerk"], "baron": ["desk", "head", "clerk"],
                "priest": ["altar", "desk"], "priestess": ["altar", "desk"], "cook": ["cook", "kitchen"], "curator": ["desk"], "clerk": ["clerk", "desk"],
                "steward": ["desk", "clerk"], "tailor": ["station:SPINNING_WHEEL", "station:LOOM", "station:CRAFT_TABLE"]},
    "byProfession": {"COOK": ["cook", "kitchen"], "INNKEEPER": ["bar", "desk"], "BARTENDER": ["bar"], "BLACKSMITH": ["station:ANVIL", "station:FURNACE"],
                     "ARMORER": ["station:ANVIL", "station:FURNACE"], "SMELTER": ["station:FURNACE"], "CARPENTER": ["station:SAWBENCH", "station:CRAFT_TABLE"],
                     "TAILOR": ["station:CRAFT_TABLE"], "CARDER": ["station:CARDING_BENCH"], "SPINNER": ["station:SPINNING_WHEEL"], "WEAVER": ["station:LOOM"],
                     "FULLER": ["station:FULLING_TUB"], "DYER": ["station:DYE_VAT"], "HOUSEHOLD_BARON": ["desk", "head", "clerk"]},
    "byRole": {"MERCHANT": ["counter", "bar", "clerk"], "PRIEST": ["altar", "desk"], "SCHOLAR": ["desk", "clerk"], "CIVIC": ["teller", "throne", "head", "desk", "clerk", "counter"],
               "CRAFTSMAN": ["station:"], "HEALER": ["desk"], "UNDERTAKER": ["desk"]}
}
c['stage']['guestBedsForResidents'] = True
json.dump(d, open(p, 'w', encoding='utf-8'), indent=1, ensure_ascii=False)

# Offstage: the night's going-in only for those with no bed indoors (a camp's, a cave's)
patch(R + 'ai/Offstage.java', [
("""            else if (n.loaded && night() && n.goal == Brain.Goal.SLEEP && free(n) && rpg.world.shadowveil.Census.of(n.id) != null
                    && Math.hypot(n.x - n.homeX, n.z - n.homeZ) < 1.8f) {""",
 """            // (0.4.4) everyone with a bed indoors now walks in to it and sleeps in it, seen by whoever comes in
            else if (n.loaded && night() && n.goal == Brain.Goal.SLEEP && free(n) && rpg.world.shadowveil.Census.of(n.id) != null && !Float.isFinite(n.homeY)
                    && Math.hypot(n.x - n.homeX, n.z - n.homeZ) < 1.8f) {"""),
])

# Brain: ShadowVeil's churches' altars for the morning prayers, the places before an altar whichever way it faces
patch(R + 'ai/Brain.java', [
("""    private float[] altarFor(Npc n) {
        if (DevMap.PALACE.equals(n.homeJurisdiction)) return rpg.world.Expanse.ALTAR;""",
 """    private float[] altarFor(Npc n) {
        // (0.4.4) on ShadowVeil their kingdom's church (its altar and the step before it)
        if (rpg.world.WorldMap.shadowveil()) { float[] a = rpg.world.shadowveil.Rooms.altarFor(n.homeJurisdiction, n.homeX, n.homeZ, ALTAR_REACH); return a == null ? null : new float[]{a[0], a[2], a[3], a[4], a[1]}; }
        if (DevMap.PALACE.equals(n.homeJurisdiction)) return rpg.world.Expanse.ALTAR;"""),
("""    private float[] prayerPlace(Npc n, float[] altar) {
        float[] best = null; float bd = Float.MAX_VALUE;
        for (int row = 0; row < 3; row++) for (int col = -2; col <= 2; col++) {
            float x = altar[0] + col * 1.7f + (row % 2) * 0.85f, z = altar[1] + 2.0f + row * 1.9f;""",
 """    /** (0.4.4) How far a person goes to their kingdom's church for the morning prayers (m). */
    static final float ALTAR_REACH = rpg.Tuning.f("ai.altarReach", 220f);
    private float[] prayerPlace(Npc n, float[] altar) {
        float[] best = null; float bd = Float.MAX_VALUE;
        // which way the altar faces: toward the step before it (ShadowVeil's), else +z (the devmap's)
        float fx = 0, fz = 1;
        if (altar.length >= 4) { float dx = altar[2] - altar[0], dz = altar[3] - altar[1], l = (float) Math.hypot(dx, dz); if (l > 0.1f) { fx = dx / l; fz = dz / l; } }
        for (int row = 0; row < 3; row++) for (int col = -2; col <= 2; col++) {
            float out = 2.0f + row * 1.9f, side = col * 1.7f + (row % 2) * 0.85f;
            float x = altar[0] + fx * out - fz * side, z = altar[1] + fz * out + fx * side;"""),
])
print('ok')
