// Astratto Engine - Impulsis: the small vector, quaternion and matrix kit the rigid-body solver is written in.
#pragma once

#include <algorithm>
#include <cmath>

namespace astratto {
namespace impulsis {

struct V3 {
    float x = 0, y = 0, z = 0;
    V3() = default;
    constexpr V3(float a, float b, float c) : x(a), y(b), z(c) {}
    float operator[](int i) const { return i == 0 ? x : i == 1 ? y : z; }
    float& operator[](int i) { return i == 0 ? x : i == 1 ? y : z; }
};
inline V3 operator+(V3 a, V3 b) { return {a.x + b.x, a.y + b.y, a.z + b.z}; }
inline V3 operator-(V3 a, V3 b) { return {a.x - b.x, a.y - b.y, a.z - b.z}; }
inline V3 operator-(V3 a) { return {-a.x, -a.y, -a.z}; }
inline V3 operator*(V3 a, float s) { return {a.x * s, a.y * s, a.z * s}; }
inline V3 operator*(float s, V3 a) { return {a.x * s, a.y * s, a.z * s}; }
inline V3 operator/(V3 a, float s) { return {a.x / s, a.y / s, a.z / s}; }
inline V3& operator+=(V3& a, V3 b) { a.x += b.x; a.y += b.y; a.z += b.z; return a; }
inline V3& operator-=(V3& a, V3 b) { a.x -= b.x; a.y -= b.y; a.z -= b.z; return a; }
inline V3& operator*=(V3& a, float s) { a.x *= s; a.y *= s; a.z *= s; return a; }
inline float dot(V3 a, V3 b) { return a.x * b.x + a.y * b.y + a.z * b.z; }
inline V3 cross(V3 a, V3 b) { return {a.y * b.z - a.z * b.y, a.z * b.x - a.x * b.z, a.x * b.y - a.y * b.x}; }
inline float len2(V3 a) { return dot(a, a); }
inline float len(V3 a) { return std::sqrt(dot(a, a)); }
inline V3 norm(V3 a) { const float l = len(a); return l > 1e-12f ? a / l : V3(0, 1, 0); }
inline V3 vmin(V3 a, V3 b) { return {std::min(a.x, b.x), std::min(a.y, b.y), std::min(a.z, b.z)}; }
inline V3 vmax(V3 a, V3 b) { return {std::max(a.x, b.x), std::max(a.y, b.y), std::max(a.z, b.z)}; }
inline V3 vabs(V3 a) { return {std::fabs(a.x), std::fabs(a.y), std::fabs(a.z)}; }
inline V3 mul(V3 a, V3 b) { return {a.x * b.x, a.y * b.y, a.z * b.z}; }

/** Two unit vectors that, with n, make a right-handed frame. */
inline void basis(V3 n, V3& t1, V3& t2) {
    if (std::fabs(n.x) >= 0.57735f) t1 = norm(V3(n.y, -n.x, 0));
    else t1 = norm(V3(0, n.z, -n.y));
    t2 = cross(n, t1);
}

/** A 3 x 3 matrix, rows. */
struct M3 {
    V3 r[3] = {V3(1, 0, 0), V3(0, 1, 0), V3(0, 0, 1)};
    static M3 diag(V3 d) { M3 m; m.r[0] = {d.x, 0, 0}; m.r[1] = {0, d.y, 0}; m.r[2] = {0, 0, d.z}; return m; }
    static M3 zero() { M3 m; m.r[0] = m.r[1] = m.r[2] = V3(); return m; }
    V3 col(int i) const { return {r[0][i], r[1][i], r[2][i]}; }
};
inline V3 operator*(const M3& m, V3 v) { return {dot(m.r[0], v), dot(m.r[1], v), dot(m.r[2], v)}; }
inline M3 transpose(const M3& m) { M3 t; t.r[0] = m.col(0); t.r[1] = m.col(1); t.r[2] = m.col(2); return t; }
inline M3 operator*(const M3& a, const M3& b) {
    M3 o; const M3 bt = transpose(b);
    for (int i = 0; i < 3; i++) o.r[i] = {dot(a.r[i], bt.r[0]), dot(a.r[i], bt.r[1]), dot(a.r[i], bt.r[2])};
    return o;
}
inline M3 operator+(const M3& a, const M3& b) { M3 o; for (int i = 0; i < 3; i++) o.r[i] = a.r[i] + b.r[i]; return o; }
/** [v]x, the matrix of a cross product with v. */
inline M3 skew(V3 v) { M3 m; m.r[0] = {0, -v.z, v.y}; m.r[1] = {v.z, 0, -v.x}; m.r[2] = {-v.y, v.x, 0}; return m; }
inline float det(const M3& m) { return dot(m.r[0], cross(m.r[1], m.r[2])); }
inline M3 inverse(const M3& m) {
    const float d = det(m);
    if (std::fabs(d) < 1e-20f) return M3::zero();
    const V3 c0 = cross(m.r[1], m.r[2]), c1 = cross(m.r[2], m.r[0]), c2 = cross(m.r[0], m.r[1]);
    M3 inv; inv.r[0] = {c0.x, c1.x, c2.x}; inv.r[1] = {c0.y, c1.y, c2.y}; inv.r[2] = {c0.z, c1.z, c2.z};
    for (auto& row : inv.r) row *= 1.0f / d;
    return inv;
}

struct Q {
    float w = 1, x = 0, y = 0, z = 0;
    Q() = default;
    constexpr Q(float a, float b, float c, float d) : w(a), x(b), y(c), z(d) {}
};
inline Q operator*(Q a, Q b) {
    return {a.w * b.w - a.x * b.x - a.y * b.y - a.z * b.z, a.w * b.x + a.x * b.w + a.y * b.z - a.z * b.y,
            a.w * b.y - a.x * b.z + a.y * b.w + a.z * b.x, a.w * b.z + a.x * b.y - a.y * b.x + a.z * b.w};
}
inline Q conj(Q q) { return {q.w, -q.x, -q.y, -q.z}; }
inline Q qnorm(Q q) {
    const float l = std::sqrt(q.w * q.w + q.x * q.x + q.y * q.y + q.z * q.z);
    return l > 1e-12f ? Q(q.w / l, q.x / l, q.y / l, q.z / l) : Q();
}
inline Q axisAngle(V3 axis, float a) { const V3 n = norm(axis); const float s = std::sin(a * 0.5f); return {std::cos(a * 0.5f), n.x * s, n.y * s, n.z * s}; }
inline V3 rotate(Q q, V3 v) {
    const V3 u(q.x, q.y, q.z);
    const V3 t = cross(u, v) * 2.0f;
    return v + t * q.w + cross(u, t);
}
inline M3 toM3(Q q) {
    const float xx = q.x * q.x, yy = q.y * q.y, zz = q.z * q.z, xy = q.x * q.y, xz = q.x * q.z, yz = q.y * q.z, wx = q.w * q.x, wy = q.w * q.y, wz = q.w * q.z;
    M3 m;
    m.r[0] = {1 - 2 * (yy + zz), 2 * (xy - wz), 2 * (xz + wy)};
    m.r[1] = {2 * (xy + wz), 1 - 2 * (xx + zz), 2 * (yz - wx)};
    m.r[2] = {2 * (xz - wy), 2 * (yz + wx), 1 - 2 * (xx + yy)};
    return m;
}
/** Integrates an orientation by an angular velocity over dt (the first-order quaternion derivative, normalised). */
inline Q integrate(Q q, V3 w, float dt) {
    const Q dq = Q(0, w.x, w.y, w.z) * q;
    return qnorm(Q(q.w + dq.w * 0.5f * dt, q.x + dq.x * 0.5f * dt, q.y + dq.y * 0.5f * dt, q.z + dq.z * 0.5f * dt));
}
inline Q slerp(Q a, Q b, float t) {
    float c = a.w * b.w + a.x * b.x + a.y * b.y + a.z * b.z;
    if (c < 0) { b = Q(-b.w, -b.x, -b.y, -b.z); c = -c; }
    if (c > 0.9995f) return qnorm(Q(a.w + (b.w - a.w) * t, a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, a.z + (b.z - a.z) * t));
    const float th = std::acos(c), s = std::sin(th), wa = std::sin((1 - t) * th) / s, wb = std::sin(t * th) / s;
    return {a.w * wa + b.w * wb, a.x * wa + b.x * wb, a.y * wa + b.y * wb, a.z * wa + b.z * wb};
}
/** The smallest rotation's vector (axis * angle) that takes a to b. */
inline V3 rotationVector(Q a, Q b) {
    Q d = b * conj(a);
    if (d.w < 0) d = Q(-d.w, -d.x, -d.y, -d.z);
    const V3 v(d.x, d.y, d.z);
    const float s = len(v);
    if (s < 1e-7f) return v * 2.0f;
    return v * (2.0f * std::atan2(s, d.w) / s);
}

struct Aabb {
    V3 lo, hi;
    bool overlaps(const Aabb& o) const { return lo.x <= o.hi.x && hi.x >= o.lo.x && lo.y <= o.hi.y && hi.y >= o.lo.y && lo.z <= o.hi.z && hi.z >= o.lo.z; }
};

}  // namespace impulsis
}  // namespace astratto
