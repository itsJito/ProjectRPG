package rpg.interact;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import rpg.Chat;
import rpg.Rpg;
import rpg.actor.Actor;

/**
 * The interaction pipeline (GDD 3.2): acquire the target under the crosshair (raycast first, then a small assist cone,
 * line of sight always), build its options, and carry out requests - each with a fresh request id so a repeated input
 * cannot transact twice - revalidating at the commit. Timed work runs here too, for the player and NPCs alike.
 */
public final class Interactions {
    private final Rpg g;
    public final Map<Long, Work> work = new HashMap<>();
    public Interactable focus;
    public float focusDist;
    public List<Option> focusOptions = List.of();
    public long requests, committed, rejected, interrupted, duplicates;
    public String lastOutcome = "";

    public Interactions(Rpg g) { this.g = g; }

    /** Every interactable: world objects and living NPCs. */
    /** (0.4.0, optimisation) What could be looked at within r of (x, z): the near objects and the living about. */
    public List<Interactable> near(float x, float z, float r) {
        List<Interactable> l = new ArrayList<>();
        for (var object : g.objects.near(x, z, r)) if (object.exists() && !(object instanceof rpg.world.Foliage.Patch)) l.add(object);
        for (var n : g.npcs) if (n.alive && n.loaded && Math.abs(n.x - x) <= r && Math.abs(n.z - z) <= r) l.add(n);
        if (g.monsters != null) for (var m : g.monsters.all()) if (m.alive && m.loaded && Math.abs(m.x - x) <= r && Math.abs(m.z - z) <= r) l.add(m);
        if (g.animals != null) for (var a : g.animals.all()) if (a.exists() && Math.abs(a.x - x) <= r && Math.abs(a.z - z) <= r) l.add(a);
        if (g.fishery != null) for (var f : g.fishery.live) if (f.alive && Math.abs(f.x - x) <= r && Math.abs(f.z - z) <= r) l.add(f);
        return l;
    }
    public List<Interactable> all() {
        List<Interactable> l = new ArrayList<>();
        // (0.2.2, gamefixes: the yellow marker still hovered over plants) foliage has nothing to offer since Examine went:
        // it is never a target
        for (var object : g.objects.all()) if (object.exists() && !(object instanceof rpg.world.Foliage.Patch)) l.add(object);
        for (var n : g.npcs) if (n.alive && n.loaded) l.add(n);
        // monsters are in the sights too (gamefixes: the slay command could not find one; nor could Examine)
        if (g.monsters != null) for (var m : g.monsters.all()) if (m.alive && m.loaded) l.add(m);
        if (g.animals != null) for (var a : g.animals.all()) if (a.exists()) l.add(a);
        // (0.3.6, gamefixes: fish had no health bars) the fish near the player can be looked at and aimed at like any creature
        if (g.fishery != null) for (var f : g.fishery.live) if (f.alive && Math.abs(f.x - g.player.x) < 40 && Math.abs(f.z - g.player.z) < 40) l.add(f);
        return l;
    }

    // ------------------------------------------------------------------ requests
    public Result request(Actor a, Interactable t, String actionId) { return request(a, t, actionId, g.state.newRequestId()); }

    public Result request(Actor a, Interactable t, String actionId, long requestId) {
        requests++;
        if (g.state.alreadyCommitted(requestId)) { duplicates++; return Result.no("That request was already carried out."); }
        Result r;
        if (t == null || !t.exists()) r = Result.no("That's no longer there.");
        else r = t.perform(g, a, actionId, requestId);
        switch (r.status()) {
            case COMMITTED -> committed++;
            case REJECTED -> rejected++;
            default -> { }
        }
        lastOutcome = (t == null ? "?" : t.label()) + " / " + actionId + " -> " + r.status() + (r.reason() == null ? "" : " (" + r.reason() + ")");
        if (a == g.player && r.rejected() && r.reason() != null) { g.chat.add(r.reason(), Chat.Kind.WARN); if (g.sfx != null) g.sfx.ui("fail", 0.4f); }
        return r;
    }

    /** (0.3.1) A target's options, highest priority first (the order they were listed in breaks ties). */
    public static List<Option> ranked(List<Option> opts) {
        if (opts.size() < 2) return opts;
        List<Option> l = new ArrayList<>(opts);
        l.sort((p, q) -> Integer.compare(q.priority(), p.priority()));
        return l;
    }

    /**
     * The option the primary button takes from a ranked list: the highest-priority enabled one; failing that the first
     * (whose reason is shown). (0.3.5) An option of negative priority is offered on the menu only - it is never what a
     * plain press does (picking a pocket is a choice, not what pressing at a person means).
     */
    public static Option primaryOf(List<Option> ranked) {
        for (Option o : ranked) if (o.enabled() && o.priority() >= 0) return o;
        for (Option o : ranked) if (o.priority() >= 0) return o;
        return null;
    }

    /** The primary button: the highest-priority enabled option, or the reason none is. */
    public Result primary(Actor a, Interactable t) {
        if (t == null) return Result.no(null);
        Option o = primaryOf(ranked(t.options(g, a)));
        return o == null ? Result.no(null) : request(a, t, o.id());
    }

    // ------------------------------------------------------------------ timed work
    public void begin(Work w) {
        Work old = work.get(w.actor.id);
        if (old != null && old != w) interrupt(old, "started something else");
        work.put(w.actor.id, w);
        w.actor.working = w;
    }

    public Work of(Actor a) { return a == null ? null : work.get(a.id); }

    public void cancel(Actor a, String why) { Work w = work.get(a.id); if (w != null) interrupt(w, why); }

    private void interrupt(Work w, String why) {
        work.remove(w.actor.id);
        if (g.fishery != null) g.fishery.reel(w.actor);
        if (w.actor.working == w) w.actor.working = null;
        interrupted++;
        g.state.commit("WorkInterrupted", w.actor.id, w.target.id(), null, 0, w.action + " (" + why + ") after " + w.commits + " commits");
    }

    public void update(float dt) {
        for (Iterator<Work> it = new ArrayList<>(work.values()).iterator(); it.hasNext(); ) {
            Work w = it.next();
            if (work.get(w.actor.id) != w) continue;
            Actor a = w.actor;
            boolean moved = Math.hypot(a.x - w.startX, a.z - w.startZ) > 0.75;
            boolean hurt = a.cs.lastHurt > w.startedAt;
            if (!a.alive || !w.target.exists() || moved || hurt || a.cs.busy()) {
                interrupt(w, !a.alive ? "dead" : !w.target.exists() ? "target gone" : moved ? "moved" : hurt ? "hit" : "busy");
                if (a == g.player && (moved || hurt)) g.chat.say("You stop " + w.verb.toLowerCase() + ".");
                continue;
            }
            w.t += dt;
            if (w.t < w.duration) continue;
            Result r = w.commit.get();
            // (0.3.5) a wait that has not come to anything yet (a line with no fish on it): kept up, looked at again shortly
            if (r.status() == Result.Status.STARTED) { w.t = Math.max(0, w.duration - 0.6f); continue; }
            w.commits++;
            if (r.committed()) committed++; else rejected++;
            if (r.rejected() && a == g.player && r.reason() != null) g.chat.add(r.reason(), Chat.Kind.WARN);
            if (r.committed() && w.repeat && (w.limit <= 0 || w.commits < w.limit)) w.t = 0;
            else { work.remove(a.id); if (a.working == w) a.working = null; }
        }
    }

    // ------------------------------------------------------------------ focus
    /** Finds the target under the crosshair from a camera ray, for an actor. */
    public void computeFocus(Actor a, float ox, float oy, float oz, float dx, float dy, float dz) {
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        dx /= len; dy /= len; dz /= len;
        float maxRange = g.cfg.inspectRange + (float) Math.hypot(a.x - ox, a.z - oz) + 1;
        var hit = g.vox.ray(ox, oy, oz, dx, dy, dz, maxRange, true);
        float wall = hit.hit ? hit.dist : maxRange;
        // someone standing in a doorway is not hidden by the open leaf swung beside them (gamefixes)
        var past = hit.hit && hit.door != null && hit.door.open ? g.vox.ray(ox, oy, oz, dx, dy, dz, maxRange, true, false) : hit;
        float wallForActors = past.hit ? past.dist : maxRange;
        // a fish is seen through the water's surface (the ray above stops at it): its own wall is the first solid
        float wallForFish = -1;
        Interactable best = null;
        float bestT = Float.MAX_VALUE;
        // (0.3.6, optimization) what is within reach, gathered once for both looks (it was every object in the world, twice a frame)
        float reach = g.cfg.inspectRange + (float) Math.hypot(a.x - ox, a.z - oz) + 3;
        List<Interactable> near = new ArrayList<>();
        for (Interactable t : near(a.x, a.z, reach + 2)) if (Math.abs(t.ax() - a.x) <= reach && Math.abs(t.az() - a.z) <= reach) near.add(t);
        // the ray: the nearest focus volume it passes through before the wall it strikes
        for (Interactable t : near) {
            // (0.3.6, gamefixes: riding flickered) the mount one sits on is never in one's own sights: the marker and the
            // prompt over it came and went as the view swung across its neck
            if (t == a || a.riding != 0 && t instanceof rpg.animal.Animal ridden && ridden.id == a.riding) continue;
            float r = t.focusRadius();
            if (Math.hypot(t.ax() - a.x, t.az() - a.z) > g.cfg.inspectRange + r) continue;
            float entry = t.focusEntry(ox, oy, oz, dx, dy, dz);
            float limit = t instanceof Actor ? wallForActors : wall;
            if (t instanceof rpg.fishing.Fish) {
                if (wallForFish < 0) { var dry = g.vox.ray(ox, oy, oz, dx, dy, dz, maxRange, false); wallForFish = dry.hit ? dry.dist : maxRange; }
                limit = wallForFish;
            }
            if (entry <= limit + 0.35f && entry <= maxRange && entry < bestT) { bestT = entry; best = t; }
        }
        // the assist: the nearest eligible anchor in a small cone, if it can be seen
        if (best == null) {
            float bestCos = g.cfg.assistCos;
            for (Interactable t : near) {
                if (t == a || a.riding != 0 && t instanceof rpg.animal.Animal ridden && ridden.id == a.riding) continue;
                float rx = t.ax() - ox, ry = t.ay() - oy, rz = t.az() - oz, d = (float) Math.sqrt(rx * rx + ry * ry + rz * rz);
                if (d < 1e-3f || Math.hypot(t.ax() - a.x, t.az() - a.z) > g.cfg.inspectRange) continue;
                float c = (rx * dx + ry * dy + rz * dz) / d;
                if (c <= bestCos) continue;
                // seen if any part of its column is (a head over a counter will do)
                boolean seen = false;
                for (float up : t.focusColumn()) if (g.vox.sight(ox, oy, oz, t.ax(), t.ay() + up, t.az())) { seen = true; break; }
                if (seen) { bestCos = c; best = t; }
            }
        }
        focus = best;
        focusDist = best == null ? 0 : (float) Math.hypot(best.ax() - a.x, best.az() - a.z);
        focusOptions = best == null ? List.of() : ranked(best.options(g, a));
    }
}
