package rpg.objects;

import java.util.Map;
import rpg.Rpg;
import rpg.item.Recipes;

/**
 * A fire someone lit (Firemaking): it cooks like a range, lights its surroundings, and burns out after a time that
 * grows with the firelighter's level and the logs used. When it goes out it leaves nothing.
 */
public final class Fire extends Station {
    public double outAt;
    private double emitAt;
    private boolean attributed = true;
    public void retireAttribution() { attributed = false; }

    public Fire(long id, float x, float y, float z, double outAt, long owner) {
        super(id, "fire", "Fire", Recipes.Station.RANGE, x, y, z);
        this.outAt = outAt;
        this.owner = owner;
    }

    public void update(Rpg g, float dt) {
        if (removed) return;
        if (g.state.minutes >= outAt) {
            removed = true;
            g.state.commit("FireBurntOut", 0, id, jurisdiction(), 0, "at " + (int) x + "," + (int) z);
            if (g.fx != null) g.fx.puff(x, y, z);
            return;
        }
        if (g.time >= emitAt) { emitAt = g.time + 0.5; g.chemistry.emit(attributed ? g.actor(owner) : null, astratto.bridge.AstrattoNative.CHEM_FIRE, x, y + 0.3f, z, 1, 1, 0.6f); }
    }

    @Override public String examine(Rpg g) { return "A fire. It will burn for about " + Math.max(0, (int) (outAt - g.state.minutes)) + " more minutes."; }

    @Override public void load(Rpg g, Map<String,Object> saved) {
        super.load(g,saved); attributed=astratto.util.Json.bool(saved,"attributed",true);
    }

    @Override public Map<String, Object> save() { Map<String, Object> m = super.save(); m.put("x", x); m.put("y", y); m.put("z", z); m.put("outAt", outAt); m.put("attributed",attributed); return m; }
}
