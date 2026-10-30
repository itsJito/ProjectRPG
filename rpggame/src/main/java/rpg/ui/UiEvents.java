package rpg.ui;

import java.util.ArrayList;
import java.util.List;
import rpg.Rpg;
import rpg.state.DomainEvent;
import rpg.world.DevMap;

/**
 * The interface's event bus. The game's committed domain events are read as they happen and turned into UI events about
 * the player ("combat", "law", "inventory", "xp", "quest", "world", "coins"); the interface adds its own ("scene",
 * "area", "chat", "focus"). Fragments wake on them; addons subscribe to them (and to every raw domain event as
 * "event:&lt;Type&gt;").
 */
public final class UiEvents {
    public interface Listener { void on(String type, String detail); }

    private final Rpg g;
    private final List<Listener> listeners = new ArrayList<>();
    private long lastSeen = -1;
    private String area;
    private int chatSeen = -1;

    UiEvents(Rpg g) { this.g = g; }

    public void listen(Listener l) { listeners.add(l); }

    public void emit(String type, String detail) {
        for (int i = 0; i < listeners.size(); i++) listeners.get(i).on(type, detail);
    }

    /** Reads what happened since the last frame. */
    void poll() {
        List<DomainEvent> recent = g.state.recent(96);
        if (lastSeen < 0) { lastSeen = recent.isEmpty() ? 0 : recent.get(recent.size() - 1).id(); }
        for (DomainEvent e : recent) {
            if (e.id() <= lastSeen) continue;
            lastSeen = e.id();
            String kind = classify(e);
            if (kind != null) emit(kind, e.type() + ": " + e.payload());
            emit("event:" + e.type(), e.payload());
        }
        String here = DevMap.jurisdiction(g.player.x, g.player.z);
        if (!here.equals(area)) { if (area != null) emit("area", here); area = here; }
        int lines = g.chat.total();
        if (lines != chatSeen) { if (chatSeen >= 0) emit("chat", ""); chatSeen = lines; }
    }

    private String classify(DomainEvent e) {
        long me = g.player.id;
        boolean mine = e.actor() == me || e.subject() == me;
        return switch (e.type()) {
            case "ActorHit", "TargetHit", "Parry", "Dodge", "ArrowLoosed", "SpellCast", "PrayerRaised", "ActorKilled", "ArrestResisted" -> mine ? "combat" : null;
            case "CrimeCommitted", "CrimeWitnessed", "CrimeReported", "BountyChanged", "BountyPaid", "Arrested", "TestimonyAdded", "PosseFormed", "FineDeclined", "WantedReported" -> mine ? "law" : null;
            case "BountyReminder" -> mine ? "bountynotice" : null;
            case "Pickpocketed", "ItemTaken", "ItemDropped", "ItemDeposited", "Equipped", "Unequipped", "Ate", "ResourceGathered", "TestSupplyTaken" -> mine ? "inventory" : null;
            case "Trade" -> mine ? "coins" : null;
            case "HonorChanged" -> "honor";
            case "ContainerOpened" -> mine ? "container" : null;
            case "LevelUp" -> mine ? "xp" : null;
            case "QuestStarted", "QuestCompleted", "QuestFailed", "QuestFlag", "QuestReset" -> "quest";
            case "TimeAdvanced", "Slept" -> "world";
            default -> null;
        };
    }
}
