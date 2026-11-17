import os
HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, '..', '..', 'rpggame', 'src', 'main', 'java', 'rpg')
p = os.path.join(SRC, 'render', 'MonsterModels.java')
s = open(p, encoding='utf-8').read()
def sub(old, new):
    global s
    if old not in s: raise SystemExit('MISSING: ' + old[:80])
    s = s.replace(old, new, 1)
new = open(os.path.join(HERE, 'models_new.java'), encoding='utf-8').read()
new = new.replace("        VoxModel m = new VoxModel(W + 4, H + 7, D + 6);\n        int ox = 2, oz = 3;", "        VoxModel m = new VoxModel(W + 4, H + 7, D + 8);\n        int ox = 2, oz = 5;")
new = new.replace("&& oz + hz < D + 6)", "&& oz + hz < D + 8)")
sub('''        public ItemModels.Model body, beastHead, jaw, legA, legB, wingIn, wingOut, tailSeg;''',
    '''        public ItemModels.Model body, beastHead, jaw, legA, legB, wingIn, wingOut, tailSeg;
        /** (0.4.8) A scorpion's pincers (the arm, the claw) where they join beside its head, and its sting at the tail's end. */
        public ItemModels.Model clawA, clawB, sting;
        public float[] clawAt;''')
sub('''    public static synchronized Fig of(MonsterKind kind, MonsterKind.Breed breed) {
        String key = kind + (breed == null ? "" : ":" + breed);
        return CACHE.computeIfAbsent(key, k -> build(kind, breed));
    }''', '''    public static synchronized Fig of(MonsterKind kind, MonsterKind.Breed breed) { return of(kind, breed, 0); }
    /** (0.4.8) The figure for one of a kind's looks (MonsterKind.variants). */
    public static synchronized Fig of(MonsterKind kind, MonsterKind.Breed breed, int variant) {
        int v = Math.floorMod(variant, Math.max(1, kind.variants()));
        String key = kind + (breed == null ? "" : ":" + breed) + (v == 0 ? "" : "#" + v);
        return CACHE.computeIfAbsent(key, k -> build(kind, breed, v));
    }''')
sub('''    private static Fig build(MonsterKind k, MonsterKind.Breed breed) {
        Fig f = switch (k) {
            case GOBLIN -> goblin();
            case ORC -> orc();
            case TROLL -> troll();''', '''    private static Fig build(MonsterKind k, MonsterKind.Breed breed, int variant) {
        Fig f = switch (k) {
            case GOBLIN -> goblin();
            case ORC -> orc();
            case TROLL, ICE_TROLL, RIVER_TROLL -> trollKin(k, variant);
            case SMALL_SCORPION -> scorpion(false);
            case LARGE_SCORPION -> scorpion(true);
            case LESSER_DEMON -> lesserDemon(variant);
            case GREATER_DEMON -> greaterDemon(variant);''')
i = s.rindex('    /** A foot: a block w wide')
s = s[:i] + new.lstrip('\n') + '\n' + s[i:]
s = s.replace(' * The eleven monsters as posable voxel figures', ' * The monsters as posable voxel figures')
open(p, 'w', encoding='utf-8', newline='').write(s)
g = os.path.join(SRC, 'render', 'Gfx.java')
t = open(g, encoding='utf-8').read()
old = '        var f = MonsterModels.of(mo.kind, mo.breed);\n        float previous = charTint;'
assert old in t
t = t.replace(old, '        var f = MonsterModels.of(mo.kind, mo.breed, mo.variant);\n        float previous = charTint;')
open(g, 'w', encoding='utf-8', newline='').write(t)
print('models')
