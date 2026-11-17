import os, json
p = os.path.join('src','main','java','rpg','monster','Monsters.java')
s = open(p, encoding='utf-8').read()
old = '''        int made = 0, wet = 0, realm = 0, town0 = 0, road0 = 0, biome0 = 0;'''
new = '''        // (0.4.8, gamefixes: monsters spawned too close to people at their day's work) everyone's places too - where they
        // live, where they work, where they start their day - with the berth the data gives people ("people")
        float people = (float) rpg.world.shadowveil.Atlas.d(clear, "people", 60);
        for (rpg.ai.Npc n : g.npcs) {
            settled.add(new float[]{n.postX, n.postZ, people});
            if (Math.hypot(n.homeX - n.postX, n.homeZ - n.postZ) > 8) settled.add(new float[]{n.homeX, n.homeZ, people});
        }
        int made = 0, wet = 0, realm = 0, town0 = 0, road0 = 0, biome0 = 0;'''
assert old in s
s = s.replace(old, new, 1)
open(p, 'w', encoding='utf-8', newline='').write(s)
q = os.path.join('src','main','resources','data','shadowveil.json')
t = open(q, encoding='utf-8').read()
old = '"clearance": {"town": 110, "homestead": 55, "road": 10}'
assert old in t
t = t.replace(old, '"clearance": {"town": 110, "homestead": 55, "road": 22, "people": 60}')
json.loads(t)
open(q, 'w', encoding='utf-8', newline='').write(t)
print('ok')
