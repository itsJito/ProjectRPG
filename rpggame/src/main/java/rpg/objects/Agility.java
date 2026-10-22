package rpg.objects;

import rpg.Chat;
import rpg.Rpg;
import rpg.actor.Actor;
import rpg.skill.Skill;
import rpg.world.DevMap;

/**
 * The agility course, walked physically: start, across the log over the pit, over the climbing wall, across the
 * stepping stones, finish. Each obstacle passed in order pays XP; a whole lap pays a bonus. Falling in sends the
 * runner back to the last checkpoint. Agility's effects live in the player controller (stamina and run speed).
 */
public final class Agility {
    /** The next checkpoint expected (0: the start). */
    public int next;
    public int laps;
    private boolean started;

    public void obstacle(Rpg g, int checkpoint) {
        if (started && checkpoint == next) advance(g, checkpoint);
    }

    public void update(Rpg g) {
        Actor p = g.player;
        float[][] cps = DevMap.AGILITY_CHECKPOINTS;
        if (Math.hypot(p.x - cps[0][0], p.z - cps[0][1]) < 2.2 && p.y >= DevMap.FLOOR - 0.1f && (!started || next != 1)) {
            if (!started || next > 1) g.chat.say("You begin a lap of the agility course.");
            started = true; next = 1;
        }
        if (started && next > 0 && next < cps.length && next != 2 && Math.hypot(p.x - cps[next][0], p.z - cps[next][1]) < 1.6 && p.y >= DevMap.FLOOR - 0.1f) advance(g, next);
        boolean inPit = p.x > 113 && p.x < 122 && ((p.z > 12 && p.z < 23) || (p.z > 33 && p.z < 44));
        if (inPit && p.y < DevMap.FLOOR - 0.35f && p.alive) {
            float[] back = p.z < 30 ? cps[0] : cps[2];
            g.chat.add("You slip and fall into the water!", Chat.Kind.WARN);
            p.hp = Math.max(1, p.hp - 4);
            g.teleport(p, back[0], DevMap.FLOOR, back[1]);
            if (g.fx != null) g.fx.splash(p.x, DevMap.FLOOR, p.z);
            if (started) next = p.z < 30 ? 1 : 3;
        }
    }

    private void advance(Rpg g, int cp) {
        String[] names = {"", "the log balance", "the climbing wall", "the stepping stones"};
        var ev = g.state.commit("ObstaclePassed", g.player.id, 0, null, 0, names[cp]);
        if (cp != 2) g.prog.award(g.player, Skill.AGILITY, 14, names[cp], ev.id());
        next = cp + 1;
        if (next >= DevMap.AGILITY_CHECKPOINTS.length) {
            laps++;
            started = false; next = 0;
            var lap = g.state.commit("AgilityLap", g.player.id, 0, null, ev.id(), "lap " + laps);
            g.prog.award(g.player, Skill.AGILITY, 60, "agility lap", lap.id());
            g.chat.say("You complete a lap of the agility course. Laps: " + laps + ".");
        }
    }
}
