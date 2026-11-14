P=r'C:\Users\John\Documents\ProjectRPG\rpggame\src\main\java\rpg\world\shadowveil\Kit.java'
s=open(P,encoding='utf-8').read()
a='''        int mark = s.b("rock");
        boolean crypt = o.b("crypt", false);'''
b='''        boolean crypt = o.b("crypt", false);
        // (0.4.6, gamefixes: random patches of cobblestone about the graveyards) the old graves are headstones, not blocks
        // of the style's rock with a bare patch before them: their places go out with the graveyard (Rooms sets a weathered
        // stone and its mound at each, solid to walk into), and the turf over them is left whole
        java.util.List<Float> old = new java.util.ArrayList<>();'''
assert a in s; s=s.replace(a,b)
a='''            if (hash(seed, u, v, 90) < 0.15) continue;
            f.put(u, P + 1, v, mark);
            if (hash(seed, u, v, 91) < 0.4) f.put(u, P + 2, v, mark);
            f.put(u, P, v + 1, Blocks.BARE_PATCH);
        }'''
b='''            if (hash(seed, u, v, 90) < 0.15) continue;
            float[] at = f.point(u + 0.5, v + 0.5);
            old.add(at[0]); old.add(at[1]);
        }
        if (!old.isEmpty()) { float[] l = new float[old.size()]; for (int i = 0; i < l.length; i++) l[i] = old.get(i); out.points.put("oldGraves", l); }'''
assert a in s; s=s.replace(a,b)
open(P,'w',encoding='utf-8').write(s)

P=r'C:\Users\John\Documents\ProjectRPG\rpggame\src\main\java\rpg\world\shadowveil\Rooms.java'
s=open(P,encoding='utf-8').read()
a='''        Registry reg = ar.registry;
        for (Architecture.Piece p : ar.pieces) {'''
b='''        Registry reg = ar.registry;
        // (0.4.6) every graveyard's old graves: a weathered headstone over a grassed mound at each place the graveyard laid
        // out, filled long ago (nothing in it), standing as stone does
        for (Architecture.Structure st : ar.structures) {
            float[] old = st.points.get("oldGraves");
            if (old == null) continue;
            for (int i = 0; i + 1 < old.length; i += 2) {
                Container stone = m.add(new Container(m.id(), "headstone", "Old gravestone", "memorial", old[i], ShadowVeil.standY(old[i], old[i + 1]), old[i + 1] - 0.45f, 0, 0));
                stone.buried = true; stone.locked = true;
            }
        }
        for (Architecture.Piece p : ar.pieces) {'''
assert a in s; s=s.replace(a,b)
open(P,'w',encoding='utf-8').write(s)
print('ok')
