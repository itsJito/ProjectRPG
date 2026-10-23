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
    public enum Goal { IDLE, LOOK, WANDER, WORK, EAT, SLEEP, SOCIALIZE, PATROL, REPORT_CRIME, INVESTIGATE, SEARCH, FIGHT, FLEE, STEAL, RETURN, HELD, COLLECT, LOOT, FENCE, HIDE, WARN, WATCH }

    private final Rpg g;
    private final AiSystem ai;

    Brain(Rpg g, AiSystem ai) { this.g = g; this.ai = ai; }

    record Score(Goal goal, float value, String reason) {}

    // ------------------------------------------------------------------ deciding
    public void think(Npc n) {
        n.nextThink = g.time + (n.goal == Goal.FIGHT ? 0.35 : 0.8) + (n.id % 5) * 0.03;
        n.perceived = perceive(n);
        // blood, bodies and limbs in sight (Gore): each noticed once, and reacted to
        if (g.gore != null) g.gore.perceive(n);
        List<Score> s = new ArrayList<>();
        double hour = g.state.hourOfDay();
        boolean night = hour >= 22 || hour < 6;
        boolean civilian = !n.combatant();

        if (n.arrestedUntil > g.state.minutes) s.add(new Score(Goal.HELD, 2f, "in custody until minute " + (int) n.arrestedUntil));
        // maimed: whoever has lost a limb tries only to get away (and, legless, can barely move)
        if (g.gore != null && g.gore.wounds.containsKey(n.id) && g.gore.wounds.get(n.id).any() && n.alive)
            s.add(new Score(Goal.FLEE, 0.97f, "maimed; getting away"));
        // threats and duties of force
        Actor threat = ai.threatFor(n);
        ai.keepHunt(n);
        // a foe lost from sight, or the culprit behind something found: search where they were last placed
        if (n.hunt != null && threat == null) {
            Hunt h = n.hunt;
            String who = h.target == 0 ? "whoever did it" : g.nameOf(h.target);
            s.add(new Score(Goal.SEARCH, n.combatant() ? 0.86f : 0.72f, String.format("searching for %s near (%.0f, %.0f), %.0fs since the last clue", who, h.x, h.z, g.time - h.clueAt)));
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
        // a guard who has heard the player is violent - but has no warrant (a rumor is not a report) - keeps an eye on them
        if (threat == null && (n.role == Npc.Role.GUARD || n.role == Npc.Role.LAWMAN) && g.player.alive && g.law.bounty(g.player.id, n.homeJurisdiction) <= 0) {
            Memory bad = n.crimeKnown(g.player.id);
            boolean violent = bad != null && !bad.settled && (bad.kind.equals("MURDER") || bad.kind.equals("ASSAULT")) && bad.confidence >= 0.45f;
            if (violent && n.distTo(g.player) < 16 && ai.canSee(n, g.player) && (g.time < n.watchUntil || g.time > n.watchUntil + 120)) {
                if (g.time > n.watchUntil) n.watchUntil = g.time + 25;
                if (n.role != Npc.Role.LAWMAN || Math.hypot(g.player.x - n.postX, g.player.z - n.postZ) < 12)
                    s.add(new Score(Goal.WATCH, 0.62f, "heard " + g.player.name + " is dangerous (" + bad.kind.toLowerCase() + ", " + bad.source + "); keeping an eye on them"));
            }
        }
        if (threat != null) {
            boolean weak = n.hp < n.maxHp() * (civilian ? 0.95f : 0.25f);
            if (civilian || weak) s.add(new Score(Goal.FLEE, weak && !civilian ? 0.95f : 0.85f, (weak ? "badly hurt" : "not a fighter") + "; fleeing " + threat.name));
            else s.add(new Score(Goal.FIGHT, 0.9f, "engaging " + threat.name + (n.hostileToPlayer ? " (hostile)" : "")));
        }
        // a crime it saw with its own eyes, not yet reported, in a jurisdiction with a lawman
        Memory unreported = unreportedCrime(n);
        // (with the culprit standing right there, the report waits until it is safe to make)
        Actor culprit = unreported == null ? null : g.actor(unreported.subject);
        boolean exposed = culprit != null && culprit.alive && n.distTo(culprit) < 15 && ai.canSee(n, culprit) && !n.combatant();
        if (unreported != null && exposed) s.add(new Score(Goal.HIDE, 0.82f, "keeping out of " + culprit.name + "'s sight before reporting"));
        if (unreported != null && !exposed && ai.lawmanFor(unreported.jurisdiction) != null && unreported.subject != n.id)
            s.add(new Score(Goal.REPORT_CRIME, 0.78f, "saw " + g.nameOf(unreported.subject) + " commit " + unreported.kind.toLowerCase() + "; must tell the law"));
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
            if (w.level >= Stealth.INVESTIGATING) s.add(new Score(Goal.INVESTIGATE, nightNow ? 0.8f : n.combatant() ? 0.66f : 0.46f, (heard ? "heard something at " : "glimpsed someone at ") + where + "; deciding what to do"));
            else s.add(new Score(Goal.LOOK, 0.56f, (heard ? "a sound from " : "a movement at ") + where));
        }
        // needs
        if (n.hunger > 0.45f) s.add(new Score(Goal.EAT, 0.25f + n.hunger * 0.6f, String.format("hunger %.0f%%", n.hunger * 100)));
        if (night && n.role != Npc.Role.GUARD && !n.combatant() && n.role != Npc.Role.THIEF) s.add(new Score(Goal.SLEEP, 0.7f, String.format("night (%02d:00)", (int) hour)));
        else if (n.fatigue > 0.8f) s.add(new Score(Goal.SLEEP, 0.55f, String.format("fatigue %.0f%%", n.fatigue * 100)));
        if (n.social > 0.55f && !n.combatant() && ai.partnerFor(n) != null) s.add(new Score(Goal.SOCIALIZE, 0.2f + n.social * 0.45f, String.format("lonely (%.0f%%), %s nearby", n.social * 100, ai.partnerFor(n).name)));
        // duties
        switch (n.role) {
            case GUARD -> s.add(new Score(Goal.PATROL, 0.5f, "on patrol duty"));
            case WORKER, VILLAGER, CARTER, MERCHANT, LAWMAN, QUEST_GIVER, CONTACT, FARMER, UNDERTAKER, FENCE -> {
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
                if (n.role == Npc.Role.GUARD) s.add(new Score(Goal.COLLECT, 0.62f, String.format("a death in town: carrying the grave at (%.0f, %.0f) to the undertaker", grave.x, grave.z)));
                else s.add(new Score(Goal.LOOT, n.role == Npc.Role.THIEF ? 0.74f : 0.6f, String.format("an unguarded grave at (%.0f, %.0f), full of someone's gear", grave.x, grave.z)));
            }
        }
        s.add(new Score(Goal.WANDER, 0.15f, "nothing pressing"));
        s.sort(Comparator.comparing(Score::value).reversed());
        // a little inertia: keep the current goal unless another beats it clearly
        Score best = s.get(0);
        // Safety decisions interrupt routine goals as soon as they outscore them.
        boolean urgent = best.goal == Goal.FIGHT || best.goal == Goal.FLEE || best.goal == Goal.INVESTIGATE;
        if (!urgent) for (Score c : s) if (c.goal == n.goal && c.value >= best.value - 0.06f) { best = c; break; }
        n.scores.clear();
        for (int i = 0; i < Math.min(6, s.size()); i++) n.scores.add(String.format("%.2f %s - %s", s.get(i).value, s.get(i).goal, s.get(i).reason));
        if (best.goal != n.goal) {
            g.state.commit("NpcGoal", n.id, 0, DevMap.jurisdiction(n.x, n.z), 0, n.goal + " -> " + best.goal + " (" + best.reason + ")");
            n.goal = best.goal;
            n.goalSince = g.time;
            n.hasGoalPos = false;
            n.legs = null;
            g.interact.cancel(n, "changed goal");
            if (best.goal == Goal.FIGHT && threat != null) n.fightTarget = threat.id;
        }
        n.goalReason = best.reason;
    }

    private String workText(Npc n) {
        return switch (n.role) {
            case WORKER -> "cut logs, sell them to Store A"; case VILLAGER -> "pick berries, sell them to Store A"; case CARTER -> "carry Store A's surplus to Store B";
            case MERCHANT -> "mind the shop"; case LAWMAN -> "keep the chancery"; case UNDERTAKER -> "keep the graveyard"; case FENCE -> "wait for a seller"; case FARMER -> "tend the stock: feed in the trough, eggs to the chest";
            default -> "keep to their post";
        };
    }

    private Memory unreportedCrime(Npc n) {
        for (Memory m : n.memories) if (m.crime() && !m.settled && m.witnessed() && !m.reported && m.subject != n.id) return m;
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
        n.hunger = Math.min(1, n.hunger + dt / 900f);
        n.social = Math.min(1, n.social + (n.combatant() ? 0 : dt / 300f));
        n.fatigue = n.goal == Goal.SLEEP ? Math.max(0, n.fatigue - dt / 60f) : Math.min(1, n.fatigue + dt / 2400f);
        n.sneaking = n.goal == Goal.STEAL;   // a thief at work creeps
        if (n.cs.staggered > 0) { ai.desire(n, AiSystem.A_IDLE); return; }
        // a leg gone: it cannot walk off (it lies where it fell, calling out)
        if (g.gore != null && g.gore.legs(n) < 0.2f) { ai.desire(n, AiSystem.A_IDLE); n.sneaking = true; if (n.speech == null && (n.id + (long) g.time) % 7 == 0) g.ai.say(n, "Help me..."); return; }
        switch (n.goal) {
            case HELD -> ai.desire(n, AiSystem.A_IDLE);
            case WANDER -> ai.desire(n, AiSystem.A_WANDER);
            case IDLE -> ai.desire(n, AiSystem.A_IDLE);
            case RETURN -> goTo(n, n.spawnX, n.spawnZ, false, 2f);
            case WORK -> work(n);
            case PATROL -> patrol(n);
            case EAT -> eat(n);
            case SLEEP -> { if (goTo(n, n.homeX, n.homeZ, false, 1.2f)) ai.desire(n, AiSystem.A_IDLE); }
            case SOCIALIZE -> socialize(n);
            case REPORT_CRIME -> report(n);
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
                // shadowing them at a few paces, eyes on them; a word of warning first
                Actor p = g.player;
                n.wantYaw = (float) Math.atan2(-(p.x - n.x), -(p.z - n.z));
                if (n.distTo(p) > 5) goTo(n, p.x, p.z, false, 4.5f); else ai.desire(n, AiSystem.A_HOLD);
                if (g.time - n.goalSince < 0.5 && n.speech == null) g.ai.say(n, "I've heard about you. Don't try anything.");
                n.lastOutcome = "watching " + p.name;
            }
            case WARN -> warn(n);
            case FIGHT -> fight(n, dt);
            case STEAL -> steal(n);
            case COLLECT -> collect(n);
            case FENCE -> fence(n);
            case LOOT -> loot(n);
        }
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
        boolean guard = n.role == Npc.Role.GUARD, crook = n.role == Npc.Role.THIEF || n.role == Npc.Role.BANDIT || n.role == Npc.Role.BRUTE;
        if (!guard && !crook) return null;
        rpg.objects.Container best = null;
        float bd = Float.MAX_VALUE;
        for (rpg.objects.Container c : g.undertaker.graves()) {
            if (c.contents.isEmpty()) continue;
            float d = (float) Math.hypot(c.x - n.x, c.z - n.z);
            if (guard) {
                if (!c.secured || (c.heldBy != 0 && c.heldBy != n.id) || !DevMap.jurisdiction(c.x, c.z).equals(n.homeJurisdiction)) continue;
                if (c.heldBy == 0 && g.time - (c.expiresAt - rpg.law.Undertaker.GRAVE_SECONDS) < WORD_REACHES_WATCH) continue;
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
                if (g.time - n.arrivedAt > 2.5) {
                    c.heldBy = n.id;
                    g.state.commit("GraveCollected", n.id, c.id, DevMap.jurisdiction(c.x, c.z), 0, "carried by " + n.name);
                    n.lastOutcome = "gathered up the grave; taking it to the undertaker";
                }
            }
            return;
        }
        if (goTo(n, DevMap.UNDERTAKER_POST[0] + 0.6f, DevMap.UNDERTAKER_POST[1] - 1.2f, false, 1.4f)) {
            g.undertaker.toCustody(c, "a guard brought it in");
            n.lastOutcome = "handed the grave to the undertaker";
            n.nextThink = 0;
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
        if (!goTo(n, c.x, c.z, n.role != Npc.Role.THIEF, 1.0f)) return;
        if (n.arrivedAt < n.goalSince) n.arrivedAt = g.time;
        n.wantYaw = (float) Math.atan2(-(c.x - n.x), -(c.z - n.z));
        if (g.time - n.lastLootAt < 1.3) return;
        n.lastLootAt = g.time;
        int best = -1, bv = -1;
        for (int i = 0; i < c.contents.size(); i++) { ItemStack s = c.contents.get(i); if (s != null && n.canStore(s) && s.value() > bv) { bv = s.value(); best = i; } }
        if (best < 0 || n.lootTaken >= 4) {
            n.lastOutcome = n.lootTaken > 0 ? "took " + n.lootTaken + " thing(s) from the grave" : "nothing in the grave I can carry";
            n.graveSeen = n.lootTaken >= 4 || best < 0 ? -c.id : n.graveSeen;
            n.lootTaken = 0;
            n.nextThink = 0;
            return;
        }
        ItemStack s = c.contents.take(best, c.contents.get(best).amount);
        s.markStolen(c.owner);
        n.store(s);
        n.lootTaken++;
        long req = g.state.newRequestId();
        var ev = g.state.commit(req, "GraveLooted", n.id, c.id, DevMap.jurisdiction(c.x, c.z), 0, s + " taken by " + n.name);
        g.law.theft(n, c.owner, s, c, ev.id());
        if (n.lootTaken == 1) g.ai.say(n, n.role == Npc.Role.THIEF ? "Finders keepers." : "Won't be needing this, will you?");
    }

    /**
     * Heads somewhere through the engine's routes; true once there. A long trip is walked leg by leg through the map's
     * waypoints (each leg a short search for the engine); the navigation target the inspector shows is the current leg's.
     */
    boolean goTo(Npc n, float x, float z, boolean run, float within) {
        if (Math.hypot(n.x - x, n.z - z) <= within) { ai.desire(n, AiSystem.A_HOLD); n.legs = null; return true; }
        if (n.legs == null || Math.hypot(n.legsX - x, n.legsZ - z) > 0.5) { n.legs = Waypoints.route(g.vox, n.x, n.z, x, z); n.legAt = 0; n.legsX = x; n.legsZ = z; }
        float tx = x, tz = z;
        while (n.legAt < n.legs.length) {
            float[] p = Waypoints.point(n.legs[n.legAt]);
            if (Math.hypot(n.x - p[0], n.z - p[1]) < 1.8) { n.legAt++; continue; }
            tx = p[0]; tz = p[1];
            break;
        }
        if (!n.hasGoalPos || Math.hypot(n.gx - tx, n.gz - tz) > 0.4) {
            n.gx = tx; n.gz = tz; n.hasGoalPos = true;
            g.game.commands().agentGoal(n.agent, tx, DevMap.FLOOR, tz);
        }
        ai.desire(n, run ? AiSystem.A_RUN : AiSystem.A_GOTO);
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
            case GUARD -> v.plan = Investigation.Plan.SEARCH;
            case LAWMAN -> v.plan = Math.hypot(w.x - n.postX, w.z - n.postZ) > 18 && ai.guardFor(n) != null ? Investigation.Plan.FETCH_HELP : Investigation.Plan.CHECK;
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
                    if (n.arrivedAt < v.stageAt) n.arrivedAt = g.time;
                    n.wantYaw = n.yaw + (float) Math.sin((g.time - n.arrivedAt) * 2.4) * 1.2f;
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
                if (t > 12 && g.time - v.lastClue > 8) { n.sneaking = false; n.hideSet = false; v.stage(Investigation.Stage.DONE, g.time); }
            }
            case DONE -> {
                if (w != null) w.settledAt = g.time;
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
        double hour = g.state.hourOfDay();
        boolean night = hour >= 21 || hour < 5.5;
        if ((night || n.fearOf == g.player.id) && n.homeX != 0 && Math.hypot(n.homeX - n.x, n.homeZ - n.z) < 40) return new float[]{n.homeX, n.homeZ};
        java.util.Random rnd = new java.util.Random(n.id * 977 + (long) g.time);
        float bestScore = -1e9f, bx = n.x, bz = n.z;
        for (int i = 0; i < 24; i++) {
            double a = rnd.nextDouble() * Math.PI * 2, d = 5 + rnd.nextDouble() * 9;
            float x = n.x + (float) (Math.cos(a) * d), z = n.z + (float) (Math.sin(a) * d);
            if (!standable(x, z)) continue;
            x = (float) Math.floor(x) + 0.5f; z = (float) Math.floor(z) + 0.5f;
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
                if (!standable(x, z)) continue;
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

    /** Keeping hidden from what they fled (crouched where they cannot be seen), until it has been out of sight a while. */
    private void hide(Npc n) {
        if (!n.hideSet) { float[] h = hideSpot(n, n.fearX, n.fearZ); n.hideX = h[0]; n.hideZ = h[1]; n.hideSet = true; }
        n.sneaking = true;
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

    /** A beast that kills: a hunter, or a bear (people keep away from one whatever it is doing). */
    static boolean predator(rpg.animal.Animal an) { return !an.baby && (an.species.diet == rpg.animal.Species.Diet.HUNTER || an.species == rpg.animal.Species.BROWN_BEAR); }

    /** What this NPC fears and is close enough to run from (not to fight): a known killer of theirs, or a beast. */
    private Actor fearFor(Npc n) {
        if (n.combatant()) return null;
        Actor p = g.player;
        if (p.alive && n.distTo(p) < 12 && ai.rumors.afraid(n) && ai.canSee(n, p)) return p;
        if (g.animals != null) for (rpg.animal.Animal an : g.animals.all())
            if (an.alive && an.loaded && (an.dangerous() || predator(an) && n.distTo(an) < 11) && n.distTo(an) < 16 && ai.canSee(n, an)) return an;
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
        if (!h.reachedLkp) {
            float lead = (float) Math.min(2.5, Math.hypot(h.vx, h.vz) * 0.7);
            float sp = (float) Math.hypot(h.vx, h.vz);
            float lx = h.x + (sp > 0.3f ? h.vx / sp * lead : 0), lz = h.z + (sp > 0.3f ? h.vz / sp * lead : 0);
            if (!standable(lx, lz)) { lx = h.x; lz = h.z; }
            if (Math.hypot(lx - n.x, lz - n.z) < 5) n.wantYaw = (float) Math.atan2(-(lx - n.x), -(lz - n.z));
            if (goTo(n, lx, lz, true, 1.3f)) {
                h.reachedLkp = true;
                h.lookUntil = g.time + 1.6;
                n.lastOutcome = "reached the last known location; nobody there";
            }
            return;
        }
        if (g.time < h.lookUntil) {
            ai.desire(n, AiSystem.A_HOLD);
            float base = h.hasPoint ? n.yaw : (float) Math.atan2(-h.vx, -h.vz);
            n.wantYaw = n.yaw + (float) Math.sin((h.lookUntil - g.time) * 2.6) * 1.2f;
            if (Float.isNaN(base)) n.wantYaw = n.yaw;
            return;
        }
        if (!h.hasPoint && !pickSearchSpot(n, h)) { h.lookUntil = g.time + 1.0; return; }
        if (goTo(n, h.px, h.pz, false, 0.9f)) {
            h.hasPoint = false;
            h.checked++;
            h.lookUntil = g.time + 1.1 + (n.id % 3) * 0.3;
            n.lastOutcome = "checked a spot (" + h.checked + "); nobody";
        }
    }

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

    private void work(Npc n) {
        switch (n.role) {
            case WORKER -> gatherAndSell(n, Items.LOGS, "tree");
            case VILLAGER -> gatherAndSell(n, Items.BERRIES, "berry_bush");
            case CARTER -> {
                float[] dest = g.econ.carterStep(n);
                if (dest == null) { if (goTo(n, n.postX, n.postZ, false, 1.2f)) holdFacingPlayer(n); }
                else goTo(n, dest[0], dest[1], false, 1.0f);
            }
            case FARMER -> tend(n);
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
        if (goTo(n, visit.x + 1.4f, visit.z, false, 1.0f)) {
            n.wantYaw = (float) Math.atan2(-(visit.x - n.x), -(visit.z - n.z));
            n.lastOutcome = "checked on a " + visit.kind().toLowerCase();
        }
    }

    private void holdFacingPlayer(Npc n) {
        ai.target(n, g.player.body);
        ai.desire(n, Math.hypot(g.player.x - n.x, g.player.z - n.z) < 6 ? AiSystem.A_HOLD : AiSystem.A_IDLE);
    }

    /** Gathers with the same node rules the player has, then sells the haul to Store A (stock and wages move). */
    private void gatherAndSell(Npc n, ItemDef product, String nodeDef) {
        var w = g.interact.of(n);
        int have = n.count(product);
        Node node = w != null && w.target instanceof Node nd ? nd : ai.nearestNode(n, nodeDef);
        if (have >= 5 || (node == null && have > 0)) {
            float[] c = g.econ.counterSpot(g.econ.shopA);
            if (goTo(n, c[0], c[1], false, 1.0f)) {
                int sold = g.econ.sellToShop(n, g.econ.shopA, product, have);
                n.lastOutcome = "sold " + sold + " " + product.name.toLowerCase() + " to Store A";
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
        float[] p = DevMap.PATROL[n.patrolIndex % DevMap.PATROL.length];
        if (goTo(n, p[0], p[1], false, 1.0f)) {
            if (n.busyUntil < g.time - 3) n.busyUntil = g.time + 2;
            if (g.time > n.busyUntil) { n.patrolIndex++; n.busyUntil = g.time - 10; n.lastOutcome = "reached patrol point " + (n.patrolIndex - 1) % DevMap.PATROL.length; }
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
        boolean canBuy = food == null && n.role != Npc.Role.MERCHANT && ada != null && ada.alive && shop.stock.count(Items.BREAD) > 0
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
            if (n.homeJurisdiction.equals(DevMap.BETA)) { wx = n.postX + 1; wz = n.postZ + 1; }
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
        n.socialPartner = p.id;
        if (!goTo(n, p.x + (n.x > p.x ? 1.4f : -1.4f), p.z, false, 0.9f)) return;
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
        if (n.hostileToPlayer && Math.hypot(n.x - n.spawnX, n.z - n.spawnZ) > 26) { n.fightTarget = 0; n.goal = Goal.RETURN; n.lastOutcome = "gave up the chase (leash)"; return; }
        // out of sight for a heartbeat: it heads for where it last saw them, not for where they really are
        if (!ai.canSee(n, t)) {
            float[] lk = ai.lastKnown(n, t);
            if (lk != null) {
                g.combat.setBlock(n, false);
                n.wantYaw = (float) Math.atan2(-(lk[0] - n.x), -(lk[1] - n.z));
                ai.combatGoal(n, lk[0], lk[1]);
                n.lastOutcome = "after them - where they were last seen";
                return;
            }
        }
        n.wantYaw = (float) Math.atan2(-(t.x - n.x), -(t.z - n.z));
        boolean facing = Math.abs(Actor.wrap(n.wantYaw - n.yaw)) < 0.45f;   // a blow is thrown only at what the body faces
        n.pitch = (float) Math.atan2(t.chestY() - n.chestY(), Math.max(0.3f, d));
        ai.target(n, t.body);
        // the law talks before it fights: a wanted player is first told to halt, unless they resist or struck first
        if (t == g.player && (n.role == Npc.Role.GUARD || n.role == Npc.Role.LAWMAN) && g.law.bounty(t.id, n.homeJurisdiction) > 0
                && !g.law.resisting(t.id) && !(n.cs.lastAttacker == t.id && g.time - n.cs.lastHurt < 12)) {
            g.combat.setBlock(n, false);
            if (d > 2.4f) { goTo(n, t.x, t.z, true, 2.2f); n.lastOutcome = "closing in to arrest"; return; }
            ai.desire(n, AiSystem.A_HOLD);
            g.law.halt(n, t);
            n.lastOutcome = "ordering the player to halt";
            return;
        }
        ItemDef main = n.main();
        boolean archer = main != null && main.family == ItemDef.Family.BOW;
        float reach = main == null || archer ? 0.6f : main.reach;
        boolean hasGuard = n.off() != null && n.off().family == ItemDef.Family.SHIELD || main != null && main.guard > 0 && !archer;
        // what the opponent is doing, as an eye would read it
        boolean telegraph = t.cs.phase == Actor.Combat.Phase.WINDUP && d < 3.2f;
        float reaction = Math.max(0.05f, 0.22f - n.skills.level(rpg.skill.Skill.DEFENSE) * 0.002f);
        boolean readIt = telegraph && t.cs.t >= reaction;
        boolean opening = t.cs.phase == Actor.Combat.Phase.RECOVER || t.cs.staggered > 0;
        String choice;
        if (archer) {
            if (d < 5) { choice = "back off to shooting range"; backOff(n, t, 9); }
            else if (n.cs.draw > 0 || (!n.cs.busy() && g.vox.sight(n.x, n.eyeY(), n.z, t.x, t.chestY(), t.z))) {
                choice = "draw and shoot";
                ai.desire(n, AiSystem.A_HOLD);
                n.cs.draw = Math.min(1, n.cs.draw + dt / 1.1f);
                if (n.cs.draw >= 1) { g.combat.shoot(n, n.yaw, n.pitch + d * 0.004f, 1); n.cs.draw = 0; }
            } else { choice = "find a line of sight"; goTo(n, t.x, t.z, false, 6); }
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
            g.combat.setBlock(n, false);
            // (to striking distance - within reach, but not in the target's own cell: a goal there walks into them)
            float stand = Math.min(reach + 0.3f, Math.max(0.9f, reach * 0.8f));
            ai.combatGoal(n, t.x + (n.x - t.x) / Math.max(0.1f, d) * stand, t.z + (n.z - t.z) / Math.max(0.1f, d) * stand);
        }
        if (!choice.equals(n.lastOutcome)) n.lastOutcome = choice;
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
