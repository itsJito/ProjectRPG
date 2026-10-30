package rpg.world;

import astratto.bridge.NativeEngine;

/**
 * The title screen's stage (0.2.1, gamefixes): a dark stone chamber deep in a dungeon, in a render world of its own (it
 * never edits the playable map). Dressed-stone walls with pillars along them, a vaulted ceiling, and in the middle of the
 * far wall a massive arched doorway onto a torch-lit passage running back into the dark. The last character played
 * stands in the middle of the floor, in front of the doorway.
 */
public final class TitleStage {
    private TitleStage() {}

    /** The chamber's walls (inclusive), the doorway's span in the far wall, and where the character stands. */
    public static final int X0 = 4, X1 = 26, Z0 = 24, Z1 = 44, HEIGHT = 11, DOOR_X0 = 12, DOOR_X1 = 18, DOOR_H = 7, PASSAGE_Z0 = 2;
    public static final float STAND_X = 15.5f, STAND_Z = 30.5f;
    /** Wall torches: {x, y above the floor, z, the way the wall faces (x, z)}. */
    public static final float[][] TORCHES = {
            {X0 + 0.75f, 3.1f, Z0 + 4.5f, 1, 0}, {X0 + 0.75f, 3.1f, Z0 + 10.5f, 1, 0}, {X0 + 0.75f, 3.1f, Z0 + 16.5f, 1, 0},
            {X1 + 0.25f, 3.1f, Z0 + 4.5f, -1, 0}, {X1 + 0.25f, 3.1f, Z0 + 10.5f, -1, 0}, {X1 + 0.25f, 3.1f, Z0 + 16.5f, -1, 0},
            {DOOR_X0 - 2.5f, 3.6f, Z0 + 1.25f, 0, 1}, {DOOR_X1 + 3.5f, 3.6f, Z0 + 1.25f, 0, 1},
            // down the passage beyond the doorway, flames seen moving through it, smaller and dimmer as it goes back
            {DOOR_X0 - 0.25f, 3.0f, Z0 - 4.5f, 1, 0}, {DOOR_X1 + 1.25f, 3.0f, Z0 - 4.5f, -1, 0}, {DOOR_X0 - 0.25f, 3.0f, Z0 - 11.5f, 1, 0},
            {DOOR_X1 + 1.25f, 3.0f, Z0 - 11.5f, -1, 0}, {DOOR_X0 - 0.25f, 3.0f, Z0 - 18.5f, 1, 0}, {DOOR_X1 + 1.25f, 3.0f, Z0 - 18.5f, -1, 0}};

    public static long build(NativeEngine engine) {
        int sx = DevMap.SX, sy = DevMap.SY, sz = DevMap.SZ, f = DevMap.FLOOR;
        byte[] cells = new byte[sx * sy * sz];
        class Put { void at(int x, int y, int z, int b) { if (x >= 0 && x < sx && z >= 0 && z < sz && y >= 0 && y < sy) cells[(x * sz + z) * sy + y] = (byte) b; }
                    void box(int x0, int y0, int z0, int x1, int y1, int z1, int b) { for (int x = x0; x <= x1; x++) for (int y = y0; y <= y1; y++) for (int z = z0; z <= z1; z++) at(x, y, z, b); } }
        Put p = new Put();
        // the rock everything is cut from
        p.box(X0 - 2, 0, PASSAGE_Z0 - 2, X1 + 2, f + HEIGHT + 2, Z1 + 2, Blocks.STONE);
        // the chamber: hollow, its floor of dressed slabs with a darker border, its walls dressed stone on a granite footing
        p.box(X0 + 1, f, Z0 + 1, X1 - 1, f + HEIGHT - 1, Z1 - 1, Blocks.AIR);
        p.box(X0 + 1, f - 1, Z0 + 1, X1 - 1, f - 1, Z1 - 1, Blocks.STONE_BRICKS);
        for (int x = X0 + 1; x <= X1 - 1; x++) { p.at(x, f - 1, Z0 + 1, Blocks.GRANITE); p.at(x, f - 1, Z1 - 1, Blocks.GRANITE); }
        for (int z = Z0 + 1; z <= Z1 - 1; z++) { p.at(X0 + 1, f - 1, z, Blocks.GRANITE); p.at(X1 - 1, f - 1, z, Blocks.GRANITE); }
        for (int x = X0; x <= X1; x++) for (int z : new int[]{Z0, Z1}) { p.box(x, f, z, x, f + HEIGHT - 1, z, Blocks.STONE_BRICKS); p.at(x, f, z, Blocks.GRANITE); }
        for (int z = Z0; z <= Z1; z++) for (int x : new int[]{X0, X1}) { p.box(x, f, z, x, f + HEIGHT - 1, z, Blocks.STONE_BRICKS); p.at(x, f, z, Blocks.GRANITE); }
        // the vault: the ceiling stepped in toward its ridge
        p.box(X0 + 1, f + HEIGHT - 1, Z0 + 1, X1 - 1, f + HEIGHT - 1, Z1 - 1, Blocks.STONE_BRICKS);
        p.box(X0 + 4, f + HEIGHT - 1, Z0 + 1, X1 - 4, f + HEIGHT - 1, Z1 - 1, Blocks.AIR);
        p.box(X0 + 4, f + HEIGHT, Z0 + 1, X1 - 4, f + HEIGHT, Z1 - 1, Blocks.STONE_BRICKS);
        p.box(X0 + 1, f + HEIGHT - 2, Z0 + 1, X0 + 1, f + HEIGHT - 2, Z1 - 1, Blocks.STONE_BRICKS);
        p.box(X1 - 1, f + HEIGHT - 2, Z0 + 1, X1 - 1, f + HEIGHT - 2, Z1 - 1, Blocks.STONE_BRICKS);
        // pillars along the side walls, between the torches
        for (int z : new int[]{Z0 + 1, Z0 + 7, Z0 + 13, Z1 - 1}) for (int x : new int[]{X0 + 1, X1 - 1}) { p.box(x, f, z, x, f + HEIGHT - 2, z, Blocks.STONE); p.at(x, f, z, Blocks.GRANITE); p.at(x, f + HEIGHT - 2, z, Blocks.GRANITE); }
        // the great doorway: an arch through the far wall, granite-framed, the passage beyond it
        p.box(DOOR_X0, f, Z0, DOOR_X1, f + DOOR_H - 1, Z0, Blocks.AIR);
        p.box(DOOR_X0 + 1, f + DOOR_H, Z0, DOOR_X1 - 1, f + DOOR_H, Z0, Blocks.AIR);
        p.box(DOOR_X0 + 2, f + DOOR_H + 1, Z0, DOOR_X1 - 2, f + DOOR_H + 1, Z0, Blocks.AIR);
        for (int y = f; y <= f + DOOR_H; y++) { p.at(DOOR_X0 - 1, y, Z0, Blocks.STONE); p.at(DOOR_X1 + 1, y, Z0, Blocks.STONE); p.at(DOOR_X0 - 1, y, Z0 + 1, Blocks.STONE); p.at(DOOR_X1 + 1, y, Z0 + 1, Blocks.STONE); }
        p.at(DOOR_X0, f + DOOR_H, Z0, Blocks.STONE); p.at(DOOR_X1, f + DOOR_H, Z0, Blocks.STONE);
        p.at(DOOR_X0 + 1, f + DOOR_H + 1, Z0, Blocks.STONE); p.at(DOOR_X1 - 1, f + DOOR_H + 1, Z0, Blocks.STONE);
        p.box(DOOR_X0 + 2, f + DOOR_H + 2, Z0, DOOR_X1 - 2, f + DOOR_H + 2, Z0, Blocks.STONE);
        p.box(DOOR_X0 - 1, f, PASSAGE_Z0, DOOR_X1 + 1, f + DOOR_H + 1, Z0 - 1, Blocks.AIR);
        p.box(DOOR_X0 - 1, f - 1, PASSAGE_Z0, DOOR_X1 + 1, f - 1, Z0 - 1, Blocks.COBBLE);
        p.box(DOOR_X0 - 2, f, PASSAGE_Z0, DOOR_X0 - 2, f + DOOR_H + 1, Z0 - 1, Blocks.STONE_BRICKS);
        p.box(DOOR_X1 + 2, f, PASSAGE_Z0, DOOR_X1 + 2, f + DOOR_H + 1, Z0 - 1, Blocks.STONE_BRICKS);
        p.box(DOOR_X0 - 1, f, PASSAGE_Z0 - 1, DOOR_X1 + 1, f + DOOR_H + 1, PASSAGE_Z0 - 1, Blocks.STONE_BRICKS);
        // a step up to the doorway, and rubble fallen in the corners
        p.box(DOOR_X0 - 1, f, Z0 + 1, DOOR_X1 + 1, f, Z0 + 1, Blocks.AIR);
        for (int[] r : new int[][]{{X0 + 2, Z1 - 2}, {X0 + 3, Z1 - 2}, {X0 + 2, Z1 - 3}, {X1 - 2, Z0 + 2}, {X1 - 3, Z0 + 2}, {X1 - 2, Z0 + 3}}) p.at(r[0], f, r[1], Blocks.COBBLE);
        return engine.createWorld(sx, sy, sz, Blocks.STONE, cells, null, astratto.bridge.AstrattoNative.WORLD_LIGHT);
    }
}
