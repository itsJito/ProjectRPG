#include "include/head.glsl"
// (2.13) Precipitation: rain, sleet, snow, hail, wind-driven sand and drifting snow, as one instanced draw per layer.
// Nothing is simulated: every particle is a point in a box of the world that tiles space round the eye - its place in
// the box from its own seed, moving through it at the layer's fall and the wind's push, wrapped back into the box round
// the camera - so the weather is the same at any speed of travel, never runs dry as one rides through it, and costs
// nothing on the CPU. A drop is drawn only under the open sky: the world's roof height (the top of the highest solid or
// liquid block in each column) is read where it is, so rain stops on roofs and awnings and falls past a window outside.
// Ground layers (sand, a ground blizzard) keep a band of height over that roof instead of the box's.
// A splash layer is the other half of rain and hail: short-lived rings and beads on whatever the drops strike.
uniform mat4 uPV;
uniform vec3 uCam, uRight, uUp;
uniform vec2 uWind, uWorld;
uniform float uTime;
uniform sampler2D uRoof;
// the layer: kind (0 rain, 1 snow, 2 hail, 3 dust, 4 splash), fall speed, size, streak length, alpha
uniform vec4 uLayer;            // kind, fall, size, length
uniform vec4 uBox;              // tile width (m), tile height (m), ground band low, ground band high (ground layers: >= 0)
uniform vec4 uMotion;           // wind share, turbulence, ground (1: kept in the band over the roof), seed salt
out vec2 vQ;
out float vFade;
out float vSeed;
out float vDepth;
flat out int vKind;

float h1(float n) { return fract(sin(n * 12.9898 + 4.1414) * 43758.5453); }

void main() {
    int kind = int(uLayer.x + 0.5);
    float id = float(gl_InstanceID) + uMotion.w * 7919.0;
    vec3 seed = vec3(h1(id), h1(id + 31.7), h1(id + 73.1));
    float tile = uBox.x, tall = uBox.y;
    vec2 q = vec2(gl_VertexID & 1, (gl_VertexID >> 1) & 1) * 2.0 - 1.0;
    vQ = q; vSeed = seed.x; vKind = kind;
    // the drift of the layer: down at its fall, along with the wind as much as it is light (flakes and dust, all of it)
    vec3 vel = vec3(uWind.x * uMotion.x, -uLayer.y, uWind.y * uMotion.x);
    float t = uTime * (0.85 + 0.3 * seed.z);
    vec3 p;
    float roof;
    if (kind == 4) {
        // a splash: somewhere in the tile, on the roof (the ground or whatever stands there), alive a moment in its cycle
        vec2 xz = uCam.xz + (fract(seed.xy + floor(uTime * 2.3 + seed.z) * vec2(0.618, 0.381)) - 0.5) * tile;
        roof = texture(uRoof, xz / uWorld).r;
        float phase = fract(uTime * 2.3 + seed.z);
        p = vec3(xz.x, roof + 0.02, xz.y);
        vFade = 1.0 - smoothstep(0.0, 0.35, phase);
        float s = uLayer.z * (0.4 + phase * 2.2);
        vec3 corner = p + vec3(q.x, 0.0, q.y) * s;
        if (abs(roof - uCam.y) > 14.0) vFade = 0.0;
        float d = distance(p, uCam);
        vFade *= 1.0 - smoothstep(tile * 0.32, tile * 0.5, d);
        vec4 cp = uPV * vec4(corner, 1.0);
        vDepth = cp.w;
        gl_Position = vFade <= 0.001 ? vec4(2.0, 2.0, 2.0, 1.0) : cp;
        return;
    }
    // where in the box: its seed, moved by the drift, wrapped round the camera
    vec3 base = vec3(seed.x * tile, seed.y * tall, seed.z * tile) + vel * t;
    if (uMotion.y > 0.0) {
        // flakes and dust tumble: a slow wander of their own on top of the drift
        base.x += sin(t * (0.9 + seed.y) + seed.x * 40.0) * uMotion.y;
        base.z += cos(t * (0.7 + seed.x) + seed.y * 40.0) * uMotion.y;
        base.y += sin(t * (1.3 + seed.z) + seed.z * 20.0) * uMotion.y * 0.4;
    }
    vec3 half_ = vec3(tile, tall, tile) * 0.5;
    vec3 rel = mod(base - (uCam - half_), vec3(tile, tall, tile)) - half_;
    p = uCam + rel;
    roof = texture(uRoof, p.xz / uWorld).r;
    if (uMotion.z > 0.5) {
        // a ground layer: its height a band over whatever is underfoot, its own seed choosing where in the band
        p.y = roof + mix(uBox.z, uBox.w, fract(seed.y + base.y / max(1.0, tall)));
    }
    // under a roof: not drawn (a layer's band over the roof never is)
    float open = p.y > roof - 0.05 ? 1.0 : 0.0;
    float d = length(rel);
    // thinned toward the box's edge (no seam where it wraps), and faded out at hand's reach (no drops in the eye)
    vFade = open * (1.0 - smoothstep(tile * 0.36, tile * 0.5, length(rel.xz))) * (1.0 - smoothstep(tall * 0.38, tall * 0.5, abs(rel.y)))
          * smoothstep(0.35, 1.4, d);
    vec3 corner;
    if (kind == 0 || kind == 3 && uLayer.w > 0.0) {
        // a streak along its own fall, turned about it to face the eye, longer the faster it falls
        vec3 dir = normalize(vel);
        vec3 view = normalize(p - uCam);
        vec3 side = cross(dir, view);
        side = length(side) > 1e-3 ? normalize(side) : uRight;
        corner = p + dir * q.y * uLayer.w + side * q.x * uLayer.z;
    } else {
        // a flake, a hailstone, a mote of dust: a small round sprite facing the eye, turned by its seed
        float a = seed.x * 6.2832 + t * (kind == 1 ? 1.5 : 0.4);
        vec2 rq = vec2(q.x * cos(a) - q.y * sin(a), q.x * sin(a) + q.y * cos(a));
        float s = uLayer.z * (0.6 + 0.8 * seed.y);
        corner = p + (uRight * rq.x + uUp * rq.y) * s;
    }
    vec4 cp = uPV * vec4(corner, 1.0);
    vDepth = cp.w;
    gl_Position = vFade <= 0.001 ? vec4(2.0, 2.0, 2.0, 1.0) : cp;
}
