package rpg.interact;

import java.util.HashMap;
import java.util.Map;
import rpg.Rpg;
import rpg.actor.Actor;
import rpg.world.DevMap;

/**
 * A persistent thing in the world: a stable entity id, a definition id, an authoritative position, an owner, and the
 * shared checks every action revalidates at commit - that the actor is alive, in range and can reach it.
 */
public abstract class WorldObject implements Interactable {
    public final long id;
    public final String def;
    public String name;
    public float x, y, z;
    public long owner;
    public boolean removed;
    public float charred;

    protected WorldObject(long id, String def, String name, float x, float y, float z) {
        this.id = id; this.def = def; this.name = name; this.x = x; this.y = y; this.z = z;
    }

    @Override public long id() { return id; }
    @Override public String label() { return name; }
    @Override public float ax() { return x; }
    @Override public float ay() { return y; }
    @Override public float az() { return z; }
    @Override public boolean exists() { return !removed; }
    @Override public long owner() { return owner; }

    public String jurisdiction() { return DevMap.jurisdiction(x, z); }

    public float dist(Actor a) {
        float dx = a.x - x, dz = a.z - z, dy = (a.y + 1f) - y;
        return (float) Math.sqrt(dx * dx + dz * dz + Math.max(0, Math.abs(dy) - 1f) * Math.max(0, Math.abs(dy) - 1f));
    }

    /** Null when the actor can act on this from where they stand, else why not. */
    public String reach(Rpg g, Actor a, float range) {
        if (a == null || !a.alive) return "You can't do that right now.";
        if (removed) return "It's no longer there.";
        if (dist(a) > range + focusRadius()) return "You're too far away.";
        if (!g.canReach(a, this)) return "You can't reach that from here.";
        return null;
    }

    /** Mutable state for saving (the definition and position are rebuilt by the fixtures). */
    public Map<String, Object> save() { Map<String, Object> m = new HashMap<>(); m.put("removed", removed); m.put("owner", owner);m.put("charred",charred); return m; }
    public void load(Rpg g, Map<String, Object> m) { removed = astratto.util.Json.bool(m, "removed", false); owner = astratto.util.Json.lng(m, "owner", owner);charred=(float)astratto.util.Json.num(m,"charred",0); }

    @Override public String inspect(Rpg g) { return String.format("%s #%d def=%s at (%.1f, %.1f, %.1f) owner=%s %s", name, id, def, x, y, z, g.nameOf(owner), jurisdiction()); }
}
