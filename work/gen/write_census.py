import json
ROOT = 'C:/Users/John/Documents/ProjectRPG/'
people = json.load(open(ROOT + 'work/gen/people.json', encoding='utf-8'))
for p in people:
    p.pop('lore', None)
census = {
    "_about": "(0.4.3, gamefixes: every NPC of the lore registered, spawned in their kingdom, with their documented occupation, home, workplace and bed) ShadowVeil's people as projectrpg.txt lists them under 'NPCs Present', one entry each: a one-word name (titles in 'title'), their kingdom, the Npc role and profession that drive their behaviour, the registry structure that is their home and the one that is their workplace (null home: the one exception, the bounty hunter). An entry with a 'body' is played by the roster person of that Ids constant (renamed and re-dressed on ShadowVeil; the devmap keeps its own people); every other entry is made for ShadowVeil alone, numbered from Ids.SV_PEOPLE in this order. Until the interiors come (0.4.4) each person keeps exterior stand-ins: a home stage and a work stage on open ground before their buildings' doors, where they arrive home, wait at work and go in for the night; their bed is planned inside their home's reserved interior and kept in the registry.",
    "stage": {"_about": "How far before a doorway its stand-ins lie (cells), how far apart people sharing one stand side by side, and when the townsfolk turn in and come out (hours).", "out": 2.0, "spacing": 1.4, "bedHour": 22, "wakeHour": 6},
    "kits": {
        "_about": "What a person made for ShadowVeil carries and how able they are, by role (then by profession where it says so): skill levels, worn kit, and items in the pack.",
        "GUARD": {"levels": {"ATTACK": 30, "STRENGTH": 30, "DEFENSE": 30, "HEALTH": 30}, "wear": ["iron_sword", "iron_shield", "iron_body", "iron_helm"], "give": {"bread": 2, "coins": 40}},
        "KNIGHT": {"levels": {"ATTACK": 40, "STRENGTH": 40, "DEFENSE": 42, "HEALTH": 40}, "wear": ["steel_sword", "steel_shield", "steel_body"], "give": {"coins": 200}},
        "BANDIT": {"levels": {"ATTACK": 12, "STRENGTH": 12, "DEFENSE": 10, "HEALTH": 14}, "wear": ["bronze_sword", "wooden_shield", "stone_body"], "give": {"coins": 12}},
        "HUNTER": {"levels": {"ARCHERY": 25, "HARVESTING": 20, "HEALTH": 18}, "wear": ["shortbow"], "ammo": ["bronze_arrows", 30], "give": {"knife": 1, "bread": 2, "coins": 30}},
        "MINER": {"levels": {"MINING": 30}, "give": {"bronze_pickaxe": 1, "bread": 2, "coins": 30}},
        "WORKER": {"levels": {"WOODCUTTING": 30, "STRENGTH": 20}, "give": {"bronze_axe": 1, "bread": 2, "coins": 25}},
        "FISHERMAN": {"levels": {"FISHING": 28}, "give": {"bread": 2, "coins": 25},
                      "byProfession": {"RIVER_FISHERMAN": {"fishing_rod": 1}, "COASTAL_FISHERMAN": {"fishing_rod": 1}, "DEEP_WATER_FISHERMAN": {"harpoon": 1},
                                       "SHELLFISH_GATHERER": {"crab_cage": 1}, "LAKE_NET_FISHERMAN": {"small_fishing_net": 1}}},
        "CRAFTSMAN": {"levels": {"SMITHING": 30, "CONSTRUCTION": 30, "COOKING": 30, "CRAFTING": 30}, "give": {"hammer": 1, "bread": 2, "coins": 60}},
        "FARMER": {"levels": {"HARVESTING": 25}, "give": {"carrot_seeds": 6, "cabbage_seeds": 4, "potato_seeds": 4, "bread": 2, "coins": 40}},
        "MERCHANT": {"give": {"bread": 3, "coins": 300}},
        "CIVIC": {"give": {"coins": 400}},
        "PRIEST": {"levels": {"FAITH": 40}, "give": {"bread": 2, "coins": 30}},
        "SCHOLAR": {"levels": {"MAGIC": 30}, "give": {"coins": 60}},
        "THIEF": {"levels": {"THIEVING": 30, "AGILITY": 25}, "give": {"lockpick": 1, "coins": 20}},
        "FENCE": {"levels": {"THIEVING": 35}, "give": {"coins": 600}},
        "_default": {"give": {"bread": 2, "coins": 20}}
    },
    "stations": {"_about": "The workstations each calling needs at its workplace (Recipes.Station names), set out in the open before the workplace until its interior comes - one of each kind per workplace, shared by everyone who works there.",
                 "BLACKSMITH": ["ANVIL", "FURNACE"], "ARMORER": ["ANVIL", "FURNACE"], "SMELTER": ["FURNACE"], "CARPENTER": ["SAWBENCH", "CRAFT_TABLE"], "TAILOR": ["CRAFT_TABLE"],
                 "COOK": ["RANGE"], "CARDER": ["CARDING_BENCH"], "SPINNER": ["SPINNING_WHEEL"], "WEAVER": ["LOOM"], "FULLER": ["FULLING_TUB"], "DYER": ["DYE_VAT"]},
    "people": people
}
try:
    prev = json.load(open(ROOT + 'rpggame/src/main/resources/data/shadowveil_census.json', encoding='utf-8'))['census']
    for k, v in prev.items():
        census.setdefault(k, v)
except FileNotFoundError:
    pass
json.dump({"census": census}, open(ROOT + 'rpggame/src/main/resources/data/shadowveil_census.json', 'w', encoding='utf-8'), indent=1, ensure_ascii=False)
print(len(people))
