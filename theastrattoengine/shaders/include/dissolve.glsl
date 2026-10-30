// Stable local voxel identity: every face of a voxel disappears together in all passes.
in vec3 vCell;
uniform float uDissolve;
float dissolveEdge() {
    if(uDissolve<=0.0)return 1.0;
    vec3 p=fract(floor(vCell)*vec3(0.1031,0.11369,0.13787));
    p+=dot(p,p.yzx+19.19);
    float cell=fract((p.x+p.y)*p.z);
    if(uDissolve>=1.0 || cell<uDissolve)discard;
    return smoothstep(0.0,0.075,cell-uDissolve);
}
