package rpg.combat;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import rpg.Ids;
import rpg.Rpg;
import rpg.actor.Actor;
import rpg.actor.Appearance;
import rpg.item.ItemDef;
import rpg.skill.Skill;
import rpg.world.DevMap;

import static org.junit.jupiter.api.Assertions.*;

class CombatFlowTest {
    private Game game;
    private Rpg g;

    @BeforeEach void open() {
        var config = new NativeEngine.Config(); config.deterministic = true;
        game = Game.create(config); game.activate(DevMap.build(game.engine()));
        g = game.add(new Rpg(game, "Combat flow", new Appearance(), true));
        for (var n : g.npcs) g.ai.unload(n);
        g.teleport(g.player, 64.5f, DevMap.FLOOR, 62.5f);
        g.cfg.xpRate = 1;
    }

    @AfterEach void close() { if (game != null) game.close(); }

    private void hit(Actor attacker, Actor target) {
        g.combat.resolve(attacker, target, 2, 0, ItemDef.DamageType.BLUNT, null, target.x, target.chestY(), target.z, "combat flow", false);
    }

    @Test void landedMeleeTrainsAttackAndStrengthWithoutTrainingDefense() {
        long attack = g.player.skills.xp(Skill.ATTACK), strength = g.player.skills.xp(Skill.STRENGTH), defense = g.player.skills.xp(Skill.DEFENSE);
        Actor target = g.dummies.get(0);
        long[] dummyXp = target.skills.raw().clone();
        hit(g.player, target);
        assertTrue(g.player.skills.xp(Skill.ATTACK) > attack);
        assertTrue(g.player.skills.xp(Skill.STRENGTH) > strength);
        assertEquals(defense, g.player.skills.xp(Skill.DEFENSE));
        assertArrayEquals(dummyXp, target.skills.raw(), "a plain Actor used as a dummy must not gain levels either");
    }

    @Test void receivingHostileDamageTrainsDefenseWhileNpcLevelsStayFixed() {
        Actor attacker = g.actor(Ids.BANDIT_1);
        long[] npcXp = attacker.skills.raw().clone();
        long defense = g.player.skills.xp(Skill.DEFENSE);
        hit(attacker, g.player);
        assertTrue(g.player.skills.xp(Skill.DEFENSE) > defense);
        assertArrayEquals(npcXp, attacker.skills.raw());
    }

    private void recoveryClick(float remaining) {
        var c = g.player.cs;
        c.phase = Actor.Combat.Phase.RECOVER; c.t = 0; c.recover = remaining;
        g.controller.in.mainDown = true; assertTrue(game.tick(1f / 60));
        g.controller.in.mainDown = false; assertTrue(game.tick(1f / 60));
    }

    @Test void aLateRecoveryClickStartsOneAttackAsSoonAsRecoveryEnds() {
        recoveryClick(0.1f);
        double previous = g.player.cs.lastAttack;
        for (int i = 0; i < 8; i++) assertTrue(game.tick(1f / 60));
        assertTrue(g.player.cs.lastAttack > previous, "a near-ready click must not disappear");
        double started = g.player.cs.lastAttack;
        for (int i = 0; i < 90; i++) assertTrue(game.tick(1f / 60));
        assertEquals(started, g.player.cs.lastAttack, "one click must not auto-repeat");
    }

    @Test void anEarlyRecoveryClickExpiresInsteadOfFiringMuchLater() {
        recoveryClick(0.6f);
        double previous = g.player.cs.lastAttack;
        for (int i = 0; i < 60; i++) assertTrue(game.tick(1f / 60));
        assertEquals(previous, g.player.cs.lastAttack);
    }

    @Test void respawningDiscardsAnyPendingAttack() {
        recoveryClick(0.1f); g.respawn();
        double previous = g.player.cs.lastAttack;
        for (int i = 0; i < 20; i++) assertTrue(game.tick(1f / 60));
        assertEquals(previous, g.player.cs.lastAttack);
    }

    @Test void releasingAPreDeathPressAfterRespawnDoesNotStartAnAttack() {
        g.controller.in.mainDown = true; assertTrue(game.tick(1f / 60));
        double previous = g.player.cs.lastAttack;
        g.respawn(); g.controller.in.mainDown = false;
        for (int i = 0; i < 20; i++) assertTrue(game.tick(1f / 60));
        assertEquals(previous, g.player.cs.lastAttack);
    }

    @Test void guardingCancelsTheBufferedSwing() {
        recoveryClick(0.1f); g.controller.in.offDown = true;
        double previous = g.player.cs.lastAttack;
        for (int i = 0; i < 20; i++) assertTrue(game.tick(1f / 60));
        assertEquals(previous, g.player.cs.lastAttack);
    }

    @Test void enteringAMenuDiscardsBothBufferedAndHeldInput() {
        recoveryClick(0.1f); g.controller.cancelHandInput();
        double previous = g.player.cs.lastAttack;
        for (int i = 0; i < 20; i++) assertTrue(game.tick(1f / 60));
        assertEquals(previous, g.player.cs.lastAttack);
        g.controller.in.mainDown = true; assertTrue(game.tick(1f / 60));
        g.controller.cancelHandInput(); g.controller.in.mainDown = false;
        for (int i = 0; i < 20; i++) assertTrue(game.tick(1f / 60));
        assertEquals(previous, g.player.cs.lastAttack);
    }

    @Test void animalAttackControlsHandlingWhileItsLevelsNeverGainXp() {
        var animal = g.animals.release(rpg.animal.Species.WOLF, false, false, 20.5f, 20.5f);
        animal.skills.setLevel(Skill.ATTACK, 1);
        assertTrue(g.combat.attack(animal, false, Actor.Combat.Move.BITE));
        float windup = animal.cs.windup, active = animal.cs.active;
        animal.cs.cancel(); animal.skills.setLevel(Skill.ATTACK, 80);
        assertTrue(g.combat.attack(animal, false, Actor.Combat.Move.BITE));
        assertTrue(animal.cs.windup < windup, "an animal's Attack governs handling, just as a human's does");
        assertEquals(active, animal.cs.active);
        long[] xp = animal.skills.raw().clone();
        g.prog.award(animal, Skill.ATTACK, 10000, "animal combat", 0);
        assertArrayEquals(xp, animal.skills.raw());
    }

    @Test void holdingAndReleasingABowDrawsAndConsumesOneArrow() {
        g.player.eq.set(ItemDef.Slot.MAIN, new rpg.item.ItemStack(rpg.item.Items.SHORTBOW, 1));
        g.player.eq.set(ItemDef.Slot.AMMO, new rpg.item.ItemStack(rpg.item.Items.BRONZE_ARROWS, 10));
        g.controller.in.mainDown = true;
        for (int i = 0; i < 45; i++) assertTrue(game.tick(1f / 60));
        assertTrue(g.player.cs.draw > 0.75f);
        g.controller.in.mainDown = false; assertTrue(game.tick(1f / 60));
        assertEquals(0, g.player.cs.draw);
        assertEquals(9, g.player.eq.get(ItemDef.Slot.AMMO).amount);
        assertEquals(1, g.combat.projectiles.size());
    }
}
