import json, collections, sys
r = json.load(open('rpggame/build/reports/shadowveil/registry.json'))['structures']
q = [s for s in r if 'interiorQuality' in s]
miss = [s['id'] for s in r if s['enterable'] and not s['underground'] and 'interiorQuality' not in s]
print('fitted', len(q), 'enterable without fit', miss)
sc = [s['interiorQuality']['score'] for s in q]
print('score min', min(sc), 'mean', sum(sc) / len(sc), 'below80', sum(1 for x in sc if x < 80))
hist = collections.Counter((x // 10) * 10 for x in sc)
print(sorted(hist.items()))
parts = collections.defaultdict(list)
for s in q:
    for k, v in s['interiorQuality']['rubric'].items(): parts[k].append(v)
for k, v in parts.items(): print(k, 'mean', round(sum(v) / len(v), 2), 'min', min(v))
d = collections.Counter()
for s in q:
    for x in s['interiorQuality']['defects']:
        import re
        d[re.sub(r'\d+', 'N', x)] += 1
for k, v in d.most_common(40): print(v, k)
by = collections.defaultdict(list)
for s in q: by[s['purpose']].append(s['interiorQuality']['score'])
for k, v in sorted(by.items()): print(k, len(v), 'min', min(v), 'mean', round(sum(v) / len(v), 1))
if len(sys.argv) > 1:
    for s in q:
        if s['interiorQuality']['score'] < int(sys.argv[1]): print(s['id'], s['purpose'], s['size'], s['interiorQuality']['score'], s['interiorQuality']['rubric'], s['interiorQuality']['defects'][:4])
