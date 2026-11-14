#include "include/head.glsl"
// (2.13) Precipitation's look (see precip.vert): a raindrop's streak is bright along its spine and soft at its edges,
// lit by the sky and a little by the sun; a snowflake is a soft white disc with a brighter heart; a hailstone a small
// hard bead; dust a soft brown puff; a splash a thin ring with a few beads thrown out of it. Each is hidden where the
// scene stands in front of it (the scene's depth), and softened where it nears a surface.
in vec2 vQ;
in float vFade;
in float vSeed;
in float vDepth;
flat in int vKind;
uniform sampler2D uDepth;
uniform vec2 uScreen;
uniform float uNear, uFar;
uniform vec3 uLight;            // the light falling on it: the sky's, the sun's share, (already toned for night)
uniform vec4 uColor;            // its own tint and the layer's opacity
layout(location=0) out vec4 oColor;
float lin(float d) { return 2.0 * uNear * uFar / (uFar + uNear - (d * 2.0 - 1.0) * (uFar - uNear)); }
void main() {
    float scene = lin(texture(uDepth, gl_FragCoord.xy / uScreen).r);
    float soft = clamp((scene - vDepth) * 3.0, 0.0, 1.0);
    if (soft <= 0.0 || vFade <= 0.0) discard;
    float a;
    vec3 col = uColor.rgb * uLight;
    float r = length(vQ);
    if (vKind == 0) {
        // the streak: its spine bright, its edges soft, its head and tail tapered
        float across = 1.0 - smoothstep(0.15, 1.0, abs(vQ.x));
        float along = smoothstep(-1.0, -0.35, vQ.y) * (1.0 - smoothstep(0.55, 1.0, vQ.y));
        a = across * along;
        col *= 1.0 + 0.35 * (1.0 - abs(vQ.x));
    } else if (vKind == 1) {
        if (r > 1.0) discard;
        a = (1.0 - smoothstep(0.25, 1.0, r));
        col *= 1.0 + 0.4 * (1.0 - r);
    } else if (vKind == 2) {
        if (r > 1.0) discard;
        a = 1.0 - smoothstep(0.55, 1.0, r);
        col *= 1.2 - 0.4 * r;
    } else if (vKind == 3) {
        // wind-borne sand or snow: a soft wisp drawn out along the wind
        float across = 1.0 - smoothstep(0.0, 1.0, abs(vQ.x));
        float along = 1.0 - smoothstep(0.2, 1.0, abs(vQ.y));
        a = across * along * (0.65 + 0.35 * vSeed);
    } else {
        // the splash: a thin ring and a few beads thrown out of it
        float ring = 1.0 - smoothstep(0.0, 0.18, abs(r - 0.72));
        float ang = atan(vQ.y, vQ.x);
        float beads = step(0.93, fract(ang * 1.4 + vSeed * 9.0)) * (1.0 - smoothstep(0.85, 1.0, r)) * step(0.55, r);
        a = max(ring * 0.8, beads);
        if (r > 1.0) discard;
    }
    a *= uColor.a * vFade * soft;
    if (a < 0.003) discard;
    oColor = vec4(col * a, a);
}
