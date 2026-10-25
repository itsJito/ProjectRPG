package astratto.ai;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static astratto.ai.Behavior.Status.*;

class BehaviorTest {
    @Test void runningSequenceNeverRepeatsCommittedActions() {
        int[] calls={0,0};Behavior.Node<int[]> sequence=Behavior.sequence(List.of(c->{c[0]++;return SUCCESS;},c->++c[1]<3?RUNNING:SUCCESS));
        assertEquals(RUNNING,sequence.tick(calls));assertEquals(RUNNING,sequence.tick(calls));assertEquals(SUCCESS,sequence.tick(calls));
        assertArrayEquals(new int[]{1,3},calls);
    }
    @Test void higherPriorityBranchCancelsThePreviousRunningAction() {
        boolean[] urgent={false};int[] canceled={0};
        Behavior.Node<boolean[]> ordinary=new Behavior.Node<>(){public Behavior.Status tick(boolean[] c){return RUNNING;}public void reset(){canceled[0]++;}};
        var tree=Behavior.selector(List.of(Behavior.condition((boolean[] c)->c[0]),ordinary));
        assertEquals(RUNNING,tree.tick(urgent));urgent[0]=true;assertEquals(SUCCESS,tree.tick(urgent));assertEquals(1,canceled[0]);
    }
    @Test void invalidatedMethodsReplanBeforeExecutingAndDomainsDoNotChangeMovementFlags() {
        boolean[] supplied={true};int[] work={0};
        var plan=new TaskNetwork<>(List.of(new TaskNetwork.Method<boolean[]>("work",c->c[0],c->ctx->{work[0]++;return RUNNING;}),new TaskNetwork.Method<boolean[]>("rest",c->!c[0],c->ctx->SUCCESS)));
        assertEquals(RUNNING,plan.tick(supplied));supplied[0]=false;assertEquals(SUCCESS,plan.tick(supplied));assertEquals(1,work[0]);
        assertEquals(AgentDomain.ANIMAL,AgentDomain.fromFlags(AgentDomain.ANIMAL.flags|9));
        assertEquals(AgentDomain.MONSTER,AgentDomain.fromFlags(AgentDomain.MONSTER.flags));
        assertThrows(IllegalArgumentException.class,()->AgentDomain.fromFlags(3<<16));
    }
}
