package rpg.law;

import java.util.ArrayList;
import java.util.List;
import rpg.Rpg;
import rpg.actor.Actor;
import rpg.objects.Container;
import rpg.objects.Corpse;

/** A burial consumes one unfilled stone. Possessions live in that filled stone, never a duplicate cache. */
public final class Graveyards {
    public record Plot(String id, String jurisdiction, float x, float y, float z, int columns, int rows) {}
    public final List<Plot> plots = new ArrayList<>();
    private final Rpg g;
    public Graveyards(Rpg g) { this.g = g; }

    private void ensureSlots(Plot plot) {
        if (g.objects.everything().stream().anyMatch(o -> o instanceof Container c && c.burialPlot.equals(plot.id))) return;
        for (int i = 0; i < plot.columns * plot.rows; i++) {
            Container stone = new Container(g.state.newEntityId(), "headstone", "Gravestone", "memorial",
                    plot.x + i % plot.columns * 1.4f, plot.y, plot.z + i / plot.columns * 1.5f, 320, 0);
            stone.burialPlot = plot.id; g.objects.add(stone);
        }
    }
    private Container vacant(Plot plot) {
        ensureSlots(plot);
        for (var object : g.objects.all()) if (object instanceof Container stone && stone.kind.equals("memorial") && !stone.buried && stone.burialPlot.equals(plot.id)) return stone;
        return null;
    }
    public float[] destination(Plot plot) {
        Container stone = vacant(plot); return stone == null ? null : new float[]{stone.x, stone.z};
    }
    public Plot nearest(Actor carrier) {
        Plot best = null; float distance = Float.MAX_VALUE;
        for (Plot plot : plots) {
            Container stone = vacant(plot); if (stone == null) continue;
            float d = carrier.distTo(stone.x, stone.z);
            if (d < distance) { best = plot; distance = d; }
        }
        return best;
    }
    public boolean buryCarried(Actor carrier, Container stone) {
        if (stone.buried || stone.removed || carrier.distTo(stone.x, stone.z) > 3.2f) return false;
        Actor dead = g.actor(carrier.carryBody);
        if (!(dead instanceof rpg.ai.Npc npc) || npc.alive || !(g.objects.get(npc.corpse) instanceof Corpse corpse)) return false;
        for (Plot plot : plots) if (plot.id.equals(stone.burialPlot)) return fill(corpse, carrier, plot, stone);
        return false;
    }
    public boolean bury(Container body, Actor carrier, Plot plot) {
        if (plot == null) return false;
        Container stone = vacant(plot);
        return stone != null && carrier.distTo(stone.x, stone.z) <= 3.2f && fill(body, carrier, plot, stone);
    }
    private boolean fill(Container body, Actor carrier, Plot plot, Container stone) {
        if (body.removed || body.buried || stone.buried || stone.removed) return false;
        if (body instanceof Corpse corpse) {
            var dead = corpse.npc(g); if (dead == null) return false;
            var originals = new ArrayList<>(body.contents.stacks()); originals.addAll(dead.eq.stacks());
            if (!stone.contents.canAddAll(originals)) return false;
            g.carrying.drop(carrier, false);
            for (var item : originals) stone.contents.add(item);
            stone.name = dead.name + "'s gravestone"; stone.owner = body.owner;
            body.contents.clear(); dead.eq.clear();
            if (dead.ragdoll != 0) { g.game.commands().release(dead.ragdoll); dead.ragdoll = 0; }
            dead.corpse = 0; body.removed = true; corpse.delivered = true; stone.buried = true;
            for (var witness : g.npcs) if (witness.hunt != null && witness.hunt.target == 0 && witness.hunt.source == dead.id) {
                witness.hunt = null; witness.nextThink = 0;
            }
        } else {
            g.carrying.drop(carrier, false);
            body.x = stone.x; body.y = stone.y; body.z = stone.z; body.heldBy = 0;
            body.buried = true; body.burialPlot = plot.id; stone.removed = true;
            g.undertaker.toCustody(body, "buried in " + plot.id);
        }
        g.state.commit("BodyBuried", carrier.id, body.id, plot.jurisdiction, 0, body.name);
        return true;
    }
}
