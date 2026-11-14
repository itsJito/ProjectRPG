import re
R = r'C:\Users\John\Documents\ProjectRPG\rpggame\src\main\java\rpg'

def patch(path, pairs):
    p = R + path
    s = open(p, encoding='utf-8').read()
    for a, b in pairs:
        assert s.count(a) == 1, (path, a[:70], s.count(a))
        s = s.replace(a, b)
    open(p, 'w', encoding='utf-8').write(s)

# Wreckage.restoreAll
patch(r'\objects\Wreckage.java', [(
'''    // ------------------------------------------------------------------ the world remembers''',
'''    /**
     * (0.4.6, gamefixes: reset all destruction on reloading, sleeping, dying or using the shrine) every broken piece of
     * furniture whole again where it stood - a chandelier back on its chain, its candles lit - and the splinters gone.
     * Returns how many were mended.
     */
    public int restoreAll() {
        int n = 0;
        for (var e : states.entrySet()) {
            State s = e.getValue();
            if (s.gone.isEmpty() && !s.wrecked) continue;
            if (g.objects.get(e.getKey()) instanceof Furniture f && s.wrecked) { f.placed = true; f.place(g, true); }
            n++;
        }
        states.clear();
        cellsFor = -1;
        while (!shards.isEmpty()) release(shards.pollFirst());
        wrecked = 0;
        return n;
    }

    // ------------------------------------------------------------------ the world remembers''')])

# Breakables.restoreAll + Breakable home pose + rebuild
patch(r'\objects\Breakable.java', [(
'''        built = c;
        qw = (float) Math.cos(yaw / 2); qy = (float) Math.sin(yaw / 2);
    }''',
'''        built = c;
        qw = (float) Math.cos(yaw / 2); qy = (float) Math.sin(yaw / 2);
        homeX = x; homeY = y; homeZ = z; homeYaw = yaw;
    }
    /** (0.4.6) Where and how it was first set down (a restored world puts it back there, whole). */
    public final float homeX, homeY, homeZ, homeYaw;
    /** Whole again where it was first set down: every voxel back, its pose its first. */
    public void rebuild() {
        java.util.Arrays.fill(mat, AIR); java.util.Arrays.fill(board, (byte) 0);
        java.util.Arrays.fill(vox.col, 0);
        Shapes.build(this);
        for (int i = 0; i < mat.length; i++) hp[i] = mat[i] != AIR ? TOUGH[mat[i]] : 0;
        lost = 0; broken = false; removed = false;
        x = homeX; y = homeY; z = homeZ; qw = (float) Math.cos(homeYaw / 2); qx = 0; qy = (float) Math.sin(homeYaw / 2); qz = 0;
        version++;
    }''')])

patch(r'\objects\Breakables.java', [(
'''    /** All bodies let go (a world unloaded). */''',
'''    /** (0.4.6) Every crate, barrel and sign whole again where it was first set down, and the splinters gone. Returns how many. */
    public int restoreAll() {
        int n = 0;
        for (Breakable b : all) {
            boolean moved = Math.abs(b.x - b.homeX) > 0.05f || Math.abs(b.z - b.homeZ) > 0.05f || Math.abs(b.y - b.homeY) > 0.05f;
            if (!b.broken && b.lost == 0 && !moved) continue;
            b.rebuild();
            respawn(b);
            n++;
        }
        while (!debris.isEmpty()) release(debris.pollFirst());
        return n;
    }

    /** All bodies let go (a world unloaded). */''')])

# Repairs.restoreAll
patch(r'\world\Repairs.java', [(
'''    public List<Damage> open() { return new ArrayList<>(open.values()); }''',
'''    public List<Damage> open() { return new ArrayList<>(open.values()); }
    /** (0.4.6) Every broken pane and block back as it was, and nobody's errand to mend them left. Returns how many. */
    public int restoreAll() {
        int n = 0;
        for (Damage d : open.values()) { if (g.vox.block(d.x(), d.y(), d.z()) != d.original()) g.setBlock(d.x(), d.y(), d.z(), d.original()); n++; }
        open.clear(); claimedBy.clear(); spots.clear(); known.clear(); reported.clear(); orders.clear();
        return n;
    }''')])

# Rpg.restoreWorld
patch(r'\Rpg.java', [(
'''    public void respawn() {''',
'''    /**
     * (0.4.6, gamefixes: reset all destruction and everything upon reloading, sleeping, dying or using the shrine) the
     * world mended: broken furniture (a chandelier and its light among it), crates and barrels, windows and walls all as
     * they were built, the debris swept away.
     */
    public void restoreWorld(String why) {
        int f = wreckage == null ? 0 : wreckage.restoreAll(), b = breakables == null ? 0 : breakables.restoreAll(), r = repairs == null ? 0 : repairs.restoreAll();
        if (f + b + r > 0) { mapDirty = true; state.commit("WorldRestored", player == null ? 0 : player.id, 0, null, 0, why + ": " + f + " furnishings, " + b + " crates and barrels, " + r + " blocks"); }
    }

    public void respawn() {'''),
('''        law.playerRespawned();
        state.commit("PlayerRespawned", player.id, 0, DevMap.ALPHA, 0, "at the graveyard");''',
'''        law.playerRespawned();
        restoreWorld("woke after dying");
        state.commit("PlayerRespawned", player.id, 0, DevMap.ALPHA, 0, "at the graveyard");''')])

patch(r'\SaveSystem.java', [(
'''        g.state.commit("GameLoaded", g.player.id, 0, null, 0, file + " (saved at revision " + Json.lng(root, "revision", 0) + ")");
        return true;''',
'''        g.restoreWorld("loaded");
        g.state.commit("GameLoaded", g.player.id, 0, null, 0, file + " (saved at revision " + Json.lng(root, "revision", 0) + ")");
        return true;''')])

patch(r'\objects\Furniture.java', [(
'''            g.advanceTime(minutes, "sleeping");
            a.hp = a.maxHp(); a.stamina = a.maxStamina();''',
'''            g.advanceTime(minutes, "sleeping");
            if (a == g.player) g.restoreWorld("slept");
            a.hp = a.maxHp(); a.stamina = a.maxStamina();''')])

patch(r'\world\Shrines.java', [(
'''        g.ai.alarms.clear();
        // bound to it''',
'''        g.ai.alarms.clear();
        if (a == g.player) g.restoreWorld("rested at " + s.name);
        // bound to it''')])

patch(r'\law\Law.java', [(
'''        g.advanceTime(sentenceRemaining(), "sleeping through the sentence");''',
'''        g.advanceTime(sentenceRemaining(), "sleeping through the sentence");
        g.restoreWorld("slept through a sentence");''')])

# chandelier / any lit piece: no light once broken, dimmer as its parts go
patch(r'\render\Gfx.java', [(
'''    private void furnitureLight(Furniture fu) {
        float[] l = fu.kind.light();
        if (l == null || !inFog(fu.x, fu.z, 10)) return;''',
'''    private void furnitureLight(Furniture fu) {
        float[] l = fu.kind.light();
        if (l == null || !inFog(fu.x, fu.z, 10)) return;
        // (0.4.6, gamefixes: a broken chandelier still lit the room) a broken light gives none; one knocked about gives less
        // (its candles gone with the parts that held them)
        var wr = g.wreckage == null ? null : g.wreckage.states.get(fu.id);
        float whole = wr == null ? 1 : wr.wrecked ? 0 : 1 - wr.gone.cardinality() / (float) Math.max(1, wr.hp.length);
        if (whole < 0.6f) return;
        if (whole < 1) l = new float[]{l[0] * whole, l[1] * whole, l[2] * whole, l[3] * whole, l[4]};''')])
print('ok')
