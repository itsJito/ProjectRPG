package rpg.world.shadowveil;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * (0.4.0, gamefixes: automated validation and visual terrain previews) The surveyor's report on a grown ShadowVeil: the
 * design's rules checked against the ground ({@link #check}) and the maps drawn ({@link #draw}) - shaded relief with
 * water, roads and labels; elevation; realms; and what a walker can reach from Loxford Castle. Run by
 * ShadowVeilTest and by {@code gradlew shadowveil} (rpg.world.shadowveil.Survey as main).
 */
public final class Survey {
    private final Terrain t;
    private final Atlas a;
    /** Columns a walker can get to from the spawn and back again. */
    public final boolean[] reach;
    public final List<String> failures = new ArrayList<>(), notes = new ArrayList<>();

    /** Water a swimmer can get to: connected through water to a shore a walker reaches. */
    public final boolean[] swim;
    public Survey(Terrain t) { this.t = t; this.a = t.atlas; this.reach = reachable(); this.swim = swimmable(); }
    private boolean[] swimmable() {
        int S = t.S;
        boolean[] seen = new boolean[S * S];
        int[] queue = new int[S * S];
        int tail = 0, head = 0;
        for (int i = 0; i < seen.length; i++) {
            if (t.water[i] < 0 || seen[i]) continue;
            int x = i / S, z = i % S;
            boolean shore = false;
            for (int n : new int[]{x > 0 ? i - S : -1, x < S - 1 ? i + S : -1, z > 0 ? i - 1 : -1, z < S - 1 ? i + 1 : -1}) if (n >= 0 && reach[n]) shore = true;
            if (shore) { seen[i] = true; queue[tail++] = i; }
        }
        while (head < tail) {
            int i = queue[head++], x = i / S, z = i % S;
            for (int n : new int[]{x > 0 ? i - S : -1, x < S - 1 ? i + S : -1, z > 0 ? i - 1 : -1, z < S - 1 ? i + 1 : -1}) {
                if (n < 0 || seen[n] || t.water[n] < 0) continue;
                seen[n] = true; queue[tail++] = n;
            }
        }
        return seen;
    }

    // ------------------------------------------------------------------ walking
    /** The height a walker stands at on a column (the deck of a bridge, the ground, or the bed of water shallow enough to wade). */
    private int standAt(int i) {
        // (0.4.1) a building's lot is walked through its doors, not over its walls: the survey goes round it
        if ((t.flags[i] & Terrain.BUILT) != 0 && (t.flags[i] & Terrain.ROAD) == 0) return -1;
        if (t.deck[i] >= 0) return t.deck[i] + 1;
        if (t.water[i] >= 0 && t.water[i] - t.top[i] > 1) return -1;       // swimming water: not walked
        return t.top[i] + 1;
    }
    private boolean[] reachable() {
        double[] sp = Atlas.pt(a.root.get("spawn"));
        int sx = (int) a.x(sp[0]), sz = (int) a.z(sp[1]);
        boolean[] fwd = flood(sx, sz, false), back = flood(sx, sz, true), both = new boolean[fwd.length];
        for (int i = 0; i < both.length; i++) both[i] = fwd[i] && back[i];
        return both;
    }
    /** Every column reachable from (or, reversed, that can reach) a start: up one cell at a step, down three. */
    private boolean[] flood(int sx, int sz, boolean reverse) {
        int S = t.S;
        boolean[] seen = new boolean[S * S];
        int start = t.idx(sx, sz);
        for (int r = 0; r < 30 && standAt(start) < 0; r++) start = t.idx(Math.min(S - 1, sx + r), sz);
        int[] queue = new int[S * S];
        int head = 0, tail = 0;
        queue[tail++] = start; seen[start] = true;
        while (head < tail) {
            int i = queue[head++];
            int x = i / S, z = i % S, y = standAt(i);
            for (int k = 0; k < 4; k++) {
                int nx = x + (k == 0 ? 1 : k == 1 ? -1 : 0), nz = z + (k == 2 ? 1 : k == 3 ? -1 : 0);
                if (nx < 0 || nz < 0 || nx >= S || nz >= S) continue;
                int n = t.idx(nx, nz);
                if (seen[n]) continue;
                int ny = standAt(n);
                if (ny < 0) continue;
                int rise = reverse ? y - ny : ny - y;
                if (rise > 1 || rise < -3) continue;
                seen[n] = true; queue[tail++] = n;
            }
        }
        return seen;
    }

    // ------------------------------------------------------------------ the rules
    public Survey check() {
        int S = t.S, sea = a.sea;
        // ocean on all four edges and every corner
        int dryEdge = 0;
        for (int k = 0; k < S; k++) for (int[] c : new int[][]{{k, 0}, {k, S - 1}, {0, k}, {S - 1, k}}) if (t.water[t.idx(c[0], c[1])] < 0) dryEdge++;
        if (dryEdge > 0) failures.add(dryEdge + " edge columns are dry land (the ocean must surround the square)");
        // nothing over the ceiling
        int high = 0, peak = 0;
        for (int i = 0; i < t.top.length; i++) { peak = Math.max(peak, t.top[i]); if (t.top[i] > a.ceiling) high++; }
        if (high > 0) failures.add(high + " columns stand above Y" + a.ceiling);
        notes.add("highest ground Y" + peak);
        notes.add(t.trees.size() + " trees, " + t.rocks.size() + " ore rocks, " + t.roads.size() + " roads");
        // the regions' elevation targets (the median of each settlement core in its range, with a little slack)
        for (Map<String, Object> o : a.list("overlays")) {
            if (!(o.get("realm") instanceof String)) continue;
            int[] r = a.cells(Atlas.arr(o, "rect"));
            List<Integer> hs = new ArrayList<>();
            for (int x = r[0]; x <= r[2]; x += 3) for (int z = r[1]; z <= r[3]; z += 3) { int i = t.idx(x, z); if (t.water[i] < 0 && (t.flags[i] & Terrain.ROAD) == 0) hs.add(t.top[i]); }
            if (hs.isEmpty()) continue;
            hs.sort(Integer::compare);
            int med = hs.get(hs.size() / 2), lo = Atlas.n(Atlas.arr(o, "y").get(0)), hi = Atlas.n(Atlas.arr(o, "y").get(1));
            notes.add(Atlas.s(o, "id", "?") + " median Y" + med + " (target " + lo + "-" + hi + ")");
            if (med < lo - 2 || med > hi + 2) failures.add(Atlas.s(o, "id", "?") + " stands at Y" + med + ", not " + lo + "-" + hi);
        }
        // Ashmaw's summit, the lake's surface
        for (Map<String, Object> p : a.list("peaks")) {
            double[] at = Atlas.pt(p.get("at"));
            int cx = (int) a.x(at[0]), cz = (int) a.z(at[1]), want = Atlas.i(p, "y", 0), best = 0;
            for (int dx = -24; dx <= 24; dx++) for (int dz = -24; dz <= 24; dz++) if (t.in(cx + dx, cz + dz)) best = Math.max(best, t.top[t.idx(cx + dx, cz + dz)]);
            notes.add(Atlas.s(p, "id", "?") + " top Y" + best + " (design " + want + ")");
            if (best < want - 6) failures.add(Atlas.s(p, "id", "?") + " rises only to Y" + best + ", not Y" + want);
        }
        for (Map<String, Object> l : a.list("lakes")) {
            double[] at = Atlas.pt(l.get("at"));
            int i = t.idx((int) a.x(at[0]), (int) a.z(at[1]));
            int want = Atlas.i(l, "surface", 0);
            if (t.water[i] != want && !"dimwater".equals(Atlas.s(l, "id", ""))) failures.add(Atlas.s(l, "id", "?") + "'s surface is Y" + t.water[i] + ", not Y" + want);
        }
        double[] dimwater = null;
        for (Map<String, Object> l : a.list("lakes")) if ("dimwater".equals(Atlas.s(l, "id", ""))) dimwater = Atlas.pt(l.get("at"));
        if (dimwater != null) {
            int wet = 0, at44 = 0;
            for (int x = (int) a.x(dimwater[0]) - 80; x <= a.x(dimwater[0]) + 80; x += 2) for (int z = (int) a.z(dimwater[1]) - 60; z <= a.z(dimwater[1]) + 60; z += 2) {
                int i = t.idx(x, z);
                if ((t.flags[i] & Terrain.LAKE) != 0) { wet++; if (t.water[i] == 44) at44++; }
            }
            if (wet == 0 || at44 < wet * 0.95) failures.add("Dimwater's surface is not Y44 (" + at44 + " of " + wet + ")");
        }
        // rivers run down to the sea, never up
        for (Map<String, Object> r : a.list("rivers")) {
            double prev = Double.MAX_VALUE;
            for (Object o : Atlas.arr(r, "points")) { double y = Atlas.pt(o)[2]; if (y > prev) failures.add(Atlas.s(r, "id", "?") + " runs uphill (Y" + prev + " to Y" + y + ")"); prev = y; }
            if (prev > sea) failures.add(Atlas.s(r, "id", "?") + " does not reach the sea (ends at Y" + prev + ")");
        }
        // every road walkable end to end: no step along it more than one cell
        for (Terrain.Road r : t.roads) {
            int worst = 0;
            for (int k = 3; k < r.path().length; k += 3) worst = Math.max(worst, Math.abs(r.path()[k + 2] - r.path()[k - 1]));
            if (worst > 1) failures.add(r.name() + " has a step of " + worst + " cells");
        }
        // the seven settlements and the named places reachable from Loxford Castle and back
        for (ShadowVeil.Realm realm : realmsOf()) {
            if (!near(realm.cx(), realm.cz(), 24)) failures.add(realm.name() + " cannot be walked to from Loxford Castle and back");
        }
        int lostPlaces = 0;
        for (var e : a.places().entrySet()) {
            if (!near((float) a.x(e.getValue()[0]), (float) a.z(e.getValue()[1]), 20)) { lostPlaces++; notes.add("unreachable place: " + e.getKey()); }
        }
        if (lostPlaces > 0) failures.add(lostPlaces + " named places cannot be walked to");
        // every chunk has reachable ground or water beside reachable ground
        int chunks = S / 16, lost = 0;
        for (int cx = 0; cx < chunks; cx++) for (int cz = 0; cz < chunks; cz++) {
            boolean ok = false;
            for (int x = cx * 16; x < cx * 16 + 16 && !ok; x++) for (int z = cz * 16; z < cz * 16 + 16 && !ok; z++) {
                int i = t.idx(x, z);
                if (reach[i] || swim[i]) ok = true;
                // (0.4.1) a chunk wholly under a castle or a palace is reached through its gates
                if ((t.flags[i] & Terrain.BUILT) != 0 && t.arch != null) { var st = t.arch.at(x, z); if (st != null && near(st.apron()[0], st.apron()[1], 3)) ok = true; }
            }
            if (!ok) {
                // the open sea beyond the belt counts as water reached from the shore of the chunk beside it
                boolean sea0 = true;
                for (int x = cx * 16; x < cx * 16 + 16 && sea0; x++) for (int z = cz * 16; z < cz * 16 + 16 && sea0; z++) if ((t.flags[t.idx(x, z)] & Terrain.SEA) == 0) sea0 = false;
                if (!sea0) { lost++; if (lost <= 12) notes.add("unreachable chunk [" + (cx + 1) + "," + (chunks - cz) + "]"); }
            }
        }
        if (lost > 0) failures.add(lost + " chunks have no ground a walker can reach");
        // the shore belt: dry ground within the outer two chunks reachable all the way round (a shore path)
        int beltReach = 0, beltDry = 0;
        for (int i = 0; i < t.top.length; i++) {
            int x = i / S, z = i % S, e = Math.min(Math.min(x, z), Math.min(S - 1 - x, S - 1 - z));
            if (e >= 16 && e < 40 && t.water[i] < 0) { beltDry++; if (reach[i]) beltReach++; }
        }
        notes.add("shore belt reached: " + (beltDry == 0 ? 0 : beltReach * 100 / beltDry) + "%");
        if (beltDry > 0 && beltReach < beltDry * 0.8) failures.add("only " + beltReach * 100 / beltDry + "% of the shore belt can be walked");
        // (0.4.1) the built world: what the architecture found (lots, doors, volumes), and every front reached on foot
        if (t.arch != null) {
            failures.addAll(t.arch.failures);
            notes.addAll(t.arch.notes);
            int cut = 0;
            for (Architecture.Structure s : t.arch.structures) {
                if (!s.reachable()) continue;
                int[] ap = s.apron();
                if (!near(ap[0], ap[1], 3)) { cut++; if (cut <= 20) notes.add("cut off: " + s.id + " (" + s.name + ") at " + ap[0] + "," + ap[1]); }
            }
            if (cut > 0) failures.add(cut + " structures' fronts cannot be walked to from Loxford Castle");
            // (0.4.2) every enterable building and underground place ready for its interior (the registry's checks)
            if (t.arch.registry != null) {
                int flagged = 0;
                for (Registry.Entry e : t.arch.registry.entries) if (!e.ready) { flagged++; if (flagged <= 40) notes.add("not ready: " + e.id + " (" + e.name + ", " + e.purpose + "): " + String.join("; ", e.issues)); }
                if (flagged > 0) failures.add(flagged + " structures are not ready for their interiors (see registry.md)");
                int ready = 0; for (Registry.Entry e : t.arch.registry.entries) if (e.ready && (e.enterable || e.underground)) ready++;
                notes.add(ready + " structures ready for their interiors");
            }
            notes.add(t.arch.structures.size() + " structures planned");
        }
        return this;
    }
    private List<ShadowVeil.Realm> realmsOf() {
        List<ShadowVeil.Realm> out = new ArrayList<>();
        for (Map<String, Object> r : Atlas.list(a.map("realms"), "list")) {
            double[] c = Atlas.pt(r.get("centre"));
            out.add(new ShadowVeil.Realm(Atlas.s(r, "name", "?"), new int[0][], (float) a.x(c[0]), (float) a.z(c[1])));
        }
        return out;
    }
    private boolean near(float x, float z, int r) {
        for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
            int cx = (int) x + dx, cz = (int) z + dz;
            if (t.in(cx, cz) && reach[t.idx(cx, cz)]) return true;
        }
        return false;
    }

    // ------------------------------------------------------------------ the maps
    /** Writes relief.png, height.png, realms.png and reach.png into a folder. */
    public void draw(Path dir) throws java.io.IOException {
        Files.createDirectories(dir);
        int S = t.S;
        BufferedImage relief = new BufferedImage(S, S, BufferedImage.TYPE_INT_RGB), height = new BufferedImage(S, S, BufferedImage.TYPE_INT_RGB);
        BufferedImage reachImg = new BufferedImage(S, S, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < S; x++) for (int z = 0; z < S; z++) {
            int i = t.idx(x, z);
            int c = colour(i);
            // light from the north-west
            int hx = t.top[t.idx(Math.max(0, x - 1), z)], hz = t.top[t.idx(x, Math.max(0, z - 1))];
            double shade = Math.max(0.45, Math.min(1.35, 1 + ((hx - t.top[i]) + (hz - t.top[i])) * -0.16 + (t.top[i] - 40) * 0.004));
            if (t.water[i] >= 0 && t.deck[i] < 0) shade = 1 - Math.min(0.45, (t.water[i] - t.top[i]) * 0.04);
            relief.setRGB(x, z, scale(c, shade));
            int g = Math.max(0, Math.min(255, (t.top[i] - 20) * 3));
            height.setRGB(x, z, t.water[i] >= 0 ? 0x203858 : g << 16 | g << 8 | g);
            reachImg.setRGB(x, z, reach[i] ? scale(c, shade * 0.9) : t.water[i] >= 0 ? 0x101828 : 0xB02020);
        }
        Graphics2D g = relief.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        for (Terrain.Road r : t.roads) {
            g.setColor(r.kind().equals("king") ? new Color(0xE8D8A8) : r.kind().equals("main") ? new Color(0xC8B078) : new Color(0x9A8460));
            g.setStroke(new BasicStroke(r.kind().equals("path") ? 1f : 2f));
            int[] p = r.path();
            for (int k = 3; k < p.length; k += 3) g.drawLine(p[k - 3], p[k - 2], p[k], p[k + 1]);
        }
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 11));
        for (var e : ShadowVeil.labels().entrySet()) {
            float[] p = e.getValue();
            g.setColor(new Color(0, 0, 0, 160)); g.drawString(e.getKey(), p[0] - 29, p[1] + 1);
            g.setColor(Color.WHITE); g.drawString(e.getKey(), p[0] - 30, p[1]);
        }
        g.setColor(new Color(255, 255, 255, 40));
        for (int k = 0; k <= S; k += 16) { g.drawLine(k, 0, k, S); g.drawLine(0, k, S, k); }
        g.dispose();
        BufferedImage realms = new BufferedImage(S, S, BufferedImage.TYPE_INT_RGB);
        int[] palette = {0x6A8A9A, 0x9A7A4A, 0x7A6A9A, 0x8A9A6A, 0xC8A060, 0x6A9A8A, 0xA06A6A};
        for (int x = 0; x < S; x++) for (int z = 0; z < S; z++) {
            String j = ShadowVeil.jurisdiction(x, z);
            int k = 0, c = 0x2A2A2A;
            for (ShadowVeil.Realm r : ShadowVeil.realms()) { if (r.name().equals(j)) c = palette[k % palette.length]; k++; }
            int i = t.idx(x, z);
            realms.setRGB(x, z, t.water[i] >= 0 ? 0x182838 : scale(c, 0.7 + Math.min(0.5, (t.top[i] - 30) * 0.008)));
        }
        javax.imageio.ImageIO.write(relief, "png", dir.resolve("relief.png").toFile());
        // (0.4.1) each settlement close up (four pixels a cell): the lots, their doors, the streets and lanes
        for (Map<String, Object> r : Atlas.list(a.map("realms"), "list")) {
            int[] box = a.cells(Atlas.arr(r, "rects").isEmpty() ? List.of(1, 1, 1, 1) : (List<?>) Atlas.arr(r, "rects").get(0));
            int x0 = Math.max(0, box[0] - 24), z0 = Math.max(0, box[1] - 24), x1 = Math.min(S - 1, box[2] + 24), z1 = Math.min(S - 1, box[3] + 24);
            int k = 4;
            BufferedImage town = new BufferedImage((x1 - x0 + 1) * k, (z1 - z0 + 1) * k, BufferedImage.TYPE_INT_RGB);
            Graphics2D tg = town.createGraphics();
            tg.drawImage(relief.getSubimage(x0, z0, x1 - x0 + 1, z1 - z0 + 1), 0, 0, town.getWidth(), town.getHeight(), null);
            if (t.arch != null) for (Architecture.Structure s : t.arch.structures) {
                if (s.x1 < x0 || s.x0 > x1 || s.z1 < z0 || s.z0 > z1) continue;
                tg.setColor(new Color(255, 230, 160, 200));
                tg.drawRect((s.x0 - x0) * k, (s.z0 - z0) * k, (s.x1 - s.x0 + 1) * k - 1, (s.z1 - s.z0 + 1) * k - 1);
                int[] ap = s.apron();
                tg.setColor(new Color(80, 220, 255));
                tg.fillRect((ap[0] - x0) * k, (ap[1] - z0) * k, k, k);
            }
            tg.dispose();
            javax.imageio.ImageIO.write(town, "png", dir.resolve("town-" + Atlas.s(r, "name", "x").toLowerCase() + ".png").toFile());
        }
        javax.imageio.ImageIO.write(height, "png", dir.resolve("height.png").toFile());
        javax.imageio.ImageIO.write(realms, "png", dir.resolve("realms.png").toFile());
        javax.imageio.ImageIO.write(reachImg, "png", dir.resolve("reach.png").toFile());
        StringBuilder report = new StringBuilder("ShadowVeil survey\n");
        for (String n : notes) report.append("  ").append(n).append('\n');
        report.append(failures.isEmpty() ? "PASSED\n" : "FAILED\n");
        for (String f : failures) report.append("  ! ").append(f).append('\n');
        report.append("timings ").append(t.timings).append('\n');
        Files.writeString(dir.resolve("survey.txt"), report);
    }
    private int colour(int i) {
        int f = t.flags[i];
        if ((f & Terrain.BUILT) != 0 && (f & Terrain.ROAD) == 0) return 0x6E4A3C;
        if ((f & Terrain.LANE) != 0) return 0xA89070;
        if ((f & Terrain.BRIDGE) != 0) return 0xB8A070;
        if ((f & Terrain.ROAD) != 0) return 0xC0A878;
        if (t.water[i] >= 0) return (f & Terrain.SEA) != 0 ? 0x2A5A84 : 0x3A72A0;
        if ((f & Terrain.TREE) != 0) return 0x2A5022;
        if ((f & Terrain.CLIFF) != 0) return 0x8A8278;
        return switch (t.biomes.get(t.biome[i])) {
            case "desert", "desert_town", "beach" -> 0xD8C080;
            case "sandstone" -> 0xC89060;
            case "claypits" -> 0xB87E5C;
            case "volcanic" -> 0x5A5654;
            case "deadwood" -> 0x4E3E2A;
            case "heath" -> 0x6A5E40;
            case "marches" -> 0x8E8A50;
            case "crags" -> t.top[i] > 70 ? 0x8C8880 : 0x6A7A54;
            case "marsh" -> 0x4E5A38;
            case "chalk" -> 0xD8D8C8;
            case "oasis" -> 0x5E9A40;
            case "oakwood", "cypress" -> 0x3E7030;
            default -> 0x6A9A44;
        };
    }
    private static int scale(int c, double k) {
        int r = (int) Math.min(255, ((c >> 16) & 255) * k), g = (int) Math.min(255, ((c >> 8) & 255) * k), b = (int) Math.min(255, (c & 255) * k);
        return r << 16 | g << 8 | b;
    }

    /** gradlew shadowveil: grows the world from the design (shadowveil.json beside the game if there is one), checks it and draws it. */
    public static void main(String[] args) throws Exception {
        Path out = Path.of(args.length > 0 ? args[0] : "build/reports/shadowveil");
        long at = System.nanoTime();
        // (0.4.1) grown whole, cells and all, so the built world's doors and volumes are checked too
        Terrain t = Terrain.generate(Atlas.get(), true);
        t.cells = null;
        ShadowVeil.prime(t);
        Survey s = new Survey(t).check();
        s.draw(out);
        // (0.4.2) the building registry and its report beside the maps
        if (t.arch != null && t.arch.registry != null) {
            java.nio.file.Files.writeString(out.resolve("registry.json"), t.arch.registry.json());
            java.nio.file.Files.writeString(out.resolve("registry.md"), t.arch.registry.report());
        }
        System.out.printf("ShadowVeil grown and surveyed in %.1fs -> %s%n", (System.nanoTime() - at) / 1e9, out.toAbsolutePath());
        for (String n : s.notes) System.out.println("  " + n);
        for (String f : s.failures) System.out.println("  ! " + f);
        System.out.println(s.failures.isEmpty() ? "PASSED" : "FAILED (" + s.failures.size() + ")");
        if (!s.failures.isEmpty()) System.exit(1);
    }
}
