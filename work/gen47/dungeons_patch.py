def sub(p, a, b, cnt=1):
    s = open(p, encoding='utf-8').read()
    assert a in s, (p, a[:90])
    s = s.replace(a, b, cnt)
    open(p, 'w', encoding='utf-8').write(s)

L = 'src/main/java/rpg/world/DungeonLayout.java'
sub(L, '''    /** Where one arrives (and leaves by): the exit's cell inside. */
    public int entryX, entryZ;

    private DungeonLayout(int id, String name, Type type, Theme theme, long seed, int ox, int oz) {
        this.id = id; this.name = name; this.type = type; this.theme = theme; this.seed = seed; this.ox = ox; this.oz = oz; this.w = type.w; this.d = type.d;
        open = new boolean[w][d]; height = new int[w][d];
        if (type.procedural) generate(); else authorBoss();
    }''', '''    /** Where one arrives (and leaves by): the exit's cell inside. */
    public int entryX, entryZ;
    /** (0.4.7) The level its floor stands at (the cell feet stand in): the devmap's floor, or a ShadowVeil level's depth. */
    public final int floorY;
    /** (0.4.7) On ShadowVeil: the site it lies under, and which of its levels it is (1 the first down). */
    public String site = "";
    public int depth = 1;

    private DungeonLayout(int id, String name, Type type, Theme theme, long seed, int ox, int oz) { this(id, name, type, theme, seed, ox, oz, DevMap.FLOOR); }
    private DungeonLayout(int id, String name, Type type, Theme theme, long seed, int ox, int oz, int floorY) {
        this.id = id; this.name = name; this.type = type; this.theme = theme; this.seed = seed; this.ox = ox; this.oz = oz; this.w = type.w; this.d = type.d; this.floorY = floorY;
        open = new boolean[w][d]; height = new int[w][d];
        if (type.procedural) generate(); else authorBoss();
    }
    /**
     * (0.4.7, gamefixes: the dungeon system on ShadowVeil) one level of a dungeon under the map: generated from its seed as
     * any dungeon is, its region's corner (world cells) and the floor it is cut at.
     */
    public static DungeonLayout level(int id, String name, Type type, Theme theme, long seed, int ox, int oz, int floorY, String site, int depth) {
        DungeonLayout l = new DungeonLayout(id, name, type, theme, seed, ox, oz, floorY);
        l.site = site; l.depth = depth;
        return l;
    }
    /** (0.4.7) ShadowVeil's dungeon levels, once planned (rpg.dungeon.Underground). */
    private static List<DungeonLayout> underground = List.of();
    public static void useUnderground(List<DungeonLayout> levels) { underground = levels; }
    public static List<DungeonLayout> underground() { return underground; }
    /** (0.4.7) The level of a ShadowVeil dungeon a point is in - inside its region and between its floor and its rock overhead - or null. */
    public static DungeonLayout underAt(float x, float y, float z) {
        if (!WorldMap.shadowveil()) return null;
        for (DungeonLayout l : underground) if (x >= l.ox && x < l.ox + l.w && z >= l.oz && z < l.oz + l.d && y >= l.floorY - 1.5f && y < l.floorY + 9) return l;
        return null;
    }''')

W = 'src/main/java/rpg/world/WorldMap.java'
sub(W, '''    public static float settleY(float x, float y, float z) {
        if (devmap()) return y;''', '''    public static float settleY(float x, float y, float z) {
        if (devmap()) return y;
        // (0.4.7) a body in a dungeon's level under the map stays there (it is not buried: the rooms are cut out of the rock)
        if (DungeonLayout.underAt(x, y, z) != null) return y;''')

M = 'src/main/java/rpg/monster/Monsters.java'
sub(M, '''    public Monster spawn(MonsterKind kind, int level, float x, float z, Lair lair) {''', '''    public Monster spawn(MonsterKind kind, int level, float x, float z, Lair lair) { return spawnAt(kind, level, x, Float.NaN, z, lair); }
    /** (0.4.7) One made at a height (a dungeon's level under the map); NaN: on the ground. */
    public Monster spawnAt(MonsterKind kind, int level, float x, float y, float z, Lair lair) {''')
sub(M, '''        Monster m = new Monster(g.state.newEntityId(), kind, level, x, z);
        if (lair != null) { m.lair = lair.id; m.lairX = lair.x; m.lairZ = lair.z; }''', '''        Monster m = new Monster(g.state.newEntityId(), kind, level, x, z);
        if (!Float.isNaN(y)) m.y = y;
        if (lair != null) { m.lair = lair.id; m.lairX = lair.x; m.lairZ = lair.z; }''')
sub(M, '''        if (lair != null && rpg.world.WorldMap.shadowveil() && Math.hypot(x - lair.x, z - lair.z) > 24) {''', '''        if (lair != null && rpg.world.WorldMap.shadowveil() && Float.isNaN(y) && Math.hypot(x - lair.x, z - lair.z) > 24) {''')
sub(M, '''            m.y = Math.max(rpg.world.Ground.at(m.x, m.z), (float) Math.floor(m.y + 0.5f));''', '''            m.y = rpg.world.WorldMap.settleY(m.x, Math.max(rpg.world.DungeonLayout.underAt(m.x, m.y, m.z) != null ? m.y : rpg.world.Ground.at(m.x, m.z), (float) Math.floor(m.y + 0.5f)), m.z);''')
print("ok")
