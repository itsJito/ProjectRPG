import json, collections
OD = collections.OrderedDict

def sub(p, a, b, cnt=1):
    s = open(p, encoding='utf-8').read()
    assert a in s, (p, a[:80])
    s = s.replace(a, b, cnt)
    open(p, 'w', encoding='utf-8').write(s)

# ---- the crown: a garment shape (worn on the hair, drawn on the head; and as a thing on its own)
G = 'src/main/java/rpg/render/Garments.java'
sub(G, '''    public static boolean onHair(ItemDef d) { String s = shape(d); return s.equals("fez") || s.equals("straw_hat") || s.equals("cap"); }''',
       '''    public static boolean onHair(ItemDef d) { String s = shape(d); return s.equals("fez") || s.equals("straw_hat") || s.equals("cap") || s.equals("crown"); }''')
sub(G, '''            case "fez" -> fez(d, c);''', '''            case "fez" -> fez(d, c);
            case "crown" -> crown(d, c);''')
sub(G, '''    private static ItemModels.Model strawHat(ItemDef d, int c) {''', '''    /**
     * (0.4.7, gamefixes: King Arthur made his own) a crown: a gold circlet, a fleur at each corner and the middle of each
     * side, a jewel set in the front and back of the band.
     */
    private static ItemModels.Model crown(ItemDef d, int c) {
        VoxModel v = new VoxModel(13, 7, 13);
        int gold = metal(d.color), gem = m(color(d, "gem", 0xA8142A)), gem2 = m(color(d, "gem2", 0x1E3A9A));
        for (int y = 0; y < 3; y++) for (int x = 0; x < 13; x++) for (int z = 0; z < 13; z++) {
            boolean ring = x == 0 || x == 12 || z == 0 || z == 12;
            if (ring) v.set(x, y, z, y == 1 ? sh(gold, 1.12f) : gold);
        }
        int[][] fleurs = {{0, 0}, {6, 0}, {12, 0}, {0, 6}, {12, 6}, {0, 12}, {6, 12}, {12, 12}};
        for (int[] f : fleurs) { v.box(f[0], 3, f[1], f[0], 4, f[1], gold); v.set(f[0], 5, f[1], sh(gold, 1.2f)); }
        v.set(6, 1, 0, gem); v.set(6, 1, 12, gem2); v.set(0, 1, 6, gem2); v.set(12, 1, 6, gem);
        return new ItemModels.Model(v, 6.5f, 0.5f, 6.5f);
    }

    private static ItemModels.Model strawHat(ItemDef d, int c) {''')

GFX = 'src/main/java/rpg/render/Gfx.java'
sub(GFX, '''            case "straw_hat" -> {
                int weave = Garments.color(head, "weave",''', '''            case "crown" -> {
                // (0.4.7) a gold circlet round the brow, its fleurs standing over the hair, a jewel at the front
                float rw = hw * 0.5f + 0.35f, rd = hd * 0.5f + 0.35f, y = hh + 0.15f;
                int gold = head.color, gem = Garments.color(head, "gem", 0xA8142A);
                headBox(0, y, -rd, rw * 2 + 0.3f, 0.75f, 0.3f, gold, 1, 0.3f);
                headBox(0, y, rd, rw * 2 + 0.3f, 0.75f, 0.3f, gold, 1, 0.3f);
                headBox(-rw, y, 0, 0.3f, 0.75f, rd * 2, gold, 1, 0.3f);
                headBox(rw, y, 0, 0.3f, 0.75f, rd * 2, gold, 1, 0.3f);
                for (float fx : new float[]{-rw, 0, rw}) for (float fz : new float[]{-rd, 0, rd}) {
                    if (fx == 0 && fz == 0) continue;
                    headBox(fx, y + 0.75f, fz, 0.42f, 0.8f, 0.42f, gold, 1, 0.3f);
                    headBox(fx, y + 1.3f, fz, 0.26f, 0.32f, 0.26f, Skin.shade(gold, 1.15f) & 0xFFFFFF, 1, 0.25f);
                }
                headBox(0, y + 0.05f, -rd - 0.2f, 0.45f, 0.45f, 0.18f, gem, 0.2f, 0.15f);
            }
            case "straw_hat" -> {
                int weave = Garments.color(head, "weave",''')

# ---- the census: a person's own levels, kit and look over their role's (Arthur's regalia and his Godly arms)
C = 'src/main/java/rpg/world/shadowveil/Census.java'
sub(C, '''            // (0.4.7, gamefixes: NPCs wearing gear must have the stats to wear and use it)
            n.meetGear();''', '''            // (0.4.7, gamefixes: King Arthur made his own) a person's own levels, arms and look, over their role's
            own(g, n, p);
            // (0.4.7, gamefixes: NPCs wearing gear must have the stats to wear and use it)
            n.meetGear();''')
sub(C, '''    /** A made person's means and abilities, from the data's kits by role (and profession). */''', '''    /**
     * (0.4.7) What the census gives one person over their role's kit: 'levels' (skills), 'wear' (items, each in its
     * slot, replacing what was there) and 'look' (their appearance's fields: hair, hairColor, beard, beardColor, skin,
     * eyeColor, bodyType) - a king who is unmistakably himself.
     */
    private static void own(rpg.Rpg g, Npc n, Map<String, Object> person) {
        for (var e : Atlas.map(person, "levels").entrySet()) { Skill s = Skill.byName(e.getKey()); if (s != null && e.getValue() instanceof Number v) n.skills.setLevel(s, v.intValue()); }
        for (Object o : Atlas.arr(person, "wear")) {
            ItemDef d = Items.find(String.valueOf(o));
            if (d == null || d.slot == null) continue;
            n.eq.set(d.slot, new ItemStack(d, 1)); g.state.mint(d, 1);
        }
        Map<String, Object> look = Atlas.map(person, "look");
        if (!look.isEmpty() && n.look != null) {
            for (var e : look.entrySet()) {
                if (!(e.getValue() instanceof Number v)) continue;
                try { java.lang.reflect.Field f = rpg.actor.Appearance.class.getField(e.getKey()); if (f.getType() == int.class) f.setInt(n.look, v.intValue()); }
                catch (ReflectiveOperationException ignored) { }
            }
            n.fitBody();
        }
    }

    /** A made person's means and abilities, from the data's kits by role (and profession). */''')

# ---- the data: the Pendragon regalia outfit and Arthur's own entry
PA = 'src/main/resources/data/apparel.json'
A = json.load(open(PA, encoding='utf-8'), object_pairs_hook=OD)
A['outfits']['pendragon'] = OD([("name", "Pendragon regalia"), ("style", "royal"), ("requires", OD()), ("pieces", [
    OD([("id", "crown_of_camelot"), ("name", "Crown of Camelot"), ("slot", "HEAD"), ("value", 9000), ("cells", [2, 2]), ("weight", 0.6), ("armor", 1),
        ("examine", "A gold circlet set with a ruby and a sapphire, its fleurs worn smooth by the hands of kings."),
        ("look", OD([("shape", "crown"), ("color", "E8C048"), ("gem", "A8142A"), ("gem2", "1E3A9A")]))]),
    OD([("id", "pendragon_mantle"), ("name", "Pendragon mantle"), ("slot", "CAPE"), ("value", 6000), ("cells", [3, 4]), ("weight", 1.6), ("armor", 1),
        ("examine", "A long mantle of deep red wool lined with ermine, edged in gold thread."),
        ("look", OD([("shape", "cloak"), ("color", "8A1420"), ("trim", "E0B040"), ("lining", "F2EEE6"), ("length", 26)]))])])])
open(PA, 'w', encoding='utf-8').write(json.dumps(A, indent=1, ensure_ascii=False) + "\n")

PC = 'src/main/resources/data/shadowveil_census.json'
Cn = json.load(open(PC, encoding='utf-8'), object_pairs_hook=OD)
c = Cn['census']
for p in c['people'] + c['additions']:
    if p['name'] == 'Arthur' and p.get('kingdom') == 'Camelot':
        p['levels'] = OD([("ATTACK", 96), ("STRENGTH", 96), ("DEFENSE", 96), ("HEALTH", 96), ("RANGED", 60), ("AGILITY", 70), ("FAITH", 80)])
        p['wear'] = ["godly_sword", "godly_shield", "godly_body", "godly_legs", "godly_helm"]
        p['look'] = OD([("hair", 3), ("hairColor", 5), ("beard", 2), ("beardColor", 5), ("eyeColor", 2), ("bodyType", 1)])
c['outfits'].setdefault('people', OD())['Arthur'] = 'pendragon'
open(PC, 'w', encoding='utf-8').write(json.dumps(Cn, indent=1, ensure_ascii=False) + "\n")
print("ok")
