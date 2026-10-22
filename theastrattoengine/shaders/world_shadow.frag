#include "include/head.glsl"
#include "include/world_tex.glsl"
void main() {
    vec3 bp;
    if ((vFlags & 8) != 0) { if (leafCardAlpha(vST) < 0.5) discard; return; }
    if (atlasTexel(bp).a < 0.5) discard;
}
