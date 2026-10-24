#include "include/head.glsl"
in vec4 vCol;
in vec3 vN;
uniform vec4 uTint;
layout(location=0) out vec4 color;
void main() {
    float light = 0.66 + 0.34 * max(0.0, dot(normalize(vN), normalize(vec3(-0.4, 0.8, -1.0))));
    color = vec4(vCol.rgb * uTint.rgb * light, vCol.a * uTint.a);
    if (color.a < 0.01) discard;
}
