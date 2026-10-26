package rpg.world;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import org.junit.jupiter.api.*;
import rpg.*;
import rpg.actor.Appearance;
import rpg.ai.Npc;
import static org.junit.jupiter.api.Assertions.*;

/** Gamefixes: no plant is ever solid. The player walks straight through one of every kind of foliage the world grows. */
class FoliagePassageTest {
    private Game game;
    private Rpg g;
    @BeforeEach void open() {
        var cfg = new NativeEngine.Config(); cfg.deterministic = true;
        game = Game.create(cfg); game.activate(DevMap.build(game.engine()));
        g = game.add(new Rpg(game, "Foliage", new Appearance(), true));
        for (Npc n : g.npcs) g.ai.unload(n);
        for (var m : g.monsters.all()) g.monsters.despawnBody(m);
    }
    @AfterEach void close() { game.close(); }
    private void frames(int n) { for (int i = 0; i < n; i++) game.tick(1f / 60); }

    @Test void everyKindOfPlantIsWalkedThrough() {
        // grow the whole map's foliage
        for (int cx = 0; cx < 8; cx++) for (int cz = 0; cz < 8; cz++) { g.teleport(g.player, cx * 16 + 8.5f, DevMap.FLOOR, cz * 16 + 8.5f); frames(20); }
        StringBuilder blocked = new StringBuilder();
        int tried = 0;
        for (Foliage.Kind kind : Foliage.Kind.values()) {
            if (kind == Foliage.Kind.IVY || kind == Foliage.Kind.MOSS) continue;   // these grow on walls
            var plant = g.foliage.chunks.values().stream().flatMap(c -> c.plants.stream())
                    .filter(p -> !p.removed && p.kind == kind && clearApproach(p.x,p.z)).findFirst().orElse(null);
            if (plant == null || plant.x < 6 || plant.z < 6) continue;
            tried++;
            g.teleport(g.player, plant.x - 2.5f, DevMap.FLOOR, plant.z);
            g.controller.snapTo((float) -Math.PI / 2);
            frames(4);
            for (int i = 0; i < 100; i++) { g.controller.in.forward = 1; game.tick(1f / 60); }
            g.controller.in.forward = 0; frames(4);
            if (g.player.x < plant.x + 0.6f) blocked.append(String.format("%s at (%.1f, %.1f): stopped at x=%.2f%n", kind, plant.x, plant.z, g.player.x));
        }
        assertTrue(tried >= 6, "tried only " + tried + " kinds");
        assertEquals("", blocked.toString(), "plants must never stop a body:\n" + blocked);
    }

    @Test void harvestPlantsAreWalkedThrough() {
        // the herb, flax and berry patches are foliage too: bodies brush through them, they do not stop at them
        StringBuilder blocked = new StringBuilder();
        for (int[][] patches : new int[][][]{DevMap.HERBS, DevMap.FLAXES, DevMap.BERRIES}) for (int[] p : patches) {
            float x = p[0] + 0.5f, z = p[1] + 0.5f;
            g.teleport(g.player, x, DevMap.FLOOR, z + 2.5f);
            g.controller.snapTo(0);
            frames(4);
            for (int i = 0; i < 100; i++) { g.controller.in.forward = 1; game.tick(1f / 60); }
            g.controller.in.forward = 0; frames(4);
            if (g.player.z > z - 0.6f) blocked.append(String.format("%d at (%d, %d): stopped at z=%.2f%n", g.vox.block(p[0], DevMap.FLOOR, p[1]), p[0], p[1], g.player.z));
        }
        assertEquals("", blocked.toString(), "harvest plants must never stop a body:\n" + blocked);
    }

    private boolean clearApproach(float x,float z) {
        // A plant beside a solid entrance is not an isolated foliage probe. Check the complete swept approach.
        for(float dx=-2.5f;dx<=1.6f;dx+=0.1f) {
            if(!open(x+dx,z) || g.objects.collisions.overlaps(x+dx,DevMap.FLOOR,z,g.player.radius+0.15f,g.player.height))return false;
        }
        return true;
    }

    /** A clear standing cell: soil or floor under it, nothing solid where the body goes. */
    private boolean open(float x, float z) {
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        return !g.objects.collisions.overlaps(x, DevMap.FLOOR, z, g.player.radius, g.player.height) && g.vox.solid(bx, DevMap.FLOOR - 1, bz) && !g.vox.solid(bx, DevMap.FLOOR, bz) && !g.vox.solid(bx, DevMap.FLOOR + 1, bz) && g.vox.block(bx, DevMap.FLOOR - 1, bz) != Blocks.WATER;
    }
}
