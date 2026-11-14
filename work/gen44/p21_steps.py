def patch(p, pairs):
    s = open(p, encoding='utf-8').read()
    for a, b in pairs:
        assert s.count(a) == 1, (p, a[:100], s.count(a))
        s = s.replace(a, b)
    open(p, 'w', encoding='utf-8').write(s)
A = 'rpggame/src/main/java/rpg/world/shadowveil/Architecture.java'
patch(A, [
("""    void clearWays() {""",
 """    /** (0.4.4) Door steps laid where a doorway stood above its ground (the survey reports how many). */
    public int doorSteps;

    /**
     * (0.4.4, gamefixes: players and NPCs reliably entering and exiting buildings; a targeted exterior correction where an
     * entrance did not meet its ground) Before every doorway, outward, the ground is met by a flight of steps wherever it
     * falls away more than a step below the threshold: a stair block a cell lower at each cell out, the ground filled under
     * it in the building's base, until the steps reach the ground. Run after the ways are cleared, so nothing undoes them.
     */
    void doorSteps() {
        Kit.Canvas c = new Kit.Canvas(t);
        int made = 0;
        for (Structure s : structures) {
            if (Boolean.TRUE.equals(s.opts.get("water"))) continue;
            int base = s.style.b("base");
            for (int k = 0; k < s.doors.size(); k++) {
                int[] out = s.doorOut.get(k);
                for (int[] cell : s.doors.get(k)) {
                    int level = s.doorFloors.get(k);   // the threshold's cell: walked on at level + 1
                    for (int step = 1; step <= 8; step++) {
                        int x = cell[0] + out[0] * step, z = cell[1] + out[1] * step;
                        if (!t.in(x, z) || inLot(x, z, 0) && !s.contains(x, z) || t.water[t.idx(x, z)] >= 0) break;
                        int ground = c.ground(x, z);
                        if (ground >= level - 1) break;          // the ground is within a step: walked down onto
                        if (c.get(x, level + 1, z) != Blocks.AIR || c.get(x, level + 2, z) != Blocks.AIR) break;
                        level--;
                        c.set(x, level, z, Blocks.STAIRS);
                        c.footing(x, level - 1, z, base);
                        int i = t.idx(x, z);
                        t.top[i] = Math.max(t.top[i], level);
                        made++;
                    }
                }
            }
        }
        doorSteps = made;
        notes.add(made + " door steps laid where doorways stood above their ground");
    }

    void clearWays() {"""),
])
patch('rpggame/src/main/java/rpg/world/shadowveil/Terrain.java', [
("""            if (t.arch != null) { t.arch.clearWays(); at = t.stage("ways", at); }""",
 """            if (t.arch != null) { t.arch.clearWays(); t.arch.doorSteps(); at = t.stage("ways", at); }"""),
])
T = 'rpggame/src/test/java/rpg/world/Gamefixes044Test.java'
patch(T, [
("""    private static float[] beside(float x, int y, float z) {
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        for (int r = 0; r <= 2; r++) for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {""",
 """    private static float[] beside(float x, int y, float z) { return beside(x, y, z, 2); }
    private static float[] beside(float x, int y, float z, int reach) {
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        for (int r = 0; r <= reach; r++) for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {"""),
("""            float[] out = beside(ox, (int) Math.floor(ShadowVeil.standY(ox, oz) + 0.01f), oz);""",
 """            // before the door (a waterside building's landing stage, or the step inside its door where its front is water)
            float[] out = beside(ox, (int) Math.floor(ShadowVeil.standY(ox, oz) + 0.01f), oz, 3);
            if (out == null) out = beside(en.x() + 0.5f, en.y() + 1, en.z() + 0.5f, 1);"""),
("""                float[] in = null;
                for (int x = r.rect[0]; x <= r.rect[2] && in == null; x++) for (int z = r.rect[1]; z <= r.rect[3] && in == null; z++)
                    if (g.vox.navigation.validPoint(x + 0.5f, r.y, z + 0.5f, d -> true)) in = new float[]{x + 0.5f, r.y, z + 0.5f};""",
 """                // the standable cell of the room nearest its middle (not a corner pocket a tower's wall leaves)
                float[] in = null; double bd = 1e9;
                float mx = (r.rect[0] + r.rect[2] + 1) / 2f, mz = (r.rect[1] + r.rect[3] + 1) / 2f;
                for (int x = r.rect[0]; x <= r.rect[2]; x++) for (int z = r.rect[1]; z <= r.rect[3]; z++)
                    if (g.vox.navigation.validPoint(x + 0.5f, r.y, z + 0.5f, d -> true) && Math.hypot(x + 0.5f - mx, z + 0.5f - mz) < bd) { bd = Math.hypot(x + 0.5f - mx, z + 0.5f - mz); in = new float[]{x + 0.5f, r.y, z + 0.5f}; }"""),
])
print('ok')
