p = 'C:/Users/John/Documents/ProjectRPG/rpggame/src/main/java/rpg/animal/Animals.java'
s = open(p, encoding='utf-8').read()
old = '''        // (0.4.0) on ShadowVeil each habitat moves whole to the wild the design gives it (rpg.world.shadowveil.Settle)
        if (rpg.world.WorldMap.shadowveil()) { wild = moved(wild); farm = moved(farm); southWood = moved(southWood); }
        habitats.add(wild); habitats.add(farm); habitats.add(southWood);'''
new = '''        // (0.4.0) on ShadowVeil each habitat moves whole to the wild the design gives it (rpg.world.shadowveil.Settle)
        // (0.4.3, gamefixes: wildlife throughout the world, no predators near the towns) the farm's stock moves to its
        // stables; the devmap's two woods do not come (their wolves and lions were set down beside Loxford) - the wild's
        // own habitats are grown over the whole map from its biomes instead (wilds())
        if (rpg.world.WorldMap.shadowveil()) { farm = moved(farm); habitats.add(farm); wild = null; southWood = null; wilds(); return; }
        habitats.add(wild); habitats.add(farm); habitats.add(southWood);'''
assert old in s
s = s.replace(old, new)
old = '''    /** Set while the starting roster is placed'''
new = '''    /** (0.4.3) ShadowVeil's wild habitats' starting groups: {habitat, species, females, males, young, {x, z}}. */
    private final List<Object[]> wildGroups = new ArrayList<>();
    /**
     * (0.4.3) The wild's habitats over all of ShadowVeil, from the design's 'fauna.wilds': a point every so many cells, its
     * biome's groups each by its chance; prey clear of every building, predators only where no kingdom's law holds and far
     * from any settlement. Each habitat drinks at the nearest bank and rests about its middle.
     */
    private void wilds() {
        var a = rpg.world.shadowveil.Atlas.get();
        java.util.Map<String, Object> w = rpg.world.shadowveil.Atlas.map(a.map("fauna"), "wilds");
        var t = rpg.world.shadowveil.ShadowVeil.terrain();
        if (t == null || w.isEmpty()) return;
        int grid = rpg.world.shadowveil.Atlas.i(w, "grid", 72), size = rpg.world.shadowveil.Atlas.i(w, "size", 40);
        java.util.Map<String, Object> clear = rpg.world.shadowveil.Atlas.map(w, "clearance"), biomes = rpg.world.shadowveil.Atlas.map(w, "biomes");
        float preyClear = (float) rpg.world.shadowveil.Atlas.d(clear, "prey", 36), predClear = (float) rpg.world.shadowveil.Atlas.d(clear, "predator", 170);
        List<float[]> built = new ArrayList<>(), settled = new ArrayList<>();
        if (t.arch != null) for (var st : t.arch.structures) {
            built.add(new float[]{st.cx(), st.cz()});
            if (!DevMap.WILDS.equals(rpg.world.shadowveil.ShadowVeil.jurisdiction(st.cx(), st.cz()))) settled.add(new float[]{st.cx(), st.cz()});
        }
        // every census home counts as a settlement's too (the farms and halls outside the kingdoms' cores)
        for (var as : rpg.world.shadowveil.Census.all().values()) if (as.homeStage() != null && !"Wilds".equals(as.kingdom())) settled.add(as.homeStage());
        int k = 0;
        for (int x = grid / 2; x < t.S - 8; x += grid) for (int z = grid / 2; z < t.S - 8; z += grid) {
            // a little off the grid, so the habitats do not stand in rows
            float px = x + (float) ((rpg.world.shadowveil.Noise.hash(a.seed, x, z, 91) - 0.5) * grid * 0.6), pz = z + (float) ((rpg.world.shadowveil.Noise.hash(a.seed, x, z, 92) - 0.5) * grid * 0.6);
            float[] p = rpg.world.shadowveil.ShadowVeil.dry(px, pz);
            int i = t.idx((int) p[0], (int) p[1]);
            if (t.water[i] >= 0) continue;
            List<?> groups = rpg.world.shadowveil.Atlas.arr(biomes, t.biomeAt((int) p[0], (int) p[1]));
            if (groups.isEmpty()) continue;
            float nearBuilt = nearest(built, p), nearSettled = nearest(settled, p);
            boolean wildsLaw = DevMap.WILDS.equals(rpg.world.shadowveil.ShadowVeil.jurisdiction(p[0], p[1]));
            if (nearBuilt < preyClear) continue;
            List<Object[]> here = new ArrayList<>();
            for (Object o : groups) {
                List<?> gr = (List<?>) o;
                Species sp;
                try { sp = Species.valueOf(String.valueOf(gr.get(0))); } catch (IllegalArgumentException e) { continue; }
                if (sp.predator() && (!wildsLaw || nearSettled < predClear)) continue;
                double chance = rpg.world.shadowveil.Atlas.dn(gr.get(4)) / 100.0;
                if (rpg.world.shadowveil.Noise.hash(a.seed, x, z, 93 + sp.ordinal()) >= chance) continue;
                here.add(new Object[]{sp, rpg.world.shadowveil.Atlas.n(gr.get(1)), rpg.world.shadowveil.Atlas.n(gr.get(2)), rpg.world.shadowveil.Atlas.n(gr.get(3))});
            }
            if (here.isEmpty()) continue;
            int[] drink = rpg.world.shadowveil.ShadowVeil.fishingSpot(p[0], p[1]);
            float[][] water = drink == null || Math.hypot(drink[0] - p[0], drink[1] - p[1]) > size ? new float[0][] : bank(drink);
            float[][] rest = new float[4][];
            for (int r = 0; r < 4; r++) rest[r] = rpg.world.shadowveil.ShadowVeil.dry(p[0] + (float) Math.cos(r * 1.57 + 0.4) * size * 0.3f, p[1] + (float) Math.sin(r * 1.57 + 0.4) * size * 0.3f);
            Habitat h = new Habitat("wild_" + k++, "the wild (" + t.biomeAt((int) p[0], (int) p[1]) + ")", p[0] - size / 2f, p[1] - size / 2f, p[0] + size / 2f, p[1] + size / 2f, 0, water, rest);
            for (Object[] gr : here) h.cap((Species) gr[0], Math.max(2, ((int) gr[1] + (int) gr[2] + (int) gr[3]) * 2));
            habitats.add(h);
            for (Object[] gr : here) wildGroups.add(new Object[]{h, gr[0], gr[1], gr[2], gr[3], p});
        }
    }
    private static float nearest(List<float[]> pts, float[] p) {
        float best = Float.MAX_VALUE;
        for (float[] q : pts) best = Math.min(best, (float) Math.hypot(q[0] - p[0], q[1] - p[1]));
        return best;
    }
    /** Drinking spots on the dry bank beside a water cell, facing it. */
    private static float[][] bank(int[] water) {
        List<float[]> l = new ArrayList<>();
        for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            float[] b = rpg.world.shadowveil.ShadowVeil.dry(water[0] + d[0] * 2 + 0.5f, water[1] + d[1] * 2 + 0.5f);
            if (Math.hypot(b[0] - water[0], b[1] - water[1]) < 5) l.add(new float[]{b[0], b[1], (float) Math.atan2(-(water[0] + 0.5f - b[0]), -(water[1] + 0.5f - b[1]))});
        }
        return l.toArray(new float[0][]);
    }

    /** Set while the starting roster is placed'''
assert old in s
s = s.replace(old, new, 1)
old = '''        herd = group(farm, Species.CAT, herd, new float[]{115, 118}, 1, 0, 1);
'''
new = '''        herd = group(farm, Species.CAT, herd, new float[]{115, 118}, 1, 0, 1);
        if (wild == null) {
            // (0.4.3) ShadowVeil: each wild habitat's own groups at its middle (the farm's stock above, a town cat below)
            for (Object[] gr : wildGroups) herd = groupAt((Habitat) gr[0], (Species) gr[1], herd, (float[]) gr[5], (int) gr[2], (int) gr[3], (int) gr[4]);
            spawnNew(Species.CAT, false, true, 99.5f, 78.5f, null, herd++, 0).range = 9;
            for (Habitat h : habitats) h.count("starting", count(h));
            return;
        }
'''
assert old in s
s = s.replace(old, new, 1)
old = '''    /** A group: so many adult females and males'''
new = '''    /** (0.4.3) A group set down where its habitat really is (no devmap spot to carry over). */
    private long groupAt(Habitat h, Species s, long id, float[] at, int females, int males, int young) {
        Animal mother = null;
        for (int i = 0; i < females + males; i++) {
            float[] p = rpg.world.shadowveil.ShadowVeil.dry(at[0] + (i % 3) * 1.4f - 1.4f, at[1] + (i / 3) * 1.5f - 0.7f);
            Animal a = spawnNew(s, false, i < females, p[0], p[1], h, id, 0);
            a.homeX = at[0]; a.homeZ = at[1]; a.range = s.predator() || s == Species.BROWN_BEAR ? 18 : 12;
            if (i < females && mother == null) mother = a;
        }
        for (int i = 0; i < young; i++) {
            float[] p = rpg.world.shadowveil.ShadowVeil.dry(at[0] + 1.2f + i * 0.7f, at[1] + 1.4f);
            Animal b = spawnNew(s, true, i % 2 == 0, p[0], p[1], h, id, mother == null ? 0 : mother.id);
            b.bornAt = g.state.minutes - s.mature * (0.2f + 0.15f * i);
            b.matureAt = b.bornAt + s.mature;
            b.homeX = at[0]; b.homeZ = at[1]; b.range = 12;
        }
        return id + 1;
    }

    /** A group: so many adult females and males'''
assert old in s
s = s.replace(old, new, 1)
open(p, 'w', encoding='utf-8').write(s)
print('patched')
