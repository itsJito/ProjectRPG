p = 'C:/Users/John/Documents/ProjectRPG/rpggame/src/main/java/rpg/world/shadowveil/Architecture.java'
s = open(p, encoding='utf-8').read()
old = '''            for (int[] p : line) for (int ox = -2; ox <= 2; ox++) for (int oz = -2; oz <= 2; oz++) {
                if (!t.in(p[0] + ox, p[1] + oz)) continue;
                int i = t.idx(p[0] + ox, p[1] + oz);
                if ((t.flags[i] & (Terrain.ROAD | Terrain.BRIDGE)) != 0) continue;
                t.flags[i] |= Math.abs(ox) <= 1 && Math.abs(oz) <= 1 ? Terrain.BUILT : Terrain.YARD;'''
new = '''            // (0.4.3, gamefixes: map glitches at 246, 55, 607 - the aqueduct ran over Valgard's roofs) its line claimed
            // three cells either side, so no lot (nor a lot's eaves) comes within reach of its arches
            for (int[] p : line) for (int ox = -4; ox <= 4; ox++) for (int oz = -4; oz <= 4; oz++) {
                if (!t.in(p[0] + ox, p[1] + oz)) continue;
                int i = t.idx(p[0] + ox, p[1] + oz);
                if ((t.flags[i] & (Terrain.ROAD | Terrain.BRIDGE)) != 0) continue;
                t.flags[i] |= Math.abs(ox) <= 3 && Math.abs(oz) <= 3 ? Terrain.BUILT : Terrain.YARD;'''
assert old in s
s = s.replace(old, new)
old = '''            for (int side = -1; side <= 1; side++) {
                int x = p[0] + sxa * side, z = p[1] + sza * side;
                if (!c.in(x, deck, z)) continue;
                int g = c.ground(x, z);
                if (g >= deck) for (int y = deck + 1; y <= g; y++) c.set(x, y, z, Blocks.AIR);'''
new = '''            for (int side = -1; side <= 1; side++) {
                int x = p[0] + sxa * side, z = p[1] + sza * side;
                if (!c.in(x, deck, z)) continue;
                // (0.4.3) never through a building's lot (its walls and roof stand), and where the ground stands over the
                // channel it runs on as a buried conduit - no trench is cut through the town above it
                if (inLot(x, z, 1)) continue;
                int g = c.ground(x, z);
                if (g >= deck + 1) continue;'''
assert old in s
s = s.replace(old, new)
old = '''    /** A building over the water (a pile hut, a boathouse)'''
new = '''    /** Whether a cell lies in a structure's lot (or within a margin of one). */
    private boolean inLot(int x, int z, int m) {
        for (Structure st : structures) if (x >= st.x0 - m && x <= st.x1 + m && z >= st.z0 - m && z <= st.z1 + m && !st.template.equals("plaza")) return true;
        return false;
    }

    /** A building over the water (a pile hut, a boathouse)'''
assert old in s
s = s.replace(old, new, 1)
open(p, 'w', encoding='utf-8').write(s)
print('ok')
