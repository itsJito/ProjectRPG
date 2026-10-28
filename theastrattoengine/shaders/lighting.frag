#include "include/head.glsl"
#include "include/voxel.glsl"
#include "include/sky.glsl"
#include "include/probes.glsl"
#include "include/light_buffers.glsl"
layout(std430, binding = 1) readonly buffer Tiles { uint tile[]; };
in vec2 vUV;
layout(location=0) out vec4 oColor;
uniform sampler2D uAlbedo, uNormal, uMat, uDepth, uAO, uSSR;
uniform sampler2DArrayShadow uShadow;
uniform sampler2DArray uShadowRaw;      // 2.4: the same maps, as depths (no comparison)
uniform vec4 uCascadeDepth;             // 2.4: each cascade's depth range (m)
uniform float uFrame;
uniform mat4 uInvPV;
uniform mat4 uShadowVP[4];
uniform vec4 uSplits, uCascadeTexel;
uniform int uCascades, uTileSize, uShadowedLights, uUseAO, uUseSSR;
// 1.1: the bodies that stand in the way of a local light - five floats each: the feet (xyz), the radius, the height
uniform int uBodyCount;
uniform float uBodies[120];
uniform ivec2 uTiles;
uniform vec3 uCam, uCamFwd, uTint;
uniform float uShadowRes, uAmbientFloor;
uniform sampler2D uExposure;

vec3 worldPos(vec2 uv, float d) { vec4 c = uInvPV * vec4(uv * 2.0 - 1.0, d * 2.0 - 1.0, 1.0); return c.xyz / c.w; }

// 2.4: soft shadows that harden toward contact (percentage-closer soft shadows). The blockers are found first (a ring of
// taps round the point, read as plain depths): how far they are from what they shade sets the penumbra - a post's
// shadow is sharp at its foot and spreads with distance, as the sun's disc makes it. Then the shadow is filtered over
// that width with a Poisson disc turned a different way at every pixel (the noise is resolved by the temporal pass).
const vec2 POISSON[16] = vec2[](vec2(-0.94201624, -0.39906216), vec2(0.94558609, -0.76890725), vec2(-0.09418410, -0.92938870), vec2(0.34495938, 0.29387760),
    vec2(-0.91588581, 0.45771432), vec2(-0.81544232, -0.87912464), vec2(-0.38277543, 0.27676845), vec2(0.97484398, 0.75648379),
    vec2(0.44323325, -0.97511554), vec2(0.53742981, -0.47373420), vec2(-0.26496911, -0.41893023), vec2(0.79197514, 0.19090188),
    vec2(-0.24188840, 0.99706507), vec2(-0.81409955, 0.91437590), vec2(0.19984126, 0.78641367), vec2(0.14383161, -0.14100790));
float shadowNoise() { return fract(52.9829189 * fract(dot(gl_FragCoord.xy + uFrame * 5.588238, vec2(0.06711056, 0.00583715)))); }
float cascadeShadow(int c, vec3 P, vec3 N, float ndl, float baked) {
    float texel = uCascadeTexel[c];
    vec3 Pb = P + N * texel * (1.6 + 2.0 * (1.0 - ndl));
    vec4 sp = uShadowVP[c] * vec4(Pb, 1.0);
    vec3 s = sp.xyz * 0.5 + 0.5;
    // 1.2: off the cascade's map there is no shadow to read - what the baked sky light says, not full sun (a room's
    // walls past the map's edge were lit by the sun through the roof)
    if (s.x < 0.0 || s.y < 0.0 || s.x > 1.0 || s.y > 1.0 || s.z > 1.0) return baked;
    float px = 1.0 / uShadowRes;
    float bias = 0.00035 * (1.0 + float(c));
    float a = shadowNoise() * 6.2831853;
    mat2 rot = mat2(cos(a), sin(a), -sin(a), cos(a));
    // the blockers: within a search radius of about 0.6 m in the world
    float searchTexels = clamp(0.6 / texel, 2.0, 14.0);
    float centerDepth = texture(uShadowRaw, vec3(s.xy, float(c))).r;
    float centerBlocked = centerDepth < s.z - bias ? 1.0 : 0.0;
    float blockers = centerDepth * centerBlocked, nb = centerBlocked;
    for (int i = 0; i < 8; i++) {
        vec2 o = rot * POISSON[i * 2] * searchTexels * px;
        float d = texture(uShadowRaw, vec3(s.xy + o, float(c))).r;
        if (d < s.z - bias) { blockers += d; nb += 1.0; }
    }
    if (nb < 0.5) return 1.0;
    // the penumbra: the gap between blocker and receiver (m) times the spread of the sun's light (a soft sun, wider
    // than the real disc, as the eye reads shadows in a game), in shadow-map texels
    float gap = (s.z - blockers / nb) * uCascadeDepth[c];
    float width = clamp(gap * 0.035 / texel, 1.0, 12.0);
    // deep inside the umbra (every search sample blocked, a narrow penumbra): no filtering would find light
    if (nb > 8.5 && width < 2.5) return 0.0;
    // a narrow penumbra is resolved by half the taps (the comparison sampler filters each one bilinearly)
    // (two fixed loops: a tap count chosen at run time would not be unrolled)
    float sum = 0.0;
    if (width < 4.0) {
        for (int i = 0; i < 8; i++) sum += texture(uShadow, vec4(s.xy + rot * POISSON[i * 2] * width * px, float(c), s.z - bias));
        return sum / 8.0;
    }
    for (int i = 0; i < 16; i++) sum += texture(uShadow, vec4(s.xy + rot * POISSON[i] * width * px, float(c), s.z - bias));
    return sum / 16.0;
}

float sunShadow(vec3 P, vec3 N, float ndl, float viewZ, float skyVis) {
    int c = 0;
    for (int i = 0; i < uCascades; i++) { c = i; if (viewZ < uSplits[i]) break; }
    float last = uSplits[uCascades - 1];
    float baked = smoothstep(0.5, 0.95, skyVis);
    if (viewZ >= last) return baked;
    float sh = cascadeShadow(c, P, N, ndl, baked);
    // cross-fade into the next cascade (or into the baked sky term past the last one)
    float end = uSplits[c], band = end * 0.12;
    float k = smoothstep(end - band, end, viewZ);
    if (k > 0.0) sh = mix(sh, c + 1 < uCascades ? cascadeShadow(c + 1, P, N, ndl, baked) : baked, k);
    return sh;
}

/** Visibility between a surface point and a local light, marched through the voxel volume. */
// 1.1: how much of a local light gets past the bodies between it and a surface. A local light's shadow was traced
// through the voxel world and nothing else, so a wall threw a shadow in a car's headlights and the person standing in
// front of them did not. Each body is an upright capsule; the ray from the surface to the light is tested against its
// axis, and the edge of the shadow is soft by the width of the body. A surface that is part of a body is not
// shadowed by that body.
float bodyVisibility(vec3 a, vec3 b) {
    float vis = 1.0;
    vec3 ab = b - a;
    float abl = max(dot(ab, ab), 1e-6);
    for (int i = 0; i < uBodyCount; i++) {
        vec3 foot = vec3(uBodies[i * 5], uBodies[i * 5 + 1], uBodies[i * 5 + 2]);
        float r = uBodies[i * 5 + 3], h = uBodies[i * 5 + 4];
        vec3 c0 = foot + vec3(0.0, r, 0.0), c1 = foot + vec3(0.0, max(h - r, r), 0.0);
        // the surface itself belongs to this body - but never the ground it stands on: the floor under the feet is
        // within the same reach of the capsule, and skipping it lit a bright disc under every body with its shadow
        // round the outside (a lamp's shadow came out inverted, a halo followed everyone about)
        vec3 ax = c1 - c0;
        float axl = max(dot(ax, ax), 1e-6);
        float ta = clamp(dot(a - c0, ax) / axl, 0.0, 1.0);
        if (a.y > foot.y + 0.06 && length(a - (c0 + ax * ta)) < r * 1.35) continue;
        // A body-attached emitter (burning clothing, a held light) must not be occluded by its own source body.
        // Otherwise every ray ends inside the capsule and a moving bright wedge appears on the ground.
        float tb = clamp(dot(b - c0, ax) / axl, 0.0, 1.0);
        if (length(b - (c0 + ax * tb)) < r * 1.35) continue;
        // closest approach of the two segments
        vec3 w0 = a - c0;
        float bb = dot(ab, ax), dd = dot(ab, w0), ee = dot(ax, w0), den = abl * axl - bb * bb;
        float s = den > 1e-6 ? clamp((bb * ee - axl * dd) / den, 0.0, 1.0) : 0.0;
        float t = clamp((bb * s + ee) / axl, 0.0, 1.0);
        s = clamp((bb * t - dd) / abl, 0.0, 1.0);
        float dist = length((a + ab * s) - (c0 + ax * t));
        // softer the further the body is from what its shadow falls on
        float soft = r * (0.25 + 0.6 * clamp(s * sqrt(abl) / 6.0, 0.0, 1.0));
        vis *= smoothstep(r - soft, r + soft, dist);
        if (vis < 0.02) return 0.0;
    }
    return vis;
}

float voxelVisibility(vec3 a, vec3 b) {
    vec3 d = b - a;
    float len = length(d);
    if (len < 0.75) return 1.0;
    d /= len;
    ivec3 c = ivec3(floor(a));
    ivec3 st = ivec3(sign(d));
    vec3 inv = 1.0 / max(abs(d), vec3(1e-5));
    vec3 tm = ddaStart(a, d, c, inv);
    float t = 0.0;
    for (int i = 0; i < 22; i++) {
        if (tm.x < tm.y && tm.x < tm.z) { t = tm.x; c.x += st.x; tm.x += inv.x; }
        else if (tm.y < tm.z) { t = tm.y; c.y += st.y; tm.y += inv.y; }
        else { t = tm.z; c.z += st.z; tm.z += inv.z; }
        if (t >= len - 0.6) return 1.0;
        if (!inWorld(c)) return 1.0;
        if (opq(voxel(c))) return 0.0;
    }
    return 1.0;
}

float distGGX(float ndh, float a) { float a2 = a * a; float d = ndh * ndh * (a2 - 1.0) + 1.0; return a2 / (PI * d * d + 1e-5); }
float visSmith(float ndv, float ndl, float a) { float k = a * 0.5 + 1e-4; return 1.0 / ((ndv * (1.0 - k) + k) * (ndl * (1.0 - k) + k) * 4.0 + 1e-4); }
vec3 fresnel(vec3 f0, float c) { float f = pow(1.0 - c, 5.0); return f0 + (1.0 - f0) * f; }

vec3 shade(vec3 albedo, vec3 f0, float rough, float metal, vec3 N, vec3 V, vec3 L, vec3 Lc) {
    float ndl = max(dot(N, L), 0.0);
    if (ndl <= 0.0) return vec3(0.0);
    vec3 H = normalize(V + L);
    float ndv = max(dot(N, V), 1e-3), ndh = max(dot(N, H), 0.0), vdh = max(dot(V, H), 0.0);
    float a = max(rough * rough, 0.02);
    vec3 F = fresnel(f0, vdh);
    vec3 spec = F * (distGGX(ndh, a) * visSmith(ndv, ndl, a));
    vec3 diff = albedo * (1.0 - metal) * (1.0 - F);
    return (diff + min(spec, vec3(8.0))) * Lc * ndl;
}

void main() {
    float depth = texture(uDepth, vUV).r;
    vec3 Pfar = worldPos(vUV, 1.0);
    vec3 viewDir = normalize(Pfar - uCam);
    if (depth >= 1.0) { oColor = vec4(skyColor(viewDir), 1.0); return; }
    vec4 ga = texture(uAlbedo, vUV), gn = texture(uNormal, vUV), gm = texture(uMat, vUV);
    int flag = int(gn.a * 3.0 + 0.5);
    vec3 N = normalize(gn.xyz * 2.0 - 1.0);
    // the viewmodel is drawn with its own projection in a reserved depth band: light it where the hands are
    vec3 P = flag == 1 ? uCam + viewDir * 0.45 : worldPos(vUV, depth);
    vec3 V = -viewDir;
    vec3 albedo = pow(ga.rgb, vec3(2.2));
    float rough = gm.r, metal = gm.g, bakedAO = gm.b;
    int pk = int(gm.a * 255.0 + 0.5);
    float skyVis = float(pk >> 4) / 15.0, blk = float(pk & 15) / 15.0;
    vec3 f0 = mix(vec3(0.04), albedo, metal);
    float viewZ = dot(P - uCam, uCamFwd);

    float ao = uUseAO != 0 && flag != 1 ? texture(uAO, vUV).r : 1.0;
    float occl = min(ao, 0.45 + 0.55 * bakedAO);

    // ---- sun or moon
    vec3 col = vec3(0.0);
    float ndl = max(dot(N, uSunDir), 0.0);
    if (flag == 3) {
        // 2.4: foliage. Leaves are thin: light wraps round them, and the sun seen through a leaf lights it from behind
        // (a translucency term, strongest looking toward the sun, tinted by the leaf: a cheap stand-in for subsurface
        // scattering). The shadow is the sun's at that point, so a leaf deep in the crown is not lit through.
        float sh = sunShadow(P, N, max(ndl, 0.3), viewZ, skyVis);
        float wrap = max(0.0, (dot(N, uSunDir) + 0.45) / 1.45);
        float through = pow(max(dot(-V, uSunDir), 0.0), 5.0) * 1.6 + 0.28;
        col += albedo * uSunCol * (wrap * 0.85 + through * vec3(0.95, 1.05, 0.55) * 0.75) * sh;
    } else if (ndl > 0.0 && dot(uSunCol, vec3(1.0)) > 1e-4) {
        float sh = sunShadow(P, N, ndl, flag == 1 ? 0.5 : viewZ, skyVis);
        if (sh > 0.0) col += shade(albedo, f0, rough, metal, N, V, uSunDir, uSunCol) * sh;
    }

    // ---- indirect diffuse from the irradiance probes
    float conf;
    vec3 irr = probeIrradiance(P, N, conf);
    vec3 fallback = skyBase(N) * skyVis * skyVis * 0.5;
    irr = mix(fallback, irr, conf) + vec3(uAmbientFloor);
    col += albedo * (1.0 - metal) * irr * occl;

    // ---- indirect specular: screen-space reflection where it found one, probe / sky lobe elsewhere
    vec3 R = reflect(-V, N);
    float ndv = max(dot(N, V), 1e-3);
    vec3 Fenv = fresnel(f0, ndv) * (1.0 - rough * 0.85);
    vec3 envSpec = mix(probeFiltered(P, R), skyBase(R), clamp(skyVis * skyVis * (1.0 - rough), 0.0, 1.0) * smoothstep(-0.1, 0.3, R.y));
    if (uUseSSR != 0 && flag != 1) { vec4 ssr = texture(uSSR, vUV); envSpec = mix(envSpec, ssr.rgb, ssr.a); }
    col += envSpec * Fenv * mix(occl, 1.0, 0.3);

    // ---- local lights of this tile
    ivec2 t = min(ivec2(gl_FragCoord.xy) / uTileSize, uTiles - 1);
    uint base = uint(t.y * uTiles.x + t.x) * 32u;
    uint n = min(tile[base], 31u), bodyLights = 0u;
    for (uint i = 0u; i < n; i++) {
        uint li = tile[base + 1u + i];
        Light l = lights[li];
        vec3 d = l.posR.xyz - P;
        float dist = length(d), r = l.posR.w;
        if (dist >= r) continue;
        vec3 L = d / max(dist, 1e-3);
        float x = dist / r;
        float att = (1.0 - x * x * x * x); att = att * att / (dist * dist * 0.35 + 1.0);
        int lf = int(l.col.a + 0.5);
        if ((lf & 2) != 0) att *= smoothstep(l.dir.w, mix(l.dir.w, 1.0, 0.45), dot(-L, l.dir.xyz));
        if (att < 1e-3) continue;
        if ((lf & 1) != 0 && int(li) < uShadowedLights) att *= voxelVisibility(P + N * 0.08, l.posR.xyz);
        else if ((lf & 4) != 0 && flag != 1) att *= smoothstep(0.0, 0.3, blk);   // unshadowed lamps never reach where their baked light did not
        if (att < 1e-3) continue;
        // Capsule occlusion remains available for ordinary lamps/fires and every quality level.
        // Bound work to the first two contributing nearby tile lights; detailed sun silhouettes use the model shadow maps.
        if (bodyLights < 2u && uBodyCount > 0 && dist < 10.0 && att > 1e-3) {
            att *= bodyVisibility(P + N * 0.05, l.posR.xyz); bodyLights++;
        }
        col += shade(albedo, f0, rough, metal, N, V, L, l.col.rgb * att);
    }

    // ---- 1.0: a lightning flash falls on everything the sky can see
    col += albedo * (1.0 - metal) * vec3(0.75, 0.80, 1.0) * uLightning * 5.5 * skyVis * skyVis * (0.35 + 0.65 * max(N.y, 0.0)) * occl;

    // ---- emission. 1.0: a lit surface is as bright on the screen in a dark room as in daylight - it used to be a
    // fixed radiance, which the eye's adaptation multiplied sixfold indoors: every lamp, screen and candle burnt out
    // to white and bloomed across the room. What lights the room is the lamp's light, not the brightness of its glass.
    float expo = clamp(texelFetch(uExposure, ivec2(0), 0).r, 0.5, 8.0);
    col += albedo * ga.a * (3.0 / expo + 1.2);
    if (flag == 2) col = albedo * (1.0 + ga.a * (2.6 / expo + 1.0));
    oColor = vec4(col * uTint, 1.0);
}
