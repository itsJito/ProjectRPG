package rpg.law;

import java.util.ArrayList;
import java.util.List;
import rpg.Rpg;
import rpg.actor.Actor;
import rpg.objects.Container;
import rpg.objects.Corpse;

/** Registered burial plots; original possessions stay in one persistent, named memorial. */
public final class Graveyards {
    public record Plot(String id, String jurisdiction, float x, float y, float z, int columns, int rows) {}
    public final List<Plot> plots = new ArrayList<>();
    private final Rpg g;
    public Graveyards(Rpg g) { this.g = g; }
    public Plot nearest(Actor carrier) {
        Plot best = null; float distance = Float.MAX_VALUE;
        for (Plot plot : plots) {
            long occupied=g.objects.all().stream().filter(o->o instanceof Container c && c.buried && c.burialPlot.equals(plot.id)).count();
            if(occupied>=(long)plot.columns*plot.rows)continue;
            float d = (float) Math.hypot(carrier.x - plot.x, carrier.z - plot.z);
            if (d < distance) { best = plot; distance = d; }
        }
        return best;
    }
    public boolean bury(Container body, Actor carrier, Plot plot) {
        if (plot == null || body.removed || body.buried || carrier.distTo(plot.x, plot.z) > 3.2f) return false;
        long count = g.objects.all().stream().filter(o -> o instanceof Container c && c.buried && c.burialPlot.equals(plot.id)).count();
        float x = plot.x + (count % plot.columns) * 1.4f, z = plot.z + (count / plot.columns % plot.rows) * 1.5f;
        // Refuse a full plot instead of piling stones on top of previous graves.
        if (count >= (long) plot.columns * plot.rows) return false;
        g.carrying.drop(carrier, false);
        if (body instanceof Corpse corpse) {
            var dead = corpse.npc(g); if (dead == null) return false;
            var originals = new ArrayList<>(body.contents.stacks()); originals.addAll(dead.eq.stacks());
            Container memorial = new Container(g.state.newEntityId(), "memorial", dead.name + "'s gravestone", "memorial", x, plot.y, z, 320, body.owner);
            if (!memorial.contents.canAddAll(originals)) return false;
            for (var item : originals) memorial.contents.add(item);
            body.contents.clear(); dead.eq.clear();
            if (dead.ragdoll != 0) { g.game.commands().release(dead.ragdoll); dead.ragdoll = 0; }
            dead.corpse = 0; body.removed = true; corpse.delivered = true;
            memorial.buried = true; memorial.burialPlot = plot.id; g.objects.add(memorial);
        } else {
            body.x = x; body.y = plot.y; body.z = z; body.heldBy = 0; body.buried = true; body.burialPlot = plot.id;
            g.undertaker.toCustody(body, "buried in " + plot.id);
        }
        g.state.commit("BodyBuried", carrier.id, body.id, plot.jurisdiction, 0, body.name);
        return true;
    }
}
