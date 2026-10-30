package rpg.world;

import static org.junit.jupiter.api.Assertions.*;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import java.util.*;
import org.junit.jupiter.api.*;
import rpg.Rpg;
import rpg.actor.Appearance;

/**
 * Gamefixes 0.2.1: a standard way of raising buildings, so that every house is tall enough, its door opens onto open
 * ground, and everything inside it can be walked to - placed for the people who use it as much as for the look of it.
 * Every building raised through BuildingKit is held to the rules here, against the built world.
 */
class BuildingStandardTest {
    private static Game game;
    private static Rpg g;
    @BeforeAll static void start() {
        var cfg = new NativeEngine.Config(); cfg.deterministic = true; game = Game.create(cfg); game.activate(DevMap.build(game.engine()));
        g = game.add(new Rpg(game, "Standard", Appearance.henric(), true)); for (var n : g.npcs) g.ai.unload(n);
    }
    @AfterAll static void close() { game.close(); }

    private static final int F = DevMap.FLOOR;
    private static boolean walkable(int x, int z) {
        int b = g.vox.block(x, F, z);
        boolean door = b == Blocks.DOOR || b == Blocks.LOCKED_DOOR;
        return (door || !g.vox.solid(x, F, z)) && !g.vox.solid(x, F + 1, z) && g.vox.solid(x, F - 1, z);
    }
    /** The way out through a plan's door: +-1 along the axis its wall faces. */
    private static int[] out(BuildingKit.Plan p) {
        if (p.axis() == 'z') return new int[]{0, p.doorZ() == p.z0() ? -1 : 1};
        return new int[]{p.doorX() == p.x0() ? -1 : 1, 0};
    }

    @Test void everyBuildingKeepsTheStandard() {
        List<BuildingKit.Plan> plans = BuildingKit.plans();
        assertTrue(plans.size() >= 15, "only " + plans.size() + " buildings raised through the kit");
        List<String> low = new ArrayList<>(), blockedDoors = new ArrayList<>(), unreachable = new ArrayList<>();
        for (BuildingKit.Plan p : plans) {
            if (p.height() < BuildingKit.MIN_HEIGHT) low.add(p.name() + " " + p.height() + " high");
            for (int y = F; y < F + BuildingKit.MIN_HEIGHT; y++) if (!g.vox.solid(p.x0(), y, p.z0())) { low.add(p.name() + " corner open at " + y); break; }
            int[] o = out(p);
            for (int k = 1; k <= BuildingKit.APRON; k++) if (!walkable(p.doorX() + o[0] * k, p.doorZ() + o[1] * k)) { blockedDoors.add(p.name() + " door " + p.doorX() + "," + p.doorZ() + " blocked " + k + " out"); break; }
            // the rooms reachable from inside the door
            Set<Long> seen = new HashSet<>();
            ArrayDeque<int[]> q = new ArrayDeque<>();
            int sx = p.doorX() - o[0], sz = p.doorZ() - o[1];
            if (walkable(sx, sz)) { q.add(new int[]{sx, sz}); seen.add((long) sx << 32 | (sz & 0xffffffffL)); }
            while (!q.isEmpty()) {
                int[] c = q.poll();
                for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    int nx = c[0] + d[0], nz = c[1] + d[1];
                    if (!p.inside(nx, nz) || !walkable(nx, nz)) continue;
                    if (seen.add((long) nx << 32 | (nz & 0xffffffffL))) q.add(new int[]{nx, nz});
                }
            }
            for (var f : g.objects.furniture) {
                // a cell's slab is behind its bars: reached by the gaoler's key, not walked to (Jails)
                if (!p.inside(f.x0, f.z0) || f.kind == rpg.objects.Furniture.Kind.SLAB) continue;
                boolean reach = false;
                for (int x = f.x0 - 1; x <= f.x1 + 1 && !reach; x++) for (int z = f.z0 - 1; z <= f.z1 + 1 && !reach; z++)
                    reach = seen.contains((long) x << 32 | (z & 0xffffffffL));
                if (!reach) unreachable.add(f.name + " (" + f.kind + ") at " + f.x0 + "," + f.z0 + " in the " + p.name());
            }
        }
        System.out.println("buildings " + plans.size() + "; low " + low + "; doors " + blockedDoors + "; unreachable " + unreachable);
        assertTrue(low.isEmpty(), "walls under the standard height: " + low);
        assertTrue(blockedDoors.isEmpty(), "doors that open onto something: " + blockedDoors);
        assertTrue(unreachable.isEmpty(), "furniture no one can walk to: " + unreachable);
    }

    @Test void wallsAreTwoFacedWithTheirInsideInward() {
        int dual = 0, wrongWay = 0;
        for (BuildingKit.Plan p : BuildingKit.plans()) for (int x = p.x0() + 1; x < p.x1(); x++) {
            int b = g.vox.block(x, F + 2, p.z0());
            if (!Blocks.dual(b)) continue;
            dual++;
            if (Blocks.innerDir(b) != Blocks.IN_PZ) wrongWay++;
        }
        assertTrue(dual > 50, "too few two-faced wall blocks: " + dual);
        assertEquals(0, wrongWay, "wall blocks with their inside facing out");
    }
}
