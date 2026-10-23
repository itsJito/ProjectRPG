package rpg.ai;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import rpg.Chat;
import rpg.Rpg;
import rpg.actor.Actor;
import rpg.world.DevMap;

/**
 * Word of mouth (gamefixes: the rumor overhaul; GDD 3.3 social knowledge, 3.5 public Honor). What people know about
 * each other and about the player travels only by actual meetings: two people passing in the street stop a moment and
 * trade their news, friends who sit down to talk trade all of it, and a witness to a killing shouts it to everyone in
 * earshot as they run for the law. Each telling is weaker than the last (a rumor from a rumor is a rumor of a rumor),
 * and nobody knows what nobody told them - there is no global reputation.
 *
 * <p>Good deeds travel too (a greeting, a finished errand, an offering, a beaten foe spared): the people who saw them
 * remember them, and pass them on. What a person has heard, good and bad, makes up their opinion of the player, and that
 * is what the player meets: a wary look and a refusal at the counter, a frightened villager backing away, a guard who
 * keeps his eye on them, or a warm greeting from someone they never met. The player learns it the way anyone would - by
 * overhearing it, or by being treated differently.
 */
public final class Rumors {
    private final Rpg g;
    private final AiSystem ai;
    /** When each pair last traded news in passing (one key per unordered pair). */
    private final Map<Long, Double> lastPass = new HashMap<>();
    private double nextScan;
    public long passed, overheard, shouted, deeds;

    Rumors(Rpg g, AiSystem ai) { this.g = g; this.ai = ai; }

    /** How much a piece of news matters (what is passed on first): killings above beatings above thefts; good deeds lightly. */
    static float weight(Memory m) {
        return switch (m.kind) {
            case "MURDER" -> 10; case "ASSAULT" -> 5; case "LIVESTOCK" -> 3; case "THEFT" -> 2.5f; case "LOCKPICKING", "VANDALISM" -> 1.5f;
            case "DEED" -> 1.2f;
            default -> 0;
        } * m.confidence;
    }

    /** Whether a memory is news worth telling (a crime, or a good deed). */
    static boolean news(Memory m) { return m.crime() || m.kind.equals("DEED"); }

    /**
     * What an NPC makes of the player from everything they know (seen and heard), -1 (dread) .. +1 (warmth). A killing
     * outweighs a dozen kindnesses; a rumor counts for less than what one saw.
     */
    public float opinion(Npc n) {
        float o = 0;
        long me = g.player.id;
        for (Memory m : n.memories) {
            if (m.subject != me) continue;
            float c = m.confidence * (m.witnessed() ? 1 : 0.75f);
            o += switch (m.kind) {
                case "MURDER" -> -0.9f * c; case "ASSAULT" -> m.victim == n.id ? -0.7f * c : -0.35f * c; case "THEFT" -> m.victim == n.id ? -0.45f * c : -0.2f * c;
                case "LIVESTOCK" -> -0.25f * c; case "LOCKPICKING", "VANDALISM" -> -0.15f * c;
                case "DEED" -> 0.12f * c;
                default -> 0;
            };
        }
        if (n.greeted) o += 0.05f;
        return Math.max(-1, Math.min(1, o));
    }

    /** Whether they dread the player: they know of a killing or an attack by them, and think the worse of them for it. */
    public boolean afraid(Npc n) {
        if (n.combatant() || n.role == Npc.Role.FENCE) return false;
        for (Memory m : n.memories) if (!m.settled && m.subject == g.player.id && (m.kind.equals("MURDER") || m.kind.equals("ASSAULT") && m.victim == n.id)) return opinion(n) < -0.3f;
        return false;
    }

    // ------------------------------------------------------------------ good deeds, seen
    /** A good deed by the player: everyone who sees it remembers it (it will travel as they talk). */
    public void deed(String what, long beneficiary) {
        for (Npc w : ai.witnesses(g.player)) {
            w.remember(new Memory("DEED", g.player.id, beneficiary, DevMap.jurisdiction(w.x, w.z), g.state.minutes, 1f, "witnessed", 0, 0, what), 24);
            w.knowsPlayer = true;
            deeds++;
        }
        if (g.actor(beneficiary) instanceof Npc b && b.alive) {
            b.remember(new Memory("DEED", g.player.id, b.id, DevMap.jurisdiction(b.x, b.z), g.state.minutes, 1f, "witnessed", 0, 0, what), 24);
            b.knowsPlayer = true;
        }
    }

    // ------------------------------------------------------------------ the spreading
    /** Twice a second: people who pass close, both free for a word, trade their freshest news (each pair at most once a minute and a half). */
    public void update() {
        if (g.time < nextScan) return;
        nextScan = g.time + 0.5;
        List<Npc> free = new ArrayList<>();
        for (Npc n : g.npcs) if (n.alive && n.loaded && freeForWord(n)) free.add(n);
        for (int i = 0; i < free.size(); i++) for (int j = i + 1; j < free.size(); j++) {
            Npc a = free.get(i), b = free.get(j);
            if (a.distTo(b) > 4.5f || !g.vox.sight(a.x, a.eyeY(), a.z, b.x, b.eyeY(), b.z)) continue;
            long key = Math.min(a.id, b.id) * 100003L + Math.max(a.id, b.id);
            Double last = lastPass.get(key);
            if (last != null && g.time - last < 90) continue;
            Memory best = hottest(a, b), other = hottest(b, a);
            Npc teller = a, hearer = b;
            if (other != null && (best == null || weight(other) > weight(best))) { best = other; teller = b; hearer = a; }
            if (best == null) continue;
            if (tell(teller, hearer, best, false)) { lastPass.put(key, g.time); passed++; }
        }
    }

    private boolean freeForWord(Npc n) {
        return switch (n.goal) {
            case FIGHT, FLEE, SLEEP, HELD, SEARCH, STEAL, LOOT, COLLECT, HIDE -> false;
            default -> n.reacting ? false : n.role != Npc.Role.BANDIT && n.role != Npc.Role.BRUTE;
        };
    }

    /** The weightiest thing the teller knows that the hearer does not (and that is not about the hearer), or null. */
    Memory hottest(Npc teller, Npc hearer) {
        Memory best = null;
        for (Memory m : teller.memories) {
            if (!news(m) || m.subject == hearer.id || m.confidence < 0.25f || knows(hearer, m)) continue;
            if (best == null || weight(m) > weight(best)) best = m;
        }
        return best;
    }

    private static boolean knows(Npc n, Memory m) {
        for (Memory k : n.memories) if (k.sameIncident(m) && (k.witnessed() || k.confidence >= m.confidence * 0.7f)) return true;
        return false;
    }

    /** Speech uses mouths/ears, distance and the same wall muffle as the shared hearing model. */
    private boolean audible(Actor speaker, Actor listener, float reach) {
        if (!speaker.alive || !listener.alive || speaker instanceof Npc n && !n.loaded || listener instanceof Npc n && !n.loaded) return false;
        if (listener instanceof Npc n && n.goal == Brain.Goal.SLEEP) reach *= 0.5f;
        float dx = speaker.x - listener.x, dy = speaker.eyeY() - listener.eyeY(), dz = speaker.z - listener.z;
        float distance = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distance >= reach) return false;
        if (distance > 2.5f && !g.vox.clear(speaker.x, speaker.eyeY(), speaker.z, listener.x, listener.eyeY(), listener.z)) reach *= Stealth.WALL_MUFFLE;
        return distance < reach;
    }

    /**
     * One piece of news told: the hearer remembers it as rumor (weaker), says something about it, and - if it is about
     * the player and the player is in plain sight - both look at them. If the player is close enough to hear, they do.
     */
    public boolean tell(Npc teller, Npc hearer, Memory m, boolean shout) {
        if (teller == hearer || !news(m) || m.subject == hearer.id || m.confidence < 0.25f ||
                !teller.memories.contains(m) || knows(hearer, m) || !audible(teller, hearer, shout ? 14 : 4.5f)) return false;
        hearer.remember(m.rumorFrom(teller), 24);
        if (m.subject == g.player.id && teller.knowsPlayer) hearer.knowsPlayer = true;
        g.state.commit("RumorShared", teller.id, hearer.id, DevMap.jurisdiction(hearer.x, hearer.z), m.eventId, m.kind + " by " + g.nameOf(m.subject) + (shout ? " (shouted)" : ""));
        ai.shared++;
        String line = line(teller, m);
        boolean newSpeech = !line.equals(teller.speech) || g.time >= teller.speechUntil;
        ai.say(teller, line);
        teller.gossipUntil = g.time + 3.5; hearer.gossipUntil = g.time + 3.5;
        hearer.wantYaw = (float) Math.atan2(-(teller.x - hearer.x), -(teller.z - hearer.z));
        teller.wantYaw = (float) Math.atan2(-(hearer.x - teller.x), -(hearer.z - teller.z));
        boolean aboutPlayer = m.subject == g.player.id;
        if (aboutPlayer && g.player.alive && ai.canSee(hearer, g.player) && hearer.distTo(g.player) < 18) {
            // a look over the shoulder at the one they are talking about
            hearer.wantYaw = (float) Math.atan2(-(g.player.x - hearer.x), -(g.player.z - hearer.z));
            ai.reply(hearer, m.kind.equals("DEED") ? "Is that them? Huh." : m.kind.equals("MURDER") ? "That's them... over there." : "Them? I'll watch my purse.");
        }
        if (newSpeech && audible(teller, g.player, shout ? 14 : 8)) {
            g.chat.add("[Overheard] " + teller.name + ": \"" + line + "\"", Chat.Kind.DIALOGUE);
            overheard++;
        }
        return true;
    }

    /** What the teller says about it (in their own words, more certain the closer to the source they were). */
    String line(Npc teller, Memory m) {
        String who = m.subject == g.player.id ? (teller.knowsPlayer ? g.player.name : "that stranger") : g.nameOf(m.subject);
        if (m.subject == 0) who = "someone";
        String victim = m.victim != 0 ? g.nameOf(m.victim) : "someone";
        boolean saw = m.witnessed();
        String hedge = saw ? "I saw it myself: " : m.witness != 0 ? "I heard " + g.nameOf(m.witness) + " saw it: "
                : m.confidence > 0.55f ? "Did you hear? " : "They're saying ";
        return switch (m.kind) {
            case "MURDER" -> hedge + who + " killed " + victim + "!";
            case "ASSAULT" -> hedge + who + " attacked " + victim + ".";
            case "THEFT" -> hedge + who + " robbed " + victim + ".";
            case "LIVESTOCK" -> hedge + who + " killed one of the farm animals.";
            case "LOCKPICKING" -> hedge + who + " was picking locks.";
            case "VANDALISM" -> hedge + who + " smashed things up.";
            case "DEED" -> (saw ? "I saw " : "I heard ") + who + " " + m.detail + ". Decent of them.";
            default -> "...";
        };
    }

    /**
     * A witness to violence raises the alarm as they go (a shout, not a chat): everyone free in earshot hears it at once,
     * as a rumor, with no pause to trade news.
     */
    public void alarm(Npc witness, Memory m) {
        if (!m.witnessed() || !(m.kind.equals("MURDER") || m.kind.equals("ASSAULT"))) return;
        if (g.time - witness.lastAlarmAt < 6) return;
        witness.lastAlarmAt = g.time;
        int told = 0;
        for (Npc o : g.npcs) {
            if (o == witness || !o.alive || !o.loaded || o.distTo(witness) > 14 || knows(o, m) || o.id == m.subject) continue;
            if (o.role == Npc.Role.BANDIT || o.role == Npc.Role.BRUTE) continue;
            if (tell(witness, o, m, true)) { o.nextThink = 0; told++; }
        }
        if (told > 0) shouted++;
    }
}
