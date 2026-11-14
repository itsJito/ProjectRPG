import os
os.chdir(r'C:\Users\John\Documents\ProjectRPG\rpggame\src\main')
p='java/rpg/world/Shrines.java'
s=open(p,encoding='utf-8').read()
old_ctor='''        Shrine(long id, String name, int x0, int z0, String graveyard, float wakeX, float wakeZ) {
            super(id, "shrine", name, x0 + 1f, DevMap.FLOOR + 1.0f, z0 + 1f);'''
new_ctor='''        /** The ground the tower stands on (its footprint's floor cell). */
        public final int floor;
        Shrine(long id, String name, int x0, int floor, int z0, String graveyard, float wakeX, float wakeZ) {
            super(id, "shrine", name, x0 + 1f, floor + 1.0f, z0 + 1f);
            this.floor = floor;'''
assert old_ctor in s; s=s.replace(old_ctor,new_ctor)
i=s.index('    // ------------------------------------------------------------------ where they stand')
j=s.index('    // ------------------------------------------------------------------ resting')
s=s[:i]+'''    // ------------------------------------------------------------------ where they stand
    /**
     * (0.4.5, gamefixes: the shrine at Loxford's graveyard stood at the devmap's floor height, underground - nothing to
     * rest at, nothing to bump into) one shrine by every graveyard of the map, on the nearest clear, level ground to the
     * graves' east end at that ground's own height, its footprint solid stone two blocks high.
     */
    public void init() {
        int k = 0;
        boolean dev = g.map.kind == WorldMap.Kind.DEVMAP;
        for (var plot : g.graveyards.plots) {
            if (k >= rpg.Ids.SHRINE_COUNT) break;
            int[] at = clear(Math.round(plot.x() + plot.columns() * 1.4f + 1.5f), Math.round(plot.z() - 2), (int) Math.floor(plot.y() + 0.01f));
            if (at == null) continue;
            float[] wake = dev ? DevMap.GRAVEYARD_SPAWN : new float[]{plot.x() + plot.columns() * 0.7f, plot.z() + plot.rows() * 0.75f};
            Shrine s = g.objects.add(new Shrine(rpg.Ids.SHRINE_0 + k++, "Shrine of Light", at[0], at[1], at[2], plot.id(), wake[0], wake[1]));
            all.add(s);
            for (int x = at[0]; x < at[0] + 2; x++) for (int z = at[2]; z < at[2] + 2; z++) for (int y = at[1]; y < at[1] + 2; y++)
                if (g.vox.block(x, y, z) == Blocks.AIR) g.setBlock(x, y, z, Blocks.PROP_STONE);
        }
    }

    /** The nearest 2 x 2 of open, level ground (with a cell of room round it) to a spot, near a floor height: {x0, floor, z0}. */
    private int[] clear(int cx, int cz, int near) {
        for (int r = 0; r < 14; r++) for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
            if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
            int x0 = cx + dx, z0 = cz + dz;
            for (int floor = near - 2; floor <= near + 2; floor++) {
                boolean ok = true;
                for (int x = x0 - 1; x <= x0 + 2 && ok; x++) for (int z = z0 - 1; z <= z0 + 2 && ok; z++)
                    ok = g.vox.solid(x, floor - 1, z) && g.vox.block(x, floor - 1, z) != Blocks.WATER
                            && g.vox.block(x, floor, z) == Blocks.AIR && g.vox.block(x, floor + 1, z) == Blocks.AIR && g.vox.block(x, floor + 2, z) == Blocks.AIR;
                if (ok) return new int[]{x0, floor, z0};
            }
        }
        return null;
    }

'''+s[j:]
s=s.replace('''        if (g.fx != null) g.fx.ring(s.x, DevMap.FLOOR, s.z, 2.6f, 0.4f, 0.7f, 1f);''','''        if (g.fx != null) g.fx.ring(s.x, s.floor, s.z, 2.6f, 0.4f, 0.7f, 1f);''')
old='''    public float[] wakeSpot() {
        for (Shrine s : all) if (s.id == bound) return new float[]{s.wakeX, s.wakeZ};
        return new float[]{DevMap.GRAVEYARD_SPAWN[0], DevMap.GRAVEYARD_SPAWN[1]};
    }'''
new='''    public float[] wakeSpot() { return wakeSpot(g.player.x, g.player.z); }

    /** Where one who died at (x, z) wakes: by the shrine they are bound to, else by the nearest shrine's graveyard (GDD 3.12). */
    public float[] wakeSpot(float x, float z) {
        for (Shrine s : all) if (s.id == bound) return new float[]{s.wakeX, s.wakeZ};
        Shrine best = null;
        for (Shrine s : all) if (best == null || Math.hypot(s.x - x, s.z - z) < Math.hypot(best.x - x, best.z - z)) best = s;
        return best != null ? new float[]{best.wakeX, best.wakeZ} : new float[]{DevMap.GRAVEYARD_SPAWN[0], DevMap.GRAVEYARD_SPAWN[1]};
    }'''
assert old in s; s=s.replace(old,new)
open(p,'w',encoding='utf-8').write(s)
p='java/rpg/Ids.java'
s=open(p,encoding='utf-8').read()
s=s.replace('''    public static final long SHRINE_0 = 990;''','''    public static final long SHRINE_0 = 990;
    /** (0.4.5) How many shrines the band holds (one by each graveyard): SHRINE_0 .. SHRINE_0 + SHRINE_COUNT - 1. */
    public static final int SHRINE_COUNT = 10;''',1)
open(p,'w',encoding='utf-8').write(s)
print('ok')
