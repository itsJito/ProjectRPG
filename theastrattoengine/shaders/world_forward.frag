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
uniform sampler2D uSceneColor, uSceneDepth,uWaves,uWaterNoise;
// 1.2: the wave grid's corner (x, z) and cell (m): it follows the camera
uniform vec3 uWaveGrid;
// 2.12: the water's motion field round the eye (corner x, z, cell, cells across): flow (m/s), whitewater, open water
uniform sampler2D uFlowField;
uniform vec4 uFlowGrid;
vec4 flowAt(vec2 xz) {
    if (uFlowGrid.w <= 0.0) return vec4(0.0);
    vec2 uv = (xz - uFlowGrid.xy) / (uFlowGrid.z * uFlowGrid.w);
    if (any(lessThan(uv, vec2(0.0))) || any(greaterThan(uv, vec2(1.0)))) return vec4(0.0);
    vec4 t = texture(uFlowField, uv);
    return vec4((t.rg * 255.0 - 128.0) / 32.0, t.b, t.a);
}
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
    float ripple=0.0,crest=0.0,rippleLight=0.0;
    vec2 flow=vec2(0.0);float fl=0.0,white=0.0;
    if (liquid && N.y > 0.5) {
        // the ripples: the surface's own slope (dh/dx, dh/dz) from the wave grid, faded out toward its edge
        vec2 uv=(vWorld.xz-uWaveGrid.xy)/(uWaveGrid.z*256.0),pixel=vec2(1.0/256.0);
        vec2 grad=vec2(texture(uWaves,uv+vec2(pixel.x,0)).r-texture(uWaves,uv-vec2(pixel.x,0)).r,
                       texture(uWaves,uv+vec2(0,pixel.y)).r-texture(uWaves,uv-vec2(0,pixel.y)).r)/(2.0*uWaveGrid.z);
        vec2 edge=smoothstep(vec2(0.0),vec2(0.1),uv)*(1.0-smoothstep(vec2(0.9),vec2(1.0),uv));
        grad*=edge.x*edge.y;
        vec2 first=texture(uWaterNoise,vWorld.xz*0.09+vec2(uTime*0.018,uTime*0.011)).rg*2.0-1.0;
        vec2 second=texture(uWaterNoise,vWorld.xz*0.17+vec2(-uTime*0.013,uTime*0.021)).gb*2.0-1.0;
        // 2.10: running water carries its surface along: the texture drifts with the flow (two phases, crossfaded, so
        // it never stretches), and a quick stretch shows a little streaked broken water
        // 2.12: the motion field reaches past the wave grid: every stretch of water in sight runs its own way
        vec4 field=flowAt(vWorld.xz);
        flow=field.xy;white=field.z;
        fl=length(flow);
        if(fl>0.02) {
            float t0=fract(uTime*0.25),t1=fract(uTime*0.25+0.5),blend=abs(t0*2.0-1.0);
            vec2 a0=texture(uWaterNoise,vWorld.xz*0.21-flow*t0*0.84).rg*2.0-1.0, a1=texture(uWaterNoise,vWorld.xz*0.21-flow*t1*0.84+0.37).rg*2.0-1.0;
            vec2 b0=texture(uWaterNoise,vWorld.xz*0.43-flow*t0*1.72+0.11).gb*2.0-1.0, b1=texture(uWaterNoise,vWorld.xz*0.43-flow*t1*1.72+0.63).gb*2.0-1.0;
            vec2 running=mix(a0,a1,blend)*1.6+mix(b0,b1,blend)*0.8;
            float k=smoothstep(0.02,0.5,fl);
            first=mix(first,running,k);second=mix(second,running*0.6,k);
            crest+=k*0.004*max(0.0,running.x+running.y-0.6)*min(fl,1.5);
        }
        N=normalize(vec3(-grad.x*0.8+(first.x+second.x)*0.035,1.0,-grad.y*0.8+(first.y+second.y)*0.035));
        ripple=clamp(length(grad),0.0,1.5);
        // how much more (or less) of the light a ring's slope faces than the flat water does: the rings read as bands
        rippleLight=dot(normalize(vec3(-grad.x,1.0,-grad.y)),normalize(uSunDir+vec3(0.0,0.6,0.0)))-dot(vec3(0.0,1.0,0.0),normalize(uSunDir+vec3(0.0,0.6,0.0)));
        crest=texture(uWaves,uv).r*edge.x*edge.y;
    }
    vec3 uvw = (vWorld + N) / (vec3(WORLD_X, WORLD_Y, WORLD_Z));
    vec4 r = texture(uProbeR, uvw), g = texture(uProbeG, uvw), b = texture(uProbeB, uvw);
    vec3 irr = max(vec3(r.x + dot(r.yzw, N), g.x + dot(g.yzw, N), b.x + dot(b.yzw, N)), vec3(0.0));
    float sky = vLight.x;
    vec3 col = albedo * (irr + uSunCol * max(dot(N, uSunDir), 0.0) * smoothstep(0.6, 1.0, sky));
    // 2.11: the face of falling water (a falls' curtain, the step of a rapid) runs downward: streaks of white water
    // pour down it at a falling pace, broken across it, catching the light
    if (liquid && abs(N.y) < 0.5) {
        vec2 across = normalize(vec2(-N.z, N.x) + vec2(1e-4));
        float u = dot(vWorld.xz, across);
        vec2 f0 = texture(uWaterNoise, vec2(u * 0.45, vWorld.y * 0.22 + uTime * 0.9)).rg;
        vec2 f1 = texture(uWaterNoise, vec2(u * 0.9 + 0.37, vWorld.y * 0.4 + uTime * 1.7)).gb;
        float streak = smoothstep(0.52, 0.86, f0.r * 0.65 + f1.g * 0.45);
        col = mix(col, vec3(0.86, 0.92, 0.96) * (irr * 0.9 + uSunCol * 0.35 * smoothstep(0.6, 1.0, sky)), streak * 0.55);
        crest = max(crest, streak * 0.4);
    }
    float fres = 0.04 + 0.96 * pow(1.0 - max(dot(N, V), 0.0), 5.0);
    vec3 R = reflect(-V, N);
    vec3 refl = mix(irr, skyColor(R), smoothstep(0.3, 0.9, sky) * smoothstep(-0.1, 0.2, R.y));
    if (liquid && uWaterSSR != 0) { vec4 sr = sceneReflection(vWorld + N * 0.02, R); refl = mix(refl, sr.rgb, sr.a); }
    float a = max(tex.a, liquid ? 0.62 : 0.0);
    if(liquid) {
        vec4 clip=uPV*vec4(vWorld,1);vec2 screen=clip.xy/clip.w*0.5+0.5;
        // 1.2: a ripple bends what is seen through it (the bed wobbles under a ring) as much as the noise does
        vec2 distorted=clamp(screen+N.xz*(0.025+0.05*ripple),vec2(0.002),vec2(0.998));
        float behind=texture(uSceneDepth,distorted).r;
        if(behind>clip.z/clip.w*0.5+0.5+0.0001) {
            vec3 refracted=texture(uSceneColor,distorted).rgb;
            col=mix(col,refracted*vec3(0.76,0.88,0.94),0.55);
        }
    }
    col = mix(col, refl, fres);
    // 2.12: white water where it runs hard - below a fall, through a rapid, along a fast channel's banks: broken foam
    // streaked out along the flow and carried down it at the water's own pace
    if (liquid && N.y > 0.5 && (white > 0.01 || fl > 0.7)) {
        vec2 dir = fl > 1e-3 ? flow / fl : vec2(1.0, 0.0), across = vec2(-dir.y, dir.x);
        vec2 q = vec2(dot(vWorld.xz, dir), dot(vWorld.xz, across));
        float s1 = texture(uWaterNoise, vec2(q.x * 0.16 - uTime * fl * 0.16, q.y * 0.85)).r;
        float s2 = texture(uWaterNoise, vec2(q.x * 0.34 - uTime * fl * 0.3 + 0.5, q.y * 1.6 + 0.3)).g;
        float amount = clamp(white * 1.25 + max(0.0, fl - 0.7) * 0.3, 0.0, 1.0);
        float streak = smoothstep(0.58, 0.86, s1 * 0.6 + s2 * 0.5) * 0.65;
        float churn = smoothstep(1.0 - amount * 0.75, 1.0 - amount * 0.25, s1 * 0.55 + s2 * 0.55);
        float w = clamp(max(streak * amount, churn), 0.0, 1.0);
        vec3 foam = vec3(0.9, 0.94, 0.97) * (irr * 0.85 + uSunCol * 0.6 * smoothstep(0.6, 1.0, sky));
        col = mix(col, foam, w * 0.85);
        a = max(a, w * 0.9);
    }
    if (liquid) {
        // a ring catches the sun on its slopes (a tight glint) and its crests show a touch lighter
        col += uSunCol * pow(max(dot(R, uSunDir), 0.0), 180.0) * (0.6 + 2.4 * ripple) * smoothstep(0.6, 1.0, sky);
        col *= 1.0 + clamp(crest * 6.0, -0.12, 0.18) + clamp(rippleLight * 5.0, -0.32, 0.45);
        col = mix(col, vec3(0.92, 0.96, 1.0) * max(0.4, dot(uSunCol, vec3(0.33))), smoothstep(0.006, 0.02, crest) * smoothstep(0.35, 0.9, ripple) * 0.3);   // foam on a steep crest
        a = clamp(a + ripple * 0.15, 0.0, 1.0);
    }
    a = clamp(a + fres * 0.6, 0.0, 1.0);
    oColor = vec4(airFog(col, vWorld, uCam, uDensity, uAmbient * uFogCol), a);
}
