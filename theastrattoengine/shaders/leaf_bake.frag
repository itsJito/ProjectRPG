#include "include/head.glsl"
// 2.5: bakes one card of leaves (layer uLayer of the leaf array) from the procedure, once, when the renderer starts:
// r the cut (how much of a leaf), g how far along its leaf's midrib the point lies.
#include "include/leaf.glsl"
in vec2 vUV;
layout(location=0) out vec4 oLeaf;
uniform int uLayer;
void main() {
    // four samples a texel: the cut's edge is antialiased into the bake, so the alpha test between texels is smooth
    float a = 0.0, sh = 0.0, wsum = 0.0;
    for (int i = 0; i < 4; i++) {
        vec2 o = (vec2(i & 1, i >> 1) - 0.5) * 0.5 / 64.0;
        float k = leafCardAlphaProc(vec2(float(uLayer) * 2.0, 0.0) + clamp(vUV + o, 0.0, 0.9999));
        a += k; sh += leafShade * max(k, 0.05); wsum += max(k, 0.05);
    }
    oLeaf = vec4(a * 0.25, sh / wsum, 0.0, 1.0);
}
