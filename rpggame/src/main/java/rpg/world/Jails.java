package rpg.world;

/** Physical cells authored inside the existing chanceries; sentence rules live in Law. */
public final class Jails {
    private Jails() { }
    public record Cell(String jurisdiction, int wallX, int frontZ, int endX, int backZ,
                       int gateX, float x, float z, float releaseX, float releaseZ, DevMap.Piece slab) {
        /** Includes the doorway and walls, with a small tolerance for body contact. */
        public boolean contains(float px, float py, float pz) {
            return Float.isFinite(px) && Float.isFinite(py) && Float.isFinite(pz)
                    && px >= wallX - 0.25f && px <= endX + 1.25f
                    && pz >= Math.min(frontZ, backZ) - 0.25f && pz <= Math.max(frontZ, backZ) + 1.25f
                    && py >= DevMap.FLOOR - 0.5f && py <= DevMap.FLOOR + 3;
        }
    }
    public static final Cell[] CELLS = {
        new Cell(DevMap.ALPHA, 115, 56, 119, 53, 118, 116.5f, 54.5f, 118.5f, 58.5f,
                new DevMap.Piece("SLAB", 118, 53, 118, 54, 1, 0, 0)),
        new Cell(DevMap.BETA, 36, 119, 39, 121, 38, 37.5f, 120.5f, 38.5f, 116.5f,
                new DevMap.Piece("SLAB", 38, 120, 38, 121, 1, 2, 0)),
        // DevPalace's cell, in the back of the basilica behind the praetor's bench (0.2.0)
        new Cell(DevMap.PALACE, 135, 118, 139, 121, 137, 137.0f, 120.0f, 138.5f, 115.5f,
                new DevMap.Piece("SLAB", 136, 120, 136, 121, 1, 2, 0))
    };
    public static Cell forJurisdiction(String jurisdiction) {
        for (Cell cell : CELLS) if (cell.jurisdiction.equals(jurisdiction)) return cell;
        return null;
    }
}
