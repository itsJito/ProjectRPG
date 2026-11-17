
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
                double n = noise(l.seed, x, z);
                int fl = switch (l.theme) {
                    case CRYPT -> edge ? Blocks.STONE_BRICKS : (x + z) % 6 == 0 ? Blocks.MOSSY_BRICKS : Blocks.FLAGSTONE;
                    case CAVE -> n < 0.22 ? Blocks.GRAVEL : n > 0.86 ? Blocks.COBBLE : floorMat;
                    case WARREN -> n < 0.25 ? Blocks.MUD : floorMat;
                    case HALL -> edge && n < 0.6 ? Blocks.GRAVEL : n > 0.9 ? Blocks.COBBLE : floorMat;
                };
                set(t, wx, F - 1, wz, fl);
                // a standing puddle in a cave's low ground, away from the walls and the ways
                if (l.theme == DungeonLayout.Theme.CAVE && !edge && !busy[x][z] && n > 0.95) set(t, wx, F - 1, wz, Blocks.WATER);
                continue;
            }
            // ---- the walls: a plinth at the foot, patches of the theme's other stones, timber posts in the halls and warrens
            int near = 0;
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) if (open(l, x + dx, z + dz)) near = Math.max(near, l.height[x + dx][z + dz]);
            if (near == 0) continue;
            for (int y = F; y <= F + near; y++) {
                if (block(t, wx, y, wz) != wall && !(l.theme == DungeonLayout.Theme.WARREN && block(t, wx, y, wz) == Blocks.SPRUCE_LOG)) continue;
                double n = noise(l.seed + y * 31, x, z);
                int b = wall;
                if (y == F) b = switch (l.theme) { case CRYPT -> Blocks.COBBLE; case CAVE -> Blocks.BASALT; case HALL -> Blocks.STONE_BRICKS; case WARREN -> Blocks.COBBLE; };
                else if (l.theme == DungeonLayout.Theme.CRYPT && n > 0.72) b = Blocks.MOSSY_BRICKS;
                else if (l.theme == DungeonLayout.Theme.CAVE && n > 0.8) b = y % 3 == 0 ? Blocks.BASALT : Blocks.GRANITE;
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
    private static double noise(long seed, int x, int z) {
        long h = seed * 0x9E3779B97F4A7C15L + x * 0xBF58476D1CE4E5B9L + z * 0x94D049BB133111EBL;
        h ^= h >>> 31; h *= 0x7FB5D329728EA185L; h ^= h >>> 27;
        return (h >>> 11) * 0x1.0p-53;
    }
