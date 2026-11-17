#include "include/head.glsl"
#include "include/dissolve.glsl"
in vec4 vCol;
in vec3 vWorld;
in vec3 vN;
uniform vec4 uTint;
uniform vec4 uMatParams;   // roughness, metallic, sky visibility, baked block light
uniform float uEmissive, uFlag;
uniform float uSnowCover;    // 2.16: how much snow lies (the world's): settled on what faces up under the open sky
layout(location=0) out vec4 oAlbedo;
layout(location=1) out vec4 oNormal;
layout(location=2) out vec4 oMat;
void main() {
    float edge = dissolveEdge();
    vec3 n = normalize(vN);
    vec3 base = vCol.rgb * uTint.rgb;
    if (uSnowCover > 0.0 && uMatParams.z > 0.7 && n.y > 0.45) {
        float lie = smoothstep(0.45, 0.85, n.y) * smoothstep(0.7, 0.95, uMatParams.z) * smoothstep(0.05, 0.6, uSnowCover);
        lie *= 0.75 + 0.25 * fract(sin(dot(floor(vWorld.xz * 12.0), vec2(12.9898, 78.233))) * 43758.5453);
        base = mix(base, vec3(0.88, 0.90, 0.94), lie);
    }
    oAlbedo = vec4(mix(vec3(0.32,0.13,0.035),base,edge), clamp(uEmissive, 0.0, 1.0));
    oNormal = vec4(n * 0.5 + 0.5, uFlag / 3.0);
    oMat = vec4(uMatParams.x, uMatParams.y, 1.0, packLight(uMatParams.z, uMatParams.w));
}
