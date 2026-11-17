import os
SRC = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', 'rpggame', 'src', 'main', 'java', 'rpg')
def sub(rel, old, new):
    p = os.path.join(SRC, rel)
    s = open(p, encoding='utf-8').read()
    if old not in s: raise SystemExit('MISSING in ' + rel + ': ' + old[:90])
    open(p, 'w', encoding='utf-8', newline='').write(s.replace(old, new, 1))
sub('render/Rig.java', '''        if (!st.stance[k]) { a.footPlants++; a.footPlantSeen = System.nanoTime(); }''',
    '''        if (!st.stance[k]) { a.footPlants++; a.footPlantSeen = System.nanoTime(); if (a.grounded && !a.swimming()) Prints.push(st.to[k][0], a.y, st.to[k][2], a.yaw, false); }''')
sub('render/Gfx.java', '''        decals.time = (float) g.time;
        decals.frame(cmd);''', '''        prints(g);
        decals.snow = g.timeSystem.weather.snowCover;
        decals.time = (float) g.time;
        decals.frame(cmd);''')
sub('render/Gfx.java', '''    /** The current matrix: the figure's root times a segment's matrix (figure space, pixels). */''', '''    /**
     * (0.4.8, gamefixes: footsteps in the snow and the puddles, each its own) every print the figures laid this frame,
     * pressed into the ground as it is: in lying snow a shadowed hollow (never buried by it: a print's tint is given a
     * negative alpha) lasting until the snow is renewed; on soaked open ground a dark wet sole that dries off; in mud a
     * deep brown print, in sand a faint one. Rock, boards and turf in dry weather keep none.
     */
    private void prints(Rpg g) {
        var w = g.timeSystem.weather;
        for (float[] p; (p = Prints.poll()) != null; ) {
            if (Math.hypot(p[0] - camX, p[2] - camZ) > 40) continue;
            int bx = (int) Math.floor(p[0]), by = (int) Math.floor(p[1] - 0.05f), bz = (int) Math.floor(p[2]);
            int below = g.vox.block(bx, by, bz);
            if (below == rpg.world.Blocks.AIR || below == rpg.world.Blocks.WATER || rpg.world.Blocks.objectCell(below)) continue;
            boolean open = g.vox.skyLight(bx, by + 1, bz) > 0.7f;
            int kind = p[4] > 0.5f ? astratto.decal.DecalAtlas.HOOFPRINT : astratto.decal.DecalAtlas.FOOTPRINT;
            float size = p[4] > 0.5f ? 0.26f : 0.34f, rot = -p[3];
            float top = by + 1.0f;
            if (open && w.snowCover > 0.18f && below != rpg.world.Blocks.STAIRS)
                decals.add(kind, p[0], top, p[2], 0, 1, 0, size, rot, rpg.Tuning.f("weather.snowPrintLife", 1200), 0x6E7E9C, -0.62f * Math.min(1, w.snowCover * 1.6f));
            else if (open && w.wetness > 0.35f && rpg.world.Blocks.material(below) != rpg.world.Blocks.Material.WOOD)
                decals.add(kind, p[0], top, p[2], 0, 1, 0, size, rot, rpg.Tuning.f("weather.wetPrintLife", 100), 0x14100C, 0.5f * Math.min(1, w.wetness));
            else if (below == rpg.world.Blocks.MUD)
                decals.add(kind, p[0], top, p[2], 0, 1, 0, size, rot, 600, 0x2A1C12, 0.62f);
            else if (below == rpg.world.Blocks.SAND)
                decals.add(kind, p[0], top, p[2], 0, 1, 0, size, rot, 240, 0x6A5A3A, 0.32f);
        }
    }

    /** The current matrix: the figure's root times a segment's matrix (figure space, pixels). */''')
sub('animal/Animals.java', '''    private void hooves(Animal a, float dt) {''', '''    private void hooves(Animal a, float dt) {
        // (0.4.8) each hoof that falls leaves its print where the ground takes one (Gfx.prints)
        if (a.grounded && Math.hypot(a.vx, a.vz) > 0.4f && a.distTo(g.player) < 40 && g.time >= a.nextPrint) {
            float sp = (float) Math.hypot(a.vx, a.vz), side = (a.printSide = -a.printSide) * 0.22f;
            float rx = (float) Math.cos(a.yaw), rz = (float) -Math.sin(a.yaw);
            rpg.render.Prints.push(a.x + rx * side, a.y, a.z + rz * side, a.yaw, true);
            a.nextPrint = g.time + Math.max(0.12, 0.55 / sp);
        }''')
sub('animal/Animal.java', '''    public final Species species;''', '''    public final Species species;
    /** (0.4.8) When its next hoof print falls, and which side. */
    public double nextPrint;
    public float printSide = 1;''')
print('prints')
