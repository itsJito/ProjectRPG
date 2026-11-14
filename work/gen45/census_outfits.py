import os, json
os.chdir(r'C:\Users\John\Documents\ProjectRPG\rpggame\src\main')
p = 'resources/data/shadowveil_census.json'
d = json.load(open(p, encoding='utf-8'))
c = d['census']
c['outfits'] = {
    "_about": "(0.4.5, gamefixes: the clothing update; everyone but the townsfolk in gear that can be seen and looted) What each person wears of the outfits (data/apparel.json), the first that applies: by name, by where they live, by calling, by role, then by kingdom (by title words, by role, else its default) for those of the kingdom not in arms. A slot the kit already filled (a guard's helm, a knight's plate) keeps what the kit put there; where an outfit has two pieces for one slot (the Desert's legs and robes), the rank decides ('robesFor').",
    "people": {"Brannoc": "rogue"},
    "byHome": {"garmunds_camp": "pigskin"},
    "byProfession": {"FIREWOOD_CUTTER": "lumberjack", "ORE_MINER": "prospector", "GEM_PROSPECTOR": "prospector", "COAL_MINER": "prospector", "QUARRY_WORKER": "prospector", "CLAY_DIGGER": "prospector", "CROP_FARMER": "farmer", "ORCHARD_KEEPER": "farmer"},
    "byRole": {"MINER": "prospector", "FARMER": "farmer", "THIEF": "rogue", "BANDIT": "pigskin", "BRUTE": "pigskin"},
    "byKingdom": {"Azharad": {"byTitle": {"king": "desert_royal", "queen": "desert_royal", "duke": "desert_royal", "count": "desert_royal", "baron": "desert_noble"}, "byRole": {"CIVIC": "desert_noble", "MERCHANT": "desert_noble", "SCHOLAR": "desert_noble", "PRIEST": "desert_noble"}, "_default": "desert_commoner"}},
    "notInArms": ["GUARD", "KNIGHT"],
    "robesFor": ["desert_royal", "desert_noble"],
}
# the armed kits complete: guards, knights and hunters wear all of what they fight in (it can be seen and looted)
k = c['kits']
k['GUARD']['wear'] = ["iron_sword", "iron_shield", "iron_body", "iron_helm", "iron_legs", "leather_boots", "leather_gloves"]
k['KNIGHT']['wear'] = ["steel_sword", "steel_shield", "steel_body", "steel_helm", "steel_legs", "leather_boots", "leather_gloves"]
k['BANDIT']['wear'] = ["bronze_sword", "wooden_shield"]
k['HUNTER']['wear'] = ["shortbow", "leather_boots", "leather_gloves"]
# ten more people, all of the wilds' own places (no new houses or beds needed in the towns)
new = [
    {"name": "Rask", "title": "Bandit", "kingdom": "Wilds", "role": "BANDIT", "female": False, "home": "garmunds_camp", "work": "garmunds_camp"},
    {"name": "Wenna", "title": "Bandit", "kingdom": "Wilds", "role": "BANDIT", "female": True, "home": "garmunds_camp", "work": "garmunds_camp"},
    {"name": "Hullen", "title": "Bandit", "kingdom": "Wilds", "role": "BANDIT", "female": False, "home": "garmunds_camp", "work": "garmunds_camp"},
    {"name": "Grisel", "title": "Bandit", "kingdom": "Wilds", "role": "BANDIT", "female": True, "home": "garmunds_camp", "work": "garmunds_camp"},
    {"name": "Haskel", "title": "Ore Miner", "kingdom": "Valgard", "role": "MINER", "female": False, "home": "sunkissed_bunkhouse", "work": "sunkissed_mine", "profession": "ORE_MINER"},
    {"name": "Tamsin", "title": "Quarry Worker", "kingdom": "Valgard", "role": "MINER", "female": True, "home": "marble_bunkhouse", "work": "marble_yard", "profession": "QUARRY_WORKER"},
    {"name": "Osk", "title": "Gem Prospector", "kingdom": "Wilds", "role": "MINER", "female": False, "home": "highland_bunkhouse", "work": "highland_adit", "profession": "GEM_PROSPECTOR"},
    {"name": "Mabry", "title": "Logger", "kingdom": "Severia", "role": "WORKER", "female": True, "home": "loggers_rest", "work": "loggers_yard", "profession": "FIREWOOD_CUTTER"},
    {"name": "Edvin", "title": "Logger", "kingdom": "Severia", "role": "WORKER", "female": False, "home": "loggers_rest", "work": "loggers_yard", "profession": "FIREWOOD_CUTTER"},
    {"name": "Corra", "title": "Charcoal Burner", "kingdom": "Wilds", "role": "WORKER", "female": True, "home": "clamp_hut", "work": "charcoal_clamps", "profession": "FIREWOOD_CUTTER"},
]
names = {x['name'] for x in c['people'] + c['additions']}
for x in new:
    assert x['name'] not in names, x['name']
    c['additions'].append(x)
c['_additions'] = c['_additions'] + " (0.4.5, gamefixes: 225 people) Ten more of the wilds: four of Garmund's band at his camp, three prospectors in the mines' bunkhouses, two loggers at the Loggers' Rest and a charcoal burner at the clamps."
json.dump(d, open(p, 'w', encoding='utf-8'), indent=1, ensure_ascii=False)
print(len(c['people']) + len(c['additions']), 'people')
