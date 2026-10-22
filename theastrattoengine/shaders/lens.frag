#include "include/head.glsl"
// 2.4: the lens - depth of field and motion blur, after the temporal pass (so the history it keeps stays sharp) and
// before bloom and exposure. Both are off unless the caller turns them on (ASTRATTO_PASS_DEPTH_OF_FIELD, _MOTION_BLUR).
in vec2 vUV;
layout(location=0) out vec4 oColor;
uniform sampler2D uColor, uDepth;
uniform mat4 uInvPV, uPrevPV;
uniform vec2 uScreen;
uniform float uShutter, uFocus, uAperture, uMaxBlur, uNear, uFar, uFrame;
uniform int uMotion, uDof;

float lin(float d) { return 2.0 * uNear * uFar / (uFar + uNear - (d * 2.0 - 1.0) * (uFar - uNear)); }
float ign(vec2 p) { return fract(52.9829189 * fract(dot(p, vec2(0.06711056, 0.00583715)))); }

// the circle of confusion (in screen heights) of a point at distance z, focused at f: a thin lens, scaled so that a
// point a metre out of focus at a metre blurs by the aperture (thousandths of the screen's height)
float coc(float z, float f) { return min(uAperture * 0.001 * abs(z - f) / max(z, 0.05) * max(f, 0.5), uMaxBlur * 0.001); }

void main() {
    vec3 col = texture(uColor, vUV).rgb;
    float d = texture(uDepth, vUV).r;
    float z = lin(d);
    float jit = ign(gl_FragCoord.xy + uFrame * 5.588);
    if (uDof != 0) {
        // the focus: as asked, or what is in the middle of the screen (five taps, the nearest that is not sky)
        float f = uFocus;
        if (f <= 0.0) {
            f = 1e9;
            for (int i = 0; i < 5; i++) {
                vec2 o = vec2(i == 1 ? 0.02 : i == 2 ? -0.02 : 0.0, i == 3 ? 0.02 : i == 4 ? -0.02 : 0.0);
                float dd = texture(uDepth, vec2(0.5) + o).r;
                if (dd < 1.0) f = min(f, lin(dd));
            }
            if (f > 1e8) f = 60.0;
        }
        float c0 = coc(z, f);
        // gather on a golden-angle spiral as wide as the widest blur near the point; each sample counts as far as its own
        // blur reaches the centre, so a sharp thing in front is not smeared over the blur behind it (and a blurred thing
        // in front does spill over what is sharp behind it)
        float aspect = uScreen.x / uScreen.y;
        float radius = max(c0, 0.0005);
        vec3 sum = col; float w = 1.0;
        if (c0 > 0.0008) {
            for (int i = 1; i < 32; i++) {
                float r = sqrt((float(i) + jit) / 32.0) * radius;
                float a = float(i) * 2.39996323 + jit * 6.2831;
                vec2 uv = vUV + vec2(cos(a) / aspect, sin(a)) * r;
                float zd = lin(texture(uDepth, uv).r);
                float cs = coc(zd, f);
                float k = smoothstep(r * 0.6, r, zd < z ? cs : min(cs, c0));
                sum += texture(uColor, uv).rgb * k; w += k;
            }
            col = sum / w;
        }
    }
    if (uMotion != 0) {
        // where this point was on the screen a frame ago (the camera's motion, from depth): the blur runs back along it
        vec4 wp = uInvPV * vec4(vUV * 2.0 - 1.0, d * 2.0 - 1.0, 1.0);
        wp /= wp.w;
        vec4 pc = uPrevPV * wp;
        vec2 prev = pc.xy / max(pc.w, 1e-4) * 0.5 + 0.5;
        vec2 vel = (vUV - prev) * uShutter;
        float len = length(vel * uScreen);
        if (len > 1.5 && pc.w > 0.0) {
            vel *= min(1.0, 40.0 / len);          // no more than 40 pixels of smear
            vec3 sum = col; float w = 1.0;
            for (int i = 0; i < 10; i++) {
                float t = (float(i) + jit) / 10.0 - 0.5;
                vec2 uv = vUV - vel * t;
                if (uv.x < 0.0 || uv.y < 0.0 || uv.x > 1.0 || uv.y > 1.0) continue;
                sum += texture(uColor, uv).rgb; w += 1.0;
            }
            col = sum / w;
        }
    }
    oColor = vec4(col, 1.0);
}
