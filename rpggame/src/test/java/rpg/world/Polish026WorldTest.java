package rpg.world;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static astratto.bridge.AstrattoNative.*;
import astratto.bridge.*;
import astratto.game.*;
import java.nio.file.Path;
import rpg.*;
import rpg.actor.*;
import rpg.ai.*;
import rpg.animal.*;
import rpg.item.*;
class Polish026WorldTest {
    Game game;Rpg g;
    @BeforeEach void open(){var config=new NativeEngine.Config();config.audioDevice=false;config.deterministic=true;game=Game.create(config);game.activate(DevMap.build(game.engine()));g=game.add(new Rpg(game,"World review",new Appearance(),true));g.state.minutes=600;g.ai.syncAbstractClock();for(var n:g.npcs)g.ai.unload(n);g.timeSystem.weather.set(astratto.weather.Weather.Kind.CLEAR);g.timeSystem.weather.snap();g.teleport(g.player,62.5f,DevMap.FLOOR,66.5f);}
    @AfterEach void close(){game.close();}
    void frames(int count){for(int i=0;i<count;i++)game.tick(1f/60);}
    @Test void livingAnimalsAndParkedBodiesKeepTheirOwnBurnMarks(@TempDir Path folder){
        var animal=g.animals.release(Species.COW,false,false,64.5f,66.5f);animal.puppet=true;game.tick(0);
        float[] center=new float[3];g.chemistry.center(animal,center);g.chemistry.emit(null,CHEM_FIRE,center[0],center[1],center[2],.8f,5,2);
        frames(240);assertTrue(animal.charred>0,"a living animal catches fire");assertTrue(animal.blood.scorched(),"fire lays localized scorch marks on animals");
        g.combat.bleedOutNow(animal);frames(90);
        var npc=(Npc)g.actor(Ids.VILLAGER);g.ai.reload(npc);g.teleport(npc,66.5f,DevMap.FLOOR,67.5f);npc.nextThink=Double.MAX_VALUE;game.tick(0);g.combat.bleedOutNow(npc);frames(120);
        assertNotEquals(0,npc.corpse);if(npc.ragdoll!=0){game.commands().release(npc.ragdoll);npc.ragdoll=0;}npc.loaded=false;game.tick(0);
        g.chemistry.center(npc,center);g.chemistry.emit(null,CHEM_FIRE,center[0],center[1],center[2],.8f,5,2);frames(240);
        assertTrue(npc.blood.scorched(),"parked bodies stay registered with chemistry");assertTrue(npc.charred>0);
        animal.puppet=false;long animalId=animal.id;var save=new SaveSystem(g);assertTrue(save.save(folder.resolve("char.json")));assertTrue(save.load(folder.resolve("char.json")));assertTrue(g.animals.byId(animalId).blood.scorched(),"animal scorch marks survive loading");assertTrue(((Npc)g.actor(npc.id)).blood.scorched());
    }
    @Test void masonryRepairsUseRealStoneAndLeaveUnrelatedMaterialsAlone(){
        g.teleport(g.player,100.5f,DevMap.FLOOR,73.5f);var worker=(Npc)g.actor(Ids.ARTHUR);g.ai.reload(worker);g.teleport(worker,101.5f,DevMap.FLOOR,70.5f);worker.goal=Brain.Goal.WORK;worker.nextThink=Double.MAX_VALUE;
        int surplus=Math.max(0,worker.count(Items.PLANK)-2);
        if(surplus>0)for(var stack:worker.takeItems(Items.PLANK,surplus))g.objects.drop(stack,worker.x,worker.y,worker.z);
        worker.inv.sort();g.give(worker,Items.get("stone_block"),2);assertEquals(2,worker.count(Items.get("stone_block")),"repair supplies must fit in the fixture's pack");g.setBlock(102,DevMap.FLOOR+1,70,Blocks.BRICK);game.tick(0);g.repairs.clear();g.setBlock(102,DevMap.FLOOR+1,70,Blocks.AIR);game.tick(0);
        for(int i=0;i<g.npcs.size()*2;i++){g.time+=.1;g.repairs.update();}g.time+=Repairs.SETTLE;
        var damage=g.repairs.open().stream().filter(d->d.x()==102&&d.y()==DevMap.FLOOR+1&&d.z()==70).findFirst().orElseThrow();int stone=worker.count(Items.get("stone_block")),planks=worker.count(Items.PLANK);
        assertEquals(damage,g.repairs.claim(worker),"loaded="+worker.loaded+" asleep="+worker.asleep()+" material="+Repairs.material(worker,damage)+" record="+g.repairs.save());assertTrue(g.repairs.mend(worker,damage));game.tick(0);assertEquals(Blocks.BRICK,g.vox.block(102,DevMap.FLOOR+1,70));assertEquals(stone-1,worker.count(Items.get("stone_block")));assertEquals(planks,worker.count(Items.PLANK));
    }
    @Test void ragdollStartsAtSevenBlocksOfUnsupportedDescent(){
        var bodies=new Snapshots.Bodies();var mover=new Snapshots.Mover();mover.handle=g.player.body;mover.y=20;bodies.movers.add(mover);
        g.falling.afterFrame(bodies,1f/60);mover.y=13.01f;g.falling.afterFrame(bodies,1f/60);assertFalse(g.player.reacting);
        mover.y=13;g.falling.afterFrame(bodies,1f/60);assertTrue(g.player.reacting);assertNotEquals(0,g.player.ragdoll);
    }

    @Test void openingAndClosingADoorNeverCreatesARepairJob(){
        var door=g.objects.jailDoor(DevMap.ALPHA);door.setLocked(g,false);door.setOpen(g,true,g.player,g.state.newRequestId());game.tick(0);
        assertTrue(g.repairs.open().stream().noneMatch(d->d.original()==Blocks.DOOR || d.original()==Blocks.LOCKED_DOOR));
        door.setOpen(g,false,g.player,g.state.newRequestId());game.tick(0);
        assertTrue(g.repairs.open().stream().noneMatch(d->d.original()==Blocks.DOOR || d.original()==Blocks.LOCKED_DOOR));
    }

    @Test void avoidingOneFailedDestinationDoesNotFreezeEveryOtherErrand(){
        var npc=(Npc)g.actor(Ids.VILLAGER);g.ai.reload(npc);g.teleport(npc,64.5f,DevMap.FLOOR,68.5f);npc.nextThink=Double.MAX_VALUE;game.tick(0);
        npc.goal=Brain.Goal.WORK;npc.legsX=90;npc.legsZ=70;
        npc.routeBook.begin(npc,90,70,new int[0],g.time);npc.routeBook.failed(npc,g.time);
        npc.routeBook.begin(npc,90,70,new int[0],g.time);g.ai.agentStuck(npc.agent);
        assertEquals(g.time+30,npc.routeBook.avoiding(90,70,g.time));assertTrue(npc.navPauseUntil<=g.time+2);
    }

    @Test void aDestinationCanChangeFloorWithoutChangingItsHorizontalPosition(){
        var npc=(Npc)g.actor(Ids.VILLAGER);g.ai.reload(npc);g.teleport(npc,64.5f,DevMap.FLOOR,68.5f);npc.nextThink=Double.MAX_VALUE;
        g.setBlock(66,DevMap.FLOOR+3,68,Blocks.STONE);g.setBlock(66,DevMap.FLOOR+7,68,Blocks.STONE);game.tick(0);
        assertFalse(g.ai.walkToAtHeight(npc,66.5f,DevMap.FLOOR+4,68.5f,false,.5f));assertEquals(DevMap.FLOOR+4,npc.goalY);
        assertFalse(g.ai.walkToAtHeight(npc,66.5f,DevMap.FLOOR+8,68.5f,false,.5f));assertEquals(DevMap.FLOOR+8,npc.goalY);
    }

}
