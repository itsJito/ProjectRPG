package rpg.ui;

import astratto.bridge.AstrattoNative;
import astratto.bridge.NativeRenderer;
import java.nio.ByteBuffer;
import org.lwjgl.system.MemoryUtil;
import rpg.skill.Skill;

/**
 * The icon sheet, painted from code in 16 x 16 pixel art with a dark outline (OSRS-like): one icon per skill (in skill
 * order), the side-panel tabs, the orbs, and a few markers.
 */
public final class Icons {
    private Icons() {}

    public static final int SIZE = 16, PER_ROW = 8;
    public static final int TAB_COMBAT = 18, TAB_SKILLS = 19, TAB_QUESTS = 20, TAB_INV = 21, TAB_EQUIP = 22, TAB_MAGIC = 23, TAB_DEBUG = 24,
            ORB_HP = 25, ORB_FAITH = 26, ORB_STAMINA = 27, ORB_MANA = 28, SKULL = 29, COMPASS = 30, COIN = 31;

    public static int skill(Skill s) { return s.ordinal(); }

    public static int texture(NativeRenderer r) {
        int w = SIZE * PER_ROW;
        int[] px = new int[w * w];
        for (int i = 0; i < 32; i++) {
            int[] c = new int[SIZE * SIZE];
            paint(i, new Canvas(c));
            outline(c);
            int ox = (i % PER_ROW) * SIZE, oy = (i / PER_ROW) * SIZE;
            for (int y = 0; y < SIZE; y++) for (int x = 0; x < SIZE; x++) px[(oy + y) * w + ox + x] = c[y * SIZE + x];
        }
        ByteBuffer b = MemoryUtil.memAlloc(w * w * 4);
        for (int p : px) b.put((byte) (p >> 16)).put((byte) (p >> 8)).put((byte) p).put((byte) (p >>> 24));
        b.flip();
        int id = r.texture(AstrattoNative.TEX_2D, w, w, 1, AstrattoNative.TEX_NEAREST, b);
        MemoryUtil.memFree(b);
        return id;
    }

    static final class Canvas {
        final int[] p;
        Canvas(int[] p) { this.p = p; }
        void set(int x, int y, int rgb) { if (x >= 0 && y >= 0 && x < SIZE && y < SIZE) p[y * SIZE + x] = 0xFF000000 | rgb; }
        void rect(int x0, int y0, int x1, int y1, int rgb) { for (int y = y0; y <= y1; y++) for (int x = x0; x <= x1; x++) set(x, y, rgb); }
        void line(int x0, int y0, int x1, int y1, int rgb) {
            int n = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
            for (int i = 0; i <= n; i++) set(Math.round(x0 + (x1 - x0) * i / (float) Math.max(1, n)), Math.round(y0 + (y1 - y0) * i / (float) Math.max(1, n)), rgb);
        }
        void thick(int x0, int y0, int x1, int y1, int rgb) { line(x0, y0, x1, y1, rgb); line(x0 + 1, y0, x1 + 1, y1, rgb); }
        void disc(float cx, float cy, float r, int rgb) { for (int y = 0; y < SIZE; y++) for (int x = 0; x < SIZE; x++) if (Math.hypot(x + 0.5 - cx, y + 0.5 - cy) <= r) set(x, y, rgb); }
        void ring(float cx, float cy, float r, float t, int rgb) { for (int y = 0; y < SIZE; y++) for (int x = 0; x < SIZE; x++) { double d = Math.hypot(x + 0.5 - cx, y + 0.5 - cy); if (d <= r && d >= r - t) set(x, y, rgb); } }
    }

    private static void outline(int[] c) {
        int[] o = c.clone();
        for (int y = 0; y < SIZE; y++) for (int x = 0; x < SIZE; x++) {
            if ((c[y * SIZE + x] >>> 24) != 0) continue;
            boolean edge = false;
            for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int nx = x + d[0], ny = y + d[1];
                if (nx >= 0 && ny >= 0 && nx < SIZE && ny < SIZE && (c[ny * SIZE + nx] >>> 24) != 0) edge = true;
            }
            if (edge) o[y * SIZE + x] = 0xFF101010;
        }
        System.arraycopy(o, 0, c, 0, c.length);
    }

    private static final int STEEL = 0xC8CCD4, DARK = 0x5A5E66, WOOD = 0x8A5A2A, GOLD = 0xE8C040;

    private static void paint(int i, Canvas c) {
        switch (i) {
            case 0 -> { c.thick(3, 12, 12, 3, STEEL); c.line(4, 12, 12, 4, 0xFFFFFF); c.line(2, 10, 5, 13, GOLD); c.line(1, 14, 3, 12, WOOD); }          // Attack: sword
            case 1 -> { c.disc(5.5f, 6, 3.4f, 0xD02020); c.disc(10.5f, 6, 3.4f, 0xD02020); for (int y = 7; y < 14; y++) c.rect(8 - (14 - y), y, 7 + (14 - y), y, 0xD02020); c.set(4, 4, 0xFF8080); }  // Health: heart
            case 2 -> { c.thick(3, 13, 11, 5, WOOD); c.line(7, 2, 13, 8, DARK); c.line(8, 2, 14, 8, STEEL); c.line(6, 3, 12, 9, DARK); }                      // Mining: pickaxe
            case 3 -> { c.rect(4, 5, 11, 12, 0xC89060); c.rect(4, 4, 11, 5, 0xB07848); for (int k = 0; k < 4; k++) c.line(5 + k * 2, 4, 5 + k * 2, 7, 0x8A5A30); c.rect(2, 8, 4, 12, 0xC89060); }  // Strength: fist
            case 4 -> { c.disc(9, 3, 1.6f, 0x50A040); c.line(9, 4, 8, 9, 0x50A040); c.line(8, 9, 11, 14, 0x50A040); c.line(8, 9, 5, 13, 0x50A040); c.line(8, 6, 12, 7, 0x50A040); c.line(8, 6, 4, 7, 0x50A040); }  // Agility: runner
            case 5 -> { c.rect(2, 9, 13, 11, DARK); c.rect(5, 12, 10, 14, DARK); c.rect(3, 8, 13, 8, 0x80848C); c.thick(9, 2, 12, 6, WOOD); c.rect(7, 1, 11, 3, STEEL); }  // Smithing: anvil
            case 6 -> { c.rect(3, 2, 12, 9, 0x4060C0); for (int y = 10; y < 15; y++) c.rect(3 + (y - 9), y, 12 - (y - 9), y, 0x4060C0); c.line(7, 3, 7, 12, 0xD0D8F0); c.line(4, 6, 11, 6, 0xD0D8F0); c.ring(7.5f, 7, 7, 1, STEEL); }  // Defense: shield
            case 7 -> { c.line(8, 14, 8, 6, 0x407020); c.disc(5, 6, 3, 0x50A040); c.disc(11, 5, 3, 0x60B848); c.disc(8, 3, 2.5f, 0x48A038); }                   // Harvesting: herb
            case 8 -> { c.disc(7, 8, 4, 0x7090B0); c.rect(3, 6, 10, 10, 0x7090B0); for (int y = 4; y < 13; y++) c.set(13 - Math.abs(y - 8) / 2, y, 0x7090B0); c.line(12, 4, 12, 12, 0x7090B0); c.set(5, 7, 0x101010); }  // Fishing: fish
            case 9 -> { for (int y = 1; y < 15; y++) c.set(5 + (int) (Math.abs(y - 7.5) * Math.abs(y - 7.5) / 9), y, WOOD); c.line(5, 1, 5, 14, 0xE0E0D0); c.thick(2, 8, 13, 8, 0xC0A070); c.line(13, 8, 11, 6, STEEL); c.line(13, 8, 11, 10, STEEL); }  // Archery: bow
            case 10 -> { c.rect(1, 6, 14, 9, 0x202024); c.rect(4, 7, 5, 8, 0xE0E0E0); c.rect(10, 7, 11, 8, 0xE0E0E0); c.line(1, 6, 3, 4, 0x202024); }     // Thieving: mask
            case 11 -> { c.rect(3, 7, 12, 12, 0x505058); c.rect(2, 6, 13, 7, 0x606068); c.line(1, 8, 2, 8, 0x505058); c.line(13, 8, 14, 8, 0x505058); c.set(6, 4, 0xC0C0C0); c.set(9, 3, 0xC0C0C0); c.set(8, 5, 0xC0C0C0); }  // Cooking: pot
            case 12 -> { c.ring(8, 8, 6.5f, 1.4f, GOLD); c.rect(7, 3, 8, 13, 0xF0E0A0); c.rect(4, 6, 11, 7, 0xF0E0A0); }                                       // Faith: halo and cross
            case 13 -> { c.rect(5, 3, 10, 12, 0xB08850); c.rect(4, 2, 11, 3, 0x8A6034); c.rect(4, 12, 11, 13, 0x8A6034); c.line(5, 6, 10, 6, 0xC03030); c.line(5, 8, 10, 9, 0xC03030); c.thick(11, 4, 14, 1, STEEL); }  // Crafting: spool and needle
            case 14 -> { c.disc(8, 10, 4.5f, 0xE06010); c.disc(8, 11, 2.6f, 0xF0C020); for (int y = 2; y < 8; y++) c.rect(8 - (y - 2) / 2, y, 8 + (y - 2) / 3, y, 0xE06010); c.rect(3, 14, 12, 14, WOOD); }  // Firemaking: flame
            case 15 -> { int col = 0x4080F0; c.line(8, 1, 10, 6, col); c.line(10, 6, 15, 6, col); c.line(15, 6, 11, 9, col); c.line(11, 9, 13, 14, col); c.line(13, 14, 8, 11, col); c.line(8, 11, 3, 14, col); c.line(3, 14, 5, 9, col); c.line(5, 9, 1, 6, col); c.line(1, 6, 6, 6, col); c.line(6, 6, 8, 1, col); c.rect(6, 6, 10, 10, col); c.rect(7, 4, 9, 11, col); }  // Magic: star
            case 16 -> { c.rect(1, 10, 9, 13, WOOD); c.disc(9, 11.5f, 2, 0xB08850); c.thick(5, 9, 12, 2, 0x6A4422); c.rect(11, 1, 14, 5, STEEL); }            // Woodcutting: axe and log
            case 17 -> { for (int y = 2; y < 8; y++) c.rect(8 - (y - 2), y, 8 + (y - 2), y, 0xA03828); c.rect(4, 8, 12, 14, 0xC8B080); c.rect(7, 10, 9, 14, WOOD); }  // Construction: house
            case TAB_COMBAT -> { c.thick(2, 13, 13, 2, STEEL); c.thick(2, 2, 13, 13, STEEL); c.rect(6, 10, 9, 11, GOLD); }
            case TAB_SKILLS -> { c.rect(2, 9, 4, 14, 0x40C040); c.rect(6, 5, 8, 14, 0xE0C040); c.rect(10, 2, 12, 14, 0xD04040); }
            case TAB_QUESTS -> { c.rect(3, 3, 12, 13, 0xE8D8A8); c.rect(2, 2, 13, 3, 0xC8B080); c.rect(2, 13, 13, 14, 0xC8B080); for (int y = 5; y < 12; y += 2) c.line(5, y, 10, y, 0x806040); }
            case TAB_INV -> { c.disc(8, 10, 5, 0x9A7040); c.rect(6, 3, 10, 6, 0x9A7040); c.line(5, 4, 11, 4, 0x6A4A20); }
            case TAB_EQUIP -> { c.rect(4, 4, 11, 11, 0x9CA4AE); c.disc(7.5f, 5, 4, 0x9CA4AE); c.rect(5, 7, 10, 8, 0x303030); c.rect(7, 8, 8, 12, 0x9CA4AE); }
            case TAB_MAGIC -> { c.rect(2, 3, 13, 13, 0x3050A0); c.rect(7, 3, 8, 13, 0x203070); c.set(4, 6, 0xF0F080); c.set(11, 8, 0xF0F080); c.set(10, 5, 0xF0F080); c.set(5, 10, 0xF0F080); }
            case TAB_DEBUG -> { c.disc(8, 8, 5.5f, 0x909098); c.disc(8, 8, 2.2f, 0x303034); for (int k = 0; k < 8; k++) { double a = k * Math.PI / 4; c.rect(8 + (int) Math.round(Math.cos(a) * 6) - 1, 8 + (int) Math.round(Math.sin(a) * 6) - 1, 8 + (int) Math.round(Math.cos(a) * 6), 8 + (int) Math.round(Math.sin(a) * 6), 0x909098); } }
            case ORB_HP -> paint(1, c);
            case ORB_FAITH -> paint(12, c);
            case ORB_STAMINA -> { c.rect(5, 2, 9, 10, 0x7A5030); c.rect(5, 10, 13, 13, 0x7A5030); c.rect(4, 13, 13, 14, 0x3A2A1A); }
            case ORB_MANA -> { for (int y = 2; y < 14; y++) { int w = y < 8 ? (y - 2) / 2 : 4 - Math.max(0, y - 11); c.rect(8 - w, y, 8 + w, y, 0x3070E0); } c.set(6, 9, 0xA0C0FF); }
            case SKULL -> { c.disc(8, 7, 5, 0xE8E8E0); c.rect(5, 11, 11, 13, 0xE8E8E0); c.rect(5, 6, 6, 8, 0x101010); c.rect(10, 6, 11, 8, 0x101010); c.set(8, 10, 0x101010); }
            case COMPASS -> { c.disc(8, 8, 7, 0xD8C8A0); for (int y = 2; y < 8; y++) c.rect(8 - (y - 2) / 2, y, 8 + (y - 2) / 2, y, 0xC02020); for (int y = 8; y < 14; y++) c.rect(8 - (13 - y) / 2, y, 8 + (13 - y) / 2, y, 0xF0F0F0); }
            case COIN -> { c.disc(8, 8, 5.5f, GOLD); c.ring(8, 8, 5.5f, 1, 0xB08820); }
            default -> { }
        }
    }
}
