import os
os.chdir(r'C:\Users\John\Documents\ProjectRPG\rpggame\src\main\java\rpg')
def patch(path, pairs):
    s = open(path, encoding='utf-8').read()
    for old, new in pairs:
        assert old in s, (path, old[:90])
        s = s.replace(old, new, 1)
    open(path, 'w', encoding='utf-8').write(s)

# FlowField: an overlay of channel boxes onto a grown field (the aqueducts)
patch('world/FlowField.java', [
 ('''    /** The devmap's field: its channels' boxes laid in''',
  '''    /** Channels' boxes laid over the field where it is still (a grown world's aqueducts), each at its own pace. */
    public void overlay(List<Fluids.Current> currents) {
        FlowField o = of(currents, x0 + w, z0 + d);
        for (int x = x0; x < x0 + w; x++) for (int z = z0; z < z0 + d; z++) {
            int i = at(x, z), j = o.at(x, z);
            if (i < 0 || j < 0 || o.fx[j] == 0 && o.fz[j] == 0 || fx[i] != 0 || fz[i] != 0) continue;
            set(i, o.fx[j] / 1000f, o.fz[j] / 1000f, 0);
        }
    }

    /** The devmap's field: its channels' boxes laid in'''),
])

# Fluids: the field answers flow()
patch('world/Fluids.java', [
 ('''    public void current(int x0, int z0, int x1, int z1, float dx, float dz, float speed) {''',
  '''    /** (0.4.5) The water's motion everywhere (FlowField): set once the map's water is known. */
    public FlowField field;

    public void current(int x0, int z0, int x1, int z1, float dx, float dz, float speed) {'''),
 ('''        float[] out = new float[2];
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        if (!in(x, y, z) && !in(x, y - 0.5f, z)) return out;
        // (0.4.1) where reaches overlap (a river's bends) the water runs at their average, never their sum
        int over = 0;
        for (Current c : currents) {
            if (!c.holds(bx, bz)) continue;
            // across the channel: 1 in the middle, about half at the bank
            float wide = Math.abs(c.dz) > Math.abs(c.dx) ? c.x1 - c.x0 + 1 : c.z1 - c.z0 + 1;
            float across = Math.abs(c.dz) > Math.abs(c.dx) ? x - (c.x0 + c.x1 + 1) * 0.5f : z - (c.z0 + c.z1 + 1) * 0.5f;
            float middle = 1 - 0.5f * Math.min(1, Math.abs(across) / Math.max(0.5f, wide * 0.5f));
            float s = surface(x, y, z);
            float near = Float.isNaN(s) ? 1 : Math.max(0.35f, 1 - Math.max(0, s - y) * 0.35f);
            out[0] += c.dx * c.speed * middle * near;
            out[1] += c.dz * c.speed * middle * near;
            over++;
        }
        if (over > 1) { out[0] /= over; out[1] /= over; }
        return out;''',
  '''        float[] out = new float[2];
        if (field == null || !in(x, y, z) && !in(x, y - 0.5f, z)) return out;
        // (0.4.5) from the field (eased between columns), strongest at the surface and slack along the bed
        field.sample(x, z, out);
        float s = surface(x, y, z);
        float near = Float.isNaN(s) ? 1 : Math.max(0.35f, 1 - Math.max(0, s - y) * 0.35f);
        out[0] *= near; out[1] *= near;
        return out;'''),
])

# Rpg: the devmap's field from its channels
patch('Rpg.java', [
 ('''        if (map.kind == rpg.world.WorldMap.Kind.DEVMAP) DevMap.currents(fluids); else rpg.world.shadowveil.ShadowVeil.currents(fluids);''',
  '''        if (map.kind == rpg.world.WorldMap.Kind.DEVMAP) { DevMap.currents(fluids); fluids.field = rpg.world.FlowField.of(fluids.currents(), map.sizeX, map.sizeZ); }
        else rpg.world.shadowveil.ShadowVeil.currents(fluids);'''),
])

# ShadowVeil: the field grown from the terrain's own water
s = open('world/shadowveil/ShadowVeil.java', encoding='utf-8').read()
i = s.index('    /** The rivers\' flow (Fluids)')
j = s.index('    /**\n     * (0.4.3, gamefixes: \'goto\' still took')
s = s[:i] + '''    /**
     * (0.4.5, gamefixes: flowing water set up for all water, by itself) the water's motion (Fluids' field) grown from the
     * terrain's own water - every river, brook, lake and outflow running toward the sea (rpg.world.FlowField) - with the
     * aqueducts' channels laid over it. The field is kept with the terrain: grown once per world.
     */
    public static void currents(rpg.world.Fluids f) {
        if (terrain == null) return;
        if (field == null || fieldOf != terrain) {
            Terrain t = terrain;
            int n = t.S * t.S;
            boolean[] sea = new boolean[n], river = new boolean[n];
            for (int i = 0; i < n; i++) { sea[i] = (t.flags[i] & Terrain.SEA) != 0; river[i] = (t.flags[i] & (Terrain.RIVER | Terrain.FALLS)) != 0; }
            long t0 = System.nanoTime();
            field = rpg.world.FlowField.grow(t.S, t.water, sea, river);
            if (t.arch != null) {
                for (float[] c : t.arch.channels) f.current((int) c[0], (int) c[1], (int) c[2], (int) c[3], c[4], c[5], rpg.Tuning.f("water.aqueductSpeed", 1.1f));
                field.overlay(f.currents());
            }
            fieldOf = t;
            System.out.printf("[water] flow field grown in %.0f ms: %d columns moving%n", (System.nanoTime() - t0) / 1e6, field.moving);
        }
        f.field = field;
    }
    private static rpg.world.FlowField field;
    private static Terrain fieldOf;

''' + s[j:]
open('world/shadowveil/ShadowVeil.java', 'w', encoding='utf-8').write(s)
print('ok')
