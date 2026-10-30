package rpg.world;

import static org.junit.jupiter.api.Assertions.*;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import rpg.Rpg;
import rpg.actor.Appearance;
import rpg.ai.Npc;
import rpg.interact.WorldObject;

/**
 * Gamefixes: the devmap had placement problems after many additions. Audits the whole overworld for them: blocks
 * floating with nothing under or beside them, holes in the ground, objects sunk in solid rock or walls, people whose
 * home or post cannot be stood on, doors walled in on both sides, beds whose head is not to a wall, and roads that run
 * under a building's wall (in at one wall and out through another).
 */
class DevMapAuditTest {
    /** The square's two bare walls are placed on purpose: the stealth tests hide behind them. */
    private static boolean occluder(int x, int z) { return z == 76 && x >= 108 && x <= 112 || x == 88 && z >= 76 && z <= 80; }

    @Test void theDevmapHasNoPlacementProblems() {
        var config = new NativeEngine.Config(); config.deterministic = true;
        try (Game game = Game.create(config)) {
            game.activate(DevMap.build(game.engine()));
            Rpg g = game.add(new Rpg(game, "Audit", new Appearance(), true));
            List<String> floating = new ArrayList<>(), holes = new ArrayList<>(), buried = new ArrayList<>(), homeless = new ArrayList<>(), walledDoors = new ArrayList<>();
            int F = DevMap.FLOOR;
            for (int x = 1; x < DevMap.SX - 1; x++) for (int z = 1; z < DevMap.SZ - 1; z++) {
                if (!g.vox.solid(x, F - 1, z) && g.vox.block(x, F - 1, z) != Blocks.WATER && g.vox.block(x, F - 2, z) != Blocks.WATER) holes.add(x + "," + z);
                for (int y = F + 1; y < DevMap.SY - 1; y++) {
                    int b = g.vox.block(x, y, z);
                    if (b == Blocks.AIR || Blocks.plant(b) || b == Blocks.LAMP || Blocks.objectCell(b)) continue;
                    boolean leafy = b == Blocks.LEAVES || b == Blocks.OAK_LEAVES || b == Blocks.WILLOW_LEAVES;
                    if (leafy) continue;
                    boolean held = g.vox.solid(x, y - 1, z) || g.vox.solid(x + 1, y, z) || g.vox.solid(x - 1, y, z) || g.vox.solid(x, y, z + 1) || g.vox.solid(x, y, z - 1) || g.vox.solid(x, y + 1, z);
                    if (!held) floating.add(x + "," + y + "," + z + " (" + b + ")");
                }
            }
            for (WorldObject o : g.objects.all()) {
                if (o.removed || o.y < F - 1 || o.x >= DevMap.SX) continue;
                if (o.def.equals("climb") || o instanceof rpg.objects.Door || o instanceof rpg.objects.Furniture || o instanceof rpg.objects.Node || o instanceof rpg.objects.BuildSpot || o instanceof rpg.dungeon.Dungeons.Door || o instanceof rpg.objects.Fixtures.Board) continue;
                int bx = (int) Math.floor(o.x), by = (int) Math.floor(o.y + 0.05f), bz = (int) Math.floor(o.z);
                int b = g.vox.block(bx, by, bz);
                if (b != Blocks.AIR && !Blocks.objectCell(b) && !Blocks.plant(b) && b != Blocks.WATER && b != Blocks.EMBERS && b != Blocks.FENCE) buried.add(o.def + " '" + o.name + "' at " + bx + "," + by + "," + bz + " in block " + b);
            }
            for (Npc n : g.npcs) {
                if (n instanceof rpg.monster.Monster || n.homeX >= DevMap.SX) continue;
                for (float[] p : new float[][]{{n.homeX, n.homeZ}, {n.postX, n.postZ}}) {
                    int bx = (int) Math.floor(p[0]), bz = (int) Math.floor(p[1]);
                    // (0.2.1) a post up a step (a temple's podium) is a place to stand: the block underfoot, room for the body above it
                    boolean step = g.vox.solid(bx, F, bz) && !g.vox.solid(bx, F + 1, bz) && !g.vox.solid(bx, F + 2, bz);
                    if (g.vox.solid(bx, F, bz) && g.vox.block(bx, F, bz) != Blocks.BED && !step || g.vox.solid(bx, F + 1, bz) && !step) homeless.add(n.name + " at " + bx + "," + bz + " (block " + g.vox.block(bx, F, bz) + ")");
                }
            }
            for (var d : g.objects.doors()) {
                int bx = (int) Math.floor(d.x), bz = (int) Math.floor(d.z);
                boolean ns = !g.vox.solid(bx, F, bz - 1) || !g.vox.solid(bx, F, bz + 1), ew = !g.vox.solid(bx - 1, F, bz) || !g.vox.solid(bx + 1, F, bz);
                if (!ns && !ew) walledDoors.add(d.name + " at " + bx + "," + bz);
            }
            // beds: the head (+z turned by the facing) is to a wall, not out in the room
            List<String> loose = new ArrayList<>(), throughWalls = new ArrayList<>();
            for (var f : g.objects.furniture) {
                if (f.kind != rpg.objects.Furniture.Kind.BED || f.x0 >= DevMap.SX) continue;
                int hx = Math.round((float) Math.sin(f.yaw())), hz = Math.round((float) Math.cos(f.yaw()));
                int x = hx > 0 ? f.x1 + 1 : hx < 0 ? f.x0 - 1 : f.x0, z = hz > 0 ? f.z1 + 1 : hz < 0 ? f.z0 - 1 : f.z0;
                boolean outdoors = g.vox.skyLight(f.x0, F + 1, f.z0) > 0.9f;
                if (!outdoors && !g.vox.solid(x, F + 1, z)) loose.add(f.name + " at " + f.x0 + "," + f.z0 + " (head toward " + x + "," + z + ")");
            }
            // roads: cobble running under a building's wall
            java.util.Set<Integer> walls = java.util.Set.of(Blocks.PLASTER, Blocks.BRICK, Blocks.STONE_BRICKS, Blocks.LIMESTONE_BRICKS);
            for (int x = 1; x < DevMap.SX - 1; x++) for (int z = 1; z < DevMap.SZ - 1; z++)
                if (g.vox.block(x, F - 1, z) == Blocks.COBBLE && walls.contains(Blocks.outerOf(g.vox.block(x, F + 1, z))) && !occluder(x, z)) throughWalls.add(x + "," + z);
            System.out.println("beds not against a wall (" + loose.size() + "): " + loose);
            System.out.println("road under walls (" + throughWalls.size() + "): " + throughWalls);
            assertTrue(loose.isEmpty(), "beds whose head is out in the room: " + loose);
            assertTrue(throughWalls.isEmpty(), "roads running under a wall: " + throughWalls);
            System.out.println("floating blocks (" + floating.size() + "): " + floating.subList(0, Math.min(40, floating.size())));
            System.out.println("holes in the ground (" + holes.size() + "): " + holes.subList(0, Math.min(40, holes.size())));
            System.out.println("objects sunk in blocks (" + buried.size() + "): " + buried);
            System.out.println("people whose home or post is blocked (" + homeless.size() + "): " + homeless);
            System.out.println("doors walled in (" + walledDoors.size() + "): " + walledDoors);
            assertTrue(buried.isEmpty(), "objects sunk in blocks: " + buried);
            assertTrue(homeless.isEmpty(), "blocked homes or posts: " + homeless);
            assertTrue(walledDoors.isEmpty(), "walled-in doors: " + walledDoors);
            assertTrue(floating.isEmpty(), "floating blocks: " + floating);
            assertTrue(holes.isEmpty(), "holes in the ground: " + holes);
        }
    }
}
