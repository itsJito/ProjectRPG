package rpg.combat;

import static org.junit.jupiter.api.Assertions.*;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import org.junit.jupiter.api.*;
import rpg.Rpg;
import rpg.actor.Appearance;
import rpg.ai.Npc;
import rpg.combat.Spells.Book;
import rpg.combat.Spells.Spell;
import rpg.skill.Skill;
import rpg.world.DevMap;

/**
 * Gamefixes: magic did no visible damage, could not decapitate, deadly spells did not dismember, and a strong Earth Wave
 * did not throw anyone down. A spell now wounds the body by its element, a killing one takes off what it struck (a head
 * included), and an Earth Wave knocks a living foe off their feet.
 */
class MagicGoreTest {
    private Game game;
    private Rpg g;
    private Npc foe;
    private float px, pz;

    @BeforeEach void open() {
        var cfg = new NativeEngine.Config(); cfg.deterministic = true;
        game = Game.create(cfg); game.activate(DevMap.build(game.engine()));
        g = game.add(new Rpg(game, "Magic gore", new Appearance(), true));
        for (Npc n : g.npcs) { if (foe == null && n.role == Npc.Role.VILLAGER) foe = n; else g.ai.unload(n); }
        g.player.skills.setLevel(Skill.MAGIC, 99); g.player.restore(); g.player.book = Book.SUN;
        float[] at = rpg.TestLanes.open(g);
        px = at[0]; pz = at[1];
        g.teleport(g.player, px, DevMap.FLOOR, pz);
        g.teleport(foe, px, DevMap.FLOOR, pz - 6);
        for (int i = 0; i < 10; i++) game.tick(1f / 60);
    }
    @AfterEach void close() { game.close(); }

    private CombatSystem.Projectile cast(Spell s, float pitch, float potential, float impact) {
        g.player.mana = g.player.maxMana(); g.player.cs.castCooldown = 0;
        assertTrue(g.combat.cast(g.player, s, 0, pitch), s.title + " cast");
        var p = g.combat.projectiles.get(g.combat.projectiles.size() - 1);
        p.potential = potential; p.impact = impact;
        return p;
    }

    @Test void aSpellLeavesItsMarkOnTheBody() {
        int before = foe.blood.marks.size();
        cast(Spell.FIRE_STRIKE, 0, 4 * CombatMath.HP_SCALE, 0);
        for (int i = 0; i < 60 && foe.hp >= foe.maxHp(); i++) game.tick(1f / 60);
        assertTrue(foe.hp < foe.maxHp(), "the fire strike landed");
        assertTrue(foe.blood.scorched(), "fire leaves a scorch where it struck");
        cast(Spell.WIND_STRIKE, 0, 4 * CombatMath.HP_SCALE, 0);
        for (int i = 0; i < 60; i++) game.tick(1f / 60);
        assertTrue(foe.blood.marks.size() > before + 1, "a wind strike cuts and bleeds (" + foe.blood.marks.size() + " marks)");
    }

    @Test void aKillingSpellToTheHeadTakesItOff() {
        // aimed at the head: a little up from level at six metres
        float head = (float) Math.atan2(foe.y + foe.height * 0.93f - g.player.eyeY(), 6);
        cast(Spell.WIND_BOLT, head, 10000, 0);
        for (int i = 0; i < 90 && foe.alive; i++) game.tick(1f / 60);
        assertFalse(foe.alive, "the bolt killed");
        assertTrue(g.gore.lost(foe, Gore.Part.HEAD), "a killing bolt to the head decapitates (lost: " + java.util.Arrays.toString(g.gore.of(foe).severed) + ")");
    }

    @Test void aKillingFireballToTheBodyTearsOffALimb() {
        cast(Spell.FIRE_BLAST, 0, 10000, 0);
        for (int i = 0; i < 90 && foe.alive; i++) game.tick(1f / 60);
        assertFalse(foe.alive);
        assertTrue(g.gore.of(foe).any(), "a deadly blast to the trunk dismembers");
    }

    @Test void aStrongEarthWaveThrowsAFoeDown() {
        foe.skills.setLevel(Skill.HEALTH, 99); foe.restore();
        cast(Spell.EARTH_WAVE, 0, 2 * CombatMath.HP_SCALE, 80);
        boolean down = false;
        for (int i = 0; i < 90 && !down; i++) { game.tick(1f / 60); down = foe.reacting && foe.ragdoll != 0; }
        assertTrue(foe.alive, "the wave was not meant to kill");
        assertTrue(down, "the earth wave knocked the foe off their feet");
    }
}
