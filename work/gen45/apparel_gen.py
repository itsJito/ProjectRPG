"""Writes rpggame/src/main/resources/data/apparel.json: the 0.4.5 outfits (gamefixes clothing update)."""
import json, os
OUT = r'C:\Users\John\Documents\ProjectRPG\rpggame\src\main\resources\data\apparel.json'

def piece(id, name, slot, shape, color, value, examine, cells=(2, 2), weight=0.5, armor=0.5, **look):
    l = {"shape": shape, "color": color}
    l.update(look)
    return {"id": id, "name": name, "slot": slot, "value": value, "cells": list(cells), "weight": weight, "armor": armor, "examine": examine, "look": l}

outfits = {}
outfits["pigskin"] = {"name": "Pigskin outfit", "requires": {}, "pieces": [
    piece("pigskin_hood", "Pigskin hood", "HEAD", "hood", "7E3AA6", 160, "A purple hood that covers the head and shadows the face.", (2, 2), 0.4, 0.5, trim="4E2470", lining="341C46"),
    piece("pigskin_shirt", "Pigskin shirt", "BODY", "tunic", "58287E", 220, "A dark purple tunic, cut close.", (3, 3), 0.8, 1, trim="7E3AA6", fit="fitted", collar="high", belt="2A2230"),
    piece("pigskin_robe", "Pigskin robe", "LEGS", "robe", "7E3AA6", 240, "Long purple lower robes that brush the ankles.", (3, 3), 1.0, 0.5, trim="4E2470", length=11.6, under="4A4650"),
    piece("pigskin_gloves", "Pigskin gloves", "HANDS", "gloves", "2E2A34", 90, "Gloves of dark fabric.", (2, 1), 0.2, 0.5, trim="4E2470"),
    piece("pigskin_boots", "Pigskin boots", "FEET", "boots", "2A2630", 110, "Simple dark boots.", (2, 2), 0.9, 0.5, rows=9, sole="18161C"),
    piece("pigskin_cloak", "Pigskin cloak", "CAPE", "cloak", "7E3AA6", 280, "A flowing purple cape.", (3, 3), 1.2, 0.5, trim="4E2470", lining="7A7480", length=20),
    piece("pigskin_logo", "Pigskin logo", "NECK", "insignia", "B8B0C4", 140, "The cult's insignia, worn on a cord about the neck.", (1, 1), 0.05, 0, cord="2A2230", mark="7E3AA6"),
]}
outfits["rogue"] = {"name": "Rogue outfit", "requires": {"AGILITY": 50, "THIEVING": 50}, "pieces": [
    piece("rogue_mask", "Rogue mask", "HEAD", "mask", "1C1C20", 300, "A black hood that hides all but the eyes.", (2, 2), 0.3, 0.5, trim="0E0E10"),
    piece("rogue_top", "Rogue top", "BODY", "tunic", "18181C", 420, "A tight tunic of black leather.", (3, 3), 1.4, 2, trim="0E0E10", fit="fitted", collar="high", leather=True, belt="0E0E10", straps="2A2A30"),
    piece("rogue_trousers", "Rogue trousers", "LEGS", "trousers", "1A1A1E", 360, "Slim black trousers.", (3, 3), 0.8, 1, trim="0E0E10", fit="slim"),
    piece("rogue_gloves", "Rogue gloves", "HANDS", "gloves", "141418", 240, "Fitted black gloves.", (2, 1), 0.15, 0.5, trim="0E0E10"),
    piece("rogue_boots", "Rogue boots", "FEET", "boots", "1E1A18", 280, "Soft dark leather boots.", (2, 2), 0.7, 0.5, rows=11, sole="0E0C0C", trim="2A2420"),
]}
outfits["prospector"] = {"name": "Prospector outfit", "requires": {"MINING": 30}, "pieces": [
    piece("prospector_helmet", "Prospector helmet", "HEAD", "mining_helmet", "8C7A52", 260, "A sturdy mining helmet, its lamp long since smoked black.", (2, 2), 1.2, 2, trim="5E5034", lamp="F2D27A"),
    piece("prospector_jacket", "Prospector jacket", "BODY", "jacket", "B08A58", 300, "A thick tan work jacket, dusted grey with rock.", (3, 3), 2.0, 2, trim="7A5A34", fit="loose", collar="fold", pockets=True, dust="9A9890", hem=3.4),
    piece("prospector_legs", "Prospector legs", "LEGS", "trousers", "8A7654", 220, "Loose, hard-wearing work trousers.", (3, 3), 1.4, 1, trim="4E4232", fit="loose", dust="8E8C84", patches="5E5040"),
    piece("prospector_boots", "Prospector boots", "FEET", "boots", "4A3624", 200, "Heavy mining boots with iron at the toe.", (2, 2), 1.6, 1, rows=12, sole="1A140E", toe="6A6A70", heavy=True),
]}
styles = {
    "commoner": {"prefix": "Commoner's ", "fez": "A0302A", "top": "D8CCAE", "coat": "B49C74", "legs": "CDBB94", "robe": "C2AE86", "trim": "8C7452", "sash": "8C5A34", "value": 1.0},
    "noble":    {"prefix": "Noble's ",    "fez": "B02A28", "top": "EEE6D2", "coat": "2E7E72", "legs": "D8CCAE", "robe": "E2D8C0", "trim": "C8A050", "sash": "2E5A5A", "value": 3.0},
    "royal":    {"prefix": "Royal ",      "fez": "9A1C24", "top": "F6F0E2", "coat": "6E2E96", "legs": "E6DCC4", "robe": "F2ECDC", "trim": "E0B848", "sash": "6A2A4A", "value": 8.0},
}
for sk, st in styles.items():
    v = st["value"]; p = st["prefix"]
    lower = p.lower() if not p.endswith("'s ") else p.replace("Commoner's ", "commoner's ").replace("Noble's ", "noble's ")
    outfits["desert_" + sk] = {"name": p + "desert outfit", "style": sk, "requires": {}, "pieces": [
        piece(sk + "_fez", p + "fez", "HEAD", "fez", st["fez"], int(60 * v), "A small cylindrical red hat with a tassel.", (1, 1), 0.2, 0.2, tassel="1A1A1A" if sk == "commoner" else st["trim"], band=st["trim"] if sk != "commoner" else None),
        piece(sk + "_desert_top", p + "desert top", "BODY", "tunic", st["top"], int(90 * v), "A loose, pale tunic for the desert's heat.", (3, 3), 0.6, 0.5, trim=st["trim"], fit="loose", collar="slit", hem=6.5, sash=st["sash"], embroidery=sk != "commoner"),
        piece(sk + "_desert_overcoat", p + "desert overcoat", "CAPE", "overcoat", st["coat"], int(160 * v), "A long, layered outer garment, worn open over the tunic.", (3, 4), 1.4, 1, trim=st["trim"], length=12.5, layers=1 if sk == "commoner" else 2),
        piece(sk + "_desert_legs", p + "desert legs", "LEGS", "trousers", st["legs"], int(70 * v), "Lightweight beige trousers.", (3, 3), 0.5, 0.5, trim=st["trim"], fit="loose", gathered=True),
        piece(sk + "_desert_robes", p + "desert robes", "LEGS", "robe", st["robe"], int(130 * v), "Long, flowing desert robes.", (3, 3), 0.9, 0.5, trim=st["trim"], length=11.6, under=st["legs"]),
    ]}
outfits["farmer"] = {"name": "Farmer's outfit", "requires": {"HARVESTING": 5}, "pieces": [
    piece("farmers_strawhat", "Farmer's strawhat", "HEAD", "straw_hat", "D8BC6C", 40, "A wide hat of woven straw.", (2, 2), 0.2, 0.2, band="7A4A2A", weave="BCA056"),
    piece("farmers_shirt", "Farmer's shirt", "BODY", "jacket", "8A7048", 60, "Simple, earthy workwear, a jacket over a shirt.", (3, 3), 0.9, 0.5, trim="5E4A2E", fit="loose", collar="fold", hem=3.4, under="C8BC9C", rolled=True),
    piece("farmers_boro_trousers", "Farmer's boro trousers", "LEGS", "trousers", "3E4A5E", 50, "Loose trousers, patched and stitched over and over.", (3, 3), 0.8, 0.5, trim="2E3646", fit="loose", patches="5A6A7E", stitch="D8D2C2"),
    piece("farmers_boots", "Farmer's boots", "FEET", "boots", "6A4A2C", 60, "Sturdy brown work boots.", (2, 2), 1.2, 0.5, rows=10, sole="2A1C10"),
]}
outfits["lumberjack"] = {"name": "Lumberjack outfit", "requires": {"WOODCUTTING": 40}, "pieces": [
    piece("lumberjack_hat", "Lumberjack hat", "HEAD", "cap", "2A2A30", 90, "A dark, fitted cap.", (2, 1), 0.2, 0.3, trim="1A1A1E", peak=True),
    piece("lumberjack_top", "Lumberjack top", "BODY", "shirt", "A82424", 160, "A red-and-black checked work shirt.", (3, 3), 0.8, 0.5, trim="1A1414", fit="regular", collar="fold", check="1A1414", rolled=True),
    piece("lumberjack_legs", "Lumberjack legs", "LEGS", "trousers", "6A6A6E", 120, "Plain grey work trousers.", (3, 3), 0.9, 0.5, trim="4A4A4E", fit="regular", braces="2A1A12"),
    piece("lumberjack_boots", "Lumberjack boots", "FEET", "boots", "2A2018", 140, "Heavy dark leather boots.", (2, 2), 1.6, 1, rows=12, sole="140E0A", laces="8A6A3A", heavy=True),
]}

for o in outfits.values():
    for p in o["pieces"]:
        p["look"] = {k: v for k, v in p["look"].items() if v is not None}

data = {
    "_about": "(0.4.5, gamefixes: the clothing update) Wearable outfits: each piece an item (its slot, worth, size in the pack, weight, protection) and its look - the shape it is drawn with (rpg.render.Garments) and its colours. 'requires' is every skill level the whole outfit asks (none: anyone may wear it). Who wears them is the census kits' 'outfits'.",
    "outfits": outfits,
}
os.makedirs(os.path.dirname(OUT), exist_ok=True)
json.dump(data, open(OUT, 'w', encoding='utf-8'), indent=1)
n = sum(len(o["pieces"]) for o in outfits.values())
print("outfits", len(outfits), "pieces", n)
