package rpg.world;

import static org.junit.jupiter.api.Assertions.assertTrue;

import astratto.bridge.AstrattoNative;
import astratto.bridge.NativeEngine;
import astratto.game.Game;
import astratto.game.GameContext;
import astratto.game.GameModule;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import rpg.Rpg;
import rpg.actor.Appearance;
import rpg.actor.Body;
import rpg.world.shadowveil.ShadowVeil;

/**
 * (0.4.5, gamefixes: most stairs kept the player from going fully up or down) Every staircase fitted in ShadowVeil's
 * buildings is walked by a body of the player's own size and step, the way a player walks it - from its foot up each
 * tread to its head, then back down - with the world's furniture, doors and NPCs in place. A flight it cannot finish
 * (stuck, or turned back by a beam, a rail or a piece of furniture) fails the test, with where it stopped.
 */
@Tag("slow")
class StairWalkTest {
    @Test @org.junit.jupiter.api.Timeout(value = 45, unit = java.util.concurrent.TimeUnit.MINUTES) void everyStaircaseIsWalkedUpAndDown() {
        var cfg = new NativeEngine.Config(); cfg.deterministic = true; cfg.audioDevice = false;
        List<String> failed = new ArrayList<>();
        int[] walked = {0};
        try (Game game = Game.create(cfg)) {
            game.activate(ShadowVeil.build(game.engine()));
            // the world as it loads (its furniture, doors, people), then held still: only the walker moves
            Rpg g = new Rpg(game, "stairs", new Appearance(), true);
            Gate gate = game.add(new Gate(g));
            game.tick(0);
            game.tick(1 / 60f);
            gate.live = false;
            List<int[][]> flights = new ArrayList<>();
            List<String> owners = new ArrayList<>();
            String only = System.getenv("RPG_STAIRS");
            for (var f : ShadowVeil.terrain().arch.fitted.values()) for (int[][] w : f.flights) {
                if (only != null && !java.util.Arrays.asList(only.split(",")).contains(f.id)) continue;
                flights.add(w); owners.add(f.id);
            }
            Walker w = game.add(new Walker());
            game.tick(1 / 60f);
            for (int i = 0; i < flights.size(); i++) {
                int[][] way = flights.get(i);
                for (int dir = 0; dir < 2; dir++) {
                    List<float[]> pts = new ArrayList<>();
                    for (int k = 0; k < way.length; k++) {
                        int[] q = way[dir == 0 ? k : way.length - 1 - k];
                        boolean step = (dir == 0 ? k : way.length - 1 - k) > 0 && (dir == 0 ? k : way.length - 1 - k) < way.length - 1;
                        pts.add(new float[]{q[0] + 0.5f, step ? q[1] + 1 : q[1], q[2] + 0.5f});
                    }
                    String why = w.walk(game, pts);
                    walked[0]++;
                    if (why != null) { failed.add(owners.get(i) + " " + (dir == 0 ? "up" : "down") + " " + way[0][0] + "," + way[0][1] + "," + way[0][2] + ": " + why); System.out.println("[stairs] FAIL " + failed.get(failed.size() - 1));
                        if (only != null) { StringBuilder b = new StringBuilder("[stairs] way:"); for (int[] q : way) b.append(" ").append(q[0]).append(",").append(q[1]).append(",").append(q[2]); System.out.println(b);
                            int cx = (int) Math.floor(w.x), cz = (int) Math.floor(w.z), cy = (int) Math.floor(w.y);
                            for (int y = cy + 3; y >= cy - 1; y--) { StringBuilder r = new StringBuilder("[stairs] y" + y + ":"); for (int z = cz - 2; z <= cz + 2; z++) { r.append("  "); for (int x = cx - 2; x <= cx + 2; x++) { int bl = g.vox.block(x, y, z); r.append(bl == Blocks.AIR ? '.' : bl == Blocks.STAIRS ? 'S' : g.vox.solid(x, y, z) ? '#' : 'o'); } } System.out.println(r + "   (x " + (cx - 2) + ".." + (cx + 2) + ", z " + (cz - 2) + ".." + (cz + 2) + " left to right blocks)"); } } }
                    if (walked[0] % 200 == 0) System.out.println("[stairs] " + walked[0] + " of " + flights.size() * 2 + " walked, " + failed.size() + " failed");
                }
            }
        } finally { WorldMap.activateDevmap(); }
        System.out.println("[stairs] " + walked[0] + " walks, " + failed.size() + " failed");
        for (String f : failed) System.out.println("[stairs] " + f);
        assertTrue(failed.isEmpty(), failed.size() + " of " + walked[0] + " stair walks failed: " + String.join("; ", failed.subList(0, Math.min(12, failed.size()))));
    }

    /** The game's own module, run while the world loads and then stopped. */
    static final class Gate implements GameModule {
        final Rpg g;
        boolean live = true;
        Gate(Rpg g) { this.g = g; }
        @Override public void init(GameContext ctx) { g.init(ctx); }
        @Override public void update(GameContext ctx, float dt) { if (live) g.update(ctx, dt); }
        @Override public void afterFrame(GameContext ctx) { if (live) g.afterFrame(ctx); }
    }

    /** A player-sized body walked along points: one input a tick toward the next point at a jog. */
    static final class Walker implements GameModule {
        long body;
        float vx, vz;
        float x, y, z;
        @Override public void init(GameContext ctx) {
            body = ctx.reserve(AstrattoNative.KIND_MOVER);
            ctx.commands().moverCreate(body, 0, 80, 0, Body.MALE.radius, Body.MALE.height, 0.6f, 0, Body.MALE.height * Body.CROUCH_HEIGHT);
        }
        @Override public void update(GameContext ctx, float dt) { ctx.commands().moverInput(body, vx, vz, 0, 0, 80, 0); }
        @Override public void afterFrame(GameContext ctx) { var m = ctx.bodies().mover(body); if (m != null) { x = m.x; y = m.y; z = m.z; } }

        String walk(Game game, List<float[]> pts) {
            float[] a = pts.get(0);
            vx = 0; vz = 0;
            game.commands().moverPlace(body, a[0], a[1] + 0.05f, a[2], 0, 0, 0);
            for (int i = 0; i < 8; i++) game.tick(1 / 30f);
            if (Math.hypot(x - a[0], z - a[2]) > 0.3 || Math.abs(y - a[1]) > 0.5) return String.format("foot does not hold a body (put at %.1f,%.1f,%.1f, is at %.2f,%.2f,%.2f)", a[0], a[1], a[2], x, y, z);
            for (int k = 1; k < pts.size(); k++) {
                float[] p = pts.get(k);
                int t = 0;
                while (Math.hypot(x - p[0], z - p[2]) > 0.22 && t++ < 75) {
                    double d = Math.hypot(p[0] - x, p[2] - z);
                    float sp = (float) Math.min(4.6, d * 5);
                    vx = (float) ((p[0] - x) / d * sp); vz = (float) ((p[2] - z) / d * sp);
                    game.tick(1 / 30f);
                }
                if (t >= 75) { vx = vz = 0; return String.format("stuck before point %d of %d (%.1f,%.1f,%.1f) at %.2f,%.2f,%.2f", k, pts.size() - 1, p[0], p[1], p[2], x, y, z); }
            }
            vx = vz = 0;
            for (int i = 0; i < 5; i++) game.tick(1 / 30f);
            float[] e = pts.get(pts.size() - 1);
            if (Math.abs(y - e[1]) > 0.4) return String.format("ended at height %.2f, not %.1f", y, e[1]);
            return null;
        }
    }
}
