import json
p = 'rpggame/src/main/resources/data/shadowveil_architecture.json'
d = json.load(open(p, encoding='utf-8'))
a = d['architecture']
a['purposes']['jail'] = {"enter": True, "rooms": ["gaoler's office", "cell block", "guard room", "bunk room"], "minArea": 40, "beds": 1}
a['_later'] = ("(0.4.4, gamefixes: each kingdom its own gaol with proper cells, its own bankhouse and its own graveyard) A settlement's "
               "'later' buildings are planned after its districts' houses, on the free ground nearest where they are asked for "
               "(so the streets already laid stay as they are).")
G = lambda i, n, at, size, **kw: dict({"id": i, "name": n, "at": at, "size": size}, **kw)
later = {
    'loxford': [G('loxford_gaol', 'Loxford Gaol', [13.9, 8.7], [11, 13], storeys=2, wallH=4, purpose='jail', chimney=True, search=48)],
    'harlette': [G('harlette_gaol', 'Harlette Lock-up', [9.4, 21.2], [11, 13], storeys=2, wallH=4, purpose='jail', search=48),
                 G('harlette_bank', 'Harlette Bank', [8.1, 20.7], [10, 11], storeys=2, wallH=4, purpose='bank', search=48)],
    'valgard': [G('valgard_gaol', 'Valgard Gaol', [11.6, 26.9], [11, 13], storeys=2, wallH=4, purpose='jail', stone=True, search=48),
                G('valgard_graveyard', 'Valgard Graveyard', [7.4, 30.2], [16, 14], template='graveyard', search=48)],
    'gorrick': [G('gorrick_gaol', 'Gorrick Gaol', [24.0, 41.9], [11, 13], storeys=2, wallH=4, purpose='jail', search=48),
                G('gorrick_graveyard', 'Gorrick Graveyard', [21.9, 44.1], [14, 12], template='graveyard', search=48)],
    'azharad': [G('azharad_gaol', 'Azharad Gaol', [55.0, 10.0], [11, 13], storeys=2, wallH=4, purpose='jail', search=48),
                G('azharad_graveyard', 'Azharad Graveyard', [52.0, 17.2], [16, 14], template='graveyard', search=48)],
    'severia': [G('severia_gaol', 'Severian Carcer', [33.4, 58.1], [11, 13], storeys=2, wallH=4, purpose='jail', style='severia_stone', search=48),
                G('severia_graveyard', 'Severian Necropolis', [25.4, 58.2], [18, 14], template='graveyard', style='severia_stone', search=48)],
    'camelot': [G('camelot_gaol', 'Camelot Gaol', [52.6, 46.1], [11, 13], storeys=2, wallH=4, purpose='jail', search=48),
                G('camelot_bank', 'Camelot Bank', [50.5, 45.7], [10, 11], storeys=2, wallH=4, purpose='bank', search=48),
                G('camelot_graveyard', 'Camelot Graveyard', [49.0, 45.8], [12, 12], template='graveyard', search=48)],
}
for s in a['settlements']:
    if s['id'] in later: s['later'] = later[s['id']]
json.dump(d, open(p, 'w', encoding='utf-8'), indent=1, ensure_ascii=False)

p = 'rpggame/src/main/resources/data/shadowveil_census.json'
d = json.load(open(p, encoding='utf-8'))
c = d['census']
names = {x['name'] for x in c['people']}
for x in c['people']:
    if x['name'] == 'Torren' and x['title'] == 'Prison Guard': x['work'] = 'loxford_gaol'
    if x['name'] == 'Gorgias' and x['title'] == 'Prison Guard': x['work'] = 'severia_gaol'
c['_additions'] = ("(0.4.4, gamefixes: ten more people across the map) The people who keep the new gaols, banks and graveyards, after the lore's "
                   "own roll (kept apart so the roll's generator never drops them): each made for ShadowVeil alone, numbered after the roll.")
add = [
    {"name": "Bogdan", "title": "Prison Guard", "kingdom": "Harlette", "role": "GUARD", "female": False, "home": "harlette_gaol", "work": "harlette_gaol"},
    {"name": "Milena", "title": "Banker", "kingdom": "Harlette", "role": "CIVIC", "female": True, "home": "harlette_bank", "work": "harlette_bank"},
    {"name": "Lothar", "title": "Prison Guard", "kingdom": "Valgard", "role": "GUARD", "female": False, "home": "valgard_gaol", "work": "valgard_gaol"},
    {"name": "Hilde", "title": "Gravedigger", "kingdom": "Valgard", "role": "UNDERTAKER", "female": True, "home": "sanctuary_jaehaerys", "work": "valgard_graveyard"},
    {"name": "Gunnar", "title": "Prison Guard", "kingdom": "Gorrick", "role": "GUARD", "female": False, "home": "gorrick_gaol", "work": "gorrick_gaol"},
    {"name": "Astrid", "title": "Gravedigger", "kingdom": "Gorrick", "role": "UNDERTAKER", "female": True, "home": "hojimo_monastery", "work": "gorrick_graveyard"},
    {"name": "Nadim", "title": "Prison Guard", "kingdom": "Azharad", "role": "GUARD", "female": False, "home": "azharad_gaol", "work": "azharad_gaol"},
    {"name": "Samir", "title": "Gravedigger", "kingdom": "Azharad", "role": "UNDERTAKER", "female": False, "home": "azharad_temple", "work": "azharad_graveyard"},
    {"name": "Emrys", "title": "Prison Guard", "kingdom": "Camelot", "role": "GUARD", "female": False, "home": "camelot_gaol", "work": "camelot_gaol"},
    {"name": "Rhiannon", "title": "Banker", "kingdom": "Camelot", "role": "CIVIC", "female": True, "home": "camelot_bank", "work": "camelot_bank"},
]
for x in add: assert x['name'] not in names, x['name']
c['additions'] = add
json.dump(d, open(p, 'w', encoding='utf-8'), indent=1, ensure_ascii=False)
print('ok')
