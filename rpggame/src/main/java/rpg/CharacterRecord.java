package rpg;

import astratto.util.Json;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import rpg.item.ItemDef;
import rpg.item.ItemIO;
import rpg.item.ItemStack;
import rpg.objects.Container;
import rpg.skill.Skill;

/**
 * (0.4.1, gamefixes: every future version able to read a character's data) The character, apart from the world: who
 * they are and what is theirs - name and looks, every skill's experience (by the skill's name, never its place in a
 * list), what they carry and wear, their bank vault, the quests' progress and journal, their Honor, their quick slots,
 * spell and prayers. Written into every save ({@code "character"}) and beside it as {@code character.json}; read by any
 * later version when the rest of a save is not (another version's line, another world): the world starts fresh - the
 * physical things of the old one (where the player stood, the people, the dropped items, the doors) are not carried -
 * and the character steps into it at the map's spawn with everything that was theirs.
 * <p>
 * The format only ever grows: a newer field is ignored by an older reader, a missing one is left at the new game's own;
 * an item or a skill a later version no longer has is skipped with a note, never a failure. Items keep their owner,
 * their provenance and their stolen marks.
 */
public final class CharacterRecord {
    private CharacterRecord() {}

    /** The record's format (it only grows; a reader takes what it knows). */
    public static final int FORMAT = 1;

    /** The character as it stands now. */
    public static Map<String, Object> capture(Rpg g) {
        var p = g.player;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("format", FORMAT);
        m.put("savedBy", Main.VERSION);
        m.put("name", p.name);
        List<Object> look = new ArrayList<>(); for (int v : p.look.toArray()) look.add(v);
        m.put("look", look);
        Map<String, Object> skills = new LinkedHashMap<>();
        long[] xp = p.skills.raw();
        for (int i = 0; i < Skill.COUNT && i < xp.length; i++) skills.put(Skill.ALL[i].name(), xp[i]);
        m.put("skills", skills);
        m.put("inv", ItemIO.inventory(p.inv)); m.put("invW", p.inv.w); m.put("invH", p.inv.h);
        m.put("eq", equipmentByName(p));
        List<Object> quick = new ArrayList<>(); for (var q : p.quick) quick.add(q == null ? "" : q.item().id); m.put("quick", quick);
        m.put("spell", p.spell.name()); m.put("book", p.book.name());
        if (g.prayers != null) m.put("prayers", g.prayers.save(p));
        // the bank: every vault the account holds, its rows and what lies in it
        List<Object> vaults = new ArrayList<>();
        if (g.bank != null) for (Container c : g.bank.vaults(p.id)) { Map<String, Object> v = new HashMap<>(); v.put("cells", c.contents.size()); v.put("items", ItemIO.inventory(c.contents)); vaults.add(v); }
        m.put("bank", vaults);
        if (g.quests != null) m.put("quests", g.quests.save());
        if (g.honor != null) m.put("honor", g.honor.save());
        m.put("faith", (double) p.faith);
        return m;
    }

    /** Worn things by slot name (the slots' order may change; their names do not). */
    private static Map<String, Object> equipmentByName(rpg.actor.Player p) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (ItemDef.Slot s : ItemDef.Slot.values()) { ItemStack st = p.eq.get(s); if (st != null) m.put(s.name(), ItemIO.stack(st)); }
        return m;
    }

    /**
     * A record built from a whole save that predates the record (0.4.0 and before): the same things, read from where those
     * saves kept them (the player's block, the bank's vault objects, the quests and Honor).
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> fromSave(Map<String, Object> root) {
        Object direct = root.get("character");
        if (direct instanceof Map<?, ?> rec) return (Map<String, Object>) rec;
        Map<String, Object> pm = Json.obj(root, "player");
        if (pm == null) return null;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("format", FORMAT);
        m.put("savedBy", Json.str(root, "version", SaveSystem.UNSTAMPED));
        m.put("name", Json.str(pm, "name", "Adventurer"));
        m.put("look", Json.list(pm, "look"));
        // experience was a list in skill order: in those versions the order was Skill.ALL's own
        Map<String, Object> skills = new LinkedHashMap<>();
        List<Object> xp = Json.list(pm, "xp");
        if (xp != null) for (int i = 0; i < xp.size() && i < Skill.COUNT; i++) skills.put(Skill.ALL[i].name(), ((Number) xp.get(i)).longValue());
        m.put("skills", skills);
        m.put("inv", Json.list(pm, "inv")); m.put("invW", Json.integer(pm, "invW", 0));
        Map<String, Object> eq = new LinkedHashMap<>();
        List<Object> eqList = Json.list(pm, "eq");
        ItemDef.Slot[] slots = ItemDef.Slot.values();
        if (eqList != null) for (int i = 0; i < eqList.size() && i < slots.length; i++) if (eqList.get(i) != null) eq.put(slots[i].name(), eqList.get(i));
        m.put("eq", eq);
        m.put("quick", Json.list(pm, "quick"));
        m.put("spell", Json.str(pm, "spell", "WIND_STRIKE")); m.put("book", Json.str(pm, "book", "SUN"));
        if (pm.get("prayers") != null) m.put("prayers", pm.get("prayers"));
        long id = Json.lng(pm, "id", Ids.PLAYER);
        List<Object> vaults = new ArrayList<>();
        for (Object o : Json.list(root, "objects")) {
            if (!(o instanceof Map<?, ?> om)) continue;
            Map<String, Object> ob = (Map<String, Object>) om;
            if (!"vault".equals(Json.str(ob, "ckind", "")) || Json.lng(ob, "owner", 0) != id) continue;
            Map<String, Object> v = new HashMap<>();
            v.put("items", ob.get("items")); v.put("cells", Json.integer(ob, "gridCells", 0));
            vaults.add(v);
        }
        m.put("bank", vaults);
        if (root.get("quests") != null) m.put("quests", root.get("quests"));
        if (root.get("honor") != null) m.put("honor", root.get("honor"));
        return m;
    }

    /**
     * Puts a record's character into this game: the new world's starting things replaced by theirs (the ledger kept
     * straight - what the new game made for the start is unmade, what the record brings is made), the bank account
     * reopened with its vault, the quests and Honor restored. Returns notes on anything that could not be brought.
     */
    @SuppressWarnings("unchecked")
    public static List<String> apply(Rpg g, Map<String, Object> m) {
        List<String> notes = new ArrayList<>();
        var p = g.player;
        p.name = Json.str(m, "name", p.name);
        List<Object> look = Json.list(m, "look");
        if (look != null && !look.isEmpty()) {
            int[] v = new int[look.size()];
            for (int i = 0; i < v.length; i++) v[i] = ((Number) look.get(i)).intValue();
            p.look = rpg.actor.Appearance.fromArray(v); p.fitBody();
        }
        // skills by name: a skill this version no longer has is noted, one it has that the record lacks keeps its start
        Map<String, Object> skills = Json.obj(m, "skills");
        if (skills != null) for (var e : skills.entrySet()) {
            Skill s = null;
            for (Skill k : Skill.ALL) if (k.name().equals(e.getKey())) s = k;
            if (s == null) { notes.add("the skill " + e.getKey() + " is not in this version"); continue; }
            p.skills.setXp(s, ((Number) e.getValue()).longValue());
        }
        // what the new game gave at the start is unmade; what the record carries is made
        for (var grid : p.grids()) for (ItemStack s : grid.stacks()) g.state.burn(s.def, s.amount);
        for (ItemDef.Slot s : ItemDef.Slot.values()) { ItemStack st = p.eq.get(s); if (st != null) g.state.burn(st.def, st.amount); }
        p.inv.clear(); p.eq.clear();
        int invW = Json.integer(m, "invW", 0);
        if (invW > 0 && invW != p.inv.w) notes.add("the pack's shape changed (" + invW + " wide then, " + p.inv.w + " now): items were repacked");
        ItemIO.inventory(p.inv, invW > 0 && invW != p.inv.w ? stripPlaces(Json.list(m, "inv")) : Json.list(m, "inv"));
        Map<String, Object> eq = Json.obj(m, "eq");
        if (eq != null) for (var e : eq.entrySet()) {
            ItemDef.Slot slot = null;
            for (ItemDef.Slot s : ItemDef.Slot.values()) if (s.name().equals(e.getKey())) slot = s;
            ItemStack st = ItemIO.stack(e.getValue());
            if (slot == null || st == null) { notes.add("a worn " + e.getKey() + " could not be brought"); continue; }
            p.eq.set(slot, st);
        }
        for (var grid : p.grids()) for (ItemStack s : grid.stacks()) g.state.mint(s.def, s.amount);
        for (ItemDef.Slot s : ItemDef.Slot.values()) { ItemStack st = p.eq.get(s); if (st != null) g.state.mint(st.def, st.amount); }
        int lost = countMissing(Json.list(m, "inv"));
        if (lost > 0) notes.add(lost + " carried item kind(s) are not in this version");
        java.util.Arrays.fill(p.quick, null);
        List<Object> quick = Json.list(m, "quick");
        if (quick != null) for (int i = 0; i < Math.min(p.quick.length, quick.size()); i++)
            if (quick.get(i) instanceof String id && !id.isEmpty() && rpg.item.Items.saved(id) != null) p.quick[i] = new rpg.actor.Player.QuickSlot(rpg.item.Items.saved(id), 0);
        p.refreshQuick();
        try { p.spell = rpg.combat.Spells.Spell.of(Json.str(m, "spell", "WIND_STRIKE")); } catch (IllegalArgumentException e) { notes.add("the chosen spell is not in this version"); }
        try { p.book = rpg.combat.Spells.Book.of(Json.str(m, "book", "SUN")); } catch (IllegalArgumentException e) { notes.add("the spellbook is not in this version"); }
        if (g.prayers != null && Json.obj(m, "prayers") != null) g.prayers.load(p, Json.obj(m, "prayers"));
        // the bank account and its vault
        List<Object> vaults = Json.list(m, "bank");
        if (vaults != null && !vaults.isEmpty() && g.bank != null) {
            for (Object o : vaults) {
                if (!(o instanceof Map<?, ?> vm)) continue;
                Map<String, Object> v = (Map<String, Object>) vm;
                List<Object> items = Json.list(v, "items");
                int brought = g.bank.restoreAccount(p, items, Json.integer(v, "cells", 0));
                if (items != null && brought < items.size()) notes.add((items.size() - brought) + " banked item kind(s) are not in this version");
            }
        }
        if (g.quests != null && Json.obj(m, "quests") != null) try { g.quests.load(Json.obj(m, "quests")); } catch (RuntimeException e) { notes.add("the quests' progress could not be read (" + e.getMessage() + ")"); }
        if (g.honor != null && Json.obj(m, "honor") != null) g.honor.load(Json.obj(m, "honor"));
        // the record's experience is this world's starting point for the XP accounting, as a loaded save's is
        if (g.invariants != null) g.invariants.rebase(p);
        p.restore();
        return notes;
    }
    @SuppressWarnings("unchecked")
    private static List<Object> stripPlaces(List<Object> l) {
        List<Object> out = new ArrayList<>();
        if (l != null) for (Object o : l) if (o instanceof Map<?, ?> om) { Map<String, Object> c = new HashMap<>((Map<String, Object>) om); c.remove("x"); c.remove("y"); c.remove("r"); out.add(c); }
        return out;
    }
    @SuppressWarnings("unchecked")
    private static int countMissing(List<Object> l) {
        int n = 0;
        if (l != null) for (Object o : l) if (o instanceof Map<?, ?> om && rpg.item.Items.saved(Json.str((Map<String, Object>) om, "id", "")) == null) n++;
        return n;
    }

    /** The record beside a save: character.json in the same folder. */
    public static java.nio.file.Path besides(java.nio.file.Path save) {
        java.nio.file.Path dir = save.toAbsolutePath().getParent();
        return dir == null ? java.nio.file.Path.of("character.json") : dir.resolve("character.json");
    }
}
