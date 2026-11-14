package rpg.ui;

import java.util.ArrayList;
import java.util.List;
import rpg.Rpg;
import rpg.ai.Npc;
import rpg.world.Blocks;
import rpg.world.DevMap;
import rpg.world.WorldMap;

/**
 * (0.4.1, gamefixes: a devmode (0.4.2: devui) console command showing a developer display - FPS, XYZ, the NPCs' line of sight -
 * genuinely useful for bug reports) The developer display: {@code devui} in the console turns it on and off. One panel
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
        out.add(String.format("time: %s   weather %s  rain %.2f  fog %.2f  overcast %.2f", g.state.clockText(), w.kind(), w.rain, w.fog, w.overcast));
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
        out.add(String.format("build: %s   map %s seed %s   heap %d / %d MB   tick %d", rpg.Main.VERSION, m.id, WorldMap.shadowveil() ? String.valueOf(rpg.world.shadowveil.Atlas.get().seed) : "-",
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
        // (0.4.4, gamefixes: devui cleaner, smaller and more readable, its background transparent, the text on black) no
        // panel at all: every run of text sits on its own tight black backing, so the world shows between the lines; the
        // frame rate coloured by how smooth it is, the position in X green, Y red, Z blue, then label and value per line,
        // and the people near as a compact table
        List<String> lines = lines();
        Ui2D.Face was = ui.use(ui.chat);
        try {
            final float S = 13, ROW = 17, BIG = 18, GAP = 6;
            float x = 12, cy = 136;   // under the resource bars
            String fpsLine = lines.isEmpty() ? "" : lines.get(0), where = "";
            List<String> rest = new ArrayList<>();
            for (int i = 1; i < lines.size(); i++) { String l = lines.get(i); if (l.startsWith("XYZ ")) where = l.substring(l.indexOf("chunk")); else if (!l.startsWith("  ")) rest.add(l); }
            float fps = parseFps(fpsLine);
            int fc = fps >= 60 ? 0xFF7CE07C : fps >= 30 ? 0xFFE8D060 : 0xFFE86050;
            float cx = x + chip(ui, "DEV", x, cy, S, Style.INFO) + GAP;
            chip(ui, fpsLine, cx, cy, S, fc);
            cy += ROW + 2;
            cx = x;
            cx += chip(ui, String.format("X %.2f", pl.x), cx, cy, BIG, 0xFF5CE65C) + GAP;
            cx += chip(ui, String.format("Y %.2f", pl.y), cx, cy, BIG, 0xFFFF5A5A) + GAP;
            chip(ui, String.format("Z %.2f", pl.z), cx, cy, BIG, 0xFF5AA0FF);
            cy += BIG + 7;
            if (!where.isEmpty()) { chip(ui, where, x, cy, S, 0xFFB8C0CC); cy += ROW; }
            for (String l : rest) {
                int cut = l.indexOf(':');
                if (cut > 0 && cut < 14) {
                    float lw = chip(ui, l.substring(0, cut + 1), x, cy, S, 0xFF8C96A4);
                    chip(ui, l.substring(cut + 1).trim(), x + lw, cy, S, 0xFFF0F0F0);
                } else chip(ui, l, x, cy, S, 0xFFF0F0F0);
                cy += ROW;
            }
            // the people near: name, distance, sight, what they do (one line each)
            List<Npc> people = nearest(5);
            if (!people.isEmpty()) {
                cy += 4;
                for (Npc o : people) {
                    float d = (float) Math.hypot(o.x - pl.x, o.z - pl.z);
                    boolean sees = o.loaded && g.vox.sight(o.x, o.eyeY(), o.z, pl.x, pl.eyeY(), pl.z);
                    String sight = !o.loaded ? "off-stage" : sees ? (d < 24 ? "sees you" : "clear") : "no line";
                    float px = x;
                    px += chip(ui, fit(ui, o.name, 110, S), px, cy, S, 0xFFFFFFFF);
                    px += chip(ui, String.format("%.0f m", d), px, cy, S, 0xFFF0F0F0);
                    px += chip(ui, sight, px, cy, S, !o.loaded ? 0xFF8C96A4 : sees ? 0xFF7CE07C : 0xFFE8B84A);
                    chip(ui, fit(ui, o.goal + (o.lastOutcome == null ? "" : ": " + o.lastOutcome), Math.max(120, w * 0.33f), S), px, cy, S, 0xFFD8D0C0);
                    cy += ROW;
                }
            }
        } finally { ui.use(was); }
    }

    /** A run of text on its own tight black backing; returns the width it took (with a hair's gap after it). */
    private static float chip(Ui2D ui, String t, float x, float y, float size, int color) {
        if (t == null || t.isEmpty()) return 0;
        float tw = ui.width(t, size);
        ui.rect(x, y - 1, tw + 8, size + 5, 0xD0000000);
        ui.text(t, x + 4, y + 1, size, color);
        return tw + 10;
    }

    /** Text cut to a width, with an ellipsis. */
    private static String fit(Ui2D ui, String s, float max, float size) {
        if (ui.width(s, size) <= max) return s;
        while (s.length() > 1 && ui.width(s + "...", size) > max) s = s.substring(0, s.length() - 1);
        return s + "...";
    }

    private static float parseFps(String line) {
        try { return line.startsWith("FPS ") ? Float.parseFloat(line.substring(4, line.indexOf(' ', 4))) : 0; } catch (RuntimeException e) { return 0; }
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
