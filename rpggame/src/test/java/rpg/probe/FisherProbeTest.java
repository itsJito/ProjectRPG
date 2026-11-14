package rpg.probe;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import rpg.Rpg;
import rpg.actor.Appearance;
import rpg.ai.Npc;
import rpg.world.WorldMap;
import rpg.world.shadowveil.ShadowVeil;

/** (0.4.2) What ShadowVeil's fishers and workers do through a morning, beside each in turn (a probe: prints, asserts nothing). */
@Tag("probe")
class FisherProbeTest {
    @Test void fishersThroughAMorning() {
        var cfg = new NativeEngine.Config(); cfg.deterministic = true; cfg.audioDevice = false;
        Game game = Game.create(cfg);
        try {
            game.activate(ShadowVeil.build(game.engine()));
            Rpg g = game.add(new Rpg(game, "probe", new Appearance(), true));
            g.cfg.minutesPerSecond = 2;
            g.state.minutes = 7 * 60;
            String only = System.getProperty("rpg.probeWho", "FISHERMAN");
            for (Npc n : new java.util.ArrayList<>(g.npcs)) {
                if (!only.contains(n.role.name()) && !only.contains(n.name)) continue;
                StringBuilder sites = new StringBuilder();
                for (var node : g.objects.nodes) if (node.kind == rpg.objects.Node.Kind.FISH && n.distTo(node.x, node.z) < 260)
                    sites.append(String.format("    site %s %s needs %s (has %s, main %s) lv %d/%d reach %s dist %.0f%n", node.name, node.waters, node.needs == null ? "-" : node.needs.id,
                            node.needs != null && n.has(node.needs, 1), n.main() == null ? "-" : n.main().id, node.level, n.skills.level(node.skill), rpg.ai.ZonePolicy.withinReach(n, node.x, node.z), n.distTo(node.x, node.z)));
                System.out.print(sites);
                g.teleport(g.player, n.x + 3, WorldMap.standY(n.x + 3, n.z + 3), n.z + 3);
                StringBuilder b = new StringBuilder(String.format("== %s %s (%s) post (%.0f, %.0f) at (%.0f, %.0f)%n", n.name, n.role, n.profession == null ? "-" : n.profession.title, n.postX, n.postZ, n.x, n.z));
                for (int f = 0, frames = Integer.getInteger("rpg.probeFrames", 2400); f < frames; f++) {
                    game.tick(1f / 60);
                    if (f % 240 == 0) {
                        int fish = 0;
                        for (var grid : n.grids()) for (var st : grid.stacks()) if (st.def.id.startsWith("raw_")) fish += st.amount;
                        var w = g.interact.of(n);
                        b.append(String.format("  %s (%.1f, %.1f) goal %s fish %d work %s  %s%n", g.state.clockText(), n.x, n.z, n.goal, fish, w == null ? "-" : w.verb, n.lastOutcome));
                    }
                }
                System.out.print(b);
            }
        } finally { game.close(); WorldMap.activateDevmap(); }
    }
}
