package rpg.world.shadowveil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import rpg.world.Blocks;

/**
 * (0.4.2, gamefixes: settlements and wilderness given purposeful exterior detail - lanterns, shop signs, woodpiles,
 * crates, benches, hay, planting - reflecting each place's culture, economy and daily life without clutter) The
 * dressing of every structure's outside, by what the building is for ({@code shadowveil_architecture.json}
 * "dressing": a list of pieces per purpose, and how likely each building is to have each): a shop's sign hung from a
 * bracket beside its door and a lantern on its wall, crates stacked at its corner; an inn's bench by the door; a
 * cottage's woodpile against its gable and shrubs under its windows; a farm's hay; a mine's timbers and crates by its
 * mouth. Each piece is drawn in the building's own materials (its culture's), only on its own ground, never on a road or
 * a lane, never before a door, and only where the ground is level with the building's floor (nothing floats, nothing
 * buries a step). The crates and benches are furniture the game draws and people use; the rest are blocks.
 */
final class Dressing {
    private final Architecture ar;
    private final Kit.Canvas c;
    private final Map<String, Object> data;
    private final Set<Long> taken = new HashSet<>();
    final List<Architecture.Prop> props = new ArrayList<>();
    /** The structure being dressed (its own lot is its ground). */
    private Architecture.Structure owner;
    int pieces;

    Dressing(Architecture ar, Kit.Canvas c) { this.ar = ar; this.c = c; this.data = Atlas.map(ar.a.map("architecture"), "dressing"); }

    void dressAll(Registry reg) {
        if (data.isEmpty()) return;
        double chance = Atlas.d(data, "chance", 0.85);
        Map<String, Object> by = Atlas.map(data, "purposes");
        for (Architecture.Structure s : ar.structures) {
            Registry.Entry e = reg == null ? null : reg.byId(s.id);
            String purpose = e == null ? "open" : e.purpose;
            List<?> kinds = Atlas.arr(by, purpose);
            if (kinds.isEmpty() || s.doors.isEmpty() || Boolean.TRUE.equals(s.opts.get("water"))) continue;
            Kit.Frame f = new Kit.Frame(c, s.x0, s.z0, s.w, s.d, s.facing, s.floor);
            int[] door = doorSpan(s, f);
            int k = 0;
            for (Object o : kinds) {
                if (Noise.hash(ar.a.seed, s.id.hashCode(), k++, 131) > chance) continue;
                owner = s;
                if (piece(String.valueOf(o), s, f, door)) pieces++;
            }
        }
        ar.notes.add(pieces + " exterior pieces dressed (" + props.size() + " of them furniture); cells refused: " + refused);
    }

    /** The front door's local span {u0, u1} (the first doorway on the front, v 0). */
    private static int[] doorSpan(Architecture.Structure s, Kit.Frame f) {
        int lo = Integer.MAX_VALUE, hi = Integer.MIN_VALUE;
        for (int u = 0; u < f.w; u++) for (int[] cell : s.doors.get(0)) if (f.wx(u, 0) == cell[0] && f.wz(u, 0) == cell[1]) { lo = Math.min(lo, u); hi = Math.max(hi, u); }
        if (lo == Integer.MAX_VALUE) { lo = f.w / 2; hi = lo; }
        return new int[]{lo, hi};
    }

    private boolean piece(String kind, Architecture.Structure s, Kit.Frame f, int[] door) {
        int P = f.P, w = f.w, d = f.d;
        int post = s.style.b("post"), trim = s.style.b("trim"), cloth = s.style.b("cloth"), banner = s.style.b("banner");
        switch (kind) {
            case "lantern" -> {
                // a lantern on the wall beside the door, at the lintel's height (lit at dusk with the street's)
                int u = door[0] - 1;
                if (u < 1 || !free(f, u, P + 3, -1, false)) return false;
                f.put(u, P + 3, -1, Blocks.LAMP);
                return true;
            }
            case "sign" -> {
                // a board hung from a bracket on the other side of the door, over a passer's head
                int u = door[1] + 1;
                if (u > w - 2 || !free(f, u, P + 3, -1, false) || !free(f, u, P + 4, -1, false)) return false;
                f.put(u, P + 4, -1, trim);
                f.put(u, P + 3, -1, banner != Blocks.AIR ? banner : cloth);
                return true;
            }
            case "woodpile" -> {
                // logs stacked against the gable, two high
                int side = Noise.hash(ar.a.seed, s.id.hashCode(), 7, 132) < 0.5 ? -1 : w;
                int v0 = Math.max(1, d / 2 - 1);
                for (int v = v0; v <= v0 + 1; v++) for (int y = P + 1; y <= P + 2; y++) if (!free(f, side, y, v, y == P + 1)) return false;
                for (int v = v0; v <= v0 + 1; v++) for (int y = P + 1; y <= P + 2; y++) f.put(side, y, v, post);
                return true;
            }
            case "hay" -> {
                int u = w, v = d - 1;
                if (!free(f, u, P + 1, v, true) || !free(f, u, P + 1, v - 1, true)) return false;
                f.put(u, P + 1, v, Blocks.HAY_BALE); f.put(u, P + 1, v - 1, Blocks.HAY_BALE);
                if (free(f, u, P + 2, v, false)) f.put(u, P + 2, v, Blocks.HAY_BALE);
                return true;
            }
            case "shrubs" -> {
                // planting along the front wall, clear of the door
                int made = 0;
                for (int u = 1; u < w - 1; u++) {
                    if (u >= door[0] - 1 && u <= door[1] + 1) continue;
                    if (Noise.hash(ar.a.seed, s.id.hashCode() + u, 9, 133) < 0.45 || !free(f, u, P + 1, -1, true)) continue;
                    f.put(u, P + 1, -1, Blocks.HEDGE);
                    made++;
                }
                return made > 0;
            }
            case "crates" -> {
                // a stack at the front corner away from the door: furniture, drawn and handled as the game's crates are
                int u = door[0] > w / 2 ? 0 : w - 1;
                if (!free(f, u, P + 1, -1, true)) return false;
                prop(f, "CRATE", u, -1, P + 1);
                return true;
            }
            case "bench" -> {
                // a bench against the front wall beside the door, facing the street
                int u = door[0] - 3;
                if (u < 1 || !free(f, u, P + 1, -1, true) || !free(f, u + 1, P + 1, -1, true)) return false;
                prop(f, "BENCH", u, -1, P + 1);
                return true;
            }
            case "timbers" -> {
                // pit-props and a crate stacked beside a mine's or a dig's mouth
                int u = door[1] + 2;
                if (!free(f, u, P + 1, 0, true) || !free(f, u, P + 1, 1, true)) return false;
                f.put(u, P + 1, 0, post); f.put(u, P + 1, 1, post); f.put(u, P + 2, 0, post);
                if (free(f, u + 1, P + 1, 0, true)) prop(f, "CRATE", u + 1, 0, P + 1);
                return true;
            }
            default -> { return false; }
        }
    }

    /** A furniture piece set out (its cell made the furniture's own cell, the object made with the world's objects). */
    private void prop(Kit.Frame f, String kind, int u, int v, int floor) {
        int x = f.wx(u, v), z = f.wz(u, v);
        c.set(x, floor, z, kind.equals("BENCH") ? Blocks.SEAT : Blocks.CRATE);
        // its back to the wall, facing out of the front (quarter turns from +z)
        int[] out = f.dir(0, -1);
        int facing = out[1] > 0 ? 0 : out[0] > 0 ? 1 : out[1] < 0 ? 2 : 3;
        props.add(new Architecture.Prop(kind, x, z, floor, facing));
        taken.add(key(x, floor, z));
    }

    /**
     * Whether a cell outside the building may take a piece: open air there, the ground under it level with the floor
     * (when it stands on the ground), its column on this lot's own ground (not a road, a lane, a bridge or anyone else's
     * lot), and not before a doorway.
     */
    private boolean free(Kit.Frame f, int u, int y, int v, boolean grounded) {
        int x = f.wx(u, v), z = f.wz(u, v);
        if (!ar.t.in(x, z) || c.get(x, y, z) != Blocks.AIR || taken.contains(key(x, y, z))) return why("occupied");
        int flags = ar.t.flags[ar.t.idx(x, z)];
        if ((flags & (Terrain.ROAD | Terrain.LANE | Terrain.BRIDGE | Terrain.CAUSEWAY)) != 0) return why("road");
        Architecture.Structure other = ar.at(x, z);
        if (other != null && other != owner) return why("another lot");
        if (grounded && !solid(c.get(x, y - 1, z))) return why("no ground");
        // (the ground's own surface, not the eaves over it: a roof's overhang is the column's highest cell)
        if (grounded && ar.t.top[ar.t.idx(x, z)] != y - 1) return why("not level");
        for (Architecture.Structure s : ar.structures) {
            if (Math.abs(s.cx() - x) > 40 || Math.abs(s.cz() - z) > 40) continue;
            for (int k = 0; k < s.doors.size(); k++) {
                int[] out = s.doorOut.get(k);
                for (int[] cell : s.doors.get(k)) for (int step = 1; step <= 2; step++) if (cell[0] + out[0] * step == x && cell[1] + out[1] * step == z) return why("doorstep");
            }
        }
        return true;
    }
    final java.util.Map<String, Integer> refused = new java.util.TreeMap<>();
    private boolean why(String reason) { refused.merge(reason, 1, Integer::sum); return false; }
    private static boolean solid(int b) { return b != Blocks.AIR && b != Blocks.WATER && !Blocks.isLeaves(b) && b != Blocks.DOOR; }
    private static long key(int x, int y, int z) { return ((long) x << 40) | ((long) y << 20) | z; }
}
