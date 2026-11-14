package rpg.probe;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import rpg.Rpg;
import rpg.actor.Appearance;
import rpg.ai.Npc;
import rpg.world.Blocks;
import rpg.world.WorldMap;
import rpg.world.shadowveil.ShadowVeil;

/**
 * (0.4.5) The pass's live checks, printed: a window broken in a kingdom - who sees it, who it is reported to, who mends
 * it and when; a field's ripe plot harvested by the player through the interaction itself; the shrines. Who could mend
 * (the carpenters, their tools) is listed. {@code gradlew probe -PprobeClass=*Pass31*}
 */
@Tag("probe")
class Pass31ProbeTest {
    @Test void probe() {
        var cfg = new NativeEngine.Config(); cfg.deterministic = true; cfg.audioDevice = false;
        Game game = Game.create(cfg);
        try {
            game.activate(ShadowVeil.build(game.engine()));
            Rpg g = game.add(new Rpg(game, "p31", new Appearance(), true));
            game.tick(0);
            System.out.println("[p31] npcs " + g.npcs.size());
            for (var s : g.shrines.all) System.out.printf("[p31] shrine %s at %.1f,%.1f,%.1f floor %d block under %d%n", s.graveyard, s.x, s.y, s.z, s.floor, g.vox.block((int) s.x, s.floor, (int) s.z));
            for (Npc n : g.npcs) if (n.profession == rpg.ai.Profession.CARPENTER || n.role == Npc.Role.WORKER)
                System.out.printf("[p31] mender %-14s %-10s %-12s hammer %s planks %d at %.0f,%.0f%n", n.name, n.role, n.profession, n.has(rpg.item.Items.HAMMER, 1) || n.main() == rpg.item.Items.HAMMER, n.count(rpg.item.Items.PLANK), n.x, n.z);
            // a pane in Loxford, near the most people
            int[] pane = null;
            float cx = 190, cz = 870; double bd = 1e9;
            for (int x = (int) cx - 40; x <= cx + 40; x++) for (int z = (int) cz - 40; z <= cz + 40; z++) for (int y = 36; y < 50; y++)
                if (Blocks.glass(g.vox.block(x, y, z))) { double d = Math.hypot(x - cx, z - cz); if (d < bd) { bd = d; pane = new int[]{x, y, z}; } }
            System.out.println("[p31] pane " + (pane == null ? "none" : pane[0] + "," + pane[1] + "," + pane[2]));
            g.state.minutes = 9 * 60;
            if (pane != null) {
                g.glass.shatter(pane[0], pane[1], pane[2], pane[0] + 0.5f, pane[1] + 0.5f, pane[2] + 0.5f, 1, 0, 0, 1, null);
                for (int s = 0; s < 60 * 60 * 18; s++) {
                    game.tick(1 / 60f);
                    if (s % (60 * 60) == 0) {
                        System.out.printf("[p31] t=%ds open %d mended %d block %d%n", s / 60, g.repairs.open().size(), g.repairs.mended, g.vox.block(pane[0], pane[1], pane[2]));
                        var dmg = g.repairs.open().isEmpty() ? null : g.repairs.open().get(0);
                        if (dmg != null) for (Npc n : g.npcs) {
                            double dh = Math.hypot(n.homeX - pane[0], n.homeZ - pane[2]), dp = Math.hypot(n.postX - pane[0], n.postZ - pane[2]), dn = Math.hypot(n.x - pane[0], n.z - pane[2]);
                            if (dh < 25 || dp < 25 || dn < 25 || g.repairs.knows(n, dmg.key())) System.out.printf("[p31]   %-14s %-10s loaded %-5s home %.0f post %.0f now %.0f knows %s goal %s report %s | %s%n", n.name, n.role, n.loaded, dh, dp, dn, g.repairs.knows(n, dmg.key()), n.goal, g.repairs.reportFor(n) != null, n.lastOutcome + " hasWork " + g.repairs.hasWork(n) + " scores " + n.scores + " clock " + g.state.clockText());
                        }
                    }
                    if (s % 600 == 0) for (Npc n : g.npcs) if (n.lastOutcome != null && n.lastOutcome.contains("mend")) System.out.printf("[p31] mender %s at %.1f,%.1f,%.1f route: %s | %s%n", n.name, n.x, n.y, n.z, n.routeNote, n.lastOutcome);
                    if (g.repairs.mended > 0) break;
                }
                System.out.println("[p31] glass: open " + g.repairs.open().size() + " mended " + g.repairs.mended);
                for (var e : g.state.all()) if (e.type().startsWith("Damage") || e.type().startsWith("Repair")) System.out.println("[p31] event " + e.type() + " " + g.nameOf(e.actor()) + " -> " + g.nameOf(e.subject()) + " " + e.payload());
            }
            // harvest: the nearest ripe plot of anyone's to the player, harvested by the player
            var plots = g.farming.plots;
            rpg.world.Farming.Plot ripe = null;
            for (var p : plots) if (p.crop != null) { p.growth = 1; p.ripeFor = 0; if (ripe == null) ripe = p; }
            System.out.println("[p31] plots " + plots.size() + (ripe == null ? "" : " first " + ripe.label() + " at " + ripe.x + "," + ripe.y + "," + ripe.z + " ground " + WorldMap.standY(ripe.x, ripe.z)));
            if (ripe != null) {
                g.player.skills.setLevel(rpg.skill.Skill.HARVESTING, 99);
                g.teleport(g.player, ripe.x + 0.9f, WorldMap.standY(ripe.x + 0.9f, ripe.z), ripe.z);
                for (int i = 0; i < 30; i++) game.tick(1 / 60f);
                System.out.println("[p31] options " + ripe.options(g, g.player).stream().map(o -> o.id() + (o.enabled() ? "" : "(" + o.reason() + ")")).toList());
                var r = ripe.perform(g, g.player, "harvest", g.state.newRequestId());
                System.out.println("[p31] perform " + r);
                for (int i = 0; i < 60 * 5; i++) game.tick(1 / 60f);
                System.out.println("[p31] after: " + ripe.label() + " pack " + g.player.count(ripe.crop == null ? rpg.item.Items.CARROT : rpg.item.Items.CARROT) + " harvests " + g.farming.harvestCount);
            }
        } finally { game.close(); WorldMap.activateDevmap(); }
    }
}
