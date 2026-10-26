package rpg.world;

import astratto.bridge.NativeEngine;

/** A reusable small dungeon stage in a separate render world; it never edits the playable map. */
public final class TitleStage {
    private TitleStage() {}
    public static long build(NativeEngine engine) {
        int sx = DevMap.SX, sy = DevMap.SY, sz = DevMap.SZ;
        byte[] cells = new byte[sx * sy * sz];
        for (int x = 2; x <= 15; x++) for (int z = 2; z <= 15; z++) {
            for (int y = 0; y < DevMap.FLOOR; y++) cells[(x * sz + z) * sy + y] = (byte) Blocks.COBBLE;
            if (x == 2 || x == 15 || z == 2 || z == 15) for (int y = DevMap.FLOOR; y < DevMap.FLOOR + 6; y++) cells[(x * sz + z) * sy + y] = (byte) Blocks.STONE;
            cells[(x * sz + z) * sy + DevMap.FLOOR + 6] = (byte) Blocks.STONE;
        }
        for (int x : new int[]{3,14}) for (int z : new int[]{3,8,14}) for (int y=DevMap.FLOOR;y<DevMap.FLOOR+6;y++) cells[(x*sz+z)*sy+y]=(byte)Blocks.COBBLE;
        return engine.createWorld(sx,sy,sz,Blocks.STONE,cells,null,astratto.bridge.AstrattoNative.WORLD_LIGHT);
    }
}
