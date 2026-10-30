package rpg.ui;

import astratto.bridge.AstrattoNative;
import astratto.bridge.NativeRenderer;
import astratto.bridge.RenderCommands;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import org.lwjgl.stb.STBTTAlignedQuad;
import org.lwjgl.stb.STBTTBakedChar;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import static org.lwjgl.stb.STBTruetype.*;

/**
 * The 2D layer drawn by the engine's renderer: coloured and textured quads in pixels, batched per texture, text from a
 * TrueType font baked once into a texture, and a sheet of pixel-art icons painted from code. Also the mouse as the UI
 * sees it (position, clicks, whether the UI took the click).
 */
public final class Ui2D {
    private static final int FW = 512, FH = 512, FIRST = 32, COUNT = 96;
    private RenderCommands cmd;
    private float[] v = new float[36 * 512];
    private int n, tex;
    public int icons;
    public final int parchment;

    /**
     * A typeface baked once (gamefixes: the provided fonts, each where it belongs). Friz Quadrata is the interface's
     * face - quest text, the level-up banners, names, most of what is on the screen; Morpheus the lore's - the quest
     * log's headers, books, scrolls and letters; Arial Narrow the chat, information, tooltips and small numbers; Skurri
     * the big numbers that pop over a body struck in combat.
     */
    public static final class Face {
        final float bake;
        final int texture;
        final STBTTBakedChar.Buffer chars;
        final float[] advance = new float[COUNT];
        final float baseline;
        Face(float bake, int texture, STBTTBakedChar.Buffer chars, float baseline) { this.bake = bake; this.texture = texture; this.chars = chars; this.baseline = baseline; }
    }
    public final Face body, heading, chat, combat;
    private Face face;
    /** Switches the face text is set in; returns the one it replaces (put it back after). */
    public Face use(Face f) { Face was = face; face = f == null ? body : f; return was; }
    /** Text in another face, the current one restored after. */
    public float text(Face f, String s, float x, float y, float size, int argb) { Face was = use(f); try { return text(s, x, y, size, argb); } finally { use(was); } }
    public float width(Face f, String s, float size) { Face was = use(f); try { return width(s, size); } finally { use(was); } }
    public void centered(Face f, String s, float cx, float y, float size, int argb) { Face was = use(f); try { centered(s, cx, y, size, argb); } finally { use(was); } }
    public void flatCentered(Face f, String s, float cx, float y, float size, int argb) { Face was = use(f); try { flatCentered(s, cx, y, size, argb); } finally { use(was); } }
    public float mx, my;
    public boolean click, rclick, down, released, consumed;
    /** Everything drawn is faded by this (a fragment's fade in and out). */
    public float alpha = 1;
    /** The provided icon art (assets to add/Icons), by name. */
    public Atlas atlas;
    public double scroll;

    public final int engineLogo;
    private static int image(NativeRenderer renderer, String path) {
        try (var stream = Ui2D.class.getResourceAsStream(path)) {
            if (stream == null) throw new IllegalStateException("Missing branding asset " + path);
            var image = javax.imageio.ImageIO.read(stream); java.nio.ByteBuffer rgba = org.lwjgl.system.MemoryUtil.memAlloc(image.getWidth()*image.getHeight()*4);
            try { for(int y=0;y<image.getHeight();y++)for(int x=0;x<image.getWidth();x++) { int c=image.getRGB(x,y);rgba.put((byte)(c>>16)).put((byte)(c>>8)).put((byte)c).put((byte)(c>>>24)); } rgba.flip();return renderer.texture(astratto.bridge.AstrattoNative.TEX_2D,image.getWidth(),image.getHeight(),1,0,rgba); }
            finally { org.lwjgl.system.MemoryUtil.memFree(rgba); }
        } catch (java.io.IOException e) { throw new IllegalStateException("Could not load " + path,e); }
    }

    public Ui2D(NativeRenderer r) {
        engineLogo = image(r, "/astratto/astrattoenginelogo.png");
        Face friz = bake(r, resource("FrizQuadrataTT.ttf"), 22f);
        body = friz != null ? friz : bake(r, fallbackFont(), 20f);
        heading = orElse(bake(r, resource("MORPHEUS.TTF"), 34f), body);
        chat = orElse(bake(r, resource("arialnarrow.ttf"), 22f), body);
        combat = orElse(bake(r, resource("skurri.ttf"), 30f), body);
        face = body;
        icons = Icons.texture(r);
        atlas = new Atlas(r);
        ByteBuffer paper = MemoryUtil.memAlloc(128 * 128 * 4);
        try {
            for (int y = 0; y < 128; y++) for (int x = 0; x < 128; x++) {
                int hash = x * 374761393 + y * 668265263;
                hash = (hash ^ (hash >>> 13)) * 1274126177; hash ^= hash >>> 16;
                int grain = (hash >>> 27) - 16;
                float fiber = (float) (Math.sin(x * 0.13 + Math.sin(y * 0.03)) + Math.sin(y * 0.19 + Math.cos(x * 0.06)));
                int shade = 47 + grain / 5 + (int) fiber;
                paper.put((byte) (shade + 13)).put((byte) (shade + 5)).put((byte) (shade - 6)).put((byte) 255);
            }
            paper.flip(); parchment = r.texture(AstrattoNative.TEX_2D, 128, 128, 1, AstrattoNative.TEX_REPEAT, paper);
        } finally { MemoryUtil.memFree(paper); }
    }

    private static Face orElse(Face f, Face fallback) { return f != null ? f : fallback; }
    private static byte[] resource(String name) {
        try (InputStream in = Ui2D.class.getResourceAsStream("/ui/fonts/" + name)) { return in == null ? null : in.readAllBytes(); } catch (Exception e) { return null; }
    }
    private static byte[] fallbackFont() {
        byte[] data = null;
        try (InputStream in = Ui2D.class.getResourceAsStream("/astratto/resources/fonts/DejaVuSans.ttf")) { if (in != null) data = in.readAllBytes(); } catch (Exception ignored) { }
        for (String p : new String[]{"C:/Windows/Fonts/verdana.ttf", "C:/Windows/Fonts/arial.ttf", "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"}) {
            if (data != null) break;
            try { data = Files.readAllBytes(Path.of(p)); } catch (Exception ignored) { }
        }
        if (data == null) throw new IllegalStateException("no font found");
        return data;
    }

    /** Bakes a face's printable ASCII into its own texture at a pixel height (null: the file could not be read as a font). */
    private static Face bake(NativeRenderer r, byte[] data, float px) {
        if (data == null) return null;
        ByteBuffer ttf = MemoryUtil.memAlloc(data.length).put(data).flip();
        ByteBuffer bitmap = MemoryUtil.memCalloc(FW * FH);
        STBTTBakedChar.Buffer chars = STBTTBakedChar.malloc(COUNT);
        try {
            int rows = stbtt_BakeFontBitmap(ttf, px, bitmap, FW, FH, FIRST, chars);
            if (rows == 0) { chars.free(); return null; }
            // the baseline: where the font's own ascent puts it at this size
            float baseline = px * 0.8f;
            try (MemoryStack st = MemoryStack.stackPush()) {
                var info = org.lwjgl.stb.STBTTFontinfo.malloc(st);
                if (stbtt_InitFont(info, ttf)) {
                    int[] asc = {0}, desc = {0}, gap = {0};
                    stbtt_GetFontVMetrics(info, asc, desc, gap);
                    baseline = asc[0] * stbtt_ScaleForPixelHeight(info, px);
                }
            }
            ByteBuffer rgba = MemoryUtil.memAlloc(FW * FH * 4);
            for (int i = 0; i < FW * FH; i++) { byte c = bitmap.get(i); rgba.put(c).put((byte) 255).put((byte) 255).put((byte) 255); }
            rgba.flip();
            Face f = new Face(px, r.texture(AstrattoNative.TEX_2D, FW, FH, 1, 0, rgba), chars, baseline);
            for (int i = 0; i < COUNT; i++) f.advance[i] = chars.get(i).xadvance();
            MemoryUtil.memFree(rgba);
            return f;
        } finally { MemoryUtil.memFree(bitmap); MemoryUtil.memFree(ttf); }
    }

    public void begin(RenderCommands c) { cmd = c; n = 0; tex = -1; }
    public void end() { flush(); }

    public void flush() {
        if (n > 0 && cmd != null) cmd.quads(tex, v, n);
        n = 0;
    }

    private void vert(int k, float x, float y, float u, float vv, int argb, float mode) {
        int o = k * 9;
        v[o] = x; v[o + 1] = y; v[o + 2] = u; v[o + 3] = vv;
        v[o + 4] = ((argb >> 16) & 255) / 255f; v[o + 5] = ((argb >> 8) & 255) / 255f; v[o + 6] = (argb & 255) / 255f; v[o + 7] = ((argb >>> 24) & 255) / 255f * alpha; v[o + 8] = mode;
    }

    private int slot(int texture) {
        if (texture != tex) { flush(); tex = texture; }
        if ((n + 1) * 36 > v.length) { flush(); }
        return n++;
    }

    /** A quad from four corners (clockwise from top-left), textured or not. */
    public void quad(int texture, float[] xy, float[] uv, int argb, float mode) {
        int q = slot(texture);
        for (int i = 0; i < 4; i++) vert(q * 4 + i, xy[i * 2], xy[i * 2 + 1], uv[i * 2], uv[i * 2 + 1], argb, mode);
    }

    public void rect(float x, float y, float w, float h, int argb) {
        quad(0, new float[]{x, y, x + w, y, x + w, y + h, x, y + h}, new float[]{0, 0, 1, 0, 1, 1, 0, 1}, argb, 0);
    }

    public void rectTex(int texture, float x, float y, float w, float h, float u0, float v0, float u1, float v1, int argb) {
        quad(texture, new float[]{x, y, x + w, y, x + w, y + h, x, y + h}, new float[]{u0, v0, u1, v0, u1, v1, u0, v1}, argb, 0);
    }

    public void border(float x, float y, float w, float h, float t, int argb) {
        rect(x, y, w, t, argb); rect(x, y + h - t, w, t, argb); rect(x, y, t, h, argb); rect(x + w - t, y, t, h, argb);
    }

    public void line(float x0, float y0, float x1, float y1, float t, int argb) {
        float dx = x1 - x0, dy = y1 - y0, l = (float) Math.hypot(dx, dy);
        if (l < 1e-3f) return;
        float nx = -dy / l * t / 2, ny = dx / l * t / 2;
        quad(0, new float[]{x0 + nx, y0 + ny, x1 + nx, y1 + ny, x1 - nx, y1 - ny, x0 - nx, y0 - ny}, new float[]{0, 0, 1, 0, 1, 1, 0, 1}, argb, 0);
    }

    /** A filled disc (a fan of slim quads). */
    public void disc(float cx, float cy, float r, int argb) {
        int seg = 28;
        for (int i = 0; i < seg; i++) {
            double a0 = i * Math.PI * 2 / seg, a1 = (i + 1) * Math.PI * 2 / seg;
            quad(0, new float[]{cx, cy, cx, cy, cx + (float) Math.cos(a0) * r, cy + (float) Math.sin(a0) * r, cx + (float) Math.cos(a1) * r, cy + (float) Math.sin(a1) * r},
                    new float[]{0, 0, 0, 0, 0, 0, 0, 0}, argb, 0);
        }
    }

    public void ring(float cx, float cy, float r, float t, int argb) {
        int seg = 40;
        for (int i = 0; i < seg; i++) {
            double a0 = i * Math.PI * 2 / seg, a1 = (i + 1) * Math.PI * 2 / seg;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            quad(0, new float[]{cx + c0 * r, cy + s0 * r, cx + c1 * r, cy + s1 * r, cx + c1 * (r - t), cy + s1 * (r - t), cx + c0 * (r - t), cy + s0 * (r - t)}, new float[]{0, 0, 0, 0, 0, 0, 0, 0}, argb, 0);
        }
    }

    /** A slice of a ring between two radii and two angles (radians, 0 along +x, clockwise on the screen). */
    public void sector(float cx, float cy, float r0, float r1, double a0, double a1, int argb) {
        int seg = Math.max(2, (int) Math.ceil(Math.abs(a1 - a0) / (Math.PI / 24)));
        for (int i = 0; i < seg; i++) {
            double b0 = a0 + (a1 - a0) * i / seg, b1 = a0 + (a1 - a0) * (i + 1) / seg;
            float c0 = (float) Math.cos(b0), s0 = (float) Math.sin(b0), c1 = (float) Math.cos(b1), s1 = (float) Math.sin(b1);
            quad(0, new float[]{cx + c0 * r1, cy + s0 * r1, cx + c1 * r1, cy + s1 * r1, cx + c1 * r0, cy + s1 * r0, cx + c0 * r0, cy + s0 * r0}, new float[]{0, 0, 0, 0, 0, 0, 0, 0}, argb, 0);
        }
    }


    // ------------------------------------------------------------------ text
    public float width(String s, float size) {
        Face f = face;
        float sc = size / f.bake, w = 0;
        for (int i = 0; i < s.length(); i++) { int c = s.charAt(i) - FIRST; if (c < 0 || c >= COUNT) c = '?' - FIRST; w += f.advance[c]; }
        return w * sc;
    }

    public float text(String s, float x, float y, float size, int argb) {
        if (s == null) return 0;
        Face f = face;
        float sc = size / f.bake;
        try (MemoryStack st = MemoryStack.stackPush()) {
            STBTTAlignedQuad q = STBTTAlignedQuad.malloc(st);
            float[] px = {0}, py = {0};
            for (int i = 0; i < s.length(); i++) {
                int c = s.charAt(i);
                if (c < FIRST || c >= FIRST + COUNT) c = '?';
                stbtt_GetBakedQuad(f.chars, FW, FH, c - FIRST, px, py, q, true);
                float x0 = x + q.x0() * sc, y0 = y + (q.y0() + f.baseline) * sc, x1 = x + q.x1() * sc, y1 = y + (q.y1() + f.baseline) * sc;
                if (x1 > x0) quad(f.texture, new float[]{x0, y0, x1, y0, x1, y1, x0, y1}, new float[]{q.s0(), q.t0(), q.s1(), q.t0(), q.s1(), q.t1(), q.s0(), q.t1()}, argb, 1);
            }
            return px[0] * sc;
        }
    }

    /** OSRS-style text: a one-pixel black shadow under it. */
    public float shadow(String s, float x, float y, float size, int argb) {
        text(s, x + 1, y + 1, size, 0xFF000000 | 0);
        return text(s, x, y, size, argb);
    }

    public void centered(String s, float cx, float y, float size, int argb) { shadow(s, cx - width(s, size) / 2, y, size, argb); }

    /** Centred text whose shadow fades with it (a plain shadow is always solid black, which shows before its text does). */
    public void centeredFade(String s, float cx, float y, float size, int argb) {
        float x = cx - width(s, size) / 2;
        int a = argb >>> 24;
        if (a == 0) return;
        text(s, x + 1, y + 1, size, (a * 7 / 8) << 24);
        text(s, x, y, size, argb);
    }

    /** Word-wrapped text; returns the height used. */
    public float wrap(String s, float x, float y, float maxW, float size, int argb) {
        float lh = size * 1.3f, cy = y;
        for (String para : s.split("\n")) {
            StringBuilder line = new StringBuilder();
            for (String word : para.split(" ")) {
                String trial = line.length() == 0 ? word : line + " " + word;
                if (width(trial, size) > maxW && line.length() > 0) { shadow(line.toString(), x, cy, size, argb); cy += lh; line = new StringBuilder(word); }
                else line = new StringBuilder(trial);
            }
            shadow(line.toString(), x, cy, size, argb);
            cy += lh;
        }
        return cy - y;
    }

    public void icon(int index, float x, float y, float size, int argb) {
        int per = Icons.PER_ROW;
        float u0 = (index % per) / (float) per, v0 = (index / per) / (float) per, d = 1f / per;
        rectTex(icons, x, y, size, size, u0, v0, u0 + d, v0 + d, argb);
    }

    /** One of the provided icons, fitted (aspect kept) into a size x size box. */
    public void img(String name, float x, float y, float size, int argb) {
        Atlas.Sprite sp = atlas.get(name);
        if (sp == null) return;
        float sc = size / Math.max(sp.w(), sp.h()), dw = sp.w() * sc, dh = sp.h() * sc;
        rectTex(atlas.texture, x + (size - dw) / 2, y + (size - dh) / 2, dw, dh, sp.u0(), sp.v0(), sp.u1(), sp.v1(), argb);
    }

    /** Plain text with no shadow, centred on a point (the flat style). */
    public void flatCentered(String s, float cx, float y, float size, int argb) { text(s, cx - width(s, size) / 2, y, size, argb); }

    // ------------------------------------------------------------------ the mouse
    /** This frame's click is the second of a double click. */
    public boolean doubleClick;

    public boolean hover(float x, float y, float w, float h) { return mx >= x && my >= y && mx < x + w && my < y + h; }

    /** Whether a left click landed here this frame (and takes it). */
    public boolean clicked(float x, float y, float w, float h) {
        if (click && !consumed && hover(x, y, w, h)) { consumed = true; return true; }
        return false;
    }
    public boolean rclicked(float x, float y, float w, float h) {
        if (rclick && !consumed && hover(x, y, w, h)) { consumed = true; return true; }
        return false;
    }

    /** A stone-and-gold OSRS button. */
    public boolean button(String label, float x, float y, float w, float h, boolean enabled) {
        boolean hv = enabled && hover(x, y, w, h);
        rect(x, y, w, h, enabled ? (hv ? 0xFF6A5A44 : 0xFF4A3F33) : 0xFF33302B);
        border(x, y, w, h, 1, 0xFF1E1A15);
        rect(x + 1, y + 1, w - 2, 1, 0x40FFFFFF);
        float ts = Math.min(13, h - 6);
        centered(label, x + w / 2, y + (h - ts * 1.1f) / 2, ts, enabled ? 0xFFFF981F : 0xFF7A7060);
        return enabled && clicked(x, y, w, h);
    }

    public void free() { for (Face f : new java.util.LinkedHashSet<>(java.util.List.of(body, heading, chat, combat))) f.chars.free(); }
}
