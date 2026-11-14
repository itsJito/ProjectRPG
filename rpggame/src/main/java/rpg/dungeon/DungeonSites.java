package rpg.dungeon;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import rpg.world.DungeonLayout;
import rpg.world.shadowveil.Architecture;

/**
 * (0.4.5, gamefixes: lay the groundwork for the dungeons of the next update) ShadowVeil's underground places as one
 * register: each from {@code data/dungeons.json} (what it is, the theme its rooms take, how many levels it is planned to
 * go down, whose lair it is), bound to the structure that has its mouth, the mouth's point where a body goes in, and the
 * volumes the architecture reserved beyond it (where the rooms will be cut). What does not bind - a site the map lacks,
 * a mouth with nothing reserved behind it - is a problem the survey and the tests report, so the next update starts from
 * places that are all really there.
 */
public final class DungeonSites {
    private DungeonSites() {}

    /** A place underground: its data, its structure's mouth (x, z; NaN if none) and the volumes kept for it. */
    public record Site(String id, String name, String kind, DungeonLayout.Theme theme, int levels, List<String> creatures,
                       float mouthX, float mouthZ, List<Architecture.Reserved> volumes) {
        public int cells() { int n = 0; for (var v : volumes) n += (v.x1() - v.x0() + 1) * (v.y1() - v.y0() + 1) * (v.z1() - v.z0() + 1); return n; }
    }

    private static List<Site> sites = List.of();
    private static final List<String> problems = new ArrayList<>();

    /** Every site, bound to the map in play (empty off ShadowVeil). */
    public static List<Site> all() { return sites; }
    public static List<String> problems() { return Collections.unmodifiableList(problems); }
    public static Site byId(String id) { for (Site s : sites) if (s.id().equals(id)) return s; return null; }

    /** Binds the data to a grown world's architecture (called once it is planned). */
    public static void bind(Architecture arch) {
        problems.clear();
        List<Site> out = new ArrayList<>();
        Map<String, Object> root;
        try (InputStream in = DungeonSites.class.getResourceAsStream("/data/dungeons.json")) {
            if (in == null) { sites = List.of(); return; }
            root = astratto.util.Json.parseObject(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (Exception e) { throw new IllegalStateException("data/dungeons.json could not be read", e); }
        Map<String, Object> data = astratto.util.Json.obj(root, "sites");
        if (data == null || arch == null) { sites = List.of(); return; }
        for (var e : data.entrySet()) {
            if (!(e.getValue() instanceof Map<?, ?> m)) continue;
            @SuppressWarnings("unchecked") Map<String, Object> d = (Map<String, Object>) m;
            Architecture.Structure s = arch.byId(e.getKey());
            if (s == null) { problems.add(e.getKey() + ": no such place on the map"); continue; }
            DungeonLayout.Theme theme;
            try { theme = DungeonLayout.Theme.valueOf(astratto.util.Json.str(d, "theme", "CAVE")); } catch (IllegalArgumentException ex) { problems.add(e.getKey() + ": no theme " + d.get("theme")); continue; }
            float[] mouth = s.points.get("mouth");
            if (mouth == null) mouth = s.points.get("crypt");
            List<Architecture.Reserved> vols = new ArrayList<>();
            for (Architecture.Reserved r : arch.reserved) if (r.owner().equals(s.id)) vols.add(r);
            if (mouth == null) problems.add(e.getKey() + ": no way in");
            if (vols.isEmpty()) problems.add(e.getKey() + ": nothing reserved below it");
            List<String> creatures = new ArrayList<>();
            for (Object c : astratto.util.Json.list(d, "creatures")) {
                try { rpg.monster.MonsterKind.valueOf(String.valueOf(c)); creatures.add(String.valueOf(c)); } catch (IllegalArgumentException ex) { problems.add(e.getKey() + ": no creature " + c); }
            }
            out.add(new Site(s.id, s.name, astratto.util.Json.str(d, "kind", "lair"), theme, (int) astratto.util.Json.num(d, "levels", 1), List.copyOf(creatures),
                    mouth == null ? Float.NaN : mouth[0], mouth == null ? Float.NaN : mouth[1], List.copyOf(vols)));
        }
        sites = List.copyOf(out);
        plans.clear();
        for (Site site : sites) plans.put(site.id(), plan(site));
    }

    // ------------------------------------------------------------------ (0.4.6) the plans the next update builds from
    /**
     * (0.4.6, gamefixes: prepare the dungeons) every site's plan, laid out now and checked, though nothing is cut yet: its
     * levels, each a run of rooms inside the volume the architecture keeps below the mouth - the way in nearest the mouth,
     * the lair's heart at the far end, dens, a hazard and a hoard between, each room joined to the last - and the combat
     * level it asks of whoever goes down (its creatures' own: their skills make their level, as everyone's do).
     */
    public record Level(int depth, int floorY, List<DungeonLayout.Room> rooms) {}
    public record Plan(String site, List<Level> levels, int recommended) {}
    private static final Map<String, Plan> plans = new java.util.LinkedHashMap<>();
    public static Plan planOf(String id) { return plans.get(id); }

    static Plan plan(Site site) {
        List<Level> levels = new ArrayList<>();
        Architecture.Reserved v = null;
        for (var r : site.volumes()) if (v == null || (r.x1() - r.x0()) * (r.z1() - r.z0()) > (v.x1() - v.x0()) * (v.z1() - v.z0())) v = r;
        if (v != null) {
            int height = Math.max(4, (v.y1() - v.y0() + 1) / Math.max(1, site.levels()));
            java.util.Random rnd = new java.util.Random(site.id().hashCode());
            for (int d = 0; d < site.levels(); d++) {
                int floor = v.y1() - (d + 1) * height + 1;
                if (floor < v.y0()) break;
                List<DungeonLayout.Room> rooms = new ArrayList<>();
                split(v.x0(), v.z0(), v.x1(), v.z1(), rnd, rooms, Math.min(4, height - 1), 0);
                // ordered from the mouth: the way in first, the heart last
                float mx = Float.isNaN(site.mouthX()) ? (v.x0() + v.x1()) / 2f : site.mouthX(), mz = Float.isNaN(site.mouthZ()) ? (v.z0() + v.z1()) / 2f : site.mouthZ();
                rooms.sort(java.util.Comparator.comparingDouble(r -> Math.hypot((r.x0() + r.x1()) / 2f - mx, (r.z0() + r.z1()) / 2f - mz)));
                List<DungeonLayout.Room> kinds = new ArrayList<>();
                for (int i = 0; i < rooms.size(); i++) {
                    var r = rooms.get(i);
                    DungeonLayout.RoomKind k = i == 0 ? DungeonLayout.RoomKind.ENTRY : i == rooms.size() - 1 && d == site.levels() - 1 ? DungeonLayout.RoomKind.BOSS
                            : i == rooms.size() - 2 ? DungeonLayout.RoomKind.TREASURE : i % 3 == 2 ? DungeonLayout.RoomKind.HAZARD : i % 2 == 0 ? DungeonLayout.RoomKind.DEN : DungeonLayout.RoomKind.CHAMBER;
                    kinds.add(new DungeonLayout.Room(r.x0(), r.z0(), r.x1(), r.z1(), k, r.height()));
                }
                levels.add(new Level(d, floor, List.copyOf(kinds)));
            }
        }
        int rec = 1;
        for (String c : site.creatures()) { var k = rpg.monster.MonsterKind.valueOf(c); rec = Math.max(rec, (k.minLevel + k.maxLevel) / 2); }
        if (levels.isEmpty()) problems.add(site.id() + ": no room for a plan below it");
        return new Plan(site.id(), List.copyOf(levels), rec);
    }

    /** Halves a footprint across its longer side until the pieces are room-sized (leaving a wall between). */
    private static void split(int x0, int z0, int x1, int z1, java.util.Random rnd, List<DungeonLayout.Room> out, int height, int depth) {
        int w = x1 - x0 + 1, d = z1 - z0 + 1;
        if (depth > 5 || w < 12 && d < 12) {
            if (w >= 5 && d >= 5) out.add(new DungeonLayout.Room(x0 + 1, z0 + 1, x1 - 1, z1 - 1, DungeonLayout.RoomKind.CHAMBER, height));
            return;
        }
        if (w >= d) { int cut = x0 + w / 3 + rnd.nextInt(Math.max(1, w / 3)); split(x0, z0, cut - 1, z1, rnd, out, height, depth + 1); split(cut + 1, z0, x1, z1, rnd, out, height, depth + 1); }
        else { int cut = z0 + d / 3 + rnd.nextInt(Math.max(1, d / 3)); split(x0, z0, x1, cut - 1, rnd, out, height, depth + 1); split(x0, cut + 1, x1, z1, rnd, out, height, depth + 1); }
    }
}
