package rpg;

import static astratto.bridge.AstrattoNative.*;

import astratto.bridge.Snapshots;
import rpg.actor.Actor;
import rpg.actor.PlayerController;
import rpg.objects.Container;
import rpg.world.DevMap;

/**
 * The player's death (gamefixes): it is seen, not skipped. The body falls as Sydney's ragdoll in the pose it was drawn
 * in; for five seconds the camera leaves the shoulder and settles behind the body, locked on whatever killed them, while
 * the world slows for a breath and the colour drains out of it to black and white. Only then may they choose: Continue
 * (wake at the graveyard) or Exit (to the title). The body becomes a grave only after that choice - everything carried
 * and worn goes into it where the body lies (GDD 3.12: classified once, inside the walls or out), and the grave stands
 * ten minutes before the undertaker takes what is left.
 */
public final class PlayerDeath {
    /** How long the death holds the player before the choice is offered (seconds of play). */
    public static final float LOCK = 5f;

    private final Rpg g;
    public boolean active;
    /** The choice is left to whoever drives the game (the gallery), even in an automated run. */
    public boolean manual;
    public double at;
    /** Who killed them (0: nobody - a fall, a wound), their name as it was, and the body's ragdoll. */
    public long killer, ragdoll;
    public String killerName;
    /** Whether the death site is inside a town's walls (the grave will be secured). */
    public boolean secured;
    private float camX, camY, camZ, lookX, lookY, lookZ;
    private boolean camSet;

    public PlayerDeath(Rpg g) { this.g = g; }

    public float elapsed() { return active ? (float) (g.time - at) : 0; }
    /** Whether the choice is on the screen (the five seconds are over). */
    public boolean choosing() { return active && elapsed() >= LOCK; }

    /** The death: the body handed to Sydney, the killer remembered, the camera's path begun. */
    public void start(Actor by) {
        var p = g.player;
        active = true;
        at = g.time;
        killer = by == null || by == p ? 0 : by.id;
        killerName = by == null || by == p ? null : by.name;
        secured = !DevMap.WILDS.equals(DevMap.jurisdiction(p.x, p.z));
        camSet = false;
        bearingAt = -9;
        bearing = 0.45f;
        boolean existing = g.reactions.died(p);
        ragdoll = existing ? p.ragdoll : g.game.reserve(KIND_RAGDOLL);
        p.ragdoll = 0;
        boolean posed = p.posePts != null && g.time - p.poseAt < 0.5;
        if (!existing) g.game.commands().ragdollCreate(ragdoll, 1, SYDNEY_HUMAN, RAGDOLL_ACTIVE | (posed ? 0 : RAGDOLL_STAND), p.x, p.y, p.z, p.yaw, 0, 0, 0);
        if (!existing && posed) g.game.commands().ragdollPose(ragdoll, p.vx, 0, p.vz, p.posePts);
        g.game.commands().ragdollControl(ragdoll, CONTROL_DIE, 1);
        if (by != null && by != p) {
            float dx = p.x - by.x, dz = p.z - by.z, l = Math.max(0.01f, (float) Math.hypot(dx, dz));
            g.game.commands().ragdollHit(ragdoll, RD_NECK, dx / l, 0.2f, dz / l, 110, 30);
        }
        if (g.sfx != null) g.sfx.ui("QuestFailed", 0.45f);
    }

    /** The world's clock through the death: a breath of slow motion as the body goes down, easing back to real time. */
    public float timeScale() {
        if (!active) return 1;
        float t = elapsed();
        return t < 0.3f ? 0.3f : t < 1.6f ? 0.3f + 0.7f * smooth((t - 0.3f) / 1.3f) : 1;
    }

    /** How far the colour has drained (0 .. 0.94): over the whole of the five seconds, eased. */
    public float grade() { return active ? 0.94f * smooth(Math.min(1, elapsed() / (LOCK * 0.92f))) : 0; }

    /** A brief red flash at the blow itself. */
    public float flash() { return active ? Math.max(0, 0.5f - elapsed()) * 1.6f : 0; }

    private static float smooth(float t) { t = Math.max(0, Math.min(1, t)); return t * t * (3 - 2 * t); }

    /** Where the body lies (the ragdoll's middle), or where the player stood. */
    public float[] body() {
        Snapshots.Ragdoll rd = ragdoll != 0 ? g.lastBodies.ragdoll(ragdoll) : null;
        return rd != null ? new float[]{rd.comX, rd.comY, rd.comZ} : new float[]{g.player.x, g.player.y + 0.3f, g.player.z};
    }

    /** The camera's chosen bearing round the body (radians from the line body -> killer), and when it was last chosen. */
    private float bearing;
    private double bearingAt = -9;

    /**
     * The death camera: from where the shoulder camera was, it eases round to stand off the body - the body in the
     * foreground, the killer beyond it, both in frame - and creeps in through the five seconds, following the killer if
     * they walk off. The bearing round the body is chosen (and re-chosen every half second) so that nothing stands
     * between the eye and the body or the killer: behind and to one side first, then further round. With nobody to
     * blame it rises slowly over the body.
     */
    public void camera(PlayerController c) {
        float[] b = body();
        Actor k = killer != 0 ? g.actor(killer) : null;
        float t = elapsed(), u = smooth(Math.min(1, t / LOCK));
        float tx, ty, tz, px, py, pz;
        if (k != null) {
            float kx = k.x, ky = k.y + (k instanceof rpg.animal.Animal ? 0.6f : 1.2f), kz = k.z;
            float dx = kx - b[0], dz = kz - b[2], l = Math.max(0.5f, (float) Math.hypot(dx, dz));
            float base = (float) Math.atan2(dx, dz);
            float dist = 3.6f - 0.8f * u, up = 2.1f - 0.4f * u;
            if (g.time - bearingAt > 0.5) {
                // candidates: behind the body (from the killer), a little to either side, then further round
                float[] tries = {0.45f, -0.45f, 0.8f, -0.8f, 0.2f, -0.2f, 1.2f, -1.2f, 1.6f, -1.6f, 2.1f, -2.1f};
                float best = bearing;
                for (float a : tries) {
                    float ang = base + (float) Math.PI + a;
                    float cx = b[0] + (float) Math.sin(ang) * dist, cy = b[1] + up, cz = b[2] + (float) Math.cos(ang) * dist;
                    if (g.vox.clear(cx, cy, cz, b[0], b[1] + 0.3f, b[2]) && g.vox.clear(cx, cy, cz, kx, ky, kz) && !g.vox.solid((int) Math.floor(cx), (int) Math.floor(cy), (int) Math.floor(cz))) { best = a; break; }
                }
                if (bearingAt < 0 || Math.abs(best - bearing) > 0.01f) bearing = best;
                bearingAt = g.time;
            }
            float ang = base + (float) Math.PI + bearing;
            px = b[0] + (float) Math.sin(ang) * dist; py = b[1] + up; pz = b[2] + (float) Math.cos(ang) * dist;
            // looking past the body to the killer: the body low in the frame, the killer above the middle
            // (the farther they walk off, the more the eye follows them rather than the body)
            float w = 0.62f + 0.3f * Math.min(1, Math.max(0, (l - 5) / 10f));
            tx = b[0] + dx * w; ty = b[1] + (ky - b[1]) * (0.75f + 0.2f * (w - 0.62f) / 0.3f); tz = b[2] + dz * w;
        } else {
            float a = (float) (t * 0.35);
            tx = b[0]; ty = b[1]; tz = b[2];
            px = b[0] + (float) Math.sin(a) * 2.4f; py = b[1] + 2.2f + 0.8f * u; pz = b[2] + (float) Math.cos(a) * 2.4f;
        }
        // never inside a wall: pulled in toward the body along the line from it
        if (!g.vox.clear(b[0], b[1] + 0.8f, b[2], px, py, pz)) {
            float dx = px - b[0], dy = py - b[1] - 0.8f, dz = pz - b[2], l = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            var hit = g.vox.ray(b[0], b[1] + 0.8f, b[2], dx / l, dy / l, dz / l, l, false);
            float keep = hit.hit ? Math.max(0.4f, hit.dist - 0.25f) / l : 1;
            px = b[0] + dx * keep; py = b[1] + 0.8f + dy * keep; pz = b[2] + dz * keep;
        }
        if (!camSet) { camX = c.cx; camY = c.cy; camZ = c.cz; lookX = c.cx + c.fx * 4; lookY = c.cy + c.fy * 4; lookZ = c.cz + c.fz * 4; camSet = true; }
        float e = Math.min(1, (float) g.lastDt * 2.2f);
        camX += (px - camX) * e; camY += (py - camY) * e; camZ += (pz - camZ) * e;
        lookX += (tx - lookX) * e * 1.4f; lookY += (ty - lookY) * e * 1.4f; lookZ += (tz - lookZ) * e * 1.4f;
        float dx = lookX - camX, dy = lookY - camY, dz = lookZ - camZ, l = Math.max(1e-3f, (float) Math.sqrt(dx * dx + dy * dy + dz * dz));
        c.cx = camX; c.cy = camY; c.cz = camZ; c.fx = dx / l; c.fy = dy / l; c.fz = dz / l;
    }

    /** In an automated run there is nobody to press Continue: it is pressed for them once the five seconds are up. */
    public void update() {
        if (active && g.scripted && !manual && elapsed() >= LOCK) proceed(false);
    }

    /**
     * The choice. Either way the body becomes the grave first - everything carried and worn, where it lay - and the
     * player wakes at the graveyard; Exit then leaves for the title screen (the game is saved as it is left).
     */
    public void proceed(boolean exit) {
        if (!active) return;
        float[] b = body();
        Container grave = g.makeGrave(b[0], b[2], killer, secured);
        if (ragdoll != 0) { g.game.commands().ragdollControl(ragdoll, CONTROL_RELEASE, 0); g.game.commands().release(ragdoll); ragdoll = 0; }
        active = false;
        g.respawn();
        if (grave != null) g.chat.add(secured
                ? "Morrow holds your belongings at the Undertaker’s chest. Stolen goods are confiscated and will not be returned. Speak to him to recover the rest."
                : "Your belongings lie in a grave where you fell, outside the walls. Anyone who finds it may help themselves: hurry. It stands ten minutes.", rpg.Chat.Kind.WARN);
        if (exit && g.ui != null) g.ui.toTitle();
    }
}
