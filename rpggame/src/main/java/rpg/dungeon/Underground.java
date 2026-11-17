package rpg.dungeon;

import java.util.ArrayList;
import java.util.List;
import rpg.world.Blocks;
import rpg.world.DungeonLayout;

/**
 * (0.4.7, gamefixes: the dungeon system on ShadowVeil - levels going down under the map, laid out at random, their
 * interiors polished) every underground site's levels, planned from the map's design and carved into its rock.
 *
 * <p>A site (DungeonSites: a lair, a mine, a crypt, a hideout, with its mouth on the map) goes down as many levels as its
 * data says. Each level is a dungeon of its own, generated from the site's seed and the level's number - rooms joined by
 * passages, its dens, treasure and hazard rooms, the last a master's - and cut ten cells or more below the one above,
 * centred under the mouth, always with rock between its roof and the ground (and any river or lake) over it. The mouth
 * goes down to the first level and each level's deepest room down to the next by a spiral stair cut round a pillar of
 * rock; the deeper, the larger and harder: a single level a small one, the first of several a dungeon, the last an elite's
 * with its master. Its rooms are dressed in the theme's stone (a cave's raw rock, a warren's packed earth and timber, a
 * crypt's dressed blocks, a hall's granite), lamps in its walls, pillars in its halls, fire pits and sunken pools in its
 * hazards, braziers and fallen rock - the same plan its residents, chests and traps are placed from (Dungeons).
 */
public final class Underground {
    private Underground() {}
    /** How far below the one above a level is cut (cells), and the rock always kept over a level's roof. */
    /** (0.4.7) Every shaft cut: {ladder x, z, top Y, floor Y, head x, z, foot x, z} (for the checks). */
    public static final List<int[]> SHAFTS = new ArrayList<>();
    static final int STEP = rpg.Tuning.i("dungeons.levelStep", 11), COVER = rpg.Tuning.i("dungeons.cover", 5), LOWEST = 4;

    /** The levels of every site, planned (and kept for the runtime: the same plan carved and populated). */
    public static List<DungeonLayout> plan(rpg.world.shadowveil.Terrain t) {
        List<DungeonLayout> out = new ArrayList<>();
        int id = 100;
        for (DungeonSites.Site s : DungeonSites.all()) {
            if (Float.isNaN(s.mouthX())) continue;
            int levels = Math.max(1, s.levels());
            int above = Integer.MAX_VALUE;
            for (int k = 1; k <= levels; k++) {
                DungeonLayout.Type type = levels == 1 ? DungeonLayout.Type.MICRO : k == levels ? DungeonLayout.Type.ELITE : DungeonLayout.Type.MACRO;
                int ox = Math.max(4, Math.min(t.S - type.w - 4, Math.round(s.mouthX() - type.w / 2f)));
                int oz = Math.max(4, Math.min(t.S - type.d - 4, Math.round(s.mouthZ() - type.d / 2f)));
                // under every column of its region by the cover, and a step below the level over it
                int low = Integer.MAX_VALUE;
                for (int x = ox; x < ox + type.w; x++) for (int z = oz; z < oz + type.d; z++) low = Math.min(low, Math.min(t.top[t.idx(x, z)], t.water[t.idx(x, z)] >= 0 ? t.water[t.idx(x, z)] - 4 : Integer.MAX_VALUE));
                int floor = Math.min(low - 8 - COVER, above - STEP);
                if (floor < LOWEST) break;
                long seed = s.id().hashCode() * 0x9E3779B97F4A7C15L + k * 7919L;
                String name = s.name() + (levels > 1 ? ", level " + k : "");
                out.add(DungeonLayout.level(id++, name, type, s.theme(), seed, ox, oz, floor, s.id(), k));
                above = floor;
            }
        }
        return out;
    }

    /** The levels cut into the grown cells: rooms, passages, dressing, and the stairs down from the mouths and between levels. */
    public static void carve(rpg.world.shadowveil.Terrain t, List<DungeonLayout> levels) {
        SHAFTS.clear();
        for (DungeonLayout l : levels) {
            int floorMat = switch (l.theme) { case CAVE -> Blocks.STONE; case WARREN -> Blocks.DIRT; case CRYPT -> Blocks.FLAGSTONE; case HALL -> Blocks.GRANITE; };
            int wall = switch (l.theme) { case CAVE -> Blocks.STONE; case WARREN -> Blocks.MUD; case CRYPT -> Blocks.STONE_BRICKS; case HALL -> Blocks.GRANITE_ASHLAR; };
            int roof = switch (l.theme) { case CAVE, HALL -> Blocks.STONE; case WARREN -> Blocks.DIRT; case CRYPT -> Blocks.STONE_BRICKS; };
            int F = l.floorY;
            for (int x = 0; x < l.w; x++) for (int z = 0; z < l.d; z++) {
                int wx = l.worldX(x), wz = l.worldZ(z);
                if (l.open[x][z]) {
                    int h = l.height[x][z];
                    set(t, wx, F - 1, wz, floorMat);
                    for (int y = F; y < F + h; y++) set(t, wx, y, wz, Blocks.AIR);
                    set(t, wx, F + h, wz, roof);
                    continue;
                }
                int near = 0;
                for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                    int nx = x + dx, nz = z + dz;
                    if (nx >= 0 && nz >= 0 && nx < l.w && nz < l.d && l.open[nx][nz]) near = Math.max(near, l.height[nx][nz]);
                }
                // the walls faced in the theme's stone; a warren's shored with timber every few cells
                if (near > 0) for (int y = F; y <= F + near; y++) set(t, wx, y, wz, l.theme == DungeonLayout.Theme.WARREN && (wx + wz) % 5 == 0 && y < F + 4 ? Blocks.SPRUCE_LOG : wall);
            }
            for (DungeonLayout.Spot s : l.spots) {
                int wx = l.worldX(s.x()), wz = l.worldZ(s.z());
                switch (s.what()) {
                    case "lamp" -> { }   // a torch in a sconce on the wall there (drawn and lit by the renderer: Gfx.dungeonLights)
                    case "embers" -> set(t, wx, F, wz, Blocks.EMBERS);
                    case "pool" -> { set(t, wx, F - 1, wz, Blocks.WATER); set(t, wx, F - 2, wz, Blocks.WATER); }
                    case "pillar" -> { for (int y = F; y < F + 7; y++) { set(t, wx, y, wz, l.theme == DungeonLayout.Theme.CRYPT ? Blocks.STONE_BRICKS : Blocks.GRANITE); set(t, wx + 1, y, wz, Blocks.GRANITE); set(t, wx, y, wz + 1, Blocks.GRANITE); set(t, wx + 1, y, wz + 1, Blocks.GRANITE); } }
                    case "brazier" -> { set(t, wx, F, wz, Blocks.COBBLE); set(t, wx, F + 1, wz, Blocks.EMBERS); }
                    case "boulder" -> set(t, wx, F, wz, Blocks.GRANITE);
                    default -> { }
                }
            }
        }
        // (0.4.8) each level dressed: floors, walls, ceilings and what stands in its rooms
        for (DungeonLayout l : levels) {
            int floorMat = switch (l.theme) { case CAVE -> Blocks.STONE; case WARREN -> Blocks.DIRT; case CRYPT -> Blocks.FLAGSTONE; case HALL -> Blocks.GRANITE; };
            int wall = switch (l.theme) { case CAVE -> Blocks.STONE; case WARREN -> Blocks.MUD; case CRYPT -> Blocks.STONE_BRICKS; case HALL -> Blocks.GRANITE_ASHLAR; };
            dress(t, l, floorMat, wall);
        }
        // the ways down: from each mouth to its first level, from each level's deepest room to the next
        DungeonLayout prev = null;
        for (DungeonLayout l : levels) {
            int ex = l.worldX(l.entryX), ez = l.worldZ(l.entryZ);
            if (l.depth == 1) {
                DungeonSites.Site s = DungeonSites.byId(l.site);
                if (s == null) { prev = l; continue; }
                int mx = (int) Math.floor(s.mouthX()), mz = (int) Math.floor(s.mouthZ());
                int top = t.top[t.idx(mx, mz)] + 1;
                // the stair goes down inside the hill behind the mouth: the way with the most rock over the mouth's
                // floor and no water, its pillar a few cells in
                int[] best = {mx + 4, mz}, head = {1, 0}; int bestRock = Integer.MIN_VALUE;
                for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    int rock = Integer.MAX_VALUE;
                    for (int k = 2; k <= 8; k++) for (int side = -2; side <= 2; side++) {
                        int x = mx + d[0] * k + d[1] * side, z = mz + d[1] * k + d[0] * side;
                        if (!t.in(x, z)) { rock = Integer.MIN_VALUE; continue; }
                        int i = t.idx(x, z);
                        rock = Math.min(rock, t.water[i] >= 0 ? -99 : t.top[i] - top);
                    }
                    if (rock > bestRock) { bestRock = rock; best = new int[]{mx + d[0] * 3, mz + d[1] * 3}; head = d; }
                }
                stairDown(t, best[0], best[1], top, l.floorY, mx, mz, ex, ez, l, head[0], head[1]);
            } else if (prev != null && prev.site.equals(l.site)) {
                DungeonLayout.Room deep = null;
                for (DungeonLayout.Room r : prev.rooms) if (r.kind() == DungeonLayout.RoomKind.BOSS || r.kind() == DungeonLayout.RoomKind.DEN) deep = r;
                if (deep == null) deep = prev.rooms.get(prev.rooms.size() - 1);
                int sx = prev.worldX((int) deep.cx()), sz = prev.worldZ((int) deep.cz());
                int hx = Math.abs(ex - sx) >= Math.abs(ez - sz) ? Integer.signum(ex - sx) : 0, hz = hx == 0 ? Integer.signum(ez - sz) : 0;
                if (hx == 0 && hz == 0) hx = 1;
                stairDown(t, sx, sz, prev.floorY, l.floorY, sx, sz, ex, ez, l, hx, hz);
            }
            prev = l;
        }
    }

    /**
     * The way down from one level to the next: a ladder shaft cut through the rock, walled all round, with a head at the
     * top (ax, az back from it) where the passage from (fromX, fromZ) comes in and a foot at the bottom where a passage
     * goes on to the level's entry. (Spiral and switchback stairs were tried: bodies could not walk them back up.)
     */
    static void stairDown(rpg.world.shadowveil.Terrain t, int cx, int cz, int topY, int floorY, int fromX, int fromZ, int toX, int toZ, DungeonLayout l, int ax, int az) {
        if (System.getenv("RPG_DUNGEON_DEBUG") != null) System.out.println("[underground] " + l.name + ": shaft at " + cx + "," + cz + " from Y" + topY + " to Y" + floorY + ", in from " + fromX + "," + fromZ + ", on to " + toX + "," + toZ);
        int wall = l.theme == DungeonLayout.Theme.CRYPT ? Blocks.STONE_BRICKS : l.theme == DungeonLayout.Theme.WARREN ? Blocks.SPRUCE_LOG : Blocks.STONE;
        // the shaft: a ladder down a column cut in the rock, walled all round (the builders' own ladders, climbed the
        // same way up and down), a head at the top where the passage comes in and a foot at the bottom where it goes on
        int hx = cx - ax, hz = cz - az, fx = cx + ax, fz = cz + az;
        passage(t, fromX, fromZ, hx, hz, topY, l);
        for (int y = floorY - 1; y <= topY + 3; y++) for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            if (dx == 0 && dz == 0) continue;
            int x = cx + dx, z = cz + dz;
            boolean head = x == hx && z == hz && y >= topY, foot = x == fx && z == fz && y >= floorY && y < floorY + 4;
            if (!head && !foot && !solid(t, x, y, z)) set(t, x, y, z, wall);
        }
        for (int y = floorY; y < topY; y++) set(t, cx, y, cz, Blocks.LADDER);
        SHAFTS.add(new int[]{cx, cz, topY, floorY, hx, hz, fx, fz});
        for (int h = 0; h < 4; h++) set(t, cx, topY + h, cz, Blocks.AIR);
        set(t, cx, floorY - 1, cz, Blocks.STONE);
        passage(t, fx, fz, toX, toZ, floorY, l);
    }
    /** A level passage three wide and four high between two points (an L), its floor laid where there was none. */
    static void passage(rpg.world.shadowveil.Terrain t, int ax, int az, int bx, int bz, int y, DungeonLayout l) {
        int x = ax, z = az;
        while (true) {
            for (int i = -1; i <= 1; i++) for (int j = -1; j <= 1; j++) {
                for (int h = 0; h < 4; h++) if (block(t, x + i, y + h, z + j) != Blocks.LADDER) set(t, x + i, y + h, z + j, Blocks.AIR);
                if (!solid(t, x + i, y - 1, z + j)) set(t, x + i, y - 1, z + j, Blocks.STONE);
            }
            if (x == bx && z == bz) break;
            if (x != bx) x += Integer.signum(bx - x); else z += Integer.signum(bz - z);
        }
    }

    private static void set(rpg.world.shadowveil.Terrain t, int x, int y, int z, int b) {
        if (!t.in(x, z) || y < 1 || y >= t.H) return;
        t.cells[t.idx(x, z) * t.H + y] = (byte) b;
    }
    private static int block(rpg.world.shadowveil.Terrain t, int x, int y, int z) { return !t.in(x, z) || y < 0 || y >= t.H ? 0 : t.cells[t.idx(x, z) * t.H + y] & 0xFF; }
    private static boolean solid(rpg.world.shadowveil.Terrain t, int x, int y, int z) {
        if (!t.in(x, z) || y < 0 || y >= t.H) return false;
        int b = t.cells[t.idx(x, z) * t.H + y] & 0xFF;
        return b != 0 && b != Blocks.WATER;
    }

    /**
     * (0.4.8, gamefixes: dungeons did not look good enough - more detailed, however random) each level's rooms dressed for
     * what they are, from the level's own seed, after they are cut:
     * <ul>
     * <li>floors laid in a pattern of the theme's stone - a crypt's flags bordered in brick, a cave's rock strewn with
     *     gravel and a standing puddle or two, a warren's earth trodden to mud with boards down its dens, a hall's granite
     *     worn to gravel along the walls;</li>
     * <li>walls given a plinth of darker stone at their foot and patched through with the theme's other stones (moss in a
     *     crypt, basalt and granite streaks in a cave), the halls' and warrens' shored with timber posts;</li>
     * <li>ceilings that read: timber beams across the halls and warrens, stalactites hanging in the caves;</li>
     * <li>and furnishings against the walls - barrels, crates and sacks in the warrens and mines, a tool rack, a weapon rack;
     *     sarcophagi with candle stands and busts in the crypts; boulders in the caves - each away from the ways through
     *     the room and the level's own spots (its lamps, chests, traps, residents).</li>
     * </ul>
     */
    static void dress(rpg.world.shadowveil.Terrain t, DungeonLayout l, int floorMat, int wall) {
        int F = l.floorY;
        java.util.Random r = new java.util.Random(l.seed ^ 0x5EED_D2E55L);
        boolean[][] busy = new boolean[l.w][l.d];
        for (DungeonLayout.Spot s : l.spots) for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            int x = s.x() + dx, z = s.z() + dz;
            if (x >= 0 && z >= 0 && x < l.w && z < l.d) busy[x][z] = true;
        }
        String owner = l.site == null || l.site.isEmpty() ? "dungeon_" + l.id : l.site;
        for (int x = 0; x < l.w; x++) for (int z = 0; z < l.d; z++) {
            int wx = l.worldX(x), wz = l.worldZ(z);
            if (l.open[x][z]) {
                // ---- the floor's pattern
                boolean edge = !open(l, x - 1, z) || !open(l, x + 1, z) || !open(l, x, z - 1) || !open(l, x, z + 1);
                // (smooth, in patches a few cells across: a cell-by-cell roll read as a chequerboard)
                double n = smooth(l.seed, x, z, 3);
                int fl = switch (l.theme) {
                    case CRYPT -> edge ? Blocks.STONE_BRICKS : (x + z) % 6 == 0 ? Blocks.MOSSY_BRICKS : Blocks.FLAGSTONE;
                    case CAVE -> n < 0.32 ? Blocks.GRAVEL : n > 0.74 ? Blocks.COBBLE : floorMat;
                    case WARREN -> n < 0.36 ? Blocks.MUD : floorMat;
                    case HALL -> edge && n < 0.6 ? Blocks.GRAVEL : n > 0.76 ? Blocks.COBBLE : floorMat;
                };
                set(t, wx, F - 1, wz, fl);
                continue;
            }
            // ---- the walls: a plinth at the foot, patches of the theme's other stones, timber posts in the halls and warrens
            int near = 0;
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) if (open(l, x + dx, z + dz)) near = Math.max(near, l.height[x + dx][z + dz]);
            if (near == 0) continue;
            for (int y = F; y <= F + near; y++) {
                if (block(t, wx, y, wz) != wall && !(l.theme == DungeonLayout.Theme.WARREN && block(t, wx, y, wz) == Blocks.SPRUCE_LOG)) continue;
                double n = smooth(l.seed + y / 2 * 31, x + z, y, 3);
                int b = wall;
                if (y == F) b = switch (l.theme) { case CRYPT -> Blocks.COBBLE; case CAVE -> Blocks.BASALT; case HALL -> Blocks.STONE_BRICKS; case WARREN -> Blocks.COBBLE; };
                else if (l.theme == DungeonLayout.Theme.CRYPT && n > 0.64) b = Blocks.MOSSY_BRICKS;
                else if (l.theme == DungeonLayout.Theme.CAVE && n > 0.68) b = y % 3 == 0 ? Blocks.BASALT : Blocks.GRANITE;
                else if (l.theme == DungeonLayout.Theme.HALL && (wx + wz) % 4 == 0 && y < F + near) b = Blocks.SPRUCE_LOG;
                else continue;
                set(t, wx, y, wz, b);
            }
        }
        // ---- the ceilings: beams across the halls' and warrens' rooms, stalactites in the caves
        for (DungeonLayout.Room room : l.rooms) {
            int h = room.height();
            boolean timber = l.theme == DungeonLayout.Theme.HALL || l.theme == DungeonLayout.Theme.WARREN;
            if (timber && h >= 4) for (int z = room.z0() + 2; z <= room.z1() - 1; z += 4) for (int x = room.x0(); x <= room.x1(); x++)
                if (open(l, x, z) && l.height[x][z] >= 4) set(t, l.worldX(x), F + l.height[x][z] - 1, l.worldZ(z), Blocks.SPRUCE_LOG);
            if (l.theme == DungeonLayout.Theme.CAVE) for (int x = room.x0(); x <= room.x1(); x++) for (int z = room.z0(); z <= room.z1(); z++) {
                if (!open(l, x, z) || l.height[x][z] < 4 || r.nextInt(9) != 0) continue;
                int hh = l.height[x][z];
                set(t, l.worldX(x), F + hh - 1, l.worldZ(z), Blocks.STONE);
                if (hh >= 5 && r.nextBoolean()) set(t, l.worldX(x), F + hh - 2, l.worldZ(z), Blocks.STONE);
            }
        }
        // ---- what stands in the rooms: by theme, against the walls, clear of the ways and the spots
        if (t.arch == null) return;
        for (DungeonLayout.Room room : l.rooms) {
            List<int[]> wallCells = new ArrayList<>();          // {x, z, toward wall dx, dz}
            for (int x = room.x0(); x <= room.x1(); x++) for (int z = room.z0(); z <= room.z1(); z++) {
                if (!open(l, x, z) || busy[x][z]) continue;
                for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                    if (open(l, x + d[0], z + d[1]) || !open(l, x - d[0], z - d[1])) continue;
                    // never beside a way out of the room (a passage's mouth): both cells along the wall must be in the room
                    boolean mouth = false;
                    if (doorway(l, room, x, z)) mouth = true;
                    for (int s : new int[]{-1, 1}) { int ax = x + d[1] * s, az = z + d[0] * s; if (open(l, ax, az) && (ax < room.x0() || ax > room.x1() || az < room.z0() || az > room.z1())) mouth = true; }
                    if (!mouth) wallCells.add(new int[]{x, z, d[0], d[1]});
                }
            }
            java.util.Collections.shuffle(wallCells, r);
            String[] kit = switch (l.theme) {
                case WARREN -> new String[]{"BARREL", "CRATE", "SACKS", "BARREL", "CRATE", "TABLE"};
                case HALL -> new String[]{"BARREL", "CRATE", "SACKS", "CRATE", "BARREL"};
                case CRYPT -> new String[]{"BUST", "CANDELABRA", "BUST", "CANDELABRA"};
                case CAVE -> new String[]{};
            };
            int want = Math.min(wallCells.size() / 3, room.kind() == DungeonLayout.RoomKind.BOSS ? 6 : 4), made = 0;
            for (int[] c : wallCells) {
                if (made >= want || kit.length == 0) break;
                if (busy[c[0]][c[1]]) continue;
                int wx = l.worldX(c[0]), wz = l.worldZ(c[1]);
                int fac = c[3] > 0 ? 0 : c[2] > 0 ? 1 : c[3] < 0 ? 2 : 3;
                String kind = kit[made % kit.length];
                int hh = 1;
                t.arch.pieces.add(new rpg.world.shadowveil.Architecture.Piece(owner, kind, wx, wz, wx, wz, F, hh, fac, "dungeon"));
                busy[c[0]][c[1]] = true;
                for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) { int ax = c[0] + d[0], az = c[1] + d[1]; if (ax >= 0 && az >= 0 && ax < l.w && az < l.d) busy[ax][az] |= made % 2 == 1; }
                made++;
            }
            // a crypt's sarcophagi: a raised stone coffin, two cells long, along the room's middle
            if (l.theme == DungeonLayout.Theme.CRYPT && room.kind() != DungeonLayout.RoomKind.ENTRY && room.x1() - room.x0() >= 5 && room.z1() - room.z0() >= 5) {
                int cx = (room.x0() + room.x1()) / 2, cz = (room.z0() + room.z1()) / 2;
                for (int k = -1; k <= 1; k += 2) {
                    int sx = cx + k * 2;
                    if (!open(l, sx, cz) || !open(l, sx, cz + 1) || busy[sx][cz] || busy[sx][cz + 1]) continue;
                    set(t, l.worldX(sx), F, l.worldZ(cz), Blocks.STONE_BRICKS); set(t, l.worldX(sx), F, l.worldZ(cz + 1), Blocks.STONE_BRICKS);
                    busy[sx][cz] = busy[sx][cz + 1] = true;
                }
            }
            // a cave's boulders: a few heaped against its walls
            if (l.theme == DungeonLayout.Theme.CAVE) for (int k = 0; k < Math.min(3, wallCells.size()); k++) {
                int[] c = wallCells.get(k);
                if (busy[c[0]][c[1]]) continue;
                set(t, l.worldX(c[0]), F, l.worldZ(c[1]), r.nextBoolean() ? Blocks.GRANITE : Blocks.STONE);
                busy[c[0]][c[1]] = true;
            }
        }
    }
    private static boolean open(DungeonLayout l, int x, int z) { return x >= 0 && z >= 0 && x < l.w && z < l.d && l.open[x][z]; }
    private static boolean doorway(DungeonLayout l, DungeonLayout.Room room, int x, int z) {
        // a cell on the room's rim with an open cell outside the room beside it: where a passage comes in
        for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            int ax = x + d[0], az = z + d[1];
            if (open(l, ax, az) && (ax < room.x0() || ax > room.x1() || az < room.z0() || az > room.z1())) return true;
        }
        return false;
    }
    /** Value noise smoothed over cells of the given size (patches, not single cells), 0..1. */
    private static double smooth(long seed, int x, int z, int size) {
        int gx = Math.floorDiv(x, size), gz = Math.floorDiv(z, size);
        double fx = (x - gx * size) / (double) size, fz = (z - gz * size) / (double) size;
        fx = fx * fx * (3 - 2 * fx); fz = fz * fz * (3 - 2 * fz);
        double a = noise(seed, gx, gz), b = noise(seed, gx + 1, gz), c = noise(seed, gx, gz + 1), d = noise(seed, gx + 1, gz + 1);
        return (a * (1 - fx) + b * fx) * (1 - fz) + (c * (1 - fx) + d * fx) * fz;
    }
    private static double noise(long seed, int x, int z) {
        long h = seed * 0x9E3779B97F4A7C15L + x * 0xBF58476D1CE4E5B9L + z * 0x94D049BB133111EBL;
        h ^= h >>> 31; h *= 0x7FB5D329728EA185L; h ^= h >>> 27;
        return (h >>> 11) * 0x1.0p-53;
    }
}
