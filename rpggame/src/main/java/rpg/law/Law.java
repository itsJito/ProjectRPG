package rpg.law;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import rpg.Chat;
import rpg.Ids;
import rpg.Rpg;
import rpg.actor.Actor;
import rpg.ai.Memory;
import rpg.ai.Npc;
import rpg.interact.Result;
import rpg.interact.WorldObject;
import rpg.item.ItemStack;
import rpg.item.Items;
import rpg.ui.Dialog;
import rpg.world.DevMap;

/**
 * Crime and law (GDD 3.5, 3.8 invariant 3). A crime is committed as an event; only NPCs that actually see it at that
 * moment gain evidence; nothing changes in law until a witness travels to a lawman of the jurisdiction where it
 * happened and reports it - then, and only then, that jurisdiction's bounty rises (theft: half the stolen value; the
 * ceiling applies). Stop the witness and there is no bounty, though the theft still happened. Bounties are separate per
 * jurisdiction; being wanted blocks trade there and sets the guards on you.
 */
public final class Law {
    private final Rpg g;
    /** jurisdiction -> offender -> bounty. */
    private final Map<String, Map<Long, Integer>> bounties = new HashMap<>();
    public final List<String> reportsLog = new ArrayList<>();
    private final Map<Long, Double> haltedAt = new HashMap<>();
    private final Set<Long> resisting = new HashSet<>();
    private final Map<Long, Double> assaultAt = new HashMap<>();
    /** When each offender was last arrested (game time): every grievance of the law's from before it is settled by the sentence. */
    private final Map<Long, Double> settledAt = new HashMap<>();
    public long crimes, witnessed, reports, rejectedReports, arrests;
    /** Crime events already reported: a second witness of the same crime adds testimony, not a second bounty. */
    private final Set<Long> reportedCrimes = new HashSet<>();

    public Law(Rpg g) { this.g = g; }

    public int bounty(long offender, String jurisdiction) {
        Map<Long, Integer> m = bounties.get(jurisdiction);
        return m == null ? 0 : m.getOrDefault(offender, 0);
    }
    public boolean wantedAnywhere(long offender) { for (var m : bounties.values()) if (m.getOrDefault(offender, 0) > 0) return true; return false; }
    public Map<String, Map<Long, Integer>> all() { return bounties; }

    // ------------------------------------------------------------------ crimes and witnesses
    public void theft(Actor thief, long owner, ItemStack stolen, WorldObject where, long cause) {
        crime(thief, "THEFT", owner, stolen.value(), where, cause);
    }

    /** Whether a victim of the player's is innocent: not an outlaw, not hostile, not someone who struck them in the last moments. */
    private boolean innocent(long victim) {
        Actor v = g.actor(victim);
        if (v == null) return victim != 0;
        if (v instanceof Npc n && (n.role == Npc.Role.BANDIT || n.role == Npc.Role.BRUTE || n.hostileToPlayer)) return false;
        if (v instanceof rpg.animal.Animal an && !an.species.domestic) return false;
        if (g.player.cs.lastAttacker == victim && g.time - g.player.cs.lastHurt < 15) return false;   // self-defence
        return true;
    }

    /** A crime happens: committed as a fact; whoever sees it now remembers it. Returns how many saw it. */
    public int crime(Actor culprit, String kind, long victim, int value, WorldObject where, long cause) {
        if (g.scripted) System.out.printf("      [crime] %s by %s against %s at (%.1f, %.1f), t %.1f%n", kind, culprit.name, g.nameOf(victim), culprit.x, culprit.z, g.time);
        crimes++;
        // Honor (GDD 3.5): the act weighs on the player's conscience whether or not anyone saw it - against innocents only
        // (not an outlaw, not someone who was attacking them); looting the dead is its own act, not a robbery as well
        if (culprit == g.player && g.honor != null) {
            if (where instanceof rpg.objects.Corpse body && kind.equals("THEFT")) { if (innocent(victim)) g.honor.lootedCorpse(body.id); }
            else g.honor.crime(kind, victim, value, innocent(victim));
        }
        String jur = where != null ? where.jurisdiction() : DevMap.jurisdiction(culprit.x, culprit.z);
        var ev = g.state.commit("CrimeCommitted", culprit.id, victim, jur, cause, kind + " value=" + value);
        List<Npc> seen = g.ai.witnesses(culprit);
        // under Disguise Self, the witnesses saw a stranger: the crime is known, the culprit is not (no bounty comes of it)
        long who = culprit.disguiseT > 0 ? 0 : culprit.id;
        // under Friends, a friend saw nothing they will tell
        seen.removeIf(w -> w.friendUntil > g.time && culprit == g.player);
        for (Npc w : seen) {
            w.remember(new Memory(kind, who, victim, jur, g.state.minutes, 1f, "witnessed", ev.id(), value, kind.toLowerCase() + " at " + (where == null ? "?" : where.name) + (who == 0 ? " (by a stranger)" : "")), 24);
            g.state.commit("CrimeWitnessed", w.id, culprit.id, jur, ev.id(), kind + " seen by " + w.name);
            witnessed++;
            g.ai.say(w, kind.equals("THEFT") ? "Thief!" : kind.equals("MURDER") ? "Murder!" : "Guards!");
            w.nextThink = 0;
            // violence is shouted to everyone in earshot at once (word of mouth, not a broadcast)
            g.ai.rumors.alarm(w, w.memories.get(w.memories.size() - 1));
        }
        if (culprit == g.player) {
            if (seen.isEmpty()) g.chat.add("[Crime] " + kind.toLowerCase() + " in " + jur + " - nobody saw you.", Chat.Kind.CRIME);
            else g.chat.add("[Crime] " + kind.toLowerCase() + " in " + jur + " was seen by " + names(seen) + ". No bounty unless they report it.", Chat.Kind.CRIME);
        }
        return seen.size();
    }

    private static String names(List<Npc> l) { StringBuilder b = new StringBuilder(); for (Npc n : l) b.append(b.length() > 0 ? ", " : "").append(n.name); return b.toString(); }

    /** Every hit: striking a peaceful NPC is assault (once in a while per victim, not per swing). */
    public void onHit(Actor a, Actor v, long cause) {
        if (a != g.player || !(v instanceof Npc n) || n.hostileToPlayer || n.fightTarget == a.id && bounty(a.id, n.homeJurisdiction) > 0) return;
        Double last = assaultAt.get(n.id);
        if (last != null && g.time - last < 10) return;
        assaultAt.put(n.id, g.time);
        crime(a, "ASSAULT", n.id, 0, null, cause);
    }

    public void onKilled(Actor victim, Actor killer, long cause) {
        if (killer == g.player && victim instanceof Npc n && !n.hostileToPlayer) crime(killer, "MURDER", n.id, 0, null, cause);
    }

    // ------------------------------------------------------------------ reports and bounties
    /** A witness, in person, tells a lawman (GDD 3.5). Validated: a real witness, the right jurisdiction, close enough, alive. */
    public Result report(Npc witness, Memory m, Npc lawman) {
        String why = null;
        if (m.settled) why = "the case was closed by the offender's death";
        else if (!m.witnessed()) why = "a rumor is not evidence";
        else if (m.reported) why = "already reported";
        else if (!lawman.alive || !witness.alive) why = "someone is dead";
        else if (!lawman.homeJurisdiction.equals(m.jurisdiction)) why = lawman.name + " has no authority in " + m.jurisdiction;
        else if (witness.distTo(lawman) > 2.8f) why = "too far from the lawman";
        if (why != null) { rejectedReports++; return Result.no(why); }
        if (m.subject == 0) {
            // found, not seen done: no one to put a bounty on - the force goes to search where it was found
            m.reported = true;
            reports++;
            var found = g.gore == null ? null : g.gore.evidence.stream().filter(e -> e.id == m.eventId).findFirst().orElse(null);
            float fx = found != null ? found.x : witness.x, fz = found != null ? found.z : witness.z;
            for (Npc n : g.npcs) if (n.alive && n.loaded && n.role == Npc.Role.GUARD && n.homeJurisdiction.equals(m.jurisdiction) && n.hunt == null) {
                rpg.ai.Hunt h = new rpg.ai.Hunt(0, witness.name + " reported " + m.detail, fx, fz, g.time, 60);
                n.hunt = h; n.nextThink = 0;
            }
            g.state.commit("EvidenceReported", witness.id, 0, m.jurisdiction, m.eventId, m.detail + " reported to " + lawman.name);
            g.ai.say(lawman, "I'll send the guards.");
            return Result.ok();
        }
        if (!reportedCrimes.add(m.eventId)) {
            m.reported = true;
            g.state.commit("TestimonyAdded", witness.id, m.subject, m.jurisdiction, m.eventId, m.kind + " already reported; " + witness.name + " confirms it");
            return Result.ok("already reported");
        }
        int add = switch (m.kind) {
            case "THEFT" -> Math.max(1, Math.round(m.value * g.cfg.theftBountyShare));
            case "LIVESTOCK" -> Math.max(10, m.value);
            case "VANDALISM" -> Math.max(5, m.value * 2);
            case "ASSAULT" -> g.cfg.assaultBounty;
            case "MURDER" -> g.cfg.murderBounty;
            default -> 20;
        };
        int before = bounty(m.subject, m.jurisdiction);
        int after = Math.min(g.cfg.bountyCap, before + add);
        bounties.computeIfAbsent(m.jurisdiction, k -> new HashMap<>()).put(m.subject, after);
        m.reported = true;
        reports++;
        var ev = g.state.commit("CrimeReported", witness.id, m.subject, m.jurisdiction, m.eventId, m.kind + " reported to " + lawman.name + "; bounty " + before + " -> " + after);
        g.state.commit("BountyChanged", lawman.id, m.subject, m.jurisdiction, ev.id(), before + " -> " + after);
        String line = String.format("%s reported %s's %s to %s: bounty in %s %d -> %d", witness.name, g.nameOf(m.subject), m.kind.toLowerCase(), lawman.name, m.jurisdiction, before, after);
        reportsLog.add(line);
        if (m.subject == g.player.id) g.chat.add("[Crime] " + line + ".", Chat.Kind.CRIME);
        g.ai.say(witness, "I saw " + g.nameOf(m.subject) + " - " + m.kind.toLowerCase() + "!");
        // everyone in the jurisdiction's force now knows to look
        for (Npc n : g.npcs) if ((n.role == Npc.Role.GUARD) && n.homeJurisdiction.equals(m.jurisdiction)) n.nextThink = 0;
        return Result.ok();
    }

    public Result payBounty(Actor a, Npc to, long req) {
        String jur = to.homeJurisdiction;
        int b = bounty(a.id, jur);
        if (b <= 0) return Result.no("You have no bounty in " + jur + ".");
        if (!a.has(Items.COINS, b)) return Result.no("You need " + b + " coins to pay your bounty.");
        a.takeItems(Items.COINS, b);
        to.store(new ItemStack(Items.COINS, b));
        bounties.get(jur).remove(a.id);
        resisting.remove(a.id);
        g.state.commit(req, "BountyPaid", a.id, to.id, jur, 0, b + " coins to " + to.name);
        if (a == g.player) { g.chat.add("You pay your bounty of " + b + " coins. You are no longer wanted in " + jur + ".", Chat.Kind.CRIME); g.sfx.ui("coins", 0.6f); g.honor.paidBounty(b); g.ai.rumors.deed("paid their bounty in full", to.id); }
        return Result.ok();
    }

    /** Whether a guard may simply attack (the player is first given the chance to pay or come quietly). */
    public boolean mayEngage(Npc guard, Actor a) { return true; }
    public boolean resisting(long id) { return resisting.contains(id); }

    /** Whether a blow struck (or a grudge formed) at a moment was settled by an arrest since (the law does not fight it again). */
    public boolean settled(long offender, double when) { Double at = settledAt.get(offender); return at != null && when <= at; }

    /** Whether someone is in custody right now: the law does not strike a prisoner. */
    public boolean inCustody(Actor a) { return a instanceof Npc n && n.arrestedUntil > g.state.minutes; }

    /** The guard catches up with the wanted player: Halt! Pay, come quietly, or resist. */
    public void halt(Npc guard, Actor p) {
        Double at = haltedAt.get(p.id);
        if (at != null && g.time - at < 14) {
            if (g.time - at > 10 && bounty(p.id, guard.homeJurisdiction) > 0) { resisting.add(p.id); g.ai.say(guard, "Then we do this the hard way!"); g.ui.closeDialog(); }
            return;
        }
        haltedAt.put(p.id, g.time);
        int b = bounty(p.id, guard.homeJurisdiction);
        g.ai.say(guard, "Halt!");
        Dialog d = new Dialog(guard.name, "Halt! You're wanted in " + guard.homeJurisdiction + " - a bounty of " + b + " coins. Pay it now, or come quietly.");
        d.npc = guard.id;
        d.choice("Pay " + b + " coins.", () -> { Result r = payBounty(p, guard, g.state.newRequestId()); if (r.rejected()) g.chat.warn(r.reason()); });
        d.choice("I'll come quietly.", () -> { g.honor.surrendered(guard.id); arrest(p, guard.homeJurisdiction, guard); });
        d.choice("Never! (resist arrest)", () -> { resisting.add(p.id); g.state.commit("ArrestResisted", p.id, guard.id, guard.homeJurisdiction, 0, ""); });
        if (p == g.player) g.ui.dialog(d);
    }

    /** A guard beating a wanted offender down arrests them instead of killing them. */
    public boolean arrestInsteadOfKill(Actor attacker, Actor victim) {
        if (!(attacker instanceof Npc n) || (n.role != Npc.Role.GUARD && n.role != Npc.Role.LAWMAN)) return false;
        if (bounty(victim.id, n.homeJurisdiction) <= 0) return false;
        arrest(victim, n.homeJurisdiction, n);
        return true;
    }

    /** Arrest: the bounty is settled by the sentence, stolen goods are confiscated back to their owners, the offender is held. */
    public void arrest(Actor a, String jur, Npc by) {
        arrests++;
        settledAt.put(a.id, g.time);
        int b = bounty(a.id, jur);
        if (bounties.containsKey(jur)) bounties.get(jur).remove(a.id);
        resisting.remove(a.id);
        int returned = 0;
        for (int i = 0; i < a.inv.size(); i++) {
            ItemStack s = a.inv.get(i);
            if (s == null || !s.stolen) continue;
            a.inv.set(i, null);
            Actor owner = g.actor(s.owner);
            ItemStack back = s.copy(s.amount);
            back.stolen = false;
            if (owner != null && owner.store(back)) returned += s.amount;
            else g.state.burn(s.def, s.amount);
        }
        Npc lawman = g.ai.lawmanFor(jur);
        float cx = lawman != null ? lawman.postX - 2 : a.x, cz = lawman != null ? lawman.postZ + 3 : a.z;
        g.teleport(a, cx, DevMap.FLOOR, cz);
        a.hp = Math.max(a.hp, a.maxHp() * 0.5f);
        a.cs.cancel();
        a.cs.blocking = false;
        haltedAt.remove(a.id);
        // the sentence settles every crime of theirs this jurisdiction knows of: nothing left to report, nothing to fight over
        for (Npc n : g.npcs) for (Memory m : n.memories)
            if (m.crime() && m.subject == a.id && jur.equals(m.jurisdiction) && !m.reported) { m.reported = true; reportedCrimes.add(m.eventId); }
        // the force stands down: no target, no grudge from blows struck during the arrest, no swing still in the air
        for (Npc n : g.npcs) {
            if (n.role != Npc.Role.GUARD && n.role != Npc.Role.LAWMAN && n != by) continue;
            boolean involved = n == by || n.fightTarget == a.id || n.cs.lastAttacker == a.id || n.goal == rpg.ai.Brain.Goal.FIGHT && n.fightTarget == 0;
            if (!involved) continue;
            n.fightTarget = 0;
            n.cs.lastAttacker = 0;
            if (n.cs.phase != rpg.actor.Actor.Combat.Phase.IDLE) n.cs.cancel();
            n.cs.blocking = false;
            n.cs.draw = 0;
            if (n.goal == rpg.ai.Brain.Goal.FIGHT) n.goal = rpg.ai.Brain.Goal.IDLE;
            n.nextThink = 0;
        }
        g.combat.projectiles.removeIf(pr -> pr.owner instanceof Npc n && (n.role == Npc.Role.GUARD || n.role == Npc.Role.LAWMAN));
        g.state.commit("Arrested", by == null ? 0 : by.id, a.id, jur, 0, "bounty " + b + " settled by sentence; " + returned + " stolen items returned");
        if (a == g.player) {
            g.ui.closeDialog();
            g.advanceTime(30, "serving a sentence");
            g.chat.add("You are arrested and serve a short sentence in " + jur + ". Your bounty there is cleared and stolen goods are confiscated.", Chat.Kind.CRIME);
        } else if (a instanceof Npc n) {
            n.arrestedUntil = g.state.minutes + 30;
            n.goal = rpg.ai.Brain.Goal.HELD;
            n.fightTarget = 0;
            n.cs.lastAttacker = 0;
        }
    }

    // ------------------------------------------------------------------ persistence
    public Map<String, Object> save() {
        Map<String, Object> m = new HashMap<>();
        m.put("_reported", new ArrayList<Object>(reportedCrimes));
        for (var e : bounties.entrySet()) {
            Map<String, Object> j = new HashMap<>();
            for (var b : e.getValue().entrySet()) j.put(String.valueOf(b.getKey()), b.getValue());
            m.put(e.getKey(), j);
        }
        return m;
    }

    @SuppressWarnings("unchecked")
    public void load(Map<String, Object> m) {
        bounties.clear(); resisting.clear(); haltedAt.clear();
        reportedCrimes.clear();
        if (m == null) return;
        for (var e : m.entrySet()) {
            if (e.getKey().equals("_reported")) { for (Object o : (List<Object>) e.getValue()) reportedCrimes.add(((Number) o).longValue()); continue; }
            Map<Long, Integer> j = new HashMap<>();
            for (var b : ((Map<String, Object>) e.getValue()).entrySet()) j.put(Long.parseLong(b.getKey()), ((Number) b.getValue()).intValue());
            bounties.put(e.getKey(), j);
        }
    }

    public void clear() { reportedCrimes.clear(); bounties.clear(); resisting.clear(); haltedAt.clear(); assaultAt.clear(); settledAt.clear(); }

    /** Death settles justice everywhere; personal memories, Honor and other people's fights remain. */
    public void playerRespawned() {
        long id = g.player.id;
        for (var charges : bounties.values()) charges.remove(id);
        resisting.remove(id); haltedAt.remove(id); assaultAt.clear();
        settledAt.put(id, g.time);
        for (Npc n : g.npcs) {
            for (Memory m : n.memories) if (m.crime() && m.subject == id) { m.settled = true; m.reported = true; }
            boolean fighting = n.fightTarget == id || n.cs.lastAttacker == id;
            boolean pursuing = n.hunt != null && n.hunt.target == id || n.fearOf == id || n.investigation != null && n.investigation.subject == id;
            if (n.hunt != null && n.hunt.target == id) n.hunt = null;
            if (n.investigation != null && n.investigation.subject == id) n.investigation = null;
            if (n.fearOf == id) { n.fearOf = 0; n.hideSet = false; }
            n.aware.remove(id);
            if (fighting) {
                n.fightTarget = 0; n.cs.lastAttacker = 0; n.cs.cancel(); n.cs.blocking = false; n.cs.draw = 0;
                g.combat.projectiles.removeIf(pr -> pr.owner == n);
            }
            if (fighting || pursuing || n.goal == rpg.ai.Brain.Goal.WATCH) {
                n.goal = rpg.ai.Brain.Goal.IDLE; n.nextThink = 0; n.hasGoalPos = false;
                if (n.loaded && n.alive) g.ai.desire(n, rpg.ai.AiSystem.A_IDLE);
            }
        }
        g.state.commit("JusticeSettledByDeath", id, 0, null, 0, "charges closed; personal memories retained");
    }

    public static boolean lawman(Npc n) { return n.id == Ids.LAWMAN_A || n.id == Ids.LAWMAN_B; }
}
