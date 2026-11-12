#include "include/head.glsl"
// (0.4.0) the lens's autofocus: one texel holding the distance the eye is focused at, eased each frame toward what is
// under the middle of the screen - the nearest solid thing in a small cross of taps, weighted to the centre - the way
// the exposure eases toward the scene's brightness. Focus racks smoothly from a face to the hills instead of jumping
// every frame (and the blur never flickers as a tap slips off an edge).
layout(location=0) out float oFocus;
uniform sampler2D uDepth, uPrev;
uniform float uDt, uNear, uFar, uReset, uRate;
float lin(float d) { return 2.0 * uNear * uFar / (uFar + uNear - (d * 2.0 - 1.0) * (uFar - uNear)); }
void main() {
    float near = 1e9, sum = 0.0, w = 0.0;
    for (int i = 0; i < 9; i++) {
        vec2 o = vec2(float(i % 3 - 1), float(i / 3 - 1)) * 0.035;
        float d = texture(uDepth, vec2(0.5) + o).r;
        if (d >= 1.0) continue;
        float z = lin(d), k = i == 4 ? 3.0 : 1.0;
        near = min(near, z); sum += z * k; w += k;
    }
    // nothing but sky in the middle: focus far; otherwise mostly what is in the centre, pulled toward the nearest thing
    float target = w > 0.0 ? mix(sum / w, near, 0.35) : 300.0;
    float prev = texelFetch(uPrev, ivec2(0), 0).r;
    if (uReset > 0.5 || !(prev > 0.0)) prev = target;
    // focusing in (toward something near) is quicker than letting focus drift back out
    float rate = target < prev ? uRate * 1.6 : uRate;
    oFocus = mix(prev, target, 1.0 - exp(-uDt * rate));
}
