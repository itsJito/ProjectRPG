package rpg.world.shadowveil;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import rpg.world.Blocks;

/**
 * (0.4.8, gamefixes: the basement at 644, 40, 552 led nowhere) the places every stair down ends at - a castle's
 * undercroft, an inn's cellar, a museum's vault, a lighthouse tower's cellars - cut into the rock beyond the door at its
 * foot, fitted for what they are, lit, and the door hung as a door. (A dungeon's mouth goes on to its levels instead:
 * rpg.dungeon.Underground.)
 *
 * <p>Each is laid out from its own seed: a vestibule in from the door (a flight of steps down first where the rooms must
 * lie deeper for rock over their vaults), a vaulted hall (pillared when it is wide), side chambers off it through arches,
 * a back chamber at its end - each of a size the seed picks - turned onto the ground the way with most rock over it
 * (straight on, or left or right at the vestibule's foot), and opened only where there is rock enough over its vault, no
 * water above it, and nobody else's building or kept volume. Its walls, floor and vault are of the stair's own lining.
 * Its rooms are furnished from {@code dungeons.json} "cellars" by what the place is (stores, wine, an undercroft's arms, a
 * vault's records): what stands against a wall stands against one, a store chest is set in the back chamber, sconces
 * light every room, and the hall's aisle and every arch are kept clear. Whatever cannot be walked to from the door is
 * filled back in.
 */
public final class Cellars {
    private Cellars() {}

    /** What the last cut made, for the checks: {cellars, rooms, pieces, lights} and per-cellar notes. */
    public static final List<String> NOTES = new ArrayList<>();
    public static int cut, rooms, pieces, lights;

    public static void cutAll(Terrain t) {
        NOTES.clear(); cut = rooms = pieces = lights = 0;
        Architecture ar = t.arch;
        if (ar == null || t.cells == null) return;
        Map<String, Object> data = data();
        for (Architecture.CellarDoor d : ar.cellars) {
            if (rpg.dungeon.DungeonSites.byId(d.owner()) != null) continue;   // a dungeon's mouth: its levels go on below
            Map<String, Object> spec = astratto.util.Json.obj(data, d.kind());
            if (spec == null) spec = astratto.util.Json.obj(data, "cellar");
            if (spec == null) continue;
            Map<String, Object> dress = astratto.util.Json.obj(astratto.util.Json.obj(data, "dress"), astratto.util.Json.str(spec, "dress", "stores"));
            new Cut(t, ar, d, spec, dress == null ? Map.of() : dress).run();
        }
    }

    private static Map<String, Object> data() {
        try (InputStream in = Cellars.class.getResourceAsStream("/data/dungeons.json")) {
            if (in == null) return Map.of();
            Map<String, Object> root = astratto.util.Json.parseObject(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            Map<String, Object> c = astratto.util.Json.obj(root, "cellars");
            return c == null ? Map.of() : c;
        } catch (Exception e) { throw new IllegalStateException("data/dungeons.json could not be read", e); }
    }

    /**
     * One cellar, in local cells: a along the way on from the door, b across it (the door's cells at b 0 and 1). The plan
     * is drawn in its own cells (p along its hall, q across) and turned onto the ground.
     */
    private static final class Cut {
        static final int A_LO = -20, A_HI = 20, B_LO = -20, B_HI = 21;
        static final int[][] FOUR = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}, EIGHT = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
        final Terrain t; final Architecture ar; final Architecture.CellarDoor d; final Map<String, Object> spec, dress;
        final int W = B_HI - B_LO + 1, L = A_HI - A_LO + 1;
        /** Clear height above the feet (0: rock), the room each cell belongs to (-1: none), kept clear (aisles, arches), taken. */
        final int[][] high = new int[W][L], room = new int[W][L];
        final boolean[][] keep = new boolean[W][L], used = new boolean[W][L];
        /** A cell's own feet level where it is not the rooms' (the vestibule's flight down); 0 the rooms'. */
        final int[][] feet = new int[W][L];
        final int sx, sz;    // across: the second door cell from the first
        final int F0;        // the feet's cell at the door
        int F, drop;         // the rooms' feet cell: the door's, less the flight down
        int turn, vest;      // 0 straight on, -1 left, 1 right; the vestibule's length
        final long seed;
        int roomCount;
        final List<int[]> lightAt = new ArrayList<>();

        Cut(Terrain t, Architecture ar, Architecture.CellarDoor d, Map<String, Object> spec, Map<String, Object> dress) {
            this.t = t; this.ar = ar; this.d = d; this.spec = spec; this.dress = dress;
            sx = Integer.signum(d.bx() - d.ax()); sz = Integer.signum(d.bz() - d.az());
            F0 = F = d.footY();
            seed = (long) d.owner().hashCode() * 0x9E3779B97F4A7C15L ^ d.kind().hashCode();
            for (int[] r : room) java.util.Arrays.fill(r, -1);
        }
        int wx(int a, int b) { return d.ax() + d.dx() * a + sx * b; }
        int wz(int a, int b) { return d.az() + d.dz() * a + sz * b; }
        double rnd(int salt) { return Noise.hash(seed, salt * 31 + 7, salt * 17 + 3, 4811); }
        int pick(int lo, int hi, int salt) { return lo + (int) Math.floor(rnd(salt) * (hi - lo + 1 - 1e-9)); }
        /** Within the plan's reach, and never in the stair coming down to the door (or its walls) behind it. */
        boolean inBox(int a, int b) { return a >= A_LO && a <= A_HI && b >= B_LO && b <= B_HI && !(a <= 0 && b >= -2 && b <= 3); }
        /** The plan's cell (p, q) on the ground (a, b): p = vest + 1 is the hall's first row, q 0 and 1 its aisle. */
        int pa(int p, int q) { return switch (turn) { case 1 -> vest + 1 - q; case -1 -> vest + q; case 2, -2 -> 2 * vest + 1 - p; default -> p; }; }
        int pb(int p, int q) { return switch (turn) { case 1 -> p - vest + 1; case -1 -> -(p - vest); case 2 -> 4 + q; case -2 -> -3 - q; default -> q; }; }

        /** Rock enough over the vault (and no water above), and nobody else's building or kept volume there: a cell may be opened. */
        boolean coverAt(int a, int b, int h) {
            if (!inBox(a, b)) return false;
            int x = wx(a, b), z = wz(a, b);
            if (!t.in(x, z)) return false;
            int i = t.idx(x, z);
            // under water only with three cells of rock between the vault and the bed
            if (t.top[i] < F + h + (t.water[i] < 0 ? 1 : 3)) return false;
            for (Architecture.Structure o : ar.structures) if (!o.id.equals(d.owner()) && o.contains(x, z) && o.floor - 26 <= F + h) return false;
            for (Architecture.Reserved r : ar.reserved)
                if (!r.owner().equals(d.owner()) && x >= r.x0() && x <= r.x1() && z >= r.z0() && z <= r.z1() && r.y0() <= F + h && r.y1() >= F - 1) return false;
            return true;
        }

        /**
         * How far the rooms go down past the door and which way they turn: of every flight (up to five steps) and turn,
         * the one with the most of the plan's cells under rock enough, each step down and a turn costing a little (a
         * cellar under a lakeshore goes deeper, or turns in under higher ground, rather than out under the sky).
         */
        void choosePlan() {
            int bestDrop = 0, bestTurn = 0; double best = -1;
            for (int tr : new int[]{0, -1, 1, 2, -2}) for (int k = 0; k <= 5; k++) {
                F = F0 - k; turn = tr; vest = Math.max(pick(2, 3, 1), k + 1);
                if (F < 6) break;
                int n = 0;
                for (int p = vest + 1; p <= vest + 17; p++) for (int q = -6; q <= 7; q++) if (coverAt(pa(p, q), pb(p, q), 4)) n++;
                double score = n - k * 4 - (tr == 0 ? 0 : Math.abs(tr) * 6);
                if (score > best) { best = score; bestDrop = k; bestTurn = tr; }
            }
            drop = bestDrop; turn = bestTurn; F = F0 - drop; vest = Math.max(pick(2, 3, 1), drop + 1);
        }

        /** Opens the plan's cells p0..p1 by q0..q1 (wherever rock allows) to a clear height, for a room (id) or a passage (-1). */
        void open(int p0, int p1, int q0, int q1, int h, int id) {
            for (int p = Math.min(p0, p1); p <= Math.max(p0, p1); p++) for (int q = Math.min(q0, q1); q <= Math.max(q0, q1); q++) {
                int a = pa(p, q), b = pb(p, q);
                if (!coverAt(a, b, h) || feet[b - B_LO][(a) - A_LO] != 0) continue;
                high[b - B_LO][(a) - A_LO] = Math.max(high[b - B_LO][(a) - A_LO], h);
                if (id >= 0 && room[b - B_LO][(a) - A_LO] < 0) room[b - B_LO][(a) - A_LO] = id;
            }
        }
        void keepPlan(int p, int q) { int a = pa(p, q), b = pb(p, q); if (inBox(a, b)) keep[b - B_LO][(a) - A_LO] = true; }
        int h(int a, int b) { return inBox(a, b) ? high[b - B_LO][(a) - A_LO] : (a == 0 && (b == 0 || b == 1) ? 2 : 0); }
        /** The feet's cell at (a, b): the door's at the door, a step lower each tread of the flight, the rooms' beyond. */
        int feetAt(int a, int b) { return a == 0 && (b == 0 || b == 1) ? F0 : inBox(a, b) && feet[b - B_LO][(a) - A_LO] != 0 ? feet[b - B_LO][(a) - A_LO] : F; }

        void run() {
            // ---- the plan, from the seed
            choosePlan();
            // the vestibule: in from the door, two wide - a flight of steps down first when the rooms lie deeper; its
            // vault level with the door's head all the way down
            for (int a = 1; a <= vest; a++) for (int b = 0; b <= 1; b++) {
                int fa = Math.max(F, F0 - a);
                feet[b - B_LO][(a) - A_LO] = fa;
                high[b - B_LO][(a) - A_LO] = F0 + 3 - fa;
                keep[b - B_LO][(a) - A_LO] = true;
            }
            boolean wide = rnd(2) < 0.65;
            int hq0 = wide ? -2 : -1, hq1 = wide ? 3 : 2;               // the hall across
            int ha0 = vest + 1, ha1 = ha0 + pick(7, 11, 3);
            int hall = ++roomCount;
            open(ha0, ha1, hq0, hq1, 3, hall);
            open(ha0, ha1, hq0 + 1, hq1 - 1, 4, hall);                  // the vault: higher down the middle
            for (int p = ha0; p <= ha1; p++) for (int q = 0; q <= 1; q++) keepPlan(p, q);   // the aisle
            // turned back beside the stair: a passage across from the vestibule's foot to the hall's aisle
            if (Math.abs(turn) == 2) for (int b = turn > 0 ? 2 : -1; turn > 0 ? b <= 3 : b >= -2; b += turn > 0 ? 1 : -1) for (int a = vest - 1; a <= vest; a++)
                if (coverAt(a, b, 3)) { high[b - B_LO][(a) - A_LO] = Math.max(3, high[b - B_LO][(a) - A_LO]); keep[b - B_LO][(a) - A_LO] = true; }
            List<int[]> pillars = new ArrayList<>();
            if (wide) for (int p = ha0 + 2; p <= ha1 - 1; p += 3) { pillars.add(new int[]{p, hq0 + 1}); pillars.add(new int[]{p, hq1 - 1}); }
            // side chambers through arches in the hall's walls, each side its own run
            for (int side : new int[]{-1, 1}) {
                int deep = pick(3, 4, 12 + side), wallQ = side < 0 ? hq0 - 1 : hq1 + 1, rq0 = side < 0 ? wallQ - deep : wallQ + 1, rq1 = side < 0 ? wallQ - 1 : wallQ + deep;
                if (Math.abs(rq1 - rq0) < 1) continue;
                int p = ha0 + pick(0, 1, 10 + side);
                while (p + 2 <= ha1) {
                    int len = pick(3, 4, 20 + side * 3 + p);
                    int p1 = Math.min(ha1, p + len - 1);
                    if (p1 - p < 2) break;
                    if (rnd(30 + side * 5 + p) < 0.8) {
                        int id = ++roomCount;
                        open(p, p1, rq0, rq1, 3, id);
                        int arch = (p + p1) / 2, in = wallQ - side;
                        open(arch, arch, wallQ, wallQ, 2, -1);
                        keepPlan(arch, wallQ); keepPlan(arch, in); keepPlan(arch, wallQ + side);
                        pillars.removeIf(pl -> Math.abs(pl[0] - arch) <= 1 && pl[1] == in);
                    }
                    p = p1 + 2;
                }
            }
            // the back chamber at the hall's end
            int ba0 = ha1 + 2, ba1 = ba0 + pick(3, 5, 40), bw = pick(2, 4, 41);
            ++roomCount;
            open(ba0, ba1, 1 - bw, bw, 3, roomCount);
            open(ha1 + 1, ha1 + 1, 0, 1, 3, -1);
            for (int q = 0; q <= 1; q++) { keepPlan(ha1 + 1, q); keepPlan(ba0, q); }
            for (int[] pl : pillars) { int a = pa(pl[0], pl[1]), b = pb(pl[0], pl[1]); if (inBox(a, b)) high[b - B_LO][(a) - A_LO] = 0; }
            // ---- only what the door reaches stays open
            reach();
            // ---- cut: the lining round and over every open cell, the floor under it, the air in it
            int lining = d.lining() == 0 ? Blocks.STONE_BRICKS : d.lining();
            int floor = Blocks.FLAGSTONE, vault = lining;
            for (int a = A_LO - 1; a <= A_HI + 1; a++) for (int b = B_LO - 1; b <= B_HI + 1; b++) {
                int x = wx(a, b), z = wz(a, b);
                if (!t.in(x, z) || a <= 0 && b >= -1 && b <= 2) continue;          // the stair down and its door: Kit's
                int hh = h(a, b);
                if (hh > 0) {
                    int fy = feetAt(a, b);
                    set(x, fy - 1, z, a <= drop && b >= 0 && b <= 1 ? Blocks.STAIRS : floor);
                    if (fy < F0) for (int y = fy - 2; y >= F - 1 && y >= fy - 6; y--) set(x, y, z, lining);   // under a tread, solid
                    for (int y = fy; y < fy + hh; y++) set(x, y, z, Blocks.AIR);
                    set(x, fy + hh, z, vault);
                    continue;
                }
                // rock: faced with the lining where it borders an open cell, over the height of what it borders
                int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
                for (int[] n : EIGHT) {
                    int na = a + n[0], nb = b + n[1];
                    if (h(na, nb) <= 0 || na == 0 && (nb == 0 || nb == 1)) continue;
                    int fy = feetAt(na, nb);
                    lo = Math.min(lo, fy - 1); hi = Math.max(hi, fy + h(na, nb));
                }
                if (lo <= hi) for (int y = lo; y <= hi; y++) set(x, y, z, lining);
            }
            // the door at the foot hung as a door (built shut, as every door is)
            int[][] doorCells = {{d.ax(), d.az()}, {d.bx(), d.bz()}};
            for (int[] c : doorCells) for (int y = F0; y <= F0 + 1; y++) set(c[0], y, c[1], Blocks.DOOR);
            boolean locked = Boolean.TRUE.equals(spec.get("locked"));
            String name = astratto.util.Json.str(spec, "name", "Cellar");
            ar.innerDoors.add(new Architecture.InnerDoor(d.owner(), name.toLowerCase(java.util.Locale.ROOT), doorCells, F0 - 1, locked, ""));
            if (System.getenv("RPG_CELLAR_DEBUG") != null) debug();
            // ---- fitted
            furnish();
            cut++;
            rooms += roomCount;
            int open = 0;
            for (int[] r : high) for (int v : r) if (v > 0) open++;
            NOTES.add(d.owner() + " " + d.kind() + ": " + roomCount + " rooms, " + open + " cells, door at " + d.ax() + "," + F0 + "," + d.az()
                    + ", rooms at Y" + F + (turn == 0 ? "" : turn < 0 ? ", turning left" : ", turning right"));
        }

        /** (RPG_CELLAR_DEBUG) the plan as cut, a row per b, a column per a: the clear height, '#' rock, '+' kept, 'x' outside the cover. */
        void debug() {
            System.out.println("[cellar] " + d.owner() + " drop " + drop + " turn " + turn + " vest " + vest + " F0 " + F0 + " way " + d.dx() + "," + d.dz() + " across " + sx + "," + sz);
            for (int b = B_LO; b <= B_HI; b++) {
                StringBuilder row = new StringBuilder(String.format("[cellar] %3d ", b));
                for (int a = A_LO; a <= A_HI; a++) {
                    int hh = h(a, b);
                    row.append(a == 0 && (b == 0 || b == 1) ? 'D' : hh > 0 ? (keep[b - B_LO][(a) - A_LO] ? '+' : (char) ('0' + hh)) : coverAt(a, b, 3) ? '#' : !inBox(a, b) ? 's' : 'x');
                }
                System.out.println(row);
            }
        }

        /** A flood from the door's first step: whatever it does not reach is filled back (rock), so no pocket is cut sealed. */
        void reach() {
            boolean[][] seen = new boolean[W][L];
            ArrayDeque<int[]> q = new ArrayDeque<>();
            for (int b = 0; b <= 1; b++) if (h(1, b) >= 2) { seen[b - B_LO][(1) - A_LO] = true; q.add(new int[]{1, b}); }
            while (!q.isEmpty()) {
                int[] c = q.poll();
                for (int[] n : FOUR) {
                    int a = c[0] + n[0], b = c[1] + n[1];
                    if (!inBox(a, b) || seen[b - B_LO][(a) - A_LO] || h(a, b) < 2) continue;
                    seen[b - B_LO][(a) - A_LO] = true; q.add(new int[]{a, b});
                }
            }
            for (int a = A_LO; a <= A_HI; a++) for (int b = B_LO; b <= B_HI; b++) if (!seen[b - B_LO][(a) - A_LO]) { high[b - B_LO][(a) - A_LO] = 0; room[b - B_LO][(a) - A_LO] = -1; }
        }

        /** The rooms fitted from the dress: sconces, a chest at the back, wall pieces with their backs to the rock, what hangs, a free piece in a big room. */
        void furnish() {
            List<?> wall = astratto.util.Json.list(dress, "wall"), hang = astratto.util.Json.list(dress, "hang"), free = astratto.util.Json.list(dress, "free");
            String chestTag = astratto.util.Json.str(dress, "chest", "store");
            Map<String, Object> heights = astratto.util.Json.obj(dress, "h");
            for (int id = 1; id <= roomCount; id++) {
                List<int[]> spots = new ArrayList<>();   // {a, b, the wall's step a, b}
                int area = 0;
                for (int a = A_LO; a <= A_HI; a++) for (int b = B_LO; b <= B_HI; b++) {
                    if (room[b - B_LO][(a) - A_LO] != id || h(a, b) < 2) continue;
                    area++;
                    if (keep[b - B_LO][(a) - A_LO]) continue;
                    for (int[] n : FOUR) if (h(a + n[0], b + n[1]) == 0 && !(a + n[0] == 0 && (b + n[1] == 0 || b + n[1] == 1))) spots.add(new int[]{a, b, n[0], n[1]});
                }
                if (area == 0) continue;
                // spread round the room from where the seed says
                int start = spots.isEmpty() ? 0 : pick(0, spots.size() - 1, 100 + id);
                List<int[]> order = new ArrayList<>();
                for (int k = 0; k < spots.size(); k++) order.add(spots.get((start + k * 7) % spots.size()));
                // a light on the wall, one to every dozen cells of floor
                int want = Math.max(1, area / 12), lit = 0;
                for (int[] s : order) {
                    if (lit >= want) break;
                    if (lightNear(s[0], s[1]) || used[s[1] - B_LO][(s[0]) - A_LO]) continue;
                    piece("SCONCE", s, 3);
                    used[s[1] - B_LO][(s[0]) - A_LO] = true;
                    lightAt.add(new int[]{s[0], s[1]});
                    lit++; lights++;
                }
                // the chest in the back chamber (the last room), then the wall pieces in turn
                boolean chest = id == roomCount;
                int placed = 0, most = Math.max(2, area / 4);
                for (int[] s : order) {
                    if (placed >= most) break;
                    if (used[s[1] - B_LO][(s[0]) - A_LO] || !clearFront(s)) continue;
                    if (chest) {
                        int x = wx(s[0], s[1]), z = wz(s[0], s[1]);
                        int fa = s[0] - s[2], fb = s[1] - s[3];
                        ar.fittings.add(new Architecture.Fitting(d.owner(), "chest", x + 0.5f, F, z + 0.5f, facing(s[2], s[3]), chestTag, wx(fa, fb) + 0.5f, wz(fa, fb) + 0.5f));
                        used[s[1] - B_LO][(s[0]) - A_LO] = true; chest = false; placed++; pieces++;
                        continue;
                    }
                    if (wall.isEmpty()) break;
                    String kind = String.valueOf(wall.get((placed + id * 3) % wall.size()));
                    int hh = heights == null ? 1 : (int) astratto.util.Json.num(heights, kind, 1);
                    if (hh > h(s[0], s[1]) - 1) hh = Math.max(1, h(s[0], s[1]) - 1);
                    piece(kind, s, hh);
                    used[s[1] - B_LO][(s[0]) - A_LO] = true; placed++;
                }
                // what hangs: over a wall nothing stands against
                if (!hang.isEmpty()) for (int[] s : order) {
                    if (used[s[1] - B_LO][(s[0]) - A_LO] || lightNear(s[0], s[1])) continue;
                    piece(String.valueOf(hang.get(id % hang.size())), s, 3);
                    used[s[1] - B_LO][(s[0]) - A_LO] = true;
                    break;
                }
                // a free piece in the middle of a room big enough for one, its four sides clear
                if (!free.isEmpty() && area >= 16) {
                    search:
                    for (int a = A_LO + 1; a < A_HI; a++) for (int b = B_LO + 1; b < B_HI; b++) {
                        if (room[b - B_LO][(a) - A_LO] != id || keep[b - B_LO][(a) - A_LO] || used[b - B_LO][(a) - A_LO] || h(a, b) < 3) continue;
                        for (int[] n : FOUR) if (h(a + n[0], b + n[1]) < 2 || used[b + n[1] - B_LO][(a + n[0]) - A_LO] || keep[b + n[1] - B_LO][(a + n[0]) - A_LO] && room[b + n[1] - B_LO][(a + n[0]) - A_LO] != id) continue search;
                        piece(String.valueOf(free.get(id % free.size())), new int[]{a, b, 0, 1}, 1);
                        used[b - B_LO][(a) - A_LO] = true;
                        break search;
                    }
                }
            }
        }
        boolean lightNear(int a, int b) { for (int[] l : lightAt) if (Math.abs(l[0] - a) + Math.abs(l[1] - b) < 4) return true; return false; }
        /** The cell before a piece's front is open floor and not some other piece. */
        boolean clearFront(int[] s) {
            int a = s[0] - s[2], b = s[1] - s[3];
            return inBox(a, b) && h(a, b) >= 2 && !used[b - B_LO][(a) - A_LO];
        }
        void piece(String kind, int[] s, int hh) {
            int x = wx(s[0], s[1]), z = wz(s[0], s[1]);
            ar.pieces.add(new Architecture.Piece(d.owner(), kind, x, z, x, z, F, hh, facing(s[2], s[3]), "cellar"));
            if (!kind.equals("SCONCE")) pieces++;
        }
        /** Quarter turns from +z toward the wall at local step (da, db): the side its back is on (Fitout's way). */
        int facing(int da, int db) {
            int wxd = d.dx() * da + sx * db, wzd = d.dz() * da + sz * db;
            return wzd > 0 ? 0 : wxd > 0 ? 1 : wzd < 0 ? 2 : 3;
        }
        void set(int x, int y, int z, int b) { if (t.in(x, z) && y >= 1 && y < t.H) t.cells[t.idx(x, z) * t.H + y] = (byte) b; }
    }
}
