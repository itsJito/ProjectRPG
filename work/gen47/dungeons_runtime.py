def sub(p, a, b, cnt=1):
    s = open(p, encoding='utf-8').read()
    assert a in s, (p, a[:90])
    s = s.replace(a, b, cnt)
    open(p, 'w', encoding='utf-8').write(s)

I = 'src/main/java/rpg/Ids.java'
sub(I, '''    public static final long SV_INTERIORS = ESTATE + 1_100_000;''', '''    public static final long SV_INTERIORS = ESTATE + 1_100_000;
    /** (0.4.7) ShadowVeil's dungeons under the map: their chests and traps, level by level. */
    public static final long SV_DUNGEONS = ESTATE + 1_900_000;''')

T = 'src/main/java/rpg/world/shadowveil/Terrain.java'
sub(T, '''    public final List<int[]> lavaCells = new ArrayList<>();''', '''    public final List<int[]> lavaCells = new ArrayList<>();
    /** (0.4.7) The dungeons' levels under the map, as planned and carved (rpg.dungeon.Underground). */
    public List<rpg.world.DungeonLayout> dungeonLevels = List.of();''')
sub(T, '''            t.lava(); at = t.stage("lava", at);''', '''            t.lava(); at = t.stage("lava", at);
            // (0.4.7, gamefixes: the dungeon system on ShadowVeil) the dungeons' levels cut under the map, and their stairs
            if (t.arch != null) { rpg.dungeon.DungeonSites.bind(t.arch); t.dungeonLevels = rpg.dungeon.Underground.plan(t); rpg.dungeon.Underground.carve(t, t.dungeonLevels); at = t.stage("dungeons", at); }''')

S = 'src/main/java/rpg/world/shadowveil/ShadowVeil.java'
sub(S, '''        terrain = t;
        realms(a);''', '''        terrain = t;
        realms(a);
        rpg.world.DungeonLayout.useUnderground(t.dungeonLevels);''')

D = 'src/main/java/rpg/dungeon/Dungeons.java'
sub(D, '''    public void populate() {
        for (DungeonLayout l : DungeonLayout.all()) {''', '''    /**
     * (0.4.7, gamefixes: the dungeon system on ShadowVeil) its dungeons' levels under the map, as Underground carved them:
     * each level's chests (the hoard deeper and richer the further down) and traps in fixed ids, then its residents.
     */
    public void initShadowveil() {
        long id = rpg.Ids.SV_DUNGEONS;
        for (DungeonLayout l : DungeonLayout.underground()) {
            java.util.Random r = new java.util.Random(l.seed ^ 0x7EA5);
            for (Spot s : l.spots) {
                float x = l.worldX(s.x()) + 0.5f, z = l.worldZ(s.z()) + 0.5f;
                switch (s.what()) {
                    case "trap" -> traps.add(g.objects.add(new Trap(id++, s.arg(), l.type.threat, level(l, 2), x, z)));
                    case "chest" -> {
                        Container chest = g.objects.add(new Container(id++, "chest", "Old chest", "chest", x, l.floorY + 0.4f, z, 20, 0));
                        for (ItemStack st : hoard(l, s.arg() + l.depth - 1, r)) if (chest.contents.add(st)) g.state.mint(st.def, st.amount);
                    }
                    default -> { }
                }
            }
        }
        populate();
    }
    /** The dungeons in play: the devmap's own, or ShadowVeil's levels under the map. */
    private static List<DungeonLayout> levels() { return rpg.world.WorldMap.shadowveil() ? DungeonLayout.underground() : DungeonLayout.all(); }

    public void populate() {
        for (DungeonLayout l : levels()) {''')
sub(D, '''                    MonsterKind kind = resident(l, s, master);''', '''                    MonsterKind kind = siteResident(l, s, master);''')
sub(D, '''                    var m = g.monsters.spawn(kind, lv, x, z, lair);''', '''                    // (0.4.7) under ShadowVeil each level down is harder, and its residents are made on its own floor
                    if (rpg.world.WorldMap.shadowveil()) lv = Math.min(kind.maxLevel, lv + (l.depth - 1) * DEPTH_LEVELS);
                    var m = rpg.world.WorldMap.shadowveil() ? g.monsters.spawnAt(kind, lv, x, l.floorY, z, lair) : g.monsters.spawn(kind, lv, x, z, lair);''')
sub(D, '''    /** Who lives where: by the dungeon's theme, the deeper rooms holding the stronger kinds. */''', '''    /** (0.4.7) How many levels harder each level down a ShadowVeil dungeon's residents are. */
    static final int DEPTH_LEVELS = rpg.Tuning.i("dungeons.levelsPerDepth", 6);
    /** (0.4.7) A ShadowVeil site's own creatures where its data names them (its lair's kind, the master the strongest), else the theme's. */
    private static MonsterKind siteResident(DungeonLayout l, Spot s, boolean master) {
        DungeonSites.Site site = l.site.isEmpty() ? null : DungeonSites.byId(l.site);
        if (site == null || site.creatures().isEmpty()) return resident(l, s, master);
        List<String> c = site.creatures();
        String pick = master ? c.get(c.size() - 1) : c.get(Math.floorMod(s.x() * 31 + s.z() * 17, c.size()));
        try { return MonsterKind.valueOf(pick); } catch (IllegalArgumentException e) { return resident(l, s, master); }
    }
    /** Who lives where: by the dungeon's theme, the deeper rooms holding the stronger kinds. */''')

R = 'src/main/java/rpg/Rpg.java'
sub(R, '''        if (map.kind == rpg.world.WorldMap.Kind.DEVMAP) dungeons.init();     // (0.4.0) ShadowVeil's dungeons come with its buildings''', '''        if (map.kind == rpg.world.WorldMap.Kind.DEVMAP) dungeons.init();
        // (0.4.7) ShadowVeil's dungeons: the levels cut under its sites, their chests, traps and residents
        else dungeons.initShadowveil();''')
print("ok")
