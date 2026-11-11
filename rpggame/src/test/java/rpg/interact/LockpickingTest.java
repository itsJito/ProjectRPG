package rpg.interact;

import static org.junit.jupiter.api.Assertions.*;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import org.junit.jupiter.api.*;
import rpg.Rpg;
import rpg.actor.Appearance;
import rpg.item.ItemStack;
import rpg.item.Items;
import rpg.skill.Skill;
import rpg.world.DevMap;
import static rpg.interact.Lockpicking.*;

/** (0.3.7, gamefixes: the lockpicking overhaul) one hidden angle on a half circle, picks that wear only while they strain. */
class LockpickingTest {
    private Game game;
    private Rpg g;
    @BeforeEach void open() {
        var cfg = new NativeEngine.Config(); cfg.deterministic = true;
        game = Game.create(cfg); game.activate(DevMap.build(game.engine()));
        g = game.add(new Rpg(game, "Locks", new Appearance(), true));
        game.tick(0);
    }
    @AfterEach void close() { game.close(); }

    @Test void theNumbersAreTheOnesAsked() {
        assertEquals(60 * 0.5f * 0.826f, sweetWidth(Difficulty.NOVICE, 1), 0.01f);
        assertEquals(60 / 32f * 1.42f, sweetWidth(Difficulty.MASTER, 100), 0.01f);
        assertEquals(22 * (0.775f + 0.015f), partialWidth(Difficulty.NOVICE, 1), 0.01f);
        assertEquals(6 * 2.275f, partialWidth(Difficulty.MASTER, 100), 0.01f);
        assertEquals(2f * 1.005f, breakSeconds(Difficulty.NOVICE, 1), 0.001f);
        assertEquals(0.25f * 1.5f, breakSeconds(Difficulty.MASTER, 100), 0.001f);
        // the sweet spot opens; a partial zone turns it as far as the pick is near the centre, then binds; outside, not at all
        float sweet = sweetWidth(Difficulty.ADEPT, 30) / 2, partial = partialWidth(Difficulty.ADEPT, 30);
        assertEquals(1, limit(10 + sweet * 0.9f, 10, Difficulty.ADEPT, 30));
        float near = limit(10 + sweet + partial * 0.2f, 10, Difficulty.ADEPT, 30), far = limit(10 + sweet + partial * 0.8f, 10, Difficulty.ADEPT, 30);
        assertTrue(near > far && far > 0 && near < 1, near + " > " + far);
        assertEquals(0, limit(10 + sweet + partial + 1, 10, Difficulty.ADEPT, 30));
    }

    private rpg.objects.Container chest(int lockLevel) {
        var c = g.objects.add(new rpg.objects.Container(g.state.newEntityId(), "chest", "Test chest", "chest", g.player.x + 1, DevMap.FLOOR + 0.4f, g.player.z, 10, 0));
        c.locked = true; c.lockLevel = lockLevel;
        return c;
    }

    @Test void aPickWearsOnlyWhileItStrainsAndKeepsItsWearAcrossLocks() {
        g.player.skills.setLevel(Skill.THIEVING, 1);
        g.player.store(new ItemStack(Items.LOCKPICK, 1)); g.player.store(new ItemStack(Items.LOCKPICK, 1));
        var a = chest(5);
        Attempt t = g.lockpicking.begin(g.player, a);
        // in the sweet spot: turning freely, no wear, and it opens
        for (int i = 0; i < 30 && !t.over; i++) g.lockpicking.tick(t, 1 / 30f, t.target, true);
        assertTrue(t.open && !a.locked, "the sweet spot opens it");
        assertEquals(0, t.using.wear, 1e-6, "no wear while it turns freely");
        // a second lock, the pick far from its spot: strain for half its life, let go, and it keeps that wear
        var b = chest(5);
        Attempt u = g.lockpicking.begin(g.player, b);
        float wrong = u.target > 0 ? u.target - 80 : u.target + 80;
        float half = breakSeconds(u.difficulty, 1) / 2;
        for (float s = 0; s < half; s += 1 / 60f) g.lockpicking.tick(u, 1 / 60f, wrong, true);
        float worn = u.using.wear;
        assertEquals(0.5f, worn, 0.05f, "half its life strained away");
        for (int i = 0; i < 60; i++) g.lockpicking.tick(u, 1 / 60f, wrong, false);
        assertEquals(worn, u.using.wear, 1e-6, "no wear with the wrench let go");
        g.lockpicking.abandon(u);
        Attempt v = g.lockpicking.begin(g.player, b);
        assertEquals(worn, v.using.wear, 1e-6, "the same pick, as worn as it was, at the next try");
        // strained to the end it snaps: one fewer carried, Thieving for the break
        double xp = g.player.skills.xp(Skill.THIEVING);
        for (int i = 0; i < 600 && v.broken == 0; i++) g.lockpicking.tick(v, 1 / 60f, v.target > 0 ? v.target - 80 : v.target + 80, true);
        assertEquals(1, v.broken);
        assertEquals(1, g.player.count(Items.LOCKPICK));
        assertTrue(g.player.skills.xp(Skill.THIEVING) > xp, "a broken pick is practice");
        assertEquals(0, v.pick, 0.001f, "below Thieving 25 a fresh pick goes in straight up");
    }

    @Test void anyLockOpensToAnyHandGivenPicks() {
        g.player.skills.setLevel(Skill.THIEVING, 1);
        for (int i = 0; i < 40; i++) g.player.store(new ItemStack(Items.LOCKPICK, 1));
        var m = chest(90);
        assertEquals(Difficulty.MASTER, Difficulty.of(m.lockLevel));
        double xp = g.player.skills.xp(Skill.THIEVING);
        Attempt t = g.lockpicking.begin(g.player, m);
        g.lockpicking.work(t);
        assertTrue(t.open && !m.locked, "a master lock at Thieving 1: " + t.outcome + ", " + t.broken + " broken");
        assertTrue(g.player.skills.xp(Skill.THIEVING) - xp >= OPEN_XP * 5, "opened, scaled by the lock");
    }

    @Test void aLockpickIsNeededUntilThieving50() {
        var c = chest(20);
        g.player.skills.setLevel(Skill.THIEVING, 49);
        assertEquals("You need a lockpick.", refusal(g.player));
        assertFalse(c.options(g, g.player).stream().anyMatch(o -> o.id().equals("lockpick") && o.enabled()));
        g.player.skills.setLevel(Skill.THIEVING, 50);
        assertNull(refusal(g.player));
        Attempt t = g.lockpicking.begin(g.player, c);
        assertNull(t.using, "an improvised pick");
        g.lockpicking.work(t);
        assertTrue(t.open, "opened with no lockpick carried: " + t.outcome);
    }

    @Test void fromThieving25AFreshPickGoesBackWhereTheLastWas() {
        g.player.skills.setLevel(Skill.THIEVING, 30);
        for (int i = 0; i < 3; i++) g.player.store(new ItemStack(Items.LOCKPICK, 1));
        var c = chest(80);
        Attempt t = g.lockpicking.begin(g.player, c);
        float at = t.target > 0 ? t.target - 60 : t.target + 60;
        for (int i = 0; i < 600 && t.broken == 0; i++) g.lockpicking.tick(t, 1 / 60f, at, true);
        assertEquals(1, t.broken);
        assertEquals(at, t.pick, 0.001f);
    }
}
