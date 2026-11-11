#include "include/head.glsl"
// (0.3.8, gamefixes: rain did not look like rain) The falling rain, drawn over the lit scene as a screen pass: several
// shells of streaks round the eye, from an arm's length out to the middle distance. Each shell is a sheet of thin, long,
// fast streaks - a raindrop smeared by its own fall over a frame - leaning with the wind, each streak a column of drops
// one after another so the sheet never thins into separate dashes. A streak is drawn only where the open sky is above
// it (uRoof holds the height of the highest solid thing in each column of the world), so under a roof or an awning it
// stops, and looking out through a door or a window the rain is outside. Nearer shells are sparse and bold, farther
// ones dense and faint, and past them all the fog's own weather veil carries on. Thin streaks keep their weight as they
// shrink below a pixel by fading rather than flickering.
in vec2 vUV;
layout(location=0) out vec4 oColor;
uniform sampler2D uDepth, uRoof;
uniform mat4 uInvPV;
uniform vec3 uCam, uAmbient, uFogCol, uSunCol;
uniform vec2 uWind, uWorld;
uniform float uRain, uTime;

float h11(float n) { return fract(sin(n * 127.1) * 43758.5453); }
float h21(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }

void main() {
    if (uRain <= 0.0) discard;
    float zc = texture(uDepth, vUV).r;
    vec4 c = uInvPV * vec4(vUV * 2.0 - 1.0, zc * 2.0 - 1.0, 1.0);
    vec3 P = c.xyz / c.w;
    vec3 ray = P - uCam;
    float scene = zc >= 1.0 ? 1e6 : length(ray);
    vec3 dir = ray / max(length(ray), 1e-4);
    float horiz = length(dir.xz);
    if (horiz < 0.08) discard;                       // straight up or down: the streaks are end-on and unseen
    float ang = atan(dir.z, dir.x);
    float pix = length(fwidth(dir));               // the angle one pixel spans (taken outside the loop: no seam, no branch)
    // the streaks' colour: the sky's light on water, a little of the sun's, faint against a dark night
    vec3 lit = uAmbient * 1.25 + uFogCol * 0.35 + uSunCol * 0.08;
    float fall = 8.5;                                // m/s: a raindrop at its terminal speed
    vec2 tangent = vec2(-sin(ang), cos(ang));        // the shell's sideways, in the world
    float slant = dot(uWind, tangent) / fall;        // how far a drop drifts sideways for each metre it falls
    float sum = 0.0;
    const int SHELLS = 6;
    for (int k = 0; k < SHELLS; k++) {
        float d = 1.3 * pow(1.72, float(k));         // 1.3, 2.2, 3.8, 6.6, 11.4, 19.6 m along the view
        if (d > scene) break;
        vec3 Q = uCam + dir * d;
        // only under the open sky
        float roof = texture(uRoof, Q.xz / uWorld).r;
        if (Q.y < roof - 0.05) continue;
        // the shell's surface, unrolled: around (metres of arc) and up (metres), the arc leaning with the wind so each
        // drop keeps to its own line as it falls
        float around = ang * d + Q.y * slant;
        float up = Q.y + uTime * fall * (0.92 + 0.05 * float(k));
        float cellW = 0.032 + 0.02 * d;             // nearer shells: fewer, bolder streaks; farther: finer and more
        float cellH = 1.9 + 0.08 * d;
        float col = floor(around / cellW + float(k) * 17.0);
        float fx = fract(around / cellW + float(k) * 17.0);
        float seedC = col * 0.6180339 + float(k) * 3.71;
        // a column holds a run of drops one behind another, each its own length and place
        float offs = h11(seedC) * cellH;
        float row = floor((up + offs) / cellH);
        float fy = fract((up + offs) / cellH);
        float seed = h21(vec2(col, row + float(k) * 41.0));
        if (seed > uRain * 0.92 + 0.06) continue;    // light rain: most cells empty; a downpour: nearly all full
        float x0 = 0.2 + 0.6 * h11(seed * 91.7);
        float len = (0.30 + 0.25 * h11(seed * 13.3)) / cellH;
        float y0 = h11(seed * 47.1) * (1.0 - len);
        // across: a hair's width in the world, never thinner than a pixel; energy kept as it narrows
        float dx = abs(fx - x0) * cellW;
        float px = max(pix * d, 1e-4);
        float width = 0.0018 + 0.0006 * float(k);
        float w = max(width, px * 0.6);
        float across = (1.0 - smoothstep(w * 0.4, w, dx)) * (width / w);
        // along: brightest at its head (the drop itself), drawn out to nothing behind
        float t = (fy - y0) / len;
        if (t < 0.0 || t > 1.0) continue;
        float along = smoothstep(0.0, 0.15, t) * (1.0 - smoothstep(0.65, 1.0, t)) * (0.6 + 0.4 * t);
        // the nearest shell fades in from the eye, the farthest out into the veil
        float shell = smoothstep(0.8, 1.6, d) * (1.0 - smoothstep(12.0, 22.0, d));
        sum += across * along * shell * (0.75 - 0.05 * float(k));
    }
    sum *= mix(0.55, 1.0, uRain) * (1.0 - smoothstep(0.55, 0.92, abs(dir.y)));
    sum = min(sum, 0.85);
    if (sum < 0.003) discard;
    // premultiplied: a little light of its own, and a little of what is behind dimmed - water, not paint
    oColor = vec4(lit * sum * 1.3, sum * 0.45);
}
