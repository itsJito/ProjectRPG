import os
ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', 'rpggame', 'src', 'main', 'java', 'rpg', 'ui')
p = os.path.join(ROOT, 'Panels.java')
s = open(p, encoding='utf-8').read()
new = open(os.path.join(os.path.dirname(os.path.abspath(__file__)), 'journal_new.java'), encoding='utf-8').read()
a = s.index('    static final class Journal extends Panel {')
b = s.index('    static final class Legend extends Panel {')
c = s.index('    // ------------------------------------------------------------------ magic: the spellbook')
assert a < b < c
s = s[:a] + new + s[c:]
s = s.replace('        hud.add(new Legend(hud));\n', '')
open(p, 'w', encoding='utf-8', newline='').write(s)
h = os.path.join(ROOT, 'Hud.java')
t = open(h, encoding='utf-8').read()
old = 'group("journal", "journal", inFrame("journal", 16, 52), "legend", inFrame("legend", 552, 52));'
assert old in t
t = t.replace(old, 'group("journal", "journal", inFrame("journal", 16, 52));')
open(h, 'w', encoding='utf-8', newline='').write(t)
print("ok")
