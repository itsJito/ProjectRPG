p='rpggame/src/main/java/rpg/world/shadowveil/Fitout.java'
s=open(p,encoding='utf-8').read()
def rep(a,b,cnt=1):
    global s
    assert s.count(a)==cnt,(a[:70],s.count(a)); s=s.replace(a,b)
rep("""        for (Storey st : all) reachCheck(st, fitted);
    }""","""        for (Storey st : all) reachCheck(st, fitted);
        if (s.id.equals(DEBUG)) for (Storey st : all) dump(st);
    }
    /** (debugging) RPG_FIT_DEBUG names a structure whose storeys are printed after fitting. */
    private static final String DEBUG = System.getenv().getOrDefault("RPG_FIT_DEBUG", "");
    private void dump(Storey st) {
        StringBuilder b = new StringBuilder("storey y" + st.floorY + " clear " + st.clear + " frame " + st.plan.f.facing + " " + st.plan.W + "x" + st.plan.D + " entries " + st.entries.size() + "\\n");
        for (int v = st.plan.D - 1; v >= 0; v--) {
            for (int u = 0; u < st.plan.W; u++) {
                int x = st.plan.f.wx(u, v), z = st.plan.f.wz(u, v);
                int blk = c.get(x, st.walk(), z);
                char ch = switch (st.g[u][v]) { case VOID -> ' '; case OPEN -> '.'; case WALL -> '#'; case STAIR -> '/'; case KEEP -> 'k'; default -> 'F'; };
                if (st.g[u][v] == FURN) ch = blk == Blocks.BED ? 'B' : blk == Blocks.SEAT ? 's' : blk == Blocks.TABLE ? 'T' : blk == Blocks.HEADSTONE ? 'H' : blk == Blocks.CRATE ? 'c' : 'F';
                for (int[] en : st.entries) if (en[0] == u && en[1] == v) ch = 'E';
                b.append(ch);
            }
            b.append('\\n');
        }
        for (Room r : st.rooms) b.append("  room ").append(r.use).append(" u").append(r.u0).append("-").append(r.u1).append(" v").append(r.v0).append("-").append(r.v1).append(r.walled ? " walled" : "").append('\\n');
        System.out.print(b);
    }""")
open(p,'w',encoding='utf-8').write(s)
print('ok')
