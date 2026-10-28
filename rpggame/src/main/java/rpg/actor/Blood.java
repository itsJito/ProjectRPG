package rpg.actor;

import java.util.ArrayList;
import java.util.List;

/**
 * Blood on a body (gamefixes: blood on the character is simulated): each mark is a splash on one face of one part of
 * the figure, at the place a blow actually landed or blood actually flew - a cut to the right forearm marks the right
 * forearm where it was struck, a bleeding wound runs down the face below it, a blow struck close spatters the striker's
 * hands and chest. Marks are wet when made and dry darker with time; water washes them all off. The figure's skin is
 * painted from them (see {@code Skin.paint}), so blood shows on clothes and armor exactly where it is.
 */
public final class Blood {
    /** The base parts a mark can lie on (the skin's base boxes; armor over them takes it too). */
    public static final int HEAD = 0, TORSO = 1, R_ARM = 2, L_ARM = 3, R_LEG = 4, L_LEG = 5;
    /** Faces, as the skin numbers them. */
    public static final int TOP = 0, BOTTOM = 1, RIGHT = 2, FRONT = 3, LEFT = 4, BACK = 5;

    /**
     * One splash: part, face, where on the face (0..1 across, 0..1 down), how big (texels), wet or dried, its own shape -
     * or a scorch (gamefixes: fire chars bodies where it burns them): soot and burnt cloth, glowing at its edge while wet
     * (still smouldering), ash-grey and black once it has cooled.
     */
    public record Mark(int part, int face, float u, float v, float radius, boolean wet, int seed, double at, boolean scorch) {}

    public final List<Mark> marks = new ArrayList<>();
    /** Bumped on every change, so the figure is repainted only when the blood on it changed. */
    public int version;
    private int seeds = 1;
    public static final int MAX = 28;
    /** Scorches kept besides the blood (a burnt body is charred all over in time, in patches). */
    public static final int MAX_SCORCH = 24;

    public boolean any() { return !marks.isEmpty(); }

    public void add(int part, int face, float u, float v, float radius, double now) {
        if (radius < 0.6f) return;
        marks.add(new Mark(part, face, clamp(u), clamp(v), Math.min(7f, radius), true, seeds++ * 0x2F6B, now, false));
        trim();
        version++;
    }

    /**
     * Fire on the body: a scorch where the flames are licking. A scorch already near the spot grows (up to the face's
     * size) rather than a new one being laid, so a long burn chars a body through, patch by patch, rather than piling up.
     */
    public void scorch(int part, int face, float u, float v, float grow, double now) {
        for (int i = marks.size() - 1; i >= 0; i--) {
            Mark m = marks.get(i);
            if (!m.scorch || m.part != part || m.face != face || Math.hypot(m.u - u, m.v - v) > 0.3f) continue;
            if (m.radius >= 9f && m.wet) return;
            marks.set(i, new Mark(part, face, m.u, m.v, Math.min(9f, m.radius + grow), true, m.seed, now, true));
            version++;
            return;
        }
        marks.add(new Mark(part, face, clamp(u), clamp(v), Math.max(1.2f, grow), true, seeds++ * 0x2F6B, now, true));
        trim();
        version++;
    }

    /** The oldest blood goes past its cap, the oldest scorch past its own. */
    private void trim() {
        int blood = 0, soot = 0;
        for (Mark m : marks) if (m.scorch) soot++; else blood++;
        for (int i = 0; i < marks.size() && (blood > MAX || soot > MAX_SCORCH); i++) {
            Mark m = marks.get(i);
            if (m.scorch ? soot > MAX_SCORCH : blood > MAX) { marks.remove(i--); if (m.scorch) soot--; else blood--; }
        }
    }

    /** Whether any of the marks is a scorch (the figure is then this body's own). */
    public boolean scorched() { for (Mark m : marks) if (m.scorch) return true; return false; }

    /** Blood runs down from the last mark on a part (a wound that keeps bleeding): a smaller mark a little below it. */
    public void run(int part, double now) {
        for (int i = marks.size() - 1; i >= 0; i--) {
            Mark m = marks.get(i);
            if (m.scorch || m.part != part || m.face == TOP || m.face == BOTTOM) continue;
            if (m.v > 0.93f) return;
            add(part, m.face, m.u + ((m.seed >>> 3) % 5 - 2) * 0.02f, m.v + 0.09f + m.radius * 0.01f, Math.max(0.9f, m.radius * 0.72f), now);
            return;
        }
    }

    /** Wet blood dries (darker) after a while, and old blood flakes away. */
    public void age(double now) {
        boolean changed = false;
        for (int i = marks.size() - 1; i >= 0; i--) {
            Mark m = marks.get(i);
            // (a scorch is burnt into the cloth and skin: it is not flaked away by time; it cools in seconds)
            if (!m.scorch && now - m.at > 900) { marks.remove(i); changed = true; }
            else if (m.wet && now - m.at > (m.scorch ? 4 : 90)) { marks.set(i, new Mark(m.part, m.face, m.u, m.v, m.radius, false, m.seed, m.at, m.scorch)); changed = true; }
        }
        if (changed) version++;
    }

    /** Water washes the blood off (a scorch is burnt in: it stays). */
    public void wash() { if (marks.removeIf(m -> !m.scorch)) version++; }
    /** Healed whole (a new life, a mending): every mark goes. */
    public void clear() { if (!marks.isEmpty()) { marks.clear(); version++; } }

    private static float clamp(float x) { return Math.max(0.04f, Math.min(0.96f, x)); }

    /** A copy for the painter (it runs on another thread). */
    public List<Mark> snapshot() { return List.copyOf(marks); }

    /**
     * Where a world point on a body lies on its figure: {part, face, u, v} - by height up the body, which side of the spine
     * it is on, and which way it faces from the part's middle (front, back or a side).
     */
    public static float[] locate(Actor a, float px, float py, float pz) {
        Body b = a.plan();
        float rx = (float) Math.cos(a.yaw), rz = (float) -Math.sin(a.yaw), fx = (float) -Math.sin(a.yaw), fz = (float) -Math.cos(a.yaw);
        float side = ((px - a.x) * rx + (pz - a.z) * rz) / Body.PX, fwd = ((px - a.x) * fx + (pz - a.z) * fz) / Body.PX, up = (py - a.y) / Body.PX;
        int part;
        float cx, halfW, halfD, top, h;
        float shoulderX = b.shoulderX();
        if (up >= Body.LEGS + Body.TORSO) { part = HEAD; cx = 0; halfW = b.head[0] / 2f; halfD = b.head[2] / 2f; top = Body.HEIGHT_PX; h = b.head[1]; }
        else if (up < Body.LEGS) { part = side >= 0 ? R_LEG : L_LEG; cx = (side >= 0 ? 1 : -1) * b.hipX(); halfW = b.leg[0] * b.limb / 2f; halfD = b.leg[2] * b.limb / 2f; top = Body.LEGS; h = b.leg[1]; }
        else if (Math.abs(side) > b.torso[0] * b.girth / 2f) { part = side >= 0 ? R_ARM : L_ARM; cx = (side >= 0 ? 1 : -1) * shoulderX; halfW = b.arm[0] * b.limb / 2f; halfD = b.arm[2] * b.limb / 2f; top = Body.LEGS + Body.TORSO; h = b.arm[1]; }
        else { part = TORSO; cx = 0; halfW = b.torso[0] * b.girth / 2f; halfD = b.torso[2] * b.depth / 2f; top = Body.LEGS + Body.TORSO; h = b.torso[1]; }
        float s = side - cx;
        float v = (top - up) / h;
        int face;
        float u;
        if (Math.abs(fwd) / Math.max(0.5f, halfD) >= Math.abs(s) / Math.max(0.5f, halfW)) {
            face = fwd >= 0 ? FRONT : BACK;
            u = face == FRONT ? (halfW - s) / (2 * halfW) : (s + halfW) / (2 * halfW);
        } else {
            face = s >= 0 ? RIGHT : LEFT;
            u = face == RIGHT ? (halfD + fwd) / (2 * halfD) : (halfD - fwd) / (2 * halfD);
        }
        return new float[]{part, face, u, v};
    }
}
