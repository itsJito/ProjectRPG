package rpg;

import astratto.bridge.NativeRenderer;
import astratto.game.Game;
import astratto.platform.Window;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import rpg.render.Gfx;
import rpg.ui.Hud;
import rpg.ui.Ui2D;
import rpg.world.DevMap;

/**
 * The frame-time benchmark ({@code ./gradlew bench}): the devmap as it is played - the same tick, the same drawing, the
 * same swap, vsync off - from a fixed tour of places and hours (the town street with its people, the grove, the pond,
 * the campfire at night, a sprint down the street). Each stop is settled, then measured frame by frame: the whole frame,
 * the simulation (the game's modules and the engine's frame), the scene built and submitted, the renderer's own CPU, the
 * GPU (by pass, read back from timer queries a few frames late), and the swap. Reports the mean, the 99th percentile and
 * the worst, and how many frames missed 60 and 144 per second. {@code RPG_BENCH_FRAMES} sets frames a stop (default 400).
 */
final class Bench {
    private final Window win;
    private final Game game;
    private final Gfx gfx;
    private final Ui2D ui;

    Bench(Window win, Game game, Gfx gfx, Ui2D ui) { this.win = win; this.game = game; this.gfx = gfx; this.ui = ui; }

    /** A stop: where the player stands and looks, the hour, whether they sprint forward through it. */
    record Stop(String name, float hour, float x, float z, float tx, float tz, boolean run) {}

    private static final Stop[] TOUR = {
            new Stop("street, morning (people)", 9f, 64.5f, 76f, 70f, 58f, false),
            new Stop("farm, livestock", 12f, 110f, 108f, 118f, 115f, false),
            new Stop("grove, noon (leaves)", 12f, 90f, 26f, 96.5f, 18.5f, false),
            new Stop("pond, evening (reflections)", 17.5f, 88f, 45f, 96f, 34f, false),
            new Stop("campfire, night (smoke, lamps)", 21f, 87.5f, 26.5f, 91.5f, 21.5f, false),
            new Stop("sprint down the street", 15f, 60f, 90f, 60f, 40f, true),
            // gamefixes: combat-heavy scenes lagged - a dozen monsters on the watch and the townsfolk, in the yard
            new Stop("battle in the yard (monsters, the watch)", 13f, 96f, 74f, 104f, 66f, false),
    };

    /** The battle stop's fight: goblins and orcs set upon the yard, where the watch stands; the player out of reach. */
    private void battle(Rpg g, Stop s) {
        g.player.skills.setLevel(rpg.skill.Skill.HEALTH, 99); g.player.restore();
        for (int i = 0; i < 12; i++) {
            var kind = i % 3 == 0 ? rpg.monster.MonsterKind.ORC : rpg.monster.MonsterKind.GOBLIN;
            float a = i * 0.52f;
            var m = g.monsters.spawn(kind, kind == rpg.monster.MonsterKind.ORC ? 25 : 10, s.tx + (float) Math.sin(a) * 4, s.tz + (float) Math.cos(a) * 4, null);
            m.hostileToPlayer = true;
        }
        for (var n : g.npcs) if (n.watch() && n.loaded) { g.teleport(n, s.tx + (n.id % 5) - 2, DevMap.FLOOR, s.tz + 2); n.nextThink = 0; }
    }

    // a sampler: the main thread's stack every 2 ms while a frame runs; a slow frame prints where its time went
    private final java.util.List<StackTraceElement[]> samples = java.util.Collections.synchronizedList(new ArrayList<>());
    private volatile boolean sampling;

    private void startSampler(Thread main) {
        Thread t = new Thread(() -> {
            while (true) {
                if (sampling) samples.add(main.getStackTrace());
                try { Thread.sleep(2); } catch (InterruptedException e) { return; }
            }
        }, "bench-sampler");
        t.setDaemon(true);
        t.start();
    }

    private void printHotspots() {
        java.util.Map<String, Integer> count = new java.util.HashMap<>();
        synchronized (samples) {
            for (StackTraceElement[] st : samples) {
                // the deepest frames that are the game's own (rpg.*): where the game was, not the JDK under it
                StringBuilder b = new StringBuilder();
                int n = 0;
                for (StackTraceElement e : st) {
                    if (!e.getClassName().startsWith("rpg.") && n == 0) continue;
                    b.append(e.getClassName().replace("rpg.", "")).append('.').append(e.getMethodName()).append(':').append(e.getLineNumber()).append(" < ");
                    if (++n == 4) break;
                }
                count.merge(b.toString(), 1, Integer::sum);
            }
        }
        count.entrySet().stream().sorted((x, y) -> y.getValue() - x.getValue()).limit(3)
                .forEach(e -> System.out.println("      " + e.getValue() + " x " + e.getKey()));
    }

    int run() {
        startSampler(Thread.currentThread());
        int frames = Integer.parseInt(System.getenv().getOrDefault("RPG_BENCH_FRAMES", "400"));
        // RPG_BENCH_FULLSCREEN=1: the whole monitor at its own resolution and refresh rate, as a player would have it
        if (System.getenv("RPG_BENCH_FULLSCREEN") != null) { win.setFullscreen(true); for (int i = 0; i < 10; i++) { win.poll(); win.swap(); } }
        Rpg g = game.add(new Rpg(game, "Bench", Main.defaultLook(), false));
        Hud hud = new Hud(g, gfx);
        g.ui = hud;
        gfx.findLamps(g.vox);
        gfx.preload(g.npcs); gfx.preload(java.util.List.of(g.player));
        g.cfg.targetFps = Integer.parseInt(System.getenv().getOrDefault("RPG_BENCH_TARGET", "60"));
        g.cfg.quality = Integer.parseInt(System.getenv().getOrDefault("RPG_BENCH_QUALITY", Integer.toString(g.cfg.quality)));
        System.out.printf("bench: %d x %d, %d frames a stop, %s%n", win.width, win.height, frames, Runtime.version());
        List<double[]> all = new ArrayList<>();
        for (Stop s : TOUR) {
            String only = System.getenv("RPG_BENCH_ONLY");
            if (only != null && !s.name.contains(only)) continue;
            g.teleport(g.player, s.x, DevMap.FLOOR, s.z);
            g.controller.snapTo((float) Math.atan2(-(s.tx - s.x), -(s.tz - s.z)));
            g.controller.camPitch = -0.08f;
            double now = g.state.hourOfDay();
            g.advanceTime(((s.hour - now) % 24 + 24) % 24 * 60, "bench");
            // settle: meshing, probes and exposure catch up (a real arrival would have streamed them on the way)
            if (s.name.startsWith("battle")) battle(g, s);
            for (int i = 0; i < Integer.parseInt(System.getenv().getOrDefault("RPG_BENCH_SETTLE", "180")); i++) frame(g, hud, s, null);
            // RPG_BENCH_SHOTS=<dir>: a full-size picture of each stop, settled (to compare render scales and passes)
            String shots = System.getenv("RPG_BENCH_SHOTS");
            if (shots != null) Main.shot(gfx, win.width, win.height, new java.io.File(shots, "bench-" + s.name.replaceAll("[^a-z]+", "-") + ".png"));
            double[][] rows = new double[frames][];
            int gcBefore = gcCount();
            for (int i = 0; i < frames; i++) {
                int gc0 = gcCount();
                samples.clear(); sampling = true;
                rows[i] = frame(g, hud, s, new double[8 + NativeRenderer.GPU_PASSES]);
                sampling = false;
                double[] r = rows[i];
                // a hitch: what took the time
                if (r[0] > 25) System.out.printf("  hitch at frame %d: %.1f ms = sim %.1f + draw %.1f (renderer cpu %.1f) + swap %.1f; gpu %.1f%s%n",
                        i, r[0], r[1], r[2], r[3], r[5], r[4], gcCount() > gc0 ? " (a gc ran)" : "");
                if (r[0] > 25 && r[3] < r[2] * 0.5) printHotspots();
            }
            if (gcCount() > gcBefore) System.out.printf("  %d gc in the stop%n", gcCount() - gcBefore);
            report(s.name + (g.cfg.fixedScale() > 0 ? "" : String.format(" [scale %.2f, aiming %d fps]", gfx.renderScaleNow(), g.cfg.targetFps)), rows);
            all.addAll(Arrays.asList(rows));
            g.controller.in.forward = 0; g.controller.in.sprint = false;
        }
        report("ALL", all.toArray(new double[0][]));
        Runtime rt = Runtime.getRuntime();
        System.out.printf("java heap: %.0f MB used of %.0f MB; gc: %s%n", (rt.totalMemory() - rt.freeMemory()) / 1048576.0, rt.totalMemory() / 1048576.0, gcLine());
        return 0;
    }

    /** One frame of play; with a row, its times: total, sim, draw, render cpu, gpu, swap, quads, models, then gpu by pass. */
    private double[] frame(Rpg g, Hud hud, Stop s, double[] row) {
        long t0 = System.nanoTime();
        win.poll();
        if (s.run) { g.controller.in.forward = 1; g.controller.in.sprint = true; g.player.stamina = g.player.maxStamina(); }
        game.tick(1 / 60f);
        long t1 = System.nanoTime();
        var c = g.controller;
        gfx.camera(c.cx, c.cy, c.cz, c.fx, c.fy, c.fz, win.width, win.height, 1 / 60f);
        gfx.scene(g, g.player.firstPerson, 1 / 60f, g.state.hourOfDay());
        ui.begin(gfx.cmd);
        hud.draw(ui, win.width, win.height, false, 1 / 60f);
        ui.end();
        NativeRenderer.Stats st = gfx.frame(win.width, win.height);
        long t2 = System.nanoTime();
        win.swap();
        long t3 = System.nanoTime();
        if (row == null) return null;
        row[0] = (t3 - t0) / 1e6; row[1] = (t1 - t0) / 1e6; row[2] = (t2 - t1) / 1e6; row[3] = st == null ? 0 : st.cpuMs;
        row[4] = st == null ? 0 : st.gpuMs; row[5] = (t3 - t2) / 1e6; row[6] = st == null ? 0 : st.quadsDrawn; row[7] = st == null ? 0 : st.modelsDrawn;
        for (int i = 0; i < NativeRenderer.GPU_PASSES; i++) row[8 + i] = st == null ? 0 : st.passGpuMs[i];
        return row;
    }

    private static void report(String name, double[][] rows) {
        int n = rows.length;
        double[] total = new double[n];
        for (int i = 0; i < n; i++) total[i] = rows[i][0];
        double[] sorted = total.clone();
        Arrays.sort(sorted);
        int over60 = 0, over144 = 0;
        for (double t : total) { if (t > 1000 / 60.0) over60++; if (t > 1000 / 144.0) over144++; }
        double[] mean = new double[rows[0].length];
        for (double[] r : rows) for (int k = 0; k < r.length; k++) mean[k] += r[k] / n;
        System.out.printf("%n== %s%n", name);
        System.out.printf("  frame  mean %6.2f ms (%5.0f fps)   p50 %6.2f   p99 %6.2f   worst %6.2f   over 16.7 ms: %d/%d   over 6.9 ms: %d/%d%n",
                mean[0], 1000 / mean[0], sorted[n / 2], sorted[Math.min(n - 1, (int) (n * 0.99))], sorted[n - 1], over60, n, over144, n);
        System.out.printf("  sim %6.2f   draw %6.2f (renderer cpu %6.2f)   swap %6.2f   gpu %6.2f ms   quads %.0f  models %.0f%n",
                mean[1], mean[2], mean[3], mean[5], mean[4], mean[6], mean[7]);
        StringBuilder b = new StringBuilder("  gpu by pass:");
        for (int i = 0; i < NativeRenderer.GPU_PASSES; i++) b.append(String.format(" %s %.2f", NativeRenderer.GPU_PASS_NAMES[i], mean[8 + i]));
        System.out.println(b);
        b = new StringBuilder("  gpu worst:  ");
        for (int i = 0; i < NativeRenderer.GPU_PASSES; i++) { double w = 0; for (double[] r : rows) w = Math.max(w, r[8 + i]); b.append(String.format(" %s %.2f", NativeRenderer.GPU_PASS_NAMES[i], w)); }
        double gw = 0; for (double[] r : rows) gw = Math.max(gw, r[4]);
        System.out.println(b.append(String.format("  (frame %.2f)", gw)));
    }

    private static int gcCount() {
        int n = 0;
        for (var gc : java.lang.management.ManagementFactory.getGarbageCollectorMXBeans()) n += (int) gc.getCollectionCount();
        return n;
    }

    private static String gcLine() {
        StringBuilder b = new StringBuilder();
        for (var gc : java.lang.management.ManagementFactory.getGarbageCollectorMXBeans()) b.append(gc.getName()).append(' ').append(gc.getCollectionCount()).append("x/").append(gc.getCollectionTime()).append("ms ");
        return b.toString().trim();
    }
}
