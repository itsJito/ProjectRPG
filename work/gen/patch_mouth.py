p = 'C:/Users/John/Documents/ProjectRPG/rpggame/src/main/java/rpg/world/shadowveil/Kit.java'
s = open(p, encoding='utf-8').read()
old = '''        int fall = o.i("fall", 2);
        for (int k = 0; k < len; k++) {
            int floorY = P - k / fall;'''
new = '''        int fall = o.i("fall", 2);
        // (0.4.3, gamefixes: the culvert cave at 573, 40, 899 was a box of loose cobble on the open meadow) where the
        // passage would stand out of the ground, the hill it runs into is raised first - an earthen mound over it, its
        // flanks falling away to the ground, grassed as the ground about it - so the mouth opens in a bank, not a box
        if (kind.equals("cave")) mound(f, seed, u0, u1, len, fall, tall);
        for (int k = 0; k < len; k++) {
            int floorY = P - k / fall;'''
assert old in s
s = s.replace(old, new)
old = '''                        if (kind.equals("cave")) { if (cur == Blocks.AIR && (floorC || k > 1)) f.put(u, y, k, rock); else if (cur != Blocks.AIR && hash(seed, u, y + k * 31, 74) < 0.35) f.put(u, y, k, rock); }'''
new = '''                        // the rock shows only about the mouth (its first two steps); further in, the bank's own earth
                        if (kind.equals("cave")) { if (cur == Blocks.AIR || k <= 1) f.put(u, y, k, rock); else if (hash(seed, u, y + k * 31, 74) < 0.2) f.put(u, y, k, rock); }'''
assert old in s
s = s.replace(old, new)
old = '''    /** A market stall: four posts, a cloth awning, a counter across the front. */'''
new = '''    /** (0.4.3) The bank a cave's passage runs into: earth over its whole length, two cells over its roof, sloping away. */
    static void mound(Frame f, long seed, int u0, int u1, int len, int fall, int tall) {
        for (int u = u0 - 6; u <= u1 + 6; u++) for (int v = 0; v <= len + 5; v++) {
            int x = f.wx(u, v), z = f.wz(u, v);
            int g = f.c.ground(x, z);
            if (f.c.get(x, g, z) == Blocks.WATER) continue;
            int roof = f.P - Math.min(v, len - 1) / fall + tall + 1;
            int side = Math.max(0, Math.max(u0 - 1 - u, u - (u1 + 1)));
            int front = v == 0 ? 1 : 0, back = Math.max(0, v - len);
            int top = roof + 2 - side - front - back + (hash(seed, u, v, 77) < 0.3 ? 1 : 0);
            if (top <= g) continue;
            int surface = f.c.get(x, g, z);
            if (surface == Blocks.AIR || surface == Blocks.STONE || surface == Blocks.COBBLE || surface == Blocks.GRAVEL) surface = Blocks.GRASS;
            for (int y = g; y < top; y++) f.c.set(x, y, z, Blocks.DIRT);
            f.c.set(x, top, z, surface);
        }
    }

    /** A market stall: four posts, a cloth awning, a counter across the front. */'''
assert old in s
s = s.replace(old, new, 1)
open(p, 'w', encoding='utf-8').write(s)
print('ok')
