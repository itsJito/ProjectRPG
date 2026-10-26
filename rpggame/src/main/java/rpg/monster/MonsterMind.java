package rpg.monster;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import rpg.Rpg;
import rpg.actor.Actor;
import rpg.actor.Actor.Combat.Move;
import rpg.ai.Npc;
import rpg.animal.Animal;
import rpg.monster.Monster.Known;
import rpg.monster.Monster.State;
import rpg.monster.MonsterKind.Archetype;
import rpg.skill.Skill;
import rpg.world.DevMap;

/**
 * The monster mind (GDD 3.14), alongside the human (rpg.ai.Brain) and animal (rpg.animal.AnimalMind) ones: instinct, not
 * schedule. Every monster carries live drives - hunger, fear, aggression, territory, fatigue, social pull, curiosity -
 * tuned by species and nudged per individual; it senses what is about it (sight, hearing, smell; never what it cannot),
 * remembers each thing it has sensed for as long as its kind remembers, and scores each as a threat:
 *
 *   threat = proximity + noise + damage dealt + visible weapon and armour + territory intrusion
 *
 * weighted by what the thing is to it (prey, rival, master, kin, something to fear). The highest weighted drive picks
 * the state - dormant, idle, patrol, alert, investigate, stalk, engage, flee, regroup, feed, rest, return - and the state
 * is carried out. Hostility is a state, never a label: distance, noise, hunger, numbers and fear all move it. There is no
 * free aggro - a monster needs a sense, a reason and a way to it - and no hard leash: the further from its lair, the
 * harder the pull home, and rage stretches it.
 */
public final class MonsterMind {
    private final Rpg g;
    private final Monsters sys;
    MonsterMind(Rpg g, Monsters sys) { this.g = g; this.sys = sys; }

    // ------------------------------------------------------------------ what is what to it
    /** What another thing is to this monster, before any sense of it: 0 nothing, negative something to fear, positive a foe or prey (and how much). */
    float regard(Monster m, Actor t) {
        if (t == m || !t.alive || t.dummy) return 0;
        if (t instanceof Monster o) {
            if (o.kind == m.kind) return 0;                                                    // kin
            if (o.kind == MonsterKind.DRAGON) return -1.5f;                                    // everything defers to a dragon
            if (m.kind.undead() && o.kind.undead()) return 0;                                  // the dead ignore the dead
            if (m.kind == MonsterKind.GOBLIN && o.kind == MonsterKind.ORC || m.kind == MonsterKind.ORC && o.kind == MonsterKind.GOBLIN) return 0;   // allies of convenience
            if (m.kind == MonsterKind.GOBLIN && (o.kind == MonsterKind.TROLL)) return -1;     // goblins flee trolls
            if (m.kind == MonsterKind.IMP && o.kind == MonsterKind.GOBLIN) return 0.8f;        // imps torment goblins
            if (m.kind == MonsterKind.TROLL && o.kind == MonsterKind.GOBLIN) return 0.9f;      // trolls bully goblins
            if (m.kind == MonsterKind.WEREWOLF) return 0.8f;                                   // werewolves prey on everything
            if (m.kind == MonsterKind.GIANT_SPIDER && (o.kind == MonsterKind.BAT || o.kind == MonsterKind.GOBLIN)) return 1f;
            return m.kind.undead() ? 0.2f : 0.5f;                                              // rivals: territory decides
        }
        if (t instanceof Animal a) {
            if (a.carcass()) return 0;
            if (m.kind.undead()) return 0.3f;                                                  // the dead attack animals only if closer than the living
            if (m.kind == MonsterKind.TROLL || m.kind == MonsterKind.GOBLIN) return a.species.w < 0.6f ? 0.1f : 0.6f;   // ignore small animals
            if (m.kind == MonsterKind.BAT) return a.species.w < 0.45f ? 0.5f : 0.1f;            // insects, small animals; nothing bigger
            return predator(m) ? 0.8f : 0.3f;
        }
        // people (the player is one): humans and monsters dislike each other
        if (m.kind == MonsterKind.GHOST) return 0.6f;
        return 1f;
    }

    /** Hunters of flesh: hunger turns them on prey. */
    static boolean predator(Monster m) { return m.kind == MonsterKind.WEREWOLF || m.kind == MonsterKind.GIANT_SPIDER || m.kind == MonsterKind.DRAGON || m.kind == MonsterKind.BAT || m.kind == MonsterKind.GOBLIN || m.kind == MonsterKind.TROLL; }

    // ------------------------------------------------------------------ senses
    /** Looks, listens and smells (four times a second): what it knows is refreshed, and what it no longer does is forgotten in time. */
    void sense(Monster m, float step) {
        boolean night = night();
        float sight = m.kind.sight * (m.state == State.DORMANT ? 0.4f : 1) * (dayBlind(m) && !night ? 0.7f : 1) * (nightSighted(m) && night ? 1.2f : 1);
        float smell = m.kind.smell * (g.timeSystem.weather.rain > 0.3f ? 0.5f : 1);
        for (Actor t : g.actors()) {
            if (t == m || !t.alive || t.dummy || t == g.player && g.notarget()) continue;
            if (t instanceof Npc n && !n.loaded || t instanceof Animal a && !a.loaded) continue;
            float d = m.distTo(t);
            if (d > Math.max(sight, smell) + 4) continue;
            String sense = null;
            // sight: through its eyes' cone and in the light (the stealth system's own test, so sneaking and shadow count)
            if (d < sight && (g.ai.canSee(m, t) || d < 2.2f)) sense = "seen";
            // smell: predators track blood and the wounded; the wind does not matter in a voxel night this small
            else if (smell > 0 && d < smell * (t.bloodied > 0.2f || t.hp < t.maxHp() * 0.5f ? 1.5f : 1)) sense = "smelt";
            if (sense == null) continue;
            Known k = m.known.computeIfAbsent(t.id, Known::new);
            k.x = t.x; k.z = t.z; k.at = g.time; k.sense = sense;
        }
        // what was heard (the stealth system keeps the heard position of things it has not placed)
        for (var e : m.aware.entrySet()) {
            var w = e.getValue();
            if (g.time - w.heardAt > 0.6 || w.heardAt <= 0) continue;
            Actor t = g.actor(e.getKey());
            if (t == null || !t.alive) continue;
            Known k = m.known.computeIfAbsent(t.id, Known::new);
            if (k.at < w.heardAt) { k.x = w.x; k.z = w.z; k.at = w.heardAt; k.sense = "heard"; }
        }
        // forgetting: the zombie's minute, the dragon's hour
        m.known.values().removeIf(k -> g.time - k.at > m.kind.memory || g.actor(k.id) == null || !g.actor(k.id).alive);
        // the threat each poses now
        for (Known k : m.known.values()) k.threat = threat(m, k);
    }

    /** Threat = proximity + noise + damage dealt + visible weapon / armour + territory intrusion, by what it is to this monster. */
    float threat(Monster m, Known k) {
        Actor t = g.actor(k.id);
        if (t == null) return 0;
        float regard = regard(m, t);
        if (regard == 0) return 0;
        float d = (float) Math.hypot(k.x - m.x, k.z - m.z);
        float fresh = (float) Math.max(0, 1 - (g.time - k.at) / Math.max(5, m.kind.memory));
        float proximity = Math.max(0, 1 - d / Math.max(4, m.kind.sight));
        float noise = t instanceof rpg.actor.Player p && (p.sprinting || p.running) || k.sense.equals("heard") ? 0.25f : 0;
        float damage = Math.min(1.5f, k.damage / Math.max(1, m.maxHp()) * 3);
        float armed = t.main() != null && t.main().damage > 0 ? 0.15f + Math.min(0.35f, t.eq.armor() * 0.02f) : 0;
        float intrusion = m.lair != 0 ? Math.max(0, 1 - (float) Math.hypot(k.x - m.lairX, k.z - m.lairZ) / Math.max(6, m.kind.range)) * m.kind.territory : 0;
        float score = (proximity + noise + damage + armed + intrusion) * fresh * Math.abs(regard);
        // how strong it looks against this monster and its fellows near it: a cautious kind keeps clear of something much
        // stronger (it becomes a thing to fear); brutes, the bound dead and the apex kinds do not weigh it so
        float caution = switch (m.kind.archetype) { case SOLITARY_BRUTE, BOUND_DEAD, APEX_SOVEREIGN -> 0; case APEX_HUNTER -> 0.4f; default -> 1; };
        float odds = might(t) / Math.max(1, might(m) * (m.kind.archetype == Archetype.PACK_RAIDER ? sys.nearKin(m, 12) : 1));
        if (regard < 0 || caution > 0 && odds > 1.8f && damage < 0.5f) return -score * Math.max(1, odds * 0.5f) * Math.max(caution, regard < 0 ? 1 : 0);
        return score;
    }

    /** How dangerous something looks: its combat level and its hit points (a bat and a bear are not alike). */
    static float might(Actor a) { return a.skills.combatLevel() + a.maxHp() * 0.5f; }

    // ------------------------------------------------------------------ drives
    void drives(Monster m, float dt) {
        MonsterKind k = m.kind;
        boolean active = m.state != State.DORMANT && m.state != State.REST && m.state != State.IDLE;
        // hunger rises over time (the dead and the haunting never hunger); fatigue with activity, rest recovers it
        m.hunger = Math.min(1, m.hunger + k.hungerRate * dt / 900f * (1 + m.nudge[0]));
        if (active) m.fatigue = Math.min(1, m.fatigue + k.fatigueRate * dt / 400f * (1 + m.nudge[4]));
        else m.fatigue = Math.max(0, m.fatigue - dt / 120f);
        // fear decays slowly; injury and a fallen packmate spike it (onHurt, Monsters.onKilled)
        m.fear = Math.max(0, m.fear - dt / 40f);
        // aggression: the species baseline, raised by hunger, night, a breach of its core, a provocation, the full moon
        float moon = k == MonsterKind.WEREWOLF ? (float) moon() : 0;
        float breach = 0;
        for (Known kn : m.known.values()) if (kn.threat > 0 && Math.hypot(kn.x - m.lairX, kn.z - m.lairZ) < k.core && m.lair != 0) breach = 0.25f;
        float provoked = g.time - m.cs.lastHurt < 15 ? 0.35f : 0;
        boolean pack = k.archetype == Archetype.PACK_RAIDER;
        float numbers = pack ? Math.min(0.3f, (sys.nearKin(m, 12) - 1) * 0.08f) : 0;
        m.aggression = Math.min(1.5f, k.aggression * (1 + m.nudge[2]) + m.hunger * 0.3f + (night() && k.active != MonsterKind.Active.DAY ? 0.12f : 0) + breach + provoked + moon * 0.3f + numbers);
        // territory: strongest at the den, nothing at the edge of its range
        m.territorial = m.lair == 0 ? 0 : Math.max(0, 1 - (float) Math.hypot(m.x - m.lairX, m.z - m.lairZ) / Math.max(6, k.range)) * k.territory;
        m.curiosityNow = k.curiosity * (1 + m.nudge[6]);
        // a pack monster alone is a coward; in numbers its fear falls away (morale is the group's)
        if (pack && sys.nearKin(m, 12) <= 1) m.fear = Math.max(m.fear, 0.25f);
        Monsters.Lair lair = sys.lair(m.lair);
        if (lair != null && lair.morale < 0.4f) m.fear = Math.max(m.fear, 0.6f);
        // a haunter's disturbance: it grows while the living stay in its range
        if (k.archetype == Archetype.HAUNTER) {
            boolean intruded = false;
            for (Known kn : m.known.values()) if (kn.threat > 0 && Math.hypot(kn.x - m.lairX, kn.z - m.lairZ) < k.range) intruded = true;
            m.disturbed = intruded ? Math.min(60, m.disturbed + dt) : Math.max(0, m.disturbed - dt * 0.5f);
        }
    }

    // ------------------------------------------------------------------ deciding
    private record Score(State state, float value, String why) {}

    void think(Monster m) {
        m.nextThink = g.time + 0.35 + (m.id % 5) * 0.04;
        MonsterKind k = m.kind;
        List<Score> s = new ArrayList<>();
        Known top = strongest(m);
        Actor threat = top == null ? null : g.actor(top.id);
        float hpShare = m.hp / Math.max(1, m.maxHp());
        boolean abroad = k.abroad(g.state.hourOfDay()) || rpg.world.DungeonLayout.inDungeonRegion(m.x);   // no day nor night underground
        float fromLair = m.lair == 0 ? 0 : (float) Math.hypot(m.x - m.lairX, m.z - m.lairZ);
        // fear against aggression: the core of hostility as a state
        // in its resting hours a monster at its lair stays put unless something comes right up to it or hurts it
        boolean resting = !abroad && fromLair < 6 && g.time - m.cs.lastHurt > 10;
        if (resting && top != null && Math.hypot(top.x - m.x, top.z - m.z) > (m.kind.flies() ? 3.5f : 5)) top = null;
        // a thing to fear that cannot get at it (a ground-bound beast below a flyer up high) or is well away is no reason to keep running
        if (top != null && top.threat < 0) {
            Actor feared = g.actor(top.id);
            boolean grounded = !(feared instanceof Monster fm && fm.kind.flies());
            float away = (float) Math.hypot(top.x - m.x, top.z - m.z);
            if (m.kind.flies() && grounded && m.altitude > 3.2f || away > Math.max(8, m.kind.sight * 0.6f) && g.time - m.cs.lastHurt > 8) top = null;
        }
        if (top != null && top.threat < 0) {
            s.add(new Score(State.FLEE, 0.9f + (-top.threat) * 0.2f, "something stronger: " + g.nameOf(top.id)));
        } else if (top != null && top.threat > 0.05f) {
            float fearNow = m.fear + (hpShare < k.woundedAt ? (k.berserk ? -0.4f : 0.8f) : 0);
            float engage = m.aggression * (0.4f + top.threat) + (k.berserk && hpShare < 0.3f ? 0.5f : 0);
            // a haunter only turns hostile as its disturbance grows; the bound dead only when the living trespass
            if (k.archetype == Archetype.HAUNTER) engage *= Math.min(1.2f, m.disturbed / 12f);
            if (k.archetype == Archetype.BOUND_DEAD && k == MonsterKind.SKELETON && m.lair != 0 && Math.hypot(top.x - m.lairX, top.z - m.lairZ) > k.range) engage *= 0.2f;
            if (fearNow > engage + 0.05f && k.woundedAt > 0) s.add(new Score(State.FLEE, 0.7f + fearNow * 0.3f, String.format("fear %.2f over aggression %.2f", fearNow, engage)));
            else if (k.archetype == Archetype.APEX_HUNTER && threat != null && m.distTo(threat) > 6 && !isolated(threat) && g.time - m.cs.lastHurt > 10)
                s.add(new Score(State.STALK, 0.6f + engage * 0.3f, "stalking " + g.nameOf(top.id) + ", waiting for them to be alone"));
            else if (k.archetype == Archetype.AMBUSH_PREDATOR && threat != null && m.distTo(threat) > 5 && g.time - m.cs.lastHurt > 6 && m.lair != 0)
                s.add(new Score(State.STALK, 0.55f, "waiting in the web for " + g.nameOf(top.id)));
            else if (engage > 0.35f) s.add(new Score(State.ENGAGE, 0.5f + engage * 0.4f, String.format("%s (threat %.2f, aggression %.2f)", g.nameOf(top.id), top.threat, m.aggression)));
            else if (m.curiosityNow > 0.3f || top.sense.equals("heard")) s.add(new Score(State.INVESTIGATE, 0.35f + m.curiosityNow * 0.3f, "something " + top.sense + " near (" + (int) top.x + ", " + (int) top.z + ")"));
            else s.add(new Score(State.ALERT, 0.4f, "watching " + g.nameOf(top.id)));
        }
        // pack morale broken: regroup on the others, or scatter
        Monsters.Lair lair = sys.lair(m.lair);
        if (k.archetype == Archetype.PACK_RAIDER && lair != null && lair.morale < 0.55f && lair.morale >= 0.4f && m.state == State.ENGAGE)
            s.add(new Score(State.REGROUP, 0.8f, String.format("the band is shaken (morale %.2f)", lair.morale)));
        // the leash: a pull home that strengthens with distance, stretched by rage
        float stretch = 1 + (g.time - m.cs.lastHurt < 20 ? 0.6f : 0) + m.hunger * 0.3f;
        if (m.lair != 0 && fromLair > k.range * stretch) s.add(new Score(State.RETURN, 0.75f + Math.min(0.4f, (fromLair / (k.range * stretch) - 1)), "too far from its lair"));
        // needs
        if (m.fatigue > 0.75f) s.add(new Score(State.REST, 0.45f + m.fatigue * 0.3f, String.format("tired (%.0f%%)", m.fatigue * 100)));
        if (hpShare < 0.6f && k.regen > 0 && (threat == null || top.threat < 0.2f)) s.add(new Score(State.REST, 0.6f, "back to the lair to mend"));
        long carcass = m.hunger > 0.45f ? sys.carcassNear(m, Math.min(k.range, 30)) : 0;
        if (carcass != 0) { m.feedOn = carcass; s.add(new Score(State.FEED, 0.35f + m.hunger * 0.5f, "hungry, and there is a carcass")); }
        Actor prey = m.hunger > 0.55f && predator(m) && abroad ? sys.preyNear(m) : null;
        if (prey != null) { m.target = prey.id; s.add(new Score(State.ENGAGE, 0.3f + m.hunger * 0.45f, "hunting " + g.nameOf(prey.id) + " (hungry)")); }
        // the day's round
        if (!abroad && fromLair < 4) s.add(new Score(State.DORMANT, 0.3f, "its resting hours"));
        else if (!abroad) s.add(new Score(State.RETURN, 0.32f, "back to the lair before its resting hours"));
        else s.add(new Score(State.PATROL, 0.22f + m.territorial * 0.05f, "patrolling its range"));
        s.add(new Score(State.IDLE, 0.15f, "at the lair, about its business"));
        s.sort(Comparator.comparing(Score::value).reversed());
        Score best = s.get(0);
        // a little inertia: keep the state unless another clearly beats it (no ping-ponging)
        if (best.state != State.FLEE) for (Score c : s) if (c.state == m.state && c.value >= best.value - 0.05f) { best = c; break; }
        if (best.state == State.ENGAGE && top != null && top.threat > 0 && (prey == null || best.why.startsWith(g.nameOf(top.id)))) chooseTarget(m, top);
        if (best.state != m.state) {
            g.state.commit("MonsterState", m.id, m.target, DevMap.jurisdiction(m.x, m.z), 0, m.state + " -> " + best.state + " (" + best.why + ")");
            m.state = best.state; m.stateSince = g.time; m.hasGoalPos = false; m.legs = null;
            enter(m);
        }
        m.why = best.why;
    }

    /** The target it fights: switched only for a good reason, with a cooldown (wounded, struck from behind, more threatening). */
    private void chooseTarget(Monster m, Known top) {
        Known cur = m.known.get(m.target);
        if (m.target == top.id) return;
        if (cur != null && g.time < m.nextSwitch && cur.threat > top.threat * 0.7f) return;
        m.target = top.id; m.targetSince = g.time; m.nextSwitch = g.time + 4;
    }

    private Known strongest(Monster m) {
        Known best = null;
        for (Known k : m.known.values()) {
            if (best == null || Math.abs(k.threat) > Math.abs(best.threat) && (k.threat < 0 || best.threat >= 0) || k.threat < -0.3f && best.threat >= 0) best = k;
        }
        return best;
    }

    /** A target with nobody of its own near it (a werewolf waits for that). */
    private boolean isolated(Actor t) {
        for (Actor o : g.actors()) if (o != t && o.alive && !(o instanceof Monster) && !(o instanceof Animal) && o.distTo(t) < 8) return false;
        return true;
    }

    /** What a state does as it begins: a call to the pack, a roar, a hush. */
    private void enter(Monster m) {
        switch (m.state) {
            case ENGAGE -> {
                // group aggro: a call alerts the others in call range (kill the caller and the call is never made)
                if (m.kind.archetype == Archetype.PACK_RAIDER || m.kind.social > 0.5f) {
                    sys.sound(m, m.kind.alarmSound, 0.9f);
                    for (Monster o : sys.kin(m, 22)) if (o.state != State.ENGAGE && o.target == 0) { o.target = m.target; o.nextThink = g.time + 0.2 + (o.id % 4) * 0.1; Known k = o.known.computeIfAbsent(m.target, Known::new); Actor t = g.actor(m.target); if (t != null) { k.x = t.x; k.z = t.z; k.at = g.time; k.sense = "called"; } }
                } else sys.sound(m, m.kind.alarmSound, 0.8f);
            }
            case ALERT -> sys.sound(m, m.kind.idleSound, 0.5f);
            case FLEE -> { m.target = 0; m.cs.cancel(); }
            default -> { }
        }
    }

    // ------------------------------------------------------------------ doing
    void act(Monster m, float dt) {
        switch (m.state) {
            case DORMANT, REST -> { home(m, 0.6f); m.wantAltitude = roost(m); if (m.state == State.REST && m.kind.regen > 0) m.hp = Math.min(m.maxHp(), m.hp + m.kind.regen * 3 * dt); }
            case IDLE -> { if (home(m, 3)) wanderNear(m, m.lairX, m.lairZ, 3); m.wantAltitude = m.kind.flies() ? 1.2f : 0; }
            case PATROL -> patrol(m);
            case ALERT -> { Known k = m.known.get(strongestId(m)); hold(m); if (k != null) m.wantYaw = yawTo(m, k.x, k.z); }
            case INVESTIGATE -> { Known k = m.known.get(strongestId(m)); if (k == null || sys.moveTo(m, k.x, k.z, false, 1.5f)) { m.state = State.ALERT; m.nextThink = g.time + 1.5; } }
            case STALK -> stalk(m);
            case ENGAGE -> engage(m, dt);
            case FLEE -> flee(m);
            case REGROUP -> { float[] c = sys.kinCentre(m); if (sys.moveTo(m, c[0], c[1], true, 2)) hold(m); }
            case FEED -> feed(m, dt);
            case RETURN -> { if (sys.moveTo(m, m.lairX, m.lairZ, false, 2)) { m.state = State.IDLE; m.nextThink = 0; } }
        }
    }

    private long strongestId(Monster m) { Known k = strongest(m); return k == null ? 0 : k.id; }

    private boolean home(Monster m, float within) { return sys.moveTo(m, m.lairX, m.lairZ, false, within); }

    /** Where a flyer rests: hanging under the den's roof, perched on a roof, settled on its hoard. */
    private float roost(Monster m) { return m.kind == MonsterKind.BAT ? sys.hangHeight(m) : m.kind == MonsterKind.IMP ? sys.perchHeight(m) : 0; }

    private void hold(Monster m) { sys.hold(m); }

    private void wanderNear(Monster m, float cx, float cz, float r) {
        if (!m.hasGoalPos || Math.hypot(m.x - m.tx, m.z - m.tz) < 1 || g.time - m.stateSince > 20) {
            double a = sys.rnd(m) * Math.PI * 2; float d = r * (0.4f + 0.6f * (float) sys.rnd(m));
            m.tx = cx + (float) Math.sin(a) * d; m.tz = cz + (float) Math.cos(a) * d; m.hasGoalPos = true; m.stateSince = g.time;
        }
        if (sys.moveTo(m, m.tx, m.tz, false, 0.8f)) hold(m);
    }

    /** The patrol: through its range along points it returns to, the pack in its pairs and threes, looking for food and checking the borders. */
    private void patrol(Monster m) {
        m.wantAltitude = m.kind.flies() ? (m.kind == MonsterKind.DRAGON ? 14 : 3.5f) : 0;
        if (!m.hasGoalPos || Math.hypot(m.x - m.tx, m.z - m.tz) < 1.5f) {
            // the pack's leader picks the way; the others keep near it
            Monster lead = sys.leaderOf(m);
            if (lead != null && lead != m && lead.alive) {
                double a = (m.id % 6) * 1.05; m.tx = lead.x + (float) Math.sin(a) * 2.2f; m.tz = lead.z + (float) Math.cos(a) * 2.2f;
            } else {
                double a = sys.rnd(m) * Math.PI * 2; float r = m.kind.range * (0.35f + 0.55f * (float) sys.rnd(m));
                float[] p = sys.open(m, m.lairX + (float) Math.sin(a) * r, m.lairZ + (float) Math.cos(a) * r);
                m.tx = p[0]; m.tz = p[1];
            }
            m.hasGoalPos = true;
        }
        if (sys.moveTo(m, m.tx, m.tz, false, 1.2f)) m.hasGoalPos = false;
    }

    /** Stalking: shadowing the target at a distance, out of its sight where it can be, until it is alone or weak - then the pounce. */
    private void stalk(Monster m) {
        Actor t = g.actor(m.target != 0 ? m.target : strongestId(m));
        if (t == null || !t.alive) { m.nextThink = 0; return; }
        m.target = t.id;
        float d = m.distTo(t);
        boolean weak = t.hp < t.maxHp() * 0.5f || t instanceof Npc n && n.asleep();
        if (weak || isolated(t) && d < 9 || g.time - m.stateSince > 45) { m.state = State.ENGAGE; m.stateSince = g.time; return; }
        // keep 9 m off, on the far side of cover from its eyes if there is any
        float ax = m.x - t.x, az = m.z - t.z, l = Math.max(0.1f, (float) Math.hypot(ax, az));
        float keep = m.kind == MonsterKind.GIANT_SPIDER ? 0 : 9;
        if (keep == 0) { hold(m); m.wantYaw = yawTo(m, t.x, t.z); return; }
        float[] p = sys.open(m, t.x + ax / l * keep, t.z + az / l * keep);
        m.sneaking = true;
        if (sys.moveTo(m, p[0], p[1], false, 1.5f)) { hold(m); m.wantYaw = yawTo(m, t.x, t.z); }
    }

    private void flee(Monster m) {
        Known k = m.known.get(strongestId(m));
        m.sneaking = false;
        m.wantAltitude = m.kind.flies() ? 6 : 0;
        // to the lair (to mend) if that is away from the threat; else straight away from it, toward the pack or cover
        float fx, fz;
        if (k == null) { fx = m.lairX; fz = m.lairZ; }
        else {
            float ax = m.x - k.x, az = m.z - k.z, l = Math.max(0.1f, (float) Math.hypot(ax, az));
            boolean lairAway = m.lair != 0 && ((m.lairX - m.x) * ax + (m.lairZ - m.z) * az) > 0;
            if (lairAway) { fx = m.lairX; fz = m.lairZ; }
            else { float[] p = sys.open(m, m.x + ax / l * 12, m.z + az / l * 12); fx = p[0]; fz = p[1]; }
            if (l > m.kind.sight * 1.2f && g.time - m.stateSince > 6) { m.nextThink = 0; }
        }
        if (sys.moveTo(m, fx, fz, true, 1.5f)) { hold(m); m.nextThink = 0; }
    }

    /** Feeding on a carcass: vulnerable and low in awareness while it eats, and it eats what is really there. */
    private void feed(Monster m, float dt) {
        Animal c = g.animals.byId(m.feedOn);
        if (c == null || !c.carcass() || c.butchered) { m.feedOn = 0; m.nextThink = 0; return; }
        if (!sys.moveTo(m, c.x, c.z, false, m.radius + 0.6f)) return;
        hold(m); m.wantYaw = yawTo(m, c.x, c.z);
        m.hunger = Math.max(0, m.hunger - dt / 40f);
        if (g.time - m.stateSince > 2) { c.eaten++; m.stateSince = g.time; if (c.eaten > 6 + c.species.meat * 2) { c.butchered = true; g.state.commit("CarcassEaten", m.id, c.id, DevMap.jurisdiction(c.x, c.z), 0, m.kind.title + " ate the " + c.kind()); } }
        if (m.hunger < 0.05f) { m.nextThink = 0; Monsters.Lair l = sys.lair(m.lair); if (l != null) l.food = Math.min(10, l.food + 1); }
    }

    // ------------------------------------------------------------------ the fight (species by species)
    private void engage(Monster m, float dt) {
        Actor t = g.actor(m.target);
        if (t == null || !t.alive) { m.target = 0; m.nextThink = 0; m.state = State.IDLE; return; }
        Known k = m.known.get(t.id);
        boolean sees = k != null && g.time - k.at < 0.6;
        // out of its senses: to where it last placed it - never to where it really is
        if (!sees) {
            if (k == null) { m.target = 0; m.nextThink = 0; return; }
            if (sys.moveTo(m, k.x, k.z, true, 1.2f)) { m.state = State.INVESTIGATE; m.stateSince = g.time; }
            return;
        }
        m.fightTarget = t.id;           // the combat system reads this as the fight being on (hostile(a, v))
        float d = m.distTo(t) - t.radius - m.radius;
        m.wantYaw = yawTo(m, t.x, t.z);
        m.pitch = (float) Math.atan2(t.chestY() - m.chestY(), Math.max(0.3f, m.distTo(t)));
        m.wantAltitude = m.kind.flies() ? Math.max(0, t.y - m.y + (m.kind == MonsterKind.DRAGON ? 6 : 1.4f)) : 0;
        boolean busy = m.cs.busy() || g.time < m.windupUntil;
        // a telegraphed strike under way (the creatures' wind-ups): it lands when the wind-up ends
        if (m.windup != null && g.time >= m.windupUntil) { sys.strike(m, t, m.windup); m.windup = null; return; }
        if (busy) { hold(m); return; }
        float reach = m.kind.reach * m.kind.scale() + (m.main() != null && !m.main().natural ? m.main().reach : 0);
        boolean ranged = m.kind.ranged != null && g.time >= m.nextRanged && d > Math.max(3, reach + 1) && d < m.kind.rangedRange && g.vox.sight(m.x, m.eyeY(), m.z, t.x, t.chestY(), t.z);
        // the harassers keep moving: hit and run; the ranged keep their distance; the brutes close in
        switch (m.kind.archetype) {
            case HARASSER -> {
                if (ranged && sys.rnd(m) < 0.6) { sys.ranged(m, t); return; }
                if (d < reach + 0.3f && g.time >= m.nextStrike) { attack(m, t); m.tx = m.x + (m.x - t.x) * 1.5f; m.tz = m.z + (m.z - t.z) * 1.5f; return; }
                if (g.time - m.nextStrike < 1.2 && d < 4) { sys.moveTo(m, m.tx, m.tz, true, 0.8f); return; }   // flit out, then back in
                sys.moveTo(m, t.x, t.z, true, reach);
            }
            default -> {
                if (ranged && (d > reach + 4 || m.kind == MonsterKind.DRAGON && sys.rnd(m) < 0.45) && sys.rnd(m) < 0.5) { sys.ranged(m, t); return; }
                if (d <= reach + 0.15f) { hold(m); if (g.time >= m.nextStrike && Math.abs(rpg.actor.Actor.wrap(m.wantYaw - m.yaw)) < 0.5f) attack(m, t); }
                else sys.moveTo(m, t.x, t.z, true, reach * 0.9f);
                // a target it cannot get to (up on something, behind something) - it adapts: throws, backs off, calls help
                if (d > reach + 0.5f && Math.hypot(m.vx, m.vz) < 0.2f) m.outOfReach += dt; else m.outOfReach = Math.max(0, m.outOfReach - dt);
                if (m.outOfReach > 4) { m.outOfReach = 0; if (m.kind.ranged != null) m.nextRanged = 0; else { m.state = State.ALERT; m.nextThink = g.time + 3; } }
            }
        }
    }

    /** A blow at the target, as its kind deals them: the humanoids through the melee system (with their weapons or their own claws), the creatures by a telegraphed strike. */
    private void attack(Monster m, Actor t) {
        m.nextStrike = g.time + switch (m.kind) { case TROLL, DRAGON -> 2.4; case ZOMBIE, SKELETON -> 1.6; case WEREWOLF, IMP, BAT -> 0.7; default -> 1.1; };
        if (m.kind.form == MonsterKind.Form.HUMANOID) {
            Move move = switch (m.kind) {
                case TROLL -> Move.HEAVY;
                case ORC -> sys.rnd(m) < 0.45 ? Move.HEAVY : null;
                case GOBLIN -> m.main() != null && (m.main().family == rpg.item.ItemDef.Family.SPEAR || m.main().family == rpg.item.ItemDef.Family.DAGGER) ? Move.THRUST : null;
                case SKELETON -> sys.rnd(m) < 0.3 ? Move.HEAVY : null;
                default -> null;
            };
            if (move == null) move = rpg.combat.CombatSystem.lightMove(m, false);
            g.combat.attack(m, false, move);
        } else {
            // the creatures: a visible, audible wind-up first (heavier = longer and clearer), then the strike
            String what = switch (m.kind) {
                case DRAGON -> sys.rnd(m) < 0.3 ? "tail" : sys.rnd(m) < 0.5 ? "claw" : "bite";
                case BAT -> "bite";
                case GIANT_SPIDER -> "bite";
                default -> "bite";
            };
            m.windup = what;
            m.windupUntil = g.time + switch (m.kind) { case DRAGON -> 0.9; case GIANT_SPIDER -> 0.45; default -> 0.25; };
            sys.sound(m, m.kind.alarmSound, 0.6f);
        }
    }

    // ------------------------------------------------------------------ helpers
    static float yawTo(Actor a, float x, float z) { return (float) Math.atan2(-(x - a.x), -(z - a.z)); }
    private boolean night() { double h = g.state.hourOfDay(); return h >= 20 || h < 5.5; }
    private static boolean dayBlind(Monster m) { return m.kind == MonsterKind.GOBLIN || m.kind == MonsterKind.BAT || m.kind == MonsterKind.TROLL; }
    private static boolean nightSighted(Monster m) { return m.kind == MonsterKind.WEREWOLF || m.kind == MonsterKind.IMP || m.kind == MonsterKind.GHOST; }
    /** The moon's fullness, 0 (new) .. 1 (full), over a 28-day cycle of game days. */
    double moon() { double day = g.state.minutes / 1440.0; return 0.5 - 0.5 * Math.cos(day / 28.0 * Math.PI * 2); }

    /** Struck: the damage is remembered against whoever dealt it, fear spikes with the wound, and a pack hears of it. */
    void onHurt(Monster m, Actor by, float damage) {
        if (by == null) return;
        Known k = m.known.computeIfAbsent(by.id, Known::new);
        k.damage += damage; k.x = by.x; k.z = by.z; k.at = g.time; k.sense = "struck";
        k.threat = threat(m, k);
        m.fear = Math.min(1.5f, m.fear + damage / Math.max(1, m.maxHp()) * 1.2f * (m.kind.woundedAt > 0 ? 1 : 0));
        m.nextThink = Math.min(m.nextThink, g.time + 0.1);
        if (m.state != State.ENGAGE && m.state != State.FLEE) { m.target = by.id; }
    }
}
