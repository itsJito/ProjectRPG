// Astratto Engine - Impulsis narrowphase: where two shapes touch.
//
//   box - box, box - cell     SAT over the fifteen axes, then the incident face clipped to the reference face (up to
//                             eight points, reduced to the four that hold a box best), or the closest points of two edges
//   sphere / capsule - x      closest points on a point or a segment (primitive tests), with both ends of a capsule
//                             lying along a face kept as contacts so that it rests instead of rocking
//   hull - x                  GJK for the distance and EPA for the depth, one point a step (the manifold keeps the
//                             others from the steps before)
//
// Every normal points from b toward a. A face of a cell with a solid cell beside it is never used: a body sliding over
// a floor of cells meets only the floor's top.
#include <cfloat>
#include <cmath>

#include "impulsis/space.h"

namespace astratto {
namespace impulsis {

namespace {

constexpr float BIG = 1e30f;

struct Obb { V3 c, ax[3], e; };

Obb obbOf(const Body& b) {
    Obb o;
    o.c = b.p;
    o.ax[0] = b.R.col(0); o.ax[1] = b.R.col(1); o.ax[2] = b.R.col(2);
    o.e = b.shape.half;
    return o;
}

Obb cellObb(int cx, int cy, int cz) {
    Obb o;
    o.c = V3(cx + 0.5f, cy + 0.5f, cz + 0.5f);
    o.ax[0] = V3(1, 0, 0); o.ax[1] = V3(0, 1, 0); o.ax[2] = V3(0, 0, 1);
    o.e = V3(0.5f, 0.5f, 0.5f);
    return o;
}

/** Whether a normal (from the cell toward the body) leaves the cell through one of its open faces. */
bool openNormal(V3 n, uint32_t open) {
    const V3 a = vabs(n);
    int axis = a.x >= a.y && a.x >= a.z ? 0 : a.y >= a.z ? 1 : 2;
    const int face = axis * 2 + (n[axis] > 0 ? 1 : 0);   // bit order: -x +x -y +y -z +z
    return (open >> face) & 1u;
}

void addContact(Manifold& m, const Body& a, const Body* b, V3 p, V3 n, float pen) {
    if (m.count >= 4) return;
    Contact& c = m.pts[m.count++];
    c = Contact();
    c.n = n;
    c.pen = pen;
    c.localA = a.toLocal(p);
    c.localB = b ? b->toLocal(p) : p;
    c.rA = p - a.p;
    c.rB = b ? p - b->p : V3();
}

/** Of up to many candidate points, keeps the four that best hold: the deepest, the furthest from it, then the two
 *  that make the largest area with those. */
void reduce(const V3* pts, const float* pens, int n, int* keep, int& kept) {
    if (n <= 4) { for (int i = 0; i < n; i++) keep[i] = i; kept = n; return; }
    int a = 0;
    for (int i = 1; i < n; i++) if (pens[i] > pens[a]) a = i;
    int b = -1; float best = -1;
    for (int i = 0; i < n; i++) { const float d = len2(pts[i] - pts[a]); if (d > best) { best = d; b = i; } }
    int c = -1; best = -1;
    for (int i = 0; i < n; i++) { if (i == a || i == b) continue; const float ar = len2(cross(pts[b] - pts[a], pts[i] - pts[a])); if (ar > best) { best = ar; c = i; } }
    int d = -1; best = -1;
    for (int i = 0; i < n; i++) {
        if (i == a || i == b || i == c) continue;
        const float ar = std::max(len2(cross(pts[b] - pts[a], pts[i] - pts[a])), std::max(len2(cross(pts[c] - pts[b], pts[i] - pts[b])), len2(cross(pts[a] - pts[c], pts[i] - pts[c]))));
        if (ar > best) { best = ar; d = i; }
    }
    kept = 0;
    for (int i : {a, b, c, d}) if (i >= 0) keep[kept++] = i;
}

/** Sutherland-Hodgman: keeps the part of a polygon with dot(p, n) <= d. */
int clip(const V3* in, int n, V3 nrm, float d, V3* out) {
    int k = 0;
    for (int i = 0; i < n; i++) {
        const V3 a = in[i], b = in[(i + 1) % n];
        const float da = dot(a, nrm) - d, db = dot(b, nrm) - d;
        if (da <= 0) out[k++] = a;
        if ((da <= 0) != (db <= 0)) out[k++] = a + (b - a) * (da / (da - db));
    }
    return k;
}

void segmentClosest(V3 p1, V3 q1, V3 p2, V3 q2, float& s, float& t) {
    const V3 d1 = q1 - p1, d2 = q2 - p2, r = p1 - p2;
    const float a = dot(d1, d1), e = dot(d2, d2), f = dot(d2, r);
    if (a <= 1e-12f && e <= 1e-12f) { s = t = 0; return; }
    if (a <= 1e-12f) { s = 0; t = std::clamp(f / e, 0.0f, 1.0f); return; }
    const float c = dot(d1, r);
    if (e <= 1e-12f) { t = 0; s = std::clamp(-c / a, 0.0f, 1.0f); return; }
    const float b = dot(d1, d2), den = a * e - b * b;
    s = den > 1e-12f ? std::clamp((b * f - c * e) / den, 0.0f, 1.0f) : 0.0f;
    t = (b * s + f) / e;
    if (t < 0) { t = 0; s = std::clamp(-c / a, 0.0f, 1.0f); }
    else if (t > 1) { t = 1; s = std::clamp((b - c) / a, 0.0f, 1.0f); }
}

/** The closest point of an oriented box to a point, and whether the point was inside. */
V3 boxClosest(const Obb& o, V3 p, bool& inside) {
    const V3 d = p - o.c;
    V3 q = o.c;
    inside = true;
    for (int i = 0; i < 3; i++) {
        float t = dot(d, o.ax[i]);
        if (t > o.e[i]) { t = o.e[i]; inside = false; }
        else if (t < -o.e[i]) { t = -o.e[i]; inside = false; }
        q += o.ax[i] * t;
    }
    return q;
}

/** A point inside a box: the face it is nearest to leave by (restricted to open faces of a cell). */
bool boxEscape(const Obb& o, V3 p, uint32_t open, V3& n, float& depth, V3& onFace) {
    const V3 d = p - o.c;
    float best = BIG; int bi = -1; float bs = 1;
    for (int i = 0; i < 3; i++) for (int s = -1; s <= 1; s += 2) {
        const int face = i * 2 + (s > 0 ? 1 : 0);
        if (!((open >> face) & 1u)) continue;
        const float dist = o.e[i] - s * dot(d, o.ax[i]);
        if (dist < best) { best = dist; bi = i; bs = static_cast<float>(s); }
    }
    if (bi < 0) return false;
    n = o.ax[bi] * bs;
    depth = best;
    onFace = p + n * best;
    return true;
}

// ------------------------------------------------------------------ box against box (SAT, clipping)
/** a: the body; o: its box; ob: the other box; other: the other body (null for a cell). open: the cell's open faces (all for a body). */
void boxBox(const Body& a, const Obb& oa, const Obb& ob, const Body* other, uint32_t open, Manifold& m) {
    const V3 t = ob.c - oa.c;
    // separations: the faces of a, the faces of b, the nine edge pairs
    float bestFace = -BIG, bestEdge = -BIG;
    int faceAxis = -1, edgeI = -1, edgeJ = -1;
    V3 faceN, edgeN;
    for (int i = 0; i < 6; i++) {
        const V3 L = i < 3 ? oa.ax[i] : ob.ax[i - 3];
        float ra = 0, rb = 0;
        for (int k = 0; k < 3; k++) { ra += oa.e[k] * std::fabs(dot(oa.ax[k], L)); rb += ob.e[k] * std::fabs(dot(ob.ax[k], L)); }
        const float dist = dot(t, L), sep = std::fabs(dist) - ra - rb;
        if (sep > SPECULATIVE) return;
        const V3 n = dist > 0 ? -L : L;          // from b toward a
        if (!other && !openNormal(n, open)) continue;
        if (sep > bestFace) { bestFace = sep; faceAxis = i; faceN = n; }
    }
    for (int i = 0; i < 3; i++) for (int j = 0; j < 3; j++) {
        V3 L = cross(oa.ax[i], ob.ax[j]);
        const float l = len(L);
        if (l < 1e-4f) continue;
        L = L / l;
        float ra = 0, rb = 0;
        for (int k = 0; k < 3; k++) { ra += oa.e[k] * std::fabs(dot(oa.ax[k], L)); rb += ob.e[k] * std::fabs(dot(ob.ax[k], L)); }
        const float dist = dot(t, L), sep = std::fabs(dist) - ra - rb;
        if (sep > SPECULATIVE) return;
        const V3 n = dist > 0 ? -L : L;
        if (!other && !openNormal(n, open)) continue;
        if (sep > bestEdge) { bestEdge = sep; edgeI = i; edgeJ = j; edgeN = n; }
    }
    if (faceAxis < 0 && edgeI < 0) return;
    const bool useEdge = edgeI >= 0 && (faceAxis < 0 || 0.95f * bestEdge > bestFace + 0.01f);
    if (useEdge) {
        // the edge of each box that reaches furthest toward the other
        const V3 n = edgeN;                       // b -> a
        V3 pa = oa.c, pb = ob.c;
        for (int k = 0; k < 3; k++) {
            if (k != edgeI) pa += oa.ax[k] * (oa.e[k] * (dot(oa.ax[k], n) > 0 ? -1.0f : 1.0f));
            if (k != edgeJ) pb += ob.ax[k] * (ob.e[k] * (dot(ob.ax[k], n) > 0 ? 1.0f : -1.0f));
        }
        const V3 a0 = pa - oa.ax[edgeI] * oa.e[edgeI], a1 = pa + oa.ax[edgeI] * oa.e[edgeI];
        const V3 b0 = pb - ob.ax[edgeJ] * ob.e[edgeJ], b1 = pb + ob.ax[edgeJ] * ob.e[edgeJ];
        float s, u;
        segmentClosest(a0, a1, b0, b1, s, u);
        const V3 ca = a0 + (a1 - a0) * s, cb = b0 + (b1 - b0) * u;
        addContact(m, a, other, (ca + cb) * 0.5f, n, -bestEdge);
        return;
    }
    // a face: of a (reference on a, incident on b) or of b
    const bool refA = faceAxis < 3;
    const Obb& ref = refA ? oa : ob;
    const Obb& inc = refA ? ob : oa;
    const V3 nRef = refA ? -faceN : faceN;     // the reference face's outward normal, toward the incident box
    const int k = refA ? faceAxis : faceAxis - 3;
    const V3 refC = ref.c + ref.ax[k] * (ref.e[k] * (dot(ref.ax[k], nRef) > 0 ? 1.0f : -1.0f));
    const int u = (k + 1) % 3, v = (k + 2) % 3;
    // the incident face: the one most against the reference normal
    int ik = 0; float most = BIG; float isg = 1;
    for (int i = 0; i < 3; i++) {
        const float d = dot(inc.ax[i], nRef);
        if (d < most) { most = d; ik = i; isg = 1; }
        if (-d < most) { most = -d; ik = i; isg = -1; }
    }
    const V3 ic = inc.c + inc.ax[ik] * (inc.e[ik] * isg);
    const int iu = (ik + 1) % 3, iv = (ik + 2) % 3;
    V3 poly[16], tmp[16];
    poly[0] = ic + inc.ax[iu] * inc.e[iu] + inc.ax[iv] * inc.e[iv];
    poly[1] = ic - inc.ax[iu] * inc.e[iu] + inc.ax[iv] * inc.e[iv];
    poly[2] = ic - inc.ax[iu] * inc.e[iu] - inc.ax[iv] * inc.e[iv];
    poly[3] = ic + inc.ax[iu] * inc.e[iu] - inc.ax[iv] * inc.e[iv];
    int n = 4;
    const V3 planes[4] = {ref.ax[u], -ref.ax[u], ref.ax[v], -ref.ax[v]};
    const float offs[4] = {dot(ref.ax[u], ref.c) + ref.e[u], -dot(ref.ax[u], ref.c) + ref.e[u], dot(ref.ax[v], ref.c) + ref.e[v], -dot(ref.ax[v], ref.c) + ref.e[v]};
    for (int pl = 0; pl < 4 && n > 0; pl++) { n = clip(poly, n, planes[pl], offs[pl], tmp); for (int i = 0; i < n; i++) poly[i] = tmp[i]; }
    V3 pts[16]; float pens[16]; int cnt = 0;
    for (int i = 0; i < n; i++) {
        const float sep = dot(poly[i] - refC, nRef);
        if (sep > SPECULATIVE) continue;
        pts[cnt] = poly[i] - nRef * (sep * 0.5f);   // halfway between the incident point and the reference face
        pens[cnt] = -sep;
        cnt++;
    }
    int keep[4], kept = 0;
    reduce(pts, pens, cnt, keep, kept);
    for (int i = 0; i < kept; i++) addContact(m, a, other, pts[keep[i]], faceN, pens[keep[i]]);
}

// ------------------------------------------------------------------ spheres and capsules
void capsuleSegment(const Body& b, V3& p0, V3& p1) {
    const V3 axis = b.R.col(1) * b.shape.halfHeight;
    p0 = b.p - axis; p1 = b.p + axis;
}

/** A sphere at c (radius r, belonging to a) against a box (a body's, or a cell's with its open faces). */
void sphereBox(const Body& a, V3 c, float r, const Obb& o, const Body* other, uint32_t open, Manifold& m) {
    bool inside;
    const V3 q = boxClosest(o, c, inside);
    V3 n; float pen; V3 at;
    if (!inside) {
        const V3 d = c - q;
        const float dl = len(d);
        if (dl - r > SPECULATIVE) return;
        n = dl > 1e-6f ? d / dl : V3(0, 1, 0);
        if (!other && !openNormal(n, open)) return;
        pen = r - dl;
        at = q;
    } else {
        if (!boxEscape(o, c, other ? 63u : open, n, pen, at)) return;
        pen += r;
    }
    addContact(m, a, other, at, n, pen);
}

/** Two capsules or a capsule and a sphere: the closest points of their segments; side by side, two contacts. */
void segSeg(const Body& a, V3 a0, V3 a1, float ra, const Body& b, V3 b0, V3 b1, float rb, Manifold& m) {
    float s, t;
    segmentClosest(a0, a1, b0, b1, s, t);
    const V3 pa = a0 + (a1 - a0) * s, pb = b0 + (b1 - b0) * t;
    V3 d = pa - pb;
    float dl = len(d);
    if (dl - ra - rb > SPECULATIVE) return;
    const V3 n = dl > 1e-6f ? d / dl : V3(0, 1, 0);
    addContact(m, a, &b, pb + n * (rb - (ra + rb - dl) * 0.5f), n, ra + rb - dl);
    // lying alongside: the ends too
    const V3 da = a1 - a0, db = b1 - b0;
    if (len2(da) > 1e-6f && len2(db) > 1e-6f && std::fabs(dot(norm(da), norm(db))) > 0.98f) {
        for (V3 e : {a0, a1}) {
            float tt = std::clamp(dot(e - b0, db) / len2(db), 0.0f, 1.0f);
            const V3 q = b0 + db * tt, dd = e - q;
            const float l = len(dd);
            if (l - ra - rb < SPECULATIVE && l > 1e-6f && len2(e - pa) > 0.01f) addContact(m, a, &b, q + dd / l * rb, dd / l, ra + rb - l);
        }
    }
}

/** A capsule's segment against a box: the closest point, and its ends where they lie on the box too. */
void capsuleBox(const Body& a, V3 p0, V3 p1, float r, const Obb& o, const Body* other, uint32_t open, Manifold& m) {
    // the distance from a box is convex along a segment: a ternary search finds its nearest point
    auto dist = [&](float s) {
        bool in = false;
        const V3 p = p0 + (p1 - p0) * s;
        const V3 q = boxClosest(o, p, in);
        if (!in) return len(p - q);
        // inside: minus the depth to the nearest face (signed distance)
        const V3 d = p - o.c;
        float depth = BIG;
        for (int k = 0; k < 3; k++) depth = std::min(depth, o.e[k] - std::fabs(dot(d, o.ax[k])));
        return -depth;
    };
    float lo = 0, hi = 1;
    for (int i = 0; i < 24; i++) { const float m1 = lo + (hi - lo) / 3, m2 = hi - (hi - lo) / 3; if (dist(m1) < dist(m2)) hi = m2; else lo = m1; }
    const float sBest = (lo + hi) * 0.5f;
    const int first = m.count;
    sphereBox(a, p0 + (p1 - p0) * sBest, r, o, other, open, m);
    if (m.count == first) return;
    const V3 n0 = m.pts[first].n;
    for (float s : {0.0f, 1.0f}) {
        if (std::fabs(s - sBest) < 0.15f) continue;
        const int c0 = m.count;
        sphereBox(a, p0 + (p1 - p0) * s, r, o, other, open, m);
        // only an end that rests on the same face as the nearest point helps (else it would twist the capsule)
        if (m.count > c0 && dot(m.pts[c0].n, n0) < 0.9f) m.count = c0;
    }
}

}  // namespace

// ------------------------------------------------------------------ shapes
V3 Shape::support(V3 d) const {
    switch (type) {
        case ASTRATTO_SHAPE_SPHERE: return norm(d) * radius;
        case ASTRATTO_SHAPE_CAPSULE: return V3(0, d.y >= 0 ? halfHeight : -halfHeight, 0) + norm(d) * radius;
        case ASTRATTO_SHAPE_HULL: {
            V3 best; float bd = -BIG;
            for (const V3& p : hull) { const float s = dot(p, d); if (s > bd) { bd = s; best = p; } }
            return best;
        }
        default: return V3(d.x >= 0 ? half.x : -half.x, d.y >= 0 ? half.y : -half.y, d.z >= 0 ? half.z : -half.z);
    }
}

V3 Shape::inertia(float m) const {
    switch (type) {
        case ASTRATTO_SHAPE_SPHERE: { const float i = 0.4f * m * radius * radius; return {i, i, i}; }
        case ASTRATTO_SHAPE_CAPSULE: {
            // a cylinder and two half spheres, by volume
            const float r = radius, h = halfHeight * 2, r2 = r * r;
            const float vc = 3.14159265f * r2 * h, vs = 4.0f / 3.0f * 3.14159265f * r2 * r, mc = m * vc / (vc + vs), ms = m - mc;
            const float iy = mc * r2 * 0.5f + ms * 0.4f * r2;
            const float ix = mc * (3 * r2 + h * h) / 12.0f + ms * (0.4f * r2 + h * h * 0.25f + 3.0f * h * r / 8.0f);
            return {ix, iy, ix};
        }
        case ASTRATTO_SHAPE_HULL: {
            V3 lo(BIG, BIG, BIG), hi(-BIG, -BIG, -BIG);
            for (const V3& p : hull) { lo = vmin(lo, p); hi = vmax(hi, p); }
            const V3 s = hi - lo;
            return V3(s.y * s.y + s.z * s.z, s.x * s.x + s.z * s.z, s.x * s.x + s.y * s.y) * (m / 12.0f);
        }
        default: {
            const V3 s = half * 2.0f;
            return V3(s.y * s.y + s.z * s.z, s.x * s.x + s.z * s.z, s.x * s.x + s.y * s.y) * (m / 12.0f);
        }
    }
}

void Shape::finish() {
    switch (type) {
        case ASTRATTO_SHAPE_SPHERE: boundRadius = radius; break;
        case ASTRATTO_SHAPE_CAPSULE: boundRadius = radius + halfHeight; break;
        case ASTRATTO_SHAPE_HULL: boundRadius = 0; for (const V3& p : hull) boundRadius = std::max(boundRadius, len(p)); break;
        default: boundRadius = len(half); break;
    }
}

// ------------------------------------------------------------------ GJK and EPA
namespace {

struct SV { V3 w, a, b; };   // a point of the Minkowski difference and the two points it came from

SV supportOf(const Body& A, const Body& B, V3 d) {
    SV s;
    s.a = A.p + A.R * A.shape.support(transpose(A.R) * d);
    s.b = B.p + B.R * B.shape.support(transpose(B.R) * -d);
    s.w = s.a - s.b;
    return s;
}

/** The closest point to the origin on a simplex; reduces it to the points that carry it, with their weights. */
V3 closestOnSimplex(SV* s, int& n, float* bary) {
    if (n == 1) { bary[0] = 1; return s[0].w; }
    if (n == 2) {
        const V3 a = s[0].w, b = s[1].w, ab = b - a;
        const float t = std::clamp(-dot(a, ab) / std::max(1e-12f, len2(ab)), 0.0f, 1.0f);
        if (t <= 0) { n = 1; bary[0] = 1; return a; }
        if (t >= 1) { s[0] = s[1]; n = 1; bary[0] = 1; return b; }
        bary[0] = 1 - t; bary[1] = t;
        return a + ab * t;
    }
    if (n == 3) {
        // Real-Time Collision Detection, ClosestPtPointTriangle, with p at the origin
        const V3 a = s[0].w, b = s[1].w, c = s[2].w, ab = b - a, ac = c - a, ap = -a;
        const float d1 = dot(ab, ap), d2 = dot(ac, ap);
        if (d1 <= 0 && d2 <= 0) { n = 1; bary[0] = 1; return a; }
        const V3 bp = -b; const float d3 = dot(ab, bp), d4 = dot(ac, bp);
        if (d3 >= 0 && d4 <= d3) { s[0] = s[1]; n = 1; bary[0] = 1; return b; }
        const float vc = d1 * d4 - d3 * d2;
        if (vc <= 0 && d1 >= 0 && d3 <= 0) { const float v = d1 / (d1 - d3); n = 2; bary[0] = 1 - v; bary[1] = v; return a + ab * v; }
        const V3 cp = -c; const float d5 = dot(ab, cp), d6 = dot(ac, cp);
        if (d6 >= 0 && d5 <= d6) { s[0] = s[2]; n = 1; bary[0] = 1; return c; }
        const float vb = d5 * d2 - d1 * d6;
        if (vb <= 0 && d2 >= 0 && d6 <= 0) { const float w = d2 / (d2 - d6); s[1] = s[2]; n = 2; bary[0] = 1 - w; bary[1] = w; return a + ac * w; }
        const float va = d3 * d6 - d5 * d4;
        if (va <= 0 && (d4 - d3) >= 0 && (d5 - d6) >= 0) { const float w = (d4 - d3) / ((d4 - d3) + (d5 - d6)); s[0] = s[1]; s[1] = s[2]; n = 2; bary[0] = 1 - w; bary[1] = w; return b + (c - b) * w; }
        const float den = 1.0f / (va + vb + vc), v = vb * den, w = vc * den;
        bary[0] = 1 - v - w; bary[1] = v; bary[2] = w;
        return a + ab * v + ac * w;
    }
    // a tetrahedron: the origin inside it, or the closest of the faces it is outside of
    float best = BIG; SV bs[3]; int bn = 0; float bb[3] = {}; V3 bp;
    const int faces[4][4] = {{0, 1, 2, 3}, {0, 3, 1, 2}, {0, 2, 3, 1}, {1, 3, 2, 0}};
    bool outside = false;
    for (const auto& f : faces) {
        const V3 a = s[f[0]].w, b = s[f[1]].w, c = s[f[2]].w, d = s[f[3]].w;
        V3 nrm = cross(b - a, c - a);
        if (dot(nrm, d - a) > 0) nrm = -nrm;
        if (dot(nrm, -a) <= 0) continue;          // the origin is on the inner side of this face
        outside = true;
        SV tri[3] = {s[f[0]], s[f[1]], s[f[2]]};
        int tn = 3; float tb[3];
        const V3 p = closestOnSimplex(tri, tn, tb);
        const float dd = len2(p);
        if (dd < best) { best = dd; bn = tn; for (int i = 0; i < tn; i++) { bs[i] = tri[i]; bb[i] = tb[i]; } bp = p; }
    }
    if (!outside) { n = 4; return V3(); }
    n = bn;
    for (int i = 0; i < bn; i++) { s[i] = bs[i]; bary[i] = bb[i]; }
    return bp;
}

struct Face { int a, b, c; V3 n; float d; bool live; };

/** EPA: grows a polytope inside the Minkowski difference until its face nearest the origin is on the boundary. */
bool epa(const Body& A, const Body& B, SV* simplex, V3& pa, V3& pb, V3& nOut, float& depth) {
    std::vector<SV> verts(simplex, simplex + 4);
    std::vector<Face> faces;
    auto addFace = [&](int a, int b, int c) {
        Face f{a, b, c, cross(verts[static_cast<size_t>(b)].w - verts[static_cast<size_t>(a)].w, verts[static_cast<size_t>(c)].w - verts[static_cast<size_t>(a)].w), 0, true};
        const float l = len(f.n);
        if (l < 1e-10f) { f.live = false; faces.push_back(f); return; }
        f.n = f.n / l;
        f.d = dot(f.n, verts[static_cast<size_t>(a)].w);
        if (f.d < 0) { std::swap(f.b, f.c); f.n = -f.n; f.d = -f.d; }
        faces.push_back(f);
    };
    addFace(0, 1, 2); addFace(0, 3, 1); addFace(0, 2, 3); addFace(1, 3, 2);
    for (int it = 0; it < 48; it++) {
        int best = -1; float bd = BIG;
        for (size_t i = 0; i < faces.size(); i++) if (faces[i].live && faces[i].d < bd) { bd = faces[i].d; best = static_cast<int>(i); }
        if (best < 0) return false;
        const Face f = faces[static_cast<size_t>(best)];
        const SV s = supportOf(A, B, f.n);
        const float grow = dot(s.w, f.n) - f.d;
        if (grow < 1e-4f || it == 47) {
            // the origin's projection on this face, as weights of its corners
            const V3 a = verts[static_cast<size_t>(f.a)].w, b = verts[static_cast<size_t>(f.b)].w, c = verts[static_cast<size_t>(f.c)].w, p = f.n * f.d;
            const V3 v0 = b - a, v1 = c - a, v2 = p - a;
            const float d00 = dot(v0, v0), d01 = dot(v0, v1), d11 = dot(v1, v1), d20 = dot(v2, v0), d21 = dot(v2, v1), den = d00 * d11 - d01 * d01;
            float v = 0, w = 0;
            if (std::fabs(den) > 1e-14f) { v = (d11 * d20 - d01 * d21) / den; w = (d00 * d21 - d01 * d20) / den; }
            const float u = 1 - v - w;
            pa = verts[static_cast<size_t>(f.a)].a * u + verts[static_cast<size_t>(f.b)].a * v + verts[static_cast<size_t>(f.c)].a * w;
            pb = verts[static_cast<size_t>(f.a)].b * u + verts[static_cast<size_t>(f.b)].b * v + verts[static_cast<size_t>(f.c)].b * w;
            nOut = -f.n;                              // from b toward a
            depth = f.d;
            return true;
        }
        // remove the faces the new point sees, keep their horizon, close it with new faces
        const int vi = static_cast<int>(verts.size());
        verts.push_back(s);
        std::vector<std::pair<int, int>> edges;
        for (Face& g : faces) {
            if (!g.live || dot(g.n, s.w - verts[static_cast<size_t>(g.a)].w) <= 0) continue;
            g.live = false;
            for (auto e : {std::make_pair(g.a, g.b), std::make_pair(g.b, g.c), std::make_pair(g.c, g.a)}) {
                bool shared = false;
                for (size_t k = 0; k < edges.size(); k++) if (edges[k].first == e.second && edges[k].second == e.first) { edges.erase(edges.begin() + static_cast<long>(k)); shared = true; break; }
                if (!shared) edges.push_back(e);
            }
        }
        for (auto& e : edges) addFace(e.first, e.second, vi);
        if (faces.size() > 512) return false;
    }
    return false;
}

}  // namespace

bool gjkEpa(const Body& A, const Body& B, V3& pa, V3& pb, V3& n, float& depth) {
    SV s[4]; int k = 0; float bary[4] = {1, 0, 0, 0};
    V3 d = B.p - A.p;
    if (len2(d) < 1e-10f) d = V3(1, 0, 0);
    s[k++] = supportOf(A, B, -d);
    V3 v = s[0].w;
    for (int it = 0; it < 40; it++) {
        if (len2(v) < 1e-12f) break;
        const SV w = supportOf(A, B, -v);
        if (len2(v) - dot(v, w.w) <= 1e-6f * std::max(1.0f, len2(v))) {
            // converged, apart: the closest points
            pa = V3(); pb = V3();
            for (int i = 0; i < k; i++) { pa += s[i].a * bary[i]; pb += s[i].b * bary[i]; }
            const V3 sep = pa - pb;
            const float l = len(sep);
            n = l > 1e-9f ? sep / l : V3(0, 1, 0);
            depth = -l;
            return false;
        }
        s[k++] = w;
        v = closestOnSimplex(s, k, bary);
        if (k == 4) break;
    }
    // overlapping: make the simplex a tetrahedron, then expand it
    const V3 dirs[6] = {V3(1, 0, 0), V3(-1, 0, 0), V3(0, 1, 0), V3(0, -1, 0), V3(0, 0, 1), V3(0, 0, -1)};
    for (int i = 0; k < 4 && i < 6; i++) {
        const SV w = supportOf(A, B, dirs[i]);
        bool dup = false;
        for (int j = 0; j < k; j++) if (len2(s[j].w - w.w) < 1e-10f) dup = true;
        if (dup) continue;
        if (k == 2 && len2(cross(s[1].w - s[0].w, w.w - s[0].w)) < 1e-10f) continue;
        if (k == 3 && std::fabs(dot(cross(s[1].w - s[0].w, s[2].w - s[0].w), w.w - s[0].w)) < 1e-10f) continue;
        s[k++] = w;
    }
    if (k < 4) return false;
    return epa(A, B, s, pa, pb, n, depth);
}

// ------------------------------------------------------------------ dispatch
namespace {

void hullAny(const Body& a, const Body& b, Manifold& m) {
    V3 pa, pb, n; float depth;
    const bool overlap = gjkEpa(a, b, pa, pb, n, depth);
    if (!overlap && -depth > SPECULATIVE) return;
    addContact(m, a, &b, (pa + pb) * 0.5f, n, overlap ? depth : depth);
}

}  // namespace

void collide(const Body& a, const Body& b, Manifold& m) {
    m.count = 0;
    const uint32_t ta = a.shape.type, tb = b.shape.type;
    if (ta == ASTRATTO_SHAPE_HULL || tb == ASTRATTO_SHAPE_HULL) { hullAny(a, b, m); return; }
    if (ta == ASTRATTO_SHAPE_BOX && tb == ASTRATTO_SHAPE_BOX) { boxBox(a, obbOf(a), obbOf(b), &b, 63u, m); return; }
    // spheres and capsules as segments with a radius (a sphere is a segment of no length)
    auto seg = [](const Body& x, V3& p0, V3& p1) { if (x.shape.type == ASTRATTO_SHAPE_CAPSULE) capsuleSegment(x, p0, p1); else p0 = p1 = x.p; };
    if (ta != ASTRATTO_SHAPE_BOX && tb != ASTRATTO_SHAPE_BOX) {
        V3 a0, a1, b0, b1;
        seg(a, a0, a1); seg(b, b0, b1);
        segSeg(a, a0, a1, a.shape.radius, b, b0, b1, b.shape.radius, m);
        return;
    }
    if (tb == ASTRATTO_SHAPE_BOX) {
        V3 p0, p1;
        seg(a, p0, p1);
        if (ta == ASTRATTO_SHAPE_SPHERE) sphereBox(a, a.p, a.shape.radius, obbOf(b), &b, 63u, m);
        else capsuleBox(a, p0, p1, a.shape.radius, obbOf(b), &b, 63u, m);
        return;
    }
    // a is the box: solve it the other way round, then turn every contact about
    Manifold r;
    collide(b, a, r);
    m.count = 0;
    for (int i = 0; i < r.count; i++) {
        const Contact& c = r.pts[i];
        const V3 p = b.toWorld(c.localA);
        addContact(m, a, &b, p, -c.n, c.pen);
    }
}

void collideCell(const Body& a, int cx, int cy, int cz, uint32_t open, Manifold& m) {
    m.count = 0;
    const Obb cell = cellObb(cx, cy, cz);
    switch (a.shape.type) {
        case ASTRATTO_SHAPE_BOX: boxBox(a, obbOf(a), cell, nullptr, open, m); break;
        case ASTRATTO_SHAPE_SPHERE: sphereBox(a, a.p, a.shape.radius, cell, nullptr, open, m); break;
        case ASTRATTO_SHAPE_CAPSULE: { V3 p0, p1; capsuleSegment(a, p0, p1); capsuleBox(a, p0, p1, a.shape.radius, cell, nullptr, open, m); break; }
        default: {
            // a hull against the cell: GJK/EPA finds the normal and depth; then every corner of the hull that lies
            // within reach of the cell's face along that normal is a contact (so a flat shard lies flat)
            Body c;
            c.p = cell.c; c.q = Q(); c.update(); c.shape.type = ASTRATTO_SHAPE_BOX; c.shape.half = cell.e;
            V3 pa, pb, n; float depth;
            const bool overlap = gjkEpa(a, c, pa, pb, n, depth);
            if (!overlap && -depth > SPECULATIVE) break;
            if (!openNormal(n, open)) break;
            // the face plane of the cell the normal leaves by
            const V3 an = vabs(n);
            const int axis = an.x >= an.y && an.x >= an.z ? 0 : an.y >= an.z ? 1 : 2;
            V3 fn; fn[axis] = n[axis] > 0 ? 1.0f : -1.0f;
            const float plane = dot(fn, cell.c) + 0.5f;
            V3 pts[64]; float pens[64]; int cnt = 0;
            for (const V3& lp : a.shape.hull) {
                if (cnt >= 64) break;
                const V3 wp = a.toWorld(lp);
                const float sep = dot(fn, wp) - plane;
                if (sep > SPECULATIVE) continue;
                // within the face's square
                const V3 off = wp - cell.c;
                bool over = true;
                for (int k = 0; k < 3; k++) if (k != axis && std::fabs(off[k]) > 0.5f + 1e-3f) over = false;
                if (!over) continue;
                pts[cnt] = wp - fn * (sep * 0.5f); pens[cnt] = -sep; cnt++;
            }
            if (cnt == 0) { addContact(m, a, nullptr, (pa + pb) * 0.5f, n, overlap ? depth : depth); break; }
            int keep[4], kept = 0;
            reduce(pts, pens, cnt, keep, kept);
            for (int i = 0; i < kept; i++) addContact(m, a, nullptr, pts[keep[i]], fn, pens[keep[i]]);
            break;
        }
    }
    for (int i = 0; i < m.count; i++) m.pts[i].localB = a.toWorld(m.pts[i].localA);
}

}  // namespace impulsis
}  // namespace astratto
