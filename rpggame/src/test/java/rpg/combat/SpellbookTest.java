package rpg.combat;

import static org.junit.jupiter.api.Assertions.*;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import org.junit.jupiter.api.Test;
import rpg.Rpg;
import rpg.actor.Appearance;
import rpg.combat.Spells.Book;
import rpg.combat.Spells.Element;
import rpg.combat.Spells.Spell;
import rpg.combat.Spells.Tier;
import rpg.item.ItemStack;
import rpg.item.Items;
import rpg.skill.Skill;
import rpg.world.DevMap;

/** Gamefixes' spellbooks: Sun's five tiers in five elements, Fogish's ice, Moon's utility, and the rules between them. */
class SpellbookTest {
    @Test void theBooksAreSunFogishAndMoon() {
        assertEquals(3, Book.values().length);
        assertEquals("Sun", Book.SUN.title); assertEquals("Fogish", Book.FOGISH.title); assertEquals("Moon", Book.MOON.title);
        assertEquals(Book.SUN, Book.of("STANDARD")); assertEquals(Book.FOGISH, Book.of("ANCIENTS"));
        assertEquals(Book.MOON, Book.of("LUNAR")); assertEquals(Book.MOON, Book.of("ARCANE"));
        assertEquals(Spell.WIND_STRIKE, Spell.of("SPARK"));
        // twenty-five tiered elemental spells, all Sun's; nothing elemental and damaging left in another book but Fogish's ice
        long tiered = java.util.Arrays.stream(Spell.values()).filter(s -> s.tier != null).count();
        assertEquals(25, tiered);
        for (Spell s : Spell.values()) {
            if (s.tier != null) assertEquals(Book.SUN, s.book, s.title);
            if (!s.utility && s.book != Book.SUN && s.element != Element.NONE) assertEquals(Element.ICE, s.element, s.title + " is elemental outside Sun");
        }
    }

    @Test void aTierHitsAsHardAsItsStrongestOpenElement() {
        // Strike: Wind 2 / Water 4 / Electric 5 / Earth 6 / Fire 8, each opening the rest of the tier up to it
        assertEquals(2, Spell.WIND_STRIKE.maxBase(1));
        assertEquals(4, Spell.WIND_STRIKE.maxBase(5));
        assertEquals(6, Spell.WATER_STRIKE.maxBase(9));
        assertEquals(8, Spell.WIND_STRIKE.maxBase(13));
        // once Fire Bolt is open, every Bolt hits up to 12
        assertEquals(11, Spell.WIND_BOLT.maxBase(34));
        for (Spell s : Spell.values()) if (s.tier == Tier.BOLT) assertEquals(12, s.maxBase(35), s.title);
        for (Tier t : Tier.values()) for (Spell s : Spell.values()) if (s.tier == t) assertEquals(t.top, s.maxBase(99), s.title + " at 99");
        // Electric and Earth share their base from Bolt on, Electric opening first
        for (Tier t : new Tier[]{Tier.BOLT, Tier.BLAST, Tier.WAVE, Tier.SURGE}) {
            Spell e = null, earth = null;
            for (Spell s : Spell.values()) if (s.tier == t) { if (s.element == Element.ELECTRIC) e = s; if (s.element == Element.EARTH) earth = s; }
            assertEquals(e.base, earth.base, 1e-6f, t.title);
            assertTrue(e.level < earth.level, t.title);
        }
        assertEquals(5, Spell.ICE_RUSH.freeze(), 1e-6f); assertEquals(20, Spell.ICE_BARRAGE.freeze(), 1e-6f);
        assertEquals(94, Spell.ICE_BARRAGE.level); assertEquals(30, Spell.ICE_BARRAGE.base, 1e-6f);
    }

    @Test void anElementalStaffMakesItsSpellsCheapAndIceFreezes() {
        var config = new NativeEngine.Config(); config.deterministic = true;
        try (Game game = Game.create(config)) {
            game.activate(DevMap.build(game.engine()));
            Rpg g = game.add(new Rpg(game, "Spells", new Appearance(), true));
            g.player.skills.setLevel(Skill.MAGIC, 99); g.player.restore();
            assertEquals(Spell.FIRE_SURGE.cost, g.combat.cost(g.player, Spell.FIRE_SURGE));
            g.player.eq.set(rpg.item.ItemDef.Slot.MAIN, new ItemStack(Items.FIRE_STAFF, 1));
            assertTrue(g.combat.cost(g.player, Spell.FIRE_SURGE) <= 2, "a fire staff makes Fire Surge very cheap");
            assertEquals(Spell.WATER_SURGE.cost, g.combat.cost(g.player, Spell.WATER_SURGE), "but not another element's");
            g.player.eq.set(rpg.item.ItemDef.Slot.MAIN, new ItemStack(Items.WATER_STAFF, 1));
            assertTrue(g.combat.cost(g.player, Spell.WATER_SURGE) <= 2);
            // a cast Fire Bolt at Magic 99 carries the tier's top
            g.player.book = Book.SUN; g.player.mana = g.player.maxMana();
            game.tick(0);
            assertTrue(g.combat.cast(g.player, Spell.WIND_BOLT, 0, 0));
            var bolt = g.combat.projectiles.get(g.combat.projectiles.size() - 1);
            assertTrue(bolt.potential >= 12, "Wind Bolt hits up to the Bolt tier's 12 once Fire Bolt is open: " + bolt.potential);
        }
    }
}
