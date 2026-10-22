package rpg.ui;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.jse.JsePlatform;
import rpg.Chat;
import rpg.Rpg;
import rpg.actor.Player;
import rpg.item.Inventory;
import rpg.item.ItemDef;
import rpg.item.ItemStack;
import rpg.item.Items;
import rpg.skill.Skill;
import rpg.ui.Fragment.Anchor;
import rpg.ui.Fragment.Placement;
import rpg.world.DevMap;

/**
 * The Lua addon API (gamefixes, UI: the interface is meant to be extended and reorganised by external code). Every
 * {@code addons/*.lua} file is run at start (and again from the debug scene's "Reload addons"). An addon can add HUD
 * fragments that fade in on game state or UI events, listen to every UI and domain event, move, hide or show any
 * fragment the game has, and put fragment groups into scenes. The scripts run sandboxed: no files, no processes, no
 * Java; only the {@code ui} and {@code game} tables below. See {@code addons/README.md}.
 */
public final class Addons {
    private final Hud hud;
    private Globals lua;
    private final List<String> loaded = new ArrayList<>(), failed = new ArrayList<>();
    private final List<LuaValue[]> listeners = new ArrayList<>();
    private final Set<String> added = new HashSet<>(), touched = new HashSet<>();
    private final List<String[]> groupEdits = new ArrayList<>();

    Addons(Hud hud) {
        this.hud = hud;
        hud.events.listen((type, detail) -> {
            for (LuaValue[] l : new ArrayList<>(listeners)) {
                if (!l[0].tojstring().equals(type) && !l[0].tojstring().equals("*")) continue;
                try { l[1].call(LuaValue.valueOf(type), LuaValue.valueOf(detail == null ? "" : detail)); }
                catch (LuaError e) { listeners.remove(l); report("an event handler for '" + type + "'", e); }
            }
        });
    }

    public String summary() { return loaded.isEmpty() && failed.isEmpty() ? "none" : String.join(", ", loaded) + (failed.isEmpty() ? "" : "  (failed: " + String.join(", ", failed) + ")"); }

    /** Runs every addon in a folder, after undoing what the last load did. */
    public void loadAll(Path dir) {
        unload();
        lua = sandbox();
        if (!Files.isDirectory(dir)) return;
        List<Path> files;
        try (Stream<Path> s = Files.list(dir)) { files = s.filter(p -> p.toString().endsWith(".lua")).sorted().toList(); }
        catch (Exception e) { System.err.println("[addons] " + e); return; }
        for (Path p : files) {
            String name = p.getFileName().toString();
            try {
                lua.load(Files.readString(p), "@" + name).call();
                loaded.add(name);
            } catch (Exception e) {
                failed.add(name);
                report(name, e);
            }
        }
        if (!loaded.isEmpty()) System.out.println("[addons] loaded " + loaded);
    }

    private void unload() {
        for (String id : added) { hud.fragments.remove(id); for (var l : hud.groups.values()) l.removeIf(s -> s.fragment().equals(id)); }
        for (String id : touched) { Fragment f = hud.fragments.get(id); if (f != null) { f.forced = null; f.moved = null; } }
        for (String[] e : groupEdits) hud.scenes.get(Hud.Scene.valueOf(e[0])).remove(e[1]);
        added.clear(); touched.clear(); groupEdits.clear(); listeners.clear(); loaded.clear(); failed.clear();
    }

    void frame() { }

    /** A fragment threw while drawing: an addon's is switched off and reported; the game's own is a bug and is rethrown. */
    void fault(Fragment f, RuntimeException e) {
        if (f instanceof LuaFragment lf) { lf.forced = false; lf.broken = true; report("fragment '" + f.id + "'", e); return; }
        throw e;
    }

    private void report(String what, Exception e) {
        String msg = e.getMessage() == null ? e.toString() : e.getMessage();
        System.err.println("[addons] " + what + ": " + msg);
        hud.g.chat.add("[Addon] " + what + " failed: " + msg, Chat.Kind.DEBUG);
    }

    // ------------------------------------------------------------------ the sandbox and the API
    private Globals sandbox() {
        Globals g = JsePlatform.standardGlobals();
        for (String k : new String[]{"io", "os", "luajava", "dofile", "loadfile", "require", "package", "debug", "collectgarbage", "load"}) g.set(k, LuaValue.NIL);
        LuaTable ui = new LuaTable(), game = new LuaTable();
        g.set("ui", ui);
        g.set("game", game);

        ui.set("fragment", fn(a -> { LuaFragment f = fragment(a.checktable(1)); return LuaValue.valueOf(f.id); }));
        ui.set("on", fn(a -> { a.checkfunction(2); listeners.add(new LuaValue[]{LuaValue.valueOf(a.checkjstring(1)), a.arg(2)}); return LuaValue.NIL; }));
        ui.set("move", fn(a -> { Fragment f = existing(a.checkjstring(1)); f.moved = Placement.at(anchor(a.checkjstring(2)), (float) a.optdouble(3, 0), (float) a.optdouble(4, 0)); return LuaValue.NIL; }));
        ui.set("hide", fn(a -> { existing(a.checkjstring(1)).forced = false; return LuaValue.NIL; }));
        ui.set("show", fn(a -> { existing(a.checkjstring(1)).forced = true; return LuaValue.NIL; }));
        ui.set("reset", fn(a -> { Fragment f = existing(a.checkjstring(1)); f.forced = null; f.moved = null; return LuaValue.NIL; }));
        ui.set("group", fn(a -> {   // ui.group(groupId, fragmentId, anchor, x, y): put a fragment into a group (made if new)
            String gid = a.checkjstring(1);
            existing(a.checkjstring(2));
            hud.group(gid, a.checkjstring(2), Placement.at(anchor(a.optjstring(3, "TOP_LEFT")), (float) a.optdouble(4, 0), (float) a.optdouble(5, 0)));
            return LuaValue.NIL;
        }));
        ui.set("add_group", fn(a -> {   // ui.add_group(scene, groupId): show a group in a scene
            Hud.Scene s = scene(a.checkjstring(1));
            String gid = a.checkjstring(2);
            List<String> l = hud.scenes.get(s);
            if (!l.contains(gid)) { l.add(Math.max(0, l.size() - 1), gid); groupEdits.add(new String[]{s.name(), gid}); }
            return LuaValue.NIL;
        }));
        ui.set("scene", fn(a -> LuaValue.valueOf(hud.scene.name())));
        ui.set("open", fn(a -> { hud.go(scene(a.checkjstring(1))); return LuaValue.NIL; }));
        ui.set("fragments", fn(a -> { LuaTable t = new LuaTable(); int i = 1; for (String id : hud.fragments.keySet()) t.set(i++, id); return t; }));
        ui.set("icons", fn(a -> { LuaTable t = new LuaTable(); int i = 1; for (String n : Atlas.NAMES) t.set(i++, n); return t; }));
        ui.set("chat", fn(a -> { hud.g.chat.add(a.checkjstring(1), Chat.Kind.GAME); return LuaValue.NIL; }));
        ui.set("screen", fn(a -> LuaValue.varargsOf(LuaValue.valueOf(hud.w), LuaValue.valueOf(hud.h))));

        game.set("player", fn(a -> player()));
        game.set("skill", fn(a -> { Skill s = Skill.valueOf(a.checkjstring(1).toUpperCase()); return LuaValue.varargsOf(LuaValue.valueOf(hud.g.player.skills.level(s)), LuaValue.valueOf(hud.g.player.skills.xp(s))); }));
        game.set("skills", fn(a -> { LuaTable t = new LuaTable(); for (Skill s : Skill.ALL) t.set(s.name(), hud.g.player.skills.level(s)); return t; }));
        game.set("count", fn(a -> { ItemDef d = Items.get(a.checkjstring(1)); return LuaValue.valueOf(d == null ? 0 : hud.g.player.count(d)); }));
        game.set("bounty", fn(a -> LuaValue.valueOf(hud.g.law.bounty(hud.g.player.id, a.optjstring(1, DevMap.jurisdiction(hud.g.player.x, hud.g.player.z))))));
        game.set("clock", fn(a -> LuaValue.valueOf(hud.g.state.clockText())));
        game.set("hour", fn(a -> LuaValue.valueOf(hud.g.state.hourOfDay())));
        game.set("time", fn(a -> LuaValue.valueOf(hud.g.time)));
        game.set("jurisdiction", fn(a -> LuaValue.valueOf(DevMap.jurisdiction(hud.g.player.x, hud.g.player.z))));
        game.set("gold_text", fn(a -> { long n = (long) a.checkdouble(1); return LuaValue.varargsOf(LuaValue.valueOf(rpg.item.Currency.text(n)), LuaValue.valueOf(rpg.item.Currency.color(n) & 0xFFFFFFFFL)); }));
        game.set("inventory", fn(a -> {
            LuaTable t = new LuaTable();
            int i = 1, gi = 0;
            for (Inventory inv : hud.g.player.grids()) {
                for (int k = 0; k < inv.size(); k++) {
                    ItemStack s = inv.get(k);
                    if (s == null) continue;
                    LuaTable e = new LuaTable();
                    e.set("id", s.def.id); e.set("name", s.def.name); e.set("amount", s.amount); e.set("stolen", LuaValue.valueOf(s.stolen));
                    e.set("grid", gi); e.set("x", inv.x(k)); e.set("y", inv.y(k)); e.set("category", s.def.category().name());
                    t.set(i++, e);
                }
                gi++;
            }
            return t;
        }));
        return g;
    }

    private LuaTable player() {
        Player p = hud.g.player;
        LuaTable t = new LuaTable();
        t.set("name", p.name);
        t.set("hp", p.hp); t.set("max_hp", p.maxHp());
        t.set("stamina", p.stamina); t.set("max_stamina", p.maxStamina());
        t.set("faith", p.faith); t.set("max_faith", p.maxFaith());
        t.set("mana", p.mana); t.set("max_mana", p.maxMana());
        t.set("x", p.x); t.set("y", p.y); t.set("z", p.z);
        t.set("alive", LuaValue.valueOf(p.alive)); t.set("sprinting", LuaValue.valueOf(p.sprinting));
        t.set("combat_level", p.skills.combatLevel()); t.set("total_level", p.skills.totalLevel());
        t.set("gold", p.count(Items.COINS)); t.set("carried", p.carried()); t.set("burden", p.burden());
        t.set("in_combat", LuaValue.valueOf(hud.g.time - p.cs.lastHurt < 6 || hud.g.time - p.cs.lastAttack < 6));
        return t;
    }

    private Fragment existing(String id) {
        Fragment f = hud.fragments.get(id);
        if (f == null) throw new LuaError("no fragment '" + id + "' (ui.fragments() lists them)");
        touched.add(id);
        return f;
    }

    private static Anchor anchor(String s) {
        try { return Anchor.valueOf(s.toUpperCase()); } catch (IllegalArgumentException e) { throw new LuaError("no anchor '" + s + "' (TOP_LEFT, TOP, TOP_RIGHT, LEFT, CENTER, RIGHT, BOTTOM_LEFT, BOTTOM, BOTTOM_RIGHT, SCREEN)"); }
    }

    private static Hud.Scene scene(String s) {
        try { return Hud.Scene.valueOf(s.toUpperCase()); } catch (IllegalArgumentException e) { throw new LuaError("no scene '" + s + "' (WORLD, INVENTORY, CHARACTER, SKILLS, JOURNAL, MAGIC)"); }
    }

    private LuaFragment fragment(LuaTable t) {
        String id = t.get("id").checkjstring();
        if (hud.fragments.containsKey(id) && !added.contains(id)) throw new LuaError("fragment id '" + id + "' is taken by the game");
        LuaFragment f = new LuaFragment(id, (float) t.get("w").optdouble(160), (float) t.get("h").optdouble(40), t.get("when"), t.get("draw").checkfunction());
        LuaValue wake = t.get("wake");
        if (wake.istable()) for (int i = 1; i <= wake.length(); i++) f.wakeOn.add(wake.get(i).tojstring());
        f.linger = (float) t.get("linger").optdouble(4);
        f.fadeIn = (float) t.get("fade_in").optdouble(0.15);
        f.fadeOut = (float) t.get("fade_out").optdouble(0.45);
        hud.add(f);
        added.add(id);
        String group = t.get("group").optjstring("addons");
        hud.group(group, id, Placement.at(anchor(t.get("anchor").optjstring("TOP_LEFT")), (float) t.get("x").optdouble(0), (float) t.get("y").optdouble(0)));
        return f;
    }

    // ------------------------------------------------------------------ an addon's fragment
    final class LuaFragment extends Fragment {
        final float fw, fh;
        final LuaValue when, drawFn;
        boolean broken;
        private float ox, oy;
        private Ui2D ui;
        private final LuaTable ctx = new LuaTable();

        LuaFragment(String id, float fw, float fh, LuaValue when, LuaValue drawFn) {
            super(id);
            this.fw = fw; this.fh = fh; this.when = when; this.drawFn = drawFn;
            ctx.set("rect", fn(a -> { ui.rect(ox + f(a, 2), oy + f(a, 3), f(a, 4), f(a, 5), color(a.arg(6), Style.CARD)); return LuaValue.NIL; }));
            ctx.set("border", fn(a -> { ui.border(ox + f(a, 2), oy + f(a, 3), f(a, 4), f(a, 5), 1, color(a.arg(6), Style.EDGE)); return LuaValue.NIL; }));
            ctx.set("text", fn(a -> { ui.text(a.checkjstring(2), ox + f(a, 3), oy + f(a, 4), (float) a.optdouble(5, 13), color(a.arg(6), Style.TEXT)); return LuaValue.NIL; }));
            ctx.set("icon", fn(a -> { ui.img(a.checkjstring(2), ox + f(a, 3), oy + f(a, 4), (float) a.optdouble(5, 16), color(a.arg(6), 0xFFFFFFFF)); return LuaValue.NIL; }));
            ctx.set("skill_icon", fn(a -> { ui.img(Atlas.skill(Skill.valueOf(a.checkjstring(2).toUpperCase())), ox + f(a, 3), oy + f(a, 4), (float) a.optdouble(5, 16), 0xFFFFFFFF); return LuaValue.NIL; }));
            ctx.set("bar", fn(a -> { Style.bar(ui, ox + f(a, 2), oy + f(a, 3), f(a, 4), f(a, 5), (float) a.checkdouble(6), color(a.arg(7), Style.ACCENT)); return LuaValue.NIL; }));
            ctx.set("width", fn(a -> LuaValue.valueOf(ui.width(a.checkjstring(2), (float) a.optdouble(3, 13)))));
            ctx.set("style", styleTable());
        }

        @Override protected boolean active() {
            if (broken) return false;
            if (when.isnil()) return true;
            try { return when.call().toboolean(); }
            catch (LuaError e) { broken = true; forced = false; report("fragment '" + id + "' (when)", e); return false; }
        }
        @Override public float width() { return fw; }
        @Override public float height() { return fh; }
        @Override public void draw(Ui2D ui, float x, float y) {
            this.ui = ui; ox = x; oy = y;
            ctx.set("w", fw); ctx.set("h", fh); ctx.set("alpha", alpha);
            drawFn.call(ctx);
        }
    }

    private static float f(Varargs a, int i) { return (float) a.checkdouble(i); }

    /** A colour from Lua: a number 0xAARRGGBB, or "#rrggbb" / "#rrggbbaa", or one of the style's names. */
    static int color(LuaValue v, int dflt) {
        if (v.isnil()) return dflt;
        if (v.isnumber()) return (int) (long) v.todouble();
        String s = v.tojstring();
        if (s.startsWith("#")) {
            long c = Long.parseLong(s.substring(1), 16);
            return s.length() == 9 ? (int) ((c & 0xFF) << 24 | c >>> 8) : (int) (0xFF000000L | c);
        }
        return switch (s) { case "text" -> Style.TEXT; case "dim" -> Style.DIM; case "faint" -> Style.FAINT; case "accent" -> Style.ACCENT; case "good" -> Style.GOOD; case "bad" -> Style.BAD; case "info" -> Style.INFO; case "card" -> Style.CARD; case "edge" -> Style.EDGE; default -> dflt; };
    }

    private static LuaTable styleTable() {
        LuaTable t = new LuaTable();
        for (String n : new String[]{"text", "dim", "faint", "accent", "good", "bad", "info", "card", "edge"}) t.set(n, n);
        return t;
    }

    interface Body { Varargs call(Varargs a); }
    private static VarArgFunction fn(Body b) {
        return new VarArgFunction() { @Override public Varargs invoke(Varargs a) { return b.call(a); } };
    }
}
