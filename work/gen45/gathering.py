import os, json
os.chdir(r'C:\Users\John\Documents\ProjectRPG\rpggame\src\main')
p = 'resources/data/shadowveil.json'
d = json.load(open(p, encoding='utf-8'))
d['gathering'] = {
    "_about": "(0.4.5, gamefixes: Harvesting did not work - ShadowVeil had nothing wild to gather) The wild's gathering, grown over the whole square like its trees: a point tried every 'grid' cells (jittered), each kind's chance by the biome there; never on a road, a lane, a lot, a yard, water, a cliff or under a tree. Each kind is a Harvesting node of the same rules as anywhere: its level, what it gives, its experience, the time a pick takes, how many picks before it is bare, and the game minutes it takes to grow back.",
    "grid": 6,
    "kinds": {
        "herb_patch": {"name": "Herb patch", "level": 1, "item": "guam", "xp": 11, "time": 2.2, "stock": 3, "block": "HERB_PATCH",
                       "biomes": {"meadow": 0.05, "oakwood": 0.06, "copse": 0.06, "willowmeadow": 0.06, "lakeshore": 0.05, "clearing": 0.07, "highland": 0.03, "upland": 0.03, "mediterranean": 0.04, "cypress": 0.03, "oasis": 0.08, "heath": 0.025, "marsh": 0.03, "pinewood": 0.02}},
        "flax": {"name": "Flax", "level": 5, "item": "flax", "xp": 8, "time": 1.8, "stock": 4, "block": "FLAX_PATCH",
                 "biomes": {"meadow": 0.035, "willowmeadow": 0.04, "lakeshore": 0.03, "marsh": 0.025, "heath": 0.015, "mediterranean": 0.02, "vineyard": 0.02}},
        "berry_bush": {"name": "Redberry bush", "level": 10, "item": "berries", "xp": 18, "time": 2.4, "stock": 3, "block": "BERRY_BUSH",
                       "biomes": {"oakwood": 0.04, "copse": 0.045, "scrubwood": 0.04, "clearing": 0.05, "deadwood": 0.015, "pinewood": 0.025, "upland": 0.02, "highland": 0.015, "heath": 0.02, "scrub": 0.015}}
    },
    "clearance": {"_about": "Kept this far (cells) from a lot and from a road: the gatherer's ground is the wild's.", "built": 3, "road": 1}
}
json.dump(d, open(p, 'w', encoding='utf-8'), indent=1, ensure_ascii=False)

os.chdir('java/rpg')
s = open('objects/Objects.java', encoding='utf-8').read()
old = '''        // (0.4.1) the buildings' doors: a door hung in every doorway that takes one, closed (the rooms behind are kept
        // for 0.4.2), on the building's own floor
        nextFixture = Ids.SV_DOORS;'''
new = '''        gathering(t);
        // (0.4.1) the buildings' doors: a door hung in every doorway that takes one, closed (the rooms behind are kept
        // for 0.4.2), on the building's own floor
        nextFixture = Ids.SV_DOORS;'''
assert old in s; s = s.replace(old, new, 1)
old = '''    private void node(Node.Kind kind, String def, String name, Skill skill, int level, rpg.item.ItemDef product, int xp, float time, int stock, float respawn, int block, int[] at, int leaves, int height) {'''
new = '''    /**
     * (0.4.5, gamefixes: Harvesting did not work - ShadowVeil had nothing wild to gather) the wild's herb patches, flax
     * and berry bushes, grown by biome from the data ('gathering'), each a Harvesting node like the devmap's: on open
     * ground of the wild, never on a way, a lot, a yard, water, a cliff or under a tree.
     */
    private void gathering(rpg.world.shadowveil.Terrain t) {
        var a = rpg.world.shadowveil.Atlas.get();
        java.util.Map<String, Object> gd = rpg.world.shadowveil.Atlas.map(a.root, "gathering");
        java.util.Map<String, Object> kinds = rpg.world.shadowveil.Atlas.map(gd, "kinds");
        if (kinds.isEmpty()) return;
        int grid = Math.max(3, rpg.world.shadowveil.Atlas.i(gd, "grid", 6));
        java.util.Map<String, Object> clear = rpg.world.shadowveil.Atlas.map(gd, "clearance");
        int builtGap = rpg.world.shadowveil.Atlas.i(clear, "built", 3), roadGap = rpg.world.shadowveil.Atlas.i(clear, "road", 1);
        int made = 0;
        for (int gx = 0; gx < t.S; gx += grid) for (int gz = 0; gz < t.S; gz += grid) {
            long h = (gx * 73856093L ^ gz * 19349663L ^ 0x5EED_6A7EL) * 0x9E3779B97F4A7C15L; h ^= h >>> 31;
            int x = gx + (int) ((h >>> 3) % grid), z = gz + (int) ((h >>> 13) % grid);
            if (!t.in(x, z)) continue;
            int i = t.idx(x, z);
            String biome = t.biomes.get(t.biome[i] & 0xFF);
            float roll = ((h >>> 23) & 0xFFFF) / 65536f;
            for (var e : kinds.entrySet()) {
                if (!(e.getValue() instanceof java.util.Map<?, ?> km)) continue;
                @SuppressWarnings("unchecked") java.util.Map<String, Object> k = (java.util.Map<String, Object>) km;
                double chance = rpg.world.shadowveil.Atlas.d(rpg.world.shadowveil.Atlas.map(k, "biomes"), biome, 0);
                if (roll >= chance) { roll -= (float) chance; continue; }
                if (!wildGround(t, x, z, builtGap, roadGap)) break;
                int y = t.top[i] + 1;
                if (g.vox.block(x, y, z) != Blocks.AIR || g.vox.block(x, y + 1, z) != Blocks.AIR || !g.vox.solid(x, y - 1, z)) break;
                int block;
                try { block = Blocks.class.getField(rpg.world.shadowveil.Atlas.s(k, "block", "HERB_PATCH")).getInt(null); } catch (ReflectiveOperationException ex) { break; }
                rpg.item.ItemDef item = rpg.item.Items.find(rpg.world.shadowveil.Atlas.s(k, "item", "guam"));
                if (item == null) break;
                node(Node.Kind.PATCH, e.getKey(), rpg.world.shadowveil.Atlas.s(k, "name", "Patch"), Skill.HARVESTING, rpg.world.shadowveil.Atlas.i(k, "level", 1), item,
                        rpg.world.shadowveil.Atlas.i(k, "xp", 10), (float) rpg.world.shadowveil.Atlas.d(k, "time", 2), rpg.world.shadowveil.Atlas.i(k, "stock", 3), g.cfg.patchRespawn, block, new int[]{x, z}, 0, 0);
                Node n = nodes.get(nodes.size() - 1);
                n.base(y);
                n.setBlocks(g, true);
                made++;
                break;
            }
        }
        System.out.println("[gathering] " + made + " wild herb patches, flax and berry bushes");
    }

    /** Open ground of the wild: dry, level enough, off every way and lot by its gap, no tree, no cliff. */
    private static boolean wildGround(rpg.world.shadowveil.Terrain t, int x, int z, int builtGap, int roadGap) {
        int i = t.idx(x, z);
        int bad = rpg.world.shadowveil.Terrain.TREE | rpg.world.shadowveil.Terrain.CLIFF | rpg.world.shadowveil.Terrain.BRIDGE | rpg.world.shadowveil.Terrain.FALLS;
        if ((t.flags[i] & bad) != 0 || t.water[i] >= 0) return false;
        for (int dx = -builtGap; dx <= builtGap; dx++) for (int dz = -builtGap; dz <= builtGap; dz++) {
            if (!t.in(x + dx, z + dz)) return false;
            int f = t.flags[t.idx(x + dx, z + dz)];
            if ((f & (rpg.world.shadowveil.Terrain.BUILT | rpg.world.shadowveil.Terrain.YARD | rpg.world.shadowveil.Terrain.SQUARE)) != 0) return false;
            if (Math.abs(dx) <= roadGap && Math.abs(dz) <= roadGap && (f & (rpg.world.shadowveil.Terrain.ROAD | rpg.world.shadowveil.Terrain.LANE)) != 0) return false;
            if (Math.abs(dx) <= 1 && Math.abs(dz) <= 1 && Math.abs(t.top[t.idx(x + dx, z + dz)] - t.top[i]) > 1) return false;
        }
        return true;
    }

    private void node(Node.Kind kind, String def, String name, Skill skill, int level, rpg.item.ItemDef product, int xp, float time, int stock, float respawn, int block, int[] at, int leaves, int height) {'''
assert old in s; s = s.replace(old, new, 1)
open('objects/Objects.java', 'w', encoding='utf-8').write(s)
print('ok')
