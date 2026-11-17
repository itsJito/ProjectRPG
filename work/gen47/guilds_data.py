import json, collections
OD = collections.OrderedDict
pa = 'src/main/resources/data/shadowveil_architecture.json'
A = json.load(open(pa, encoding='utf-8'), object_pairs_hook=OD)
a = A['architecture']

# the guild hall purpose: a hall with the guild's counter, a workroom, the master's rooms over it
a['purposes']['guildhall'] = OD([("enter", True), ("rooms", ["guild hall with counter", "workroom", "master's chamber"]), ("minArea", 40), ("beds", 1), ("perResident", True)])
rules = a['purposeRules']
if not any(r.get('purpose') == 'guildhall' for r in rules):
    rules.insert(0, OD([("name", "Guild Hall|Guild Chapter|Moon Chapter"), ("purpose", "guildhall")]))
if not any(r.get('name') == 'Bank' and r.get('purpose') == 'bank' for r in rules[:1]):
    pass

halls = {
    'loxford': [OD([("id", "foresters_guild"), ("name", "Foresters' Guild Hall"), ("template", "building"), ("beside", "millwood_lodge"), ("at", [19.3, 11.0]), ("size", [14, 11]), ("search", 40), ("nearRoad", 14), ("laneReach", 120), ("guild", "FORESTERS"), ("seat", "hq")]),
                OD([("id", "loxford_bank"), ("name", "Loxford Bank"), ("template", "building"), ("at", [16.2, 8.6]), ("size", [10, 11]), ("search", 64), ("nearRoad", 14), ("laneReach", 120)])],
    'harlette': [OD([("id", "farmers_guild"), ("name", "Farmers' Guild Hall"), ("template", "building"), ("at", [8.4, 21.6]), ("size", [14, 11]), ("search", 64), ("nearRoad", 14), ("laneReach", 120), ("guild", "FARMERS"), ("seat", "hq")])],
    'gorrick': [OD([("id", "provisioners_chapter"), ("name", "Provisioners' Guild Chapter"), ("template", "building"), ("at", [24.3, 42.0]), ("size", [12, 10]), ("search", 64), ("nearRoad", 14), ("laneReach", 120), ("guild", "PROVISIONERS"), ("seat", "chapter")])],
    'azharad': [OD([("id", "artisans_guild"), ("name", "Artisans' Guild Hall"), ("template", "building"), ("beside", "azharad_workshop"), ("at", [53.0, 12.55]), ("size", [14, 12]), ("search", 48), ("nearRoad", 14), ("laneReach", 120), ("guild", "ARTISANS"), ("seat", "hq")]),
                OD([("id", "arcanists_moon_chapter"), ("name", "Arcanists' Moon Chapter"), ("template", "building"), ("at", [55.0, 15.6]), ("size", [12, 10]), ("search", 64), ("nearRoad", 14), ("laneReach", 120), ("guild", "ARCANISTS"), ("seat", "chapter")])],
    'severia': [OD([("id", "mariners_guild"), ("name", "Mariners' Guild Hall"), ("template", "building"), ("beside", "harbor_tenement"), ("at", [29.9, 65.0]), ("size", [14, 11]), ("search", 48), ("nearRoad", 14), ("laneReach", 120), ("guild", "MARINERS"), ("seat", "hq")]),
                OD([("id", "provisioners_guild"), ("name", "Provisioners' Guild Hall"), ("template", "building"), ("at", [28.0, 57.2]), ("size", [14, 11]), ("search", 64), ("nearRoad", 14), ("laneReach", 120), ("guild", "PROVISIONERS"), ("seat", "hq")]),
                OD([("id", "arcanists_guild"), ("name", "Arcanists' Guild Hall"), ("template", "building"), ("at", [31.5, 57.0]), ("size", [16, 12]), ("search", 64), ("nearRoad", 14), ("laneReach", 120), ("guild", "ARCANISTS"), ("seat", "hq")])],
    'camelot': [OD([("id", "arcanists_chapter"), ("name", "Arcanists' Guild Chapter"), ("template", "building"), ("at", [48.6, 44.4]), ("size", [12, 10]), ("search", 64), ("nearRoad", 14), ("laneReach", 120), ("guild", "ARCANISTS"), ("seat", "chapter")])],
}
for st in a['settlements']:
    for h in halls.get(st['id'], []):
        if not any(b.get('id') == h['id'] for b in st.get('later', []) + st.get('buildings', [])):
            st.setdefault('later', []).append(h)
    # the Metallists' hall already stands in Valgard: it is the guild's seat
    for b in st.get('buildings', []):
        if b.get('id') == 'metallists_guild': b['guild'] = 'METALLISTS'; b['seat'] = 'hq'
open(pa, 'w', encoding='utf-8').write(json.dumps(A, indent=1, ensure_ascii=False) + "\n")

# interiors: the guild hall's program and its hall room
pi = 'src/main/resources/data/shadowveil_interiors.json'
I = json.load(open(pi, encoding='utf-8'), object_pairs_hook=OD)
i = I['interiors']
i['programs']['guildhall'] = OD([("wealth", "rich"), ("beds", 1), ("levels", OD([
    ("single", [OD([("use", "guild_hall"), ("share", 3)]), OD([("use", "workroom"), ("share", 2)])]),
    ("ground", [OD([("use", "guild_hall"), ("share", 3)]), OD([("use", "workroom"), ("share", 2)])]),
    ("upper", [OD([("use", "library"), ("share", 1)]), OD([("use", "chamber"), ("share", 1), ("door", "plain")])]),
    ("top", [OD([("use", "chamber")])])]))])
i['uses']['guild_hall'] = OD([("pieces", [
    OD([("place", "counter"), ("kind", "COUNTER"), ("post", "counter"), ("need", True), ("depth", 0.6), ("fallback", OD([("place", "wall"), ("kind", "COUNTER"), ("w", 3), ("post", "counter")]))]),
    OD([("place", "centre"), ("kind", "TABLE"), ("w", 3), ("d", 1), ("seats", "CHAIR")]),
    OD([("place", "wall"), ("kind", "SHELVES"), ("h", 2), ("count", 2)]),
    OD([("place", "wall"), ("kind", "BOOKCASE"), ("h", 2)]),
    OD([("place", "wall"), ("fitting", "chest"), ("tag", "store")]),
    OD([("place", "hang"), ("kind", "TAPESTRY"), ("w", 2), ("count", 2)]),
    OD([("place", "hang"), ("kind", "PAINTING"), ("chance", 0.5)]),
    OD([("place", "wall"), ("kind", "BENCH"), ("w", 2), ("chance", 0.6)]),
    OD([("place", "ceiling"), ("kind", "CHANDELIER"), ("minClear", 5)]),
    OD([("place", "corner"), ("kind", "CANDELABRA")])]), ("noBeds", True)])
open(pi, 'w', encoding='utf-8').write(json.dumps(I, indent=1, ensure_ascii=False) + "\n")

# the census: each hall's master (the devmap's guildmaster bodies now keep the halls), the chapters' heads, Loxford's banker at the bank
pc = 'src/main/resources/data/shadowveil_census.json'
C = json.load(open(pc, encoding='utf-8'), object_pairs_hook=OD)
c = C['census']
everyone = c['people'] + c['additions']
def person(name):
    for p in everyone:
        if p['name'] == name: return p
    return None
for name, body in (('Einar', 'GUILD_FORESTERS'), ('Karim', 'GUILD_PROVISIONERS'), ('Soren', 'GUILD_ARCANISTS')):
    p = person(name)
    if p is not None and p.get('body') == body: del p['body']
for name, work in (('Brennar', 'metallists_guild'), ('Jalal', 'artisans_guild'), ('Petros', 'mariners_guild'), ('Ilva', 'farmers_guild')):
    p = person(name)
    if p is not None: p['work'] = work
hadwin = person('Hadwin')
if hadwin is not None: hadwin['home'] = 'loxford_bank'; hadwin['work'] = 'loxford_bank'
new = [
    ("Aldred", "Guildmaster of the Foresters", "Loxford", "CIVIC", False, "foresters_guild", "foresters_guild", "GUILD_FORESTERS"),
    ("Ioanna", "Guildmistress of the Provisioners", "Severia", "CIVIC", True, "provisioners_guild", "provisioners_guild", "GUILD_PROVISIONERS"),
    ("Kallias", "Guildmaster of the Arcanists", "Severia", "SCHOLAR", False, "arcanists_guild", "arcanists_guild", "GUILD_ARCANISTS"),
    ("Nasira", "Moon Chapter Scholar", "Azharad", "SCHOLAR", True, "arcanists_moon_chapter", "arcanists_moon_chapter", None),
    ("Merrin", "Arcanists' Chapter Head", "Camelot", "SCHOLAR", False, "arcanists_chapter", "arcanists_chapter", None),
    ("Hrodna", "Provisioners' Chapter Head", "Gorrick", "CIVIC", True, "provisioners_chapter", "provisioners_chapter", None),
]
for name, title, kingdom, role, female, home, work, body in new:
    if person(name) is not None: continue
    p = OD([("name", name), ("title", title), ("kingdom", kingdom), ("role", role), ("female", female), ("home", home), ("work", work)])
    if body: p['body'] = body
    c['additions'].append(p)
# where the moved guildmasters go on ShadowVeil (the transfer of the devmap's people)
ps = 'src/main/resources/data/shadowveil.json'
S = json.load(open(ps, encoding='utf-8'), object_pairs_hook=OD)
moves = {'GUILD_FORESTERS': 'foresters_guild', 'GUILD_ARTISANS': 'artisans_guild', 'GUILD_FARMERS': 'farmers_guild', 'GUILD_PROVISIONERS': 'provisioners_guild', 'GUILD_MARINERS': 'mariners_guild', 'GUILD_ARCANISTS': 'arcanists_guild', 'TELLER': 'loxford_bank'}
def walk(o):
    if isinstance(o, dict):
        if o.get('id') in moves and 'to' in o: o['to'] = moves[o['id']]
        for v in o.values(): walk(v)
    elif isinstance(o, list):
        for v in o: walk(v)
walk(S['transfer'])
open(ps, 'w', encoding='utf-8').write(json.dumps(S, indent=1, ensure_ascii=False) + "\n")
open(pc, 'w', encoding='utf-8').write(json.dumps(C, indent=1, ensure_ascii=False) + "\n")
print("ok")
