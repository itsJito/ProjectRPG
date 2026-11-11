package rpg.ai;

import rpg.Rpg;
import rpg.law.Homicide;

/**
 * (0.3.9, gamefixes: homicide investigations) A guard working a murder case (rpg.law.Homicide), the way it is walked: out
 * to where the body was found; down on one knee over it for a while, reading it; then from door to door among those who
 * live or stand near, asking each what they saw - the question put, a pause, the answer given aloud; and at the end, the
 * judgement said where the officer stands. Anything more pressing (a fight, a fire, a thief in the act) comes first, and
 * the case is picked up again where it was left.
 */
final class Detective {
    private final Rpg g;
    private final Brain brain;
    Detective(Rpg g, Brain brain) { this.g = g; this.brain = brain; }

    /** The pause between the question and the answer (s). */
    static final float ANSWER_AFTER = 2.4f;

    void work(Npc n) {
        Homicide.Case c = g.law.homicide.workedBy(n.id);
        if (c == null) { n.nextThink = 0; return; }
        String victim = g.nameOf(c.victim);
        switch (c.stage) {
            case OPEN, SCENE -> {
                c.stage = Homicide.Stage.SCENE;
                if (!brain.goTo(n, c.x, c.z, false, 1.6f)) { n.sneaking = false; c.stageAt = -1; n.lastOutcome = "on the way to where " + victim + " was found"; return; }
                g.ai.desire(n, AiSystem.A_IDLE);
                n.wantYaw = (float) Math.atan2(-(c.x - n.x), -(c.z - n.z));
                n.sneaking = true;   // down on one knee over the body
                if (c.stageAt < 0) { c.stageAt = g.time; if (n.speech == null) g.ai.say(n, "Stand back. Let me see what was done here."); }
                n.lastOutcome = "examining the body of " + victim;
                if (g.time - c.stageAt >= Homicide.EXAMINE_TIME) {
                    g.law.homicide.examine(c);
                    n.sneaking = false;
                    c.stage = Homicide.Stage.CANVASS; c.stageAt = g.time; c.askingSince = -1;
                    if (c.findings.size() > 1) g.ai.say(n, c.findings.get(1));
                }
            }
            case CANVASS -> {
                n.sneaking = false;
                if (c.toAsk.isEmpty()) {
                    String verdict = g.law.homicide.conclude(c);
                    g.ai.say(n, verdict);
                    n.lastOutcome = "closed the case of " + victim + "'s killing: " + verdict;
                    n.nextThink = 0;
                    return;
                }
                Npc w = g.actor(c.toAsk.get(0)) instanceof Npc x && x.alive && x.loaded ? x : null;
                if (w == null) { c.toAsk.remove(0); c.askingSince = -1; c.stageAt = -1; return; }
                if (c.askingSince < 0) { c.askingSince = g.time; c.stageAt = -1; }
                if (g.time - c.askingSince > Homicide.ASK_PATIENCE) { c.toAsk.remove(0); c.askingSince = -1; n.lastOutcome = "could not find " + w.name + " to ask"; return; }
                n.lastOutcome = "asking " + w.name + " about " + victim + "'s killing";
                if (!brain.meet(n, w, 1.3f, 1.7f)) return;
                g.ai.desire(n, AiSystem.A_IDLE);
                n.wantYaw = (float) Math.atan2(-(w.x - n.x), -(w.z - n.z));
                w.wantYaw = (float) Math.atan2(-(n.x - w.x), -(n.z - w.z));
                if (c.stageAt < 0) {
                    c.stageAt = g.time;
                    g.ai.say(n, w.asleep() ? "Wake up. The watch. " + victim + " is dead - did you hear anything?" : "A word, " + w.name + ". " + victim + " was killed near here. What did you see?");
                } else if (g.time - c.stageAt >= ANSWER_AFTER) {
                    g.ai.say(w, g.law.homicide.ask(c, w));
                    c.askingSince = -1; c.stageAt = -1;
                }
            }
            default -> n.nextThink = 0;
        }
    }
}
