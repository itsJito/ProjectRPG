def sub(p, a, b, cnt=1):
    s = open(p, encoding='utf-8').read()
    assert a in s, (p, a[:90])
    s = s.replace(a, b, cnt)
    open(p, 'w', encoding='utf-8').write(s)
G = 'src/main/java/rpg/render/Gfx.java'
sub(G, """    private void smoke(Rpg g) {
        int n = 0;""", """    private void smoke(Rpg g) {
        int n = volcanoes(0);""")
sub(G, """    private int putSmoke(int n, float x, float y, float z, float radius, float density, float r, float gg, float b, float glow, float rise, float swirl, long seed) {""",
"""    /**
     * (0.4.7, gamefixes: Ashmaw more impressive) every volcano with a 'plume' in the map's data smokes: a column of dark
     * ash cloud rising from its crater and spreading as it climbs, leaning with the wind, lit red from beneath by the lava
     * (a strong light in the crater that the cloud and the cone catch, by night a glow seen for miles), and embers thrown
     * up out of the lake when the eye is near.
     */
    private int volcanoes(int n) {
        var t = rpg.world.WorldMap.shadowveil() ? rpg.world.shadowveil.ShadowVeil.terrain() : null;
        if (t == null || t.craters.isEmpty()) return n;
        var peaks = rpg.world.shadowveil.Atlas.list(rpg.world.shadowveil.Atlas.get().root, "peaks");
        int k = 0;
        for (var p : peaks) {
            java.util.Map<String, Object> plume = rpg.world.shadowveil.Atlas.map(p, "plume");
            if (plume.isEmpty() || k >= t.craters.size()) continue;
            double[] c = t.craters.get(k++);
            float cx = (float) c[0], cz = (float) c[1];
            float d = (float) Math.hypot(cx - camX, cz - camZ);
            if (d > rpg.world.shadowveil.Atlas.d(plume, "reach", 900)) continue;
            float floor = rpg.world.WorldMap.standY(cx, cz);
            int puffs = rpg.world.shadowveil.Atlas.i(plume, "puffs", 8);
            float height = (float) rpg.world.shadowveil.Atlas.d(plume, "height", 60);
            double[] rad = rpg.world.shadowveil.Atlas.pt(plume.get("radius")), den = rpg.world.shadowveil.Atlas.pt(plume.get("density")), col = rpg.world.shadowveil.Atlas.pt(plume.get("colour"));
            float glow = (float) rpg.world.shadowveil.Atlas.d(plume, "glow", 0.5);
            var w = sceneGame == null ? null : sceneGame.timeSystem.weather;
            float wx = w == null ? 0.6f : 0.6f + w.wind * 2.2f, lean = 0.35f;
            for (int i = 0; i < puffs && n < 14; i++) {
                float f = i / (float) Math.max(1, puffs - 1);
                float y = floor + 6 + f * height, drift = f * f * height * lean * wx;
                float r = (float) (rad[0] + (rad[1] - rad[0]) * f), dens = (float) (den[0] + (den[1] - den[0]) * f);
                float wob = (float) Math.sin(clock * 0.21 + i * 1.7) * r * 0.18f;
                n = putSmoke(n, cx + drift + wob, y, cz + drift * 0.4f - wob, r, dens, (float) col[0], (float) col[1], (float) col[2], glow * (1 - f) * (1 - f), 0.25f, 0.6f, 0x7A11L + i);
            }
            // the lake's own light, the cone and the cloud lit by it
            float flick = 0.85f + 0.15f * (float) Math.sin(clock * 3.1) * (float) Math.sin(clock * 1.7 + 1);
            pointLight(cx, floor + 4, cz, 46, 6.5f * flick, 2.2f * flick, 0.5f * flick, 0);
            if (d < 260) {
                cmd.emitter(0x7A15L, 2, cx, floor + 1.2f, cz, (float) c[2] * 0.6f, 60, 0.6f, 2.4f, 1.0f, 0.5f, 0.12f, 0, 3.2f, 0);
                cmd.emitter(0x7A16L, 1, cx, floor + 3f, cz, (float) c[2] * 0.5f, 40, 0.5f, 3.0f, 0.24f, 0.21f, 0.2f, 0, 1.2f, 0);
            }
        }
        return n;
    }

    private int putSmoke(int n, float x, float y, float z, float radius, float density, float r, float gg, float b, float glow, float rise, float swirl, long seed) {""")
R = 'src/main/java/rpg/Rpg.java'
sub(R, """    private void worldEdge() {""", """    /** (0.4.7) Lava underfoot burns: a tenth of max health a second, and it sets the one standing there alight. */
    private double lavaNext;
    private void lavaUnderfoot() {
        if (player == null || !player.alive || time < lavaNext) return;
        int below = vox.block((int) Math.floor(player.x), (int) Math.floor(player.y - 0.05f), (int) Math.floor(player.z));
        if (below != rpg.world.Blocks.LAVA) return;
        lavaNext = time + 0.5;
        combat.environmentalDamage(player, Math.max(1, Math.round(player.maxHp() * LAVA_HURT * 0.5f)), "lava");
        if (time - lavaWarned > 5) { lavaWarned = time; chat.add("The lava burns you!", rpg.Chat.Kind.WARN); }
    }
    private double lavaWarned = -99;
    static final float LAVA_HURT = Tuning.f("world.lavaHurt", 0.1f);

    private void worldEdge() {""")
sub(R, """        worldEdge();
        if (PROF) mark(1);""", """        worldEdge();
        lavaUnderfoot();
        if (PROF) mark(1);""")
print("ok")
