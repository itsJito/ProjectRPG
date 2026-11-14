import json
def patch(p, pairs):
    s = open(p, encoding='utf-8').read()
    for a, b in pairs:
        assert s.count(a) == 1, (p, a[:100], s.count(a))
        s = s.replace(a, b)
    open(p, 'w', encoding='utf-8').write(s)
p = 'rpggame/src/main/resources/data/shadowveil.json'
d = json.load(open(p, encoding='utf-8'))
r = d['realms']
fog = {'Loxford': [0.92, 0.97, 1.06], 'Harlette': [0.86, 0.95, 0.9], 'Valgard': [0.9, 0.95, 1.08], 'Gorrick': [0.9, 0.96, 1.1],
       'Azharad': [1.12, 1.0, 0.8], 'Camelot': [1.05, 1.02, 0.95], 'Severia': [1.06, 1.02, 0.94]}
for x in r['list']: x['fog'] = fog[x['name']]
r['_fog'] = ("(0.4.4, gamefixes: fog tinted by the region you are in) each realm's fog colour (a multiplier on the hour's fog), "
             "blended in over its bounds as its tint is; the wilds' by the biome under the eye (wilds.fogBiomes), a neutral one where none is named")
wb = {}
for b, c in {('meadow', 'willowmeadow', 'clearing', 'copse', 'chalk', 'stony', 'scrub', 'scrubwood', 'marches'): [1.0, 1.0, 1.0],
             ('oakwood',): [0.92, 1.0, 0.92], ('pinewood',): [0.9, 0.98, 0.95], ('marsh', 'lakeshore'): [0.88, 0.96, 0.9],
             ('desert', 'desert_town', 'dunes', 'sandstone', 'claypits', 'oasis'): [1.12, 1.0, 0.8],
             ('volcanic', 'cinders', 'scorched'): [0.86, 0.8, 0.78], ('deadwood',): [0.85, 0.85, 0.9],
             ('highland', 'crags', 'upland', 'heath'): [0.92, 0.95, 1.06],
             ('mediterranean', 'vineyard', 'cypress', 'olivegrove'): [1.05, 1.02, 0.94], ('beach', 'seabed'): [0.95, 1.0, 1.08]}.items():
    for k in b: wb[k] = c
r['wilds']['fogBiomes'] = wb
json.dump(d, open(p, 'w', encoding='utf-8'), indent=1, ensure_ascii=False)

patch('rpggame/src/main/java/rpg/world/shadowveil/ShadowVeil.java', [
("""    /**
     * (0.4.0, gamefixes: volumetric ray marching) the design's ground mists""",
 """    /**
     * (0.4.4, gamefixes: fog should have a different colour/tint depending on which region of the world you're in) the fog
     * colour at a point, as a multiplier on the hour's own: the wilds' by the biomes round it (sampled over a few dozen
     * cells, so it changes gradually as the land does), each realm's over its bounds, blended in toward its edge as the
     * realms' tints are. {r, g, b}.
     */
    public static float[] fogTint(float x, float z) {
        Atlas a = Atlas.get();
        Terrain t = terrain;
        Map<String, Object> realms = a.map("realms");
        Map<String, Object> biomeFog = Atlas.map(Atlas.map(realms, "wilds"), "fogBiomes");
        float r = 0, g = 0, b = 0; int n = 0;
        if (t != null) for (int dx = -24; dx <= 24; dx += 12) for (int dz = -24; dz <= 24; dz += 12) {
            int sx = (int) Math.max(0, Math.min(t.S - 1, x + dx)), sz = (int) Math.max(0, Math.min(t.S - 1, z + dz));
            double[] c = Atlas.pt(biomeFog.get(t.biomeAt(sx, sz)));
            if (c.length < 3) c = new double[]{1, 1, 1};
            r += c[0]; g += c[1]; b += c[2]; n++;
        }
        float[] out = n == 0 ? new float[]{1, 1, 1} : new float[]{r / n, g / n, b / n};
        float blend = (float) Atlas.d(realms, "blend", 48);
        for (Map<String, Object> rl : Atlas.list(realms, "list")) {
            double[] c = Atlas.pt(rl.get("fog"));
            if (c.length < 3) continue;
            float w = 0;
            for (Object o : Atlas.arr(rl, "rects")) {
                int[] q = a.cells((List<?>) o);
                float inX = Math.min(x - q[0], q[2] + 1 - x), inZ = Math.min(z - q[1], q[3] + 1 - z), in = Math.min(inX, inZ);
                w = Math.max(w, blend <= 0 ? (in >= 0 ? 1 : 0) : Math.max(0, Math.min(1, (in + blend * 0.5f) / blend)));
            }
            if (w <= 0) continue;
            for (int k = 0; k < 3; k++) out[k] += ((float) c[k] - out[k]) * w;
        }
        return out;
    }

    /**
     * (0.4.0, gamefixes: volumetric ray marching) the design's ground mists"""),
])
patch('rpggame/src/main/java/rpg/render/Gfx.java', [
("""        float fogR = clamp01(hr * lift) * 0.9f + 0.03f, fogG = clamp01(hg * lift) * 0.9f + 0.035f, fogB = clamp01(hb * lift) * 0.9f + 0.06f;""",
 """        float fogR = clamp01(hr * lift) * 0.9f + 0.03f, fogG = clamp01(hg * lift) * 0.9f + 0.035f, fogB = clamp01(hb * lift) * 0.9f + 0.06f;
        // (0.4.4) the region's own fog colour, eased in as the eye moves (checked twice a second; the change takes a few)
        if (rpg.world.WorldMap.shadowveil() && sceneGame != null) {
            if (time - fogTintAt > 0.5 || time < fogTintAt) { fogTintAt = time; fogTintWant = rpg.world.shadowveil.ShadowVeil.fogTint(camX, camZ); }
            float k = (float) Math.min(1, Math.max(0, time - fogTintEased) * 0.6); fogTintEased = time;
            for (int i = 0; i < 3; i++) fogTint[i] += (fogTintWant[i] - fogTint[i]) * k;
            fogR = clamp01(fogR * fogTint[0]); fogG = clamp01(fogG * fogTint[1]); fogB = clamp01(fogB * fogTint[2]);
        }"""),
("""    private float camX, camY, camZ;""",
 """    private float camX, camY, camZ;
    /** (0.4.4) The regional fog tint: as it is now, as it is going to, when it was last asked and eased. */
    private final float[] fogTint = {1, 1, 1};
    private float[] fogTintWant = {1, 1, 1};
    private double fogTintAt = -1, fogTintEased;"""),
])
print('ok')
