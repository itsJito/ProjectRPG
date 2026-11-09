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
    /** (0.3.3) A fall already under way when this body was handed over (a rider thrown off a falling mount): counted from that height. */
    public void carry(Actor actor, float top) { Flight f = new Flight(); f.top = f.last = top; f.active = true; flights.put(actor.id, f); }
    /** How deep in water (m) a body's feet must be for the water to have taken its fall. */
    public static final float WATER_BREAKS = 0.25f;
    public static int damage(float blocks) { return blocks > 5 ? 2 + Math.max(0, (int)Math.floor(blocks - 5)) : 0; }
    public void afterFrame(Snapshots.Bodies bodies, float dt) {
        for (Actor actor : g.actors()) {
            if (!(actor == g.player || actor instanceof Npc n && n.loaded) || !actor.alive || actor.dummy) { flights.remove(actor.id); continue; }
            // (0.3.3) in the saddle the mount's fall is the ride's to judge (Riding.unseat): the two go down together
            if (g.traversal.busy(actor) || actor.gaseousT > 0 || actor.riding != 0 || actor == g.player && g.ui != null && g.ui.console.noclip) { reset(actor); continue; }
            Flight f = flights.computeIfAbsent(actor.id, id -> { Flight start = new Flight(); start.top = start.last = actor.y; return start; });
            var mover = bodies.mover(actor.body); var physical = actor.reacting ? bodies.ragdoll(actor.ragdoll) : null;
            float y = physical != null ? Math.min(physical.p[RD_L_ANKLE*3+1], physical.p[RD_R_ANKLE*3+1]) : mover == null ? actor.y : mover.y;
            boolean grounded = physical != null ? physical.grounded != 0 : mover != null && mover.grounded;
            // (0.3.5, gamefixes: players fell through water and died on its bed) water breaks a fall: a body whose feet
            // are a quarter metre into water has had its fall taken by it, whether it is still sinking or already
            // standing on the bottom - the fall is measured afresh from there
            float bx = physical != null ? physical.p[RD_L_ANKLE*3] : mover == null ? actor.x : mover.x, bz = physical != null ? physical.p[RD_L_ANKLE*3+2] : mover == null ? actor.z : mover.z;
            if (g.fluids != null && g.fluids.submerged(bx - 0.3f, y, bz - 0.3f, bx + 0.3f, y + 1.2f, bz + 0.3f) >= WATER_BREAKS) {
                if (f.active && f.top - y > 2) g.state.commit("FallBroken", actor.id, 0, rpg.world.DevMap.jurisdiction(bx, bz), 0, String.format("%.1f m into water", f.top - y));
                f.active = f.paid = false; f.top = f.last = y;
                continue;
            }
            if (!grounded) {
                f.active = true; f.top = Math.max(f.top, y);
                if (f.top - y >= 7 && !actor.reacting) { g.reactions.falling(actor, dt > 0 ? Math.min(-1, (y-f.last)/dt) : -8); }
            } else {
                float distance = f.top - y;
                if (f.active && !f.paid && damage(distance) > 0) { f.paid = true; g.combat.environmentalDamage(actor, damage(distance), "fall"); }
                if (!actor.reacting) { f.active = f.paid = false; f.top = y; }
            }
            f.last = y;
        }
    }
}
