import json, re, math, sys
from collections import Counter
import os
ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', '..')).replace(os.sep, '/') + '/'
lines = open(ROOT + 'projectrpg.txt', encoding='utf-8').read().split('\n')
reg = json.load(open(ROOT + 'rpggame/build/reports/shadowveil/registry.json', encoding='utf-8'))['structures']
byid = {s['id']: s for s in reg}
sections = [('Loxford', 89, 124), ('Azharad', 152, 189), ('Severia', 221, 275), ('Valgard', 301, 320), ('Gorrick', 339, 353),
            ('Harlette', 372, 385), ('Camelot', 404, 419), ('Wilds', 441, 461)]
HON = ['King', 'Duke', 'Earl', 'Sir', 'Father', 'Sultan', 'Bey', 'Agha', 'Emperor', 'Archduke', 'Count', 'Imperial Knight', 'Queen', 'Chieftain']


def mono(n):
    n = re.sub(r'\(.*?\)', '', n).strip()
    for h in sorted(HON, key=len, reverse=True):
        if n.startswith(h + ' '):
            n = n[len(h) + 1:]
    return n.split(' ')[0]


CALL = [(r'Household Baron', 'CIVIC', 'HOUSEHOLD_BARON'), (r'King|Duke|Count|Baron|Chancellor', 'CIVIC', None),
        (r'Order Knight', 'KNIGHT', 'ORDER_KNIGHT'), (r'Border Knight', 'KNIGHT', 'BORDER_KNIGHT'), (r'Escort Knight', 'KNIGHT', 'ESCORT_KNIGHT'),
        (r'Knight Errant', 'KNIGHT', 'KNIGHT_ERRANT'), (r'Household Knight', 'KNIGHT', 'HOUSEHOLD_KNIGHT'),
        (r'Priest|Faith Instructor|Shrine Keeper', 'PRIEST', None),
        (r'Banker', 'CIVIC', None), (r'Clerk|Curator', 'SCHOLAR', None),
        (r'Innkeeper', 'CIVIC', 'INNKEEPER'), (r'Bartender', 'MERCHANT', 'BARTENDER'), (r'Cook', 'CRAFTSMAN', 'COOK'),
        (r'Blacksmith', 'CRAFTSMAN', 'BLACKSMITH'), (r'Armorer', 'CRAFTSMAN', 'ARMORER'), (r'Tailor|Cobbler|Leather|Tanner', 'CRAFTSMAN', 'TAILOR'),
        (r'Carpenter|Builder|Repair Worker|Road Worker', 'CRAFTSMAN', 'CARPENTER'),
        (r'General Store|Food Merchant|Weapons Merchant|Traveling Merchant', 'MERCHANT', None),
        (r'Courier|Caravan|Wagon|Supply Runner', 'CARTER', None), (r'Porter|Dockworker', 'WORKER', 'PORTER'),
        (r'Vineyard|Herbal|Crop Farmer', 'FARMER', 'CROP_FARMER'), (r'Orchard', 'FARMER', 'ORCHARD_KEEPER'), (r'Livestock', 'FARMER', None),
        (r'Logger|Firewood|Charcoal|Resin', 'WORKER', 'FIREWOOD_CUTTER'), (r'Forester', 'HUNTER', 'GAME_HUNTER'),
        (r'River Fisherman', 'FISHERMAN', 'RIVER_FISHERMAN'), (r'Coastal Fisherman', 'FISHERMAN', 'COASTAL_FISHERMAN'),
        (r'Deep-Water', 'FISHERMAN', 'DEEP_WATER_FISHERMAN'), (r'Shellfish', 'FISHERMAN', 'SHELLFISH_GATHERER'), (r'Lake Net', 'FISHERMAN', 'LAKE_NET_FISHERMAN'),
        (r'Ore Miner', 'MINER', 'ORE_MINER'), (r'Quarry|Excavator', 'MINER', 'QUARRY_WORKER'), (r'Gem', 'MINER', 'GEM_PROSPECTOR'),
        (r'Coal', 'MINER', 'COAL_MINER'), (r'Clay', 'MINER', 'CLAY_DIGGER'),
        (r'Game Hunter', 'HUNTER', 'GAME_HUNTER'), (r'Trapper', 'HUNTER', 'TRAPPER'), (r'Monster Hunter', 'HUNTER', 'MONSTER_HUNTER'),
        (r'Predator', 'HUNTER', 'PREDATOR_HUNTER'), (r'Bounty', 'HUNTER', 'BOUNTY_HUNTER'),
        (r'Guard', 'GUARD', None), (r'Hobo|Displaced|Job Seeker', 'BEGGAR', None), (r'Retiree', 'VILLAGER', None),
        (r'Pickpocket|Burglar', 'THIEF', None), (r'Fence|Smuggler', 'FENCE', None), (r'Highwayman|Bandit', 'BANDIT', None)]
FEMALE = set(('Ealdra Maeve Ysolde Wynn Berthild Layla Zainab Jamila Samira Eudokia Ione Melitta Irene Theodora Xenia Eleni Zoe Damaris Kyra '
              'Melina Phaedra Polyxena Sabeen Maelin Hesta Fenna Aldis Yrsa Runa Bera Nessa Siv Ilva Vesna Mara Sella Mirel Guinevere Cerys '
              'Rowena Sigrun Ysmay').split())
BODY = {'MERCHANT_A': 'Ealdra', 'CRAFTSMAN': 'Fred', 'LAWMAN_A': 'Torren', 'GUARD': 'Aldric', 'BOBWIN': 'Bram', 'WORKER': 'Oswin',
        'WOODCUTTER_CITY': 'Cenric', 'CARPENTER_CITY': 'Dunstan', 'MANSION_WOODCUTTER': 'Leofric', 'PRIEST': 'Gregory', 'UNDERTAKER': 'Wulfric',
        'HEALER': 'Ysolde', 'FARMER': 'Wynn', 'CARTER': 'Ferrick', 'CROP_FARMER_CITY': 'Cuthmar', 'ORCHARD_KEEPER_CITY': 'Osric',
        'INNKEEPER_CITY': 'Berthild', 'BARTENDER_CITY': 'Maddoc', 'COOK_CITY': 'Karlon', 'TELLER': 'Hadwin', 'KNIGHT': 'Andrew',
        'BORDER_KNIGHT': 'Hedric', 'QUEST_GIVER': 'Garvin', 'VILLAGER': 'Tam', 'FISHER_P': 'Wat', 'HUNTER': 'Rafe',
        'EMPEROR': 'Leonidas', 'PRAETOR': 'Photios', 'LEGIONARY_1': 'Leontios', 'MACELLUM': 'Zoe', 'TRADER_PALACE': 'Damaris', 'PRIESTESS': 'Paul',
        'PHILOSOPHER': 'Aristides', 'STONEMASON': 'Polyxena', 'PALACE_BEGGAR': 'Tolis', 'COASTAL_FISHER': 'Yannis', 'GUILD_MARINERS': 'Petros',
        'INNKEEPER_PALACE': 'Melitta', 'BARTENDER_PALACE': 'Basil', 'COOK_PALACE': 'Lazaros', 'CROP_FARMER_PALACE': 'Kyra',
        'ORCHARD_KEEPER_PALACE': 'Marinos', 'CARPENTER_PALACE': 'Alexios',
        'CARDER': 'Layla', 'SPINNER': 'Zainab', 'FULLER': 'Ziad', 'DYER': 'Murad', 'GUILD_ARTISANS': 'Jalal', 'GUILD_PROVISIONERS': 'Karim',
        'BEGGAR': 'Bilal', 'QUARRY_WORKER': 'Dawud', 'LEGIONARY_2': 'Anwar', 'CITIZEN_2': 'Fahad',
        'GUILD_METALLISTS': 'Brennar', 'SMELTER': 'Hadrik', 'ARMORY': 'Fenna', 'MINER_CITY': 'Dorran', 'MINER_PALACE': 'Merek', 'LEGIONARY_4': 'Roder',
        'SCHOLAR': 'Maelin', 'MANSION_KNIGHT': 'Garran', 'MANSION_PORTER': 'Orven',
        'MERCHANT_B': 'Mara', 'LAWMAN_B': 'Lucan', 'COOK': 'Danek', 'MANSION_COOK': 'Sella', 'RIVER_FISHERMAN': 'Vesna', 'GUILD_FARMERS': 'Ilva',
        'WOODCUTTER_TOWN': 'Radek', 'CARPENTER_TOWN': 'Olek', 'BARON': 'Dracula',
        'GUILD_ARCANISTS': 'Soren', 'MEDICUS': 'Eirik', 'LEGIONARY_3': 'Brand', 'CENTURION': 'Torkell', 'MANSION_ORCHARDIST': 'Siv',
        'CARPENTER_WOOD': 'Sten', 'GUILD_FORESTERS': 'Einar', 'TRISTAN': 'Viggo', 'CITIZEN_4': 'Bera', 'CITIZEN_1': 'Yrsa',
        'ARTHUR': 'Arthur', 'SENATOR': 'Guinevere', 'LANCELOT': 'Lancelot', 'GAWAIN': 'Gawain', 'MERLIN': 'Merlin', 'CITIZEN_3': 'Cerys',
        'FISHERMAN': 'Bryn', 'THIEF': 'Brannoc', 'PREDATOR_HUNTER': 'Hakon', 'SHELLFISH_GATHERER': 'Gudrik', 'TRADER_CITY': 'Meriwyn',
        'MINER': 'Brokk', 'PERCIVAL': 'Torvik', 'HUNTRESS': 'Tauron', 'FENCE': 'Skarn', 'CONTACT': 'Kettil',
        'BRUTE': 'Garmund', 'BANDIT_1': 'Hurk', 'BANDIT_2': 'Sigrun'}
KEY = {
    'Loxford': [('Castle', 'loxford_castle'), ('Drunken Duck', 'drunken_duck'), ('Millwood', 'millwood_lodge'), ('Church', 'loxford_church'),
                ('Rectory', 'loxford_church'), ('Stables', 'loxford_stables'), ('Watchtower', 'watch_barracks'), ('Hunting lodge', 'hunting_lodge'),
                ('Mere', 'mere_shack'), ('Sexton', 'sextons_cottage'), ('Cottage by the church', 'garvins_cottage'), ('Farm hall', 'osric_hall'),
                ('Manor', 'earl_manor'), ('Farmhouse', 'cuthmar_farmhouse'), ("Fred's", 'freds_axes'), ('General Store', 'loxford_store'),
                ('Cottage', 'tams_cottage')],
    'Azharad': [('garrison', 'azharad_palace'), ('Palace', 'azharad_palace'), ('Temple', 'azharad_temple'), ('Bank', 'azharad_bank'),
                ('Zafir', 'zafirs_swords'), ('Osama', 'osamas_armour'), ('his armour shop [59', 'osamas_armour'), ('his armour shop [60', 'marwans_armour'),
                ('tannery', 'azharad_tannery'), ('Karim', 'karims_kitchen'), ('Mohamed', 'mohameds_crafting'), ('general store', 'azharad_store'),
                ('Khan', 'workers_khan'), ('Clay', 'clay_hut'), ('Oasis', 'oasis_cottage'), ('Workshop manor', 'azharad_workshop'),
                ('souq', 'azharad_store')],
    'Severia': [('Palace barracks', 'palace_barracks'), ('Palace', 'severian_palace'), ('Customs', 'customs_house'), ('Museum', 'severian_museum'),
                ('Church', 'severian_church'), ('Moon shrine', 'market_flats'), ('Beacon', 'harbor_beacon'), ('Bank', 'severian_bank'),
                ('Anvil', 'anvil_building'), ('Red Moon', 'red_moon_inn'), ('square', 'tenement_1'), ('Harbor tenement', 'harbor_tenement'),
                ('harbor', 'net_loft'), ('Great Market', 'market_flats'), ('Terrace', 'vineyard_farmhouse'), ('Tenement', 'tenement_2'),
                ('Dig shed', 'dig_shed'), ('Loggers', 'loggers_rest')],
    'Valgard': [('Keep', 'valgard_keep'), ('White Knight', 'valgard_keep'), ('Temple Knight', 'sanctuary_jaehaerys'), ('Sanctuary', 'sanctuary_jaehaerys'),
                ('Guild master', 'metallists_guild'), ('Guild forge', 'metallists_guild'), ('Founders', 'tovins_workshop'), ('Ore Exchange', 'valgard_bank'),
                ('Sunkissed', 'sunkissed_bunkhouse'), ('Marble', 'marble_bunkhouse'), ('Guild bunkhouse', 'guild_bunkhouse'),
                ('cart shed', 'valgard_homes_0'), ('South Fields farmhouse', 'valgard_homes_1'), ('shop flat', 'fennas_shop'), ('White Hart', 'white_hart')],
    'Gorrick': [('Hall', 'chieftains_hall'), ('Monastery', 'hojimo_monastery'), ('Bank', 'gorrick_bank'), ('Iron Yard', 'iron_yard_forge'),
                ('Last Hearth', 'last_hearth'), ("Workers' Lodge", 'workers_lodge'), ('Allotment', 'allotment_cottage'), ('Postern', 'viggos_cottage')],
    'Harlette': [('Manor gatehouse', 'dracula_manor'), ('Manor', 'dracula_manor'), ('Chapel', 'harlette_chapel'), ('Sexton', 'harlette_sexton'),
                 ('East Fields', 'ilva_farmhouse'), ('South Fields', 'borin_farmhouse'), ('Willow Mill cottage [6,20], separate', 'mill_cottage_2'),
                 ('Willow Mill', 'mill_cottage_1'), ('Brookside', 'brookside_cottage'), ("green's shop", 'harlette_store'), ('Shuttered Lantern', 'shuttered_lantern')],
    'Camelot': [('Keep', 'camelot_keep'), ('Training', 'training_quarters'), ('East Gate', 'camelot_stables'), ('Chapel', 'camelot_chapel'),
                ('Sinclair', 'sinclair_ward'), ('Service Court', 'service_court')],
    'Wilds': [('Culvert', 'culvert_cave'), ('Boathouse', 'smugglers_boathouse'), ('Driftwood', 'driftwood_hut'), ('Camp leader', 'garmunds_camp'),
              ('Garmund', 'garmunds_camp'), ('Wagon bed', 'waystation_bunks'), ('Waystation', 'waystation_bunks'), ('Highland', 'highland_bunkhouse'),
              ('Ranger', 'forester_lodge'), ('Clamp', 'clamp_hut'), ('Tent', 'resin_camp'), ('Snare', 'snare_hut'), ('Pile hut', 'bryns_pile_hut'),
              ('Marches', 'orrins_leanto'), ('Hollow Crags', 'hakons_lodge'), ('No home', None)]}


def centre(s):
    f = s['footprint']
    return (f[0] + f[2]) / 2, (f[1] + f[3]) / 2


def near(sid, pred, maxd):
    h = byid.get(sid)
    if h is None:
        return None
    hx, hz = centre(h)
    best, bd = None, maxd
    for s in reg:
        if not pred(s):
            continue
        x, z = centre(s)
        d = math.hypot(x - hx, z - hz)
        if d < bd:
            bd, best = d, s['id']
    return best


FIELDS = lambda s: s['template'] in ('field', 'garden', 'orchard') or s['id'] in ('vineyard', 'olive_terraces', 'date_grove', 'oasis_garden', 'gorrick_allotments')
MINES = lambda s: (s['template'] == 'mouth' and s['id'] not in ('goblin_hollow', 'spider_nest', 'murk_caves', 'ashmaw_throat', 'culvert_cave')) \
    or s['id'] in ('sandstone_quarry', 'clay_pits', 'fossil_dig', 'marble_yard', 'ore_yard')
WOODS = lambda s: s['id'] in ('millwood_yard', 'loggers_yard', 'gorrick_woodyard', 'charcoal_clamps', 'resin_camp') or s['id'] == 'willow_mill'


def work_for(role, prof, title, home):
    if home is None:
        return None
    if prof in ('CROP_FARMER', 'ORCHARD_KEEPER'):
        return near(home, FIELDS, 160) or home
    if prof == 'FIREWOOD_CUTTER':
        return near(home, WOODS, 220) or home
    if role == 'MINER':
        return near(home, MINES, 300) or home
    if 'Funerary' in title:
        return near(home, lambda s: s['template'] == 'graveyard', 120) or home
    if 'Livestock' in title:
        return near(home, lambda s: s['purpose'] == 'stable' or s['id'] == 'borin_yard', 120) or home
    if prof == 'PORTER' and 'harbor' in home:
        return 'harbor_pier_1'
    if role == 'GUARD' and 'Gate' in title:
        return near(home, lambda s: s['purpose'] in ('gatehouse', 'gate'), 150) or home
    return home


out, seen, problems = [], set(), []
for kingdom, a, b in sections:
    for i in range(a - 1, b):
        l = lines[i].strip()
        m = re.match(r'^(.+?) - (.+?) - (.+)$', l)
        if not m or 'Unnamed' in l:
            continue
        title, full, where = m.group(1).strip(), m.group(2).strip(), m.group(3).strip()
        name = mono(full)
        if name in seen:
            problems.append('duplicate name ' + name + ': ' + l)
        seen.add(name)
        role = prof = None
        for pat, r, p in CALL:
            if re.search(pat, title):
                role, prof = r, p
                break
        if role is None:
            problems.append('no calling: ' + l)
            continue
        if name == 'Garmund':
            role = 'BRUTE'
        home = 'none'
        for k, sid in KEY[kingdom]:
            if k.lower() in where.lower():
                home = sid
                break
        if home == 'none':
            problems.append('no home: ' + l)
            home = None
        if home and home not in byid:
            problems.append('bad home ' + home + ': ' + l)
        e = {'name': name, 'title': title, 'kingdom': kingdom, 'role': role, 'female': name in FEMALE, 'home': home,
             'work': work_for(role, prof, title, home), 'lore': l}
        if prof:
            e['profession'] = prof
        out.append(e)
inv = {v: k for k, v in BODY.items()}
for e in out:
    if e['name'] in inv:
        e['body'] = inv[e['name']]
missing = [v for v in BODY.values() if v not in seen]
for p in problems:
    print(p, file=sys.stderr)
print('people', len(out), 'bodies mapped', sum('body' in e for e in out), 'missing', missing, file=sys.stderr)
print(Counter(e['kingdom'] for e in out), file=sys.stderr)
json.dump(out, open(ROOT + 'rpggame/build/census-people.json', 'w', encoding='utf-8'), indent=1, ensure_ascii=False)
