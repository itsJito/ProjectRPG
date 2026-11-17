uniform vec3 uWallCol, uFogOrigin;
uniform vec4 uWall;
// 0.4.7 (gamefixes: fog always at the edge of the map, hiding what lies past it) the world's edge {x0, z0, x1, z1}; over
// the last uEdgeBand metres before it the bank closes in whatever the weather, and past it nothing is seen
uniform vec4 uEdge;
uniform float uEdgeBand;
float edgeAt(vec3 P) {
    if (uEdgeBand <= 0.0) return 0.0;
    float d = max(max(uEdge.x - P.x, P.x - uEdge.z), max(uEdge.y - P.z, P.z - uEdge.w));   // < 0 inside, metres
    return smoothstep(-uEdgeBand, 0.0, d);
}
// how far the eye travels across the ground from cam along dir before it leaves the world (the sky past the edge)
float edgeSkyK(vec3 dir, vec3 cam) {
    if (uEdgeBand <= 0.0) return 0.0;
    vec2 d = dir.xz; float len = length(d);
    if (len < 1e-4) return 0.0;
    d /= len;
    float tx = d.x > 1e-4 ? (uEdge.z - cam.x) / d.x : d.x < -1e-4 ? (uEdge.x - cam.x) / d.x : 1e9;
    float tz = d.y > 1e-4 ? (uEdge.w - cam.z) / d.y : d.y < -1e-4 ? (uEdge.y - cam.z) / d.y : 1e9;
    float t = max(0.0, min(tx, tz));
    // a near edge veils the low sky toward it entirely, and less of it the higher one looks
    return (1.0 - smoothstep(uEdgeBand, uEdgeBand * 8.0, t)) * (1.0 - smoothstep(0.08, 0.6, dir.y));
}
float bankSkyK(vec3 dir) { return mix(uWall.z, uWall.w, smoothstep(0.02, 0.55, dir.y)); }
vec3 bankSky(vec3 dir) { return mix(skyColor(dir), uWallCol, bankSkyK(dir)); }
// 0.4.7: what the bank looks like from cam along dir, the world's edge counted: the world dissolving at the edge and the
// sky past it go to the one colour, so there is no seam where the ground ends
vec3 bankSkyAt(vec3 dir, vec3 cam) { return mix(skyColor(dir), uWallCol, max(bankSkyK(dir), edgeSkyK(dir, cam))); }
// 0.3.7 (gamefixes: the fog a circle round the player) the bank is a ring on the ground round its origin, measured across
// the ground only: what is near in plan is clear however high it stands, what is past the ring is gone at any height
float bankAt(vec3 P) { return max(smoothstep(uWall.x, uWall.y, length((P - uFogOrigin).xz)), edgeAt(P)); }
vec3 bankApply(vec3 col, vec3 P, vec3 cam) { float k = bankAt(P); return k <= 0.0 ? col : mix(col, bankSkyAt(normalize(P - cam), cam), k); }
