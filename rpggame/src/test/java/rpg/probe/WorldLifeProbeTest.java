package rpg.probe;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import rpg.Rpg;
import rpg.actor.Appearance;
import rpg.ai.Brain;
import rpg.ai.Npc;
import rpg.world.WorldMap;
import rpg.world.shadowveil.ShadowVeil;

/**
 * (0.4.6, gamefixes: NPC AI must work 100%; AI spun in circles round altars; NPCs stuck short of their routines; spawn
 * points clustered) Every peopled part of ShadowVeil in turn: the player is stood among each group of homes and the world
 * runs a while; each person in play is watched for turning on the spot (a full turn and a half in five seconds without
 * going two metres), for standing blocked, and for standing still on an errand. Also prints how clustered everyone
 * stands the moment the world begins. {@code gradlew probe -PprobeClass=*WorldLife* -Prpg.probeFrames=3600}
 * (frames a stop; -Prpg.probeWho="x,z;x,z" picks the stops).
 */
@Tag("probe")
class WorldLifeProbeTest {
    @Test void life() {
        int frames = Integer.getInteger("rpg.probeFrames", 3600);
        java.util.Set<String> trace = new java.util.HashSet<>(java.util.Arrays.asList(System.getProperty("rpg.probeTrace", "").split(",")));
        trace.remove("");
        var cfg = new NativeEngine.Config(); cfg.deterministic = true; cfg.audioDevice = false;
        Game game = Game.create(cfg);
        try {
            game.activate(ShadowVeil.build(game.engine()));
            Rpg g = game.add(new Rpg(game, "world life", new Appearance(), true));
            g.player.skills.setLevel(rpg.skill.Skill.HEALTH, 99); g.player.restore();
            game.tick(0);
            // the clusters at the start: anyone with three or more others within two metres
            int crowded = 0;
            StringBuilder cl = new StringBuilder();
            for (Npc n : g.npcs) {
                int near = 0;
                for (Npc o : g.npcs) if (o != n && Math.hypot(o.x - n.x, o.z - n.z) < 2 && Math.abs(o.y - n.y) < 2) near++;
                if (near >= 3) { crowded++; if (cl.length() < 4000) cl.append(String.format("   %s %s (%.1f,%.1f,%.1f) %d near goal %s%n", n.name, n.role, n.x, n.y, n.z, near, n.goal)); }
            }
            System.out.println("[life] start: " + g.npcs.size() + " people, " + crowded + " stand with 3+ others within 2 m\n" + cl);
            g.state.minutes = Integer.getInteger("rpg.probeLeg", 6 * 60 + 20);
            List<float[]> stops = new ArrayList<>();
            String who = System.getProperty("rpg.probeWho", "");
            if (!who.isBlank()) for (String p : who.split(";")) { String[] v = p.trim().split("[, ]+"); stops.add(new float[]{Float.parseFloat(v[0]), Float.parseFloat(v[1])}); }
            else {
                // greedy cover of everyone's home: a stop at the first uncovered home, covering all within 110 m
                List<Npc> left = new ArrayList<>(g.npcs);
                while (!left.isEmpty()) {
                    Npc a = left.get(0);
                    float sx = 0, sz = 0; int k = 0;
                    for (Npc o : left) if (Math.hypot(o.homeX - a.homeX, o.homeZ - a.homeZ) < 110) { sx += o.homeX; sz += o.homeZ; k++; }
                    float cx = sx / k, cz = sz / k;
                    stops.add(new float[]{cx, cz});
                    left.removeIf(o -> Math.hypot(o.homeX - cx, o.homeZ - cz) < 120);
                }
            }
            StringBuilder plan = new StringBuilder("[life] stops:");
            for (float[] s : stops) plan.append(String.format(" %.0f,%.0f;", s[0], s[1]));
            System.out.println(plan);
            Map<Long, Integer> spins = new HashMap<>(), blocked = new HashMap<>(), still = new HashMap<>(), seen = new HashMap<>();
            Map<Long, String> spinAt = new HashMap<>(), lastWhy = new HashMap<>();
            for (float[] s : stops) {
                float y = ShadowVeil.standY(s[0], s[1]);
                g.teleport(g.player, s[0], y, s[1]);
                Map<Long, float[]> hist = new HashMap<>();   // x, z, yawSum, frames, and the window's start
                Map<Long, float[]> last = new HashMap<>();
                Map<Long, StringBuilder> history = new HashMap<>();
                for (int f = 0; f < frames; f++) {
                    game.tick(1f / 60);
                    g.player.restore(); g.player.bloodied = 0; if (g.gore != null) g.gore.healed(g.player);
                    if (!trace.isEmpty() && f % 10 == 0) for (Npc n : g.npcs) if (trace.contains(n.name) && n.loaded)
                        System.out.printf("[trace] %5d %-10s (%.2f,%.2f,%.2f) yaw %.2f want %.2f v %.2f,%.2f goal %s(%s) act %s working %s gp %s %.1f,%.1f,%.1f legs %s st %d nu %d ss %.1f na %.1f t %.1f | %s%n", f, n.name, n.x, n.y, n.z, n.yaw, n.wantYaw, n.vx, n.vz, n.goal, n.goalReason, n.nativeAction, n.working, n.hasGoalPos, n.gx, n.goalY, n.gz, n.legs == null ? "-" : n.legAt + "/" + n.legs.length, n.stalls, n.nudges, n.stallSince, n.nudgeAt, g.time, n.lastOutcome + doors(g, n));
                    if (f % 6 != 0 || f < 120) continue;
                    for (Npc n : g.npcs) {
                        if (!n.alive || !n.loaded || Math.hypot(n.x - g.player.x, n.z - g.player.z) > 100) continue;
                        seen.merge(n.id, 1, Integer::sum);
                        float[] h = hist.get(n.id), p = last.get(n.id);
                        if (h == null || p == null) { hist.put(n.id, new float[]{n.x, n.z, 0, 0}); last.put(n.id, new float[]{n.x, n.z, n.yaw}); continue; }
                        float dy = Math.abs(wrap(n.yaw - p[2]));
                        StringBuilder rec = history.computeIfAbsent(n.id, k -> new StringBuilder());
                        rec.append(String.format(" [%.2f %.1f,%.1f %s/%s %s]", n.yaw, n.x, n.z, n.goal, n.nativeAction, n.lastOutcome == null ? "" : n.lastOutcome.length() > 30 ? n.lastOutcome.substring(0, 30) : n.lastOutcome));
                        if (rec.length() > 6000) rec.delete(0, rec.length() - 6000);
                        h[2] += dy; h[3] += 6;
                        if (h[3] >= 300) {
                            float moved = (float) Math.hypot(n.x - h[0], n.z - h[1]);
                            if (h[2] > Math.PI * 3 && moved < 2 && n.goal != Brain.Goal.FIGHT) {
                                if (spins.getOrDefault(n.id, 0) == 0) System.out.println("[spin] " + n.name + ":" + history.get(n.id));
                                spins.merge(n.id, 1, Integer::sum);
                                spinAt.put(n.id, String.format("(%.1f,%.1f,%.1f) %s: %s", n.x, n.y, n.z, n.goal, n.lastOutcome));
                            }
                            hist.put(n.id, new float[]{n.x, n.z, 0, 0});
                        }
                        String o = n.lastOutcome == null ? "" : n.lastOutcome;
                        if (o.contains("blocked") || o.contains("replanning") || o.contains("no route") || o.contains("locked") || n.stuck) { blocked.merge(n.id, 1, Integer::sum); lastWhy.put(n.id, String.format("(%.1f,%.1f,%.1f) %s: %s", n.x, n.y, n.z, n.goal, o)); }
                        if (Math.hypot(p[0] - n.x, p[1] - n.z) < 0.02 && (n.goal == Brain.Goal.RETURN || n.goal == Brain.Goal.PRAY && o.startsWith("on the way") || o.startsWith("on the way") || o.startsWith("heading")))
                            { still.merge(n.id, 1, Integer::sum); if (!lastWhy.containsKey(n.id)) lastWhy.put(n.id, String.format("(%.1f,%.1f,%.1f) %s: %s", n.x, n.y, n.z, n.goal, o)); }
                        last.put(n.id, new float[]{n.x, n.z, n.yaw});
                    }
                }
                System.out.printf("[life] stop %.0f,%.0f done (%d people seen so far)%n", s[0], s[1], seen.size());
            }
            StringBuilder b = new StringBuilder("\n[life] " + stops.size() + " stops of " + frames + " frames; " + seen.size() + " people seen\n");
            int bad = 0;
            for (Npc n : g.npcs) {
                int sp = spins.getOrDefault(n.id, 0), bl = blocked.getOrDefault(n.id, 0), st = still.getOrDefault(n.id, 0), se = seen.getOrDefault(n.id, 0);
                if (se == 0 || sp == 0 && bl * 20 < se && st * 10 < se) continue;
                bad++;
                b.append(String.format("  %-16s %-12s seen %5d spins %3d blocked %5d still %5d%n", n.name, n.role, se, sp, bl, st));
                if (sp > 0) b.append("      spun at ").append(spinAt.get(n.id)).append('\n');
                if (lastWhy.containsKey(n.id)) b.append("      last ").append(lastWhy.get(n.id)).append('\n');
            }
            for (Npc n : g.npcs) if (seen.getOrDefault(n.id, 0) == 0) b.append("  never in play: ").append(n.name).append(' ').append(n.role).append(String.format(" home (%.0f,%.0f)%n", n.homeX, n.homeZ));
            b.append("[life] ").append(bad).append(" people with trouble\n");
            System.out.println(b);
        } finally { game.close(); WorldMap.activateDevmap(); }
    }

    static String doors(Rpg g, Npc n) {
        StringBuilder b = new StringBuilder();
        for (var o : g.objects.near(n.x, n.z, 2)) if (o instanceof rpg.objects.Door d) b.append(String.format(" [door %s open %s locked %s blocked %s angle %s hinge %s]", d.label(), d.open, d.locked, d.blocked(g), java.util.Arrays.toString(d.angle), java.util.Arrays.deepToString(d.hingeAt)));
        return b.toString();
    }

    static float wrap(float a) { while (a > Math.PI) a -= (float) (2 * Math.PI); while (a < -Math.PI) a += (float) (2 * Math.PI); return a; }
}
