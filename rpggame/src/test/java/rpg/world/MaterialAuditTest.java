package rpg.world;

import astratto.bridge.AstrattoNative;
import java.awt.Color;
import java.awt.Font;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.HexFormat;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;

import static org.junit.jupiter.api.Assertions.*;

class MaterialAuditTest {
    @Test void everyLayerHasThe64By64FormatAndDistinctVisibleContent() throws Exception {
        assertEquals(64, Blocks.TEX);
        ByteBuffer data = Blocks.atlasPixels();
        try {
            assertEquals(Blocks.LAYERS * 64 * 64 * 4, data.remaining());
            var hashes = new HashSet<String>();
            int cell = 144, rows = (Blocks.LAYERS + 7) / 8;
            BufferedImage sheet = new BufferedImage(cell * 8, cell * rows, BufferedImage.TYPE_INT_RGB);
            var gr = sheet.createGraphics();
            gr.setColor(new Color(24, 26, 30)); gr.fillRect(0, 0, sheet.getWidth(), sheet.getHeight());
            gr.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
            gr.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            StringBuilder csv = new StringBuilder("layer,width,height,colors,alpha_min,alpha_max,sha256\n");
            for (int layer = 0; layer < Blocks.LAYERS; layer++) {
                byte[] bytes = new byte[64 * 64 * 4]; data.get(bytes);
                String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
                assertTrue(hashes.add(hash), "duplicate texture layer " + layer);
                var colors = new HashSet<Integer>();
                int min = 255, max = 0;
                BufferedImage tile = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
                for (int i = 0; i < 64 * 64; i++) {
                    int a = bytes[i * 4 + 3] & 255;
                    int rgba = a << 24 | (bytes[i * 4] & 255) << 16 | (bytes[i * 4 + 1] & 255) << 8 | bytes[i * 4 + 2] & 255;
                    tile.setRGB(i % 64, i / 64, rgba);
                    if (a > 0) colors.add(rgba);
                    min = Math.min(min, a); max = Math.max(max, a);
                }
                assertFalse(colors.isEmpty(), "empty visible layer " + layer);
                int x = layer % 8 * cell + 8, y = layer / 8 * cell + 16;
                for (int cy = 0; cy < 128; cy += 8) for (int cx = 0; cx < 128; cx += 8) {
                    gr.setColor((cx / 8 + cy / 8) % 2 == 0 ? new Color(80, 80, 88) : new Color(130, 130, 138));
                    gr.fillRect(x + cx, y + cy, 8, 8);
                }
                gr.drawImage(tile, x, y, 128, 128, null);
                gr.setColor(new Color(230, 220, 190)); gr.drawString("Layer " + layer, x, y - 3);
                csv.append(layer).append(",64,64,").append(colors.size()).append(',').append(min).append(',').append(max).append(',').append(hash).append('\n');
            }
            gr.dispose();
            Path out = Path.of("build/reports/materials"); Files.createDirectories(out);
            ImageIO.write(sheet, "png", out.resolve("atlas.png").toFile());
            Files.writeString(out.resolve("layers.csv"), csv);
        } finally { MemoryUtil.memFree(data); }
    }

    @Test void everyBlockFaceHasAValidLayerAndPhysicsAgreesWithRenderedSolidity() throws Exception {
        var rendered = Blocks.renderDefs(); var physical = Blocks.engineDefs();
        assertEquals(Blocks.COUNT, rendered.length); assertEquals(Blocks.COUNT, physical.length);
        StringBuilder csv = new StringBuilder("block,top,side,bottom,solid_voxel,door\n");
        for (int id = 0; id < Blocks.COUNT; id++) {
            var block = rendered[id];
            for (int layer : new int[]{block.layerTop, block.layerSide, block.layerBottom})
                assertTrue(layer >= 0 && layer < Blocks.LAYERS, "unbound face on block " + id);
            boolean solid = (physical[id].flags & AstrattoNative.BLOCK_SOLID) != 0;
            boolean door = (physical[id].flags & AstrattoNative.BLOCK_DOOR) != 0;
            // Doors collide through their moving thin body, rather than a whole solid voxel.
            assertEquals(door, (block.flags & AstrattoNative.RB_DOOR) != 0, "door binding mismatch on block " + id);
            if (!door) assertEquals(solid, (block.flags & AstrattoNative.RB_SOLID) != 0, "collision/render mismatch on block " + id);
            csv.append(id).append(',').append(block.layerTop).append(',').append(block.layerSide).append(',').append(block.layerBottom).append(',').append(solid).append(',').append(door).append('\n');
        }
        Path out = Path.of("build/reports/materials"); Files.createDirectories(out);
        Files.writeString(out.resolve("blocks.csv"), csv);
    }
}
