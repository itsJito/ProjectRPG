package rpg.actor;

import rpg.Rpg;
import rpg.skill.Skill;
import rpg.world.Blocks;
import static astratto.bridge.AstrattoNative.*;

/** Intent -> local geometry candidates -> hang/mantle -> momentum. No authored endpoints or map coordinates. */
public final class Traversal {
    public enum Phase { REACH, HANG, MANTLE, EXIT }
    /** A hold: the hand on the edge, its top, the face's normal, where the body lands (and at what height), and whether it is a vault - over a one-block obstacle and down the far side, not up onto it. */
    public record Anchor(float handX, float top, float handZ, float nx, float nz, float landX, float landZ, float score, float landY, boolean vault) {
        public Anchor(float handX, float top, float handZ, float nx, float nz, float landX, float landZ, float score) { this(handX, top, handZ, nx, nz, landX, landZ, score, top + 0.03f, false); }
    }
    /**
     * (0.2.1, gamefixes) vaulting a fence or a one-block wall comes at Agility 2; climbing up a ledge at 5. (0.2.9,
     * gamefixes) at 6 the climb is two-step: the hands take the edge and the body hangs there - the climb button again
     * pulls up, the strafe keys shimmy along the edge, crouch lets go (a controlled drop) and back with jump leaps off
     * behind; and crouch-walking off an edge lowers one onto it to hang, rather than falling. A hang is held on stamina.
     * At 10 the sprint becomes a shoulder charge that barges through people in the way.
     */
    public static final int VAULT_LEVEL = 2, CLIMB_LEVEL = 5, HANG_LEVEL = 6, CHARGE_LEVEL = 10;
    /** Stamina a hang costs each second (less with Agility), and how fast a shimmy goes along an edge (m/s). */
    public static final float HANG_COST = 3.0f, SHIMMY_SPEED = 0.95f;
    /**
     * (0.3.0, gamefixes) the least rise held as a hang: a one-block edge (a rise of a block or so) is simply climbed - one
     * never dangles from something one could step up onto.
     */
    public static final float HANG_MIN_RISE = 1.6f;
    /** (0.3.0) seconds of sprinting before the shoulder charge starts, and of charging before it can knock a body down. */
    public static final float CHARGE_AFTER = 2.0f, CHARGE_KNOCKDOWN = 0.75f;
    public static final class Motion {
        public Anchor anchor;
        public Phase phase = Phase.REACH;
        public float startX, startY, startZ, hangX, hangY, hangZ, vx, vz, progress, duration, reachEnd, hangEnd, mantleEnd;
        public double began;
        /** Holding the hang until told what next (Agility 6); lowered onto the edge from above (a controlled drop). */
        public boolean hold, lowering;
        /** The hand-over-hand of a shimmy: how far through its cycle, which way (-1 left, +1 right, 0 still), and the hang's age. */
        public float shimmy, holdT;
        public int shimmyDir;
        /** The crouch key as it was last frame (a press lets go, not one still held from before the hang). */
        public boolean sneakWas;
        /** Whether the hands have reached the edge yet and the sprint key was read there (held: on up; let go: hang). */
        public boolean decided;
        /** The shimmy's carried speed along the edge (m/s, signed: the body eases into and out of each sideways move). */
        public float shimmyV;
    }
    private final Rpg g;
    public Traversal(Rpg g) { this.g = g; }
    public static float reach(int level) { return level < 5 ? 0 : 2 + (level >= 20 ? 0.5f : 0) + (level >= 50 ? 0.75f : 0) + (level >= 80 ? 0.75f : 0); }
    public boolean busy(Actor a) { return a.traversal != null; }
    public boolean allowed(Actor a) { return a.alive && a.carryBody == 0 && !g.law.inCustody(a) && a.cs.staggered <= 0 && a.cs.phase == Actor.Combat.Phase.IDLE && !a.cs.blocking && a.cs.draw <= 0 && a.stamina >= 5 && a.skills.level(Skill.AGILITY) >= VAULT_LEVEL; }

    /** Three direction probes; only tagged full-cell structure materials form anchors. */
    public Anchor sense(Actor a, float yaw) {
        Anchor vault = vault(a, yaw);
        float max = reach(a.skills.level(Skill.AGILITY)); if (max <= 0) return vault;
        Anchor best = vault;
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
    /**
     * A vault: a fence, or a single full block (a low wall, a crate, a bale) just ahead at the feet, open above it and
     * with ground at the same level on its far side - the hands go on its top and the body over it, landing beyond.
     */
    public Anchor vault(Actor a, float yaw) {
        if (a.skills.level(Skill.AGILITY) < VAULT_LEVEL) return null;
        Anchor best = null;
        for (float angle : new float[]{0, -0.18f, 0.18f}) {
            float dx = (float) -Math.sin(yaw + angle), dz = (float) -Math.cos(yaw + angle);
            var hit = g.vox.ray(a.x, a.y + 0.45f, a.z, dx, 0, dz, a.radius + 0.9f, false);
            if (!hit.hit || hit.ny != 0) continue;
            int b = hit.block, fy = (int) Math.floor(a.y + 0.05f);
            if (hit.by != fy || !(b == Blocks.FENCE || Blocks.traversable(b))) continue;
            // one block high: open above it, and above that for the body going over
            if (g.vox.solid(hit.bx, fy + 1, hit.bz) || g.vox.solid(hit.bx, fy + 2, hit.bz)) continue;
            // the far side: the next cell on, open at the feet and the head, ground beneath
            int fx = hit.bx - Math.round(hit.nx), fz = hit.bz - Math.round(hit.nz);
            if (g.vox.solid(fx, fy, fz) || g.vox.solid(fx, fy + 1, fz) || !g.vox.solid(fx, fy - 1, fz)) continue;
            float lx = fx + 0.5f, lz = fz + 0.5f, top = fy + 1;
            if (!free(a, lx, a.y + 0.03f, lz)) continue;
            float hx = hit.x + hit.nx * 0.035f, hz = hit.z + hit.nz * 0.035f;
            float score = hit.dist + Math.abs(angle) * 2 - 0.2f;   // a vault over beats a climb onto it
            if (best == null || score < best.score()) best = new Anchor(hx, top, hz, hit.nx, hit.nz, lx, lz, score, a.y + 0.03f, true);
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
    /** Whether a hangable edge (higher than a step up: see HANG_MIN_RISE) is in reach ahead. */
    public boolean highEdge(Actor a, float yaw) {
        if (!allowed(a) || busy(a) || g.time < a.traverseAgain) return false;
        Anchor an = sense(a, yaw);
        return an != null && !an.vault() && an.top() - a.y >= HANG_MIN_RISE;
    }
    /** Whether this body is hanging from an edge, waiting on the next move. */
    public boolean hanging(Actor a) { return a.traversal != null && a.traversal.hold && a.traversal.progress >= a.traversal.hangEnd - 1e-4f; }

    /**
     * A hang's next move (the player's keys): a press of jump pulls up (or, with back held, leaps off behind), the strafe
     * keys shimmy along the edge where it goes on, a press of crouch lets go. Also pays for the hang in stamina, and lets
     * go when that runs out.
     */
    public boolean hang(Actor a, float strafe, float forward, boolean jump, boolean sneak, float dt) { return hang(a, strafe, forward, jump, sneak, false, dt); }
    public boolean hang(Actor a, float strafe, float forward, boolean jump, boolean sneak, boolean sprint, float dt) {
        Motion m = a.traversal;
        if (m == null || !m.hold) return false;
        boolean drop = sneak && !m.sneakWas;
        m.sneakWas = sneak;
        if (!hanging(a)) return false;
        m.holdT += dt;
        a.stamina = Math.max(0, a.stamina - HANG_COST * (1 - Math.min(0.6f, a.skills.level(Skill.AGILITY) * 0.006f)) * dt);
        a.cs.regenDelay = 0.6f;
        if (jump && forward < -0.3f) { leapBack(a); return true; }
        // (0.3.0) up: jump, or the sprint taken up again toward the wall - the same key that took the edge
        if (jump || sprint && forward > 0.3f) { m.hold = false; g.state.commit("LedgePulledUp", a.id, 0, null, 0, "after " + String.format("%.1f", m.holdT) + " s hanging"); return false; }
        if (drop || a.stamina <= 0) { letGo(a, a.stamina <= 0 ? "grip failed" : "let go"); return true; }
        // the shimmy carries a little speed: hand over hand it eases in and out rather than snapping to pace and stopping dead
        int want = Math.abs(strafe) > 0.3f ? (strafe > 0 ? 1 : -1) : 0;
        float target = want * SHIMMY_SPEED, rate = want != 0 && Math.signum(m.shimmyV) != -want ? 3.2f : 5.5f;
        m.shimmyV += Math.max(-rate * dt, Math.min(rate * dt, target - m.shimmyV));
        m.shimmyDir = Math.abs(m.shimmyV) > 0.05f ? (m.shimmyV > 0 ? 1 : -1) : 0;
        if (m.shimmyDir != 0) { if (!shimmy(a, m, m.shimmyV * dt)) m.shimmyV = 0; }
        else { m.shimmyV = 0; m.shimmy = Math.max(0, m.shimmy - dt * 2); }
        return false;
    }

    /** Along the edge by a step, if the edge goes on there: the same top, a face to hold, room to hang and to climb out. */
    private boolean shimmy(Actor a, Motion m, float step) {
        Anchor an = m.anchor;
        float yaw = (float) Math.atan2(an.nx(), an.nz());
        float rx = (float) Math.cos(yaw), rz = (float) -Math.sin(yaw);
        float hx = an.handX() + rx * step, hz = an.handZ() + rz * step;
        // the block the hands hold: just behind the face, at the top's own height
        int bx = (int) Math.floor(hx - an.nx() * 0.1f), bz = (int) Math.floor(hz - an.nz() * 0.1f), top = Math.round(an.top());
        // a little ahead too, so the body's width stays on the edge
        int ax = (int) Math.floor(hx + rx * Math.signum(step) * a.radius - an.nx() * 0.1f), az = (int) Math.floor(hz + rz * Math.signum(step) * a.radius - an.nz() * 0.1f);
        boolean edge = Blocks.traversable(g.vox.block(bx, top - 1, bz)) && !g.vox.solid(bx, top, bz) && Blocks.traversable(g.vox.block(ax, top - 1, az)) && !g.vox.solid(ax, top, az);
        float nhx = m.hangX + rx * step, nhz = m.hangZ + rz * step;
        if (!edge || !free(a, nhx, m.hangY, nhz)) { m.shimmyDir = 0; return false; }
        m.hangX = nhx; m.hangZ = nhz;
        m.anchor = new Anchor(hx, an.top(), hz, an.nx(), an.nz(), bx + 0.5f, bz + 0.5f, an.score(), an.top() + 0.03f, false);
        m.shimmy += Math.abs(step) / 0.45f;
        a.shimmied += Math.abs(step);
        if (a.shimmied >= 2) { a.shimmied = 0; var e = g.state.commit("LedgeShimmy", a.id, 0, null, 0, "2 m along an edge"); g.prog.award(a, Skill.AGILITY, 3, "shimmy", e.id()); }
        return true;
    }

    /** Lets go of the edge: the body drops straight down from the hang, eased off the wall. */
    public void letGo(Actor a, String why) {
        Motion m = a.traversal; if (m == null) return;
        float nx = m.anchor.nx(), nz = m.anchor.nz();
        a.traversal = null; a.traverseAgain = g.time + 0.5;
        a.grounded = false;
        if (a.body != 0) g.game.commands().moverInput(a.body, nx * 0.8f, nz * 0.8f, 0, 0, 80, 0);
        a.vx = nx * 0.8f; a.vz = nz * 0.8f;
        g.state.commit("LedgeDropped", a.id, 0, null, 0, why);
        if (a == g.player && why.equals("grip failed")) g.chat.add("Your grip gives out.", rpg.Chat.Kind.WARN);
    }

    /** Pushes off the wall and away behind: turned round, launched up and out from the edge (true: the move's own input stands this frame). */
    public void leapBack(Actor a) {
        Motion m = a.traversal; if (m == null) return;
        float nx = m.anchor.nx(), nz = m.anchor.nz();
        a.traversal = null; a.traverseAgain = g.time + 0.5;
        a.grounded = false;
        // (the body turns round in the air at its own pace - snapped at once, it flicked about in a frame)
        float out = 4.6f, up = g.cfg.jumpSpeed * 0.85f;
        // launched: the body placed where it hangs with the leap's own velocity (a jump input is a grounded thing)
        if (a.body != 0) { g.game.commands().moverPlace(a.body, a.x, a.y, a.z, nx * out, up, nz * out); g.game.commands().moverInput(a.body, nx * out, nz * out, 0, 0, 80, 0); }
        a.vx = nx * out; a.vz = nz * out;
        a.stamina = Math.max(0, a.stamina - 6);
        a.leapAt = g.time;
        var e = g.state.commit("LedgeLeapt", a.id, 0, null, 0, "off the wall behind");
        g.prog.award(a, Skill.AGILITY, 6, "leap from a hang", e.id());
    }

    /**
     * A controlled drop (Agility 6): crouch-walking off an edge with a fall beyond it, one turns and lowers onto the edge's
     * outer face to hang there - from where one drops the rest of the way or pulls back up.
     */
    public boolean lower(Actor a, float yaw) {
        if (!allowed(a) || busy(a) || g.time < a.traverseAgain || a.skills.level(Skill.AGILITY) < HANG_LEVEL || !a.grounded) return false;
        float dx = (float) -Math.sin(yaw), dz = (float) -Math.cos(yaw);
        int fy = (int) Math.floor(a.y + 0.05f);
        // one of the four sides only (an edge is square on): the one the way one goes points across most
        int sx = Math.abs(dx) >= Math.abs(dz) ? (dx > 0 ? 1 : -1) : 0, sz = sx == 0 ? (dz > 0 ? 1 : -1) : 0;
        int cx = (int) Math.floor(a.x), cz = (int) Math.floor(a.z);
        // standing at the cell's rim on that side, on firm ground
        float rim = sx != 0 ? (sx > 0 ? cx + 1 - a.x : a.x - cx) : (sz > 0 ? cz + 1 - a.z : a.z - cz);
        if (rim > a.radius + 0.25f || !Blocks.traversable(g.vox.block(cx, fy - 1, cz))) return false;
        int ox = cx + sx, oz = cz + sz;
        // beyond: open at the feet and below for at least two blocks, room for a hanging body
        if (g.vox.solid(ox, fy, oz) || g.vox.solid(ox, fy - 1, oz) || g.vox.solid(ox, fy - 2, oz) || g.vox.solid(ox, fy + 1, oz)) return false;
        float nx = sx, nz = sz;
        float hx = (sx != 0 ? (sx > 0 ? cx + 1 : cx) : a.x) + nx * 0.035f, hz = (sz != 0 ? (sz > 0 ? cz + 1 : cz) : a.z) + nz * 0.035f;
        Anchor anchor = new Anchor(hx, fy, hz, nx, nz, cx + 0.5f, cz + 0.5f, 0, fy + 0.03f, false);
        Motion m = new Motion(); m.anchor = anchor; m.startX = a.x; m.startY = a.y; m.startZ = a.z;
        m.hangX = hx + nx * (a.radius + 0.04f); m.hangZ = hz + nz * (a.radius + 0.04f); m.hangY = fy - a.height * 0.84f;
        if (!free(a, m.hangX, m.hangY, m.hangZ) || !free(a, m.hangX, a.y, m.hangZ)) return false;
        m.vx = 0; m.vz = 0; m.began = g.time;
        m.duration = 1.1f; m.reachEnd = 0.3f; m.hangEnd = 0.36f; m.mantleEnd = 0.8f;
        m.hold = true; m.lowering = true; m.sneakWas = true;
        a.traversal = m; a.cs.cancel(); a.cs.regenDelay = 0.8f;
        g.interact.cancel(a, "traversing");
        g.state.commit("LedgeLowered", a.id, 0, null, 0, "onto an edge at " + fy);
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
        // a vault: the hands planted on the top (no hang), the body swung up and over it, then down to the far side
        if (anchor.vault()) { m.hangY = a.y; m.duration = Math.max(0.5f, 0.68f - a.skills.level(Skill.AGILITY) * 0.0015f); m.reachEnd = 0.18f; m.hangEnd = 0.24f; m.mantleEnd = 0.62f; }
        // a ledge (not a vault) taken at Agility 6 by the player may be held at the hang (0.3.0: never a one-block edge);
        // whether it is, is read when the hands reach it - see update()
        m.hold = !anchor.vault() && a == g.player && a.skills.level(Skill.AGILITY) >= HANG_LEVEL && rise >= HANG_MIN_RISE;
        m.sneakWas = a == g.player && g.controller != null && g.controller.in.sneak;
        a.traversal = m; a.stamina -= 5; a.cs.cancel(); a.cs.regenDelay = 0.8f;
        g.interact.cancel(a, "traversing");
        return true;
    }
    /** True while this system owns movement; completion restores the pre-transition horizontal momentum. */
    public boolean update(Actor a, float dt) {
        Motion m = a.traversal; if (m == null) return false;
        if (!a.alive || a.cs.staggered > 0 || a.cs.lastHurt > m.began) { cancel(a); return false; }
        if (!free(a, m.anchor.landX(), m.anchor.landY(), m.anchor.landZ())) { cancel(a); return false; }
        // (0.3.0, gamefixes) seamless: as the hands take the edge, the sprint key still held carries straight on up and over;
        // let go by then, the body hangs there and the strafe keys shimmy (a lowering is always a hang: it was asked for)
        if (m.hold && !m.decided && m.progress + dt / m.duration >= m.hangEnd) {
            m.decided = true;
            boolean sprintHeld = a == g.player && g.controller != null && g.controller.in.sprint && g.controller.in.forward > 0.1f;
            if (sprintHeld && !m.lowering) m.hold = false;
        }
        m.progress = Math.min(m.hold ? m.hangEnd : 1, m.progress + dt / m.duration); float t = m.progress, x, y, z;
        if (t < m.reachEnd && m.lowering) {
            // over the rim first, still at the top's height, then down onto the hands
            m.phase = Phase.REACH; float k = t / m.reachEnd;
            float h = ease(Math.min(1, k / 0.45f)), v = ease(Math.max(0, (k - 0.5f) / 0.5f));
            x = mix(m.startX, m.hangX, h); z = mix(m.startZ, m.hangZ, h); y = mix(m.startY, m.hangY, v);
        }
        else if (t < m.reachEnd) { m.phase = Phase.REACH; float k = ease(t / m.reachEnd); x = mix(m.startX, m.hangX, k); y = mix(m.startY, m.hangY, k); z = mix(m.startZ, m.hangZ, k); }
        else if (t < m.hangEnd) { m.phase = Phase.HANG; x = m.hangX; y = m.hangY; z = m.hangZ; }
        else if (t < m.mantleEnd) { m.phase = Phase.MANTLE; x = m.hangX; y = mix(m.hangY, m.anchor.top + 0.12f, ease((t - m.hangEnd) / (m.mantleEnd - m.hangEnd))); z = m.hangZ; }
        else { m.phase = Phase.EXIT; float k = ease((t - m.mantleEnd) / (1 - m.mantleEnd)); x = mix(m.hangX, m.anchor.landX, k); y = mix(m.anchor.top + 0.12f, m.anchor.landY(), k); z = mix(m.hangZ, m.anchor.landZ, k); }
        if (m.anchor.vault()) {
            // over the top: carried across the obstacle as the body rises, then down to the far side
            float ox = m.anchor.handX() - m.anchor.nx() * 0.5f, oz = m.anchor.handZ() - m.anchor.nz() * 0.5f;
            // the hips swing just over the top on the planted hands, the knees tucked up to clear it - never stood upon it
            float peak = m.anchor.top() - 0.42f;
            if (m.phase == Phase.MANTLE) { float k = ease((t - m.hangEnd) / (m.mantleEnd - m.hangEnd)); x = mix(m.hangX, ox, k); z = mix(m.hangZ, oz, k); y = mix(m.hangY, peak, (float) Math.sin(k * Math.PI / 2)); }
            if (m.phase == Phase.EXIT) { float k = ease((t - m.mantleEnd) / (1 - m.mantleEnd)); x = mix(ox, m.anchor.landX(), k); z = mix(oz, m.anchor.landZ(), k); y = mix(peak, m.anchor.landY(), k * k); }
        }
        // a vaulting body passes over the obstacle's own cell, which was checked clear above it
        if (!m.anchor.vault() && !free(a, x, y, z)) { cancel(a); return false; }
        a.x = x; a.y = y; a.z = z; a.vx = a.vz = 0; a.grounded = false;
        a.yaw = (float) Math.atan2(m.anchor.nx, m.anchor.nz);
        g.game.commands().moverPlace(a.body, x, y, z, 0, 0, 0);
        g.game.commands().moverInput(a.body, 0, 0, 0, 0, 80, MOVER_POSED);
        if (t >= 1) {
            var event = g.state.commit("Traversed", a.id, 0, null, 0, (m.anchor.vault() ? "vault " : "ledge ") + m.anchor.top);
            g.prog.award(a, Skill.AGILITY, m.anchor.vault() ? 6 : 8 + (m.anchor.top - m.startY) * 4, m.anchor.vault() ? "vault" : "geometry traversal", event.id());
            g.game.commands().moverInput(a.body, m.vx, m.vz, 0, 0, 80, 0);
            a.vx = m.vx; a.vz = m.vz; a.traversal = null; a.traverseAgain = g.time + 0.25;
        }
        return true;
    }
    public void cancel(Actor a) { if (a.traversal == null) return; a.traversal = null; a.traverseAgain = g.time + 0.4; if (a.body != 0) g.game.commands().moverInput(a.body, 0, 0, 0, 0, 80, 0); }
    private static float mix(float a, float b, float t) { return a + (b - a) * t; }
    private static float ease(float t) { return t * t * (3 - 2 * t); }
}
