package rpg.ai;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import astratto.bridge.*;
import astratto.game.*;
import rpg.world.*;
import static astratto.bridge.AstrattoNative.*;
class Navigation026Test {
    Game game;
    Game world(int x,int z){var config=new NativeEngine.Config();config.deterministic=true;config.audioDevice=false;game=Game.create(config);game.engine().blockTable(new NativeEngine.BlockDef[]{new NativeEngine.BlockDef(0),new NativeEngine.BlockDef(BLOCK_SOLID|BLOCK_OPAQUE)});long world=game.engine().createWorld(x,24,z,1,null,null,0);var c=new CommandBuffer();c.fill(0,0,0,x-1,3,z-1,1|SET_RAW);game.engine().edit(world,c);c.free();game.activate(world);return game;}
    @AfterEach void close(){if(game!=null)game.close();}
    @Test void partialSearchesAreCountedAndNeverArriveAtAnUnreachableGoal(){
        world(256,128);long[] id=new long[1];int[] arrived={0},stuck={0};
        game.add(new GameModule(){public void init(GameContext ctx){var c=ctx.commands();c.fill(128,4,0,128,12,127,1);long policy=ctx.reserve(KIND_POLICY),body=ctx.reserve(KIND_MOVER);id[0]=ctx.reserve(KIND_AGENT);c.policyCreate(policy,List.of(new CommandBuffer.Action(1,ACT_MOVE_TO_GOAL).base(1).speed(6)),.05f,.1f);c.moverCreate(body,20.5f,4,64.5f,.28f,1.8f,.6f,MOVER_AGENT,1.2f);c.agentCreate(id[0],policy,body,20.5f,4,64.5f,1.6f,1,1,0,1,0,32);c.agentGoal(id[0],220.5f,4,64.5f);}public void agentArrived(long agent,long target){arrived[0]++;}public void agentStuck(long agent){stuck[0]++;}});
        for(int frame=0;frame<60*65;frame++)game.tick(1f/60);
        var agent=game.engine().agents().agent(id[0]);assertNotNull(agent);assertTrue(agent.partialPaths>0);assertTrue(agent.capHits>0);assertTrue(agent.expansions>0);assertEquals(0,arrived[0]);assertTrue(stuck[0]>0);assertTrue(agent.x<128);
    }
    @Test void standableSurfacesKeepSeparateFloorsAndChunkVersionsStayLocal(){
        world(64,64);var commands=game.commands();commands.fill(20,9,20,22,9,22,1);game.tick(0);var vox=new Voxels(game.view());
        assertEquals(4,vox.standY(21.5f,4,21.5f));assertEquals(10,vox.standY(21.5f,10,21.5f));
        long far=vox.chunkVersion(50,50);vox.edited(21,9,21,1,0);assertEquals(far,vox.chunkVersion(50,50));assertTrue(vox.chunkVersion(21,21)>0);
    }
    @Test void blockedRoutesRetainTheirPenaltyThroughSavingAndDoNotSpreadAsVerifiedNews(){
        var book=new RouteBook();var npc=new Npc(999,"Walker",Npc.Role.VILLAGER,"Walker",10,10);npc.legsX=40;npc.legsZ=40;
        book.begin(npc,40,40,new int[0],1);assertEquals(3,book.failed(npc,1));book.begin(npc,40,40,new int[0],4);assertEquals(34,book.failed(npc,4));
        var restored=new RouteBook();restored.load(book.save(5),100);assertEquals(129,restored.avoiding(40,40,100));assertEquals(0,book.tell(new RouteBook(),5));
    }
    @Test void anUnreachableFloorEmitsBlockedInsteadOfFreezingAtTheSameHorizontalPosition(){
        world(48,48);int[] arrived={0},stuck={0};long[] agent={0};
        game.add(new GameModule(){public void init(GameContext ctx){var c=ctx.commands();c.blockSet(20,11,20,1);long policy=ctx.reserve(KIND_POLICY),body=ctx.reserve(KIND_MOVER);agent[0]=ctx.reserve(KIND_AGENT);c.policyCreate(policy,List.of(new CommandBuffer.Action(1,ACT_MOVE_TO_GOAL).base(1).speed(3.5f)),.05f,.1f);c.moverCreate(body,20.5f,4,20.5f,.28f,1.8f,.6f,MOVER_AGENT,1.2f);c.agentCreate(agent[0],policy,body,20.5f,4,20.5f,1.6f,1,1,0,1,0,74);c.agentGoal(agent[0],20.5f,12,20.5f);}public void agentArrived(long id,long target){arrived[0]++;}public void agentStuck(long id){stuck[0]++;}});
        for(int frame=0;frame<240;frame++)game.tick(1f/60);
        assertEquals(0,arrived[0]);assertTrue(stuck[0]>0,"vertical failure must reach the same BLOCKED recovery as horizontal failure");
    }

    @Test void arrivingAtAnUnrelatedErrandCannotVerifyAnInterruptedJourney(){
        world(64,64);var vox=new Voxels(game.view());var npc=new Npc(993,"Walker",Npc.Role.VILLAGER,"Walker",10,10);var book=npc.routeBook;
        book.begin(npc,40,40,new int[0],0);book.success(vox,10,10,4,10);
        assertNull(book.recall(npc,40,40,vox,10,door->true));
        book.begin(npc,40,40,new int[0],10);book.success(vox,20,40,4,40);
        assertNotNull(book.recall(npc,40,40,vox,20,door->true),"only arrival at that route's destination verifies it");
    }

}
