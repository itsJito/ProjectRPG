package rpg.ui;

import java.util.ArrayList;
import java.util.List;

/** A dialogue box in the chat area (OSRS-style): who speaks, what they say, and the replies on offer. */
public final class Dialog {
    public record Choice(String label, Runnable action, boolean closes) {}

    public final String speaker, text;
    public final List<Choice> choices = new ArrayList<>();
    /** The NPC spoken to (0 none): the box closes if they die or the player walks off. */
    public long npc;
    /** The game menu uses the pause presentation while retaining ordinary numbered-choice handling. */
    public boolean pauseMenu;
    public int focusedChoice, choiceOffset, textOffset;

    public Dialog(String speaker, String text) { this.speaker = speaker; this.text = text; }
    public Dialog closeChoice(String label,Runnable action) { choices.add(new Choice(label,action,true));return this; }
    public Dialog choice(String label, Runnable action) { choices.add(new Choice(label, action, false)); return this; }
}
