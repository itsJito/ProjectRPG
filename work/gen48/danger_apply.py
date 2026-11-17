import os
SRC = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', 'rpggame', 'src', 'main', 'java', 'rpg')
def sub(p, old, new):
    s = open(p, encoding='utf-8').read()
    if old not in s: raise SystemExit('MISSING in ' + p + ': ' + old[:80])
    open(p, 'w', encoding='utf-8', newline='').write(s.replace(old, new, 1))

M = os.path.join(SRC, 'monster', 'Monsters.java')
sub(M, '''                int lo = rpg.world.shadowveil.Atlas.n(gr.get(1)), hi = rpg.world.shadowveil.Atlas.n(gr.get(2)), cap = rpg.world.shadowveil.Atlas.n(gr.get(3));''',
'''                int lo = rpg.world.shadowveil.Atlas.n(gr.get(1)), hi = rpg.world.shadowveil.Atlas.n(gr.get(2)), cap = rpg.world.shadowveil.Atlas.n(gr.get(3));
                // (0.4.8, gamefixes: the dangerous further from Loxford) the territory's levels are its place's: a kind
                // too strong for where it would live makes no home there
                int[] band = rpg.world.shadowveil.Danger.levels(kind, p[0], p[1]);
                if (band == null) continue;
                lo = band[0]; hi = band[1];''')

D = os.path.join(SRC, 'dungeon', 'Dungeons.java')
sub(D, '''                    // (0.4.7) under ShadowVeil each level down is harder, and its residents are made on its own floor
                    if (rpg.world.WorldMap.shadowveil()) lv = Math.min(kind.maxLevel, lv + (l.depth - 1) * DEPTH_LEVELS);''',
'''                    // (0.4.7) under ShadowVeil each level down is harder, and its residents are made on its own floor;
                    // (0.4.8, gamefixes: dungeons more dangerous further from Loxford) their levels are the band of the
                    // ground over the mouth, climbing through it room by room and level by level, the master at its top
                    if (rpg.world.WorldMap.shadowveil()) {
                        int levelsDown = siteOf == null ? 1 : Math.max(1, siteOf.levels());
                        double share = ((l.depth - 1) + Math.min(1, s.arg() / (double) (deepest + 1))) / levelsDown;
                        float mx = siteOf == null ? l.worldX(l.entryX) : siteOf.mouthX(), mz = siteOf == null ? l.worldZ(l.entryZ) : siteOf.mouthZ();
                        lv = rpg.world.shadowveil.Danger.level(mx, mz, master ? 1.1 : share);
                        lv = Math.max(kind.minLevel, Math.min(kind.maxLevel, lv));
                    }''')
# the lair's own range follows (it was made from the type's level above)
sub(D, '''                    if (lair == null) { lair = g.monsters.addLair(kind, lairName + " " + (++room), cx, cz, Math.max(kind.minLevel, top - 6), Math.min(kind.maxLevel, top), size); lairs.put(kind, lair); }''',
'''                    if (lair == null) {
                        int lTop = top;
                        if (rpg.world.WorldMap.shadowveil() && siteOf != null) { int[] b = rpg.world.shadowveil.Danger.band(siteOf.mouthX(), siteOf.mouthZ()); lTop = b[1]; }
                        lair = g.monsters.addLair(kind, lairName + " " + (++room), cx, cz, Math.max(kind.minLevel, Math.min(kind.maxLevel, lTop - 8)), Math.max(kind.minLevel, Math.min(kind.maxLevel, lTop)), size);
                        lairs.put(kind, lair);
                    }''')

E = os.path.join(SRC, 'encounter', 'Encounters.java')
sub(E, '''        int level = lv.size() == 2 ? n(lv.get(0)) + r.nextInt(Math.max(1, n(lv.get(1)) - n(lv.get(0)) + 1)) : n(c.getOrDefault("level", 1));
        String tag = str(c, "tag", "?");''',
'''        int level = lv.size() == 2 ? n(lv.get(0)) + r.nextInt(Math.max(1, n(lv.get(1)) - n(lv.get(0)) + 1)) : n(c.getOrDefault("level", 1));
        // (0.4.8, gamefixes: the dangerous further from Loxford) a fighting party's levels are the place's: its place in
        // its data's range carried into the band of the ground it comes in on
        if (lv.size() == 2 && fights(c)) {
            double share = n(lv.get(1)) > n(lv.get(0)) ? (level - n(lv.get(0))) / (double) (n(lv.get(1)) - n(lv.get(0))) : 0.5;
            level = rpg.world.shadowveil.Danger.level(at[0], at[1], share);
        }
        String tag = str(c, "tag", "?");''')
sub(E, '''    /** (0.4.8) Whether an encounter person's present behaviour is a fight the encounter set (attack, hunt, chase). */''',
'''    /** (0.4.8) Whether a cast member is one of the fighting kind (a monster, a predator, an armed role): its level follows the place's danger. */
    private static boolean fights(Map<String, Object> c) {
        String kind = str(c, "kind", "person");
        if (kind.equals("monster")) return true;
        if (kind.equals("animal")) { try { return Species.valueOf(str(c, "species", "DEER")).diet == Species.Diet.HUNTER; } catch (IllegalArgumentException e) { return false; } }
        String role = str(c, "role", "VILLAGER");
        return role.equals("BANDIT") || role.equals("BRUTE") || role.equals("THIEF") || role.equals("HUNTER") || role.equals("KNIGHT") || role.equals("GUARD");
    }
    /** (0.4.8) Whether an encounter person's present behaviour is a fight the encounter set (attack, hunt, chase). */''')
print('danger applied')
