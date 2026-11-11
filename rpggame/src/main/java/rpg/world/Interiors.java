package rpg.world;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import rpg.Ids;
import rpg.Rpg;
import rpg.objects.Furniture;
import rpg.objects.Furniture.Kind;

/**
 * (0.3.9, gamefixes: all interiors on the devmap reworked and polished) Every room raised through the building kit dressed
 * for what it is, from the furniture library the DevMansion was built with. A room is read for its use by what already
 * stands in it - beds make a home, a counter a shop or an office, the rest a hall - and dressed to suit: a home gets a
 * bedside table by each bed, a wardrobe and a chest of drawers, a table and chairs, a fern, a candelabrum and a rug; a
 * shop shelves, a dresser and racks; a hall a sideboard, a clock and a suit of armour; and every room something on its
 * walls and, if it is big enough, a chandelier.
 *
 * <p>Nothing is placed blindly. Solid pieces only stand against a wall, never in the apron inside the door, never on a
 * cell people stand on to use something (a bed's side, a counter's front), and a piece is kept only if every cell the
 * room had that could be walked to still can be, and everything in it can still be reached: a room is never furnished
 * shut. What hangs on a wall, lies on the floor or hangs from the ceiling takes no room at all.
 *
 * <p>The pieces are numbered in a band of their own (Ids.ESTATE's, from INTERIORS on), so no fixture made before them
 * changes its number and older saves keep theirs.
 */
public final class Interiors {
    private Interiors() {}

    /** Where the interiors' numbers start (in the estates' fixture band, after the DevMansion's). */
    public static final long INTERIORS = Ids.ESTATE + 200_000;
    /** About one solid piece for every so many open floor cells, and never more than this many in a room. */
    static final int CELLS_PER_PIECE = rpg.Tuning.i("interiors.cellsPerPiece", 7), MOST = rpg.Tuning.i("interiors.mostPerRoom", 9);
    /** The rooms the furnisher leaves as they are (dressed by hand already, or not rooms to live in). */
    static boolean skipped(BuildingKit.Plan p) { String n = p.name().toLowerCase(); return n.contains("devmansion") || n.contains("jail") || n.contains("cell"); }

    enum Use { HOME, SHOP, HALL }

    /** Furnishes every kit-built room; returns the next free number. */
    public static long furnish(Rpg g, long nextId) {
        for (BuildingKit.Plan p : BuildingKit.plans()) if (!skipped(p)) nextId = room(g, p, nextId);
        return nextId;
    }

    private static long room(Rpg g, BuildingKit.Plan p, long id) {
        int f = DevMap.FLOOR;
        Voxels vox = g.vox;
        // the room's open floor: inside the walls, a floor under it, two cells of air over it, a roof somewhere above
        Set<Long> open = new HashSet<>();
        for (int x = p.x0() + 1; x < p.x1(); x++) for (int z = p.z0() + 1; z < p.z1(); z++) if (walkable(vox, x, f, z) && roofed(vox, x, f, z)) open.add(key(x, z));
        if (open.size() < 6) return id;
        // what is in it already, and its use
        List<Furniture> inside = new ArrayList<>();
        for (Furniture fu : g.objects.furniture) if (fu.exists() && fu.floor == f && fu.x0 >= p.x0() && fu.x1 <= p.x1() && fu.z0 >= p.z0() && fu.z1 <= p.z1()) inside.add(fu);
        boolean beds = inside.stream().anyMatch(fu -> fu.kind.bed()), counter = inside.stream().anyMatch(fu -> fu.kind == Kind.COUNTER);
        if (inside.stream().anyMatch(fu -> fu.kind == Kind.SLAB)) return id;
        Use use = beds ? Use.HOME : counter ? Use.SHOP : Use.HALL;
        long owner = inside.stream().filter(fu -> fu.owner != 0).mapToLong(fu -> fu.owner).findFirst().orElse(0);
        // kept clear: the door's apron, and the cells beside what is used from its side (a bed's flank, a counter's front)
        int[] in = inward(p);
        Set<Long> keep = new HashSet<>();
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) if (Math.abs(dx) + Math.abs(dz) <= 3) keep.add(key(p.doorX() + in[0] * 2 + dx, p.doorZ() + in[1] * 2 + dz));
        for (Furniture fu : inside) {
            int r = fu.kind == Kind.COUNTER ? 2 : 1;   // a counter is worked from behind and served from in front: two cells clear
            for (int x = fu.x0 - r; x <= fu.x1 + r; x++) for (int z = fu.z0 - r; z <= fu.z1 + r; z++) keep.add(key(x, z));
        }
        // and where anyone stands to work or sleeps: their post and their bed, with a cell round each
        for (rpg.ai.Npc n : g.npcs) for (float[] at : new float[][]{{n.postX, n.postZ}, {n.homeX, n.homeZ}, {n.spawnX, n.spawnZ}}) {
            int ax = (int) Math.floor(at[0]), az = (int) Math.floor(at[1]);
            if (ax < p.x0() || ax > p.x1() || az < p.z0() || az > p.z1()) continue;
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) keep.add(key(ax + dx, az + dz));
        }
        for (var shop : new rpg.econ.Economy.Shop[]{g.econ.shopA, g.econ.shopB}) { if (shop == null) continue; float[] c = g.econ.counterSpot(shop); int ax = (int) Math.floor(c[0]), az = (int) Math.floor(c[1]); for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) keep.add(key(ax + dx, az + dz)); }
        int[] start = {p.doorX() + in[0], p.doorZ() + in[1]};
        if (!open.contains(key(start[0], start[1]))) { start = nearest(open, start); if (start == null) return id; }
        Set<Long> reach = reach(open, start);
        // the pieces this room asks for, in the order they matter
        List<Kind> wish = switch (use) {
            case HOME -> List.of(Kind.WARDROBE, Kind.DRESSER, Kind.CHAIR, Kind.PLANTER, Kind.CANDELABRA, Kind.SHELVES, Kind.ARMCHAIR, Kind.CLOCK, Kind.STOOL);
            case SHOP -> List.of(Kind.SHELVES, Kind.CUPBOARD, Kind.WINE_RACK, Kind.CANDELABRA, Kind.PLANTER, Kind.CLOCK, Kind.STOOL, Kind.SHELVES);
            case HALL -> List.of(Kind.SIDEBOARD, Kind.CLOCK, Kind.CANDELABRA, Kind.ARMOR_STAND, Kind.PLANTER, Kind.BOOKCASE, Kind.ARMCHAIR, Kind.BUST);
        };
        int budget = Math.min(MOST, Math.max(2, reach.size() / CELLS_PER_PIECE));
        // a bedside table by each bed's head, first
        if (use == Use.HOME) for (Furniture bed : new ArrayList<>(inside)) if (bed.kind.bed() && budget > 0) {
            for (int[] c : new int[][]{{bed.x0 - 1, bed.z0}, {bed.x1 + 1, bed.z0}, {bed.x0 - 1, bed.z1}, {bed.x1 + 1, bed.z1}}) {
                if (!open.contains(key(c[0], c[1])) || !wallBeside(vox, c[0], f, c[1])) continue;
                if (place(g, open, reach, start, inside, c[0], c[1], Kind.NIGHTSTAND, facingToWall(vox, c[0], f, c[1]), owner, id)) { id++; budget--; break; }
            }
        }
        // then the rest, each against a wall, spread round the room
        List<int[]> walls = new ArrayList<>();
        for (long k : reach) { int x = (int) (k >> 32), z = (int) k; if (!keep.contains(k) && wallBeside(vox, x, f, z)) walls.add(new int[]{x, z}); }
        walls.sort((a, b) -> a[0] != b[0] ? Integer.compare(a[0], b[0]) : Integer.compare(a[1], b[1]));
        int wi = 0, stride = Math.max(1, walls.size() / Math.max(1, budget + 1));
        for (Kind k : wish) {
            if (budget <= 0 || walls.isEmpty()) break;
            for (int tries = 0; tries < walls.size(); tries++) {
                int[] c = walls.get((wi + tries * 3) % walls.size());
                if (!open.contains(key(c[0], c[1])) || keep.contains(key(c[0], c[1]))) continue;
                if (place(g, open, reach, start, inside, c[0], c[1], k, facingToWall(vox, c[0], f, c[1]), owner, id)) {
                    id++; budget--;
                    for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) keep.add(key(c[0] + dx, c[1] + dz));
                    break;
                }
            }
            wi += stride;
        }
        // what takes no room: a rug in the middle, a painting or a tapestry on a long wall, a chandelier in a big room
        int[] mid = centre(open, keep, inside);
        if (mid != null) { add(g, Kind.RUG, mid[0], mid[1], mid[0] + 1, mid[1] + 1, 1, 0, owner, id++); }
        int[] hang = hangSpot(vox, reach, f, p);
        if (hang != null) add(g, use == Use.HALL ? Kind.TAPESTRY : Kind.PAINTING, hang[0], hang[1], hang[2], hang[3], 3, hang[4], owner, id++);
        int height = roomHeight(vox, (p.x0() + p.x1()) / 2, f, (p.z0() + p.z1()) / 2);
        if (reach.size() >= 30 && height >= 4) { int cx = (p.x0() + p.x1()) / 2, cz = (p.z0() + p.z1()) / 2; if (open.contains(key(cx, cz))) add(g, Kind.CHANDELIER, cx, cz, cx, cz, height, 0, owner, id++); }
        return id;
    }

    /** A solid piece tried at a cell: kept only if the room stays whole (every cell it had reachable, and everything in it, still reachable). */
    private static boolean place(Rpg g, Set<Long> open, Set<Long> reach, int[] start, List<Furniture> inside, int x, int z, Kind k, int facing, long owner, long id) {
        long key = key(x, z);
        open.remove(key);
        Set<Long> now = reach(open, start);
        boolean whole = now.size() == reach.size() - 1;
        if (whole) for (Furniture fu : inside) if (!touches(now, fu)) { whole = false; break; }
        if (!whole) { open.add(key); return false; }
        reach.clear(); reach.addAll(now);
        int h = k == Kind.WARDROBE || k == Kind.CLOCK || k == Kind.SHELVES || k == Kind.CUPBOARD || k == Kind.WINE_RACK || k == Kind.ARMOR_STAND || k == Kind.BOOKCASE || k == Kind.BUST ? 2 : 1;
        Furniture fu = add(g, k, x, z, x, z, h, facing, owner, id);
        inside.add(fu);
        return true;
    }

    private static Furniture add(Rpg g, Kind k, int x0, int z0, int x1, int z1, int h, int facing, long owner, long id) {
        Furniture fu = g.objects.add(new Furniture(id, k, x0, z0, x1, z1, h, facing, owner, true, DevMap.FLOOR));
        g.objects.furniture.add(fu);
        fu.place(g, true);
        return fu;
    }

    private static boolean touches(Set<Long> reach, Furniture fu) {
        for (int x = fu.x0 - 1; x <= fu.x1 + 1; x++) for (int z = fu.z0 - 1; z <= fu.z1 + 1; z++) if (reach.contains(key(x, z))) return true;
        return false;
    }

    private static Set<Long> reach(Set<Long> open, int[] start) {
        Set<Long> seen = new HashSet<>();
        if (!open.contains(key(start[0], start[1]))) return seen;
        ArrayDeque<int[]> q = new ArrayDeque<>();
        q.add(start); seen.add(key(start[0], start[1]));
        while (!q.isEmpty()) {
            int[] c = q.poll();
            for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                long k = key(c[0] + d[0], c[1] + d[1]);
                if (open.contains(k) && seen.add(k)) q.add(new int[]{c[0] + d[0], c[1] + d[1]});
            }
        }
        return seen;
    }

    private static int[] nearest(Set<Long> open, int[] at) {
        int[] best = null; int bd = Integer.MAX_VALUE;
        for (long k : open) { int x = (int) (k >> 32), z = (int) k, d = Math.abs(x - at[0]) + Math.abs(z - at[1]); if (d < bd) { bd = d; best = new int[]{x, z}; } }
        return best != null && bd <= 3 ? best : null;
    }

    /** Into the room from the door: the step that is not outside. */
    private static int[] inward(BuildingKit.Plan p) {
        if (p.axis() == 'z') return new int[]{0, p.doorZ() == p.z0() ? 1 : -1};
        return new int[]{p.doorX() == p.x0() ? 1 : -1, 0};
    }

    private static boolean walkable(Voxels v, int x, int f, int z) { return v.solid(x, f - 1, z) && !v.solid(x, f, z) && !v.solid(x, f + 1, z) && v.block(x, f, z) == Blocks.AIR && v.block(x, f + 1, z) == Blocks.AIR; }
    private static boolean roofed(Voxels v, int x, int f, int z) { for (int y = f + 2; y <= f + 7; y++) if (v.solid(x, y, z)) return true; return false; }
    private static int roomHeight(Voxels v, int x, int f, int z) { for (int y = f + 1; y <= f + 8; y++) if (v.solid(x, y, z)) return y - f; return 0; }
    /** A wall (two cells high, structure, not a piece of furniture) beside a cell. */
    private static boolean wallBeside(Voxels v, int x, int f, int z) { return facingToWall(v, x, f, z) >= 0; }
    /** Which way the wall beside a cell is, as a piece's facing with its back to it (0 +z, 1 +x, 2 -z, 3 -x); -1 none. */
    private static int facingToWall(Voxels v, int x, int f, int z) {
        int[][] d = {{0, 1}, {1, 0}, {0, -1}, {-1, 0}};
        for (int i = 0; i < 4; i++) {
            int bx = x + d[i][0], bz = z + d[i][1];
            int b = v.block(bx, f, bz);
            if (v.solid(bx, f, bz) && v.solid(bx, f + 1, bz) && !Blocks.objectCell(b) && b != Blocks.DOOR && b != Blocks.LOCKED_DOOR && !Blocks.glass(b)) return i;
        }
        return -1;
    }

    /** A 2 x 2 patch of open floor nearest the room's middle with nothing solid in it and clear of the door: for a rug. */
    private static int[] centre(Set<Long> open, Set<Long> keep, List<Furniture> inside) {
        double cx = 0, cz = 0; for (long k : open) { cx += (int) (k >> 32); cz += (int) k; } cx /= open.size(); cz /= open.size();
        int[] best = null; double bd = Double.MAX_VALUE;
        for (long k : open) {
            int x = (int) (k >> 32), z = (int) k;
            if (!open.contains(key(x + 1, z)) || !open.contains(key(x, z + 1)) || !open.contains(key(x + 1, z + 1))) continue;
            boolean under = false;
            for (Furniture fu : inside) if (fu.kind == Kind.RUG && fu.x0 <= x + 1 && fu.x1 >= x && fu.z0 <= z + 1 && fu.z1 >= z) under = true;
            if (under) continue;
            double d = Math.hypot(x + 1 - cx, z + 1 - cz);
            if (d < bd) { bd = d; best = new int[]{x, z}; }
        }
        return best;
    }

    /** Two cells of open floor along a plain wall (no door, no window): where a picture hangs ({x0, z0, x1, z1, facing}). */
    private static int[] hangSpot(Voxels v, Set<Long> reach, int f, BuildingKit.Plan p) {
        int[] best = null; double bd = Double.MAX_VALUE;
        double cx = (p.x0() + p.x1()) / 2.0, cz = (p.z0() + p.z1()) / 2.0;
        for (long k : reach) {
            int x = (int) (k >> 32), z = (int) k, face = facingToWall(v, x, f, z);
            if (face < 0) continue;
            int ax = face % 2 == 0 ? 1 : 0, az = 1 - ax;      // along the wall
            if (!reach.contains(key(x + ax, z + az)) || facingToWall(v, x + ax, f, z + az) != face) continue;
            int[] d = new int[][]{{0, 1}, {1, 0}, {0, -1}, {-1, 0}}[face];
            if (!v.solid(x + d[0], f + 2, z + d[1]) || Blocks.glass(v.block(x + d[0], f + 1, z + d[1])) || Blocks.glass(v.block(x + ax + d[0], f + 1, z + az + d[1]))) continue;
            double dist = -Math.hypot(x - p.doorX(), z - p.doorZ()) + Math.hypot(x - cx, z - cz) * 0.2;   // the wall facing the door
            if (dist < bd) { bd = dist; best = new int[]{x, z, x + ax, z + az, face}; }
        }
        return best;
    }

    private static long key(int x, int z) { return (long) x << 32 | (z & 0xffffffffL); }
}
