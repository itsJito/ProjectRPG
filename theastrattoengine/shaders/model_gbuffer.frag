#include "include/head.glsl"
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
    // Negative alpha requests opaque, spatial erosion (zero to one), rather than transparency.
    float edge = 0.0;
    if (uTint.a < 0.0) {
        vec3 cell = floor(vWorld * 38.0);
        float grain = fract(sin(dot(cell, vec3(12.9898, 78.233, 37.719))) * 43758.5453);
        float coverage = grain + uTint.a;
        if (coverage < 0.0) discard;
        edge = 1.0 - smoothstep(0.0, 0.09, coverage);
    }
    vec3 n = normalize(vN);
    oAlbedo = vec4(mix(vCol.rgb * uTint.rgb, vec3(0.55, 0.72, 0.68), edge), max(edge * 0.35, clamp(uEmissive, 0.0, 1.0)));
    oNormal = vec4(n * 0.5 + 0.5, uFlag / 3.0);
    oMat = vec4(uMatParams.x, uMatParams.y, 1.0, (floor(uMatParams.z * 15.0 + 0.5) * 16.0 + floor(uMatParams.w * 15.0 + 0.5)) / 255.0);
}
