package rpg.objects;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import rpg.Ids;
import rpg.Rpg;
import rpg.actor.Actor;
import rpg.interact.Option;
import rpg.interact.Result;
import rpg.item.Recipes;
import rpg.item.Recipes.Recipe;
import rpg.world.DevMap;

/**
 * The Construction plot (GDD 3.4 property, supported building work): furniture built here is placed into the world as
 * objects - real, collidable, persistent, owned by the builder - and can be removed again. Each piece has its own place
 * in the plot.
 */
public final class BuildSpot extends Station {
    /** The pieces that can be built, in a fixed order (their fixture ids follow it), and where each stands. */
    public static final String[] ORDER = {"crate", "table", "bookcase", "bed"};
    public static final Map<String, DevMap.Piece> PIECES = Map.of(
            "crate", new DevMap.Piece("CRATE", 76, 25, 76, 25, 1, 0, 0),
            "table", new DevMap.Piece("TABLE", 77, 27, 78, 27, 1, 0, 0),
            "bookcase", new DevMap.Piece("BOOKCASE", 79, 25, 79, 25, 2, 1, 0),
            "bed", new DevMap.Piece("BED", 75, 27, 75, 28, 1, 0, Ids.PLAYER));
    public final Map<String, Boolean> built = new HashMap<>();
    /** The furniture objects themselves (made with the fixtures, standing only once built). */
    public final Map<String, Furniture> pieces = new LinkedHashMap<>();

    public BuildSpot(long id, float x, float y, float z) {
        super(id, "build_spot", "Build spot", Recipes.Station.BUILD_SPOT, x, y, z);
        for (String k : ORDER) built.put(k, false);
    }

    @Override public float focusRadius() { return 1.6f; }

    @Override public String refusal(Recipe r, Actor a) {
        if (r.furniture != null && built.getOrDefault(r.furniture, false)) return "There's already a " + r.furniture + " here.";
        return super.refusal(r, a);
    }

    @Override public List<Option> options(Rpg g, Actor a) {
        List<Option> o = new java.util.ArrayList<>(super.options(g, a));
        for (String k : ORDER) if (built.get(k)) o.add(Option.on("remove:" + k, "Remove " + k, 3, g.cfg.interactRange));
        return o;
    }

    @Override public Result perform(Rpg g, Actor a, String action, long req) {
        if (action.startsWith("remove:")) {
            String why = reach(g, a, g.cfg.interactRange);
            if (why != null) return Result.no(why);
            String f = action.substring(7);
            if (!built.getOrDefault(f, false)) return Result.no("There's no " + f + " to remove.");
            Furniture piece = pieces.get(f);
            if (piece != null && occupied(g, piece)) return Result.no("Something is in the way.");
            built.put(f, false);
            place(g, f, false);
            g.state.commit(req, "FurnitureRemoved", a.id, id, jurisdiction(), 0, f);
            return Result.ok();
        }
        return super.perform(g, a, action, req);
    }

    @Override protected Result make(Rpg g, Actor a, Recipe r) {
        Furniture piece = r.furniture == null ? null : pieces.get(r.furniture);
        if (piece != null && occupied(g, piece)) return Result.no("Something is standing where the " + r.furniture + " goes.");
        Result res = super.make(g, a, r);
        if (res.committed() && r.furniture != null) {
            built.put(r.furniture, true);
            if (piece != null) piece.owner = a.id;
            place(g, r.furniture, true);
            g.state.commit("FurnitureBuilt", a.id, piece == null ? id : piece.id, jurisdiction(), 0, r.furniture);
        }
        return res;
    }

    /** Whether a body stands in the piece's cells (it cannot be built into or removed from under someone). */
    private static boolean occupied(Rpg g, Furniture f) {
        for (Actor o : g.actors())
            if (o.x + o.radius > f.x0 && o.x - o.radius < f.x1 + 1 && o.z + o.radius > f.z0 && o.z - o.radius < f.z1 + 1 && o.y < DevMap.FLOOR + f.h) return true;
        return false;
    }

    private void place(Rpg g, String f, boolean on) {
        Furniture piece = pieces.get(f);
        if (piece != null) piece.place(g, on);
    }

    public void apply(Rpg g) { for (String k : ORDER) place(g, k, built.get(k)); }

    @Override public String inspect(Rpg g) { return super.inspect(g) + "\n  built=" + built; }

    @Override public Map<String, Object> save() { Map<String, Object> m = super.save(); m.put("built", new HashMap<String, Object>(built)); return m; }
    @Override public void load(Rpg g, Map<String, Object> m) {
        super.load(g, m);
        Map<String, Object> b = astratto.util.Json.obj(m, "built");
        if (b != null) for (String k : ORDER) built.put(k, Boolean.TRUE.equals(b.get(k)));
        apply(g);
    }
}
