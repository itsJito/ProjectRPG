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
    public rpg.ai.Npc.EmploymentState employment;
    /** The game menu uses the pause presentation while retaining ordinary numbered-choice handling. */
    public boolean pauseMenu;
    /** Spoken in a standoff (the watch halting someone wanted): the speaker is squared up to the player, not free to chat, and the replies still stand. */
    public boolean standoff;
    public int focusedChoice, choiceOffset, textOffset;
    /** An item held out to the player (gamefixes): the box becomes the offer card, Accept or Decline. */
    public rpg.item.ItemStack offer;

    public Dialog(String speaker, String text) { this.speaker = speaker; this.text = text; }
    /**
     * Whether a reply can still be given (gamefixes: paying a guard who had halted you did nothing): an NPC's box stands
     * while they can talk and their work has not changed under it; a halting officer is in a fight stance and cannot
     * chat, but the replies to his challenge stand while he lives.
     */
    public boolean stands(rpg.Rpg g) {
        if (!(g.actor(npc) instanceof rpg.ai.Npc speaker)) return true;
        return standoff ? speaker.alive : speaker.canTalk(g) && (employment == null || employment == speaker.employmentState());
    }
    public Dialog closeChoice(String label,Runnable action) { choices.add(new Choice(label,action,true));return this; }
    public Dialog choice(String label, Runnable action) { choices.add(new Choice(label, action, false)); return this; }
}
