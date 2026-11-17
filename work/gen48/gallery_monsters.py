import os
p = os.path.join('src','main','java','rpg','Gallery.java')
s = open(p, encoding='utf-8').read()
def sub(old, new):
    global s
    if old not in s: raise SystemExit('MISSING: ' + old[:80])
    s = s.replace(old, new, 1)
sub('''            int breeds = kind == rpg.monster.MonsterKind.DRAGON ? rpg.monster.MonsterKind.Breed.values().length : 1;
            for (int b = 0; b < breeds; b++) {
                var mo = sys.display(kind, (kind.minLevel + kind.maxLevel) / 2, x0, z0, (float) Math.PI * 0.8f);
                if (kind == rpg.monster.MonsterKind.DRAGON) mo.breed = rpg.monster.MonsterKind.Breed.values()[b];''',
'''            int breeds = kind == rpg.monster.MonsterKind.DRAGON ? rpg.monster.MonsterKind.Breed.values().length : kind.variants();
            for (int b = 0; b < breeds; b++) {
                var mo = sys.display(kind, (kind.minLevel + kind.maxLevel) / 2, x0, z0, (float) Math.PI * 0.8f);
                if (kind == rpg.monster.MonsterKind.DRAGON) mo.breed = rpg.monster.MonsterKind.Breed.values()[b];
                else mo.variant = b;   // (0.4.8) each of the kind's looks''')
sub('''            for (int mode = 0; mode < (kind.flies() ? 4 : 3); mode++) {
                if ("attack".equals(System.getenv("RPG_MONSTER_MODE")) && mode != 2) continue;
                var mo = sys.display(kind, (kind.minLevel + kind.maxLevel) / 2, 50.5f, 70.5f, (float) -Math.PI / 2);
                // mode 3: a flyer aloft, flying past (taken from below and to the side, so the wings read)
                boolean aloft = mode == 3;''',
'''            // (0.4.8) a scorpion's second attack (the sting) and a greater demon's hover as a fourth sheet
            boolean extra = kind.scorpion() || kind == rpg.monster.MonsterKind.GREATER_DEMON;
            for (int mode = 0; mode < (kind.flies() || extra ? 4 : 3); mode++) {
                if ("attack".equals(System.getenv("RPG_MONSTER_MODE")) && mode < 2) continue;
                var mo = sys.display(kind, (kind.minLevel + kind.maxLevel) / 2, 50.5f, 70.5f, (float) -Math.PI / 2);
                // mode 3: a flyer aloft, flying past (taken from below and to the side, so the wings read)
                boolean aloft = mode == 3 && !extra;
                if (mode == 3 && kind == rpg.monster.MonsterKind.GREATER_DEMON) { mo.hoverFrom = g.time + 0.1; mo.hoverUntil = g.time + 4; }''')
sub('''                        if (kind.form == rpg.monster.MonsterKind.Form.HUMANOID) g.combat.attack(mo, false, kind == rpg.monster.MonsterKind.TROLL ? Actor.Combat.Move.HEAVY : rpg.combat.CombatSystem.lightMove(mo, false));
                        else { mo.windup = "bite"; mo.windupStart = g.time; mo.windupUntil = g.time + 0.5; }''',
'''                        if (kind.form == rpg.monster.MonsterKind.Form.HUMANOID) { if (mode == 2) g.combat.attack(mo, false, kind.troll() ? Actor.Combat.Move.HEAVY : rpg.combat.CombatSystem.lightMove(mo, false)); }
                        else { mo.windup = kind.scorpion() ? (mode == 3 ? "sting" : "pincer") : "bite"; mo.windupStart = g.time; mo.windupUntil = g.time + (mode == 3 ? 0.7 : 0.5); }''')
sub('''                sheet("monster-" + kind.name().toLowerCase() + "-" + (mode == 0 ? "walk" : mode == 1 ? "run" : mode == 2 ? "attack" : "fly"), ft, fl, 6);''',
'''                sheet("monster-" + kind.name().toLowerCase() + "-" + (mode == 0 ? "walk" : mode == 1 ? "run" : mode == 2 ? "attack" : kind.scorpion() ? "sting" : extra ? "hover" : "fly"), ft, fl, 6);''')
open(p, 'w', encoding='utf-8', newline='').write(s)
print('gallery monsters')
