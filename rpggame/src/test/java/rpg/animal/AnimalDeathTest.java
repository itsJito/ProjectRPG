package rpg.animal;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import org.junit.jupiter.api.Test;
import rpg.Rpg;
import rpg.actor.Appearance;
import rpg.world.DevMap;
import static org.junit.jupiter.api.Assertions.*;

class AnimalDeathTest {
    @Test void killingAKnockedDownAnimalKeepsItsPhysicalBodyAndDisablesRecovery() {
        var cfg = new NativeEngine.Config(); cfg.deterministic = true;
        try (Game game = Game.create(cfg)) {
            game.activate(DevMap.build(game.engine()));
            Rpg g = game.add(new Rpg(game, "Animal death", new Appearance(), true));
            Animal a = g.animals.release(Species.COW, false, false, 64.5f, 66.5f);
            g.animals.struck(a, 1, 0, 70, true);
            assertNotNull(a.ragdoll);
            AnimalRagdoll fallen = a.ragdoll;
            a.amp = 0.6f; a.flap = 1; a.rearing = true;
            a.alive = false; a.hp = 0;
            g.animals.onKilled(a, g.player, 0);
            assertSame(fallen, a.ragdoll, "death must not replace a fallen body with a fresh upright one");
            assertFalse(a.ragdoll.knockdown);
            assertEquals(0, a.amp); assertEquals(0, a.flap); assertFalse(a.rearing);
            for (int i = 0; i < 240; i++) assertTrue(game.tick(1f / 60));
            assertSame(fallen, a.ragdoll, "dead animals never return to their living animation");
            assertEquals(0, a.body);
        }
    }
    @Test void aVisibleParkedCarcassCanBeCarriedAfterSavingAndLoading(@org.junit.jupiter.api.io.TempDir java.nio.file.Path folder) {
        var config=new NativeEngine.Config();config.deterministic=true;
        try(Game game=Game.create(config)) {
            game.activate(DevMap.build(game.engine()));Rpg g=game.add(new Rpg(game,"Parked body",new Appearance(),true));
            for(var npc:g.npcs)g.ai.unload(npc);
            Animal body=g.animals.release(Species.RABBIT,false,false,61.5f,64.5f);g.combat.bleedOutNow(body);
            for(int i=0;i<180;i++)game.tick(1f/60);
            g.animals.dropRagdoll(body);assertNull(body.ragdoll);assertFalse(body.restPose.isEmpty());
            long id=body.id;var saves=new rpg.SaveSystem(g);var file=folder.resolve("carcass.json");
            assertTrue(saves.save(file));assertTrue(saves.load(file));game.tick(0);
            body=g.animals.byId(id);assertTrue(body.exists());assertFalse(body.restPose.isEmpty());
            g.teleport(g.player,body.x,DevMap.FLOOR,body.z+1.5f);g.controller.snapTo(0);game.tick(0);
            var response=g.carrying.begin(g.player,body);assertFalse(response.rejected(),response.reason());
            for(int i=0;i<15;i++)game.tick(1f/60);
            assertEquals(id,g.player.carryBody,"a parked but visible body must regain physics and complete its pickup");
            assertNotNull(body.ragdoll);assertEquals(g.player.id,body.carriedBy);
        }
    }
}
