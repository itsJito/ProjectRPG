package rpg.ai;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import java.util.ArrayList;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import rpg.Ids;
import rpg.Rpg;
import rpg.actor.Appearance;
import rpg.item.ItemDef;
import rpg.item.ItemStack;
import rpg.item.Items;
import rpg.monster.Monster;
import rpg.monster.MonsterKind;
import rpg.world.DevMap;

/** Probe: an archer and a swordsman against a bat - shots, hits, how often the archer moves, what the swordsman does. */
@EnabledIfEnvironmentVariable(named = "RPG_PROBE", matches = "1")
class FlyerProbeTest {
    @Test void probe() {
        for (boolean archer : new boolean[]{true}) {
            var cfg = new NativeEngine.Config(); cfg.deterministic = true; Game game = Game.create(cfg); game.activate(DevMap.build(game.engine()));
            Rpg g = game.add(new Rpg(game, "Probe", Appearance.henric(), true));
            for (Monster m : new ArrayList<>(g.monsters.all())) g.ai.unload(m);
            Npc n = (Npc) g.actor(Ids.GUARD);
            for (Npc o : g.npcs) if (o != n) g.ai.unload(o);
            g.state.minutes = 23 * 60;
            float x = 70.5f, z = 70.5f;
            g.teleport(n, x, DevMap.FLOOR, z); g.teleport(g.player, 20.5f, DevMap.FLOOR, 150.5f);
            n.skills.setLevel(rpg.skill.Skill.ARCHERY, 40);
            if (archer) {
                n.eq.set(ItemDef.Slot.MAIN, new ItemStack(Items.get("shortbow"), 1));
                n.eq.set(ItemDef.Slot.AMMO, new ItemStack(Items.get("bronze_arrows"), 60));
            }
            game.tick(0);
            Monster bat = g.monsters.spawn(MonsterKind.BAT, 40, x + 6, z + 3, null);
            bat.hunger = 1; bat.aggression = 1; bat.target = n.id; bat.cs.lastAttacker = n.id; bat.cs.lastHurt = g.time; bat.state = Monster.State.ENGAGE; bat.stateSince = g.time; n.fightTarget = bat.id;
            long shots0 = g.combat.shots, hits0 = g.combat.contacts; float hp0 = bat.hp, nhp0 = n.hp;
            java.util.Set<String> spots = new java.util.LinkedHashSet<>(); java.util.Map<String, Integer> choices = new java.util.TreeMap<>();
            for (int f = 0; f < 60 * 25 && bat.alive && n.alive; f++) {
                game.tick(1f / 60);
                if (f % 30 == 0) { spots.add((int) n.x + "," + (int) n.z); choices.merge(n.lastOutcome == null ? "-" : n.lastOutcome.replaceAll("[0-9.]+", "#"), 1, Integer::sum); if (f % 120 == 0 && false) SoundProbeTest.log(String.format("  t%.0f bat %s loaded=%b at (%.1f,%.1f,%.1f) d=%.1f target=%d guard goal %s", f / 60f, bat.state, bat.loaded, bat.x, bat.y, bat.z, bat.distTo(n), bat.target, n.goal)); }
            }
            SoundProbeTest.log(String.format("[flyer] archer=%b hits=%d shots=%d bat hp %.1f->%.1f alive=%b  guard hp %.1f->%.1f  cells visited %d  goal %s  outcomes %s",
                    archer, g.combat.contacts - hits0, g.combat.shots - shots0, hp0, bat.hp, bat.alive, nhp0, n.hp, spots.size(), n.goal, choices));
            game.close();
        }
    }
}
