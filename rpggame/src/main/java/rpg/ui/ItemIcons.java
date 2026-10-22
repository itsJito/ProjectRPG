package rpg.ui;

import astratto.bridge.AstrattoNative;
import astratto.bridge.NativeRenderer;
import astratto.voxel.VoxModel;
import astratto.voxel.VoxRaster;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import org.lwjgl.system.MemoryUtil;
import rpg.item.ItemDef;
import rpg.render.ItemModels;

/**
 * Inventory icons rendered from each item's own voxel model (the same model the hand holds and the ground shows), at
 * the shape of its grid footprint: a long sword lies along its one-by-four cells, logs along their three-by-one. Made
 * once per item, when first shown.
 */
public final class ItemIcons {
    /** Pixels per grid cell in the icon image (drawn smaller, so it stays crisp). */
    static final int CELL = 40;
    record Icon(int texture, int w, int h) {}

    private final NativeRenderer r;
    private final Map<String, Icon> icons = new HashMap<>();

    ItemIcons(NativeRenderer r) { this.r = r; }

    Icon of(ItemDef d) {
        return icons.computeIfAbsent(d.id, k -> make(d));
    }

    private Icon make(ItemDef d) {
        int w = Math.max(1, d.gw) * CELL, h = Math.max(1, d.gh) * CELL;
        int[] px = raster(d, CELL);
        ByteBuffer b = MemoryUtil.memAlloc(w * h * 4);
        for (int p : px) {
            int a = p >>> 24;
            if (a != 0) a = 255;
            b.put((byte) (p >> 16)).put((byte) (p >> 8)).put((byte) p).put((byte) a);
        }
        b.flip();
        int tex = r.texture(AstrattoNative.TEX_2D, w, h, 1, 0, b);
        MemoryUtil.memFree(b);
        return new Icon(tex, w, h);
    }

    /** The icon's pixels (ARGB, rows from the top) at a size per grid cell. */
    public static int[] raster(ItemDef d, int cell) {
        VoxModel m = ItemModels.of(d).vox();
        int w = Math.max(1, d.gw) * cell, h = Math.max(1, d.gh) * cell;
        // turn the model so its silhouette matches the footprint's shape: upright in a tall footprint, lying in a wide one
        int[] bb = VoxRaster.bounds(m);
        float mw = bb[3] - bb[0], mh = bb[4] - bb[1];
        float foot = w / (float) h;
        float upright = Math.abs((float) Math.log((mw / mh) / foot)), lying = Math.abs((float) Math.log((mh / mw) / foot));
        float roll = lying + 0.05f < upright ? (float) (Math.PI / 2) : 0;
        boolean flat = (bb[5] - bb[2]) <= 2;
        float yaw = flat ? 0.18f : 0.55f, pitch = flat ? 0.2f : 0.42f;
        // a shield is shown by its face (its model's face looks the way the arm holds it, to the front)
        if (d.family == ItemDef.Family.SHIELD) yaw += (float) Math.PI;
        return VoxRaster.render(m, w, h, yaw, pitch, roll, 0.86f, true);
    }
}
