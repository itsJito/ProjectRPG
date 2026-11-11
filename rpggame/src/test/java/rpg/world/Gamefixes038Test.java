package rpg.world;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import rpg.Ids;
import rpg.Rpg;
import rpg.actor.Appearance;
import rpg.ai.Npc;
import rpg.item.ItemDef;
import rpg.item.Items;
import rpg.monster.Monster;
import rpg.monster.MonsterKind;
import rpg.objects.Breakable;
import rpg.objects.Furniture;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Alpha 0.3.8 gamefixes: the breakable crates, boxes, barrels and signs; furniture that breaks; a skeleton that falls
 * apart; the DevMansion, its rooms and its household; the new materials.
 */
class Gamefixes038Test {
    private Game game;
    private Rpg g;
    @BeforeEach void open() {
        var cfg = new NativeEngine.Config(); cfg.deterministic = true;
        game = Game.create(cfg); game.activate(DevMap.build(game.engine()));
        g = game.add(new Rpg(game, "Gamefixes 0.3.8", new Appearance(), true));
        game.tick(0);
    }
    @AfterEach void close() { game.close(); }

    private void ticks(int n) { for (int i = 0; i < n; i++) game.tick(1f / 60); }

    // ------------------------------------------------------------------ breakables
    @Test void aCrateIsAPhysicalBodyThatABlowCarvesAndAHardOneBursts() {
        Breakable b = g.breakables.place(new Breakable(g.state.newEntityId(), Breakable.Form.CRATE, 64.5f, DevMap.FLOOR + Breakable.Form.CRATE.hy() + 0.02f, 64.5f, 0, 0));
        b.contents.add(new rpg.item.ItemStack(Items.BREAD, 3));
        ticks(30);
        assertNotEquals(0, b.body, "it has a rigid body");
        assertEquals(DevMap.FLOOR + Breakable.Form.CRATE.hy(), b.y, 0.08f, "it rests on the ground");
        int built = b.built;
        g.breakables.strike(b, 6, 6, 0, 3f, 0, 0, 1, ItemDef.DamageType.SLASH, 0, g.player);
        assertTrue(b.lost > 0 && b.lost < built / 3, "a blow takes some of it, where it lands: " + b.lost);
        assertFalse(b.broken);
        int items = g.objects.all().size();
        g.breakables.strike(b, 6, 6, 0, 40f, 0, 0, 1, ItemDef.DamageType.BLUNT, 0, g.player);
        assertTrue(b.broken, "a hard enough blow bursts it");
        assertTrue(b.removed);
        assertFalse(g.breakables.debris.isEmpty(), "its boards fly as pieces of their own");
        assertTrue(g.objects.all().size() >= items - 1, "what it held spills");
        assertTrue(g.objects.all().stream().anyMatch(o -> o instanceof rpg.objects.GroundItem gi && gi.stack.def == Items.BREAD), "the bread is on the ground");
    }

    @Test void aSwungBladeStrikesTheCrateInItsPath() {
        Breakable b = g.breakables.place(new Breakable(g.state.newEntityId(), Breakable.Form.BARREL, 64.5f, DevMap.FLOOR + Breakable.Form.BARREL.hy() + 0.02f, 63.4f, 0, 0));
        ticks(20);
        g.teleport(g.player, 64.5f, DevMap.FLOOR, 64.6f); g.player.yaw = 0; g.controller.snapTo(0);
        g.player.eq.set(ItemDef.Slot.MAIN, new rpg.item.ItemStack(Items.get("bronze_sword"), 1));
        ticks(5);
        for (int k = 0; k < 4 && b.lost == 0; k++) { g.combat.attack(g.player, false, rpg.combat.CombatSystem.lightMove(g.player, false)); ticks(50); }
        assertTrue(b.lost > 0, "the blade cut into the barrel");
    }

    @Test void someonesCrateBrokenInTownIsVandalism() {
        Breakable b = g.breakables.place(new Breakable(g.state.newEntityId(), Breakable.Form.BOX, 100.5f, DevMap.FLOOR + Breakable.Form.BOX.hy() + 0.02f, 70.5f, 0, Ids.MERCHANT_A));
        ticks(10);
        g.breakables.breakApart(b, b.x, b.y, b.z, 0, 0, 1, 2, g.player);
        assertTrue(g.state.recent(40).stream().anyMatch(e -> e.type().equals("Broken")));
        assertTrue(g.state.recent(40).stream().anyMatch(e -> e.type().toUpperCase().contains("CRIME") || e.payload() != null && e.payload().contains("VANDALISM")), "a crime is on the books");
    }

    // ------------------------------------------------------------------ furniture that breaks
    @Test void furnitureLosesThePartsItIsStruckOnAndAtLastComesApart() {
        Furniture chair = null;
        for (Furniture f : g.objects.furniture) if (f.kind == Furniture.Kind.CHAIR) { chair = f; break; }
        assertNotNull(chair, "the mansion has chairs");
        var st = g.wreckage.of(chair);
        int parts = st.hp.length;
        g.wreckage.strike(chair, 0, chair.x, chair.floor + 0.2f, chair.z, 2f, 0, 0, 1, ItemDef.DamageType.BLUNT, g.player);
        assertTrue(st.gone.cardinality() > 0 && st.gone.cardinality() < parts, "some of it is knocked off");
        for (int k = 0; k < 30 && !st.wrecked; k++) g.wreckage.strike(chair, k % parts, chair.x, chair.floor + 0.5f, chair.z, 8f, 1, 0, 0, ItemDef.DamageType.BLUNT, g.player);
        assertTrue(st.wrecked, "beaten enough, it comes apart");
        assertFalse(chair.exists(), "and is gone from where it stood");
        assertFalse(g.wreckage.shards.isEmpty());
        // the world keeps it
        var saved = g.wreckage.save();
        g.wreckage.states.clear();
        g.wreckage.load(saved);
        assertTrue(g.wreckage.of(chair).wrecked);
    }

    // ------------------------------------------------------------------ the skeleton
    @Test void aSkeletonFallsApartIntoItsBones() {
        Monster sk = g.monsters.spawn(MonsterKind.SKELETON, 15, 30.5f, 40.5f, null);
        ticks(10);
        sk.hp = 0; sk.alive = false;
        g.monsters.onKilled(sk, g.player);
        assertEquals(0, sk.ragdoll, "no body to fall as one");
        long bones = g.bones.bones.stream().filter(b -> b.owner == sk.id).count();
        assertTrue(bones >= 15, "skull, jaw, ribs, spine, pelvis, every limb bone: " + bones);
        float y0 = (float) g.bones.bones.stream().filter(b -> b.owner == sk.id).mapToDouble(b -> g.lastRigids.body(b.body) == null ? 9 : g.lastRigids.body(b.body).y).max().orElse(0);
        ticks(180);
        float y1 = (float) g.bones.bones.stream().filter(b -> b.owner == sk.id).mapToDouble(b -> g.lastRigids.body(b.body) == null ? 9 : g.lastRigids.body(b.body).y).max().orElse(0);
        assertTrue(y1 < DevMap.FLOOR + 0.6f, "the bones lie on the ground: highest at " + y1 + " (from " + y0 + ")");
    }

    // ------------------------------------------------------------------ the mansion and its people
    @Test void theMansionStandsWithItsRoomsStairsAndHousehold() {
        assertTrue(g.vox.solid(Mansion.X0, DevMap.FLOOR + 1, Mansion.Z0 + 3), "its walls");
        assertTrue(Blocks.stairs(g.vox.block(44, DevMap.FLOOR, 149)), "the stair's foot");
        for (Mansion.Room r : Mansion.ROOMS) assertEquals(r.floor(), g.vox.block((int) r.cx(), r.y() - 1, (int) r.cz()) == r.floor() ? r.floor() : g.vox.block(r.x0(), r.y() - 1, r.z0()), r.name() + "'s floor");
        for (long id : new long[]{Ids.BARON, Ids.MANSION_KNIGHT, Ids.MANSION_COOK, Ids.MANSION_PORTER, Ids.MANSION_WOODCUTTER, Ids.MANSION_ORCHARDIST})
            assertTrue(g.actor(id) instanceof Npc n && n.alive, "the household: " + id);
        assertTrue(g.objects.furniture.stream().filter(f -> Mansion.inside(f.x, f.z)).count() > 100, "furnished");
        assertTrue(g.breakables.all.stream().anyMatch(b -> Mansion.inside(b.x, b.z) && b.contents.count(Items.CARROT) > 0), "a stocked pantry");
    }

    @Test void theHouseholdWorks() {
        g.state.minutes = 9 * 60;
        for (Npc n : g.npcs) if (!(n.id >= Ids.BARON && n.id <= Ids.MANSION_ORCHARDIST)) g.ai.unload(n);
        for (Furniture f : g.objects.furniture) if (f.kind == Furniture.Kind.FIREPLACE) f.fuel = 0;
        // (the event log keeps the latest few thousand: over three hours the first fires scroll out of it, so it is read as it goes)
        boolean lit = false, took = false, picked = false;
        for (int s = 0; s < 60 * 60 * 3; s++) {
            game.tick(1f / 60);
            if (s % 300 == 0) for (var e : g.state.all()) {
                lit |= e.type().equals("FireLit") && e.actor() == Ids.MANSION_WOODCUTTER;
                took |= e.type().equals("TookFromStore") && e.actor() == Ids.MANSION_COOK;
                picked |= e.actor() == Ids.MANSION_ORCHARDIST && (e.type().equals("ApplePicked") || e.type().equals("PutInStore") || e.type().contains("Pick"));
            }
        }
        assertTrue(lit, "the cutter lit a fire");
        assertTrue(took, "the cook took from the pantry");
        assertTrue(picked, "the orchard keeper picked");
        Npc baron = (Npc) g.actor(Ids.BARON);
        assertTrue(Mansion.onEstate(baron.x, baron.z), () -> "the Baron keeps to his house: " + baron.x + "," + baron.y + "," + baron.z + " " + baron.goal + " " + baron.lastOutcome + " " + baron.goalReason);
    }

    // ------------------------------------------------------------------ the materials
    @Test void theNewMaterialsAreBlocksWithTheirOwnTexturesAndWallPairs() {
        for (int id : new int[]{Blocks.WALLPAPER_GREEN, Blocks.PARQUET, Blocks.CHECKER_TILE, Blocks.SLATE_ROOF, Blocks.TIMBER_FRAME, Blocks.STAINED_GLASS, Blocks.GRAVEL})
            assertNotEquals(0xFF00FF, Blocks.mapColor(id) & 0xFFFFFF, "painted: " + id);
        int d = Blocks.dualOf(Blocks.TIMBER_FRAME, Blocks.WALLPAPER_RED, Blocks.IN_PZ);
        assertTrue(Blocks.dual(d));
        assertEquals(Blocks.TIMBER_FRAME, Blocks.outerOf(d));
        assertEquals(Blocks.WALLPAPER_RED, Blocks.innerOf(d));
        assertEquals(Blocks.IN_PZ, Blocks.innerDir(d));
        assertTrue(Blocks.glass(Blocks.STAINED_GLASS));
    }
}
