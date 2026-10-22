#include "include/head.glsl"
in vec2 vUV;
layout(location=0) out vec4 oColor;
uniform sampler2D uColor, uHistory, uDepth, uNormal;
uniform mat4 uInvPV, uPrevPV;
uniform vec2 uScreen;      // the render resolution (the colour, depth and normal targets)
uniform vec2 uOut;         // 2.5: the output resolution (the history): larger when rendering below it (upscaling)
uniform vec2 uJitter;      // 2.5: this frame's sub-pixel jitter, in render texels
uniform float uBlend;
vec3 tm(vec3 c) { return c / (1.0 + max(c.r, max(c.g, c.b))); }
vec3 itm(vec3 c) { return c / max(1e-4, 1.0 - max(c.r, max(c.g, c.b))); }
void main() {
    // 2.5, temporal upscaling: this output pixel's nearest render sample, and how close its jittered position falls to
    // the pixel's centre. A sample right on the centre is trusted, one at the edge of its texel barely; over the eight
    // jitter positions every output pixel is hit squarely, so the history converges to the full-resolution image.
    bool up = uOut.x > uScreen.x + 0.5;
    vec2 rp = vUV * uScreen - 0.5 - uJitter;
    ivec2 ri = up ? ivec2(clamp(floor(rp + 0.5), vec2(0.0), uScreen - 1.0)) : ivec2(gl_FragCoord.xy);
    vec2 off = (rp - vec2(ri)) * (uOut / uScreen);        // in output pixels
    float near = exp(-1.6 * dot(off, off));
    vec3 cur = tm(texelFetch(uColor, ri, 0).rgb);
    float d = texelFetch(uDepth, ri, 0).r;
    // the viewmodel moves with the camera: it is never reprojected
    // (its depth band first: the normal buffer is read only where it could be the hands)
    bool hands = d < 0.05 && int(texelFetch(uNormal, ri, 0).a * 3.0 + 0.5) == 1;
    vec2 puv = vUV;
    if (!hands) {
        vec4 w = uInvPV * vec4(vUV * 2.0 - 1.0, d * 2.0 - 1.0, 1.0);
        vec4 pc = uPrevPV * vec4(w.xyz / w.w, 1.0);
        puv = pc.xy / pc.w * 0.5 + 0.5;
        if (pc.w <= 0.0) puv = vec2(-1.0);
    }
    vec3 mn = cur, mx = cur;
    for (int y = -1; y <= 1; y++) for (int x = -1; x <= 1; x++) {
        if (x == 0 && y == 0) continue;
        vec3 s = tm(texelFetch(uColor, clamp(ri + ivec2(x, y), ivec2(0), ivec2(uScreen) - 1), 0).rgb);
        mn = min(mn, s); mx = max(mx, s);
    }
    if (puv.x < 0.0 || puv.y < 0.0 || puv.x > 1.0 || puv.y > 1.0) {
        // nothing to remember: the nearest sample, smoothed between neighbours when upscaling
        oColor = vec4(itm(up ? tm(texture(uColor, vUV).rgb) : cur), 1.0);
        return;
    }
    vec3 hist = clamp(tm(texture(uHistory, puv).rgb), mn, mx);
    float speed = length((puv - vUV) * uOut);
    float k = mix(uBlend, 0.35, clamp(speed * 0.05, 0.0, 1.0));
    // upscaling: each frame contributes by how squarely its sample hit this pixel
    if (up) k = clamp(k * (0.25 + 1.75 * near), 0.02, 0.6);
    oColor = vec4(itm(mix(hist, cur, k)), 1.0);
}
