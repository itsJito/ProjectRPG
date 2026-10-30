package rpg.ai;

import java.util.Map;
import rpg.Rpg;
import rpg.actor.Actor;
import rpg.actor.Player;
import rpg.interact.WorldObject;
import rpg.objects.Fire;
import rpg.world.DevMap;

/**
 * Being noticed (gamefixes: stealth). Every NPC keeps an awareness of every actor it has caught sight or sound of, and
 * it climbs Unaware -> Curious -> Investigating -> Alert from what its senses actually get:
 * <ul>
 * <li>Sight: a cone (wide close in, narrower far out), three sight lines to the body (head, chest, knees) through the
 * real cells - walls stop them, glass does not, leaves and the smoke over a fire thin them - and then how easy that body
 * is to make out: the light on the block it stands in (sky light by the time of day, lamp and glow light, fires),
 * whether it creeps or runs, how far it is, and how sharp the watcher is (a guard is sharper, a sleeper nearly blind).</li>
 * <li>Hearing: noises (footsteps, blows, breaking glass) raise it towards Investigating with a rough idea of where -
 * never to Alert: a sound says "over there", not who.</li>
 * </ul>
 * What each level does is the mind's business (Brain): a curious NPC stops and looks, an investigating one goes to
 * where it last placed the noise or shape, an alert one knows exactly who and where - a guard engages the wanted, a
 * bandit attacks, anyone witnesses a crime. Crimes are witnessed only by someone who sees the culprit clearly now or is
 * already alert to them: a theft in a dark alley, crept, is seen by nobody.
 */
public final class Stealth {
    public enum Level { UNAWARE, CURIOUS, INVESTIGATING, ALERT }

    /** Awareness thresholds, and the exposure that is a clear sighting outright. */
    public static final float CURIOUS = 0.2f, INVESTIGATING = 0.5f, ALERT = 1f, CLEAR = 0.45f, MAX = 1.25f;
    /** How far eyes reach (metres) and how close a body is sensed whatever the cone. */
    public static final float RANGE = 24, CLOSE = 2.2f, CLOSE_CREEPING = 1.2f;
    /** Speech and other noises share the same coarse wall attenuation. */
    public static final float WALL_MUFFLE = 0.55f;

    /** One NPC's awareness of one actor: how much, where it last placed them, and when. */
    public static final class Aware {
        public float level, x, z, exposure;
        public double seenAt = -99, heardAt = -99, at = -99, changedAt = -99;
        /** When it was looked into and settled (an investigation that found the spot, and them or nobody, there). */
        public double settledAt = -1e9;   // (0.2.2: -99 read as "settled lately" for the first minutes of a game)
        /** Blood warrants a bounded conversation, not a permanent warrant or an endlessly renewed watch. */
        public double bloodQuestionUntil = -99, bloodQuestionAgain = -99;
        public float bloodSeen;
        /** (0.2.2) Until when the blood on them is explained to this NPC (it saw the blow land), and whose blow it was. */
        public double bloodKnownUntil = -99;
        public long bloodBy;
        public Level shown = Level.UNAWARE;
        public Level level() { return of(level); }
    }

    public static Level of(float v) { return v >= ALERT ? Level.ALERT : v >= INVESTIGATING ? Level.INVESTIGATING : v >= CURIOUS ? Level.CURIOUS : Level.UNAWARE; }

    private final Rpg g;
    /** Sight lines cast (profiling and the telemetry panel). */
    public long rays;

    Stealth(Rpg g) { this.g = g; }

    // ------------------------------------------------------------------ light
    /** The sky's share of light for the hour (the same curve the renderer lights the world with). */
    public static float daylight(double hour) {
        return astratto.weather.TimeSystem.daylight(hour);
    }

    /**
     * How lit the block an actor stands in is, 0 .. 1: the sky light that reaches the cell (none indoors, in full under
     * the open sky) by the time of day, the lamp and glow light flooded into it, and any fire close by.
     */
    public float light(Actor a) { return light(a.x, a.y + 0.2f, a.z); }

    /**
     * The fires burning this tick, gathered once (gamefixes: combat lagged - every sight line, three a look for every
     * pair of eyes and body, walked every object in the world twice over to find the few fires).
     */
    private final java.util.List<Fire> fires = new java.util.ArrayList<>();
    private double firesAt = Double.NaN;
    private java.util.List<Fire> fires() {
        if (firesAt != g.time) {
            firesAt = g.time;
            fires.clear();
            for (WorldObject o : g.objects.all()) if (o instanceof Fire f && !f.removed) fires.add(f);
        }
        return fires;
    }

    public float light(float x, float y, float z) {
        int bx = (int) Math.floor(x), by = (int) Math.floor(y), bz = (int) Math.floor(z);
        float l = Math.max(g.vox.skyLight(bx, by, bz) * daylight(g.state.hourOfDay()) * (1 - g.timeSystem.weather.overcast * 0.35f), g.vox.blockLight(bx, by, bz));
        for (Fire f : fires()) {
            float d = (float) Math.sqrt((f.x - x) * (f.x - x) + (f.y - y) * (f.y - y) + (f.z - z) * (f.z - z));
            if (d < 7) l = Math.max(l, 1 - d / 7f);
        }
        // a darkness spell over the spot
        if (g.magic != null && !g.magic.darkness.isEmpty()) l *= g.magic.dim(x, y, z);
        return Math.min(1, l);
    }

    /** An illusory sound (Minor Illusion): heard as someone there - those in earshot go to look at the spot. */
    public void illusion(float x, float y, float z, float radius) {
        boolean nt = g.notarget();
        if (nt) return;
        hear(g.player, x, y, z, radius);
    }

    // ------------------------------------------------------------------ sight
    /** How sharp an NPC's eyes are right now: a guard's are trained, a sleeper's are closed. */
    public float acuity(Npc n) {
        if (n.asleep()) return 0.12f;
        float base = n.role == Npc.Role.GUARD || n.role == Npc.Role.LAWMAN ? 1.2f : n.combatant() ? 1.1f : 1f;
        // searching, or still on edge after a search, the eyes are keener
        if (n.hunt != null || g.time < n.vigilantUntil) base *= 1.25f;
        return base;
    }

    /**
     * How exposed an actor is to an NPC's eyes this instant, 0 .. 2 (0: not in sight at all). At or above
     * {@link #CLEAR} the NPC sees them plainly; below it a glimpse that builds awareness over time.
     */
    public float exposure(Npc n, Actor a) {
        if (!n.alive || !n.loaded) return 0;
        return exposure(n, acuity(n), a);
    }

    /** The same sight for any watcher (people, animals): its eyes, its heading, how sharp they are. */
    public float exposure(Actor n, float acuity, Actor a) {
        if (!n.alive || a == null || !a.alive || a == n) return 0;
        float dx = a.x - n.x, dz = a.z - n.z, d = (float) Math.hypot(dx, dz);
        if (d > RANGE) return 0;
        float cone = 1;
        // someone right beside you is sensed whichever way you face - unless they creep
        float close = a.sneaking ? CLOSE_CREEPING : CLOSE;
        if (d > close) {
            float fx = (float) -Math.sin(n.yaw), fz = (float) -Math.cos(n.yaw);
            float cos = (fx * dx + fz * dz) / d;
            // close in, the edge of the eye (90 degrees each side); far out, only what is ahead (about 50 degrees)
            float edge = d < 6 ? 0f : 0.65f * Math.min(1, (d - 6) / 14f);
            if (cos < edge) return 0;
            if (cos < edge + 0.25f) cone = 0.5f;   // the corner of the eye: half as noticed
        }
        float ey = n.eyeY();
        boolean low = a.sneaking || a.crouched;
        float h = a.eyeY() - a.y;
        float[] at = {h, a.chestY() - a.y, 0.45f}, weight = {0.4f, 0.4f, 0.2f};
        float seen = 0;
        for (int i = 0; i < 3; i++) {
            rays++;
            float t = g.vox.seeThrough(n.x, ey, n.z, a.x, a.y + at[i], a.z, GLASS_REACH);
            if (t > 0) t *= smoke(n.x, ey, n.z, a.x, a.y + at[i], a.z);
            seen += weight[i] * t;
        }
        if (seen <= 0.01f) return 0;
        float speed = (float) Math.hypot(a.vx, a.vz);
        float stance = low ? 0.42f : 1;
        float motion = speed > 4.5f ? 1.45f : speed > 0.5f ? 1f : 0.75f;
        float lit = Math.max(0.07f, light(a));
        // near at hand a body is hard to miss in any but the darkest place: a strong, smooth bonus inside six metres
        float dist = d <= close ? 2f : Math.max(0.1f, 1.1f - d / RANGE) + Math.max(0, (6 - d) / 6f) * 0.9f;
        // past the recognising range a figure is a shape, not a face: less sure the farther it is
        if (d > RECOGNISE) dist *= 1 - 0.4f * Math.min(1, (d - RECOGNISE) / (RANGE - RECOGNISE));
        float mist = a.gaseousT > 0 ? 0.15f : 1;   // Gaseous Form: a shimmer, hard to see
        float e = Math.min(2, seen * cone * stance * motion * lit * dist * acuity * mist);
        // (gamefixes: NPCs missed the player standing right in front of them) within ten blocks, anyone going about
        // upright in plain view - most of the body in the line of sight, ahead, not in pitch dark - is recognised at once
        if (d <= RECOGNISE && !low && mist == 1 && cone == 1 && seen >= 0.55f && lit >= 0.12f && acuity >= 0.9f) e = Math.max(e, CLEAR * 1.6f);
        return e;
    }
    /** How far a figure in plain view is recognised outright; how far through glass anything is made out. */
    public static final float RECOGNISE = 10, GLASS_REACH = 5;

    /** Smoke over the fires: a sight line through the column above one is thinned. */
    private float smoke(float ax, float ay, float az, float bx, float by, float bz) {
        float t = 1;
        for (Fire f : fires()) {
            float dx = bx - ax, dz = bz - az, len2 = dx * dx + dz * dz;
            if (len2 < 1e-4f) continue;
            float s = Math.max(0, Math.min(1, ((f.x - ax) * dx + (f.z - az) * dz) / len2));
            float px = ax + dx * s - f.x, pz = az + dz * s - f.z, py = ay + (by - ay) * s - f.y;
            if (px * px + pz * pz < 0.8f && py > 0.7f && py < 4.5f) t *= 0.45f;
        }
        return t;
    }

    /** Whether an NPC sees an actor now: plainly, or at all once it is already alert to them. */
    public boolean sees(Npc n, Actor a) {
        if (a == g.player && g.notarget()) return false;
        Aware w = n.aware.get(a.id);
        float e = w != null && g.time - w.at < 0.25 ? w.exposure : exposure(n, a);
        return e >= CLEAR || e > 0 && w != null && w.level >= ALERT;
    }

    public Aware of(Npc n, Actor a) { return n.aware.get(a.id); }
    public Level level(Npc n, Actor a) { Aware w = n.aware.get(a.id); return w == null ? Level.UNAWARE : w.level(); }

    // ------------------------------------------------------------------ the senses, each frame
    private final Map<Long, Double> nextLook = new java.util.HashMap<>();

    /** 0..1, fixed for an id and well spread between neighbouring ids (golden ratio): a beat's offset. */
    public static double phase(long id) { double v = id * 0.6180339887; return v - Math.floor(v); }

    /** Five looks a second per NPC: every actor in reach is weighed, awareness rises with what is seen and fades without. */
    void update(Npc n, float dt) {
        // (0.2.3, gamefixes: constant stutter) each NPC looks on its own beat - they all used to start on the same frame
        // and stay in step, every look in town (each one weighing every actor) landing on one frame in five
        Double at = nextLook.get(n.id);
        if (at == null) { nextLook.put(n.id, g.time + phase(n.id) * 0.2); return; }
        double due = at;
        if (g.time < due) return;
        float step = (float) Math.min(0.5, g.time - due + 0.2);
        nextLook.put(n.id, Math.max(due + 0.2, g.time + 0.05));
        for (Actor a : g.actors()) {
            if (a == n || !a.alive || a.dummy) continue;
            // animals going about their lives are no one's concern; one that menaces people is
            if (a instanceof rpg.animal.Animal an && !an.dangerous() && !n.aware.containsKey(a.id)) continue;
            float d = n.distTo(a);
            Aware w = n.aware.get(a.id);
            if (d > RANGE && w == null) continue;
            float e = d > RANGE ? 0 : exposure(n, a);
            if (w == null) { if (e <= 0) continue; w = new Aware(); n.aware.put(a.id, w); }
            w.exposure = e; w.at = g.time;
            if (e > 0) {
                // a clear sighting makes it certain at once; a glimpse adds up
                w.level = Math.min(MAX, w.level + e * 2.4f * step);
                if (e >= CLEAR * 1.6f) w.level = Math.max(w.level, ALERT);
                w.x = a.x; w.z = a.z; w.seenAt = g.time;
                if (a == g.player && (e >= CLEAR || w.level >= ALERT))
                    n.playerSighting = new Npc.Sighting(n.id, a.x, a.z, a.vx, a.vz, g.time);
            } else {
                // out of sight it fades: slowly while it was sure, faster from a mere notion
                double since = g.time - Math.max(w.seenAt, w.heardAt);
                if (since > 3) w.level -= (w.level >= ALERT ? 0.05f : 0.09f) * step;
            }
            changed(n, a, w);
            if (w.level <= 0) n.aware.remove(a.id);
        }
    }

    /**
     * A noise: everyone in earshot (walls muffle it) grows aware of whoever made it, towards Investigating at most, and
     * places it roughly - closer is surer.
     */
    public void hear(Actor source, float x, float y, float z, float radius) { hear(source, x, y, z, radius, 0.5f); }

    /**
     * A noise, with how loud a kind it is (footsteps are faint, blows and screams loud). At night an awake ear is
     * keener and a sound means more - the streets should be quiet; by day in a town the ordinary bustle of feet is no
     * cause for alarm, while anything violent always is.
     */
    public void hear(Actor source, float x, float y, float z, float radius, float loudness) {
        if (source == null || radius <= 0 || source == g.player && g.notarget()) return;
        double hour = g.state.hourOfDay();
        boolean night = hour >= 21 || hour < 5.5;
        for (Npc n : g.npcs) {
            if (n == source || !n.alive || !n.loaded) continue;
            float d = (float) Math.sqrt((n.x - x) * (n.x - x) + (n.eyeY() - y) * (n.eyeY() - y) + (n.z - z) * (n.z - z));
            boolean asleep = n.asleep();   // in bed, not on the way to it
            float nightK = night && !asleep ? 1.35f : 1;
            boolean bustle = !night && loudness < 0.5f && !source.sneaking && !DevMap.WILDS.equals(DevMap.jurisdiction(n.x, n.z));
            float reach = radius * (asleep ? 0.5f : 1) * nightK;
            if (d < reach && d > 2.5f && !g.vox.clear(n.x, n.eyeY(), n.z, x, y + 1f, z)) reach *= WALL_MUFFLE;
            if (d >= reach) continue;
            Aware w = n.aware.computeIfAbsent(source.id, k -> new Aware());
            float loud = 1 - d / reach;
            float gain = (0.3f + 0.5f * loud) * nightK * (bustle ? 0.45f : 1) * (loudness >= 0.75f ? 1.4f : 1);
            if (loudness >= 0.75f) n.violenceHeardAt = g.time;
            // (the day's bustle is never more than something to glance at)
            float cap = bustle ? CURIOUS + 0.1f : INVESTIGATING + 0.2f;
            if (w.level < cap) w.level = Math.min(cap, w.level + gain);
            float err = Math.max(0.6f, d * 0.25f * (1.2f - loud));
            double ang = (g.time * 7.3 + n.id) % (2 * Math.PI);
            if (g.time - w.seenAt > 0.5) { w.x = x + (float) Math.cos(ang) * err; w.z = z + (float) Math.sin(ang) * err; }
            w.heardAt = g.time;
            changed(n, source, w);
        }
    }

    /** A level crossed: the NPC reacts in a word over its head, and the player's own are logged. */
    private void changed(Npc n, Actor a, Aware w) {
        Level now = w.level();
        if (now == w.shown) return;
        Level was = w.shown;
        w.shown = now;
        w.changedAt = g.time;
        if (now.ordinal() <= was.ordinal()) {
            if (now == Level.UNAWARE && a instanceof Player && was.ordinal() >= Level.INVESTIGATING.ordinal()) {
                n.say("Must have been nothing.", g.time + 2.5);
                g.state.commit("Awareness", n.id, a.id, DevMap.jurisdiction(n.x, n.z), 0, "UNAWARE");
            }
            return;
        }
        if (!(a instanceof Player)) return;
        switch (now) {
            case CURIOUS -> n.say("Hm?", g.time + 1.5);
            case INVESTIGATING -> n.say(n.combatant() ? "Who's there?" : "Is someone there?", g.time + 2.5);
            case ALERT -> n.say(n.hostileToPlayer ? "There you are!" : n.combatant() ? "I see you." : "Oh! It's you.", g.time + 2.5);
            default -> { }
        }
        g.state.commit("Awareness", n.id, a.id, DevMap.jurisdiction(n.x, n.z), 0, now.name());
    }

    /** The player's own standing, for the sneak meter: how exposed to the sharpest eye, and how far the most aware NPC has got. */
    public float mostExposed(Actor a) {
        float e = 0;
        for (Npc n : g.npcs) { Aware w = n.aware.get(a.id); if (w != null && g.time - w.at < 0.5) e = Math.max(e, w.exposure); }
        return e;
    }
    public Npc mostAware(Actor a) {
        Npc best = null; float lv = 0;
        for (Npc n : g.npcs) { Aware w = n.aware.get(a.id); if (n.alive && w != null && w.level > lv) { lv = w.level; best = n; } }
        return best;
    }

    /** What the NPC is most aware of that it cannot place for certain (the one worth looking into), or null. */
    public Map.Entry<Long, Aware> unresolved(Npc n) {
        Map.Entry<Long, Aware> best = null;
        for (Map.Entry<Long, Aware> e : n.aware.entrySet()) {
            Aware w = e.getValue();
            // placed for certain: seen plainly now, or alert and still in sight
            if (w.level < CURIOUS || w.exposure >= CLEAR || w.level >= ALERT && w.exposure > 0) continue;
            // a neighbour going about openly in the dusk is no mystery; a stranger, or anyone creeping, is
            Actor who = g.actor(e.getKey());
            // (a sound alone says nothing of who made it: only a glimpse of a familiar figure settles it)
            // (by day, though, the steps of the people one lives among are just the day going on)
            double hour = g.state.hourOfDay();
            boolean day = hour >= 6 && hour < 21;
            if (who != null && who.team == n.team && !who.sneaking && (w.seenAt >= w.heardAt || day)) continue;
            // by day a townsman walking openly is a townsman, whatever their trade: a thief does not take every passing
            // guard for the law closing in, nor a guard every passing thief for a prowler (gamefixes: NPCs left their
            // routines all morning - the thief hid from each guard, crouched, and the guards came to see why, and so on)
            boolean resident = who instanceof Npc person && !person.hostileToPlayer && !ZonePolicy.outlaw(person) && person.summonOwner == 0;
            if (resident && !who.sneaking && day) continue;
            // and so is the player, out openly by day in a town where nothing is known against them (half-seen at a
            // distance, a figure in the street is someone in the street - not something to go and look into)
            if (who == g.player && !who.sneaking && day && !DevMap.WILDS.equals(DevMap.jurisdiction(who.x, who.z)) && n.crimeKnown(who.id) == null
                    && g.law.bounty(who.id, DevMap.jurisdiction(who.x, who.z)) <= 0) continue;
            // looked into lately: no mystery again for a while, unless they are creeping about now - and someone known by
            // sight already found creeping about, with nothing known against them, is not a fresh mystery for minutes
            if (g.time - w.settledAt < 30 && (who == null || !who.sneaking)) continue;
            boolean known = who instanceof Npc person && !person.hostileToPlayer && person.summonOwner == 0;
            if (known && g.time - w.settledAt < 300 && n.crimeKnown(who.id) == null) continue;
            // the player too, once looked into and found going about openly: not a fresh mystery again for minutes (a
            // border knight went back to look at the same figure standing in the same place all morning)
            if (who == g.player && !who.sneaking && g.time - w.settledAt < 300 && n.crimeKnown(who.id) == null) continue;
            if (who instanceof rpg.animal.Animal an && !an.dangerous()) continue;
            if (best == null || w.level > best.getValue().level) best = e;
        }
        return best;
    }
}
