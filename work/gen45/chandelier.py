import os
os.chdir(r'C:\Users\John\Documents\ProjectRPG\rpggame\src\main\java\rpg\world\shadowveil')
p='Fitout.java'
s=open(p,encoding='utf-8').read()
old=s[s.index('    /** A chandelier hung in the room\'s middle'):s.index('    /**\n     * The cells of a storey nobody can walk to')]
new='''    /**
     * A chandelier hung in the room's middle (under a ceiling tall enough to walk beneath it). (0.4.5, gamefixes: one hung
     * down a stairwell over the steps, another twice in the same spot) the cell nearest the middle whose ceiling is the
     * storey's own slab (not the open well of a stair, not the roof seen up through it), clear of the steps by a cell all
     * round, and no other chandelier within 'chandelierApart' cells.
     */
    private boolean placeCeiling(Storey st, Room r, Architecture.Fitted fitted, Map<String, Object> pc) {
        Plan p = st.plan;
        if (st.clear < Atlas.i(pc, "minClear", 4)) return false;
        int apart = Atlas.i(data, "chandelierApart", 4);
        double mu = r.u0 + (r.w() - 1) / 2.0, mv = r.v0 + (r.d() - 1) / 2.0;
        int[] best = null; double bestD = 1e9;
        for (int u = r.u0; u <= r.u1; u++) for (int v = r.v0; v <= r.v1; v++) {
            if (!walk(st.g[u][v]) && st.g[u][v] != FURN) continue;
            double d = Math.hypot(u - mu, v - mv);
            if (d >= bestD || !hangable(st, u, v, apart)) continue;
            bestD = d; best = new int[]{u, v};
        }
        if (best == null) return false;
        int[] rect = worldRect(p, best[0], best[1], best[0], best[1]);
        ar.pieces.add(new Architecture.Piece(p.s.id, Atlas.s(pc, "kind", "CHANDELIER"), rect[0], rect[1], rect[2], rect[3], st.walk(), Math.min(st.clear, 8), 0, ""));
        st.hungAt.add(best);
        fitted.pieces.add("chandelier");
        fitted.rooms.get(fitted.rooms.size() - 1).lit = true;
        pieces++;
        return true;
    }

    /** Whether a chandelier may hang over a cell: its ceiling the storey's own slab, no step beside it, none hung near. */
    private boolean hangable(Storey st, int u, int v, int apart) {
        Plan p = st.plan;
        for (int[] h : st.hungAt) if (Math.abs(h[0] - u) + Math.abs(h[1] - v) < apart) return false;
        for (int du = -1; du <= 1; du++) for (int dv = -1; dv <= 1; dv++) {
            int a = u + du, b = v + dv;
            if (p.in(a, b) && st.g[a][b] == STAIR) return false;
        }
        int x = p.f.wx(u, v), z = p.f.wz(u, v);
        for (int y = st.walk(); y < st.walk() + st.clear; y++) if (!open(c.get(x, y, z))) return false;
        int top = st.walk() + st.clear;
        return solid(c.get(x, top, z)) && c.get(x, top, z) != Blocks.STAIRS;
    }

'''
s=s.replace(old,new)
old2='''        /** Walls something already hangs on. */
        final Set<String> hung = new HashSet<>();'''
assert old2 in s
s=s.replace(old2,old2+'''
        /** (0.4.5) Where its chandeliers hang. */
        final List<int[]> hungAt = new ArrayList<>();''')
open(p,'w',encoding='utf-8').write(s)
print('ok')
