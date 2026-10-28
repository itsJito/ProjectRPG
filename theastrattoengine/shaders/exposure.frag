#include "include/head.glsl"
layout(location=0) out float oExposure;
uniform sampler2D uLum, uPrev;
uniform float uDt, uMin, uMax, uReset;
void main() {
    float avg = exp(textureLod(uLum, vec2(0.5), 10.0).r);
    // 0.1.9: the eye adapts only part of the way to the dark (it kept a lamp-lit room at night as bright as noon):
    // below the daylight key the scene is brought up to 0.16 * (avg / 0.16)^0.6, so a dim room stays dim, a dark
    // night stays dark, and the lamps' pools of light read against it
    float a = max(avg, 1e-4);
    float want = a < 0.16 ? 0.16 * pow(a / 0.16, 0.6) : 0.16;
    float target = clamp(want / a, uMin, uMax);
    float prev = texelFetch(uPrev, ivec2(0), 0).r;
    if (uReset > 0.5 || !(prev > 0.0)) prev = target;
    // eyes open slowly in the dark and shut quickly in the light
    float rate = target > prev ? 0.7 : 2.4;
    oExposure = mix(prev, target, 1.0 - exp(-uDt * rate));
}
