package rpg.ui;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SpeechBudgetTest {
    @Test void dialogueAndUrgentDistinctNewsWinTheSixAvailablePlaces(){
        var lines=new ArrayList<SpeechBudget.Line>();for(int i=0;i<20;i++)lines.add(new SpeechBudget.Line(i,"Small talk "+i,0,i));
        lines.add(new SpeechBudget.Line(40,"Stop! Thief!",400,15));lines.add(new SpeechBudget.Line(41,"stop, thief",350,1));lines.add(new SpeechBudget.Line(42,"Your conversation",1000,17));
        var selected=SpeechBudget.select(lines);assertEquals(6,selected.size());assertTrue(selected.containsAll(Set.of(40L,42L)));assertFalse(selected.contains(41L));
    }
}
