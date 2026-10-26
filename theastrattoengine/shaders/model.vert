#include "include/head.glsl"
layout(location=0) in vec3 aPos;
layout(location=1) in vec4 aCol;
layout(location=2) in vec3 aNormal;
uniform mat4 uPV, uModel;
uniform vec3 uFoliage;
uniform float uSway;
uniform int uBendCount;
uniform vec4 uBenders[16]; // x, feet y, z, radius; selected locally per foliage chunk (bodies, then their trails)
out vec4 vCol;
out vec3 vWorld;
out vec3 vN;
void main() {
    vec4 w = uModel * vec4(aPos, 1.0);
    if(uSway>0.5) {
        float height=clamp(aPos.y,0.0,1.5),phase=dot(w.xz,vec2(0.71,1.13));
        w.xz+=uFoliage.xy*height*height*(0.025+0.018*sin(uFoliage.z*1.9+phase));
        // 2.0.1: every vertex of a plant bends from the plant's root (packed in the vertex alpha by the chunk builder:
        // 2 + x * 16 + z * 16 * 256, chunk-local, 1/16 m), so the whole plant leans away from a body as one. Worked out
        // from each vertex's own position, a plant with someone standing in it was pushed outward on every side: it grew.
        vec2 root=w.xz;
        if(aCol.a>=1.5) {
            float code=aCol.a-2.0, rz=floor(code/256.0), rx=code-rz*256.0;
            root=(uModel*vec4(rx/16.0,0.0,rz/16.0,1.0)).xz;
        }
        vec2 off=vec2(0);
        float rise=max(aPos.y,0.0);
        for(int i=0;i<uBendCount;i++) {
            vec2 away=root-uBenders[i].xz;
            float distance=length(away), reach=uBenders[i].w+0.45;
            if(distance>=reach)continue;
            float level=1.0-smoothstep(0.6,1.6,abs(uModel[3].y-uBenders[i].y));
            // a trail point's radius shrinks as the stems recover, and so does its hold on them
            float hold=min(1.0,uBenders[i].w/0.25);
            vec2 dir=distance>0.03?away/distance:vec2(0.7071,0.7071);
            float k=1.0-distance/reach;
            off+=dir*(k*k*(3.0-2.0*k))*level*hold*min(rise*0.85,0.42);
        }
        // pressed over, not stretched: what moves sideways comes down, so a stem keeps its length
        float lean=length(off);
        if(lean>0.42)off*=0.42/lean;
        w.xz+=off;
        w.y-=min(rise*0.7,dot(off,off)/max(rise*1.4,0.10));
    }
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
