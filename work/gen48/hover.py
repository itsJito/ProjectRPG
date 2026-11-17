import os
p = os.path.join('src','main','java','rpg','monster','MonsterMind.java')
s = open(p, encoding='utf-8').read()
old = '''    void act(Monster m, float dt) {
        if(m.observedFire!=null) {'''
new = '''    void act(Monster m, float dt) {
        // (0.4.8, gamefixes: the greater demon's wings flap and it hovers from time to time, mostly grounded) now and then -
        // more often closing on a foe across open ground - it beats its wings and lifts off the ground for a few seconds
        if (m.kind == MonsterKind.GREATER_DEMON && m.alive && g.time >= m.nextHover && m.hoverUntil < g.time) {
            Actor foe = m.state == State.ENGAGE ? g.actor(m.target) : null;
            boolean closing = foe != null && m.distTo(foe) > 5;
            if (closing || sys.rnd(m) < 0.25f) {
                m.hoverFrom = g.time; m.hoverUntil = g.time + 2.6 + sys.rnd(m) * 2.2;
                sys.sound(m, "swish", 0.8f);
            }
            m.nextHover = g.time + (closing ? 9 : 18) + sys.rnd(m) * 14;
        }
        if(m.observedFire!=null) {'''
assert old in s
s = s.replace(old, new, 1)
open(p, 'w', encoding='utf-8', newline='').write(s)
q = os.path.join('src','main','java','rpg','monster','Monster.java')
t = open(q, encoding='utf-8').read()
old = '    public double hoverUntil, hoverFrom;'
assert old in t
t = t.replace(old, '    public double hoverUntil, hoverFrom, nextHover;')
open(q, 'w', encoding='utf-8', newline='').write(t)
print('hover')
