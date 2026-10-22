// The leaf cards' leaves, as they are cut (2.4). Since 2.5 this runs once, when the renderer starts, baked into a
// 64 x 64 texture array of sixteen cards (leaf_bake.frag); the world's shaders read the bake (world_tex.glsl).
// 2.4: leaf cards. A card's texture coordinates are 0..1 across it plus twice its cluster's seed; the leaves on it
// are cut here: eight to eleven pointed ovals fanned from two twigs across the card, each turned and
// sized by the seed. Returns how much of a leaf there is at the point (0 none, 1 within one), and in leafShade how far
// along its leaf's midrib the point lies.
float leafShade;
float leafHash(float n) { return fract(sin(n * 12.9898) * 43758.5453); }
float leafCardAlphaProc(vec2 st) {
    float seed = floor(st.x * 0.5 + 0.0001);
    vec2 uv = fract(st);
    float a = 0.0;
    leafShade = 0.5;
    int n = 8 + int(leafHash(seed + 0.7) * 4.0);
    for (int i = 0; i < 11; i++) {
        if (i >= n) break;
        float fi = float(i);
        float r1 = leafHash(seed * 7.13 + fi * 1.91), r2 = leafHash(seed * 3.71 + fi * 5.37), r3 = leafHash(seed * 1.37 + fi * 9.13);
        // two twigs on the card, the leaves fanned out from each: angle across the fan, a little reach, a little size
        float twig = mod(fi, 2.0);
        float k0 = floor(fi * 0.5) / max(1.0, floor(float(n) * 0.5));
        float ang = mix(-1.35, 1.35, k0 + 0.1) * (twig > 0.5 ? -1.0 : 1.0) + (r1 - 0.5) * 0.5;
        vec2 base = vec2(twig > 0.5 ? 0.62 : 0.38, 0.22 + r3 * 0.30) + vec2((r2 - 0.5) * 0.12, 0.0);
        vec2 dirL = vec2(sin(ang), cos(ang));
        float len = 0.20 + r1 * 0.11, wid = len * (0.34 + r2 * 0.12);
        vec2 d = uv - (base + dirL * len * 0.55);
        float along = dot(d, dirL) / (len * 0.5), across = dot(d, vec2(-dirL.y, dirL.x)) / wid;
        // a pointed oval: wide in the middle, narrowing to a tip at both ends
        float shape = 1.0 - (across * across) / max(1e-3, 1.0 - along * along) - along * along * 0.15;
        float k = smoothstep(0.0, 0.18, shape) * step(abs(along), 1.0);
        if (k > a) { a = k; leafShade = along * 0.5 + 0.5; }
    }
    return a;
}
