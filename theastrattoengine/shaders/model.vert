#include "include/head.glsl"
layout(location=0) in vec3 aPos;
layout(location=1) in vec4 aCol;
layout(location=2) in vec3 aNormal;
uniform mat4 uPV, uModel;
uniform vec3 uFoliage;
uniform float uSway;
uniform int uBendCount;
uniform vec4 uBenders[8]; // x, feet y, z, radius; selected locally per foliage chunk
out vec4 vCol;
out vec3 vWorld;
out vec3 vN;
void main() {
    vec4 w = uModel * vec4(aPos, 1.0);
    if(uSway>0.5) {
        float height=clamp(aPos.y,0.0,1.5),phase=dot(w.xz,vec2(0.71,1.13));
        w.xz+=uFoliage.xy*height*height*(0.025+0.018*sin(uFoliage.z*1.9+phase));
        vec2 bend=vec2(0);
        for(int i=0;i<uBendCount;i++) {
            vec2 away=w.xz-uBenders[i].xz;
            float distance=length(away), reach=uBenders[i].w+0.65;
            float contact=1.0-smoothstep(uBenders[i].w*0.4,reach,distance);
            float level=1.0-smoothstep(0.6,1.6,abs(uModel[3].y-uBenders[i].y));
            bend+=away/max(distance,0.08)*contact*level;
        }
        float amount=min(length(bend),1.0);
        w.xz+=bend/max(length(bend),1.0)*height*0.55;
        w.y-=height*amount*0.2;
    }
    vWorld = w.xyz;
    vCol = aCol;
    // 1.0: normals go through the normal matrix (the inverse-transpose of the model matrix), not the model matrix: a
    // limb that is stretched, a prop that is squashed or a mirrored part would otherwise be lit as if it were not.
    // The cofactor matrix is that inverse-transpose times the determinant - no division, so a flattened model cannot
    // produce NaNs - and the determinant's sign puts a mirrored model's normals back the right way round.
    mat3 m = mat3(uModel);
    mat3 cof = mat3(cross(m[1], m[2]), cross(m[2], m[0]), cross(m[0], m[1]));
    vN = cof * aNormal * (dot(m[0], cof[0]) < 0.0 ? -1.0 : 1.0);
    gl_Position = uPV * w;
}
