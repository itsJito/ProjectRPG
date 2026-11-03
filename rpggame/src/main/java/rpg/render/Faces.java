package rpg.render;

import rpg.Rpg;
import rpg.actor.Actor;
import rpg.ai.Brain;
import rpg.ai.Npc;

/**
 * The faces of people (world figures and Chatbox portraits share it). (0.3.0, gamefixes: faces more animated, capable of
 * expressions beyond anger, convincing by trickery) A face is a small set of overlays on the painted head - brows,
 * lids, mouth - but it reads as alive because of what drives them:
 *
 * <ul>
 * <li><b>A baseline mood</b> read from the person's state: fighting, fleeing, searching, hurt, tired, hungry, chatting,
 *     how they regard whoever they are talking to.</li>
 * <li><b>Reactions</b> laid over it by what happens - a start at a shout or a crime (surprise first, then the feeling it
 *     turns into), pleasure at a kind word, a sneer at a jibe, sorrow at a burial - each held a moment and let go.</li>
 * <li><b>Blinks</b> at each person's own irregular rhythm, faster when afraid, and</li>
 * <li><b>Lips</b> that move with the words actually being said: open on the vowels, shut on m, b and p, half between.</li>
 * </ul>
 */
public final class Faces {
    private Faces() {}
    public enum Mood { NEUTRAL, ANGRY, AFRAID, HURT, WARM, SUSPICIOUS, HAPPY, SAD, SURPRISED, DISGUSTED, TIRED, THINKING }

    /** A reaction: this mood, for so long (game seconds), then the baseline again; a start becomes what it turns into. */
    public static void react(Rpg g, Actor a, Mood mood, double seconds) {
        if (g == null || a == null || !a.alive) return;
        a.emote = mood.ordinal(); a.emoteUntil = g.time + seconds; a.emoteSince = g.time;
    }
    /** A start (surprise for a beat), then the feeling it turns into for a while. */
    public static void startle(Rpg g, Actor a, Mood then, double seconds) {
        react(g, a, Mood.SURPRISED, 0.55);
        a.emoteNext = then.ordinal(); a.emoteNextFor = seconds;
    }

    public static Mood mood(Rpg g, Actor actor) {
        if (g == null || !actor.alive) return Mood.NEUTRAL;
        if (g.time - actor.cs.lastHurt < 0.7) return Mood.HURT;
        if (actor.emote >= 0) {
            if (g.time < actor.emoteUntil) return Mood.values()[actor.emote];
            if (actor.emoteNext >= 0) { int next = actor.emoteNext; actor.emoteNext = -1; react(g, actor, Mood.values()[next], actor.emoteNextFor); return Mood.values()[next]; }
            actor.emote = -1;
        }
        if (actor instanceof Npc npc) {
            if (npc.goal == Brain.Goal.FLEE || npc.goal == Brain.Goal.HIDE) return Mood.AFRAID;
            if (npc.goal == Brain.Goal.FIGHT) return Mood.ANGRY;
            if (npc.goal == Brain.Goal.INVESTIGATE || npc.goal == Brain.Goal.WATCH || npc.goal == Brain.Goal.SEARCH) return Mood.SUSPICIOUS;
            if (npc.hp < npc.maxHp() * 0.4f) return Mood.HURT;
            if (npc.gossipUntil > g.time || npc.chatUntil > g.time && g.ai.rumors.opinion(npc) > 0.1f) return Mood.WARM;
            // talking with the player: how they regard them shows
            if (g.ui != null && g.ui.dialogWith(npc.id)) {
                float o = g.ai.rumors.opinion(npc);
                return o > 0.35f ? Mood.HAPPY : o < -0.35f ? Mood.DISGUSTED : ((long) (g.time / 3.7) + npc.id) % 3 == 0 ? Mood.THINKING : Mood.NEUTRAL;
            }
            if (npc.goal == Brain.Goal.SOCIALIZE && npc.speech != null) return Mood.HAPPY;
            if (npc.fatigue > 0.75f || npc.hunger > 0.8f) return Mood.TIRED;
        } else if (actor.hp < actor.maxHp() * 0.3f) return Mood.HURT;
        return Mood.NEUTRAL;
    }

    /**
     * Whether the eyes are shut in a blink this instant: each person blinks on their own rhythm (two to six seconds,
     * varying blink to blink), a blink a tenth of a second; the afraid blink more often.
     */
    public static boolean blinking(Actor a, double time, Mood mood) {
        double rate = mood == Mood.AFRAID || mood == Mood.SURPRISED ? 0.55 : 1;
        long seed = a.id * 0x9E3779B97F4A7C15L;
        double t = time / rate + (seed >>> 40) % 1000 / 250.0;
        long beat = (long) Math.floor(t / 3.1);
        double within = t - beat * 3.1;
        double offset = ((beat * 0x632BE59BD9B4E019L + seed) >>> 44) % 1000 / 1000.0 * 2.2;
        return within > offset && within < offset + 0.11;
    }

    /** The mouth's opening (0 shut .. 3 wide) for a line being said: by the letter the words have reached. */
    public static int speech(double time, String line) {
        if (line == null || line.isEmpty()) return 0;
        char c = Character.toLowerCase(line.charAt((int) (time * 14) % line.length()));
        return switch (c) { case 'a', 'o' -> 3; case 'e', 'i', 'u', 'y' -> 2; case 'm', 'b', 'p', ' ', '.', ',', '-' -> 0; default -> 1; };
    }
}
