package rpg.world;

import astratto.bridge.*;
import astratto.game.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static astratto.bridge.AstrattoNative.*;
import static org.junit.jupiter.api.Assertions.*;

class Traversal028Test {
    @Test void theActualDevMapCourseHasTraversableStairsAndALadderLanding() {
        var cfg=new NativeEngine.Config();cfg.audioDevice=false;cfg.deterministic=true;
        try(var game=Game.create(cfg)) {
            game.activate(DevMap.build(game.engine()));long[] agents=new long[2];int[] arrivals={0,0};
            game.add(new GameModule() {
                public void init(GameContext ctx) {
                    var c=ctx.commands();long policy=ctx.reserve(KIND_POLICY);
                    c.policyCreate(policy,List.of(new CommandBuffer.Action(1,ACT_MOVE_TO_GOAL).base(1).speed(3)),.05f,.1f);
                    for(int i=0;i<2;i++) {
                        float x=i==0?165.5f:163.5f,z=i==0?12.5f:20.5f;
                        long body=ctx.reserve(KIND_MOVER);agents[i]=ctx.reserve(KIND_AGENT);
                        c.moverCreate(body,x,DevMap.FLOOR,z,.28f,1.8f,.6f,MOVER_AGENT,1.2f);
                        c.agentCreate(agents[i],policy,body,x,DevMap.FLOOR,z,1.6f,1,1,i==0?AGENT_NO_LADDERS:0,1,0,2800+i);
                        c.agentGoal(agents[i],i==0?166.5f:164.5f,DevMap.FLOOR+4,20.5f);
                    }
                }
                public void agentArrived(long id,long target){for(int i=0;i<2;i++)if(id==agents[i])arrivals[i]++;}
            });
            for(int f=0;f<1200;f++)game.tick(1f/60);
            for(int i=0;i<2;i++) {
                var a=game.engine().agents().agent(agents[i]);
                assertTrue(a.y>DevMap.FLOOR+3.5f,"course "+i+" stopped at "+a.x+","+a.y+","+a.z);
                assertTrue(arrivals[i]>0,"course "+i+" never arrived");
            }
        }
    }
}
