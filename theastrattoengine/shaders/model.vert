#include "include/head.glsl"
layout(location=0) in vec3 aPos;
layout(location=1) in vec4 aCol;
layout(location=2) in vec3 aNormal;
uniform mat4 uPV, uModel;
uniform vec3 uFoliage;
uniform float uSway;
uniform vec3 uVoxelOrigin;
uniform int uBendCount;
uniform vec4 uBenders[16]; // x, feet y, z, radius; selected locally per foliage chunk (bodies, then their trails)
out vec4 vCol;
out vec3 vWorld;
out vec3 vN;
out vec3 vCell;
void main() {
    vec4 w = uModel * vec4(aPos, 1.0);
    // The reserved marker (65538) keeps wall growth rigid without making its shared mesh translucent.
    // 2.7 (the game's 0.3.5): a root code at or above 131072 marks a stiff plant - a bush, a sapling, a garden crop -
    // which gives far less to the wind and to a body than grass and fern do.
    bool rigid = aCol.a >= 65538.0 && aCol.a < 131072.0;
    if(uSway>0.5 && !rigid) {
        float stiff = aCol.a >= 131072.0 ? 1.0 : 0.0;
        float code = aCol.a - stiff * 131072.0;
        // 2.0.1: every vertex of a plant bends from the plant's root (packed in the vertex alpha by the mesh builder:
        // 2 + x * 16 + z * 16 * 256, mesh-local, 1/16 m), so the whole plant moves as one.
        vec2 root=w.xz;
        if(code>=1.5) {
            float c=code-2.0, rz=floor(c/256.0), rx=c-rz*256.0;
            root=(uModel*vec4(rx/16.0,0.0,rz/16.0,1.0)).xz;
        }
        float rise=max(aPos.y,0.0);
        // the wind: (2.7) the phase is the plant's, not each vertex's, so a stalk sways as a stalk rather than its
        // leaves shivering apart; a stiff plant barely moves
        float height=clamp(rise,0.0,1.5),phase=dot(root,vec2(0.71,1.13));
        w.xz+=uFoliage.xy*height*height*(0.025+0.018*sin(uFoliage.z*1.9+phase))*mix(1.0,0.3,stiff);
        // a body among the stems turns the plant over about its root (2.7, as foliage.vert does since 1.3): a rotation,
        // so every leaf keeps its length - the old sideways shove of each vertex by its height sheared a plant out of
        // shape and pressed it into the ground in steps as a foot came and went
        vec2 lean=vec2(0);
        float crush=0.0;
        for(int i=0;i<uBendCount;i++) {
            vec2 away=root-uBenders[i].xz;
            float distance=length(away), reach=uBenders[i].w+0.45;
            if(distance>=reach)continue;
            float level=1.0-smoothstep(0.6,1.6,abs(uModel[3].y-uBenders[i].y));
            // a trail point's radius shrinks as the stems recover, and so does its hold on them
            float hold=min(1.0,uBenders[i].w/0.25);
            vec2 dir=distance>0.03?away/distance:vec2(0.7071,0.7071);
            float k=1.0-distance/reach;
            lean+=dir*(k*k*(3.0-2.0*k))*level*hold;
            crush=max(crush,(1.0-smoothstep(0.0,0.6,distance/reach))*level*hold);
        }
        float ll=length(lean);
        if(ll>1e-4) {
            vec2 ld=lean/ll;
            float give=mix(1.0,0.32,stiff);
            float theta=min(ll,1.0)*(0.62+0.5*crush)*give*smoothstep(0.0,0.12,rise+0.02);
            vec2 rel=w.xz-root;
            float h=dot(rel,ld), cs=cos(theta), sn=sin(theta);
            float h2=h*cs+rise*sn, y2=rise*cs-h*sn;
            w.xz+=ld*(h2-h);
            w.y+=max(y2,0.015)-rise;
        }
    }
    vCell = aPos * 64.0 + uVoxelOrigin - aNormal * 0.01;
    vWorld = w.xyz;
    vCol = uSway>0.5 ? vec4(aCol.rgb,1.0) : aCol;
    // 1.0: normals go through the normal matrix (the inverse-transpose of the model matrix), not the model matrix: a
    // limb that is stretched, a prop that is squashed or a mirrored part would otherwise be lit as if it were not.
    // The cofactor matrix is that inverse-transpose times the determinant - no division, so a flattened model cannot
    // produce NaNs - and the determinant's sign puts a mirrored model's normals back the right way round.
    mat3 m = mat3(uModel);
    mat3 cof = mat3(cross(m[1], m[2]), cross(m[2], m[0]), cross(m[0], m[1]));
    vN = cof * aNormal * (dot(m[0], cof[0]) < 0.0 ? -1.0 : 1.0);
    gl_Position = uPV * w;
}
