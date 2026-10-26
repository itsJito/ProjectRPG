package rpg.dungeon;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import java.nio.file.Path;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import rpg.*;
import rpg.actor.Appearance;
import rpg.ai.Npc;
import rpg.skill.Skill;
import rpg.world.DevMap;
import rpg.world.DungeonLayout;
import static org.junit.jupiter.api.Assertions.*;

/** The dungeon system (gamefixes): four kinds, each a sealed map of its own, with residents, traps and hoards that last. */
class DungeonTest {
    private Game game;
    private Rpg g;
    @BeforeEach void open() {
        var cfg = new NativeEngine.Config(); cfg.deterministic = true;
        game = Game.create(cfg); game.activate(DevMap.build(game.engine()));
        g = game.add(new Rpg(game, "Dungeons", new Appearance(), true));
        for (Npc n : g.npcs) g.ai.unload(n);
        g.player.skills.setLevel(Skill.HEALTH, 99); g.player.restore();
        game.tick(0);
    }
    @AfterEach void close() { game.close(); }
    private void frames(int n) { for (int i = 0; i < n; i++) assertTrue(game.tick(1f / 60)); }

    @Test void everyKindExistsWithItsOwnSealedMap() {
        var kinds = new java.util.HashSet<DungeonLayout.Type>();
        for (DungeonLayout l : DungeonLayout.all()) {
            kinds.add(l.type);
            assertTrue(l.ox >= DevMap.SX, l.name + " lies outside the overworld");
            assertTrue(l.openAt(l.worldX(l.entryX), l.worldZ(l.entryZ)), l.name + " has floor where one arrives");
            // sealed: nothing open on its region's edge, and every room reachable from the entry
            for (int x = 0; x < l.w; x++) { assertFalse(l.open[x][0]); assertFalse(l.open[x][l.d - 1]); }
            for (int z = 0; z < l.d; z++) { assertFalse(l.open[0][z]); assertFalse(l.open[l.w - 1][z]); }
            boolean[][] seen = new boolean[l.w][l.d];
            var queue = new java.util.ArrayDeque<int[]>(); queue.add(new int[]{l.entryX, l.entryZ}); seen[l.entryX][l.entryZ] = true;
            while (!queue.isEmpty()) { int[] c = queue.poll(); for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) { int nx = c[0] + d[0], nz = c[1] + d[1]; if (l.open[nx][nz] && !seen[nx][nz]) { seen[nx][nz] = true; queue.add(new int[]{nx, nz}); } } }
            for (var room : l.rooms) assertTrue(seen[(int) room.cx()][(int) room.cz()], l.name + ": every room can be reached");
            // the world carved to the plan: air where it is open, rock where it is not
            assertFalse(g.vox.solid(l.worldX(l.entryX), DevMap.FLOOR, l.worldZ(l.entryZ)));
            assertTrue(g.vox.solid(l.worldX(l.entryX), DungeonLayout.ROCK_TOP - 1, l.worldZ(l.entryZ)), "roofed over");
            long residents = g.monsters.all().stream().filter(m -> m.alive && DungeonLayout.at(m.x, m.z) == l).count();
            assertTrue(residents >= (l.type == DungeonLayout.Type.BOSS ? 1 : 2), l.name + " has residents: " + residents);
            assertTrue(g.objects.all().stream().anyMatch(o -> o instanceof rpg.objects.Container c && c.kind.equals("chest") && DungeonLayout.at(c.x, c.z) == l && !c.contents.isEmpty()), l.name + " has a hoard");
        }
        assertEquals(4, kinds.size(), "micro, macro, elite and boss");
    }

    @Test void inAndOutThroughTheDoorsAndTheTrapsBite(@TempDir Path dir) {
        for (var door : g.dungeons.doors) {
            if (door.inside) continue;
            g.teleport(g.player, door.x, DevMap.FLOOR, door.z + 1.2f); frames(3);
            assertTrue(g.interact.request(g.player, door, "enter").committed(), "enter " + door.layout.name);
            frames(5);
            assertSame(door.layout, DungeonLayout.at(g.player.x, g.player.z), "inside " + door.layout.name);
            var exit = g.dungeons.doors.stream().filter(d -> d.inside && d.layout == door.layout).findFirst().orElseThrow();
            g.teleport(g.player, exit.x + 0.8f, DevMap.FLOOR, exit.z); frames(3);
            assertTrue(g.interact.request(g.player, exit, "leave").committed());
            frames(5);
            assertNull(DungeonLayout.at(g.player.x, g.player.z), "back in the overworld");
            assertTrue(g.player.x < DevMap.SX);
        }
        // a trap: stepped on, it bites and is found; a thief's hands wedge it for good, and that is remembered
        var trap = g.dungeons.traps.get(0);
        g.player.hp = g.player.maxHp();
        float before = g.player.hp;
        g.teleport(g.player, trap.x, DevMap.FLOOR, trap.z); frames(20);
        assertTrue(g.player.hp < before, "the trap bit");
        assertTrue(trap.found);
        g.player.skills.setLevel(Skill.THIEVING, 99);
        frames(60 * 9);
        g.teleport(g.player, trap.x + 0.9f, DevMap.FLOOR, trap.z); frames(3);
        for (int i = 0; i < 20 && !trap.disarmed; i++) { g.interact.request(g.player, trap, "disarm"); frames(30 * 9); g.teleport(g.player, trap.x + 0.9f, DevMap.FLOOR, trap.z); frames(2); }
        assertTrue(trap.disarmed, "disarmed");
        var saves = new SaveSystem(g);
        assertTrue(saves.save(dir.resolve("dungeon.json")));
        assertTrue(saves.load(dir.resolve("dungeon.json")));
        var again = g.dungeons.traps.stream().filter(t -> t.id == trap.id).findFirst().orElseThrow();
        assertTrue(again.disarmed, "a disarmed trap stays disarmed across a load");
        long lairs = g.monsters.lairs.stream().filter(l -> DungeonLayout.at(l.x, l.z) != null).count();
        assertTrue(lairs >= 6, "the dungeons' residents come back with the save: " + lairs);
    }
}
