package rpg.ai;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import java.util.ArrayList;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import rpg.Ids;
import rpg.Rpg;
import rpg.actor.Appearance;
import rpg.monster.Monster;
import rpg.world.DevMap;

/** Probe (not an assertion suite): does a sound's hearer actually go to where it was made? */
@EnabledIfEnvironmentVariable(named = "RPG_PROBE", matches = "1")
class SoundProbeTest {
    static void log(String line) {
        try { java.nio.file.Files.writeString(java.nio.file.Path.of("build/probe.txt"), line + System.lineSeparator(), java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND); } catch (java.io.IOException e) { throw new RuntimeException(e); }
    }
    @Test void probe() {
        for (long who : new long[]{Ids.VILLAGER, Ids.WORKER, Ids.MERCHANT_A}) for (int side = 0; side < 3; side++) {
            var cfg = new NativeEngine.Config(); cfg.deterministic = true; Game game = Game.create(cfg); game.activate(DevMap.build(game.engine()));
            Rpg g = game.add(new Rpg(game, "Probe", Appearance.henric(), true));
            for (Monster m : new ArrayList<>(g.monsters.all())) g.ai.unload(m);
            for (Npc o : g.npcs) if (o.id != who) g.ai.unload(o);
            g.state.minutes = 23 * 60;
            Npc n = (Npc) g.actor(who);
            g.teleport(n, n.postX, DevMap.FLOOR, n.postZ); game.tick(0);
            // a spot 9 m off, round a corner where possible
            float[][] offs = {{9, 0}, {0, 9}, {-6, -6}};
            float sx = n.x + offs[side][0], sz = n.z + offs[side][1];
            g.teleport(g.player, sx, DevMap.FLOOR, sz); g.player.sneaking = true; game.tick(0);
            float closest = 999; StringBuilder goals = new StringBuilder();
            Brain.Goal last = null;
            for (int f = 0; f < 60 * 35; f++) {
                if (f % 120 == 0 && f < 60 * 6) g.noise(g.player, g.player.x, g.player.y, g.player.z, 14, 0.6f);
                game.tick(1f / 60);
                closest = Math.min(closest, n.distTo(g.player));
                if (n.goal != last) { last = n.goal; goals.append(String.format(" %.1f:%s", f / 60f, n.goal)); }
                if (f % 90 == 0 && n.investigation != null) goals.append(String.format(" [%.1f %s/%s c%.2f d%.1f '%s']", f / 60f, n.investigation.plan, n.investigation.stage, n.investigation.courage, n.distTo(g.player), n.investigation.why));
            }
            var v = n.investigation;
            log(String.format("[sound] %s side %d src (%.1f,%.1f) blocked=%b closest %.1f m, goals%s, inv=%s%n", n.name, side, sx, sz,
                    g.vox.solid((int) sx, DevMap.FLOOR, (int) sz), closest, goals, v == null ? "-" : v.plan + "/" + v.stage + " at " + String.format("(%.1f,%.1f)", v.x, v.z)));
            game.close();
        }
    }
}
