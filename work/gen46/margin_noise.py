import re
P = r'C:\Users\John\Documents\ProjectRPG\rpggame\src\main\java\rpg\world\shadowveil\Terrain.java'
s = open(P, encoding='utf-8').read()
lines = s.split('\n')
out = []
changed = 0
for ln in lines:
    o = ln
    if ('.at(' in ln or '.fbm(' in ln or 'Noise.hash(' in ln) and 'OX' not in ln:
        # plain coordinates inside noise samples
        ln = re.sub(r'(?<![\w.])x / ', '(x - OX) / ', ln)
        ln = re.sub(r'(?<![\w.])z / ', '(z - OX) / ', ln)
        ln = ln.replace('noise.fbm(wx / scale, wz / scale, 5)', 'noise.fbm((wx - OX) / scale, (wz - OX) / scale, 5)')
        ln = ln.replace('noise.fbm((wx + salt * 1013 + k * 377) / scale, (wz - salt * 701 + k * 911) / scale, 3)',
                        'noise.fbm((wx - OX + salt * 1013 + k * 377) / scale, (wz - OX - salt * 701 + k * 911) / scale, 3)')
        ln = ln.replace('noise.fbm((x + 911) / 90.0, (z - 377) / 90.0, 4)', 'noise.fbm((x - OX + 911) / 90.0, (z - OX - 377) / 90.0, 4)')
        ln = ln.replace('Noise.hash(atlas.seed, x, z,', 'Noise.hash(atlas.seed, x - OX, z - OX,')
        ln = ln.replace('Noise.hash(atlas.seed, x + dx, z + dz,', 'Noise.hash(atlas.seed, x + dx - OX, z + dz - OX,')
        ln = ln.replace('Noise.hash(atlas.seed, x + dx * 7, z + dz * 7,', 'Noise.hash(atlas.seed, x + dx * 7 - OX, z + dz * 7 - OX,')
        ln = ln.replace('Noise.hash(atlas.seed, cx, cz, 1)', 'Noise.hash(atlas.seed, cx - OX / cell, cz - OX / cell, 1)')
        ln = ln.replace('Noise.hash(atlas.seed, cx, cz, 2)', 'Noise.hash(atlas.seed, cx - OX / cell, cz - OX / cell, 2)')
        ln = ln.replace('cx * 0.01', '(cx - OX) * 0.01').replace('cz * 0.01', '(cz - OX) * 0.01')
    if ln != o: changed += 1
    out.append(ln)
s = '\n'.join(out)
# warpOf's samples (its own lines have no .at( on the declaration line)
a = '''        return n.at(x / 170.0 + axis * 13, z / 170.0 - axis * 7) * 58 + n.at(x / 52.0 + 50, z / 52.0 + 50) * 16 + n.at(x / 17.0 - 30, z / 17.0 + 30) * 4;'''
if a in s:
    s = s.replace(a, a.replace('x / ', '(x - OX) / ').replace('z / ', '(z - OX) / ')); changed += 1
# the margin itself
a = '''        S = a.world; H = a.height; sea = a.sea;'''
assert a in s
s = s.replace(a, a + '\n        OX = a.margin;')
# declare OX next to S
m = re.search(r'\n(\s*)(public |private |)final int S', s)
if m is None:
    m = re.search(r'\n(\s*)public int S', s)
print('decl match', m.group(0) if m else None)
open(P, 'w', encoding='utf-8').write(s)
print('changed lines', changed)

A = r'C:\Users\John\Documents\ProjectRPG\rpggame\src\main\java\rpg\world\shadowveil\Architecture.java'
s = open(A, encoding='utf-8').read()
for a, b in [('Noise.hash(a.seed, x, z, 97)', 'Noise.hash(a.seed, x - a.margin, z - a.margin, 97)'),
             ('Noise.hash(a.seed, x, z, 98 + side)', 'Noise.hash(a.seed, x - a.margin, z - a.margin, 98 + side)')]:
    assert s.count(a) == 1, a
    s = s.replace(a, b)
open(A, 'w', encoding='utf-8').write(s)
