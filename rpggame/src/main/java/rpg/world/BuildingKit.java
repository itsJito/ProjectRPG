package rpg.world;

import java.util.ArrayList;
import java.util.List;

/**
 * The standard way buildings are raised (0.2.1, gamefixes: a standardized way of creating buildings, streets and
 * interiors). Every builder of the map raises its houses through here, so every building keeps the same rules - and the
 * rules are checked against the built world (BuildingStandardTest):
 * <ul>
 * <li>walls stand at least {@link #MIN_HEIGHT} blocks above the floor;</li>
 * <li>walls are two-faced blocks where the material has an inside finish: brick or stone outside, plaster or planks
 *     inside, in one layer of blocks ({@link #innerFinish});</li>
 * <li>a doorway opens onto {@link #APRON} clear cells of ground outside it, so a street or a lane - never a wall, a
 *     tree or another house - is what it leads to;</li>
 * <li>inside, furniture stands against the walls and every piece is reachable on foot from the door, with a walkway at
 *     least one cell wide.</li>
 * </ul>
 * The plans are kept so the rules can be checked, and so the routers can know where the doors are.
 */
public final class BuildingKit {
    private BuildingKit() {}

    public static final int MIN_HEIGHT = 3, APRON = 2;

    /** A building as raised: its walls' footprint (inclusive), where its doorway is and which way its wall runs ('x' or 'z'), and its walls' height. */
    public record Plan(String name, int x0, int z0, int x1, int z1, int doorX, int doorZ, char axis, int height) {
        public boolean inside(int x, int z) { return x > x0 && x < x1 && z > z0 && z < z1; }
    }
    private static final List<Plan> PLANS = new ArrayList<>();
    public static synchronized List<Plan> plans() { return List.copyOf(PLANS); }
    static synchronized void reset() { PLANS.clear(); }
    static synchronized void plan(Plan p) { PLANS.add(p); }

    /** What a wall shows inside, by what it is outside: the finish a builder would give it (or the wall itself, bare). */
    public static int innerFinish(int outer) {
        return switch (outer) {
            case Blocks.BRICK, Blocks.STONE_BRICKS -> Blocks.PLASTER;
            case Blocks.PLASTER -> Blocks.PLANKS;
            case Blocks.LIMESTONE_BRICKS -> Blocks.MARBLE;
            default -> outer;
        };
    }

    /** Somewhere to put blocks: the map builders' own writers. */
    public interface Cells { void set(int x, int y, int z, int id); }

    /**
     * The four walls of a building, y0 to y1 inclusive (at least MIN_HEIGHT high), each cell a two-faced block whose
     * inside faces into the room; the corners stay the plain outside material (they show no inside).
     */
    public static void walls(Cells c, int x0, int z0, int x1, int z1, int y0, int y1, int outer) {
        if (y1 - y0 + 1 < MIN_HEIGHT) throw new IllegalArgumentException("a building's walls stand at least " + MIN_HEIGHT + " blocks high");
        int inner = innerFinish(outer);
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                boolean corner = x == x0 || x == x1;
                c.set(x, y, z0, corner || inner == outer ? outer : Blocks.dualOf(outer, inner, Blocks.IN_PZ));
                c.set(x, y, z1, corner || inner == outer ? outer : Blocks.dualOf(outer, inner, Blocks.IN_NZ));
            }
            for (int z = z0 + 1; z < z1; z++) {
                c.set(x0, y, z, inner == outer ? outer : Blocks.dualOf(outer, inner, Blocks.IN_PX));
                c.set(x1, y, z, inner == outer ? outer : Blocks.dualOf(outer, inner, Blocks.IN_NX));
            }
        }
    }
}
