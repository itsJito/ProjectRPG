package rpg.ui;

import java.util.ArrayList;
import java.util.List;
import rpg.Rpg;
import rpg.ai.Npc;
import rpg.world.Blocks;
import rpg.world.DevMap;
import rpg.world.WorldMap;

/**
 * (0.4.1, gamefixes: a devmode console command showing a developer display - FPS, XYZ, the NPCs' line of sight -
 * genuinely useful for bug reports) The developer display: {@code devmode} in the console turns it on and off. One panel
 * down the left of the screen with everything a bug report needs to say where and when and what:
 * <ul>
 * <li>frames: the rate over the last second (its worst frame too), the render's load (chunks, quads, models, lights);</li>
 * <li>where: x, y, z; the chunk as projectrpg.txt counts them ([x, z], 1-based, south to north) and the cell in it; the
 *     heading; whose law holds; the region and biome; the structure the player is in or beside; the block underfoot
 *     and the one looked at;</li>
 * <li>when: the date, the hour, the weather, the fog's ring;</li>
 * <li>who: people simulated in full, far off and beyond the fog (the simple off-stage day), animals, monsters; the
 *     nearest people with what they are doing, and whether each can see the player - drawn over the world too, a line
 *     from each one's eyes to the player's (green: seen, amber: in range but blocked, grey: out of range);</li>
 * <li>the build: version, map, seed, heap.</li>
 * </ul>
 * {@code devreport} writes all of it - with the nearest person's full AI readout and the last chat lines - to
 * {@code reports/} beside the game and takes a screenshot, so a report is one command.
 */
public final class DevDisplay {
    private final Rpg g;
    private final Hud hud;
    public boolean on;
    /** The last second's frame times. */
    private final float[] frames = new float[240];
    private int frameAt, frameCount;
    private final float[] p = new float[2], q = new float[2];

    /** The display for a game (the HUD it draws on; null for its lines alone, as the tests read them). */
    public DevDisplay(Rpg g, Hud hud) { this.g = g; this.hud = hud; }

    void frame(float dt) {
        frames[frameAt] = dt; frameAt = (frameAt + 1) % frames.length; frameCount = Math.min(frames.length, frameCount + 1);
    }

    /** The display's lines (also what devreport writes). */
    public List<String> lines() {
        List<String> out = new ArrayList<>();
        var pl = g.player;
        // frames over the last second
        float sum = 0, worst = 0; int n = 0;
        for (int k = 0; k < frameCount && sum < 1f; k++) { float f = frames[Math.floorMod(frameAt - 1 - k, frames.length)]; sum += f; worst = Math.max(worst, f); n++; }
        float fps = n == 0 ? 0 : n / Math.max(1e-4f, sum);
        out.add(String.format("FPS %.0f   frame %.1f ms   worst %.1f ms (last second)", fps, n == 0 ? 0 : sum / n * 1000, worst * 1000));
        var st = hud == null || hud.gfx == null ? null : hud.gfx.last;
        if (st != null) out.add(String.format("render: %d chunks drawn, %d quads, %d models, %d lights, %d meshing", st.columnsDrawn, st.quadsDrawn, st.modelsDrawn, st.lights, st.columnsPending));
        // where
        WorldMap m = WorldMap.active();
        int cx = (int) Math.floor(pl.x / 16) + 1, czFromNorth = (int) Math.floor(pl.z / 16);
        int cz = m.sizeZ / 16 - czFromNorth;
        out.add(String.format("XYZ %.2f / %.2f / %.2f   chunk [%d, %d] cell (%d, %d)   facing %s %.0f deg  pitch %.0f", pl.x, pl.y, pl.z, cx, cz,
                Math.floorMod((int) Math.floor(pl.x), 16), Math.floorMod((int) Math.floor(pl.z), 16), compass(g.controller.camYaw), Math.toDegrees(g.controller.camYaw), Math.toDegrees(g.controller.camPitch)));
        String place = DevMap.jurisdiction(pl.x, pl.z);
        String extra = "";
        if (WorldMap.shadowveil()) {
            var t = rpg.world.shadowveil.ShadowVeil.terrain();
            if (t != null) {
                int bx = Math.max(0, Math.min(t.S - 1, (int) pl.x)), bz = Math.max(0, Math.min(t.S - 1, (int) pl.z));
                extra = "  biome " + t.biomeAt(bx, bz) + "  ground Y" + t.ground(bx, bz);
                var s = t.arch == null ? null : t.arch.at(bx, bz);
                if (s == null && t.arch != null) {
                    float best = 24;
                    for (var c : t.arch.structures) { float d = (float) Math.hypot(c.cx() - pl.x, c.cz() - pl.z); if (d < best) { best = d; s = c; } }
                    if (s != null) extra += String.format("   near: %s (%s) %.0f m", s.name, s.id, best);
                } else if (s != null) extra += "   in: " + s.name + " (" + s.id + ", " + s.template + ")";
            }
        }
        out.add("law: " + place + "   map: " + m.title + extra);
        int under = g.vox.block((int) Math.floor(pl.x), (int) Math.floor(pl.y - 0.05f), (int) Math.floor(pl.z));
        var hit = g.vox.ray(pl.x, pl.eyeY(), pl.z, (float) (-Math.sin(g.controller.camYaw) * Math.cos(g.controller.camPitch)), (float) Math.sin(g.controller.camPitch),
                (float) (-Math.cos(g.controller.camYaw) * Math.cos(g.controller.camPitch)), 24, false);
        out.add("underfoot: " + blockName(under) + "   looking at: " + (hit.hit ? blockName(hit.block) + String.format(" (%d, %d, %d) %.1f m", hit.bx, hit.by, hit.bz, hit.dist) : "-")
                + (g.interact.focus != null ? "   focus: " + g.interact.focus.label() : ""));
        // when
        var w = g.timeSystem.weather;
        out.add(String.format("%s   weather %s  rain %.2f  fog %.2f  overcast %.2f", g.state.clockText(), w.kind(), w.rain, w.fog, w.overcast));
        // who
        int full = 0, far = 0, off = 0, dead = 0;
        for (Npc o : g.npcs) {
            if (!o.alive) { dead++; continue; }
            if (!o.loaded) off++;
            else if (Math.hypot(o.x - pl.x, o.z - pl.z) > rpg.ai.AiSystem.LOD_FAR) far++;
            else full++;
        }
        out.add(String.format("people: %d near (full), %d far (reduced), %d off-stage (simple day, %d steps), %d dead   animals %d   monsters %d",
                full, far, off, g.ai.offstageSteps(), dead, g.animals == null ? 0 : g.animals.all().size(), g.monsters == null ? 0 : g.monsters.all().size()));
        List<Npc> near = nearest(6);
        for (Npc o : near) {
            boolean range = Math.hypot(o.x - pl.x, o.z - pl.z) < 24;
            boolean sees = o.loaded && g.vox.sight(o.x, o.eyeY(), o.z, pl.x, pl.eyeY(), pl.z);
            var aw = o.aware.get(pl.id);
            out.add(String.format("  %-10s %-16s %5.1f m  %s  aware %.2f  %s: %s", o.name, o.profession == null ? o.role : o.profession.title, (float) Math.hypot(o.x - pl.x, o.z - pl.z),
                    !o.loaded ? "off-stage" : sees ? (range ? "SEES you" : "clear line") : "no line", aw == null ? 0 : aw.level, o.goal, o.lastOutcome == null ? "-" : o.lastOutcome));
        }
        Runtime rt = Runtime.getRuntime();
        out.add(String.format("%s   map %s seed %s   heap %d / %d MB   tick %d", rpg.Main.VERSION, m.id, WorldMap.shadowveil() ? String.valueOf(rpg.world.shadowveil.Atlas.get().seed) : "-",
                (rt.totalMemory() - rt.freeMemory()) >> 20, rt.maxMemory() >> 20, g.state.tick));
        return out;
    }

    private List<Npc> nearest(int k) {
        List<Npc> all = new ArrayList<>();
        for (Npc o : g.npcs) if (o.alive && Math.hypot(o.x - g.player.x, o.z - g.player.z) < 60) all.add(o);
        all.sort((a, b) -> Float.compare((float) Math.hypot(a.x - g.player.x, a.z - g.player.z), (float) Math.hypot(b.x - g.player.x, b.z - g.player.z)));
        return all.subList(0, Math.min(k, all.size()));
    }

    private static String compass(float yaw) {
        // the camera's yaw: 0 looks toward -z (north), a quarter turn anticlockwise toward -x (west)
        String[] dirs = {"N", "NW", "W", "SW", "S", "SE", "E", "NE"};
        int k = Math.floorMod(Math.round((float) (yaw / (Math.PI / 4))), 8);
        return dirs[k];
    }
    private static String blockName(int id) {
        for (var f : Blocks.class.getFields()) {
            if (f.getType() != int.class || !java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
            if (f.getName().startsWith("L_") || f.getName().startsWith("IN_") || f.getName().equals("COUNT") || f.getName().equals("TEX") || f.getName().equals("LAYERS") || f.getName().equals("CHAR_LAYER0")) continue;
            try { if (f.getInt(null) == id) return f.getName().toLowerCase(java.util.Locale.ROOT) + " #" + id; } catch (IllegalAccessException ignored) { }
        }
        return "#" + id;
    }

    /** The panel and the sight lines over the world. */
    void draw(Ui2D ui, float w, float h) {
        if (!on) return;
        // the sight lines: from each near person's eyes to the player's
        var pl = g.player;
        for (Npc o : nearest(8)) {
            if (!o.loaded) continue;
            if (!hud.gfx.project(o.x, o.eyeY(), o.z, p) || !hud.gfx.project(pl.x, pl.eyeY() - 0.3f, pl.z, q)) continue;
            boolean range = Math.hypot(o.x - pl.x, o.z - pl.z) < 24;
            boolean sees = g.vox.sight(o.x, o.eyeY(), o.z, pl.x, pl.eyeY(), pl.z);
            int col = !range ? 0x80A0A0A0 : sees ? 0xC060E070 : 0xC0E0A040;
            ui.line(p[0], p[1], q[0], q[1], 1.5f, col);
            ui.shadow(o.name, p[0] - ui.width(o.name, 11) / 2, p[1] - 16, 11, col | 0xFF000000);
        }
        List<String> lines = lines();
        float x = 8, y = 64, lw = 0;
        for (String s : lines) lw = Math.max(lw, ui.width(s, 12));
        lw = Math.min(lw + 14, w - 16);
        ui.rect(x, y, lw, 22 + lines.size() * 15, 0xC8101014);
        ui.border(x, y, lw, 22 + lines.size() * 15, 1, Style.EDGE);
        ui.text("DEVMODE  (devreport writes this to reports/)", x + 6, y + 4, 12, Style.INFO);
        for (int i = 0; i < lines.size(); i++) ui.text(lines.get(i), x + 6, y + 22 + i * 15, 12, i == 0 ? 0xFFE8E8A0 : lines.get(i).startsWith("  ") ? 0xFFC8D8E8 : 0xFFE0E0E0);
    }

    /** Writes the display, the nearest person's AI readout and the last chat to reports/bug-<time>.txt; returns the file. */
    public java.nio.file.Path report(String note) throws java.io.IOException {
        java.nio.file.Path dir = java.nio.file.Path.of("reports");
        java.nio.file.Files.createDirectories(dir);
        String stamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        java.nio.file.Path file = dir.resolve("bug-" + stamp + ".txt");
        StringBuilder b = new StringBuilder("SHADOWVEIL bug report " + stamp + "\n");
        if (note != null && !note.isBlank()) b.append("note: ").append(note).append('\n');
        b.append('\n');
        for (String s : lines()) b.append(s).append('\n');
        List<Npc> near = nearest(1);
        if (!near.isEmpty()) {
            b.append("\nnearest person, in full:\n");
            for (String s : HudFragments.aiLines(hud, near.get(0))) b.append("  ").append(s).append('\n');
        }
        b.append("\nlast chat:\n");
        for (var line : g.chat.last(20)) b.append("  ").append(line.text()).append('\n');
        if (Rpg.PROF) b.append("\nupdate profile:\n").append(g.profile());
        java.nio.file.Files.writeString(file, b);
        hud.screenshotRequested = true;
        return file;
    }
}
