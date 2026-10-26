package rpg.render;

import rpg.Rpg;
import rpg.actor.Actor;
import rpg.ai.Brain;
import rpg.ai.Npc;

/** The same visible emotional state is used on world figures and Chatbox portraits. */
public final class Faces {
    private Faces() {}
    public enum Mood { NEUTRAL, ANGRY, AFRAID, HURT, WARM, SUSPICIOUS }
    public static Mood mood(Rpg g, Actor actor) {
        if (g == null || !actor.alive) return Mood.NEUTRAL;
        if (g.time - actor.cs.lastHurt < 0.7) return Mood.HURT;
        if (actor instanceof Npc npc) {
            if (npc.goal == Brain.Goal.FLEE || npc.goal == Brain.Goal.HIDE) return Mood.AFRAID;
            if (npc.goal == Brain.Goal.FIGHT) return Mood.ANGRY;
            if (npc.goal == Brain.Goal.INVESTIGATE || npc.goal == Brain.Goal.WATCH || npc.goal == Brain.Goal.SEARCH) return Mood.SUSPICIOUS;
            if (npc.gossipUntil > g.time || npc.chatUntil > g.time && g.ai.rumors.opinion(npc) > 0.1f) return Mood.WARM;
        }
        return Mood.NEUTRAL;
    }
    private static final int[] MOUTH = {0,1,2,3,2,1};
    public static int speech(double time, boolean talking) {
        if (!talking) return 0;
        return MOUTH[(int)(time / 0.1) % 6];
    }
}
