#include "include/head.glsl"
#include "include/world_tex.glsl"
#include "include/sky.glsl"
#include "include/bank.glsl"
#include "include/fwd_fog.glsl"
uniform sampler3D uProbeR, uProbeG, uProbeB;
uniform vec3 uCam, uFogCol, uAmbient;
uniform float uDensity;
// 2.4: the lit scene behind the glass and water (before them), its depth, and how to read a point back from it - for
// reflections in water of what is really there
uniform sampler2D uSceneColor, uSceneDepth;
uniform mat4 uPV, uInvPV;
uniform int uWaterSSR;
layout(location=0) out vec4 oColor;

/** Marches the reflected ray through the scene's depth (on the screen); the colour where it strikes, its confidence in a. */
vec4 sceneReflection(vec3 P, vec3 R) {
    float stepLen = 0.25;
    vec3 pos = P + R * stepLen;
    for (int i = 0; i < 40; i++) {
        vec4 c = uPV * vec4(pos, 1.0);
        if (c.w <= 0.05) break;
        vec2 uv = c.xy / c.w * 0.5 + 0.5;
        if (uv.x < 0.0 || uv.y < 0.0 || uv.x > 1.0 || uv.y > 1.0) break;
        float sd = textureLod(uSceneDepth, uv, 0.0).r;
        if (sd < 1.0) {
            vec4 s = uInvPV * vec4(uv * 2.0 - 1.0, sd * 2.0 - 1.0, 1.0);
            vec3 S = s.xyz / s.w;
            float behind = distance(S, uCam) < distance(pos, uCam) - 0.02 ? 1.0 : 0.0;
            if (behind > 0.0 && distance(S, pos) < stepLen * 2.0 + 0.3) {
                vec2 edge = smoothstep(vec2(0.0), vec2(0.06), uv) * (1.0 - smoothstep(vec2(0.94), vec2(1.0), uv));
                return vec4(textureLod(uSceneColor, uv, 0.0).rgb, edge.x * edge.y * (1.0 - float(i) / 40.0));
            }
        }
        stepLen *= 1.15;
        pos += R * stepLen;
    }
    return vec4(0.0);
}
void main() {
    vec3 bp;
    vec4 tex = atlasTexel(bp);
    vec3 N = NORMALS[vNormal];
    bool liquid = (vFlags & 4) != 0;
    // 0.8: a pane is drawn here once - its near face - and only where it is glass. The frame round it is solid and
    // was written with the rest of the solid world (it has depth: it hides what is behind it, glass included).
    if (!liquid && (paneAlpha() >= GLASS_SOLID || !gl_FrontFacing)) discard;
    vec3 albedo = pow(tex.rgb, vec3(2.2));
    vec3 V = normalize(uCam - vWorld);
    if (dot(N, V) < 0.0) N = -N;
    if (liquid && N.y > 0.5) {
        // 2.4: the water's surface moves: three crossing wave trains, small and slow, bend its normal
        vec2 w = vWorld.xz;
        vec2 g = vec2(cos(w.x * 1.7 + uTime * 1.3) * 0.5 + cos(w.x * 0.9 - w.y * 1.3 + uTime * 0.8) * 0.35 + cos(w.x * 3.1 + w.y * 2.2 - uTime * 2.1) * 0.15,
                      sin(w.y * 1.9 + uTime * 1.1) * 0.5 + cos(w.y * 1.1 + w.x * 0.7 - uTime * 0.9) * 0.35 + sin(w.y * 2.9 - w.x * 1.8 + uTime * 1.9) * 0.15);
        N = normalize(vec3(g.x * 0.06, 1.0, g.y * 0.06));
    }
    vec3 uvw = (vWorld + N) / (vec3(WORLD_X, WORLD_Y, WORLD_Z));
    vec4 r = texture(uProbeR, uvw), g = texture(uProbeG, uvw), b = texture(uProbeB, uvw);
    vec3 irr = max(vec3(r.x + dot(r.yzw, N), g.x + dot(g.yzw, N), b.x + dot(b.yzw, N)), vec3(0.0));
    float sky = vLight.x;
    vec3 col = albedo * (irr + uSunCol * max(dot(N, uSunDir), 0.0) * smoothstep(0.6, 1.0, sky));
    float fres = 0.04 + 0.96 * pow(1.0 - max(dot(N, V), 0.0), 5.0);
    vec3 R = reflect(-V, N);
    vec3 refl = mix(irr, skyColor(R), smoothstep(0.3, 0.9, sky) * smoothstep(-0.1, 0.2, R.y));
    if (liquid && uWaterSSR != 0) { vec4 sr = sceneReflection(vWorld + N * 0.02, R); refl = mix(refl, sr.rgb, sr.a); }
    float a = max(tex.a, liquid ? 0.62 : 0.0);
    col = mix(col, refl, fres);
    a = clamp(a + fres * 0.6, 0.0, 1.0);
    oColor = vec4(airFog(col, vWorld, uCam, uDensity, uAmbient * uFogCol), a);
}
