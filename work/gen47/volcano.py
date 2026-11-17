import json, collections
OD = collections.OrderedDict

def sub(p, a, b, cnt=1):
    s = open(p, encoding='utf-8').read()
    assert a in s, (p, a[:90])
    s = s.replace(a, b, cnt)
    open(p, 'w', encoding='utf-8').write(s)

B = 'src/main/java/rpg/world/Blocks.java'
sub(B, "HERB_PATCH = 33, FLAX_PATCH = 34, BERRY_BUSH = 35, CRATE = 36, BOOKCASE = 37, MARK_RED = 38,",
       "HERB_PATCH = 33, FLAX_PATCH = 34, BERRY_BUSH = 35, CRATE = 36, BOOKCASE = 37,\n            // (0.4.7, gamefixes: make Ashmaw more impressive) the volcano's lava: a glowing crust over the melt, a hazard\n            // routes go round and that burns whoever stands on it (id 38, once the devmap's red marker)\n            LAVA = 38,")
sub(B, "L_FENCE = 32, L_HERB = 33, L_FLAX = 34, L_BERRY = 35, L_CRATE = 36, L_BOOKS = 37, L_RED = 38, L_BLUE = 39, L_YELLOW = 40,",
       "L_FENCE = 32, L_HERB = 33, L_FLAX = 34, L_BERRY = 35, L_CRATE = 36, L_BOOKS = 37, L_RED = 38, L_LAVA = 39, L_YELLOW = 40,")
sub(B, "f(CRATE, L_CRATE); f(BOOKCASE, L_PLANKS, L_BOOKS, L_PLANKS); f(MARK_RED, L_RED);",
       "f(CRATE, L_CRATE); f(BOOKCASE, L_PLANKS, L_BOOKS, L_PLANKS); f(LAVA, L_LAVA);")
sub(B, "        r[LAMP] = def(FACES[LAMP], cube | AstrattoNative.RB_EMISSIVE);",
       "        r[LAMP] = def(FACES[LAMP], cube | AstrattoNative.RB_EMISSIVE);\n        r[LAVA] = def(FACES[LAVA], cube | AstrattoNative.RB_EMISSIVE);")
sub(B, "        d[EMBERS] = new NativeEngine.BlockDef(AstrattoNative.BLOCK_HAZARD | AstrattoNative.BLOCK_LIGHT_PASS);",
       "        d[EMBERS] = new NativeEngine.BlockDef(AstrattoNative.BLOCK_HAZARD | AstrattoNative.BLOCK_LIGHT_PASS);\n        d[LAVA] = new NativeEngine.BlockDef(AstrattoNative.BLOCK_SOLID | AstrattoNative.BLOCK_HAZARD);")

T = 'src/main/java/rpg/world/Textures.java'
sub(T, "            case L_BLUE -> banner(cv, 0x3A5EC0);\n", "            case L_LAVA -> lava(cv);\n")
sub(T, '''    /** (0.4.2) Harlette's daub:''', '''    /**
     * (0.4.7) Lava: a black-red crust broken into plates, the melt glowing orange and yellow in the cracks between them,
     * brightest where they are widest.
     */
    static void lava(Canvas cv) {
        float[] crust = rgb(0x2A1410), hot = rgb(0xFF7A1A), white = rgb(0xFFE070);
        for (int y = 0; y < T; y++) for (int x = 0; x < T; x++) {
            int i = y * T + x;
            float n = cv.noise(3, x, y), m = cv.noise(4, x + 13, y + 7);
            float crack = Math.abs(n - 0.5f) * 2;
            float[] c = crack < 0.12f ? (crack < 0.05f ? white : hot) : shade(crust, (m - 0.5f) * 0.4f + (crack < 0.25f ? 0.35f : 0));
            cv.r[i] = c[0]; cv.g[i] = c[1]; cv.b[i] = c[2];
        }
    }

    /** (0.4.2) Harlette's daub:''')

D = 'src/main/java/rpg/world/DevMap.java'
s = open(D, encoding='utf-8').read()
s = s.replace("set((int) p[0], FLOOR - 1, (int) p[1], MARK_RED);", "set((int) p[0], FLOOR - 1, (int) p[1], RED_SANDSTONE);")
s = s.replace("set(83, FLOOR + 3, z, MARK_RED); }", "set(83, FLOOR + 3, z, RED_SANDSTONE); }")
open(D, 'w', encoding='utf-8').write(s)

# the terrain: gullies down the cone, the crater's lava lake (data: the peak's 'gullies', 'gullyDepth', 'lava')
TR = 'src/main/java/rpg/world/shadowveil/Terrain.java'
sub(TR, '''            double crater = Atlas.d(p, "crater", 0), craterDepth = Atlas.d(p, "craterDepth", 0), rough = Atlas.d(p, "rough", 0.2);''',
        '''            double crater = Atlas.d(p, "crater", 0), craterDepth = Atlas.d(p, "craterDepth", 0), rough = Atlas.d(p, "rough", 0.2);
            // (0.4.7, gamefixes: Ashmaw more impressive) gullies cut down the cone between ribs of rock, and its crater's lava
            double gullies = Atlas.d(p, "gullies", 0), gullyDepth = Atlas.d(p, "gullyDepth", 0), lava = Atlas.d(p, "lava", 0);
            if (!isles && lava > 0 && crater > 0) craters.add(new double[]{cx, cz, crater * lava});''')
sub(TR, '''                if (crater > 0 && r < crater) ph -= craterDepth * (1 - (r / crater) * (r / crater));''',
        '''                if (crater > 0 && r < crater) ph -= craterDepth * (1 - (r / crater) * (r / crater));
                if (gullies > 0 && r > crater + 2) {
                    double ang = Math.atan2(z + 0.5 - cz, x + 0.5 - cx) + noise.at((x - OX) / 22.0, (z - OX) / 22.0) * 0.35;
                    double rib = Math.pow(Math.abs(Math.sin(ang * gullies / 2)), 0.5);
                    ph -= gullyDepth * (1 - rib) * Math.sin(Math.PI * Math.min(1, f));
                }''')
sub(TR, '''    public final List<int[]> lampCells = new ArrayList<>(), stainedCells = new ArrayList<>();''',
        '''    public final List<int[]> lampCells = new ArrayList<>(), stainedCells = new ArrayList<>();
    /** (0.4.7) The volcanoes' lava lakes: {x, z, radius} (cells), and where each one's melt lies {x, floorY, z} once laid. */
    public final List<double[]> craters = new ArrayList<>();
    public final List<int[]> lavaCells = new ArrayList<>();''')
sub(TR, '''            // (0.4.7, gamefixes: floating blocks - a street lamp left in the air when the souq's stalls took its post)''',
        '''            // (0.4.7, gamefixes: Ashmaw more impressive) the crater's floor a lake of lava
            t.lava(); at = t.stage("lava", at);
            // (0.4.7, gamefixes: floating blocks - a street lamp left in the air when the souq's stalls took its post)''')
sub(TR, '''    private static final boolean FLOAT_DEBUG''', '''    /** (0.4.7) Each crater's floor laid with lava out to its lake's radius, the rim's foot left as rock. */
    private void lava() {
        for (double[] c : craters) {
            int r = (int) Math.ceil(c[2]);
            for (int x = (int) c[0] - r; x <= (int) c[0] + r; x++) for (int z = (int) c[1] - r; z <= (int) c[1] + r; z++) {
                if (!in(x, z) || Math.hypot(x + 0.5 - c[0], z + 0.5 - c[1]) > c[2] * (0.9 + 0.1 * noise.at(x / 7.0, z / 7.0))) continue;
                int i = idx(x, z), y = top[i];
                if (y <= 0 || y >= H) continue;
                cells[i * H + y] = (byte) Blocks.LAVA;
                if (y - 1 > 0) cells[i * H + y - 1] = (byte) Blocks.LAVA;
                lavaCells.add(new int[]{x, y, z});
            }
        }
    }

    private static final boolean FLOAT_DEBUG''')

# the data
PS = 'src/main/resources/data/shadowveil.json'
S = json.load(open(PS, encoding='utf-8'), object_pairs_hook=OD)
for p in S['peaks']:
    if p['id'] == 'ashmaw':
        p['y'] = 97; p['radius'] = 104; p['crater'] = 16; p['craterDepth'] = 11; p['power'] = 1.55; p['rough'] = 0.2
        p['gullies'] = 13; p['gullyDepth'] = 4; p['lava'] = 0.62
        p['plume'] = OD([("height", 70), ("puffs", 9), ("radius", [7, 26]), ("density", [0.42, 0.1]), ("colour", [0.22, 0.2, 0.2]), ("glow", 0.55), ("reach", 900)])
open(PS, 'w', encoding='utf-8').write(json.dumps(S, indent=1, ensure_ascii=False) + "\n")
print("ok")
