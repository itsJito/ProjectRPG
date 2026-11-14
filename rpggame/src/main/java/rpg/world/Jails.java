package rpg.world;

/** Physical cells authored inside the existing chanceries; sentence rules live in Law. */
public final class Jails {
    private Jails() { }
    public record Cell(String jurisdiction, int wallX, int frontZ, int endX, int backZ,
                       int gateX, float x, float z, float releaseX, float releaseZ, DevMap.Piece slab, int floorY, int[] box) {
        /** A devmap cell (on the devmap's floor, its box from its walls). */
        public Cell(String jurisdiction, int wallX, int frontZ, int endX, int backZ, int gateX, float x, float z, float releaseX, float releaseZ, DevMap.Piece slab) {
            this(jurisdiction, wallX, frontZ, endX, backZ, gateX, x, z, releaseX, releaseZ, slab, DevMap.FLOOR, null);
        }
        /** Includes the doorway and walls, with a small tolerance for body contact. */
        public boolean contains(float px, float py, float pz) {
            if (!Float.isFinite(px) || !Float.isFinite(py) || !Float.isFinite(pz) || py < floorY - 0.5f || py > floorY + 3) return false;
            // (0.4.4) ShadowVeil's cells: their own box {x0, z0, x1, z1}, whichever way they face
            if (box != null) return px >= box[0] - 0.25f && px <= box[2] + 1.25f && pz >= box[1] - 0.25f && pz <= box[3] + 1.25f;
            return px >= wallX - 0.25f && px <= endX + 1.25f && pz >= Math.min(frontZ, backZ) - 0.25f && pz <= Math.max(frontZ, backZ) + 1.25f;
        }
    }
    public static final Cell[] CELLS = {
        new Cell(DevMap.ALPHA, 115, 56, 119, 53, 118, 116.5f, 54.5f, 118.5f, 58.5f,
                new DevMap.Piece("SLAB", 117, 54, 117, 55, 1, 2, 0)),
        new Cell(DevMap.BETA, 36, 119, 39, 121, 38, 37.5f, 120.5f, 38.5f, 116.5f,
                new DevMap.Piece("SLAB", 38, 120, 38, 121, 1, 2, 0)),
        // DevPalace's cell, in the back of the basilica behind the praetor's bench (0.2.0)
        new Cell(DevMap.PALACE, 135, 118, 139, 121, 137, 137.0f, 120.0f, 138.5f, 115.5f,
                new DevMap.Piece("SLAB", 136, 119, 136, 120, 1, 0, 0))
    };
    public static Cell forJurisdiction(String jurisdiction) {
        // (0.4.4, gamefixes: there were no jails in any kingdom) on ShadowVeil each kingdom's own jail
        if (WorldMap.shadowveil()) return jurisdiction == null ? null : rpg.world.shadowveil.Rooms.cell(jurisdiction);
        for (Cell cell : CELLS) if (cell.jurisdiction.equals(jurisdiction)) return cell;
        return null;
    }
}
