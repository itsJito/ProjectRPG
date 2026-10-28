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
    if (liquid && N.y > 0.5) {
        // the ripples: the surface's own slope (dh/dx, dh/dz) from the wave grid, faded out toward its edge
        vec2 uv=(vWorld.xz-uWaveGrid.xy)/(uWaveGrid.z*256.0),pixel=vec2(1.0/256.0);
        vec2 grad=vec2(texture(uWaves,uv+vec2(pixel.x,0)).r-texture(uWaves,uv-vec2(pixel.x,0)).r,
                       texture(uWaves,uv+vec2(0,pixel.y)).r-texture(uWaves,uv-vec2(0,pixel.y)).r)/(2.0*uWaveGrid.z);
        vec2 edge=smoothstep(vec2(0.0),vec2(0.1),uv)*(1.0-smoothstep(vec2(0.9),vec2(1.0),uv));
        grad*=edge.x*edge.y;
        vec2 first=texture(uWaterNoise,vWorld.xz*0.09+vec2(uTime*0.018,uTime*0.011)).rg*2.0-1.0;
        vec2 second=texture(uWaterNoise,vWorld.xz*0.17+vec2(-uTime*0.013,uTime*0.021)).gb*2.0-1.0;
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
