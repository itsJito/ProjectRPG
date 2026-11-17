import os
HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, '..', '..', 'rpggame', 'src', 'main', 'java', 'rpg')
def sub(p, old, new):
    s = open(p, encoding='utf-8').read()
    if old not in s: raise SystemExit('MISSING in ' + p + ': ' + old[:80])
    open(p, 'w', encoding='utf-8', newline='').write(s.replace(old, new, 1))
hf = os.path.join(SRC, 'ui', 'HudFragments.java')
s = open(hf, encoding='utf-8').read()
a = s.index('    /** The full playable area, sharing the minimap\'s edited-column texture and landmark data. */')
b = s.index('    /** What each map icon stands for, in the order a traveller needs them; only the ones on the chart are listed. */')
s = s[:a] + open(os.path.join(HERE, 'worldmap_new.java'), encoding='utf-8').read() + '\n' + s[b:]
# the old right-hand legend served only the old chart
a = s.index("    /** The world map's right-hand panel: where one stands, the key to the chart and how to use it, laid out to fit. */")
b = s.index('    // ------------------------------------------------------------------ minimap: top right')
s = s[:a] + s[b:]
# the minimap: the active quest starred (held to the rim when off it)
old = '            if (hud.flag != null) dot(ui, hud.flag[0], hud.flag[1], cx, cy, 0xFFFF2020, 4);'
assert old in s
s = s.replace(old, old + '''
            // (0.4.8, gamefixes: the journal's "Set Active") the active quest's destination: a gold star, on the rim when off the chart
            var aq = g.questLog == null ? null : g.questLog.activeEntry();
            if (aq != null && aq.destination() != null) {
                boolean on = toMap(aq.destination()[0], aq.destination()[1], cx, cy);
                if (!on) rim(aq.destination()[0], aq.destination()[1], cx, cy);
                ui.disc(p2[0], p2[1], 7, 0xC0141008);
                Panels.Journal.star(ui, p2[0], p2[1], on ? 6 : 5, 0xFFFFD15E);
            }''')
open(hf, 'w', encoding='utf-8', newline='').write(s)
ql = os.path.join(SRC, 'quest', 'QuestLog.java')
sub(ql, '    public Sort sort = Sort.STATUS;', '''    public Sort sort = Sort.STATUS;
    /** (0.4.8) The player's own markers on the world map: {x, z, number} each, and the next number. */
    public final List<float[]> mapMarks = new ArrayList<>();
    public int nextMark;''')
sub(ql, '''        m.put("active", active); m.put("showUnstarted", showUnstarted); m.put("showUnmet", showUnmet); m.put("sort", sort.name());''',
    '''        m.put("active", active); m.put("showUnstarted", showUnstarted); m.put("showUnmet", showUnmet); m.put("sort", sort.name());
        List<Object> marks = new ArrayList<>();
        for (float[] mk : mapMarks) marks.add(List.of((double) mk[0], (double) mk[1], (double) mk[2]));
        m.put("marks", marks); m.put("nextMark", nextMark);''')
sub(ql, '''        try { sort = Sort.valueOf(astratto.util.Json.str(m, "sort", "STATUS")); } catch (IllegalArgumentException e) { sort = Sort.STATUS; }''',
    '''        try { sort = Sort.valueOf(astratto.util.Json.str(m, "sort", "STATUS")); } catch (IllegalArgumentException e) { sort = Sort.STATUS; }
        mapMarks.clear();
        List<Object> marks = astratto.util.Json.list(m, "marks");
        if (marks != null) for (Object o : marks) if (o instanceof List<?> l && l.size() == 3) mapMarks.add(new float[]{((Number) l.get(0)).floatValue(), ((Number) l.get(1)).floatValue(), ((Number) l.get(2)).floatValue()});
        nextMark = (int) astratto.util.Json.num(m, "nextMark", mapMarks.size());''')
print('patched')
