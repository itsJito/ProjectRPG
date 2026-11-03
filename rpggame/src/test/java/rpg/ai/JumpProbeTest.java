package rpg.ai;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import rpg.Ids;
import rpg.Rpg;
import rpg.actor.Actor;
import rpg.actor.Appearance;
import rpg.monster.Monster;
import rpg.monster.MonsterKind;
import rpg.world.DevMap;

/** Probe: fighters' positions frame to frame in brawls; every jump over 0.45 m in one frame, with what happened that tick. */
@EnabledIfEnvironmentVariable(named = "RPG_PROBE", matches = "1")
class JumpProbeTest {
    @Test void probe() {
        var cfg = new NativeEngine.Config(); cfg.deterministic = true; Game game = Game.create(cfg); game.activate(DevMap.build(game.engine()));
        Rpg g = game.add(new Rpg(game, "Probe", Appearance.henric(), true));
        for (Monster m : new ArrayList<>(g.monsters.all())) g.ai.unload(m);
        g.state.minutes = 12 * 60;
        g.teleport(g.player, 60.5f, DevMap.FLOOR, 80.5f);
        Npc guard = (Npc) g.actor(Ids.GUARD), lawman = (Npc) g.actor(Ids.LAWMAN_A);
        g.teleport(guard, 62.5f, DevMap.FLOOR, 72.5f); g.teleport(lawman, 64.5f, DevMap.FLOOR, 72.5f);
        game.tick(0);
        java.util.List<Monster> foes = new ArrayList<>();
        for (MonsterKind k : new MonsterKind[]{MonsterKind.ORC, MonsterKind.GOBLIN, MonsterKind.GOBLIN, MonsterKind.SKELETON}) {
            var mo = g.monsters.spawn(k, (k.minLevel + k.maxLevel) / 2, 63.5f + foes.size(), 67.5f, null);
            mo.hunger = 1; mo.aggression = 1; mo.target = guard.id; mo.cs.lastAttacker = guard.id; mo.cs.lastHurt = g.time;
            mo.state = Monster.State.ENGAGE; mo.stateSince = g.time; foes.add(mo);
        }
        Npc bandit = g.npcs.stream().filter(o -> o.role == Npc.Role.BANDIT && o.alive).findFirst().orElse(null);
        if (bandit != null) { g.teleport(bandit, 58.5f, DevMap.FLOOR, 80.5f); bandit.skills.setLevel(rpg.skill.Skill.HEALTH, 99); bandit.restore(); }
        g.player.skills.setLevel(rpg.skill.Skill.HEALTH, 99); g.player.skills.setLevel(rpg.skill.Skill.STRENGTH, 60); g.player.restore();
        g.player.eq.set(rpg.item.ItemDef.Slot.MAIN, new rpg.item.ItemStack(rpg.item.Items.get("bronze_battleaxe"), 1));
        g.combat.sheathe(g.player, false);
        Map<Long, float[]> last = new HashMap<>();
        int jumps = 0, events0 = g.state.all().size();
        for (int f = 0; f < 60 * 60; f++) {
            int before = g.state.all().size();
            if (bandit != null && bandit.alive) {
                if (g.player.distTo(bandit) > 2.0f) g.teleport(g.player, bandit.x + 1.5f, DevMap.FLOOR, bandit.z);
                g.controller.snapTo((float) Math.atan2(-(bandit.x - g.player.x), -(bandit.z - g.player.z)));
                g.controller.in.mainDown = f % 50 < 25;
            }
            game.tick(1f / 60);
            for (Actor a : g.actors()) {
                if (!a.alive || a == g.player || a == bandit && false || a instanceof Npc n && !n.loaded) continue;
                if (a instanceof rpg.animal.Animal) continue;
                float[] p = last.get(a.id);
                if (p != null) {
                    float d = (float) Math.hypot(a.x - p[0], a.z - p[1]);
                    boolean fighting = a instanceof Npc n ? n.goal == Brain.Goal.FIGHT || n.goal == Brain.Goal.FLEE || g.time - n.cs.lastHurt < 3 : a instanceof Monster;
                    if (d > 0.45f && fighting && jumps < 60) {
                        jumps++;
                        StringBuilder ev = new StringBuilder();
                        for (int i = before; i < g.state.all().size(); i++) { var e = g.state.all().get(i); if (e.actor() == a.id || e.subject() == a.id) ev.append(e.type()).append(' '); }
                        SoundProbeTest.log(String.format("[jump] t%.2f %s %.2f m (%.1f,%.1f)->(%.1f,%.1f) reacting=%b ragdoll=%d %s events: %s", g.time, a.name, d, p[0], p[1], a.x, a.z, a.reacting, a.ragdoll,
                                a instanceof Npc n ? n.goal + "/" + n.lastOutcome : ((Monster) a).state, ev));
                    }
                }
                last.put(a.id, new float[]{a.x, a.z});
            }
        }
        SoundProbeTest.log("[jump] total " + jumps + " over 60 s; foes alive " + foes.stream().filter(m -> m.alive).count());
    }
}
