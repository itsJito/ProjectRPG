package rpg.world.shadowveil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import rpg.objects.Container;
import rpg.objects.Door;
import rpg.objects.Fixtures;
import rpg.objects.Furniture;
import rpg.objects.Station;

/**
 * (0.4.4, gamefixes: every interior supporting its intended gameplay) What ShadowVeil's interiors hold, as the game's
 * objects, and where the systems find it: each building's beds (by structure, the ones its people are given), the posts
 * its workers stand at, its stations and chests, every church's altar, every jail's cells. Made once with the world's
 * fixtures from what the architecture fitted out ({@link Architecture#pieces}, {@link Architecture#fittings},
 * {@link Architecture#innerDoors}, {@link Architecture#cells}).
 */
public final class Rooms {
    private Rooms() {}

    /** One building's working parts as objects. */
    public static final class Site {
        public final String id;
        public final List<Furniture> beds = new ArrayList<>(), guestBeds = new ArrayList<>(), furniture = new ArrayList<>();
        public final List<Architecture.Fitting> posts = new ArrayList<>();
        public final List<Station> stations = new ArrayList<>();
        public final List<Container> chests = new ArrayList<>();
        public final List<Door> doors = new ArrayList<>();
        public final List<Fixtures.Altar> altars = new ArrayList<>();
        Site(String id) { this.id = id; }
    }

    private static final Map<String, Site> SITES = new LinkedHashMap<>();
    /** The jails' cells by kingdom (the first cell of each kingdom's jail is the one its law sends people to). */
    private static final Map<String, List<rpg.world.Jails.Cell>> CELLS = new LinkedHashMap<>();
    private static final List<float[]> ALTARS = new ArrayList<>();
    private static final List<String> ALTAR_REALMS = new ArrayList<>();

    public static Site site(String id) { return SITES.get(id); }
    /** (0.4.6) Each kingdom's jail chest of held goods (a prisoner's possessions are kept in it, where they can be found). */
    private static final Map<String, Container> HELD = new LinkedHashMap<>();
    static final int HELD_CELLS = rpg.Tuning.i("law.heldGoodsCells", 320);
    public static Container heldGoods(String realm) { return HELD.get(realm); }
    public static Map<String, Site> sites() { return SITES; }
    public static void clear() { SITES.clear(); CELLS.clear(); ALTARS.clear(); ALTAR_REALMS.clear(); HELD.clear(); }

    /** The objects' maker (Objects' own: its numbering, its lists). */
    public interface Maker {
        long id();
        <T extends rpg.interact.WorldObject> T add(T o);
        void furniture(Furniture f);
        void door(Door d, boolean cell);
    }

    /** Makes every interior's objects (ShadowVeil only; called with the world's other fixtures). */
    public static void spawn(rpg.Rpg g, Architecture ar, Maker m) {
        clear();
        if (ar == null) return;
        Registry reg = ar.registry;
        // (0.4.6) every graveyard's old graves: a weathered headstone over a grassed mound at each place the graveyard laid
        // out, filled long ago (nothing in it), standing as stone does
        for (Architecture.Structure st : ar.structures) {
            float[] old = st.points.get("oldGraves");
            if (old == null) continue;
            for (int i = 0; i + 1 < old.length; i += 2) {
                Container stone = m.add(new Container(m.id(), "headstone", "Old gravestone", "memorial", old[i], ShadowVeil.standY(old[i], old[i + 1]), old[i + 1] - 0.45f, 0, 0));
                stone.buried = true; stone.decor = true;
            }
        }
        for (Architecture.Piece p : ar.pieces) {
            Furniture.Kind kind;
            try { kind = Furniture.Kind.valueOf(p.kind()); } catch (IllegalArgumentException e) { continue; }
            Furniture f = m.add(new Furniture(m.id(), kind, p.x0(), p.z0(), p.x1(), p.z1(), Math.max(1, p.h()), p.facing(), 0, true, p.y()));
            Architecture.Fitted fit = ar.fitted.get(p.owner());
            f.plain = fit != null && (fit.wealth.equals("poor") || fit.wealth.equals("common"));
            // a lived-in home's hearth is laid and lit
            if (kind == Furniture.Kind.FIREPLACE) f.fuel = 600;
            f.laid = p.tag().equals("laid");
            m.furniture(f);
            Site s = SITES.computeIfAbsent(p.owner(), Site::new);
            s.furniture.add(f);
            if (kind.bed()) { if (p.tag().equals("guest")) { s.guestBeds.add(f); f.name = "Guest bed"; } else s.beds.add(f); }
            if (kind == Furniture.Kind.SLAB) f.name = "Cell slab";
        }
        for (Architecture.InnerDoor d : ar.innerDoors) {
            Door door = m.add(new Door(m.id(), name(ar, d.owner()) + " " + d.name() + " door", d.cells(), d.locked(), null, d.locked() ? 3 : 1, 0, d.floorY() + 1));
            m.door(door, false);
            SITES.computeIfAbsent(d.owner(), Site::new).doors.add(door);
        }
        for (Architecture.Fitting f : ar.fittings) {
            Site s = SITES.computeIfAbsent(f.owner(), Site::new);
            if (f.type().startsWith("station:")) {
                rpg.item.Recipes.Station k;
                try { k = rpg.item.Recipes.Station.valueOf(f.type().substring(8)); } catch (IllegalArgumentException e) { continue; }
                Station st = m.add(new Station(m.id(), k.name().toLowerCase(java.util.Locale.ROOT), title(k), k, f.x(), f.y() + 0.6f, f.z()));
                st.floorY = f.y();
                s.stations.add(st);
                // the station is worked from before it: a post there for whoever works it
                s.posts.add(new Architecture.Fitting(f.owner(), "post:" + f.type(), f.useX(), f.y(), f.useZ(), f.facing(), f.tag(), f.x(), f.z()));
            } else if (f.type().equals("chest")) {
                String nm = switch (f.tag()) { case "vault" -> "Vault strongbox"; case "larder" -> "Larder chest"; case "arms" -> "Arms chest"; case "confiscated" -> "Jail's held goods";
                    case "records" -> "Records chest"; case "vestments" -> "Vestment chest"; case "tack" -> "Tack chest"; case "store", "work" -> "Store chest"; default -> "Chest"; };
                // (0.4.6, gamefixes: a jail's held goods chest held nothing of the prisoner's) a jail's chest is big enough for
                // everything a prisoner carries and wears, and it is where their possessions are kept (Law.arrest)
                boolean held = f.tag().equals("confiscated");
                Container c = new Container(m.id(), "chest", nm, "chest", f.x(), f.y() + 0.4f, f.z(), held ? HELD_CELLS : 20, 0);
                if (held) { Architecture.Structure hs = ar.byId(f.owner()); HELD.putIfAbsent(hs == null ? ShadowVeil.jurisdiction(f.x(), f.z()) : ar.realmOf(hs), c); }
                c.floorY = f.y();
                m.add(c);
                if (f.tag().equals("vault") || f.tag().equals("confiscated")) { c.locked = true; c.lockLevel = 3; }
                s.chests.add(c);
            } else if (f.type().equals("altar")) {
                Fixtures.Altar a = m.add(new Fixtures.Altar(m.id(), f.x(), f.y() + 0.6f, f.z()));
                s.altars.add(a);
                ALTARS.add(new float[]{f.x(), f.y(), f.z(), f.useX(), f.useZ()});
                Architecture.Structure st = ar.byId(f.owner());
                ALTAR_REALMS.add(st == null ? ShadowVeil.jurisdiction(f.x(), f.z()) : ar.realmOf(st));
            } else if (f.type().startsWith("post:")) s.posts.add(f);
        }
        // the jails' cells: a barred door hung in each, the cell kept for its kingdom's law
        for (Architecture.Cell c : ar.cells) {
            Architecture.Structure jail = ar.byId(c.owner());
            String realm = jail == null ? ShadowVeil.jurisdiction(c.gateX() + 0.5f, c.gateZ() + 0.5f) : ar.realmOf(jail);
            int k = CELLS.computeIfAbsent(realm, x -> new ArrayList<>()).size();
            String doorName = k == 0 ? realm + " cell door" : realm + " cell " + (k + 1) + " door";
            Door door = m.add(Door.cellDoor(m.id(), doorName, new int[][]{{c.gateX(), c.gateZ()}}, c.y()));
            m.door(door, true);
            SITES.computeIfAbsent(c.owner(), Site::new).doors.add(door);
            rpg.world.DevMap.Piece slab = new rpg.world.DevMap.Piece("SLAB", c.slab()[0], c.slab()[1], c.slab()[2], c.slab()[3], 1, c.slabFacing(), 0);
            CELLS.get(realm).add(new rpg.world.Jails.Cell(realm, c.box()[0], c.gateZ(), c.box()[2], c.box()[3], c.gateX(), c.inside()[0], c.inside()[1], c.release()[0], c.release()[1], slab, c.y(), c.box()));
        }
        // the registry's record of what works in each building
        if (reg != null) for (Site s : SITES.values()) {
            Registry.Entry e = reg.byId(s.id);
            if (e == null) continue;
            e.interactions.clear();
            if (!s.beds.isEmpty()) e.interactions.add(s.beds.size() + " bed(s) to sleep in");
            if (!s.guestBeds.isEmpty()) e.interactions.add(s.guestBeds.size() + " guest bed(s) to let");
            for (Station st : s.stations) e.interactions.add(st.verb() + " at the " + st.name.toLowerCase(java.util.Locale.ROOT));
            if (!s.chests.isEmpty()) e.interactions.add(s.chests.size() + " chest(s) to store in");
            if (!s.altars.isEmpty()) e.interactions.add("pray, donate and change spellbook at the altar");
            for (Door d : s.doors) e.interactions.add((d.locked ? "locked " : "") + d.name);
            long counters = s.furniture.stream().filter(f -> f.kind == Furniture.Kind.COUNTER).count();
            if (counters > 0) e.interactions.add("served across " + counters + " counter(s)");
            long seats = s.furniture.stream().filter(f -> f.kind.seat()).count();
            if (seats > 0) e.interactions.add(seats + " seat(s)");
        }
    }

    private static String name(Architecture ar, String id) { Architecture.Structure s = ar.byId(id); return s == null ? id : s.name; }

    static String title(rpg.item.Recipes.Station k) {
        return switch (k) { case CARDING_BENCH -> "Carding bench"; case SPINNING_WHEEL -> "Spinning wheel"; case LOOM -> "Loom"; case FULLING_TUB -> "Fulling tub"; case DYE_VAT -> "Dye vat";
            case FURNACE -> "Furnace"; case ANVIL -> "Anvil"; case RANGE -> "Range"; case SAWBENCH -> "Sawbench"; case CRAFT_TABLE -> "Crafting table"; default -> "Workbench"; };
    }

    /** A kingdom's jail cell for its law (null: none). */
    public static rpg.world.Jails.Cell cell(String realm) { List<rpg.world.Jails.Cell> l = CELLS.get(realm); return l == null || l.isEmpty() ? null : l.get(0); }
    /** Every jail cell of every kingdom. */
    public static Map<String, List<rpg.world.Jails.Cell>> cells() { return CELLS; }

    /**
     * The altar a person of a kingdom worships at: the nearest in their own kingdom, else the nearest anywhere within a
     * reach. {x, y, z, frontX, frontZ} (the front: the step before it, where the faithful face it from), or null.
     */
    public static float[] altarFor(String realm, float x, float z, float reach) {
        float[] best = null; double bd = Double.MAX_VALUE;
        for (int k = 0; k < ALTARS.size(); k++) {
            float[] a = ALTARS.get(k);
            double d = Math.hypot(a[0] - x, a[2] - z) + (realm != null && realm.equals(ALTAR_REALMS.get(k)) ? 0 : 400);
            if (d < bd && d - (realm != null && realm.equals(ALTAR_REALMS.get(k)) ? 0 : 400) < reach) { bd = d; best = a; }
        }
        return best;
    }
}
