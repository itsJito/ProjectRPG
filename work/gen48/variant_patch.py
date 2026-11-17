import os
SRC = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', 'rpggame', 'src', 'main', 'java', 'rpg')
def sub(p, old, new):
    s = open(p, encoding='utf-8').read()
    if old not in s: raise SystemExit('MISSING in ' + p + ': ' + old[:80])
    open(p, 'w', encoding='utf-8', newline='').write(s.replace(old, new, 1))
M = os.path.join(SRC, 'monster', 'Monster.java')
sub(M, '    public MonsterKind.Breed breed;', '''    public MonsterKind.Breed breed;
    /** (0.4.8) Which of its kind's looks this one has (MonsterKind.variants; the figure built by MonsterModels for it). */
    public int variant;
    /** (0.4.8) Lifted on its wings until then (a greater demon hovering a moment over the ground it fights on). */
    public double hoverUntil, hoverFrom;''')
S = os.path.join(SRC, 'monster', 'Monsters.java')
sub(S, '''        if (kind == MonsterKind.DRAGON) m.breed = MonsterKind.Breed.values()[(int) Math.floorMod(m.id, MonsterKind.Breed.values().length)];''',
'''        if (kind == MonsterKind.DRAGON) m.breed = MonsterKind.Breed.values()[(int) Math.floorMod(m.id, MonsterKind.Breed.values().length)];
        // (0.4.8) its look among its kind's: by its own seed; the greatest of the greater demons (level 100 and over)
        // the crested, bearded one
        m.variant = (int) Math.floorMod(m.id * 0x2545F4914F6CDD1DL >>> 33, Math.max(1, kind.variants()));
        if (kind == MonsterKind.GREATER_DEMON) m.variant = level >= rpg.Tuning.i("monsters.demonLordLevel", 100) ? 2 : m.variant % 2;''')
sub(S, '''            if (m.breed != null) o.put("breed", m.breed.name());''', '''            if (m.breed != null) o.put("breed", m.breed.name());
            if (m.variant != 0) o.put("variant", m.variant);''')
open(S, 'a').close()
s = open(S, encoding='utf-8').read()
i = s.index('            Monster m = new Monster(astratto.util.Json.lng(r, "id", 0), kind, astratto.util.Json.integer(r, "level", kind.minLevel)')
j = s.index('\n', i)
s = s[:j + 1] + '            m.variant = astratto.util.Json.integer(r, "variant", 0);\n' + s[j + 1:]
open(S, 'w', encoding='utf-8', newline='').write(s)
print('variant')
