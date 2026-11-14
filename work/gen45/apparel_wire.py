import os
os.chdir(r'C:\Users\John\Documents\ProjectRPG\rpggame\src\main\java\rpg')
def patch(path, pairs):
    s = open(path, encoding='utf-8').read()
    for old, new in pairs:
        assert old in s, (path, old[:90])
        s = s.replace(old, new, 1)
    open(path, 'w', encoding='utf-8').write(s)

patch('item/ItemDef.java', [
 ('''    public String look;''',
  '''    public String look;
    /**
     * (0.4.5) A garment's look (data/apparel.json via Apparel): the shape it is drawn with and its colours, read by
     * rpg.render.Garments on the body and as the item itself. Null for everything else.
     */
    public java.util.Map<String, Object> wear;
    /** (0.4.5) The outfit a garment belongs to (Apparel's id), or null. */
    public String outfit;
    /** (0.4.5) Every skill level it asks to be worn, where more than one (the Rogue outfit's Agility and Thieving). */
    public java.util.Map<Skill, Integer> reqs = java.util.Map.of();
    /** Every requirement: the extra ones, else the one. */
    public java.util.Map<Skill, Integer> requirements() { return !reqs.isEmpty() ? reqs : reqSkill == null ? java.util.Map.of() : java.util.Map.of(reqSkill, reqLevel); }'''),
])
s = open('item/ItemDef.java', encoding='utf-8').read()
if 'import rpg.skill.Skill;' not in s:
    s = s.replace('package rpg.item;\n', 'package rpg.item;\n\nimport rpg.skill.Skill;\n', 1)
    open('item/ItemDef.java', 'w', encoding='utf-8').write(s)

patch('item/Equipment.java', [
 ('''        if (d.reqSkill != null && skills.level(d.reqSkill) < d.reqLevel)
            return "You need " + d.reqSkill.title + " level " + d.reqLevel + " to equip the " + d.name + ".";
        return null;''',
  '''        // (0.4.5) every requirement it asks (an outfit may ask two skills)
        for (var r : d.requirements().entrySet()) if (skills.level(r.getKey()) < r.getValue())
            return "You need " + r.getKey().title + " level " + r.getValue() + " to equip the " + d.name + ".";
        return null;'''),
])

patch('item/Items.java', [
 ('''    static {
        BY_ID''' if '    static {\n        BY_ID' in open('item/Items.java', encoding='utf-8').read() else '''    public static ItemDef find(String id) { return BY_ID.get(id); }''',
  None),
] if False else [])

s = open('item/Items.java', encoding='utf-8').read()
# register the apparel after every other item is made: a static block at the very end of the class
idx = s.rstrip().rfind('}')
s = s[:idx] + '''
    // (0.4.5, gamefixes: the clothing update) the outfits' pieces, from data/apparel.json
    static { Apparel.load(d -> { if (BY_ID.put(d.id, d) != null) throw new IllegalStateException("duplicate item " + d.id); return d; }); }
}
'''
open('item/Items.java', 'w', encoding='utf-8').write(s)

patch('ui/Hud.java', [
 ('''        if (d.reqSkill != null) b.append("\\nRequires ").append(d.reqSkill.title).append(" ").append(d.reqLevel);''',
  '''        for (var r : d.requirements().entrySet()) b.append("\\nRequires ").append(r.getKey().title).append(" ").append(r.getValue());'''),
])
patch('ui/Windows.java', [
 ('''"\\nRequires " + d.reqSkill.title + " " + d.reqLevel''',
  '''d.requirements().entrySet().stream().map(r -> "\\nRequires " + r.getKey().title + " " + r.getValue()).reduce("", String::concat)'''),
])
print('ok')
