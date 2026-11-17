import json, re
p = 'src/main/resources/data/shadowveil.json'
s = open(p, encoding='utf-8').read()
d = json.loads(s)
mon = d['monsters']
adds = {
 'highland': [["ICE_TROLL", 55, 70, 1, 10]],
 'crags': [["ICE_TROLL", 58, 72, 1, 8]],
 'marsh': [["RIVER_TROLL", 35, 50, 1, 15]],
 'lakeshore': [["RIVER_TROLL", 35, 50, 1, 14]],
 'willowmeadow': [["RIVER_TROLL", 35, 48, 1, 8]],
 'marches': [["RIVER_TROLL", 35, 48, 1, 6]],
 'desert': [["LARGE_SCORPION", 26, 40, 1, 15], ["SMALL_SCORPION", 3, 15, 3, 45]],
 'dunes': [["LARGE_SCORPION", 26, 42, 1, 12], ["SMALL_SCORPION", 3, 15, 3, 40]],
 'sandstone': [["SMALL_SCORPION", 3, 14, 3, 30]],
 'volcanic': [["GREATER_DEMON", 70, 90, 1, 8], ["LESSER_DEMON", 30, 45, 2, 30]],
 'cinders': [["LESSER_DEMON", 30, 45, 2, 22]],
 'scorched': [["GREATER_DEMON", 70, 85, 1, 6], ["LESSER_DEMON", 30, 45, 2, 18]],
}
for b, rows in adds.items():
    mon['biomes'][b] = rows + mon['biomes'].get(b, [])
mon['names'].update({"ICE_TROLL": "Ice troll's haunt", "RIVER_TROLL": "River troll's haunt", "SMALL_SCORPION": "Scorpion burrows", "LARGE_SCORPION": "Giant scorpion's burrow", "LESSER_DEMON": "Demon pit", "GREATER_DEMON": "Greater demon's pit"})
mon['most'] = 56
# write back only the monsters block, keeping the rest of the file's text as it is
start = s.index('\n "monsters": {')
depth = 0; i = s.index('{', start)
for j in range(i, len(s)):
    if s[j] == '{': depth += 1
    elif s[j] == '}':
        depth -= 1
        if depth == 0: end = j + 1; break
block = json.dumps(mon, indent=1, ensure_ascii=False)
block = '\n'.join((' ' + l if k else l) for k, l in enumerate(block.split('\n')))
s = s[:start] + '\n "monsters": ' + block + s[end:]
json.loads(s)
open(p, 'w', encoding='utf-8', newline='').write(s)
q = 'src/main/resources/data/dungeons.json'
t = open(q, encoding='utf-8').read()
old = '"ashmaw_throat":     {"kind": "lair",    "theme": "CAVE",   "levels": 3, "creatures": ["IMP"]}'
if old not in t:
    m = re.search(r'"ashmaw_throat":\s*\{[^}]*\}', t); print('found', m.group(0)); old = m.group(0)
t = t.replace(old, old.replace('"creatures": ["IMP"]', '"creatures": ["IMP", "LESSER_DEMON", "LESSER_DEMON", "GREATER_DEMON"]'))
json.loads(t)
open(q, 'w', encoding='utf-8', newline='').write(t)
print('spawns')
