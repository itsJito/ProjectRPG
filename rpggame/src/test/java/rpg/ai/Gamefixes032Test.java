package rpg.ai;

import static org.junit.jupiter.api.Assertions.*;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import java.util.ArrayList;
import org.junit.jupiter.api.*;
import rpg.Rpg;
import rpg.actor.Appearance;
import rpg.monster.Monster;
import rpg.world.DevMap;
import rpg.world.Blocks;
import rpg.animal.Animal;
import rpg.animal.Species;
import rpg.item.Items;

/** Alpha 0.3.2 gamefixes. */
class Gamefixes032Test {
    private Game game;
    private Rpg g;
    @BeforeEach void open() {
        var cfg = new NativeEngine.Config(); cfg.deterministic = true; game = Game.create(cfg); game.activate(DevMap.build(game.engine()));
        g = game.add(new Rpg(game, "Gamefixes 0.3.2", Appearance.henric(), true));
        for (Monster m : new ArrayList<>(g.monsters.all())) g.ai.unload(m);
        game.tick(0);
    }
    @AfterEach void close() { game.close(); }
    private void ticks(int n) { for (int i = 0; i < n; i++) game.tick(1f / 60); }

    private rpg.world.Foliage.Patch nearestPlant(rpg.world.Foliage.Kind kind) {
        rpg.world.Foliage.Patch best = null; float bd = 1e9f;
        for (var c : g.foliage.chunks.values()) for (var p : c.plants) if (!p.removed && (kind == null || p.kind == kind)) {
            float d = g.player.distTo(p.x, p.z); if (d < bd) { bd = d; best = p; }
        }
        return best;
    }

    // ------------------------------------------------------------------ fire spells burn the plants, wherever one has walked
    @Test void aFireSpellStillBurnsGrassAfterWalkingTheWholeMap() {
        // the whole map walked over first: every chunk's growth comes up and goes by
        for (int x = 8; x < DevMap.SX; x += 16) for (int z = 8; z < DevMap.SZ; z += 16) {
            g.teleport(g.player, x + 0.5f, DevMap.FLOOR, z + 0.5f); ticks(20);
        }
        g.teleport(g.player, 96.5f, DevMap.FLOOR, 96.5f); ticks(60 * 3);
        g.player.skills.setLevel(rpg.skill.Skill.MAGIC, 99); g.player.restore();
        g.player.book = rpg.combat.Spells.Book.SUN; g.player.mana = g.player.maxMana();
        var grass = nearestPlant(rpg.world.Foliage.Kind.TALL_GRASS);
        assertNotNull(grass);
        g.teleport(g.player, grass.x, DevMap.FLOOR, grass.z + 3f); g.controller.snapTo(0); ticks(30);
        float pitch = (float) Math.atan2(grass.y + 0.2f - g.player.eyeY(), 3f);
        assertTrue(g.combat.cast(g.player, rpg.combat.Spells.Spell.FIRE_STRIKE, 0, pitch));
        boolean lit = false;
        for (int i = 0; i < 60 * 6; i++) { game.tick(1f / 60); if ((g.chemistry.state(grass.id) & astratto.bridge.AstrattoNative.CHEM_BURNING) != 0 || grass.removed) lit = true; }
        assertTrue(lit, "the tall grass caught after a walk round the map: state " + g.chemistry.state(grass.id));
    }

    // ------------------------------------------------------------------ a saddle goes on however worked up the horse is
    @Test void aBoltingWildHorseCanStillBeSaddledByHoldingItsBridle() {
        for (int x = 56; x <= 73; x++) for (int z = 51; z <= 70; z++) for (int y = DevMap.FLOOR; y < DevMap.FLOOR + 8; y++) g.setBlock(x, y, z, Blocks.AIR);
        for (Npc o : g.npcs) if (o.distTo(64.5f, 60f) < 24) g.ai.unload(o);
        Animal h = g.animals.spawnNew(Species.HORSE, false, true, 64.5f, 62.5f, null, 0, 0);
        g.player.inv.clear();
        g.teleport(g.player, 66.0f, DevMap.FLOOR, 62.5f); game.tick(0);
        g.give(g.player, Items.SADDLE, 1);
        h.fear = 0.95f; h.threat = g.player.id; h.threatX = g.player.x; h.threatZ = g.player.z; h.threatSeen = g.time; h.goal = Animal.Goal.FLEE;
        float agitation = h.agitation();
        assertTrue(agitation > 0.6f, "worked up: " + agitation);
        var opts = rpg.interact.Interactions.ranked(h.options(g, g.player));
        var saddle = opts.stream().filter(o -> o.id().equals("saddle")).findFirst().orElseThrow();
        assertTrue(saddle.enabled(), "the saddle may be started on an agitated horse: " + saddle.reason());
        g.interact.primary(g.player, h);
        double start = g.time;
        for (int i = 0; i < 60 * 12 && !h.saddled; i++) game.tick(1f / 60);
        assertTrue(h.saddled, "saddled after holding it: goal " + h.goal + " fear " + h.fear + " outcome " + g.interact.lastOutcome);
        System.out.println("[saddle] agitated saddling took " + (g.time - start) + " s; fear now " + h.fear);
        assertTrue(g.time - start > 3.5, "an agitated horse takes longer to saddle than a calm one");
    }

    // ------------------------------------------------------------------ the crop farmer's round: harvest the ripe, sow the empty
    @Test void theCropFarmerHarvestsAndResowsHisRows() {
        g.state.minutes = 1440 + 9 * 60;   // Lunday morning: a working day
        Npc tobin = (Npc) g.actor(rpg.Ids.CROP_FARMER_CITY);
        var mine = g.farming.plots.stream().filter(p -> p.owner == tobin.id).toList();
        for (int i = 0; i < mine.size(); i++) { var p = mine.get(i); if (i % 2 == 0) { p.crop = rpg.world.Farming.Crop.CARROT; p.growth = 1; } else { p.crop = null; } }
        int sown0 = g.farming.sownCount, harvested0 = g.farming.harvestCount;
        java.util.LinkedHashMap<String, Integer> seen = new java.util.LinkedHashMap<>();
        for (int i = 0; i < 60 * 150; i++) { game.tick(1f / 60); if (i % 30 == 0) seen.merge(tobin.goal + ": " + String.valueOf(tobin.lastOutcome).replaceAll("[0-9]+", "#"), 1, Integer::sum); }
        System.out.println("[farmer] sown " + (g.farming.sownCount - sown0) + " harvested " + (g.farming.harvestCount - harvested0) + " seeds " + tobin.count(Items.CARROT_SEEDS) + "/" + tobin.count(Items.CABBAGE_SEEDS) + " " + seen);
        assertTrue(g.farming.harvestCount - harvested0 >= 3, "harvested: " + seen);
        assertTrue(g.farming.sownCount - sown0 >= 3, "sowed: " + seen);
    }

    // ------------------------------------------------------------------ a monster thwarted by prey up out of reach
    @Test void anOrcBelowAPlayerOnAWallRoarsAndThrowsStonesUp() {
        for (int x = 56; x <= 73; x++) for (int z = 51; z <= 70; z++) for (int y = DevMap.FLOOR; y < DevMap.FLOOR + 8; y++) g.setBlock(x, y, z, Blocks.AIR);
        for (Npc o : g.npcs) if (o.distTo(64.5f, 60f) < 30) g.ai.unload(o);
        // a block of wall three high, no way up it
        for (int x = 62; x <= 66; x++) for (int z = 58; z <= 62; z++) for (int y = 0; y < 3; y++) g.setBlock(x, DevMap.FLOOR + y, z, Blocks.STONE_BRICKS);
        g.player.skills.setLevel(rpg.skill.Skill.HEALTH, 99); g.player.restore();
        g.teleport(g.player, 64.5f, DevMap.FLOOR + 3, 60.5f); game.tick(0);
        var lair = g.monsters.addLair(rpg.monster.MonsterKind.ORC, "probe", 64.5f, 66.5f, 20, 24, 1);
        var orc = g.monsters.spawn(rpg.monster.MonsterKind.ORC, 22, 64.5f, 66.5f, lair);
        boolean roared = false; int thrown = 0;
        for (int i = 0; i < 60 * 20; i++) {
            int before = g.combat.projectiles.size();
            game.tick(1f / 60);
            g.player.hp = g.player.maxHp();
            if (g.time - orc.tauntAt < 0.05) roared = true;
            for (var p : g.combat.projectiles) if (p.owner == orc && "boulder".equals(p.what) && g.combat.projectiles.size() > before) { thrown++; break; }
        }
        System.out.println("[thwarted] roared " + roared + " thrown " + thrown + " last " + orc.lastOutcome + " state " + orc.state);
        assertTrue(roared, "it roared up at the player: " + orc.lastOutcome);
        assertTrue(thrown >= 1, "it threw stones up: " + orc.lastOutcome);
    }
}
