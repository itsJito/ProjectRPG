package rpg.combat;

import static astratto.bridge.AstrattoNative.*;

import astratto.bridge.Snapshots;
import java.util.ArrayList;
import java.util.List;
import rpg.Rpg;
import rpg.actor.Actor;
import rpg.ai.Npc;
import rpg.skill.Skill;
import rpg.world.DevMap;

/**
 * Bodies that react to blows physically. A person struck hard enough to stagger is handed to Sydney, the engine's
 * muscle-driven body, in the pose they were drawn in a moment ago and moving as they were: from then on what they do
 * is simulated - they take a step or three to catch themselves, or go down, brace with their hands, lie a moment and
 * get up - and when Sydney says they are back on their feet the person is handed back to the animated figure where
 * they stand. Their skills are their body's traits: Strength their muscles' force, Agility how quickly they react
 * and how surely they step, Defence how long a blow shakes them, Health how long they hold on and how often they try
 * to rise. A killing blow on a body Sydney already holds is the same body dying: nothing is swapped.
 */
public final class Reactions {
    private final Rpg g;
    /** The people Sydney holds at the moment (alive). */
    private final List<Actor> held = new ArrayList<>();
    public int started, recovered, fallen;

    public Reactions(Rpg g) { this.g = g; }

    public boolean reacting(Actor a) { return a.reacting; }

    /**
     * A blow to a living person: impulse in newton seconds along (dx, dy, dz), at the world point (px, py, pz); force how
     * hard it was felt (how long it shakes them). Starts a reaction if they are not in one.
     */
    public void struck(Actor n, float dx, float dy, float dz, float impulse, float force, float px, float py, float pz) {
        if (!n.alive || n instanceof Npc npc && !npc.loaded) return;
        if (n.ragdoll == 0) {
            if (n.posePts == null || g.time - n.poseAt > 0.5) return;      // never drawn lately (far off): no reaction to show
            n.ragdoll = g.game.reserve(KIND_RAGDOLL);
            g.game.commands().ragdollCreate(n.ragdoll, n.ragdollScale(), n.sydneyProfile(), RAGDOLL_ACTIVE, n.x, n.y, n.z, n.yaw, 0, 0, 0);
            g.game.commands().ragdollPose(n.ragdoll, n.vx, 0, n.vz, n.posePts);
            traits(n);
            if (g.gore != null && n instanceof Npc npc) g.gore.limbsToBody(npc);
            n.reacting = true;
            n.reactSince = g.time;
            held.add(n);
            started++;
            if (n instanceof Npc npc) g.ai.desire(npc, rpg.ai.AiSystem.A_IDLE);
        }
        g.game.commands().ragdollHit(n.ragdoll, nearestPoint(n, px, py, pz), dx, dy, dz, impulse, force);
    }

    /** A long fall hands the existing body to Sydney even outside the renderer's view. */
    public void falling(Actor a, float verticalSpeed) {
        if (!a.alive || a.reacting || a instanceof rpg.animal.Animal) return;
        g.carrying.drop(a, false); g.traversal.cancel(a); a.cs.cancel(); a.cs.blocking = false;
        a.ragdoll = g.game.reserve(KIND_RAGDOLL);
        boolean posed = a.posePts != null && g.time - a.poseAt < 0.5;
        g.game.commands().ragdollCreate(a.ragdoll, a.ragdollScale(), a.sydneyProfile(), RAGDOLL_ACTIVE | (posed ? 0 : RAGDOLL_STAND), a.x, a.y, a.z, a.yaw, 0, 0, 0);
        if (posed) g.game.commands().ragdollPose(a.ragdoll, a.vx, verticalSpeed, a.vz, a.posePts);
        g.game.commands().ragdollCarry(a.ragdoll, a.vx, verticalSpeed, a.vz, 1);
        traits(a); a.reacting = true; a.reactSince = g.time; held.add(a); started++;
        if (a.body != 0) g.game.commands().moverInput(a.body, 0, 0, 0, 0, 0, MOVER_POSED);
        if (a instanceof Npc npc) g.ai.desire(npc, rpg.ai.AiSystem.A_IDLE);
    }
    private static float footY(Snapshots.Ragdoll body) {
        return Math.min(body.p[RD_L_ANKLE * 3 + 1], body.p[RD_R_ANKLE * 3 + 1]);
    }

    /** The body's traits from its skills (1 = an ordinary adult at level 30 or so). */
    private void traits(Actor n) {
        float str = 0.75f + n.skills.level(Skill.STRENGTH) / 99f * 0.8f;
        float agi = 0.75f + n.skills.level(Skill.AGILITY) / 99f * 0.8f;
        float tough = 0.75f + n.skills.level(Skill.DEFENSE) / 99f * 0.8f;
        float end = 0.75f + n.skills.level(Skill.HEALTH) / 99f * 0.8f;
        g.game.commands().ragdollTraits(n.ragdoll, str, agi, tough, end);
    }

    /** Which of the body's fifteen points is nearest where the blow landed. */
    private int nearestPoint(Actor n, float px, float py, float pz) {
        float[] points = n.posePts;
        if (n.ragdoll != 0 && g.lastBodies != null) {
            var body = g.lastBodies.ragdoll(n.ragdoll);
            if (body != null) points = body.p;
        }
        if (points == null) return RD_NECK;
        int best = RD_NECK;
        float bd = Float.MAX_VALUE;
        for (int i = 0; i < RD_PARTICLES; i++) {
            float dx = points[i * 3] - px, dy = points[i * 3 + 1] - py, dz = points[i * 3 + 2] - pz, d = dx * dx + dy * dy + dz * dz;
            if (d < bd) { bd = d; best = i; }
        }
        return best;
    }

    /** After the engine's frame: where each held body is, and whether it is back on its feet. */
    public void afterFrame(Snapshots.Bodies bodies) {
        for (int i = held.size() - 1; i >= 0; i--) {
            Actor n = held.get(i);
            if (!n.alive || n.ragdoll == 0) { n.reacting = false; held.remove(i); continue; }
            Snapshots.Ragdoll rd = bodies.ragdoll(n.ragdoll);
            if (rd == null) {
                // Creation is queued for the next engine frame; a body missing after that grace period cannot recover.
                if (g.time - n.reactSince > 0.5) {
                    n.ragdoll = 0; // already absent in the authoritative snapshot: do not command a stale handle
                    cancel(n);
                    n.cs.staggered = 0;
                    if (n instanceof Npc npc) { npc.nextThink = 0; npc.hasGoalPos = false; }
                    recovered++;
                }
                continue;
            }
            // the person is where the body is: its mover follows (so others see, hear and path round it there)
            n.x = rd.comX; n.z = rd.comZ; n.y = footY(rd);
            if (n.body != 0) g.game.commands().moverInput(n.body, 0, 0, 0, 0, 0, MOVER_POSED);
            if (n.body != 0) g.game.commands().moverPlace(n.body, rd.comX, n.y, rd.comZ, 0, 0, 0);
            if ((rd.state == SYDNEY_FALL || rd.state == SYDNEY_DOWN) && !n.wentDown) {
                n.wentDown = true;
                // going down hard, the weapon may be knocked from the hand: it falls where the hand was
                var w = n.eq.get(rpg.item.ItemDef.Slot.MAIN);
                if (w != null && Math.floorMod(n.id * 7919 + (long) (g.time * 13), 100) < 45) {   // (about half the time; deterministic for a replay)
                    float hx = rd.p[RD_R_WRIST * 3], hz = rd.p[RD_R_WRIST * 3 + 2];
                    n.dropped = g.objects.drop(w, hx, n.y + 0.05f, hz);
                    n.eq.set(rpg.item.ItemDef.Slot.MAIN, null);
                    if (g.fx != null) g.fx.text("Disarmed!", n.x, n.y + 2.1f, n.z, 0xFFFFC040);
                }
            }
            boolean back = rd.outcome == OUTCOME_RECOVERED && rd.state == SYDNEY_STAND;
            boolean tooLong = g.time - n.reactSince > 14;
            boolean rolling = n.burnSince>=0 && g.time-n.burnSince>=3 && (g.chemistry.state(n.id)&CHEM_BURNING)!=0;
            if (!rolling && (back || tooLong || rd.outcome == OUTCOME_STUCK)) {
                // handed back to the figure, standing where the feet are, facing the way the trunk faces - but never
                // inside someone else (the body may have reeled into them): stepped out to arm's length
                float sx = rd.standX, sz = rd.standZ;
                for (rpg.actor.Actor o : g.actors()) {
                    if (o == n || !o.alive || o.dummy || o instanceof Npc other && !other.loaded) continue;
                    float ox = sx - o.x, oz = sz - o.z, ol = (float) Math.hypot(ox, oz), min = n.radius + o.radius + 0.1f;
                    if (ol >= min) continue;
                    if (ol < 1e-3f) { ox = (float) Math.sin(n.yaw); oz = (float) Math.cos(n.yaw); ol = 1; }
                    sx = o.x + ox / ol * min; sz = o.z + oz / ol * min;
                }
                n.x = sx; n.z = sz; n.yaw = rd.yaw;
                // back on their feet, the weapon knocked from their hand is the first thing they reach for
                if (n.dropped != null && !n.dropped.removed && n.eq.get(rpg.item.ItemDef.Slot.MAIN) == null && Math.hypot(n.dropped.x - sx, n.dropped.z - sz) < 3.5) {
                    var w = n.dropped.stack;
                    n.dropped.removed = true;
                    n.eq.set(rpg.item.ItemDef.Slot.MAIN, w);
                    g.state.commit("ItemTaken", n.id, n.dropped.id, DevMap.jurisdiction(sx, sz), 0, w + " picked up again by " + n.name);
                }
                n.dropped = null;
                if (n.body != 0) g.game.commands().moverPlace(n.body, sx, n.y, sz, 0, 0, 0);
                g.game.commands().ragdollControl(n.ragdoll, CONTROL_RELEASE, 0);
                g.game.commands().release(n.ragdoll);
                n.ragdoll = 0;
                if (n.body != 0) g.game.commands().moverInput(n.body, 0, 0, 0, 0, 80, 0);
                n.reacting = false;
                n.cs.staggered = 0.15f;
                if (n.wentDown) fallen++;
                n.wentDown = false;
                recovered++;
                held.remove(i);
                n.poseReset = true;
                if (n instanceof Npc npc) { npc.hasGoalPos = false; npc.nextThink = 0; }
            }
        }
    }

    /** Ends a reaction at once (the body is moved, reloaded or reset): the figure takes over where it stands. */
    public void cancel(Actor n) {
        if (!held.remove(n)) return;
        if (n.ragdoll != 0) { g.game.commands().ragdollControl(n.ragdoll, CONTROL_RELEASE, 0); g.game.commands().release(n.ragdoll); n.ragdoll = 0; }
        n.reacting = false; n.wentDown = false; n.poseReset = true;
        if (n.body != 0) g.game.commands().moverInput(n.body, 0, 0, 0, 0, 80, 0);
    }

    /** A held body was killed: the same body goes limp (the engine's DIE); it stays the dead body's ragdoll. */
    public boolean died(Actor n) {
        if (!n.reacting || n.ragdoll == 0) return false;
        g.game.commands().ragdollControl(n.ragdoll, CONTROL_DIE, 1);
        n.reacting = false;
        held.remove(n);
        return true;
    }
}
