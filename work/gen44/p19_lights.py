import re
p = 'rpggame/src/main/java/rpg/render/Gfx.java'
s = open(p, encoding='utf-8').read()
n = s.count('cmd.pointLight(')
s = s.replace('cmd.pointLight(', 'pointLight(')
s = s.replace("""    public NativeRenderer.Stats frame(int w, int h) {
        lastH = h;
        last = r.frame(cmd, w, h);""", """    public NativeRenderer.Stats frame(int w, int h) {
        lastH = h;
        flushLights();
        last = r.frame(cmd, w, h);""")
s = s.replace("""    private float camX, camY, camZ;""", """    private float camX, camY, camZ;
    /**
     * (0.4.4, gamefixes: interiors black, the light budget) every point light of the frame, held until it ends: the renderer
     * takes only the first so many it is given (its quality's budget), and ShadowVeil's street lamps alone filled it
     * before a room's candles were reached - so a lit room near the eye went dark while lamps across the town burned.
     * They go to it nearest the eye first, a shadow-casting light ahead of a plain one at the same distance.
     */
    private float[] lightBuf = new float[12 * 64];
    private int lightCount;
    private Integer[] lightOrder = new Integer[64];
    private void pointLight(float x, float y, float z, float radius, float r, float g, float b, int flags) {
        if (!Float.isFinite(x + y + z + radius + r + g + b) || radius <= 0) return;
        if ((lightCount + 1) * 12 > lightBuf.length) lightBuf = java.util.Arrays.copyOf(lightBuf, lightBuf.length * 2);
        int o = lightCount++ * 12;
        lightBuf[o] = x; lightBuf[o + 1] = y; lightBuf[o + 2] = z; lightBuf[o + 3] = radius; lightBuf[o + 4] = r; lightBuf[o + 5] = g; lightBuf[o + 6] = b; lightBuf[o + 7] = flags;
        // its rank: how far its reach falls short of the eye (a light whose pool the eye stands in ranks first)
        float d = (float) Math.sqrt((x - camX) * (x - camX) + (y - camY) * (y - camY) + (z - camZ) * (z - camZ));
        lightBuf[o + 8] = Math.max(0, d - radius * 0.35f) - ((flags & AstrattoNative.LIGHT_SHADOW) != 0 ? 0.5f : 0);
    }
    private void flushLights() {
        if (lightCount == 0) return;
        if (lightOrder.length < lightCount) lightOrder = new Integer[lightCount * 2];
        for (int i = 0; i < lightCount; i++) lightOrder[i] = i;
        final float[] buf = lightBuf;
        java.util.Arrays.sort(lightOrder, 0, lightCount, (a, b) -> Float.compare(buf[a * 12 + 8], buf[b * 12 + 8]));
        for (int k = 0; k < lightCount; k++) { int o = lightOrder[k] * 12; cmd.pointLight(buf[o], buf[o + 1], buf[o + 2], buf[o + 3], buf[o + 4], buf[o + 5], buf[o + 6], (int) buf[o + 7]); }
        lightCount = 0;
    }""")
open(p, 'w', encoding='utf-8').write(s)
print('replaced', n)
