package rpg.ai;

import rpg.Rpg;
import rpg.actor.Actor;
import rpg.world.DevMap;

/**
 * Arms shown in the open (gamefixes: NPCs react to the player swinging a weapon, loosing arrows or casting in a town or
 * near it - each kind of person in their own way). A swing is seen; a shot or a spell is seen, or heard from closer by.
 * Each witness keeps how often they have seen it lately (it fades over a minute), and what they do follows who they are
 * and how close it came:
 * <ul>
 * <li>the watch warns, then keeps an eye on the player, and after a third display gives a last warning in person;</li>
 * <li>a knight or a hunter is unimpressed and says so; a priest or healer asks for peace; a scholar wants to see more
 *     of the magic;</li>
 * <li>a thief or a fence wants none of it and slips off;</li>
 * <li>a brute or a bandit takes a blade swung within reach as a challenge;</li>
 * <li>anyone else flinches and backs off from it close by, stares from further off, and after the third display shouts
 *     for the guard, who comes to watch.</li>
 * </ul>
 * Nothing here is a crime: blows that land, and what follows them, are the law's.
 *
 * <p>(0.2.2, gamefixes: the sheathe system) A weapon drawn is arms shown too, more quietly: in a town or city everyone
 * but the watch should go with their weapons sheathed. Drawing one in the street is noticed; carrying it drawn is noticed
 * the longer it goes on (the watch tells the bearer to put it away, and keeps an eye on them; townsfolk keep clear); a
 * swing or a shot is the stronger matter above. Putting it away settles it, and the watch says so.
 */
public final class Arms {
    public enum Kind { SWING, SHOT, SPELL, DRAW }
    private final Rpg g;
    private final AiSystem ai;
    private double rumorAt = -999;

    Arms(Rpg g, AiSystem ai) { this.g = g; this.ai = ai; }

    /** Someone showed arms: a blade swung, an arrow or bolt loosed, a spell cast. Only the player's are answered. */
    public void shown(Actor who, Kind kind) {
        if (who != g.player || !who.alive || g.notarget()) return;
        boolean town = !DevMap.WILDS.equals(DevMap.jurisdiction(who.x, who.z));
        if (kind == Kind.DRAW && !town) return;   // out in the wilds a drawn blade is only sense
        float sight = kind == Kind.SWING || kind == Kind.DRAW ? 12 : 18, hearing = kind == Kind.SWING ? 0 : kind == Kind.DRAW ? 4 : kind == Kind.SHOT ? 9 : 12;
        int startled = 0;
        for (Npc n : g.npcs) {
            if (!n.alive || !n.loaded || n.asleep() || n.summonOwner != 0 || n.goal == Brain.Goal.FIGHT || n.goal == Brain.Goal.HELD) continue;
            float d = n.distTo(who);
            if (d > sight) continue;
            // aimed at them is a fight, which combat and the law answer
            if (who.cs.lastAttacker == n.id || n.cs.lastAttacker == who.id && g.time - n.cs.lastHurt < 10) continue;
            boolean seen = ai.canSee(n, who);
            if (!seen && d > hearing) continue;
            // how often lately (fading over a minute)
            n.armsSeen = (float) (n.armsSeen * Math.exp(-(g.time - n.armsAt) / 60.0)) + (kind == Kind.DRAW ? 0.5f : 1);
            n.armsAt = g.time; n.armsKind = kind;
            if (react(n, who, kind, d, seen, town)) startled++;
        }
        if (startled > 0 && town && g.time - rumorAt > 300) {
            rumorAt = g.time;
            ai.rumors.deed(kind == Kind.SPELL ? "throwing spells about in the street" : kind == Kind.SHOT ? "loosing arrows in the town" : "swinging a blade about in the street", 0);
        }
    }

    private double carriedCheckAt;

    /**
     * A weapon carried drawn through a town (checked twice a second): each person who can see it keeps count of how long,
     * and past a few seconds - and again after a while - answers it as a display of arms, a drawn weapon's way.
     */
    public void tick() {
        Actor p = g.player;
        if (g.time - carriedCheckAt < 0.5) return;
        float dt = (float) Math.min(1, g.time - carriedCheckAt);
        carriedCheckAt = g.time;
        if (!p.alive || g.notarget() || DevMap.WILDS.equals(DevMap.jurisdiction(p.x, p.z)) || !g.combat.drawn(p)) {
            for (Npc n : g.npcs) n.drawnFor = 0;
            return;
        }
        for (Npc n : g.npcs) {
            if (!n.alive || !n.loaded || n.asleep() || n.summonOwner != 0 || n.goal == Brain.Goal.FIGHT || n.goal == Brain.Goal.HELD || n.distTo(p) > 10 || !ai.canSee(n, p)) { n.drawnFor = Math.max(0, n.drawnFor - dt); continue; }
            n.drawnFor += dt;
            if (n.drawnFor < 5) continue;
            n.drawnFor = -20;   // (answered: the next word on it comes after a good while longer)
            n.armsSeen = (float) (n.armsSeen * Math.exp(-(g.time - n.armsAt) / 60.0)) + 1;
            n.armsAt = g.time; n.armsKind = Kind.DRAW;
            react(n, p, Kind.DRAW, n.distTo(p), true, true);
        }
    }

    /** Put away again: those of the watch who were told to see it put away say so. */
    public void sheathed(Actor who) {
        if (who != g.player) return;
        for (Npc n : g.npcs) {
            if (!n.alive || !n.loaded || !n.watch() || n.watchTarget != who.id || g.time - n.armsAt > 60 || n.armsKind != Kind.DRAW && n.armsKind != Kind.SWING) continue;
            if (n.distTo(who) < 14 && ai.canSee(n, who)) { ai.say(n, n.armsSeen >= 3 ? "About time." : "Better. Keep it that way."); n.watchUntil = Math.min(n.watchUntil, g.time + 4); }
            n.drawnFor = 0;
        }
    }

    /** One witness's answer; true if it was more than a glance. */
    private boolean react(Npc n, Actor p, Kind kind, float d, boolean seen, boolean town) {
        if (kind == Kind.DRAW) return drawnAnswer(n, p, d, town);
        int times = (int) n.armsSeen;
        boolean quiet = g.time - n.armsSaidAt < 6;
        String word;
        switch (n.role) {
            case GUARD, LAWMAN -> {
                if (!town || !n.homeJurisdiction.equals(DevMap.jurisdiction(p.x, p.z))) return false;
                n.watchTarget = p.id; n.watchUntil = g.time + (times >= 3 ? 40 : 20); n.nextThink = 0;
                word = times >= 3 ? "Last warning. Put it away, or you'll spend the night in a cell."
                        : kind == Kind.SPELL ? "No spellcraft in the streets." : kind == Kind.SHOT ? "Who's loosing arrows in town?" : "Put that away before someone gets hurt.";
            }
            case KNIGHT -> word = kind == Kind.SPELL ? "Sorcery. Hmph." : times >= 2 ? "Your guard drops when you swing like that." : "Mind your form.";
            case HUNTER -> word = kind == Kind.SHOT ? "Mind where you loose those." : "You'll scare off every deer for a mile.";
            case PRIEST, HEALER -> word = kind == Kind.SPELL && n.role == Npc.Role.PRIEST ? "That's no holy working. Put it away." : "Peace, friend. There's no call for that here.";
            case SCHOLAR -> {
                if (kind == Kind.SPELL) {   // seen or only heard: either way the scholar comes to look
                    // drawn to it: comes to see the working closer
                    n.watchTarget = p.id; n.watchUntil = g.time + 15; n.nextThink = 0;
                    word = times >= 2 ? "Again! I almost had the shape of it." : "Fascinating. Where did you learn that?";
                } else word = "Careful with that.";
            }
            case THIEF, FENCE -> {
                n.fearOf = p.id; n.fearX = p.x; n.fearZ = p.z; n.fearSeenAt = g.time; n.hideSet = false; n.nextThink = 0;
                word = "Not my business.";
            }
            case BRUTE, BANDIT -> {
                if (kind == Kind.SWING && d < 5 && seen) { n.fightTarget = p.id; n.goal = Brain.Goal.FIGHT; n.nextThink = 0; word = "You want a fight? You've got one."; }
                else word = times >= 2 ? "Keep waving that about." : "Hah. Nice trick.";
            }
            default -> {
                // townsfolk: close by they flinch and get away from it; further off they stop and stare
                float close = kind == Kind.SPELL ? 8 : kind == Kind.SHOT ? 6 : 4;
                if (d < close) {
                    n.fearOf = p.id; n.fearX = p.x; n.fearZ = p.z; n.fearSeenAt = g.time - 6; n.hideSet = false; n.nextThink = 0;
                    word = kind == Kind.SPELL ? "Gods! Keep that away from me!" : "Watch it!";
                } else {
                    if (seen) n.wantYaw = (float) Math.atan2(-(p.x - n.x), -(p.z - n.z));
                    word = kind == Kind.SPELL ? "Was that magic?" : "Is that a fight?";
                }
                if (times >= 3 && town) { word = "Guards! Someone's waving a weapon about!"; callGuard(n, p); }
            }
        }
        if (!quiet && word != null && !word.isEmpty()) { ai.say(n, word); n.armsSaidAt = g.time; }
        n.lastOutcome = "saw " + p.name + (kind == Kind.SPELL ? " cast a spell" : kind == Kind.SHOT ? " loose a shot" : " swing a weapon");
        return true;
    }

    /** A weapon drawn, or carried drawn, in a town: everyone but the watch should have theirs sheathed. */
    private boolean drawnAnswer(Npc n, Actor p, float d, boolean town) {
        int times = (int) n.armsSeen;
        boolean quiet = g.time - n.armsSaidAt < 8;
        String word;
        switch (n.role) {
            case GUARD, LAWMAN -> {
                if (!town || !n.homeJurisdiction.equals(DevMap.jurisdiction(p.x, p.z))) return false;
                n.watchTarget = p.id; n.watchUntil = g.time + (times >= 3 ? 30 : 12); n.nextThink = 0;
                word = times >= 3 ? "I'll not say it again. Sheathe it." : times >= 2 ? "I told you once. Weapons sheathed in town." : pick(n, "Sheathe that. No bare steel in the streets.", "Put your weapon away while you're in town.");
            }
            case KNIGHT -> word = pick(n, "Steel out in the street? Hm.", "Sheathe it, friend. This isn't a battlefield.");
            case HUNTER -> word = "Expecting trouble?";
            case PRIEST, HEALER -> word = pick(n, "Put it away. Nobody here means you harm.", "This is a peaceful place. Sheathe it, please.");
            case THIEF, FENCE -> {
                n.fearOf = p.id; n.fearX = p.x; n.fearZ = p.z; n.fearSeenAt = g.time - 8; n.hideSet = false; n.nextThink = 0;
                word = "I'm not looking for trouble.";
            }
            case BRUTE, BANDIT -> word = d < 5 ? "Going to use that, or just wave it about?" : null;
            default -> {
                if (d < 3) { n.fearOf = p.id; n.fearX = p.x; n.fearZ = p.z; n.fearSeenAt = g.time - 9; n.hideSet = false; n.nextThink = 0; }
                else n.wantYaw = (float) Math.atan2(-(p.x - n.x), -(p.z - n.z));
                word = times >= 3 ? "Someone tell the guard - they won't put that away!" : pick(n, "Why's your weapon out?", "Put that away, would you? You're frightening people.", "Is something wrong? Your blade's drawn.");
                if (times >= 3 && town) callGuard(n, p);
            }
        }
        if (!quiet && word != null) { ai.say(n, word); n.armsSaidAt = g.time; }
        n.lastOutcome = "saw " + p.name + " with a weapon drawn in town";
        return true;
    }

    private static String pick(Npc n, String... lines) { return lines[(int) Math.floorMod(n.id + n.conversations, (long) lines.length)]; }

    /** A shout for the watch: the nearest officer of the place within earshot comes to keep an eye on the player. */
    private void callGuard(Npc caller, Actor p) {
        Npc best = null; float bd = 30;
        for (Npc o : g.npcs) if (o.alive && o.loaded && o.watch() && o.homeJurisdiction.equals(DevMap.jurisdiction(p.x, p.z)) && o.distTo(caller) < bd) { best = o; bd = o.distTo(caller); }
        if (best == null || best.goal == Brain.Goal.FIGHT) return;
        best.watchTarget = p.id; best.watchUntil = g.time + 40; best.nextThink = 0;
    }
}
