import json
def patch(p, pairs):
    s = open(p, encoding='utf-8').read()
    for a, b in pairs:
        assert s.count(a) == 1, (p, a[:100], s.count(a))
        s = s.replace(a, b)
    open(p, 'w', encoding='utf-8').write(s)
p = 'rpggame/src/main/resources/data/shadowveil.json'
d = json.load(open(p, encoding='utf-8'))
d['monsters'] = {
    "_about": "(0.4.4, gamefixes: monsters with natural spawn locations and territories in the wilderness - orcs, goblins, the overworld's kinds, not the caves' zombies, skeletons or dragons) A territory grows on the open wilds on a grid (jittered), where its biome holds the kind by its chance, far from every kingdom and homestead ('clearance', cells) and off the roads: its lair (a camp, a nest, a haunt) and the ground its members roam, hunt and return to. Per biome: [kind, lowest level, highest level, how many it holds, chance in a hundred]. Its population is the monster system's own - it breeds when fed and safe, and is not respawned on a timer.",
    "grid": 88, "most": 40, "clearance": {"town": 170, "road": 10},
    "names": {"GOBLIN": "Goblin camp", "ORC": "Orc warband's camp", "TROLL": "Troll's haunt", "IMP": "Imp nest", "WEREWOLF": "Werewolf's range", "GIANT_SPIDER": "Giant spider's web", "GHOST": "Restless ground"},
    "biomes": {
        "oakwood": [["GOBLIN", 1, 9, 6, 30], ["WEREWOLF", 18, 28, 1, 8]],
        "pinewood": [["GOBLIN", 1, 9, 6, 25], ["WEREWOLF", 18, 30, 1, 10]],
        "copse": [["GOBLIN", 1, 7, 5, 20]],
        "deadwood": [["WEREWOLF", 20, 32, 2, 35], ["GIANT_SPIDER", 6, 14, 2, 25], ["GHOST", 15, 24, 2, 20]],
        "heath": [["IMP", 1, 8, 3, 30], ["GOBLIN", 2, 10, 5, 20]],
        "highland": [["ORC", 20, 32, 5, 30], ["TROLL", 55, 64, 1, 10]],
        "crags": [["ORC", 20, 34, 5, 35], ["TROLL", 58, 66, 1, 15]],
        "upland": [["ORC", 18, 30, 4, 25]],
        "stony": [["ORC", 18, 28, 4, 15]],
        "marsh": [["GIANT_SPIDER", 4, 12, 2, 25], ["IMP", 1, 6, 3, 15]],
        "marches": [["GOBLIN", 2, 10, 5, 25], ["ORC", 18, 28, 4, 15]],
        "scrub": [["GOBLIN", 1, 8, 5, 20]], "scrubwood": [["GOBLIN", 1, 8, 5, 25]],
        "volcanic": [["IMP", 4, 12, 4, 40]], "cinders": [["IMP", 4, 12, 4, 35]], "scorched": [["IMP", 4, 12, 4, 30]],
        "desert": [["GIANT_SPIDER", 4, 12, 2, 15]], "dunes": [["GIANT_SPIDER", 4, 12, 2, 12]],
        "meadow": [["GOBLIN", 1, 6, 4, 8]], "chalk": [["GOBLIN", 1, 6, 4, 8]]
    }
}
json.dump(d, open(p, 'w', encoding='utf-8'), indent=1, ensure_ascii=False)

M = 'rpggame/src/main/java/rpg/monster/Monsters.java'
patch(M, [
("""        Lair spiders = addLair(MonsterKind.GIANT_SPIDER, "Spider's web", web[0], web[1], 1, 12, 2);
        spawn(MonsterKind.GIANT_SPIDER, 6, web[0], web[1], spiders);
    }""",
 """        Lair spiders = addLair(MonsterKind.GIANT_SPIDER, "Spider's web", web[0], web[1], 1, 12, 2);
        spawn(MonsterKind.GIANT_SPIDER, 6, web[0], web[1], spiders);
        if (rpg.world.WorldMap.shadowveil()) territories();
    }

    /**
     * (0.4.4, gamefixes: monsters with natural spawn locations and territories in the wilderness) ShadowVeil's wild ground
     * given its overworld monsters from the data ({@code shadowveil.json} "monsters"): on a jittered grid over the wilds,
     * each point's biome offers its kinds by their chances; a territory takes root only far from every kingdom, homestead
     * and road, its lair set on dry open ground, half its number living there to begin with - the rest it breeds.
     */
    private void territories() {
        var a = rpg.world.shadowveil.Atlas.get();
        var data = a.map("monsters");
        var t = rpg.world.shadowveil.ShadowVeil.terrain();
        if (data.isEmpty() || t == null) return;
        int grid = rpg.world.shadowveil.Atlas.i(data, "grid", 88), most = rpg.world.shadowveil.Atlas.i(data, "most", 40);
        var clear = rpg.world.shadowveil.Atlas.map(data, "clearance"); var names = rpg.world.shadowveil.Atlas.map(data, "names"); var biomes = rpg.world.shadowveil.Atlas.map(data, "biomes");
        float town = (float) rpg.world.shadowveil.Atlas.d(clear, "town", 170), road = (float) rpg.world.shadowveil.Atlas.d(clear, "road", 10);
        java.util.List<float[]> settled = new ArrayList<>();
        if (t.arch != null) for (var st : t.arch.structures) if (!DevMap.WILDS.equals(rpg.world.shadowveil.ShadowVeil.jurisdiction(st.cx(), st.cz())) || st.template.equals("building") || st.template.equals("hall")) settled.add(new float[]{st.cx(), st.cz()});
        for (var as : rpg.world.shadowveil.Census.all().values()) if (as.homeStage() != null) settled.add(as.homeStage());
        int made = 0;
        for (int x = grid / 2; x < t.S - 8 && made < most; x += grid) for (int z = grid / 2; z < t.S - 8 && made < most; z += grid) {
            float px = x + (float) ((rpg.world.shadowveil.Noise.hash(a.seed, x, z, 151) - 0.5) * grid * 0.6), pz = z + (float) ((rpg.world.shadowveil.Noise.hash(a.seed, x, z, 152) - 0.5) * grid * 0.6);
            float[] p = rpg.world.shadowveil.ShadowVeil.dry(px, pz);
            int i = t.idx((int) p[0], (int) p[1]);
            if (t.water[i] >= 0 || (t.flags[i] & rpg.world.shadowveil.Terrain.ROAD) != 0) continue;
            if (!DevMap.WILDS.equals(rpg.world.shadowveil.ShadowVeil.jurisdiction(p[0], p[1]))) continue;
            float near = Float.MAX_VALUE;
            for (float[] s : settled) near = Math.min(near, (float) Math.hypot(s[0] - p[0], s[1] - p[1]));
            if (near < town || nearRoad(t, (int) p[0], (int) p[1], (int) road)) continue;
            int k = 0;
            for (Object o : rpg.world.shadowveil.Atlas.arr(biomes, t.biomeAt((int) p[0], (int) p[1]))) {
                java.util.List<?> gr = (java.util.List<?>) o; k++;
                MonsterKind kind;
                try { kind = MonsterKind.valueOf(String.valueOf(gr.get(0))); } catch (IllegalArgumentException e) { continue; }
                if (rpg.world.shadowveil.Noise.hash(a.seed, x + k, z, 153 + kind.ordinal()) >= rpg.world.shadowveil.Atlas.dn(gr.get(4)) / 100.0) continue;
                int lo = rpg.world.shadowveil.Atlas.n(gr.get(1)), hi = rpg.world.shadowveil.Atlas.n(gr.get(2)), cap = rpg.world.shadowveil.Atlas.n(gr.get(3));
                Lair l = new Lair(g.state.newEntityId(), kind, rpg.world.shadowveil.Atlas.s(names, kind.name(), kind.title + "s' ground"), p[0], p[1], lo, hi, cap);
                lairs.add(l);
                int start = Math.max(1, (cap + 1) / 2);
                for (int n = 0; n < start; n++) {
                    double ang = n * 2.4 + x, r = 1.5 + n * 0.9;
                    float[] q = rpg.world.shadowveil.ShadowVeil.dry(p[0] + (float) Math.cos(ang) * (float) r, p[1] + (float) Math.sin(ang) * (float) r);
                    spawn(kind, lo + (int) ((hi - lo) * rpg.world.shadowveil.Noise.hash(a.seed, x + n, z, 154)), q[0], q[1], l);
                }
                made++;
                break;   // one territory to a point
            }
        }
        System.out.println("[monsters] " + made + " wilderness territories");
    }
    private static boolean nearRoad(rpg.world.shadowveil.Terrain t, int x, int z, int r) {
        for (int dx = -r; dx <= r; dx += 2) for (int dz = -r; dz <= r; dz += 2) { int xx = x + dx, zz = z + dz; if (t.in(xx, zz) && (t.flags[t.idx(xx, zz)] & rpg.world.shadowveil.Terrain.ROAD) != 0) return true; }
        return false;
    }"""),
("""        for (Monster m : new ArrayList<>(monsters)) {
            if (!m.alive || !m.loaded || m.body == 0) continue;
            if (m.reacting) continue;""",
 """        for (Monster m : new ArrayList<>(monsters)) {
            if (!m.alive || !m.loaded || m.body == 0) continue;
            if (m.reacting) continue;
            // (0.4.4, optimisation: the wilds' territories) a monster far beyond the player's reach rests where it is - no
            // senses, no mind, its body still - until the player comes within its reach again (a fight in progress goes on)
            if (rpg.world.WorldMap.shadowveil() && m.fightTarget == 0 && Math.abs(m.x - g.player.x) + Math.abs(m.z - g.player.z) > FAR_REST) {
                g.game.commands().moverInput(m.body, 0, 0, 0, 0, 30, MOVER_STANCE_ONLY);
                continue;
            }"""),
("""    public void update(float dt) {""",
 """    /** (0.4.4) Beyond this (cells, along x plus z) from the player a monster rests unsimulated. */
    static final float FAR_REST = rpg.Tuning.f("monsters.farRest", 220f);

    public void update(float dt) {"""),
])
print('ok')
