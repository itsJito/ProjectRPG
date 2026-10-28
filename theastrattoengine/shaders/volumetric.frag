#include "include/head.glsl"
#include "include/light_buffers.glsl"
in vec2 vUV;
layout(location=0) out vec4 oFog;      // rgb in-scattered light, a transmittance
uniform sampler2D uDepth;
uniform sampler2DArrayShadow uShadow;
uniform mat4 uInvPV;
uniform mat4 uShadowVP[4];
uniform vec4 uSplits;
uniform int uCascades, uLightCount, uSteps;
uniform vec3 uCam, uCamFwd, uSunDir, uSunCol, uFogCol, uAmbient;
uniform float uDensity, uFrame, uHeightBase, uMaxDist;
uniform sampler3D uProbeR, uProbeG, uProbeB;
// 2.4: smoke, dust and steam - up to 32 soft volumes, three vec4 each (centre + radius; density + colour; glow, rise,
// swirl, seed)
uniform int uSmokeN;
uniform vec4 uSmoke[96];
uniform float uTime,uCloudCover;
uniform vec2 uCloudOffset;
float hash31(vec3 p) { p = fract(p * 0.3183099 + 0.1); p *= 17.0; return fract(p.x * p.y * p.z * (p.x + p.y + p.z)); }
float vnoise(vec3 p) {
    vec3 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(hash31(i), hash31(i + vec3(1, 0, 0)), f.x), mix(hash31(i + vec3(0, 1, 0)), hash31(i + vec3(1, 1, 0)), f.x), f.y),
               mix(mix(hash31(i + vec3(0, 0, 1)), hash31(i + vec3(1, 0, 1)), f.x), mix(hash31(i + vec3(0, 1, 1)), hash31(i + vec3(1, 1, 1)), f.x), f.y), f.z);
}
/** How thick a volume's smoke is at a point (per metre), what colour it scatters and how much it glows. Billows: two
 *  octaves of noise carried up with the smoke's rise, thinning toward the volume's edge and its top. */
// (the march through a volume samples only that volume: summing every plume at every step made the cost grow with
// the square of the plumes in view - milliseconds a frame over a town of chimneys)
float smokeOne(int i, vec3 p, out vec3 albedo, out float glow) {
    vec4 a = uSmoke[i * 3], b = uSmoke[i * 3 + 1], c = uSmoke[i * 3 + 2];
    albedo = b.yzw; glow = c.x;
    vec3 q = (p - a.xyz) / a.w;
    float l = length(q * vec3(1.15, 0.6, 1.15));
    if (l >= 1.0) return 0.0;
    vec3 n = p * (1.6 / max(a.w, 0.3)) - vec3(0.0, uTime * c.y / max(a.w, 0.3) * 1.6, 0.0) + vec3(c.w * 17.0);
    float billow = vnoise(n) * 0.5 + vnoise(n * 2.3 + 7.1) * 0.3 + vnoise(n * 5.1 + 3.3) * 0.2;
    return max(0.0, b.x * (1.0 - smoothstep(0.2, 1.0, l)) * mix(1.0, smoothstep(0.25, 0.75, billow) * 1.8, clamp(c.z, 0.0, 1.0)));
}
// 0.9, interiors: how much of the sky's light there is in the air at a point, 0..1, from the light the probes hold there.
// The haze used to glow with the full ambient of the open sky wherever it was - in a sealed corridor, a cellar, a
// windowless hall - so the air indoors was milky by day and dark rooms were never dark. Air glows by the light that
// reaches it: in the open that is the sky's, in a lit room the room's, in a shut one almost nothing.
float airLight(vec3 p) {
    vec3 uvw = p / (vec3(WORLD_X, WORLD_Y, WORLD_Z));
    float l = dot(max(vec3(texture(uProbeR, uvw).x, texture(uProbeG, uvw).x, texture(uProbeB, uvw).x), vec3(0.0)), vec3(0.2126, 0.7152, 0.0722));
    float open = dot(uAmbient, vec3(0.2126, 0.7152, 0.0722));
    return clamp(l / max(open, 1e-4), 0.035, 1.0);
}
float ign(vec2 p) { return fract(52.9829189 * fract(dot(p, vec2(0.06711056, 0.00583715)))); }
float sunVis(vec3 P, float viewZ) {
    int c = uCascades - 1;
    for (int i = 0; i < uCascades; i++) if (viewZ < uSplits[i]) { c = i; break; }
    if (viewZ >= uSplits[uCascades - 1]) return 1.0;
    vec4 sp = uShadowVP[c] * vec4(P, 1.0);
    vec3 s = sp.xyz * 0.5 + 0.5;
    if (s.x < 0.0 || s.y < 0.0 || s.x > 1.0 || s.y > 1.0 || s.z > 1.0) return 1.0;
    return texture(uShadow, vec4(s.xy, float(c), s.z - 0.0006));
}
float density(vec3 p, float air) {
    // thicker near the street, thinning with height; underground air is still and heavy
    float h = p.y - uHeightBase;
    if (h < -3.0) return uDensity * 2.2;
    float haze = exp(-max(h, 0.0) * 0.045);
    // 0.1.9, the mist: a layer a few metres deep lying on the ground, torn by slow noise into banks that drift with
    // the air - thick in the hollows, clear between - so the fog is a thing in the world, not a tint over it. Only in
    // the open air (the probes' sky light): a room keeps its own air.
    vec3 q = p * vec3(0.05, 0.16, 0.05) + vec3(uTime * 0.011, -uTime * 0.004, uTime * 0.006);
    float n = vnoise(q) * 0.62 + vnoise(q * 2.6 + vec3(5.3, 1.7, 9.1)) * 0.38;
    float layer = exp(-max(h + 0.5, 0.0) * 0.36);
    float mist = smoothstep(0.36, 0.82, n) * layer * smoothstep(0.3, 0.75, air);
    return uDensity * (haze + mist * 5.5);
}
void main() {
    float d = textureLod(uDepth, vUV, 0.0).r;
    vec4 c = uInvPV * vec4(vUV * 2.0 - 1.0, d * 2.0 - 1.0, 1.0);
    vec3 P = c.xyz / c.w;
    vec3 ray = P - uCam;
    float len = min(length(ray), uMaxDist);
    vec3 dir = normalize(ray);
    float stepLen = len / float(uSteps);
    float j = ign(gl_FragCoord.xy + uFrame * 5.588238);
    float mu = dot(dir, uSunDir);
    float g = 0.62;
    float phase = (1.0 - g * g) / (4.0 * PI * pow(1.0 + g * g - 2.0 * g * mu, 1.5));
    vec3 scat = vec3(0.0);
    float trans = 1.0;
    // the air's own light changes slowly along a ray: read from the probes at its near end, middle and far end, and
    // blended between (three probe reads a step were a third of the fog's cost)
    float air0 = airLight(uCam + dir * min(len, 1.0)), air1 = airLight(uCam + dir * (len * 0.5)), air2 = airLight(uCam + dir * len);
    for (int i = 0; i < uSteps; i++) {
        float t = (float(i) + j) * stepLen;
        vec3 p = uCam + dir * t;
        float u = t / max(len, 1e-3);
        float air = u < 0.5 ? mix(air0, air1, u * 2.0) : mix(air1, air2, u * 2.0 - 1.0);
        float dens = density(p, air);
        if (dens <= 0.0) continue;
        float ext = dens * stepLen;
        float tr = exp(-ext);
        vec3 light = uAmbient * air + uSunCol * phase * 6.0 * sunVis(p, dot(p - uCam, uCamFwd));
        // the nearest local lights glow in the haze (flashlight cone, fires, muzzle flashes)
        for (int k = 0; k < min(uLightCount, 4); k++) {
            vec3 ld = lights[k].posR.xyz - p;
            float ll = length(ld), r = lights[k].posR.w;
            if (ll >= r) continue;
            float x = ll / r, att = (1.0 - x * x); att = att * att / (ll * ll * 0.35 + 1.0);
            if ((int(lights[k].col.a + 0.5) & 2) != 0) att *= smoothstep(lights[k].dir.w, mix(lights[k].dir.w, 1.0, 0.45), dot(-ld / max(ll, 1e-3), lights[k].dir.xyz));
            light += lights[k].col.rgb * att * 0.35;
        }
        scat += trans * (1.0 - tr) * light * uFogCol;
        trans *= tr;
        if (trans < 0.01) break;
    }
    // 2.4: the smoke. Each volume the ray passes through is marched on its own, finely, over just the stretch of the ray
    // inside it (the haze's steps are metres apart: a plume would fall between them). Nearer volumes hide farther ones
    // and the haze behind them.
    if (uSmokeN > 0) {
        vec3 sscat = vec3(0.0);
        float strans = 1.0;
        for (int v = 0; v < 32; v++) {
            if (v >= uSmokeN) break;
            vec4 a = uSmoke[v * 3];
            vec3 oc = uCam - a.xyz;
            float b = dot(oc, dir), c = dot(oc, oc) - a.w * a.w, disc = b * b - c;
            if (disc <= 0.0) continue;
            float sq = sqrt(disc), t0 = max(0.0, -b - sq), t1 = min(len, -b + sq);
            if (t1 <= t0) continue;
            const int N = 12;
            float sl = (t1 - t0) / float(N);
            for (int i = 0; i < N; i++) {
                vec3 p = uCam + dir * (t0 + (float(i) + j) * sl);
                vec3 col; float glow;
                float k = smokeOne(v, p, col, glow);
                if (k <= 0.0) continue;
                float tr = exp(-k * sl);
                // lit by the sky and the sun (broadly: smoke scatters with a weak forward lobe, and shades itself a little),
                // the nearest lamps and fires, and its own glow (a fire's smoke lit from beneath)
                vec3 light = uAmbient * airLight(p) * 1.4 + uSunCol * (0.9 + phase * 1.5) * exp(-k * 0.5) * sunVis(p, dot(p - uCam, uCamFwd));
                for (int q = 0; q < min(uLightCount, 4); q++) {
                    vec3 ld = lights[q].posR.xyz - p;
                    float ll = length(ld), r = lights[q].posR.w;
                    if (ll >= r) continue;
                    float x = ll / r, att = (1.0 - x * x); att = att * att / (ll * ll * 0.35 + 1.0);
                    light += lights[q].col.rgb * att * 0.3;
                }
                sscat += strans * (1.0 - tr) * (light * col + col * glow * 1.5);
                strans *= tr;
            }
            if (strans < 0.02) break;
        }
        scat = sscat + scat * strans;
        trans *= strans;
    }
    // Bounded cloud slab, at the existing quarter-resolution fog target. Geometry depth clips the march.
    if(uCloudCover>0.01 && dir.y>0.025) {
        float nearCloud=max(0.0,(78.0-uCam.y)/dir.y), farCloud=(106.0-uCam.y)/dir.y;
        farCloud=min(farCloud,d>=0.99999?800.0:length(ray));
        if(farCloud>nearCloud) {
            int count=uSteps<=10?6:10; float stride=(farCloud-nearCloud)/float(count);
            vec3 cloud=vec3(0);float visibility=1.0;
            for(int i=0;i<10;i++) {
                if(i>=count)break;
                vec3 p=uCam+dir*(nearCloud+(float(i)+0.5)*stride);
                vec3 uv=(p-vec3(uCloudOffset.x,0,uCloudOffset.y))*0.024;
                float shape=vnoise(uv)*0.7+vnoise(uv*2.2)*0.3;
                float edge=smoothstep(78.0,84.0,p.y)*(1.0-smoothstep(98.0,106.0,p.y));
                float amount=max(0.0,shape-(0.7-uCloudCover*0.38))*edge;
                float a=1.0-exp(-amount*stride*0.14);
                vec3 light=uAmbient*(0.8+0.5*edge)+uSunCol*(0.28+0.35*max(mu,0.0));
                cloud+=visibility*a*light;visibility*=1.0-a;
            }
            scat+=trans*cloud;trans*=visibility;
        }
    }
    // (what lies beyond the march is fogged where this is applied, the same for a wall as for the sky behind it)
    oFog = vec4(scat, trans);
}
