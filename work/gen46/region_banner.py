import json
# 1) region names in data
P = r'C:\Users\John\Documents\ProjectRPG\rpggame\src\main\resources\data\shadowveil.json'
s = open(P, encoding='utf-8').read()
names = {'loxford_lowlands': 'Loxford Lowlands', 'southern_marches': 'Southern Marches', 'azharad_basin': 'Azharad Basin',
         'valgard_highlands': 'Valgard Highlands', 'western_crags': 'Western Crags', 'gallows_heath': 'Gallows Heath',
         'gorrick_shoulder': 'Gorrick Shoulder', 'dimwater_basin': 'Dimwater Basin', 'dead_wood': 'Dead Wood',
         'camelot_shoulder': 'Camelot Shoulder', 'ashmaw_coast': 'Ashmaw Coast', 'severian_north': 'Severian North'}
if '"name": "Loxford Lowlands"' not in s:
    for k, v in names.items():
        pat = '"id": "%s",' % k
        i0 = s.index('"regions"'); i1 = s.index(pat, i0)
        s = s[:i1] + '"id": "%s", "name": "%s",' % (k, v) + s[i1 + len(pat):]
    open(P, 'w', encoding='utf-8').write(s)
json.loads(s)

# 2) Terrain.regionName
P = r'C:\Users\John\Documents\ProjectRPG\rpggame\src\main\java\rpg\world\shadowveil\Terrain.java'
s = open(P, encoding='utf-8').read()
a = '''    // ------------------------------------------------------------------ 2. settlements and named grounds'''
b = '''    /** (0.4.6) The name of the region a point lies deepest in (its data's "name"), drawn through the same warp as the ground; null off the map. */
    public String regionName(int x, int z) {
        if (regions == null || x < 0 || z < 0 || x >= S || z >= S) return null;
        double wx = x + 0.5 + warpOf(x, z, 0), wz = z + 0.5 + warpOf(x, z, 1), best = -1e9;
        int a = -1;
        for (int k = 0; k < regions.size(); k++) { double d = inside(regionCells[k], wx, wz); if (d > best) { best = d; a = k; } }
        return a < 0 ? null : Atlas.s(regions.get(a), "name", Atlas.s(regions.get(a), "id", null));
    }

    // ------------------------------------------------------------------ 2. settlements and named grounds'''
if 'public String regionName' not in s:
    assert a in s; s = s.replace(a, b)
    open(P, 'w', encoding='utf-8').write(s)

# 3) fragment
P = r'C:\Users\John\Documents\ProjectRPG\rpggame\src\main\java\rpg\ui\HudFragments.java'
s = open(P, encoding='utf-8').read()
a = '''        hud.add(new BountyNotice(hud)).wake("bountynotice");'''
b = '''        hud.add(new BountyNotice(hud)).wake("bountynotice");
        hud.add(new RegionBanner(hud));'''
assert a in s; s = s.replace(a, b)
a = '''    private static float ease(float t) { t = Math.max(0, Math.min(1, t)); return t * t * (3 - 2 * t); }
'''
b = a + open(r'C:\Users\John\Documents\ProjectRPG\work\gen46\region_banner.java', encoding='utf-8').read()
assert a in s; s = s.replace(a, b, 1)
open(P, 'w', encoding='utf-8').write(s)

P = r'C:\Users\John\Documents\ProjectRPG\rpggame\src\main\java\rpg\ui\Hud.java'
s = open(P, encoding='utf-8').read()
a = '''    static final String[] TOP_STACK = {"sentence", "target", "levelbanner", "levelup", "honor", "bountynotice"};'''
b = '''    static final String[] TOP_STACK = {"regionbanner", "sentence", "target", "levelbanner", "levelup", "honor", "bountynotice"};'''
assert a in s; s = s.replace(a, b)
a = '''"levelbanner", Placement.at(Anchor.TOP, 0, 96), "bountynotice", Placement.at(Anchor.TOP, 0, 150),'''
b = '''"levelbanner", Placement.at(Anchor.TOP, 0, 96), "bountynotice", Placement.at(Anchor.TOP, 0, 150), "regionbanner", Placement.at(Anchor.TOP, 0, 16),'''
assert a in s; s = s.replace(a, b)
open(P, 'w', encoding='utf-8').write(s)
print('ok')
