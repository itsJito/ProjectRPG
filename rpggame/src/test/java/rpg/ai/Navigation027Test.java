package rpg.ai;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import astratto.bridge.*;
import astratto.game.*;
import rpg.world.*;
import java.util.*;
import static astratto.bridge.AstrattoNative.*;

class Navigation027Test {
    Game game;Voxels vox;
    @BeforeEach void open(){var cfg=new NativeEngine.Config();cfg.audioDevice=false;cfg.deterministic=true;game=Game.create(cfg);game.engine().blockTable(Blocks.engineDefs());long world=game.engine().createWorld(64,32,64,1,null,null,0);var c=new CommandBuffer();c.fill(0,0,0,63,3,63,Blocks.STONE|SET_RAW);game.engine().edit(world,c);c.free();game.activate(world);vox=new Voxels(game.view());}
    @AfterEach void close(){game.close();}
    int[] route(float x,float y,float z,float tx,float ty,float tz,boolean ladder){return vox.navigation.route(x,y,z,tx,ty,tz,d->true,ladder,99,null,0);}
    @Test void foliageAndAirborneStartsAgreeWithNativeWalkability(){
        game.commands().fill(10,4,10,15,6,15,Blocks.HEDGE);game.tick(0);
        assertTrue(vox.navigation.validPoint(12.5f,4,12.5f,d->true));
        assertEquals(4,vox.navigation.standY(12.5f,4.7f,12.5f));
        assertNotNull(route(12.5f,4.7f,12.5f,20.5f,4,12.5f,false));
        game.commands().blockSet(12,4,12,Blocks.STONE);vox.edited(12,4,12,Blocks.HEDGE,Blocks.STONE);game.tick(0);vox.afterCommands();
        assertFalse(vox.navigation.validPoint(12.5f,4,12.5f,d->true));
        game.commands().blockSet(12,4,12,Blocks.WATER);vox.edited(12,4,12,Blocks.STONE,Blocks.WATER);
        game.commands().blockSet(12,5,12,Blocks.WATER);vox.edited(12,5,12,Blocks.HEDGE,Blocks.WATER);game.tick(0);vox.afterCommands();
        assertFalse(vox.navigation.validPoint(12.5f,4,12.5f,d->true));
        game.commands().blockSet(12,5,12,Blocks.AIR);vox.edited(12,5,12,Blocks.WATER,Blocks.AIR);game.tick(0);vox.afterCommands();
        assertTrue(vox.navigation.validPoint(12.5f,4,12.5f,d->true));
    }
    @Test void generatedRegionsFindAnUnhintedGapAndRebuildOnlyEditedChunks(){
        game.commands().fill(24,4,1,24,8,62,Blocks.STONE);game.commands().fill(24,4,47,24,6,47,Blocks.AIR);game.tick(0);
        var path=route(10.5f,4,20.5f,45.5f,4,20.5f,false);assertNotNull(path);assertTrue(Arrays.stream(path).mapToObj(RuntimeNavigation::point).anyMatch(p->p[1]>46));
        long rebuilds=vox.navigation.rebuiltChunks;
        assertNotNull(route(10.5f,4,20.5f,45.5f,4,20.5f,false));assertEquals(rebuilds,vox.navigation.rebuiltChunks);
        game.commands().fill(24,4,47,24,6,47,Blocks.STONE);vox.edited(24,4,47,Blocks.AIR,Blocks.STONE);game.tick(0);vox.afterCommands();
        assertNull(route(10.5f,4,20.5f,45.5f,4,20.5f,false));assertTrue(vox.navigation.rebuiltChunks-rebuilds<=2);
    }
    @Test void stackedFloorsUseStairPortalsAndLaddersRespectCapability(){
        game.commands().fill(20,9,20,25,9,25,Blocks.STONE);
        for(int y=4;y<=10;y++)game.commands().blockSet(19,y,20,Blocks.LADDER);game.tick(0);
        assertNull(route(10.5f,4,20.5f,22.5f,10,22.5f,false));
        var ladder=route(10.5f,4,20.5f,22.5f,10,22.5f,true);assertNotNull(ladder);assertTrue(Arrays.stream(ladder).mapToObj(RuntimeNavigation::point).anyMatch(p->p[2]>4));
        for(int i=0;i<6;i++)game.commands().blockSet(22,4+i,14+i,Blocks.STAIRS);vox.edited(22,4,14,Blocks.AIR,Blocks.STAIRS);vox.edited(22,6,16,Blocks.AIR,Blocks.STAIRS);game.tick(0);vox.afterCommands();
        assertNotNull(route(22.5f,4,12.5f,22.5f,10,22.5f,false));
    }
    @Test void nativeLadderTravelActuallyReachesTheRoof(){
        int[] arrivals={0};long[] agent={0};
        game.commands().fill(20,9,20,25,9,25,Blocks.STONE);for(int y=4;y<=10;y++)game.commands().blockSet(19,y,20,Blocks.LADDER);
        game.add(new GameModule(){public void init(GameContext ctx){var c=ctx.commands();long policy=ctx.reserve(KIND_POLICY),body=ctx.reserve(KIND_MOVER);agent[0]=ctx.reserve(KIND_AGENT);c.policyCreate(policy,List.of(new CommandBuffer.Action(1,ACT_MOVE_TO_GOAL).base(1).speed(3)),.05f,.1f);c.moverCreate(body,17.5f,4,20.5f,.28f,1.8f,.6f,MOVER_AGENT,1.2f);c.agentCreate(agent[0],policy,body,17.5f,4,20.5f,1.6f,1,1,0,1,0,74);c.agentGoal(agent[0],22.5f,10,22.5f);}public void agentArrived(long id,long target){arrivals[0]++;}});
        for(int f=0;f<1200;f++)game.tick(1f/60);var a=game.engine().agents().agent(agent[0]);assertTrue(a.y>9.5,"ladder mover must reach the roof: "+a.x+","+a.y+","+a.z);assertTrue(arrivals[0]>0,"no arrival at "+a.x+","+a.y+","+a.z+" stuck="+a.stuck+" partial="+a.partial);
    }
    @Test void stairsCarryDifferentBodySizesAcrossSeveralFlightLengths(){
        long[] agents=new long[4];int[] arrivals={0};
        game.add(new GameModule(){
            public void init(GameContext ctx){var c=ctx.commands();long policy=ctx.reserve(KIND_POLICY);c.policyCreate(policy,List.of(new CommandBuffer.Action(1,ACT_MOVE_TO_GOAL).base(1).speed(3)),.05f,.1f);
                for(int i=0;i<4;i++){int x=10+i*10,steps=3+i;for(int j=0;j<steps;j++)c.fill(x-1,4+j,12+j,x+1,4+j,12+j,Blocks.STAIRS);
                    c.fill(x-2,3+steps,12+steps,x+2,3+steps,17+steps,Blocks.STONE);
                    long body=ctx.reserve(KIND_MOVER);agents[i]=ctx.reserve(KIND_AGENT);float radius=new float[]{.22f,.28f,.45f,.8f}[i],height=new float[]{.6f,1.8f,2.2f,2.6f}[i];
                    c.moverCreate(body,x+.5f,4,10.5f,radius,height,.6f,MOVER_AGENT,1.2f);c.agentCreate(agents[i],policy,body,x+.5f,4,10.5f,height*.85f,1,1,AGENT_NO_LADDERS,1,0,100+i);c.agentGoal(agents[i],x+.5f,4+steps,14.5f+steps);
                }
            }
            public void agentArrived(long id,long target){arrivals[0]++;}
        });
        for(int f=0;f<1200;f++)game.tick(1f/60);
        for(int i=0;i<4;i++){var a=game.engine().agents().agent(agents[i]);assertTrue(a.y>6.5+i,"body "+i+" stayed at "+a.y);}
        assertEquals(4,arrivals[0]);
    }
    @Test void blockedKnowledgeDecaysAndSurvivesSaveAndNearbyRetelling(){
        var n=new Npc(777,"Walker",Npc.Role.VILLAGER,"Walker",8,8);n.gx=20;n.goalY=4;n.gz=20;n.legsX=30;n.legsZ=30;
        n.routeBook.failed(n,1);n.routeBook.failed(n,4);assertTrue(n.routeBook.penalty(20,4,20,5)>0);
        var restored=new RouteBook();restored.load(n.routeBook.save(5),100);assertTrue(restored.penalty(20,4,20,101)>0);assertEquals(0,restored.penalty(20,4,20,150));
        var listener=new RouteBook();restored.tell(listener,101);assertTrue(listener.penalty(20,4,20,102)>0);assertTrue(listener.routes.stream().allMatch(r->r.confidence==0));
    }
}
