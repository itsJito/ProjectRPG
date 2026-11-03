#include "include/head.glsl"
layout(location=0) in vec4 iPosSize;     // world position, size
layout(location=1) in vec4 iColor;       // rgb (HDR), alpha
layout(location=2) in vec4 iParams;      // rotation, kind, age 0..1, seed
uniform mat4 uPV;
uniform vec3 uRight, uUp;
out vec2 vQ;
out vec4 vColor;
out vec4 vParams;
out float vClipW;
void main() {
    vec2 q = vec2(gl_VertexID & 1, (gl_VertexID >> 1) & 1) * 2.0 - 1.0;
    int kind = int(iParams.y + 0.5);
    float cr = cos(iParams.x), sr = sin(iParams.x);
    // a streak or a drop is long and thin: its width is a share of its length (carried where the others carry a seed)
    vec2 lq = (kind == 5 || kind == 7) ? vec2(q.x * iParams.w, q.y) : q;
    vec2 rq = vec2(lq.x * cr - lq.y * sr, lq.x * sr + lq.y * cr);
    // flames are taller than they are wide and stay upright
    vec3 up = kind == 0 ? vec3(0.0, 1.35, 0.0) : kind == 7 ? vec3(0.0, 1.0, 0.0) : uUp;
    vec3 p = iPosSize.xyz + (uRight * rq.x + up * rq.y) * iPosSize.w;
    // a ring lies on the ground whichever way the camera looks at it (and so does a splat of liquid, turned by its seed)
    if (kind == 4) p = iPosSize.xyz + vec3(q.x, 0.0, q.y) * iPosSize.w;
    if (kind == 10) p = iPosSize.xyz + vec3(q.x * cr - q.y * sr, 0.0, q.x * sr + q.y * cr) * iPosSize.w;
    // a drop of liquid in flight: drawn out along its own velocity (packed where the others carry rotation, seed and
    // alpha), a little longer the faster it goes, turned to face the eye about that line
    if (kind == 9) {
        vec3 vel = vec3(iParams.x, iColor.a, iParams.w);
        float speed = length(vel);
        vec3 dir = speed > 1e-3 ? vel / speed : vec3(0.0, -1.0, 0.0);
        vec3 view = normalize(cross(uRight, uUp));
        vec3 side = cross(dir, view);
        side = length(side) > 1e-3 ? normalize(side) : uRight;
        float stretch = 1.0 + min(speed * 0.09, 2.2);
        p = iPosSize.xyz + (dir * q.y * stretch + side * q.x) * iPosSize.w;
    }
    vQ = q; vColor = iColor; vParams = iParams;
    gl_Position = uPV * vec4(p, 1.0);
    vClipW = gl_Position.w;
}
