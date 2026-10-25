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
    private record Assault(long offender, long victim) {}
    private final Map<Assault, Double> assaultAt = new HashMap<>();
    /** When each offender was last arrested (game time): every grievance of the law's from before it is settled by the sentence. */
    private final Map<Long, Double> settledAt = new HashMap<>();
    public long crimes, witnessed, reports, rejectedReports, arrests;
    private double sentenceUntil = -1;
    private String sentenceJurisdiction = "";
    private int jailAdmissions;
    private record RoofKey(long actor,String jurisdiction) {}
    private static final class RoofWarning { long guard; double at,lastSeen,nextCharge; }
    private final Map<RoofKey,RoofWarning> roofWarnings=new HashMap<>();
    private double roofCheck;
    public boolean onRoof(Actor actor) {
        return actor!=null && actor.alive && actor.grounded && g.vox.block((int)Math.floor(actor.x),(int)Math.floor(actor.y-0.08f),(int)Math.floor(actor.z))==rpg.world.Blocks.ROOF;
    }
    public Actor roofTarget(Npc guard) {
        for(var entry:roofWarnings.entrySet())if(entry.getValue().guard==guard.id && g.time-entry.getValue().lastSeen<1 && g.time<entry.getValue().at+8) {
            Actor actor=g.actor(entry.getKey().actor);if(onRoof(actor))return actor;
        }
        return null;
    }
    private void roofs() {
        if(g.time<roofCheck)return;roofCheck=g.time+0.5;
        roofWarnings.entrySet().removeIf(entry->g.time-entry.getValue().lastSeen>20);
        for(Actor actor:g.actors())if(actor.domain==astratto.ai.AgentDomain.HUMAN && onRoof(actor) && !inCustody(actor)) {
            String jur=DevMap.jurisdiction(actor.x,actor.z);if(DevMap.WILDS.equals(jur))continue;
            Npc observer=null;float nearest=25;
            for(Npc guard:g.npcs)if(guard.alive && guard.loaded && (guard.role==Npc.Role.GUARD || guard.role==Npc.Role.LAWMAN) && guard.homeJurisdiction.equals(jur) && guard.distTo(actor)<nearest && g.ai.canSee(guard,actor)) {observer=guard;nearest=guard.distTo(actor);}
            if(observer==null)continue;
            RoofKey key=new RoofKey(actor.id,jur);RoofWarning warning=roofWarnings.get(key);
            if(warning==null) {
                warning=new RoofWarning();warning.at=g.time;warning.guard=observer.id;warning.nextCharge=g.time+8;roofWarnings.put(key,warning);
                g.ai.say(observer,"Get down from that roof. This is your warning!");observer.nextThink=0;
                g.state.commit("RoofWarning",observer.id,actor.id,jur,0,"eight seconds to leave the roof");
            }
            warning.lastSeen=g.time;
            if(g.time>=warning.nextCharge) {warning.nextCharge=g.time+30;crime(actor,"TRESPASS",0,5,null,0);}
        }
    }
    public int jailAdmissions() { return jailAdmissions; }
    public double sentenceRemaining() { return Math.max(0, sentenceUntil - g.state.minutes); }
    public String sentenceJurisdiction() { return sentenceJurisdiction; }

    /** Clock-driven custody; sleeping on a cell slab uses the same release path. */
    public void update() {
        roofs();
        if (sentenceUntil >= 0 && g.state.minutes >= sentenceUntil) releasePlayer();
        for (Npc npc : g.npcs) if (npc.arrestedUntil >= 0 && (!npc.alive || g.state.minutes >= npc.arrestedUntil)) {
            String jurisdiction = npc.arrestJurisdiction.isEmpty() ? DevMap.jurisdiction(npc.x, npc.z) : npc.arrestJurisdiction;
            npc.arrestedUntil = -1; npc.arrestJurisdiction = "";
            returnPossessions(npc);
            var cell = rpg.world.Jails.forJurisdiction(jurisdiction);
            if (npc.alive && cell != null) {
                g.teleport(npc, cell.releaseX(), DevMap.FLOOR, cell.releaseZ());
                npc.goal = rpg.ai.Brain.Goal.IDLE; npc.nextThink = 0;
            }
            openEmptyCell(jurisdiction);
        }
    }
    private void openEmptyCell(String jurisdiction) {
        if (inCustody(g.player) && sentenceJurisdiction.equals(jurisdiction)) return;
        for (Npc npc : g.npcs) if (npc.alive && npc.arrestedUntil > g.state.minutes && npc.arrestJurisdiction.equals(jurisdiction)) return;
        var door = g.objects.jailDoor(jurisdiction);
        if (door != null) { door.setLocked(g, false); door.setOpen(g, true, g.player, g.state.newRequestId()); }
    }
    public void sleepThroughSentence() {
        if (!inCustody(g.player)) return;
        g.advanceTime(sentenceRemaining(), "sleeping through the sentence");
        update();
    }
    private void releasePlayer() {
        var cell = rpg.world.Jails.forJurisdiction(sentenceJurisdiction);
        sentenceUntil = -1;
        returnPossessions(g.player);
        openEmptyCell(sentenceJurisdiction);
        if (cell != null) g.player.poseReset = true;
        g.chat.add("Your sentence is served. You are free to leave.", Chat.Kind.CRIME);
        g.state.commit("SentenceServed", g.player.id, 0, sentenceJurisdiction, 0, "admission " + jailAdmissions);
        sentenceJurisdiction = "";
    }

    /** Close this jurisdiction's cases and cancel its force's outstanding attacks; personal memories remain. */
    private void settle(Actor a, String jur) {
        settledAt.put(a.id, g.time);
        a.cs.cancel(); a.cs.blocking = false; a.cs.draw = 0;
        if (a == g.player) g.controller.cancelHandInput();
        if (bounties.containsKey(jur)) bounties.get(jur).remove(a.id);
        resisting.remove(a.id); haltedAt.remove(a.id);
        Set<Actor> stoodDown = new HashSet<>();
        stoodDown.add(a);
        for (Npc n : g.npcs) {
            for (Memory m : n.memories) if (m.crime() && m.subject == a.id && jur.equals(m.jurisdiction)) {
                m.reported = true; m.settled = true; reportedCrimes.add(m.eventId);
            }
            if ((n.role != Npc.Role.GUARD && n.role != Npc.Role.LAWMAN) || !jur.equals(n.homeJurisdiction)) continue;
            boolean involved = n.fightTarget == a.id || n.cs.lastAttacker == a.id || n.watchTarget == a.id
                    || n.hunt != null && n.hunt.target == a.id || n.investigation != null && n.investigation.subject == a.id;
            involved |= n.goal == rpg.ai.Brain.Goal.FIGHT && n.fightTarget == 0 && n.cs.lastAttacker == 0;
            if (!involved) continue;
            stoodDown.add(n);
            n.fightTarget = 0; n.cs.lastAttacker = 0; n.cs.cancel(); n.cs.blocking = false; n.cs.draw = 0;
            n.hunt = null; n.investigation = null; n.aware.remove(a.id); n.watchTarget = 0;
            n.goal = rpg.ai.Brain.Goal.IDLE; n.hasGoalPos = false; n.nextThink = 0;
            if (n.alive && n.loaded) g.ai.desire(n, rpg.ai.AiSystem.A_IDLE);
        }
        g.combat.landProjectiles(stoodDown::contains);
    }
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
            String protest=kind.equals("VANDALISM") ? switch(w.role) {
                case GUARD,LAWMAN,KNIGHT -> "Stop damaging that property! The watch will hear of this.";
                case MERCHANT,CRAFTSMAN,WORKER,MINER -> "That took work to make! You'll pay for the damage.";
                case THIEF,BANDIT,BRUTE -> "Keep that noise away from me.";
                default -> "Leave that alone! Someone fetch the watch.";
            } : kind.equals("THEFT") ? "Thief!" : kind.equals("MURDER") ? "Murder!" : "Guards!";
            w.wantYaw=(float)Math.atan2(-(culprit.x-w.x),-(culprit.z-w.z)); g.ai.say(w,protest);
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

    private boolean lawfulForce(Actor a, Actor v) {
        if (rpg.ai.AiSystem.alliedWatch(a,v))return true;
        if (a == null || v == null || a == v || !(a == g.player || a instanceof Npc)) return true;
        if (a.cs.lastAttacker == v.id && g.time - a.cs.lastHurt < 15) return true;
        if (v instanceof Npc n && (n.role == Npc.Role.BANDIT || n.role == Npc.Role.BRUTE)) return true;
        if (a instanceof Npc officer && (officer.role == Npc.Role.GUARD || officer.role == Npc.Role.LAWMAN) && bounty(v.id, officer.homeJurisdiction) > 0) return true;
        return a == g.player && g.bountyBoard.authorized(v.id);
    }

    /** Player and human NPC offenses use the same witness/report/bounty path. */
    public void onHit(Actor a, Actor v, long cause) {
        if(a.summonOwner!=0) { Actor owner=g.actor(a.summonOwner); if(owner==null)return; a=owner; }
        if(v.summonOwner!=0)return;
        if (!(v == g.player || v instanceof Npc) || lawfulForce(a, v)) return;
        Assault key = new Assault(a.id, v.id);
        Double last = assaultAt.get(key);
        if (last != null && g.time - last < 10) return;
        assaultAt.put(key, g.time);
        crime(a, "ASSAULT", v.id, 0, null, cause);
    }

    public void onKilled(Actor victim, Actor killer, long cause) {
        if(victim.summonOwner!=0)return;
        if(killer!=null && killer.summonOwner!=0)killer=g.actor(killer.summonOwner);
        if(killer!=null && killer.domain!=astratto.ai.AgentDomain.HUMAN && (victim==g.player || victim instanceof Npc)) {
            for(Npc observer:g.ai.witnesses(killer)) if(observer.distTo(victim)<25 && g.vox.sight(observer.x,observer.eyeY(),observer.z,victim.x,victim.chestY(),victim.z)) {
                observer.remember(new Memory("BEAST_ATTACK",killer.id,victim.id,DevMap.jurisdiction(victim.x,victim.z),g.state.minutes,1,"witnessed",cause,0,"saw a creature kill "+victim.name),24);
                g.ai.say(observer,"That beast killed "+victim.name+"!"); observer.fearOf=killer.id;observer.fearX=killer.x;observer.fearZ=killer.z;observer.fearSeenAt=g.time;
            }
            return;
        }
        if ((victim == g.player || victim instanceof Npc) && !lawfulForce(killer, victim)) crime(killer, "MURDER", victim.id, 0, null, cause);
    }

    public void closeCase(Actor offender, String jurisdiction) { settle(offender, jurisdiction); }

    // ------------------------------------------------------------------ reports and bounties
    /** A witness, in person, tells a lawman (GDD 3.5). Validated: a real witness, the right jurisdiction, close enough, alive. */
    public Result report(Npc witness, Memory m, Npc lawman) {
        String why = null;
        if (m.settled) why = "the case has already been settled";
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
            case "TRESPASS" -> 5;
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
        ItemStack payment = new ItemStack(Items.COINS, b, to.id, false, a.id);
        if (!to.canStore(payment)) return Result.no("The watch cannot receive the payment right now.");
        a.takeItems(Items.COINS, b);
        to.store(payment);
        settle(a, jur);
        g.state.commit(req, "BountyPaid", a.id, to.id, jur, 0, b + " coins to " + to.name);
        if (a == g.player) { g.chat.add("You pay your bounty of " + b + " coins. You are no longer wanted in " + jur + ".", Chat.Kind.CRIME); if (g.sfx != null) g.sfx.ui("coins", 0.6f); g.honor.paidBounty(b); g.ai.rumors.deed("paid their bounty in full", to.id); }
        return Result.ok();
    }

    public boolean resisting(long id) { return resisting.contains(id); }

    /** Whether a blow struck (or a grudge formed) at a moment was settled by an arrest since (the law does not fight it again). */
    public boolean settled(long offender, double when) { Double at = settledAt.get(offender); return at != null && when <= at; }

    /** Whether someone is in custody right now: the law does not strike a prisoner. */
    public boolean inCustody(Actor a) { return a == g.player ? sentenceRemaining() > 0 : a instanceof Npc n && n.arrestedUntil > g.state.minutes; }

    /** Only what this officer actually knows informs the challenge; hearsay remains weaker than sight. */
    public float certainty(Npc guard, Actor offender) {
        float confidence = 0.25f;
        for (var memory : guard.memories) if (memory.subject == offender.id && memory.crime() && !memory.settled
                && guard.homeJurisdiction.equals(memory.jurisdiction))
            confidence = Math.max(confidence, memory.confidence * (memory.witnessed() ? 1 : 0.65f));
        return Math.min(1, confidence);
    }

    public Result denyCharge(Actor offender, Npc guard, long request) {
        if (g.state.alreadyCommitted(request) || bounty(offender.id, guard.homeJurisdiction) <= 0) return Result.no("There is no open charge to dispute.");
        float evidence = certainty(guard, offender);
        float chance = Math.max(0.02f, Math.min(0.75f, (1 - evidence) * 0.7f + offender.skills.level(rpg.skill.Skill.THIEVING) * 0.001f));
        double roll = Math.floorMod(request * 6364136223846793005L + guard.id * 1442695040888963407L, 10000) / 10000.0;
        g.state.commit(request, "ChargeDisputed", offender.id, guard.id, guard.homeJurisdiction, 0, "evidence " + evidence);
        if (roll < chance) {
            settle(offender, guard.homeJurisdiction);
            g.ai.say(guard, "That account doesn't hold together. You're free to go.");
            g.chat.say("The charge is dismissed. People still remember what they saw.");
        } else {
            g.ai.say(guard, evidence > 0.85f ? "I saw you myself. You're coming with me." : "Your story doesn't match the testimony. Come quietly.");
            arrest(offender, guard.homeJurisdiction, guard);
        }
        return Result.ok();
    }

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
        float evidence = certainty(guard, p);
        Dialog d = new Dialog(guard.name, (evidence >= 0.85f ? "I saw what you did. " : evidence >= 0.5f ? "Witnesses have identified you. " : "There is an accusation against you. ") + "You are wanted in " + guard.homeJurisdiction + " for " + b + " coins. Pay, explain yourself, or come quietly.");
        d.npc = guard.id;
        d.choice("Pay " + b + " coins.", () -> { Result r = payBounty(p, guard, g.state.newRequestId()); if (r.rejected()) g.chat.warn(r.reason()); });
        d.choice("You have the wrong person. (lie; risk jail)", () -> denyCharge(p, guard, g.state.newRequestId()));
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
        if(inCustody(a))return;
        arrests++;
        int b = bounty(a.id, jur);
        List<Npc> witnesses = g.ai.witnesses(a); // identify witnesses before the prisoner is moved into the cell
        settle(a, jur);
        var escrow = new rpg.objects.Container(g.state.newEntityId(), "jail_escrow", a.name + "'s held possessions", "escrow", a.x, -32, a.z, 320, a.id);
        escrow.locked = true;
        // Preflight before moving any originals. Filled bags remain their own containers.
        var originals = new ArrayList<>(a.inv.stacks()); originals.addAll(a.eq.stacks());
        if (!escrow.contents.canAddAll(originals)) throw new IllegalStateException("prison escrow capacity");
        for (var slot : rpg.item.ItemDef.Slot.values()) {
            var item = a.eq.get(slot); if (item == null) continue;
            int[] place=escrow.contents.findSpace(item.def,-1);
            if(place==null || !escrow.contents.putAt(item,place[0],place[1],place[2]!=0))throw new IllegalStateException("prison gear layout");
            a.eq.set(slot, null);
            escrow.custodySlots.put(slot.name(), place[1]*escrow.contents.w+place[0]);
        }
        for (var item : new ArrayList<>(a.inv.stacks())) escrow.contents.add(item);
        a.inv.clear(); g.objects.add(escrow);
        int returned = Property.confiscate(g, escrow.contents);
        Npc lawman = g.ai.lawmanFor(jur);
        int bill=b+(int)((b*3L+99)/100), paid=Math.min(bill,Property.coins(escrow.contents));
        for(ItemStack coin:Property.takeCoins(escrow.contents,paid)) {
            coin.owner=lawman==null?0:lawman.id;coin.stolen=false;
            if(lawman==null || !lawman.store(coin))g.objects.drop(coin,by==null?a.x:by.x,DevMap.FLOOR+0.1f,by==null?a.z:by.z);
        }
        double duration=Math.min(180,30+b/10)+Math.ceil((bill-paid)/10.0);
        g.state.commit("PrisonFinePaid",a.id,lawman==null?0:lawman.id,jur,0,"bounty+3%="+bill+" paid="+paid+" shortfall served="+(bill-paid));
        var cell = rpg.world.Jails.forJurisdiction(jur);
        float cx = cell != null ? cell.x() : lawman != null ? lawman.postX - 2 : a.x;
        float cz = cell != null ? cell.z() : lawman != null ? lawman.postZ + 3 : a.z;
        g.teleport(a, cx, DevMap.FLOOR, cz);
        if (a == g.player && cell != null) g.controller.snapTo((float) Math.atan2(-(cell.gateX() + 0.5f - cx), -(cell.frontZ() + 0.5f - cz)));
        a.hp = Math.max(a.hp, a.maxHp() * 0.5f);
        a.cs.cancel();
        a.cs.blocking = false;
        haltedAt.remove(a.id);
        g.state.commit("Arrested", by == null ? 0 : by.id, a.id, jur, 0, "bounty " + b + " settled by sentence; " + returned + " stolen items returned");
        var door = g.objects.jailDoor(jur);
        if (door != null) { door.open = false; door.setLocked(g, true); door.apply(g); }
        if (a == g.player) {
            if (g.ui != null) g.ui.closeDialog();
            sentenceUntil = g.state.minutes + duration;
            sentenceJurisdiction = jur;
            jailAdmissions++;
            var incident = g.state.commit("JailAdmission", a.id, by == null ? 0 : by.id, jur, 0, "admission " + jailAdmissions);
            if (by != null && !witnesses.contains(by)) witnesses.add(by);
            for (Npc witness : witnesses) witness.remember(new Memory("IMPRISONED", a.id, 0, jur, g.state.minutes, 1, "witnessed", incident.id(), jailAdmissions, "was taken to jail"), 24);
            g.chat.add("You are held in " + jur + ". Your possessions are held until release; stolen goods are confiscated. Bounty plus 3% tax: " + bill + " coins (paid " + paid + "). Any shortfall adds sentence time. Sleep on the cell slab to serve it.", Chat.Kind.CRIME);
        } else if (a instanceof Npc n) {
            n.arrestedUntil = g.state.minutes + duration;
            n.arrestJurisdiction = jur;
            n.goal = rpg.ai.Brain.Goal.HELD;
            n.fightTarget = 0;
            n.cs.lastAttacker = 0;
        }
    }

    private void returnPossessions(Actor actor) {
        for (var object : g.objects.all()) if (object instanceof rpg.objects.Container held && held.kind.equals("escrow") && held.owner == actor.id) {
            for (var entry : held.custodySlots.entrySet()) {
                rpg.item.ItemDef.Slot slot;
                try { slot = rpg.item.ItemDef.Slot.valueOf(entry.getKey()); } catch (IllegalArgumentException ignored) { continue; }
                int cell=entry.getValue();
                int index=held.contents.at(cell%held.contents.w,cell/held.contents.w);
                ItemStack item = held.contents.get(index);
                if (item != null && actor.eq.get(slot) == null) { held.contents.set(index, null); actor.eq.set(slot, item); }
            }
            for (var item : new ArrayList<>(held.contents.stacks())) {
                if (!actor.store(item)) g.objects.drop(item, actor.x, actor.y+0.1f, actor.z);
            }
            held.contents.clear(); held.removed = true;
        }
    }

    // ------------------------------------------------------------------ persistence
    public Map<String, Object> save() {
        Map<String, Object> m = new HashMap<>();
        m.put("_reported", new ArrayList<Object>(reportedCrimes));
        m.put("_custody", Map.of("until", sentenceUntil, "jur", sentenceJurisdiction, "admissions", jailAdmissions));
        for (var e : bounties.entrySet()) {
            Map<String, Object> j = new HashMap<>();
            for (var b : e.getValue().entrySet()) j.put(String.valueOf(b.getKey()), b.getValue());
            m.put(e.getKey(), j);
        }
        return m;
    }

    @SuppressWarnings("unchecked")
    public void load(Map<String, Object> m) {
        clear();
        if (m == null) return;
        for (var e : m.entrySet()) {
            if (e.getKey().equals("_custody")) {
                Map<String, Object> custody = (Map<String, Object>) e.getValue();
                sentenceUntil = astratto.util.Json.num(custody, "until", -1);
                sentenceJurisdiction = astratto.util.Json.str(custody, "jur", "");
                jailAdmissions = astratto.util.Json.integer(custody, "admissions", 0);
                continue;
            }
            if (e.getKey().equals("_reported")) { for (Object o : (List<Object>) e.getValue()) reportedCrimes.add(((Number) o).longValue()); continue; }
            Map<Long, Integer> j = new HashMap<>();
            for (var b : ((Map<String, Object>) e.getValue()).entrySet()) j.put(Long.parseLong(b.getKey()), ((Number) b.getValue()).intValue());
            bounties.put(e.getKey(), j);
        }
    }

    public void clear() { roofWarnings.clear();roofCheck=0; reportedCrimes.clear(); bounties.clear(); resisting.clear(); haltedAt.clear(); assaultAt.clear(); settledAt.clear(); sentenceUntil = -1; sentenceJurisdiction = ""; jailAdmissions = 0; }

    /** Death settles justice everywhere; personal memories, Honor and other people's fights remain. */
    public void playerRespawned() {
        roofWarnings.keySet().removeIf(key->key.actor==g.player.id);
        String heldIn = sentenceJurisdiction;
        sentenceUntil = -1; sentenceJurisdiction = "";
        if (!heldIn.isEmpty()) openEmptyCell(heldIn);
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
                g.combat.landProjectiles(owner -> owner == n);
            }
            if (fighting || pursuing || n.goal == rpg.ai.Brain.Goal.WATCH && (n.watchTarget == id || n.watchTarget == 0)) {
                n.goal = rpg.ai.Brain.Goal.IDLE; n.nextThink = 0; n.hasGoalPos = false; n.watchTarget = 0;
                if (n.loaded && n.alive) g.ai.desire(n, rpg.ai.AiSystem.A_IDLE);
            }
        }
        g.state.commit("JusticeSettledByDeath", id, 0, null, 0, "charges closed; personal memories retained");
    }

    public static boolean lawman(Npc n) { return n.id == Ids.LAWMAN_A || n.id == Ids.LAWMAN_B; }
}
