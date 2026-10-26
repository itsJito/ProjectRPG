package rpg.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * The dungeons' plans (gamefixes: a dungeon system - micro, macro, boss and elite dungeons, each in its own map). A plan
 * is a pure function of its seed: the world is carved from it when it is built ({@link DevMap}), and the dungeon's
 * runtime (rpg.dungeon.Dungeons) places its lights' fixtures, chests, traps, hazards and residents from the very same
 * plan - so what is carved and what lives in it always agree, across every load.
 *
 * <p>Every dungeon has a sealed region of its own beyond the overworld's edge (x from {@link #X0}), roofed and walled in
 * stone: there is no way into it but its entrance, and no way out but its exit. The procedural kinds (micro, macro,
 * elite) are rooms scattered through their region and joined by a spanning tree of corridors with a loop or two; the
 * boss dungeon is authored - an antechamber, a long approach and the arena it is built round.
 */
public final class DungeonLayout {
    /** The dungeon region starts here (the overworld is x 0 .. DevMap.SX - 1; the gap between is solid rock). */
    public static final int X0 = DevMap.SX + 8;
    /** The world's full width: the overworld and the dungeon region beyond it. */
    public static final int WORLD_X = 248;
    /** The rock's top over the dungeons (grassed: from the overworld it is a cliff beyond the border). */
    public static final int ROCK_TOP = DevMap.FLOOR + 9;

    public enum Type {
        MICRO("Micro-dungeon", 28, 28, 3, 4, 3, 1.0f, true),
        MACRO("Dungeon", 60, 56, 8, 11, 2, 1.0f, true),
        ELITE("Elite dungeon", 44, 44, 5, 7, 3, 2.6f, true),
        BOSS("Boss dungeon", 40, 52, 0, 0, 3, 3.0f, false);
        public final String title;
        /** The region's size (cells), how many rooms it has (min..max), its corridors' width, how hard its residents are, and whether it is generated. */
        public final int w, d, minRooms, maxRooms, corridor;
        public final float threat;
        public final boolean procedural;
        Type(String title, int w, int d, int minRooms, int maxRooms, int corridor, float threat, boolean procedural) {
            this.title = title; this.w = w; this.d = d; this.minRooms = minRooms; this.maxRooms = maxRooms; this.corridor = corridor; this.threat = threat; this.procedural = procedural;
        }
    }

    /** What it is built of and who lives there. */
    public enum Theme { CAVE, WARREN, CRYPT, HALL }

    public enum RoomKind { ENTRY, CHAMBER, DEN, TREASURE, HAZARD, BOSS }
    public record Room(int x0, int z0, int x1, int z1, RoomKind kind, int height) {
        public float cx() { return (x0 + x1) / 2f + 0.5f; }
        public float cz() { return (z0 + z1) / 2f + 0.5f; }
        public boolean contains(float x, float z) { return x >= x0 && x <= x1 + 1 && z >= z0 && z <= z1 + 1; }
    }
    /** A thing to place at a cell: a lamp in a wall, a chest, a trap, a hazard, a pillar, a resident's spawn; with a facing or a strength. */
    public record Spot(String what, int x, int z, int arg) {}

    public final int id;
    public final String name;
    public final Type type;
    public final Theme theme;
    public final long seed;
    /** The region's corner (world cells) and size. */
    public final int ox, oz, w, d;
    /** Open floor (true: a body may stand there), and the ceiling height over each open cell. */
    public final boolean[][] open;
    public final int[][] height;
    public final List<Room> rooms = new ArrayList<>();
    public final List<Spot> spots = new ArrayList<>();
    /** Where one arrives (and leaves by): the exit's cell inside. */
    public int entryX, entryZ;

    private DungeonLayout(int id, String name, Type type, Theme theme, long seed, int ox, int oz) {
        this.id = id; this.name = name; this.type = type; this.theme = theme; this.seed = seed; this.ox = ox; this.oz = oz; this.w = type.w; this.d = type.d;
        open = new boolean[w][d]; height = new int[w][d];
        if (type.procedural) generate(); else authorBoss();
    }

    // ------------------------------------------------------------------ the world's dungeons
    private static List<DungeonLayout> all;
    /** The dungeons of the world, in a fixed order (their ids are their indices). */
    public static synchronized List<DungeonLayout> all() {
        if (all == null) {
            all = List.of(
                    new DungeonLayout(0, "Goblin Warren", Type.MACRO, Theme.WARREN, 0x6A0B11L, X0, 4),
                    new DungeonLayout(1, "Spider Hollow", Type.MICRO, Theme.CAVE, 0x5B1D3EL, X0 + 64, 4),
                    new DungeonLayout(2, "Old Catacombs", Type.ELITE, Theme.CRYPT, 0xCA7A00L, X0, 66),
                    new DungeonLayout(3, "Troll's Den", Type.BOSS, Theme.HALL, 0x7B0115L, X0 + 50, 66));
        }
        return all;
    }
    /** The dungeon whose region holds a point, or null (the overworld). */
    public static DungeonLayout at(float x, float z) {
        if (x < X0 - 1) return null;
        for (DungeonLayout l : all()) if (x >= l.ox && x < l.ox + l.w && z >= l.oz && z < l.oz + l.d) return l;
        return null;
    }
    public static boolean inDungeonRegion(float x) { return x >= DevMap.SX; }

    // ------------------------------------------------------------------ generation (micro, macro, elite)
    private void generate() {
        Random r = new Random(seed);
        int want = type.minRooms + r.nextInt(type.maxRooms - type.minRooms + 1);
        int minSize = type == Type.MICRO ? 6 : 5, maxSize = type == Type.MICRO ? 10 : type == Type.ELITE ? 10 : 9;
        for (int tries = 0; tries < 400 && rooms.size() < want; tries++) {
            int rw = minSize + r.nextInt(maxSize - minSize + 1), rd = minSize + r.nextInt(maxSize - minSize + 1);
            int x0 = 2 + r.nextInt(Math.max(1, w - rw - 4)), z0 = 2 + r.nextInt(Math.max(1, d - rd - 4));
            boolean clash = false;
            for (Room o : rooms) if (x0 - 2 <= o.x1() && x0 + rw + 1 >= o.x0() && z0 - 2 <= o.z1() && z0 + rd + 1 >= o.z0()) { clash = true; break; }
            if (clash) continue;
            rooms.add(new Room(x0, z0, x0 + rw - 1, z0 + rd - 1, RoomKind.CHAMBER, 4 + (rw * rd > 60 ? 1 : 0)));
        }
        // the entry: the room nearest the region's west edge (the side the overworld is on)
        Room entry = rooms.get(0);
        for (Room o : rooms) if (o.x0() < entry.x0()) entry = o;
        rooms.remove(entry); rooms.add(0, new Room(entry.x0(), entry.z0(), entry.x1(), entry.z1(), RoomKind.ENTRY, entry.height()));
        // joined by a spanning tree (Prim's, nearest first), and a loop or two so it is not a single line of rooms
        List<int[]> links = new ArrayList<>();
        boolean[] in = new boolean[rooms.size()];
        in[0] = true;
        for (int k = 1; k < rooms.size(); k++) {
            int bi = -1, bj = -1; float bd = Float.MAX_VALUE;
            for (int i = 0; i < rooms.size(); i++) if (in[i]) for (int j = 0; j < rooms.size(); j++) if (!in[j]) {
                float dd = (float) Math.hypot(rooms.get(i).cx() - rooms.get(j).cx(), rooms.get(i).cz() - rooms.get(j).cz());
                if (dd < bd) { bd = dd; bi = i; bj = j; }
            }
            in[bj] = true; links.add(new int[]{bi, bj});
        }
        int loops = type == Type.MACRO ? 2 : 1;
        for (int k = 0; k < loops && rooms.size() > 3; k++) links.add(new int[]{1 + r.nextInt(rooms.size() - 1), 1 + r.nextInt(rooms.size() - 1)});
        // how deep each room lies (links from the entry): the deeper, the worse what lives there
        int[] depth = new int[rooms.size()];
        java.util.Arrays.fill(depth, -1); depth[0] = 0;
        for (int pass = 0; pass < rooms.size(); pass++) for (int[] l : links) {
            if (depth[l[0]] >= 0 && depth[l[1]] < 0) depth[l[1]] = depth[l[0]] + 1;
            if (depth[l[1]] >= 0 && depth[l[0]] < 0) depth[l[0]] = depth[l[1]] + 1;
        }
        // what each room is: the deepest a den (an elite's, its master's chamber); one a treasure room; one a hazard
        int deepest = 0;
        for (int i = 1; i < rooms.size(); i++) if (depth[i] > depth[deepest]) deepest = i;
        for (int i = 1; i < rooms.size(); i++) {
            Room o = rooms.get(i);
            RoomKind kind = i == deepest ? (type == Type.ELITE ? RoomKind.BOSS : RoomKind.DEN) : i % 4 == 2 ? RoomKind.TREASURE : i % 4 == 3 ? RoomKind.HAZARD : RoomKind.DEN;
            if (type == Type.MICRO && kind == RoomKind.HAZARD) kind = RoomKind.DEN;
            rooms.set(i, new Room(o.x0(), o.z0(), o.x1(), o.z1(), kind, o.height()));
        }
        for (Room o : rooms) carveRoom(o);
        for (int[] l : links) corridor(rooms.get(l[0]), rooms.get(l[1]), r);
        entryX = (int) rooms.get(0).cx(); entryZ = (int) rooms.get(0).cz();
        furnish(r, depth);
    }

    private void carveRoom(Room o) {
        for (int x = o.x0(); x <= o.x1(); x++) for (int z = o.z0(); z <= o.z1(); z++) { open[x][z] = true; height[x][z] = Math.max(height[x][z], o.height()); }
    }

    /** An L-shaped corridor between two rooms' middles, the corridor's width wide; a dog-leg one way or the other. */
    private void corridor(Room a, Room b, Random r) {
        int ax = (int) a.cx(), az = (int) a.cz(), bx = (int) b.cx(), bz = (int) b.cz(), cw = type.corridor;
        boolean xFirst = r.nextBoolean();
        int mx = xFirst ? bx : ax, mz = xFirst ? az : bz;
        line(ax, az, mx, mz, cw); line(mx, mz, bx, bz, cw);
    }
    private void line(int x0, int z0, int x1, int z1, int cw) {
        int sx = Integer.signum(x1 - x0), sz = Integer.signum(z1 - z0), x = x0, z = z0;
        while (true) {
            for (int i = 0; i < cw; i++) for (int j = 0; j < cw; j++) {
                int cx = x - cw / 2 + i, cz = z - cw / 2 + j;
                if (cx < 1 || cz < 1 || cx >= w - 1 || cz >= d - 1) continue;
                if (!open[cx][cz]) { open[cx][cz] = true; height[cx][cz] = 3; }
            }
            if (x == x1 && z == z1) break;
            if (x != x1) x += sx; else z += sz;
        }
    }

    /** Lamps along the walls, the residents of each room, chests, traps in the corridors, hazards; all from the same seed. */
    private void furnish(Random r, int[] depth) {
        for (int i = 0; i < rooms.size(); i++) {
            Room o = rooms.get(i);
            // a lamp in the middle of two of its walls (the crypt's and the hall's lit better than a cave's)
            int lamps = theme == Theme.CAVE ? 1 : 2;
            wallLamp(o.x0() - 1, (o.z0() + o.z1()) / 2, 1);
            if (lamps > 1) wallLamp(o.x1() + 1, (o.z0() + o.z1()) / 2, 3);
            int level = depth[i] < 0 ? 1 : depth[i];
            switch (o.kind()) {
                case ENTRY -> spots.add(new Spot("exit", entryX, entryZ, 0));
                case DEN -> { int n = (o.x1() - o.x0() + 1) * (o.z1() - o.z0() + 1) > 50 ? 3 : 2; for (int k = 0; k < n; k++) spots.add(new Spot("resident", o.x0() + 1 + r.nextInt(Math.max(1, o.x1() - o.x0() - 1)), o.z0() + 1 + r.nextInt(Math.max(1, o.z1() - o.z0() - 1)), level)); }
                case BOSS -> { spots.add(new Spot("master", (int) o.cx(), (int) o.cz(), level + 2)); spots.add(new Spot("resident", o.x0() + 1, o.z0() + 1, level)); spots.add(new Spot("resident", o.x1() - 1, o.z1() - 1, level)); spots.add(new Spot("chest", o.x1() - 1, o.z0() + 1, 3)); }
                case TREASURE -> { spots.add(new Spot("chest", (int) o.cx(), o.z0() + 1, 1 + level / 2)); spots.add(new Spot("resident", (int) o.cx() - 1, (int) o.cz(), level)); spots.add(new Spot("trap", (int) o.cx(), (int) o.cz() - 1, 0)); }
                case HAZARD -> {
                    // a fire pit across the middle, or a sunken pool, with a way round it
                    String hz = r.nextBoolean() ? "embers" : "pool";
                    for (int x = o.x0() + 1; x < o.x1(); x++) for (int z = (int) o.cz() - 1; z <= (int) o.cz(); z++) if (x != o.x0() + 1) spots.add(new Spot(hz, x, z, 0));
                    spots.add(new Spot("resident", o.x1() - 1, o.z1() - 1, level));
                }
                default -> { }
            }
        }
        // traps in the corridors: on cells of corridor (not room) floor, spaced apart, never near the entry
        int traps = type == Type.MICRO ? 1 : type == Type.ELITE ? 6 : 4;
        for (int t = 0, tries = 0; t < traps && tries < 600; tries++) {
            int x = 1 + r.nextInt(w - 2), z = 1 + r.nextInt(d - 2);
            if (!open[x][z] || height[x][z] != 3 || Math.hypot(x - entryX, z - entryZ) < 8) continue;
            boolean near = false;
            for (Spot s : spots) if (s.what().equals("trap") && Math.hypot(s.x() - x, s.z() - z) < 6) near = true;
            if (near) continue;
            spots.add(new Spot("trap", x, z, r.nextInt(2)));
            t++;
        }
    }

    /** A lamp on a wall, at the wall cell nearest the one asked for along the wall (a doorway there moves it along). */
    private void wallLamp(int x, int z, int facing) {
        for (int k = 0; k < 6; k++) for (int sgn = -1; sgn <= 1; sgn += 2) {
            int zz = z + sgn * k;
            if (x >= 0 && x < w && zz >= 0 && zz < d && !open[x][zz] && open[Math.max(0, Math.min(w - 1, x + (facing == 1 ? 1 : -1)))][zz]) { spots.add(new Spot("lamp", x, zz, facing)); return; }
        }
    }

    // ------------------------------------------------------------------ the boss dungeon (authored)
    /**
     * The Troll's Den: a small antechamber at the door, a long dark approach that narrows and turns, and the hall it was
     * dug for - a high arena with pillars to break a thrown boulder's line, braziers in its corners (a troll does not
     * mend while it burns), its master at the far end and its hoard behind it.
     */
    private void authorBoss() {
        Room ante = new Room(3, 22, 9, 30, RoomKind.ENTRY, 4);
        Room arena = new Room(18, 6, 36, 46, RoomKind.BOSS, 7);
        rooms.add(ante); rooms.add(arena);
        carveRoom(ante); carveRoom(arena);
        line(9, 26, 13, 26, 3); line(13, 26, 13, 34, 3); line(13, 34, 18, 34, 3);
        entryX = 5; entryZ = 26;
        spots.add(new Spot("exit", entryX, entryZ, 0));
        wallLamp(2, 26, 1); wallLamp(10, 23, 3);
        for (int z = 16; z <= 36; z += 10) { wallLamp(17, z, 1); wallLamp(37, z, 3); }
        // pillars, two rows down the hall; braziers in its four corners; boulders lying about
        for (int z = 12; z <= 40; z += 7) { spots.add(new Spot("pillar", 22, z, 0)); spots.add(new Spot("pillar", 32, z, 0)); }
        for (int[] c : new int[][]{{19, 7}, {35, 7}, {19, 45}, {35, 45}}) spots.add(new Spot("brazier", c[0], c[1], 0));
        for (int[] c : new int[][]{{25, 18}, {29, 30}, {26, 40}}) spots.add(new Spot("boulder", c[0], c[1], 0));
        spots.add(new Spot("master", 27, 12, 5));
        spots.add(new Spot("chest", 27, 7, 4));
        spots.add(new Spot("trap", 13, 30, 1));
    }

    // ------------------------------------------------------------------ helpers
    public int worldX(int x) { return ox + x; }
    public int worldZ(int z) { return oz + z; }
    public boolean openAt(int wx, int wz) { int x = wx - ox, z = wz - oz; return x >= 0 && z >= 0 && x < w && z < d && open[x][z]; }
    public Room roomAt(float wx, float wz) { for (Room r : rooms) if (r.contains(wx - ox, wz - oz)) return r; return null; }
    /** Waypoints for routing inside: each room's middle and every corridor bend (world coordinates). */
    public List<float[]> waypoints() {
        List<float[]> l = new ArrayList<>();
        for (Room r : rooms) l.add(new float[]{ox + r.cx(), oz + r.cz()});
        return l;
    }
    public String threatWord() { return type.threat >= 2.9f ? "deadly" : type.threat >= 2.5f ? "extreme" : type == Type.MICRO ? "low" : "moderate"; }
}
