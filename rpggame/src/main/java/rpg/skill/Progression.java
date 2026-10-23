package rpg.skill;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import rpg.Rpg;
import rpg.actor.Actor;
import rpg.item.Items;

/**
 * Every XP award in the game goes through here (GDD 2.2, 4.3 P0): the test reward is scaled by the configured rate,
 * applied atomically to the actor's skills, committed as an event (with the request it answers, so a repeated request
 * cannot pay twice), and - for the player - presented: the XP drop, and on a level the overlay, jingle and fireworks.
 */
public final class Progression {
    private final Rpg g;
    /** Recent XP drops for the HUD: skill, amount, when. */
    public record Drop(Skill skill, long amount, double at) {}
    public final ArrayDeque<Drop> drops = new ArrayDeque<>();
    /** The level-up being celebrated (null none), and when it began. */
    public Skills.Award celebrating;
    /** What the level just reached revealed (gamefixes: what a skill gives is kept hidden until the level is reached). */
    public final List<String> revealed = new ArrayList<>();
    public double celebratedAt = -99;
    /** Awards made, for the acceptance checks. */
    public long awards, levelUps;

    public Progression(Rpg g) { this.g = g; }

    /** Awards scaled test XP; returns what was applied. cause: the event that earned it (0 none). */
    public Skills.Award award(Actor a, Skill s, double baseXp, String why, long cause) {
        return awardRaw(a, s, Math.round(baseXp * g.cfg.xpRate), why, cause, 0);
    }

    /** Awards exactly this XP, unscaled (lamps, debug grants). requestId 0: not deduplicated. */
    public Skills.Award awardRaw(Actor a, Skill s, long xp, String why, long cause, long requestId) {
        // only the player gains XP and levels (gamefixes): NPCs and animals keep the levels they were made with
        if (xp <= 0 || a == null || a instanceof rpg.ai.Npc || a instanceof rpg.animal.Animal) return null;
        if (requestId != 0 && g.state.alreadyCommitted(requestId)) return null;
        Skills.Award w = a.skills.award(s, xp);
        awards++;
        if (g.invariants != null) g.invariants.onAward(a, s, w.applied());
        var ev = g.state.commit(requestId, "XpAwarded", a.id, 0, null, cause, s.name() + " +" + w.applied() + " (" + why + ") -> " + a.skills.xp(s));
        if (w.levelledUp()) {
            levelUps++;
            g.state.commit("LevelUp", a.id, 0, null, ev.id(), s.name() + " " + w.fromLevel() + "->" + w.toLevel());
            if (a == g.player) celebrate(w);
        }
        if (a == g.player && w.applied() > 0) {
            drops.addLast(new Drop(s, w.applied(), g.time));
            while (drops.size() > 8) drops.removeFirst();
        }
        return w;
    }

    private void celebrate(Skills.Award w) {
        Skill s = w.skill();
        g.chat.add("Congratulations, you've just advanced " + article(s) + " " + s.title + " level. Your " + s.title + " level is now " + w.toLevel() + ".", rpg.Chat.Kind.LEVEL);
        if (w.toLevel() >= XpTable.MAX_LEVEL)
            g.chat.add("You have mastered " + s.title + "! You may now purchase the " + Items.cape(s).name + " at the debug terminal.", rpg.Chat.Kind.LEVEL);
        if (s == Skill.HEALTH) g.player.hp = Math.min(g.player.maxHp(), g.player.hp + (w.toLevel() - w.fromLevel()));
        if (s == Skill.FAITH) g.player.faith = Math.min(g.player.maxFaith(), g.player.faith + (w.toLevel() - w.fromLevel()));
        if (s == Skill.MAGIC) g.player.mana = Math.min(g.player.maxMana(), g.player.mana + (w.toLevel() - w.fromLevel()));
        // what the new level opens up, announced now that it is earned (the guide showed only "???" until this moment)
        revealed.clear();
        for (String line : rpg.ui.SkillGuide.unlocks(s)) {
            int need = Integer.parseInt(line.substring(0, line.indexOf(' ')));
            if (need > w.fromLevel() && need <= w.toLevel()) revealed.add(line.substring(line.indexOf(' ') + 1));
        }
        for (String r : revealed) g.chat.add("New at " + s.title + " level " + w.toLevel() + ": " + r + ".", rpg.Chat.Kind.LEVEL);
        celebrating = w;
        celebratedAt = g.time;
        if (g.sfx != null) g.sfx.jingle();
        if (g.fx != null) g.fx.fireworks(g.player.x, g.player.y + 2.2f, g.player.z);
    }

    private static String article(Skill s) { return "AEIOU".indexOf(s.title.charAt(0)) >= 0 ? "an" : "a"; }

    public void update() {
        while (!drops.isEmpty() && g.time - drops.peekFirst().at() > 2.4) drops.removeFirst();
        if (celebrating != null && g.time - celebratedAt > 4.5) celebrating = null;
    }
}
