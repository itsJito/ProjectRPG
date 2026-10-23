package rpg.animal;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import rpg.Rpg;
import rpg.actor.Actor;
import rpg.actor.Player;
import rpg.ai.Npc;
import rpg.animal.Animal.Goal;
import rpg.item.Items;
import rpg.world.DevMap;

/**
 * The one animal mind (GDD 3.9: 14 species x 2 stages share one architecture). It senses (sight through the same
 * stealth exposure people use, hearing, and at arm's length, smell and touch), weighs its options in the GDD's priority
 * bands - danger and its young first, then urgent needs, then following, then feeding, resting and roaming, then
 * courting - with a little inertia so it does not flip goals every look, and carries the choice out through its engine
 * agent. Species and stage only change the numbers and which options are open.
 */
final class AnimalMind {
    static final int A_IDLE = 0, A_WANDER = 1, A_GOTO = 2, A_RUN = 3, A_APPROACH = 4, A_FLEE = 5, A_HOLD = 6, ACTIONS = 7;

    private final Rpg g;
    private final Animals sys;

    AnimalMind(Rpg g, Animals sys) { this.g = g; this.sys = sys; }

    record Score(Goal goal, float value, long other, String reason) {}

    // ------------------------------------------------------------------ the senses
    /** How much an actor worries this animal (0: not at all): its enemies, strangers, whoever hurt it. */
    float threatWeight(Animal a, Actor t) {
        if (t == a || !t.alive || t.dummy) return 0;
        if (a.cs.lastAttacker == t.id && g.time - a.cs.lastHurt < 20) return 2f;
        if (t instanceof Animal o) {
            if (o.baby) return 0;
            if (o.species.prey.contains(a.species)) return a.owned() && o.owned() ? 0 : 1.2f;
            if (o.species == Species.BROWN_BEAR && a.species != Species.BROWN_BEAR) return 0.7f;
            if (o.species == Species.DOG && a.species == Species.CAT && !(a.owned() && o.owned())) return 0.6f;
            return 0;
        }
        // people
        if (a.owner == t.id || a.trustOf(t.id) > 0.5f) return 0;
        if (a.owned() && t instanceof Npc n && n.homeJurisdiction.equals(DevMap.jurisdiction(a.homeX, a.homeZ))) return 0;   // the household and the neighbours
        if (a.species.domestic) return t instanceof Player p && p.sprinting ? 0.6f : 0.25f;
        return a.species.predator() || a.species == Species.BROWN_BEAR ? 0.5f : 1f;
    }

    /** Looks and listens (five times a second): the worst threat in reach, and the fear it builds or sheds. */
    void sense(Animal a, float step) {
        Actor worst = null;
        float worstScore = 0;
        float reach = Math.max(18, a.species.wary * 2.2f);
        for (Actor t : g.actors()) {
            if (t == g.player && g.notarget()) continue;
            float w = threatWeight(a, t);
            if (w <= 0) continue;
            float d = a.distTo(t);
            if (d > reach) continue;
            float e = g.ai.stealth.exposure(a, sys.acuity(a), t);
            if (d < 1.8f) e = Math.max(e, 0.6f);                                   // brushed against: felt and smelt
            if (a.cs.lastAttacker == t.id && g.time - a.cs.lastHurt < 6) e = Math.max(e, 1);
            if (e <= 0) continue;
            float near = a.species.wary > 0 ? Math.max(0, 1 - d / (a.species.wary * 2.2f)) : Math.max(0, 1 - d / 6);
            float s = e * w * (0.25f + near);
            if (s > worstScore) { worstScore = s; worst = t; }
        }
        if (worst != null) {
            a.fear = Math.min(1, a.fear + worstScore * 2.2f * step);
            if (a.threat != worst.id) { a.threat = worst.id; a.threatSince = g.time; }
            a.threatX = worst.x; a.threatZ = worst.z; a.threatSeen = g.time;
        } else {
            // recover and resume: calm returns over a few seconds once nothing threatening is seen
            if (g.time - a.threatSeen > 2) a.fear = Math.max(0, a.fear - 0.12f * step);
            if (a.fear <= 0.05f) a.threat = 0;
        }
        // an alarm: the first to take fright warns its group (a bleat, a squawk, a stamp)
        if (a.fear > 0.6f && !a.alarmed) {
            a.alarmed = true;
            sys.sound(a, a.species.alarmSound, 0.8f);
            for (Animal o : sys.live()) {
                if (o == a || o.species != a.species || o.group != a.group || o.distTo(a) > 18) continue;
                o.fear = Math.max(o.fear, 0.45f);
                if (o.threat == 0) { o.threat = a.threat; o.threatX = a.threatX; o.threatZ = a.threatZ; o.threatSeen = g.time; }
                o.nextThink = Math.min(o.nextThink, g.time + 0.15 + (o.id % 5) * 0.06);
            }
        }
        if (a.fear < 0.3f) a.alarmed = false;
    }

    /** A noise reached it: fear in proportion to how loud and how skittish it is (a sound never says who, only where). */
    void heard(Animal a, Actor source, float x, float z, float loud) {
        float w = threatWeight(a, source);
        if (w <= 0) return;
        a.fear = Math.min(1, a.fear + loud * 0.35f * w * a.species.hearing);
        if (a.threat == 0 || a.threat == source.id) { a.threat = source.id; a.threatX = x; a.threatZ = z; a.threatSeen = g.time - 1; }
        a.nextThink = Math.min(a.nextThink, g.time + 0.2);
    }

    // ------------------------------------------------------------------ deciding
    void think(Animal a) {
        a.nextThink = g.time + 0.6 + (a.id % 7) * 0.05;
        List<Score> s = new ArrayList<>();
        Species sp = a.species;
        Actor threat = a.threat != 0 ? g.actor(a.threat) : null;
        if (threat != null && !threat.alive) threat = null;
        double hour = g.state.hourOfDay();
        boolean night = hour >= 21 || hour < 6;
        Animal mother = a.mother != 0 && sys.byId(a.mother) instanceof Animal m && m.alive ? m : null;

        // (1) danger and its young
        if (threat != null && a.fear >= 0.3f) {
            float d = a.distTo(threat);
            boolean hurt = a.cs.lastAttacker == threat.id && g.time - a.cs.lastHurt < 15;
            boolean young = sys.youngNear(a, 9) && sp.defends || sp == Species.BROWN_BEAR && sys.youngNear(a, 12);
            boolean stands = !a.baby && (sp.flight <= 0 || young || hurt && sys.couldWin(a, threat));
            if (a.baby && mother != null) s.add(new Score(Goal.FLEE, 0.97f, mother.id, "frightened by " + threat.name + "; running to its mother"));
            else if (stands) {
                if (hurt || d < Math.max(1.6f, sp.wary * 0.3f) || young && d < sp.wary * 0.6f) s.add(new Score(Goal.DEFEND, 0.96f, threat.id, (young ? "protecting its young from " : hurt ? "fighting back against " : "cornered by ") + threat.name));
                else if (d < sp.wary) s.add(new Score(Goal.WARN, 0.9f, threat.id, "warning " + threat.name + " off"));
                else s.add(new Score(Goal.ALERT, 0.8f, threat.id, "watching " + threat.name));
            } else if (d < Math.max(sp.flight, 1.2f) * 2.5f || a.fear > 0.7f || hurt) s.add(new Score(Goal.FLEE, 0.95f, threat.id, "fleeing " + threat.name));
            else s.add(new Score(Goal.ALERT, 0.8f, threat.id, "uneasy: " + threat.name + String.format(" %.0f m off", d)));
        }
        // (2) urgent needs
        if (a.thirst > 0.7f) s.add(new Score(Goal.DRINK, 0.55f + a.thirst * 0.3f, 0, String.format("thirsty (%.0f%%)", a.thirst * 100)));
        if (a.hunger > 0.6f && !a.baby) {
            boolean meat = sp.diet == Species.Diet.HUNTER || sp.diet == Species.Diet.OMNIVORE || sp.diet == Species.Diet.SCAVENGER;
            Animal carcass = meat ? sys.carcassNear(a, 30) : null;
            Animal prey = sp.prey.isEmpty() ? null : sys.preyFor(a);
            if (carcass != null) s.add(new Score(Goal.FEED, 0.5f + a.hunger * 0.35f, carcass.id, "hungry; feeding on a " + carcass.kind().toLowerCase() + " carcass"));
            else if (prey != null) s.add(new Score(Goal.HUNT, 0.5f + a.hunger * 0.35f, prey.id, "hungry; hunting a " + prey.kind().toLowerCase() + String.format(" %.0f m off", a.distTo(prey))));
            else if (sp.diet != Species.Diet.HUNTER) s.add(new Score(Goal.GRAZE, 0.45f + a.hunger * 0.3f, 0, String.format("hungry (%.0f%%); %s", a.hunger * 100, sys.troughFor(a) != null ? "to the trough" : "foraging")));
        }
        // (3) young: follow the mother; a mother keeps by her young
        if (mother != null && a.distTo(mother) > 2.6f) s.add(new Score(Goal.FOLLOW, 0.72f, mother.id, "keeping close to its mother"));
        Animal stray = sys.strayYoung(a, 6);
        if (stray != null) s.add(new Score(Goal.FOLLOW, 0.66f, stray.id, "staying by its young"));
        // a hen sits her clutch
        Animals.Nest clutch = sys.clutchOf(a);
        if (clutch != null && a.hunger < 0.7f) s.add(new Score(Goal.NEST, 0.6f, 0, "sitting on its clutch"));
        // (4) feeding, resting, roaming, company
        boolean restTime = sp.nocturnal ? hour >= 9 && hour < 17 : night;
        if (a.fatigue > 0.8f) s.add(new Score(Goal.REST, 0.5f + a.fatigue * 0.2f, 0, String.format("tired (%.0f%%)", a.fatigue * 100)));
        else if (restTime) s.add(new Score(Goal.REST, 0.5f, 0, sp.nocturnal ? "lying up through the day" : "night: resting"));
        if (a.owned() && sp == Species.DOG) {
            Actor owner = g.actor(a.owner);
            if (owner != null && owner.alive && a.distTo(owner) > 5) s.add(new Score(Goal.SEEK, 0.48f, owner.id, "following " + owner.name));
        }
        Animal lead = sys.groupLead(a);
        if (lead != null && a.distTo(lead) > spacing(a)) s.add(new Score(Goal.FOLLOW, 0.42f, lead.id, "keeping with the " + groupWord(sp)));
        if (sp.diet == Species.Diet.GRAZER || sp.diet == Species.Diet.FORAGER || sp.diet == Species.Diet.SCAVENGER || sp.diet == Species.Diet.OMNIVORE)
            s.add(new Score(Goal.GRAZE, 0.3f + a.hunger * 0.3f, 0, sp.diet == Species.Diet.GRAZER ? "grazing" : "foraging"));
        if (!restTime && a.fear < 0.2f && a.hunger < 0.55f && a.thirst < 0.55f) {
            boolean grooms = sp == Species.CAT || sp.plan == Species.Plan.BIRD;
            if (grooms && g.time >= a.careAgain) s.add(new Score(Goal.GROOM, 0.44f, 0, sp.plan == Species.Plan.BIRD ? "preening feathers" : "washing its face"));
            Actor playmate = a.baby && lead != null ? lead : sp == Species.DOG && a.owned() ? g.actor(a.owner) : null;
            if (playmate != null && playmate.alive && a.distTo(playmate) < 5 && g.time >= a.careAgain) s.add(new Score(Goal.PLAY, 0.43f, playmate.id, "play beside a familiar companion"));
        }
        s.add(new Score(Goal.WANDER, 0.22f, 0, sp.diet == Species.Diet.HUNTER ? "roaming its range" : "wandering"));
        // (5) courting
        Animal mate = sys.mateFor(a);
        if (mate != null) s.add(new Score(Goal.MATE, 0.46f, mate.id, "courting a " + mate.kind().toLowerCase()));

        s.sort(Comparator.comparing(Score::value).reversed());
        Score best = s.get(0);
        // inertia: keep what it is doing unless something beats it clearly (hysteresis, GDD 3.9)
        for (Score c : s) if (c.goal == a.goal && c.other == a.other && c.value >= best.value - 0.08f) { best = c; break; }
        a.scores.clear();
        for (int i = 0; i < Math.min(5, s.size()); i++) a.scores.add(String.format("%.2f %s - %s", s.get(i).value, s.get(i).goal, s.get(i).reason));
        if (best.goal != a.goal || best.other != a.other) {
            a.goal = best.goal; a.other = best.other; a.goalSince = g.time; a.hasGoal = false;
            if (best.goal == Goal.WARN) sys.sound(a, sp.alarmSound, 1f);
        }
        a.reason = best.reason;
        Actor o = a.other != 0 ? g.actor(a.other) : null;
        a.menacing = (a.goal == Goal.DEFEND || a.goal == Goal.WARN || a.goal == Goal.HUNT) && o != null && !(o instanceof Animal);
    }

    static String groupWord(Species s) {
        return switch (s.group) { case HERD -> "herd"; case FLOCK -> "flock"; case PACK -> "pack"; default -> "family"; };
    }

    /** How far apart a group may spread before a member closes up. */
    static float spacing(Animal a) { return a.species.group == Species.Group.PACK ? 7 : a.species.group == Species.Group.FAMILY ? 6 : 5 + a.species.w * 2; }

    // ------------------------------------------------------------------ acting
    void act(Animal a, float dt, float dm) {
        Species sp = a.species;
        boolean resting = a.goal == Goal.REST && a.atRest;
        // needs, per game minute: hunger and thirst build; grazing, drinking and resting meet them
        float hungerRate = sp.diet == Species.Diet.HUNTER ? 1 / 520f : 1 / 380f;
        a.hunger = Math.min(1, a.hunger + dm * hungerRate * (resting ? 0.5f : 1));
        a.thirst = Math.min(1, a.thirst + dm / 320f * (resting ? 0.5f : 1));
        a.fatigue = resting ? Math.max(0, a.fatigue - dm / 50f) : Math.min(1, a.fatigue + dm / (a.goal == Goal.FLEE ? 40f : a.goal == Goal.HUNT ? 90f : 900f));
        Actor o = a.other != 0 ? g.actor(a.other) : null;
        a.atRest = false; a.headDown = false; a.rearing = false;
        if (a.goal != Goal.DEFEND && a.goal != Goal.HUNT && a.cs.phase == rpg.actor.Actor.Combat.Phase.IDLE) a.wantYaw = Float.NaN;
        switch (a.goal) {
            case IDLE -> sys.desire(a, A_HOLD);
            case GROOM -> {
                sys.desire(a, A_HOLD); a.headDown = true;
                if (g.time - a.goalSince > 3) { a.careAgain = g.time + 45 + a.id % 30; a.nextThink = 0; }
            }
            case PLAY -> {
                if (o == null || !o.alive || a.distTo(o) > 8) { a.nextThink = 0; break; }
                float angle = (float) (g.time * 1.4 + a.id);
                sys.goTo(a, o.x + (float) Math.cos(angle) * 1.6f, o.z + (float) Math.sin(angle) * 1.6f, true, 0.4f);
                if (g.time - a.goalSince > 4) { a.careAgain = g.time + 35 + a.id % 30; a.nextThink = 0; }
            }
            case ALERT -> { sys.desire(a, A_HOLD); a.faceThreat = true; }
            case WARN -> {
                sys.desire(a, A_HOLD); a.faceThreat = true;
                a.rearing = sp == Species.BROWN_BEAR || sp == Species.HORSE;
                if (g.time - a.lastSound > 2.2) sys.sound(a, sp.alarmSound, 1f);
            }
            case FLEE -> {
                Actor from = o == null ? g.actor(a.threat) : o;
                if (a.baby && o instanceof Animal m && m.id == a.mother) { sys.goTo(a, m.x, m.z, true, 1.2f); break; }
                if (from == null) { sys.desire(a, A_HOLD); break; }
                if (from.body != 0) { sys.target(a, from.body); sys.desire(a, A_FLEE); }
                else sys.goTo(a, a.x + (a.x - a.threatX) * 2, a.z + (a.z - a.threatZ) * 2, true, 1);
            }
            case DEFEND, HUNT -> engage(a, o, dt);
            case FEED -> {
                if (!(o instanceof Animal c) || !c.carcass() || c.butchered) { a.nextThink = 0; sys.desire(a, A_HOLD); break; }
                if (sys.goTo(a, c.x, c.z, false, a.radius + 0.6f)) {
                    a.headDown = true;
                    a.hunger = Math.max(0, a.hunger - dm / 12f);
                    sys.eat(c, dm);
                }
            }
            case GRAZE -> graze(a, dm);
            case DRINK -> {
                float[] w = sys.waterFor(a);
                if (w == null) { a.thirst = 0; break; }
                if (sys.goTo(a, w[0], w[1], false, 1.1f)) { a.headDown = true; a.wantYaw = w.length > 2 ? w[2] : Float.NaN; a.thirst = Math.max(0, a.thirst - dm / 6f); if (a.thirst <= 0.02f) a.nextThink = 0; }
            }
            case REST -> {
                float[] r = sys.restFor(a);
                if (r == null || sys.goTo(a, r[0] + a.restOx, r[1] + a.restOz, false, 1.3f)) { sys.desire(a, A_HOLD); a.atRest = true; }
            }
            case WANDER -> {
                if (!a.hasGoal || a.distTo(a.gx, a.gz) < 1.2f || g.time - a.goalSince > 25) {
                    float[] p = sys.roamPoint(a);
                    a.gx = p[0]; a.gz = p[1]; a.hasGoal = true; a.goalSince = g.time;
                }
                if (sys.goTo(a, a.gx, a.gz, false, 1.0f)) a.nextThink = Math.min(a.nextThink, g.time + 1.5);
            }
            case FOLLOW, SEEK -> {
                if (o == null || !o.alive) { a.nextThink = 0; break; }
                float off = a.goal == Goal.SEEK ? 2.2f : a.baby && o.id == a.mother ? 1.2f : spacing(a) * 0.4f;   // (young keep at their mother's side)
                double ang = (a.id * 2.399) % (2 * Math.PI);
                float tx = o.x + (float) Math.cos(ang) * off, tz = o.z + (float) Math.sin(ang) * off;
                if (sys.goTo(a, tx, tz, a.distTo(o) > 9, 1.0f)) a.nextThink = Math.min(a.nextThink, g.time + 0.8);
            }
            case MATE -> {
                if (!(o instanceof Animal m) || !m.alive) { a.nextThink = 0; break; }
                if (sys.goTo(a, m.x, m.z, false, a.radius + m.radius + 0.4f)) sys.conceive(a, m);
            }
            case NEST -> {
                Animals.Nest n = sys.clutchOf(a);
                if (n == null) { a.nextThink = 0; break; }
                if (sys.goTo(a, n.x, n.z, false, 0.5f)) { sys.desire(a, A_HOLD); a.atRest = true; }
            }
            default -> sys.desire(a, A_HOLD);
        }
        if (a.goal != Goal.ALERT && a.goal != Goal.WARN) a.faceThreat = false;
        // an idle voice now and then (a cluck, a moo, a bark at a stranger)
        if (sp.idleSound != null && g.time - a.lastSound > 9 + (a.id % 11) && a.goal != Goal.REST && g.time > a.nextVoice) {
            a.nextVoice = g.time + 8 + ((a.id * 31 + (long) g.time) % 17);
            if (a.distTo(g.player) < 26) sys.sound(a, sp.idleSound, 0.55f);
        }
    }

    /** Closes on its quarry or foe and strikes when in reach (its bite, kick or swipe, through the same combat rules people use). */
    private void engage(Animal a, Actor t, float dt) {
        if (t == null || !t.alive) { a.nextThink = 0; sys.desire(a, A_HOLD); return; }
        float d = a.distTo(t), reach = a.radius + a.species.reach + t.radius;
        if (t.body != 0) sys.target(a, t.body);
        a.wantYaw = (float) Math.atan2(-(t.x - a.x), -(t.z - a.z));
        // a hunter stalks: low and slow while its quarry is far and unaware, then a short rush (a pounce) to close
        boolean stalk = a.goal == Goal.HUNT && d > 5 && !(t instanceof Animal p && p.fear > 0.5f);
        a.sneaking = stalk;
        float pounce = Math.min(1.2f, (float) Math.hypot(a.vx, a.vz) * 0.22f);
        if (stalk) sys.goTo(a, t.x, t.z, false, reach);
        else if (d > reach * 0.9f + pounce) sys.desire(a, A_APPROACH);
        else {
            sys.desire(a, A_HOLD);
            float face = Math.abs(Actor.wrap(a.wantYaw - a.yaw));
            if (face < 0.5f && !a.cs.busy() && g.time - a.cs.lastAttack > 0.55) g.combat.attack(a, false, rpg.actor.Actor.Combat.Move.BITE);
        }
    }

    /** Eats: at the trough when it is owned and there is feed, else the grass (or the scraps) where it stands, moving on now and then. */
    private void graze(Animal a, float dm) {
        Animals.Trough tr = sys.troughFor(a);
        if (tr != null && a.hunger > 0.25f) {
            if (sys.goTo(a, tr.spotX(a), tr.spotZ(a), false, 0.8f)) {
                a.headDown = true; a.wantYaw = tr.yaw(a);
                if (g.time - a.lastBite > 6) { a.lastBite = g.time; if (sys.eatFeed(tr, a)) a.hunger = Math.max(0, a.hunger - 0.55f); }
            }
            return;
        }
        if (!a.hasGoal || g.time - a.goalSince > 9 + (a.id % 6)) {
            float[] p = sys.grazePoint(a);
            a.gx = p[0]; a.gz = p[1]; a.hasGoal = true; a.goalSince = g.time;
        }
        if (sys.goTo(a, a.gx, a.gz, false, 0.9f)) {
            a.headDown = true;
            a.hunger = Math.max(0, a.hunger - dm / (a.species.diet == Species.Diet.GRAZER ? 30f : 45f));
        }
    }
}
