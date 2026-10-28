#include "include/head.glsl"
in vec2 vUV;
layout(location=0) out float oAO;
uniform sampler2D uDepth, uNormal;
uniform mat4 uInvProj, uView;
uniform vec2 uScreen;
uniform float uFrame, uRadius, uProjScale;
vec3 viewPos(vec2 uv) { float d = textureLod(uDepth, uv, 0.0).r; vec4 c = uInvProj * vec4(uv * 2.0 - 1.0, d * 2.0 - 1.0, 1.0); return c.xyz / c.w; }
void main() {
    float d = textureLod(uDepth, vUV, 0.0).r;
    if (d >= 1.0 || d < 0.03) { oAO = 1.0; return; }
    vec3 P = viewPos(vUV);
    vec3 N = normalize(mat3(uView) * (texture(uNormal, vUV).xyz * 2.0 - 1.0));
    vec3 V = normalize(-P);
    // 1.2 (interiors shimmered): the slice and step offsets from a 4 x 4 tile, not from a noise that runs on along
    // diagonals - every 4 x 4 window holds each of the sixteen once, so the blur after (4 x 4) averages the whole set and
    // the result is smooth and still from frame to frame; the frame only nudges the steps a little for the TAA to resolve
    const float BAYER[16] = float[](0.0, 8.0, 2.0, 10.0, 12.0, 4.0, 14.0, 6.0, 3.0, 11.0, 1.0, 9.0, 15.0, 7.0, 13.0, 5.0);
    ivec2 q = ivec2(gl_FragCoord.xy) & 3;
    float noiseA = (BAYER[q.y * 4 + q.x] + 0.5) / 16.0;
    float noiseB = fract((float((q.x + 2 * q.y) & 3) + 0.5) / 4.0 + mod(uFrame, 4.0) * 0.0625);
    // world-space radius projected to the screen, capped so near surfaces do not sample half the frame
    float rUV = clamp(uRadius * uProjScale / -P.z, 4.0 / uScreen.y, 0.12);
    const int SLICES = 2, STEPS = 4;
    float vis = 0.0;
    for (int s = 0; s < SLICES; s++) {
        float phi = (float(s) + noiseA) / float(SLICES) * PI;
        vec2 om = vec2(cos(phi), sin(phi));
        vec3 dirV = vec3(om, 0.0);
        vec3 ortho = dirV - dot(dirV, V) * V;
        vec3 axis = normalize(cross(dirV, V));
        vec3 projN = N - axis * dot(N, axis);
        float pl = length(projN);
        float sgn = sign(dot(ortho, projN));
        float cosN = clamp(dot(projN, V) / max(pl, 1e-4), 0.0, 1.0);
        float n = sgn * acos(cosN);
        for (int side = 0; side < 2; side++) {
            float hc = -1.0;
            float sd = float(side) * 2.0 - 1.0;
            for (int k = 0; k < STEPS; k++) {
                float st = (float(k) + noiseB) / float(STEPS);
                vec2 uv = vUV + sd * om * vec2(1.0, uScreen.x / uScreen.y) * rUV * st * st;
                vec3 sp = viewPos(clamp(uv, vec2(0.001), vec2(0.999)));
                vec3 dv = sp - P;
                float l = length(dv);
                float c = dot(dv / max(l, 1e-4), V);
                c = mix(c, -1.0, smoothstep(uRadius * 0.6, uRadius * 1.5, l));
                hc = max(hc, c);
            }
            float h = n + clamp(sd * acos(clamp(hc, -1.0, 1.0)) - n, -PI * 0.5, PI * 0.5);
            vis += pl * (cosN + 2.0 * h * sin(n) - cos(2.0 * h - n)) * 0.25;
        }
    }
    oAO = clamp(vis / float(SLICES), 0.0, 1.0);
}
