package rpg.ai;

import rpg.Rpg;
import rpg.actor.Actor;
import rpg.item.ItemDef;
import rpg.world.DevMap;

/** Short movement commitments, chosen from visible threats, visible allies and actual incoming fire. */
final class CombatTactics {
    enum Kind { COVER, FLANK }
    static final class Plan {
        Kind kind;
        long target;
        float x, z, sourceX, sourceZ;
        double until;
    }
    private final Rpg g;
    CombatTactics(Rpg g) { this.g = g; }

    boolean move(Npc n, Actor foe, boolean visible) {
        Plan plan = n.tactic;
        if (plan != null && (plan.target != foe.id || g.time >= plan.until || n.stuck || !standable(plan.x, plan.z))) n.tactic = plan = null;
        if (plan != null) {
            // A moving attacker can expose cover. Reconsider only when actually seen.
            if (plan.kind == Kind.COVER && visible && !covered(foe.x, foe.eyeY(), foe.z, plan.x, n.eyeY(), plan.z)) n.tactic = plan = null;
        }
        if (plan == null && visible && g.time >= n.nextTactic && n.distTo(foe) > 3.5f && !n.cs.busy()) {
            n.nextTactic = g.time + 1.2;
            int allies = allies(n, foe);
            boolean fire = incoming(n) || n.cs.lastAttacker == foe.id && g.time - n.cs.lastHurt < 2 && ranged(foe);
            if (fire && (allies == 0 || n.hp < n.maxHp() * 0.65f || n.cs.reload > 0)) plan = cover(n, foe);
            if (plan == null && allies > 0 && n.stamina > 30 && n.distTo(foe) > 5) plan = flank(n, foe);
            if (plan != null) { n.tactic = plan; n.say(plan.kind == Kind.COVER ? "Taking cover!" : "Go! I'll go around!", g.time + 2); }
        }
        if (plan == null) return false;
        n.wantYaw = (float) Math.atan2(-(plan.sourceX - n.x), -(plan.sourceZ - n.z));
        g.combat.setBlock(n, false);
        n.cs.draw = 0;
        boolean arrived = Math.hypot(n.x - plan.x, n.z - plan.z) < 0.85;
        if (arrived && plan.kind == Kind.FLANK) { n.tactic = null; n.nextTactic = g.time + 2.5; return false; }
        if (arrived) {
            g.ai.desire(n, AiSystem.A_HOLD);
            n.lastOutcome = "in cover; waiting for an opening";
            // A short stay then a fresh decision: never track a hidden foe through cover.
            plan.until = Math.min(plan.until, g.time + 0.65);
        } else {
            g.ai.combatGoal(n, plan.x, plan.z);
            n.lastOutcome = plan.kind == Kind.COVER ? "moving into cover from incoming fire" : "flanking with visible allies";
        }
        return true;
    }

    int allies(Npc n, Actor foe) {
        int count = 0;
        for (Npc other : g.npcs) if (other != n && other.loaded && other.alive && other.team == n.team && other.combatant()
                && other.fightTarget == foe.id && n.distTo(other) < 12 && g.ai.canSee(n, other)) count++;
        return count;
    }

    private boolean incoming(Npc n) {
        for (var p : g.combat.projectiles) {
            if (p.done || p.owner == n || p.owner.team != 0 && p.owner.team == n.team) continue;
            float vx = p.vx, vy = p.vy, vz = p.vz, speed2 = vx * vx + vy * vy + vz * vz;
            if (speed2 < 1) continue;
            float t = ((n.x - p.x) * vx + (n.chestY() - p.y) * vy + (n.z - p.z) * vz) / speed2;
            if (t < 0 || t > 0.65f) continue;
            float dx = p.x + vx * t - n.x, dy = p.y + vy * t - n.chestY(), dz = p.z + vz * t - n.z;
            if (dx * dx + dy * dy + dz * dz < 4 && g.vox.sight(n.x, n.eyeY(), n.z, p.x, p.y, p.z)) return true;
        }
        return false;
    }

    Plan cover(Npc n, Actor foe) {
        Plan best = null; float score = Float.MAX_VALUE;
        for (int radius = 2; radius <= 7; radius++) for (int i = 0; i < 16; i++) {
            double angle = i * Math.PI / 8;
            float x = (float) Math.floor(n.x + Math.cos(angle) * radius) + 0.5f;
            float z = (float) Math.floor(n.z + Math.sin(angle) * radius) + 0.5f;
            if (n.stuck && Math.hypot(n.gx - x, n.gz - z) < 1.5) continue;
            if (!standable(x, z) || !covered(foe.x, foe.eyeY(), foe.z, x, n.eyeY(), z) || occupied(n, x, z)) continue;
            float value = (float) Math.hypot(x - n.x, z - n.z) + 0.1f * Math.abs((float) Math.hypot(x - foe.x, z - foe.z) - 9);
            if (value < score) { score = value; best = plan(Kind.COVER, foe, x, z, 3.5); }
        }
        return best;
    }

    private Plan flank(Npc n, Actor foe) {
        double angle = Math.atan2(n.z - foe.z, n.x - foe.x);
        float radius = Math.min(8, Math.max(3, n.distTo(foe) * 0.65f));
        Plan best = null; float score = Float.MAX_VALUE;
        for (int side : new int[]{-1, 1}) {
            float x = foe.x + (float) Math.cos(angle + side * 0.85) * radius;
            float z = foe.z + (float) Math.sin(angle + side * 0.85) * radius;
            if (!standable(x, z) || occupied(n, x, z)) continue;
            float value = (float) Math.hypot(n.x - x, n.z - z);
            for (Npc ally : g.npcs) if (ally != n && ally.alive && ally.loaded && ally.team == n.team && g.ai.canSee(n, ally))
                value += 5 / Math.max(1, Math.hypot(ally.x - x, ally.z - z));
            if (value < score) { score = value; best = plan(Kind.FLANK, foe, x, z, 2.5); }
        }
        return best;
    }

    private Plan plan(Kind kind, Actor foe, float x, float z, double duration) {
        Plan plan = new Plan(); plan.kind = kind; plan.target = foe.id; plan.x = x; plan.z = z;
        plan.sourceX = foe.x; plan.sourceZ = foe.z; plan.until = g.time + duration;
        return plan;
    }
    private boolean covered(float sx, float sy, float sz, float x, float eyeY, float z) {
        return !g.vox.sight(sx, sy, sz, x, eyeY, z) && !g.vox.sight(sx, sy, sz, x, eyeY - 0.6f, z);
    }
    private boolean occupied(Npc n, float x, float z) {
        for (Npc a : g.npcs) if (a != n && a.loaded && a.alive && (Math.hypot(a.x - x, a.z - z) < 1 || a.tactic != null && Math.hypot(a.tactic.x - x, a.tactic.z - z) < 1)) return true;
        return false;
    }
    private boolean standable(float x, float z) {
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z), y = DevMap.FLOOR;
        return bx > 0 && bz > 0 && bx < DevMap.SX - 1 && bz < DevMap.SZ - 1 && g.vox.solid(bx, y - 1, bz)
                && !g.vox.solid(bx, y, bz) && !g.vox.solid(bx, y + 1, bz);
    }
    private static boolean ranged(Actor a) { return a.main() != null && (a.main().family == ItemDef.Family.BOW || a.main().family == ItemDef.Family.CROSSBOW); }
}
