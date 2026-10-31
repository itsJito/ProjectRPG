package rpg.ai;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import rpg.Ids;
import rpg.Rpg;
import rpg.actor.Actor;
import rpg.actor.Actor.Combat.Move;
import rpg.combat.CombatSystem;
import rpg.interact.Result;
import rpg.item.ItemDef;
import rpg.item.ItemStack;
import rpg.item.Items;
import rpg.objects.Node;
import rpg.skill.Skill;
import rpg.ui.Dialog;
import rpg.world.DevMap;

/**
 * An NPC's mind (GDD 3.3, 4.2 gate three): perceive -> remember -> weigh competing needs and duties -> choose a goal ->
 * navigate and act -> see how it went -> reconsider. Every option is scored with a reason, and the scores, the choice,
 * what it perceives, where it is going and what happened last are kept for the AI inspector. Nothing here is a
 * scripted path: goals are chosen from the NPC's state and the world's, and fail and replan when the world says no.
 *
 * The engine's agent carries the choice out: Java writes each action's desire into the agent's facts, the engine's
 * policy scores them (with its own sight and hearing in the mix) and walks the body along routes it searches itself.
 */
public final class Brain {
    public enum Goal { FOLLOW, IDLE, LOOK, WANDER, WORK, EAT, SLEEP, SOCIALIZE, PATROL, REPORT_CRIME, INVESTIGATE, SEARCH, FIGHT, FLEE, STEAL, RETURN, HELD, COLLECT, LOOT, FENCE, HIDE, WARN, WATCH, AID, EVICT, BANK, TIP_OFF, SHELTER, PRAY, REPORT_DAMAGE, FIRE_RESPONSE }

    private final Rpg g;
    private final AiSystem ai;
    private final CombatTactics tactics;
    private final Jobs jobs;

    Brain(Rpg g, AiSystem ai) { this.g = g; this.ai = ai; this.tactics = new CombatTactics(g); this.jobs=new Jobs(g,this); }

    record Score(Goal goal, float value, String reason) {}

    /** (0.2.3) Fighters of this NPC's own side within r metres, standing and able: the watch with the watch, outlaws with outlaws. */
    int alliesNear(Npc n, float r) {
        int c = 0;
        for (Npc o : g.npcs) {
            if (o == n || !o.alive || !o.loaded || !o.combatant() || o.hp < o.maxHp() * 0.25f || o.distTo(n) > r) continue;
            boolean outlaw = n.role == Npc.Role.BANDIT || n.role == Npc.Role.BRUTE, oOutlaw = o.role == Npc.Role.BANDIT || o.role == Npc.Role.BRUTE;
            if (outlaw == oOutlaw && o.hostileToPlayer == n.hostileToPlayer) c++;
        }
        return c;
    }

    // ------------------------------------------------------------------ deciding
    public void think(Npc n) {
        // (0.2.3) spread over the whole beat by a per-NPC phase, so a crowd's decisions do not land on the same frame
        n.nextThink = g.time + (n.goal == Goal.FIGHT ? 0.35 : 0.8) * (0.8 + 0.4 * Stealth.phase(n.id + g.state.tick));
        if (n.summonOwner != 0) {
            Actor owner = g.actor(n.summonOwner), target = ai.threatFor(n);
            Goal next = target == null ? Goal.FOLLOW : Goal.FIGHT;
            if (next != n.goal) { n.hasGoalPos = false; n.legs = null; n.tactic = null; n.goalSince = g.time; g.interact.cancel(n, "bound duty changed"); }
            n.goal = next; n.fightTarget = target == null ? 0 : target.id;
            n.hunt = null; n.investigation = null; n.fearOf = 0;
            n.goalReason = target == null ? "bound to follow " + (owner == null ? "the caster" : owner.name) : "defending the caster against " + target.name;
            n.scores.clear(); n.scores.add("1.00 " + next + " - " + n.goalReason);
            return;
        }
        n.observedFire=rpg.world.FireSafety.observe(g,n,n.combatant()?7:11);
        if(n.observedFire!=null) {
            var retreat=rpg.world.FireSafety.refuge(g,n,n.observedFire);n.fireEscapeX=retreat[0];n.fireEscapeZ=retreat[1];
        }
        n.perceived = perceive(n);
        // blood, bodies and limbs in sight (Gore): each noticed once, and reacted to
        if (g.gore != null) g.gore.perceive(n);
        List<Score> s = new ArrayList<>();
        double hour = g.state.hourOfDay();
        boolean night = hour >= 22 || hour < 6;
        boolean civilian = !n.combatant();
        if(n.observedFire!=null && n.arrestedUntil<=g.state.minutes) {
            float distance=n.distTo(n.observedFire.x(),n.observedFire.z());
            s.add(new Score(Goal.FIRE_RESPONSE,distance<4?1.1f:0.91f,n.role==Npc.Role.LAWMAN?"keeping a burning building clear":n.profession==Profession.CARPENTER?"waiting for heat to clear before repairs":"getting clear of an uncontrolled fire"));
        }

        if (n.arrestedUntil > g.state.minutes) s.add(new Score(Goal.HELD, 2f, "in custody until minute " + (int) n.arrestedUntil));
        // maimed: whoever has lost a limb tries only to get away (and, legless, can barely move)
        if (g.gore != null && g.gore.wounds.containsKey(n.id) && g.gore.wounds.get(n.id).any() && n.alive)
            s.add(new Score(Goal.FLEE, 0.97f, "maimed; getting away"));
        // threats and duties of force
        Actor threat = ai.threatFor(n);
        ai.keepHunt(n);
        Actor rooftop=g.law.roofTarget(n);
        if(threat==null && rooftop!=null){n.watchTarget=rooftop.id;s.add(new Score(Goal.WATCH,0.72f,"ordering a trespasser down from a roof"));}
        // the alarm (gamefixes: a dragon in the city should cause a genuine panic): heard, it is cried on; the city's
        // fighters turn out after the terror, everyone else makes for shelter and stays there while the alarm is up
        AiSystem.Alarm alarm = ai.alarmFor(n);
        if (alarm != null && n.alarmCryAt < alarm.raisedAt) {
            n.alarmCryAt = g.time;
            String what = g.nameOf(alarm.monster).toLowerCase();
            if (n.speech == null) g.ai.say(n, defender(n) ? "The " + what + "! With me - to the " + place(alarm.x, alarm.z) + "!" : "Did you hear? A " + what + "! Inside, quick!");
            ai.heardAlarm(n, alarm);
        }
        Actor terror = alarm == null ? null : g.actor(alarm.monster);
        if (terror != null && !defender(n)) {
            if (n.fearOf != terror.id) n.hideSet = false;
            n.fearOf = terror.id; n.fearSeenAt = g.time;
            n.fearX = threat == terror ? terror.x : alarm.x; n.fearZ = threat == terror ? terror.z : alarm.z;
            s.add(new Score(Goal.HIDE, 0.93f, "sheltering from the " + terror.name.toLowerCase() + (threat == terror ? " (in sight)" : " (the alarm is up)")));
        } else if (terror != null && threat == null && (n.hunt == null || n.hunt.target != terror.id)) {
            n.hunt = new Hunt(terror.id, "turned out at the alarm", alarm.x, alarm.z, g.time, 60);
            g.state.commit("SearchStarted", n.id, terror.id, DevMap.jurisdiction(alarm.x, alarm.z), 0, "the alarm");
        }
        // a foe lost from sight, or the culprit behind something found: search where they were last placed
        if (n.hunt != null && threat == null) {
            Hunt h = n.hunt;
            String who = h.target == 0 ? "whoever did it" : g.nameOf(h.target);
            // (0.2.2) a known foe is hunted hard; a culprit nobody saw is looked for while the trail is fresh, and with no
            // new clue in a few seconds the scene's duties (the body, the report) come back ahead of it
            float search = n.combatant() ? 0.86f : 0.72f;
            if (h.target == 0) search = Math.max(0.5f, search - (float) (g.time - h.clueAt) * 0.08f);
            s.add(new Score(Goal.SEARCH, search, String.format("searching for %s near (%.0f, %.0f), %.0fs since the last clue", who, h.x, h.z, g.time - h.clueAt)));
        }
        // dread (gamefixes: dynamic reactions): someone they know for a killer, or a beast, close and in sight - they run;
        // once it is out of sight they keep hidden a while; when it is safe, whoever can tell the guard does
        Actor feared = threat == null ? fearFor(n) : null;
        if (feared != null) {
            n.fearOf = feared.id; n.fearX = feared.x; n.fearZ = feared.z; n.fearSeenAt = g.time; n.hideSet = false;
            s.add(new Score(Goal.FLEE, 0.88f, "afraid of " + feared.name + (feared == g.player ? " (I know what they did)" : "") + "; running"));
        } else if (n.fearOf != 0 && g.time - n.fearSeenAt < 14 && !n.combatant()) {
            s.add(new Score(Goal.HIDE, 0.8f, String.format("hiding from %s (last seen %.0fs ago)", g.nameOf(n.fearOf), g.time - n.fearSeenAt)));
        } else if (n.fearOf != 0 && g.actor(n.fearOf) instanceof rpg.animal.Animal beast && beast.alive && !n.combatant() && ai.guardFor(n) != null) {
            s.add(new Score(Goal.WARN, 0.79f, "safe now; telling the guard about the " + beast.kind()));
        } else if (n.fearOf != 0 && g.time - n.fearSeenAt > 14) n.fearOf = 0;
        if (threat != null) n.investigation = null;   // something real to deal with: no more wondering
        // a beast bearing down on someone who cannot fight it: what they flee, they remember (and will report)
        if (threat instanceof rpg.animal.Animal beast && !n.combatant()) { n.fearOf = beast.id; n.fearX = beast.x; n.fearZ = beast.z; n.fearSeenAt = g.time; n.hideSet = false; }
        // arms shown in the street (Arms): the watch keeps an eye on whoever is waving them about, a while
        // (and a scholar comes to see a spell worked close up)
        boolean curious = n.role == Npc.Role.SCHOLAR && n.armsKind == Arms.Kind.SPELL;
        if (threat == null && (n.watch() || curious) && n.watchTarget == g.player.id && g.time < n.watchUntil && g.time - n.armsAt < 45 && g.player.alive && (curious || ai.canSee(n, g.player)))
            s.add(new Score(Goal.WATCH, curious ? 0.72f : 0.63f, curious ? "come to see " + g.player.name + "'s magic close up" : "keeping an eye on " + g.player.name + ", who has been showing arms in the street"));
        // a guard who has heard the player is violent - but has no warrant (a rumor is not a report) - keeps an eye on them
        if (threat == null && (n.role == Npc.Role.GUARD || n.role == Npc.Role.LAWMAN) && g.player.alive && g.law.bounty(g.player.id, n.homeJurisdiction) <= 0) {
            Memory bad = n.crimeKnown(g.player.id);
            boolean violent = bad != null && !bad.settled && (bad.kind.equals("MURDER") || bad.kind.equals("ASSAULT")) && bad.confidence >= 0.45f;
            if (violent && n.distTo(g.player) < 16 && ai.canSee(n, g.player) && (g.time < n.watchUntil || g.time > n.watchUntil + 120)) {
                if (g.time > n.watchUntil) n.watchUntil = g.time + 25;
                n.watchTarget = g.player.id;
                s.add(new Score(Goal.WATCH, 0.62f, "heard " + g.player.name + " is dangerous (" + bad.kind.toLowerCase() + ", " + bad.source + "); keeping an eye on them"));
            }
        }
        if (threat == null && n.watch()) {
            // the watch drives out the outlaws of the wilds, and a thief only once it knows what they have done (a thief
            // who lives in the town is not ordered out of it for being one)
            for (Npc other : g.npcs) if (other.alive && other.loaded && ZonePolicy.outlaw(other) && (other.role != Npc.Role.THIEF || n.crimeKnown(other.id) != null)
                    && n.homeJurisdiction.equals(DevMap.jurisdiction(other.x, other.z)) && n.distTo(other) < 18 && ai.canSee(n, other)) {
                n.watchTarget = other.id; s.add(new Score(Goal.EVICT, 0.81f, "driving " + other.name + " out of the city")); break;
            }
        }
        if (ZonePolicy.outlaw(n) && g.time < n.leaveUntil) s.add(new Score(Goal.RETURN, 0.87f, "ordered out by the watch"));
        if (threat == null && (n.role == Npc.Role.GUARD || n.role == Npc.Role.LAWMAN)) {
            Actor bloody = null; float distance = 12;
            for (Actor a : g.actors()) if (a != n && a.alive && !a.dummy && (a == g.player || a instanceof Npc person && person.loaded) && a.bloodied >= 0.25f
                    && !ai.bloodExplained(n, a) && n.distTo(a) < distance && ai.canSee(n, a)) { bloody = a; distance = n.distTo(a); }
            if (bloody != null) {
                Stealth.Aware evidence = n.aware.computeIfAbsent(bloody.id, k -> new Stealth.Aware());
                if (g.time >= evidence.bloodQuestionAgain || bloody.bloodied > evidence.bloodSeen + 0.2f) {
                    evidence.bloodQuestionUntil = g.time + 8;
                    evidence.bloodQuestionAgain = g.time + 90;
                    evidence.bloodSeen = bloody.bloodied;
                }
                if (g.time < evidence.bloodQuestionUntil) {
                    n.watchTarget = bloody.id;
                    s.add(new Score(Goal.WATCH, 0.68f, "visible blood on " + bloody.name + "; checking what happened, no charge"));
                }
            }
        }
        if (threat != null && !(threat == terror && !defender(n))) {
            boolean weak = n.hp < n.maxHp() * (civilian ? 0.95f : 0.25f);
            // (0.2.3, gamefixes: human AI choices) a fighter weighs the odds as the monsters do: one facing something more than
            // twice as strong, with none of its own side near enough to help, runs for help instead of throwing its life
            // away; the watch holds its ground (that is its duty); with friends at hand, anyone stands
            float odds = rpg.monster.MonsterMind.might(threat) / Math.max(1, rpg.monster.MonsterMind.might(n));
            int friends = civilian || weak || odds <= 2 ? 0 : alliesNear(n, 14);
            boolean outmatched = !civilian && !weak && !n.watch() && n.summonOwner == 0 && odds > 2 && friends == 0;
            if (civilian || weak) s.add(new Score(Goal.FLEE, weak && !civilian ? 0.95f : 0.85f, (weak ? "badly hurt" : "not a fighter") + "; fleeing " + threat.name));
            else if (outmatched) s.add(new Score(Goal.FLEE, 0.92f, String.format("outmatched by %s (%.1fx) and alone; running for help", threat.name, odds)));
            else s.add(new Score(Goal.FIGHT, 0.9f, "engaging " + threat.name + (n.hostileToPlayer ? " (hostile)" : "") + (friends > 0 ? String.format(" (%.1fx, %d at my side)", odds, friends) : "")));
        }
        // a crime it saw with its own eyes, not yet reported, in a jurisdiction with a lawman
        Memory unreported = unreportedCrime(n);
        // (with the culprit standing right there, the report waits until it is safe to make)
        Actor culprit = unreported == null ? null : g.actor(unreported.subject);
        boolean exposed = culprit != null && culprit.alive && n.distTo(culprit) < 15 && ai.canSee(n, culprit) && !n.combatant();
        if (unreported != null && exposed) s.add(new Score(Goal.HIDE, 0.82f, "keeping out of " + culprit.name + "'s sight before reporting"));
        if (unreported != null && !exposed && ai.lawmanFor(unreported.jurisdiction) != null && unreported.subject != n.id)
            s.add(new Score(Goal.REPORT_CRIME, 0.78f, unreported.subject == 0 ? "discovered " + unreported.detail + "; must tell the law" : "saw " + g.nameOf(unreported.subject) + " commit " + unreported.kind.toLowerCase() + "; must tell the law"));
        // a wanted player seen in town by someone who knows it: the watch is told where (gamefixes)
        Npc tellWatch = threat == null ? tipFor(n) : null;
        if (tellWatch != null) s.add(new Score(Goal.TIP_OFF, 0.74f, String.format("saw %s (wanted) at (%.0f, %.0f); telling %s", g.player.name, n.tip.x(), n.tip.z(), tellWatch.name)));
        // something half-seen or heard (Stealth): a curious mind stops to look; past that, each looks into it their own
        // way (Investigation: a guard checks and searches, a bold farmhand peeks, a timid one calls out or fetches the
        // guard, a thief hides) - and keeps to that plan until it is settled
        var odd = ai.stealth.unresolved(n);
        if (n.investigation != null && threat == null) {
            Investigation v = n.investigation;
            float urgency = v.plan == Investigation.Plan.HIDE || v.plan == Investigation.Plan.FETCH_HELP ? 0.8f : n.combatant() ? 0.68f : 0.6f;
            s.add(new Score(Goal.INVESTIGATE, urgency, v.plan.name().toLowerCase().replace('_', ' ') + ": " + v.label() + String.format(" at (%.0f, %.0f) - %s", v.x, v.z, v.why)));
        } else if (odd != null && threat == null) {
            Stealth.Aware w = odd.getValue();
            String where = String.format("(%.0f, %.0f)", w.x, w.z);
            boolean heard = w.heardAt > w.seenAt;
            // (enough to keep anyone from their bed or their mischief until they have decided what to do about it)
            // (enough at night to keep anyone from their bed; by day a civilian weighs it against their work)
            double hr = g.state.hourOfDay();
            boolean nightNow = hr >= 21 || hr < 5.5;
            // (an escort keeps to the one in their charge: they turn to look, and go only for the sound of violence)
            boolean escort = n.profession == Profession.ESCORT_KNIGHT && g.time - n.violenceHeardAt > 4;
            if (w.level >= Stealth.INVESTIGATING && !escort) s.add(new Score(Goal.INVESTIGATE, nightNow ? 0.8f : n.combatant() ? 0.66f : 0.46f, (heard ? "heard something at " : "glimpsed someone at ") + where + "; deciding what to do"));
            else s.add(new Score(Goal.LOOK, 0.56f, (heard ? "a sound from " : "a movement at ") + where));
        }
        if (threat == null && !n.hostileToPlayer && hasFood(n) && n.hp > n.maxHp() * 0.7f) {
            Actor injured = null; float nearest = 12;
            for (Actor actor : g.actors()) if (actor != n && (actor == g.player || actor instanceof Npc) && (actor.team == n.team || actor == g.player && ai.rumors.opinion(n) >= 0 && !g.law.wantedAnywhere(actor.id)) && actor.hp < actor.maxHp() * 0.6f && actor.alive && n.distTo(actor) < nearest && ai.canSee(n, actor)) { injured = actor; nearest = n.distTo(actor); }
            if (injured != null) { n.aidTarget = injured.id; s.add(new Score(Goal.AID, n.role == Npc.Role.HEALER ? 0.76f : civilian ? 0.64f : 0.58f, "food to help " + injured.name + ", visibly wounded")); }
        }
        // needs
        if(threat==null && g.repairs.reportFor(n)!=null)s.add(new Score(Goal.REPORT_DAMAGE,n.role==Npc.Role.LAWMAN?0.76f:0.63f,"reporting witnessed building damage"));
        if(threat==null && g.repairs.hasWork(n))s.add(new Score(Goal.WORK,0.72f,"committed repair work"));
        if (n.hunger > 0.45f) s.add(new Score(Goal.EAT, 0.25f + n.hunger * 0.6f, String.format("hunger %.0f%%", n.hunger * 100)));
        if (night && n.role != Npc.Role.GUARD && !n.combatant() && n.role != Npc.Role.THIEF) s.add(new Score(Goal.SLEEP, 0.7f, String.format("night (%02d:00)", (int) hour)));
        else if (n.fatigue > 0.8f) s.add(new Score(Goal.SLEEP, 0.55f, String.format("fatigue %.0f%%", n.fatigue * 100)));
        if (n.social > 0.55f && !n.combatant() && ai.partnerFor(n) != null) s.add(new Score(Goal.SOCIALIZE, 0.2f + n.social * 0.45f, String.format("lonely (%.0f%%), %s nearby", n.social * 100, ai.partnerFor(n).name)));
        // duties
        switch (n.role) {
            case GUARD, LAWMAN -> s.add(new Score(Goal.PATROL, 0.5f, "on patrol duty"));
            case WORKER, VILLAGER, CARTER, MERCHANT, QUEST_GIVER, CONTACT, FARMER, UNDERTAKER, FENCE, BEGGAR, HEALER, PRIEST, HUNTER, SCHOLAR, FISHERMAN, MINER, CRAFTSMAN, KNIGHT, CIVIC -> {
                // a job under way is a commitment: a carter with a shipment to carry sees it through before lesser wants
                boolean committed = n.role == Npc.Role.CARTER && g.econ.active() != null;
                s.add(new Score(Goal.WORK, committed ? 0.88f : night ? 0.2f : 0.5f, committed ? "a shipment to carry (" + g.econ.active().status.name().toLowerCase() + ")" : "day's work: " + workText(n)));
            }
            case THIEF -> {
                float opp = ai.stealOpportunity(n);
                s.add(new Score(Goal.STEAL, opp, opp > 0.3f ? "stall unwatched" : "stall watched or empty"));
            }
            default -> { }
        }
        // stolen goods burn a hole in a crook's pocket: a thief or an outlaw carrying them takes them to the fence
        if (threat == null && n.role == Npc.Role.THIEF && stolenCarried(n) >= 2 && g.econ.merchant(g.econ.fence) != null && g.econ.merchant(g.econ.fence).alive)
            s.add(new Score(Goal.FENCE, 0.58f, stolenCarried(n) + " stolen thing(s) to sell to the fence"));
        switch (n.role) {
            case BANDIT, BRUTE -> s.add(new Score(Goal.RETURN, Math.hypot(n.x - n.spawnX, n.z - n.spawnZ) > 6 ? 0.45f : 0.05f, "keep to the pen"));
            default -> { }
        }
        // the dead's belongings (GDD 3.12): a guard carries a secured grave in to the undertaker once word of it has had
        // time to reach the watch; a thief or an outlaw who has seen an exposed grave goes to help himself
        if (threat == null && g.undertaker != null) {
            rpg.objects.Container grave = graveFor(n);
            if (grave != null) {
                // (the dead are a duty, but not before the living: a guard hunts the killer, looks into the scene and
                // takes the reports first - gamefixes: guards fetched bodies while the culprit walked away)
                if (n.watch() || n.role==Npc.Role.UNDERTAKER) s.add(new Score(Goal.COLLECT, n.role == Npc.Role.UNDERTAKER ? 0.74f : 0.58f, String.format("a death in town: carrying the grave at (%.0f, %.0f) to the undertaker", grave.x, grave.z)));
                else s.add(new Score(Goal.LOOT, n.role == Npc.Role.THIEF ? 0.74f : 0.6f, String.format("an unguarded grave at (%.0f, %.0f), full of someone's gear", grave.x, grave.z)));
            }
        }
        if (g.carrying.busy(n)) s.add(new Score(Goal.COLLECT, n.role == Npc.Role.UNDERTAKER ? 0.8f : 0.64f, "carrying a body to the undertaker"));
        // money matters: a purse too heavy to carry about, or too light to work with, means an errand to the bank (by day)
        if (threat == null && !night && g.bank != null) { String why = g.bank.errand(n); if (why != null) s.add(new Score(Goal.BANK, 0.53f, why)); }
        if (!ZonePolicy.routineAllowed(n, n.x, n.z) && threat == null && !g.carrying.busy(n)) s.add(new Score(Goal.RETURN, 0.57f, "leaving an unwelcome zone"));
        // (0.2.1, gamefixes: human AI choices expanded) a downpour sends ordinary folk out of doors home to shelter; the
        // watch keeps its round, and work under a roof goes on
        float rain = g.timeSystem.weather.rain;
        if (threat == null && rain > 0.45f && !n.combatant() && !n.watch() && !g.carrying.busy(n) && g.vox.skyLight((int) Math.floor(n.x), DevMap.FLOOR + 1, (int) Math.floor(n.z)) > 0.9f)
            s.add(new Score(Goal.SHELTER, 0.45f + rain * 0.25f, String.format("rain (%.0f%%); going in out of it", rain * 100)));
        // the devout go to their town's altar of a morning, once a day
        double hr = g.state.hourOfDay();
        int today = (int) (g.state.minutes / 1440);
        if (threat == null && !n.combatant() && devout(n) && hr >= 6.5 && hr < 9 && n.prayedDay != today && altarFor(n) != null)
            s.add(new Score(Goal.PRAY, 0.56f, "morning prayers at the altar"));
        s.add(new Score(Goal.WANDER, 0.15f, "nothing pressing"));
        s.sort(Comparator.comparing(Score::value).reversed());
        // a little inertia: keep the current goal unless another beats it clearly
        Score best = s.get(0);
        // Safety decisions interrupt routine goals as soon as they outscore them.
        boolean urgent = best.goal == Goal.FIGHT || best.goal == Goal.FLEE || best.goal == Goal.INVESTIGATE || best.goal == Goal.FIRE_RESPONSE;
        if (!urgent) for (Score c : s) if (c.goal == n.goal && c.value >= best.value - 0.06f) { best = c; break; }
        n.scores.clear();
        for (int i = 0; i < Math.min(6, s.size()); i++) n.scores.add(String.format("%.2f %s - %s", s.get(i).value, s.get(i).goal, s.get(i).reason));
        if (best.goal != n.goal) {
            if (n.goal == Goal.SOCIALIZE) { n.socialPartner = 0; n.socialSide = 0; }
            g.state.commit("NpcGoal", n.id, 0, DevMap.jurisdiction(n.x, n.z), 0, n.goal + " -> " + best.goal + " (" + best.reason + ")");
            n.goal = best.goal;
            n.lastOutcome=""; // The prior errand's status must not describe a new one.
            n.goalSince = g.time;
            n.hasGoalPos = false;
            n.legs = null;
            n.tactic = null;
            resetJob(n);
            g.interact.cancel(n, "changed goal");
        }
        if (best.goal == Goal.FIGHT && threat != null) n.fightTarget = threat.id;
        n.goalReason = best.reason;
    }

    private String workText(Npc n) {
        if(n.profession!=null)return n.profession.title+" duties";
        return switch (n.role) {
            case HUNTER -> "hunt permitted small game, butcher it and sell the actual yield"; case HEALER -> "gather herbs and tend the wounded"; case BEGGAR -> "seek a meal and shelter"; case PRIEST -> "tend the graveyard"; case SCHOLAR -> "compare accounts with the townspeople";
            case WORKER -> "cut logs, sell them to Store A"; case VILLAGER -> "pick berries, sell them to Store A"; case CARTER -> "carry Store A's surplus to Store B";
            case MERCHANT -> "mind the shop"; case LAWMAN -> "keep the chancery"; case UNDERTAKER -> "keep the graveyard"; case FENCE -> "wait for a seller"; case FARMER -> "tend the stock: feed in the trough, eggs to the chest";
            default -> "keep to their post";
        };
    }

    private Memory unreportedCrime(Npc n) {
        for (Memory m : n.memories) if (m.crime() && !m.settled && !m.withheld && m.witnessed() && !m.reported && m.subject != n.id) return m;
        return null;
    }

    private String perceive(Npc n) {
        StringBuilder b = new StringBuilder();
        for (Actor a : g.actors()) {
            if (a == n || !a.alive || a.dummy) continue;
            if (!ai.canSee(n, a)) continue;
            if (b.length() > 0) b.append(", ");
            b.append(a.name).append(String.format(" %.0fm", n.distTo(a)));
            if (a.cs.phase != Actor.Combat.Phase.IDLE) b.append(" (attacking)");
            if (a == g.player && g.law.wantedAnywhere(a.id)) b.append(" (wanted)");
        }
        return b.length() == 0 ? "nobody in sight" : b.toString();
    }

    // ------------------------------------------------------------------ acting
    public void act(Npc n, float dt) {
        n.cs.aim = 0;   // a raised crossbow is requested again only while engaging a visible target
        n.hunger = Math.min(1, n.hunger + dt / 900f);
        n.social = Math.min(1, n.social + (n.combatant() ? 0 : dt / 300f));
        n.fatigue = n.asleep() ? Math.max(0, n.fatigue - dt / 60f) : Math.min(1, n.fatigue + dt / 2400f);
        n.sneaking = n.goal == Goal.STEAL;   // a thief at work creeps
        if (n.cs.staggered > 0) { ai.desire(n, AiSystem.A_IDLE); return; }
        // a leg gone: it cannot walk off (it lies where it fell, calling out)
        if (g.gore != null && g.gore.legs(n) < 0.2f) { ai.desire(n, AiSystem.A_IDLE); n.sneaking = true; if (n.speech == null && (n.id + (long) g.time) % 7 == 0) g.ai.say(n, "Help me..."); return; }
        switch (n.goal) {
            case FIRE_RESPONSE -> {
                g.interact.cancel(n,"uncontrolled fire nearby");g.combat.setBlock(n,false);
                if(g.time-n.fireObservedAt>12) {
                    n.fireObservedAt=g.time;
                    ai.say(n,n.role==Npc.Role.LAWMAN?"Keep clear of the fire! Give the workers room.":n.profession==Profession.CARPENTER?"Let it cool. Then I can mend the damage.":n.role==Npc.Role.WORKER?"The timber's alight! Stand back.":"Fire! Get clear of the building!");
                    g.state.commit("FireResponse",n.id,0,n.homeJurisdiction,0,n.goalReason);
                }
                goTo(n,n.fireEscapeX,n.fireEscapeZ,true,0.8f);n.lastOutcome=n.goalReason;
            }
            case EVICT -> {
                Actor other = g.actor(n.watchTarget);
                if (!(other instanceof Npc outlaw) || !other.alive || !n.homeJurisdiction.equals(DevMap.jurisdiction(other.x, other.z)) || !ai.canSee(n, other)) { n.nextThink = 0; ai.desire(n, AiSystem.A_HOLD); break; }
                if (n.distTo(other) < 8) {
                    outlaw.leaveUntil = g.time + 20; outlaw.nextThink = 0;
                    if (g.time - n.goalSince < 0.5 && n.speech == null) ai.say(n, "You. Out of the city. Keep moving.");
                }
                goTo(n, other.x, other.y, other.z, true, 3.2f);
            }
            case HELD -> ai.desire(n, AiSystem.A_IDLE);
            case WANDER -> ai.desire(n, AiSystem.A_WANDER);
            case IDLE -> ai.desire(n, AiSystem.A_IDLE);
            case RETURN -> {
                // ordered out by the watch: out of the jurisdiction by the nearest way, if home is inside it
                if (ZonePolicy.outlaw(n) && g.time < n.leaveUntil && !DevMap.WILDS.equals(DevMap.jurisdiction(n.spawnX, n.spawnZ))) {
                    float[] out = Waypoints.nearestOutside(n.x, n.z);
                    if (goTo(n, out[0], out[1], false, 2f)) n.leaveUntil = 0;
                } else goTo(n, n.spawnX, n.spawnZ, false, 2f);
            }
            case FOLLOW -> followCaster(n);
            case WORK -> work(n,dt);
            case PATROL -> patrol(n);
            case EAT -> eat(n);
            case SLEEP -> { if (goTo(n, n.homeX, n.homeZ, false, 1.2f)) ai.desire(n, AiSystem.A_IDLE); }
            case SOCIALIZE -> socialize(n);
            case REPORT_CRIME -> report(n);
            case REPORT_DAMAGE -> {
                var report=g.repairs.reportFor(n);
                if(report==null){ai.desire(n,AiSystem.A_HOLD);n.nextThink=0;}
                else if(goTo(n,report.recipient().x,report.recipient().z,false,2.3f)){g.repairs.report(n,report.damage(),report.recipient());n.nextThink=0;}
            }
            case LOOK -> { Stealth.Aware w = lead(n); ai.desire(n, AiSystem.A_IDLE); if (w != null) n.wantYaw = (float) Math.atan2(-(w.x - n.x), -(w.z - n.z)); }
            case INVESTIGATE -> investigate(n);
            case SEARCH -> search(n);
            case FLEE -> {
                Actor t = ai.threatFor(n);
                if (t == null && n.fearOf != 0) { Actor f = g.actor(n.fearOf); if (f != null) { runFrom(n, f); return; } }
                if (t != null) { ai.target(n, t.body); ai.desire(n, AiSystem.A_FLEE); n.sneaking = false; if (n.speech == null && g.time - n.goalSince < 0.5) g.ai.say(n, t instanceof rpg.animal.Animal an ? "A " + an.kind() + "! Run!" : "Stay away from me!"); }
                else ai.desire(n, AiSystem.A_IDLE);
            }
            case HIDE -> hide(n);
            case WATCH -> {
                Actor p = g.actor(n.watchTarget);
                // (a curious scholar who only heard the spell goes toward it to see)
                if (p != null && p.alive && !ai.canSee(n, p) && n.role == Npc.Role.SCHOLAR && g.time - n.armsAt < 45) { goTo(n, p.x, p.y, p.z, false, 4.5f); break; }
                if (p == null || !p.alive || !ai.canSee(n, p)) { ai.desire(n, AiSystem.A_HOLD); n.nextThink = 0; break; }
                n.wantYaw = (float) Math.atan2(-(p.x - n.x), -(p.z - n.z));
                if (n.distTo(p) > 5 && !g.law.onRoof(p)) goTo(n, p.x, p.y, p.z, false, 4.5f); else ai.desire(n, AiSystem.A_HOLD);
                if (n.speech == null && g.time - n.lastDialogAt > 8) {
                    boolean arms = g.time - n.armsAt < 45;
                    g.ai.say(n, g.law.onRoof(p) ? "Get down from that roof, now." : p.bloodied >= 0.25f && !ai.bloodExplained(n, p) ? "Is that blood? Are you hurt? What happened?"
                            : arms && n.role == Npc.Role.SCHOLAR ? "Slowly, this time. I want to see how the hands go."
                            : arms ? (n.armsKind == Arms.Kind.SPELL ? "Keep your hands still where I can see them." : "Easy now. Keep it sheathed.") : "I've heard about you. Don't try anything.");
                    n.lastDialogAt = g.time;
                }
                n.lastOutcome = "watching " + p.name + (p.bloodied >= 0.25f ? "; visible blood, asking questions" : "");
            }
            case WARN -> warn(n);
            case TIP_OFF -> tipOff(n);
            case FIGHT -> fight(n, dt);
            case STEAL -> steal(n);
            case COLLECT -> { n.sneaking = false; collect(n); }
            case FENCE -> fence(n);
            case LOOT -> loot(n);
            case AID -> aid(n);
            case BANK -> bank(n);
            case SHELTER -> { if (goTo(n, n.homeX, n.homeZ, false, 1.4f)) { ai.desire(n, AiSystem.A_IDLE); n.lastOutcome = "sheltering from the rain"; } }
            case PRAY -> pray(n);
        }
    }

    /** Who keeps the morning prayers: the priests' flock - the civic and village folk, the healers and scholars, some of each (by their id). */
    private static boolean devout(Npc n) {
        return switch (n.role) { case PRIEST -> false; case CIVIC, VILLAGER, HEALER, SCHOLAR, FARMER -> Math.floorMod(n.id, 3) == 0; default -> Math.floorMod(n.id, 7) == 0; };
    }
    /** The altar of the town a person lives in (DevPalace's temple, or the debug square's), or null in the wilds. */
    private float[] altarFor(Npc n) {
        if (DevMap.PALACE.equals(n.homeJurisdiction)) return rpg.world.Expanse.ALTAR;
        if (DevMap.ALPHA.equals(n.homeJurisdiction) || DevMap.BETA.equals(n.homeJurisdiction)) return new float[]{DevMap.ALTAR[0] + 0.5f, DevMap.ALTAR[1] + 0.5f};
        return null;
    }
    /** At the altar: a moment's prayer, head bowed, a word if spoken to; done for the day. */
    private void pray(Npc n) {
        float[] at = altarFor(n);
        if (at == null) { n.nextThink = 0; return; }
        if (!goTo(n, at[0], at[1] + 1.6f, false, 1.4f)) { n.lastOutcome = "on the way to prayers"; return; }
        ai.desire(n, AiSystem.A_HOLD);
        n.wantYaw = (float) Math.atan2(-(at[0] - n.x), -(at[1] - n.z));
        if (n.prayStarted < n.goalSince) n.prayStarted = g.time;
        n.lastOutcome = "praying at the altar";
        if (g.time - n.prayStarted > 25) { n.prayedDay = (int) (g.state.minutes / 1440); n.nextThink = 0; }
    }

    /** To the bank counter; served when the teller is there (the bank shut, they come back later). */
    private void bank(Npc n) {
        float[] at = g.bank.customerSpot();
        if (!goTo(n, at[0] + (n.id % 3 - 1) * 0.7f, at[1] - 0.4f, false, 0.9f)) return;
        n.wantYaw = (float) Math.PI;   // facing the counter (south, +z)
        if (!g.bank.open()) { n.bankAgain = g.time + 300; n.lastOutcome = "the bank was shut"; n.nextThink = 0; return; }
        String done = g.bank.serve(n);
        if (done != null) { ai.say(n, done.startsWith("deposited") ? "Into my account, if you please." : "I'll need a little from my account."); n.lastOutcome = done + " at the bank"; }
        n.nextThink = 0;
    }

    /** A helpful neighbor spends real food to aid someone they can see; threats interrupt the work. */
    private void aid(Npc n) {
        Actor target = g.actor(n.aidTarget);
        if (target == null || !target.alive || target.hp >= target.maxHp() * 0.8f || !hasFood(n)) { n.nextThink = 0; ai.desire(n, AiSystem.A_IDLE); return; }
        if (!goTo(n, target.x, target.y, target.z, false, n.radius + target.radius + 0.5f)) return;
        n.wantYaw = (float) Math.atan2(-(target.x - n.x), -(target.z - n.z));
        if (n.spellBook==rpg.combat.Spells.Book.MOON && n.skills.level(Skill.MAGIC)>=60 && n.mana>=rpg.combat.Spells.Spell.VITALMEND.cost) {
            float pitch=(float)Math.atan2(target.chestY()-n.eyeY(),Math.max(0.1f,n.distTo(target)));
            if(g.combat.cast(n,rpg.combat.Spells.Spell.VITALMEND,n.wantYaw,pitch))return;
        }
        n.reachX = target.x; n.reachY = target.chestY(); n.reachZ = target.z;
        if (target == g.player) { offer(n); return; }
        if (g.time > n.busyUntil && g.time >= n.lootUntil) { n.lootStarted = g.time; n.lootUntil = g.time + 0.9; g.ai.say(n, "Here. Eat a little, and catch your breath."); }
        if (g.time < n.lootStarted + 0.6 || n.lastLootAt >= n.lootStarted) return;
        n.lastLootAt = g.time; n.busyUntil = g.time + 4;
        for (ItemStack food : n.inv.stacks()) if (food.def.heal > 0) {
            n.takeItems(food.def, 1); g.state.burn(food.def, 1);
            float before = target.hp; target.hp = Math.min(target.maxHp(), target.hp + food.def.heal);
            g.state.commit("AidGiven", n.id, target.id, DevMap.jurisdiction(n.x, n.z), 0, food.def.name + ": " + (target.hp - before) + " HP"); break;
        }
    }

    /**
     * To the player, help is an offer (gamefixes): the NPC holds the food out and the player chooses. Nothing changes
     * hands until they accept; then the very item moves from the NPC's pack to theirs (theirs now, honestly come by).
     * A refusal is taken at its word for a while.
     */
    private void offer(Npc n) {
        if (g.ui == null || g.ui.dialog != null || g.time < n.offerAgain) return;
        ItemStack food = null;
        for (ItemStack s : n.inv.stacks()) if (s.def.heal > 0 && !s.stolen) { food = s; break; }
        if (food == null) { n.aidTarget = 0; n.nextThink = 0; ai.desire(n, AiSystem.A_IDLE); return; }
        n.offerAgain = g.time + 60;
        rpg.item.ItemDef what = food.def;
        String line = n.role == Npc.Role.HEALER ? "Here. Eat a little, and catch your breath." : "You look like you need this more than I do.";
        g.ai.say(n, line);
        Dialog d = new Dialog(n.name, n.name + " holds out " + (what.name.endsWith("s") ? "some" : "AEIOU".indexOf(what.name.charAt(0)) >= 0 ? "an" : "a") + " " + what.name.toLowerCase() + ". \"" + line + "\"");
        d.npc = n.id; d.offer = new ItemStack(what, 1);
        long req = g.state.newRequestId();
        g.state.commit(req, "ItemOffered", n.id, g.player.id, DevMap.jurisdiction(n.x, n.z), 0, what.name);
        d.closeChoice("Accept", () -> {
            if (!n.alive || n.distTo(g.player) > 4 || !n.has(what, 1)) { g.chat.warn(n.name + " no longer has it to give."); return; }
            if (!g.player.canStore(new ItemStack(what, 1))) { g.chat.warn("You have no room for the " + what.name.toLowerCase() + "."); return; }
            for (ItemStack s : n.takeItems(what, 1)) { s.owner = g.player.id; s.stolen = false; g.player.store(s); }
            g.ui.received(what);
            g.ai.say(n, "Go carefully.");
            g.honor.greeted(n.id);
            n.aidTarget = 0; n.busyUntil = g.time + 4; n.nextThink = 0;
            g.state.commit(g.state.newRequestId(), "OfferAccepted", g.player.id, n.id, DevMap.jurisdiction(n.x, n.z), 0, what.name);
        });
        d.closeChoice("Decline", () -> {
            g.ai.say(n, "As you like. The offer stands if you change your mind.");
            n.aidTarget = 0; n.offerAgain = g.time + 240; n.nextThink = 0;
            g.state.commit(g.state.newRequestId(), "OfferDeclined", g.player.id, n.id, DevMap.jurisdiction(n.x, n.z), 0, what.name);
        });
        g.ui.dialog(d);
    }

    /** How many stolen stacks an NPC carries. */
    private int stolenCarried(Npc n) { int k = 0; for (var grid : n.grids()) for (ItemStack s : grid.stacks()) if (s.stolen) k++; return k; }

    /** To the fence's nook; there, every stolen thing sold to him for what he pays (a real trade, coins for goods). */
    private void fence(Npc n) {
        var shop = g.econ.fence;
        if (!goTo(n, shop.counterX + 0.4f, shop.counterZ - 0.6f, false, 1.2f)) return;
        Npc silas = g.econ.merchant(shop);
        if (silas == null || g.econ.tradeRefusal(silas, n) != null) { n.lastOutcome = "the fence won't deal now"; n.nextThink = g.time + 6; return; }
        int sold = 0;
        for (var grid : n.grids()) for (int i = 0; i < grid.size(); i++) {
            ItemStack s = grid.get(i);
            if (s == null || !s.stolen || s.def == Items.COINS) continue;
            if (g.econ.sell(n, grid, shop, i, s.amount, g.state.newRequestId()).rejected()) continue;
            sold++;
        }
        n.lastOutcome = sold > 0 ? "sold " + sold + " stolen thing(s) to " + silas.name : "the fence would take nothing";
        if (sold > 0) g.ai.say(n, "Pleasure doing business.");
        n.nextThink = 0;
    }

    /** How long a secured grave stands before word of it brings a guard (seconds). */
    static final double WORD_REACHES_WATCH = 120;

    /**
     * The grave this NPC has business with, or null. A guard: a secured grave in his own town that has stood long enough
     * for word to reach the watch, not being carried by another. A thief or outlaw: an exposed grave he can see now, or
     * one he saw and has not finished with.
     */
    rpg.objects.Container graveFor(Npc n) {
        boolean guard = n.watch(), undertaker=n.role==Npc.Role.UNDERTAKER, collector=guard||undertaker, crook = n.role == Npc.Role.THIEF || n.role == Npc.Role.BANDIT || n.role == Npc.Role.BRUTE;
        if (collector && g.graveyards.nearest(n)==null)return null;
        if (!collector && !crook || collector && g.time < n.collectAgain && !g.carrying.busy(n)) return null;
        rpg.objects.Container best = null;
        float bd = Float.MAX_VALUE;
        for (var object : g.objects.all()) {
            if (!(object instanceof rpg.objects.Container c) || c.buried || !(c.kind.equals("grave") || c instanceof rpg.objects.Corpse)) continue;
            // a dead monster is carrion, not a townsman to be buried
            if (c instanceof rpg.objects.Corpse remains && g.actor(remains.body) instanceof rpg.monster.Monster) continue;
            if (!collector && (c instanceof rpg.objects.Corpse corpse ? corpse.empty(g) : c.contents.isEmpty())) continue;
            float d = (float) Math.hypot(c.x - n.x, c.z - n.z);
            if (undertaker) {
                if (c.heldBy != 0 && c.heldBy != n.id) continue;
                if (c instanceof rpg.objects.Corpse corpse) {
                    if (corpse.npc(g) == null || corpse.delivered || !DevMap.jurisdiction(c.x,c.z).equals(n.homeJurisdiction) && d > 5) continue;
                    if (c.heldBy == 0 && (d > 24 || !g.vox.sight(n.x,n.eyeY(),n.z,c.x,c.y+0.3f,c.z))) continue;
                } else if (!c.kind.equals("grave") || c.secured || g.time < c.expiresAt) continue;
            } else if (guard) {
                if ((c.heldBy != 0 && c.heldBy != n.id) || !c.kind.equals("grave") && !(c instanceof rpg.objects.Corpse) || !DevMap.jurisdiction(c.x, c.z).equals(n.homeJurisdiction) && c.heldBy != n.id) continue;
                if (c instanceof rpg.objects.Corpse corpse) {
                    if (corpse.delivered || corpse.npc(g) == null) continue;
                    if (c.heldBy == 0 && (d > 24 || !g.vox.sight(n.x, n.eyeY(), n.z, c.x, c.y + 0.3f, c.z))) continue;
                } else {
                    if (!c.secured || c.contents.isEmpty()) continue;
                    if (c.heldBy == 0 && g.time - (c.expiresAt - rpg.law.Undertaker.GRAVE_SECONDS) < WORD_REACHES_WATCH) continue;
                }
            } else {
                if (c.secured || c.heldBy != 0 || n.graveSeen == -c.id) continue;
                boolean known = n.graveSeen == c.id;
                if (!known && (d > 24 || !g.vox.sight(n.x, n.eyeY(), n.z, c.x, c.y + 0.3f, c.z))) continue;
                n.graveSeen = c.id;
            }
            if (d < bd) { bd = d; best = c; }
        }
        return best;
    }

    /** The guard's errand: to the grave, a moment gathering it up, then the bundle carried to the undertaker's hut. */
    private void collect(Npc n) {
        rpg.objects.Container c = graveFor(n);
        if (c == null) { ai.desire(n, AiSystem.A_IDLE); n.nextThink = 0; return; }
        if (c.heldBy != n.id) {
            if (goTo(n, c.x, c.z, false, 1.2f)) {
                if (n.arrivedAt < n.goalSince) { n.arrivedAt = g.time; g.ai.say(n, "Poor soul. I'll see this to Morrow."); }
                n.wantYaw = (float) Math.atan2(-(c.x - n.x), -(c.z - n.z));
                if (g.time - n.arrivedAt > 0.5) {
                    if (c instanceof rpg.objects.Corpse && g.carrying.begin(n, c).rejected()) { n.nextThink = g.time + 1; return; }
                    c.heldBy = n.id;
                    g.state.commit("GraveCollected", n.id, c.id, DevMap.jurisdiction(c.x, c.z), 0, "carried by " + n.name);
                    n.lastOutcome = "gathered up the grave; taking it to the undertaker";
                }
            }
            return;
        }
        var plot = g.graveyards.nearest(n);
        if (plot == null) { g.carrying.drop(n, false); c.heldBy = 0; n.nextThink = g.time + 5; return; }
        if (g.carrying.busy(n) && !g.carrying.ready(n)) { ai.desire(n, AiSystem.A_HOLD); return; }
        float[] graveSpot = g.graveyards.destination(plot);
        if (graveSpot == null) { ai.desire(n, AiSystem.A_HOLD); return; }
        if (goTo(n, graveSpot[0], graveSpot[1], false, 1.1f)) {
            if (!g.graveyards.bury(c, n, plot)) { g.carrying.drop(n, false); c.heldBy = 0; n.collectAgain = g.time + 15; }
            n.lastOutcome = "buried the dead and protected their belongings"; n.nextThink = 0;
        }
    }

    /**
     * The opportunist's errand: to the grave, then a piece at a time - the most valuable first - into his own pack,
     * every piece a theft (it is someone's property, and anyone watching sees a grave robbed). He stops when his pack is
     * full or he has taken what he can carry off quickly, and keeps what he took as any thief would.
     */
    private void loot(Npc n) {
        rpg.objects.Container c = graveFor(n);
        if (c == null) { n.graveSeen = 0; ai.desire(n, AiSystem.A_IDLE); n.nextThink = 0; return; }
        if (!goTo(n, c.x, c.z, n.role != Npc.Role.THIEF, 0.75f)) return;
        n.reachX = c.x; n.reachY = c.y + 0.15f; n.reachZ = c.z;
        if (n.arrivedAt < n.goalSince) n.arrivedAt = g.time;
        n.wantYaw = (float) Math.atan2(-(c.x - n.x), -(c.z - n.z));
        if (g.time >= n.lootUntil) {
            if (g.time - n.lastLootAt < 1.3) return;
            n.lootStarted = g.time; n.lootUntil = g.time + 0.9;
        }
        if (g.time < n.lootStarted + 0.6 || n.lastLootAt >= n.lootStarted) return;
        n.lastLootAt = g.time;
        int best = -1, bv = -1;
        for (int i = 0; i < c.contents.size(); i++) { ItemStack s = c.contents.get(i); if (s != null && n.canStore(s) && s.value() > bv) { bv = s.value(); best = i; } }
        rpg.item.ItemDef.Slot worn = null;
        Npc dead = c instanceof rpg.objects.Corpse corpse ? corpse.npc(g) : null;
        if (dead != null) for (var slot : rpg.item.ItemDef.Slot.values()) {
            ItemStack item = dead.eq.get(slot);
            if (item != null && n.canStore(item) && item.value() > bv) { worn = slot; bv = item.value(); }
        }
        if (best < 0 && worn == null || n.lootTaken >= 4) {
            n.lastOutcome = n.lootTaken > 0 ? "took " + n.lootTaken + " thing(s) from the grave" : "nothing in the grave I can carry";
            n.graveSeen = n.lootTaken >= 4 || best < 0 ? -c.id : n.graveSeen;
            n.lootTaken = 0;
            n.nextThink = 0;
            return;
        }
        ItemStack source = worn != null ? dead.eq.get(worn) : c.contents.get(best);
        ItemStack s = source.copyWhole();
        boolean theft = c.isTheft(n);
        if (theft) s.markStolen(c.owner);
        // A stolen mark can change stack compatibility; never remove the source until the actual transfer fits.
        if (!n.store(s)) { n.graveSeen = -c.id; n.nextThink = 0; return; }
        if (worn != null) dead.eq.set(worn, null);
        else c.contents.take(best, source.amount);
        n.lootTaken++;
        long req = g.state.newRequestId();
        var ev = g.state.commit(req, "GraveLooted", n.id, c.id, DevMap.jurisdiction(c.x, c.z), 0, s + " taken by " + n.name);
        if (theft) g.law.theft(n, c.owner, s, c, ev.id());
        if (n.lootTaken == 1) g.ai.say(n, n.role == Npc.Role.THIEF ? "Finders keepers." : "Won't be needing this, will you?");
    }

    /**
     * Heads somewhere through the engine's routes; true once there. A long trip is walked leg by leg through the map's
     * runtime surface portals (each leg a short search for the engine); the inspector shows the current leg.
     */
    boolean rangedPosition(Npc n,Actor target,boolean visible){return tactics.move(n,target,visible);}
    public static boolean canUseLadders(Npc n){
        if(n instanceof rpg.monster.Monster m)return m.kind.form==rpg.monster.MonsterKind.Form.HUMANOID && m.kind.hw<=1 && m.kind.hh<=2.3f;
        return n.watch() || n.goal==Goal.FIGHT || n.goal==Goal.WORK && (n.profession==Profession.CARPENTER || n.role==Npc.Role.WORKER) || DevMap.WILDS.equals(DevMap.jurisdiction(n.x,n.z));
    }
    boolean goTo(Npc n,float x,float z,boolean run,float within){return goTo(n,x,Float.NaN,z,run,within);}
    boolean goTo(Npc n, float x, float targetY, float z, boolean run, float within) {
        if (n.summonOwner == 0 && !ZonePolicy.routineAllowed(n, x, z) && n.goal != Goal.COLLECT && n.goal != Goal.REPORT_CRIME && n.goal != Goal.REPORT_DAMAGE && n.goal != Goal.FLEE && n.goal != Goal.HIDE) { x = n.spawnX; z = n.spawnZ; }
        if(!Float.isFinite(targetY) && Math.hypot(n.x-x,n.z-z)<=within){ai.desire(n,AiSystem.A_HOLD);n.legs=null;n.hasGoalPos=false;if(n.lastOutcome!=null && (n.lastOutcome.contains("blocked") || n.lastOutcome.contains("no traversable")))n.lastOutcome="arrived";return true;}
        // An interaction targets the near ground beside a solid object, not its roof.
        if(!Float.isFinite(targetY) && !g.vox.navigation.validPoint(x,n.y,z,d->!d.locked || d.owner==n.id)){
            float best=Float.MAX_VALUE,bx=x,bz=z;
            for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++){
                float px=(float)Math.floor(x)+dx+.5f,pz=(float)Math.floor(z)+dz+.5f;
                if(Math.hypot(px-x,pz-z)>within+.15f || !g.vox.navigation.validPoint(px,n.y,pz,d->!d.locked || d.owner==n.id))continue;
                float distance=(float)Math.hypot(px-n.x,pz-n.z);if(distance<best){best=distance;bx=px;bz=pz;}
            }within=Math.max(.15f,within-(float)Math.hypot(bx-x,bz-z));x=bx;z=bz;
        }
        float surface=g.vox.navigation.standY(x,Float.isFinite(targetY)?targetY:n.y,z);
        if(!Float.isFinite(surface)){ai.desire(n,AiSystem.A_HOLD);n.lastOutcome="looking for supported ground";return false;}
        if (Math.hypot(n.x - x, n.z - z) <= within && (!Float.isFinite(targetY) || Math.abs(n.y-targetY)<1.2f)) { n.routeBook.success(g.vox,g.time,x,n.y,z);if(n.lastOutcome!=null && n.lastOutcome.contains("blocked"))n.lastOutcome="arrived";ai.desire(n, AiSystem.A_HOLD); n.legs = null; n.hasGoalPos = false; return true; }
        if(n.navRevision!=g.vox.navigationRevision) {
            n.navRevision=g.vox.navigationRevision;
            if(n.legs!=null && n.hasGoalPos && !g.vox.navigation.validPoint(n.gx,n.goalY,n.gz,d->!d.locked || d.owner==n.id || d.key!=null && n.has(d.key,1))) {n.legs=null;n.hasGoalPos=false;}
        }
        n.routeBook.seed(n,g);n.routeBook.readBlocked(n,g.state.minutes,g.time);
        if(g.time<n.navPauseUntil && Math.hypot(n.legsX-x,n.legsZ-z)<1 && Math.abs(n.legsY-surface)<1){ai.desire(n,AiSystem.A_HOLD);n.wantYaw=n.yaw+(float)Math.sin(g.time*1.2)*.35f;n.lastOutcome="paused to reconsider a blocked route";return false;}
        if (n.legs == null || Math.hypot(n.legsX-x,n.legsZ-z)>.5 || Math.abs(n.legsY-surface)>.5f) {
            java.util.function.Predicate<rpg.objects.Door> permission=d->n instanceof rpg.monster.Monster mo && mo.kind.form!=rpg.monster.MonsterKind.Form.HUMANOID ? d.open&&!d.locked : !d.locked||d.owner==n.id||d.key!=null&&n.has(d.key,1);
            n.legs=n.routeBook.recall(n,x,surface,z,g.vox,g.time,permission);
            if(n.legs==null)n.legs=g.vox.navigation.route(n.x,n.y,n.z,x,surface,z,permission,canUseLadders(n),n.id,n.routeBook,g.time);
            if(n.legs==null){n.legsX=x;n.legsY=surface;n.legsZ=z;n.gx=x;n.goalY=surface;n.gz=z;n.routeBook.begin(n,x,surface,z,new int[0],g.time);n.navPauseUntil=Math.min(g.time+2,n.routeBook.failed(n,g.time));ai.desire(n,AiSystem.A_HOLD);n.lastOutcome="no traversable route; looking for another way";return false;}
            n.routeBook.begin(n,x,surface,z,n.legs,g.time);
            n.legAt=0;n.legsX=x;n.legsY=surface;n.legsZ=z;
        }
        float tx = x, tz = z, routeY=surface;
        while (n.legAt < n.legs.length) {
            float[] p = Waypoints.point(n.legs[n.legAt]);
            if (Math.hypot(n.x - p[0], n.z - p[1]) < 1.8 && (p.length<3 || Math.abs(n.y-p[2])<.65)) { n.legAt++; continue; }
            tx = p[0]; tz = p[1];routeY=p.length>2?p[2]:surface;
            break;
        }
        float ty=routeY;
        if (!n.hasGoalPos || Math.hypot(n.gx - tx, n.gz - tz) > 0.4 || Math.abs(n.goalY-ty)>.4f) {
            n.gx = tx; n.gz = tz; n.hasGoalPos = true;
            n.goalY=ty;
            if(Float.isFinite(n.goalY))g.game.commands().agentGoal(n.agent,tx,n.goalY,tz);
        }
        ai.desire(n, run ? ai.pace(n, x, z, within) : AiSystem.A_GOTO);
        return false;
    }

    /** The strongest thing it is aware of but cannot place for certain. */
    private Stealth.Aware lead(Npc n) { var e = ai.stealth.unresolved(n); return e == null ? null : e.getValue(); }

    // ------------------------------------------------------------------ investigating (each in their own way)
    /** The plan for something noticed, from who they are and how it feels: night, dark, violence, company. */
    private Investigation choose(Npc n, long subject, Stealth.Aware w) {
        Investigation.Stimulus st = g.time - n.violenceHeardAt < 4 ? Investigation.Stimulus.VIOLENCE : w.seenAt >= w.heardAt ? Investigation.Stimulus.GLIMPSE : Investigation.Stimulus.SOUND;
        Investigation v = new Investigation(subject, w.x, w.z, st, g.time);
        double hour = g.state.hourOfDay();
        boolean night = hour >= 21 || hour < 5.5;
        boolean dark = ai.stealth.light(w.x, DevMap.FLOOR + 1, w.z) < 0.25f;
        int company = 0;
        for (Npc o : g.npcs) if (o != n && o.alive && o.loaded && o.goal != Goal.SLEEP && o.distTo(n) < 8 && bystander(o)) company++;
        float c = ai.bravery(n) - (night ? 0.3f : 0) - (dark ? 0.12f : 0) - (st == Investigation.Stimulus.VIOLENCE ? 0.25f : 0) + Math.min(0.16f, company * 0.08f) + (g.time < n.vigilantUntil ? 0.05f : 0);
        v.courage = c;
        StringBuilder why = new StringBuilder(String.format("courage %.2f", c));
        if (night) why.append(", night"); if (dark) why.append(", dark there"); if (st == Investigation.Stimulus.VIOLENCE) why.append(", sounds of violence"); if (company > 0) why.append(", not alone");
        v.why = why.toString();
        switch (n.role) {
            case THIEF, FENCE -> v.plan = Investigation.Plan.HIDE;                 // it might be the law: slip away
            case BANDIT, BRUTE -> v.plan = Investigation.Plan.CONFRONT;
            case GUARD, LAWMAN -> v.plan = Investigation.Plan.SEARCH;
            default -> {
                if (c >= 0.45f) v.plan = night || dark ? Investigation.Plan.PEEK : Investigation.Plan.CHECK;
                else if (c >= 0.2f) v.plan = Investigation.Plan.CALL_OUT;
                else v.plan = ai.guardFor(n) != null ? Investigation.Plan.FETCH_HELP : Investigation.Plan.HIDE;
            }
        }
        g.state.commit("InvestigationPlanned", n.id, subject, DevMap.jurisdiction(n.x, n.z), 0, v.plan + " (" + v.why + ")");
        return v;
    }

    /** How long the first moment of noticing lasts before they act (the quick react quicker). */
    private double reactTime(Npc n) { return 0.5 + (1 - ai.bravery(n)) * 0.8; }

    /** The line called out into the dark. */
    private String callLine(Npc n, Investigation v) {
        double hour = g.state.hourOfDay();
        boolean night = hour >= 21 || hour < 5.5;
        return switch (n.role) {
            case GUARD, LAWMAN -> night ? "Who goes there? Show yourself!" : "You there! Step out where I can see you.";
            case BANDIT, BRUTE -> "Come out, coward!";
            default -> v.courage < 0.3f ? "H-hello? Who's there?" : "Hello? Is someone there?";
        };
    }

    /** What they say when it is settled. */
    private String settleLine(Npc n, Investigation v) {
        boolean law = n.role == Npc.Role.GUARD || n.role == Npc.Role.LAWMAN;
        double hour = g.state.hourOfDay();
        boolean night = hour >= 21 || hour < 5.5;
        if (v.plan == Investigation.Plan.HIDE) return n.role == Npc.Role.THIEF ? "..." : "I think it's gone.";
        if (law) return night ? "Nothing. Stay sharp." : "All clear.";
        if (n.combatant()) return "Hmph. Nobody.";
        return switch ((int) (n.id % 3)) { case 0 -> "Must have been the wind."; case 1 -> "Just a cat, I hope."; default -> "Hm. Nothing there."; };
    }

    /**
     * Looking into it (gamefixes): the plan carried through, stage by stage - noticing, calling out, going to look or
     * creeping close enough to peek, looking round, searching nearby, fetching the guard, or hiding - and every new
     * sound moves the spot (and a second one can change a timid mind to fetching help).
     */
    private void investigate(Npc n) {
        Stealth.Aware w = lead(n);
        var odd = ai.stealth.unresolved(n);
        Investigation v = n.investigation;
        if (v == null) {
            if (w == null) { ai.desire(n, AiSystem.A_IDLE); n.nextThink = Math.min(n.nextThink, g.time + 0.2); return; }
            v = n.investigation = choose(n, odd.getKey(), w);
        }
        // Continue the original lead, not whichever unrelated sound happens to rank highest now.
        w = n.aware.get(v.subject);
        double patience = v.stimulus == Investigation.Stimulus.VIOLENCE || v.plan == Investigation.Plan.HIDE ? 50 : 20;
        if (g.time - v.startedAt > patience) v.stage(Investigation.Stage.DONE, g.time);
        // A nearby figure identified in clear sight settles an ordinary glimpse or footstep, rather than a full search.
        Actor identified = g.actor(v.subject);
        Memory charge = identified == null ? null : n.crimeKnown(identified.id);
        if (v.stimulus != Investigation.Stimulus.VIOLENCE && w != null && w.exposure >= Stealth.CLEAR && w.seenAt >= w.heardAt
                && identified instanceof Npc person && !person.hostileToPlayer && !person.sneaking && n.distTo(person) < 6
                && (charge == null || charge.settled)) v.stage(Investigation.Stage.DONE, g.time);
        // a fresh clue moves the spot; heard again, a timid plan gives way to fetching help
        if (w != null && Math.max(w.heardAt, w.seenAt) > v.lastClue + 0.05) {
            v.x = w.x; v.z = w.z; v.lastClue = Math.max(w.heardAt, w.seenAt); v.repeats++;
            if (v.repeats == 2 && (v.plan == Investigation.Plan.CALL_OUT || v.plan == Investigation.Plan.PEEK) && ai.guardFor(n) != null && !n.combatant()) {
                v.plan = Investigation.Plan.FETCH_HELP; v.why += ", heard it again"; v.stage(Investigation.Stage.FETCHING, g.time);
                g.ai.say(n, "There it is again... I'm getting the guard.");
            }
        }
        double t = v.inStage(g.time);
        float toward = (float) Math.atan2(-(v.x - n.x), -(v.z - n.z));
        boolean law = n.role == Npc.Role.GUARD || n.role == Npc.Role.LAWMAN;
        double hour = g.state.hourOfDay();
        boolean night = hour >= 21 || hour < 5.5;
        n.lastOutcome = v.label();
        switch (v.stage) {
            case NOTICED -> {
                ai.desire(n, AiSystem.A_HOLD);
                n.wantYaw = toward;
                if (t < reactTime(n)) return;
                switch (v.plan) {
                    case HIDE -> v.stage(Investigation.Stage.HIDING, g.time);
                    case FETCH_HELP -> { g.ai.say(n, v.courage < 0.1f ? "I'm not going over there. Guard!" : "I'd better tell the guard."); v.stage(Investigation.Stage.FETCHING, g.time); }
                    case CALL_OUT -> v.stage(Investigation.Stage.CALLING, g.time);
                    case SEARCH -> v.stage(night || v.stimulus == Investigation.Stimulus.GLIMPSE ? Investigation.Stage.CALLING : Investigation.Stage.APPROACHING, g.time);
                    case CONFRONT -> { g.ai.say(n, callLine(n, v)); v.stage(Investigation.Stage.APPROACHING, g.time); }
                    default -> v.stage(Investigation.Stage.APPROACHING, g.time);
                }
            }
            case CALLING -> {
                ai.desire(n, AiSystem.A_HOLD);
                n.wantYaw = toward;
                if (t < 0.05) g.ai.say(n, callLine(n, v));
                if (t > (night ? 3.5 : 2.5)) {
                    if (v.plan == Investigation.Plan.CALL_OUT) { if (v.repeats > 0 && ai.guardFor(n) != null) v.stage(Investigation.Stage.FETCHING, g.time); else v.stage(Investigation.Stage.DONE, g.time); }
                    else v.stage(Investigation.Stage.APPROACHING, g.time);
                }
            }
            case APPROACHING -> {
                float stop = v.plan == Investigation.Plan.PEEK ? 4.5f : 1.3f;
                boolean run = v.plan == Investigation.Plan.CONFRONT || law && v.stimulus == Investigation.Stimulus.VIOLENCE;
                if (Math.hypot(v.x - n.x, v.z - n.z) < 6) n.wantYaw = toward;
                // creeping up to it: a peeker keeps low; a guard at night goes carefully, weapon ready
                n.sneaking = v.plan == Investigation.Plan.PEEK;
                if (law && night) g.combat.setBlock(n, false);
                if (goTo(n, v.x, v.z, run, stop) || t > 25) { n.sneaking = false; v.stage(Investigation.Stage.LOOKING, g.time); n.arrivedAt = g.time; }
            }
            case LOOKING -> {
                ai.desire(n, AiSystem.A_HOLD);
                n.wantYaw = toward + (float) Math.sin(t * 0.9) * 1.4f;
                if (t > 3) {
                    boolean search = (v.plan == Investigation.Plan.SEARCH || v.plan == Investigation.Plan.CONFRONT) && v.checked < (night ? 3 : 2);
                    v.stage(search ? Investigation.Stage.SEARCHING : Investigation.Stage.DONE, g.time);
                }
            }
            case SEARCHING -> {
                if (!v.hasSpot) {
                    java.util.Random rnd = new java.util.Random(n.id * 31 + v.checked * 7919L + (long) (v.startedAt * 10));
                    for (int i = 0; i < 12 && !v.hasSpot; i++) {
                        double a = rnd.nextDouble() * Math.PI * 2, d = 2.5 + rnd.nextDouble() * 4;
                        float x = v.x + (float) (Math.cos(a) * d), z = v.z + (float) (Math.sin(a) * d);
                        if (!standable(x, z)) continue;
                        v.px = (float) Math.floor(x) + 0.5f; v.pz = (float) Math.floor(z) + 0.5f; v.hasSpot = true;
                    }
                    if (!v.hasSpot) { v.stage(Investigation.Stage.DONE, g.time); return; }
                }
                if (goTo(n, v.px, v.pz, false, 0.9f) || t > 14) {
                    if (n.arrivedAt < v.stageAt) { n.arrivedAt = g.time; v.lookYaw=n.yaw; }
                    n.wantYaw = v.lookYaw + (float) Math.sin((g.time - n.arrivedAt) * 2.4) * 0.65f;
                    if (g.time - n.arrivedAt > 1.4 || t > 14) { v.checked++; v.hasSpot = false; v.stage(v.checked >= (night ? 3 : 2) ? Investigation.Stage.DONE : Investigation.Stage.SEARCHING, g.time); }
                }
            }
            case FETCHING -> {
                Npc guard = v.helper != 0 && g.actor(v.helper) instanceof Npc h && h.alive ? h : ai.guardFor(n);
                if (guard == null) { v.plan = Investigation.Plan.HIDE; v.stage(Investigation.Stage.HIDING, g.time); return; }
                v.helper = guard.id;
                if (goTo(n, guard.x + 1.1f, guard.z + 1.1f, v.stimulus == Investigation.Stimulus.VIOLENCE, 1.8f)) {
                    // the lead handed over: the guard now has it, from their account (where, and what it was)
                    n.wantYaw = (float) Math.atan2(-(guard.x - n.x), -(guard.z - n.z));
                    g.ai.say(n, v.stimulus == Investigation.Stimulus.VIOLENCE ? "Come quick - fighting, by the " + place(v.x, v.z) + "!" : "I heard someone creeping about by the " + place(v.x, v.z) + ".");
                    handOver(guard, v);
                    g.state.commit("LeadReported", n.id, guard.id, DevMap.jurisdiction(n.x, n.z), 0, String.format("%s at (%.0f, %.0f)", v.stimulus, v.x, v.z));
                    v.stage(Investigation.Stage.DONE, g.time);
                }
            }
            case HIDING -> {
                if (!n.hideSet) { float[] h = hideSpot(n, v.x, v.z); n.hideX = h[0]; n.hideZ = h[1]; n.hideSet = true; }
                n.sneaking = true;
                if (goTo(n, n.hideX, n.hideZ, t < 4, 0.9f)) { ai.desire(n, AiSystem.A_HOLD); n.wantYaw = toward; }
                // out again once it has been quiet a while - or, in a street that is never quiet, after a long minute of it
                if (t > 12 && g.time - v.lastClue > 8 || t > 60) { n.sneaking = false; n.hideSet = false; v.stage(Investigation.Stage.DONE, g.time); }
            }
            case DONE -> {
                if (w != null) { w.settledAt = g.time; w.level = Math.min(w.level, Stealth.CURIOUS); }
                n.vigilantUntil = Math.max(n.vigilantUntil, g.time + 60);
                if (n.speech == null) g.ai.say(n, settleLine(n, v));
                n.investigation = null;
                n.sneaking = false;
                n.nextThink = 0;
            }
        }
    }

    /** A guard given a lead by someone who came to him: it is his to look into now (his own search, in his own way). */
    private void handOver(Npc guard, Investigation from) {
        if (g.law.settled(from.subject, from.startedAt) && g.law.bounty(from.subject, guard.homeJurisdiction) == 0) return;
        if (guard.investigation != null) { g.ai.reply(guard, "I heard it too. I'm on it."); return; }
        Stealth.Aware w = guard.aware.computeIfAbsent(from.subject, k -> new Stealth.Aware());
        w.level = Math.max(w.level, Stealth.INVESTIGATING + 0.1f);
        w.x = from.x; w.z = from.z; w.heardAt = g.time; w.settledAt = -99;
        Investigation v = new Investigation(from.subject, from.x, from.z, from.stimulus, g.time);
        v.plan = Investigation.Plan.SEARCH; v.courage = ai.bravery(guard); v.why = "told of it";
        v.stage(Investigation.Stage.APPROACHING, g.time);
        guard.investigation = v;
        g.ai.reply(guard, "I'll take a look.");
        guard.nextThink = 0;
    }

    /** A plain name for a spot (the nearest landmark), for what people tell each other. */
    private String place(float x, float z) {
        String best = "square"; double bd = 1e9;
        Object[][] marks = {{"well", (float) DevMap.WELL[0], (float) DevMap.WELL[1]}, {"store", 91f, 57f}, {"chancery", 114f, 57f}, {"houses", 97f, 90f},
                {"graveyard", 118f, 93f}, {"farm", 104f, 110f}, {"pond", 96f, 34f}, {"road", 64f, 85f}, {"bay", 40f, 105f}, {"woods", 22f, 22f}};
        for (Object[] m : marks) { double d = Math.hypot(x - (float) m[1], z - (float) m[2]); if (d < bd) { bd = d; best = (String) m[0]; } }
        return best;
    }

    /**
     * Somewhere to hide from a spot (or a person): home, if it is near and night or a killer is about; else a cell out of
     * sight of the spot, with cover round it, some way off from it.
     */
    private float[] hideSpot(Npc n, float fx, float fz) {
        java.util.Random rnd = new java.util.Random(n.id * 977 + (long) g.time);
        float bestScore = -1e9f, bx = n.x, bz = n.z;
        for (int i = 0; i < 24; i++) {
            double a = rnd.nextDouble() * Math.PI * 2, d = 5 + rnd.nextDouble() * 9;
            float x = n.x + (float) (Math.cos(a) * d), z = n.z + (float) (Math.sin(a) * d);
            if (!standable(x, z)) continue;
            x = (float) Math.floor(x) + 0.5f; z = (float) Math.floor(z) + 0.5f;
            // Cover beyond the threat is not safety: do not cross its reach to get there.
            if (!safeRetreat(n,fx,fz,x,z)) continue;
            float score = 0;
            if (!g.vox.sight(fx, DevMap.FLOOR + 1.5f, fz, x, DevMap.FLOOR + 1.0f, z)) score += 4;
            score += (float) Math.hypot(x - fx, z - fz) * 0.25f;
            int cover = 0;
            for (int ox = -1; ox <= 1; ox++) for (int oz = -1; oz <= 1; oz++) if ((ox != 0 || oz != 0) && g.vox.solid((int) Math.floor(x) + ox, DevMap.FLOOR, (int) Math.floor(z) + oz)) cover++;
            score += Math.min(3, cover) * 0.7f;
            if (score > bestScore) { bestScore = score; bx = x; bz = z; }
        }
        return new float[]{bx, bz};
    }

    /**
     * Running from something they dread (not a fight they are in): away from it to somewhere they could stand, toward
     * town and home rather than into the wild where it can be helped, re-aimed as it moves.
     */
    private boolean safeRetreat(Npc n,float fx,float fz,float x,float z) {
        float dx=x-n.x,dz=z-n.z,length=dx*dx+dz*dz;
        float t=length>0?Math.max(0,Math.min(1,((fx-n.x)*dx+(fz-n.z)*dz)/length)):0;
        double distance=Math.hypot(n.x+dx*t-fx,n.z+dz*t-fz);
        return distance>=Math.min(3,Math.hypot(n.x-fx,n.z-fz)-.1)
            && g.vox.navigation.validLeg(n.x,n.y,n.z,x,n.y,z,d->d.open && !d.locked);
    }

    private void runFrom(Npc n, Actor f) {
        if (n.speech == null && g.time - n.goalSince < 0.5) g.ai.say(n, f instanceof rpg.animal.Animal an ? "A " + an.kind().toLowerCase() + "! Run!" : "Stay away from me!");
        n.sneaking = false;
        float ax = n.x - f.x, az = n.z - f.z, al = Math.max(0.1f, (float) Math.hypot(ax, az));
        ax /= al; az /= al;
        float bx = n.x, bz = n.z, best = -1e9f;
        for (int i = -4; i <= 4; i++) {
            double a = Math.atan2(az, ax) + i * 0.32;
            for (float d : new float[]{10, 7, 4.5f}) {
                float x = n.x + (float) Math.cos(a) * d, z = n.z + (float) Math.sin(a) * d;
                if (!standable(x, z) || !safeRetreat(n,f.x,f.z,x,z)) continue;
                float score = (float) Math.hypot(x - f.x, z - f.z) - Math.abs(i) * 0.4f;
                if (n.homeX != 0) score -= (float) Math.hypot(x - n.homeX, z - n.homeZ) * 0.05f;
                if (!g.vox.clear(n.x, n.eyeY(), n.z, x, DevMap.FLOOR + 1, z)) score -= 3;
                if (score > best) { best = score; bx = x; bz = z; }
                break;
            }
        }
        goTo(n, bx, bz, true, 0.6f);
        n.lastOutcome = "running from " + f.name;
    }

    /**
     * Those who stand against a terror when the alarm goes up: the watch, the knights, the hunters (and whatever a
     * caster has called); everyone else - the bandits too - takes cover.
     */
    static boolean defender(Npc n) { return n.watch() || n.role == Npc.Role.KNIGHT || n.role == Npc.Role.HUNTER || n.summonOwner != 0; }

    /**
     * Keeping hidden from what they fled (crouched where they cannot be seen), until it has been out of sight a while.
     * From a terror there is no crouching behind a cart: they run for the shelter of home, if it is near enough.
     */
    private void hide(Npc n) {
        boolean terror = g.actor(n.fearOf) instanceof rpg.monster.Monster tm && tm.kind.terror() > 0;
        if (!n.hideSet) {
            float[] h = terror && n.homeX != 0 && Math.hypot(n.homeX - n.x, n.homeZ - n.z) < 90 ? new float[]{n.homeX, n.homeZ} : hideSpot(n, n.fearX, n.fearZ);
            n.hideX = h[0]; n.hideZ = h[1]; n.hideSet = true;
        }
        n.sneaking = !terror || Math.hypot(n.hideX - n.x, n.hideZ - n.z) < 1.5f;
        n.lastOutcome = "hiding from " + g.nameOf(n.fearOf);
        if (goTo(n, n.hideX, n.hideZ, true, 0.9f)) { ai.desire(n, AiSystem.A_HOLD); n.wantYaw = (float) Math.atan2(-(n.fearX - n.x), -(n.fearZ - n.z)); }
        if (n.speech == null && (n.id + (long) g.time) % 9 == 0) g.ai.say(n, "Please don't find me...");
    }

    /** Safe again, off to the nearest guard to tell him about the beast (he takes it up as a search for it). */
    private void warn(Npc n) {
        Npc guard = ai.guardFor(n);
        Actor beast = g.actor(n.fearOf);
        if (guard == null || beast == null || !beast.alive) { n.fearOf = 0; n.nextThink = 0; return; }
        if (goTo(n, guard.x + 1.1f, guard.z + 1.1f, true, 1.8f)) {
            g.ai.say(n, "A " + (beast instanceof rpg.animal.Animal an ? an.kind() : beast.name) + " - by the " + place(n.fearX, n.fearZ) + "! It nearly had me!");
            // a beast among the townsfolk comes before an old trail (but not before a live chase after the player)
            boolean busy = guard.hunt != null && guard.hunt.target == g.player.id && g.time - guard.hunt.clueAt < 20;
            if (!busy) {
                guard.hunt = new Hunt(beast.id, n.name + " saw a beast", n.fearX, n.fearZ, g.time, 55);
                g.state.commit("SearchStarted", guard.id, beast.id, DevMap.jurisdiction(n.fearX, n.fearZ), 0, "told by " + n.name);
            }
            g.ai.reply(guard, "Stay here. I'll deal with it.");
            guard.nextThink = 0;
            n.fearOf = 0;
            n.nextThink = 0;
        }
    }

    /**
     * A tip-off (gamefixes: good people who know the player is wanted report where they saw them). A law-abiding
     * civilian who has just seen the player with their own eyes, knowing them wanted here (a crime of theirs witnessed
     * or heard of, not settled, and a bounty on the books), carries that sighting to the nearest officer of the town who
     * is not already on the player - by walking to him, so stopping the messenger stops the word. The officer searches
     * where the player was seen. Once told, the same person waits a couple of minutes before telling again.
     */
    private Npc tipFor(Npc n) {
        if (n.combatant() || !n.lawAbiding() || n.asleep() || g.time - n.tippedAt < 120) return null;
        Actor p = g.player;
        String jur = n.homeJurisdiction;
        if (!p.alive || DevMap.WILDS.equals(jur) || g.law.bounty(p.id, jur) <= 0 || g.law.inCustody(p)) return null;
        Memory known = n.crimeKnown(p.id);
        if (known == null || known.settled) return null;
        var seen = n.playerSighting;
        if (n.tip == null || seen != null && seen.witness() == n.id && seen.at() > n.tip.at()) n.tip = seen != null && seen.witness() == n.id && g.time - seen.at() < 20 ? seen : null;
        if (n.tip == null || g.time - n.tip.at() > 90 || !jur.equals(DevMap.jurisdiction(n.tip.x(), n.tip.z()))) { n.tip = null; return null; }
        // too close to the wanted one to slip away and tell: it waits until out of their sight
        if (n.distTo(p) < 6 && ai.canSee(n, p)) return null;
        Npc officer = null; float best = 45;
        for (Npc o : g.npcs) if (o.alive && o.loaded && o.watch() && jur.equals(o.homeJurisdiction) && o.fightTarget != p.id && !(o.hunt != null && o.hunt.target == p.id && o.hunt.clueAt >= n.tip.at())) {
            float d = n.distTo(o);
            if (d < best) { best = d; officer = o; }
        }
        return officer;
    }
    private void tipOff(Npc n) {
        Npc officer = tipFor(n);
        if (officer == null) { n.tip = null; n.nextThink = 0; return; }
        if (!goTo(n, officer.x + 1.1f, officer.z + 1.1f, true, 1.9f)) { n.lastOutcome = "hurrying to tell " + officer.name; return; }
        var seen = n.tip;
        g.ai.say(n, g.player.name + " - the wanted one! I saw them by the " + place(seen.x(), seen.z()) + ".");
        Hunt h = officer.hunt != null && officer.hunt.target == g.player.id ? officer.hunt : new Hunt(g.player.id, n.name + " saw them", seen.x(), seen.z(), seen.at(), 60);
        if (h == officer.hunt) h.clue(seen.x(), seen.z(), seen.vx(), seen.vz(), seen.at()); else { h.vx = seen.vx(); h.vz = seen.vz(); officer.hunt = h; }
        if (officer.playerSighting == null || officer.playerSighting.at() < seen.at()) officer.playerSighting = seen;
        g.ai.reply(officer, "Good. I'll look into it.");
        officer.nextThink = 0;
        g.state.commit("WantedReported", n.id, g.player.id, officer.homeJurisdiction, 0, String.format("told %s: seen at (%.0f, %.0f) %.0fs ago", officer.name, seen.x(), seen.z(), g.time - seen.at()));
        n.tippedAt = g.time; n.tip = null; n.nextThink = 0;
        n.lastOutcome = "told " + officer.name + " where the wanted player was";
    }

    /** A beast that kills: a hunter, or a bear (people keep away from one whatever it is doing). */
    static boolean predator(rpg.animal.Animal an) { return !an.baby && (an.species.diet == rpg.animal.Species.Diet.HUNTER && an.species != rpg.animal.Species.FOX || an.species == rpg.animal.Species.BROWN_BEAR); }

    /** What this NPC fears and is close enough to run from (not to fight): a known killer of theirs, or a beast. */
    private Actor fearFor(Npc n) {
        if (n.combatant()) return null;
        java.util.Map<Long, Integer> murders = new java.util.HashMap<>();
        for (Memory m : n.memories) if (m.kind.equals("MURDER") && m.witnessed() && g.state.minutes - m.minutes < 30) murders.merge(m.subject, 1, Integer::sum);
        for (var e : murders.entrySet()) if (e.getValue() >= 2) { Actor killer = g.actor(e.getKey()); if (killer != null && killer.alive && n.distTo(killer) < 24 && ai.canSee(n, killer)) return killer; }
        Actor p = g.player;
        if (p.alive && n.distTo(p) < 12 && ai.rumors.afraid(n) && ai.canSee(n, p)) return p;
        if (g.animals != null) for (rpg.animal.Animal an : g.animals.all())
            if (an.alive && an.loaded && (an.threatens(n) || predator(an) && n.distTo(an) < 11) && n.distTo(an) < 16 && ai.canSee(n, an)) return an;
        return null;
    }

    /**
     * The search (gamefixes): first to where the quarry was last placed - a little along the way they were going - at a
     * run; then spot after spot in the area they could have reached by now, choosing the ones it cannot see from where
     * it stands (round corners, behind cover) and that no one else searching is already checking, looking round each.
     */
    private void search(Npc n) {
        Hunt h = n.hunt;
        if (h == null) { ai.desire(n, AiSystem.A_IDLE); n.nextThink = Math.min(n.nextThink, g.time + 0.2); return; }
        if (g.gore != null) g.gore.followTrail(n);
        if (n.watch() && h.target != 0 && askAround(n, h)) return;
        if (n.posse && n.profession == Profession.BOUNTY_HUNTER && setSnare(n, h)) return;
        if (n.stuck && g.time-h.startedAt>3) { h.reachedLkp=true;h.hasPoint=false; }
        if (!h.reachedLkp) {
            float lead = (float) Math.min(2.5, Math.hypot(h.vx, h.vz) * 0.7);
            float sp = (float) Math.hypot(h.vx, h.vz);
            float lx = h.x + (sp > 0.3f ? h.vx / sp * lead : 0), lz = h.z + (sp > 0.3f ? h.vz / sp * lead : 0);
            if (!standable(lx, lz)) { lx = h.x; lz = h.z; }
            if (Math.hypot(lx - n.x, lz - n.z) < 5) n.wantYaw = (float) Math.atan2(-(lx - n.x), -(lz - n.z));
            if (goTo(n, lx, lz, true, 1.3f)) {
                h.reachedLkp = true;
                h.lookYaw = n.yaw; h.lookUntil = g.time + 1.6;
                n.lastOutcome = "reached the last known location; nobody there";
            }
            return;
        }
        if (g.time < h.lookUntil) {
            ai.desire(n, AiSystem.A_HOLD);
            n.wantYaw = h.lookYaw + (float) Math.sin((h.lookUntil - g.time) * 2.1) * 0.65f;
            return;
        }
        if (!h.hasPoint && !pickSearchSpot(n, h)) { h.lookYaw = n.yaw; h.lookUntil = g.time + 1.0; return; }
        if (goTo(n, h.px, h.pz, false, 0.9f)) {
            h.hasPoint = false;
            h.checked++;
            h.lookYaw = n.yaw; h.lookUntil = g.time + 1.1 + (n.id % 3) * 0.3;
            n.lastOutcome = "checked a spot (" + h.checked + "); nobody";
        }
    }

    /**
     * The watch asks after the one it is searching for (gamefixes): an officer on a search stops someone nearby and asks
     * whether they have seen them. The answer is that person's own memory - where and when they last saw them, if they
     * did - and a newer sighting than the officer's own moves the search there. Someone who thinks well of the one sought
     * may say they saw nothing; someone who fears or loathes them tells all. Each person is asked once a while.
     */
    private boolean askAround(Npc n, Hunt h) {
        if (g.time < n.askAgain) return false;
        Actor sought = g.actor(h.target);
        if (sought == null) return false;
        Npc best = null; float bd = 7;
        for (Npc w : g.npcs) {
            if (w == n || w.id == h.target || !w.alive || !w.loaded || w.asleep() || w.reacting || w.watch() || w.hostileToPlayer || w instanceof rpg.monster.Monster) continue;
            if (w.goal == Goal.FLEE || w.goal == Goal.FIGHT || w.goal == Goal.HIDE || g.law.inCustody(w)) continue;
            Double asked = n.questioned.get(w.id);
            if (asked != null && g.time - asked < 90) continue;
            float d = n.distTo(w);
            if (d < bd && ai.canSee(n, w)) { bd = d; best = w; }
        }
        if (best == null) { n.askAgain = g.time + 2; return false; }
        if (bd > 2.6f) { goTo(n, best.x, best.z, false, 2.2f); n.lastOutcome = "going to ask " + best.name + " about " + sought.name; return true; }
        Npc w = best;
        n.questioned.put(w.id, g.time);
        n.askAgain = g.time + 6;
        ai.desire(n, AiSystem.A_HOLD);
        n.wantYaw = (float) Math.atan2(-(w.x - n.x), -(w.z - n.z));
        w.wantYaw = (float) Math.atan2(-(n.x - w.x), -(n.z - w.z));
        ai.say(n, pickLine(n, "Have you seen " + sought.name + "?", "I'm looking for " + sought.name + ". Seen them?", "You there - " + sought.name + ". Have they been by?"));
        // what they remember: their last clear sight of the player (kept), or of anyone else the passing awareness
        Npc.Sighting last = sought == g.player ? w.playerSighting : null;
        Stealth.Aware glimpse = w.aware.get(h.target);
        float sx = last != null ? last.x() : glimpse != null ? glimpse.x : 0, sz = last != null ? last.z() : glimpse != null ? glimpse.z : 0;
        double seenAt = last != null ? last.at() : glimpse != null ? glimpse.seenAt : -99;
        boolean saw = seenAt > -90 && g.time - seenAt < 900;
        // what they think of the one sought decides whether they say: a friend covers for them
        boolean covers = sought == g.player && ai.rumors.opinion(w) > 0.55f && w.crimeKnown(sought.id) == null;
        if (!saw || covers) {
            ai.say(w, covers ? pickLine(w, "No. Haven't seen anyone like that.", "Can't say I have.") : pickLine(w, "No, sorry.", "Not that I've noticed.", "Haven't seen them today."));
            n.lastOutcome = "asked " + w.name + " about " + sought.name + ": " + (covers ? "nothing (covering for them)" : "not seen");
            g.state.commit("WitnessQuestioned", n.id, w.id, n.homeJurisdiction, 0, sought.name + ": nothing");
            return true;
        }
        float ago = (float) (g.time - seenAt);
        String when = ago < 30 ? "just now" : ago < 120 ? "a minute or two ago" : ago < 420 ? "a few minutes ago" : "a while back";
        float dx = sx - w.x, dz = sz - w.z;
        String where = Math.hypot(dx, dz) < 4 ? "right around here" : "off to the " + compass(dx, dz) + " of here";
        ai.say(w, pickLine(w, "Aye, " + when + " - " + where + ".", "I saw them " + when + ", " + where + ".", "Yes! " + where.substring(0, 1).toUpperCase() + where.substring(1) + ", " + when + "."));
        boolean newer = seenAt > h.clueAt;
        if (newer) {
            h.clue(sx, sz, 0, 0, seenAt);
            h.patience = Math.max(h.patience, (float) (g.time - seenAt) + 60);
            h.source = w.id;
        }
        n.lastOutcome = "asked " + w.name + " about " + sought.name + ": seen " + when + " at (" + (int) sx + ", " + (int) sz + ")" + (newer ? " - searching there" : " - no newer than what I had");
        g.state.commit("WitnessQuestioned", n.id, w.id, n.homeJurisdiction, 0, String.format("%s seen %.0fs ago at (%.0f, %.0f)%s", sought.name, ago, sx, sz, newer ? ", search moved" : ""));
        return true;
    }

    /**
     * A bounty hunter on the trail lays snares where the quarry was last placed and on the way they were going: two at
     * most, not one on top of another, each a short kneel's work.
     */
    private boolean setSnare(Npc n, Hunt h) {
        if (n.snaresSet >= 2 || g.time < n.snareAgain || g.dungeons == null) return false;
        if (Math.hypot(n.x - h.x, n.z - h.z) > 9 || !standable(n.x, n.z)) return false;
        for (var t : g.dungeons.traps) if (!t.removed && Math.hypot(t.x - n.x, t.z - n.z) < 5) return false;
        n.snaresSet++;
        n.snareAgain = g.time + 18;
        float fx = (float) -Math.sin(n.yaw), fz = (float) -Math.cos(n.yaw);
        float sx = n.x + fx * 0.9f, sz = n.z + fz * 0.9f;
        if (!standable(sx, sz)) { sx = n.x; sz = n.z; }
        g.dungeons.snare(n, (float) Math.floor(sx) + 0.5f, (float) Math.floor(sz) + 0.5f);
        ai.desire(n, AiSystem.A_HOLD);
        h.lookUntil = g.time + 1.4; h.lookYaw = n.yaw;
        if (n.distTo(g.player) < 12) ai.say(n, pickLine(n, "That'll hold them.", "Let's see them walk through this.", "One more for luck."));
        n.lastOutcome = "set a snare (" + n.snaresSet + ") near where " + g.nameOf(h.target) + " was last placed";
        return true;
    }

    private static String compass(float dx, float dz) {
        // the devmap's north is -z
        double a = Math.toDegrees(Math.atan2(dx, -dz));
        String[] names = {"north", "north-east", "east", "south-east", "south", "south-west", "west", "north-west"};
        return names[(int) Math.floorMod(Math.round(a / 45), 8)];
    }

    private String pickLine(Npc n, String... lines) { return lines[(int) Math.floorMod(n.id * 31 + (long) (g.time / 7), lines.length)]; }

    /** Whether someone could stand at a spot: open cells at body height, ground beneath, inside the map. */
    private boolean standable(float x, float z) {
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z), f = DevMap.FLOOR;
        if (bx < 1 || bz < 1 || bx >= DevMap.SX - 1 || bz >= DevMap.SZ - 1) return false;
        return !g.vox.solid(bx, f, bz) && !g.vox.solid(bx, f + 1, bz) && g.vox.solid(bx, f - 1, bz);
    }

    /** The next spot to check: inside the area, reachable, out of its present sight, by cover, not already being checked. */
    private boolean pickSearchSpot(Npc n, Hunt h) {
        float r = h.radius(g.time);
        float sp = (float) Math.hypot(h.vx, h.vz);
        // the search drifts the way they were going
        float cx = h.x + (sp > 0.3f ? h.vx / sp * Math.min(r * 0.5f, 5) : 0), cz = h.z + (sp > 0.3f ? h.vz / sp * Math.min(r * 0.5f, 5) : 0);
        java.util.Random rnd = new java.util.Random(n.id * 31 + h.checked * 7919L + (long) (h.startedAt * 10));
        float bestScore = -1e9f, bx = 0, bz = 0;
        float ey = n.eyeY();
        for (int i = 0; i < 18; i++) {
            double a = rnd.nextDouble() * Math.PI * 2, d = Math.sqrt(rnd.nextDouble()) * r;
            float x = cx + (float) (Math.cos(a) * d), z = cz + (float) (Math.sin(a) * d);
            if (!standable(x, z)) continue;
            x = (float) Math.floor(x) + 0.5f; z = (float) Math.floor(z) + 0.5f;
            float score = 0;
            // somewhere it cannot see from here is somewhere they could be
            if (!g.vox.sight(n.x, ey, n.z, x, DevMap.FLOOR + 1.0f, z)) score += 3;
            // cover: walls, bushes and trees round the spot
            int cover = 0;
            for (int ox = -1; ox <= 1; ox++) for (int oz = -1; oz <= 1; oz++) if ((ox != 0 || oz != 0) && g.vox.solid((int) Math.floor(x) + ox, DevMap.FLOOR, (int) Math.floor(z) + oz)) cover++;
            score += Math.min(4, cover) * 0.6f;
            score -= (float) Math.hypot(x - n.x, z - n.z) * 0.06f;
            // not where another searcher is already looking
            for (Npc o : g.npcs) if (o != n && o.hunt != null && o.hunt.target == h.target && o.hunt.hasPoint && Math.hypot(o.hunt.px - x, o.hunt.pz - z) < 4) score -= 3;
            if (Math.hypot(x - n.x, z - n.z) < 2) score -= 2;
            if (score > bestScore) { bestScore = score; bx = x; bz = z; }
        }
        if (bestScore <= -1e8f) return false;
        h.px = bx; h.pz = bz; h.hasPoint = true;
        return true;
    }

    public void resetJob(Npc n) { if(n.job!=null)n.job.cancel();n.job=null; if (g.repairs != null) g.repairs.release(n); }

    /**
     * Mending (gamefixes: broken fences, windows and the like put back by the people whose trade it is): the next thing
     * broken that this person can mend (Repairs.claim) - walk to it, work at it a few seconds with the hands busy at the
     * spot, and put it back. Returns false when there is nothing for them to mend.
     */
    boolean mend(Npc n) {
        if(n.working!=null && n.working.action.equals("repair")){ai.desire(n,AiSystem.A_HOLD);return true;}
        var d = g.repairs.claim(n);
        if (d == null) return false;
        float[] at = g.repairs.standSpot(n, d);
        if (at == null) { g.repairs.abandon(n); return false; }
        if (!goTo(n, at[0], at[1], false, 0.9f)) { n.lastOutcome = "on the way to mend the broken " + (d.glass() ? "window" : "timber") + " at (" + d.x() + ", " + d.z() + ")"; return true; }
        n.wantYaw = (float) Math.atan2(-(d.x() + 0.5f - n.x), -(d.z() + 0.5f - n.z));
        ai.desire(n, AiSystem.A_HOLD);
        n.lootStarted=g.time;n.lootUntil=g.time+3.5;n.reachX=d.x()+0.5f;n.reachY=d.y()+0.5f;n.reachZ=d.z()+0.5f;
        var site=new rpg.interact.WorldObject(d.key()^(1L<<62),"repair_site","Damaged building",n.reachX,n.reachY,n.reachZ) {
            public java.util.List<rpg.interact.Option> options(Rpg world,Actor actor){return java.util.List.of();}
            public rpg.interact.Result perform(Rpg world,Actor actor,String action,long request){return rpg.interact.Result.no("Repair work is already assigned.");}
            public boolean exists(){return g.repairs.contains(d.key());}
        };
        g.interact.begin(new rpg.interact.Work(n,site,"repair","Repairing",3.5f,false,g.time,()->{
            boolean done=g.repairs.mend(n,d);n.lootUntil=-99;n.nextThink=0;
            if(!done)g.repairs.abandon(n);
            n.lastOutcome=done?"completed a repair":"repair interrupted";
            return done?rpg.interact.Result.ok():rpg.interact.Result.no("The repair is no longer available.");
        }));
        n.lastOutcome="mending the "+(d.glass()?"window":"timber");
        return true;
    }

    private void followCaster(Npc n) {
        Actor owner = g.actor(n.summonOwner);
        if (owner == null || !owner.alive) { ai.desire(n, AiSystem.A_HOLD); return; }
        float side = (n.id % 2 == 0 ? 1 : -1) * 1.2f;
        float x = owner.x + (float)Math.cos(owner.yaw) * side + (float)Math.sin(owner.yaw) * 1.8f;
        float z = owner.z - (float)Math.sin(owner.yaw) * side + (float)Math.cos(owner.yaw) * 1.8f;
        if (!Waypoints.clear(g.vox, owner.x, owner.z, x, z)) { x = owner.x; z = owner.z; }
        if (goTo(n, x, z, n.distTo(owner) > 5, 1.1f)) n.wantYaw = owner.yaw;
    }

    private void work(Npc n,float dt) {
        if(n.profession!=null && n.summonOwner==0) { jobs.tick(n,dt); return; }
        switch (n.role) {
            case WORKER -> { if (!mend(n)) gatherAndSell(n, Items.LOGS, "tree"); }
            case VILLAGER -> gatherAndSell(n, Items.BERRIES, "berry_bush");
            case CARTER -> {
                float[] dest = g.econ.carterStep(n);
                if (dest == null) { if (goTo(n, n.postX, n.postZ, false, 1.2f)) holdFacingPlayer(n); }
                else goTo(n, dest[0], dest[1], false, 1.0f);
            }
            case FARMER -> tend(n);
            // an herbalist gathers herbs (and berries when the herb patches are bare)
            case HEALER -> { if (n.count(Items.GUAM) > 0 || ai.nearestNode(n, "herb_patch") != null) gatherAndSell(n, Items.GUAM, "herb_patch"); else gatherAndSell(n, Items.BERRIES, "berry_bush"); }
            case HUNTER -> huntSmallGame(n,dt);
            case BEGGAR -> {
                Npc neighbor = ai.partnerFor(n);
                if (neighbor != null && n.hunger > 0.3f) { if (goTo(n, neighbor.x, neighbor.z, false, 1.7f)) { holdFacingPlayer(n); if (g.time - n.lastDialogAt > 30) { ai.say(n, "A little bread, if you can spare it?"); n.lastDialogAt = g.time; } } }
                else if (goTo(n, n.postX, n.postZ, false, 1)) ai.desire(n, AiSystem.A_IDLE);
            }
            case SCHOLAR -> { Npc neighbor = ai.partnerFor(n); if (neighbor != null && goTo(n, neighbor.x, neighbor.z, false, 2)) { ai.share(n, neighbor); ai.desire(n, AiSystem.A_IDLE); } }
            case PRIEST -> { if (goTo(n, n.postX, n.postZ, false, 1)) ai.desire(n, AiSystem.A_IDLE); }
            default -> { if (goTo(n, n.postX, n.postZ, false, 0.8f)) holdFacingPlayer(n); }
        }
    }

    /**
     * The livestock farmer's round (GDD 3.9, Livestock Farmer): feed from her chest carried to the trough when it runs
     * low, the hens' eggs carried from the nest boxes to the chest, and between times a walk among her animals. Every
     * step moves real items; a chest with no feed left means a hungry herd (and no breeding) until more is bought.
     */
    private void tend(Npc n) {
        var trough = g.objects.trough;
        var chest = g.objects.farmChest;
        if (trough == null || chest == null) { if (goTo(n, n.postX, n.postZ, false, 0.8f)) holdFacingPlayer(n); return; }
        if (n.has(Items.ANIMAL_FEED, 1)) {
            if (goTo(n, trough.x, trough.z - 1.2f, false, 0.9f)) {
                int moved = 0;
                for (ItemStack s : n.takeItems(Items.ANIMAL_FEED, n.count(Items.ANIMAL_FEED))) {
                    ItemStack in = new ItemStack(s.def, s.amount, n.id, false, trough.id);
                    if (trough.contents.add(in)) moved += s.amount; else n.store(s);
                }
                n.wantYaw = 0;
                n.lastOutcome = "filled the trough (" + moved + " feed)";
                n.lastTroughFill = g.time;
                g.state.commit("TroughFilled", n.id, trough.id, DevMap.jurisdiction(trough.x, trough.z), 0, moved + " animal feed");
            }
            return;
        }
        // a full chest of eggs: the surplus goes to Store A, and pays for the feed
        var shop = g.econ.shopA;
        if (n.has(Items.EGG, 1) && chest.contents.count(Items.EGG) >= 6 && g.econ.merchant(shop) != null && g.econ.tradeRefusal(g.econ.merchant(shop), n) == null) {
            float[] c = g.econ.counterSpot(shop);
            if (goTo(n, c[0], c[1], false, 1.2f)) {
                int sold = g.econ.sellToShop(n, shop, Items.EGG, n.count(Items.EGG));
                n.lastOutcome = "sold " + sold + " eggs at Store A";
                if (sold == 0) { for (ItemStack s : n.takeItems(Items.EGG, n.count(Items.EGG))) if (!chest.contents.add(s)) n.store(s); }
            }
            return;
        }
        // running short of feed: buy more at Store A while the purse allows
        int feedHeld = chest.contents.count(Items.ANIMAL_FEED) + trough.contents.count(Items.ANIMAL_FEED);
        int price = g.econ.buyPrice(shop, Items.ANIMAL_FEED);
        if (feedHeld < 3 && shop.stock.count(Items.ANIMAL_FEED) > 0 && n.has(Items.COINS, price) && g.econ.merchant(shop) != null && g.econ.tradeRefusal(g.econ.merchant(shop), n) == null) {
            float[] c = g.econ.counterSpot(shop);
            if (goTo(n, c[0], c[1], false, 1.2f)) {
                int k = Math.max(1, Math.min(3, n.count(Items.COINS) / Math.max(1, price)));
                Result r = g.econ.buy(n, shop, Items.ANIMAL_FEED, k, g.state.newRequestId());
                n.lastOutcome = "bought " + k + " feed at Store A -> " + r.status() + (r.reason() == null ? "" : " (" + r.reason() + ")");
            }
            return;
        }
        if (n.has(Items.EGG, 1)) {
            if (goTo(n, chest.x, chest.z - 1.1f, false, 0.9f)) {
                int moved = 0;
                for (ItemStack s : n.takeItems(Items.EGG, n.count(Items.EGG))) { if (chest.contents.add(s)) moved += s.amount; else n.store(s); }
                n.lastOutcome = "put " + moved + " eggs in the chest";
            }
            return;
        }
        int feed = trough.contents.count(Items.ANIMAL_FEED);
        int boxEggs = 0;
        for (var box : g.objects.nestBoxes) boxEggs += box.contents.count(Items.EGG);
        // an empty trough comes first; otherwise eggs piling up in the boxes are gathered before the trough is topped up
        // ...but boxes filling up are not left while a hungry herd empties the trough as fast as she fills it
        boolean eggsWaiting = boxEggs >= 6 && g.time - n.lastTroughFill < 90;
        if ((feed == 0 && !eggsWaiting || feed < 3 && boxEggs < 4) && chest.contents.count(Items.ANIMAL_FEED) > 0) {
            if (goTo(n, chest.x, chest.z - 1.1f, false, 0.9f)) {
                for (ItemStack s : chest.contents.remove(Items.ANIMAL_FEED, Math.min(3, chest.contents.count(Items.ANIMAL_FEED)))) if (!n.store(s)) chest.contents.add(s);
                n.lastOutcome = "took feed from the chest";
            }
            return;
        }
        for (var box : g.objects.nestBoxes) {
            if (box.contents.count(Items.EGG) == 0) continue;
            if (goTo(n, box.x, box.z - 1.0f, false, 0.8f)) {
                int got = 0;
                for (ItemStack s : box.contents.remove(Items.EGG, box.contents.count(Items.EGG))) { if (n.store(s)) got += s.amount; else box.contents.add(s); }
                n.lastOutcome = "collected eggs";
                if (got > 0) g.state.commit("EggsCollected", n.id, box.id, DevMap.jurisdiction(box.x, box.z), 0, got + " eggs");
            }
            return;
        }
        if (feed == 0) n.lastOutcome = "out of feed: the stock goes hungry";
        // the round: from one of her animals to the next
        rpg.animal.Animal visit = null;
        int k = (int) ((g.time / 20) % 997);
        List<rpg.animal.Animal> mine = new ArrayList<>();
        for (rpg.animal.Animal a : g.animals.all()) if (a.alive && a.loaded && a.owner == n.id) mine.add(a);
        if (!mine.isEmpty()) visit = mine.get(k % mine.size());
        if (visit == null) { if (goTo(n, n.postX, n.postZ, false, 0.8f)) holdFacingPlayer(n); return; }
        if (goTo(n, visit.x, visit.z, false, 2.4f)) {
            n.wantYaw = (float) Math.atan2(-(visit.x - n.x), -(visit.z - n.z));
            n.lastOutcome = "checked on a " + visit.kind().toLowerCase();
        }
    }

    void huntSmallGame(Npc hunter,float dt) {
        if(g.interact.of(hunter)!=null) { ai.desire(hunter,AiSystem.A_HOLD);return; }
        if(hunter.main()==null || hunter.main().family!=ItemDef.Family.BOW) {
            for(var grid:hunter.grids())for(ItemStack item:grid.stacks())if(item.def.family==ItemDef.Family.BOW) {g.items.equipFirst(hunter,item.def,ItemDef.Slot.MAIN,g.state.newRequestId());break;}
            if(hunter.main()==null || hunter.main().family!=ItemDef.Family.BOW){gatherAndSell(hunter,Items.BERRIES,"berry_bush");return;}
        }
        if(hunter.has(Items.RAW_MEAT,1)) { gatherAndSell(hunter,Items.RAW_MEAT,"no gathering node");return; }
        rpg.animal.Animal quarry=null;float nearest=20;
        for(var animal:g.animals.all()) {
            if(animal.owner!=0 || animal.baby || animal.butchered || !animal.loaded || animal.species!=rpg.animal.Species.RABBIT || animal.distTo(hunter)>=nearest)continue;
            if(!g.vox.sight(hunter.x,hunter.eyeY(),hunter.z,animal.x,animal.y+animal.height*0.5f,animal.z))continue;
            quarry=animal;nearest=animal.distTo(hunter);
        }
        if(quarry==null) { gatherAndSell(hunter,Items.BERRIES,"berry_bush");return; }
        hunter.wantYaw=(float)Math.atan2(-(quarry.x-hunter.x),-(quarry.z-hunter.z));
        if(quarry.alive) {
            if(hunter.eq.get(ItemDef.Slot.AMMO)==null) { gatherAndSell(hunter,Items.BERRIES,"berry_bush");return; }
            if(hunter.distTo(quarry)>12) { goTo(hunter,quarry.x,quarry.z,false,10);return; }
            ai.desire(hunter,AiSystem.A_HOLD);
            if(g.time<hunter.busyUntil)return;
            float flight=hunter.distTo(quarry)/52;
            float x=quarry.x+quarry.vx*flight,z=quarry.z+quarry.vz*flight,d=(float)Math.hypot(x-hunter.x,z-hunter.z);
            hunter.wantYaw=(float)Math.atan2(-(x-hunter.x),-(z-hunter.z));
            hunter.pitch=(float)Math.atan2(quarry.chestY()+4.9f*flight*flight-hunter.eyeY(),Math.max(0.1f,d));
            hunter.cs.draw=Math.min(1,hunter.cs.draw+dt/0.8f);
            if(hunter.cs.draw>=1 && Math.abs(Actor.wrap(hunter.yaw-hunter.wantYaw))<0.3f && g.combat.shoot(hunter,hunter.wantYaw,hunter.pitch,1)) { hunter.cs.draw=0;hunter.busyUntil=g.time+0.7; }
        }
        else if(goTo(hunter,quarry.x,quarry.z,false,1.2f) && !g.interact.request(hunter,quarry,"butcher").rejected()) { hunter.lootStarted=g.time;hunter.lootUntil=g.time+3.2;hunter.reachX=quarry.x;hunter.reachY=quarry.y;hunter.reachZ=quarry.z; }
    }

    private void holdFacingPlayer(Npc n) {
        if (n.id == Ids.TELLER) {
            float[] customer = g.bank.customerSpot();
            n.wantYaw = (float) Math.atan2(-(customer[0] - n.x), -(customer[1] - n.z));
            ai.target(n, 0); ai.desire(n, AiSystem.A_IDLE); return;
        }
        ai.target(n, g.player.body);
        ai.desire(n, Math.hypot(g.player.x - n.x, g.player.z - n.z) < 6 ? AiSystem.A_HOLD : AiSystem.A_IDLE);
    }

    /** Gathers with the same node rules the player has, then sells the haul to Store A (stock and wages move). */
    private void gatherAndSell(Npc n, ItemDef product, String nodeDef) {
        var w = g.interact.of(n);
        int have = n.count(product);
        Node node = w != null && w.target instanceof Node nd ? nd : ai.nearestNode(n, nodeDef);
        if (have >= 5 || (node == null && have > 0)) {
            // to their own town's market (DevPalace's people to the market hall, not across the map to Store A)
            var shop = g.econ.localShop(n);
            float[] c = g.econ.counterSpot(shop);
            if (goTo(n, c[0], c[1], false, 1.0f)) {
                int sold = g.econ.sellToShop(n, shop, product, have);
                n.lastOutcome = "sold " + sold + " " + product.name.toLowerCase() + " to " + shop.name;
            }
            return;
        }
        if (node == null) { n.lastOutcome = "no " + nodeDef + " available; waiting"; ai.desire(n, AiSystem.A_WANDER); return; }
        if (w != null) { ai.desire(n, AiSystem.A_IDLE); return; }
        // the tool in the hand (a woodcutter's axe is wielded, not carried, to fell a tree)
        if (node.kind == Node.Kind.TREE && (n.main() == null || n.main().toolSkill != rpg.skill.Skill.WOODCUTTING || n.main().twoHanded())) {
            ItemDef axe = null;
            for (var grid : n.grids()) for (ItemStack s : grid.stacks()) if (axe == null && s.def.toolSkill == rpg.skill.Skill.WOODCUTTING && !s.def.twoHanded()) axe = s.def;
            if (axe != null) g.items.equipFirst(n, axe, ItemDef.Slot.MAIN, g.state.newRequestId());
        }
        if (node.kind == Node.Kind.TREE && n.cs.busy()) { ai.desire(n, AiSystem.A_IDLE); n.nextThink = Math.min(n.nextThink, g.time + 0.15); return; }
        float[] spot = ai.standSpot(n, node);
        if (goTo(n, spot[0], spot[1], false, 0.6f)) {
            if (node.kind == Node.Kind.TREE) n.nextThink = Math.min(n.nextThink, g.time + 0.25);
            n.wantYaw = (float) Math.atan2(-(node.x - n.x), -(node.z - n.z));
            Result r = g.interact.request(n, node, "gather");
            n.lastOutcome = "gather " + node.name + " #" + node.id + " -> " + r.status() + (r.reason() == null ? "" : " (" + r.reason() + ")");
            if (r.rejected()) n.remember(new Memory("NODE_EMPTY", node.id, 0, null, g.state.minutes, 1, "personal", 0, 0, node.name), 24);
        }
    }

    private void patrol(Npc n) {
        float[][] round = DevMap.patrol(n.homeJurisdiction);
        float[] p = round[n.patrolIndex % round.length];
        if (goTo(n, p[0], p[1], false, 1.0f)) {
            if (n.busyUntil < g.time - 3) n.busyUntil = g.time + 2;
            if (g.time > n.busyUntil) { n.patrolIndex++; n.busyUntil = g.time - 10; n.lastOutcome = "reached patrol point " + (n.patrolIndex - 1) % round.length; }
        }
    }

    /**
     * Food in hand is eaten where they stand; with none but money, they buy bread at Store A (real stock, real coins);
     * with neither, they walk to the well (or their post) to drink.
     */
    private void eat(Npc n) {
        ItemStack food = null;
        for (var grid : n.grids()) for (ItemStack s : grid.stacks()) if (food == null && s.def.heal > 0) food = s;
        var shop = g.econ.shopA;
        Npc ada = g.econ.merchant(shop);
        // (only while the shopkeeper is at the counter to sell it: nobody stands at an empty counter asking)
        boolean canBuy = food == null && n.role != Npc.Role.MERCHANT && ada != null && ada.alive && shop.stock.count(Items.BREAD) > 0
                && Math.hypot(ada.x - g.econ.counterSpot(shop)[0], ada.z - g.econ.counterSpot(shop)[1]) < g.cfg.interactRange + 1
                && n.has(Items.COINS, g.econ.buyPrice(shop, Items.BREAD)) && n.homeJurisdiction.equals(DevMap.ALPHA) && g.econ.tradeRefusal(ada, n) == null;
        if (canBuy) {
            float[] c = g.econ.counterSpot(shop);
            if (!goTo(n, c[0], c[1], false, 1.2f)) return;
            Result r = g.econ.buy(n, shop, Items.BREAD, 1, g.state.newRequestId());
            n.lastOutcome = "bought bread at Store A -> " + r.status() + (r.reason() == null ? "" : " (" + r.reason() + ")");
            if (r.rejected()) { n.hunger = Math.max(0, n.hunger - 0.2f); return; }
            for (var grid : n.grids()) for (ItemStack s : grid.stacks()) if (food == null && s.def.heal > 0) food = s;
        }
        if (food == null) {
            float wx = DevMap.WELL[0] + 0.5f, wz = DevMap.WELL[1] + 2.2f;
            // (DevTown has no well: a drink at the post, on the side away from the cells)
            if (n.homeJurisdiction.equals(DevMap.BETA)) { wx = n.postX - 1; wz = n.postZ - 1.5f; }
            // outlaws keep out of town: they make do at their own camp
            boolean outlaw = n.role == Npc.Role.BANDIT || n.role == Npc.Role.BRUTE;
            if (outlaw) { wx = n.spawnX; wz = n.spawnZ; }
            if (!goTo(n, wx, wz, false, outlaw ? 2f : 1.2f)) return;
            if (outlaw) { n.lastOutcome = "foraged at camp"; n.hunger = 0; return; }
        } else ai.desire(n, AiSystem.A_IDLE);
        if (food != null) {
            n.takeItems(food.def, 1);
            g.state.burn(food.def, 1);
            g.state.commit("NpcAte", n.id, 0, DevMap.jurisdiction(n.x, n.z), 0, food.def.name);
            n.lastOutcome = "ate " + food.def.name.toLowerCase();
        } else n.lastOutcome = "drank from the well";
        n.hunger = 0;
    }

    private void socialize(Npc n) {
        Npc p = ai.partnerFor(n);
        if (p == null) { n.lastOutcome = "no one to talk to"; n.social = 0.3f; return; }
        if (n.socialPartner != p.id || n.socialSide == 0) n.socialSide = n.x > p.x ? 1.4f : -1.4f;
        n.socialPartner = p.id;
        if (!goTo(n, p.x + n.socialSide, p.z, false, 0.9f)) {
            if (g.time < n.navPauseUntil) {
                n.social = 0.3f;
                n.socialPartner = 0;
                n.nextThink = 0;
                n.lastOutcome = "postponed a conversation; no safe route to the partner";
            }
            return;
        }
        if (n.chatUntil < g.time - 1) { n.chatUntil = g.time + 4; g.ai.say(n, ai.smallTalk(n, p)); }
        n.wantYaw = (float) Math.atan2(-(p.x - n.x), -(p.z - n.z));
        if (g.time >= n.chatUntil) {
            int told = ai.share(n, p) + ai.share(p, n);
            n.social = 0; p.social = 0;
            n.lastOutcome = "talked with " + p.name + (told > 0 ? "; exchanged " + told + " pieces of news" : "");
            n.chatUntil = -9;
        }
    }

    private void report(Npc n) {
        Memory m = unreportedCrime(n);
        if (m == null) return;
        Npc lawman = ai.lawmanFor(m.jurisdiction);
        if (lawman == null) { n.lastOutcome = "no lawman in " + m.jurisdiction; return; }
        // the lawman saw it himself: his own word is the report, made where he stands
        if (lawman == n) { Result r = g.law.report(n, m, n); n.lastOutcome = "recorded " + m.kind.toLowerCase() + " he saw himself -> " + r.status(); return; }
        if (goTo(n, lawman.x + 1.3f, lawman.z + 1.3f, true, 1.6f)) {
            Result r = g.law.report(n, m, lawman);
            n.lastOutcome = "reported " + m.kind.toLowerCase() + " by " + g.nameOf(m.subject) + " to " + lawman.name + " -> " + r.status() + (r.reason() == null ? "" : " (" + r.reason() + ")");
        }
    }

    private void steal(Npc n) {
        var stall = g.objects.stall;
        float sx = stall.x, sz = stall.z + 1.4f;
        if (ai.stealOpportunity(n) < 0.3f) { ai.desire(n, AiSystem.A_WANDER); return; }
        if (!goTo(n, sx, sz, false, 0.9f)) return;
        if (g.interact.of(n) == null) {
            Result r = g.interact.request(n, stall, "steal");
            n.lastOutcome = "steal from stall -> " + r.status() + (r.reason() == null ? "" : " (" + r.reason() + ")");
        }
    }

    // ------------------------------------------------------------------ fighting
    /** Tactical choices each moment: close in, strike when the opening is there, guard or dodge a telegraphed blow, back off to recover, retreat when beaten. */
    private void fight(Npc n, float dt) {
        Actor t = g.actor(n.fightTarget);
        if (t == null || !t.alive || (t == g.player && !g.player.alive) || ai.threatFor(n) != t) { n.fightTarget = 0; n.goal = Goal.IDLE; n.nextThink = 0; g.combat.setBlock(n, false); return; }
        float d = n.distTo(t);
        if (n.skills.level(Skill.MAGIC)>=10 && !n.cs.busy() && n.cs.castCooldown<=0 && ai.canSee(n,t)) {
            var spell=g.magic.combatSpell(n,t);
            float yaw=(float)Math.atan2(-(t.x-n.x),-(t.z-n.z)), pitch=(float)Math.atan2(t.chestY()-n.eyeY(),Math.max(0.1f,d));
            if(spell!=null && g.combat.cast(n,spell,yaw,pitch)) { ai.desire(n,AiSystem.A_HOLD); return; }
        }
        if (n.hostileToPlayer && Math.hypot(n.x - n.spawnX, n.z - n.spawnZ) > 26 && !(n.cs.lastAttacker == t.id && g.time - n.cs.lastHurt < 12)) { n.fightTarget = 0; n.goal = Goal.RETURN; n.lastOutcome = "gave up the chase (leash)"; ai.giveUp(n, t.id); return; }
        // out of sight for a heartbeat: it heads for where it last saw them, not for where they really are
        boolean visible = ai.canSee(n, t);
        if (!visible) {
            if (n.tactic != null && tactics.move(n, t, false)) return;
            float[] lk = ai.lastKnown(n, t);
            if (lk != null) {
                g.combat.setBlock(n, false);
                n.wantYaw = (float) Math.atan2(-(lk[0] - n.x), -(lk[1] - n.z));
                ai.combatGoal(n, lk[0], lk[1]);
                n.lastOutcome = "after them - where they were last seen";
                return;
            }
            g.combat.setBlock(n, false); ai.desire(n, AiSystem.A_HOLD); return;
        }
        n.wantYaw = (float) Math.atan2(-(t.x - n.x), -(t.z - n.z));
        boolean facing = Math.abs(Actor.wrap(n.wantYaw - n.yaw)) < 0.45f;   // a blow is thrown only at what the body faces
        float targetY = t.chestY();
        if (t.reacting && t.ragdoll != 0) {
            var body = g.lastBodies.ragdoll(t.ragdoll);
            if (body != null) targetY = body.comY;
        }
        n.pitch = (float) Math.atan2(targetY - n.chestY(), Math.max(0.3f, d));
        ai.target(n, t.body);
        // the law talks before it fights: a wanted player is first told to halt, unless they resist or struck first
        // (in a lockdown there is no talking: the watch attacks on sight)
        if (t == g.player && (n.role == Npc.Role.GUARD || n.role == Npc.Role.LAWMAN) && g.law.bounty(t.id, n.homeJurisdiction) > 0
                && !g.law.lockdown(n.homeJurisdiction)
                && !g.law.resisting(t.id,n.homeJurisdiction) && !(n.cs.lastAttacker == t.id && g.time - n.cs.lastHurt < 12)) {
            g.combat.setBlock(n, false);
            if (d > 2.4f) { goTo(n, t.x, t.y, t.z, true, 2.2f); n.lastOutcome = "closing in to arrest"; return; }
            ai.desire(n, AiSystem.A_HOLD);
            g.law.halt(n, t);
            n.lastOutcome = "ordering the player to halt";
            return;
        }
        if (d > 3.5f && tactics.move(n, t, true)) return;
        ItemDef main = n.main();
        if (main != null && (main.family == ItemDef.Family.BOW || main.family == ItemDef.Family.CROSSBOW) && n.eq.get(ItemDef.Slot.AMMO) == null) {
            ItemStack weapon = n.eq.get(ItemDef.Slot.MAIN);
            if (n.canStore(weapon)) { n.eq.set(ItemDef.Slot.MAIN, null); n.store(weapon); }
            else { n.eq.set(ItemDef.Slot.MAIN, null); g.objects.drop(weapon, n.x, n.y + 0.1f, n.z); }
            for (var grid : n.grids()) for (ItemStack spare : grid.stacks()) if (n.main() == null && spare.def.equippable() && spare.def.slot == ItemDef.Slot.MAIN && spare.def.reach > 0 && spare.def.family != ItemDef.Family.BOW && spare.def.family != ItemDef.Family.CROSSBOW)
                g.items.equipFirst(n, spare.def, ItemDef.Slot.MAIN, g.state.newRequestId());
            main = n.main(); n.cs.draw = 0; n.cs.aim = 0;
        }
        boolean archer = main != null && (main.family == ItemDef.Family.BOW || main.family == ItemDef.Family.CROSSBOW);
        float reach = main == null || archer ? 0.6f : main.reach;
        boolean hasGuard = main == null && n.off() == null || n.off() != null && n.off().family == ItemDef.Family.SHIELD || main != null && main.guard > 0 && !archer;
        // what the opponent is doing, as an eye would read it
        boolean telegraph = t.cs.phase == Actor.Combat.Phase.WINDUP && d < 3.2f;
        float reaction = Math.max(0.05f, 0.22f - n.skills.level(rpg.skill.Skill.DEFENSE) * 0.002f);
        boolean readIt = (telegraph && t.cs.t >= reaction) || t.cs.phase == Actor.Combat.Phase.ACTIVE && d < 3.2f;
        boolean opening = t.cs.phase == Actor.Combat.Phase.RECOVER || t.cs.staggered > 0;
        String choice;
        if (archer) {
            n.pitch=(float)Math.atan2(targetY-n.eyeY(),Math.max(0.3f,d));
            Actor between = d < 5 ? null : g.combat.inLineOfFire(n, t);
            if (d < 5) { choice = "back off to shooting range"; backOff(n, t, 9); }
            else if (between != null && !ai.hostile(n, between)) {
                // someone of no quarrel stands in the line: hold the shot and step aside to a clear one (the side away
                // from them), rather than loose through them
                choice = "step aside for a clear shot";
                n.cs.draw = Math.max(0, n.cs.draw - dt);
                float lx = t.z - n.z, lz = -(t.x - n.x), ll = (float) Math.max(0.01, Math.hypot(lx, lz));
                float side = (between.x - n.x) * lx + (between.z - n.z) * lz > 0 ? -1 : 1;
                goTo(n, n.x + lx / ll * side * 2.5f, n.z + lz / ll * side * 2.5f, false, 0.4f);
            }
            else if (n.cs.draw > 0 || (!n.cs.busy() && g.vox.sight(n.x, n.eyeY(), n.z, t.x, t.chestY(), t.z))) {
                choice = "draw and shoot";
                ai.desire(n, AiSystem.A_HOLD);
                if (main.family == ItemDef.Family.CROSSBOW) {
                    if (n.cs.reload <= 0) g.combat.shootCrossbow(n, n.yaw, n.pitch + d * 0.001f);
                    n.cs.aim = 1;
                } else n.cs.draw = Math.min(1, n.cs.draw + dt / 1.1f);
                if (n.cs.draw >= 1) { g.combat.shoot(n, n.yaw, n.pitch + d * 0.004f, 1); n.cs.draw = 0; }
            } else { choice = "find a line of sight"; goTo(n, t.x, t.y, t.z, false, 6); }
        } else if (t instanceof rpg.monster.Monster big && big.kind.terror() >= 20 && n.hp < n.maxHp() * 0.4f && d < 9) {
            // against a terror, the hurt fall back out of its reach and let the others hold it
            choice = "fall back, hurt";
            g.combat.setBlock(n, hasGuard);
            backOff(n, t, 12);
        } else if (t instanceof rpg.monster.Monster big && big.kind.terror() >= 20 && d < 11 && inFront(big, n, 0.8f) && !(d <= reach + 0.55f && opening)) {
            // never square in front of it - its jaws and its breath are there: round to its flank (each to the side
            // they are nearer), and strike from there
            choice = "round to its flank, out of its breath";
            g.combat.setBlock(n, hasGuard && d < 5);
            float side = Actor.wrap((float) Math.atan2(-(n.x - big.x), -(n.z - big.z)) - big.yaw) >= 0 ? 1 : -1;
            float ang = big.yaw + side * 1.9f, r = Math.max(reach + big.radius + 0.4f, 2.5f);
            ai.combatGoal(n, big.x - (float) Math.sin(ang) * r, big.z - (float) Math.cos(ang) * r);
        } else if (t instanceof rpg.monster.Monster fm && fm.kind.flies() && t.chestY() - n.y > 1.9f + Math.max(0, reach - 1) * 0.5f) {
            // (gamefixes: people stood swinging at bats in the air) a flyer up out of reach: no blow is thrown at the air -
            // guard, keep it in view a step off, and strike when it dives; the unarmed and the townsfolk give it up and run
            n.flyerWait += dt;
            boolean fighter = main != null && main.damage > 0 || n.role == Npc.Role.GUARD || n.role == Npc.Role.LAWMAN || n.role == Npc.Role.BRUTE;
            if (!fighter && n.flyerWait > 6) {
                choice = "run from the flyer";
                g.combat.setBlock(n, false);
                ai.target(n, t.body); ai.desire(n, AiSystem.A_FLEE);
            } else {
                choice = "wait for it to dive";
                g.combat.setBlock(n, hasGuard);
                if (d < 1.2f) backOff(n, t, 2.2f); else ai.desire(n, AiSystem.A_HOLD);
            }
        } else if (n.hp < n.maxHp() * 0.45f && hasFood(n) && d > 3.5f && !n.cs.busy()) {
            choice = "eat to recover";
            eatInFight(n);
        } else if (d < Math.min(0.55f, reach * 0.5f) && !n.cs.busy()) {
            // pressed chest to chest: no blow can be thrown from there - a step back makes room for one (to well past
            // reach: the way back in passes through striking distance; a target nearer than a cell would not move him)
            choice = "make room";
            g.combat.setBlock(n, false);
            backOff(n, t, Math.max(1.6f, reach + 0.9f));
        } else if (readIt && hasGuard && n.stamina > 15 && (t.cs.move != Move.HEAVY || n.off() != null)) {
            choice = "guard against " + t.cs.move;
            g.combat.setBlock(n, true);
            ai.desire(n, AiSystem.A_HOLD);
        } else if (readIt && n.stamina > g.cfg.staminaDodge + 5) {
            choice = "dodge the " + t.cs.move;
            g.combat.setBlock(n, false);
            float ax = n.x - t.x, az = n.z - t.z, l = Math.max(0.01f, (float) Math.hypot(ax, az));
            float side = (n.id & 1) == 0 ? 1 : -1;
            g.combat.dodge(n, ax / l * 0.6f - az / l * side, az / l * 0.6f + ax / l * side);
        } else if (facing && (d <= reach + 0.55f && !n.cs.busy() && n.stamina > 12 && !t.cs.blocking || opening && d <= reach + 0.55f && !n.cs.busy())) {
            boolean heavy = opening || t.cs.blocking || n.role == Npc.Role.BRUTE && (n.id + (long) (g.time * 2)) % 3 == 0;
            choice = heavy ? "heavy blow into the opening" : "quick strike";
            n.flyerWait = 0;
            g.combat.setBlock(n, false);
            g.combat.attack(n, false, heavy ? Move.HEAVY : CombatSystem.lightMove(n, false));
            ai.desire(n, AiSystem.A_HOLD);
        } else if (n.stamina < 22) {
            choice = "back off to catch breath";
            g.combat.setBlock(n, hasGuard && d < 2.5f);
            backOff(n, t, 3.4f);
        } else if (t.cs.blocking && d <= reach + 1.2f) {
            choice = "circle the guard";
            g.combat.setBlock(n, false);
            circle(n, t, reach + 0.3f);
        } else {
            choice = "close the distance";
            g.combat.setBlock(n, n.off() != null && n.off().family == ItemDef.Family.SHIELD && d < reach + 2 && !n.cs.busy());
            // (to striking distance - within reach, but not in the target's own cell: a goal there walks into them)
            float stand = Math.min(reach + 0.3f, Math.max(0.9f, reach * 0.8f));
            ai.closeIn(n, t.x + (n.x - t.x) / Math.max(0.1f, d) * stand, t.z + (n.z - t.z) / Math.max(0.1f, d) * stand);
        }
        if (!choice.equals(n.lastOutcome)) n.lastOutcome = choice;
    }

    /** Whether n stands within a cone (half-angle in radians) ahead of a body. */
    private static boolean inFront(Actor body, Actor n, float half) {
        float to = (float) Math.atan2(-(n.x - body.x), -(n.z - body.z));
        return Math.abs(Actor.wrap(to - body.yaw)) < half;
    }

    private void backOff(Npc n, Actor t, float range) {
        float ax = n.x - t.x, az = n.z - t.z, l = Math.max(0.01f, (float) Math.hypot(ax, az));
        ai.combatGoal(n, t.x + ax / l * range, t.z + az / l * range);
    }

    private void circle(Npc n, Actor t, float r) {
        double ang = Math.atan2(n.z - t.z, n.x - t.x) + ((n.id & 1) == 0 ? 0.6 : -0.6);
        ai.combatGoal(n, t.x + (float) Math.cos(ang) * r, t.z + (float) Math.sin(ang) * r);
    }

    private static boolean hasFood(Npc n) { for (ItemStack s : n.inv.stacks()) if (s.def.heal > 0) return true; return false; }

    private void eatInFight(Npc n) {
        for (ItemStack s : n.inv.stacks()) if (s.def.heal > 0) {
            n.takeItems(s.def, 1);
            g.state.burn(s.def, 1);
            n.hp = Math.min(n.maxHp(), n.hp + s.def.heal);
            n.cs.castCooldown = 1.2f;
            g.state.commit("NpcAte", n.id, 0, null, 0, s.def.name + " mid-fight");
            if (g.sfx != null) g.sfx.play("eat", n.x, n.y + 1.5f, n.z, 0.5f);
            return;
        }
    }

    /** Whether this NPC is a witness-capable bystander (not the culprit, not a hostile). */
    static boolean bystander(Npc n) { return n.role != Npc.Role.BANDIT && n.role != Npc.Role.BRUTE && n.id != Ids.PLAYER; }
}
