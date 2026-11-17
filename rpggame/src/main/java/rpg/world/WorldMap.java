package rpg.world;

/**
 * (0.4.0, gamefixes: ShadowVeil) The map a session is played on, and what every shared system asks of it: how big the
 * world is, where the sea stands, whose law holds at a point, where a new character wakes. Two maps exist side by side
 * and neither touches the other's data:
 * <ul>
 * <li>{@link Kind#SHADOWVEIL} - the permanent 1,072 x 1,072 world of Ashenwrath's playable square (rpg.world.shadowveil),
 * 200 cells high; the default from 0.4.0 on;</li>
 * <li>{@link Kind#DEVMAP} - the 192 x 256 x 100 developer map (DevMap), kept whole and unchanged for regression testing.</li>
 * </ul>
 * The map that was built last is the active one: {@link DevMap#build} and {@code ShadowVeil.build} each make theirs active
 * before they return, so a session (and every test) plays on the world it built. The law districts' names are the map's
 * (DevMap.ALPHA, BETA and PALACE are the three towns that keep a lawman; on ShadowVeil they are Loxford, Harlette and Severia).
 */
public final class WorldMap {
    public enum Kind { DEVMAP, SHADOWVEIL }

    public final Kind kind;
    /** The id saved with a game and used on the command line and in settings ("devmap", "shadowveil"). */
    public final String id;
    public final String title;
    /** The world's cells: x (west to east), y (up), z (north to south on screen; the map's south is +z). */
    public final int sizeX, sizeY, sizeZ;
    /** The highest cell of still sea water; the usual ground height (the devmap's floor; ShadowVeil's has none and uses the sea). */
    public final int sea, floor;
    /** Where a new character is set down, and which way they face (the camera's yaw). */
    public final float spawnX, spawnZ, spawnYaw;

    private WorldMap(Kind kind, String id, String title, int sizeX, int sizeY, int sizeZ, int sea, int floor, float spawnX, float spawnZ, float spawnYaw) {
        this.kind = kind; this.id = id; this.title = title; this.sizeX = sizeX; this.sizeY = sizeY; this.sizeZ = sizeZ;
        this.sea = sea; this.floor = floor; this.spawnX = spawnX; this.spawnZ = spawnZ; this.spawnYaw = spawnYaw;
    }

    /** The devmap as it has always been (its east strip of rock holds the dungeons). */
    public static final WorldMap DEVMAP = new WorldMap(Kind.DEVMAP, "devmap", "Devmap (legacy test map)", DungeonLayout.WORLD_X, DevMap.SY, DevMap.SZ,
            DevMap.FLOOR - 1, DevMap.FLOOR, DevMap.SPAWN_X, DevMap.SPAWN_Z, (float) Math.PI);

    private static volatile WorldMap active = DEVMAP;

    /** The map the last world was built from (the devmap until one is built). */
    public static WorldMap active() { return active; }
    public static boolean shadowveil() { return active.kind == Kind.SHADOWVEIL; }
    /** Whether the devmap's built content - its towns, fixtures, dungeons - is in the world (false on ShadowVeil, which has terrain only in 0.4.0). */
    public static boolean devmap() { return active.kind == Kind.DEVMAP; }

    /** Makes the devmap the active map (its jurisdictions back to their own names). */
    public static WorldMap activateDevmap() {
        DevMap.ALPHA = "DevCity"; DevMap.BETA = "DevTown"; DevMap.PALACE = "DevPalace";
        active = DEVMAP;
        return DEVMAP;
    }

    /** A map other than the devmap, made active by its builder; the three lawman towns renamed to its own. */
    public static WorldMap activate(Kind kind, String id, String title, int sizeX, int sizeY, int sizeZ, int sea, float spawnX, float spawnZ, float spawnYaw,
                                    String city, String town, String empire) {
        WorldMap m = new WorldMap(kind, id, title, sizeX, sizeY, sizeZ, sea, sea + 1, spawnX, spawnZ, spawnYaw);
        DevMap.ALPHA = city; DevMap.BETA = town; DevMap.PALACE = empire;
        active = m;
        return m;
    }

    /** The map named in a save, the settings or RPG_MAP (unknown names: ShadowVeil). */
    public static Kind parse(String name) {
        if (name == null) return Kind.SHADOWVEIL;
        String n = name.trim().toLowerCase(java.util.Locale.ROOT);
        return n.equals("devmap") || n.equals("dev") || n.equals("legacy") ? Kind.DEVMAP : Kind.SHADOWVEIL;
    }

    // ------------------------------------------------------------------ what the shared systems ask
    /** The overworld's width (the devmap's 192, not counting its dungeon strip). */
    public int overworldX() { return kind == Kind.DEVMAP ? DevMap.SX : sizeX; }

    /** Where a body stands at (x, z) on the open ground: the devmap's floor; on ShadowVeil one above the terrain there. */
    public static float standY(float x, float z) {
        return devmap() ? DevMap.FLOOR : rpg.world.shadowveil.ShadowVeil.standY(x, z);
    }
    /**
     * A height for a body at (x, z) that is not inside the ground: the height kept when it is plausible, the ground's
     * otherwise (a body made at the devmap's floor height on ShadowVeil's hills would be buried). The devmap: unchanged.
     */
    public static float settleY(float x, float y, float z) {
        if (devmap()) return y;
        // (0.4.7) a body in a dungeon's level under the map stays there (it is not buried: the rooms are cut out of the rock)
        if (DungeonLayout.underAt(x, y, z) != null) return y;
        float g = standY(x, z);
        return y < g - 0.5f || y > g + 48 ? g : y;
    }
}
