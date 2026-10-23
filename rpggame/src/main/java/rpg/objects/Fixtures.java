package rpg.objects;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import rpg.Rpg;
import rpg.actor.Actor;
import rpg.interact.Option;
import rpg.interact.Result;
import rpg.interact.Work;
import rpg.interact.WorldObject;
import rpg.item.ItemDef;
import rpg.item.ItemStack;
import rpg.item.Items;
import rpg.item.Inventory;
import rpg.skill.Skill;
import rpg.skill.XpTable;

/** The devmap's labelled test fixtures that are not containers, nodes or stations. */
public final class Fixtures {
    private Fixtures() {}

    /**
     * An altar (GDD 2.5, 3.11): praying at it restores Faith to the full (the one way the pool fills besides holy drink),
     * and its rite opens another spellbook - Standard, Ancients, Lunar or Arcane - in place of the one in use. Nothing
     * that has not been learned is unlocked by it.
     */
    public static final class Altar extends WorldObject {
        public Altar(long id, float x, float y, float z) { super(id, "altar", "Altar", x, y, z); }
        @Override public float focusRadius() { return 1.0f; }
        @Override public List<Option> options(Rpg g, Actor a) {
            List<Option> o = new ArrayList<>();
            o.add(Option.when(a.faith < a.maxFaith(), "pray", "Pray", 10, "Your Faith is already full.", g.cfg.interactRange));
            o.add(Option.when(a.has(rpg.item.Items.COINS, 25), "donate", "Leave an offering (25 coins)", 4, "You need 25 coins.", g.cfg.interactRange));
            if (a instanceof rpg.actor.Player p) for (rpg.combat.Spells.Book b : rpg.combat.Spells.Book.values())
                if (b != p.book) o.add(Option.on("book:" + b.name(), "Take up the " + b.title + " spellbook", 5, g.cfg.interactRange));
            o.add(Option.on("examine", "Examine", 0, 5));
            return o;
        }
        @Override public Result perform(Rpg g, Actor a, String action, long req) {
            if (action.equals("examine")) { if (a == g.player) g.chat.say(examine(g)); return Result.ok(); }
            String why = reach(g, a, g.cfg.interactRange);
            if (why != null) return Result.no(why);
            if (action.startsWith("book:") && a instanceof rpg.actor.Player p) {
                p.book = rpg.combat.Spells.Book.valueOf(action.substring(5));
                for (rpg.combat.Spells.Spell s : rpg.combat.Spells.Spell.values()) if (s.book == p.book && s.level <= p.skills.level(Skill.MAGIC)) { p.spell = s; break; }
                g.state.commit(req, "SpellbookChanged", a.id, id, jurisdiction(), 0, p.book.title);
                g.chat.say("You kneel at the altar; the " + p.book.title + " spellbook opens to you.");
                if (g.sfx != null) g.sfx.ui("SpellBookCloseA", 0.5f);
                return Result.ok();
            }
            if (action.equals("donate")) {
                if (!a.has(rpg.item.Items.COINS, 25)) return Result.no("You need 25 coins.");
                a.takeItems(rpg.item.Items.COINS, 25);
                g.state.burn(rpg.item.Items.COINS, 25);
                g.state.commit(req, "Donated", a.id, id, jurisdiction(), 0, "25 coins to the shrine");
                if (a == g.player) { g.chat.say("You leave 25 coins in the offering bowl."); g.honor.donated(25); g.ai.rumors.deed("left an offering at the shrine", 0); }
                return Result.ok();
            }
            if (!action.equals("pray")) return Result.no("You can't do that.");
            g.interact.begin(new Work(a, this, "pray", "Praying", 2.0f, false, g.time, () -> {
                float before = a.faith;
                a.faith = a.maxFaith();
                g.state.commit("Prayed", a.id, id, jurisdiction(), 0, String.format("faith %.0f -> %.0f", before, a.faith));
                if (a == g.player) g.chat.say("You pray at the altar. Your Faith is restored.");
                if (g.sfx != null) g.sfx.play("bless", x, y + 1, z, 0.6f);
                return Result.ok();
            }));
            return Result.started();
        }
        @Override public String examine(Rpg g) { return "An altar: pray here to restore your Faith, or take up another spellbook."; }
    }

    /** Discoverable course marker; traversal itself probes the same geometry anywhere in the world. */
    public static final class Climb extends WorldObject {
        public Climb(long id, String name, float x, float y, float z) { super(id, "climb", name, x, y, z); }
        @Override public List<Option> options(Rpg g, Actor a) { return List.of(Option.on("examine", "Examine", 0, g.cfg.inspectRange)); }
        @Override public Result perform(Rpg g, Actor a, String action, long req) {
            if (!action.equals("examine")) return Result.no("Face a ledge and hold Sprint to traverse it (Agility 5).");
            if (a == g.player) g.chat.say(examine(g)); return Result.ok();
        }
        @Override public String examine(Rpg g) { return "A practice ledge. Face it and hold Sprint. Agility 5 allows a two-block ledge; higher Agility extends reach."; }
    }

    /** The equipment cabinet: every tier's gear for equipment-requirement tests (test supply - it mints what it hands out). */
    public static final class Cabinet extends WorldObject {
        public Cabinet(long id, float x, float y, float z) { super(id, "cabinet", "Equipment cabinet", x, y, z); }
        @Override public float focusRadius() { return 1f; }
        @Override public List<Option> options(Rpg g, Actor a) { return List.of(Option.on("open", "Browse gear", 10, g.cfg.interactRange), Option.on("kit", "Take combat test kit", 5, g.cfg.interactRange), Option.on("examine", "Examine", 0, 5)); }
        @Override public Result perform(Rpg g, Actor a, String action, long req) {
            if (action.equals("examine")) { if (a == g.player) g.chat.say(examine(g)); return Result.ok(); }
            String why = reach(g, a, g.cfg.interactRange);
            if (why != null) return Result.no(why);
            if (action.equals("open")) { if (a == g.player) g.ui.openCabinet(this); return Result.ok(); }
            if (action.equals("kit")) {
                List<ItemStack> kit = List.of(new ItemStack(Items.BRONZE_ARROWS, 60), new ItemStack(Items.SHORTBOW, 1), new ItemStack(Items.STAFF, 1),
                        new ItemStack(Items.get("wooden_shield"), 1), new ItemStack(Items.get("bronze_sword"), 1), new ItemStack(Items.SHRIMP, 4), new ItemStack(Items.HOLY_SYMBOL, 1));
                return give(g, a, kit, req);
            }
            if (action.startsWith("take:")) {
                ItemDef d = Items.find(action.substring(5));
                if (d == null) return Result.no("No such item.");
                return give(g, a, List.of(new ItemStack(d, d.stackable ? 100 : 1)), req);
            }
            return Result.no("You can't do that.");
        }
        private Result give(Rpg g, Actor a, List<ItemStack> l, long req) {
            if (!a.canStoreAll(l)) return Result.no("You don't have enough inventory space.");
            for (ItemStack s : l) g.grant(a, s);
            g.state.commit(req, "TestSupplyTaken", a.id, id, jurisdiction(), 0, l.toString());
            return Result.ok();
        }
        @Override public String examine(Rpg g) { return "Test equipment of every tier."; }
    }

    /** The debug terminal: opens the debug panel; sells skillcapes for mastered skills. */
    public static final class Terminal extends WorldObject {
        public Terminal(long id, float x, float y, float z) { super(id, "terminal", "Debug terminal", x, y, z); }
        @Override public float focusRadius() { return 1f; }
        @Override public List<Option> options(Rpg g, Actor a) {
            List<Option> o = new ArrayList<>();
            o.add(Option.on("use", "Use terminal", 10, g.cfg.interactRange));
            for (Skill s : Skill.ALL) if (a.skills.level(s) >= XpTable.MAX_LEVEL) o.add(Option.when(a.has(Items.COINS, 99000), "cape:" + s.name(), "Buy " + s.title + " cape", 4, "A skillcape costs 99,000 coins.", g.cfg.interactRange));
            o.add(Option.on("examine", "Examine", 0, 5));
            return o;
        }
        @Override public Result perform(Rpg g, Actor a, String action, long req) {
            if (action.equals("examine")) { if (a == g.player) g.chat.say(examine(g)); return Result.ok(); }
            String why = reach(g, a, g.cfg.interactRange);
            if (why != null) return Result.no(why);
            if (action.equals("use")) { if (a == g.player) { if (!g.ui.console.isOpen()) g.ui.console.toggle(); g.ui.console.print("The debug terminal: the developer console (` anywhere). Try give, setlevel, goto, spawn ... ('commands' lists them all)", 0xFFE6E6E0); } return Result.ok(); }
            if (action.startsWith("cape:")) {
                Skill s = Skill.valueOf(action.substring(5));
                if (a.skills.level(s) < XpTable.MAX_LEVEL) return Result.no("You haven't mastered " + s.title + ".");
                if (!a.has(Items.COINS, 99000)) return Result.no("A skillcape costs 99,000 coins.");
                ItemStack cape = new ItemStack(Items.cape(s), 1);
                Inventory probe = a.inv.copy(); probe.remove(Items.COINS, 99000);
                if (!probe.add(cape)) return Result.no("You don't have enough inventory space.");
                a.takeItems(Items.COINS, 99000); g.state.burn(Items.COINS, 99000);
                g.grant(a, cape);
                g.state.commit(req, "SkillcapeBought", a.id, id, null, 0, s.name());
                return Result.ok();
            }
            return Result.no("You can't do that.");
        }
        @Override public String examine(Rpg g) { return "Debug controls: clock, teleports, XP, gear, AI and state inspectors, save/load."; }
    }

    /** A market stall: its goods belong to the merchant; stealing from it is Thieving, and a crime if seen. Restocks over time. */
    public static final class Stall extends WorldObject {
        public final Inventory goods = new Inventory(10, 1);   // a counter of five loaves
        public final int level = 5;
        public double restockAt;
        public Stall(long id, float x, float y, float z, long owner) { super(id, "stall", "Market stall", x, y, z); this.owner = owner; }
        @Override public float focusRadius() { return 1.1f; }
        @Override public List<Option> options(Rpg g, Actor a) {
            boolean lvl = a.skills.level(Skill.THIEVING) >= level, any = !goods.isEmpty(), room = goods.isEmpty() || a.canStore(goods.stacks().get(0).copy(1));
            return List.of(Option.when(lvl && any && room, "steal", "Steal from", 10, !lvl ? "You need Thieving level " + level + "." : !any ? "The stall is empty." : "Your inventory is too full.", g.cfg.interactRange),
                    Option.on("examine", "Examine", 0, 5));
        }
        @Override public Result perform(Rpg g, Actor a, String action, long req) {
            if (action.equals("examine")) { if (a == g.player) g.chat.say(examine(g)); return Result.ok(); }
            String why = reach(g, a, g.cfg.interactRange);
            if (why != null) return Result.no(why);
            if (!action.equals("steal")) return Result.no("You can't do that.");
            if (a.skills.level(Skill.THIEVING) < level) return Result.no("You need Thieving level " + level + " to steal from this stall.");
            g.interact.begin(new Work(a, this, "steal", "Stealing", 1.2f, false, g.time, () -> {
                long r2 = g.state.newRequestId();
                String w2 = reach(g, a, g.cfg.interactRange);
                if (w2 != null) return Result.no(w2);
                int slot = -1;
                for (int i = 0; i < goods.size(); i++) if (goods.get(i) != null) { slot = i; break; }
                if (slot < 0) return Result.no("The stall is empty.");
                ItemStack s = goods.get(slot).copy(1);
                s.owner = owner; s.markStolen(owner);
                if (!a.canStore(s)) return Result.no("Your inventory is too full.");
                goods.take(slot, 1);
                a.store(s);
                var ev = g.state.commit(r2, "ItemTaken", a.id, id, jurisdiction(), 0, s + " from the stall");
                g.law.theft(a, owner, s, this, ev.id());
                g.prog.award(a, Skill.THIEVING, 16, "stall theft", ev.id());
                if (a == g.player) g.chat.say("You steal " + s.def.name.toLowerCase() + " from the stall.");
                return Result.ok();
            }));
            return Result.started();
        }
        public void update(Rpg g) {
            if (g.state.minutes >= restockAt) {
                restockAt = g.state.minutes + 20;
                if (goods.count(Items.BREAD) < 5 && goods.add(new ItemStack(Items.BREAD, 1, owner, false, id))) g.state.mint(Items.BREAD, 1);
            }
        }
        @Override public String examine(Rpg g) { return "Bread for sale. It belongs to " + g.nameOf(owner) + ". (" + goods.count(Items.BREAD) + " loaves)"; }
        @Override public String inspect(Rpg g) { return super.inspect(g) + "\n  goods=" + goods.stacks(); }
        @Override public Map<String, Object> save() { Map<String, Object> m = super.save(); m.put("goods", rpg.item.ItemIO.inventory(goods)); m.put("restockAt", restockAt); return m; }
        @Override public void load(Rpg g, Map<String, Object> m) { super.load(g, m); rpg.item.ItemIO.inventory(goods, astratto.util.Json.list(m, "goods")); restockAt = astratto.util.Json.num(m, "restockAt", 0); }
    }

    /** A reusable board belongs to a jurisdiction, rather than a particular NPC or map coordinate. */
    public static final class Board extends WorldObject {
        private final String jurisdiction;
        public Board(long id, float x, float y, float z, String jurisdiction) { super(id, "bounty_board", "Bounty board", x, y, z); this.jurisdiction = jurisdiction; }
        @Override public float focusRadius() { return 0.75f; }
        @Override public String examine(Rpg g) { return "Sworn warrants posted by the watch of " + jurisdiction + "."; }
        @Override public List<Option> options(Rpg g, Actor a) { return List.of(Option.on("read", "Read warrants", 10, g.cfg.interactRange), Option.on("examine", "Examine", 0, g.cfg.inspectRange)); }
        @Override public Result perform(Rpg g, Actor a, String action, long req) {
            String why = reach(g, a, action.equals("examine") ? g.cfg.inspectRange : g.cfg.interactRange); if (why != null) return Result.no(why);
            if (action.equals("examine")) { if (a == g.player) g.chat.say("Sworn warrants posted by the watch of " + jurisdiction + "."); return Result.ok(); }
            if (!action.equals("read")) return Result.no("You cannot do that.");
            if (a == g.player && g.ui != null) g.ui.dialog(g.bountyBoard.browse(jurisdiction)); return Result.ok();
        }
    }

    /** The quest-state kiosk: the debug quest panel (flags, the test quest's state machine, the removable NPC). */
    public static final class Kiosk extends WorldObject {
        public Kiosk(long id, float x, float y, float z) { super(id, "kiosk", "Quest-state kiosk", x, y, z); }
        @Override public float focusRadius() { return 1f; }
        @Override public List<Option> options(Rpg g, Actor a) { return List.of(Option.on("use", "Use quest panel", 10, g.cfg.interactRange), Option.on("examine", "Examine", 0, 5)); }
        @Override public Result perform(Rpg g, Actor a, String action, long req) {
            if (action.equals("examine")) { if (a == g.player) g.chat.say(examine(g)); return Result.ok(); }
            String why = reach(g, a, g.cfg.interactRange);
            if (why != null) return Result.no(why);
            if (a == g.player) g.ui.openKiosk();
            return Result.ok();
        }
        @Override public String examine(Rpg g) { return "Debug quest flags and the test quest's state machine."; }
    }
}
