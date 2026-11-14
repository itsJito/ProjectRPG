import os
os.chdir(r'C:\Users\John\Documents\ProjectRPG\rpggame\src\main\java\rpg')
def patch(path, pairs):
    s = open(path, encoding='utf-8').read()
    for old, new in pairs:
        assert old in s, (path, old[:90])
        s = s.replace(old, new, 1)
    open(path, 'w', encoding='utf-8').write(s)

patch('world/Voxels.java', [
 ('''    public Hit ray(float ox, float oy, float oz, float dx, float dy, float dz, float max, boolean stopAtWater, boolean leaves) {
        Hit h = new Hit();''',
  '''    public Hit ray(float ox, float oy, float oz, float dx, float dy, float dz, float max, boolean stopAtWater, boolean leaves) { return ray(ox, oy, oz, dx, dy, dz, max, stopAtWater, leaves, false); }

    /**
     * (0.4.5, gamefixes: none of the furniture could be broken) The cells a piece of furniture stands on (its invisible
     * collision): a blade, an arrow or a spell passes into them to meet the piece's own parts (rpg.objects.Wreckage) -
     * the cell's face is not the piece. Null: none.
     */
    public java.util.function.LongPredicate furnitureCells;
    public static long cellKey(int x, int y, int z) { return ((long) x & 0xfffff) << 40 | ((long) y & 0xfffff) << 20 | (long) z & 0xfffff; }

    /** The same, passing through furniture's own cells when asked (a blow or a shot: the piece's parts take it, not its cell). */
    public Hit ray(float ox, float oy, float oz, float dx, float dy, float dz, float max, boolean stopAtWater, boolean leaves, boolean throughFurniture) {
        Hit h = new Hit();'''),
 ('''            int b = v.block(x, y, z);
            if (b != 0 && (!door(b) && solid[b] || stopAtWater && b == Blocks.WATER)) {
                h.hit = true;''',
  '''            int b = v.block(x, y, z);
            if (b != 0 && (!door(b) && solid[b] || stopAtWater && b == Blocks.WATER) && !(throughFurniture && furnitureCells != null && furnitureCells.test(cellKey(x, y, z)))) {
                h.hit = true;'''),
 ('''    public boolean clear(float ax, float ay, float az, float bx, float by, float bz, boolean leaves) {
        float dx = bx - ax, dy = by - ay, dz = bz - az, d = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (d < 1e-4f) return !solid((int) Math.floor(ax), (int) Math.floor(ay), (int) Math.floor(az));
        Hit h = ray(ax, ay, az, dx, dy, dz, d, false, leaves);
        return !h.hit || h.dist >= d;
    }''',
  '''    public boolean clear(float ax, float ay, float az, float bx, float by, float bz, boolean leaves) { return clear(ax, ay, az, bx, by, bz, leaves, false); }
    /** The same, the furniture's own cells passed through when asked (a blade's length reaching into a table). */
    public boolean clear(float ax, float ay, float az, float bx, float by, float bz, boolean leaves, boolean throughFurniture) {
        float dx = bx - ax, dy = by - ay, dz = bz - az, d = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (d < 1e-4f) { int x = (int) Math.floor(ax), y = (int) Math.floor(ay), z = (int) Math.floor(az); return !solid(x, y, z) || throughFurniture && furnitureCells != null && furnitureCells.test(cellKey(x, y, z)); }
        Hit h = ray(ax, ay, az, dx, dy, dz, d, false, leaves, throughFurniture);
        return !h.hit || h.dist >= d;
    }'''),
])

patch('objects/Wreckage.java', [
 ('''    public Wreckage(Rpg g) { this.g = g; }''',
  '''    public Wreckage(Rpg g) { this.g = g; g.vox.furnitureCells = this::occupied; }

    /** (0.4.5) The cells the standing, breakable furniture occupies (its collision), kept as pieces come and go. */
    private final java.util.HashSet<Long> cells = new java.util.HashSet<>();
    private int cellsFor = -1, cellsWrecked = -1;
    /** Whether a cell is a breakable piece of furniture's own (a blow passes into it to meet the piece). */
    public boolean occupied(long key) {
        if (cellsFor != g.objects.furniture.size() || cellsWrecked != wrecked) {
            cells.clear();
            for (Furniture f : g.objects.furniture) {
                if (!f.exists() || !breakable(f.kind) || f.kind.block == rpg.world.Blocks.AIR) continue;
                State st = states.get(f.id);
                if (st != null && st.wrecked) continue;
                for (int x = f.x0; x <= f.x1; x++) for (int z = f.z0; z <= f.z1; z++) for (int y = 0; y < Math.max(1, f.h); y++) cells.add(rpg.world.Voxels.cellKey(x, f.floor + y, z));
            }
            cellsFor = g.objects.furniture.size(); cellsWrecked = wrecked;
        }
        return cells.contains(key);
    }'''),
])

patch('combat/CombatSystem.java', [
 ('''            Voxels.Hit wall = g.vox.ray(grip[0], grip[1], grip[2], dx, dy, dz, length, false);
            if (wall.hit && wall.door != null && wall.door.open && !wall.door.locked) {
                wall.door.knock(g, wall.x, wall.z, dx, dz);
                wall = g.vox.ray(grip[0], grip[1], grip[2], dx, dy, dz, length, false, false);
            }
            boolean hard = wall.hit && !Blocks.glass(wall.block) && !Blocks.isLeaves(wall.block);
            float limit = hard ? wall.dist : length;
            if (!g.vox.clear(tip[3], tip[4], tip[5], grip[0], grip[1], grip[2], false)) limit = 0;''',
  '''            // (0.4.5, gamefixes: none of the furniture was destructible) a blade passes into a piece of furniture's own
            // cells to meet the piece's parts (Wreckage) - the invisible cell it stands on stopped every blow at its face
            Voxels.Hit wall = g.vox.ray(grip[0], grip[1], grip[2], dx, dy, dz, length, false, true, true);
            if (wall.hit && wall.door != null && wall.door.open && !wall.door.locked) {
                wall.door.knock(g, wall.x, wall.z, dx, dz);
                wall = g.vox.ray(grip[0], grip[1], grip[2], dx, dy, dz, length, false, false, true);
            }
            boolean hard = wall.hit && !Blocks.glass(wall.block) && !Blocks.isLeaves(wall.block);
            float limit = hard ? wall.dist : length;
            if (!g.vox.clear(tip[3], tip[4], tip[5], grip[0], grip[1], grip[2], false, true)) limit = 0;'''),
 ('''        Voxels.Hit h = g.vox.ray(p.x, p.y, p.z, sx, sy, sz, len, elemental);
        float wall = h.hit ? h.dist : len;''',
  '''        Voxels.Hit h = g.vox.ray(p.x, p.y, p.z, sx, sy, sz, len, elemental, true, true);
        float wall = h.hit ? h.dist : len;'''),
])
print('ok')
