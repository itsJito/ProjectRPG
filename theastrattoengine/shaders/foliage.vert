#version 450 core
layout(location=0) in vec3 aPos;
layout(location=1) in vec3 aNormal;
layout(location=2) in vec4 aColSway;
layout(location=3) in vec4 iPosScale;
layout(location=4) in vec4 iRot;       // turn, tint, phase, flex
layout(location=5) in vec4 iLight;     // sky light, block light, how much snow settles on it, -
uniform mat4 uPV;
uniform vec4 uWind;                    // x, z in m/s; time; gust
uniform vec3 uCam;
uniform vec2 uFade;
uniform vec4 uPush[8];                 // x, z, radius, y of whatever is wading through
uniform int uPushN;
uniform float uSnow;
uniform vec2 uRainFall;                // 0.4.7: the rain falling now (0..1), how wet the leaves are (0..1)
out vec3 vCol;
out vec3 vN;
out vec2 vLight;
void main() {
    float c = cos(iRot.x), s = sin(iRot.x);
    vec3 lp = aPos * iPosScale.w;
    vec3 p = vec3(lp.x * c + lp.z * s, lp.y, -lp.x * s + lp.z * c);
    vec3 n = vec3(aNormal.x * c + aNormal.z * s, aNormal.y, -aNormal.x * s + aNormal.z * c);
    vec3 root = iPosScale.xyz;
    // at the edge of the drawn distance a plant sinks into the ground rather than appearing out of nothing
    p *= 1.0 - smoothstep(uFade.x, uFade.y, length(root.xz - uCam.xz));
    float sway = aColSway.a * iPosScale.w * iRot.w;
    // the wind: a steady lean, and two waves that cross the field so that it moves in swathes, not in step
    float strength = length(uWind.xy);
    vec2 dir = strength > 1e-3 ? uWind.xy / strength : vec2(1.0, 0.0);
    float ph = iRot.z + dot(root.xz, dir) * 0.35;
    float wave = sin(uWind.z * 1.9 - ph) * 0.6 + sin(uWind.z * 4.3 - ph * 1.7 + root.x) * 0.25 + sin(uWind.z * 8.1 + iRot.z * 5.0 + p.y * 5.0) * 0.15;
    vec2 off = dir * sway * (strength * 0.020 * (1.0 + 0.5 * uWind.w) + wave * (0.012 + strength * 0.010));
    // anything wading through bends what it touches over, away from itself. (1.1: the whole plant leans one way, from
    // its root. 1.2: the pushes are summed and capped, so a body and the trail point it has just left never fold a plant
    // twice.) (1.3, the game's 0.3.4: plants looked stretched when bumped or stood in) the lean used to be a sideways
    // shove of every point by up to 0.42 m - a shear, which drew a low fern or a bush out like taffy and squashed a stem
    // underfoot flat in height only. Now the plant is turned over about its root: a rotation, which keeps every leaf the
    // length it was. The angle grows over the first hand's breadth of the stem (a curve, not a hinge), right under a foot
    // it goes furthest (the stem pressed down to the ground rather than shoved aside), and a stiff plant - a bush, a
    // sapling - gives far less than grass. Only a body down among the stems presses them: one standing a block above (on
    // a step, a wall top) leaves the plants below alone.
    vec2 lean = vec2(0.0);
    float crush = 0.0;
    float pliant = step(0.01, aColSway.a);
    for (int i = 0; i < uPushN; i++) {
        vec2 d = root.xz - uPush[i].xy;
        float l = length(d), r = uPush[i].z;
        float feet = uPush[i].w - root.y;
        if (r > 0.01 && l < r && feet > -0.6 && feet < 0.9) {
            float s = l / r;
            vec2 away = l > 1e-3 ? d / l : vec2(cos(iRot.x), -sin(iRot.x));
            lean += away * (1.0 - smoothstep(0.4, 1.0, s));
            crush = max(crush, 1.0 - smoothstep(0.0, 0.6, s));
        }
    }
    float ll = length(lean);
    if (ll > 1e-4 && pliant > 0.0) {
        vec2 ld = lean / ll;
        float give = mix(0.35, 1.0, clamp(iRot.w, 0.0, 1.0));
        float theta = min(ll, 1.0) * (0.70 + 0.55 * crush) * give * smoothstep(0.0, 0.12, max(p.y, 0.0) + 0.02);
        float h = dot(p.xz, ld), cs = cos(theta), sn = sin(theta);
        float h2 = h * cs + p.y * sn, y2 = p.y * cs - h * sn;
        p.xz += ld * (h2 - h);
        p.y = max(y2, 0.02 * iPosScale.w);
    }
    p.xz += off;
    p.y -= min(p.y * 0.7, dot(off, off) / max(p.y * 1.4, 0.10));
    // 0.4.7 (gamefixes: foliage did not react to rain) wet leaves hang heavier - the tips droop and spread a little -
    // and the falling drops strike them: a quick, small, uneven bob, each leaf on its own beat, stronger the harder it falls
    if (pliant > 0.0 && (uRainFall.x > 0.01 || uRainFall.y > 0.01)) {
        float tip = clamp(p.y / max(0.25, 0.9 * iPosScale.w), 0.0, 1.0);
        float droop = 0.10 * uRainFall.y * tip * tip * iPosScale.w * mix(0.4, 1.0, clamp(iRot.w, 0.0, 1.0));
        p.y -= droop;
        p.xz *= 1.0 + 0.6 * droop / max(0.2, iPosScale.w);
        float seed = fract(sin(dot(root.xz + p.xz * 3.0, vec2(12.9898, 78.233))) * 43758.5453);
        float beat = uWind.z * (9.0 + 7.0 * seed) + seed * 40.0;
        float hit = pow(max(0.0, sin(beat)), 12.0) * (0.5 + 0.5 * sin(beat * 0.37 + seed * 9.0));
        p.y -= hit * 0.035 * uRainFall.x * tip * iPosScale.w;
    }
    vCol = aColSway.rgb * mix(vec3(0.82, 0.86, 0.80), vec3(1.14, 1.08, 0.86), iRot.y);
    float snowOn = uSnow * iLight.z * smoothstep(0.1, 0.9, n.y * 0.6 + 0.4);
    vCol = mix(vCol, vec3(0.84, 0.86, 0.90), snowOn);
    // wet leaves are darker and deeper in colour
    vCol *= mix(vec3(1.0), vec3(0.78, 0.84, 0.80), clamp(uRainFall.y, 0.0, 1.0) * (1.0 - snowOn));
    // lit as a soft mass, not as a heap of little cubes: the normal leans toward the sky
    vN = normalize(mix(n, vec3(0.0, 1.0, 0.0), 0.5));
    vLight = iLight.xy;
    gl_Position = uPV * vec4(root + p, 1.0);
}
