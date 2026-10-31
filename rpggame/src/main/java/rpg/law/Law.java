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
 * happened and reports it - then, and only then, that jurisdiction's bounty rises. Stop the witness and there is no
 * bounty, though the theft still happened.
 *
 * Each kingdom keeps its own books (gamefixes): a bounty in one does not follow you over the border, and every report
 * adds to that kingdom's total by the crime's severity (see {@link #charge}), halved for high Honor, with no ceiling.
 * The watch goes after a bounty above 21 G, or any bounty of someone who walks up to them (a petty one is a fine asked
 * for face to face, never fought over); at the lockdown bounty the
 * town shuts its shops and the watch attacks on sight; bounty hunters (Posses) do the rest.
 */
public final class Law {
    private final Rpg g;
    /** jurisdiction -> offender -> bounty. */
    private final Map<String, Map<Long, Integer>> bounties = new HashMap<>();
    public final List<String> reportsLog = new ArrayList<>();
    private final Map<Long, Double> haltedAt = new HashMap<>();
    private final Set<Long> resisting = new HashSet<>();
    private final Set<String> defiance=new HashSet<>();
    private static String defianceKey(long actor,String jurisdiction){return actor+":"+jurisdiction;}
    public boolean refusesPeace(long actor,String jurisdiction){return defiance.contains(defianceKey(actor,jurisdiction));}
    private record Assault(long offender, long victim) {}
    private final Map<Assault, Double> assaultAt = new HashMap<>();
    /** When each offender was last arrested (game time): every grievance of the law's from before it is settled by the sentence. */
    private final Map<Long, Double> settledAt = new HashMap<>();
    public long crimes, witnessed, reports, rejectedReports, arrests;
    private double sentenceUntil = -1;
    private String sentenceJurisdiction = "";
    private int jailAdmissions;
    private int sentenceBounty;
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
        // A fine's unanswered conversation expires even if a doorway prevents the officer catching up.
        for(var entry:new java.util.ArrayList<>(haltedAt.entrySet()))if(g.time-entry.getValue()>10) {
            Actor offender=g.actor(entry.getKey());
            if(offender==null || !offender.alive){haltedAt.remove(entry.getKey());continue;}
            for(Npc officer:g.npcs)if(officer.alive && officer.fightTarget==offender.id
                    && (officer.role==Npc.Role.GUARD || officer.role==Npc.Role.LAWMAN)
                    && petty(offender.id,officer.homeJurisdiction) && !resisting(offender.id,officer.homeJurisdiction)
                    && !(officer.cs.lastAttacker==offender.id && g.time-officer.cs.lastHurt<12)) {
                declineFine(offender,officer);break;
            }
        }
        borders();
        posses.update();
        if (sentenceUntil >= 0 && g.state.minutes >= sentenceUntil) releasePlayer();
        else if (sentenceUntil >= 0 && g.player.alive) {
            var cell = rpg.world.Jails.forJurisdiction(sentenceJurisdiction);
            if (cell != null && !cell.contains(g.player.x, g.player.y, g.player.z)) escapePlayer();
        }
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
        if (door != null) { door.relock=false; door.autoOpened=false; door.setLocked(g, false); door.setOpen(g, true, g.player, g.state.newRequestId()); }
    }
    public void sleepThroughSentence() {
        if (!inCustody(g.player)) return;
        g.advanceTime(sentenceRemaining(), "sleeping through the sentence");
        update();
    }
    private void releasePlayer() {
        var cell = rpg.world.Jails.forJurisdiction(sentenceJurisdiction);
        sentenceUntil = -1;
        sentenceBounty = 0;
        returnPossessions(g.player);
        openEmptyCell(sentenceJurisdiction);
        if (cell != null) { g.player.poseReset = true; var door=g.objects.jailDoor(cell.jurisdiction()); if(door!=null && door.locked)g.teleport(g.player,cell.releaseX(),DevMap.FLOOR,cell.releaseZ()); }
        clearSlate(g.player);
        g.chat.add("Your sentence is served. You are free to leave.", Chat.Kind.CRIME);
        g.state.commit("SentenceServed", g.player.id, 0, sentenceJurisdiction, 0, "admission " + jailAdmissions);
        sentenceJurisdiction = "";
    }

    private void escapePlayer() {
        String jurisdiction = sentenceJurisdiction;
        int before = bounty(g.player.id, jurisdiction);
        int restored = (int)Math.min(Integer.MAX_VALUE, (long)before + sentenceBounty + 75);
        sentenceUntil = -1; sentenceJurisdiction = ""; sentenceBounty = 0;
        bounties.computeIfAbsent(jurisdiction, k -> new HashMap<>()).put(g.player.id, restored);
        var event = g.state.commit("Jailbreak", g.player.id, 0, jurisdiction, 0, "bounty " + before + " -> " + restored);
        g.state.commit("BountyChanged", 0, g.player.id, jurisdiction, event.id(), before + " -> " + restored);
        g.chat.add("You escaped before serving your sentence. Your previous bounty is restored, plus 75 Gold.", Chat.Kind.CRIME);
        openEmptyCell(jurisdiction);
    }

    /**
     * (0.2.2, gamefixes) A served sentence is a clean slate, as far as blows go: every townsperson who was fighting,
     * hunting, watching or fleeing the released prisoner stands down, and the blows they took from them are answered.
     * What they remember of the crime stays (gossip, opinion); only the outlaws, and a watch with a bounty still
     * standing elsewhere, keep after them.
     */
    private void clearSlate(Actor a) {
        for (Npc n : g.npcs) {
            if (!n.alive || n.hostileToPlayer || n.posse) continue;
            if ((n.role == Npc.Role.GUARD || n.role == Npc.Role.LAWMAN) && bounty(a.id, n.homeJurisdiction) > 0) continue;
            boolean involved = n.fightTarget == a.id || n.cs.lastAttacker == a.id || n.watchTarget == a.id || n.fearOf == a.id
                    || n.hunt != null && n.hunt.target == a.id || n.investigation != null && n.investigation.subject == a.id;
            var known = n.aware.get(a.id);
            if (known != null) { known.level = 0; known.settledAt = g.time; }
            if (!involved) continue;
            if (n.fightTarget == a.id) n.fightTarget = 0;
            if (n.cs.lastAttacker == a.id) n.cs.lastAttacker = 0;
            if (n.watchTarget == a.id) n.watchTarget = 0;
            if (n.fearOf == a.id) n.fearOf = 0;
            if (n.hunt != null && n.hunt.target == a.id) n.hunt = null;
            if (n.investigation != null && n.investigation.subject == a.id) n.investigation = null;
            n.cs.cancel(); n.cs.blocking = false; n.cs.draw = 0;
            n.goal = rpg.ai.Brain.Goal.IDLE; n.hasGoalPos = false; n.nextThink = 0;
            if (n.loaded) g.ai.desire(n, rpg.ai.AiSystem.A_IDLE);
        }
        settledAt.put(a.id, g.time);
        g.state.commit("SlateCleared", a.id, 0, sentenceJurisdiction, 0, "released: townsfolk stand down");
    }

    /** Close this jurisdiction's cases and cancel its force's outstanding attacks; personal memories remain. */
    private void settle(Actor a, String jur) {
        settledAt.put(a.id, g.time);
        a.cs.cancel(); a.cs.blocking = false; a.cs.draw = 0;
        if (a == g.player) g.controller.cancelHandInput();
        if (bounties.containsKey(jur)) bounties.get(jur).remove(a.id);
        confronted.remove(new Key(a.id, jur)); fineDeclined.remove(new Key(a.id, jur));
        resisting.remove(a.id); haltedAt.remove(a.id);defiance.remove(defianceKey(a.id,jur));
        if(a==g.player){reminder="";if(g.ui!=null && g.ui.dialog!=null && g.ui.dialog.standoff)g.ui.closeDialog();}
        Set<Actor> stoodDown = new HashSet<>();
        stoodDown.add(a);
        for (Npc n : g.npcs) {
            for (Memory m : n.memories) if (m.crime() && m.subject == a.id && jur.equals(m.jurisdiction)) {
                m.reported = true; m.settled = true; reportedCrimes.add(m.eventId);
            }
            if ((n.role != Npc.Role.GUARD && n.role != Npc.Role.LAWMAN) || !jur.equals(n.homeJurisdiction)) continue;
            // (gamefixes: after a lie that landed, the whole watch still came after the player) the matter is closed for
            // every officer, not just those on it: what they know of where the player is stays known, marked looked into,
            // so the player standing among them is not a fresh mystery to go and see about
            var known = n.aware.computeIfAbsent(a.id, id -> new rpg.ai.Stealth.Aware());
            known.settledAt = g.time;
            known.bloodQuestionUntil = -99;
            known.bloodQuestionAgain = g.time + 90;
            known.bloodSeen = a.bloodied;
            if (n.investigation != null && n.investigation.subject == a.id) { n.investigation = null; if (n.goal == rpg.ai.Brain.Goal.INVESTIGATE) { n.goal = rpg.ai.Brain.Goal.IDLE; n.nextThink = 0; } }
            boolean involved = n.fightTarget == a.id || n.cs.lastAttacker == a.id || n.watchTarget == a.id
                    || n.hunt != null && n.hunt.target == a.id || n.investigation != null && n.investigation.subject == a.id;
            involved |= n.goal == rpg.ai.Brain.Goal.FIGHT && n.fightTarget == 0 && n.cs.lastAttacker == 0;
            if (!involved) continue;
            stoodDown.add(n);
            n.fightTarget = 0; n.cs.lastAttacker = 0; n.cs.cancel(); n.cs.blocking = false; n.cs.draw = 0;
            n.hunt = null; n.investigation = null; n.watchTarget = 0;
            n.goal = rpg.ai.Brain.Goal.IDLE; n.hasGoalPos = false; n.nextThink = 0;
            if (n.alive && n.loaded) g.ai.desire(n, rpg.ai.AiSystem.A_IDLE);
        }
        g.combat.landProjectiles(stoodDown::contains);
    }
    /** Crime events already reported: a second witness of the same crime adds testimony, not a second bounty. */
    private final Set<Long> reportedCrimes = new HashSet<>();

    public Law(Rpg g) { this.g = g; this.posses = new Posses(g, this); }
    public final Posses posses;
    /** Offenders who have walked up to the watch of a kingdom while wanted there: that watch acts on any bounty. */
    private record Key(long offender, String jurisdiction) {}
    private final Set<Key> confronted = new HashSet<>();
    /** The kingdom the player was last in, for the reminder on crossing into one where they are wanted. */
    private String lastKingdom = "";
    public String reminder = "";

    /** Whether the watch of a kingdom goes after an offender: a bounty above the pursuit line, or any once they came to the watch. */
    public boolean pursued(long offender, String jurisdiction) {
        int b = bounty(offender, jurisdiction);
        return b > g.cfg.pursuitBounty || b > 0 && confronted.contains(new Key(offender, jurisdiction));
    }
    /**
     * A petty bounty (gamefixes: the watch attacked over 10 G): at or under the pursuit line the watch does not hunt
     * or strike - an officer who sees the offender close by walks up and asks for the fine, and one who is put off lets
     * it stand on the books and leaves them be for a while ({@link #FINE_GRACE} seconds).
     */
    public boolean petty(long offender, String jurisdiction) { int b = bounty(offender, jurisdiction); return b > 0 && b <= g.cfg.pursuitBounty; }
    static final double FINE_GRACE = 240;
    private final Map<Key, Double> fineDeclined = new HashMap<>();
    public boolean fineDeferred(long offender,String jurisdiction) {
        Double at=fineDeclined.get(new Key(offender,jurisdiction));
        return petty(offender,jurisdiction) && at!=null && g.time-at<=FINE_GRACE;
    }
    /** Whether an officer who sees a petty offender this close should go and ask for the fine now. */
    public boolean fineDue(Npc officer, Actor offender) {
        if (!petty(offender.id, officer.homeJurisdiction) || officer.distTo(offender) > 9) return false;
        Double at = fineDeclined.get(new Key(offender.id, officer.homeJurisdiction));
        return at == null || g.time - at > FINE_GRACE;
    }
    /** A town in lockdown (gamefixes): the player is in it with a bounty this high - shops shut, the watch attacks on sight. */
    public boolean lockdown(String jurisdiction) {
        return !DevMap.WILDS.equals(jurisdiction) && jurisdiction.equals(DevMap.jurisdiction(g.player.x, g.player.z)) && bounty(g.player.id, jurisdiction) >= g.cfg.lockdownBounty;
    }
    /** The wanted player speaks to the watch: whatever the bounty, the officer acts on it now. */
    public void confront(Npc officer, Actor offender) {
        confronted.add(new Key(offender.id, officer.homeJurisdiction));
        haltedAt.remove(offender.id);
        officer.fightTarget = offender.id; officer.goal = rpg.ai.Brain.Goal.FIGHT; officer.nextThink = 0;
        halt(officer, offender);
    }

    /**
     * What a reported crime adds to the kingdom's bounty (gamefixes schedule): disorderly conduct 5, vandalism 10, animal
     * slaughter 30, horse slaughter 50, trespassing 50, arson 200, murder 400 (a guard or lawman 500, a noble 600, a royal
     * 1,000 - carried in the memory's value); theft half the value; assault and lockpicking their configured charges.
     */
    int charge(Memory m) {
        return switch (m.kind) {
            case "THEFT" -> Math.max(1, Math.round(m.value * g.cfg.theftBountyShare));
            case "DISORDERLY" -> 5;
            case "BRIBERY" -> 25;
            case "VANDALISM" -> 10;
            case "LIVESTOCK" -> m.value >= 50 ? 50 : 30;
            case "TRESPASS" -> 50;
            case "ARSON" -> 200;
            case "ASSAULT" -> g.cfg.assaultBounty;
            case "MURDER" -> Math.max(400, m.value);
            default -> 20;
        };
    }
    /** The bounty a murder carries, by who was killed. */
    private static int murderCharge(Actor victim) {
        if (victim instanceof Npc n) {
            if (n.standing == Npc.Standing.ROYAL) return 1000;
            if (n.standing == Npc.Standing.NOBLE) return 600;
            if (n.role == Npc.Role.GUARD || n.role == Npc.Role.LAWMAN) return 500;
        }
        return 400;
    }

    /** Crossing into a kingdom where the player is wanted brings up a reminder of the amount. */
    private void borders() {
        String here = DevMap.jurisdiction(g.player.x, g.player.z);
        if (here.equals(lastKingdom)) return;
        lastKingdom = here;
        int b = DevMap.WILDS.equals(here) ? 0 : bounty(g.player.id, here);
        if (b <= 0 || !g.player.alive) return;
        reminder = here + (lockdown(here) ? " is in lockdown. " : ": ") + "you are wanted for " + rpg.item.Currency.text(b) + " G.";
        g.state.commit("BountyReminder", g.player.id, 0, here, 0, reminder);
    }

    public int bounty(long offender, String jurisdiction) {
        Map<Long, Integer> m = bounties.get(jurisdiction);
        return m == null ? 0 : m.getOrDefault(offender, 0);
    }
    public boolean wantedAnywhere(long offender) { for (var m : bounties.values()) if (m.getOrDefault(offender, 0) > 0) return true; return false; }
    public Map<String, Map<Long, Integer>> all() { return bounties; }
    /** Developer changes close existing case state before replacing its amount, scoped to one kingdom. */
    public boolean setBounty(Actor actor,String jurisdiction,int amount){
        if(amount<0 || !java.util.List.of(DevMap.ALPHA,DevMap.BETA,DevMap.PALACE).contains(jurisdiction))return false;
        settle(actor,jurisdiction);
        if(amount>0)bounties.computeIfAbsent(jurisdiction,k->new HashMap<>()).put(actor.id,amount);
        g.state.commit("BountySet",actor.id,0,jurisdiction,0,Integer.toString(amount));return true;
    }


    // ------------------------------------------------------------------ crimes and witnesses
    public void theft(Actor thief, long owner, ItemStack stolen, WorldObject where, long cause) {
        crime(thief, "THEFT", owner, stolen.value(), where, cause);
    }

    /** Whether a victim of the player's is innocent: not an outlaw, not hostile, not someone who struck them in the last moments. */
    private boolean innocent(long victim) {
        Actor v = g.actor(victim);
        if (v == null) return victim != 0;
        if (v instanceof Npc n && (n.role == Npc.Role.BANDIT || n.role == Npc.Role.BRUTE || n.role == Npc.Role.MONSTER || n.hostileToPlayer)) return false;
        if (v instanceof rpg.animal.Animal an && !an.species.domestic) return false;
        if (g.player.cs.lastAttacker == victim && g.time - g.player.cs.lastHurt < 15) return false;   // self-defence
        return true;
    }

    /** A crime happens: committed as a fact; whoever sees it now remembers it. Returns how many saw it. */
    public int crime(Actor culprit, String kind, long victim, int value, WorldObject where, long cause) {
        if (g.actor(victim) instanceof rpg.monster.Monster || culprit instanceof rpg.monster.Monster) return 0;
        if (g.scripted) System.out.printf("      [crime] %s by %s against %s at (%.1f, %.1f), t %.1f%n", kind, culprit.name, g.nameOf(victim), culprit.x, culprit.z, g.time);
        crimes++;rememberName(culprit);if(g.actor(victim)!=null)rememberName(g.actor(victim));
        // Honor (GDD 3.5): the act weighs on the player's conscience whether or not anyone saw it - against innocents only
        // (not an outlaw, not someone who was attacking them); looting the dead is its own act, not a robbery as well
        if (culprit == g.player && g.honor != null) {
            if (where instanceof rpg.objects.Corpse body && kind.equals("THEFT")) { if (innocent(victim)) g.honor.lootedCorpse(body.id); }
            else g.honor.crime(kind, victim, value, innocent(victim));
        }
        String jur = where != null ? where.jurisdiction() : DevMap.jurisdiction(culprit.x, culprit.z);
        var ev = g.state.commit("CrimeCommitted", culprit.id, victim, jur, cause, kind + " value=" + value);
        List<Npc> seen = g.ai.witnesses(culprit);
        Actor affected=g.actor(victim);
        seen.removeIf(w -> g.ai.stealth.exposure(w,culprit)<=0 || (where!=null
                ? !g.vox.sightCell(w.x,w.eyeY(),w.z,(int)Math.floor(where.x),(int)Math.floor(where.y),(int)Math.floor(where.z))
                : affected!=null && affected!=culprit && !g.vox.sight(w.x,w.eyeY(),w.z,affected.x,affected.chestY(),affected.z)));
        // under Disguise Self, the witnesses saw a stranger: the crime is known, the culprit is not (no bounty comes of it)
        long who = culprit.disguiseT > 0 ? 0 : culprit.id;
        // under Friends, a friend saw nothing they will tell
        seen.removeIf(w -> w.friendUntil > g.time && culprit == g.player);
        for (Npc w : seen) {
            w.remember(new Memory(kind, who, victim, jur, g.state.minutes, 1f, "witnessed", ev.id(), value, kind.toLowerCase() + " at " + (where == null ? "?" : where.name) + (who == 0 ? " (by a stranger)" : "")), 24);
            w.memories.get(w.memories.size()-1).witness=w.id;
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
            if (seen.isEmpty()) g.chat.crime("unseen " + kind, "Nobody saw your " + crimeWord(kind) + " in " + jur + ".");
            else g.chat.crime("seen " + kind, names(seen) + (seen.size() == 1 ? " saw" : " all saw") + " your " + crimeWord(kind) + " in " + jur + ". You will be wanted if it is reported to the watch.");
        }
        return seen.size();
    }

    private void rememberName(Actor actor){g.rememberName(actor.id,actor.name);}

    private static String names(List<Npc> l) { StringBuilder b = new StringBuilder(); for (Npc n : l) b.append(b.length() > 0 ? ", " : "").append(n.name); return b.toString(); }

    private boolean lawfulForce(Actor a, Actor v) {
        if (rpg.ai.AiSystem.alliedWatch(a,v))return true;
        if (a == null || v == null || a == v || !(a == g.player || a instanceof Npc)) return true;
        if (a.cs.lastAttacker == v.id && g.time - a.cs.lastHurt < 15) return true;
        if (v instanceof Npc n && (n.role == Npc.Role.BANDIT || n.role == Npc.Role.BRUTE || n.posse)) return true;
        if (a instanceof Npc officer && (officer.role == Npc.Role.GUARD || officer.role == Npc.Role.LAWMAN) && bounty(v.id, officer.homeJurisdiction) > 0) return true;
        return a == g.player && g.bountyBoard.authorized(v.id);
    }

    /** Player and human NPC offenses use the same witness/report/bounty path. */
    public void onHit(Actor a, Actor v, long cause) {
        g.ai.sawHurt(a, v);
        if(a.summonOwner!=0) { Actor owner=g.actor(a.summonOwner); if(owner==null)return; a=owner; }
        if(v.summonOwner!=0)return;
        if (!(v == g.player || v instanceof Npc) || lawfulForce(a, v)) return;
        Assault key = new Assault(a.id, v.id);
        Double last = assaultAt.get(key);
        if (last != null && g.time - last < 10) return;
        assaultAt.put(key, g.time);
        // a bare-handed brawl is disorderly conduct; a weapon makes it assault
        crime(a, a.main() == null ? "DISORDERLY" : "ASSAULT", v.id, 0, null, cause);
    }

    public void onKilled(Actor victim, Actor killer, long cause) {
        if(victim.summonOwner!=0)return;
        if(victim instanceof rpg.monster.Monster)return;   // killing a monster is never a crime (gamefixes)
        if(killer!=null && killer.summonOwner!=0)killer=g.actor(killer.summonOwner);
        if(killer!=null && killer.domain!=astratto.ai.AgentDomain.HUMAN && (victim==g.player || victim instanceof Npc)) {
            for(Npc observer:g.ai.witnesses(killer)) if(observer.distTo(victim)<25 && g.vox.sight(observer.x,observer.eyeY(),observer.z,victim.x,victim.chestY(),victim.z)) {
                observer.remember(new Memory("BEAST_ATTACK",killer.id,victim.id,DevMap.jurisdiction(victim.x,victim.z),g.state.minutes,1,"witnessed",cause,0,"saw a creature kill "+victim.name),24);
                g.ai.say(observer,"That beast killed "+victim.name+"!"); observer.fearOf=killer.id;observer.fearX=killer.x;observer.fearZ=killer.z;observer.fearSeenAt=g.time;
            }
            return;
        }
        if ((victim == g.player || victim instanceof Npc) && !lawfulForce(killer, victim)) crime(killer, "MURDER", victim.id, murderCharge(victim), null, cause);
    }

    public void closeCase(Actor offender, String jurisdiction) { settle(offender, jurisdiction); }

    // ------------------------------------------------------------------ reports and bounties
    /** A witness, in person, tells a lawman (GDD 3.5). Validated: a real witness, the right jurisdiction, close enough, alive. */
    public Result report(Npc witness, Memory m, Npc lawman) {
        String why = null;
        if(m.withheld)why="this eyewitness withheld the report";
        else if (m.settled) why = "the case has already been settled";
        else if (!m.witnessed()) why = "a rumor is not evidence";
        else if (m.reported) why = "already reported";
        else if(m.relays!=0 || m.witness!=0 && m.witness!=witness.id || !witness.memories.stream().anyMatch(owned->owned==m || owned.sameIncident(m) && owned.witnessed()))why="not this person's eyewitness account";
        else if(lawman.role!=Npc.Role.LAWMAN && lawman.role!=Npc.Role.GUARD)why="not an officer";
        else if (!lawman.alive || !witness.alive) why = "someone is dead";
        else if (!lawman.homeJurisdiction.equals(m.jurisdiction)) why = lawman.name + " has no authority in " + m.jurisdiction;
        else if (witness.distTo(lawman) > 2.8f || !g.vox.sight(witness.x,witness.eyeY(),witness.z,lawman.x,lawman.eyeY(),lawman.z)) why = "cannot speak to the officer here";
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
            g.ai.say(witness, m.detail.contains("body") ? "I found a dead body. Please send someone." : "I found " + m.detail.replaceFirst("^found ", "") + ". Please send someone.");
            g.ai.say(lawman, "I'll send the guards.");
            return Result.ok();
        }
        if (!reportedCrimes.add(m.eventId)) {
            m.reported = true;
            g.state.commit("TestimonyAdded", witness.id, m.subject, m.jurisdiction, m.eventId, m.kind + " already reported; " + witness.name + " confirms it");
            return Result.ok("already reported");
        }
        int add = charge(m);
        if (m.subject == g.player.id && g.honor != null && g.honor.high()) add = Math.max(1, add / 2);
        int before = bounty(m.subject, m.jurisdiction);
        int after = before + add;
        bounties.computeIfAbsent(m.jurisdiction, k -> new HashMap<>()).put(m.subject, after);
        m.reported = true;
        reports++;
        var ev = g.state.commit("CrimeReported", witness.id, m.subject, m.jurisdiction, m.eventId, m.kind + " reported to " + lawman.name + "; bounty " + before + " -> " + after);
        g.state.commit("BountyChanged", lawman.id, m.subject, m.jurisdiction, ev.id(), before + " -> " + after);
        // the lawman's own account, as he would tell it (the record above keeps the numbers)
        String line = String.format("%s came to %s about %s - %s. That puts the bounty on %s at %d coins", witness.name, lawman.name,
                g.nameOf(m.subject), crimeWord(m.kind), g.nameOf(m.subject), after);
        reportsLog.add(line);
        if (m.subject == g.player.id) g.chat.crime("reported " + m.kind, witness.name + " told " + lawman.name + " about your " + crimeWord(m.kind) + ". Your bounty in " + m.jurisdiction + " is now " + after + " coins.");
        g.ai.say(witness, "I saw " + g.nameOf(m.subject) + " - " + m.kind.toLowerCase() + "!");
        // everyone in the jurisdiction's force now knows to look
        for (Npc n : g.npcs) if ((n.role == Npc.Role.GUARD) && n.homeJurisdiction.equals(m.jurisdiction)) n.nextThink = 0;
        return Result.ok();
    }

    public Result payBounty(Actor a, Npc to, long req) {
        String jur = to.homeJurisdiction;
        if(g.state.alreadyCommitted(req))return Result.no("This payment has already been settled.");
        if(!a.alive || !to.alive || (to.role!=Npc.Role.GUARD && to.role!=Npc.Role.LAWMAN))return Result.no("The payment cannot be received.");
        if(refusesPeace(a.id,jur))return Result.no("You chose to resist arrest. The watch will not accept payment during this fight.");
        int b = bounty(a.id, jur);
        if (b <= 0) return Result.no("You have no bounty in " + jur + ".");
        if (!a.has(Items.COINS, b)) return Result.no("You need " + b + " coins to pay your bounty.");
        ItemStack payment = new ItemStack(Items.COINS, b, to.id, false, a.id);
        if (!to.canStore(payment)) {
            // Existing unowned purses are also legitimate recipients; try both ownership groups.
            payment.owner = 0;
            if (!to.canStore(payment)) return Result.no("The watch cannot receive the payment right now.");
        }
        long purseOwner = payment.owner;
        String paidCrime="offence";int severity=-1;
        for(Npc witness:g.npcs)for(Memory memory:witness.memories)if(memory.subject==a.id && memory.crime() && !memory.settled && jur.equals(memory.jurisdiction) && charge(memory)>severity){severity=charge(memory);paidCrime=memory.kind;}
        for(ItemStack coin:a.takeItems(Items.COINS,b)){coin.owner=purseOwner;coin.stolen=false;if(!to.store(coin))throw new IllegalStateException("Validated bounty payment did not fit");}
        settle(a, jur);
        g.ai.say(to,paymentComment(paidCrime));
        g.state.commit(req, "BountyPaid", a.id, to.id, jur, 0, b + " coins to " + to.name);
        if (a == g.player) { g.chat.add("You pay your bounty of " + b + " coins. You are no longer wanted in " + jur + ".", Chat.Kind.CRIME); if (g.sfx != null) g.sfx.ui("coins", 0.6f); g.honor.event("RESTITUTION",to.id,2,"bounty:"+req,false,"paid a bounty of "+b); g.ai.rumors.deed("paid their bounty in full", to.id); }
        return Result.ok();
    }

    private static String paymentComment(String crime) {
        return switch(crime) {
            case "THEFT"->"The theft fine is paid. Keep your hands off other people's belongings.";
            case "MURDER"->"The payment is recorded. It cannot bring back the dead.";
            case "ARSON"->"The arson charge is settled. Leave the buildings standing.";
            case "ASSAULT"->"Your assault fine is settled. Put the weapon away and keep the peace.";
            case "VANDALISM"->"The damage fine is paid. Think of the people who must mend it.";
            case "TRESPASS"->"The trespass fine is paid. Respect the boundaries next time.";
            case "LIVESTOCK"->"The livestock fine is settled. Leave the animals alone.";
            case "DISORDERLY"->"That settles the disturbance. No more trouble.";
            default->"Your payment is recorded. Thank you for settling this peacefully.";
        };
    }
    public boolean resisting(long id) { return resisting.contains(id) || defiance.stream().anyMatch(key->key.startsWith(id+":")); }
    public boolean resisting(long id,String jurisdiction){return resisting.contains(id) || refusesPeace(id,jurisdiction); }
    /** A crime's kind in words a person would use (the records' THEFT is a "theft", TRESPASS "trespassing"). */
    public static String crimeWord(String kind) {
        return switch (kind) {
            case "THEFT" -> "theft"; case "ASSAULT" -> "assault"; case "MURDER" -> "killing"; case "TRESPASS" -> "trespassing";
            case "BRIBERY" -> "attempted bribery"; case "VANDALISM" -> "vandalism"; case "LOCKPICKING" -> "lock-picking"; case "ARSON" -> "arson"; case "LIVESTOCK" -> "livestock theft";
            default -> kind.toLowerCase().replace('_', ' ');
        };
    }
    /** The wanted refuse to come quietly: the officer who halted them (and the watch) take them by force. */
    public void resist(Actor p, Npc officer) { if(petty(p.id,officer.homeJurisdiction)){declineFine(p,officer);return;} if(bounty(p.id,officer.homeJurisdiction)<=0)return;defiance.add(defianceKey(p.id,officer.homeJurisdiction));if(g.ui!=null)g.ui.closeDialog(); g.state.commit("ArrestResisted", p.id, officer.id, officer.homeJurisdiction, 0, ""); }

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

    /** The guard catches up with the wanted player: Halt! Pay, come quietly, or resist. A petty bounty is a fine asked for, never a fight. */
    public Dialog halt(Npc guard, Actor p) {
        String jur = guard.homeJurisdiction;
        if(refusesPeace(p.id,jur))return null;
        boolean petty = petty(p.id, jur);
        Double at = haltedAt.get(p.id);
        if (at != null && g.time - at < 14) {
            // no answer (the challenge waved off): a serious bounty is resisted; a fine is left on the books
            if (g.time - at > 10 && bounty(p.id, jur) > 0) {
                if (petty) declineFine(p, guard); else { resisting.add(p.id); g.ai.say(guard, "Then we do this the hard way!"); }
                if (g.ui != null) g.ui.closeDialog();
            }
            return null;
        }
        haltedAt.put(p.id, g.time);
        confronted.add(new Key(p.id, jur));   // told to halt, the matter is between them now
        int b = bounty(p.id, jur);
        g.ai.say(guard, petty ? "A word with you." : "Halt!");
        float evidence = certainty(guard, p);
        String charge = evidence >= 0.85f ? "I saw what you did. " : evidence >= 0.5f ? "Witnesses have identified you. " : "There is an accusation against you. ";
        Dialog d = new Dialog(guard.name, petty ? charge + "It's a small matter: a fine of " + b + " coins in " + jur + ". Pay it and we're done."
                : charge + "You are wanted in " + jur + " for " + b + " coins. Pay, explain yourself, or come quietly.");
        d.npc = guard.id; d.standoff = true;
        d.choice("Pay " + b + " coins.", () -> { Result r = payBounty(p, guard, g.state.newRequestId()); if (r.rejected()) { g.chat.warn(r.reason()); if (petty) declineFine(p, guard); } });
        d.choice("You have the wrong person. (lie; risk jail)", () -> denyCharge(p, guard, g.state.newRequestId()));
        if (petty) d.closeChoice("Not now. (it stays on your record)", () -> declineFine(p, guard));
        else {
            d.choice("I'll come quietly.", () -> { g.honor.surrendered(guard.id); arrest(p, jur, guard); });
            d.closeChoice("Never! (resist arrest)", () -> resist(p, guard));
        }
        if (p == g.player && g.ui != null) g.ui.dialog(d);
        return d;
    }
    /** A fine put off: the officer lets it stand, the watch leaves the offender be for a while; nobody is fought over it. */
    private void declineFine(Actor p, Npc guard) {
        Key key = new Key(p.id, guard.homeJurisdiction);
        fineDeclined.put(key, g.time);
        confronted.remove(key); haltedAt.remove(p.id);
        for (Npc n : g.npcs) if ((n.role == Npc.Role.GUARD || n.role == Npc.Role.LAWMAN) && n.homeJurisdiction.equals(guard.homeJurisdiction)) {
            boolean involved=n.fightTarget==p.id || n.hunt!=null && n.hunt.target==p.id;
            if(n.hunt!=null && n.hunt.target==p.id)n.hunt=null;
            if(!involved || n.fightTarget!=0 && n.fightTarget!=p.id)continue;
            n.fightTarget = 0; n.goal = rpg.ai.Brain.Goal.IDLE; n.hasGoalPos = false; n.nextThink = 0;
            g.combat.setBlock(n, false);
        }
        g.ai.say(guard, "Suit yourself. It stays on the books until it's paid.");
        g.state.commit("FineDeclined", p.id, guard.id, guard.homeJurisdiction, 0, bounty(p.id, guard.homeJurisdiction) + " coins left owing");
    }

    /** A guard beating a wanted offender down arrests them instead of killing them. */
    public boolean arrestInsteadOfKill(Actor attacker, Actor victim) {
        if (!(attacker instanceof Npc n) || (n.role != Npc.Role.GUARD && n.role != Npc.Role.LAWMAN)) return false;
        if (bounty(victim.id, n.homeJurisdiction) <= 0 || refusesPeace(victim.id,n.homeJurisdiction)) return false;
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
        // (0.2.2, gamefixes) a bounty worth more than everything the prisoner owns (their gold and the worth of their things
        // together): all of it is forfeit to the crown, for good - sold off through the town's store (burnt if there is no
        // room), never held for them and never to be bought back from the undertaker. What it fetched comes off the time.
        int forfeited = 0;
        if (a == g.player && b > worth(escrow.contents) + paid) {
            var shop = lawman != null || by != null ? g.econ.localShop(lawman != null ? lawman : by) : null;
            for (var item : new ArrayList<>(escrow.contents.stacks())) {
                forfeited += item.def.value * item.amount;
                item.owner = shop == null ? 0 : shop.merchant; item.stolen = false;
                if (shop == null || !shop.stock.add(item)) g.state.burn(item.def, item.amount);
            }
            escrow.contents.clear(); escrow.custodySlots.clear();
            paid = Math.min(bill, paid + forfeited);
            g.state.commit("PossessionsForfeit", a.id, lawman == null ? 0 : lawman.id, jur, forfeited, "bounty " + b + " exceeded everything owned");
            g.chat.add("Your bounty was worth more than everything you own. All of it is forfeit to " + jur + ".", Chat.Kind.CRIME);
        }
        double duration=Math.min(180,30+b/10)+Math.ceil((bill-paid)/10.0);
        g.state.commit("PrisonFinePaid",a.id,lawman==null?0:lawman.id,jur,0,"bounty+3%="+bill+" paid="+paid+" shortfall served="+(bill-paid));
        var cell = rpg.world.Jails.forJurisdiction(jur);
        float cx = cell != null ? cell.x() : lawman != null ? lawman.postX - 2 : a.x;
        float cz = cell != null ? cell.z() : lawman != null ? lawman.postZ + 3 : a.z;
        g.teleport(a, cx, DevMap.FLOOR, cz);
        if (a == g.player && cell != null) g.controller.snapTo((float) Math.atan2(-(cell.gateX() + 0.5f - cx), -(cell.frontZ() + 0.5f - cz)));
        // taken in, the prisoner's wounds are seen to (gamefixes: they bled out in the cell): whole again, the bleeding
        // stopped, any fire on them out
        a.hp = a.maxHp();
        if (g.gore != null) g.gore.staunch(a);
        a.burnSince = -1;
        if (g.chemistry != null) g.chemistry.cleanse(a);
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
            sentenceBounty = b;
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

    /** What a held inventory is worth: its gold, and each thing at its value (bags with what is in them). */
    private static int worth(rpg.item.Inventory inv) {
        int w = 0;
        for (ItemStack s : inv.stacks()) { w += s.def.value * s.amount; if (s.inner != null) w += worth(s.inner); }
        return w;
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
        Map<String, Object> m = new HashMap<>();m.put("_defiance",new ArrayList<>(defiance));
        m.put("_reported", new ArrayList<Object>(reportedCrimes));
        m.put("_custody", Map.of("until", sentenceUntil, "jur", sentenceJurisdiction, "admissions", jailAdmissions, "bounty", sentenceBounty));
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
        for(Object entry:astratto.util.Json.list(m,"_defiance"))defiance.add(entry.toString());
        for (var e : m.entrySet()) {
            if(e.getKey().equals("_defiance"))continue;
            if (e.getKey().equals("_custody")) {
                Map<String, Object> custody = (Map<String, Object>) e.getValue();
                sentenceUntil = astratto.util.Json.num(custody, "until", -1);
                sentenceJurisdiction = astratto.util.Json.str(custody, "jur", "");
                jailAdmissions = astratto.util.Json.integer(custody, "admissions", 0);
                sentenceBounty = Math.max(0, astratto.util.Json.integer(custody, "bounty", 0));
                // Older saves kept the original amount in the arrest event, before custody stored it directly.
                if (!custody.containsKey("bounty") && sentenceUntil >= 0) {
                    var events = g.state.all();
                    for (int i = events.size() - 1; i >= 0; i--) {
                        var event = events.get(i);
                        if (!event.type().equals("Arrested") || event.subject() != g.player.id || !sentenceJurisdiction.equals(event.jurisdiction())) continue;
                        String payload = event.payload();
                        if (payload.startsWith("bounty ")) {
                            int end = payload.indexOf(' ', 7);
                            try { sentenceBounty = Math.max(0, Integer.parseInt(payload.substring(7, end < 0 ? payload.length() : end))); }
                            catch (NumberFormatException ignored) { }
                        }
                        break;
                    }
                }
                continue;
            }
            if (e.getKey().equals("_reported")) { for (Object o : (List<Object>) e.getValue()) reportedCrimes.add(((Number) o).longValue()); continue; }
            Map<Long, Integer> j = new HashMap<>();
            for (var b : ((Map<String, Object>) e.getValue()).entrySet()) j.put(Long.parseLong(b.getKey()), ((Number) b.getValue()).intValue());
            bounties.put(e.getKey(), j);
        }
    }

    public void clear() { defiance.clear();confronted.clear(); fineDeclined.clear(); lastKingdom = ""; reminder = ""; posses.clear(); roofWarnings.clear();roofCheck=0; reportedCrimes.clear(); bounties.clear(); resisting.clear(); haltedAt.clear(); assaultAt.clear(); settledAt.clear(); sentenceUntil = -1; sentenceJurisdiction = ""; jailAdmissions = 0; sentenceBounty = 0; }

    /** Below this a bounty costs nothing at death; from it the law takes its share of what the dead carried (gamefixes). */
    public static final int FORFEIT_FROM = 25;
    /** The share of what was carried the law seizes at death for a bounty: none to 25 G, a twentieth just past it, all of it at 500 G. */
    public static float forfeitShare(int bounty) { return bounty <= FORFEIT_FROM ? 0 : Math.min(1, 0.05f + 0.95f * (bounty - FORFEIT_FROM) / 475f); }

    /**
     * The wanted die (gamefixes): with a bounty over 25 G the law of the kingdom that wants them most seizes a share of
     * everything they carried and wore - scaling with the whole bounty, all of it at 500 G - before what is left goes to
     * the grave as any death's does (the undertaker's buy-back and the grave are untouched; a death with no bounty, or a
     * small one, loses nothing here). Which things go is the luck of the draw, the share counted by worth. Stolen goods
     * among them go back to their owners; the rest to that kingdom's lawman. Returns how many things were taken.
     */
    public int forfeitOnDeath(Actor dead) {
        int total = 0; String worst = null; int most = 0;
        for (var e : bounties.entrySet()) { int b = e.getValue().getOrDefault(dead.id, 0); total += b; if (b > most) { most = b; worst = e.getKey(); } }
        float share = forfeitShare(total);
        if (share <= 0 || worst == null) return 0;
        // everything carried and worn, by worth
        record Held(rpg.item.Inventory grid, int index, rpg.item.ItemDef.Slot slot, ItemStack stack) {}
        List<Held> held = new ArrayList<>();
        for (var grid : dead.grids()) for (int i = 0; i < grid.size(); i++) { ItemStack s = grid.get(i); if (s != null) held.add(new Held(grid, i, null, s)); }
        for (var slot : rpg.item.ItemDef.Slot.values()) { ItemStack s = dead.eq.get(slot); if (s != null) held.add(new Held(null, -1, slot, s)); }
        long worth = 0; for (Held h : held) worth += Math.max(1, h.stack.value());
        long due = share >= 1 ? Long.MAX_VALUE : Math.round(worth * share);
        java.util.Collections.shuffle(held, new java.util.Random(dead.id * 31 + (long) g.time));
        // a bag (a worn backpack) goes whole, its contents with it: the bag after its contents, so neither is counted twice
        held.sort((a, b) -> Boolean.compare(a.stack.inner != null, b.stack.inner != null));
        var seized = new rpg.item.Inventory(20, 20);
        long taken = 0; int count = 0;
        for (Held h : held) {
            if (taken >= due) break;
            boolean stillThere = h.slot != null ? dead.eq.get(h.slot) == h.stack : h.grid.get(h.index) == h.stack;
            if (!stillThere) continue;
            if (h.slot != null) dead.eq.set(h.slot, null); else h.grid.set(h.index, null);
            taken += Math.max(1, h.stack.value()); count++;
            if (!seized.add(h.stack)) g.objects.drop(h.stack, dead.x, DevMap.FLOOR + 0.05f, dead.z);
        }
        int returned = Property.confiscate(g, seized);
        Npc lawman = g.ai.lawmanFor(worst);
        for (ItemStack s : new ArrayList<>(seized.stacks())) {
            s.owner = lawman == null ? 0 : lawman.id;
            if (lawman == null || !lawman.store(s)) { var evidence = g.objects.custody; if (!evidence.contents.add(s)) g.objects.drop(s, evidence.x, evidence.y, evidence.z); }
        }
        g.state.commit("BountyForfeit", lawman == null ? 0 : lawman.id, dead.id, worst, 0,
                String.format("bounty %d G: %.0f%% of %d worth seized (%d things, %d stolen returned)", total, share * 100, worth, count, returned));
        if (dead == g.player) g.chat.add(String.format("The law of %s seizes %s of what you carried for your bounty of %s G.", worst,
                share >= 1 ? "everything" : count == 1 ? "one thing" : count + " things", rpg.item.Currency.text(total)), Chat.Kind.CRIME);
        return count;
    }

    /** Death settles justice everywhere; personal memories, Honor and other people's fights remain. */
    public void playerRespawned() {
        roofWarnings.keySet().removeIf(key->key.actor==g.player.id);
        String heldIn = sentenceJurisdiction;
        sentenceUntil = -1; sentenceJurisdiction = ""; sentenceBounty = 0;
        if (!heldIn.isEmpty()) openEmptyCell(heldIn);
        long id = g.player.id;
        for (var charges : bounties.values()) charges.remove(id);
        defiance.removeIf(key->key.startsWith(id+":"));
        confronted.removeIf(k -> k.offender() == id);
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
