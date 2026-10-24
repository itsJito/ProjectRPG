package rpg.combat;

import astratto.bridge.Snapshots;
import java.util.HashMap;
import java.util.Map;
import rpg.Rpg;
import rpg.actor.Actor;
import rpg.ai.Npc;
import static astratto.bridge.AstrattoNative.*;

/** Measures real unsupported descent, independently of animation, and pays for each landing once. */
public final class Falling {
    private static final class Flight { float top, last; boolean active, paid; }
    private final Map<Long, Flight> flights = new HashMap<>();
    private final Rpg g;
    public Falling(Rpg g) { this.g = g; }
    public void reset(Actor actor) { flights.remove(actor.id); }
    public static int damage(float blocks) { return blocks > 5 ? 2 + Math.max(0, (int)Math.floor(blocks - 5)) : 0; }
    public void afterFrame(Snapshots.Bodies bodies, float dt) {
        for (Actor actor : g.actors()) {
            if (!(actor == g.player || actor instanceof Npc n && n.loaded) || !actor.alive || actor.dummy) { flights.remove(actor.id); continue; }
            if (g.traversal.busy(actor) || actor.gaseousT > 0 || actor == g.player && g.ui != null && g.ui.console.noclip) { reset(actor); continue; }
            Flight f = flights.computeIfAbsent(actor.id, id -> { Flight start = new Flight(); start.top = start.last = actor.y; return start; });
            var mover = bodies.mover(actor.body); var physical = actor.reacting ? bodies.ragdoll(actor.ragdoll) : null;
            float y = physical != null ? Math.min(physical.p[RD_L_ANKLE*3+1], physical.p[RD_R_ANKLE*3+1]) : mover == null ? actor.y : mover.y;
            boolean grounded = physical != null ? physical.grounded != 0 : mover != null && mover.grounded;
            if (!grounded) {
                f.active = true; f.top = Math.max(f.top, y);
                if (f.top - y > 8 && !actor.reacting) { g.reactions.falling(actor, dt > 0 ? Math.min(-1, (y-f.last)/dt) : -8); }
            } else {
                float distance = f.top - y;
                if (f.active && !f.paid && damage(distance) > 0) { f.paid = true; g.combat.environmentalDamage(actor, damage(distance), "fall"); }
                if (!actor.reacting) { f.active = f.paid = false; f.top = y; }
            }
            f.last = y;
        }
    }
}
