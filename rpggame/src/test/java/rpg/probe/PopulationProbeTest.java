package rpg.probe;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import rpg.Rpg;
import rpg.actor.Appearance;
import rpg.ai.Npc;
import rpg.world.DevMap;
import rpg.world.WorldMap;
import rpg.world.shadowveil.ShadowVeil;

/**
 * (0.4.3) ShadowVeil's people: who they are, where they live and work, and what each does through a stretch of the
 * day - every person's last outcome sampled each game half hour, so a stuck or idle routine shows at once.
 * {@code gradlew probe -PprobeClass=*Population* [-Prpg.probeFrames=hours]}.
 */
@Tag("probe")
class PopulationProbeTest {
    @Test void population() {
        int hours = Integer.getInteger("rpg.probeFrames", 4);
        var cfg = new NativeEngine.Config(); cfg.deterministic = true; cfg.audioDevice = false;
        Game game = Game.create(cfg);
        try {
            game.activate(ShadowVeil.build(game.engine()));
            Rpg g = game.add(new Rpg(game, "population", new Appearance(), true));
            game.tick(0);
            System.out.println("[people] " + g.npcs.size() + " people");
            Map<String, Integer> byRealm = new HashMap<>();
            for (Npc n : g.npcs) {
                byRealm.merge(DevMap.jurisdiction(n.homeX, n.homeZ), 1, Integer::sum);
                System.out.printf("[person] %-12s %-12s %-22s home (%.0f,%.0f) %-9s post (%.0f,%.0f) %-9s%n", n.name, n.role, n.profession == null ? "-" : n.profession.name(),
                        n.homeX, n.homeZ, DevMap.jurisdiction(n.homeX, n.homeZ), n.postX, n.postZ, DevMap.jurisdiction(n.postX, n.postZ));
            }
            System.out.println("[people] by realm " + byRealm);
            Map<String, Integer> species = new java.util.TreeMap<>();
            float closest = Float.MAX_VALUE; String who = "";
            for (var an : g.animals.all()) {
                species.merge(an.species.name(), 1, Integer::sum);
                if (!an.species.predator()) continue;
                for (var as : rpg.world.shadowveil.Census.all().values()) if (as.homeStage() != null && !"Wilds".equals(as.kingdom())) {
                    float d = (float) Math.hypot(as.homeStage()[0] - an.x, as.homeStage()[1] - an.z);
                    if (d < closest) { closest = d; who = an.species + " near " + as.name() + " (" + as.homeId() + ")"; }
                }
            }
            System.out.println("[wild] " + g.animals.all().size() + " animals " + species + "; habitats " + g.animals.habitats.size() + "; nearest predator to a town home " + closest + " m: " + who);
            Map<String, Integer> predHabs = new java.util.TreeMap<>(); float predNear = Float.MAX_VALUE;
            for (var h : g.animals.habitats) for (var sp : h.capacity.keySet()) if (sp.predator()) {
                predHabs.merge(sp.name(), 1, Integer::sum);
                for (var as : rpg.world.shadowveil.Census.all().values()) if (as.homeStage() != null && !"Wilds".equals(as.kingdom())) predNear = Math.min(predNear, (float) Math.hypot(as.homeStage()[0] - h.cx(), as.homeStage()[1] - h.cz()));
            }
            System.out.println("[wild] predator habitats " + predHabs + ", nearest to a town home " + predNear + " m");
            System.out.println("[census] problems " + rpg.world.shadowveil.Census.problems());
            g.state.minutes = 6 * 60;
            Map<Long, Map<String, Integer>> outcomes = new HashMap<>();
            Map<Long, float[]> start = new HashMap<>();
            Map<Long, Float> walked = new HashMap<>();
            for (Npc n : g.npcs) start.put(n.id, new float[]{n.x, n.z});
            int ticksPerSample = 60 * 30 / Math.max(1, (int) 1);   // a game half hour at a minute a second: 30 s of ticks
            for (int h = 0; h < hours * 2; h++) {
                for (int i = 0; i < 30 * 60; i++) {
                    float[] before = null;
                    game.tick(1 / 60f);
                }
                // keep the player beside the busiest town so the people there are on stage
                for (Npc n : g.npcs) {
                    String o = n.lastOutcome == null ? "-" : n.lastOutcome.replaceAll("[0-9]+", "#");
                    if (o.length() > 70) o = o.substring(0, 70);
                    outcomes.computeIfAbsent(n.id, k -> new HashMap<>()).merge(n.goal + " | " + o, 1, Integer::sum);
                    float[] s = start.get(n.id);
                    walked.put(n.id, (float) Math.hypot(n.x - s[0], n.z - s[1]));
                }
                System.out.printf("[clock] %s%n", g.state.clockText());
            }
            for (Npc n : g.npcs) {
                StringBuilder b = new StringBuilder(String.format("[day] %-12s %-20s moved %4.0f m loaded %-5s :", n.name, n.profession == null ? n.role : n.profession.name(), walked.getOrDefault(n.id, 0f), n.loaded));
                outcomes.getOrDefault(n.id, Map.of()).entrySet().stream().sorted((a, c) -> c.getValue() - a.getValue()).limit(3).forEach(e -> b.append("  [").append(e.getValue()).append("] ").append(e.getKey()));
                System.out.println(b);
            }
        } finally { game.close(); WorldMap.activateDevmap(); }
    }
}
