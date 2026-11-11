package rpg.ai;

import rpg.Rpg;
import rpg.actor.Actor;
import rpg.actor.Actor.Combat.Move;
import rpg.combat.CombatSystem;
import rpg.interact.WorldObject;
import rpg.objects.Breakable;
import rpg.objects.GroundItem;
import rpg.world.DevMap;

/**
 * (0.3.8, gamefixes: NPCs should be able to interact with the crates, barrels and signs, and break them) What people
 * and monsters do with the loose wooden things about them:
 * <ul>
 * <li>a thief, an outlaw or a brute who finds someone's crate or barrel of goods with nobody watching breaks it open and
 * helps himself to what spills (it is vandalism and theft, and a witness who comes on it reports both);</li>
 * <li>a goblin or an orc on the prowl smashes what it comes across and takes what falls out;</li>
 * <li>anyone whose way is blocked by one shoves it aside - and one who cannot shift it, and is in a hurry or a fight,
 * breaks through it;</li>
 * <li>and a blow swung in a fight lands on whatever is in its way, a crate as much as a man (CombatSystem).</li>
 * </ul>
 */
public final class Smashing {
    /** How far off a crook notices something worth breaking open (m), and how near a watcher spoils it. */
    static final float SPOIL_RANGE = 22, WATCHED = 14;
    /** How close they stand to strike it (m from its middle), and how far round it they gather what spilled. */
    static final float STRIKE_AT = 1.05f, GATHER = 2.6f;

    private final Rpg g;
    private final AiSystem ai;

    Smashing(Rpg g, AiSystem ai) { this.g = g; this.ai = ai; }

    /** Whether this one breaks things open for what is in them (by trade or by nature). */
    static boolean crook(Npc n) { return n.role == Npc.Role.THIEF || n.role == Npc.Role.BANDIT || n.role == Npc.Role.BRUTE; }

    /** The nearest crate or barrel of someone else's goods it could break open unseen, or null. */
    Breakable spoil(Npc n) {
        if (g.breakables == null || !crook(n) || g.time < n.smashAfter) return null;
        Breakable best = null;
        float bd = SPOIL_RANGE;
        for (Breakable b : g.breakables.all) {
            if (b.broken || b.removed || b.form == Breakable.Form.SIGN || b.contents.stacks().isEmpty() || b.owner == n.id) continue;
            float d = (float) Math.hypot(b.x - n.x, b.z - n.z);
            if (d >= bd || Math.abs(b.y - n.y) > 3) continue;
            if (watched(n, b)) continue;
            bd = d; best = b;
        }
        return best;
    }

    /** Someone who would stop it or tell of it can see the spot (the law, its owner, any honest soul near), or the player is close. */
    private boolean watched(Npc n, Breakable b) {
        if (ai.lawInSight(n, b.x, b.z, WATCHED + 6) != null) return true;
        for (Npc o : g.npcs) {
            if (o == n || !o.alive || !o.loaded || crook(o)) continue;
            if ((o.id == b.owner || Brain.bystander(o)) && Math.hypot(o.x - b.x, o.z - b.z) < WATCHED && g.vox.sight(o.x, o.eyeY(), o.z, b.x, b.y + 0.3f, b.z)) return true;
        }
        return g.player.alive && Math.hypot(g.player.x - b.x, g.player.z - b.z) < 9;
    }

    /** The work of it: up to it, strike till it gives, then gather what fell out. */
    void act(Npc n) {
        WorldObject o = g.objects.get(n.smashTarget);
        if (!(o instanceof Breakable b)) { done(n, "nothing there to break"); return; }
        if (!b.broken) {
            if (watched(n, b)) { done(n, "someone's watching; leaving it"); n.smashAfter = g.time + 90; return; }
            if (!ai.brain.goTo(n, b.x, b.y, b.z, false, STRIKE_AT)) return;
            strikeAt(n, b);
            n.lastOutcome = "breaking open the " + b.form.title.toLowerCase();
            return;
        }
        // broken: what spilled, the best first
        GroundItem pick = null;
        int bv = -1;
        for (WorldObject w : g.objects.all()) {
            if (!(w instanceof GroundItem gi) || gi.removed || gi.dir != null) continue;
            if (Math.hypot(gi.x - b.x, gi.z - b.z) > GATHER || !n.canStore(gi.stack)) continue;
            if (gi.stack.value() > bv) { bv = gi.stack.value(); pick = gi; }
        }
        if (pick == null || n.smashTaken >= 4) { done(n, n.smashTaken > 0 ? "took " + n.smashTaken + " thing(s) from the broken " + b.form.title.toLowerCase() : "nothing in it worth carrying"); return; }
        if (!ai.brain.goTo(n, pick.x, pick.y, pick.z, false, 0.7f)) return;
        if (g.time - n.lastLootAt < 0.8) return;
        n.lastLootAt = g.time;
        if (pick.takeBy(g, n, g.state.newRequestId(), "hand").committed()) {
            n.smashTaken++;
            if (n.smashTaken == 1) ai.say(n, n.role == Npc.Role.THIEF ? "Fell off the back of a cart, this did." : "Mine now.");
        }
    }

    private void done(Npc n, String why) {
        n.lastOutcome = why;
        n.smashTarget = 0; n.smashTaken = 0;
        n.smashAfter = Math.max(n.smashAfter, g.time + 45);
        n.nextThink = 0;
        ai.desire(n, AiSystem.A_IDLE);
    }

    /** Facing it, a blow as it comes ready (a heavy one with a weapon in hand: it is wood, not a man). */
    void strikeAt(Npc a, Breakable b) {
        a.wantYaw = (float) Math.atan2(-(b.x - a.x), -(b.z - a.z));
        ai.desire(a, AiSystem.A_HOLD);
        if (a.cs.busy() || Math.abs(Actor.wrap(a.wantYaw - a.yaw)) > 0.45f || g.time < a.cs.lastAttack + 0.9) return;
        boolean armed = a.main() != null && !a.main().natural && a.main().damage > 0;
        g.combat.attack(a, false, armed ? Move.HEAVY : CombatSystem.lightMove(a, false));
    }

    /**
     * Its way is blocked: if one of them is what is in the way (just ahead, near), it is shoved aside - and one in a fight
     * or a hurry, who has shoved before and is still stuck, breaks through it. True when that was the trouble.
     */
    boolean blocked(Npc n) {
        if (g.breakables == null) return false;
        float fx = (float) -Math.sin(n.yaw), fz = (float) -Math.cos(n.yaw);
        for (Breakable b : g.breakables.all) {
            if (b.broken || b.body == 0) continue;
            float dx = b.x - n.x, dz = b.z - n.z, d = (float) Math.hypot(dx, dz);
            if (d > 1.6f || (dx * fx + dz * fz) / Math.max(0.01f, d) < 0.3f || Math.abs(b.y - n.y) > 1.6f) continue;
            boolean urgent = n.goal == Brain.Goal.FIGHT || n.goal == Brain.Goal.FLEE || n.goal == Brain.Goal.FIRE_RESPONSE;
            if (urgent && g.time - n.shovedAt < 6 && b.owner != n.id || crook(n) && g.time - n.shovedAt < 6) {
                n.smashTarget = b.id;
                strikeAt(n, b);
                n.lastOutcome = "breaking through the " + b.form.title.toLowerCase() + " in the way";
                return true;
            }
            // a shove: along the way they were going, sideways a little, as hard as a person can push
            float side = (n.id & 1) == 0 ? 0.45f : -0.45f;
            float px = fx - fz * side, pz = fz + fx * side;
            g.game.commands().rigidImpulse(b.body, b.x, b.y, b.z, px * 70, 8, pz * 70);
            n.shovedAt = g.time;
            n.lastOutcome = "shoving the " + b.form.title.toLowerCase() + " out of the way";
            if (g.sfx != null) g.sfx.play("wood.light", b.x, b.y, b.z, 0.4f, 0.85f, 14);
            g.state.commit("Shoved", n.id, b.id, DevMap.jurisdiction(b.x, b.z), 0, b.form.title);
            return true;
        }
        return false;
    }
}
