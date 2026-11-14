import re
def patch(p, pairs):
    s = open(p, encoding='utf-8').read()
    for a, b in pairs:
        assert s.count(a) == 1, (p, a[:90], s.count(a))
        s = s.replace(a, b)
    open(p, 'w', encoding='utf-8').write(s)

R = 'rpggame/src/main/java/rpg/'
patch(R + 'Ids.java', [
("""    public static final long ESTATE = 1L << 40, ESTATE_END = ESTATE + 1_000_000;""",
 """    public static final long ESTATE = 1L << 40, ESTATE_END = ESTATE + 2_000_000;"""),
("""    public static final long SV_PEOPLE = ESTATE + 990_000;""",
 """    public static final long SV_PEOPLE = ESTATE + 990_000;
    /** (0.4.4) ShadowVeil's interiors: their furniture, inner doors, stations, chests, altars and cell doors, in the order fitted. */
    public static final long SV_INTERIORS = ESTATE + 1_100_000;"""),
])

patch(R + 'objects/Door.java', [
("""    public static Door cellDoor(long id, String name, int[][] cells) {
        Door door = new Door(id, name, cells, true, null, 100, 0);
        door.cellDoor = true;
        return door;
    }""",
 """    public static Door cellDoor(long id, String name, int[][] cells) {
        Door door = new Door(id, name, cells, true, null, 100, 0);
        door.cellDoor = true;
        return door;
    }
    /** (0.4.4) A gaol cell's barred door on its own floor (ShadowVeil's gaols): 'walk' is the cell above the floor's blocks. */
    public static Door cellDoor(long id, String name, int[][] cells, int walk) {
        Door door = new Door(id, name, cells, true, null, 100, 0, walk);
        door.cellDoor = true;
        return door;
    }"""),
])

patch(R + 'objects/Station.java', [
("""    public final Recipes.Station kind;
""", """    public final Recipes.Station kind;
    /** (0.4.4) The floor it stands on (its cell above the floor's blocks), where known: an upper storey's station is drawn there. */
    public float floorY = Float.NaN;
"""),
])
patch(R + 'objects/Container.java', [
("""    public boolean locked;
    public int lockLevel;""", """    public boolean locked;
    public int lockLevel;
    /** (0.4.4) The floor it stands on, where known (a chest upstairs is drawn on its own floor, not the ground's top). */
    public float floorY = Float.NaN;"""),
])

# Gfx: stations and chests drawn on their own floor
patch(R + 'render/Gfx.java', [
("""            m.identity().translate(st.x, rpg.world.Ground.at(st.x, st.z), st.z).rotateY((float) Math.PI);""",
 """            m.identity().translate(st.x, Float.isFinite(st.floorY) ? st.floorY : rpg.world.Ground.at(st.x, st.z), st.z).rotateY((float) Math.PI);"""),
("""                float yaw = c.kind.equals("cargo") ? 0 : (float) Math.PI;
                m.identity().translate(c.x, rpg.world.Ground.at(c.x, c.z) + 0.3f, c.z).rotateY(yaw).scale(0.9f, 0.6f, 0.6f); put(box, c.kind.equals("cargo") ? 0x9A7448 : 0x7A5030, 1, 0.8f, 0);
                m.identity().translate(c.x, rpg.world.Ground.at(c.x, c.z) + 0.62f, c.z).rotateY(yaw).scale(0.94f, 0.08f, 0.64f); put(box, 0x5A3A20, 1, 0.8f, 0);
                if (c.locked) { m.identity().translate(c.x, rpg.world.Ground.at(c.x, c.z + 0.31f) + 0.45f, c.z + 0.31f).scale(0.12f, 0.14f, 0.04f); put(box, 0xC0A040, 1, 0.3f, 0.9f); }""",
 """                float yaw = c.kind.equals("cargo") ? 0 : (float) Math.PI;
                float base = Float.isFinite(c.floorY) ? c.floorY : rpg.world.Ground.at(c.x, c.z);
                m.identity().translate(c.x, base + 0.3f, c.z).rotateY(yaw).scale(0.9f, 0.6f, 0.6f); put(box, c.kind.equals("cargo") ? 0x9A7448 : 0x7A5030, 1, 0.8f, 0);
                m.identity().translate(c.x, base + 0.62f, c.z).rotateY(yaw).scale(0.94f, 0.08f, 0.64f); put(box, 0x5A3A20, 1, 0.8f, 0);
                // iron straps round the lid and the body (a chest, not a box)
                for (float sx : new float[]{-0.3f, 0.3f}) { m.identity().translate(c.x, base + 0.34f, c.z).rotateY(yaw).translate(sx, 0, 0).scale(0.05f, 0.66f, 0.66f); put(box, 0x3A3A40, 1, 0.4f, 0.8f); }
                if (c.locked) { m.identity().translate(c.x, (Float.isFinite(c.floorY) ? c.floorY : rpg.world.Ground.at(c.x, c.z + 0.31f)) + 0.45f, c.z + 0.31f).scale(0.12f, 0.14f, 0.04f); put(box, 0xC0A040, 1, 0.3f, 0.9f); }"""),
])

# Jails: a cell on its own floor with its own box (ShadowVeil's), and the kingdoms' gaols
patch(R + 'world/Jails.java', [
("""    public record Cell(String jurisdiction, int wallX, int frontZ, int endX, int backZ,
                       int gateX, float x, float z, float releaseX, float releaseZ, DevMap.Piece slab) {
        /** Includes the doorway and walls, with a small tolerance for body contact. */
        public boolean contains(float px, float py, float pz) {
            return Float.isFinite(px) && Float.isFinite(py) && Float.isFinite(pz)
                    && px >= wallX - 0.25f && px <= endX + 1.25f
                    && pz >= Math.min(frontZ, backZ) - 0.25f && pz <= Math.max(frontZ, backZ) + 1.25f
                    && py >= DevMap.FLOOR - 0.5f && py <= DevMap.FLOOR + 3;
        }
    }""",
 """    public record Cell(String jurisdiction, int wallX, int frontZ, int endX, int backZ,
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
    }"""),
("""    public static Cell forJurisdiction(String jurisdiction) {
        for (Cell cell : CELLS) if (cell.jurisdiction.equals(jurisdiction)) return cell;
        return null;
    }""",
 """    public static Cell forJurisdiction(String jurisdiction) {
        // (0.4.4, gamefixes: there were no jails in any kingdom) on ShadowVeil each kingdom's own gaol
        if (WorldMap.shadowveil()) return jurisdiction == null ? null : rpg.world.shadowveil.Rooms.cell(jurisdiction);
        for (Cell cell : CELLS) if (cell.jurisdiction.equals(jurisdiction)) return cell;
        return null;
    }"""),
])
print('ok')
