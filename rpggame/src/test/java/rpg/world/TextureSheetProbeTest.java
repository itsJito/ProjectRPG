package rpg.world;

import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Probe (RPG_PROBE=1): the world's main surface textures each tiled three by three at twice size, as they lie across
 * neighbouring blocks - for judging them for repetition and finish. Written to build/reports/materials/tiled.png.
 */
@EnabledIfEnvironmentVariable(named = "RPG_PROBE", matches = "1")
class TextureSheetProbeTest {
    @Test void sheet() throws Exception {
        int[] layers = {Blocks.L_GRASS, Blocks.L_GRASS_SIDE, Blocks.L_DIRT, Blocks.L_COBBLE, Blocks.L_STONE, Blocks.L_BRICK, Blocks.L_ROOF, Blocks.L_PLASTER,
                Blocks.L_PLANKS, Blocks.L_BARK, Blocks.L_OAK_BARK, Blocks.L_LEAVES, Blocks.L_STONE_BRICKS, Blocks.L_LIMESTONE, Blocks.L_SAND, Blocks.L_MUD};
        int T = Blocks.TEX, S = 2, cell = T * 3 * S + 8, cols = 4;
        BufferedImage out = new BufferedImage(cols * cell, ((layers.length + cols - 1) / cols) * cell, BufferedImage.TYPE_INT_RGB);
        for (int k = 0; k < layers.length; k++) {
            int[][] px = Textures.layer(layers[k]);
            int ox = (k % cols) * cell + 4, oy = (k / cols) * cell + 4;
            for (int y = 0; y < T * 3 * S; y++) for (int x = 0; x < T * 3 * S; x++) {
                int[] c = px[((y / S) % T) * T + (x / S) % T];
                out.setRGB(ox + x, oy + y, c[0] << 16 | c[1] << 8 | c[2]);
            }
        }
        var file = new java.io.File("build/reports/materials/tiled.png"); file.getParentFile().mkdirs();
        javax.imageio.ImageIO.write(out, "png", file);
        System.out.println("[tex] " + file.getAbsolutePath());
    }
}
