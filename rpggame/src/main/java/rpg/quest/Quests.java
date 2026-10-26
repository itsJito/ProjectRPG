package rpg.quest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import rpg.Chat;
import rpg.Ids;
import rpg.Rpg;
import rpg.item.ItemStack;
import rpg.item.Items;
import rpg.state.DomainEvent;

/**
 * The test-only authored quest (GDD 4.2 flow 8): a two-stage state machine driven by world facts. Jun asks the player
 * to carry a sealed letter to Kit. If Kit dies - by any valid world event - the quest fails permanently, with its
 * reason, and nothing replaces Kit to rescue it. The journal keeps only what the player actually learned.
 */
public final class Quests {
    public enum State { NOT_STARTED, ACTIVE, COMPLETED, FAILED }

    private final Rpg g;
    public State courier = State.NOT_STARTED;
    public String failReason;
    public final List<String> journal = new ArrayList<>();
    /** Debug flags the kiosk can set (authored-content hooks). */
    public final Map<String, Boolean> flags = new HashMap<>();

    public Quests(Rpg g) {
        this.g = g;
        g.state.subscribe(this::onEvent);
    }

    private void onEvent(DomainEvent e) {
        if (e.type().equals("ActorKilled") && e.subject() == Ids.CONTACT && courier != State.COMPLETED && courier != State.FAILED) {
            courier = State.FAILED;
            failReason = "Kit, the courier contact, is dead (" + e.payload() + ").";
            journal.add(stamp() + "Courier: I learned that Kit is dead. The delivery can never be made.");
            g.state.commit("QuestFailed", 0, Ids.CONTACT, null, e.id(), "QT-COURIER: " + failReason);
            g.chat.add("Quest failed: Courier - " + failReason, Chat.Kind.WARN);
        }
    }

    private String stamp() { return "[" + g.state.clockText() + "] "; }

    public void start() {
        if (courier != State.NOT_STARTED) return;
        if (!g.player.canStore(new ItemStack(Items.SEALED_LETTER, 1))) { g.chat.warn("You need a free inventory slot for the letter."); return; }
        g.player.store(new ItemStack(Items.SEALED_LETTER, 1));
        g.state.mint(Items.SEALED_LETTER, 1);
        courier = State.ACTIVE;
        journal.add(stamp() + "Courier: Jun gave me a sealed letter for Kit, who lives east of Store B in DevTown.");
        g.state.commit("QuestStarted", g.player.id, Ids.QUEST_GIVER, null, 0, "QT-COURIER stage 1: deliver the letter");
        g.chat.add("Quest started: Courier.", Chat.Kind.LEVEL);
    }

    public void deliver() {
        if (courier != State.ACTIVE) return;
        if (!g.player.has(Items.SEALED_LETTER, 1)) { g.chat.warn("You don't have the letter."); return; }
        ItemStack lamp = new ItemStack(Items.XP_LAMP, 1);
        g.player.takeItems(Items.SEALED_LETTER, 1);
        g.state.burn(Items.SEALED_LETTER, 1);
        g.grant(g.player, lamp);
        g.grant(g.player, new ItemStack(Items.COINS, 150));
        courier = State.COMPLETED;
        g.honor.choreDone("the courier's errand");
        g.ai.rumors.deed("carried Jun's letter to Kit, sealed and safe", Ids.CONTACT);
        journal.add(stamp() + "Courier: I delivered the letter to Kit. Reward: an experience lamp and 150 coins.");
        g.state.commit("QuestCompleted", g.player.id, Ids.CONTACT, null, 0, "QT-COURIER");
        g.chat.add("Quest complete: Courier!", Chat.Kind.LEVEL);
        g.sfx.ui("ui.LevelUp", 0.85f);
    }

    /** Debug: back to the fixture's start (explicitly a scenario reset, not an in-world rescue). */
    public void reset() {
        courier = State.NOT_STARTED;
        failReason = null;
        journal.clear();
        flags.clear();
        g.state.commit("QuestReset", 0, 0, null, 0, "QT-COURIER fixture reset (debug)");
    }

    public String stateText() { return courier + (failReason != null ? " - " + failReason : ""); }

    public Map<String, Object> save() {
        Map<String, Object> m = new HashMap<>();
        m.put("courier", courier.name()); m.put("fail", failReason); m.put("journal", new ArrayList<Object>(journal)); m.put("flags", new HashMap<String, Object>(flags));
        return m;
    }

    @SuppressWarnings("unchecked")
    public void load(Map<String, Object> m) {
        courier = State.valueOf(astratto.util.Json.str(m, "courier", "NOT_STARTED"));
        failReason = astratto.util.Json.str(m, "fail", null);
        journal.clear();
        List<Object> j = astratto.util.Json.list(m, "journal");
        if (j != null) for (Object o : j) journal.add(String.valueOf(o));
        flags.clear();
        Map<String, Object> f = astratto.util.Json.obj(m, "flags");
        if (f != null) for (var e : f.entrySet()) flags.put(e.getKey(), Boolean.TRUE.equals(e.getValue()));
    }
}
