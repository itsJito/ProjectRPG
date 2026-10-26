package rpg.ai;

import astratto.bridge.NativeEngine;
import astratto.game.Game;
import java.nio.file.Path;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import rpg.*;
import rpg.actor.Appearance;
import rpg.animal.Species;
import rpg.item.*;
import rpg.objects.Trap;
import rpg.world.DevMap;
import static org.junit.jupiter.api.Assertions.*;

class UltimateAiTest {
    private Game game;private Rpg g;
    @BeforeEach void start(){var config=new NativeEngine.Config();config.deterministic=true;game=Game.create(config);game.activate(DevMap.build(game.engine()));g=game.add(new Rpg(game,"AI policy",Appearance.henric(),true));for(Npc n:g.npcs)g.ai.unload(n);g.state.minutes=600;g.teleport(g.player,60.5f,DevMap.FLOOR,70.5f);}
    @AfterEach void close(){game.close();}
    @Test void guardsDiscardFriendlyTargetsEvenIfAnOldSaveContainsAnAccusation() {
        Npc cole=(Npc)g.actor(Ids.GUARD),bobwin=(Npc)g.actor(Ids.BOBWIN);g.ai.reload(cole);g.ai.reload(bobwin);
        g.teleport(cole,60.5f,DevMap.FLOOR,71.5f);g.teleport(bobwin,60.5f,DevMap.FLOOR,69.5f);game.tick(0);
        cole.fightTarget=bobwin.id;cole.cs.lastAttacker=bobwin.id;cole.cs.lastHurt=g.time;
        g.law.all().computeIfAbsent(cole.homeJurisdiction,key->new java.util.HashMap<>()).put(bobwin.id,500);
        assertNotSame(bobwin,g.ai.threatFor(cole));assertEquals(0,cole.fightTarget);
    }
    @Test void anEmptyBowCannotPreventGodricAnsweringANewAttacker() {
        Npc hunter=(Npc)g.actor(Ids.HUNTER);g.ai.reload(hunter);g.teleport(hunter,60.5f,DevMap.FLOOR,72.5f);hunter.yaw=0;game.tick(0);
        hunter.eq.set(ItemDef.Slot.AMMO,null);hunter.cs.lastAttacker=g.player.id;hunter.cs.lastHurt=g.time;
        hunter.fightTarget=0;hunter.goal=Brain.Goal.FIGHT;assertSame(g.player,g.ai.threatFor(hunter));
        hunter.fightTarget=g.player.id;g.ai.brain.act(hunter,1f/60);
        assertTrue(hunter.main()==null || hunter.main().family!=ItemDef.Family.BOW && hunter.main().family!=ItemDef.Family.CROSSBOW);
        assertEquals(g.player.id,hunter.fightTarget);
    }
    @Test void aSavedSnareRetainsItsCapturedAnimalUntilTheActualCarcassIsHarvested(@TempDir Path directory) {
        var rabbit=g.animals.all().stream().filter(a->a.species==Species.RABBIT && a.alive && !a.baby).findFirst().orElseThrow();
        var trap=g.objects.add(new Trap(g.state.newEntityId(),Ids.PREDATOR_HUNTER,rabbit.x,DevMap.FLOOR,rabbit.z));trap.contents.add(new ItemStack(Items.SNARE,1));
        rabbit.loaded=true;g.traps.update();assertEquals(rabbit.id,trap.caught);
        var saves=new SaveSystem(g);Path file=directory.resolve("snare.json");assertTrue(saves.save(file));assertTrue(saves.load(file));
        var restored=(Trap)g.objects.get(trap.id);var captured=g.animals.byId(rabbit.id);
        assertEquals(restored.id,captured.trappedBy);
        var tool=restored.contents.remove(Items.SNARE,1);restored.contents.add(new ItemStack(Items.BREAD,1));g.time+=0.3;g.traps.update();
        assertEquals(0,captured.trappedBy,"unrelated stored goods cannot replace the physical snare tool");assertEquals(0,restored.caught);
        restored.contents.remove(Items.BREAD,1);for(var item:tool)restored.contents.add(item);g.time+=0.3;g.traps.update();assertEquals(captured.id,restored.caught);
        captured.alive=false;g.time+=0.3;g.traps.update();
        assertEquals(captured.id,restored.caught,"a dead catch must remain available to the trapper for butchering");assertEquals(0,captured.trappedBy);
        captured.butchered=true;g.time+=0.3;g.traps.update();assertEquals(0,restored.caught);
    }
    @Test void anOrderKnightReportsItsActualAssignmentOnceAndRetainsTheReportAcrossLoad(@TempDir Path directory) {
        Npc knight=(Npc)g.actor(Ids.KNIGHT),commander=(Npc)g.actor(Ids.GUARD);g.ai.reload(knight);g.ai.reload(commander);
        g.teleport(knight,60.5f,DevMap.FLOOR,70.5f);g.teleport(commander,61.5f,DevMap.FLOOR,70.5f);game.tick(0);
        Profession.ORDER_KNIGHT.assign(knight);knight.dutyTarget=commander.id;knight.dutyX=knight.x;knight.dutyZ=knight.z;knight.goal=Brain.Goal.WORK;knight.nextThink=Double.MAX_VALUE;
        long[] xp=knight.skills.raw();g.ai.brain.act(knight,1f/60);g.ai.brain.act(knight,1f/60);
        assertEquals(2,knight.dutyPhase);
        for(int i=0;i<10;i++)g.ai.brain.act(knight,1f/60);
        assertEquals(1,g.state.all().stream().filter(event->event.type().equals("KnightDutyReported") && event.actor()==knight.id).count());
        assertArrayEquals(xp,knight.skills.raw());var saves=new SaveSystem(g);Path file=directory.resolve("order.json");assertTrue(saves.save(file));assertTrue(saves.load(file));assertEquals(2,knight.dutyPhase);
    }

}
