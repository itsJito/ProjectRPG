p = 'C:/Users/John/Documents/ProjectRPG/rpggame/src/main/java/rpg/world/shadowveil/Architecture.java'
s = open(p, encoding='utf-8').read()
old = '''    public record Prop(String kind, int x, int z, int floor, int facing) {}'''
new = '''    public record Prop(String kind, int x, int z, int floor, int facing) {}

    /** (0.4.3) What a way is cleared of: loose logs, earth, rock, hay, crates and low leaves - never a building, a door, a node. */
    private static final java.util.Set<Integer> LITTER = java.util.Set.of(Blocks.LOG, Blocks.OAK_LOG, Blocks.WILLOW_LOG, Blocks.SPRUCE_LOG, Blocks.JUNGLE_LOG,
            Blocks.BIRCH_LOG, Blocks.DIRT, Blocks.GRASS, Blocks.STONE, Blocks.GRAVEL, Blocks.COBBLE, Blocks.MUD, Blocks.SAND, Blocks.GRANITE, Blocks.HAY_BALE, Blocks.CRATE);
    /** (0.4.3) Cells cleared by the last {@link #clearWays()} and props taken off the ways (the survey reports them). */
    public int waysCleared, propsMoved;

    /**
     * (0.4.3, gamefixes: some alleyways were blocked by random logs or dirt blocks) Every road, lane and square outside a
     * building's lot is walked clear, after everything else has been raised and grown: whatever litter stands within two
     * cells over its surface - a woodpile's log, a heap of earth, a stray rock or hay bale, a branch hanging at head
     * height - is taken away, and no prop is left standing on a way. A living tree's trunk is left (its node stands).
     */
    void clearWays() {
        Kit.Canvas c = new Kit.Canvas(t);
        java.util.Set<Long> trunks = new java.util.HashSet<>();
        for (int[] tr : t.trees) trunks.add((long) tr[0] << 32 | (tr[1] & 0xffffffffL));
        int cleared = 0;
        for (int x = 1; x < t.S - 1; x++) for (int z = 1; z < t.S - 1; z++) {
            int i = t.idx(x, z);
            if ((t.flags[i] & (Terrain.ROAD | Terrain.LANE | Terrain.SQUARE)) == 0 || (t.flags[i] & Terrain.BRIDGE) != 0 || t.water[i] >= 0) continue;
            if (inLot(x, z, 0) || trunks.contains((long) x << 32 | (z & 0xffffffffL))) continue;
            int top = t.top[i];
            for (int y = top + 1; y <= top + 2; y++) {
                int b = c.get(x, y, z);
                if (LITTER.contains(b) || Blocks.isLeaves(b)) { c.set(x, y, z, Blocks.AIR); cleared++; }
            }
        }
        waysCleared = cleared;
        int before = props.size();
        props.removeIf(pr -> { int i = t.idx(pr.x(), pr.z()); return (t.flags[i] & (Terrain.ROAD | Terrain.LANE)) != 0 && !inLot(pr.x(), pr.z(), 0); });
        propsMoved = before - props.size();
    }'''
assert old in s
s = s.replace(old, new)
open(p, 'w', encoding='utf-8').write(s)

p = 'C:/Users/John/Documents/ProjectRPG/rpggame/src/main/java/rpg/world/shadowveil/Terrain.java'
s = open(p, encoding='utf-8').read()
old = '''            t.outcrops(); at = t.stage("outcrops", at);'''
new = '''            t.outcrops(); at = t.stage("outcrops", at);
            // (0.4.3) the ways walked clear of litter, last of all
            if (t.arch != null) { t.arch.clearWays(); at = t.stage("ways", at); }'''
assert old in s
s = s.replace(old, new, 1)
open(p, 'w', encoding='utf-8').write(s)
print('ok')
