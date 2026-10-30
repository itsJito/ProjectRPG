package rpg.ui;
import java.util.*;
/** Select readable, distinct speech before drawing; labels and health bars have their own rules. */
public final class SpeechBudget {
    private SpeechBudget(){}
    public record Line(long speaker,String text,int priority,float distance){}
    public static Set<Long> select(List<Line> lines){
        var ordered=new ArrayList<>(lines);ordered.sort(Comparator.comparingInt(Line::priority).reversed().thenComparingDouble(Line::distance).thenComparingLong(Line::speaker));
        Set<Long> chosen=new HashSet<>();Set<String> heard=new HashSet<>();
        for(var line:ordered){if(line.text()==null || line.text().isBlank())continue;String key=line.text().toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+"," ").strip();if(heard.add(key))chosen.add(line.speaker());if(chosen.size()==6)break;}return chosen;
    }
}
