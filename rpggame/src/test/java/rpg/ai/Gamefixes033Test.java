package rpg.ai;

import static org.junit.jupiter.api.Assertions.*;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import java.util.ArrayList;
import org.junit.jupiter.api.*;
import rpg.Ids;
import rpg.Rpg;
import rpg.actor.Appearance;
import rpg.animal.Animal;
import rpg.animal.Species;
import rpg.item.ItemStack;
import rpg.item.Items;
import rpg.monster.Monster;
import rpg.world.Blocks;
import rpg.world.DevMap;

/** Alpha 0.3.3 gamefixes. */
class Gamefixes033Test {
    private Game game;
    private Rpg g;
    @BeforeEach void open() {
        var cfg = new NativeEngine.Config(); cfg.deterministic = true; game = Game.create(cfg); game.activate(DevMap.build(game.engine()));
        g = game.add(new Rpg(game, "Gamefixes 0.3.3", Appearance.henric(), true));
        for (Monster m : new ArrayList<>(g.monsters.all())) g.ai.unload(m);
        g.state.minutes = 13 * 60;
        game.tick(0);
    }
    @AfterEach void close() { game.close(); }
    private void ticks(int n) { for (int i = 0; i < n; i++) game.tick(1f / 60); }
    private void yard() {
        for (int x = 52; x <= 77; x++) for (int z = 40; z <= 92; z++) for (int y = DevMap.FLOOR; y < DevMap.FLOOR + 12; y++) g.setBlock(x, y, z, Blocks.AIR);
        for (Npc o : g.npcs) if (o.distTo(64.5f, 66f) < 34) g.ai.unload(o);
    }

    // ------------------------------------------------------------------ the watch asks about a small fine once, not over and over
    @Test void aPettyFineIsAskedForOnceAndNotAgainWhileItStandsUnchanged() {
        for (Npc n : g.npcs) n.memories.clear();
        g.player.store(new ItemStack(Items.COINS, 500));
        Npc bram = (Npc) g.actor(Ids.LAWMAN_A);
        Npc seer = g.npcs.stream().filter(n -> n.role == Npc.Role.VILLAGER && DevMap.ALPHA.equals(n.homeJurisdiction)).findFirst().orElseThrow();
        Memory m = new Memory("DISORDERLY", g.player.id, seer.id, DevMap.ALPHA, g.state.minutes, 1f, "witnessed", 77, 0, "disorderly");
        seer.remember(m, 24);
        g.teleport(seer, bram.x + 1, DevMap.FLOOR, bram.z);
        assertTrue(g.law.report(seer, m, bram).committed());
        assertTrue(g.law.petty(g.player.id, DevMap.ALPHA));
        Npc guard = (Npc) g.actor(Ids.GUARD);
        g.teleport(g.player, guard.x + 1.5f, DevMap.FLOOR, guard.z);
        var d = g.law.halt(guard, g.player);
        assertNotNull(d, "the guard asks for the fine");
        assertNotNull(d.dismissed, "closing the box without a word is an answer");
        d.dismissed.run();   // Escape, or walking off
        // a quarter of an hour of game seconds near the watch: nobody asks again about the same fine
        int asked = 0;
        for (int i = 0; i < 60 * 120; i++) {
            game.tick(1f / 60);
            if (i % 60 == 0 && g.law.halt(guard, g.player) != null) asked++;
        }
        assertEquals(0, asked, "the same small fine was asked for again");
        assertEquals(5, g.law.bounty(g.player.id, DevMap.ALPHA), "it stays on the books");
    }

    // ------------------------------------------------------------------ the townsfolk see someone on a ledge just above them
    @Test void someoneOnALedgeJustOverheadIsStillSeen() {
        yard();
        Npc n = (Npc) g.actor(Ids.GUARD);
        g.ai.reload(n);
        g.teleport(n, 64.5f, DevMap.FLOOR, 60.5f); n.yaw = 0;
        for (int y = DevMap.FLOOR; y < DevMap.FLOOR + 4; y++) g.setBlock(64, y, 59, Blocks.STONE);
        g.teleport(g.player, 64.5f, DevMap.FLOOR + 4, 59.5f);
        ticks(3);
        g.teleport(g.player, 64.5f, DevMap.FLOOR + 4, 59.5f);
        n.yaw = 0;
        float e = g.ai.stealth.exposure(n, g.player);
        assertTrue(e > 0.3f, "the player on the pillar top a pace away is seen: exposure " + e);
    }

    // ------------------------------------------------------------------ horse and rider go down together off a drop
    private Animal saddled(Species s, float x, float y, float z) {
        Animal h = g.animals.spawnNew(s, false, true, x, z, null, 0, 0);
        g.teleport(h, x, y, z);
        h.tamedBy = g.player.id; h.owner = g.player.id; h.saddled = true; h.yaw = 0;
        return h;
    }
    private void shelf() { for (int x = 58; x <= 71; x++) for (int z = 62; z <= 76; z++) for (int y = DevMap.FLOOR; y < DevMap.FLOOR + 8; y++) g.setBlock(x, y, z, Blocks.STONE); }

    @Test void aMountGoingOffAHighDropThrowsRiderAndMountDownTogether() {
        yard(); shelf();
        float top = DevMap.FLOOR + 8;
        g.teleport(g.player, 66.5f, top, 70.5f); ticks(3);
        Animal h = saddled(Species.HORSE, 64.5f, top, 70.5f); ticks(10);
        assertTrue(g.riding.mount(g.player, h).committed());
        g.controller.in.forward = 1;
        boolean together = false;
        for (int i = 0; i < 60 * 6 && !together; i++) { game.tick(1f / 60); together = g.player.riding == 0 && g.player.reacting && h.ragdoll != null; }
        g.controller.in.forward = 0;
        assertTrue(together, "rider ragdolled and the horse knocked down at once: riding " + g.player.riding + " reacting " + g.player.reacting + " horse ragdoll " + (h.ragdoll != null));
        float hpBefore = h.hp;
        ticks(60 * 4);
        assertEquals(0, g.player.riding, "never snapped back into the saddle");
        assertTrue(h.hp < hpBefore || h.hp < h.maxHp(), "the horse paid for the fall: " + h.hp + "/" + h.maxHp());
    }

    @Test void aPegasusGlidesWithTheJumpKeyHeld() {
        yard(); shelf();
        float top = DevMap.FLOOR + 8;
        g.teleport(g.player, 66.5f, top, 74.5f); ticks(3);
        Animal p = saddled(Species.PEGASUS, 64.5f, top, 74.5f); ticks(10);
        assertTrue(g.riding.mount(g.player, p).committed());
        g.controller.in.forward = 1; g.controller.in.jumpHeld = true;
        float worstSink = 0; boolean glided = false;
        for (int i = 0; i < 60 * 6; i++) {
            game.tick(1f / 60);
            var r = g.riding.of(g.player);
            if (r == null) break;
            if (r.gliding) { glided = true; worstSink = Math.min(worstSink, r.body.vy); }
        }
        g.controller.in.forward = 0; g.controller.in.jumpHeld = false;
        assertTrue(glided, "it glided off the edge");
        assertTrue(worstSink >= -rpg.mount.Riding.GLIDE_SINK - 0.4f, "a glide sinks slowly: " + worstSink);
        assertNotEquals(0, g.player.riding, "a glide down is no fall: still in the saddle");
    }

    @Test void aMountNeverJumpsAThreeBlockWall() {
        yard();
        for (int x = 58; x <= 71; x++) for (int y = DevMap.FLOOR; y < DevMap.FLOOR + 3; y++) g.setBlock(x, y, 60, Blocks.BRICK);
        g.teleport(g.player, 66.5f, DevMap.FLOOR, 74.5f); ticks(3);
        Animal h = saddled(Species.PEGASUS, 64.5f, DevMap.FLOOR, 74.5f);
        h.mountStats = rpg.mount.MountStats.pegasus(h.id); h.mountStats.jump = 3.0f;
        ticks(10);
        assertTrue(g.riding.mount(g.player, h).committed());
        var in = g.controller.in;
        for (int i = 0; i < 60 * 8; i++) {
            in.forward = 1; in.sprint = false;
            var r = g.riding.of(g.player);
            in.jumpHeld = r != null && h.z < 62.6f && h.z > 61.6f && !r.jumpWas;
            game.tick(1f / 60);
        }
        in.forward = 0; in.jumpHeld = false;
        assertTrue(h.z > 60.5f, "a three-block wall stops it: at z " + h.z + " y " + h.y + " wall " + g.vox.block(64, DevMap.FLOOR + 2, 60) + " events "
                + g.state.all().stream().filter(e -> e.type().startsWith("Mount") || e.type().equals("Dismounted")).map(e -> e.type() + ":" + e.payload()).toList());
    }

    // ------------------------------------------------------------------ businesses keep hours
    @Test void aShopIsClosedOutsideItsHoursAndSaysWhenItOpens() {
        var shop = g.econ.shopA;
        Npc ada = g.econ.merchant(shop);
        g.state.minutes = 1440 * 2 + 3 * 60;   // three in the morning
        assertFalse(rpg.econ.Hours.open(g, shop));
        String why = g.econ.tradeRefusal(ada, g.player);
        assertNotNull(why);
        assertTrue(why.contains("closed") && why.contains("opens"), why);
        g.state.minutes = 1440 * 2 + 10 * 60;
        assertTrue(rpg.econ.Hours.open(g, shop), "open mid-morning: " + rpg.econ.Hours.today(g, shop));
        assertTrue(rpg.econ.Hours.open(g, g.econ.fence) || rpg.econ.Hours.today(g, g.econ.fence).contains("quiet"));
    }
}
