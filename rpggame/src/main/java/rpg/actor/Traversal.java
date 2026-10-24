package rpg.actor;

import rpg.Rpg;
import rpg.skill.Skill;
import rpg.world.Blocks;
import static astratto.bridge.AstrattoNative.*;

/** Intent -> local geometry candidates -> hang/mantle -> momentum. No authored endpoints or map coordinates. */
public final class Traversal {
    public enum Phase { REACH, HANG, MANTLE, EXIT }
    public record Anchor(float handX, float top, float handZ, float nx, float nz, float landX, float landZ, float score) {}
    public static final class Motion {
        public Anchor anchor;
        public Phase phase = Phase.REACH;
        public float startX, startY, startZ, hangX, hangY, hangZ, vx, vz, progress, duration, reachEnd, hangEnd, mantleEnd;
        public double began;
    }
    private final Rpg g;
    public Traversal(Rpg g) { this.g = g; }
    public static float reach(int level) { return level < 5 ? 0 : 2 + (level >= 20 ? 0.5f : 0) + (level >= 50 ? 0.75f : 0) + (level >= 80 ? 0.75f : 0); }
    public boolean busy(Actor a) { return a.traversal != null; }
    public boolean allowed(Actor a) { return a.alive && a.carryBody == 0 && !g.law.inCustody(a) && a.cs.staggered <= 0 && a.cs.phase == Actor.Combat.Phase.IDLE && !a.cs.blocking && a.cs.draw <= 0 && a.stamina >= 5 && a.skills.level(Skill.AGILITY) >= 5; }

    /** Three direction probes; only tagged full-cell structure materials form anchors. */
    public Anchor sense(Actor a, float yaw) {
        float max = reach(a.skills.level(Skill.AGILITY)); if (max <= 0) return null;
        Anchor best = null;
        for (float angle : new float[]{0, -0.18f, 0.18f}) for (float height : new float[]{0.55f, 1.25f}) {
            float dx = (float) -Math.sin(yaw + angle), dz = (float) -Math.cos(yaw + angle);
            var hit = g.vox.ray(a.x, a.y + height, a.z, dx, 0, dz, a.radius + 1.05f, false);
            if (!hit.hit || hit.ny != 0 || !Blocks.traversable(hit.block)) continue;
            int top = hit.by + 1;
            while (top - a.y <= max + 0.05f && Blocks.traversable(g.vox.block(hit.bx, top, hit.bz))) top++;
            float rise = top - a.y;
            if (rise < 0.65f || rise > max + 0.05f ) continue;
            float hx = hit.x + hit.nx * 0.035f, hz = hit.z + hit.nz * 0.035f;
            float lx = hit.bx + 0.5f, lz = hit.bz + 0.5f;
            if (!free(a, lx, top + 0.03f, lz) || !free(a, hx + hit.nx * (a.radius + 0.04f), Math.max(a.y, top - a.height * 0.84f), hz + hit.nz * (a.radius + 0.04f))) continue;
            float score = hit.dist + Math.abs(angle) * 2 + rise * 0.06f;
            if (best == null || score < best.score) best = new Anchor(hx, top, hz, hit.nx, hit.nz, lx, lz, score);
        }
        return best;
    }
    /** A capsule's conservative cell bounds, plus occupied character space at the landing. */
    public boolean free(Actor a, float x, float y, float z) {
        float radius = a.radius * 0.96f;
        for (int bx = (int) Math.floor(x - radius); bx <= (int) Math.floor(x + radius); bx++)
            for (int bz = (int) Math.floor(z - radius); bz <= (int) Math.floor(z + radius); bz++)
                for (int by = (int) Math.floor(y + 0.02f); by <= (int) Math.floor(y + a.height - 0.02f); by++) if (g.vox.solid(bx, by, bz)) return false;
        for (Actor other : g.actors()) if (other != a && other.alive && y < other.y + other.height && y + a.height > other.y && Math.hypot(x - other.x, z - other.z) < radius + other.radius) return false;
        return true;
    }
    public boolean begin(Actor a, float yaw) {
        if (!allowed(a) || busy(a) || g.time < a.traverseAgain) return false;
        Anchor anchor = sense(a, yaw); if (anchor == null) return false;
        Motion m = new Motion(); m.anchor = anchor; m.startX = a.x; m.startY = a.y; m.startZ = a.z;
        m.hangX = anchor.handX + anchor.nx * (a.radius + 0.04f); m.hangZ = anchor.handZ + anchor.nz * (a.radius + 0.04f); m.hangY = Math.max(a.y, anchor.top - a.height * 0.84f);
        m.vx = a.vx; m.vz = a.vz; m.began = g.time;
        float rise = anchor.top - a.y;
        m.duration = rise < 1.2f ? 0.55f : Math.max(0.6f, 0.9f + rise * 0.08f - a.skills.level(Skill.AGILITY) * 0.002f);
        m.reachEnd = rise > 2.5f ? 0.36f : 0.2f; m.hangEnd = m.reachEnd + 0.12f; m.mantleEnd = 0.78f;
        a.traversal = m; a.stamina -= 5; a.cs.cancel(); a.cs.regenDelay = 0.8f;
        g.interact.cancel(a, "traversing");
        return true;
    }
    /** True while this system owns movement; completion restores the pre-transition horizontal momentum. */
    public boolean update(Actor a, float dt) {
        Motion m = a.traversal; if (m == null) return false;
        if (!a.alive || a.cs.staggered > 0 || a.cs.lastHurt > m.began) { cancel(a); return false; }
        if (!free(a, m.anchor.landX, m.anchor.top + 0.03f, m.anchor.landZ)) { cancel(a); return false; }
        m.progress = Math.min(1, m.progress + dt / m.duration); float t = m.progress, x, y, z;
        if (t < m.reachEnd) { m.phase = Phase.REACH; float k = ease(t / m.reachEnd); x = mix(m.startX, m.hangX, k); y = mix(m.startY, m.hangY, k); z = mix(m.startZ, m.hangZ, k); }
        else if (t < m.hangEnd) { m.phase = Phase.HANG; x = m.hangX; y = m.hangY; z = m.hangZ; }
        else if (t < m.mantleEnd) { m.phase = Phase.MANTLE; x = m.hangX; y = mix(m.hangY, m.anchor.top + 0.12f, ease((t - m.hangEnd) / (m.mantleEnd - m.hangEnd))); z = m.hangZ; }
        else { m.phase = Phase.EXIT; float k = ease((t - m.mantleEnd) / (1 - m.mantleEnd)); x = mix(m.hangX, m.anchor.landX, k); y = mix(m.anchor.top + 0.12f, m.anchor.top + 0.03f, k); z = mix(m.hangZ, m.anchor.landZ, k); }
        if (!free(a, x, y, z)) { cancel(a); return false; }
        a.x = x; a.y = y; a.z = z; a.vx = a.vz = 0; a.grounded = false;
        a.yaw = (float) Math.atan2(m.anchor.nx, m.anchor.nz);
        g.game.commands().moverPlace(a.body, x, y, z, 0, 0, 0);
        g.game.commands().moverInput(a.body, 0, 0, 0, 0, 80, MOVER_POSED);
        if (t >= 1) {
            var event = g.state.commit("Traversed", a.id, 0, null, 0, "ledge " + m.anchor.top);
            g.prog.award(a, Skill.AGILITY, 8 + (m.anchor.top - m.startY) * 4, "geometry traversal", event.id());
            g.game.commands().moverInput(a.body, m.vx, m.vz, 0, 0, 80, 0);
            a.vx = m.vx; a.vz = m.vz; a.traversal = null; a.traverseAgain = g.time + 0.25;
        }
        return true;
    }
    public void cancel(Actor a) { if (a.traversal == null) return; a.traversal = null; a.traverseAgain = g.time + 0.4; if (a.body != 0) g.game.commands().moverInput(a.body, 0, 0, 0, 0, 80, 0); }
    private static float mix(float a, float b, float t) { return a + (b - a) * t; }
    private static float ease(float t) { return t * t * (3 - 2 * t); }
}
