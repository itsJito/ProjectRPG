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
    /** Awards made, for the acceptance checks. */
    public long awards, levelUps;

    private record Pending(Skills.Award award,int before,int after,double at) {}
    private final ArrayDeque<Pending> pending = new ArrayDeque<>();
    private double nextCelebration;
    private final ArrayDeque<Drop> pendingDrops=new ArrayDeque<>();
    public Progression(Rpg g) { this.g = g; }

    /** Awards scaled test XP; returns what was applied. cause: the event that earned it (0 none). */
    public Skills.Award award(Actor a, Skill s, double baseXp, String why, long cause) {
        return awardRaw(a, s, Math.round(baseXp * g.cfg.xpRate * (g.cfg.xpRate == 1 ? 1 : rpg.Config.skillPace(s))), why, cause, 0);
    }

    /** Awards exactly this XP, unscaled (lamps, debug grants). requestId 0: not deduplicated. */
    public Skills.Award awardRaw(Actor a, Skill s, long xp, String why, long cause, long requestId) {
        // Only this session's player progresses; NPCs, animals and training dummies retain their configured skills.
        if (xp <= 0 || a == null || a != g.player) return null;
        if (requestId != 0 && g.state.alreadyCommitted(requestId)) return null;
        int combatBefore = a.skills.combatLevel();
        Skills.Award w = a.skills.award(s, xp);
        awards++;
        if (g.invariants != null) g.invariants.onAward(a, s, w.applied());
        var ev = g.state.commit(requestId, "XpAwarded", a.id, 0, null, cause, s.name() + " +" + w.applied() + " (" + why + ") -> " + a.skills.xp(s));
        if (w.levelledUp()) {
            levelUps++;
            g.state.commit("LevelUp", a.id, 0, null, ev.id(), s.name() + " " + w.fromLevel() + "->" + w.toLevel());
            pending.addLast(new Pending(w,combatBefore,a.skills.combatLevel(),g.time));
        }
        if (a == g.player && w.applied() > 0) {
            pendingDrops.addLast(new Drop(s,w.applied(),g.time));
            while(pendingDrops.size()>8)pendingDrops.removeFirst();
        }
        return w;
    }

    private void celebrate(Skills.Award w, int combatBefore, int combatNow) {
        Skill s = w.skill();
        g.chat.add("Congratulations, you've just advanced " + article(s) + " " + s.title + " level. Your " + s.title + " level is now " + w.toLevel() + ".", rpg.Chat.Kind.LEVEL);
        if (w.toLevel() >= XpTable.MAX_LEVEL)
            g.chat.add("You have mastered " + s.title + "! You may now purchase the " + Items.cape(s).name + " at the debug terminal.", rpg.Chat.Kind.LEVEL);
        if (s == Skill.HEALTH) g.player.hp = Math.min(g.player.maxHp(), g.player.hp + (w.toLevel() - w.fromLevel()));
        if (s == Skill.FAITH) g.player.faith = Math.min(g.player.maxFaith(), g.player.faith + (w.toLevel() - w.fromLevel()));
        if (s == Skill.MAGIC) g.player.mana = Math.min(g.player.maxMana(), g.player.mana + (w.toLevel() - w.fromLevel()));
        // what the new level opens up, announced now that it is earned (the guide showed only "???" until this moment)
        List<String> revealed=new ArrayList<>();
        for (String line : rpg.ui.SkillGuide.unlocks(s)) {
            int need = Integer.parseInt(line.substring(0, line.indexOf(' ')));
            if (need > w.fromLevel() && need <= w.toLevel()) revealed.add(line.substring(line.indexOf(' ') + 1));
        }
        for (String r : revealed) g.chat.add("New at " + s.title + " level " + w.toLevel() + ": " + r + ".", rpg.Chat.Kind.LEVEL);
        boolean combatUp = combatNow > combatBefore, combatMax = combatUp && combatNow >= Skills.MAX_COMBAT;
        if (combatUp) g.chat.add("Your combat level is now " + combatNow + ".", rpg.Chat.Kind.LEVEL);
        if (g.sfx != null) g.sfx.jingle(w.toLevel() >= XpTable.MAX_LEVEL, combatUp, combatMax);
        if (g.music != null) g.music.duckFor(combatMax || w.toLevel() >= XpTable.MAX_LEVEL ? 8 : 4);
        // the Holy Light (gamefixes): the beam, the glow and the rain of sparkles; and out of a fight, a cheer - never on
        // the final blow, nor with other foes about
        if (g.fx != null) g.fx.holyLight(g.player.x, g.player.y, g.player.z, combatUp);
        if (!fighting()) g.player.cheerAt = g.time;
        // a combat level heals the body whole; a skill's does not
        if (combatUp) g.player.hp = g.player.maxHp();
        banner = new Banner(s, w.toLevel(), combatUp ? combatNow : 0, combatMax, g.time);
        if (combatUp) g.state.commit("CombatLevelUp", g.player.id, 0, null, 0, String.valueOf(combatNow));
    }

    /** The banner splashed across the screen for the latest level: the skill and its level, and the new combat level if one came with it. */
    public record Banner(Skill skill, int level, int combat, boolean combatMax, double at) {}
    public Banner banner;

    /** In a fight: striking or struck just now, the last blow just landed, or a foe close at hand. */
    private boolean fighting() {
        var p = g.player;
        if (p.cs.busy() || g.time - p.cs.lastHurt < 4 || g.combat != null && g.time - g.combat.lastPlayerKill < 2.5) return true;
        for (Actor o : g.actors()) {
            if (o == p || !o.alive || o.dummy || o.distTo(p) >= 15) continue;
            if (g.ai.hostile(o, p) || o instanceof rpg.animal.Animal an && (an.goal == rpg.animal.Animal.Goal.DEFEND || an.goal == rpg.animal.Animal.Goal.HUNT) && (an.threat == p.id || an.other == p.id)) return true;
        }
        return false;
    }

    private static String article(Skill s) { return "AEIOU".indexOf(s.title.charAt(0)) >= 0 ? "an" : "a"; }

    public List<Object> save(){List<Object> rows=new ArrayList<>();for(var p:pending)rows.add(List.of(p.award.skill().name(),p.award.requested(),p.award.applied(),p.award.fromLevel(),p.award.toLevel(),p.before,p.after));return rows;}
    public void load(List<Object> rows){pending.clear();pendingDrops.clear();drops.clear();banner=null;nextCelebration=0;for(Object row:rows){if(!(row instanceof List<?> v) || v.size()!=7)continue;try{var award=new Skills.Award(Skill.valueOf((String)v.get(0)),((Number)v.get(1)).longValue(),((Number)v.get(2)).longValue(),((Number)v.get(3)).intValue(),((Number)v.get(4)).intValue());pending.add(new Pending(award,((Number)v.get(5)).intValue(),((Number)v.get(6)).intValue(),g.time));}catch(IllegalArgumentException ignored){}}}
    public void update() {
        if(g.player.alive && g.player.hp>0 && !g.death.active && !pending.isEmpty() && g.time>pending.peekFirst().at && g.time>=nextCelebration){
            var p=pending.removeFirst();celebrate(p.award,p.before,p.after);nextCelebration=g.time+4;
        }
        if(g.player.alive && g.player.hp>0 && !g.death.active){while(!pendingDrops.isEmpty() && g.time>pendingDrops.peekFirst().at){var d=pendingDrops.removeFirst();drops.addLast(new Drop(d.skill,d.amount,g.time));}while(drops.size()>8)drops.removeFirst();}
        while (!drops.isEmpty() && g.time - drops.peekFirst().at() > 2.4) drops.removeFirst();
    }
}
