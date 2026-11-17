import os, json
HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, '..', '..', 'rpggame', 'src', 'main', 'java', 'rpg')
RES = os.path.join(HERE, '..', '..', 'rpggame', 'src', 'main', 'resources', 'data')
def sub(rel, old, new):
    p = os.path.join(SRC, rel)
    s = open(p, encoding='utf-8').read()
    if old not in s: raise SystemExit('MISSING in ' + rel + ': ' + old[:90])
    open(p, 'w', encoding='utf-8', newline='').write(s.replace(old, new, 1))
sub('objects/Furniture.java', '''        SCONCE("Wall sconce", Blocks.AIR);''', '''        SCONCE("Wall sconce", Blocks.AIR),
        // (0.4.8, gamefixes: fifteen new interior models, fully destructible) the cellar's and the storeroom's, the study's
        // and the library's, the nursery's and the great house's
        BARREL("Barrel", Blocks.TABLE), KEG("Keg", Blocks.TABLE), LECTERN("Lectern", Blocks.TABLE), SPINNING_WHEEL("Spinning wheel", Blocks.TABLE),
        SCROLL_RACK("Scroll rack", Blocks.TABLE), THRONE("Throne", Blocks.TABLE), CRADLE("Cradle", Blocks.TABLE), COAT_RACK("Coat rack", Blocks.TABLE),
        BUTTER_CHURN("Butter churn", Blocks.TABLE), GLOBE("Globe", Blocks.TABLE), TOOL_RACK("Tool rack", Blocks.AIR), MAP_TABLE("Map table", Blocks.TABLE),
        DISPLAY_CABINET("Display cabinet", Blocks.TABLE), FOLDING_SCREEN("Folding screen", Blocks.TABLE), SACKS("Sacks", Blocks.TABLE);''')
sub('render/Props.java', '''            case SCONCE -> Furnishings.sconce();
        };''', '''            case SCONCE -> Furnishings.sconce();
            case BARREL -> Furnishings.barrel(seed);
            case KEG -> Furnishings.keg(seed);
            case LECTERN -> Furnishings.lectern(seed);
            case SPINNING_WHEEL -> Furnishings.spinningWheel(seed);
            case SCROLL_RACK -> Furnishings.scrollRack(w, h, seed);
            case THRONE -> Furnishings.throne(seed);
            case CRADLE -> Furnishings.cradle(seed);
            case COAT_RACK -> Furnishings.coatRack(seed);
            case BUTTER_CHURN -> Furnishings.butterChurn(seed);
            case GLOBE -> Furnishings.globe(seed);
            case TOOL_RACK -> Furnishings.toolRack(w, seed);
            case MAP_TABLE -> Furnishings.mapTable(w, d, seed);
            case DISPLAY_CABINET -> Furnishings.displayCabinet(w, h, seed);
            case FOLDING_SCREEN -> Furnishings.foldingScreen(w, seed);
            case SACKS -> Furnishings.sacks(w, seed);
        };''')
p = os.path.join(SRC, 'render', 'Furnishings.java')
s = open(p, encoding='utf-8').read()
i = s.rindex('}')
s = s[:i] + open(os.path.join(HERE, 'furnish_new.java'), encoding='utf-8').read() + '}\n'
open(p, 'w', encoding='utf-8', newline='').write(s)
# where they stand: each room's use given its new pieces
q = os.path.join(RES, 'shadowveil_interiors.json')
d = json.load(open(q, encoding='utf-8'))
uses = d['interiors']['uses']
add = {
 'storeroom': [{"place": "wall", "kind": "BARREL", "count": 2}, {"place": "wall", "kind": "SACKS", "w": 2, "chance": 0.7}],
 'pantry': [{"place": "wall", "kind": "BARREL"}, {"place": "wall", "kind": "BUTTER_CHURN", "chance": 0.6}, {"place": "wall", "kind": "SACKS", "w": 2, "chance": 0.5}],
 'kitchen': [{"place": "wall", "kind": "BARREL", "chance": 0.6}, {"place": "wall", "kind": "BUTTER_CHURN", "chance": 0.4}],
 'common_room': [{"place": "wall", "kind": "KEG", "chance": 0.85}, {"place": "wall", "kind": "BARREL", "chance": 0.6}, {"place": "wall", "kind": "COAT_RACK", "chance": 0.5}],
 'study': [{"place": "wall", "kind": "LECTERN", "chance": 0.6}, {"place": "wall", "kind": "GLOBE", "chance": 0.5}, {"place": "wall", "kind": "SCROLL_RACK", "h": 2, "chance": 0.4}],
 'library': [{"place": "wall", "kind": "LECTERN"}, {"place": "wall", "kind": "SCROLL_RACK", "h": 2, "count": 2}, {"place": "wall", "kind": "GLOBE", "chance": 0.6}],
 'records': [{"place": "wall", "kind": "SCROLL_RACK", "h": 2, "count": 2}, {"place": "wall", "kind": "LECTERN", "chance": 0.4}],
 'throne_room': [{"place": "wall", "kind": "THRONE", "need": True}, {"place": "wall", "kind": "DISPLAY_CABINET", "h": 2, "chance": 0.5}],
 'bedroom': [{"place": "wall", "kind": "CRADLE", "chance": 0.25}, {"place": "wall", "kind": "FOLDING_SCREEN", "w": 2, "chance": 0.3, "wealth": ["rich", "noble", "royal"]}, {"place": "wall", "kind": "COAT_RACK", "chance": 0.35}],
 'hall': [{"place": "wall", "kind": "COAT_RACK", "chance": 0.6}, {"place": "wall", "kind": "SPINNING_WHEEL", "chance": 0.3}],
 'hearth': [{"place": "wall", "kind": "SPINNING_WHEEL", "chance": 0.4}, {"place": "wall", "kind": "BUTTER_CHURN", "chance": 0.3}, {"place": "wall", "kind": "COAT_RACK", "chance": 0.4}],
 'workroom': [{"place": "hang", "kind": "TOOL_RACK", "w": 2}, {"place": "wall", "kind": "BARREL", "chance": 0.5}],
 'guardroom': [{"place": "centre", "kind": "MAP_TABLE", "w": 2, "d": 1, "chance": 0.5}, {"place": "wall", "kind": "BARREL", "chance": 0.5}],
 'office': [{"place": "wall", "kind": "SCROLL_RACK", "h": 2, "chance": 0.6}, {"place": "wall", "kind": "COAT_RACK", "chance": 0.5}],
 'solar': [{"place": "wall", "kind": "DISPLAY_CABINET", "h": 2, "chance": 0.6}, {"place": "wall", "kind": "GLOBE", "chance": 0.4}, {"place": "wall", "kind": "FOLDING_SCREEN", "w": 2, "chance": 0.4}],
 'gallery': [{"place": "wall", "kind": "DISPLAY_CABINET", "h": 2, "count": 2}, {"place": "wall", "kind": "GLOBE", "chance": 0.5}],
 'great_hall': [{"place": "wall", "kind": "DISPLAY_CABINET", "h": 2, "chance": 0.4}, {"place": "wall", "kind": "BARREL", "chance": 0.4}],
 'mill_floor': [{"place": "wall", "kind": "SACKS", "w": 2, "count": 2}, {"place": "wall", "kind": "BARREL", "chance": 0.5}],
 'high_room': [{"place": "wall", "kind": "DISPLAY_CABINET", "h": 2, "chance": 0.5}, {"place": "wall", "kind": "SCROLL_RACK", "h": 2, "chance": 0.4}],
 'dormitory': [{"place": "wall", "kind": "COAT_RACK", "chance": 0.5}],
 'vestry': [{"place": "wall", "kind": "LECTERN", "chance": 0.5}, {"place": "wall", "kind": "COAT_RACK", "chance": 0.5}],
}
for use, pcs in add.items():
    if use in uses: uses[use]['pieces'].extend(pcs)
open(q, 'w', encoding='utf-8', newline='').write(json.dumps(d, indent=1, ensure_ascii=True) + '\n')
# the cellars' dresses take barrels and sacks too
r = os.path.join(RES, 'dungeons.json')
t = open(r, encoding='utf-8').read()
t = t.replace('"stores":     {"wall": ["SHELVES", "CRATE", "CRATE", "WINE_RACK", "SHELVES", "CRATE"]', '"stores":     {"wall": ["BARREL", "SHELVES", "SACKS", "CRATE", "BARREL", "WINE_RACK", "CRATE"]')
t = t.replace('"wine":       {"wall": ["WINE_RACK", "WINE_RACK", "CRATE", "SHELVES", "WINE_RACK"]', '"wine":       {"wall": ["WINE_RACK", "BARREL", "WINE_RACK", "KEG", "BARREL", "SHELVES"]')
t = t.replace('"archive":    {"wall": ["BOOKCASE", "BOOKCASE", "SHELVES", "BUST", "CRATE"]', '"archive":    {"wall": ["SCROLL_RACK", "BOOKCASE", "DISPLAY_CABINET", "BUST", "LECTERN", "BOOKCASE"]')
t = t.replace('"h": {"BOOKCASE": 2, "SHELVES": 2}}', '"h": {"BOOKCASE": 2, "SHELVES": 2, "SCROLL_RACK": 2, "DISPLAY_CABINET": 2}}')
json.loads(t)
open(r, 'w', encoding='utf-8', newline='').write(t)
print('furnish')
