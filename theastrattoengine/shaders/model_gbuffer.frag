#include "include/head.glsl"
#include "include/dissolve.glsl"
in vec4 vCol;
in vec3 vWorld;
in vec3 vN;
uniform vec4 uTint;
uniform vec4 uMatParams;   // roughness, metallic, sky visibility, baked block light
uniform float uEmissive, uFlag;
layout(location=0) out vec4 oAlbedo;
layout(location=1) out vec4 oNormal;
layout(location=2) out vec4 oMat;
void main() {
    float edge = dissolveEdge();
    vec3 n = normalize(vN);
    oAlbedo = vec4(mix(vec3(0.32,0.13,0.035),vCol.rgb * uTint.rgb,edge), clamp(uEmissive, 0.0, 1.0));
    oNormal = vec4(n * 0.5 + 0.5, uFlag / 3.0);
    oMat = vec4(uMatParams.x, uMatParams.y, 1.0, (floor(uMatParams.z * 15.0 + 0.5) * 16.0 + floor(uMatParams.w * 15.0 + 0.5)) / 255.0);
}
