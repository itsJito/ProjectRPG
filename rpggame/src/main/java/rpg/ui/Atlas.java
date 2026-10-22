package rpg.ui;

import astratto.bridge.AstrattoNative;
import astratto.bridge.NativeRenderer;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import org.lwjgl.system.MemoryUtil;
import rpg.skill.Skill;

/**
 * The provided icon art (assets to add/Icons, copied to resources/ui/icons) packed into one texture: skill icons, map
 * markers and the interface icons. Each keeps its own proportions; art larger than a cell is scaled down smoothly.
 * Icons are looked up by file name without the extension ("Mining_Icon", "Bank_map_icon").
 */
public final class Atlas {
    public record Sprite(String name, float u0, float v0, float u1, float v1, int w, int h) {}

    /** Every provided icon, in a fixed order (the map legend lists the map icons from here). */
    public static final String[] NAMES = {
            "Achery_Icon", "Agility_Icon", "Attack_Icon", "Construction_Icon", "Cooking_Icon", "Crafting_Icon", "Defense_Icon",
            "Faith_Icon", "Firemaking_Icon", "Fishing_Icon", "Harvesting_Icon", "Health_Icon", "Magic_Icon", "Mining_Icon",
            "Smithing_Icon", "Strength_Icon", "Thieving_Icon", "Woodcutting_Icon", "Boss_Icon", "XP_Icon",
            "Inventory_Icon_UI", "Skills_icon_UI", "Worn_Equipment_icon_UI", "questionmark_UI",
            "Agility_training_map_icon", "Bank_map_icon", "BowArrow_map_icon", "Casino_map_icon", "Clothes_shop_map_icon",
            "Cookery_shop_map_icon", "Cookingrange_map_icon", "Crafting_shop_map_icon", "Estate_map_icon", "Farming_shop_map_icon",
            "Fishing_shop_map_icon", "Fishing_spot_map_icon", "Food_shop_map_icon", "Fur_trader_map_icon", "Gem_shop_map_icon",
            "General_store_map_icon", "Gravestone_map_icon", "Harvesting_area_map_icon", "Jewellery_shop_map_icon",
            "Minigame_map_icon", "Pet_shop_map_icon", "Platebody_map_icon", "Pub_map_icon", "Quest_map_icon", "Scimitar_map_icon",
            "Shield_map_icon", "Sidequest_map_icon", "Silk_trader_map_icon", "Staff_map_icon", "Sword_map_icon", "Water_source_map_icon"};

    private static final int CELL = 64, PER_ROW = 8;
    public final int texture;
    private final Map<String, Sprite> sprites = new HashMap<>();

    public Atlas(NativeRenderer r) {
        int rows = (NAMES.length + PER_ROW - 1) / PER_ROW, W = CELL * PER_ROW, H = CELL * rows;
        int[] px = new int[W * H];
        for (int i = 0; i < NAMES.length; i++) {
            BufferedImage img = load(NAMES[i]);
            if (img == null) continue;
            int w = img.getWidth(), h = img.getHeight();
            if (w > CELL - 2 || h > CELL - 2) {
                float sc = (CELL - 2f) / Math.max(w, h);
                int nw = Math.max(1, Math.round(w * sc)), nh = Math.max(1, Math.round(h * sc));
                img = shrink(img, nw, nh);
                w = nw; h = nh;
            }
            int cx = (i % PER_ROW) * CELL + (CELL - w) / 2, cy = (i / PER_ROW) * CELL + (CELL - h) / 2;
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) px[(cy + y) * W + cx + x] = img.getRGB(x, y);
            sprites.put(NAMES[i], new Sprite(NAMES[i], cx / (float) W, cy / (float) H, (cx + w) / (float) W, (cy + h) / (float) H, w, h));
        }
        ByteBuffer b = MemoryUtil.memAlloc(W * H * 4);
        for (int p : px) b.put((byte) (p >> 16)).put((byte) (p >> 8)).put((byte) p).put((byte) (p >>> 24));
        b.flip();
        texture = r.texture(AstrattoNative.TEX_2D, W, H, 1, 0, b);
        MemoryUtil.memFree(b);
    }

    public Sprite get(String name) { return sprites.get(name); }
    public int count() { return sprites.size(); }

    private static BufferedImage load(String name) {
        try (InputStream in = Atlas.class.getResourceAsStream("/ui/icons/" + name + ".png")) {
            if (in == null) { System.err.println("[ui] missing icon " + name); return null; }
            BufferedImage src = ImageIO.read(in);
            BufferedImage argb = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
            argb.getGraphics().drawImage(src, 0, 0, null);
            return argb;
        } catch (Exception e) { System.err.println("[ui] icon " + name + ": " + e); return null; }
    }

    /** Halving steps then one bilinear pass: a large image shrinks without aliasing. */
    private static BufferedImage shrink(BufferedImage img, int w, int h) {
        BufferedImage cur = img;
        while (cur.getWidth() / 2 >= w && cur.getHeight() / 2 >= h) cur = scale(cur, cur.getWidth() / 2, cur.getHeight() / 2);
        return scale(cur, w, h);
    }

    private static BufferedImage scale(BufferedImage img, int w, int h) {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        var g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(img, 0, 0, w, h, null);
        g.dispose();
        return out;
    }

    /** The provided icon for a skill. */
    public static String skill(Skill s) {
        return switch (s.name()) {
            case "ARCHERY" -> "Achery_Icon";
            case "DEFENSE" -> "Defense_Icon";
            default -> s.title.replace(" ", "") + "_Icon";
        };
    }
}
