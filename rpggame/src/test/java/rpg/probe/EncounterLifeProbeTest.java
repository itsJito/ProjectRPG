package rpg.probe;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import rpg.Rpg;
import rpg.actor.Actor;
import rpg.actor.Appearance;
import rpg.ai.Npc;
import rpg.encounter.Encounters;
import rpg.world.WorldMap;
import rpg.world.shadowveil.Atlas;
import rpg.world.shadowveil.ShadowVeil;

/**
 * (0.4.8, gamefixes: random events spawned in awkward places right in front of the player, their people never walking)
 * every encounter forced in turn on a road of the wilds: where each of its people comes in (how far off, whether the
 * player can see the spot), and over the next twenty seconds how far each one goes, what they are about, and whether
 * they stand stuck. {@code gradlew probe -PprobeClass=*EncounterLife*} (-Prpg.probeWho="id,id" picks encounters).
 */
@Tag("probe")
class EncounterLifeProbeTest {
    @Test void everyEncounterComesInUnseenAndMoves() {
        var cfg = new NativeEngine.Config(); cfg.deterministic = true; cfg.audioDevice = false;
        Game game = Game.create(cfg);
        try {
            game.activate(ShadowVeil.build(game.engine()));
            Rpg g = game.add(new Rpg(game, "encounters", new Appearance(), true));
            game.tick(0);
            String only = System.getProperty("rpg.probeWho", "");
            int frames = Integer.getInteger("rpg.probeFrames", 1200);
            String[] regions = {"marches", "heath", "highland", "upland", "desert"};
            int k = 0, troubled = 0, seen = 0, close = 0, stuck = 0;
            for (var d : g.encounters.definitions().values()) {
                String id = String.valueOf(d.getOrDefault("id", "?"));
                if (!only.isEmpty() && !List.of(only.split(",")).contains(id)) continue;
                g.encounters.console(new String[]{"encounter", "clear"});
                placeOnRoad(g, regions[k++ % regions.length]);
                for (int i = 0; i < 30; i++) game.tick(1 / 60f);
                String res = g.encounters.console(new String[]{"encounter", "force", id});
                System.out.println("\n[enc] " + id + ": " + res + " (player " + Math.round(g.player.x) + "," + Math.round(g.player.z) + ")");
                if (!res.startsWith("started")) continue;
                var in = g.encounters.active.get(g.encounters.active.size() - 1);
                java.util.Map<Long, float[]> from = new java.util.HashMap<>();
                for (var e : in.cast.entrySet()) for (long aid : e.getValue()) {
                    Actor a = g.actor(aid);
                    if (a == null) continue;
                    float dist = (float) Math.hypot(a.x - g.player.x, a.z - g.player.z);
                    boolean visible = g.vox.sight(g.player.x, g.player.eyeY(), g.player.z, a.x, a.y + 1.2f, a.z);
                    if (visible && dist < 70) seen++;
                    if (dist < 30) close++;
                    from.put(aid, new float[]{a.x, a.z, dist});
                    System.out.printf("[enc]   %-8s %-18s at %.0f m %s  y %.1f stand %.1f%n", e.getKey(), a.name, dist, visible ? "IN SIGHT" : "unseen", a.y, WorldMap.standY(a.x, a.z));
                }
                for (int i = 0; i < frames; i++) game.tick(1 / 60f);
                for (var e : in.cast.entrySet()) for (long aid : e.getValue()) {
                    Actor a = g.actor(aid);
                    float[] f = from.get(aid);
                    if (a == null || f == null) { System.out.printf("[enc]   %-8s #%d gone%n", e.getKey(), aid); continue; }
                    float moved = (float) Math.hypot(a.x - f[0], a.z - f[1]);
                    String what = a instanceof Npc n ? n.goal + " / " + (n.encounter == null ? "-" : n.encounter.behavior) + " loaded " + n.loaded : a.getClass().getSimpleName();
                    boolean still = moved < 1.5f && a.alive;
                    if (still) stuck++;
                    System.out.printf("[enc]   %-8s %-18s moved %.1f m, now %.0f m off, %s%s%n", e.getKey(), a.name, moved, Math.hypot(a.x - g.player.x, a.z - g.player.z), what, still ? "  STILL" : "");
                }
                System.out.println("[enc]   stage " + in.stage + (in.ended ? " (ended)" : ""));
            }
            System.out.println("\n[enc] SUMMARY: in sight within 70 m " + seen + ", within 30 m " + close + ", standing still " + stuck);
        } finally { game.close(); WorldMap.activateDevmap(); }
    }

    static void placeOnRoad(Rpg g, String region) {
        var a = Atlas.get();
        for (var r : Atlas.list(a.root, "regions")) {
            if (!region.equals(Atlas.s(r, "id", ""))) continue;
            int[] c = a.cells((List<?>) r.get("rect"));
            int[] road = rpg.encounter.Encounters.roadWithinPublic((c[0] + c[2]) / 2f, (c[1] + c[3]) / 2f, 160);
            if (road == null) return;
            g.teleport(g.player, road[0] + 0.5f, WorldMap.standY(road[0] + 0.5f, road[1] + 0.5f), road[1] + 0.5f);
            g.player.vx = g.player.vz = 0;
            return;
        }
    }
}
