p = 'C:/Users/John/Documents/ProjectRPG/rpggame/src/main/java/rpg/world/shadowveil/Architecture.java'
s = open(p, encoding='utf-8').read()
old = '''            double dd = Math.hypot(x + 0.5 - cx, z + 0.5 - cz) / R;
            if (dd >= 1) continue;
            int want = P + (int) Math.round(need * (1 - dd * dd) + Noise.hash(a.seed, x, z, 97) * 1.2);
            if (s.contains(x, z) || t.top[i] < want) t.top[i] = Math.max(t.top[i], want);
            t.flags[i] |= Terrain.CLIFF;'''
new = '''            double dd = Math.hypot(x + 0.5 - cx, z + 0.5 - cz) / R;
            if (dd >= 1) continue;
            // (0.4.3, gamefixes: the culvert cave at 573, 40, 899 was a dome of bare cobble) a smooth rise, grassed as the
            // land about it; bare rock only on the face about the opening
            double e = 1 - dd, rise = e * e * (3 - 2 * e);
            int want = P + (int) Math.round(need * rise + Noise.hash(a.seed, x, z, 97) * 0.8);
            if (s.contains(x, z) || t.top[i] < want) t.top[i] = Math.max(t.top[i], want);
            if (face(s, x, z)) t.flags[i] |= Terrain.CLIFF; else t.flags[i] &= ~Terrain.CLIFF;'''
assert old in s
s = s.replace(old, new)
old = '''    private void claim(Structure s, int margin) {'''
new = '''    /** Whether a cell is on a mouth's face: within two cells of its front, and no wider than its lot and a cell each side. */
    private static boolean face(Structure s, int x, int z) {
        return switch (s.facing) {
            case 'n' -> z >= s.z0 - 1 && z <= s.z0 + 2 && x >= s.x0 - 1 && x <= s.x1 + 1;
            case 's' -> z <= s.z1 + 1 && z >= s.z1 - 2 && x >= s.x0 - 1 && x <= s.x1 + 1;
            case 'w' -> x >= s.x0 - 1 && x <= s.x0 + 2 && z >= s.z0 - 1 && z <= s.z1 + 1;
            default -> x <= s.x1 + 1 && x >= s.x1 - 2 && z >= s.z0 - 1 && z <= s.z1 + 1;
        };
    }

    private void claim(Structure s, int margin) {'''
assert old in s
s = s.replace(old, new, 1)
open(p, 'w', encoding='utf-8').write(s)
print('ok')
